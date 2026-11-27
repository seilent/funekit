package net.seilent.funekit.kit.saver

private const val IDEO_SPACE = 0x3000
private const val MARK = 0x300
private const val MARK_END = 0x36F
private const val MARK_EXT = 0x1AB0
private const val MARK_EXT_END = 0x1AFF
private const val MARK_SUP = 0x1DC0
private const val MARK_SUP_END = 0x1DFF
private const val MARK_SYM = 0x20D0
private const val MARK_SYM_END = 0x20FF
private const val VS = 0xFE00
private const val VS_END = 0xFE0F
private const val HALF_MARK = 0xFE20
private const val HALF_MARK_END = 0xFE2F
internal const val ZWJ = 0x200D
private const val VOICED = 0x3099
private const val SEMI_VOICED = 0x309A
private const val VS_SUP = 0xE0100
private const val VS_SUP_END = 0xE01EF
private const val INV_EXCL = 0xA1
private const val INV_QUEST = 0xBF
private const val ORD_F = 0xAA
private const val MICRO = 0xB5
private const val ORD_M = 0xBA
private const val SUP1 = 0xB9
private const val SUP2 = 0xB2
private const val SUP3 = 0xB3
private const val QUARTER = 0xBC
private const val HALF_FRAC = 0xBD
private const val THREE_QUARTERS = 0xBE
private const val EXCL = 0x21
private const val SLASH = 0x2F
private const val COLON = 0x3A
private const val AT = 0x40
private const val BRACKET = 0x5B
private const val GRAVE = 0x60
private const val BRACE = 0x7B
private const val TILDE = 0x7E
private const val TIMES = 0xD7
private const val DIVIDE = 0xF7
private const val PUNCT = 0x2000
private const val PUNCT_END = 0x206F
private const val CJK_PUNCT_END = 0x303F
private const val KATAKANA_DOT = 0x30FB
private const val VERT = 0xFE10
private const val VERT_END = 0xFE19
private const val COMPAT = 0xFE30
private const val COMPAT_END = 0xFE6F
private const val FULL_EXCL = 0xFF01
private const val FULL_SLASH = 0xFF0F
private const val FULL_COLON = 0xFF1A
private const val FULL_AT = 0xFF20
private const val FULL_BRACKET = 0xFF3B
private const val FULL_GRAVE = 0xFF40
private const val FULL_BRACE = 0xFF5B
private const val HALF_DOT = 0xFF65
private const val FLAG = 0x1F1E6
private const val FLAG_END = 0x1F1FF
private const val SKIN = 0x1F3FB
private const val SKIN_END = 0x1F3FF
private const val CJK_JAMO = 0x1100
private const val CJK_JAMO_END = 0x11FF
private const val CJK_RADICAL = 0x2E80
private const val CJK_UNIFIED_END = 0x9FFF
private const val CJK_HANGUL = 0xAC00
private const val CJK_HANGUL_END = 0xD7AF
private const val CJK_COMPAT = 0xF900
private const val CJK_COMPAT_END = 0xFAFF
private const val CJK_FULL = 0xFF00
private const val CJK_FULL_END = 0xFFEF
private const val CJK_PLANE2 = 0x20000
private const val CJK_PLANE3_END = 0x3FFFF
private const val HAN_A = 0x3400
private const val HAN_A_END = 0x4DBF
private const val HAN = 0x4E00
private const val HAN_B_END = 0x3134F
private const val HAN_SUP = 0x2F800
private const val HAN_SUP_END = 0x2FA1F
private const val ITERATION = 0x3005
private const val ITERATION_END = 0x3007
private const val HANGZHOU = 0x3021
private const val HANGZHOU_END = 0x3029
private const val VERTICAL_ITERATION = 0x303B
private const val KANA_HALF = 0xFF61
private const val KANA_HALF_END = 0xFF9F
private const val REPLACEMENT = 0xFFFD
private const val B1 = 0x80
private const val B2 = 0xE0
private const val B2_TAG = 0xC0
private const val B3 = 0xF0
private const val B4 = 0xF8
private const val CONT = 0x3F
private const val LEAD2 = 0x1F
private const val LEAD3 = 0x0F
private const val LEAD4 = 0x07
private const val CONT_BITS = 6
private const val BYTE = 0xFF
private const val TRAIL4 = 3
private const val TRAIL3 = 2

internal fun blankCp(c: Int): Boolean = c == ' '.code || c == '\t'.code || c == IDEO_SPACE

