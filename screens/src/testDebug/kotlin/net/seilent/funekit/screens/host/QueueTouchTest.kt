package net.seilent.funekit.screens.host

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Player
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.queue.QueueGeo
import net.seilent.funekit.screens.music.queue.QueueModel
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class QueueTouchTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val a105 = Window(720, 1280, 35, none, none, none, none, none, Dpi(406.4f, 406.4f, 360, 720, 1280, 1, 720, 1280))
    private lateinit var page: Scale
    private lateinit var app: TestApp
    private val stack get() = app.stack

    private val fake = testFake(FakeSet.Demo)
    private val calls = mutableListOf<Pair<Int, Long>>()
    private val recording = object : Player by fake.player {
        override suspend fun playAt(index: Int, revision: Long) {
            calls += index to revision
            fake.player.playAt(index, revision)
        }
    }
    private val music = MusicSource(fake.library, recording, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)

    private fun launcher() {
        page = scale(a105, Nav.None, Surface.Page, false)
        app = TestApp(page, page, music, StillLevel) { SystemClock.uptimeMillis() }
        app.compose(rule, root = true)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun settle() {
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(SETTLE, ignoreFrameDuration = true)
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(FRAME)
    }

    private fun push(screen: MusicEntry, kind: MoveKind) {
        rule.runOnUiThread {
            stack.push(screen, kind)
            Snapshot.sendApplyNotifications()
        }
        settle()
        var frames = 0
        while (stack.phase != Phase.Rest) {
            rule.mainClock.advanceTimeByFrame()
            assertTrue(++frames < MAX_FRAMES)
        }
        settle()
    }

    private fun openQueue(): Triple<MusicEntry, MusicEntry, QueueModel> {
        val np = MusicEntry(stack.entry(), MusicPage.NowPlaying)
        push(np, MoveKind.T7)
        val screen = MusicEntry(stack.entry(), MusicPage.Queue)
        push(screen, MoveKind.T3)
        assertEquals(screen, stack.top)
        val m = app.models[screen.entry] as QueueModel
        return Triple(np, screen, m)
    }

    @Test
    fun aRowTapOnTheQueuePlaysThatRowAtThePageRevisionAndReturnsToTheNowPlayingUnderIt() {
        runBlocking { fake.player.play(Listing.Songs, fake.library.view().generation, 0, 0L) }
        launcher()
        val (np, screen, m) = openQueue()
        val row = 2
        assertNotNull("premise: the queue window holds row $row", m.row(row))
        val revision = runBlocking { fake.library.view().page(Listing.Queue, 0, 1).revision }
        assertEquals("premise: the page revision is the queue revision", fake.player.state.value.queueRevision, revision)
        val geo = QueueGeo(page)
        val y = geo.top + row * geo.pitch - m.track.q + geo.pitch / 2f
        rule.onRoot().performTouchInput {
            down(Offset(page.widthPx / 2f, y))
            up()
        }
        settle()
        assertEquals(listOf(row to revision), calls)
        assertEquals(row, fake.player.state.value.index)
        assertEquals(Phase.Rest, stack.phase)
        assertEquals(listOf(np), stack.pages.toList())
        assertFalse(screen in stack.pages)
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun sync() {
        if (rule.mainClock.currentTime < SystemClock.uptimeMillis()) rule.mainClock.advanceTimeBy(SystemClock.uptimeMillis() - rule.mainClock.currentTime)
        val d = rule.mainClock.currentTime - SystemClock.uptimeMillis()
        if (d > 0) ShadowSystemClock.advanceBy(Duration.ofMillis(d))
    }

    private fun step(ms: Long) {
        apply()
        rule.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)
        apply()
        sync()
    }

    private fun touch(block: TouchInjectionScope.() -> Unit) {
        rule.onRoot().performTouchInput(block)
        apply()
    }

    @Test
    fun aRowPressedInsideTheLastRowsFadeKeepsItFadingOnTheBackGlow() {
        runBlocking { fake.player.play(Listing.Songs, fake.library.view().generation, 0, 0L) }
        launcher()
        val (_, _, m) = openQueue()
        val geo = QueueGeo(page)
        fun y(r: Int) = geo.top + r * geo.pitch - m.track.q + geo.pitch / 2f
        val x = page.widthPx / 2f
        val slide = (Tok.touchSlop + Tok.gesture_swipe) / 2 * page.touchPpu
        sync()
        touch { down(Offset(x, y(ROW_A))) }
        step(HELD_MS)
        touch {
            moveTo(Offset(x + slide, y(ROW_A)))
            up()
        }
        step(FRAME)
        touch { down(Offset(x, y(ROW_B))) }
        rule.runOnUiThread {
            val at = SystemClock.uptimeMillis()
            val la = m.look(ROW_A, at, true)
            val lb = m.look(ROW_B, at, true)
            assertEquals(ROW_B, m.glow.glow.key)
            assertEquals(ROW_A, m.glow.back.key)
            assertTrue("row $ROW_A draws on the back glow", la.back && (la.glow ?: 0f) > 0f)
            assertFalse(lb.back)
            assertTrue("row $ROW_B rises from 0", (lb.glow ?: 1f) < (la.glow ?: 0f))
        }
        step(HELD_MS)
        touch {
            moveTo(Offset(x + slide, y(ROW_B)))
            up()
        }
        touch { down(Offset(x, y(ROW_A))) }
        rule.runOnUiThread {
            assertEquals(ROW_A, m.glow.glow.key)
            assertEquals("the front glow on row $ROW_B moves to the back", ROW_B, m.glow.back.key)
        }
        touch {
            moveTo(Offset(x + slide, y(ROW_A)))
            up()
        }
    }

    @Test
    fun aCutGestureClearsTheQueueRowPressAndGlowByTheNextFrame() {
        runBlocking { fake.player.play(Listing.Songs, fake.library.view().generation, 0, 0L) }
        launcher()
        val (_, _, m) = openQueue()
        val geo = QueueGeo(page)
        val x = page.widthPx / 2f
        sync()
        touch { down(Offset(x, geo.top + ROW_A * geo.pitch - m.track.q + geo.pitch / 2f)) }
        step(HELD_MS)
        assertEquals("premise: the row glows", ROW_A, m.glow.glow.key)
        rule.runOnUiThread { m.lists.first().cancel() }
        touch { moveBy(Offset(0f, 1f)) }
        step(FRAME)
        assertNull(m.glow.glow.key)
        assertNull(m.glow.back.key)
        assertFalse(m.press.stale(SystemClock.uptimeMillis()))
        touch { up() }
    }

    companion object {
        const val ROW_A = 1
        const val ROW_B = 3
        const val HELD_MS = 60L
        const val FRAME = 16L
        const val SETTLE = 1000L
        const val MAX_FRAMES = 200
    }
}
