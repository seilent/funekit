package net.seilent.funekit.screens.music

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.touch.Pan
import net.seilent.funekit.kit.touch.Swipe
import kotlin.math.max

abstract class PivotPage(entry: Long, page: PageId) : PageModel(entry, page) {
    abstract val switchDir: Int

    abstract fun canSwitch(d: Int): Boolean

    abstract fun switchTo(p: Int, now: Long): Boolean

    abstract fun drag(dxU: Float, now: Long)

    abstract fun release(now: Long)

    abstract fun land()

    abstract fun dragX(now: Long, ppu: Float): Int

    abstract fun motionActive(now: Long): Boolean

    open fun toTop(now: Long): Boolean = false

    abstract fun haltList()

    abstract fun poke()

    open fun unpress(now: Long) {}

    open fun dropUnpress() {}
}

fun pivotSwitchEnd(): Long = (Tok.motion_pivotOut + max(Tok.motion_pivotRows, Tok.motion_pivotRowFade)).toLong()

fun pivotChromeAt(c: Chrome, now: Long): Long {
    val since = c.since(now) ?: return 0L
    val strip = Tok.motion_pivotStrip.toLong()
    return if (since < strip) now else now - since + strip
}

fun pivotLand(m: PivotPage, chrome: Chrome, now: Long) {
    if (!m.motionActive(now)) return
    m.land()
    chrome.select(m.pivot)
    m.poke()
}

fun pivotSwitch(m: PivotPage, chrome: Chrome, to: Int, now: Long) {
    if (to == m.pivot) {
        if (m.toTop(now)) m.poke()
        return
    }
    m.haltList()
    if (m.switchTo(to, now)) chrome.switchTo(m.pivot, now, m.switchDir)
    m.poke()
}

fun pivotBy(s: Swipe): Int = when (s) {
    Swipe.Left -> 1
    Swipe.Right -> -1
    else -> 0
}

fun pivotPan(m: PivotPage, chrome: Chrome, ppu: () -> Float, now: () -> Long, ok: () -> Boolean): Pan = object : Pan {
    private var from = 0f

    override fun begin(x: Float, origin: Float, down: Offset): Boolean {
        if (!ok()) return false
        from = origin
        m.unpress(now())
        drag(x)
        return true
    }

    override fun drag(x: Float) {
        m.drag((x - from) / ppu(), now())
        m.poke()
    }

    override fun end(s: Swipe) {
        val d = pivotBy(s)
        val t = now()
        if (d != 0 && m.canSwitch(d)) pivotSwitch(m, chrome, m.pivot + d, t) else m.release(t)
        m.poke()
    }
}

fun Modifier.pivotWatch(m: PivotPage, chrome: Chrome, live: Boolean, now: () -> Long): Modifier = if (live) {
    pointerInput(m, chrome) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pivotLand(m, chrome, now())
        }
    }
} else {
    this
}
