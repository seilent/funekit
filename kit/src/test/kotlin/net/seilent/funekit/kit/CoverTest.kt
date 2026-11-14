package net.seilent.funekit.kit

import androidx.compose.ui.text.font.FontWeight
import net.seilent.funekit.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverTest {
    private val res = RuntimeEnvironment.getApplication().resources
    private val covered = listOf(0x0D, 0x20, 0x23, 0x61, 0xE9, 0x175, 0x20AC)
    private val lacked = listOf(0xAA, 0xBA, 0x108, 0x5D0, 0x627, 0x3000, 0x3042, 0x6F22, 0xAC00)

    @Test
    fun everyWeightCoversTheSameThreeHundredFortyEightCodePoints() {
        for (w in Weight.entries) {
            val c = Cover.selawik(res, w)
            assertEquals(w.name, 348, c.count)
            for (cp in covered) assertTrue("${w.name} ${Integer.toHexString(cp)}", c.has(cp))
            for (cp in lacked) assertFalse("${w.name} ${Integer.toHexString(cp)}", c.has(cp))
        }
    }

    @Test
    fun theCoverOfAStyleIsReadFromTheFaceOfItsWeight() {
        val covers = Covers(res)
        assertEquals(Cover.selawik(res, Weight.Bold), covers.at(FontWeight.Bold))
        assertEquals(Cover.selawik(res, Weight.SemiLight), covers.at(FontWeight(SEMI_LIGHT)))
        assertEquals(Cover.selawik(res, Weight.Regular), covers.at(null))
    }

    @Test
    fun aCodePointMappedToGlyphZeroIsNotCovered() {
        val c = Cover.of(font())
        assertFalse(c.has('A'.code))
        assertTrue(c.has('B'.code))
        assertTrue(c.has('C'.code))
        assertFalse(c.has('D'.code))
        assertTrue(c.has('a'.code))
        assertFalse(c.has('b'.code))
        assertTrue(c.has('c'.code))
        assertEquals(4, c.count)
    }

    @Test
    fun aGlyphArrayEntryPastTheCmapLengthIsNotCovered() {
        val c = Cover.of(font(cut = 2))
        assertTrue(c.has('a'.code))
        assertFalse(c.has('c'.code))
        assertTrue(c.has('B'.code))
        assertEquals(3, c.count)
    }

    @Test
    fun runsSplitATextWhereTheCoverChanges() {
        val c = Cover.selawik(res, Weight.Bold)
        val runs = c.runs("ab 日本語 cd")
        assertEquals(listOf(0 to 3, 3 to 6, 6 to 9), runs.map { it.start to it.end })
        assertEquals(listOf(true, false, true), runs.map { it.covered })
        assertTrue(c.runs("abc").single().covered)
        assertEquals(listOf(0 to 2), c.runs("🎵").map { it.start to it.end })
    }

    @Test
    fun aKeycapOrAnEmojiSequenceStaysInOneRun() {
        val c = Cover.selawik(res, Weight.Bold)
        val keycap = c.runs("a1\uFE0F\u20E3b")
        assertEquals(listOf(0 to 5), keycap.map { it.start to it.end })
        assertTrue(keycap.single().covered)
        val family = "ab\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val runs = c.runs(family)
        assertEquals(listOf(0 to 2, 2 to 10), runs.map { it.start to it.end })
        assertEquals(listOf(true, false), runs.map { it.covered })
    }

    @Test
    fun aBidiIgnorableStaysInTheRunBeforeIt() {
        val c = Cover.selawik(res, Weight.Bold)
        assertFalse(c.has(0x200B))
        assertEquals(listOf(0 to 3), c.runs("a\u200Bb").map { it.start to it.end })
        assertEquals(listOf(0 to 2, 2 to 3), c.runs("\u65E5\u2060b").map { it.start to it.end })
        assertEquals(listOf(false, true), c.runs("\u65E5\u2060b").map { it.covered })
    }

    @Test
    fun aGlyphArrayEntryTakesTheSegmentDeltaAndGlyphZeroStaysUncovered() {
        val c = Cover.of(font(listOf(0x63, 0xFFFF), listOf(0x61, 0xFFFF), listOf(0xFFFD, 1), listOf(4, 0), listOf(3, 5, 0)))
        assertFalse(c.has('a'.code))
        assertTrue(c.has('b'.code))
        assertFalse(c.has('c'.code))
        assertEquals(1, c.count)
    }

    private fun font(cut: Int = 0): ByteArray = font(listOf(0x43, 0x63, 0xFFFF), listOf(0x41, 0x61, 0xFFFF), listOf(-0x41, 0, 1), listOf(0, 4, 0), listOf(5, 0, 7), cut)

    private fun font(ends: List<Int>, starts: List<Int>, deltas: List<Int>, offsets: List<Int>, glyphs: List<Int>, cut: Int = 0): ByteArray {
        val segs = ends.size
        val sub = 16 + segs * 8 + glyphs.size * 2
        val cmapLen = 4 + 8 + sub
        val b = ByteBuffer.allocate(12 + 16 + cmapLen)
        b.putInt(0x00010000).putShort(1).putShort(0).putShort(0).putShort(0)
        b.put("cmap".toByteArray()).putInt(0).putInt(28).putInt(cmapLen - cut)
        b.putShort(0).putShort(1).putShort(3).putShort(1).putInt(12)
        b.putShort(4).putShort(sub.toShort()).putShort(0).putShort((segs * 2).toShort()).putShort(0).putShort(0).putShort(0)
        ends.forEach { b.putShort(it.toShort()) }
        b.putShort(0)
        starts.forEach { b.putShort(it.toShort()) }
        deltas.forEach { b.putShort(it.toShort()) }
        offsets.forEach { b.putShort(it.toShort()) }
        glyphs.forEach { b.putShort(it.toShort()) }
        return b.array()
    }

    companion object {
        private const val SEMI_LIGHT = 350
    }
}
