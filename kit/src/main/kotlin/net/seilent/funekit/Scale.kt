package net.seilent.funekit

import android.os.Build
import android.util.DisplayMetrics
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.gen.TokFit
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Nav { Buttons, Gesture, None }

enum class Surface { Home, Page, NowPlaying, Lyrics, Saver }

class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int)

class Dpi(
    val x: Float,
    val y: Float,
    val stable: Int,
    val modeW: Int,
    val modeH: Int,
    val modeSizes: Int,
    val logicalW: Int,
    val logicalH: Int,
)

class Window(
    val widthPx: Int,
    val heightPx: Int,
    val sdk: Int,
    val statusBars: Insets,
    val navigationBars: Insets,
    val systemGestures: Insets,
    val cutout: Insets,
    val ime: Insets,
    val dpi: Dpi? = null,
)

class Scale(
    val ppu: Float,
    val stdPpu: Float,
    val w: Float,
    val h: Float,
    val stdH: Float,
    val left: Int,
    val widthPx: Int,
    val heightPx: Int,
    val board: TokBoard.Board,
    val nav: Nav,
    val navOn: Boolean,
    val stdContentBottom: Float,
    val edgeBack: Boolean,
    val bottomSwipe: Boolean,
    val statusBar: Boolean,
    val stdStatusOverlap: Float,
    val stdNavOverlap: Float,
    val stdImeTop: Float?,
    val large: Boolean,
    val fit: Scale? = null,
    val coast: Boolean = false,
) {
    val touchPpu: Float get() = fit?.stdPpu ?: stdPpu
}

private const val FX = 65536f
private const val FX_BITS = 16
private const val PCT = 100.0
private const val HALF = 2f
const val A105_PPI = 407.94f
private const val DEADBAND = 0.9f
private const val FLOOR = 0.5f
private const val AXIS = 1.1f
private const val NEAR = 1f

private val buckets = intArrayOf(
    DisplayMetrics.DENSITY_LOW, DisplayMetrics.DENSITY_140, DisplayMetrics.DENSITY_MEDIUM, DisplayMetrics.DENSITY_180,
    DisplayMetrics.DENSITY_200, DisplayMetrics.DENSITY_TV, DisplayMetrics.DENSITY_220, DisplayMetrics.DENSITY_HIGH,
    DisplayMetrics.DENSITY_260, DisplayMetrics.DENSITY_280, DisplayMetrics.DENSITY_300, DisplayMetrics.DENSITY_XHIGH,
    DisplayMetrics.DENSITY_340, DisplayMetrics.DENSITY_360, DisplayMetrics.DENSITY_390, DisplayMetrics.DENSITY_400,
    DisplayMetrics.DENSITY_420, DisplayMetrics.DENSITY_440, DisplayMetrics.DENSITY_450, DisplayMetrics.DENSITY_XXHIGH,
    DisplayMetrics.DENSITY_520, DisplayMetrics.DENSITY_560, DisplayMetrics.DENSITY_600, DisplayMetrics.DENSITY_XXXHIGH,
)

fun px(u: Float, ppu: Float): Int = (u * ppu).roundToInt()

fun textPx(size: Float, ppu: Float, minTextPx: Float): Int = max(minTextPx, size * ppu).roundToInt()

fun fx(u: Float): Long = (u.toBigDecimal() * FX.toBigDecimal()).setScale(0, RoundingMode.HALF_UP).longValueExact()

fun fxU(v: Long): Float = (v.toDouble() / FX).toFloat()

fun fxInt(v: Int): Long = v.toLong() shl FX_BITS

fun fxDiv(a: Long, b: Long): Long = (a shl FX_BITS) / b

fun fxMul(a: Long, b: Long): Long = a * b shr FX_BITS

fun fxOfPx(px: Float, ppu: Long): Long = fxDiv((px.toDouble() * FX).roundToLong(), ppu)

fun px64(u: Long, ppu: Long): Int = (((u * ppu shr FX_BITS) + (1L shl (FX_BITS - 1))) shr FX_BITS).toInt()

fun navMode(w: Window): Nav {
    val g = w.systemGestures
    val n = w.navigationBars
    return when {
        w.sdk >= Build.VERSION_CODES.Q && (g.left > n.left || g.right > n.right) -> Nav.Gesture
        n.left > 0 || n.top > 0 || n.right > 0 || n.bottom > 0 -> Nav.Buttons
        else -> Nav.None
    }
}

private fun largePpu(std: Float): Float =
    ceil((std * FX).roundToInt().toDouble() * Tok.size_largeScale / PCT).toFloat() / FX

enum class DpiReject { Value, Axis, Stable, Bucket, Modes, Size }

fun dpiReject(d: Dpi): DpiReject? = when {
    !(d.x > 0f && d.y > 0f && d.x.isFinite() && d.y.isFinite()) -> DpiReject.Value
    max(d.x, d.y) > AXIS * min(d.x, d.y) -> DpiReject.Axis
    abs(d.x - d.stable) <= NEAR -> DpiReject.Stable
    buckets.any { abs(d.x - it) <= NEAR } -> DpiReject.Bucket
    d.modeSizes > 1 -> DpiReject.Modes
    min(d.modeW, d.modeH) <= 0 || min(d.logicalW, d.logicalH) <= 0 -> DpiReject.Size
    else -> null
}

