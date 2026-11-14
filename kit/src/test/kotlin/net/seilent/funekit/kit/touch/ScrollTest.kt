package net.seilent.funekit.kit.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong

class ScrollTest {
    private class Line(var pos: Float, private val limit: Float) : Track {
        override val canMove = limit > 0f

        override fun by(delta: Float): Float {
            val to = (pos + delta).coerceIn(0f, limit)
            val used = to - pos
            pos = to
            return used
        }

        override fun atEnd(forward: Boolean) = if (forward) pos >= limit else pos <= 0f
    }

    private fun fling(kind: Kind, frameMs: Double): Float {
        val line = Line(0f, 100000f)
        val s = Scroll(kind, 1f, 1f, 1f, line)
        s.begin(0f)
        for (i in 1..10) s.drag(-i * 20f)
        val dragged = line.pos
        s.release(100, -2000f)
        assertEquals(2000f, s.v, 0f)
        var t = 100.0
        while (s.moving) {
            t += frameMs
            s.step(t.roundToLong())
        }
        return line.pos - dragged
    }

    @Test
    fun kIs62Point5TimesMinusLnRetention() {
        for (kind in Kind.entries) {
            val s = Scroll(kind, 1f, 1f, 1f, Line(0f, 1f))
            assertEquals(2000f / (62.5f * -ln(retention(kind))), s.travelOf(2000f), 0.01f)
        }
    }

    private fun retention(kind: Kind) = when (kind) {
        Kind.List -> 0.83f
        Kind.Quickplay -> 0.974f
        Kind.Grid -> 0.95f
    }

    @Test
    fun aListFlingAtTheCapTravels172uAtEveryFrameRate() {
        for (hz in listOf(30.0, 60.0, 120.0)) {
            val travel = fling(Kind.List, 1000.0 / hz)
            assertTrue("list at $hz Hz: $travel", abs(travel - 172f) <= 1f)
        }
    }

    @Test
    fun aGridFlingAtTheCapTravels624u() {
        for (hz in listOf(30.0, 60.0, 120.0)) {
            val travel = fling(Kind.Grid, 1000.0 / hz)
            assertTrue("grid at $hz Hz: $travel", abs(travel - 624f) <= 1f)
        }
    }

    @Test
    fun aListFlingWithNoCoastAtTouchPpu4Point5HaltsOnTheStepItsSpeedFallsUnder5PxPerSecond() {
        val line = Line(0f, 100000f)
        val s = Scroll(Kind.List, 4.5f, 4.5f, 4.5f, line)
        s.release(0, -3369f)
        assertEquals(3369f, s.v, 0f)
        var steps = 0
        while (s.moving) {
            steps++
            s.step(steps * 16L)
        }
        assertEquals(35, steps)
        assertEquals(288.87f, line.pos, 0.1f)
        assertEquals(3369f / (62.5f * -ln(0.83f)), s.travelOf(3369f), 0.01f)
    }

    @Test
    fun underFiftyUPerSecondNothingFlings() {
        val s = Scroll(Kind.List, 1f, 1f, 1f, Line(500f, 1000f))
        assertEquals(0f, s.flingVelocity(49f), 0f)
        assertEquals(-2000f, s.flingVelocity(5000f), 0f)
    }

    @Test
    fun aDragPastAnEndFollowsOneToOneUpTo7Point1uAndHolds() {
        val line = Line(0f, 1000f)
        val s = Scroll(Kind.List, 1f, 1f, 1f, line)
        s.begin(100f)
        s.drag(105f)
        assertEquals(-5f, s.over, 0.0001f)
        s.drag(130f)
        assertEquals(-7f, s.over, 0.0001f)
        s.drag(110f)
        assertEquals(-7f, s.over, 0.0001f)
        s.drag(103f)
        assertEquals(-3f, s.over, 0.0001f)
        assertEquals(0f, line.pos, 0f)
    }

    @Test
    fun theReturnIsExponentialAndNeverPassesTheEnd() {
        val s = Scroll(Kind.List, 1f, 1f, 1f, Line(0f, 1000f))
        s.begin(100f)
        s.drag(107f)
        s.release(16, 0f)
        assertTrue(s.returning)
        s.step(136)
        assertEquals(-7f * exp(-120f / 120f), s.over, 0.01f)
        var t = 136L
        while (s.returning) {
            t += 16
            s.step(t)
            assertTrue(s.over <= 0f)
        }
        assertEquals(0f, s.over, 0f)
    }

    @Test
    fun aFlingIntoAnEndOvershootsBySpeedTimes16msAtMost7Point1u() {
        val line = Line(960f, 1000f)
        val s = Scroll(Kind.List, 1f, 1f, 1f, line)
        s.begin(500f)
        s.drag(480f)
        s.drag(460f)
        s.release(20, -2000f)
        assertTrue(s.flinging)
        s.step(36)
        assertFalse(s.flinging)
        assertTrue(s.returning)
        assertEquals(7f, s.over, 0.0001f)
    }

    @Test
    fun aTouchOnAFastListOnlyStopsIt() {
        val s = Scroll(Kind.List, 1f, 1f, 1f, Line(0f, 100000f))
        s.begin(500f)
        for (i in 1..10) s.drag(500f - i * 20f)
        s.release(100, -2000f)
        assertTrue(s.flinging)
        assertTrue(s.grab(116))
        assertFalse(s.flinging)
        assertFalse(s.grab(132))
    }

    @Test
    fun aTouchBeforeTheLastStepJudgesTheSpeedBackInTime() {
        val s = Scroll(Kind.List, 1f, 1f, 1f, Line(0f, 100000f))
        s.release(100, -100f)
        s.step(116)
        assertTrue(s.flinging)
        assertEquals(83f, s.v, 0.01f)
        assertTrue(s.grab(110))
        val slow = Scroll(Kind.List, 1f, 1f, 1f, Line(0f, 100000f))
        slow.release(100, -55f)
        slow.step(116)
        assertTrue(slow.grab(100))
        val late = Scroll(Kind.List, 1f, 1f, 1f, Line(0f, 100000f))
        late.release(100, -55f)
        late.step(116)
        assertFalse(late.grab(140))
    }

    @Test
    fun aNewDragStopsAFlingAndLandsAReturn() {
        val line = Line(0f, 1000f)
        val s = Scroll(Kind.List, 1f, 1f, 1f, line)
        s.begin(100f)
        s.drag(105f)
        s.release(16, 0f)
        assertTrue(s.returning)
        s.begin(200f)
        assertFalse(s.returning)
        assertEquals(0f, s.over, 0f)
        assertTrue(s.dragging)
        s.halt()
        assertFalse(s.dragging)
        s.endDrag()
        assertFalse(s.returning)
    }

    @Test
    fun velocityIsOverTheLast100msInterpolated() {
        val v = Velocity(100)
        v.add(0, 0f)
        v.add(50, -100f)
        v.add(150, -300f)
        assertEquals(-200f * 1000f / 100f, v.perS(), 0.01f)
        val one = Velocity(100)
        one.add(10, 5f)
        assertEquals(0f, one.perS(), 0f)
        val short = Velocity(100)
        short.add(0, 0f)
        short.add(20, -40f)
        assertEquals(-2000f, short.perS(), 0.01f)
    }
}
