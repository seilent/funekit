package net.seilent.funekit.api

enum class Output { System, BitPerfect, Usb }

data class Dac(val vendor: Int, val product: Int, val serial: String)

data class Format(val rate: Int, val bits: Int, val channels: Int)

data class Anchor(val frame: Long, val rate: Int, val elapsedNanos: Long, val running: Boolean, val durationMs: Long) {
    fun ms(nowElapsedNanos: Long): Long {
        if (rate <= 0) return 0
        val base = frame / rate * NANOS + frame % rate * NANOS / rate
        val run = if (running) (nowElapsedNanos - elapsedNanos).coerceAtLeast(0) else 0
        val ms = ((base + run) / MILLI).coerceAtLeast(0)
        return if (durationMs > 0) ms.coerceAtMost(durationMs) else ms
    }

    companion object {
        private const val NANOS = 1_000_000_000L
        private const val MILLI = 1_000_000L
        val NONE = Anchor(0, 0, 0, false, 0)
    }
}

data class PlayerState(
    val queueSize: Int = 0,
    val queueRevision: Long = 0,
    val index: Int = -1,
    val track: Long? = null,
    val origin: Listing? = null,
    val playing: Boolean = false,
    val anchor: Anchor = Anchor.NONE,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.Off,
    val route: Output = Output.System,
    val resampled: Boolean = false,
    val input: Format? = null,
    val output: Format? = null,
    val xruns: Long = 0,
) {
    val durationMs: Long get() = anchor.durationMs
}
