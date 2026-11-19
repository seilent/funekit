package net.seilent.funekit.screens.music.nowplaying

import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptText
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.arc
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.np.NP_BACK
import net.seilent.funekit.kit.np.NP_MARK
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.np.NpRect
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.ring
import net.seilent.funekit.kit.ringPress
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.statusRow
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val HALF = 2
private const val HALF_TURN = 180.0

fun npFromRole(s: NpLayout): Role = keptText(Roles.sectionLabel, s.s)

fun npNextRole(s: NpLayout, variant: Boolean): Role = keptText(if (variant) Roles.npCount else Roles.npViewNext(s.s.board), s.s)

fun npTimeRole(s: NpLayout): Role = keptText(Roles.npViewTime, s.s)

class NpInk(val shown: NpShown, val artist: Word, val album: Word, val title: Word, val next: List<Word>, val from: Word)

class NpMeasure(val g: NpLayout, val measurer: TextMeasurer, val fonts: FontFamily.Resolver, val density: Density, val covers: Covers?) {
    fun word(t: String, r: Role): Word = measurer.word(t, r, roleStyle(r, g.s, density, Ink.white, ppu = g.p), fonts, density, covers)

    fun ink(n: NpShown, variant: Boolean): NpInk {
        val nr = npNextRole(g, variant)
        return NpInk(
            n,
            word(n.artist, Roles.npViewArtist),
            word(n.album, Roles.npViewAlbum),
            word(n.title, Roles.npViewTitle),
            (0 until g.nextLines).map { word(n.nextLine(it), nr) },
            word(n.from, npFromRole(g)),
        )
    }

    fun time(t: NpTimes): NpTimeInk {
        val r = npTimeRole(g)
        return NpTimeInk(t, word(t.left, r), t.right?.let { word(it, r) })
    }
}

data class NpTimes(val left: String, val right: String?, val fill: Int, val shown: Boolean)

class NpTimeInk(val t: NpTimes, val left: Word, val right: Word?)

fun npTimes(g: NpLayout, m: NowPlayingModel, now: Long, nanos: Long): NpTimes {
    val n = m.shownAt(now)
    val dur = n.durationMs
    if (m.variant && dur <= 0) return NpTimes("", null, 0, false)
    val tick = now - Math.floorMod(now, NowPlayingModel.TICK)
    val pos = if (m.slideHeld(now)) m.heldPos else m.position(tick, nanos - (now - tick) * NANOS_PER_MS)
    val w = g.bar.w
    val fill = if (dur > 0) (pos.coerceIn(0L, dur) * w / dur).toInt() else 0
    return NpTimes(npTime(pos), if (dur > 0) npRemaining(dur, pos) else null, fill, true)
}

private const val NANOS_PER_MS = 1000000L

fun npUptime(): Long = SystemClock.uptimeMillis()

fun npNanosAt(at: Long): Long = SystemClock.elapsedRealtimeNanos() - (SystemClock.uptimeMillis() - at) * NANOS_PER_MS

fun timeRight64(g: NpLayout, left: Float, right: Float, durationMs: Long): Int {
    val pair = pen(left) + g.timePair64 + pen(right)
    val edge = if (durationMs >= NP_HOUR_MS || pair > g.art64) g.timeWideRight64 else g.timeRight64
    return edge - pen(right)
}

private fun pen(w: Float): Int = (w * PEN).roundToInt()

private const val PEN = 64f

data class NpRingLook(val glyph: String, val lit: Boolean, val focused: Boolean, val press: Float, val idle: Float)

data class NpRingsLook(val rings: List<NpRingLook>)

data class NpBackLook(val hidden: Boolean, val press: Float)

data class NpMarkLook(val shown: Boolean, val press: Float)

data class NpTextLook(val shown: NpShown, val covers: List<NpCover>)

data class NpCover(val dx: Int, val shown: Boolean)

