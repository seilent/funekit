package net.seilent.funekit.screens.music.albcol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Scale
import net.seilent.funekit.core.Section
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.PageChrome
import net.seilent.funekit.kit.ThumbLayer
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.scrollInto
import net.seilent.funekit.kit.scrub.edgeScrub
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.thumbProps
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberCoast
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.hub.hubChromeAt
import net.seilent.funekit.screens.music.lists.quiet
import net.seilent.funekit.screens.music.overlay.SheetTarget

private const val HIDDEN = -1
private const val AHEAD_ROWS = 2

@Composable
private fun colTouch(m: AlbumColModel, geo: AlbumColGeo, spec: RowSpec): ListTouch {
    val s = geo.s
    val state = remember(m) { LazyListState(m.track.drawFirst, 0) }
    val scope = rememberCoroutineScope()
    val coast = rememberCoast(Kind.List, s)
    val touch = remember(m, state, s.touchPpu, s.stdPpu, s.ppu, spec.pitch, coast) { ListTouch(Kind.List, state, s, scope, m.track, spec.pitch, coast) }
    DisposableEffect(touch) { onDispose { touch.stop() } }
    SideEffect {
        if (m.ppu > 0f && m.ppu != s.ppu) m.track.rescale(m.ppu, s.ppu, spec)
        m.ppu = s.ppu
        m.geometry(geo)
        if (!touch.moving && state.firstVisibleItemIndex != m.track.drawFirst) state.requestScrollToItem(m.track.drawFirst)
    }
    return touch
}

fun colScrubSection(m: AlbumColModel, letter: Int): Int? {
    val map = m.map ?: return null
    val present = (0 until Section.count).filter { map.has(it) }
    if (present.isEmpty()) return null
    return present[Section.first(present.size, letter) { present[it] }]
}

