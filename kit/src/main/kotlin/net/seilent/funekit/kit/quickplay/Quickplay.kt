package net.seilent.funekit.kit.quickplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.fxDiv
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxOfPx
import net.seilent.funekit.fxU
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptText
import net.seilent.funekit.kit.BoxInk
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.artTile
import net.seilent.funekit.kit.boxInk
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeInk
import net.seilent.funekit.kit.home.HomeLook
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.home.homeMenu
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.Leg
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.proj.M3
import net.seilent.funekit.kit.proj.qpProjection
import net.seilent.funekit.kit.proj.t6
import net.seilent.funekit.kit.pxAt
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.textWidth
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.px64
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

const val QP_PINS = 0
const val QP_PLAYING = 3
const val QP_NOW = "Playing"
const val QP_PAUSED = "Paused"
const val QP_HINT = "press and hold a media item to pin it to quickplay."
val QP_HEADERS = listOf("Pins", "History", "New")

private const val SECTIONS = 3
private const val COLUMNS = 2
private const val SIDES = 2
private const val PASSES = 2
private const val NEW = 2
private const val HIDDEN = -1

class QpTile(val section: Int, val item: QpItem)

class QpTiles(val tiles: List<QpTile>, val counts: IntArray, val paused: Boolean = false)

fun qpTiles(d: QpData): QpTiles {
    val max = Tok.quickplay_tiles.toInt()
    val counts = IntArray(SECTIONS)
    val out = ArrayList<QpTile>()
    d.playing?.let { out += QpTile(QP_PLAYING, it) }
    fun add(items: List<QpItem>, section: Int, cap: Int) {
        for (it in items) {
            if (counts[section] >= cap) break
            out += QpTile(section, it)
            counts[section]++
        }
    }
    add(d.pins, 0, Int.MAX_VALUE)
    add(d.history, 1, max)
    add(d.newest, NEW, max)
    return QpTiles(out, counts, d.paused && d.playing != null)
}

class QpInk(val headers: List<Word>, val now: Word, val hint: List<Word>, val paused: Word = now)

fun qpHintLines(text: String, max64: Int, width64: (String) -> Int): List<String> {
    val out = mutableListOf<String>()
    var line = ""
    for (word in text.split(' ')) {
        val next = if (line.isEmpty()) word else "$line $word"
        if (line.isNotEmpty() && width64(next) > max64) {
            out += line
            line = word
        } else {
            line = next
        }
    }
    if (line.isNotEmpty()) out += line
    return out
}

fun qpInk(m: TextMeasurer, fonts: FontFamily.Resolver, s: Scale, density: Density, status: String = ""): QpInk {
    val sp = s.stdPpu
    val hs = roleStyle(Roles.quickplayHeader, s, density, Ink.white, ppu = sp)
    val hint = keptText(Roles.qpHint, s)
    val ts = roleStyle(hint, s, density, Ink.white, ppu = sp)
    val w = textWidth(hint, ts, fonts, density, null)
    val lines = qpHintLines(status.ifEmpty { QP_HINT }, pen64(Tok.qpHintW, sp)) { pen64(w(it)) }
    return QpInk(
        QP_HEADERS.map { m.word(it, Roles.quickplayHeader, hs, fonts, density, null) },
        m.word(QP_NOW, Roles.quickplayHeader, hs, fonts, density, null),
        lines.map { m.word(it, hint, ts, fonts, density, null) },
        m.word(QP_PAUSED, Roles.quickplayHeader, hs, fonts, density, null),
    )
}

