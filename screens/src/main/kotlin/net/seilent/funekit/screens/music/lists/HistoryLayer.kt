package net.seilent.funekit.screens.music.lists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.PageChrome
import net.seilent.funekit.kit.ThumbLayer
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.thumbProps
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.accentShown
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.pivotChromeAt
import net.seilent.funekit.screens.music.pivotSwitch
import net.seilent.funekit.screens.music.pivotWatch

private const val HIDDEN = -1

@Composable
fun HistoryLayer(
    m: HistoryModel,
    draw: HistoryDraw,
    chrome: Chrome,
    source: MusicSource?,
    a: Arbiter,
    bands: List<Band>,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    frozen: Boolean,
    hideFocus: Boolean,
    snap: Int,
    onBack: () -> Unit,
    onSheet: (SheetTarget) -> Boolean,
    leavesLit: () -> Boolean = { false },
    pageCovered: Boolean = false,
    onPlay: ((HistoryAct) -> Boolean)? = null,
) {
    val s = draw.s
    val geo = draw.geo
    val scope = rememberCoroutineScope()
    val titleShown = accentShown(hideFocus, pageCovered)
    val frozenNow = rememberUpdatedState(frozen)
    var frame by remember { mutableLongStateOf(0L) }
    val p = m.pivot
    val pn = m.panes[p]
    val size = m.loads.let { m.count(p) }
    val sizeNow = rememberUpdatedState(size)
    val t = listTouch(pn, s, RowSpec(geo.pitch, geo.view))
    SideEffect {
        for ((i, q) in m.panes.withIndex()) {
            q.lists = if (i == p) listOf(t) else emptyList()
            if (i != p) q.scaleTo(s.ppu, RowSpec(geo.pitch, geo.view))
        }
        t.onScroll = { at ->
            pn.afterScroll(t.px, sizeNow.value, at)
            m.poke()
        }
        if (pn.counted != size) {
            t.count(size, t.first)
            pn.counted = size
        }
    }
    DisposableEffect(m) { onDispose { m.drop() } }

    LaunchedEffect(m) {
        while (true) {
            withFrameMillis { at ->
                for (q in m.panes) q.press.look(at)
                frame = at
                m.tick(at, geo.thumb(m.count(m.pivot), m.pane.track.q) != null, m.pane.lists.any { it.moving || it.scroll.dragging })
            }
            if (m.animating(frame) || m.pane.lists.any { it.moving || it.scroll.dragging }) continue
            val w = m.wakeAt(frame)
            if (w == null) m.wake.receive() else withTimeoutOrNull(w - frame) { m.wake.receive() }
        }
    }
    LaunchedEffect(m, t) { snapshotFlow { t.px to t.moving }.collect { m.poke() } }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        src.player.state.map { it.track }.distinctUntilChanged().collect { track ->
            m.current = track
            m.playing = track?.let { id -> quiet { src.viewed { v -> v.trackIds(id) } } }
            m.poke()
        }
    }
    suspend fun rest() {
        while (true) {
            m.pane.atRest { frozenNow.value }
            if (!m.switching(now())) return
            withFrameMillis {}
        }
    }
    LaunchedEffect(m, source, live) {
        val src = source ?: return@LaunchedEffect
        combine(src.quickplay.revision, src.library.generation) { r, g -> ViewKey(r, g) }.collectLatest { k ->
            if (m.seen == null || (m.stale(k) && live)) {
                if (m.seen != null) rest()
                quiet { src.viewed { v -> m.load(v, k, now()) } }
                draw.keep(m.panes.indices.flatMap { i -> (0 until m.count(i)).mapNotNull { m.rowModel(i, it) } }.toSet())
            }
        }
    }
    ListAim(pn, t)

    val rowTap = listRowTap(
        pn, live, { sizeNow.value }, now,
        onTap = { r ->
            val act = m.act(r)
            val open = onPlay
            val src = source
            when {
                act == HistoryAct.None -> false
                open != null -> open(act)
                else -> {
                    if (src != null) {
                        scope.launch(FailFast) {
                            quiet {
                                when (act) {
                                    is HistoryAct.Song -> src.player.play(listOf(act.id), 0, 0L)
                                    is HistoryAct.Album -> src.player.play(act.listing, m.generation, act.index, 0L)
                                    HistoryAct.None -> Unit
                                }
                            }
                        }
                    }
                    true
                }
            }
        },
        onLong = { r -> m.target(r)?.let(onSheet) == true },
    )
    val switching by remember(m) { derivedStateOf { m.switchAt != null && m.switching(frame) } }
    val side by remember(m) { derivedStateOf { m.side(frame) } }
    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx).then(Modifier.pivotWatch(m, chrome, live, now))) { covered ->
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { pageGround(bands, draw.accent) }
        Box(
            Modifier.pxSize(s.widthPx, s.heightPx).graphicsLayer {
                alpha = if (switching) 0f else 1f
                translationX = m.dragX(frame, s.ppu).toFloat()
            },
        ) {
            val empty = rememberLook(m, p, covered = covered) { m.loads to m.empty(p) }
            Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { with(draw) { lines(empty.value.second, null, "history") } }
            Column {
                Box(Modifier.pxSize(s.widthPx, geo.top))
                key(p) {
                    FuneList(a, t, Modifier.pxSize(s.widthPx, geo.view), if (live) 0 else HIDDEN, rowTap) {
                        items(size) { r ->
                            val look = rememberLook(p, r, m, pageCovered, snap, covered = covered) { m.look(p, r, frame) }
                            val data = rememberLook(p, r, m, covered = covered) { m.loads to m.rowModel(p, r) }
                            val ink = remember(data.value, draw) { data.value.second?.let(draw::ink) }
                            Canvas(Modifier.pxSize(s.widthPx, geo.pitch)) { with(draw) { row(p, look.value, ink, null, "", titleShown) } }
                        }
                    }
                }
            }
        }
        val at = m.switchAt
        if (switching && at != null) {
            val shownAt = if (side == m.pivot) at + Tok.motion_pivotOut.toLong() else at
            Box(
                Modifier.pxSize(s.widthPx, s.heightPx).graphicsLayer {
                    translationX = m.motion.dx(frame, s.ppu).toFloat()
                    alpha = m.motion.alpha(frame)
                },
            ) {
                key(side) {
                    val k = side
                    Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { with(draw) { content(k, shownAt, titleShown, null, "history") } }
                }
            }
        }
        val bar = geo.thumb(m.count(side), 0)
        if (bar != null) key(side, m.count(side)) { ThumbLayer(bar, covered, snap) { thumbProps(geo.thumb(m.count(side), m.panes[side].track.q), m.thumbAlpha(frame)) } }
        PageChrome(chrome, a, { pivotChromeAt(chrome, frame) }, onPivot = { pivotSwitch(m, chrome, it, now()) }, onHeader = if (live) onBack else null, leavesLit = leavesLit)
    }
}
