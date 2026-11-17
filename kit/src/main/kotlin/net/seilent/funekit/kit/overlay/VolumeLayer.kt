package net.seilent.funekit.kit.overlay

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import net.seilent.funekit.Fonts
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.GlowCache
import net.seilent.funekit.kit.GlowMask
import net.seilent.funekit.kit.GlowText
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.boxCenter
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.outline
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.pressGlow
import net.seilent.funekit.kit.proj.tiltMatrix
import net.seilent.funekit.kit.proj.tiltPoint
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.ring
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.Mark
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.textPx
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val HALF = 2
private const val ROOM = 3
private const val EIGHTHS = 8
private const val PEN = 64
private const val ARC_STEP = 24
private const val PADS = 3
private const val ALPHA_BITS = 24
private const val SCRIM = "the HUD scrim darkens the whole page under it, so under the half contrast rule the page it darkens counts as ink"
private const val ON_RING = "the press disc fills the ring under the ring and its glyph, whose ink sits inside its box"
private const val UNDER_PRESS = "its press glow or press disc is drawn with it: the glow region masks its window, the disc's ink fills it"
private val FLAT = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

enum class VolumePart { GlowPrev, GlowRing, GlowNext, GlowPlus, GlowMinus, Disc, Readout, Prev, Next, Ring, Play, Pause, Plus, Minus, Label, Number }

data class VolumePlace(val scale: Float, val x: Float, val y: Float) {
    fun matrix(): DoubleArray = doubleArrayOf(scale.toDouble(), 0.0, x.toDouble(), 0.0, scale.toDouble(), y.toDouble(), 0.0, 0.0, 1.0)
}

data class VolumeShown(val place: VolumePlace, val alpha: Float)

data class VolumeText(val alpha: Float, val dx: Int)

data class VolumeDraw(
    val revision: Int,
    val scrim: Float,
    val plate: List<Double>?,
    val parts: List<VolumeShown>,
    val exit: Float,
    val exitGlow: Float,
    val left: Int,
    val cross: Boolean,
    val lines: List<VolumeText>,
    val old: List<VolumeText>,
)

class VolumeInk(
    val label: Word,
    val exit: Word,
    val number: Word,
    val numberPx: Int,
    val readout: Word,
    val artist: Word,
    val title: Word,
    val oldArtist: Word,
    val oldTitle: Word,
    val artistBase: Int,
    val titleBase: Int,
)

class VolumeParts(val board: TokBoard.Board, val ppu: Float, val w: Float = Tok.canvasW) {
    val cx: Int get() = px(w, ppu) / HALF
    val ringY: Int get() = px(board.vol_ringY, ppu)

    fun rolePx(r: Role): Int = textPx(r.size, ppu, board.minTextPx)

    fun capMid(size: Int): Int = (size * Tok.capHeight / HALF).roundToInt()

    fun labelBase(): Int = px(board.vol_labelY, ppu) + (rolePx(Roles.volLabel) * Tok.capHeight).roundToInt()

    fun labelX(label: Word): Float = cx - label.width / HALF

    fun labelBox(label: Word): IntArray {
        val x = labelX(label)
        val b = labelBase()
        return intArrayOf((x + label.inkLeft).toInt(), (b + label.inkTop).toInt(), (x + label.inkRight).toInt(), (b + label.inkBottom).toInt())
    }

    fun exitX(exit: Word): Float = Tok.vol_exitX * ppu - (exit.inkLeft + exit.inkRight) / HALF

    fun exitBase(): Int = px(board.vol_exitY, ppu) + capMid(rolePx(Roles.volExit))

    fun numberRoom64(): Int = pen64(Tok.canvasW / HALF - Tok.vol_plus * ROOM / EIGHTHS, ppu) - pen64(Tok.vol_numberX, ppu)

    fun numberX(number: Word): Float = Tok.vol_numberX * ppu - number.inkLeft

    fun numberBase(size: Int): Int = px(board.vol_numberY, ppu) + capMid(size)

