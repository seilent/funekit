package net.seilent.funekit.screens.music.artist

import net.seilent.funekit.screens.music.overlay.GO_TO_ALBUM
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.sheetItems
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.scale
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.artist.ARTIST_ALBUMS
import net.seilent.funekit.screens.music.artist.ARTIST_SONGS
import net.seilent.funekit.screens.music.artist.AlbumInk
import net.seilent.funekit.screens.music.artist.ArtistDraw
import net.seilent.funekit.screens.music.artist.ArtistGeo
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.RING_ADD
import net.seilent.funekit.screens.music.artist.RING_PLAY
import net.seilent.funekit.screens.music.artist.RING_SHUFFLE
import net.seilent.funekit.screens.music.artist.artistAdd
import net.seilent.funekit.screens.music.artist.artistPlay
import net.seilent.funekit.screens.music.overlay.SONGS_ADDED
import net.seilent.funekit.screens.music.overlay.SONG_ADDED
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)

    private fun fake(set: FakeSet = FakeSet.Demo) = testFake(set)

    private fun artist(v: LibraryView, pick: (Int) -> Boolean): Long = runBlocking {
        val n = v.page(Listing.Artists, 0, 0).total
        val rows = v.page(Listing.Artists, 0, n).rows
        rows.first { pick(v.page(Listing.ArtistAlbums(it.id), 0, 0).total) }.id
    }

    private fun model(f: Fake, id: Long, load: Boolean = true): ArtistModel = runBlocking {
        val m = ArtistModel(0L, id)
        m.geometry(ArtistGeo(a105))
        if (load) {
            val v = f.library.view()
            m.load(v)
            for (p in m.panes) {
                p.track.count(p.count, 0)
                if (p.count > 0) m.show(v, p.pivot, 0, p.count - 1)
            }
        }
        m
    }

    @Test
    fun aSwitchCarriesTheLeavingPanesThumbUntilTheArrivingOneComesIn() {
        val f = fake()
        val m = model(f, artist(runBlocking { f.library.view() }) { it > 0 })
        m.fade.show(0L)
        val before = m.fade.at(SWITCH_AT)
        assertTrue("premise: the thumb is fading at the switch", before > 0f && before < 1f)
        assertTrue(m.switchTo(ARTIST_SONGS, SWITCH_AT))
        val t = SWITCH_AT + 1
        assertFalse("premise: the arriving thumb is not in yet", m.motion.thumbIn(t))
        assertEquals(before * m.motion.thumbAlpha(t), m.thumbAlpha(t), 1e-4f)
        assertEquals(1f, m.fade.at(t), 1e-4f)
    }

    @Test
    fun theRingActsPlayTheArtistsSongsInOrderOrShuffledAndTheAddRingQueuesThemWithItsToastWords() = runBlocking {
        val f = fake()
        val m = model(f, artist(f.library.view()) { it > 0 })
        val songs = f.library.view().page(Listing.ArtistTracks(m.id), 0, 0).total
        assertTrue("premise: the artist has more than one song", songs > 1)
        artistPlay(f.player, m, RING_PLAY)
        assertEquals(songs, f.player.state.value.queueSize)
        assertFalse(f.player.state.value.shuffle)
        artistPlay(f.player, m, RING_SHUFFLE)
        assertEquals(songs, f.player.state.value.queueSize)
        assertTrue(f.player.state.value.shuffle)
        assertEquals(SONGS_ADDED, artistAdd(f.player, m))
        assertEquals(2 * songs, f.player.state.value.queueSize)
    }

    @Test
    fun theAddRingOfAOneSongArtistToastsOneSong() = runBlocking {
        val f = fake()
        val v = f.library.view()
        val n = v.page(Listing.Artists, 0, 0).total
        val id = v.page(Listing.Artists, 0, n).rows.first { v.page(Listing.ArtistTracks(it.id), 0, 0).total == 1 }.id
        val m = model(f, id)
        assertEquals(SONG_ADDED, artistAdd(f.player, m))
        assertEquals(1, f.player.state.value.queueSize)
    }

    @Test
    fun anArtistAddAtTheQueueEndToastsTheSongsItAdded() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        val f = testFake(FakeSet.Demo, scheduler)
        val m = model(f, artist(f.library.view()) { it > 0 })
        val v = f.library.view()
        val songs = v.page(Listing.ArtistTracks(m.id), 0, 0).total
        assertTrue("premise: the artist has more than one song", songs > 1)
        val last = v.page(Listing.ArtistTracks(m.id), songs - 1, 1).rows.single()
        f.player.play(Listing.ArtistTracks(m.id), v.generation, songs - 1, checkNotNull(last.durationMs) - 500)
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()
        assertFalse("premise: the queue ended", f.player.state.value.playing)
        assertEquals(SONGS_ADDED, artistAdd(f.player, m))
        assertEquals(songs, f.player.state.value.queueSize)
    }

    @Test
    fun thePageViewIsTheGenerationTheArtistLoadedAt() = runBlocking {
        val f = fake(FakeSet.Main)
        val m = model(f, artist(f.library.view()) { it > 0 })
        val gen = m.gen
        f.library.removeRoot(f.library.roots.value.last().uri)
        assertTrue("premise: the rescan moves the generation", f.library.view().generation != gen)
        assertEquals(gen, m.pageView(MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)).generation)
    }

    @Test
    fun theA105BlockSitsOnTheBoardTokens() {
        val g = ArtistGeo(a105)
        assertEquals(listOf(135, 360, 585), (0..2).map { g.ringCx(it) })
        assertEquals(356, g.ringTop)
        assertEquals(150, g.ring)
        assertEquals(290, g.nameBase)
        assertEquals(608, g.boxTop)
        assertEquals(608, g.clipTop)
        assertEquals(372, g.albumPitch)
    }

    @Test
    fun aRingTakesADownInsideItsHalfBoxOfRingAndGap() {
        val g = ArtistGeo(a105)
        val y = g.ringCy.toFloat()
        assertEquals(2, g.ringAt((RING2_CX + HIT_IN) * U, y))
        assertEquals(-1, g.ringAt((RING2_CX + HIT_OUT) * U, y))
        assertEquals(1, g.ringAt((RING0_CX + HIT_OUT) * U, y))
        assertEquals(0, g.ringAt(RING0_CX * U, y))
    }

    @Test
    fun theSongsRowsTakeTheSixteenSixteenBoxTopAndTheAlbumsRowsTheDetailPitch() {
        val f = fake()
        val m = model(f, artist(runBlocking { f.library.view() }) { it >= 2 })
        val songs = m.panes[ARTIST_SONGS].track
        val albums = m.panes[ARTIST_ALBUMS].track
        val g = m.geo!!
        assertFalse(songs.whole)
        assertTrue("premise: four songs", m.panes[ARTIST_SONGS].count >= 4)
        assertEquals(listOf(608, 767, 927, 1086), (0..3).map { g.rowTop(songs, it) })
        assertEquals(listOf(608, 980), (0..1).map { g.rowTop(albums, it) })
    }

    @Test
    fun theAlbumTileIsRowZeroAtTheOpenBeforeAnyRowAndItsOwnRowAfter() {
        val f = fake()
        val id = artist(runBlocking { f.library.view() }) { it >= 2 }
        val cold = model(f, id, load = false)
        val e = cold.rectOf(ANY_ALBUM)!!
        assertEquals(listOf(60, 608, 408, 956), listOf(e.x0, e.y0, e.x1, e.y1))
        val m = model(f, id)
        val a0 = m.panes[ARTIST_ALBUMS].row(0)!!.id
        val a1 = m.panes[ARTIST_ALBUMS].row(1)!!.id
        val r1 = m.rectOf(a1)!!
        assertEquals(980, r1.y0)
        assertTrue(m.cell(a0))
        assertFalse(m.cell(a1))
        m.pivot = ARTIST_SONGS
        assertFalse(m.cell(a0))
        assertNull(m.rectOf(a0))
    }

    @Test
    fun theFocusOpensOnThePlayRingAndAnEmptyPaneKeepsItOnTheRings() {
        val f = fake()
        val m = model(f, artist(runBlocking { f.library.view() }) { it >= 1 })
        assertTrue(m.onRings)
        assertEquals(RING_PLAY, m.ring)
        m.listDown()
        assertFalse(m.onRings)
        m.ringDown(RING_ADD, 0L)
        assertTrue(m.onRings)
        assertEquals(RING_ADD, m.ring)
        m.listDown()
        m.panes[ARTIST_SONGS].total = 0
        m.switchTo(ARTIST_SONGS, 0L)
        assertTrue(m.onRings)
    }

    @Test
    fun theDetailLeaveBandsSplitTheRingsAtTheirCentreMidpoints() {
        val f = fake()
        val m = model(f, artist(runBlocking { f.library.view() }) { it >= 1 })
        val g = m.geo!!
        val side = WordSide("artists", 1f, 1f, 0f, 0f, -1, true, Word.none)
        val d = ArtistDraw(m, g, { Word.none }, Word.none, { _, _ -> AlbumInk(emptyList(), Word.none, null) }, { error("no ink") }, emptyList(), side, Ink.white, true)
        m.ringDown(RING_ADD, 0L)
        val b = d.leaveBands().bands
        val want = listOf(
            Part.NpLHead to listOf(0, 0, 720, 189),
            Part.NpLPivot to listOf(0, 189, 720, 262),
            Part.NpLName to listOf(0, 262, 720, 349),
            Part.NpLRing0 to listOf(0, 349, 247, 513),
            Part.NpLPress to listOf(247, 349, 472, 513),
            Part.NpLRing2 to listOf(472, 349, 720, 513),
            Part.NpLLabel to listOf(0, 513, 720, 608),
            Part.NpLLabel to listOf(0, 608, 720, 1280),
        )
        assertEquals(want, b.map { (e, p) -> p to listOf(e.x0, e.y0, e.x1, e.y1) })
    }

    @Test
    fun anArtistIsReadyOnceItsActivePaneHoldsItsFirstShownRow() = runBlocking {
        val f = fake()
        val id = artist(f.library.view()) { it > 1 }
        val m = model(f, id, load = false)
        assertFalse("no load yet", m.ready)
        val v = f.library.view()
        m.load(v)
        for (p in m.panes) p.track.count(p.count, 0)
        assertTrue("premise: the albums pane has rows", m.panes[ARTIST_ALBUMS].count > 0)
        assertFalse("the totals landed, no window yet", m.ready)
        m.show(v, ARTIST_ALBUMS, 0, m.panes[ARTIST_ALBUMS].count - 1)
        assertTrue(m.ready)
    }

    @Test
    fun anArtistNotInTheLibraryIsGoneWithBothPanesEmptyAndTheFocusOnTheRings() {
        val m = model(fake(), MISSING)
        m.listDown()
        runBlocking { m.load(fake().library.view()) }
        assertTrue(m.gone)
        assertEquals(0, m.panes[ARTIST_ALBUMS].count)
        assertEquals(0, m.panes[ARTIST_SONGS].count)
        assertTrue(m.onRings)
        assertTrue(m.ready)
    }

    @Test
    fun theArtistPageGivesSongAlbumAndRingTargets() {
        val f = fake()
        val id = artist(runBlocking { f.library.view() }) { it >= 2 }
        val m = model(f, id)
        val song = requireNotNull(m.target(ARTIST_SONGS, 1))
        assertEquals(SheetKind.Song, song.kind)
        assertEquals(Listing.ArtistTracks(id), song.listing)
        assertEquals(1, song.start)
        assertEquals(m.panes[ARTIST_SONGS].row(1)?.id, song.track)
        assertEquals(SheetFrom.Artist, song.from)
        assertTrue(GO_TO_ALBUM in sheetItems(song, false))
        val album = requireNotNull(m.target(ARTIST_ALBUMS, 0))
        assertEquals(SheetKind.Album, album.kind)
        assertEquals(Listing.AlbumTracks(m.panes[ARTIST_ALBUMS].row(0)!!.id), album.listing)
        assertEquals(SheetFrom.Artist, album.from)
        val ring = requireNotNull(m.ringTarget(0))
        assertEquals(SheetKind.Artist, ring.kind)
        assertEquals(m.name, ring.title)
        assertEquals(Listing.ArtistTracks(id), ring.listing)
        assertEquals(id, ring.id)
        assertNull("a ring band down off every ring holds nothing", m.ringTarget(-1))
    }

    companion object {
        private const val SWITCH_AT = 1750L
        private const val SDK = 35
        private const val W = 720
        private const val H = 1280
        private const val U = 3f
        private const val RING0_CX = 45f
        private const val RING2_CX = 195f
        private const val HIT_IN = 37.4f
        private const val HIT_OUT = 37.6f
        private const val ANY_ALBUM = 7L
        private const val MISSING = -99L
    }
}
