package net.seilent.funekit.kit.stack

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Moves
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.ChromePart
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.motion.ContentForm
import net.seilent.funekit.kit.motion.MorphKind
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.proj.M3
import net.seilent.funekit.kit.proj.camTurn
import net.seilent.funekit.kit.proj.edgeAngle
import net.seilent.funekit.kit.proj.facing
import net.seilent.funekit.kit.proj.inverse
import net.seilent.funekit.kit.proj.turn
import net.seilent.funekit.kit.quickplay.menuPeek
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.sprite
import net.seilent.funekit.kit.statusRow
import net.seilent.funekit.kit.wordBottom
import net.seilent.funekit.px
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val HALF = 2f
private const val MID = 2
private const val SLIDE_X = 200f
private const val SLIDE_W = 480f
private const val TAIL_STEP = 4L
private const val REACH_STEP = 16L
private const val REACH_PAD = 2

class Bands(val first: Int, val y: IntArray, val dy: IntArray, val dx: Float)

class Place(val s: Float, val dx: Float, val dy: Float, val xa: Float, val ya: Float)

sealed interface Content {
    data object Rest : Content

    class Zoom(val s: Float, val origin: Offset) : Content

    class Rows(val sy: Float, val ya: Float, val dx: Float) : Content

    class Frozen(val bands: Bands) : Content

    class Lift(val bands: Bands?, val at: Place, val cut: Float?, val tail: Int = 0, val barX: Float = Float.MAX_VALUE) : Content
}

class WordSide(val text: String, val s: Float, val look: Float, val x: Float, val y: Float, val pivot: Int, val header: Boolean, val word: Word)

class TileHide(val album: Long, val ring: Boolean)

class HomeMove(val items: Float, val sel: Int, val selAlpha: Float, val sub: Float, val off: Float, val watermark: Boolean = true)

interface HomePaint {
    val quickplay: Boolean

    fun peekAlpha(at: Long): Float

    fun baseline(item: Int): Float

    fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?)

    fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?)

    fun frontPane(): Edges? = null

    fun DrawScope.front(now: Long) {}

    fun DrawScope.frontPeek(alpha: Float, now: Long) {}

    fun itemWord(item: Int): Word? = null
}

interface PagePaint {
    val chrome: Chrome?
    val header: Word?
    val item: Int
    val grounded: Boolean
    val kind: PageKind
    val top: Int
    val bottom: Int
    val count: Int
    val scroll: Int get() = 0
    val above: Int get() = 0

    fun rowY(i: Int): Int

    fun DrawScope.ground(leave: Float, arrive: Float)

    fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String)

    fun wordSide(pivot: Boolean): WordSide? = null

    fun rowSide(row: Int): WordSide? = null

    fun hideRow(row: Int) {}

    val nowPlaying: Boolean get() = false
    val queue: Boolean get() = false
    val albumCol: Boolean get() = false

    fun tileRect(album: Long): Edges? = null

    fun DrawScope.tile(album: Long, side: Float, cx: Float, cy: Float, alpha: Float, rec: DrawRecord?) {
        val h = side / HALF
        drawRect(Ink.tile, Offset(cx - h, cy - h), Size(side, side), alpha = alpha)
    }

    fun thumbRect(row: Int): Edges? = null

    fun hide(h: TileHide?) {}

    fun pivotRow(): Edges? = null

    fun DrawScope.pivotRowInk(rec: DrawRecord?) {}

    fun thumbFade(at: Long): Float = 0f

    fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {}

    fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) = restRows(this@PagePaint, now, rec, name)

    fun DrawScope.content(c: Content, now: Long, rec: DrawRecord?, name: String, fade: Float = 0f) = contentRows(this@PagePaint, c, now, rec, name, fade)

    fun DrawScope.bands(b: Bands, now: Long, rec: DrawRecord?, name: String) = bandRows(this@PagePaint, b, now, rec, name)

    fun DrawScope.npPart(part: Part, now: Long, rec: DrawRecord?) {
        if (part == Part.NpIn) content(Content.Rest, now, rec, "arrive")
    }

    fun npRing(): Edges? = null
}

fun PagePaint.rowTop(i: Int): Int = rowY(i) - scroll

fun PagePaint.clipTopPx(): Int = top - above

fun PagePaint.first(): Int {
    val t = clipTopPx()
    var lo = 0
    var hi = count
    while (lo < hi) {
        val m = (lo + hi) ushr 1
        if (rowTop(m + 1) > t) hi = m else lo = m + 1
    }
    return if (lo < count) lo else -1
}

fun PagePaint.last(): Int {
    val f = first()
    if (f < 0 || rowTop(f) >= bottom) return -1
    var lo = f
    var hi = count - 1
    while (lo < hi) {
        val m = (lo + hi + 1) ushr 1
        if (rowTop(m) < bottom) lo = m else hi = m - 1
    }
    return lo
}

fun DrawScope.restRows(p: PagePaint, now: Long, rec: DrawRecord?, name: String) {
    clipped(rec, 0f, p.clipTopPx().toFloat(), size.width, p.bottom.toFloat()) {
        val f = p.first()
        if (f >= 0) for (i in f..p.last()) with(p) { row(i, rowTop(i), now, rec, name) }
    }
}

fun DrawScope.contentRows(p: PagePaint, c: Content, now: Long, rec: DrawRecord?, name: String, fade: Float = 0f) {
    fun DrawScope.bar() {
        if (fade > 0f) with(p) { thumb(fade, rec, name) }
    }
    when (c) {
        Content.Rest -> {
            with(p) { rest(now, rec, name) }
            bar()
        }
        is Content.Zoom -> zoomed(rec, c.s, c.origin.x, c.origin.y) {
            with(p) { rest(now, rec, name) }
            bar()
        }
        is Content.Rows -> p.bandsOf(c.sy, c.ya, c.dx)?.let { with(p) { bands(it, now, rec, name) } } ?: with(p) { rest(now, rec, name) }
        is Content.Frozen -> with(p) { bands(c.bands, now, rec, name) }
        is Content.Lift -> cut(rec, c.cut) {
            val b = c.bands
            if (b == null) {
                val y = p.restBand()
                placed(rec, c.at) {
                    clipped(rec, 0f, y[0].toFloat(), size.width, y[1].toFloat()) {
                        with(p) { rest(now, rec, name) }
                        bar()
                    }
                }
            } else {
                val y0 = b.y.first().toFloat()
                val y1 = b.y.last().toFloat()
                val bx = min(c.barX, size.width)
                placed(rec, c.at) {
                    clipped(rec, 0f, y0, bx, y1) {
                        if (b.first < 0) {
                            with(p) { rest(now, rec, name) }
                        } else {
                            with(p) { bands(b, now, rec, name) }
                        }
                    }
                }
                if (c.tail > 0 && c.at.s != 1f) {
                    clipped(rec, 0f, 0f, size.width, y1) { placed(rec, c.at) { clipped(rec, 0f, y1, bx, y1 + c.tail) { tailRows(p, y1, c.tail, now, rec, name) } } }
                }
                if (bx < size.width) clipped(rec, bx, y0, size.width, y1) { bar() }
            }
        }
    }
}

private fun DrawScope.cut(rec: DrawRecord?, y: Float?, block: DrawScope.() -> Unit) = if (y == null) block() else clipped(rec, 0f, 0f, size.width, y, block)

private fun DrawScope.placed(rec: DrawRecord?, at: Place, block: DrawScope.() -> Unit) {
    if (at.s == 1f) {
        shifted(rec, Math.round(at.dx).toFloat(), Math.round(at.dy).toFloat(), block)
    } else {
        shifted(rec, at.dx, at.dy) { zoomed(rec, at.s, at.xa, at.ya, block) }
    }
}

private fun DrawScope.tailRows(p: PagePaint, hb: Float, tail: Int, now: Long, rec: DrawRecord?, name: String) {
    val l = p.last()
    if (l < 0) return
    var i = l
    while (i < p.count && p.rowTop(i) < hb + tail) {
        if (p.rowTop(i + 1) > hb) with(p) { row(i, rowTop(i), now, rec, name) }
        i++
    }
}

