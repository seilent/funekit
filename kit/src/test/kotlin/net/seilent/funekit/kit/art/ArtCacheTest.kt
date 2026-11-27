package net.seilent.funekit.kit.art

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ArtCacheTest {
    private data class Ref(val id: Long, val name: String)

    private val folder = Ref(1, "cover")
    private val embedded = Ref(2, "embedded")

    private fun png(w: Int, h: Int, paint: (Int, Int) -> Int): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) b.setPixel(x, y, paint(x, y))
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun tile(px: Int) = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)

    @Test
    fun aCoverDecodesAtItsTilePxCentreCroppedInSoftware() {
        val bytes = png(60, 20) { x, _ -> if (x in 20 until 40) Color.BLUE else Color.RED }
        val b = decodeArt(bytes, 10)!!
        assertEquals(10, b.width)
        assertEquals(10, b.height)
        assertEquals(Bitmap.Config.ARGB_8888, b.config)
        for ((x, y) in listOf(0 to 0, 9 to 0, 0 to 9, 9 to 9, 5 to 5)) assertEquals(Color.BLUE, b.getPixel(x, y))
    }

    @Test
    fun anAppsPictureIsCentreCroppedToItsSideAndKeptWhole() {
        val src = Bitmap.createBitmap(60, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 60) src.setPixel(x, y, if (x in 20 until 40) Color.BLUE else Color.RED)
        val b = squareArt(src, 10)
        assertEquals(10, b.width)
        assertEquals(10, b.height)
        for ((x, y) in listOf(0 to 0, 9 to 0, 0 to 9, 9 to 9, 5 to 5)) assertEquals(Color.BLUE, b.getPixel(x, y))
        assertTrue("the app's own picture stays usable", !src.isRecycled && src.width == 60)
    }

    @Test
    fun everyPictureIsPowerOfTwoSampledToItsTileByItsBounds() {
        assertEquals(8, sampleOf(1000, 1000, 77))
        assertEquals(2, sampleOf(300, 160, 77))
        assertEquals(1, sampleOf(153, 200, 77))
        assertEquals(1, sampleOf(77, 77, 77))
        assertEquals(1, sampleOf(40, 60, 77))
        assertEquals(2, sampleOf(512, 512, 231))
        assertEquals(2, sampleOf(600, 300, 77))
        assertEquals(77, decodeArt(png(1000, 1000) { _, _ -> Color.GREEN }, 77)?.width)
        assertEquals(77, decodeArt(png(512, 512) { _, _ -> Color.GREEN }, 77)?.width)
        assertEquals(77, decodeArt(png(8, 8) { _, _ -> Color.GREEN }, 77)?.width)
    }

    @Test
    fun aFailedOrEmptyDecodeGivesNoBitmapAndCachesNothing() = runBlocking {
        assertNull(decodeArt(byteArrayOf(1, 2, 3), 10))
        assertNull(decodeArt(ByteArray(0), 10))
        val art = Art(1 shl 20)
        assertNull(art.cover(folder, 10) { byteArrayOf(1, 2, 3) })
        assertNull(art.cover(embedded, 10) { null })
        assertTrue(art.keys().isEmpty())
    }

    @Test
    fun aCachedCoverIsServedWithoutFetchingAgain() = runBlocking {
        val art = Art(1 shl 20)
        val first = art.cover(embedded, 10) { png(8, 8) { _, _ -> Color.RED } }
        assertNotNull(first)
        assertSame(first, art.cover(embedded, 10) { error("fetched") })
        assertSame(first, art[ArtKey.Cover(embedded, 10)])
        assertNull(art[ArtKey.Cover(embedded, 20)])
    }

    @Test
    fun iconsAndCoversEvictEachOtherByBytes() {
        val art = Art(3 * 10 * 10 * 4)
        val icon = ArtKey.Icon("0/com.example.a/a.png")
        art.put(icon, tile(10))
        art.put(ArtKey.Cover(folder, 10), tile(10))
        art.put(ArtKey.Cover(embedded, 10), tile(10))
        assertNotNull(art[icon])
        art.put(ArtKey.Cover(folder, 20), tile(10))
        assertNull(art[ArtKey.Cover(folder, 10)])
        assertNotNull(art[icon])
        assertNotNull(art[ArtKey.Cover(embedded, 10)])
        assertNotNull(art[ArtKey.Cover(folder, 20)])
        art.put(ArtKey.Cover(embedded, 20), tile(10))
        assertNull(art[icon])
        assertNotNull(art[ArtKey.Cover(embedded, 10)])
    }

    @Test
    fun theBudgetIsAShareOfTheMemoryClassSmallerOnLowRamDevices() {
        assertEquals(64 * 1024 * 1024 / 16, Art.budget(64, false))
        assertEquals(64 * 1024 * 1024 / 32, Art.budget(64, true))
    }

    @Test
    fun largeArtTakesHardwareFromApi28AndEverythingElseSoftware() {
        assertEquals(Bitmap.Config.ARGB_8888, configFor(27, true))
        assertEquals(Bitmap.Config.HARDWARE, configFor(28, true))
        assertEquals(Bitmap.Config.HARDWARE, configFor(35, true))
        assertEquals(Bitmap.Config.ARGB_8888, configFor(28, false))
        assertEquals(Bitmap.Config.ARGB_8888, configFor(35, false))
    }

    @Test
    fun largeArtInSoftwareIsTheDecodedTileOutsideTheCache() = runBlocking {
        val b = largeArt(12, Bitmap.Config.ARGB_8888) { png(24, 24) { _, _ -> Color.BLUE } }!!
        assertEquals(12, b.width)
        assertEquals(Bitmap.Config.ARGB_8888, b.config)
        assertEquals(Color.BLUE, b.getPixel(6, 6))
        assertNull(largeArt(12, Bitmap.Config.ARGB_8888) { null })
    }
}
