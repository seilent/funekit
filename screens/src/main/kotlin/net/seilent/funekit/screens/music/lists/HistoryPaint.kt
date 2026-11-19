package net.seilent.funekit.screens.music.lists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb as drawThumb

fun HistoryGeo(s: Scale): ListGeo = ListGeo(s, s.board.page_contentTop, Rows.pitch(s, false))

class HistoryDraw(
    val m: HistoryModel,
    geo: ListGeo,
    inkOf: (RowModel) -> RowInk,
    words: ListLines,
    accent: Color,
    accentOff: Boolean,
) : ListPaneDraw(geo, inkOf, words, accent, accentOff) {
    fun DrawScope.row(p: Int, look: ListLook, k: RowInk?, rec: DrawRecord?, name: String, focusShown: Boolean) = paneRow(m.panes[p].glow, look, k, rec, name, focusShown)

    fun DrawScope.lines(empty: String?, rec: DrawRecord?, name: String) {
        listLines(geo, words, null, 1f, empty, rec, name)
    }

    fun DrawScope.content(p: Int, now: Long, focusShown: Boolean, rec: DrawRecord?, name: String) {
        lines(m.empty(p), rec, name)
        val n = m.count(p)
        if (n == 0) return
        val off = m.panes[p].track.q
        val first = maxOf(0, off / geo.pitch)
        val last = minOf(n - 1, (off + geo.view - 1) / geo.pitch)
        for (r in first..last) {
            val model = m.rowModel(p, r) ?: continue
            val look = m.look(p, r, now)
            clipped(rec, 0f, geo.top.toFloat(), size.width, geo.bottom.toFloat()) {
                shifted(rec, 0f, (geo.top + r * geo.pitch - off).toFloat()) { row(p, look, ink(model), rec, "$name.row$r", focusShown) }
            }
        }
    }
}

fun historyDraw(m: HistoryModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, accent: Color, accentOff: Boolean): HistoryDraw =
    HistoryDraw(m, HistoryGeo(s), { r -> rowInk(measurer, fonts, r, s, density, accentOff, covers) }, listWords(s, measurer, fonts, density, covers), accent, accentOff)

class HistoryPaint(
    private val d: HistoryDraw,
    override val chrome: Chrome,
    private val bands: List<Band>,
    private val accent: Color,
    private val hideFocus: Boolean = false,
    override val item: Int = -1
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    private val p = m.pivot
    override val header: Word = chrome.header
    override val grounded: Boolean = true
    override val kind: PageKind = PageKind.Plain
    override val top: Int = geo.top
    override val bottom: Int = geo.bottom
    override val count: Int get() = m.count(p)
    override val scroll: Int get() = m.panes[p].track.q

    override fun rowY(i: Int): Int = geo.top + i * geo.pitch

    override fun DrawScope.ground(leave: Float, arrive: Float) = pageGround(bands, accent, leave, arrive)

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val model = m.rowModel(p, i) ?: return
        val look = m.look(p, i, now)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(p, look, ink(model), rec, "$name.row$i", !hideFocus) } }
    }

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
        with(d) { content(p, now, !hideFocus, rec, name) }
    }

    override fun thumbFade(at: Long): Float = m.panes[p].fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = geo.thumb(count, scroll) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, bands, accent)
    }
}
