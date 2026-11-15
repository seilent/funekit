package net.seilent.funekit.kit.stack

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InputGateTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val gate = InputGate()
    private val stack = PageStack<String>(MoveForms(), { PageKind.Hub }, gate = gate) { MoveEnds() }.also { it.pages.add("hub") }
    private val pop: (Turn<String>) -> MoveKind = { MoveKind.T4 }
    private val t0 = SystemClock.uptimeMillis()
    private val seen = mutableListOf<Triple<String, Int, Long>>()
    private val speeds = mutableListOf<Float>()
    private var longs = 0

    private fun content(body: @Composable () -> Unit) {
        rule.setContent(body)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun Modifier.target(name: (Float) -> String = { PAGE }, onUp: (String) -> Unit = {}) = pointerInput(Unit) {
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false)
            val n = name(d.position.y)
            seen += Triple(n, DOWN, d.uptimeMillis)
            while (true) {
                val c = awaitPointerEvent().changes.first()
                if (!c.pressed) {
                    seen += Triple(n, UP, c.uptimeMillis)
                    onUp(n)
                    break
                }
            }
        }
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun frame(ms: Long) = stack.frame(t0 + ms)

    private fun ev(action: Int, ms: Long, down: Long, y: Float = Y): MotionEvent =
        MotionEvent.obtain(t0 + down, t0 + ms, action, X, y, 0).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }

    private fun send(e: MotionEvent) {
        gate.touch(e) { rule.activity.dispatchTouchEvent(it) }
        e.recycle()
    }

    private fun tap(down: Long, up: Long, y: Float = Y) {
        send(ev(MotionEvent.ACTION_DOWN, down, down, y))
        send(ev(MotionEvent.ACTION_UP, up, down, y))
    }

    private fun names() = seen.map { it.first to it.second }

    @Test
    fun whileArmedATapReachesNoHandlerAndAfterTheReleaseItsDownAndUpArriveOnceInOrderWithTheirSpacing() {
        content { Box(Modifier.fillMaxSize().target()) }
        val h = stack.pushOpen("np", t0, BOUND)
        frame(0)
        tap(20, 70)
        assertTrue(seen.isEmpty())
        h.done()
        frame(100)
        assertTrue(seen.isEmpty())
        settle()
        frame(116)
        assertEquals(listOf(PAGE to DOWN, PAGE to UP), names())
        assertEquals(50L, seen[1].third - seen[0].third)
        assertTrue("no event older than the frame it lands in", seen[0].third >= t0 + 116)
    }

    @Test
    fun liveInputBetweenTheReleaseAndTheReplayArrivesAfterTheHeldDownInOrderOnce() {
        content {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false)
                        seen += Triple(PAGE, DOWN, d.uptimeMillis)
                        while (true) {
                            val c = awaitPointerEvent().changes.first()
                            if (!c.pressed) {
                                seen += Triple(PAGE, UP, c.uptimeMillis)
                                break
                            }
                            if (c.position != c.previousPosition) seen += Triple(PAGE, MOVE, c.uptimeMillis)
                        }
                    }
                },
            )
        }
        val h = stack.pushOpen("np", t0, BOUND)
        frame(0)
        send(ev(MotionEvent.ACTION_DOWN, 20, 20))
        h.done()
        frame(100)
        send(ev(MotionEvent.ACTION_MOVE, 105, 20, Y + MOVE_D))
        send(ev(MotionEvent.ACTION_UP, 110, 20, Y + MOVE_D))
        assertTrue(seen.isEmpty())
        settle()
        frame(116)
        assertEquals(listOf(PAGE to DOWN, PAGE to MOVE, PAGE to UP), names())
    }

    @Test
    fun aLiveTapAfterAReplayStopsAtATurnAskArrivesAfterTheHeldRest() {
        content { Box(Modifier.fillMaxSize().target({ y -> if (y < SPLIT) HEADER else ROW }, onUp = { if (it == HEADER) stack.back(pop) })) }
        val h = stack.pushOpen("np", t0, BOUND)
        frame(0)
        tap(20, 30, HEADER_Y)
        tap(40, 50, Y)
        h.done()
        frame(100)
        settle()
        frame(116)
        assertEquals(listOf(HEADER to DOWN, HEADER to UP), names())
        tap(120, 125, Y + MOVE_D)
        assertEquals(2, seen.size)
        settle()
        frame(132)
        assertEquals(listOf(HEADER to DOWN, HEADER to UP, ROW to DOWN, ROW to UP, ROW to DOWN, ROW to UP), names())
        assertTrue(seen[2].third >= t0 + 132)
        assertTrue(seen[4].third >= seen[3].third)
    }

    @Test
    fun aRefusalAt400WithAHeldTapLandsTheT8AndTheTapActsOnThePageUnderAtTheNextFrameOnce() {
        content {
            if (stack.phase == Phase.Rest) {
                stack.top?.let { p -> key(p) { Box(Modifier.fillMaxSize().target({ p })) } }
            }
        }
        val h = stack.pushOpen("np", t0, BOUND)
        frame(0)
        settle()
        frame(400)
        tap(380, 390)
        h.fail()
        frame(416)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals("hub", stack.top)
        assertTrue(seen.isEmpty())
        settle()
        frame(432)
        assertEquals(listOf("hub" to DOWN, "hub" to UP), names())
        settle()
        frame(448)
        assertEquals(2, seen.size)
    }

    @Test
    fun aDownHeldThroughTheWaitFiresItsLongPressAHoldTimeAfterTheRelease() {
        content {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = withTimeoutOrNull(Tok.input_keyHoldMs.toLong()) {
                            while (awaitPointerEvent().changes.first().pressed) {}
                        }
                        if (up == null) longs++
                    }
                },
            )
        }
        val h = stack.pushOpen("np", t0, BOUND)
        frame(0)
        send(ev(MotionEvent.ACTION_DOWN, 10, 10))
        rule.mainClock.advanceTimeBy(WAIT, ignoreFrameDuration = true)
        assertEquals(0, longs)
        h.done()
        frame(310)
        settle()
        frame(326)
        rule.mainClock.advanceTimeBy(Tok.input_keyHoldMs.toLong() - 1, ignoreFrameDuration = true)
        assertEquals(0, longs)
        rule.mainClock.advanceTimeBy(STEP + 1, ignoreFrameDuration = true)
        assertEquals(1, longs)
    }

    @Test
    fun aHeldDragReleasedAsAFlingReplaysAtTheSpeedItWasSent() {
        content {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false)
                        val v = VelocityTracker()
                        v.addPointerInputChange(d)
                        while (true) {
                            val c = awaitPointerEvent().changes.first()
                            v.addPointerInputChange(c)
                            if (!c.pressed) {
                                speeds += v.calculateVelocity().y
                                break
                            }
                        }
                    }
                },
            )
        }
        drag(0)
        assertEquals(1, speeds.size)
        val h = stack.pushOpen("np", t0 + 100, BOUND)
        frame(100)
        drag(120)
        assertEquals(1, speeds.size)
        h.done()
        frame(200)
        settle()
        frame(216)
        assertEquals(2, speeds.size)
        assertTrue(speeds[0] != 0f)
        assertEquals(speeds[0], speeds[1], 1f)
    }

    private fun drag(at: Long) {
        send(ev(MotionEvent.ACTION_DOWN, at, at, DRAG_Y0))
        val m = ev(MotionEvent.ACTION_MOVE, at + DRAG_STEP, at, DRAG_Y0 - DRAG_D1)
        m.addBatch(t0 + at + 2 * DRAG_STEP, X, DRAG_Y0 - DRAG_D2, 1f, 1f, 0)
        m.addBatch(t0 + at + 3 * DRAG_STEP, X, DRAG_Y0 - DRAG_D3, 1f, 1f, 0)
        m.addBatch(t0 + at + 4 * DRAG_STEP, X, DRAG_Y0 - DRAG_D4, 1f, 1f, 0)
        send(m)
        send(ev(MotionEvent.ACTION_UP, at + 4 * DRAG_STEP + DRAG_UP, at, DRAG_Y0 - DRAG_D4 - DRAG_D1))
    }

    @Test
    fun twoHeldTapsWhoseFirstIsASongPickReplayOneAndHoldTheSecondAgain() {
        val picks = mutableListOf<PlayOpen>()
        content { Box(Modifier.fillMaxSize().target(onUp = { picks += stack.pushOpen("np2", stack.clock, BOUND) })) }
        val h = stack.pushOpen("np", t0, BOUND)
        frame(0)
        tap(20, 30)
        tap(40, 50)
        h.done()
        frame(100)
        settle()
        frame(116)
        assertEquals(listOf(PAGE to DOWN, PAGE to UP), names())
        assertEquals(1, picks.size)
        assertTrue(gate.holding)
        assertTrue(gate.queued)
        assertEquals(PlayOpen.State.Armed, picks[0].state)
        settle()
        frame(132)
        assertEquals(2, seen.size)
    }

    @Test
    fun aHeldHeaderTapThenARowTapReachesTheRowOnlyAtTheFrameAfterThePopStarts() {
        content { Box(Modifier.fillMaxSize().target({ y -> if (y < SPLIT) HEADER else ROW }, onUp = { if (it == HEADER) stack.back(pop) })) }
        val h = stack.pushOpen("np", t0, BOUND)
        frame(0)
        tap(20, 30, HEADER_Y)
        tap(40, 50, Y)
        h.done()
        frame(100)
        settle()
        frame(116)
        assertEquals(listOf(HEADER to DOWN, HEADER to UP), names())
        assertEquals(MoveKind.T4, stack.turn?.kind)
        assertEquals("np", stack.turn?.from)
        settle()
        frame(132)
        assertEquals(listOf(HEADER to DOWN, HEADER to UP, ROW to DOWN, ROW to UP), names())
    }

    @Test
    fun aSideEffectReadingThePlayOpenStateSeesArmedAndReleased() {
        var handle by mutableStateOf<PlayOpen?>(null)
        val states = mutableListOf<PlayOpen.State?>()
        content {
            val st = handle?.state
            SideEffect { states += st }
        }
        handle = stack.pushOpen("np", t0, BOUND)
        frame(0)
        settle()
        assertTrue(states.contains(PlayOpen.State.Armed))
        requireNotNull(handle).done()
        settle()
        assertTrue(states.contains(PlayOpen.State.Released))
    }

    private companion object {
        const val BOUND = 500L
        const val WAIT = 300L
        const val STEP = 16L
        const val X = 100f
        const val Y = 300f
        const val HEADER_Y = 30f
        const val SPLIT = 100f
        const val DRAG_Y0 = 400f
        const val DRAG_STEP = 10L
        const val DRAG_UP = 8L
        const val DRAG_D1 = 10f
        const val DRAG_D2 = 30f
        const val DRAG_D3 = 70f
        const val DRAG_D4 = 150f
        const val DOWN = 0
        const val UP = 1
        const val MOVE = 2
        const val MOVE_D = 40f
        const val PAGE = "page"
        const val HEADER = "header"
        const val ROW = "row"
    }
}
