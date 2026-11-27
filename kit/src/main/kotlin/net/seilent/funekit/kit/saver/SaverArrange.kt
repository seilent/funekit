package net.seilent.funekit.kit.saver

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal const val P1 = 0
internal const val P2 = 1
internal const val P3 = 2
internal const val P3_TILE = 3

private const val PASSES = 7
private const val LAST_PASS = 6
private const val REF_W = 272L
private const val REF_H = 480L
private const val TWO = 2
private const val MS = 1000.0
private const val WORD = 32
private const val U32 = 0xFFFFFFFFL

private fun lo32(d: Long): Long = d and U32

private fun key(j: Int, d: Long): Long = (j.toLong() shl WORD) or lo32(d)

internal fun SaverScenes.arrange(from: Long) {
    var steps = 0L
    var most = 0L
    val now = stepAt
    stepAt = if (from > -NEVER) stepAt else -NEVER
    val p = pos?.invoke() ?: -1L
    val len = sceneEnd() - sceneAt
    crossLeft = 0
    for (l in lanes) {
        l.redone = false
        l.redoCut = -1
    }
    for (l in lanes) {
        if (l.t0 <= from) continue
        val sp = l.spec
        if (l.gave) {
            l.gave = false
            l.skip = sp.src != Src.Art && l.t.g.isEmpty()
        }
        if (from > -NEVER) {
            l.t0 = max(sceneAt + secsMs(sp.inAt), from + 1)
            l.across = toPx(sp.across)
            l.span[0] = Span()
            l.span[1] = Span()
            l.kept = -1
            l.cutBy = -1
            l.cutFell = false
            lay(l)
            l.skip = sp.src != Src.Art && l.t.g.isEmpty()
            if (!l.skip) place(l)
        }
        l.block = Blocker()
        growOf(l, p, len)
    }
    fun gaves(): Int = lanes.count { it.gave }
    for (pass in 0 until PASSES) {
        val kept = lanes.map { it.kept }
        val gave = gaves()
        pack(from)
        for (l in lanes) if (l.t0 > from && !l.skip) place(l)
        openCut(from)
        schedule(from)
        steps += glyphSteps + redoSteps
        most = max(most, (glyphSteps + redoSteps).toLong())
        var cut = false
        for (i in lanes.indices) cut = cut || lanes[i].kept != kept[i]
        if ((!cut && gaves() == gave) || pass == LAST_PASS) break
        crossLeft = 0
        for (l in lanes) {
            if (l.t0 <= from) continue
            l.t0 = max(sceneAt + secsMs(l.spec.inAt), from + 1)
            l.across = toPx(l.spec.across)
            l.block = if (l.gave) l.block else Blocker()
            l.skip = (l.spec.src != Src.Art && l.t.g.isEmpty()) || l.gave
            if (!l.skip) place(l)
        }
    }
    this.most = most.toInt()
    stepAt = now
}

internal fun SaverScenes.extent(l: Lane, c: Int, e: DoubleArray) {
    val s = sgn(l)
    val last = l.half && l.count != 0 && c == l.count - 1
    var a = if (last && s > 0) l.t.ink0b else l.t.ink0
    var b = if (last && s < 0) l.t.ink1a else l.t.ink1
    if (l.t.timed && !last) {
        a = min(a, l.t.tm0)
        b = max(b, l.t.tm1) + l.grow
    }
    val off = -s * laneCopyOff(l, c).toDouble()
    e[0] = a + off
    e[1] = b + off
}

internal fun SaverScenes.reach(l: Lane, target: Double, strict: Boolean): Long {
    val s = sgn(l)
    fun ok(t: Long): Boolean {
        val p = s * along(l, t).toDouble() / ONE
        return if (strict) p > target else p >= target
    }
    var lo = l.t0
    var hi = max(l.t0, l.out)
    if (ok(lo)) return lo
    if (!ok(hi)) return NEVER
    while (hi - lo > 1) {
        val mid = lo + (hi - lo) / TWO
        if (ok(mid)) hi = mid else lo = mid
    }
    return hi
}

internal fun SaverScenes.window(l: Lane, w: LongArray) {
    w[0] = l.t0
    w[1] = l.out
    if (l.skip || (l.period != 0 && l.count == 0)) return
    val s = sgn(l)
    val e = DoubleArray(TWO)
    extent(l, max(l.count - 1, 0), e)
    val t = reach(l, if (s > 0) l.umax / ONE.toDouble() - e[0] else e[1] - l.umin / ONE.toDouble(), false)
    if (t != NEVER) w[1] = min(w[1], t)
}

internal fun SaverScenes.refWindow(l: Lane, w: LongArray) {
    window(l, w)
    val cut = (widthPx.toLong() shl BITS) - (heightPx.toLong() shl BITS) * REF_W / REF_H
    if (l.skip || l.spec.angle != 0 || (l.period != 0 && l.count == 0) || cut <= 0) return
    val s = sgn(l)
    val e = DoubleArray(TWO)
    val d = cut / ONE.toDouble()
    extent(l, max(l.count - 1, 0), e)
    val t = reach(l, if (s > 0) l.umax / ONE.toDouble() - d - e[0] else e[1] - l.umin / ONE.toDouble() - d, false)
    if (t != NEVER) w[1] = min(w[1], t)
}

internal fun SaverScenes.runsOf(l: Lane, c: Int, v0: Double, v1: Double, out: ArrayList<DoubleArray>) {
    out.clear()
    val off = -sgn(l) * laneCopyOff(l, c).toDouble()
    if (l.spec.src == Src.Art) {
        if (l.t.bottom > v0 && l.t.top < v1) out += doubleArrayOf(off, l.t.ink1 + off)
        return
    }
    val gs = IntArray(TWO)
    laneCopyGlyphs(l, c, l.t.g, gs)
    var open = false
    var a = 0.0
    var b = 0.0
    for (k in gs[0] until gs[1]) {
        val g = l.t.g[k]
        if (g.box.empty || g.oy + g.box.y1 <= v0 || g.oy + g.box.y0 >= v1) {
            if (open) out += doubleArrayOf(a + off, b + off)
            open = false
            continue
        }
        var x0 = (g.ox + g.box.x0).toDouble()
        var x1 = (g.ox + g.box.x1).toDouble()
        if ((if (k >= l.t.cut) l.spec.src2 else l.spec.src) == Src.Time) {
            x0 = min(x0, l.t.tm0.toDouble())
            x1 = max(x1, (l.t.tm1 + l.grow).toDouble())
        }
        a = if (open) min(a, x0) else x0
        b = if (open) max(b, x1) else x1
        open = true
    }
    if (open) out += doubleArrayOf(a + off, b + off)
}

internal fun SaverScenes.occupy(l: Lane, r0: Double, r1: Double, v0: Double, v1: Double, out: ArrayList<Seg>) {
    out.clear()
    if (l.skip || (l.spec.src != Src.Art && l.t.g.isEmpty())) return
    val n = if (l.count != 0) l.count else if (l.period != 0) COPIES else 1
    val runs = ArrayList<DoubleArray>()
    val segs = ArrayList<Seg>()
    for (c in 0 until n) if (!copyOccupy(l, c, r0, r1, v0, v1, runs, segs)) break
    segs.sortBy { it.a }
    for (g in segs) {
        if (out.isNotEmpty() && g.a <= out.last().b) out.last().b = max(out.last().b, g.b) else out += Seg(g.a, g.b)
    }
}

