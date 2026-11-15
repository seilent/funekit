package net.seilent.funekit.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.delay
import net.seilent.funekit.core.Section
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.OverlayGate
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.BlockBelow
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberListTouch
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.px
import kotlin.math.max

private const val LATIN = 27
private const val HALF = 2

object Jump {
    const val EXIT = -1
    const val NONE = EXIT - 1
    val SEARCH: Int get() = Section.count
    val cols: Int get() = Tok.jumpCols.toInt()
    private val exitCells: Int get() = Tok.jumpExitCells.toInt()
    val slotCount: Int get() = Section.count + exitCells
    val rows: Int get() = (slotCount + cols - 1) / cols
    private val searchSlot: Int get() = LATIN + 1
    val tile: Float get() = Tok.jumpPitch - Tok.jumpGap

    fun slotOf(i: Int): Int = when {
        i == EXIT -> 0
        i == SEARCH -> searchSlot
        i < LATIN -> i + 1
        else -> i + exitCells
    }

    fun itemOf(slot: Int): Int = when {
        slot == 0 -> EXIT
        slot == searchSlot -> SEARCH
        slot < searchSlot -> slot - 1
        else -> slot - exitCells
    }

    fun rowTop(slot: Int): Float = Tok.jumpTop + (slot / cols) * Tok.jumpPitch

    fun cellX(slot: Int): Float = Tok.jumpLeft + (slot % cols) * Tok.jumpPitch

    fun content(): Float = Tok.jumpTop + (rows - 1) * Tok.jumpPitch + tile + Tok.edge
}

class JumpGrid {
    val avail = BooleanArray(Section.count)
    var focus by mutableIntStateOf(Jump.EXIT)
        private set
    val press = PressSlots()
    val glow = Glow()
    val backGlow = Glow()
    var fade = ThumbFade()
        private set
    var bottom = 0f
        private set
    var opens by mutableIntStateOf(0)
        private set
    var aims by mutableIntStateOf(0)
        private set
    var offset = 0f
    var target = 0f
        private set
    var dragging = false

    fun open(available: BooleanArray, section: Int, bottomU: Float) {
        available.copyInto(avail)
        press.reset()
        glow.clear()
        backGlow.clear()
        bottom = bottomU
        focus = if (has(section)) section else (0 until Section.count).firstOrNull { avail[it] } ?: Jump.EXIT
        offset = offsetFor(focus, 0f)
        target = offset
        fade = ThumbFade()
        opens++
    }

    fun follow(off: Float) {
        offset = off
        target = off
    }

    fun has(i: Int): Boolean = i in 0 until Section.count && avail[i]

    private fun takes(i: Int): Boolean = i == Jump.EXIT || i == Jump.SEARCH || avail[i]

    fun limit(): Float = max(0f, Jump.content() - bottom)

    fun offsetFor(i: Int, off: Float): Float {
        val slot = Jump.slotOf(i)
        val top = Jump.rowTop(slot)
        val end = top + Jump.tile
        var o = off
        if (top - o < Tok.edge) {
            o = if (slot < Jump.cols) 0f else top - Tok.edge
        } else if (end - o > bottom - Tok.edge) {
            o = end - (bottom - Tok.edge)
        }
        return o.coerceIn(0f, limit())
    }

    fun inBand(slot: Int, off: Float): Boolean {
        val top = Jump.rowTop(slot) - off
        return top >= Tok.edge && top + Jump.tile <= bottom - Tok.edge
    }

    private fun setFocus(i: Int, now: Long) {
        focus = i
        press.focus(i, i != Jump.EXIT, now)
    }

    private fun stepFocus(delta: Int, now: Long): Boolean {
        var s = Jump.slotOf(focus) + delta
        while (s >= 0 && s < Jump.slotCount) {
            val i = Jump.itemOf(s)
            if (takes(i)) {
                setFocus(i, now)
                return true
            }
            s += delta
        }
        return false
    }

