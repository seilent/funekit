package net.seilent.funekit.kit.touch

import androidx.compose.animation.SplineBasedFloatDecayAnimationSpec
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px

class Tap(
    val onPress: () -> Unit,
    val onCancel: () -> Unit,
    val onTap: () -> Unit,
    val onLong: (() -> Boolean)? = null,
    val solo: Boolean = false,
    val onAt: ((Offset) -> Unit)? = null,
    val onCut: (() -> Unit)? = null,
)

class TapSlot {
    var tap: Tap? = null
}

class ListTouch(
    val kind: Kind,
    val state: LazyListState,
    scale: Scale,
    private val scope: CoroutineScope,
    internal val rows: RowTrack? = null,
    internal val pitch: Int = rows?.pitch ?: 0,
    coast: FloatDecayAnimationSpec? = null,
    edge: Edge = Edge.Follow,
) {
    private val track: Track = rows?.let { r ->
        object : Track {
            override val canMove: Boolean get() = r.canMove

            override fun by(delta: Float): Float {
                val f = r.drawFirst
                val used = r.by(delta)
                if (r.drawFirst != f) cross(r)
                return used
            }

            override fun atEnd(forward: Boolean): Boolean = r.atEnd(forward)
        }
    } ?: object : Track {
        override val canMove: Boolean get() = state.canScrollForward || state.canScrollBackward

        override fun by(delta: Float): Float = state.dispatchRawDelta(delta)

        override fun atEnd(forward: Boolean): Boolean = if (forward) !state.canScrollForward else !state.canScrollBackward
    }
    val scroll = Scroll(kind, scale.touchPpu, scale.stdPpu, scale.ppu, track, coast, edge)
    var over by mutableFloatStateOf(0f)
        private set
    var moving by mutableStateOf(false)
        private set
    var pressed by mutableStateOf(false)
        internal set
    var onScroll: (Long) -> Unit = {}
    var band: () -> Int = { 0 }
    var box: LayoutCoordinates? = null
        internal set
    var rowTap: (Int) -> Tap? = { null }
        internal set
    private var lift: Float? = null
    private var clock: Job? = null
    private var stopped = false

    val px: Int get() = checkNotNull(rows).q
    val first: Int get() = checkNotNull(rows).first
    val sub: Int get() = checkNotNull(rows).sub
    val above: Int get() = rows?.above ?: 0
    val drawSub: Int get() = checkNotNull(rows).drawSub

    fun topOf(r: Int): Int = checkNotNull(rows).topOf(r)

    fun rowHeight(i: Int): Int = topOf(i + 1) - topOf(i)

    fun toRow(i: Int) {
        val r = checkNotNull(rows)
        r.toRow(i)
        state.requestScrollToItem(r.drawFirst)
    }

    fun toPx(px: Int) {
        val r = checkNotNull(rows)
        r.at(px)
        state.requestScrollToItem(r.drawFirst)
    }

    fun count(n: Int, anchor: Int) {
        val r = checkNotNull(rows)
        r.count(n, anchor)
        state.requestScrollToItem(r.drawFirst)
    }

    private fun cross(r: RowTrack) {
        val d = (r.topOf(r.drawFirst) - (r.topOf(state.firstVisibleItemIndex) + state.firstVisibleItemScrollOffset)).toFloat()
        if (state.dispatchRawDelta(d) != d) state.requestScrollToItem(r.drawFirst)
    }

    internal fun sync() {
        over = scroll.over
        moving = scroll.moving || lift != null
        if (moving && !stopped && clock?.isActive != true) clock = scope.launch { while (moving) withFrameMillis { frame(it) } }
    }

    internal fun grab(at: Long): Boolean {
        val v = lift
        lift = null
        if (v != null) scroll.release(at, v)
        val fast = scroll.grab(at)
        sync()
        return fast
    }

    fun land() {
        if (!scroll.returning) return
        scroll.halt()
        sync()
    }

    fun halt() {
        if (lift == null && !scroll.moving) return
        lift = null
        scroll.endDrag()
        scroll.halt()
        sync()
    }

    var cuts = 0
        private set

    fun cancel() {
        cuts++
        lift = null
        scroll.endDrag()
        scroll.halt()
        sync()
    }

    fun stop() {
        stopped = true
        clock?.cancel()
        clock = null
    }

    internal fun release(fingerV: Float) {
        lift = fingerV
        sync()
    }

    internal fun frame(now: Long) {
        val v = lift
        if (v != null) {
            lift = null
            scroll.release(now, v)
        } else {
            val fl = scroll.flinging
            scroll.step(now)
            if (fl) onScroll(now)
        }
        sync()
    }
}

