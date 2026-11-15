package net.seilent.funekit.kit.touch

import androidx.compose.animation.core.FloatDecayAnimationSpec
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

private const val MS_PER_S = 1000f
private const val SAMPLES = 32
private const val NO_TIME = Long.MIN_VALUE

enum class Kind { List, Quickplay, Grid }

enum class Edge { Follow, Lead }

interface Track {
    val canMove: Boolean

    fun by(delta: Float): Float

    fun atEnd(forward: Boolean): Boolean
}

class Velocity(private val windowMs: Long) {
    private val ms = ArrayDeque<Long>()
    private val y = ArrayDeque<Float>()

    fun add(t: Long, v: Float) {
        while (ms.size > 1 && ms[1] <= t - windowMs) {
            ms.removeFirst()
            y.removeFirst()
        }
        if (ms.size == SAMPLES) {
            ms.removeFirst()
            y.removeFirst()
        }
        ms.addLast(t)
        y.addLast(v)
    }

    fun perS(): Float {
        if (ms.size <= 1) return 0f
        val lt = ms.last()
        val at = lt - windowMs
        var tAt = ms.first()
        var yAt = y.first()
        if (tAt < at) {
            var i = 0
            while (ms[i + 1] < at) i++
            val f = (at - ms[i]).toFloat() / (ms[i + 1] - ms[i])
            yAt = y[i] + (y[i + 1] - y[i]) * f
            tAt = at
        }
        val dt = lt - tAt
        if (dt <= 0) return 0f
        return (y.last() - yAt) * MS_PER_S / dt
    }
}

