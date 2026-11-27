package net.seilent.funekit.screens.host

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.gen.Tok

private const val MS_PER_S = 1000f

internal val RETURN_MS = (Tok.settings_npReturn * MS_PER_S).toLong()

class IdleClock(now0: Long) {
    var last = now0
        private set
    var returned by mutableLongStateOf(Long.MIN_VALUE)
        private set
    var held by mutableStateOf(false)
        private set
    var away by mutableStateOf(true)
        private set
    internal var spent by mutableStateOf(false)
        private set
    private var finger = false
    private val keys = HashSet<Int>()
    private var live = false
    private var focused = true
    private var lit = true

    fun touch(action: Int, now: Long) {
        when (action) {
            MotionEvent.ACTION_DOWN -> finger = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finger = false
        }
        hold()
        stamp(now)
    }

    fun key(action: Int, code: Int, now: Long) {
        when (action) {
            KeyEvent.ACTION_DOWN -> keys += code
            KeyEvent.ACTION_UP -> keys -= code
        }
        hold()
        stamp(now)
    }

    fun moved(now: Long) = stamp(now)

    fun focus(on: Boolean, now: Long) {
        focused = on
        turn(on, now)
    }

    fun resumed(on: Boolean, now: Long) {
        live = on
        turn(on, now)
    }

    fun screen(on: Boolean, now: Long) {
        lit = on
        turn(on, now)
    }

    fun stamp(now: Long) {
        last = now
        spent = false
    }

    internal fun returnedAt(now: Long) {
        returned = now
        spent = true
    }

    private fun turn(on: Boolean, now: Long) {
        away = !live || !focused || !lit
        if (away) {
            finger = false
            keys.clear()
            hold()
        }
        if (on) stamp(now)
    }

    private fun hold() {
        held = finger || keys.isNotEmpty()
    }
}
