package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Kind as ApiKind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.Row
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastInk
import net.seilent.funekit.kit.ToastPlace
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.scrollInto
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.InputGate
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.layer
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb
import net.seilent.funekit.kit.toast
import net.seilent.funekit.kit.toastSurface
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px64
import net.seilent.funekit.scale
import net.seilent.funekit.screens.host.MusicEntry
import net.seilent.funekit.screens.host.PageThumb
import net.seilent.funekit.screens.host.albumPushKind
import net.seilent.funekit.screens.host.colPushKind
import net.seilent.funekit.screens.host.hubRowAt
import net.seilent.funekit.screens.host.isNp
import net.seilent.funekit.screens.host.musicEntry
import net.seilent.funekit.screens.host.musicPopKind
import net.seilent.funekit.screens.host.pageThumbAtSwap
import net.seilent.funekit.screens.host.pageThumbsAtTurn
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.albcol.AlbumColDraw
import net.seilent.funekit.screens.music.albcol.AlbumColGeo
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.albcol.AlbumColPaint
import net.seilent.funekit.screens.music.albcol.albumColDraw
import net.seilent.funekit.screens.music.album.AlbumDraw
import net.seilent.funekit.screens.music.album.AlbumGeo
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.album.AlbumPaint
import net.seilent.funekit.screens.music.album.FLIGHT
import net.seilent.funekit.screens.music.album.albumChrome
import net.seilent.funekit.screens.music.album.albumDraw
import net.seilent.funekit.screens.music.album.albumHeaderSide
import net.seilent.funekit.screens.music.artist.ARTIST_ALBUMS
import net.seilent.funekit.screens.music.artist.ArtistDraw
import net.seilent.funekit.screens.music.artist.ArtistGeo
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.ArtistPaint
import net.seilent.funekit.screens.music.artist.RINGS
import net.seilent.funekit.screens.music.artist.artistChrome
import net.seilent.funekit.screens.music.artist.artistDraw
import net.seilent.funekit.screens.music.artist.artistSide
import net.seilent.funekit.screens.music.artist.artistSwitch
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPaint
import net.seilent.funekit.screens.music.hub.HubSet
import net.seilent.funekit.screens.music.hub.HubState
import net.seilent.funekit.screens.music.hub.SEARCH_PIVOTS
import net.seilent.funekit.screens.music.hub.hubDraw
import net.seilent.funekit.screens.music.hub.hubThumbAt
import net.seilent.funekit.screens.music.hub.musicModel
import net.seilent.funekit.screens.music.lists.HistoryDraw
import net.seilent.funekit.screens.music.lists.HistoryGeo
import net.seilent.funekit.screens.music.lists.HistoryModel
import net.seilent.funekit.screens.music.lists.HistoryPaint
import net.seilent.funekit.screens.music.lists.ListViewDraw
import net.seilent.funekit.screens.music.lists.ListViewGeo
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.ListViewPaint
import net.seilent.funekit.screens.music.lists.PlaylistsDraw
import net.seilent.funekit.screens.music.lists.PlaylistsGeo
import net.seilent.funekit.screens.music.lists.PlaylistsKey
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.lists.PlaylistsPaint
import net.seilent.funekit.screens.music.lists.ViewKey
import net.seilent.funekit.screens.music.lists.historyDraw
import net.seilent.funekit.screens.music.lists.listViewDraw
import net.seilent.funekit.screens.music.lists.playlistsDraw
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpInks
import net.seilent.funekit.screens.music.nowplaying.NpMeasure
import net.seilent.funekit.screens.music.nowplaying.NpPaint
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.npFlat
import net.seilent.funekit.screens.music.nowplaying.npFrame
import net.seilent.funekit.screens.music.nowplaying.npPage
import net.seilent.funekit.screens.music.pivotSwitch
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.sheet.Box
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Kind
import net.seilent.funekit.sheet.Region
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.toast
import kotlin.math.ceil
import kotlin.math.floor