private object QpU {
    val baseline = fx(Tok.qpBaseline)
    val tileTop = fx(Tok.qpTileTop)
    val tile = fx(Tok.qpTile)
    val gutter = fx(Tok.qpGutter)
    val edge = fx(Tok.qpEdge)
    val sectionGap = fx(Tok.qpSectionGap)
    val hintBase = fx(Tok.qpHintBase)
    val hintStep = fx(Tok.qpHintStep)
    val hintBaseLarge = fx(Tok.qpHintBaseLarge)
    val hintStepLarge = fx(Tok.qpHintStepLarge)
    val hintGap = fx(Tok.qpHintGap)
    val endPad = fx(Tok.qpEndPad)
    val playTop = fx(Tok.qpPlayTop)
    val playGap = fx(Tok.qpPlayGap)
    val border = fx(Tok.focus_border)
}

class QpLayout(val s: Scale, val q: QpTiles, private val hintLines: Int) {
    private val sp = s.stdPpu
    val ppu = fx(sp)
    private val view = fx(s.stdH)
    val tiles: List<QpTile> get() = q.tiles
    val count: Int get() = q.tiles.size
    val playing: Boolean = q.tiles.firstOrNull()?.section == QP_PLAYING
    val paused: Boolean = playing && q.paused
    private val playArt = COLUMNS * QpU.tile + QpU.gutter
    val playSpan = QpU.playTop + playArt + QpU.playGap
    private val border = fxDiv(fxInt(px64(QpU.border, ppu)), ppu)
    val panelPx: Int = px(Tok.qpW, sp)
    val caption: Role = keptText(Roles.boxCaption, s)
    private val hintBase = if (s.large) QpU.hintBaseLarge else QpU.hintBase
    private val hintStep = if (s.large) QpU.hintStepLarge else QpU.hintStep

    fun sectionHeight(k: Int): Long {
        if (k == 0 && q.counts[0] == 0) return hintBase + hintStep * max(0, hintLines - 1)
        val rows = (q.counts[k] + 1) / COLUMNS
        return QpU.tileTop + if (rows > 0) rows * QpU.tile + (rows - 1) * QpU.gutter else 0L
    }

    fun sectionGap(k: Int): Long = if (k == 0 && q.counts[0] == 0) QpU.hintGap else QpU.sectionGap

    fun sectionTop(k: Int): Long {
        var top = QpU.baseline
        if (k == QP_PLAYING) return top
        if (playing) top += playSpan
        for (i in 0 until k) top += sectionHeight(i) + sectionGap(i)
        return top
    }

    fun hintTop(i: Int): Long = sectionTop(0) + hintBase + i * hintStep

    fun firstSection(): Int = if (playing) QP_PLAYING else if (q.counts[0] > 0) 0 else 1

    fun local(i: Int): Int {
        val k = q.tiles[i].section
        if (k == QP_PLAYING) return 0
        var first = if (playing) 1 else 0
        for (j in 0 until k) first += q.counts[j]
        return i - first
    }

    fun tileTop(i: Int): Long {
        val t = q.tiles[i]
        if (t.section == QP_PLAYING) return sectionTop(QP_PLAYING) + QpU.playTop
        return sectionTop(t.section) + QpU.tileTop + (local(i) / COLUMNS) * (QpU.tile + QpU.gutter)
    }

    fun tileX(i: Int): Long = QpU.edge + (local(i) % COLUMNS) * (QpU.tile + QpU.gutter)

    fun tileSide(i: Int): Long = if (q.tiles[i].section == QP_PLAYING) playArt else QpU.tile

    val limit: Long = max(0L, sectionTop(NEW) + sectionHeight(NEW) + QpU.endPad - view)

    val contentPx: Int = s.heightPx + px64(limit, ppu)

    fun pxOf(u: Long): Int = px64(u, ppu)

    fun ofPx(px: Float): Long = fxOfPx(px, ppu)

    fun rect(i: Int, off: Long): Edges {
        val x = px64(tileX(i), ppu)
        val y = px64(tileTop(i) - off, ppu)
        val side = px64(tileSide(i), ppu)
        return Edges(x, y, x + side, y + side)
    }

