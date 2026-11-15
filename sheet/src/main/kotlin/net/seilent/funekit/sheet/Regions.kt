package net.seilent.funekit.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.seilent.funekit.core.Section
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Jump
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.MenuInk
import net.seilent.funekit.kit.MenuPlan
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowKind
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastInk
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.bandGround
import net.seilent.funekit.kit.boxCenter
import net.seilent.funekit.kit.jumpThumb
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.navBand
import net.seilent.funekit.kit.outline
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.swatchFrame
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.px
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import net.seilent.funekit.kit.Ink as KitInk

private const val MIN_PART = 2
private val SIDES = listOf("top", "bottom", "left", "right")

enum class Kind(val key: String) { Glyph("glyph"), Tile("tile"), Ring("ring"), Bar("bar"), Art("art"), Text("text"), Glow("glow") }

class Box(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
    val empty: Boolean get() = x1 <= x0 || y1 <= y0

    fun and(o: Box) = Box(max(x0, o.x0), max(y0, o.y0), min(x1, o.x1), min(y1, o.y1))

    fun grow(m: Float) = Box(x0 - m, y0 - m, x1 + m, y1 + m)

    fun hits(o: Box) = !and(o).empty

    fun minus(o: Box): List<Box> {
        val c = and(o)
        if (c.empty) return listOf(this)
        return listOf(
            Box(x0, y0, x1, c.y0),
            Box(x0, c.y1, x1, y1),
            Box(x0, c.y0, c.x0, c.y1),
            Box(c.x1, c.y0, x1, c.y1),
        ).filterNot { it.empty }
    }

    companion object {
        fun of(e: Edges) = Box(e.x0.toFloat(), e.y0.toFloat(), e.x1.toFloat(), e.y1.toFloat())
    }
}

class Region(
    val name: String,
    val kind: Kind,
    val box: Box,
    val window: Box,
    val band: Box?,
    val color: Int,
    val ref: Int?,
    val alpha: Float,
    val hidden: Boolean,
    var exclude: String?,
    val masks: List<Box> = emptyList(),
    val peakOff: String? = null,
)