class S4Rig(val sc: MusicScenes, val dev: Dev, nav: Nav = Nav.None, large: Boolean = false, val accentOff: Boolean = false) {
    val s: Scale = sc.pageScale(dev, nav, large)
    private val npS = scale(dev.window(nav), nav, Surface.NowPlaying, large)
    private val g = NpLayout(npS)
    val forms: MoveForms = sc.forms(dev, s)
    val bands = groundBands(dev.h, accentOff)
    val accent = Accent.color(Accent.defaultHue, accentOff)
    private val art = Art(MusicScenes.ART_BYTES)
    private val hubState = HubState()
    val models = PageModels { e, p -> musicModel(e, p as MusicPage, hubState) }
    val hubChrome = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots, sc.covers)
    val albumChrome = albumChrome(sc.measurer, sc.fonts, s, sc.density, sc.covers)
    private val albumSide = albumHeaderSide(sc.measurer, sc.fonts, s, sc.density, sc.covers, albumChrome)
    val artistChrome = artistChrome(sc.measurer, sc.fonts, s, sc.density, sc.covers)
    private val hubDraws = HashMap<HubModel, HubDraw>()
    private val albumDraws = HashMap<AlbumModel, AlbumDraw>()
    private val colDraws = HashMap<AlbumColModel, AlbumColDraw>()
    private val artistDraws = HashMap<ArtistModel, ArtistDraw>()
    val playlistsChrome = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Playlists.headerWord ?: "", MusicPage.Playlists.pivots, sc.covers)
    val historyChrome = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.History.headerWord ?: "", MusicPage.History.pivots, sc.covers)
    private val playlistsDraws = HashMap<PlaylistsModel, PlaylistsDraw>()
    private val viewDraws = HashMap<ListViewModel, ListViewDraw>()
    private val historyDraws = HashMap<HistoryModel, HistoryDraw>()
    val np = NowPlayingModel()
    private val inks = NpInks(NpMeasure(g, sc.measurer, sc.fonts, sc.density, sc.covers), np)
    private val npPaint = NpPaint(g, np, listOf(npFlat(g) to inks), { null }, accent, hideFocus = { true })
    val npScreen = MusicEntry(NP_ENTRY, MusicPage.NowPlaying)
    private val move = PageMove()
    val mp = MovePaint(move)
    private val moving = mutableListOf<LongRange>()
    private val status = sc.moveStatus(s)
    private var next = FIRST_ENTRY
    private var colLetter = -1
    private var colAt = -1L
    var t = 0L
        private set
    var toast: SceneToast? = null
    val hub = MusicEntry(next++, MusicPage.Hub)

    private val home = object : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = Unit
    }

    val searchChrome = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Search("").headerWord ?: "", SEARCH_PIVOTS, sc.covers)

    fun hubChromeOf(m: HubModel): Chrome = if (m.set is HubSet.Search) searchChrome else hubChrome

    fun hubDrawOf(m: HubModel): HubDraw = hubDraws.getOrPut(m) { hubDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, accent, accentOff, hubChromeOf(m)) }

    fun albumDrawOf(m: AlbumModel): AlbumDraw = albumDraws.getOrPut(m) { albumDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, albumChrome, accent, accentOff) }

    fun colDrawOf(m: AlbumColModel): AlbumColDraw = colDraws.getOrPut(m) { albumColDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, albumSide, accent, accentOff) }

    fun artistDrawOf(m: ArtistModel): ArtistDraw = artistDraws.getOrPut(m) { artistDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, artistChrome, accent, accentOff) }

    fun playlistsDrawOf(m: PlaylistsModel): PlaylistsDraw = playlistsDraws.getOrPut(m) { playlistsDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, accent, accentOff) }

    fun viewDrawOf(m: ListViewModel): ListViewDraw = viewDraws.getOrPut(m) { listViewDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, accent, accentOff) }

    fun historyDrawOf(m: HistoryModel): HistoryDraw = historyDraws.getOrPut(m) { historyDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, accent, accentOff) }

    fun model(e: MusicEntry) = models[e.entry]

    val hubModel: HubModel get() = models[hub.entry] as HubModel

    private fun paintOf(m: MusicEntry): PagePaint? = when (val pm = models[m.entry]) {
        is HubModel -> HubPaint(hubDrawOf(pm), hubChromeOf(pm), -1, bands, accent)
        is AlbumModel -> AlbumPaint(albumDrawOf(pm), albumChrome, accent) { _, side -> pm.tileArt(art, pm.head?.art, side.toInt()) }
        is AlbumColModel -> AlbumColPaint(colDrawOf(pm), albumChrome, accent)
        is ArtistModel -> ArtistPaint(artistDrawOf(pm), artistChrome, accent)
        is PlaylistsModel -> PlaylistsPaint(playlistsDrawOf(pm), playlistsChrome, bands, accent, item = HOME.indexOf("playlists"))
        is ListViewModel -> viewDrawOf(pm).let { d -> ListViewPaint(d, d.chrome, bands, accent) }
        is HistoryModel -> HistoryPaint(historyDrawOf(pm), historyChrome, bands, accent, item = HOME.indexOf("history"))
        else -> if (m == npScreen) npPaint else null
    }

    private fun side(m: MusicEntry?): PageSide? = m?.let { e -> paintOf(e)?.let { PageSide(if (e.page == MusicPage.NowPlaying) npS else s, it) } }

    private fun pageThumbOf(m: MusicEntry): PageThumb? = when (val pm = models[m.entry]) {
        is HubModel -> PageThumb(pm.fade, hubThumbAt(pm, hubDrawOf(pm).geo, pm.pivot, pm.pane.track.q) != null, pm::poke)
        is AlbumModel -> PageThumb(pm.fade, albumDrawOf(pm).geo.thumb(pm.track) != null, pm::poke)
        is AlbumColModel -> PageThumb(pm.fade, colDrawOf(pm).geo.thumb(pm.track) != null, pm::poke)
        is ArtistModel -> PageThumb(pm.fade, artistDrawOf(pm).geo.thumb(pm.pane.track) != null, pm::poke)
        is PlaylistsModel -> PageThumb(pm.pane.fade, playlistsDrawOf(pm).geo.thumb(pm.size, pm.track.q) != null, pm::poke)
        is ListViewModel -> PageThumb(pm.pane.fade, viewDrawOf(pm).geo.thumb(pm.total, pm.track.q) != null, pm::poke)
        is HistoryModel -> PageThumb(pm.pane.fade, historyDrawOf(pm).geo.thumb(pm.count(pm.pivot), pm.pane.track.q) != null, pm::poke)
        else -> null
    }

    val st = PageStack<MusicEntry>(
        forms,
        { p -> models[p.entry]?.kind ?: PageKind.Plain },
        move = move,
        ready = { p -> if (isNp(p)) np.shown.track == sc.fake.player.state.value.track else models[p.entry]?.ready ?: true },
        enter = { p -> if (!isNp(p)) enterModel(p) },
        started = { turn -> pageThumbsAtTurn(turn) { pageThumbOf(it) } },
        swapped = { top, at -> pageThumbAtSwap(top, at) { pageThumbOf(it) } },
        dropped = { top, at ->
            if (colLetter >= 0) colAt = at
            pageThumbAtSwap(top, at) { pageThumbOf(it) }
        },
        isNp = ::isNp,
        gate = InputGate(),
        albumOf = { (it.page as? MusicPage.Album)?.id ?: -1L },
    ) { turn -> mp.ends(turn, HomeSide(s, home, 0f), side(turn.from), side(turn.to), forms) }

    private fun enterModel(p: MusicEntry) = runBlocking {
        val v = sc.fake.library.view()
        when (val pm = models.enter(p.entry, p.page)) {
            is HubModel -> loadHub(pm, v, pm.pivot)
            is AlbumModel -> {
                if (pm.head == null) pm.head = p.head
                loadAlbum(pm, v)
            }
            is AlbumColModel -> loadCol(pm, v)
            is ArtistModel -> loadArtist(pm, v)
            is PlaylistsModel -> {
                val geo = PlaylistsGeo(s)
                pm.pane.ppu = s.ppu
                pm.pane.geometry(RowSpec(geo.pitch, geo.view))
                pm.load(v, PlaylistsKey(0L, 0L, 0, null, v.generation), t)
            }
            is ListViewModel -> {
                if (pm.head == null) pm.head = p.head
                val geo = ListViewGeo(s)
                pm.pane.ppu = s.ppu
                pm.pane.geometry(RowSpec(geo.pitch, geo.view))
                pm.refresh(v, ViewKey(0L, v.generation), t)
                if (pm.total > 0) pm.show(v, 0, pm.total - 1)
            }
            is HistoryModel -> {
                val geo = HistoryGeo(s)
                for (q in pm.panes) {
                    q.ppu = s.ppu
                    q.geometry(RowSpec(geo.pitch, geo.view))
                }
                pm.load(v, ViewKey(0L, v.generation), t)
            }
            else -> Unit
        }
    }

    fun openHome(page: MusicPage) {
        st.push(MusicEntry(next++, page), MoveKind.T2)
    }

    fun openList(row: Int) {
        val l = when (val m = model(st.top!!)) {
            is PlaylistsModel -> m.rows.list(row)
            is HubModel -> m.pane.row(m.pane.cell(row, 0))
            else -> null
        }
        val r = requireNotNull(l) { "row $row is no list" }
        val head = Row(r.id, ApiKind.Playlist, r.title, "", null, writable = r.writable)
        st.push(musicEntry(next++, MusicPage.ListView(Listing.PlaylistTracks(r.id)), MoveKind.T3, st.top, models, row, head), MoveKind.T3, row)
    }

    fun openSearch(query: String) {
        st.push(musicEntry(next++, MusicPage.Search(query), MoveKind.T3, st.top, models), MoveKind.T3)
    }

    fun hubPivot(m: HubModel, pivot: Int) = runBlocking { loadHub(m, sc.fake.library.view(), pivot) }

    fun switchHistory(to: Int, at: Long) {
        pivotSwitch(model(st.top!!) as HistoryModel, historyChrome, to, at)
    }

    private suspend fun loadHub(m: HubModel, v: LibraryView, pivot: Int) {
        val d = hubDrawOf(m)
        m.select(pivot)
        hubChromeOf(m).select(pivot)
        m.cols = d.geo.grid.cols
        m.load(v, pivot)
        m.songs = v.page(Listing.Songs, 0, 0).total
        val pn = m.panes[pivot]
        pn.track.resize(d.geo.spec(m.role(pivot)))
        pn.track.count(pn.map?.count ?: 0, 0)
        pn.counted = pn.map
        sc.fill(m, d)
    }

    private suspend fun loadAlbum(m: AlbumModel, v: LibraryView) {
        val geo = AlbumGeo(s)
        m.geometry(geo)
        m.ppu = s.ppu
        m.load(v)
        m.show(v, 0, m.total - 1)
        m.track.count(m.count, 0)
        m.focus.set(0, m.count, 0L, false)
        m.head?.art?.let { ref ->
            m.loadArt(v, art, ref, geo.large)
            m.loadArt(v, art, ref, geo.grid)
        }
    }

    private suspend fun loadCol(m: AlbumColModel, v: LibraryView) {
        val geo = AlbumColGeo(s)
        m.geometry(geo)
        m.ppu = s.ppu
        m.load(v)
        m.track.count(m.count, 0)
        m.open(t)
        val f = m.track.drawFirst
        val l = m.track.rowAt(m.track.q + geo.view - 1)
        m.show(v, f, l + AHEAD)
        m.readTracks(v, f, l + AHEAD)
        for (r in f..minOf(m.count - 1, l + AHEAD)) m.row(r)?.art?.let { m.loadArt(v, art, it, geo.side) }
    }

    private suspend fun loadArtist(m: ArtistModel, v: LibraryView) {
        val geo = ArtistGeo(s)
        m.geometry(geo)
        for (p in m.panes) p.ppu = s.ppu
        m.load(v)
        for (p in m.panes) {
            p.track.count(p.count, 0)
            if (p.count > 0) m.show(v, p.pivot, 0, p.count - 1)
        }
        for (r in 0 until m.panes[ARTIST_ALBUMS].count) m.panes[ARTIST_ALBUMS].row(r)?.art?.let { m.loadArt(v, art, it, geo.side) }
    }

    fun start(pivot: Int) {
        st.pages.add(hub)
        models.enter(hub.entry, hub.page)
        runBlocking { loadHub(hubModel, sc.fake.library.view(), pivot) }
        hubModel.fade.track(hubThumbAt(hubModel, hubDrawOf(hubModel).geo, pivot, hubModel.pane.track.q) != null, false, HUB_SHOWN)
        t = HUB_START
        st.frame(t)
    }

    fun hubKnob(row: Int, col: Int, at: Long) {
        val pn = hubModel.pane
        pn.setFocus(row, col, at, false)
    }

    fun hubPickTitle(title: String, at: Long, top: Boolean = true) {
        val m = hubModel
        val pn = m.pane
        val map = requireNotNull(pn.map)
        for (r in 0 until map.count) {
            val row = pn.row(pn.cell(r, 0)) ?: continue
            if (row.title != title) continue
            pn.setFocus(r, 0, at, false)
            if (top) pn.track.at(pn.track.topOf(r))
            sc.fill(m, hubDrawOf(m))
            return
        }
        error("no hub row titled $title")
    }

    fun hubSelect(at: Long) {
        val m = hubModel
        val pn = m.pane
        val r = pn.focus.row
        val c = pn.col
        val row = requireNotNull(pn.row(pn.cell(r, c))) { "the hub focus at $r, $c holds no item" }
        val at0 = hubRowAt(m, hubDrawOf(m), r)
        if (m.pivot == HUB_ARTISTS) {
            val e = musicEntry(next++, MusicPage.Artist(row.id), MoveKind.T3, st.top, models, at0)
            st.push(e, MoveKind.T3, at0, pn.firsts[row.id] ?: -1L)
        } else {
            val tile = HubPaint(hubDrawOf(m), hubChrome, -1, bands, accent).tileRect(row.id) != null
            push(row, at0, tile)
        }
    }

    private fun push(row: Row, at: Int, tile: Boolean) {
        val k = albumPushKind(st.top, models, row, tile)
        st.push(musicEntry(next++, MusicPage.Album(row.id), k, st.top, models, at, row), k, at, row.id)
    }

    fun openJump() {
        st.open(Overlay(OverKind.Jump), hides = false, covers = false)
    }

    fun pickJump() {
        val map = requireNotNull(hubModel.pane.map)
        colLetter = (0 until LETTERS).first { map.has(it) }
        st.close()
    }

    fun artistKnob(n: Int, at: Long) {
        val m = model(st.top!!) as ArtistModel
        repeat(n) { i ->
            when {
                m.onRings && m.ring < RINGS - 1 -> m.ring = m.ring + 1
                m.onRings && m.pane.count > 0 -> m.listDown()
                !m.onRings -> listKnob(m.pane.track, m.pane.focus.row + 1, m.pane.count, at + i * SHEET_KNOB) { r -> m.pane.focus.set(r, m.pane.count, at + i * SHEET_KNOB, false) }
            }
        }
    }

    fun colKnob(at: Long) {
        val m = model(st.top!!) as AlbumColModel
        listKnob(m.track, m.focus.row + 1, m.count, at) { r -> m.focus.set(r, m.count, at, false) }
    }

    private fun listKnob(t: RowTrack, row: Int, count: Int, at: Long, focus: (Int) -> Unit) {
        if (row >= count) return
        focus(row)
        val q = scrollInto(t.topOf(row), t.topOf(row + 1) - t.topOf(row), t)
        if (q == t.q) return
        t.at(q)
        moving += at until at + Tok.motion_scroll.toLong()
    }

    fun pageSelect(at: Long) {
        when (val m = model(st.top!!)) {
            is ArtistModel -> push(requireNotNull(m.pane.row(m.pane.focus.row)), -1, false)
            is AlbumColModel -> push(requireNotNull(m.entry(m.focus.row).row), -1, false)
            else -> error("no select on ${st.top}")
        }
    }

    fun back() {
        st.back { musicPopKind(it, { e -> e }, models) { w -> w in HOME } ?: MoveKind.T4n }
    }

    private fun pageChrome(e: MusicEntry): Chrome? = when (models[e.entry]) {
        is AlbumModel, is AlbumColModel -> albumChrome
        is ArtistModel -> artistChrome
        is HubModel -> hubChromeOf(models[e.entry] as HubModel)
        is PlaylistsModel -> playlistsChrome
        is ListViewModel -> viewDrawOf(models[e.entry] as ListViewModel).chrome
        is HistoryModel -> historyChrome
        else -> null
    }

    fun headerDown() {
        st.top?.let(::pageChrome)?.press(Chrome.HEADER)
    }

    fun headerUp(at: Long) {
        val c = st.top?.let(::pageChrome)
        c?.lift()
        back()
        c?.settle()
        c?.tick(at, st.moving)
    }

    fun switchArtist(to: Int, at: Long) {
        artistSwitch(model(st.top!!) as ArtistModel, artistChrome, to, at)
    }

    fun play(listing: Listing, gen: Long, index: Int, at: Long, shuffle: Boolean = false) = playBy(at) { it.play(listing, gen, index, 0L, shuffle) }

    fun playBy(at: Long, act: suspend (Player) -> Unit) {
        val h = st.pushOpen(npScreen, at, NowPlayingModel.HOLD_CAP)
        runBlocking {
            act(sc.fake.player)
            val p = sc.fake.player.state.value
            val rows = sc.fake.library.view().page(Listing.Queue, p.index, 1 + g.nextTracks).rows
            np.feed(p.copy(anchor = Anchor(0, RATE, 0, false, rows.first().durationMs ?: 0L)))
            np.sync(NpShown.of(rows), at)
            np.resetFocus(at)
        }
        h.done()
    }

    fun run(to: Long, due: MutableList<Pair<Long, () -> Unit>> = mutableListOf()) {
        due.sortBy { it.first }
        val times = (generateSequence((t / SHEET_STEP + 1) * SHEET_STEP) { it + SHEET_STEP }.takeWhile { it < to } + due.map { it.first }.filter { it in t..to } + to).toSortedSet()
        for (x in times) {
            while (due.isNotEmpty() && due.first().first <= x) due.removeAt(0).second()
            st.frame(x)
            if (st.turn == null) mp.landed()
            if (colLetter >= 0 && colAt >= 0 && x > colAt && st.overlay == null) {
                val k = colPushKind(true)
                st.push(musicEntry(next++, MusicPage.AlbumCol(colLetter), k, st.top, models), k)
                colLetter = -1
                colAt = -1L
                st.frame(x)
            }
            tick(x)
            t = x
        }
    }

    private fun tick(x: Long) {
        if (st.phase != Phase.Rest) return
        val top = st.top ?: return
        val on = pageThumbOf(top)?.on ?: return
        val busy = moving.any { x in it }
        when (val m = models[top.entry]) {
            is AlbumModel -> m.tick(x, on, busy)
            is AlbumColModel -> m.tick(x, on, busy)
            is ArtistModel -> m.tick(x, on, busy)
            else -> Unit
        }
    }

    fun DrawScope.world(now: Long, rec: Regions?) {
        np.stepTo(now)
        val hr = rec?.let { HubRegions(it) }
        val turn = st.turn
        if (st.phase != Phase.Rest && turn != null) {
            with(mp) { drawMove(now, hr, if (turn.kind == MoveKind.T7 || turn.kind == MoveKind.T8) status else null) }
            rec?.let(::underFlight)
            rec?.let(::overLine)
            return
        }
        val top = st.top ?: return
        if (top == npScreen) {
            npRest(now, rec)
            return
        }
        val p = paintOf(top) ?: return
        if (p.grounded) with(p) { ground(1f, 0f) }
        val am = models[top.entry]
        val at = (am as? ArtistModel)?.switchAt
        if (am is ArtistModel && at != null && am.switching(now)) {
            val d = artistDrawOf(am)
            val side = artistSide(am, now)
            val shownAt = if (side == am.pivot) at + Tok.motion_pivotOut.toLong() else at
            with(d) { block(now, false, hr, "page") }
            shifted(hr, am.motion.dx(now, s.ppu).toFloat(), 0f) {
                layer(hr, am.motion.alpha(now), Paint()) { with(d) { content(side, shownAt, false, false, hr, "page") } }
            }
            val a = am.thumbAlpha(now)
            val r = d.geo.thumb(am.panes[side].track)
            if (r != null && a > 0f) {
                thumb(r, a)
                hr?.thumbParts("page.thumb", r, Tok.scrollbar_alpha * a, groundBands(s.heightPx, true), accent)
            }
        } else if (am is HistoryModel && am.switchAt != null && am.switching(now)) {
            val hs = requireNotNull(am.switchAt)
            val side = am.side(now)
            val shownAt = if (side == am.pivot) hs + Tok.motion_pivotOut.toLong() else hs
            val d = historyDrawOf(am)
            layer(hr, am.motion.alpha(now), Paint()) {
                shifted(hr, am.motion.dx(now, s.ppu).toFloat(), 0f) { with(d) { content(side, shownAt, true, hr, "page") } }
            }
            val a = am.thumbAlpha(now)
            val r = d.geo.thumb(am.count(side), 0)
            if (r != null && a > 0f) thumb(r, a)
        } else {
            if (am is ArtistModel) {
                with(artistDrawOf(am)) {
                    block(now, false, hr, "page")
                    content(am.pivot, now, false, false, hr, "page")
                }
            } else {
                with(p) { rest(now, hr, "page") }
            }
            val f = p.thumbFade(now)
            if (f > 0f) with(p) { thumb(f, hr, "page") }
        }
        p.chrome?.let { c ->
            chrome(c, now)
            rec?.chrome("page.chrome", c, now, size.width.toInt())
        }
        toast?.let { sceneToast(it, s, now, rec) }
    }

    fun funeosYear(rec: Regions) {
        val m = st.top?.let { models[it.entry] } as? ArtistModel ?: return
        val pn = m.panes[ARTIST_ALBUMS]
        val row = pn.row(0) ?: return
        val d = artistDrawOf(m)
        val g = d.geo
        val drop = px64(fx(Tok.detailTitleDrop) + fx(Tok.detailTitlePitch) + fx(Tok.detailSubPitch), fx(s.stdPpu))
        rec.word("funeos.row0.year", d.albumInk(row, false).year, g.textX.toFloat(), (g.rowTop(pn.track, 0) + drop).toFloat(), Roles.detailSub.alpha)
    }

    private fun overLine(r: Regions) {
        val all = r.regions as MutableList<Region>
        val lines = all.filter { it.kind == Kind.Text && it.name.startsWith(LEAVE_LINE) }
        if (lines.isEmpty()) return
        for ((i, o) in all.withIndex()) {
            if (o.kind == Kind.Text || o.kind == Kind.Glow || o.exclude != null) continue
            val crossed = lines.flatMap { l -> if (l.window.hits(o.window)) l.window.and(o.window).minus(o.box).filterNot { it.empty } else emptyList() }
            if (crossed.isEmpty()) continue
            all[i] = Region(o.name, o.kind, o.box, o.window, o.band, o.color, o.ref, o.alpha, o.hidden, o.exclude, o.masks + crossed, o.peakOff)
        }
    }

    private fun underFlight(r: Regions) {
        val all = r.regions
        val at = all.indexOfLast { it.name == FLIGHT }
        if (at < 0) return
        val f = all[at]
        val whole = Box(floor(f.box.x0), floor(f.box.y0), ceil(f.box.x1), ceil(f.box.y1))
        val crossed = ArrayList<Box>()
        for (o in all.subList(0, at)) {
            val area = if (o.kind == Kind.Text) o.band ?: o.window else o.window
            if (!area.hits(f.box)) continue
            if (o.exclude == null) o.exclude = UNDER_FLIGHT
            crossed += area.and(f.window).minus(whole).filterNot { it.empty }
        }
        if (crossed.isEmpty()) return
        (all as MutableList<Region>)[at] = Region(f.name, f.kind, f.box, f.window, f.band, f.color, f.ref, f.alpha, f.hidden, f.exclude, f.masks + crossed, f.peakOff)
    }

    private fun DrawScope.npRest(now: Long, rec: Regions?) {
        if (rec != null) {
            rec.edges("np.art", Kind.Art, Edges(g.artX, g.artY, g.artX + g.artSide, g.artY + g.artSide), Ink.tile, 1f, peakOff = ART_PEAK)
            val b = g.bar
            rec.edges("np.bar", Kind.Bar, Edges(b.x0, b.y0, b.x1, b.y1), Ink.white, Tok.npBarTrack)
        }
        npPage(g, npFrame(np, g, now, false), inks.ink(now), inks.time(now, 0L), null, null, accent, accentOff, false, rec)
    }

    companion object {
        const val NP_ENTRY = 1L
        const val FIRST_ENTRY = 2L
        const val HUB_START = 2400L
        const val RATE = 48000
        const val AHEAD = 2
        const val LETTERS = 38
        private const val LEAVE_LINE = "leave.line."
        const val UNDER_FLIGHT = "under the flying album tile, which gates on its own edges: the fixture's flat art there against funeOS's gradient demo cover puts funeOS ink at the covered window's edge (A105 artist-150 ring peaks 83 and 56 levels against funeOS's 143 and 193)"
    }
}

