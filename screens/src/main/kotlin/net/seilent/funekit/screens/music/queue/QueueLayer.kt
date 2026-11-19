package net.seilent.funekit.screens.music.queue

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.accentShown
import net.seilent.funekit.screens.music.lists.ListPaneLayer
import net.seilent.funekit.screens.music.lists.quiet
import net.seilent.funekit.screens.music.overlay.SheetTarget

private const val AHEAD_ROWS = 2

@Composable
fun QueueLayer(
    m: QueueModel,
    draw: QueueDraw,
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
    onPlay: ((Int) -> Boolean)? = null,
) {
    val s = draw.s
    val geo = draw.geo
    val scope = rememberCoroutineScope()
    val focusShown = !hideFocus
    val titleShown = accentShown(hideFocus, pageCovered)
    val rows: suspend (Int, Int) -> Unit = { f, l -> source?.let { src -> quiet { src.viewed { v -> m.show(v, f - AHEAD_ROWS, l + AHEAD_ROWS) } } } }
    ListPaneLayer(
        m.pane, geo, chrome, a, bands, draw.accent, { m.size }, now, live, visible, frozen, snap, onBack, leavesLit,
        onTap = { r ->
            val open = onPlay
            val src = source
            if (open != null) {
                open(r)
            } else {
                if (src != null) scope.launch(FailFast) { quiet { m.tap(r, src) } }
                true
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
                src.library.generation.collect {
                    m.loaded = it > 0
                    m.poke()
                }
            }
            LaunchedEffect(m, source) {
                val src = source ?: return@LaunchedEffect
                src.player.state.collectLatest { st ->
                    quiet {
                        src.viewed { v ->
                            if (!m.opened) {
                                m.state(st)
                                m.show(v, st.index - AHEAD_ROWS, st.index + AHEAD_ROWS)
                                m.open(st, now())
                            } else if (m.changed(st)) {
                                m.sync(v, st, now())
                            }
                        }
                    }
                    m.poke()
                }
            }
        },
        lines = { frame, covered ->
            val lines = rememberLook(m, covered = covered) { m.countLine(frame()) to m.empty() }
            Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { with(draw) { lines(lines.value.first, lines.value.second, null, "queue") } }
        },
        item = { r, frame, covered ->
            val look = rememberLook(r, m, hideFocus, pageCovered, snap, covered = covered) { m.look(r, frame(), focusShown) }
            val data = rememberLook(r, m, covered = covered) { m.rowModel(r) }
            val ink = remember(data.value, draw) { data.value?.let(draw::ink) }
            Canvas(Modifier.pxSize(s.widthPx, geo.pitch)) { with(draw) { row(look.value, ink, null, "", titleShown) } }
        },
    )
}