class Regions(
    private val board: String,
    private val w: Int,
    private val h: Int,
    s: Scale,
    private val rules: List<Pair<String, String>> = emptyList(),
) : DrawRecord {
    private val margin = (TOLERANCE + 1).toFloat()
    private val status = px(Tok.statusTop + Tok.statusHeight, s.stdPpu)
    private val nav = if (s.navOn) navBand(s).y0 else -1
    private val image = Box(0f, 0f, w.toFloat(), h.toFloat())
    private val list = mutableListOf<Region>()
    private var k = 1f
    private var tx = 0f
    private var ty = 0f
    private var a = 1f
    private var cut = image
    private var why: String? = null
    private var hidden = false
    private var back: Color? = null
    private var veils: List<Pair<Color, Float>> = emptyList()
    var residual = false
    var residualWhy = ""

    private fun map(b: Box) = Box(tx + k * b.x0, ty + k * b.y0, tx + k * b.x1, ty + k * b.y1)

    private fun keep(block: () -> Unit) {
        val sk = k
        val sx = tx
        val sy = ty
        val sa = a
        val sc = cut
        val sw = why
        val sg = hidden
        val sb = back
        val sv = veils
        try {
            block()
        } finally {
            k = sk
            tx = sx
            ty = sy
            a = sa
            cut = sc
            why = sw
            hidden = sg
            back = sb
            veils = sv
        }
    }

    override fun move(dx: Float, dy: Float, block: () -> Unit) = keep {
        tx += k * dx
        ty += k * dy
        block()
    }

    override fun zoom(z: Float, px: Float, py: Float, block: () -> Unit) = keep {
        tx += k * px * (1 - z)
        ty += k * py * (1 - z)
        k *= z
        block()
    }

    override fun fade(f: Float, block: () -> Unit) = keep {
        a *= f
        block()
    }

    override fun clip(x0: Float, y0: Float, x1: Float, y1: Float, block: () -> Unit) = keep {
        cut = cut.and(map(Box(x0, y0, x1, y1)))
        block()
    }

    override fun skip(reason: String, block: () -> Unit) = keep {
        if (why == null) why = reason
        block()
    }

    override fun hide(reason: String, block: () -> Unit) = keep {
        hidden = true
        if (why == null) why = reason
        block()
    }

    override fun on(c: Color, block: () -> Unit) = keep {
        back = c
        block()
    }

    fun under(c: Color, v: Float, block: () -> Unit) = keep {
        veils = listOf(c to v) + veils
        block()
    }

    private fun seen(c: Color): Color = veils.fold(c) { g, (o, v) ->
        Color(g.red + (o.red - g.red) * v, g.green + (o.green - g.green) * v, g.blue + (o.blue - g.blue) * v)
    }

    fun add(name: String, kind: Kind, b: Box, color: Color, alpha: Float, window: Box? = null, line: Box? = null, masks: List<Box> = emptyList(), peakOff: String? = null) {
        val e = alpha * a
        if (e <= 0f) return
        val d = map(b).and(cut)
        if (d.empty) return
        val win = (window?.let { map(it) } ?: d.grow(margin)).and(image)
        val band = line?.let { val m = map(it); Box(m.x0, m.y0, cut.x1, m.y1 + margin).and(cut) }
        val reason = why ?: rules.firstOrNull { name.startsWith(it.first) }?.second
        val whole = Box(floor(d.x0), floor(d.y0), ceil(d.x1), ceil(d.y1))
        val cover = masks.flatMap { map(it).and(win).minus(whole) }.filterNot { it.empty }
        list += Region(name, kind, d, win, band, color.toArgb(), back?.let(::seen)?.toArgb(), e, hidden, reason, cover, peakOff)
    }

    fun shade(b: Box, reason: String) {
        val d = map(b)
        for (r in list) if (r.exclude == null && r.box.hits(d)) r.exclude = reason
    }

    val count: Int get() = list.size

    val regions: List<Region> get() = list

    private fun contrast(r: Region): Float {
        val c = Color(r.color)
        val bg = KitInk.bg
        return r.alpha * maxOf(abs(c.red - bg.red), abs(c.green - bg.green), abs(c.blue - bg.blue))
    }

    private fun levels(r: Region): Int = Math.round(contrast(r) * LEVELS)

    private fun reaches(o: Region, u: Region): Boolean = levels(o) * 2 + ROUNDING >= levels(u)

    fun overlap(from: Int) {
        val under = list.subList(0, from)
        val over = list.subList(from, list.size)
        for (o in over) {
            for (u in under) {
                if (u.kind == Kind.Glow || o.kind == Kind.Glow) continue
                if (o.window.hits(u.box) && reaches(u, o) && o.exclude == null) o.exclude = "over ${u.name}, whose ink reaches its half contrast"
                if (u.window.hits(o.box) && reaches(o, u) && u.exclude == null) u.exclude = "under ${o.name}, whose ink reaches its half contrast"
            }
        }
    }

    fun edges(name: String, kind: Kind, e: Edges, color: Color, alpha: Float, masks: List<Edges> = emptyList(), peakOff: String? = null) =
        add(name, kind, Box.of(e), color, alpha, masks = masks.map { Box.of(it) }, peakOff = peakOff)

    override fun word(name: String, wd: Word, x: Float, base: Float, alpha: Float, color: Color) {
        if (!wd.inked) return
        val runs = wd.runs.map { it.layout }
        val line = if (runs.isEmpty()) null else Box(x, runs.minOf { base - it.firstBaseline }, x, runs.maxOf { base - it.firstBaseline + it.size.height })
        add(name, Kind.Text, Box(x + wd.inkLeft, base + wd.inkTop, x + wd.inkRight, base + wd.inkBottom), color, alpha, line = line)
    }

    override fun ring(name: String, c: Offset, d: Float, alpha: Float) {
        val at = boxCenter(c, d)
        add(name, Kind.Ring, Box(at.x - d / HALF, at.y - d / HALF, at.x + d / HALF, at.y + d / HALF), KitInk.white, alpha)
    }

    override fun glyphs(name: String, gs: List<Glyph>, c: Offset, slot: Float, alpha: Float, ringD: Float, ringStroke: Float) {
        val q = slot / Glyphs.box
        val half = Glyphs.box / HALF
        val at = boxCenter(c, slot)
        val bs = gs.map { glyphBox(it) }
        val b = Box(
            at.x + (bs.minOf { it.x0 } - half) * q, at.y + (bs.minOf { it.y0 } - half) * q,
            at.x + (bs.maxOf { it.x1 } - half) * q, at.y + (bs.maxOf { it.y1 } - half) * q,
        )
        if (ringD <= 0f) {
            add(name, Kind.Glyph, b, KitInk.white, alpha)
            return
        }
        val rc = boxCenter(c, ringD)
        val hole = (ringD / HALF - ringStroke) / sqrt(HALF)
        val room = min(min(b.x0 - (rc.x - hole), (rc.x + hole) - b.x1), min(b.y0 - (rc.y - hole), (rc.y + hole) - b.y1))
        add(name, Kind.Glyph, b, KitInk.white, alpha, b.grow(max(1f, min(margin, room))))
    }

    override fun row(name: String, ink: RowInk, s: Scale, w: Int, alpha: Float, focused: Boolean, press: Float, glow: Boolean, accent: Color, accentOff: Boolean, focusShown: Boolean) {
        val r = ink.r
        val h = Rows.pitch(s, r).toFloat()
        val wf = w.toFloat()
        if (r.kind == RowKind.Divider) {
            val t = Rows.divider(s, 0, r.collection, ink.primary.width, r.appTile, r.song)
            val ba = Rows.dividerAlpha(focused)
            val lx = Rows.letterRight(t, s) - ink.primary.width
            val lb = Rows.letterBaseline(t, s).toFloat()
            edges("$name.tile", Kind.Tile, t, KitInk.white, ba)
            val strips = Rows.letterBorder(t, Rows.dividerBorder(s, focused)).map { Box.of(it) }
            val letter = if (ink.primary.inked) listOf(Box(lx + ink.primary.inkLeft, lb + ink.primary.inkTop, lx + ink.primary.inkRight, lb + ink.primary.inkBottom).grow(1f)) else emptyList()
            strips.forEachIndexed { i, b -> add("$name.tile.${SIDES[i]}", Kind.Bar, b, KitInk.white, ba, masks = strips.filterIndexed { k, _ -> k != i } + letter) }
            clip(0f, 0f, wf, h) {
                word("$name.letter", ink.primary, lx, lb, Roles.letterGlyph.alpha)
            }
            return
        }
        val lines = ink.lines
        val base = lines.title.toFloat()
        run {
            val spot = if (focused) ink.spotFocused else ink.spot
            if (spot != null) {
                val d = if (r.ringScale != 1f) (spot.d * r.ringScale).roundToInt() else spot.d
                val stroke = spot.stroke * d.toFloat() / spot.d
                clip(0f, 0f, wf, h) {
                    val c = Offset(spot.cx.toFloat(), spot.cy.toFloat())
                    if (!r.ringBare) {
                        ring("$name.ring", c, d.toFloat(), spot.alpha)
                    }
                    val parts = if (r.wifi) listOf(Glyphs.wifiDot, Glyphs.wifiArc1, Glyphs.wifiArc2) else listOf(r.glyph)
                    val slot = (d / 2).toFloat()
                    if (r.ringBare) glyphs("$name.glyph", parts, c, slot, spot.alpha) else glyphs("$name.glyph", parts, c, slot, spot.alpha, d.toFloat(), stroke)
                }
            }
            if (r.number.isNotEmpty() && !r.song) clip(0f, 0f, wf, h) { word("$name.number", ink.number, penPx(pen64(Tok.hubEdge, s.ppu)), base, Roles.listCount.alpha) }
            if (r.trailing.isNotEmpty()) clip(0f, 0f, wf, h) { word("$name.trailing", ink.trailing, penPx(ink.trailingX64), base, Roles.listCount.alpha) }
            if (glow && !r.hide && (!r.accent || accentOff)) {
                val pad = px(Tok.pressGlowDilate, s.ppu) + px(Tok.pressGlow, s.ppu)
                val g = ink.glowClip
                add("$name.glow", Kind.Glow, Box((g.x0 - pad).toFloat(), g.y0.toFloat(), g.x1.toFloat(), g.y1.toFloat()), KitInk.white, Tok.pressGlowAlpha)
            }
            val clipL = lines.titleX.toFloat()
            if (!r.hide) {
                clip(clipL, 0f, ink.primaryRight.toFloat(), h) {
                    word("$name.primary", ink.primary, penPx(lines.titleX64), base, if (r.accent && (!focusShown || r.ring)) 1f else alpha, if (r.accent) accent else KitInk.white)
                }
            }
            if (r.secondary.isNotEmpty()) {
                val sa = if (r.libSub && !r.song) Roles.listLibSecondary.alpha else Rows.secondaryRole(r).alpha
                clip(clipL, 0f, ink.right.toFloat(), h) { word("$name.secondary", ink.secondary, penPx(lines.titleX64), lines.sub.toFloat(), sa) }
            }
        }
    }

    override fun thumbParts(name: String, r: ThumbRect, alpha: Float, bands: List<Band>, accent: Color) {
        val whole = Box(r.x0.toFloat(), r.y0.toFloat(), r.x1.toFloat(), r.y1.toFloat())
        val parts = bands.filter { it.y0 < r.y1 && it.y1 > r.y0 }.map { Triple(maxOf(it.y0, r.y0), minOf(it.y1, r.y1), bandGround(it, accent)) }.toMutableList()
        if (parts.size > 1 && parts.first().second - parts.first().first < MIN_PART) {
            val e = parts.removeAt(0)
            parts[0] = parts[0].copy(first = e.first)
        }
        if (parts.size > 1 && parts.last().second - parts.last().first < MIN_PART) {
            val e = parts.removeAt(parts.lastIndex)
            parts[parts.lastIndex] = parts.last().copy(second = e.second)
        }
        for ((from, to, ground) in parts) {
            on(ground) { add("$name.y$from", Kind.Bar, Box(whole.x0, from.toFloat(), whole.x1, to.toFloat()), KitInk.white, alpha, masks = listOf(whole)) }
        }
    }

    override fun chrome(name: String, c: Chrome, now: Long, w: Int) {
        val s = c.scale
        val hp = c.headerPart
        if (!hp.hidden) {
            val hy = hp.dy.toFloat()
            clip(0f, hy, w.toFloat(), hy + px(s.board.pivotTop, s.stdPpu)) {
                val ha = if (c.lit(Chrome.HEADER, now)) Tok.alpha_full else c.headerAlpha
                word("$name.header", c.header, c.headerDrawX(now) + hp.dx, px(Tok.page_headerBaseline, s.stdPpu) + hy, ha * hp.alpha)
            }
        }
        val row = c.row(now)
        for (i in 0 until c.count) {
            val part = c.pivotParts[i]
            if (part.hidden) continue
            val wd = if (i == c.active) c.pivots[i].active else c.pivots[i].rest
            word("$name.pivot$i", wd, row + c.pivotOffset(i) + part.dx, (px(s.board.page_pivotBaseline, s.ppu) + part.dy).toFloat(), c.pivotAlpha(i, now) * part.alpha)
        }
    }

    override fun jump(name: String, g: JumpGrid, ink: JumpInk, cells: JumpCells, s: Scale, off: Float, now: Long, h: Int) {
        val sp = s.stdPpu
        val e = cells.at(Jump.slotOf(Jump.EXIT), off)
        if (e.y1 > 0) {
            val ex64 = pen64(Tok.jumpExitCx, sp) - pen64(ink.exit.width) / 2
            val exitPx = rolePx(Roles.jumpExit, s, sp)
            val base = e.y0 + px(Tok.jumpExitTop - Tok.jumpTop, sp) + Math.round(exitPx * Tok.capHeight)
            word("$name.exit", ink.exit, penPx(ex64), base.toFloat(), if (g.focus == Jump.EXIT) Tok.alpha_full else Roles.jumpExit.alpha)
        }
        val st = cells.at(Jump.slotOf(Jump.SEARCH), off)
        if (st.y1 > 0) {
            val focused = g.focus == Jump.SEARCH
            val ba = if (focused) Tok.alpha_full else Tok.alpha_low
            zoom(g.press.draw(Jump.SEARCH, now), (st.x0 + st.x1) / 2f, (st.y0 + st.y1) / 2f) {
                edges("$name.search", Kind.Tile, st, KitInk.white, ba)
                val c = Offset(((st.x0 + st.x1) / 2).toFloat(), ((st.y0 + st.y1) / 2).toFloat())
                glyphs("$name.search.glyph", listOf(Glyphs.searchRing, Glyphs.searchHandle), c, px(Tok.jumpSearchGlyph, sp).toFloat(), Roles.letterGlyph.alpha)
            }
        }
        for (i in 0 until Section.count) {
            val t = cells.at(Jump.slotOf(i), off)
            if (t.y1 <= 0 || t.y0 >= h) continue
            val focused = g.focus == i
            val ba = if (focused) Tok.alpha_full else Tok.alpha_low
            val z = g.press.draw(i, now)
            zoom(z, (t.x0 + t.x1) / 2f, (t.y0 + t.y1) / 2f) {
                if (z != 1f || g.avail[i]) edges("$name.letter$i", Kind.Tile, t, KitInk.white, ba)
                clip(t.x0.toFloat(), t.y0.toFloat(), t.x1.toFloat(), t.y1.toFloat()) {
                    val l = ink.letters[i]
                    val x = (t.x1 - px(Tok.jumpLetterInsetX, sp)) - l.width
                    word("$name.letter$i.text", l, x, (t.y1 - px(Tok.jumpLetterInsetBaseline, sp)).toFloat(), if (g.avail[i]) Roles.jumpCell.alpha else Tok.jump_emptyAlpha)
                }
            }
        }
        jumpThumb(g, s, off)?.let {
            add("$name.thumb", Kind.Bar, Box(it.x0.toFloat(), it.y0.toFloat(), it.x1.toFloat(), it.y1.toFloat()), KitInk.white, Tok.scrollbar_alpha * g.fade.at(now))
        }
    }

    override fun menuRegions(name: String, menu: Menu, ink: MenuInk, plan: MenuPlan, s: Scale, now: Long, w: Int, h: Int, accent: Color, accentOff: Boolean) {
        val wf = w.toFloat()
        if (menu.form == MenuForm.Sheet) {
            val y0 = (menu.bottomPx - plan.panelH).toFloat()
            skip("the panel is color.bgElevated, 7 levels over color.bg; under the half contrast rule the scrimmed page above it counts as ink") {
                add("$name.panel", Kind.Tile, Box(0f, y0, wf, menu.bottomPx.toFloat()), KitInk.elevated, 1f)
            }
        }
        if (plan.titleRect != null) {
            clip(0f, 0f, ink.titleClip.toFloat(), h.toFloat()) {
                word("$name.title", ink.title, penPx(pen64(Tok.edge, ink.ppu)), plan.titleBase.toFloat(), Roles.dialogTitle.alpha)
            }
        }
        val x = penPx(pen64(Tok.edge, ink.ppu))
        for (slot in plan.rows) {
            val i = slot.i
            val r = ink.rows[i]
            move(0f, slot.top.toFloat()) {
                var a = when {
                    r.note -> Roles.settingsValue.alpha
                    r.line -> Tok.type_dialogItem_alphaDim
                    else -> menu.focus.rowAlpha(i, now, Tok.type_dialogItem_alpha, Tok.type_dialogItem_alphaDim)
                }
                val value = r.value
                if (value != null) {
                    val va = when {
                        i == menu.edit -> 1f
                        r.line -> Tok.settingsValueDim
                        else -> menu.focus.rowAlpha(i, now, Tok.settingsValueFocused, Tok.settingsValueDim)
                    }
                    word("$name.item$i.value", value, penPx(r.vx64), r.valueBase.toFloat(), va, if (i == menu.edit) accent else KitInk.white)
                }
                val swatch = i - menu.lead in menu.swatches.indices
                val color = if (i == menu.mark) accent else KitInk.white
                if (i == menu.pressed) a = 1f
                val clipped = r.line || menu.cellsOn
                val right = if (clipped) r.clipX.toFloat() else wf
                val bottom = if (clipped) slot.h.toFloat() else h.toFloat()
                val top = if (clipped) 0f else -slot.top.toFloat()
                if (swatch && !menu.readOnly && i == menu.focus.row) edges("$name.item$i.swatch", Kind.Tile, ink.swatchFrame(r), KitInk.white, Tok.alpha_full)
                if (!swatch) {
                    clip(0f, top, right, bottom) {
                        word("$name.item$i", r.text, x, r.base.toFloat(), a, color)
                        r.second?.let { word("$name.item$i.second", it, x, (r.base + Metrics.ascent(r.textPx) + Metrics.descent(r.textPx)).toFloat(), a, color) }
                    }
                }
            }
        }
        val n = menu.items.size
        if (menu.form == MenuForm.Sheet && plan.maxFirst > plan.pin) {
            var total = 0L
            var above = 0L
            var most = 0L
            for (i in plan.pin until n) {
                val rh = ink.rows[i].h
                total += rh
                if (i < plan.first) above += rh
                if (i < plan.maxFirst) most += rh
            }
            thumbOf(s, plan.top + plan.pinH, menu.bottomPx, total - most, total, above, ink.ppu)?.let {
                add("$name.thumb", Kind.Bar, Box(it.x0.toFloat(), it.y0.toFloat(), it.x1.toFloat(), it.y1.toFloat()), KitInk.white, Tok.scrollbar_alpha * menu.fade.at(now))
            }
        }
    }

    fun write(file: File) {
        val o = StringBuilder()
        o.append("{\n  \"tile\": ").append(q(file.name.removeSuffix(SUFFIX) + ".png")).append(",\n")
        o.append("  \"board\": ").append(q(board)).append(",\n")
        o.append("  \"size\": [").append(w).append(", ").append(h).append("],\n")
        o.append("  \"tolerance\": ").append(TOLERANCE).append(",\n")
        o.append("  \"margin\": ").append(margin.roundToInt()).append(",\n")
        o.append("  \"bands\": {\"status\": [0, ").append(status).append("], \"nav\": ")
        o.append(if (nav < 0) "[]" else "[$nav, $h]").append("},\n")
        o.append("  \"residual\": {\"on\": ").append(residual).append(", \"why\": ").append(q(residualWhy)).append("},\n")
        o.append("  \"regions\": [")
        list.forEachIndexed { i, r ->
            o.append(if (i == 0) "\n" else ",\n")
            o.append("    {\"name\": ").append(q(r.name))
            o.append(", \"kind\": ").append(q(r.kind.key))
            o.append(", \"box\": ").append(ints(r.box))
            o.append(", \"window\": ").append(ints(r.window))
            o.append(", \"color\": ").append(q("#%06x".format(r.color and RGB)))
            o.append(", \"alpha\": ").append("%.4f".format(java.util.Locale.ROOT, r.alpha))
            o.append(", \"hidden\": ").append(r.hidden)
            o.append(", \"band\": ").append(r.band?.let { ints(it) } ?: "null")
            o.append(", \"ref\": ").append(r.ref?.let { q("#%06x".format(it and RGB)) } ?: "null")
            o.append(", \"mask\": [").append(r.masks.joinToString(", ") { ints(it) }).append("]")
            o.append(", \"peak\": ").append(r.peakOff?.let { q(it) } ?: "null")
            o.append(", \"exclude\": ").append(r.exclude?.let { q(it) } ?: "null").append("}")
        }
        o.append("\n  ]\n}\n")
        file.parentFile!!.mkdirs()
        file.writeText(o.toString())
    }

    private fun ints(b: Box) = "[${floor(b.x0).toInt()}, ${floor(b.y0).toInt()}, ${ceil(b.x1).toInt()}, ${ceil(b.y1).toInt()}]"

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        const val SUFFIX = ".regions.json"
        const val TOLERANCE = 1
        private const val HALF = 2f
        private const val LEVELS = 255f
        private const val ROUNDING = 1
        private const val RGB = 0xFFFFFF
        private val glyphBoxes = HashMap<String, Box>()

        fun glyphBox(g: Glyph): Box = glyphBoxes.getOrPut(g.name) {
            val pts = outline(g.path).subs.flatten()
            val pad = if (g.fill) 0f else Glyphs.stroke / HALF
            Box(pts.minOf { it.x } - pad, pts.minOf { it.y } - pad, pts.maxOf { it.x } + pad, pts.maxOf { it.y } + pad)
        }
    }
}

