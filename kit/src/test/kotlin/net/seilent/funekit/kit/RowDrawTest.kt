package net.seilent.funekit.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.scale
import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RowDrawTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val bg = 0x11

    private class Shot(val px: IntArray, val w: Int) {
        fun red(x: Int, y: Int) = px[y * w + x] shr 16 and 0xFF
        fun rgb(x: Int, y: Int) = px[y * w + x] and 0xFFFFFF
    }

    private fun shot(w: Int, h: Int, root: Color = Ink.bg, draw: DrawScope.() -> Unit): Shot {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(root, Offset.Zero, size)
            draw()
        }
        val px = IntArray(w * h)
        img.readPixels(px)
        return Shot(px, w)
    }

    private fun row(
        r: RowModel,
        focused: Boolean = false,
        press: Float = 1f,
        glow: RowGlow? = null,
        accent: Color = Ink.white,
        alpha: Float = 1f,
        focusShown: Boolean = false,
        ringLit: Float = 0f,
    ) = shot(720, Rows.pitch(a105, r)) {
        listRow(rowInk(measurer, fonts, r, a105, unitDensity, false, null), a105, alpha, focused, press, accent, false, glow, focusShown = focusShown, ringLit = ringLit)
    }

    private fun near(want: Int, got: Int, tol: Int = 3) = assertTrue("want $want got $got", abs(want - got) <= tol)

    private fun over(alpha: Float) = Math.round(bg + (0xFF - bg) * alpha)

    @Test
    fun aDividerRowDrawsItsLetterTileAndLetter() {
        val s = row(RowModel(kind = RowKind.Divider, primary = "a"))
        near(over(0.4f), s.red(100, 7))
        near(over(0.4f), s.red(61, 60))
        near(over(0.4f), s.red(166, 60))
        near(bg, s.red(59, 60), 0)
        near(bg, s.red(80, 30), 0)
        var ink = 0
        for (y in 60 until 90) for (x in 115 until 150) ink = maxOf(ink, s.red(x, y))
        assertTrue("letter ink $ink", ink > 200)
        near(bg, s.red(156, 60), 0)
    }

    @Test
    fun aFocusedDividerDrawsItsTileBorderTwoUnitsWideAtFullAlpha() {
        val s = row(RowModel(kind = RowKind.Divider, primary = "a"), focused = true, focusShown = true)
        near(0xFF, s.red(100, 6), 0)
        near(0xFF, s.red(100, 11), 0)
        near(bg, s.red(100, 12), 0)
        near(0xFF, s.red(60, 60), 0)
        near(0xFF, s.red(65, 60), 0)
        near(bg, s.red(66, 60), 0)
        near(0xFF, s.red(167, 60), 0)
        near(0xFF, s.red(162, 60), 0)
        near(bg, s.red(161, 60), 0)
        near(bg, s.red(59, 60), 0)
    }

    @Test
    fun noFocusedRowDrawsAColumnWhereTheFocusBarWas() {
        for (r in listOf(RowModel(primary = "Hits"), RowModel(ring = true, primary = "Song"), RowModel(kind = RowKind.Divider, primary = "a"))) {
            near(bg, most(row(r, focused = true, focusShown = true), 36, 42, 0, 120), 0)
        }
    }

    @Test
    fun aPlayingTitleTakesTheRowAlphaWhileTheFocusIsShownUnlessItsRowHasARing() {
        val playing = RowModel(primary = "Hits", accent = true)
        near(over(0.85f), most(row(playing, alpha = 0.85f, focusShown = true), 60, 200, 40, 80), 8)
        near(0xFF, most(row(playing, alpha = 0.85f, focusShown = false), 60, 200, 40, 80))
        near(0xFF, most(row(RowModel(ring = true, primary = "Song", accent = true), alpha = 0.85f, focusShown = true), 132, 400, 30, 90))
        near(over(0.85f), most(row(RowModel(primary = "Hits"), alpha = 0.85f, focusShown = false), 60, 200, 40, 80), 8)
    }

    @Test
    fun aHiddenFocusDrawsTheDividerIdleAndThePlayingTitleWhole() {
        val divider = row(RowModel(kind = RowKind.Divider, primary = "a"), focused = false, focusShown = false)
        near(over(0.4f), divider.red(100, 7))
        near(bg, divider.red(100, 9), 0)
        near(bg, divider.red(64, 60), 0)
        val playing = row(RowModel(primary = "Hits", accent = true), focused = false, alpha = 0.85f, focusShown = false)
        near(0xFF, most(playing, 60, 200, 40, 80))
    }

    @Test
    fun theF15FrameIsWhiteAtItsEdges() {
        val s = shot(720, 400) {
            letterTile(Edges(300, 100, 480, 280), 3, 0.4f)
            pressFrame(Edges(300, 100, 480, 280), a105)
        }
        near(bg, s.red(295, 190), 0)
        near(0xFF, s.red(296, 190), 0)
        near(0xFF, s.red(313, 190), 0)
        near(bg, s.red(314, 190), 0)
    }

    @Test
    fun aLitFrameGlowsInsideItsTileAndTheRingTakesItsAlpha() {
        val t = Edges(300, 100, 480, 280)
        val ring = Rows.pressFrame(t, a105)
        val inner = ring.filter { it.h > it.w }.minBy { it.x0 }.x1
        val top = ring.minOf { it.y0 }
        val half = shot(720, 400) { pressFrame(t, a105, 0f, 0.5f) }
        near(over(0.5f), half.red(305, 190), 1)
        near(bg, half.red(inner + 1, 190), 0)
        val lit = shot(720, 400) { pressFrame(t, a105, 1f, 1f) }
        near(0xFF, lit.red(305, 190), 0)
        assertTrue(lit.red(inner + 1, 190) > bg)
        near(bg, lit.red(390, top - 2), 0)
    }

    @Test
    fun theRowRingIsIdleAtMidAlphaAndFocusedAtFull() {
        near(over(0.6f), row(RowModel(ring = true, primary = "Song")).red(116, 60))
        near(0xFF, row(RowModel(ring = true, primary = "Song"), focused = true).red(115, 60))
    }

    @Test
    fun aPressDrawsTheRowAtScaleOne() {
        val r = RowModel(ring = true, primary = "Song")
        assertArrayEquals(row(r).px, row(r, press = 0.96f).px)
        near(over(0.6f), row(r, press = 0.96f).red(116, 60))
    }

    @Test
    fun aLitRingDrawsItsDiscInsideAndItsHaloOutside() {
        val r = RowModel(ring = true, primary = "Song")
        val spot = rowInk(measurer, fonts, r, a105, unitDensity, false, null).spot!!
        val idle = row(r)
        val lit = row(r, ringLit = 1f)
        val inner = spot.d - 2 * spot.stroke
        val y = spot.cy + (spot.d / 4 + inner / 2) / 2
        near(bg, idle.red(spot.cx, y), 0)
        near(over(Tok.vol_discAlpha), lit.red(spot.cx, y), 2)
        val out = spot.cx - spot.d / 2 - ringHalo(spot.d) / 2
        near(bg, idle.red(out, spot.cy), 0)
        assertTrue(lit.red(out, spot.cy) > bg)
    }

    @Test
    fun theArcDrawsInTheAccentFromTheTopClockwise() {
        val accent = Accent.forHue(Accent.defaultHue)
        val s = row(RowModel(ring = true, primary = "Song", arc = 90), accent = accent)
        assertEquals(accent.toArgb() and 0xFFFFFF, s.rgb(95, 35))
        near(over(0.6f), s.red(90, 86))
    }

    @Test
    fun aPressedTitleGlowsUnderItsWordWithinThePad() {
        val r = RowModel(primary = "Hits")
        val g = Glow().also { it.down(0, 0) }
        val lit = row(r, glow = RowGlow(g, g.level(50)))
        val start = row(r, glow = RowGlow(g, g.level(0)))
        val plain = row(r)
        near(bg, plain.red(58, 62), 0)
        near(bg, start.red(58, 62), 0)
        assertTrue("glow ${lit.red(58, 62)}", lit.red(58, 62) > bg + 20)
        near(bg, lit.red(45, 62), 0)
    }

    private fun most(s: Shot, x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var m = 0
        for (y in y0 until y1) for (x in x0 until x1) m = maxOf(m, s.red(x, y))
        return m
    }

    @Test
    fun thePressedArcKeepsItsRingSize() {
        val accent = Accent.forHue(Accent.defaultHue)
        val r = RowModel(ring = true, primary = "Song", arc = 90)
        assertArrayEquals(row(r, accent = accent).px, row(r, press = 0.96f, accent = accent).px)
    }

    @Test
    fun aSongRowDrawsItsTitleAtTheEdgeAndItsSubAtTheDrop() {
        val r = RowModel(primary = "Slow Comet", secondary = "Amber Field", number = "12", song = true)
        val ink = rowInk(measurer, fonts, r, a105, unitDensity, false, null)
        assertEquals(60, ink.lines.titleX)
        assertEquals(ink.lines.title + 44, ink.lines.sub)
        val s = row(r)
        near(bg, most(s, 0, 59, 0, 160), 0)
        assertTrue(most(s, 60, 400, ink.lines.title - 20, ink.lines.title) > 200)
        assertTrue(most(s, 60, 400, ink.lines.sub - 10, ink.lines.sub) > 0x40)
    }

    private fun inkColumns(s: Shot, y0: Int, y1: Int, plain: Shot? = null): List<Int> =
        (0 until s.w).filter { x -> (y0 until y1).any { y -> if (plain == null) s.red(x, y) > bg else s.px[y * s.w + x] != plain.px[y * s.w + x] } }

    private fun inkRows(s: Shot, x0: Int, x1: Int, plain: Shot? = null): List<Int> =
        (0 until s.px.size / s.w).filter { y -> (x0 until x1).any { x -> if (plain == null) s.red(x, y) > bg else s.px[y * s.w + x] != plain.px[y * s.w + x] } }

    @Test
    fun aSongRowDrawsItsAlbumOneGutterRightOfTheArtistInkOnTheSubBaseline() {
        val gutter = 36f
        val artist = RowModel(primary = "Slow Comet", secondary = "Amber Field", song = true)
        val r = artist.copy(album = "Moon Hall")
        val ink = rowInk(measurer, fonts, r, a105, unitDensity, false, null)
        val left = penPx(ink.lines.titleX64 + ink.albumX64) + ink.album.inkLeft
        val want = penPx(ink.lines.titleX64) + ink.secondary.inkRight + gutter
        assertTrue("album ink left $left want $want", abs(left - want) <= 1f / 64)
        val plain = row(artist)
        val s = row(r)
        val y0 = ink.lines.title + 4
        val h = Rows.pitch(a105, r)
        val artistRight = inkColumns(plain, y0, h).last()
        val albumCols = inkColumns(s, y0, h, plain)
        assertTrue("album columns from ${albumCols.first()} want ${artistRight + 1 + gutter.toInt()}", abs(albumCols.first() - (artistRight + 1 + gutter.toInt())) <= 2)
        val albumBottom = inkRows(s, albumCols.first(), albumCols.last() + 1, plain).last()
        val artistBottom = inkRows(plain, ink.lines.titleX, artistRight + 1).last()
        near(artistBottom, albumBottom, 1)
        near(ink.lines.sub - 1, albumBottom, 1)
        near(most(plain, ink.lines.titleX, artistRight + 1, y0, h), most(s, albumCols.first(), albumCols.last() + 1, y0, h), 2)
    }

    @Test
    fun aSongRowWithNoArtistDrawsItsAlbumAtTheTitlePen() {
        val r = RowModel(primary = "Slow Comet", album = "Moon Hall", song = true)
        val ink = rowInk(measurer, fonts, r, a105, unitDensity, false, null)
        assertEquals(0, ink.albumX64)
        val plain = row(r.copy(album = ""))
        val cols = inkColumns(row(r), ink.lines.title + 4, Rows.pitch(a105, r), plain)
        near((penPx(ink.lines.titleX64) + ink.album.inkLeft).toInt(), cols.first(), 2)
    }

    @Test
    fun anAlbumPastTheRightEdgeLeavesTheRowUnchanged() {
        val r = RowModel(primary = "Slow Comet", secondary = "W".repeat(40), album = "Moon Hall", song = true)
        val ink = rowInk(measurer, fonts, r, a105, unitDensity, false, null)
        assertTrue("album pen ${ink.lines.titleX64 + ink.albumX64} right ${pen64(ink.right.toFloat())}", ink.lines.titleX64 + ink.albumX64 >= pen64(ink.right.toFloat()))
        assertArrayEquals(row(r.copy(album = "")).px, row(r).px)
    }

    @Test
    fun aRowThatIsNotASongDrawsNoAlbum() {
        val r = RowModel(primary = "Slow Comet", secondary = "Amber Field", album = "Moon Hall")
        assertTrue(rowInk(measurer, fonts, r, a105, unitDensity, false, null).album.text.isEmpty())
        assertArrayEquals(row(r.copy(album = "")).px, row(r).px)
    }

    @Test
    fun aCollectionRowDrawsItsPlayRingAndBoldTitle() {
        val r = RowModel(ring = true, collection = true, primary = "Lantern Hours")
        val ink = rowInk(measurer, fonts, r, a105, unitDensity, false, null)
        val spot = ink.spot!!
        assertEquals(listOf(103, 43, 87), listOf(spot.cx, spot.cy, spot.d))
        val s = row(r)
        near(over(0.6f), s.red(spot.cx + (spot.d - spot.stroke) / 2, spot.cy))
        assertEquals(165, ink.lines.titleX)
        assertTrue(most(s, 165, 400, ink.lines.title - 20, ink.lines.title) > 200)
        near(bg, most(s, 165, 400, ink.lines.title + 2, ink.lines.title + 10), 0)
    }

    @Test
    fun aScaledRingGrowsAboutItsCentre() {
        near(over(0.6f), row(RowModel(ring = true, primary = "Song", ringScale = 1.5f)).red(128, 60))
        near(bg, row(RowModel(ring = true, primary = "Song")).red(128, 60), 0)
    }

    @Test
    fun aHiddenTitleDrawsNothing() {
        near(bg, most(row(RowModel(primary = "Hits", hide = true)), 60, 200, 40, 80), 0)
        assertTrue(most(row(RowModel(primary = "Hits")), 60, 200, 40, 80) > 200)
    }

    @Test
    fun theSecondLineIsAtSixTenthsOrFourTenthsOnLibraryRows() {
        near(over(0.6f), most(row(RowModel(primary = "Hits", secondary = "Lantern")), 60, 250, 77, 102), 8)
        near(over(0.4f), most(row(RowModel(primary = "Hits", secondary = "Lantern", libSub = true)), 60, 250, 77, 102), 8)
    }

    @Test
    fun numberAndTrailingDrawAtCountAlphaAroundTheTitle() {
        val s = row(RowModel(primary = "Hits", number = "12", trailing = "4:05"))
        near(over(0.6f), most(s, 60, 110, 50, 77), 8)
        near(bg, most(s, 110, 131, 0, 120), 0)
        assertTrue(most(s, 132, 200, 46, 77) > 200)
        near(over(0.6f), most(s, 600, 660, 50, 77), 8)
        near(bg, most(s, 661, 720, 0, 120), 0)
    }

    @Test
    fun thePageGroundMatchesTheFuneOsHubShotRows() {
        val accent = Accent.forHue(Accent.defaultHue)
        val s = shot(4, 1280) { pageGround(groundBands(1280, false), accent) }
        fun rgb(y: Int) = listOf(s.rgb(1, y) shr 16 and 0xFF, s.rgb(1, y) shr 8 and 0xFF, s.rgb(1, y) and 0xFF)
        for ((y, want) in listOf(0 to listOf(51, 20, 25), 640 to listOf(34, 18, 21), 1279 to listOf(17, 17, 17))) {
            rgb(y).zip(want).forEach { (g, w) -> near(w, g, 0) }
        }
        val bare = shot(4, 1280, Ink.white) { pageGround(groundBands(1280, true), accent) }
        for (y in listOf(0, 640, 1279)) assertEquals(0xFFFFFF, bare.rgb(1, y))
        val off = shot(4, 1280) { pageGround(groundBands(1280, true), accent) }
        for (y in listOf(0, 640, 1279)) near(bg, off.red(1, y), 0)
    }

    @Test
    fun onTheP2160PageATitleDrawsToTheWindowLessTheHubEdge() {
        val p2160 = scale(Window(1080, 2160, 35, none, none, none, none, none, Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)), Nav.None, Surface.Page, false)
        val r = RowModel(primary = "W".repeat(40))
        val h = Rows.pitch(p2160, false)
        val s = shot(1080, h) { listRow(rowInk(measurer, fonts, r, p2160, unitDensity, false, null), p2160, 1f) }
        assertEquals(1021, Rows.right(p2160))
        assertTrue(most(s, 990, 1021, 0, h) > 150)
        near(bg, most(s, 1021, 1080, 0, h), 0)
    }
}
