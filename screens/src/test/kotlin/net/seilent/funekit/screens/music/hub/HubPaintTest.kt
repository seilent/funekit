package net.seilent.funekit.screens.music.hub

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Layout
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.stack.TileHide
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.scale
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubGeo
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPaint
import net.seilent.funekit.screens.music.hub.HubState
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HubPaintTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val geo = HubGeo(s)
    private val fake = testFake()
    private val pivot = WordSide("artists", 1f, 1f, 0f, 0f, HUB_ARTISTS, false, Word.none)
    private val header = WordSide("music", 1f, 1f, 0f, 0f, -1, true, Word.none)

    private fun model(p: Int): HubModel = HubModel(0L, MusicPage.Hub, HubState()).also { m ->
        m.select(p)
        m.cols = geo.grid.cols
        runBlocking {
            val v = fake.library.view()
            m.load(v, p)
            val pn = m.panes[p]
            val n = pn.map!!.count
            pn.track.resize(geo.spec(p))
            pn.track.count(n, 0)
            m.show(v, p, 0, n - 1)
            m.thumbs(v, p, 0, n - 1, THUMBS)
        }
    }

    private fun paintOf(m: HubModel, chrome: Chrome = Chrome(s, Word.none, emptyList())): HubPaint {
        val d = HubDraw(m, geo, { error("no row ink") }, { _, _ -> error("no grid ink") }, { error("no label") }, { error("no line") }, Ink.white, true, List(MusicPage.Hub.pivots.size) { pivot }, header)
        return HubPaint(d, chrome, 0, emptyList(), Ink.white)
    }

    @Test
    fun anAlbumsCellRectIsTheGridTileAndNoneAboveTheClipTop() {
        val m = model(HUB_ALBUMS)
        val pn = m.panes[HUB_ALBUMS]
        val map = pn.map!!
        val w = pn.paging.window!!
        val i = (0 until map.count * geo.grid.cols).first { map.rowOfIndex(it) == 1 && map.colOfIndex(it) == 1 }
        val album = w.rows[i - w.offset].id
        val want = geo.grid.tileAt(1, geo.top + pn.track.topOf(1))
        assertEquals(listOf(want.x0, want.y0, want.x1, want.y1), paintOf(m).tileRect(album)!!.let { listOf(it.x0, it.y0, it.x1, it.y1) })
        pn.track.at(pn.track.topOf(1) + 1)
        assertNull(paintOf(m).tileRect(album))
        m.select(HUB_ARTISTS)
        assertNull(paintOf(m).tileRect(album))
    }

    @Test
    fun aThumbRectIsSlotZeroOfAHeldFirstAlbumAndNoneForACoverlessOneOrAboveTheClipTop() {
        val m = model(HUB_ARTISTS)
        val pn = m.panes[HUB_ARTISTS]
        val row = (0 until pn.map!!.count).first { r -> pn.row(pn.cell(r, 0))?.let { pn.thumbs[it.id]?.firstOrNull() } != null }
        val id = pn.row(pn.cell(row, 0))!!.id
        val ref = pn.thumbs[id]!!.first()!!
        assertNull("not held", paintOf(m).thumbRect(row))
        m.art[ArtKey.Cover(ref, geo.thumb)] = ImageBitmap(1, 1)
        val y = geo.top + pn.track.topOf(row) + Layout.thumbsTop(s, 0)
        val x = Layout.thumbsX(s, true)
        assertEquals(listOf(x, y, x + geo.thumb, y + geo.thumb), paintOf(m).thumbRect(row)!!.let { listOf(it.x0, it.y0, it.x1, it.y1) })
        assertTrue(pn.firsts[id]!! >= 0L)
        pn.track.at(pn.track.topOf(row) + Layout.thumbsTop(s, 0) + 1)
        assertNull("above the clip top", paintOf(m).thumbRect(row))
        pn.track.at(0)
        pn.readThumbs(id, listOf(null, ref), pn.firsts[id]!!)
        assertNull("a coverless first album", paintOf(m).thumbRect(row))
    }

    @Test
    fun hideNamesTheAlbumAndWordSideIsTheActivePivotOrTheHeader() {
        val m = model(HUB_ARTISTS)
        val chrome = Chrome(s, Word.none, emptyList())
        val p = paintOf(m, chrome)
        p.hide(TileHide(ALBUM, false))
        assertEquals(ALBUM, m.hidden)
        p.hide(null)
        assertEquals(-1L, m.hidden)
        assertSame(header, p.wordSide(false))
        val side = requireNotNull(p.wordSide(true))
        assertEquals(pivot.text, side.text)
        assertEquals(chrome.pivotX(chrome.active), side.x, 0f)
    }

    companion object {
        private const val SDK = 35
        private const val W = 720
        private const val H = 1280
        private const val THUMBS = 20
        private const val ALBUM = 9L
    }
}
