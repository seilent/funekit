package net.seilent.funekit.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb as drawThumb

open class RowsPaint<R : Any, L>(
    private val d: RowsDraw<R, L>,
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
        shifted(rec, 0f, y.toFloat()) { with(d) { row(r, look(p, i, now)) } }
    }

    override fun thumbFade(at: Long): Float = m.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = d.thumbAt(p, scroll) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, bands, accent)
    }
}
