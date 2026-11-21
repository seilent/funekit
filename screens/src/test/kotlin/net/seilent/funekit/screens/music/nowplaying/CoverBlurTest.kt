package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.scale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverBlurTest {
    private val white = 0xFFFFFF
    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val v1x3 = scale(Window(720, 960, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)

    @Test
    fun theBoxRadiusFollowsTheSigmaAtTheStandardScale() {
        assertEquals(1, CoverBlur.radius(CoverBlur.sigma(v1)))
        assertEquals(4, CoverBlur.radius(CoverBlur.sigma(v1x3)))
    }

    @Test
    fun theGridIsAQuarterOfTheScreenBox() {
        assertArrayEquals(intArrayOf(180, 320), CoverBlur.grid(720, 1280))
        assertNull(CoverBlur.cover(IntArray(16), 4, 4, 3, 3, v1))
    }

    @Test
    fun aPictureTooThinForItsCropMakesNoBlur() {
        assertNull(CoverBlur.cover(IntArray(1), 1, 1, 1080, 2400, v1))
        assertNull(CoverBlur.cover(IntArray(2), 2, 1, 1080, 2400, v1))
    }

    @Test
    fun theReductionAveragesTheCentredCrop() {
        val w = 8
        val h = 4
        val src = IntArray(w * h) { i -> if (i % w in 2 until 6) white else 0 }
        assertArrayEquals(intArrayOf(white), CoverBlur.area(src, w, h, 1, 1))
        val g = CoverBlur.cover(src, w, h, 4, 4, v1)!!
        assertEquals(1, g.w)
        assertEquals(white, g.px.single())
    }

    @Test
    fun aSmallCoverIsScaledUpFromItsCentredCrop() {
        val src = intArrayOf(0, white, white, 0, 0, white, white, 0)
        val g = CoverBlur.cover(src, 4, 2, 16, 16, v1)!!
        assertEquals(4, g.w)
        assertTrue(g.px.all { it == white })
    }

    @Test
    fun theUpscaleWeighsTheTwoNearestPixelsAndRounds() {
        assertArrayEquals(intArrayOf(0, 64, 191, 255), CoverBlur.bilinear(intArrayOf(0, 0xFF), 2, 0, 0, 2, 1, 4, 1))
    }

    @Test
    fun theBlurKeepsAFlatPictureFlatAndRunsThreePasses() {
        val flat = IntArray(25) { white }
        CoverBlur.blur(flat, 5, 5, 1)
        assertArrayEquals(IntArray(25) { white }, flat)
        val row = intArrayOf(0, 0, 0xFF, 0, 0)
        CoverBlur.blur(row, 5, 1, 1)
        assertArrayEquals(intArrayOf(38, 57, 66, 57, 38), row)
    }

    @Test
    fun aSeeThroughPixelBlendsOverTheTileColour() {
        val tile = Tok.color_tile.toInt() and white
        assertEquals(tile, CoverBlur.overTile(0x00FFFFFF))
        assertEquals(white, CoverBlur.overTile(0xFFFFFFFF.toInt()))
        val ch = (255 * 128 + 0x20 * 127 + 127) / 255
        assertEquals(ch shl 16 or (ch shl 8) or ch, CoverBlur.overTile(0x80FFFFFF.toInt()))
    }
}