internal fun SaverScenes.copyOccupy(
    l: Lane,
    c: Int,
    r0: Double,
    r1: Double,
    v0: Double,
    v1: Double,
    runs: ArrayList<DoubleArray>,
    out: ArrayList<Seg>,
): Boolean {
    val s = sgn(l)
    val gap = pxOf(crossGap)
    runsOf(l, c, v0 - gap, v1 + gap, runs)
    var any = false
    for (e in runs) {
        val lo = if (s > 0) r0 - gap - e[1] else e[0] - r1 - gap
        val hi = if (s > 0) r1 + gap - e[0] else e[1] - r0 + gap
        val a = reach(l, lo, true)
        if (a == NEVER) continue
        any = true
        var b = reach(l, hi, false)
        b = if (b == NEVER) l.out else b
        if (b > a) out += Seg(a, b)
    }
    return any || (runs.isEmpty() && c == l.brkCopy - 1)
}

private fun clipHalf(p: ArrayList<DoubleArray>, y: Boolean, lim: Double, below: Boolean) {
    val out = ArrayList<DoubleArray>()
    for (i in p.indices) {
        val a = p[i]
        val b = p[(i + 1) % p.size]
        val av = if (y) a[1] else a[0]
        val bv = if (y) b[1] else b[0]
        val ia = if (below) av <= lim else av >= lim
        val ib = if (below) bv <= lim else bv >= lim
        if (ia) out += a
        if (ia != ib) {
            val k = (lim - av) / (bv - av)
            out += doubleArrayOf(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)
        }
    }
    p.clear()
    p.addAll(out)
}

internal fun SaverScenes.region(a: Lane, b: Lane, v: Need): Boolean {
    val fa = Axes.of(a.spec.angle, a.ox, a.oy)
    val fb = Axes.of(b.spec.angle, b.ox, b.oy)
    val ca = fa.ox * fa.nx + fa.oy * fa.ny
    val cb = fb.ox * fb.nx + fb.oy * fb.ny
    val va = doubleArrayOf(ca + a.t.top, ca + a.t.bottom)
    val vb = doubleArrayOf(cb + b.t.top, cb + b.t.bottom)
    val p = ArrayList<DoubleArray>()
    for (k in 0 until CORNERS) {
        val s = va[if (k == 1 || k == TWO) 1 else 0]
        val q = vb[if (k >= TWO) 1 else 0]
        p += doubleArrayOf(s * fa.nx + q * fb.nx, s * fa.ny + q * fb.ny)
    }
    clipHalf(p, false, 0.0, false)
    clipHalf(p, false, widthPx.toDouble(), true)
    clipHalf(p, true, 0.0, false)
    clipHalf(p, true, heightPx.toDouble(), true)
    if (p.isEmpty()) return false
    v.ry0 = FAR
    v.rk0 = FAR
    v.ryv0 = FAR
    v.rkv0 = FAR
    v.ry1 = -FAR
    v.rk1 = -FAR
    v.ryv1 = -FAR
    v.rkv1 = -FAR
    for (q in p) {
        val ua = fa.u(q[0], q[1])
        val ub = fb.u(q[0], q[1])
        val wa = fa.v(q[0], q[1])
        val wb = fb.v(q[0], q[1])
        v.ry0 = min(v.ry0, ua)
        v.ry1 = max(v.ry1, ua)
        v.rk0 = min(v.rk0, ub)
        v.rk1 = max(v.rk1, ub)
        v.ryv0 = min(v.ryv0, wa)
        v.ryv1 = max(v.ryv1, wa)
        v.rkv0 = min(v.rkv0, wb)
        v.rkv1 = max(v.rkv1, wb)
    }
    return v.ry1 > v.ry0 && v.rk1 > v.rk0
}

internal fun SaverScenes.latest(l: Lane): Long {
    val v = abs(pxOf(l.spec.speed))
    val chord = if (v > 0) ceil((l.umax - l.umin) / ONE.toDouble() / v * MS).toLong() else 0L
    return sceneEnd() - chord
}

internal fun SaverScenes.bound(l: Lane): Long = max(0L, latest(l) - l.t0)

internal fun SaverScenes.fast(a: Lane, b: Lane): Boolean {
    if ((a.spec.speed >= 0) == (b.spec.speed >= 0)) return false
    fun len(l: Lane, v: Double): Double {
        val w = (l.t.ink1 - l.t.ink0).toDouble()
        return if (l.count != 0) w + laneCopyOff(l, l.count - 1).toDouble() else if (l.period != 0) w + v * (l.out - l.t0) / MS else w
    }
    val va = abs(pxOf(a.spec.speed))
    val vb = abs(pxOf(b.spec.speed))
    val chord = (a.umax - a.umin) / ONE.toDouble()
    return va + vb > 0 && (len(a, va) + len(b, vb) + chord) * MS / (va + vb) <= pass
}

internal fun SaverScenes.clears(k: Lane, r0: Double, r1: Double, v0: Double, v1: Double, by: Long, len64: LongArray): Int {
    len64[0] = 0
    if (k.spec.src == Src.Art || (k.period != 0 && k.count == 0)) return 0
    val s = sgn(k)
    val n = k.t.g.size
    var keep = 0
    var x0 = Int.MAX_VALUE
    var x1 = Int.MIN_VALUE
    val gap = pxOf(crossGap)
    val off = -s * laneCopyOff(k, max(k.count - 1, 0)).toDouble()
    for (i in 0 until n) {
        val g = k.t.g[if (s > 0) n - 1 - i else i]
        if (!g.box.empty) {
            val target = if (s > 0) r1 + gap - (g.ox + g.box.x0 + off) else g.ox + g.box.x1 + off - r0 + gap
            if (g.oy + g.box.y1 > v0 - gap && g.oy + g.box.y0 < v1 + gap && reach(k, target, false) > by) break
            x0 = min(x0, g.ox + g.box.x0)
            x1 = max(x1, g.ox + g.box.x1)
        }
        keep++
    }
    len64[0] = if (x1 > x0) (x1 - x0).toLong() * SUB else 0L
    return keep
}

internal fun SaverScenes.keepGlyphs(l: Lane, g: List<G>, cut: Int, base: Array<Span>, k0: Int, k1: Int) {
    val n = g.size
    for (p in 0 until TWO) {
        val pa = if (p != 0) cut else 0
        val pb = if (p != 0) n else cut
        val a = max(k0, pa)
        val b = min(k1, pb)
        if (a >= b) {
            l.span[p] = Span(0, 0)
            continue
        }
        l.span[p] = Span(if (a > pa) g[a].byte else base[p].b0, if (b < pb) g[b].byte else base[p].b1)
    }
    lay(l)
    l.skip = l.spec.src != Src.Art && l.t.g.isEmpty()
    if (!l.skip) place(l)
}

internal fun SaverScenes.anchor(): Int {
    var diag = false
    for (l in lanes) diag = diag || (!l.skip && turned(l.spec.angle))
    var a = -1
    for (i in lanes.indices) {
        val sp = lanes[i].spec
        if (lanes[i].skip || sp.src == Src.Art || sp.angle == UP || turned(sp.angle) != diag) continue
        if (a < 0) {
            a = i
            continue
        }
        val ap = lanes[a].spec
        val g = sp.size >= giantCjk
        val ag = ap.size >= giantCjk
        if (if (diag) !ag && (g || sp.size > ap.size) else sp.size > ap.size) a = i
    }
    return a
}

