package net.seilent.funekit.kit.motion

import net.seilent.funekit.gen.Keys
import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.Moves
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.min

private const val SLIDE_OUT = 300
private const val SLIDE_IN = 500
private const val HALF = 2f

enum class MoveKind { T2, T2n, T2w, T4h, T4n, T3, T4, T3m, T4m, T7, T8, T3c, T3a, T4a, T3z, T4z, None }

enum class Part {
    Leave, LeaveHeader, LeavePivots, LeaveGround, LeaveWord, Content, Header, Zoom, Pivots, Ground, Word, Look, Home,
    Sub, Peek, Tile, NpIn, NpOut, NpLeave, NpEdge, NpPeek, NpMusic, NpUnder, NpPhoto, Face, HomeTurn,
}

enum class Prop { A, S, X, Y, R, PX, PY }

enum class ContentForm { Zoom, Step, Rest }

enum class WordLeg { HomeOpen, HomeBack, Push, Pop, Grow, Shrink }

enum class MorphKind { None, Pivot, Row }

enum class NpForm { Page, Front, Ring }

val leaveGroup: Set<Part> = setOf(Part.Leave, Part.LeaveHeader, Part.LeavePivots, Part.LeaveGround, Part.LeaveWord)

val arriveGroup: Set<Part> = setOf(Part.Content, Part.Header, Part.Pivots)

val wordGroup: Set<Part> = setOf(Part.Word, Part.LeaveWord)

private fun ms(v: Float): Int = v.toInt()

private fun formOf(mode: String): ContentForm = when (mode) {
    "step" -> ContentForm.Step
    "rest" -> ContentForm.Rest
    else -> ContentForm.Zoom
}

private fun Keys.scaled(u: Float) = Keys(t, FloatArray(v.size) { v[it] * u })

class MoveLeg(
    val part: Part,
    val prop: Prop,
    var from: Float,
    val to: Float,
    var delay: Int,
    var dur: Int,
    var curve: Curve = Curves.linear,
    val pin: Int = 0,
) {
    val end: Int get() = delay + dur

    fun at(e: Long): Float {
        if (dur <= 0) return if (e >= delay) to else from
        if (e <= delay) return from
        if (e >= end) return to
        return lerp(from, to, curve.at((e - delay).toFloat() / dur))
    }
}

data class MoveForms(
    val leaveFade: Boolean = true,
    val content: ContentForm = ContentForm.Zoom,
    val chromeSlide: Boolean = true,
    val headerZoom: Boolean = true,
    val t3mFade: Boolean = true,
    val wordMorph: Boolean = true,
    val hubRows: ContentForm = ContentForm.Zoom,
    val pageRows: ContentForm = ContentForm.Zoom,
    val t2PivotY: Float = TokBoard.a105.motion_t2PivotY,
) {
    companion object {
        fun board(b: TokBoard.Board) = MoveForms(
            leaveFade = Tok.motion_leaveFade == "fade",
            content = formOf(Tok.motion_contentZoom),
            chromeSlide = Tok.motion_chromeSlide == "slide",
            headerZoom = Tok.motion_headerForm == "zoom",
            t3mFade = Tok.motion_t3mForm == "fade",
            wordMorph = Tok.motion_wordForm == "morph",
            hubRows = formOf(Tok.motion_contentHubRows),
            pageRows = formOf(Tok.motion_contentPageRows),
            t2PivotY = b.motion_t2PivotY,
        )
    }
}

data class MoveEnds(
    val pivotX: Float = 0f,
    val pivotY: Float = 0f,
    val wordS0: Float = 1f,
    val wordS1: Float = 1f,
    val wordX0: Float = 0f,
    val wordX1: Float = 0f,
    val wordY0: Float = 0f,
    val wordY1: Float = 0f,
    val tileS0: Float = 1f,
    val tileS1: Float = 1f,
    val tileX0: Float = 0f,
    val tileX1: Float = 0f,
    val tileY0: Float = 0f,
    val tileY1: Float = 0f,
    val peek0: Float = 0f,
    val peek1: Float = 0f,
    val sub: Float = 1f,
    val look: Float = 1f,
    val morphS0: Float = 1f,
    val morphS1: Float = 1f,
    val morphX0: Float = 0f,
    val morphX1: Float = 0f,
    val morphY0: Float = 0f,
    val morphY1: Float = 0f,
    val morph: Boolean = false,
    val morphHeaderIn: Boolean = false,
    val morphHeaderOut: Boolean = false,
    val grounds: Boolean = false,
    val zoom: Boolean = true,
    val pivots: Boolean = true,
    val word: Boolean = false,
    val morphKind: MorphKind = MorphKind.None,
    val morphBack: Boolean = false,
    val rows: Boolean = false,
    val retarget: Boolean = false,
    val rowsDx: Float = 0f,
    val endAt: Int = 0,
    val npForm: NpForm = NpForm.Page,
    val npCut: Boolean = false,
    val npX: Float = 0f,
    val npY: Float = 0f,
    val npLeft: Float = 0f,
    val npRight: Float = 0f,
    val npBack: Float = 0f,
    val plug: Boolean = false,
    val npQueue: Boolean = false,
    val colHold: Boolean = false,
    val tileBack: Boolean = false,
    val colBack: Boolean = false,
    val thumb: Boolean = false,
    val leaveRows: Boolean = false,
    val pxU: Float = 1f,
    val homeU: Float = 1f,
    val sw: Float = 0f,
    val sh: Float = 0f,
    val zoomX: Float = 0f,
    val zoomY: Float = 0f,
    val zoomBase: Float = 0f,
    val lheadBase: Float = 0f,
    val tapX: Float = 0f,
    val tapY: Float = 0f,
    val lhead: Boolean = false,
    val slideX: Float = 0f,
)

fun npInk(form: NpForm, f: MoveForms): Int = when (form) {
    NpForm.Front -> ms(Tok.motion_t7PaneInk)
    NpForm.Ring -> (if (f.leaveFade) ms(Old.motion_t7RingLeave) else 0) + ms(Old.motion_t7RingDark)
    NpForm.Page -> ms(Old.motion_t7Leave) + ms(Old.motion_npDark)
}

class PageMove {
    private class Off(val p: Part, val q: Prop, val at: Int, val d: Float)

    var kind = MoveKind.T3
        private set
    var t0 = 0L
        private set
    var live = false
        private set
    var reversed = false
        private set
    var wordEnd = 0
        private set
    var cut = false
        private set
    val legs: List<MoveLeg> get() = list

    private var swap = 0
    private var end = 0
    private var endAtMs = 0
    private var held = mutableSetOf<Part>()
    private var tied = mutableSetOf<Part>()
    private var morph = true
    private var list = mutableListOf<MoveLeg>()
    private var offs = mutableListOf<Off>()
    private val base = Array(Part.entries.size) { FloatArray(Prop.entries.size) }

    val swapAt: Long get() = t0 + swap
    val endAt: Long get() = t0 + end

    fun stop() {
        live = false
    }

    fun leg(p: Part, q: Prop): MoveLeg? {
        var f: MoveLeg? = null
        for (l in list) if (l.part == p && l.prop == q && (f == null || l.delay < f.delay)) f = l
        return f
    }

    fun last(p: Part, q: Prop): MoveLeg? {
        var z: MoveLeg? = null
        for (l in list) if (l.part == p && l.prop == q && (z == null || l.delay >= z.delay)) z = l
        return z
    }

    fun legsOf(p: Part, q: Prop): Int = list.count { it.part == p && it.prop == q }