fun appPpi(d: Dpi): Float? = if (dpiReject(d) != null) null else d.x * min(d.logicalW, d.logicalH) / min(d.modeW, d.modeH)

private fun physicalPpu(d: Dpi?, fit: Float): Float? {
    val ppi = d?.let(::appPpi) ?: return null
    val phys = TokBoard.a105.u * ppi / A105_PPI
    val p = fx(phys)
    return if (p < fx(fit * DEADBAND) && p >= fx(fit * FLOOR)) phys else null
}

private fun pagePpu(w: Window, fit: Float): Float? {
    if (w.widthPx / Tok.canvasW > w.heightPx / TokBoard.v1.canvasH) return null
    return physicalPpu(w.dpi, fit)
}

private fun phonePanel(d: Dpi?): Boolean {
    if (d == null) return false
    val fit = min(min(d.logicalW, d.logicalH) / Tok.canvasW, max(d.logicalW, d.logicalH) / TokBoard.v1.canvasH)
    return physicalPpu(d, fit) != null
}

private fun build(
    w: Window,
    nav: Nav,
    surface: Surface,
    large: Boolean,
    stdPpu: Float,
    widthPx: Int,
    left: Int,
    board: TokBoard.Board,
    fit: Scale?,
    coast: Boolean,
): Scale {
    val stdH = w.heightPx / stdPpu
    val ppu = if (large) largePpu(stdPpu) else stdPpu
    val navOn = nav == Nav.Buttons && w.navigationBars.bottom > 0 && surface != Surface.Home && surface != Surface.Saver
    return Scale(
        ppu = ppu,
        stdPpu = stdPpu,
        w = widthPx / ppu,
        h = w.heightPx / ppu,
        stdH = stdH,
        left = left,
        widthPx = widthPx,
        heightPx = w.heightPx,
        board = board,
        nav = nav,
        navOn = navOn,
        stdContentBottom = if (navOn) (w.heightPx - w.navigationBars.bottom) / stdPpu else stdH,
        edgeBack = nav != Nav.Gesture,
        bottomSwipe = nav != Nav.Gesture,
        statusBar = surface == Surface.Home || surface == Surface.Page,
        stdStatusOverlap = max(0f, w.statusBars.top / stdPpu - (Tok.statusTop + Tok.statusHeight)),
        stdNavOverlap = if (nav == Nav.Buttons && !navOn) max(0f, w.navigationBars.bottom / stdPpu - Tok.navH) else 0f,
        stdImeTop = if (w.ime.bottom > 0) (w.heightPx - w.ime.bottom) / stdPpu else null,
        large = large,
        fit = fit,
        coast = coast,
    )
}

fun scale(w: Window, nav: Nav, surface: Surface, large: Boolean): Scale {
    val stdPpu = min(w.widthPx / Tok.canvasW, w.heightPx / TokBoard.v1.canvasH)
    val widthPx = min(w.widthPx, px(Tok.canvasW, stdPpu))
    val sideL = max(w.navigationBars.left, w.cutout.left)
    val sideR = max(w.navigationBars.right, w.cutout.right)
    val left = if (widthPx < w.widthPx) sideL + ((w.widthPx - sideL - sideR - widthPx) / HALF).roundToInt() else 0
    val board = TokFit.at(w.heightPx / stdPpu, stdPpu)
    val coast = phonePanel(w.dpi)
    val fit = build(w, nav, surface, large, stdPpu, widthPx, left, board, null, coast)
    if (surface != Surface.Page) return fit
    val page = pagePpu(w, stdPpu) ?: return fit
    val std = if (large) build(w, nav, surface, false, stdPpu, widthPx, left, board, null, coast) else fit
    return build(w, nav, surface, large, page, w.widthPx, 0, TokFit.at(w.heightPx / page, page, board), std, coast)
}

fun Scale.kept(): Scale {
    val b = fit ?: this
    return Scale(
        ppu = b.stdPpu,
        stdPpu = b.stdPpu,
        w = b.widthPx / b.stdPpu,
        h = b.heightPx / b.stdPpu,
        stdH = b.stdH,
        left = b.left,
        widthPx = b.widthPx,
        heightPx = b.heightPx,
        board = b.board,
        nav = b.nav,
        navOn = b.navOn,
        stdContentBottom = b.stdContentBottom,
        edgeBack = b.edgeBack,
        bottomSwipe = b.bottomSwipe,
        statusBar = b.statusBar,
        stdStatusOverlap = b.stdStatusOverlap,
        stdNavOverlap = b.stdNavOverlap,
        stdImeTop = b.stdImeTop,
        large = large,
        coast = coast,
    )
}

fun keptText(r: Role, s: Scale): Role =
    if (!s.large || fx(r.size) >= fx(Tok.size_keptTextMax)) r else Role(fxU(fx(r.size) * fx(Tok.size_largeScale) / fxInt(PCT.toInt())), r.weight, r.case, r.alpha, r.tracking)
