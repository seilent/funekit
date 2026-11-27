package net.seilent.funekit.kit.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.gen.Tok
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PRESS_STEPS = 16f
private const val LOG_STEPS = 10f
private const val MS_PER_S = 1000f

internal var motionSeq = 0

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

class Leg(
    val from: Float,
    val to: Float,
    val delay: Float,
    val dur: Float,
    val curve: Curve = Curves.linear,
) {
    val end: Float get() = delay + dur

    fun at(e: Float): Float {
        if (dur <= 0f) return if (e >= delay) to else from
        if (e <= delay) return from
        if (e >= end) return to
        return lerp(from, to, curve.at((e - delay) / dur))
    }
}

class Sheet(val scrim: Float, val dy: Float, val alpha: Float)

class SheetLook(val rise: Float, val alpha: Float, val scrim: Float)

class JumpLook(val dyU: Float, val alpha: Float, val page: Float)

private fun prog(e: Long, d: Float): Float = if (d <= 0f) 1f else (e.toFloat() / d).coerceIn(0f, 1f)

fun sheetIn(e: Long): SheetLook = SheetLook(
    Curves.easeOut.at(prog(e, Tok.motion_sheet)),
    Curves.easeOut.at(prog(e, Tok.motion_sheetFade)),
    Curves.easeOut.at(prog(e, Tok.motion_sheetScrimIn)),
)

private fun share(t: Float, by: Float): Float = (t * by).toInt().toFloat()

fun sheetOut(e: Long, from: SheetLook): SheetLook = SheetLook(
    from.rise * (1 - prog(e, share(Tok.motion_sheetClose, from.rise))),
    from.alpha * (1 - Curves.smoothStep.at(prog(e, share(Tok.motion_sheetCloseFade, from.alpha)))),
    from.scrim * (1 - Curves.easeOut.at(prog(e, share(Tok.motion_sheetScrimOut, from.scrim)))),
)

fun sheetInMs(): Int = maxOf(Tok.motion_sheet, Tok.motion_sheetFade, Tok.motion_sheetScrimIn).toInt()

fun sheetOutMs(from: SheetLook): Int =
    maxOf(share(Tok.motion_sheetClose, from.rise), share(Tok.motion_sheetCloseFade, from.alpha), share(Tok.motion_sheetScrimOut, from.scrim)).toInt()

fun jumpInMs(): Int = max(Tok.motion_jumpPageOut, Tok.motion_jumpInDelay + max(Tok.motion_jumpRiseIn, Tok.motion_jumpIn)).toInt()

fun jumpOutMs(): Int = maxOf(Tok.motion_jumpCloseIn, Tok.motion_jumpOut, Tok.motion_jumpPageInDelay + Tok.motion_jumpPageIn).toInt()

fun jumpIn(e: Long): JumpLook {
    val r = e - Tok.motion_jumpInDelay.toLong()
    return JumpLook(
        (1 - Curves.easeOut.at(prog(r, Tok.motion_jumpRiseIn))) * Tok.jumpPitch,
        Curves.easeOut.at(prog(r, Tok.motion_jumpIn)),
        1 - Curves.expOut6.at(prog(e, Tok.motion_jumpPageOut)),
    )
}

fun jumpOut(e: Long, from: JumpLook): JumpLook = JumpLook(
    lerp(from.dyU, -Tok.motion_jumpCloseRise, Curves.easeOut.at(prog(e, Tok.motion_jumpCloseIn))),
    from.alpha * (1 - Curves.cubicOut.at(prog(e, Tok.motion_jumpOut))),
    lerp(from.page, 1f, prog(e - Tok.motion_jumpPageInDelay.toLong(), Tok.motion_jumpPageIn)),
)

class Modal(val scrim: Float, val alpha: Float, val scale: Float)

enum class OverKind { Sheet, Dlg, Jump, Volume, Adjust }

interface OverHud {
    val hiding: Boolean

    fun opening(now: Long): Boolean

    fun closing(now: Long): Boolean

    fun visible(now: Long): Boolean

    fun wakeAt(now: Long): Long?

    fun hide(now: Long)

    fun dismiss()
}

class Overlay(val kind: OverKind, val hud: OverHud? = null, private val commit: () -> Unit = {}) {
    var start = 0L
        private set
    private var closed = false
    val closing: Boolean get() = hud?.hiding ?: closed
    var risen = -1L
        private set
    private var backMs = -1
    private var backFrom = 1f
    private var backO = 1f
    private var sheetFrom = SheetLook(1f, 1f, 1f)
    private var jumpFrom = JumpLook(0f, 1f, 0f)
    var lead = 0L
    var back: (() -> Boolean)? = null
    var revision by mutableIntStateOf(0)
        private set
    internal var movedSeq = 0
        private set

