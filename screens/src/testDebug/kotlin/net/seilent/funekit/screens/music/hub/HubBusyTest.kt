package net.seilent.funekit.screens.music.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryState
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.rememberChrome
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.Anchor
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class HubBusyTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.Page, false)

    private fun settle() {
        repeat(SETTLE_FRAMES) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()
    }

    private class Rig(val f: Fake, val m: HubModel) {
        var asks = 0
    }

    private fun compose(): Rig {
        val f = testFake()
        val song = runBlocking { f.library.view().page(Listing.Songs, FAR_ROW, 1).rows.single() }
        val r = Rig(f, HubModel(0L))
        fun busyOnce(v: LibraryView): LibraryView = object : LibraryView by v {
            override suspend fun indexOf(listing: Listing, kind: Kind, id: Long): Int? {
                if (id == song.id && ++r.asks == 1) throw Busy()
                return v.indexOf(listing, kind, id)
            }
        }
        val library = object : Library by f.library {
            override val state: StateFlow<LibraryState> get() = f.library.state

            override fun view(): LibraryView = busyOnce(f.library.view())

            override fun view(generation: Long): LibraryView = busyOnce(f.library.view(generation))
        }
        val src = MusicSource(library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)
        r.m.select(HUB_SONGS)
        r.m.anchor = Anchor(Kind.Song, song.id, 0f, SAVED_INDEX)
        rule.setContent {
            val a = remember { Arbiter(s) }
            val chrome = rememberChrome(s, "music", MusicPage.Hub.pivots)
            val measurer = rememberTextMeasurer()
            val fonts = LocalFontFamilyResolver.current
            val density = LocalDensity.current
            val draw = remember { hubDraw(r.m, s, measurer, fonts, density, null, Accent.color(Accent.defaultHue, true), true, chrome) }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(edges = false, bottom = false, turn = false, acts = { false }, onSwipe = { _, _ -> }))) {
                HubLayer(
                    r.m, draw, chrome, src, a, emptyList(), { rule.mainClock.currentTime },
                    live = true, visible = true, frozen = false, hideFocus = false, snap = 0, onBack = {}, onJump = {},
                )
            }
        }
        rule.mainClock.autoAdvance = false
        settle()
        val pn = r.m.pane
        assertEquals(1, r.asks)
        assertEquals(pn.map!!.rowOfIndex(SAVED_INDEX), pn.track.first)
        assertTrue("premise: the busy restore keeps its anchor", pn.pending != null)
        return r
    }

    @Test
    fun aDragAfterABusyRestoreDropsTheAnchorAndNoLaterChangeLandsTheList() {
        val r = compose()
        val pn = r.m.pane
        rule.onRoot().performTouchInput { swipeUp() }
        settle()
        assertEquals(null, pn.pending)
        val q = pn.track.q
        assertTrue("premise: the drag moved the list", q != pn.heldAt)
        r.f.card(Card.Held)
        settle()
        r.f.card(Card.Present)
        settle()
        assertEquals(1, r.asks)
        assertEquals(q, pn.track.q)
    }

    @Test
    fun aLibraryChangeAfterABusyRestoreLandsTheUnmovedListOnTheAnchor() {
        val r = compose()
        val pn = r.m.pane
        r.f.card(Card.Held)
        settle()
        assertEquals(2, r.asks)
        assertEquals(pn.map!!.rowOfIndex(FAR_ROW), pn.track.first)
        assertEquals(null, pn.pending)
    }

    private companion object {
        const val FAR_ROW = 800
        const val SAVED_INDEX = 3
        const val SETTLE_FRAMES = 120
    }
}
