package net.seilent.funekit.kit

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.min

private const val PASSES = 3
private const val SIDES = 2
private const val ALPHA_SHIFT = 24
private const val RGB = 0xFFFFFF
private const val SQUARE = 4
private const val FADE_TAIL = 2

data class GlowText(val word: Word, val x64: Int, val base: Int, val px: Int)

class GlowMask(val image: ImageBitmap, val x: Int, val y: Int)

object GlowMath {
    fun dilate(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (w <= 0 || h <= 0 || r <= 0) return src.copyOf()
        val b = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (i in max(0, x - r)..min(w - 1, x + r)) m = max(m, src[y * w + i])
            b[y * w + x] = m
        }
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (i in max(0, y - r)..min(h - 1, y + r)) m = max(m, b[i * w + x])
            out[y * w + x] = m
        }
        return out
    }

    private fun pass(src: IntArray, to: IntArray, r: Int, len: Int, lines: Int, step: Int, line: Int) {
        val n = SIDES * r + 1
        for (l in 0 until lines) {
            val o = l * line
            var sum = 0
            for (i in 0 until min(r, len)) sum += src[o + i * step]
            for (i in 0 until len) {
                if (i + r < len) sum += src[o + (i + r) * step]
                if (i - r - 1 >= 0) sum -= src[o + (i - r - 1) * step]
                to[o + i * step] = (sum + n / SIDES) / n
            }
        }
    }

    fun glow(src: IntArray, w: Int, h: Int, grow: Int, reach: Int, hole: Int = 0): IntArray {
        val gw = w + SIDES * grow
        val gh = h + SIDES * grow
        val a = IntArray(max(0, gw) * max(0, gh))
        if (w <= 0 || grow <= 0) return a
        for (y in 0 until h) for (x in 0 until w) a[(y + grow) * gw + x + grow] = src[y * w + x]
        val b = IntArray(a.size)
        for (k in 0 until PASSES) {
            val r = if (reach > 0) (reach + PASSES - 1 - k) / PASSES else max(1, grow / PASSES)
            if (r <= 0) continue
            pass(a, b, r, gw, gh, 1, gw)
            pass(b, a, r, gh, gw, gw, 1)
        }
        if (hole > 0) {
            val cut = SQUARE.toLong() * hole * hole
            for (y in 0 until gh) for (x in 0 until gw) {
                val dx = (SIDES * x + 1 - gw).toLong()
                val dy = (SIDES * y + 1 - gh).toLong()
                if (dx * dx + dy * dy < cut) a[y * gw + x] = 0
            }
        }
        return a
    }
}

class GlowPair(glow: Glow = Glow(), back: Glow = Glow()) {
    var glow by mutableStateOf(glow)
        private set
    var back by mutableStateOf(back)
        private set

    fun arm(row: Int, now: Long) {
        if (back.key == row) back.clear()
        val k = glow.key
        if (k != null && k != row) {
            val g = glow
            glow = back
            back = g
        }
        glow.down(row, now)
    }

    fun off(): Boolean {
        val a = glow.off()
        val b = back.off()
        return a || b
    }
}

class Glow(dilate: Float = Tok.pressGlowDilate, blur: Float = Tok.pressGlow, alpha: Float = Tok.pressGlowAlpha) {
    var dilate = dilate
        private set
    var blur = blur
        private set
    var alpha = alpha
        private set
    var key: Int? by mutableStateOf(null)
        private set
    var at by mutableLongStateOf(0L)
        private set
    private var lv = -1f
    private var fadeAt = -1L
    private var until = -1L
    private var ended = false
    private var sigTexts: List<GlowText>? = null
    private var sigClip: Edges? = null
    private var sigPpu = 0f
    private var mask: GlowMask? = null
    internal var builds = 0
        private set

    fun down(key: Int, now: Long) {
        clear()
        this.key = key
        at = now
    }

    fun clear() {
        key = null
        sigTexts = null
        mask = null
        lv = -1f
        fadeAt = -1L
        until = -1L
        ended = false
    }

    fun off(): Boolean {
        val was = key != null
        clear()
        return was
    }

    fun setLevel(l: Float) {
        lv = l
    }

    fun fade(now: Long) {
        if (key != null && fadeAt < 0 && until < 0) fadeAt = now
    }

    fun idleUp(now: Long) {
        if (key != null && fadeAt < 0 && until < 0) until = now + Tok.motion_touchReturnDelay.toLong()
    }

    val fading: Boolean get() = fadeAt >= 0

    fun follow(key: Int, lit: Float, held: Boolean): Glow? {
        if (!held && lit == 0f) {
            if (this.key == key) clear()
            return null
        }
        if (this.key != key) down(key, 0L)
        lv = lit
        return this
    }

    private fun back(): Long = Tok.motion_press.toLong()

