package net.seilent.funekit.screens.music.artist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.PageChrome
import net.seilent.funekit.kit.ThumbLayer
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.listThumbOf
import net.seilent.funekit.kit.pxAt
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.thumbProps
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberCoast
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.px
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.lists.quiet
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.pivotChromeAt
import net.seilent.funekit.screens.music.pivotSwitch
import net.seilent.funekit.screens.music.pivotWatch

private const val HIDDEN = -1
private const val AHEAD_ROWS = 2

@Composable
private fun artistTouch(pane: ArtistPane, s: Scale, spec: RowSpec): ListTouch {
    val state = remember(pane) { LazyListState(pane.track.drawFirst, 0) }
    val scope = rememberCoroutineScope()
    val coast = rememberCoast(Kind.List, s)
    val touch = remember(pane, state, s.touchPpu, s.stdPpu, s.ppu, spec.pitch, coast) { ListTouch(Kind.List, state, s, scope, pane.track, spec.pitch, coast) }
    DisposableEffect(touch) { onDispose { touch.stop() } }
    SideEffect {
        if (pane.ppu > 0f && pane.ppu != s.ppu) pane.track.rescale(pane.ppu, s.ppu, spec)
        pane.ppu = s.ppu
        pane.track.resize(spec)
        if (!touch.moving && state.firstVisibleItemIndex != pane.track.drawFirst) state.requestScrollToItem(pane.track.drawFirst)
    }
    return touch
}

fun artistSwitch(m: ArtistModel, chrome: Chrome, to: Int, now: Long) = pivotSwitch(m, chrome, to, now)

fun artistSide(m: ArtistModel, now: Long): Int {
    if (m.switchAt == null || !m.switching(now)) return m.pivot
    return if (m.motion.entering(now)) m.pivot else m.switchFrom
}