    fun focusAfterScroll(off: Float, now: Long) {
        val fs = Jump.slotOf(focus)
        if (inBand(fs, off)) return
        val d = if (Jump.rowTop(fs) - off < Tok.edge) 1 else -1
        var s = if (d > 0) 0 else Jump.slotCount - 1
        while (s >= 0 && s < Jump.slotCount) {
            if (inBand(s, off)) {
                val i = Jump.itemOf(s)
                if (takes(i)) {
                    if (i != focus) setFocus(i, now)
                    return
                }
            }
            s += d
        }
    }

    fun hit(x: Float, y: Float, off: Float): Int {
        val cx = x - (Tok.jumpLeft - Tok.jumpGap / HALF)
        val cy = y + off - (Tok.jumpTop - Tok.jumpGap / HALF)
        if (cx < 0 || cy < 0) return Jump.NONE
        val col = (cx / Tok.jumpPitch).toInt()
        val slot = (cy / Tok.jumpPitch).toInt() * Jump.cols + col
        if (col >= Jump.cols || slot >= Jump.slotCount) return Jump.NONE
        val i = Jump.itemOf(slot)
        return if (takes(i)) i else Jump.NONE
    }

    fun down(i: Int, now: Long) {
        focus = i
        press.down(i, now)
    }

    fun step(delta: Int, now: Long): Boolean {
        val moved = stepFocus(delta, now)
        val to = offsetFor(focus, target)
        if (dragging || to == target) return moved
        target = to
        aims++
        return true
    }

    fun thumbOn(s: Scale): Boolean = jumpThumb(this, s, offset) != null

    fun keyDown(now: Long) = press.keyDown(focus, focus != Jump.EXIT, now)

    fun keyUp(now: Long) = press.keyUp(now)
}

class JumpInk(val exit: Word, val letters: List<Word>)

class JumpCells(s: Scale) {
    private val sp = s.stdPpu
    private val xs = IntArray(Jump.slotCount) { px(Jump.cellX(it), sp) }
    private val tile = px(Jump.tile, sp)

    fun at(slot: Int, off: Float): Edges {
        val y = px(Jump.rowTop(slot) - off, sp)
        return Edges(xs[slot], y, xs[slot] + tile, y + tile)
    }
}

fun jumpThumb(g: JumpGrid, s: Scale, off: Float): ThumbRect? {
    val sp = s.stdPpu
    val bottomPx = px(g.bottom, sp)
    val limitPx = px(g.limit(), sp)
    return thumbOf(s, px(Tok.edge, sp), px(g.bottom - Tok.edge, sp), bottomPx.toLong(), (limitPx + bottomPx).toLong(), px(off, sp).coerceAtMost(limitPx).toLong(), sp)
}

private fun DrawScope.border(t: Edges, bw: Int, alpha: Float) = Rows.letterBorder(t, bw).forEach {
    drawRect(Ink.white, Offset(it.x0.toFloat(), it.y0.toFloat()), Size(it.w.toFloat(), it.h.toFloat()), alpha)
}

private fun JumpGrid.glowOf(i: Int, lit: Float, now: Long): Glow? = when (i) {
    press.on.item -> glow.follow(i, lit, press.framed(i, now))
    press.back.item -> backGlow.follow(i, lit, press.framed(i, now))
    else -> null
}

