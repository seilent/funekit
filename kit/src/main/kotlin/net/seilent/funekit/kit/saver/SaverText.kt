package net.seilent.funekit.kit.saver

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.BreakIterator
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import net.seilent.funekit.Fonts
import net.seilent.funekit.Weight
import net.seilent.funekit.gen.Tok
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class SaverStyle(val weight: Weight, val px: Int, val track64: Int, val saver: Boolean)

class RunGlyph(val face: Int, val gid: Int, val x64: Int, val dx64: Int, val dy64: Int, val byte: Int)

class GlyphRun(val g: List<RunGlyph>, val width64: Int)

interface SaverGlyphs {
    fun run(st: SaverStyle, s: String): GlyphRun

    fun width64(st: SaverStyle, s: String): Int

    fun box(face: Int, px: Int, gid: Int): SaverRect?

    fun hbox(face: Int, px: Int, gid: Int): IntArray?

    fun vbox(face: Int, px: Int, gid: Int): IntArray?
}

private const val HAN = 4
private const val OTHER = 5
private const val HALF_KANA = 6
private const val HANGUL = 7
private const val HAN_PLAIN = 8
private const val FACE_BITS = 40
private const val PX_BITS = 24

private fun px64(v: Int): Int = ((v + SUB_HALF) shr SUB_BITS) * SUB

private class Face(val type: Typeface, val em: Float, val rise: Float)

private class Ink(val box: SaverRect, val h: IntArray?, val v: IntArray?)

private class Shaped(val t: String, val starts: List<Int>, val face: IntArray, val adv: FloatArray)

class SaverText(fonts: FontFamily.Resolver) : SaverGlyphs {
    private val lock = Any()
    private val faces = Weight.entries.map {
        Face(fonts.resolve(Fonts.selawik, Fonts.weight(it), FontStyle.Normal, FontSynthesis.None).value as Typeface, 1f, 0f)
    } + listOf(
        Face(Typeface.DEFAULT, Tok.fontHanEm * Tok.saverCjkEm, Tok.fontHanRise * Tok.saverCjkEm + Tok.saverCjkRise),
        Face(Typeface.DEFAULT, Tok.saverCjkEm, Tok.saverCjkRise),
        Face(Typeface.DEFAULT, Tok.saverCjkEm, Tok.saverHalfRise),
        Face(Typeface.DEFAULT, Tok.saverHangulEm, Tok.saverHangulRise),
        Face(Typeface.DEFAULT, Tok.fontHanEm, Tok.fontHanRise),
    )
    private val paints = HashMap<Long, Paint>()
    private val ids = HashMap<String, Int>()
    private val texts = ArrayList<String>()
    private val inks = HashMap<Long, Ink>()
    private val path = Path()
    private val bounds = RectF()

    private fun key(face: Int, px: Int, gid: Int): Long = (face.toLong() shl FACE_BITS) or (px.toLong() shl PX_BITS) or gid.toLong()

    private fun paint(face: Int, px: Int): Paint = paints.getOrPut(key(face, px, 0)) {
        val f = faces[face]
        Paint().also {
            it.isAntiAlias = true
            it.isLinearText = true
            it.isSubpixelText = true
            it.typeface = f.type
            it.textSize = px * f.em
        }
    }

    private fun faceOf(st: SaverStyle, c: Int): Int = when {
        st.saver && cjkCp(c) -> when {
            hanCp(c) -> HAN
            halfKanaCp(c) -> HALF_KANA
            hangulCp(c) -> HANGUL
            else -> OTHER
        }
        hanCp(c) -> HAN_PLAIN
        else -> st.weight.ordinal
    }

    private fun upper(s: String): String = buildString(s.length) { for (c in s) append(if (c in 'a'..'z') c.uppercaseChar() else c) }

    private fun shape(st: SaverStyle, s: String): Shaped {
        val t = upper(s)
        val bi = BreakIterator.getCharacterInstance()
        bi.setText(t)
        val starts = ArrayList<Int>()
        var b = bi.first()
        while (b != BreakIterator.DONE && b < t.length) {
            starts += b
            b = bi.next()
        }
        val n = starts.size
        val face = IntArray(n) { k -> faceOf(st, t.codePointAt(starts[k])) }
        val adv = FloatArray(n)
        var k = 0
        while (k < n) {
            var e = k + 1
            while (e < n && face[e] == face[k]) e++
            val a = starts[k]
            val z = if (e < n) starts[e] else t.length
            val p = paint(face[k], st.px)
            var x = 0f
            for (j in k until e) {
                val to = p.getRunAdvance(t, a, z, a, z, false, if (j + 1 < n) starts[j + 1] else t.length)
                adv[j] = to - x
                x = to
            }
            k = e
        }
        return Shaped(t, starts, face, adv)
    }

    private fun adv64(face: Int, adv: Float): Int = if (face >= HAN) px64((adv * SUB).roundToInt()) else (adv * SUB).toInt()

    override fun run(st: SaverStyle, s: String): GlyphRun = synchronized(lock) {
        val sh = shape(st, s)
        val t = sh.t
        val starts = sh.starts
        val face = sh.face
        val adv = sh.adv
        val n = starts.size
        val out = ArrayList<RunGlyph>(n)
        var pen = 0
        var track = 0
        var x = 0
        var w = 0
        var byte = 0
        for (j in 0 until n) {
            val a = starts[j]
            val z = if (j + 1 < n) starts[j + 1] else t.length
            val text = t.substring(a, z)
            val gid = ids.getOrPut(text) { texts.size.also { texts += text } }
            if (j > 0) track += st.track64
            w = adv64(face[j], adv[j])
            if (face[j] >= HAN) pen = px64(pen + track) - track
            x = pen + track
            out += RunGlyph(face[j], gid, x, 0, 0, byte)
            pen += w
            byte += text.toByteArray(Charsets.UTF_8).size
        }
        GlyphRun(out, x + w)
    }

    override fun width64(st: SaverStyle, s: String): Int = synchronized(lock) {
        val sh = shape(st, s)
        var pen = 0
        for (j in sh.face.indices) {
            if (sh.face[j] >= HAN) pen = px64(pen)
            pen += adv64(sh.face[j], sh.adv[j]) + st.track64
        }
        pen
    }

    private fun ink(face: Int, px: Int, gid: Int): Ink = synchronized(lock) {
        inks.getOrPut(key(face, px, gid)) {
            val text = texts[gid]
            val rise = faces[face].rise * px
            path.reset()
            paint(face, px).getTextPath(text, 0, text.length, 0f, -rise, path)
            path.computeBounds(bounds, true)
            val box = if (bounds.width() <= 0f || bounds.height() <= 0f) SaverRect.NONE
            else SaverRect(floor(bounds.left).toInt(), floor(bounds.top).toInt(), ceil(bounds.right).toInt(), ceil(bounds.bottom).toInt())
            val h = if (bounds.right > bounds.left) intArrayOf((bounds.left * SUB).roundToInt(), (bounds.right * SUB).roundToInt()) else null
            val v = if (bounds.bottom > bounds.top) intArrayOf((bounds.top * SUB).roundToInt() - 1, (bounds.bottom * SUB).roundToInt() + 1) else null
            Ink(box, h, v)
        }
    }

    override fun box(face: Int, px: Int, gid: Int): SaverRect = ink(face, px, gid).box

    override fun hbox(face: Int, px: Int, gid: Int): IntArray? = ink(face, px, gid).h

    override fun vbox(face: Int, px: Int, gid: Int): IntArray? = ink(face, px, gid).v
}