@Composable
fun ArtistLayer(
    m: ArtistModel,
    draw: ArtistDraw,
    chrome: Chrome,
    source: MusicSource?,
    a: Arbiter,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    frozen: Boolean,
    hideFocus: Boolean,
    snap: Int,
    onBack: () -> Unit,
    onRing: (Int) -> Boolean,
    onAlbum: (Row) -> Unit,
    onPlay: (Int) -> Boolean,
    leavesLit: () -> Boolean = { false },
    pageCovered: Boolean = false,
    onSheet: (SheetTarget) -> Boolean = { false },
) {
    val geo = draw.geo
    val s: Scale = geo.s
    val context = LocalContext.current
    var frame by remember { mutableLongStateOf(0L) }
    val frozenNow = rememberUpdatedState(frozen)
    val touches = m.panes.map { artistTouch(it, s, geo.spec(it.pivot)) }
    val p = m.pivot
    val pn = m.pane
    val t = touches[p]
    val switching by remember(m) { derivedStateOf { m.switching(frame) } }
    val side by remember(m) { derivedStateOf { artistSide(m, frame) } }
    SideEffect {
        m.geometry(geo)
        m.lists = touches
        for ((k, tk) in touches.withIndex()) {
            val pk = m.panes[k]
            tk.onScroll = { at ->
                if (k == m.pivot) m.afterScroll(at)
                m.poke()
            }
            if (pk.counted != pk.count) {
                tk.count(pk.count, tk.first)
                pk.counted = pk.count
            }
        }
        m.restoreAt(now())
        if (!switching && chrome.active != m.pivot) chrome.select(m.pivot)
    }
    DisposableEffect(m) { onDispose { m.lists = emptyList() } }

    LaunchedEffect(m) {
        while (true) {
            withFrameMillis { at ->
                for (q in m.panes) q.press.look(at)
                m.rings.look(at)
                frame = at
                val tt = m.lists.getOrNull(m.pivot)
                m.tick(at, geo.thumb(m.pane.track) != null, tt?.moving == true || tt?.scroll?.dragging == true)
            }
            val tt = m.lists.getOrNull(m.pivot)
            if (m.animating(frame) || tt?.moving == true || tt?.scroll?.dragging == true) continue
            val w = m.wakeAt(frame)
            if (w == null) m.wake.receive() else withTimeoutOrNull(w - frame) { m.wake.receive() }
        }
    }
    LaunchedEffect(m, t) { snapshotFlow { t.px to t.moving }.collect { m.poke() } }

    suspend fun rest() {
        while (true) {
            snapshotFlow { frozenNow.value || m.lists.any { it.moving || it.pressed } }.first { !it }
            if (!m.switching(now())) return
            withFrameMillis {}
        }
    }

    val gen = source?.library?.generation?.collectAsState()?.value ?: -1L
    LaunchedEffect(m, source, gen) {
        val src = source ?: return@LaunchedEffect
        if (gen <= 0L || m.gen == gen) return@LaunchedEffect
        if (m.loads > 0) {
            rest()
            quiet { src.viewed { v -> m.migrate(v, now()) } }
        } else {
            quiet { src.viewed { v -> m.load(v) } }
        }
        m.poke()
    }
    LaunchedEffect(m, source, p, m.loads) {
        val src = source ?: return@LaunchedEffect
        snapshotFlow { pn.track.drawFirst to pn.track.rowAt(pn.track.q + geo.view - 1) }.distinctUntilChanged().conflate().collect { (f, l) ->
            quiet { m.show(m.pageView(src), p, f - AHEAD_ROWS, l + AHEAD_ROWS) }
            m.poke()
        }
    }
    LaunchedEffect(m, source, m.loads) {
        val src = source ?: return@LaunchedEffect
        val cache = Art.get(context)
        val albums = m.panes[ARTIST_ALBUMS]
        snapshotFlow { (albums.track.drawFirst to albums.track.rowAt(albums.track.q + geo.view - 1)) to albums.paging.window }.distinctUntilChanged().collectLatest { (rows, _) ->
            val keys = HashSet<ArtKey>()
            quiet {
                withContext(Dispatchers.IO) {
                    m.pageView(src).let { v ->
                        for (r in maxOf(0, rows.first - AHEAD_ROWS)..minOf(albums.count - 1, rows.second + AHEAD_ROWS)) {
                            val ref = albums.row(r)?.art ?: continue
                            keys += ArtKey.Cover(ref, geo.side)
                            m.loadArt(v, cache, ref, geo.side)
                        }
                    }
                }
            }
            if (keys.isNotEmpty()) m.keepArt(keys)
            val songs = m.panes[ARTIST_SONGS]
            val near = { t: RowTrack, n: Int -> maxOf(0, t.drawFirst - AHEAD_ROWS)..minOf(n - 1, t.rowAt(t.q + geo.view - 1) + AHEAD_ROWS) }
            draw.keep(near(albums.track, albums.count).mapNotNull { albums.row(it) }.toSet(), near(songs.track, songs.count).mapNotNull { m.songModel(it) }.toSet())
            m.poke()
        }
    }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        src.player.state.collectLatest { st ->
            val tr = st.track
            m.track = tr
            m.playing = if (tr == null) null else quiet { src.viewed { v -> v.trackIds(tr) } }
        }
    }

    fun ringTap(): Tap {
        var started = false
        var i = -1
        return Tap(
            onPress = { started = false },
            onCancel = {
                if (i >= 0) m.rings.cancel(now())
                m.poke()
            },
            onCut = {
                m.rings.drop()
                m.poke()
            },
            onTap = {
                if (i >= 0) {
                    m.rings.tap(now())
                    if (!onRing(i)) m.rings.untap(now())
                }
                m.poke()
            },
            onLong = {
                val target = m.ringTarget(i)
                if (target != null && onSheet(target)) {
                    m.rings.reset()
                    m.poke()
                    true
                } else {
                    false
                }
            },
            onAt = { o ->
                if (!started) {
                    started = true
                    i = geo.ringAt(o.x, o.y)
                    if (i >= 0) m.ringDown(i, now())
                    m.poke()
                }
            },
        )
    }

    val rowTap: (Int) -> Tap? = { r ->
        if (!live) {
            null
        } else {
            var started = false
            var valid = false
            Tap(
                onPress = {},
                onCancel = {
                    if (valid) {
                        pn.press.cancel(now())
                        m.poke()
                    }
                },
                onCut = {
                    pn.press.drop()
                    pn.glow.off()
                    m.poke()
                },
                onTap = {
                    if (valid) {
                        pn.press.tap(now())
                        val row = pn.row(r)
                        if (p == ARTIST_ALBUMS) {
                            if (row != null) onAlbum(row)
                        } else if (!onPlay(r)) {
                            pn.press.untap(now())
                        }
                        m.poke()
                    }
                },
                onLong = {
                    val target = if (valid) m.target(p, r) else null
                    if (target != null && onSheet(target)) {
                        pn.press.reset()
                        pn.glow.off()
                        m.poke()
                        true
                    } else {
                        false
                    }
                },
                onAt = {
                    if (!started) {
                        started = true
                        val at = now()
                        if (r < pn.count && pn.row(r) != null) {
                            valid = true
                            m.listDown()
                            val was = pn.focus.row
                            pn.focus.set(r, pn.count, at, true)
                            pn.focus.setUnpress(was)
                            pn.press.down(r, at)
                            pn.glow.arm(r, at)
                            m.poke()
                        }
                    }
                },
            )
        }
    }

    val watch = Modifier.pivotWatch(m, chrome, live, now)
    val half = px((Tok.detailBlockRing + Tok.detailRingGap) / ArtistGeo.RING_HALF, s.stdPpu)
    val ringBand = Edges(0, geo.ringCy - half, s.widthPx, geo.ringCy + half)
    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx).then(watch)) { covered ->
        val block = rememberLook(m, draw, hideFocus, snap, covered = covered) { Triple(m.name, (0 until RINGS).map { m.ringLook(it, frame, hideFocus) }, m.gone) }
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) {
            with(draw) { block(block.value.second, null, "artist") }
        }
        if (live) Box(Modifier.pxAt(ringBand).tapTarget(a, ringTap()))
        Column(
            Modifier.graphicsLayer {
                alpha = if (switching) 0f else 1f
                translationX = m.dragX(frame, s.ppu).toFloat()
            },
        ) {
            Box(Modifier.pxSize(s.widthPx, geo.boxTop))
            key(p) {
                FuneList(a, t, Modifier.pxSize(s.widthPx, geo.view), if (live) 0 else HIDDEN, rowTap) {
                    items(pn.count) { r ->
                        val look = rememberLook(p, r, m, hideFocus, pageCovered, snap, covered = covered) { m.look(p, r, frame, hideFocus) }
                        val data = rememberLook(p, r, m, covered = covered) {
                            val row = pn.row(r)
                            Triple(row, if (p == ARTIST_ALBUMS) row?.art?.let { m.art[ArtKey.Cover(it, geo.side)] } else null, m.hiddenAlbum to m.litAlbum(row?.id ?: -1L))
                        }
                        Canvas(Modifier.pxSize(s.widthPx, t.rowHeight(r))) {
                            data.value
                            with(draw) { row(p, r, look.value, null, "", !m.onRings && !hideFocus && !pageCovered) }
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
                    Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { with(draw) { content(k, shownAt, hideFocus, pageCovered, null, "artist") } }
                }
            }
        }
        val bar = listThumbOf(s, geo.boxTop, geo.bottom, m.panes[side].count, m.panes[side].track.pitch, 0L)
        if (bar != null) key(side, m.panes[side].count) { ThumbLayer(bar, covered, snap) { thumbProps(geo.thumb(m.panes[side].track), m.thumbAlpha(frame)) } }
        PageChrome(chrome, a, { pivotChromeAt(chrome, frame) }, onPivot = { artistSwitch(m, chrome, it, now()) }, onHeader = if (live) onBack else null, leavesLit = leavesLit)
    }
}
