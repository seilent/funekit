package net.seilent.funekit.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.seilent.funekit.Role
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import net.seilent.funekit.px64
import java.text.Bidi

private const val HALF = 2
private const val PEN = 64

class BoxInk(val lines: List<Word>, val rtl: Boolean, val x64: Int, val pitch: Long, val ppu: Long, val track64: Int) {
    fun x(w: Word): Float = if (rtl) penPx(x64 - Tiles.placed64(w.width, track64)) else penPx(x64)
}

object Tiles {
    fun placed64(width: Float, track64: Int): Int = pen64(width) - track64

    fun inner(t: Edges, ppu: Float): Edges {
        val g = px(Tok.boxInset + Tok.boxLine / HALF, ppu)
        return Edges(t.x0 + g, t.y0 + g, t.x1 - g, t.y1 - g)
    }

    private val captionBase = fx(Tok.boxInset) + fx(Tok.boxCaptionBase)
    private val captionPitch = fx(Tok.boxCaptionPitch)
    private val libSize = fx(Tok.type_boxCaptionLib_size)

    fun pitch(role: Role): Long = captionPitch * fx(role.size) / libSize

    fun base(t: Edges, i: Int, rows: Int, role: Role, ppu: Float): Int = base(t, i, rows, pitch(role), fx(ppu))

    fun base(t: Edges, i: Int, rows: Int, pitch: Long, ppu: Long): Int = t.y1 - px64(captionBase + (rows - 1 - i) * pitch, ppu)

    fun line(t: Edges, ppu: Float): List<Edges> = Rows.insetRing(t, Tok.boxInset, Tok.boxLine, ppu)
}

fun boxInk(m: TextMeasurer, fonts: FontFamily.Resolver, name: String, role: Role, style: TextStyle, t: Edges, lines: Int, ppu: Float, density: Density, covers: Covers?): BoxInk {
    val text = cased(name, role)
    val rtl = text.isNotEmpty() && !Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()
    val inset64 = pen64(Tok.boxInset + Tok.boxCaptionX, ppu)
    val inner = Tiles.inner(t, ppu)
    val x64 = if (rtl) t.x1 * PEN - inset64 else t.x0 * PEN + inset64
    val track64 = pen64(role.tracking, ppu)
    if (text.isEmpty() || lines <= 0) return BoxInk(emptyList(), rtl, x64, 0L, 0L, track64)
    val avail = if (rtl) x64 - inner.x0 * PEN else inner.x1 * PEN - x64
    val base = if (rtl) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT
    val w = textWidth(role, style, fonts, density, covers, base)
    val rows = wrapRanges(text, avail, lines, { b, e -> if (e > b) Tiles.placed64(w(text.substring(b, e)), track64) else 0 }, { p, e -> nextCluster(text, p, e) })
    val words = rows.mapIndexed { i, r -> m.word(text.substring(r.first, if (i == rows.lastIndex) text.length else r.second), role, style, fonts, density, covers, base) }
    return BoxInk(words, rtl, x64, Tiles.pitch(role), fx(ppu), track64)
}

private fun DrawScope.fill(e: Edges, alpha: Float, color: Color = Ink.white) =
    drawRect(color, Offset(e.x0.toFloat(), e.y0.toFloat()), Size(e.w.toFloat(), e.h.toFloat()), alpha)

fun DrawScope.boxTile(t: Edges, ink: BoxInk, role: Role, ppu: Float) {
    Tiles.line(t, ppu).forEach { fill(it, Tok.boxLineAlpha) }
    if (ink.lines.isEmpty()) return
    val inner = Tiles.inner(t, ppu)
    clipRect(inner.x0.toFloat(), inner.y0.toFloat(), inner.x1.toFloat(), inner.y1.toFloat()) {
        ink.lines.forEachIndexed { i, w ->
            word(w, ink.x(w), Tiles.base(t, i, ink.lines.size, ink.pitch, ink.ppu).toFloat(), Ink.white, role.alpha)
        }
    }
}

fun DrawScope.artTile(t: Edges, art: ImageBitmap?, box: BoxInk?, role: Role, border: Int, frame: Boolean, s: Scale) =
    artTile(t, art, box, role, border, 0f, if (frame) 1f else 0f, s)

fun DrawScope.artTile(t: Edges, art: ImageBitmap?, box: BoxInk?, role: Role, border: Int, lit: Float, ringA: Float, s: Scale) {
    when {
        art != null -> drawImage(art, dstOffset = IntOffset(t.x0, t.y0), dstSize = IntSize(t.w, t.h))
        box != null -> boxTile(t, box, role, s.stdPpu)
        else -> fill(t, 1f, Ink.tile)
    }
    if (border > 0) Rows.ringRects(t, border).forEach { fill(it, 1f) }
    pressFrame(t, s, if (ringA > 0f) lit else 0f, ringA)
}
