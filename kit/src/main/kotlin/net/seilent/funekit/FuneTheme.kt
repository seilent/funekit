package net.seilent.funekit

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.gen.Tok

private class Touch(base: ViewConfiguration, override val touchSlop: Float) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = Tok.input_keyHoldMs.toLong()
    override val doubleTapTimeoutMillis: Long get() = 0
    override val doubleTapMinTimeMillis: Long get() = 0
    override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
}

internal data object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
}

@Composable
fun FuneTheme(scale: Scale, content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val density = LocalDensity.current.density
    val touch = remember(base, scale.touchPpu) { Touch(base, Tok.touchSlop * scale.touchPpu) }
    CompositionLocalProvider(
        LocalViewConfiguration provides touch,
        LocalOverscrollFactory provides null,
        LocalIndication provides NoIndication,
        LocalDensity provides Density(density, 1f),
        LocalLayoutDirection provides LayoutDirection.Ltr,
        content = content,
    )
}
