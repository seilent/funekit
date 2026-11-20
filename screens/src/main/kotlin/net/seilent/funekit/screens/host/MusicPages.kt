package net.seilent.funekit.screens.host

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.rememberChrome
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.PivotPage
import net.seilent.funekit.screens.music.albcol.AlbumColDraw
import net.seilent.funekit.screens.music.albcol.AlbumColLayer
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.albcol.AlbumColPaint
import net.seilent.funekit.screens.music.albcol.albumColDraw
import net.seilent.funekit.screens.music.album.AlbumDraw
import net.seilent.funekit.screens.music.album.AlbumLayer
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.album.AlbumPaint
import net.seilent.funekit.screens.music.album.albumDraw
import net.seilent.funekit.screens.music.album.albumHeaderSide
import net.seilent.funekit.screens.music.album.rememberAlbumChrome
import net.seilent.funekit.screens.music.artist.ArtistDraw
import net.seilent.funekit.screens.music.artist.ArtistLayer
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.ArtistPaint
import net.seilent.funekit.screens.music.artist.artistDraw
import net.seilent.funekit.screens.music.artist.rememberArtistChrome
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubLayer
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPaint
import net.seilent.funekit.screens.music.hub.HubSet
import net.seilent.funekit.screens.music.hub.SEARCH_PIVOTS
import net.seilent.funekit.screens.music.hub.hubDraw
import net.seilent.funekit.screens.music.hub.hubThumbAt
import net.seilent.funekit.screens.music.lists.HistoryAct
import net.seilent.funekit.screens.music.lists.HistoryDraw
import net.seilent.funekit.screens.music.lists.HistoryLayer
import net.seilent.funekit.screens.music.lists.HistoryModel
import net.seilent.funekit.screens.music.lists.HistoryPaint
import net.seilent.funekit.screens.music.lists.ListViewDraw
import net.seilent.funekit.screens.music.lists.ListViewLayer
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.ListViewPaint
import net.seilent.funekit.screens.music.lists.PlaylistsDraw
import net.seilent.funekit.screens.music.lists.PlaylistsLayer
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.lists.PlaylistsPaint
import net.seilent.funekit.screens.music.lists.historyDraw
import net.seilent.funekit.screens.music.lists.listViewDraw
import net.seilent.funekit.screens.music.lists.playlistsDraw
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.queue.QueueDraw
import net.seilent.funekit.screens.music.queue.QueueLayer
import net.seilent.funekit.screens.music.queue.QueueModel
import net.seilent.funekit.screens.music.queue.QueuePaint
import net.seilent.funekit.screens.music.queue.queueDraw

class MusicEnv(
    val page: Scale,
    val np: Scale,
    val measurer: TextMeasurer,
    val fonts: FontFamily.Resolver,
    val density: Density,
    val covers: Covers?,
    val bands: List<Band>,
    val accent: Color,
    val accentOff: Boolean,
    val a: Arbiter,
    val now: () -> Long,
    val poke: () -> Unit,
    val grid: Scale,
)

class PageThumb(val fade: ThumbFade, val on: Boolean, val poke: () -> Unit)

class PageAt(val live: Boolean, val visible: Boolean, val frozen: Boolean, val hideFocus: Boolean, val snap: Int, val covered: Boolean, val leavesLit: () -> Boolean)

interface MusicActs {
    fun back()

    fun sheet(t: SheetTarget): Boolean

    fun nowPlaying(): Boolean

    fun playOpen(form: NpForm, keep: Boolean = false, cut: Boolean = false, play: suspend (MusicSource) -> Boolean): Boolean

    fun album(row: Row, at: Int, hubTile: Boolean)

    fun artist(row: Row, at: Int, thumb: Long)

    fun ring(m: ArtistModel, i: Int): Boolean

    fun jump(e: MusicEntry)

    fun commit(query: String)
}

abstract class MusicPageType<M : PageModel, D : Any>(protected val types: MusicTypes) {
    private val draws = HashMap<M, D>()

    protected val env: MusicEnv get() = types.env

    protected abstract fun of(pm: PageModel?): M?

    protected abstract fun draw(m: M): D

