package net.seilent.funekit.screens.music.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetSheetTest {
    private class Edit : OffsetEdit {
        val calls = mutableListOf<String>()
        override var shownMs = 0
        override var editing = false

        override fun begin() {
            calls += "begin"
            editing = true
        }

        override fun step(d: Int) {
            calls += "step $d"
            shownMs += 100 * d
        }

        override fun coarse(d: Int) {
            calls += "coarse $d"
            shownMs += 1000 * d
        }

        override fun keep() {
            calls += "keep"
            editing = false
        }

        override fun restore() {
            calls += "restore"
            editing = false
        }

        override fun entry(adjust: Boolean) {
            calls += "entry $adjust"
        }

        override fun end() {
            calls += "end"
        }
    }

    @Test
    fun theOverlayOpensOnlyForAnOpenEdit() {
        val e = Edit()
        val s = OffsetSheet(e)
        assertFalse(s.adjust(0))
        assertFalse(s.hud.shown)
        s.press(0)
        assertTrue(s.adjust(10))
        assertFalse(s.adjust(20))
    }

    @Test
    fun aTapOnTheRowBeginsTheEditAndOpensTheAdjustOverlay() {
        val e = Edit()
        val s = OffsetSheet(e)
        s.press(0)
        s.adjust(1000)
        s.press(0)
        s.adjust(1100)
        assertEquals(listOf("begin"), e.calls)
        assertTrue(s.hud.shown)
        assertEquals("lyrics", s.hud.line1)
        assertEquals("0 MS", s.hud.line2)
        assertEquals(listOf(AdjustCol("0", "offset ms", up = true, down = true)), s.hud.cols)
    }

    @Test
    fun plusAndMinusStepTheOffsetAndExitKeepsIt() {
        val e = Edit()
        val s = OffsetSheet(e)
        s.press(0)
        s.adjust(0)
        s.tap(AdjustHit.Up0)
        s.tap(AdjustHit.Up0)
        s.tap(AdjustHit.Down0)
        assertEquals("100", s.hud.cols.single().num)
        assertEquals("+100 MS", s.hud.line2)
        s.tap(AdjustHit.None)
        s.tap(AdjustHit.Exit)
        assertFalse(s.hud.shown)
        assertEquals(listOf("begin", "step 1", "step 1", "step -1", "keep"), e.calls)
    }

    @Test
    fun theEndsOfTheRangeDimTheirControl() {
        val e = Edit()
        val s = OffsetSheet(e)
        e.shownMs = 60000
        s.press(0)
        s.adjust(0)
        assertEquals(AdjustCol("60000", "offset ms", up = false, down = true), s.hud.cols.single())
        e.shownMs = -60000
        s.tap(AdjustHit.Down0)
        assertFalse(s.hud.cols.single().down)
    }

    @Test
    fun backInTheOverlayRestoresAndReturnsToTheSheet() {
        val e = Edit()
        val s = OffsetSheet(e)
        s.press(0)
        s.adjust(0)
        assertTrue(s.back())
        assertFalse(s.hud.shown)
        assertFalse(s.back())
        assertEquals(listOf("begin", "restore"), e.calls)
    }

    @Test
    fun keysStepTheOffsetCoarseOnlyWhileTheOverlayIsOpen() {
        val e = Edit()
        val s = OffsetSheet(e)
        assertFalse(s.key(1))
        s.press(0)
        s.adjust(0)
        assertTrue(s.key(-1))
        assertEquals("-1000", s.hud.cols.single().num)
        s.tap(AdjustHit.Exit)
        assertFalse(s.key(1))
        assertEquals(listOf("begin", "coarse -1", "keep"), e.calls)
    }

    @Test
    fun theScrimWhileEditingDropsTheEditAndKeepsTheSheet() {
        val e = Edit()
        val s = OffsetSheet(e)
        assertFalse(s.scrim())
        e.editing = true
        assertTrue(s.scrim())
        assertEquals(listOf("restore"), e.calls)
    }

    @Test
    fun aHoldOnTheRowOrTheNumberOpensTheEntry() {
        val e = Edit()
        val s = OffsetSheet(e)
        assertFalse(s.hold(1))
        assertTrue(s.hold(0))
        s.press(0)
        s.adjust(0)
        assertTrue(s.holdNumber())
        assertFalse(s.hud.shown)
        assertFalse(s.holdNumber())
        s.dismiss()
        assertEquals(listOf("entry false", "begin", "keep", "entry true", "end"), e.calls)
    }

    @Test
    fun aHoldOrADismissDuringAnEditDropsTheEditFirst() {
        val e = Edit()
        val s = OffsetSheet(e)
        s.press(0)
        s.adjust(0)
        assertTrue(s.hold(0))
        assertFalse(e.editing)
        s.press(0)
        s.adjust(0)
        s.dismiss()
        assertFalse(s.hud.shown)
        assertEquals(listOf("begin", "restore", "entry false", "begin", "restore", "end"), e.calls)
    }

    @Test
    fun theValueReadsAsTheDeviceWritesIt() {
        val e = Edit()
        val s = OffsetSheet(e)
        e.shownMs = 300
        assertEquals("+300 MS", s.value())
        e.shownMs = -1200
        assertEquals("-1200 MS", s.value())
        assertEquals(-1, s.field())
        e.editing = true
        assertEquals(0, s.field())
    }
}
