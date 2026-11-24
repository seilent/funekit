package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.flow.first
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.OverHud
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val HALF = 2
private const val PEN = 64
private const val COLS = 2

data class AdjustCol(val num: String, val label: String, val up: Boolean = true, val down: Boolean = true)

enum class AdjustHit { None, Exit, Up0, Down0, Up1, Down1 }

class AdjustLegs(val scrim: Float, val number: Float, val exit: Float, val group: Float, val text: Float, val textDx: Float) {
    companion object {
        val Hidden = AdjustLegs(0f, 0f, 0f, 0f, 0f, 0f)
    }
}

interface AdjustSource {
    val hud: AdjustHud

    fun tap(h: AdjustHit)

    fun back()

    fun number(side: Int): Boolean
}

private fun progress(e: Long, d: Float): Float = if (d <= 0f) 1f else (e / d).coerceIn(0f, 1f)

class AdjustHud : OverHud {
    var shown by mutableStateOf(false)
        private set
    var cols: List<AdjustCol> = emptyList()
        private set
    var line1 = ""
        private set
    var line2 = ""
        private set
    var revision by mutableIntStateOf(0)
        private set

    private var inAt = 0L
    private var hideAt = 0L
    override var hiding = false
        private set
    private val numbers = arrayOfNulls<IntRect>(COLS)

    private fun bump() {
        revision++
    }

    fun open(now: Long, cols: List<AdjustCol>, line1: String, line2: String) {
        shown = true
        hiding = false
        inAt = now
        numbers.fill(null)
        this.cols = cols
        this.line1 = line1
        this.line2 = line2.uppercase()
        bump()
    }

    fun set(cols: List<AdjustCol>, line2: String) {
        this.cols = cols
        this.line2 = line2.uppercase()
        bump()
    }

    override fun hide(now: Long) {
        if (hiding) return
        hiding = true
        hideAt = now
        bump()
    }

    override fun dismiss() {
        shown = false
        hiding = false
        numbers.fill(null)
        bump()
    }

    override fun opening(now: Long): Boolean = shown && !hiding && now < inAt + Tok.motion_volumeOpenEnd.toLong()

    override fun closing(now: Long): Boolean = hiding

    override fun visible(now: Long): Boolean = shown && (!hiding || now < hideAt + VolumeHud.closeMs())

    override fun wakeAt(now: Long): Long? = null

    fun moving(now: Long): Boolean {
        if (!shown) return false
        if (hiding) return now < hideAt + VolumeHud.closeMs()
        return now < inAt + Tok.motion_volumeOpenEnd.toLong()
    }

    fun legs(now: Long): AdjustLegs {
        if (!shown) return AdjustLegs.Hidden
        val e = now - inAt
        var scrim = progress(e + Tok.motion_volumeScrimLead.toLong(), Tok.motion_volumeOpen)
        var number = Curves.powOut17.at(progress(e - Tok.motion_volumeNumberAt.toLong(), Tok.motion_volumeNumberIn))
        var exit = Curves.powOut17.at(progress(e - Tok.motion_volumeExitAt.toLong(), Tok.motion_volumeExitIn))
        var group = Curves.cubicOut.at(progress(e + Tok.motion_volumeGroupLead.toLong(), Tok.motion_volumeGroupFade))
        var text = Curves.easeOut.at(progress(e - Tok.motion_volumeTextFadeAt.toLong(), Tok.motion_volumeTextFadeIn))
        val slid = Curves.easeOut.at(progress(e - Tok.motion_volumeTextAt.toLong(), Tok.motion_volumeTextSlide))
        val dx = Tok.motion_volumeTextShift * (1 - slid)
        if (hiding) {
            val c = now - hideAt
            val part = 1 - progress(c, Tok.motion_volumeTouchClose)
            number *= part
            exit *= part
            group *= part
            text *= part
            scrim *= 1 - Curves.easeOut.at(progress(c + Tok.motion_volumeTouchScrimLead.toLong(), Tok.motion_volumeTouchScrim))
        }
        return AdjustLegs(scrim, number, exit, group, text, dx)
    }