    private fun span(focus: Int, asc: Long): Pair<Long, Long>? {
        if (focus !in 0 until count) return null
        val t = q.tiles[focus]
        val side = fxDiv(fxInt(px64(tileSide(focus), ppu)), ppu)
        var top = tileTop(focus) - border
        val bottom = top + side + SIDES * border
        if (local(focus) < COLUMNS) top = if (t.section == firstSection()) 0L else sectionTop(t.section) - asc
        return top to bottom
    }

    fun visible(focus: Int, target: Long, asc: Long): Long {
        val (top, bottom) = span(focus, asc) ?: return target
        var y = target
        if (bottom > y + view) y = bottom - view
        if (top < y) y = top
        return y.coerceIn(0L, limit)
    }

    fun clamp(focus: Int, off: Long): Int {
        if (focus !in 0 until count) return focus
        val top = tileTop(focus)
        if (top >= off && top + tileSide(focus) <= off + view) return focus
        var best = -1
        var dist = 0L
        for (pass in 0 until PASSES) {
            if (best >= 0) break
            for (i in 0 until count) {
                val ty = tileTop(i)
                if ((pass == 0 && local(i) % COLUMNS != local(focus) % COLUMNS) || ty < off || ty + tileSide(i) > off + view) continue
                val d = abs(ty - top)
                if (best < 0 || d < dist) {
                    best = i
                    dist = d
                }
            }
        }
        return if (best >= 0) best else focus
    }

    fun headerAsc(): Long = fxDiv(fxInt(Metrics.ascent(rolePx(Roles.quickplayHeader, s, sp))), ppu)
}

class QpPan {
    var front by mutableStateOf(false)
        private set
    var shown by mutableStateOf(false)
        private set
    var still by mutableStateOf(true)
        private set
    var active = false
        private set
    private var leg = Leg(0f, 0f, 0f, 0f)
    private var t0 = 0L
    private var dragging = false
    private var dragAt = 0f
    private var x0 = 0f
    private var left = false
    var tiles: List<Edges> = emptyList()
        private set

    fun at(now: Long): Float = if (dragging) dragAt else leg.at((now - t0).toFloat())

    fun moved(now: Long): Boolean {
        if (!left && at(now) != if (active) 0f else 1f) left = true
        return left
    }

    fun snap(l: QpLayout, off: Long, waiting: Boolean) {
        val b = px(Tok.focus_border, l.s.stdPpu)
        tiles = if (waiting) emptyList() else (0 until l.count).map { l.rect(it, off) }.filter { it.y0 < l.s.heightPx && it.y1 + b > 0 }
    }

    fun moving(now: Long): Boolean = dragging || (now - t0) < leg.end

    fun run(front: Boolean, now: Long) {
        val t = at(now)
        val to = if (front) 1f else 0f
        dragging = false
        this.front = front
        shown = true
        still = false
        leg = Leg(t, to, 0f, Tok.motion_quickplay * abs(to - t), Curves.easeOut)
        t0 = now
    }

    fun show(front: Boolean, now: Long) {
        dragging = false
        active = front
        left = false
        this.front = front
        shown = true
        still = false
        leg = Leg(if (front) 0f else 1f, if (front) 1f else 0f, 0f, Tok.motion_quickplay, Curves.easeOut)
        t0 = now
    }

    fun begin(x: Float) {
        x0 = x
        active = !front
        left = false
        dragAt = if (front) 1f else 0f
        dragging = true
        shown = true
        still = false
    }

    fun drag(x: Float, stdPpu: Float) {
        if (!dragging) return
        val base = if (front) 1f else 0f
        dragAt = (base + (x - x0) / (Tok.motion_quickplayMenuX * stdPpu)).coerceIn(0f, 1f)
    }

    fun end(commit: Boolean, now: Long) {
        if (dragging) run(commit != front, now)
    }

    fun leave(now: Long) {
        if (dragging) run(false, now)
    }

    fun land(now: Long) {
        if (dragging) return
        leg = Leg(leg.to, leg.to, 0f, 0f)
        t0 = now
        settle(now)
    }

