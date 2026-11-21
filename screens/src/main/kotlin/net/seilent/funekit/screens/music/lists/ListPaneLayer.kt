package net.seilent.funekit.screens.music.lists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.PageChrome
import net.seilent.funekit.kit.ThumbLayer
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.thumbProps
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberCoast
import net.seilent.funekit.screens.music.pivotChromeAt

private const val HIDDEN = -1

suspend fun ListPane.atRest(frozen: () -> Boolean) {
    snapshotFlow { frozen() || lists.any { it.moving || it.pressed } }.first { !it }
}

internal suspend fun <T> quiet(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

@Composable
fun listTouch(p: ListPane, s: Scale, spec: RowSpec): ListTouch {
    val state = remember(p) { LazyListState(p.track.drawFirst, 0) }
    val scope = rememberCoroutineScope()
    val coast = rememberCoast(Kind.List, s)
    val touch = remember(p, state, s.touchPpu, s.stdPpu, s.ppu, spec.pitch, coast) { ListTouch(Kind.List, state, s, scope, p.track, spec.pitch, coast) }
    DisposableEffect(touch) { onDispose { touch.stop() } }
    SideEffect {
        p.scaleTo(s.ppu, spec)
        if (!touch.moving && state.firstVisibleItemIndex != p.track.drawFirst) state.requestScrollToItem(p.track.drawFirst)
    }
    return touch
}

@Composable
fun ListAim(p: ListPane, t: ListTouch) {
    LaunchedEffect(p, p.aims) {
        if (p.aims == 0) return@LaunchedEffect
        val cuts = t.cuts
        val from = p.track.q.toFloat()
        val to = p.aim.toFloat()
        val d = Tok.motion_scroll
        var t0 = -1L
        var e = 0f
        while (e < d) {
            if (t.scroll.dragging || t.moving || t.cuts != cuts) break
            withFrameMillis { at ->
                if (t0 < 0) t0 = at
                e = (at - t0).toFloat()
                if (t.cuts == cuts) t.toPx(Math.round(lerp(from, to, Curves.easeIn.at((e / d).coerceIn(0f, 1f)))))
            }
        }
    }
}

fun listRowTap(p: ListPane, live: Boolean, size: () -> Int, now: () -> Long, onTap: (Int) -> Boolean, onLong: (Int) -> Boolean): (Int) -> Tap? = { r ->
    if (!live) {
        null
    } else {
        var started = false
        var valid = false
        Tap(
            onPress = {},
            onCancel = {
                if (valid) {
                    p.press.cancel(now())
                    p.poke()
                }
            },
            onCut = {
                p.cut()
                p.poke()
            },
            onTap = {
                if (valid) {
                    p.press.tap(now())
                    p.into(r)
                    if (!onTap(r)) p.press.untap(now())
                    p.poke()
                }
            },
            onLong = {
                if (valid && onLong(r)) {
                    p.reset()
                    p.poke()
                    true
                } else {
                    false
                }
            },
            onAt = {
                if (!started) {
                    started = true
                    val at = now()
                    val n = size()
                    if (r < n && p.present(r)) {
                        valid = true
                        p.down(r, n, at)
                        p.poke()
                    }
                }
            },
        )
    }
}

@Composable
fun ListPaneLayer(
    p: ListPane,
    geo: ListGeo,
    chrome: Chrome,
    a: Arbiter,
    bands: List<Band>,
    accent: Color,
    size: () -> Int,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    frozen: Boolean,
    snap: Int,
    onBack: () -> Unit,
    leavesLit: () -> Boolean,
    onTap: (Int) -> Boolean,
    onLong: (Int) -> Boolean,
    tick: (Long, Boolean, Boolean) -> Unit,
    animating: (Long) -> Boolean,
    wakeAt: (Long) -> Long?,
    rowsKey: Any?,
    onRows: (suspend (Int, Int) -> Unit)?,
    effects: @Composable () -> Unit,
    lines: @Composable (() -> Long, Boolean) -> Unit,
    item: @Composable (Int, () -> Long, Boolean) -> Unit,
) {
    val s = geo.s
    var frame by remember { mutableLongStateOf(0L) }
    val clock = remember { { frame } }
    val n = size()
    val sizeNow = rememberUpdatedState(size)
    val tickNow = rememberUpdatedState(tick)
    val animatingNow = rememberUpdatedState(animating)
    val wakeNow = rememberUpdatedState(wakeAt)
    val rowsNow = rememberUpdatedState(onRows)
    val t = listTouch(p, s, RowSpec(geo.pitch, geo.view))
    SideEffect {
        p.lists = listOf(t)
        t.onScroll = { at ->
            p.afterScroll(t.px, sizeNow.value(), at)
            p.poke()
        }
        if (p.counted != n) {
            t.count(n, t.first)
            p.counted = n
        }
    }
    DisposableEffect(p) { onDispose { p.lists = emptyList() } }

    LaunchedEffect(p) {
        while (true) {
            withFrameMillis { at ->
                p.press.look(at)
                frame = at
                tickNow.value(at, geo.thumb(sizeNow.value(), p.track.q) != null, t.moving || t.scroll.dragging)
            }
            if (animatingNow.value(frame) || t.moving || t.scroll.dragging) continue
            val w = wakeNow.value(frame)
            if (w == null) p.wake.receive() else withTimeoutOrNull(w - frame) { p.wake.receive() }
        }
    }
    LaunchedEffect(p, t) { snapshotFlow { t.px to t.moving }.collect { p.poke() } }
    effects()
    LaunchedEffect(p, rowsKey, onRows != null) {
        if (rowsNow.value == null) return@LaunchedEffect
        snapshotFlow { p.track.q / geo.pitch to (p.track.q + geo.view - 1) / geo.pitch }.distinctUntilChanged().conflate().collect { (f, l) ->
            rowsNow.value?.invoke(f, l)
            p.poke()
        }
    }
    ListAim(p, t)

    val rowTap = listRowTap(p, live, { sizeNow.value() }, now, onTap, onLong)
    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx)) { covered ->
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { pageGround(bands, accent) }
        lines(clock, covered)
        Column {
            Box(Modifier.pxSize(s.widthPx, geo.top))
            FuneList(a, t, Modifier.pxSize(s.widthPx, geo.view), if (live) 0 else HIDDEN, rowTap) {
                items(n) { r -> item(r, clock, covered) }
            }
        }
        val bar = geo.thumb(n, 0)
        if (bar != null) key(n) { ThumbLayer(bar, covered, snap) { thumbProps(geo.thumb(n, p.track.q), p.fade.at(frame)) } }
        PageChrome(chrome, a, { pivotChromeAt(chrome, frame) }, onPivot = {}, onHeader = if (live) onBack else null, leavesLit = leavesLit)
    }
}
