package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.Case
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px64
import kotlin.math.max
import kotlin.math.min

const val LYRIC_NONE = -2
const val LYRIC_TITLE = -1
private const val HALF = 2L
private const val A256 = 256
private val ONE = fxInt(1)

fun lyricAlpha(a: Long): Int = px64(a.coerceIn(0L, ONE), fxInt(A256))

fun lyricEdge(u: Long, ppu: Long): Int = -Math.floorDiv(-fxMul(u, ppu), ONE).toInt()

private fun lerp(a: Long, b: Long, t: Long): Long = a + fxMul(b - a, t)

private fun sized(r: Role, size: Float) = Role(size, r.weight, r.case, r.alpha, r.tracking)

private fun mixed(r: Role) = Role(r.size, r.weight, Case.Mixed, r.alpha, r.tracking)

class LyricTable(
    val big: Role,
    val next: Role,
    val plain: Role,
    val cp: Long,
    val np: Long,
    val ap: Long,
    val top: Long,
    val bottom: Long,
) {
    fun role(big: Boolean): Role = if (big) this.big else next

    fun end(pitch: Long): Long = top + (bottom - top) / pitch * pitch

    fun baseline(pitch: Long, size: Float, slot: Long, ppu: Long): Int =
        px64(slot + (pitch + fxMul(fx(Tok.capHeight), fx(size))) / HALF, ppu)

    companion object {
        fun of(s: Scale): LyricTable {
            val b = s.board
            val plain = mixed(Roles.npAlbum)
            val top = fx(Tok.npTextTop)
            if (!s.large) {
                return LyricTable(
                    Roles.npTitle, Roles.npNext, plain, fx(Tok.npLyricPitch), fx(Tok.npNextPitch),
                    fx(Tok.npAlbumBaseline) - top, top, fx(b.npLyricBottom),
                )
            }
            return LyricTable(
                sized(Roles.npTitle, Tok.large_type_npTitle_size), sized(Roles.npNext, Tok.large_type_npNext_size),
                sized(plain, Tok.large_type_npAlbum_size), fx(b.large_npLyricPitch), fx(b.large_npNextPitch),
                fx(b.large_npAlbumPitch), top, fx(b.npLyricBottom),
            )
        }
    }
}

interface LyricInk {
    fun rows(line: Int, big: Boolean): Int

    fun top(line: Int, big: Boolean): Int

    fun bottom(line: Int, big: Boolean, row: Int): Int
}

data class LyricBlock(val line: Int, val big: Boolean, val top: Long, val rows: Int, val pitch: Long, val alpha: Long) {
    val h: Long get() = rows * pitch
}

class LyricBlocks(val list: List<LyricBlock>, val vis: Long)

data class LyricForm(val block: LyricBlock, val alpha: Long)

data class LyricDraw(val line: Int, val top: Long, val h: Long, val y0: Int, val y1: Int, val old: LyricForm?, val new: LyricForm?, val change: Boolean)

class LyricColumn(val draws: List<LyricDraw>, val bottom: Long)

private class Span(val fa: LyricBlock?, val fb: LyricBlock?, var top: Long, var h: Long, var held: Boolean = false)

object LyricRows {
    fun blocks(t: LyricTable, line: Int, lines: List<String>?, next: String, ink: LyricInk): LyricBlocks {
        val texts = if (line == LYRIC_NONE) null else lines
        val none = texts == null
        var curN = if (none || line >= 0) max(ink.rows(if (none) LYRIC_TITLE else line, true), 1) else 0
        var i = 0
        fun nextIndex(): Int {
            if (texts == null) return if (i++ == 0 && next.isNotEmpty()) LYRIC_TITLE else LYRIC_NONE
            while (i < texts.size) {
                val k = i++
                if (texts[k].isNotEmpty()) return k
            }
            return LYRIC_NONE
        }
        val room = t.bottom - t.top
        curN = min(curN.toLong(), room / t.cp).toInt()
        val used = curN * t.cp
        val prevN = if (!none && line >= 1) ink.rows(line - 1, false) else 0
        val after = if (none) 0 else line + 1
        i = after
        val more = nextIndex() != LYRIC_NONE
        val out = ArrayList<LyricBlock>()
        var slot = t.top
        if (prevN > 0 && used + prevN * t.np + (if (more) t.np else 0L) <= room) {
            out += LyricBlock(line - 1, false, slot, prevN, t.np, fx(Tok.alpha_low))
            slot += prevN * t.np
        }
        if (curN > 0) {
            out += LyricBlock(if (none) LYRIC_TITLE else line, true, slot, curN, t.cp, fx(t.big.alpha))
            slot += curN * t.cp
        }
        i = after
        while (slot + t.np <= t.bottom) {
            val k = nextIndex()
            if (k == LYRIC_NONE) break
            val b = LyricBlock(k, false, slot, ink.rows(k, false), t.np, fx(t.next.alpha))
            out += b
            val fit = min(b.rows.toLong(), (t.bottom - slot) / t.np).toInt()
            if (fit < b.rows) return LyricBlocks(out, slot + fit * t.np)
            slot += b.h
        }
        return LyricBlocks(out, slot)
    }

