package net.seilent.funekit.screens.host

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.seilent.funekit.Scale
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Disconnected
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.Stale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.cover
import net.seilent.funekit.kit.home.HomeEntry
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeInk
import net.seilent.funekit.kit.home.HomeLook
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.home.homeInk
import net.seilent.funekit.kit.home.homeMenu
import net.seilent.funekit.kit.home.homeRingRect
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.proj.M3
import net.seilent.funekit.kit.proj.qpProjection
import net.seilent.funekit.kit.proj.t6
import net.seilent.funekit.kit.pxAt
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.quickplay.QP_PINS
import net.seilent.funekit.kit.quickplay.QP_PLAYING
import net.seilent.funekit.kit.quickplay.QpBoxes
import net.seilent.funekit.kit.quickplay.QpData
import net.seilent.funekit.kit.quickplay.QpInk
import net.seilent.funekit.kit.quickplay.QpKind
import net.seilent.funekit.kit.quickplay.QpLayout
import net.seilent.funekit.kit.quickplay.QpLook
import net.seilent.funekit.kit.quickplay.QpPan
import net.seilent.funekit.kit.quickplay.QpTile
import net.seilent.funekit.kit.quickplay.QuickplayFront
import net.seilent.funekit.kit.quickplay.QuickplayState
import net.seilent.funekit.kit.quickplay.homeLandAim
import net.seilent.funekit.kit.quickplay.menuPeek
import net.seilent.funekit.kit.quickplay.peekHit
import net.seilent.funekit.kit.quickplay.qpAlpha
import net.seilent.funekit.kit.quickplay.qpBoxes
import net.seilent.funekit.kit.quickplay.qpInk
import net.seilent.funekit.kit.quickplay.qpLandAim
import net.seilent.funekit.kit.quickplay.qpMenuLook
import net.seilent.funekit.kit.quickplay.qpOff
import net.seilent.funekit.kit.quickplay.qpPanStart
import net.seilent.funekit.kit.quickplay.qpPanel
import net.seilent.funekit.kit.quickplay.qpPos
import net.seilent.funekit.kit.quickplay.qpTiles
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Edge
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.Pan
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberListTouch
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.hub.cardLine
import net.seilent.funekit.screens.music.lists.playlistsCount
import net.seilent.funekit.screens.music.lists.quiet
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val HIDDEN = -1
private const val CUT_GROW = 2
private const val PEEK = 1

private val cutPaint by lazy { Paint().apply { colorFilter = ColorFilter.tint(Ink.bg) } }

fun playlistsSub(lists: Int, none: HomeEntry): String = if (lists > 0) playlistsCount(lists) else none.sub

