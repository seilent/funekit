package net.seilent.funekit.kit.saver

internal const val ONE = 65536L
internal const val HALF = 32768L
internal const val BITS = 16
internal const val R = 46341L
internal const val ROOT2 = 92682L
internal const val NEVER = Long.MAX_VALUE / 4
internal const val NO_PEN = -(1 shl 30)
internal const val PHASES = 4
internal const val CORNERS = 4
internal const val GRID_MS = 16L
internal const val CROSS_FRAMES = 15
internal const val GLYPH_STEPS = 640
internal const val LEAD_MS = 1500L
internal const val DIAG = 45
internal const val UP = 90
internal const val MS_PER_S = 1000L
internal const val SUB = 64
internal const val SUB_BITS = 6
internal const val SUB_HALF = 32
internal const val PX64_BITS = 10
internal const val PX64_HALF = 512
internal const val FX_PER_64 = 1024L
internal const val COPIES = 255
internal const val FAR = 1e18
internal const val A256 = 256L

internal fun ms(t: Long): Int = (t shr BITS).toInt()

internal fun secsMs(s: Long): Long = (s * MS_PER_S + HALF) shr BITS

internal fun rnd(v: Long): Int = ((v + HALF) shr BITS).toInt()

internal fun fdiv(a: Int, b: Int): Int = if (a >= 0) a / b else -((-a + b - 1) / b)

internal fun wrap(a: Int, b: Int): Int = (a % b + b) % b

internal fun turned(angle: Int): Boolean = angle == DIAG || angle == -DIAG

internal fun dirX(angle: Int): Long = if (angle == UP) 0L else if (angle != 0) R else ONE

internal fun dirY(angle: Int): Long = if (angle == UP) -ONE else if (angle > 0) R else if (angle < 0) -R else 0L

internal fun stepLen(angle: Int): Long = if (turned(angle)) ROOT2 else ONE

internal fun along64(angle: Int, u: Int, v: Int): Int {
    if (!turned(angle)) return u * SUB
    val sy = if (angle > 0) 1 else -1
    val ex = rnd((u - sy * v).toLong() * R)
    val ey = rnd((sy * u + v).toLong() * R)
    return (((ex + sy * ey).toLong() * R * SUB + HALF) shr BITS).toInt()
}

internal fun alpha256(a: Long): Int = ((a.coerceIn(0L, ONE) * A256 + HALF) shr BITS).toInt()

internal data class SaverPt(val x: Int, val y: Int)

data class SaverRect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val empty: Boolean get() = x1 <= x0 || y1 <= y0

    fun intersect(o: SaverRect): SaverRect =
        SaverRect(maxOf(x0, o.x0), maxOf(y0, o.y0), minOf(x1, o.x1), minOf(y1, o.y1)).let { if (it.empty) NONE else it }

    fun unite(o: SaverRect): SaverRect = when {
        empty -> o
        o.empty -> this
        else -> SaverRect(minOf(x0, o.x0), minOf(y0, o.y0), maxOf(x1, o.x1), maxOf(y1, o.y1))
    }

    companion object {
        val NONE = SaverRect(0, 0, 0, 0)
    }
}
