package net.seilent.funekit.screens.host

import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.seilent.funekit.Case
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.api.Kind as ApiKind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpGridOverlay
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.MenuOverlay
import net.seilent.funekit.kit.NavGround
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastStrip
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.npStatus
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.overlay.VolumeLayer
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.pxAt
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberStatusInk
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.MoveStatus
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.PageStackClock
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.PlayOpen
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.kit.textWidth
import net.seilent.funekit.kit.toastPlace
import net.seilent.funekit.kit.toastSurface
import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.kit.touch.Pan
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.PivotPage
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.RING_ADD
import net.seilent.funekit.screens.music.artist.artistAdd
import net.seilent.funekit.screens.music.artist.artistPlay
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.hubPickAtDrop
import net.seilent.funekit.screens.music.hub.hubPicked
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.nowplaying.NowPlayingLayer
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpAct
import net.seilent.funekit.screens.music.nowplaying.NpFeed
import net.seilent.funekit.screens.music.nowplaying.NpHalf
import net.seilent.funekit.screens.music.nowplaying.NpInks
import net.seilent.funekit.screens.music.nowplaying.NpMeasure
import net.seilent.funekit.screens.music.nowplaying.NpPaint
import net.seilent.funekit.screens.music.nowplaying.NpPart
import net.seilent.funekit.screens.music.nowplaying.NpSplit
import net.seilent.funekit.screens.music.nowplaying.holding
import net.seilent.funekit.screens.music.nowplaying.npFlat
import net.seilent.funekit.screens.music.nowplaying.npNanosAt
import net.seilent.funekit.screens.music.overlay.ADD_TO_PLAYLIST
import net.seilent.funekit.screens.music.overlay.DELETE_ASK
import net.seilent.funekit.screens.music.overlay.DELETE_PLAYLIST
import net.seilent.funekit.screens.music.overlay.GO_TO_ALBUM
import net.seilent.funekit.screens.music.overlay.GO_TO_ARTIST
import net.seilent.funekit.screens.music.overlay.INFO
import net.seilent.funekit.screens.music.overlay.KEEP_PLAYLIST
import net.seilent.funekit.screens.music.overlay.OPEN_PLAYLIST
import net.seilent.funekit.screens.music.overlay.PLAY
import net.seilent.funekit.screens.music.overlay.SHUFFLE
import net.seilent.funekit.screens.music.overlay.SheetDone
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.artistRows
import net.seilent.funekit.screens.music.overlay.confirmDelete
import net.seilent.funekit.screens.music.overlay.infoLines
import net.seilent.funekit.screens.music.overlay.infoMax64
import net.seilent.funekit.screens.music.overlay.infoOf
import net.seilent.funekit.screens.music.overlay.picker
import net.seilent.funekit.screens.music.overlay.runEntry
import net.seilent.funekit.screens.music.overlay.runPick
import net.seilent.funekit.screens.music.overlay.runSheet
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.pivotBy
import net.seilent.funekit.screens.music.queue.QueueModel

private const val OVER = 1
private const val LEAD_MS = 17L
private const val MS_PER_S = 1000f

data class Shown(val visible: Boolean, val frozen: Boolean)

fun displayLead(hz: Float?): Long = if (hz == null || hz <= 0f) LEAD_MS else Math.round(MS_PER_S / hz).toLong()

fun <S : Any> toastAtTurn(t: Turn<S>, toast: Toast) {
    if (t.kind != MoveKind.T8) toast.hide()
}

fun <S : Any> pageThumbsAtTurn(t: Turn<S>, thumb: (S) -> PageThumb?) {
    for (sc in listOfNotNull(t.from, t.to).distinct()) {
        thumb(sc)?.let { p ->
            p.fade.track(p.on, false, t.at)
            p.poke()
        }
    }
}

fun <S : Any> pageThumbAtSwap(top: S, at: Long, thumb: (S) -> PageThumb?) {
    thumb(top)?.let {
        it.fade.show(at)
        it.poke()
    }
}

private fun scaleKey(s: Scale): List<Any> = listOf(s.ppu, s.stdPpu, s.widthPx, s.heightPx, s.stdContentBottom)

