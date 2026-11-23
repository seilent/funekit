package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.motion.VariantMotion
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.posture.Fold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private var live by mutableStateOf(true)
    private var one by mutableStateOf(true)
    private var two by mutableStateOf(false)
    private val seen = mutableListOf<Edges?>()

    private fun row(durationMs: Long = DURATION, title: String = "Song") = Row(1, Kind.Song, title, "Artist", null, durationMs = durationMs, album = "Album")

    private fun frames(n: Int) = repeat(n) {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
    }

    @Composable
    private fun BandLayer(m: NowPlayingModel, a: Arbiter, split: NpSplit?) {
        NowPlayingLayer(
            m, g, null, null, null, a, Ink.white, false, { rule.mainClock.currentTime },
            live = live, visible = true, frozen = false, hideFocus = false, snap = 0,
            onAct = { acts += it },
            onSeek = { seeks += it },
            split = split,
        )
    }

    private fun banded(durationMs: Long = DURATION, split: NpSplit? = null): NowPlayingModel {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 1, index = 0, track = 1))
        m.sync(NpShown.of(listOf(row(durationMs))), 0)
        m.resetFocus(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val a = remember { Arbiter(s) }
            LaunchedEffect(m) { snapshotFlow { m.seekBand }.collect { seen += it } }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(edges = false, bottom = false, turn = false, acts = { false }, onSwipe = { _, _ -> }))) {
                if (one) BandLayer(m, a, split)
                if (two) BandLayer(m, a, split)
            }
        }
        frames(2)
        return m
    }

    @Test
    fun theSeekBandIsTheSeekHitInTheLayersPixels() {
        val m = banded()
        val r = g.seekBand
        assertTrue("the band is the seek hit", checkNotNull(m.seekBand).same(Edges(r.x0, r.y0, r.x1, r.y1)))
        m.sync(NpShown.of(listOf(row(title = "Other"))), rule.mainClock.currentTime)
        frames(2)
        assertEquals("an equal rect is no new value", 1, seen.count { it != null })
    }

    @Test
    fun theSeekBandIsNullWhileTheBandTakesNoSeek() {
        val fade = (VariantMotion.total(VariantMotion.Dir.Out) / FRAME + 2).toInt()
        val m = banded()
        assertNotNull("premise: the band", m.seekBand)
        m.lyricsView = true
        frames(2)
        assertNotNull("premise: the cover shows with the own mode off", m.seekBand)
        m.lyrics.toggle(rule.mainClock.currentTime)
        frames(2)
        assertNull("null from the toggle", m.seekBand)
        frames(fade / 2)
        assertNull("null through the fade", m.seekBand)
        frames(fade)
        assertNull("null in the lyrics view", m.seekBand)
        m.lyrics.toggle(rule.mainClock.currentTime)
        frames(fade * 2)
        assertNotNull("set again off it", m.seekBand)
        m.lyricsView = false
        m.feed(PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null))
        frames(2)
        assertNull("null for a phone feed", m.seekBand)
        m.feed(PlayerState(queueSize = 1, index = 0, track = 1))
        frames(2)
        assertNotNull("premise: the own feed again", m.seekBand)
        m.sync(NpShown.of(listOf(row(0))), rule.mainClock.currentTime)
        frames(2)
        assertNull("null for a row with no duration", m.seekBand)
        m.sync(NpShown.of(listOf(row())), rule.mainClock.currentTime)
        frames(2)
        live = false
        frames(2)
        assertNull("null while the layer is not live", m.seekBand)
        live = true
        frames(2)
        assertNotNull("premise: live again", m.seekBand)
        one = false
        frames(2)
        assertNull("null once the layer leaves", m.seekBand)
        one = true
        frames(2)
        two = true
        frames(2)
        assertNotNull("premise: both layers write the band", m.seekBand)
        one = false
        frames(2)
        assertNotNull("the second layer's rect stays", m.seekBand)
    }

    @Test
    fun aDurationThatArrivesOnTheShownTrackBringsTheRectBack() {
        val m = banded(durationMs = 0)
        assertNull("premise: no band with no duration", m.seekBand)
        m.sync(NpShown.of(listOf(row())), rule.mainClock.currentTime)
        frames(2)
        assertNotNull(m.seekBand)
    }

    @Test
    fun inASplitTheSeekBandIsTheLowerHalfsAtItsOffset() {
        val split = checkNotNull(NpSplit.of(Window(FOLD_W, FOLD_H, 35, none, none, none, none, none, null), Nav.None, false, Fold(FOLD_TOP, FOLD_BOTTOM), 0))
        val m = banded(split = split)
        val h = split.lower
        val r = h.g.seekBand
        val b = checkNotNull(m.seekBand)
        assertTrue("premise: the band lies in the lower half", h.y - h.dy + r.y0 >= h.y && h.y - h.dy + r.y1 <= h.y + h.clip)
        assertEquals(h.y - h.dy + r.y0, b.y0)
        assertEquals(h.x + r.x0, b.x0)
        assertEquals(h.x + r.x1, b.x1)
        assertEquals(h.y - h.dy + r.y1, b.y1)
    }

    @Test
    fun theHeartRingTapIsTheFavoriteActAndFocusesIt() {
        val m = compose()
        tap(622f, 1195f)
        assertEquals(listOf(NpAct.Favorite), acts)
        assertEquals(2, m.ringFocus())
    }

    private companion object {
        const val DURATION = 180000L
        const val FRAME = 16L
        const val FOLD_W = 1080
        const val FOLD_H = 2520
        const val FOLD_TOP = 1250
        const val FOLD_BOTTOM = 1270
    }
}
