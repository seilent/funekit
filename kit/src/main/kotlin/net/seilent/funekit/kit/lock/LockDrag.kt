package net.seilent.funekit.kit.lock

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.touch.Slop
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.kit.touch.Velocity
import kotlin.math.roundToInt

private const val HALF = 2f

class LockDrag(val heightPx: Int) {
    private val slop = Slop(Tok.touchSlop, Tok.gesture_swipe)
    private val easeMs = Tok.motion_lockCommit.toLong()
    private var vel = Velocity(Tok.motion_flingWindow.toLong())
    private var off = 0f
    private var base = 0f
    private var py0 = 0f
    private var x0 = 0f
    private var y0 = 0f
    private var from = 0f
    private var to = 0f
    private var easeAt = 0L
    private var commitAt = 0L

    var dragging = false
        private set
    var committing = false
        private set

    private fun eased(now: Long): Float {
        val e = now - easeAt
        if (e >= easeMs) return to
        return lerp(from, to, Curves.expOut6.at(e.toFloat() / easeMs))
    }

    private fun hold(v: Float) {
        from = v
        to = v
        easeAt = 0L
    }

    fun down(py: Float, x: Float, y: Float, ms: Long, now: Long) {
        base = if (dragging) off else eased(now)
        off = base
        hold(base)
        py0 = py
        x0 = x
        y0 = y
        dragging = true
        committing = false
        vel = Velocity(Tok.motion_flingWindow.toLong())
        vel.add(ms, y)
    }

    fun move(py: Float, y: Float, ms: Long) {
        if (!dragging) return
        off = (base + py0 - py).coerceIn(0f, heightPx.toFloat())
        vel.add(ms, y)
    }

    fun up(py: Float, x: Float, y: Float, ms: Long, now: Long) {
        if (!dragging) return
        move(py, y, ms)
        val swipe = slop.swipeUpOf(x - x0, y - y0) == Swipe.Up && vel.perS() <= -Tok.flingMin
        committing = off * HALF >= heightPx || swipe
        commitAt = now
        from = off
        to = if (committing) heightPx.toFloat() else 0f
        easeAt = now
        dragging = false
    }

    fun rest() {
        dragging = false
        committing = false
        off = 0f
        base = 0f
        hold(0f)
        vel = Velocity(Tok.motion_flingWindow.toLong())
    }

    fun offset(now: Long): Int = (if (dragging) off else eased(now)).roundToInt()

    fun sheetAlpha(now: Long): Float {
        val e = now - commitAt - Tok.motion_lockSliverDelay.toLong()
        val d = Tok.motion_lockSliver.toLong()
        if (dragging || !committing || e <= 0) return 1f
        if (e >= d) return Tok.motion_lockSliverAlpha
        return lerp(1f, Tok.motion_lockSliverAlpha, e.toFloat() / d)
    }

    fun moving(now: Long): Boolean = dragging || eased(now).roundToInt() != to.roundToInt()

    fun landed(now: Long): Boolean = committing && !moving(now) && offset(now) >= heightPx
}