fun knobFade(steps: Int, shot: Long): Float = ThumbFade().also { it.show(PAGE_KNOB + (steps - 1) * SHEET_KNOB + Tok.motion_scroll.toLong()) }.at(shot)

fun shotMs(kind: String): Long = Regex("(album|artist)-(\\d+)").find(kind)?.groupValues?.get(2)?.toLong() ?: DETAIL_SHOT

const val PAGE_KNOB = 1100L
const val NAV_LARGE = "with NAV BAR ON at SIZE LARGE funeOS ends the page at its nav strip top, canvas_h less navH in LARGE units (V1 276.8 px), and the kit at Android's bar top (284 px): the page content runs 7 px lower here (the artist row art cut at 284 against 277)"
const val DETAIL_SHOT = 2500L

class SceneToast(val toast: Toast, val ink: ToastInk)

fun MusicScenes.sceneToast(row: Row, s: Scale, shown: Long): SceneToast = sceneToast(row.sub, row.title, s, shown, true)

fun MusicScenes.sceneToast(top: String, bottom: String, s: Scale, shown: Long, track: Boolean): SceneToast {
    val t = Toast()
    t.show(shown, top, bottom, track = track)
    t.frame(ToastPlace.Bottom, toastSurface(s, s.navOn), shown)
    return SceneToast(t, toastInk(t, s))
}

fun DrawScope.sceneToast(st: SceneToast, s: Scale, now: Long, rec: Regions?) {
    st.toast.frame(ToastPlace.Bottom, toastSurface(s, s.navOn), now)
    toast(st.toast, st.ink, s, now)
    rec?.toast("toast", st.toast, st.ink, s, now, size.width.toInt())
}

fun playAt(knob: Int): Long = PAGE_KNOB + knob * SHEET_KNOB + PLAY_KEY

const val PLAY_KEY = 100L
const val ARTIST_PLAY = 600L
