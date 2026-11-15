package net.seilent.funekit.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Case
import net.seilent.funekit.Fonts
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PAIR = 2
private const val GROW = 2

enum class MenuForm { Sheet, Dlg }

private fun clamp(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (hi < v) hi else v

private fun mixedRole(r: Role) = Role(r.size, r.weight, Case.Mixed, r.alpha, r.tracking)

class Menu {
    var form = MenuForm.Dlg
        private set
    var title = ""
        private set
    var items: List<String> = emptyList()
        private set
    var readOnly = false
        private set
    var lead = 0
        private set
    var mark = -1
        private set
    var edit = -1
        private set
    var mixedRow = -1
        private set
    var mixedTitle = false
        private set
    var valueSheet = false
        private set
    var cellsOn = false
        private set
    var values: List<String> = emptyList()
        private set
    var mixed: List<Boolean> = emptyList()
        private set
    var wraps: List<String> = emptyList()
        private set
    var subs: List<String> = emptyList()
        private set
    var swatches: List<Color> = emptyList()
        private set
    var kept = false
        private set
    var capped = false
        private set
    var maxPanel = 0
        private set
    var pressed = -1
        private set
    var version by mutableIntStateOf(0)
        private set
    var content by mutableIntStateOf(0)
        private set

    val focus = FocusTrack()
    val glow = Glow()
    val fade = ThumbFade()
    private var wrap = false
    private var free = false
    private var unglow = false
    internal var first = 0
    internal var shown = 0
    internal var pin = 0
    internal var maxFirst = 0
    internal var drawnFirst = 0
    internal var placed: MenuPlan? = null
    var confirm = false
        private set
    var held = false
    val atTop: Boolean get() = first <= pin
    var topPx = 0
        private set
    var bottomPx = 0
        private set

    private fun bump() {
        version++
    }

    private fun change() {
        content++
        version++
    }

    fun open(
        f: MenuForm,
        title: String,
        items: List<String>,
        now: Long,
        readOnly: Boolean = false,
        mixedRow: Int = -1,
        mixedTitle: Boolean = false,
        lead: Int = 0,
        confirm: Boolean = false,
    ) {
        form = f
        this.confirm = confirm
        held = false
        this.readOnly = readOnly
        valueSheet = false
        wrap = false
        cellsOn = false
        mark = -1
        edit = -1
        values = emptyList()
        mixed = emptyList()
        wraps = emptyList()
        subs = emptyList()
        swatches = emptyList()
        pin = 0
        maxFirst = 0
        this.mixedTitle = mixedTitle
        this.mixedRow = mixedRow
        this.lead = if (readOnly) 0 else clamp(lead, 0, max(0, items.size - 1))
        this.title = title
        this.items = items
        focus.attach()
        focus.set(this.lead, items.size, now, false)
        first = 0
        shown = items.size
        free = false
        pressed = -1
        unglow = false
        glow.clear()
        panelTop = -1
        change()
    }

    fun value(focusRow: Int, mark: Int, wrap: Boolean, now: Long) {
        valueSheet = true
        this.mark = mark
        this.wrap = wrap
        free = false
        focus.set(clamp(focusRow, lead, max(lead, items.size - 1)), items.size, now, false)
        change()
    }

    fun mark(i: Int) {
        mark = i
        bump()
    }

    fun cells(values: List<String>, mixed: List<Boolean>, edit: Int) {
        cellsOn = true
        this.values = values
        this.mixed = mixed
        this.edit = edit
        change()
    }

    fun leadParts(wraps: List<String>, subs: List<String> = emptyList()) {
        this.wraps = wraps
        this.subs = subs
        change()
    }

    fun swatches(colors: List<Color>) {
        swatches = colors
        change()
    }

    fun frame(top: Int, bottom: Int) {
        topPx = top
        bottomPx = bottom
        bump()
    }

    fun dialogEnd(s: Scale): Int {
        val item = rolePx(Roles.dialogItem, s, s.ppu)
        val count = rolePx(Roles.listCount, s, s.ppu)
        return px(Tok.contentTop, s.ppu) + items.size * px(Tok.settingsPitch, s.ppu) +
            wraps.count { it.isNotEmpty() } * (Metrics.ascent(item) + Metrics.descent(item)) +
            subs.count { it.isNotEmpty() } * (Metrics.ascent(count) + Metrics.descent(count))
    }

    fun keep(kept: Boolean, capped: Boolean, maxPanel: Int) {
        this.kept = kept
        this.capped = kept && capped
        this.maxPanel = if (this.capped) maxPanel else 0
        change()
    }

    fun press(i: Int, now: Long) {
        pressed = if (i >= lead && !readOnly) i else -1
        unglow = false
        if (pressed < 0 || pressed != glow.key) glow.clear()
        if (pressed >= 0 && glow.key != pressed) glow.down(pressed, now)
        bump()
    }

    fun dropGlow() {
        unglow = true
        glow.clear()
        bump()
    }

    internal val glowing: Boolean get() = !unglow

    fun scrollTo(to: Int, now: Long): Boolean {
        val n = items.size
        if (form != MenuForm.Sheet || shown >= n) return false
        val f = clamp(to, pin, max(pin, maxFirst))
        if (f == first) return false
        first = f
        bump()
        if (swatches.isNotEmpty()) {
            free = true
            return true
        }
        val fr = clamp(focus.row, first, first + shown - pin - 1)
        if (fr != focus.row) focus.set(fr, n, now, false)
        return true
    }

    fun step(delta: Int, now: Long, hold: Boolean = false): Boolean {
        if (readOnly) return scrollTo(first + delta, now)
        val n = items.size
        val to = focus.row + delta
        if (to in lead until n) {
            focus.set(focus.row + if (delta > 0) 1 else -1, n, now, true)
            free = false
            bump()
            return true
        }
        if (!wrap || hold || n - lead < PAIR) return false
        focus.set(if (delta > 0) lead else n - 1, n, now, true)
        free = false
        bump()
        return true
    }

    var panelTop by mutableIntStateOf(-1)
        private set

    fun backTarget(x: Float, y: Float, o: Overlay, now: Long, bandTop: Int): Boolean {
        val p = placed ?: return false
        if (y >= bandTop) return false
        if (o.kind == OverKind.Sheet && !o.closing && o.moving(now)) {
            val h = p.panelH
            if (y < bottomPx - h + (o.sheet(now).dy * h).roundToInt()) return true
        }
        if (p.rows.any { y >= it.top && y < it.top + it.h }) return false
        if (form == MenuForm.Dlg) {
            val t = p.titleRect ?: return true
            return !(x >= t.x0 && x < t.x1 && y >= t.y0 && y < t.y1)
        }
        return y < bottomPx - p.panelH
    }

    fun settle(p: MenuPlan) {
        placed = p
        panelTop = if (form == MenuForm.Sheet) bottomPx - p.panelH else -1
        first = p.first
        shown = p.shown
        pin = p.pin
        maxFirst = p.maxFirst
    }

    fun plan(ink: MenuInk): MenuPlan {
        val n = items.size
        val pitch = ink.pitch
        val edge = ink.edge
        var titleH = ink.titleH
        var pin = ink.pin
        var first = this.first
        val h = ink.rows.map { it.h }
        var pinH = (0 until min(pin, n)).sumOf { h[it] }
        var maxFirst = 0
        var vis = n
        if (form == MenuForm.Sheet && n > 0) {
            val room = bottomPx - topPx - PAIR * edge - titleH
            if (readOnly) {
                var used = 0
                var last = n
                while (last > 0 && used + h[last - 1] <= room) used += h[--last]
                maxFirst = min(last, n - 1)
                first = clamp(first, 0, maxFirst)
                used = 0
                vis = 0
                while (first + vis < n && (vis == 0 || used + h[first + vis] <= room)) used += h[first + vis++]
            } else {
                vis = clamp(pin + (room - pinH) / pitch, min(n, pin + 1), n)
                if (capped) {
                    val fit = vis
                    val least = min(n, pin + 1)
                    fun over() = PAIR * edge + titleH + pinH + (vis - pin) * pitch > maxPanel
                    vis = clamp(pin + (maxPanel - PAIR * edge - titleH - pinH) / pitch, least, fit)
                    if (titleH > 0 && over()) {
                        titleH = 0
                        vis = clamp(pin + (maxPanel - PAIR * edge - pinH) / pitch, least, fit)
                    }
                    if (pin > 0 && over()) {
                        val rows = max(1, min(n - pin, fit - pin))
                        pin = 0
                        pinH = 0
                        vis = clamp((maxPanel - PAIR * edge) / pitch, 1, rows)
                    }
                }
                val roomRows = vis - pin
                val fr = clamp(focus.row, pin, n - 1)
                maxFirst = n - roomRows
                first = if (free) {
                    clamp(first, pin, max(pin, n - roomRows))
                } else {
                    clamp(first, max(pin, fr - roomRows + 1), min(fr, n - roomRows))
                }
            }
        } else {
            first = pin
        }
        val order = (0 until vis).map { if (it < pin) it else first + it - pin }
        val rowsH = order.sumOf { h[it] }
        val panelH: Int
        val titleBase: Int
        val top: Int
        if (form == MenuForm.Sheet) {
            panelH = edge + titleH + rowsH + edge
            val y0 = bottomPx - panelH
            titleBase = y0 + edge + ink.titleAscent
            top = y0 + edge + titleH
        } else {
            panelH = 0
            titleBase = ink.dialogTitleBase
            top = ink.dialogTop
        }
        var rt = top
        val rows = order.map { i -> MenuSlot(i, rt, h[i]).also { rt += h[i] } }
        val titleRect = if (title.isNotEmpty() && titleH > 0) {
            Edges(edge, titleBase - ink.titleAscent, min(ink.titleClip, edge + penFloor(pen64(ink.title.width))), titleBase + ink.titleDescent)
        } else {
            null
        }
        return MenuPlan(pitch, top, pinH, rowsH, panelH, titleBase, titleRect, rows, first, pin, vis, maxFirst)
    }
}

class MenuRow(
    val text: Word,
    val second: Word?,
    val wrap: Word?,
    val sub: Word?,
    val value: Word?,
    val vx64: Int,
    val clipX: Int,
    val h: Int,
    val base: Int,
    val valueBase: Int,
    val textPx: Int,
    val note: Boolean,
    val line: Boolean,
    val glow: List<GlowText>,
    val glowClip: Edges,
)

class MenuInk(
    val ppu: Float,
    val pitch: Int,
    val edge: Int,
    val title: Word,
    val titleH: Int,
    val titleAscent: Int,
    val titleDescent: Int,
    val dialogTitleBase: Int,
    val dialogTop: Int,
    val titleClip: Int,
    val pin: Int,
    val parts: Boolean,
    val countPx: Int,
    val rows: List<MenuRow>,
)

fun MenuInk.swatch(r: MenuRow): Edges {
    val sy = (pitch - r.textPx) / PAIR
    return Edges(edge, sy, edge + r.textPx, sy + r.textPx)
}

fun MenuInk.swatchBorder(): Int = px(Tok.focus_border, ppu)

fun MenuInk.swatchFrame(r: MenuRow): Edges {
    val sq = swatch(r)
    val g = GROW * swatchBorder()
    return Edges(sq.x0 - g, sq.y0 - g, sq.x1 + g, sq.y1 + g)
}

class MenuSlot(val i: Int, val top: Int, val h: Int)

class MenuPlan(
    val pitch: Int,
    val top: Int,
    val pinH: Int,
    val rowsH: Int,
    val panelH: Int,
    val titleBase: Int,
    val titleRect: Edges?,
    val rows: List<MenuSlot>,
    val first: Int,
    val pin: Int,
    val shown: Int,
    val maxFirst: Int,
)

fun menuInk(m: TextMeasurer, fonts: FontFamily.Resolver, menu: Menu, s: Scale, density: Density, accentOff: Boolean, covers: Covers?): MenuInk {
    val mp = if (menu.kept) s.stdPpu else s.ppu
    fun style(role: Role, ppu: Float = mp) = roleStyle(role, s, density, Ink.white, ppu = ppu)
    fun text(t: String, role: Role, ppu: Float = mp) = m.word(t, role, style(role, ppu), fonts, density, covers)
    val pitch = px(Tok.settingsPitch, mp)
    val edge = px(Tok.edge, mp)
    val titleRole = if (menu.mixedTitle) mixedRole(Roles.dialogTitle) else Roles.dialogTitle
    val titlePx = rolePx(titleRole, s, s.stdPpu)
    val itemRole = Roles.dialogItem
    val mixedItem = mixedRole(Roles.dialogItem)
    val itemPx = rolePx(itemRole, s, mp)
    val valuePx = rolePx(Roles.settingsValue, s, mp)
    val countPx = rolePx(Roles.listCount, s, mp)
    val title = m.word(menu.title, titleRole, style(titleRole, s.stdPpu), fonts, density, covers)
    val titleH = if (menu.title.isEmpty()) 0 else Metrics.ascent(titlePx) + Metrics.descent(titlePx)
    val parts = menu.wraps.isNotEmpty() || menu.subs.isNotEmpty()
    val pin = if ((menu.form == MenuForm.Sheet && (menu.valueSheet || menu.cellsOn)) || parts) menu.lead else 0
    val w = s.widthPx
    val mixedWidth = textWidth(mixedItem, style(mixedItem), fonts, density, covers)
    fun width64(t: String) = pen64(mixedWidth(t))
    val rows = menu.items.mapIndexed { i, t ->
        val line = menu.readOnly || i < menu.lead
        val note = menu.valueSheet && i < menu.lead
        val isMixed = i == menu.mixedRow || menu.mixed.getOrElse(i) { false }
        val role = if (note) Roles.settingsValue else if (isMixed) mixedItem else itemRole
        val rolePx = if (note) valuePx else itemPx
        val v = menu.values.getOrElse(i) { "" }
        val valueRole = Roles.settingsValue
        val valueStyle = style(valueRole).let { if (i == menu.edit) it.copy(fontWeight = Fonts.weight(Accent.bold(valueRole.weight, accentOff))) else it }
        val value = if (v.isEmpty()) null else m.word(v, valueRole, valueStyle, fonts, density, covers)
        val vx64 = if (value == null) Int.MIN_VALUE else pen64(w.toFloat()) - pen64(Tok.edge, mp) - pen64(value.width)
        val clipX = if (value == null) w - edge else penFloor(vx64) - px(Tok.gutter, mp)
        var h = pitch
        var cut = 0
        val swatch = i - menu.lead in menu.swatches.indices
        val wrapText = menu.wraps.getOrElse(i) { "" }
        val subText = menu.subs.getOrElse(i) { "" }
        if (parts && i < menu.lead) {
            if (wrapText.isNotEmpty()) h += Metrics.ascent(itemPx) + Metrics.descent(itemPx)
            if (subText.isNotEmpty()) h += Metrics.ascent(countPx) + Metrics.descent(countPx)
        } else if (menu.form == MenuForm.Sheet && line && !note && isMixed && (menu.readOnly || i < pin) && !swatch) {
            val room64 = pen64(clipX.toFloat()) - pen64(Tok.edge, mp)
            if (width64(t) > room64) {
                val k = wrapAt(t, room64) { width64(it) }
                if (k > 0) {
                    cut = k
                    h = pitch + Metrics.ascent(itemPx) + Metrics.descent(itemPx)
                }
            }
        }
        val base = (pitch - Metrics.ascent(rolePx) - Metrics.descent(rolePx)) / PAIR + Metrics.ascent(rolePx)
        val head = if (cut > 0) t.substring(0, cut) else t
        val restAt = if (cut > 0) (cut until t.length).firstOrNull { t[it] != ' ' } else null
        val second = if (cut > 0) text(restAt?.let { t.substring(it) } ?: "", role) else null
        val first = text(head, role)
        val step = Metrics.ascent(rolePx) + Metrics.descent(rolePx)
        val x64 = pen64(Tok.edge, mp)
        MenuRow(
            first,
            second,
            if (parts && i < menu.lead && wrapText.isNotEmpty()) text(wrapText, role) else null,
            if (parts && i < menu.lead && subText.isNotEmpty()) text(subText, Roles.listCount) else null,
            value,
            vx64,
            clipX,
            h,
            base,
            (pitch - Metrics.ascent(valuePx) - Metrics.descent(valuePx)) / PAIR + Metrics.ascent(valuePx),
            rolePx,
            note,
            line,
            listOfNotNull(GlowText(first, x64, base, rolePx), second?.let { GlowText(it, x64, base + step, rolePx) }),
            Edges(0, 0, clipX, h),
        )
    }
    return MenuInk(
        mp, pitch, edge, title, titleH, Metrics.ascent(titlePx), Metrics.descent(titlePx),
        px(Tok.pivotBaseline, mp), px(Tok.contentTop, mp), w - edge, pin, parts, countPx, rows,
    )
}

fun DrawScope.menu(menu: Menu, ink: MenuInk, plan: MenuPlan, s: Scale, now: Long, accent: Color, accentOff: Boolean) {
    val w = size.width
    if (menu.form == MenuForm.Sheet) {
        val y0 = (menu.bottomPx - plan.panelH).toFloat()
        drawRect(Ink.elevated, Offset(0f, y0), Size(w, menu.bottomPx - y0))
    }
    if (plan.titleRect != null) {
        clipRect(0f, 0f, ink.titleClip.toFloat(), size.height) {
            word(ink.title, penPx(pen64(Tok.edge, ink.ppu)), plan.titleBase.toFloat(), Ink.white, Roles.dialogTitle.alpha)
        }
    }
    val x = penPx(pen64(Tok.edge, ink.ppu))
    for (slot in plan.rows) {
        val i = slot.i
        val r = ink.rows[i]
        translate(0f, slot.top.toFloat()) {
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
                word(value, penPx(r.vx64), r.valueBase.toFloat(), if (i == menu.edit) accent else Ink.white, va)
            }
            val si = i - menu.lead
            val swatch = si in menu.swatches.indices
            val color = if (i == menu.mark) accent else Ink.white
            if (i == menu.pressed) a = 1f
            val clipped = r.line || menu.cellsOn
            val right = if (clipped) r.clipX.toFloat() else w
            val bottom = if (clipped) slot.h.toFloat() else size.height
            val top = if (clipped) 0f else -slot.top.toFloat()
            if (i == menu.pressed && !swatch && menu.glowing && menu.glow.key != null && (i != menu.mark || accentOff)) pressGlow(menu.glow, r.glow, r.glowClip, ink.ppu, menu.glow.level(now))
            if (swatch) {
                val sq = ink.swatch(r)
                drawRect(menu.swatches[si], Offset(sq.x0.toFloat(), sq.y0.toFloat()), Size(sq.w.toFloat(), sq.h.toFloat()))
                if (!menu.readOnly && i == menu.focus.row) {
                    Rows.letterBorder(ink.swatchFrame(r), ink.swatchBorder()).forEach {
                        drawRect(Ink.white, Offset(it.x0.toFloat(), it.y0.toFloat()), Size(it.w.toFloat(), it.h.toFloat()), Tok.alpha_full)
                    }
                }
            } else {
                clipRect(0f, top, right, bottom) {
                    word(r.text, x, r.base.toFloat(), color, a)
                    r.second?.let { word(it, x, (r.base + Metrics.ascent(r.textPx) + Metrics.descent(r.textPx)).toFloat(), color, a) }
                }
            }
            if (ink.parts && i < menu.lead) {
                clipRect(0f, top, right, bottom) {
                    var next = r.base
                    r.wrap?.let {
                        next += Metrics.ascent(r.textPx) + Metrics.descent(r.textPx)
                        word(it, x, next.toFloat(), color, a)
                    }
                    r.sub?.let {
                        word(it, x, (next + Metrics.descent(r.textPx) + Metrics.ascent(ink.countPx)).toFloat(), Ink.white, Roles.listCount.alpha)
                    }
                }
            }
        }
    }
    val n = menu.items.size
    var bar: ThumbRect? = null
    if (menu.form == MenuForm.Sheet && plan.maxFirst > plan.pin) {
        var total = 0L
        var above = 0L
        var most = 0L
        for (i in plan.pin until n) {
            val h = ink.rows[i].h
            total += h
            if (i < plan.first) above += h
            if (i < plan.maxFirst) most += h
        }
        bar = thumbOf(s, plan.top + plan.pinH, menu.bottomPx, total - most, total, above, ink.ppu)
    }
    menu.fade.track(bar != null, plan.first != menu.drawnFirst || menu.held, now)
    if (!menu.held) menu.fade.release(now)
    menu.drawnFirst = plan.first
    bar?.let { thumb(it, menu.fade.at(now)) }
}
