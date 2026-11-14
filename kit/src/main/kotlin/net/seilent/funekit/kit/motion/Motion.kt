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
        if (closing && backMs >= 0) return backMs
        return when (kind) {
            OverKind.Sheet -> Old.motion_sheet
            OverKind.Dlg -> if (closing) Tok.motion_dialogHide else Tok.motion_dialog
            OverKind.Jump -> if (closing) Old.motion_jumpOut else Old.motion_jumpIn
        }.toInt()
    }

    private fun progress(e: Long, d: Int): Float = if (d <= 0) 1f else (e.toFloat() / d).coerceIn(0f, 1f)

    fun close(now: Long) {
        if (closing) return
        backMs = -1
        val e = now - start
        val d = dur()
        if (e <= 0) {
            backMs = 0
            backFrom = 0f
            backO = 0f
        } else if (e < d) {
            val t = progress(e, d)
            when (kind) {
                OverKind.Sheet -> {
                    backFrom = Curves.decel.at(t)
                    backMs = (Old.motion_sheet * backFrom).toInt()
                }
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
                OverKind.Jump -> {
                    backFrom = t
                    backMs = min(e, d.toLong()).toInt()
                }
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

    fun sheet(now: Long): Sheet {
        val t = Curves.decel.at(progress(now - start, dur()))
        val p = if (!closing) t else if (backMs >= 0) backFrom * (1 - t) else 1 - t
        return Sheet(p * Tok.motion_sheetScrim, 1 - p, p)
    }

    fun dialog(now: Long): Modal {
        val back = closing && backMs >= 0
        val t = progress(now - start, if (back) Tok.motion_dialogHide.toInt() else dur())
        val pop = Curves.pop.at(t)
        val o = if (!closing) pop else if (back) backO * (1 - pop) else 1 - pop
        val s = if (!closing) lerp(Tok.motion_dialogScale, 1f, Curves.bezierPop.at(t)) else if (back) backFrom else 1f
        return Modal(Tok.motion_dialogScrim * o, o, s)
    }

    fun jump(now: Long): Float {
        val t = progress(now - start, dur())
        return if (!closing) t else if (backMs >= 0) backFrom * (1 - t) else 1 - t
    }
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

class Press {
    var revision by mutableIntStateOf(0)
        private set
    private var leg = Leg(1f, 1f, 0f, 0f)
    private var t0 = 0f
    private var releaseAt: Float? = null

    private val to: Float get() = leg.to

    val held: Boolean get() = to != 1f && releaseAt == null

    fun framed(now: Float): Boolean = to != 1f && releaseAt.let { it == null || now < it }

    private fun shrink(now: Float): Float = leg.at(now - t0)

    private fun run(now: Float, from: Float, to: Float) {
        leg = Leg(from, to, 0f, Tok.motion_press)
        t0 = now
    }

    fun scale(now: Float): Float {
        val r = releaseAt
        if (r == null || now < r) return shrink(now)
        val e = now - r
        if (e >= Tok.motion_press) return 1f
        return lerp(shrink(r), 1f, e / Tok.motion_press)
    }

    fun down(now: Float) {
        revision++
        val v = scale(now)
        releaseAt = null
        run(now, v, Tok.motion_pressScale)
    }

    fun tap(now: Float) {
        revision++
        if (to != 1f && releaseAt == null) releaseAt = now + Tok.motion_touchReturnDelay
    }

    fun cancel(now: Float) {
        revision++
        if (!held) return
        val v = scale(now)
        releaseAt = null
        if (v == 1f) leg = Leg(1f, 1f, 0f, 0f) else run(now, v, 1f)
    }

    fun reset() {
        revision++
        leg = Leg(1f, 1f, 0f, 0f)
        releaseAt = null
    }

    fun moving(now: Float): Boolean {
        val r = releaseAt
        return now - t0 < leg.end || (r != null && now >= r && now < r + Tok.motion_press)
    }

    fun drop() {
        revision++
        if (held) reset()
    }

    private var shown = 0

    private fun step(now: Float): Int {
        val span = 1 - Tok.motion_pressScale
        return ((1 - scale(now)) * PRESS_STEPS / span).roundToInt().coerceIn(0, PRESS_STEPS.toInt())
    }

    private fun factorOf(step: Int): Float = 1 - (1 - Tok.motion_pressScale) * step / PRESS_STEPS

    fun show(now: Float) {
        shown = step(now)
    }

    fun factor(): Float = factorOf(shown)

    fun factorAt(now: Float): Float = factorOf(step(now))

    fun drawn(now: Float): Float {
        show(now)
        return factor()
    }

    fun stale(now: Float): Boolean = moving(now) || step(now) != shown

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
