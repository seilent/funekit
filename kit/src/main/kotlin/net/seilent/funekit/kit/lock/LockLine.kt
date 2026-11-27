package net.seilent.funekit.kit.lock

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.lerp
import java.text.Bidi
import kotlin.math.max

private const val DIP_LEGS = 2

class LockText(val text: String, val base: Int, val right: Boolean) {
    companion object {
        val none = LockText("", Bidi.DIRECTION_LEFT_TO_RIGHT, false)
    }
}

class LockLine {
    private val d = Tok.motion_valueChange.toLong()
    private var cur = LockText.none
    private var old = LockText.none
    private var at: Long? = null

    val text: LockText get() = cur

    fun set(text: String, base: Int, right: Boolean, now: Long) {
        if (text == cur.text && (text.isEmpty() || (base == cur.base && right == cur.right))) return
        old = cur
        cur = LockText(text, base, right)
        at = now
    }

    fun cut() {
        at = null
    }

    fun shown(now: Long): LockText {
        val a = at ?: return cur
        return if (now - a < d) old else cur
    }

    fun alpha(now: Long): Float {
        val a = at ?: return 1f
        val e = max(0L, now - a)
        if (e < d) return lerp(1f, Tok.motion_valueDip, e.toFloat() / d)
        if (e < DIP_LEGS * d) return lerp(Tok.motion_valueDip, 1f, (e - d).toFloat() / d)
        return 1f
    }

    fun moving(now: Long): Boolean {
        val a = at ?: return false
        return now - a < DIP_LEGS * d
    }
}
