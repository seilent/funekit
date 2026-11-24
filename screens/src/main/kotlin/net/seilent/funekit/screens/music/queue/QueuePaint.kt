package net.seilent.funekit.screens.music.queue

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
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb as drawThumb
import net.seilent.funekit.screens.music.lists.ListGeo
import net.seilent.funekit.screens.music.lists.ListLines
import net.seilent.funekit.screens.music.lists.ListLook
import net.seilent.funekit.screens.music.lists.listLines
import net.seilent.funekit.screens.music.lists.listWords

fun QueueGeo(s: Scale): ListGeo = ListGeo(s, s.board.queueRowsTop, Rows.pitch(s, false))

class QueueDraw(
    val m: QueueModel,
    val geo: ListGeo,
    private val inkOf: (RowModel) -> RowInk,
    val words: ListLines,
    val accent: Color,
    val accentOff: Boolean,
) {
    val s: Scale get() = geo.s
    private val inks = HashMap<RowModel, RowInk>()

    fun ink(r: RowModel): RowInk = inks.getOrPut(r) { inkOf(r) }

    fun keep(models: Set<RowModel>) {
        inks.keys.retainAll(models)
    }

    fun DrawScope.row(look: ListLook, k: RowInk?, rec: DrawRecord?, name: String, focusShown: Boolean) {
        if (k == null) return
        val glow = look.glow?.let { RowGlow(if (look.back) m.glow.back else m.glow.glow, it) }
        listRow(k, s, look.alpha, look.focused, 1f, accent, accentOff, glow, null, focusShown)
        rec?.row(name, k, s, size.width.toInt(), look.alpha, look.focused, 1f, glow != null, accent, accentOff, focusShown)
    }

    fun DrawScope.lines(count: CountLine?, line: String?, rec: DrawRecord?, name: String) {
        listLines(geo, words, count?.text, count?.alpha ?: 1f, line, rec, name)
    }
}

fun queueDraw(m: QueueModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, accent: Color, accentOff: Boolean): QueueDraw =
    QueueDraw(m, QueueGeo(s), { r -> rowInk(measurer, fonts, r, s, density, accentOff, covers) }, listWords(s, measurer, fonts, density, covers), accent, accentOff)

class QueuePaint(
    private val d: QueueDraw,
    override val chrome: Chrome,
    private val bands: List<Band>,
    private val accent: Color,
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    override val queue: Boolean = true
    override val header: Word = chrome.header
    override val item: Int = -1
    override val grounded: Boolean = true
    override val kind: PageKind = PageKind.Plain
    override val top: Int = geo.top
    override val bottom: Int = geo.bottom
    override val count: Int get() = m.rowsShown
    override val scroll: Int get() = m.track.q

    override fun rowY(i: Int): Int = geo.top + i * geo.pitch

    override fun DrawScope.ground(leave: Float, arrive: Float) = pageGround(bands, accent, leave, arrive)

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val model = m.rowModel(i) ?: return
        val look = m.look(i, now, true)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(look, ink(model), rec, "$name.row$i", true) } }
    }

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
        with(d) { lines(m.countLine(now), m.line(), rec, name) }
        val off = m.track.q
        if (m.rowsShown == 0) return
        val first = maxOf(0, off / geo.pitch)
        val last = minOf(m.rowsShown - 1, (off + geo.view - 1) / geo.pitch)
        for (r in first..last) {
            clipped(rec, 0f, geo.top.toFloat(), size.width, geo.bottom.toFloat()) { row(r, geo.top + r * geo.pitch - off, now, rec, name) }
        }
    }

    override fun thumbFade(at: Long): Float = m.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = geo.thumb(m.rowsShown, scroll) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, bands, accent)
    }
}