internal fun SaverScenes.openCut(from: Long) {
    var f = -1
    for (i in lanes.indices) {
        val l = lanes[i]
        if (!l.skip && l.spec.angle != UP && (f < 0 || l.spec.inAt < lanes[f].spec.inAt)) f = i
    }
    if (f < 0 || lanes[f].t0 <= from) return
    val lim = openHold
    fun held(only: Int): Long {
        val v = ArrayList<Need>()
        for (k in lanes.indices) {
            val nd = Need(k, P1)
            if ((only < 0 || only == k) && !lanes[k].skip && lanes[k].spec.angle == UP && region(lanes[f], lanes[k], nd)) v += nd
        }
        return if (v.isEmpty()) 0L else settle(f, v, NEVER, false)
    }
    if (held(-1) <= lim) return
    val order = ArrayList<LongArray>()
    for (v in lanes.indices) {
        val V = lanes[v]
        if (!V.skip && V.spec.angle == UP && V.spec.src != Src.Art && V.t0 > from) order += longArrayOf(-held(v), v.toLong())
    }
    order.sortWith(compareBy<LongArray>({ it[0] }, { it[1] }))
    for (o in order) {
        if (held(-1) <= lim) break
        val V = lanes[o[1].toInt()]
        val full = V.t.g
        val fcut = V.t.cut
        val n = full.size
        val base = arrayOf(V.span[0].copy(), V.span[1].copy())
        val at = ArrayList<Int>()
        var i0 = 0
        while (i0 < n && !keptLetter(V, i0, i0 + 1)) i0++
        for (k in i0 + 1 until n) if (boundary(V, full, fcut, k, true) && timeKept(V, 0, k, n, fcut)) at += k
        var lo = -1
        var hi = at.size
        while (hi - lo > 1) {
            val mid = (lo + hi) / TWO
            keepGlyphs(V, full, fcut, base, 0, at[mid])
            if (held(-1) <= lim) lo = mid else hi = mid
        }
        var k = if (lo >= 0) at[lo] else if (at.isEmpty()) n else at[0]
        while (k > 1 && full[k - 1].box.empty) k--
        keepGlyphs(V, full, fcut, base, 0, k)
        V.kept = if (k < n) k else V.kept
    }
}

internal fun SaverScenes.pack(from: Long) {
    val n = lanes.size
    fun text(i: Int) = lanes[i].spec.src != Src.Art
    fun moves(i: Int) = !lanes[i].skip && lanes[i].t0 > from
    val tn = LongArray(n) { toPx(lanes[it].spec.across) }
    val up = ArrayList<Int>()
    for (i in 0 until n) if (!lanes[i].skip && lanes[i].spec.angle == UP && text(i)) up += i
    up.sortWith { x, y -> if (tn[x] != tn[y]) tn[x].compareTo(tn[y]) else x.compareTo(y) }
    if (up.size >= TWO && moves(up[1])) {
        val i = lanes[up[0]]
        val o = lanes[up[1]]
        val gap = o.across - i.across + (i.t.top - o.t.bottom).toLong() * ONE
        if (gap < ONE) o.across += ONE - gap
    }
    val a = anchor()
    if (a < 0 || !turned(lanes[a].spec.angle)) return
    val axis = lanes[a].spec.angle
    val tb = LongArray(TWO)
    fun nominal(i: Int) {
        val l = lanes[i]
        val base = l.across - (if (l.spec.top) l.t.top.toLong() * ONE else 0L)
        tb[0] = base + l.t.top.toLong() * ONE
        tb[1] = base + l.t.bottom.toLong() * ONE
        if (!text(i)) return
        val dg = l.spec.size == digits || l.spec.size == giantDigits
        val st = partStyle(l.spec, if (dg && l.spec.src2 == Src.Time) 1 else 0, cjk)
        val h = glyphs.run(st, if (dg || l.spec.src == Src.Time) "0" else "H")
        val v = if (h.g.isEmpty()) null else glyphs.vbox(h.g[0].face, st.px, h.g[0].gid)
        if (v != null) tb[0] = base + v[0].toLong() * FX_PER_64
        tb[1] = base
    }
    val lim = ((widthPx + heightPx) / (TWO * sqrt(TWO.toDouble())) * ONE).toLong()
    var lo = 0L
    var hi = 0L
    var sh = 0L
    var tlo = 0L
    var thi = 0L
    var any = false
    var seen = false
    var fixed = false
    for (i in 0 until n) {
        if (lanes[i].skip || lanes[i].spec.angle != axis) continue
        fixed = fixed || !moves(i)
        nominal(i)
        val t = tb[0]
        val b = tb[1]
        lo = if (any) min(lo, t) else t
        hi = if (any) max(hi, b) else b
        any = true
        if (!text(i)) continue
        tlo = if (seen) min(tlo, t) else t
        thi = if (seen) max(thi, b) else b
        seen = true
    }
    if (seen && !fixed && thi > lim) sh = -min(thi - lim, max(0L, lo + lim))
    else if (seen && !fixed && tlo < -lim) sh = min(-lim - tlo, max(0L, lim - hi))
    if (sh != 0L) for (i in 0 until n) if (!lanes[i].skip && lanes[i].spec.angle == axis) lanes[i].across += sh
}

internal fun SaverScenes.spansOf(y: Int, nd: Need, ys: ArrayList<Seg>, ks: ArrayList<Seg>) {
    val Y = lanes[y]
    val K = lanes[nd.k]
    ys.clear()
    ks.clear()
    if (K.skip || (K.spec.src != Src.Art && K.t.g.isEmpty())) return
    if (nd.kind >= P3) {
        val w = LongArray(TWO)
        window(Y, w)
        ys += Seg(w[0], w[1])
        window(K, w)
        ks += Seg(w[0], w[1])
        return
    }
    occupy(Y, nd.ry0, nd.ry1, nd.ryv0, nd.ryv1, ys)
    occupy(K, nd.rk0, nd.rk1, nd.rkv0, nd.rkv1, ks)
}

internal fun SaverScenes.touchesOk(y: Int, nd: Need, pr: Probe? = null): Boolean {
    val Y = lanes[y]
    val K = lanes[nd.k]
    val ys = ArrayList<Seg>()
    val ks = ArrayList<Seg>()
    val on = ArrayList<Seg>()
    spansOf(y, nd, ys, ks)
    for (s in ys) for (k in ks) {
        val a = max(s.a, k.a) - GRID_MS
        val b = minOf(s.b, k.b, Y.out, K.out) + GRID_MS
        if (a < b) on += Seg(a, b)
    }
    on.sortBy { it.a }
    val tile = nd.kind == P2 && (Y.spec.src == Src.Art || K.spec.src == Src.Art)
    val gap = pxOf(crossGap)
    val tch = pxOf(if (tile) tileTouch else touch)
    val most = if (tile) tileTouchMs - GRID_MS else if (nd.kind == P2) (CROSS_FRAMES - 1) * GRID_MS else pass - GRID_MS
    val budget = (if (tile) tileTouchMs else pairBudget) - GRID_MS
    var run = 0L
    var total = 0L
    var last = -NEVER
    val by = ArrayList<Box>()
    val bk = ArrayList<Box>()
    val q = ArrayList<Quad>()
    for (s in on) {
        var t = sceneAt + max(0L, (s.a - sceneAt + GRID_MS - 1) / GRID_MS) * GRID_MS
        t = max(t, last + GRID_MS)
        while (t <= s.b) {
            if (pr != null && pr.steps >= pr.left) {
                pr.capped = true
                return false
            }
            if (pr != null) pr.steps++
            if (t > last + GRID_MS) run = 0
            last = t
            boxes(Y, t, Y.spec.angle, Y.ox, Y.oy, by, q)
            boxes(K, t, Y.spec.angle, Y.ox, Y.oy, bk, q)
            var shared = false
            var thick = 0.0
            var dist = FAR
            for (p in by) for (o in bk) {
                val w = min(p.u1, o.u1) - max(p.u0, o.u0)
                val h = min(p.v1, o.v1) - max(p.v0, o.v0)
                if (w > 0 && h > 0) {
                    shared = true
                    thick = max(thick, if (nd.kind == P2) min(w, h) else h)
                }
                dist = min(dist, hypot(max(0.0, -w), max(0.0, -h)))
            }
            if (shared && pr != null && pr.at0 && (pr.contact < 0 || t - sceneAt < pr.contact)) pr.contact = t - sceneAt
            if (shared && pr != null && pr.at0 && nd.kind == P2) pr.thick = max(pr.thick, thick)
            if (nd.kind == P1 && (shared || dist < gap)) return false
            if (nd.kind == P3_TILE && shared) return false
            if (nd.kind == P1 || nd.kind == P3_TILE) {
                t += GRID_MS
                continue
            }
            if (shared && thick > (if (tile) tch else tch - 1)) return false
            run = if (shared) run + GRID_MS else 0L
            total += if (shared) GRID_MS else 0L
            if (pr != null && pr.at0 && nd.kind == P2) {
                pr.run = max(pr.run, run)
                pr.total = max(pr.total, total)
            }
            if (run > most || total > budget) return false
            t += GRID_MS
        }
    }
    return true
}