    fun span(p: Part, q: Prop): Pair<Float, Float>? {
        val f = leg(p, q) ?: return null
        val z = last(p, q) ?: return null
        return f.from to z.to
    }

    private fun active(p: Part, q: Prop, e: Long): MoveLeg? {
        var a: MoveLeg? = null
        for (l in list) if (l.part == p && l.prop == q && l.delay <= e && (a == null || l.delay >= a.delay)) a = l
        return a ?: leg(p, q)
    }

    fun has(p: Part, q: Prop): Boolean = leg(p, q) != null

    fun hold(p: Part) {
        held += p
    }

    fun hold(group: Set<Part>) {
        held += group
    }

    fun held(p: Part): Boolean = p in held

    private fun tie(p: Part) {
        tied += p
    }

    private fun value(p: Part, q: Prop, at: Long): Float {
        val b = base[p.ordinal][q.ordinal]
        var v = b
        for (o in offs) if (o.p == p && o.q == q && at >= o.at) v += o.d
        val f = leg(p, q) ?: return v
        var e = at
        if (held(p)) {
            if (p !in wordGroup) return b
            e = min(e, f.delay - 1L)
        } else if (p in tied && held.any { it in wordGroup }) {
            e = min(e, f.delay - 1L)
        } else if (p == Part.Leave && held(Part.Content) && !cut) {
            val z = last(p, q) ?: f
            e = min(e, if (z.dur > Moves.kTail) z.end - Moves.kTail.toLong() else z.delay - 1L)
        }
        return v - b + (active(p, q, e) ?: f).at(e)
    }

    fun at(p: Part, q: Prop, t: Long): Float = value(p, q, t - t0)

    fun moving(p: Part, t: Long): Boolean {
        val e = t - t0
        return list.any { it.part == p && it.dur > 0 && e > it.delay && e < it.end }
    }

    fun partEnd(p: Part): Long = t0 + (list.filter { it.part == p }.maxOfOrNull { it.end } ?: 0)

    fun set(p: Part, q: Prop, v: Float) {
        base[p.ordinal][q.ordinal] = v
    }

    fun from(p: Part, q: Prop, v: Float) {
        val l = leg(p, q)
        if (l != null) l.from = v else set(p, q, v)
    }

    fun fadeFrom(p: Part, a: Float) {
        val f = leg(p, Prop.A)
        if (f == null || f.from != 1f || legsOf(p, Prop.A) <= 1) {
            from(p, Prop.A, a)
            return
        }
        val i = list.indexOfFirst { it.part == p && it.prop == Prop.A }
        for (k in i until list.size) {
            val l = list[k]
            if (l.part == p && l.prop == Prop.A) list[k] = MoveLeg(l.part, l.prop, l.from * a, l.to * a, l.delay, l.dur, l.curve, l.pin)
        }
    }

    private fun erase(p: Part, q: Prop) {
        list.removeAll { it.part == p && it.prop == q }
    }

    fun clear(p: Part) {
        list.removeAll { it.part == p }
    }

    fun land(p: Part) {
        for (q in Prop.entries) last(p, q)?.let { base[p.ordinal][q.ordinal] = it.to }
        clear(p)
    }

    fun add(l: MoveLeg) {
        erase(l.part, l.prop)
        list += l
    }

    private fun append(l: MoveLeg) {
        list += l
    }

    private fun keys(p: Part, q: Prop, t: IntArray, v: FloatArray, c: Curve, delay: Int) {
        erase(p, q)
        val n = t.size
        if (n <= 0) return
        if (n == 1) {
            append(MoveLeg(p, q, v[0], v[0], delay + t[0], 0))
            return
        }
        for (i in 0 until n - 1) append(MoveLeg(p, q, v[i], v[i + 1], delay + t[i], max(0, t[i + 1] - t[i]), c))
    }

    private fun keys(p: Part, q: Prop, k: Keys, c: Curve = Curves.linear, delay: Int = 0) = keys(p, q, k.t, k.v, c, delay)

    private fun stepKeys(p: Part, q: Prop, k: Keys, delay: Int = 0) {
        step(p, q, 0f, k.v[0], delay + k.t[0])
        for (i in 0 until k.t.size - 1) append(MoveLeg(p, q, k.v[i], k.v[i + 1], delay + k.t[i], k.t[i + 1] - k.t[i]))
    }

    fun pivot(p: Part, x: Float, y: Float, at: Int) {
        val e = at.toLong()
        val s = value(p, Prop.S, e)
        val px0 = value(p, Prop.PX, e)
        val py0 = value(p, Prop.PY, e)
        offs += Off(p, Prop.X, at, (1 - s) * (px0 - x))
        offs += Off(p, Prop.Y, at, (1 - s) * (py0 - y))
        append(MoveLeg(p, Prop.PX, px0, x, at, 0))
        append(MoveLeg(p, Prop.PY, py0, y, at, 0))
        finish()
    }

    private fun ramp(p: Part, q: Prop, from: Float, to: Float, delay: Int, dur: Int, c: Curve) =
        add(MoveLeg(p, q, from, to, delay, max(dur, 0), c))

    private fun step(p: Part, q: Prop, from: Float, to: Float, delay: Int) = add(MoveLeg(p, q, from, to, delay, 0))

    private fun finish() {
        var e = max(end, swap)
        for (l in list) e = max(e, l.end)
        end = e
        if (endAtMs > 0) end = max(swap, endAtMs)
    }