fun Regions.toast(name: String, t: Toast, ink: ToastInk, s: Scale, now: Long, w: Int) {
    val l = t.look(s, now) ?: return
    val strip = Box(0f, l.y.toFloat(), w.toFloat(), l.stripBottom.toFloat())
    shade(strip, "under the toast strip: a black scrim, so the page under it no longer sits on color.bg")
    skip("the strip is a black scrim 17 levels under color.bg; under the half contrast rule the page it darkens counts as ink") {
        add("$name.strip", Kind.Bar, strip, KitInk.black, l.scrim)
    }
    val c = l.clip
    clip(c.x0.toFloat(), c.y0.toFloat(), c.x1.toFloat(), c.y1.toFloat()) {
        val x = penPx(pen64(Tok.edge, s.ppu))
        word("$name.artist", ink.artist, x, (l.y + px(Tok.toastArtistBaseline, s.ppu)).toFloat(), l.alpha * Roles.toastArtist.alpha)
        word("$name.title", ink.title, x, (l.y + px(Tok.toastTitleBaseline, s.ppu)).toFloat(), l.alpha * Roles.toastTitle.alpha)
    }
}

fun Regions.menuLayer(name: String, menu: Menu, ink: MenuInk, plan: MenuPlan, o: Overlay, s: Scale, now: Long, w: Int, h: Int, accent: Color, accentOff: Boolean) {
    val from = count
    if (o.kind == OverKind.Sheet) {
        val sh = o.sheet(now)
        val dy = (sh.dy * plan.panelH).roundToInt().toFloat()
        shade(Box(0f, menu.bottomPx - plan.panelH + dy, w.toFloat(), h.toFloat()), "under the sheet panel")
        fade(sh.alpha) { move(0f, dy) { on(KitInk.elevated) { menuRegions(name, menu, ink, plan, s, now, w, h, accent, accentOff) } } }
    } else {
        val md = o.dialog(now)
        zoom(md.scale, w / 2f, h / 2f) { fade(md.alpha) { menuRegions(name, menu, ink, plan, s, now, w, h, accent, accentOff) } }
    }
    overlap(from)
}
