package net.seilent.funekit.screens.music.lists

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.Playlists
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.TrackSet
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.ViewKey
import net.seilent.funekit.screens.music.overlay.MOVE_DOWN
import net.seilent.funekit.screens.music.overlay.REMOVE
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListViewTest {
    private val f = testFake()
    private val src = MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)

    private fun lists(): List<Row> = runBlocking {
        val v = f.library.view()
        v.page(Listing.Playlists, 0, v.page(Listing.Playlists, 0, 0).total).rows
    }

    private fun drive(): Row = lists().first { it.title == DRIVE }

    private fun key(m: ListViewModel): ViewKey {
        val r = if (m.listing == Listing.Favorites) src.favorites.revision.value else src.playlists.revision.value
        return ViewKey(r, f.library.generation.value)
    }

    private fun model(listing: Listing, head: Row? = null): ListViewModel {
        val m = ListViewModel(0L, listing).also { it.head = head }
        m.pane.geometry(RowSpec(PITCH, VIEW))
        runBlocking { src.viewed { v -> m.show(v, 0, ROWS) } }
        m.note(key(m))
        return m
    }

    private fun refresh(m: ListViewModel) = runBlocking { src.viewed { v -> m.refresh(v, key(m), 0L) } }

    private fun pageRevision(l: Listing): Long = runBlocking { f.library.view().page(l, 0, 1).revision }

    @Test
    fun theRowsAreNumberedWithTheArtistAtTheLibraryAlpha() {
        val d = drive()
        val m = model(Listing.PlaylistTracks(d.id), d)
        val r = m.rowModel(0)!!
        assertEquals("1", r.number)
        assertEquals(m.row(0)?.title, r.primary)
        assertEquals(m.row(0)?.sub, r.secondary)
        assertTrue(r.libSub)
        assertEquals("12", m.rowModel(11)?.number)
        assertEquals("12 songs", m.count())
        assertEquals(Tok.type_listPrimary_alphaDim, m.look(1, AT).alpha)
        assertFalse(m.look(0, AT).focused)
    }

    @Test
    fun theCurrentTrackTakesTheAccent() {
        val d = drive()
        val m = model(Listing.PlaylistTracks(d.id), d)
        m.current = m.row(2)?.id
        assertTrue(m.rowModel(2)?.accent == true)
        assertFalse(m.rowModel(1)?.accent == true)
    }

    @Test
    fun anEmptyListShowsNothingHereAndNoCount() {
        val id = runBlocking { f.playlists.create(TrackSet.Ids(listOf(firstSong()))) }!!
        runBlocking { f.playlists.remove(id, 0, pageRevision(Listing.PlaylistTracks(id))) }
        val m = model(Listing.PlaylistTracks(id))
        assertTrue(m.empty())
        assertNull("no count on an empty list", m.count())
        assertTrue(m.ready)
    }

    @Test
    fun aRemoveClampsTheFocusWithNoMotion() {
        val d = drive()
        val m = model(Listing.PlaylistTracks(d.id), d)
        m.pane.focus.set(LAST, m.total, AT, false)
        runBlocking { f.playlists.remove(d.id, LAST, pageRevision(Listing.PlaylistTracks(d.id))) }
        assertTrue(m.stale(key(m)))
        refresh(m)
        assertEquals("11 songs", m.count())
        assertEquals("the focus clamps to the last row", LAST - 1, m.pane.focus.row)
        assertFalse("the focus lands with no motion", m.pane.focus.animating(0L))
    }

    @Test
    fun theTitleComesFromTheHeadWithNoLoad() {
        val d = drive()
        val m = ListViewModel(0L, Listing.PlaylistTracks(d.id)).also { it.head = Row(d.id, Kind.Playlist, DRIVE, "", null, writable = true) }
        assertEquals(DRIVE, m.title)
        assertTrue(m.writable)
        assertEquals("favorites", ListViewModel(0L, Listing.Favorites).title)
    }

    @Test
    fun aNewGenerationMakesTheViewStaleAndTheRefreshReadsIt() {
        val d = drive()
        val m = model(Listing.PlaylistTracks(d.id), d)
        val k = key(m)
        assertFalse(m.stale(k))
        val next = k.copy(generation = k.generation + 1)
        assertTrue("a new generation makes the view stale", m.stale(next))
        runBlocking { src.viewed { v -> m.refresh(v, next, 0L) } }
        assertFalse(m.stale(next))
        assertEquals("12 songs", m.count())
    }

    @Test
    fun theViewIsReadyOnceItsFirstWindowLandsAndOnAnEmptyList() {
        val d = drive()
        val m = ListViewModel(0L, Listing.PlaylistTracks(d.id))
        m.pane.geometry(RowSpec(PITCH, VIEW))
        assertFalse(m.ready)
        runBlocking { src.viewed { v -> m.show(v, 0, ROWS) } }
        assertTrue(m.ready)
    }

    @Test
    fun noCountShowsBeforeTheFirstWindow() {
        val m = ListViewModel(0L, Listing.PlaylistTracks(drive().id))
        assertNull("no count before the first window", m.count())
    }

    @Test
    fun anEmptyListIsReadyAndAViewScrolledPastItsWindowIsNot() {
        val id = runBlocking { f.playlists.create(TrackSet.Ids(listOf(firstSong()))) }!!
        runBlocking { f.playlists.remove(id, 0, pageRevision(Listing.PlaylistTracks(id))) }
        assertTrue("an empty list is ready", model(Listing.PlaylistTracks(id)).ready)
        val big = runBlocking { f.playlists.create(TrackSet.Listed(Listing.Songs, f.library.generation.value)) }!!
        val m = model(Listing.PlaylistTracks(big))
        assertTrue("premise: the first window is shown", m.ready)
        m.track.at(FAR * PITCH)
        assertFalse("a view scrolled past its window is not ready", m.ready)
    }

    @Test
    fun entryVerbsRemoveAndMoveAtTheTargetsRevision() {
        val d = drive()
        val m = model(Listing.PlaylistTracks(d.id), d)
        val moved = m.row(2)?.id
        val t = m.target(2)!!
        runBlocking { m.verb(MOVE_DOWN, t, src, 0L) }
        assertFalse("the verb's own edit leaves the view not stale", m.stale(key(m)))
        assertEquals(moved, m.row(3)?.id)
        assertEquals(3, m.pane.focus.row)
        runBlocking { m.verb(REMOVE, m.target(LAST)!!, src, 0L) }
        assertEquals("a remove of the last row focuses the clamped row", LAST - 1, m.pane.focus.row)
        val gone = m.row(0)?.id
        runBlocking { m.verb(REMOVE, m.target(0)!!, src, 0L) }
        assertEquals("10 songs", m.count())
        assertTrue(m.row(0)?.id != gone)
        assertEquals(0, m.pane.focus.row)
    }

    @Test
    fun aFavoritesEntryVerbEditsTheFavorites() {
        val m = model(Listing.Favorites)
        assertEquals("5 songs", m.count())
        runBlocking { m.verb(REMOVE, m.target(0)!!, src, 0L) }
        assertEquals("4 songs", m.count())
    }

    @Test
    fun aStaleEntryVerbDoesNothing() {
        val d = drive()
        val m = model(Listing.PlaylistTracks(d.id), d)
        val t = m.target(0)!!
        runBlocking { f.playlists.remove(d.id, LAST, pageRevision(Listing.PlaylistTracks(d.id))) }
        refresh(m)
        val first = m.row(0)?.id
        runBlocking { m.verb(REMOVE, t, src, 0L) }
        assertEquals("a verb at an old revision does nothing", "11 songs", m.count())
        assertEquals(first, m.row(0)?.id)
    }

    private fun firstSong(): Long = runBlocking { f.library.view().page(Listing.Songs, 0, 1).rows.first().id }

    @Test
    fun aVerbWhoseEditTheViewAlreadyTookRefreshesNoMore() {
        val d = drive()
        var reads = 0
        lateinit var m: ListViewModel
        val counted = object : Library by f.library {
            override fun view(): LibraryView = counting(f.library.view())

            override fun view(generation: Long): LibraryView = counting(f.library.view(generation))

            fun counting(v: LibraryView): LibraryView = object : LibraryView by v {
                override suspend fun page(listing: Listing, offset: Int, count: Int): Page {
                    if (listing == Listing.PlaylistTracks(d.id)) reads++
                    return v.page(listing, offset, count)
                }
            }
        }
        val first = object : Playlists by f.playlists {
            override suspend fun move(id: Long, from: Int, to: Int, revision: Long) {
                f.playlists.move(id, from, to, revision)
                src.viewed { v -> m.refresh(v, key(m), 0L) }
            }
        }
        val s = MusicSource(counted, f.player, f.quickplay, f.favorites, first, f.lyrics)
        m = model(Listing.PlaylistTracks(d.id), d)
        val moved = m.row(2)?.id
        reads = 0
        runBlocking { m.verb(MOVE_DOWN, m.target(2)!!, s, 0L) }
        assertTrue("premise: the collector took the edit", !m.stale(key(m)) && m.row(3)?.id == moved)
        assertEquals("the verb takes no second refresh", 0, reads)
    }

    private companion object {
        const val PITCH = 120
        const val VIEW = 920
        const val ROWS = 20
        const val LAST = 11
        const val AT = 1_000L
        const val DRIVE = "Night Drive"
        const val FAR = 600
    }
}