@Composable
fun HomeLayer(
    ink: HomeInk,
    menu: HomeMenu,
    geo: HomeGeo,
    a: Arbiter,
    touch: ListTouch,
    frame: () -> Long,
    now: () -> Long,
    ringLit: Boolean,
    live: Boolean,
    visible: Boolean,
    poke: () -> Unit,
    onSelect: (HomeEntry) -> Unit,
    onRing: () -> Unit = {},
) {
    val s = geo.s
    val sp = s.stdPpu
    LaunchedEffect(menu, menu.aims) {
        if (menu.aims == 0) return@LaunchedEffect
        val from = geo.offU(touch.state.firstVisibleItemScrollOffset)
        val to = menu.aim
        val d = Tok.motion_scroll
        var t0 = -1L
        var e = 0f
        while (e < d && menu.aiming) {
            withFrameMillis {
                if (t0 < 0) t0 = it
                e = (it - t0).toFloat()
                val want = lerp(from, to, Curves.easeIn.at((e / d).coerceIn(0f, 1f)))
                if (menu.aiming) touch.state.dispatchRawDelta((want - geo.offU(touch.state.firstVisibleItemScrollOffset)) * sp)
            }
        }
        if (menu.aiming) menu.rest(to, touch.state.firstVisibleItemScrollOffset)
        menu.stopAim()
    }
    val cut = {
        menu.ring.drop()
        menu.items.drop()
        menu.lit.up()
        menu.glow.clear()
        poke()
    }
    val ringTap = Tap(
        onPress = {
            menu.ringDown(now())
            poke()
        },
        onCancel = {
            menu.cancel(now())
            poke()
        },
        onTap = {
            menu.tap(now())
            poke()
            onRing()
        },
        onCut = cut,
    )
    fun itemTap(i: Int): Tap {
        var pressed: HomeEntry? = null
        return Tap(
            onPress = {
                pressed = ink.keys[i]
                menu.down(i, now())
                poke()
            },
            onCancel = {
                menu.cancel(now())
                poke()
            },
            onTap = {
                menu.tap(now())
                menu.into(geo.offU(touch.state.firstVisibleItemScrollOffset), geo)
                poke()
                pressed?.let(onSelect)
            },
            onCut = cut,
        )
    }
    PageLayer(visible, !visible) { covered ->
        val rest = rememberLook(menu, geo, ink, covered = covered) { menu.restAt(frame(), ringLit) }
        FuneList(a, touch, Modifier.pxSize(s.widthPx, s.heightPx), if (live) 0 else HIDDEN) {
            item {
                Box(Modifier.pxSize(s.widthPx, geo.contentPx)) {
                    Canvas(Modifier.pxSize(s.widthPx, geo.contentPx)) { homeMenu(ink, menu, geo, rest.value, null, menu.shift(geo) { touch.state.firstVisibleItemScrollOffset }) }
                    if (live) {
                        Box(Modifier.pxAt(geo.ringHit()).tapTarget(a, ringTap))
                        for (i in ink.keys.indices) Box(Modifier.pxAt(geo.itemHit(i)).tapTarget(a, itemTap(i)))
                    }
                }
            }
        }
    }
}

fun DrawScope.homeT6(ink: HomeInk, menu: HomeMenu, geo: HomeGeo, t: Float, ringLit: Boolean, now: Long, off: Float, look: HomeLook) {
    val m = t6(t, geo.s.stdPpu, geo.s.widthPx).compose()
    withTransform({ transform(m) }) {
        clipRect(0f, 0f, geo.s.widthPx.toFloat(), geo.s.heightPx.toFloat()) {
            homeMenu(ink, menu, geo, menu.restAt(now, ringLit), look, off)
        }
    }
}

fun projectedBounds(r: Edges, m: M3, grow: Int): Edges {
    var x0 = Int.MAX_VALUE
    var y0 = Int.MAX_VALUE
    var x1 = Int.MIN_VALUE
    var y1 = Int.MIN_VALUE
    for (x in listOf(r.x0, r.x1)) for (y in listOf(r.y0, r.y1)) {
        val o = m.map(x.toFloat(), y.toFloat())
        val ix = floor(o.x).toInt()
        val iy = floor(o.y).toInt()
        x0 = min(x0, ix)
        y0 = min(y0, iy)
        x1 = max(x1, ix)
        y1 = max(y1, iy)
    }
    return Edges(x0 - grow, y0 - grow, x1 + grow, y1 + grow)
}

fun homeRingAt(s: Scale, homeOff: Float, t: Float, grow: Int): Edges = projectedBounds(homeRingRect(s, homeOff, false), t6(t, s.stdPpu, s.widthPx), grow)

fun DrawScope.qpRingCut(l: QpLayout, words: QpInk, st: QuickplayState, boxes: QpBoxes, look: QpLook, active: Boolean, tiles: List<Edges>, geo: HomeGeo, t: Float, homeOff: Float, qpOff: Long) {
    val s = geo.s
    val rb = homeRingAt(s, homeOff, t, CUT_GROW).and(Edges(0, 0, s.widthPx, s.heightPx))
    if (rb.w <= 0 || rb.h <= 0) return
    val r = Rect(rb.x0.toFloat(), rb.y0.toFloat(), rb.x1.toFloat(), rb.y1.toFloat())
    clipRect(r.left, r.top, r.right, r.bottom) {
        drawContext.canvas.saveLayer(r, cutPaint)
        qpPanel(l, words, st, boxes, t, 1f, active, look, qpOff)
        drawContext.canvas.restore()
    }
    val pq = qpProjection(t, l.s.stdPpu)
    for (e in tiles) {
        val d = projectedBounds(e, pq, 0).and(rb)
        if (d.w > 0 && d.h > 0) drawRect(Ink.bg, Offset(d.x0.toFloat(), d.y0.toFloat()), Size(d.w.toFloat(), d.h.toFloat()))
    }
}

