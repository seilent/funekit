package net.seilent.funekit.kit.touch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints

@Composable
fun FuneList(a: Arbiter, touch: ListTouch, modifier: Modifier = Modifier, layer: Int = 0, rowTap: (Int) -> Tap? = { null }, content: LazyListScope.() -> Unit) {
    if (touch.rows == null) {
        LazyColumn(
            modifier.listTarget(a, touch, layer).graphicsLayer { translationY = -touch.over },
            state = touch.state,
            userScrollEnabled = false,
            content = content,
        )
        return
    }
    SideEffect { touch.rowTap = rowTap }
    val d = LocalDensity.current
    Box(modifier.listTarget(a, touch, layer)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationY = -touch.over
                clip = true
            },
        ) {
            LazyColumn(
                Modifier.layout { m, c ->
                    val p = m.measure(Constraints.fixed(c.maxWidth, c.maxHeight + touch.pitch))
                    layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
                }.graphicsLayer { translationY = -touch.sub.toFloat() },
                state = touch.state,
                contentPadding = PaddingValues(bottom = with(d) { touch.pitch.toDp() }),
                userScrollEnabled = false,
                content = content,
            )
        }
    }
}
