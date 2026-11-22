package net.seilent.funekit.kit

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import net.seilent.funekit.Case
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Weight
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
@Config(sdk = [28, 34, 35])
class WordInkTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val bg = 0x11

    private class Shot(val px: IntArray, val w: Int, val h: Int) {
        fun red(x: Int, y: Int) = px[y * w + x] shr 16 and 0xFF
    }

    private fun shot(w: Int, h: Int, draw: DrawScope.() -> Unit): Shot {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Ink.bg, Offset.Zero, size)
            draw()
        }
        val px = IntArray(w * h)
        img.readPixels(px)
        return Shot(px, w, h)
    }

    private fun column(s: Shot, x: Int, y0: Int, y1: Int): Int {
        var m = 0
        for (y in y0 until y1) m = maxOf(m, s.red(x, y))
        return m
    }

    private fun leftEdge(s: Shot, from: Int, y0: Int, y1: Int, full: Int): Float {
        var x = from
        while (column(s, x, y0, y1) <= bg) x++
        return x + 1 - (column(s, x, y0, y1) - bg).toFloat() / (full - bg)
    }

    private fun rightEdge(s: Shot, from: Int, y0: Int, y1: Int, full: Int): Float {
        var x = from
        while (column(s, x, y0, y1) <= bg) x--
        return x + (column(s, x, y0, y1) - bg).toFloat() / (full - bg)
    }

    @Test
    fun trackingMovesNoGlyphBeforeTheFirstPen() {
        val style = roleStyle(Roles.pageHeader, a105, unitDensity, Ink.white, ppu = a105.stdPpu)
        val tracked = measurer.word("l", Roles.pageHeader, style, fonts, unitDensity, null)
        val flat = measurer.word("l", Roles.pageHeader, style.copy(letterSpacing = TextUnit.Unspecified), fonts, unitDensity, null)
        val a = shot(600, 500) { word(tracked, 100f, 420f, Ink.white, 1f) }
        val b = shot(600, 500) { word(flat, 100f, 420f, Ink.white, 1f) }
        val ea = leftEdge(a, 0, 300, 400, 0xFF)
        val eb = leftEdge(b, 0, 300, 400, 0xFF)
        assertEquals(eb, ea, 0.35f)
        assertEquals(flat.inkLeft, tracked.inkLeft, 0.35f)
    }

    @Test
    fun aWordIsAsWideAsFuneOsWidth64AdvancesPlusSpacingPerGlyph() {
        val style = roleStyle(Roles.pageHeader, a105, unitDensity, Ink.white, ppu = a105.stdPpu)
        val flatStyle = style.copy(letterSpacing = TextUnit.Unspecified)
        for ((t, n) in listOf("l" to 1, "AB" to 2, "ABC" to 3)) {
            val w = measurer.word(t, Roles.pageHeader, style, fonts, unitDensity, null).width
            val f = measurer.word(t, Roles.pageHeader, flatStyle, fonts, unitDensity, null).width
            assertEquals("$t", -3f * n, w - f, 0.05f)
        }
    }

    @Test
    fun paintSpacesBetweenGlyphsOnSdk35AndAtEveryGlyphBefore() {
        val style = roleStyle(Roles.pageHeader, a105, unitDensity, Ink.white, ppu = a105.stdPpu)
        val face = fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal, FontStyle.Normal, FontSynthesis.None).value as Typeface
        val size = style.fontSize.value
        val tracked = Paint().also {
            it.typeface = face
            it.textSize = size
            it.letterSpacing = style.letterSpacing.value / size
        }
        val flat = Paint(tracked).also { it.letterSpacing = 0f }
        fun gap(t: String) = tracked.measureText(t) - flat.measureText(t)
        val one = gap("A")
        val two = gap("AB")
        val three = gap("ABC")
        val seen = "sdk ${Build.VERSION.SDK_INT}: A $one, AB $two, ABC $three"
        assertEquals(seen, -3f, two - one, 0.05f)
        assertEquals(seen, -3f, three - two, 0.05f)
        if (Build.VERSION.SDK_INT != 34) assertEquals(seen, if (Build.VERSION.SDK_INT >= 35) 0f else -3f, one, 0.05f)
    }

    @Test
    fun theWordTakesOffExactlyTheShiftTheLayoutDraws() {
        val style = roleStyle(Roles.pageHeader, a105, unitDensity, Ink.white, ppu = a105.stdPpu)
        val flatStyle = style.copy(letterSpacing = TextUnit.Unspecified)
        val tracked = measurer.word("l", Roles.pageHeader, style, fonts, unitDensity, null)
        fun raw(st: TextStyle) = shot(600, 500) {
            val l = measurer.measure("l", st, density = unitDensity, fontFamilyResolver = fonts)
            drawText(l, Ink.white, Offset(100f, 420f - l.firstBaseline))
        }
        val measured = leftEdge(raw(style), 0, 300, 400, 0xFF) - leftEdge(raw(flatStyle), 0, 300, 400, 0xFF)
        val dx = tracked.runs.single().dx
        assertEquals("sdk ${Build.VERSION.SDK_INT}", measured, -dx, 0.35f)
        if (Build.VERSION.SDK_INT != 34) assertEquals("sdk ${Build.VERSION.SDK_INT}", if (Build.VERSION.SDK_INT >= 35) 0f else 1.5f, dx, 0.05f)
    }

    @Test
    fun inkIsTheGlyphOutlineNotTheAdvanceBox() {
        val style = roleStyle(Roles.pageHeader, a105, unitDensity, Ink.white, ppu = a105.stdPpu)
        val w = measurer.word("l", Roles.pageHeader, style, fonts, unitDensity, null)
        val s = shot(600, 500) { word(w, 100f, 420f, Ink.white, 1f) }
        assertTrue("lsb ${w.inkLeft}", w.inkLeft > 1f)
        assertEquals(100f + w.inkLeft, leftEdge(s, 0, 300, 400, 0xFF), 0.5f)
        assertEquals(100f + w.inkRight, rightEdge(s, 599, 300, 400, 0xFF), 0.5f)
        assertTrue(w.inkTop < 0f)
    }

    @Test
    fun aSpaceHasNoInk() {
        val style = roleStyle(Roles.pageHeader, a105, unitDensity, Ink.white, ppu = a105.stdPpu)
        val w = measurer.word(" ", Roles.pageHeader, style, fonts, unitDensity, null)
        assertTrue(!w.inked)
    }

    @Test
    fun pivotLabelsSitInkToInkAtTheTokenGaps() {
        val c = chromeOf(measurer, fonts, a105, unitDensity, "artists", listOf("artists", "albums", "songs"), null)
        val s = shot(720, 400) { chrome(c, 0L) }
        val y0 = 189
        val y1 = 278
        val inactive = Math.round(bg + (0xFF - bg) * 0.28f)
        var x = 60
        while (column(s, x, y0, y1) <= bg) x++
        var gap = 0
        while (gap < 12) {
            if (column(s, x, y0, y1) > bg) gap = 0 else gap++
            x++
        }
        val end0 = rightEdge(s, x - gap, y0, y1, 0xFF)
        val start1 = leftEdge(s, x, y0, y1, inactive)
        assertEquals(7.7f * 3, start1 - end0, 0.6f)
        x = start1.toInt() + 1
        gap = 0
        while (gap < 12) {
            if (column(s, x, y0, y1) > bg) gap = 0 else gap++
            x++
        }
        val end1 = rightEdge(s, x - gap, y0, y1, inactive)
        val start2 = leftEdge(s, x, y0, y1, inactive)
        assertEquals(6.6f * 3, start2 - end1, 0.6f)
    }

    @Test
    fun aChromeMeasuresItsHeaderInTheCaseAndWeightItIsGiven() {
        val eq = chromeOf(measurer, fonts, a105, unitDensity, "eq", emptyList(), null, Case.Upper, Weight.Bold)
        assertEquals("the header takes its case", "EQ", eq.header.text)
        assertEquals("the header takes its weight", FontWeight.Bold, eq.header.runs[0].layout.layoutInput.style.fontWeight)
        val page = chromeOf(measurer, fonts, a105, unitDensity, "eq", emptyList(), null)
        assertEquals("the default header is the page header's case", "eq", page.header.text)
        assertEquals("the default header is the page header's weight", FontWeight.Light, page.header.runs[0].layout.layoutInput.style.fontWeight)
    }

    @Test
    fun aSpriteScalesTheWordAboutItsPenAndClipsAtItsBottom() {
        val style = roleStyle(Roles.pageHeader, a105, unitDensity, Ink.white, ppu = a105.stdPpu)
        val w = measurer.word("l", Roles.pageHeader, style, fonts, unitDensity, null)
        val whole = shot(600, 500) { sprite(w, 1f, 100f, 420f, 1f, 500) }
        val half = shot(600, 500) { sprite(w, 0.5f, 100f, 420f, 1f, 500) }
        assertEquals(100f + w.inkLeft, leftEdge(whole, 0, 300, 400, 0xFF), 0.5f)
        assertEquals(100f + w.inkLeft / 2, leftEdge(half, 0, 380, 410, 0xFF), 0.5f)
        val cut = shot(600, 500) { sprite(w, 1f, 100f, 420f, 1f, 350) }
        var below = 0
        for (y in 350 until 420) for (x in 0 until 600) below = maxOf(below, cut.red(x, y))
        assertEquals(bg, below)
    }

    @Test
    fun animatedTextKeepsFuneOsPenPositionsAcrossAPivotLabel() {
        val base = roleStyle(Roles.pivotActive, a105, unitDensity, Ink.white)
        val width = textWidth(Roles.pivotActive, base, fonts, unitDensity, null)
        val t = cased("artists", Roles.pivotActive)
        val last = t.substring(t.length - 1)
        val want = 40f + width(t) - width(last) + measurer.word(last, Roles.pivotActive, base, fonts, unitDensity, null).inkRight
        fun drawn(motion: TextMotion): Float {
            val st = base.copy(textMotion = motion)
            val w = measurer.word(t, Roles.pivotActive, st, fonts, unitDensity, null)
            val s = shot(720, 300) { word(w, 40f, 200f, Ink.white, 1f) }
            return rightEdge(s, 719, 150, 200, 0xFF)
        }
        val animated = abs(drawn(TextMotion.Animated) - want)
        val static = abs(drawn(TextMotion.Static) - want)
        val seen = "sdk ${Build.VERSION.SDK_INT}: animated off by $animated, static by $static"
        assertTrue(seen, animated <= 1f)
        assertTrue(seen, animated <= static)
    }
}
