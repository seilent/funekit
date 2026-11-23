package net.seilent.funekit.kit.stack

import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.sheetInMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverlaySlotTest {
    private class Slots {
        val takes = mutableListOf<Boolean>()
        lateinit var stack: PageStack<String>
        var t = 0L

        init {
            stack = PageStack<String>(MoveForms(), { PageKind.Hub }, swapped = { _, _ -> takes += stack.taken }) { MoveEnds() }.also { it.pages.add(HUB) }
        }

        fun settle(o: Overlay) {
            t += sheetInMs().toLong() + STEP
            stack.frame(t)
            assertSame("premise: the overlay is up", o, stack.overlay)
            assertFalse("premise: the overlay is settled", o.closing || o.moving(t))
        }

        fun step() {
            t += STEP
            stack.frame(t)
        }
    }

    @Test
    fun anOpenHoldsTheEmptySlotFromItsCallAndRefusesASecondThere() {
        val s = Slots()
        val a = Overlay(OverKind.Sheet)
        val b = Overlay(OverKind.Sheet)
        assertTrue(s.stack.open(a))
        assertEquals("an open holds the slot from its call", true, s.stack.taken)
        assertNull(s.stack.overlay)
        assertEquals("a second open is refused at its call", false, s.stack.open(b))
        s.stack.frame(0)
        assertSame(a, s.stack.overlay)
        assertTrue(s.stack.taken)
        s.stack.close()
        s.step()
        s.t += sheetInMs().toLong() + STEP
        s.stack.frame(s.t)
        assertNull(s.stack.overlay)
        assertFalse(s.stack.taken)
    }

    @Test
    fun anOpenOverAnOverlayItsFrameCutsOrClosesTakesTheSlot() {
        val s = Slots()
        val a = Overlay(OverKind.Sheet)
        s.stack.open(a)
        s.stack.frame(0)
        s.settle(a)
        val b = Overlay(OverKind.Sheet)
        s.stack.cut()
        assertEquals("an open over an overlay its frame cuts takes the slot", true, s.stack.open(b))
        s.step()
        s.settle(b)
        val c = Overlay(OverKind.Sheet)
        s.stack.cutOverlay(b, closes = false)
        assertTrue(s.stack.open(c))
        s.step()
        s.settle(c)
        val d = Overlay(OverKind.Sheet)
        s.stack.accept()
        assertTrue(s.stack.open(d))
        s.step()
        assertSame(d, s.stack.overlay)
    }

    @Test
    fun anOpenFromAnOpensOnOpenFailsPlainly() {
        val s = Slots()
        val a = Overlay(OverKind.Sheet)
        val b = Overlay(OverKind.Sheet)
        s.stack.open(a, onOpen = { s.stack.open(b) })
        val failed = runCatching { s.stack.frame(0) }.exceptionOrNull()
        assertTrue("an open from an onOpen fails plainly: expected IllegalStateException", failed is IllegalStateException)
        assertSame(a, s.stack.overlay)
    }

    @Test
    fun aLandingInsideAnOpensAskSeesTheSlotTaken() {
        val s = Slots()
        s.stack.push(ALBUM, MoveKind.T3)
        s.stack.frame(0)
        assertEquals("premise: the push is in its exit", Phase.Exit, s.stack.phase)
        assertTrue("premise: the swap is still ahead", s.takes.isEmpty())
        val a = Overlay(OverKind.Sheet)
        s.stack.open(a)
        s.stack.frame(STEP)
        assertEquals("a landing inside an open's ask sees the slot taken", true, s.takes.single())
        assertSame(a, s.stack.overlay)
    }

    private companion object {
        const val STEP = 16L
        const val HUB = "hub"
        const val ALBUM = "album"
    }
}
