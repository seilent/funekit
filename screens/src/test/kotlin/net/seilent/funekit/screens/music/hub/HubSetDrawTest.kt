package net.seilent.funekit.screens.music.hub

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubSet
import net.seilent.funekit.screens.music.hub.HubState
import net.seilent.funekit.screens.music.hub.QUERY_CELL
import net.seilent.funekit.screens.music.hub.SEARCH_ALBUMS
import net.seilent.funekit.screens.music.hub.hubDraw
import net.seilent.funekit.screens.music.hub.letterOf
import net.seilent.funekit.screens.music.hub.look
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
class HubSetDrawTest {
    private val sc = MusicScenes()
    private val dev = sc.a105
    private val s: Scale = sc.pageScale(dev)

    private fun albums(): Pair<HubModel, HubDraw> = runBlocking {
        val m = HubModel(0L, MusicPage.Search(QUERY), HubState(HubSet.Search(QUERY)))
        m.select(SEARCH_ALBUMS)
        val d = hubDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, Accent.color(Accent.defaultHue, true), true)
        m.cols = d.geo.grid.cols
        val v = sc.fake.library.view()
        m.load(v, SEARCH_ALBUMS)
        m.songs = v.page(Listing.Songs, 0, 0).total
        m.generation = v.generation
        val pn = m.pane
        pn.track.resize(d.geo.spec(m.role(SEARCH_ALBUMS)))
        pn.track.count(pn.map?.count ?: 0, 0)
        pn.counted = pn.map
        sc.fill(m, d)
        m to d
    }

    private fun shot(m: HubModel, d: HubDraw): Bitmap = sc.draw(dev) {
        with(d) { content(m.pivot, 0L, true, null, "page") { r -> m.look(m.pivot, r, 0L, true) } }
    }

    private fun red(b: Bitmap, x: Int, y: Int): Int = Color.red(b.getPixel(x, y))

    @Test
    fun theQueryRowOnAlbumsIsAListRowAtTheGridPitchAndTheLettersStartBelowIt() {
        val (m, d) = albums()
        val g = d.geo.grid
        val pn = m.pane
        assertEquals(QUERY_CELL, pn.cell(0, 0))
        assertTrue("premise: row 1 starts with a letter cell", letterOf(pn.cell(1, 0)) >= 0)
        assertEquals("row 1 sits one grid pitch below row 0", g.pitch, pn.track.topOf(1))
        val b = shot(m, d)
        val top = d.geo.top
        val x0 = px(Tok.hubEdge, s.ppu)
        val ink = (x0 until x0 + TEXT_W).maxOf { x -> (top until top + g.pitch).maxOf { y -> red(b, x, y) } }
        assertTrue("the query text inks row 0 ($ink)", ink > INK)
        val t = g.tileAt(0, top + g.pitch)
        assertTrue("the first letter tile's border is on row 1's top", red(b, t.x0, (t.y0 + t.y1) / 2) > GROUND)
        assertTrue("no letter tile draws at row 0's top", red(b, g.x(0), top + g.tile / 2) <= GROUND)
    }

    private companion object {
        const val QUERY = "name"
        const val TEXT_W = 200
        const val INK = 120
        const val GROUND = 17
    }
}
