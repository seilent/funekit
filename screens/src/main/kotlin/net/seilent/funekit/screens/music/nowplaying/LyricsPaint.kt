package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Role
import net.seilent.funekit.fx
import net.seilent.funekit.fxOfPx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.nextCluster
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.pxAt
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.textWidth
import net.seilent.funekit.kit.thumb
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.listTarget
import net.seilent.funekit.kit.touch.rememberCoast
import net.seilent.funekit.kit.word
import net.seilent.funekit.kit.wrapRanges
import net.seilent.funekit.px
import net.seilent.funekit.px64
import java.text.Bidi
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val PEN = 64f
private const val A256 = 256f

private fun lyricWords(s: String): String {
    var t = s
    while (true) {
        t = t.trimStart(' ', '\t')
        if (t.isEmpty()) return t
        val e = if (t[0] == '[') t.indexOf(']') else -1
        if (e < 0) return t
        t = t.substring(e + 1)
    }
}

private fun strong(s: String): Int? {
    val t = lyricWords(s)
    var i = 0
    var iso = 0
    while (i < t.length) {
        val c = t.codePointAt(i)
        i += Character.charCount(c)
        when (Character.getDirectionality(c)) {
            Character.DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE -> if (iso > 0) iso--
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE,
            Character.DIRECTIONALITY_FIRST_STRONG_ISOLATE -> iso++
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> if (iso == 0) return Bidi.DIRECTION_LEFT_TO_RIGHT
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> if (iso == 0) return Bidi.DIRECTION_RIGHT_TO_LEFT
        }
    }
    return null
}

fun lyricDir(texts: List<String>): Int = texts.firstNotNullOfOrNull(::strong) ?: Bidi.DIRECTION_LEFT_TO_RIGHT

fun lyricLineDir(line: String, dir: Int): Int = strong(line) ?: dir

