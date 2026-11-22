package net.seilent.funekit.kit.touch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.seilent.funekit.Dpi
import net.seilent.funekit.FuneTheme
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kept
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.scrub.edgeScrub
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.ln

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArbiterTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Page, false)

    private val swipes = mutableListOf<Swipe>()
    private var presses = 0
    private var cancels = 0
    private var cutCount = 0
    private var taps = 0
    private var longs = 0
    private var takesLong = true
    private lateinit var touch: ListTouch
    private lateinit var arb: Arbiter

    private val tap = Tap(
        onPress = { presses++ },
        onCancel = { cancels++ },
        onTap = { taps++ },
        onLong = {
            longs++
            takesLong
        },
    )

    private fun page(turn: Boolean = true, pan: Pan? = null) = Swipes(
        edges = true,
        bottom = false,
        turn = turn,
        acts = { it == Swipe.Left || it == Swipe.Right },
        onSwipe = { s, _ -> swipes += s },
        pan = pan,
    )

    private val begins = mutableListOf<Offset>()
    private val drags = mutableListOf<Float>()
    private val ends = mutableListOf<Swipe>()

    private fun pan(takes: (x: Float, origin: Float) -> Boolean) = object : Pan {
        override fun begin(x: Float, origin: Float, down: Offset): Boolean {
            if (!takes(x, origin)) return false
            begins += down
            return true
        }

        override fun drag(x: Float) {
            drags += x
        }

        override fun end(s: Swipe) {
            ends += s
        }
    }

    private fun list(pan: Pan? = null) {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            arb = a
            touch = rememberListTouch(Kind.List, v1)
            Box(Modifier.fillMaxSize().arbiter(a, page(pan = pan))) {
                FuneList(a, touch, Modifier.fillMaxSize()) {
                    items(200) { Box(Modifier.fillMaxWidth().height(40.dp).tapTarget(a, tap)) }
                }
            }
        }
    }

    @Test
    fun aTapWithinTheSlopPressesAndTaps() {
        list()
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(10f, 10f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, presses)
        assertEquals(1, taps)
        assertEquals(0, cancels)
    }

    @Test
    fun aScrollThatTurnsIntoASwipeStopsTheListWithNoFling() {
        list()
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
            moveBy(Offset(0f, -40f))
            moveBy(Offset(-30f, 0f))
            moveBy(Offset(-30f, 0f))
            moveBy(Offset(-30f, 0f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Swipe.Left), swipes)
        assertFalse(touch.scroll.flinging)
        assertFalse(touch.scroll.dragging)
        assertEquals(0, taps)
        assertEquals(1, cancels)
    }

    private fun cutList() {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            arb = a
            touch = rememberListTouch(Kind.List, v1)
            Box(Modifier.fillMaxSize().arbiter(a, page())) {
                FuneList(a, touch, Modifier.fillMaxSize()) {
                    items(200) { Box(Modifier.fillMaxWidth().height(40.dp).tapTarget(a, Tap(onPress = { presses++ }, onCancel = { cancels++ }, onTap = { taps++ }, onCut = { cutCount++ }))) }
                }
            }
        }
    }

    @Test
    fun aSwipeTakeCutsThePressItsSlopExitFaded() {
        cutList()
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
            moveBy(Offset(0f, -40f))
            moveBy(Offset(-30f, 0f))
            moveBy(Offset(-30f, 0f))
            moveBy(Offset(-30f, 0f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Swipe.Left), swipes)
        assertEquals(1, presses)
        assertEquals(1, cancels)
        assertEquals(1, cutCount)
        assertEquals(0, taps)
    }

    @Test
    fun aSlopExitWithNoSwipeTakeOnlyFadesThePress() {
        cutList()
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
            moveBy(Offset(0f, -40f))
            up()
        }
        rule.waitForIdle()
        assertEquals(emptyList<Swipe>(), swipes)
        assertEquals(1, presses)
        assertEquals(1, cancels)
        assertEquals(0, cutCount)
    }

    @Test
    fun aTouchOnAFlingingListOnlyStopsIt() {
        list()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, -60f), delayMillis = 8) }
            up()
        }
        rule.mainClock.advanceTimeBy(48)
        assertTrue(touch.scroll.flinging)
        val before = presses
        rule.onRoot().performTouchInput {
            down(center)
            up()
        }
        rule.mainClock.advanceTimeBy(16)
        assertFalse(touch.scroll.flinging)
        assertEquals(before, presses)
        assertEquals(0, taps)
    }

    @Test
    fun aDragAndItsFlingReportEveryScrollToTheList() {
        list()
        val calls = mutableListOf<Pair<Boolean, Int>>()
        rule.runOnIdle {
            val item = touch.state.layoutInfo.visibleItemsInfo.first().size
            touch.onScroll = { calls += touch.scroll.dragging to touch.state.firstVisibleItemIndex * item + touch.state.firstVisibleItemScrollOffset }
        }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, -60f), delayMillis = 8) }
            up()
        }
        rule.mainClock.advanceTimeBy(48)
        assertTrue(touch.scroll.flinging)
        val dragged = calls.takeWhile { it.first }.map { it.second }
        assertEquals(listOf(60, 120, 180, 240), dragged)
        val flung = calls.drop(dragged.size)
        assertTrue(flung.isNotEmpty() && flung.none { it.first })
        assertTrue(calls.zipWithNext().all { (p, q) -> q.second > p.second })
    }

    @Test
    fun anOverscrollReturnWithNoFlingReportsNoScrollAfterTheUp() {
        list()
        val calls = mutableListOf<Boolean>()
        rule.runOnIdle { touch.onScroll = { calls += touch.scroll.dragging } }
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, 60f), delayMillis = 8) }
            up()
        }
        rule.mainClock.advanceTimeBy(48)
        assertTrue(touch.scroll.returning)
        assertFalse(touch.scroll.flinging)
        rule.mainClock.advanceTimeBy(1000)
        assertFalse(touch.moving)
        assertEquals(List(4) { true }, calls)
    }

    @Test
    fun aHoldOf400msIsALongPressAndTheLiftDoesNothing() {
        list()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(399, ignoreFrameDuration = true)
        assertEquals(0, longs)
        rule.mainClock.advanceTimeBy(17, ignoreFrameDuration = true)
        assertEquals(1, longs)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(16)
        assertEquals(0, taps)
    }

    @Test
    fun aMoveInsideTheSlopKeepsTheHoldTimedFromTheDown() {
        list()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(200, ignoreFrameDuration = true)
        rule.onRoot().performTouchInput { moveBy(Offset(0f, 2f), delayMillis = 0) }
        rule.mainClock.advanceTimeBy(199, ignoreFrameDuration = true)
        assertEquals(0, longs)
        rule.mainClock.advanceTimeBy(17, ignoreFrameDuration = true)
        assertEquals(1, longs)
    }

    @Test
    fun aMovePastTheSlopBeforeTheHoldFiresNoLongPress() {
        list()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
        rule.onRoot().performTouchInput { moveBy(Offset(0f, 60f)) }
        rule.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        assertEquals(0, longs)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(16)
        assertEquals(0, longs)
        assertEquals(0, taps)
    }

    @Test
    fun anUpBeforeTheHoldTapsAndFiresNoLongPress() {
        list()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300, ignoreFrameDuration = true)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        assertEquals(0, longs)
        assertEquals(1, taps)
    }

    @Test
    fun aLongPressTheTapRefusesStillTapsAtTheUp() {
        takesLong = false
        list()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        assertEquals(1, longs)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(16)
        assertEquals(1, taps)
    }

    @Test
    fun aTakenLongPressTapsNothingScrollsNothingAndCommitsNoSwipe() {
        list()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        assertEquals(1, longs)
        val px = touch.state.firstVisibleItemIndex to touch.state.firstVisibleItemScrollOffset
        rule.onRoot().performTouchInput {
            moveBy(Offset(0f, -100f))
            moveBy(Offset(-100f, -100f))
            moveBy(Offset(-100f, -100f))
            up()
        }
        rule.mainClock.advanceTimeBy(16)
        assertEquals(0, taps)
        assertEquals(px, touch.state.firstVisibleItemIndex to touch.state.firstVisibleItemScrollOffset)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun aScrubThatTakesTheTouchCancelsThePress() {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                Box(Modifier.fillMaxSize().tapTarget(a, tap).edgeScrub(30f, 25f, listOf("a", "b", "c")) {})
            }
        }
        rule.onRoot().performTouchInput {
            down(Offset(width - 10f, centerY))
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 20f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, presses)
        assertEquals(1, cancels)
        assertEquals(0, taps)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun theClippedPartOfAScrolledRowNeverTakesATap() {
        var heads = 0
        val head = Tap(onPress = { }, onCancel = { }, onTap = { heads++ })
        rule.setContent {
            val a = remember { Arbiter(v1) }
            touch = rememberListTouch(Kind.List, v1)
            Box(Modifier.fillMaxSize().arbiter(a, page())) {
                Column {
                    Box(Modifier.fillMaxWidth().height(40.dp).tapTarget(a, head))
                    FuneList(a, touch, Modifier.fillMaxSize()) {
                        items(200) { Box(Modifier.fillMaxWidth().height(40.dp).tapTarget(a, tap)) }
                    }
                }
            }
        }
        val row = with(rule.density) { 40.dp.toPx() }
        rule.runOnIdle { touch.state.requestScrollToItem(0, (row / 2).toInt()) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput {
            down(Offset(centerX, row - 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, heads)
        assertEquals(0, taps)
    }

    @Test
    fun hitsAreInTheArbitersOwnSpaceUnderPadding() {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            Box(Modifier.fillMaxSize().padding(start = 30.dp, top = 30.dp)) {
                Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                    Box(Modifier.size(40.dp).tapTarget(a, tap))
                }
            }
        }
        val d = with(rule.density) { 30.dp.toPx() }
        val e = with(rule.density) { 35.dp.toPx() }
        rule.onRoot().performTouchInput {
            down(Offset(e + d, e))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, taps)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aBatchedSampleLocksTheListAndTheGestureOnTheSameAxis() {
        list()
        rule.onRoot().performTouchInput {
            down(center)
            updatePointerTo(0, center + Offset(-40f, 80f))
            moveWithHistory(listOf(-8L), listOf(center + Offset(-40f, 0f)))
        }
        rule.waitForIdle()
        assertEquals(Gesture.State.Horizontal, arb.gesture.state)
        assertFalse(touch.scroll.dragging)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, touch.state.firstVisibleItemIndex)
        assertEquals(0, touch.state.firstVisibleItemScrollOffset)
    }

    @Test
    fun aModalAboveTheRowsTakesEveryTouch() {
        var panel = 0
        rule.setContent {
            val a = remember { Arbiter(v1) }
            touch = rememberListTouch(Kind.List, v1)
            Box(Modifier.fillMaxSize().arbiter(a, page())) {
                FuneList(a, touch, Modifier.fillMaxSize()) {
                    items(200) { Box(Modifier.fillMaxWidth().height(40.dp).tapTarget(a, tap)) }
                }
                BlockBelow(a, 1)
                Box(
                    Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
                        .tapTarget(a, Tap(onPress = {}, onCancel = {}, onTap = { panel++ }), layer = 1),
                )
            }
        }
        rule.onRoot().performTouchInput {
            down(Offset(centerX, 20f))
            up()
        }
        rule.waitForIdle()
        assertEquals(0, presses)
        assertEquals(0, taps)
        rule.onRoot().performTouchInput {
            down(Offset(centerX, height - 20f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, panel)
        assertEquals(0, taps)
    }

    @Test
    fun aRightLockBeginsThePanDropsTheListAndTheTapAndEndsAtTheLift() {
        list(pan { x, origin -> x > origin })
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 0f))
            advanceEventTime(8)
            up()
        }
        rule.waitForIdle()
        assertEquals(1, begins.size)
        assertEquals(1, presses)
        assertEquals(1, cancels)
        assertEquals(0, taps)
        assertFalse(touch.scroll.dragging)
        assertTrue(swipes.isEmpty())
        assertEquals(listOf(Swipe.Right), ends)
        assertEquals(100f, drags.last() - begins[0].x, 0.5f)
    }

    @Test
    fun aLockThePanRefusesFallsToTheSwipe() {
        list(pan { x, origin -> x > origin })
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-40f, 0f))
            moveBy(Offset(-40f, 0f))
            up()
        }
        rule.waitForIdle()
        assertTrue(begins.isEmpty())
        assertTrue(drags.isEmpty())
        assertTrue(ends.isEmpty())
        assertEquals(listOf(Swipe.Left), swipes)
    }

    @Test
    fun theTurnTakeBeginsThePan() {
        list(pan { _, _ -> true })
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
            moveBy(Offset(0f, -40f))
            moveBy(Offset(30f, 0f))
            moveBy(Offset(30f, 0f))
            moveBy(Offset(30f, 0f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, begins.size)
        assertEquals(listOf(Swipe.Right), ends)
        assertTrue(swipes.isEmpty())
        assertFalse(touch.scroll.flinging)
        assertFalse(touch.scroll.dragging)
    }

    @Test
    fun aBareLiftThatTurnsBeginsThePan() {
        list(pan { _, _ -> true })
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f))
            moveBy(Offset(0f, -40f))
            moveBy(Offset(-60f, 0f))
            advanceEventTime(8)
            up()
        }
        rule.waitForIdle()
        assertEquals(1, begins.size)
        assertEquals(listOf(Swipe.Left), ends)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun aConsumedMoveEndsThePanWithNone() {
        var eat = false
        val p = pan { x, origin -> x > origin }
        rule.setContent {
            val a = remember { Arbiter(v1) }
            Box(Modifier.fillMaxSize().arbiter(a, page(pan = p))) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val e = awaitPointerEvent()
                                if (eat) e.changes.forEach { it.consume() }
                                if (e.changes.none { it.pressed }) break
                            }
                        }
                    },
                )
            }
        }
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(40f, 0f))
        }
        rule.waitForIdle()
        assertEquals(1, begins.size)
        eat = true
        rule.onRoot().performTouchInput {
            moveBy(Offset(40f, 0f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Swipe.None), ends)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun aBareLiftTakeBeginsThePanFromTheTurnNotFromTheDown() {
        list(pan { x, origin -> x > origin })
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-40f, -60f))
            moveBy(Offset(-40f, -60f))
            moveBy(Offset(50f, 0f))
            advanceEventTime(8)
            up()
        }
        rule.waitForIdle()
        assertEquals(1, begins.size)
        assertEquals(listOf(Swipe.Right), ends)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun aModalLayerKeepsThePanFromBeginning() {
        val p = pan { x, origin -> x > origin }
        rule.setContent {
            val a = remember { Arbiter(v1) }
            touch = rememberListTouch(Kind.List, v1)
            Box(Modifier.fillMaxSize().arbiter(a, page(pan = p))) {
                FuneList(a, touch, Modifier.fillMaxSize()) {
                    items(200) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
                BlockBelow(a, 1)
            }
        }
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 0f))
            up()
        }
        rule.waitForIdle()
        assertTrue(begins.isEmpty())
        assertTrue(drags.isEmpty())
        assertTrue(ends.isEmpty())
    }

    private var blocked by mutableStateOf(false)

    private fun modal(pan: Pan? = null, start: Dp = 0.dp) {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            arb = a
            Box(Modifier.fillMaxSize().padding(start = start)) {
                Box(Modifier.fillMaxSize().arbiter(a, page(pan = pan)))
                if (blocked) BlockBelow(a, 1)
            }
        }
    }

    @Test
    fun aFlingUnderAModalBlockReachesNoSwipe() {
        blocked = true
        modal()
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-40f, 0f))
            moveBy(Offset(-40f, 0f))
            up()
        }
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 0f))
            up()
        }
        rule.waitForIdle()
        assertEquals(emptyList<Swipe>(), swipes)
    }

    @Test
    fun anEdgeBackUnderAModalBlockReachesTheSwipe() {
        blocked = true
        modal()
        rule.onRoot().performTouchInput {
            down(Offset(2f, centerY))
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 0f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Swipe.Right), swipes)
    }

    @Test
    fun anOutwardSwipeFromABandUnderAModalBlockReachesNoSwipe() {
        blocked = true
        modal(start = 80.dp)
        val d = with(rule.density) { 80.dp.toPx() }
        val far = d - Arbiter(v1).slop.swipe - 10f
        rule.onRoot().performTouchInput {
            down(Offset(d + 4f, centerY))
            moveTo(Offset((d + 4f + far) / 2, centerY))
            moveTo(Offset(far, centerY))
            up()
        }
        rule.waitForIdle()
        assertEquals("premise: the down was in the left band", Gesture.Edge.Left, arb.gesture.edge)
        assertEquals(emptyList<Swipe>(), swipes)
    }

    @Test
    fun aSwipeWhoseModalBlockCameOrWentBeforeTheUpReachesNoSwipe() {
        modal()
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-40f, 0f))
            moveBy(Offset(-40f, 0f))
        }
        rule.waitForIdle()
        blocked = true
        rule.waitForIdle()
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals("a block that came before the up", emptyList<Swipe>(), swipes)
        rule.onRoot().performTouchInput {
            down(Offset(2f, centerY))
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 0f))
        }
        rule.waitForIdle()
        blocked = false
        rule.waitForIdle()
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals("a block that went before the up", emptyList<Swipe>(), swipes)
    }

    @Test
    fun aPanAModalBlockCameUnderEndsWithNoSwipe() {
        modal(pan { x, origin -> x > origin })
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 0f))
        }
        rule.waitForIdle()
        assertEquals(1, begins.size)
        blocked = true
        rule.waitForIdle()
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf(Swipe.None), ends)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun aDownOnASoloTapLeavesTheListUnderIt() {
        var solos = 0
        var soloCancels = 0
        rule.setContent {
            val a = remember { Arbiter(v1) }
            touch = rememberListTouch(Kind.List, v1)
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                FuneList(a, touch, Modifier.fillMaxSize()) {
                    items(200) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
                Box(
                    Modifier.size(80.dp).align(Alignment.BottomEnd)
                        .tapTarget(a, Tap(onPress = {}, onCancel = { soloCancels++ }, onTap = { solos++ }, solo = true)),
                )
            }
        }
        val box = with(rule.density) { 80.dp.toPx() }
        rule.onRoot().performTouchInput {
            down(Offset(width - box / 2, height - box / 4))
            repeat(3) { moveBy(Offset(0f, -60f)) }
            up()
        }
        rule.waitForIdle()
        assertEquals(0, touch.state.firstVisibleItemIndex)
        assertEquals(0, touch.state.firstVisibleItemScrollOffset)
        assertEquals(1, soloCancels)
        rule.onRoot().performTouchInput {
            down(Offset(width - box / 2, height - box / 4))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, solos)
        rule.onRoot().performTouchInput {
            down(Offset(width / 2f, height - box / 4))
            repeat(3) { moveBy(Offset(0f, -60f)) }
            up()
        }
        rule.waitForIdle()
        assertTrue(touch.state.firstVisibleItemIndex > 0 || touch.state.firstVisibleItemScrollOffset > 0)
    }

    @Test
    fun aTapGetsTheDownAndEveryPointInsideTheSlopAndNoneAfterIt() {
        val at = mutableListOf<Offset>()
        rule.setContent {
            val a = remember { Arbiter(v1) }
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap(onPress = { presses++ }, onCancel = { cancels++ }, onTap = { taps++ }, onAt = { at += it })))
            }
        }
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(2f, 3f))
            up()
        }
        rule.waitForIdle()
        assertEquals(2, at.size)
        assertEquals(at[0] + Offset(2f, 3f), at[1])
        assertEquals(1, taps)
        at.clear()
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            moveBy(Offset(0f, 10f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, at.size)
        assertEquals(1, cancels)
        assertEquals(1, taps)
    }

    @Test
    fun aGestureCutOffBeforeItsUpCancelsItsTap() {
        var a by mutableStateOf(Arbiter(v1))
        rule.setContent {
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap(onPress = { presses++ }, onCancel = { cancels++ }, onTap = { taps++ })))
            }
        }
        rule.onRoot().performTouchInput { down(center) }
        rule.waitForIdle()
        assertEquals(1, presses)
        assertEquals(0, cancels)
        rule.runOnIdle { a = Arbiter(v1) }
        rule.waitForIdle()
        assertEquals(1, cancels)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, taps)
        assertEquals(1, cancels)
    }

    @Test
    fun aGestureCutBeforeItsUpRunsItsCutAndNotItsCancel() {
        var a by mutableStateOf(Arbiter(v1))
        rule.setContent {
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                Box(Modifier.fillMaxSize().tapTarget(a, Tap(onPress = { presses++ }, onCancel = { cancels++ }, onTap = { taps++ }, onCut = { cutCount++ })))
            }
        }
        rule.onRoot().performTouchInput { down(center) }
        rule.waitForIdle()
        assertEquals(1, presses)
        rule.runOnIdle { a = Arbiter(v1) }
        rule.waitForIdle()
        assertEquals(1, cutCount)
        assertEquals(0, cancels)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, taps)
        assertEquals(1, cutCount)
        assertEquals(0, cancels)
    }

    @Test
    fun aMoveAChildConsumesCutsTheTapAndDoesNotCancelIt() {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                Box(
                    Modifier.fillMaxSize()
                        .tapTarget(a, Tap(onPress = { presses++ }, onCancel = { cancels++ }, onTap = { taps++ }, onCut = { cutCount++ }))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (e.changes.any { it.positionChanged() }) e.changes.forEach { it.consume() }
                                    if (e.changes.none { it.pressed }) break
                                }
                            }
                        },
                )
            }
        }
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(2f, 0f))
        }
        rule.waitForIdle()
        assertEquals(1, presses)
        assertEquals(1, cutCount)
        assertEquals(0, cancels)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0, taps)
        assertEquals(1, cutCount)
        assertEquals(0, cancels)
    }

    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val rowTaps = HashMap<Int, Tap>()
    private val tapped = mutableListOf<Int>()

    private fun rowTap(i: Int): Tap = rowTaps.getOrPut(i) { Tap(onPress = { presses++ }, onCancel = { cancels++ }, onTap = { tapped += i }, onCut = { cutCount++ }) }

    private fun rowList(layer: Int = 0, modal: Boolean = false) {
        rule.setContent {
            val a = remember { Arbiter(a105) }
            arb = a
            touch = rememberListTouch(Kind.List, a105, rows = RowSpec(165, 920))
            val counted = remember { booleanArrayOf(false) }
            SideEffect {
                if (!counted[0]) {
                    touch.count(30, touch.first)
                    counted[0] = true
                }
            }
            if (modal) BlockBelow(a, 1)
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                Column {
                    Box(Modifier.pxSize(720, 360))
                    FuneList(a, touch, Modifier.pxSize(720, 920), layer, rowTap = { rowTap(it) }) {
                        items(30) { Box(Modifier.pxSize(720, 165)) }
                    }
                }
            }
        }
        rule.runOnIdle {
            touch.toRow(6)
            requireNotNull(touch.rows).by(10f)
        }
        rule.waitForIdle()
        assertEquals(1000, touch.px)
    }

    private fun tapRow(y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(360f, y))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun aTapInARowListTakesTheRowUnderItByDivision() {
        rowList()
        tapRow(360f)
        tapRow(514f)
        tapRow(515f)
        tapRow(1279f)
        assertEquals(listOf(6, 6, 7, 11), tapped)
        tapRow(359f)
        assertEquals(listOf(6, 6, 7, 11), tapped)
        assertNull(arb.tapAt(Offset(360f, 359f)))
        assertNull(arb.tapAt(Offset(360f, 1280f)))
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun theOverscrollAtTheEndMovesTheRowTapsUpByIt() {
        rowList()
        rule.runOnIdle { touch.toRow(30) }
        rule.waitForIdle()
        assertEquals(4030, touch.px)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 800f))
            moveTo(Offset(360f, 700f))
            moveTo(Offset(360f, 688f))
        }
        rule.waitForIdle()
        assertEquals(4030, touch.px)
        assertEquals(12f, touch.over, 0f)
        rule.runOnIdle {
            assertSame(rowTap(24), arb.tapAt(Offset(360f, 442f)))
            assertSame(rowTap(25), arb.tapAt(Offset(360f, 443f)))
            assertSame(rowTap(29), arb.tapAt(Offset(360f, 1267f)))
        }
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun aModalLayerAboveARowListTakesNoRowTap() {
        rowList(modal = true)
        tapRow(600f)
        assertNull(arb.tapAt(Offset(360f, 600f)))
        assertTrue(tapped.isEmpty())
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun aHiddenRowListTakesNoRowTap() {
        rowList(layer = -1)
        tapRow(600f)
        assertNull(arb.tapAt(Offset(360f, 600f)))
        assertTrue(tapped.isEmpty())
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun aListCancelCutsTheRowTapUnderTheDown() {
        rowList()
        rule.onRoot().performTouchInput { down(Offset(360f, 600f)) }
        rule.waitForIdle()
        assertEquals(1, presses)
        rule.runOnIdle { touch.cancel() }
        rule.onRoot().performTouchInput { moveTo(Offset(360f, 602f)) }
        rule.waitForIdle()
        assertEquals(1, cutCount)
        assertEquals(0, cancels)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertTrue(tapped.isEmpty())
        assertEquals(1, cutCount)
        assertEquals(0, cancels)
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun aRowDownMovedPastTheSlopCancelsItsTapAndDragsTheList() {
        rowList()
        rule.onRoot().performTouchInput {
            down(Offset(360f, 600f))
            moveTo(Offset(360f, 500f))
            moveTo(Offset(360f, 450f))
        }
        rule.waitForIdle()
        assertEquals(1, presses)
        assertEquals(1, cancels)
        assertEquals(0, cutCount)
        assertEquals(1050, touch.px)
        rule.onRoot().performTouchInput { up() }
        rule.waitForIdle()
        assertTrue(tapped.isEmpty())
        assertEquals(0, cutCount)
    }

    private fun albumList() {
        rule.setContent {
            val a = remember { Arbiter(a105) }
            arb = a
            touch = rememberListTouch(Kind.List, a105, rows = RowSpec(ALBUM_PITCH, ALBUM_VIEW, ALBUM_ABOVE, collapse = true))
            val counted = remember { booleanArrayOf(false) }
            SideEffect {
                if (!counted[0]) {
                    touch.count(ALBUM_ROWS, touch.first)
                    counted[0] = true
                }
            }
            Box(Modifier.fillMaxSize().arbiter(a, page(turn = false))) {
                Column {
                    Box(Modifier.pxSize(720, ALBUM_TOP))
                    FuneList(a, touch, Modifier.pxSize(720, ALBUM_VIEW), rowTap = { rowTap(it) }) {
                        items(ALBUM_ROWS) { Box(Modifier.pxSize(720, ALBUM_PITCH)) }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun albumAt(q: Int) {
        rule.runOnIdle {
            touch.halt()
            touch.toPx(q)
        }
        rule.waitForIdle()
        assertEquals(q, touch.px)
    }

    private fun dragFrom(y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(360f, y))
            moveTo(Offset(360f, y - 100f))
            moveTo(Offset(360f, y - 150f))
            up()
        }
        rule.waitForIdle()
    }

    private fun taps(vararg ys: Float): List<Int> {
        tapped.clear()
        for (y in ys) tapRow(y)
        return tapped.toList()
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun aCollapseListTakesADownOnlyFromItsMovingRowsTop() {
        albumList()
        assertEquals(1876, requireNotNull(touch.rows).max)
        dragFrom(500f)
        assertEquals(0, touch.px)
        assertEquals(0, presses)
        assertEquals(emptyList<Int>(), taps(500f))
        assertEquals(listOf(0), taps(756f))
        albumAt(300)
        assertEquals(300, requireNotNull(touch.rows).lift)
        dragFrom(455f)
        assertEquals(300, touch.px)
        dragFrom(456f)
        assertTrue("a drag from the rows top scrolls, px ${touch.px}", touch.px > 300)
        albumAt(300)
        assertEquals(listOf(0, 0, 1), taps(456f, 575f, 576f))
        albumAt(600)
        assertEquals(345, requireNotNull(touch.rows).lift)
        assertEquals(emptyList<Int>(), taps(410f))
        assertEquals(listOf(2, 2, 3), taps(411f, 515f, 516f))
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun aDownInTheBandAboveTheMovingRowsTopLeavesAFlingRunningAndOneOnTheRowsGrabsIt() {
        albumList()
        albumAt(300)
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { touch.release(2000f) }
        rule.mainClock.advanceTimeBy(48)
        assertTrue(touch.scroll.flinging)
        val lift = requireNotNull(touch.rows).lift
        assertTrue("the fling runs back from 300, lift $lift", lift in 1 until 300)
        assertTrue("y 420 is inside the band and above the rows top ${ALBUM_TOP - lift}", 420 >= ALBUM_TOP - ALBUM_ABOVE && 420 < ALBUM_TOP - lift)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 420f))
            up()
        }
        val q = touch.px
        rule.mainClock.advanceTimeBy(16)
        assertTrue(touch.scroll.flinging)
        assertTrue("the fling moved on from $q to ${touch.px}", touch.px < q)
        val rows = ALBUM_TOP - requireNotNull(touch.rows).lift
        assertTrue("y 700 is on the rows below $rows", 700 >= rows)
        rule.onRoot().performTouchInput {
            down(Offset(360f, 700f))
            up()
        }
        rule.mainClock.advanceTimeBy(16)
        assertFalse(touch.scroll.flinging)
        assertTrue(tapped.isEmpty())
    }

    @Test
    fun onTheP2160PageTheSlopTheEdgeBandsAndTheFlingCapKeepTheFitPx() {
        val dpi = Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)
        val w = Window(1080, 2160, 34, Insets(0, 66, 0, 0), Insets(0, 0, 0, 66), Insets(82, 0, 82, 0), none, none, dpi)
        val p2160 = scale(w, Nav.Gesture, Surface.Page, false)
        assertTrue(p2160.stdPpu < 3f)
        assertEquals(4.5f, p2160.touchPpu)
        var slop = 0f
        rule.setContent {
            FuneTheme(p2160) {
                slop = LocalViewConfiguration.current.touchSlop
                touch = rememberListTouch(Kind.List, p2160)
            }
        }
        rule.waitForIdle()
        assertEquals(112.5f, slop)
        val a = Arbiter(p2160)
        assertEquals(113f, a.slop.slop)
        assertEquals(203f, a.slop.swipe)
        assertEquals(Gesture.Edge.Left, a.edgeAt(71f, 1080))
        assertEquals(Gesture.Edge.None, a.edgeAt(72f, 1080))
        assertEquals(Gesture.Edge.Right, a.edgeAt(981f, 1080))
        assertEquals(Gesture.Edge.None, a.edgeAt(980f, 1080))
        assertEquals(9000f, touch.scroll.flingVelocity(-20000f))
        assertEquals(-9000f, touch.scroll.flingVelocity(20000f))
        assertEquals(0f, touch.scroll.flingVelocity(-224f))
        assertEquals(225f, touch.scroll.flingVelocity(-225f))
    }

    private companion object {
        const val ALBUM_TOP = 756
        const val ALBUM_PITCH = 120
        const val ALBUM_VIEW = 524
        const val ALBUM_ABOVE = 345
        const val ALBUM_ROWS = 20
    }

    private fun decay(retention: Float) = 3369f / (62.5f * -ln(retention))

    @Test
    @Config(qualifiers = "440dpi")
    fun onAPhonePanelAListTakesTheAndroidCoastAtTheCompositionDensityAndQuickplayAndTheGridDoNot() {
        val dpi = Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)
        val w = Window(1080, 2160, 34, Insets(0, 66, 0, 0), Insets(0, 0, 0, 66), Insets(82, 0, 82, 0), none, none, dpi)
        val page = scale(w, Nav.Gesture, Surface.Page, false)
        val home = scale(w, Nav.Gesture, Surface.Home, false)
        assertTrue(page.coast)
        assertTrue(home.coast)
        lateinit var pageList: ListTouch
        lateinit var pageRows: ListTouch
        lateinit var homeList: ListTouch
        lateinit var quickplay: ListTouch
        lateinit var grid: ListTouch
        rule.setContent {
            FuneTheme(page) {
                pageList = rememberListTouch(Kind.List, page)
                pageRows = rememberListTouch(Kind.List, page, rows = RowSpec(163, 1804))
                homeList = rememberListTouch(Kind.List, home)
                quickplay = rememberListTouch(Kind.Quickplay, home)
                grid = rememberListTouch(Kind.Grid, page.kept())
            }
        }
        rule.waitForIdle()
        assertEquals(760.19f, pageList.scroll.travelOf(3369f), 0.01f)
        assertEquals(760.19f, pageRows.scroll.travelOf(3369f), 0.01f)
        assertEquals(760.19f, homeList.scroll.travelOf(3369f), 0.01f)
        assertEquals(decay(0.974f), quickplay.scroll.travelOf(3369f), 0.01f)
        assertEquals(decay(0.95f), grid.scroll.travelOf(3369f), 0.01f)
    }

    @Test
    @Config(qualifiers = "360dpi")
    fun onTheA105AListKeepsTheDecay() {
        val dpi = Dpi(406.4f, 406.4f, 360, 720, 1280, 1, 720, 1280)
        val w = Window(720, 1280, 28, Insets(0, 54, 0, 0), Insets(0, 0, 0, 108), none, none, none, dpi)
        val page = scale(w, Nav.Buttons, Surface.Page, false)
        assertFalse(page.coast)
        assertEquals(3f, page.touchPpu)
        lateinit var pageList: ListTouch
        lateinit var pageRows: ListTouch
        rule.setContent {
            FuneTheme(page) {
                pageList = rememberListTouch(Kind.List, page)
                pageRows = rememberListTouch(Kind.List, page, rows = RowSpec(165, 812))
            }
        }
        rule.waitForIdle()
        assertEquals(289.29f, pageList.scroll.travelOf(3369f), 0.01f)
        assertEquals(decay(0.83f), pageList.scroll.travelOf(3369f), 0.01f)
        assertEquals(decay(0.83f), pageRows.scroll.travelOf(3369f), 0.01f)
    }
}
