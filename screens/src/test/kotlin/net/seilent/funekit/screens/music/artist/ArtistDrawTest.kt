package net.seilent.funekit.screens.music.artist

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.artist.ARTIST_ALBUMS
import net.seilent.funekit.screens.music.artist.ARTIST_SONGS
import net.seilent.funekit.screens.music.artist.ArtistGeo
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.ArtistPaint
import net.seilent.funekit.screens.music.artist.RING_ADD
import net.seilent.funekit.screens.music.artist.RING_PLAY
import net.seilent.funekit.screens.music.artist.RING_SHUFFLE
import net.seilent.funekit.screens.music.artist.artistChrome
import net.seilent.funekit.screens.music.artist.artistDraw
import net.seilent.funekit.screens.music.artist.artistHeader
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.sheet.MusicScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ArtistDrawTest {
    private val sc = MusicScenes(FakeSet.Demo)
    private val s = sc.pageScale(sc.a105)

    private fun loaded(): ArtistModel = runBlocking {
        val v = sc.fake.library.view()
        val n = v.page(Listing.Artists, 0, 0).total
        val id = v.page(Listing.Artists, 0, n).rows.first { v.page(Listing.ArtistAlbums(it.id), 0, 0).total > 2 }.id
        val m = ArtistModel(0L, id)
        m.geometry(ArtistGeo(s))
        m.load(v)
        for (p in m.panes) {
            p.track.count(p.count, 0)
            if (p.count > 0) m.show(v, p.pivot, 0, p.count - 1)
        }
        m
    }

    @Test
    fun everyLookHoldsOverOneHundredRestFrames() {
        val m = loaded()
        m.listDown()
        m.panes[ARTIST_ALBUMS].focus.set(0, m.panes[ARTIST_ALBUMS].count, 0L, false)
        val rows = (0 until m.panes[ARTIST_ALBUMS].count).map { m.look(ARTIST_ALBUMS, it, REST, false) }
        val rings = (RING_PLAY..RING_SHUFFLE).map { m.ringLook(it, REST, false) }
        for (f in 1..FRAMES) {
            val t = REST + f * FRAME
            assertEquals(rows, (0 until m.panes[ARTIST_ALBUMS].count).map { m.look(ARTIST_ALBUMS, it, t, false) })
            assertEquals(rings, (RING_PLAY..RING_SHUFFLE).map { m.ringLook(it, t, false) })
        }
    }

    @Test
    fun aRingDownChangesThatRingAndTheRowsItTakesTheFocusFrom() {
        val m = loaded()
        m.listDown()
        val pn = m.panes[ARTIST_ALBUMS]
        pn.focus.set(1, pn.count, 0L, false)
        val rows = (0 until pn.count).map { m.look(ARTIST_ALBUMS, it, REST, false) }
        val rings = (RING_PLAY..RING_SHUFFLE).map { m.ringLook(it, REST, false) }
        m.ringDown(RING_ADD, REST)
        val after = (RING_PLAY..RING_SHUFFLE).map { m.ringLook(it, REST + FRAME, false) }
        assertNotEquals(rings[RING_ADD], after[RING_ADD])
        assertTrue(after[RING_ADD].focused)
        for (i in listOf(RING_PLAY, RING_SHUFFLE)) assertEquals(rings[i], after[i])
        assertTrue(m.onRings)
        val back = (0 until pn.count).map { m.look(ARTIST_ALBUMS, it, REST + FRAME, false) }
        assertTrue(rows[1].focused)
        assertFalse("the list focus goes to the rings", back[1].focused)
        for (i in 2 until pn.count) assertEquals(rows[i].focused, back[i].focused)
    }

    @Test
    fun aHiddenFocusDrawsNoRingFocus() {
        val m = loaded()
        assertTrue(m.onRings)
        assertTrue(m.ringLook(m.ring, REST, false).focused)
        assertFalse(m.ringLook(m.ring, REST, true).focused)
    }

    @Test
    fun theMovePaintDrawsTheRingFocusAsTheRestDoes() {
        val m = loaded()
        assertTrue("premise: the focus sits on the rings", m.onRings)
        val chrome = artistChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = artistDraw(m, s, sc.measurer, sc.fonts, sc.density, null, chrome, Ink.white, true)
        val paint = ArtistPaint(d, chrome, Ink.white)
        val moved = sc.draw(sc.a105) { with(paint) { rest(REST, null, "move") } }
        val shown = sc.draw(sc.a105) { with(d) { block(REST, false, null, "rest"); content(m.pivot, REST, false, false, null, "rest") } }
        val hidden = sc.draw(sc.a105) { with(d) { block(REST, true, null, "rest"); content(m.pivot, REST, true, false, null, "rest") } }
        assertFalse("premise: a hidden focus draws apart", shown.sameAs(hidden))
        assertTrue(moved.sameAs(shown))
    }

    @Test
    fun theLineBandRunsFromThePivotTopToTheNameBaselineLessItsDescent() {
        val m = loaded()
        val chrome = artistChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val paint = ArtistPaint(artistDraw(m, s, sc.measurer, sc.fonts, sc.density, null, chrome, Ink.white, true), chrome, Ink.white)
        assertEquals("premise: the A105 name baseline", A105_NAME_BASE, ArtistGeo(s).nameBase)
        assertEquals("premise: the A105 name px", A105_NAME_PX, rolePx(Roles.detailName, s))
        val e = requireNotNull(paint.lineBand())
        assertEquals(listOf(0, A105_PIVOT_TOP, s.widthPx, A105_BAND_END), listOf(e.x0, e.y0, e.x1, e.y1))
    }

    @Test
    fun theDrawMeasuresTheNameTheLabelAndEachPaneRow() {
        val m = loaded()
        val chrome = artistChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = artistDraw(m, s, sc.measurer, sc.fonts, sc.density, null, chrome, Ink.white, true)
        assertTrue("the label inks", d.label.width > 0f)
        assertTrue("premise: both panes hold rows", m.panes[ARTIST_ALBUMS].count > 0 && m.panes[ARTIST_SONGS].count > 0)
        assertEquals(s, d.geo.s)
    }

    @Test
    fun aPressedAlbumRowDrawsItsGlowPastTheTitleEnd() {
        val m = loaded()
        m.listDown()
        val pn = m.panes[ARTIST_ALBUMS]
        pn.focus.set(0, pn.count, REST, false)
        val chrome = artistChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = artistDraw(m, s, sc.measurer, sc.fonts, sc.density, null, chrome, Ink.white, true)
        val row = requireNotNull(pn.row(0))
        val at = REST + Tok.motion_pressGlow.toLong()
        fun shot() = sc.draw(sc.a105) { with(d) { albumRow(0, m.look(ARTIST_ALBUMS, 0, at, false), null, "row") } }
        val idle = shot()
        pn.press.down(0, REST)
        pn.glow.arm(0, REST)
        val lit = shot()
        val end = d.geo.textX + d.albumInk(row, false).title.first().width.toInt() + TEXT_SPILL
        val reach = px(Tok.pressGlowDilate, s.ppu) + px(Tok.pressGlow, s.ppu)
        val glowed = (end until end + reach - TEXT_SPILL).sumOf { x -> (0 until px(Tok.detailRow, s.stdPpu)).count { y -> lit.getPixel(x, y) != idle.getPixel(x, y) } }
        assertTrue("the glow lights px past the title end, $glowed", glowed > 0)
    }

    @Test
    fun theHeaderMorphSideIsTheChromeHeaderInItsCaseAndWeight() {
        val m = loaded()
        val chrome = artistChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = artistDraw(m, s, sc.measurer, sc.fonts, sc.density, null, chrome, Ink.white, true)
        val word = MusicPage.Hub.pivots[HUB_ARTISTS]
        fun measured(r: Role) = sc.measurer.word(word, r, roleStyle(r, s, sc.density, Ink.header, ppu = s.stdPpu), sc.fonts, sc.density, null)
        val page = measured(Roles.pageHeader)
        val artist = measured(artistHeader())
        assertTrue("premise: the page header role measures apart from the artist header, ${page.width} ${artist.width}", abs(page.width - artist.width) > PEN)
        assertEquals(artist.text, chrome.header.text)
        assertEquals(artist.width, chrome.header.width, PEN)
        assertEquals(chrome.header.text, d.headerSide.word.text)
        assertEquals(chrome.header.width, d.headerSide.word.width, PEN)
    }

    @Test
    fun aCoverlessAlbumRowsBoxCaptionStartsAtItsTileInsetFromTheRowArt() {
        val (m, row) = runBlocking {
            val v = sc.fake.library.view()
            val n = v.page(Listing.Artists, 0, 0).total
            val id = v.page(Listing.Artists, 0, n).rows.first { it.title == COVERLESS_ARTIST }.id
            val m = ArtistModel(0L, id)
            m.geometry(ArtistGeo(s))
            m.load(v)
            val pn = m.panes[ARTIST_ALBUMS]
            pn.track.count(pn.count, 0)
            m.show(v, ARTIST_ALBUMS, 0, pn.count - 1)
            m to requireNotNull((0 until pn.count).mapNotNull { pn.row(it) }.firstOrNull { it.art == null }) { "premise: the artist has a coverless album" }
        }
        val chrome = artistChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = artistDraw(m, s, sc.measurer, sc.fonts, sc.density, null, chrome, Ink.white, true)
        val box = requireNotNull(d.albumInk(row, false).box)
        assertEquals(A105_ART_X, d.geo.artX)
        assertEquals(A105_CAPTION_X, box.x(box.lines.first()), PEN)
    }

    companion object {
        private const val COVERLESS_ARTIST = "ヨルノオト"
        private const val A105_ART_X = 60
        private const val A105_CAPTION_X = 76.34375f
        private const val PEN = 1f / 64f
        private const val A105_PIVOT_TOP = 189
        private const val A105_NAME_BASE = 290
        private const val A105_NAME_PX = 39
        private const val A105_BAND_END = 299
        private const val REST = 10_000L
        private const val TEXT_SPILL = 2
        private const val FRAME = 16L
        private const val FRAMES = 100
    }
}