    fun readoutX(readout: Word): Float = cx - readout.width / HALF

    fun readoutBase(): Int = ringY + (rolePx(Roles.overlayNumber) * Tok.adjustNumberDrop).roundToInt()

    fun textTop(): Int {
        val label = labelBase() - capMid(rolePx(Roles.volLabel))
        return maxOf(px(board.vol_textY, ppu), label + px(board.vol_textGap, ppu))
    }

    fun artistBase(): Int = textTop() + capMid(rolePx(Roles.volArtist))

    fun titleBase(): Int = artistBase() + px(Tok.vol_textPitch, ppu)

    fun lineRight(): Int = px(w - Tok.edge, ppu)

    fun anchor(p: VolumePart, label: IntArray? = null): Pair<Int, Int> {
        val sx = px(Tok.vol_skipX, ppu)
        return when (p) {
            VolumePart.Prev, VolumePart.GlowPrev -> cx - sx to ringY
            VolumePart.Next, VolumePart.GlowNext -> cx + sx to ringY
            VolumePart.Plus, VolumePart.GlowPlus -> cx to px(board.vol_plusY, ppu)
            VolumePart.Minus, VolumePart.GlowMinus -> cx to px(board.vol_minusY, ppu)
            VolumePart.Label -> label?.let { (l, t, r, b) -> (l + r) / HALF to (t + b) / HALF } ?: (cx to ringY)
            else -> cx to ringY
        }
    }

    fun flat(p: VolumePart, l: VolumeLook, label: IntArray? = null): VolumePlace {
        if (p == VolumePart.Number || p == VolumePart.Readout) return VolumePlace(1f, 0f, 0f)
        val (ax, ay) = anchor(p, label)
        val x = cx + (ax - cx) * l.k - l.size * ax
        val y = ringY + (ay - ringY) * l.k - l.size * ay
        if (l.size == 1f) return VolumePlace(1f, x.roundToInt().toFloat(), y.roundToInt().toFloat())
        return VolumePlace(l.size, x, y)
    }

    fun alpha(p: VolumePart, l: VolumeLook, hud: VolumeHud): Float {
        val lit = l.group * Tok.overlayLit
        val glow = { at: VolumeHud.Hit -> if (hud.pressed() == at) l.group * l.press * Tok.overlayLit else 0f }
        return when (p) {
            VolumePart.Number -> l.number * Tok.overlayLit
            VolumePart.Play -> if (hud.playing) 0f else lit
            VolumePart.Pause -> if (hud.playing) lit else 0f
            VolumePart.Readout -> if (hud.seeking) l.group * Tok.overlayDim else 0f
            VolumePart.GlowPrev -> glow(VolumeHud.Hit.Prev)
            VolumePart.GlowRing -> glow(VolumeHud.Hit.Ring)
            VolumePart.GlowNext -> glow(VolumeHud.Hit.Next)
            VolumePart.GlowPlus -> glow(VolumeHud.Hit.Plus)
            VolumePart.GlowMinus -> glow(VolumeHud.Hit.Minus)
            VolumePart.Disc -> if (hud.pressed() == VolumeHud.Hit.Ring) l.group * l.press * Tok.vol_discAlpha else 0f
            else -> lit
        }
    }

    fun exitAlpha(l: VolumeLook): Float = l.exit * Tok.overlayLit

    fun exitGlowAlpha(l: VolumeLook): Float = l.exit * l.exitGlow * Tok.overlayLit

    fun lineLeft(l: VolumeLook): Int = px(if (l.cross) Tok.vol_textClip else Tok.vol_textX, ppu)

    fun lineAlpha(line: VolumeLine, r: Role): Float = line.a * Tok.overlayLit * r.alpha

    fun lineDx(line: VolumeLine): Int = (line.dx * ppu).roundToInt()

    fun plate(hud: VolumeHud, k: Float): DoubleArray =
        tiltMatrix(hud.plate(), k.toDouble(), px(w, ppu) / HALF.toDouble(), px(hud.h, ppu) / HALF.toDouble(), (Tok.motion_volumeTiltEye * w * ppu).toDouble())

