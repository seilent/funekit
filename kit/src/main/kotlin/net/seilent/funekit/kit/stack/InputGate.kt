package net.seilent.funekit.kit.stack

import android.graphics.Matrix
import android.view.KeyEvent
import android.view.MotionEvent

internal interface GateHost {
    val asked: Int
    val armed: Boolean
    val turning: Boolean get() = false
    val closing: Boolean get() = false
    val motion: Boolean get() = false
    fun cut()
    fun back()
    fun land() {}
}

class InputGate {
    private sealed class Kept(val at: Long) {
        abstract fun recycle()
        open val action: Int get() = -1
    }

    private class Touch(val ev: MotionEvent, val pass: (MotionEvent) -> Boolean, val m: Matrix? = null) :
        Kept(if (ev.historySize > 0) ev.getHistoricalEventTime(0) else ev.eventTime) {
        override val action: Int get() = ev.actionMasked

        override fun recycle() = ev.recycle()
    }

    private class Key(val ev: KeyEvent, val pass: (KeyEvent) -> Boolean) : Kept(ev.eventTime) {
        fun send(k: Long) {
            pass(KeyEvent(ev.downTime + k, ev.eventTime + k, ev.action, ev.keyCode, ev.repeatCount, ev.metaState, ev.deviceId, ev.scanCode, ev.flags, ev.source))
        }

        override fun recycle() {}
    }

    internal var host: GateHost? = null

    internal var holding = false
        private set

    internal val queued: Boolean get() = queue.isNotEmpty()

    internal var backOwed = false

    var carry: ((x: Float, y: Float) -> Matrix?)? = null

    var holds: ((MotionEvent) -> Boolean)? = null

    private var carried: Matrix? = null
    private var tail = 0L

    private val queue = ArrayDeque<Kept>()
    private val copies = ArrayList<Touch>()
    private var recording = false
    private var cancelOwed = false
    private val keysHeld = mutableSetOf<Int>()
    private val swallow = mutableSetOf<Int>()
    private var swallowTouch = false
    private var cutAsked = false
    private var shift = 0L
    private var gesture = false
    private var downAt = 0L
    private var lastAt = 0L
    private var lastDevice = 0
    private var lastSource = 0
    private var props = emptyArray<MotionEvent.PointerProperties>()
    private var coords = emptyArray<MotionEvent.PointerCoords>()
    private var lastPass: ((MotionEvent) -> Boolean)? = null
    private var tapFrom = NONE
    private var tapAt = NONE

