package net.seilent.funekit.screens.music.hub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.core.Section
import net.seilent.funekit.fxMul
import net.seilent.funekit.fxU
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.jumpScroll
import net.seilent.funekit.kit.PageChrome
import net.seilent.funekit.kit.RowKind
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.ThumbLayer
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.scrub.edgeScrub
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.textWidth
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.kit.thumbProps
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberCoast
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.accentShown
import net.seilent.funekit.screens.music.lists.quiet
import net.seilent.funekit.screens.music.morphSide
import net.seilent.funekit.screens.music.rowWordSide
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.pivotSwitch
import net.seilent.funekit.screens.music.pivotWatch
import net.seilent.funekit.screens.music.search.ImeInsets
import net.seilent.funekit.screens.music.search.SearchField
import net.seilent.funekit.screens.music.search.rememberIme
import net.seilent.funekit.screens.music.search.searchView
import net.seilent.funekit.api.Kind as ApiKind
import kotlin.math.abs
import kotlin.math.max

private const val HIDDEN = -1
private const val AHEAD_ROWS = 2
private const val THUMB_READS = 2

fun hubDraw(m: HubModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, accent: Color, accentOff: Boolean, chrome: Chrome? = null): HubDraw {
    val geo = HubGeo(s)
    val base = px(s.board.page_pivotBaseline, s.ppu).toFloat()
    val sides = chrome?.let { c -> m.set.pivots.mapIndexed { i, p -> morphSide(measurer, fonts, s, density, covers, p, Roles.pivotActive, false, Roles.pivotActive.alpha, c.pivotX(i), base, i) } }.orEmpty()
    val header = chrome?.let { c -> morphSide(measurer, fonts, s, density, covers, c.header.text, Roles.pageHeader, true, c.headerAlpha, c.headerRestX(), px(Tok.page_headerBaseline, s.stdPpu).toFloat(), -1) }
    val ls = roleStyle(Roles.sectionLabel, s, density, Ink.white)
    val qs = roleStyle(Roles.quickplayHint, s, density, Ink.white, ppu = s.stdPpu)
    val lw = textWidth(Roles.sectionLabel, ls, fonts, density, covers)
    return HubDraw(
        m,
        geo,
        { r -> rowInk(measurer, fonts, r, s, density, accentOff, covers) },
        { c, k -> gridInk(measurer, fonts, geo.grid, c, k, density, accentOff, covers) },
        { path -> measurer.word(fitPath(path, labelMax64(s), PATH_DOTS) { pen64(lw(it)) }, Roles.sectionLabel, ls, fonts, density, covers) },
        { t -> measurer.word(t, Roles.quickplayHint, qs, fonts, density, covers) },
        accent,
        accentOff,
        sides,
        header,
        rowWordSide(measurer, fonts, s, density, covers),
    )
}

fun hubShown(m: HubModel, geo: HubGeo, p: Int): IntRange? {
    val map = m.panes[p].map ?: return null
    if (map.count == 0) return null
    val t = m.panes[p].track
    return max(0, t.drawFirst - AHEAD_ROWS)..minOf(map.count - 1, t.rowAt(t.q + geo.view - 1) + AHEAD_ROWS)
}

fun keepShown(m: HubModel, draw: HubDraw) {
    val geo = draw.geo
    val keys = HashSet<ArtKey>()
    val models = HashSet<RowModel>()
    val cells = HashSet<Pair<GridCell, Int>>()
    for (p in m.panes.indices) {
        val pn = m.panes[p]
        val rows = hubShown(m, geo, p)
        val ids = HashSet<Long>()
        val thumbs = hubThumbs(m.role(p))
        if (rows != null) {
            for (r in rows) {
                if (m.gridRow(p, r)) {
                    for (k in 0 until (pn.map?.cells(r) ?: 0)) {
                        val c = m.cellAt(r, k) ?: continue
                        cells += c to k
                        c.ref?.let { keys += ArtKey.Cover(it, geo.grid.tile) }
                    }
                    continue
                }
                m.rowModel(p, r)?.let { models += it }
                if (!thumbs) continue
                val id = pn.row(pn.cell(r, 0))?.id ?: continue
                ids += id
                pn.thumbs[id]?.forEach { ref -> ref?.let { keys += ArtKey.Cover(it, geo.thumb) } }
            }
        }
        if (thumbs) for (id in pn.thumbs.keys.toList()) if (id !in ids) {
            pn.thumbs.remove(id)
            pn.firsts.remove(id)
        }
    }
    m.keepArt(keys)
    draw.keep(models, cells, m.panes.indices.flatMap { listOf(m.line(it), m.found(it)) }.toSet())
}

