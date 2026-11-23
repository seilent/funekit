package net.seilent.funekit.kit.proj

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val HALF_TURN = 180f
private const val EPS = 1e-30f

class M3(
    val a: Float, val b: Float, val c: Float,
    val d: Float, val e: Float, val f: Float,
    val g: Float, val h: Float, val i: Float,
) {
    operator fun times(o: M3) = M3(
        a * o.a + b * o.d + c * o.g, a * o.b + b * o.e + c * o.h, a * o.c + b * o.f + c * o.i,
        d * o.a + e * o.d + f * o.g, d * o.b + e * o.e + f * o.h, d * o.c + e * o.f + f * o.i,
        g * o.a + h * o.d + i * o.g, g * o.b + h * o.e + i * o.h, g * o.c + h * o.f + i * o.i,
    )

    fun map(x: Float, y: Float): Offset {
        val w = g * x + h * y + i
        return Offset((a * x + b * y + c) / w, (d * x + e * y + f) / w)
    }

    fun into(m: Matrix): Matrix = m.also {
        it.reset()
        it.values[Matrix.ScaleX] = a
        it.values[Matrix.SkewX] = b
        it.values[Matrix.TranslateX] = c
        it.values[Matrix.SkewY] = d
        it.values[Matrix.ScaleY] = e
        it.values[Matrix.TranslateY] = f
        it.values[Matrix.Perspective0] = g
        it.values[Matrix.Perspective1] = h
        it.values[Matrix.Perspective2] = i
    }

    fun compose(): Matrix = into(Matrix())

    companion object {
        fun translate(x: Float, y: Float) = M3(1f, 0f, x, 0f, 1f, y, 0f, 0f, 1f)

        fun scale(s: Float) = M3(s, 0f, 0f, 0f, s, 0f, 0f, 0f, 1f)
    }
}

fun rad(deg: Float): Float = (deg * PI / HALF_TURN).toFloat()

fun turn(deg: Float, hinge: Float, eye: Float, d: Float): M3 {
    val s = sin(rad(deg)) / d
    val k = hinge * s + cos(rad(deg))
    return M3(
        k, 0f, hinge * (1 - k),
        eye * s, 1f, -eye * hinge * s,
        s, 0f, 1 - hinge * s,
    )
}

fun M3.inverse(): M3 {
    val ca = e * i - f * h
    val cb = c * h - b * i
    val cc = b * f - c * e
    val cd = f * g - d * i
    val ce = a * i - c * g
    val cf = c * d - a * f
    val cg = d * h - e * g
    val ch = b * g - a * h
    val ci = a * e - b * d
    val det = a * ca + b * cd + c * cg
    if (abs(det) < EPS) return M3(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    return M3(ca / det, cb / det, cc / det, cd / det, ce / det, cf / det, cg / det, ch / det, ci / det)
}

fun camTurn(deg: Float, hinge: Float, eye: Float, d: Float, cx: Float): M3 {
    val c = cos(rad(deg))
    val s = sin(rad(deg))
    val es = eye * s / d
    return M3(
        1 - hinge * s / d, 0f, hinge * c - hinge + hinge * cx * s / d,
        -es, c + (cx - hinge) * s / d, es * hinge,
        -s / d, 0f, c + cx * s / d,
    ).inverse()
}

fun facing(deg: Float, hinge: Float, d: Float, cx: Float): Boolean = cos(rad(deg)) + (cx - hinge) * sin(rad(deg)) / d > 0f

fun Modifier.project(m: () -> M3): Modifier {
    val matrix = Matrix()
    return drawWithContent {
        val t = m().into(matrix)
        withTransform({ transform(t) }) { this@drawWithContent.drawContent() }
    }
}

fun t6(p: Float, stdPpu: Float, widthPx: Int): M3 {
    val s = 1 + p * (Tok.motion_quickplayScale - 1)
    val hinge = floor(widthPx * s)
    val d = px(Tok.motion_perspective, stdPpu).toFloat()
    val x = p * Tok.motion_quickplayMenuX * stdPpu
    val y = p * Tok.motion_quickplayMenuY * stdPpu
    return M3.translate(x, y) * M3.scale(s) * turn(p * Tok.motion_quickplayAngle, hinge, 0f, d)
}

fun qpProjection(p: Float, stdPpu: Float): M3 {
    val s = Tok.qpPeekScale + (1 - Tok.qpPeekScale) * p
    return M3.translate((1 - p) * Tok.qpPeekX * stdPpu, (1 - p) * Tok.qpPeekY * stdPpu) * M3.scale(s)
}