@Composable
fun AlbumColLayer(
    m: AlbumColModel,
    draw: AlbumColDraw,
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
    onJump: (Int) -> Unit,
    onAlbum: (Row) -> Unit,
    leavesLit: () -> Boolean = { false },
    onSheet: (SheetTarget) -> Boolean = { false },
) {
    val geo = draw.geo
    val s: Scale = geo.s
    val context = LocalContext.current
    var frame by remember { mutableLongStateOf(0L) }
    val focusShown = !hideFocus
    val frozenNow = rememberUpdatedState(frozen)
    val t = colTouch(m, geo, geo.spec())
    SideEffect {
        m.lists = listOf(t)
        t.onScroll = { at ->
            m.afterScroll(at)
            m.poke()
        }
        val map = m.map
        if (map != null && m.counted !== map) {
            t.count(map.count, t.first)
            m.counted = map
        }
        m.restoreAt(now())
    }
    DisposableEffect(m) { onDispose { m.lists = emptyList() } }

    LaunchedEffect(m) {
        while (true) {
            withFrameMillis { at ->
                m.press.look(at)
                frame = at
                m.tick(at, geo.thumb(m.track) != null, t.moving || t.scroll.dragging)
            }
            if (m.animating(frame) || t.moving || t.scroll.dragging) continue
            val w = m.wakeAt(frame)
            if (w == null) m.wake.receive() else withTimeoutOrNull(w - frame) { m.wake.receive() }
        }
    }
    LaunchedEffect(m, t) { snapshotFlow { t.px to t.moving }.collect { m.poke() } }

    suspend fun rest() {
        snapshotFlow { frozenNow.value || t.moving || t.pressed }.first { !it }
    }

    val gen = source?.library?.generation?.collectAsState()?.value ?: -1L
    LaunchedEffect(m, source, gen) {
        val src = source ?: return@LaunchedEffect
        if (gen <= 0L || m.gen == gen) return@LaunchedEffect
        if (m.map != null) {
            rest()
            quiet { src.viewed { v -> m.migrate(v, now()) } }
        } else {
            quiet {
                src.viewed { v ->
                    m.load(v)
                    m.map?.let { t.count(it.count, 0) }
                    m.counted = m.map
                    m.open(now())
                }
            }
        }
        m.poke()
    }
    LaunchedEffect(m, source, m.map) {
        val src = source ?: return@LaunchedEffect
        snapshotFlow { m.track.drawFirst to m.track.rowAt(m.track.q + geo.view - 1) }.distinctUntilChanged().conflate().collect { (f, l) ->
            quiet {
                m.pageView(src).let { v ->
                    m.show(v, f - AHEAD_ROWS, l + AHEAD_ROWS)
                    m.readTracks(v, f, l)
                }
            }
            m.poke()
        }
    }
    LaunchedEffect(m, source, m.map) {
        val src = source ?: return@LaunchedEffect
        val cache = Art.get(context)
        snapshotFlow { (m.track.drawFirst to m.track.rowAt(m.track.q + geo.view - 1)) to m.paging.window }.distinctUntilChanged().collectLatest { (rows, _) ->
            quiet {
                withContext(Dispatchers.IO) {
                    m.pageView(src).let { v ->
                        for (r in maxOf(0, rows.first - AHEAD_ROWS)..minOf(m.count - 1, rows.second + AHEAD_ROWS)) m.row(r)?.art?.let { m.loadArt(v, cache, it, geo.side) }
                    }
                }
            }
            m.keepArt(rows.first - AHEAD_ROWS, rows.second + AHEAD_ROWS, geo.side)
            draw.keep((maxOf(0, rows.first - AHEAD_ROWS)..minOf(m.count - 1, rows.second + AHEAD_ROWS)).map { m.entry(it) }.toSet())
            m.poke()
        }
    }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        src.player.state.collectLatest { st ->
            val tr = st.track
            m.playing = if (tr == null) -1L else quiet { src.viewed { v -> v.trackIds(tr)?.album } } ?: -1L
        }
    }
    LaunchedEffect(m, m.aims) {
        if (m.aims == 0) return@LaunchedEffect
        val cuts = t.cuts
        val from = m.track.q.toFloat()
        val to = m.aim.toFloat()
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
                        m.press.cancel(now())
                        m.poke()
                    }
                },
                onCut = {
                    m.press.drop()
                    m.poke()
                },
                onTap = {
                    if (valid) {
                        m.press.tap(now())
                        m.aimTo(scrollInto(m.track.topOf(r), m.track.topOf(r + 1) - m.track.topOf(r), m.track))
                        val l = m.letterAt(r)
                        val row = m.row(r)
                        if (l >= 0) onJump(l) else if (row != null) onAlbum(row)
                        m.poke()
                    }
                },
                onLong = {
                    val target = if (valid) m.target(r) else null
                    if (target != null && onSheet(target)) {
                        m.press.reset()
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
                        if (r < m.count && (m.letterAt(r) >= 0 || m.row(r) != null)) {
                            valid = true
                            m.focus.set(r, m.count, at, true)
                            m.press.down(r, at)
                            m.poke()
                        }
                    }
                },
            )
        }
    }

    val buckets = remember { (0 until Section.count).toList() }
    val scrubs = live && m.count > 0
    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx)) { covered ->
        Column {
            Box(Modifier.pxSize(s.widthPx, geo.boxTop))
            val list = Modifier.pxSize(s.widthPx, geo.view)
            Box(if (scrubs) list.edgeScrub(s, buckets, a) { l -> colScrubSection(m, l)?.let { m.jump(it, now()) } } else list) {
                FuneList(a, t, Modifier.pxSize(s.widthPx, geo.view), if (live) 0 else HIDDEN, rowTap) {
                    items(m.count) { r ->
                        val look = rememberLook(r, m, hideFocus, snap, covered = covered) { m.look(r, frame, focusShown) }
                        val data = rememberLook(r, m, covered = covered) { m.entry(r) }
                        val ink = remember(data.value, draw) { draw.ink(data.value) }
                        Canvas(Modifier.pxSize(s.widthPx, t.rowHeight(r))) { with(draw) { row(data.value, look.value, ink, null, "") } }
                    }
                }
            }
        }
        val bar = thumbOf(s, geo.boxTop, geo.bottom, (geo.bottom - geo.boxTop).toLong(), m.track.topOf(m.track.count).toLong(), 0L)
        if (bar != null) key(m.count) { ThumbLayer(bar, covered, snap) { thumbProps(geo.thumb(m.track), m.fade.at(frame)) } }
        PageChrome(chrome, a, { hubChromeAt(chrome, frame) }, onPivot = {}, onHeader = if (live) onBack else null, leavesLit = leavesLit)
    }
}
