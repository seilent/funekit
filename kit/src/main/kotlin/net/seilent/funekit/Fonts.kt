package net.seilent.funekit

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

private const val SEMI_LIGHT = 350

object Fonts {
    val selawik = FontFamily(Weight.entries.map { Font(file(it), weight(it)) })

    fun file(w: Weight): Int = when (w) {
        Weight.Light -> R.font.selawkl
        Weight.SemiLight -> R.font.selawksl
        Weight.Regular -> R.font.selawk
        Weight.Bold -> R.font.selawkb
    }

    fun weight(w: Weight): FontWeight = when (w) {
        Weight.Light -> FontWeight.Light
        Weight.SemiLight -> FontWeight(SEMI_LIGHT)
        Weight.Regular -> FontWeight.Normal
        Weight.Bold -> FontWeight.Bold
    }

    fun style(role: Role, px: Int, density: Density, trackPx: Float = 0f): TextStyle = TextStyle(
        fontFamily = selawik,
        fontWeight = weight(role.weight),
        fontSize = TextUnit(px / (density.density * density.fontScale), TextUnitType.Sp),
        letterSpacing = TextUnit(trackPx / (density.density * density.fontScale), TextUnitType.Sp),
        fontSynthesis = FontSynthesis.None,
        textMotion = TextMotion.Animated,
    )
}
