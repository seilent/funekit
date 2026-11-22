package net.seilent.funekit.screens.host

import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.seilent.funekit.Case
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Disconnected
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.api.Kind as ApiKind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.Refused
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
import net.seilent.funekit.kit.rememberChrome
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
import net.seilent.funekit.kit.touch.BlockBelow
import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.kit.touch.Pan
import net.seilent.funekit.kit.touch.Swipe
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.px
import net.seilent.funekit.screens.eq.EqActs
import net.seilent.funekit.screens.eq.EqId
import net.seilent.funekit.screens.eq.EqLayer
import net.seilent.funekit.screens.eq.EqLine
import net.seilent.funekit.screens.eq.EqModel
import net.seilent.funekit.screens.eq.EqPage
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
import net.seilent.funekit.screens.music.hub.LibraryMark
import net.seilent.funekit.screens.music.hub.hubPickAtDrop
import net.seilent.funekit.screens.music.hub.hubPicked
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.music.nowplaying.AdjustLayer
import net.seilent.funekit.screens.music.nowplaying.AdjustSource
import net.seilent.funekit.screens.music.nowplaying.CoverBlur
import net.seilent.funekit.screens.music.nowplaying.NowPlayingLayer
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpAct
import net.seilent.funekit.screens.music.nowplaying.NpBackdrop
import net.seilent.funekit.screens.music.nowplaying.NpFeed
import net.seilent.funekit.screens.music.nowplaying.NpHalf
import net.seilent.funekit.screens.music.nowplaying.NpInks
import net.seilent.funekit.screens.music.nowplaying.NpMeasure
import net.seilent.funekit.screens.music.nowplaying.NpPaint
import net.seilent.funekit.screens.music.nowplaying.NpPart
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import net.seilent.funekit.screens.music.nowplaying.NpSplit
import net.seilent.funekit.screens.music.nowplaying.OffsetSheet
import net.seilent.funekit.screens.music.nowplaying.holding
import net.seilent.funekit.screens.music.nowplaying.npFlat
import net.seilent.funekit.screens.music.nowplaying.npNanosAt
import net.seilent.funekit.screens.music.overlay.ADD_TO_PLAYLIST
import net.seilent.funekit.screens.music.overlay.DELETE
import net.seilent.funekit.screens.music.overlay.DELETE_ASK
import net.seilent.funekit.screens.music.overlay.DELETE_PLAYLIST
import net.seilent.funekit.screens.music.overlay.DeleteDone
import net.seilent.funekit.screens.music.overlay.GO_TO_ALBUM
import net.seilent.funekit.screens.music.overlay.GO_TO_ARTIST
import net.seilent.funekit.screens.music.overlay.INFO
import net.seilent.funekit.screens.music.overlay.KEEP_PLAYLIST
import net.seilent.funekit.screens.music.overlay.OPEN_PLAYLIST
import net.seilent.funekit.screens.music.overlay.PLAY
import net.seilent.funekit.screens.music.overlay.SHUFFLE
import net.seilent.funekit.screens.music.overlay.SheetDelete
import net.seilent.funekit.screens.music.overlay.SheetDone
import net.seilent.funekit.screens.music.overlay.SheetExtra
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.artistRows
import net.seilent.funekit.screens.music.overlay.confirmDelete
import net.seilent.funekit.screens.music.overlay.deleteAsk
import net.seilent.funekit.screens.music.overlay.deleteToast
import net.seilent.funekit.screens.music.overlay.infoLines
import net.seilent.funekit.screens.music.overlay.infoMax64
import net.seilent.funekit.screens.music.overlay.infoOf
import net.seilent.funekit.screens.music.overlay.picker
import net.seilent.funekit.screens.music.overlay.runEntry
import net.seilent.funekit.screens.music.overlay.runPick
import net.seilent.funekit.screens.music.overlay.runSheet
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.overlay.sheetWords
import net.seilent.funekit.screens.music.pivotBy
import net.seilent.funekit.screens.music.queue.QueueModel
import net.seilent.funekit.screens.settings.FieldPress
import net.seilent.funekit.screens.settings.FieldSheet
import net.seilent.funekit.screens.settings.HeaderLook
import net.seilent.funekit.screens.settings.RangeAdjust
import net.seilent.funekit.screens.settings.RowAdjust
import net.seilent.funekit.screens.settings.RowPivot
import net.seilent.funekit.screens.settings.RowsPage
import net.seilent.funekit.screens.settings.SettingKind
import net.seilent.funekit.screens.settings.SettingPivot
import net.seilent.funekit.screens.settings.SettingRow
import net.seilent.funekit.screens.settings.SettingSheet
import net.seilent.funekit.screens.settings.SettingsId
import net.seilent.funekit.screens.settings.SettingsLayer
import net.seilent.funekit.screens.settings.SettingsModel
import net.seilent.funekit.screens.settings.SettingsPage
import net.seilent.funekit.screens.settings.settingsChrome
import net.seilent.funekit.screens.settings.valueSheet

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

private suspend fun deleteDone(act: suspend () -> DeleteDone): DeleteDone = try {
    act()
} catch (_: NoCard) {
    DeleteDone.NoCard
} catch (_: Refused) {
    DeleteDone.Refused
} catch (_: Expired) {
    DeleteDone.Refused
} catch (_: Busy) {
    DeleteDone.Refused
}

private class OwedExit(val album: Long, val artist: Long?)