fun DrawScope.panFrame(ink: HomeInk, menu: HomeMenu, geo: HomeGeo, pan: QpPan, now: Long, ringLit: Boolean, homeOff: Float, l: QpLayout, words: QpInk, st: QuickplayState, boxes: QpBoxes, qpOff: Long) {
    val p = pan.at(now)
    val look = st.look(l, now)
    val ring = panRingLook(menu.peek(now))
    val under = pan.active || !pan.moved(now)
    homeT6(ink, menu, geo, p, ringLit, now, homeOff, panMenuLook(p))
    if (under) {
        homeT6(ink, menu, geo, p, ringLit, now, homeOff, ring)
        qpRingCut(l, words, st, boxes, look, pan.active, pan.tiles, geo, p, homeOff, qpOff)
    }
    if (!(pan.front && pan.still)) qpPanel(l, words, st, boxes, p, qpAlpha(p, menu.peek(now)), pan.active, look, qpOff)
    if (!under) homeT6(ink, menu, geo, p, ringLit, now, homeOff, ring)
}

fun panMenuLook(t: Float): HomeLook {
    val l = qpMenuLook(t)
    return HomeLook(l.items, l.sel, l.selAlpha, l.sub, ring = 0f, mark = l.mark)
}

fun panRingLook(peek: Float): HomeLook = HomeLook(0f, -1, 0f, 0f, ring = peek, mark = 0f)

@Composable
fun rememberHomeMenu(words: List<String>, now: () -> Long): HomeMenu {
    val was = remember { arrayOfNulls<Pair<List<String>, HomeMenu>>(1) }
    val menu = remember(words) {
        HomeMenu(words.size).also { m ->
            val (old, om) = was[0] ?: return@also
            val f = words.indexOf(old.getOrNull(om.focus.row))
            if (f >= 0) m.focus.set(f, words.size, now(), false)
        }
    }
    SideEffect { was[0] = words to menu }
    return menu
}

internal suspend fun qpLoads(src: QpSource, qp: QuickplayState, tiles: Int, log: (String) -> Unit, land: (QpData) -> Unit) {
    src.changes.collectLatest {
        val d = try {
            src.load(tiles)
        } catch (e: Exception) {
            if (e !is Busy && e !is Disconnected && e !is NoCard && e !is Stale) throw e
            log("quickplay load: ${e.message}")
            return@collectLatest
        }
        if (d != qp.data) {
            if (d.copy(paused = qp.data.paused) != qp.data) land(d)
            qp.data = d
        }
        if (d.generation > 0) qp.waiting = false
    }
}

internal suspend fun shuffleLibrary(s: MusicSource): Boolean {
    val g = s.library.generation.value
    if (s.library.view(g).page(Listing.Songs, 0, 0).total == 0) return false
    s.player.play(Listing.Songs, g, -1, 0L, shuffle = true)
    return true
}

internal fun qpResumes(t: QpTile, track: Long?, variant: Boolean): Boolean =
    t.section == QP_PLAYING || (!variant && track != null && t.item.kind == QpKind.Song && t.item.id == track)

fun homeStarting(t: Turn<*>, menu: HomeMenu, touch: ListTouch) {
    if (t.from == null) menu.land()
    touch.land()
    if (t.from == null) touch.cancel()
}

class HomeShown(val home: Boolean, val pan: Boolean, val front: Boolean, val frontFrozen: Boolean, val peek: Boolean)