    fun colX(i: Int, s: Scale): Int {
        val p = s.stdPpu
        if (cols.size < COLS) return s.widthPx / HALF
        return if (i == 0) px(Tok.edge + Tok.adjustColumn, p) else px(Tok.canvasW - Tok.edge - Tok.adjustColumn, p)
    }

    fun hit(x: Float, y: Float, s: Scale): AdjustHit {
        val p = s.stdPpu
        val hx = x.roundToInt()
        val hy = y.roundToInt()
        val e = px(Tok.adjustExit, p)
        val r = px(Tok.overlayRing, p) / HALF
        if (hx < e && hy < e) return AdjustHit.Exit
        for (i in 0 until minOf(cols.size, COLS)) {
            val cx = colX(i, s)
            if (hx < cx - r || hx >= cx + r) continue
            val py = px(Tok.overlayPlusY, p)
            val my = px(s.board.overlayMinusY, p)
            if (hy >= py - r && hy < py + r) return if (cols[i].up) AdjustHit.entries[AdjustHit.Up0.ordinal + HALF * i] else AdjustHit.None
            if (hy >= my - r && hy < my + r) return if (cols[i].down) AdjustHit.entries[AdjustHit.Down0.ordinal + HALF * i] else AdjustHit.None
        }
        return AdjustHit.None
    }

    fun placed(i: Int, r: IntRect?) {
        if (i in 0 until COLS) numbers[i] = r
    }

    fun numberAt(x: Float, y: Float): Int {
        val hx = x.roundToInt()
        val hy = y.roundToInt()
        for (i in 0 until minOf(cols.size, COLS)) {
            val r = numbers[i] ?: continue
            if (hx >= r.left && hx < r.right && hy >= r.top && hy < r.bottom) return i
        }
        return -1
    }
}

class AdjustInk(private val s: Scale, private val measurer: TextMeasurer, private val fonts: FontFamily.Resolver, private val density: Density, private val covers: Covers?) {
    private val p = s.stdPpu
    private var used = HashMap<Pair<Role, String>, Word>()
    private var last = HashMap<Pair<Role, String>, Word>()

    fun px(r: Role): Int = rolePx(r, s, p)

    fun frame() {
        last = used
        used = HashMap()
    }

    fun word(r: Role, t: String): Word = used.getOrPut(r to t) {
        last[r to t] ?: measurer.word(t, r, roleStyle(r, s, density, Ink.white, ppu = p), fonts, density, covers, s.board.fontHanMinPx)
    }
}

class AdjustText(val role: Role, val text: String, val x64: Int, val base: Int, val alpha: Float, val clip: IntRect?)

class AdjustGlyph(val plus: Boolean, val x: Int, val y: Int, val alpha: Float)

class AdjustPlace(val scrim: Float, val texts: List<AdjustText>, val glyphs: List<AdjustGlyph>, val numbers: List<IntRect>)