fun npRingsLook(m: NowPlayingModel, now: Long, hideFocus: Boolean): NpRingsLook {
    m.revision
    val idle = m.idleAlpha(now)
    return NpRingsLook(
        (0 until m.rings()).map { i ->
            val dead = m.variant && i > 0
            val focused = !dead && !hideFocus && !m.titleFocus && m.ring == i
            val lit = if (dead) false else if (m.variant && i == 0) focused else m.on(i)
            NpRingLook(m.glyph(i), lit, focused, m.press[i].at(now.toFloat()).lit, if (focused) 1f else idle)
        },
    )
}

fun npBackLook(m: NowPlayingModel, now: Long, hideFocus: Boolean): NpBackLook {
    m.revision
    return NpBackLook(hideFocus, m.press[NP_BACK].at(now.toFloat()).lit)
}

fun npMarkLook(m: NowPlayingModel, now: Long): NpMarkLook {
    m.revision
    return NpMarkLook(m.markAt(now) && !m.variant, m.press[NP_MARK].at(now.toFloat()).factor)
}

fun npTextLook(m: NowPlayingModel, g: NpLayout, now: Long): NpTextLook {
    m.revision
    return NpTextLook(m.shownAt(now), npCovers(m, g, now))
}

fun npCovers(m: NowPlayingModel, g: NpLayout, now: Long): List<NpCover> {
    if (!m.slideBusy()) return emptyList()
    val sl = m.slide
    val o = Math.round(m.slideOffset(now) * g.p)
    val w = px(g.s.w, g.p)
    val t = sl.target
    if (t != null) return listOf(NpCover(o, m.shown.track == t), NpCover(o - sl.dir * w, m.shown.track == sl.start))
    val out = mutableListOf(NpCover(o, true))
    if (sl.next != null) out += NpCover(o + w, false)
    if (sl.prev != null) out += NpCover(o - w, false)
    return out
}

fun npStatusAlpha(m: NowPlayingModel, now: Long): Float {
    m.revision
    return m.idleAlpha(now)
}

private fun glyphOf(name: String): Glyph = when (name) {
    NowPlayingModel.SHUFFLE -> Glyphs.shuffle
    NowPlayingModel.HEART -> Glyphs.heart
    NowPlayingModel.PLAY -> Glyphs.play
    NowPlayingModel.PAUSE -> Glyphs.pause
    else -> Glyphs.repeat
}

fun DrawScope.npBackRing(g: NpLayout, l: NpBackLook, alpha: Float, rec: DrawRecord?) {
    val c = Offset(g.backCx.toFloat(), g.backCy.toFloat())
    val a = (if (l.hidden) Tok.alpha_mid else Tok.alpha_full) * alpha
    if (a <= 0f) return
    val d = g.backSize.toFloat()
    val stroke = (if (l.hidden) g.backStrokeIdle else g.backStroke).toFloat()
    clipRect(0f, 0f, g.backClip.x1.toFloat(), g.backClip.y1.toFloat()) {
        ringPress(g.backSize, stroke.toInt(), g.backCx, g.backCy, l.press, alpha)
        ring(c, d, stroke, Ink.white, a)
        glyph(Glyphs.back, c, g.backGlyph.toFloat(), Ink.white, a)
    }
    rec?.ring("np.back", c, d, a)
    rec?.glyphs("np.back.glyph", listOf(Glyphs.back), c, g.backGlyph.toFloat(), a, d, stroke)
}

fun DrawScope.npTop(g: NpLayout, ink: NpInk, variant: Boolean, alpha: Float, rec: DrawRecord?) {
    val c = g.textClip
    val x = penPx(g.textX64)
    clipRect(c.x0.toFloat(), 0f, c.x1.toFloat(), size.height) {
        if (variant) {
            val fr = npFromRole(g)
            word(ink.from, x, g.fromY.toFloat(), Ink.white, fr.alpha * alpha)
            rec?.word("np.from", ink.from, x, g.fromY.toFloat(), fr.alpha * alpha)
        }
        val aa = Roles.npViewArtist.alpha * alpha
        val ba = Roles.npViewAlbum.alpha * alpha
        word(ink.artist, x, g.artistY.toFloat(), Ink.white, aa)
        word(ink.album, x, g.albumY.toFloat(), Ink.white, ba)
        rec?.word("np.artist", ink.artist, x, g.artistY.toFloat(), aa)
        rec?.word("np.album", ink.album, x, g.albumY.toFloat(), ba)
    }
}

