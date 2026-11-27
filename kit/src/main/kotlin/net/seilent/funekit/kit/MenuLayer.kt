package net.seilent.funekit.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import net.seilent.funekit.Scale
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.OverlayGate
import net.seilent.funekit.kit.motion.motionSeq
import net.seilent.funekit.kit.motion.rememberOverlayFrame
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.BlockBelow
import net.seilent.funekit.kit.touch.Slop
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.tapTarget
import kotlin.math.roundToInt

private const val HALF = 2
private const val PX_CENTRE = 0.5f

fun dialogPivot(w: Int, h: Int): Offset = Offset(w / HALF + PX_CENTRE, h / HALF + PX_CENTRE)

private fun DrawScope.faded(alpha: Float, paint: Paint, block: DrawScope.() -> Unit) {
    if (alpha >= 1f) {
        block()
        return
    }
    paint.alpha = alpha
    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
    block()
    drawContext.canvas.restore()
}

fun DrawScope.menuLayer(menu: Menu, ink: MenuInk, plan: MenuPlan, o: Overlay, s: Scale, now: Long, accent: Color, accentOff: Boolean, paint: Paint, tick: Boolean = true) {
    if (tick) menu.tick(now, ink, plan, s)
    if (o.kind == OverKind.Sheet) {
        val sh = o.sheet(now)
        drawRect(Ink.bg, Offset.Zero, size, sh.scrim)
        faded(sh.alpha, paint) {
            translate(0f, (sh.dy * plan.panelH).roundToInt().toFloat()) { menu(menu, ink, plan, s, now, accent, accentOff) }
        }
    } else {
        val md = o.dialog(now)
        drawRect(Ink.bg, Offset.Zero, size, md.scrim)
        withTransform({ scale(md.scale, md.scale, dialogPivot(size.width.toInt(), size.height.toInt())) }) {
            faded(md.alpha, paint) { menu(menu, ink, plan, s, now, accent, accentOff) }
        }
    }
}

@Composable
fun MenuOverlay(
    menu: Menu,
    overlay: Overlay,
    s: Scale,
    a: Arbiter,
    now: () -> Long,
    onPick: (Int) -> Boolean,
    onClose: () -> Unit,
    layer: Int,
    modifier: Modifier = Modifier,
    accent: Color = Ink.white,
    accentOff: Boolean = true,
    band: NavGround? = null,
    onHold: ((Int) -> Boolean)? = null,
    onScrim: () -> Boolean = { false },
) {
    MenuOverlay(menu, overlay, s, a, now, onPick, onClose, layer, modifier, accent, accentOff, band, onHold, onScrim, seen = { _, _ -> })
}