fun DrawScope.jumpGrid(g: JumpGrid, ink: JumpInk, cells: JumpCells, s: Scale, off: Float, now: Long) {
    val sp = s.stdPpu
    drawRect(Ink.bg, Offset.Zero, size)
    val e = cells.at(Jump.slotOf(Jump.EXIT), off)
    if (e.y1 > 0) {
        val ex64 = pen64(Tok.jumpExitCx, sp) - pen64(ink.exit.width) / HALF
        val exitPx = rolePx(Roles.jumpExit, s, sp)
        val base = e.y0 + px(Tok.jumpExitTop - Tok.jumpTop, sp) + Math.round(exitPx * Tok.capHeight)
        word(ink.exit, penPx(ex64), base.toFloat(), Ink.white, if (g.focus == Jump.EXIT) Tok.alpha_full else Roles.jumpExit.alpha)
    }
    val st = cells.at(Jump.slotOf(Jump.SEARCH), off)
    if (st.y1 > 0) {
        val focused = g.focus == Jump.SEARCH
        val bw = px(if (focused) Tok.letterTileBorderFocused else Tok.letterTileBorder, sp)
        val ba = if (focused) Tok.alpha_full else Tok.alpha_low
        val lit = g.press.look(Jump.SEARCH, now)
        border(st, bw, ba)
        val cx = (st.x0 + st.x1) / HALF
        val cy = (st.y0 + st.y1) / HALF
        val c = Offset(cx.toFloat(), cy.toFloat())
        val gi = px(Tok.jumpSearchGlyph, sp)
        val gs = gi.toFloat()
        glyphPress(Glyphs.searchRing, gi, cx, cy, lit, sp)
        glyphPress(Glyphs.searchHandle, gi, cx, cy, lit, sp)
        glyph(Glyphs.searchRing, c, gs, Ink.white, Roles.letterGlyph.alpha)
        glyph(Glyphs.searchHandle, c, gs, Ink.white, Roles.letterGlyph.alpha)
    }
    for (i in 0 until Section.count) {
        val t = cells.at(Jump.slotOf(i), off)
        if (t.y1 <= 0 || t.y0 >= size.height) continue
        val focused = g.focus == i
        val bw = px(if (focused) Tok.letterTileBorderFocused else Tok.letterTileBorder, sp)
        val ba = if (focused) Tok.alpha_full else Tok.alpha_low
        val lit = g.press.look(i, now)
        if (g.avail[i]) border(t, bw, ba)
        clipRect(t.x0.toFloat(), t.y0.toFloat(), t.x1.toFloat(), t.y1.toFloat()) {
            val l = ink.letters[i]
            val x = (t.x1 - px(Tok.jumpLetterInsetX, sp)) - l.width
            val base = t.y1 - px(Tok.jumpLetterInsetBaseline, sp)
            g.glowOf(i, lit, now)?.let { gl -> pressGlow(gl, listOf(GlowText(l, pen64(x), base, rolePx(Roles.jumpCell, s, sp))), t, sp, gl.level(now)) }
            word(l, x, base.toFloat(), Ink.white, if (g.avail[i]) Roles.jumpCell.alpha else Tok.jump_emptyAlpha)
        }
    }
    jumpThumb(g, s, off)?.let { thumb(it, g.fade.at(now)) }
}

