package net.seilent.funekit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepLeftTest {
    @Test
    fun minutesRoundUp() {
        assertEquals(30, SleepLeft.minutes(1_800_000))
        assertEquals(30, SleepLeft.minutes(1_799_999))
        assertEquals(29, SleepLeft.minutes(1_740_000))
        assertEquals(29, SleepLeft.minutes(1_739_999))
        assertEquals(1, SleepLeft.minutes(1))
        assertEquals(0, SleepLeft.minutes(0))
    }

    @Test
    fun nextDropIsWhenTheRoundedMinuteChanges() {
        assertEquals(60_000L, SleepLeft.nextDropMs(1_800_000))
        assertEquals(1L, SleepLeft.nextDropMs(1_740_001))
        assertEquals(59_999L, SleepLeft.nextDropMs(59_999))
    }
}
