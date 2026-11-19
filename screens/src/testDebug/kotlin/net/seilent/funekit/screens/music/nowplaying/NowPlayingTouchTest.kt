package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class NowPlayingTouchTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val g = NpLayout(s)
    private val acts = mutableListOf<NpAct>()
    private val seeks = mutableListOf<Long>()

    private fun compose(): NowPlayingModel {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 1, index = 0, track = 1))
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000, album = "Album"))), 0)
        m.resetFocus(0)
        rule.setContent {
            val a = remember { Arbiter(s) }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(edges = false, bottom = false, turn = false, acts = { false }, onSwipe = { _, _ -> }))) {
                NowPlayingLayer(
                    m, g, null, null, null, a, Ink.white, false, { rule.mainClock.currentTime },
                    live = true, visible = true, frozen = false, hideFocus = false, snap = 0,
                    onAct = { acts += it },
                    onSeek = { seeks += it },
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeByFrame()
        return m
    }

    private fun tap(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theBackRingTapIsTheBackAct() {
        compose()
        tap(60f, 60f)
        assertEquals(listOf(NpAct.Back), acts)
    }

    @Test
    fun aTitleTapOpensTheQueueAndAnArtTapTheControls() {
        compose()
        tap(300f, 1000f)
        tap(300f, 700f)
        assertEquals(listOf(NpAct.Queue, NpAct.Controls), acts)
    }

    @Test
    fun aSeekPressAndReleaseSeeksOnce() {
        compose()
        tap(300f, 900f)
        assertEquals(1, seeks.size)
        assertEquals(g.seekAt(100f, 180000), seeks[0])
        assertEquals(emptyList<NpAct>(), acts)
    }

    @Test
    fun theHeartRingTapIsTheFavoriteActAndFocusesIt() {
        val m = compose()
        tap(622f, 1195f)
        assertEquals(listOf(NpAct.Favorite), acts)
        assertEquals(2, m.ringFocus())
    }
}