fun DrawScope.bandRows(p: PagePaint, b: Bands, now: Long, rec: DrawRecord?, name: String) {
    val n = b.dy.size
    for (k in 0 until n) {
        val y0 = (b.y[k] + b.dy[k]).toFloat()
        val y1 = if (k + 1 < n) (b.y[k + 1] + b.dy[k + 1]).toFloat() else min((b.y[n] + b.dy[k]).toFloat(), size.height)
        clipped(rec, 0f, y0, size.width, y1) {
            shifted(rec, b.dx, b.dy[k].toFloat()) {
                val i = b.first + k
                with(p) { row(i, rowTop(i), now, rec, name) }
            }
        }
    }
}

fun PagePaint.bandTops(): IntArray? {
    val f = first()
    val l = last()
    if (f < 0 || l < f) return null
    val y = ArrayList<Int>()
    y += clipTopPx()
    for (r in f + 1..l) {
        val t = rowTop(r)
        if (t > y.last()) y += t
    }
    y += max(bottom, y.last() + 1)
    return y.toIntArray()
}

fun PagePaint.bandsOf(sy: Float, ya: Float, dx: Float): Bands? {
    val y = bandTops() ?: return null
    return Bands(first(), y, IntArray(y.size - 1) { Math.round((1 - sy) * (ya - y[it])) }, dx)
}

fun PagePaint.bandsAt(): Bands? {
    val y = bandTops() ?: return null
    return Bands(first(), y, IntArray(y.size - 1), 0f)
}

private fun PagePaint.restBand(): IntArray = intArrayOf(clipTopPx(), max(bottom, clipTopPx() + 1))

internal fun foldWord(s: String): String = String(CharArray(s.length) { val c = s[it]; if (c in 'A'..'Z') c + ('a' - 'A') else c })

fun PagePaint.bandsOrRest(): Bands = bandsAt() ?: Bands(-1, restBand(), IntArray(1), 0f)

class HomeSide(val scale: Scale, val paint: HomePaint, val off: Float)

class PageSide(val scale: Scale, val paint: PagePaint)

class MoveStatus(val home: StatusInk, val page: StatusInk, val accent: Color, val accentOff: Boolean)

class Morph(val a: WordSide, val b: WordSide, val push: Boolean) {
    val word: WordSide get() = if (push) b else a
    val other: WordSide get() = if (push) a else b
}

class LeaveWord(val word: Word, val pen: Float, val base: Float, val alpha: Float)

class Fade(val word: Word, val s: Float, val x: Float, val y: Float, val a: Float)

class MoveCtx(
    val ends: MoveEnds,
    val kind: MoveKind,
    val home: HomeSide?,
    val from: PageSide?,
    val to: PageSide?,
    val rows: Boolean,
    val ya: Float,
    val word: Word?,
    val wordLook: Float,
    val item: Int,
    val zoom: Word?,
    val zoomPen: Float,
    val zoomBase: Float,
    val zoomAlpha: Float,
    val morph: Morph?,
    val morphLook: Float,
    val leaveBands: Bands? = null,
    val leaveWord: LeaveWord? = null,
    val fades: List<Fade> = emptyList(),
    val fadeAt: Long = 0L,
    val xa: Float = 0f,
    val cut: Float? = null,
    val leaveAt: Place? = null,
    val leaveCut: Float? = null,
    val leaveAnchor: Float = 0f,
    val thumbAt: Long = 0L,
    val leaveThumb: Float = 0f,
    val arriveThumb: Float = 0f,
    val leaveDx: Float = 0f,
    val face: Word? = null,
    val album: Long = -1L,
    val tileSide: PageSide? = null,
    val barX: Float = Float.MAX_VALUE,
    val leaveBarX: Float = Float.MAX_VALUE,
    val pivotRow: Edges? = null,
    val hides: List<PageSide> = emptyList(),
    val turnEye: TurnEye? = null,
    val rowHide: PageSide? = null,
    val hideRow: Int = -1,
    val rowBottom: Int = -1,
) {
    val scale: Scale? get() = (to ?: from)?.scale ?: home?.scale

    val lift: Boolean get() = rows

    var tail: Int = -1
    var leaveTail: Int = -1
    var pivotReach: Int = -1

    fun carry(old: MoveCtx, v: PageMove, at: Long): MoveCtx {
        fun cur(p: Part, q: Prop) = v.at(p, q, at)
        val ca = cur(Part.Content, Prop.A)
        val oldTo = old.to?.paint
        val cs = cur(Part.Content, Prop.S)
        val cx = Math.round(cur(Part.Content, Prop.X)).toFloat()
        val slid = old.kind == MoveKind.T2w && oldTo != null && ca > 0f
        val on = old.rows && oldTo != null && ca > 0f
        val bands = if (slid) leaveBands else if (!on || oldTo == null) null else oldTo.bandsOrRest()
        val at0 = leaveAt
        val held = if (slid && at0 != null) Place(1f, cx, 0f, at0.xa, at0.ya) else if (on) Place(cs, cx, 0f, old.xa, old.ya) else null
        val sc = scale
        val anchor = when {
            held == null || slid -> leaveAnchor
            oldTo != null && sc != null && bands != null -> PageMove.popAnchor(kind, oldTo.top, sc.heightPx, sc.stdPpu).toFloat()
            else -> 0f
        }
        val zw = old.zoom
        val zoomed = zw != null && cur(Part.Zoom, Prop.A) > 0f && cur(Part.Header, Prop.A) <= 0f
        val leave = if (zoomed && morph == null && zw != null) LeaveWord(zw, old.zoomPen, old.zoomBase, old.zoomAlpha) else leaveWord
        val om = old.morph
        val wasMorph = om != null && at < v.t0 + v.wordEnd
        val nm = morph
        val back = wasMorph && om != null && nm != null && nm.word.text == om.word.text && nm.other.text == om.other.text
        val fades = if (om != null && wasMorph && !back) {
            listOf(Fade(om.word.word, cur(Part.Word, Prop.S), cur(Part.Word, Prop.X), cur(Part.Word, Prop.Y), old.morphLook * cur(Part.Word, Prop.A)))
        } else {
            emptyList()
        }
        val c = MoveCtx(
            ends.copy(morphBack = back), kind, home, from, to, rows, ya, word, wordLook, item, zoom, zoomPen, zoomBase, zoomAlpha, morph, morphLook,
            bands, leave, fades, at, xa, cut, held, if (held != null) old.cut else leaveCut, anchor,
            old.thumbAt, old.arriveThumb, old.leaveThumb, if (slid) cx else 0f,
            face, album, tileSide, barX, if (bands != null) old.barX else leaveBarX, pivotRow, hides,
            rowHide = rowHide, hideRow = hideRow, rowBottom = rowBottom,
        )
        if (bands != null) c.leaveTail = max(old.tail, 0)
        return c
    }

    companion object {
        val None = MoveCtx(MoveEnds(), MoveKind.None, null, null, null, false, 0f, null, 0f, -1, null, 0f, 0f, 0f, null, 0f)
    }
}

private fun formOf(kind: PageKind, f: MoveForms): ContentForm = when (kind) {
    PageKind.Grid -> f.content
    PageKind.Hub -> f.hubRows
    PageKind.Plain -> f.pageRows
}

private fun headerBase(s: Scale): Float = px(Tok.page_headerBaseline, s.stdPpu).toFloat()


private fun stdPx(u: Float, s: Scale): Float = if (s.large) Math.round(u * s.stdPpu).toFloat() else u * s.stdPpu

private fun whole(u: Float, ppu: Float): Float = px(u, ppu).toFloat()

fun npEnds(k: MoveKind, np: PageSide?, home: HomeSide?, form: NpForm, toHome: Boolean = false): MoveEnds {
    val p = (np?.scale ?: home?.scale)?.stdPpu ?: return MoveEnds(npForm = form)
    return MoveEnds(npForm = form, npCut = Tok.motion_npIn != NP_TURN, npHome = k == MoveKind.T8 && toHome, pxU = p)
}

private const val NP_TURN = "turn"

private val NP_PARTS = listOf(
    Part.NpStatus, Part.NpArtist, Part.NpAlbum, Part.NpIn, Part.NpBar, Part.NpTimeL, Part.NpTimeR, Part.NpTitle, Part.NpNext,
    Part.NpShuffle, Part.NpRings,
)

