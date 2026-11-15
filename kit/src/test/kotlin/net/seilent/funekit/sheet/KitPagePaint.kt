package net.seilent.funekit.sheet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.px

class KitPagePaint(
    val page: Page,
    private val s: Scale,
    private val w: Int,
    private val h: Int,
    override val chrome: Chrome,
    private val inks: List<RowInk>,
    override val header: Word?,
    override val kind: PageKind,
    override val item: Int,
    private val pivotSide: WordSide?,
    private val headerSide: WordSide?,
    private val bands: List<Band>,
    private val accent: Color,
) : PagePaint {
    private val ys: List<Int> = page.rows.runningFold(px(s.board.page_contentTop, s.ppu)) { y, r -> y + Rows.pitch(s, r) }

    override val grounded: Boolean = true
    override val top: Int = px(s.board.page_contentTop, s.ppu)
    override val bottom: Int = px(s.stdContentBottom, s.stdPpu)
    override val count: Int = page.rows.size

    override fun rowY(i: Int): Int = ys[i]

    override fun wordSide(pivot: Boolean): WordSide? = if (pivot && pivotSide != null) pivotSide else headerSide

    override fun DrawScope.ground(leave: Float, arrive: Float) = pageGround(bands, accent, leave, arrive)

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) = rowAt(i, y, rec, name, 1f, null, null, now)

    private fun DrawScope.rowAt(i: Int, y: Int, rec: DrawRecord?, name: String, press: Float, glow: RowGlow?, focus: FocusTrack?, now: Long) {
        val focused = i == page.focused
        val pressed = i == page.pressed
        val k = if (pressed) press else 1f
        val alpha = focus?.rowAlpha(i, now, Tok.alpha_full, Tok.type_listPrimary_alphaDim) ?: if (focused) Tok.alpha_full else Tok.type_listPrimary_alphaDim
        val g = if (pressed) glow else null
        shifted(rec, 0f, y.toFloat()) {
            listRow(inks[i], s, alpha, focused, k, accent, false, g, focusShown = true)
            rec?.row("$name.row$i", inks[i], s, w, alpha, focused, k, g != null, accent, false, focusShown = true)
        }
    }

    fun DrawScope.whole(rec: DrawRecord?, name: String, press: Float = 1f, glow: RowGlow? = null, focus: FocusTrack? = null, now: Long = 0L) {
        val wf = w.toFloat()
        page.rows.indices.forEach { i ->
            clipped(rec, 0f, 0f, wf, h.toFloat()) {
                shifted(rec, 0f, 0f) { rowAt(i, ys[i], rec, name, press, glow, focus, now) }
            }
        }
    }
}
