package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.scale
import net.seilent.funekit.screens.posture.Fold

enum class NpPart { Status, Back, Top, Art, Mark, Times, Title, Rings }

val NP_PARTS: Set<NpPart> = NpPart.entries.toSet()
val NP_UPPER: Set<NpPart> = setOf(NpPart.Status, NpPart.Back, NpPart.Top, NpPart.Art, NpPart.Mark)
val NP_LOWER: Set<NpPart> = setOf(NpPart.Times, NpPart.Title, NpPart.Rings)

class NpHalf(
    val g: NpLayout,
    val x: Int,
    val y: Int,
    val dy: Int,
    val parts: Set<NpPart>,
    val from: Float,
    val to: Float,
    val flat: Boolean = false,
) {
    val clip: Int = g.s.heightPx - dy

    fun ux(wx: Float): Float = (wx - x) / g.p

    fun uy(wy: Float): Float = (wy - y + dy) / g.p

    fun holds(wx: Float, wy: Float): Boolean {
        if (wx < x || wx >= x + g.s.widthPx || wy < y || wy >= y + clip) return false
        val u = uy(wy)
        return u >= from && u < to
    }
}

fun npFlat(g: NpLayout): NpHalf = NpHalf(g, 0, 0, 0, NP_PARTS, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, true)

fun List<NpHalf>.at(wx: Float, wy: Float): NpHalf? = firstOrNull { it.holds(wx, wy) }

fun List<NpHalf>.holding(p: NpPart): NpHalf = first { p in it.parts }

class NpSplit(val upper: NpHalf, val lower: NpHalf) {
    val halves: List<NpHalf> = listOf(upper, lower)

    companion object {
        private val NONE = Insets(0, 0, 0, 0)

        fun of(w: Window, nav: Nav, large: Boolean, fold: Fold, left: Int): NpSplit? {
            if (fold.top <= 0 || fold.bottom >= w.heightPx) return null
            val n = w.navigationBars
            val c = w.cutout
            val up = scale(
                Window(w.widthPx, fold.top, w.sdk, w.statusBars, Insets(n.left, 0, n.right, 0), w.systemGestures, Insets(c.left, c.top, c.right, 0), NONE),
                nav, Surface.NowPlaying, large,
            )
            val low = scale(
                Window(w.widthPx, w.heightPx - fold.bottom, w.sdk, NONE, Insets(n.left, 0, n.right, 0), w.systemGestures, Insets(c.left, 0, c.right, c.bottom), NONE),
                nav, Surface.NowPlaying, large,
            )
            val ug = NpLayout(up)
            val lg = NpLayout(low)
            val bar = if (nav == Nav.Buttons) n.bottom else 0
            if (bar > lg.bar.y0) return null
            val upper = NpHalf(ug, up.left - left, 0, 0, NP_UPPER, Float.NEGATIVE_INFINITY, up.board.npArtTop + up.board.npArt)
            val lower = NpHalf(lg, low.left - left, fold.bottom, bar, NP_LOWER, low.board.npArtTop + low.board.npArt, Float.POSITIVE_INFINITY)
            return NpSplit(upper, lower)
        }
    }
}

fun DrawScope.npHalf(h: NpHalf, block: DrawScope.() -> Unit) {
    if (h.flat) {
        block()
        return
    }
    translate(h.x.toFloat(), h.y.toFloat()) {
        clipRect(0f, 0f, h.g.s.widthPx.toFloat(), h.clip.toFloat()) {
            translate(0f, -h.dy.toFloat()) { block() }
        }
    }
}
