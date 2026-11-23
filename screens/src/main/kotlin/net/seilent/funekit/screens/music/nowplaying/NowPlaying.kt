package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.Press
import net.seilent.funekit.kit.np.NP_BACK
import net.seilent.funekit.kit.np.NP_HEART
import net.seilent.funekit.kit.np.NP_MARK
import net.seilent.funekit.kit.np.NP_PRESSES
import net.seilent.funekit.kit.np.NP_RINGS
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Repeat
import kotlin.coroutines.CoroutineContext

class NpSlide {
    var touch = false
    var armed = false
    var locked = false
    var live = false
    var up = false
    var lock = 0f
    var d = 0f
    var from = 0f
    var w = 0f
    var start: Long? = null
    var next: Long? = null
    var prev: Long? = null
    var target: Long? = null
    var dir = 0
    var upAt = -1L
    var landAt = -1L
    var landedAt = -1L
    var shownAt = -1L
    var cap = 0L
}

enum class NpAct { None, Back, Queue, Favorite, Lyrics, LyricsSheet, Controls, Shuffle, Repeat, Toggle, Info }

private fun progress(e: Long, d: Long): Float = if (d > 0) (e.toFloat() / d).coerceIn(0f, 1f) else 1f

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private val sameEdges = object : SnapshotMutationPolicy<Edges?> {
    override fun equivalent(a: Edges?, b: Edges?): Boolean = if (a == null || b == null) a === b else a.same(b)
}

class NowPlayingModel {
    var shown by mutableStateOf(NpShown.None)
        private set
    var state by mutableStateOf(PlayerState())
        private set
    var phone: PhoneTrack? by mutableStateOf(null)
        private set
    var favorite by mutableStateOf(false)
    var mark by mutableStateOf(false)
    var art: ImageBitmap? by mutableStateOf(null)
    var artKey: Any? = null
    var artPhone = false
    var ring by mutableIntStateOf(0)
        private set
    var titleFocus by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var seeking by mutableStateOf(false)
        private set
    var seekX by mutableStateOf(0f)
        private set
    var phoneShuffle by mutableStateOf(false)
    var phoneRepeat by mutableStateOf(false)
    var seekBand by mutableStateOf<Edges?>(null, sameEdges)
        private set
    private var bandOwner: Any? = null

    val variant: Boolean get() = phone != null
    val lyrics = LyricsModel()
    var lyricsView by mutableStateOf(false)
    var workContext: CoroutineContext = Dispatchers.Default

    fun lyricsShown(): Boolean = lyricsView && lyrics.shown(variant)
    val press = Array(NP_PRESSES) { i -> Press().also { it.scaled = i == NP_MARK } }

    private var held = NpShown.None
    private var heldMark = false
    var heldPos = 0L
        private set
    private var inputAt = 0L
    private var idle = false
    private var idleAt = -1L
    private var idleFrom = 1f
    private var idleTo = 1f
    private var touchDown = false
    private var markDown = false
    private var downX = 0f
    private var downY = 0f
    private var seekTrack: Long? = null
    private var geoSeek = 0L
    private var cancelled = false

    private fun bump() {
        revision++
    }

    private fun phoneIs(p: PhoneTrack?) {
        val edge = (p == null) != (phone == null)
        phone = p
        if (edge) lyrics.land(p != null)
    }

    fun feed(s: PlayerState) {
        if (s == state && phone == null) return
        state = s
        phoneIs(null)
        bump()
    }

    fun feed(p: PhoneTrack) {
        if (p == phone) return
        phoneIs(p)
        bump()
    }

    fun sync(n: NpShown, now: Long) {
        shown = n
        val tg = slide.target
        if (tg != null && n.track == tg && slide.shownAt < 0) slide.shownAt = now
        else if (tg != null && n.track != tg && n.track != slide.start) slide = NpSlide()
        bump()
        stepTo(now)
    }

    fun changed(n: NpShown, now: Long) {
        val tg = slide.target
        if (tg != null && n.track == tg) {
            shown = n
            if (slide.shownAt < 0) slide.shownAt = now
            bump()
            return
        }
        if (tg != null) slide = NpSlide()
        if (slide.armed && n.track != slide.start) {
            if (slide.live || slide.d != 0f) {
                slide.up = true
                slide.upAt = now
                slide.landAt = now
                slide.from = slide.d
            }
            slide.armed = false
            slide.live = false
            slide.start = null
            slide.next = null
            slide.prev = null
        }
        if (n != shown) shown = n
        bump()
    }

