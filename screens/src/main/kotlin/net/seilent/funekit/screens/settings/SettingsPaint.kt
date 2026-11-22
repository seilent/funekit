package net.seilent.funekit.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.SettingInk
import net.seilent.funekit.kit.SettingMeasure
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.settingInk
import net.seilent.funekit.kit.settingMeasure
import net.seilent.funekit.kit.settingSub
import net.seilent.funekit.kit.settingSwatch
import net.seilent.funekit.kit.settingTitle
import net.seilent.funekit.kit.settingValue
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.px

class SettingsDraw(
    override val m: SettingsModel,
    override val s: Scale,
    val measure: SettingMeasure,
    private val inkOf: (SettingRow) -> SettingInk,
    override val accent: Color,
) : RowsDraw<SettingRow, SettingLook> {
    override val top: Int = px(s.board.page_contentTop, s.ppu)
    override val bottom: Int = px(s.stdContentBottom, s.stdPpu)
    override val view: Int = bottom - top
    private val inks = HashMap<SettingRow, SettingInk>()

    fun ink(r: SettingRow): SettingInk = inks.getOrPut(r) { inkOf(r) }

    override fun keep() {
        inks.keys.retainAll(m.groups.flatMap { it.rows }.toSet())
    }

    override fun thumbAt(p: Int, off: Int): ThumbRect? = thumbOf(s, top, bottom, view.toLong(), m.content(p).toLong(), off.toLong())

    override fun look(p: Int, i: Int, now: Long): SettingLook = m.look(p, i, now)

    override fun DrawScope.row(r: SettingRow, look: SettingLook, rec: DrawRecord?, name: String) = row(ink(r), look, r.swatch)

    fun DrawScope.row(ink: SettingInk, look: SettingLook, swatch: Color?) {
        settingValue(ink, look.value, if (look.edit) accent else Ink.white)
        if (swatch != null) settingSwatch(ink, swatch, look.swatch)
        settingSub(ink, look.sub)
        settingTitle(ink, look.title, look.glow?.let { RowGlow(m.glow, it) })
    }

    override fun DrawScope.content(p: Int, off: Int, rec: DrawRecord?, name: String, look: (Int) -> SettingLook) {
        val rows = m.rows(p)
        val tops = m.tops(p)
        clipRect(0f, top.toFloat(), size.width, bottom.toFloat()) {
            for (i in rows.indices) {
                val k = ink(rows[i])
                val y = top + tops[i] - off
                if (y + k.size.px <= top || y >= bottom) continue
                translate(0f, y.toFloat()) { row(k, look(i), rows[i].swatch) }
            }
        }
    }
}

fun settingsDraw(
    m: SettingsModel,
    s: Scale,
    measurer: TextMeasurer,
    fonts: FontFamily.Resolver,
    density: Density,
    covers: Covers?,
    accent: Color,
    accentOff: Boolean,
): SettingsDraw {
    val meas = settingMeasure(s, fonts, density, covers, accentOff)
    val d = SettingsDraw(m, s, meas, { r -> settingInk(measurer, fonts, density, covers, meas, r.title, r.live ?: r.value, r.words + r.shows + listOfNotNull(r.live?.let { r.value }), r.sub, r.editable, swatch = r.swatch != null) }, accent)
    m.heightOf = { d.ink(it).size.px }
    m.view = d.view
    m.scale(s.ppu)
    return d
}

fun settingsChrome(c: Chrome, look: HeaderLook): Chrome = c.also {
    when (look) {
        HeaderLook.Settings -> {
            it.headerPen = Tok.page_settingsHeaderX
            it.headerAlpha = Tok.page_settingsHeaderAlpha
        }
        HeaderLook.Page -> {
            it.headerPen = Tok.page_headerX
            it.headerAlpha = Tok.page_settingsHeaderAlpha
        }
        HeaderLook.Default -> {
            it.headerPen = Tok.page_headerX
            it.headerAlpha = Roles.pageHeader.alpha
        }
    }
}

class SettingsPaint(
    d: SettingsDraw,
    chrome: Chrome,
    item: Int,
    bands: List<Band>,
    accent: Color,
) : RowsPaint<SettingRow, SettingLook>(d, chrome, item, bands, accent)
