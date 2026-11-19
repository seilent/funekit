package net.seilent.funekit.screens.music.album

import net.seilent.funekit.screens.music.overlay.GO_TO_ALBUM
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.sheetItems
import org.junit.Assert.assertNull
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fxU
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.bandsOrRest
import net.seilent.funekit.kit.stack.first
import net.seilent.funekit.kit.stack.rowTop
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.album.AlbumDraw
import net.seilent.funekit.screens.music.album.AlbumGeo
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.album.AlbumPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTest {
    private val none = Insets(0, 0, 0, 0)

    private fun page(w: Int, h: Int, large: Boolean = false): Scale = scale(Window(w, h, SDK, none, none, none, none, none), Nav.None, Surface.Page, large)

    private val a105 = page(A105_W, A105_H)

    private fun fake(set: FakeSet) = Fake(CoroutineScope(EmptyCoroutineContext), { 0L }, set, 0L, LEASE_MS)

    private fun albumId(v: LibraryView, title: String): Long = runBlocking {
        val n = v.page(Listing.Albums, 0, 0).total
        v.page(Listing.Albums, 0, n).rows.first { it.title == title }.id
    }

    private fun loaded(f: Fake, title: String, s: Scale = a105): AlbumModel = runBlocking {
        val v = f.library.view()
        val m = AlbumModel(0L, albumId(v, title))
        m.geometry(AlbumGeo(s))
        m.load(v)
        m.show(v, 0, m.total - 1)
        m.track.count(m.count, 0)
        m
    }

    private fun lift(g: AlbumGeo, offU: Int): Int = minOf(px(offU.toFloat(), g.s.ppu), g.above)

    @Test
    fun theA105CollapseTableFollowsFuneOs() {
        val g = AlbumGeo(a105)
        val offs = listOf(0, 31, 63, 67, 89, 115, 215)
        assertEquals(listOf(756, 663, 567, 555, 489, 411, 411), offs.map { g.rowsTopPx(lift(g, it)) })
        assertEquals(listOf(348, 255, 159, 159, 159, 159, 159), offs.map { g.artRect(lift(g, it)).w })
        assertEquals(listOf(372, 372, 372, 360, 294, 216, 216), offs.map { g.artRect(lift(g, it)).y0 })
        val xs = offs.map { fxU(g.textX(g.liftU(lift(g, it)))) }
        val want = listOf(16f, 16f, 16f, 41.24f, 77f, 77f, 77f)
        for (i in xs.indices) assertEquals("text x at $i", want[i], xs[i], TEXT_X_TOL)
        assertEquals(g.artX, g.artRect(0).x0)
        assertEquals(48, g.artX)
    }

    @Test
    fun theTextXAtDockFourTakesTheRiseOfEachBoard() {
        val v1 = AlbumGeo(page(V1_W, V1_H))
        val r1 = AlbumGeo(page(R1_W, R1_H))
        assertEquals(43.11f, fxU(v1.textX(v1.liftU(lift(v1, DOCK_FOUR)))), TEXT_X_TOL)
        assertEquals(41.68f, fxU(r1.textX(r1.liftU(lift(r1, DOCK_FOUR)))), TEXT_X_TOL)
    }

    @Test
    fun theListTopPitchAndCollapseBandKeepTheA105PxAtBothSizes() {
        val g = AlbumGeo(a105)
        assertEquals(756, g.top)
        assertEquals(120, g.pitch)
        assertEquals(345, g.above)
        assertEquals(660, Rows.right(a105))
        assertEquals(36, px(Tok.gutter, a105.ppu))
        val l = AlbumGeo(page(A105_W, A105_H, true))
        assertEquals(756, l.top)
        assertEquals(144, l.pitch)
        assertTrue(l.spec().collapse)
        assertEquals(l.above, l.spec().above)
    }

    private fun paint(m: AlbumModel, artist: Word, album: Word): AlbumPaint {
        val g = m.geo!!
        val chrome = Chrome(g.s, Word.none, emptyList())
        val side = WordSide("albums", 1f, 1f, 0f, 0f, -1, true, Word.none)
        val d = AlbumDraw(m, g, { error("no ink in a jvm test") }, { t, _ -> if (t == m.head?.sub) artist else album }, side, Ink.white, true)
        return AlbumPaint(d, chrome, d.accent)
    }

    @Test
    fun theThumbRunsOverTheListTopLessTheLiftAsFuneOsThumbOf() {
        val m = loaded(fake(FakeSet.Main), COBALT)
        val g = AlbumGeo(a105)
        assertEquals(COBALT_ROWS, m.count)
        val ys = listOf(0, LIFT_Q, g.above, Int.MAX_VALUE).map { q ->
            m.track.at(q)
            g.thumb(m.track)!!.let { listOf(it.x0, it.y0, it.x1, it.y1) }
        }
        assertEquals(listOf(listOf(708, 756, 720, 982), listOf(708, 656, 720, 951), listOf(708, 411, 720, 928), listOf(708, 763, 720, 1280)), ys)
    }

    @Test
    fun aListThatScrollsNoFurtherThanItsLiftHasNoThumb() {
        val m = loaded(fake(FakeSet.Main), COBALT)
        val g = AlbumGeo(a105)
        m.track.resize(g.pitch, m.track.topOf(m.count) - g.above, g.above)
        assertTrue("premise: the list scrolls by its lift, ${m.track.max} ${g.above}", m.track.max in 1..g.above)
        assertEquals(null, g.thumb(m.track))
    }

    @Test
    fun thePageViewIsTheGenerationTheAlbumLoadedAt() = runBlocking {
        val f = fake(FakeSet.Main)
        val m = loaded(f, COBALT)
        val gen = m.gen
        f.library.removeRoot(f.library.roots.value.last().uri)
        assertTrue("premise: the rescan moves the generation", f.library.view().generation != gen)
        assertEquals(gen, m.pageView(MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)).generation)
    }

    @Test
    fun theMoveSideStartsAtTheMovingRowsTopAndItsBandsFollowTheLift() {
        val f = fake(FakeSet.Main)
        val m = loaded(f, COBALT)
        assertEquals(COBALT_ROWS, m.count)
        m.head = runBlocking { f.library.view().let { v -> v.page(Listing.Albums, v.indexOf(Listing.Albums, Kind.Album, m.id)!!, 1).rows.first() } }
        val artist = Word("a", emptyList(), 0f, 0f, 0f, ARTIST_TOP, ARTIST_BOTTOM)
        val album = Word("b", emptyList(), 0f, 0f, 0f, ALBUM_TOP, ALBUM_BOTTOM)
        val rest = paint(m, artist, album)
        assertEquals(756, rest.top)
        assertEquals(756, rest.bandsOrRest().y[0])
        assertEquals(372, rest.npArtTop)
        val bands = rest.npLeaveBands()!!.bands
        assertEquals(listOf(Part.NpLArtist, Part.NpLRow1, Part.NpLRows), bands.map { it.second })
        val e = bands.map { it.first }
        assertEquals(listOf(0, 189, 720, MID), listOf(e[0].x0, e[0].y0, e[0].x1, e[0].y1))
        assertEquals(listOf(756, 876), listOf(e[1].y0, e[1].y1))
        assertEquals(listOf(876, 1280), listOf(e[2].y0, e[2].y1))
        m.track.at(FAR_Q)
        val far = paint(m, artist, album)
        assertEquals(411, far.top)
        assertEquals(0, far.above)
        assertEquals(396, far.rowTop(2))
        assertEquals(2, far.first())
        assertEquals(411, far.bandsOrRest().y[0])
        assertEquals(216, far.npArtTop)
        val fb = far.npLeaveBands()!!.bands.map { it.first }
        assertEquals("the first drawn row at q 600 is row 2", 2, m.track.drawFirst)
        assertEquals(listOf(411, 516), listOf(fb[1].y0, fb[1].y1))
        assertEquals(listOf(516, 1280), listOf(fb[2].y0, fb[2].y1))
    }

    @Test
    fun anAlbumOpenedOnItsPlayingTrackShowsTheAccentOnThatRow() = runBlocking {
        val f = fake(FakeSet.Main)
        val v = f.library.view()
        val m = AlbumModel(0L, albumId(v, COBALT))
        m.geometry(AlbumGeo(a105))
        m.load(v)
        val track = v.page(Listing.AlbumTracks(m.id), FOCUS, 1).rows.first().id
        m.play(PlayerState(track = track))
        m.show(v, 0, m.total - 1)
        m.track.count(m.count, 0)
        assertTrue(m.rowModel(FOCUS)!!.accent)
        assertFalse(m.rowModel(0)!!.accent)
    }

    @Test
    fun anAlbumOfARootRemovedThenAddedBackComesBack() {
        val f = fake(FakeSet.Main)
        val m = loaded(f, SALT_LAKE)
        val rows = m.count
        val uri = f.library.roots.value.last().uri
        runBlocking { f.library.removeRoot(uri) }
        assertFalse(runBlocking { m.migrate(f.library.view(), 0L) })
        assertTrue(m.gone)
        val g0 = f.library.generation.value
        runBlocking { f.library.addRoot(uri) }
        val end = System.currentTimeMillis() + SCAN_WAIT_MS
        while (f.library.generation.value == g0 && System.currentTimeMillis() < end) Thread.sleep(SCAN_STEP_MS)
        assertTrue("premise: the scan of the root added back published", f.library.generation.value != g0)
        val v = runBlocking { f.library.view() }
        assertTrue(runBlocking { m.migrate(v, 0L) })
        assertFalse(m.gone)
        assertEquals(rows, m.count)
        assertEquals(v.generation, m.gen)
    }

    @Test
    fun aRescanKeepsTheFocusedTrackAndAnAlbumOfTheGoneRootGoesToNone() {
        val f = fake(FakeSet.Main)
        val kept = loaded(f, COBALT)
        kept.focus.set(FOCUS, kept.count, 0L, false)
        val key = kept.row(FOCUS)!!.id
        assertTrue("premise: the list scrolls", kept.track.max > 0)
        val off = kept.track.topOf(FOCUS) - kept.track.q
        val lost = loaded(f, SALT_LAKE)
        assertEquals(SALT_LAKE_ID, lost.id)
        runBlocking { f.library.removeRoot(f.library.roots.value.last().uri) }
        val v = runBlocking { f.library.view() }
        assertTrue(runBlocking { kept.migrate(v, 0L) })
        assertEquals(key, kept.row(kept.focus.row)!!.id)
        assertEquals(off, kept.track.topOf(kept.focus.row) - kept.track.q)
        assertEquals(v.generation, kept.gen)
        assertFalse(runBlocking { lost.migrate(v, 0L) })
        assertTrue(lost.gone)
        assertEquals(0, lost.count)
        assertTrue(lost.ready)
    }

    @Test
    fun anAlbumIsReadyOnceItsWindowHoldsTheFirstShownRow() = runBlocking {
        val f = fake(FakeSet.Main)
        val v = f.library.view()
        val m = AlbumModel(0L, albumId(v, COBALT))
        m.geometry(AlbumGeo(a105))
        assertFalse(m.ready)
        m.load(v)
        assertFalse(m.ready)
        m.show(v, 0, 0)
        m.track.count(m.count, 0)
        assertTrue(m.ready)
    }

    @Test
    fun aHoldOnATrackGivesASongSheetOfTheAlbumFromItsRowWithNoGoToAlbum() {
        val m = loaded(fake(FakeSet.Main), COBALT)
        val i = 2
        val t = requireNotNull(m.target(i))
        assertEquals(SheetKind.Song, t.kind)
        assertEquals(Listing.AlbumTracks(m.id), t.listing)
        assertEquals(i, t.start)
        assertEquals(m.row(i)?.id, t.track)
        assertEquals(m.gen, t.generation)
        assertEquals(SheetFrom.Album, t.from)
        assertFalse(GO_TO_ALBUM in sheetItems(t, false))
        assertNull(m.target(m.count))
    }

    companion object {
        private const val SDK = 35
        private const val LEASE_MS = 60_000L
        private const val A105_W = 720
        private const val A105_H = 1280
        private const val R1_W = 480
        private const val R1_H = 800
        private const val V1_W = 240
        private const val V1_H = 320
        private const val DOCK_FOUR = 67
        private const val TEXT_X_TOL = 0.01f
        private const val COBALT = "Cobalt Rooms"
        private const val COBALT_ROWS = 13
        private const val LIFT_Q = 100
        private const val SALT_LAKE = "Salt Lake Postcards"
        private const val SALT_LAKE_ID = 20L
        private const val FOCUS = 3
        private const val FAR_Q = 600
        private const val ARTIST_TOP = -41.3f
        private const val ARTIST_BOTTOM = 12.2f
        private const val ALBUM_TOP = -41.3f
        private const val ALBUM_BOTTOM = 12.2f
        private const val MID = 280
        private const val SCAN_WAIT_MS = 5_000L
        private const val SCAN_STEP_MS = 10L
    }
}
