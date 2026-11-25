package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fxInt
import net.seilent.funekit.kept
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.LyricCopy
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.core.Section
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.Jump
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.px
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.npStatus
import net.seilent.funekit.screens.music.nowplaying.NpHalf
import net.seilent.funekit.screens.music.nowplaying.NpSplit
import net.seilent.funekit.screens.music.nowplaying.npFlat
import net.seilent.funekit.screens.posture.Fold
import net.seilent.funekit.scale
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpAct
import net.seilent.funekit.screens.music.nowplaying.npTitleGlow
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.OffsetEdit
import net.seilent.funekit.screens.music.nowplaying.PhoneLyrics
import net.seilent.funekit.screens.music.nowplaying.PhoneRepeat
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack
import net.seilent.funekit.screens.music.queue.QueueModel
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
import java.io.ByteArrayOutputStream
import java.io.IOException
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
    private val plusAt = Offset(Tok.canvasW / 2 * np.ppu + (np.left - page.left), np.board.vol_plusY * np.ppu)
    private val passAt = Offset(Tok.canvasW / 2 * np.ppu + (np.left - page.left), (np.h - Tok.gesture_bottom / 2) * np.ppu)
    private val titleAt = Tok.npTextX + TITLE_DX to np.board.npTitleY - TITLE_DY
    private val seekAt = (Tok.npSeekBandX0 + np.board.npSeekBandX1) / 2 to (np.board.npProgressY + np.board.npSeekBandBottom) / 2
    private val titlePx = Offset((Tok.npTextX + TITLE_DX) * np.ppu + (np.left - page.left), (np.board.npTitleY - TITLE_DY) * np.ppu)
    private val artPx = NpLayout(np).let { Offset(it.artX + it.artSide / 2f + (np.left - page.left), it.artY + it.artSide / 2f) }
    private val ringPx = NpLayout(np).let { Offset(it.ringCx(0) + (np.left - page.left).toFloat(), it.ringCy.toFloat()) }
    private val seekPx = Offset(seekAt.first * np.ppu + (np.left - page.left), seekAt.second * np.ppu)
    private val markAt = NpLayout(np).let { Offset(it.markX + (np.left - page.left).toFloat(), it.markY.toFloat()) }
    private val idleFrames = ((NowPlayingModel.IDLE + NowPlayingModel.IDLE_FADE) / FRAME_MS + 2).toInt()
    private val idleFadeFrames = (NowPlayingModel.IDLE_FADE / FRAME_MS + 2).toInt()

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

    private class StepLevel(var pct: Int) : VolumeLevel {
        val waits = mutableListOf<CompletableDeferred<Unit>>()

        override fun percent(): Int = pct

        override fun stepPct(): Int = STEP

        override suspend fun step(detents: Int) {
            val d = CompletableDeferred<Unit>()
            waits += d
            d.await()
            pct = (pct + detents * STEP).coerceIn(0, PCT)
        }

        override suspend fun set(pct: Int) {
            this.pct = pct
        }

        fun land() {
            waits.removeAt(0).complete(Unit)
        }
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

    private fun until(what: String, ok: () -> Boolean) {
        var n = 0
        while (!rule.runOnIdle(ok)) {
            frames(1)
            assertTrue(what, ++n < MAX_FRAMES)
        }
    }

    private fun cardApp(root: Boolean = false, stack: (TestApp) -> List<TestScreen>): Pair<TestApp, Fake> {
        val f = testFake(FakeSet.Demo)
        val src = MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)
        return TestApp(page, np, src, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.compose(rule, root)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.runOnIdle { it.stack.reset(stack(it)) }
            frames(OPEN_FRAMES)
        } to f
    }

    private fun music(app: TestApp, p: MusicPage) = TestScreen.Music(MusicEntry(app.stack.entry(), p))

    private fun pages(app: TestApp): List<Any?> = rule.runOnIdle { app.stack.pages.map { it.page } }

    @Test
    fun aCardOutOverQueueThenAlbumPopsToQueue() {
        val (app, f) = cardApp { listOf(music(it, MusicPage.Hub), music(it, MusicPage.Queue), music(it, MusicPage.Album(ALBUM))) }
        assertEquals("premise", listOf(MusicPage.Hub, MusicPage.Queue, MusicPage.Album(ALBUM)), pages(app))
        rule.runOnIdle { f.card(Card.Out) }
        frames(OPEN_FRAMES)
        assertEquals(listOf<Any?>(MusicPage.Hub, MusicPage.Queue), pages(app))
    }

    @Test
    fun aCardOutDuringAMovePopsAtTheSettle() {
        val (app, f) = cardApp { listOf(music(it, MusicPage.Hub), music(it, MusicPage.Queue)) }
        rule.runOnIdle { app.stack.push(music(app, MusicPage.Album(ALBUM)), MoveKind.T2) }
        frames(CUT_FRAMES)
        assertTrue("premise: the push moves", rule.runOnIdle { app.stack.moving })
        rule.runOnIdle { f.card(Card.Out) }
        frames(CUT_FRAMES)
        assertTrue("the card out leaves the push moving", rule.runOnIdle { app.stack.moving })
        assertEquals("nothing pops during the move, the album still arrives", MusicPage.Album(ALBUM), rule.runOnIdle { app.stack.turn?.to?.page })
        var landed = false
        repeat(OPEN_FRAMES) {
            frames(1)
            if (pages(app).size == STACKED) landed = true
        }
        assertTrue("the album lands before the pop", landed)
        until("the pop lands") { !app.stack.moving }
        assertEquals(listOf<Any?>(MusicPage.Hub, MusicPage.Queue), pages(app))
    }

    @Test
    fun aCardBackBeforeTheSettleWithdrawsThePop() {
        val (app, f) = cardApp { listOf(music(it, MusicPage.Hub), music(it, MusicPage.Queue)) }
        rule.runOnIdle { app.stack.push(music(app, MusicPage.Album(ALBUM)), MoveKind.T2) }
        frames(CUT_FRAMES)
        rule.runOnIdle { f.card(Card.Out) }
        frames(CUT_FRAMES)
        rule.runOnIdle { f.card(Card.Present) }
        frames(CUT_FRAMES)
        assertTrue("premise: the push still moves", rule.runOnIdle { app.stack.moving })
        until("the moves settle") { !app.stack.moving }
        assertEquals("the card came back before the pop could run", listOf<Any?>(MusicPage.Hub, MusicPage.Queue, MusicPage.Album(ALBUM)), pages(app))
    }

    @Test
    fun theQueueACardOutPopRevealsShowsNoRowsFromItsFirstFrame() {
        val (app, f) = cardApp(root = true) { listOf(music(it, MusicPage.Hub), music(it, MusicPage.Queue)) }
        runBlocking { f.player.play(Listing.Songs, f.library.view().generation, 0, 0L) }
        val q = rule.runOnIdle { app.models[app.stack.pages.last().entry] as QueueModel }
        until("premise: the queue shows rows") { q.rowsShown > 0 }
        rule.runOnIdle { app.stack.push(music(app, MusicPage.Album(ALBUM)), MoveKind.T2) }
        until("premise: the album lands") { app.stack.pages.size == STACKED && !app.stack.moving }
        rule.runOnIdle { f.card(Card.Out) }
        var first = -1
        repeat(OPEN_FRAMES) {
            frames(1)
            if (first < 0 && rule.runOnIdle { app.stack.turn != null }) first = rule.runOnIdle { q.rowsShown }
        }
        assertEquals("the queue under the pop takes the card before its first frame", 0, first)
        until("the pop lands") { !app.stack.moving }
        assertEquals(listOf<Any?>(MusicPage.Hub, MusicPage.Queue), pages(app))
    }

    @Test
    fun aQueueOpenedWhileTheCardIsOutShowsNoRows() {
        val (app, f) = cardApp(root = true) { listOf(music(it, MusicPage.Hub)) }
        runBlocking { f.player.play(Listing.Songs, f.library.view().generation, 0, 0L) }
        rule.runOnIdle { f.card(Card.Out) }
        frames(OPEN_FRAMES)
        val queue = rule.runOnIdle { music(app, MusicPage.Queue).also { app.stack.push(it, MoveKind.T2) } }
        until("premise: the queue opens") { app.models[queue.entry] != null }
        val q = rule.runOnIdle { app.models[queue.entry] as QueueModel }
        assertEquals("a model made after the card went out takes the card", Card.Out, rule.runOnIdle { q.card })
        until("premise: the queue takes the player state") { q.size > 0 }
        assertEquals(0, rule.runOnIdle { q.rowsShown })
    }

    @Test
    fun aCardOutDropsAnAlbumColumnPickWaitingForItsDrop() {
        val (app, f) = cardApp(root = true) { listOf(music(it, MusicPage.Hub)) }
        val hub = rule.runOnIdle { app.stack.pages.single() }
        val h = rule.runOnIdle { app.models[hub.entry] as HubModel }
        rule.runOnIdle { h.select(HUB_ALBUMS) }
        until("premise: the albums pane loads") { h.pane.map != null }
        val letter = rule.runOnIdle { (0 until Section.count).first { h.pane.map?.has(it) == true } }
        rule.runOnIdle { h.colPick = letter }
        rule.runOnIdle { f.card(Card.Out) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.host.dropped(hub, rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertFalse("no column push moves", rule.runOnIdle { app.stack.moving })
        assertEquals("the pick went with the card", listOf<Any?>(MusicPage.Hub), pages(app))
    }

    @Test
    fun aMosaicPickLandsOnTheAlbumColumnAtItsLetter() {
        val (app, _) = cardApp(root = true) { listOf(music(it, MusicPage.Hub)) }
        val hub = rule.runOnIdle { app.stack.pages.single() }
        val h = rule.runOnIdle { app.models[hub.entry] as HubModel }
        rule.runOnIdle { h.select(HUB_ALBUMS) }
        until("premise: the albums pane loads") { h.pane.map != null }
        val letter = rule.runOnIdle { (0 until Section.count).filter { h.pane.map?.has(it) == true }[1] }
        val grid = page.kept()
        rule.runOnIdle {
            h.jumpPick = -1
            h.colPick = -1
            h.jump.open(h.avail(), letter, grid.stdContentBottom)
            app.host.openJump(hub)
        }
        frames(OPEN_FRAMES)
        assertEquals("premise: the jump grid is up", OverKind.Jump, rule.runOnIdle { app.stack.overlay?.kind })
        val slot = Jump.slotOf(letter)
        val off = rule.runOnIdle { h.jump.offset }
        tap((Jump.cellX(slot) + Jump.tile / 2) * grid.stdPpu, (Jump.rowTop(slot) - off + Jump.tile / 2) * grid.stdPpu)
        until("the grid drops") { app.stack.overlay == null }
        assertEquals("the pick waits for the drop", letter, rule.runOnIdle { h.colPick })
        var n = 0
        while (rule.runOnIdle { app.stack.turn == null }) {
            frames(1)
            assertTrue("the column push starts", ++n < OPEN_FRAMES)
        }
        assertEquals("the column lands by T3c", MoveKind.T3c, rule.runOnIdle { app.stack.turn?.kind })
        assertEquals("the pick fired", -1, rule.runOnIdle { h.colPick })
        until("the column lands") { !app.stack.moving }
        assertEquals(listOf<Any?>(MusicPage.Hub, MusicPage.AlbumCol(letter)), pages(app))
        val col = rule.runOnIdle { app.models[app.stack.pages.last().entry] as AlbumColModel }
        until("premise: the column opens") { col.opened }
        assertEquals("the column shows its letter's tile first", letter, rule.runOnIdle { col.letterAt(col.track.first) })
    }

    @Test
    fun aCardOutOverARootRunPopsToTheRoot() {
        val (app, f) = cardApp { listOf(music(it, MusicPage.AlbumCol(0)), music(it, MusicPage.Album(ALBUM))) }
        rule.runOnIdle { f.card(Card.Error) }
        frames(OPEN_FRAMES)
        assertEquals(emptyList<Any?>(), pages(app))
    }

    @Test
    fun aFailedQueueReadWritesItsLineThroughTheApp() {
        val (app, f) = cardApp { listOf(music(it, MusicPage.Hub)) }
        val id = runBlocking { f.library.view().page(Listing.Songs, 0, 1).rows.first().id }
        f.busyNext("scan running", Listing.Queue)
        runBlocking { f.player.play(listOf(id), 0, 0) }
        frames(OPEN_FRAMES)
        assertEquals(1, app.logs.count { it == "now playing queue read at 0: scan running" })
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

    @Test
    fun aVolumeOpenRefusedUnderAnotherOverlayShowsNoPlate() {
        val app = app(Level(START, null))
        rule.runOnIdle {
            app.stack.open(Overlay(OverKind.Sheet))
            app.host.openVolume(rule.mainClock.currentTime)
        }
        frames(OPEN_FRAMES)
        assertEquals("the volume open is refused", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("no plate without its overlay", false, app.host.hud.shown)
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the volume open lands", OverKind.Volume, app.stack.overlay?.kind)
        assertTrue("the plate shows with its overlay", app.host.hud.visible(rule.mainClock.currentTime))
    }

    @Test
    fun aPlateThatFadesOutOnItsOwnClearsItsShownState() {
        val app = app(Level(START, null))
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        var n = 0
        while (app.stack.overlay != null && n < PLATE_FRAMES) {
            frames(1)
            n++
        }
        assertNull("premise: the plate fades out on its own", app.stack.overlay)
        assertEquals("the plate's shown state ends with its overlay", false, app.host.hud.shown)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(CUT_FRAMES)
        assertEquals(OverKind.Volume, app.stack.overlay?.kind)
        assertTrue("the next open starts the plate again", app.host.hud.opening(rule.mainClock.currentTime))
    }

    @Test
    fun aPressDuringThePlatesFadeRevivesItAtOnceAndShowsTheStepWhenItAnswers() {
        val level = StepLevel(START)
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        var n = 0
        while (!app.host.hud.closing(rule.mainClock.currentTime) && n < PLATE_FRAMES) {
            frames(1)
            n++
        }
        assertTrue("premise: the plate fades with its overlay up", app.host.hud.closing(rule.mainClock.currentTime) && app.stack.overlay?.kind == OverKind.Volume)
        rule.runOnIdle { app.host.volPress(1) }
        frames(1)
        assertTrue("the press revives the plate in its frame", !app.host.hud.closing(rule.mainClock.currentTime) && app.stack.overlay?.kind == OverKind.Volume)
        assertEquals("the plate keeps its level while the step waits", START, app.host.hud.percent)
        rule.runOnIdle { level.land() }
        frames(1)
        assertEquals("the plate shows the step once it answers", START + STEP, app.host.hud.percent)
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
    fun aVolumeKeyAndASheetInOneFrameOpenOnlyThePlate() {
        val app = app(Level(START, null))
        val sheet = rule.runOnIdle {
            app.host.volPress(1)
            app.host.openMenu(MenuForm.Sheet, "sheet", listOf("one", "two"), false, -1, 0, false, { true })
        }
        frames(OPEN_FRAMES)
        assertNull("a sheet asked while the plate's open is held is refused at its call", sheet)
        assertEquals(OverKind.Volume, app.stack.overlay?.kind)
        assertTrue("the plate shows", app.host.hud.visible(rule.mainClock.currentTime))
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

    private class MovingEdit(start: Int) : OffsetEdit {
        override var shownMs by mutableIntStateOf(start)
        override var editing = false
        private var from = start

        override fun begin() {
            from = shownMs
            editing = true
        }

        override fun step(d: Int) {
            shownMs += STEP_MS * d
        }

        override fun coarse(d: Int) {
            shownMs += COARSE_MS * d
        }

        override fun keep() {
            editing = false
        }

        override fun restore() {
            shownMs = from
            editing = false
        }

        override fun entry(adjust: Boolean) {}

        override fun end() {}
    }

    private fun song(id: Long) = Row(id, Kind.Song, "Song", "Artist", null, durationMs = DURATION, album = "Album")

    private fun lyricsApp(e: OffsetEdit, view: Boolean = true, mode: Boolean = true, pl: PhoneLyrics? = null, modeAtStart: Boolean = false, root: Boolean = false): TestApp =
        TestApp(page, np, null, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.lyricsOn = view
            it.edit = e
            it.phoneLyricsOf = pl
            if (pl != null) it.music.np.workContext = Dispatchers.Unconfined
            it.modeAtStart = modeAtStart
            it.compose(rule, root)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.runOnIdle {
                val m = it.music.np
                m.sync(NpShown.of(listOf(song(1))), 0)
                m.lyrics.begin(1)
                m.lyrics.load(1, "[00:01.00]one\n[00:02.00]two", 0)
                if (mode) {
                    m.lyrics.wanted(true)
                    m.lyrics.open(1, false)
                }
                it.host.resetNp(listOf(TestScreen.Music(MusicEntry(it.stack.entry(), MusicPage.Hub))))
            }
            frames(OPEN_FRAMES)
        }

    @Test
    fun aLyricsOffsetAskedWhileAnOpenIsHeldIsRefused() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle {
            app.stack.open(Overlay(OverKind.Sheet))
            app.host.openOffset()
        }
        frames(OPEN_FRAMES)
        assertNull("a lyrics offset asked while an open is held is refused", app.host.fieldSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun aLyricsSheetRowTapOpensTheAdjustOverlayOnlyOnceTheSheetHasSettled() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        val sheet = app.host.fieldSheet!!
        rule.runOnIdle { app.menuHost.act(0) }
        frames(1)
        assertTrue(e.editing)
        assertFalse(sheet.hud.shown)
        frames(OPEN_FRAMES)
        assertTrue(sheet.hud.shown)
        assertTrue(rule.runOnIdle { app.host.fieldKey(1) })
        assertEquals(listOf("begin", "coarse 1"), e.calls)
    }

    @Test
    fun anEdgeBackOnTheLyricsSheetWithAnEditLiveKeepsTheSheetAndRestoresTheEdit() {
        val e = Edit().also { it.editing = true }
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertFalse("premise: the live edit shows no hud", app.host.fieldSheet!!.hud.shown)
        rule.runOnIdle { app.stack.edgeBack(app.host.popKind) }
        frames(OPEN_FRAMES)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the edge restores the edit", listOf("restore"), e.calls)
        assertFalse("the edit ends", e.editing)
    }

    private fun carriedApp(e: MovingEdit): TestApp = lyricsApp(e).also {
        rule.runOnIdle { it.music.np.lyrics.load(1, CARRIED_COPY, CARRIED, carries = true) }
        frames(CUT_FRAMES)
    }

    private fun shownLines(app: TestApp): List<Long> = rule.runOnIdle { app.music.np.lyrics.lines.map { it.ms } }

    private fun hudUp() {
        val at = Offset(page.widthPx / 2f, px(Tok.overlayPlusY, page.stdPpu).toFloat())
        rule.onRoot().performTouchInput {
            down(at)
            up()
        }
        frames(CUT_FRAMES)
    }

    private fun offsetHud(app: TestApp) {
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(OPEN_FRAMES)
        assertTrue("premise: the hud is up", app.host.fieldSheet!!.hud.shown)
    }

    @Test
    fun anOffsetStepPreviewsTheShownCopyAtTheEditsValue() {
        val e = MovingEdit(CARRIED)
        val app = carriedApp(e)
        assertEquals("premise: the copy shows as served", listOf(500L, 1500L), shownLines(app))
        offsetHud(app)
        hudUp()
        assertEquals("premise: the step moves the edit", CARRIED + STEP_MS, e.shownMs)
        assertEquals(listOf(400L, 1400L), shownLines(app))
    }

    @Test
    fun aRestoreAndAnUnkeptCloseLandTheCopyBackAtTheStart() {
        val e = MovingEdit(CARRIED).also { it.editing = true }
        val app = carriedApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertFalse("premise: the live edit shows no hud", app.host.fieldSheet!!.hud.shown)
        rule.runOnIdle { app.host.fieldSheet!!.tap(AdjustHit.Up0) }
        frames(CUT_FRAMES)
        assertEquals(listOf(400L, 1400L), shownLines(app))
        rule.runOnIdle { app.stack.edgeBack(app.host.popKind) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the restore lands the copy back", listOf(500L, 1500L), shownLines(app))
        rule.runOnIdle {
            e.begin()
            app.host.fieldSheet!!.tap(AdjustHit.Up0)
        }
        frames(CUT_FRAMES)
        assertEquals(listOf(400L, 1400L), shownLines(app))
        rule.runOnIdle { app.host.fieldEnd() }
        frames(CUT_FRAMES)
        assertEquals("an unkept close lands the copy back", listOf(500L, 1500L), shownLines(app))
    }

    @Test
    fun aTypedValueShowsAtTheReturn() {
        val e = MovingEdit(CARRIED)
        val app = carriedApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        assertNull("premise: the entry closes the sheet", app.stack.overlay)
        rule.runOnIdle { e.shownMs = TYPED_MS }
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.fieldReturn(false) }
        frames(3)
        assertEquals("premise: the sheet is back", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(listOf(0L, 500L), shownLines(app))
    }

    @Test
    fun aCopyServedDuringTheEditLandsAtTheEditsValue() {
        val e = MovingEdit(CARRIED)
        val app = carriedApp(e)
        offsetHud(app)
        hudUp()
        assertEquals("premise: the step moves the edit", CARRIED + STEP_MS, e.shownMs)
        rule.runOnIdle { app.music.np.lyrics.load(1, "[00:00.80]one\n[00:01.80]two", 200, carries = true) }
        assertEquals(listOf(400L, 1400L), shownLines(app))
    }

    @Test
    fun aCopyForAnotherTrackTakesNoOpenEdit() {
        val e = MovingEdit(CARRIED)
        val app = carriedApp(e)
        offsetHud(app)
        hudUp()
        assertEquals("premise: the step moves the edit", CARRIED + STEP_MS, e.shownMs)
        val lines = rule.runOnIdle {
            val l = app.music.np.lyrics
            l.begin(2)
            l.load(2, "[00:00.80]one\n[00:01.80]two", 200, carries = true)
            l.lines.map { it.ms }
        }
        assertEquals("another track's copy lands as served", listOf(800L, 1800L), lines)
    }

    @Test
    fun anAppMoveOfTheShownOffsetPreviewsWithNoSheetCall() {
        val e = MovingEdit(CARRIED)
        val app = carriedApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertEquals("premise: the sheet is up", OverKind.Sheet, app.stack.overlay?.kind)
        rule.runOnIdle { e.shownMs = APP_MS }
        frames(CUT_FRAMES)
        assertEquals(listOf(300L, 1300L), shownLines(app))
    }

    @Test
    fun aTypedEntryClosedByACutKeepsTheRunAndReopensItsSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        assertNull("premise: the entry closes the sheet", app.stack.overlay)
        val keys = Overlay(OverKind.Sheet)
        rule.runOnIdle { app.stack.open(keys) }
        frames(OPEN_FRAMES)
        assertSame("premise: the entry is a stack overlay", keys, app.stack.overlay)
        rule.runOnIdle {
            app.stack.cut()
            app.host.fieldReturn(false)
        }
        frames(OPEN_FRAMES)
        assertNotNull("the run stays", app.host.fieldSheet)
        assertEquals("the sheet is back", OverKind.Sheet, app.stack.overlay?.kind)
        assertNotSame("the sheet is the run's own", keys, app.stack.overlay)
    }

    @Test
    fun anOffsetOpenWhileTheReopenLandsIsRefusedAndTheRunStays() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        val sheet = app.host.fieldSheet
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        assertNull("premise: the entry closes the sheet", app.stack.overlay)
        rule.runOnIdle { app.host.fieldReturn(false) }
        rule.runOnIdle {
            assertTrue("premise: the reopen is asked and not landed", app.stack.moving && app.stack.overlay == null)
            app.host.openOffset()
        }
        frames(OPEN_FRAMES)
        assertSame("the run stays", sheet, app.host.fieldSheet)
        assertEquals("its sheet is back", OverKind.Sheet, app.stack.overlay?.kind)
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
        assertNotNull(app.host.fieldSheet)
        rule.runOnIdle { app.host.fieldReturn(true) }
        frames(3)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertTrue(app.host.fieldSheet!!.hud.shown)
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
        assertTrue(app.host.fieldSheet!!.hud.shown)
        shot()
        val x = px(Tok.edge, page.stdPpu) + 2f
        val y = px(page.board.overlayRingY, page.stdPpu) - 2f
        rule.onRoot().performTouchInput { down(Offset(x, y)) }
        var n = 0
        while (app.host.fieldSheet!!.hud.shown && n < OPEN_FRAMES) {
            frames(1)
            n++
        }
        assertFalse(app.host.fieldSheet!!.hud.shown)
        frames(CUT_FRAMES)
        assertNull(app.stack.overlay)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertNull(app.stack.overlay)
        assertNotNull(app.host.fieldSheet)
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
        assertTrue(app.host.fieldSheet!!.hud.shown)
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
        rule.runOnIdle { app.host.fieldReturn(false) }
        frames(CUT_FRAMES)
        assertNull(app.stack.overlay)
        assertNull(app.host.fieldSheet)
        assertEquals(listOf("entry false", "end"), e.calls)
    }

    @Test
    fun aSheetOpenedOverAHeldOneEndsTheHeldEdit() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        val held = app.host.fieldSheet
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertNotSame(held, app.host.fieldSheet)
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
            app.host.fieldReturn(false)
            app.host.openOffset()
        }
        val next = app.host.fieldSheet
        frames(OPEN_FRAMES)
        assertNotNull(next)
        assertSame(next, app.host.fieldSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(listOf("entry false", "end"), e.calls)
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(0) })
        frames(OPEN_FRAMES)
        assertNull(app.stack.overlay)
        assertSame(next, app.host.fieldSheet)
        assertEquals(listOf("entry false", "end", "entry false"), e.calls)
    }

    @Test
    fun turningTheLyricsViewOffClosesTheLyricsSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        assertNotNull(app.host.fieldSheet)
        rule.runOnIdle { app.lyricsOn = false }
        frames(OPEN_FRAMES)
        assertNull(app.host.fieldSheet)
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
            app.host.fieldReturn(false)
        }
        frames(2 * OPEN_FRAMES)
        assertTrue(app.host.isNp(app.stack.top!!))
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertNotNull(app.host.fieldSheet)
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
        val sheet = app.host.fieldSheet
        frames(OPEN_FRAMES)
        assertNotNull(sheet)
        assertSame(sheet, app.host.fieldSheet)
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
        assertNull(app.host.fieldSheet)
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
        assertNull(app.host.fieldSheet)
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
        assertNull(app.host.fieldSheet)
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
        assertNull(app.host.fieldSheet)
        assertEquals(listOf("end"), e.calls)
    }

    @Test
    fun aReReadOfTheSameTrackKeepsTheLyricsSheet() {
        val app = lyricsApp(Edit())
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        val sheet = app.host.fieldSheet
        rule.runOnIdle { app.music.np.lyrics.begin(1) }
        frames(OPEN_FRAMES)
        assertSame(sheet, app.host.fieldSheet)
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
        assertNull(app.host.fieldSheet)
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
    fun theOwnLyricsModeSeedsFromTheAppAndEachOwnTurnCallsBack() {
        val app = lyricsApp(Edit(), mode = false, modeAtStart = true)
        val l = app.music.np.lyrics
        rule.runOnIdle {
            assertTrue("the own mode seeds from the app", l.mode)
            assertEquals("the shade lands at once", fxInt(1), l.dim(rule.mainClock.currentTime))
            assertFalse("no fade runs", l.fading(rule.mainClock.currentTime))
        }
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(OPEN_FRAMES)
        assertEquals(listOf(false), app.modeChanges)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(OPEN_FRAMES)
        assertEquals(listOf(false, true), app.modeChanges)
        rule.runOnIdle { app.stack.edgeBack(app.host.popKind) }
        frames(OPEN_FRAMES)
        assertEquals(listOf(false, true, false), app.modeChanges)
    }

    @Test
    fun aVariantTurnCallsBackOnlyThePhoneMode() {
        val pl = Copy()
        val app = phoneApp(pl)
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(OPEN_FRAMES)
        assertEquals(listOf(true), pl.sets)
        assertTrue("a variant turn calls no own mode back", app.modeChanges.isEmpty())
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(OPEN_FRAMES)
        assertEquals(listOf(true), app.modeChanges)
        assertEquals("the own turn sets no phone mode", listOf(true), pl.sets)
    }

    @Test
    fun theHostSeedsTheOwnModeOnceAndATurnStays() {
        val app = lyricsApp(Edit(), mode = false)
        val l = app.music.np.lyrics
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(OPEN_FRAMES)
        assertTrue("premise: the tap turns the own mode on", rule.runOnIdle { l.mode })
        rule.runOnIdle { app.hosted = false }
        frames(2)
        rule.runOnIdle { app.hosted = true }
        frames(OPEN_FRAMES)
        assertTrue("the turn stays", rule.runOnIdle { l.mode })
    }

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
            assertTrue("the own mode is wanted", m.lyrics.want)
            assertFalse("the own shown mode stays off", m.lyrics.mode)
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
        rule.runOnIdle { app.backdropFlow.value = pic }
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
        assertNull(app.host.fieldSheet)
    }

    @Test
    fun plainLyricsOpenNoLyricsSheet() {
        val app = lyricsApp(Edit())
        rule.runOnIdle {
            app.music.np.lyrics.begin(1)
            app.music.np.lyrics.load(1, "one\ntwo", 0)
            app.host.openOffset()
        }
        assertNull(app.host.fieldSheet)
    }

    @Test
    fun anAppWithNoEditForTheTrackOpensNoSheet() {
        val app = lyricsApp(Edit())
        app.edit = null
        rule.runOnIdle { app.host.openOffset() }
        frames(CUT_FRAMES)
        assertNull(app.stack.overlay)
        assertNull(app.host.fieldSheet)
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
        assertNull(app.host.fieldSheet)
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
        val sheet = app.host.fieldSheet!!
        assertTrue(sheet.hud.shown)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertFalse(sheet.hud.shown)
        assertSame(sheet, app.host.fieldSheet)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(listOf("begin", "restore"), e.calls)
    }

    @Test
    fun backOnTheLyricsSheetWithItsEditOpenDropsItAndASecondBackClosesIt() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        val sheet = app.host.fieldSheet!!
        rule.runOnIdle { app.menuHost.act(0) }
        frames(1)
        assertTrue("premise: the edit is open", e.editing)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertFalse("the hud stays shut", sheet.hud.shown)
        assertEquals(listOf("begin", "restore"), e.calls)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertEquals("a second back closes the sheet", null, app.stack.overlay?.kind)
        assertEquals(listOf("begin", "restore", "end"), e.calls)
    }

    @Test
    fun aBackAfterTheEditEndedOutsideTheHostClosesTheSheet() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(1)
        assertTrue("premise: the edit is open", e.editing)
        e.editing = false
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertEquals("the back closes the sheet", null, app.stack.overlay?.kind)
        assertEquals(listOf("begin", "end"), e.calls)
    }

    @Test
    fun exitInTheOverlayKeepsTheEditAndRaisesTheSheetAgain() {
        val e = Edit()
        val app = lyricsApp(e)
        rule.runOnIdle { app.host.openOffset() }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.menuHost.act(0) }
        frames(OPEN_FRAMES)
        val sheet = app.host.fieldSheet!!
        assertTrue(sheet.hud.shown)
        tap(2f, 2f)
        frames(2)
        assertFalse(sheet.hud.shown)
        assertSame(sheet, app.host.fieldSheet)
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
        assertNull(app.host.fieldSheet)
        assertEquals(listOf("end"), e.calls)
    }

    @Test
    fun withNoLyricsViewTheLyricsSheetNeverOpens() {
        val e = Edit()
        val app = lyricsApp(e, view = false)
        rule.runOnIdle { app.host.openOffset() }
        frames(2)
        assertNull(app.stack.overlay)
        assertNull(app.host.fieldSheet)
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

    private class Painted(var png: ByteArray)

    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(COVER, COVER, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun coverApp(art: Painted = Painted(png(Color.BLUE))): Pair<TestApp, Fake> {
        val f = testFake(FakeSet.Demo)
        fun painted(v: LibraryView): LibraryView = object : LibraryView by v {
            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray = art.png
        }
        val lib = object : Library by f.library {
            override fun view(): LibraryView = painted(f.library.view())

            override fun view(generation: Long): LibraryView = painted(f.library.view(generation))
        }
        val ts = runBlocking { f.library.view().page(Listing.Songs, 0, ART_SONGS).rows.filter { it.art != null }.map { it.id }.take(2) }
        runBlocking { f.player.play(ts, 0, 0) }
        val lyrics = object : Lyrics by f.lyrics {
            override suspend fun lyrics(track: Long): String? = LRC

            override suspend fun copy(track: Long): LyricCopy? = LyricCopy(LRC, null)
        }
        val src = MusicSource(lib, f.player, f.quickplay, f.favorites, f.playlists, lyrics)
        return TestApp(page, np, src, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.lyricsOn = true
            it.modeAtStart = true
            it.music.np.workContext = Dispatchers.Unconfined
            it.backdropFlow.value = NpPicture(ImageBitmap(4, 4))
            it.compose(rule)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.waitUntil(WAIT_MS) { rule.runOnIdle { it.music.np.lyrics.timed(it.music.np.shown.track, false) } }
            rule.runOnIdle { it.host.resetNp(listOf(TestScreen.Music(MusicEntry(it.stack.entry(), MusicPage.Hub)))) }
            frames(OPEN_FRAMES)
        } to f
    }

    private fun artAfter(app: TestApp, old: ImageBitmap?): ImageBitmap {
        rule.waitUntil(WAIT_MS) {
            rule.waitForIdle()
            app.music.np.art.let { it != null && it !== old }
        }
        return checkNotNull(app.music.np.art)
    }

    private fun settledCover(app: TestApp): ImageBitmap {
        val art = artAfter(app, null)
        frames(BLUR_FRAMES)
        assertTrue("premise: the cover shows", app.host.npBackdrop.shown!!.whole)
        frames((Tok.motion_backdropFade / FRAME_MS).toInt() + 2)
        aloneAtOne(app, "premise: the first cover has settled")
        return art
    }

    private fun nextCover(app: TestApp, f: Fake, art: Painted, old: ImageBitmap) {
        art.png = png(Color.RED)
        runBlocking { f.player.next() }
        frames(OPEN_FRAMES)
        artAfter(app, old)
        frames(BLUR_FRAMES)
    }

    private fun aloneAtOne(app: TestApp, what: String) {
        val b = app.host.npBackdrop
        val t = rule.mainClock.currentTime
        assertEquals(what, listOf(1f), rule.runOnIdle { b.layers(t) }.map { it.alpha })
        assertFalse(what, rule.runOnIdle { b.fading(t) })
    }

    @Test
    fun aCoverBlurredAgainAtANewSizeShowsWithNoFade() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
        val (app, _) = coverApp()
        val art = settledCover(app)
        val first = app.host.npBackdrop.shown
        rule.runOnIdle { app.np = shorter }
        frames(BLUR_FRAMES)
        assertNotSame("premise: the cover is blurred again", first, app.host.npBackdrop.shown)
        aloneAtOne(app, "the blur at the new scale cuts in")
        artAfter(app, art)
        frames(BLUR_FRAMES)
        aloneAtOne(app, "the new side's blur cuts in")
    }

    @Test
    fun aNewPictureFadesInOverTheShownCover() {
        val painted = Painted(png(Color.BLUE))
        val (app, f) = coverApp(painted)
        val art = settledCover(app)
        nextCover(app, f, painted, art)
        val t = rule.mainClock.currentTime
        val ls = rule.runOnIdle { app.host.npBackdrop.layers(t) }
        assertEquals("the shown cover and the new one", 2, ls.size)
        assertTrue("the new picture fades in", ls[1].alpha < 1f)
        assertTrue(rule.runOnIdle { app.host.npBackdrop.fading(t) })
    }

    @Test
    fun aCoverBlurredAgainDuringItsFadeSwapsInPlace() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
        val painted = Painted(png(Color.BLUE))
        val (app, f) = coverApp(painted)
        val art = settledCover(app)
        nextCover(app, f, painted, art)
        val b = app.host.npBackdrop
        val next = b.shown
        frames((Tok.motion_backdropFade / FRAME_MS / 2).toInt())
        assertTrue("premise: the new cover fades in", rule.runOnIdle { b.fading(rule.mainClock.currentTime) })
        rule.runOnIdle { app.np = shorter }
        frames(BLUR_FRAMES)
        assertNotSame("premise: the cover is blurred again", next, b.shown)
        val t = rule.mainClock.currentTime
        val ls = rule.runOnIdle { b.layers(t) }
        assertEquals("the fade runs on", 2, ls.size)
        assertTrue("the outgoing cover still shows", ls[0].alpha > 0f)
        assertTrue("the new blur still fades in", ls[1].alpha < 1f)
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

    private fun npApp(level: VolumeLevel = Level(START, null), root: Boolean = false): TestApp = TestApp(page, np, null, level) { rule.mainClock.currentTime }.also {
        it.compose(rule, root)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            it.music.np.sync(NpShown.of(listOf(song(1))), 0)
            it.host.resetNp(listOf(TestScreen.Music(MusicEntry(it.stack.entry(), MusicPage.Hub))))
        }
        frames(OPEN_FRAMES)
    }

    private fun npDown(app: TestApp, at: Pair<Float, Float>) = rule.runOnIdle { app.music.np.down(NpLayout(np), at.first, at.second, rule.mainClock.currentTime) }

    private fun idleAlpha(app: TestApp): Float = rule.runOnIdle { app.music.np.idleAlpha(rule.mainClock.currentTime) }

    @Test
    fun aVolumeKeyOnNowPlayingWakesItsIdlePage() {
        val app = npApp(root = true)
        frames(idleFrames)
        assertEquals("premise: the page is idle", Tok.motion_idleAlpha, idleAlpha(app), 0.001f)
        val at = rule.runOnIdle { rule.mainClock.currentTime.also { app.host.volPress(1) } }
        frames(idleFadeFrames)
        assertEquals("the key wakes the page", 1f, idleAlpha(app), 0f)
        assertEquals("the key restarts the idle wait", at + NowPlayingModel.IDLE, app.music.np.idleWake())
    }

    @Test
    fun aVolumeKeyOnNowPlayingDropsASeekAndTheTitleFocus() {
        val app = npApp()
        val m = app.music.np
        npDown(app, titleAt)
        assertTrue("premise: the title down focuses it", m.titleFocus)
        rule.runOnIdle { app.host.volPress(1) }
        frames(OPEN_FRAMES)
        assertFalse("the key clears the title focus", m.titleFocus)
        assertEquals("premise: the plate is up", OverKind.Volume, app.stack.overlay?.kind)
        npDown(app, seekAt)
        assertTrue("premise: the seek band down seeks", m.seeking)
        rule.runOnIdle { app.host.volPress(1) }
        assertFalse("the key drops the seek", m.seeking)
    }

    @Test
    fun aTouchOnThePlateWakesNowPlayingAndKeepsItsFocus() {
        val level = Level(START, null)
        val app = npApp(level, root = true)
        val m = app.music.np
        npDown(app, titleAt)
        rule.runOnIdle { m.tap(NpLayout(np), rule.mainClock.currentTime) }
        frames(idleFrames)
        assertEquals("premise: the page is idle", Tok.motion_idleAlpha, idleAlpha(app), 0.001f)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertTrue("premise: the title keeps its focus under the plate", m.titleFocus)
        rule.onRoot().performTouchInput {
            down(plusAt)
            up()
        }
        frames(idleFadeFrames)
        assertEquals("the plus steps the level", START + STEP, level.pct)
        assertEquals("a plate touch wakes now playing", 1f, idleAlpha(app), 0f)
        assertTrue("a plate touch keeps the title focus", m.titleFocus)
    }

    @Test
    fun aVolumeKeyUnderASheetOnNowPlayingDoesNothing() {
        val level = Level(START, null)
        val app = npApp(level)
        rule.runOnIdle { app.stack.open(Overlay(OverKind.Sheet)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: a sheet is up", OverKind.Sheet, app.stack.overlay?.kind)
        rule.runOnIdle { app.host.volPress(1) }
        frames(OPEN_FRAMES)
        assertEquals("the key under a sheet steps nothing", START, level.pct)
        assertEquals("no plate opens over the sheet", OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun aVolumeKeyDuringAPageMoveDoesNothing() {
        val level = Level(START, null)
        val app = app(level)
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Hub)), MoveKind.T2) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(CUT_FRAMES)
        assertTrue("premise: the push runs", app.stack.phase != Phase.Rest)
        rule.runOnIdle { app.host.volPress(1) }
        frames(OPEN_FRAMES)
        assertEquals("a key during a push steps nothing", START, level.pct)
        rule.runOnIdle {
            app.music.np.sync(NpShown.of(listOf(song(1))), rule.mainClock.currentTime)
            app.host.openNowPlaying()
        }
        frames(CUT_FRAMES)
        assertEquals("premise: the turn into now playing runs", MoveKind.T7, app.stack.turn?.kind)
        rule.runOnIdle { app.host.volPress(1) }
        frames(OPEN_FRAMES)
        assertEquals("a key during the turn into now playing steps", START + STEP, level.pct)
    }

    @Test
    fun aKeyDuringALyricsFadeOpensThePlateWhenTheFadeEnds() {
        val app = lyricsApp(Edit())
        val m = app.music.np
        rule.runOnIdle { app.host.npBack() }
        frames(CUT_FRAMES)
        assertTrue("premise: the lyrics fade runs", rule.runOnIdle { m.lyrics.fading(rule.mainClock.currentTime) })
        rule.runOnIdle { app.host.volPress(1) }
        frames(CUT_FRAMES)
        npDown(app, titleAt)
        var n = 0
        while (rule.runOnIdle { m.lyrics.fading(rule.mainClock.currentTime) } && n < FADE_FRAMES) {
            assertNull("no plate during the fade", app.stack.overlay)
            frames(1)
            n++
        }
        frames(OPEN_FRAMES)
        assertEquals("the plate at the fade's end", OverKind.Volume, app.stack.overlay?.kind)
        assertEquals("the open drops the touch down during the fade", NpAct.None, rule.runOnIdle { m.tap(NpLayout(np), rule.mainClock.currentTime) })
    }

    @Test
    fun aPlatePassDownWakesNowPlaying() {
        val app = npApp(root = true)
        frames(idleFrames)
        assertEquals("premise: the page is idle", Tok.motion_idleAlpha, idleAlpha(app), 0.001f)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the plate is up", OverKind.Volume, app.stack.overlay?.kind)
        rule.onRoot().performTouchInput { down(passAt) }
        frames(idleFadeFrames)
        assertEquals("the passed down wakes now playing", 1f, idleAlpha(app), 0f)
        rule.onRoot().performTouchInput { up() }
        frames(CUT_FRAMES)
    }

    @Test
    fun aMarkTapDuringTheFadeBackLeavesTheFadeRunning() {
        val app = lyricsApp(Edit(), root = true)
        val m = app.music.np
        rule.runOnIdle {
            m.mark = true
            app.host.npBack()
        }
        frames(3)
        rule.onRoot().performTouchInput {
            down(markAt)
            up()
        }
        frames(1)
        assertFalse("the mode stays off", m.lyrics.mode)
        assertTrue("the fade back runs on", rule.runOnIdle { m.lyrics.fading(rule.mainClock.currentTime) })
    }

    @Test
    fun aKeyTurnDuringAMarkTouchDropsTheTouch() {
        val app = lyricsApp(Edit(), mode = false, root = true)
        val m = app.music.np
        rule.runOnIdle { m.mark = true }
        frames(CUT_FRAMES)
        rule.onRoot().performTouchInput { down(markAt) }
        frames(1)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(1)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertTrue("the key turns the mode on", m.lyrics.mode)
        assertNull("no sheet opens", app.stack.overlay)
    }

    private fun npGlow(app: TestApp): Float? = rule.runOnIdle { npTitleGlow(app.music.np, rule.mainClock.currentTime) }

    private fun landed(app: TestApp) {
        var n = 0
        while (app.stack.phase != Phase.Rest && n < 4 * OPEN_FRAMES) {
            frames(1)
            n++
        }
    }

    private fun seekApp(seeks: MutableList<Long>): TestApp {
        val f = testFake(FakeSet.Demo)
        val player = object : Player by f.player {
            override suspend fun seek(ms: Long) {
                seeks += ms
                f.player.seek(ms)
            }
        }
        val ts = runBlocking { f.library.view().page(Listing.Songs, 0, ART_SONGS).rows.map { it.id }.take(2) }
        runBlocking { f.player.play(ts, 0, 0) }
        val src = MusicSource(f.library, player, f.quickplay, f.favorites, f.playlists, f.lyrics)
        return TestApp(page, np, src, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.compose(rule, root = true)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.runOnIdle { it.host.resetNp(listOf(TestScreen.Music(MusicEntry(it.stack.entry(), MusicPage.Hub)))) }
            frames(OPEN_FRAMES)
        }
    }

    @Test
    fun aPushFromNowPlayingDropsItsHeldTouchAndAnUpAfterACloseActsNothing() {
        val seeks = mutableListOf<Long>()
        val app = seekApp(seeks)
        rule.onRoot().performTouchInput { down(titlePx) }
        frames(CUT_FRAMES)
        assertNotNull("premise: the held title glows", npGlow(app))
        rule.runOnIdle { app.host.npTap(NpAct.Queue) }
        frames(CUT_FRAMES)
        assertNull("the push from now playing drops its held title", npGlow(app))
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertFalse("premise: the queue is the top", app.host.isNp(app.stack.top!!))
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(OPEN_FRAMES)
        assertTrue("premise: now playing is the top again", app.host.isNp(app.stack.top!!))
        rule.onRoot().performTouchInput { down(artPx) }
        frames(CUT_FRAMES)
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(CUT_FRAMES)
        assertEquals("premise: the close runs", MoveKind.T8, app.stack.turn?.kind)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertNull("an up after now playing leaves acts nothing", app.stack.overlay)
        assertNull("no glow after the close", npGlow(app))
        landed(app)
        rule.runOnIdle { app.host.openNowPlaying() }
        frames(OPEN_FRAMES)
        landed(app)
        assertTrue("premise: now playing is open again", app.host.isNp(app.stack.top!!))
        rule.onRoot().performTouchInput { down(seekPx) }
        frames(CUT_FRAMES)
        assertTrue("premise: the seek band down seeks", app.music.np.seeking)
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(CUT_FRAMES)
        assertEquals("premise: the second close runs", MoveKind.T8, app.stack.turn?.kind)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertTrue("a seek let go after now playing leaves seeks nothing", seeks.isEmpty())
    }

    @Test
    fun aSeekDragThatOutlivesACloseStartSeeksNothingAtItsEnd() {
        val seeks = mutableListOf<Long>()
        val app = seekApp(seeks)
        val m = app.music.np
        npDown(app, seekAt)
        assertTrue("premise: the seek band down seeks", m.seeking)
        val x = seekPx.x
        rule.runOnIdle {
            assertTrue("premise: the drag takes the seek", app.host.npPan.begin(x, x, seekPx))
            app.host.npPan.drag(x / 2)
        }
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(CUT_FRAMES)
        assertEquals("premise: the close runs", MoveKind.T8, app.stack.turn?.kind)
        rule.runOnIdle { app.host.npPan.end(Swipe.None) }
        frames(OPEN_FRAMES)
        assertTrue("a seek drag ended after now playing leaves seeks nothing", seeks.isEmpty())
        assertFalse("the drag's end still ends the seek", m.seeking)
    }

    private fun rowInk(rest: Bitmap, plate: Bitmap, h: NpHalf = npFlat(NpLayout(np))): Pair<List<Int>, List<Int>> {
        val s = h.g.s
        val box = npStatus(s)
        val ox = np.left - page.left + h.x
        val oy = h.y - h.dy
        val shown = mutableListOf<Int>()
        val over = mutableListOf<Int>()
        for (y in oy until oy + px(box.top + box.height, s.ppu)) for (x in ox + px(box.left, s.ppu) until ox + s.widthPx) {
            val c = rest.getPixel(x, y)
            if (c != Color.WHITE) continue
            shown += c
            over += plate.getPixel(x, y)
        }
        return shown to over
    }

    private fun plateOver(app: TestApp, h: NpHalf = npFlat(NpLayout(np))): Pair<List<Int>, List<Int>> {
        val rest = shot()
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the plate is up", OverKind.Volume, app.stack.overlay?.kind)
        val plate = shot()
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        return rowInk(rest, plate, h)
    }

    @Test
    fun theStatusRowDrawsAboveTheVolumePlatesScrim() {
        val app = npApp(root = true)
        rule.runOnIdle { app.status = StatusState("", HALF_BATTERY, false, Play.Playing, false, -1, -1) }
        frames(CUT_FRAMES)
        val (flat, flatOver) = plateOver(app)
        assertTrue("premise: the status row inks", flat.isNotEmpty())
        assertEquals("the status row draws undimmed above the plate's scrim", flat, flatOver)
        val halfState = StatusState("", HALF_BATTERY, false, Play.Playing, false, -1, -1)
        val halfRest = shot()
        rule.runOnIdle { app.status = StatusState("", LOW_BATTERY, false, Play.Playing, false, -1, -1) }
        frames(CUT_FRAMES)
        val (fuller, lowRest) = rowInk(halfRest, shot())
        assertNotEquals("premise: the half row inks where the low row does not", fuller, lowRest)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.status = halfState }
        frames(CUT_FRAMES)
        val (stepped, steppedOver) = rowInk(halfRest, shot())
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        assertTrue("premise: the stepped row inks", stepped.isNotEmpty())
        assertEquals("the strip above the plate takes a status step", stepped, steppedOver)
        val split = NpSplit.of(window, Nav.None, false, Fold(H / 2, H / 2), np.left)
        assertNotNull("premise: the fold splits now playing", split)
        rule.runOnUiThread { app.split = split }
        frames(OPEN_FRAMES)
        val (half, halfOver) = plateOver(app, split!!.upper)
        assertTrue("premise: the split status row inks", half.isNotEmpty())
        assertEquals("in a split the status row draws above the scrim in its half", half, halfOver)
    }

    private fun hub(app: TestApp) = TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Hub))

    private fun reopen(app: TestApp, text: String?) {
        rule.runOnIdle {
            app.music.np.lyrics.load(1, text, 0)
            app.host.resetNp(listOf(hub(app)))
        }
        frames(OPEN_FRAMES)
    }

    @Test
    fun aPlainTrackOpensOnTheCoverWithTheModeWanted() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        reopen(app, PLAIN)
        assertFalse("a plain track opens on the cover", l.mode)
        assertFalse("with no fade", rule.runOnIdle { l.fading(rule.mainClock.currentTime) })
        assertTrue("the mode stays wanted", l.want)
    }

    @Test
    fun aTrackWithNoLyricsOpensOnTheCover() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        reopen(app, null)
        assertFalse("a track with no lyrics opens on the cover", l.mode)
        assertFalse("with no fade", rule.runOnIdle { l.fading(rule.mainClock.currentTime) })
        assertTrue("the mode stays wanted", l.want)
    }

    @Test
    fun aReopenCutsToTheTarget() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        rule.runOnIdle { l.toggle(rule.mainClock.currentTime) }
        frames(CUT_FRAMES)
        assertFalse("premise: the fade leaves the lyrics", l.mode)
        reopen(app, LRC)
        assertTrue("the reopen cuts to the timed lines", l.mode)
        assertFalse("with no fade", rule.runOnIdle { l.fading(rule.mainClock.currentTime) })
    }

    @Test
    fun theMarkOnAPlainTrackEntersForThatTrackOnly() {
        val app = lyricsApp(Edit(), mode = false)
        val l = app.music.np.lyrics
        rule.runOnIdle { l.load(1, PLAIN, 0) }
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(OPEN_FRAMES)
        assertTrue("the mark enters the plain lyrics", l.mode)
        assertEquals("the entry is for this track", 1L, l.hand)
        assertTrue("the entry wants the mode", l.want)
        reopen(app, PLAIN)
        assertNull("a reopen ends the entry", l.hand)
        assertFalse("the plain track opens on the cover again", l.mode)
    }

    @Test
    fun aHandEntryLeavesWhenItsLyricsGoAndKeepsTheModeWanted() {
        val app = lyricsApp(Edit(), mode = false, root = true)
        val l = app.music.np.lyrics
        rule.runOnIdle { l.load(1, PLAIN, 0) }
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(OPEN_FRAMES)
        assertEquals("premise: a hand entry", 1L, l.hand)
        rule.runOnIdle { l.load(1, null, 0) }
        frames(CUT_FRAMES)
        assertNull("the entry ends when its lyrics go", l.hand)
        assertTrue("the mode stays wanted", l.want)
        settle(app)
        assertFalse("the follow leaves once the lyrics go", mode(app))
    }

    @Test
    fun aCloseTurnsTheShownModeOffAtItsLandingAndClearsTheHand() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        rule.runOnIdle {
            l.hand = 1L
            app.stack.leave({ app.host.isNp(it) }, app.host.popKind)
        }
        frames(CUT_FRAMES)
        assertTrue("premise: the close runs", app.stack.phase != Phase.Rest)
        assertTrue("the shown mode holds through the close", l.mode)
        frames(2 * OPEN_FRAMES)
        assertEquals("premise: the close has landed", Phase.Rest, app.stack.phase)
        assertFalse("premise: now playing is gone", app.stack.pages.any { app.host.isNp(it) })
        assertFalse("the close turns the shown mode off at its landing", l.mode)
        assertNull("the close ends the entry", l.hand)
    }

    @Test
    fun showLyricsWithNoLyricsWantsTheModeAndShowsTheCover() {
        val app = lyricsApp(Edit(), mode = false)
        val l = app.music.np.lyrics
        rule.runOnIdle { l.load(1, null, 0) }
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(CUT_FRAMES)
        assertEquals("the key reports the wanted mode", listOf(true), app.modeChanges)
        assertFalse("the cover stays", l.mode)
        assertEquals(MODE_ON, app.toast.artist)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the plate is up", OverKind.Volume, app.stack.overlay?.kind)
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(CUT_FRAMES)
        assertNull("the key dismisses the plate first", app.stack.overlay)
        frames(TOAST_FRAMES)
        assertEquals(MODE_OFF, app.toast.artist)
        rule.runOnIdle {
            l.load(1, LRC, 0)
            app.host.showLyrics(true)
        }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            l.toggle(rule.mainClock.currentTime)
            l.load(1, null, 0)
        }
        frames(CUT_FRAMES)
        assertTrue("premise: the fade back runs on a track with no lyrics", rule.runOnIdle { l.fading(rule.mainClock.currentTime) && !l.mode })
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(CUT_FRAMES)
        assertFalse("the key lands the fade and keeps the cover", l.mode)
        assertFalse("no fade runs after it", rule.runOnIdle { l.fading(rule.mainClock.currentTime) })
        frames(TOAST_FRAMES)
        assertEquals("and toasts", MODE_ON, app.toast.artist)
    }

    @Test
    fun backInLyricsTurnsTheWantOff() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        assertTrue("premise: the mode is wanted", l.want)
        rule.runOnIdle { app.host.npBack() }
        frames(OPEN_FRAMES)
        assertFalse("back in lyrics turns the want off", l.want)
        assertEquals(listOf(false), app.modeChanges)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(CUT_FRAMES)
        assertTrue("premise: the key runs the fade back in", rule.runOnIdle { l.fading(rule.mainClock.currentTime) && l.mode })
        rule.runOnIdle { app.host.npBack() }
        frames(CUT_FRAMES)
        assertFalse("back during the fade in turns the want off", l.want)
        assertEquals(listOf(false, true, false), app.modeChanges)
    }

    @Test
    fun aPlayOpenOpensOnThePlayedTracksLyrics() {
        val (app, _) = followApp(ArtGate(), listOf(LRC, PLAIN), root = true)
        val l = app.music.np.lyrics
        rule.runOnIdle { app.host.resetNp(listOf(hub(app))) }
        frames(OPEN_FRAMES)
        assertTrue("premise: the timed track shows its lyrics", rule.runOnIdle { l.mode })
        rule.runOnIdle { app.host.npTap(NpAct.Queue) }
        frames(OPEN_FRAMES)
        assertFalse("premise: the queue is over now playing", app.host.isNp(app.stack.top!!))
        rule.runOnIdle { app.host.playOpen(NpForm.Page, keep = true, cut = false) { s -> s.player.next(); true } }
        frames(OPEN_FRAMES)
        landed(app)
        assertTrue("premise: the play pops back to now playing", app.host.isNp(app.stack.top!!))
        assertFalse("the play opens on the played track, a plain track's cover", rule.runOnIdle { l.mode })
        assertFalse("with no fade", rule.runOnIdle { l.fading(rule.mainClock.currentTime) })
    }

    @Test
    fun aResetDuringTheCloseOpensTheNewPageInItsLyrics() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        rule.runOnIdle { app.stack.leave({ app.host.isNp(it) }, app.host.popKind) }
        frames(CUT_FRAMES)
        assertTrue("premise: the close runs", app.stack.phase != Phase.Rest)
        rule.runOnIdle { app.host.resetNp(listOf(hub(app))) }
        frames(OPEN_FRAMES)
        assertTrue("premise: now playing is the top again", app.host.isNp(app.stack.top!!))
        assertTrue("the reset opens the new page in its lyrics", l.mode)
        assertFalse("with no fade", rule.runOnIdle { l.fading(rule.mainClock.currentTime) })
    }

    @Test
    fun showLyricsInLyricsWithAPendingReadWantsTheModeAndStays() {
        val app = lyricsApp(Edit(), mode = false)
        val l = app.music.np.lyrics
        rule.runOnIdle { l.begin(1) }
        frames(CUT_FRAMES)
        rule.runOnIdle { l.toggle(rule.mainClock.currentTime) }
        frames(FADE_FRAMES)
        assertTrue("premise: the lyrics show unwanted while the read is pending", rule.runOnIdle { l.mode && !l.want && l.pending })
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(CUT_FRAMES)
        assertTrue("the key keeps the lyrics", l.mode)
        assertTrue("and wants the mode", l.want)
        assertEquals(listOf(true), app.modeChanges)
    }

    @Test
    fun aBackInThePhoneVariantReportsNoOwnMode() {
        val pl = Copy()
        val app = phoneApp(pl)
        val l = app.music.np.lyrics
        rule.runOnIdle { l.wanted(true) }
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(FADE_FRAMES)
        assertTrue("premise: the phone lyrics show", l.phoneMode)
        rule.runOnIdle { app.host.npBack() }
        frames(FADE_FRAMES)
        assertTrue("a back in the variant reports no own mode", app.modeChanges.isEmpty())
        assertTrue("and keeps the own mode wanted", l.want)
    }

    @Test
    fun aResetUnderNowPlayingInLyricsKeepsTheMode() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        rule.runOnIdle { app.host.resetNp(listOf(hub(app))) }
        frames(OPEN_FRAMES)
        assertTrue("a reset keeps the lyrics", l.mode)
        assertFalse("with no fade", rule.runOnIdle { l.fading(rule.mainClock.currentTime) })
    }

    private fun change(app: TestApp, id: Long, text: String?, lands: Boolean = true) {
        rule.runOnIdle {
            val m = app.music.np
            m.sync(NpShown.of(listOf(song(id))), rule.mainClock.currentTime)
            m.lyrics.begin(id)
            if (lands) m.lyrics.load(id, text, 0)
        }
    }

    private fun mode(app: TestApp): Boolean = rule.runOnIdle { app.music.np.lyrics.mode }

    private fun fading(app: TestApp): Boolean = rule.runOnIdle { app.music.np.lyrics.fading(rule.mainClock.currentTime) }

    private fun settle(app: TestApp) {
        frames(CUT_FRAMES)
        var n = 0
        while (fading(app) && n < FADE_FRAMES) {
            frames(1)
            n++
        }
        frames(CUT_FRAMES)
    }

    @Test
    fun aPendingReadOpensOnTheCoverThenTimedLinesFadeIn() {
        val app = lyricsApp(Edit())
        change(app, 2, null, lands = false)
        rule.runOnIdle { app.host.resetNp(listOf(hub(app))) }
        frames(OPEN_FRAMES)
        assertFalse("a pending read opens on the cover", mode(app))
        rule.runOnIdle { app.music.np.lyrics.load(2, LRC, 0) }
        frames(CUT_FRAMES)
        assertTrue("the timed lines fade in", mode(app) && fading(app))
    }

    @Test
    fun aTrackChangeToPlainFadesToTheCover() {
        val app = lyricsApp(Edit())
        change(app, 2, PLAIN)
        frames(CUT_FRAMES)
        assertTrue("a change to a plain track fades to the cover", !mode(app) && fading(app))
    }

    @Test
    fun aTrackChangeBackToTimedLinesFadesIn() {
        val app = lyricsApp(Edit())
        change(app, 2, PLAIN)
        settle(app)
        assertFalse("premise: the plain track shows the cover", mode(app))
        change(app, 1, LRC)
        frames(CUT_FRAMES)
        assertTrue("the timed lines fade back in", mode(app) && fading(app))
    }

    @Test
    fun aTrackChangeBetweenTwoTimedTracksKeepsTheLyricsWithNoFade() {
        val app = lyricsApp(Edit())
        rule.runOnIdle { app.music.np.sync(NpShown.of(listOf(song(2))), rule.mainClock.currentTime) }
        repeat(CUT_FRAMES) {
            frames(1)
            assertTrue("the lyrics hold while the read is the old track's", mode(app) && !fading(app))
        }
        rule.runOnIdle { app.music.np.lyrics.begin(2) }
        repeat(CUT_FRAMES) {
            frames(1)
            assertTrue("the lyrics hold while the read is pending", mode(app) && !fading(app))
        }
        rule.runOnIdle { app.music.np.lyrics.load(2, LRC, 0) }
        repeat(CUT_FRAMES) {
            frames(1)
            assertTrue("the lyrics hold through the read", mode(app) && !fading(app))
        }
    }

    @Test
    fun aLeaveFadeThenATimedTrackFollowsAfterTheFade() {
        val app = lyricsApp(Edit(), root = true)
        change(app, 2, PLAIN)
        frames(CUT_FRAMES)
        assertTrue("premise: the leave fade runs", !mode(app) && fading(app))
        change(app, 1, LRC)
        frames(CUT_FRAMES)
        assertFalse("the follow waits for the fade", mode(app))
        settle(app)
        assertTrue("the follow enters once the leave ends", mode(app))
    }

    @Test
    fun aFingerHeldThroughATrackChangeFollowsAtTheUp() {
        val app = lyricsApp(Edit(), root = true)
        rule.onRoot().performTouchInput { down(ringPx) }
        frames(CUT_FRAMES)
        change(app, 2, PLAIN)
        frames(OPEN_FRAMES)
        assertTrue("the mode holds while the finger rests", mode(app))
        val asked = (1..OPEN_FRAMES).count {
            frames(1)
            rule.runOnIdle { Recomposer.runningRecomposers.value.any { r -> r.hasPendingWork } }
        }
        assertEquals("no frame is asked while the finger rests", 0, asked)
        rule.onRoot().performTouchInput { up() }
        frames(CUT_FRAMES)
        assertFalse("the follow leaves at the up", mode(app))
    }

    @Test
    fun aTrackChangeInTheEnterFadeFinishesThenFollows() {
        val app = lyricsApp(Edit(), mode = false, root = true)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(CUT_FRAMES)
        assertTrue("premise: the enter fade runs", mode(app) && fading(app))
        change(app, 2, PLAIN)
        frames(CUT_FRAMES)
        assertTrue("the enter fade runs on", mode(app))
        settle(app)
        assertFalse("then the follow leaves", mode(app))
    }

    @Test
    fun theFollowReportsNothing() {
        val app = lyricsApp(Edit())
        change(app, 2, PLAIN)
        settle(app)
        assertFalse("premise: the follow left the plain track", mode(app))
        change(app, 1, LRC)
        settle(app)
        assertTrue("premise: the follow ran both ways", mode(app))
        assertTrue("the follow reports nothing", app.modeChanges.isEmpty())
    }

    @Test
    fun aKeyDuringALyricsFadeThatATrackChangeEndsOpensThePlateAfterTheFollowsFade() {
        val app = lyricsApp(Edit(), mode = false, root = true)
        rule.runOnIdle { app.host.showLyrics(true) }
        frames(CUT_FRAMES)
        assertTrue("premise: the enter fade runs", mode(app) && fading(app))
        change(app, 2, PLAIN)
        rule.runOnIdle { app.host.volPress(1) }
        frames(CUT_FRAMES)
        assertNull("premise: the key waits for the fade", app.stack.overlay)
        var n = 0
        while (app.stack.overlay == null && n < 3 * FADE_FRAMES) {
            frames(1)
            n++
        }
        assertEquals("premise: the key opens the plate", OverKind.Volume, app.stack.overlay?.kind)
        assertFalse("the plate opens with no lyrics fade running", fading(app))
        assertFalse("after the follow left the plain track's lyrics", mode(app))
    }

    @Test
    fun theFollowWaitsForAPlateWhoseOpenIsAsked() {
        val app = lyricsApp(Edit())
        val l = app.music.np.lyrics
        rule.runOnIdle {
            app.host.openVolume(rule.mainClock.currentTime)
            l.load(1, PLAIN, 0)
        }
        frames(OPEN_FRAMES)
        assertEquals("premise: the plate is up", OverKind.Volume, app.stack.overlay?.kind)
        assertTrue("the follow waits for the plate", mode(app) && !fading(app))
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        settle(app)
        assertFalse("and leaves once the plate is gone", mode(app))
    }

    @Test
    fun theFollowDoesNothingInThePhoneVariant() {
        val pl = Copy()
        val app = phoneApp(pl)
        val l = app.music.np.lyrics
        rule.runOnIdle {
            l.wanted(true)
            l.begin(app.music.np.shown.track!!)
            l.load(app.music.np.shown.track!!, LRC, 0)
        }
        frames(OPEN_FRAMES)
        assertTrue("premise: the mode is wanted", l.want)
        assertFalse("the follow holds the mode in the variant", mode(app))
        assertFalse("and runs no fade", fading(app))
    }

    private class ArtGate {
        var hold: CompletableDeferred<Unit>? = null
        var fails = false
    }

    private fun followApp(gate: ArtGate, texts: List<String?>, root: Boolean = false): Pair<TestApp, Fake> {
        val f = testFake(FakeSet.Demo)
        val cover = png(Color.BLUE)
        fun painted(v: LibraryView): LibraryView = object : LibraryView by v {
            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray {
                gate.hold?.await()
                if (gate.fails) throw IOException("art read failed")
                return cover
            }
        }
        val lib = object : Library by f.library {
            override fun view(): LibraryView = painted(f.library.view())

            override fun view(generation: Long): LibraryView = painted(f.library.view(generation))
        }
        val ts = runBlocking { f.library.view().page(Listing.Songs, 0, ART_SONGS).rows.filter { it.art != null }.map { it.id }.take(texts.size) }
        val byTrack = ts.zip(texts).toMap()
        val lyrics = object : Lyrics by f.lyrics {
            override suspend fun lyrics(track: Long): String? = byTrack[track]

            override suspend fun copy(track: Long): LyricCopy? = byTrack[track]?.let { LyricCopy(it, null) }
        }
        runBlocking { f.player.play(ts, 0, 0) }
        val src = MusicSource(lib, f.player, f.quickplay, f.favorites, f.playlists, lyrics)
        return TestApp(page, np, src, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.lyricsOn = true
            it.modeAtStart = true
            it.music.np.workContext = Dispatchers.Unconfined
            it.compose(rule, root)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.runOnIdle { it.host.resetNp(listOf(hub(it))) }
            frames(OPEN_FRAMES)
            frames(FADE_FRAMES)
        } to f
    }

    private fun skip(f: Fake, d: Int) {
        runBlocking { if (d > 0) f.player.next() else f.player.prev() }
        rule.waitForIdle()
        frames(OPEN_FRAMES)
    }

    private fun artSettled(app: TestApp) {
        rule.waitUntil(WAIT_MS) { rule.runOnIdle { app.music.np.artFor == app.music.np.shown.track } }
        frames(CUT_FRAMES)
    }

    @Test
    fun aHandEntryEndsWhenItsTrackLeavesEvenUnderASheet() {
        val app = lyricsApp(Edit(), mode = false)
        val l = app.music.np.lyrics
        rule.runOnIdle { l.load(1, PLAIN, 0) }
        frames(CUT_FRAMES)
        rule.runOnIdle { app.host.npTap(NpAct.Lyrics) }
        frames(OPEN_FRAMES)
        assertEquals("premise: a hand entry", 1L, l.hand)
        rule.runOnIdle { app.stack.open(Overlay(OverKind.Sheet)) }
        frames(OPEN_FRAMES)
        change(app, 2, LRC)
        frames(CUT_FRAMES)
        change(app, 1, PLAIN)
        frames(CUT_FRAMES)
        assertNull("the entry ends once its track leaves, under a sheet too", l.hand)
    }

    @Test
    fun aLeaveWaitsForTheCoversArtToLand() {
        val gate = ArtGate()
        val (app, f) = followApp(gate, listOf(LRC, PLAIN))
        assertTrue("premise: the timed track shows its lyrics", mode(app))
        val hold = CompletableDeferred<Unit>()
        gate.hold = hold
        skip(f, 1)
        assertTrue("the lyrics hold while the cover's art read runs", mode(app))
        hold.complete(Unit)
        artSettled(app)
        assertFalse("the follow leaves once the art lands", mode(app))
    }

    @Test
    fun aFailedArtReadDuringALeaveLetsTheFollowLeave() {
        val gate = ArtGate()
        val (app, f) = followApp(gate, listOf(LRC, PLAIN))
        assertTrue("premise: the timed track shows its lyrics", mode(app))
        val hold = CompletableDeferred<Unit>()
        gate.hold = hold
        gate.fails = true
        skip(f, 1)
        hold.complete(Unit)
        artSettled(app)
        assertFalse("the failed read settles the art and the follow leaves", mode(app))
    }

    @Test
    fun aReturnToATrackAfterAFailedArtReadShowsItsArtAgain() {
        val gate = ArtGate()
        val (app, f) = followApp(gate, listOf(LRC, LRC, PLAIN))
        artSettled(app)
        assertNotNull("premise: the first track's art shows", rule.runOnIdle { app.music.np.art })
        gate.fails = true
        skip(f, 1)
        artSettled(app)
        assertNull("premise: the failed read shows no art", rule.runOnIdle { app.music.np.art })
        gate.fails = false
        skip(f, -1)
        artSettled(app)
        assertNotNull("the first track's art shows again", rule.runOnIdle { app.music.np.art })
        skip(f, 1)
        artSettled(app)
        skip(f, 1)
        artSettled(app)
        settle(app)
        assertFalse("a change from it to a plain track leaves", mode(app))
    }

    @Test
    fun twoKeysInOneIdleBlockOpenOnePlateAndStepTwice() {
        val level = Level(START, null)
        val app = npApp(level)
        rule.runOnIdle {
            app.host.volPress(1)
            app.host.volPress(1)
        }
        frames(OPEN_FRAMES)
        assertEquals("the keys open the plate", OverKind.Volume, app.stack.overlay?.kind)
        assertEquals("both keys step", START + 2 * STEP, level.pct)
    }

    @Test
    fun twoKeysInsideThePlatesOpenBothStep() {
        val level = Level(START, null)
        val app = npApp(level)
        rule.runOnIdle { app.host.volPress(1) }
        frames(1)
        assertTrue("premise: the plate's open runs at rest", app.stack.moving && app.stack.phase == Phase.Rest)
        rule.runOnIdle { app.host.volPress(1) }
        frames(OPEN_FRAMES)
        assertEquals("both keys step", START + 2 * STEP, level.pct)
    }

    private class HeldPlayer(base: Player, st: PlayerState) : Player by base {
        override val state = MutableStateFlow(st)
        val seeks = mutableListOf<Long>()

        override suspend fun seek(ms: Long) {
            seeks += ms
            state.value = state.value.copy(anchor = Anchor(ms, 1000, 0, false, state.value.durationMs))
        }
    }

    private fun holdApp(): Pair<TestApp, HeldPlayer> {
        val f = testFake(FakeSet.Demo)
        val id = runBlocking { f.library.view().page(Listing.Songs, 0, ART_SONGS).rows.first().id }
        val p = HeldPlayer(f.player, PlayerState(queueSize = 3, index = 0, track = id, playing = true, anchor = Anchor(HOLD_POS, 1000, 0, false, HOLD_DUR)))
        val src = MusicSource(f.library, p, f.quickplay, f.favorites, f.playlists, f.lyrics)
        val app = TestApp(page, np, src, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.compose(rule, root = true)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
            rule.runOnIdle { it.host.resetNp(listOf(TestScreen.Music(MusicEntry(it.stack.entry(), MusicPage.Hub)))) }
            frames(OPEN_FRAMES)
        }
        return app to p
    }

    private val nextAt = Offset((Tok.canvasW / 2 + Tok.vol_skipX) * np.ppu + (np.left - page.left), np.board.vol_ringY * np.ppu)

    private fun secs(t: String): Long = t.split(":").let { it[0].toLong() * 60 + it[1].toLong() }

    private fun heldNext(app: TestApp): Long {
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the plate is up", OverKind.Volume, app.stack.overlay?.kind)
        val down = rule.mainClock.currentTime
        rule.onRoot().performTouchInput { down(nextAt) }
        frames(HOLD_FRAMES)
        return down
    }

    @Test
    fun aHeldNextOnThePlateStepsTheRampAndSeeks() {
        val (app, p) = holdApp()
        heldNext(app)
        assertEquals("the hold's first step seeks a tick step on (funeOS player/ui/app.cpp App::seek_start :15464-15494, seek_tick :15496-15576, v0.4.4)", HOLD_POS + 1000, p.seeks.firstOrNull())
        frames(HOLD_FRAMES)
        assertTrue("the ramp seeks on at the tick rate, ${p.seeks}", p.seeks.size >= 2 && p.seeks.zipWithNext().all { (a, b) -> b > a })
        assertTrue("the readout leads the last seek, ${app.host.hud.seekLeft}", secs(app.host.hud.seekLeft) * 1000 >= p.seeks.last())
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
    }

    @Test
    fun aReleaseTakesOneLastStepAndNoMore() {
        val (app, p) = holdApp()
        heldNext(app)
        frames(TICK_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(PRESS_OFF_FRAMES)
        val last = app.host.hud.seekLeft
        assertEquals("the one last step is seeked", secs(last) * 1000, p.seeks.last())
        val n = p.seeks.size
        frames(OPEN_FRAMES)
        assertEquals("no step after the last one, ${p.seeks}", n, p.seeks.size)
        assertEquals(last, app.host.hud.seekLeft)
    }

    @Test
    fun theStatusRowShowsTheSeekStateDuringAHold() {
        val (app, _) = holdApp()
        assertEquals("premise: no seek state", Play.None, app.host.npStatus?.state?.play)
        heldNext(app)
        frames(CUT_FRAMES)
        assertEquals("funeOS player/ui/nowplaying.cpp status_play, player/ui/rows.cpp draw_status :1275-1284, v0.4.4", Play.SeekForward, app.host.npStatus?.state?.play)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals("the end clears the seek state", Play.None, app.host.npStatus?.state?.play)
    }

    @Test
    fun aSeekKeyHeldOnNowPlayingOpensThePlate() {
        val (app, p) = holdApp()
        var down = true
        rule.runOnIdle { app.host.seekKey(1) { down } }
        frames(OPEN_FRAMES)
        assertEquals("a held key on a settled now playing opens the plate (funeOS player/ui/app.cpp App::seek_start :15475-15480, v0.4.4)", OverKind.Volume, app.stack.overlay?.kind)
        assertTrue("the key hold seeks, ${p.seeks}", p.seeks.isNotEmpty())
        down = false
        frames(OPEN_FRAMES)
        val n = p.seeks.size
        frames(OPEN_FRAMES)
        assertEquals("a key read up ends the hold", n, p.seeks.size)
    }

    @Test
    fun aHeldNextOnThePlateMovesTheNowPlayingTimes() {
        val (app, _) = holdApp()
        heldNext(app)
        frames(TICK_FRAMES)
        assertTrue("the page's times follow the ramp, ${app.music.np.keySeekPos}", app.music.np.keySeeking && app.music.np.keySeekPos > HOLD_POS)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertFalse("the end gives the times back to the player", app.music.np.keySeeking)
    }

    @Test
    fun aSeekTickOnNowPlayingWakesItsIdle() {
        val (app, _) = holdApp()
        val down = heldNext(app)
        frames(TICK_FRAMES)
        val wake = app.music.np.idleWake()
        assertTrue(
            "the tick stamps the idle later than the glyph down, $wake against ${down + NowPlayingModel.IDLE} (funeOS player/ui/app.cpp App::seek_tick :15505-15506, v0.4.4)",
            wake > down + NowPlayingModel.IDLE + Tok.seekHoldMs.toLong() - FRAME_MS,
        )
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
    }

    @Test
    fun aHeldGlyphOnAPhoneVariantSkipsNothing() {
        val (app, p) = holdApp()
        var skips = 0
        val track = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null)
        rule.runOnIdle {
            p.state.value = p.state.value.copy(playing = false)
            app.phone.value = PhoneSession(track, {}) { skips++ }
        }
        frames(OPEN_FRAMES)
        assertTrue("premise: the phone is the variant", rule.runOnIdle { app.music.np.variant })
        heldNext(app)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals("a held glyph on the phone's track skips nothing (funeOS player/ui/app.cpp App::vol_glyph_hold :15455-15462, v0.4.4)", 0, skips)
        assertEquals("and seeks nothing", emptyList<Long>(), p.seeks)
    }

    @Test
    fun aPointerCancelMidHoldEndsTheHold() {
        val (app, p) = holdApp()
        heldNext(app)
        frames(TICK_FRAMES)
        rule.onRoot().performTouchInput { cancel() }
        frames(PRESS_OFF_FRAMES)
        val n = p.seeks.size
        frames(OPEN_FRAMES)
        assertEquals("a cancelled touch ends the glyph hold, ${p.seeks}", n, p.seeks.size)
    }

    @Test
    fun aCancelledTouchOnThePlateActsNothing() {
        val level = Level(START, null)
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        rule.onRoot().performTouchInput {
            down(plusAt)
            cancel()
        }
        frames(OPEN_FRAMES)
        assertEquals("a cancel is no tap on the plus", START, level.pct)
    }

    @Test
    fun aPlateClosedMidHoldTakesOneLastStepThenNoMore() {
        val (app, p) = holdApp()
        heldNext(app)
        frames(TICK_FRAMES)
        rule.runOnIdle { app.stack.close() }
        frames(CLOSE_FRAMES)
        val n = p.seeks.size
        assertTrue("the close's release takes one last step within the plate's wake, volumePressOff and a frame, ${p.seeks}", n >= 2 && p.seeks[n - 1] > p.seeks[n - 2])
        frames(OPEN_FRAMES)
        assertEquals("the plate's close ends the glyph hold, ${p.seeks}", n, p.seeks.size)
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aHostDisposedMidHoldEndsIt() {
        val (app, _) = holdApp()
        rule.runOnIdle { app.host.seekKey(1) { true } }
        frames(OPEN_FRAMES)
        assertTrue("premise: the key hold runs", app.host.seekOn)
        rule.runOnIdle { app.hosted = false }
        frames(CUT_FRAMES)
        assertFalse("the host's dispose ends the hold", app.host.seekOn)
    }

    @Test
    fun aLifecycleStopMidHoldEndsIt() {
        val (app, _) = holdApp()
        rule.runOnIdle { app.host.seekKey(1) { true } }
        frames(OPEN_FRAMES)
        assertTrue("premise: the key hold runs", app.host.seekOn)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse("a lifecycle stop ends the hold", app.host.seekOn)
    }

    private fun coverApp(gate: ArtGate, playing: Boolean = true): TestApp {
        val f = testFake(FakeSet.Demo)
        val cover = png(Color.BLUE)
        fun painted(v: LibraryView): LibraryView = object : LibraryView by v {
            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray {
                gate.hold?.await()
                return cover
            }
        }
        val lib = object : Library by f.library {
            override fun view(): LibraryView = painted(f.library.view())

            override fun view(generation: Long): LibraryView = painted(f.library.view(generation))
        }
        val ts = runBlocking { f.library.view().page(Listing.Songs, 0, ART_SONGS).rows.filter { it.art != null }.map { it.id }.take(2) }
        runBlocking {
            f.player.play(ts, 0, 0)
            if (!playing) f.player.pause()
        }
        val src = MusicSource(lib, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)
        return TestApp(page, np, src, Level(START, null)) { rule.mainClock.currentTime }.also {
            it.music.np.workContext = Dispatchers.Unconfined
            it.compose(rule, root = true)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
        }
    }

    private fun lateCoverAtRest(asleep: Boolean = false): TestApp {
        val gate = ArtGate().apply { hold = CompletableDeferred() }
        val app = coverApp(gate, playing = !asleep)
        rule.runOnIdle { app.host.resetNp(listOf(hub(app))) }
        frames(if (asleep) OPEN_FRAMES + idleFrames else OPEN_FRAMES)
        assertFalse("premise: no cover yet", app.music.np.artPending)
        if (asleep) {
            val t = rule.mainClock.currentTime
            assertTrue("premise: the page sleeps", rule.runOnIdle { !app.music.np.animating(t) && app.music.np.wakeAt(t) == null })
        }
        rule.runOnIdle { gate.hold?.complete(Unit) }
        artSettled(app)
        frames(TICK_FRAMES)
        return app
    }

    @Test
    fun aCoverLandingAtRestFades() {
        val app = lateCoverAtRest()
        assertTrue(
            "the resting page starts the late cover's fade (funeOS player/ui/nowplaying.cpp NowPlaying::art_tile :418-427, NowPlaying::art_in :374-389, v0.4.4)",
            app.music.np.artStart >= 0,
        )
        val mid = rule.runOnIdle { app.music.np.artAlpha(1f, app.music.np.artStart + Tok.motion_t7Fade.toLong() / 2) }
        assertTrue("the resting draw takes the fade's alpha mid fade, $mid", mid > 0f && mid < 1f)
        frames(ART_FADE_FRAMES)
        assertFalse("the fade ends at rest", app.music.np.artPending)
    }

    @Test
    fun aCoverLandingOnASleepingPageWakesItsFade() {
        val app = lateCoverAtRest(asleep = true)
        assertTrue(
            "the late cover wakes the sleeping page into its fade (funeOS player/ui/nowplaying.cpp NowPlaying::art_tile :418-427, NowPlaying::art_in :374-389, v0.4.4)",
            app.music.np.artStart >= 0,
        )
        frames(ART_FADE_FRAMES)
        assertFalse("the fade ends on the sleeping page", app.music.np.artPending)
    }

    @Test
    fun aLeaveAfterALateCoverKeepsTheCover() {
        val app = lateCoverAtRest()
        frames(ART_FADE_FRAMES)
        rule.runOnIdle { app.host.npBack() }
        frames(CUT_FRAMES)
        assertEquals("the leave draws the cover whole", 1f, rule.runOnIdle { app.music.np.artIn(rule.mainClock.currentTime) }, 0f)
    }

    @Test
    fun aSlowCoverOnAnNpEnterKeepsFadingAcrossTheLanding() {
        val gate = ArtGate().apply { hold = CompletableDeferred() }
        val app = coverApp(gate)
        rule.runOnIdle { app.host.goNp() }
        frames(ENTER_FRAMES)
        rule.runOnIdle { gate.hold?.complete(Unit) }
        artSettled(app)
        frames(TICK_FRAMES)
        assertTrue("premise: the fade starts in the enter", app.music.np.artStart >= 0)
        frames(OPEN_FRAMES + ART_FADE_FRAMES)
        assertFalse("the fade runs on at rest and ends", app.music.np.artPending)
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
        private const val STACKED = 3
        private const val MAX_FRAMES = 400
        private const val BLUR_FRAMES = 4
        private const val COVER = 64
        private const val DURATION = 180_000L
        private const val H_SHORT = 1080
        private const val ART_SONGS = 20
        private const val WAIT_MS = 5_000L
        private const val FRAME_MS = 16
        private const val HOLD_FRAMES = 27
        private const val TICK_FRAMES = 3
        private const val CLOSE_FRAMES = 11
        private const val ART_FADE_FRAMES = 23
        private const val ENTER_FRAMES = 25
        private const val PRESS_OFF_FRAMES = 9
        private const val HOLD_POS = 216000L
        private const val HOLD_DUR = 377000L
        private const val CARRIED = 500
        private const val CARRIED_COPY = "[00:00.50]one\n[00:01.50]two"
        private const val STEP_MS = 100
        private const val COARSE_MS = 1000
        private const val TYPED_MS = 1500
        private const val APP_MS = 700
        private const val MIN_SHIFT = 10
        private const val COL = 2
        private const val WHITE = 200
        private const val RESIZE_FRAMES = 2
        private const val TOAST_FRAMES = 20
        private const val ALBUM = 20L
        private const val STALE = 7L
        private const val TOP_Y = 40f
        private const val FADE_FRAMES = 60
        private const val PLATE_FRAMES = 400
        private const val POSITION = 5000L
        private const val RING_ONE_X = 120f
        private const val RING_Y = 398f
        private const val LRC = "[00:01.00]one\n[00:02.00]two"
        private const val TITLE_DX = 20f
        private const val HALF_BATTERY = 50
        private const val LOW_BATTERY = 20
        private const val PLAIN = "a plain line"
        private const val MODE_ON = "lyrics mode on"
        private const val MODE_OFF = "lyrics mode off"
        private const val TITLE_DY = 4f
    }
}
