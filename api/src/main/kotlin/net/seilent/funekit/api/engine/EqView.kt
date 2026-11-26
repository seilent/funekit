package net.seilent.funekit.api.engine

const val EQ_OFF = "off"

const val EQ_CUSTOM = "custom"

enum class EqFilterType { Off, Peak, LowShelf, HighShelf }

enum class EqPart { Type, Freq, Gain, Q }

enum class EqAnswer { Done, Refused, NoSpace, Failed, Fitting }

data class EqFilter(val type: EqFilterType, val milliHz: Int, val gain10: Int, val q100: Int)

data class EqCurve(val preamp10: Int, val filters: List<EqFilter>)

data class EqLevels(
    val level: Int = 0,
    val rateHz: Int = 0,
    val dsd: Int = 0,
    val dop: Boolean = false,
    val dopTrim: Int = 0,
    val peak10: Int? = null,
)

data class EqView(
    val on: Boolean = true,
    val key: String = EQ_OFF,
    val presets: List<String> = emptyList(),
    val shown: EqCurve? = null,
    val missing: Boolean = false,
    val levels: EqLevels = EqLevels(),
)