    protected abstract fun chrome(m: M): Chrome

    protected abstract fun side(m: M): PagePaint

    protected abstract fun thumb(m: M): PageThumb

    protected open fun back(m: M): Boolean = false

    protected open fun enter(m: M, e: MusicEntry) {}

    @Composable
    protected abstract fun Layer(e: MusicEntry, m: M, src: MusicSource?, at: PageAt, acts: MusicActs)

    fun drawOf(m: M): D = draws.getOrPut(m) { draw(m) }

    fun owns(pm: PageModel?): Boolean = of(pm) != null

    fun chromeOf(pm: PageModel?): Chrome? = of(pm)?.let(::chrome)

    fun sideOf(pm: PageModel?): PagePaint? = of(pm)?.let(::side)

    fun thumbOf(pm: PageModel?): PageThumb? = of(pm)?.let(::thumb)

    fun backOf(pm: PageModel?): Boolean = of(pm)?.let(::back) == true

    fun enterOf(pm: PageModel?, e: MusicEntry) {
        of(pm)?.let { enter(it, e) }
    }

    fun pivotOf(pm: PageModel?): Pair<PivotPage, Chrome>? = of(pm)?.let { m -> (m as? PivotPage)?.let { it to chrome(m) } }

    fun retain(models: PageModels) {
        draws.keys.retainAll { models[it.entry] === it }
    }

    internal fun holds(pm: PageModel?): Boolean = of(pm)?.let(draws::containsKey) == true

    @Composable
    fun LayerOf(e: MusicEntry, pm: PageModel?, src: MusicSource?, at: PageAt, acts: MusicActs) {
        of(pm)?.let { Layer(e, it, src, at, acts) }
    }
}

class HubType(types: MusicTypes, private val hub: Chrome, private val search: Chrome) : MusicPageType<HubModel, HubDraw>(types) {
    override fun of(pm: PageModel?): HubModel? = pm as? HubModel

    override fun chrome(m: HubModel): Chrome = if (m.set is HubSet.Search) search else hub

    override fun draw(m: HubModel): HubDraw = env.let { hubDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, it.accent, it.accentOff, chrome(m)) }

    override fun side(m: HubModel): PagePaint =
        HubPaint(drawOf(m), chrome(m), if (m.set is HubSet.Search) -1 else types.homeItem(MusicPage.Hub.headerWord ?: ""), env.bands, env.accent)

    override fun thumb(m: HubModel): PageThumb = PageThumb(m.fade, hubThumbAt(m, drawOf(m).geo, m.pivot, m.pane.track.q) != null, m::poke)

    override fun back(m: HubModel): Boolean = m.up()

    @Composable
    override fun Layer(e: MusicEntry, m: HubModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        HubLayer(
            m, drawOf(m), chrome(m), src, env.a, env.bands, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap, pageCovered = at.covered,
            onBack = acts::back,
            onJump = { letter ->
                m.jumpPick = -1
                m.colPick = -1
                m.jump.open(m.avail(), letter, env.grid.stdContentBottom)
                acts.jump(e)
            },
            onSheet = acts::sheet,
            leavesLit = at.leavesLit,
            onPlay = { act ->
                acts.playOpen(NpForm.Page) {
                    m.play(act, it)
                    true
                }
            },
            onAlbum = { row, r -> acts.album(row, hubRowAt(m, drawOf(m), r), HubPaint(drawOf(m), chrome(m), -1, env.bands, env.accent).tileRect(row.id) != null) },
            onArtist = { row, r, thumb -> acts.artist(row, hubRowAt(m, drawOf(m), r), thumb) },
            onNowPlaying = acts::nowPlaying,
            onCommit = acts::commit,
        )
    }
}

class QueueType(types: MusicTypes, private val chrome: Chrome) : MusicPageType<QueueModel, QueueDraw>(types) {
    override fun of(pm: PageModel?): QueueModel? = pm as? QueueModel

    override fun chrome(m: QueueModel): Chrome = chrome

