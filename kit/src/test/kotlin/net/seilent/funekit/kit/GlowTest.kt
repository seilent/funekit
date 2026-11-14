package net.seilent.funekit.kit

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowTest {
    @Test
    fun dilateSpreadsAPixelOverItsSquare() {
        val src = IntArray(25).also { it[12] = 200 }
        val one = GlowMath.dilate(src, 5, 5, 1)
        for (y in 0 until 5) for (x in 0 until 5) {
            val inside = x in 1..3 && y in 1..3
            assertEquals("($x, $y)", if (inside) 200 else 0, one[y * 5 + x])
        }
        assertTrue(GlowMath.dilate(src, 5, 5, 2).all { it == 200 })
        assertArrayEquals(src, GlowMath.dilate(src, 5, 5, 0))
    }

    @Test
    fun oneBlurPassAtReachOneSpreadsAPixelEvenly() {
        val out = GlowMath.glow(intArrayOf(255), 1, 1, 1, 1)
        assertArrayEquals(IntArray(9) { 28 }, out)
    }

    @Test
    fun reachTwoRunsTwoPassesOfRadiusOne() {
        val out = GlowMath.glow(intArrayOf(255), 1, 1, 2, 2)
        val want = intArrayOf(
            3, 6, 9, 6, 3,
            6, 13, 19, 13, 6,
            9, 19, 28, 19, 9,
            6, 13, 19, 13, 6,
            3, 6, 9, 6, 3,
        )
        assertArrayEquals(want, out)
    }

    @Test
    fun noGrowLeavesAnEmptyMask() {
        assertArrayEquals(IntArray(4), GlowMath.glow(intArrayOf(255, 255, 255, 255), 2, 2, 0, 0))
    }

    @Test
    fun dilateAndBlurInPxPerBoard() {
        assertEquals(listOf(1, 3, 4), listOf(1f, 2f, 3f).map { px(Tok.pressGlowDilate, it) })
        assertEquals(listOf(2, 4, 5), listOf(1f, 2f, 3f).map { px(Tok.pressGlow, it) })
        assertEquals(0.75f, Tok.pressGlowAlpha, 0f)
    }

    @Test
    fun theGlowRisesForFiftyMsFromTheDownAndClears() {
        val g = Glow()
        assertFalse(g.moving(0))
        g.down(3, 1000)
        assertEquals(3, g.key)
        assertTrue(g.moving(1049))
        assertFalse(g.moving(1050))
        assertTrue(g.off())
        assertEquals(null, g.key)
        assertFalse(g.off())
    }
}
