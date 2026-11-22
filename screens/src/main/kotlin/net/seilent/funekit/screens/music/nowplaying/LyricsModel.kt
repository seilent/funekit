package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.core.Lrc
import net.seilent.funekit.fx
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.VariantMotion
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import kotlin.coroutines.CoroutineContext

private const val MIRROR = 2L
private const val WAKE_SLACK_MS = 25L
private val ONE = fxInt(1)

private fun progress(e: Long, d: Long): Float = if (d > 0) (e.toFloat() / d).coerceIn(0f, 1f) else 1f

class LyricAt(val shown: Long?, val playing: Long?, val pos: Long, val variant: Boolean, val running: Boolean)

sealed interface LyricView {
    data object Blank : LyricView

    data object Plain : LyricView

    data class Column(val from: Int, val to: Int, val p: Long) : LyricView
}

private class Dim {
    var from = 0L
    var to = 0L
    var start = 0L
    var dur = 0L
    var back = -1L

    private fun value(t: Long): Long {
        val e = t - start
        if (dur <= 0 || e >= dur) return to
        if (e <= 0) return from
        return from + fxMul(to - from, fx(Curves.decel.at(progress(e, dur))))
    }

    fun at(now: Long): Long = value(if (back < 0) now else MIRROR * back - now)

    fun run(now: Long, target: Long, ms: Long) {
        from = at(now)
        to = target
        start = now
        dur = ms
        back = -1
    }

    fun set(v: Long) {
        from = v
        to = v
        dur = 0
        back = -1
    }

    fun moving(now: Long): Boolean {
        val t = if (back < 0) now else MIRROR * back - now
        return dur > 0 && t >= start && t - start < dur
    }

    fun mirror(now: Long) {
        if (back >= 0) start += MIRROR * (now - back)
        back = if (back < 0) now else -1
    }
}

class LyricsModel {
    var track: Long? = null
        private set
    var pending = false
        private set
    var lines: List<Lrc.Line> = emptyList()
        private set
    var plain: List<String> = emptyList()
        private set
    var offsetMs = 0
        private set
    var gen by mutableIntStateOf(0)
        private set
    var mode by mutableStateOf(false)
        private set
    var phoneLyrics by mutableStateOf(false)
    var phoneMode by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var lineShown = LYRIC_NONE
        private set
    var slideFrom = LYRIC_NONE
        private set
    var slideAt = 0L
        private set
    var slideD = 0L
        private set
    private var slide = false

    val plainTrack = RowTrack(1, 0)
    val plainFade = ThumbFade()

    private var src: List<Lrc.Line> = emptyList()
    private var lineTrack: Long? = null
    private var slideTrack: Long? = null
    private var slideGen = -1
    private var fadeAt = -1L
    private var backAt = -1L
    private val dim = Dim()

    private fun bump() {
        revision++
    }

    private fun changed() {
        gen++
        slide = false
        plainTrack.at(0)
        bump()
    }

    fun begin(track: Long) {
        this.track = track
        pending = true
        src = emptyList()
        lines = emptyList()
        plain = emptyList()
        changed()
    }

    fun load(track: Long, text: String?, offsetMs: Int) {
        loaded(track, parsed(text), offsetMs)
    }

    private fun loaded(track: Long, p: Lrc.Parsed, offsetMs: Int) {
        if (this.track != track) return
        src = p.lines
        this.offsetMs = offsetMs
        lines = Lrc.shift(src, offsetMs.toLong())
        plain = p.plain
        pending = false
        changed()
    }

    suspend fun read(l: Lyrics, track: Long, on: CoroutineContext) {
        begin(track)
        val text = l.lyrics(track)
        val off = l.offsetMs(track)
        val p = withContext(on) { parsed(text) }
        loaded(track, p, off)
    }

    suspend fun readPhone(pl: PhoneLyrics, t: PhoneTrack, shown: Long, on: CoroutineContext) {
        begin(shown)
        val text = pl.lyrics(t)
        val off = pl.offsetMs(t)
        val p = withContext(on) { parsed(text) }
        loaded(shown, p, off)
    }

    fun retime(offsetMs: Int) {
        this.offsetMs = offsetMs
        lines = Lrc.shift(src, offsetMs.toLong())
        lineTrack = null
        slide = false
        gen++
        bump()
    }

    fun has(shown: Long?): Boolean = shown != null && track == shown && !pending && (lines.isNotEmpty() || plain.isNotEmpty())

    fun on(variant: Boolean): Boolean = if (variant && phoneLyrics) phoneMode else mode

    fun shown(variant: Boolean): Boolean = on(variant) && (!variant || phoneLyrics)

    fun index(at: LyricAt): Int {
        if ((at.variant && !phoneLyrics) || at.shown == null || track != at.shown || at.playing != at.shown) return LYRIC_NONE
        if (pending) return LYRIC_TITLE
        if (lines.isEmpty()) return LYRIC_NONE
        return lineAt(at.pos)
    }