    override fun draw(m: QueueModel): QueueDraw = env.let { queueDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, it.accent, it.accentOff) }

    override fun side(m: QueueModel): PagePaint = QueuePaint(drawOf(m), chrome, env.bands, env.accent)

    override fun thumb(m: QueueModel): PageThumb = drawOf(m).let { d -> PageThumb(d.m.fade, d.geo.thumb(d.m.size, d.m.track.q) != null, d.m::poke) }

    @Composable
    override fun Layer(e: MusicEntry, m: QueueModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        QueueLayer(
            m, drawOf(m), chrome, src, env.a, env.bands, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap, pageCovered = at.covered,
            onBack = acts::back,
            onSheet = acts::sheet,
            leavesLit = at.leavesLit,
            onPlay = { r -> m.revision >= 0 && m.row(r) != null && acts.playOpen(NpForm.Page, keep = true) { m.tap(r, it) } },
        )
    }
}

class AlbumType(types: MusicTypes, private val chrome: Chrome, private val context: Context) : MusicPageType<AlbumModel, AlbumDraw>(types) {
    override fun of(pm: PageModel?): AlbumModel? = pm as? AlbumModel

    override fun chrome(m: AlbumModel): Chrome = chrome

    override fun draw(m: AlbumModel): AlbumDraw = env.let { albumDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, chrome, it.accent, it.accentOff) }

    override fun side(m: AlbumModel): PagePaint = AlbumPaint(drawOf(m), chrome, env.accent) { _, side -> m.tileArt(Art.get(context), m.head?.art, side.toInt()) }

    override fun thumb(m: AlbumModel): PageThumb = PageThumb(m.fade, drawOf(m).geo.thumb(m.track) != null, m::poke)

    override fun enter(m: AlbumModel, e: MusicEntry) {
        if (m.head == null) m.head = e.head
    }

    @Composable
    override fun Layer(e: MusicEntry, m: AlbumModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        AlbumLayer(
            m, drawOf(m), chrome, src, env.a, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap,
            onBack = acts::back,
            leavesLit = at.leavesLit,
            pageCovered = at.covered,
            onPlay = { r ->
                m.gen >= 0 && acts.playOpen(NpForm.Page) { s ->
                    s.player.play(Listing.AlbumTracks(m.id), m.gen, r, 0L)
                    true
                }
            },
            onSheet = acts::sheet,
        )
    }
}

class AlbumColType(types: MusicTypes, private val chrome: Chrome) : MusicPageType<AlbumColModel, AlbumColDraw>(types) {
    private val header = env.let { albumHeaderSide(it.measurer, it.fonts, it.page, it.density, it.covers, chrome) }

    override fun of(pm: PageModel?): AlbumColModel? = pm as? AlbumColModel

    override fun chrome(m: AlbumColModel): Chrome = chrome

    override fun draw(m: AlbumColModel): AlbumColDraw = env.let { albumColDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, header, it.accent, it.accentOff) }

    override fun side(m: AlbumColModel): PagePaint = AlbumColPaint(drawOf(m), chrome, env.accent)

    override fun thumb(m: AlbumColModel): PageThumb = PageThumb(m.fade, drawOf(m).geo.thumb(m.track) != null, m::poke)

    @Composable
    override fun Layer(e: MusicEntry, m: AlbumColModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        AlbumColLayer(
            m, drawOf(m), chrome, src, env.a, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap,
            onBack = acts::back,
            onJump = { letter ->
                m.jumpGrid.open(m.avail(), letter, env.grid.stdContentBottom)
                acts.jump(e)
            },
            onAlbum = { row -> acts.album(row, -1, false) },
            leavesLit = at.leavesLit,
            onSheet = acts::sheet,
        )
    }
}

class ArtistType(types: MusicTypes, private val chrome: Chrome) : MusicPageType<ArtistModel, ArtistDraw>(types) {
    override fun of(pm: PageModel?): ArtistModel? = pm as? ArtistModel

    override fun chrome(m: ArtistModel): Chrome = chrome