private fun underBands(to: PageSide, p: Float): List<Pair<Edges, Part>> {
    val s = to.scale
    val h = s.heightPx
    val hy = min(px(s.board.pivotTop, p), h)
    val pp = to.paint
    val y = if (pp.kind == PageKind.Grid) null else pp.bandsOrRest().y
    val n = (y?.size ?: 1) - 1
    val top = if (n > 0) y!![0].coerceIn(hy, h) else h
    val out = ArrayList<Pair<Edges, Part>>()
    fun band(y0: Int, y1: Int, part: Part) {
        if (y1 > y0) out += Edges(0, y0, s.widthPx, y1) to part
    }
    band(0, hy, Part.NpUHeader)
    band(hy, top, Part.NpUnder)
    val rows = listOf(Part.NpURow0, Part.NpURow1, Part.NpURow2, Part.NpURow3, Part.NpURows)
    for (i in 0 until n) {
        val y0 = max(y!![i], top)
        val y1 = if (i + 1 == n) h else max(y[i + 1], y0)
        band(y0, y1, rows[min(i, rows.size - 1)])
    }
    return out
}

class NpEye(val d: Float, val hinge: Float, val eye: Float)

class TurnEye(val out: NpEye, val inD: Float, val inEye: Float, val cx: Float)

internal const val NP_OUT = "np.out"
internal const val NP_PEEK = "np.peek"
internal const val NP_PANE = "np.pane"
internal const val NP_HOME = "np.home"
internal const val NP_UNDER = "np.under."
internal const val NP_LEAVE = "np.leave"
internal const val NP_PART = "np.part."
internal const val NP_RING = "np.ring"
internal const val TURN_LEAVE = "turn.leave"
internal const val TURN_HOME = "turn.home"

internal fun npCam(r: Float, slide: Float, hinge: Float, d: Float, eye: Float, cx: Float): M3? {
    if (!facing(r, hinge, d, cx - slide)) return null
    return M3.translate(slide, 0f) * camTurn(r, hinge, eye, d, cx - slide)
}

internal class CarryAt(
    val kind: MoveKind,
    val toHome: Boolean,
    val rows: Boolean,
    val bar: Float,
    val contentTop: Float = 0f,
    val bandTop: Float = 0f,
    val pivotTop: Float = 0f,
    val pivotBottom: Float = 0f,
    val xa: Float = 0f,
    val ya: Float = 0f,
    val ox: Float = 0f,
    val oy: Float = 0f,
    val ca: Float = 0f,
    val cs: Float = 1f,
    val cx: Float = 0f,
    val pa: Float = 0f,
    val px: Float = 0f,
    val py: Float = 0f,
    val under: M3? = null,
)

internal fun carryOf(x: Float, y: Float, c: CarryAt): M3? {
    if (y >= c.bar || c.kind == MoveKind.T7) return null
    if (c.kind == MoveKind.T8) {
        val inv = c.under?.inverse() ?: return null
        val w = inv.g * x + inv.h * y + inv.i
        if (!(w > 0f)) return null
        val u = (inv.a * x + inv.b * y + inv.c) / w
        val v = (inv.d * x + inv.e * y + inv.f) / w
        return M3.translate(u - x, v - y)
    }
    if (c.toHome) return null
    val content = c.ca > 0f && y >= c.contentTop
    val pivots = c.pa > 0f && y >= c.pivotTop && y < c.pivotBottom
    return when {
        content && c.rows -> if (y >= c.ya + c.cs * (c.bandTop - c.ya)) carryPart(max(c.cs, 1f), c.xa, c.ya, Math.round(c.cx).toFloat(), 0f) else null
        content -> carryPart(max(c.cs, 1f), c.ox, c.oy, 0f, 0f)
        pivots -> M3.translate(-Math.round(c.px).toFloat(), -Math.round(c.py).toFloat())
        else -> null
    }
}

private fun carryPart(s: Float, ox: Float, oy: Float, dx: Float, dy: Float): M3 = M3(1f / s, 0f, ox - (dx + ox) / s, 0f, 1f / s, oy - (dy + oy) / s, 0f, 0f, 1f)

internal fun npFrontUnder(l: Float, r: Float, hinge: Float, edge: Float, eye: Float, d: Float): M3 =
    M3.translate(Math.round(l).toFloat(), 0f) * turn(edgeAngle(edge, edge + (r - l), hinge, d), hinge, eye, d)

internal fun npPeekRect(menu: Edges, pane: Edges, src: Edges): Edges = Edges(max(max(menu.x0, pane.x1), src.x1), menu.y0, menu.x1, menu.y1)

fun npEye(stdPpu: Float, heightPx: Int, hinge: Float, eye: Float): NpEye =
    NpEye(max(1f, whole(Tok.motion_perspective, stdPpu)), whole(hinge, stdPpu), Math.round(eye * heightPx).toFloat())

private fun quad(deg: Float, e: NpEye, x: Float, y: Float): M3 = M3.translate(Math.round(x).toFloat(), Math.round(y).toFloat()) * turn(deg, e.hinge, e.eye, e.d)

private fun barOf(s: Scale): Float = px(s.w - Tok.scrollbar_width, s.ppu).coerceIn(0, s.widthPx).toFloat()

private fun sameHeader(a: PageSide, b: PageSide): Boolean {
    val x = a.paint.header
    val y = b.paint.header
    return x != null && y != null && x.text == y.text
}

private class AlbumWord(val e: MoveEnds, val word: Word?, val look: Float)

private fun albumWord(e: MoveEnds, hub: PageSide, album: PageSide, grow: Boolean, pivot: Int): AlbumWord {
    val hs = hub.scale
    val ss = album.scale
    val ch = hub.paint.chrome
    val px0 = ch?.pivotX(pivot) ?: 0f
    val py0 = px(hs.board.page_pivotBaseline, hs.ppu).toFloat()
    val sp = rolePx(Roles.pivotActive, hs, hs.ppu).toFloat() / max(1, rolePx(Roles.albumHeader, ss, ss.stdPpu))
    val ax = album.paint.chrome?.headerRestX() ?: Tok.page_albumHeaderX * ss.stdPpu
    val ay = headerBase(ss)
    val look = album.paint.chrome?.headerAlpha ?: Roles.albumHeader.alpha
    return AlbumWord(
        e.copy(
            word = true,
            wordS0 = if (grow) sp else 1f, wordS1 = if (grow) 1f else sp,
            wordX0 = if (grow) px0 else ax, wordX1 = if (grow) ax else px0,
            wordY0 = if (grow) py0 else ay, wordY1 = if (grow) ay else py0,
        ),
        album.paint.header, look,
    )
}

private fun tileEnds(e: MoveEnds, a: Edges, b: Edges): MoveEnds = e.copy(
    tileS0 = (a.x1 - a.x0).toFloat(), tileX0 = (a.x0 + a.x1) / HALF, tileY0 = (a.y0 + a.y1) / HALF,
    tileS1 = (b.x1 - b.x0).toFloat(), tileX1 = (b.x0 + b.x1) / HALF, tileY1 = (b.y0 + b.y1) / HALF,
)