    fun shownAt(now: Long): NpShown = if (slideHeld(now)) held else shown

    fun markAt(now: Long): Boolean = if (slideHeld(now)) heldMark else mark

    fun stepTo(now: Long) {
        if (!idle && now - inputAt >= IDLE) {
            idle = true
            fadeIdle(now, Tok.motion_idleAlpha)
        }
        for (k in 0 until NP_PRESSES) press[k].draw(now.toFloat())
        slideStep(now)
    }

    private fun fadeIdle(now: Long, to: Float) {
        idleFrom = idleAlpha(now)
        idleTo = to
        idleAt = now
        bump()
    }

    fun idleAlpha(now: Long): Float {
        if (idleAt < 0) return idleTo
        return lerp(idleFrom, idleTo, Curves.decel.at(progress(now - idleAt, IDLE_FADE)))
    }

    fun idleDone(now: Long): Boolean = idleAt < 0 || now - idleAt >= IDLE_FADE

    fun idleWake(): Long = inputAt + IDLE

    fun resetFocus(now: Long) {
        ring = 0
        titleFocus = false
        idle = false
        idleAt = -1L
        idleFrom = 1f
        idleTo = 1f
        inputAt = now
        bump()
    }

    fun touched(now: Long) {
        inputAt = now
        if (idle) {
            idle = false
            fadeIdle(now, 1f)
        }
    }

    fun wake(now: Long) {
        touched(now)
        bump()
    }

    fun clearTitleFocus() {
        if (!titleFocus) return
        titleFocus = false
        bump()
    }

    fun clear() {
        phoneIs(null)
        state = PlayerState()
        shown = NpShown.None
        slide = NpSlide()
        favorite = false
        mark = false
        bump()
    }

    fun ticking(): Boolean = phone?.let { it.playing && it.durationMs > 0 } ?: state.playing

    fun wakeAt(now: Long): Long? {
        val at = mutableListOf<Long>()
        if (ticking()) at += now - Math.floorMod(now, TICK) + TICK
        if (!idle) at += idleWake()
        for (p in press) p.due(now.toFloat())?.let { at += Math.ceil(it.toDouble()).toLong() }
        return at.filter { it > now }.minOrNull()
    }

    fun gestureUp() {
        if (!cancelled) return
        cancelled = false
        clearTitleFocus()
    }

    fun ringAt(g: NpLayout, x: Float, y: Float): Int {
        val i = g.ringHit(x, y, rings())
        return if (variant && ((i == 1 && !phoneShuffle) || (i > 1 && !phoneRepeat))) -1 else i
    }

    fun heartShown(): Boolean = variant || shown.track != null

    fun rings(): Int = if (heartShown()) NP_RINGS else NP_RINGS - 1

    fun ringFocus(): Int = if (titleFocus) -1 else ring

    fun on(i: Int): Boolean {
        val p = phone
        if (p != null) return when (i) {
            0 -> false
            1 -> p.shuffle
            else -> p.repeat != PhoneRepeat.Off
        }
        return when (i) {
            0 -> state.shuffle
            1 -> state.repeat != Repeat.Off
            else -> favorite
        }
    }

    fun glyph(i: Int): String {
        val p = phone
        if (p != null) return when (i) {
            0 -> if (p.playing) PAUSE else PLAY
            1 -> SHUFFLE
            else -> if (p.repeat == PhoneRepeat.One) REPEAT_ONE else REPEAT
        }
        return when (i) {
            0 -> SHUFFLE
            1 -> if (state.repeat == Repeat.One) REPEAT_ONE else REPEAT
            else -> HEART
        }
    }

    fun position(now: Long, elapsedNanos: Long): Long {
        val p = phone
        if (p != null) return p.position(elapsedNanos / NANOS_MS)
        if (seeking) return geoSeek
        return state.anchor.ms(elapsedNanos)
    }

