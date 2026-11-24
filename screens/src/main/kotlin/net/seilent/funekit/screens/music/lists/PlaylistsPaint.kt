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
import net.seilent.funekit.kit.RowWord
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.listRowWord
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb as drawThumb
import net.seilent.funekit.screens.music.rowWordSide

fun PlaylistsGeo(s: Scale): ListGeo = ListGeo(s, s.board.queueRowsTop, Rows.pitch(s, true))

class PlaylistsDraw(
    val m: PlaylistsModel,
    geo: ListGeo,
    inkOf: (RowModel) -> RowInk,
    words: ListLines,
    accent: Color,
    accentOff: Boolean,
    val rowSide: ((RowWord, RowModel) -> WordSide)? = null,
) : ListPaneDraw(geo, inkOf, words, accent, accentOff) {
    var hidden = -1

    fun DrawScope.row(look: ListLook, k: RowInk?, rec: DrawRecord?, name: String, focusShown: Boolean) = paneRow(m.pane.glow, look, k, rec, name, focusShown)

    fun DrawScope.lines(count: String?, status: String?, rec: DrawRecord?, name: String) {
        listLines(geo, words, count, 1f, status, rec, name)
    }
}

fun playlistsDraw(m: PlaylistsModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, accent: Color, accentOff: Boolean): PlaylistsDraw =
    PlaylistsDraw(m, PlaylistsGeo(s), { r -> rowInk(measurer, fonts, r, s, density, accentOff, covers) }, listWords(s, measurer, fonts, density, covers), accent, accentOff, rowWordSide(measurer, fonts, s, density, covers))

class PlaylistsPaint(
    private val d: PlaylistsDraw,
    override val chrome: Chrome,
    private val bands: List<Band>,
    private val accent: Color,
    private val hideFocus: Boolean = false,
    override val item: Int = -1
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    override val header: Word = chrome.header
    override val grounded: Boolean = true
    override val kind: PageKind = PageKind.Plain
    override val top: Int = geo.top
    override val bottom: Int = geo.bottom
    override val count: Int get() = if (m.ready) m.size else 0
    override val scroll: Int get() = m.track.q

    override fun rowY(i: Int): Int = geo.top + i * geo.pitch

    override fun DrawScope.ground(leave: Float, arrive: Float) = pageGround(bands, accent, leave, arrive)

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val model = m.rowModel(i)?.let { if (i == d.hidden) it.copy(primary = "") else it } ?: return
        val look = m.look(i, now, !hideFocus)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(look, ink(model), rec, "$name.row$i", !hideFocus) } }
    }

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
        if (!m.ready) return
        with(d) { lines(if (m.status() == null) m.count() else null, m.status(), rec, name) }
        val off = m.track.q
        val first = maxOf(0, off / geo.pitch)
        val last = minOf(m.size - 1, (off + geo.view - 1) / geo.pitch)
        for (r in first..last) {
            clipped(rec, 0f, geo.top.toFloat(), size.width, geo.bottom.toFloat()) { row(r, geo.top + r * geo.pitch - off, now, rec, name) }
        }
    }

    override fun rowSide(row: Int): WordSide? {
        val model = m.rowModel(row) ?: return null
        val w = listRowWord(m.track, geo.top, geo.bottom, row, false, model, d.ink(model), geo.s) ?: return null
        return d.rowSide?.invoke(w, model)
    }

    override fun hideRow(row: Int) {
        d.hidden = row
    }

    val hidden: Int get() = d.hidden

    override fun thumbFade(at: Long): Float = m.pane.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = geo.thumb(count, scroll) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, bands, accent)
    }
}