    fun pair(a: List<LyricBlock>, b: List<LyricBlock>, t: LyricTable, ink: LyricInk): List<LyricBlock> {
        if (a.isEmpty()) return a
        val first = a.first().line
        val last = a.last().line
        val out = ArrayList(a)
        fun off(line: Int) = LyricBlock(line, false, 0L, ink.rows(line, false), t.np, 0L)
        for (x in b.asReversed()) {
            if (x.line < first && out.none { it.line == x.line }) {
                val v = off(x.line)
                out.add(0, v.copy(top = out.first().top - v.h))
            }
        }
        for (x in b) {
            if (x.line > last && out.none { it.line == x.line }) out += off(x.line).copy(top = out.last().top + out.last().h)
        }
        return out
    }

    fun column(t: LyricTable, from: Int, to: Int, p: Long, ppu: Long, lines: List<String>?, next: String, ink: LyricInk): LyricColumn {
        val bb = blocks(t, to, lines, next, ink)
        var a = bb.list
        var b = bb.list
        var visA = bb.vis
        if (from != to) {
            val ab = blocks(t, from, lines, next, ink)
            visA = ab.vis
            a = pair(ab.list, b, t, ink)
            b = pair(b, a, t, ink)
        }
        val half = ONE / HALF
        val ds = ArrayList<Span>()
        fun drawn(line: Int) = ds.first { it.fa?.line == line || it.fb?.line == line }
        if (from == to) {
            for (x in b) ds += Span(x, x, x.top, x.h)
        } else {
            for (x in a) b.firstOrNull { it.line == x.line }?.let { y -> ds += Span(x, y, lerp(x.top, y.top, p), lerp(x.h, y.h, p)) }
            val enter = ds.lastOrNull { it.fb!!.line == to }
            val nx = ds.firstOrNull { it.fb!!.line > to }
            if (enter != null) {
                val held = nx != null && nx.fb!!.top > nx.fa!!.top
                if (held) {
                    for (d in ds) {
                        if (d.fb!!.line <= to) continue
                        val k = if (p < half) d.fa!! else d.fb
                        d.held = true
                        d.top = k.top
                        d.h = k.h
                    }
                }
                val lim = when {
                    held -> if (p < half) nx.fa.top else nx.fb.top
                    nx != null -> lerp(nx.fa!!.top, nx.fb!!.top, p)
                    else -> t.bottom
                }
                enter.h = min(enter.top + (if (p < half) enter.fa!! else enter.fb!!).h, lim) - enter.top
            }
            for (i in a.indices.reversed()) {
                val x = a[i]
                if (b.any { it.line == x.line }) continue
                val h = fxMul(x.h, ONE - p)
                val top = if (i + 1 < a.size) drawn(a[i + 1].line).top - h else x.top
                ds += Span(x, null, top, h)
            }
            for (i in b.indices) {
                val y = b[i]
                if (a.any { it.line == y.line }) continue
                val top = if (i > 0) {
                    val r = drawn(b[i - 1].line)
                    r.top + r.h + (y.top - (b[i - 1].top + b[i - 1].h))
                } else {
                    y.top
                }
                ds += Span(null, y, top, fxMul(y.h, p))
            }
        }
        val bottom = max(visA, bb.vis)
        val colY0 = lyricEdge(t.top, ppu)
        val colY1 = lyricEdge(bottom, ppu)
        val roomY1 = lyricEdge(t.bottom, ppu)
        fun base(k: LyricBlock, slot: Long) = t.baseline(k.pitch, t.role(k.big).size, slot, ppu)
        fun rows(k: LyricBlock) = if (k.big) min(ink.rows(k.line, true), k.rows) else ink.rows(k.line, false)
        val draws = ds.map { d ->
            val fa = d.fa
            val fb = d.fb
            val change = fa != null && fb != null && fa.big != fb.big
            val swap = change || d.held
            var ao = 0L
            var an = 0L
            var fo: LyricBlock? = null
            var fn: LyricBlock? = null
            when {
                fa != null && fb != null && !swap -> {
                    an = lerp(fa.alpha, fb.alpha, p)
                    fn = fb
                }
                fa != null && fb != null -> {
                    ao = if (p < half) fxMul(fa.alpha, ONE - HALF * p) else 0L
                    an = if (p < half) 0L else fxMul(fb.alpha, HALF * p - ONE)
                    fo = fa
                    fn = fb
                }
                fa != null -> {
                    ao = fxMul(fa.alpha, ONE - p)
                    fo = fa
                }
                else -> {
                    an = fxMul(fb!!.alpha, p)
                    fn = fb
                }
            }
            var y0 = max(colY0, lyricEdge(d.top, ppu))
            var y1 = max(y0, min(colY1, lyricEdge(d.top + d.h, ppu)))
            fun grow(k: LyricBlock) {
                val n = rows(k)
                if (n == 0) return
                val e = lyricEdge(d.top, ppu)
                if (e >= colY0 && e < colY1) y0 = max(colY0, min(y0, base(k, d.top) - ink.top(k.line, k.big)))
                if (d.h == k.h && lyricEdge(d.top + d.h, ppu) <= colY1 && (fa == null || fa.alpha > 0)) {
                    val tail = base(k, d.top + (n - 1) * k.pitch) + ink.bottom(k.line, k.big, n - 1)
                    y1 = max(y1, min(roomY1, tail))
                }
            }
            if (fo != null && lyricAlpha(ao) > 0) grow(fo)
            if (fn != null && lyricAlpha(an) > 0) grow(fn)
            LyricDraw((fb ?: fa!!).line, d.top, d.h, y0, y1, fo?.let { LyricForm(it, ao) }, fn?.let { LyricForm(it, an) }, change)
        }
        return LyricColumn(draws, bottom)
    }
}