    fun pausePx(): Int {
        val p = px(Tok.vol_pause, ppu)
        if ((p - px(Tok.vol_ring, ppu)) and 1 == 0) return p
        return if (Tok.vol_pause * ppu < p) p - 1 else p + 1
    }

    fun playPx(): Int {
        val want = Tok.vol_pause * ppu * Tok.vol_playScale
        val p = want.roundToInt()
        if ((p - px(Tok.vol_ring, ppu)) and 1 == 0) return p
        return if (want < p) p - 1 else p + 1
    }
}

fun screenBox(pts: List<Offset>, m: DoubleArray): Rect {
    var x0 = Double.MAX_VALUE
    var y0 = Double.MAX_VALUE
    var x1 = -Double.MAX_VALUE
    var y1 = -Double.MAX_VALUE
    for (o in pts) {
        val (sx, sy) = tiltPoint(m, o.x.toDouble(), o.y.toDouble())
        x0 = minOf(x0, sx)
        y0 = minOf(y0, sy)
        x1 = maxOf(x1, sx)
        y1 = maxOf(y1, sy)
    }
    return Rect(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
}

private fun whole(e: Rect): IntArray = intArrayOf(floor(e.left).toInt(), floor(e.top).toInt(), ceil(e.right).toInt(), ceil(e.bottom).toInt())

fun screenBox(box: IntArray, m: DoubleArray): IntArray {
    val (l, t, r, b) = box.map { it.toFloat() }
    return whole(screenBox(listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)), m))
}

private fun glyphPoints(g: Glyph, c: Offset, slot: Int): List<Offset> {
    val q = slot / Glyphs.box
    val half = Glyphs.box / HALF
    val at = boxCenter(c, slot.toFloat())
    val pad = if (g.fill) 0f else Glyphs.stroke / HALF
    val pads = listOf(-pad, pad)
    return outline(g.path).subs.flatten().flatMap { o ->
        pads.flatMap { dx -> pads.map { dy -> Offset(at.x + (o.x + dx - half) * q, at.y + (o.y + dy - half) * q) } }
    }
}

fun VolumeParts.partPoints(p: VolumePart, ink: VolumeInk): List<Offset>? {
    fun corners(l: Float, t: Float, r: Float, b: Float) = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))
    fun inkOf(w: Word, x: Float, base: Int) = if (w.inked) corners(x + w.inkLeft, base + w.inkTop, x + w.inkRight, base + w.inkBottom) else null
    val (ax, ay) = anchor(p)
    val c = Offset(ax.toFloat(), ay.toFloat())
    fun circle(side: Int): List<Offset> {
        val o = boxCenter(c, side.toFloat())
        val r = side / HALF.toFloat()
        return List(ARC_STEP * HALF) { i ->
            val t = PI * i / ARC_STEP
            Offset(o.x + r * cos(t).toFloat(), o.y + r * sin(t).toFloat())
        }
    }
    fun mask(m: GlowMask?) = m?.let { corners((ax + it.x).toFloat(), (ay + it.y).toFloat(), (ax + it.x + it.image.width).toFloat(), (ay + it.y + it.image.height).toFloat()) }
    val rpx = px(Tok.vol_ring, ppu)
    val stroke = px(Tok.vol_ringStroke, ppu)
    return when (p) {
        VolumePart.Label -> inkOf(ink.label, labelX(ink.label), labelBase())
        VolumePart.Number -> inkOf(ink.number, numberX(ink.number), numberBase(ink.numberPx))
        VolumePart.Readout -> inkOf(ink.readout, readoutX(ink.readout), readoutBase())
        VolumePart.Ring -> circle(rpx)
        VolumePart.Disc -> (rpx - HALF * stroke).takeIf { it > 0 }?.let { circle(it) }
        VolumePart.GlowRing -> mask(GlowCache.halo(rpx, stroke, px(Tok.vol_glowRing, ppu)))
        VolumePart.GlowPrev, VolumePart.GlowNext, VolumePart.GlowPlus, VolumePart.GlowMinus -> mask(glyphGlow(p))
        else -> glyphOf(p)?.let { (g, slot) -> glyphPoints(g, c, slot) }
    }
}