    internal fun moved() {
        movedSeq = ++motionSeq
    }

    fun open(now: Long) {
        start = now
        closed = false
        backMs = -1
        risen = -1L
        revision++
        moved()
    }

    fun rise(now: Long) {
        if (risen < 0 && !closing && !moving(now)) risen = max(now, start + dur())
    }

    fun dur(): Int {
        if (hud != null) return 0
        if (kind == OverKind.Sheet) return if (closing) sheetOutMs(sheetFrom) else sheetInMs()
        if (closing && backMs >= 0) return backMs
        return when (kind) {
            OverKind.Dlg -> (if (closing) Tok.motion_dialogHide else Tok.motion_dialog).toInt()
            else -> lead.toInt() + if (closing) jumpOutMs() else jumpInMs()
        }
    }

    private fun progress(e: Long, d: Int): Float = if (d <= 0) 1f else (e.toFloat() / d).coerceIn(0f, 1f)

    fun close(now: Long) {
        val h = hud
        if (h != null) {
            if (h.hiding) return
            commit()
            h.hide(now)
            return
        }
        if (closing) return
        backMs = -1
        val e = now - start
        val d = dur()
        if (kind == OverKind.Sheet) sheetFrom = if (e <= 0) SheetLook(0f, 0f, 0f) else if (e < d) sheetIn(e) else SheetLook(1f, 1f, 1f)
        if (kind == OverKind.Jump) jumpFrom = if (e < d) jumpIn(e - lead) else JumpLook(0f, 1f, 0f)
        if (kind == OverKind.Jump && e > 0) {
            closed = true
            start = now
            revision++
            moved()
            return
        }
        if (e <= 0) {
            backMs = 0
            backFrom = 0f
            backO = 0f
        } else if (e < d) {
            val t = progress(e, d)
            when (kind) {
                OverKind.Sheet -> {}
                OverKind.Dlg -> {
                    val hide = Tok.motion_dialogHide.toInt()
                    backO = Curves.pop.at(t)
                    backFrom = lerp(Tok.motion_dialogScale, 1f, Curves.bezierPop.at(t))
                    backMs = when {
                        backO >= 1f -> hide
                        backO <= 0f -> 0
                        else -> hide - min(hide, (-hide / LOG_STEPS * log2(backO)).roundToInt())
                    }
                }
                OverKind.Jump, OverKind.Volume, OverKind.Adjust -> {}
            }
        }
        closed = true
        start = now
        revision++
        moved()
    }

    fun wakeAt(now: Long): Long? = hud?.wakeAt(now)

    fun done(now: Long): Boolean = hud?.let { !it.visible(now) } ?: (closing && now - start >= dur())

    fun drop() {
        val h = hud ?: return
        commit()
        h.dismiss()
    }

    fun land(now: Long): Boolean {
        if (hud != null) return false
        if (closing) return true
        start = now - dur()
        revision++
        return false
    }

    fun moving(now: Long): Boolean =
        hud?.let { it.opening(now) || it.closing(now) } ?: (now - start < dur())

    fun sheetLook(now: Long): SheetLook = if (closing) sheetOut(now - start, sheetFrom) else sheetIn(now - start)

    fun sheet(now: Long): Sheet {
        val l = sheetLook(now)
        return Sheet(l.scrim * Tok.motion_sheetScrim, 1 - l.rise, l.alpha)
    }

    fun dialog(now: Long): Modal {
        val back = closing && backMs >= 0
        val t = progress(now - start, if (back) Tok.motion_dialogHide.toInt() else dur())
        val pop = Curves.pop.at(t)
        val o = if (!closing) pop else if (back) backO * (1 - pop) else 1 - pop
        val s = if (!closing) lerp(Tok.motion_dialogScale, 1f, Curves.bezierPop.at(t)) else if (back) backFrom else 1f
        return Modal(Tok.motion_dialogScrim * o, o, s)
    }

    fun jumpLook(now: Long): JumpLook {
        if (!closing) return jumpIn(now - start - lead)
        if (backMs >= 0) return JumpLook(0f, 0f, 1f)
        return jumpOut(now - start - lead, jumpFrom)
    }

    fun jump(now: Long): Float = jumpLook(now).alpha
}

class Pivot(val stripDx: Float, val stripAlpha: Float, val rowsDx: Float, val rowsAlpha: Float, val rowsIn: Boolean)