    fun wordLegs(k: WordLeg, e: MoveEnds, delay: Int = 0): Int {
        fun box(p: Part, s0: Float, s1: Float, x0: Float, x1: Float, y0: Float, y1: Float, sd: Int, sl: Int, xd: Int, xl: Int, yd: Int, yl: Int, sc: Curve, xc: Curve, yc: Curve) {
            if (!morph) return
            ramp(p, Prop.S, s0, s1, delay + sd, sl, sc)
            ramp(p, Prop.X, x0, x1, delay + xd, xl, xc)
            ramp(p, Prop.Y, y0, y1, delay + yd, yl, yc)
        }

        fun word(sd: Int, sl: Int, xd: Int, xl: Int, yd: Int, yl: Int, sc: Curve, xc: Curve, yc: Curve, out: Int) {
            box(Part.Word, e.wordS0, e.wordS1, e.wordX0, e.wordX1, e.wordY0, e.wordY1, sd, sl, xd, xl, yd, yl, sc, xc, yc)
            step(Part.Word, Prop.A, 1f, 0f, delay + out)
        }

        fun chain(q: Prop, v0: Float, v1: Float, k: Keys, progress: Boolean, from: Int) {
            val t = ArrayList<Int>()
            val v = ArrayList<Float>()
            if (from >= 0) {
                t += from
                v += v0
            }
            for (i in k.t.indices) {
                t += k.t[i]
                v += if (progress) lerp(v0, v1, k.v[i]) else k.v[i]
            }
            keys(Part.Word, q, t.toIntArray(), v.toFloatArray(), Curves.linear, delay)
        }
        val t4md = ms(Tok.motion_t4mDelay)
        return when (k) {
            WordLeg.HomeOpen -> {
                val wl = ms(Tok.motion_t2Word)
                val bd = ms(Tok.motion_t2BaseDelay)
                val bl = ms(Tok.motion_t2Base)
                val out = max(wl, bd + bl)
                if (morph) {
                    chain(Prop.S, e.wordS0, e.wordS1, Moves.kT2Scale, false, 0)
                    ramp(Part.Word, Prop.X, e.wordX0, e.wordX1, delay + bd, bl, Curves.easeOut)
                    chain(Prop.Y, e.wordY0, e.wordY1, Moves.kT2Base, true, -1)
                    keys(Part.Face, Prop.A, Moves.kT2Face, Curves.linear, delay)
                }
                step(Part.Word, Prop.A, 1f, 0f, delay + out)
                delay + out
            }
            WordLeg.HomeBack -> {
                val bl = ms(Tok.motion_t4hBox)
                if (morph) {
                    val sk = Moves.kT4hScale
                    keys(Part.Word, Prop.S, intArrayOf(0) + sk.t + Moves.kT4hScaleEnd, floatArrayOf(e.wordS0) + sk.v + e.wordS1, Curves.linear, delay)
                    ramp(Part.Word, Prop.X, e.wordX0, e.wordX1, delay, bl, Curves.powOut15)
                    chain(Prop.Y, e.wordY0, e.wordY1, Moves.kT4hBase, true, -1)
                    keys(Part.Face, Prop.A, Moves.kT4hFace, Curves.linear, delay)
                }
                step(Part.Word, Prop.A, 1f, 0f, delay + ms(Tok.motion_t4hSwap))
                delay + max(Moves.kT4hScaleEnd, bl)
            }
            WordLeg.Push -> if (e.morphKind == MorphKind.Row) {
                if (morph) {
                    chain(Prop.S, e.morphS0, e.morphS1, Moves.kT3RowScale, false, 0)
                    ramp(Part.Word, Prop.X, e.morphX0, e.morphX1, delay, Moves.kT3RowWordIn, Curves.easeOut)
                    val mid = e.morphY1 + Moves.kT3RowWordDrop * e.pxU
                    ramp(Part.Word, Prop.Y, e.morphY0, mid, delay, Moves.kT3RowWordIn, Curves.easeOut)
                    val b = Moves.kT3RowBase
                    for (i in 0 until b.t.size - 1) {
                        append(MoveLeg(Part.Word, Prop.Y, lerp(mid, e.morphY1, b.v[i]), lerp(mid, e.morphY1, b.v[i + 1]), delay + b.t[i], b.t[i + 1] - b.t[i]))
                    }
                }
                keys(Part.Word, Prop.A, Moves.kT3RowWordA, Curves.linear, delay)
                append(MoveLeg(Part.Word, Prop.A, 1f, 0f, delay + Moves.kT3RowWordEnd, 0))
                delay + Moves.kT3RowWordEnd
            } else {
                val l = ms(Tok.motion_morphPush)
                box(Part.Word, e.morphS0, e.morphS1, e.morphX0, e.morphX1, e.morphY0, e.morphY1, t4md, l, t4md, l, t4md, l, Curves.cubicOut, Curves.cubicOut, Curves.cubicOut)
                step(Part.Word, Prop.A, 1f, 0f, delay + t4md + l)
                delay + t4md + l
            }
            WordLeg.Pop -> if (e.morphKind == MorphKind.Row) {
                if (morph) {
                    val s0 = e.morphS0
                    val s1 = e.morphS1
                    val ds = s0 - s1
                    val w = Moves.kT4RowWord
                    val n = w.t.size + 1
                    val t = IntArray(n)
                    val ss = FloatArray(n)
                    val xs = FloatArray(n)
                    val ys = FloatArray(n)
                    ss[0] = s0
                    xs[0] = e.morphX0
                    ys[0] = e.morphY0
                    for (i in w.t.indices) {
                        val s = w.v[i] * s1
                        val p = if (ds != 0f) ((s0 - s) / ds).coerceIn(0f, 1f) else 1f
                        t[i + 1] = w.t[i]
                        ss[i + 1] = s
                        xs[i + 1] = lerp(e.morphX0, e.morphX1, p)
                        ys[i + 1] = lerp(e.morphY0, e.morphY1, p)
                    }
                    keys(Part.Word, Prop.S, t, ss, Curves.linear, delay)
                    keys(Part.Word, Prop.X, t, xs, Curves.linear, delay)
                    keys(Part.Word, Prop.Y, t, ys, Curves.linear, delay)
                    for (l in list) if (l.part == Part.Word && l.delay == delay && l.prop != Prop.A) l.curve = Curves.easeOut
                }
                val a = Moves.kT4RowWordA
                keys(Part.Word, Prop.A, a, Curves.linear, delay)
                append(MoveLeg(Part.Word, Prop.A, a.v.last(), 0f, delay + Moves.kT4RowWordOut, 0))
                delay + Moves.kT4RowWordOut
            } else {
                val l = ms(Tok.motion_morphPop)
                box(Part.Word, e.morphS0, e.morphS1, e.morphX0, e.morphX1, e.morphY0, e.morphY1, t4md, l, t4md, l, t4md, l, Curves.cubicOut, Curves.cubicOut, Curves.cubicOut)
                keys(Part.Word, Prop.A, Moves.kT4PivWordA, Curves.linear, delay)
                delay + Moves.kT4PivWordOut
            }
            WordLeg.Grow -> {
                val ld = ms(Tok.motion_t3mLabelDelay)
                val ll = ms(Tok.motion_t3mLabel)
                val bd = ms(Tok.motion_t3mBaseDelay)
                val bl = ms(Tok.motion_t3mBase)
                val out = max(ld + ll, bd + bl)
                word(ld, ll, ld, ll, bd, bl, Curves.easeOut, Curves.easeOut, Curves.cubicOut, out)
                delay + out
            }
            WordLeg.Shrink -> {
                val l = ms(Tok.motion_t4m)
                word(t4md, l, t4md, l, t4md, l, Curves.cubicOut, Curves.cubicOut, Curves.cubicOut, t4md + l)
                delay + t4md + l
            }
        }
    }

    private fun rowsIn(e: MoveEnds, f: MoveForms) {
        val t2 = kind == MoveKind.T2 || kind == MoveKind.T2n
        clear(Part.Content)
        set(Part.Content, Prop.S, 1f)
        if (f.content == ContentForm.Rest) {
            step(Part.Content, Prop.A, 0f, 1f, 0)
            step(Part.Leave, Prop.A, 1f, 0f, 0)
        } else {
            val rl = ms(if (t2) Tok.motion_rowsT2 else Tok.motion_rows)
            ramp(Part.Content, Prop.S, Tok.motion_rowsDy, 1f, 0, rl, Curves.powOut15)
            ramp(Part.Content, Prop.X, e.rowsDx, 0f, 0, rl, Curves.powOut15)
            when {
                f.content == ContentForm.Step -> step(Part.Content, Prop.A, 0f, 1f, 0)
                t2 -> {
                    val ink = if (e.plug) Moves.kT2nPlugInk else Moves.kT2RowsInk
                    step(Part.Content, Prop.A, 0f, Tok.motion_rowsT2Alpha, ink)
                    append(MoveLeg(Part.Content, Prop.A, Tok.motion_rowsT2Alpha, 1f, ink, ms(Tok.motion_rowsT2Fade)))
                }
                else -> ramp(Part.Content, Prop.A, 0f, 1f, ms(Tok.motion_rowsT3FadeDelay), ms(Tok.motion_rowsT3Fade), Curves.smoothStep)
            }
        }
        if (t2) {
            erase(Part.Ground, Prop.A)
            val a = list.filter { it.part == Part.Content && it.prop == Prop.A }
            for (g in a) append(MoveLeg(Part.Ground, Prop.A, g.from, g.to, g.delay, g.dur, g.curve, g.pin))
            hold(Part.Ground)
        }
    }

