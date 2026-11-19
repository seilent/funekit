package net.seilent.funekit.screens.posture

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Fold(val top: Int, val bottom: Int)

object Posture {
    fun of(
        state: FoldingFeature.State,
        orientation: FoldingFeature.Orientation,
        separating: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        w: Int,
        h: Int,
    ): Fold? {
        if (state != FoldingFeature.State.HALF_OPENED) return null
        if (orientation != FoldingFeature.Orientation.HORIZONTAL) return null
        if (!separating) return null
        if (left > 0 || right < w || top <= 0 || bottom >= h || top > bottom) return null
        return Fold(top, bottom)
    }

    fun fold(info: WindowLayoutInfo, w: Int, h: Int): Fold? {
        val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() ?: return null
        val b = f.bounds
        return of(f.state, f.orientation, f.isSeparating, b.left, b.top, b.right, b.bottom, w, h)
    }
}

fun postures(activity: ComponentActivity): Flow<Fold?> = WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
    val b = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
    Posture.fold(info, b.width(), b.height())
}

@Composable
fun rememberPosture(activity: ComponentActivity): State<Fold?> {
    val fold = remember(activity) { mutableStateOf<Fold?>(null) }
    LaunchedEffect(activity) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            postures(activity).collect { fold.value = it }
        }
    }
    return fold
}