fun hubChromeAt(c: Chrome, now: Long): Long {
    val since = c.since(now) ?: return 0L
    val strip = Tok.motion_pivotStrip.toLong()
    return if (since < strip) now else now - since + strip
}

fun hubSide(m: HubModel, now: Long): Int {
    if (m.switchAt == null || !m.switching(now)) return m.pivot
    return if (m.motion.entering(now)) m.pivot else m.switchFrom
}

fun hubTopRing(m: HubModel, geo: HubGeo, over: Float, focusShown: Boolean, now: Long): Pair<Int, Int>? {
    if (!focusShown || !m.grid(m.pivot) || m.switching(now)) return null
    val pn = m.pane
    pn.focus.revision
    val c = m.cellAt(pn.focus.row, pn.col) ?: return null
    if (c.letter >= 0) return null
    val y = geo.top + pn.track.topOf(pn.focus.row) - pn.track.q - Math.round(over)
    if (pn.focus.row != pn.track.drawFirst || y < geo.clipTop) return null
    return y to pn.col
}

fun hubThumbAt(m: HubModel, geo: HubGeo, p: Int, off: Int): ThumbRect? {
    if (scanShown(m.scanning, scanSongs(m.generation, m.songs))) return null
    val n = m.panes[p].map?.count ?: return null
    return thumbOf(geo.s, geo.top, geo.bottom, geo.view.toLong(), m.panes[p].track.topOf(n).toLong(), off.toLong())
}

fun hubScrubSection(m: HubModel, letter: Int): Int? {
    val map = m.pane.map ?: return null
    val present = (0 until Section.count).filter { map.has(it) }
    if (present.isEmpty()) return null
    return present[Section.first(present.size, letter) { present[it] }]
}

fun hubJump(m: HubModel, section: Int, now: Long) {
    val row = m.jumpTo(section, now) ?: return
    m.lists.getOrNull(m.pivot)?.toRow(row)
    m.poke()
}

fun hubJumpScroll(m: HubModel, section: Int, now: Long) {
    val row = m.jumpTo(section, now) ?: return
    val tr = m.pane.track
    val u = m.pane.ppu
    if (u <= 0f) {
        m.lists.getOrNull(m.pivot)?.toRow(row)
    } else {
        m.jumpAim(jumpScroll(row, fxU(fxMul(tr.pitch64, tr.ppu64)) / u, tr.q / u, tr.max / u, 0L), u)
    }
    m.poke()
}

fun hubPicked(m: HubModel, letter: Int, now: Long) {
    if (hubPick(m.set, m.pivot) == HubPick.Defer) {
        m.colPick = letter
        return
    }
    m.jumpPick = letter
}

fun hubPickAtDrop(m: HubModel?, now: Long) {
    val h = m ?: return
    val p = h.jumpPick
    h.jumpPick = -1
    if (p >= 0) hubJumpScroll(h, p, now)
}

internal fun onQueryRow(m: HubModel, top: Int, y: Float): Boolean {
    val pn = m.pane
    val t = m.lists.getOrNull(m.pivot) ?: return false
    val v = y - top + t.over + t.px
    return y >= top && pn.cell(0, 0) == QUERY_CELL && v >= 0f && v < pn.track.topOf(1)
}

fun hubSwitch(m: HubModel, chrome: Chrome, to: Int, now: Long) = pivotSwitch(m, chrome, to, now)

fun hubScrollInto(top: Int, extent: Int, off: Int, view: Int): Int = when {
    top < off || (top == 0 && off > 0) -> top
    top + extent > off + view -> top + extent - view
    else -> off
}

