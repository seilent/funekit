package net.seilent.funekit.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressSlotsTest {
    @Test
    fun aSecondPressLetsTheFirstReturnAndAThirdSnapsIt() {
        val p = PressSlots()
        p.down(1, 0)
        assertEquals(0.96f, p.draw(1, 100), 0.001f)
        p.down(2, 100)
        val back = p.draw(1, 150)
        assertTrue("returning $back", back > 0.96f && back < 1f)
        assertEquals(0.98f, p.draw(2, 150), 0.0026f)
        p.down(3, 150)
        assertEquals(1f, p.draw(1, 150), 0f)
        assertTrue(p.draw(2, 160) < 1f)
    }

    @Test
    fun aKeyHeldOnTheFocusMovesThePressWithTheFocus() {
        val p = PressSlots()
        p.keyDown(4, true, 0)
        p.focus(5, true, 50)
        val back = p.draw(4, 100)
        assertTrue("returning $back", back > 0.96f && back < 1f)
        assertEquals(1f, p.draw(7, 50), 0f)
        assertTrue(p.draw(5, 150) < 1f)
        p.keyUp(150)
        assertEquals(1f, p.draw(5, 400), 0f)
    }

    @Test
    fun aSlotThatIsNeverDrawnAgainClearsStaleAfterOneShow() {
        val p = PressSlots()
        p.down(1, 0)
        p.draw(1, 50)
        p.tap(60)
        assertTrue(p.stale(1000))
        p.show(1000)
        assertFalse(p.stale(1000))
    }

    @Test
    fun aShownSlotIsStaleOnlyWhileItsPressMoves() {
        val p = PressSlots()
        p.down(1, 0)
        p.show(50)
        assertTrue(p.stale(50))
        p.tap(100)
        p.show(500)
        assertFalse(p.stale(500))
    }

    @Test
    fun factorAfterShowAndFactorAtEqualDrawOnBothSlots() {
        val a = PressSlots()
        val b = PressSlots()
        val c = PressSlots()
        for (s in listOf(a, b, c)) {
            s.down(1, 0)
            s.down(2, 50)
        }
        for (t in listOf(60L, 100L, 150L, 400L)) {
            a.show(t)
            for (i in -1..3) {
                val d = c.draw(i, t)
                assertEquals(d, a.factor(i), 0f)
                assertEquals(d, b.factorAt(i, t), 0f)
            }
        }
    }

    @Test
    fun everyMutatorAndSlotSwapButShowBumpsTheRevision() {
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
        )
        for (m in mutators) {
            val r = p.revision
            m(p)
            assertTrue(p.revision > r)
        }
        val r = p.revision
        p.show(80)
        p.factor(1)
        p.factorAt(1, 90)
        p.draw(1, 100)
        p.stale(110)
        assertEquals(r, p.revision)
    }
}
