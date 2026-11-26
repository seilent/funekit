package net.seilent.funekit.screens.eq

import net.seilent.funekit.api.engine.EqLevels
import net.seilent.funekit.gen.Tok
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val TEN = 10L
private const val Q_DIGITS = 2
private const val MILLI = 1000L
private const val HALF_MILLI = 500L
private const val KHZ = 1000L
private const val HZ_ROUND = 100L
private const val TENTH_KHZ = 100000L
private const val HALF_TENTH_KHZ = 50000L
private const val GAIN_SCALE = 10f
private const val Q_SCALE = 100f
private const val KHZ_POINT = 34
private const val SIXTHS = 6
private const val FX = 65536.0
private const val OCTAVE = 2.0
private const val FX_BITS = 16
private const val Q_STEP = 10
private const val FRESH_Q = 140
private const val PARSE_CAP = 40
private const val DSD_BASE = 32
private const val RATE_KHZ = 1000
private const val RATE_TENTH = 100

private val sixth = LongArray(SIXTHS) { (FX * OCTAVE.pow(it.toDouble() / SIXTHS)).roundToLong() }

internal val gridPoints: Int = Tok.eq_freqPoints.toInt()

internal val preampMin: Int = (Tok.eq_preampMin * GAIN_SCALE).roundToInt()

internal val preampMax: Int = (Tok.eq_preampMax * GAIN_SCALE).roundToInt()

internal val gainMin: Int = (Tok.eq_gainMin * GAIN_SCALE).roundToInt()

internal val gainMax: Int = (Tok.eq_gainMax * GAIN_SCALE).roundToInt()

internal val qMin: Int = (Tok.eq_qMin * Q_SCALE).roundToInt()

internal val qMax: Int = (Tok.eq_qMax * Q_SCALE).roundToInt()

fun eqGridMilliHz(index: Int): Int {
    if (index <= 0) return (Tok.eq_freqMin * MILLI).roundToInt()
    if (index >= gridPoints - 1) return (Tok.eq_freqMax * MILLI).roundToInt()
    val k = index - KHZ_POINT
    val oct = if (k >= 0) k / SIXTHS else -((-k + SIXTHS - 1) / SIXTHS)
    var v = MILLI * MILLI * sixth[k - oct * SIXTHS]
    v = if (oct >= 0) v shl oct else (v + (1L shl (-oct - 1))) shr -oct
    return ((v + (1L shl (FX_BITS - 1))) shr FX_BITS).toInt()
}

fun eqGridOf(milliHz: Int): Int {
    for (g in 0 until gridPoints) if (eqGridMilliHz(g) == milliHz) return g
    return -1
}

internal val freshBand = EqBand(EqBandType.Peak, eqGridMilliHz(KHZ_POINT), KHZ_POINT, 0, FRESH_Q)

internal fun gridNear(milliHz: Int, delta: Int): Int {
    if (delta > 0) {
        for (g in 0 until gridPoints) if (eqGridMilliHz(g) > milliHz) return g
        return gridPoints - 1
    }
    for (g in gridPoints - 1 downTo 0) if (eqGridMilliHz(g) < milliHz) return g
    return 0
}

internal fun snap(v: Int, step: Int, delta: Int): Int {
    val base = if (v >= 0) v / step else -((-v + step - 1) / step)
    if (delta > 0) return (base + 1) * step
    val up = if (v >= 0) (v + step - 1) / step else -(-v / step)
    return (up - 1) * step
}

internal fun fixedText(v: Long, digits: Int): String {
    val neg = v < 0
    val a = if (neg) -v else v
    var div = 1L
    repeat(digits) { div *= TEN }
    val f = (a % div).toString().padStart(digits, '0')
    return (if (neg) "-" else "") + (a / div).toString() + (if (digits > 0) ".$f" else "")
}

internal fun parseFixed(s: String, digits: Int): Long? {
    var i = 0
    var neg = false
    if (i < s.length && (s[i] == '-' || s[i] == '+')) neg = s[i++] == '-'
    var v = 0L
    var frac = -1
    var any = false
    while (i < s.length) {
        val c = s[i++]
        if (c == '.' && frac < 0) {
            frac = 0
            continue
        }
        if (c !in '0'..'9') return null
        any = true
        if (frac >= digits) {
            if (frac == digits && c >= '5') v++
            frac++
            continue
        }
        v = v * TEN + (c - '0')
        if (frac >= 0) frac++
    }
    if (!any || v > 1L shl PARSE_CAP) return null
    for (k in (if (frac < 0) 0 else minOf(frac, digits)) until digits) v *= TEN
    return if (neg) -v else v
}

internal fun digitsOf(f: EqField): Int = when (f) {
    EqField.Q -> Q_DIGITS
    EqField.Gain -> 1
    else -> 0
}

