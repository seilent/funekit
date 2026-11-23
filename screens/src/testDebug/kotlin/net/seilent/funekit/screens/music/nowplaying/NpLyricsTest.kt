package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class NpLyricsTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val g = NpLayout(s)

    private fun state(ms: Long) = PlayerState(queueSize = 1, index = 0, track = 1, anchor = Anchor(ms, RATE, 0, false, DURATION))

    private fun compose(m: LyricsModel, np: NowPlayingModel) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val a = remember { Arbiter(s) }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(edges = false, bottom = false, turn = false, acts = { false }, onSwipe = { _, _ -> }))) {
                NpLyrics(m, np, g, null, a, { rule.mainClock.currentTime })
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun np(ms: Long): NowPlayingModel = NowPlayingModel().also {
        it.feed(state(ms))
        it.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = DURATION, album = "Album"))), 0)
    }

    private fun model(text: String): LyricsModel = LyricsModel().also {
        it.begin(1)
        it.load(1, text, 0)
        it.toggle(0)
        it.land()
    }

    @Test
    fun aNewPositionWakesTheViewAndTheNextLineSlidesIn() {
        val np = np(1500)
        val m = model("[00:01.00]one\n[00:02.00]two\n[00:03.00]three")
        compose(m, np)
        assertEquals(0, m.lineShown)
        np.feed(state(2050))
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(1, m.lineShown)
        assertTrue(m.sliding(m.slideAt + 1))
        rule.mainClock.advanceTimeBy(Tok.motion_lyricSlide.toLong() + FRAME)
        assertFalse(m.sliding(rule.mainClock.currentTime))
    }

    @Test
    fun leavingTheLyricsViewClearsTheDueAndTheSlideEnd() {
        val np = np(0)
        val m = model("[00:01.00]one\n[00:02.00]two\n[00:03.00]three")
        var on by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        np.feed(PlayerState(queueSize = 1, index = 0, track = 1, playing = true, anchor = Anchor(HALF_LINE, RATE, npNanosAt(rule.mainClock.currentTime), true, DURATION)))
        rule.setContent {
            val a = remember { Arbiter(s) }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(edges = false, bottom = false, turn = false, acts = { false }, onSwipe = { _, _ -> }))) {
                if (on) NpLyrics(m, np, g, null, a, { rule.mainClock.currentTime })
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertTrue("premise: the view names the next line", m.nextDue >= 0)
        on = false
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(-1L, m.nextDue)
        assertEquals(-1L, m.slideEnd)
    }

    @Test
    fun aDragOverPlainLyricsScrollsThem() {
        val m = model((1..PLAIN_LINES).joinToString("\n") { "plain line $it" })
        compose(m, np(0))
        rule.mainClock.advanceTimeByFrame()
        assertTrue(m.plainTrack.max > 0)
        assertEquals(0, m.plainTrack.q)
        rule.onRoot().performTouchInput {
            down(Offset(X, FROM))
            for (k in 1..STEPS) {
                advanceEventTime(FRAME)
                moveTo(Offset(X, FROM - k * STEP))
            }
            up()
        }
        rule.mainClock.advanceTimeByFrame()
        assertTrue(m.plainTrack.q > 0)
    }

    companion object {
        private const val RATE = 1000
        private const val HALF_LINE = 1500L
        private const val DURATION = 180000L
        private const val FRAME = 16L
        private const val PLAIN_LINES = 40
        private const val X = 360f
        private const val FROM = 900f
        private const val STEPS = 10
        private const val STEP = 40f
    }
}
