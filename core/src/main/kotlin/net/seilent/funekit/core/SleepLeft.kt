package net.seilent.funekit.core

object SleepLeft {
    private const val MINUTE_MS = 60_000L

    fun minutes(leftMs: Long): Int = ((leftMs + MINUTE_MS - 1) / MINUTE_MS).toInt()

    fun nextDropMs(leftMs: Long): Long = (leftMs - 1) % MINUTE_MS + 1
}
