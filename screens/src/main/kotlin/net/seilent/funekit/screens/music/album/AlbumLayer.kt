package net.seilent.funekit.screens.music.album

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.PageChrome
import net.seilent.funekit.kit.ThumbLayer
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.listThumbOf
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.penPx
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
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberCoast
import net.seilent.funekit.kit.word
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.api.Kind as ApiKind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.accentShown
import net.seilent.funekit.screens.music.hub.hubChromeAt
import net.seilent.funekit.screens.music.lists.quiet
import net.seilent.funekit.screens.music.overlay.SheetTarget

private const val HIDDEN = -1
private const val AHEAD_ROWS = 2

@Composable
private fun albumTouch(m: AlbumModel, geo: AlbumGeo, spec: RowSpec): ListTouch {
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

@Composable
fun AlbumLayer(
    m: AlbumModel,
    draw: AlbumDraw,
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
    leavesLit: () -> Boolean = { false },
    pageCovered: Boolean = false,
    onPlay: ((Int) -> Boolean)? = null,
    onSheet: (SheetTarget) -> Boolean = { false },
) {
    val geo = draw.geo
    val s: Scale = geo.s
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var frame by remember { mutableLongStateOf(0L) }
    val focusShown = accentShown(hideFocus, pageCovered)
    val frozenNow = rememberUpdatedState(frozen)
    val t = albumTouch(m, geo, geo.spec())
    SideEffect {
        m.lists = listOf(t)
        t.onScroll = { at ->
            m.afterScroll(at)
            m.poke()
        }
        if (m.counted != m.count) {
            t.count(m.count, t.first)
            m.counted = m.count
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
        if (m.total >= 0) {
            rest()
            quiet { src.viewed { v -> m.migrate(v, now()) } }
            t.count(m.count, t.first)
            m.counted = m.count
        } else {
            quiet {
                src.viewed { v ->
                    if (m.head == null) {
                        val at = v.indexOf(Listing.Albums, ApiKind.Album, m.id)
                        if (at != null) v.page(Listing.Albums, at, 1).rows.firstOrNull()?.let { m.head = it }
                    }
                    m.load(v)
                }
            }
        }
        m.poke()
    }
    LaunchedEffect(m, source, m.loads) {
        val src = source ?: return@LaunchedEffect
        snapshotFlow { m.track.drawFirst to m.track.rowAt(m.track.q + geo.view - 1) }.distinctUntilChanged().conflate().collect { (f, l) ->
            quiet { m.show(m.pageView(src), f - AHEAD_ROWS, l + AHEAD_ROWS) }
            m.poke()
        }
    }
    LaunchedEffect(m, source, m.head?.art, geo) {
        val src = source ?: return@LaunchedEffect
        val ref = m.head?.art ?: return@LaunchedEffect
        val cache = Art.get(context)
        quiet {
            withContext(Dispatchers.IO) {
                m.pageView(src).let { v ->
                    m.loadArt(v, cache, ref, geo.large)
                    m.loadArt(v, cache, ref, geo.grid)
                }
            }
        }
        m.poke()
    }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        src.player.state.collect { st -> m.play(st) }
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
                    m.glow.off()
                    m.poke()
                },
                onTap = {
                    if (valid) {
                        m.press.tap(now())
                        m.aimTo(m.intoView(r))
                        val open = onPlay
                        val src = source
                        if (open != null) {
                            if (!open(r)) m.press.untap(now())
                        } else if (src != null) {
                            scope.launch(FailFast) { quiet { m.tap(r, src) } }
                        }
                        m.poke()
                    }
                },
                onLong = {
                    val target = if (valid) m.target(r) else null
                    if (target != null && onSheet(target)) {
                        m.press.reset()
                        m.glow.off()
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
                        if (r < m.count && m.row(r) != null) {
                            valid = true
                            m.focus.set(r, m.count, at, true)
                            m.press.down(r, at)
                            m.glow.arm(r, at)
                            m.poke()
                        }
                    }
                },
            )
        }
    }

    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx)) { covered ->
        val art = rememberLook(m, geo, covered = covered) {
            val l = m.track.lift
            Triple(m.source(l), m.block(l).art, m.hidden && !m.gone)
        }
        Box(
            Modifier.pxAt(Edges(geo.artX, 0, geo.artX + geo.large, geo.large)).graphicsLayer {
                val l = m.track.lift
                val e = geo.artRect(l)
                val src = art.value.first.coerceAtLeast(1)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = e.w.toFloat() / src
                scaleY = e.h.toFloat() / src
                translationY = e.y0.toFloat()
            },
        ) {
            Canvas(Modifier.pxSize(geo.large, geo.large)) {
                val (side, img, hide) = art.value
                if (hide) return@Canvas
                if (img == null) {
                    drawRect(Ink.tile, Offset.Zero, Size(side.toFloat(), side.toFloat()))
                } else {
                    drawImage(img, dstOffset = IntOffset.Zero, dstSize = IntSize(side, side))
                }
            }
        }
        val lines = rememberLook(m, draw, covered = covered) { m.head to m.gone }
        Box(Modifier.pxSize(geo.clipRight, s.heightPx).graphicsLayer { clip = true }) {
            Box(
                Modifier.pxSize(geo.clipRight, s.heightPx).graphicsLayer {
                    translationX = geo.textXPx(m.track.lift).toFloat()
                    clip = true
                },
            ) {
                Canvas(
                    Modifier.pxSize(geo.clipRight, s.heightPx).graphicsLayer {
                        val l = m.track.lift
                        translationX = penPx(geo.textX64(l)) - geo.textXPx(l) - geo.edge
                    },
                ) {
                    lines.value
                    val l = draw.lines() ?: return@Canvas
                    val x = geo.edge.toFloat()
                    word(l.first, x, geo.artistBase.toFloat(), Ink.white, Roles.npArtist.alpha)
                    word(l.second, x, geo.albumBase.toFloat(), Ink.white, Roles.npAlbum.alpha)
                }
            }
        }
        Column {
            Box(Modifier.pxSize(s.widthPx, geo.top))
            FuneList(a, t, Modifier.pxSize(s.widthPx, geo.view), if (live) 0 else HIDDEN, rowTap) {
                items(m.count) { r ->
                    val look = rememberLook(r, m, hideFocus, pageCovered, snap, covered = covered) { m.look(r, frame, focusShown) }
                    val data = rememberLook(r, m, covered = covered) { m.rowModel(r) }
                    val ink = remember(data.value, draw) { data.value?.let(draw::ink) }
                    Canvas(Modifier.pxSize(s.widthPx, geo.pitch)) { with(draw) { row(look.value, ink, null, "", focusShown) } }
                }
            }
        }
        val count = m.count
        val bar = listThumbOf(geo.s, geo.top, geo.bottom, count, geo.pitch, 0L)
        val len by remember(geo, m, count) { derivedStateOf { geo.thumb(m.track)?.let { it.y1 - it.y0 } } }
        val n = len
        if (bar != null && n != null) key(count, n) { ThumbLayer(ThumbRect(bar.x0, 0, bar.x1, n), covered, snap) { thumbProps(geo.thumb(m.track), m.fade.at(frame)) } }
        PageChrome(chrome, a, { hubChromeAt(chrome, frame) }, onPivot = {}, onHeader = if (live) onBack else null, leavesLit = leavesLit)
    }
}
