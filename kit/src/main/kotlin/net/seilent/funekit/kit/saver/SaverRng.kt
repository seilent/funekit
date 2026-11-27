package net.seilent.funekit.kit.saver

import kotlin.math.sqrt

private const val N = 624
private const val M = 397
private const val MATRIX = 0x9908b0df.toInt()
private const val UPPER = 0x80000000.toInt()
private const val LOWER = 0x7fffffff
private const val INIT = 1812433253
private const val INIT_SHIFT = 30
private const val U = 11
private const val S = 7
private const val B = 0x9d2c5680.toInt()
private const val T = 15
private const val C = 0xefc60000.toInt()
private const val L = 18
private const val U32 = 0xFFFFFFFFL
private const val WORD = 32
private const val UNIT = 1 shl 30
private const val DEFAULT_SEED = 5489

internal class Mt19937 private constructor(private val mt: IntArray, private var i: Int) {
    constructor() : this(IntArray(N), N) {
        seed(DEFAULT_SEED)
    }

    fun seed(s: Int) {
        mt[0] = s
        for (k in 1 until N) mt[k] = INIT * (mt[k - 1] xor (mt[k - 1] ushr INIT_SHIFT)) + k
        i = N
    }

    fun copy(): Mt19937 = Mt19937(mt.copyOf(), i)

    fun next(): Long {
        if (i >= N) twist()
        var y = mt[i++]
        y = y xor (y ushr U)
        y = y xor (y shl S and B)
        y = y xor (y shl T and C)
        y = y xor (y ushr L)
        return y.toLong() and U32
    }

    private fun mix(a: Int, b: Int, c: Int): Int {
        val y = a and UPPER or (b and LOWER)
        return c xor (y ushr 1) xor (if ((y and 1) != 0) MATRIX else 0)
    }

    private fun twist() {
        for (k in 0 until N - M) mt[k] = mix(mt[k], mt[k + 1], mt[k + M])
        for (k in N - M until N - 1) mt[k] = mix(mt[k], mt[k + 1], mt[k + M - N])
        mt[N - 1] = mix(mt[N - 1], mt[0], mt[M - 1])
        i = 0
    }

    fun uniform(a: Long, b: Long): Long {
        val range = (b - a + 1) and U32
        var product = next().toULong() * range.toULong()
        var low = (product and U32.toULong()).toLong()
        if (low < range) {
            val threshold = ((U32 + 1) - range) % range
            while (low < threshold) {
                product = next().toULong() * range.toULong()
                low = (product and U32.toULong()).toLong()
            }
        }
        return (product shr WORD).toLong() + a
    }

    fun triangular(lo: Long, mode: Long, hi: Long): Long {
        val a = ms(lo).toDouble()
        val c = ms(mode).toDouble()
        val b = ms(hi).toDouble()
        if (b <= a) return ms(lo).toLong()
        val u = uniform(0, UNIT - 1L).toDouble() / UNIT.toDouble()
        val f = (c - a) / (b - a)
        val x = if (u < f) a + sqrt(u * (b - a) * (c - a)) else b - sqrt((1 - u) * (b - a) * (b - c))
        return lround(x)
    }
}

internal fun lround(x: Double): Long = if (x >= 0) Math.round(x) else -Math.round(-x)
