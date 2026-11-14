package net.seilent.funekit.kit.stack

import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.kit.touch.Slop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldWatchTest {
    private val slop = Slop(25f, 45f)

    private fun watch(x: Float, y: Float, target: Boolean, band: Gesture.Edge) = HoldWatch(slop, x, y, 0, target, band)

    @Test
    fun aTapOnTheTargetIsABack() {
        val w = watch(120f, 30f, target = true, band = Gesture.Edge.None)
        assertTrue(w.move(130f, 40f, 30))
        assertTrue(w.back(130f, 40f, 60, false))
    }

    @Test
    fun aTargetHoldEndsOnTheFirstMovePastTheSlop() {
        val w = watch(120f, 30f, target = true, band = Gesture.Edge.None)
        assertFalse(w.move(146f, 30f, 30))
    }

    @Test
    fun anUpPastTheSlopOnTheTargetIsNoBack() {
        val w = watch(120f, 30f, target = true, band = Gesture.Edge.None)
        assertFalse(w.back(146f, 30f, 60, false))
    }

    @Test
    fun aLeftBandKeepsAHorizontalLeaveAndBacksOnTheSwipeRight() {
        val w = watch(5f, 100f, target = false, band = Gesture.Edge.Left)
        assertTrue(w.move(35f, 100f, 20))
        assertTrue(w.move(70f, 110f, 40))
        assertTrue(w.back(70f, 110f, 60, false))
    }

    @Test
    fun aLeftBandEndsOnAVerticalLeave() {
        val w = watch(5f, 100f, target = false, band = Gesture.Edge.Left)
        assertFalse(w.move(5f, 140f, 20))
    }

    @Test
    fun onceSwipedEveryMoveIsKept() {
        val w = watch(5f, 100f, target = false, band = Gesture.Edge.Left)
        assertTrue(w.move(40f, 100f, 20))
        assertTrue(w.move(40f, 200f, 40))
    }

    @Test
    fun aRightBandSwipeOutwardIsNoBack() {
        val w = watch(235f, 100f, target = false, band = Gesture.Edge.Right)
        assertTrue(w.move(290f, 100f, 20))
        assertFalse(w.back(290f, 100f, 40, false))
    }

    @Test
    fun aRightBandSwipeLeftIsABack() {
        val w = watch(235f, 100f, target = false, band = Gesture.Edge.Right)
        assertTrue(w.move(180f, 100f, 20))
        assertTrue(w.back(180f, 100f, 40, false))
    }

    @Test
    fun aSwipeShortOfTheCommitIsNoBack() {
        val w = watch(5f, 100f, target = false, band = Gesture.Edge.Left)
        assertTrue(w.move(35f, 100f, 20))
        assertFalse(w.back(50f, 100f, 40, false))
    }
}
