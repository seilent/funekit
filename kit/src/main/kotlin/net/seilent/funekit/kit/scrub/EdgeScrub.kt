package net.seilent.funekit.kit.scrub

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import net.seilent.funekit.Platform
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Axis
import net.seilent.funekit.kit.touch.axisOf
import net.seilent.funekit.px

@Composable
fun <T : Any> Modifier.edgeScrub(scale: Scale, buckets: List<T>, arbiter: Arbiter? = null, layer: Int = 0, onBucket: (T) -> Unit): Modifier =
    edgeScrub(px(Tok.gesture_edgeRight, scale.touchPpu).toFloat(), px(Tok.touchSlop, scale.touchPpu).toFloat(), buckets, arbiter, layer, onBucket)

@Composable
fun <T : Any> Modifier.edgeScrub(
    bandPx: Float,
    slopPx: Float,
    buckets: List<T>,
    arbiter: Arbiter? = null,
    layer: Int = 0,
    onBucket: (T) -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val cap = Platform.exclusionCapPx(LocalDensity.current.density).toFloat()
    val current by rememberUpdatedState(onBucket)
    return systemGestureExclusion { c ->
        if (buckets.isEmpty()) {
            Rect.Zero
        } else {
            val z = Scrub.zone(c.size.width.toFloat(), c.size.height.toFloat(), bandPx, cap)
            Rect(z.left, z.top, z.right, z.bottom)
        }
    }.pointerInput(buckets, bandPx, slopPx, cap, arbiter, layer) {
        fun hit(b: T) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            current(b)
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val h = size.height.toFloat()
            val zone = Scrub.zone(size.width.toFloat(), h, bandPx, cap)
            if (arbiter != null && arbiter.modal > layer) return@awaitEachGesture
            if (buckets.isEmpty() || !zone.contains(down.position.x, down.position.y)) return@awaitEachGesture
            val s = Scrub(buckets)
            val x0 = down.position.x
            val y0 = down.position.y
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) {
                    if (s.active) c.consume()
                    break
                }
                if (!s.active) {
                    val dx = c.position.x - x0
                    val dy = c.position.y - y0
                    if (dx * dx + dy * dy <= slopPx * slopPx) continue
                    if (axisOf(dx, dy) == Axis.Horizontal) break
                    s.down(x0, y0, zone)
                }
                s.move(c.position.y, h)?.let { hit(it) }
                c.consume()
            }
            s.up()
        }
    }
}
