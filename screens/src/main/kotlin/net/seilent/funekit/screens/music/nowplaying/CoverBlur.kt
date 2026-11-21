package net.seilent.funekit.screens.music.nowplaying

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.fxInt
import net.seilent.funekit.gen.Tok
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val RED = 16
private const val GREEN = 8
private const val BYTE = 0xFF
private const val RGB = 3
private const val W256 = 256
private const val W_HALF = 128
private const val W_MASK = 255
private const val ROUND = 32768
private const val MIX = 16
private const val PASSES = 3
private const val SIGMAS = 4
private const val TWO = 2
private const val ALPHA = 24
private const val HALF_BYTE = 127

object CoverBlur {
    class Grid(val w: Int, val h: Int, val px: IntArray)

    private val one = fxInt(1)

    fun sigma(s: Scale): Double = Tok.backdrop_coverSigma.toDouble() * s.stdPpu * Tok.backdrop_coverGrid

    fun width(q: Double): Int {
        val lo = max(1, floor((q - 1) / TWO).toInt() * TWO + 1)
        val hi = lo + TWO
        return if (q - lo <= hi - q) lo else hi
    }

    fun radius(s: Double): Int = width(sqrt(SIGMAS * s * s + 1)) / TWO

    fun crop(w: Int, h: Int, bw: Int, bh: Int): IntArray =
        if (w.toLong() * bh >= h.toLong() * bw) intArrayOf((h.toLong() * bw / bh).toInt(), h) else intArrayOf(w, (w.toLong() * bh / bw).toInt())

    fun fits(w: Int, h: Int, gw: Int, gh: Int): Boolean {
        val c = crop(w, h, gw, gh)
        return c[0] >= gw && c[1] >= gh
    }

    fun grid(bw: Int, bh: Int): IntArray {
        val g = fx(Tok.backdrop_coverGrid)
        return intArrayOf((bw * g / one).toInt(), (bh * g / one).toInt())
    }

    private fun pack(r: Int, g: Int, b: Int): Int = r shl RED or (g shl GREEN) or b

    fun area(src: IntArray, w: Int, h: Int, ow: Int, oh: Int): IntArray {
        val c = crop(w, h, ow, oh)
        val cw = c[0]
        val ch = c[1]
        val x0 = (w - cw) / TWO
        val y0 = (h - ch) / TWO
        val cx = IntArray(ow)
        for (x in 0 until cw) cx[(x.toLong() * ow / cw).toInt()]++
        val out = IntArray(ow * oh)
        val sum = IntArray(ow * RGB)
        var cur = -1
        var n = 0
        fun flush() {
            for (x in 0 until ow) {
                val k = cx[x] * n
                val at = x * RGB
                out[cur * ow + x] = pack((sum[at] + k / TWO) / k, (sum[at + 1] + k / TWO) / k, (sum[at + TWO] + k / TWO) / k)
            }
        }
        for (y in y0 until y0 + ch) {
            val by = ((y - y0).toLong() * oh / ch).toInt()
            if (by != cur) {
                if (cur >= 0) flush()
                cur = by
                n = 0
                sum.fill(0)
            }
            n++
            val row = y * w + x0
            for (x in 0 until cw) {
                val v = src[row + x]
                val d = (x.toLong() * ow / cw).toInt() * RGB
                sum[d] += v shr RED and BYTE
                sum[d + 1] += v shr GREEN and BYTE
                sum[d + TWO] += v and BYTE
            }
        }
        if (cur >= 0) flush()
        return out
    }

    private fun axis(src: Int, dst: Int, i: Int, out: IntArray, at: Int) {
        val pos = (((TWO.toLong() * i + 1) * src * W256) / (TWO.toLong() * dst) - W_HALF).coerceIn(0L, (src - 1).toLong() * W256)
        val a = (pos / W256).toInt()
        out[at] = a
        out[at + 1] = min(a + 1, src - 1)
        out[at + TWO] = (pos and W_MASK.toLong()).toInt()
    }

    private fun mix(a: Int, b: Int, c: Int, d: Int, s: Int, wx: Int, wy: Int): Int {
        val top = (a shr s and BYTE) * (W256 - wx) + (b shr s and BYTE) * wx
        val bot = (c shr s and BYTE) * (W256 - wx) + (d shr s and BYTE) * wx
        return (top * (W256 - wy) + bot * wy + ROUND) shr MIX
    }

