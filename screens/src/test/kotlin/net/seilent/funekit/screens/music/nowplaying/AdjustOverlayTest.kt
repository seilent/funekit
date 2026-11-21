package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.kit.touch.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustOverlayTest {
    private val pop: (Turn<String>) -> MoveKind = { MoveKind.T4 }

    private class Adj {
        val hud = AdjustHud()
        var commits = 0
        val closings = mutableListOf<Overlay>()
        val dropped = mutableListOf<String>()
        val stack = PageStack<String>(
            MoveForms(),
            { PageKind.Hub },
            closing = { o, _ -> closings += o },
            dropped = { p, _ -> dropped += p },
        ) { MoveEnds() }.also { it.pages.add("settings") }
        val o = Overlay(OverKind.Adjust, hud) { commits++ }

        init {
            hud.open(0, listOf(AdjustCol("0.0", "left db"), AdjustCol("0.0", "right db")), "balance", "center")
            stack.open(o)
            stack.frame(0)
        }
    }

    @Test
    fun theHudOpensAtOnceWithNoFocusHideAndNoCover() {
        val a = Adj()
        assertSame(a.o, a.stack.overlay)
        assertEquals(0, a.o.dur())
        assertFalse(a.stack.hideFocus)
        assertFalse(a.stack.covered)
    }

    @Test
    fun itMovesWhileTheHudOpensOrClosesAndNotBetween() {
        val a = Adj()
        assertTrue(a.o.moving(Tok.motion_volumeOpenEnd.toLong() - 1))
        assertFalse(a.o.moving(Tok.motion_volumeOpenEnd.toLong()))
        assertFalse(a.o.closing)
        a.hud.hide(1000)
        assertTrue(a.o.moving(1010))
        assertTrue(a.o.closing)
    }

    @Test
    fun theHudsCloseDrawsItsHideFramesBeforeTheOverlayLeaves() {
        val a = Adj()
        a.stack.close()
        a.stack.frame(1000)
        assertTrue(a.hud.hiding)
        assertEquals(1, a.commits)
        a.stack.frame(1000 + VolumeHud.closeMs() - 1)
        assertSame("the hide frames draw with the overlay up", a.o, a.stack.overlay)
        a.stack.frame(1000 + VolumeHud.closeMs())
        assertNull(a.stack.overlay)
    }

    @Test
    fun theOverlaysDropShowsNoPageThumb() {
        val a = Adj()
        a.stack.back(pop)
        a.stack.frame(1000)
        assertEquals("premise: the close went to the closing hook", listOf(a.o), a.closings)
        a.stack.frame(1000 + VolumeHud.closeMs())
        assertNull(a.stack.overlay)
        assertNull(a.stack.turn)
        assertEquals(listOf("settings"), a.stack.pages.toList())
        assertTrue("no page thumb at the drop", a.dropped.isEmpty())
    }

    @Test
    fun aCutDropsTheHudWithNoMotionAndRunsItsCommit() {
        val a = Adj()
        a.stack.cut()
        a.stack.frame(100)
        assertNull(a.stack.overlay)
        assertEquals(1, a.commits)
        assertFalse(a.hud.shown)
        assertTrue("no close", a.closings.isEmpty())
        assertTrue("no dropped", a.dropped.isEmpty())
        assertNull(a.stack.turn)
    }

    @Test
    fun aDownInAnEdgeBandWhileTheHudOpensHoldsNoBack() {
        val a = Adj()
        assertTrue(a.o.moving(100))
        assertFalse(a.stack.hold(100, false, Gesture.Edge.Left))
        assertFalse(a.stack.hold(100, true, Gesture.Edge.None))
    }

    @Test
    fun aTouchWhileTheHudHidesLandsNothing() {
        val a = Adj()
        a.stack.close()
        a.stack.frame(1000)
        a.stack.touch()
        a.stack.frame(1000 + VolumeHud.closeMs() - HIDE_LEFT)
        assertSame("the hide frames go on", a.o, a.stack.overlay)
        assertTrue(a.hud.hiding)
    }

    @Test
    fun aTouchWhileTheHudOpensLandsNothing() {
        val a = Adj()
        a.stack.touch()
        a.stack.frame(100)
        assertSame(a.o, a.stack.overlay)
        assertTrue(a.hud.visible(100))
        assertTrue(a.hud.opening(100))
    }

    private companion object {
        const val HIDE_LEFT = 10
    }
}