private suspend fun <T> quiet(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

class MusicHost<S : Any> internal constructor(
    private val music: MusicModels,
    private val models: PageModels,
    private val toast: Toast,
    private val jump: Overlay,
    private val menuHost: MenuHost,
    private val movePaint: MovePaint,
    private val scope: CoroutineScope,
    app: MusicApp<S>,
    env: MusicEnv,
    src: MusicSource?,
    volume: VolumeLevel,
    types: MusicTypes,
    npGeo: NpLayout,
    npHalves: List<NpHalf>,
    npHalfInks: List<NpInks>,
    navGround: NavGround,
    split: NpSplit?,
    private var view: View,
) : MusicActs {
    lateinit var stack: PageStack<S>
    private var app: MusicApp<S> = app
    private var env by mutableStateOf(env)
    private var src by mutableStateOf(src)
    private var volume by mutableStateOf(volume)
    internal var types by mutableStateOf(types)
        private set
    private var npGeo by mutableStateOf(npGeo)
    private var npHalves by mutableStateOf(npHalves)
    private var npHalfInks by mutableStateOf(npHalfInks)
    private var navGround by mutableStateOf(navGround)
    private var split by mutableStateOf(split)
    private var scaleAt: List<Any> = scaleKey(env.page)
    private var splitAt: NpSplit? = split
    private var phone: PhoneSession? = null
    private var appPop: (Turn<S>) -> MoveKind = { MoveKind.T4n }
    private var npHides = true
    private var npPrimed: Long? = null
    internal var jumpOwner: S? by mutableStateOf(null)
    internal var colFireAt: S? by mutableStateOf(null)
    internal var searchAt: S? by mutableStateOf(null)
    internal var volDue by mutableStateOf(false)
    var feed: NpFeed? = null
        private set
    var opened: PlayOpen? = null
        private set
    val hud = VolumeHud(env.np.board, env.np.w, env.np.h)
    private val volumeOverlay = Overlay(OverKind.Volume, hud) { if (hud.pendingSeek() != null) volSeek() }
    private val infoRole = Role(Roles.dialogItem.size, Roles.dialogItem.weight, Case.Mixed, Roles.dialogItem.alpha, Roles.dialogItem.tracking)
    val popKind: (Turn<S>) -> MoveKind = { t -> pop(t) ?: appPop(t) }

    val chromes: MusicChromes get() = types.chromes

    private val np: NowPlayingModel get() = music.np

    private val npArtHalf: NpHalf get() = npHalves.holding(NpPart.Art)

    private val npSeekHalf: NpHalf get() = npHalves.holding(NpPart.Times)

    internal fun update(
        app: MusicApp<S>,
        env: MusicEnv,
        src: MusicSource?,
        volume: VolumeLevel,
        types: MusicTypes,
        phone: PhoneSession?,
        feed: NpFeed?,
        npGeo: NpLayout,
        npHalves: List<NpHalf>,
        npHalfInks: List<NpInks>,
        navGround: NavGround,
        split: NpSplit?,
        view: View,
    ) {
        this.app = app
        this.env = env
        this.src = src
        this.volume = volume
        this.types = types
        this.phone = phone
        this.feed = feed
        this.npGeo = npGeo
        this.npHalves = npHalves
        this.npHalfInks = npHalfInks
        this.navGround = navGround
        this.split = split
        this.view = view
        hud.board = env.np.board
        hud.w = env.np.w
        hud.h = env.np.h
    }

    internal fun applied() {
        types.retain(models)
        val key = scaleKey(env.page)
        if (scaleAt != key) {
            scaleAt = key
            stack.touch()
            stack.overlay?.takeIf { it.kind == OverKind.Sheet || it.kind == OverKind.Dlg }?.let { frameMenu(menuHost.menu, env.page, menuHost.lift) { app.liftRow(stack.top) } }
        }
        if (splitAt !== split) {
            splitAt = split
            stack.touch()
            np.seekCancel()
            np.slideLand(now(), npArtHalf.g.s.w)
        }
    }

    private fun now(): Long = env.now()

    private fun entryOf(s: S?): MusicEntry? = s?.let(app::music)

    private fun modelOf(s: S?): PageModel? = entryOf(s)?.let { models[it.entry] }

    private fun hubOf(s: S?): HubModel? = modelOf(s) as? HubModel

    private fun queueOf(s: S?): QueueModel? = modelOf(s) as? QueueModel

    private fun colOf(s: S?): AlbumColModel? = modelOf(s) as? AlbumColModel

    private fun playlistsOf(s: S?): PlaylistsModel? = modelOf(s) as? PlaylistsModel

    private fun homeBusy(t: Long): Boolean = stack.top == null && app.homeBusy(t)

    private fun at(page: MusicPage, kind: MoveKind, row: Int = -1, head: Row? = null): S = app.screen(musicEntry(stack.entry(), page, kind, entryOf(stack.top), models, row, head))

    fun isNp(s: S): Boolean = entryOf(s)?.page == MusicPage.NowPlaying

    private fun npReady(): Boolean {
        if (opened?.state != PlayOpen.State.Released) return true
        val cur = src?.player?.state?.value?.track ?: return false
        return np.shown.track == cur && npPrimed == cur
    }

    private fun thumbOf(s: S): PageThumb? = types.thumb(modelOf(s))

    private fun pop(t: Turn<S>): MoveKind? =
        musicPopKind(t, app::music, models, app::homeHas)?.also { if (it == MoveKind.T8) movePaint.npForm = npCloseForm(t.to == null, app.homeFront()) }

    fun ready(s: S): Boolean? {
        val e = entryOf(s) ?: return null
        return if (e.page == MusicPage.NowPlaying) npReady() else models[e.entry]?.ready ?: true
    }

    fun enter(s: S) {
        val e = entryOf(s) ?: return
        types.enter(models.enter(e.entry, e.page), e)
    }

    fun drop(s: S) {
        entryOf(s)?.let { models.drop(it.entry) }
    }

    fun kind(s: S): PageKind? = entryOf(s)?.let { models[it.entry]?.kind ?: PageKind.Plain }

    fun pageBack(s: S): Boolean = entryOf(s) != null && types.back(modelOf(s))

    fun retargets(s: S): Boolean = !isNp(s)

    fun albumOf(s: S): Long = (entryOf(s)?.page as? MusicPage.Album)?.id ?: -1L

    fun started(t: Turn<S>) {
        npHides = t.kind == MoveKind.T7 || t.kind == MoveKind.T8
        toastAtTurn(t, toast)
        npHalfInks.forEach { k -> npAtTurn(t, k, ::isNp) }
        if (listOfNotNull(t.from, t.to).any(::isNp)) npPrimed = np.shown.track
        pageThumbsAtTurn(t, ::thumbOf)
        if (t.kind == MoveKind.T8) t.to?.let { swapped(it, t.at) }
    }

    fun starting(t: Turn<S>) {
        for (s in listOfNotNull(t.from, t.to)) modelOf(s)?.lists?.forEach { it.land() }
        modelOf(t.from)?.lists?.forEach { it.cancel() }
    }

    fun swapped(s: S, at: Long) = pageThumbAtSwap(s, at, ::thumbOf)

    fun dropped(s: S, at: Long) {
        if (hubOf(s)?.colPick?.let { it >= 0 } == true) colFireAt = s
        hubPickAtDrop(hubOf(s), at)
        swapped(s, at)
    }

    fun side(s: S): PagePaint? {
        val e = entryOf(s) ?: return null
        if (e.page == MusicPage.NowPlaying) return NpPaint(npGeo, np, npHalves.zip(npHalfInks), { np.art }, env.accent) { npHides }
        return types.side(models[e.entry])
    }

    fun pivot(s: S?): Pair<PivotPage, Chrome>? = types.pivot(modelOf(s))

    fun heldTarget(from: S?, o: Offset): Boolean? {
        val e = entryOf(from) ?: return null
        if (e.page == MusicPage.NowPlaying) return npHalves.holding(NpPart.Back).let { h -> h.holds(o.x, o.y) && h.g.backHit(h.ux(o.x), h.uy(o.y)) }
        val b: Edges? = types.chrome(models[e.entry])?.headerBox()
        return b != null && o.x >= b.x0 && o.x < b.x1 && o.y >= b.y0 && o.y < b.y1
    }

    fun warm() {
        hubOf(stack.top)?.jumpPick = -1
        hubOf(stack.top)?.colPick = -1
    }

    fun down() {
        hubOf(stack.top)?.stopAim()
    }

    fun up(t: Long, upX: Float?) {
        if (np.seeking) np.seekUp(npSeekHalf.g, upX?.let { npSeekHalf.ux(it) } ?: np.seekX)?.let { ms -> scope.launch(FailFast) { quiet { src?.player?.seek(ms) } } }
        np.gestureUp()
        hubOf(stack.top)?.let {
            it.fade.release(t)
            it.jump.fade.release(t)
        }
        queueOf(stack.top)?.fade?.release(t)
        menuHost.menu.held = false
        menuHost.menu.fade.release(t)
    }

    fun skip(s: Swipe) {
        if (!np.slide.touch && (s == Swipe.Left || s == Swipe.Right)) scope.launch(FailFast) { quiet { npSkip(pivotBy(s), np, src, phone) } }
    }

    val npPan = object : Pan {
        override fun begin(x: Float, origin: Float, down: Offset): Boolean {
            if (stack.phase != Phase.Rest || env.a.gesture.edge != Gesture.Edge.None) return false
            if (np.seeking) return true
            val ok = np.slideLock(npArtHalf.ux(x))
            if (ok) env.poke()
            return ok
        }

        override fun drag(x: Float) {
            if (np.seeking) np.seekMove(npSeekHalf.g, npSeekHalf.ux(x)) else np.slideDrag(npArtHalf.ux(x), npArtHalf.g.s.w)
        }

        override fun end(s: Swipe) {
            if (np.seeking) {
                np.seekUp(npSeekHalf.g, np.seekX)?.let { ms -> scope.launch(FailFast) { quiet { src?.player?.seek(ms) } } }
                return
            }
            val d = when (s) {
                Swipe.Left -> 1
                Swipe.Right -> -1
                else -> 0
            }
            val t = now()
            if (np.slideUp(t, d, npArtHalf.g.s.w, npNanosAt(t))) scope.launch(FailFast) { quiet { if (d > 0) src?.player?.next() else src?.player?.prev() } }
        }
    }

    fun openMenu(
        form: MenuForm,
        title: String,
        items: List<String>,
        readOnly: Boolean,
        mixedRow: Int,
        lead: Int,
        confirm: Boolean,
        act: (Int) -> Boolean,
        lift: Boolean = false,
        setup: (Menu) -> Unit = {},
    ) {
        val menu = menuHost.menu
        if (stack.overlay != null) stack.accept()
        menu.open(form, title, items, now(), readOnly, mixedRow, lead = lead, confirm = confirm)
        setup(menu)
        menuHost.lift = lift
        frameMenu(menu, env.page, lift) { app.liftRow(stack.top) }
        menuHost.act = act
        stack.open(Overlay(if (form == MenuForm.Sheet) OverKind.Sheet else OverKind.Dlg))
    }

    fun openJump(owner: S) {
        jumpOwner = owner
        jump.lead = displayLead(view.display?.refreshRate)
        stack.open(jump)
    }

    fun openPage(page: MusicPage) {
        if (src == null) return
        app.homeLand()
        env.poke()
        stack.push(at(page, MoveKind.T2), MoveKind.T2)
    }

    fun openNp(form: NpForm) {
        if (!npHasTrack(np) || stack.phase != Phase.Rest || stack.overlay != null) return
        if (stack.pages.any(::isNp)) return
        if (homeBusy(now())) return
        val t = now()
        movePaint.npForm = form
        np.sync(np.shown, t)
        np.resetFocus(t)
        env.poke()
        stack.push(at(MusicPage.NowPlaying, MoveKind.T7), MoveKind.T7)
    }

    fun openVolume(t: Long) {
        if (stack.phase != Phase.Rest || homeBusy(t)) {
            volDue = true
            return
        }
        val o = stack.overlay
        if (o != null && o !== volumeOverlay) return
        if (o == null) {
            val top = stack.top
            val e = entryOf(top)
            if (e != null) models[e.entry]?.lists?.forEach { it.cancel() } else app.cancel(top)
            toast.cancel(t)
            stack.open(volumeOverlay, hides = false, covers = false)
        }
        val artist = if (np.variant) np.shown.from else np.shown.artist
        hud.show(t, volume.percent(), playOf(feed) == Play.Playing, artist, np.shown.title)
    }

    fun volPress(d: Int) {
        scope.launch(FailFast) {
            if (quiet { volume.step(d) } != null) openVolume(now())
        }
    }

    private fun volSeek() {
        val s = src ?: return
        if (!hud.seekMoved() || s.player.state.value.track != hud.seekTrack()) return
        val ms = hud.seekTo
        scope.launch(FailFast) { quiet { s.player.seek(ms) } }
    }

    private fun volAct(act: VolumeHud.Act) {
        val s = src
        when (act) {
            VolumeHud.Act.Plus -> volPress(1)
            VolumeHud.Act.Minus -> volPress(-1)
            VolumeHud.Act.Prev, VolumeHud.Act.Next -> scope.launch(FailFast) { quiet { npSkip(if (act == VolumeHud.Act.Next) 1 else -1, np, s, phone) } }
            VolumeHud.Act.Ring -> scope.launch(FailFast) {
                quiet {
                    val p = phone
                    if (np.variant && p != null) p.toggle() else s?.player?.toggle()
                }
            }
            VolumeHud.Act.Volume -> {
                val n = volume.maxPct()?.let { minOf(hud.number, it) } ?: hud.number
                scope.launch(FailFast) {
                    val was = volume.percent()
                    quiet { volume.set(n) } ?: return@launch
                    hud.took(volume.percent())
                    if (volume.percent() != was) openVolume(now())
                }
            }
            VolumeHud.Act.Seek -> volSeek()
            else -> Unit
        }
    }

    private fun volPos(): VolumeHud.Pos {
        val st = src?.player?.state?.value ?: return VolumeHud.Pos()
        val track = st.track ?: return VolumeHud.Pos()
        val dur = st.anchor.durationMs
        return VolumeHud.Pos(!np.variant && dur > 0, st.anchor.ms(SystemClock.elapsedRealtimeNanos()), dur, track)
    }

    fun goNp() {
        val top = stack.top
        if (!npHasTrack(np) || stack.phase != Phase.Rest || stack.overlay != null || (top != null && isNp(top))) return
        val form = npOpenForm(top == null, app.homeFront())
        val kept = stack.pages.lastOrNull(::isNp)
        if (kept == null) {
            openNp(form)
            return
        }
        val t = now()
        movePaint.npForm = form
        np.resetFocus(t)
        env.poke()
        val h = stack.popToNp(kept, t, NowPlayingModel.HOLD_CAP)
        opened = h
        h.done()
    }

    fun openNowPlaying(): Boolean {
        if (!npHasTrack(np)) return false
        openNp(NpForm.Page)
        return true
    }

    override fun playOpen(form: NpForm, keep: Boolean, cut: Boolean, play: suspend (MusicSource) -> Boolean): Boolean {
        val s = src ?: return false
        if (s.library.generation.value <= 0) return false
        val t = now()
        val kept = if (keep) stack.pages.lastOrNull(::isNp) else null
        val h = if (homeBusy(t) || (kept == null && stack.pages.any(::isNp))) {
            null
        } else {
            movePaint.npForm = form
            np.resetFocus(t)
            env.poke()
            when {
                kept != null -> stack.popToNp(kept, t, NowPlayingModel.HOLD_CAP)
                cut || stack.overlay === volumeOverlay -> stack.cutThenPush(at(MusicPage.NowPlaying, MoveKind.T7), t, NowPlayingModel.HOLD_CAP)
                else -> stack.pushOpen(at(MusicPage.NowPlaying, MoveKind.T7), t, NowPlayingModel.HOLD_CAP)
            }
        }
        if (h != null) opened = h
        scope.launch(FailFast) {
            val ok = quiet { play(s) } == true && s.player.state.value.track != null
            if (ok) h?.done() else h?.fail()
        }
        return true
    }

    private fun infoWidth(): (String) -> Int {
        val w = textWidth(infoRole, roleStyle(infoRole, env.page, env.density, Ink.white), env.fonts, env.density, env.covers)
        return { t -> pen64(w(t)) }
    }

    private fun openInfo(title: String, d: SheetDone.Info, swap: Boolean) {
        val s = src ?: return
        val over = stack.overlay
        scope.launch(FailFast) {
            val info = quiet { infoOf(d, s) } ?: return@launch
            val lines = infoLines(info, infoMax64(env.page), infoWidth())
            if (swap) {
                val o = stack.overlay
                if (o == null || o !== over || o.closing) return@launch
                stack.swapMenu(over) { t -> menuHost.menu.open(MenuForm.Sheet, title, lines.lines, t, true, lines.pathRow) }
                menuHost.act = { true }
            } else {
                if (stack.phase != Phase.Rest || stack.overlay != null) return@launch
                openMenu(MenuForm.Sheet, title, lines.lines, true, lines.pathRow, 0, false, { true })
            }
        }
    }

    private fun refuse(over: Overlay?) {
        if (over != null && stack.overlay === over) stack.accept()
    }

    private fun sheetPush(page: MusicPage, kind: MoveKind, row: Int, album: Long, head: Row?, over: Overlay? = stack.overlay) {
        stack.cutPush(at(page, kind, row, head), kind, row, album)
        scope.launch {
            withFrameMillis {}
            withFrameMillis {}
            if (stack.turn == null) refuse(over)
        }
    }

    private fun sourceRow(t: SheetTarget): Int = when (t.from) {
        SheetFrom.Playlists -> playlistsOf(stack.top)?.pane?.focus?.row ?: -1
        SheetFrom.Hub -> hubOf(stack.top)?.let { it.panes[it.pivot].focus.row } ?: -1
        else -> -1
    }

    private fun openPicker(t: SheetTarget, s: MusicSource) {
        val over = stack.overlay
        scope.launch(FailFast) {
            val p = quiet { picker(s) } ?: return@launch refuse(over)
            stack.swapMenu(over) { at ->
                menuHost.menu.open(MenuForm.Sheet, t.title, p.items, at)
                menuHost.act = { k ->
                    menuHost.act = { false }
                    scope.launch(FailFast) {
                        when (val d = quiet { runPick(k, p, t, s) }) {
                            is SheetDone.Toast -> toast.show(now(), d.line, d.title)
                            else -> Unit
                        }
                        if (over != null && stack.overlay === over && !over.closing) stack.accept()
                    }
                    false
                }
            }
        }
    }

    private fun goAlbum(track: Long, generation: Long, s: MusicSource) {
        val over = stack.overlay
        scope.launch(FailFast) {
            val row = quiet {
                val v = s.library.view(generation)
                val a = v.trackIds(track)?.album ?: return@quiet null
                val at = v.indexOf(Listing.Albums, ApiKind.Album, a) ?: return@quiet null
                v.page(Listing.Albums, at, 1).rows.firstOrNull()
            }
            if (stack.overlay !== over || over?.closing == true) return@launch
            if (row == null) return@launch refuse(over)
            sheetPush(MusicPage.Album(row.id), albumPushKind(entryOf(stack.top), models, row, false), -1, row.id, row, over)
        }
    }

    private fun goArtist(t: SheetTarget, s: MusicSource) {
        val over = stack.overlay
        scope.launch(FailFast) {
            val rows = quiet { artistRows(t, s) }
            if (stack.overlay !== over || over?.closing == true) return@launch
            if (rows.isNullOrEmpty()) return@launch refuse(over)
            if (rows.size == 1) return@launch sheetPush(MusicPage.Artist(rows[0].id), MoveKind.T3, -1, -1L, null, over)
            val names = rows.map { it.title }
            stack.swapMenu(over) { at ->
                menuHost.menu.open(MenuForm.Sheet, t.title, names, at)
                menuHost.act = { k ->
                    menuHost.act = { false }
                    rows.getOrNull(k)?.let { r -> sheetPush(MusicPage.Artist(r.id), MoveKind.T3, -1, -1L, null, over) }
                    false
                }
            }
        }
    }

    private fun pickMusic(t: SheetTarget, words: List<String>, i: Int): Boolean {
        val word = words.getOrNull(i) ?: return true
        val s = src ?: return true
        if (word == ADD_TO_PLAYLIST) {
            openPicker(t, s)
            return false
        }
        if (word == OPEN_PLAYLIST) {
            val l = t.listing ?: return true
            val head = Row(t.id, ApiKind.Playlist, t.title, "", null, writable = t.writable || t.kind == SheetKind.Favorites)
            val over = stack.overlay
            val row = sourceRow(t)
            scope.launch(FailFast) {
                val id = (l as? Listing.PlaylistTracks)?.playlist
                val gone = id != null && quiet { s.viewed { v -> v.indexOf(Listing.Playlists, ApiKind.Playlist, id) } } == null
                if (stack.overlay !== over || over?.closing == true) return@launch
                if (gone) return@launch refuse(over)
                sheetPush(MusicPage.ListView(l), MoveKind.T3, row, -1L, head, over)
            }
            return false
        }
        if (word == GO_TO_ALBUM) {
            val track = t.track ?: return true
            goAlbum(track, t.generation, s)
            return false
        }
        if (word == GO_TO_ARTIST) {
            goArtist(t, s)
            return false
        }
        if (t.kind == SheetKind.Entry && word != INFO) {
            val v = modelOf(stack.top) as? ListViewModel
            scope.launch(FailFast) { quiet { runEntry(word, t, v, s, now()) } }
            return true
        }
        if (t.kind == SheetKind.Queue && word != INFO) {
            val q = queueOf(stack.top) ?: return true
            scope.launch(FailFast) { quiet { q.verb(word, t.slot, t.revision, s, now()) } }
            return true
        }
        if (word == DELETE_PLAYLIST) {
            val ask = listOf(DELETE_PLAYLIST, KEEP_PLAYLIST)
            openMenu(MenuForm.Dlg, DELETE_ASK, ask, false, -1, 0, true, { k ->
                if (ask[k] == DELETE_PLAYLIST) scope.launch(FailFast) { quiet { confirmDelete(DELETE_PLAYLIST, t.id, s) } }
                true
            }) { menu -> menu.focus.set(ask.indexOf(KEEP_PLAYLIST), ask.size, now(), false) }
            return false
        }
        if (word == INFO) {
            val track = t.track ?: return true
            openInfo(t.title, SheetDone.Info(track, t.listing == Listing.Queue, t.generation), true)
            return false
        }
        if (word == PLAY || word == SHUFFLE) {
            playOpen(npOpenForm(stack.top == null, app.homeFront()), cut = true) {
                runSheet(word, t, it)
                t.listing != null || (word == PLAY && t.track != null)
            }
            return true
        }
        scope.launch(FailFast) {
            when (val d = quiet { runSheet(word, t, s) }) {
                is SheetDone.Toast -> toast.show(now(), d.line, d.title)
                else -> Unit
            }
        }
        return true
    }

    fun openMusic(t: SheetTarget): Boolean {
        val s = src ?: return false
        if (stack.phase != Phase.Rest || stack.overlay != null || sheetItems(t, false).isEmpty()) return false
        fun open(pinned: Boolean) {
            val words = sheetItems(t, pinned)
            openMenu(MenuForm.Sheet, t.title, words, false, -1, 0, false, { i -> pickMusic(t, words, i) })
        }
        t.pinned?.let {
            open(it)
            return true
        }
        scope.launch(FailFast) {
            val pinned = t.pin?.let { k -> quiet { s.viewed { v -> v.pinned(k, t.id) } } } ?: false
            if (stack.phase != Phase.Rest || stack.overlay != null) return@launch
            open(pinned)
        }
        return true
    }

    private fun pushAlbum(row: Row, at: Int, hubTile: Boolean) {
        if (stack.phase != Phase.Rest || stack.overlay != null) return
        val k = albumPushKind(entryOf(stack.top), models, row, hubTile)
        env.poke()
        stack.push(at(MusicPage.Album(row.id), k, at, row), k, at, row.id)
    }

    private fun pushArtist(row: Row, at: Int, thumb: Long) {
        if (stack.phase != Phase.Rest || stack.overlay != null) return
        env.poke()
        stack.push(at(MusicPage.Artist(row.id), MoveKind.T3, at), MoveKind.T3, at, thumb)
    }

    private fun artistRing(am: ArtistModel, i: Int): Boolean {
        if (i == RING_ADD) {
            scope.launch(FailFast) {
                quiet {
                    val p = src?.player ?: return@quiet
                    artistAdd(p, am)?.let { toast.show(now(), it, am.name.orEmpty()) }
                }
            }
            return true
        }
        return playOpen(NpForm.Page) { s ->
            artistPlay(s.player, am, i)
            true
        }
    }

    private fun openSearch(owner: S?) {
        val p = entryOf(owner)?.page
        val from = owner != null && stack.top == owner && (p == MusicPage.Hub || p is MusicPage.Search || p is MusicPage.AlbumCol)
        env.poke()
        if (!from) return stack.cut()
        if (p is MusicPage.Search) {
            stack.cut()
            searchAt = owner
            return
        }
        val e = at(MusicPage.Search(music.searchQuery), MoveKind.T3)
        stack.cutPush(e, MoveKind.T3)
        searchAt = e
    }

    private fun pushCol(letter: Int, pick: Boolean) {
        if (stack.phase != Phase.Rest || stack.overlay != null) return
        val k = colPushKind(pick)
        env.poke()
        stack.push(at(MusicPage.AlbumCol(letter), k), k)
    }

    private fun openQueue() {
        if (stack.phase != Phase.Rest || stack.overlay != null) return
        if (stack.top?.let(::isNp) != true) return
        env.poke()
        stack.push(at(MusicPage.Queue, MoveKind.T3), MoveKind.T3)
    }

    private fun npInfo() {
        val track = np.shown.track ?: return
        openInfo(np.shown.title, SheetDone.Info(track, true, 0L), false)
    }

    override fun back() {
        stack.back(popKind)
    }

    override fun sheet(t: SheetTarget): Boolean = openMusic(t)

    override fun nowPlaying(): Boolean = openNowPlaying()

    override fun album(row: Row, at: Int, hubTile: Boolean) {
        pushAlbum(row, at, hubTile)
    }

    override fun artist(row: Row, at: Int, thumb: Long) {
        pushArtist(row, at, thumb)
    }

    override fun ring(m: ArtistModel, i: Int): Boolean = artistRing(m, i)

    override fun jump(e: MusicEntry) {
        openJump(app.screen(e))
    }

    override fun commit(query: String) {
        music.searchQuery = query
    }

    @Composable
    fun StackClock(pop: (Turn<S>) -> MoveKind) {
        appPop = pop
        PageStackClock(stack, popKind)
        LaunchedEffect(stack) { snapshotFlow { stack.armed }.collect { if (!it) opened = null } }
        LaunchedEffect(npHalfInks, stack) {
            snapshotFlow { np.revision }.collect {
                if (stack.moving && !stack.armed) return@collect
                npHalfInks.forEach { it.prime(SystemClock.uptimeMillis()) }
                npPrimed = np.shown.track
            }
        }
        LaunchedEffect(stack) {
            snapshotFlow { stack.overlay }.collect { o -> if (o != null) toast.opened(o.kind, stack.top?.let(::isNp) == true, now()) }
        }
        LaunchedEffect(volDue, stack.phase) {
            if (!volDue || stack.phase != Phase.Rest) return@LaunchedEffect
            while (homeBusy(now())) withFrameMillis { }
            volDue = false
            if (stack.overlay == null) openVolume(now())
        }
        LaunchedEffect(np, stack) {
            var seen = false
            snapshotFlow { np.shown.track }.collect { track ->
                if (track == null) return@collect
                val first = !seen
                seen = true
                if (first || listOfNotNull(stack.top, stack.turn?.to).any(::isNp)) return@collect
                toast.show(now(), if (np.variant) np.shown.from else np.shown.artist, np.shown.title, track = true)
            }
        }
        LaunchedEffect(searchAt) {
            val sc = searchAt ?: return@LaunchedEffect
            while (stack.moving) withFrameMillis {}
            if (stack.top == sc && stack.overlay == null) hubOf(sc)?.let { h ->
                h.openField()
                h.poke()
            }
            searchAt = null
        }
        LaunchedEffect(colFireAt) {
            val sc = colFireAt ?: return@LaunchedEffect
            withFrameMillis {}
            colFireAt = null
            val h = hubOf(sc) ?: return@LaunchedEffect
            val l = colFire(h, stack.top == sc, stack.phase == Phase.Rest, stack.overlay != null)
            if (l >= 0) pushCol(l, true)
        }
    }

    @Composable
    fun MusicPages(pages: Map<S, Shown>, status: StatusState) {
        val npStatusScale = npHalves.holding(NpPart.Status).g.s
        val npStatusInk = rememberStatusInk(status, npStatusScale, npStatus(npStatusScale))
        val rest = stack.phase == Phase.Rest
        for ((sc, sh) in pages) {
            val e = entryOf(sc) ?: continue
            if (e.page == MusicPage.NowPlaying) {
                key(e.entry) {
                    NowPlayingLayer(
                        np, npGeo, env.covers, np.art, npStatusInk, env.a, env.accent, env.accentOff, env.now,
                        live = rest && stack.top == sc, visible = sh.visible, frozen = sh.frozen,
                        hideFocus = stack.hideFocus, snap = stack.snap,
                        onAct = { act ->
                            when (act) {
                                NpAct.Back -> stack.back(popKind)
                                NpAct.Queue -> openQueue()
                                NpAct.Info -> npInfo()
                                NpAct.Controls -> openVolume(now())
                                else -> scope.launch(FailFast) { quiet { npAct(act, np, src, phone, toast, env.now) } }
                            }
                        },
                        onSeek = { ms -> scope.launch(FailFast) { quiet { src?.player?.seek(ms) } } },
                        split = split,
                    )
                }
                continue
            }
            val pm = models[e.entry]
            val type = types.of(pm) ?: continue
            key(e.entry) {
                type.LayerOf(e, pm, src, PageAt(rest && stack.top == sc, sh.visible, sh.frozen, stack.hideFocus, stack.snap, stack.covered) { stack.moving }, this)
            }
        }
    }

    @Composable
    fun MoveCanvas(painter: Boolean, status: MoveStatus) {
        if (!painter) return
        Canvas(Modifier.pxSize(env.page.widthPx, env.page.heightPx)) {
            val t = stack.clock
            with(movePaint) { drawMove(t, null, status) }
        }
    }

    @Composable
    fun JumpHost(grid: (S) -> JumpGrid?, pick: (S, Int) -> Unit) {
        val owner = jumpOwner
        val jumpHub = hubOf(owner)
        val jumpCol = colOf(owner)
        val g = jumpHub?.jump ?: jumpCol?.jumpGrid ?: owner?.let(grid)
        if (stack.overlay !== jump || g == null) return
        JumpGridOverlay(
            g, jump, env.grid, env.a, env.now,
            onPick = { l ->
                if (jumpHub != null) {
                    hubPicked(jumpHub, l, now())
                } else if (jumpCol != null) {
                    jumpCol.jump(l, now())
                    jumpCol.poke()
                } else if (owner != null) {
                    pick(owner, l)
                }
                stack.close()
            },
            onExit = { stack.close() },
            onSearch = { openSearch(jumpOwner) },
            layer = OVER,
            band = if (env.page.navOn) navGround else null,
        )
    }

    @Composable
    fun MenuOverlayHost() {
        val over = stack.overlay
        if (over == null || !(over.kind == OverKind.Sheet || over.kind == OverKind.Dlg)) return
        MenuOverlay(
            menuHost.menu, over, env.page, env.a, env.now,
            onPick = { i ->
                val closes = menuHost.act(i)
                if (closes) stack.accept()
                closes
            },
            onClose = { stack.close() },
            layer = OVER,
            accent = env.accent,
            accentOff = env.accentOff,
            band = if (env.page.navOn) navGround else null,
        )
    }

    @Composable
    fun ToastHost(hidden: Boolean) {
        val page = env.page
        val npTop = stack.top?.let(::isNp) == true
        ToastStrip(
            toast, page, env.now,
            { toastPlace(stack.overlay?.kind, stack.overlay?.closing == true, npTop) },
            toastSurface(page, page.navOn),
            modifier = if (hidden) Modifier.graphicsLayer { alpha = 0f } else Modifier,
            a = if (hidden) null else env.a,
            layer = OVER,
            onTap = { goNp() },
        )
    }

    @Composable
    fun VolumeHost() {
        if (stack.overlay?.kind != OverKind.Volume) return
        val s = env.np
        val scale by rememberUpdatedState(s)
        var blocks by remember { mutableStateOf(true) }
        val rev = hud.revision
        LaunchedEffect(hud, rev) {
            while (true) {
                val t = withFrameMillis { it }
                blocks = hud.visible(t) && !hud.closing(t)
                if (!blocks) break
                val w = hud.wakeAt(t) ?: return@LaunchedEffect
                delay((w - t).coerceAtLeast(0L))
            }
            if (hud.hiding) stack.touch()
        }
        val solo = remember { Tap({}, {}, {}, solo = true) }
        Box(
            Modifier.offset { IntOffset(s.left - env.page.left, 0) }.pxSize(s.widthPx, s.heightPx).pointerInput(hud) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    fun u(o: Offset) = o.x / scale.ppu to o.y / scale.ppu
                    hud.touch()
                    hud.setStep(volume.stepPct())
                    val (x0, y0) = u(d.position)
                    if (hud.down(now(), x0, y0) == VolumeHud.Act.Pass) {
                        stack.cut()
                        return@awaitEachGesture
                    }
                    d.consume()
                    var ended = false
                    try {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val c = e.changes.firstOrNull { it.id == d.id } ?: break
                            c.consume()
                            val (x, y) = u(c.position)
                            hud.setStep(volume.stepPct())
                            if (!c.pressed) {
                                ended = true
                                volAct(hud.up(now(), x, y))
                                break
                            }
                            volAct(hud.move(now(), x, y, volume.percent(), volPos()))
                        }
                    } finally {
                        if (!ended) hud.cancel(now())
                    }
                }
            },
        ) {
            VolumeLayer(hud, s)
            if (blocks) Box(Modifier.pxAt(Edges(0, 0, s.widthPx, px(s.h - Tok.gesture_bottom, s.ppu))).tapTarget(env.a, solo, OVER + 1))
        }
    }
}

