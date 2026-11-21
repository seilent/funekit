package net.seilent.funekit.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorTest {
    private val second = 1_000_000_000L
    private val anchor = Anchor(44100, 44100, second, true, 10_000)

    @Test
    fun extrapolatesOnlyWhileRunning() {
        assertEquals(1_000L, anchor.ms(second))
        assertEquals(3_500L, anchor.ms(second * 7 / 2))
        assertEquals(1_000L, anchor.copy(running = false).ms(second * 9))
    }

    @Test
    fun clampsToTheDuration() {
        assertEquals(10_000L, anchor.ms(second * 60))
    }

    @Test
    fun noDurationDoesNotClamp() {
        assertEquals(60_000L, anchor.copy(durationMs = 0).ms(second * 60))
        assertEquals(60_000L, anchor.copy(durationMs = -1).ms(second * 60))
    }

    @Test
    fun aClockBeforeTheAnchorDoesNotRewind() {
        assertEquals(1_000L, anchor.ms(0))
    }

    @Test
    fun runsAtItsSpeed() {
        val fast = anchor.copy(speedPct = 150)
        assertEquals(4_000L, fast.ms(second * 3))
        assertEquals(1_500L, anchor.copy(speedPct = 50).ms(second * 2))
        assertEquals(1_000L, fast.copy(running = false).ms(second * 3))
        assertEquals(10_000L, fast.ms(second * 60))
    }

    @Test
    fun aSpeedBelowOneIsRefused() {
        for (bad in listOf(0, -50)) assertTrue("$bad", runCatching { anchor.copy(speedPct = bad) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun noRateIsZero() {
        assertEquals(0L, Anchor.NONE.ms(second))
    }
}
