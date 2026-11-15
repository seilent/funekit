package net.seilent.funekit.kit.stack

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.kit.touch.Slop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeldTouchTest {
    @get:Rule
    val rule = createComposeRule()

    private val slop = Slop(25f, 45f)
    private val pop: (Turn<String>) -> MoveKind = { MoveKind.T4 }
    private val ups = mutableListOf<Boolean>()
    private val stack = PageStack<String>(MoveForms(), { PageKind.Grid }) { MoveEnds() }.also { it.pages.add("hub") }

    private fun content(push: Boolean = true, target: (Offset) -> Boolean) {
        rule.setContent {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val band = if (d.position.x < BAND) Gesture.Edge.Left else Gesture.Edge.None
                        if (stack.moving) watchHeld(stack, d, slop, target(d.position), band, pop)
                    }
                },
            ) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val c = awaitPointerEvent().changes.first()
                                if (!c.pressed) {
                                    ups += c.isConsumed
                                    break
                                }
                            }
                        }
                    },
                )
            }
        }
        if (push) stack.push("album", MoveKind.T3)
        stack.frame(rule.mainClock.currentTime)
    }

    private fun popping(device: Boolean) {
        content(push = false) { it.y < HEADER }
        stack.pages.add("album")
        stack.back(device, pop)
        stack.frame(rule.mainClock.currentTime)
        assertEquals(Phase.Exit, stack.phase)
        assertEquals(true, stack.turn?.popping)
    }

    private fun heldOut() {
        rule.onRoot().performTouchInput {
            down(Offset(120f, 30f))
            moveTo(Offset(120f, 90f))
            up()
        }
        rule.waitForIdle()
        frame(2000)
    }

    private fun heldTap() {
        rule.onRoot().performTouchInput {
            down(Offset(120f, 30f))
            up()
        }
        rule.waitForIdle()
        frame(100)
        frame(2000)
        frame(4000)
    }

    @Test
    fun aHeldTargetLeftDuringADevicePopLandsItAndDropsTheRestOfTheGesture() {
        popping(device = true)
        heldOut()
        assertEquals(listOf("hub"), stack.pages.toList())
        assertEquals(listOf(true), ups)
    }

    @Test
    fun aHeldTargetLeftDuringATouchPopLandsItAndLetsTheUpThrough() {
        popping(device = false)
        heldOut()
        assertEquals(listOf("hub"), stack.pages.toList())
        assertEquals(listOf(false), ups)
    }

    @Test
    fun aHeldTargetTapDuringATouchPopBacksOnceMore() {
        popping(device = false)
        heldTap()
        assertEquals(emptyList<String>(), stack.pages.toList())
        assertEquals(listOf(true), ups)
    }

    @Test
    fun aHeldTargetTapDuringADevicePopOnlyLandsIt() {
        popping(device = true)
        heldTap()
        assertEquals(listOf("hub"), stack.pages.toList())
        assertEquals(listOf(true), ups)
    }

    private fun frame(ms: Long) = stack.frame(stack.move.t0 + ms)

    @Test
    fun aSwipeInFromTheLeftBandDuringThePushReversesIt() {
        content { false }
        rule.onRoot().performTouchInput {
            down(Offset(5f, 100f))
            moveTo(Offset(35f, 100f))
            moveTo(Offset(70f, 110f))
            up()
        }
        rule.waitForIdle()
        frame(100)
        assertTrue(stack.move.reversed)
        assertEquals(listOf("hub"), stack.pages.toList())
        assertEquals(listOf(true), ups)
    }

    @Test
    fun aVerticalLeaveFromTheBandLandsThePush() {
        content { false }
        rule.onRoot().performTouchInput {
            down(Offset(5f, 100f))
            moveTo(Offset(5f, 140f))
        }
        rule.waitForIdle()
        frame(100)
        assertEquals(Phase.Rest, stack.phase)
        assertFalse(stack.move.reversed)
        assertEquals(listOf("hub", "album"), stack.pages.toList())
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(false), ups)
    }

    @Test
    fun aHeldTargetKeepsTheSwapAndLandsAtTheKeyHold() {
        content { it.y < HEADER }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(120f, 30f)) }
        rule.mainClock.advanceTimeBy(16)
        frame(200)
        assertEquals(Phase.Exit, stack.phase)
        assertEquals("hub", stack.top)
        rule.mainClock.advanceTimeBy(400, ignoreFrameDuration = true)
        frame(450)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals("album", stack.top)
        assertNull(stack.held)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(16)
        frame(500)
        assertEquals(Phase.Exit, stack.phase)
        assertEquals(true, stack.turn?.popping)
        assertEquals("album", stack.turn?.from)
        assertEquals(listOf(true), ups)
        frame(2000)
        assertEquals(listOf("hub"), stack.pages.toList())
    }

    @Test
    fun aHeldTargetLeftPastTheKeyHoldBacksNothing() {
        content { it.y < HEADER }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(120f, 30f)) }
        rule.mainClock.advanceTimeBy(16)
        rule.mainClock.advanceTimeBy(400, ignoreFrameDuration = true)
        frame(450)
        assertEquals(Phase.Rest, stack.phase)
        rule.onRoot().performTouchInput {
            moveTo(Offset(120f, 90f))
            up()
        }
        rule.mainClock.advanceTimeBy(16)
        frame(500)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals(listOf("hub", "album"), stack.pages.toList())
        assertEquals(listOf(false), ups)
    }

    @Test
    fun aBandHoldSwipedInPastTheKeyHoldLandsThePushAndPopsIt() {
        content { false }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(5f, 100f)) }
        rule.mainClock.advanceTimeBy(16)
        rule.mainClock.advanceTimeBy(400, ignoreFrameDuration = true)
        frame(450)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals(listOf("hub", "album"), stack.pages.toList())
        rule.onRoot().performTouchInput {
            moveTo(Offset(35f, 100f))
            moveTo(Offset(70f, 110f))
            up()
        }
        rule.mainClock.advanceTimeBy(16)
        frame(500)
        assertEquals(Phase.Exit, stack.phase)
        assertEquals(true, stack.turn?.popping)
        assertEquals("album", stack.turn?.from)
        assertEquals(listOf(true), ups)
        frame(2000)
        assertEquals(listOf("hub"), stack.pages.toList())
    }

    @Test
    fun aBandHoldLeftUpwardPastTheKeyHoldBacksNothing() {
        content { false }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(5f, 100f)) }
        rule.mainClock.advanceTimeBy(16)
        rule.mainClock.advanceTimeBy(400, ignoreFrameDuration = true)
        frame(450)
        rule.onRoot().performTouchInput {
            moveTo(Offset(5f, 160f))
            moveTo(Offset(70f, 170f))
            up()
        }
        rule.mainClock.advanceTimeBy(16)
        frame(500)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals(listOf("hub", "album"), stack.pages.toList())
        assertEquals(listOf(false), ups)
    }

    @Test
    fun aCancelledHoldLandsThePushAndBacksNothing() {
        content { it.y < HEADER }
        rule.onRoot().performTouchInput {
            down(Offset(120f, 30f))
            cancel()
        }
        rule.waitForIdle()
        frame(100)
        assertFalse(stack.move.reversed)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals(listOf("hub", "album"), stack.pages.toList())
    }

    @Test
    fun aTapOnTheHeldTargetReversesThePush() {
        content { it.y < HEADER }
        rule.onRoot().performTouchInput {
            down(Offset(120f, 30f))
            up()
        }
        rule.waitForIdle()
        frame(100)
        assertTrue(stack.move.reversed)
    }

    companion object {
        private const val BAND = 16f
        private const val HEADER = 60f
    }
}
