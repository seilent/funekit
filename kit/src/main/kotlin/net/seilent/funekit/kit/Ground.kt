package net.seilent.funekit.kit

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.roundToInt

private const val LEVELS = 256
private const val RB = 0xFF00FFL
private const val GREEN = 0xFF00L
private const val SHIFT = 8
private const val U32 = 0xFFFFFFFFL
private const val OPAQUE = 0xFF000000L

class Band(val y0: Int, val y1: Int, val level: Int)

fun groundBands(heightPx: Int, accentOff: Boolean): List<Band> {
    val top = if (accentOff) 0 else (Tok.pageSurface_alphaTop * LEVELS).roundToInt()
    val span = max(1, heightPx)
    val out = mutableListOf<Band>()
    var y0 = 0
    var level = top
    for (y in 0..heightPx) {
        val a = if (y == heightPx) -1 else top + (0 - top) * y.coerceIn(0, span) / span
        if (a != level) {
            if (y > y0) out += Band(y0, y, level)
            y0 = y
            level = a
        }
    }
    return out
}

fun blendPx(d: Long, s: Long, a: Int): Long {
    val rb = d and RB
    val g = d and GREEN
    val rbOut = (rb + ((((s and RB) - rb) and U32) * a and U32 ushr SHIFT)) and RB
    val gOut = (g + ((((s and GREEN) - g) and U32) * a and U32 ushr SHIFT)) and GREEN
    return rbOut or gOut
}

private fun bandColor(b: Band, over: Long): Color = Color(blendPx(Tok.color_bg, over, b.level) or OPAQUE)

fun bandGround(b: Band, accent: Color): Color =
    if (b.level > 0) bandColor(b, accent.toArgb().toLong() and U32) else Ink.bg

private fun DrawScope.bandRects(bands: List<Band>, accent: Color) {
    val over = accent.toArgb().toLong() and U32
    for (b in bands) {
        if (b.level > 0) drawRect(bandColor(b, over), Offset(0f, b.y0.toFloat()), Size(size.width, (b.y1 - b.y0).toFloat()))
    }
}

fun DrawScope.pageGround(bands: List<Band>, accent: Color) {
    if (bands.none { it.level > 0 }) return
    drawRect(Ink.bg, Offset.Zero, size)
    bandRects(bands, accent)
}

fun DrawScope.pageGround(bands: List<Band>, accent: Color, leave: Float, arrive: Float) {
    if (bands.none { it.level > 0 }) return
    drawRect(Ink.bg, Offset.Zero, size)
    val over = accent.toArgb().toLong() and U32
    val la = (leave * LEVELS).roundToInt()
    val aa = (arrive * LEVELS).roundToInt()
    for (b in bands) {
        if (b.level <= 0) continue
        val g = blendPx(Tok.color_bg, over, b.level)
        drawRect(Color(blendPx(blendPx(Tok.color_bg, g, la), g, aa) or OPAQUE), Offset(0f, b.y0.toFloat()), Size(size.width, (b.y1 - b.y0).toFloat()))
    }
}

fun DrawScope.hostGround() = drawRect(Ink.bg, Offset.Zero, size)

fun Modifier.hostGround(): Modifier = drawBehind { this.hostGround() }

class NavGround(val bands: List<Band>, val accent: Color, val ground: Boolean = true)

fun navBand(s: Scale): Edges = Edges(0, px(s.stdContentBottom, s.stdPpu), s.widthPx, s.heightPx)

fun DrawScope.navBand(s: Scale, g: NavGround) {
    val b = navBand(s)
    clipRect(b.x0.toFloat(), b.y0.toFloat(), b.x1.toFloat(), b.y1.toFloat()) {
        drawRect(Ink.bg, Offset.Zero, size)
        if (g.ground) bandRects(g.bands, g.accent)
    }
}