    private fun popIn(e: MoveEnds, f: MoveForms) {
        val after = ms(Tok.motion_contentAfter)
        val from = ms(Tok.motion_leave)
        val word = if (wordEnd > 0) wordEnd else ms(Tok.motion_headerDelay) + ms(Tok.motion_header)
        val out = max(word + after, from + after)
        clear(Part.Content)
        if (kind == MoveKind.T4m || kind == MoveKind.T4a) {
            set(Part.Content, Prop.S, 1f)
            when {
                e.colBack -> ramp(Part.Content, Prop.A, 0f, 1f, ms(Tok.motion_t3cBackDelay), ms(Tok.motion_t3cBack), Curves.linear)
                f.content == ContentForm.Rest || f.content == ContentForm.Step -> step(Part.Content, Prop.A, 0f, 1f, word)
                else -> ramp(Part.Content, Prop.A, 0f, 1f, word, ms(Tok.motion_t4mFade), Curves.linear)
            }
            return
        }
        if (f.content == ContentForm.Zoom) {
            add(MoveLeg(Part.Content, Prop.A, 0f, 1f, from, out - from, Curves.linear, after))
        } else {
            step(Part.Content, Prop.A, 0f, 1f, from)
        }
        if (!e.rows && f.content != ContentForm.Rest) {
            add(MoveLeg(Part.Content, Prop.S, Tok.motion_contentScale, 1f, from, out - from, Curves.easeOut, after))
        } else {
            set(Part.Content, Prop.S, 1f)
        }
    }

    private fun pageIn(e: MoveEnds, f: MoveForms) {
        val hd = ms(Tok.motion_headerDelay)
        val hl = ms(Tok.motion_header)
        when {
            !e.zoom -> step(Part.Header, Prop.A, 0f, 1f, hd)
            !f.headerZoom -> step(Part.Header, Prop.A, 0f, 1f, hd + hl)
            else -> {
                ramp(Part.Zoom, Prop.S, Tok.motion_headerZoom, 1f, hd, hl, Curves.easeOut)
                step(Part.Zoom, Prop.A, 0f, 1f, hd)
                step(Part.Header, Prop.A, 0f, 1f, hd + hl)
            }
        }
        set(Part.Zoom, Prop.PX, e.zoomX)
        set(Part.Zoom, Prop.PY, e.zoomY)
        if (e.pivots) {
            val pd = ms(Tok.motion_pivotDelay)
            val pl = ms(Tok.motion_pivotIn)
            val pf = ms(Tok.motion_pivotFade)
            when {
                kind == MoveKind.T4m -> keys(Part.Pivots, Prop.A, Moves.kT4PivPivots)
                f.chromeSlide -> {
                    ramp(Part.Pivots, Prop.X, e.pivotX, 0f, pd, pl, Curves.easeOut)
                    ramp(Part.Pivots, Prop.Y, e.pivotY, 0f, pd, pl, Curves.easeOut)
                    ramp(Part.Pivots, Prop.A, 0f, 1f, pd, pf, Curves.linear)
                }
                else -> step(Part.Pivots, Prop.A, 0f, 1f, pd + pl)
            }
        }
        if (e.grounds) ramp(Part.Ground, Prop.A, 0f, 1f, ms(Tok.motion_groundDelay), ms(Tok.motion_ground), Curves.linear)
        if (kind == MoveKind.T4 || kind == MoveKind.T4m || kind == MoveKind.T4a) {
            popIn(e, f)
            return
        }
        val cd = ms(Tok.motion_contentDelay)
        val cl = ms(Tok.motion_content)
        if (f.content == ContentForm.Rest) {
            step(Part.Content, Prop.A, 0f, 1f, cd + cl)
            set(Part.Content, Prop.S, 1f)
        } else {
            ramp(Part.Content, Prop.S, Tok.motion_contentScale, 1f, cd, cl, Curves.easeOut)
            if (f.content == ContentForm.Step) step(Part.Content, Prop.A, 0f, 1f, cd) else ramp(Part.Content, Prop.A, 0f, 1f, cd, cl, Curves.linear)
        }
        if (e.rows) rowsIn(e, f)
    }

    private fun tileBack(e: MoveEnds) {
        if (!e.tileBack) return
        val l = ms(Tok.motion_tileBack)
        ramp(Part.Tile, Prop.S, e.tileS0, e.tileS1, 0, l, Curves.cubicOut)
        ramp(Part.Tile, Prop.X, e.tileX0, e.tileX1, 0, l, Curves.cubicOut)
        ramp(Part.Tile, Prop.Y, e.tileY0, e.tileY1, 0, l, Curves.cubicOut)
        set(Part.Tile, Prop.A, 1f)
    }

    private fun tileIn(e: MoveEnds) {
        val td = ms(Tok.motion_t3mTileDelay)
        val tl = ms(Tok.motion_t3mTile)
        ramp(Part.Tile, Prop.S, e.tileS0, e.tileS1, td, tl, Curves.cubicOut)
        ramp(Part.Tile, Prop.X, e.tileX0, e.tileX1, td, tl, Curves.cubicOut)
        ramp(Part.Tile, Prop.Y, e.tileY0, e.tileY1, td, tl, Curves.cubicOut)
        set(Part.Tile, Prop.A, 1f)
    }

    private fun grounds(e: MoveEnds) {
        val g = ms(Tok.motion_ground)
        if (e.grounds) {
            ramp(Part.LeaveGround, Prop.A, 1f, 0f, 0, g, Curves.linear)
            ramp(Part.Ground, Prop.A, 0f, 1f, 0, g, Curves.linear)
        } else {
            set(Part.LeaveGround, Prop.A, 1f)
        }
    }

    private fun fadeOrStep(p: Part, dur: Int, fade: Boolean) {
        if (fade) ramp(p, Prop.A, 1f, 0f, 0, dur, Curves.linear) else step(p, Prop.A, 1f, 0f, 0)
    }

    private fun leavePage(e: MoveEnds, f: MoveForms, dur: Int, header: Boolean) {
        fadeOrStep(Part.Leave, dur, f.leaveFade)
        if (header) fadeOrStep(Part.LeaveHeader, dur, f.leaveFade)
        if (e.grounds) ramp(Part.LeaveGround, Prop.A, 1f, 0f, 0, if (f.leaveFade) dur else 0, Curves.linear) else set(Part.LeaveGround, Prop.A, 1f)
    }

    private fun leaveGround(e: MoveEnds, f: MoveForms) {
        if (e.grounds) ramp(Part.LeaveGround, Prop.A, 1f, 0f, 0, if (f.leaveFade) ms(Tok.motion_leave) else 0, Curves.linear) else set(Part.LeaveGround, Prop.A, 1f)
    }

    private fun popGrounds(e: MoveEnds) {
        if (e.grounds) {
            ramp(Part.LeaveGround, Prop.A, 1f, 0f, 0, ms(Tok.motion_ground), Curves.linear)
            ramp(Part.Ground, Prop.A, 0f, 1f, ms(Tok.motion_groundDelay), ms(Tok.motion_ground), Curves.linear)
        } else {
            set(Part.LeaveGround, Prop.A, 1f)
        }
    }

    private fun colLegs(e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        cut = true
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        fadeOrStep(Part.LeaveHeader, ms(Tok.motion_leave), f.leaveFade)
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        grounds(e)
        var we = max(ms(Tok.motion_t3mLabelDelay) + ms(Tok.motion_t3mLabel), ms(Tok.motion_t3mBaseDelay) + ms(Tok.motion_t3mBase))
        if (e.word) {
            wordEnd = wordLegs(WordLeg.Grow, e)
            we = wordEnd
        }
        step(Part.Header, Prop.A, 0f, 1f, we)
        tie(Part.Header)
        val s = last(Part.Word, Prop.S)
        val from = s?.end ?: (ms(Tok.motion_t3mLabelDelay) + ms(Tok.motion_t3mLabel))
        if (f.t3mFade) ramp(Part.Content, Prop.A, 0f, 1f, from, ms(Tok.motion_t3cFade), Curves.linear) else step(Part.Content, Prop.A, 0f, 1f, from)
        set(Part.Content, Prop.S, 1f)
    }