@Composable
fun JumpGridOverlay(
    g: JumpGrid,
    overlay: Overlay,
    s: Scale,
    a: Arbiter,
    now: () -> Long,
    onPick: (Int) -> Unit,
    onExit: () -> Unit,
    onSearch: () -> Unit,
    layer: Int,
    modifier: Modifier = Modifier,
    band: NavGround? = null,
) {
    require(s.fit == null && s.ppu == s.stdPpu)
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val sp = s.stdPpu
    val covers = rememberCovers()
    val ink = remember(s, density, fonts, covers) {
        fun style(r: Role) = roleStyle(r, s, density, Ink.white, ppu = sp)
        JumpInk(
            m.word("exit", Roles.jumpExit, style(Roles.jumpExit), fonts, density, null),
            (0 until Section.count).map {
                val l = Section.letter(it)
                m.word(l, Roles.jumpCell, style(Roles.jumpCell), fonts, density, covers)
            },
        )
    }
    val cells = remember(s) { JumpCells(s) }
    val touch = rememberListTouch(Kind.Grid, s)
    val tops = remember(sp) { (0..Jump.rows).map { px(Tok.jumpTop + it * Tok.jumpPitch, sp) } }
    val end = px(Jump.content(), sp)
    fun offset(): Float {
        val i = touch.state.firstVisibleItemIndex
        return ((if (i == 0) 0 else tops[i - 1]) + touch.state.firstVisibleItemScrollOffset) / sp
    }
    var frameAt by remember { mutableLongStateOf(0L) }
    var pokes by remember { mutableIntStateOf(0) }
    LaunchedEffect(g, g.opens) { touch.state.scrollToItem(0, px(g.offset, sp)) }
    LaunchedEffect(g, g.aims) {
        if (g.aims == 0) return@LaunchedEffect
        val from = offset()
        val to = g.target
        val d = Tok.motion_scroll
        var t0 = -1L
        var e = 0f
        while (e < d) {
            withFrameMillis {
                if (t0 < 0) t0 = it
                e = (it - t0).toFloat()
                val want = lerp(from, to, Curves.easeIn.at((e / d).coerceIn(0f, 1f)))
                touch.state.dispatchRawDelta((want - offset()) * sp)
                g.offset = offset()
            }
        }
    }
    LaunchedEffect(g, g.opens, pokes, touch.moving) {
        while (true) {
            withFrameMillis {
                frameAt = it
                g.dragging = touch.scroll.dragging
                if (touch.moving) {
                    g.follow(offset())
                    g.focusAfterScroll(g.offset, it)
                } else {
                    g.offset = offset()
                }
                g.fade.track(g.thumbOn(s), touch.moving, it)
            }
            if (g.press.stale(frameAt) || touch.moving || g.fade.fading(frameAt)) continue
            val w = listOfNotNull(g.fade.wakeAt(frameAt).takeIf { it >= 0 }, g.press.due(frameAt)).minOrNull() ?: break
            delay(w - frameAt)
        }
    }
    val paint = remember { Paint() }
    val gate = remember(overlay) { OverlayGate() }
    BlockBelow(a, layer)
    val slop by rememberUpdatedState(a.slop)
    val exit by rememberUpdatedState(onExit)
    val drag = Modifier.pointerInput(overlay, touch) {
        swipeDownClose(
            overlay, { slop }, now,
            down = {},
            top = { offset() == 0f },
            scrolled = { offset() != 0f },
            close = {
                touch.halt()
                exit()
            },
        ) { _, _ -> false }
    }
    Box(modifier.pxSize(s.widthPx, s.heightPx).then(drag)) {
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) {
            val t = frameAt
            val v = overlay.jump(now())
            val off = offset()
            if (v >= 1f) {
                jumpGrid(g, ink, cells, s, off, t)
            } else {
                paint.alpha = v
                drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                jumpGrid(g, ink, cells, s, off, t)
                drawContext.canvas.restore()
            }
            if (band != null) navBand(s, band)
        }
        FuneList(a, touch, Modifier.pxSize(s.widthPx, px(g.bottom, sp)), layer) {
            item { Box(Modifier.pxSize(s.widthPx, tops[0])) }
            items(Jump.rows) { r ->
                Box(Modifier.pxSize(s.widthPx, tops[r + 1] - tops[r])) {
                    for (c in 0 until Jump.cols) {
                        val slot = r * Jump.cols + c
                        val i = if (slot < Jump.slotCount) Jump.itemOf(slot) else Jump.NONE
                        if (i == Jump.EXIT || i == Jump.SEARCH || g.has(i)) {
                            val x0 = px(Tok.jumpLeft - Tok.jumpGap / HALF + c * Tok.jumpPitch, sp)
                            val y0 = px(Tok.jumpTop - Tok.jumpGap / HALF + r * Tok.jumpPitch, sp) - tops[r]
                            val tap = Tap(
                                onPress = {
                                    if (gate.down(overlay, now())) {
                                        g.down(i, now())
                                        pokes++
                                    }
                                },
                                onCancel = {
                                    g.press.cancel(now())
                                    pokes++
                                },
                                onTap = {
                                    if (gate.passes(overlay, false)) {
                                        g.press.tap(now())
                                        pokes++
                                        when (i) {
                                            Jump.EXIT -> onExit()
                                            Jump.SEARCH -> onSearch()
                                            else -> onPick(i)
                                        }
                                    }
                                },
                            )
                            Box(Modifier.pxAt(Edges(x0, y0, x0 + px(Tok.jumpPitch, sp), y0 + px(Tok.jumpPitch, sp))).tapTarget(a, tap, layer))
                        }
                    }
                }
            }
            item { Box(Modifier.pxSize(s.widthPx, max(0, end - tops[Jump.rows]))) }
        }
    }
}
