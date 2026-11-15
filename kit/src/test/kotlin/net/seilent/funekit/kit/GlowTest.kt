package net.seilent.funekit.kit

import androidx.compose.ui.unit.Density
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
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

    @Test
    fun theLevelRisesOnTheCubicAndAFadeTakesItToZeroOverThePress() {
        val g = Glow()
        g.down(3, 1000)
        assertEquals(Curves.cubicOut.at(0.5f), g.level(1025), 0.0001f)
        g.fade(1050)
        assertTrue(g.fading)
        assertEquals(0.5f, g.level(1100), 0.0001f)
        assertTrue(g.moving(1100))
        assertEquals(0f, g.level(1150), 0f)
        assertFalse(g.moving(1150))
    }

    @Test
    fun anIdleUpHoldsTheGlowForTheTouchReturnAndASetLevelWins() {
        val g = Glow()
        g.down(3, 1000)
        g.idleUp(1100)
        assertEquals(1300L, g.due(1100))
        assertTrue(g.live(1200))
        assertEquals(1f, g.level(1299), 0f)
        assertEquals(0f, g.level(1300), 0f)
        val f = Glow()
        assertEquals(null, f.follow(5, 0f, false))
        assertEquals(f, f.follow(5, 0.4f, true))
        assertEquals(5, f.key)
        assertEquals(0.4f, f.level(2000), 0f)
        assertEquals(null, f.follow(5, 0f, false))
        assertEquals(null, f.key)
    }

    @Test
    fun theMaskIsKeptPerTextClipAndPpu() {
        val g = Glow()
        val d = Density(1f)
        g.mask(d, emptyList(), Edges(0, 0, 10, 10), 3f)
        g.mask(d, emptyList(), Edges(0, 0, 10, 10), 3f)
        assertEquals(1, g.builds)
        g.mask(d, emptyList(), Edges(0, 0, 10, 12), 3f)
        assertEquals(2, g.builds)
        g.mask(d, emptyList(), Edges(0, 0, 10, 12), 2f)
        assertEquals(3, g.builds)
    }

    @Test
    fun aSecondRowsPressSwapsTheFirstRowsGlowOntoTheBackGlowAtItsLevel() {
        val p = GlowPair()
        p.arm(3, 0L)
        p.glow.fade(100L)
        val alone = Glow().also {
            it.down(3, 0L)
            it.fade(100L)
        }
        p.arm(5, 150L)
        assertEquals(3, p.back.key)
        assertEquals(alone.level(150L), p.back.level(150L), 0f)
        assertTrue(p.back.level(150L) > 0f)
        assertEquals(5, p.glow.key)
        assertEquals(0f, p.glow.level(150L), 0f)
    }

    @Test
    fun aPressOfTheRowTheBackGlowHoldsClearsTheBackGlow() {
        val p = GlowPair()
        p.arm(5, 0L)
        p.arm(3, 10L)
        assertEquals(5, p.back.key)
        p.glow.off()
        p.arm(5, 20L)
        assertEquals(null, p.back.key)
        assertEquals(5, p.glow.key)
    }

    @Test
    fun offLeavesNeitherGlow() {
        val p = GlowPair()
        p.arm(3, 0L)
        p.arm(5, 10L)
        assertTrue(p.off())
        assertEquals(null, p.glow.key)
        assertEquals(null, p.back.key)
        assertFalse(p.off())
    }
}