@Composable
fun rememberCoast(kind: Kind, scale: Scale): FloatDecayAnimationSpec? {
    val density = LocalDensity.current
    return remember(kind, scale.coast, density) { if (kind == Kind.List && scale.coast) SplineBasedFloatDecayAnimationSpec(density) else null }
}

@Composable
fun rememberListTouch(kind: Kind, scale: Scale, onRescale: () -> Unit = {}, rows: RowSpec? = null, edge: Edge = Edge.Follow): ListTouch {
    if (rows != null) return rememberRowTouch(kind, scale, onRescale, rows, edge)
    val ppu by rememberUpdatedState(scale.ppu)
    val state = rememberSaveable(saver = listSaver(
        save = { listOf(it.firstVisibleItemIndex.toFloat(), it.firstVisibleItemScrollOffset / ppu) },
        restore = { LazyListState(it[0].toInt(), px(it[1], ppu)) },
    )) { LazyListState() }
    val last = remember { floatArrayOf(scale.ppu) }
    SideEffect {
        if (last[0] != scale.ppu) {
            state.requestScrollToItem(state.firstVisibleItemIndex, px(state.firstVisibleItemScrollOffset / last[0], scale.ppu))
            last[0] = scale.ppu
            onRescale()
        }
    }
    val scope = rememberCoroutineScope()
    val coast = rememberCoast(kind, scale)
    val touch = remember(kind, state, scale.touchPpu, scale.stdPpu, scale.ppu, coast, edge) { ListTouch(kind, state, scale, scope, coast = coast, edge = edge) }
    DisposableEffect(touch) { onDispose { touch.stop() } }
    return touch
}

@Composable
private fun rememberRowTouch(kind: Kind, scale: Scale, onRescale: () -> Unit, rows: RowSpec, edge: Edge): ListTouch {
    val ppu by rememberUpdatedState(scale.ppu)
    val spec by rememberUpdatedState(rows)
    val track = rememberSaveable(saver = listSaver(
        save = { it.save(ppu) },
        restore = { RowTrack.restore(it, spec, ppu) },
    )) { RowTrack(rows.pitch, rows.view, 0, rows.above, rows.pitch64, rows.ppu64, rows.whole, rows.collapse, rows.top64) }
    val state = remember(track) { LazyListState(track.drawFirst, 0) }
    val last = remember { floatArrayOf(scale.ppu) }
    SideEffect {
        val f = track.drawFirst
        if (last[0] != scale.ppu) {
            track.rescale(last[0], scale.ppu, rows)
            last[0] = scale.ppu
            onRescale()
        }
        track.resize(rows)
        if (track.drawFirst != f) state.requestScrollToItem(track.drawFirst)
    }
    val scope = rememberCoroutineScope()
    val coast = rememberCoast(kind, scale)
    val touch = remember(kind, state, scale.touchPpu, scale.stdPpu, scale.ppu, coast, edge) { ListTouch(kind, state, scale, scope, track, rows.pitch, coast, edge) }
    DisposableEffect(touch) { onDispose { touch.stop() } }
    return touch
}

class Arbiter(val scale: Scale) {
    val slop = Slop(px(Tok.touchSlop, scale.touchPpu).toFloat(), px(Tok.gesture_swipe, scale.touchPpu).toFloat())
    val gesture = Gesture(slop)
    private var root: LayoutCoordinates? = null
    private class Spot(val c: LayoutCoordinates, val layer: Int)

    private val lists = LinkedHashMap<ListTouch, Spot>()
    private val taps = LinkedHashMap<TapSlot, Spot>()
    private val blocks = LinkedHashMap<Any, Int>()

    val modal: Int get() = blocks.values.maxOrNull() ?: 0

    var downAt = 0L
        internal set

    internal fun root(c: LayoutCoordinates) {
        root = c
    }

    internal fun place(t: ListTouch, c: LayoutCoordinates, layer: Int) {
        lists[t] = Spot(c, layer)
    }

    internal fun place(s: TapSlot, c: LayoutCoordinates, layer: Int) {
        taps[s] = Spot(c, layer)
    }

    internal fun block(key: Any, layer: Int) {
        blocks[key] = layer
    }

    internal fun drop(t: ListTouch) {
        lists.remove(t)
    }

    internal fun drop(s: TapSlot) {
        taps.remove(s)
    }

    internal fun unblock(key: Any) {
        blocks.remove(key)
    }

