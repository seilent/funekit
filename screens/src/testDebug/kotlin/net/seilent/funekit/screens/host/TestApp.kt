package net.seilent.funekit.screens.host

import android.graphics.Matrix
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.seilent.funekit.FuneTheme
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kept
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.navBand
import net.seilent.funekit.kit.pageStatus
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.rememberStatusInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.InputGate
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.MoveStatus
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.PlayOpen
import net.seilent.funekit.kit.stack.watchHeld
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.hub.musicModel
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import net.seilent.funekit.screens.music.nowplaying.OffsetEdit
import net.seilent.funekit.screens.music.nowplaying.NpSplit
import net.seilent.funekit.screens.music.pivotBy
import net.seilent.funekit.screens.music.pivotPan
import net.seilent.funekit.screens.music.pivotSwitch

class TestApp(page: Scale, np: Scale, private val src: MusicSource?, private val level: VolumeLevel, private val now: () -> Long) : MusicApp<MusicEntry> {
    var page by mutableStateOf(page)
    var np by mutableStateOf(np)
    var split by mutableStateOf<NpSplit?>(null)
    val gate = InputGate()
    val music = MusicModels()
    val models = PageModels { e, p -> musicModel(e, p as MusicPage, music.hub) }
    lateinit var host: MusicHost<MusicEntry>
    lateinit var stack: PageStack<MusicEntry>
    lateinit var toast: Toast
    lateinit var movePaint: MovePaint
    lateinit var menu: Menu
    lateinit var menuHost: MenuHost
    var pokes = 0
    var lyricsOn by mutableStateOf(false)
    var edit: OffsetEdit? = null
    var asked: () -> Unit = {}
    val backdropFlow = MutableStateFlow<NpPicture?>(null)

    override fun music(s: MusicEntry): MusicEntry = s

    override fun screen(e: MusicEntry): MusicEntry = e

    override fun homeHas(word: String): Boolean = false

    override fun homeItem(word: String): Int = -1

    override fun homeBusy(now: Long): Boolean = false

    override fun homeLand() {}

    override fun homeFront(): Boolean = false

    override fun liftRow(top: MusicEntry?): Int? = null

    override fun cancel(top: MusicEntry?) {}

    override val phone = MutableStateFlow<PhoneSession?>(null)

    override val backdrop: StateFlow<NpPicture?> get() = backdropFlow

    override val lyricsView: Boolean get() = lyricsOn

    override fun offset(track: Long): OffsetEdit? = edit.also { asked() }

