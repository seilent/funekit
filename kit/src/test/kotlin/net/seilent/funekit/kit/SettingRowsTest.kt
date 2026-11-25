package net.seilent.funekit.kit

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Phones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SettingRowsTest {
    private val none = Insets(0, 0, 0, 0)
    private fun page(w: Int, h: Int, large: Boolean = false) = scale(Window(w, h, 35, none, none, none, none, none), Nav.None, Surface.Page, large)
    private val v1 = page(240, 320)
    private val r1 = page(480, 800)
    private val a105 = page(720, 1280)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val density = Density(1f)

    private fun measure(s: Scale) = settingMeasure(s, fonts, density, null, true)

    private fun width64(what: String, text: String, funeOs64: Int, kit64: Int) {
        val glyphs = text.codePointCount(0, text.length)
        val over = kit64 - funeOs64
        assertTrue(
            "$what: funeOS width64 $funeOs64 / 64 truncates each of its $glyphs glyph advances to 1/64 px and the kit sums them unrounded, so the kit passes it by 0 to under $glyphs / 64, got $kit64 / 64 ($over over)",
            over in 0 until glyphs,
        )
    }

    @Test
    fun titleAndValueWidthsFollowTheDesignRowWidthTable() {
        val m = measure(v1)
        width64("RESCAN LIBRARY", "rescan library", 9545, m.title64("rescan library"))
        width64("PRIORITY", "priority", 5218, m.title64("priority"))
        width64("SCROBBLE", "scrobble", 5724, m.title64("scrobble"))
        width64("NEWEST", "newest", 2349, m.value64("newest", false))
        width64("CARD LOG", "card log", 3006, m.value64("card log", false))
    }

    @Test
    fun rescanLibraryTakesTwoTitleLinesOnEveryBoard() {
        for (s in listOf(v1, r1, a105)) {
            val m = measure(s)
            assertEquals("ppu ${s.ppu} with a count", listOf("rescan", "library"), m.titleLines("rescan library", listOf("scanning", "1293 songs"), false))
            assertEquals("ppu ${s.ppu} while scanning", listOf("rescan", "library"), m.titleLines("rescan library", listOf("scanning", "scanning"), false))
        }
    }

    @Test
    fun aReadOnlyRowMeasuresItsValueLightSoAValueThatWouldWrapBoldStaysOneLine() {
        val m = measure(v1)
        val title = "scrobble"
        val value = "newest bluetooth"
        val room = m.right64 - m.left64 - m.title64(title) - pen64(m.gutter.toFloat())
        assertTrue("premise: the light value fits beside the title", m.value64(value, false) <= room)
        assertTrue("premise: the bold value does not", m.value64(value, true) > room)
        assertEquals(listOf(value), m.valueLines(title, value, false))
        assertEquals(listOf("newest", "bluetooth"), m.valueLines(title, value, true))
        assertEquals(listOf(title), m.titleLines(title, listOf(value), false))
    }

    @Test
    fun rowHeightsFollowTheMeasureArithmetic() {
        val m = measure(a105)
        val plain = m.size(listOf("about"), listOf("0.1.0"), false, false)
        assertEquals(129, plain.px)
        assertEquals(Tok.settingsPitch, plain.height, 0f)
        assertEquals(0, plain.pad)
        assertEquals(129, plain.band)
        val twoNoSub = m.size(listOf("rescan", "library"), emptyList(), false, false)
        assertEquals(129, twoNoSub.px)
        assertEquals(Tok.settingsPitch, twoNoSub.height, 0f)
        val sub = m.size(listOf("debug"), emptyList(), true, false)
        assertEquals(148, sub.px)
        assertEquals(18, sub.pad)
        assertEquals(69, sub.band)
        assertEquals(90, sub.subY)
        assertEquals(148f / a105.ppu, sub.height, 0f)
        val twoSub = m.size(listOf("notification", "access"), emptyList(), true, false)
        assertEquals(199, twoSub.px)
        assertEquals(18, twoSub.pad)
        assertEquals(120, twoSub.band)
        assertEquals(141, twoSub.subY)
    }

    private fun ink(m: SettingMeasure, title: String, value: String, sub: String?) =
        settingInk(TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null, m, title, value, emptyList(), sub, false)

    private class Shot(val top: Int, val base: Int, val right64: Int, val sub: Int, val resumeTop: Int, val resumeSub: Int)

    private fun landsOnTheShot(s: Scale, want: Shot) {
        val m = measure(s)
        val top = px(s.board.page_contentTop, s.ppu)
        assertEquals("ppu ${s.ppu} content top", want.top, top)
        val priority = ink(m, "priority", "jack", "jack or headset when both connect")
        assertEquals("ppu ${s.ppu} title baseline", want.base, top + priority.titleBase.single())
        assertEquals("ppu ${s.ppu} value baseline", want.base, top + priority.valueBase.single())
        assertEquals("ppu ${s.ppu} value right edge", want.right64, m.right64)
        assertEquals(m.right64, priority.valueX64.single() + pen64(priority.value.single().width))
        assertEquals("ppu ${s.ppu} sub baseline", want.sub, top + priority.subBase)
        val above = listOf(
            ink(m, "library folders", "1 on", null),
            settingInk(TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null, m, "rescan library", "1293 songs", listOf("scanning"), null, false),
            ink(m, "play through", "off", "what plays when the queue ends"),
        )
        val resumeTop = top + above.sumOf { it.size.px }
        assertEquals("ppu ${s.ppu} resume top", want.resumeTop, resumeTop)
        assertEquals("ppu ${s.ppu} resume sub baseline", want.resumeSub, resumeTop + ink(m, "resume", "position", "restores the queue at power on").subBase)
    }

    @Test
    fun theCorrectionEqRowSizesForTwoLinesWithOneLineDrawn() {
        val a = measure(a105).size(listOf("correction eq"), listOf("off"), true, false, tall = true)
        assertEquals("A105: two title lines 120 + gap 3 + sub 40 + pad 36, as funeOS `block` for EarEq (ui/settings.cpp:524)", 199, a.px)
        assertEquals(43, a.pad)
        assertEquals(69, a.band)
        assertEquals(115, a.subY)
        val r = measure(r1).size(listOf("correction eq"), listOf("off"), true, false, tall = true)
        assertEquals("R1: 80 + 2 + 27 + 24", 133, r.px)
        assertEquals(29, r.pad)
        assertEquals(46, r.band)
        assertEquals(77, r.subY)
    }

    @Test
    fun theA105AndR1RowsLandOnTheFuneOsShotBaselines() {
        landsOnTheShot(a105, Shot(A105_TOP, A105_BASE, A105_RIGHT64, A105_SUB, A105_RESUME_TOP, A105_RESUME_SUB))
        landsOnTheShot(r1, Shot(R1_TOP, R1_BASE, R1_RIGHT64, R1_SUB, R1_RESUME_TOP, R1_RESUME_SUB))
    }

    @Test
    fun theLargeRowsLandOnTheFuneOsShotBaselines() {
        val a = page(720, 1280, true)
        val r = page(480, 800, true)
        assertTrue("premise: the large page ppu (funeOS player/ui/size.cpp set_size :79-84, kLargePpu :62, v0.4.4)", a.large && r.large)
        landsOnTheShot(a, Shot(A105_LARGE_TOP, A105_LARGE_BASE, A105_LARGE_RIGHT64, A105_LARGE_SUB, A105_LARGE_RESUME_TOP, A105_LARGE_RESUME_SUB))
        landsOnTheShot(r, Shot(R1_LARGE_TOP, R1_LARGE_BASE, R1_LARGE_RIGHT64, R1_LARGE_SUB, R1_LARGE_RESUME_TOP, R1_LARGE_RESUME_SUB))
    }

    private fun drawn(w: Int, h: Int, block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
        return img.asAndroidBitmap()
    }

    private fun Bitmap.inked(xs: IntRange, ys: IntRange): Boolean = xs.any { x -> ys.any { y -> getPixel(x, y) ushr ALPHA_SHIFT != 0 } }

    @Test
    fun aLongSubDrawsOnOneLineClippedAtTheRightEdge() {
        val m = measure(a105)
        val ink = ink(m, "resume", "position", LONG_SUB)
        val sub = requireNotNull(ink.sub)
        assertTrue("premise: the sub runs past the room left of HUB_EDGE", penPx(ink.titleX64) + sub.width > ink.right)
        assertEquals("the row keeps the one line height (funeOS player/ui/settings.cpp SettingsPage::measure :555-580, v0.4.4)", A105_SUB_ROW, ink.size.px)
        val box = ink.size.subY + Metrics.ascent(m.subPx) + Metrics.descent(m.subPx)
        assertEquals("premise: the sub box bottom", A105_SUB_BOX, box)
        val bmp = drawn(a105.widthPx, ink.size.px) { settingSub(ink, 1f) }
        assertTrue("premise: the sub draws", bmp.inked(0 until ink.right, ink.size.subY until box))
        assertFalse("no pixel right of the clip (funeOS player/ui/settings.cpp SettingsPage::draw_row :1955-1961, player/ui/rows.cpp draw_clipped :121-129, v0.4.4)", bmp.inked(ink.right until bmp.width, 0 until bmp.height))
        assertFalse("no second line under the sub box", bmp.inked(0 until bmp.width, box until bmp.height))
        bmp.recycle()
    }

    @Test
    fun onThePhonePageBaseTheValueEndsAtTheWindowLessHubEdge() {
        val s = scale(Phones.p2160Page.window(Nav.Gesture), Nav.Gesture, Surface.Page, false)
        assertTrue("premise: the page base", s.fit != null)
        val m = measure(s)
        assertEquals(PAGE_RIGHT64, m.right64)
        assertEquals(listOf("rescan library"), m.titleLines("rescan library", listOf("scanning", "1293 songs"), false))
    }

    @Test
    fun aMeasureTakesItsTitleRoleForTheTitleSizeWidthAndWords() {
        val page = measure(a105)
        val info = settingMeasure(a105, fonts, density, null, true, Roles.settingsValue)
        assertTrue("premise: the two roles differ in size", rolePx(Roles.pageRowTitle, a105) != rolePx(Roles.settingsValue, a105))
        assertEquals(rolePx(Roles.pageRowTitle, a105), page.titlePx)
        assertEquals(rolePx(Roles.settingsValue, a105), info.titlePx)
        assertEquals("the title measures as a light value would", info.value64("battery", false), info.title64("battery"))
        assertTrue("premise: the page title measures wider", page.title64("battery") > info.title64("battery"))
        val ink = settingInk(TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null, info, "battery", "", emptyList(), null, false)
        assertEquals("the title word draws in that role", info.title64("battery") / PEN, ink.title.single().width, 1f)
    }

    private companion object {
        const val A105_TOP = 360
        const val A105_BASE = 435
        const val A105_SUB = 483
        const val A105_RIGHT64 = 42240
        const val A105_RESUME_TOP = 766
        const val A105_RESUME_SUB = 889
        const val R1_TOP = 240
        const val R1_BASE = 290
        const val R1_SUB = 322
        const val R1_RIGHT64 = 28160
        const val R1_RESUME_TOP = 511
        const val R1_RESUME_SUB = 593
        const val A105_LARGE_TOP = 432
        const val A105_LARGE_BASE = 522
        const val A105_LARGE_SUB = 581
        const val A105_LARGE_RIGHT64 = 41472
        const val A105_LARGE_RESUME_TOP = 983
        const val A105_LARGE_RESUME_SUB = 1132
        const val R1_LARGE_TOP = 288
        const val R1_LARGE_BASE = 348
        const val R1_LARGE_SUB = 386
        const val R1_LARGE_RIGHT64 = 27648
        const val R1_LARGE_RESUME_TOP = 653
        const val R1_LARGE_RESUME_SUB = 751
        const val A105_SUB_ROW = 148
        const val A105_SUB_BOX = 130
        const val ALPHA_SHIFT = 24
        const val LONG_SUB = "a sub line that runs on past the right edge of the row in a window narrower than the device"
        const val PAGE_RIGHT64 = 65323
        const val PEN = 64f
    }
}
