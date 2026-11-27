package net.seilent.funekit.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.core.Section
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverlayHitTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val picks = mutableListOf<Int>()
    private var exits = 0
    private var searches = 0
    private var under = 0
    private var toastTaps = 0

    private fun now() = rule.mainClock.currentTime

    private lateinit var overlay: Overlay
    private lateinit var arb: Arbiter

    private fun jump(section: Int, openAt: Long = -10_000L): JumpGrid {
        val g = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, section, 320f) }
        rule.setContent {
            val a = remember { Arbiter(v1) }
            val o = remember { Overlay(OverKind.Jump).also { it.open(openAt) } }
            overlay = o
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap({}, {}, { under++ })))
                JumpGridOverlay(g, o, v1, a, ::now, onPick = { picks += it }, onExit = { exits++ }, onSearch = { searches++ }, layer = 1)
            }
        }
        rule.waitForIdle()
        return g
    }

    private fun tap(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun letterExitAndSearchTapsReachTheirCallbacks() {
        jump(0)
        tap(92f, 45f)
        assertEquals(listOf(0), picks)
        tap(35f, 45f)
        assertEquals(1, exits)
        assertEquals(0, under)
    }

    @Test
    fun theSearchTileTakesItsTapOnceScrolledIntoView() {
        val g = jump(27)
        assertEquals(19.54f + 7 * 56.03f + 51.79f - (320f - 16f), g.offset, 1f)
        val y = 19.54f + 7 * 56.03f + 51.79f / 2 - g.offset
        tap(35f, y)
        assertEquals(1, searches)
    }

    @Test
    fun aDownPressesTheLetterAndItsReleaseFinishesWhileTheGridStaysOpen() {
        val g = jump(0)
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(92f, 45f)) }
        rule.mainClock.advanceTimeBy(50, ignoreFrameDuration = true)
        val held = g.press.scale(0, now())
        assertTrue("held $held", held > 0.96f && held < 1f)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(1f, g.press.scale(0, now()), 0f)
        assertFalse(g.press.stale(now()))
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(listOf(0), picks)
    }

    private fun swipe(dy: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(120f, 150f))
            moveBy(Offset(0f, dy))
            moveBy(Offset(0f, dy))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun aSwipeDownAtTheTopClosesTheJumpGridWithNoPick() {
        jump(0)
        swipe(60f)
        assertEquals(1, exits)
        assertEquals(emptyList<Int>(), picks)
        assertEquals(0, searches)
    }

    @Test
    fun aScrolledJumpGridStaysOpenUnderASwipeDown() {
        val g = jump(27)
        assertTrue("premise: scrolled ${g.offset}", g.offset > 1f)
        swipe(60f)
        assertEquals(0, exits)
        assertEquals(emptyList<Int>(), picks)
    }

    @Test
    fun aSwipeDownWhileTheGridOpensDoesNothing() {
        rule.mainClock.autoAdvance = false
        jump(0, openAt = 0L)
        assertTrue("premise: the overlay moves at the down", overlay.moving(now()))
        swipe(60f)
        assertEquals(0, exits)
    }

    @Test
    fun aSwipeUpLeavesTheGridOpen() {
        jump(0)
        swipe(-30f)
        assertEquals(0, exits)
    }

    private fun toast(place: ToastPlace, lag: Long = 0L): Toast {
        val t = Toast()
        rule.setContent {
            val a = remember { Arbiter(v1) }
            arb = a
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap({}, {}, { under++ })))
                ToastStrip(t, v1, { now() + lag }, { place }, 320, a = a, onTap = { toastTaps++ })
            }
        }
        rule.mainClock.autoAdvance = false
        t.show(now(), "artist", "title", track = true)
        Snapshot.sendApplyNotifications()
        return t
    }

    private fun tapNow(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        rule.mainClock.advanceTimeBy(16)
    }

    @Test
    fun aTapInsideARisenBottomToastReachesIt() {
        toast(ToastPlace.Bottom)
        rule.mainClock.advanceTimeBy(150)
        tapNow(120f, 300f)
        assertEquals(1, toastTaps)
        assertEquals(0, under)
    }

    @Test
    fun aCutTouchOnTheToastSnapsItsPressAndTapsNothing() {
        val t = toast(ToastPlace.Bottom)
        rule.mainClock.advanceTimeBy(150)
        rule.onRoot().performTouchInput { down(Offset(120f, 300f)) }
        rule.mainClock.advanceTimeBy(200)
        assertTrue("premise: the toast press is lit", (t.look(v1, now())?.lit ?: 0f) > 0f)
        rule.runOnIdle { arb.cancel() }
        rule.mainClock.advanceTimeBy(48)
        assertEquals("the cut snaps the press", 0f, t.look(v1, now())?.lit ?: -1f, 0f)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(16)
        assertEquals(0, toastTaps)
        assertEquals(0, under)
    }

    @Test
    fun aToastHiddenWhileItHoldsTakesNoTapAfterTheNextFrames() {
        val t = toast(ToastPlace.Top)
        rule.mainClock.advanceTimeBy(350 + 500)
        assertTrue(t.visible(now()))
        t.hide()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(48)
        tapNow(120f, 20f)
        assertEquals(0, toastTaps)
        assertEquals(1, under)
    }

    @Test
    fun aTapUnderATopToastGoingOutReachesThePage() {
        toast(ToastPlace.Top)
        rule.mainClock.advanceTimeBy(100 + 3500 + 100)
        tapNow(120f, 30f)
        assertEquals(0, toastTaps)
        assertEquals(1, under)
    }

    private fun held(ms: Long) {
        rule.onRoot().performTouchInput { down(Offset(120f, 300f)) }
        rule.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)
        rule.onRoot().performTouchInput { up() }
    }

    private fun upAfter(ms: Long) {
        rule.onRoot().performTouchInput {
            down(Offset(120f, 300f))
            advanceEventTime(ms)
            up()
        }
        rule.mainClock.advanceTimeBy(FRAME)
    }

    @Test
    fun aToastHeldPastTheHoldTimeTapsNothingAtItsUp() {
        val t = toast(ToastPlace.Bottom)
        rule.mainClock.advanceTimeBy(RISEN)
        held(Tok.input_keyHoldMs.toLong() + HOLD_STEP)
        val lit = t.look(v1, now())?.lit ?: 0f
        rule.mainClock.advanceTimeByFrame()
        assertEquals("a toast held past the hold time taps nothing", 0, toastTaps)
        val next = t.look(v1, now())?.lit ?: 0f
        assertTrue("the press fades from the up, $lit then $next", lit > 0f && next < lit)
        assertEquals(0, under)
    }

    @Test
    fun aToastUpAtExactlyTheHoldTimeTapsNothing() {
        toast(ToastPlace.Bottom)
        rule.mainClock.advanceTimeBy(RISEN)
        upAfter(Tok.input_keyHoldMs.toLong())
        assertEquals("an up at the hold time taps nothing", 0, toastTaps)
    }

    @Test
    fun aToastUpJustUnderTheHoldTimeTaps() {
        toast(ToastPlace.Bottom)
        rule.mainClock.advanceTimeBy(RISEN)
        upAfter(Tok.input_keyHoldMs.toLong() - FRAME)
        assertEquals("an up a frame under the hold time taps", 1, toastTaps)
    }

    @Test
    fun aToastJudgesItsHoldByTheEventTimesNotTheTimeItIsHandled() {
        toast(ToastPlace.Bottom, lag = LAG)
        rule.mainClock.advanceTimeBy(RISEN)
        held(Tok.input_keyHoldMs.toLong() - FRAME / 2)
        rule.mainClock.advanceTimeBy(FRAME)
        assertEquals("the up's event time is under the hold time, so it taps", 1, toastTaps)
    }

    companion object {
        private const val RISEN = 150L
        private const val HOLD_STEP = 50L
        private const val LAG = 16L
        private const val FRAME = 16L
    }
}