private suspend fun exitRead(e: OwedExit, s: MusicSource): List<Long>? = s.viewed { v ->
    val listed = if (s.library.caps.indexOf(Listing.Albums)) v.indexOf(Listing.Albums, ApiKind.Album, e.album) != null else v.page(Listing.AlbumTracks(e.album), 0, 0).total > 0
    if (listed) null else listOfNotNull(e.artist?.takeIf { v.page(Listing.ArtistAlbums(it), 0, 0).total == 0 })
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
    private val menuOvers = ArrayList<Pair<Overlay, Long>>()
    private val libraryOvers = ArrayList<Overlay>()
    private val exits = ArrayList<OwedExit>()
    private var exitEpoch = 0
    private val backdrop = NpBackdrop()
    private var fieldRun by mutableStateOf<FieldRun?>(null)
    private val volSets = mutableListOf<Int>()
    private var settingsRead: Map<SettingsId, List<SettingPivot>> = emptyMap()
    private var eqRead: Map<EqId, List<RowPivot<EqLine>>> = emptyMap()
    private val eqActs = HashMap<EqModel, PaneActs>()
    private val eqSheets = ArrayList<EqSheet>()
    private var menuOpens = 0
    private var edit: Pair<Overlay, RowsPage<*>>? = null
    private var editGone: Overlay? = null
    private var liftOver: Overlay? = null
    private val rangeHud = AdjustHud()
    private var range by mutableStateOf<Pair<Overlay, RowAdjust>?>(null)
    private val rangeSource = object : AdjustSource {
        override val hud: AdjustHud get() = rangeHud

        override fun tap(h: AdjustHit) {
            range?.second?.tap(h)
        }

        override fun back() {
            range?.second?.back()
        }

        override fun number(side: Int): Boolean = range?.second?.number(side) == true
    }
    private var liftModel: RowsPage<*>? = null
    private val infoRole = Role(Roles.dialogItem.size, Roles.dialogItem.weight, Case.Mixed, Roles.dialogItem.alpha, Roles.dialogItem.tracking)
    val popKind: (Turn<S>) -> MoveKind = { t -> pop(t) ?: appPop(t) }

    val chromes: MusicChromes get() = types.chromes

    val settingsChromes: Map<SettingsId, Chrome> get() = types.settings.chromes

    val eqChromes: Map<EqId, Chrome> get() = types.eq.chromes

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
        settingsRead: Map<SettingsId, List<SettingPivot>>,
        eqRead: Map<EqId, List<RowPivot<EqLine>>>,
    ) {
        this.app = app
        this.env = env
        if (src !== this.src) dropExits()
        this.src = src
        this.volume = volume
        this.types = types
        this.phone = phone
        np.phoneShuffle = phone?.shuffle != null
        np.phoneRepeat = phone?.repeat != null
        this.feed = feed
        this.npGeo = npGeo
        this.npHalves = npHalves
        this.npHalfInks = npHalfInks
        this.navGround = navGround
        this.split = split
        this.view = view
        this.settingsRead = settingsRead
        this.eqRead = eqRead
        hud.board = env.np.board
        hud.w = env.np.w
        hud.h = env.np.h
        np.lyricsView = app.lyricsView
        np.lyrics.phoneLyrics = app.phoneLyrics != null
    }

    fun applied() {
        types.retain(models)
        for (e in models.entries) {
            val m = models[e] as? SettingsModel ?: continue
            m.pivots = settingsRead.listed(m.id)
        }
        eqActs.keys.retainAll { models[it.entry] === it }
        for (e in models.entries) {
            val m = models[e] as? EqModel ?: continue
            val was = m.rows(m.pivot)
            m.pivots = eqRead.listed(m.id)
            wire(m)
            m.leave()
            val now = m.rows(m.pivot)
            if (now != was && now.size == was.size) m.poke()
        }
        editEnd()
        rangeLanded()
        val key = scaleKey(env.page)
        if (scaleAt != key) {
            scaleAt = key
            stack.touch()
            stack.overlay?.takeIf { it.kind == OverKind.Sheet || it.kind == OverKind.Dlg }?.let { frameMenu(menuHost.menu, env.page, menuHost.lift) { liftRow(stack.top) } }
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

    private fun pageEntry(s: S?): Long? = s?.let { entryOf(it)?.entry ?: app.settings(it)?.entry ?: app.eq(it)?.entry }

    private fun modelOf(s: S?): PageModel? = pageEntry(s)?.let { models[it] }

    private fun settingsOf(s: S?): SettingsModel? = s?.let(app::settings)?.let { models[it.entry] as? SettingsModel }

    private fun eqOf(s: S?): EqModel? = s?.let(app::eq)?.let { models[it.entry] as? EqModel }

    private fun rowsOf(s: S?): RowsPage<*>? = settingsOf(s) ?: eqOf(s)

    private fun <T> Map<SettingsId, T>.listed(id: SettingsId): T = requireNotNull(this[id]) { "a settings model takes the rows of a listed page" }

    private fun <T> Map<EqId, T>.listed(id: EqId): T = requireNotNull(this[id]) { "an eq model takes the rows of a listed page" }

    private fun settingsPage(id: SettingsId): SettingsPage = requireNotNull(app.settingsPages.firstOrNull { it.id == id }) { "a settings screen names a listed page" }

    private fun eqPage(id: EqId): EqPage = requireNotNull(app.eqPages.firstOrNull { it.id == id }) { "an eq screen names a listed page" }

    private fun liftRow(top: S?): Int? {
        val m = rowsOf(top) ?: return app.liftRow(top)
        return m.takeIf { it.rows(it.pivot).isNotEmpty() }?.let { it.heights(it.pivot)[it.focusRow(it.pivot)] }
    }

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

    private fun pop(t: Turn<S>): MoveKind? {
        t.from?.let { s -> app.settings(s)?.id?.header ?: app.eq(s)?.id?.header }?.let { h -> return if (t.to != null) MoveKind.T4 else if (app.homeHas(h)) MoveKind.T4h else MoveKind.T4n }
        return musicPopKind(t, app::music, models, app::homeHas)?.also { if (it == MoveKind.T8) movePaint.npForm = npCloseForm(t.to == null, app.homeFront()) }
    }

    fun ready(s: S): Boolean? {
        val e = entryOf(s) ?: return pageEntry(s)?.let { models[it]?.ready ?: true }
        return if (e.page == MusicPage.NowPlaying) npReady() else models[e.entry]?.ready ?: true
    }

    fun enter(s: S) {
        val e = entryOf(s)
        if (e != null) {
            types.enter(models.enter(e.entry, e.page), e)
            return
        }
        val sc = app.settings(s) ?: return enterEq(s)
        val page = settingsPage(sc.id)
        val was = models[sc.entry]
        val m = models.enter(sc.entry, sc.id)
        require(m is SettingsModel) { "a settings page takes a settings model, pageModels builds one" }
        m.pivots = page.rows.pivots.value
        if (m !== was) sc.pivot?.let { m.openAt(it) }
    }

    private fun enterEq(s: S) {
        val sc = app.eq(s) ?: return
        val page = eqPage(sc.id)
        val was = models[sc.entry]
        val m = models.enter(sc.entry, sc.id)
        require(m is EqModel) { "an eq page takes an eq model, pageModels builds one" }
        m.pivots = page.pane.pivots.value
        wire(m)
        if (m !== was) {
            sc.pivot?.let { m.openAt(it) }
            page.pane.opened()
        }
    }

    private fun wire(m: EqModel) {
        val pane = eqPage(m.id).pane
        m.left = pane::left
        m.moving = pane::moving
    }

    fun drop(s: S) {
        if (fieldRun?.owner == s) fieldEnd()
        pageEntry(s)?.let { models.drop(it) }
    }

    fun kind(s: S): PageKind? = pageEntry(s)?.let { models[it]?.kind ?: PageKind.Plain }

    fun pageBack(s: S): Boolean = entryOf(s) != null && ((isNp(s) && npBack()) || types.back(modelOf(s)))

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
        if (edit?.second?.let { it === modelOf(t.from) } == true && rangeLeft()) rangeHud.dismiss()
    }

    fun swapped(s: S, at: Long) = pageThumbAtSwap(s, at, ::thumbOf)

    fun dropped(s: S, at: Long) {
        if (hubOf(s)?.colPick?.let { it >= 0 } == true) colFireAt = s
        hubPickAtDrop(hubOf(s), at)
        swapped(s, at)
    }

    fun side(s: S): PagePaint? {
        if (entryOf(s)?.page == MusicPage.NowPlaying) return NpPaint(npGeo, np, npHalves.zip(npHalfInks), { np.art }, env.accent) { npHides }
        return types.side(modelOf(s))
    }

    fun pivot(s: S?): Pair<PivotPage, Chrome>? = types.pivot(modelOf(s))

    fun heldTarget(from: S?, o: Offset): Boolean? {
        val e = entryOf(from)
        if (e == null && rowsOf(from) == null) return null
        if (e?.page == MusicPage.NowPlaying) return npHalves.holding(NpPart.Back).let { h -> h.holds(o.x, o.y) && h.g.backHit(h.ux(o.x), h.uy(o.y)) }
        val b: Edges? = types.chrome(modelOf(from))?.headerBox()
        return b != null && o.x >= b.x0 && o.x < b.x1 && o.y >= b.y0 && o.y < b.y1
    }

    fun warm() {
        hubOf(stack.top)?.jumpPick = -1
        hubOf(stack.top)?.colPick = -1
    }

    fun down() {
        hubOf(stack.top)?.stopAim()
        rowsOf(stack.top)?.stopAim()
    }

    fun up(t: Long, upX: Float?) {
        if (np.seeking) np.seekUp(npSeekHalf.g, upX?.let { npSeekHalf.ux(it) } ?: np.seekX)?.let { ms -> scope.launch(FailFast) { quiet { src?.player?.seek(ms) } } }
        np.gestureUp()
        hubOf(stack.top)?.let {
            it.fade.release(t)
            it.jump.fade.release(t)
        }
        queueOf(stack.top)?.fade?.release(t)
        rowsOf(stack.top)?.fade?.release(t)
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
        hold: ((Int) -> Boolean)? = null,
        scrim: () -> Boolean = { false },
        setup: (Menu) -> Unit = {},
    ): Overlay {
        val menu = menuHost.menu
        if (stack.overlay != null) stack.accept()
        menu.open(form, title, items, now(), readOnly, mixedRow, lead = lead, confirm = confirm)
        setup(menu)
        menuHost.lift = lift
        frameMenu(menu, env.page, lift) { liftRow(stack.top) }
        menuHost.act = act
        menuHost.hold = hold
        menuHost.scrim = scrim
        val o = Overlay(if (form == MenuForm.Sheet) OverKind.Sheet else OverKind.Dlg)
        val at = stack.clock
        menuOvers.retainAll { it.first === stack.overlay || it.second == at }
        menuOvers += o to at
        menuOpens++
        stack.open(o)
        return o
    }

    fun openSheet(sheet: SettingSheet, pick: (Int) -> Boolean): Overlay {
        val o = openMenu(sheet.form, sheet.title, sheet.items, sheet.readOnly, sheet.mixedRow, sheet.lead, sheet.confirm, pick, sheet.lift) { menu ->
            if (sheet.focus >= 0) menu.value(sheet.focus, sheet.mark, sheet.wrap, now())
            if (sheet.values.isNotEmpty() || sheet.mixed.isNotEmpty()) menu.cells(sheet.values, sheet.mixed, -1)
            if (sheet.swatches.isNotEmpty()) menu.swatches(sheet.swatches)
        }
        val m = rowsOf(stack.top)
        m?.edit(sheet.edit?.takeIf { sheet.focus >= 0 }, sheet.lift)
        edit = m?.let { o to it }
        return o
    }

    fun closing(o: Overlay) {
        if (o === range?.first) rangeLeft()
        if (o !== liftOver) return
        liftModel?.drop(minOf(o.dur().toFloat(), Tok.motion_scroll))
        liftModel?.poke()
    }

    internal fun settingsTap(m: SettingsModel, row: SettingRow) {
        val rows = settingsPage(m.id).rows
        if (rows.open(row)) return
        if (row.kind == SettingKind.Action) {
            rows.action(row.key)
            return
        }
        if (!row.editable) return
        if (row.kind == SettingKind.Range && row.swatches.isEmpty()) {
            openAdjust(m, RangeAdjust(row, rows, m, rangeHud, row.level, ::rangeEnd, scope))
            return
        }
        if (row.words.isEmpty()) return
        openSheet(valueSheet(row)) { i ->
            val n = row.values.size
            val v = row.values.getOrNull(if (row.kind == SettingKind.Steps) n - 1 - i else i) ?: return@openSheet true
            if (!m.holds(row.key)) return@openSheet true
            val opens = menuOpens
            if (row.kind == SettingKind.Draft) rows.draft(row.key, v) else scope.launch(FailFast, CoroutineStart.UNDISPATCHED) { rows.set(row.key, v) }
            menuOpens == opens
        }
    }

    private fun editEnd() {
        val (o, m) = edit ?: return
        val k = m.editing ?: return
        if (m.holds(k)) return
        edit = null
        if (o.kind == OverKind.Adjust) {
            m.endEdit()
            range?.second?.finish()
            range = null
            stack.cut()
        } else if (stack.overlay === o) {
            stack.close()
        } else if (menuOvers.any { it.first === o }) {
            editGone = o
        }
    }

    private fun openAdjust(m: RowsPage<*>, src: RowAdjust): Boolean {
        require(src.hud === rangeHud) { "a row adjust shows on the host's hud" }
        if (!src.open(now())) return false
        val o = Overlay(OverKind.Adjust, src.hud)
        stack.open(o)
        m.edit(src.key, false)
        edit = o to m
        range = o to src
        return true
    }

    private fun rangeEnd(typed: Boolean) {
        val (o, src) = range ?: return
        src.finish()
        edit?.takeIf { it.first === o }?.let { (_, m) ->
            m.endEdit()
            edit = null
        }
        if (typed) {
            range = null
            stack.cut()
        } else {
            stack.close()
        }
    }

    private fun rangeLeft(): Boolean {
        val (o, src) = range ?: return false
        val (eo, m) = edit ?: return false
        if (eo !== o) return false
        src.revert()
        src.finish()
        m.endEdit()
        edit = null
        return true
    }

    private fun rangeLanded() {
        val (o, src) = range ?: return
        val (eo, _) = edit ?: return
        if (eo !== o) return
        src.landed()
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

    fun resetNp(under: List<S>) {
        require(under.none(::isNp)) { "a reset under now playing takes no now playing page" }
        val t = now()
        np.sync(np.shown, t)
        np.resetFocus(t)
        env.poke()
        stack.reset(under + app.screen(musicEntry(stack.entry(), MusicPage.NowPlaying, MoveKind.T7, entryOf(under.lastOrNull()), models)))
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
            val e = pageEntry(top)
            if (e != null) models[e]?.lists?.forEach { it.cancel() } else app.cancel(top)
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
                volSets += n
                scope.launch(FailFast) {
                    val done = try {
                        quiet { volume.set(n) }
                    } finally {
                        volSets.remove(n)
                    }
                    done ?: return@launch
                    val p = volume.percent()
                    hud.took(p)
                    if (p != hud.percent) openVolume(now())
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
            val ok = quiet { play(s) } == true && s.player.state.value.let { it.track != null || it.service != null }
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
                libraryOver(openMenu(MenuForm.Sheet, title, lines.lines, true, lines.pathRow, 0, false, { true }))
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

    private fun pickMusic(t: SheetTarget, words: List<String>, i: Int, sheetSrc: MusicSource, del: SheetDelete?, extras: List<SheetExtra>): Boolean {
        val word = words.getOrNull(i) ?: return true
        val kit = words.size - extras.size
        if (i >= kit) {
            val sheet = extras[i - kit].act(now()) ?: return true
            val over = stack.overlay
            stack.swapMenu(over) { at ->
                menuHost.menu.open(MenuForm.Sheet, sheet.title, sheet.items, at)
                menuHost.act = sheet.pick
            }
            return false
        }
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
        if (word == DELETE) {
            val d = del ?: return true
            val a = deleteAsk(t, d.songs)
            libraryOver(openMenu(MenuForm.Dlg, a.title, a.items, false, 0, a.lead, true, { k ->
                if (a.items[k] == a.yes) deleteYes(t, d, sheetSrc)
                true
            }) { menu -> menu.focus.set(a.focus, a.items.size, now(), false) })
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
            libraryOver(openMenu(MenuForm.Dlg, DELETE_ASK, ask, false, -1, 0, true, { k ->
                if (ask[k] == DELETE_PLAYLIST) scope.launch(FailFast) { quiet { confirmDelete(DELETE_PLAYLIST, t.id, s) } }
                true
            }) { menu -> menu.focus.set(ask.indexOf(KEEP_PLAYLIST), ask.size, now(), false) })
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

    private fun libraryOver(o: Overlay): Overlay {
        libraryOvers.retainAll { m -> menuOvers.any { it.first === m } }
        libraryOvers += o
        return o
    }

    private fun deleteYes(t: SheetTarget, d: SheetDelete, s: MusicSource) {
        if (src !== s || s.library.generation.value != t.generation) {
            deleteToast(t, DeleteDone.Refused)?.let { toast.show(now(), it.line, it.title) }
            return
        }
        scope.launch(FailFast) {
            val done = deleteDone { d.delete(now()) }
            deleteToast(t, done)?.let { toast.show(now(), it.line, it.title) }
            if (done == DeleteDone.Done && t.kind == SheetKind.Album) owe(t.id, d.artist, s)
        }
    }

    private fun owe(album: Long, artist: Long?, s: MusicSource) {
        if (src !== s) return
        exits += OwedExit(album, artist)
        readExits(s)
    }

    private fun readExits(s: MusicSource) {
        if (exits.isEmpty()) return
        val epoch = exitEpoch
        val owed = exits.toList()
        scope.launch(FailFast) {
            val due = ArrayList<Pair<OwedExit, List<Long>>>()
            for (e in owed) {
                val leaves = try {
                    exitRead(e, s)
                } catch (x: Busy) {
                    app.log("album ${e.album} exit read: ${x.message}")
                    continue
                } catch (x: Disconnected) {
                    app.log("album ${e.album} exit read: ${x.message}")
                    continue
                } ?: continue
                due += e to leaves
            }
            if (exitEpoch != epoch) return@launch
            val ready = due.filter { it.first in exits }
            if (ready.isEmpty()) return@launch
            exits.removeAll(ready.map { it.first }.toSet())
            val pages = stack.pages.toList()
            val leaving = deleteExits(pages.map { entryOf(it)?.page }, ready.map { it.first.album }.toSet(), ready.flatMap { it.second }.toSet()).map { pages[it] }
            if (leaving.isNotEmpty()) stack.leave({ p -> exitEpoch == epoch && leaving.any { it === p } }, popKind)
        }
    }

    private fun dropExits() {
        exits.clear()
        exitEpoch++
    }

    fun closeLibrarySheets(newCard: Boolean = false) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "MusicHost.closeLibrarySheets runs on the main thread only" }
        warm()
        val o = stack.overlay
        if (o != null && (o === jump || libraryOvers.any { it === o })) stack.cutOverlay(o)
        searchAt = null
        val top = stack.top
        val h = hubOf(top)
        if (top != null && h != null && entryOf(top)?.page is MusicPage.Search && h.search?.open == true) {
            val empty = h.closeField()
            h.poke()
            if (empty) stack.leave({ it === top }, popKind)
        }
        if (newCard) dropExits()
    }

    fun openMusic(t: SheetTarget): Boolean {
        val s = src ?: return false
        if (stack.phase != Phase.Rest || stack.overlay != null || sheetItems(t, false).isEmpty()) return false
        fun open(pinned: Boolean) {
            val del = if (t.kind == SheetKind.Song || t.kind == SheetKind.Album) app.sheetDelete(t) else null
            val extras = app.sheetExtras(t)
            val words = sheetWords(t, pinned, del != null, extras)
            libraryOver(openMenu(MenuForm.Sheet, t.title, words, false, -1, 0, false, { i -> pickMusic(t, words, i, s, del, extras) }))
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
        LaunchedEffect(stack) {
            snapshotFlow { stack.overlay }.collect { o ->
                val r = fieldRun ?: return@collect
                if (o === r.over || r.entry) return@collect
                fieldRun = null
                r.sheet.dismiss()
            }
        }
        LaunchedEffect(stack) {
            snapshotFlow { np.lyrics.revision to (fieldRun?.ends?.invoke() == true) }.collect { (_, gone) -> if (gone) fieldEnd() }
        }
        LaunchedEffect(stack) {
            var seen: FieldRun? = null
            snapshotFlow { fieldRun?.let { Triple(it, it.sheet.values(), it.sheet.field()) } }.collect { t ->
                val r = t?.first
                if (r !== seen) {
                    seen = r
                    return@collect
                }
                if (r == null || stack.overlay !== r.over) return@collect
                if (r.sheet.field() < 0 && r.sheet.hud.shown) r.sheet.hud.dismiss()
                fieldSync(r)
            }
        }
        LaunchedEffect(stack) {
            snapshotFlow { stack.overlay to stack.top.takeIf { stack.overlay == null && fieldRun == null } }.collect { (o, top) ->
                for (e in eqSheets.toList()) {
                    if (e.over === o) {
                        e.seen = true
                    } else if (e.seen || o != null) {
                        eqSheets -= e
                        if (!e.picked) e.closed()
                    }
                }
                val m = eqOf(top) ?: return@collect
                types.eq.panes.getValue(m.id).rest(now())
                m.poke()
            }
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
        LaunchedEffect(src) {
            val s = src ?: return@LaunchedEffect
            combine(s.library.generation, s.library.state, s.library.scan) { g, state, scan -> g to LibraryMark(state, scan) }.distinctUntilChanged().collect { (_, mark) ->
                if (mark.state.card != Card.Present) dropExits() else readExits(s)
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
        LaunchedEffect(stack) {
            snapshotFlow { stack.overlay }.collect { o ->
                val r = range
                if (r != null && r.first !== o) {
                    rangeLeft()
                    range = null
                }
                if (o == null || o !== editGone) return@collect
                editGone = null
                stack.close()
            }
        }
        LaunchedEffect(stack) {
            snapshotFlow { Triple(stack.overlay, menuHost.menu.panelTop, stack.top) }.collect { (o, panel, top) ->
                val sm = rowsOf(top)
                if (o == null) {
                    if (liftOver != null) {
                        liftModel?.drop(Tok.motion_scroll)
                        liftModel?.endEdit()
                        liftModel?.poke()
                    }
                    liftOver = null
                    liftModel = null
                } else if (sm != null && o !== liftOver && panel >= 0) {
                    liftOver = o
                    liftModel = sm
                    val pg = env.page
                    if (sm.lift(px(pg.board.page_contentTop, pg.ppu), panel - px(Tok.gutter, pg.ppu), menuHost.menu.kept) && !o.closing) o.open(now() + Tok.motion_scroll.toLong())
                    sm.poke()
                }
            }
        }
    }

    @Composable
    fun SettingsPages(pages: Map<S, Shown>) {
        val rest = stack.phase == Phase.Rest
        for ((sc, sh) in pages) {
            val m = settingsOf(sc) ?: continue
            val type = types.settings
            key(m.entry) {
                SettingsLayer(
                    m, type.drawOf(m), type.chromes.listed(m.id), env.a, env.bands, env.now,
                    live = rest && stack.top == sc, visible = sh.visible, frozen = sh.frozen, snap = stack.snap,
                    onBack = { stack.back(popKind) },
                    onTap = { r -> settingsTap(m, r) },
                    leavesLit = { stack.moving },
                )
            }
        }
    }

    @Composable
    fun EqPages(pages: Map<S, Shown>) {
        val rest = stack.phase == Phase.Rest
        for ((sc, sh) in pages) {
            val m = eqOf(sc) ?: continue
            val type = types.eq
            key(m.entry) {
                EqLayer(
                    m, type.drawOf(m), type.chromes.getValue(m.id), env.a, env.bands, env.now,
                    live = rest && stack.top == sc, visible = sh.visible, frozen = sh.frozen, snap = stack.snap,
                    onBack = { stack.back(popKind) },
                    onTap = { line -> eqTap(m, line) },
                    onHold = { line -> type.panes.getValue(m.id).hold(m.names[m.pivot], line, actsOf(m)) },
                    leavesLit = { stack.moving },
                )
            }
        }
    }

    internal fun eqTap(m: EqModel, line: EqLine) = types.eq.panes.getValue(m.id).tap(m.names[m.pivot], line, actsOf(m))

    internal fun actsOf(m: EqModel): EqActs = eqActs.getOrPut(m) { PaneActs(m) }

    private fun eqLive(m: EqModel): Boolean = eqOf(stack.top) === m && stack.phase == Phase.Rest && stack.overlay == null && !stack.moving && fieldRun?.entry != true

    private inner class PaneActs(private val m: EqModel) : EqActs {
        private var own: Overlay? = null
        private var run: FieldRun? = null

        override fun now(): Long = this@MusicHost.now()

        override fun sheet(sheet: SettingSheet, pick: (Int) -> Boolean, closed: () -> Unit): Boolean {
            if (!eqLive(m)) return false
            val e = EqSheet(closed)
            e.over = openSheet(sheet) { i -> pick(i).also { if (it) e.picked = true } }
            eqSheets += e
            own = e.over
            run = null
            return true
        }

        override fun fields(sheet: FieldSheet): Boolean {
            if (!eqLive(m) || !openFields(sheet)) return false
            run = fieldRun
            own = null
            return true
        }

        override fun adjust(make: (AdjustHud, (Boolean) -> Unit) -> RowAdjust): Boolean {
            if (!eqLive(m)) return false
            return openAdjust(m, make(rangeHud, ::rangeEnd))
        }

        override fun focus(key: String) {
            m.focusKey(key)
            m.poke()
        }

        override fun toast(line1: String, line2: String) {
            toast.show(now(), line1, line2)
        }

        override fun close() {
            val o = run?.takeIf { it === fieldRun }?.over ?: own
            if (o != null && o === stack.overlay) stack.close()
        }

        override fun launch(block: suspend () -> Unit) {
            scope.launch(FailFast, CoroutineStart.UNDISPATCHED) { block() }
        }
    }

    @Composable
    fun MusicPages(pages: Map<S, Shown>, status: StatusState) {
        NpBackdropFeed()
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
                        onAct = ::npTap,
                        onSeek = { ms -> scope.launch(FailFast) { quiet { src?.player?.seek(ms) } } },
                        split = split,
                        backdrop = backdrop,
                    )
                }
                continue
            }
            val pm = models[e.entry]
            val type = types.musicOf(pm) ?: continue
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
        val r = range
        if (r != null && r.first === over) {
            AdjustOverlayHost(rangeSource)
            return
        }
        if (over == null || menuOvers.none { it.first === over }) return
        val f = fieldRun?.takeIf { it.over === over }
        if (f != null) {
            val src = remember(f) {
                object : AdjustSource {
                    override val hud = f.sheet.hud

                    override fun tap(h: AdjustHit) = fieldTap(f, h)

                    override fun back() = fieldBack(f)

                    override fun number(side: Int): Boolean = fieldNumber(f)
                }
            }
            if (f.sheet.hud.shown) {
                AdjustOverlayHost(src)
                return
            }
            val want = f.want
            LaunchedEffect(over, f, want) {
                if (!want) return@LaunchedEffect
                while (!over.closing && over.moving(now())) withFrameMillis { }
                if (over.closing || fieldRun !== f) return@LaunchedEffect
                f.want = false
                f.sheet.adjust(now())
            }
            BackHandler(f.live && f.choice == null) { fieldDrop(f) }
            BackHandler(f.choice != null) { choiceBack(f) }
        }
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
            onHold = menuHost.hold,
            onScrim = menuHost.scrim,
        )
    }

    @Composable
    private fun AdjustOverlayHost(src: AdjustSource) {
        val s = env.page
        val at = remember(src.hud) { AdjustDown() }
        BlockBelow(env.a, OVER)
        BackHandler { if (!src.hud.hiding) src.back() }
        Box(Modifier.pxSize(s.widthPx, s.heightPx)) {
            AdjustLayer(src.hud, s, env.covers, env.now)
            val tap = Tap(
                onPress = { at.fresh = true },
                onCancel = { at.hit = AdjustHit.None },
                onTap = { if (!src.hud.hiding) src.tap(at.hit) },
                onLong = { at.number >= 0 && !src.hud.hiding && src.number(at.number) },
                onAt = { o ->
                    if (at.fresh) {
                        at.fresh = false
                        at.hit = src.hud.hit(o.x, o.y, s)
                        at.number = if (at.hit == AdjustHit.None) src.hud.numberAt(o.x, o.y) else -1
                    }
                },
            )
            Box(Modifier.pxSize(s.widthPx, s.heightPx).tapTarget(env.a, tap, OVER))
        }
    }

    @Composable
    private fun NpBackdropFeed() {
        val pic by app.backdrop.collectAsState()
        val shown = np.lyricsView && np.lyrics.shown(np.variant)
        val art = np.art?.takeIf { !np.artPhone }
        val gs = npGeo.s
        var cover by remember { mutableStateOf<Blurred?>(null) }
        LaunchedEffect(art, shown, gs) {
            if (!shown || art == null || cover?.of(art, gs) == true) return@LaunchedEffect
            val img = withContext(np.workContext) { CoverBlur.image(art, gs.widthPx, gs.heightPx, gs) }
            cover = Blurred(art, gs, img?.let { NpPicture(it, whole = true) })
        }
        val done = cover?.takeIf { it.of(art, gs) }
        val wait = shown && art != null && done == null
        val want = (if (shown) done?.picture else null) ?: pic
        LaunchedEffect(want, wait) { if (!wait) backdrop.set(want, now()) }
    }

    fun npBack(): Boolean {
        if (!np.lyricsView) return false
        val l = np.lyrics
        val t = now()
        if (l.fading(t)) {
            if (l.on(np.variant)) {
                l.reverse(t, np.variant)
                syncPhoneMode()
                return true
            }
            l.land(np.variant)
        }
        if (!l.shown(np.variant)) return false
        l.toggle(t, np.variant)
        syncPhoneMode()
        return true
    }

    private fun syncPhoneMode() {
        val pl = app.phoneLyrics ?: return
        if (!np.variant) return
        pl.setMode(np.lyrics.phoneMode)
        np.lyrics.echo(pl.mode.value, true)
    }

    private fun lyricsToggle() {
        val l = np.lyrics
        val t = now()
        if (l.fading(t)) l.reverse(t, np.variant) else l.toggle(t, np.variant)
        syncPhoneMode()
    }

    fun showLyrics(on: Boolean) {
        val l = np.lyrics
        if (!np.lyricsView || l.mode == on) return
        if (np.variant && l.phoneLyrics) l.turn(on, false) else lyricsToggle()
    }

    val fieldSheet: FieldSheet? get() = fieldRun?.sheet

    internal val npBackdrop: NpBackdrop get() = backdrop

    internal fun npTap(act: NpAct) {
        when (act) {
            NpAct.Back -> stack.back(popKind)
            NpAct.Lyrics -> if (np.lyricsView) lyricsToggle()
            NpAct.LyricsSheet -> openOffset()
            NpAct.Queue -> openQueue()
            NpAct.Info -> npInfo()
            NpAct.Controls -> openVolume(now())
            else -> scope.launch(FailFast) { quiet { npAct(act, np, src, phone, toast, env.now) } }
        }
    }

    fun openOffset() {
        val track = offsetOpen() ?: return
        fieldEnd()
        val p = phone?.track
        val edit = (if (np.variant && p != null) app.phoneLyrics?.offset(p) else app.offset(track)) ?: return
        fieldOpen(FieldRun(OffsetSheet(edit), null, { offsetOpen() == track }) { offsetGone(track) })
    }

    internal fun openFields(sheet: FieldSheet): Boolean {
        if (stack.phase != Phase.Rest || stack.overlay != null || fieldRun?.entry == false) return false
        fieldEnd()
        val top = stack.top
        val pivot = shownPivot(top)
        fieldOpen(FieldRun(sheet, top, { stack.top == top && !sheet.gone() }) { sheet.gone() || shownPivot(top) != pivot })
        return true
    }

    private fun shownPivot(s: S?): String? = rowsOf(s)?.let { it.names.getOrNull(it.pivot) }

    private fun offsetOpen(): Long? {
        if (!np.lyricsView || stack.top?.let(::isNp) != true || stack.overlay != null || stack.phase != Phase.Rest) return null
        if (fieldRun?.entry == false) return null
        val l = np.lyrics
        val t = now()
        if (l.fading(t)) l.land(np.variant)
        val track = np.shown.track ?: return null
        return if (!l.shown(np.variant) || l.track != track || l.lines.isEmpty()) null else track
    }

    private fun offsetGone(track: Long): Boolean {
        val l = np.lyrics
        return !np.lyricsView || !l.shown(np.variant) || np.shown.track != track || l.track != track || !(l.pending || l.lines.isNotEmpty())
    }

    private fun fieldOpen(r: FieldRun) {
        fieldRun = r
        fieldMenu(r, -1)
    }

    private fun fieldCells(r: FieldRun, m: Menu = menuHost.menu) {
        m.cells(r.sheet.values(), r.sheet.mixed(), r.sheet.field())
    }

    private fun fieldSync(r: FieldRun) {
        r.live = r.sheet.field() >= 0
        if (!r.live) r.want = false
        if (r.choice == null && !r.entry) fieldCells(r)
    }

    private fun fieldMenu(r: FieldRun, at: Int) {
        val sheet = r.sheet
        r.over = openMenu(
            MenuForm.Sheet, sheet.title, sheet.items, readOnly = false, mixedRow = -1, lead = 0, confirm = false,
            act = { i -> fieldPick(r, i) },
            setup = { m ->
                fieldCells(r, m)
                r.live = sheet.field() >= 0
                if (at >= 0) m.focus.set(at, sheet.items.size, now(), false)
            },
            hold = { i -> fieldHold(r, i) },
            scrim = { sheet.scrim().also { fieldSync(r) } },
        )
    }

    private fun fieldPick(r: FieldRun, i: Int): Boolean {
        menuHost.menu.focus.set(i, r.sheet.items.size, now(), false)
        if (r.live) {
            r.sheet.pickLive(i)
        } else {
            when (val p = r.sheet.press(i)) {
                FieldPress.Field -> r.want = true
                is FieldPress.Choice -> fieldChoice(r, p.sheet, i)
                FieldPress.None -> Unit
            }
        }
        fieldSync(r)
        return false
    }

    private fun fieldChoice(r: FieldRun, c: SettingSheet, i: Int) {
        require(c.form == MenuForm.Sheet) { "a field sheet's choice is a sheet" }
        r.choice = c
        r.at = i
        r.act = menuHost.act
        r.hold = menuHost.hold
        r.scrim = menuHost.scrim
        stack.swapMenu(r.over) { t ->
            val menu = menuHost.menu
            menu.open(MenuForm.Sheet, c.title, c.items, t)
            frameMenu(menu, env.page, false) { liftRow(stack.top) }
            if (c.focus >= 0) menu.value(c.focus, c.mark, c.wrap, t)
            menuHost.act = { k ->
                r.sheet.choose(k)
                choiceEnd(r)
                false
            }
            menuHost.hold = null
            menuHost.scrim = {
                choiceBack(r)
                true
            }
        }
    }

    private fun choiceBack(r: FieldRun) {
        r.sheet.back()
        choiceEnd(r)
    }

    private fun choiceEnd(r: FieldRun) {
        fieldSync(r)
        stack.swapMenu(r.over) { t ->
            val menu = menuHost.menu
            r.choice = null
            menu.open(MenuForm.Sheet, r.sheet.title, r.sheet.items, t)
            frameMenu(menu, env.page, false) { liftRow(stack.top) }
            fieldCells(r, menu)
            menu.focus.set(r.at, r.sheet.items.size, t, false)
            menuHost.act = r.act
            menuHost.hold = r.hold
            menuHost.scrim = r.scrim
        }
    }

    private fun fieldRaise(r: FieldRun) {
        fieldSync(r)
        stack.swapMenu(r.over) { _ -> fieldCells(r) }
    }

    private fun fieldTap(r: FieldRun, h: AdjustHit) {
        r.sheet.tap(h)
        if (h == AdjustHit.Exit) fieldRaise(r) else fieldSync(r)
    }

    private fun fieldBack(r: FieldRun) {
        if (r.sheet.back()) fieldRaise(r) else fieldSync(r)
    }

    private fun fieldDrop(r: FieldRun) {
        val kept = r.sheet.back()
        fieldSync(r)
        if (!kept) stack.back(true, popKind)
    }

    private fun fieldEntry(r: FieldRun, i: Int) {
        r.held = i
        r.entry = true
        r.gen++
    }

    private fun fieldNumber(r: FieldRun): Boolean {
        val i = r.sheet.field()
        if (!r.sheet.typed(i)) return false
        fieldEntry(r, i)
        stack.cut()
        return fieldHeld(r, r.sheet.holdNumber())
    }

    private fun fieldHold(r: FieldRun, i: Int): Boolean {
        if (!r.sheet.typed(i)) return false
        fieldEntry(r, i)
        stack.cut()
        return fieldHeld(r, r.sheet.hold(i))
    }

    private fun fieldHeld(r: FieldRun, held: Boolean): Boolean {
        fieldSync(r)
        if (!held) fieldEnd()
        return held
    }

    fun fieldReturn(adjust: Boolean) {
        val r = fieldRun ?: return
        if (!r.entry) return
        if (stack.overlay === r.over) {
            r.entry = false
            return
        }
        val gen = r.gen
        scope.launch(FailFast) {
            while (stack.overlay != null || stack.moving) withFrameMillis { }
            if (fieldRun !== r || r.gen != gen) return@launch
            if (!r.reopens()) {
                fieldEnd()
                return@launch
            }
            r.entry = false
            fieldMenu(r, r.held)
            if (adjust) {
                r.sheet.press(r.held)
                fieldSync(r)
                r.sheet.adjust(now())
            }
        }
    }

    fun fieldKey(d: Int): Boolean {
        val r = fieldRun ?: return false
        if (!r.sheet.key(d)) return false
        fieldSync(r)
        return true
    }

    fun fieldEnd() {
        val r = fieldRun ?: return
        fieldRun = null
        val over = r.over
        r.sheet.dismiss()
        if (over != null && stack.overlay === over) stack.close()
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
                            val p = volume.percent()
                            if (p in volSets) hud.took(p)
                            volAct(hud.move(now(), x, y, p, volPos()))
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
    val phoneLyrics = app.phoneLyrics
    val feed = NowPlayingFeed(np, npHalves.holding(NpPart.Art).g, src, phone, phoneLyrics) { SystemClock.uptimeMillis() }
    LaunchedEffect(phoneLyrics) { phoneLyrics?.mode?.collect { np.lyrics.echo(it, np.variant) } }
    val npHalfInks = remember(npHalves, env.measurer, env.fonts, env.density, env.covers) { npHalves.map { NpInks(NpMeasure(it.g, env.measurer, env.fonts, env.density, env.covers), np) } }
    val types = rememberMusicTypes(env, app::homeItem)
    val pages = app.settingsPages
    require(pages.distinctBy { it.id }.size == pages.size) { "a settings page is listed once" }
    val settings = pages.associate { page ->
        key(page.id) {
            val pivots = page.rows.pivots.value
            val names = remember(pivots) { pivots.map { it.name } }
            val c = rememberChrome(env.page, page.id.header, names)
            page.id to (pivots to remember(c, page.look) { settingsChrome(c, page.look) })
        }
    }
    types.settings.chromes = settings.mapValues { it.value.second }
    val eqs = app.eqPages
    require(eqs.distinctBy { it.id }.size == eqs.size) { "an eq page is listed once" }
    val eq = eqs.associate { page ->
        key(page.id) {
            val pivots = page.pane.pivots.value
            val names = remember(pivots) { pivots.map { it.name } }
            val c = rememberChrome(env.page, page.id.header, names)
            page.id to (pivots to remember(c) { settingsChrome(c, HeaderLook.Page) })
        }
    }
    types.eq.chromes = eq.mapValues { it.value.second }
    types.eq.panes = eqs.associate { it.id to it.pane }
    val navGround = remember(env.bands, env.accent) { NavGround(env.bands, env.accent) }
    val host = remember { MusicHost(music, models, toast, jump, menuHost, movePaint, scope, app, env, src, volume, types, npGeo, npHalves, npHalfInks, navGround, split, view) }
    host.update(app, env, src, volume, types, phone, feed, npGeo, npHalves, npHalfInks, navGround, split, view, settings.mapValues { it.value.first }, eq.mapValues { it.value.first })
    return host
}

private class Blurred(val art: ImageBitmap, val scale: Scale, val picture: NpPicture?) {
    fun of(a: ImageBitmap?, s: Scale): Boolean = art === a && scale == s
}

private class FieldRun(val sheet: FieldSheet, val owner: Any?, val reopens: () -> Boolean, val ends: () -> Boolean) {
    var over: Overlay? = null
    var entry by mutableStateOf(false)
    var gen = 0
    var held = 0
    var at = 0
    var want by mutableStateOf(false)
    var live by mutableStateOf(false)
    var choice by mutableStateOf<SettingSheet?>(null)
    var act: (Int) -> Boolean = { false }
    var hold: ((Int) -> Boolean)? = null
    var scrim: () -> Boolean = { false }
}

private class EqSheet(val closed: () -> Unit) {
    var over: Overlay? = null
    var seen = false
    var picked = false
}

private class AdjustDown {
    var fresh = false
    var hit = AdjustHit.None
    var number = -1
}