internal fun SaverScenes.settle(
    y: Int,
    v: List<Need>,
    cap: Long,
    glyphs: Boolean,
    pr: Probe? = null,
    seen: HashMap<Long, Boolean>? = null,
): Long {
    val Y = lanes[y]
    val m = v.size
    val ys = List(m) { ArrayList<Seg>() }
    val ks = List(m) { ArrayList<Seg>() }
    for (j in 0 until m) spansOf(y, v[j], ys[j], ks[j])
    val cand = sortedSetOf(0L)
    for (j in 0 until m) for (k in ks[j]) for (s in ys[j]) if (k.b > s.a && k.b - s.a <= cap) cand += k.b - s.a
    val t0 = Y.t0
    for (d in cand) {
        var ok = true
        var j = 0
        while (j < m && ok) {
            var clear = true
            for (s in ys[j]) {
                val a = s.a + d
                val b = min(s.b + d, Y.out)
                for (k in ks[j]) clear = clear && !(a < b && a < k.b && k.a < b)
            }
            if (!clear && glyphs) {
                val kk = key(j, d)
                val at = seen?.get(kk)
                if (at != null) {
                    clear = at
                } else {
                    Y.t0 = t0 + d
                    if (pr != null) pr.at0 = d == 0L
                    clear = touchesOk(y, v[j], pr)
                    Y.t0 = t0
                    if (seen != null && !(pr != null && pr.capped)) seen[kk] = clear
                }
            }
            ok = clear
            j++
        }
        if (ok) return d
    }
    return NEVER
}

internal fun SaverScenes.blame(y: Int, v: List<Need>, by: Long): Blocker {
    val r = Blocker()
    var worst = 0L
    var w: Need? = null
    for (nd in v) {
        val d = settle(y, listOf(nd), NEVER, false)
        if (d > worst) {
            worst = d
            w = nd
        }
    }
    if (w == null) return r
    val K = lanes[w.k]
    val p3 = w.kind >= P3
    r.lane = w.k
    val len = LongArray(1)
    r.keep = if (p3) clears(K, K.umin / ONE.toDouble(), K.umax / ONE.toDouble(), -FAR, FAR, by, len)
    else clears(K, w.rk0, w.rk1, w.rkv0, w.rkv1, by, len)
    r.len64 = len[0]
    return r
}

internal fun SaverScenes.pickCut(K: Lane, full: List<G>, fcut: Int, most: Int, least: Int): Int {
    val n = full.size
    val fwd = K.spec.speed >= 0
    var pick = 0
    var word = 1
    while (word >= least && pick == 0) {
        var k = min(most, n - 1)
        while (k >= 1 && pick == 0) {
            if (boundary(K, full, fcut, if (fwd) n - k else k, word == 1) && keptLetter(K, if (fwd) n - k else 0, if (fwd) n else k) &&
                timeKept(K, if (fwd) n - k else 0, if (fwd) n else k, n, fcut)
            ) pick = k
            k--
        }
        word--
    }
    while (pick > 0 && full[if (fwd) n - pick else pick - 1].box.empty) pick--
    if (pick != 0 && (!keptLetter(K, if (fwd) n - pick else 0, if (fwd) n else pick) ||
            !timeKept(K, if (fwd) n - pick else 0, if (fwd) n else pick, n, fcut))
    ) pick = 0
    return pick
}

internal fun SaverScenes.cutBlocker(y: Int, v: List<Need>, cap: Long, from: Long, least: Int, searchIn: Boolean = false): Long {
    if (!cut) return NEVER
    val Y = lanes[y]
    Y.block = blame(y, v, sceneAt + secsMs(Y.spec.inAt))
    if (Y.block.lane < 0) return NEVER
    val K = lanes[Y.block.lane]
    if (K.spec.src == Src.Art || K.t0 <= from || (K.period != 0 && K.count == 0) || K.t.g.size < TWO) return NEVER
    val full = K.t.g
    val fcut = K.t.cut
    val n = full.size
    val base = arrayOf(K.span[0].copy(), K.span[1].copy())
    val fwd = K.spec.speed >= 0
    fun keep(k: Int) = keepGlyphs(K, full, fcut, base, if (fwd) n - k else 0, if (fwd) n else k)
    fun choose(most: Int, least: Int) = pickCut(K, full, fcut, most, least)
    val search = searchIn && cap > 0
    var pick = if (search) 0 else choose(Y.block.keep, least)
    var chose = -1L
    var fell = false
    if (pick == 0 && cap > 0) {
        val lane = Y.block.lane
        fun keepBy(by: Long): Int {
            val b = blame(y, v, by)
            return if (b.lane == lane) b.keep else 0
        }
        fun works(p: Int, lead: Boolean): Boolean {
            keep(p)
            val d = settle(y, v, cap, false)
            var ok = d != NEVER
            if (ok && lead) {
                Y.t0 += d
                ok = leads(y, lane)
                Y.t0 -= d
            }
            keep(n)
            return ok
        }
        var by = Y.t0 + cap
        var kk = keepBy(by)
        while (kk >= 1 && pick == 0) {
            val p = choose(kk, 1)
            if (p != 0 && works(p, true)) {
                pick = p
                chose = by
                break
            }
            if (keepBy(Y.t0) >= kk) break
            var lo = Y.t0
            var hi = by
            while (hi - lo > GRID_MS) {
                val mid = lo + (hi - lo) / TWO
                if (keepBy(mid) < kk) lo = mid else hi = mid
            }
            by = lo
            kk = keepBy(by)
        }
        if (pick == 0 && search) pick = choose(Y.block.keep, least)
        var p = 1
        while (pick == 0 && p < n) {
            if (choose(p, 1) == p && works(p, false)) {
                pick = p
                fell = true
            }
            p++
        }
    }
    if (pick == 0) return NEVER
    keep(pick)
    val d = settle(y, v, cap, false)
    if (d == NEVER) {
        keep(n)
        return NEVER
    }
    var x0 = Int.MAX_VALUE
    var x1 = Int.MIN_VALUE
    for (i in (if (fwd) n - pick else 0) until (if (fwd) n else pick)) {
        if (!full[i].box.empty) {
            x0 = min(x0, full[i].ox + full[i].box.x0)
            x1 = max(x1, full[i].ox + full[i].box.x1)
        }
    }
    K.kept = pick
    K.cutBy = if (chose < 0) -1 else chose - sceneAt
    K.cutFell = fell
    Y.block.keep = pick
    Y.block.len64 = if (x1 > x0) (x1 - x0).toLong() * SUB else 0L
    return d
}

