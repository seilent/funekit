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
import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.ChromePart
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
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
import net.seilent.funekit.kit.motion.npInk
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.proj.M3
import net.seilent.funekit.kit.proj.turn
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.sprite
import net.seilent.funekit.kit.statusRow
import net.seilent.funekit.kit.wordBottom
import net.seilent.funekit.px
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val HALF = 2f
private const val NP_HALF = 2f
private const val NP_FX = 65536f
private const val SLIDE_X = 200f
private const val SLIDE_W = 480f
private const val TAIL_STEP = 4L

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

class HomeMove(val items: Float, val sel: Int, val selAlpha: Float, val sub: Float, val off: Float)

interface HomePaint {
    val quickplay: Boolean

    fun peekAlpha(at: Long): Float

    fun baseline(item: Int): Float

    fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?)

    fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?)

    fun npBands(off: Float): List<Edges> = emptyList()

    val npMusic: Int get() = -1

    fun frontPane(): Edges? = null

    fun DrawScope.front(now: Long) {}

    fun DrawScope.frontPeek(alpha: Float, dx: Float, now: Long) {}

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

    fun rowY(i: Int): Int

    fun DrawScope.ground(leave: Float, arrive: Float)

    fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String)

    fun wordSide(pivot: Boolean): WordSide? = null

    val nowPlaying: Boolean get() = false
    val queue: Boolean get() = false

    fun tileRect(album: Long): Edges? = null

    fun DrawScope.tile(album: Long, side: Float, cx: Float, cy: Float, alpha: Float, rec: DrawRecord?) {
        val h = side / HALF
        drawRect(Ink.tile, Offset(cx - h, cy - h), Size(side, side), alpha = alpha)
    }

    fun thumbRect(row: Int): Edges? = null

    fun hide(h: TileHide?) {}

    fun pivotRow(): Edges? = null

    fun thumbFade(at: Long): Float = 0f

    fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {}

    fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) = restRows(this@PagePaint, now, rec, name)

    fun DrawScope.content(c: Content, now: Long, rec: DrawRecord?, name: String, fade: Float = 0f) = contentRows(this@PagePaint, c, now, rec, name, fade)

    fun DrawScope.bands(b: Bands, now: Long, rec: DrawRecord?, name: String) = bandRows(this@PagePaint, b, now, rec, name)
}

fun PagePaint.rowTop(i: Int): Int = rowY(i) - scroll