fun adjustPlace(h: AdjustHud, s: Scale, ink: AdjustInk, now: Long): AdjustPlace {
    val p = s.stdPpu
    val b = s.board
    val o = h.legs(now)
    val ls = Roles.overlayLabel
    val ns = Roles.overlayNumber
    val lit = o.group * Tok.overlayLit
    val dim = o.group * Tok.overlayDim
    val texts = ArrayList<AdjustText>()
    val glyphs = ArrayList<AdjustGlyph>()
    val numbers = ArrayList<IntRect>()
    texts += AdjustText(ls, EXIT, pen64(Tok.edge, p), px(Tok.edge, p) + Metrics.ascent(ink.px(ls)), o.exit * Tok.overlayLit, null)
    val n = minOf(h.cols.size, COLS)
    val ltop = px(b.overlayMinusY + Tok.overlayGlyph / HALF, p)
    val npx = ink.px(ns)
    val base = px(b.overlayRingY, p) + (npx * Tok.adjustNumberDrop).roundToInt()
    for (i in 0 until n) {
        val col = h.cols[i]
        val cx = h.colX(i, s)
        glyphs += AdjustGlyph(true, cx, px(Tok.overlayPlusY, p), if (col.up) lit else dim)
        glyphs += AdjustGlyph(false, cx, px(b.overlayMinusY, p), if (col.down) lit else dim)
        texts += AdjustText(ls, col.label, cx * PEN - pen64(ink.word(ls, col.label).width) / HALF, ltop + px(Tok.overlayLabelDrop, p), dim, null)
        val w64 = pen64(ink.word(ns, col.num).width)
        val x64 = if (n < COLS) pen64(Tok.edge, p) else cx * PEN - w64 / HALF
        val x0 = x64 / PEN
        numbers += IntRect(x0, base - Metrics.ascent(npx), x0 + ceil(w64.toFloat() / PEN).toInt() + 1, base + Metrics.descent(npx))
        texts += AdjustText(ns, col.num, x64, base, o.number * Tok.overlayLit, null)
    }
    val tb = px(b.overlayTitleBottom, p) - Metrics.descent(ink.px(Roles.toastTitle))
    val clip = IntRect(px(Tok.edge, p), 0, px(Tok.canvasW - Tok.edge, p), s.heightPx)
    val dx = pen64(px(o.textDx, p).toFloat())
    texts += AdjustText(Roles.toastTitle, h.line2, pen64(Tok.edge, p) + dx, tb, o.text * Tok.overlayDim * Roles.toastTitle.alpha, clip)
    texts += AdjustText(Roles.toastArtist, h.line1, pen64(Tok.edge, p) + dx, tb - px(Tok.overlayTextPitch, p), o.text * Tok.overlayDim * Roles.toastArtist.alpha, clip)
    return AdjustPlace(o.scrim * Tok.scrim_overlay_alpha, texts, glyphs, numbers)
}

fun DrawScope.adjustHud(h: AdjustHud, s: Scale, ink: AdjustInk, now: Long) {
    if (!h.visible(now)) return
    ink.frame()
    val pl = adjustPlace(h, s, ink, now)
    for (i in 0 until COLS) h.placed(i, pl.numbers.getOrNull(i))
    drawRect(Ink.black, alpha = pl.scrim)
    val gpx = px(Tok.overlayGlyph, s.stdPpu).toFloat()
    for (g in pl.glyphs) glyph(if (g.plus) Glyphs.plus else Glyphs.minus, Offset(g.x.toFloat(), g.y.toFloat()), gpx, Ink.white, g.alpha)
    for (t in pl.texts) {
        if (t.alpha <= 0f || t.text.isEmpty()) continue
        val w = ink.word(t.role, t.text)
        val c = t.clip
        if (c == null) {
            word(w, t.x64.toFloat() / PEN, t.base.toFloat(), Ink.white, t.alpha)
        } else {
            clipRect(c.left.toFloat(), c.top.toFloat(), c.right.toFloat(), c.bottom.toFloat()) { word(w, t.x64.toFloat() / PEN, t.base.toFloat(), Ink.white, t.alpha) }
        }
    }
}

private const val EXIT = "exit"

@Composable
fun AdjustLayer(h: AdjustHud, s: Scale, covers: Covers?, now: () -> Long) {
    val measurer = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val ink = remember(s, measurer, fonts, density, covers) { AdjustInk(s, measurer, fonts, density, covers) }
    var frame by remember { mutableLongStateOf(now()) }
    LaunchedEffect(h) {
        while (true) {
            withFrameMillis { at -> frame = at }
            if (h.moving(frame)) continue
            val seen = h.revision
            snapshotFlow { h.revision }.first { it != seen }
        }
    }
    Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { adjustHud(h, s, ink, frame) }
}
