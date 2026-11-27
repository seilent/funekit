package net.seilent.funekit.screens.host

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.testFake
import net.seilent.funekit.screens.settings.HeaderLook
import net.seilent.funekit.screens.settings.SETTINGS
import net.seilent.funekit.screens.settings.SettingKind
import net.seilent.funekit.screens.settings.SettingPivot
import net.seilent.funekit.screens.settings.SettingRow
import net.seilent.funekit.screens.settings.SettingsId
import net.seilent.funekit.screens.settings.SettingsPage
import net.seilent.funekit.screens.settings.SettingsRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class NpReturnTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val window = Window(W, H, SDK, none, none, none, none, none)
    private val page = scale(window, Nav.None, Surface.Page, false)
    private val np = scale(window, Nav.None, Surface.NowPlaying, false)
    private val fake = testFake(FakeSet.Demo)
    private val idle = IdleClock(0L)
    private lateinit var app: TestApp

    private class Rows : SettingsRows {
        override val pivots = mutableStateOf(listOf(SettingPivot(OUT, listOf(SettingRow(OUT, OUT, WORDS[0], SettingKind.Choice, values = WORDS, words = WORDS, at = 0)))))

        override suspend fun set(key: String, value: String): Boolean = true
    }

    private fun now(): Long = rule.mainClock.currentTime

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun idleFor(ms: Long) {
        apply()
        rule.mainClock.advanceTimeBy(ms)
        apply()
    }

    private fun play() = runBlocking { fake.player.play(Listing.Songs, fake.library.generation.value, 0, 0) }

    private fun launch(playing: Boolean = true, root: Boolean = false, pages: () -> List<TestScreen> = { listOf(screen(MusicPage.Hub)) }) {
        if (playing) play()
        val src = MusicSource(fake.library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)
        app = TestApp(page, np, src, StillLevel) { now() }.also {
            it.idle = idle
            it.settingsList = listOf(SettingsPage(ID, Rows(), HeaderLook.Settings))
            it.compose(rule, root)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
        }
        rule.runOnIdle { app.stack.reset(pages()) }
        frames(OPEN_FRAMES)
        if (playing) until("premise: the queue's track shows") { npHasTrack(app.music.np) }
        rule.runOnIdle { idle.resumed(true, now()) }
        frames(1)
    }

    private fun screen(p: MusicPage): TestScreen = TestScreen.Music(MusicEntry(app.stack.entry(), p))

    private fun until(what: String, ok: () -> Boolean) {
        var n = 0
        while (!rule.runOnIdle(ok)) {
            frames(1)
            assertTrue(what, ++n < MAX_FRAMES)
        }
    }

    private fun returned(): Long = rule.runOnIdle { idle.returned }

    private fun marked(at: Long, what: String) = assertEquals("$what: the return is marked at its turn", at, returned())

    private fun t7(): Boolean {
        val t = app.stack.turn
        return t != null && t.kind == MoveKind.T7 && t.to?.let(app.host::isNp) == true
    }

    private fun opening(): Boolean = rule.runOnIdle { t7() }

    private fun t7At(): Long = rule.runOnIdle { checkNotNull(app.stack.turn).at }

    private fun topIsNp(): Boolean = rule.runOnIdle { app.stack.top?.let(app.host::isNp) == true }

    private fun rested() = until("premise: the stack rests") { app.stack.phase == Phase.Rest && !app.stack.moving }

    private fun stamped(): Long = rule.runOnIdle {
        idle.stamp(now())
        idle.last
    }

    private fun left(from: Long): Long = from + WAIT_MS - rule.runOnIdle { now() }

    private fun short(from: Long) = idleFor(left(from) - EARLY_MS)

    private fun opensIn(start: Long, what: String): Long {
        var n = 0
        while (!opening()) {
            frames(1)
            assertTrue("premise: $what: now playing opens", ++n < MAX_FRAMES)
        }
        val at = t7At()
        assertTrue("$what: now playing opens within $WINDOW_MS ms, at ${at - start} ms", at in start..start + WINDOW_MS)
        return at
    }

    private fun assertOpensAt(from: Long, what: String): Long {
        short(from)
        assertFalse("$what: nothing opens before the wait ends", opening() || topIsNp())
        val at = opensIn(from + WAIT_MS, "$what: past the wait")
        marked(at, what)
        return at
    }

    private fun hub(): HubModel = rule.runOnIdle { app.models[app.stack.top!!.entry] as HubModel }

    @Test
    fun aHubPage20sPastItsLastInputOpensNowPlaying() {
        launch()
        val t0 = stamped()
        assertOpensAt(t0, "the hub")
    }

    @Test
    fun homeAtRestOpensNowPlaying() {
        launch(pages = { emptyList() })
        assertNull("premise: home", rule.runOnIdle { app.stack.top })
        assertOpensAt(stamped(), "home")
    }

    @Test
    fun aSettingsPage20sPastItsLastInputOpensNowPlaying() {
        launch(pages = { listOf(TestScreen.Settings(app.stack.entry(), ID)) })
        assertOpensAt(stamped(), "a settings page")
    }

    @Test
    fun aPausedQueueOpensNowPlayingAndStaysPaused() {
        launch()
        runBlocking { fake.player.pause() }
        frames(OPEN_FRAMES)
        assertFalse("premise: paused", fake.player.state.value.playing)
        assertOpensAt(stamped(), "paused")
        assertFalse("the queue stays paused", fake.player.state.value.playing)
    }

    @Test
    fun noTrackKeepsThePage() {
        launch(playing = false)
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        assertFalse("no track opens nothing", opening() || topIsNp())
        assertEquals("no return is marked", NONE, returned())
    }

    @Test
    fun aSheetUpAt20sHoldsTheReturn() {
        launch()
        val t0 = stamped()
        rule.runOnIdle { app.host.openMenu(MenuForm.Sheet, "sheet", listOf("one", "two"), false, -1, 0, false, { true }) }
        idleFor(left(t0) + LATE_MS)
        assertEquals("premise: the sheet stays up", OverKind.Sheet, rule.runOnIdle { app.stack.overlay?.kind })
        assertFalse("the sheet holds the return", opening() || topIsNp())
    }

    @Test
    fun aQueueOverAKeptNowPlayingPopsToItAndItsBackLandsUnderIt() {
        launch(root = true, pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.NowPlaying), screen(MusicPage.Queue)) })
        val t0 = stamped()
        assertOpensAt(t0, "the queue")
        rested()
        val pages = rule.runOnIdle { app.stack.pages.map { (it as TestScreen.Music).music.page } }
        assertEquals("the queue goes and one now playing stays", listOf(MusicPage.Hub, MusicPage.NowPlaying), pages)
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        rested()
        assertEquals("its back lands on the hub", MusicPage.Hub, rule.runOnIdle { (app.stack.top as TestScreen.Music).music.page })
    }

    @Test
    fun nowPlayingOpenedOnHomeWithTheQueueOverItReturnsOnceAndItsBackLandsHome() {
        launch(root = true, pages = { listOf(screen(MusicPage.NowPlaying), screen(MusicPage.Queue)) })
        val first = assertOpensAt(stamped(), "the queue over home's now playing")
        rested()
        idleFor(WAIT_MS + LATE_MS)
        assertEquals("a second wait on now playing runs no open", first, returned())
        assertEquals("one now playing", 1, rule.runOnIdle { app.stack.pages.size })
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        rested()
        assertNull("its back lands on home", rule.runOnIdle { app.stack.top })
    }

    @Test
    fun aHeldFingerHoldsTheReturnUntilItsUp() {
        launch()
        rule.runOnIdle { idle.touch(MotionEvent.ACTION_DOWN, now()) }
        val t0 = rule.runOnIdle { idle.last }
        idleFor(left(t0) + LATE_MS)
        assertFalse("a finger down holds the return", opening() || topIsNp())
        rule.runOnIdle { idle.touch(MotionEvent.ACTION_UP, now()) }
        assertOpensAt(rule.runOnIdle { idle.last }, "after the up")
    }

    @Test
    fun aHeldKeyHoldsTheReturnUntilItsUp() {
        launch()
        rule.runOnIdle { idle.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, now()) }
        val t0 = rule.runOnIdle { idle.last }
        idleFor(left(t0) + LATE_MS)
        assertFalse("a key down holds the return", opening() || topIsNp())
        rule.runOnIdle { idle.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, now()) }
        assertOpensAt(rule.runOnIdle { idle.last }, "after the key's up")
    }

    @Test
    fun aFocusLossHoldsTheReturnAndItsGainRestartsIt() {
        launch()
        val t0 = stamped()
        rule.runOnIdle { idle.focus(false, now()) }
        idleFor(left(t0) + LATE_MS)
        assertFalse("no focus holds the return", opening() || topIsNp())
        rule.runOnIdle { idle.focus(true, now()) }
        assertOpensAt(rule.runOnIdle { idle.last }, "after the focus gain")
    }

    @Test
    fun anOpenSearchFieldHoldsTheReturn() {
        launch(pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.Search(QUERY))) })
        val m = hub()
        rule.runOnIdle { m.openField() }
        frames(1)
        assertTrue("premise: the field is open", rule.runOnIdle { m.search?.open == true })
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        assertFalse("the open field holds the return", opening() || topIsNp())
    }

    @Test
    fun aSearchFieldCloseRestartsTheWait() {
        launch(pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.Search(QUERY))) })
        val m = hub()
        rule.runOnIdle { m.openField() }
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        val closed = rule.runOnIdle {
            m.search!!.ended()
            m.closeField()
            now()
        }
        frames(1)
        assertFalse("premise: the field shut", rule.runOnIdle { m.search?.open == true })
        val at = rule.runOnIdle { idle.last }
        assertTrue("the field's own close stamps the clock", at >= closed)
        assertOpensAt(at, "after the field's close")
    }

    private fun openSearchField(): HubModel {
        launch(root = true, pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.Search(QUERY))) })
        val m = hub()
        rule.runOnIdle { m.openField() }
        frames(1)
        assertTrue("premise: the field is open", rule.runOnIdle { m.search?.open == true })
        return m
    }

    @Test
    fun aBackOnTheSearchFieldRestartsTheWait() {
        val m = openSearchField()
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        val backs = rule.runOnIdle { app.stack.backs }
        val closed = rule.runOnIdle {
            rule.activity.onBackPressedDispatcher.onBackPressed()
            now()
        }
        frames(1)
        assertFalse("premise: the back shut the field", rule.runOnIdle { m.search?.open == true })
        assertEquals("premise: the field took the back, not the stack", backs, rule.runOnIdle { app.stack.backs })
        val at = rule.runOnIdle { idle.last }
        assertTrue("the field's back stamps the clock", at >= closed)
        assertOpensAt(at, "after the field's back")
    }

    @Test
    fun theSearchFieldsDoneRestartsTheWait() {
        val m = openSearchField()
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        val done = rule.runOnIdle { now() }
        rule.onNode(hasImeAction(ImeAction.Done), useUnmergedTree = true).performImeAction()
        frames(1)
        assertFalse("premise: done shut the field", rule.runOnIdle { m.search?.open == true })
        val at = rule.runOnIdle { idle.last }
        assertTrue("the field's done stamps the clock", at >= done)
        assertOpensAt(at, "after the field's done")
    }

    @Test
    fun aCardReturnClosingTheSearchFieldKeepsTheWait() {
        launch(pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.Search(QUERY))) })
        val m = hub()
        rule.runOnIdle { m.openField() }
        val t0 = stamped()
        idleFor(left(t0) - WAIT_MS / 2)
        rule.runOnIdle { app.host.closeLibrarySheets(newCard = true) }
        frames(1)
        assertFalse("premise: the library close shut the field", rule.runOnIdle { m.search?.open == true })
        assertEquals("the library close stamps nothing", t0, rule.runOnIdle { idle.last })
        assertOpensAt(t0, "after a library close")
    }

    @Test
    fun aTypedEntryInFlightHoldsTheReturn() {
        launch()
        val f = BandFields()
        assertTrue("premise: the field sheet opens", rule.runOnIdle { app.host.openFields(f) })
        frames(OPEN_FRAMES)
        assertTrue("premise: the hold takes the entry", rule.runOnIdle { app.menuHost.hold!!(GAIN) })
        frames(OPEN_FRAMES)
        assertNull("premise: the entry cuts the sheet", rule.runOnIdle { app.stack.overlay })
        assertTrue("premise: the run waits for its entry", rule.runOnIdle { app.host.fieldSheet != null })
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        assertFalse("a typed entry in flight holds the return", opening() || topIsNp())
    }

    @Test
    fun aReturnedNowPlayingLeftWithNoInputOpensNoSecond() {
        launch()
        val again = assertOpensAt(stamped(), "the hub")
        rested()
        rule.runOnIdle { app.stack.leave({ app.host.isNp(it) }, app.host.popKind) }
        rested()
        assertFalse("premise: now playing left", topIsNp())
        idleFor(WAIT_MS + LATE_MS)
        assertFalse("no input, no second return", opening() || topIsNp())
        assertEquals("no second return is marked", again, returned())
    }

    @Test
    fun aPageMoveRunningAt20sHoldsTheReturnUntilItLands() {
        launch()
        val t0 = stamped()
        idleFor(left(t0) - MOVE_LEAD_MS)
        rule.runOnIdle { app.stack.push(screen(MusicPage.Hub), MoveKind.T3) }
        idleFor(MOVE_LEAD_MS + EARLY_MS)
        assertTrue("premise: the push still runs past the wait", rule.runOnIdle { app.stack.turn?.kind == MoveKind.T3 })
        assertEquals("the move holds the return", NONE, returned())
        until("the push lands") { app.stack.turn?.kind != MoveKind.T3 }
        marked(opensIn(rule.runOnIdle { now() }, "once the page lands"), "once the page lands")
    }

    @Test
    fun aVolumeOverlayUpAt20sHoldsTheReturnUntilItCloses() {
        launch()
        val t0 = stamped()
        idleFor(left(t0) - VOLUME_LEAD_MS)
        rule.runOnIdle { app.host.openVolume(now()) }
        idleFor(VOLUME_LEAD_MS + EARLY_MS)
        assertEquals("premise: the plate is up at the wait's end", OverKind.Volume, rule.runOnIdle { app.stack.overlay?.kind })
        assertEquals("the plate holds the return", NONE, returned())
        until("the plate goes") { app.stack.overlay == null }
        marked(opensIn(rule.runOnIdle { now() }, "once the plate goes"), "once the plate goes")
    }

    @Test
    fun aTrackThatArrivesPastTheWaitOpensNowPlaying() {
        launch(playing = false)
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        assertFalse("premise: no track opens nothing", opening() || topIsNp())
        play()
        until("now playing opens once a track arrives") { t7() }
        marked(t7At(), "once a track arrives")
    }

    @Test
    fun anIdlePageWithNoTrackRunsNoFrame() {
        launch(playing = false)
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        val asked = (1..OPEN_FRAMES).count {
            frames(1)
            rule.runOnIdle { Recomposer.runningRecomposers.value.any { r -> r.hasPendingWork } }
        }
        assertEquals("no frame is asked on an idle page with no track", 0, asked)
    }

    @Test
    fun aHomePanAtTheDueTimeOpensOnceItSettles() {
        launch(pages = { emptyList() })
        val t0 = stamped()
        rule.runOnIdle { app.panUntil = t0 + WAIT_MS + PAN_MS }
        short(t0)
        frames(WINDOW_FRAMES)
        assertFalse("the pan holds the open", opening())
        marked(opensIn(t0 + WAIT_MS + PAN_MS, "once the pan settles"), "once the pan settles")
    }

    @Test
    fun aRefusedOpenMarksNoReturnAndRetriesAtRest() {
        launch(pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.Search(QUERY))) })
        val m = hub()
        rule.runOnIdle { m.openField() }
        val t0 = stamped()
        idleFor(left(t0) + LATE_MS)
        assertEquals("premise: the field holds the return", NONE, returned())
        rule.runOnIdle {
            app.stack.push(screen(MusicPage.Hub), MoveKind.T3)
            m.closeField()
        }
        frames(CUT_FRAMES)
        assertTrue("premise: the push took the stack ahead of now playing", rule.runOnIdle { app.stack.turn?.kind == MoveKind.T3 })
        assertEquals("a refused open marks no return", NONE, returned())
        until("the push lands") { app.stack.turn?.kind != MoveKind.T3 }
        marked(opensIn(rule.runOnIdle { now() }, "once the stack rests"), "once the stack rests")
    }

    private fun gesture() = rule.runOnIdle { rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)) }

    @Test
    fun aPredictiveBackHeldPastTheWaitHoldsTheReturnUntilItsCancel() {
        launch(pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.Hub)) })
        val t0 = stamped()
        idleFor(left(t0) - WAIT_MS / 4)
        val started = rule.runOnIdle { now() }
        gesture()
        frames(1)
        assertTrue("premise: the gesture is live", rule.runOnIdle { app.stack.backing })
        assertTrue("the gesture's start stamps the clock", rule.runOnIdle { idle.last } >= started)
        idleFor(WAIT_MS + LATE_MS)
        assertFalse("the held back holds the return", opening() || topIsNp())
        val cancelled = rule.runOnIdle {
            rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            now()
        }
        frames(1)
        assertFalse("premise: the cancel ends the gesture", rule.runOnIdle { app.stack.backing })
        val at = rule.runOnIdle { idle.last }
        assertTrue("the cancel stamps the clock", at >= cancelled)
        assertOpensAt(at, "after the cancel")
    }

    @Test
    fun aBackWithNoWindowInputRestartsTheWait() {
        launch(pages = { listOf(screen(MusicPage.Hub), screen(MusicPage.Hub)) })
        val t0 = stamped()
        idleFor(left(t0) - WAIT_MS / 4)
        val backed = rule.runOnIdle {
            app.stack.back(app.host.popKind)
            now()
        }
        frames(1)
        val at = rule.runOnIdle { idle.last }
        assertTrue("the back stamps the clock", at >= backed)
        rested()
        assertOpensAt(at, "after the back")
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val OUT = "output"
        private const val QUERY = "a"
        private const val NONE = Long.MIN_VALUE
        private const val GAIN = 2
        private const val OPEN_FRAMES = 30
        private const val CUT_FRAMES = 2
        private const val WINDOW_FRAMES = 3
        private const val WINDOW_MS = 50L
        private const val MAX_FRAMES = 600
        private const val EARLY_MS = 32L
        private const val LATE_MS = 5_000L
        private const val MS_PER_S = 1000f
        private val WAIT_MS = (Tok.settings_npReturn * MS_PER_S).toLong()
        private const val MOVE_LEAD_MS = 50L
        private const val VOLUME_LEAD_MS = 300L
        private const val PAN_MS = 400L
        private val ID = SettingsId(SETTINGS)
        private val WORDS = listOf("off", "on")
    }
}
