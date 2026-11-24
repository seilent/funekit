package net.seilent.funekit.kit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import net.seilent.funekit.kit.motion.rememberOverlayFrame
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

    private val holds = mutableListOf<Int>()
    private var scrims = 0

    private fun show(form: MenuForm, kind: OverKind, lead: Int = 0, hold: ((Int) -> Boolean)? = null, scrim: () -> Boolean = { false }, opened: Long = 0, closed: Long? = null): Menu {
        val m = Menu().also {
            it.open(form, "play", listOf("play", "play next", "shuffle"), 0, lead = lead)
            it.frame(px(22f, 1f), 320)
        }
        rule.setContent {
            val a = remember { Arbiter(v1) }
            val o = remember {
                Overlay(kind).also {
                    it.open(opened)
                    if (closed != null) it.close(closed)
                }
            }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap({}, {}, { under++ })))
                MenuOverlay(m, o, v1, a, { 1000L }, onPick = { picks += it; true }, onClose = { closes++ }, layer = 1, onHold = hold, onScrim = scrim)
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

    private fun holdAt(x: Float, y: Float) {
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(x, y)) }
        rule.mainClock.advanceTimeBy(Tok.input_keyHoldMs.toLong() + HOLD_STEP, ignoreFrameDuration = true)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test
    fun aHeldSheetItemCallsTheHoldAndPicksNothing() {
        show(MenuForm.Sheet, OverKind.Sheet, hold = { holds += it; true })
        holdAt(120f, 320f - 16f - 43f / 2)
        assertEquals(listOf(2), holds)
        assertEquals(emptyList<Int>(), picks)
    }

    @Test
    fun aHoldWhileTheSheetOpensCallsNoHold() {
        show(MenuForm.Sheet, OverKind.Sheet, hold = { holds += it; true }, opened = 1000L - LAG)
        holdAt(120f, 320f - 16f - 43f / 2)
        assertEquals(emptyList<Int>(), holds)
    }

    @Test
    fun aHoldOnAClosedSheetCallsNoHold() {
        show(MenuForm.Sheet, OverKind.Sheet, hold = { holds += it; true }, opened = -10_000L, closed = 0L)
        holdAt(120f, 320f - 16f - 43f / 2)
        assertEquals(emptyList<Int>(), holds)
    }

    @Test
    fun withNoHoldAHeldItemStillPicksOnRelease() {
        show(MenuForm.Sheet, OverKind.Sheet)
        holdAt(120f, 320f - 16f - 43f / 2)
        assertEquals(listOf(2), picks)
        assertEquals(emptyList<Int>(), holds)
    }

    @Test
    fun aScrimVetoKeepsTheSheetOpen() {
        var keep = true
        show(MenuForm.Sheet, OverKind.Sheet, scrim = { scrims++; keep })
        tap(120f, 30f)
        assertEquals(1, scrims)
        assertEquals(0, closes)
        keep = false
        tap(120f, 30f)
        assertEquals(1, closes)
    }

    private lateinit var frame: State<Long>
    private lateinit var draws: DrawScope.() -> Unit
    private var calls = 0
    private var lastNow = 0L

    private fun seenNow(): Long {
        calls++
        return clock().also { lastNow = it }
    }

    private fun watch(m: Menu) {
        rule.mainClock.autoAdvance = false
        stack = PageStack<String>(MoveForms(), { PageKind.Plain }) { MoveEnds() }.also { it.pages.add("page") }
        rule.setContent {
            val a = remember { Arbiter(a105) }
            PageStackClock(stack) { MoveKind.T4 }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                val o = stack.overlay
                if (o != null) {
                    MenuOverlay(
                        m, o, a105, a, ::seenNow, { picks += it; stack.accept(); true }, { closes++; stack.close() }, 1, Modifier, Ink.white, true, null, null, { false },
                        seen = { f, d ->
                            frame = f
                            draws = d
                        },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(32)
    }

    private lateinit var arb: Arbiter
    private var restarts by mutableIntStateOf(0)

    private fun watchJump(g: JumpGrid, o: Overlay, now: () -> Long = ::seenNow) {
        rule.mainClock.autoAdvance = false
        o.lead = LEAD
        stack = PageStack<String>(MoveForms(), { PageKind.Plain }) { MoveEnds() }.also { it.pages.add("page") }
        rule.setContent {
            val a = remember(restarts) { Arbiter(a105).also { arb = it } }
            PageStackClock(stack) { MoveKind.T4 }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                if (stack.overlay === o) {
                    JumpGridOverlay(
                        g, o, a105, a, now, { jumped += it }, { stack.close() }, {}, 1, Modifier, null,
                        seen = { f, d ->
                            frame = f
                            draws = d
                        },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(32)
    }

    private fun raise(o: Overlay) {
        stack.open(o)
        Snapshot.sendApplyNotifications()
        repeat(OPEN_FRAMES) { if (stack.overlay == null) rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
        assertTrue("premise: the overlay is up", stack.overlay === o)
    }

    private fun tick(): Long {
        val was = frame.value
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        return was
    }

    private fun advances(what: String) {
        val was = tick()
        val f = frame.value
        val t = clock()
        assertTrue("$what: frame $f after $was at $t", f > was && t - FRAME < f && f <= t)
        if (stack.moving) assertEquals("$what: the stack's clock", stack.clock, f)
    }

    private fun drawsAt(what: String) {
        val reads = mutableListOf<Any>()
        val was = calls
        rule.runOnUiThread {
            Snapshot.observe(readObserver = { reads += it }) {
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(a105.widthPx, a105.heightPx)), Size(a105.widthPx.toFloat(), a105.heightPx.toFloat()), draws)
            }
        }
        assertTrue("$what: the draw reads the frame", reads.any { it === frame })
        assertEquals("$what: the draw asks no time", was, calls)
    }

    private fun parks(what: String, n: Int = PARK_FRAMES) {
        val f = frame.value
        repeat(n) {
            tick()
            assertEquals("$what: parked at frame $it", f, frame.value)
        }
    }

    private fun still() {
        var n = 0
        while (tick() != frame.value) assertTrue("premise: the loop parks", ++n < STILL_CAP)
        parks("premise: still", REST_FRAMES)
    }

    private fun wakes(end: Long, by: String, holds: String) {
        val held = frame.value
        while (frame.value == held) {
            assertTrue("$by $end, at ${clock()}", clock() <= end + WAKE)
            tick()
        }
        assertTrue("$holds $end, woke at ${frame.value}", frame.value >= end)
    }

    private fun fades(fade: ThumbFade, end: Long) {
        wakes(end, "the fade wakes by its hold's end", "the thumb holds until")
        while (fade.fading(frame.value)) advances("the fade")
        parks("the fade's end")
        val t = clock()
        assertEquals("the thumb faded out", 0f, fade.at(t), 0f)
        assertFalse("the fade ended", fade.fading(t))
    }

    private fun three(form: MenuForm): Menu = Menu().also {
        it.open(form, "play", listOf("play", "play next", "shuffle"), 0)
        it.frame(px(Tok.statusTop + Tok.statusHeight, a105.stdPpu), 1280)
    }

    private fun risesEveryFrame(form: MenuForm, kind: OverKind) {
        watch(three(form))
        val o = Overlay(kind)
        raise(o)
        var n = 0
        while (o.moving(stack.clock)) {
            advances("rise frame $n")
            drawsAt("rise frame $n")
            n++
        }
        assertTrue("premise: the rise runs frames", n > 1)
    }

    private fun rested(m: Menu): Overlay {
        watch(m)
        val o = Overlay(OverKind.Sheet)
        raise(o)
        while (o.moving(stack.clock)) tick()
        return o
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aSheetsRiseAdvancesItsFrameEveryFrameAndDrawsAtIt() = risesEveryFrame(MenuForm.Sheet, OverKind.Sheet)

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aDialogsPopAdvancesItsFrameEveryFrameAndDrawsAtIt() = risesEveryFrame(MenuForm.Dlg, OverKind.Dlg)

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aPressGlowAtRestAdvancesTheFrameThenItParks() {
        val m = three(MenuForm.Sheet)
        rested(m)
        parks("premise: at rest", REST_FRAMES)
        rule.onRoot().performTouchInput { down(Offset(360f, item(m, 2))) }
        assertTrue("premise: the down presses the row", m.pressed == 2 && m.glow.moving(lastNow))
        while (m.glow.moving(frame.value)) advances("the glow")
        parks("the glow's end")
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aFocusStepAtRestAdvancesTheFrameThenItParks() {
        val m = three(MenuForm.Sheet)
        rested(m)
        parks("premise: at rest", REST_FRAMES)
        var stepped = false
        rule.runOnUiThread {
            stepped = m.step(1, clock())
            Snapshot.sendApplyNotifications()
        }
        assertTrue("premise: the step moves the focus", stepped)
        while (m.focus.animating(frame.value)) advances("the focus fade")
        parks("the focus fade's end")
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aDragsThumbHoldsAfterTheUpThenEveryFrameOfItsFadeAdvances() {
        val m = lines(12)
        rested(m)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 1100f))
            moveBy(Offset(0f, -161f))
            moveBy(Offset(0f, -161f))
        }
        assertTrue("premise: the drag scrolls and holds", m.first > 0 && m.held)
        still()
        rule.onRoot().performTouchInput { up() }
        assertFalse("premise: the up lifts", m.held)
        val end = lastNow + Tok.scrollbar_hold.toLong()
        advances("the lift's wake")
        fades(m.fade, end)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun theLoopTracksTheThumbFadeAndAKeyScrollLightsIt() {
        val m = lines(12)
        watch(m)
        val o = Overlay(OverKind.Sheet)
        raise(o)
        advances("the first frame")
        val end = frame.value + Tok.scrollbar_hold.toLong()
        while (o.moving(stack.clock)) advances("the rise")
        fades(m.fade, end)
        var scrolled = false
        rule.runOnUiThread {
            scrolled = m.scrollTo(m.first + 1, clock())
            Snapshot.sendApplyNotifications()
        }
        assertTrue("premise: the key scrolls", scrolled)
        tick()
        repeat(LIT_FRAMES) {
            tick()
            val t = clock()
            assertTrue("the thumb lights at $t", m.fade.at(t) > 0f && m.fade.fading(t))
        }
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun theCloseAdvancesEveryFrameUntilTheOverlayIsDropped() {
        val o = rested(three(MenuForm.Sheet))
        parks("premise: at rest", REST_FRAMES)
        stack.close()
        Snapshot.sendApplyNotifications()
        repeat(OPEN_FRAMES) { if (!o.closing) tick() }
        assertTrue("premise: the close's ask ran", o.closing)
        var n = 0
        while (stack.overlay != null) {
            advances("close frame $n")
            drawsAt("close frame $n")
            n++
        }
        assertTrue("premise: the close runs frames", n > 1)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aSheetClosedBeforeItsFirstFrameParksAfterIt() {
        val m = three(MenuForm.Sheet)
        val o = Overlay(OverKind.Sheet).also {
            it.open(-10_000L)
            it.close(-5_000L)
        }
        assertFalse("premise: the close is over", o.moving(0L))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val a = remember { Arbiter(a105) }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                MenuOverlay(
                    m, o, a105, a, ::seenNow, { true }, {}, 1, Modifier, Ink.white, true, null, null, { false },
                    seen = { f, d ->
                        frame = f
                        draws = d
                    },
                )
            }
        }
        tick()
        parks("a closed sheet")
    }

    @Test
    fun aFrameComposedOverARisenOverlayStartsAtItsRise() {
        val o = Overlay(OverKind.Sheet).also {
            it.open(0L)
            it.rise(RISEN)
        }
        assertEquals("premise: the overlay rose", RISEN, o.risen)
        var first = -1L
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val f = rememberOverlayFrame(o, ::clock, { false }, { null })
            if (first < 0) first = f.value
        }
        rule.waitForIdle()
        assertEquals(RISEN, first)
    }

    @Test
    fun aFrameAtRestStepsOnceThenParks() {
        val o = Overlay(OverKind.Sheet).also { it.open(-10_000L) }
        var steps = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { frame = rememberOverlayFrame(o, ::clock, { false }, { null }, step = { steps++ }) }
        rule.waitForIdle()
        tick()
        assertEquals("the first frame steps once", 1, steps)
        parks("at rest")
        assertEquals("a parked frame steps no more", 1, steps)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aReentryOverARisenSheetStartsAtTheComposingTimeAndShowsTheRowSetAfterItsRise() {
        val m = three(MenuForm.Sheet)
        val o = Overlay(OverKind.Sheet).also {
            it.open(0L)
            it.rise(RISEN)
        }
        val picked = RISEN + LATE
        m.focus.set(2, m.items.size, picked, false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val a = remember { Arbiter(a105) }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                MenuOverlay(
                    m, o, a105, a, { picked }, { true }, {}, 1, Modifier, Ink.white, true, null, null, { false },
                    seen = { f, d ->
                        frame = f
                        draws = d
                    },
                )
            }
        }
        rule.waitForIdle()
        assertEquals("the first drawn frame is the composing time", picked, frame.value)
        assertEquals("it shows the row set after the rise", 1f, m.focus.rowAlpha(2, frame.value, 1f, 0f), 0f)
    }

    @Test
    fun aWakeAtNotAfterTheFrameFailsPlainly() {
        val o = Overlay(OverKind.Sheet).also { it.open(-10_000L) }
        rule.mainClock.autoAdvance = false
        rule.setContent { frame = rememberOverlayFrame(o, ::clock, { false }, { it }) }
        rule.waitForIdle()
        val failed = runCatching { tick() }.exceptionOrNull()
        assertTrue("a wake at the frame fails plainly: $failed", generateSequence(failed) { it.cause }.any { it is IllegalStateException && it.message == "wakeAt returns a time after now" })
    }

    @Test
    fun aGlowsFadeParksTheFrameAtItsEndWithNoDraw() {
        val g = Glow().also { it.down(0, -10_000L) }
        val o = Overlay(OverKind.Sheet).also { it.open(-10_000L) }
        val fade = clock()
        g.fade(fade)
        rule.mainClock.autoAdvance = false
        rule.setContent { frame = rememberOverlayFrame(o, ::clock, g::moving, g::due) }
        rule.waitForIdle()
        while (clock() < fade + Tok.motion_press.toLong() + FRAME) tick()
        parks("a glow's fade")
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aSecondBarredSheetShowsItsThumbOnOpen() {
        val m = lines(12)
        watch(m)
        val first = Overlay(OverKind.Sheet)
        raise(first)
        advances("the first sheet's first frame")
        val gone = frame.value + (Tok.scrollbar_hold + Tok.scrollbar_fade).toLong()
        while (clock() < gone + FRAME) tick()
        assertEquals("premise: the first sheet's thumb faded out", 0f, m.fade.at(clock()), 0f)
        stack.close()
        Snapshot.sendApplyNotifications()
        var n = 0
        while (stack.overlay != null) {
            assertTrue("premise: the first sheet closes", ++n < STILL_CAP)
            tick()
        }
        rule.runOnUiThread {
            m.open(MenuForm.Sheet, "info", List(12) { i -> "line $i" }, clock(), readOnly = true)
            m.frame(px(Tok.statusTop + Tok.statusHeight, a105.stdPpu), 1280)
            Snapshot.sendApplyNotifications()
        }
        raise(Overlay(OverKind.Sheet))
        advances("the second sheet's first frame")
        assertEquals("premise: both sheets open at the same first", 0, m.first)
        assertEquals("the second sheet shows its thumb", 1f, m.fade.at(frame.value), 0f)
    }

    private fun full(): JumpGrid = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, 1280f / a105.stdPpu) }

    private fun shot(): IntArray {
        val img = ImageBitmap(a105.widthPx, a105.heightPx)
        rule.runOnUiThread {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(a105.widthPx.toFloat(), a105.heightPx.toFloat()), draws)
        }
        return IntArray(a105.widthPx * a105.heightPx).also { img.readPixels(it) }
    }

    private fun faded(g: JumpGrid): Overlay {
        val o = Overlay(OverKind.Jump)
        watchJump(g, o)
        raise(o)
        assertTrue("premise: the grid shows its thumb", g.thumbOn(a105))
        advances("the open's first frame")
        val end = frame.value + Tok.scrollbar_hold.toLong()
        while (o.moving(stack.clock)) tick()
        fades(g.fade, end)
        return o
    }

    private fun settles(g: JumpGrid, what: String) {
        var n = 0
        while (g.press.stale(frame.value)) {
            assertTrue("$what settles within $PRESS_FRAMES frames", n < PRESS_FRAMES)
            advances("$what frame $n")
            n++
        }
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aJumpGridsOpenAdvancesItsFrameEveryFrameAndDrawsAtIt() {
        val o = Overlay(OverKind.Jump)
        watchJump(full(), o)
        raise(o)
        val d = o.dur()
        var n = 0
        while (o.moving(stack.clock)) {
            advances("open frame $n")
            drawsAt("open frame $n")
            n++
        }
        assertTrue("the open of $d ms draws a frame per $FRAME ms, drew $n", n >= d / FRAME)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aJumpGridDrawsItsOpenAtItsFrameNotAtTheClockItIsGiven() {
        val o = Overlay(OverKind.Jump)
        watchJump(full(), o) { PINNED }
        raise(o)
        val shots = mutableListOf<IntArray>()
        repeat(LATE_SHOT + 1) {
            advances("open frame $it")
            if (it == EARLY_SHOT || it == LATE_SHOT) shots += shot()
        }
        assertTrue("premise: the open still moves", o.moving(stack.clock))
        assertFalse("open frames $EARLY_SHOT and $LATE_SHOT draw two looks", shots[0].contentEquals(shots[1]))
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aJumpGridsCloseAdvancesEveryFrameUntilItIsDropped() {
        val o = faded(full())
        stack.close()
        Snapshot.sendApplyNotifications()
        repeat(OPEN_FRAMES) { if (!o.closing) tick() }
        assertTrue("premise: the close's ask ran", o.closing)
        val d = o.dur()
        var n = 0
        while (stack.overlay != null) {
            advances("close frame $n")
            drawsAt("close frame $n")
            n++
        }
        assertTrue("the close of $d ms draws a frame per $FRAME ms, drew $n", n >= d / FRAME)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aPressAndAKeyOnARestingJumpGridAdvanceItsFrameThenItParks() {
        val g = full()
        faded(g)
        rule.onRoot().performTouchInput { down(Offset(px(Tok.jumpLeft + Tok.jumpPitch + Tok.jumpPitch / 2, a105.stdPpu).toFloat(), px(Tok.jumpTop + Tok.jumpPitch / 2, a105.stdPpu).toFloat())) }
        assertEquals("premise: the down presses the letter", 0, g.press.on.item)
        settles(g, "the press")
        parks("the held press")
        rule.onRoot().performTouchInput { up() }
        assertEquals("premise: the up taps the letter", listOf(0), jumped)
        val release = lastNow + Tok.motion_touchReturnDelay.toLong()
        advances("the up")
        wakes(release, "the release wakes by", "the press holds its frame until")
        parks("the release's end")
        rule.runOnUiThread {
            g.keyDown(clock())
            Snapshot.sendApplyNotifications()
        }
        settles(g, "the key")
        parks("the held key")
        rule.runOnUiThread {
            g.keyUp(clock())
            Snapshot.sendApplyNotifications()
        }
        advances("the key up")
        parks("the key's end")
    }

    private fun dragGrid(): JumpGrid = JumpGrid().also { it.open(BooleanArray(Section.count) { s -> s != EMPTY }, MID, 1280f / a105.stdPpu) }

    private fun cellAt(g: JumpGrid, i: Int): Offset {
        val slot = Jump.slotOf(i)
        return Offset(px(Jump.cellX(slot) + Jump.tile / 2, a105.stdPpu).toFloat(), px(Jump.rowTop(slot) - g.offset + Jump.tile / 2, a105.stdPpu).toFloat())
    }

    private fun dragPremises(g: JumpGrid) {
        assertTrue("premise: the grid scrolls and shows its thumb", g.limit() > 0f && g.thumbOn(a105))
        assertTrue("premise: the focus stays in band at every offset", g.inBand(Jump.slotOf(MID), 0f) && g.inBand(Jump.slotOf(MID), g.limit()))
        assertTrue("premise: the empty cell shows", !g.has(EMPTY) && g.inBand(Jump.slotOf(EMPTY), g.offset))
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aDragFromAnEmptyJumpCellAndItsFlingAdvanceEveryFrameThenTheThumbFades() {
        val g = dragGrid()
        val o = Overlay(OverKind.Jump)
        watchJump(g, o)
        raise(o)
        while (o.moving(stack.clock)) tick()
        dragPremises(g)
        val at = cellAt(g, EMPTY)
        rule.onRoot().performTouchInput { down(at) }
        assertTrue("premise: the down comes inside the thumb's hold", g.fade.wakeAt(clock()) > clock())
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -(arb.slop.slop + MOVE_PX))) }
        advances("drag frame 0")
        repeat(MOVES) {
            rule.onRoot().performTouchInput { moveBy(Offset(0f, -MOVE_PX)) }
            advances("drag frame ${it + 1}")
        }
        assertTrue("the grid follows the drag", g.offset > 0f)
        rule.onRoot().performTouchInput { up() }
        val list = checkNotNull(arb.listAt(at))
        var n = 0
        while (list.moving) {
            advances("fling frame $n")
            n++
        }
        assertTrue("premise: the fling runs $FLING_FRAMES frames, ran $n", n >= FLING_FRAMES)
        assertEquals("premise: no letter took the drag", emptyList<Int>(), jumped)
        still()
        val end = g.fade.wakeAt(frame.value)
        assertTrue("premise: the thumb holds after the fling", end > frame.value)
        fades(g.fade, end)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aDragBringsTheJumpGridsFadedThumbBack() {
        val g = dragGrid()
        faded(g)
        dragPremises(g)
        assertEquals("premise: the thumb faded out", 0f, g.fade.at(frame.value), 0f)
        rule.onRoot().performTouchInput { down(cellAt(g, EMPTY)) }
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -(arb.slop.slop + MOVE_PX))) }
        advances("drag frame 0")
        var was = 0f
        repeat(MOVES) {
            rule.onRoot().performTouchInput { moveBy(Offset(0f, -MOVE_PX)) }
            advances("drag frame ${it + 1}")
            val level = g.fade.at(frame.value)
            assertTrue("drag frame ${it + 1}: the thumb shows, $level after $was", level > was)
            was = level
        }
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aDragThatEndsWithNoUpLetsTheJumpGridPark() {
        val g = dragGrid()
        val o = Overlay(OverKind.Jump)
        watchJump(g, o)
        raise(o)
        while (o.moving(stack.clock)) tick()
        dragPremises(g)
        val at = cellAt(g, EMPTY)
        rule.onRoot().performTouchInput { down(at) }
        assertTrue("premise: the down comes inside the thumb's hold", g.fade.wakeAt(clock()) > clock())
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -(arb.slop.slop + MOVE_PX))) }
        advances("drag frame 0")
        repeat(MOVES) {
            rule.onRoot().performTouchInput { moveBy(Offset(0f, -MOVE_PX)) }
            advances("drag frame ${it + 1}")
        }
        val list = checkNotNull(arb.listAt(at))
        assertTrue("premise: the list drags", list.dragging)
        rule.runOnUiThread {
            restarts++
            Snapshot.sendApplyNotifications()
        }
        tick()
        tick()
        assertFalse("the restart's frame, then the next: the drag ended", list.dragging)
        still()
        val end = g.fade.wakeAt(frame.value)
        assertTrue("premise: the thumb holds after the drag", end > frame.value)
        fades(g.fade, end)
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aJumpGridsThumbHoldsThenEveryFrameOfItsFadeAdvances() {
        faded(full())
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun aJumpGridParksAtRestAfterItsThumbFades() {
        val g = full()
        val o = Overlay(OverKind.Jump)
        watchJump(g, o)
        raise(o)
        advances("the open's first frame")
        val gone = frame.value + (Tok.scrollbar_hold + Tok.scrollbar_fade).toLong()
        while (clock() < gone + FRAME) tick()
        assertFalse("premise: the open and the thumb's fade are over", o.moving(clock()) || g.fade.fading(clock()))
        parks("at rest")
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun theJumpGridsFocusFollowsAFlingsLastStep() {
        val end = Jump.rowTop(TAIL_ROW * Jump.cols) - Tok.edge + TAIL_OUT
        val g = JumpGrid().also { it.open(BooleanArray(Section.count) { s -> s != EMPTY }, 0, Jump.content() - end) }
        val o = Overlay(OverKind.Jump)
        watchJump(g, o)
        raise(o)
        while (o.moving(stack.clock)) tick()
        assertTrue("premise: the grid scrolls and shows its thumb", g.limit() > 0f && g.thumbOn(a105))
        assertTrue("premise: the focus sits in the top band", g.focus == 0 && g.inBand(Jump.slotOf(0), g.offset))
        val at = cellAt(g, EMPTY)
        assertTrue("premise: the down cell is empty", !g.has(EMPTY))
        rule.onRoot().performTouchInput { down(at) }
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -(arb.slop.slop + 1f))) }
        tick()
        val step = (g.limit() - TAIL_LEFT) * a105.stdPpu / TAIL_MOVES
        repeat(TAIL_MOVES) {
            rule.onRoot().performTouchInput { moveBy(Offset(0f, -step)) }
            tick()
        }
        val last = Jump.slotOf(g.focus)
        assertTrue("premise: the drag leaves the focus in band, ${g.offset} of ${g.limit()}", g.inBand(last, g.offset) && !g.inBand(last, g.limit()))
        rule.onRoot().performTouchInput { up() }
        val list = checkNotNull(arb.listAt(at))
        var n = 0
        while (list.moving) {
            tick()
            n++
        }
        assertTrue("premise: the fling's first step reaches the end, in $n frames", n <= TAIL_FRAMES && !g.inBand(last, g.offset))
        assertTrue("the first frame with no move: the focus ${g.focus} is in band at ${g.offset}", g.inBand(Jump.slotOf(g.focus), g.offset))
    }

    @Test
    @Config(qualifiers = A105_SCREEN)
    fun theJumpGridsFocusFollowsADrag() {
        val g = JumpGrid().also { it.open(BooleanArray(Section.count) { s -> s != TOP_EMPTY }, 0, 1280f / a105.stdPpu) }
        val o = Overlay(OverKind.Jump)
        watchJump(g, o)
        raise(o)
        while (o.moving(stack.clock)) tick()
        val top = Jump.slotOf(g.focus)
        assertTrue("premise: the grid scrolls and shows its thumb", g.limit() > 0f && g.thumbOn(a105))
        assertTrue("premise: the focus sits on the top row at offset 0", g.focus == 0 && g.offset == 0f && g.inBand(top, 0f))
        assertTrue("premise: the down cell is empty", !g.has(TOP_EMPTY))
        rule.onRoot().performTouchInput { down(cellAt(g, TOP_EMPTY)) }
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -(arb.slop.slop + 1f))) }
        advances("drag frame 0")
        var n = 0
        while (g.inBand(top, g.offset)) {
            assertTrue("premise: the drag takes the focused slot out of the band within $TOP_STEPS steps", ++n <= TOP_STEPS)
            rule.onRoot().performTouchInput { moveBy(Offset(0f, -STEP_PX)) }
            advances("drag frame $n")
        }
        assertTrue("drag frame $n: the focus moves into the band, at ${g.focus}", g.focus != 0 && g.inBand(Jump.slotOf(g.focus), g.offset))
    }

    companion object {
        private const val HOLD_STEP = 50L
        private const val A105_SCREEN = "w720dp-h1280dp-mdpi"
        private const val OPEN_FRAMES = 10
        private const val LAG = 16L
        private const val FRAME = 16L
        private const val WAKE = 2 * FRAME
        private const val PARK_FRAMES = 30
        private const val REST_FRAMES = 2
        private const val STILL_CAP = 60
        private const val LIT_FRAMES = 3
        private const val RISEN = 1000L
        private const val LATE = 100L
        private const val LEAD = 17L
        private const val PRESS_FRAMES = 4
        private const val PINNED = 0L
        private const val EARLY_SHOT = 2
        private const val LATE_SHOT = 6
        private const val MID = 12
        private const val EMPTY = 20
        private const val MOVES = 2
        private const val MOVE_PX = 20f
        private const val FLING_FRAMES = 5
        private const val TOP_EMPTY = 1
        private const val STEP_PX = 10f
        private const val TOP_STEPS = 6
        private const val TAIL_ROW = 3
        private const val TAIL_OUT = 2f
        private const val TAIL_LEFT = 4f
        private const val TAIL_MOVES = 5
        private const val TAIL_FRAMES = 2
    }
}
