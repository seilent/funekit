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
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.settingInk
import net.seilent.funekit.kit.settingMeasure
import net.seilent.funekit.kit.settingSub
import net.seilent.funekit.kit.settingTitle
import net.seilent.funekit.kit.settingValue
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.px
import net.seilent.funekit.kit.thumb as drawThumb

class SettingsDraw(
    val m: SettingsModel,
    val s: Scale,
    val measure: SettingMeasure,
    private val inkOf: (SettingRow) -> SettingInk,
    val accent: Color,
) {
    val top: Int = px(s.board.page_contentTop, s.ppu)
    val bottom: Int = px(s.stdContentBottom, s.stdPpu)
    val view: Int = bottom - top
    private val inks = HashMap<SettingRow, SettingInk>()

    fun ink(r: SettingRow): SettingInk = inks.getOrPut(r) { inkOf(r) }

    fun keep() {
        inks.keys.retainAll(m.groups.flatMap { it.rows }.toSet())
    }

    fun thumbAt(p: Int, off: Int): ThumbRect? = thumbOf(s, top, bottom, view.toLong(), m.content(p).toLong(), off.toLong())

    fun DrawScope.row(ink: SettingInk, look: SettingLook) {
        settingValue(ink, look.value, if (look.edit) accent else Ink.white)
        settingSub(ink, look.sub)
        settingTitle(ink, look.title, look.glow?.let { RowGlow(m.glow, it) })
    }

    fun DrawScope.content(p: Int, off: Int, look: (Int) -> SettingLook) {
        val rows = m.rows(p)
        val tops = m.tops(p)
        clipRect(0f, top.toFloat(), size.width, bottom.toFloat()) {
            for (i in rows.indices) {
                val k = ink(rows[i])
                val y = top + tops[i] - off
                if (y + k.size.px <= top || y >= bottom) continue
                translate(0f, y.toFloat()) { row(k, look(i)) }
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
    val d = SettingsDraw(m, s, meas, { r -> settingInk(measurer, fonts, density, covers, meas, r.title, r.live ?: r.value, r.words + r.shows + listOfNotNull(r.live?.let { r.value }), r.sub, r.editable) }, accent)
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
    private val d: SettingsDraw,
    override val chrome: Chrome,
    override val item: Int,
    private val bands: List<Band>,
    private val accent: Color,
) : PagePaint {
    private val m = d.m
    private val p = m.pivot
    private val tops = m.tops(p)
    override val header: Word = chrome.header
    override val grounded: Boolean = true
    override val kind: PageKind = PageKind.Hub
    override val top: Int = d.top
    override val bottom: Int = d.bottom
    override val count: Int = tops.size
    override val scroll: Int get() = m.pane(p).px

    override fun rowY(i: Int): Int = d.top + if (i < tops.size) tops[i] else m.content(p)

    override fun DrawScope.ground(leave: Float, arrive: Float) = pageGround(bands, accent, leave, arrive)

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val r = m.rows(p).getOrNull(i) ?: return
        shifted(rec, 0f, y.toFloat()) { with(d) { row(ink(r), m.look(p, i, now)) } }
    }

    override fun thumbFade(at: Long): Float = m.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = d.thumbAt(p, scroll) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, bands, accent)
    }
}
