package net.seilent.funekit.kit.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun rememberOverlayFrame(
    overlay: Overlay,
    now: () -> Long,
    animating: (Long) -> Boolean,
    wakeAt: (Long) -> Long?,
    version: () -> Int = { 0 },
    step: (Long) -> Unit = {},
): State<Long> {
    val busy by rememberUpdatedState(animating)
    val due by rememberUpdatedState(wakeAt)
    val seenOf by rememberUpdatedState(version)
    val onFrame by rememberUpdatedState(step)
    val frame = remember(overlay) { mutableLongStateOf(maxOf(overlay.start, overlay.risen, now())) }
    LaunchedEffect(overlay) {
        while (true) {
            val seen = overlay.revision to seenOf()
            withFrameMillis { frame.longValue = it }
            val t = frame.longValue
            onFrame(t)
            if (overlay.moving(t) || busy(t)) continue
            val w = due(t)
            if (w != null) check(w > t) { "wakeAt returns a time after now" }
            if (w == null) {
                snapshotFlow { overlay.revision to seenOf() }.first { it != seen }
            } else {
                withTimeoutOrNull(w - t) { snapshotFlow { overlay.revision to seenOf() }.first { it != seen } }
            }
        }
    }
    return frame
}
