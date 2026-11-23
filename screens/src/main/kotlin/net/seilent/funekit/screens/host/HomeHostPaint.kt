package net.seilent.funekit.screens.host

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeInk
import net.seilent.funekit.kit.home.HomeLook
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.home.homeMenu
import net.seilent.funekit.kit.quickplay.QpBoxes
import net.seilent.funekit.kit.quickplay.QpInk
import net.seilent.funekit.kit.quickplay.QpLayout
import net.seilent.funekit.kit.quickplay.QuickplayState
import net.seilent.funekit.kit.quickplay.layered
import net.seilent.funekit.kit.quickplay.qpFront
import net.seilent.funekit.kit.quickplay.qpMenu
import net.seilent.funekit.kit.quickplay.qpOff
import net.seilent.funekit.kit.quickplay.qpPanel
import net.seilent.funekit.kit.quickplay.qpPeekWords
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.touch.ListTouch

class LauncherHomePaint(
    private val geo: HomeGeo,
    private val ink: HomeInk,
    private val menu: HomeMenu,
    private val ringLit: Boolean,
    private val qpLayout: QpLayout,
    private val qpWords: QpInk,
    private val qp: QuickplayState,
    private val qpBox: QpBoxes,
    private val qpTouch: ListTouch,
    override val quickplay: Boolean = true,
    private val homeOff: () -> Float = { 0f },
) : HomePaint {
    override fun peekAlpha(at: Long): Float = if (quickplay) Tok.qpPeekAlpha * menu.peek(at) else 0f

    override fun baseline(item: Int): Float = geo.baseline(item)

    override fun itemWord(item: Int): Word? = ink.names.getOrNull(item)

    override fun frontPane(): Edges = Edges(0, 0, qpLayout.panelPx, geo.s.heightPx)

    override fun DrawScope.front(now: Long) = qpFront(qpLayout, qpWords, qp, qpBox, qpTouch, qp.look(qpLayout, now))

    override fun DrawScope.frontPeek(alpha: Float, now: Long) {
        clipRect(qpLayout.panelPx.toFloat(), 0f, size.width, size.height) {
            layered(alpha / Tok.motion_quickplayAlpha) { qpMenu(ink, menu, geo, 1f, ringLit, now, homeOff()) }
        }
    }

    override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) {
        val off = qpOff(qpTouch, qp, qpLayout)
        qpPanel(qpLayout, qpWords, qp, qpBox, 0f, alpha, false, qp.look(qpLayout, now), off)
        if (rec != null && alpha > 0f) qpPeekWords(rec, qpLayout, qpWords, off, alpha, qp.waiting)
    }

    override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) =
        homeMenu(ink, menu, geo, menu.restAt(now, ringLit), HomeLook(look.items, look.sel, look.selAlpha, look.sub, mark = if (look.watermark) look.items else 0f), look.off, rec)
}