fun VolumeParts.partBox(p: VolumePart, ink: VolumeInk): IntArray? = partPoints(p, ink)?.let { whole(screenBox(it, FLAT)) }

fun VolumeParts.drawn(hud: VolumeHud, ink: VolumeInk, now: Long): VolumeDraw? {
    if (now < 0 || !hud.visible(now)) return null
    val l = hud.look(now)
    val k = hud.tiltK(now)
    val plate = if (k != 0f) plate(hud, k).toList() else null
    val label = labelBox(ink.label)
    val still = VolumePlace(1f, 0f, 0f)
    val parts = VolumePart.entries.map { VolumeShown(if (plate == null) flat(it, l, label) else still, alpha(it, l, hud)) }
    val roles = listOf(Roles.volArtist, Roles.volTitle)
    fun texts(ls: List<VolumeLine>) = ls.zip(roles) { line, r -> VolumeText(lineAlpha(line, r), lineDx(line)) }
    return VolumeDraw(hud.revision, l.scrim * Tok.scrim_overlay_alpha, plate, parts, exitAlpha(l), exitGlowAlpha(l), lineLeft(l), l.cross, texts(l.line), texts(l.old))
}

private fun markOf(p: VolumePart): Mark = when (p) {
    VolumePart.GlowPrev, VolumePart.GlowRing, VolumePart.GlowNext, VolumePart.GlowPlus, VolumePart.GlowMinus -> Mark.Glow
    VolumePart.Disc, VolumePart.Ring -> Mark.Ring
    VolumePart.Readout, VolumePart.Label, VolumePart.Number -> Mark.Text
    VolumePart.Prev, VolumePart.Next, VolumePart.Play, VolumePart.Pause, VolumePart.Plus, VolumePart.Minus -> Mark.Glyph
}

private fun pressOf(p: VolumePart): VolumePart? = when (p) {
    VolumePart.Prev -> VolumePart.GlowPrev
    VolumePart.Next -> VolumePart.GlowNext
    VolumePart.Plus -> VolumePart.GlowPlus
    VolumePart.Minus -> VolumePart.GlowMinus
    VolumePart.Ring -> VolumePart.GlowRing
    VolumePart.Play, VolumePart.Pause -> VolumePart.Disc
    else -> null
}

private fun VolumeParts.record(rec: DrawRecord, p: VolumePart, ink: VolumeInk, d: VolumeDraw) {
    val (place, a) = d.parts[p.ordinal]
    if (a <= 0f) return
    val pts = partPoints(p, ink) ?: return
    val box = screenBox(pts, d.plate?.toDoubleArray() ?: place.matrix())
    val mark = { rec.box("volume.${p.name.lowercase()}", markOf(p), box, a) }
    val pressed = pressOf(p)?.let { d.parts[it.ordinal].alpha > 0f } == true
    when {
        p == VolumePart.Disc -> rec.skip(ON_RING, mark)
        pressed -> rec.skip(UNDER_PRESS, mark)
        else -> mark()
    }
}

fun numberFit(full: Int, room64: Int, ink64: (Int) -> Int): Int {
    var size = full
    val w = ink64(size)
    if (w > room64 && w > 0) size = size * room64 / w
    while (size > 1 && ink64(size) > room64) size--
    return size
}

