package net.seilent.funekit.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Fonts
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.min

private const val HALF = 2
private const val LINES = 2

class SettingMeasure(
    val s: Scale,
    private val titleWidth: (String) -> Float,
    private val light: (String) -> Float,
    private val bold: (String) -> Float,
) {
    val left64: Int = pen64(Tok.hubEdge, s.ppu)
    val right64: Int = pen64(s.w - Tok.hubEdge, s.ppu)
    val gutter: Int = px(Tok.gutter, s.ppu)
    val titlePx: Int = rolePx(Roles.pageRowTitle, s)
    val valuePx: Int = rolePx(Roles.settingsValue, s)
    val subPx: Int = rolePx(Roles.settingsSub, s)
    val pitch: Int = px(Tok.settingsPitch, s.ppu)

    fun title64(t: String): Int = pen64(titleWidth(t))

    fun value64(v: String, editable: Boolean): Int = pen64(if (editable) bold(v) else light(v))
}

fun settingMeasure(s: Scale, fonts: FontFamily.Resolver, density: Density, covers: Covers?, accentOff: Boolean): SettingMeasure {
    val ts = roleStyle(Roles.pageRowTitle, s, density, Ink.white)
    val vs = roleStyle(Roles.settingsValue, s, density, Ink.white)
    val bs = vs.copy(fontWeight = Fonts.weight(Accent.bold(Roles.settingsValue.weight, accentOff)))
    return SettingMeasure(
        s,
        textWidth(Roles.pageRowTitle, ts, fonts, density, covers),
        textWidth(Roles.settingsValue, vs, fonts, density, covers),
        textWidth(Roles.settingsValue, bs, fonts, density, covers),
    )
}

private fun splits(text: String): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    var sp = text.indexOf(' ')
    while (sp >= 0) {
        val l0 = text.substring(0, sp)
        val l1 = text.substring(sp + 1)
        if (l0.isNotEmpty() && l1.isNotEmpty() && !l0.endsWith(' ') && !l1.startsWith(' ')) out += l0 to l1
        sp = text.indexOf(' ', sp + 1)
    }
    return out
}

fun SettingMeasure.valueLines(title: String, value: String, editable: Boolean): List<String> {
    val end64 = left64 + title64(title)
    fun fits(w64: Int) = end64 + pen64(gutter.toFloat()) + w64 <= right64
    if (fits(value64(value, editable))) return listOf(value)
    var best = listOf(value)
    var narrow = 0
    for ((l0, l1) in splits(value)) {
        val w = max(value64(l0, editable), value64(l1, editable))
        if (fits(w) && (best.size == 1 || w < narrow)) {
            narrow = w
            best = listOf(l0, l1)
        }
    }
    return best
}

fun SettingMeasure.wideLine(lines: List<String>): String =
    if (lines.size > 1 && title64(lines[1]) > title64(lines[0])) lines[1] else lines[0]

fun SettingMeasure.titleLines(title: String, values: List<String>, editable: Boolean): List<String> {
    val room64 = right64 - left64 - pen64(gutter.toFloat())
    fun fits(t64: Int, v: String) = t64 + value64(v, editable) <= room64
    val whole = title64(title)
    val need = values.any { !fits(whole, it) && valueLines(title, it, editable).size == 1 }
    if (!need) return listOf(title)
    var best = listOf(title)
    var narrow = 0
    for ((l0, l1) in splits(title)) {
        val w = max(title64(l0), title64(l1))
        if (best.size == 1 || w < narrow) {
            narrow = w
            best = listOf(l0, l1)
        }
    }
    if (best.size == 1) return best
    val wide = wideLine(best)
    for (v in values) if (!fits(narrow, v) && valueLines(wide, v, editable).size == 1) return listOf(title)
    return best
}

class SettingSize(val pad: Int, val band: Int, val subY: Int, val height: Float, val px: Int)

fun SettingMeasure.size(lines: List<String>, values: List<String>, sub: Boolean, editable: Boolean, tall: Boolean = false): SettingSize {
    if (!sub) return SettingSize(0, pitch, 0, Tok.settingsPitch, pitch)
    val title = Metrics.ascent(titlePx) + Metrics.descent(titlePx)
    val value = Metrics.ascent(valuePx) + Metrics.descent(valuePx)
    val twoTitle = title + px(Tok.settingsTitlePitch, s.ppu)
    val twoValue = LINES * value
    var drawn = if (lines.size > 1) twoTitle else title
    val wide = wideLine(lines)
    for (v in values) if (valueLines(wide, v, editable).size > 1) drawn = max(drawn, twoValue)
    val gap = px(1f, s.ppu)
    val subH = Metrics.ascent(subPx) + Metrics.descent(subPx)
    val padPx = fxMul(fx(Tok.settingsSubPad), titlePx.toLong()).toInt()
    val block = if (tall) max(twoTitle, twoValue) else drawn
    val h = block + gap + subH + LINES * padPx
    val pad = (h - drawn - gap - subH) / HALF
    return SettingSize(pad, drawn, pad + drawn + gap, h / s.ppu, h)
}

