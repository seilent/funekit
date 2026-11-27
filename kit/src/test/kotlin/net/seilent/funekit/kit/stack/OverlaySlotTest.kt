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

    @Test
    fun anOpenAfterAnAskThatEndsTheTopHoldsTheSlotFromItsCall() {
        val s = Slots()
        var top = Overlay(OverKind.Sheet)
        s.stack.open(top)
        s.stack.frame(0)
        s.settle(top)
        val asks = listOf<Pair<String, (Overlay) -> Unit>>(
            "accept" to { _ -> s.stack.accept() },
            "close" to { _ -> s.stack.close() },
            "home" to { _ -> s.stack.home(MoveKind.T4n) },
            "reset" to { _ -> s.stack.reset(listOf(HUB)) },
            "cutOverlay" to { o -> s.stack.cutOverlay(o) },
        )
        for ((name, ask) in asks) {
            val b = Overlay(OverKind.Sheet)
            ask(top)
            s.stack.open(b)
            assertTrue("an open after $name holds the slot", s.stack.holds(b))
            s.step()
            assertSame("the open after $name is up", b, s.stack.overlay)
            assertFalse("the open after $name is no longer held", s.stack.holds(b))
            s.settle(b)
            top = b
        }
        s.stack.close()
        s.step()
        assertTrue("premise: the top is closing", top.closing && s.stack.overlay === top)
        val b = Overlay(OverKind.Sheet)
        s.stack.open(b)
        assertTrue("an open over a closing top holds the slot", s.stack.holds(b))
        s.step()
        assertSame(b, s.stack.overlay)
        assertFalse(s.stack.holds(b))
    }

    @Test
    fun aSecondOpenInTheTurnHoldsNothing() {
        val s = Slots()
        val a = Overlay(OverKind.Sheet)
        s.stack.open(a)
        s.stack.frame(0)
        s.settle(a)
        val b = Overlay(OverKind.Sheet)
        val c = Overlay(OverKind.Sheet)
        s.stack.accept()
        s.stack.open(b)
        s.stack.open(c)
        assertTrue("the first open after the accept holds the slot", s.stack.holds(b))
        assertFalse("a second open in the turn holds nothing", s.stack.holds(c))
        s.step()
        assertSame(b, s.stack.overlay)
        s.settle(b)
        val e = Overlay(OverKind.Sheet)
        s.stack.open(e)
        assertFalse("an open over a live top with nothing asked holds nothing", s.stack.holds(e))
        s.step()
        assertSame("the live top stays", b, s.stack.overlay)
    }

    @Test
    fun aSecondOpenOverAClosingTopHoldsNothing() {
        val s = Slots()
        val a = Overlay(OverKind.Sheet)
        s.stack.open(a)
        s.stack.frame(0)
        s.settle(a)
        s.stack.close()
        s.step()
        assertTrue("premise: the top is closing", a.closing && s.stack.overlay === a)
        val b = Overlay(OverKind.Sheet)
        val c = Overlay(OverKind.Sheet)
        s.stack.open(b)
        s.stack.open(c)
        assertTrue("the first open over the closing top holds the slot", s.stack.holds(b))
        assertFalse("a second open over it holds nothing", s.stack.holds(c))
        s.step()
        assertSame(b, s.stack.overlay)
    }

    @Test
    fun aNoteDiesWithItsFrame() {
        val s = Slots()
        val d = Overlay(OverKind.Sheet)
        s.stack.open(d)
        s.stack.frame(0)
        s.settle(d)
        s.stack.close()
        s.stack.swapMenu {}
        s.step()
        assertSame("premise: the swap reopens the closing sheet", d, s.stack.overlay)
        assertFalse("premise: the sheet is live", d.closing)
        val c = Overlay(OverKind.Sheet)
        s.stack.open(c)
        assertFalse("an open a turn after the close holds nothing", s.stack.holds(c))
        s.step()
        assertSame("the live sheet stays", d, s.stack.overlay)
    }

    private companion object {
        const val STEP = 16L
        const val HUB = "hub"
        const val ALBUM = "album"
    }
}
