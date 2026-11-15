package net.seilent.funekit.kit.motion

import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.OldBoard
import net.seilent.funekit.gen.Moves
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import kotlin.math.max
import kotlin.math.min

private const val TAIL = 17
private const val SLIDE_OUT = 300
private const val SLIDE_IN = 500

enum class MoveKind { T2, T2n, T2w, T4h, T4n, T3, T4, T3m, T4m, T7, T8, T3c, T3a, T4a, None }

enum class Part {
    Leave, LeaveHeader, LeavePivots, LeaveGround, LeaveWord, Content, Header, Zoom, Pivots, Ground, Word, Look, Home,
    Sub, Peek, Tile, NpIn, NpOut, NpLeave, NpEdge, NpPeek, NpMusic, NpUnder, NpPhoto,
}

enum class Prop { A, S, X, Y, R }

enum class ContentForm { Zoom, Step, Rest }

enum class WordLeg { HomeOpen, HomeBack, Push, Pop, Grow, Shrink, Hold }

enum class MorphKind { None, Pivot, Row }

enum class NpForm { Page, Front, Ring }

private fun ms(v: Float): Int = v.toInt()

private fun formOf(mode: String): ContentForm = when (mode) {
    "step" -> ContentForm.Step
    "rest" -> ContentForm.Rest
    else -> ContentForm.Zoom
}