    private fun artLegs(e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        grounds(e)
        tileIn(e)
        if (f.t3mFade) {
            ramp(Part.Content, Prop.A, 0f, 1f, Moves.kT3aIn, Moves.kT3aLand - Moves.kT3aIn, Curves.linear)
        } else {
            step(Part.Content, Prop.A, 0f, 1f, Moves.kT3aIn)
        }
        set(Part.Content, Prop.S, 1f)
        val rest = e.tileY1 + e.tileS1 / HALF
        val t = ArrayList<Int>()
        val v = ArrayList<Float>()
        var at = 0
        while (at < Moves.kT3aLand) {
            val bottom = value(Part.Tile, Prop.Y, at.toLong()) + value(Part.Tile, Prop.S, at.toLong()) / HALF
            t += at
            v += (bottom - rest) * Moves.kT3aRide
            at += Moves.kTail
        }
        t += Moves.kT3aLand
        v += 0f
        keys(Part.Content, Prop.Y, t.toIntArray(), v.toFloatArray(), Curves.linear, 0)
        if (e.colHold) {
            step(Part.Header, Prop.A, 0f, 1f, 0)
            return
        }
        var we = Moves.kT3aLand
        if (e.word) {
            wordEnd = wordLegs(WordLeg.Grow, e)
            we = wordEnd
        }
        step(Part.Header, Prop.A, 0f, 1f, we)
        tie(Part.Header)
    }

    private fun zoomLegs(e: MoveEnds, s: Keys?, a: Keys, px: Float, header: Int) {
        if (s != null) keys(Part.Zoom, Prop.S, s) else erase(Part.Zoom, Prop.S)
        stepKeys(Part.Zoom, Prop.A, a)
        set(Part.Zoom, Prop.PX, px * e.sw)
        set(Part.Zoom, Prop.PY, e.zoomBase)
        step(Part.Header, Prop.A, 0f, 1f, header)
    }

    private fun artBackLegs(e: MoveEnds, f: MoveForms) {
        val fade = ms(Tok.motion_t4aFade)
        fadeOrStep(Part.Leave, fade, f.leaveFade)
        fadeOrStep(Part.LeaveHeader, fade, f.leaveFade)
        set(Part.LeaveGround, Prop.A, 1f)
        tileBack(e)
        if (e.colHold) {
            swap = 0
            keys(Part.Content, Prop.A, Moves.kT4aCut)
            set(Part.Content, Prop.S, 1f)
            set(Part.Pivots, Prop.A, 1f)
            set(Part.Ground, Prop.A, 1f)
            step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
            step(Part.Header, Prop.A, 0f, 1f, 0)
            return
        }
        swap = ms(Tok.motion_swap)
        if (e.word) wordEnd = wordLegs(WordLeg.Shrink, e)
        pageIn(e, f)
    }

    private fun pageZoom(e: MoveEnds) {
        swap = ms(Tok.motion_swap)
        for (p in listOf(Part.Leave, Part.LeaveHeader, Part.LeavePivots)) step(p, Prop.A, 1f, 0f, Moves.kT3zCut)
        set(Part.LeaveGround, Prop.A, 1f)
        keys(Part.Content, Prop.S, Moves.kT3zScale)
        stepKeys(Part.Content, Prop.A, Moves.kT3zA)
        keys(Part.Content, Prop.PX, Moves.kT3zX.scaled(e.sw))
        set(Part.Content, Prop.PY, Moves.kT3zY * e.sh)
        step(Part.Header, Prop.A, 0f, 1f, Moves.kT3zLand)
        step(Part.Pivots, Prop.A, 0f, 1f, Moves.kT3zLand)
        hold(Part.Header)
        hold(Part.Pivots)
        if (e.grounds) ramp(Part.Ground, Prop.A, 0f, 1f, ms(Tok.motion_groundDelay), ms(Tok.motion_ground), Curves.linear)
    }

    private fun pageFade(e: MoveEnds) {
        swap = ms(Tok.motion_swap)
        for (p in listOf(Part.Leave, Part.LeaveHeader, Part.LeavePivots)) keys(p, Prop.A, Moves.kT4zA)
        ramp(Part.Leave, Prop.Y, 0f, Moves.kT4zDrop * e.pxU, 0, Moves.kT4zDropLen, Curves.linear)
        set(Part.LeaveGround, Prop.A, 1f)
        for (p in listOf(Part.Header, Part.Pivots)) {
            step(p, Prop.A, 0f, Moves.kT4zHeader0, Moves.kT4zHeader)
            append(MoveLeg(p, Prop.A, Moves.kT4zHeader0, 1f, Moves.kT4zHeader, Moves.kT4zHeaderLand - Moves.kT4zHeader))
        }
        keys(Part.Content, Prop.A, Moves.kT4zCut)
        set(Part.Content, Prop.S, 1f)
        if (e.grounds) ramp(Part.Ground, Prop.A, 0f, 1f, ms(Tok.motion_groundDelay), ms(Tok.motion_ground), Curves.linear)
    }

    private fun rowsOut(e: MoveEnds, home: Boolean) {
        if (!e.leaveRows) return
        if (home) {
            keys(Part.Leave, Prop.S, Moves.kT4hPitch)
            keys(Part.Leave, Prop.X, Moves.kT4hDx.scaled(e.pxU))
            return
        }
        keys(Part.Leave, Prop.S, Moves.kT4RowPitch)
        keys(Part.Leave, Prop.X, Moves.kT4RowDx.scaled(e.pxU))
        keys(Part.Leave, Prop.Y, Moves.kT4RowDy.scaled(e.pxU))
    }

    private fun rowPush(e: MoveEnds, f: MoveForms) {
        cut = true
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        if (e.lhead) {
            step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
            keys(Part.LeaveWord, Prop.S, Moves.kT3OldHeader)
            ramp(Part.LeaveWord, Prop.Y, 0f, Moves.kT3OldRise * e.pxU, 0, Moves.kT3OldOut, Curves.linear)
            set(Part.LeaveWord, Prop.A, 1f)
            step(Part.LeaveWord, Prop.A, 1f, 0f, Moves.kT3OldOut)
            set(Part.LeaveWord, Prop.PX, Moves.kT3OldHeaderX * e.sw)
            set(Part.LeaveWord, Prop.PY, e.lheadBase)
        } else if (e.morphHeaderOut) {
            step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        }
        leaveGround(e, f)
        wordEnd = wordLegs(WordLeg.Push, e)
        pageIn(e, f)
        if (e.rows && f.content != ContentForm.Rest) {
            val r = Moves.kT3Rows
            keys(Part.Content, Prop.S, r)
            keys(Part.Content, Prop.X, Keys(r.t, FloatArray(r.v.size) { e.rowsDx * ((1 - r.v[it]) / (1 - Tok.motion_rowsDy)) }))
        }
        step(Part.Header, Prop.A, 0f, 1f, wordEnd)
        tie(Part.Header)
    }