internal fun SaverScenes.leads(y: Int, skip: Int): Boolean {
    val Y = lanes[y]
    val a = anchor()
    if (a < 0 || !turned(Y.spec.angle) || Y.spec.angle != -lanes[a].spec.angle) return true
    for (m in lanes.indices) {
        val M = lanes[m]
        val nd = Need()
        if (m == skip || M.skip || M.spec.src == Src.Art || M.t.g.isEmpty() || M.spec.angle != -Y.spec.angle || !region(Y, M, nd)) continue
        val sy = ArrayList<Seg>()
        val sm = ArrayList<Seg>()
        occupy(Y, nd.ry0, nd.ry1, nd.ryv0, nd.ryv1, sy)
        occupy(M, nd.rk0, nd.rk1, nd.rkv0, nd.rkv1, sm)
        if (sy.isNotEmpty() && sm.isNotEmpty() && sm[0].a <= sy[0].a) return false
    }
    return true
}

private class Fix(val k: Int = -1, val copy: Int = 0, val keep: Int = 0, val add: Int = 0, val dt: Long = 0, val hold: Long = 0)

private class Was(val g: List<G>, val cut: Int, val kept: Int, val span: Array<Span>, val by: Long, val fell: Boolean)

internal fun SaverScenes.hold(y: Int, v: List<Need>, from: Long) {
    if (lanes[y].skip || v.isEmpty()) return
    val Y = lanes[y]
    var cut = false
    val tile = Y.spec.src == Src.Art
    val m = v.size
    for (j in 0 until m) if (tile && v[j].kind == P2 && lanes[v[j].k].brkCopy != 0 && lanes[v[j].k].t0 > from) undoBreak(v[j].k)
    val cap = bound(Y)
    val seen = HashMap<Long, Boolean>()
    fun waitOf(): Long {
        var d = settle(y, v, cap, false)
        if (d > 0 && d != NEVER) {
            val pr = Probe()
            pr.left = GLYPH_STEPS - glyphSteps
            val g = if (pr.left > 0) settle(y, v, d - 1, true, pr, seen) else NEVER
            glyphSteps += pr.steps
            Y.capped = pr.left <= 0 || pr.capped
            Y.holdRegion = d
            d = if (g == NEVER) d else g
            Y.holdGlyph = d
            Y.contact = pr.contact
            Y.p2thick = pr.thick
            Y.p2run = pr.run
            Y.p2total = pr.total
        }
        if (d == NEVER) d = settle(y, v, cap, true, null, seen)
        return d
    }

    fun way(wait: Long, entry: Boolean): Long {
        val t0 = Y.t0
        val rep = BooleanArray(m)
        val ys = List(m) { ArrayList<Seg>() }
        val kall = List(m) { ArrayList<Seg>() }
        val ks = List(m) { ArrayList<ArrayList<Seg>>() }
        val runs = ArrayList<DoubleArray>()
        for (j in 0 until m) {
            val K = lanes[v[j].k]
            rep[j] = v[j].kind == P2 && K.spec.src != Src.Art && K.period != 0 && K.count == 0 && K.brkCopy == 0 &&
                K.t.g.isNotEmpty() && pxOf(K.spec.speed) != 0.0
            spansOf(y, v[j], ys[j], kall[j])
            var c = 0
            while (rep[j] && c < COPIES) {
                val s = ArrayList<Seg>()
                if (!copyOccupy(K, c, v[j].rk0, v[j].rk1, v[j].rkv0, v[j].rkv1, runs, s)) break
                ks[j] += s
                c++
            }
        }
        val pr = Probe()
        pr.left = GLYPH_STEPS - glyphSteps
        fun hit(j: Int, d: Long, on: List<Seg>, dt: Long): Boolean {
            val out = lanes[v[j].k].out
            for (s in ys[j]) {
                val a = s.a + d
                val b = min(s.b + d, Y.out)
                for (k in on) {
                    val ka = k.a + dt
                    val kb = if (dt != 0L) min(k.b + dt, out) else k.b
                    if (a < b && a < kb && ka < b) return true
                }
            }
            return false
        }

        fun clear(j: Int, d: Long, f: Fix): Boolean {
            if (f.k >= 0 && f.hold != 0L) return true
            var ok = true
            if (!rep[j]) ok = !hit(j, d, kall[j], 0)
            var c = 0
            while (rep[j] && ok && c < ks[j].size) {
                if (f.k < 0 || c != f.copy - 1 || f.keep >= lanes[f.k].t.g.size) ok = !hit(j, d, ks[j][c], if (f.k >= 0 && c >= f.copy) f.dt else 0L)
                c++
            }
            if (ok || f.k >= 0 || entry) return ok
            val kk = key(j, d)
            val at = seen[kk]
            if (at != null) return at
            Y.t0 = t0 + d
            ok = touchesOk(y, v[j], pr)
            Y.t0 = t0
            if (!pr.capped) seen[kk] = ok
            return ok
        }

        fun shiftFor(j: Int, d: Long, copy: Int, keep: Int): Fix? {
            val K = lanes[v[j].k]
            val sp = abs(pxOf(K.spec.speed))
            val dts = sortedSetOf(0L)
            for (c in copy until ks[j].size) for (k in ks[j][c]) for (s in ys[j]) if (s.b + d > k.a) dts += s.b + d - k.a
            for (dt in dts) {
                val add = ceil(dt * sp / MS).toInt()
                if (add > K.period) break
                val g = Fix(v[j].k, copy, keep, add, ceil(add * MS / sp).toLong())
                if ((dt != 0L || keep < K.t.g.size) && clear(j, d, g) && tryBreak(g.k, g.copy, g.keep, g.add)) {
                    undoBreak(g.k)
                    return g
                }
            }
            return null
        }

        fun keptBy(j: Int, c: Int, by: Long): Int {
            val K = lanes[v[j].k]
            val nd = v[j]
            val s = sgn(K)
            val n = K.t.g.size
            var keep = 0
            val gap = pxOf(crossGap)
            val off = -s * laneCopyOff(K, c).toDouble()
            for (i in 0 until n) {
                val g = K.t.g[if (s > 0) n - 1 - i else i]
                val at = if (s > 0) nd.rk1 + gap - (g.ox + g.box.x0 + off) else g.ox + g.box.x1 + off - nd.rk0 + gap
                if (!g.box.empty && g.oy + g.box.y1 > nd.rkv0 - gap && g.oy + g.box.y0 < nd.rkv1 + gap && reach(K, at, false) > by) break
                keep++
            }
            return keep
        }

        fun holdFor(j: Int, d: Long): Fix? {
            val k = v[j].k
            val K = lanes[k]
            val nd = Need(y, P2)
            if (v[j].kind != P2 || K.spec.src == Src.Art || K.t0 <= from || K.t0 < t0 || !region(K, Y, nd)) return null
            Y.t0 = t0 + d
            val c2 = bound(K)
            var dr = settle(k, listOf(nd), c2, false)
            if (dr > 0 && dr != NEVER) {
                val q = Probe()
                q.left = pr.left - pr.steps
                val g = if (q.left > 0) settle(k, listOf(nd), dr - 1, true, q) else NEVER
                pr.steps += q.steps
                pr.capped = pr.capped || q.capped
                dr = if (g == NEVER) dr else g
            } else if (dr == NEVER) {
                val q = Probe()
                q.left = pr.left - pr.steps
                dr = if (q.left > 0) settle(k, listOf(nd), c2, true, q) else NEVER
                pr.steps += q.steps
                pr.capped = pr.capped || q.capped
            }
            Y.t0 = t0
            if (dr == NEVER || dr <= 0) return null
            return Fix(k, 0, 0, 0, dr, dr)
        }

        fun fix(j: Int, d: Long, brk: Boolean): Fix? {
            val none = Fix()
            if (clear(j, d, none)) return none
            var c = 0
            val n = lanes[v[j].k].t.g.size
            while (rep[j] && c < ks[j].size && !hit(j, d, ks[j][c], 0)) c++
            val at = rep[j] && c < ks[j].size
            if (at && c >= 1) shiftFor(j, d, c, n)?.let { return it }
            if (entry) holdFor(j, d)?.let { return it }
            if (!at || !brk) return null
            var by = NEVER
            for (s in ys[j]) by = min(by, s.a + d)
            val K = lanes[v[j].k]
            val fwd = K.spec.speed >= 0
            var letter = false
            val txt = arrayOf(utf8(srcText(K, 0, K.time)), utf8(srcText(K, 1, K.time)))
            val most = min(keptBy(j, c, by), n - 1)
            var pick = 0
            for (k in 1..most) {
                val gi = if (fwd) n - k else k - 1
                val p = if (fwd) n - k else k
                val g = K.t.g[gi]
                letter = letter || letterCp(cpAt(txt[if (gi >= K.t.cut) 1 else 0], g.byte))
                val ca = cpAt(txt[if (p - 1 >= K.t.cut) 1 else 0], K.t.g[p - 1].byte)
                val cb = cpAt(txt[if (p >= K.t.cut) 1 else 0], K.t.g[p].byte)
                if (letter && !g.box.empty && (p == K.t.cut || blankCp(ca) || blankCp(cb) || (cjkCp(ca) && cjkCp(cb))) &&
                    timeKept(K, if (fwd) n - k else 0, if (fwd) n else k) && boundary(K, K.t.g, K.t.cut, p, true)
                ) pick = k
            }
            if (pick == 0 || !tryBreak(v[j].k, c + 1, pick, 0)) return null
            undoBreak(v[j].k)
            return shiftFor(j, d, c + 1, pick)
        }

        val sh = LongArray(1)
        fun solve(d: Long, brk: Boolean, fs: ArrayList<Fix>): Boolean {
            fs.clear()
            sh[0] = d
            for (j in 0 until m) {
                val f = fix(j, d, brk) ?: return false
                if (f.k >= 0) {
                    fs += f
                    sh[0] = max(sh[0], f.dt)
                }
            }
            return true
        }

        fun othersOk(f: Fix): Boolean {
            val kk = f.k
            val K = lanes[kk]
            for (o in lanes.indices) {
                val O = lanes[o]
                val a = O.spec.angle
                val cross = if (a == UP) K.spec.angle != UP else turned(a) && a == -K.spec.angle
                val nd = Need(o, if (a == UP) P1 else P2)
                if (o == y || o == kk || !cross || O.skip || (O.spec.src != Src.Art && O.t.g.isEmpty()) || !region(K, O, nd) ||
                    touchesOk(kk, nd, pr)
                ) continue
                if (f.hold != 0L) {
                    K.t0 -= f.hold
                    val was = !pr.capped && touchesOk(kk, nd, pr)
                    K.t0 += f.hold
                    if (was || pr.capped) return false
                    continue
                }
                val b = laneBreak(kk)
                undoBreak(kk)
                val was = !pr.capped && touchesOk(kk, nd, pr)
                if (!tryBreak(kk, b.copy, b.keep, b.add) || was || pr.capped) return false
            }
            return true
        }

        fun put(fs: List<Fix>, on: Boolean): Int {
            var a = 0
            while (on && a < fs.size) {
                if (fs[a].hold != 0L) lanes[fs[a].k].t0 += fs[a].hold
                else if (!tryBreak(fs[a].k, fs[a].copy, fs[a].keep, fs[a].add)) break
                a++
            }
            var i = if (on) a else fs.size
            while (!on && i > 0) {
                if (fs[i - 1].hold != 0L) lanes[fs[i - 1].k].t0 -= fs[i - 1].hold else undoBreak(fs[i - 1].k)
                i--
            }
            return a
        }

        fun apply(fs: List<Fix>): Boolean {
            val a = put(fs, true)
            var ok = a == fs.size
            var i = 0
            while (ok && i < fs.size) {
                ok = othersOk(fs[i])
                i++
            }
            if (!ok) put(fs.subList(0, a), false)
            i = 0
            while (ok && i < fs.size) {
                if (fs[i].hold != 0L) {
                    lanes[fs[i].k].block = Blocker(y, 0, 0)
                    for (r in crosses) {
                        r.am += if (r.m == fs[i].k && r.am >= 0) fs[i].hold else 0L
                        r.ax += if (r.x == fs[i].k && r.ax >= 0) fs[i].hold else 0L
                    }
                }
                i++
            }
            return ok
        }

        fun done(d: Long): Long {
            Y.t0 = t0
            glyphSteps += pr.steps
            Y.capped = Y.capped || pr.capped
            return d
        }

        if (entry) {
            val cand = sortedSetOf(0L)
            fun ends(j: Int, on: List<Seg>) {
                for (k in on) for (q in ys[j]) if (k.b > q.a && k.b - q.a <= cap) cand += k.b - q.a
            }
            for (j in 0 until m) {
                ends(j, kall[j])
                for (c in ks[j]) ends(j, c)
                if (!rep[j]) continue
                val K = lanes[v[j].k]
                val nd = v[j]
                val s = sgn(K)
                val n = K.t.g.size
                val gap = pxOf(crossGap)
                var ya = NEVER
                for (q in ys[j]) ya = min(ya, q.a)
                val x = IntArray(TWO)
                for (c in ks[j].indices) {
                    val off = -s * laneCopyOff(K, c).toDouble()
                    var gone = Long.MIN_VALUE
                    for (k in 1 until n) {
                        val at = if (s > 0) n - k else k - 1
                        val g = K.t.g[at]
                        glyphSpan(K, at, x)
                        if (!g.box.empty && g.oy + g.box.y1 > nd.rkv0 - gap && g.oy + g.box.y0 < nd.rkv1 + gap) {
                            val r = reach(K, if (s > 0) nd.rk1 + gap - (x[0] + off) else x[1] + off - nd.rk0 + gap, false)
                            if (r == NEVER) break
                            gone = max(gone, r)
                        }
                        if (gone != Long.MIN_VALUE && gone > ya && gone - ya <= cap && timeKept(K, if (s > 0) n - k else 0, if (s > 0) n else k) &&
                            boundary(K, K.t.g, K.t.cut, if (s > 0) n - k else k, true)
                        ) cand += gone - ya
                    }
                }
            }
            val fs = ArrayList<Fix>()
            var best: List<Fix> = emptyList()
            var bd = NEVER
            var bs = NEVER
            for (d in cand) {
                if (pr.left <= 0 || pr.capped) {
                    pr.capped = true
                    break
                }
                if (bs != NEVER && d >= bs) break
                if (!solve(d, true, fs) || (bs != NEVER && sh[0] >= bs)) continue
                val a = put(fs, true)
                var ok = a == fs.size
                Y.t0 = t0 + d
                var j = 0
                while (ok && j < m) {
                    ok = touchesOk(y, v[j], pr)
                    j++
                }
                Y.t0 = t0
                put(fs.subList(0, a), false)
                if (ok) {
                    bd = d
                    bs = sh[0]
                    best = ArrayList(fs)
                }
            }
            if (bd != NEVER && !apply(best)) bd = NEVER
            return done(bd)
        }
        var first = false
        var late = wait == NEVER
        if (!late) {
            Y.t0 = t0 + wait
            late = laneIn(Y, sceneEnd())
            Y.t0 = t0
        }
        var j = 0
        while (j < m && !first) {
            if (!rep[j] || ks[j].isEmpty() || !hit(j, 0, ks[j][0], 0) || clear(j, 0, Fix())) {
                j++
                continue
            }
            val K = lanes[v[j].k]
            first = late || wait > K.period * MS / abs(pxOf(K.spec.speed))
            j++
        }
        val fs = ArrayList<Fix>()
        if (first && solve(0, true, fs) && apply(fs)) return done(0)
        val cand = sortedSetOf(0L)
        for (jj in 0 until m) for (k in kall[jj]) for (s in ys[jj]) if (k.b > s.a && k.b - s.a <= cap) cand += k.b - s.a
        for (brk in booleanArrayOf(false, true)) {
            var bd = if (brk) NEVER else wait
            var bs = bd
            var best: List<Fix> = emptyList()
            for (d in cand) {
                if (bs != NEVER && d >= bs) break
                if (solve(d, brk, fs) && (bs == NEVER || sh[0] < bs || (sh[0] == bs && d < bd))) {
                    bd = d
                    bs = sh[0]
                    best = ArrayList(fs)
                }
            }
            if (best.isNotEmpty() && !apply(best)) bd = if (brk) NEVER else wait
            if (bd != NEVER) return done(bd)
        }
        return done(NEVER)
    }

    var d = waitOf()
    if (tile && d != 0L) {
        Y.holdGlyph = if (d == NEVER) Y.holdGlyph else d
        d = way(d, false)
    }
    if (d == NEVER && tile) {
        val once = ArrayList<Need>()
        for (nd in v) {
            val K = lanes[nd.k]
            if (nd.kind == P2 && K.spec.src != Src.Art && !(K.period != 0 && K.count == 0)) once += nd
        }
        val was = ArrayList<Was>()
        for (nd in once) {
            val K = lanes[nd.k]
            was += Was(K.t.g, K.t.cut, K.kept, arrayOf(K.span[0].copy(), K.span[1].copy()), K.cutBy, K.cutFell)
        }
        if (once.isNotEmpty() && cutBlocker(y, once, cap, from, 1) != NEVER) {
            seen.clear()
            d = waitOf()
            if (d != 0L) d = way(d, false)
            cut = d != NEVER
            var j = 0
            while (!cut && j < once.size) {
                val K = lanes[once[j].k]
                if (K.kept != was[j].kept) {
                    keepGlyphs(K, was[j].g, was[j].cut, was[j].span, 0, was[j].g.size)
                    K.kept = was[j].kept
                    K.cutBy = was[j].by
                    K.cutFell = was[j].fell
                }
                j++
            }
        }
    }
    if (d == NEVER && tile && entryHold && (scene == SaverScene.J || scene == SaverScene.L)) d = way(NEVER, true)
    if (d == NEVER && !tile) {
        d = cutBlocker(y, v, cap, from, 0)
        cut = d != NEVER
    }
    if (d == NEVER && tile) {
        Y.block = blame(y, v, sceneAt + secsMs(Y.spec.inAt))
        Y.unfit = !give
        Y.gave = give
        Y.skip = Y.skip || give
        Y.t0 = max(sceneAt + secsMs(Y.spec.inAt), from + 1)
        return
    }
    if (d == NEVER) {
        Y.block = blame(y, v, sceneAt + secsMs(Y.spec.inAt))
        val k = Y.block.lane
        val at = v.firstOrNull { it.k == k }
        if (k >= 0 && at != null && lanes[k].t0 > from && lanes[k].t0 >= Y.t0 && !lanes[k].skip) {
            val K = lanes[k]
            val nd = Need(y, at.kind)
            if (nd.kind >= P3 || region(K, Y, nd)) {
                val c2 = bound(K)
                var d2 = settle(k, listOf(nd), c2, false)
                if (d2 == NEVER) d2 = settle(k, listOf(nd), c2, true)
                if (d2 != NEVER) {
                    K.t0 += d2
                    K.block = Blocker(y, 0, 0)
                    return
                }
            }
        }
        if (give && Y.period == 0) {
            Y.gave = true
            Y.skip = true
            Y.t0 = max(sceneAt + secsMs(Y.spec.inAt), from + 1)
            return
        }
        Y.unfit = true
        Y.t0 += cap
        return
    }
    if (d > 0 && !cut) {
        val b = blame(y, v, sceneAt + secsMs(lanes[y].spec.inAt))
        if (b.lane >= 0) Y.block = b
    }
    Y.t0 += d
}