class HomeState internal constructor(
    entries: List<HomeEntry>,
    scale: Scale,
    ink: HomeInk,
    geo: HomeGeo,
    menu: HomeMenu,
    homeTouch: ListTouch,
    qp: QuickplayState,
    qpWords: QpInk,
    qpLayout: QpLayout,
    qpBox: QpBoxes,
    qpTouch: ListTouch,
    peekBox: Edges,
    source: QpSource?,
    gate: Boolean,
    internal var now: () -> Long,
    internal var poke: () -> Unit,
    internal var toast: Toast,
    internal var log: (String) -> Unit,
) {
    internal var entries by mutableStateOf(entries)
    internal var scale by mutableStateOf(scale)
    internal var inks by mutableStateOf(ink)
    internal var geo by mutableStateOf(geo)
    internal var menu by mutableStateOf(menu)
    internal var homeTouch by mutableStateOf(homeTouch)
    internal var qp by mutableStateOf(qp)
    internal var qpWords by mutableStateOf(qpWords)
    internal var qpLayout by mutableStateOf(qpLayout)
    internal var qpBox by mutableStateOf(qpBox)
    internal var qpTouch by mutableStateOf(qpTouch)
    internal var peekBox by mutableStateOf(peekBox)
    internal var source by mutableStateOf(source)
    internal var gate by mutableStateOf(gate)
    internal var frame by mutableLongStateOf(0L)
    private var peekDown = false
    private var held: List<HomeEntry>? = null

    val quickplay: QuickplayState get() = qp

    val ink: HomeInk get() = inks

    val front: Boolean get() = qp.pan.front

    val pan: QpPan get() = qp.pan

    fun starting(t: Turn<*>) {
        held = entries
        homeStarting(t, menu, homeTouch)
        if (t.kind == MoveKind.T8 && npCloseForm(t.to == null, qp.pan.front) == NpForm.Front) {
            qp.stopAim()
            qpTouch.halt()
        }
    }

    fun cancel() = homeTouch.cancel()

    fun leave() {
        val p = qp.pan
        val t = now()
        if (p.dragged) {
            p.leave(t)
        } else {
            p.land(t)
            if (p.front) show(false)
        }
        poke()
    }

    fun land() = homeLandAim(menu, homeTouch, geo)

    fun panning(now: Long): Boolean = qp.pan.moving(now)

    fun has(word: String): Boolean = (held ?: entries).any { it.word == word }

    fun item(word: String): Int = (held ?: entries).indexOfFirst { it.word == word }

    fun back() = leave()

    fun show(front: Boolean) {
        if (!panLayers(front)) return
        qp.pan.show(front, now())
        poke()
    }

    fun acts(s: Swipe): Boolean = if (qp.pan.front) peekDown && s == Swipe.Left else gate && s == Swipe.Right

    fun homePan(stack: PageStack<*>): Pan = object : Pan {
        override fun begin(x: Float, origin: Float, down: Offset): Boolean {
            val pan = qp.pan
            if (stack.top != null || stack.phase != Phase.Rest || pan.moving(now())) return false
            val front = pan.front
            if (front && !inPeek(down)) return false
            if (if (front) x >= origin else x <= origin) return false
            if (!panLayers(!front)) return false
            pan.begin(x)
            poke()
            return true
        }

        override fun drag(x: Float) {
            qp.pan.drag(x, scale.stdPpu)
            poke()
        }

        override fun end(s: Swipe) {
            val pan = qp.pan
            pan.end(s != Swipe.None && (s == Swipe.Right) != pan.front, now())
            poke()
        }
    }

    fun shown(atHome: Boolean, rest: Boolean, over: OverKind?): HomeShown {
        val p = qp.pan
        val home = atHome && rest && !p.shown
        val modal = over == OverKind.Sheet || over == OverKind.Dlg
        return HomeShown(home, atHome && p.shown, atHome && p.front && p.still, atHome && p.front && modal, home && gate)
    }

    fun tick(t: Long) {
        menu.items.look(t)
        menu.ring.draw(t.toFloat())
        qp.press.look(t)
        frame = t
        menu.tick(t)
        qp.pan.settle(t)
    }

    fun animating(frame: Long): Boolean = menu.animating(frame) || qp.animating(frame) || qpTouch.moving || qpTouch.scroll.dragging

    fun wakeAt(frame: Long): Long? = listOfNotNull(menu.wakeAt(frame), qp.wakeAt(frame)).minOrNull()

    fun downBefore() = menu.stopAim()

    fun downAfter(at: Offset) {
        qpLandAim(qp, qpTouch, qpLayout)
        val p = qp.pan
        if (p.moving(now()) && !p.dragged) {
            p.land(now())
            poke()
        }
        peekDown = p.front && inPeek(at)
    }

    fun off(): Float = menu.off(homeTouch.state.firstVisibleItemScrollOffset, geo)

    fun paint(ringLit: Boolean): HomePaint = LauncherHomePaint(geo, inks, menu, ringLit, qpLayout, qpWords, qp, qpBox, qpTouch, gate, ::off)

    internal fun landed() {
        held = null
    }

    internal fun gateOff() {
        val p = qp.pan
        val toFront = if (p.dragged) !p.front else p.front && !p.still
        if (!toFront) return
        p.run(false, now())
        poke()
    }

    private fun inPeek(o: Offset): Boolean = o.x >= peekBox.x0 && o.x < peekBox.x1 && o.y >= peekBox.y0 && o.y < peekBox.y1

    private fun panLayers(front: Boolean): Boolean {
        if (front && !gate) return false
        qpPanStart(front, geo, menu, homeTouch, qp, qpTouch, qpLayout, toast)
        return true
    }

    internal fun update(
        entries: List<HomeEntry>,
        scale: Scale,
        ink: HomeInk,
        geo: HomeGeo,
        menu: HomeMenu,
        homeTouch: ListTouch,
        qp: QuickplayState,
        qpWords: QpInk,
        qpLayout: QpLayout,
        qpBox: QpBoxes,
        qpTouch: ListTouch,
        peekBox: Edges,
        source: QpSource?,
        gate: Boolean,
        now: () -> Long,
        poke: () -> Unit,
        toast: Toast,
        log: (String) -> Unit,
    ) {
        if (menu !== this.menu) this.homeTouch.cancel()
        this.entries = entries
        this.scale = scale
        this.inks = ink
        this.geo = geo
        this.menu = menu
        this.homeTouch = homeTouch
        this.qp = qp
        this.qpWords = qpWords
        this.qpLayout = qpLayout
        this.qpBox = qpBox
        this.qpTouch = qpTouch
        this.peekBox = peekBox
        this.source = source
        this.gate = gate
        this.now = now
        this.poke = poke
        this.toast = toast
        this.log = log
    }
}

