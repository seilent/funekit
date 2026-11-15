package net.seilent.funekit.kit.proj

import androidx.compose.ui.geometry.Offset
import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
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
    fun theTurnMapsThePageCornersAsTheProjection() {
        val ppu = 3.0
        val d = (2.3 * 240 * ppu).roundToInt().toDouble()
        val hinge = (-89.1 * ppu).roundToInt().toDouble()
        val eye = (0.5148 * 1280).roundToInt().toDouble()
        for (t in listOf(150.0, 200.0, 250.0, 300.0, 400.0)) {
            val deg = 39.4 * (1 - quartOut(((t - 150) / 400).coerceIn(0.0, 1.0)))
            val m = turn(deg.toFloat(), hinge.toFloat(), eye.toFloat(), d.toFloat())
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

    private fun ay(x: Double, y: Double, deg: Double, h: Double, eye: Double, d: Double, cx: Double): Pair<Double, Double> {
        val th = deg * PI / 180
        val c = cos(th)
        val sn = sin(th)
        val es = eye * sn / d
        val u = (1 - h * sn / d) * x + (h * c - h + h * cx * sn / d)
        val v = -es * x + (c + (cx - h) * sn / d) * y + es * h
        val w = -sn / d * x + c + cx * sn / d
        return Pair(u / w, v / w)
    }

    @Test
    fun aCameraAtTheHingeIsThePlainTurn() {
        for (deg in listOf(-60f, -20f, 0f, 30f, 47.6f)) {
            val a = camTurn(deg, -175f, 656f, 2304f, -175f)
            val b = turn(deg, -175f, 656f, 2304f)
            for ((x, y) in listOf(0f to 0f, 720f to 0f, 0f to 1280f, 720f to 1280f)) {
                assertEquals("x $deg", b.map(x, y).x, a.map(x, y).x, 0.05f)
                assertEquals("y $deg", b.map(x, y).y, a.map(x, y).y, 0.05f)
            }
        }
    }

    @Test
    fun aCameraOffTheHingeMapsEachPointBackThroughTheFuneOsInverse() {
        val h = -175.0
        val eye = 656.0
        val d = 2304.0
        val cx = 360.0
        for (deg in listOf(-40.0, 25.0, 47.6)) {
            val m = camTurn(deg.toFloat(), h.toFloat(), eye.toFloat(), d.toFloat(), cx.toFloat())
            for ((x, y) in listOf(0.0 to 0.0, 720.0 to 0.0, 0.0 to 1280.0, 720.0 to 1280.0, 360.0 to 640.0)) {
                val o = m.map(x.toFloat(), y.toFloat())
                val (bx, by) = ay(o.x.toDouble(), o.y.toDouble(), deg, h, eye, d, cx)
                assertEquals("x $deg ($x, $y)", x, bx, 0.05)
                assertEquals("y $deg ($x, $y)", y, by, 0.05)
            }
        }
    }

    @Test
    fun aPlaneFacesTheCameraUntilItTurnsPastItsEdge() {
        assertEquals(true, facing(60f, 0f, 1000f, 0f))
        assertEquals(false, facing(100f, 0f, 1000f, 0f))
        assertEquals(true, facing(100f, 0f, 1000f, 1000f))
    }

    @Test
    fun theEdgeAngleTurnsAnEdgeOntoItsTarget() {
        assertEquals(0f, edgeAngle(100.2f, 300f, 100f, 2304f), 0f)
        assertEquals(0f, edgeAngle(400f, 300f, 100f, 0f), 0f)
        for (to in listOf(150f, 250f, 350f)) {
            val deg = edgeAngle(400f, to, 100f, 2304f)
            assertEquals("edge to $to", to, turn(deg, 100f, 640f, 2304f).map(400f, 640f).x, 0.5f)
        }
    }

    @Test
    fun theEdgeAngleStopsAtTheEdgeOnAngle() {
        val edge = 89.5
        assertEquals(edge, Tok.motion_npEdgeOn.toDouble(), 0.0)
        val up = Math.toDegrees(atan2(5100.0 / 2304, 1.0) + PI)
        val down = Math.toDegrees(atan2(-4900.0 / 2304, 1.0) - PI)
        assertTrue("$up", up > edge)
        assertTrue("$down", down < -edge)
        assertEquals(edge, edgeAngle(400f, -5000f, 100f, 2304f).toDouble(), 1e-4)
        assertEquals(-edge, edgeAngle(0f, 5000f, 100f, 2304f).toDouble(), 1e-4)
        assertEquals(Math.toDegrees(atan2(-1900.0 / 2304, 1.0)), edgeAngle(400f, 2000f, 100f, 2304f).toDouble(), 1e-3)
    }
}