class LineInk(private val m: IntArray, private val w: Int, val h: Int, val up: Int) {
    fun at(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h && m[y * w + x] != 0

    fun rows(x0: Int, x1: Int): Pair<Int, Int> {
        var top = -1
        var bottom = -1
        for (y in 0 until h) {
            if ((x0 until x1).any { at(it, y) }) {
                if (top < 0) top = y
                bottom = y
            }
        }
        return top to bottom
    }

    fun low(): Int = (h - 1 downTo 0).firstOrNull { y -> (0 until w).any { at(it, y) } } ?: -1
}

fun VolumeParts.lineInk(line: Word, size: Int, d: Density): LineInk {
    val w = px(this.w - Tok.edge + Tok.motion_volumeTextOutShift, ppu)
    val up = Metrics.ascent(size) + Metrics.descent(size)
    val h = up + PADS * Metrics.descent(size) + PADS
    val img = ImageBitmap(w, h)
    if (line.inked) {
        CanvasDrawScope().draw(d, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat())) {
            word(line, Tok.vol_textX * ppu, up.toFloat(), Ink.white, 1f)
        }
    }
    val pixels = IntArray(w * h)
    img.readPixels(pixels)
    return LineInk(IntArray(w * h) { pixels[it] ushr ALPHA_BITS }, w, h, up)
}

fun VolumeParts.placeLines(artist: LineInk, title: LineInk, artistTop: Int, labelBottom: Int, h: Int): Pair<Int, Int> {
    var a = artistBase()
    val x0 = px(Tok.vol_textX, ppu)
    val x1 = lineRight()
    val (a0, a1) = artist.rows(x0, x1)
    val (t0, t1) = title.rows(x0, x1)
    val low = title.low()
    fun shared(d: Int): Boolean {
        if (a0 < 0 || t0 < 0) return false
        for (y in a0..a1) {
            val ty = y - artist.up + title.up - d
            if (ty in t0..t1 && (x0 until x1).any { artist.at(it, y) && title.at(it, ty) }) return true
        }
        return false
    }
    var d = titleBase() - a
    while (shared(d)) d++
    val drop = if (low < 0) 0 else low - title.up + 1
    val need = if (low < 0) 0 else max(0, a + d + drop - h)
    val room = max(0, a + artistTop - labelBottom)
    if (need <= room) {
        a -= need
        return a to a + d
    }
    a -= room
    for (e in 1 until d) {
        if (a + e + drop > h) break
        val clear = a0 < 0 || (e + t0 - title.up > a0 - artist.up && e + t1 - title.up > a1 - artist.up)
        if (clear && !shared(e)) return a to a + e
    }
    return a to a + d
}

fun TextMeasurer.volumeInk(hud: VolumeHud, v: VolumeParts, fonts: FontFamily.Resolver, density: Density, covers: Covers?, old: Pair<String, String>? = null): VolumeInk {
    fun style(r: Role, size: Int = v.rolePx(r)) = Fonts.style(r, size, density, r.tracking * v.ppu).merge(TextStyle(color = Ink.white))
    fun of(text: String, r: Role, size: Int = v.rolePx(r)) = word(text, r, style(r, size), fonts, density, covers)
    val num = VolumeHud.numberText(hud.percent)
    val full = v.rolePx(Roles.volNumber)
    val size = numberFit(full, v.numberRoom64()) { s -> of(num, Roles.volNumber, s).let { ((it.inkRight - it.inkLeft) * PEN).roundToInt() } }
    val label = of("volume", Roles.volLabel)
    val artist = of(hud.artist, Roles.volArtist)
    val title = of(hud.title, Roles.volTitle)
    val (ab, tb) = v.placeLines(
        v.lineInk(artist, v.rolePx(Roles.volArtist), density),
        v.lineInk(title, v.rolePx(Roles.volTitle), density),
        floor(artist.inkTop).toInt(),
        v.labelBase() + ceil(label.inkBottom).toInt(),
        px(hud.h, v.ppu),
    )
    return VolumeInk(
        label = label,
        exit = of("exit", Roles.volExit),
        number = of(num, Roles.volNumber, size),
        numberPx = size,
        readout = if (hud.seeking) of(hud.readout, Roles.overlayNumber) else Word.none,
        artist = artist,
        title = title,
        oldArtist = old?.let { of(it.first, Roles.volArtist) } ?: Word.none,
        oldTitle = old?.let { of(it.second, Roles.volTitle) } ?: Word.none,
        artistBase = ab,
        titleBase = tb,
    )
}