fun pivotAt(e: Float, dir: Int): Pivot {
    val d = dir.toFloat()
    val out = Tok.motion_pivotOut
    if (e < out) return Pivot(0f, 1f, -d * Tok.motion_pivotPaneSpeed * e / MS_PER_S, 1 - e / out, false)
    val k = e - out
    val s = 1 - Curves.easeOut.at((k / Tok.motion_pivotRows).coerceIn(0f, 1f))
    return Pivot(0f, 1f, d * Tok.motion_pivotRowInShift * s, (k / Tok.motion_pivotRowFade).coerceIn(0f, 1f), true)
}

class PivotMotion {
    private var at = -1L
    private var back = -1L
    private var dir = 1
    private var from = 0f
    private var stamp = 0L
    private var ready = false
    var offset = 0f
        private set
    var dragging = false
        private set

    fun begin(now: Long, dir: Int, from: Float = 0f) {
        at = now
        back = -1L
        this.dir = dir
        this.from = from
        offset = 0f
        dragging = false
        ready = false
    }

    fun drag(dx: Float) {
        dragging = true
        at = -1L
        back = -1L
        offset = dx
    }

    fun release(now: Long) {
        if (!dragging) return
        dragging = false
        ready = false
        from = offset
        offset = 0f
        back = now
    }

    fun end() {
        at = -1L
        back = -1L
        offset = 0f
        from = 0f
        dragging = false
        ready = false
    }

    fun prepare(stamp: Long) {
        ready = true
        this.stamp = stamp
    }

    fun prepared(stamp: Long): Boolean = ready && this.stamp == stamp

    fun active(now: Long): Boolean {
        if (dragging) return true
        if (back >= 0) return now - back < Tok.motion_pivotStrip
        return at >= 0 && now - at < Tok.motion_pivotOut + max(Tok.motion_pivotRows, Tok.motion_pivotRowFade)
    }

    fun switching(now: Long): Boolean = at >= 0 && active(now)

    fun entering(now: Long): Boolean = at >= 0 && active(now) && now - at >= Tok.motion_pivotOut

    fun alpha(now: Long): Float {
        if (dragging || at < 0) return 1f
        val e = now - at
        val out = Tok.motion_pivotOut
        if (e < out) return 1 - e / out
        val k = e - out
        return if (k >= Tok.motion_pivotRowFade) 1f else k / Tok.motion_pivotRowFade
    }

    fun dx(now: Long, ppu: Float): Int {
        if (dragging) return (offset * ppu).roundToInt()
        if (back >= 0) {
            val d = Tok.motion_pivotStrip
            val e = (now - back).coerceIn(0L, d.toLong())
            return (from * (1 - Curves.expOut6.at(e / d)) * ppu).roundToInt()
        }
        if (at < 0) return 0
        val e = max(0L, now - at)
        if (e < Tok.motion_pivotOut) return ((from - dir * Tok.motion_pivotPaneSpeed * e / MS_PER_S) * ppu).roundToInt()
        val k = (e - Tok.motion_pivotOut).coerceIn(0f, Tok.motion_pivotRows)
        return dir * (Tok.motion_pivotRowInShift * (1 - Curves.easeOut.at(k / Tok.motion_pivotRows)) * ppu).roundToInt()
    }

    fun thumbIn(now: Long): Boolean = !active(now) || entering(now)

    fun thumbAlpha(now: Long): Float = if (active(now)) alpha(now) else 1f
}

class Tw {
    var start = 0f
        private set
    var dur = 0f
        private set
    var from = 0f
        private set
    var to = 0f
        private set
    private var curve: Curve = Curves.linear

    fun set(v: Float) {
        from = v
        to = v
        dur = 0f
    }

    fun run(now: Float, f: Float, t: Float, ms: Float, c: Curve) {
        start = now
        dur = ms
        from = f
        to = t
        curve = c
    }

    fun at(now: Float): Float {
        val e = now - start
        if (dur <= 0f || e >= dur) return to
        if (e <= 0f) return from
        return lerp(from, to, curve.at(e / dur))
    }

    fun end(): Float = if (dur <= 0f) 0f else start + dur

    fun done(now: Float): Boolean = dur <= 0f || now - start >= dur
}

data class PressLook(val factor: Float, val lit: Float, val ring: Float, val framed: Boolean)

class Press {
    var revision by mutableIntStateOf(0)
        private set
    private val t = Tw().apply { set(1f) }
    private val g = Tw().apply { set(0f) }
    private var releaseAt: Float? = null
    private var cutAt: Float? = null
    private var shown = 0
    private var litShown = 0f
    private var ringShown = 0f
    private var framedShown = false
    private var ringCut = false
    var scaled = false

    val held: Boolean get() = t.to != 1f && releaseAt == null