fun moveCtx(t: Turn<*>, home: HomeSide?, from: PageSide?, to: PageSide?, forms: MoveForms, npForm: NpForm = NpForm.Page): MoveCtx {
    val k = t.kind
    if (k == MoveKind.T7 || k == MoveKind.T8) return MoveCtx(npEnds(k, if (k == MoveKind.T7) to else from, home, npForm, k == MoveKind.T8 && to == null), k, home, from, to, false, 0f, null, 0f, -1, null, 0f, 0f, 0f, null, 0f)
    val side = to ?: from ?: return MoveCtx.None
    val s = side.scale
    val leaveG = from?.paint?.grounded == true
    val arriveG = to?.paint?.grounded == true
    val toChrome = to?.paint?.chrome
    var e = MoveEnds(
        pivotX = Tok.motion_pivotInX * s.ppu,
        pivotY = Tok.motion_pivotInY * s.ppu,
        grounds = leaveG != arriveG,
        pivots = (side.paint.chrome?.count ?: 0) > 0,
        zoom = to != null && to.paint.header != null,
        rowsDx = Tok.motion_rowsDx * s.stdPpu,
        sub = Roles.homeSubtitle.alpha,
        npQueue = from != null && to != null && from.paint.nowPlaying && to.paint.queue,
        pxU = s.ppu,
        sw = s.widthPx.toFloat(),
        sh = s.heightPx.toFloat(),
        zoomX = Tok.motion_headerZoomX * s.ppu,
        zoomY = Tok.motion_headerZoomY * s.ppu,
        zoomBase = to?.let { headerBase(it.scale) } ?: 0f,
    )
    val lit = from?.paint?.chrome?.lit(Chrome.HEADER, t.at) == true
    var word: Word? = null
    var face: Word? = null
    var look = 0f
    var item = -1
    val p = if (k == MoveKind.T2) to else if (k == MoveKind.T4h) from else null
    if (p != null && home != null) {
        val t2 = k == MoveKind.T2
        val hs = home.scale
        val c = p.paint.chrome
        item = if (c != null && p.paint.header != null) p.paint.item else -1
        if (item >= 0 && c != null) {
            val ps = p.scale
            val hw = p.paint.header
            face = home.paint.itemWord(item)
            val fw = face?.let { it.inkRight - it.inkLeft } ?: 0f
            val ww = hw?.let { it.inkRight - it.inkLeft } ?: 0f
            val sm = if (fw > 0f && ww > 0f) fw / ww else rolePx(Roles.homeItem, hs, hs.stdPpu).toFloat() / rolePx(Roles.pageHeader, ps, ps.stdPpu)
            val hx = c.headerRestX()
            val hy = headerBase(ps)
            val ix = if (t2) penPx(pen64(Tok.homeTextX, hs.stdPpu)) else Tok.homeTextX * hs.stdPpu
            val iy = Math.round((home.paint.baseline(item) - home.off) * hs.stdPpu).toFloat()
            look = if (lit && !t2) Tok.alpha_full else c.headerAlpha
            e = e.copy(
                word = true,
                wordS0 = if (t2) sm else 1f, wordS1 = if (t2) 1f else sm,
                wordX0 = if (t2) ix else hx, wordX1 = if (t2) hx else ix,
                wordY0 = if (t2) iy else hy, wordY1 = if (t2) hy else iy,
                look = if (t2) e.look else look,
            )
            word = hw
        }
        if (t2) e = e.copy(zoom = false)
    }
    if (home != null && (k == MoveKind.T2 || k == MoveKind.T2n || k == MoveKind.T2w)) e = e.copy(peek0 = home.paint.peekAlpha(t.at))
    if (k == MoveKind.T2w) e = e.copy(zoom = false, slideX = SLIDE_X / SLIDE_W * s.widthPx)
    if (home != null && (k == MoveKind.T4h || k == MoveKind.T4n)) e = e.copy(peek1 = if (home.paint.quickplay) Tok.qpPeekAlpha else 0f)
    var turnEye: TurnEye? = null
    if (k == MoveKind.T4n && t.morph && !t.retarget && to == null && from != null && from.paint.item < 0 && home != null) {
        val hs = home.scale
        val hp = hs.stdPpu
        e = e.copy(turn = true, homeU = hp)
        turnEye = TurnEye(
            npEye(hp, hs.heightPx, Tok.motion_t8OutHinge, Tok.motion_t8OutEye),
            max(1f, whole(Tok.motion_t7Persp, hp)),
            Math.round(Moves.kT4nInEye * hs.heightPx).toFloat(),
            Math.round(Tok.motion_t7EyeX * hs.widthPx).toFloat(),
        )
    }
    var album = t.album
    var tileSide: PageSide? = null
    var colHold = false
    if (from != null && to != null && (k == MoveKind.T3m || k == MoveKind.T3c || k == MoveKind.T4m)) {
        val pop = k == MoveKind.T4m
        val hub = if (pop) to else from
        val aw = albumWord(e, hub, if (pop) from else to, !pop, hub.paint.chrome?.active ?: 0)
        e = aw.e
        word = aw.word
        look = aw.look
        if (pop) e = e.copy(colBack = from.paint.albumCol)
        if (!pop) {
            e = e.copy(zoom = false)
            val a = from.paint.tileRect(album)
            val b = to.paint.tileRect(album)
            if (k == MoveKind.T3m && a != null && b != null) {
                e = tileEnds(e, a, b)
                tileSide = to
            }
        }
    }
    if (from != null && to != null && (k == MoveKind.T3a || k == MoveKind.T4a)) {
        val a = from.paint.tileRect(album)
        val b = to.paint.tileRect(album)
        if (a != null && b != null) {
            e = tileEnds(e, a, b)
            tileSide = to
        }
        colHold = sameHeader(from, to)
        e = e.copy(colHold = colHold, tileBack = k == MoveKind.T4a && a != null && b != null)
        if (k == MoveKind.T3a || colHold) e = e.copy(zoom = false)
        if (!colHold) {
            val aw = if (k == MoveKind.T3a) albumWord(e, from, to, true, 0) else albumWord(e, to, from, false, 0)
            e = aw.e
            word = aw.word
            look = aw.look
        }
    }
    var morph: Morph? = null
    var morphLook = 0f
    var leaveWord: LeaveWord? = null
    var pivotPush = false
    var rowHide: PageSide? = null
    var hideRow = -1
    var rowBottom = -1
    if ((k == MoveKind.T3 || k == MoveKind.T4) && forms.wordMorph && from != null && to != null) {
        val push = k == MoveKind.T3
        val rp = if (push) from else to
        val hp = if (push) to else from
        val rs = if (t.row >= 0) rp.paint.rowSide(t.row) else null
        val hs = if (rs != null) hp.paint.wordSide(false) else null
        val byRow = rs != null && hs != null && hs.header && foldWord(rs.text) == foldWord(hs.text)
        val a = if (byRow) (if (push) rs else hs) else from.paint.wordSide(push)
        val b = if (byRow) (if (push) hs else rs) else to.paint.wordSide(!push)
        if (a != null && b != null && (byRow || !(a.text == b.text && a.header == b.header)) && (a.header || b.header)) {
            val m = Morph(a, b, push)
            morph = m
            morphLook = if (!push && !byRow && lit && a.header) Tok.alpha_full else m.word.look
            if (byRow && rs != null) {
                rowHide = rp
                hideRow = t.row
                rowBottom = Math.round(rs.y) + max(1, Metrics.descent(Math.round(rs.s * rolePx(Roles.pageHeader, hp.scale, hp.scale.stdPpu))))
            }
            e = e.copy(
                morph = true, morphKind = if (byRow) MorphKind.Row else MorphKind.Pivot,
                morphS0 = a.s, morphS1 = b.s,
                morphX0 = a.x, morphX1 = b.x, morphY0 = a.y, morphY1 = b.y,
                morphHeaderOut = a.header, morphHeaderIn = b.header,
                zoom = e.zoom && !(push && b.header),
            )
            if (push) {
                pivotPush = !byRow
                val fc = from.paint.chrome
                val hw = from.paint.header
                if (fc != null && hw != null && (byRow || hw.text != m.word.text)) {
                    val base = headerBase(from.scale)
                    leaveWord = LeaveWord(hw, fc.headerRestX(), base, if (lit) Tok.alpha_full else fc.headerAlpha)
                    e = e.copy(lhead = true, lheadBase = base)
                }
                if (!byRow) {
                    val fp = from.paint
                    var tapY = s.heightPx / HALF
                    if (t.row >= 0 && t.row < fp.count) tapY = fp.rowTop(t.row) + (fp.rowY(t.row + 1) - fp.rowY(t.row)) / HALF
                    e = e.copy(tapX = s.widthPx / HALF, tapY = tapY)
                    val th = if (t.row >= 0) fp.thumbRect(t.row) else null
                    val tr = if (th != null) to.paint.tileRect(album) else null
                    if (th != null && tr != null) {
                        e = tileEnds(e, th, tr).copy(thumb = true)
                        tileSide = to
                    }
                }
            }
        }
    }
    val ts = to?.scale
    val rows = to != null && to.paint.kind != PageKind.Grid && formOf(to.paint.kind, forms) != ContentForm.Rest &&
        (k == MoveKind.T2 || k == MoveKind.T2n || k == MoveKind.T3) && !pivotPush
    val ya = if (ts == null) 0f else ts.heightPx - stdPx(Tok.motion_rowsYa, ts)
    val xa = stdPx(Tok.hubEdge, s)
    if (ts != null) e = e.copy(rowsDx = stdPx(Tok.motion_rowsDx, ts))
    val cut = if (rows && ts != null && ts.navOn) px(ts.stdContentBottom, ts.stdPpu).toFloat() else null
    val zoom = if (e.zoom && toChrome != null) toChrome.header else null
    val bandsOut = ((k == MoveKind.T4h || k == MoveKind.T4n) && to == null) || (k == MoveKind.T4 && e.morph && e.morphKind == MorphKind.Row)
    val leaving = if (bandsOut && from != null && from.paint.kind != PageKind.Grid) from else null
    val leaveBands = leaving?.paint?.bandsOrRest()
    val anchor = if (leaving == null) 0f else PageMove.popAnchor(k, leaving.paint.top, leaving.scale.heightPx, leaving.scale.stdPpu).toFloat()
    val leaveAt = if (leaving != null) Place(1f, 0f, 0f, xa, anchor) else null
    val leaveCut = if (leaving != null && leaving.scale.navOn) px(leaving.scale.stdContentBottom, leaving.scale.stdPpu).toFloat() else null
    if (leaving != null) e = e.copy(leaveRows = true)
    if (album < 0) album = -1L
    val hides = if (tileSide != null) listOfNotNull(from, to) else emptyList()
    return MoveCtx(
        e, k, home, from, to, rows, ya, word, look, item,
        zoom, toChrome?.headerRestX() ?: 0f, to?.let { headerBase(it.scale) } ?: 0f, toChrome?.headerAlpha ?: 0f,
        morph, morphLook, leaveBands = leaveBands, leaveWord = leaveWord, xa = xa, cut = cut, leaveAt = leaveAt, leaveCut = leaveCut, leaveAnchor = anchor,
        thumbAt = t.at, leaveThumb = from?.paint?.thumbFade(t.at) ?: 0f, arriveThumb = to?.paint?.thumbFade(t.at) ?: 0f,
        face = face, album = album, tileSide = tileSide, barX = ts?.let { barOf(it) } ?: Float.MAX_VALUE,
        leaveBarX = leaving?.let { barOf(it.scale) } ?: Float.MAX_VALUE, pivotRow = to?.paint?.pivotRow(), hides = hides, turnEye = turnEye,
        rowHide = rowHide, hideRow = hideRow, rowBottom = rowBottom,
    )
}

