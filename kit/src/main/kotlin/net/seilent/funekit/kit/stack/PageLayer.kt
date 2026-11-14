package net.seilent.funekit.kit.stack

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun PageLayer(visible: Boolean, frozen: Boolean, modifier: Modifier = Modifier, content: @Composable (frozen: Boolean) -> Unit) {
    Box(modifier.graphicsLayer { alpha = if (visible) 1f else 0f }) { content(frozen) }
}
