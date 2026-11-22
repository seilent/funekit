package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fxInt
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Row
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpAct
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.OffsetEdit
import net.seilent.funekit.screens.music.nowplaying.PhoneLyrics
import net.seilent.funekit.screens.music.nowplaying.PhoneRepeat
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class HostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val window = Window(W, H, SDK, none, none, none, none, none)
    private val page = scale(window, Nav.None, Surface.Page, false)
    private val np = scale(window, Nav.None, Surface.NowPlaying, false)
    private val hudAt = Offset(Tok.canvasW / 2 * np.ppu + (np.left - page.left), MID_U * np.ppu)

    private class Level(var pct: Int, private val cap: Int?) : VolumeLevel {
        val sets = mutableListOf<Int>()

        override fun percent(): Int = pct

        override fun stepPct(): Int = STEP

        override suspend fun step(detents: Int) {
            pct = (pct + detents * STEP).coerceIn(0, PCT)
        }

        override suspend fun set(pct: Int) {
            sets += pct
            this.pct = pct
        }

        override fun maxPct(): Int? = cap
    }

    private class SlowLevel(var pct: Int) : VolumeLevel {
        var cap: Int? = null
        var refuse = false
        val sets = mutableListOf<Int>()
        val waits = mutableListOf<CompletableDeferred<Unit>>()

        override fun percent(): Int = pct

        override fun stepPct(): Int = STEP

        override suspend fun step(detents: Int) {
            pct = (pct + detents * STEP).coerceIn(0, PCT)
        }

        override suspend fun set(pct: Int) {
            sets += pct
            check(!refuse) { "refused" }
            if (pct == this.pct) return
            val d = CompletableDeferred<Unit>()
            waits += d
            d.await()
            this.pct = pct
        }

        override fun maxPct(): Int? = cap
    }

    private class FifoLevel(var pct: Int) : VolumeLevel {
        val sets = mutableListOf<Int>()
        val waits = ArrayDeque<Pair<Int, CompletableDeferred<Unit>>>()

        override fun percent(): Int = pct

        override fun stepPct(): Int = STEP

        override suspend fun step(detents: Int) {
            pct = (pct + detents * STEP).coerceIn(0, PCT)
        }

        override suspend fun set(pct: Int) {
            sets += pct
            val d = CompletableDeferred<Unit>()
            waits += pct to d
            d.await()
        }

        fun land() {
            val (p, d) = waits.removeFirst()
            pct = p
            d.complete(Unit)
        }
    }

    private class GridLevel(var index: Int) : VolumeLevel {
        val sets = mutableListOf<Int>()
        val waits = mutableListOf<CompletableDeferred<Unit>>()

        override fun percent(): Int = (PCT * index.toFloat() / GRID).roundToInt()

        override fun stepPct(): Int = (PCT + GRID - 1) / GRID

        override suspend fun step(detents: Int) {
            index = (index + detents).coerceIn(0, GRID)
        }

        override suspend fun set(pct: Int) {
            sets += pct
            val d = CompletableDeferred<Unit>()
            waits += d
            d.await()
            index = (pct * GRID.toFloat() / PCT).roundToInt()
        }
    }

    private fun app(level: VolumeLevel) = TestApp(page, np, null, level) { rule.mainClock.currentTime }.also {
        it.compose(rule)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun drag(level: Level) {
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the hud is up", OverKind.Volume, app.stack.overlay?.kind)
        dragUp()
    }

    private fun dragUp() {
        rule.onRoot().performTouchInput {
            down(hudAt)
            repeat(STEPS) { moveBy(Offset(0f, -DRAG_PX / STEPS)) }
            up()
        }
        frames(OPEN_FRAMES)
    }

    private fun stridePx(step: Int) = np.board.vol_slideStride / step * np.ppu

    private fun dragBy(step: Int, vararg strides: Int) {
        val stride = stridePx(step)
        rule.onRoot().performTouchInput {
            down(hudAt)
            moveBy(Offset(0f, -2 * Tok.touchSlop * np.ppu))
            strides.forEach { moveBy(Offset(0f, -it * stride)) }
            up()
        }
        frames(OPEN_FRAMES)
    }

    @Test
    fun aSetThatWaitsForTheDeviceShowsItsNumberOnlyOnceItCompletesAndASetTheDeviceHoldsReturnsWithTheLevelUnchanged() {
        val level = SlowLevel(START)
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        dragUp()
        assertTrue("premise: the drag set the level", level.sets.isNotEmpty())
        assertEquals("premise: every set waits for the device", level.sets.size, level.waits.size)
        assertEquals("premise: the level holds its number", START, level.pct)
        assertEquals("the plate keeps the level's number while the sets wait", START, app.host.hud.percent)
        rule.runOnIdle { level.waits.forEach { it.complete(Unit) } }
        frames(OPEN_FRAMES)
        assertEquals("premise: the level lands on the last set", level.sets.last(), level.pct)
        assertTrue("premise: the last set moved the level", level.pct > START)
        assertEquals("the plate shows the number the level reports once its set completes", level.pct, app.host.hud.percent)
        val held = level.pct
        val waits = level.waits.size
        val sets = level.sets.size
        level.cap = held
        dragUp()
        assertTrue("premise: the second drag sets the level", level.sets.size > sets)
        assertTrue("premise: every set of the second drag holds the level's number", level.sets.drop(sets).all { it == held })
        assertEquals("a set the device holds returns with the level unchanged, nothing waits", waits, level.waits.size)
        assertEquals("the plate keeps its number", held, app.host.hud.percent)
    }

    @Test
    fun aSetThatThrowsLeavesThePlateOnTheDevicesNumber() {
        val level = SlowLevel(START)
        level.refuse = true
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        dragUp()
        assertTrue("premise: the drag set the level", level.sets.isNotEmpty())
        assertTrue("premise: every set asked past the device's number", level.sets.all { it > START })
        assertEquals("premise: the device keeps its number", START, level.pct)
        assertEquals("the plate shows the device's number", START, app.host.hud.percent)
    }

    @Test
    fun twoSetsInFlightThatTheDeviceAppliesInSendOrderLeaveThePlateOnTheLevel() {
        val level = FifoLevel(START)
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        dragBy(STEP, 1, -1)
        assertEquals("premise: the drag sends one stride up, then one back", listOf(START + 1, START), level.sets)
        assertEquals("premise: both sets wait for the device", level.sets.size, level.waits.size)
        assertEquals("premise: the level holds its number", START, level.pct)
        assertEquals("premise: the plate keeps the level's number while the sets wait", START, app.host.hud.percent)
        rule.runOnIdle { level.land() }
        frames(OPEN_FRAMES)
        assertEquals("premise: the first set lands", START + 1, level.pct)
        assertEquals("premise: the plate shows the first set once it lands", START + 1, app.host.hud.percent)
        val t = rule.mainClock.currentTime
        assertEquals("premise: the hud is up", OverKind.Volume, app.stack.overlay?.kind)
        assertTrue("premise: the hud is up and not closing", app.host.hud.visible(t) && !app.host.hud.closing(t))
        rule.runOnIdle { level.land() }
        frames(OPEN_FRAMES)
        assertEquals("premise: the level lands on the last set", START, level.pct)
        assertEquals("the plate shows the level once the last set lands", level.pct, app.host.hud.percent)
    }

    @Test
    fun aDragFollowsTheFingerWhenTheLevelTakesOneOfItsSetsBeforeThatSetReturnsAndReanchorsOnALevelItDidNotSend() {
        val level = FifoLevel(START)
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        val stride = stridePx(STEP)
        rule.onRoot().performTouchInput {
            down(hudAt)
            moveBy(Offset(0f, -2 * Tok.touchSlop * np.ppu))
            moveBy(Offset(0f, -stride))
            moveBy(Offset(0f, -stride))
        }
        frames(OPEN_FRAMES)
        assertEquals("premise: two strides up send two sets", listOf(START + 1, START + 2), level.sets)
        assertEquals("premise: both sets wait for the device", 2, level.waits.size)
        rule.runOnIdle { level.pct = level.waits.first().first }
        assertEquals("premise: the level takes the first set with its waiter open", START + 1, level.percent())
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -stride)) }
        frames(OPEN_FRAMES)
        assertEquals("the next set is the finger's number, sets ${level.sets}", listOf(START + 3), level.sets.drop(2))
        rule.runOnIdle { level.pct = START - STEP }
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -stride)) }
        frames(OPEN_FRAMES)
        assertEquals("a level the drag did not send anchors the drag there and sends nothing, sets ${level.sets}", listOf(START + 3), level.sets.drop(2))
        rule.onRoot().performTouchInput { moveBy(Offset(0f, -stride)) }
        frames(OPEN_FRAMES)
        assertEquals("the drag goes on from that level anchored at the previous move's y, two strides back, sets ${level.sets}", listOf(START + 3, START - STEP + 2), level.sets.drop(2))
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
    }

    @Test
    fun aLevelOnACoarseGridShowsNothingForASetThatHoldsItsStepAndItsOwnPercentForASetThatMovesIt() {
        val level = GridLevel(GRID_AT)
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        val held = level.percent()
        dragBy(level.stepPct(), 1)
        assertEquals("premise: the drag sends one stride up", listOf(held + 1), level.sets)
        assertEquals("premise: the set waits for the device", 1, level.waits.size)
        assertEquals("premise: the plate shows the level", held, app.host.hud.percent)
        val rev = app.host.hud.revision
        rule.runOnIdle { level.waits.last().complete(Unit) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the set holds the grid step", held, level.percent())
        assertEquals("a set that holds the grid step leaves the plate's number", held, app.host.hud.percent)
        assertEquals("a set that holds the grid step shows nothing", rev, app.host.hud.revision)
        dragBy(level.stepPct(), GRID_STRIDES)
        val n = level.sets.last()
        assertEquals("premise: the second drag sends one set", 2, level.sets.size)
        rule.runOnIdle { level.waits.last().complete(Unit) }
        frames(OPEN_FRAMES)
        assertTrue("premise: the set moves the grid step, ${level.percent()} from $held", level.percent() > held)
        assertTrue("premise: the dragged number $n is off the grid", n != level.percent())
        assertEquals("the plate shows the grid percent, never the dragged $n", level.percent(), app.host.hud.percent)
    }

    @Test
    fun aDragOnTheHudPassesTheCapPercentWhenTheLevelHasNoCap() {
        val level = Level(START, null)
        drag(level)
        assertTrue("premise: the drag raises the level past $CAP, at ${level.pct}", level.pct > CAP)
    }

    @Test
    fun aCappedLevelStopsTheHudDragAtItsCap() {
        val level = Level(START, CAP)
        drag(level)
        assertTrue("premise: the drag set the level", level.sets.isNotEmpty())
        assertTrue("no set passes the cap: ${level.sets}", level.sets.all { it <= CAP })
        assertEquals(CAP, level.pct)
    }

    private fun shot(): Bitmap {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
        return b
    }

    private fun white(c: Int): Boolean = Color.red(c) >= WHITE && Color.green(c) >= WHITE && Color.blue(c) >= WHITE

    private fun whiteRows(b: Bitmap): List<Int> {
        val pix = IntArray(b.width * b.height)
        b.getPixels(pix, 0, b.width, 0, 0, b.width, b.height)
        return (0 until b.height).filter { y -> (0 until b.width).any { x -> white(pix[y * b.width + x]) } }
    }

    private fun plusTop(s: Scale, other: Scale): Int {
        val b = shot()
        val cx = px(s.w, s.ppu) / 2 + (s.left - page.left)
        val end = px(minOf(s.board.vol_plusY + s.board.vol_ringY, other.board.vol_plusY + other.board.vol_ringY) / 2, s.ppu)
        return (0 until end).firstOrNull { y -> (cx - COL..cx + COL).any { x -> Color.red(b.getPixel(x, y)) >= WHITE } } ?: -1
    }

    @Test
    fun anNpScaleChangeUnderTheHudDrawsItOnTheNewBoardAtOnce() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
        val shift = px(shorter.board.vol_plusY, shorter.ppu) - px(np.board.vol_plusY, np.ppu)
        assertTrue("premise: both scales draw at one ppu, ${np.ppu} and ${shorter.ppu}", np.ppu == shorter.ppu)
        assertTrue("premise: the plus moves with the board, $shift px", abs(shift) >= MIN_SHIFT)
        val app = app(Level(START, null))
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        val before = plusTop(np, shorter)
        assertTrue("premise: the plus is drawn, top $before", before >= 0)
        val rev = app.host.hud.revision
        rule.runOnIdle { app.np = shorter }
        frames(RESIZE_FRAMES)
        assertEquals("premise: the scale change changes nothing in the hud", rev, app.host.hud.revision)
        val after = plusTop(np, shorter)
        assertTrue("the plus moves with the new board at once, from $before by ${after - before} where $shift", abs(after - before - shift) <= 1)
    }

    @Test
    fun aToastShownWhileAToastHoldsAfterAPageScaleChangeSitsOnTheNewSurface() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
        val strip = px(Tok.toastHeight, page.ppu)
        val app = app(Level(START, null))
        rule.runOnIdle { app.toast.show(rule.mainClock.currentTime, "line", "title") }
        frames(TOAST_FRAMES)
        val first = whiteRows(shot())
        assertTrue("premise: the toast draws on the page bottom, rows $first", first.isNotEmpty() && first.all { it in page.heightPx - strip until page.heightPx })
        rule.runOnIdle { app.page = shorter }
        frames(RESIZE_FRAMES)
        rule.runOnIdle {
            assertTrue("premise: the first toast still holds", app.toast.visible(rule.mainClock.currentTime))
            app.toast.show(rule.mainClock.currentTime, "next", "title")
        }
        frames(TOAST_FRAMES)
        val rows = whiteRows(shot())
        assertTrue("the next toast sits on the new page bottom ${shorter.heightPx}, rows $rows", rows.isNotEmpty() && rows.all { it in shorter.heightPx - strip until shorter.heightPx })
    }

    @Test
    fun aTurnAfterAPageScaleChangeTakesItsThumbFromTheNewDrawMapsAndLeavesTheOldOnes() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
        val app = app(Level(START, null))
        val first = app.host.types
        rule.runOnIdle { app.page = shorter }
        frames(RESIZE_FRAMES)
        val types = app.host.types
        assertTrue("premise: the scale change makes new draw maps", types !== first)
        val e = TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM)))
        rule.runOnIdle { app.stack.push(e, MoveKind.T2) }
        frames(OPEN_FRAMES)
        val m = app.models[e.entry] as? AlbumModel
        assertNotNull("premise: the push enters the album", m)
        fun album(t: MusicTypes) = t.all.filterIsInstance<AlbumType>().single()
        assertSame("premise: the album is drawn on the new scale", shorter, album(types).drawOf(m!!).s)
        val was = first.env
        first.env = MusicEnv(np, was.np, was.measurer, was.fonts, was.density, was.covers, was.bands, was.accent, was.accentOff, was.a, was.now, was.poke, was.grid)
        assertSame("the turn's thumb built no draw in the old maps, so the one there is built now on the scale set after the turn", np, album(first).drawOf(m).s)
    }

    @Test
    fun aPhoneSessionThatStartsAfterTheFirstCompositionShowsAsTheVariant() {
        val app = app(Level(START, null))
        frames(OPEN_FRAMES)
        assertFalse("premise: no session, no variant", app.music.np.variant)
        val track = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null)
        rule.runOnIdle { app.phone.value = PhoneSession(track, {}) {} }
        frames(OPEN_FRAMES)
        rule.runOnIdle { assertTrue("the late session shows as the variant", app.music.np.variant) }
    }

    @Test
    fun aBlankAppGivesTheTrackToastAndTheHudAnEmptyLead() {
        val app = app(Level(START, null))
        frames(OPEN_FRAMES)
        val first = PhoneTrack("", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null)
        rule.runOnIdle { app.phone.value = PhoneSession(first, {}) {} }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.phone.value = PhoneSession(first.copy(title = "Next"), {}) {} }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            assertTrue("premise: the session shows as the variant", app.music.np.variant)
            assertEquals("premise: the new track toasts", "Next", app.toast.title)
            assertEquals("the track toast leads with an empty line", "", app.toast.artist)
        }
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            assertEquals("premise: the hud names the track", "Next", app.host.hud.title)
            assertEquals("the hud's lead line is empty", "", app.host.hud.artist)
        }
    }

    @Test
    fun aResetUnderNowPlayingRefusesUnderPagesThatHoldNowPlaying() {
        val app = app(Level(START, null))
        val kept = TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.NowPlaying))
        val refused = rule.runOnIdle { runCatching { app.host.resetNp(listOf(kept)) }.exceptionOrNull() }
        assertTrue("the reset refuses, $refused", refused is IllegalArgumentException)
    }

    @Test
    fun aResetUnderNowPlayingLandsTheAppPagesUnderANewNowPlayingWithNoTurnAndSetsItUp() {
        val app = app(Level(START, null))
        val hub = TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Hub))
        val album = TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM)))
        rule.runOnIdle { app.stack.reset(listOf(hub, album)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the hub and the album are up", listOf(hub, album), app.stack.pages.toList())
        val np = app.music.np
        rule.runOnIdle {
            np.slide.start = STALE
            np.slide.target = STALE + 1
        }
        val pokes = app.pokes
        val wake = np.idleWake()
        val at = rule.runOnIdle { rule.mainClock.currentTime.also { app.host.resetNp(listOf(hub)) } }
        assertTrue("premise: the focus was last reset before the reset, wake $wake at $at", wake < at + NowPlayingModel.IDLE)
        frames(OPEN_FRAMES)
        val pages = app.stack.pages.toList()
        assertEquals(2, pages.size)
        assertEquals(hub, pages[0])
        assertTrue("now playing is on top", app.host.isNp(pages[1]))
        assertNull("no turn", app.stack.turn)
        assertEquals(Phase.Rest, app.stack.phase)
        assertNull("the album's model is dropped", app.models[album.entry])
        assertNotNull("the new now playing has its model", app.models[pages[1].entry])
        assertEquals("the focus is reset at the reset", at + NowPlayingModel.IDLE, np.idleWake())
        assertNull("a stale slide is cleared", np.slide.target)
        assertTrue("the reset pokes the page", app.pokes > pokes)
    }

    private fun tap(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
    }

    @Test
    fun twoMenuOpensBeforeAFrameDrawTheMenuOfTheOverlayTheStackOpened() {
        val app = app(Level(START, null))
        rule.runOnIdle {
            app.host.openMenu(MenuForm.Sheet, "first", listOf("one", "two"), false, -1, 0, false, { true })
            app.host.openMenu(MenuForm.Sheet, "second", listOf("one", "two"), false, -1, 0, false, { true })
        }
        frames(OPEN_FRAMES)
        val o = app.stack.overlay
        assertTrue("premise: a sheet is open, $o", o != null && o.kind == OverKind.Sheet && !o.closing)
        tap(page.widthPx / 2f, TOP_Y)
        frames(OPEN_FRAMES)
        assertTrue("the open sheet has its kit menu, so the scrim tap closes it", app.stack.overlay.let { it == null || it.closing })
    }

    @Test
    fun anAppSheetOverlayGetsNoKitMenuAndAHostMenuSheetDoes() {
        val app = app(Level(START, null))
        val own = Overlay(OverKind.Sheet)
        rule.runOnIdle { app.stack.open(own) }
        frames(OPEN_FRAMES)
        assertSame("premise: the app's sheet is open", own, app.stack.overlay)
        tap(page.widthPx / 2f, TOP_Y)
        frames(OPEN_FRAMES)
        assertSame("a tap over the app's sheet meets no kit menu, the sheet stays", own, app.stack.overlay)
        assertFalse("the app's sheet is not closing", own.closing)
        rule.runOnIdle { app.stack.cut() }
        frames(OPEN_FRAMES)
        assertNull("premise: the app's sheet is cut", app.stack.overlay)
        rule.runOnIdle { app.host.openMenu(MenuForm.Sheet, "title", listOf("one", "two"), false, -1, 0, false, { true }) }
        frames(OPEN_FRAMES)
        val menu = app.stack.overlay
        assertTrue("premise: the host's sheet is open, $menu", menu != null && menu !== own && menu.kind == OverKind.Sheet)
        tap(page.widthPx / 2f, TOP_Y)
        frames(OPEN_FRAMES)
        assertTrue("premise: the same tap closes the host's sheet", app.stack.overlay.let { it == null || it.closing })
    }

    @Test
    fun aRecompositionWithEqualEnvInputsWritesNoNewEnv() {
        val app = app(Level(START, null))
        frames(OPEN_FRAMES)
        val envs = mutableListOf<MusicEnv>()
        val watch = Snapshot.registerApplyObserver { changed, _ -> changed.forEach { (it as? State<*>)?.value?.let { v -> if (v is MusicEnv) envs += v } } }
        try {
            val track = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null)
            rule.runOnIdle { app.phone.value = PhoneSession(track, {}) {} }
            frames(OPEN_FRAMES)
            rule.runOnIdle { assertTrue("premise: the phone session recomposed the host", app.music.np.variant) }
            assertTrue("the recomposition writes no env, ${envs.size}", envs.isEmpty())
            val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
            rule.runOnIdle { app.page = shorter }
            frames(RESIZE_FRAMES)
            assertTrue("premise: a page change writes the new env", envs.any { it.page === shorter })
        } finally {
            watch.dispose()
        }
    }

    private class Edit : OffsetEdit {
        val calls = mutableListOf<String>()
        override var shownMs = 0
        override var editing = false

        override fun begin() {
            calls += "begin"
            editing = true
        }

        override fun step(d: Int) {
            calls += "step $d"
        }

        override fun coarse(d: Int) {
            calls += "coarse $d"
        }

        override fun keep() {
            calls += "keep"
            editing = false
        }

        override fun restore() {
            calls += "restore"
            editing = false
        }

        override fun entry(adjust: Boolean) {
            calls += "entry $adjust"
        }

        override fun end() {
            calls += "end"
        }
    }

    private fun song(id: Long) = Row(id, Kind.Song, "Song", "Artist", null, durationMs = DURATION, album = "Album")

    private fun lyricsApp(e: Edit, view: Boolean = true, mode: Boolean = true, pl: PhoneLyrics? = null): TestApp =
        TestApp(page, np, null, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.lyricsOn = view
            it.edit = e
            it.phoneLyricsOf = pl
            it.compose(rule)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.runOnIdle {
                val m = it.music.np
                m.sync(NpShown.of(listOf(song(1))), 0)
                m.lyrics.begin(1)
                m.lyrics.load(1, "[00:01.00]one\n[00:02.00]two", 0)
                if (mode) {
                    m.lyrics.toggle(0)
                    m.lyrics.land()
                }
                it.host.resetNp(listOf(TestScreen.Music(MusicEntry(it.stack.entry(), MusicPage.Hub))))
            }
            frames(OPEN_FRAMES)
        }

    @Test
    fun aLyricsSheetRowTapOpensTheAdjustOverlayOnlyOnceTheSheetHasSettled() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        val sheet = app.host.offsetSheet!!
        rule.runOnIdle { app.menuHost.act(0) }
        frames(1)
        assertTrue(e.editing)
        assertFalse(sheet.hud.shown)
        frames(OPEN_FRAMES)
        assertTrue(sheet.hud.shown)
        assertTrue(rule.runOnIdle { app.host.offsetKey(1) })
        assertEquals(listOf("begin", "coarse 1"), e.calls)
    }

    @Test
    fun aRowHoldClosesTheSheetForTheEntryAndTheReturnReopensItWithTheOverlay() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        assertNull(app.stack.overlay)
        assertNotNull(app.host.offsetSheet)
        rule.runOnIdle { app.host.offsetReturn(true) }
        frames(3)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertTrue(app.host.offsetSheet!!.hud.shown)
        assertEquals(listOf("entry false", "begin"), e.calls)
    }

    @Test
    fun aNumberHoldKeepsTheEditAndClosesTheSheetForTheEntry() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(OPEN_FRAMES)
        assertTrue(app.host.offsetSheet!!.hud.shown)
        shot()
        val x = px(Tok.edge, page.stdPpu) + 2f
        val y = px(page.board.overlayRingY, page.stdPpu) - 2f
        rule.onRoot().performTouchInput { down(Offset(x, y)) }
        var n = 0
        while (app.host.offsetSheet!!.hud.shown && n < OPEN_FRAMES) {
            frames(1)
            n++
        }
        assertFalse(app.host.offsetSheet!!.hud.shown)
        frames(CUT_FRAMES)
        assertNull(app.stack.overlay)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertNull(app.stack.overlay)
        assertNotNull(app.host.offsetSheet)
        assertEquals(listOf("begin", "keep", "entry true"), e.calls)
    }

    @Test
    fun aHoldOffTheNumberTakesNoEntry() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(OPEN_FRAMES)
        shot()
        rule.onRoot().performTouchInput { down(Offset(W - 2f, H / 2f)) }
        frames(OPEN_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(CUT_FRAMES)
        assertTrue(app.host.offsetSheet!!.hud.shown)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(listOf("begin"), e.calls)
    }

    @Test
    fun aReturnOffNowPlayingEndsTheEditInsteadOfReopening() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.host.offsetReturn(false) }
        frames(CUT_FRAMES)
        assertNull(app.stack.overlay)
        assertNull(app.host.offsetSheet)
        assertEquals(listOf("entry false", "end"), e.calls)
    }

    @Test
    fun aSheetOpenedOverAHeldOneEndsTheHeldEdit() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        val held = app.host.offsetSheet
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertNotSame(held, app.host.offsetSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(listOf("entry false", "end"), e.calls)
    }

    @Test
    fun aReturnForAReplacedSheetReopensNothing() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        rule.runOnIdle {
            app.host.offsetReturn(false)
            app.host.openOffset()
        }
        val next = app.host.offsetSheet
        frames(OPEN_FRAMES)
        assertNotNull(next)
        assertSame(next, app.host.offsetSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(listOf("entry false", "end"), e.calls)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(OPEN_FRAMES)
        assertNull(app.stack.overlay)
        assertSame(next, app.host.offsetSheet)
        assertEquals(listOf("entry false", "end", "entry false"), e.calls)
    }

    @Test
    fun turningTheLyricsViewOffClosesTheLyricsSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertNotNull(app.host.offsetSheet)
        rule.runOnIdle { app.lyricsOn = false }
        frames(OPEN_FRAMES)
        assertNull(app.host.offsetSheet)
        assertNull(app.stack.overlay)
        assertEquals(listOf("end"), e.calls)
    }

    @Test
    fun aReturnInTheFrameOfTheEntrysPopReopensTheSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            app.stack.back(app.host.popKind)
            app.host.offsetReturn(false)
        }
        frames(2 * OPEN_FRAMES)
        assertTrue(app.host.isNp(app.stack.top!!))
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertNotNull(app.host.offsetSheet)
        assertEquals(listOf("entry false"), e.calls)
    }

    @Test
    fun twoSheetOpensInOneFrameOpenOneSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle {
            app.host.openOffset()
            app.host.openOffset()
        }
        val sheet = app.host.offsetSheet
        frames(OPEN_FRAMES)
        assertNotNull(sheet)
        assertSame(sheet, app.host.offsetSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(emptyList<String>(), e.calls)
    }

    @Test
    fun aSheetOverAHeldOneEndsTheHeldEditBeforeTheAppIsAsked() {
        val e = Edit()
        val app = lyricsApp(e)
        app.asked = { e.calls += "asked" }
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertEquals(listOf("asked", "entry false", "end", "asked"), e.calls)
    }

    @Test
    fun aNewTrackClosesTheLyricsSheetAndDropsTheEdit() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(2)
        rule.runOnIdle { app.music.np.sync(NpShown.of(listOf(song(2))), rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertNull(app.host.offsetSheet)
        assertNull(app.stack.overlay)
        assertEquals(listOf("begin", "restore", "end"), e.calls)
    }

    @Test
    fun lyricsReadForAnotherTrackCloseTheLyricsSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.music.np.lyrics.begin(2) }
        frames(OPEN_FRAMES)
        assertNull(app.host.offsetSheet)
        assertEquals(listOf("end"), e.calls)
    }

    @Test
    fun turningTheLyricsOffClosesTheLyricsSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.host.showLyrics(false) }
        frames(OPEN_FRAMES)
        assertNull(app.host.offsetSheet)
        assertEquals(listOf("end"), e.calls)
    }

    @Test
    fun lyricsWithNoTimedLinesCloseTheLyricsSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            app.music.np.lyrics.begin(1)
            app.music.np.lyrics.load(1, "one\ntwo", 0)
        }
        frames(OPEN_FRAMES)
        assertNull(app.host.offsetSheet)
        assertEquals(listOf("end"), e.calls)
    }

    @Test
    fun aReReadOfTheSameTrackKeepsTheLyricsSheet() {
        val app = lyricsApp(Edit())
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        val sheet = app.host.offsetSheet
        rule.runOnIdle { app.music.np.lyrics.begin(1) }
        frames(OPEN_FRAMES)
        assertSame(sheet, app.host.offsetSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun duringTheTurnBackToNowPlayingTheLyricsSheetStaysShut() {
        val app = lyricsApp(Edit())
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        var n = 0
        while (app.stack.phase != Phase.Enter && n < OPEN_FRAMES) {
            frames(1)
            n++
        }
        assertEquals(Phase.Enter, app.stack.phase)
        assertTrue(app.host.isNp(app.stack.top!!))
        rule.runOnIdle { app.host.openOffset() }
        assertNull(app.host.offsetSheet)
    }

    private class Copy(private val off: OffsetEdit? = null) : PhoneLyrics {
        override val mode = MutableStateFlow(false)
        override val revision = MutableStateFlow(0L)
        val sets = mutableListOf<Boolean>()
        var refuses = false
        var reads = 0
        var offsets = 0

        override fun setMode(on: Boolean) {
            sets += on
            if (!refuses) mode.value = on
        }

        override suspend fun lyrics(t: PhoneTrack): String? {
            reads++
            return LRC
        }

        override fun offset(t: PhoneTrack): OffsetEdit? {
            offsets++
            return off
        }
    }

    private val phoneTrack = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null)

    private fun session(app: TestApp, track: PhoneTrack? = phoneTrack) {
        rule.runOnIdle { app.phone.value = track?.let { PhoneSession(it, {}) {} } }
        frames(OPEN_FRAMES)
    }

    private fun phoneApp(pl: Copy, e: Edit = Edit()): TestApp = lyricsApp(e, mode = false, pl = pl).also { session(it) }

    private fun shade(app: TestApp): Long = rule.runOnIdle { app.music.np.lyrics.dim(rule.mainClock.currentTime) }

    @Test
    fun aPhoneCopyShowsInTheLyricsViewAndTheKeyFlipsThePhoneMode() {
        val pl = Copy()
        val app = phoneApp(pl)
        val m = app.music.np
        rule.runOnIdle {
            assertTrue("premise: the session shows as the variant", m.variant)
            assertTrue("the mark shows the phone copy", m.mark)
            assertEquals("the copy is read for the shown id", m.shown.track, m.lyrics.track)
            assertEquals(listOf(1000L, 2000L), m.lyrics.lines.map { it.ms })
        }
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            assertEquals("the key calls the app's mode", listOf(true), pl.sets)
            assertTrue(m.lyrics.phoneMode)
            assertFalse("the own mode stays", m.lyrics.mode)
            assertTrue(m.lyricsShown())
        }
    }

    @Test
    fun aPhoneLyricsReadKeysOnTheShownIdNotThePosition() {
        val pl = Copy()
        val app = phoneApp(pl)
        val reads = pl.reads
        assertTrue("premise: the copy was read", reads > 0)
        session(app, phoneTrack.copy(positionMs = POSITION, positionAt = POSITION))
        assertEquals("a state that moves only the position reads nothing", reads, pl.reads)
        rule.runOnIdle { pl.revision.value = 1L }
        frames(OPEN_FRAMES)
        assertEquals("a revision change reads again", reads + 1, pl.reads)
    }

    @Test
    fun theLyricsTapOnAPhoneCopyOpensItsOffsetEdit() {
        val e = Edit()
        val pl = Copy(e)
        val app = phoneApp(pl)
        var asked = 0
        app.asked = { asked++ }
        rule.runOnIdle { pl.mode.value = true }
        frames(OPEN_FRAMES)
        assertTrue("premise: the phone copy shows", rule.runOnIdle { app.music.np.lyricsShown() })
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the phone copy's edit is asked", 1, pl.offsets)
        assertEquals("the own edit is not asked", 0, asked)
    }

    @Test
    fun aSourceSwitchLandsTheShadeOnTheNewSourcesMode() {
        val pl = Copy()
        val app = lyricsApp(Edit(), pl = pl)
        assertEquals("premise: the own mode shows", fxInt(1), shade(app))
        session(app)
        assertEquals("the phone mode is off, the shade lands off", 0L, shade(app))
        session(app, null)
        assertEquals("back on the own source the shade lands on", fxInt(1), shade(app))
        rule.runOnIdle {
            app.host.showLyrics(false)
            pl.mode.value = true
        }
        frames(FADE_FRAMES)
        assertEquals("premise: the own mode is off", 0L, shade(app))
        session(app)
        assertEquals("the phone mode is on, the shade lands on", fxInt(1), shade(app))
        session(app, null)
        assertEquals("back on the own source the shade lands off", 0L, shade(app))
    }

    @Test
    fun showLyricsDuringTheVariantSetsTheOwnModeOnly() {
        val pl = Copy()
        val app = phoneApp(pl)
        val m = app.music.np
        val before = shade(app)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            assertTrue("the own mode is set", m.lyrics.mode)
            assertFalse("the phone mode stays", m.lyrics.phoneMode)
            assertTrue("no mode call reaches the app", pl.sets.isEmpty())
            assertFalse(m.lyrics.fading(rule.mainClock.currentTime))
        }
        assertEquals("the shade does not move", before, shade(app))
    }

    @Test
    fun theModeEchoOfAToggleKeepsItsFade() {
        val pl = Copy()
        val app = phoneApp(pl)
        val l = app.music.np.lyrics
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(2)
        rule.runOnIdle {
            assertEquals(listOf(true), pl.sets)
            assertTrue("premise: the app took the mode", pl.mode.value)
            assertTrue("the echo keeps the fade", l.fading(rule.mainClock.currentTime))
            assertTrue(l.dimming(rule.mainClock.currentTime))
        }
    }

    @Test
    fun anAppThatRefusesTheModeLandsTheShadeBack() {
        val pl = Copy().also { it.refuses = true }
        val app = phoneApp(pl)
        val l = app.music.np.lyrics
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(2)
        rule.runOnIdle {
            assertEquals(listOf(true), pl.sets)
            assertFalse("the refused mode lands back", l.phoneMode)
            assertFalse(l.fading(rule.mainClock.currentTime))
        }
        assertEquals(0L, shade(app))
    }

    @Test
    fun aSessionThatGainsShuffleTakesRingOne() {
        val app = app(Level(START, null))
        frames(OPEN_FRAMES)
        session(app)
        val m = app.music.np
        val g = NpLayout(np)
        assertEquals("premise: a session with no shuffle act", -1, rule.runOnIdle { m.ringAt(g, RING_ONE_X, RING_Y) })
        rule.runOnIdle { app.phone.value = PhoneSession(phoneTrack, {}, { }) {} }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { m.phoneShuffle })
        assertEquals("the same track with a shuffle act takes ring one", 1, rule.runOnIdle { m.ringAt(g, RING_ONE_X, RING_Y) })
    }

    @Test
    fun withPhoneLyricsShownTheBackdropIsTheAppPictureWithTheShade() {
        val pl = Copy().also { it.mode.value = true }
        val app = lyricsApp(Edit(), mode = false, pl = pl)
        val pic = NpPicture(ImageBitmap(4, 4))
        val art = Bitmap.createBitmap(COVER, COVER, Bitmap.Config.ARGB_8888)
        rule.runOnIdle {
            app.music.np.workContext = Dispatchers.Unconfined
            app.backdropFlow.value = pic
        }
        session(app, phoneTrack.copy(art = art))
        frames(BLUR_FRAMES)
        rule.runOnIdle {
            assertTrue("premise: the phone copy shows", app.music.np.lyricsShown())
            assertTrue("premise: the art is the phone's", app.music.np.artPhone)
        }
        assertSame("the phone art stays out of the blur", pic, app.host.npBackdrop.shown)
        assertEquals("the shade is on", fxInt(1), shade(app))
    }

    @Test
    fun aPhoneSessionKeepsTheLyricsSheetShut() {
        val app = lyricsApp(Edit())
        val track = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null)
        rule.runOnIdle {
            app.music.np.feed(track)
            app.host.openOffset()
        }
        assertNull(app.host.offsetSheet)
    }

    @Test
    fun plainLyricsOpenNoLyricsSheet() {
        val app = lyricsApp(Edit())
        rule.runOnIdle {
            app.music.np.lyrics.begin(1)
            app.music.np.lyrics.load(1, "one\ntwo", 0)
            app.host.openOffset()
        }
        assertNull(app.host.offsetSheet)
    }

    @Test
    fun anAppWithNoEditForTheTrackOpensNoSheet() {
        val app = lyricsApp(Edit())
        app.edit = null
        rule.runOnIdle { app.host.openOffset() }
        frames(CUT_FRAMES)
        assertNull(app.stack.overlay)
        assertNull(app.host.offsetSheet)
    }

    @Test
    fun withNoLyricsViewTheMarkTapLeavesTheLyricsMode() {
        val app = lyricsApp(Edit(), view = false, mode = false)
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(CUT_FRAMES)
        assertFalse(app.music.np.lyrics.mode)
    }

    @Test
    fun aBackOnAPageOverNowPlayingPopsItAndLeavesTheLyrics() {
        val app = lyricsApp(Edit())
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(OPEN_FRAMES)
        assertFalse(app.host.isNp(app.stack.top!!))
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertTrue(app.music.np.lyrics.mode)
        assertTrue(app.host.isNp(app.stack.top!!))
    }

    @Test
    fun aSheetOpenedDuringTheLyricsFadeLandsTheFade() {
        val app = lyricsApp(Edit(), mode = false)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(2)
        rule.runOnIdle { app.host.openOffset() }
        assertFalse(app.music.np.lyrics.fading(rule.mainClock.currentTime))
        frames(2)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun aToggleDuringTheLyricsFadeRunsItBack() {
        val app = lyricsApp(Edit(), mode = false)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(3)
        val l = app.music.np.lyrics
        val at = rule.mainClock.currentTime
        val shown = l.fade(at)
        assertNotNull(shown)
        rule.runOnIdle { app.host.showLyrics(false) }
        assertFalse(l.mode)
        assertEquals(shown, l.fade(at))
    }

    @Test
    fun backDuringTheLyricsFadeInRunsItBack() {
        val app = lyricsApp(Edit(), mode = false)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(3)
        val l = app.music.np.lyrics
        val at = rule.mainClock.currentTime
        val shown = l.fade(at)
        assertTrue(rule.runOnIdle { app.host.npBack() })
        assertFalse(l.mode)
        assertEquals(shown, l.fade(at))
    }

    @Test
    fun offNowPlayingTheLyricsSheetNeverOpens() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        assertNull(app.stack.overlay)
        assertNull(app.host.offsetSheet)
    }

    @Test
    fun systemBackOnNowPlayingInLyricsModeLeavesTheLyricsFirst() {
        val app = lyricsApp(Edit())
        assertTrue(app.host.isNp(app.stack.top!!))
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertFalse(app.music.np.lyrics.mode)
        assertTrue(app.host.isNp(app.stack.top!!))
    }

    @Test
    fun backInTheOverlayDropsTheEditAndRaisesTheSheetAgain() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(OPEN_FRAMES)
        val sheet = app.host.offsetSheet!!
        assertTrue(sheet.hud.shown)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertFalse(sheet.hud.shown)
        assertSame(sheet, app.host.offsetSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(listOf("begin", "restore"), e.calls)
    }

    @Test
    fun exitInTheOverlayKeepsTheEditAndRaisesTheSheetAgain() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(OPEN_FRAMES)
        val sheet = app.host.offsetSheet!!
        assertTrue(sheet.hud.shown)
        tap(2f, 2f)
        frames(2)
        assertFalse(sheet.hud.shown)
        assertSame(sheet, app.host.offsetSheet)
        assertTrue(app.stack.overlay!!.moving(rule.mainClock.currentTime))
        assertEquals(listOf("begin", "keep"), e.calls)
    }

    @Test
    fun closingTheLyricsSheetEndsTheEdit() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        assertNull(app.host.offsetSheet)
        assertEquals(listOf("end"), e.calls)
    }

    @Test
    fun withNoLyricsViewTheLyricsSheetNeverOpens() {
        val e = Edit()
        val app = lyricsApp(e, view = false)
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        assertNull(app.stack.overlay)
        assertNull(app.host.offsetSheet)
    }

    @Test
    fun theBackdropShowsTheAppPictureAndGivesWayInLyricsMode() {
        val app = lyricsApp(Edit(), mode = false)
        val pic = NpPicture(ImageBitmap(4, 4))
        rule.runOnIdle {
            app.music.np.workContext = Dispatchers.Unconfined
            app.music.np.art = ImageBitmap(COVER, COVER)
            app.backdropFlow.value = pic
        }
        frames(2)
        assertSame(pic, app.host.npBackdrop.shown)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(BLUR_FRAMES)
        val shown = app.host.npBackdrop.shown
        assertNotSame(pic, shown)
        assertTrue(shown!!.whole)
    }

    @Test
    fun aCoverStillBlurringKeepsTheShownBackdrop() {
        val app = lyricsApp(Edit(), mode = false)
        rule.runOnIdle {
            app.music.np.workContext = Dispatchers.Unconfined
            app.music.np.art = ImageBitmap(COVER, COVER)
            app.backdropFlow.value = NpPicture(ImageBitmap(4, 4))
        }
        frames(2)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(BLUR_FRAMES)
        val first = app.host.npBackdrop.shown
        assertTrue(first!!.whole)
        rule.runOnIdle {
            app.music.np.workContext = StandardTestDispatcher()
            app.music.np.art = ImageBitmap(COVER, COVER)
        }
        frames(BLUR_FRAMES)
        assertSame(first, app.host.npBackdrop.shown)
    }

    @Test
    fun aNewSongsCoverBlurringHoldsThePreviousCoverUntilItLands() {
        val app = lyricsApp(Edit(), mode = false)
        rule.runOnIdle {
            app.music.np.workContext = Dispatchers.Unconfined
            app.music.np.art = ImageBitmap(COVER, COVER)
            app.backdropFlow.value = NpPicture(ImageBitmap(4, 4))
        }
        frames(2)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(BLUR_FRAMES)
        val first = app.host.npBackdrop.shown
        assertTrue(first!!.whole)
        val held = StandardTestDispatcher()
        rule.runOnIdle {
            val key = app.music.np.artKey as List<*>
            app.music.np.artKey = listOf(2L) + key.drop(1)
            app.music.np.workContext = held
            app.music.np.sync(NpShown.of(listOf(song(2))), rule.mainClock.currentTime)
        }
        frames(BLUR_FRAMES)
        assertSame(first, app.host.npBackdrop.shown)
        rule.runOnIdle { app.music.np.art = ImageBitmap(COVER, COVER) }
        frames(BLUR_FRAMES)
        assertSame(first, app.host.npBackdrop.shown)
        rule.runOnIdle { held.scheduler.advanceUntilIdle() }
        frames(BLUR_FRAMES)
        val next = app.host.npBackdrop.shown
        assertTrue(next!!.whole)
        assertNotSame(first, next)
    }

    @Test
    fun lyricsModeWithNoArtShowsTheAppPicture() {
        val app = lyricsApp(Edit(), mode = false)
        val pic = NpPicture(ImageBitmap(4, 4))
        val next = NpPicture(ImageBitmap(4, 4))
        rule.runOnIdle { app.backdropFlow.value = pic }
        frames(2)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(BLUR_FRAMES)
        assertNull(app.music.np.art)
        assertSame(pic, app.host.npBackdrop.shown)
        rule.runOnIdle { app.backdropFlow.value = next }
        frames(BLUR_FRAMES)
        assertSame(next, app.host.npBackdrop.shown)
    }

    @Test
    fun aCoverBlurredAtOneSizeIsBlurredAgainAtTheNext() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
        val app = lyricsApp(Edit(), mode = false)
        rule.runOnIdle {
            app.music.np.workContext = Dispatchers.Unconfined
            app.music.np.art = ImageBitmap(COVER, COVER)
            app.backdropFlow.value = NpPicture(ImageBitmap(4, 4))
        }
        frames(2)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(BLUR_FRAMES)
        val first = app.host.npBackdrop.shown
        assertTrue(first!!.whole)
        rule.runOnIdle {
            app.music.np.artKey = listOf(1L, null, NpLayout(shorter).artSide, null, false)
            app.np = shorter
        }
        frames(BLUR_FRAMES)
        val next = app.host.npBackdrop.shown
        assertTrue(next!!.whole)
        assertNotSame(first, next)
    }

    @Test
    fun afterAPhoneSessionThePhoneArtGivesNoCover() {
        val app = lyricsApp(Edit())
        val pic = NpPicture(ImageBitmap(4, 4))
        val art = Bitmap.createBitmap(COVER, COVER, Bitmap.Config.ARGB_8888)
        val track = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, art)
        rule.runOnIdle {
            app.music.np.workContext = Dispatchers.Unconfined
            app.backdropFlow.value = pic
            app.phone.value = PhoneSession(track, {}) {}
        }
        frames(OPEN_FRAMES)
        assertTrue(app.music.np.variant)
        assertTrue(app.music.np.artPhone)
        assertNotNull(app.music.np.art)
        assertSame(pic, app.host.npBackdrop.shown)
        val phoneArt = app.music.np.art
        rule.runOnIdle { app.phone.value = null }
        frames(OPEN_FRAMES)
        assertFalse(app.music.np.variant)
        rule.runOnIdle {
            app.music.np.artPhone = true
            app.music.np.art = phoneArt
        }
        frames(BLUR_FRAMES)
        assertSame(pic, app.host.npBackdrop.shown)
    }

    @Test
    fun aCoverThatCannotBlurGivesTheAppPicture() {
        val app = lyricsApp(Edit(), mode = false)
        val pic = NpPicture(ImageBitmap(4, 4))
        val next = NpPicture(ImageBitmap(4, 4))
        rule.runOnIdle {
            app.music.np.workContext = Dispatchers.Unconfined
            app.music.np.art = ImageBitmap(1, 1)
            app.backdropFlow.value = pic
        }
        frames(2)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(BLUR_FRAMES)
        assertSame(pic, app.host.npBackdrop.shown)
        rule.runOnIdle { app.backdropFlow.value = next }
        frames(BLUR_FRAMES)
        assertSame(next, app.host.npBackdrop.shown)
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val PCT = 100
        private const val STEP = 10
        private const val START = 50
        private const val CAP = 60
        private const val GRID = 15
        private const val GRID_AT = 7
        private const val GRID_STRIDES = 4
        private const val MID_U = 300f
        private const val DRAG_PX = 600f
        private const val STEPS = 10
        private const val OPEN_FRAMES = 30
        private const val CUT_FRAMES = 2
        private const val BLUR_FRAMES = 4
        private const val COVER = 64
        private const val DURATION = 180_000L
        private const val H_SHORT = 1080
        private const val MIN_SHIFT = 10
        private const val COL = 2
        private const val WHITE = 200
        private const val RESIZE_FRAMES = 2
        private const val TOAST_FRAMES = 20
        private const val ALBUM = 20L
        private const val STALE = 7L
        private const val TOP_Y = 40f
        private const val FADE_FRAMES = 60
        private const val POSITION = 5000L
        private const val RING_ONE_X = 120f
        private const val RING_Y = 398f
        private const val LRC = "[00:01.00]one\n[00:02.00]two"
    }
}
