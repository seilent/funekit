package net.seilent.funekit.kit.saver

import androidx.compose.ui.graphics.ImageBitmap
import net.seilent.funekit.Weight
import net.seilent.funekit.fx
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.TableCurve
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val STEM_UPEM = 2048L
private const val STEM_LIGHT = 92L
private const val STEM_REGULAR = 164L
private const val STEM_BOLD = 316L
private const val STEM_SEMI = 128L
private const val TEN = 10
private const val MINUTE = 60L
private const val SECOND = 1000L
private const val TWO = 2

internal class FxTween {
    private var start = 0L
    private var dur = 0
    private var from = 0L
    private var to = 0L
    private var curve: TableCurve? = null

    fun set(v: Long) {
        from = v
        to = v
        dur = 0
    }

    fun run(now: Long, f: Long, t: Long, ms: Int, c: TableCurve?) {
        start = now
        dur = ms
        from = f
        to = t
        curve = c
    }

    fun at(now: Long): Long {
        val e = now - start
        val p = if (dur <= 0 || e >= dur) ONE else if (e <= 0) 0L else e * ONE / dur
        if (p >= ONE) return to
        return from + fxMul(to - from, curve?.fx(p) ?: p)
    }

    fun done(now: Long): Boolean = dur <= 0 || now - start >= dur
}

internal fun formatTime(t: Long): String {
    val total = max(0L, t / SECOND)
    val s = total % MINUTE
    return "${total / MINUTE}:" + (if (s < TEN) "0" else "") + s
}

