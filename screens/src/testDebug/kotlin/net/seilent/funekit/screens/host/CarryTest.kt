package net.seilent.funekit.screens.host

import android.app.Application
import android.graphics.Matrix
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kept
import net.seilent.funekit.kit.Jump
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.albcol.AlbumColGeo
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.album.AlbumGeo
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubGeo
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.cellAt
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.testFake
import net.seilent.funekit.screens.settings.AimKind
import net.seilent.funekit.screens.settings.HeaderLook
import net.seilent.funekit.screens.settings.SETTINGS
import net.seilent.funekit.screens.settings.SettingKind
import net.seilent.funekit.screens.settings.SettingPivot
import net.seilent.funekit.screens.settings.SettingRow
import net.seilent.funekit.screens.settings.SettingsId
import net.seilent.funekit.screens.settings.SettingsModel
import net.seilent.funekit.screens.settings.SettingsPage
import net.seilent.funekit.screens.settings.SettingsRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class CarryTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val a105 = Window(720, 1280, 35, none, none, none, none, none, Dpi(406.4f, 406.4f, 360, 720, 1280, 1, 720, 1280))
    private val context: Application = RuntimeEnvironment.getApplication()
    private val page = scale(a105, Nav.None, Surface.Page, false)
    private lateinit var app: TestApp
    private val stack get() = app.stack
    private val gate get() = app.gate
    private val move get() = app.movePaint

    private val fake = testFake(FakeSet.Demo)
    private val music = MusicSource(fake.library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)

    private fun launcher(vararg pages: SettingsPage) {
        app = TestApp(page, page, music, StillLevel) { SystemClock.uptimeMillis() }
        app.settingsList = pages.toList()
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

    private var downAt = 0L

    private fun send(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downAt = now
        val e = MotionEvent.obtain(downAt, now, action, x, y, 0).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }
        rule.runOnUiThread { gate.touch(e) { rule.activity.dispatchTouchEvent(it) } }
        e.recycle()
        apply()
    }

    private fun step(ms: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        sync()
    }

    private fun push(screen: TestScreen, kind: MoveKind) {
        rule.runOnUiThread {
            stack.push(screen, kind)
            Snapshot.sendApplyNotifications()
        }
        until("premise: the push starts") { stack.turn != null && stack.phase != Phase.Rest }
    }

    private fun into(ms: Long) {
        val at = stack.turn?.at ?: rule.mainClock.currentTime
        until("premise: $ms ms into the turn") { stack.clock - at >= ms }
    }

    @Test
    fun aTapDuringT7LandsItAndActsAtItsOwnPoint() {
        launcher()
        runBlocking { fake.player.play(Listing.Songs, fake.library.generation.value, 0, 0) }
        settle()
        val np = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.NowPlaying))
        push(np, MoveKind.T7)
        into(TAP_AT)
        val g = NpLayout(page)
        val x = g.backCx.toFloat()
        val y = g.backCy.toFloat()
        assertNull("T7 carries no map", move.carry(x, y, stack.clock))
        sync()
        send(MotionEvent.ACTION_DOWN, x, y)
        step(TAP_MS)
        send(MotionEvent.ACTION_UP, x, y)
        until("the back ring tap acts on the landed now playing") { stack.turn?.kind == MoveKind.T8 || (stack.phase == Phase.Rest && np !in stack.pages) }
    }

    @Test
    fun aDownHeldOnAnAlbumsCellDuringTheHubPushOpensItsMenu() {
        launcher()
        val screen = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub))
        push(screen, MoveKind.T2)
        val m = app.models[screen.entry] as HubModel
        assertEquals(HUB_ALBUMS, m.pivot)
        until("premise: the ALBUMS window lands") { m.ready && (m.pane.map?.count ?: 0) > 0 }
        assertTrue("premise: the push still runs", stack.phase != Phase.Rest)
        val hg = HubGeo(page)
        val k = (0 until hg.grid.cols).firstOrNull { (m.cellAt(0, it)?.letter ?: 0) < 0 }
        assertNotNull("premise: the first ALBUMS row holds an album cell", k)
        val p = floatArrayOf(hg.grid.x(k!!) + hg.grid.tile / 2f, hg.top + hg.grid.tile / 2f)
        move.carry(p[0], p[1], stack.clock)?.let { c -> Matrix().also { c.invert(it) }.mapPoints(p) }
        val (x, y) = p
        sync()
        send(MotionEvent.ACTION_DOWN, x, y)
        frames(Tok.input_keyHoldMs.toLong() + HOLD_PAST)
        until("the held cell opens its sheet") { stack.overlay?.kind == OverKind.Sheet }
        send(MotionEvent.ACTION_UP, x, y)
        assertEquals(screen, stack.top)
    }

    @Test
    fun aTargetHeldPastTheHoldTimeGetsItsTapWatchOnTheReplayedDown() {
        launcher()
        runBlocking { fake.player.play(Listing.Songs, fake.library.generation.value, 0, 0) }
        settle()
        val np = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.NowPlaying))
        push(np, MoveKind.T7)
        until("premise: now playing lands") { stack.phase == Phase.Rest }
        push(TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Queue)), MoveKind.T3)
        until("premise: the queue lands") { stack.phase == Phase.Rest }
        rule.runOnUiThread { stack.back { MoveKind.T4 } }
        until("premise: the pop starts") { stack.phase != Phase.Rest }
        assertEquals(Phase.Exit, stack.phase)
        val fonts = createFontFamilyResolver(context)
        val d = Density(1f)
        val b = chromeOf(TextMeasurer(fonts, d, LayoutDirection.Ltr), fonts, page, d, MusicPage.Queue.headerWord ?: "", MusicPage.Queue.pivots, null).headerBox()
        val x = (maxOf(b.x0, 0) + minOf(b.x1, page.widthPx)) / 2f
        val y = (b.y0 + b.y1) / 2f
        assertTrue("premise: the down lies on the screen and in the header box, x $x in ${b.x0} to ${b.x1}", x >= 0f && x < page.widthPx && x >= b.x0 && x < b.x1)
        val g = NpLayout(page)
        val ux = x / page.stdPpu
        val uy = y / page.stdPpu
        assertTrue("premise: now playing takes no act at ($ux, $uy) u", !g.backHit(ux, uy) && !g.titleHit(ux, uy) && !g.artHit(ux, uy) && !g.markHit(ux, uy) && !g.seekHit(ux, uy) && g.ringHit(ux, uy, NP_RINGS) < 0)
        val model = app.music.np
        sync()
        send(MotionEvent.ACTION_DOWN, x, y)
        assertTrue("premise: the queue header down is held", stack.held?.target == true)
        assertFalse("premise: now playing, hidden under the pop, takes no down", model.slide.touch)
        frames(Tok.input_keyHoldMs.toLong() + HOLD_PAST)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals("premise: the hold lands the pop onto now playing", listOf(np), stack.pages.toList())
        assertTrue("the replay puts the held down on the landed now playing", model.slide.touch)
        send(MotionEvent.ACTION_UP, x, y)
        until("the up in the slop backs from now playing") { stack.phase == Phase.Rest && stack.pages.isEmpty() }
    }

    private fun hubAtRest(): Pair<TestScreen, HubModel> {
        launcher()
        val hub = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub))
        push(hub, MoveKind.T2)
        until("premise: the hub rests") { stack.phase == Phase.Rest }
        val m = app.models[hub.entry] as HubModel
        until("premise: the ALBUMS window lands") { m.ready && (m.pane.map?.count ?: 0) > 0 }
        return hub to m
    }

    private fun tapCarried(x: Float, y: Float) {
        val p = floatArrayOf(x, y)
        move.carry(p[0], p[1], stack.clock)?.let { c -> Matrix().also { c.invert(it) }.mapPoints(p) }
        sync()
        send(MotionEvent.ACTION_DOWN, p[0], p[1])
        step(TAP_MS)
        send(MotionEvent.ACTION_UP, p[0], p[1])
    }

    @Test
    fun aTapDuringTheT3cLandsItAndPushesTheAlbumUnderItOnTheLandedColumn() {
        val (_, h) = hubAtRest()
        val letter = (0 until LETTERS).first { h.pane.map?.has(it) == true }
        val col = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.AlbumCol(letter)))
        push(col, MoveKind.T3c)
        val cm = app.models[col.entry] as AlbumColModel
        until("premise: the column window lands") { cm.ready && cm.count > 0 }
        into(TAP_AT)
        assertTrue("premise: the T3c still runs", stack.phase != Phase.Rest)
        val r = (0 until cm.count).first { cm.entry(it).row != null }
        val g = AlbumColGeo(page)
        val album = requireNotNull(cm.entry(r).row).id
        val y = (g.rowTop(cm.track, r) + g.rowTop(cm.track, r + 1)) / 2f
        tapCarried(TAP_X, y)
        until("the tap pushes the album on the landed column") { (stack.turn?.to ?: stack.top)?.page == MusicPage.Album(album) }
    }

    @Test
    fun aTapDuringTheT3mLandsItAndPlaysTheRowUnderItOnTheLandedAlbum() {
        val (_, h) = hubAtRest()
        val hg = HubGeo(page)
        val k = (0 until hg.grid.cols).first { (h.cellAt(0, it)?.letter ?: 0) < 0 }
        send(MotionEvent.ACTION_DOWN, hg.grid.x(k) + hg.grid.tile / 2f, hg.top + hg.grid.tile / 2f)
        step(TAP_MS)
        send(MotionEvent.ACTION_UP, hg.grid.x(k) + hg.grid.tile / 2f, hg.top + hg.grid.tile / 2f)
        until("premise: the album cell pushes T3m") { stack.turn?.kind == MoveKind.T3m && stack.phase != Phase.Rest }
        into(TAP_AT)
        val g = AlbumGeo(page)
        tapCarried(TAP_X, g.top + g.pitch / 2f)
        until("the tap on the landed album asks the play open") { stack.turn?.kind == MoveKind.T7 }
    }

    private class Rows(start: List<SettingPivot>) : SettingsRows {
        override val pivots = mutableStateOf(start)
        val opens = mutableListOf<String>()

        override fun open(row: SettingRow): Boolean {
            opens += row.key
            return false
        }

        override suspend fun set(key: String, value: String): Boolean = true
    }

    @Test
    fun aTapCarriedDuringALiftedSheetsCloseDropsItAtOnceAndActsOnTheRowAtItsBase() {
        val rows = Rows(listOf(SettingPivot(OUT, (0 until LIFT_ROWS).map { SettingRow("r$it", "r$it", FOUR[0], SettingKind.Choice, values = FOUR, words = FOUR, at = 0) })))
        launcher(SettingsPage(SET_ID, rows, HeaderLook.Settings))
        val sc = TestScreen.Settings(stack.entry(), SET_ID)
        rule.runOnUiThread { stack.reset(listOf(sc)) }
        settle()
        val m = app.models[sc.entry] as SettingsModel
        rule.runOnUiThread {
            app.host.types.settings.drawOf(m)
            m.down(LIFT_ROW, SystemClock.uptimeMillis())
            m.cancel(SystemClock.uptimeMillis())
        }
        val before = m.pane.px
        val y = (px(page.board.page_contentTop, page.ppu) + m.tops(m.pivot)[0] + m.heights(m.pivot)[0] / 2 - before).toFloat()
        rule.runOnUiThread { app.host.settingsTap(m, m.rows(m.pivot)[LIFT_ROW]) }
        settle()
        assertTrue("premise: the lift moved the pane and landed", m.pane.px > before && m.aimKind == AimKind.None)
        rule.runOnUiThread { stack.close() }
        until("premise: the sheet closes") { stack.overlay?.closing == true }
        assertTrue("premise: the close drops the lift over the scroll time", m.aimKind == AimKind.Drop && m.aimMs == Tok.motion_scroll)
        sync()
        send(MotionEvent.ACTION_DOWN, TAP_X, y)
        step(TAP_MS)
        assertNull("the carry lands the close", stack.overlay)
        send(MotionEvent.ACTION_UP, TAP_X, y)
        frame()
        frame()
        assertEquals("the land drops the page at once", before, m.pane.px)
        assertEquals("the replayed tap acts on the row under the finger at the base", "r0", rows.opens.last())
    }

    private fun songsHub(): HubModel {
        val (_, h) = hubAtRest()
        rule.runOnUiThread { h.select(HUB_SONGS) }
        until("premise: the songs pane loads") { (h.pane.map?.count ?: 0) > 0 }
        return h
    }

    private fun pickFar(h: HubModel): Int {
        val letter = rule.runOnUiThread { (0 until LETTERS).last { h.pane.map?.has(it) == true } }
        val grid = page.kept()
        rule.runOnUiThread {
            h.jumpPick = -1
            h.colPick = -1
            h.jump.open(h.avail(), letter, grid.stdContentBottom)
            app.host.openJump(stack.top!!)
        }
        settle()
        assertEquals("premise: the jump grid is up", OverKind.Jump, stack.overlay?.kind)
        val slot = Jump.slotOf(letter)
        val x = (Jump.cellX(slot) + Jump.tile / 2) * grid.stdPpu
        val y = (Jump.rowTop(slot) - h.jump.offset + Jump.tile / 2) * grid.stdPpu
        sync()
        send(MotionEvent.ACTION_DOWN, x, y)
        step(TAP_MS)
        send(MotionEvent.ACTION_UP, x, y)
        assertEquals("premise: the letter is picked", letter, h.jumpPick)
        return letter
    }

    private fun jumpRows(h: HubModel, y: Float): Pair<Int, Int> {
        val j = checkNotNull(h.aimJump) { "premise: the drop starts the pick's jump" }
        val v = (y - HubGeo(page).top).toInt()
        val start = h.pane.track.rowAt(v + Math.round(j.from * h.aimU))
        val end = h.pane.track.rowAt(v + Math.round(j.to * h.aimU))
        assertTrue("premise: the rows at the jump's start and end differ, $start and $end", start != end)
        return start to end
    }

    @Test
    fun aTapCarriedDuringAGridPicksCloseActsOnTheRowAtTheJumpsStart() {
        val h = songsHub()
        pickFar(h)
        until("premise: the pick closes the grid") { stack.overlay?.closing == true }
        val y = HubGeo(page).let { it.top + it.view / 2f }
        sync()
        send(MotionEvent.ACTION_DOWN, TAP_X, y)
        step(TAP_MS)
        assertNull("the carry lands the close", stack.overlay)
        val (start, _) = jumpRows(h, y)
        send(MotionEvent.ACTION_UP, TAP_X, y)
        frame()
        frame()
        assertEquals("the replayed tap acts on the row under the finger at the jump's start", start, h.pane.focus.row)
    }

    companion object {
        private const val SETTLE = 1000L
        private const val MAX_FRAMES = 200
        private const val TAP_AT = 100L
        private const val LETTERS = 38
        private const val TAP_MS = 50L
        private const val TAP_X = 360f
        private const val HOLD_PAST = 100L
        private const val NP_RINGS = 3
        private const val LIFT_ROWS = 4
        private const val LIFT_ROW = 2
        private const val OUT = "output"
        private val FOUR = listOf("off", "low", "mid", "high")
        private val SET_ID = SettingsId(SETTINGS)
    }
}
