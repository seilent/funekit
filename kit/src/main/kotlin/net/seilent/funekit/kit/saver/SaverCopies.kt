package net.seilent.funekit.kit.saver

import kotlin.math.max
import kotlin.math.min

private const val TEN_MIN = 600000L
private const val HOUR = 3600000L
private const val SUB_MAX = 63
private const val TWO = 2

internal fun SaverScenes.has(i: Int): Boolean = i >= 0 && i < lanes.size

internal fun sgn(l: Lane): Int = if (l.spec.speed >= 0) 1 else -1

internal fun SaverScenes.growOf(l: Lane, p: Long, len: Long) {
    val sp = l.spec
    l.grow = 0
    if (!l.t.timed || p < 0) return
    val st = l.t.st.copy(weight = if (sp.src == Src.Time) sp.w else sp.w2)
    val cell64 = glyphs.width64(st, "0")
    val colon64 = glyphs.width64(st, ":")
    val q = p + len
    var grow64 = if (p < TEN_MIN && q >= TEN_MIN) cell64 else 0
    grow64 += if (p < HOUR && q >= HOUR) cell64 + colon64 else 0
    l.grow = (grow64 + SUB_MAX) shr SUB_BITS
}

internal fun SaverScenes.regrow() {
    val p = pos?.invoke() ?: -1L
    for (l in lanes) growOf(l, p, sceneEnd() - sceneAt)
}

internal fun SaverScenes.boundary(l: Lane, g: List<G>, cut: Int, at: Int, word: Boolean): Boolean {
    if (at <= 0 || at >= g.size) return false
    if (at == cut) return true
    val src = if (at > cut) l.spec.src2 else l.spec.src
    if (src == Src.Time) return false
    val s = utf8(srcText(l, if (at > cut) 1 else 0, ""))
    val a = g[at - 1]
    val b = g[at]
    if (a.byte == b.byte) return false
    val ca = cpAt(s, a.byte)
    val cb = cpAt(s, b.byte)
    if (word) return blankCp(ca) || blankCp(cb) || (cjkCp(ca) && cjkCp(cb))
    return !markCp(cb) && ca != ZWJ
}

internal fun SaverScenes.laneCopyOff(l: Lane, c: Int): Long =
    if (l.brkCopy != 0 && c >= l.brkCopy) l.brkStart + (c - l.brkCopy).toLong() * l.period else c.toLong() * l.period

internal fun SaverScenes.laneCopyGlyphs(l: Lane, c: Int, v: List<G>, out: IntArray) {
    glyphsOf(l, c, v, out)
    if (l.brkCopy == 0 || c != l.brkCopy - 1 || v !== l.t.g) return
    val keep = min(v.size, max(l.brkKeep, 0))
    if (l.spec.speed >= 0) out[0] = max(out[0], v.size - keep) else out[1] = min(out[1], keep)
    out[1] = max(out[0], out[1])
}

internal fun SaverScenes.laneCopyRange(l: Lane, t: Long, c: IntArray) {
    c[0] = 0
    c[1] = 0
    if (l.period == 0) return
    val pu = along(l, t)
    val per = l.period.toLong() * ONE
    val fwd = l.spec.speed >= 0
    val lo = if (fwd) pu + l.t.ink0.toLong() * ONE - l.umax else l.umin - pu - l.t.ink1.toLong() * ONE
    val hi = if (fwd) pu + l.t.ink1.toLong() * ONE - l.umin else l.umax - pu - l.t.ink0.toLong() * ONE
    fun span(d: Long, out: IntArray) {
        out[0] = max(0L, (lo - d) / per).toInt()
        out[1] = max(out[0].toLong(), min((hi - d) / per + 1, out[0].toLong() + COPIES)).toInt()
    }
    span(0, c)
    val k = l.brkCopy
    val add = brkAdd(l)
    if (k != 0 && add != 0L && (c[1] >= k || add < 0)) {
        val s = IntArray(TWO)
        span(add * ONE, s)
        s[0] = max(s[0], k)
        c[1] = min(c[1], k - 1)
        if (c[0] > c[1] && s[0] > s[1]) {
            c[0] = k - 1
            c[1] = k
        } else if (s[0] <= s[1]) {
            c[0] = if (c[0] <= c[1]) c[0] else s[0]
            c[1] = s[1]
        }
    }
    if (l.count != 0) c[1] = min(c[1], l.count - 1)
}

internal fun SaverScenes.keptLetter(l: Lane, g0In: Int, g1In: Int): Boolean {
    val g0 = max(g0In, 0)
    val g1 = min(g1In, l.t.g.size)
    val s = arrayOf(ByteArray(0), ByteArray(0))
    for (p in 0 until TWO) if (g0 < g1 && (if (p != 0) g1 > l.t.cut else g0 < l.t.cut)) s[p] = utf8(srcText(l, p, l.time))
    for (k in g0 until g1) if (letterCp(cpAt(s[if (k >= l.t.cut) 1 else 0], l.t.g[k].byte))) return true
    return false
}

internal fun SaverScenes.breakEdge(l: Lane, copy: Int, keep: Int): Long {
    val n = l.t.g.size
    val fwd = l.spec.speed >= 0
    val off = (if (fwd) -1 else 1) * laneCopyOff(l, copy - 1)
    var e = if (fwd) Long.MAX_VALUE else Long.MIN_VALUE
    val x = IntArray(TWO)
    for (k in (if (fwd) n - keep else 0) until (if (fwd) n else keep)) {
        glyphSpan(l, k, x)
        if (!l.t.g[k].box.empty) e = if (fwd) min(e, x[0] + off) else max(e, x[1] + off)
    }
    return e
}

