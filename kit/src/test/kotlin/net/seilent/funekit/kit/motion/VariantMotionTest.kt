package net.seilent.funekit.kit.motion

import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Test

private const val EPS = 1e-6f

class VariantMotionTest {
    private fun check(dir: VariantMotion.Dir, e: Long, from: Boolean, alpha: Float, scale: Float) {
        val f = VariantMotion.frame(dir, e)
        assertEquals("$dir $e from", from, f.from)
        assertEquals("$dir $e alpha", alpha, f.alpha, EPS)
        assertEquals("$dir $e scale", scale, f.scale, EPS)
    }

    @Test
    fun theTokensTheCasesAreWorkedFrom() {
        assertEquals(300f, Tok.motion_nowPlayingIn, 0f)
        assertEquals(0.9f, Tok.motion_nowPlayingScale, 0f)
        assertEquals(300f, Tok.motion_nowPlayingFade, 0f)
        assertEquals(380f, Tok.motion_variantFade, 0f)
    }

    @Test
    fun intoTheVariantTheOldLayerPopsOutThenTheNewOneEasesInFromNinetyPercent() {
        val d = VariantMotion.Dir.In
        val half = Curves.easeIn.at(0.5f)
        check(d, 0, true, 1f, 1f)
        check(d, 150, true, 1 - Curves.pop.at(0.5f), 1f)
        assertEquals(true, VariantMotion.frame(d, 299).from)
        check(d, 300, false, 0f, 0.9f)
        check(d, 450, false, half, 0.9f + 0.1f * half)
        check(d, 600, false, 1f, 1f)
        assertEquals(600L, VariantMotion.total(d))
    }

    @Test
    fun outOfTheVariantTheOldLayerDecelsOutThenTheNewOneDecelsInAtFullSize() {
        val d = VariantMotion.Dir.Out
        val half = Curves.decel.at(0.5f)
        check(d, 190, true, 1 - half, 1f)
        check(d, 380, false, 0f, 1f)
        check(d, 570, false, half, 1f)
        check(d, 760, false, 1f, 1f)
        assertEquals(760L, VariantMotion.total(d))
        check(d, 900, false, 1f, 1f)
    }
}