class MovePaint(private val move: PageMove) {
    var ctx: MoveCtx = MoveCtx.None
        private set
    var npForm: NpForm = NpForm.Page
    private var forms = MoveForms()
    private val layerPaint = Paint()

    fun ends(t: Turn<*>, home: HomeSide?, from: PageSide?, to: PageSide?, forms: MoveForms): MoveEnds {
        this.forms = forms
        val c = moveCtx(t, home, from, to, forms, npForm)
        for (h in ctx.hides) h.paint.hide(null)
        ctx.rowHide?.paint?.hideRow(-1)
        ctx = if (t.retarget) c.carry(ctx, move, t.at) else c
        val hide = TileHide(ctx.album, ctx.ends.thumb)
        for (h in ctx.hides) h.paint.hide(hide)
        ctx.rowHide?.paint?.hideRow(ctx.hideRow)
        return ctx.ends
    }

    fun landed() {
        for (h in ctx.hides) h.paint.hide(null)
        ctx.rowHide?.paint?.hideRow(-1)
    }

    private fun tailNeed(v: PageMove, hb: Int, anchor: Float, s0: Float, oy: Float): Int {
        var need = 0f
        var t = v.t0
        while (t <= v.endAt) {
            val ls = v.at(Part.Leave, Prop.S, t)
            if (ls > 0f && s0 * ls < 1f && v.at(Part.Leave, Prop.A, t) > 0f) {
                val y = ((hb - oy) / s0 - v.at(Part.Leave, Prop.Y, t) - (1 - ls) * anchor) / ls
                need = max(need, y - hb)
            }
            t += TAIL_STEP
        }
        return ceil(need).toInt()
    }

    private fun arriveTail(m: MoveCtx, forms: MoveForms): Int {
        val to = m.to ?: return 0
        val b = to.paint.bandsAt() ?: return 0
        val hb = b.y.last()
        val lift = hb - m.ya
        if (!m.rows || lift <= 0f || hb > to.scale.heightPx) return 0
        val dy = Tok.motion_rowsDy
        var need = ceil(lift * (1 - dy) / dy).toInt()
        val back = when {
            move.kind != MoveKind.T3 -> MoveKind.T4h
            move.cut && m.from?.paint?.nowPlaying != true -> MoveKind.T4
            else -> MoveKind.None
        }
        if (back != MoveKind.None) {
            val p = PageMove()
            p.start(back, 0L, MoveEnds(leaveRows = true, morph = back == MoveKind.T4, morphKind = MorphKind.Row, pxU = to.scale.ppu), forms)
            val anchor = PageMove.popAnchor(back, to.paint.top, to.scale.heightPx, to.scale.stdPpu).toFloat()
            need = max(need, tailNeed(p, hb, anchor, dy, (1 - dy) * m.ya))
        }
        return need
    }

    private fun pivotReach(w: Float): Int {
        val v = move
        val l = v.leg(Part.Pivots, Prop.S)
        var reach = w
        if (l != null) {
            var t = v.t0 + l.delay
            val end = v.partEnd(Part.Pivots)
            while (t <= end) {
                val ox = v.at(Part.Pivots, Prop.PX, t)
                reach = max(reach, ox + (w - v.at(Part.Pivots, Prop.X, t) - ox) / v.at(Part.Pivots, Prop.S, t))
                t += REACH_STEP
            }
        }
        return floor(reach).toInt() + REACH_PAD
    }

    private fun leaveTail(m: MoveCtx): Int {
        val b = m.leaveBands ?: return 0
        val held = m.leaveAt ?: return 0
        val from = m.from ?: return 0
        val hb = b.y.last()
        if (hb > from.scale.heightPx) return 0
        val oy = held.dy + (1 - held.s) * held.ya
        return tailNeed(move, hb, m.leaveAnchor, held.s, oy)
    }

    private fun DrawScope.spriteAt(rec: DrawRecord?, name: String, wd: Word, sc: Float, x: Float, y: Float, a: Float, bottom: Int) {
        sprite(wd, sc, x, y, a, bottom)
        if (rec != null) rec.clip(0f, 0f, size.width, bottom.coerceIn(0, size.height.toInt()).toFloat()) { rec.move(x, y) { rec.zoom(sc, 0f, 0f) { rec.word(name, wd, 0f, 0f, a) } } }
    }

    private fun DrawScope.chromeOf(side: PageSide, leave: Boolean, m: MoveCtx, now: Long, morphing: Boolean, drawn: Boolean, rec: DrawRecord?, pivrow: Boolean = false) {
        val c = side.paint.chrome ?: return
        val v = move
        val k = v.kind
        var ha = v.at(if (leave) Part.LeaveHeader else Part.Header, Prop.A, now)
        val start = leave && m.word != null && (k == MoveKind.T4h || k == MoveKind.T4m || k == MoveKind.T3m)
        if (start && k != MoveKind.T3m) ha = 0f
        var hp = ChromePart(alpha = ha, hidden = ha <= 0f)
        val pp = if (leave) Part.LeavePivots else Part.Pivots
        val pa = v.at(pp, Prop.A, now)
        val pv = ChromePart(Math.round(v.at(pp, Prop.X, now)).toFloat(), Math.round(v.at(pp, Prop.Y, now)), pa, pa <= 0f)
        val mo = m.morph
        val sd = if (mo == null) null else if (leave) mo.a else mo.b
        val ma = v.at(Part.Word, Prop.A, now)
        if (morphing && sd != null && sd.header && (leave || k != MoveKind.T4)) {
            val a = if (drawn) 0f else ma
            hp = ChromePart(alpha = a, hidden = a <= 0f)
        }
        c.headerPart = hp
        for (i in 0 until c.count) {
            var q = pv
            if (k == MoveKind.T3m && leave) q = ChromePart(hidden = true)
            if (k == MoveKind.T4m && !leave && i == c.active && ma > 0f) q = ChromePart(q.dx, q.dy, q.alpha, true)
            if (sd != null && i == sd.pivot && (morphing || leave) && (leave || drawn || k != MoveKind.T4)) {
                val a = if (morphing && !drawn) ma else 0f
                q = ChromePart(alpha = a, hidden = a <= 0f)
            }
            if (pivrow && !leave) q = ChromePart(q.dx, q.dy, q.alpha, true)
            c.pivotParts[i] = q
        }
        chrome(c, now)
        rec?.chrome(if (leave) "leave.chrome" else "arrive.chrome", c, now, size.width.toInt())
        c.clearParts()
    }

