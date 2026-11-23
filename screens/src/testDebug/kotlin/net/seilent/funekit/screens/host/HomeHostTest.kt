package net.seilent.funekit.screens.host

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.LibraryState
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.pins
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.home.HomeEntry
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.quickplay.QpData
import net.seilent.funekit.kit.quickplay.QpItem
import net.seilent.funekit.kit.quickplay.QpKind
import net.seilent.funekit.kit.quickplay.QpLayout
import net.seilent.funekit.kit.quickplay.QuickplayState
import net.seilent.funekit.kit.quickplay.peekHit
import net.seilent.funekit.kit.quickplay.qpTiles
import net.seilent.funekit.kit.quickplay.quickplay
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.overlay.ADD
import net.seilent.funekit.screens.music.overlay.ADD_TO_PLAYLIST
import net.seilent.funekit.screens.music.overlay.GO_TO_ALBUM
import net.seilent.funekit.screens.music.overlay.INFO
import net.seilent.funekit.screens.music.overlay.PLAY
import net.seilent.funekit.screens.music.overlay.PLAY_NEXT
import net.seilent.funekit.screens.music.overlay.SHUFFLE
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.UNPIN
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class HomeHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private data class Entry(override val word: String, override val sub: String) : HomeEntry

    private val none = Insets(0, 0, 0, 0)
    private val a105 = Window(720, 1280, 35, none, none, none, none, none, Dpi(406.4f, 406.4f, 360, 720, 1280, 1, 720, 1280))
    private val home = scale(a105, Nav.None, Surface.Home, false)
    private val a105Page: Scale = scale(a105, Nav.None, Surface.Page, false)
    private val np: Scale = scale(a105, Nav.None, Surface.NowPlaying, false)
    private val holdMs = Tok.input_keyHoldMs.toLong()
    private val entries = listOf(Entry("music", "albums artists"), Entry("playlists", "now playing saved"), Entry("history", "recent new"), Entry("settings", "output library"))
    private lateinit var app: TestApp
    private lateinit var rig: TestHome
    private val stack get() = app.stack
    private val gate get() = app.gate
    private val state: HomeState get() = checkNotNull(rig.state)

    private val fake = testFake(FakeSet.Demo)
    private val noPins = testFake(FakeSet.Demo, quickplay = false)
    private var reply: CompletableDeferred<Unit>? = null
    private var cold = false
    private var qpPlays = true
    private val zero = MutableStateFlow(0L)

    private val player = object : Player by fake.player {
        override suspend fun play(ids: List<Long>, index: Int, posMs: Long) {
            reply?.await()
            fake.player.play(ids, index, posMs)
        }
    }

    private val library = object : Library by fake.library {
        override val generation: StateFlow<Long> get() = if (cold) zero else fake.library.generation
    }

    private val music = MusicSource(library, player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)
    private val played = mutableListOf<QpItem>()

    private fun qpSource(data: QpData) = object : QpSource {
        override val changes: Flow<Any> = flowOf(Unit)

        override suspend fun load(tiles: Int): QpData = data

        override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? = null

        override suspend fun play(item: QpItem): Boolean {
            played += item
            if (!qpPlays) return false
            player.play(listOf(item.id), 0, 0)
            return true
        }
    }

    private fun launcher(qp: QpSource?, src: MusicSource = music, listed: List<HomeEntry> = entries) {
        rig = TestHome(home, listed, qp)
        app = TestApp(a105Page, np, src, StillLevel) { SystemClock.uptimeMillis() }
        app.homeHost = rig
        app.compose(rule, root = true)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        settle()
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun sync() {
        if (rule.mainClock.currentTime < SystemClock.uptimeMillis()) rule.mainClock.advanceTimeBy(SystemClock.uptimeMillis() - rule.mainClock.currentTime)
        val d = rule.mainClock.currentTime - SystemClock.uptimeMillis()
        if (d > 0) ShadowSystemClock.advanceBy(Duration.ofMillis(d))
    }

    private fun frame() {
        apply()
        rule.mainClock.advanceTimeByFrame()
        sync()
    }

    private fun frames(ms: Long) {
        val end = rule.mainClock.currentTime + ms
        while (rule.mainClock.currentTime < end) frame()
    }

    private fun until(what: String, ok: () -> Boolean) {
        var n = 0
        while (!ok()) {
            frame()
            assertTrue(what, ++n < MAX_FRAMES)
        }
    }

    private fun settle() {
        apply()
        rule.mainClock.advanceTimeBy(SETTLE, ignoreFrameDuration = true)
        apply()
        rule.mainClock.advanceTimeByFrame()
        sync()
    }

    private fun step(ms: Long) {
        apply()
        rule.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)
        apply()
    }

    private fun send(action: Int, x: Float, y: Float, down: Long) {
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }
        gate.touch(e) { rule.activity.dispatchTouchEvent(it) }
        e.recycle()
    }

    private fun tap(x: Float, y: Float) {
        sync()
        val d = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x, y, d)
        ShadowSystemClock.advanceBy(Duration.ofMillis(TAP_MS))
        send(MotionEvent.ACTION_UP, x, y, d)
        apply()
    }

    private fun npEntries(): Int = stack.pages.count(app.host::isNp)

    private fun npOnTop(): Boolean = stack.phase == Phase.Rest && stack.top?.let(app.host::isNp) == true

    private fun showFront(): QuickplayState {
        val peek = peekHit(home)
        tap((peek.x0 + peek.x1) / 2f, (peek.y0 + peek.y1) / 2f)
        settle()
        settle()
        val qp = state.quickplay
        assertTrue("premise: quickplay is in front", qp.pan.front && qp.pan.still)
        return qp
    }

    private fun front(data: QpData): QuickplayState {
        launcher(qpSource(data))
        return showFront()
    }

    private fun tileAt(data: QpData, i: Int): Offset {
        val r = QpLayout(home, qpTiles(data), 0).rect(i, 0L)
        return Offset((r.x0 + r.x1) / 2f, (r.y0 + r.y1) / 2f)
    }

    private fun tapTile(data: QpData, i: Int) {
        val o = tileAt(data, i)
        tap(o.x, o.y)
    }

    private fun songs(n: Int): List<Long> = runBlocking { fake.library.view().page(Listing.Songs, 0, n).rows.map { it.id } }

    private fun songData(ids: List<Long>) = QpData(null, ids.mapIndexed { k, id -> QpItem(QpKind.Song, id, "t$k", null) }, emptyList(), emptyList(), 1L)

    private fun armed() {
        until("the tile play starts T7") { stack.turn?.kind == MoveKind.T7 }
        assertTrue("premise: the play open waits for its reply", stack.armed)
    }

    private fun swapped(): QuickplayState {
        val first = state.quickplay
        rule.runOnUiThread { rig.source = qpSource(QpData.EMPTY) }
        settle()
        val next = state.quickplay
        assertNotSame("premise: the source swap makes a new quickplay state", first, next)
        return next
    }

    private fun items(n: Int, kind: QpKind) = List(n) { QpItem(kind, it.toLong(), "t$it", null) }

    @Test
    fun aQuickplayTilePlayInFrontOpensNowPlayingWithTheFrontForm() {
        val ids = songs(TILES)
        val data = songData(ids)
        reply = CompletableDeferred()
        front(data)
        tapTile(data, 1)
        armed()
        assertEquals(NpForm.Front, app.movePaint.npForm)
        reply!!.complete(Unit)
        until("the tile play lands on now playing") { npOnTop() && !stack.armed }
        assertEquals(ids[1], fake.player.state.value.track)
    }

    @Test
    fun aTilePlayThatPlaysNothingTurnsBackWithNoNowPlaying() {
        val ids = songs(TILES)
        runBlocking { fake.player.play(listOf(ids[0]), 0, 0) }
        qpPlays = false
        val data = songData(ids)
        front(data)
        tapTile(data, 1)
        until("premise: the tile was asked to play") { played.isNotEmpty() }
        frames(NowPlayingModel.HOLD_CAP + HOLD)
        until("the refusal lands") { stack.phase == Phase.Rest && !stack.armed }
        assertEquals(0, npEntries())
        assertNull(stack.top)
    }

    @Test
    fun thePlayingTileOpensNowPlayingWithNoHold() {
        val ids = songs(TILES)
        runBlocking { fake.player.play(listOf(ids[0]), 0, 0) }
        val data = QpData(QpItem(QpKind.Song, ids[0], "playing", null), ids.drop(1).mapIndexed { k, id -> QpItem(QpKind.Song, id, "t$k", null) }, emptyList(), emptyList(), 1L)
        front(data)
        tapTile(data, 0)
        until("the playing tile starts T7") { stack.turn?.kind == MoveKind.T7 }
        assertFalse(stack.armed)
        assertTrue(played.isEmpty())
    }

    @Test
    fun beforeTheFirstReadyGenerationATilePlaysNothingAndStartsNoT7() {
        cold = true
        val data = songData(songs(TILES))
        val qp = front(data)
        tapTile(data, 1)
        assertNull("the tile press is untapped", qp.press.on.p.due(SystemClock.uptimeMillis().toFloat()))
        frames(HOLD)
        assertNull(stack.turn)
        assertTrue(played.isEmpty())
    }

    @Test
    fun aQuickplayTileInFrontHeldOpensItsSheetTitledWithTheTileTitle() {
        val data = QpData(null, items(TILES, QpKind.Album), emptyList(), emptyList(), 1L)
        front(data)
        val o = tileAt(data, 0)
        rule.onRoot().performTouchInput { down(o) }
        step(holdMs - FRAME - 1)
        rule.mainClock.advanceTimeByFrame()
        assertNull(stack.overlay)
        step(1)
        settle()
        assertEquals(OverKind.Sheet, stack.overlay?.kind)
        assertEquals(MenuForm.Sheet, app.menu.form)
        assertEquals(data.pins.first().title, app.menu.title)
        assertEquals(UNPIN, app.menu.items.first())
        rule.onRoot().performTouchInput { up() }
        settle()
        assertEquals(OverKind.Sheet, stack.overlay?.kind)
    }

    @Test
    fun aTileHeldWhileAnotherOpenHoldsTheSlotOpensNoSheetAndItsUpPlaysNothing() {
        val data = QpData(null, items(TILES, QpKind.Album), emptyList(), emptyList(), 1L)
        front(data)
        val held = Overlay(OverKind.Dlg)
        val o = tileAt(data, 0)
        rule.onRoot().performTouchInput { down(o) }
        step(holdMs - FRAME - 1)
        rule.mainClock.advanceTimeByFrame()
        assertNull(stack.overlay)
        var called = false
        rule.runOnUiThread { called = stack.open(held) }
        assertTrue("premise: the other open is called and holds the empty slot", called && stack.taken && stack.overlay == null)
        step(1)
        settle()
        assertSame("no sheet opens and the held open lands", held, stack.overlay)
        rule.onRoot().performTouchInput { up() }
        settle()
        assertTrue("the up plays nothing, $played", played.isEmpty())
        assertNull(stack.turn)
        assertEquals(0, npEntries())
        assertSame(held, stack.overlay)
    }

    @Test
    fun aDownLandsThePanOfAQuickplayStateMadeAfterTheFirstTouch() {
        launcher(null)
        rule.onRoot().performTouchInput {
            down(blank())
            up()
        }
        settle()
        val q = swapped()
        var shownAt = 0L
        rule.runOnUiThread {
            shownAt = SystemClock.uptimeMillis()
            q.pan.show(true, shownAt)
        }
        assertTrue(q.pan.moving(shownAt))
        assertEquals(0f, q.pan.at(shownAt), 0f)
        rule.onRoot().performTouchInput { down(blank()) }
        assertEquals(1f, q.pan.at(shownAt), 0f)
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aDownLandsTheAimOfAQuickplayStateMadeAfterTheFirstTouch() {
        launcher(qpSource(QpData.EMPTY))
        rule.onRoot().performTouchInput {
            down(blank())
            up()
        }
        settle()
        val q = swapped()
        val l = QpLayout(home, qpTiles(QpData(null, items(3, QpKind.Album), items(10, QpKind.Song), items(12, QpKind.Album))), 3)
        rule.runOnUiThread {
            q.focus = l.count - 1
            q.into(l, 0L)
        }
        assertTrue(q.aiming)
        rule.onRoot().performTouchInput { down(blank()) }
        assertFalse(q.aiming)
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aTilePressLooksPressedAfterASourceSwap() {
        val data = songData(songs(TILES))
        launcher(qpSource(QpData.EMPTY))
        rule.runOnUiThread { rig.source = qpSource(data) }
        settle()
        val peek = peekHit(home)
        tap((peek.x0 + peek.x1) / 2f, (peek.y0 + peek.y1) / 2f)
        settle()
        settle()
        val qp = state.quickplay
        assertTrue("the swapped pane settles in front", qp.pan.front && qp.pan.still)
        val o = tileAt(data, 1)
        rule.onRoot().performTouchInput { down(o) }
        frames(Tok.motion_pressGlow.toLong() + HOLD)
        val s = state
        assertEquals("the pressed tile is lit at the last frame", 1f, qp.look(s.qpLayout, s.frame).lit[1], 0f)
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aServiceRowInALoadLeavesTheOtherTilesShown() {
        val f = testFake(FakeSet.Demo, services = true)
        val pins = runBlocking { f.library.view().page(Listing.Pins, 0, Int.MAX_VALUE).rows }
        assertTrue("premise: the pins hold a service row", pins.any { it.kind == Kind.Service })
        launcher(ApiQuickplay(f.library, f.player, f.quickplay), MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics))
        until("the load lands") { !state.quickplay.waiting }
        assertEquals(pins.filter { it.kind != Kind.Service }.map { it.id }, state.quickplay.data.pins.map { it.id })
        assertTrue(state.qpLayout.count > 0)
    }

    @Test
    fun aFailedLoadWritesItsLineAndKeepsTheWaitingForm() {
        val busy = object : QpSource {
            override val changes: Flow<Any> = flowOf(Unit)

            override suspend fun load(tiles: Int): QpData = throw Busy("scanning")

            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? = null

            override suspend fun play(item: QpItem) = true
        }
        launcher(busy)
        settle()
        assertTrue(app.logs.contains("quickplay load: scanning"))
        assertTrue("the pane keeps its waiting form", state.quickplay.waiting)
        assertEquals(QpData.EMPTY, state.quickplay.data)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun quickplayStaysWaitingUntilItsFirstDataLands() = runTest {
        val f = testFake(FakeSet.Demo)
        val seeded = f.library.view().pins(0, Int.MAX_VALUE).rows.size
        assertTrue("premise: the library holds pins", seeded > 0)
        assertTrue("premise: the loaded library has a generation", f.library.generation.value > 0)
        val gen = MutableStateFlow(0L)
        val cold = object : Library by f.library {
            override val generation: StateFlow<Long> get() = gen
        }
        val inner = ApiQuickplay(cold, f.player, f.quickplay)
        assertFalse("premise: a cold library is not ready", inner.ready.first())
        val gate = CompletableDeferred<Unit>()
        val src = object : QpSource by inner {
            override suspend fun load(tiles: Int): QpData {
                val d = inner.load(tiles)
                gate.await()
                return d
            }
        }
        val qp = QuickplayState().also { it.waiting = true }
        val landed = mutableListOf<QpData>()
        backgroundScope.launch { qpLoads(src, qp, LOAD_TILES, {}) { landed += it } }
        runCurrent()
        assertTrue(qp.waiting)
        gen.value = f.library.generation.value
        runCurrent()
        assertTrue(src.ready.first())
        assertTrue(qp.waiting)
        assertEquals(QpData.EMPTY, qp.data)
        assertTrue(landed.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertFalse(qp.waiting)
        assertEquals(seeded, qp.data.pins.size)
        assertEquals(qp.data, landed.last())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aPauseAloneSetsTheDataWithNoRebuild() = runTest(UnconfinedTestDispatcher()) {
        val song = QpItem(QpKind.Song, 1, "Song", null)
        var data = QpData(song, listOf(song), emptyList(), emptyList(), 1L)
        val changes = MutableStateFlow(0)
        val src = object : QpSource {
            override val changes: Flow<Any> = changes

            override suspend fun load(tiles: Int) = data

            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? = null

            override suspend fun play(item: QpItem) = true
        }
        val qp = QuickplayState()
        val landed = mutableListOf<QpData>()
        backgroundScope.launch { qpLoads(src, qp, LOAD_TILES, {}) { landed += it } }
        assertEquals(1, landed.size)
        data = data.copy(paused = true)
        changes.value = 1
        assertEquals(data, qp.data)
        assertEquals(1, landed.size)
        data = data.copy(history = listOf(song))
        changes.value = 2
        assertEquals(data, qp.data)
        assertEquals(2, landed.size)
    }

    @Test
    fun aWindowSizeChangeKeepsTheFrontAndTheData() {
        val data = songData(songs(TILES))
        val qp = front(data)
        rule.runOnUiThread { rig.home = scale(Window(UNFOLD_W, UNFOLD_H, 35, none, none, none, none, none), Nav.None, Surface.Home, false) }
        settle()
        assertSame(qp, state.quickplay)
        assertTrue(qp.pan.front)
        assertEquals(data, qp.data)
    }

    @Test
    fun aDensityChangeKeepsTheFrontAndTheData() {
        val data = songData(songs(TILES))
        val qp = front(data)
        rule.runOnUiThread { rig.density = Density(DENSITY) }
        settle()
        assertSame(qp, state.quickplay)
        assertTrue(qp.pan.front)
        assertEquals(data, qp.data)
    }

    @Test
    fun anEntryListChangeKeepsThePane() {
        val data = songData(songs(TILES))
        val qp = front(data)
        rule.runOnUiThread { rig.entries = entries.dropLast(1) }
        settle()
        assertSame(qp, state.quickplay)
        assertTrue(qp.pan.front)
        assertEquals(data, qp.data)
    }

    private fun itemAt(i: Int): Offset {
        val e = HomeGeo(home, entries.size).itemHit(i)
        return Offset((e.x0 + e.x1) / 2f, (e.y0 + e.y1) / 2f)
    }

    private fun pressItem(i: Int): HomeMenu {
        launcher(qpSource(QpData.EMPTY))
        val menu = state.menu
        rule.onRoot().performTouchInput { down(itemAt(i)) }
        frame()
        assertTrue("premise: item $i is pressed", menu.items.held && menu.items.on.item == i)
        return menu
    }

    private fun lift() {
        rule.onRoot().performTouchInput { up() }
        settle()
    }

    @Test
    fun aSubChangeKeepsTheMenuFocusAndPressWithNoEnter() {
        val menu = pressItem(ITEM)
        rule.runOnUiThread { rig.entries = entries.mapIndexed { k, e -> if (k == ITEM) e.copy(sub = "changed") else e } }
        frames(HOLD)
        assertSame(menu, state.menu)
        assertEquals("the sub change enters no menu", 1, rig.menus.size)
        assertEquals(ITEM, menu.focus.row)
        assertTrue("the press stays in flight", menu.items.held && menu.items.on.item == ITEM)
        lift()
    }

    @Test
    fun aSubChangeMidPressSelectsTheEntryPressedAtTheDown() {
        pressItem(ITEM)
        rule.runOnUiThread { rig.entries = entries.mapIndexed { k, e -> if (k == ITEM) e.copy(sub = "changed") else e } }
        frames(HOLD)
        lift()
        assertSame(entries[ITEM], rig.picks.single())
    }

    @Test
    fun aWordChangeMidPressSelectsNothing() {
        pressItem(ITEM)
        rule.runOnUiThread { rig.entries = entries.dropLast(1) + Entry("radio", "stations") }
        frames(HOLD)
        lift()
        assertTrue("a word change cuts the press, ${rig.picks}", rig.picks.isEmpty())
    }

    @Test
    fun aShrinkUnderThePressedLastItemSelectsNothing() {
        val last = entries.size - 1
        pressItem(last)
        rule.runOnUiThread { rig.entries = entries.dropLast(1) }
        frames(HOLD)
        lift()
        assertTrue("the shrink cuts the press, ${rig.picks}", rig.picks.isEmpty())
        assertEquals("the pressed word is gone, so the new menu keeps its first row", 0, state.menu.focus.row)
    }

    @Test
    fun aMoveKeepsHasAndItemFromItsStart() {
        launcher(qpSource(QpData.EMPTY))
        assertEquals(2, app.homeItem("history"))
        rule.runOnUiThread {
            stack.push(TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub)), MoveKind.T2)
            Snapshot.sendApplyNotifications()
        }
        until("premise: the push starts its turn") { stack.turn?.kind == MoveKind.T2 }
        rule.runOnUiThread { rig.entries = listOf(entries[2], entries[0], entries[3]) }
        frame()
        assertTrue(app.homeHas("playlists"))
        assertEquals(2, app.homeItem("history"))
        until("the push lands") { stack.phase == Phase.Rest }
        frame()
        assertFalse(app.homeHas("playlists"))
        assertEquals(0, app.homeItem("history"))
    }

    @Test
    fun twoEntriesWithOneWordAreRefused() {
        val failed = runCatching { launcher(qpSource(QpData.EMPTY), listed = entries + Entry("music", "again")) }.exceptionOrNull()
        assertTrue("the listing fails, $failed", failed is IllegalArgumentException)
        assertEquals("a home entry is listed once", failed?.message)
    }

    private fun gated(data: QpData) {
        launcher(qpSource(data))
        rule.runOnUiThread { rig.quickplay = false }
        settle()
    }

    private fun tapPeek() {
        val peek = peekHit(home)
        tap((peek.x0 + peek.x1) / 2f, (peek.y0 + peek.y1) / 2f)
    }

    @Test
    fun aGateOffDrawsNoPeekAndTakesNoSwipe() {
        gated(songData(songs(TILES)))
        assertFalse("no peek is drawn", state.shown(true, true, null).peek)
        assertFalse("no swipe is taken toward the pane", state.acts(Swipe.Right))
        val from = Offset(a105Page.widthPx * DRAG_FROM, a105Page.heightPx - DRAG_Y)
        rule.onRoot().performTouchInput {
            down(from)
            repeat(DRAG_STEPS) { moveBy(Offset(DRAG_PX, 0f)) }
        }
        assertFalse("the drag opens no pane", state.quickplay.pan.shown)
        rule.onRoot().performTouchInput { up() }
        settle()
        assertFalse(state.quickplay.pan.shown)
    }

    @Test
    fun aPeekTapWithTheGateOffOpensNothing() {
        gated(songData(songs(TILES)))
        tapPeek()
        settle()
        assertFalse(state.quickplay.pan.shown)
        assertFalse(state.quickplay.pan.front)
    }

    @Test
    fun aGateOffHomeMovePaintsNoPeek() {
        gated(songData(songs(TILES)))
        rule.runOnUiThread {
            stack.push(TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub)), MoveKind.T2)
            Snapshot.sendApplyNotifications()
        }
        until("premise: the push starts its turn") { stack.turn?.kind == MoveKind.T2 }
        assertFalse(checkNotNull(app.movePaint.ctx.home).paint.quickplay)
        assertEquals("the push starts with no peek", 0f, app.movePaint.ctx.ends.peek0, 0f)
        until("the push lands") { stack.phase == Phase.Rest }
        rule.runOnUiThread { app.host.back() }
        until("premise: the back starts its turn") { stack.turn?.let { it.popping && it.to == null } == true }
        assertEquals("home lands with no peek", 0f, app.movePaint.ctx.ends.peek1, 0f)
    }

    @Test
    fun aGateTurnedOffLeavesAnOpenPaneOpenUntilItCloses() {
        val qp = front(songData(songs(TILES)))
        rule.runOnUiThread { rig.quickplay = false }
        settle()
        assertTrue("the open pane stays open", qp.pan.front && qp.pan.still)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertFalse("a back closes it", qp.pan.front)
        assertFalse(qp.pan.shown)
        tapPeek()
        settle()
        assertFalse("the gate keeps it closed", qp.pan.front)
    }

    @Test
    fun aGateTurnedOffMidPanEndsTheMoveTowardHome() {
        launcher(qpSource(songData(songs(TILES))))
        tapPeek()
        frame()
        val qp = state.quickplay
        assertTrue("premise: the pan runs toward the front", qp.pan.front && !qp.pan.still)
        rule.runOnUiThread { rig.quickplay = false }
        settle()
        assertFalse(qp.pan.front)
        assertFalse(qp.pan.shown)
    }

    private fun shown(): HomeShown = state.shown(stack.top == null, stack.phase == Phase.Rest, stack.overlay?.kind)

    @Test
    fun aPanShowsThePanCanvasAndTheFrontAddsTheFront() {
        launcher(qpSource(songData(songs(TILES))))
        val rest = shown()
        assertTrue("home at rest shows the home layer", rest.home)
        assertFalse(rest.pan)
        assertFalse(rest.front)
        tapPeek()
        frame()
        assertTrue("premise: the pan runs toward the front", state.pan.front && !state.pan.still)
        val moving = shown()
        assertFalse(moving.home)
        assertTrue("a pan shows the pan canvas", moving.pan)
        assertFalse("a moving pan adds no front", moving.front)
        settle()
        assertTrue("premise: quickplay is in front", state.pan.front && state.pan.still)
        val front = shown()
        assertFalse(front.home)
        assertTrue(front.pan)
        assertTrue("the settled pan adds the front", front.front)
        assertFalse(front.frontFrozen)
    }

    @Test
    fun aSheetOverTheQuickplayFrontFreezesTheFrontAndKeepsItShown() {
        front(songData(songs(TILES)))
        assertFalse("the front at rest is live", shown().frontFrozen)
        val sheet = Overlay(OverKind.Sheet)
        rule.runOnUiThread { stack.open(sheet) }
        until("premise: the sheet opens") { stack.overlay === sheet }
        val opening = shown()
        assertTrue("the front stays shown under the opening sheet", opening.front)
        assertTrue("the opening sheet freezes the front", opening.frontFrozen)
        settle()
        assertSame("premise: the sheet stays open", sheet, stack.overlay)
        val open = shown()
        assertTrue(open.front)
        assertTrue("the open sheet keeps the front frozen", open.frontFrozen)
        rule.runOnUiThread { stack.close() }
        until("premise: the sheet closes") { stack.overlay == null }
        val closed = shown()
        assertTrue(closed.front)
        assertFalse("the closed sheet leaves the front live", closed.frontFrozen)
    }

    @Test
    fun aDialogOverTheQuickplayFrontFreezesTheFront() {
        front(songData(songs(TILES)))
        val dialog = Overlay(OverKind.Dlg)
        rule.runOnUiThread { stack.open(dialog) }
        until("premise: the dialog opens") { stack.overlay === dialog }
        val open = shown()
        assertTrue(open.front)
        assertTrue("a dialog over the front freezes it", open.frontFrozen)
    }

    @Test
    fun aDialogOverAMusicPageLeavesTheFrontUnfrozen() {
        front(songData(songs(TILES)))
        rule.runOnUiThread {
            stack.push(TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub)), MoveKind.T2)
            Snapshot.sendApplyNotifications()
        }
        until("premise: the push lands") { stack.phase == Phase.Rest && stack.top != null }
        assertTrue("premise: the pane stays in front under the page", state.pan.front)
        val dialog = Overlay(OverKind.Dlg)
        rule.runOnUiThread { stack.open(dialog) }
        until("premise: the dialog opens") { stack.overlay === dialog }
        val opening = shown()
        assertFalse("no front over a music page", opening.front)
        assertFalse("a dialog over a music page leaves the front unfrozen", opening.frontFrozen)
        settle()
        assertSame("premise: the dialog stays open", dialog, stack.overlay)
        assertFalse(shown().frontFrozen)
    }

    private fun drawn(s: HomeState): IntArray {
        val l = s.qpLayout
        val img = ImageBitmap(l.s.widthPx, l.s.heightPx)
        rule.runOnUiThread {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(l.s.widthPx.toFloat(), l.s.heightPx.toFloat())) {
                drawRect(Ink.bg)
                quickplay(l, s.qpWords, s.quickplay, s.qpBox, 0L, s.quickplay.look(l, 0L), true)
            }
        }
        return IntArray(l.s.widthPx * l.s.heightPx).also { img.readPixels(it) }
    }

    @Test
    fun aCardOutDrawsNoCardWhereTheHintGoesOverALoadedPane() {
        val card = MutableStateFlow(LibraryState(Card.Out))
        val lib = object : Library by fake.library {
            override val state: StateFlow<LibraryState> get() = card
        }
        val loaded = QpData(null, emptyList(), items(TILES, QpKind.Song), items(TILES, QpKind.Album))
        val waits = object : QpSource {
            override val changes: Flow<Any> = flowOf(Unit)

            override val ready: Flow<Boolean> = flowOf(false)

            override suspend fun load(tiles: Int): QpData = loaded

            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? = null

            override suspend fun play(item: QpItem) = true
        }
        launcher(waits, MusicSource(lib, player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics))
        val s = state
        assertEquals("premise: the pane holds its data", loaded, s.quickplay.data)
        assertEquals(CARD_OUT, s.quickplay.status)
        val px = drawn(s)
        val w = s.qpLayout.s.widthPx
        val base = s.qpLayout.pxOf(s.qpLayout.hintTop(0))
        val bg = Ink.bg.toArgb() and RGB
        assertTrue("no card draws where the hint goes", (base - HINT_PX until base + 2).any { y -> (0 until w).any { px[y * w + it] and RGB != bg } })
    }

    @Test
    fun aColdStartWithTheCardOutShowsNoCardInPlaceOfTheWaitingForm() {
        val card = MutableStateFlow(LibraryState(Card.Out))
        val lib = object : Library by fake.library {
            override val state: StateFlow<LibraryState> get() = card
        }
        val cold = object : QpSource {
            override val changes: Flow<Any> = flowOf(Unit)

            override val ready: Flow<Boolean> = flowOf(false)

            override suspend fun load(tiles: Int): QpData = throw NoCard()

            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? = null

            override suspend fun play(item: QpItem) = true
        }
        launcher(cold, MusicSource(lib, player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics))
        assertEquals(CARD_OUT, state.quickplay.status)
        assertFalse("the card status leaves the waiting form", state.quickplay.waiting)
        assertEquals(QpData.EMPTY, state.quickplay.data)
        card.value = LibraryState(Card.Present)
        settle()
        assertTrue("a mounted card with no library waits again", state.quickplay.waiting)
    }

    @Test
    fun theHostFeedsTheCardStateFromTheLibrary() {
        val card = MutableStateFlow(LibraryState(Card.Out))
        val lib = object : Library by fake.library {
            override val state: StateFlow<LibraryState> get() = card
        }
        launcher(qpSource(QpData.EMPTY), MusicSource(lib, player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics))
        assertEquals(CARD_OUT, state.quickplay.status)
        assertEquals("the pane lays out the status line", QpLayout(home, qpTiles(QpData.EMPTY), 1).sectionTop(1), state.qpLayout.sectionTop(1))
        assertEquals(listOf(CARD_OUT), state.qpWords.hint.map { it.text.lowercase() })
        card.value = LibraryState(Card.Error)
        settle()
        assertEquals(CARD_ERROR, state.quickplay.status)
        assertEquals(listOf(CARD_ERROR), state.qpWords.hint.map { it.text.lowercase() })
        card.value = LibraryState(Card.Held)
        settle()
        assertEquals("a held card shows the hint", "", state.quickplay.status)
        assertTrue(state.qpWords.hint.size > 1)
    }

    private fun noPinsMusic(lib: Library = noPins.library) = MusicSource(lib, noPins.player, noPins.quickplay, noPins.favorites, noPins.playlists, noPins.lyrics)

    private fun albumSheet(): SheetTarget {
        val a = runBlocking { noPins.library.view().page(Listing.Albums, 0, 1).rows.single() }
        return SheetTarget(SheetKind.Album, a.title, Listing.AlbumTracks(a.id), noPins.library.generation.value, pin = Kind.Album, id = a.id)
    }

    private fun openSheet(t: SheetTarget) {
        var opened = false
        rule.runOnUiThread { opened = app.host.openMusic(t) }
        assertTrue("premise: the sheet is asked", opened)
        settle()
        assertEquals(OverKind.Sheet, stack.overlay?.kind)
    }

    @Test
    fun anAlbumSheetOnASourceWithNoQuickplayOffersNoPin() {
        launcher(qpSource(QpData.EMPTY), noPinsMusic())
        val t = albumSheet()
        openSheet(t)
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST), app.menu.items)
        val song = t.copy(kind = SheetKind.Song, pin = Kind.Song)
        assertEquals("the next verb takes the place of the pin", listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST, INFO, GO_TO_ALBUM), sheetItems(song, false, pins = false))
    }

    @Test
    fun aTileHoldOnASourceWithNoQuickplayOpensNoSheet() {
        val data = QpData(null, items(TILES, QpKind.Album), emptyList(), emptyList(), 1L)
        launcher(qpSource(data), noPinsMusic())
        showFront()
        val o = tileAt(data, 0)
        rule.onRoot().performTouchInput { down(o) }
        step(holdMs + HOLD)
        settle()
        assertNull(stack.overlay)
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aSheetOnASourceWithNoQuickplayReadsNoPinned() {
        var reads = 0
        fun counted(v: LibraryView) = object : LibraryView by v {
            override suspend fun pinned(kind: Kind, id: Long): Boolean {
                reads++
                return v.pinned(kind, id)
            }
        }
        val lib = object : Library by noPins.library {
            override fun view(): LibraryView = counted(noPins.library.view())

            override fun view(generation: Long): LibraryView = counted(noPins.library.view(generation))
        }
        launcher(qpSource(QpData.EMPTY), noPinsMusic(lib))
        openSheet(albumSheet())
        assertEquals("a source with no quickplay is asked no pinned", 0, reads)
    }

    private fun blank() = Offset(a105Page.widthPx - BLANK, a105Page.heightPx - BLANK)

    companion object {
        private const val SETTLE = 1000L
        private const val MAX_FRAMES = 200
        private const val HOLD = 300L
        private const val TAP_MS = 50L
        private const val TILES = 3
        private const val LOAD_TILES = 8
        private const val RGB = 0xFFFFFF
        private const val HINT_PX = 6
        private const val FRAME = 16L
        private const val BLANK = 10f
        private const val UNFOLD_W = 1080
        private const val UNFOLD_H = 1280
        private const val DENSITY = 2f
        private const val ITEM = 1
        private const val DRAG_FROM = 0.4f
        private const val DRAG_Y = 200f
        private const val DRAG_STEPS = 10
        private const val DRAG_PX = 20f
    }
}
