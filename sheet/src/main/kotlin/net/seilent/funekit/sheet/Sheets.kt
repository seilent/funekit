package net.seilent.funekit.sheet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.px
import net.seilent.funekit.scale
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class Mark(val label: String, val l: Int, val t: Int, val r: Int, val b: Int, val color: Long)

class Dev(
    val id: String,
    val label: String,
    val w: Int,
    val h: Int,
    val modes: List<Nav>,
    val probe: String = "",
    val probed: Nav? = null,
    val sdk: Int = SDK,
    val statusPx: Int = 0,
    val navPx: Int = 0,
    val gesturePx: Int = 0,
    val cutout: Rect? = null,
    val extra: List<Mark> = emptyList(),
    val dpi: Dpi? = null,
) {
    val stdPpu = minOf(w / Tok.canvasW, h / TokBoard.v1.canvasH)

    fun withDpi(dpi: Dpi) = Dev(id, label, w, h, modes, probe, probed, sdk, statusPx, navPx, gesturePx, cutout, extra, dpi)

    fun navBottom(nav: Nav) = when {
        nav == Nav.Buttons && probed == Nav.Buttons -> navPx
        nav == Nav.Buttons -> px(Tok.navH, stdPpu)
        nav == Nav.Gesture && probed == Nav.Gesture -> navPx
        else -> 0
    }

    fun window(nav: Nav): Window {
        val g = if (nav == Nav.Gesture && probed == Nav.Gesture) gesturePx else 0
        val c = cutout ?: Rect()
        return Window(
            w, h, sdk,
            Insets(0, statusPx, 0, 0),
            Insets(0, 0, 0, navBottom(nav)),
            Insets(g, 0, g, 0),
            Insets(0, if (c.top == 0) c.bottom else 0, 0, if (c.top > 0) h - c.top else 0),
            Insets(0, 0, 0, 0),
            dpi,
        )
    }

    fun marks(nav: Nav): List<Mark> {
        val out = mutableListOf<Mark>()
        if (statusPx > 0) out += Mark("statusBars $statusPx px, $probe", 0, 0, w, statusPx, Tok.accent_h200)
        cutout?.let { out += Mark("displayCutout ${it.height()} px, $probe", it.left, it.top, it.right, it.bottom, Tok.accent_default) }
        val nb = navBottom(nav)
        when {
            nav == Nav.Buttons && probed == Nav.Buttons ->
                out += Mark("navigationBars $nb px, $probe", 0, h - nb, w, h, Tok.accent_h120)
            nav == Nav.Buttons ->
                out += Mark("3 button bar not probed, drawn at navH $nb px", 0, h - nb, w, h, Tok.accent_h270)
            nav == Nav.Gesture && probed == Nav.Gesture -> {
                out += Mark("navigationBars handle $nb px, $probe", 0, h - nb, w, h, Tok.accent_h120)
                out += Mark("systemGestures $gesturePx px", 0, 0, gesturePx, h, Tok.accent_h50)
                out += Mark("systemGestures $gesturePx px", w - gesturePx, 0, w, h, Tok.accent_h50)
            }
        }
        if (nav == probed) out += extra
        return out
    }

    companion object {
        const val SDK = 35
    }
}

class Tile(val option: String, val path: String, val notes: List<String>, val bmp: Bitmap, val marks: List<Mark>, val stdPpu: Float)

class Sheet(val folder: String, val case: String, val title: List<String>, val tiles: List<Tile>)

fun contentBottom(s: Scale) = if (s.navOn) s.h - Tok.navH else s.h

class Render(val root: File) {
    val sheetPpu = TokBoard.r1.u
    private val gap = px(Tok.gutter, sheetPpu)
    private val stroke = Tok.focus_border / Ink.HALF
    private val segment = Regex("[^/]*/|[^/]+$")