fun DrawScope.npArt(g: NpLayout, art: ImageBitmap?, variant: Boolean, alpha: Float, covers: List<NpCover> = emptyList()) {
    val aa = alpha
    if (aa <= 0f) return
    val side = g.artSide
    if (variant && art == null) {
        glyph(Glyphs.bluetooth, Offset((g.artX + side / HALF).toFloat(), (g.artY + side / HALF).toFloat()), g.btGlyph.toFloat(), Ink.white, aa)
        return
    }
    if (covers.isNotEmpty()) {
        for (c in covers) {
            val img = if (c.shown) art else null
            if (img != null && img.width == side) {
                drawImage(img, IntOffset.Zero, IntSize(side, side), IntOffset(g.artX + c.dx, g.artY), IntSize(side, side), aa)
            } else {
                drawRect(Ink.tile, Offset((g.artX + c.dx).toFloat(), g.artY.toFloat()), Size(side.toFloat(), side.toFloat()), aa)
            }
        }
        return
    }
    if (art != null && art.width == side) {
        drawImage(art, IntOffset.Zero, IntSize(side, side), IntOffset(g.artX, g.artY), IntSize(side, side), aa)
    } else {
        drawRect(Ink.tile, Offset(g.artX.toFloat(), g.artY.toFloat()), Size(side.toFloat(), side.toFloat()), aa)
    }
}

fun DrawScope.npMark(g: NpLayout, l: NpMarkLook, alpha: Float, rec: DrawRecord?) {
    if (!l.shown) return
    val a = Tok.alpha_mid * alpha
    if (a <= 0f) return
    val c = Offset(g.markX.toFloat(), g.markY.toFloat())
    val s = g.ringD.toFloat()
    val stroke = g.ringStroke.toFloat()
    ring(c, s, stroke, Ink.white, a, l.press)
    val mic = (s.toInt() / HALF).toFloat()
    glyph(Glyphs.micHead, c, mic, Ink.white, a, l.press)
    glyph(Glyphs.micBase, c, mic, Ink.white, a, l.press)
    rec?.ring("np.mark", c, s * l.press, a)
}

fun DrawScope.npTimeBand(g: NpLayout, t: NpTimeInk, durationMs: Long, accent: Color, alpha: Float, rec: DrawRecord?) {
    if (!t.t.shown) return
    val a = alpha
    val b = g.bar
    drawRect(Ink.white, Offset(b.x0.toFloat(), b.y0.toFloat()), Size(b.w.toFloat(), b.h.toFloat()), Tok.npBarTrack * a)
    if (t.t.fill > 0) drawRect(accent, Offset(b.x0.toFloat(), b.y0.toFloat()), Size(t.t.fill.toFloat(), b.h.toFloat()), a)
    val r = npTimeRole(g)
    val ta = r.alpha * a
    val x = penPx(g.timeX64)
    word(t.left, x, g.timeBase.toFloat(), Ink.white, ta)
    rec?.word("np.time.left", t.left, x, g.timeBase.toFloat(), ta)
    val right = t.right ?: return
    val rx = timeRight64(g, t.left.width, right.width, durationMs)
    val px = penPx(rx)
    word(right, px, g.timeBase.toFloat(), Ink.white, ta)
    rec?.word("np.time.right", right, px, g.timeBase.toFloat(), ta)
}

