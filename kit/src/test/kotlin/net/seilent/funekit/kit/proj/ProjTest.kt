package net.seilent.funekit.kit.proj

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class ProjTest {
    private fun project(x: Double, y: Double, deg: Double, hinge: Double, eye: Double, d: Double): Pair<Double, Double> {
        val th = deg * PI / 180
        val big = x - hinge
        val w = 1 + big * sin(th) / d
        return Pair(hinge + big * cos(th) / w, eye + (y - eye) / w)
    }

    private fun quartOut(t: Double): Double {
        val m = 1 - t
        return 1 - m * m * m * m
    }

    @Test
    fun t7PageCornersOnTheA105() {
        val ppu = 3.0
        val d = (2.3 * 240 * ppu).roundToInt().toDouble()
        val hinge = (-89.1 * ppu).roundToInt().toDouble()
        val eye = (0.5148 * 1280).roundToInt().toDouble()
        val e = t7Eye(3f, 1280)
        assertEquals(d, e.d.toDouble(), 0.0)
        assertEquals(hinge, e.hinge.toDouble(), 0.0)
        assertEquals(eye, e.eye.toDouble(), 0.0)
        for (t in listOf(150.0, 200.0, 250.0, 300.0, 400.0)) {
            val deg = 39.4 * (1 - quartOut(((t - 150) / 400).coerceIn(0.0, 1.0)))
            val m = t7(deg.toFloat(), e)
            for ((x, y) in listOf(0.0 to 0.0, 720.0 to 0.0, 0.0 to 1280.0, 720.0 to 1280.0)) {
                val (wx, wy) = project(x, y, deg, hinge, eye, d)
                val got = m.map(x.toFloat(), y.toFloat())
                assertEquals("x at $t ms ($x, $y)", wx, got.x.toDouble(), 0.05)
                assertEquals("y at $t ms ($x, $y)", wy, got.y.toDouble(), 0.05)
            }
        }
        assertEquals(23.10, 39.4 * (1 - quartOut(50.0 / 400)), 0.01)
    }

    @Test
    fun theComposeMatrixMapsLikeTheProjection() {
        val m = turn(30f, -267f, 659f, 1656f)
        val c = m.compose()
        for ((x, y) in listOf(0f to 0f, 720f to 0f, 0f to 1280f, 720f to 1280f)) {
            val want = m.map(x, y)
            val got = c.map(Offset(x, y))
            assertEquals(want.x, got.x, 0.05f)
            assertEquals(want.y, got.y, 0.05f)
        }
    }

    @Test
    fun t6MenuPanelCornersOnTheA105() {
        for (t in listOf(0.0, 150.0, 300.0, 445.0)) {
            val q = t / 445
            val p = q * (2 - q)
            val scale = 1 + p * (0.445 - 1)
            val hinge = kotlin.math.floor(720 * scale)
            val m = t6(p.toFloat(), 3f, 720)
            for ((x, y) in listOf(0.0 to 0.0, 720.0 to 0.0, 0.0 to 1280.0, 720.0 to 1280.0)) {
                val (lx, ly) = project(x, y, p * -6, hinge, 0.0, 1656.0)
                val got = m.map(x.toFloat(), y.toFloat())
                assertEquals("x at $t ms ($x, $y)", p * 164.3 * 3 + lx * scale, got.x.toDouble(), 0.05)
                assertEquals("y at $t ms ($x, $y)", p * 133.3 * 3 + ly * scale, got.y.toDouble(), 0.05)
            }
        }
    }
}
