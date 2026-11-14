package net.seilent.funekit.kit

import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun Menu.placed(ink: MenuInk): MenuPlan = plan(ink).also { settle(it) }

class MenuTest {
    private val edge = 16 * 3
    private val pitch = 43 * 3
    private val titleAscent = 66
    private val titleDescent = 14
    private val statusBand = 22 * 3

    private fun ink(n: Int, pin: Int = 0) = MenuInk(
        3f, pitch, edge, Word.none, titleAscent + titleDescent, titleAscent, titleDescent, 79 * 3, 98 * 3, 720 - edge, pin, false, 33,
        List(n) { MenuRow(Word.none, null, null, null, null, Int.MIN_VALUE, 720 - edge, pitch, 80, 80, 51, false, false, emptyList(), Edges(0, 0, 720 - edge, pitch)) },
    )

    private fun sheet(n: Int, readOnly: Boolean = false): Menu = Menu().also {
        it.open(MenuForm.Sheet, "play", List(n) { i -> "item $i" }, 0, readOnly)
        it.frame(statusBand, 1280)
    }

    @Test
    fun aSheetIsEdgeTitleItemsEdgeTallAtTheSurfaceBottom() {
        val m = sheet(3)
        val p = m.placed(ink(3))
        assertEquals(edge + 80 + 3 * pitch + edge, p.panelH)
        assertEquals(1280 - p.panelH + edge + 80, p.top)
        assertEquals(1280 - p.panelH + edge + titleAscent, p.titleBase)
        assertEquals(listOf(p.top, p.top + pitch, p.top + 2 * pitch), p.rows.map { it.top })
        assertEquals(listOf(0, 1, 2), p.rows.map { it.i })
        assertEquals(listOf(pitch, pitch, pitch), p.rows.map { it.h })
    }

    @Test
    fun aTallSheetShowsWhatFitsBelowTheStatusBandAndFollowsTheFocus() {
        val m = sheet(20)
        val room = 1280 - statusBand - 2 * edge - 80
        var p = m.placed(ink(20))
        assertEquals(room / pitch, p.rows.size)
        assertEquals(0, p.rows.first().i)
        repeat(12) { m.step(1, 0) }
        p = m.placed(ink(20))
        assertEquals(12 - room / pitch + 1, p.rows.first().i)
        assertEquals(12, p.rows.last().i)
        assertEquals(p.top + (room / pitch - 1) * pitch, p.rows.last().top)
    }

    @Test
    fun aValueSheetWrapsOnlyWhenAsked() {
        val m = sheet(3)
        m.value(2, 2, wrap = false, now = 0)
        assertFalse(m.step(1, 0))
        m.value(2, 2, wrap = true, now = 0)
        assertTrue(m.step(1, 0))
        assertEquals(0, m.focus.row)
        assertFalse(m.step(-1, 0, hold = true))
        assertEquals(0, m.focus.row)
    }

    @Test
    fun aReadOnlySheetScrollsByRowsAndTakesNoPress() {
        val m = sheet(20, readOnly = true)
        m.placed(ink(20))
        assertTrue(m.step(1, 0))
        val p = m.placed(ink(20))
        assertEquals(1, p.rows.first().i)
        m.press(3, 0)
        assertEquals(-1, m.pressed)
        assertEquals(null, m.glow.key)
    }

    @Test
    fun aPressLightsTheItemAndStartsItsGlow() {
        val m = sheet(3)
        m.placed(ink(3))
        m.press(1, 40)
        assertEquals(1, m.pressed)
        assertEquals(1, m.glow.key)
        assertEquals(40L, m.glow.at)
        m.press(-1, 50)
        assertEquals(null, m.glow.key)
    }

    @Test
    fun aCappedKeptSheetDropsRowsToItsPanel() {
        val m = sheet(20)
        m.keep(kept = true, capped = true, maxPanel = 600)
        val p = m.placed(ink(20))
        assertEquals((600 - 2 * edge - 80) / pitch, p.rows.size)
        assertTrue(p.panelH <= 600)
    }

    @Test
    fun theBackTargetIsAboveTheRisingPanelOffTheRowsAndNeverInTheNavBand() {
        val m = sheet(3)
        val p = m.placed(ink(3))
        val o = Overlay(OverKind.Sheet).also { it.open(0) }
        val risingTop = 1280 - p.panelH + Math.round(o.sheet(100).dy * p.panelH)
        assertTrue(risingTop > 1280 - p.panelH)
        assertTrue(m.backTarget(360f, risingTop - 1f, o, 100, 1280))
        assertFalse(m.backTarget(360f, risingTop + 1f, o, 100, 1280))
        val row = p.rows[1]
        assertFalse(m.backTarget(360f, row.top + 1f, o, 10_000, 1280))
        assertTrue(m.backTarget(360f, 1280f - p.panelH - 1f, o, 10_000, 1280))
        assertFalse(m.backTarget(360f, 1200f, o, 100, 1180))
        assertFalse(m.backTarget(360f, 30f, o, 100, 20))
    }
}