internal fun eqFreqText(milliHz: Int, grid: Int): String {
    val hz = (milliHz + HALF_MILLI) / MILLI
    if (hz < KHZ || (grid < 0 && hz % HZ_ROUND != 0L)) return "$hz hz"
    val d = (milliHz + HALF_TENTH_KHZ) / TENTH_KHZ
    return "${d / TEN}" + (if (d % TEN != 0L) ".${d % TEN}" else "") + " khz"
}

internal fun eqCapText(l: EqLevels): String {
    if (l.dsd != 0) return "dsd${DSD_BASE shl l.dsd}"
    val r = l.rateHz
    return "${r / RATE_KHZ}" + (if (r % RATE_KHZ != 0) ".${r % RATE_KHZ / RATE_TENTH}" else "") + " khz"
}

internal fun eqGainText(gain10: Int, plus: Boolean): String = (if (plus && gain10 > 0) "+" else "") + fixedText(gain10.toLong(), 1) + " db"

internal fun eqQText(q100: Int): String = fixedText(q100.toLong(), Q_DIGITS)

internal fun eqTypeText(t: EqBandType, short: Boolean): String = when (t) {
    EqBandType.Off -> "off"
    EqBandType.Peak -> if (short) "pk" else "peak"
    EqBandType.LowShelf -> if (short) "ls" else "low shelf"
    EqBandType.HighShelf -> if (short) "hs" else "high shelf"
}

internal fun eqBandText(b: EqBand): String {
    if (b.type == EqBandType.Off) return "off"
    return eqTypeText(b.type, true) + " " + eqFreqText(b.milliHz, b.grid) + " " + eqGainText(b.gain10, true) + " q " + eqQText(b.q100)
}

internal fun eqEntryText(f: EqField, value: Int): String =
    if (f == EqField.Freq) ((value + HALF_MILLI) / MILLI).toString() else fixedText(value.toLong(), digitsOf(f))

private fun lo(f: EqField, preamp: Boolean): Int = when (f) {
    EqField.Freq -> eqGridMilliHz(0)
    EqField.Q -> qMin
    else -> if (preamp) preampMin else gainMin
}

private fun hi(f: EqField, preamp: Boolean): Int = when (f) {
    EqField.Freq -> eqGridMilliHz(gridPoints - 1)
    EqField.Q -> qMax
    else -> if (preamp) preampMax else gainMax
}

internal fun eqParse(f: EqField, preamp: Boolean, text: String): Int? {
    val digits = digitsOf(f)
    val dot = text.indexOf('.')
    if (f == EqField.Type || text.isEmpty() || text[0] == '+' || (text[0] == '-' && f != EqField.Gain)) return null
    if (dot >= 0 && text.length - dot - 1 > digits) return null
    val v = parseFixed(text, digits) ?: return null
    val milli = f == EqField.Freq
    val low = if (milli) lo(f, preamp) / MILLI else lo(f, preamp).toLong()
    val high = if (milli) hi(f, preamp) / MILLI else hi(f, preamp).toLong()
    if (v < low || v > high) return null
    return (if (milli) v * MILLI else v).toInt()
}

internal fun eqRangeText(f: EqField, preamp: Boolean): String {
    fun num(v: Long, digits: Int): String {
        var s = fixedText(v, digits)
        if (digits > 0) {
            s = s.trimEnd('0')
            s = s.removeSuffix(".")
        }
        return s
    }
    if (f == EqField.Freq) return num(lo(f, false) / MILLI, 0) + " to " + num(hi(f, false) / MILLI, 0) + " hz"
    if (f == EqField.Q) return num(qMin.toLong(), Q_DIGITS) + " to " + num(qMax.toLong(), Q_DIGITS)
    val h = hi(f, preamp)
    return num(lo(f, preamp).toLong(), 1) + " to " + (if (h > 0) "+" else "") + num(h.toLong(), 1) + " db"
}

internal fun eqAtEnd(f: EqField, preamp: Boolean, value: Int, dir: Int): Boolean =
    if (dir > 0) value >= hi(f, preamp) else value <= lo(f, preamp)

internal fun eqStepGain(v: Int, delta: Int, preamp: Boolean): Int = snap(v, 1, delta).coerceIn(lo(EqField.Gain, preamp), hi(EqField.Gain, preamp))

internal fun eqStepQ(v: Int, delta: Int): Int = snap(v, Q_STEP, delta).coerceIn(qMin, qMax)

internal fun eqStepFreq(milliHz: Int, grid: Int, delta: Int): Int {
    var n = 1
    var g = grid
    if (g < 0) {
        g = gridNear(milliHz, delta)
        n--
    }
    return eqGridMilliHz((g + if (delta > 0) n else -n).coerceIn(0, gridPoints - 1))
}
