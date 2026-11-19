package net.seilent.funekit.screens.music

import androidx.compose.ui.geometry.Offset
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.PivotMotion
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PivotPageTest {
    private class Stub : PivotPage(1, MusicPage.History) {
        val motion = PivotMotion()
        val dragged = ArrayList<Float>()
        val switched = ArrayList<Int>()
        var released = 0
        var landed = 0
        var halted = 0
        override var switchDir = 1

        override fun canSwitch(d: Int): Boolean = pivot + d in 0..1

        override fun switchTo(p: Int, now: Long): Boolean {
            if (p == pivot) return false
            val dir = if (p > pivot) 1 else -1
            switchDir = dir
            switched += p
            pivot = p
            motion.begin(now, dir)
            return true
        }

        override fun drag(dxU: Float, now: Long) {
            dragged += dxU
        }

        override fun release(now: Long) {
            released++
        }

        override fun land() {
            landed++
            motion.end()
        }

        override fun dragX(now: Long, ppu: Float): Int = 0

        override fun motionActive(now: Long): Boolean = motion.active(now)

        override fun haltList() {
            halted++
        }

        override fun poke() = Unit
    }

    private val none = Insets(0, 0, 0, 0)
    private val chrome = Chrome(scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false), Word.none, emptyList())

    @Test
    fun thePanBeginsAtTheLockAndDragsByTheFingerFromTheOrigin() {
        val m = Stub()
        var ok = false
        val pan = pivotPan(m, chrome, { PPU }, { NOW }) { ok }
        assertFalse(pan.begin(60f, 20f, Offset.Zero))
        assertTrue(m.dragged.isEmpty())
        ok = true
        assertTrue(pan.begin(60f, 20f, Offset.Zero))
        pan.drag(100f)
        assertEquals(listOf(20f, 40f), m.dragged)
    }

    @Test
    fun aReleaseThatStepsAPivotSwitchesAndAnyOtherReturns() {
        val m = Stub()
        val pan = pivotPan(m, chrome, { PPU }, { NOW }) { true }
        pan.begin(0f, 0f, Offset.Zero)
        pan.end(Swipe.Left)
        assertEquals(listOf(1), m.switched)
        assertEquals(1, chrome.active)
        assertEquals(1, m.halted)
        pan.begin(0f, 0f, Offset.Zero)
        pan.end(Swipe.Left)
        assertEquals(1, m.released)
        pan.begin(0f, 0f, Offset.Zero)
        pan.end(Swipe.None)
        assertEquals(2, m.released)
        pan.begin(0f, 0f, Offset.Zero)
        pan.end(Swipe.Right)
        assertEquals(listOf(1, 0), m.switched)
        assertEquals(0, chrome.active)
    }

    @Test
    fun aDownLandsARunningSwitchAndNothingAtRest() {
        val m = Stub()
        pivotLand(m, chrome, NOW)
        assertEquals(0, m.landed)
        pivotSwitch(m, chrome, 1, NOW)
        assertTrue(m.motionActive(NOW + 1))
        pivotLand(m, chrome, NOW + 1)
        assertEquals(1, m.landed)
        assertEquals(1, chrome.active)
        assertFalse(m.motionActive(NOW + 1))
    }

    private companion object {
        const val PPU = 2f
        const val NOW = 1_000L
    }
}
