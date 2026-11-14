package net.seilent.funekit.kit

import androidx.compose.ui.graphics.Color
import net.seilent.funekit.Weight
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class AccentTest {
    private fun hsv(h: Double, s: Double, v: Double): IntArray {
        val c = v * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60).toInt()) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return intArrayOf(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    private fun lin(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun rule(h: Int): IntArray {
        for (pct in 83 downTo 40) {
            val rgb = hsv(h.toDouble(), pct / 100.0, 0.98)
            if (0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2]) >= 0.149) return rgb
        }
        return hsv(h.toDouble(), 0.40, 0.98)
    }

    private fun rgb(c: Color) = intArrayOf((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

    @Test
    fun theDefaultIs350AsFA2A4D() {
        assertEquals(350, Accent.defaultHue)
        assertEquals(listOf(0xFA, 0x2A, 0x4D), rgb(Accent.forHue(350)).toList())
    }

    @Test
    fun the36HuesFollowTheDesignRule() {
        for (h in 0 until 360 step 10) assertEquals("hue $h", rule(h).toList(), rgb(Accent.forHue(h)).toList())
    }

    @Test
    fun huesSnapToTheTenDegreeGrid() {
        assertEquals(0, Accent.onGrid(355))
        assertEquals(350, Accent.onGrid(354))
        assertEquals(0, Accent.onGrid(-5))
        assertEquals(350, Accent.onGrid(-6))
        assertEquals(10, Accent.onGrid(370))
    }

    @Test
    fun accentOffIsWhiteAndBold() {
        assertEquals(listOf(0xFF, 0xFF, 0xFF), rgb(Accent.color(350, off = true)).toList())
        assertEquals(Weight.Bold, Accent.bold(Weight.Regular, off = true))
        assertEquals(Weight.Regular, Accent.bold(Weight.Regular, off = false))
    }
}
