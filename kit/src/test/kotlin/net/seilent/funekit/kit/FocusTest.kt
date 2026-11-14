package net.seilent.funekit.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTest {
    @Test
    fun theTitlesCrossFadeOver200msAndTheTrackAnimatesUntilTheAlphaLegsEnd() {
        val f = FocusTrack()
        f.set(1, 3, 0, true)
        assertEquals(0.8f, f.rowAlpha(1, 100, 1f, 0.6f), 0.001f)
        assertEquals(0.8f, f.rowAlpha(0, 100, 1f, 0.6f), 0.001f)
        assertEquals(1f, f.rowAlpha(1, 200, 1f, 0.6f), 0f)
        assertEquals(0.6f, f.rowAlpha(0, 200, 1f, 0.6f), 0f)
        assertEquals(0.6f, f.rowAlpha(2, 100, 1f, 0.6f), 0f)
        assertTrue(f.animating(120))
        assertTrue(f.animating(199))
        assertFalse(f.animating(200))
    }

    @Test
    fun aJumpWithNoMotionLandsAtOnce() {
        val f = FocusTrack()
        f.set(2, 3, 0, false)
        assertEquals(1f, f.rowAlpha(2, 0, 1f, 0.6f), 0f)
        assertFalse(f.animating(0))
        f.set(5, 3, 0, false)
        assertEquals(2, f.row)
    }

    @Test
    fun aStepDuringTheFadeFadesTheLeavingRowOutFromTheAlphaItReached() {
        val f = FocusTrack()
        f.set(1, 3, 0, true)
        assertEquals(0.7f, f.rowAlpha(1, 50, 1f, 0.6f), 0.001f)
        f.set(2, 3, 50, true)
        assertEquals(0.7f, f.rowAlpha(1, 50, 1f, 0.6f), 0.001f)
        assertEquals(0.65f, f.rowAlpha(1, 150, 1f, 0.6f), 0.001f)
        assertEquals(0.8f, f.rowAlpha(2, 150, 1f, 0.6f), 0.001f)
        assertEquals(0.6f, f.rowAlpha(0, 50, 1f, 0.6f), 0f)
        assertTrue(f.animating(249))
        assertFalse(f.animating(250))
    }

    @Test
    fun aRowStillWhollyInTheViewKeepsTheFocus() {
        assertEquals(4, focusAfterScroll(4, 20, 120, 280, 40))
        assertEquals(9, focusAfterScroll(9, 20, 120, 280, 40))
    }

    @Test
    fun aRowScrolledAboveTheViewGivesTheFirstWholeRow() {
        assertEquals(7, focusAfterScroll(4, 20, 262, 280, 40))
        assertEquals(7, focusAfterScroll(4, 20, 280, 280, 40))
        assertEquals(8, focusAfterScroll(7, 20, 281, 280, 40))
    }

    @Test
    fun aRowBelowTheViewGivesTheLastWholeRow() {
        assertEquals(6, focusAfterScroll(12, 20, 0, 280, 40))
        assertEquals(6, focusAfterScroll(12, 20, 10, 280, 40))
        assertEquals(7, focusAfterScroll(12, 20, 40, 280, 40))
        assertEquals(9, focusAfterScroll(10, 20, 120, 280, 40))
    }

    @Test
    fun theFocusIsClampedToTheRowsAndToTheFirstRowWhenNoneIsWhole() {
        assertEquals(4, focusAfterScroll(0, 5, 300, 280, 40))
        assertEquals(1, focusAfterScroll(0, 20, 10, 30, 40))
        assertEquals(1, focusAfterScroll(5, 20, 10, 30, 40))
        assertEquals(3, focusAfterScroll(3, 0, 300, 280, 40))
    }

    @Test
    fun theCollapseLiftMovesTheBandTopUp() {
        assertEquals(1, focusAfterScroll(0, 20, 30, 280, 40))
        assertEquals(0, focusAfterScroll(0, 20, 30, 280, 40, 50))
        assertEquals(3, focusAfterScroll(1, 20, 120, 280, 40))
        assertEquals(2, focusAfterScroll(1, 20, 120, 280, 40, 50))
    }

    @Test
    fun theFocusAfterAScrollLandsWithNoMotion() {
        val f = FocusTrack()
        f.set(4, 20, 0, true)
        f.afterScroll(20, 262, 280, 40, 50)
        assertEquals(7, f.row)
        assertEquals(1f, f.rowAlpha(7, 50, 1f, 0.6f), 0f)
        assertEquals(0.6f, f.rowAlpha(4, 50, 1f, 0.6f), 0f)
        f.afterScroll(20, 270, 280, 40, 60)
        assertEquals(7, f.row)
    }

    @Test
    fun atAPxPitchOf163UnderA1804PxViewElevenRowsAreWholeInPx() {
        assertEquals(10, focusAfterScroll(10, 30, 0, 1804, 163))
        assertEquals(10, focusAfterScroll(11, 30, 0, 1804, 163))
        assertEquals(4, focusAfterScroll(2, 30, 539, 1804, 163))
        assertEquals(5, focusAfterScroll(5, 30, 539, 1804, 163))
        assertEquals(13, focusAfterScroll(14, 30, 539, 1804, 163))
    }
}
