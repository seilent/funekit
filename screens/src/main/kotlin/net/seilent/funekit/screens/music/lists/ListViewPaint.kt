package net.seilent.funekit.screens.music.lists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb as drawThumb
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.morphSide
import net.seilent.funekit.screens.music.queue.QUEUE_EMPTY

fun ListViewGeo(s: Scale): ListGeo = ListGeo(s, s.board.queueRowsTop, Rows.pitch(s, false))

class ListViewDraw(
    val m: ListViewModel,
    geo: ListGeo,
    inkOf: (RowModel) -> RowInk,
    words: ListLines,
    accent: Color,
    accentOff: Boolean,
    val chrome: Chrome,
    val headerSide: WordSide,
) : ListPaneDraw(geo, inkOf, words, accent, accentOff) {
    fun DrawScope.row(look: ListLook, k: RowInk?, rec: DrawRecord?, name: String, focusShown: Boolean) = paneRow(m.pane.glow, look, k, rec, name, focusShown)

    fun DrawScope.lines(count: String?, empty: Boolean, rec: DrawRecord?, name: String) {
        listLines(geo, words, count, 1f, if (empty) QUEUE_EMPTY else null, rec, name)
    }
}

fun viewHeader(): Role = Role(Roles.pageHeader.size, Roles.listCollection.weight, Roles.listCollection.case, Roles.pageHeader.alpha, Roles.pageHeader.tracking)

fun viewChrome(m: TextMeasurer, fonts: FontFamily.Resolver, s: Scale, density: Density, covers: Covers?, title: String): Chrome {
    val role = viewHeader()
    return Chrome(s, m.word(title, role, roleStyle(role, s, density, Ink.header, ppu = s.stdPpu), fonts, density, covers), emptyList())
}

fun listViewDraw(m: ListViewModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, accent: Color, accentOff: Boolean): ListViewDraw {
    val chrome = viewChrome(measurer, fonts, s, density, covers, m.title)
    val header = morphSide(measurer, fonts, s, density, covers, chrome.header.text, viewHeader(), true, chrome.headerAlpha, chrome.headerRestX(), px(Tok.page_headerBaseline, s.stdPpu).toFloat(), -1)
    return ListViewDraw(m, ListViewGeo(s), { r -> rowInk(measurer, fonts, r, s, density, accentOff, covers) }, listWords(s, measurer, fonts, density, covers), accent, accentOff, chrome, header)
}

class ListViewPaint(
    private val d: ListViewDraw,
    override val chrome: Chrome,
    private val bands: List<Band>,
    private val accent: Color,
    private val hideFocus: Boolean = false,
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    override val header: Word = chrome.header
    override val item: Int = -1
    override val grounded: Boolean = true
    override val kind: PageKind = PageKind.Plain
    override val top: Int = geo.top
    override val bottom: Int = geo.bottom
    override val count: Int get() = m.total
    override val scroll: Int get() = m.track.q

    override fun rowY(i: Int): Int = geo.top + i * geo.pitch

    override fun DrawScope.ground(leave: Float, arrive: Float) = pageGround(bands, accent, leave, arrive)

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val model = m.rowModel(i) ?: return
        val look = m.look(i, now)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(look, ink(model), rec, "$name.row$i", !hideFocus) } }
    }

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
        with(d) { lines(m.count(), m.empty(), rec, name) }
        if (m.total == 0) return
        val off = m.track.q
        val first = maxOf(0, off / geo.pitch)
        val last = minOf(m.total - 1, (off + geo.view - 1) / geo.pitch)
        for (r in first..last) {
            clipped(rec, 0f, geo.top.toFloat(), size.width, geo.bottom.toFloat()) { row(r, geo.top + r * geo.pitch - off, now, rec, name) }
        }
    }

    override fun wordSide(pivot: Boolean): WordSide? = if (pivot) null else d.headerSide

    override fun thumbFade(at: Long): Float = m.pane.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = geo.thumb(count, scroll) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, bands, accent)
    }
}