private fun VolumeParts.glyphOf(p: VolumePart) = when (p) {
    VolumePart.Plus, VolumePart.GlowPlus -> Glyphs.plus to px(Tok.vol_plus, ppu)
    VolumePart.Minus, VolumePart.GlowMinus -> Glyphs.minus to px(Tok.vol_minus, ppu)
    VolumePart.Prev, VolumePart.GlowPrev -> Glyphs.skipPrevious to px(Tok.vol_skip, ppu)
    VolumePart.Next, VolumePart.GlowNext -> Glyphs.skipNext to px(Tok.vol_skip, ppu)
    VolumePart.Play -> Glyphs.play to playPx()
    VolumePart.Pause -> Glyphs.pauseWide to pausePx()
    else -> null
}

fun VolumeParts.glyphGlow(p: VolumePart): GlowMask? {
    val (g, size) = glyphOf(p) ?: return null
    return GlowCache.glyph(g, size, 0, px(Tok.vol_glowSkip, ppu))
}

private fun DrawScope.volumePart(v: VolumeParts, ink: VolumeInk, p: VolumePart, a: Float) {
    if (a <= 0f) return
    val (ax, ay) = v.anchor(p)
    val c = Offset(ax.toFloat(), ay.toFloat())
    val rpx = px(Tok.vol_ring, v.ppu)
    val stroke = px(Tok.vol_ringStroke, v.ppu)
    when (p) {
        VolumePart.Ring -> ring(c, rpx.toFloat(), stroke.toFloat(), Ink.white, a)
        VolumePart.Disc -> {
            val d = rpx - HALF * stroke
            if (d > 0) ring(c, d.toFloat(), (d / HALF).toFloat(), Ink.white, a)
        }
        VolumePart.GlowRing -> GlowCache.halo(rpx, stroke, px(Tok.vol_glowRing, v.ppu))?.let { drawImage(it.image, Offset((ax + it.x).toFloat(), (ay + it.y).toFloat()), alpha = a) }
        VolumePart.GlowPrev, VolumePart.GlowNext, VolumePart.GlowPlus, VolumePart.GlowMinus ->
            v.glyphGlow(p)?.let { drawImage(it.image, Offset((ax + it.x).toFloat(), (ay + it.y).toFloat()), alpha = a) }
        VolumePart.Label -> word(ink.label, v.labelX(ink.label), v.labelBase().toFloat(), Ink.white, a)
        VolumePart.Number -> word(ink.number, v.numberX(ink.number), v.numberBase(ink.numberPx).toFloat(), Ink.white, a)
        VolumePart.Readout -> word(ink.readout, v.readoutX(ink.readout), v.readoutBase().toFloat(), Ink.white, a)
        else -> {
            val (g, size) = v.glyphOf(p) ?: return
            glyph(g, c, size.toFloat(), Ink.white, a)
        }
    }
}

fun DrawScope.volumePlate(hud: VolumeHud, v: VolumeParts, ink: VolumeInk, glow: Glow, now: Long, rec: DrawRecord? = null) {
    val d = v.drawn(hud, ink, now) ?: return
    volumePlate(v, ink, glow, d, rec)
}

