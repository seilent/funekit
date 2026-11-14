package net.seilent.funekit.kit.scrub

import net.seilent.funekit.core.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubTest {
    private val letters = listOf(
        "#", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "漢",
    )
    private val h = 3800f
    private val zone = Scrub.zone(400f, h, 30f, 200f)

    private fun started(): Scrub<String> = Scrub(letters).also {
        assertTrue(it.down(385f, 1850f, zone))
        assertNull(it.bucket)
    }

    @Test
    fun bucketsAreSectionOrder() {
        assertEquals(Section.count, letters.size)
        assertEquals(letters, (0 until Section.count).map { Section.letter(it) })
    }

    @Test
    fun zoneIsARightEdgeStripCappedAt200() {
        assertEquals(ScrubZone(370f, 1800f, 400f, 2000f), zone)
        assertEquals(ScrubZone(370f, 0f, 400f, 150f), Scrub.zone(400f, 150f, 30f, 200f))
    }

    @Test
    fun aDownAloneEmitsNothing() {
        val s = started()
        assertTrue(s.active)
        s.up()
        assertNull(s.bucket)
    }

    @Test
    fun firstMoveEmitsEvenOnTheDownBucket() {
        val s = started()
        assertEquals("r", s.move(1850f, h))
        assertNull(s.move(1860f, h))
    }

    @Test
    fun everyBucketMapsOverTheFullHeightAfterAStart() {
        for (i in letters.indices) {
            val s = started()
            assertEquals(letters[i], s.move(i * 100f + 50f, h))
            assertEquals(letters[i], s.bucket)
        }
    }

    @Test
    fun boundaries() {
        val s = started()
        assertEquals("#", s.move(0f, h))
        assertNull(s.move(99.5f, h))
        assertEquals("a", s.move(100f, h))
        assertEquals("z", s.move(2699.5f, h))
        assertEquals("あ", s.move(2700f, h))
        assertEquals("わ", s.move(3699.5f, h))
        assertEquals("漢", s.move(3700f, h))
        assertNull(s.move(h, h))
        assertNull(s.move(9000f, h))
        assertEquals("#", s.move(-40f, h))
    }

    @Test
    fun emitsOnlyOnChange() {
        val s = started()
        assertEquals("r", s.move(1810f, h))
        assertNull(s.move(1899f, h))
        assertEquals("s", s.move(1900f, h))
        assertNull(s.move(1950f, h))
        assertEquals("r", s.move(1899f, h))
    }

    @Test
    fun outsideTheZoneBeforeADragDoesNothing() {
        val s = Scrub(letters)
        for ((x, y) in listOf(200f to 1850f, 385f to 1799f, 385f to 2001f, 385f to 10f)) {
            assertFalse(s.down(x, y, zone))
            assertFalse(s.active)
            assertNull(s.move(50f, h))
            assertNull(s.bucket)
        }
    }

    @Test
    fun zoneEdgesStart() {
        val s = Scrub(letters)
        assertTrue(s.down(370f, 1900f, zone))
        assertEquals("s", s.move(1900f, h))
        assertTrue(Scrub(letters).down(400f, 1800f, zone))
        assertTrue(Scrub(letters).down(400f, 2000f, zone))
    }

    @Test
    fun upEndsTheDrag() {
        val s = started()
        s.move(50f, h)
        s.up()
        assertFalse(s.active)
        assertNull(s.bucket)
        assertNull(s.move(50f, h))
    }

    @Test
    fun noBucketsNeverStarts() {
        val s = Scrub(emptyList<String>())
        assertFalse(s.down(385f, 1850f, zone))
        assertFalse(s.active)
    }
}