    fun framed(now: Float): Boolean = t.to != 1f && releaseAt.let { it == null || now < it }

    fun scale(now: Float): Float {
        val r = releaseAt
        if (r == null || now < r) return t.at(now)
        val e = now - r
        if (e >= Tok.motion_press) return 1f
        return lerp(t.at(r), 1f, e / Tok.motion_press)
    }

    fun lit(now: Float): Float = if (cutAt.let { it != null && now >= it }) 0f else g.at(now)

    fun ring(now: Float): Float {
        if (framed(now)) return if (cutAt.let { it != null && now >= it }) 0f else 1f
        if (t.to != 1f || cutAt != null || ringCut || g.to != 0f || g.from <= 0f) return 0f
        return g.at(now) / g.from
    }

    fun down(now: Float) {
        revision++
        val v = scale(now)
        val l = lit(now)
        releaseAt = null
        cutAt = null
        ringCut = false
        t.run(now, v, Tok.motion_pressScale, Tok.motion_press, Curves.linear)
        g.run(now, l, 1f, Tok.motion_pressGlow, Curves.cubicOut)
    }

    fun tap(now: Float, acts: Boolean = true) {
        revision++
        if (t.to != 1f && releaseAt == null) {
            val r = now + Tok.motion_touchReturnDelay
            releaseAt = r
            cutAt = if (acts) now else r
        }
    }

    fun keyUp(now: Float) {
        revision++
        if (!held) return
        val v = scale(now)
        releaseAt = null
        cutAt = now
        if (v == 1f) t.set(1f) else t.run(now, v, 1f, Tok.motion_press, Curves.linear)
    }

    fun cancel(now: Float) {
        revision++
        if (!held) return
        val v = scale(now)
        val l = lit(now)
        releaseAt = null
        cutAt = null
        if (v == 1f) t.set(1f) else t.run(now, v, 1f, Tok.motion_press, Curves.linear)
        if (l == 0f) g.set(0f) else g.run(now, l, 0f, Tok.motion_press, Curves.linear)
    }

    fun untap(now: Float) {
        revision++
        if (releaseAt == null) return
        releaseAt = null
        cutAt = null
        cancel(now)
    }

    fun reset() {
        revision++
        t.set(1f)
        g.set(0f)
        releaseAt = null
        cutAt = null
    }

    fun drop() {
        revision++
        if (held) reset()
    }

    fun hold(now: Float) {
        revision++
        if (t.to != 1f && releaseAt.let { it == null || now < it }) {
            t.set(Tok.motion_pressScale)
            g.set(1f)
        }
    }

    fun cutRing(now: Float) {
        revision++
        if (!framed(now)) ringCut = true
    }

    private fun step(now: Float): Int {
        val span = 1 - Tok.motion_pressScale
        return ((1 - scale(now)) * PRESS_STEPS / span).roundToInt().coerceIn(0, PRESS_STEPS.toInt())
    }

    private fun factorOf(step: Int): Float = 1 - (1 - Tok.motion_pressScale) * step / PRESS_STEPS

    fun draw(now: Float): Float {
        framedShown = framed(now)
        litShown = lit(now)
        ringShown = ring(now)
        shown = step(now)
        return factorOf(shown)
    }

    fun look(now: Float): Float {
        draw(now)
        return litShown
    }

    fun at(now: Float): PressLook = PressLook(factorOf(step(now)), lit(now), ring(now), framed(now))

    fun rest(now: Float): Boolean = !moving(now) && scale(now) == 1f

    fun moving(now: Float): Boolean {
        if (!scaled) return !g.done(now)
        val r = releaseAt
        return !t.done(now) || !g.done(now) || (r != null && now >= r && now < r + Tok.motion_press)
    }

    fun stale(now: Float): Boolean =
        moving(now) || (scaled && step(now) != shown) || lit(now) != litShown || framed(now) != framedShown || ring(now) != ringShown

    fun end(): Float = releaseAt?.let { it + Tok.motion_press } ?: max(t.end(), g.end())

    fun due(now: Float): Float? = releaseAt?.takeIf { it > now }
}

class OverlayGate {
    private var at = -1L
    private var shut = false
    private var seq = 0

    fun down(o: Overlay, now: Long, at: Long = now): Boolean {
        this.at = at
        seq = motionSeq
        shut = o.closing || o.moving(now)
        return !shut
    }

    fun passes(o: Overlay, confirm: Boolean): Boolean = !shut && live(o) && !(confirm && (o.risen < 0 || at <= o.risen))

    internal fun live(o: Overlay): Boolean = o.movedSeq <= seq
}
