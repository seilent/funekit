package net.seilent.funekit.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Case
import net.seilent.funekit.Fonts
import net.seilent.funekit.Role
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.textPx
import kotlin.math.ceil

private const val ASCENT = 2027
private const val DESCENT = 431
private const val EM = 2048
private const val HALF = 2
private const val WHITE = 0xFFFFFFFFL

object Metrics {
    fun ascent(px: Int): Int = ceil(ASCENT.toDouble() * px / EM).toInt()

    fun lineAscent(px: Int): Int = ceil(ASCENT.toDouble() * px / EM - 1.0 / HALF).toInt()

    fun descent(px: Int): Int = ceil(DESCENT.toDouble() * px / EM).toInt()

    fun capTop(baseline: Int, px: Int): Int = baseline - Math.round(Tok.capHeight * px)

    fun centered(top: Int, height: Int, px: Int): Int = top + (height - ascent(px) - descent(px)) / HALF + ascent(px)
}

object Ink {
    val white = Color(WHITE)
    val bg = Color(Tok.color_bg)
    val elevated = Color(Tok.color_bgElevated)
    val tile = Color(Tok.color_tile)
    val tilePressed = Color(Tok.color_tilePressed)
    val border = Color(Tok.color_border)
    val header = Color(Tok.color_pageHeader)
    val pivotInactive = Color(Tok.color_pivotInactive)
    val black = Color(Tok.color_black)
}

fun cased(text: String, role: Role): String = when (role.case) {
    Case.Lower -> buildString(text.length) { for (c in text) append(if (c in 'A'..'Z') c.lowercaseChar() else c) }
    Case.Upper -> buildString(text.length) { for (c in text) append(if (c in 'a'..'z') c.uppercaseChar() else c) }
    Case.Mixed -> text
}

fun rolePx(role: Role, scale: Scale, ppu: Float = scale.ppu): Int = textPx(role.size, ppu, scale.board.minTextPx)

fun roleStyle(role: Role, scale: Scale, density: Density, color: Color, alpha: Float = role.alpha, ppu: Float = scale.ppu): TextStyle =
    Fonts.style(role, rolePx(role, scale, ppu), density, role.tracking * ppu).merge(
        TextStyle(
            color = color.copy(alpha = color.alpha * alpha),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
        ),
    )