    private val home = object : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = Unit
    }

    fun npTop(): Boolean {
        if (stack.armed && host.opened?.state == PlayOpen.State.Armed) return false
        val t = stack.turn
        val dest = if (stack.phase != Phase.Rest && t != null) t.to else stack.top
        return dest?.let(host::isNp) == true
    }

    fun compose(rule: ComposeContentTestRule, root: Boolean = false) {
        rule.setContent {
            FuneTheme(page) {
                Pages(root)
            }
        }
    }

    @Composable
    private fun Pages(root: Boolean) {
        val m = rememberTextMeasurer()
        val fonts = LocalFontFamilyResolver.current
        val density = LocalDensity.current
        val covers = rememberCovers()
        val a = remember(page.touchPpu) { Arbiter(page) }
        val toast = remember { Toast() }
        this@TestApp.toast = toast
        val jump = remember { Overlay(OverKind.Jump) }
        val menuHost = remember { MenuHost() }
        this@TestApp.menuHost = menuHost
        menu = menuHost.menu
        val move = remember { PageMove() }
        val movePaint = remember { MovePaint(move) }
        this@TestApp.movePaint = movePaint
        val bands = remember(page.heightPx) { groundBands(page.heightPx, true) }
        val grid = remember(page) { page.kept() }
        val accent = Accent.color(Accent.defaultHue, true)
        val env = MusicEnv(page, np, m, fonts, density, covers, bands, accent, true, a, now, { pokes++ }, grid)
        val h = rememberMusicHost(this@TestApp, music, models, env, src, split, level, toast, jump, menuHost, movePaint)
        host = h
        stack = remember {
            PageStack<MusicEntry>(
                MoveForms.board(page.board),
                { h.kind(it) ?: PageKind.Plain },
                pageBack = { s, _ -> h.pageBack(s) },
                retargets = h::retargets,
                move = move,
                ready = { h.ready(it) ?: true },
                enter = h::enter,
                drop = h::drop,
                started = h::started,
                starting = h::starting,
                swapped = h::swapped,
                dropped = h::dropped,
                isNp = h::isNp,
                gate = if (root) gate else null,
                albumOf = h::albumOf,
            ) { t ->
                movePaint.ends(t, HomeSide(page, home, 0f), t.from?.let { s -> h.side(s)?.let { PageSide(if (h.isNp(s)) np else page, it) } }, t.to?.let { s -> h.side(s)?.let { PageSide(if (h.isNp(s)) np else page, it) } }, MoveForms.board(page.board))
            }.also { h.stack = it }
        }
        val stack = stack
        SideEffect {
            if (root) stack.forms = MoveForms.board(page.board)
            h.applied()
        }
        h.StackClock { MoveKind.T4n }
        val status = StatusState("", -1, false, Play.None, false, -1, -1)
        if (!root) {
            Box(Modifier.pxSize(page.widthPx, page.heightPx).arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                h.MusicPages(emptyMap(), status)
                h.MenuOverlayHost()
                h.ToastHost(false)
                h.VolumeHost()
            }
            return
        }
        LaunchedEffect(stack.turn) { if (stack.turn == null) movePaint.landed() }
        DisposableEffect(stack) { onDispose { gate.dispose() } }
        val heldTarget by rememberUpdatedState { o: Offset, at: Long ->
            val ov = stack.overlay
            when {
                ov != null && (ov.kind == OverKind.Sheet || ov.kind == OverKind.Dlg) -> menuHost.menu.backTarget(o.x, o.y, ov, at, if (page.navOn) navBand(page).y0 else page.heightPx)
                ov != null || stack.phase != Phase.Exit -> false
                else -> h.heldTarget(stack.turn?.from, o) ?: false
            }
        }
        val heldBand by rememberUpdatedState { x: Float, width: Int -> if (page.edgeBack) a.edgeAt(x, width) else Gesture.Edge.None }
        val heldSlop by rememberUpdatedState(a.slop)
        SideEffect {
            val left = page.left.toFloat()
            gate.carry = { x, y -> movePaint.carry(x - left, y, stack.clock)?.let { Matrix(it).apply { preTranslate(-left, 0f); postTranslate(left, 0f) } } }
            gate.holds = { ev -> stack.hold(ev.eventTime, heldTarget(Offset(ev.x - left, ev.y), ev.eventTime), heldBand(ev.x - left, page.widthPx)) }
        }
        DisposableEffect(gate) {
            onDispose {
                gate.carry = null
                gate.holds = null
            }
        }
        val popKind = h.popKind
        val rest = stack.phase == Phase.Rest
        val turn = stack.turn
        val over = stack.overlay
        val modal = over?.kind == OverKind.Sheet || over?.kind == OverKind.Dlg
        val pages = listOfNotNull(stack.top, turn?.from, turn?.to).distinct().associateWith { p ->
            val visible = rest && p == stack.top && !stack.jumpSettled
            Shown(visible, !visible || modal)
        }
        val pivotTop = h.pivot(stack.top)
        val npOn = stack.top?.let(h::isNp) == true
        val swipes = Swipes(
            edges = page.edgeBack,
            bottom = false,
            turn = pivotTop != null || npOn,
            acts = { s ->
                (s == Swipe.Left || s == Swipe.Right) &&
                    (a.gesture.edge != Gesture.Edge.None || pivotTop == null || pivotTop.first.canSwitch(pivotBy(s)))
            },
            pan = if (npOn) {
                h.npPan
            } else {
                pivotTop?.let { (pm, c) -> pivotPan(pm, c, { page.ppu }, now) { stack.phase == Phase.Rest && stack.overlay == null && a.gesture.edge == Gesture.Edge.None } }
            },
            onSwipe = { s, e ->
                if ((e == Gesture.Edge.Left && s == Swipe.Right) || (e == Gesture.Edge.Right && s == Swipe.Left)) {
                    stack.back(popKind)
                } else if (pivotTop != null && rest && pivotBy(s) != 0 && pivotTop.first.canSwitch(pivotBy(s))) {
                    pivotSwitch(pivotTop.first, pivotTop.second, pivotTop.first.pivot + pivotBy(s), now())
                } else if (npOn && rest) {
                    h.skip(s)
                }
            },
        )
        val statusInk = rememberStatusInk(status, page, pageStatus())
        Box(
            Modifier.pxSize(page.widthPx, page.heightPx)
                .pointerInput(stack) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        h.down()
                        val moving = stack.moving
                        var up: Long? = null
                        var upX: Float? = null
                        try {
                            if (moving || stack.heldTap(d.uptimeMillis)) watchHeld(stack, d, heldSlop, heldTarget(d.position, d.uptimeMillis), heldBand(d.position.x, size.width), popKind)
                            var e = currentEvent
                            while (e.changes.any { it.pressed }) e = awaitPointerEvent(PointerEventPass.Initial)
                            up = e.changes.maxOfOrNull { it.uptimeMillis }
                            upX = e.changes.firstOrNull()?.position?.x
                        } finally {
                            h.up(up ?: now(), upX)
                        }
                    }
                }
                .arbiter(a, swipes),
        ) {
            h.MusicPages(pages, status)
            h.MoveCanvas(!rest && turn != null, MoveStatus(statusInk, statusInk, accent, true))
            h.JumpHost({ null }) { _, _ -> }
            h.MenuOverlayHost()
            h.ToastHost(over?.kind == OverKind.Volume)
            h.VolumeHost()
        }
    }
}

object StillLevel : VolumeLevel {
    override fun percent(): Int = 0

    override fun stepPct(): Int = 1

    override suspend fun step(detents: Int) {}

    override suspend fun set(pct: Int) {}
}
