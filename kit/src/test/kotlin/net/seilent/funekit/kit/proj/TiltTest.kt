package net.seilent.funekit.kit.proj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val FULL = 0.24434609527920614
private const val HALF_FULL = 0.12217304763960307
private const val EXACT = 1e-15
private const val LAW = 1e-9
private const val REACH = 88.0
private const val MAX_DEG = 14.0
private const val CX = 120.0
private const val CY = 160.0
private const val D = 552.0

class TiltTest {
    private fun mapX(h: DoubleArray, x: Double, y: Double): Double = (h[0] * x + h[1] * y + h[2]) / (h[6] * x + h[7] * y + h[8])

    private fun mapY(h: DoubleArray, x: Double, y: Double): Double = (h[3] * x + h[4] * y + h[5]) / (h[6] * x + h[7] * y + h[8])

    @Test
    fun theFullTurnIsFourteenDegrees() {
        assertEquals(FULL, MAX_DEG * PI / 180, EXACT)
    }

    @Test
    fun aDragHalfTheReachRightAndTheReachUpTurnsSevenAndFourteenDegrees() {
        val p = platePose(44.0, -REACH, REACH, MAX_DEG)
        assertEquals(-HALF_FULL, p.ry, EXACT)
        assertEquals(-FULL, p.rx, EXACT)
        assertEquals(0.0, p.depth, 0.0)
    }

    @Test
    fun theCornerClampsAtTheFullTurnTheCentreIsFlatAndTheEdgeTurnsYOnly() {
        val c = platePose(-2 * REACH, -2 * REACH, REACH, MAX_DEG)
        assertEquals(-FULL, c.rx, EXACT)
        assertEquals(FULL, c.ry, EXACT)
        assertEquals(0.0, c.depth, 0.0)
        val m = platePose(0.0, 0.0, REACH, MAX_DEG)
        assertEquals(0.0, m.rx, 0.0)
        assertEquals(0.0, m.ry, 0.0)
        val e = platePose(2 * REACH, 0.0, REACH, MAX_DEG)
        assertEquals(0.0, e.rx, 0.0)
        assertEquals(-FULL, e.ry, EXACT)
    }

    @Test
    fun theMatrixTurnsThePlateAboutItsCentreAndProjectsItFromTheEye() {
        val c = platePose(-2 * REACH, -2 * REACH, REACH, MAX_DEG)
        val h = tiltMatrix(c, 1.0, CX, CY, D)
        assertEquals(9, h.size)
        for (sx in listOf(45.0, 195.0)) {
            for (sy in listOf(85.0, 235.0)) {
                val x = sx - CX
                val y = sy - CY
                val ca = cos(c.rx)
                val sa = sin(c.rx)
                val cb = cos(c.ry)
                val sb = sin(c.ry)
                val x3 = x * cb + y * sa * sb
                val y3 = y * ca
                val z3 = -x * sb + y * sa * cb
                val k = D / (D + z3 + c.depth)
                assertEquals("x of ($sx, $sy)", CX + x3 * k, mapX(h, sx, sy), LAW)
                assertEquals("y of ($sx, $sy)", CY + y3 * k, mapY(h, sx, sy), LAW)
            }
        }
    }

    @Test
    fun aHalfReturnScalesThePose() {
        val c = platePose(-2 * REACH, -2 * REACH, REACH, MAX_DEG)
        val whole = mapX(tiltMatrix(c, 1.0, CX, CY, D), 45.0, 85.0)
        val half = mapX(tiltMatrix(c, 0.5, CX, CY, D), 45.0, 85.0)
        assertTrue("$half > 45", half > 45.0)
        assertTrue("$half < $whole", half < whole)
    }

    @Test
    fun noTurnIsTheEyeDistanceTimesTheIdentity() {
        val h = tiltMatrix(TiltPose(0.0, 0.0), 1.0, CX, CY, D)
        val id = doubleArrayOf(D, 0.0, 0.0, 0.0, D, 0.0, 0.0, 0.0, D)
        for (i in 0 until 9) assertTrue("h[$i] ${h[i]}", abs(h[i] - id[i]) < LAW)
    }
}