class LyricsInk(
    val t: LyricTable,
    private val g: NpLayout,
    private val measurer: TextMeasurer,
    private val fonts: FontFamily.Resolver,
    private val density: Density,
    private val covers: Covers?,
    val lines: List<String>?,
    private val title: String,
    val next: String,
    private val plainTexts: List<String>,
) : LyricInk {
    val dir: Int = lyricDir(if (lines.isNullOrEmpty()) plainTexts else lines)
    val rtl: Boolean get() = dir == Bidi.DIRECTION_RIGHT_TO_LEFT
    private val width64 = pen64(Tok.canvasW - Tok.edge - Tok.edge, g.p)
    private val cache = HashMap<Pair<Int, Boolean>, List<Word>>()
    val plain: List<Word?> by lazy {
        plainTexts.flatMap { s -> if (s.isEmpty()) listOf(null) else wrap(s, t.plain, lyricLineDir(s, dir)) }
    }

    private fun wrap(text: String, role: Role, base: Int): List<Word> {
        if (text.isEmpty()) return emptyList()
        val style = roleStyle(role, g.s, density, Ink.white, ppu = g.p)
        val tw = textWidth(role, style, fonts, density, covers, base)
        val rows = wrapRanges(text, width64, Int.MAX_VALUE, { b, e -> if (e > b) pen64(tw(text.substring(b, e))) else 0 }, { p, e -> nextCluster(text, p, e) })
        return rows.map { (b, e) -> measurer.word(text.substring(b, e), role, style, fonts, density, covers, base) }
    }

    fun words(line: Int, big: Boolean): List<Word> = cache.getOrPut(line to big) {
        if (line >= 0 && lines != null) {
            val s = lines[line]
            wrap(s, t.role(big), lyricLineDir(s, dir))
        } else {
            wrap(if (big) title else next, t.role(big), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        }
    }

    override fun rows(line: Int, big: Boolean): Int = words(line, big).size

    override fun top(line: Int, big: Boolean): Int = words(line, big).firstOrNull()?.let { ceil(-it.inkTop).toInt() } ?: 0

    override fun bottom(line: Int, big: Boolean, row: Int): Int = ceil(words(line, big)[row].inkBottom).toInt()
}

fun lyricAt(np: NowPlayingModel, now: Long): LyricAt =
    LyricAt(np.shownAt(now).track, np.state.track, np.position(now, npNanosAt(now)), np.variant, np.state.playing && !np.seeking)

class LyricRowDraw(val word: Word, val x64: Int, val base: Int, val alpha: Float, val x0: Int, val y0: Int, val x1: Int, val y1: Int)

private fun rowDraw(w: Word, x64: Int, base: Int, a: Float, x0: Int, y0: Int, x1: Int, y1: Int, cap: Int): LyricRowDraw =
    LyricRowDraw(w, x64, base, a, x0, y0, max(x1, min(cap, x1 + ceil(max(0f, w.inkRight - w.width)).toInt())), y1)

fun lyricColumnRows(g: NpLayout, t: LyricTable, c: LyricColumn, words: (LyricBlock) -> List<Word>, rtl: Boolean): List<LyricRowDraw> {
    val ppu = fx(g.p)
    val x0 = px(Tok.edge, g.p)
    val x1 = px(Tok.canvasW - Tok.edge, g.p)
    val left64 = pen64(Tok.edge, g.p)
    val right64 = pen64(Tok.canvasW - Tok.edge, g.p)
    val cap = px(Tok.scrollbar_x, g.p)
    val out = ArrayList<LyricRowDraw>()
    for (d in c.draws) {
        for (f in listOfNotNull(d.old, d.new)) {
            val a8 = lyricAlpha(f.alpha)
            if (a8 <= 0) continue
            val k = f.block
            val w = words(k)
            val n = if (k.big) min(w.size, k.rows) else w.size
            var slot = d.top
            for (i in 0 until n) {
                val x = if (rtl && k.line >= 0) right64 - pen64(w[i].width) else left64
                out += rowDraw(w[i], x, t.baseline(k.pitch, t.role(k.big).size, slot, ppu), a8 / A256, x0, d.y0, x1, d.y1, cap)
                slot += k.pitch
            }
        }
    }
    return out
}

class LyricPlain(val y0: Int, val y1: Int, val spec: RowSpec)

fun lyricPlain(g: NpLayout, t: LyricTable): LyricPlain {
    val ppu = fx(g.p)
    val y0 = px64(t.top, ppu)
    val y1 = px64(t.end(t.ap), ppu)
    return LyricPlain(y0, y1, RowSpec(px64(t.ap, ppu) + 1, y1 - y0, 0, t.ap, ppu))
}

fun lyricPlainRows(g: NpLayout, t: LyricTable, rows: List<Word?>, q: Int, end: Boolean, rtl: Boolean): List<LyricRowDraw> {
    val ppu = fx(g.p)
    val pitch = t.ap
    val top = t.top
    val bottom = t.end(pitch)
    val plain = lyricPlain(g, t)
    val off = fxOfPx(q.toFloat(), ppu)
    val x0 = px(Tok.edge, g.p)
    val x1 = px(Tok.canvasW - Tok.edge, g.p)
    val left64 = pen64(Tok.edge, g.p)
    val right64 = pen64(Tok.canvasW - Tok.edge, g.p)
    val cap = px(Tok.scrollbar_x, g.p)
    val roomY1 = px64(t.bottom, ppu)
    val a = lyricAlpha(fx(t.plain.alpha)) / A256
    val out = ArrayList<LyricRowDraw>()
    var i = (off / pitch).toInt()
    while (i < rows.size) {
        val slot = top + i * pitch - off
        if (slot >= bottom) break
        val w = rows[i]
        if (w != null) {
            val base = t.baseline(pitch, t.plain.size, slot, ppu)
            val ry1 = if (end && i + 1 == rows.size) max(plain.y1, min(roomY1, base + ceil(w.inkBottom).toInt())) else plain.y1
            out += rowDraw(w, if (rtl) right64 - pen64(w.width) else left64, base, a, x0, plain.y0, x1, ry1, cap)
        }
        i++
    }
    return out
}

fun lyricPlainBar(g: NpLayout, p: LyricPlain, m: LyricsModel): ThumbRect? {
    val track = m.plainTrack
    return thumbOf(g.s, p.y0, p.y1, (p.y1 - p.y0).toLong(), track.topOf(track.count).toLong(), track.q.toLong(), g.p)
}

fun DrawScope.lyricRows(rows: List<LyricRowDraw>, alpha: Float) {
    for (r in rows) {
        if (r.y1 <= r.y0 || r.x1 <= r.x0) continue
        clipRect(r.x0.toFloat(), r.y0.toFloat(), r.x1.toFloat(), r.y1.toFloat()) { word(r.word, r.x64 / PEN, r.base.toFloat(), Ink.white, r.alpha * alpha) }
    }
}

fun DrawScope.npLyricColumn(g: NpLayout, c: LyricColumn, ink: LyricsInk, alpha: Float) {
    lyricRows(lyricColumnRows(g, ink.t, c, { k -> ink.words(k.line, k.big) }, ink.rtl), alpha)
}

fun DrawScope.npLyricPlain(g: NpLayout, m: LyricsModel, ink: LyricsInk, now: Long, alpha: Float) {
    val track = m.plainTrack
    lyricRows(lyricPlainRows(g, ink.t, ink.plain, track.q, track.q >= track.max, ink.rtl), alpha)
    val bar = lyricPlainBar(g, lyricPlain(g, ink.t), m) ?: return
    thumb(bar, m.plainFade.at(now) * alpha)
}

@Composable
private fun plainTouch(m: LyricsModel, g: NpLayout, spec: RowSpec): ListTouch {
    val s = g.s
    val state = remember(m) { LazyListState(m.plainTrack.drawFirst, 0) }
    val scope = rememberCoroutineScope()
    val coast = rememberCoast(Kind.List, s)
    val touch = remember(m, state, s.touchPpu, s.stdPpu, s.ppu, spec.pitch, coast) { ListTouch(Kind.List, state, s, scope, m.plainTrack, spec.pitch, coast) }
    DisposableEffect(touch) { onDispose { touch.stop() } }
    SideEffect { touch.onScroll = { m.scrolled() } }
    return touch
}

@Composable
fun NpLyrics(m: LyricsModel, np: NowPlayingModel, g: NpLayout, covers: Covers?, a: Arbiter, now: () -> Long, alpha: () -> Float = { 1f }) {
    val measurer = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val t = remember(g) { LyricTable.of(g.s) }
    val plain = remember(g, t) { lyricPlain(g, t) }
    val touch = plainTouch(m, g, plain.spec)
    var frame by remember { mutableLongStateOf(now()) }
    var view by remember { mutableStateOf<LyricView>(LyricView.Blank) }
    val shown = np.shown
    val ink = remember(m.gen, shown.title, shown.next, t, g, measurer, fonts, density, covers) {
        LyricsInk(t, g, measurer, fonts, density, covers, m.lines.map { it.text }, shown.title, shown.next, m.plain)
    }
    val inkNow by rememberUpdatedState(ink)
    LaunchedEffect(m, np, touch) {
        while (true) {
            withFrameMillis { at ->
                m.stepTo(at)
                val v = m.view(at, lyricAt(np, at))
                if (v == LyricView.Plain) {
                    m.plainRows(plain.spec, inkNow.plain.size)
                    m.plainFade.track(lyricPlainBar(g, plain, m) != null, touch.moving || touch.scroll.dragging, at)
                }
                view = v
                frame = at
            }
            if (m.moving(frame)) continue
            val seen = m.revision to np.revision
            val due = m.wakeAt(lyricAt(np, frame), frame)
            val wait = suspend { snapshotFlow { m.revision to np.revision }.first { it != seen } }
            if (due < 0) wait() else withTimeoutOrNull(max(1L, due - frame)) { wait() }
        }
    }
    Box(Modifier.pxSize(g.s.widthPx, g.s.heightPx)) {
        Canvas(Modifier.pxSize(g.s.widthPx, g.s.heightPx)) {
            when (val v = view) {
                LyricView.Blank -> {}
                LyricView.Plain -> npLyricPlain(g, m, ink, frame, alpha())
                is LyricView.Column -> npLyricColumn(g, LyricRows.column(t, v.from, v.to, v.p, fx(g.p), ink.lines, ink.next, ink), ink, alpha())
            }
        }
        if (view == LyricView.Plain) {
            val x0 = px(Tok.edge, g.p)
            val x1 = px(Tok.canvasW - Tok.edge, g.p)
            Box(Modifier.pxAt(Edges(x0, plain.y0, x1, plain.y1)).listTarget(a, touch))
        }
    }
}
