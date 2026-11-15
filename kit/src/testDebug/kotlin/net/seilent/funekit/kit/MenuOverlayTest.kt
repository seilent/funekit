package net.seilent.funekit.kit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.core.Section
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.PageStackClock
import net.seilent.funekit.kit.stack.watchHeld
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.px
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
class MenuOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val picks = mutableListOf<Int>()
    private var closes = 0
    private var under = 0

    private fun show(form: MenuForm, kind: OverKind, lead: Int = 0): Menu {
        val m = Menu().also {
            it.open(form, "play", listOf("play", "play next", "shuffle"), 0, lead = lead)
            it.frame(px(22f, 1f), 320)
        }
        rule.setContent {
            val a = remember { Arbiter(v1) }
            val o = remember { Overlay(kind).also { it.open(0) } }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap({}, {}, { under++ })))
                MenuOverlay(m, o, v1, a, { 1000L }, onPick = { picks += it; true }, onClose = { closes++ }, layer = 1)
            }
        }
        rule.waitForIdle()
        return m
    }

    private fun tap(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun aSheetItemTapPicksItAndHoldsItsPress() {
        val m = show(MenuForm.Sheet, OverKind.Sheet)
        tap(120f, 320f - 16f - 43f / 2)
        assertEquals(listOf(2), picks)
        assertEquals(2, m.pressed)
        assertEquals(0, under)
    }

    @Test
    fun aTapAboveTheSheetClosesItAndThePanelTakesNoClose() {
        show(MenuForm.Sheet, OverKind.Sheet)
        tap(120f, 30f)
        assertEquals(1, closes)
        tap(120f, 320f - 3f)
        assertEquals(1, closes)
        assertEquals(0, under)
    }

    private fun drag(vararg ys: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(120f, ys.first()))
            for (y in ys.drop(1)) moveTo(Offset(120f, y))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun aSwipeDownThatFirstRosePastTheSlopDoesNotClose() {
        show(MenuForm.Sheet, OverKind.Sheet)
        drag(200f, 170f, 310f)
        assertEquals(0, closes)
        assertEquals(emptyList<Int>(), picks)
        drag(200f, 190f, 310f)
        assertEquals(1, closes)
    }

    @Test
    fun aTapWhoseUpLandsOnTheNextItemPicksTheDownsItem() {
        show(MenuForm.Sheet, OverKind.Sheet)
        drag(266f, 248f)
        assertEquals(listOf(2), picks)
    }

    @Test
    fun aSwipeDownOnADialogCloses() {
        show(MenuForm.Dlg, OverKind.Dlg)
        drag(160f, 220f, 280f)
        assertEquals(1, closes)
        assertEquals(emptyList<Int>(), picks)
    }

    @Test
    fun aDialogClosesOnATapOffItsItemsTitleAndLead() {
        show(MenuForm.Dlg, OverKind.Dlg, lead = 1)
        tap(120f, 98f + 43f / 2)
        assertEquals(0, closes)
        assertEquals(emptyList<Int>(), picks)
        tap(120f, 98f + 43f + 43f / 2)
        assertEquals(listOf(1), picks)
        tap(120f, 300f)
        assertEquals(1, closes)
    }

    @Test
    fun overTheNavBandASheetTakesNoClose() {
        val nav = scale(Window(240, 320, 35, none, Insets(0, 0, 0, 36), none, none, none), Nav.Buttons, Surface.Page, false)
        val m = Menu().also {
            it.open(MenuForm.Sheet, "play", listOf("play"), 0)
            it.frame(px(22f, 1f), 284)
        }
        rule.setContent {
            val a = remember { Arbiter(nav) }
            val o = remember { Overlay(OverKind.Sheet).also { it.open(0) } }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap({}, {}, { under++ })))
                MenuOverlay(m, o, nav, a, { 1000L }, onPick = { picks += it; true }, onClose = { closes++ }, layer = 1, band = NavGround(emptyList(), Ink.white, ground = false))
            }
        }
        rule.waitForIdle()
        tap(120f, 300f)
        assertEquals(0, closes)
        assertEquals(0, under)
        tap(120f, 30f)
        assertEquals(1, closes)
    }

    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private lateinit var stack: PageStack<String>
    private val jumped = mutableListOf<Int>()

    private fun clock() = rule.mainClock.currentTime

    private fun stacked(m: Menu?, g: JumpGrid? = null, lag: Long = 0L) {
        rule.mainClock.autoAdvance = false
        stack = PageStack<String>(MoveForms(), { PageKind.Plain }) { MoveEnds() }.also { it.pages.add("page") }
        rule.setContent {
            val a = remember { Arbiter(a105) }
            PageStackClock(stack) { MoveKind.T4 }
            val root = Modifier.fillMaxSize().pointerInput(stack) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (stack.moving) watchHeld(stack, d, a.slop, false, Gesture.Edge.None) { MoveKind.T4 }
                }
            }
            Box(root.arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap({}, {}, { under++ })))
                val o = stack.overlay
                if (o != null && m != null) {
                    MenuOverlay(m, o, a105, a, { clock() + lag }, onPick = { picks += it; stack.accept(); true }, onClose = { closes++; stack.close() }, layer = 1)
                }
                if (o != null && g != null) {
                    JumpGridOverlay(g, o, a105, a, ::clock, onPick = { jumped += it }, onExit = { stack.close() }, onSearch = {}, layer = 1)
                }
            }
        }
        rule.mainClock.advanceTimeBy(32)
    }

    private fun item(m: Menu, i: Int): Float {
        val slot = checkNotNull(m.placed).rows.first { it.i == i }
        return slot.top + slot.h / 2f
    }

    private fun press(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        rule.mainClock.advanceTimeBy(16)
    }

    private fun lines(n: Int): Menu = Menu().also {
        it.open(MenuForm.Sheet, "info", List(n) { i -> "line $i" }, 0, readOnly = true)
        it.frame(px(Tok.statusTop + Tok.statusHeight, a105.stdPpu), 1280)
    }

    private fun drawn(m: Menu) {
        rule.setContent {
            val a = remember { Arbiter(a105) }
            val o = remember { Overlay(OverKind.Sheet).also { it.open(-10_000L) } }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                MenuOverlay(m, o, a105, a, ::clock, onPick = { picks += it; true }, onClose = { closes++ }, layer = 1)
            }
        }
        rule.waitForIdle()
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aTallSheetScrollsByWholeRowsUnderADrag() {
        val m = lines(12)
        drawn(m)
        assertEquals(0, m.first)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 1100f))
            moveBy(Offset(0f, -100f))
            moveBy(Offset(0f, -100f))
            moveBy(Offset(0f, -122f))
            up()
        }
        rule.waitForIdle()
        assertEquals(2, m.first)
        assertEquals(0, closes)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aSwipeDownFromTheTopThatScrollsNothingCloses() {
        val m = lines(12)
        drawn(m)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 900f))
            moveBy(Offset(0f, 100f))
            moveBy(Offset(0f, 100f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aSwipeDownOnAScrolledSheetDoesNotClose() {
        val m = lines(12)
        drawn(m)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 1100f))
            moveBy(Offset(0f, -161f))
            moveBy(Offset(0f, -161f))
            up()
        }
        rule.waitForIdle()
        assertEquals(2, m.first)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 700f))
            moveBy(Offset(0f, 100f))
            moveBy(Offset(0f, 100f))
            up()
        }
        rule.waitForIdle()
        assertEquals(0, closes)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aPressMovedPastTheSlopDropsItsPress() {
        val m = Menu().also {
            it.open(MenuForm.Sheet, "play", listOf("play", "play next", "shuffle"), 0)
            it.frame(px(Tok.statusTop + Tok.statusHeight, a105.stdPpu), 1280)
        }
        drawn(m)
        val y = item(m, 1)
        rule.onRoot().performTouchInput { down(Offset(360f, y)) }
        rule.waitForIdle()
        assertEquals(1, m.pressed)
        rule.onRoot().performTouchInput { moveBy(Offset(200f, 0f)) }
        rule.waitForIdle()
        assertEquals(-1, m.pressed)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertTrue(picks.isEmpty())
    }

    private fun opened(kind: OverKind, form: MenuForm, confirm: Boolean = false, lag: Long = 0L): Menu {
        val m = Menu().also {
            it.open(form, "play", listOf("play", "play next", "shuffle"), 0, confirm = confirm)
            it.frame(px(Tok.statusTop + Tok.statusHeight, a105.stdPpu), 1280)
        }
        stacked(m, lag = lag)
        stack.open(Overlay(kind))
        Snapshot.sendApplyNotifications()
        repeat(OPEN_FRAMES) { if (stack.overlay == null) rule.mainClock.advanceTimeByFrame() }
        return m
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aTapDuringTheRiseDoesNothingAndLandsNothing() {
        val m = opened(OverKind.Sheet, MenuForm.Sheet)
        val o = checkNotNull(stack.overlay)
        rule.mainClock.advanceTimeBy(100)
        assertTrue(o.moving(clock()))
        press(360f, item(m, 2))
        assertTrue(picks.isEmpty())
        assertTrue(o.moving(clock()))
        rule.mainClock.advanceTimeBy(1000)
        assertFalse(o.moving(clock()))
        press(360f, item(m, 2))
        assertEquals(listOf(2), picks)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aTapDuringTheCloseDoesNothingAndTheCloseRunsOn() {
        val m = opened(OverKind.Sheet, MenuForm.Sheet)
        rule.mainClock.advanceTimeBy(1000)
        stack.close()
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        val o = checkNotNull(stack.overlay)
        assertTrue(o.closing)
        press(360f, item(m, 2))
        assertTrue(picks.isEmpty())
        assertTrue(stack.overlay === o && o.closing && o.moving(clock()))
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aTapOnALetterDuringTheJumpGridOpenDoesNothing() {
        val g = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, 1280f / a105.stdPpu) }
        stacked(null, g)
        stack.open(Overlay(OverKind.Jump))
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(100)
        val o = checkNotNull(stack.overlay)
        assertTrue(o.moving(clock()))
        press(px(Tok.jumpLeft + Tok.jumpPitch + Tok.jumpPitch / 2, a105.stdPpu).toFloat(), px(Tok.jumpTop + Tok.jumpPitch / 2, a105.stdPpu).toFloat())
        assertTrue(jumped.isEmpty())
        assertTrue(o.moving(clock()))
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aConfirmDialogTakesNoPickWhoseDownCameAtOrBeforeItsRiseEnd() {
        val m = opened(OverKind.Dlg, MenuForm.Dlg, confirm = true)
        val o = checkNotNull(stack.overlay)
        while (o.risen < 0) rule.mainClock.advanceTimeBy(16)
        assertEquals(o.risen, clock())
        assertFalse(o.moving(clock()))
        press(360f, item(m, 2))
        assertTrue(picks.isEmpty())
        assertTrue(clock() > o.risen)
        press(360f, item(m, 2))
        assertEquals(listOf(2), picks)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aConfirmDialogJudgesADownByItsEventTimeNotTheTimeItIsHandled() {
        val m = opened(OverKind.Dlg, MenuForm.Dlg, confirm = true, lag = LAG)
        val o = checkNotNull(stack.overlay)
        while (o.risen < 0) rule.mainClock.advanceTimeBy(16)
        assertEquals(o.risen, clock())
        assertFalse(o.moving(clock() + LAG))
        press(360f, item(m, 2))
        assertTrue(picks.isEmpty())
        press(360f, item(m, 2))
        assertEquals(listOf(2), picks)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aSwipeDownTakesTheSwipeDistanceOfTheArbiterItIsComposedWith() {
        val big = scale(Window(1080, 2160, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
        val small = Arbiter(a105).slop.swipe
        val large = Arbiter(big).slop.swipe
        val d = (small + large) / 2f
        assertTrue("premise: $d lies between $small and $large", d > small && d < large)
        val m = lines(12)
        var arb by mutableStateOf(Arbiter(a105))
        rule.setContent {
            val o = remember { Overlay(OverKind.Sheet).also { it.open(-10_000L) } }
            Box(Modifier.fillMaxSize().arbiter(arb, Swipes(false, false, false, { false }, { _, _ -> }))) {
                MenuOverlay(m, o, a105, arb, ::clock, onPick = { picks += it; true }, onClose = { closes++ }, layer = 1)
            }
        }
        rule.waitForIdle()
        fun swipe() = rule.onRoot().performTouchInput {
            down(Offset(360f, 900f))
            moveBy(Offset(0f, d / 2f))
            moveBy(Offset(0f, d / 2f))
            up()
        }
        swipe()
        rule.waitForIdle()
        assertEquals("premise: the swipe closes at the first arbiter's distance", 1, closes)
        arb = Arbiter(big)
        rule.waitForIdle()
        swipe()
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    companion object {
        private const val A105_SCREEN = "w720dp-h1280dp-mdpi"
        private const val OPEN_FRAMES = 10
        private const val LAG = 16L
    }
}
