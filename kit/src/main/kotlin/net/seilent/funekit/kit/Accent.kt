package net.seilent.funekit.kit

import androidx.compose.ui.graphics.Color
import net.seilent.funekit.Weight
import net.seilent.funekit.gen.Tok

private const val HALF = 2

object Accent {
    private val step = Tok.accent_hueStep.toInt()
    private val count = Tok.accent_hueCount.toInt()
    private val hues = listOf(
        Color(Tok.accent_h0), Color(Tok.accent_h10), Color(Tok.accent_h20), Color(Tok.accent_h30),
        Color(Tok.accent_h40), Color(Tok.accent_h50), Color(Tok.accent_h60), Color(Tok.accent_h70),
        Color(Tok.accent_h80), Color(Tok.accent_h90), Color(Tok.accent_h100), Color(Tok.accent_h110),
        Color(Tok.accent_h120), Color(Tok.accent_h130), Color(Tok.accent_h140), Color(Tok.accent_h150),
        Color(Tok.accent_h160), Color(Tok.accent_h170), Color(Tok.accent_h180), Color(Tok.accent_h190),
        Color(Tok.accent_h200), Color(Tok.accent_h210), Color(Tok.accent_h220), Color(Tok.accent_h230),
        Color(Tok.accent_h240), Color(Tok.accent_h250), Color(Tok.accent_h260), Color(Tok.accent_h270),
        Color(Tok.accent_h280), Color(Tok.accent_h290), Color(Tok.accent_h300), Color(Tok.accent_h310),
        Color(Tok.accent_h320), Color(Tok.accent_h330), Color(Tok.accent_h340), Color(Tok.accent_h350),
    )

    val defaultHue: Int = Tok.accent_hueDefault.toInt()

    fun onGrid(hue: Int): Int {
        val n = Math.floorDiv(hue + step / HALF, step) % count
        return (n + count) % count * step
    }

    fun forHue(hue: Int): Color = hues[onGrid(hue) / step]

    fun color(hue: Int, off: Boolean): Color = if (off) Ink.white else forHue(hue)

    fun bold(weight: Weight, off: Boolean): Weight = if (off && weight != Weight.Bold) Weight.Bold else weight
}