@Composable
fun rememberHomeState(
    entries: List<HomeEntry>,
    sub: (HomeEntry) -> String,
    scale: Scale,
    source: QpSource?,
    quickplay: Boolean,
    now: () -> Long,
    poke: () -> Unit,
    toast: Toast,
    covers: Covers,
    measurer: TextMeasurer,
    fonts: FontFamily.Resolver,
    density: Density,
    log: (String) -> Unit,
): HomeState {
    require(entries.distinctBy { it.word }.size == entries.size) { "a home entry is listed once" }
    val context = LocalContext.current
    val words = remember(entries) { entries.map { it.word } }
    val qp = remember(source) { QuickplayState().also { it.waiting = source != null } }
    val ink = remember(entries, sub, scale, density, fonts) { homeInk(measurer, fonts, scale, density, entries, sub) }
    val geo = remember(scale, entries.size) { HomeGeo(scale, entries.size) }
    val menu = rememberHomeMenu(words, now)
    val homeTouch = rememberListTouch(Kind.List, scale, edge = Edge.Lead)
    val qpSet = remember(qp.data) { qpTiles(qp.data) }
    val qpWords = remember(scale, density, fonts, qp.status) { qpInk(measurer, fonts, scale, density, qp.status) }
    val qpLayout = remember(qpSet, scale, qpWords) { QpLayout(scale, qpSet, qpWords.hint.size) }
    val qpBox = remember(qpLayout, density, fonts, covers) { qpBoxes(measurer, fonts, qpLayout, density, covers) }
    val qpTouch = rememberListTouch(Kind.Quickplay, scale, qp::forget)
    val peekBox = remember(scale, qpLayout.panelPx) { menuPeek(scale, qpLayout.panelPx) }
    val state = remember { HomeState(entries, scale, ink, geo, menu, homeTouch, qp, qpWords, qpLayout, qpBox, qpTouch, peekBox, source, quickplay, now, poke, toast, log) }
    state.update(entries, scale, ink, geo, menu, homeTouch, qp, qpWords, qpLayout, qpBox, qpTouch, peekBox, source, quickplay, now, poke, toast, log)
    LaunchedEffect(menu) {
        withFrameMillis { menu.enter(it) }
        poke()
    }
    LaunchedEffect(quickplay) { if (!quickplay) state.gateOff() }
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        qp.waiting = qp.data.generation == 0L
        var out = false
        combine(src.ready, snapshotFlow { qp.status.isNotEmpty() }) { ready, card -> ready to card }.collect { (ready, card) ->
            val was = out
            out = card
            when {
                card -> qp.waiting = false
                !ready -> qp.waiting = true
                was -> qp.waiting = qp.data.generation == 0L
                else -> return@collect
            }
            poke()
        }
    }
    LaunchedEffect(source, scale, qpWords, qpTouch) {
        val src = source ?: return@LaunchedEffect
        qpLoads(src, qp, Tok.quickplay_tiles.toInt(), { state.log(it) }) { d ->
            if (d.generation != qp.data.generation) qp.reload()
            qpTouch.halt()
            val next = QpLayout(scale, qpTiles(d), qpWords.hint.size)
            qp.build(next, qpPos(qpTouch, qp, next))?.let {
                qp.ask(it, next.pxOf(it))
                qpTouch.state.requestScrollToItem(0, next.pxOf(it))
            }
            poke()
        }
    }
    LaunchedEffect(source, qpLayout) {
        val src = source ?: return@LaunchedEffect
        qp.keep(qpLayout)
        for (i in 0 until qpLayout.count) {
            val ref = qpLayout.tiles[i].item.art as? ArtRef ?: continue
            val side = qpLayout.rect(i, 0L).w
            if (qp.tried(ref, side)) continue
            val got = quiet { withContext(Dispatchers.IO) { listOfNotNull(Art.get(context).cover(ref, side) { src.art(ref, side) }?.asImageBitmap()) } } ?: continue
            qp.putArt(ref, side, got.firstOrNull())
            poke()
        }
    }
    LaunchedEffect(qp, qpTouch, qpLayout) {
        snapshotFlow { qpTouch.state.firstVisibleItemScrollOffset to qpTouch.moving }.collect { (off, _) ->
            if (qpTouch.moving || qpTouch.scroll.dragging) qp.focus = qpLayout.clamp(qp.focus, qpLayout.ofPx(off.toFloat()))
            poke()
        }
    }
    LaunchedEffect(homeTouch, geo, menu) {
        snapshotFlow { homeTouch.state.firstVisibleItemScrollOffset }.collect {
            menu.moved(it)
            if (homeTouch.moving || homeTouch.scroll.dragging) {
                menu.afterScroll(geo.offU(it), geo, now())
                poke()
            }
        }
    }
    return state
}

