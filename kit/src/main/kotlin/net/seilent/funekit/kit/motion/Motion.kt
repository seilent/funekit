package net.seilent.funekit.kit.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.Tok
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PRESS_STEPS = 16f
private const val LOG_STEPS = 10f

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

enum class OverKind { Sheet, Dlg, Jump }

class Overlay(val kind: OverKind) {
    var start = 0L
        private set
    var closing = false
        private set
    var risen = -1L
        private set
    private var backMs = -1
    private var backFrom = 1f
    private var backO = 1f
    private var sheetFrom = SheetLook(1f, 1f, 1f)
    private var jumpFrom = JumpLook(0f, 1f, 0f)
    var lead = 0L

    fun open(now: Long) {
        start = now
        closing = false
        backMs = -1
        risen = -1L
    }

    fun rise(now: Long) {
        if (risen < 0 && !closing && !moving(now)) risen = max(now, start + dur())
    }

    fun dur(): Int {
        if (kind == OverKind.Sheet) return if (closing) sheetOutMs(sheetFrom) else sheetInMs()
        if (closing && backMs >= 0) return backMs
        return when (kind) {
            OverKind.Dlg -> (if (closing) Tok.motion_dialogHide else Tok.motion_dialog).toInt()
            else -> lead.toInt() + if (closing) jumpOutMs() else jumpInMs()
        }
    }

    private fun progress(e: Long, d: Int): Float = if (d <= 0) 1f else (e.toFloat() / d).coerceIn(0f, 1f)

    fun close(now: Long) {
        if (closing) return
        backMs = -1
        val e = now - start
        val d = dur()
        if (kind == OverKind.Sheet) sheetFrom = if (e <= 0) SheetLook(0f, 0f, 0f) else if (e < d) sheetIn(e) else SheetLook(1f, 1f, 1f)
        if (kind == OverKind.Jump) jumpFrom = if (e < d) jumpIn(e - lead) else JumpLook(0f, 1f, 0f)
        if (kind == OverKind.Jump && e > 0) {
            closing = true
            start = now
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
                OverKind.Jump -> {}
            }
        }
        closing = true
        start = now
    }

    fun done(now: Long): Boolean = closing && now - start >= dur()

    fun land(now: Long): Boolean {
        if (closing) return true
        start = now - dur()
        return false
    }

    fun moving(now: Long): Boolean = now - start < dur()

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
    val strip = (e / Old.motion_pivotStrip).coerceIn(0f, 1f)
    val stripDx = d * Old.motion_pivotStripShift * (1 - Curves.cubicOut.at(strip))
    val stripAlpha = lerp(Old.motion_pivotStripAlpha, 1f, strip)
    if (e < Old.motion_pivotOut) {
        val o = Curves.strip.at(e / Old.motion_pivotOut)
        return Pivot(stripDx, stripAlpha, -d * Old.motion_pivotShift * o, 1 - o, false)
    }
    val k = e - Old.motion_pivotOut
    val dx = d * Old.motion_pivotRowInShift * (1 - Curves.cubicOut.at(k / Old.motion_pivotRows))
    return Pivot(stripDx, stripAlpha, dx, (k / Old.motion_pivotRowFade).coerceIn(0f, 1f), true)
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

    fun show(now: Float) {
        draw(now)
    }

    fun factor(): Float = factorOf(shown)

    fun factorAt(now: Float): Float = factorOf(step(now))

    fun drawn(now: Float): Float = draw(now)

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

    fun down(o: Overlay, now: Long, at: Long = now): Boolean {
        this.at = at
        shut = o.closing || o.moving(now)
        return !shut
    }

    fun passes(o: Overlay, confirm: Boolean): Boolean = !shut && !(confirm && (o.risen < 0 || at <= o.risen))
}
