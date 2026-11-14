package net.seilent.funekit.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Leg
import net.seilent.funekit.kit.motion.lerp
import kotlin.math.max

fun focusAfterScroll(row: Int, count: Int, off: Long, view: Long, pitch: Long, collapse: Long = 0L): Int {
    if (count == 0) return row
    val top = row.toLong() * pitch
    val band = off - off.coerceIn(0L, collapse)
    if (top >= band && top + pitch <= off + view) return row
    val first = ((band + pitch - 1) / pitch).toInt()
    val last = max(first, ((off + view) / pitch).toInt() - 1)
    return (if (top < band) first else last).coerceIn(0, count - 1)
}

class FocusTrack {
    var revision by mutableIntStateOf(0)
        private set
    var row = 0
        private set
    private var prev = -1
    private var into = Leg(1f, 1f, 0f, 0f)
    private var intoAt = 0L
    private var out = Leg(0f, 0f, 0f, 0f)
    private var outAt = 0L

    fun attach() {
        revision++
        row = 0
        prev = -1
        into = Leg(1f, 1f, 0f, 0f)
        out = Leg(0f, 0f, 0f, 0f)
    }

    fun rowAlpha(r: Int, now: Long, focused: Float, other: Float): Float = when (r) {
        row -> lerp(other, focused, into.at((now - intoAt).toFloat()))
        prev -> lerp(other, focused, out.at((now - outAt).toFloat()))
        else -> other
    }

    fun set(r: Int, count: Int, now: Long, animate: Boolean) {
        if (count == 0) return
        val to = r.coerceIn(0, count - 1)
        if (to == row) return
        revision++
        val a = if (animate) Tok.motion_focusAlpha else 0f
        val was = into.at((now - intoAt).toFloat())
        prev = row
        out = Leg(was, 0f, 0f, a)
        outAt = now
        into = Leg(0f, 1f, 0f, a)
        intoAt = now
        row = to
    }

    fun afterScroll(count: Int, off: Long, view: Long, pitch: Long, now: Long) =
        set(focusAfterScroll(row, count, off, view, pitch), count, now, false)

    fun animating(now: Long): Boolean = now - intoAt < into.end || now - outAt < out.end
}