@Composable
internal fun MenuOverlay(
    menu: Menu,
    overlay: Overlay,
    s: Scale,
    a: Arbiter,
    now: () -> Long,
    onPick: (Int) -> Boolean,
    onClose: () -> Unit,
    layer: Int,
    modifier: Modifier,
    accent: Color,
    accentOff: Boolean,
    band: NavGround?,
    onHold: ((Int) -> Boolean)?,
    onScrim: () -> Boolean,
    seen: (State<Long>, DrawScope.() -> Unit) -> Unit,
) {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    val content = menu.content
    val ink = remember(content, s, accentOff, density, fonts, covers) { menuInk(m, fonts, menu, s, density, accentOff, covers) }
    val plan = remember(menu.version, ink) { menu.plan(ink) }
    SideEffect { menu.settle(plan) }
    val paint = remember { Paint() }
    BlockBelow(a, layer)
    val gate = remember(menu, overlay) { OverlayGate() }
    val none = remember(gate, overlay) { Tap({ gate.down(overlay, now()) }, {}, {}) }
    val pitch by rememberUpdatedState(plan.pitch)
    val close by rememberUpdatedState(onClose)
    val slop by rememberUpdatedState(a.slop)
    val moves = remember(overlay) { intArrayOf(0) }
    val frame = rememberOverlayFrame(overlay, now, menu::animating, menu::wakeAt, { menu.version }) { t ->
        menu.tick(t, ink, plan, s)
        if (overlay.movedSeq != moves[0]) {
            moves[0] = overlay.movedSeq
            a.cancel(layer, moves[0])
            menu.lift(t)
        }
    }
    val draw: DrawScope.() -> Unit = {
        menuLayer(menu, ink, plan, overlay, s, frame.value, accent, accentOff, paint, tick = false)
        if (band != null) navBand(s, band)
    }
    SideEffect { seen(frame, draw) }
    Box(modifier.pxSize(s.widthPx, s.heightPx).pointerInput(menu, overlay) { sheetDrag(menu, overlay, { slop }, now, { pitch }, { close() }) }) {
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx).graphicsLayer(), onDraw = draw)
        val shut = Tap({ gate.down(overlay, now()) }, {}, { if (gate.passes(overlay, false) && !onScrim()) onClose() })
        Box(Modifier.pxAt(Edges(0, 0, s.widthPx, if (band != null) navBand(s).y0 else s.heightPx)).tapTarget(a, shut, layer))
        if (menu.form == MenuForm.Sheet) {
            Box(Modifier.pxAt(Edges(0, menu.bottomPx - plan.panelH, s.widthPx, menu.bottomPx)).tapTarget(a, none, layer))
        } else {
            plan.titleRect?.let { Box(Modifier.pxAt(it).tapTarget(a, none, layer)) }
        }
        if (!menu.readOnly) for (slot in plan.rows) {
            val i = slot.i
            val e = Edges(0, slot.top, s.widthPx, slot.top + slot.h)
            if (i < menu.lead) {
                if (menu.form == MenuForm.Dlg) Box(Modifier.pxAt(e).tapTarget(a, none, layer))
            } else {
                val tap = Tap(
                    onPress = { if (gate.down(overlay, now(), a.downAt)) menu.press(i, now()) },
                    onCancel = { menu.press(-1, now()) },
                    onTap = { if (!gate.passes(overlay, menu.confirm) || !onPick(i)) menu.press(-1, now()) },
                    onLong = onHold?.let { h -> { gate.live(overlay) && !overlay.closing && !overlay.moving(now()) && h(i) } },
                )
                Box(Modifier.pxAt(e).tapTarget(a, tap, layer))
            }
        }
    }
}

private suspend fun PointerInputScope.sheetDrag(
    menu: Menu,
    overlay: Overlay,
    slop: () -> Slop,
    now: () -> Long,
    pitch: () -> Int,
    close: () -> Unit,
) {
    var first0 = 0
    swipeDownClose(
        overlay, slop, now,
        down = { first0 = menu.first },
        top = { menu.form == MenuForm.Dlg || menu.atTop },
        scrolled = { false },
        close = close,
        end = { menu.lift(now()) },
    ) { y0, y ->
        val p = pitch()
        val moved = menu.form == MenuForm.Sheet && p > 0 && menu.scrollTo(first0 + ((y0 - y) / p).toInt(), now())
        if (moved) menu.held = true
        moved
    }
}

internal suspend fun PointerInputScope.swipeDownClose(
    overlay: Overlay,
    slop: () -> Slop,
    now: () -> Long,
    down: () -> Unit,
    top: () -> Boolean,
    scrolled: () -> Boolean,
    close: () -> Unit,
    end: () -> Unit = {},
    onY: (Float, Float) -> Boolean,
) {
    awaitEachGesture {
        val d = awaitFirstDown(requireUnconsumed = false)
        val x0 = d.position.x
        val y0 = d.position.y
        if (overlay.closing || overlay.moving(now())) return@awaitEachGesture
        val seq = motionSeq
        down()
        val atTop = top()
        var low = y0
        var moved = false
        try {
            while (true) {
                val c = awaitPointerEvent().changes.firstOrNull { it.id == d.id } ?: break
                val ys = c.historical.map { it.position.y } + c.position.y
                for (y in ys) {
                    low = minOf(low, y)
                    if (overlay.movedSeq <= seq && onY(y0, y)) moved = true
                }
                if (!c.pressed) {
                    val swiped = slop().swipeDownOf(c.position.x - x0, c.position.y - y0) == Swipe.Down
                    if (overlay.movedSeq <= seq && atTop && !moved && !scrolled() && y0 - low <= slop().slop && swiped) close()
                    break
                }
            }
        } finally {
            end()
        }
    }
}
