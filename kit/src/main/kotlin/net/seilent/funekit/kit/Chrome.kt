package net.seilent.funekit.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.seilent.funekit.Case
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Weight
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.px
import kotlin.math.floor
import kotlin.math.max

class PivotInk(val rest: Word, val active: Word, val stdRest: Word, val stdActive: Word)

class ChromePart(val dx: Float = 0f, val dy: Int = 0, val alpha: Float = 1f, val hidden: Boolean = false)

class Chrome(val scale: Scale, val header: Word, val pivots: List<PivotInk>) {
    var active by mutableIntStateOf(0)
        private set
    var fromActive by mutableIntStateOf(0)
        private set
    private var dir by mutableIntStateOf(1)
    private var at: Long? by mutableStateOf(null)
    private var pressAt: Long? by mutableStateOf(null)
    var pressed: Int? by mutableStateOf(null)
        private set
    var lifted by mutableStateOf(false)
        private set
    var headerPen by mutableStateOf(Tok.page_headerX)
    var headerAlpha by mutableStateOf(Roles.pageHeader.alpha)
    var headerPart by mutableStateOf(ChromePart())
    val pivotParts = mutableStateListOf<ChromePart>().also { l -> repeat(pivots.size) { l.add(ChromePart()) } }
    val headerImage = WordImage()

    val count: Int get() = pivots.size

    fun select(i: Int) {
        active = i
        fromActive = i
        at = null
    }

    fun switchTo(i: Int, now: Long, d: Int) {
        if (i == active) return
        fromActive = active
        active = i
        dir = d
        at = now
    }

    fun moving(now: Long): Boolean = at?.let { now - it < Tok.motion_pivotStrip.toLong() } ?: false

    fun since(now: Long): Long? = at?.let { now - it }

    fun clearParts() {
        headerPart = ChromePart()
        for (i in pivotParts.indices) pivotParts[i] = ChromePart()
    }

    fun press(part: Int) {
        pressed = part
        lifted = false
    }

    fun lift() {
        lifted = true
        pressAt = at
    }

    fun clear() {
        pressed = null
        lifted = false
    }

    fun settle() {
        pressAt = at
    }

    fun tick(now: Long, leavesLit: Boolean) {
        val part = pressed ?: return
        if (lifted && !(if (part == HEADER) leavesLit else lit(part, now))) clear()
    }

    fun lit(part: Int, now: Long): Boolean = pressed == part && (part == HEADER || !lifted || (at == pressAt && moving(now)))

    private fun gap(k: Int): Float = when (k) {
        active - 1 -> Tok.pivotLeadGap
        active -> Tok.pivotActiveGap
        else -> Tok.pivotGap
    }

    private fun ink(k: Int, std: Boolean): Word {
        val p = pivots[k]
        return when {
            std && k == active -> p.stdActive
            std -> p.stdRest
            k == active -> p.active
            else -> p.rest
        }
    }

    private fun pos(i: Int, std: Boolean): Float {
        val ppu = if (std) scale.stdPpu else scale.ppu
        var x = 0f
        for (k in 0 until i) {
            x += if (k == active - 1) {
                (if (std) pivots[k].stdRest else pivots[k].rest).width + gap(k) * ppu
            } else {
                ink(k, std).inkRight + gap(k) * ppu - ink(k + 1, std).inkLeft
            }
        }
        return x
    }

    private fun rest(std: Boolean): Float = Tok.hubEdge * (if (std) scale.stdPpu else scale.ppu) - pos(active, std)

    fun pivotOffset(i: Int): Float = pos(i, false)

    fun pivotX(i: Int): Float = rest(false) + pos(i, false)

    fun pivotW(i: Int): Float = (if (i == active) pivots[i].active else pivots[i].rest).width

    private fun strip(now: Long, std: Boolean): Float {
        val e = since(now) ?: return 0f
        val d = Tok.motion_pivotStrip
        if (e < 0 || e >= d) return 0f
        val p = 1 - Curves.expOut6.at(e / d)
        return Math.round((pos(active, std) - pos(fromActive, std)) * p).toFloat()
    }

    fun row(now: Long): Float = rest(false) + strip(now, false)

    private fun stdRow(now: Long): Float = rest(true) + strip(now, true)

    fun pivotOn(i: Int, now: Long): Float {
        val e = since(now)
        val label = Tok.motion_pivotLabel.toLong()
        val lp = if (e == null || e >= label || e < 0) 1f else e.toFloat() / label
        return when (i) {
            active -> if (i != fromActive) lp else 1f
            fromActive -> 1 - lp
            else -> 0f
        }
    }

    fun pivotAlpha(i: Int, now: Long): Float {
        if (lit(i, now)) return Tok.alpha_full
        return lerp(Roles.pivotInactive.alpha, Roles.pivotActive.alpha, pivotOn(i, now))
    }

    private fun headerAt(stdRow: Float): Float = headerPen * scale.stdPpu + (stdRow - Tok.hubEdge * scale.stdPpu) * Tok.motion_pivotHeaderParallax

    fun headerRestX(): Float = headerAt(rest(true))

    fun headerDrawX(now: Long): Float = headerAt(stdRow(now))