    fun down(g: NpLayout, x: Float, y: Float, now: Long) {
        touched(now)
        seeking = false
        val holding = slide.target != null
        if (holding) {
            if (slide.landedAt < 0) slide.landedAt = now
            slide.touch = false
            slide.armed = false
            slide.live = false
        } else {
            slide = NpSlide()
        }
        val lyr = lyricsShown()
        if (!lyr && seekable(g, x, y)) {
            seeking = true
            seekX = x
            seekTrack = shown.track
            geoSeek = g.seekAt(x, shown.durationMs)
            bump()
            return
        }
        touchDown = true
        downX = x
        downY = y
        slide.armed = !lyr && !holding && !variant && shown.track != null && y < g.s.stdH - Tok.gesture_bottom
        slide.touch = slide.armed
        if (!holding) slide.start = if (slide.armed) shown.track else null
        markDown = !lyr && markAt(now) && (!variant || lyrics.phoneLyrics) && g.markHit(x, y)
        val t = now.toFloat()
        when {
            g.backHit(x, y) -> press[NP_BACK].down(t)
            markDown -> press[NP_MARK].down(t)
            else -> {
                val i = ringAt(g, x, y)
                if (i >= 0) {
                    press[i].down(t)
                    ring = i
                    titleFocus = false
                }
            }
        }
        if (!lyr && g.titleHit(x, y) && !titleFocus) titleFocus = true
        bump()
    }

    fun seekShown(): Boolean = !variant && shown.track != null && shown.durationMs > 0

    private fun seekable(g: NpLayout, x: Float, y: Float): Boolean = seekShown() && g.seekHit(x, y)

    internal fun band(owner: Any, e: Edges?) {
        if (e != null) bandOwner = owner else if (owner !== bandOwner) return
        seekBand = e
    }

    fun seekMove(g: NpLayout, x: Float) {
        if (!seeking) return
        seekX = x
        geoSeek = g.seekAt(x, shown.durationMs)
        bump()
    }

    fun seekUp(g: NpLayout, x: Float): Long? {
        if (!seeking) return null
        seekMove(g, x)
        seeking = false
        bump()
        return if (state.track == seekTrack) geoSeek else null
    }

    fun seekCancel() {
        if (!seeking) return
        seeking = false
        bump()
    }

    fun cancel(now: Long) {
        if (seeking) return
        if (!touchDown) return
        touchDown = false
        val t = now.toFloat()
        for (p in press) p.cancel(t)
        cancelled = true
        bump()
    }

    fun tap(g: NpLayout, now: Long): NpAct {
        if (seeking) return NpAct.None
        if (!touchDown) return NpAct.None
        touchDown = false
        val t = now.toFloat()
        for (p in press) p.tap(t)
        bump()
        val x = downX
        val y = downY
        if (g.backHit(x, y)) return NpAct.Back
        if (markDown && markAt(now) && (!variant || lyrics.phoneLyrics) && g.markHit(x, y)) return NpAct.Lyrics
        val i = ringAt(g, x, y)
        if (i >= 0) {
            ring = i
            titleFocus = false
            bump()
            if (variant) return if (i == 0) NpAct.Toggle else if (i == 1) NpAct.Shuffle else NpAct.Repeat
            if (i == NP_HEART) return NpAct.Favorite
            return if (i == 0) NpAct.Shuffle else NpAct.Repeat
        }
        if (lyricsShown()) return NpAct.LyricsSheet
        if (g.titleHit(x, y)) return NpAct.Queue
        if (g.artHit(x, y)) return NpAct.Controls
        return NpAct.None
    }

    fun hold(g: NpLayout): Boolean {
        if (!touchDown || seeking || variant || lyricsShown() || shown.track == null || !g.titleHit(downX, downY)) return false
        touchDown = false
        titleFocus = false
        slide.armed = false
        slide.touch = false
        bump()
        return true
    }

    fun pressing(now: Long): Boolean = press.any { it.stale(now.toFloat()) }

    fun animating(now: Long): Boolean = !idleDone(now) || pressing(now) || slideBusy() || lyrics.fading(now)

    var slide = NpSlide()
        private set
    var prevId: Long? = null

    fun slideBusy(): Boolean = slide.live || slide.up || slide.landAt >= 0 || slide.target != null

