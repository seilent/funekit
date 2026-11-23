package net.seilent.funekit.screens.music.album

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Roles
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.penFloor
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.album.AlbumGeo
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.album.albumChrome
import net.seilent.funekit.screens.music.album.albumDraw
import net.seilent.funekit.screens.music.sheet.MusicScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AlbumDrawTest {
    private val sc = MusicScenes(FakeSet.Main)
    private val s = sc.pageScale(sc.a105)

    private fun loaded(): AlbumModel = runBlocking {
        val v = sc.fake.library.view()
        val n = v.page(Listing.Albums, 0, 0).total
        val head = v.page(Listing.Albums, 0, n).rows.first { it.title == COBALT }
        val m = AlbumModel(0L, head.id)
        m.head = head
        m.geometry(AlbumGeo(s))
        m.load(v)
        m.show(v, 0, m.total - 1)
        m.track.count(m.count, 0)
        m.focus.set(0, m.count, 0L, false)
        m
    }

    @Test
    fun theHeaderIsTheAlbumsWordAtItsHalfAlphaAndItsBoxIsTheHeaderBandOverTheWord() {
        val c = albumChrome(sc.measurer, sc.fonts, s, sc.density, null)
        assertEquals(Roles.albumHeader.alpha, c.headerAlpha, 0f)
        assertEquals(0.55f, c.headerAlpha, 0f)
        val b = c.headerBox()
        assertEquals(-84, b.x0)
        assertEquals(210, b.y1)
        assertEquals(floor(-84f + c.header.width).toInt(), b.x1)
    }

    @Test
    fun theDurationEndsAtTheRightEdgeAndTheTitleClipsAGutterBeforeIt() {
        val ink = rowInk(sc.measurer, sc.fonts, RowModel(primary = "Room 1", number = "1", trailing = "3:00"), s, sc.density, true, null)
        assertEquals(660, ink.right)
        assertEquals(penFloor(ink.trailingX64) - px(Tok.gutter, s.ppu), ink.primaryRight)
        assertEquals(36, px(Tok.gutter, s.ppu))
    }

    @Test
    fun everyRowLookAndModelStaysEqualOverRestFramesAndAFocusStepChangesTwoRows() {
        val m = loaded()
        m.onRings = false
        val settle = Tok.motion_focusAlpha.toLong() + 1
        val looks = (0 until m.count).map { m.look(it, settle, true) }
        val models = (0 until m.count).map { m.rowModel(it) }
        for (f in 0 until FRAMES) {
            val at = settle + f * FRAME_MS
            assertEquals((0 until m.count).map { m.look(it, at, true) }, looks)
            assertEquals((0 until m.count).map { m.rowModel(it) }, models)
        }
        m.focus.set(1, m.count, settle, false)
        val after = (0 until m.count).map { m.look(it, settle + 1, true) }
        assertEquals(2, after.indices.count { after[it] != looks[it] })
    }

    @Test
    fun theBlockArtKeepsItsSourceAtEveryLiftButTheSwitchAtTheGridTile() {
        val m = loaded()
        val g = m.geo!!
        m.art[g.large] = ImageBitmap(g.large, g.large)
        m.art[g.grid] = ImageBitmap(g.grid, g.grid)
        var switches = 0
        var last = m.source(0)
        assertEquals(g.large, last)
        for (l in 0..g.above) {
            val src = m.source(l)
            if (src != last) switches++
            last = src
            assertEquals(if (g.artRect(l).w <= g.grid) g.grid else g.large, src)
        }
        assertEquals(1, switches)
        assertEquals(g.grid, m.source(g.above))
    }

    @Test
    fun anAlbumGoneFromTheLibraryDrawsTheHeaderAndATileSquareOnly() = runBlocking {
        val sc2 = MusicScenes(FakeSet.Main)
        val v = sc2.fake.library.view()
        val n = v.page(Listing.Albums, 0, 0).total
        val head = v.page(Listing.Albums, 0, n).rows.first { it.title == SALT_LAKE }
        val m = AlbumModel(0L, head.id)
        m.head = head
        m.geometry(AlbumGeo(s))
        m.load(v)
        val d = albumDraw(m, s, sc2.measurer, sc2.fonts, sc2.density, null, albumChrome(sc2.measurer, sc2.fonts, s, sc2.density, null), Ink.white, true)
        assertTrue(d.lines() != null)
        sc2.fake.library.removeRoot(sc2.fake.library.roots.value.last().uri)
        m.migrate(sc2.fake.library.view(), 0L)
        assertEquals(0, m.count)
        assertNull(d.lines())
        val b = m.block(0)
        assertTrue(b.gone)
        assertNull(b.art)
        assertNotEquals(0, m.geo!!.artRect(0).w)
    }

    companion object {
        private const val COBALT = "Cobalt Rooms"
        private const val SALT_LAKE = "Salt Lake Postcards"
        private const val FRAMES = 100
        private const val FRAME_MS = 16L
    }
}
