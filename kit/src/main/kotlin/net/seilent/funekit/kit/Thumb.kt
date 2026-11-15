package net.seilent.funekit.kit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px

private const val HALF = 2

class ThumbRect(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

fun thumbOf(s: Scale, top: Int, bottom: Int, view: Long, content: Long, scrolled: Long, ppu: Float = s.ppu): ThumbRect? {
    val t = bottom - top
    val m = px(Tok.scrollbar_min, ppu)
    if (view <= 0 || content <= view || t - m <= px(1f, ppu)) return null
    val len = m + (((t - m).toLong() * view + content / HALF) / content).toInt()
    val room = content - view
    val y = top + (((t - len).toLong() * scrolled.coerceIn(0L, room) + room / HALF) / room).toInt()
    val w = s.widthPx / ppu
    return ThumbRect(px(w - Tok.scrollbar_width, ppu), y, px(w, ppu), y + len)
}

fun listThumbOf(s: Scale, top: Int, bottom: Int, count: Int, pitch: Int, scrolled: Long): ThumbRect? =
    thumbOf(s, top, bottom, (bottom - top).toLong(), count.toLong() * pitch, scrolled)

fun listThumbOf(s: Scale, top: Int, bottom: Int, t: RowTrack): ThumbRect? =
    thumbOf(s, top, bottom, (bottom - top).toLong(), t.topOf(t.count).toLong(), t.q.toLong())

class ThumbFade {
    private val hold = Tok.scrollbar_hold.toLong()
    private val fade = Tok.scrollbar_fade.toLong()
    private var from = 1f
    private var t0 = 0L
    private var running = false
    private var seen = 0L
    private var drawn = 0L
    private var on = false
    private var held = false

    private fun shown(now: Long): Float {
        if (!running) return 1f
        val e = now - t0
        if (e >= fade) return 1f
        return lerp(from, 1f, Curves.pop.at(e.toFloat() / fade))
    }

    private val holdEnd: Long get() = seen + hold

    fun show(now: Long) {
        running = false
        seen = now
        held = false
    }

    fun track(on: Boolean, moving: Boolean, now: Long) {
        if (on && !this.on) {
            show(now)
        } else if (moving) {
            if (now >= holdEnd) {
                from = at(now)
                t0 = now
                running = true
            }
            seen = now
        }
        this.on = on
        held = on && moving
        drawn = now
    }

    fun release(now: Long) {
        if (held) {
            held = false
            seen = now
        }
    }

    fun at(now: Long): Float {
        val end = holdEnd
        if (held || now < end) return shown(now)
        val e = now - end
        if (e >= fade) return 0f
        return lerp(shown(end), 0f, Curves.pop.at(e.toFloat() / fade))
    }

    fun fading(now: Long): Boolean {
        if (!on) return false
        val end = holdEnd
        return if (held || now < end) running && drawn < t0 + fade else drawn < end + fade
    }

    fun wakeAt(now: Long): Long = if (on && !held && now < holdEnd) holdEnd else -1L
}

fun DrawScope.thumb(r: ThumbRect, fade: Float) {
    drawRect(Ink.white, Offset(r.x0.toFloat(), r.y0.toFloat()), Size((r.x1 - r.x0).toFloat(), (r.y1 - r.y0).toFloat()), Tok.scrollbar_alpha * fade)
}

data class ThumbProps(val y: Float, val alpha: Float)

fun thumbProps(at: ThumbRect?, fade: Float): ThumbProps = if (at == null) ThumbProps(0f, 0f) else ThumbProps(at.y0.toFloat(), fade)

private class ThumbHold {
    var props: ThumbProps? = null
    var snap = 0
}

@Composable
fun ThumbLayer(rest: ThumbRect, covered: Boolean, snap: Int, follow: Boolean = false, props: () -> ThumbProps) {
    val hold = remember { ThumbHold() }
    Canvas(
        Modifier.pxAt(Edges(rest.x0, 0, rest.x1, rest.y1 - rest.y0)).graphicsLayer {
            val held = hold.props
            val p = when {
                !covered || held == null || hold.snap != snap -> props()
                follow -> held.copy(y = props().y)
                else -> held
            }
            hold.props = p
            hold.snap = snap
            translationY = p.y
            alpha = p.alpha
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) { drawRect(Ink.white, alpha = Tok.scrollbar_alpha) }
}