class Scroll(
    val kind: Kind,
    touchPpu: Float,
    stdPpu: Float,
    ppu: Float,
    private val track: Track,
    private val coast: FloatDecayAnimationSpec? = null,
    private val edge: Edge = Edge.Follow,
) {
    private val retention = when (kind) {
        Kind.List -> Tok.motion_listFlingRetention
        Kind.Quickplay -> Tok.motion_qpFlingRetention
        Kind.Grid -> Tok.motion_flingRetention
    }
    private val backMs = when (kind) {
        Kind.List -> Tok.motion_overscrollBackList
        Kind.Quickplay -> Tok.motion_overscrollBackQp
        Kind.Grid -> 0f
    }
    private val k = MS_PER_S / Tok.motion_flingTick * -ln(retention)
    private val stop = px(if (kind == Kind.Quickplay) Tok.motion_qpFlingStop else Tok.flingStop, touchPpu).toFloat()
    private val min = px(Tok.flingMin, touchPpu).toFloat()
    private val cap = px(Tok.flingCap, touchPpu).toFloat()
    private val room = px(Tok.motion_overscrollCap, stdPpu).toFloat()
    private val land = Tok.motion_overscrollLand * ppu
    private val maxStep = Tok.motion_flingMaxStep.toLong()

    var over = 0f
        private set
    var v = 0f
        private set
    var flinging = false
        private set
    var returning = false
        private set
    var dragging = false
        private set

    private var raw = 0f
    private var finger = 0f
    private var last = NO_TIME
    private var v0 = 0f
    private var clock = 0L
    private var pos = 0f
    private var end = 0L
    private var past = false
    private var easeFrom = 0f
    private var easeAt = NO_TIME

    val moving: Boolean get() = flinging || returning

    private fun lead(v: Float): Float = min(abs(v) * Tok.motion_overscrollLead / MS_PER_S, room)

    fun keep(dt: Float): Float = retention.pow(dt / Tok.motion_flingTick)

    fun travel(dt: Float): Float = (1 - keep(dt)) / k

    fun travelOf(v0: Float): Float = coast?.getTargetValue(0f, v0) ?: (v0 / k)

    private fun nanos(ms: Long): Long = TimeUnit.MILLISECONDS.toNanos(ms)

    private fun ends() = kind != Kind.Grid && track.canMove

    fun flingVelocity(fingerV: Float): Float {
        if (abs(fingerV) < min) return 0f
        return -fingerV.coerceIn(-cap, cap)
    }

    fun grab(at: Long): Boolean {
        val c = coast
        val fast = flinging && when {
            c != null -> abs(c.getVelocityFromNanos(nanos(max(0L, clock + at - last)), 0f, v0)) >= min
            at < last -> abs(v) >= min * keep((last - at).toFloat())
            else -> abs(v) * keep((at - last).toFloat()) >= min
        }
        halt()
        return fast
    }

    fun halt() {
        if (returning) over = 0f
        returning = false
        past = false
        easeAt = NO_TIME
        dragging = false
        flinging = false
        v = 0f
    }

    fun begin(y: Float) {
        halt()
        raw = over
        finger = y
        dragging = true
    }

    fun drag(y: Float, fingerV: Float = 0f) {
        if (past) {
            finger = y
            return
        }
        var rest = finger - y
        finger = y
        if (raw != 0f && rest != 0f && rest.sign != raw.sign) {
            val take = min(abs(rest), abs(raw)) * rest.sign
            raw += take
            rest -= take
        }
        if (rest != 0f) {
            val used = track.by(rest)
            if (track.atEnd(rest > 0f)) raw += rest - used
        }
        val r = if (ends()) room else 0f
        if (edge == Edge.Lead && r > 0f && raw != 0f) {
            past = true
            over = lead(fingerV) * raw.sign
            raw = over
            returning = over != 0f
            last = NO_TIME
            return
        }
        over = raw.coerceIn(-r, r)
    }

    fun endDrag() {
        if (!dragging) return
        dragging = false
        past = false
        if (over != 0f && !returning) {
            returning = true
            last = NO_TIME
        }
    }

    fun release(now: Long, fingerV: Float) {
        if (past) {
            past = false
            dragging = false
            return
        }
        halt()
        if (over != 0f) {
            returning = true
            last = now
            if (kind == Kind.Quickplay) {
                easeFrom = over
                easeAt = now
            }
            return
        }
        val pv = flingVelocity(fingerV)
        if (abs(pv) < stop) return
        v = pv
        last = now
        flinging = true
        val c = coast ?: return
        v0 = pv
        clock = 0L
        pos = 0f
        end = TimeUnit.NANOSECONDS.toMillis(c.getDurationNanos(0f, pv))
    }

    private fun hit(delta: Float): Boolean {
        val used = track.by(delta)
        if (abs(used) >= abs(delta) || !track.atEnd(delta > 0f)) return false
        if (ends()) {
            over = lead(v) * delta.sign
            flinging = false
            v = 0f
            returning = over != 0f
        } else {
            halt()
        }
        return true
    }

    fun step(now: Long): Boolean {
        if (returning && easeAt != NO_TIME) {
            val e = (now - easeAt).toFloat() / Tok.motion_overscrollReleaseQp
            over = if (e >= 1f) 0f else easeFrom * (1 - Curves.sine.at(e.coerceAtLeast(0f)))
            returning = over != 0f
            if (!returning) easeAt = NO_TIME
            return true
        }
        if (returning) {
            var dt = if (last == NO_TIME) 0L else max(0L, now - last)
            last = now
            var d = over
            while (dt > 0 && abs(d) >= land) {
                d *= exp(-min(dt, maxStep) / backMs)
                dt -= maxStep
            }
            over = if (abs(d) < land) 0f else d
            returning = over != 0f
            return true
        }
        if (!flinging) return false
        val dt = (now - last).coerceIn(0L, maxStep)
        last = now
        if (dt == 0L) return true
        val c = coast
        if (c != null) {
            clock += dt
            val p = c.getValueFromNanos(nanos(clock), 0f, v0)
            val delta = p - pos
            pos = p
            if (hit(delta)) return true
            v = c.getVelocityFromNanos(nanos(clock), 0f, v0)
            if (clock >= end) halt()
            return true
        }
        if (hit(v * travel(dt.toFloat()))) return true
        v *= keep(dt.toFloat())
        if (abs(v) < stop) halt()
        return true
    }
}