internal fun SaverScenes.glyphSpan(l: Lane, k: Int, x: IntArray) {
    val g = l.t.g[k]
    x[0] = g.ox + g.box.x0
    x[1] = g.ox + g.box.x1
    if ((if (k >= l.t.cut) l.spec.src2 else l.spec.src) == Src.Time) {
        x[0] = min(x[0], l.t.tm0)
        x[1] = max(x[1], l.t.tm1 + l.grow)
    }
}

internal fun SaverScenes.timeKept(l: Lane, k0: Int, k1: Int, nIn: Int = -1, cutIn: Int = -1): Boolean {
    val n = if (nIn < 0) l.t.g.size else nIn
    val cut = if (cutIn < 0) l.t.cut else cutIn
    val t0 = l.spec.src == Src.Time
    val t1 = l.spec.src2 == Src.Time
    val a = if (t0) 0 else cut
    val b = if (t0) cut else n
    return !timeKeep || (!t0 && !t1) || a >= b || (k0 <= a && b <= k1)
}

internal fun SaverScenes.brkAdd(l: Lane): Long = if (l.brkCopy != 0) l.brkStart - l.brkCopy.toLong() * l.period else 0L

internal fun SaverScenes.carryBreak(l: Lane, copy: Int, keepIn: Int, n0: Int, edgeIn: Long, startIn: Long) {
    val n = l.t.g.size
    if (copy < 1 || l.skip || l.spec.src == Src.Art || l.period == 0 || l.count != 0 || n == 0) return
    var keep = keepIn
    var edge = edgeIn
    var start = startIn
    val fwd = l.spec.speed >= 0
    val t0 = l.spec.src == Src.Time
    val timed = t0 || l.spec.src2 == Src.Time
    val a = if (t0) 0 else l.t.cut
    val b = if (t0) l.t.cut else n
    val least = if (!timeKeep || !timed || a >= b) 0 else if (fwd) n - a else b
    val gap = max(0, l.period - (l.t.ink1 - l.t.ink0)).toLong()
    start = max(start, (if (keep >= n0) copy else copy - 1).toLong() * l.period)
    if (keep >= n0) {
        keep = n
    } else {
        val lead = if (fwd) l.t.ink1 - start else l.t.ink0 + start
        edge = if (fwd) max(edge, lead + gap) else min(edge, lead - gap)
        val s = arrayOf(utf8(srcText(l, 0, l.time)), utf8(srcText(l, 1, l.time)))
        val off = (if (fwd) -1 else 1) * laneCopyOff(l, copy - 1)
        var e = if (fwd) Long.MAX_VALUE else Long.MIN_VALUE
        var letter = false
        keep = 0
        val x = IntArray(TWO)
        for (k in 1..n) {
            val at = if (fwd) n - k else k - 1
            val g = l.t.g[at]
            glyphSpan(l, at, x)
            if (!g.box.empty) e = if (fwd) min(e, x[0] + off) else max(e, x[1] + off)
            if (if (fwd) e < edge else e > edge) break
            letter = letter || letterCp(cpAt(s[if (at >= l.t.cut) 1 else 0], g.byte))
            if (k >= least && (k == n || (letter && !g.box.empty && boundary(l, l.t.g, l.t.cut, if (fwd) n - k else k, true)))) keep = k
        }
        if (keep < least) {
            keep = least
            val kept = breakEdge(l, copy, keep)
            start = max(start, if (fwd) l.t.ink1 + gap - kept else kept + gap - l.t.ink0)
            if (start - copy.toLong() * l.period > l.period) return
        }
    }
    l.brkCopy = copy
    l.brkKeep = keep
    l.brkEdge = edge
    l.brkStart = start
}

internal fun SaverScenes.laneBreak(i: Int): Break {
    if (!has(i)) return Break()
    val l = lanes[i]
    return Break(l.brkCopy, l.brkKeep, brkAdd(l).toInt())
}

internal fun SaverScenes.tryBreak(i: Int, copy: Int, keep: Int, add: Int): Boolean {
    if (!has(i)) return false
    val l = lanes[i]
    val n = l.t.g.size
    val fwd = l.spec.speed >= 0
    if (l.skip || l.spec.src == Src.Art || l.period == 0 || l.count != 0 || l.brkCopy != 0 || copy < 1 || keep < 1 ||
        keep > n || add < 0 || add > l.period
    ) return false
    if (keep < n && (if (fwd) l.t.g[n - keep] else l.t.g[keep - 1]).box.empty) return false
    if (keep < n && (!keptLetter(l, if (fwd) n - keep else 0, if (fwd) n else keep) ||
            !timeKept(l, if (fwd) n - keep else 0, if (fwd) n else keep) ||
            !boundary(l, l.t.g, l.t.cut, if (fwd) n - keep else keep, true))
    ) return false
    if (laneIn(l, stepAt)) {
        val c = IntArray(TWO)
        laneCopyRange(l, stepAt, c)
        if (c[1] >= (if (keep < n) copy - 1 else copy)) return false
    }
    l.brkCopy = copy
    l.brkKeep = keep
    l.brkEdge = breakEdge(l, copy, keep)
    l.brkStart = copy.toLong() * l.period + add
    return true
}

internal fun SaverScenes.undoBreak(i: Int) {
    if (!has(i)) return
    val l = lanes[i]
    l.brkCopy = 0
    l.brkKeep = 0
    l.brkEdge = 0
    l.brkStart = 0
}
