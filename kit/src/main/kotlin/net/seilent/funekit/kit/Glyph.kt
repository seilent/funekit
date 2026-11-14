package net.seilent.funekit.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val HALF = 2.0
private const val ARC_STEP = 24.0
private const val MIN_STEPS = 2
private const val MITER = 4f

class Outline(val subs: List<List<Offset>>, val closed: List<Boolean>)

private fun arc(p: MutableList<Offset>, x1: Double, y1: Double, r0: Double, large: Boolean, sweep: Boolean, x2: Double, y2: Double) {
    var r = r0
    val dx = (x1 - x2) / HALF
    val dy = (y1 - y2) / HALF
    val l = (dx * dx + dy * dy) / (r * r)
    if (l > 1) r *= sqrt(l)
    val num = r * r * r * r - r * r * dy * dy - r * r * dx * dx
    val den = r * r * dy * dy + r * r * dx * dx
    var co = if (den > 0 && num > 0) sqrt(num / den) else 0.0
    if (large == sweep) co = -co
    val cxp = co * dy
    val cyp = -co * dx
    val cx = cxp + (x1 + x2) / HALF
    val cy = cyp + (y1 + y2) / HALF
    val t1 = atan2((dy - cyp) / r, (dx - cxp) / r)
    var dt = atan2((-dy - cyp) / r, (-dx - cxp) / r) - t1
    if (!sweep && dt > 0) dt -= HALF * PI
    if (sweep && dt < 0) dt += HALF * PI
    val n = max(MIN_STEPS, ceil(abs(dt) / (PI / ARC_STEP)).toInt())
    for (i in 1..n) {
        val t = t1 + dt * i / n
        p.add(Offset((cx + r * cos(t)).toFloat(), (cy + r * sin(t)).toFloat()))
    }
}

fun outline(d: String): Outline {
    val subs = mutableListOf<MutableList<Offset>>()
    val closed = mutableListOf<Boolean>()
    val tokens = d.trim().split(Regex("\\s+"))
    var i = 0
    var x = 0.0
    var y = 0.0
    fun num() = tokens[i++].toDouble()
    fun at() = Offset(x.toFloat(), y.toFloat())
    while (i < tokens.size) {
        val c = tokens[i++]
        if (c != "M" && subs.isEmpty()) {
            subs.add(mutableListOf(at()))
            closed.add(false)
        }
        when (c) {
            "M" -> {
                x = num()
                y = num()
                subs.add(mutableListOf(at()))
                closed.add(false)
            }
            "L" -> {
                x = num()
                y = num()
                subs.last().add(at())
            }
            "H" -> {
                x = num()
                subs.last().add(at())
            }
            "V" -> {
                y = num()
                subs.last().add(at())
            }
            "A" -> {
                val r = num()
                num()
                num()
                val large = num() != 0.0
                val sweep = num() != 0.0
                val nx = num()
                val ny = num()
                arc(subs.last(), x, y, r, large, sweep, nx, ny)
                x = nx
                y = ny
            }
            "Z" -> closed[closed.lastIndex] = true
        }
    }
    return Outline(subs, closed)
}

private val outlines = HashMap<String, Path>()

private fun path(g: Glyph): Path = outlines.getOrPut(g.name) {
    val o = outline(g.path)
    Path().also { p ->
        o.subs.forEachIndexed { k, s ->
            p.moveTo(s.first().x, s.first().y)
            for (q in s.drop(1)) p.lineTo(q.x, q.y)
            if (o.closed[k]) p.close()
        }
    }
}

private fun boxCenter(c: Float, px: Int): Float = ((floor(c).toInt() - (px shr 1)) + px / HALF).toFloat()

fun boxCenter(c: Offset, px: Float): Offset = Offset(boxCenter(c.x, px.toInt()), boxCenter(c.y, px.toInt()))

fun DrawScope.glyph(g: Glyph, center: Offset, slotPx: Float, color: Color, alpha: Float, scale: Float = 1f) {
    val k = slotPx * scale / Glyphs.box
    val half = Glyphs.box / HALF.toFloat()
    val at = boxCenter(center, slotPx)
    translate(at.x, at.y) {
        withTransform({ scale(k, k, Offset.Zero) }) {
            translate(-half, -half) {
                val style = if (g.fill) Fill else Stroke(Glyphs.stroke, MITER, StrokeCap.Butt, StrokeJoin.Miter)
                drawPath(path(g), color, alpha, style)
            }
        }
    }
}

fun DrawScope.ring(center: Offset, diameterPx: Float, strokePx: Float, color: Color, alpha: Float, scale: Float = 1f) {
    val d = diameterPx * scale
    val w = strokePx * scale
    drawCircle(color, (d - w) / HALF.toFloat(), boxCenter(center, diameterPx), alpha, Stroke(w))
}

fun DrawScope.arc(center: Offset, diameterPx: Float, strokePx: Float, startDeg: Float, sweepDeg: Float, color: Color, alpha: Float) {
    val r = (diameterPx - strokePx) / HALF.toFloat()
    val c = boxCenter(center, diameterPx)
    drawArc(color, startDeg, sweepDeg, false, Offset(c.x - r, c.y - r), Size(r * HALF.toFloat(), r * HALF.toFloat()), alpha, Stroke(strokePx))
}

private val wifiParts = listOf(Glyphs.wifiDot, Glyphs.wifiArc1, Glyphs.wifiArc2)

fun DrawScope.wifi(center: Offset, slotPx: Float, level: Int, color: Color, alpha: Float) {
    wifiParts.forEachIndexed { i, g -> glyph(g, center, slotPx, color, if (level < 0 || i < level) alpha else alpha * Tok.alpha_low) }
}
