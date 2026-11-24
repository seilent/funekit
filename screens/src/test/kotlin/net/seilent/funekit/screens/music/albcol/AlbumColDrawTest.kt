package net.seilent.funekit.screens.music.albcol

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.screens.music.albcol.AlbumColGeo
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.albcol.AlbumColPaint
import net.seilent.funekit.screens.music.albcol.ColEntry
import net.seilent.funekit.screens.music.albcol.ColLook
import net.seilent.funekit.screens.music.albcol.albumColDraw
import net.seilent.funekit.screens.music.album.albumChrome
import net.seilent.funekit.screens.music.album.albumHeaderSide
import net.seilent.funekit.screens.music.sheet.MusicScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AlbumColDrawTest {
    private val sc = MusicScenes()
    private val s = sc.pageScale(sc.a105)

    private fun model(): AlbumColModel = runBlocking {
        val m = AlbumColModel(0L, 0)
        m.geometry(AlbumColGeo(s))
        val v = sc.fake.library.view()
        m.load(v)
        m.track.count(m.count, 0)
        m.show(v, 0, m.count - 1)
        m
    }

    private fun drawn(m: AlbumColModel, e: ColEntry): Bitmap {
        val chrome = albumChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = albumColDraw(m, s, sc.measurer, sc.fonts, sc.density, null, albumHeaderSide(sc.measurer, sc.fonts, s, sc.density, null, chrome), Ink.white, true)
        return sc.draw(sc.a105) { with(d) { row(e, ColLook(false, 0f, 0f), d.ink(e), null, "") } }
    }

    @Test
    fun theColumnTakesTheFlatGround() {
        val chrome = albumChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = albumColDraw(model(), s, sc.measurer, sc.fonts, sc.density, null, albumHeaderSide(sc.measurer, sc.fonts, s, sc.density, null, chrome), Ink.white, true)
        assertFalse(AlbumColPaint(d, chrome, Ink.white).grounded)
    }

    @Test
    fun aCoverlessEntryDrawsTheBoxTileWithItsTitle() {
        val m = model()
        val r = (0 until m.count).first { m.row(it) != null && m.row(it)!!.art == null }
        val e = m.entry(r)
        assertTrue(e.boxed)
        val g = m.geo!!
        val chrome = albumChrome(sc.measurer, sc.fonts, s, sc.density, null)
        val d = albumColDraw(m, s, sc.measurer, sc.fonts, sc.density, null, albumHeaderSide(sc.measurer, sc.fonts, s, sc.density, null, chrome), Ink.white, true)
        assertNotNull(d.ink(e).box)
        val b = drawn(m, e)
        val inked = (0 until g.side).any { y -> (0 until g.side).any { x -> b.getPixel(x, y) != Ink.bg.toArgb() } }
        assertTrue(inked)
    }

    @Test
    fun aHiddenAlbumDrawsNeitherItsArtNorItsRing() {
        val m = model()
        val r = (0 until m.count).first { m.row(it) != null }
        val e = m.entry(r)
        val g = m.geo!!
        val shown = drawn(m, e)
        val hidden = drawn(m, e.copy(hidden = true))
        val cx = g.ringX
        val cy = g.ringDrop
        assertNotEquals(Ink.bg.toArgb(), shown.getPixel(g.side / CENTRE, g.side / CENTRE))
        for (y in 0 until g.side) for (x in 0 until g.side) assertEquals("art px $x $y", Ink.bg.toArgb(), hidden.getPixel(x, y))
        assertEquals(Ink.bg.toArgb(), hidden.getPixel(cx, cy - g.ring / CENTRE))
    }

    companion object {
        private const val CENTRE = 2
    }
}