    private fun pivotPush(e: MoveEnds, f: MoveForms) {
        if (f.leaveFade) {
            keys(Part.Leave, Prop.S, Moves.kT3List)
            keys(Part.Leave, Prop.A, Moves.kT3ListA)
            set(Part.Leave, Prop.PX, e.tapX)
            set(Part.Leave, Prop.PY, e.tapY)
        } else {
            step(Part.Leave, Prop.A, 1f, 0f, 0)
        }
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        if (e.lhead) {
            step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
            keys(Part.LeaveWord, Prop.S, Moves.kT3PivHeader)
            ramp(Part.LeaveWord, Prop.Y, 0f, Moves.kT3PivRise * e.pxU, 0, Moves.kT3PivRiseEnd, Curves.linear)
            keys(Part.LeaveWord, Prop.A, Moves.kT3PivHeaderA)
            set(Part.LeaveWord, Prop.PX, Moves.kT3PivHeaderX * e.sw)
            set(Part.LeaveWord, Prop.PY, e.lheadBase)
        } else if (e.morphHeaderOut) {
            step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        }
        if (e.grounds) {
            ramp(Part.LeaveGround, Prop.A, 1f, 0f, 0, if (f.leaveFade) ms(Tok.motion_leave) else 0, Curves.linear)
            ramp(Part.Ground, Prop.A, 0f, 1f, ms(Tok.motion_groundDelay), ms(Tok.motion_ground), Curves.linear)
        } else {
            set(Part.LeaveGround, Prop.A, 1f)
        }
        wordEnd = wordLegs(WordLeg.Push, e)
        step(Part.Header, Prop.A, 0f, 1f, wordEnd)
        tie(Part.Header)
        if (e.thumb) {
            val k = Moves.kT3Thumb
            keys(Part.Tile, Prop.S, Keys(k.t, FloatArray(k.v.size) { e.tileS0 + (e.tileS1 - e.tileS0) * k.v[it] }))
            keys(Part.Tile, Prop.X, Keys(k.t, FloatArray(k.v.size) { e.tileX0 + (e.tileX1 - e.tileX0) * k.v[it] }))
            keys(Part.Tile, Prop.Y, Keys(k.t, FloatArray(k.v.size) { e.tileY0 + (e.tileY1 - e.tileY0) * k.v[it] }))
            set(Part.Tile, Prop.A, 1f)
        }
        if (e.pivots) {
            keys(Part.Pivots, Prop.S, Moves.kT3PivRow)
            step(Part.Pivots, Prop.A, 0f, Moves.kT3PivRowA0, Moves.kT3PivRowIn)
            append(MoveLeg(Part.Pivots, Prop.A, Moves.kT3PivRowA0, 1f, Moves.kT3PivRowIn, Moves.kT3PivRowLand - Moves.kT3PivRowIn))
            set(Part.Pivots, Prop.PX, Moves.kT3PivRowX * e.sw)
            set(Part.Pivots, Prop.PY, Moves.kT3PivRowY * e.sh)
        }
        step(Part.Content, Prop.A, 0f, 1f, Moves.kT3PivContent)
        set(Part.Content, Prop.S, 1f)
    }

    private fun rowPop(e: MoveEnds, f: MoveForms) {
        rowsOut(e, false)
        if (f.leaveFade) {
            keys(Part.Leave, Prop.A, Moves.kT4RowA)
            if (f.chromeSlide) pivotsOut(e)
            keys(Part.LeavePivots, Prop.A, Moves.kT4PivPivotsA)
        } else {
            step(Part.Leave, Prop.A, 1f, 0f, 0)
            step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        }
        step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        popGrounds(e)
        wordEnd = wordLegs(WordLeg.Pop, e)
        if (e.zoom && f.headerZoom) {
            zoomLegs(e, Moves.kT4RowZoom, Moves.kT4RowZoomA, Moves.kT4RowZoomX, Moves.kT4RowHeader)
        } else {
            step(Part.Header, Prop.A, 0f, 1f, Moves.kT4RowHeader)
        }
        if (e.pivots) ramp(Part.Pivots, Prop.A, 0f, 1f, Moves.kT4RowIn, Moves.kT4RowInLen, Curves.easeOut)
        ramp(Part.Content, Prop.A, 0f, 1f, Moves.kT4RowIn, Moves.kT4RowInLen, Curves.easeOut)
        set(Part.Content, Prop.S, 1f)
    }

    private fun pivotsOut(e: MoveEnds) {
        ramp(Part.LeavePivots, Prop.X, 0f, Moves.kT4PivPivotsDx * e.pxU, 0, Moves.kT4PivPivotsMove, Curves.easeOut)
        ramp(Part.LeavePivots, Prop.Y, 0f, Moves.kT4PivPivotsDy * e.pxU, 0, Moves.kT4PivPivotsMove, Curves.easeOut)
    }

    private fun pivotPop(e: MoveEnds, f: MoveForms) {
        if (f.leaveFade) keys(Part.Leave, Prop.A, Moves.kT4PivLeave) else step(Part.Leave, Prop.A, 1f, 0f, 0)
        if (f.chromeSlide) pivotsOut(e)
        keys(Part.LeavePivots, Prop.A, Moves.kT4PivPivotsA)
        step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        popGrounds(e)
        wordEnd = wordLegs(WordLeg.Pop, e)
        if (e.zoom && f.headerZoom) {
            zoomLegs(e, null, Moves.kT4PivZoomA, Moves.kT4PivZoomX, Moves.kT4PivHeader)
            ramp(Part.Zoom, Prop.S, Tok.motion_headerZoom, 1f, Moves.kT4PivZoomIn, Moves.kT4PivHeader - Moves.kT4PivZoomIn, Curves.easeOut)
        } else {
            step(Part.Header, Prop.A, 0f, 1f, Moves.kT4PivHeader)
        }
        if (e.pivots) keys(Part.Pivots, Prop.A, Moves.kT4PivPivots)
        stepKeys(Part.Content, Prop.A, Moves.kT4PivRows)
        set(Part.Content, Prop.S, 1f)
    }

    private fun pushPop(k: MoveKind, e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        val row = e.morph && e.morphKind == MorphKind.Row
        val pivot = e.morph && e.morphKind == MorphKind.Pivot
        when {
            k == MoveKind.T3 && row -> return rowPush(e, f)
            k == MoveKind.T3 && pivot -> return pivotPush(e, f)
            k == MoveKind.T4 && row -> return rowPop(e, f)
            k == MoveKind.T4 && pivot -> return pivotPop(e, f)
        }
        val leave = ms(Tok.motion_leave)
        if (k == MoveKind.T3 && e.npQueue) {
            cut = true
            for (p in listOf(Part.Leave, Part.LeaveHeader, Part.LeavePivots)) step(p, Prop.A, 1f, 0f, 0)
            leaveGround(e, f)
        } else {
            leavePage(e, f, leave, true)
            fadeOrStep(Part.LeavePivots, leave, f.leaveFade)
        }
        if (k == MoveKind.T4m && e.word) wordEnd = wordLegs(WordLeg.Shrink, e)
        pageIn(e, f)
    }

    private fun t3m(e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        fadeOrStep(Part.LeaveHeader, ms(Tok.motion_leave), f.leaveFade)
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        grounds(e)
        tileIn(e)
        var we = max(ms(Tok.motion_t3mLabelDelay) + ms(Tok.motion_t3mLabel), ms(Tok.motion_t3mBaseDelay) + ms(Tok.motion_t3mBase))
        if (e.word) {
            wordEnd = wordLegs(WordLeg.Grow, e)
            we = wordEnd
        }
        step(Part.Header, Prop.A, 0f, 1f, we)
        tie(Part.Header)
        val fd = ms(Tok.motion_t3mFadeDelay)
        if (f.t3mFade) ramp(Part.Content, Prop.A, 0f, 1f, fd, ms(Tok.motion_t3mFade), Curves.linear) else step(Part.Content, Prop.A, 0f, 1f, fd)
        set(Part.Content, Prop.S, 1f)
    }