    fun slideLock(x: Float): Boolean {
        if (!slide.armed) return false
        slide.live = true
        slide.locked = true
        slide.lock = x
        slide.d = 0f
        slide.next = shown.nextId
        slide.prev = prevId
        titleFocus = false
        bump()
        return true
    }

    fun slideDrag(x: Float, w: Float) {
        if (!slide.live) return
        slide.d = (x - slide.lock).coerceIn(-w, w)
        bump()
    }

    fun slideUp(now: Long, dir: Int, w: Float, nanos: Long): Boolean {
        touchDown = false
        if (slide.armed && slide.live) {
            slide.up = true
            slide.upAt = now
        }
        slide.armed = false
        slide.live = false
        slide.w = w
        bump()
        if (dir == 0 || !slide.touch || state.track != slide.start) return false
        return slideCommit(dir, w, HOLD_CAP, position(now, nanos))
    }

    fun slideLand(now: Long, w: Float) {
        if (slide.live) slide.touch = false
        if (slide.armed || slide.live) slideUp(now, 0, w, 0L)
    }

    private fun slideCommit(dir: Int, w: Float, cap: Long, pos: Long): Boolean {
        if (!slide.up || slide.target != null || slide.landAt >= 0) return false
        val to = (if (dir > 0) slide.next else slide.prev) ?: return false
        slide.target = to
        slide.dir = if (dir > 0) 1 else -1
        slide.from = slide.d + if (dir > 0) w else -w
        slide.landAt = slide.upAt
        slide.landedAt = -1L
        slide.cap = cap
        slide.shownAt = if (shown.track == to) slide.upAt else -1L
        held = shown
        heldMark = mark
        heldPos = pos
        bump()
        return true
    }

    fun slideOffset(now: Long): Float {
        if (slide.landAt >= 0) {
            if (slide.landedAt >= 0) return 0f
            return slide.from * (1 - Curves.expOut6.at(progress(now - slide.landAt, SLIDE_LAND)))
        }
        return if (slide.live || slide.up) slide.d else 0f
    }

    fun slideHeld(now: Long): Boolean {
        val tg = slide.target ?: return false
        return slide.shownAt < 0 || shown.track != tg || now < maxOf(slide.upAt + SLIDE_TEXT_DELAY, slide.shownAt)
    }

    private fun slideStep(now: Long) {
        val sl = slide
        if (sl.up && sl.target == null && sl.landAt < 0) {
            sl.landAt = sl.upAt
            sl.from = sl.d
        }
        if (sl.landAt >= 0 && sl.landedAt < 0 && Math.abs(slideOffset(now)) < SLIDE_REST) sl.landedAt = now
        if (sl.landedAt < 0) return
        val tg = sl.target
        if (tg != null) {
            if (shown.track == tg && sl.shownAt >= 0) {
                if (!slideHeld(now)) slide = NpSlide()
                bump()
                return
            }
            if (now >= sl.landedAt + sl.cap) {
                val back = NpSlide()
                back.up = true
                back.upAt = now
                back.landAt = now
                back.from = if (sl.dir > 0) -sl.w else sl.w
                if (sl.dir > 0) back.next = tg else back.prev = tg
                slide = back
                bump()
            }
            return
        }
        val keep = touchDown && sl.touch
        val start = sl.start
        slide = NpSlide()
        if (keep) {
            slide.touch = true
            slide.start = start
        }
        bump()
    }

    companion object {
        val IDLE: Long = Tok.motion_idle.toLong()
        val IDLE_FADE: Long = Tok.motion_idleFade.toLong()
        val TICK: Long = Tok.motion_progress.toLong()
        val SLIDE_LAND: Long = Tok.motion_npSlideLand.toLong()
        val SLIDE_TEXT_DELAY: Long = Tok.motion_npSlideTextDelay.toLong()
        const val SLIDE_REST = 1f
        const val HOLD_CAP = 500L
        const val NANOS_MS = 1000000L
        const val SHUFFLE = "shuffle"
        const val REPEAT = "repeat"
        const val REPEAT_ONE = "repeatOne"
        const val HEART = "heart"
        const val PLAY = "play"
        const val PAUSE = "pause"
    }
}
