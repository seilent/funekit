package net.seilent.funekit.screens.music.nowplaying

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NpBackdropTest {
    private val a = NpPicture(ImageBitmap(4, 4))
    private val b = NpPicture(ImageBitmap(4, 4))
    private val fade = Tok.motion_backdropFade.toLong()

    private fun alphas(d: NpBackdrop, now: Long) = d.layers(now).map { it.picture to it.alpha }

    @Test
    fun aPictureWithNoFocusCropsAboutItsCentre() {
        assertEquals(IntOffset(100, 0) to IntSize(200, 200), npCrop(400, 200, 100, 100, NpFocus.Center))
        assertEquals(IntOffset(0, 50) to IntSize(100, 100), npCrop(100, 200, 100, 100, NpFocus.Center))
    }

    @Test
    fun aPictureTooThinForTheBoxDrawsNoLayer() {
        assertNull(npCrop(8, 1, 100, 200, NpFocus.Center))
        assertNull(npCrop(1, 8, 200, 100, NpFocus.Center))
        val bg = Ink.bg.red * 255f
        val thin = shot(0f, NpLayer(NpPicture(solid(8, 1) { true }), 1f))
        assertTrue(abs(red(thin, 100) - bg) <= 2)
    }

    @Test
    fun aFocusPointMovesTheCropWithinThePicture() {
        assertEquals(IntOffset(200, 0), npCrop(400, 200, 100, 100, NpFocus(1f, 0.5f))!!.first)
        assertEquals(IntOffset(0, 0), npCrop(400, 200, 100, 100, NpFocus(0f, 0.5f))!!.first)
        assertEquals(IntOffset(0, 10), npCrop(100, 300, 100, 100, NpFocus(0.5f, 0.2f))!!.first)
    }

    @Test
    fun aNewPictureFadesInOverTheOneItReplaces() {
        val d = NpBackdrop()
        d.set(a, 1000)
        assertEquals(listOf(a to 0f), alphas(d, 1000))
        assertEquals(listOf(a to 0.5f), alphas(d, 1000 + fade / 2))
        d.stepTo(1000 + fade)
        assertFalse(d.fading(1000 + fade))
        assertEquals(listOf(a to 1f), alphas(d, 1000 + fade))
        d.set(b, 4000)
        assertEquals(listOf(a to 1f, b to 0.5f), alphas(d, 4000 + fade / 2))
        d.stepTo(4000 + fade)
        assertEquals(listOf(b to 1f), alphas(d, 4000 + fade))
    }

    private fun solid(w: Int, h: Int, white: (Int) -> Boolean): ImageBitmap = ImageBitmap(w, h).also { img ->
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            for (x in 0 until w) drawRect(if (white(x)) Ink.white else Ink.black, Offset(x.toFloat(), 0f), Size(1f, h.toFloat()))
        }
    }

    @Test
    fun aChangeDuringAFadeFreezesTheFrameShownThenIntoOneLayer() {
        val d = NpBackdrop()
        d.size(4, 4)
        d.set(a, 0)
        d.set(b, fade / 2)
        val held = d.layers(fade / 2)
        assertEquals(2, held.size)
        assertTrue(held[0].picture.whole)
        assertEquals(1f, held[0].alpha)
        assertEquals(4, held[0].picture.image.width)
        assertEquals(b to 0f, held[1].picture to held[1].alpha)
        for (k in 1..SKIPS) d.set(NpPicture(ImageBitmap(4, 4)), fade / 2 + k)
        assertEquals(2, d.layers(fade / 2 + SKIPS).size)
        d.set(null, fade)
        val gone = d.layers(fade + fade / 2)
        assertEquals(1, gone.size)
        assertTrue(gone.single().picture.whole)
        assertEquals(0.5f, gone.single().alpha)
        d.stepTo(2 * fade)
        assertTrue(d.layers(2 * fade).isEmpty())
    }

    @Test
    fun beforeItsFirstLayoutNothingIsHeldUnderAChange() {
        val d = NpBackdrop()
        d.set(a, 0)
        d.set(b, fade / 2)
        assertEquals(listOf(b to 0f), alphas(d, fade / 2))
    }

    @Test
    fun theFrozenFrameHoldsTheGroundUnderTheHalfShownPicture() {
        val d = NpBackdrop()
        d.size(4, 4)
        d.set(NpPicture(solid(4, 4) { true }), 0)
        d.set(b, fade / 2)
        val frozen = d.layers(fade / 2).first().picture.image.asAndroidBitmap()
        val bg = Ink.bg.red * 255f
        assertTrue(abs(((frozen.getPixel(2, 2) shr 16) and 0xFF) - over(255f, 0.5f, bg)) <= 2)
    }

    @Test
    fun aCutShowsThePictureAtOnce() {
        val d = NpBackdrop()
        d.set(a, 0)
        d.set(b, fade / 4, cut = true)
        assertEquals(listOf(b to 1f), alphas(d, fade / 4))
    }

    private fun shot(dim: Float, layer: NpLayer? = null): Bitmap {
        val white = solid(8, 8) { true }
        val img = ImageBitmap(100, 200)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(100f, 200f)) {
            drawRect(Ink.black)
            npBackdrop(listOf(layer ?: NpLayer(NpPicture(white), 1f)), dim)
        }
        return img.asAndroidBitmap()
    }

    private fun red(b: Bitmap, y: Int, x: Int = 50) = (b.getPixel(x, y) shr 16) and 0xFF

    private fun over(top: Float, a: Float, under: Float) = top * a + under * (1 - a)

    @Test
    fun theShadeDarkensTowardTheBottomWithTheGroundColourAndTheDimAddsTheSheetScrim() {
        val bg = Ink.bg.red * 255f
        val plain = shot(0f)
        assertTrue(abs(red(plain, 0) - over(bg, Tok.backdrop_stop0_alpha, 255f)) <= 3)
        assertTrue(abs(red(plain, 199) - over(bg, Tok.backdrop_stop2_alpha, 255f)) <= 3)
        assertTrue(abs(red(plain, 110) - over(bg, Tok.backdrop_stop1_alpha, 255f)) <= 3)
        val dimmed = shot(1f)
        assertTrue(abs(red(dimmed, 199) - over(bg, Tok.scrim_sheet_alpha, over(bg, Tok.backdrop_stop2_alpha, 255f))) <= 3)
    }

    @Test
    fun theGroundShowsUnderAHalfShownPicture() {
        val bg = Ink.bg.red * 255f
        val half = shot(0f, NpLayer(NpPicture(solid(8, 8) { true }), 0.5f))
        assertTrue(abs(red(half, 0) - over(bg, Tok.backdrop_stop0_alpha, over(255f, 0.5f, bg))) <= 3)
    }

    @Test
    fun aWholePictureFillsTheBoxWhereAPhotoIsCropped() {
        val strip = solid(8, 4) { it > 0 }
        val whole = shot(0f, NpLayer(NpPicture(strip, whole = true), 1f))
        val crop = shot(0f, NpLayer(NpPicture(strip), 1f))
        assertTrue(red(whole, 50, 5) < 100)
        assertTrue(red(crop, 50, 5) > 150)
    }

    companion object {
        private const val SKIPS = 10
    }
}
