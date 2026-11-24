package net.seilent.funekit.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.px
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
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MenuDrawTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)

    private fun red(px: IntArray, x: Int, y: Int) = px[y * 720 + x] shr 16 and 0xFF

    private fun near(want: Int, got: Int, tol: Int = 2) = assertTrue("want $want got $got", abs(want - got) <= tol)

    private fun menu(form: MenuForm): Menu = Menu().also {
        it.open(form, "play", listOf("play", "play next", "add to now playing"), 0)
        it.frame(px(22f, 3f), 1280)
    }

    private class Shot(val px: IntArray, val plan: MenuPlan, val ink: MenuInk) {
        fun red(x: Int, y: Int) = px[y * 720 + x] shr 16 and 0xFF
        fun green(x: Int, y: Int) = px[y * 720 + x] shr 8 and 0xFF
        fun rgb(x: Int, y: Int) = px[y * 720 + x] and 0xFFFFFF
        fun most(x0: Int, x1: Int, y0: Int, y1: Int): Int {
            var m = 0
            for (y in y0 until y1) for (x in x0 until x1) m = maxOf(m, red(x, y))
            return m
        }
    }

    private val accent = Accent.forHue(Accent.defaultHue)

    private fun draw(m: Menu, kind: OverKind, at: Long, accentOff: Boolean = true): Shot {
        val ink = menuInk(measurer, fonts, m, a105, unitDensity, accentOff, covers)
        val plan = m.plan(ink).also { m.settle(it) }
        val o = Overlay(kind).also { it.open(0) }
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) {
            drawRect(Ink.white, Offset.Zero, size)
            menuLayer(m, ink, plan, o, a105, at, if (accentOff) Ink.white else accent, accentOff, Paint())
        }
        val px = IntArray(720 * 1280)
        img.readPixels(px)
        return Shot(px, plan, ink)
    }

    private fun shot(m: Menu, kind: OverKind, at: Long): Pair<IntArray, MenuPlan> = draw(m, kind, at).let { it.px to it.plan }

    private fun sheet(items: List<String>, readOnly: Boolean = false, lead: Int = 0, mixedRow: Int = -1, title: String = "play"): Menu = Menu().also {
        it.open(MenuForm.Sheet, title, items, 0, readOnly, mixedRow, lead = lead)
        it.frame(px(22f, 3f), 1280)
    }

    private val panel = 0x18
    private val ascent51 = (2027 * 51 + 2047) / 2048
    private val descent51 = (431 * 51 + 2047) / 2048
    private val line33 = (2027 * 33 + 2047) / 2048 + (431 * 33 + 2047) / 2048

    @Test
    fun aDialogScalesAboutTheCentreOfTheScreenCentresPixel() {
        assertEquals(Offset(360.5f, 640.5f), dialogPivot(720, 1280))
        assertEquals(Offset(120.5f, 160.5f), dialogPivot(241, 321))
    }

    @Test
    fun theMenuTakesItsSizesFromTheTokensAtPagePx() {
        val ink = menuInk(measurer, fonts, menu(MenuForm.Sheet), a105, unitDensity, true, covers)
        assertEquals(43 * 3, ink.pitch)
        assertEquals(16 * 3, ink.edge)
        assertEquals((2027 * 66 + 2047) / 2048 + (431 * 66 + 2047) / 2048, ink.titleH)
        assertEquals(79 * 3, ink.dialogTitleBase)
        assertEquals(98 * 3, ink.dialogTop)
    }

    @Test
    fun anOpenSheetIsAnElevatedPanelUnderAScrimWithNoFocusBar() {
        val (px, plan) = shot(menu(MenuForm.Sheet), OverKind.Sheet, 380)
        near(0x18, red(px, 700, 1270))
        near(Math.round(0xFF * 0.45f + 0x11 * 0.55f), red(px, 360, 100))
        for (x in 24 until 30) near(panel, red(px, x, plan.top + 60), 0)
        var ink = 0
        for (y in plan.top until plan.top + 43 * 3) for (x in 48 until 300) ink = maxOf(ink, red(px, x, y))
        assertTrue("item ink $ink", ink > 200)
    }

    @Test
    fun aSheetStartsBelowItsPanelAndRises() {
        val (px, plan) = shot(menu(MenuForm.Sheet), OverKind.Sheet, 0)
        near(0xFF, red(px, 700, 1270), 0)
        assertTrue(plan.panelH > 0)
    }

    @Test
    fun anOpenDialogDarkensThePageAndDrawsNoPanel() {
        val (px, plan) = shot(menu(MenuForm.Dlg), OverKind.Dlg, 300)
        near(Math.round(0xFF * 0.04f + 0x11 * 0.96f), red(px, 700, 1270))
        assertEquals(98 * 3, plan.top)
        assertEquals(79 * 3, plan.titleBase)
    }

    @Test
    fun aLongMixedReadOnlyLineTakesASecondLine() {
        val m = sheet(listOf("An Artist Name Long Enough To Need A Second Line Here", "short"), readOnly = true, mixedRow = 0)
        val d = draw(m, OverKind.Sheet, 380)
        val row = d.plan.rows.first()
        assertEquals(43 * 3 + ascent51 + descent51, row.h)
        assertTrue(d.ink.rows[0].second != null)
        val base2 = row.top + d.ink.rows[0].base + ascent51 + descent51
        assertTrue(d.most(48, 400, base2 - 30, base2) > 100)
    }

    @Test
    fun leadLinesCarryTheirWrapAndSubLines() {
        val m = Menu().also {
            it.open(MenuForm.Dlg, "remove", listOf("Lantern Hours", "remove", "keep"), 0, lead = 1)
            it.leadParts(listOf("Field Notes"), listOf("two keys"))
            it.frame(px(22f, 3f), 1280)
        }
        val d = draw(m, OverKind.Dlg, 300)
        val row = d.plan.rows.first()
        assertEquals(43 * 3 + ascent51 + descent51 + line33, row.h)
        val base = row.top + d.ink.rows[0].base
        val wrap = base + ascent51 + descent51
        val sub = wrap + descent51 + (2027 * 33 + 2047) / 2048
        assertTrue(d.most(48, 400, wrap - 30, wrap) > 100)
        assertTrue(d.most(48, 400, sub - 20, sub) > 60)
        assertEquals(row.top + row.h, d.plan.rows[1].top)
    }

    @Test
    fun aValueSheetPinsItsLeadRowAndScrollsTheRest() {
        val m = sheet(List(20) { "choice $it" }, lead = 1)
        m.value(1, 1, wrap = false, now = 0)
        val d = draw(m, OverKind.Sheet, 380)
        assertEquals(1, d.plan.pin)
        assertEquals(1 + (1280 - 66 - 2 * 48 - 80 - 129) / 129, d.plan.rows.size)
        assertTrue(m.scrollTo(5, 0))
        val e = draw(m, OverKind.Sheet, 380)
        assertEquals(listOf(0, 5, 6, 7, 8, 9, 10, 11), e.plan.rows.map { it.i })
        assertEquals(5, m.focus.row)
        val top = e.plan.top
        near(panel, e.red(26, top + 129 + 60), 0)
        near(panel, e.red(26, top + 60), 0)
        var thumb = 0
        var beside = 0
        for (y in top + 129 until 1280) {
            thumb = maxOf(thumb, e.red(714, y))
            beside = maxOf(beside, e.red(705, y))
        }
        near(Math.round(panel + (0xFF - panel) * 0.2f), thumb, 2)
        near(panel, beside, 0)
    }

    @Test
    fun aCappedSheetDropsItsTitleThenItsPin() {
        val m = sheet(List(20) { "choice $it" }, lead = 1)
        m.value(1, 1, wrap = false, now = 0)
        m.keep(kept = true, capped = true, maxPanel = 2 * 48 + 2 * 129)
        val t = draw(m, OverKind.Sheet, 380)
        assertEquals(null, t.plan.titleRect)
        assertEquals(2 * 48 + 2 * 129, t.plan.panelH)
        assertEquals(1, t.plan.pin)
        m.keep(kept = true, capped = true, maxPanel = 2 * 48 + 129)
        val p = draw(m, OverKind.Sheet, 380)
        assertEquals(0, p.plan.pin)
        assertEquals(1, p.plan.rows.size)
        assertEquals(2 * 48 + 129, p.plan.panelH)
    }

    @Test
    fun theMarkDrawsInTheAccentAndWhiteAtTheRowAlphaUnderAccentOff() {
        val m = sheet(listOf("one", "two", "three"))
        m.value(0, 2, wrap = false, now = 0)
        val on = draw(m, OverKind.Sheet, 380, accentOff = false)
        val r = on.plan.rows[2]
        var best = 0
        for (y in r.top until r.top + r.h) for (x in 48 until 300) best = maxOf(best, on.red(x, y) - on.green(x, y))
        assertTrue("accent $best", best > 80)
        val off = draw(m, OverKind.Sheet, 380, accentOff = true)
        near(Math.round(panel + (0xFF - panel) * 0.6f), off.most(48, 300, r.top, r.top + r.h), 8)
    }

    @Test
    fun underAccentOffTheMarkedChoiceTakesTheRowAlphaOnceAStepLeavesIt() {
        val m = sheet(listOf("one", "two", "three"))
        m.value(2, 2, wrap = false, now = 0)
        val focused = draw(m, OverKind.Sheet, 380, accentOff = true)
        val r = focused.plan.rows[2]
        assertTrue(focused.most(48, 300, r.top, r.top + r.h) > 250)
        assertTrue(m.step(-1, 0))
        val left = draw(m, OverKind.Sheet, 380, accentOff = true)
        near(Math.round(panel + (0xFF - panel) * 0.6f), left.most(48, 300, r.top, r.top + r.h), 8)
        assertTrue(left.most(48, 300, left.plan.rows[1].top, left.plan.rows[1].top + r.h) > 250)
    }

    @Test
    fun theEditedCellValueDrawsInTheAccentAtFullAlpha() {
        val m = sheet(listOf("mode", "level", "rate"))
        m.cells(listOf("on", "high", "auto"), listOf(false, false, false), 1)
        val d = draw(m, OverKind.Sheet, 380, accentOff = false)
        val r = d.plan.rows[1]
        var best = 0
        for (y in r.top until r.top + r.h) for (x in 400 until 672) best = maxOf(best, d.red(x, y) - d.green(x, y))
        assertTrue("edit $best", best > 80)
        assertEquals(panel, d.most(672, 720, r.top, r.top + r.h))
    }

    @Test
    fun aSwatchRowDrawsItsColourSquareAtTheItemSize() {
        val m = sheet(listOf("red", "blue"))
        m.swatches(listOf(accent, Ink.white))
        val d = draw(m, OverKind.Sheet, 380)
        val r = d.plan.rows[0]
        val sy = r.top + (129 - 51) / 2
        assertEquals(accent.toArgb() and 0xFFFFFF, d.rgb(48 + 25, sy + 25))
        near(panel, d.red(48 + 52, sy + 25), 0)
        near(panel, d.red(48 + 25, sy - 2), 0)
    }

    @Test
    fun theFocusedSwatchTakesAWhiteBorderOutsideItsSquareAndNoneOnAReadOnlyMenu() {
        val m = sheet(listOf("red", "blue"))
        m.swatches(listOf(accent, Ink.white))
        val d = draw(m, OverKind.Sheet, 380)
        val sy = d.plan.rows[0].top + (129 - 51) / 2
        for (x in 36 until 42) near(0xFF, d.red(x, sy + 25), 0)
        for (x in 42 until 48) near(panel, d.red(x, sy + 25), 0)
        for (x in 99 until 105) near(panel, d.red(x, sy + 25), 0)
        for (x in 105 until 111) near(0xFF, d.red(x, sy + 25), 0)
        near(panel, d.red(35, sy + 25), 0)
        near(panel, d.red(111, sy + 25), 0)
        for (y in sy - 12 until sy - 6) near(0xFF, d.red(48 + 25, y), 0)
        for (y in sy - 6 until sy) near(panel, d.red(48 + 25, y), 0)
        for (y in sy + 51 + 6 until sy + 51 + 12) near(0xFF, d.red(48 + 25, y), 0)
        near(panel, d.red(48 + 25, sy - 13), 0)
        near(0xFF, d.red(36, sy - 12), 0)
        val other = d.plan.rows[1].top + (129 - 51) / 2
        near(panel, d.red(38, other + 25), 0)
        val ro = sheet(listOf("red", "blue"), readOnly = true)
        ro.swatches(listOf(accent, Ink.white))
        val q = draw(ro, OverKind.Sheet, 380)
        val qy = q.plan.rows[0].top + (129 - 51) / 2
        assertEquals(accent.toArgb() and 0xFFFFFF, q.rgb(48 + 25, qy + 25))
        near(panel, q.red(38, qy + 25), 0)
        near(panel, q.red(48 + 25, qy - 10), 0)
    }

    @Test
    fun aSwatchItemDrawsItsSquareAndNoWord() {
        val m = sheet(listOf("red", "blue"))
        m.swatches(listOf(Ink.black, Ink.black))
        val d = draw(m, OverKind.Sheet, 380)
        val r = d.plan.rows[1]
        val sy = r.top + (129 - 51) / 2
        assertEquals("no word over the square", 0, d.most(48, 48 + 51, sy, sy + 51))
        assertEquals("and none beside it", panel, d.most(48 + 51 + 12, 672, r.top, r.top + r.h))
    }

    @Test
    fun aPressedItemGlowsUnderItsWord() {
        val m = sheet(listOf("play", "play next"))
        val plain = draw(m, OverKind.Sheet, 380)
        m.press(1, 330)
        val lit = draw(m, OverKind.Sheet, 380)
        val r = lit.plan.rows[1]
        val y = r.top + lit.ink.rows[1].base - 15
        assertTrue("glow ${lit.red(45, y)} plain ${plain.red(45, y)}", lit.red(45, y) > plain.red(45, y) + 10)
        near(panel, lit.red(30, y), 0)
    }

    @Test
    fun aLongTitleIsCutAtTheRightEdge() {
        val m = sheet(listOf("play"), title = "a very long sheet title that runs well past the right edge")
        val d = draw(m, OverKind.Sheet, 380)
        val t = d.plan.titleRect!!
        assertEquals(720 - 48, t.x1)
        assertEquals(panel, d.most(672, 720, t.y0, t.y1))
        assertTrue(d.most(400, 672, t.y0, t.y1) > 100)
    }

    @Test
    fun aCjkSheetTitleDrawsItsKanjiThroughCoversAtRegular() {
        val m = sheet(listOf("play"), title = "play 漢字")
        assertEquals("with no covers only the Han glyphs split the title", 3, menuInk(measurer, fonts, m, a105, unitDensity, true, null).title.runs.size)
        val d = draw(m, OverKind.Sheet, 380)
        val runs = d.ink.title.runs
        assertEquals("each Han glyph is its own run", listOf("play ", "漢", "字"), runs.map { it.layout.layoutInput.text.text })
        assertEquals(FontWeight.Normal, runs[1].layout.layoutInput.style.fontWeight)
        val t = d.plan.titleRect!!
        val kanji = 48 + ceil(runs[1].x).toInt()
        assertTrue(d.most(kanji, 672, t.y0, t.y1) > 100)
    }

    @Test
    fun onTheP2160PageAValueEndsAtTheWindowLessTheEdge() {
        val p2160 = scale(Window(1080, 2160, 35, none, none, none, none, none, Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)), Nav.None, Surface.Page, false)
        assertEquals(364.04f, p2160.w, 0.005f)
        val m = sheet(listOf("one", "two"))
        m.cells(listOf("on", "off"), listOf(false, false), -1)
        val ink = menuInk(measurer, fonts, m, p2160, unitDensity, true, null)
        assertEquals(47, ink.edge)
        assertEquals(1033, ink.titleClip)
        for (r in ink.rows) assertEquals(66082, r.vx64 + pen64(r.value!!.width))
    }

    @Test
    fun aSecondSheetReadsNoPanelTopUntilItsOwnSettle() {
        val m = sheet(listOf("off", "default", "chu moy", "jan meier"), title = "crossfeed")
        m.settle(m.plan(menuInk(measurer, fonts, m, a105, unitDensity, true, covers)))
        val four = m.panelTop
        assertTrue("premise: a sheet's settle gives its panel top, $four", four >= 0)
        m.open(MenuForm.Sheet, "stereo width", listOf("off", "50%", "75%", "125%", "150%", "200%"), 0)
        assertEquals("a reopened menu holds no panel top of the sheet before it", -1, m.panelTop)
        m.settle(m.plan(menuInk(measurer, fonts, m, a105, unitDensity, true, covers)))
        assertTrue("the taller sheet's panel starts higher: $four then ${m.panelTop}", m.panelTop in 0 until four)
    }
}
