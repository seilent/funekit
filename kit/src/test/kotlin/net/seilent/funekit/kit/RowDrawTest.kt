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
    ) = shot(720, Rows.pitch(a105, r.collection)) {
        listRow(rowInk(measurer, fonts, r, a105, unitDensity, false, null), a105, alpha, focused, press, accent, false, glow, focusShown = focusShown)
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
    fun theRowRingIsIdleAtMidAlphaAndFocusedAtFull() {
        near(over(0.6f), row(RowModel(ring = true, primary = "Song")).red(116, 60))
        near(0xFF, row(RowModel(ring = true, primary = "Song"), focused = true).red(115, 60))
    }

    @Test
    fun aPressScalesTheWholeRowAboutItsCentre() {
        val s = row(RowModel(ring = true, primary = "Song"), press = 0.96f)
        near(bg, s.red(116, 60), 0)
        near(over(0.6f), s.red(126, 60))
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
        val lit = row(r, glow = RowGlow(g, g.progress(50)))
        val start = row(r, glow = RowGlow(g, g.progress(0)))
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
    fun thePressedArcScalesWithTheRing() {
        val accent = Accent.forHue(Accent.defaultHue)
        val s = row(RowModel(ring = true, primary = "Song", arc = 90), press = 0.96f, accent = accent)
        assertEquals(accent.toArgb() and 0xFFFFFF, s.rgb(106, 36))
        near(bg, s.red(115, 34))
    }

    @Test
    fun aCollectionRowDrawsItsPlayRingAndBoldTitle() {
        val s = row(RowModel(ring = true, collection = true, primary = "Lantern Hours"))
        near(over(0.6f), s.red(123, 82))
        assertTrue(most(s, 134, 400, 20, 50) > 200)
        near(bg, most(s, 134, 400, 52, 70), 0)
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
