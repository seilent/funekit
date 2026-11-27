package net.seilent.funekit.kit.stack

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.sheetInMs
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.touch.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EdgeBackTest {
    private val pop: (Turn<String>) -> MoveKind = { MoveKind.T4 }

    private fun stack(vararg pages: String): PageStack<String> = PageStack<String>(MoveForms(), { PageKind.Hub }) { MoveEnds() }.also { it.pages.addAll(pages) }

    private fun settled(s: PageStack<String>, o: Overlay): Long {
        s.open(o)
        s.frame(0)
        val t = sheetInMs().toLong() + STEP
        s.frame(t)
        assertFalse("premise: the sheet is settled", o.closing || o.moving(t))
        return t
    }

    @Test
    fun anEdgeBackOverAMovingOverlayWithNoBackDoesNothing() {
        val s = stack(HUB)
        val o = Overlay(OverKind.Sheet)
        s.open(o)
        s.frame(0)
        assertTrue("premise: the sheet rises", o.moving(STEP))
        s.edgeBack(pop)
        s.frame(STEP)
        assertEquals("an edge back over a moving overlay does nothing", false, o.closing)
        assertSame(o, s.overlay)
        val t = sheetInMs().toLong() + STEP
        s.frame(t)
        s.edgeBack(pop)
        s.frame(t + STEP)
        assertTrue("an edge back over the settled sheet closes it", o.closing)
    }

    @Test
    fun anEdgeBackOverThePlateDoesNothing() {
        val s = stack(HUB)
        val hud = VolumeHud()
        val o = Overlay(OverKind.Volume, hud)
        hud.show(0, LEVEL, true, ARTIST, TITLE)
        s.open(o)
        s.frame(0)
        val t = Tok.motion_volumeOpenEnd.toLong() + STEP
        assertFalse("premise: the plate is open", o.moving(t))
        s.edgeBack(pop)
        s.frame(t)
        assertEquals("an edge back over the plate does nothing", false, hud.closing(t))
        assertSame(o, s.overlay)
    }

    @Test
    fun aHeldBackLiftedAfterTheRiseAsksTheOverlaysBack() {
        val s = stack(HUB, ALBUM)
        var asked = 0
        val o = Overlay(OverKind.Sheet).also {
            it.back = {
                asked++
                true
            }
        }
        s.open(o)
        s.frame(0)
        assertTrue("premise: a band down inside the rise is held", s.hold(STEP, false, Gesture.Edge.Left))
        val t = sheetInMs().toLong() + STEP
        s.frame(t)
        assertFalse("premise: the rise ended", o.moving(t))
        s.heldBack(pop)
        s.frame(t + STEP)
        assertEquals("a held back after the rise asks the overlay's back", 1, asked)
        assertSame(o, s.overlay)
        assertFalse(o.closing)
        s.close()
        s.frame(t + 2 * STEP)
        assertTrue("premise: the sheet closes", o.closing)
        s.heldBack(pop)
        s.frame(t + 3 * STEP)
        assertEquals("a held back during the close moves no page", Phase.Rest, s.phase)
        assertEquals(listOf(HUB, ALBUM), s.pages.toList())
    }

    @Test
    fun aBandDownDuringAChoiceSwapHoldsNothing() {
        val s = stack(HUB)
        val o = Overlay(OverKind.Sheet)
        val t = settled(s, o)
        val swap = t + STEP
        s.swapMenu(o) {}
        s.frame(swap)
        assertTrue("premise: the swap moves the sheet", o.moving(swap + STEP))
        assertEquals("a band down during a choice swap holds nothing", false, s.hold(swap + STEP, false, Gesture.Edge.Left))
        s.edgeBack(pop)
        s.frame(swap + 2 * STEP)
        assertFalse("an edge back during the swap does nothing", o.closing)
        assertSame(o, s.overlay)
    }

    @Test
    fun everyBackTheStackIsAskedCountsOnce() {
        val gate = InputGate()
        val s = PageStack<String>(MoveForms(), { PageKind.Hub }, gate = gate) { MoveEnds() }.also { it.pages.addAll(listOf(HUB, ALBUM)) }
        assertEquals(0, s.backs)
        s.back(pop)
        assertEquals("a back", 1, s.backs)
        s.back(true, pop)
        assertEquals("a device back", 2, s.backs)
        s.edgeBack(pop)
        assertEquals("an edge back", 3, s.backs)
        s.backed()
        assertEquals("a back another handler took", 4, s.backs)
        gate.host!!.back()
        assertEquals("the gate's back key", 5, s.backs)
        s.frame(0)
        assertEquals("a frame counts nothing", 5, s.backs)
    }

    private companion object {
        const val STEP = 16L
        const val HUB = "hub"
        const val ALBUM = "album"
        const val LEVEL = 50
        const val ARTIST = "artist"
        const val TITLE = "title"
    }
}
