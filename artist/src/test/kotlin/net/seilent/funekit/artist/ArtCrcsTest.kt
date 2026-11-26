package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import java.util.zip.CRC32

class ArtCrcsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val utada = "b539e453-c4fe-47e3-8a07-8517eac74429"
    private val time = FakeTime()
    private val dir by lazy { tmp.newFolder("art") }
    private val cache by lazy { ArtCache(dir) }
    private val one = byteArrayOf(1, 2, 3)
    private val two = byteArrayOf(4, 5, 6)
    private val other = byteArrayOf(9)

    private fun hit(source: ByteArray = byteArrayOf(8)) =
        cache.putHit(ArtCache.Entry(utada, "宇多田ヒカル", utada, Sources.DEEZER, null, "https://e/$utada.jpg", time.now++, "1"), source)

    private fun crc(b: ByteArray) = CRC32().apply { update(b) }.value

    private fun hashOf(k: String) = java.security.MessageDigest.getInstance("SHA-1").digest(k.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private fun meta() = File(dir, "${hashOf(utada)}.meta")

    private fun jpg(w: Int, h: Int) = File(dir, "${hashOf(utada)}.${w}x$h.jpg")

    private fun props() = Properties().apply { meta().reader().use { load(it) } }

    private fun field(): String? = props().getProperty("renders")

    private fun old() {
        val p = props()
        p.remove("renders")
        meta().writer().use { p.store(it, null) }
    }

    @Test
    fun theMetaHoldsEachRenderCrcSoCrcsReadsNoJpeg() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        cache.putRendered(utada, 360, 480, two)
        assertEquals(setOf(crc(one), crc(two)), cache.crcs(utada))
        assertEquals("240x320:${crc(one)},360x480:${crc(two)}", field())
        jpg(240, 320).writeBytes(other)
        jpg(360, 480).writeBytes(other)
        assertEquals(setOf(crc(one), crc(two)), cache.crcs(utada))
    }

    @Test
    fun anOldMetaReadsItsJpegsOnceAndKeepsTheirCrcs() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        cache.putRendered(utada, 360, 480, two)
        old()
        assertEquals(setOf(crc(one), crc(two)), cache.crcs(utada))
        assertEquals("240x320:${crc(one)},360x480:${crc(two)}", field())
        jpg(240, 320).writeBytes(other)
        jpg(360, 480).writeBytes(other)
        assertEquals(setOf(crc(one), crc(two)), cache.crcs(utada))
    }

    @Test
    fun aRenderOfAnOldMetaKeepsTheRendersAlreadyThere() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        old()
        cache.putRendered(utada, 360, 480, two)
        assertEquals(setOf(crc(one), crc(two)), cache.crcs(utada))
    }

    @Test
    fun aRenderAgainAtOneSizeReplacesItsCrc() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        cache.putRendered(utada, 240, 320, two)
        val c = cache.crcs(utada)
        assertTrue(crc(two) in c)
        assertFalse(crc(one) in c)
    }

    @Test
    fun aRenderRemovedOutsideTheKitKeepsItsCrc() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        assertTrue(jpg(240, 320).delete())
        assertTrue(crc(one) in cache.crcs(utada))
    }

    @Test
    fun aBadRenderItemIsSkipped() {
        hit()
        val p = props()
        p.setProperty("renders", "240x320:x,360x480:${crc(two)}")
        meta().writer().use { p.store(it, null) }
        assertEquals(setOf(crc(two)), cache.crcs(utada))
    }

    @Test
    fun aRenderWhoseJpegCannotBeWrittenIsListedFirst() {
        hit()
        File(dir, "${hashOf(utada)}.240x320.jpg.tmp").mkdir()
        assertThrows(StoreException::class.java) { cache.putRendered(utada, 240, 320, one) }
        assertTrue(crc(one) in cache.crcs(utada))
        assertNull(cache.rendered(utada, 240, 320))
    }

    @Test
    fun aRenderWhoseMetaCannotBeSavedWritesNoJpeg() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        File(dir, "${hashOf(utada)}.meta.tmp").mkdir()
        assertThrows(StoreException::class.java) { cache.putRendered(utada, 360, 480, two) }
        assertFalse(jpg(360, 480).exists())
        assertEquals("240x320:${crc(one)}", field())
    }

    @Test
    fun aNewHitOrADropLeavesNoRenderCrc() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        hit(byteArrayOf(7))
        assertFalse(crc(one) in cache.crcs(utada))
        assertEquals("", field())
        cache.drop(utada)
        assertEquals(emptySet<Long>(), cache.crcs(utada))
        assertFalse(meta().exists())
    }

    @Test
    fun aRenderOfAKeyWithNoHitWritesNothing() {
        cache.putMiss(utada, time.now, setOf(Sources.DEEZER), Size(240, 320))
        cache.putRendered(utada, 240, 320, one)
        assertNull(cache.rendered(utada, 240, 320))
        assertEquals(emptySet<Long>(), cache.crcs(utada))
        assertFalse(meta().exists())
        assertTrue(dir.listFiles { f -> f.name.endsWith(".jpg") }!!.isEmpty())
    }

    @Test
    fun aFillAndARenderLeaveTheListing() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        old()
        val l = cache.entries()
        cache.crcs(utada)
        cache.putRendered(utada, 360, 480, two)
        assertSame(l, cache.entries())
    }

    @Test
    fun aSourceClearOfAnOldMetaFillsNoField() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        old()
        File(dir, "${hashOf(utada)}.meta.tmp").mkdir()
        val seen = HashMap<String, Set<Long>>()
        val gone = cache.clearSource(Sources.DEEZER) { e, c -> seen[e.key] = c }
        assertEquals(listOf(utada), gone.map { it.key })
        assertEquals(setOf(crc(one)), seen[utada])
    }

    @Test
    fun aClearAllOfAnOldMetaFillsNoField() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        old()
        File(dir, "${hashOf(utada)}.meta.tmp").mkdir()
        val seen = HashMap<String, Set<Long>>()
        val gone = cache.clearAll { e, c -> seen[e.key] = c }
        assertEquals(listOf(utada), gone.map { it.key })
        assertEquals(setOf(crc(one)), seen[utada])
    }

    @Test
    fun aFillThatCannotBeSavedThrows() {
        hit()
        cache.putRendered(utada, 240, 320, one)
        old()
        File(dir, "${hashOf(utada)}.meta.tmp").mkdir()
        assertThrows(StoreException::class.java) { cache.crcs(utada) }
    }
}
