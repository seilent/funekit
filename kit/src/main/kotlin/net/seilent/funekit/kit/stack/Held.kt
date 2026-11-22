package net.seilent.funekit.kit.stack

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.touch.Axis
import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.kit.touch.Slop
import net.seilent.funekit.kit.touch.axisOf

class Held(val at: Long, val target: Boolean, val band: Gesture.Edge, val seq: Int, val dev: Boolean = false)

class HoldWatch(private val slop: Slop, private val x0: Float, private val y0: Float, t0: Long, private val target: Boolean, private val band: Gesture.Edge) {
    private val g = Gesture(slop).also { it.arm(x0, y0, band, ms = t0) }
    private var swiped = false

    fun move(x: Float, y: Float, t: Long): Boolean {
        val tap = slop.tap(x0, y0, x, y)
        if (!tap && !swiped && !(band != Gesture.Edge.None && axisOf(x - x0, y - y0) == Axis.Horizontal)) return false
        if (!tap) swiped = true
        g.move(x, y, t)
        return true
    }

    fun back(x: Float, y: Float, t: Long, bare: Boolean): Boolean {
        if (target && slop.tap(x0, y0, x, y)) return true
        return swipedBack(x, y, t, bare)
    }

    fun swipedBack(x: Float, y: Float, t: Long, bare: Boolean): Boolean = g.backs(g.up(x, y, t, bare))
}

suspend fun <P : Any> AwaitPointerEventScope.watchHeld(
    stack: PageStack<P>,
    down: PointerInputChange,
    slop: Slop,
    target: Boolean,
    band: Gesture.Edge,
    popKind: (Turn<P>) -> MoveKind,
) {
    if (stack.heldTap(down.uptimeMillis)) {
        val w = HoldWatch(slop, down.position.x, down.position.y, down.uptimeMillis, true, Gesture.Edge.None)
        watchLanded(stack, down, slop, w, true, false, popKind)
        return
    }
    if (!stack.hold(down.uptimeMillis, target, band)) {
        stack.touchDown()
        return
    }
    val dev = stack.held?.dev == true
    val w = HoldWatch(slop, down.position.x, down.position.y, down.uptimeMillis, target, band)
    val end = down.uptimeMillis + Tok.input_keyHoldMs.toLong()
    var last = down.uptimeMillis
    while (last < end) {
        val e = withTimeoutOrNull(end - last) { awaitPointerEvent(PointerEventPass.Initial) } ?: break
        val c = e.changes.firstOrNull { it.id == down.id }
        if (c == null || c.isConsumed) {
            stack.touch()
            return
        }
        last = c.uptimeMillis
        if (!c.pressed) {
            if (w.back(c.position.x, c.position.y, c.uptimeMillis, !c.positionChanged())) {
                c.consume()
                stack.heldBack(popKind)
            } else if (dev || !stack.heldReplay(down.uptimeMillis, target)) {
                stack.touch()
            }
            return
        }
        val kept = c.historical.all { w.move(it.position.x, it.position.y, it.uptimeMillis) } && w.move(c.position.x, c.position.y, c.uptimeMillis)
        if (!kept) {
            if (!dev && stack.heldReplay(down.uptimeMillis, target)) return
            stack.touch()
            if (dev) {
                c.consume()
                dropRest(down)
            }
            return
        }
    }
    if (!dev && stack.heldReplay(down.uptimeMillis, target)) return
    stack.touch()
    if (dev) dropRest(down) else watchLanded(stack, down, slop, w, target, band != Gesture.Edge.None, popKind)
}

private suspend fun AwaitPointerEventScope.dropRest(down: PointerInputChange) {
    while (true) {
        val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return
        c.consume()
        if (!c.pressed) return
    }
}

private suspend fun <P : Any> AwaitPointerEventScope.watchLanded(
    stack: PageStack<P>,
    down: PointerInputChange,
    slop: Slop,
    w: HoldWatch,
    target: Boolean,
    band: Boolean,
    popKind: (Turn<P>) -> MoveKind,
) {
    val x0 = down.position.x
    val y0 = down.position.y
    var tapping = target
    var swiping = band
    while (tapping || swiping) {
        val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return
        if (c.isConsumed) return
        tapping = tapping && c.historical.all { slop.tap(x0, y0, it.position.x, it.position.y) } && slop.tap(x0, y0, c.position.x, c.position.y)
        if (!c.pressed) {
            if (tapping || (swiping && w.swipedBack(c.position.x, c.position.y, c.uptimeMillis, !c.positionChanged()))) {
                c.consume()
                stack.heldBack(popKind)
            }
            return
        }
        swiping = swiping && c.historical.all { w.move(it.position.x, it.position.y, it.uptimeMillis) } && w.move(c.position.x, c.position.y, c.uptimeMillis)
    }
}