    fun bilinear(src: IntArray, stride: Int, x0: Int, y0: Int, sw: Int, sh: Int, ow: Int, oh: Int): IntArray {
        val xs = IntArray(ow * RGB)
        for (x in 0 until ow) axis(sw, ow, x, xs, x * RGB)
        val ys = IntArray(RGB)
        val out = IntArray(ow * oh)
        for (y in 0 until oh) {
            axis(sh, oh, y, ys, 0)
            val r0 = (y0 + ys[0]) * stride + x0
            val r1 = (y0 + ys[1]) * stride + x0
            val wy = ys[TWO]
            for (x in 0 until ow) {
                val at = x * RGB
                val wx = xs[at + TWO]
                val a = src[r0 + xs[at]]
                val b = src[r0 + xs[at + 1]]
                val c = src[r1 + xs[at]]
                val d = src[r1 + xs[at + 1]]
                out[y * ow + x] = pack(mix(a, b, c, d, RED, wx, wy), mix(a, b, c, d, GREEN, wx, wy), mix(a, b, c, d, 0, wx, wy))
            }
        }
        return out
    }

    fun boxPass(p: IntArray, off: Int, n: Int, step: Int, r: Int, tmp: IntArray) {
        for (i in 0 until n) tmp[i] = p[off + i * step]
        val w = TWO * r + 1
        var s0 = 0
        var s1 = 0
        var s2 = 0
        for (i in -r..r) {
            val v = tmp[i.coerceIn(0, n - 1)]
            s0 += v shr RED and BYTE
            s1 += v shr GREEN and BYTE
            s2 += v and BYTE
        }
        for (i in 0 until n) {
            p[off + i * step] = pack((s0 + w / TWO) / w, (s1 + w / TWO) / w, (s2 + w / TWO) / w)
            val a = tmp[(i + r + 1).coerceIn(0, n - 1)]
            val b = tmp[(i - r).coerceIn(0, n - 1)]
            s0 += (a shr RED and BYTE) - (b shr RED and BYTE)
            s1 += (a shr GREEN and BYTE) - (b shr GREEN and BYTE)
            s2 += (a and BYTE) - (b and BYTE)
        }
    }

    fun blur(px: IntArray, w: Int, h: Int, r: Int) {
        if (r <= 0) return
        val tmp = IntArray(max(w, h))
        repeat(PASSES) {
            for (y in 0 until h) boxPass(px, y * w, w, 1, r, tmp)
            for (x in 0 until w) boxPass(px, x, h, w, r, tmp)
        }
    }

    fun cover(src: IntArray, w: Int, h: Int, bw: Int, bh: Int, s: Scale): Grid? {
        if (w <= 0 || h <= 0 || bw <= 0 || bh <= 0) return null
        val g = grid(bw, bh)
        val gw = g[0]
        val gh = g[1]
        if (gw < 1 || gh < 1) return null
        val px = if (fits(w, h, gw, gh)) {
            area(src, w, h, gw, gh)
        } else {
            val c = crop(w, h, bw, bh)
            if (c[0] <= 0 || c[1] <= 0) return null
            bilinear(src, w, (w - c[0]) / TWO, (h - c[1]) / TWO, c[0], c[1], gw, gh)
        }
        blur(px, gw, gh, radius(sigma(s)))
        return Grid(gw, gh, px)
    }

    fun overTile(argb: Int): Int {
        val a = argb ushr ALPHA and BYTE
        val bg = Tok.color_tile.toInt()
        fun ch(shift: Int): Int = ((argb shr shift and BYTE) * a + (bg shr shift and BYTE) * (BYTE - a) + HALF_BYTE) / BYTE
        return pack(ch(RED), ch(GREEN), ch(0))
    }

    fun image(image: ImageBitmap, bw: Int, bh: Int, s: Scale): ImageBitmap? {
        val src = IntArray(image.width * image.height)
        image.readPixels(src)
        for (i in src.indices) src[i] = overTile(src[i])
        val g = cover(src, image.width, image.height, bw, bh, s) ?: return null
        for (i in g.px.indices) g.px[i] = g.px[i] or (BYTE shl ALPHA)
        return Bitmap.createBitmap(g.px, g.w, g.h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