    private fun t2(k: MoveKind, e: MoveEnds, f: MoveForms) {
        val word = k == MoveKind.T2 && e.word
        swap = ms(if (word) Tok.motion_t2Swap else Tok.motion_swap)
        step(Part.Home, Prop.A, 1f, 0f, 0)
        ramp(Part.Peek, Prop.A, e.peek0, 0f, 0, ms(Tok.motion_t2Peek), Curves.linear)
        if (!word) {
            pageIn(e, f)
            return
        }
        wordEnd = wordLegs(WordLeg.HomeOpen, e)
        step(Part.Header, Prop.A, 0f, 1f, wordEnd)
        tie(Part.Header)
        tie(Part.Face)
        if (e.pivots && f.chromeSlide) {
            keys(Part.Pivots, Prop.S, Moves.kT2PivS)
            keys(Part.Pivots, Prop.X, Moves.kT2PivX.scaled(e.pxU))
            stepKeys(Part.Pivots, Prop.A, Moves.kT2PivA)
            set(Part.Pivots, Prop.PX, Tok.motion_t2PivotX * e.pxU)
            set(Part.Pivots, Prop.PY, f.t2PivotY * e.pxU)
        } else if (e.pivots) {
            step(Part.Pivots, Prop.A, 0f, 1f, Moves.kT2PivA.t.last())
        }
        if (e.rows) {
            rowsIn(e, f)
            return
        }
        ramp(Part.Ground, Prop.A, 0f, 1f, ms(Tok.motion_t2GroundDelay), ms(Tok.motion_t2Ground), Curves.linear)
        val cd = ms(Tok.motion_t2ContentDelay)
        val cl = ms(Tok.motion_t2Content)
        if (f.content == ContentForm.Rest) {
            step(Part.Content, Prop.A, 0f, 1f, cd + cl)
        } else {
            ramp(Part.Content, Prop.S, Tok.motion_contentScale, 1f, cd, cl, Curves.easeOut)
            val ad = ms(Tok.motion_t2FadeDelay)
            if (f.content == ContentForm.Step) step(Part.Content, Prop.A, 0f, 1f, ad) else ramp(Part.Content, Prop.A, 0f, 1f, ad, ms(Tok.motion_t2Fade), Curves.linear)
        }
    }

    private fun t2w(e: MoveEnds) {
        swap = SLIDE_OUT
        val c = Curves.expOut6
        ramp(Part.Home, Prop.A, 1f, 0f, 0, SLIDE_OUT, c)
        ramp(Part.Home, Prop.X, 0f, -e.slideX, 0, SLIDE_OUT, c)
        set(Part.Peek, Prop.A, e.peek0)
        for (p in listOf(Part.Content, Part.Header, Part.Pivots, Part.Ground)) ramp(p, Prop.A, 0f, 1f, SLIDE_OUT, SLIDE_IN, c)
        ramp(Part.Content, Prop.X, e.slideX, 0f, SLIDE_OUT, SLIDE_IN, c)
    }

    private fun t4h(k: MoveKind, e: MoveEnds, f: MoveForms) {
        val word = k == MoveKind.T4h && e.word
        val pl = ms(Tok.motion_t4hPivot)
        val cl = ms(Tok.motion_t4hContent)
        swap = if (word) ms(Tok.motion_t4hSwap) else cl
        leavePage(e, f, cl, !word)
        rowsOut(e, true)
        when {
            f.leaveFade && f.chromeSlide -> {
                ramp(Part.LeavePivots, Prop.X, 0f, e.pivotX, 0, pl, Curves.easeIn)
                ramp(Part.LeavePivots, Prop.Y, 0f, e.pivotY, 0, pl, Curves.easeIn)
                ramp(Part.LeavePivots, Prop.A, 1f, 0f, 0, pl, Curves.easeIn)
            }
            f.leaveFade -> ramp(Part.LeavePivots, Prop.A, 1f, 0f, 0, pl, Curves.easeIn)
            else -> step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        }
        val hd = swap
        val hl = if (word) ms(Tok.motion_t4hItems) else Moves.kT4nItems
        ramp(Part.Home, Prop.A, 0f, 1f, hd, hl, Curves.linear)
        ramp(Part.Peek, Prop.A, 0f, e.peek1, ms(Tok.motion_t4hPeekDelay), ms(Tok.motion_t4hPeek), Curves.linear)
        if (word) {
            wordEnd = wordLegs(WordLeg.HomeBack, e)
            step(Part.Look, Prop.A, 0f, e.look, hd)
            append(MoveLeg(Part.Look, Prop.A, e.look, 1f, hd, hl))
            tie(Part.Look)
            tie(Part.Face)
            ramp(Part.Sub, Prop.A, 0f, e.sub, ms(Tok.motion_t4hSubDelay), ms(Tok.motion_t4hSub), Curves.easeOut)
        } else {
            ramp(Part.Sub, Prop.A, 0f, e.sub, hd, hl, Curves.linear)
        }
    }

    private fun npLegs(k: MoveKind, e: MoveEnds, f: MoveForms) {
        if (k == MoveKind.T8) {
            val out = ms(Old.motion_t8OutEnd)
            val inAt = out + ms(Old.motion_npDark)
            ramp(Part.NpOut, Prop.R, 0f, Tok.motion_t8OutAngle, ms(Tok.motion_t8OutDelay), ms(Tok.motion_t8Out), Curves.powIn15)
            step(Part.NpOut, Prop.A, 1f, 0f, out)
            when {
                e.npCut -> step(Part.NpUnder, Prop.A, 0f, 1f, inAt)
                e.npForm == NpForm.Front -> {
                    ramp(Part.NpUnder, Prop.A, 0f, 1f, inAt, ms(Tok.motion_t8InFade), Curves.linear)
                    ramp(Part.NpUnder, Prop.R, Old.motion_t8InAngle, 0f, inAt + ms(Old.motion_t8InDelay), ms(Old.motion_t8In), Curves.cubicOut)
                    val sd = inAt + ms(Old.motion_t8SlideDelay)
                    val sl = ms(Old.motion_t8Slide)
                    ramp(Part.NpUnder, Prop.X, e.npX, 0f, sd, sl, Curves.cubicOut)
                    ramp(Part.NpUnder, Prop.Y, e.npY, 0f, sd, sl, Curves.cubicOut)
                }
                else -> {
                    ramp(Part.NpUnder, Prop.A, 0f, 1f, inAt + ms(Old.motion_t8PageFadeDelay), ms(Old.motion_t8PageFade), Curves.linear)
                    ramp(Part.NpUnder, Prop.X, e.npX, 0f, inAt, ms(Old.motion_t8Page), Curves.cubicOut)
                }
            }
            return
        }
        when (e.npForm) {
            NpForm.Page -> fadeOrStep(Part.NpLeave, ms(Old.motion_t7Leave), f.leaveFade)
            NpForm.Front -> {
                val sh = ms(Old.motion_t7PaneShift)
                val st = ms(Old.motion_t7PaneStep)
                ramp(Part.NpLeave, Prop.X, 0f, e.npLeft, 0, sh, Curves.linear)
                ramp(Part.NpEdge, Prop.X, 0f, e.npRight, 0, st, Curves.linear)
                ramp(Part.NpEdge, Prop.Y, 0f, e.npBack - e.npRight, st, st, Curves.linear)
                ramp(Part.NpPeek, Prop.A, 1f, 0f, 0, sh, Curves.linear)
                ramp(Part.NpLeave, Prop.R, 0f, Old.motion_t7PaneAngle, ms(Old.motion_t7PaneDelay), ms(Old.motion_t7PaneTurn), Curves.powIn15)
                step(Part.NpLeave, Prop.A, 1f, 0f, ms(Tok.motion_t7PaneEnd))
            }
            NpForm.Ring -> if (f.leaveFade) {
                val rl = ms(Old.motion_t7RingLeave)
                val md = ms(Old.motion_t7RingMusicDelay)
                val ml = ms(Old.motion_t7RingMusic)
                ramp(Part.NpLeave, Prop.X, 0f, e.npX, 0, rl, Curves.linear)
                ramp(Part.NpLeave, Prop.A, 1f, 0f, 0, rl, Curves.linear)
                ramp(Part.NpPeek, Prop.A, 1f, 0f, 0, rl, Curves.linear)
                ramp(Part.NpMusic, Prop.X, 0f, e.npX, md, ml, Curves.linear)
                ramp(Part.NpMusic, Prop.A, 1f, 0f, md, ml, Curves.linear)
            } else {
                step(Part.NpLeave, Prop.A, 1f, 0f, 0)
                step(Part.NpPeek, Prop.A, 1f, 0f, 0)
                step(Part.NpMusic, Prop.A, 1f, 0f, 0)
            }
        }
        val ink = npInk(e.npForm, f)
        if (e.npCut) {
            step(Part.NpIn, Prop.A, 0f, 1f, ink)
            step(Part.NpPhoto, Prop.A, 0f, 1f, ink)
            return
        }
        ramp(Part.NpIn, Prop.A, 0f, 1f, ink, ms(Tok.motion_t7Fade), Curves.easeOut)
        ramp(Part.NpIn, Prop.R, Old.motion_t7Angle, 0f, ink + ms(Old.motion_t7TurnDelay), ms(Old.motion_t7Turn), Curves.quartOut)
        step(Part.NpPhoto, Prop.A, 0f, 1f, ink + ms(Tok.motion_t7Rest))
    }

