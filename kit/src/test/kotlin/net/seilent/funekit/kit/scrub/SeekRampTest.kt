package net.seilent.funekit.kit.scrub

import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekRampTest {
    @Test
    fun theFirstStepIsTheTickStepAndTheNextIsDueARampLater() {
        val r = SeekRamp()
        r.start(1, 1000, 0, 600000)
        assertEquals(SeekRamp.Step.Moved, r.step(1000))
        assertEquals(1000L, r.target)
        assertEquals(1000L + Tok.seekRampMs.toLong(), r.stepDue())
    }

    @Test
    fun theStepGrowsByATickStepEachStage() {
        val r = SeekRamp()
        r.start(1, 1000, 0, 600000)
        assertEquals(listOf(1000L, 2000L, 3000L), listOf(1709L, 1710L, 2420L).map { r.sizeAt(it) })
        r.step(1000L + 48 * 20)
        assertEquals("funeOS player/tests/pages_test.cpp test_hold_seek :18819-18851, v0.4.4", 21, r.steps)
        assertEquals(27000L, r.target)
    }

    @Test
    fun theStepIsCappedAtTheStepMax() {
        val r = SeekRamp()
        r.start(1, 1000, 0, 600000)
        assertEquals(listOf(4000L, 4000L, 4000L), listOf(3130L, 4000L, 1000L + 710 * 10).map { r.sizeAt(it) })
    }

    @Test
    fun aReleaseOwesOneStepOfTheCurrentSizeAPressOffLater() {
        val r = SeekRamp()
        r.start(1, 1000, 0, 600000)
        r.step(1000L + 48 * 20)
        r.release(2000)
        assertTrue(r.released)
        assertEquals(2000L + Tok.motion_volumePressOff.toLong(), r.finalDue())
        assertEquals(-1L, r.stepDue())
        assertEquals(29000L, r.finalStep())
    }

    @Test
    fun forwardStopsAtTheMarginAndTheCrossingStepRunsIn() {
        val r = SeekRamp()
        r.start(1, 0, 359000, 377000)
        r.step(720)
        assertEquals("6:16, the end margin before 6:17", 376000L, r.target)
        assertEquals(SeekRamp.Step.RunIn, r.step(768))
        assertTrue(r.waiting)
        assertEquals(768L + Tok.seekRunInMs.toLong(), r.waitEnd())
        assertEquals(-1L, r.stepDue())
        assertEquals(376000L, r.target)
        assertFalse("no release owed", r.land(1500, 300000))
        assertEquals("the overshoot carried into the next song", 4000L, r.target)
        assertEquals(1548L, r.stepDue())
        assertEquals("the clock stood through the wait", 2000L, r.sizeAt(1548))
    }

    @Test
    fun aReleaseInsideTheWaitOwesTheCarryToTheLanding() {
        val r = SeekRamp()
        r.start(1, 0, 375500, 377000)
        r.step(48)
        r.release(60)
        assertTrue(r.waiting)
        assertEquals(-1L, r.finalDue())
        assertTrue(r.land(500, 300000))
        assertEquals(2000L, r.target)
    }

    @Test
    fun rewindStopsAtZeroWithNoStepDue() {
        val r = SeekRamp()
        r.start(-1, 0, 1500, 377000)
        r.step(200)
        assertEquals(0L, r.target)
        assertEquals(2, r.steps)
        assertEquals(-1L, r.stepDue())
        assertEquals(-1, r.dir)
    }

    @Test
    fun aHeldEdgeStepsNoMoreUntilARewindJoins() {
        val r = SeekRamp()
        r.start(1, 0, 375500, 377000)
        assertEquals(SeekRamp.Step.RunIn, r.step(48))
        r.holdEdge()
        assertFalse(r.waiting)
        assertTrue(r.edgeHeld)
        assertEquals(-1L, r.stepDue())
        assertEquals(SeekRamp.Step.None, r.step(500))
        r.join(-1, 500)
        assertFalse(r.edgeHeld)
        assertEquals(500L, r.stepDue())
        assertEquals(SeekRamp.Step.Moved, r.step(500))
        assertTrue(r.target < 376000L)
    }
}