    fun touch(ev: MotionEvent, pass: (MotionEvent) -> Boolean): Boolean {
        onMain("InputGate.touch")
        if (swallowTouch) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> swallowTouch = false
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> return true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    swallowTouch = false
                    return true
                }
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) forget()
        if (!holding && queue.isEmpty()) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                carried = null
                tail = 0L
            }
            val live = carried
            val k = tail
            if (live != null || k != 0L) {
                val c = (if (k != 0L) ev.shifted(k) else MotionEvent.obtain(ev)).also { if (live != null) it.transform(live) }
                ends(ev)
                return try {
                    deliver(c, pass)
                } finally {
                    c.recycle()
                }
            }
            if (ev.actionMasked == MotionEvent.ACTION_DOWN && carries(ev)) {
                carried = if (host?.turning == true) carry?.invoke(ev.x, ev.y) else null
                queue.addLast(Touch(MotionEvent.obtain(ev), pass, carried))
                host?.land()
                return true
            }
            return record(ev, pass)
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) carried = null
        keep(Touch(MotionEvent.obtain(ev), pass, carried))
        ends(ev)
        return true
    }

    private fun record(ev: MotionEvent, pass: (MotionEvent) -> Boolean): Boolean {
        val a = ev.actionMasked
        if (a == MotionEvent.ACTION_DOWN) recording = host?.motion == true
        if (recording) copies.add(Touch(MotionEvent.obtain(ev), pass))
        val r = deliver(ev, pass)
        if (recording && (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)) forget()
        return r
    }

    private fun forget() {
        for (c in copies) c.recycle()
        copies.clear()
        recording = false
    }

    internal fun replayHeld(downAt: Long, tap: Boolean): Boolean {
        if (holding || queue.isNotEmpty()) return false
        if (copies.isEmpty() || copies.first().ev.downTime != downAt) return false
        queue.addAll(copies)
        copies.clear()
        recording = false
        cancelOwed = true
        tapFrom = if (tap) downAt else NONE
        return true
    }

    internal fun tapped(downAt: Long): Boolean = tapAt != NONE && tapAt == downAt

    private fun carries(ev: MotionEvent): Boolean {
        val h = holds ?: return false
        val g = host ?: return false
        return carry != null && (g.turning || g.closing) && !h(ev)
    }

    private fun ends(ev: MotionEvent) {
        val a = ev.actionMasked
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            carried = null
            tail = 0L
        }
    }

    fun key(ev: KeyEvent, pass: (KeyEvent) -> Boolean): Boolean {
        onMain("InputGate.key")
        val code = ev.keyCode
        if (ev.action == KeyEvent.ACTION_UP && code in swallow) {
            swallow -= code
            return true
        }
        if (code in passes || (!holding && queue.isEmpty())) return pass(ev)
        if (ev.action == KeyEvent.ACTION_UP && code !in keysHeld) return pass(ev)
        if (code == KeyEvent.KEYCODE_BACK && holding && ev.action == KeyEvent.ACTION_DOWN) {
            if (host?.armed == true) {
                swallow += code
                if (ev.repeatCount == 0) {
                    drop()
                    host?.back()
                }
                return true
            }
            backOwed = true
        }
        when (ev.action) {
            KeyEvent.ACTION_UP -> keysHeld -= code
            KeyEvent.ACTION_DOWN -> keysHeld += code
        }
        keep(Key(KeyEvent(ev), pass))
        return true
    }

    fun dispose() {
        onMain("InputGate.dispose")
        drop(cancel = false)
        holding = false
    }

    private fun keep(k: Kept) {
        queue.addLast(k)
        if (holding) askCut()
    }

    private fun deliver(ev: MotionEvent, pass: (MotionEvent) -> Boolean): Boolean {
        val r = pass(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> open(ev, pass)
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> if (gesture) open(ev, pass)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> close()
        }
        return r
    }

    private fun open(ev: MotionEvent, pass: (MotionEvent) -> Boolean) {
        gesture = true
        downAt = ev.downTime
        lastAt = ev.eventTime
        lastDevice = ev.deviceId
        lastSource = ev.source
        lastPass = pass
        val n = ev.pointerCount
        if (props.size != n) {
            props = Array(n) { MotionEvent.PointerProperties() }
            coords = Array(n) { MotionEvent.PointerCoords() }
        }
        for (i in 0 until n) {
            ev.getPointerProperties(i, props[i])
            ev.getPointerCoords(i, coords[i])
        }
    }

    private fun close() {
        gesture = false
        lastPass = null
    }

    private fun askCut() {
        if (cutAsked) return
        cutAsked = true
        host?.cut()
    }

    internal fun hold() {
        holding = true
        cutAsked = false
        if (queue.isNotEmpty()) askCut()
    }

    internal fun free() {
        holding = false
    }

    internal fun drop(cancel: Boolean = true) {
        var pressed = false
        for (k in queue) {
            when (k.action) {
                MotionEvent.ACTION_DOWN -> pressed = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressed = false
            }
            k.recycle()
        }
        if (gesture) pressed = true
        if (cancel) cancelLive()
        close()
        forget()
        cancelOwed = false
        tapFrom = NONE
        carried = null
        tail = 0L
        if (pressed) swallowTouch = true
        queue.clear()
        swallow += keysHeld
        keysHeld.clear()
        shift = 0L
        backOwed = false
    }

    private fun cancelLive() {
        val p = lastPass ?: return
        if (!gesture) return
        val c = MotionEvent.obtain(downAt, lastAt, MotionEvent.ACTION_CANCEL, props.size, props, coords, 0, 0, 1f, 1f, lastDevice, 0, lastSource, 0)
        try {
            p(c)
        } finally {
            c.recycle()
        }
    }

    internal fun replay(now: Long): Boolean {
        if (holding || queue.isEmpty()) return false
        if (cancelOwed) {
            cancelOwed = false
            cancelLive()
            close()
        }
        shift = maxOf(shift, now - queue.first().at, 0L)
        while (queue.isNotEmpty()) {
            val asked = host?.asked
            val k = queue.removeFirst()
            try {
                when (k) {
                    is Touch -> {
                        val c = k.ev.shifted(shift)
                        k.m?.let { c.transform(it) }
                        if (k.ev.actionMasked == MotionEvent.ACTION_DOWN && k.ev.downTime == tapFrom) {
                            tapFrom = NONE
                            tapAt = c.downTime
                        }
                        try {
                            deliver(c, k.pass)
                        } finally {
                            tapAt = NONE
                            c.recycle()
                        }
                    }
                    is Key -> k.send(shift)
                }
            } finally {
                k.recycle()
            }
            if (holding) return false
            if (host?.asked != asked) return queue.isNotEmpty()
        }
        if (gesture) tail = shift
        shift = 0L
        keysHeld.clear()
        return false
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
        val passes = setOf(
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_MEDIA_CLOSE,
            KeyEvent.KEYCODE_MEDIA_EJECT, KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_MEDIA_TOP_MENU,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
        )
    }
}

private fun MotionEvent.shifted(k: Long): MotionEvent {
    val n = pointerCount
    val dx = rawX - x
    val dy = rawY - y
    val props = Array(n) { i -> MotionEvent.PointerProperties().also { getPointerProperties(i, it) } }
    fun coords(h: Int) = Array(n) { i ->
        MotionEvent.PointerCoords().also {
            if (h < 0) getPointerCoords(i, it) else getHistoricalPointerCoords(i, h, it)
            it.x += dx
            it.y += dy
        }
    }
    val hs = historySize
    val t = if (hs > 0) getHistoricalEventTime(0) else eventTime
    val c = MotionEvent.obtain(downTime + k, t + k, action, n, props, coords(if (hs > 0) 0 else -1), metaState, buttonState, xPrecision, yPrecision, deviceId, edgeFlags, source, flags)
    for (h in 1 until hs) c.addBatch(getHistoricalEventTime(h) + k, coords(h), metaState)
    if (hs > 0) c.addBatch(eventTime + k, coords(-1), metaState)
    c.offsetLocation(-dx, -dy)
    return c
}