fun DrawScope.npTitle(g: NpLayout, ink: NpInk, variant: Boolean, alpha: Float, rec: DrawRecord?) {
    val a = Roles.npViewTitle.alpha * alpha
    val c = g.textClip
    clipRect(c.x0.toFloat(), 0f, c.x1.toFloat(), size.height) {
        val x = penPx(g.textX64)
        word(ink.title, x, g.titleY.toFloat(), Ink.white, a)
        rec?.word("np.title", ink.title, x, g.titleY.toFloat(), a)
        val nr = npNextRole(g, variant)
        val na = nr.alpha * alpha
        val nx = penPx(g.nextX64)
        ink.next.forEachIndexed { i, w ->
            word(w, nx, g.nextBase(i).toFloat(), Ink.white, na)
            rec?.word("np.next$i", w, nx, g.nextBase(i).toFloat(), na)
        }
    }
}

fun DrawScope.npRings(g: NpLayout, l: NpRingsLook, alpha: Float, rec: DrawRecord?) {
    val d = g.ringD.toFloat()
    val cy = g.ringCy.toFloat()
    l.rings.forEachIndexed { i, r ->
        val c = Offset(g.ringCx(i).toFloat(), cy)
        val stroke = (if (r.focused) g.ringStrokeFocused else g.ringStroke).toFloat()
        val ringA = r.idle * alpha
        val ga = (if (r.lit) Tok.alpha_full else Tok.alpha_mid) * ringA
        ringPress(g.ringD, stroke.toInt(), g.ringCx(i), g.ringCy, r.press, alpha)
        val one = r.glyph == NowPlayingModel.REPEAT_ONE
        if (one) {
            arc(c, d, stroke, Tok.ringArcStart, Tok.npRepeatOneSweep, Ink.white, ringA)
            val ang = Tok.npRepeatOneAt * Math.PI / HALF_TURN
            val rr = Tok.npRepeatOneR * g.p
            val oc = Offset(c.x + Math.round(rr * sin(ang)).toFloat(), c.y - Math.round(rr * cos(ang)).toFloat())
            glyph(Glyphs.one, oc, g.repeatOne.toFloat(), Ink.white, ga)
        } else {
            ring(c, d, stroke, Ink.white, ringA)
        }
        val gs = (d.toInt() / HALF).toFloat()
        glyph(glyphOf(r.glyph), c, gs, Ink.white, ga)
        rec?.ring("np.ring$i", c, d, ringA)
        rec?.glyphs("np.ring$i.glyph", listOf(glyphOf(r.glyph)), c, gs, ga, d, stroke)
    }
}

class NpFrame(
    val text: NpTextLook,
    val back: NpBackLook,
    val mark: NpMarkLook,
    val rings: NpRingsLook,
    val status: Float,
)

fun npFrame(m: NowPlayingModel, g: NpLayout, now: Long, hideFocus: Boolean): NpFrame =
    NpFrame(npTextLook(m, g, now), npBackLook(m, now, hideFocus), npMarkLook(m, now), npRingsLook(m, now, hideFocus), npStatusAlpha(m, now))

fun DrawScope.npPage(
    g: NpLayout,
    f: NpFrame,
    ink: NpInk,
    time: NpTimeInk,
    art: ImageBitmap?,
    status: StatusInk?,
    accent: Color,
    accentOff: Boolean,
    variant: Boolean,
    rec: DrawRecord?,
) = npParts(g, f, ink, time, art, status, accent, accentOff, variant, rec, NP_PARTS)