    fun settle(now: Long) {
        if (moving(now)) return
        still = true
        if (!front) shown = false
    }

    val dragged: Boolean get() = dragging
}

data class QpLook(val focus: Int, val framed: List<Boolean>, val revision: Int, val shown: Boolean = true, val lit: List<Float> = emptyList(), val ring: List<Float> = emptyList(), val key: Boolean = false)

class QuickplayState {
    var data by mutableStateOf(QpData.EMPTY)
    private val art = HashMap<ArtKey.Cover, ImageBitmap>()
    private val failed = HashSet<ArtKey.Cover>()
    var revision by mutableIntStateOf(0)
        private set
    var focus = 0
        set(v) {
            if (field == v) return
            field = v
            revision++
        }
    var keyFocus = false
        private set(v) {
            if (field == v) return
            field = v
            revision++
        }
    val press = PressSlots()
    val pan = QpPan()
    private var down = -1
    private var shownPlaying = false
    private var shownCount = 0
    var aim = 0L
        private set
    var aims by mutableIntStateOf(0)
        private set
    var aiming = false
        private set
    private var rested = 0L
    private var restedPx = -1
    private var asked = false
    private var waits by mutableStateOf(false)
    var waiting: Boolean
        get() = waits
        set(v) {
            if (waits == v) return
            waits = v
            revision++
        }
    private var said by mutableStateOf("")
    var status: String
        get() = said
        set(v) {
            if (said == v) return
            said = v
            revision++
        }

    fun art(ref: Any?, side: Int): ImageBitmap? = ref?.let { art[ArtKey.Cover(it, side)] }

    fun tried(ref: Any, side: Int): Boolean = ArtKey.Cover(ref, side).let { it in art || it in failed }

    fun putArt(ref: Any, side: Int, b: ImageBitmap?) {
        revision++
        if (b == null) failed += ArtKey.Cover(ref, side) else art[ArtKey.Cover(ref, side)] = b
    }

    fun boxed(ref: Any?, side: Int): Boolean = ref == null || ArtKey.Cover(ref, side) in failed

    fun reload() {
        revision++
        art.clear()
        failed.clear()
    }

    fun keep(l: QpLayout) {
        val want = (0 until l.count).mapNotNull { i -> l.tiles[i].item.art?.let { ArtKey.Cover(it, l.rect(i, 0L).w) } }.toSet()
        art.keys.retainAll(want)
        failed.retainAll(want)
        revision++
    }

    fun rest(pos: Long, px: Int) {
        revision++
        rested = pos
        restedPx = px
        asked = false
    }

    fun ask(pos: Long, px: Int) {
        rest(pos, px)
        asked = true
    }

    fun forget() {
        revision++
        restedPx = -1
        asked = false
    }

    fun pos(px: Int, over: Float, moving: Boolean, l: QpLayout): Long {
        if (asked && (moving || px == restedPx)) asked = false
        return if (asked || (!moving && over == 0f && px == restedPx)) rested else l.ofPx(px + over)
    }

    fun build(l: QpLayout, pos: Long): Long? {
        revision++
        val had = shownPlaying
        val prev = shownCount
        shownPlaying = l.playing
        shownCount = l.count
        if (had != l.playing && prev > 0) focus += if (l.playing) 1 else -1
        focus = focus.coerceIn(0, max(0, l.count - 1))
        down = -1
        aiming = false
        press.reset()
        if (had == l.playing || pos <= 0L) return null
        return (pos + if (l.playing) l.playSpan else -l.playSpan).coerceIn(0L, l.limit)
    }

    fun keyMove(delta: Int, l: QpLayout) {
        keyFocus = true
        focus = (focus + delta).coerceIn(0, max(0, l.count - 1))
    }

    fun keySelect() {
        keyFocus = true
    }

    fun dropKeyFocus() {
        keyFocus = false
    }