fun PagePaint.first(): Int {
    var lo = 0
    var hi = count
    while (lo < hi) {
        val m = (lo + hi) ushr 1
        if (rowTop(m + 1) > top) hi = m else lo = m + 1
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
    clipped(rec, 0f, p.top.toFloat(), size.width, p.bottom.toFloat()) {
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
                placed(rec, c.at) {
                    clipped(rec, 0f, p.top.toFloat(), size.width, max(p.bottom, p.top + 1).toFloat()) {
                        with(p) { rest(now, rec, name) }
                        bar()
                    }
                }
            } else {
                val y0 = b.y.first().toFloat()
                val y1 = b.y.last().toFloat()
                val bx = min(c.barX, size.width)
                placed(rec, c.at) { clipped(rec, 0f, y0, bx, y1) { with(p) { bands(b, now, rec, name) } } }
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
    y += top
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
) {
    val scale: Scale? get() = (to ?: from)?.scale ?: home?.scale

    val lift: Boolean get() = rows

    var tail: Int = -1
    var leaveTail: Int = -1

    fun carry(old: MoveCtx, v: PageMove, at: Long): MoveCtx {
        fun cur(p: Part, q: Prop) = v.at(p, q, at)
        val ca = cur(Part.Content, Prop.A)
        val oldTo = old.to?.paint
        val cs = cur(Part.Content, Prop.S)
        val cx = Math.round(cur(Part.Content, Prop.X)).toFloat()
        val slid = old.kind == MoveKind.T2w && oldTo != null && ca > 0f
        val on = old.rows && oldTo != null && ca > 0f
        val bands = if (slid) leaveBands else if (!on || oldTo == null) null else oldTo.bandsAt()
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

fun npEnds(k: MoveKind, np: PageSide?, home: HomeSide?, form: NpForm, forms: MoveForms): MoveEnds {
    val p = (np?.scale ?: home?.scale)?.stdPpu ?: return MoveEnds(npForm = form)
    val cut = Tok.motion_npIn != NP_TURN
    if (k == MoveKind.T8) {
        val page = form != NpForm.Front
        return MoveEnds(
            npForm = form,
            npCut = cut,
            npX = whole(if (page) Old.motion_t8PageX else Old.motion_t8SlideX, p),
            npY = if (page) 0f else whole(Old.motion_t8SlideY, p),
        )
    }
    return MoveEnds(
        npForm = form,
        npCut = cut,
        npLeft = whole(Old.motion_t7PaneLeft, p),
        npRight = whole(Old.motion_t7PaneRight, p),
        npBack = whole(Old.motion_t7PaneBack, p),
        npX = whole(Old.motion_t7RingX, p),
        endAt = npInk(form, forms) + if (cut) 0 else Tok.motion_t7Rest.toInt(),
    )
}

private const val NP_TURN = "turn"

class NpEye(val d: Float, val hinge: Float, val eye: Float)

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
    if (k == MoveKind.T7 || k == MoveKind.T8) return MoveCtx(npEnds(k, if (k == MoveKind.T7) to else from, home, npForm, forms), k, home, from, to, false, 0f, null, 0f, -1, null, 0f, 0f, 0f, null, 0f)
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
    if ((k == MoveKind.T3 || k == MoveKind.T4) && forms.wordMorph && from != null && to != null) {
        val push = k == MoveKind.T3
        val a = from.paint.wordSide(push)
        val b = to.paint.wordSide(!push)
        if (a != null && b != null && a.text != b.text) {
            val m = Morph(a, b, push)
            morph = m
            morphLook = if (!push && lit && a.header) Tok.alpha_full else m.word.look
            e = e.copy(
                morph = true, morphKind = MorphKind.Pivot,
                morphS0 = a.s, morphS1 = b.s,
                morphX0 = a.x, morphX1 = b.x, morphY0 = a.y, morphY1 = b.y,
                morphHeaderOut = a.header, morphHeaderIn = b.header,
                zoom = e.zoom && !(push && b.header),
            )
            if (push) {
                pivotPush = true
                val fc = from.paint.chrome
                val hw = from.paint.header
                if (fc != null && hw != null && hw.text != m.word.text) {
                    val base = headerBase(from.scale)
                    leaveWord = LeaveWord(hw, fc.headerRestX(), base, if (lit) Tok.alpha_full else fc.headerAlpha)
                    e = e.copy(lhead = true, lheadBase = base)
                }
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
    val ts = to?.scale
    val rows = to != null && to.paint.kind != PageKind.Grid && formOf(to.paint.kind, forms) != ContentForm.Rest &&
        (k == MoveKind.T2 || k == MoveKind.T2n || k == MoveKind.T3) && !pivotPush
    val ya = if (ts == null) 0f else ts.heightPx - stdPx(Tok.motion_rowsYa, ts)
    val xa = stdPx(Tok.hubEdge, s)
    if (ts != null) e = e.copy(rowsDx = stdPx(Tok.motion_rowsDx, ts))
    val cut = if (rows && ts != null && ts.navOn) px(ts.stdContentBottom, ts.stdPpu).toFloat() else null
    val zoom = if (e.zoom && toChrome != null) toChrome.header else null
    val leaving = if ((k == MoveKind.T4h || k == MoveKind.T4n) && to == null && from != null && from.paint.kind != PageKind.Grid) from else null
    val leaveBands = leaving?.paint?.bandsAt()
    val anchor = if (leaving == null || leaveBands == null) 0f else PageMove.popAnchor(k, leaving.paint.top, leaving.scale.heightPx, leaving.scale.stdPpu).toFloat()
    val leaveAt = if (leaving != null) Place(1f, 0f, 0f, xa, anchor) else null
    val leaveCut = if (leaving != null && leaving.scale.navOn) px(leaving.scale.stdContentBottom, leaving.scale.stdPpu).toFloat() else null
    if (leaving != null) e = e.copy(leaveRows = leaveBands != null)
    if (album < 0) album = -1L
    val hides = if (tileSide != null) listOfNotNull(from, to) else emptyList()
    return MoveCtx(
        e, k, home, from, to, rows, ya, word, look, item,
        zoom, toChrome?.headerRestX() ?: 0f, to?.let { headerBase(it.scale) } ?: 0f, toChrome?.headerAlpha ?: 0f,
        morph, morphLook, leaveBands = leaveBands, leaveWord = leaveWord, xa = xa, cut = cut, leaveAt = leaveAt, leaveCut = leaveCut, leaveAnchor = anchor,
        thumbAt = t.at, leaveThumb = from?.paint?.thumbFade(t.at) ?: 0f, arriveThumb = to?.paint?.thumbFade(t.at) ?: 0f,
        face = face, album = album, tileSide = tileSide, barX = ts?.let { barOf(it) } ?: Float.MAX_VALUE,
        leaveBarX = leaving?.let { barOf(it.scale) } ?: Float.MAX_VALUE, pivotRow = to?.paint?.pivotRow(), hides = hides,
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
        ctx = if (t.retarget) c.carry(ctx, move, t.at) else c
        val hide = TileHide(ctx.album, ctx.ends.thumb)
        for (h in ctx.hides) h.paint.hide(hide)
        return ctx.ends
    }

    fun landed() {
        for (h in ctx.hides) h.paint.hide(null)
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

    private fun DrawScope.homeAt(h: HomeSide, now: Long) {
        with(h.paint) { home(HomeMove(1f, -1, 0f, Roles.homeSubtitle.alpha, h.off), now, null) }
    }

    private fun DrawScope.paneAt(h: HomeSide, p: Edges, at: Long) {
        val v = move
        val w = max(1, p.w)
        val l = p.x0 + v.at(Part.NpLeave, Prop.X, at)
        val r = p.x1 + v.at(Part.NpEdge, Prop.X, at) + v.at(Part.NpEdge, Prop.Y, at)
        val sc = max((r - l) / w, 1f / NP_FX)
        val cy = (p.y0 + p.y1) / NP_HALF
        val x0 = l
        val y0 = cy - (cy - p.y0) * sc
        clipRect(x0, y0, x0 + p.w * sc, y0 + p.h * sc) {
            withTransform({
                translate(l - p.x0 * sc, cy - cy * sc)
                scale(sc, sc, Offset.Zero)
            }) { with(h.paint) { front(at) } }
        }
    }

    private fun DrawScope.npMove(m: MoveCtx, now: Long) {
        val v = move
        val e = m.ends
        val np = (if (v.kind == MoveKind.T8) m.from else m.to) ?: return
        val s = np.scale
        val h = m.home
        val t0 = v.t0
        if (v.kind == MoveKind.T8) {
            val out = npEye(s.stdPpu, s.heightPx, Tok.motion_t8OutHinge, Tok.motion_t8OutEye)
            val oa = v.at(Part.NpOut, Prop.A, now)
            if (oa > 0f) {
                val t = quad(v.at(Part.NpOut, Prop.R, now), out, 0f, 0f).compose()
                layer(null, oa, layerPaint) { withTransform({ transform(t) }) { with(np.paint) { content(Content.Rest, now, null, "leave") } } }
            }
            val ua = v.at(Part.NpUnder, Prop.A, now)
            if (ua > 0f) {
                val inn = npEye(s.stdPpu, s.heightPx, Old.motion_t8InHinge, Tok.motion_t8InEye)
                val t = quad(v.at(Part.NpUnder, Prop.R, now), inn, v.at(Part.NpUnder, Prop.X, now), v.at(Part.NpUnder, Prop.Y, now)).compose()
                val to = m.to
                layer(null, ua, layerPaint) {
                    withTransform({ transform(t) }) {
                        when {
                            to != null -> restOf(to, now, null, "arrive")
                            h != null && e.npForm == NpForm.Front && h.paint.frontPane() != null -> with(h.paint) { front(now) }
                            h != null -> homeAt(h, now)
                            else -> Unit
                        }
                    }
                }
            }
            return
        }
        var pane = false
        when (e.npForm) {
            NpForm.Page -> {
                val la = v.at(Part.NpLeave, Prop.A, now)
                val from = m.from
                if (la > 0f) layer(null, la, layerPaint) { if (from != null) restOf(from, now, null, "leave") else if (h != null) homeAt(h, now) }
            }
            NpForm.Front -> {
                val p = h?.paint?.frontPane()
                if (h != null && p != null) {
                    if (now - t0 < Old.motion_t7PaneDraw.toLong()) {
                        paneAt(h, p, now)
                        val pa = Tok.motion_quickplayAlpha * v.at(Part.NpPeek, Prop.A, now)
                        if (pa > 0f) with(h.paint) { frontPeek(pa, Math.round(v.at(Part.NpEdge, Prop.X, now) + v.at(Part.NpEdge, Prop.Y, now)).toFloat(), now) }
                        pane = true
                    } else {
                        val la = v.at(Part.NpLeave, Prop.A, now)
                        if (la > 0f) {
                            val ey = npEye(s.stdPpu, s.heightPx, Old.motion_t7PaneHinge, Tok.motion_t7Eye)
                            val t = quad(v.at(Part.NpLeave, Prop.R, now), ey, 0f, 0f).compose()
                            val copy = t0 + Old.motion_t7PaneShift.toLong()
                            layer(null, la, layerPaint) { withTransform({ transform(t) }) { paneAt(h, p, copy) } }
                            pane = true
                        }
                    }
                }
            }
            NpForm.Ring -> if (h != null) {
                val x = Math.round(v.at(Part.NpLeave, Prop.X, now)).toFloat()
                val mx = Math.round(v.at(Part.NpMusic, Prop.X, now)).toFloat()
                val a = v.at(Part.NpLeave, Prop.A, now)
                val ma = v.at(Part.NpMusic, Prop.A, now)
                val bands = h.paint.npBands(h.off)
                if (bands.isEmpty()) {
                    if (a > 0f) layer(null, a, layerPaint) { translate(x, 0f) { homeAt(h, now) } }
                } else {
                    bands.forEachIndexed { i, b ->
                        val music = i == h.paint.npMusic
                        val ba = if (music) ma else a
                        if (ba > 0f) layer(null, ba, layerPaint) { clipRect(b.x0.toFloat(), b.y0.toFloat(), b.x1.toFloat(), b.y1.toFloat()) { translate(if (music) mx else x, 0f) { homeAt(h, now) } } }
                    }
                }
                val pk = (if (h.paint.quickplay) h.paint.peekAlpha(t0) else 0f) * v.at(Part.NpPeek, Prop.A, now)
                if (pk > 0f) with(h.paint) { peek(pk, now, null) }
            }
        }
        val ia = v.at(Part.NpIn, Prop.A, now)
        if (!pane && ia > 0f) {
            val ey = npEye(s.stdPpu, s.heightPx, Tok.motion_t7Hinge, Tok.motion_t7Eye)
            val t = quad(v.at(Part.NpIn, Prop.R, now), ey, 0f, 0f).compose()
            layer(null, ia, layerPaint) { withTransform({ transform(t) }) { with(np.paint) { content(Content.Rest, now, null, "arrive") } } }
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
            npMove(m, now)
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
                Content.Lift(to.paint.bandsAt(), Place(cs, cx + (1 - cs) * m.xa, (1 - cs) * m.ya, 0f, 0f), m.cut, m.tail, m.barX)
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
        if (to != null && pr != null && tc != null && pva > 0f) {
            val sc = v.at(Part.Pivots, Prop.S, now)
            val px = v.at(Part.Pivots, Prop.PX, now)
            val py = v.at(Part.Pivots, Prop.PY, now)
            layer(rec, pva, layerPaint) {
                placed(rec, Place(sc, px - px * sc + v.at(Part.Pivots, Prop.X, now), py - py * sc, 0f, 0f)) {
                    clipped(rec, pr.x0.toFloat(), pr.y0.toFloat(), pr.x1.toFloat(), pr.y1.toFloat()) {
                        tc.headerPart = ChromePart(hidden = true)
                        chrome(tc, now)
                        rec?.chrome("pivrow.chrome", tc, now, size.width.toInt())
                        tc.clearParts()
                    }
                }
            }
        }
        if (to != null) chromeOf(to, false, m, now, morphing, drawn, rec, pivrow)
        val hext = (px(s.board.pivotTop, s.stdPpu) - px(Tok.page_headerBaseline, s.stdPpu)).toFloat()
        if (mo != null && morphing && drawn) {
            val sc = v.at(Part.Word, Prop.S, now)
            val y = v.at(Part.Word, Prop.Y, now)
            val bottom = wordBottom(s, y, sc, progress(Prop.S, sc), mo.b.header)
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
