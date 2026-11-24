package net.seilent.funekit.kit

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.BreakIterator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isSpecified
import net.seilent.funekit.Role
import net.seilent.funekit.gen.Tok
import java.text.Bidi
import kotlin.math.abs
import kotlin.math.roundToInt

class WordRun(val layout: TextLayoutResult, val x: Float, val dx: Float, val dy: Float = 0f)

class Word(
    val text: String,
    val runs: List<WordRun>,
    val width: Float,
    val inkLeft: Float,
    val inkRight: Float,
    val inkTop: Float,
    val inkBottom: Float,
) {
    val inked: Boolean get() = inkRight > inkLeft && inkBottom > inkTop

    companion object {
        val none = Word("", emptyList(), 0f, 0f, 0f, 0f, 0f)
    }
}

private const val HALF = 2f
private const val ALM = 0x061C
private const val CGJ = 0x034F
private const val BOM = 0xFEFF
private const val ZWSP = 0x200B
private const val LRM = 0x200E
private const val RLM = 0x200F
private const val LRE = 0x202A
private const val RLO = 0x202E
private const val WJ = 0x2060
private const val INVISIBLE_PLUS = 0x2064
private const val LRI = 0x2066
private const val PDI = 0x2069
private const val VS1 = 0xFE00
private const val VS16 = 0xFE0F
private const val VS17 = 0xE0100
private const val VS256 = 0xE01EF
private const val ARABIC = 0x0600
private const val ARABIC_END = 0x06FF
private const val ARABIC_SUPPLEMENT = 0x0750
private const val ARABIC_SUPPLEMENT_END = 0x077F
private const val ARABIC_EXTENDED = 0x0870
private const val ARABIC_EXTENDED_END = 0x08FF
private const val ARABIC_FORMS_A = 0xFB50
private const val ARABIC_FORMS_A_END = 0xFDFF
private const val ARABIC_FORMS_B = 0xFE70
private const val ARABIC_FORMS_B_END = 0xFEFF
private const val ARABIC_EXTENDED_C = 0x10EFD
private const val ARABIC_EXTENDED_C_END = 0x10EFF
private const val HAN_A = 0x3400
private const val HAN_A_END = 0x4DBF
private const val HAN = 0x4E00
private const val HAN_END = 0x9FFF
private const val HAN_COMPAT = 0xF900
private const val HAN_COMPAT_END = 0xFAFF
private const val HAN_B = 0x20000
private const val HAN_B_END = 0x3134F
private const val HAN_COMPAT_SUPPLEMENT = 0x2F800
private const val HAN_COMPAT_SUPPLEMENT_END = 0x2FA1F
private const val ITERATION = 0x3005
private const val ITERATION_END = 0x3007
private const val HANGZHOU = 0x3021
private const val HANGZHOU_END = 0x3029
private const val VERTICAL_ITERATION = 0x303B

private val shifts = HashMap<List<Any?>, Float>()

private fun glyphPaint(style: TextStyle, fonts: FontFamily.Resolver, d: Density): Paint {
    val face = fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal, FontStyle.Normal, FontSynthesis.None).value as Typeface
    val size = with(d) { style.fontSize.toPx() }
    val track = if (style.letterSpacing.isSpecified) with(d) { style.letterSpacing.toPx() } else 0f
    return Paint().also {
        it.isAntiAlias = true
        it.isLinearText = true
        it.isSubpixelText = true
        it.typeface = face
        it.textSize = size
        it.letterSpacing = track / size
    }
}

private fun inkOf(p: Paint, s: String): RectF {
    val path = Path()
    p.getTextPath(s, 0, s.length, 0f, 0f, path)
    return RectF().also { path.computeBounds(it, true) }
}

