package net.seilent.funekit.kit.home

import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val FRAME = 16L

private class Frames(val m: HomeMenu) {
    private var t = 0L

    fun to(now: Long) {
        while (t <= now) {
            m.tick(t)
            t += FRAME
        }
    }
}

class HomeMenuTest {
    private fun level(m: HomeMenu, now: Long): Float? = m.restAt(now, false).items[2].glow

    @Test
    fun theTokensTheCasesAreWorkedFrom() {
        assertEquals(200f, Tok.motion_focusAlpha, 0f)
        assertEquals(100f, Tok.motion_press, 0f)
        assertEquals(50f, Tok.motion_pressGlow, 0f)
    }

    @Test
    fun aSlopExitWhileTheRowIsLitFadesTheGlowWithThePressAndClearsItOnceTheRowGoesUp() {
        val m = HomeMenu(4)
        val f = Frames(m)
        m.down(2, 0L)
        f.to(49L)
        m.cancel(50L)
        assertEquals(1f, level(m, 50L)!!, 1e-4f)
        f.to(100L)
        assertEquals(0.5f, level(m, 100L)!!, 1e-4f)
        f.to(150L)
        assertEquals(0f, level(m, 150L)!!, 0f)
        f.to(192L)
        assertEquals(2, m.glow.key)
        f.to(208L)
        assertNull(m.glow.key)
    }

    @Test
    fun aSlopExitAfterTheRowWentUpFadesTheGlowWithThePressAndClearsItAtTheFadeEnd() {
        val m = HomeMenu(4)
        val f = Frames(m)
        m.down(2, 0L)
        f.to(249L)
        m.cancel(250L)
        assertEquals(1f, level(m, 250L)!!, 1e-4f)
        f.to(300L)
        assertEquals(0.5f, level(m, 300L)!!, 1e-4f)
        f.to(336L)
        assertEquals(2, m.glow.key)
        f.to(352L)
        assertNull(m.glow.key)
    }

    @Test
    fun theNextDownAfterASlopExitClearsTheFadingGlow() {
        val m = HomeMenu(4)
        val f = Frames(m)
        m.down(2, 0L)
        f.to(49L)
        m.cancel(50L)
        f.to(112L)
        assertEquals(2, m.glow.key)
        m.down(1, 120L)
        assertEquals(1, m.glow.key)
        assertNull(level(m, 120L))
    }
}
