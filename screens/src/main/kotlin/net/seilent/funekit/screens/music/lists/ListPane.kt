package net.seilent.funekit.screens.music.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.GlowPair
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.focusAfterScroll
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.listThumbOf
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.Paging
import net.seilent.funekit.screens.music.hub.hubScrollInto
import net.seilent.funekit.screens.music.staleIn
import kotlin.math.max

class ListGeo(val s: Scale, rowsTop: Float, val pitch: Int) {
    val top: Int = px(rowsTop, s.ppu)
    val bottom: Int = px(s.stdContentBottom, s.stdPpu)
    val view: Int = bottom - top
    val x: Float = penPx(pen64(Tok.hubEdge, s.ppu))
    val countBase: Int = px64(fx(s.board.page_countBaseline), fx(s.ppu))
    val emptyBase: Int = px64(fx(rowsTop) + fx(Tok.qpHintDrop), fx(s.ppu))

    fun thumb(count: Int, off: Int): ThumbRect? = listThumbOf(s, top, bottom, count, pitch, off.toLong())
}

data class ListLook(val alpha: Float, val focused: Boolean, val glow: Float?, val back: Boolean = false)

class ListPane(val wake: Channel<Unit> = Channel(Channel.CONFLATED)) {
    val track = RowTrack(1, 0)
    var ppu = 0f
    var counted = -1
    val focus = FocusTrack().also { it.attach() }
    val press = PressSlots()
    val glow = GlowPair()
    val fade = ThumbFade()
    var paging: Paging? = null
    var whole: List<Row>? = null
    var pitch = 1
    var view = 0
    var aim by mutableIntStateOf(-1)
        private set
    var aims by mutableIntStateOf(0)
        private set
    var lists: List<ListTouch> = emptyList()

    fun poke() {
        wake.trySend(Unit)
    }

    fun row(i: Int): Row? {
        val w = whole
        return if (w != null) w.getOrNull(i) else paging?.window?.row(i)
    }

    var present: (Int) -> Boolean = { row(it) != null }

    fun geometry(spec: RowSpec) {
        pitch = max(1, spec.pitch)
        view = spec.view
        track.resize(spec)
    }

    fun scaleTo(to: Float, spec: RowSpec) {
        if (ppu > 0f && ppu != to) track.rescale(ppu, to, spec)
        ppu = to
        geometry(spec)
    }

    fun look(i: Int, now: Long, focusShown: Boolean, on: Float, off: Float): ListLook {
        focus.revision
        press.revision
        val alpha = focus.rowAlpha(i, now, on, off)
        val back = glow.glow.key != i && glow.back.key == i
        return ListLook(alpha, focusShown && i == focus.row, if (glow.glow.key == i || back) press.lit(i, now) else null, back)
    }

    fun animating(now: Long): Boolean = focus.animating(now) || press.staleIn(track.drawFirst, track.rowAt(track.q + track.view - 1), 1, now) || glow.glow.moving(now) || glow.back.moving(now) || fade.fading(now)

    fun wakeAt(now: Long): Long? = listOfNotNull(press.due(now), fade.wakeAt(now).takeIf { it >= 0 }).minOrNull()

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        fade.track(thumb, moving, now)
        for (g in listOf(glow.glow, glow.back)) {
            val k = g.key ?: continue
            if (!press.framed(k, now) && press.lit(k, now) == 0f) g.clear()
        }
    }

    fun aimTo(q: Int) {
        aim = q
        aims++
    }

    fun into(r: Int) {
        aimTo(hubScrollInto(r * pitch, pitch, track.q, view))
    }

    fun afterScroll(off: Int, size: Int, now: Long) {
        if (size == 0) return
        val r = focusAfterScroll(focus.row, size, off.toLong(), view.toLong(), pitch.toLong())
        if (r != focus.row) focus.set(r, size, now, false)
    }

    fun go(size: Int, row: Int? = null, px: Int? = null) {
        val t = lists.firstOrNull()
        if (t != null) {
            t.count(size, track.first)
            row?.let { t.toRow(it) }
            px?.let { t.toPx(it) }
        } else {
            track.count(size, track.first)
            row?.let { track.toRow(it) }
            px?.let { track.at(it) }
        }
        counted = size
    }

    fun place(i: Int, size: Int, now: Long, animate: Boolean) {
        if (size == 0) return
        val r = i.coerceIn(0, size - 1)
        focus.set(r, size, now, animate)
        if (animate) {
            go(size)
            into(r)
        } else {
            go(size, row = r)
        }
        poke()
    }

    fun down(r: Int, size: Int, at: Long) {
        focus.set(r, size, at, true)
        press.down(r, at)
        glow.arm(r, at)
    }

    fun cut() {
        press.drop()
        glow.off()
    }

    fun reset() {
        press.reset()
        glow.off()
    }
}

class ListLines(private val countOf: (String) -> Word, private val lineOf: (String) -> Word) {
    private val counts = HashMap<String, Word>()
    private val lines = HashMap<String, Word>()

    fun count(text: String): Word {
        if (text !in counts && counts.size > 1) counts.clear()
        return counts.getOrPut(text) { countOf(text) }
    }

    fun line(text: String): Word = lines.getOrPut(text) { lineOf(text) }
}

open class ListPaneDraw(
    val geo: ListGeo,
    private val inkOf: (RowModel) -> RowInk,
    val words: ListLines,
    val accent: Color,
    val accentOff: Boolean,
) {
    val s: Scale get() = geo.s
    private val inks = HashMap<RowModel, RowInk>()

    fun ink(r: RowModel): RowInk = inks.getOrPut(r) { inkOf(r) }

    fun keep(models: Set<RowModel>) {
        inks.keys.retainAll(models)
    }

    fun DrawScope.paneRow(g: GlowPair, look: ListLook, k: RowInk?, rec: DrawRecord?, name: String, focusShown: Boolean) {
        if (k == null) return
        val glow = look.glow?.let { RowGlow(if (look.back) g.back else g.glow, it) }
        listRow(k, s, look.alpha, look.focused, 1f, accent, accentOff, glow, null, focusShown)
        rec?.row(name, k, s, size.width.toInt(), look.alpha, look.focused, 1f, glow != null, accent, accentOff, focusShown)
    }
}

fun DrawScope.listLines(geo: ListGeo, w: ListLines, count: String?, countAlpha: Float, empty: String?, rec: DrawRecord?, name: String) {
    count?.let { text ->
        val c = w.count(text)
        val a = Roles.listCount.alpha * countAlpha
        word(c, geo.x, geo.countBase.toFloat(), Ink.white, a)
        rec?.word("$name.count", c, geo.x, geo.countBase.toFloat(), a)
    }
    empty?.let { text ->
        val e = w.line(text)
        word(e, geo.x, geo.emptyBase.toFloat(), Ink.white, Roles.quickplayHint.alpha)
        rec?.word("$name.empty", e, geo.x, geo.emptyBase.toFloat(), Roles.quickplayHint.alpha)
    }
}

fun listWords(s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?): ListLines {
    val cs = roleStyle(Roles.listCount, s, density, Ink.white)
    val qs = roleStyle(Roles.quickplayHint, s, density, Ink.white, ppu = s.stdPpu)
    return ListLines(
        { t -> measurer.word(t, Roles.listCount, cs, fonts, density, covers) },
        { t -> measurer.word(t, Roles.quickplayHint, qs, fonts, density, covers) },
    )
}