    private fun ended(now: Long): Boolean = (fadeAt >= 0 && now >= fadeAt + back()) || (until >= 0 && now >= until)

    fun moving(now: Long): Boolean {
        if (key == null) return false
        val rising = now - at < Tok.motion_pressGlow.toLong()
        if (fadeAt >= 0) return now < fadeAt + back() || (!ended(now) && now < fadeAt + FADE_TAIL * back())
        if (until >= 0) return rising || (now >= until && !ended(now) && now < until + back())
        return rising
    }

    fun live(now: Long): Boolean = moving(now) || (key != null && until > now)

    fun due(now: Long): Long? = until.takeIf { key != null && it > now }

    private fun rise(w: Long): Float = Curves.cubicOut.at(((w - at).toFloat() / max(1, Tok.motion_pressGlow.toInt())).coerceIn(0f, 1f))

    fun level(now: Long): Float {
        ended = ended(now)
        if (key == null || ended) return 0f
        if (lv >= 0f) return lv
        if (fadeAt >= 0) return lerp(rise(fadeAt), 0f, ((now - fadeAt).toFloat() / back()).coerceIn(0f, 1f))
        return rise(now)
    }

    internal fun mask(d: Density, texts: List<GlowText>, clip: Edges, ppu: Float): GlowMask? {
        val sc = sigClip
        if (sigTexts == null || texts != sigTexts || sc == null || !sc.same(clip) || ppu != sigPpu) {
            mask = build(d, texts, clip, ppu)
            builds++
            sigTexts = texts
            sigClip = clip
            sigPpu = ppu
        }
        return mask
    }

    fun tune(dilate: Float, blur: Float, alpha: Float) {
        if (dilate != this.dilate || blur != this.blur) sigTexts = null
        this.dilate = dilate
        this.blur = blur
        this.alpha = alpha
    }

    fun prepare(d: Density, texts: List<GlowText>, clip: Edges, ppu: Float) {
        if (texts.isNotEmpty()) mask(d, texts, clip, ppu)
    }

    private fun build(d: Density, texts: List<GlowText>, clip: Edges, ppu: Float): GlowMask? {
        if (texts.isEmpty()) return null
        val dil = px(dilate, ppu)
        val g = px(blur, ppu)
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        for (t in texts) {
            x0 = min(x0, penFloor(t.x64))
            y0 = min(y0, t.base - Metrics.ascent(t.px))
            x1 = max(x1, penCeil(t.x64 + pen64(t.word.width)) + 1)
            y1 = max(y1, t.base + Metrics.descent(t.px))
        }
        x0 = max(x0 - 1, clip.x0) - dil
        y0 = max(y0 - 1, clip.y0) - dil
        x1 = min(x1 + 1, clip.x1) + dil
        y1 = min(y1 + 1, clip.y1) + dil
        val w = x1 - x0
        val h = y1 - y0
        if (w <= SIDES * dil || h <= SIDES * dil) return null
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(d, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            clipRect((clip.x0 - x0).toFloat(), (clip.y0 - y0).toFloat(), (clip.x1 - x0).toFloat(), (clip.y1 - y0).toFloat()) {
                for (t in texts) word(t.word, penPx(t.x64) - x0, (t.base - y0).toFloat(), Ink.white, 1f)
            }
        }
        val pixels = IntArray(w * h)
        img.readPixels(pixels)
        val cov = IntArray(w * h) { pixels[it] ushr ALPHA_SHIFT }
        val out = GlowMath.glow(GlowMath.dilate(cov, w, h, dil), w, h, g, g)
        val gw = w + SIDES * g
        val gh = h + SIDES * g
        val argb = IntArray(out.size) { (out[it] shl ALPHA_SHIFT) or RGB }
        return GlowMask(Bitmap.createBitmap(argb, gw, gh, Bitmap.Config.ARGB_8888).asImageBitmap(), x0 - g, y0 - g)
    }
}

fun DrawScope.pressGlow(g: Glow, texts: List<GlowText>, clip: Edges, ppu: Float, level: Float) {
    if (texts.isEmpty() || level <= 0f) return
    val m = g.mask(this, texts, clip, ppu) ?: return
    val pad = px(g.dilate, ppu) + px(g.blur, ppu)
    clipRect((clip.x0 - pad).toFloat(), clip.y0.toFloat(), clip.x1.toFloat(), clip.y1.toFloat()) {
        drawImage(m.image, Offset(m.x.toFloat(), m.y.toFloat()), alpha = min(1f, g.alpha * level))
    }
}

object GlowCache {
    private val map = HashMap<String, GlowMask>()
    internal var builds = 0
        private set