class SaverScenes(
    internal val board: TokBoard.Board,
    internal val ppu: Float,
    internal val widthPx: Int,
    internal val heightPx: Int,
    internal val glyphs: SaverGlyphs,
) {
    internal var specs = specsOf(board)
    internal val ppu64 = fx(ppu)
    private val minText = fx(board.minTextPx)
    private val minStem = fx(board.minStemPx)
    internal val giantCjk = fx(board.saverGiantCjk)
    internal val digits = fx(board.saverDigits)
    internal val giantDigits = fx(board.saverGiantDigits)
    internal val crossGap = fx(board.saverCrossGap)
    internal val touch = fx(board.saverTouch)
    internal val tileTouch = fx(board.saverTileTouch)
    private val wordEntryShift = fx(board.motion_saverWordEntryShift)
    private val tracking = fx(Tok.tracking)
    internal val whoosh = ms(fx(Tok.motion_saverWhoosh)).toLong()
    internal val openHold = secsMs(fx(Tok.motion_saverOpenHold))
    internal val pairBudget = secsMs(fx(Tok.motion_saverPairBudget))
    internal val pass = secsMs(fx(Tok.motion_saverPass))
    internal val tileTouchMs = secsMs(fx(Tok.motion_saverTileTouch))
    internal val delayMin = ms(fx(Tok.motion_saverWordDelayMin)).toLong()
    internal val delayMax = ms(fx(Tok.motion_saverWordDelayMax)).toLong()

    internal var shown = false
    internal var phone = false
    internal var hideAt = -1L
    private val fade = FxTween()
    internal var title = ""
    internal var artist = ""
    internal var album = ""
    internal var wantTitle = ""
    internal var wantArtist = ""
    internal var wantAlbum = ""
    internal var wantPhone = false
    internal var lanes = ArrayList<Lane>()
    internal var crossLeft = 0
    internal var packPasses = 0
    internal var glyphSteps = 0
    internal var redoSteps = 0
    internal var most = 0
    internal var crosses = ArrayList<Cross>()
    internal var stepAt = -1L
    internal var pos: (() -> Long)? = null
    internal var art: ImageBitmap? = null
    internal var seed = 1
    internal var rng = Mt19937()
    internal var scene = SaverScene.E
    internal var pin = -1
    internal var sceneAt = 0L
    internal var gap = 0L
    internal var afterChange = false
    internal var shownAt = -1L
    internal var firstAt = -1L
    internal var cutDue = -1L
    internal var keyUntil = -1L
    internal var startAt = -1L
    internal var timeLays = 0
    internal var trim = true
    internal var cjk = true
    internal var redo = true
    internal var redoKeep = true
    internal var timeKeep = true
    internal var give = true
    internal var entryHold = true
    internal var cut = true
    internal var gen = 0
    internal var lay: SaverLay? = null
    internal var tiles: (SaverTile) -> Unit = {}

    internal fun toPx(u: Long): Long = u * ppu64 shr BITS

    internal fun px(u: Long): Int = ((toPx(u) + HALF) shr BITS).toInt()

    internal fun to64(u: Long): Int = ((toPx(u) + PX64_HALF) shr PX64_BITS).toInt()

    internal fun pxOf(u: Long): Double = toPx(u) / ONE.toDouble()

    private fun stem(w: Weight): Long = when (w) {
        Weight.Light -> STEM_LIGHT
        Weight.Regular -> STEM_REGULAR
        Weight.Bold -> STEM_BOLD
        Weight.SemiLight -> STEM_SEMI
    }

    private fun stemWeight(w: Weight, px: Int): Weight {
        var x = w
        while ((x == Weight.Light || x == Weight.SemiLight) && stem(x) * (px.toLong() shl BITS) / STEM_UPEM < minStem)
            x = if (x == Weight.Light) Weight.SemiLight else Weight.Regular
        return x
    }

    internal fun style(size: Long, w: Weight): SaverStyle {
        val p = ((max(toPx(size), minText) + HALF) shr BITS).toInt()
        return SaverStyle(stemWeight(w, p), p, ((toPx(tracking) * SUB + HALF) shr BITS).toInt(), false)
    }

    internal fun saverCjk(sp: Spec): Boolean = sp.src != Src.First && !sp.top

    internal fun partStyle(sp: Spec, part: Int, cjk: Boolean): SaverStyle =
        style(sp.size, sp.w).copy(weight = if (part != 0) sp.w2 else sp.w, saver = cjk && saverCjk(sp))

    fun show(now: Long, seed: Long) {
        if (shown) return
        shown = true
        shownAt = now
        hideAt = -1
        fade.run(now, fade.at(now), ONE, ms(fx(Tok.motion_saverFade)), null)
        this.seed = seed.toInt()
        startAt = -1
        cutDue = -1
        keyUntil = -1
        afterChange = false
        if (lay?.takeFirst(this, now, seed) == true) return
        rng.seed(this.seed)
        firstAt = now + rng.uniform(delayMin, delayMax)
        begin(draw(false), firstAt, true)
    }

    fun hide(now: Long) {
        if (!shown) return
        for (l in lanes) if (!laneIn(l, now)) l.skip = true
        shown = false
        hideAt = now
        cutDue = -1
        keyUntil = -1
        fade.run(now, fade.at(now), 0, ms(fx(Tok.motion_saver)), Curves.decel)
        gen++
    }

    fun land() {
        shown = false
        hideAt = -1
        fade.set(0)
        lanes.clear()
        startAt = -1
        cutDue = -1
        keyUntil = -1
        gen++
    }

    fun visible(now: Long): Boolean = shown || !fade.done(now)

    fun progress(now: Long): Float = fade.at(now) / ONE.toFloat()

    internal fun hiding(): Boolean = !shown && hideAt >= 0

    fun setTrack(title: String, artist: String, album: String, phone: Boolean) {
        if (title != wantTitle || artist != wantArtist || album != wantAlbum || phone != wantPhone) gen++
        wantTitle = title
        wantArtist = artist
        wantAlbum = album
        wantPhone = phone
        if (shown && lanes.isNotEmpty() && phone == this.phone) return
        if (title == this.title && artist == this.artist && album == this.album && phone == this.phone) return
        val t = title != this.title
        val a = artist != this.artist
        val b = album != this.album
        fun one(x: Src) = (t && (x == Src.Title || x == Src.First)) || (a && x == Src.Artist) || (b && x == Src.Album)
        this.title = title
        this.artist = artist
        this.album = album
        this.phone = phone
        for (l in lanes) {
            if (one(l.spec.src) || one(l.spec.src2)) {
                l.span[0] = Span()
                l.span[1] = Span()
                l.kept = -1
                l.cutBy = -1
                l.cutFell = false
                lay(l)
                l.skip = l.skip || (l.spec.src != Src.Art && l.t.g.isEmpty())
            }
        }
        if (shown && lanes.isNotEmpty() && cutDue < 0 && startAt < 0) arrange(stepAt)
    }

    fun setPosition(f: () -> Long) {
        pos = f
    }

    fun setArt(a: ImageBitmap?) {
        art = a
    }

    fun pinNext(scene: SaverScene) {
        pin = scene.ordinal
        gen++
    }

    internal fun timeText(): String {
        val p = pos?.invoke() ?: -1L
        return if (p < 0) "" else formatTime(p)
    }

    internal fun srcText(l: Lane, part: Int, time: String): String = when (if (part != 0) l.spec.src2 else l.spec.src) {
        Src.Title -> title
        Src.Artist -> artist
        Src.Album -> album
        Src.Time -> time
        Src.First -> firstChar(title)
        else -> ""
    }

    internal fun lay(l: Lane) {
        val sp = l.spec
        val copy = l.brkCopy
        val keep = l.brkKeep
        val n0 = l.t.g.size
        val edge = l.brkEdge
        val start = l.brkStart
        l.brkCopy = 0
        l.brkKeep = 0
        l.brkEdge = 0
        l.brkStart = 0
        l.time = ""
        l.period = 0
        l.count = 0
        l.half = false
        l.two = false
        l.ahead = emptyList()
        l.aheadCut = 0
        if (sp.src == Src.Art) {
            val s = px(sp.size)
            l.t = Text().also {
                it.ink1 = s
                it.edge1 = s * SUB
                it.top = -(if (sp.tall != 0L) px(sp.tall) else s)
            }
            return
        }
        val timed = sp.src == Src.Time || sp.src2 == Src.Time
        val tm = if (timed) timeText() else ""
        val t = Text()
        val pen64 = compose(l, tm, t)
        l.t = t
        l.two = t.two
        if (t.g.isNotEmpty() && sp.repeat) {
            val adv64 = if (sp.gap != 0L) (t.ink1 - t.ink0) * SUB + to64(sp.gap) else pen64 + glyphs.width64(t.st, " ")
            l.period = maxOf(1, (adv64 + SUB_HALF) shr SUB_BITS, if (sp.period != 0L) px(sp.period) else 0)
            if (sp.pieces > 0) {
                l.count = if (l.two) (sp.pieces + 1) / TWO else sp.pieces
                l.half = l.two && sp.pieces % TWO != 0
            }
        }
        if (tm.isEmpty()) {
            carryBreak(l, copy, keep, n0, edge, start)
            return
        }
        l.time = tm
        val next = Text()
        compose(l, formatTime(((pos?.invoke() ?: 0L) / SECOND + 1) * SECOND), next)
        l.aheadCut = next.cut
        l.ahead = next.g
        carryBreak(l, copy, keep, n0, edge, start)
    }

    internal fun compose(l: Lane, time: String, t: Text): Int {
        val sp = l.spec
        t.st = style(sp.size, sp.w).copy(saver = cjk && saverCjk(sp))
        var pen64 = 0
        var cut = -1
        val laid = IntArray(TWO)
        var any = false
        var any0 = false
        var any1 = false
        var inked = false
        var banded = false
        val g = ArrayList<G>()
        for (part in 0 until TWO) {
            val src = if (part != 0) sp.src2 else sp.src
            var s = srcText(l, part, time)
            var b0 = 0
            if (src != Src.Time || l.span[part].b1 == 0) {
                val b = utf8(s)
                b0 = min(l.span[part].b0, b.size)
                val b1 = min(l.span[part].b1, b.size)
                s = if (b1 > b0) bytesOf(b, b0, b1) else ""
            }
            if (s.isEmpty()) continue
            if (part != 0) cut = g.size
            val st = t.st.copy(weight = if (part != 0) sp.w2 else sp.w)
            val run = glyphs.run(st, s)
            if (part != 0 && sp.join != 0L) {
                pen64 = to64(sp.join)
            } else if (t.s.isNotEmpty()) {
                val space64 = glyphs.width64(st, " ")
                var lead = SaverRect.NONE
                var lead64 = 0
                var led = sp.split != 0L && any && run.g.isNotEmpty()
                if (led) {
                    val b = glyphs.box(run.g[0].face, t.st.px, run.g[0].gid)
                    led = b != null
                    if (b != null) lead = b
                    lead64 = run.g[0].x64 + run.g[0].dx64
                }
                pen64 = if (led && !lead.empty) (t.ink1 + px(sp.split) - lead.x0) * SUB - lead64
                else pen64 + when {
                    sp.split != 0L -> to64(sp.split)
                    sp.even -> max(space64, (to64(sp.period) - pen64 - run.width64) / TWO)
                    else -> space64
                }
            }
            if (t.s.isNotEmpty()) t.s += " "
            t.s += s
            val isTime = src == Src.Time
            val cell64 = if (isTime) glyphs.width64(st, "0") else 0
            var at64 = 0
            for (rg in run.g) {
                var x64 = rg.x64
                if (isTime) {
                    val ch = s[min(rg.byte, s.length - 1)]
                    val digit = ch in '0'..'9'
                    val own64 = glyphs.width64(st, ch.toString())
                    x64 = at64 + (if (digit) (cell64 - own64) / TWO else 0)
                    at64 += if (digit) cell64 else own64
                }
                val ox = (pen64 + x64 + rg.dx64 + SUB_HALF) shr SUB_BITS
                val oy = rg.dy64 shr SUB_BITS
                val box = glyphs.box(rg.face, t.st.px, rg.gid) ?: SaverRect.NONE
                if (!box.empty) {
                    val x0 = ox + box.x0
                    val x1 = ox + box.x1
                    val y0 = oy + box.y0
                    val y1 = oy + box.y1
                    t.ink0 = if (any) min(t.ink0, x0) else x0
                    t.ink1 = if (any) max(t.ink1, x1) else x1
                    t.top = if (any) min(t.top, y0) else y0
                    t.bottom = if (any) max(t.bottom, y1) else y1
                    any = true
                    if (part != 0) {
                        t.ink0b = if (any1) min(t.ink0b, x0) else x0
                        any1 = true
                    } else {
                        t.ink1a = if (any0) max(t.ink1a, x1) else x1
                        any0 = true
                    }
                    var h0 = box.x0 * SUB
                    var h1 = box.x1 * SUB
                    val at = along64(sp.angle, ox, oy)
                    glyphs.hbox(rg.face, t.st.px, rg.gid)?.let {
                        h0 = it[0]
                        h1 = it[1]
                    }
                    t.edge0 = if (inked) min(t.edge0, at + h0) else at + h0
                    t.edge1 = if (inked) max(t.edge1, at + h1) else at + h1
                    inked = true
                    var v0 = box.y0 * SUB
                    var v1 = box.y1 * SUB
                    val f = t.foot64[part]
                    glyphs.vbox(rg.face, t.st.px, rg.gid)?.let {
                        v0 = it[0]
                        v1 = it[1]
                    }
                    v0 += oy * SUB
                    v1 += oy * SUB
                    val c1 = if (f != Int.MAX_VALUE && v1 > f) max(v0, f) else v1
                    t.top64 = if (banded) min(t.top64, v0) else v0
                    t.bottom64 = if (banded) max(t.bottom64, v1) else v1
                    t.cap64 = if (banded) max(t.cap64, c1) else c1
                    banded = true
                }
                g += G(rg.face, rg.gid, ox, oy, box, b0 + rg.byte)
            }
            laid[part] = run.g.size
            if (isTime) {
                t.timed = true
                t.tm0 = (pen64 + SUB_HALF) shr SUB_BITS
                t.tm1 = (pen64 + at64 + SUB_HALF) shr SUB_BITS
            }
            pen64 += if (isTime) at64 else run.width64
        }
        t.g = g
        t.cut = if (cut < 0) g.size else cut
        t.two = any && laid[0] > 0 && laid[1] > 0
        if (!any0) t.ink1a = t.ink1
        if (!any1) t.ink0b = t.ink0
        if (!any) {
            t.g = emptyList()
            t.cut = 0
        }
        return pen64
    }

    internal fun place(l: Lane) {
        val t = l.t
        val sp = l.spec
        val angle = sp.angle
        val dx = dirX(angle)
        val dy = dirY(angle)
        val nx = -dy
        val ny = dx
        val c = l.across
        val w = widthPx.toLong() shl BITS
        val h = heightPx.toLong() shl BITS
        val ox = if (angle == 0) 0L else if (angle == UP) w - c else w / TWO + (c * nx shr BITS)
        val oy = if (angle == 0) c - (if (sp.top) t.top.toLong() * ONE else 0L) else if (angle == UP) h else h / TWO + (c * ny shr BITS)
        l.ox = ox
        l.oy = oy
        val cx = longArrayOf(0, w, w, 0)
        val cy = longArrayOf(0, 0, h, h)
        var us = LongArray(cx.size) { k -> ((cx[k] - ox) * dx + (cy[k] - oy) * dy) shr BITS }
        var vs = LongArray(cx.size) { k -> ((cx[k] - ox) * nx + (cy[k] - oy) * ny) shr BITS }
        fun clip(lim: Long, below: Boolean) {
            val ou = ArrayList<Long>()
            val ov = ArrayList<Long>()
            for (i in us.indices) {
                val j = (i + 1) % us.size
                val ia = if (below) vs[i] <= lim else vs[i] >= lim
                val ib = if (below) vs[j] <= lim else vs[j] >= lim
                if (ia) {
                    ou += us[i]
                    ov += vs[i]
                }
                if (ia != ib) {
                    ou += us[i] + (us[j] - us[i]) * (lim - vs[i]) / (vs[j] - vs[i])
                    ov += lim
                }
            }
            us = ou.toLongArray()
            vs = ov.toLongArray()
        }
        clip(t.top.toLong() * ONE, false)
        clip(t.bottom.toLong() * ONE, true)
        if (us.isEmpty()) {
            l.skip = true
            return
        }
        l.umin = us.min()
        l.umax = us.max()
        val s = if (sp.speed >= 0) 1 else -1
        val e0 = t.edge0.toLong() * FX_PER_64
        val e1 = t.edge1.toLong() * FX_PER_64
        val lead0 = if (sp.join != 0L) min(e0, 0L) else e0
        val u = when {
            sp.roll != 0L -> toPx(sp.roll) - (if (s > 0) e1 else e0)
            sp.tail -> toPx(sp.trail) - (if (s > 0) e0 else e1) - s * toPx(sp.shift)
            s > 0 -> l.umin - e1
            else -> l.umax - lead0
        }
        val rx = ox + (u * dx shr BITS)
        val ry = oy + (u * dy shr BITS)
        fun off(p: SaverPt, along: Boolean): Long {
            val ex = p.x.toLong() * ONE - rx
            val ey = p.y.toLong() * ONE - ry
            return abs(if (along) (ex * dx + ey * dy) shr BITS else (ex * nx + ey * ny) shr BITS)
        }
        var rest = SaverPt(rnd(rx), rnd(ry))
        for (k in 0 until CORNERS) {
            val p = SaverPt((rx shr BITS).toInt() + (k and 1), (ry shr BITS).toInt() + (k shr 1))
            val a = off(p, true)
            val b = off(rest, true)
            if (a < b || (a == b && off(p, false) < off(rest, false))) rest = p
        }
        l.rest = rest
        l.u0 = ((rest.x.toLong() * ONE - ox) * dx + (rest.y.toLong() * ONE - oy) * dy) shr BITS
        l.dir = when {
            angle == 0 -> SaverPt(s, 0)
            angle == UP -> SaverPt(0, -s)
            angle > 0 -> SaverPt(s, s)
            else -> SaverPt(s, -s)
        }
    }

    private fun takeStrings() {
        title = wantTitle
        artist = wantArtist
        album = wantAlbum
        phone = wantPhone
    }

    internal fun take(t: SaverScenes) {
        title = t.title
        artist = t.artist
        album = t.album
        phone = t.phone
    }

    internal fun tile(l: Lane): SaverTile? {
        val a = art ?: return null
        val w = px(l.spec.size)
        return SaverTile(a, w, if (l.spec.tall != 0L) px(l.spec.tall) else w, if (turned(l.spec.angle)) l.spec.angle else 0)
    }

    private fun askTiles() {
        for (l in lanes) {
            if (l.skip || l.spec.src != Src.Art) continue
            val t = tile(l) ?: continue
            l.job = true
            tiles(t)
        }
    }

    internal fun begin(scene: Int, at: Long, first: Boolean) {
        takeStrings()
        this.scene = SaverScene.entries[scene]
        sceneAt = at
        gap = rng.triangular(fx(Tok.motion_saverGapMin), fx(Tok.motion_saverGapMode), fx(Tok.motion_saverGapMax))
        lanes = ArrayList()
        crossLeft = 0
        packPasses = 0
        for (sp in specs) if (sp.scene == this.scene) {
            lanes += Lane(sp).also {
                it.t0 = at + secsMs(sp.inAt)
                it.out = sceneEnd()
                it.first = first
                it.across = toPx(sp.across)
                setEntry(it)
            }
        }
        layScene()
        askTiles()
    }

    internal fun layScene() {
        for (l in lanes) {
            lay(l)
            l.skip = l.spec.src != Src.Art && l.t.g.isEmpty()
            if (!l.skip) place(l)
        }
        arrange(-NEVER)
        fit()
    }

    internal fun draw(other: Boolean): Int {
        if (pin >= 0) return pin.also { pin = -1 }
        if (!other) return rng.uniform(0, SaverScene.entries.size - 1L).toInt()
        val n = rng.uniform(0, SaverScene.entries.size - TWO.toLong()).toInt()
        return if (n >= scene.ordinal) n + 1 else n
    }

    internal fun sceneEnd(): Long = sceneAt + secsMs(length(scene))

    internal fun nextAt(): Long = sceneEnd() + whoosh + gap

    fun stage(now: Long): SaverStage = when {
        now < sceneAt -> SaverStage.Gap
        now < sceneEnd() -> SaverStage.Scene
        now < sceneEnd() + whoosh -> SaverStage.Whoosh
        else -> SaverStage.Gap
    }

    fun step(now: Long) {
        stepAt = now
        if (!visible(now) || hiding()) return
        if (startAt < 0 && cutDue < 0) while (now >= sceneEnd() + whoosh) {
            val at = sceneEnd() + whoosh + gap
            val other = !afterChange
            afterChange = false
            if (lay?.takeNext(this, at) != true) begin(draw(other), at, false)
        }
        var tm = ""
        var read = false
        for (l in lanes) {
            val soon = !l.skip && now < laneOn(l) && now >= laneOn(l) - LEAD_MS
            if (l.time.isEmpty() || !(soon || laneIn(l, now))) continue
            if (!read) {
                tm = timeText()
                read = true
            }
            if (tm.isNotEmpty() && tm != l.time) {
                val period = l.period
                val count = l.count
                val copy = l.brkCopy
                val keep = l.brkKeep
                val n0 = l.t.g.size
                val edge = l.brkEdge
                val start = l.brkStart
                lay(l)
                if (period != 0 && l.period != 0) {
                    l.period = max(l.period, period)
                    l.count = count
                    carryBreak(l, copy, keep, n0, edge, start)
                }
                timeLays++
            }
        }
        for (l in lanes) {
            if (l.skip || l.spec.src != Src.Art || l.job || !laneIn(l, now)) continue
            val t = tile(l)
            if (t == null) {
                l.skip = true
            } else {
                l.job = true
                tiles(t)
            }
        }
        lay?.ahead(this, now)
    }

    fun shotScene(scene: SaverScene, firstInk: Long) {
        startAt = -1
        cutDue = -1
        keyUntil = -1
        afterChange = false
        begin(scene.ordinal, firstInk, false)
    }

    internal fun setEntry(l: Lane) {
        val sp = l.spec
        if (l.first && sp.scene == SaverScene.E && sp.look == Look.Eased) {
            l.enShift = wordEntryShift
            l.enDur = ms(fx(Tok.motion_saverWordEntry))
            l.en = true
            return
        }
        l.enShift = sp.shift
        l.enDur = ms(sp.shiftT)
        l.en = (sp.look == Look.Eased || sp.look == Look.Slide) && l.enShift != 0L && l.enDur > 0
    }

    internal fun travel(l: Lane, e: Long): Long {
        if (e <= 0) return 0
        val eo = max(l.out - l.t0, 0L)
        if (e > eo) return travel(l, eo) + (e - eo) * toPx(l.spec.whoosh) / SECOND
        var d = e * abs(toPx(l.spec.speed)) / SECOND
        if (l.en) {
            val u = min(e * ONE / l.enDur, ONE)
            d += toPx(l.enShift) * (if (l.spec.look == Look.Slide) u else Curves.powOut17.fx(u)) shr BITS
        }
        return d
    }

    internal fun steps(l: Lane, e: Long): Int = (travel(l, e) * PHASES / stepLen(l.spec.angle)).toInt()

    internal fun stepsNow(l: Lane, now: Long): Int = steps(l, now - l.t0)

    internal fun along(l: Lane, at: Long): Long =
        l.u0 + (if (l.spec.speed >= 0) 1 else -1) * steps(l, at - l.t0).toLong() * stepLen(l.spec.angle) / PHASES

    internal fun laneOn(l: Lane): Long = if (l.spec.off != 0L) sceneAt + secsMs(l.spec.on) else l.t0

    internal fun laneIn(l: Lane, now: Long): Boolean {
        if (l.skip || now < sceneAt) return false
        if (now < l.t0 || now < laneOn(l) || now >= l.out + whoosh) return false
        if (l.spec.off != 0L && (if (hiding()) min(now, hideAt) else now) >= sceneAt + secsMs(l.spec.off)) return false
        if (l.period != 0 && l.count == 0) return true
        val s = if (l.spec.speed >= 0) 1 else -1
        val u = along(l, now) - s * laneCopyOff(l, max(l.count - 1, 0)) * ONE
        val e0 = if (l.half && s > 0) l.t.ink0b else l.t.ink0
        val e1 = if (l.half && s < 0) l.t.ink1a else l.t.ink1
        return if (s > 0) u + e0.toLong() * ONE < l.umax else u + e1.toLong() * ONE > l.umin
    }

    internal fun fineOf(l: Lane, now: Long): SaverPt {
        if (!laneIn(l, now)) return SaverPt(NO_PEN, NO_PEN)
        val k = stepsNow(l, now)
        return SaverPt(l.rest.x * PHASES + k * l.dir.x, l.rest.y * PHASES + k * l.dir.y)
    }

    internal fun penOf(l: Lane, now: Long): SaverPt {
        val f = fineOf(l, now)
        return if (f.x == NO_PEN) f else SaverPt(fdiv(f.x, PHASES), fdiv(f.y, PHASES))
    }

    internal fun stepAt(l: Lane, now: Long): Long {
        if (l.skip) return -1
        if (now < l.t0) return l.t0
        if (!laneIn(l, now)) return -1
        val v = abs(toPx(l.spec.speed))
        val len = stepLen(l.spec.angle)
        if (v <= 0) return -1
        val e = now - l.t0
        val eo = max(l.out - l.t0, 0L)
        val end = eo + whoosh
        val k = stepsNow(l, now)
        fun firstAfter(lo0: Long, hi0: Long): Long {
            var lo = lo0
            var hi = hi0
            if (lo >= hi || steps(l, hi) <= k) return -1
            while (hi - lo > 1) {
                val mid = (lo + hi) / TWO
                if (steps(l, mid) > k) hi = mid else lo = mid
            }
            return l.t0 + hi
        }
        if (e >= eo) return firstAfter(e, end)
        val inEntry = l.en
        val dur = if (inEntry) l.enDur.toLong() else 0L
        if (inEntry && e < dur) {
            val at = firstAfter(e, min(dur, eo))
            if (at >= 0) return at
            if (dur >= eo) return firstAfter(eo, end)
        }
        val settled = if (inEntry) travel(l, dur) - dur * v / SECOND else 0L
        val next = ((k + 1).toLong() * len + PHASES - 1) / PHASES
        val at = max(e + 1, (SECOND * (next - settled) + v - 1) / v)
        return if (at <= eo) l.t0 + at else firstAfter(eo, end)
    }

    internal fun alpha(l: Lane, now: Long): Int {
        var a = ONE
        val e = max(now - l.t0, 0L)
        val sp = l.spec
        val wordFade = ms(fx(Tok.motion_saverWordFadeIn))
        if (l.first && sp.scene == SaverScene.E && sp.look == Look.Eased && e < wordFade) a = e * ONE / wordFade
        if (sp.fadeT != 0L && e < ms(sp.fadeT)) a = e * ONE / ms(sp.fadeT)
        val w = now - l.out
        if (w >= 0) a = fxMul(a, max(0L, ONE - w * ONE / whoosh))
        return alpha256(a)
    }

    internal fun origin(l: Lane, p: SaverPt, g: G, copy: Int): SaverPt {
        val u = g.ox - (if (l.spec.speed >= 0) 1 else -1) * laneCopyOff(l, copy).toInt()
        val v = g.oy
        return when (l.spec.angle) {
            0 -> SaverPt(p.x + u, p.y + v)
            UP -> SaverPt(p.x + v, p.y - u)
            DIAG -> SaverPt(p.x + rnd((u - v).toLong() * R), p.y + rnd((u + v).toLong() * R))
            else -> SaverPt(p.x + rnd((u + v).toLong() * R), p.y + rnd((v - u).toLong() * R))
        }
    }

    internal fun glyphRect(l: Lane, p: SaverPt, g: G, copy: Int): SaverRect {
        val o = origin(l, p, g, copy)
        val b = g.box
        if (l.spec.angle == 0) return SaverRect(o.x + b.x0, o.y + b.y0, o.x + b.x1, o.y + b.y1)
        if (l.spec.angle == UP) return SaverRect(o.x + b.y0, o.y - b.x1, o.x + b.y1, o.y - b.x0)
        val sy = if (l.spec.angle > 0) 1 else -1
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        val xs = intArrayOf(b.x0, b.x1, b.x1, b.x0)
        val ys = intArrayOf(b.y0, b.y0, b.y1, b.y1)
        for (k in xs.indices) {
            val x = rnd((xs[k] - sy * ys[k]).toLong() * R)
            val y = rnd((sy * xs[k] + ys[k]).toLong() * R)
            x0 = min(x0, x)
            y0 = min(y0, y)
            x1 = max(x1, x)
            y1 = max(y1, y)
        }
        return SaverRect(o.x + x0 - 1, o.y + y0 - 1, o.x + x1 + 1, o.y + y1 + 1)
    }

    internal fun glyphsOf(l: Lane, c: Int, v: List<G>, out: IntArray) {
        out[0] = 0
        out[1] = v.size
        if (!l.half || l.count == 0 || c != l.count - 1) return
        val cut = min(if (v === l.ahead) l.aheadCut else l.t.cut, v.size)
        if (l.spec.speed >= 0) out[0] = cut else out[1] = cut
    }

    internal fun quadsOf(l: Lane, now: Long, out: ArrayList<Quad>) {
        out.clear()
        if (!laneIn(l, now)) return
        val p = penOf(l, now)
        val angle = l.spec.angle
        val sy = if (angle > 0) 1 else -1
        fun put(o: SaverPt, b: SaverRect, g: Int, c: Int, part: Int) {
            val xs = intArrayOf(b.x0, b.x1, b.x1, b.x0)
            val ys = intArrayOf(b.y0, b.y0, b.y1, b.y1)
            out += Quad(Array(CORNERS) { k ->
                val x = xs[k]
                val y = ys[k]
                when (angle) {
                    0 -> SaverPt((o.x + x) * SUB, (o.y + y) * SUB)
                    UP -> SaverPt((o.x + y) * SUB, (o.y - x) * SUB)
                    else -> SaverPt(
                        o.x * SUB + (((x - sy * y).toLong() * R * SUB + HALF) shr BITS).toInt(),
                        o.y * SUB + (((sy * x + y).toLong() * R * SUB + HALF) shr BITS).toInt(),
                    )
                }
            }, g, c, part)
        }
        if (l.spec.src == Src.Art) {
            put(p, SaverRect(0, l.t.top, l.t.ink1, 0), -1, 0, 0)
            return
        }
        val c = IntArray(TWO)
        laneCopyRange(l, now, c)
        val gs = IntArray(TWO)
        for (k in c[0]..c[1]) {
            laneCopyGlyphs(l, k, l.t.g, gs)
            for (j in gs[0] until gs[1]) {
                val g = l.t.g[j]
                if (!g.box.empty) put(origin(l, p, g, k), g.box, j, k, if (j >= l.t.cut) 1 else 0)
            }
        }
    }

    internal fun boxes(l: Lane, t: Long, angle: Int, ox: Long, oy: Long, out: ArrayList<Box>, q: ArrayList<Quad>) {
        out.clear()
        quadsOf(l, t, q)
        val f = Axes.of(angle, ox, oy)
        for (k in q) {
            val b = Box(FAR, -FAR, FAR, -FAR)
            for (c in k.p) {
                val x = c.x / SUB.toDouble()
                val y = c.y / SUB.toDouble()
                val u = f.u(x, y)
                val v = f.v(x, y)
                b.u0 = min(b.u0, u)
                b.u1 = max(b.u1, u)
                b.v0 = min(b.v0, v)
                b.v1 = max(b.v1, v)
            }
            out += b
        }
    }

    internal fun band(i: Int): SaverRect {
        if (i < 0 || i >= lanes.size) return SaverRect.NONE
        val l = lanes[i]
        if (l.skip) return SaverRect.NONE
        val screen = SaverRect(0, 0, widthPx, heightPx)
        if (l.spec.angle != 0) return screen
        return SaverRect(0, l.rest.y + l.t.top, widthPx, l.rest.y + l.t.bottom).intersect(screen)
    }

    fun wakeAt(now: Long): Long {
        var w = eventAt(now)
        fun take(t: Long) {
            if (t > now && (w < 0 || t < w)) w = t
        }
        var timed = false
        for (l in lanes) {
            take(stepAt(l, now))
            timed = timed || (l.time.isNotEmpty() && laneIn(l, now))
        }
        val p = if (timed) pos?.invoke() ?: -1L else -1L
        if (p >= 0) take(now + SECOND - p % SECOND)
        return w
    }

    internal fun eventAt(now: Long): Long {
        var w = -1L
        fun take(t: Long) {
            if (t > now && (w < 0 || t < w)) w = t
        }
        take(cutDue)
        take(keyUntil)
        take(sceneAt)
        take(sceneEnd())
        take(sceneEnd() + whoosh)
        for (l in lanes) if (!l.skip && l.spec.off != 0L) {
            take(laneOn(l))
            take(sceneAt + secsMs(l.spec.off))
        }
        return w
    }
}

internal class Axes(val dx: Double, val dy: Double, val ox: Double, val oy: Double) {
    val nx = -dy
    val ny = dx

    fun u(x: Double, y: Double): Double = (x - ox) * dx + (y - oy) * dy

    fun v(x: Double, y: Double): Double = (x - ox) * nx + (y - oy) * ny

    companion object {
        fun of(angle: Int, ox: Long, oy: Long): Axes =
            Axes(dirX(angle) / ONE.toDouble(), dirY(angle) / ONE.toDouble(), ox / ONE.toDouble(), oy / ONE.toDouble())
    }
}
