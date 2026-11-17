package net.seilent.funekit.kit.stack

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuInk
import net.seilent.funekit.kit.MenuPlan
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.proj.M3

enum class Mark { Glyph, Ring, Text, Glow, Bar }

interface DrawRecord {
    fun move(dx: Float, dy: Float, block: () -> Unit)

    fun zoom(z: Float, px: Float, py: Float, block: () -> Unit)

    fun fade(f: Float, block: () -> Unit)

    fun clip(x0: Float, y0: Float, x1: Float, y1: Float, block: () -> Unit)

    fun skip(reason: String, block: () -> Unit)

    fun hide(reason: String, block: () -> Unit)

    fun on(c: Color, block: () -> Unit)

    fun word(name: String, wd: Word, x: Float, base: Float, alpha: Float, color: Color = Ink.white)

    fun ring(name: String, c: Offset, d: Float, alpha: Float)

    fun glyphs(name: String, gs: List<Glyph>, c: Offset, slot: Float, alpha: Float, ringD: Float = 0f, ringStroke: Float = 0f)

    fun row(name: String, ink: RowInk, s: Scale, w: Int, alpha: Float, focused: Boolean, press: Float, glow: Boolean, accent: Color, accentOff: Boolean, focusShown: Boolean = false)

    fun thumbParts(name: String, r: ThumbRect, alpha: Float, bands: List<Band>, accent: Color)

    fun chrome(name: String, c: Chrome, now: Long, w: Int)

    fun jump(name: String, g: JumpGrid, ink: JumpInk, cells: JumpCells, s: Scale, off: Float, now: Long, h: Int)

    fun menuRegions(name: String, menu: Menu, ink: MenuInk, plan: MenuPlan, s: Scale, now: Long, w: Int, h: Int, accent: Color, accentOff: Boolean)

    fun quad(name: String, a: Float, t: M3, src: Edges?, block: () -> Unit) = block()

    fun box(name: String, kind: Mark, r: Rect, alpha: Float, color: Color = Ink.white) {}
}

fun DrawScope.shifted(rec: DrawRecord?, dx: Float, dy: Float, block: DrawScope.() -> Unit) = translate(dx, dy) {
    val d = this
    if (rec == null) d.block() else rec.move(dx, dy) { d.block() }
}

fun DrawScope.clipped(rec: DrawRecord?, x0: Float, y0: Float, x1: Float, y1: Float, block: DrawScope.() -> Unit) = clipRect(x0, y0, x1, y1) {
    val d = this
    if (rec == null) d.block() else rec.clip(x0, y0, x1, y1) { d.block() }
}

fun DrawScope.zoomed(rec: DrawRecord?, k: Float, x: Float, y: Float, block: DrawScope.() -> Unit) = withTransform({ scale(k, k, Offset(x, y)) }) {
    val d = this
    if (rec == null) d.block() else rec.zoom(k, x, y) { d.block() }
}

fun DrawScope.layer(rec: DrawRecord?, a: Float, paint: Paint, block: DrawScope.() -> Unit) {
    val d = this
    val body = { if (rec == null) d.block() else rec.fade(a) { d.block() } }
    if (a >= 1f) {
        body()
        return
    }
    paint.alpha = a
    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
    body()
    drawContext.canvas.restore()
}
