package net.seilent.funekit.kit

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.Roles
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeTest {
    private val none = Insets(0, 0, 0, 0)

    private fun page(w: Int, h: Int, large: Boolean = false) =
        scale(Window(w, h, 28, none, none, none, none, none), Nav.None, Surface.Page, large)

    private val v1 = page(240, 320)
    private val a105 = page(720, 1280)
    private val a105Large = page(720, 1280, large = true)

    private fun w(width: Float, l: Float, r: Float) = Word("x", emptyList(), width, l, r, 0f, 0f)

    private fun pivot(rest: Float, active: Float, k: Float = 1f) = PivotInk(
        w(rest * k, k, (rest - 2) * k),
        w(active * k, 2 * k, (active - 1) * k),
        w(rest, 1f, rest - 2),
        w(active, 2f, active - 1),
    )

    private fun chrome(s: Scale, k: Float = 1f) =
        Chrome(s, w(300f * k, 0f, 300f * k), listOf(pivot(60f, 70f, k), pivot(80f, 90f, k), pivot(50f, 55f, k)))

    @Test
    fun theActiveLabelSitsAtTheHubEdge() {
        val c = chrome(v1)
        assertEquals(20f, c.pivotX(0), 0.001f)
        c.select(1)
        assertEquals(20f, c.pivotX(1), 0.001f)
        c.select(2)
        assertEquals(20f, c.pivotX(2), 0.001f)
    }

    @Test
    fun labelsAfterTheActiveOneSitInkToInk() {
        val c = chrome(v1)
        val x1 = 20f + (70f - 1f) + 7.7f - 1f
        assertEquals(x1, c.pivotX(1), 0.001f)
        assertEquals(x1 + (80f - 2f) + 6.6f - 1f, c.pivotX(2), 0.001f)
    }

    @Test
    fun theLabelBeforeTheActiveOneEndsTwentyTwoUnitsBeforeIt() {
        val c = chrome(v1)
        c.select(1)
        assertEquals(20f - 22f - 60f, c.pivotX(0), 0.001f)
        assertEquals(20f + (90f - 1f) + 7.7f - 1f, c.pivotX(2), 0.001f)
    }

    @Test
    fun gapsScaleWithThePagePpu() {
        val c = chrome(a105, 3f)
        assertEquals(60f + (70f - 1f) * 3 + 7.7f * 3 - 3f, c.pivotX(1), 0.01f)
    }

    @Test
    fun headerWordPenAndBandAtStandardPxAtBothSizes() {
        val std = chrome(a105)
        assertEquals(-66f, std.headerRestX(), 0.001f)
        assertEquals(listOf(-66, 0, 234, 210), std.headerBox().let { listOf(it.x0, it.y0, it.x1, it.y1) })
        val large = chrome(a105Large)
        assertEquals(-66f, large.headerRestX(), 0.001f)
        assertEquals(210, large.headerBox().y1)
    }

    @Test
    fun pivotBandsPerBoard() {
        val v = chrome(v1).pivotBox(0)
        assertEquals(listOf(58, 86), listOf(v.y0, v.y1))
        val a = chrome(a105).pivotBox(0)
        assertEquals(listOf(189, 278), listOf(a.y0, a.y1))
        assertEquals(listOf(20, 90), listOf(chrome(v1).pivotBox(0).x0, chrome(v1).pivotBox(0).x1))
    }

    @Test
    fun theStripMovesOnePitchToItsRestInWholePixels() {
        val c = chrome(v1)
        c.switchTo(1, 1000, 1)
        val d = c.pivotOffset(1) - c.pivotOffset(0)
        val rest = c.row(1236)
        assertEquals(rest + Math.round(d), c.row(1000), 0.001f)
        val mid = c.row(1118) - rest
        assertEquals(Math.round(d * (1 - Curves.expOut6.at(0.5f))).toFloat(), mid, 0f)
        assertEquals(mid, Math.round(mid).toFloat(), 0f)
        assertEquals(Roles.pivotActive.alpha, c.pivotAlpha(1, 1000), 0.001f)
        assertEquals(Roles.pivotInactive.alpha, c.pivotAlpha(2, 1000), 0.001f)
        assertTrue(c.moving(1235))
        assertFalse(c.moving(1236))
        c.switchTo(0, 2000, -1)
        assertEquals(c.row(2236) + Math.round(c.pivotOffset(0) - c.pivotOffset(1)), c.row(2000), 0.001f)
    }

    @Test
    fun aTappedPivotStaysLitThroughItsSwitchAndAHeaderUntilCleared() {
        val c = chrome(v1)
        c.press(1)
        assertTrue(c.lit(1, 0))
        assertEquals(1f, c.pivotAlpha(1, 0), 0f)
        c.switchTo(1, 10, 1)
        c.lift()
        assertTrue(c.lit(1, 245))
        assertFalse(c.lit(1, 246))
        c.clear()
        c.press(Chrome.HEADER)
        c.lift()
        assertTrue(c.lit(Chrome.HEADER, 5000))
        c.clear()
        assertFalse(c.lit(Chrome.HEADER, 5000))
    }

    @Test
    fun aLiftWithNoSwitchRestsAtOnce() {
        val c = chrome(v1)
        c.press(2)
        c.lift()
        assertFalse(c.lit(2, 0))
    }

    @Test
    fun partsResetAfterAMoveFrame() {
        val c = chrome(v1)
        c.headerPart = ChromePart(dx = 4f, alpha = 0.5f)
        c.pivotParts[1] = ChromePart(hidden = true)
        c.clearParts()
        assertEquals(0f, c.headerPart.dx, 0f)
        assertEquals(1f, c.headerPart.alpha, 0f)
        assertFalse(c.pivotParts[1].hidden)
    }

    @Test
    fun theTapLiftsBeforeItsSwitchAndSettlesAfterAsFuneOsDoes() {
        val c = chrome(v1)
        c.press(1)
        c.lift()
        c.switchTo(1, 10, 1)
        assertFalse(c.lit(1, 100))
        c.settle()
        assertTrue(c.lit(1, 100))
        c.tick(245, leavesLit = false)
        assertEquals(1, c.pressed)
        c.tick(246, leavesLit = false)
        assertEquals(null, c.pressed)
    }

    @Test
    fun aLiftedHeaderStaysLitOnlyWhileTheMoveLeavesItLit() {
        val c = chrome(v1)
        c.press(Chrome.HEADER)
        c.tick(0, leavesLit = false)
        assertEquals(Chrome.HEADER, c.pressed)
        c.lift()
        c.settle()
        c.tick(0, leavesLit = true)
        assertTrue(c.lit(Chrome.HEADER, 0))
        c.tick(16, leavesLit = false)
        assertEquals(null, c.pressed)
    }
}
