package net.seilent.funekit.kit.stack

import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.toastOver
import net.seilent.funekit.kit.touch.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class Vol {
    val hud = VolumeHud()
    var commits = 0
    val closings = mutableListOf<Overlay>()
    val dropped = mutableListOf<String>()
    val stack = PageStack<String>(
        MoveForms(),
        { PageKind.Hub },
        closing = { o, _ -> closings += o },
        dropped = { p, _ -> dropped += p },
    ) { MoveEnds() }.also { it.pages.add("hub") }
    val o = Overlay(OverKind.Volume, hud) { commits++ }

    init {
        hud.show(0, 50, true, "artist", "title")
        stack.open(o)
        stack.frame(0)
    }
}

class VolumeOverlayTest {
    private val gone = 282L + 2027L + 650L + 267L
    private val pop: (Turn<String>) -> MoveKind = { MoveKind.T4 }

    @Test
    fun theHudOpensWithNoFocusHideAndNoCover() {
        val v = Vol()
        assertSame(v.o, v.stack.overlay)
        assertFalse(v.stack.hideFocus)
        assertFalse(v.stack.covered)
        assertEquals(1, v.stack.snap)
        assertFalse(v.stack.jumpSettled)
        assertFalse(toastOver(OverKind.Volume))
    }

    @Test
    fun itMovesWhileTheHudOpensOrClosesAndNotBetween() {
        val v = Vol()
        assertTrue(v.o.moving(100))
        assertFalse(v.o.moving(500))
        assertFalse(v.o.closing)
        v.hud.hide(1000)
        assertTrue(v.o.moving(1010))
        assertTrue(v.o.closing)
    }

    @Test
    fun itIsDroppedOnTheFrameTheHudIsGone() {
        val v = Vol()
        v.stack.frame(gone - 1)
        assertNotNull(v.stack.overlay)
        v.stack.frame(gone)
        assertNull(v.stack.overlay)
        assertTrue("no page thumb at the drop", v.dropped.isEmpty())
    }

    @Test
    fun backClosesTheHudWithTheSeekCommittedAndPopsNothing() {
        val v = Vol()
        v.stack.back(pop)
        v.stack.frame(1000)
        assertTrue(v.hud.hiding)
        assertEquals(1, v.commits)
        assertNull(v.stack.turn)
        assertEquals(listOf("hub"), v.stack.pages.toList())
        assertNotNull(v.stack.overlay)
        v.stack.frame(1000 + VolumeHud.closeMs())
        assertNull(v.stack.overlay)
        assertTrue("no page thumb at the drop", v.dropped.isEmpty())
    }

    @Test
    fun aBackWhileTheHudClosesDoesNothing() {
        val v = Vol()
        v.stack.back(pop)
        v.stack.frame(1000)
        v.stack.back(pop)
        v.stack.frame(1020)
        assertEquals(1, v.commits)
        assertNull(v.stack.turn)
        assertEquals(listOf("hub"), v.stack.pages.toList())
    }

    @Test
    fun aBackInTheFastFadeHidesWithTheSeekCommitted() {
        val f = Vol()
        f.stack.frame(282L + 2027L + 700L)
        f.stack.back(pop)
        f.stack.frame(282L + 2027L + 716L)
        assertEquals(1, f.commits)
        assertTrue(f.hud.hiding)
        assertNull(f.stack.turn)
        assertEquals(listOf("hub"), f.stack.pages.toList())
        f.stack.frame(282L + 2027L + 716L + VolumeHud.closeMs())
        assertNull(f.stack.overlay)
    }

    @Test
    fun homeDropsTheHudWithTheSeekCommitted() {
        val v = Vol()
        v.stack.home(MoveKind.T4n)
        v.stack.frame(500)
        assertNull(v.stack.overlay)
        assertEquals(1, v.commits)
        assertFalse(v.hud.shown)
        assertTrue("no page thumb at the drop", v.dropped.isEmpty())
    }

    @Test
    fun aDownInAnEdgeBandWhileTheHudOpensHoldsNoBack() {
        val v = Vol()
        assertTrue(v.o.moving(100))
        assertFalse(v.stack.hold(100, false, Gesture.Edge.Left))
        assertFalse(v.stack.hold(100, true, Gesture.Edge.None))
    }

    @Test
    fun aTouchWhileTheHudOpensLandsNothing() {
        val v = Vol()
        v.stack.touch()
        v.stack.frame(100)
        assertSame(v.o, v.stack.overlay)
        assertTrue(v.hud.visible(100))
        assertTrue(v.hud.opening(100))
    }

    @Test
    fun anotherOverlayAndAPushAreRefusedWhileTheHudIsUp() {
        val v = Vol()
        v.stack.open(Overlay(OverKind.Sheet))
        v.stack.frame(500)
        assertSame(v.o, v.stack.overlay)
        v.stack.push("album", MoveKind.T3)
        v.stack.frame(516)
        assertNull(v.stack.turn)
        assertEquals(listOf("hub"), v.stack.pages.toList())
    }

    @Test
    fun aCutDropsTheHudWithNoMotionAndCommitsTheSeek() {
        val v = Vol()
        v.stack.cut()
        v.stack.frame(100)
        assertNull(v.stack.overlay)
        assertEquals(1, v.commits)
        assertFalse(v.hud.shown)
        assertTrue("no close", v.closings.isEmpty())
        assertTrue("no dropped", v.dropped.isEmpty())
        assertNull(v.stack.turn)
    }
}
