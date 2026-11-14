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
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.min

private const val PASSES = 3
private const val SIDES = 2
private const val ALPHA_SHIFT = 24
private const val RGB = 0xFFFFFF

class GlowText(val word: Word, val x64: Int, val base: Int, val px: Int)

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

    fun glow(src: IntArray, w: Int, h: Int, grow: Int, reach: Int): IntArray {
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
        return a
    }
}

class Glow {
    var key: Int? by mutableStateOf(null)
        private set
    var at by mutableLongStateOf(0L)
        private set
    private var sigTexts: List<GlowText>? = null
    private var sigClip: Edges? = null
    private var sigPpu = 0f
    private var mask: GlowMask? = null

    fun down(key: Int, now: Long) {
        clear()
        this.key = key
        at = now
    }

    fun clear() {
        key = null
        sigTexts = null
        mask = null
    }

    fun off(): Boolean {
        val was = key != null
        clear()
        return was
    }

    fun moving(now: Long): Boolean = key != null && now - at < Tok.motion_pressGlow.toLong()

    fun progress(now: Long): Float = ((now - at).toFloat() / max(1, Tok.motion_pressGlow.toInt())).coerceIn(0f, 1f)

    internal fun mask(d: Density, texts: List<GlowText>, clip: Edges, ppu: Float): GlowMask? {
        if (texts !== sigTexts || clip !== sigClip || ppu != sigPpu) {
            mask = build(d, texts, clip, ppu)
            sigTexts = texts
            sigClip = clip
            sigPpu = ppu
        }
        return mask
    }

    private fun build(d: Density, texts: List<GlowText>, clip: Edges, ppu: Float): GlowMask? {
        val dil = px(Tok.pressGlowDilate, ppu)
        val g = px(Tok.pressGlow, ppu)
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

fun DrawScope.pressGlow(g: Glow, texts: List<GlowText>, clip: Edges, ppu: Float, p: Float) {
    if (texts.isEmpty()) return
    val m = g.mask(this, texts, clip, ppu) ?: return
    val pad = px(Tok.pressGlowDilate, ppu) + px(Tok.pressGlow, ppu)
    clipRect((clip.x0 - pad).toFloat(), clip.y0.toFloat(), clip.x1.toFloat(), clip.y1.toFloat()) {
        drawImage(m.image, Offset(m.x.toFloat(), m.y.toFloat()), alpha = Tok.pressGlowAlpha * Curves.cubicOut.at(p))
    }
}