    private fun DrawScope.restOf(side: PageSide, now: Long, rec: DrawRecord?, name: String, fade: Float = 0f) {
        val p = side.paint
        if (p.grounded) with(p) { ground(1f, 0f) }
        with(p) { content(Content.Rest, now, rec, name, fade) }
        val c = p.chrome ?: return
        chrome(c, now)
        rec?.chrome("$name.chrome", c, now, size.width.toInt())
        c.clearParts()
    }

    private fun DrawScope.npHomeAt(h: HomeSide, at: Long, now: Long) {
        with(h.paint) {
            if (quickplay) peek(peekAlpha(at), now, null)
            home(HomeMove(1f, -1, 0f, Roles.homeSubtitle.alpha, h.off, watermark = false), now, null)
        }
    }

    private fun cam(p: Part, d: Float, eye: Float, cx: Float, at: Long): M3? {
        val v = move
        return npCam(v.at(p, Prop.R, at), Math.round(v.at(p, Prop.X, at)).toFloat(), Math.round(v.at(p, Prop.PX, at)).toFloat(), d, eye, cx)
    }

    private fun DrawScope.quadAt(rec: DrawRecord?, name: String, a: Float, t: M3?, src: Edges? = null, block: DrawScope.() -> Unit) {
        if (a <= 0f || t == null) return
        val draw = {
            layer(null, a, layerPaint) {
                withTransform({ transform(t.compose()) }) {
                    if (src == null) block() else clipRect(src.x0.toFloat(), src.y0.toFloat(), src.x1.toFloat(), src.y1.toFloat()) { block() }
                }
            }
        }
        if (rec == null) draw() else rec.quad(name, a, t, src, draw)
    }

    private fun underXf(m: MoveCtx, now: Long): M3? {
        val v = move
        val s = (m.from ?: return null).scale
        val p = s.stdPpu
        val h = m.home
        val d = max(1f, whole(Tok.motion_perspective, p))
        val ineye = Math.round(Tok.motion_t8InEye * s.heightPx).toFloat()
        return when {
            m.ends.npForm == NpForm.Front && h != null && h.paint.frontPane() != null -> {
                val edge = px(Tok.qpEdge + Tok.qpTile + Tok.qpGutter, p) + px(Tok.qpTile, p)
                npFrontUnder(v.at(Part.NpUnder, Prop.X, now), v.at(Part.NpEdge, Prop.X, now), px(Tok.qpEdge, p).toFloat(), edge.toFloat(), ineye, d)
            }
            m.to != null -> turn(v.at(Part.NpUnder, Prop.R, now), 0f, ineye, max(1f, whole(Tok.motion_t8InD, p)))
            else -> {
                val eye = Math.round(s.board.motion_t7EyeY * s.heightPx).toFloat()
                cam(Part.NpUnder, max(1f, whole(Tok.motion_t7Persp, p)), eye, Math.round(Tok.motion_t7EyeX * s.widthPx).toFloat(), now)
            }
        }
    }

    fun carry(x: Float, y: Float, now: Long): android.graphics.Matrix? {
        val m = ctx
        val v = move
        val k = v.kind
        if (m === MoveCtx.None) return null
        val to = m.to
        val s = (to ?: m.from ?: return null).scale
        val bar = if (s.navOn) px(s.stdContentBottom, s.stdPpu).toFloat() else Float.MAX_VALUE
        val c = if (k == MoveKind.T8) {
            CarryAt(k, true, false, bar, under = if (v.at(Part.NpUnder, Prop.A, now) > 0f) underXf(m, now) else null)
        } else if (to == null) {
            CarryAt(k, true, false, bar)
        } else {
            val ts = to.scale
            val top = px(ts.board.page_contentTop, ts.ppu).toFloat()
            CarryAt(
                k, false, m.rows, bar, top, if (to.paint.bandsAt() == null) top else to.paint.top.toFloat(),
                px(ts.board.pivotTop, ts.ppu).toFloat(), px(ts.board.pivotBottom, ts.ppu).toFloat(),
                m.xa, m.ya, Tok.motion_contentX * ts.widthPx, Tok.motion_contentY * ts.heightPx,
                v.at(Part.Content, Prop.A, now), v.at(Part.Content, Prop.S, now), v.at(Part.Content, Prop.X, now),
                v.at(Part.Pivots, Prop.A, now), v.at(Part.Pivots, Prop.X, now), v.at(Part.Pivots, Prop.Y, now),
            )
        }
        val t = carryOf(x, y, c) ?: return null
        return android.graphics.Matrix().also { it.setValues(floatArrayOf(t.a, t.b, t.c, t.d, t.e, t.f, t.g, t.h, t.i)) }
    }

    private fun DrawScope.npMove(m: MoveCtx, now: Long, rec: DrawRecord?) {
        val v = move
        val e = m.ends
        val np = (if (v.kind == MoveKind.T8) m.from else m.to) ?: return
        val s = np.scale
        val p = s.stdPpu
        val h = m.home
        val w = s.widthPx.toFloat()
        val d = max(1f, whole(Tok.motion_perspective, p))
        val peye = Math.round(s.board.motion_t7EyeY * s.heightPx).toFloat()
        val cx = Math.round(Tok.motion_t7EyeX * w).toFloat()
        val x1 = px(Tok.qpEdge, p)
        val x2 = px(Tok.qpEdge + Tok.qpTile + Tok.qpGutter, p)
        val side = px(Tok.qpTile, p)
        val src = Edges(max(0, x1), 0, min(s.widthPx, x2 + side), s.heightPx)
        val pane = h?.paint?.frontPane()
        val front = e.npForm == NpForm.Front && h != null && pane != null
        val peekR = if (front && h != null && pane != null) npPeekRect(menuPeek(h.scale, pane.x1), pane, src) else null
        fun DrawScope.peek() {
            if (h == null || peekR == null) return
            val r = v.at(Part.NpPeek, Prop.R, now)
            val sh = M3.translate(Math.round(v.at(Part.NpPeek, Prop.X, now)).toFloat(), Math.round(v.at(Part.NpPeek, Prop.Y, now)).toFloat())
            val t = if (r != 0f) sh * turn(r, peekR.x0.toFloat(), ((peekR.y0 + peekR.y1) / MID).toFloat(), d) else sh
            quadAt(rec, NP_PEEK, v.at(Part.NpPeek, Prop.A, now), t, peekR) { with(h.paint) { frontPeek(Tok.motion_quickplayAlpha, now) } }
        }
        if (v.kind == MoveKind.T8) {
            val out = npEye(p, s.heightPx, Tok.motion_t8OutHinge, Tok.motion_t8OutEye)
            quadAt(rec, NP_OUT, v.at(Part.NpOut, Prop.A, now), quad(v.at(Part.NpOut, Prop.R, now), out, 0f, 0f)) { with(np.paint) { content(Content.Rest, now, null, "leave") } }
            val to = m.to
            val ua = v.at(Part.NpUnder, Prop.A, now)
            when {
                front && h != null -> {
                    peek()
                    quadAt(rec, NP_PANE, ua, underXf(m, now), src) { with(h.paint) { front(now) } }
                }
                to != null -> {
                    val t = underXf(m, now)
                    for ((b, part) in underBands(to, p)) quadAt(rec, NP_UNDER + part.name, v.at(part, Prop.A, now), t, b) { restOf(to, now, null, "arrive") }
                }
                else -> quadAt(rec, NP_HOME, ua, underXf(m, now)) { if (h != null) npHomeAt(h, now, now) }
            }
            return
        }
        val leave: DrawScope.() -> Unit = {
            val la = v.at(Part.NpLeave, Prop.A, now)
            val t = cam(Part.NpLeave, max(1f, whole(Tok.motion_t7PaneD, p)), peye, cx, now)
            val from = m.from
            when {
                front && h != null -> quadAt(rec, NP_LEAVE, la, t, src) { with(h.paint) { front(now) } }
                from != null -> quadAt(rec, NP_LEAVE, la, t) { restOf(from, now, null, "leave") }
                h != null -> quadAt(rec, NP_LEAVE, la, t) { npHomeAt(h, v.t0, now) }
                else -> Unit
            }
            peek()
        }
        val opaque = e.npForm == NpForm.Page
        if (opaque) leave()
        val camIn = cam(Part.NpIn, max(1f, whole(Tok.motion_t7Persp, p)), peye, cx, now)
        for (part in NP_PARTS) quadAt(rec, NP_PART + part.name, v.at(part, Prop.A, now), camIn) { with(np.paint) { npPart(part, now, null) } }
        val ring = np.paint.npRing()
        if (ring != null) {
            val rs = v.at(Part.NpRing, Prop.S, now).let { if (it > 0f) it else 1f }
            val rcx = ((ring.x0 + ring.x1) / MID).toFloat()
            val rcy = ((ring.y0 + ring.y1) / MID).toFloat()
            val t = M3.translate((1 - rs) * rcx + v.at(Part.NpRing, Prop.X, now), (1 - rs) * rcy + v.at(Part.NpRing, Prop.Y, now)) *
                M3.scale(rs) * turn(v.at(Part.NpRing, Prop.R, now), rcx, rcy, d)
            quadAt(rec, NP_RING, v.at(Part.NpRing, Prop.A, now), t) { with(np.paint) { npPart(Part.NpRing, now, null) } }
        }
        if (!opaque) leave()
    }

