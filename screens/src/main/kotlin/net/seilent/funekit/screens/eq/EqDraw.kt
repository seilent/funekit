package net.seilent.funekit.screens.eq

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Scale
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.settingMeasure
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.px
import net.seilent.funekit.screens.settings.RowsDraw

class EqDraw(
    override val m: EqModel,
    override val s: Scale,
    val measure: EqMeasure,
    private val paneOf: () -> EqPane,
    override val accent: Color,
) : RowsDraw<EqLine, EqLook> {
    override val top: Int = px(s.board.page_contentTop, s.ppu)
    override val bottom: Int = px(s.stdContentBottom, s.stdPpu)
    override val view: Int = bottom - top
    private val inks = HashMap<EqLine, EqInk>()
    private var inked: EqPane? = null

    fun ink(r: EqLine): EqInk {
        val pane = paneOf()
        if (pane !== inked) {
            inks.clear()
            inked = pane
        }
        return inks.getOrPut(r) { pane.ink(r, measure) }
    }

    override fun keep() {
        inks.keys.retainAll(m.groups.flatMap { it.rows }.toSet())
    }

    override fun thumbAt(p: Int, off: Int): ThumbRect? = thumbOf(s, top, bottom, view.toLong(), m.content(p).toLong(), off.toLong())

    override fun look(p: Int, i: Int, now: Long): EqLook {
        val look = m.look(p, i, now)
        val line = m.rows(p).getOrNull(i) ?: return EqLook(look, RowEnter.Rest)
        return EqLook(look, paneOf().enter(m.groups[p].name, line, now))
    }

    override fun DrawScope.row(r: EqLine, look: EqLook, rec: DrawRecord?, name: String) {
        val k = ink(r)
        with(paneOf()) { line(r, k, look, look.look.glow?.let { RowGlow(m.glow, it) }, rec, name) }
    }

    override fun DrawScope.content(p: Int, off: Int, rec: DrawRecord?, name: String, look: (Int) -> EqLook) {
        val rows = m.rows(p)
        val tops = m.tops(p)
        clipped(rec, 0f, top.toFloat(), size.width, bottom.toFloat()) {
            for (i in rows.indices) {
                val h = ink(rows[i]).height
                val y = top + tops[i] - off
                if (y + h <= top || y >= bottom) continue
                shifted(rec, 0f, y.toFloat()) { row(rows[i], look(i), rec, "$name.row$i") }
            }
        }
    }
}

fun eqDraw(
    m: EqModel,
    s: Scale,
    measurer: TextMeasurer,
    fonts: FontFamily.Resolver,
    density: Density,
    covers: Covers?,
    accent: Color,
    accentOff: Boolean,
    pane: () -> EqPane,
): EqDraw {
    val rows = settingMeasure(s, fonts, density, covers, accentOff)
    val d = EqDraw(m, s, EqMeasure(s, measurer, fonts, density, covers, rows, accent, accentOff), pane, accent)
    m.heightOf = { d.ink(it).height }
    m.view = d.view
    m.scale(s.ppu)
    return d
}