    override fun draw(m: ArtistModel): ArtistDraw = env.let { artistDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, chrome, it.accent, it.accentOff) }

    override fun side(m: ArtistModel): PagePaint = ArtistPaint(drawOf(m), chrome, env.accent)

    override fun thumb(m: ArtistModel): PageThumb = PageThumb(m.fade, drawOf(m).geo.thumb(m.pane.track) != null, m::poke)

    @Composable
    override fun Layer(e: MusicEntry, m: ArtistModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        ArtistLayer(
            m, drawOf(m), chrome, src, env.a, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap,
            onBack = acts::back,
            onRing = { i -> m.gen >= 0 && acts.ring(m, i) },
            onAlbum = { row -> acts.album(row, -1, false) },
            onPlay = { r ->
                m.gen >= 0 && acts.playOpen(NpForm.Page) { s ->
                    s.player.play(Listing.ArtistTracks(m.id), m.gen, r, 0L)
                    true
                }
            },
            leavesLit = at.leavesLit,
            pageCovered = at.covered,
            onSheet = acts::sheet,
        )
    }
}

class PlaylistsType(types: MusicTypes, private val chrome: Chrome) : MusicPageType<PlaylistsModel, PlaylistsDraw>(types) {
    override fun of(pm: PageModel?): PlaylistsModel? = pm as? PlaylistsModel

    override fun chrome(m: PlaylistsModel): Chrome = chrome

    override fun draw(m: PlaylistsModel): PlaylistsDraw = env.let { playlistsDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, it.accent, it.accentOff) }

    override fun side(m: PlaylistsModel): PagePaint = PlaylistsPaint(drawOf(m), chrome, env.bands, env.accent, item = types.homeItem(MusicPage.Playlists.headerWord ?: ""))

    override fun thumb(m: PlaylistsModel): PageThumb = PageThumb(m.pane.fade, drawOf(m).geo.thumb(m.size, m.track.q) != null, m::poke)

    @Composable
    override fun Layer(e: MusicEntry, m: PlaylistsModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        PlaylistsLayer(
            m, drawOf(m), chrome, src, env.a, env.bands, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap,
            onBack = acts::back,
            onSheet = acts::sheet,
            onNowPlaying = acts::nowPlaying,
            leavesLit = at.leavesLit,
            pageCovered = at.covered,
            onPlay = { act ->
                acts.playOpen(NpForm.Page) { s ->
                    s.player.play(act.listing, m.rows.generation, act.index, 0L)
                    true
                }
            },
        )
    }
}

class ListViewType(types: MusicTypes) : MusicPageType<ListViewModel, ListViewDraw>(types) {
    override fun of(pm: PageModel?): ListViewModel? = pm as? ListViewModel

    override fun chrome(m: ListViewModel): Chrome = drawOf(m).chrome

    override fun draw(m: ListViewModel): ListViewDraw = env.let { listViewDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, it.accent, it.accentOff) }

    override fun side(m: ListViewModel): PagePaint = drawOf(m).let { d -> ListViewPaint(d, d.chrome, env.bands, env.accent) }

    override fun thumb(m: ListViewModel): PageThumb = PageThumb(m.pane.fade, drawOf(m).geo.thumb(m.total, m.track.q) != null, m::poke)

    override fun enter(m: ListViewModel, e: MusicEntry) {
        if (m.head == null) m.head = e.head
    }

    @Composable
    override fun Layer(e: MusicEntry, m: ListViewModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        val d = drawOf(m)
        ListViewLayer(
            m, d, d.chrome, src, env.a, env.bands, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap,
            onBack = acts::back,
            onSheet = acts::sheet,
            leavesLit = at.leavesLit,
            pageCovered = at.covered,
            onPlay = { act ->
                acts.playOpen(NpForm.Page) { s ->
                    s.player.play(act.listing, m.generation, act.index, 0L)
                    true
                }
            },
        )
    }
}

class HistoryType(types: MusicTypes, private val chrome: Chrome) : MusicPageType<HistoryModel, HistoryDraw>(types) {
    override fun of(pm: PageModel?): HistoryModel? = pm as? HistoryModel

    override fun chrome(m: HistoryModel): Chrome = chrome

    override fun draw(m: HistoryModel): HistoryDraw = env.let { historyDraw(m, it.page, it.measurer, it.fonts, it.density, it.covers, it.accent, it.accentOff) }

