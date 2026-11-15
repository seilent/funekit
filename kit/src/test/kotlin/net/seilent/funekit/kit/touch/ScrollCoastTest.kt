package net.seilent.funekit.kit.touch

import android.view.ViewConfiguration
import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.math.exp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScrollCoastTest {
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

    private val spline = SplineBasedFloatDecayAnimationSpec(Density(DENSITY))

    private fun at(ms: Long, v0: Float) = spline.getValueFromNanos(TimeUnit.MILLISECONDS.toNanos(ms), 0f, v0)

    private fun coast(line: Line, kind: Kind = Kind.List, page: Float = 1f) = Scroll(kind, TOUCH, page, page, line, spline)

    @Before
    fun theFrictionIsAndroids() {
        assertEquals(0.015f, ViewConfiguration.getScrollFriction(), 0f)
    }

    @Test
    fun travelAndEndAreTheAndroidSplineAtTheFlick() {
        val cases = listOf(Triple(900f, 76.84f, 243L), Triple(3369f, 760.19f, 644L), Triple(5043f, 1531.42f, 867L), Triple(9000f, 4186.56f, 1329L))
        for ((v, travel, end) in cases) {
            val s = coast(Line(0f, 100000f))
            assertEquals("$v", travel, s.travelOf(v), 0.01f)
            assertEquals("$v", travel, spline.getTargetValue(0f, v), 0.01f)
            assertEquals("$v", end, TimeUnit.NANOSECONDS.toMillis(spline.getDurationNanos(0f, v)))
        }
        assertEquals(307.31f, at(100, 3369f), 0.01f)
        assertEquals(633.32f, at(300, 3369f), 0.01f)
        assertEquals(477.77f, at(100, 5043f), 0.01f)
        assertEquals(1098.13f, at(300, 5043f), 0.01f)
    }

    private fun run(frameMs: Long): Int {
        val line = Line(0f, 100000f)
        val s = coast(line)
        s.release(0, -3369f)
        assertTrue(s.flinging)
        assertEquals(3369f, s.v, 0f)
        var steps = 0
        while (s.moving) {
            steps++
            s.step(steps * frameMs)
            if (s.moving) assertEquals("$frameMs ms step $steps", at(steps * frameMs, 3369f), line.pos, 0.01f)
        }
        assertEquals("$frameMs ms", 760.19f, line.pos, 0.01f)
        assertEquals(0f, s.v, 0f)
        return steps
    }

    @Test
    fun aCoastStepsTheSplineAndHaltsOnTheFirstStepAtOrPastItsEnd() {
        assertEquals(41, run(16))
        assertEquals(81, run(8))
        assertEquals(20, run(33))
    }

    @Test
    fun aFrameGapAdvancesTheCoastByTheStepCapOnly() {
        val line = Line(0f, 100000f)
        val s = coast(line)
        s.release(0, -3369f)
        s.step(100)
        assertEquals(at(33, 3369f), line.pos, 0.01f)
        s.step(116)
        assertEquals(at(49, 3369f), line.pos, 0.01f)
    }

    private fun released(steps: Int): Scroll {
        val s = coast(Line(0f, 100000f))
        s.release(0, -3369f)
        for (i in 1..steps) s.step(i * 16L)
        assertTrue(s.flinging)
        return s
    }

    @Test
    fun aDownJudgesTheCoastBySpeedOnTheSplineClockAtTheDown() {
        assertEquals(260.69f, spline.getVelocityFromNanos(TimeUnit.MILLISECONDS.toNanos(500), 0f, 3369f), 0.01f)
        assertEquals(191.97f, spline.getVelocityFromNanos(TimeUnit.MILLISECONDS.toNanos(530), 0f, 3369f), 0.01f)
        val fast = released(31)
        assertTrue(fast.grab(500))
        assertFalse(fast.moving)
        assertFalse(released(33).grab(530))
        assertTrue(released(32).grab(500))
        assertTrue(released(33).grab(512))
        assertFalse(released(33).grab(516))
    }

    @Test
    fun aHaltStopsACoast() {
        val line = Line(0f, 100000f)
        val s = coast(line)
        s.release(0, -3369f)
        for (i in 1..5) s.step(i * 16L)
        s.halt()
        val p = line.pos
        assertFalse(s.moving)
        assertFalse(s.step(96))
        assertEquals(p, line.pos, 0f)
    }

    @Test
    fun aCoastIntoAListEndCarriesTheVelocityAtTheStepStartAndReturns() {
        val line = Line(0f, 500f)
        val s = coast(line, page = PAGE)
        s.release(0, -3369f)
        var t = 0L
        while (t < 192L) {
            t += 16
            s.step(t)
            assertTrue(s.flinging)
            assertEquals(0f, s.over, 0f)
        }
        assertEquals(1649.2f, s.v, 0.1f)
        s.step(208)
        assertEquals(500f, line.pos, 0f)
        assertFalse(s.flinging)
        assertTrue(s.returning)
        assertEquals(1649.2f * 7 / 1000, s.over, 0.01f)
        t = 208
        while (t < 544L) {
            t += 16
            s.step(t)
            assertTrue("$t", s.over > 0f)
        }
        s.step(560)
        assertEquals(0f, s.over, 0f)
        assertFalse(s.moving)
    }

    @Test
    fun aGridGivenACoastClampsAtItsEnd() {
        val line = Line(0f, 500f)
        val s = coast(line, Kind.Grid, PAGE)
        s.release(0, -3369f)
        var t = 0L
        while (s.moving) {
            t += 16
            s.step(t)
        }
        assertEquals(208L, t)
        assertEquals(500f, line.pos, 0f)
        assertEquals(0f, s.over, 0f)
    }

    @Test
    fun quickplayGivenACoastReturnsOnItsOwnTimeConstant() {
        val line = Line(0f, 500f)
        val s = coast(line, Kind.Quickplay, PAGE)
        s.release(0, -3369f)
        for (t in 16L..208L step 16L) s.step(t)
        assertEquals(1649.2f * 7 / 1000, s.over, 0.01f)
        val o = s.over
        s.step(224)
        assertEquals(o * exp(-16f / 100f), s.over, 0.001f)
    }

    companion object {
        private const val DENSITY = 2.75f
        private const val TOUCH = 4.5f
        private const val PAGE = 2.96669f
    }
}
