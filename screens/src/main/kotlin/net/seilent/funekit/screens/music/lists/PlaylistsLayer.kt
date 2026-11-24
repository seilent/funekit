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

@Composable
fun PlaylistsLayer(
    m: PlaylistsModel,
    draw: PlaylistsDraw,
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
    onNowPlaying: () -> Boolean,
    leavesLit: () -> Boolean = { false },
    pageCovered: Boolean = false,
    onPlay: ((HubAct.Play) -> Boolean)? = null,
) {
    val s = draw.s
    val geo = draw.geo
    val scope = rememberCoroutineScope()
    val focusShown = !hideFocus
    val titleShown = accentShown(hideFocus, pageCovered)
    val frozenNow = rememberUpdatedState(frozen)
    ListPaneLayer(
        m.pane, geo, chrome, a, bands, draw.accent, { m.size }, now, live, visible, frozen, snap, onBack, leavesLit,
        onTap = { r ->
            when (val act = m.act(r)) {
                HubAct.NowPlaying -> onNowPlaying()
                is HubAct.Play -> {
                    val open = onPlay
                    val src = source
                    if (open != null) {
                        open(act)
                    } else {
                        if (src != null) scope.launch(FailFast) { quiet { src.player.play(act.listing, m.rows.generation, act.index, 0L) } }
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
        onRows = null,
        effects = {
            LaunchedEffect(m, source, live) {
                val src = source ?: return@LaunchedEffect
                combine(src.playlists.revision, src.favorites.revision, src.player.state, src.library.generation) { l, f, st, g -> PlaylistsKey(l, f, st, g) }
                    .collectLatest { k ->
                        if (m.stale(k) && (m.loaded == null || live)) {
                            if (m.loaded != null) m.pane.atRest { frozenNow.value }
                            quiet { src.viewed { v -> m.load(v, k, now()) } }
                            draw.keep((0 until m.size).mapNotNull { m.rowModel(it) }.toSet())
                        }
                    }
            }
        },
        lines = { _, covered ->
            val count = rememberLook(m, covered = covered) { (if (m.ready && m.status() == null) m.count() else null) to m.status() }
            Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { with(draw) { lines(count.value.first, count.value.second, null, "playlists") } }
        },
        item = { r, frame, covered ->
            val look = rememberLook(r, m, hideFocus, pageCovered, snap, covered = covered) { m.look(r, frame(), focusShown) }
            val data = rememberLook(r, m, covered = covered) { m.rowModel(r) }
            val ink = remember(data.value, draw) { data.value?.let(draw::ink) }
            Canvas(Modifier.pxSize(s.widthPx, geo.pitch)) { with(draw) { row(look.value, ink, null, "", titleShown) } }
        },
    )
}
