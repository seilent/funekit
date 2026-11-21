package net.seilent.funekit.screens.music.search

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.SEARCH_ARTISTS
import net.seilent.funekit.screens.music.hub.hubDraw
import net.seilent.funekit.screens.music.hub.lineBase
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.hub.musicModel
import net.seilent.funekit.screens.music.hub.rowModel
import net.seilent.funekit.screens.music.sheet.HubRegions
import net.seilent.funekit.screens.music.sheet.MusicScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SearchDrawTest {
    private val sc = MusicScenes()
    private val dev = sc.a105
    private val s: Scale = sc.pageScale(dev)

    private class Shown(rec: DrawRecord) : DrawRecord by rec {
        private val inner = rec
        val rows = HashMap<String, Boolean>()
        val bases = HashMap<String, Float>()

        override fun row(name: String, ink: RowInk, s: Scale, w: Int, alpha: Float, focused: Boolean, press: Float, glow: Boolean, accent: Color, accentOff: Boolean, focusShown: Boolean) {
            rows[name] = focusShown
            inner.row(name, ink, s, w, alpha, focused, press, glow, accent, accentOff, focusShown)
        }

        override fun word(name: String, wd: Word, x: Float, base: Float, alpha: Float, color: Color) {
            bases[name] = base
            inner.word(name, wd, x, base, alpha, color)
        }
    }

    private fun hub(query: String): Pair<HubModel, HubDraw> = runBlocking {
        val m = musicModel(0L, MusicPage.Search(query)) as HubModel
        val d = hubDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, Accent.color(Accent.defaultHue, true), true)
        m.cols = d.geo.grid.cols
        val v = sc.fake.library.view()
        m.load(v, SEARCH_ARTISTS)
        m.show(v, SEARCH_ARTISTS, 0, ROWS)
        m.songs = v.page(Listing.Songs, 0, 0).total
        m.generation = v.generation
        val pn = m.pane
        pn.track.resize(d.geo.spec(m.role(SEARCH_ARTISTS)))
        pn.track.count(pn.map?.count ?: 0, 0)
        pn.counted = pn.map
        m to d
    }

    private fun record(m: HubModel, d: HubDraw): Shown {
        val rec = Shown(HubRegions(sc.regions(dev, s)))
        sc.draw(dev) { with(d) { content(m.pivot, 0L, true, rec, "page") { r -> m.look(m.pivot, r, 0L, true) } } }
        return rec
    }

    @Test
    fun theQueryRowDrawsWithNoFocusShownAndTheResultsWithIt() {
        val (m, d) = hub(QUERY)
        val rec = record(m, d)
        assertEquals(false, rec.rows["page.row0"])
        val item = (1 until m.pane.map!!.count).first { m.pane.map!!.indexAt(it) >= 0 }
        assertEquals(true, rec.rows["page.row$item"])
    }

    @Test
    fun theQueryRowDrawsTheFieldTextOnlyWhileTheFieldIsOpen() {
        val (m, d) = hub(QUERY)
        m.openField()
        m.typed(TYPED)
        assertEquals(TYPED, d.data(SEARCH_ARTISTS, 0).model?.primary)
        m.blur()
        assertEquals(QUERY, d.data(SEARCH_ARTISTS, 0).model?.primary)
        m.search!!.text = TYPED
        assertEquals("a closed field never shows its text", QUERY, d.data(SEARCH_ARTISTS, 0).model?.primary)
    }

    @Test
    fun everyRowLookAndDataHoldOverAHundredRestFrames() {
        val (m, d) = hub(QUERY)
        val n = m.pane.map!!.count
        val looks = (0 until n).map { m.look(SEARCH_ARTISTS, it, 0L, true) }
        val data = (0 until n).map { d.data(SEARCH_ARTISTS, it) }
        for (f in 1..FRAMES) {
            val t = f * FRAME_MS
            assertEquals(looks, (0 until n).map { m.look(SEARCH_ARTISTS, it, t, true) })
            assertEquals(data, (0 until n).map { d.data(SEARCH_ARTISTS, it) })
        }
    }

    @Test
    fun theCountWordEndsAtTheWidthLessTheHubEdgeWhileTheFieldIsOpen() {
        val (m, d) = hub(QUERY)
        m.openField()
        m.search!!.word = WORD
        val model = requireNotNull(m.rowModel(SEARCH_ARTISTS, 0))
        assertEquals(WORD, model.trailing)
        val ink = d.ink(model)
        assertEquals(px(s.w - Tok.hubEdge, s.ppu), Rows.right(s))
        assertEquals(pen64(Rows.right(s).toFloat()), ink.trailingX64 + pen64(ink.trailing.width))
        m.blur()
        assertEquals("", m.rowModel(SEARCH_ARTISTS, 0)?.trailing)
    }

    @Test
    fun theEmptyLineSitsOnePitchBelowTheQueryRow() {
        val (m, d) = hub(EMPTY_HITS)
        val rec = record(m, d)
        val base = requireNotNull(rec.bases["page.found"])
        assertEquals((lineBase(s) + m.pane.track.topOf(1)).toFloat(), base)
        assertTrue(m.pane.track.topOf(1) > 0)
    }

    private companion object {
        const val QUERY = "name"
        const val TYPED = "abc"
        const val WORD = "2 songs"
        const val EMPTY_HITS = "zzqx"
        const val ROWS = 20
        const val FRAMES = 100
        const val FRAME_MS = 16L
    }
}
