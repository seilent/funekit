package net.seilent.funekit.kit.proj

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val HALF_TURN = 180.0
private const val N = 3

class TiltPose(val rx: Double, val ry: Double, val depth: Double = 0.0)

fun platePose(dx: Double, dy: Double, reach: Double, maxDeg: Double): TiltPose {
    val r = maxDeg * PI / HALF_TURN
    return TiltPose(r * (dy / reach).coerceIn(-1.0, 1.0), -r * (dx / reach).coerceIn(-1.0, 1.0))
}

private fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(N * N) { k ->
    (0 until N).sumOf { j -> a[k / N * N + j] * b[j * N + k % N] }
}

fun tiltMatrix(p: TiltPose, q: Double, cx: Double, cy: Double, d: Double): DoubleArray {
    val ax = p.rx * q
    val ay = p.ry * q
    val dz = p.depth * q
    val ca = cos(ax)
    val sa = sin(ax)
    val cb = cos(ay)
    val sb = sin(ay)
    val to = doubleArrayOf(1.0, 0.0, -cx, 0.0, 1.0, -cy, 0.0, 0.0, 1.0)
    val back = doubleArrayOf(1.0, 0.0, cx, 0.0, 1.0, cy, 0.0, 0.0, 1.0)
    val turn = doubleArrayOf(d * cb, d * sa * sb, 0.0, 0.0, d * ca, 0.0, -sb, sa * cb, d + dz)
    return mul(back, mul(turn, to))
}

fun tiltPoint(m: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
    val v = DoubleArray(N) { i -> m[i * N] * x + m[i * N + 1] * y + m[i * N + N - 1] }
    return v[0] / v[N - 1] to v[1] / v[N - 1]
}
