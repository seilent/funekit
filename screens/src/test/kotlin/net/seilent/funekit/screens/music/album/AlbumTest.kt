package net.seilent.funekit.screens.music.album

import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.screens.music.overlay.GO_TO_ALBUM
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertNull
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
import net.seilent.funekit.kit.scrollInto
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.bandsOrRest
import net.seilent.funekit.kit.stack.first
import net.seilent.funekit.kit.stack.rowTop
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
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

    private fun fake(set: FakeSet) = testFake(set)

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
        assertEquals(756, g.ringTop)
        assertEquals(942, g.top)
        assertEquals(120, g.pitch)
        assertEquals(531, g.above)
        assertEquals(660, Rows.right(a105))
        assertEquals(36, px(Tok.gutter, a105.ppu))
        val l = AlbumGeo(page(A105_W, A105_H, true))
        assertEquals(942, l.top)
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
    fun theRingRowSitsAtTheRingCentresOnTheRowsTop() {
        val m = loaded(fake(FakeSet.Main), COBALT)
        val g = m.geo!!
        assertEquals(listOf(756, 942, 150, 89, 356, 831), listOf(g.ringTop, g.top, g.ring, g.ringCx(0), g.ringCx(1), g.ringCy(0)))
        val b = g.ringBand
        assertEquals(listOf(0, 411, 720, 942), listOf(b.x0, b.y0, b.x1, b.y1))
        val y = g.ringCy(0).toFloat()
        for ((x, want) in listOf(1 to 0, 66 to 0, 68 to -1, 80 to -1, 82 to 1, 155 to 1, 157 to -1, 230 to -1)) {
            assertEquals("x $x u", want, m.ringAt(px(x.toFloat(), a105.stdPpu).toFloat(), y))
        }
        assertEquals(listOf(-1, 0, 0, -1), listOf(755f, 756f, 941f, 942f).map { m.ringAt(RING_X, it) })
        assertTrue("the page opens on the rings", m.onRings)
        assertEquals(ALBUM_PLAY, m.ring)
        val look = m.look(0, 0L, true)
        assertEquals("the rows dim while the rings hold the focus", Tok.type_listPrimary_alphaDim, look.alpha)
        assertFalse(look.focused)
    }

    @Test
    fun anEmptyAlbumHasNoRingBand() {
        val g = AlbumGeo(a105, rings = false)
        assertEquals(listOf(756, 756, 345), listOf(g.ringTop, g.top, g.above))
        assertEquals(0, g.ringAlpha(0))
        assertEquals(-1, g.ringAt(RING_X, g.ringCy(0).toFloat(), 0, 0))
        val f = fake(FakeSet.Main)
        val v = f.library.view()
        val empty = object : LibraryView by v {
            override suspend fun page(listing: Listing, offset: Int, count: Int): Page =
                if (listing is Listing.AlbumTracks) Page(v.generation, offset, 0, emptyList()) else v.page(listing, offset, count)
        }
        val m = AlbumModel(0L, albumId(v, COBALT))
        runBlocking { m.load(empty) }
        assertFalse("an album with no tracks opens off the rings", m.onRings)
    }

    @Test
    fun theRingRowFadesOverItsLift() {
        val m = loaded(fake(FakeSet.Main), COBALT)
        val g = m.geo!!
        val at = listOf(0, 12, 32, 62, 63, 115, 155, 177, 200).map { o ->
            m.track.at(px(o.toFloat(), a105.ppu))
            listOf(g.ringAlpha(m.track.lift), g.ringCy(m.track.q), g.rowsTopPx(m.track.lift), m.track.lift)
        }
        assertEquals(listOf(256, 207, 125, 4, 0, 0, 0, 0, 0), at.map { it[0] })
        assertEquals(listOf(831, 795, 735, 645, 642, 486, 366, 300, 231), at.map { it[1] })
        assertEquals(listOf(756, 720, 660, 570, 567, 411, 411, 411, 411), at.map { it[2] })
        assertEquals(listOf(0, 36, 96, 186, 189, 345, 465, 531, 531), at.map { it[3] })
        assertEquals(189, g.fadeEnd)
        m.track.at(px(62f, a105.ppu))
        m.fadeFocus()
        assertTrue("a ring row still showing keeps the focus", m.onRings)
        assertEquals(0, m.ringAt(RING_X, g.rowsTopPx(m.track.lift).toFloat()))
        m.track.at(px(63f, a105.ppu))
        m.fadeFocus()
        assertFalse("the faded ring row gives the focus to the rows", m.onRings)
        assertEquals(-1, m.ringAt(RING_X, g.rowsTopPx(m.track.lift).toFloat()))
        assertEquals(Tok.type_listPrimary_alpha, m.look(0, 0L, true).alpha)
    }

    @Test
    fun theFocusFadeReadsTheScrollTargetOnEveryStep() {
        val m = loaded(fake(FakeSet.Main), COBALT)
        val g = m.geo!!
        m.track.at(px(63f, a105.ppu))
        assertEquals("premise: the ring row has faded", 0, g.ringAlpha(m.track.lift))
        m.aimTo(0)
        m.tick(0L, false, false)
        assertTrue("an aim back above the fade keeps the rings", m.onRings)
        m.aimed = m.aims
        m.tick(0L, false, false)
        assertFalse("the step after the aim gives the focus to the rows", m.onRings)
    }

    @Test
    fun aScrollToTheFirstRowDocksAtTheCollapse() {
        val m = loaded(fake(FakeSet.Main), COBALT)
        val g = m.geo!!
        m.track.at(DEEP_Q)
        assertEquals(g.above, m.intoView(0))
        assertEquals("the plain rule goes to the very top", 0, scrollInto(0, g.pitch, m.track))
        assertEquals(g.pitch + g.above, m.intoView(1))
        m.track.at(INSIDE_Q)
        assertEquals(INSIDE_Q, m.intoView(0))
        m.track.at(0)
        assertEquals(0, m.intoView(0))
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
        assertEquals(listOf(listOf(708, 942, 720, 1074), listOf(708, 842, 720, 1019), listOf(708, 411, 720, 928), listOf(708, 763, 720, 1280)), ys)
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
        assertEquals(listOf(Part.NpLArtist, Part.NpLArtist, Part.NpLRow1, Part.NpLRows), bands.map { it.second })
        val e = bands.map { it.first }
        assertEquals(listOf(0, 189, 720, MID), listOf(e[0].x0, e[0].y0, e[0].x1, e[0].y1))
        assertEquals(listOf(0, 756, 720, 942), listOf(e[1].x0, e[1].y0, e[1].x1, e[1].y1))
        assertEquals(listOf(942, 1062), listOf(e[2].y0, e[2].y1))
        assertEquals(listOf(1062, 1280), listOf(e[3].y0, e[3].y1))
        m.track.at(FAR_Q)
        val far = paint(m, artist, album)
        assertEquals(411, far.top)
        assertEquals(0, far.above)
        assertEquals(396, far.rowTop(2))
        assertEquals(2, far.first())
        assertEquals(411, far.bandsOrRest().y[0])
        assertEquals(216, far.npArtTop)
        val farBands = far.npLeaveBands()!!.bands
        assertEquals("the ring band is empty once the block is docked", listOf(Part.NpLArtist, Part.NpLRow1, Part.NpLRows), farBands.map { it.second })
        val fb = farBands.map { it.first }
        assertEquals("the first drawn row at q 786 is row 2", 2, m.track.drawFirst)
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
        val scheduler = TestCoroutineScheduler()
        val f = testFake(FakeSet.Main, scheduler)
        val m = loaded(f, SALT_LAKE)
        val rows = m.count
        val uri = f.library.roots.value.last().uri
        runBlocking { f.library.removeRoot(uri) }
        assertFalse(runBlocking { m.migrate(f.library.view(), 0L) })
        assertTrue(m.gone)
        val g0 = f.library.generation.value
        runBlocking { f.library.addRoot(uri) }
        scheduler.advanceUntilIdle()
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
        private const val FAR_Q = 786
        private const val RING_X = 90f
        private const val DEEP_Q = 1000
        private const val INSIDE_Q = 300
        private const val ARTIST_TOP = -41.3f
        private const val ARTIST_BOTTOM = 12.2f
        private const val ALBUM_TOP = -41.3f
        private const val ALBUM_BOTTOM = 12.2f
        private const val MID = 280
    }
}