fun DrawScope.npParts(
    g: NpLayout,
    f: NpFrame,
    ink: NpInk,
    time: NpTimeInk,
    art: ImageBitmap?,
    status: StatusInk?,
    accent: Color,
    accentOff: Boolean,
    variant: Boolean,
    rec: DrawRecord?,
    parts: Set<NpPart>,
) {
    if (NpPart.Back in parts) npBackRing(g, f.back, 1f, rec)
    if (NpPart.Top in parts) npTop(g, ink, variant, 1f, rec)
    if (NpPart.Art in parts) npArt(g, art, variant, 1f, f.text.covers)
    if (NpPart.Mark in parts) npMark(g, f.mark, 1f, rec)
    if (NpPart.Times in parts) npTimeBand(g, time, f.text.shown.durationMs, accent, 1f, rec)
    if (NpPart.Title in parts) npTitle(g, ink, variant, 1f, rec)
    if (NpPart.Rings in parts) npRings(g, f.rings, 1f, rec)
    if (status != null && NpPart.Status in parts) statusRow(status, accent, accentOff, f.status)
}

class NpInks(private val measure: NpMeasure, private val m: NowPlayingModel) {
    private var key: NpShown? = null
    private var keyVariant = false
    private var held: NpInk? = null
    private var times: NpTimes? = null
    private var timeInk: NpTimeInk? = null

    fun ink(now: Long): NpInk {
        val n = m.shownAt(now)
        val h = held
        if (h != null && key == n && keyVariant == m.variant) return h
        return measure.ink(n, m.variant).also {
            key = n
            keyVariant = m.variant
            held = it
        }
    }

    fun prime(now: Long) {
        ink(now)
        time(now, npNanosAt(now))
    }

    fun shownInk(now: Long): NpInk = held ?: ink(now)

    fun shownTime(now: Long): NpTimeInk = timeInk ?: time(now, npNanosAt(now))

    fun time(now: Long, nanos: Long): NpTimeInk {
        val t = npTimes(measure.g, m, now, nanos)
        val h = timeInk
        if (h != null && times == t) return h
        return measure.time(t).also {
            times = t
            timeInk = it
        }
    }
}

class NpPaint(
    private val g: NpLayout,
    private val m: NowPlayingModel,
    private val halves: List<Pair<NpHalf, NpInks>>,
    private val art: () -> ImageBitmap?,
    private val accent: Color,
    private val hideFocus: () -> Boolean = { true },
) : PagePaint {
    override val nowPlaying: Boolean = true

    override val chrome: Chrome? = null
    override val header: Word? = null
    override val item = -1
    override val grounded = false
    override val kind = PageKind.Plain
    override val top = 0
    override val bottom = g.s.heightPx
    override val count = 0

    override fun rowY(i: Int) = 0

    override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) = Unit

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) = page(now, rec) { it }

    override fun npRing(): Edges {
        val h = halves.map { it.first }.holding(NpPart.Back)
        val r = h.g.ringRect
        val y = h.y - h.dy
        return Edges(r.x0 + h.x, r.y0 + y, r.x1 + h.x, r.y1 + y)
    }

    override fun DrawScope.npPart(part: Part, now: Long, rec: DrawRecord?) {
        page(now, rec, { h -> if (part == Part.NpRing) h.g.ringRect else h.g.partRects(m.variant, false)[part] }) {
            if (part == Part.NpRing) it intersect setOf(NpPart.Back) else it - NpPart.Back
        }
    }

    private fun DrawScope.page(
        now: Long,
        rec: DrawRecord?,
        clip: ((NpHalf) -> NpRect?)? = null,
        parts: (Set<NpPart>) -> Set<NpPart>,
    ) {
        for ((h, k) in halves) {
            val shown = parts(h.parts)
            if (shown.isEmpty()) continue
            val r = if (clip == null) null else clip(h) ?: continue
            if (r != null && (r.w <= 0 || r.h <= 0)) continue
            npHalf(h) {
                val draw: DrawScope.() -> Unit = {
                    val f = npFrame(m, h.g, now, hideFocus())
                    npParts(h.g, f, k.shownInk(now), k.shownTime(now), art(), null, accent, true, m.variant, rec, shown)
                }
                if (r == null) draw() else clipRect(r.x0.toFloat(), r.y0.toFloat(), r.x1.toFloat(), r.y1.toFloat(), block = draw)
            }
        }
    }
}