    fun pivotBox(i: Int): Edges {
        val x0 = pivotX(i)
        return Edges(floor(x0).toInt(), px(scale.board.pivotTop, scale.ppu), floor(x0 + pivotW(i)).toInt(), px(scale.board.pivotBottom, scale.ppu))
    }

    fun headerBox(): Edges {
        val hx = headerRestX()
        return Edges(floor(hx).toInt(), 0, floor(hx + header.width).toInt(), px(Tok.headerBand, scale.stdPpu))
    }

    companion object {
        const val HEADER = -1
    }
}

fun DrawScope.chrome(c: Chrome, now: Long) {
    val s = c.scale
    val hp = c.headerPart
    if (!hp.hidden) {
        val hy = hp.dy.toFloat()
        val clip = Rect(0f, hy, size.width, hy + px(s.board.pivotTop, s.stdPpu))
        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
            val ha = (if (c.lit(Chrome.HEADER, now)) Tok.alpha_full else c.headerAlpha) * hp.alpha
            val x = c.headerDrawX(now) + hp.dx
            val base = px(Tok.page_headerBaseline, s.stdPpu) + hp.dy
            if (c.moving(now) || hp.dx != floor(hp.dx)) {
                word(c.header, x, base.toFloat(), Ink.header, ha)
            } else {
                wordImage(c.headerImage, c.header, x, base, Ink.header, ha, clip)
            }
        }
    }
    val row = c.row(now)
    for (i in 0 until c.count) {
        val part = c.pivotParts[i]
        if (part.hidden) continue
        val color = lerp(Ink.pivotInactive, Ink.white, c.pivotOn(i, now))
        val w = if (i == c.active) c.pivots[i].active else c.pivots[i].rest
        word(w, row + c.pivotOffset(i) + part.dx, (px(s.board.page_pivotBaseline, s.ppu) + part.dy).toFloat(), color, c.pivotAlpha(i, now) * part.alpha)
    }
}

fun chromeOf(
    m: TextMeasurer,
    fonts: FontFamily.Resolver,
    s: Scale,
    density: Density,
    header: String,
    pivots: List<String>,
    covers: Covers?,
    headerCase: Case = Roles.pageHeader.case,
    headerWeight: Weight = Roles.pageHeader.weight,
): Chrome {
    fun ink(n: String, std: Boolean, bold: Boolean): Word {
        val role = if (bold) Roles.pivotActive else Roles.pivotInactive
        return m.word(n, role, roleStyle(role, s, density, Ink.white, ppu = if (std) s.stdPpu else s.ppu), fonts, density, covers)
    }
    val h = Role(Roles.pageHeader.size, headerWeight, headerCase, Roles.pageHeader.alpha, Roles.pageHeader.tracking)
    return Chrome(
        s,
        m.word(header, h, roleStyle(h, s, density, Ink.header, ppu = s.stdPpu), fonts, density, covers),
        pivots.map { PivotInk(ink(it, false, false), ink(it, false, true), ink(it, true, false), ink(it, true, true)) },
    )
}

@Composable
fun rememberChrome(
    s: Scale,
    header: String,
    pivots: List<String>,
    headerCase: Case = Roles.pageHeader.case,
    headerWeight: Weight = Roles.pageHeader.weight,
): Chrome {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    return remember(s, header, pivots, headerCase, headerWeight, density, fonts, covers) {
        chromeOf(m, fonts, s, density, header, pivots, covers, headerCase, headerWeight)
    }
}

@Composable
fun PageChrome(
    c: Chrome,
    a: Arbiter,
    now: () -> Long,
    onPivot: (Int) -> Unit,
    onHeader: (() -> Unit)?,
    modifier: Modifier = Modifier,
    headerWins: Boolean = false,
    leavesLit: () -> Boolean = { false },
    layer: Int = 0,
) {
    val s = c.scale
    val scope = rememberCoroutineScope()
    val loop = remember(c) { arrayOfNulls<Job>(1) }
    DisposableEffect(c) {
        onDispose {
            loop[0]?.cancel()
            c.clear()
        }
    }
    Box(modifier) {
        Canvas(Modifier.pxSize(s.widthPx, max(px(s.board.pivotBottom, s.ppu), px(s.board.pivotTop, s.stdPpu)))) { chrome(c, now()) }
        fun tap(part: Int, act: () -> Unit) = Tap(onPress = { c.press(part) }, onCancel = { c.clear() }, onTap = {
            c.lift()
            act()
            c.settle()
            c.tick(now(), leavesLit())
            if (c.pressed != null && loop[0]?.isActive != true) {
                loop[0] = scope.launch { while (c.pressed != null && c.lifted) withFrameMillis { c.tick(now(), leavesLit()) } }
            }
        })
        if (onHeader != null && !headerWins) Box(Modifier.pxAt(c.headerBox()).tapTarget(a, tap(Chrome.HEADER, onHeader), layer))
        for (i in 0 until c.count) Box(Modifier.pxAt(c.pivotBox(i)).tapTarget(a, tap(i) { onPivot(i) }, layer))
        if (onHeader != null && headerWins) Box(Modifier.pxAt(c.headerBox()).tapTarget(a, tap(Chrome.HEADER, onHeader), layer))
    }
}
