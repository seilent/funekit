package net.seilent.funekit.kit.stack

import androidx.compose.ui.geometry.Offset
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
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.motion.ContentForm
import net.seilent.funekit.kit.motion.MorphKind
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
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
import kotlin.math.max
import kotlin.math.min

private const val BANDS = 12
private const val NP_HALF = 2f
private const val NP_FX = 65536f
private const val SLIDE_X = 200f
private const val SLIDE_W = 480f

class Bands(val first: Int, val y: IntArray, val dy: IntArray, val dx: Float)

class Place(val s: Float, val dx: Float, val dy: Float, val xa: Float, val ya: Float)

sealed interface Content {
    data object Rest : Content

    class Zoom(val s: Float, val origin: Offset) : Content

    class Rows(val sy: Float, val ya: Float, val dx: Float) : Content

    class Frozen(val bands: Bands) : Content

    class Lift(val bands: Bands?, val at: Place, val cut: Float?) : Content
}

class WordSide(val text: String, val s: Float, val look: Float, val x: Float, val y: Float, val pivot: Int, val header: Boolean, val word: Word)

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

    fun tileRect(): Edges? = null

    fun DrawScope.tile(side: Float, cx: Float, cy: Float, alpha: Float, rec: DrawRecord?) {}

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
            placed(rec, c.at) {
                val b = c.bands
                if (b == null) {
                    clipped(rec, 0f, p.top.toFloat(), size.width, max(p.bottom, p.top + 1).toFloat()) {
                        with(p) { rest(now, rec, name) }
                        bar()
                    }
                } else {
                    clipped(rec, 0f, b.y.first().toFloat(), size.width, b.y.last().toFloat()) {
                        with(p) { bands(b, now, rec, name) }
                        bar()
                    }
                }
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

class Morph(val a: WordSide, val b: WordSide)

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
    val leaveLook: Float,
    val leaveBands: Bands? = null,
    val leaveWord: LeaveWord? = null,
    val fades: List<Fade> = emptyList(),
    val fadeAt: Long = 0L,
    val lift: Boolean = false,
    val xa: Float = 0f,
    val cut: Float? = null,
    val leaveAt: Place? = null,
    val leaveCut: Float? = null,
    val leaveAnchor: Float = 0f,
    val thumbAt: Long = 0L,
    val leaveThumb: Float = 0f,
    val arriveThumb: Float = 0f,
    val leaveDx: Float = 0f,
) {
    val scale: Scale? get() = (to ?: from)?.scale ?: home?.scale

    fun carry(old: MoveCtx, v: PageMove, at: Long): MoveCtx {
        fun cur(p: Part, q: Prop) = v.at(p, q, at)
        val ca = cur(Part.Content, Prop.A)
        val oldTo = old.to?.paint
        val cs = cur(Part.Content, Prop.S)
        val cx = Math.round(cur(Part.Content, Prop.X)).toFloat()
        val slid = old.kind == MoveKind.T2w && oldTo != null && ca > 0f
        val on = old.rows && oldTo != null && ca > 0f
        val bands = if (slid) leaveBands else if (!on || oldTo == null) null else if (old.lift) oldTo.bandsAt() else oldTo.bandsOf(cs, old.ya, cx)
        val at0 = leaveAt
        val held = if (slid && at0 != null) Place(1f, cx, 0f, at0.xa, at0.ya) else if (on && old.lift) Place(cs, cx, 0f, old.xa, old.ya) else null
        val anchor = if (held == null || slid) leaveAnchor else (bands?.let { anchorOf(it) } ?: oldTo?.top ?: 0).toFloat()
        val zw = old.zoom
        val zoomed = zw != null && cur(Part.Zoom, Prop.A) > 0f && cur(Part.Header, Prop.A) <= 0f
        val leave = if (zoomed && morph == null && zw != null) LeaveWord(zw, old.zoomPen, old.zoomBase, old.zoomAlpha) else null
        val om = old.morph
        val wasMorph = om != null && at < v.t0 + v.wordEnd
        val nm = morph
        val back = wasMorph && om != null && nm != null && nm.a.text == om.b.text && nm.b.text == om.a.text &&
            nm.a.header == om.b.header && nm.b.header == om.a.header
        val fades = if (om != null && wasMorph && !back) {
            val ws = cur(Part.Word, Prop.S)
            val wx = cur(Part.Word, Prop.X)
            val wy = cur(Part.Word, Prop.Y)
            listOf(
                Fade(om.a.word, ws, wx, wy, old.leaveLook * cur(Part.LeaveWord, Prop.A)),
                Fade(om.b.word, ws, wx, wy, om.b.look * cur(Part.Word, Prop.A)),
            )
        } else {
            emptyList()
        }
        return MoveCtx(
            ends.copy(morphBack = back), kind, home, from, to, rows, ya, word, wordLook, item, zoom, zoomPen, zoomBase, zoomAlpha, morph, leaveLook,
            bands, leave, fades, at, lift, xa, cut, held, if (held != null) old.cut else null, anchor,
            old.thumbAt, old.arriveThumb, old.leaveThumb, if (slid) cx else 0f,
        )
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

private fun anchorOf(b: Bands): Int = b.y[min(b.dy.size, BANDS) - 1]

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
    )
    val lit = from?.paint?.chrome?.lit(Chrome.HEADER, t.at) == true
    var word: Word? = null
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
            val sm = rolePx(Roles.homeItem, hs, hs.stdPpu).toFloat() / rolePx(Roles.pageHeader, ps, ps.stdPpu)
            val hx = c.headerRestX()
            val hy = headerBase(ps)
            val ix = if (t2) penPx(pen64(Tok.homeTextX, hs.stdPpu)) else Tok.homeTextX * hs.stdPpu
            val iy = Math.round((home.paint.baseline(item) - home.off) * hs.stdPpu).toFloat()
            e = e.copy(
                word = true,
                wordS0 = if (t2) sm else 1f, wordS1 = if (t2) 1f else sm,
                wordX0 = if (t2) ix else hx, wordX1 = if (t2) hx else ix,
                wordY0 = if (t2) iy else hy, wordY1 = if (t2) hy else iy,
            )
            word = p.paint.header
            look = if (lit && !t2) Tok.alpha_full else c.headerAlpha
        }
        if (t2) e = e.copy(zoom = false)
    }
    if (home != null && (k == MoveKind.T2 || k == MoveKind.T2n || k == MoveKind.T2w)) e = e.copy(peek0 = home.paint.peekAlpha(t.at))
    if (k == MoveKind.T2w) e = e.copy(zoom = false, slideX = SLIDE_X / SLIDE_W * s.widthPx)
    if (home != null && (k == MoveKind.T4h || k == MoveKind.T4n)) e = e.copy(peek1 = if (home.paint.quickplay) Tok.qpPeekAlpha else 0f)
    var morph: Morph? = null
    var leaveLook = 0f
    if ((k == MoveKind.T3 || k == MoveKind.T4) && forms.wordMorph && from != null && to != null) {
        val push = k == MoveKind.T3
        val a = from.paint.wordSide(push)
        val b = to.paint.wordSide(!push)
        if (a != null && b != null && a.text != b.text) {
            morph = Morph(a, b)
            leaveLook = if (lit && a.header) Tok.alpha_full else a.look
            e = e.copy(
                morph = true, morphKind = MorphKind.Pivot,
                morphS0 = a.s, morphS1 = b.s,
                morphX0 = a.x, morphX1 = b.x, morphY0 = a.y, morphY1 = b.y,
                morphHeaderOut = a.header, morphHeaderIn = b.header,
                zoom = e.zoom && !b.header,
            )
        }
    }
    val rows = to != null && to.paint.kind != PageKind.Grid && formOf(to.paint.kind, forms) != ContentForm.Rest &&
        (k == MoveKind.T2 || k == MoveKind.T2n || k == MoveKind.T3)
    val ts = to?.scale
    val lift = rows && ts != null && (k == MoveKind.T2 || k == MoveKind.T2n)
    val ya = when {
        ts == null -> 0f
        lift -> ts.heightPx - stdPx(Old.motion_rowsYa, ts)
        else -> ts.heightPx - Old.motion_rowsYa * ts.stdPpu
    }
    val xa = if (lift && ts != null) stdPx(Tok.hubEdge, ts) else 0f
    if (lift && ts != null) e = e.copy(rowsDx = stdPx(Tok.motion_rowsDx, ts))
    val cut = if (lift && ts != null && ts.navOn) px(ts.stdContentBottom, ts.stdPpu).toFloat() else null
    val zoom = if (e.zoom && toChrome != null) toChrome.header else null
    val leaving = if ((k == MoveKind.T4h || k == MoveKind.T4n) && to == null && from != null && from.paint.kind != PageKind.Grid) from else null
    val leaveBands = leaving?.paint?.bandsAt()
    val anchor = if (leaving == null) 0f else (leaveBands?.let { anchorOf(it) } ?: leaving.paint.top).toFloat()
    val leaveAt = leaving?.let { Place(1f, 0f, 0f, stdPx(Tok.hubEdge, it.scale), anchor) }
    if (leaving != null) e = e.copy(leaveRows = true, pxU = leaving.scale.ppu)
    return MoveCtx(
        e, k, home, from, to, rows, ya, word, look, item,
        zoom, toChrome?.headerRestX() ?: 0f, to?.let { headerBase(it.scale) } ?: 0f, toChrome?.headerAlpha ?: 0f,
        morph, leaveLook, leaveBands = leaveBands, lift = lift, xa = xa, cut = cut, leaveAt = leaveAt, leaveAnchor = anchor,
        thumbAt = t.at, leaveThumb = from?.paint?.thumbFade(t.at) ?: 0f, arriveThumb = to?.paint?.thumbFade(t.at) ?: 0f,
    )
}

