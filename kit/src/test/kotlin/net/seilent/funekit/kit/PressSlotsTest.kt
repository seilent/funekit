package net.seilent.funekit.kit

import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressSlotsTest {
    @Test
    fun aSecondPressLetsTheFirstReturnAndAThirdSnapsIt() {
        val p = PressSlots()
        p.down(1, 0)
        assertEquals(0.96f, p.scale(1, 100), 0.001f)
        p.down(2, 100)
        val back = p.scale(1, 150)
        assertTrue("returning $back", back > 0.96f && back < 1f)
        assertEquals(0.98f, p.scale(2, 150), 0.0026f)
        p.down(3, 150)
        assertEquals(1f, p.scale(1, 150), 0f)
        assertTrue(p.scale(2, 160) < 1f)
    }

    @Test
    fun aKeyHeldOnTheFocusMovesThePressWithTheFocus() {
        val p = PressSlots()
        p.keyDown(4, true, 0)
        p.focus(5, true, 50)
        val back = p.scale(4, 100)
        assertTrue("returning $back", back > 0.96f && back < 1f)
        assertEquals(1f, p.scale(7, 50), 0f)
        assertTrue(p.scale(5, 150) < 1f)
        p.keyUp(150)
        assertEquals(1f, p.scale(5, 400), 0f)
    }

    @Test
    fun aSlotThatIsNeverDrawnAgainClearsStaleAfterOneLook() {
        val p = PressSlots()
        p.down(1, 0)
        p.look(1, 50)
        p.tap(60)
        assertTrue(p.stale(1000))
        p.look(1, 1000)
        assertFalse(p.stale(1000))
    }

    @Test
    fun aShownSlotIsStaleOnlyWhileItsPressMoves() {
        val p = PressSlots()
        p.down(1, 0)
        p.look(1, 40)
        assertTrue(p.stale(40))
        p.tap(100)
        p.look(1, 500)
        assertFalse(p.stale(500))
    }

    @Test
    fun everyMutatorAndSlotSwapBumpsTheRevision() {
        val p = PressSlots()
        val mutators = listOf<(PressSlots) -> Unit>(
            { it.down(1, 0) },
            { it.down(2, 10) },
            { it.focus(3, true, 20) },
            { it.keyDown(4, true, 30) },
            { it.keyUp(40) },
            { it.tap(50) },
            { it.cancel(60) },
            { it.letGo(70) },
            { it.drop() },
            { it.reset() },
            { it.down(5, 75) },
            { it.hold(76) },
            { it.cutRing(77) },
            { it.untap(78) },
        )
        for (m in mutators) {
            val r = p.revision
            m(p)
            assertTrue(p.revision > r)
        }
        val r = p.revision
        p.look(1, 100)
        p.stale(110)
        assertEquals(r, p.revision)
    }

    @Test
    fun eachSlotAnswersItsOwnLitRingAndLook() {
        val p = PressSlots()
        p.down(1, 0)
        p.down(2, 100)
        assertEquals(0.5f, p.lit(1, 150), 0.0001f)
        assertEquals(0.5f, p.ring(1, 150), 0.0001f)
        assertEquals(1f, p.lit(2, 150), 0f)
        assertEquals(1f, p.ring(2, 150), 0f)
        assertEquals(0f, p.lit(3, 150), 0f)
        assertEquals(1f, p.look(2, 150), 0f)
        assertFalse(p.stale(2, 150))
        assertEquals(1f, p.scale(3, 150), 0f)
        assertTrue(p.held)
    }

    @Test
    fun aKeyUpCutsTheLitAndATapThatDoesNotActKeepsIt() {
        val p = PressSlots()
        p.keyDown(1, true, 0)
        p.keyUp(100)
        assertEquals(0f, p.lit(1, 100), 0f)
        assertFalse(p.held)
        val q = PressSlots()
        q.down(1, 0)
        q.tap(100, acts = false)
        assertEquals(1f, q.lit(1, 200), 0f)
        assertEquals(0f, q.lit(1, 300), 0f)
    }

    @Test
    fun aHoldPressesBothSlotsAtOnce() {
        val p = PressSlots()
        p.down(1, 0)
        p.down(2, 10)
        p.hold(20)
        assertEquals(Tok.motion_pressScale, p.scale(2, 20), 0f)
        assertEquals(1f, p.lit(2, 20), 0f)
    }

    @Test
    fun lookAtATimeMarksBothSlotsDrawn() {
        val p = PressSlots()
        p.down(1, 0L)
        p.look(SHOWN)
        p.down(2, SHOWN + 100L)
        assertEquals(1, p.back.item)
        assertEquals(2, p.on.item)
        assertTrue(p.stale(1, SETTLED))
        assertTrue(p.stale(2, SETTLED))
        p.look(SETTLED)
        assertFalse(p.stale(1, SETTLED))
        assertFalse(p.stale(2, SETTLED))
        assertFalse(p.stale(SETTLED))
    }

    private companion object {
        const val SHOWN = 300L
        const val SETTLED = 10000L
    }
}