@Composable
internal fun paneTouch(pane: HubPane, s: Scale, spec: RowSpec): ListTouch {
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

private fun place(pn: HubPane, tt: ListTouch?, n: Int, row: Int, px: Int? = null) {
    if (tt != null) {
        tt.count(n, row)
        if (px != null) tt.toPx(px)
    } else {
        pn.track.count(n, row)
        if (px != null) pn.track.at(px)
    }
    pn.counted = pn.map
}

@Composable
fun HubLayer(
    m: HubModel,
    draw: HubDraw,
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
    onJump: (Int) -> Unit,
    pageCovered: Boolean = false,
    leavesLit: () -> Boolean = { false },
    onSheet: (SheetTarget) -> Boolean = { false },
    onPlay: ((HubAct.Play) -> Boolean)? = null,
    onAlbum: (Row, Int) -> Unit = { _, _ -> },
    onArtist: (Row, Int, Long) -> Unit = { _, _, _ -> },
    onNowPlaying: () -> Boolean = { false },
    ime: ImeInsets? = null,
    onCommit: (String) -> Unit = {},
) {
    val s = draw.s
    val geo = draw.geo
    val g = geo.grid
    val q = m.search
    val insets = if (q == null) null else ime ?: rememberIme()
    var view by remember(m, geo) { mutableIntStateOf(geo.view) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var frame by remember { mutableLongStateOf(0L) }
    val focusShown = !hideFocus
    val titleShown = accentShown(hideFocus, pageCovered)
    val frozenNow = rememberUpdatedState(frozen)
    val touches = ArrayList<ListTouch>(m.panes.size)
    for (p in m.panes.indices) touches += paneTouch(m.panes[p], s, geo.spec(m.role(p), view))
    val p = m.pivot
    val pn = m.pane
    val t = touches[p]
    val switching by remember(m) { derivedStateOf { m.switching(frame) } }
    val side by remember(m) { derivedStateOf { hubSide(m, frame) } }
    val chromeAt by remember(chrome) { derivedStateOf { hubChromeAt(chrome, frame) } }
    SideEffect {
        m.lists = touches
        m.regrid(g.cols)
        for (k in m.panes.indices) {
            val tk = touches[k]
            val pk = m.panes[k]
            tk.onScroll = { at ->
                pk.afterScroll(at)
                m.poke()
            }
            val map = pk.map
            if (map != null && pk.counted !== map) {
                tk.count(map.count, tk.first)
                pk.counted = map
            }
        }
        if (!switching && chrome.active != m.pivot) chrome.select(m.pivot)
    }
    DisposableEffect(m) { onDispose { m.lists = emptyList() } }

    LaunchedEffect(m) {
        while (true) {
            withFrameMillis { at ->
                for (pane in m.panes) pane.press.look(at)
                frame = at
                val tt = m.lists.getOrNull(m.pivot)
                m.tick(at, hubThumbAt(m, geo, m.pivot, m.pane.track.q) != null, tt?.moving == true || tt?.scroll?.dragging == true)
            }
            val tt = m.lists.getOrNull(m.pivot)
            if (m.animating(frame) || tt?.moving == true || tt?.scroll?.dragging == true) continue
            val w = m.wakeAt(frame)
            if (w == null) m.wake.receive() else withTimeoutOrNull(w - frame) { m.wake.receive() }
        }
    }
    LaunchedEffect(m, t) { snapshotFlow { t.px to t.moving }.collect { m.poke() } }
    if (q != null && insets != null) {
        LaunchedEffect(m, insets, geo) {
            snapshotFlow { insets.target() }.collect { b ->
                snapshotFlow { m.lists.any { it.pressed } }.first { !it }
                view = searchView(geo, s.heightPx, b)
            }
        }
        LaunchedEffect(m, source, q.commits) {
            val src = source ?: return@LaunchedEffect
            while (q.landing) {
                quiet { src.viewed { v -> m.landAll(v, max(1, view / geo.pitch(m.role(m.pivot))) + AHEAD_ROWS) } }
                if (m.land(now())) m.lists.getOrNull(m.pivot)?.toPx(0)
                m.poke()
                if (!q.landing) break
                val seen = m.panes.map { it.map }
                snapshotFlow { m.panes.map { it.map } }.first { it != seen }
            }
        }
        LaunchedEffect(m, source, q.open, q.text) {
            val src = source ?: return@LaunchedEffect
            if (!q.open) return@LaunchedEffect
            q.settle(q.text, q.fresh) { text -> quiet { src.viewed { v -> v.page(Listing.Search(text, ApiKind.Song), 0, 0).total } } }
            m.poke()
        }
    }

    suspend fun rest() {
        while (true) {
            snapshotFlow {
                val l = m.lists.getOrNull(p)
                frozenNow.value || l?.moving == true || l?.pressed == true
            }.first { !it }
            if (!m.switching(now())) return
            withFrameMillis {}
        }
    }

    val gen = source?.library?.generation?.collectAsState()?.value ?: -1L
    LaunchedEffect(m, source, p, pn.loads, gen) {
        val src = source ?: return@LaunchedEffect
        if (pn.map != null && pn.gen == gen) {
            m.restoring = false
            return@LaunchedEffect
        }
        val stale = pn.map != null
        if (stale) rest()
        m.revisions = src.playlists.revision.value to src.favorites.revision.value
        quiet {
            src.viewed { v ->
                val tt = m.lists.getOrNull(p)
                val pitch = geo.pitch(m.role(p))
                fun settle() {
                    if (m.set != HubSet.Music || p != HUB_FOLDERS) return
                    val q = m.settleFolder(geo.view, pitch, now())
                    val map = pn.map
                    if (map != null && q != null) place(pn, tt, map.count, 0, q)
                }
                if (stale) {
                    val first = pn.track.first
                    val to = m.migrate(v, p, first, src.library.caps)
                    pn.map?.let { place(pn, tt, it.count, to ?: first) }
                    settle()
                } else {
                    m.load(v, p)
                    settle()
                    val to = m.restoreRow(v, p, src.library.caps)
                    val map = pn.map
                    if (map != null && to != null) place(pn, tt, map.count, to, m.anchor?.let { pn.track.topOf(to) + px(it.off, s.ppu) })
                }
                m.songs = v.page(Listing.Songs, 0, 0).total
            }
        }
        m.poke()
    }
    LaunchedEffect(m, source, p, pn.map) {
        val src = source ?: return@LaunchedEffect
        snapshotFlow { pn.track.drawFirst to pn.track.rowAt(pn.track.q + geo.view - 1) }.distinctUntilChanged().conflate().collect { (f, l) ->
            val v = src.library.view(pn.gen)
            quiet {
                m.show(v, p, f - AHEAD_ROWS, l + AHEAD_ROWS)
                m.thumbs(v, p, f, l, THUMB_READS * geo.slots(draw.thumbX))
            }
            keepShown(m, draw)
            val tt = m.lists.getOrNull(p)
            if (tt != null && !tt.moving && !tt.scroll.dragging) m.anchorAt(p, pn.track, s.ppu)?.let { m.anchor = it }
            m.poke()
        }
    }
    LaunchedEffect(m, source, p, pn.map) {
        val src = source ?: return@LaunchedEffect
        val cache = Art.get(context)
        snapshotFlow { Triple(pn.track.drawFirst to pn.track.rowAt(pn.track.q + geo.view - 1), pn.paging.window, pn.thumbsRead) }
            .distinctUntilChanged()
            .collectLatest { (rows, _, _) ->
                val v = src.library.view(pn.gen)
                quiet { withContext(Dispatchers.IO) { loadArt(m, v, cache, draw, p, rows.first, rows.second) } }
                m.poke()
            }
    }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        combine(src.playlists.revision, src.favorites.revision) { l, f -> l to f }.collect { r ->
            if (m.revised(r.first, r.second)) m.poke()
        }
    }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        src.library.generation.collect {
            m.generation = it
            m.poke()
        }
    }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        src.player.state.collectLatest { st -> quiet { src.viewed { v -> m.play(v, st) } } }
    }
    LaunchedEffect(m, source) {
        val src = source ?: return@LaunchedEffect
        var last = 0L
        src.library.scan.collect { sc ->
            val wait = last + Tok.motion_progress.toLong() - now()
            if (wait > 0) delay(wait)
            m.scanning = sc.running
            m.found = sc.found
            last = now()
            m.poke()
        }
    }
    LaunchedEffect(m, m.aimAt) {
        if (m.aimAt == 0) return@LaunchedEffect
        val tt = m.lists.getOrNull(m.pivot) ?: return@LaunchedEffect
        val cut = m.aimCut
        val cuts = tt.cuts
        val jump = m.aimJump
        val u = m.aimU
        if (jump != null) tt.toPx(Math.round(jump.from * u))
        val from = m.pane.track.q.toFloat()
        val to = m.aim.toFloat()
        val d = Tok.motion_scroll
        var t0 = -1L
        var e = 0f
        while (if (jump != null) !jump.done(e.toLong()) else e < d) {
            if (tt.scroll.dragging || tt.moving || m.aimCut != cut || tt.cuts != cuts) break
            withFrameMillis { at ->
                if (t0 < 0) t0 = at
                e = (at - t0).toFloat()
                val q = if (jump != null) jump.at(e.toLong()) * u else lerp(from, to, Curves.easeIn.at((e / d).coerceIn(0f, 1f)))
                if (m.aimCut == cut && tt.cuts == cuts) tt.toPx(Math.round(q))
            }
        }
    }

    fun play(act: HubAct.Play) {
        val open = onPlay
        if (open != null) {
            if (!open(act)) pn.press.untap(now())
            return
        }
        val src = source ?: return
        scope.launch(FailFast) { quiet { m.play(act, src) } }
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
                        m.aimTo(hubScrollInto(pn.track.topOf(r), t.rowHeight(r), pn.track.q, geo.view))
                        when (val act = m.act(p, r, pn.downCol, pn.ring)) {
                            is HubAct.Jump -> onJump(act.letter)
                            is HubAct.Play -> play(act)
                            is HubAct.Enter -> m.enter(act.folder, act.name)
                            HubAct.Query -> m.openField()
                            is HubAct.Album -> pn.row(pn.cell(r, pn.downCol))?.let { onAlbum(it, r) }
                            is HubAct.Artist -> pn.row(pn.cell(r, 0))?.let { onArtist(it, r, pn.firsts[it.id] ?: -1L) }
                            HubAct.NowPlaying -> if (!onNowPlaying()) pn.press.untap(now())
                            else -> Unit
                        }
                        m.poke()
                    }
                },
                onLong = {
                    val target = if (valid) m.target(p, r, pn.downCol) else null
                    if (target != null && onSheet(target)) {
                        pn.press.reset()
                        pn.glow.off()
                        m.poke()
                        true
                    } else {
                        false
                    }
                },
                onAt = { o ->
                    pn.downX = o.x
                    if (!started) {
                        started = true
                        val at = now()
                        if (q?.open == true && pn.cell(r, 0) != QUERY_CELL) m.blur()
                        m.stopAim()
                        val map = pn.map
                        val y = o.y - geo.top + t.over + t.px - t.topOf(r)
                        val gridRow = m.gridRow(p, r)
                        val c = if (gridRow) g.colAt(o.x) else 0
                        val inside = map != null && c >= 0 && c < map.cells(r) && (!gridRow || y < g.cellH)
                        if (inside && !m.onLabel(p, r)) {
                            valid = true
                            pn.setFocus(r, c, at, true)
                            val model = if (gridRow) null else m.rowModel(p, r)
                            val d = abs(o.x / s.ppu - Tok.rowPlayRingX)
                            pn.ring = hubCollection(m.role(p)) && model?.ring == true && d + d <= Tok.rowPlayRing
                            if (gridRow || model?.kind == RowKind.Item) {
                                pn.press.down(pn.item(r, c), at)
                                if (model?.kind == RowKind.Item && !pn.ring) pn.glow.arm(r, at)
                            }
                            pn.down = r
                            pn.downCol = c
                            m.poke()
                        }
                    }
                },
            )
        }
    }

    val count = if (scanShown(m.scanning, scanSongs(m.generation, m.songs))) 0 else pn.map?.count ?: 0
    val text = m.line(p)
    val buckets = remember { (0 until Section.count).toList() }
    val scrubs = live && count > 0 && buckets.any { pn.map?.has(it) == true }
    val watch = Modifier.pivotWatch(m, chrome, live, now).then(
        if (live && q != null) {
            Modifier.pointerInput(m, geo) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (m.search?.open == true && !onQueryRow(m, geo.top, d.position.y)) m.blur()
                }
            }
        } else {
            Modifier
        },
    )
    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx).then(watch)) { covered ->
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { pageGround(bands, draw.accent) }
        PageChrome(chrome, a, { chromeAt }, onPivot = { hubSwitch(m, chrome, it, now()) }, onHeader = if (live) onBack else null, leavesLit = leavesLit)
        Column(
            Modifier.graphicsLayer {
                alpha = if (switching) 0f else 1f
                translationX = m.dragX(frame, s.ppu).toFloat()
            },
        ) {
            Box(Modifier.pxSize(s.widthPx, geo.top))
            val list = Modifier.pxSize(s.widthPx, view)
            Box(if (scrubs) list.edgeScrub(s, buckets, a) { l -> hubScrubSection(m, l)?.let { hubJump(m, it, now()) } } else list) {
                key(p) {
                    FuneList(a, t, Modifier.pxSize(s.widthPx, view), if (live) 0 else HIDDEN, rowTap) {
                        items(count) { r ->
                            val look = rememberLook(p, r, m, hideFocus, pageCovered, snap, covered = covered) { m.look(p, r, frame, focusShown) }
                            val data = rememberLook(p, r, m, draw, covered = covered) { draw.data(p, r) }
                            val d = data.value
                            val ink = remember(d.model, draw) { d.model?.let(draw::ink) }
                            val inks = remember(d.cells, draw) { d.cells.mapIndexed { k, c -> c?.let { draw.gridInk(it, k) } } }
                            val shown = if (q != null && m.panes[p].cell(r, 0) == QUERY_CELL) m.queryText() else null
                            val sem = if (shown == null) {
                                Modifier
                            } else {
                                Modifier.semantics {
                                    editableText = AnnotatedString(shown)
                                    onClick {
                                        if (live) {
                                            m.openField()
                                            m.poke()
                                        }
                                        live
                                    }
                                }
                            }
                            Canvas(Modifier.pxSize(s.widthPx, t.rowHeight(r)).then(sem)) { with(draw) { row(p, look.value, d, ink, inks, null, "", titleShown) } }
                        }
                    }
                }
            }
        }
        if (m.grid(p)) {
            val ring = rememberLook(m, geo, focusShown, covered = covered) { hubTopRing(m, geo, t.over, focusShown, frame) }
            Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) {
                val (y, k) = ring.value ?: return@Canvas
                val w = g.border
                clipRect(0f, (geo.clipTop - geo.edge).toFloat(), size.width, y.toFloat()) {
                    drawRect(Ink.white, Offset((g.x(k) - w).toFloat(), (y - w).toFloat()), Size((g.tile + w + w).toFloat(), w.toFloat()))
                }
            }
        }
        if (text.isNotEmpty() && !switching) Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { statusLine(s, draw.line(text), null, "") }
        val found = m.found(p)
        if (found.isNotEmpty() && !switching && !scanShown(m.scanning, scanSongs(m.generation, m.songs))) {
            Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { statusLine(s, draw.line(found), null, "", draw.foundBase(m, p).toFloat()) }
        }
        if (q != null && insets != null) {
            SearchField(
                q, insets,
                done = {
                    val ok = m.done()
                    if (ok) {
                        onCommit(m.committed())
                        m.poke()
                    }
                    ok
                },
                close = {
                    if (m.closeField()) onBack()
                    m.poke()
                },
                typed = { m.typed(it) },
            )
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
                    Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) {
                        with(draw) { content(k, shownAt, titleShown, null, "hub") { r -> m.look(k, r, shownAt, focusShown) } }
                    }
                }
            }
        }
        val bar = hubThumbAt(m, geo, side, 0)
        if (bar != null) {
            key(side, m.panes[side].map?.count) { ThumbLayer(bar, covered, snap) { thumbProps(hubThumbAt(m, geo, side, m.panes[side].track.q), switchThumb(m, frame)) } }
        }
    }
}