    private fun hits(spot: Spot, o: Offset, up: Int = 0): Boolean {
        val r = root ?: return false
        if (spot.layer < modal || !r.isAttached || !spot.c.isAttached) return false
        val b = r.localBoundingBoxOf(spot.c)
        return b.copy(top = b.top - up).contains(o)
    }

    private fun hits(t: ListTouch, spot: Spot, o: Offset): Boolean {
        val rows = t.rows
        val up = if (rows != null && rows.collapse) rows.above else 0
        if (!hits(spot, o, up)) return false
        if (up == 0 || rows == null) return true
        val y = o.y - (root ?: return false).localPositionOf(spot.c, Offset.Zero).y
        return y >= -rows.lift - t.band()
    }

    internal fun listAt(o: Offset): ListTouch? {
        val hit = lists.entries.filter { hits(it.key, it.value, o) }
        val top = hit.maxOfOrNull { it.value.layer } ?: return null
        return hit.last { it.value.layer == top }.key
    }

    internal fun tapAt(o: Offset): Tap? {
        val hit: List<Pair<Int, Tap?>> = taps.entries.filter { hits(it.value, o) }.map { it.value.layer to it.key.tap } +
            lists.entries.filter { hits(it.key, it.value, o) }.mapNotNull { e -> rowTapAt(e.key, e.value, o)?.let { e.value.layer to it } }
        val top = hit.maxOfOrNull { it.first } ?: return null
        return hit.last { it.first == top }.second
    }

    private fun rowTapAt(t: ListTouch, spot: Spot, o: Offset): Tap? {
        val r = t.rows ?: return null
        val y = o.y - (root ?: return null).localPositionOf(spot.c, Offset.Zero).y + t.over
        if (y < -r.lift || y >= r.view) return null
        val i = r.tapRow(y)
        return if (i in 0 until r.count) t.rowTap(i) else null
    }

    fun edgeAt(x: Float, width: Int): Gesture.Edge = when {
        x < px(Tok.gesture_edge, scale.touchPpu) -> Gesture.Edge.Left
        x >= width - px(Tok.gesture_edgeRight, scale.touchPpu) -> Gesture.Edge.Right
        else -> Gesture.Edge.None
    }
}

@Composable
fun Modifier.listTarget(a: Arbiter, t: ListTouch, layer: Int = 0): Modifier {
    DisposableEffect(a, t) { onDispose { a.drop(t) } }
    return onGloballyPositioned {
        a.place(t, it, layer)
        t.box = it
    }
}

@Composable
fun BlockBelow(a: Arbiter, layer: Int) {
    val key = remember(a) { Any() }
    DisposableEffect(a, key, layer) {
        a.block(key, layer)
        onDispose { a.unblock(key) }
    }
}

@Composable
fun Modifier.tapTarget(a: Arbiter, tap: Tap, layer: Int = 0): Modifier {
    val slot = remember(a) { TapSlot() }
    SideEffect { slot.tap = tap }
    DisposableEffect(a, slot) { onDispose { a.drop(slot) } }
    return onGloballyPositioned { a.place(slot, it, layer) }
}

interface Pan {
    fun begin(x: Float, origin: Float, down: Offset): Boolean

    fun drag(x: Float)

    fun end(s: Swipe)
}

class Swipes(
    val edges: Boolean,
    val bottom: Boolean,
    val turn: Boolean,
    val acts: (Swipe) -> Boolean,
    val onSwipe: (Swipe, Gesture.Edge) -> Unit,
    val pan: Pan? = null,
)