internal fun SaverScenes.schedule(from: Long) {
    val n = lanes.size
    crosses.clear()
    glyphSteps = 0
    redoSteps = 0
    for (l in lanes) {
        l.holdRegion = -1
        l.holdGlyph = -1
        l.contact = -1
        l.p2thick = 0.0
        l.p2run = 0
        l.p2total = 0
        l.capped = l.gave && l.capped
        l.unfit = false
        if (l.t0 > from) {
            l.brkCopy = 0
            l.brkKeep = 0
            l.brkEdge = 0
            l.brkStart = 0
        }
    }
    val need = List(n) { ArrayList<Need>() }
    val wa = LongArray(n)
    val wb = LongArray(n)
    val w = LongArray(TWO)
    for (i in 0 until n) {
        refWindow(lanes[i], w)
        wa[i] = w[0]
        wb[i] = w[1]
    }
    fun live(i: Int) = !lanes[i].skip && (lanes[i].spec.src == Src.Art || lanes[i].t.g.isNotEmpty())
    fun text(i: Int) = lanes[i].spec.src != Src.Art
    fun started(i: Int) = lanes[i].t0 <= from
    fun pair(y: Int, k: Int, kind: Int) {
        val v = Need(k, kind)
        if (kind < P3 && !region(lanes[y], lanes[k], v)) return
        need[y] += v
    }
    fun keeps(x: Int, z: Int, ax: Long, az: Long): Boolean {
        if (started(x) != started(z)) return started(x)
        if (ax != az) return ax < az
        return if (lanes[x].spec.inAt != lanes[z].spec.inAt) lanes[x].spec.inAt < lanes[z].spec.inAt else x < z
    }
    for (y in 0 until n) if (live(y) && !started(y) && lanes[y].spec.angle != UP) {
        for (k in 0 until n) if (live(k) && lanes[k].spec.angle == UP) pair(y, k, P1)
        hold(y, need[y], from)
    }
    val a = anchor()
    val axis = if (a >= 0) lanes[a].spec.angle else 0
    var x = 0
    while (x < n && turned(axis)) {
        if (!live(x) || lanes[x].spec.angle != -axis) {
            x++
            continue
        }
        val mains = ArrayList<Int>()
        for (m in 0 until n) {
            val v = Need()
            if (live(m) && lanes[m].spec.angle == axis && !(started(x) && started(m)) && region(lanes[x], lanes[m], v)) mains += m
        }
        val first = BooleanArray(mains.size)
        val redoOn = redo && text(x) && !lanes[x].redone
        var pre: List<Lane> = emptyList()
        var pc: List<Cross> = emptyList()
        val t0 = lanes[x].t0
        val g0 = glyphSteps
        val nv = need[x].size
        var held = Blocker()
        val xi = x
        fun rounds() {
            var round = 0
            while (round <= mains.size && !lanes[xi].skip) {
                var more = false
                for (j in mains.indices) {
                    val v = Need()
                    if (first[j] || !region(lanes[xi], lanes[mains[j]], v)) continue
                    val sx = ArrayList<Seg>()
                    val sm = ArrayList<Seg>()
                    occupy(lanes[xi], v.ry0, v.ry1, v.ryv0, v.ryv1, sx)
                    occupy(lanes[mains[j]], v.rk0, v.rk1, v.rkv0, v.rkv1, sm)
                    val tx = text(xi) || started(xi)
                    val tm = text(mains[j]) || started(mains[j])
                    val kept = sx.isNotEmpty() && sm.isNotEmpty() && (if (tx != tm) tx else keeps(xi, mains[j], sx[0].a, sm[0].a))
                    val r = Cross(
                        xi, mains[j], v.ry0, v.ry1, v.rk0, v.rk1, if (sx.isEmpty()) -1 else sx[0].a - sceneAt,
                        if (sm.isEmpty()) -1 else sm[0].a - sceneAt, kept,
                    )
                    val at = crosses.indexOfFirst { it.x == r.x && it.m == r.m }
                    if (at < 0) crosses += r else crosses[at] = r
                    if (sx.isEmpty() || sm.isEmpty() || r.kept) continue
                    first[j] = true
                    more = true
                    pair(xi, mains[j], P2)
                }
                if (!more) break
                if (redoOn && pre.isEmpty()) {
                    pre = lanes.map { it.copy() }
                    pc = crosses.map { it.copy() }
                }
                hold(xi, need[xi], from)
                if (held.lane < 0 && !lanes[xi].unfit && !lanes[xi].gave && lanes[xi].t0 > t0) held = lanes[xi].block.copy()
                round++
            }
        }
        rounds()
        if (redoOn && pre.isNotEmpty() && (lanes[x].unfit || lanes[x].gave) && held.lane >= 0) {
            val snap = lanes
            val cs = crosses
            val nx = ArrayList(need[x])
            val fs = first.copyOf()
            val g1 = glyphSteps
            lanes = ArrayList(pre.map { it.copy() })
            crosses = ArrayList(pc.map { it.copy() })
            glyphSteps = g0
            while (need[x].size > nv) need[x].removeAt(need[x].size - 1)
            first.fill(false)
            val vv = ArrayList<Need>()
            for (nd in nx) if (nd.k == held.lane) vv += nd
            val X = lanes[x]
            val cap = min(bound(X), pass)
            val cut = vv.isNotEmpty() && cutBlocker(x, vv, cap, from, 1, true) != NEVER
            X.block = pre[x].block.copy()
            if (cut) rounds()
            if (!cut || lanes[x].unfit || lanes[x].gave || !redoKeep) {
                redoSteps += glyphSteps - g0
                lanes = snap
                crosses = cs
                need[x].clear()
                need[x].addAll(nx)
                fs.copyInto(first)
                glyphSteps = g1
            } else {
                redoSteps += g1 - g0
                lanes[x].redoCut = held.lane
            }
            lanes[x].redone = true
        }
        var j = 0
        while (j < mains.size && !lanes[x].skip) {
            if (!first[j]) {
                pair(mains[j], x, P2)
                hold(mains[j], need[mains[j]], from)
            }
            j++
        }
        x++
    }
    val seen = BooleanArray(n * n)
    packPasses = 0
    val ca = LongArray(n)
    val cb = LongArray(n)
    for (p in 0..n * n) {
        packPasses++
        var found = false
        for (i in 0 until n) {
            window(lanes[i], w)
            ca[i] = w[0]
            cb[i] = w[1]
        }
        for (xx in 0 until n) for (z in xx + 1 until n) {
            val X = lanes[xx]
            val Z = lanes[z]
            val ang = X.spec.angle
            if (seen[xx * n + z] || !live(xx) || !live(z) || text(xx) != text(z) || ang != Z.spec.angle || ang == UP ||
                (started(xx) && started(z)) || !(ca[xx] < cb[z] && ca[z] < cb[xx]) ||
                (text(xx) && min(wb[xx], wb[z]) - max(wa[xx], wa[z]) >= pass)
            ) continue
            val f = Axes.of(ang, 0, 0)
            val nx = (X.ox * f.nx + X.oy * f.ny) / ONE.toDouble()
            val nz = (Z.ox * f.nx + Z.oy * f.ny) / ONE.toDouble()
            val over = min(nx + X.t.bottom, nz + Z.t.bottom) - max(nx + X.t.top, nz + Z.t.top)
            val floor = if (!text(xx)) 0.0 else if (fast(X, Z)) pxOf(touch) else -1.0
            if (over <= floor) continue
            seen[xx * n + z] = true
            val xk = keeps(xx, z, ca[xx], ca[z])
            val y = if (xk) z else xx
            pair(y, if (xk) xx else z, if (text(xx)) P3 else P3_TILE)
            hold(y, need[y], from)
            found = true
        }
        if (!found) break
    }
}

