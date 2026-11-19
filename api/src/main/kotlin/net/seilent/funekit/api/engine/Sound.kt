package net.seilent.funekit.api.engine

enum class ReplayGain { Off, Track, Album }

enum class Crossfeed { Off, Default, ChuMoy, JanMeier }

enum class Tube { Off, Low, Mid, High }

data class Sound(
    val gapless: Boolean,
    val crossfadeMs: Int,
    val replayGain: ReplayGain,
    val preampDb: Float,
    val crossfeed: Crossfeed,
    val width: Int,
    val tube: Tube,
    val output: Output,
    val volume: Int?,
    val volumePct: Int?,
    val volumeStepPct: Int?,
)

data class Outputs(
    val offered: List<Output> = listOf(Output.System),
    val dac: String? = null,
    val held: Boolean = false,
    val asks: Boolean = false,
    val usbWhy: String? = null,
    val dop: Boolean? = null,
)