class SettingInk(
    val s: Scale,
    val lines: List<String>,
    val size: SettingSize,
    val title: List<Word>,
    val titleX64: Int,
    val titleBase: List<Int>,
    val value: List<Word>,
    val valueX64: List<Int>,
    val valueBase: List<Int>,
    val clipX: Int,
    val right: Int,
    val sub: Word?,
    val subBase: Int,
    val glowText: List<GlowText>,
    val glowClip: Edges,
)

fun settingInk(
    m: TextMeasurer,
    fonts: FontFamily.Resolver,
    density: Density,
    covers: Covers?,
    meas: SettingMeasure,
    title: String,
    value: String,
    words: List<String>,
    sub: String?,
    editable: Boolean,
    tall: Boolean = false,
): SettingInk {
    val s = meas.s
    val ts = roleStyle(Roles.pageRowTitle, s, density, Ink.white)
    val vs = roleStyle(Roles.settingsValue, s, density, Ink.white)
    val cands = words + value
    val lines = meas.titleLines(title, cands, editable)
    val size = meas.size(lines, cands, sub != null, editable, tall)
    val top = size.pad
    val h = size.band
    val vl = if (value.isEmpty()) emptyList() else meas.valueLines(meas.wideLine(lines), value, editable)
    val vw = vl.map { m.word(it, Roles.settingsValue, vs, fonts, density, covers) }
    val vasc = Metrics.ascent(meas.valuePx)
    val vdesc = Metrics.descent(meas.valuePx)
    val two = vl.size > 1
    val vpitch = if (two) vasc + vdesc else 0
    val asc = if (two) vasc else Metrics.ascent(meas.titlePx)
    val desc = if (two) vdesc else Metrics.descent(meas.titlePx)
    val vbase = top + (h - asc - vpitch - desc) / HALF + asc
    val vx = vw.map { meas.right64 - pen64(it.width) }
    val clipX = if (vx.isEmpty()) penFloor(meas.right64) else penFloor(min(meas.right64, vx.min())) - meas.gutter
    val tasc = Metrics.ascent(meas.titlePx)
    val tdesc = Metrics.descent(meas.titlePx)
    val tpitch = if (lines.size > 1) px(Tok.settingsTitlePitch, s.ppu) else 0
    val tbase = top + (h - tasc - tpitch - tdesc) / HALF + tasc
    val tw = lines.map { m.word(it, Roles.pageRowTitle, ts, fonts, density, covers) }
    val tb = tw.indices.map { tbase + it * tpitch }
    val sw = sub?.let { m.word(it, Roles.settingsSub, roleStyle(Roles.settingsSub, s, density, Ink.white), fonts, density, covers) }
    return SettingInk(
        s,
        lines,
        size,
        tw,
        meas.left64,
        tb,
        vw,
        vx,
        vw.indices.map { vbase + it * vpitch },
        clipX,
        penFloor(meas.right64),
        sw,
        size.subY + Metrics.ascent(meas.subPx),
        tw.indices.map { GlowText(tw[it], meas.left64, tb[it], meas.titlePx) },
        Edges(0, top, clipX, top + h),
    )
}

fun DrawScope.settingValue(ink: SettingInk, alpha: Float, color: Color = Ink.white) {
    for (i in ink.value.indices) word(ink.value[i], penPx(ink.valueX64[i]), ink.valueBase[i].toFloat(), color, alpha)
}

fun DrawScope.settingTitle(ink: SettingInk, alpha: Float, glow: RowGlow? = null) {
    val c = ink.glowClip
    if (glow != null) pressGlow(glow.glow, ink.glowText, c, ink.s.ppu, glow.level)
    clipRect(c.x0.toFloat(), c.y0.toFloat(), c.x1.toFloat(), c.y1.toFloat()) {
        for (i in ink.title.indices) word(ink.title[i], penPx(ink.titleX64), ink.titleBase[i].toFloat(), Ink.white, alpha)
    }
}

fun DrawScope.settingSub(ink: SettingInk, alpha: Float) {
    val w = ink.sub ?: return
    clipRect(0f, 0f, ink.right.toFloat(), px(ink.size.height, ink.s.ppu).toFloat()) {
        word(w, penPx(ink.titleX64), ink.subBase.toFloat(), Ink.white, alpha)
    }
}