    fun down(i: Int, now: Long) {
        revision++
        keyFocus = false
        focus = i
        down = i
        press.down(i, now)
    }

    fun cancel(now: Long) {
        revision++
        down = -1
        press.cancel(now)
    }

    fun tap(i: Int, now: Long): Boolean {
        revision++
        if (down != i) return false
        down = -1
        press.tap(now)
        return true
    }

    fun into(l: QpLayout, pos: Long) {
        if (focus !in 0 until l.count) return
        val y = l.visible(focus, if (aiming) aim else pos, l.headerAsc())
        if (y == pos) return
        aim = y
        aiming = true
        aims++
    }

    fun stopAim() {
        aiming = false
    }

    fun landAim(): Long? {
        if (!aiming) return null
        aiming = false
        return aim
    }

    fun look(l: QpLayout, now: Long, hideFocus: Boolean = false): QpLook {
        press.revision
        return QpLook(focus, List(l.count) { press.framed(it, now) }, revision, !hideFocus, List(l.count) { press.look(it, now) }, List(l.count) { press.ring(it, now) }, keyFocus)
    }

    fun animating(now: Long): Boolean = pan.moving(now) || press.stale(now)

    fun wakeAt(now: Long): Long? = press.due(now)
}

class QpBoxes(private val make: (Int) -> BoxInk?) {
    private val made = HashMap<Int, BoxInk?>()

    operator fun get(i: Int): BoxInk? = made.getOrPut(i) { make(i) }
}

fun qpBoxes(m: TextMeasurer, fonts: FontFamily.Resolver, l: QpLayout, density: Density, covers: Covers?): QpBoxes {
    val s = l.s
    val sp = s.stdPpu
    val style = roleStyle(l.caption, s, density, Ink.white, ppu = sp)
    return QpBoxes { i ->
        val t = l.tiles[i]
        val lines = (if (t.section == QP_PLAYING) Tok.boxLinesPlaying else Tok.boxLines).toInt()
        boxInk(m, fonts, t.item.title, l.caption, style, l.rect(i, 0L), lines, sp, density, covers)
    }
}

fun DrawScope.quickplay(l: QpLayout, ink: QpInk, st: QuickplayState, boxes: QpBoxes, off: Long, look: QpLook, active: Boolean) {
    val s = l.s
    val sp = s.stdPpu
    val x = penPx(pen64(Tok.qpTextX, sp))
    val ha = Roles.quickplayHeader.alpha
    clipRect(0f, 0f, size.width, s.heightPx.toFloat()) {
        if (l.playing) word(if (l.paused) ink.paused else ink.now, x, l.pxOf(l.sectionTop(QP_PLAYING) - off).toFloat(), Ink.white, ha)
        for (k in 0 until SECTIONS) word(ink.headers[k], x, l.pxOf(l.sectionTop(k) - off).toFloat(), Ink.white, ha)
        if (l.q.counts[0] == 0 && !st.waiting) {
            ink.hint.forEachIndexed { i, w -> word(w, x, l.pxOf(l.hintTop(i) - off).toFloat(), Ink.white, Roles.qpHint.alpha) }
        }
        val b = px(Tok.focus_border, sp)
        for (i in 0 until if (st.waiting) 0 else l.count) {
            val r = l.rect(i, off)
            if (r.y0 >= s.heightPx || r.y1 + b <= 0) continue
            val t = l.tiles[i]
            val img = st.art(t.item.art, r.w)
            val border = if (active && look.shown && look.key && i == look.focus) b else 0
            val box = if (img == null && st.boxed(t.item.art, r.w)) boxes[i] else null
            artTile(r, img, box, l.caption, border, look.lit.getOrElse(i) { 0f }, look.ring.getOrElse(i) { 0f }, s)
        }
    }
}

private val layerPaint by lazy { Paint() }

fun DrawScope.layered(a: Float, block: DrawScope.() -> Unit) {
    if (a >= 1f) {
        block()
        return
    }
    layerPaint.alpha = a
    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), layerPaint)
    block()
    drawContext.canvas.restore()
}