    private fun lineAt(ms: Long): Int {
        var lo = 0
        var hi = lines.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ms < lines[mid].ms) hi = mid else lo = mid + 1
        }
        return lo - 1
    }

    fun due(at: LyricAt, seenAt: Long, now: Long): Long {
        if (!shown(at.variant) || !at.running || index(at) == LYRIC_NONE) return -1
        val i = lineAt(at.pos)
        if (i + 1 >= lines.size) return -1
        val due = seenAt + maxOf(1L, lines[i + 1].ms - at.pos)
        if (due > now) return due
        return if (due + WAKE_SLACK_MS > now) due + WAKE_SLACK_MS else -1
    }

    fun step(now: Long, at: LyricAt) {
        if (slide && now - slideAt >= slideD) slide = false
        val fresh = at.shown != lineTrack
        val line = index(at)
        if (!fresh && line == lineShown) return
        slide = false
        val drawn = !fresh && !pending && track != null && track == slideTrack && gen == slideGen
        if (drawn && line >= 0 && line == lineShown + 1) {
            val stamp = lines[line].ms
            val late = maxOf(0L, at.pos - stamp)
            var d = Tok.motion_lyricSlide.toLong()
            if (line + 1 < lines.size) d = minOf(d, lines[line + 1].ms - stamp)
            if (d - late >= Tok.motion_scroll.toLong()) {
                slideFrom = lineShown
                slideAt = now - late
                slideD = d
                slide = true
            }
        }
        lineTrack = at.shown
        lineShown = line
        bump()
    }

    fun view(now: Long, at: LyricAt): LyricView {
        step(now, at)
        slideTrack = null
        if (at.shown != null && track == at.shown && pending) return LyricView.Blank
        val line = lineShown
        if (line == LYRIC_NONE && has(at.shown)) return LyricView.Plain
        if (line != LYRIC_NONE && (track != at.shown || line >= lines.size)) return LyricView.Blank
        if (line != LYRIC_NONE) {
            slideTrack = track
            slideGen = gen
        }
        val moving = slide && line >= 0
        val p = if (moving) fx(Curves.expOut6.at(progress(now - slideAt, slideD))) else ONE
        return LyricView.Column(if (moving) slideFrom else line, line, p)
    }

    fun sliding(now: Long): Boolean = slide && now - slideAt < slideD

    internal fun turn(on: Boolean, variant: Boolean) {
        if (variant && phoneLyrics) phoneMode = on else mode = on
        lineTrack = null
        slide = false
        bump()
    }

    fun echo(on: Boolean, variant: Boolean) {
        if (on == phoneMode) return
        phoneMode = on
        lineTrack = null
        slide = false
        if (variant) land(true) else bump()
    }

    fun toggle(now: Long, variant: Boolean = false) {
        turn(!on(variant), variant)
        dim.run(now, if (shown(variant)) ONE else 0L, Tok.motion_variantFade.toLong())
        fadeAt = now
        backAt = -1
    }

    fun reverse(now: Long, variant: Boolean = false) {
        turn(!on(variant), variant)
        dim.mirror(now)
        if (backAt < 0) {
            backAt = now
        } else {
            fadeAt += MIRROR * (now - backAt)
            backAt = -1
        }
    }

    fun land(variant: Boolean = false) {
        dim.set(if (shown(variant)) ONE else 0L)
        fadeAt = -1
        backAt = -1
        bump()
    }

    private fun fadeE(now: Long): Long = if (backAt < 0) now - fadeAt else MIRROR * backAt - fadeAt - now

    fun fading(now: Long): Boolean {
        if (fadeAt < 0) return false
        val e = fadeE(now)
        return if (backAt < 0) e < VariantMotion.total(VariantMotion.Dir.Out) else e > 0
    }

    fun fade(now: Long): VariantMotion.Frame? = if (fading(now)) VariantMotion.frame(VariantMotion.Dir.Out, fadeE(now)) else null

    fun stepTo(now: Long) {
        if (fadeAt < 0 || fading(now)) return
        fadeAt = -1
        backAt = -1
        bump()
    }

    fun dim(now: Long): Long = dim.at(now)

    fun dimming(now: Long): Boolean = dim.moving(now)

    fun plainRows(spec: RowSpec, rows: Int) {
        plainTrack.resize(spec)
        if (plainTrack.count != rows) plainTrack.count(rows, plainTrack.first)
    }

    fun scrolled() {
        bump()
    }

    fun moving(now: Long): Boolean = sliding(now) || fading(now) || plainFade.fading(now)

    fun wakeAt(at: LyricAt, now: Long): Long {
        val d = due(at, now, now)
        val f = plainFade.wakeAt(now)
        return if (d < 0) f else if (f < 0) d else minOf(d, f)
    }
}

private fun parsed(text: String?): Lrc.Parsed = if (text == null) Lrc.Parsed(emptyList(), emptyList()) else Lrc.parse(text.toByteArray(Charsets.UTF_8))