    override fun side(m: HistoryModel): PagePaint = HistoryPaint(drawOf(m), chrome, env.bands, env.accent, item = types.homeItem(MusicPage.History.headerWord ?: ""))

    override fun thumb(m: HistoryModel): PageThumb = PageThumb(m.pane.fade, drawOf(m).geo.thumb(m.count(m.pivot), m.pane.track.q) != null, m::poke)

    @Composable
    override fun Layer(e: MusicEntry, m: HistoryModel, src: MusicSource?, at: PageAt, acts: MusicActs) {
        HistoryLayer(
            m, drawOf(m), chrome, src, env.a, env.bands, env.now,
            live = at.live, visible = at.visible, frozen = at.frozen,
            hideFocus = at.hideFocus, snap = at.snap,
            onBack = acts::back,
            onSheet = acts::sheet,
            leavesLit = at.leavesLit,
            pageCovered = at.covered,
            onPlay = { act ->
                acts.playOpen(NpForm.Page) { s ->
                    when (act) {
                        is HistoryAct.Song -> s.player.play(listOf(act.id), 0, 0L)
                        is HistoryAct.Album -> s.player.play(act.listing, m.generation, act.index, 0L)
                        HistoryAct.None -> return@playOpen false
                    }
                    true
                }
            },
        )
    }
}

class MusicTypes(var env: MusicEnv, var homeItem: (String) -> Int, val chromes: MusicChromes, context: Context) {
    val all: List<MusicPageType<*, *>> = listOf(
        HubType(this, chromes.hub, chromes.search),
        QueueType(this, chromes.queue),
        AlbumType(this, chromes.album, context),
        AlbumColType(this, chromes.album),
        ArtistType(this, chromes.artist),
        PlaylistsType(this, chromes.playlists),
        ListViewType(this),
        HistoryType(this, chromes.history),
    )

    fun of(pm: PageModel?): MusicPageType<*, *>? = all.firstOrNull { it.owns(pm) }

    fun chrome(pm: PageModel?): Chrome? = of(pm)?.chromeOf(pm)

    fun side(pm: PageModel?): PagePaint? = of(pm)?.sideOf(pm)

    fun thumb(pm: PageModel?): PageThumb? = of(pm)?.thumbOf(pm)

    fun pivot(pm: PageModel?): Pair<PivotPage, Chrome>? = of(pm)?.pivotOf(pm)

    fun back(pm: PageModel?): Boolean = of(pm)?.backOf(pm) == true

    fun enter(pm: PageModel?, e: MusicEntry) {
        of(pm)?.enterOf(pm, e)
    }

    fun retain(models: PageModels) = all.forEach { it.retain(models) }

    internal fun holds(pm: PageModel?): Boolean = of(pm)?.holds(pm) == true
}

class MusicChromes(val hub: Chrome, val search: Chrome, val queue: Chrome, val album: Chrome, val artist: Chrome, val playlists: Chrome, val history: Chrome)

@Composable
fun rememberMusicTypes(env: MusicEnv, homeItem: (String) -> Int): MusicTypes {
    val context = LocalContext.current
    val s = env.page
    val chromes = MusicChromes(
        rememberChrome(s, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots),
        rememberChrome(s, MusicPage.Search("").headerWord ?: "", SEARCH_PIVOTS),
        rememberChrome(s, MusicPage.Queue.headerWord ?: "", MusicPage.Queue.pivots),
        rememberAlbumChrome(s),
        rememberArtistChrome(s),
        rememberChrome(s, MusicPage.Playlists.headerWord ?: "", MusicPage.Playlists.pivots),
        rememberChrome(s, MusicPage.History.headerWord ?: "", MusicPage.History.pivots),
    )
    val c = listOf(chromes.hub, chromes.search, chromes.queue, chromes.album, chromes.artist, chromes.playlists, chromes.history)
    val types = remember(s, env.density, env.fonts, env.covers, env.accent, c) { MusicTypes(env, homeItem, chromes, context) }
    types.env = env
    types.homeItem = homeItem
    return types
}