class MoveLeg(
    val part: Part,
    val prop: Prop,
    var from: Float,
    val to: Float,
    var delay: Int,
    var dur: Int,
    val curve: Curve = Curves.linear,
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
) {
    companion object {
        fun board(b: TokBoard.Board) = MoveForms(
            leaveFade = Tok.motion_leaveFade == "fade",
            content = formOf(Tok.motion_contentZoom),
            chromeSlide = Tok.motion_chromeSlide == "slide",
            headerZoom = Tok.motion_headerForm == "zoom",
            t3mFade = Tok.motion_t3mForm == "fade",
            wordMorph = Tok.motion_wordForm == "morph",
            hubRows = formOf(
                when (b) {
                    TokBoard.r1 -> OldBoard.r1.motion_contentHubRows
                    TokBoard.a105 -> OldBoard.a105.motion_contentHubRows
                    else -> OldBoard.v1.motion_contentHubRows
                },
            ),
            pageRows = formOf(
                when (b) {
                    TokBoard.r1 -> OldBoard.r1.motion_contentPageRows
                    TokBoard.a105 -> OldBoard.a105.motion_contentPageRows
                    else -> OldBoard.v1.motion_contentPageRows
                },
            ),
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
    val rowAscent: Float = 0f,
    val rowBand: Float = 0f,
    val rowsDx: Float = 0f,
    val rowLive: Boolean = false,
    val endAt: Int = 0,
    val npForm: NpForm = NpForm.Page,
    val npCut: Boolean = false,
    val npX: Float = 0f,
    val npY: Float = 0f,
    val npLeft: Float = 0f,
    val npRight: Float = 0f,
    val npBack: Float = 0f,
    val colHold: Boolean = false,
    val tileBack: Boolean = false,
    val leaveRows: Boolean = false,
    val pxU: Float = 0f,
    val slideX: Float = 0f,
)

fun npInk(form: NpForm, f: MoveForms): Int = when (form) {
    NpForm.Front -> ms(Tok.motion_t7PaneInk)
    NpForm.Ring -> (if (f.leaveFade) ms(Old.motion_t7RingLeave) else 0) + ms(Old.motion_t7RingDark)
    NpForm.Page -> ms(Old.motion_t7Leave) + ms(Old.motion_npDark)
}

class PageMove {
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
    val legs: List<MoveLeg> get() = list

    private var swap = 0
    private var end = 0
    private var endAtMs = 0
    private var held = mutableSetOf<Part>()
    private var tied = mutableSetOf<Part>()
    private var morph = true
    private var list = mutableListOf<MoveLeg>()
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

    fun held(p: Part): Boolean = p in held

    private fun tie(p: Part) {
        tied += p
    }

    private fun value(p: Part, q: Prop, at: Long): Float {
        val f = leg(p, q) ?: return base[p.ordinal][q.ordinal]
        var e = at
        if (held(p)) {
            if (p != Part.Word && p != Part.LeaveWord) return base[p.ordinal][q.ordinal]
            e = min(e, f.delay - 1L)
        } else if (p in tied && (held(Part.Word) || held(Part.LeaveWord))) {
            e = min(e, f.delay - 1L)
        } else if (p == Part.Leave && held(Part.Content)) {
            val z = last(p, q) ?: f
            e = min(e, if (z.dur > TAIL) z.end - TAIL.toLong() else z.delay - 1L)
        }
        return (active(p, q, e) ?: f).at(e)
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

    private fun erase(p: Part, q: Prop) {
        list.removeAll { it.part == p && it.prop == q }
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

    private fun ramp(p: Part, q: Prop, from: Float, to: Float, delay: Int, dur: Int, c: Curve) =
        add(MoveLeg(p, q, from, to, delay, max(dur, 0), c))

    private fun step(p: Part, q: Prop, from: Float, to: Float, delay: Int) = add(MoveLeg(p, q, from, to, delay, 0))

    private fun finish() {
        var e = max(end, swap)
        for (l in list) e = max(e, l.end)
        end = e
        if (endAtMs > 0) end = max(swap, endAtMs)
    }

    private fun box(p: Part, s0: Float, s1: Float, x0: Float, x1: Float, y0: Float, y1: Float, d: Int, sd: Int, sl: Int, xd: Int, xl: Int, yd: Int, yl: Int, sc: Curve, xc: Curve, yc: Curve) {
        if (!morph) return
        ramp(p, Prop.S, s0, s1, d + sd, sl, sc)
        ramp(p, Prop.X, x0, x1, d + xd, xl, xc)
        ramp(p, Prop.Y, y0, y1, d + yd, yl, yc)
    }

    fun wordLegs(k: WordLeg, e: MoveEnds, delay: Int = 0): Int {
        fun pair(sd: Int, sl: Int, xd: Int, xl: Int, yd: Int, yl: Int, sc: Curve, xc: Curve, yc: Curve, ad: Int, al: Int): Int {
            for (p in listOf(Part.LeaveWord, Part.Word)) {
                box(p, e.morphS0, e.morphS1, e.morphX0, e.morphX1, e.morphY0, e.morphY1, delay, sd, sl, xd, xl, yd, yl, sc, xc, yc)
            }
            ramp(Part.LeaveWord, Prop.A, 1f, 0f, delay + ad, al, Curves.linear)
            ramp(Part.Word, Prop.A, 0f, 1f, delay + ad, al, Curves.linear)
            return delay + maxOf(sd + sl, xd + xl, yd + yl, ad + al)
        }

        fun word(sd: Int, sl: Int, xd: Int, xl: Int, yd: Int, yl: Int, sc: Curve, xc: Curve, yc: Curve, out: Int) {
            box(Part.Word, e.wordS0, e.wordS1, e.wordX0, e.wordX1, e.wordY0, e.wordY1, delay, sd, sl, xd, xl, yd, yl, sc, xc, yc)
            step(Part.Word, Prop.A, 1f, 0f, delay + out)
        }
        val t4md = ms(Tok.motion_t4mDelay)
        return when (k) {
            WordLeg.HomeOpen -> {
                val wl = ms(Tok.motion_t2Word)
                val bd = ms(Tok.motion_t2BaseDelay)
                val bl = ms(Tok.motion_t2Base)
                val out = max(wl, bd + bl)
                if (morph) {
                    val sk = Moves.kT2Scale
                    keys(Part.Word, Prop.S, intArrayOf(0) + sk.t, floatArrayOf(e.wordS0) + sk.v, Curves.linear, delay)
                    ramp(Part.Word, Prop.X, e.wordX0, e.wordX1, delay + bd, bl, Curves.easeOut)
                    val yk = Moves.kT2Base
                    keys(Part.Word, Prop.Y, yk.t, FloatArray(yk.v.size) { lerp(e.wordY0, e.wordY1, yk.v[it]) }, Curves.linear, delay)
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
                    val yk = Moves.kT4hBase
                    keys(Part.Word, Prop.Y, yk.t, FloatArray(yk.v.size) { lerp(e.wordY0, e.wordY1, yk.v[it]) }, Curves.linear, delay)
                }
                step(Part.Word, Prop.A, 1f, 0f, delay + ms(Tok.motion_t4hSwap) + ms(Tok.motion_t4hItems))
                delay + max(Moves.kT4hScaleEnd, bl)
            }
            WordLeg.Push -> if (e.morphKind == MorphKind.Row) {
                val sl = ms(Old.motion_morphScale)
                val pd = ms(Old.motion_morphRowPenDelay)
                val pl = ms(Old.motion_morphRowPen)
                pair(0, sl, pd, pl, 0, sl, Curves.easeOut, Curves.easeOut, Curves.easeOut, 0, pd + pl)
            } else {
                val l = ms(Tok.motion_morphPush)
                pair(t4md, l, t4md, l, t4md, l, Curves.cubicOut, Curves.cubicOut, Curves.cubicOut, t4md, l)
            }
            WordLeg.Pop -> if (e.morphKind == MorphKind.Row) {
                val out = ms(Old.motion_morphRowPop)
                val sd = if (e.retarget) 0 else ms(Old.motion_morphRowPopDelay)
                pair(sd, out - sd, 0, out, 0, out, Curves.powOut15, Curves.powOut15, Curves.powOut15, 0, out)
            } else {
                val l = ms(Tok.motion_morphPop)
                pair(t4md, l, t4md, l, t4md, l, Curves.cubicOut, Curves.cubicOut, Curves.cubicOut, t4md, l)
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
            WordLeg.Hold -> {
                set(Part.Word, Prop.S, e.wordS0)
                set(Part.Word, Prop.X, e.wordX0)
                set(Part.Word, Prop.Y, e.wordY0)
                set(Part.Word, Prop.A, 1f)
                var out = max(end, swap)
                for (l in list) out = max(out, l.end)
                out
            }
        }
    }

    private fun rowsIn(e: MoveEnds, f: MoveForms) {
        val t2 = kind == MoveKind.T2 || kind == MoveKind.T2n
        list.removeAll { it.part == Part.Content }
        set(Part.Content, Prop.S, 1f)
        if (f.content == ContentForm.Rest) {
            step(Part.Content, Prop.A, 0f, 1f, 0)
            step(Part.Leave, Prop.A, 1f, 0f, 0)
        } else {
            val rl = ms(Tok.motion_rows)
            ramp(Part.Content, Prop.S, Tok.motion_rowsDy, 1f, 0, rl, Curves.powOut15)
            ramp(Part.Content, Prop.X, e.rowsDx, 0f, 0, rl, Curves.powOut15)
            when {
                f.content == ContentForm.Step -> step(Part.Content, Prop.A, 0f, 1f, 0)
                t2 -> {
                    val ink = Moves.kT2RowsInk
                    step(Part.Content, Prop.A, 0f, Tok.motion_rowsT2Alpha, ink)
                    append(MoveLeg(Part.Content, Prop.A, Tok.motion_rowsT2Alpha, 1f, ink, ms(Tok.motion_rowsT2Fade), Curves.linear))
                }
                else -> ramp(Part.Content, Prop.A, 0f, 1f, 0, ms(Old.motion_rowsFade), Curves.smoothStep)
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
        list.removeAll { it.part == Part.Content }
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

    private fun pivotsIn(e: MoveEnds, f: MoveForms, pd: Int) {
        val pl = ms(Tok.motion_pivotIn)
        val pf = ms(Tok.motion_pivotFade)
        if (f.chromeSlide) {
            ramp(Part.Pivots, Prop.X, e.pivotX, 0f, pd, pl, Curves.easeOut)
            ramp(Part.Pivots, Prop.Y, e.pivotY, 0f, pd, pl, Curves.easeOut)
            ramp(Part.Pivots, Prop.A, 0f, 1f, pd, pf, Curves.linear)
        } else {
            step(Part.Pivots, Prop.A, 0f, 1f, pd + pl)
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
        if (e.pivots) pivotsIn(e, f, ms(Tok.motion_pivotDelay))
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

    private fun tileLegs(e: MoveEnds, d: Int, l: Int) {
        ramp(Part.Tile, Prop.S, e.tileS0, e.tileS1, d, l, Curves.cubicOut)
        ramp(Part.Tile, Prop.X, e.tileX0, e.tileX1, d, l, Curves.cubicOut)
        ramp(Part.Tile, Prop.Y, e.tileY0, e.tileY1, d, l, Curves.cubicOut)
        set(Part.Tile, Prop.A, 1f)
    }

    private fun tileBack(e: MoveEnds) {
        if (e.tileBack) tileLegs(e, 0, ms(Old.motion_tileBack))
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

    private fun fadeOrStep(p: Part, delay: Int, dur: Int, fade: Boolean) {
        if (fade) ramp(p, Prop.A, 1f, 0f, delay, dur, Curves.linear) else step(p, Prop.A, 1f, 0f, delay)
    }

    private fun colLegs(e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        fadeOrStep(Part.LeaveHeader, 0, ms(Tok.motion_leave), f.leaveFade)
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        grounds(e)
        var we = max(ms(Tok.motion_t3mLabelDelay) + ms(Tok.motion_t3mLabel), ms(Tok.motion_t3mBaseDelay) + ms(Tok.motion_t3mBase))
        if (e.word) {
            wordEnd = wordLegs(WordLeg.Grow, e)
            we = wordEnd
        }
        step(Part.Header, Prop.A, 0f, 1f, we)
        tie(Part.Header)
        val s = leg(Part.Word, Prop.S)
        val from = s?.end ?: (ms(Tok.motion_t3mLabelDelay) + ms(Tok.motion_t3mLabel))
        if (f.t3mFade) ramp(Part.Content, Prop.A, 0f, 1f, from, ms(Old.motion_t3cFade), Curves.linear) else step(Part.Content, Prop.A, 0f, 1f, from)
        set(Part.Content, Prop.S, 1f)
    }

    private fun artLegs(e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        grounds(e)
        tileLegs(e, ms(Tok.motion_t3mTileDelay), ms(Tok.motion_t3mTile))
        val fd = ms(Old.motion_t3aFadeDelay)
        val fl = ms(Old.motion_t3aFade)
        if (f.t3mFade) ramp(Part.Content, Prop.A, 0f, 1f, fd, fl, Curves.linear) else step(Part.Content, Prop.A, 0f, 1f, fd)
        set(Part.Content, Prop.S, 1f)
        var we = fd + fl
        if (e.word) {
            wordEnd = wordLegs(if (e.colHold) WordLeg.Hold else WordLeg.Grow, e)
            we = wordEnd
        }
        step(Part.Header, Prop.A, 0f, 1f, we)
        tie(Part.Header)
    }

    private fun artBackLegs(e: MoveEnds, f: MoveForms) {
        fadeOrStep(Part.Leave, 0, ms(Tok.motion_leave), f.leaveFade)
        step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        set(Part.LeaveGround, Prop.A, 1f)
        tileBack(e)
        if (e.colHold) {
            swap = 0
            set(Part.Content, Prop.A, 1f)
            set(Part.Content, Prop.S, 1f)
            set(Part.Pivots, Prop.A, 1f)
            set(Part.Ground, Prop.A, 1f)
            if (e.word) wordEnd = wordLegs(WordLeg.Hold, e)
            step(Part.Header, Prop.A, 0f, 1f, wordEnd)
            tie(Part.Header)
            return
        }
        swap = ms(Tok.motion_swap)
        if (e.word) wordEnd = wordLegs(WordLeg.Shrink, e)
        pageIn(e, f)
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
            NpForm.Page -> fadeOrStep(Part.NpLeave, 0, ms(Old.motion_t7Leave), f.leaveFade)
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

    private fun leavePage(e: MoveEnds, f: MoveForms, dur: Int, header: Boolean) {
        fadeOrStep(Part.Leave, 0, dur, f.leaveFade)
        if (header) fadeOrStep(Part.LeaveHeader, 0, dur, f.leaveFade)
        if (e.grounds) ramp(Part.LeaveGround, Prop.A, 1f, 0f, 0, if (f.leaveFade) dur else 0, Curves.linear) else set(Part.LeaveGround, Prop.A, 1f)
    }

    private fun rowsOut(e: MoveEnds) {
        if (!e.leaveRows) return
        val s = Moves.kT4hPitch
        keys(Part.Leave, Prop.S, s.t, s.v, Curves.linear, 0)
        val x = Moves.kT4hDx
        keys(Part.Leave, Prop.X, x.t, FloatArray(x.v.size) { x.v[it] * e.pxU }, Curves.linear, 0)
    }

    private fun pushPop(k: MoveKind, e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        val leave = ms(Tok.motion_leave)
        val row = k == MoveKind.T3 && e.morph && e.morphKind == MorphKind.Row
        leavePage(e, f, leave, true)
        fadeOrStep(Part.LeavePivots, 0, leave, f.leaveFade)
        if (k != MoveKind.T4m && e.morph) wordEnd = wordLegs(if (k == MoveKind.T3) WordLeg.Push else WordLeg.Pop, e)
        if (k == MoveKind.T4m && e.word) wordEnd = wordLegs(WordLeg.Shrink, e)
        if (k == MoveKind.T4m) tileBack(e)
        pageIn(e, f)
        if (k != MoveKind.T4m && e.morph) {
            if (e.morphHeaderIn) {
                step(Part.Header, Prop.A, 0f, 1f, wordEnd)
                tie(Part.Header)
            }
            if (e.morphHeaderOut) step(Part.LeaveHeader, Prop.A, 1f, 0f, 0)
        }
        if (!row) return
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        val s = leg(Part.Word, Prop.S)
        val y = leg(Part.Word, Prop.Y)
        var out = wordEnd
        if (s != null && y != null) {
            var t = 0
            while (t < wordEnd) {
                if (y.at(t.toLong()) - e.rowAscent * s.at(t.toLong()) <= e.rowBand) {
                    out = t
                    break
                }
                t += TAIL
            }
        }
        if (e.rowLive) {
            ramp(Part.LeaveHeader, Prop.A, 1f, 0f, 0, wordEnd, Curves.linear)
            ramp(Part.LeavePivots, Prop.A, 1f, 0f, 0, wordEnd, Curves.linear)
        } else {
            step(Part.LeaveHeader, Prop.A, 1f, 0f, out)
            step(Part.LeavePivots, Prop.A, 1f, 0f, out)
        }
        tie(Part.LeaveHeader)
        tie(Part.LeavePivots)
    }

    private fun t3m(e: MoveEnds, f: MoveForms) {
        swap = ms(Tok.motion_swap)
        step(Part.Leave, Prop.A, 1f, 0f, 0)
        fadeOrStep(Part.LeaveHeader, 0, ms(Tok.motion_leave), f.leaveFade)
        step(Part.LeavePivots, Prop.A, 1f, 0f, 0)
        grounds(e)
        tileLegs(e, ms(Tok.motion_t3mTileDelay), ms(Tok.motion_t3mTile))
        var we = max(ms(Tok.motion_t3mLabelDelay) + ms(Tok.motion_t3mLabel), ms(Tok.motion_t3mBaseDelay) + ms(Tok.motion_t3mBase))
        if (e.word) {
            wordEnd = wordLegs(WordLeg.Grow, e)
            we = wordEnd
        }
        step(Part.Header, Prop.A, 0f, 1f, we)
        tie(Part.Header)
        val fd = ms(Old.motion_t3mFadeDelay)
        if (f.t3mFade) ramp(Part.Content, Prop.A, 0f, 1f, fd, ms(Old.motion_t3mFade), Curves.linear) else step(Part.Content, Prop.A, 0f, 1f, fd)
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
        if (e.pivots) pivotsIn(e, f, ms(Old.motion_t2PivotDelay))
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
        swap = ms(Tok.motion_t4hSwap)
        val pl = ms(Tok.motion_t4hPivot)
        leavePage(e, f, ms(Tok.motion_t4hContent), !word)
        rowsOut(e)
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
        val hl = ms(Tok.motion_t4hItems)
        ramp(Part.Home, Prop.A, 0f, 1f, hd, hl, Curves.linear)
        ramp(Part.Peek, Prop.A, 0f, e.peek1, ms(Tok.motion_t4hPeekDelay), ms(Tok.motion_t4hPeek), Curves.linear)
        if (word) {
            wordEnd = wordLegs(WordLeg.HomeBack, e)
            ramp(Part.Look, Prop.A, 0f, 1f, hd, hl, Curves.linear)
            tie(Part.Look)
            ramp(Part.Sub, Prop.A, 0f, e.sub, ms(Tok.motion_t4hSubDelay), ms(Tok.motion_t4hSub), Curves.easeOut)
        } else {
            ramp(Part.Sub, Prop.A, 0f, e.sub, hd, hl, Curves.linear)
        }
    }

    fun start(k: MoveKind, t0: Long, e: MoveEnds, f: MoveForms) {
        kind = k
        this.t0 = t0
        live = true
        reversed = false
        morph = f.wordMorph
        list = mutableListOf()
        for (row in base) for (q in row.indices) row[q] = if (q == Prop.S.ordinal) 1f else 0f
        swap = 0
        end = 0
        wordEnd = 0
        endAtMs = e.endAt
        held = mutableSetOf()
        tied = mutableSetOf()
        when (k) {
            MoveKind.T3, MoveKind.T4, MoveKind.T4m -> pushPop(k, e, f)
            MoveKind.T3c -> colLegs(e, f)
            MoveKind.T3a -> artLegs(e, f)
            MoveKind.T4a -> artBackLegs(e, f)
            MoveKind.T3m -> t3m(e, f)
            MoveKind.T2, MoveKind.T2n -> t2(k, e, f)
            MoveKind.T2w -> t2w(e)
            MoveKind.T4h, MoveKind.T4n -> t4h(k, e, f)
            MoveKind.T7, MoveKind.T8 -> npLegs(k, e, f)
            MoveKind.None -> Unit
        }
        if (k != MoveKind.T3m && list.any { it.part == Part.Content }) hold(Part.Content)
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
        val shown = at - t0
        val r = max(shown - TAIL, 0L)
        val first = list.filter { it.part == p && it.pin == 0 }.minOfOrNull { it.delay.toLong() }
        val d = if (first == null) 0 else max(0L, r - first).toInt()
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
        if (p == Part.Content) {
            for (q in Prop.entries) {
                val z = last(Part.Leave, q) ?: continue
                if (z.end >= shown + TAIL) continue
                if (z.dur <= 0) z.delay = (shown + TAIL).toInt() else z.dur = (shown + TAIL - z.delay).toInt()
            }
        }
        finish()
        return d
    }

    fun releaseWords(at: Long): Int {
        val words = setOf(Part.Word, Part.LeaveWord)
        if (held.none { it in words }) return 0
        held.removeAll(words)
        val r = max(at - t0, 0L)
        val first = list.filter { it.part in words }.minOfOrNull { it.delay.toLong() }
        val d = if (first == null) 0 else max(0L, r - first).toInt()
        for (l in list) if (l.part in words || l.part in tied) l.delay += d
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
}