    private fun DrawScope.turnOf(m: MoveCtx, now: Long, rec: DrawRecord?) {
        val v = move
        val te = m.turnEye ?: return
        val from = m.from
        val h = m.home
        if (from != null) {
            val fp = from.paint
            if (fp.grounded) with(fp) { ground(v.at(Part.LeaveGround, Prop.A, now), 0f) }
            quadAt(rec, TURN_LEAVE, v.at(Part.Leave, Prop.A, now), quad(v.at(Part.Leave, Prop.R, now), te.out, 0f, 0f)) {
                with(fp) { content(Content.Rest, now, null, "leave", m.leaveThumb) }
                val c = fp.chrome
                if (c != null) {
                    chrome(c, now)
                    c.clearParts()
                }
            }
        }
        if (h == null) return
        val pa = v.at(Part.Peek, Prop.A, now)
        if (pa > 0f) shifted(rec, Math.round(v.at(Part.Peek, Prop.X, now)).toFloat(), 0f) { with(h.paint) { peek(pa, now, rec) } }
        val sub = v.at(Part.Sub, Prop.A, now) * Roles.homeSubtitle.alpha
        quadAt(rec, TURN_HOME, v.at(Part.HomeTurn, Prop.A, now), cam(Part.HomeTurn, te.inD, te.inEye, te.cx, now)) {
            with(h.paint) { home(HomeMove(1f, -1, 0f, sub, h.off), now, null) }
        }
    }

    private fun leaveAt(held: Place, anchor: Float, now: Long): Place {
        val v = move
        val ls = v.at(Part.Leave, Prop.S, now)
        val lx = Math.round(v.at(Part.Leave, Prop.X, now)).toFloat()
        val ly = v.at(Part.Leave, Prop.Y, now)
        if (ls == 1f && lx == 0f && ly == 0f) return held
        val ix = lx + (1 - ls) * held.xa
        val iy = ly + (1 - ls) * anchor
        val hs = held.s
        return Place(hs * ls, held.dx + (1 - hs) * held.xa + hs * ix, held.dy + (1 - hs) * held.ya + hs * iy, 0f, 0f)
    }

    private fun DrawScope.slide(m: MoveCtx, now: Long, rec: DrawRecord?) {
        val v = move
        val h = m.home
        val ha = v.at(Part.Home, Prop.A, now)
        if (h != null && ha > 0f) {
            layer(rec, ha, layerPaint) {
                shifted(rec, Math.round(v.at(Part.Home, Prop.X, now)).toFloat(), 0f) {
                    with(h.paint) {
                        peek(v.at(Part.Peek, Prop.A, now), now, rec)
                        home(HomeMove(1f, -1, 0f, m.ends.sub, h.off), now, rec)
                    }
                }
            }
        }
        val to = m.to
        val ca = v.at(Part.Content, Prop.A, now)
        if (to != null && ca > 0f) {
            layer(rec, ca, layerPaint) {
                shifted(rec, Math.round(v.at(Part.Content, Prop.X, now)).toFloat(), 0f) { restOf(to, now, rec, "arrive", m.arriveThumb) }
            }
        }
    }

    private fun DrawScope.leaving(rec: DrawRecord?, dx: Float, block: DrawScope.() -> Unit) =
        if (dx == 0f) block() else clipped(rec, 0f, 0f, size.width, size.height) { shifted(rec, dx, 0f, block) }

    private fun DrawScope.statusOf(st: MoveStatus, m: MoveCtx, now: Long) {
        val one = m.home == null || (m.from != null && m.to != null) || st.home.sameRow(st.page)
        val h = if (one) 0f else move.at(Part.Home, Prop.A, now)
        if (h > 0f) statusRow(st.home, st.accent, st.accentOff, h)
        if (h < 1f) statusRow(st.page, st.accent, st.accentOff, 1f - h)
    }