private fun ltr(t: String): Boolean = !Bidi.requiresBidi(t.toCharArray(), 0, t.length) || Bidi(t, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()

private fun inkOf(p: Paint, s: String, rtl: Boolean, c: String): RectF {
    if (rtl) return inkOf(p, RLM.toChar() + s)
    if (ltr(s)) return inkOf(p, s)
    val mark = inkOf(p, LRM.toChar() + c).left - inkOf(p, c).left
    return inkOf(p, LRM.toChar() + s).also { it.offset(-mark, 0f) }
}

private fun inked(p: Paint, t: String, start: Int, end: Int, backward: Boolean): String? {
    var i = if (backward) end else start
    while (if (backward) i > start else i < end) {
        val n = Character.charCount(if (backward) t.codePointBefore(i) else t.codePointAt(i))
        val at = if (backward) i - n else i
        val c = t.substring(at, at + n)
        if (!inkOf(p, c).isEmpty) return c
        i = if (backward) at else at + n
    }
    return null
}

private fun leftInked(p: Paint, t: String, base: Int): String? {
    if (base != Bidi.DIRECTION_RIGHT_TO_LEFT && !Bidi.requiresBidi(t.toCharArray(), 0, t.length)) return inked(p, t, 0, t.length, false)
    val bidi = Bidi(t, base)
    val order = Array<Any>(bidi.runCount) { it }
    Bidi.reorderVisually(ByteArray(bidi.runCount) { bidi.getRunLevel(it).toByte() }, 0, order, 0, order.size)
    for (k in order) {
        val i = k as Int
        inked(p, t, bidi.getRunStart(i), bidi.getRunLimit(i), (bidi.getRunLevel(i) and 1) == 1)?.let { return it }
    }
    return null
}

private val edges = HashMap<Typeface?, Boolean>()

private fun advance(p: Paint, t: String, start: Int, end: Int, rtl: Boolean): Float = p.getRunAdvance(t, start, end, start, end, rtl, end)

private fun spacedAtEnds(p: Paint, c: String): Boolean = edges.getOrPut(p.typeface) {
    val probe = Paint(p).also { it.letterSpacing = 1f }
    val flat = Paint(p).also { it.letterSpacing = 0f }
    abs(probe.measureText(c) - flat.measureText(c)) >= probe.textSize / HALF
}

private fun shiftOf(p: Paint, c: String): Float {
    if (p.letterSpacing == 0f) return 0f
    val key = listOf(p.typeface, p.textSize, p.letterSpacing, c)
    return shifts.getOrPut(key) { inkOf(p, c).left - inkOf(Paint(p).also { it.letterSpacing = 0f }, c).left }
}

fun bidiIgnorable(cp: Int): Boolean = cp == ALM || cp == CGJ || cp == BOM || cp in ZWSP..RLM || cp in LRE..RLO ||
    cp in WJ..INVISIBLE_PLUS || cp in LRI..PDI || cp in VS1..VS16 || cp in VS17..VS256

private fun tracked(t: String, start: Int, end: Int): Int {
    var n = 0
    var i = start
    while (i < end) {
        val cp = t.codePointAt(i)
        if (!bidiIgnorable(cp)) n++
        i += Character.charCount(cp)
    }
    return n
}

private fun joinedArabic(cp: Int): Boolean = cp in ARABIC..ARABIC_END || cp in ARABIC_SUPPLEMENT..ARABIC_SUPPLEMENT_END ||
    cp in ARABIC_EXTENDED..ARABIC_EXTENDED_END || cp in ARABIC_FORMS_A..ARABIC_FORMS_A_END || cp in ARABIC_FORMS_B..ARABIC_FORMS_B_END ||
    cp in ARABIC_EXTENDED_C..ARABIC_EXTENDED_C_END

private fun scriptOf(t: String, start: Int, end: Int): Character.UnicodeScript? {
    var i = start
    while (i < end) {
        val cp = t.codePointAt(i)
        if (joinedArabic(cp)) return Character.UnicodeScript.ARABIC
        val s = Character.UnicodeScript.of(cp)
        if (s != Character.UnicodeScript.INHERITED) return s
        i += Character.charCount(cp)
    }
    return null
}

private class ScriptRun(val start: Int, val end: Int, val script: Character.UnicodeScript?)

private fun scriptRuns(t: String, start: Int, end: Int): List<ScriptRun> {
    val b = clusterBreaks.get().also { it.setText(t.substring(start, end)) }
    val out = ArrayList<ScriptRun>()
    var from = start
    var script: Character.UnicodeScript? = null
    var at = b.first()
    var next = b.next()
    while (next != BreakIterator.DONE) {
        val s = scriptOf(t, start + at, start + next)
        if (s != null) {
            if (script != null && s != script) {
                out += ScriptRun(from, start + at, script)
                from = start + at
            }
            script = s
        }
        at = next
        next = b.next()
    }
    out += ScriptRun(from, end, script)
    return out
}

private val spacedScripts = HashMap<Pair<Typeface?, Character.UnicodeScript?>, Boolean>()

private fun spacedRuns(p: Paint, flat: Paint, track: Float, t: String, start: Int, end: Int, rtl: Boolean): List<ScriptRun> {
    if (track == 0f) return emptyList()
    return scriptRuns(t, start, end).filter { r ->
        tracked(t, r.start, r.end) > 0 && r.script != Character.UnicodeScript.ARABIC && spacedScripts.getOrPut(Pair(p.typeface, r.script)) {
            abs(advance(p, t, r.start, r.end, rtl) - advance(flat, t, r.start, r.end, rtl)) >= abs(track) / HALF
        }
    }
}

private fun reportedTracks(p: Paint, flat: Paint, track: Float, t: String, rtl: Boolean): Int =
    if (track == 0f) 0 else ((advance(p, t, 0, t.length, rtl) - advance(flat, t, 0, t.length, rtl)) / track).roundToInt()

private fun width64Of(p: Paint, flat: Paint, track: Float, t: String, start: Int, end: Int, rtl: Boolean): Float =
    advance(flat, t, start, end, rtl) + spacedRuns(p, flat, track, t, start, end, rtl).sumOf { tracked(t, it.start, it.end) } * track

private fun flatOf(p: Paint): Paint = Paint(p).also { it.letterSpacing = 0f }

private class Piece(val start: Int, val end: Int, val covered: Boolean, val rtl: Boolean, val han: Boolean = false)

private fun han(cp: Int): Boolean = cp in HAN_A..HAN_A_END || cp in HAN..HAN_END || cp in HAN_COMPAT..HAN_COMPAT_END || cp in HAN_B..HAN_B_END ||
    cp in HAN_COMPAT_SUPPLEMENT..HAN_COMPAT_SUPPLEMENT_END || cp in ITERATION..ITERATION_END || cp in HANGZHOU..HANGZHOU_END || cp == VERTICAL_ITERATION

private fun hanOn(p: Paint, minPx: Float): Boolean = p.textSize >= minPx

private fun hanIn(t: String): Boolean = t.codePoints().anyMatch(::han)

private fun hanSplit(t: String, pieces: List<Piece>): List<Piece> = pieces.flatMap { r ->
    val out = ArrayList<Piece>()
    var i = r.start
    while (i < r.end) {
        val h = han(t.codePointAt(i))
        var j = i + Character.charCount(t.codePointAt(i))
        while (!h && j < r.end && !han(t.codePointAt(j))) j += Character.charCount(t.codePointAt(j))
        out += Piece(i, j, r.covered, r.rtl, h)
        i = j
    }
    out
}

private fun hanPaint(p: Paint): Paint = Paint(p).also {
    it.textSize = p.textSize * Tok.fontHanEm
    it.letterSpacing = p.letterSpacing * p.textSize / it.textSize
}

private fun hanRise(p: Paint): Float = Tok.fontHanRise * p.textSize

private fun visual(t: String, runs: List<CoverRun>, base: Int): List<Piece> {
    if (base != Bidi.DIRECTION_RIGHT_TO_LEFT && !Bidi.requiresBidi(t.toCharArray(), 0, t.length)) return runs.map { Piece(it.start, it.end, it.covered, false) }
    val bidi = Bidi(t, base)
    val pieces = ArrayList<Piece>()
    val levels = ArrayList<Byte>()
    for (r in runs) {
        var i = r.start
        while (i < r.end) {
            val level = bidi.getLevelAt(i)
            var j = i + 1
            while (j < r.end && bidi.getLevelAt(j) == level) j++
            pieces += Piece(i, j, r.covered, (level and 1) == 1)
            levels += level.toByte()
            i = j
        }
    }
    val order = Array<Any>(pieces.size) { pieces[it] }
    Bidi.reorderVisually(levels.toByteArray(), 0, order, 0, order.size)
    return order.map { it as Piece }
}

private fun whole(t: String, base: Int): List<Piece> = visual(t, listOf(CoverRun(0, t.length, true)), base)

private class Paints(val paint: Paint, val regular: Paint) {
    val flat = flatOf(paint)
    val flatRegular = if (regular === paint) flat else flatOf(regular)
    val han by lazy { hanPaint(paint) }
    val hanRegular by lazy { if (regular === paint) han else hanPaint(regular) }
    val flatHan by lazy { flatOf(han) }
    val flatHanRegular by lazy { if (regular === paint) flatHan else flatOf(hanRegular) }

    fun paintFor(r: Piece): Paint = if (r.han) (if (r.covered) han else hanRegular) else if (r.covered) paint else regular

    fun flatFor(r: Piece): Paint = if (r.han) (if (r.covered) flatHan else flatHanRegular) else if (r.covered) flat else flatRegular
}

private fun widthOf(pieces: List<Piece>, t: String, ps: Paints, track: Float): Float = pieces.fold(0f) { w, r ->
    val full = width64Of(ps.paintFor(r), ps.flatFor(r), track, t, r.start, r.end, r.rtl)
    if (!r.han) return@fold w + full
    val adv = advance(ps.flatFor(r), t, r.start, r.end, r.rtl)
    w.roundToInt() + adv.roundToInt() + full - adv
}

private fun shown(t: String, pieces: List<Piece>, ps: Paints, hanMinPx: Float): List<Piece> = if (hanOn(ps.paint, hanMinPx) && hanIn(t)) hanSplit(t, pieces) else pieces

fun textWidth(role: Role, style: TextStyle, fonts: FontFamily.Resolver, density: Density, covers: Covers?, hanMinPx: Float, base: Int = Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT): (String) -> Float {
    val paint = glyphPaint(style, fonts, density)
    val track = paint.letterSpacing * paint.textSize
    val cover = if (style.fontWeight == FontWeight.Normal) null else covers?.at(style.fontWeight)
    if (cover == null) {
        val ps = Paints(paint, paint)
        return { cased(it, role).let { t -> widthOf(shown(t, whole(t, base), ps, hanMinPx), t, ps, track) } }
    }
    val ps = Paints(paint, glyphPaint(style.copy(fontWeight = FontWeight.Normal), fonts, density))
    return { s ->
        val t = cased(s, role)
        widthOf(shown(t, visual(t, cover.runs(t), base), ps, hanMinPx), t, ps, track)
    }
}

fun TextMeasurer.word(
    text: String,
    role: Role,
    style: TextStyle,
    fonts: FontFamily.Resolver,
    density: Density,
    covers: Covers?,
    hanMinPx: Float,
    base: Int = Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
): Word {
    val t = cased(text, role)
    if (t.isEmpty()) return Word.none
    val lacked = if (style.fontWeight == FontWeight.Normal) null else covers?.at(style.fontWeight)?.runs(t)?.takeIf { r -> r.any { !it.covered } }
    val fixed = base == Bidi.DIRECTION_LEFT_TO_RIGHT || base == Bidi.DIRECTION_RIGHT_TO_LEFT
    val paint = glyphPaint(style, fonts, density)
    val track = paint.letterSpacing * paint.textSize
    val regularStyle = style.copy(fontWeight = FontWeight.Normal)
    val ps = Paints(paint, if (lacked == null) paint else glyphPaint(regularStyle, fonts, density))
    val split = hanOn(paint, hanMinPx) && hanIn(t)
    val pieces = when {
        lacked != null -> shown(t, visual(t, lacked, base), ps, hanMinPx)
        split -> hanSplit(t, whole(t, base))
        else -> listOf(Piece(0, t.length, true, base == Bidi.DIRECTION_RIGHT_TO_LEFT))
    }
    val rise = hanRise(paint)
    val out = ArrayList<WordRun>(pieces.size)
    var pen = 0f
    var ink: RectF? = null
    for ((i, r) in pieces.withIndex()) {
        if (r.han) pen = pen.roundToInt().toFloat()
        val p = ps.paintFor(r)
        val part = t.substring(r.start, r.end)
        val covered = if (r.covered) style else regularStyle
        val face = if (r.han) covered.copy(fontSize = covered.fontSize * Tok.fontHanEm) else covered
        val dy = if (r.han) -rise else 0f
        val own = lacked == null && !split && !fixed
        val st = if (own) face else face.copy(textDirection = if (r.rtl) TextDirection.Rtl else TextDirection.Ltr)
        val layout = measure(part, st, softWrap = false, maxLines = 1, density = density, fontFamilyResolver = fonts)
        val c = leftInked(p, part, if (own) base else if (r.rtl) Bidi.DIRECTION_RIGHT_TO_LEFT else Bidi.DIRECTION_LEFT_TO_RIGHT)
        var dx = 0f
        if (c != null) {
            val shift = shiftOf(p, c)
            val b = if (own) inkOf(p, part) else inkOf(p, part, r.rtl, c)
            val at = RectF(pen + b.left - shift, b.top + dy, pen + b.right - shift, b.bottom + dy)
            ink = ink?.also { it.union(at) } ?: at
            if (shift != 0f && spacedAtEnds(p, c)) dx = -shift
        }
        val fp = ps.flatFor(r)
        out += WordRun(layout, pen, dx - layout.getLineLeft(0), dy)
        if (i < pieces.lastIndex) {
            val adv = advance(fp, part, 0, part.length, r.rtl)
            pen += (if (r.han) adv.roundToInt().toFloat() else adv) + reportedTracks(p, fp, track, part, r.rtl) * track
        }
    }
    val width = widthOf(if (lacked == null) shown(t, whole(t, base), ps, hanMinPx) else pieces, t, ps, track)
    val b = ink ?: return Word(t, out, width, 0f, width, 0f, 0f)
    return Word(t, out, width, b.left, b.right, b.top, b.bottom)
}

fun DrawScope.word(w: Word, x: Float, baseline: Float, color: Color, alpha: Float) {
    for (r in w.runs) drawText(r.layout, color, Offset(x + r.x + r.dx, baseline - r.layout.firstBaseline + r.dy), alpha)
}