@Composable
fun HomeBack(state: HomeState, stack: PageStack<*>) {
    BackHandler(enabled = state.front && !stack.canBack) { state.back() }
}

@Composable
fun <S : Any> HomeLayer(
    state: HomeState,
    host: MusicHost<S>,
    models: MusicModels,
    music: MusicSource?,
    page: Scale,
    a: Arbiter,
    onEntry: (HomeEntry) -> Unit,
    onRing: () -> Unit = { host.playOpen(npOpenForm(true, false)) { shuffleLibrary(it) } },
) {
    val scope = rememberCoroutineScope()
    val stack = host.stack
    HomeBack(state, stack)
    val atHome = stack.top == null
    val rest = stack.phase == Phase.Rest
    val over = stack.overlay
    val shown = state.shown(atHome, rest, over?.kind)
    val ringLit = host.feed != null
    val scale = state.scale
    val ink = state.inks
    val menu = state.menu
    val geo = state.geo
    val qp = state.qp
    val qpLayout = state.qpLayout
    val qpWords = state.qpWords
    val qpBox = state.qpBox
    val qpTouch = state.qpTouch
    val homeTouch = state.homeTouch
    val source = state.source
    val now = state.now
    val poke = state.poke
    val pan = qp.pan
    val homeLive = atHome && rest && !pan.front
    val frontLive = atHome && rest && pan.front
    LaunchedEffect(stack.turn) { if (stack.turn == null) state.landed() }
    LaunchedEffect(music, qp) {
        val lib = music?.library
        if (lib == null) {
            qp.status = ""
            return@LaunchedEffect
        }
        lib.state.collect { qp.status = cardLine(it.card) }
    }
    fun qpHold(tile: QpTile): Boolean {
        if (tile.section == QP_PLAYING) return false
        val s = music ?: return false
        if (!s.quickplay.available.value) return false
        val q = tile.item
        val g = s.library.generation.value
        val listing = when (q.kind) {
            QpKind.Album -> Listing.AlbumTracks(q.id)
            QpKind.Artist -> Listing.ArtistTracks(q.id)
            else -> null
        }
        val track = if (q.kind == QpKind.Song) q.id else null
        return host.openMusic(SheetTarget(SheetKind.Tile, q.title, listing, g, track = track, pin = q.kind.api(), id = q.id, pinned = tile.section == QP_PINS, from = SheetFrom.Tile))
    }
    if (shown.peek) {
        val peek = rememberLook(qpLayout, qpBox, covered = false) { qpAlpha(0f, menu.peek(state.frame)) to qp.look(qpLayout, state.frame) }
        Canvas(Modifier.pxSize(page.widthPx, page.heightPx)) {
            val (aq, look) = peek.value
            qpPanel(qpLayout, qpWords, qp, qpBox, 0f, aq, false, look, qpOff(qpTouch, qp, qpLayout))
        }
    }
    HomeLayer(
        ink, menu, geo, a, homeTouch, { state.frame }, now, ringLit, live = homeLive, visible = shown.home, poke = poke,
        onRing = onRing,
        onSelect = onEntry,
    )
    if (homeLive) Box(Modifier.pxAt(peekHit(scale)).tapTarget(a, Tap(onPress = {}, onCancel = {}, onTap = { state.show(true) }), PEEK))
    if (shown.pan) {
        Canvas(Modifier.pxSize(page.widthPx, page.heightPx)) {
            panFrame(ink, menu, geo, pan, state.frame, ringLit, state.off(), qpLayout, qpWords, qp, qpBox, qpOff(qpTouch, qp, qpLayout))
        }
    }
    if (atHome && pan.front) {
        QuickplayFront(
            qpLayout, qpWords, qp, qpBox, a, qpTouch, { state.frame }, now, live = frontLive, visible = shown.front, poke = poke,
            onPlay = { t ->
                val q = source
                if (!qpResumes(t, music?.player?.state?.value?.track, models.np.variant)) {
                    val untap = {
                        qp.press.untap(now())
                        poke()
                    }
                    val asked = q != null && host.playOpen(npOpenForm(true, true), refused = untap) { q.play(t.item) }
                    if (!asked) untap()
                } else {
                    scope.launch(FailFast) { quiet { source?.resume() } }
                    host.openNp(npOpenForm(true, true))
                }
            },
            onBack = { state.show(false) },
            hideFocus = stack.hideFocus,
            snap = stack.snap,
            onHold = ::qpHold,
            frozen = shown.frontFrozen,
        )
    }
}
