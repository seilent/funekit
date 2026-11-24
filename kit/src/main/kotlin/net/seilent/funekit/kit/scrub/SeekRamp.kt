package net.seilent.funekit.kit.scrub

import net.seilent.funekit.gen.Tok
import kotlin.math.max
import kotlin.math.min

class SeekRamp {
    enum class Step { None, Moved, RunIn }

    var dir = 0
        private set
    var steps = 0
        private set
    var target = 0L
        private set
    var dur = 0L
        private set
    var edgeHeld = false
        private set

    private var t0 = 0L
    private var stood = 0L
    private var next = 0L
    private var carry = 0L
    private var waitAt = -1L
    private var finalAt = -1L
    private var finalSize = 0L
    private var finalOwed = false

    val on: Boolean get() = dir != 0
    val waiting: Boolean get() = waitAt >= 0
    val released: Boolean get() = finalAt >= 0 || finalOwed

    fun start(dir: Int, now: Long, from: Long, dur: Long) {
        this.dir = dir
        steps = 0
        t0 = now
        next = now
        stood = 0
        carry = 0
        waitAt = -1
        finalAt = -1
        finalSize = 0
        edgeHeld = false
        finalOwed = false
        target = if (dir > 0) min(from, max(0L, dur - margin())) else from
        this.dur = dur
    }

    fun join(dir: Int, now: Long) {
        this.dir = dir
        if (dir < 0) edgeHeld = false
        finalAt = -1
        next = max(next, now)
        finalOwed = false
    }

    fun stepDue(): Long =
        if (dir != 0 && waitAt < 0 && finalAt < 0 && !edgeHeld && !finalOwed && !(dir < 0 && target == 0L)) next else -1

    fun finalDue(): Long = if (dir != 0) finalAt else -1

    fun waitEnd(): Long = if (dir != 0 && waitAt >= 0) waitAt + Tok.seekRunInMs.toLong() else -1

    fun sizeAt(at: Long): Long {
        val stage = max(0L, at - t0 - stood) / Tok.seekStageMs.toLong()
        return min(Tok.seekTickStepMs.toLong() * (1 + stage), Tok.seekStepMaxMs.toLong())
    }

    fun step(now: Long): Step {
        var r = Step.None
        while (stepDue() >= 0 && now >= next) {
            val size = sizeAt(next)
            val edge = dur - margin()
            if (dir > 0 && target >= edge) {
                carry = target + size - edge + size
                waitAt = next
                return Step.RunIn
            }
            val to = if (dir > 0) min(edge, target + size) else max(0L, target - size)
            next += Tok.seekRampMs.toLong()
            steps++
            if (to != target) {
                target = to
                r = Step.Moved
            }
        }
        return r
    }

    fun holdEdge() {
        waitAt = -1
        edgeHeld = true
    }

    fun land(now: Long, dur: Long): Boolean {
        this.dur = dur
        target = carry.coerceIn(0L, max(0L, dur - margin()))
        stood += now - waitAt
        waitAt = -1
        next = now + Tok.seekRampMs.toLong()
        return finalOwed
    }

    fun release(now: Long) {
        if (waitAt >= 0) {
            finalOwed = true
        } else {
            finalAt = now + Tok.motion_volumePressOff.toLong()
            finalSize = sizeAt(now)
        }
    }

    fun finalStep(): Long {
        val edge = max(0L, dur - margin())
        target = if (dir > 0) min(edge, target + finalSize) else max(0L, target - finalSize)
        return target
    }

    fun stop() {
        dir = 0
    }

    private fun margin(): Long = Tok.seekEndMarginMs.toLong()
}