internal fun markCp(c: Int): Boolean =
    c in MARK..MARK_END || c in MARK_EXT..MARK_EXT_END || c in MARK_SUP..MARK_SUP_END || c in MARK_SYM..MARK_SYM_END ||
        c in VS..VS_END || c in HALF_MARK..HALF_MARK_END || c == ZWJ || c == VOICED || c == SEMI_VOICED ||
        c in VS_SUP..VS_SUP_END

internal fun letterCp(c: Int): Boolean {
    val latin1 = c in INV_EXCL..INV_QUEST && c != ORD_F && c != MICRO && c != ORD_M && c != SUP1 && c != SUP2 && c != SUP3 &&
        c != QUARTER && c != HALF_FRAC && c != THREE_QUARTERS
    return c != 0 && !blankCp(c) && !markCp(c) && c !in EXCL..SLASH && c !in COLON..AT && c !in BRACKET..GRAVE &&
        c !in BRACE..TILDE && !latin1 && c != TIMES && c != DIVIDE && c !in PUNCT..PUNCT_END && c !in IDEO_SPACE..CJK_PUNCT_END &&
        c != KATAKANA_DOT && c !in VERT..VERT_END && c !in COMPAT..COMPAT_END && c !in FULL_EXCL..FULL_SLASH &&
        c !in FULL_COLON..FULL_AT && c !in FULL_BRACKET..FULL_GRAVE && c !in FULL_BRACE..HALF_DOT
}

internal fun cjkCp(c: Int): Boolean =
    c in CJK_JAMO..CJK_JAMO_END || c in CJK_RADICAL..CJK_UNIFIED_END || c in CJK_HANGUL..CJK_HANGUL_END ||
        c in CJK_COMPAT..CJK_COMPAT_END || c in CJK_FULL..CJK_FULL_END || c in CJK_PLANE2..CJK_PLANE3_END

internal fun hanCp(c: Int): Boolean =
    c in HAN_A..HAN_A_END || c in HAN..CJK_UNIFIED_END || c in CJK_COMPAT..CJK_COMPAT_END || c in CJK_PLANE2..HAN_B_END ||
        c in HAN_SUP..HAN_SUP_END || c in ITERATION..ITERATION_END || c in HANGZHOU..HANGZHOU_END || c == VERTICAL_ITERATION

internal fun halfKanaCp(c: Int): Boolean = c in KANA_HALF..KANA_HALF_END

internal fun hangulCp(c: Int): Boolean = c in CJK_JAMO..CJK_JAMO_END || c in CJK_HANGUL..CJK_HANGUL_END

internal fun utf8(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)

internal class Utf8Next(var cp: Int, var at: Int)

internal fun utf8Next(b: ByteArray, at: Int, out: Utf8Next) {
    var p = at
    var c = b[p++].toInt() and BYTE
    var n = 0
    if (c >= B4 || (c >= B1 && c < B2_TAG)) {
        out.cp = REPLACEMENT
        out.at = p
        return
    }
    if (c >= B3) {
        c = c and LEAD4
        n = TRAIL4
    } else if (c >= B2) {
        c = c and LEAD3
        n = TRAIL3
    } else if (c >= B2_TAG) {
        c = c and LEAD2
        n = 1
    }
    while (n > 0) {
        if (p >= b.size || (b[p].toInt() and B2_TAG) != B1) {
            c = REPLACEMENT
            break
        }
        c = (c shl CONT_BITS) or (b[p++].toInt() and CONT)
        n--
    }
    out.cp = c
    out.at = p
}

internal fun cpAt(b: ByteArray, at: Int): Int {
    if (at >= b.size) return 0
    val o = Utf8Next(0, 0)
    utf8Next(b, at, o)
    return o.cp
}

internal fun clusterEnd(b: ByteArray, at: Int): Int {
    if (at >= b.size) return b.size
    val o = Utf8Next(0, 0)
    utf8Next(b, at, o)
    var p = o.at
    if (o.cp in FLAG..FLAG_END && p < b.size) {
        utf8Next(b, p, o)
        if (o.cp in FLAG..FLAG_END) p = o.at
    }
    var zwj = false
    while (p < b.size) {
        utf8Next(b, p, o)
        if (!zwj && !markCp(o.cp) && o.cp !in SKIN..SKIN_END) break
        zwj = o.cp == ZWJ
        p = o.at
    }
    return p
}

internal fun bytesOf(b: ByteArray, b0: Int, b1: Int): String = String(b, b0, b1 - b0, Charsets.UTF_8)

internal fun firstChar(s: String): String {
    val b = utf8(s)
    return bytesOf(b, 0, clusterEnd(b, 0))
}