@Composable
fun Modifier.arbiter(a: Arbiter, swipes: Swipes): Modifier {
    val page by rememberUpdatedState(swipes)
    return onGloballyPositioned { a.root(it) }.pointerInput(a) {
        val longMs = Tok.input_keyHoldMs.toLong()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val x0 = down.position.x
            val y0 = down.position.y
            val t0 = down.uptimeMillis
            val vel = Velocity(Tok.motion_flingWindow.toLong())
            vel.add(t0, y0)
            val hit = a.tapAt(down.position)
            var list = if (hit?.solo == true) null else a.listAt(down.position)
            val under = a.listAt(down.position)
            under?.pressed = true
            val cut = list?.cuts ?: 0
            val caught = list?.grab(t0) == true
            var tap = if (caught) null else hit
            a.downAt = t0
            tap?.onPress?.invoke()
            tap?.onAt?.invoke(down.position)
            var long = tap?.onLong != null
            var held = false
            var dragging = false
            var took = false
            var panned: Pan? = null
            val edge = if (page.edges) a.edgeAt(x0, size.width) else Gesture.Edge.None
            val open = a.modal == 0
            val bottom = open && page.bottom && y0 >= size.height - px(Tok.gesture_bottom, a.scale.touchPpu)
            a.gesture.arm(x0, y0, edge, bottom, open && page.turn, t0)

            var faded: Tap? = null

            fun drop(cut: Boolean = false) {
                tap?.let {
                    val c = it.onCut
                    if (cut && c != null) c() else it.onCancel()
                    if (!cut) faded = it
                }
                tap = null
                long = false
            }

            fun take(cut: Boolean = false) {
                drop(cut)
                list?.let {
                    if (dragging) it.scroll.endDrag()
                    it.sync()
                }
                list = null
                dragging = false
            }

            fun swipeTake() {
                val f = faded
                faded = null
                take(true)
                f?.onCut?.invoke()
            }

            fun begin(x: Float, origin: Float): Boolean {
                val p = page.pan ?: return false
                if (panned != null || a.modal != 0 || !p.begin(x, origin, down.position)) return false
                panned = p
                return true
            }

            fun cutOff() {
                if (list?.let { it.cuts != cut } != true) return
                drop(true)
                list = null
                dragging = false
            }

            fun step(x: Float, y: Float, t: Long) {
                cutOff()
                if (list != null) vel.add(t, y)
                if (dragging) {
                    list?.let {
                        it.scroll.drag(y, vel.perS())
                        it.onScroll(t)
                        it.sync()
                    }
                } else if (!a.slop.tap(x0, y0, x, y)) {
                    drop()
                    val l = list
                    if (l != null) {
                        if (axisOf(x - x0, y - y0) == Axis.Horizontal) {
                            list = null
                        } else {
                            dragging = true
                            l.scroll.begin(y)
                            l.sync()
                        }
                    }
                } else {
                    tap?.onAt?.invoke(Offset(x, y))
                }
                val locked = a.gesture.move(x, y, t)
                if (locked && a.gesture.state == Gesture.State.Horizontal && !a.gesture.turned && begin(x, a.gesture.originX)) swipeTake()
                if (a.gesture.turned && !took && page.acts(a.gesture.commitAt(x, y))) {
                    took = true
                    a.gesture.keep()
                    swipeTake()
                    begin(x, a.gesture.originX)
                }
                panned?.drag(x)
            }

            var change: PointerInputChange = down
            try {
                while (true) {
                    val e = if (long) {
                        withTimeoutOrNull(longMs - (change.uptimeMillis - t0)) { awaitPointerEvent() }
                    } else {
                        awaitPointerEvent()
                    }
                    if (e == null) {
                        long = false
                        if (tap?.onLong?.invoke() == true) {
                            held = true
                            take()
                            a.gesture.cancel()
                        }
                        continue
                    }
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    change = c
                    val x = c.position.x
                    val y = c.position.y
                    val t = c.uptimeMillis
                    if (c.isConsumed) {
                        take(true)
                        a.gesture.cancel()
                        panned?.end(Swipe.None)
                        panned = null
                        if (!c.pressed) break
                        continue
                    }
                    for (h in c.historical) step(h.position.x, h.position.y, h.uptimeMillis)
                    if (!c.pressed) {
                        cutOff()
                        val bare = !c.positionChanged()
                        if (bare && !took) {
                            val lift = a.gesture.liftCommit(x, y, t)
                            if (lift.turned && page.acts(lift.swipe)) {
                                took = true
                                a.gesture.keep()
                                swipeTake()
                                begin(lift.x, lift.origin)
                            }
                        }
                        if (list != null) vel.add(t, y)
                        if (dragging) {
                            list?.release(vel.perS())
                        } else if (tap != null && !held && a.slop.tap(x0, y0, x, y)) {
                            tap?.onTap?.invoke()
                        } else {
                            tap?.onCancel?.invoke()
                        }
                        tap = null
                        val s = a.gesture.up(x, y, t, bare)
                        val p = panned
                        if (p != null) {
                            panned = null
                            p.drag(a.gesture.liftX)
                            p.end(if (a.modal == 0) s else Swipe.None)
                        } else if (s != Swipe.None && (if (open) a.modal == 0 else a.modal != 0 && a.gesture.backs(s)) && page.acts(s)) {
                            page.onSwipe(s, a.gesture.edge)
                        }
                        break
                    }
                    step(x, y, t)
                }
            } finally {
                under?.pressed = false
                drop(true)
                panned?.end(Swipe.None)
            }
        }
    }
}
