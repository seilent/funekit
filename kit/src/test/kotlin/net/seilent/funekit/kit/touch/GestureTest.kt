package net.seilent.funekit.kit.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTest {
    private val slop = Slop(25f, 45f)

    private fun gesture() = Gesture(slop)

    @Test
    fun aReleaseWithin25uIsATapAtAnyHold() {
        assertTrue(slop.tap(100f, 100f, 125f, 100f))
        assertTrue(slop.tap(100f, 100f, 117.6f, 117.6f))
        assertFalse(slop.tap(100f, 100f, 125.1f, 100f))
        val g = gesture()
        g.arm(100f, 100f, ms = 0)
        assertFalse(g.move(120f, 110f, 900))
        assertEquals(Gesture.State.Pending, g.state)
    }

    @Test
    fun theAxisLocksAtTheSlopCrossingAndATieIsVertical() {
        assertEquals(Axis.Vertical, axisOf(20f, 20f))
        assertEquals(Axis.Vertical, axisOf(-20f, 20f))
        assertEquals(Axis.Horizontal, axisOf(20.01f, 20f))
        val g = gesture()
        g.arm(100f, 100f)
        assertTrue(g.move(120f, 120f, 16))
        assertEquals(Gesture.State.Vertical, g.state)
        assertFalse(g.move(200f, 120f, 32))
        assertEquals(Gesture.State.Vertical, g.state)
    }

    @Test
    fun aSwipeCommitsPast45uWithinTheFourteenToTenRatio() {
        assertEquals(Swipe.None, slop.swipeOf(-45f, 0f))
        assertEquals(Swipe.Left, slop.swipeOf(-45.5f, 0f))
        assertEquals(Swipe.Right, slop.swipeOf(70f, 49f))
        assertEquals(Swipe.None, slop.swipeOf(70f, 50f))
        val g = gesture()
        g.arm(200f, 100f)
        g.move(170f, 102f, 16)
        assertEquals(Gesture.State.Horizontal, g.state)
        assertEquals(Swipe.Left, g.up(140f, 104f, 48))
    }

    @Test
    fun aVerticalLockNeverSwipesWithoutATurn() {
        val g = gesture()
        g.arm(100f, 100f)
        g.move(100f, 140f, 16)
        g.move(30f, 140f, 32)
        assertEquals(Swipe.None, g.up(30f, 140f, 48))
    }

    @Test
    fun aScrollTurnsIntoASwipeFromTheAnchor() {
        val g = gesture()
        g.arm(100f, 100f, turn = true)
        g.move(100f, 140f, 16)
        assertEquals(Gesture.State.Vertical, g.state)
        g.move(100f, 180f, 32)
        assertFalse(g.move(90f, 182f, 48))
        assertTrue(g.move(70f, 183f, 64))
        assertTrue(g.turned)
        assertEquals(Gesture.State.Horizontal, g.state)
        assertEquals(100f, g.originX, 0f)
        assertEquals(Gesture.Edge.None, g.edge)
        g.keep()
        assertEquals(Swipe.Left, g.commitAt(40f, 183f))
        assertEquals(Swipe.Left, g.up(40f, 183f, 96))
    }

    @Test
    fun aMoveVerticalFromTheOriginEndsTheTurnBeforeTheTake() {
        val g = gesture()
        g.arm(100f, 100f, turn = true)
        g.move(100f, 140f, 16)
        g.move(100f, 180f, 32)
        g.move(70f, 182f, 48)
        g.move(60f, 182f, 64)
        assertTrue(g.turned)
        g.move(90f, 260f, 80)
        assertEquals(Gesture.State.Vertical, g.state)
        assertFalse(g.turned)
    }

    @Test
    fun theBottomBandSwipesUp() {
        val g = gesture()
        g.arm(100f, 310f, bottom = true)
        g.move(100f, 280f, 16)
        assertEquals(Swipe.Up, g.up(100f, 260f, 32))
    }

    @Test
    fun theEdgeIsKeptForAPlainSwipe() {
        val g = gesture()
        g.arm(4f, 100f, edge = Gesture.Edge.Left)
        g.move(40f, 100f, 16)
        assertEquals(Swipe.Right, g.up(60f, 101f, 32))
        assertEquals(Gesture.Edge.Left, g.edge)
    }

    @Test
    fun aBareLiftExtrapolatesByTheLastStep() {
        val g = gesture()
        g.arm(200f, 100f, ms = 0)
        g.move(170f, 100f, 16)
        g.move(158f, 100f, 32)
        val lift = g.liftCommit(158f, 100f, 48)
        assertEquals(Swipe.Left, lift.swipe)
        assertEquals(146f, lift.x, 0.0001f)
        assertEquals(Gesture.State.Horizontal, g.state)
        assertEquals(Swipe.None, gesture().also {
            it.arm(200f, 100f, ms = 0)
            it.move(170f, 100f, 16)
            it.move(158f, 100f, 32)
        }.up(158f, 100f, 48))
        assertEquals(Swipe.Left, g.up(158f, 100f, 48, bare = true))
    }

    @Test
    fun aBareLiftNeverExtrapolatesInsideTheSlopOrPastOneStep() {
        val g = gesture()
        g.arm(200f, 100f, ms = 0)
        g.move(190f, 100f, 16)
        assertEquals(Swipe.None, g.up(190f, 100f, 32, bare = true))
        val late = gesture()
        late.arm(200f, 100f, ms = 0)
        late.move(170f, 100f, 16)
        late.move(158f, 100f, 32)
        assertEquals(Swipe.None, late.up(158f, 100f, 49, bare = true))
    }

    @Test
    fun aVerticalArmCommitsUpAlwaysAndDownOnlyWhenAsked() {
        val down = gesture()
        down.armVertical(100f, 100f, down = true)
        down.move(100f, 160f, 16)
        assertEquals(Swipe.Down, down.up(100f, 160f, 32))
        val upOnly = gesture()
        upOnly.armVertical(100f, 100f, down = false)
        upOnly.move(100f, 160f, 16)
        assertEquals(Swipe.None, upOnly.up(100f, 160f, 32))
        upOnly.armVertical(100f, 100f, down = false)
        upOnly.move(100f, 40f, 16)
        assertEquals(Swipe.Up, upOnly.up(100f, 40f, 32))
        val sideways = gesture()
        sideways.armVertical(100f, 100f, down = true)
        sideways.move(160f, 100f, 16)
        assertEquals(Swipe.None, sideways.up(160f, 100f, 32))
    }
}