fun DrawScope.volumePlate(v: VolumeParts, ink: VolumeInk, glow: Glow, d: VolumeDraw, rec: DrawRecord? = null) {
    drawRect(Ink.black, alpha = d.scrim)
    if (rec != null) rec.skip(SCRIM) { rec.box("volume.scrim", Mark.Bar, Rect(0f, 0f, size.width, size.height), d.scrim, Ink.black) }
    val plate = d.plate
    if (plate != null) {
        val m = Matrix().apply { setValues(plate.map { it.toFloat() }.toFloatArray()) }
        val nc = drawContext.canvas.nativeCanvas
        nc.save()
        nc.concat(m)
        for (p in VolumePart.entries) volumePart(v, ink, p, d.parts[p.ordinal].alpha)
        nc.restore()
    } else {
        for (p in VolumePart.entries) {
            val (f, a) = d.parts[p.ordinal]
            withTransform({ translate(f.x, f.y); scale(f.scale, f.scale, Offset.Zero) }) { volumePart(v, ink, p, a) }
        }
    }
    if (rec != null) for (p in VolumePart.entries) v.record(rec, p, ink, d)
    val ex = v.exitX(ink.exit)
    val eb = v.exitBase()
    val screen = Edges(0, 0, size.width.toInt(), size.height.toInt())
    if (d.exitGlow > 0f) {
        val texts = listOf(GlowText(ink.exit, pen64(ex), eb, v.rolePx(Roles.volExit)))
        pressGlow(glow, texts, screen, v.ppu, d.exitGlow)
        if (rec != null) glow.mask(this, texts, screen, v.ppu)?.let { m ->
            rec.box("volume.exit.glow", Mark.Glow, Rect(m.x.toFloat(), m.y.toFloat(), (m.x + m.image.width).toFloat(), (m.y + m.image.height).toFloat()), d.exitGlow)
        }
    }
    word(ink.exit, ex, eb.toFloat(), Ink.white, d.exit)
    rec?.word("volume.exit", ink.exit, ex, eb.toFloat(), d.exit)
    val left = d.left.toFloat()
    val right = v.lineRight().toFloat()
    val x = Tok.vol_textX * v.ppu
    val ab = ink.artistBase.toFloat()
    val tb = ink.titleBase.toFloat()
    val (artist, title) = d.lines
    val (oldArtist, oldTitle) = d.old
    clipRect(left, 0f, right, size.height) {
        if (d.cross) {
            word(ink.oldTitle, x + oldTitle.dx, tb, Ink.white, oldTitle.alpha)
            word(ink.oldArtist, x + oldArtist.dx, ab, Ink.white, oldArtist.alpha)
        }
        word(ink.title, x + title.dx, tb, Ink.white, title.alpha)
        word(ink.artist, x + artist.dx, ab, Ink.white, artist.alpha)
    }
    if (rec != null) rec.clip(left, 0f, right, size.height) {
        if (d.cross) {
            rec.word("volume.title.old", ink.oldTitle, x + oldTitle.dx, tb, oldTitle.alpha)
            rec.word("volume.artist.old", ink.oldArtist, x + oldArtist.dx, ab, oldArtist.alpha)
        }
        rec.word("volume.title", ink.title, x + title.dx, tb, title.alpha)
        rec.word("volume.artist", ink.artist, x + artist.dx, ab, artist.alpha)
    }
}

@Composable
fun VolumeLayer(hud: VolumeHud, s: Scale, modifier: Modifier = Modifier) {
    val rev = hud.revision
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    val v = remember(hud.board, hud.w, s.ppu) { VolumeParts(hud.board, s.ppu, hud.w) }
    val glow = remember { Glow() }
    val cur = hud.artist to hud.title
    val old = hud.oldArtist to hud.oldTitle
    val ink = remember(v, density, fonts, covers, cur, old, hud.percent, hud.seeking, hud.readout, hud.h) {
        m.volumeInk(hud, v, fonts, density, covers, old)
    }
    var frameAt by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(hud, rev) {
        while (true) {
            withFrameMillis {
                frameAt = it
                hud.tiltFrame(it)
            }
            if (!hud.visible(frameAt)) break
            if (!hud.moving(frameAt)) {
                val w = hud.wakeAt(frameAt) ?: break
                delay(w - frameAt)
            }
        }
    }
    val look = rememberLook(hud, v, ink, covered = false) { v.drawn(hud, ink, frameAt) }
    Canvas(modifier.pxSize(s.widthPx, s.heightPx)) {
        look.value?.let { volumePlate(v, ink, glow, it) }
    }
}