internal fun SaverScenes.fit() {
    for (pass in 0 until PASSES) {
        var cut = false
        for (l in lanes) cut = pieceCut(l) || cut
        if (!cut) break
        again()
    }
    for (l in lanes) cull(l)
}

internal fun SaverScenes.secondAt(l: Lane): Long {
    val s = sgn(l)
    val lead = if (l.two) (if (s > 0) l.t.ink1a else l.t.ink0b).toDouble()
    else if (s > 0) l.t.ink1 - laneCopyOff(l, 1).toDouble() else l.t.ink0 + laneCopyOff(l, 1).toDouble()
    return reach(l, if (s > 0) l.umin / ONE.toDouble() - lead else lead - l.umax / ONE.toDouble(), true)
}

internal fun SaverScenes.pieceCut(l: Lane): Boolean {
    val sp = l.spec
    val s = sgn(l)
    val w = l.w[if (l.two && s > 0) 1 else 0]
    val alt = l.period != 0 && l.two && (sp.split != 0L || sp.even)
    val once = !sp.repeat && l.two && s < 0 && sp.src2 == Src.Time
    if (l.pieced || l.skip || !w.laid || (sp.pieces < TWO && !alt && !once) || secondAt(l) != NEVER) return false
    l.pieced = true
    return false
}

internal fun SaverScenes.again() {
    for (l in lanes) {
        l.t0 = sceneAt + secsMs(l.spec.inAt)
        l.across = toPx(l.spec.across)
        l.skip = l.spec.src != Src.Art && l.t.g.isEmpty()
        if (!l.skip) place(l)
    }
    arrange(-NEVER)
}

internal fun SaverScenes.cull(l: Lane) {
    if (!l.w[0].shaped && !l.w[1].shaped) return
    for (w in l.w) w.laid = false
}