fun qpAlpha(t: Float, peek: Float): Float = (Tok.qpPeekAlpha + (1 - Tok.qpPeekAlpha) * t) * peek

private fun ListTouch.held(): Boolean = moving || scroll.dragging

fun qpPos(touch: ListTouch, st: QuickplayState, l: QpLayout): Long = st.pos(touch.state.firstVisibleItemScrollOffset, 0f, touch.held(), l)

fun qpOff(touch: ListTouch, st: QuickplayState, l: QpLayout): Long = st.pos(touch.state.firstVisibleItemScrollOffset, touch.over, touch.held(), l)

fun DrawScope.qpPanel(l: QpLayout, ink: QpInk, st: QuickplayState, boxes: QpBoxes, t: Float, aq: Float, active: Boolean, look: QpLook, off: Long) {
    if (aq <= 0f) return
    val m = qpProjection(t, l.s.stdPpu).compose()
    layered(aq) {
        withTransform({ transform(m) }) {
            clipRect(0f, 0f, l.panelPx.toFloat(), l.s.heightPx.toFloat()) { quickplay(l, ink, st, boxes, off, look, active) }
        }
    }
}

fun qpWordRecords(rec: DrawRecord, l: QpLayout, ink: QpInk, off: Long, waiting: Boolean = false) {
    val x = penPx(pen64(Tok.qpTextX, l.s.stdPpu))
    val ha = Roles.quickplayHeader.alpha
    if (l.playing) rec.word("qp.header.playing", if (l.paused) ink.paused else ink.now, x, l.pxOf(l.sectionTop(QP_PLAYING) - off).toFloat(), ha)
    QP_HEADERS.forEachIndexed { i, h -> rec.word("qp.header.${h.lowercase()}", ink.headers[i], x, l.pxOf(l.sectionTop(i) - off).toFloat(), ha) }
    if (l.q.counts[0] == 0 && !waiting) ink.hint.forEachIndexed { i, w -> rec.word("qp.hint$i", w, x, l.pxOf(l.hintTop(i) - off).toFloat(), Roles.qpHint.alpha) }
}

fun qpPeekWords(rec: DrawRecord, l: QpLayout, ink: QpInk, off: Long, alpha: Float, waiting: Boolean = false) {
    val s = l.s
    val sp = s.stdPpu
    rec.move(Tok.qpPeekX * sp, Tok.qpPeekY * sp) {
        rec.zoom(Tok.qpPeekScale, 0f, 0f) {
            rec.fade(alpha) {
                rec.clip(0f, 0f, l.panelPx.toFloat(), s.heightPx.toFloat()) { qpWordRecords(rec, l, ink, off, waiting) }
            }
        }
    }
}

fun qpMenuLook(t: Float): HomeLook {
    val am = 1 + t * (Tok.motion_quickplayAlpha - 1)
    return HomeLook(am, -1, 0f, Roles.homeSubtitle.alpha * am, ring = am, mark = (1 - t) * am)
}

fun DrawScope.qpMenu(ink: HomeInk, menu: HomeMenu, geo: HomeGeo, t: Float, ringLit: Boolean, now: Long, homeOff: Float) {
    val m = t6(t, geo.s.stdPpu, geo.s.widthPx).compose()
    withTransform({ transform(m) }) {
        clipRect(0f, 0f, geo.s.widthPx.toFloat(), geo.s.heightPx.toFloat()) {
            homeMenu(ink, menu, geo, menu.restAt(now, ringLit), qpMenuLook(t), homeOff)
        }
    }
}

