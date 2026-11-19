package net.seilent.funekit.screens.music.lists

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.accentShown
import net.seilent.funekit.screens.music.hub.HubAct
import net.seilent.funekit.screens.music.overlay.SheetTarget

private const val AHEAD_ROWS = 2

@Composable
fun ListViewLayer(
    m: ListViewModel,
    draw: ListViewDraw,
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
    onPlay: ((HubAct.Play) -> Boolean)? = null,
) {
    val s = draw.s
    val geo = draw.geo
    val scope = rememberCoroutineScope()
    val titleShown = accentShown(hideFocus, pageCovered)
    val frozenNow = rememberUpdatedState(frozen)
    fun keepShown() {
        val f = m.pane.track.q / geo.pitch - AHEAD_ROWS
        val l = (m.pane.track.q + geo.view - 1) / geo.pitch + AHEAD_ROWS
        draw.keep((f..l).mapNotNull { m.rowModel(it) }.toSet())
    }
    val rows: suspend (Int, Int) -> Unit = { f, l ->
        source?.let { src -> quiet { src.viewed { v -> m.show(v, f - AHEAD_ROWS, l + AHEAD_ROWS) } } }
        keepShown()
    }
    ListPaneLayer(
        m.pane, geo, chrome, a, bands, draw.accent, { m.total }, now, live, visible, frozen, snap, onBack, leavesLit,
        onTap = { r ->
            when (val act = m.act(r)) {
                is HubAct.Play -> {
                    val open = onPlay
                    val src = source
                    if (open != null) {
                        open(act)
                    } else {
                        if (src != null) scope.launch(FailFast) { quiet { src.player.play(act.listing, m.generation, act.index, 0L) } }
                        true
                    }
                }
                else -> false
            }
        },
        onLong = { r -> m.target(r)?.let(onSheet) == true },
        tick = m::tick,
        animating = m::animating,
        wakeAt = m::wakeAt,
        rowsKey = source,
        onRows = if (source == null) null else rows,
        effects = {
            LaunchedEffect(m, source) {
                val src = source ?: return@LaunchedEffect
                src.player.state.collect { st ->
                    m.current = st.track
                    m.poke()
                }
            }
            LaunchedEffect(m, source, live) {
                val src = source ?: return@LaunchedEffect
                val revs = src.revisions(m.listing) ?: return@LaunchedEffect
                combine(revs, src.library.generation) { r, g -> ViewKey(r, g) }.collectLatest { k ->
                    m.note(k)
                    if (m.stale(k) && live) {
                        m.pane.atRest { frozenNow.value }
                        if (!m.stale(k)) return@collectLatest
                        quiet { src.viewed { v -> m.refresh(v, k, now()) } }
                        keepShown()
                    }
                }
            }
        },
        lines = { _, covered ->
            val lines = rememberLook(m, covered = covered) { m.count() to m.empty() }
            Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { with(draw) { lines(lines.value.first, lines.value.second, null, "view") } }
        },
        item = { r, frame, covered ->
            val look = rememberLook(r, m, pageCovered, snap, covered = covered) { m.look(r, frame()) }
            val data = rememberLook(r, m, covered = covered) { m.rowModel(r) }
            val ink = remember(data.value, draw) { data.value?.let(draw::ink) }
            Canvas(Modifier.pxSize(s.widthPx, geo.pitch)) { with(draw) { row(look.value, ink, null, "", titleShown) } }
        },
    )
}