    fun canvas(dev: Dev, nav: Nav, surface: Surface, large: Boolean = false, draw: (Ink, Scale) -> Unit): Pair<Bitmap, Scale> {
        val s = scale(dev.window(nav), nav, surface, large)
        val bmp = Bitmap.createBitmap(dev.w, dev.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(argb(Tok.color_bg, Tok.alpha_full))
        c.save()
        c.clipRect(s.left, 0, s.left + s.widthPx, s.heightPx)
        draw(Ink(c, s.ppu, s.left, s.board, s.h), s)
        c.restore()
        return bmp to s
    }

    fun bands(s: Scale, dev: Dev, nav: Nav, gs: Boolean): List<Mark> {
        val out = mutableListOf<Mark>()
        val x0 = s.left
        val x1 = s.left + s.widthPx
        val white = Tok.color_pageHeader
        out += Mark("funeOS status band 22 u = ${px(Tok.statusTop + Tok.statusHeight, s.stdPpu)} px", x0, 0, x1, px(Tok.statusTop + Tok.statusHeight, s.stdPpu), white)
        if (s.navOn) out += Mark("navH 36 u = ${s.heightPx - px(s.h - Tok.navH, s.ppu)} px", x0, px(s.h - Tok.navH, s.ppu), x1, s.heightPx, white)
        if (s.left > 0) {
            out += Mark("pillar band ${s.left} px color.bg", 0, 0, s.left, s.heightPx, Tok.accent_h300)
            out += Mark("pillar band ${dev.w - x1} px color.bg", x1, 0, dev.w, s.heightPx, Tok.accent_h300)
        }
        if (gs && nav != Nav.Gesture) {
            out += Mark("back edge band left", x0, 0, x0 + px(Tok.gesture_edge, s.stdPpu), s.heightPx, white)
            out += Mark("back edge band right", x1 - px(Tok.gesture_edgeRight, s.stdPpu), 0, x1, s.heightPx, white)
            val b = px(contentBottom(s), s.ppu)
            out += Mark("bottom edge band", x0, b - px(Tok.gesture_bottom, s.stdPpu), x1, b, white)
        }
        return out
    }

    fun facts(dev: Dev, nav: Nav, s: Scale) = "${dev.w}x${dev.h} ${dev.label}, ${nav.name}, ppu ${"%.3f".format(s.ppu)}, H ${"%.2f".format(s.stdH)} u"

    fun tile(folder: String, file: String, option: String, notes: List<String>, bmp: Bitmap, marks: List<Mark>, stdPpu: Float, regions: Regions? = null): Tile {
        val path = "$folder/tiles/$file.png"
        val f = File(root, path)
        f.parentFile!!.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        regions?.write(File(root, "$folder/tiles/$file${Regions.SUFFIX}"))
        return Tile(option, path, notes, bmp, marks, stdPpu)
    }

    private fun pieces(ink: Ink, role: Role, word: String, width: Float): List<String> {
        if (ink.advance(role, word) <= width) return listOf(word)
        val out = mutableListOf<String>()
        var cur = ""
        for (part in segment.findAll(word).map { it.value }) {
            val next = cur + part
            if (cur.isNotEmpty() && ink.advance(role, next) > width) {
                out += cur
                cur = part
            } else cur = next
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    private fun wrap(ink: Ink, role: Role, s: String, width: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = ""
        for (word in s.split(' ').flatMap { pieces(ink, role, it, width) }) {
            val next = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isNotEmpty() && ink.advance(role, next) > width) {
                out += cur
                cur = word
            } else cur = next
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    fun write(sheet: Sheet) {
        val scratch = Ink(Canvas(), sheetPpu, 0, TokBoard.r1, TokBoard.r1.canvasH)
        val primary = Roles.listPrimary
        val secondary = Roles.listSecondary
        val pLine = scratch.line(primary)
        val sLine = scratch.line(secondary)
        val sizes = sheet.tiles.map { t -> val k = sheetPpu / t.stdPpu; (t.bmp.width * k).roundToInt() to (t.bmp.height * k).roundToInt() }
        val captions = sheet.tiles.mapIndexed { i, t ->
            val width = sizes[i].first / sheetPpu
            val lines = mutableListOf<Pair<Role, String>>()
            wrap(scratch, primary, t.option, width).forEach { lines += primary to it }
            wrap(scratch, secondary, t.path, width).forEach { lines += secondary to it }
            t.notes.forEach { n -> wrap(scratch, secondary, n, width).forEach { lines += secondary to it } }
            lines
        }
        val capH = captions.maxOf { c -> c.sumOf { (if (it.first === primary) pLine else sLine).toDouble() }.toFloat() }
        val width = sizes.sumOf { it.first } + gap * (sizes.size + 1)
        val titleLines = sheet.title.flatMap { wrap(scratch, primary, it, (width - gap * HALF_GAPS) / sheetPpu) }
        val titleH = px(titleLines.size * pLine, sheetPpu)
        val height = gap + titleH + gap + sizes.maxOf { it.second } + gap + px(capH, sheetPpu) + gap
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(argb(Tok.color_black, Tok.alpha_full))
        val ink = Ink(c, sheetPpu, 0, TokBoard.r1, height / sheetPpu)
        var ty = gap / sheetPpu
        for (l in titleLines) {
            ink.text(primary, l, gap / sheetPpu, ty + ink.ascent(primary), Tok.alpha_full)
            ty += pLine
        }
        var x = gap
        val top = gap + titleH + gap
        val filter = Paint(Paint.FILTER_BITMAP_FLAG)
        sheet.tiles.forEachIndexed { i, t ->
            val (w, h) = sizes[i]
            c.drawBitmap(t.bmp, null, Rect(x, top, x + w, top + h), filter)
            marks(ink, t, x, top)
            var cy = (top + h + gap) / sheetPpu
            for ((role, l) in captions[i]) {
                ink.text(role, l, x / sheetPpu, cy + ink.ascent(role), Tok.alpha_full)
                cy += if (role === primary) pLine else sLine
            }
            x += w + gap
        }
        val f = File(root, "${sheet.folder}/${sheet.case}.png")
        f.parentFile!!.mkdirs()
        f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        out.recycle()
        sheet.tiles.forEach { it.bmp.recycle() }
    }

    private fun marks(ink: Ink, t: Tile, ox: Int, oy: Int) {
        val k = sheetPpu / t.stdPpu
        val role = Roles.listSecondary
        val line = ink.line(role)
        val used = mutableListOf<Pair<Float, Float>>()
        for (m in t.marks) {
            val l = (ox + m.l * k) / sheetPpu
            val tp = (oy + m.t * k) / sheetPpu
            val w = max((m.r - m.l) * k / sheetPpu, stroke * HALF_GAPS)
            val h = max((m.b - m.t) * k / sheetPpu, stroke * HALF_GAPS)
            ink.frame(l, tp, w, h, stroke, m.color)
            val tileBottom = (oy + t.bmp.height * k) / sheetPpu
            var ly = if (h >= line) tp + stroke else if (tp + h + line <= tileBottom) tp + h else tp - line
            while (used.any { (a, b) -> ly < b && ly + line > a }) ly += line
            used += ly to ly + line
            val label = m.label
            ink.box(l + stroke, ly, ink.advance(role, label), line, Tok.color_black, Tok.alpha_high)
            ink.text(role, label, l + stroke, ly + ink.ascent(role), Tok.alpha_full, m.color)
        }
    }

    fun index(folder: String, lines: List<String>) {
        val f = File(root, "$folder/index.md")
        f.parentFile!!.mkdirs()
        f.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    companion object {
        const val PNG_QUALITY = 100
        const val HALF_GAPS = 2
        const val BASE = "#/funekit/scale"
    }
}