fun menuPeek(s: Scale, panelPx: Int): Edges {
    val m: M3 = t6(1f, s.stdPpu, s.widthPx)
    val pts = listOf(m.map(0f, 0f), m.map(s.widthPx.toFloat(), 0f), m.map(0f, s.heightPx.toFloat()), m.map(s.widthPx.toFloat(), s.heightPx.toFloat()))
    val x0 = max(panelPx, floor(pts.minOf { it.x }).toInt() - 1)
    val x1 = min(s.widthPx, ceil(pts.maxOf { it.x }).toInt() + 1)
    val y0 = max(0, floor(pts.minOf { it.y }).toInt() - 1)
    val y1 = min(s.heightPx, ceil(pts.maxOf { it.y }).toInt() + 1)
    return Edges(x0, y0, x1, y1)
}

fun DrawScope.qpFront(l: QpLayout, ink: QpInk, st: QuickplayState, boxes: QpBoxes, touch: ListTouch, look: QpLook) =
    quickplay(l, ink, st, boxes, qpOff(touch, st, l), look, true)

fun peekHit(s: Scale): Edges {
    val sp = s.stdPpu
    return Edges(0, px(Tok.qpPeekY, sp), px(Tok.qpPeekX + Tok.qpW * Tok.qpPeekScale, sp), px(Tok.qpPeekY + s.stdH * Tok.qpPeekScale, sp))
}

@Composable
fun QuickplayFront(
    l: QpLayout,
    ink: QpInk,
    st: QuickplayState,
    boxes: QpBoxes,
    a: Arbiter,
    touch: ListTouch,
    frame: () -> Long,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    poke: () -> Unit,
    onPlay: (QpTile) -> Unit,
    onBack: () -> Unit,
    hideFocus: Boolean = false,
    snap: Int = 0,
    onHold: (QpTile) -> Boolean = { false },
    frozen: Boolean = false,
) {
    val s = l.s
    val sp = s.stdPpu
    LaunchedEffect(st, st.aims) {
        if (st.aims == 0) return@LaunchedEffect
        val from = qpPos(touch, st, l)
        val to = st.aim
        val d = Tok.motion_scroll
        var t0 = -1L
        var e = 0f
        while (e < d && st.aiming) {
            withFrameMillis {
                if (t0 < 0) t0 = it
                e = (it - t0).toFloat()
                val want = from + ((to - from) * Curves.easeIn.at((e / d).coerceIn(0f, 1f)).toDouble()).roundToLong()
                if (st.aiming) touch.state.dispatchRawDelta(fxU(want - qpPos(touch, st, l)) * sp)
            }
        }
        if (st.aiming) st.rest(to, touch.state.firstVisibleItemScrollOffset)
        st.stopAim()
    }
    fun tap(i: Int) = Tap(
        onPress = {
            st.down(i, now())
            poke()
        },
        onCancel = {
            st.cancel(now())
            poke()
        },
        onTap = {
            if (st.tap(i, now())) {
                st.into(l, qpPos(touch, st, l))
                onPlay(l.tiles[i])
            }
            poke()
        },
        onLong = {
            if (onHold(l.tiles[i])) {
                st.cancel(now())
                poke()
                true
            } else {
                false
            }
        },
    )
    val back = Tap(onPress = {}, onCancel = {}, onTap = onBack, solo = true)
    val look = rememberLook(l, boxes, hideFocus, snap, covered = !visible || frozen) { st.look(l, frame(), hideFocus) }
    Box(Modifier.pxSize(s.widthPx, s.heightPx)) {
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) {
            if (visible) qpFront(l, ink, st, boxes, touch, look.value)
        }
        FuneList(a, touch, Modifier.pxSize(s.widthPx, s.heightPx), if (live) 0 else HIDDEN) {
            item {
                Box(Modifier.pxSize(s.widthPx, l.contentPx)) {
                    if (live && !st.waiting) for (i in 0 until l.count) Box(Modifier.pxAt(l.rect(i, 0L)).tapTarget(a, tap(i)))
                }
            }
        }
        if (live) Box(Modifier.pxAt(menuPeek(s, l.panelPx)).tapTarget(a, back))
    }
}
