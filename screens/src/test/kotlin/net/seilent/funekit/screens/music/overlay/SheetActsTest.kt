package net.seilent.funekit.screens.music.overlay

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.Playlists
import net.seilent.funekit.api.Quickplay
import net.seilent.funekit.api.Refused
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.TrackSet
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.lists.HistoryModel
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.ViewKey
import net.seilent.funekit.screens.music.overlay.ADD
import net.seilent.funekit.screens.music.overlay.ADDED_TO
import net.seilent.funekit.screens.music.overlay.DELETE_PLAYLIST
import net.seilent.funekit.screens.music.overlay.INFO
import net.seilent.funekit.screens.music.overlay.KEEP_PLAYLIST
import net.seilent.funekit.screens.music.overlay.NEW_PLAYLIST
import net.seilent.funekit.screens.music.overlay.PIN
import net.seilent.funekit.screens.music.overlay.PLAY
import net.seilent.funekit.screens.music.overlay.PLAY_NEXT
import net.seilent.funekit.screens.music.overlay.REMOVE
import net.seilent.funekit.screens.music.overlay.SHUFFLE
import net.seilent.funekit.screens.music.overlay.SONGS_ADDED
import net.seilent.funekit.screens.music.overlay.SONG_ADDED
import net.seilent.funekit.screens.music.overlay.SheetDone
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.UNPIN_QP
import net.seilent.funekit.screens.music.overlay.confirmDelete
import net.seilent.funekit.screens.music.overlay.infoOf
import net.seilent.funekit.screens.music.overlay.picker
import net.seilent.funekit.screens.music.overlay.runEntry
import net.seilent.funekit.screens.music.overlay.runPick
import net.seilent.funekit.screens.music.overlay.runSheet
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetActsTest {
    private var clock = 0L
    private val scheduler = TestCoroutineScheduler()
    private val f = testFake(FakeSet.Demo, scheduler) { clock }
    private val calls = mutableListOf<Int>()

    private val recording = object : Player by f.player {
        override suspend fun play(listing: Listing, generation: Long, index: Int, posMs: Long, shuffle: Boolean) {
            if (shuffle) calls += index
            f.player.play(listing, generation, index, posMs, shuffle)
        }
    }
    private val src = MusicSource(f.library, recording, f.quickplay, f.favorites, f.playlists, f.lyrics)

    private fun gen(): Long = runBlocking { f.library.view().generation }

    private fun album(): Row = runBlocking { f.library.view().page(Listing.Albums, 0, 50).rows.first { it.count == 8 } }

    private fun albumTarget(a: Row) = SheetTarget(SheetKind.Album, a.title, Listing.AlbumTracks(a.id), gen(), pin = Kind.Album, id = a.id)

    private fun song(i: Int): Row = runBlocking { f.library.view().page(Listing.Songs, i, 1).rows.single() }

    @Test
    fun playOfASongsSheetPlaysSongsFromItsIndex() {
        val s = song(5)
        val t = SheetTarget(SheetKind.Song, s.title, Listing.Songs, gen(), 5, s.id, Kind.Song, s.id)
        assertEquals(SheetDone.Close, runBlocking { runSheet(PLAY, t, src) })
        val st = f.player.state.value
        assertEquals(Listing.Songs, st.origin)
        assertEquals(5, st.index)
        assertEquals(s.id, st.track)
    }

    @Test
    fun playOfARecentSongsSheetPlaysTheSongAlone() {
        val h = HistoryModel(0L).also { m -> m.panes.forEach { it.geometry(RowSpec(1, 1)) } }
        runBlocking { h.load(f.library.view(), ViewKey(0L, gen()), 0L) }
        val t = requireNotNull(h.target(RECENT_ROW)) { "no RECENT row $RECENT_ROW" }
        assertEquals(SheetDone.Close, runBlocking { runSheet(PLAY, t, src) })
        val st = f.player.state.value
        assertEquals("the RECENT sheet plays the song alone", 1, st.queueSize)
        assertEquals(t.track, st.track)
    }

    @Test
    fun playNextOfASongAddsItAfterTheCurrentAndToastsOneSong() {
        val a = album()
        runBlocking { f.player.play(Listing.AlbumTracks(a.id), gen(), 0, 0) }
        val s = song(40)
        val t = SheetTarget(SheetKind.Song, s.title, Listing.Songs, gen(), 40, s.id, Kind.Song, s.id)
        val done = runBlocking { runSheet(PLAY_NEXT, t, src) }
        assertEquals(SheetDone.Toast(SONG_ADDED, s.title), done)
        val q = runBlocking { f.library.view().page(Listing.Queue, 0, 10).rows.map { it.id } }
        assertEquals(s.id, q[1])
    }

    @Test
    fun addingAnAlbumToastsSongsAndIntoAnEmptyQueueLoadsItPausedAtTheStart() {
        val a = album()
        val done = runBlocking { runSheet(ADD, albumTarget(a), src) }
        assertEquals(SheetDone.Toast(SONGS_ADDED, a.title), done)
        val st = f.player.state.value
        assertEquals(8, st.queueSize)
        assertFalse(st.playing)
        assertEquals(0L, st.anchor.frame)
    }

    @Test
    fun anAddAtTheQueueEndToastsTheSongItAdded() {
        val a = song(0)
        val b = song(1)
        runBlocking { f.player.play(listOf(a.id, b.id), 1, checkNotNull(b.durationMs) - 500) }
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()
        assertFalse("premise: the queue ended", f.player.state.value.playing)
        val s = song(40)
        val t = SheetTarget(SheetKind.Song, s.title, Listing.Songs, gen(), 40, s.id, Kind.Song, s.id)
        assertEquals(SheetDone.Toast(SONG_ADDED, s.title), runBlocking { runSheet(ADD, t, src) })
        val q = runBlocking { f.library.view().page(Listing.Queue, 0, 10).rows.map { it.id } }
        assertEquals(listOf(s.id), q)
    }

    @Test
    fun addingAnEmptyPlaylistToastsNothing() {
        val id = runBlocking {
            val made = checkNotNull(f.playlists.create(TrackSet.Ids(listOf(f.library.view().page(Listing.Songs, 0, 1).rows.single().id))))
            f.playlists.remove(made, 0, f.library.view().page(Listing.PlaylistTracks(made), 0, 1).revision)
            made
        }
        val t = SheetTarget(SheetKind.Playlist, "playlist", Listing.PlaylistTracks(id), gen(), id = id, writable = true)
        assertEquals(SheetDone.Close, runBlocking { runSheet(ADD, t, src) })
    }

    @Test
    fun shuffleIsOnePlayFromIndexMinusOneAndDoesNotAlwaysStartOnTrackZero() {
        val a = album()
        val firsts = mutableListOf<Long?>()
        val tracks = runBlocking { f.library.view().page(Listing.AlbumTracks(a.id), 0, 8).rows.map { it.id } }
        repeat(8) {
            runBlocking {
                f.player.shuffle(false)
                runSheet(SHUFFLE, albumTarget(a), src)
            }
            firsts += f.player.state.value.track
            assertTrue(f.player.state.value.shuffle)
        }
        assertEquals(List(8) { -1 }, calls)
        assertFalse(firsts.all { it == tracks[0] })
    }

    @Test
    fun pinThenUnpinReviseQuickplayTwice() {
        val a = runBlocking {
            val v = f.library.view()
            v.page(Listing.Albums, 0, 50).rows.first { it.count == 8 && !v.pinned(Kind.Album, it.id) }
        }
        val r0 = f.quickplay.revision.value
        runBlocking { runSheet(PIN, albumTarget(a), src) }
        assertTrue(runBlocking { f.library.view().pinned(Kind.Album, a.id) })
        runBlocking { runSheet(UNPIN_QP, albumTarget(a), src) }
        assertFalse(runBlocking { f.library.view().pinned(Kind.Album, a.id) })
        assertEquals(r0 + 2, f.quickplay.revision.value)
    }

    @Test
    fun aPinPickedAfterQuickplayWentAwayPinsNothing() {
        val gone = object : Quickplay by f.quickplay {
            override val available: StateFlow<Boolean> = MutableStateFlow(false)

            override suspend fun pin(kind: Kind, id: Long) = throw UnsupportedOperationException()

            override suspend fun unpin(kind: Kind, id: Long) = throw UnsupportedOperationException()
        }
        val s = MusicSource(f.library, recording, gone, f.favorites, f.playlists, f.lyrics)
        val t = albumTarget(album())
        assertEquals(SheetDone.Close, runBlocking { runSheet(PIN, t, s) })
        assertEquals(SheetDone.Close, runBlocking { runSheet(UNPIN_QP, t, s) })
    }

    @Test
    fun deletePlaylistAsksAndTheConfirmDeletesOrKeeps() {
        val lists = runBlocking { f.library.view().page(Listing.Playlists, 0, 10).rows }
        val w = lists.first { it.writable }
        val t = SheetTarget(SheetKind.Playlist, w.title, Listing.PlaylistTracks(w.id), gen(), id = w.id, songs = w.count ?: 0, writable = true)
        val done = runBlocking { runSheet(DELETE_PLAYLIST, t, src) }
        assertEquals(SheetDone.Confirm(w.id), done)
        runBlocking { confirmDelete(KEEP_PLAYLIST, w.id, src) }
        assertTrue(runBlocking { f.library.view().page(Listing.Playlists, 0, 10).rows.any { it.id == w.id } })
        runBlocking { confirmDelete(DELETE_PLAYLIST, w.id, src) }
        assertFalse(runBlocking { f.library.view().page(Listing.Playlists, 0, 10).rows.any { it.id == w.id } })
    }

    @Test
    fun aRefusedPlaylistDeleteShowsTheDevicesLine() {
        val refused = object : Playlists by f.playlists {
            override suspend fun delete(id: Long) = throw Refused(LINE)
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, refused, f.lyrics)
        val w = lists().first { it.writable }
        assertEquals(SheetDone.Toast(LINE, ""), runBlocking { confirmDelete(DELETE_PLAYLIST, w.id, s) })
    }

    @Test
    fun aPlaylistDeleteWithNoCardShowsCardNotAvailable() {
        val out = object : Playlists by f.playlists {
            override suspend fun delete(id: Long) = throw NoCard()
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, out, f.lyrics)
        val w = lists().first { it.writable }
        assertEquals(SheetDone.Toast(CARD_GONE, ""), runBlocking { confirmDelete(DELETE_PLAYLIST, w.id, s) })
    }

    @Test
    fun aPlaylistDeleteFailingOutsideTheSeamFailsPlainly() {
        val e = IllegalStateException("broken")
        val broken = object : Playlists by f.playlists {
            override suspend fun delete(id: Long) = throw e
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, broken, f.lyrics)
        val w = lists().first { it.writable }
        assertSame(e, assertThrows(IllegalStateException::class.java) { runBlocking { confirmDelete(DELETE_PLAYLIST, w.id, s) } })
    }

    @Test
    fun infoOnAQueueRowAfterTheLeaseOpensTheTrackInfo() {
        val a = album()
        runBlocking { f.player.play(Listing.AlbumTracks(a.id), gen(), 0, 0) }
        val old = gen()
        val row = runBlocking { f.library.view().page(Listing.Queue, 0, 1).rows.single() }
        runBlocking { f.library.rescan() }
        scheduler.runCurrent()
        assertTrue(f.library.generation.value > old)
        clock += LEASE_MS * NANOS
        val t = SheetTarget(SheetKind.Queue, row.title, Listing.Queue, old, track = row.id, slot = 0, cur = 0, size = 8)
        val done = runBlocking { runSheet(INFO, t, src) }
        assertTrue(done is SheetDone.Info)
        assertNotNull(runBlocking { infoOf(done as SheetDone.Info, src) })
    }

    private fun lists(): List<Row> = runBlocking {
        val v = f.library.view()
        v.page(Listing.Playlists, 0, v.page(Listing.Playlists, 0, 0).total).rows
    }

    private fun count(l: Listing): Int = runBlocking { f.library.view().page(l, 0, 0).total }

    @Test
    fun aPickOfNewPlaylistCreatesItWithTheTracksAndToasts() {
        val a = album()
        val p = runBlocking { picker(src) }
        assertEquals(NEW_PLAYLIST, p.items[0])
        val before = lists().size
        val done = runBlocking { runPick(0, p, albumTarget(a), src) }
        assertEquals("the toast names the list made", SheetDone.Toast(ADDED_TO + "playlist 1", a.title), done)
        assertEquals(before + 1, lists().size)
        assertTrue(lists().any { it.count == 8 && it.writable && it.title.startsWith("playlist") })
    }

    @Test
    fun aPickOfFavoritesAddsTheTracksAndToasts() {
        val a = album()
        val p = runBlocking { picker(src) }
        assertEquals("favorites", p.items[1])
        val before = count(Listing.Favorites)
        val fresh = runBlocking { f.library.view().page(Listing.AlbumTracks(a.id), 0, ALBUM_ROWS).rows.count { !f.favorites.has(it.id) } }
        val done = runBlocking { runPick(1, p, albumTarget(a), src) }
        assertEquals(SheetDone.Toast(ADDED_TO + "favorites", a.title), done)
        assertEquals(before + fresh, count(Listing.Favorites))
    }

    @Test
    fun thePickerListsTheWritableListsAndAPickAddsToOne() {
        val p = runBlocking { picker(src) }
        assertEquals(listOf(NEW_PLAYLIST, "favorites") + lists().filter { it.writable }.map { it.title }, p.items)
        val i = p.items.indexOf(DRIVE)
        val id = lists().first { it.title == DRIVE }.id
        val before = count(Listing.PlaylistTracks(id))
        val a = album()
        val done = runBlocking { runPick(i, p, albumTarget(a), src) }
        assertEquals(SheetDone.Toast(ADDED_TO + DRIVE, a.title), done)
        assertEquals(before + 8, count(Listing.PlaylistTracks(id)))
    }

    @Test
    fun aBusyAddToAListToastsCardNotAvailable() {
        val busy = object : Playlists by f.playlists {
            override suspend fun add(id: Long, tracks: TrackSet) = throw Busy()
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, busy, f.lyrics)
        val p = runBlocking { picker(s) }
        assertEquals(SheetDone.Toast(CARD_GONE, ""), runBlocking { runPick(p.items.indexOf(DRIVE), p, albumTarget(album()), s) })
    }

    @Test
    fun aRefusedAddToAListShowsTheDevicesLine() {
        val refused = object : Playlists by f.playlists {
            override suspend fun add(id: Long, tracks: TrackSet) = throw Refused(LINE)
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, refused, f.lyrics)
        val p = runBlocking { picker(s) }
        assertEquals(SheetDone.Toast(LINE, ""), runBlocking { runPick(p.items.indexOf(DRIVE), p, albumTarget(album()), s) })
    }

    @Test
    fun aRefusedAddWithNoLineShowsNoWordAndNoAddedToast() {
        var refusal = Refused()
        val refused = object : Playlists by f.playlists {
            override suspend fun add(id: Long, tracks: TrackSet) = throw refusal
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, refused, f.lyrics)
        val p = runBlocking { picker(s) }
        for (e in listOf(Refused(), Refused(" "))) {
            refusal = e
            assertEquals("a refusal with the line '${e.text}'", SheetDone.Close, runBlocking { runPick(p.items.indexOf(DRIVE), p, albumTarget(album()), s) })
        }
    }

    @Test
    fun anUnreadableListAddShowsPlaylistNotReadableAndItsName() {
        val unreadable = object : Playlists by f.playlists {
            override suspend fun add(id: Long, tracks: TrackSet) = throw IllegalStateException()
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, unreadable, f.lyrics)
        val p = runBlocking { picker(s) }
        assertEquals(SheetDone.Toast(NOT_READABLE, DRIVE), runBlocking { runPick(p.items.indexOf(DRIVE), p, albumTarget(album()), s) })
    }

    @Test
    fun aCancelledListEditIsThrown() {
        val cancelled = object : Playlists by f.playlists {
            override suspend fun add(id: Long, tracks: TrackSet) = throw CancellationException()
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, cancelled, f.lyrics)
        val p = runBlocking { picker(s) }
        assertThrows(CancellationException::class.java) { runBlocking { runPick(p.items.indexOf(DRIVE), p, albumTarget(album()), s) } }
    }

    @Test
    fun aRefusedNewPlaylistShowsTheDevicesLine() {
        val refused = object : Playlists by f.playlists {
            override suspend fun create(tracks: TrackSet) = throw Refused(LINE)
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, refused, f.lyrics)
        val p = runBlocking { picker(s) }
        assertEquals(SheetDone.Toast(LINE, ""), runBlocking { runPick(0, p, albumTarget(album()), s) })
    }

    @Test
    fun aNewPlaylistWithNoCardShowsCardNotAvailable() {
        val out = object : Playlists by f.playlists {
            override suspend fun create(tracks: TrackSet) = throw NoCard()
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, out, f.lyrics)
        val p = runBlocking { picker(s) }
        assertEquals(SheetDone.Toast(CARD_GONE, ""), runBlocking { runPick(0, p, albumTarget(album()), s) })
    }

    @Test
    fun aNewPlaylistFailingOutsideTheSeamFailsPlainly() {
        val e = IllegalStateException("broken")
        val broken = object : Playlists by f.playlists {
            override suspend fun create(tracks: TrackSet) = throw e
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, broken, f.lyrics)
        val p = runBlocking { picker(s) }
        assertSame(e, assertThrows(IllegalStateException::class.java) { runBlocking { runPick(0, p, albumTarget(album()), s) } })
    }

    @Test
    fun aBusyNewPlaylistOrDeleteShowsCardNotAvailable() {
        val busy = object : Playlists by f.playlists {
            override suspend fun create(tracks: TrackSet) = throw Busy()

            override suspend fun delete(id: Long) = throw Busy()
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, busy, f.lyrics)
        val p = runBlocking { picker(s) }
        assertEquals(SheetDone.Toast(CARD_GONE, ""), runBlocking { runPick(0, p, albumTarget(album()), s) })
        assertEquals(SheetDone.Toast(CARD_GONE, ""), runBlocking { confirmDelete(DELETE_PLAYLIST, lists().first { it.writable }.id, s) })
    }

    @Test
    fun aSongTargetAddsItsTrackAlone() {
        val s = song(5)
        val t = SheetTarget(SheetKind.Song, s.title, Listing.Songs, gen(), 5, s.id, Kind.Song, s.id)
        val p = runBlocking { picker(src) }
        val id = lists().first { it.title == DRIVE }.id
        val before = count(Listing.PlaylistTracks(id))
        runBlocking { runPick(p.items.indexOf(DRIVE), p, t, src) }
        assertEquals("a song target adds its track alone", before + 1, count(Listing.PlaylistTracks(id)))
    }

    @Test
    fun anEntryPickReachesTheViewsVerbWithItsTarget() {
        val id = lists().first { it.title == DRIVE }.id
        val m = ListViewModel(0L, Listing.PlaylistTracks(id))
        m.pane.geometry(RowSpec(PITCH, VIEW))
        runBlocking { src.viewed { v -> m.show(v, 0, ROWS) } }
        val gone = m.row(0)?.id
        val done = runBlocking { runEntry(REMOVE, m.target(0)!!, m, src, 0L) }
        assertEquals(SheetDone.Close, done)
        assertEquals(ROWS_DRIVE - 1, count(Listing.PlaylistTracks(id)))
        assertTrue(m.row(0)?.id != gone)
    }

    @Test
    fun aBusyListRemoveShowsCardNotAvailable() {
        val busy = object : Playlists by f.playlists {
            override suspend fun remove(id: Long, index: Int, revision: Long) = throw Busy()
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, busy, f.lyrics)
        val id = lists().first { it.title == DRIVE }.id
        val m = ListViewModel(0L, Listing.PlaylistTracks(id))
        m.pane.geometry(RowSpec(PITCH, VIEW))
        runBlocking { s.viewed { v -> m.show(v, 0, ROWS) } }
        val done = runBlocking { runEntry(REMOVE, m.target(0)!!, m, s, 0L) }
        assertEquals(SheetDone.Toast(CARD_GONE, ""), done)
    }

    @Test
    fun aRefusedListRemoveShowsTheDevicesLine() {
        val refused = object : Playlists by f.playlists {
            override suspend fun remove(id: Long, index: Int, revision: Long) = throw Refused(LINE)
        }
        val s = MusicSource(f.library, recording, f.quickplay, f.favorites, refused, f.lyrics)
        val id = lists().first { it.title == DRIVE }.id
        val m = ListViewModel(0L, Listing.PlaylistTracks(id))
        m.pane.geometry(RowSpec(PITCH, VIEW))
        runBlocking { s.viewed { v -> m.show(v, 0, ROWS) } }
        val done = runBlocking { runEntry(REMOVE, m.target(0)!!, m, s, 0L) }
        assertEquals(SheetDone.Toast(LINE, ""), done)
    }

    private val songT = SheetTarget(SheetKind.Song, "Rain Song", Listing.Songs, track = 4L, pin = Kind.Song, id = 4)
    private val albumT = SheetTarget(SheetKind.Album, "Blue Hours", Listing.AlbumTracks(2), pin = Kind.Album, id = 2)

    @Test
    fun theDeleteConfirmsAreTheUnitWordsWithKeepFocused() {
        val song = deleteAsk(songT, 0)
        assertEquals(DELETE_SONG_ASK, song.title)
        assertEquals(listOf("Rain Song", DELETE_SONG, KEEP_SONG), song.items)
        assertEquals(1, song.lead)
        assertEquals(2, song.focus)
        assertEquals(DELETE_SONG, song.yes)
        val one = deleteAsk(albumT, 1)
        assertEquals(DELETE_ALBUM_ASK, one.title)
        assertEquals(listOf("Blue Hours", "1 song", DELETE_ALBUM, KEEP_ALBUM), one.items)
        assertEquals(2, one.lead)
        assertEquals(3, one.focus)
        assertEquals(DELETE_ALBUM, one.yes)
        assertEquals(listOf("Blue Hours", "4 songs", DELETE_ALBUM, KEEP_ALBUM), deleteAsk(albumT, 4).items)
    }

    @Test
    fun eachDeleteOutcomeShowsTheUnitWords() {
        fun toast(t: SheetTarget, d: DeleteDone) = deleteToast(t, d)?.let { it.line to it.title }
        assertEquals(null, toast(songT, DeleteDone.Done))
        assertEquals(null, toast(albumT, DeleteDone.Done))
        assertEquals(SONG_NOT_DELETED to "Rain Song", toast(songT, DeleteDone.Refused))
        assertEquals(ALBUM_NOT_DELETED to "Blue Hours", toast(albumT, DeleteDone.Refused))
        assertEquals(SONG_NOT_DELETED to READ_ONLY, toast(songT, DeleteDone.ReadOnly))
        assertEquals(ALBUM_NOT_DELETED to READ_ONLY, toast(albumT, DeleteDone.ReadOnly))
        assertEquals(SONG_NOT_DELETED to CUE_PART, toast(songT, DeleteDone.Cue))
        assertEquals(ALBUM_NOT_DELETED to CUE_PART, toast(albumT, DeleteDone.Cue))
        assertEquals(CARD_GONE to "", toast(songT, DeleteDone.NoCard))
        assertEquals(CARD_GONE to "", toast(albumT, DeleteDone.NoCard))
        assertEquals("1 song not deleted" to "Blue Hours", toast(albumT, DeleteDone.Partly(1)))
        assertEquals("3 songs not deleted" to "Blue Hours", toast(albumT, DeleteDone.Partly(3)))
        assertEquals("a song batch is one path, so a partly song reads as refused", SONG_NOT_DELETED to "Rain Song", toast(songT, DeleteDone.Partly(1)))
        assertEquals("song not deleted" to "no answer from the dap", toast(songT, DeleteDone.Words("song not deleted", "no answer from the dap")))
        assertEquals("album not deleted" to "declined on the dap", toast(albumT, DeleteDone.Words("album not deleted", "declined on the dap")))
    }

    @Test
    fun aWordsOutcomeRefusesABlankLine() {
        assertThrows(IllegalArgumentException::class.java) { DeleteDone.Words("", "Rain Song") }
        assertThrows(IllegalArgumentException::class.java) { DeleteDone.Words(" ", "Rain Song") }
    }

    companion object {
        private const val LEASE_MS = 60_000L
        private const val RECENT_ROW = 3
        private const val NANOS = 1_000_000L
        private const val DRIVE = "Night Drive"
        private const val PITCH = 120
        private const val VIEW = 920
        private const val ROWS = 20
        private const val ROWS_DRIVE = 12
        private const val ALBUM_ROWS = 8
        private const val LINE = "card read only"
    }
}
