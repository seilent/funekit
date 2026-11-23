package net.seilent.funekit.kit.motion

import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val SEG = 512
private const val FX = 65536.0
private const val HALF = 2.0
private const val THREE = 3.0
private const val BISECT = 48
private const val EXP6 = 6.0
private const val EXP15 = 15.0
private const val POP = 10.0
private const val POW17 = 1.7
private const val POW15 = 1.5
private const val BACK = 111515.0
private val ONE = FX.toLong()

fun interface Curve {
    fun at(t: Float): Float
}

private fun bez(a: Double, b: Double, s: Double): Double {
    val m = 1 - s
    return THREE * m * m * s * a + THREE * m * s * s * b + s * s * s
}

private fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, t: Double): Double {
    var lo = 0.0
    var hi = 1.0
    repeat(BISECT) {
        val mid = (lo + hi) / HALF
        if (bez(x1, x2, mid) < t) lo = mid else hi = mid
    }
    return bez(y1, y2, (lo + hi) / HALF)
}

class TableCurve internal constructor(ends: Boolean, pop: Boolean, f: (Double) -> Double) : Curve {
    private val v = LongArray(SEG + 1) { i ->
        val t = i.toDouble() / SEG
        val y = if (ends && (i == 0 || i == SEG)) t else f(t)
        (y * FX).roundToLong()
    }.also { if (pop) it[SEG] = FX.toLong() }

    override fun at(t: Float): Float {
        val p = t.coerceIn(0f, 1f).toDouble() * SEG
        val i = p.toInt()
        if (i >= SEG) return (v[SEG] / FX).toFloat()
        val f = p - i
        return ((v[i] + (v[i + 1] - v[i]) * f) / FX).toFloat()
    }

    fun fx(t: Long): Long {
        val p = t.coerceIn(0L, ONE) * SEG
        val i = (p / ONE).toInt()
        if (i >= SEG) return v[SEG]
        return v[i] + fxMul(v[i + 1] - v[i], p and (ONE - 1))
    }
}

private class Closed(private val f: (Double) -> Double) : Curve {
    override fun at(t: Float): Float = f(t.coerceIn(0f, 1f).toDouble()).toFloat()
}

private fun table(f: (Double) -> Double) = TableCurve(ends = false, pop = false, f = f)

object Curves {
    private val back = BACK / FX
    private val e1 = exp(1.0) - 1

    val linear: Curve = Closed { it }
    val expOut6: TableCurve = table { 1 - (exp(EXP6 * (1 - it)) - 1) / (exp(EXP6) - 1) }
    val expIn6: Curve = table { (exp(EXP6 * it) - 1) / (exp(EXP6) - 1) }
    val expIn1: Curve = table { (exp(it) - 1) / e1 }
    val expOut1: Curve = table { 1 - (exp(1 - it) - 1) / e1 }
    val expIn15: Curve = table { (exp(EXP15 * it) - 1) / (exp(EXP15) - 1) }
    val decel: TableCurve = TableCurve(ends = true, pop = false) {
        bezier(Tok.motion_decel_x1.toDouble(), Tok.motion_decel_y1.toDouble(), Tok.motion_decel_x2.toDouble(), Tok.motion_decel_y2.toDouble(), it)
    }
    val strip: Curve = TableCurve(ends = true, pop = false) {
        bezier(Tok.motion_strip_x1.toDouble(), Tok.motion_strip_y1.toDouble(), Tok.motion_strip_x2.toDouble(), Tok.motion_strip_y2.toDouble(), it)
    }
    val sine: Curve = table { sin(it * PI / HALF) }
    val pop: Curve = TableCurve(ends = false, pop = true) { 1 - exp(-POP * it * ln(HALF)) }
    val bezierPop: Curve = Closed {
        val m = it - 1
        m * m * ((back + 1) * m + back) + 1
    }
    val easeIn: Curve = Closed { it * it }
    val easeOut: Curve = Closed { it * (HALF - it) }
    val powOut17: Curve = table { if (it >= 1) 1.0 else 1 - exp(POW17 * ln(1 - it)) }
    val cubicOut: Curve = Closed {
        val m = 1 - it
        1 - m * m * m
    }
    val smoothStep: Curve = Closed { it * it * (THREE - HALF * it) }
    val powOut15: Curve = table { if (it >= 1) 1.0 else 1 - exp(POW15 * ln(1 - it)) }
    val quartOut: Curve = Closed {
        val m = 1 - it
        1 - m * m * m * m
    }
    val powIn15: Curve = Closed { it * sqrt(it) }
}
