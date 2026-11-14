package net.seilent.funekit.sheet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import net.seilent.funekit.Case
import net.seilent.funekit.Role
import net.seilent.funekit.Weight
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.px
import net.seilent.funekit.textPx
import java.io.File
import kotlin.math.roundToInt

object Faces {
    private val dir = listOf(File("src/main/res/font"), File("../kit/src/main/res/font"), File("../kit/kit/src/main/res/font")).firstOrNull { it.isDirectory }
        ?: error("Selawik fonts not found under src/main/res/font, ../kit/src/main/res/font or ../kit/kit/src/main/res/font from ${File("").absolutePath}")
    private val light = Typeface.createFromFile(File(dir, "selawkl.ttf"))
    private val semiLight = Typeface.createFromFile(File(dir, "selawksl.ttf"))
    private val regular = Typeface.createFromFile(File(dir, "selawk.ttf"))
    private val bold = Typeface.createFromFile(File(dir, "selawkb.ttf"))

    fun of(w: Weight): Typeface = when (w) {
        Weight.Light -> light
        Weight.SemiLight -> semiLight
        Weight.Regular -> regular
        Weight.Bold -> bold
    }
}

private const val BYTE = 0xFF
private const val ALPHA_SHIFT = 24
private const val RGB = 0xFFFFFF

fun argb(color: Long, alpha: Float): Int = ((alpha * BYTE).roundToInt() shl ALPHA_SHIFT) or (color.toInt() and RGB)

fun cased(role: Role, s: String) = when (role.case) {
    Case.Lower -> s.lowercase()
    Case.Upper -> s.uppercase()
    Case.Mixed -> s
}

class Ink(val c: Canvas, val ppu: Float, val left: Int, val b: TokBoard.Board, val h: Float, val w: Float = Tok.canvasW) {
    fun ux(u: Float) = left + px(u, ppu)

    fun uy(u: Float) = px(u, ppu)

    fun rect(x: Float, y: Float, w: Float, h: Float) = Rect(ux(x), uy(y), ux(x + w), uy(y + h))

    fun fill(color: Long, alpha: Float) = Paint().also { it.color = argb(color, alpha) }

    fun box(x: Float, y: Float, w: Float, h: Float, color: Long = Tok.color_tile, alpha: Float = Tok.alpha_full) =
        c.drawRect(rect(x, y, w, h), fill(color, alpha))

    fun frame(x: Float, y: Float, w: Float, h: Float, stroke: Float, color: Long = Tok.color_pageHeader, alpha: Float = Tok.alpha_full) {
        box(x, y, w, stroke, color, alpha)
        box(x, y + h - stroke, w, stroke, color, alpha)
        box(x, y, stroke, h, color, alpha)
        box(x + w - stroke, y, stroke, h, color, alpha)
    }

    fun ring(cx: Float, cy: Float, d: Float, stroke: Float, color: Long = Tok.color_pageHeader, alpha: Float = Tok.alpha_full) {
        val p = fill(color, alpha)
        p.style = Paint.Style.STROKE
        p.isAntiAlias = true
        p.strokeWidth = stroke * ppu
        c.drawCircle(left + cx * ppu, cy * ppu, (d - stroke) * ppu / HALF, p)
    }

    fun paint(role: Role, alpha: Float = role.alpha, color: Long = Tok.color_pageHeader) = fill(color, alpha).also {
        it.typeface = Faces.of(role.weight)
        it.textSize = textPx(role.size, ppu, b.minTextPx).toFloat()
        it.isAntiAlias = true
    }

    fun advance(role: Role, s: String) = paint(role).measureText(cased(role, s)) / ppu

    fun line(role: Role) = paint(role).fontMetrics.let { (it.descent - it.ascent) / ppu }

    fun ascent(role: Role) = -paint(role).fontMetrics.ascent / ppu

    fun centre(role: Role) = paint(role).fontMetrics.let { -(it.ascent + it.descent) / HALF / ppu }

    fun text(role: Role, s: String, x: Float, base: Float, alpha: Float = role.alpha, color: Long = Tok.color_pageHeader): Float {
        c.drawText(cased(role, s), ux(x).toFloat(), uy(base).toFloat(), paint(role, alpha, color))
        return advance(role, s)
    }

    fun clip(top: Float, bottom: Float, body: () -> Unit) {
        c.save()
        c.clipRect(ux(0f), uy(top), ux(w), uy(bottom))
        body()
        c.restore()
    }

    companion object {
        const val HALF = 2f
    }
}
