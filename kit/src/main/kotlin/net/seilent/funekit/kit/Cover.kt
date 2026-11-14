package net.seilent.funekit.kit

import android.content.res.Resources
import android.icu.text.BreakIterator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import net.seilent.funekit.Fonts
import net.seilent.funekit.Weight

private const val U16 = 2
private const val TABLES_AT = 4
private const val RECORDS_AT = 12
private const val RECORD = 16
private const val TABLE_OFFSET_AT = 8
private const val TABLE_LENGTH_AT = 12
private const val CMAP_COUNT_AT = 2
private const val CMAP_RECORDS_AT = 4
private const val CMAP_RECORD = 8
private const val SUB_OFFSET_AT = 4
private const val SEG_X2_AT = 6
private const val ENDS_AT = 14
private const val WINDOWS = 3
private const val UNICODE_BMP = 1
private const val FORMAT4 = 4
private const val LAST = 0xFFFF
private const val GLYPH_MASK = 0xFFFF
private const val BYTE = 0xFF
private const val SHIFT = 8
private const val TAG = "cmap"

internal val clusterBreaks = ThreadLocal.withInitial { BreakIterator.getCharacterInstance() }

class CoverRun(val start: Int, val end: Int, val covered: Boolean)

class Cover(private val starts: IntArray, private val ends: IntArray) {
    val count: Int get() = starts.indices.sumOf { ends[it] - starts[it] + 1 }

    fun has(cp: Int): Boolean {
        var lo = 0
        var hi = starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                cp < starts[mid] -> hi = mid - 1
                cp > ends[mid] -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    fun runs(text: String): List<CoverRun> {
        val out = mutableListOf<CoverRun>()
        val clusters = clusterBreaks.get().also { it.setText(text) }
        var i = clusters.first()
        var next = clusters.next()
        while (next != BreakIterator.DONE) {
            val cp = text.codePointAt(i)
            val last = out.lastOrNull()
            val on = if (last != null && bidiIgnorable(cp)) last.covered else has(cp)
            if (last != null && last.covered == on) out[out.lastIndex] = CoverRun(last.start, next, on) else out += CoverRun(i, next, on)
            i = next
            next = clusters.next()
        }
        return out
    }

    companion object {
        private val none = Cover(IntArray(0), IntArray(0))
        private val loaded = HashMap<Int, Cover>()

        fun of(font: ByteArray): Cover {
            fun u16(at: Int) = (font[at].toInt() and BYTE shl SHIFT) or (font[at + 1].toInt() and BYTE)
            fun u32(at: Int) = (u16(at) shl (SHIFT + SHIFT)) or u16(at + U16)
            var cmap = -1
            var cmapEnd = 0
            for (t in 0 until u16(TABLES_AT)) {
                val at = RECORDS_AT + t * RECORD
                if (String(font, at, TAG.length, Charsets.US_ASCII) == TAG) {
                    cmap = u32(at + TABLE_OFFSET_AT)
                    cmapEnd = minOf(font.size, cmap + u32(at + TABLE_LENGTH_AT))
                }
            }
            if (cmap < 0) return none
            var sub = -1
            for (r in 0 until u16(cmap + CMAP_COUNT_AT)) {
                val at = cmap + CMAP_RECORDS_AT + r * CMAP_RECORD
                if (u16(at) == WINDOWS && u16(at + U16) == UNICODE_BMP) sub = cmap + u32(at + SUB_OFFSET_AT)
            }
            if (sub < 0 || u16(sub) != FORMAT4) return none
            val segs = u16(sub + SEG_X2_AT) / U16
            val endsAt = sub + ENDS_AT
            val startsAt = endsAt + segs * U16 + U16
            val deltasAt = startsAt + segs * U16
            val rangesAt = deltasAt + segs * U16
            val s = ArrayList<Int>()
            val e = ArrayList<Int>()
            for (k in 0 until segs) {
                val start = u16(startsAt + k * U16)
                if (start == LAST) continue
                val delta = u16(deltasAt + k * U16)
                val range = u16(rangesAt + k * U16)
                for (cp in start..u16(endsAt + k * U16)) {
                    val g = if (range == 0) {
                        (cp + delta) and GLYPH_MASK
                    } else {
                        val entry = rangesAt + k * U16 + range + (cp - start) * U16
                        val v = if (entry + U16 > cmapEnd) 0 else u16(entry)
                        if (v == 0) 0 else (v + delta) and GLYPH_MASK
                    }
                    if (g == 0) continue
                    if (e.isNotEmpty() && e.last() == cp - 1) {
                        e[e.lastIndex] = cp
                    } else {
                        s += cp
                        e += cp
                    }
                }
            }
            return Cover(s.toIntArray(), e.toIntArray())
        }

        fun of(res: Resources, font: Int): Cover = synchronized(loaded) { loaded.getOrPut(font) { of(res.openRawResource(font).use { it.readBytes() }) } }

        fun selawik(res: Resources, w: Weight): Cover = of(res, Fonts.file(w))
    }
}

class Covers(private val res: Resources) {
    fun at(w: FontWeight?): Cover = Cover.selawik(res, Weight.entries.firstOrNull { Fonts.weight(it) == w } ?: Weight.Regular)
}

@Composable
fun rememberCovers(): Covers {
    val res = LocalContext.current.resources
    return remember(res) { Covers(res) }
}