    fun DrawScope.drawMove(now: Long, rec: DrawRecord?, status: MoveStatus? = null) {
        val m = ctx
        val v = move
        val k = v.kind
        if (k == MoveKind.T7 || k == MoveKind.T8) {
            npMove(m, now, rec)
            if (status != null) statusOf(status, m, now)
            return
        }
        if (k == MoveKind.T4n && m.ends.turn) {
            turnOf(m, now, rec)
            if (status != null) statusOf(status, m, now)
            return
        }
        val s = m.scale
        if (s == null) {
            if (status != null) statusOf(status, m, now)
            return
        }
        if (k == MoveKind.T2w) {
            slide(m, now, rec)
            if (status != null) statusOf(status, m, now)
            return
        }
        if (m.tail < 0) m.tail = arriveTail(m, forms)
        if (m.leaveTail < 0) m.leaveTail = leaveTail(m)
        val from = m.from
        val to = m.to
        val leaveG = from?.paint?.grounded == true
        val arriveG = to?.paint?.grounded == true
        var lg = if (leaveG) v.at(Part.LeaveGround, Prop.A, now) else 0f
        val ag = if (!arriveG || (leaveG && !v.has(Part.Ground, Prop.A))) 0f else v.at(Part.Ground, Prop.A, now)
        if (leaveG && arriveG && !v.has(Part.Ground, Prop.A)) lg = 1f
        val grounder = if (arriveG) to else if (leaveG) from else null
        if (grounder != null) leaving(rec, if (grounder === from) m.leaveDx else 0f) { with(grounder.paint) { ground(lg, ag) } }
        val ox = Tok.motion_contentX * size.width
        val oy = Tok.motion_contentY * size.height
        val la = v.at(Part.Leave, Prop.A, now)
        if (from != null && la > 0f) {
            val lb = m.leaveBands
            val held = m.leaveAt
            val ls = v.at(Part.Leave, Prop.S, now)
            val c = if (held != null) {
                Content.Lift(lb, leaveAt(held, m.leaveAnchor, now), m.leaveCut, m.leaveTail, m.leaveBarX)
            } else {
                var px = v.at(Part.Leave, Prop.PX, now)
                var py = v.at(Part.Leave, Prop.PY, now)
                if (px == 0f && py == 0f) {
                    px = ox
                    py = oy
                }
                Content.Lift(null, Place(ls, px - px * ls, py - py * ls + v.at(Part.Leave, Prop.Y, now), 0f, 0f), null)
            }
            layer(rec, la, layerPaint) { with(from.paint) { content(c, now, rec, "leave", m.leaveThumb) } }
        }
        val mo = m.morph
        val morphing = mo != null && now < v.t0 + v.wordEnd
        val wa = if (mo != null) m.morphLook * v.at(Part.Word, Prop.A, now) else 0f
        val drawn = morphing && wa > 0f
        if (from != null) leaving(rec, m.leaveDx) { chromeOf(from, true, m, now, morphing, drawn, rec) }
        val hs = m.home
        if (hs != null && (from == null || to == null)) {
            val look = HomeMove(v.at(Part.Home, Prop.A, now), if (m.word != null) m.item else -1, if (to == null) v.at(Part.Look, Prop.A, now) else 0f, v.at(Part.Sub, Prop.A, now), hs.off)
            with(hs.paint) {
                peek(v.at(Part.Peek, Prop.A, now), now, rec)
                home(look, now, rec)
            }
        }
        val ca = v.at(Part.Content, Prop.A, now)
        if (m.ends.thumb) tileOf(m, now, rec)
        if (to != null && ca > 0f) {
            val cs = v.at(Part.Content, Prop.S, now)
            val cx = Math.round(v.at(Part.Content, Prop.X, now)).toFloat()
            val image = k == MoveKind.T3z
            val c = if (m.rows) {
                Content.Lift(to.paint.bandsOrRest(), Place(cs, cx + (1 - cs) * m.xa, (1 - cs) * m.ya, 0f, 0f), m.cut, m.tail, m.barX)
            } else {
                val px = if (image) v.at(Part.Content, Prop.PX, now) else ox
                val py = if (image) v.at(Part.Content, Prop.PY, now) else oy
                Content.Lift(null, Place(cs, px - px * cs, py - py * cs + v.at(Part.Content, Prop.Y, now), 0f, 0f), null)
            }
            layer(rec, ca, layerPaint) {
                if (image) {
                    placed(rec, c.at) {
                        drawRect(Ink.bg)
                        if (to.paint.grounded) with(to.paint) { ground(1f, 0f) }
                    }
                }
                with(to.paint) { content(c, now, rec, "arrive", m.arriveThumb) }
            }
        }
        if (!m.ends.thumb) tileOf(m, now, rec)
        val pr = m.pivotRow
        val pivrow = to != null && pr != null && v.has(Part.Pivots, Prop.S)
        val pva = if (pivrow) v.at(Part.Pivots, Prop.A, now) else 0f
        val tc = to?.paint?.chrome
        if (pivrow && m.pivotReach < 0) m.pivotReach = pivotReach(size.width)
        if (to != null && pr != null && tc != null && pva > 0f) {
            val rx = max(pr.x1, m.pivotReach).toFloat()
            val sc = v.at(Part.Pivots, Prop.S, now)
            val px = v.at(Part.Pivots, Prop.PX, now)
            val py = v.at(Part.Pivots, Prop.PY, now)
            layer(rec, pva, layerPaint) {
                placed(rec, Place(sc, px - px * sc + v.at(Part.Pivots, Prop.X, now), py - py * sc, 0f, 0f)) {
                    clipped(rec, pr.x0.toFloat(), pr.y0.toFloat(), rx, pr.y1.toFloat()) {
                        tc.headerPart = ChromePart(hidden = true)
                        chrome(tc, now)
                        rec?.chrome("pivrow.chrome", tc, now, size.width.toInt())
                        tc.clearParts()
                        with(to.paint) { pivotRowInk(rec) }
                    }
                }
            }
        }
        if (to != null) chromeOf(to, false, m, now, morphing, drawn, rec, pivrow)
        val hext = (px(s.board.pivotTop, s.stdPpu) - px(Tok.page_headerBaseline, s.stdPpu)).toFloat()
        if (mo != null && morphing && drawn) {
            val sc = v.at(Part.Word, Prop.S, now)
            val y = v.at(Part.Word, Prop.Y, now)
            val bottom = if (!mo.push && m.rowBottom >= 0) m.rowBottom else wordBottom(s, y, sc, progress(Prop.S, sc), mo.b.header)
            spriteAt(rec, "morph", mo.word.word, sc, v.at(Part.Word, Prop.X, now), y, wa, bottom)
        }
        val span = max(1, Tok.motion_leave.toInt())
        val e = (now - m.fadeAt).coerceIn(0L, span.toLong())
        for ((i, f) in m.fades.withIndex()) {
            val a = f.a * (1 - e.toFloat() / span)
            if (a > 0f) spriteAt(rec, if (i == 0) "fade.leave" else "fade.arrive", f.word, f.s, f.x, f.y, a, Math.round(f.y + hext * f.s))
        }
        val wd = m.word
        if (mo == null && wd != null && v.at(Part.Word, Prop.A, now) > 0f) wordOf(m, wd, s, now, rec)
        val lw = m.leaveWord
        if (lw != null && v.at(Part.LeaveWord, Prop.A, now) > 0f) {
            val sc = v.at(Part.LeaveWord, Prop.S, now)
            var px = v.at(Part.LeaveWord, Prop.PX, now)
            var py = v.at(Part.LeaveWord, Prop.PY, now)
            if (px == 0f && py == 0f) {
                px = m.ends.zoomX
                py = m.ends.zoomY
            }
            val x = px + (lw.pen - px) * sc + v.at(Part.LeaveWord, Prop.X, now)
            val y = py + (lw.base - py) * sc + v.at(Part.LeaveWord, Prop.Y, now)
            spriteAt(rec, "leave.zoom", lw.word, sc, x, y, lw.alpha * v.at(Part.LeaveWord, Prop.A, now), Math.round(y + hext * sc))
        }
        val zw = m.zoom
        if (zw != null && v.at(Part.Zoom, Prop.A, now) > 0f && v.at(Part.Header, Prop.A, now) <= 0f) {
            val sc = v.at(Part.Zoom, Prop.S, now)
            val px = v.at(Part.Zoom, Prop.PX, now)
            val py = v.at(Part.Zoom, Prop.PY, now)
            val y = py + (m.zoomBase - py) * sc
            spriteAt(rec, "zoom", zw, sc, px + (m.zoomPen - px) * sc, y, m.zoomAlpha * v.at(Part.Zoom, Prop.A, now), Math.round(y + hext * sc))
        }
        if (status != null) statusOf(status, m, now)
    }

    private fun progress(q: Prop, value: Float): Float {
        val l = move.span(Part.Word, q)
        return if (l != null && l.second != l.first) ((value - l.first) / (l.second - l.first)).coerceIn(0f, 1f) else 1f
    }

    private fun DrawScope.tileOf(m: MoveCtx, now: Long, rec: DrawRecord?) {
        val ts = m.tileSide ?: return
        val v = move
        val a = v.at(Part.Tile, Prop.A, now)
        if (a > 0f) with(ts.paint) { tile(m.album, v.at(Part.Tile, Prop.S, now), v.at(Part.Tile, Prop.X, now), v.at(Part.Tile, Prop.Y, now), a, rec) }
    }

    private fun DrawScope.wordOf(m: MoveCtx, wd: Word, s: Scale, now: Long, rec: DrawRecord?) {
        val v = move
        val k = v.kind
        val sc = v.at(Part.Word, Prop.S, now)
        val y = v.at(Part.Word, Prop.Y, now)
        val wx = v.at(Part.Word, Prop.X, now)
        val p = min(progress(Prop.S, sc), progress(Prop.Y, y))
        var look = m.wordLook
        if (k == MoveKind.T3m || k == MoveKind.T3c || k == MoveKind.T4m) look = lerp(Roles.pivotActive.alpha, look, if (k == MoveKind.T4m) 1 - p else p)
        val a = look * (1 - v.at(Part.Look, Prop.A, now))
        val bottom = wordBottom(s, y, sc, p, k == MoveKind.T2 || k == MoveKind.T3m)
        val fw = m.face
        val faceA = if (fw != null && v.has(Part.Word, Prop.S)) v.at(Part.Face, Prop.A, now) else 0f
        val l = v.span(Part.Word, Prop.S)
        val itemS = if (l == null) 0f else if (k == MoveKind.T2) l.first else l.second
        val fa = a * faceA
        val faced = fw != null && faceA > 0f && itemS > 0f
        if (faced && fw != null) spriteAt(rec, "face", fw, sc / itemS, wx, y, fa, bottom)
        val wl = if (!faced) a else if (faceA >= 1f) 0f else a * (1 - faceA) / (1 - fa)
        if (!faced || wl > 0f) spriteAt(rec, "word", wd, sc, wx, y, wl, bottom)
    }
}