@Composable
fun <S : Any> rememberMusicHost(
    app: MusicApp<S>,
    music: MusicModels,
    models: PageModels,
    env: MusicEnv,
    src: MusicSource?,
    split: NpSplit?,
    volume: VolumeLevel,
    toast: Toast,
    jump: Overlay,
    menuHost: MenuHost,
    movePaint: MovePaint,
): MusicHost<S> {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val np = music.np
    val npGeo = remember(env.np) { NpLayout(env.np) }
    val npHalves = remember(npGeo, split) { split?.halves ?: listOf(npFlat(npGeo)) }
    val phone by app.phone.collectAsState()
    val feed = NowPlayingFeed(np, npHalves.holding(NpPart.Art).g, src, phone) { SystemClock.uptimeMillis() }
    val npHalfInks = remember(npHalves, env.measurer, env.fonts, env.density, env.covers) { npHalves.map { NpInks(NpMeasure(it.g, env.measurer, env.fonts, env.density, env.covers), np) } }
    val types = rememberMusicTypes(env, app::homeItem)
    val navGround = remember(env.bands, env.accent) { NavGround(env.bands, env.accent) }
    val host = remember { MusicHost(music, models, toast, jump, menuHost, movePaint, scope, app, env, src, volume, types, npGeo, npHalves, npHalfInks, navGround, split, view) }
    host.update(app, env, src, volume, types, phone, feed, npGeo, npHalves, npHalfInks, navGround, split, view)
    SideEffect { host.applied() }
    return host
}