private fun switchThumb(m: HubModel, now: Long): Float {
    if (m.switchAt == null || !m.switching(now)) return m.fade.at(now)
    return (if (m.motion.thumbIn(now)) m.fade.at(now) else m.switchFade) * m.motion.thumbAlpha(now)
}

suspend fun loadArt(m: HubModel, v: LibraryView, cache: Art, draw: HubDraw, p: Int, f: Int, l: Int) {
    val geo = draw.geo
    val pn = m.panes[p]
    val map = pn.map ?: return
    if (m.grid(p)) {
        for (r in max(0, f - AHEAD_ROWS)..minOf(map.count - 1, l + AHEAD_ROWS)) {
            for (k in 0 until map.cells(r)) m.cellAt(r, k)?.ref?.let { m.art(v, cache, it, geo.grid.tile) }
        }
        return
    }
    if (!hubThumbs(m.role(p))) return
    val side = geo.thumb
    val slots = geo.slots(draw.thumbX)
    for (r in max(0, f)..minOf(map.count - 1, l)) {
        val refs = pn.row(pn.cell(r, 0))?.id?.let { pn.thumbs[it] } ?: continue
        var x = draw.thumbX
        var misses = 0
        for (ref in refs) {
            if (x + side > geo.thumbRight) break
            if (ref != null) m.art(v, cache, ref, side)
            val k = ref?.let { ArtKey.Cover(it, side) }
            if (k == null || (k in m.art && m.art[k] == null)) {
                if (++misses >= slots) break
                continue
            }
            x += side + geo.thumbGap
        }
    }
}
