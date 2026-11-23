package net.seilent.funekit.screens.host

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
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
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.pins
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.home.HomeEntry
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.quickplay.QpData
import net.seilent.funekit.kit.quickplay.QpItem
import net.seilent.funekit.kit.quickplay.QpKind
import net.seilent.funekit.kit.quickplay.QpLayout
import net.seilent.funekit.kit.quickplay.QuickplayState
import net.seilent.funekit.kit.quickplay.peekHit
import net.seilent.funekit.kit.quickplay.qpTiles
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.overlay.UNPIN
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
        frame()
        assertTrue("premise: the push moves", stack.moving)
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

    private fun blank() = Offset(a105Page.widthPx - BLANK, a105Page.heightPx - BLANK)

    companion object {
        private const val SETTLE = 1000L
        private const val MAX_FRAMES = 200
        private const val HOLD = 300L
        private const val TAP_MS = 50L
        private const val TILES = 3
        private const val LOAD_TILES = 8
        private const val FRAME = 16L
        private const val BLANK = 10f
        private const val UNFOLD_W = 1080
        private const val UNFOLD_H = 1280
        private const val DENSITY = 2f
        private const val ITEM = 1
    }
}