    fun start(k: MoveKind, t0: Long, e: MoveEnds, f: MoveForms) {
        kind = k
        this.t0 = t0
        live = true
        reversed = false
        morph = f.wordMorph
        list = mutableListOf()
        offs = mutableListOf()
        for (row in base) for (q in row.indices) row[q] = if (q == Prop.S.ordinal) 1f else 0f
        swap = 0
        end = 0
        wordEnd = 0
        endAtMs = e.endAt
        held = mutableSetOf()
        tied = mutableSetOf()
        cut = false
        when (k) {
            MoveKind.T3, MoveKind.T4, MoveKind.T4m -> pushPop(k, e, f)
            MoveKind.T3c -> colLegs(e, f)
            MoveKind.T3a -> artLegs(e, f)
            MoveKind.T4a -> artBackLegs(e, f)
            MoveKind.T3z -> pageZoom(e)
            MoveKind.T4z -> pageFade(e)
            MoveKind.T3m -> t3m(e, f)
            MoveKind.T2, MoveKind.T2n -> t2(k, e, f)
            MoveKind.T2w -> t2w(e)
            MoveKind.T4h, MoveKind.T4n -> t4h(k, e, f)
            MoveKind.T7, MoveKind.T8 -> npLegs(k, e, f)
            MoveKind.None -> Unit
        }
        if (k != MoveKind.T3m && k != MoveKind.T3a && list.any { it.part == Part.Content && it.prop == Prop.A }) hold(Part.Content)
        finish()
    }

    fun reverse(now: Long) {
        val e = max(now - t0, 0L)
        var out = 0
        val back = mutableListOf<MoveLeg>()
        for (l in list) {
            val f = leg(l.part, l.prop)
            if (f !== l) continue
            val z = last(l.part, l.prop) ?: l
            val ran = z.end.toLong() - f.delay
            val v = value(l.part, l.prop, e)
            val r = if (ran <= 0) {
                MoveLeg(l.part, l.prop, v, f.from, if (e >= f.delay) (e - f.delay).toInt() else 0, 0)
            } else {
                MoveLeg(l.part, l.prop, v, f.from, 0, (e - f.delay).coerceIn(0L, ran).toInt())
            }
            out = max(out, r.end)
            back += r
        }
        list = back
        offs = mutableListOf()
        t0 = now
        swap = 0
        end = out
        endAtMs = 0
        held = mutableSetOf()
        tied = mutableSetOf()
        reversed = true
    }

    fun shift(p: Part, by: Int, from: Long = Long.MIN_VALUE) {
        if (by <= 0) return
        for (l in list) if (l.part == p && (held(p) || t0 + l.delay >= from)) l.delay += by
        finish()
    }

    fun release(p: Part, at: Long): Int {
        if (!held(p)) return 0
        held -= p
        val tail = Moves.kTail.toLong()
        val shown = at - t0
        val r = max(shown - tail, 0L)
        var first = Long.MAX_VALUE
        var firstA = Long.MAX_VALUE
        for (l in list) {
            if (l.part != p || l.pin != 0) continue
            first = min(first, l.delay.toLong())
            if (l.prop == Prop.A) firstA = min(firstA, l.delay.toLong())
        }
        if (cut && firstA != Long.MAX_VALUE) first = firstA
        val d = if (first == Long.MAX_VALUE) 0 else max(0L, r - first).toInt()
        for (l in list) {
            if (l.part != p) continue
            if (l.pin != 0) {
                val e = l.end.toLong()
                val s = max(l.delay.toLong(), r)
                l.delay = s.toInt()
                l.dur = max(e - s, l.pin.toLong()).toInt()
            } else {
                l.delay += d
            }
        }
        if (p == Part.Content && !cut) {
            for (q in Prop.entries) {
                val z = last(Part.Leave, q) ?: continue
                if (z.end >= shown + tail) continue
                if (z.dur <= 0) z.delay = (shown + tail).toInt() else z.dur = (shown + tail - z.delay).toInt()
            }
        }
        finish()
        return d
    }

    fun release(group: Set<Part>, at: Long): Int {
        var d = 0
        for (p in Part.entries) if (p in group) d = max(d, release(p, at))
        return d
    }

    fun releaseWords(at: Long): Int {
        if (held.none { it in wordGroup }) return 0
        held.removeAll(wordGroup)
        val r = max(at - t0, 0L)
        val first = list.filter { it.part in wordGroup }.minOfOrNull { it.delay.toLong() }
        val d = if (first == null) 0 else max(0L, r - first).toInt()
        for (l in list) if (l.part in wordGroup || l.part in tied) l.delay += d
        wordEnd += d
        finish()
        return d
    }

    fun npPop(delay: Int) {
        for (l in list) {
            if (l.part == Part.Content && l.delay < delay) {
                val e = l.end
                l.delay = delay
                l.dur = max(0, e - delay)
            }
        }
        finish()
    }

    fun npShift(by: Int) {
        if (by <= 0) return
        for (l in list) if (l.part == Part.NpIn || l.part == Part.NpPhoto) l.delay += by
        if (endAtMs > 0) endAtMs += by
        finish()
    }

    companion object {
        fun popAnchor(k: MoveKind, top: Int, h: Int, stdPpu: Float): Int = if (k == MoveKind.T4) top else h - px(Tok.motion_t4hRowsYa, stdPpu)
    }
}