class MovePaint(private val move: PageMove) {
    var ctx: MoveCtx = MoveCtx.None
        private set
    var npForm: NpForm = NpForm.Page
    private val layerPaint = Paint()

    fun ends(t: Turn<*>, home: HomeSide?, from: PageSide?, to: PageSide?, forms: MoveForms): MoveEnds {
        val c = moveCtx(t, home, from, to, forms, npForm)
        ctx = if (t.retarget) c.carry(ctx, move, t.at) else c
        return ctx.ends
    }

    private fun DrawScope.spriteAt(rec: DrawRecord?, name: String, wd: Word, sc: Float, x: Float, y: Float, a: Float, bottom: Int) {
        sprite(wd, sc, x, y, a, bottom)
        if (rec != null) rec.clip(0f, 0f, size.width, bottom.coerceIn(0, size.height.toInt()).toFloat()) { rec.move(x, y) { rec.zoom(sc, 0f, 0f) { rec.word(name, wd, 0f, 0f, a) } } }
    }

    private fun DrawScope.chromeOf(side: PageSide, leave: Boolean, m: MoveCtx, now: Long, morphing: Boolean, drawn: Boolean, rec: DrawRecord?) {
        val c = side.paint.chrome ?: return
        val v = move
        val k = v.kind
        var ha = v.at(if (leave) Part.LeaveHeader else Part.Header, Prop.A, now)
        if (leave && m.word != null && (k == MoveKind.T4h || k == MoveKind.T4m)) ha = 0f
        var hp = ChromePart(alpha = ha, hidden = ha <= 0f)
        val pp = if (leave) Part.LeavePivots else Part.Pivots
        val pa = v.at(pp, Prop.A, now)
        val pv = ChromePart(Math.round(v.at(pp, Prop.X, now)).toFloat(), Math.round(v.at(pp, Prop.Y, now)), pa, pa <= 0f)
        val mo = m.morph
        val sd = if (mo == null) null else if (leave) mo.a else mo.b
        val ma = v.at(if (leave) Part.LeaveWord else Part.Word, Prop.A, now)
        if (morphing && sd != null && sd.header) {
            val a = if (drawn) 0f else ma
            hp = ChromePart(alpha = a, hidden = a <= 0f)
        }
        c.headerPart = hp
        for (i in 0 until c.count) {
            var q = pv
            if (sd != null && i == sd.pivot && (morphing || leave)) {
                val a = if (morphing && !drawn) ma else 0f
                q = ChromePart(alpha = a, hidden = a <= 0f)
            }
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
        val from = m.from
        val to = m.to
        val leaveG = from?.paint?.grounded == true
        val arriveG = to?.paint?.grounded == true
        var lg = if (leaveG) v.at(Part.LeaveGround, Prop.A, now) else 0f
        val ag = if (!arriveG || (leaveG && !v.has(Part.Ground, Prop.A))) 0f else v.at(Part.Ground, Prop.A, now)
        if (leaveG && arriveG && !v.has(Part.Ground, Prop.A)) lg = 1f
        val grounder = if (arriveG) to else if (leaveG) from else null
        if (grounder != null) leaving(rec, if (grounder === from) m.leaveDx else 0f) { with(grounder.paint) { ground(lg, ag) } }
        val origin = Offset(Tok.motion_contentX * size.width, Tok.motion_contentY * size.height)
        val la = v.at(Part.Leave, Prop.A, now)
        if (from != null && la > 0f) {
            val lb = m.leaveBands
            val held = m.leaveAt
            val c = when {
                held != null -> Content.Lift(lb, leaveAt(held, m.leaveAnchor, now), m.leaveCut)
                lb != null -> Content.Frozen(lb)
                else -> Content.Zoom(v.at(Part.Leave, Prop.S, now), origin)
            }
            layer(rec, la, layerPaint) { with(from.paint) { content(c, now, rec, "leave", m.leaveThumb) } }
        }
        val mo = m.morph
        val morphing = mo != null && now < v.t0 + v.wordEnd
        val leaveA = if (mo != null) m.leaveLook * v.at(Part.LeaveWord, Prop.A, now) else 0f
        val arriveA = if (mo != null) mo.b.look * v.at(Part.Word, Prop.A, now) else 0f
        val leaveSprite = morphing && leaveA > 0f
        val arriveSprite = morphing && arriveA > 0f
        if (from != null) leaving(rec, m.leaveDx) { chromeOf(from, true, m, now, morphing, leaveSprite, rec) }
        val hs = m.home
        if (hs != null && (from == null || to == null)) {
            val look = HomeMove(v.at(Part.Home, Prop.A, now), if (m.word != null) m.item else -1, if (to == null) v.at(Part.Look, Prop.A, now) else 0f, v.at(Part.Sub, Prop.A, now), hs.off)
            with(hs.paint) {
                peek(v.at(Part.Peek, Prop.A, now), now, rec)
                home(look, now, rec)
            }
        }
        val ca = v.at(Part.Content, Prop.A, now)
        if (to != null && ca > 0f) {
            val cs = v.at(Part.Content, Prop.S, now)
            val cx = Math.round(v.at(Part.Content, Prop.X, now)).toFloat()
            val c = when {
                m.lift -> Content.Lift(to.paint.bandsAt(), Place(cs, cx, 0f, m.xa, m.ya), m.cut)
                m.rows -> Content.Rows(cs, m.ya, cx)
                else -> Content.Zoom(cs, origin)
            }
            layer(rec, ca, layerPaint) { with(to.paint) { content(c, now, rec, "arrive", m.arriveThumb) } }
        }
        val tileSide = if (to?.paint?.tileRect() != null) to else if (from?.paint?.tileRect() != null) from else null
        val ta = v.at(Part.Tile, Prop.A, now)
        if (tileSide != null && ta > 0f) with(tileSide.paint) { tile(v.at(Part.Tile, Prop.S, now), v.at(Part.Tile, Prop.X, now), v.at(Part.Tile, Prop.Y, now), ta, rec) }
        if (to != null) chromeOf(to, false, m, now, morphing, arriveSprite, rec)
        val hext = (px(s.board.pivotTop, s.stdPpu) - px(Tok.page_headerBaseline, s.stdPpu)).toFloat()
        if (mo != null && morphing) {
            val sc = v.at(Part.Word, Prop.S, now)
            val x = v.at(Part.Word, Prop.X, now)
            val y = v.at(Part.Word, Prop.Y, now)
            val l = v.span(Part.Word, Prop.S)
            val p = if (l != null && l.second != l.first) ((sc - l.first) / (l.second - l.first)).coerceIn(0f, 1f) else 1f
            val bottom = wordBottom(s, y, sc, p, mo.b.header)
            if (leaveSprite) spriteAt(rec, "morph.leave", mo.a.word, sc, x, y, leaveA, bottom)
            if (arriveSprite) spriteAt(rec, "morph.arrive", mo.b.word, sc, x, y, arriveA, bottom)
        }
        val span = max(1, Tok.motion_leave.toInt())
        val e = (now - m.fadeAt).coerceIn(0L, span.toLong())
        for ((i, f) in m.fades.withIndex()) {
            val a = f.a * (1 - e.toFloat() / span)
            if (a > 0f) spriteAt(rec, if (i == 0) "fade.leave" else "fade.arrive", f.word, f.s, f.x, f.y, a, Math.round(f.y + hext * f.s))
        }
        val wd = m.word
        if (mo == null && wd != null && v.at(Part.Word, Prop.A, now) > 0f) {
            val sc = v.at(Part.Word, Prop.S, now)
            val y = v.at(Part.Word, Prop.Y, now)
            fun done(q: Prop, value: Float): Float {
                val l = v.span(Part.Word, q)
                return if (l != null && l.second != l.first) ((value - l.first) / (l.second - l.first)).coerceIn(0f, 1f) else 1f
            }
            val p = min(done(Prop.S, sc), done(Prop.Y, y))
            val a = m.wordLook * (1 - v.at(Part.Look, Prop.A, now))
            spriteAt(rec, "word", wd, sc, v.at(Part.Word, Prop.X, now), y, a, wordBottom(s, y, sc, p, k == MoveKind.T2))
        }
        val zx = Tok.motion_headerZoomX * s.ppu
        val zy = Tok.motion_headerZoomY * s.ppu
        val lw = m.leaveWord
        if (mo == null && lw != null && v.at(Part.LeaveWord, Prop.A, now) > 0f) {
            val sc = v.at(Part.LeaveWord, Prop.S, now)
            val y = zy + (lw.base - zy) * sc
            spriteAt(rec, "leave.zoom", lw.word, sc, zx + (lw.pen - zx) * sc, y, lw.alpha * v.at(Part.LeaveWord, Prop.A, now), Math.round(y + hext * sc))
        }
        val zw = m.zoom
        if (zw != null && v.at(Part.Zoom, Prop.A, now) > 0f && v.at(Part.Header, Prop.A, now) <= 0f) {
            val sc = v.at(Part.Zoom, Prop.S, now)
            val y = zy + (m.zoomBase - zy) * sc
            spriteAt(rec, "zoom", zw, sc, zx + (m.zoomPen - zx) * sc, y, m.zoomAlpha, Math.round(y + hext * sc))
        }
        if (status != null) statusOf(status, m, now)
    }
}
