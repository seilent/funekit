package net.seilent.funekit.screens.music.nowplaying

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.rolePx
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class NowPlayingGlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val g = NpLayout(s)
    private val acts = mutableListOf<NpAct>()
    private var frozen by mutableStateOf(false)
    private var snap by mutableIntStateOf(0)

    private fun compose(): NowPlayingModel {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 1, index = 0, track = 1))
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = DURATION, album = "Album"))), 0)
        m.resetFocus(0)
        rule.setContent {
            val a = remember { Arbiter(s) }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(edges = false, bottom = false, turn = false, acts = { false }, onSwipe = { _, _ -> }))) {
                NowPlayingLayer(
                    m, g, null, null, null, a, Ink.white, false, { rule.mainClock.currentTime },
                    live = true, visible = true, frozen = frozen, hideFocus = false, snap = snap,
                    onAct = {
                        acts += it
                        if (it == NpAct.Info) {
                            frozen = true
                            snap++
                        }
                    },
                    onSeek = {},
                )
            }
        }
        rule.mainClock.autoAdvance = false
        frames(2)
        return m
    }

    private fun frames(n: Int) = repeat(n) {
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun shot(): Bitmap {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
        return b
    }

    private fun band(b: Bitmap): List<Int> {
        val top = g.titleY - 2 * rolePx(Roles.npViewTitle, s)
        val out = mutableListOf<Int>()
        for (y in top until g.titleY) for (x in g.textClip.x0 until g.textClip.x1) out += b.getPixel(x, y)
        return out
    }

    @Test
    fun anInfoHoldLeavesNoGlowOnThePageUnderItsSheet() {
        compose()
        val rest = band(shot())
        val at = Offset((Tok.npTextX + TITLE_DX) * s.ppu, (s.board.npTitleY - TITLE_DY) * s.ppu)
        rule.onRoot().performTouchInput { down(at) }
        frames(GLOW_FRAMES)
        assertFalse("premise: the held title glows", band(shot()) == rest)
        rule.mainClock.advanceTimeBy(Tok.input_keyHoldMs.toLong())
        frames(GLOW_FRAMES)
        assertEquals("premise: the hold asks for the info sheet", listOf(NpAct.Info), acts)
        assertTrue("premise: the page freezes under the sheet", frozen)
        assertTrue("the frozen page under the sheet draws no glow", band(shot()) == rest)
        rule.onRoot().performTouchInput { up() }
        frames(2)
    }

    private companion object {
        const val DURATION = 180000L
        const val TITLE_DX = 20f
        const val TITLE_DY = 4f
        const val GLOW_FRAMES = 4
    }
}
