package net.seilent.funekit.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class WrapTest {
    private fun width(s: String) = s.length * 64

    private fun lines(s: String, cols: Int, rows: Int) =
        wrapRanges(s, cols * 64, rows, { b, e -> width(s.substring(b, e)) }, { p, e -> nextCluster(s, p, e) }).map { s.substring(it.first, it.second) }

    @Test
    fun wordsFillALineUntilTheNextOneDoesNotFit() {
        assertEquals(listOf("aa bb", "cc"), lines("aa bb cc", 5, 3))
        assertEquals(listOf("aa bb"), lines("aa bb cc", 5, 1))
    }

    @Test
    fun aWordWiderThanTheLineBreaksByCluster() {
        assertEquals(listOf("abc", "def", "gh"), lines("abcdefgh", 3, 4))
        assertEquals(listOf("ab", "cde", "fg"), lines("ab cdefg", 3, 4))
    }

    @Test
    fun wrapAtCutsAtTheLastFittingSpaceWhenTheRestFits() {
        assertEquals(5, wrapAt("aa bb cc", 5 * 64, ::width))
        assertEquals(3, wrapAt("aaaaaaa b", 3 * 64, ::width))
    }
}