    private fun raster(w: Int, h: Int, paint: DrawScope.() -> Unit): IntArray {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), paint)
        val pixels = IntArray(w * h)
        img.readPixels(pixels)
        return IntArray(w * h) { pixels[it] ushr ALPHA_SHIFT }
    }

    private fun image(cov: IntArray, w: Int, h: Int): ImageBitmap {
        val argb = IntArray(cov.size) { (cov[it] shl ALPHA_SHIFT) or RGB }
        return Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun keep(k: String, make: () -> GlowMask?): GlowMask? {
        map[k]?.let { return it }
        val m = make() ?: return null
        builds++
        map[k] = m
        return m
    }

    fun halo(d: Int, stroke: Int, grow: Int, spread: Int = 0): GlowMask? = keep("halo:$d:$stroke:$grow:$spread") {
        val n = d + SIDES * spread
        if (n <= 0 || grow <= 0) return@keep null
        val c = n / SIDES.toFloat()
        val src = raster(n, n) { ring(Offset(c, c), n.toFloat(), (stroke + SIDES * spread).toFloat(), Ink.white, 1f) }
        val out = GlowMath.glow(src, n, n, grow, 0, d / SIDES)
        val w = n + SIDES * grow
        GlowMask(image(out, w, w), -(w shr 1), -(w shr 1))
    }

    fun glyph(g: Glyph, size: Int, dilate: Int, grow: Int): GlowMask? = keep("glow:${g.name}:$size:$dilate:$grow") {
        if (size <= 0) return@keep null
        val p = size + SIDES * dilate
        val c = p / SIDES.toFloat()
        val src = raster(p, p) { glyph(g, Offset(c, c), size.toFloat(), Ink.white, 1f) }
        val out = GlowMath.glow(GlowMath.dilate(src, p, p, dilate), p, p, grow, 0)
        val w = p + SIDES * grow
        GlowMask(image(out, w, w), -(w shr 1), -(w shr 1))
    }

    fun frame(w: Int, h: Int, s: Scale): GlowMask? = keep("frame:$w:$h:${s.stdPpu}") {
        if (w <= 0 || h <= 0) return@keep null
        val g = px(Tok.pressGlow, s.stdPpu)
        val src = raster(w, h) { Rows.pressFrame(Edges(0, 0, w, h), s).forEach { drawRect(Ink.white, Offset(it.x0.toFloat(), it.y0.toFloat()), Size(it.w.toFloat(), it.h.toFloat())) } }
        val out = GlowMath.glow(src, w, h, g, 0)
        GlowMask(image(out, w + SIDES * g, h + SIDES * g), -g, -g)
    }
}

private fun DrawScope.centered(m: GlowMask, cx: Int, cy: Int, alpha: Float) {
    if (alpha > 0f) drawImage(m.image, Offset((cx + m.x).toFloat(), (cy + m.y).toFloat()), alpha = min(1f, alpha))
}

fun ringHalo(d: Int): Int = max(1, ((fx(Tok.vol_glowRing) * d + fx(Tok.vol_ring) / SIDES) / fx(Tok.vol_ring)).toInt())

fun DrawScope.ringPress(d: Int, stroke: Int, cx: Int, cy: Int, lit: Float, a: Float = 1f, halo: Int = ringHalo(d), spread: Int = 0, haloA: Float = 1f) {
    if (lit <= 0f || d <= 0) return
    GlowCache.halo(d, stroke, halo, spread)?.let { centered(it, cx, cy, lit * haloA * a) }
    val inner = d - SIDES * stroke
    if (inner > 0) ring(Offset(cx.toFloat(), cy.toFloat()), inner.toFloat(), (inner / SIDES).toFloat(), Ink.white, lit * Tok.vol_discAlpha * a)
}

fun DrawScope.glyphPress(g: Glyph, size: Int, cx: Int, cy: Int, lit: Float, ppu: Float, a: Float = 1f) {
    if (lit <= 0f || size <= 0) return
    GlowCache.glyph(g, size, px(Tok.pressGlowDilate, ppu), px(Tok.pressGlow, ppu))?.let { centered(it, cx, cy, lit * Tok.pressGlowAlpha * a) }
}

fun DrawScope.pressFrame(t: Edges, s: Scale, lit: Float, ringA: Float) {
    if (t.w > 0 && t.h > 0 && lit > 0f) {
        GlowCache.frame(t.w, t.h, s)?.let { m ->
            clipRect(t.x0.toFloat(), t.y0.toFloat(), t.x1.toFloat(), t.y1.toFloat()) {
                drawImage(m.image, Offset((t.x0 + m.x).toFloat(), (t.y0 + m.y).toFloat()), alpha = min(1f, Tok.pressGlowAlpha * lit))
            }
        }
    }
    if (ringA > 0f) Rows.pressFrame(t, s).forEach { drawRect(Ink.white, Offset(it.x0.toFloat(), it.y0.toFloat()), Size(it.w.toFloat(), it.h.toFloat()), ringA) }
}
