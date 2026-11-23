package net.seilent.funekit.screens.music.hub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.core.Section
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.GlowPair
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpScroll
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.art.cover
import net.seilent.funekit.kit.focusAfterScroll
import net.seilent.funekit.kit.motion.PivotMotion
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryCaps
import net.seilent.funekit.api.LibraryState
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.ScanProgress
import net.seilent.funekit.api.TrackIds
import net.seilent.funekit.screens.music.Anchor
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.Paging
import net.seilent.funekit.screens.music.PivotPage
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.lists.HistoryModel
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.lists.favsPlay
import net.seilent.funekit.screens.music.lists.favsTarget
import net.seilent.funekit.screens.music.lists.listPlay
import net.seilent.funekit.screens.music.lists.listTarget
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.pivotSwitchEnd
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.queue.QueueModel
import net.seilent.funekit.screens.music.search.SearchQuery
import net.seilent.funekit.screens.music.search.searchBytes
import net.seilent.funekit.screens.music.staleIn
import kotlin.math.max
import kotlin.math.min

val HUB_ARTISTS: Int = MusicPage.Hub.pivots.indexOf("artists")
val HUB_PLAYLISTS: Int = MusicPage.Hub.pivots.indexOf("playlists")
val HUB_SONGS: Int = MusicPage.Hub.pivots.indexOf("songs")
val HUB_GENRES: Int = MusicPage.Hub.pivots.indexOf("genres")
val HUB_FOLDERS: Int = MusicPage.Hub.pivots.indexOf("folders")
val HUB_COUNT: Int = MusicPage.Hub.pivots.size

const val NO_CELL = Int.MAX_VALUE
const val LABEL_CELL = -1
const val NOW_CELL = LABEL_CELL - 1
const val FAVS_CELL = NOW_CELL - 1

fun letterCell(letter: Int): Int = Int.MIN_VALUE + letter

fun letterOf(cell: Int): Int = if (cell < Int.MIN_VALUE + Section.count) cell - Int.MIN_VALUE else -1

fun hubListing(pivot: Int, folder: Long): Listing = when (pivot) {
    HUB_ALBUMS -> Listing.Albums
    HUB_ARTISTS -> Listing.Artists
    HUB_PLAYLISTS -> Listing.Playlists
    HUB_SONGS -> Listing.Songs
    HUB_GENRES -> Listing.Genres
    else -> Listing.Folder(folder)
}

fun hubCollection(pivot: Int): Boolean = pivot == HUB_ARTISTS || pivot == HUB_GENRES || pivot == HUB_PLAYLISTS

fun hubGrid(pivot: Int): Boolean = pivot == HUB_ALBUMS

fun hubThumbs(pivot: Int): Boolean = pivot == HUB_ARTISTS || pivot == HUB_GENRES

fun songsLabel(n: Int): String = if (n == 1) "$n song" else "$n songs"

class HubMap(private val cells: IntArray, val cols: Int, private val first: IntArray, private val pos: IntArray, val lead: Int = 0) {
    val count: Int = lead + (cells.size - lead + cols - 1) / cols

    val size: Int get() = cells.size

    private fun start(row: Int): Int = if (row < lead) row else lead + (row - lead) * cols

    private fun rowOfCell(i: Int): Int = if (i < lead) i else lead + (i - lead) / cols

    private fun colOfCell(i: Int): Int = if (i < lead) 0 else (i - lead) % cols

    fun cells(row: Int): Int = if (row < 0) 0 else if (row < lead) 1 else (cells.size - start(row)).coerceIn(0, cols)

    fun at(row: Int, col: Int = 0): Int {
        if (row < 0 || col < 0 || col >= cols || col >= cells(row)) return NO_CELL
        return cells[start(row) + col]
    }

    fun isLead(row: Int): Boolean = row in 0 until lead

    fun has(section: Int): Boolean = section in first.indices && first[section] >= 0

    fun rowOf(section: Int): Int = if (has(section)) rowOfCell(first[section]) else -1

    fun colOf(section: Int): Int = if (has(section)) colOfCell(first[section]) else 0

    fun avail(): BooleanArray = BooleanArray(Section.count) { has(it) }

    fun isLetter(row: Int, col: Int = 0): Boolean = letterOf(at(row, col)) >= 0

    fun rowOfIndex(index: Int): Int = if (index in pos.indices) rowOfCell(pos[index]) else -1

    fun colOfIndex(index: Int): Int = if (index in pos.indices) colOfCell(pos[index]) else 0

    fun indexAt(row: Int, col: Int = 0): Int = at(row, col).takeIf { it >= 0 && it != NO_CELL } ?: -1

    fun indices(firstRow: Int, lastRow: Int): IntRange? {
        var lo = Int.MAX_VALUE
        var hi = -1
        for (r in max(0, firstRow)..min(count - 1, lastRow)) {
            for (c in 0 until cells(r)) {
                val i = indexAt(r, c)
                if (i < 0) continue
                lo = min(lo, i)
                hi = max(hi, i)
            }
        }
        return if (hi < 0) null else lo..hi
    }

    fun sectionAt(row: Int, col: Int, sections: ByteArray): Int {
        val v = at(row, col)
        val l = letterOf(v)
        if (l >= 0) return l
        if (v >= 0 && v != NO_CELL) return sections.getOrNull(v)?.toInt() ?: 0
        return (row + 1 until count).firstNotNullOfOrNull { r -> letterOf(at(r, 0)).takeIf { it >= 0 } } ?: 0
    }
}

fun hubMap(sections: ByteArray, cols: Int = 1, lead: List<Int> = emptyList(), divided: Int = sections.size): HubMap {
    val cells = ArrayList<Int>(sections.size + lead.size)
    val first = IntArray(Section.count) { -1 }
    val pos = IntArray(sections.size)
    cells += lead
    var prev = -1
    for (i in sections.indices) {
        if (i < divided) {
            val sec = sections[i].toInt()
            if (sec != prev && sec in first.indices) {
                if (first[sec] < 0) first[sec] = cells.size
                cells += letterCell(sec)
                prev = sec
            }
        }
        pos[i] = cells.size
        cells += i
    }
    return HubMap(cells.toIntArray(), cols, first, pos, lead.size)
}

class HubPlaying(val track: Long? = null, val ids: TrackIds? = null, val origin: Listing? = null, val queue: Int = 0) {
    fun artist(id: Long): Boolean = ids?.artists?.contains(id) == true

    fun album(id: Long): Boolean = ids?.album == id

    fun genre(id: Long): Boolean = ids?.genre == id

    fun folder(id: Long): Boolean = ids?.folders?.contains(id) == true

    fun song(id: Long): Boolean = track == id

    fun list(id: Long): Boolean = queue > 0 && origin == Listing.PlaylistTracks(id)

    fun favorites(): Boolean = queue > 0 && origin == Listing.Favorites
}

class FolderLevel(val parent: Long, val offset: Int, val key: Long, val kind: Kind?, val name: String)

sealed interface HubAct {
    data object None : HubAct

    data class Jump(val letter: Int) : HubAct

    data class Play(val listing: Listing, val index: Int) : HubAct

    data class Enter(val folder: Long, val name: String) : HubAct

    data object NowPlaying : HubAct

    data class Artist(val id: Long) : HubAct

    data class Album(val id: Long) : HubAct

    data object Query : HubAct
}

enum class HubPick { Scroll, Defer }

fun hubPick(pivot: Int): HubPick = hubPick(HubSet.Music, pivot)

data class LibraryMark(val state: LibraryState, val running: Boolean, val failed: Boolean) {
    constructor(state: LibraryState, scan: ScanProgress) : this(state, scan.running, scan.failed)
}

class HubPane(val pivot: Int, first: Listing = hubListing(pivot, 0L)) {
    var listing: Listing by mutableStateOf(first)
        private set
    var sections: ByteArray? by mutableStateOf(null)
    var map: HubMap? by mutableStateOf(null)
    var paging: Paging by mutableStateOf(Paging(listing))
        private set
    var whole: List<Row>? by mutableStateOf(null)
    var pending: Anchor? by mutableStateOf(null)
    var heldAt = 0
    var heldOn: LibraryMark? = null
    var favorites by mutableIntStateOf(0)
    var folders = 0
    var loads by mutableIntStateOf(0)
        private set
    var gen = -1L
    val track = RowTrack(1, 0)
    var ppu = 0f
    var counted: HubMap? = null
    val focus = FocusTrack().also { it.attach() }
    var col by mutableIntStateOf(0)
    private var keptCol = 0
    val press = PressSlots()
    val glow = GlowPair()
    val thumbs = mutableStateMapOf<Long, List<ArtRef?>>()
    val firsts = HashMap<Long, Long>()
    var thumbsRead by mutableIntStateOf(0)
        private set
    var down = -1
    var downCol = 0
    var downX = 0f
    var ring = false

    var lists = -1L
    var favs = -1L

    fun stale() {
        gen = -1L
        loads++
    }

    fun readThumbs(id: Long, refs: List<ArtRef?>, first: Long = -1L) {
        firsts[id] = first
        thumbs[id] = refs
        thumbsRead++
    }

    fun row(index: Int): Row? = if (index < 0) null else whole?.getOrNull(index) ?: paging.window?.row(index)

    fun cell(row: Int, col: Int): Int = map?.at(row, col) ?: NO_CELL

    fun into(l: Listing, bump: Boolean = true) {
        listing = l
        paging = Paging(l)
        sections = null
        map = null
        whole = null
        pending = null
        heldOn = null
        thumbs.clear()
        firsts.clear()
        counted = null
        if (bump) loads++
    }

    fun item(row: Int, col: Int): Int = row * (map?.cols ?: 1) + col

    fun setFocus(row: Int, col: Int, now: Long, animate: Boolean) {
        val m = map ?: return
        if (m.count == 0) return
        val r = row.coerceIn(0, m.count - 1)
        focus.set(r, m.count, now, animate)
        this.col = col.coerceIn(0, max(0, m.cells(r) - 1))
    }

    fun focusDown(row: Int, col: Int, now: Long) {
        val r = focus.row
        val c = this.col
        setFocus(row, col, now, true)
        focus.setUnpress(r)
        keptCol = c
    }

    fun unpress(now: Long) {
        val m = map ?: return
        if (focus.unpress(m.count, now)) col = min(keptCol, max(0, m.cells(focus.row) - 1))
    }

    fun afterScroll(now: Long) {
        focus.dropUnpress()
        val m = map ?: return
        val r = focusAfterScroll(focus.row, track)
        if (r == focus.row) return
        focus.set(r, m.count, now, false)
        col = min(col, max(0, m.cells(r) - 1))
    }
}

class HubState(first: HubSet = HubSet.Music) {
    var set: HubSet by mutableStateOf(first)
    val panes: List<HubPane> = List(first.pivots.size) { HubPane(it, first.listing(it, 0L)) }
    var folder by mutableLongStateOf(0L)
    var base = 0L
    val levels = ArrayList<FolderLevel>()
    var settle: FolderLevel? = null
    var settleFrom = 0L
    var entered = false
    var songs by mutableIntStateOf(-1)
    val fade = ThumbFade()

    fun open() {
        for (pn in panes) {
            pn.press.reset()
            pn.glow.off()
            pn.down = -1
        }
        val a = panes[set.grid]
        a.setFocus(0, 0, 0L, false)
        a.col = 0
        a.track.at(0)
        if (set == HubSet.Music) panes[HUB_PLAYLISTS].gen = -1L
    }
}

class HubModel(entry: Long, page: MusicPage = MusicPage.Hub, private val st: HubState = HubState()) : PivotPage(entry, page) {
    val panes: List<HubPane> get() = st.panes
    val pane: HubPane get() = panes[pivot]
    val set: HubSet get() = st.set
    val folder: Long get() = st.folder
    val atRoot: Boolean get() = st.folder == st.base
    val path: List<String> get() = st.levels.map { it.name }
    var playing by mutableStateOf(HubPlaying())
    var scanning by mutableStateOf(false)
    var found by mutableIntStateOf(0)
    var songs: Int
        get() = st.songs
        set(v) {
            st.songs = v
        }
    var generation by mutableLongStateOf(-1L)
    var revisions = -1L to -1L
    var restoring = true
    var cols = st.panes[st.set.grid].map?.cols ?: 1
    val jump = JumpGrid()
    val fade: ThumbFade get() = st.fade
    var switchAt: Long? by mutableStateOf(null)
        private set
    var switchFrom by mutableIntStateOf(0)
        private set
    override var switchDir by mutableIntStateOf(1)
        private set
    var switchFade = 1f
        private set
    val motion = PivotMotion()
    var drags by mutableIntStateOf(0)
        private set
    var aimAt by mutableIntStateOf(0)
        private set
    var aim = 0
        private set
    var aimCut = 0
        private set
    var aimJump: JumpScroll? = null
        private set
    var aimU = 0f
        private set
    var jumpPick = -1
    var colPick = -1
    var hidden by mutableLongStateOf(-1L)
    val art = mutableStateMapOf<ArtKey, ImageBitmap?>()
    override var lists: List<ListTouch> = emptyList()
    val wake = Channel<Unit>(Channel.CONFLATED)

    override val kind: PageKind get() = set.kind(pivot)

    fun role(p: Int): Int = set.role(p)

    fun grid(p: Int): Boolean = p == set.grid

    fun leadRow(p: Int, r: Int): Boolean = panes[p].map?.isLead(r) == true

    fun gridRow(p: Int, r: Int): Boolean = grid(p) && !leadRow(p, r)

    val search: SearchQuery? = (st.set as? HubSet.Search)?.let { SearchQuery(it.query) }

    fun committed(): String = (set as? HubSet.Search)?.query.orEmpty()

    fun queryText(): String = search?.let { if (it.open) it.text else committed() }.orEmpty()

    fun openField() {
        val q = search ?: return
        if (q.open) return q.show()
        q.openField(committed())
    }

    fun typed(t: String) {
        val q = search ?: return
        q.text = t
        q.fresh = false
    }

    fun blur() {
        search?.shut(committed())
    }

    fun closeField(): Boolean {
        val q = search ?: return false
        q.shut(committed())
        return committed().isEmpty()
    }

    private fun swap(t: String) {
        st.set = HubSet.Search(t)
        for (p in panes.indices) panes[p].into(set.listing(p, 0L))
    }

    fun done(): Boolean {
        val q = search ?: return false
        val t = q.text
        if (searchBytes(t) < 1) return false
        val same = t == committed()
        if (!same) swap(t)
        q.shut(t)
        q.committed(true)
        return true
    }

    fun hits(p: Int): Int = panes[p].sections?.size ?: -1

    fun found(p: Int): String = if (search != null && panes[p].map != null && hits(p) == 0) "no " + set.pivots[p] + " found" else ""

    suspend fun landAll(v: LibraryView, rows: Int) {
        for (p in panes.indices) {
            if (panes[p].map == null || panes[p].gen != v.generation) load(v, p)
            show(v, p, 0, rows)
        }
    }

    fun land(now: Long): Boolean {
        val q = search ?: return false
        if (!q.landing || panes.any { it.map == null }) return false
        val to = panes.indices.firstOrNull { hits(it) > 0 } ?: 0
        select(to)
        pane.setFocus(0, 0, now, false)
        pane.col = 0
        pane.track.at(0)
        q.landing = false
        return true
    }

    override fun state(): List<Any>? = search?.let { listOf(committed()) }

    override fun restoreState(v: List<*>) {
        val q = search ?: return
        val t = v.firstOrNull() as? String ?: return
        if (t == committed()) return
        swap(t)
        q.shut(t)
    }

    fun line(p: Int): String = hubLine(set, p, scanning, found, songs, panes[p].map?.count ?: 0, generation)

    override val ready: Boolean
        get() {
            if (search != null && committed().isEmpty()) return true
            if (scanShown(scanning, scanSongs(generation, songs))) return true
            val pn = pane
            val m = pn.map ?: return false
            for (row in pn.track.first until m.count) {
                val i = m.indices(row, row)?.first ?: continue
                return pn.row(i) != null
            }
            return true
        }

    override fun poke() {
        wake.trySend(Unit)
    }

    fun animating(now: Long): Boolean {
        val pn = pane
        return switching(now) || motion.active(now) || pn.focus.animating(now) || pn.press.staleIn(pn.track.drawFirst, pn.track.rowAt(pn.track.q + pn.track.view - 1), pn.map?.cols ?: 1, now) || pn.glow.glow.moving(now) || pn.glow.back.moving(now) || fade.fading(now)
    }

    fun wakeAt(now: Long): Long? = listOfNotNull(pane.press.due(now), fade.wakeAt(now).takeIf { it >= 0 }).minOrNull()

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        fade.track(thumb, moving, now)
        val pn = pane
        for (g in listOf(pn.glow.glow, pn.glow.back)) {
            val i = pn.item(g.key ?: continue, 0)
            if (!pn.press.framed(i, now) && pn.press.lit(i, now) == 0f) g.clear()
        }
    }

    fun switching(now: Long): Boolean = switchAt?.let { now - it < pivotSwitchEnd() } == true

    override fun canSwitch(d: Int): Boolean = pivot + d in panes.indices

    override fun motionActive(now: Long): Boolean = motion.active(now)

    override fun haltList() {
        lists.getOrNull(pivot)?.halt()
    }

    override fun switchTo(p: Int, now: Long): Boolean {
        val to = p.coerceIn(0, panes.size - 1)
        if (to == pivot) return false
        val from = if (motion.dragging) motion.offset else 0f
        pane.press.reset()
        pane.glow.off()
        switchFrom = pivot
        switchDir = if (to > pivot) 1 else -1
        switchFade = fade.at(now)
        pivot = to
        fade.show(now)
        switchAt = now
        motion.begin(now, switchDir, from)
        return true
    }

    override fun land() {
        switchAt = null
        motion.end()
    }

    fun select(p: Int) {
        pivot = p.coerceIn(0, panes.size - 1)
        land()
    }

    override fun drag(dxU: Float, now: Long) {
        if (!motion.dragging && motion.active(now)) land()
        motion.drag(dxU)
        drags++
    }

    override fun release(now: Long) {
        if (!motion.dragging) return
        motion.release(now)
        drags++
    }

    override fun unpress(now: Long) = pane.unpress(now)

    override fun dropUnpress() = panes.forEach { it.focus.dropUnpress() }

    override fun dragX(now: Long, ppu: Float): Int = if (switching(now)) 0 else motion.dx(now, ppu)

    override fun toTop(now: Long): Boolean {
        val pn = pane
        val m = pn.map ?: return false
        if (m.count == 0) return false
        val lead = if (onLabel(pivot, 0)) 1 else 0
        if (pn.focus.row == lead && pn.col == 0 && pn.track.q == 0) return false
        pn.setFocus(lead, 0, now, false)
        aimTo(0)
        return true
    }

    fun aimTo(q: Int) {
        aimJump = null
        aim = q
        aimAt++
    }

    fun jumpAim(j: JumpScroll, u: Float) {
        aimJump = j
        aimU = u
        aim = Math.round(j.to * u)
        aimAt++
    }

    fun stopAim() {
        aimCut++
    }

    fun onLabel(p: Int, row: Int): Boolean = role(p) == HUB_FOLDERS && panes[p].cell(row, 0) == LABEL_CELL

    fun jumpTo(section: Int, now: Long): Int? {
        val pn = pane
        val m = pn.map ?: return null
        val row = m.rowOf(section)
        if (row < 0) return null
        pn.setFocus(row, m.colOf(section), now, false)
        return row
    }

    fun focusSection(): Int {
        val pn = pane
        val m = pn.map ?: return -1
        if (m.count == 0) return -1
        return m.sectionAt(pn.focus.row, pn.col, pn.sections ?: ByteArray(0))
    }

    fun avail(): BooleanArray = pane.map?.avail() ?: BooleanArray(Section.count)

    fun revised(lists: Long, favs: Long): Boolean {
        if (set != HubSet.Music) return false
        val pn = panes[HUB_PLAYLISTS]
        if (pn.map == null || pn.gen < 0 || (lists == pn.lists && favs == pn.favs)) return false
        pn.stale()
        return true
    }

    fun act(p: Int, row: Int, col: Int, ring: Boolean): HubAct {
        val pn = panes[p]
        val v = pn.cell(row, col)
        val l = letterOf(v)
        if (l >= 0) return HubAct.Jump(l)
        if (v == QUERY_CELL) return HubAct.Query
        if (v == NO_CELL || v == LABEL_CELL) return HubAct.None
        val k = role(p)
        if (k == HUB_PLAYLISTS) {
            return when (v) {
                NOW_CELL -> HubAct.NowPlaying
                FAVS_CELL -> favsPlay(pn.favorites)?.let { HubAct.Play(it, -1) } ?: HubAct.None
                else -> pn.row(v)?.let(::listPlay)?.let { HubAct.Play(it, -1) } ?: HubAct.None
            }
        }
        val r = pn.row(v) ?: return HubAct.None
        return when (k) {
            HUB_ALBUMS -> HubAct.Album(r.id)
            HUB_ARTISTS -> if (ring) HubAct.Play(Listing.ArtistTracks(r.id), 0) else HubAct.Artist(r.id)
            HUB_SONGS -> HubAct.Play(pn.listing, v)
            HUB_GENRES -> HubAct.Play(Listing.GenreTracks(r.id), -1)
            else -> if (r.kind == Kind.Folder) HubAct.Enter(r.id, r.title) else HubAct.Play(Listing.FolderTracks(folder), folderStart(pn, v))
        }
    }

    fun target(p: Int, row: Int, col: Int): SheetTarget? {
        if (scanShown(scanning, scanSongs(generation, songs))) return null
        val pn = panes[p]
        val v = pn.cell(row, col)
        if (letterOf(v) >= 0 || v == NO_CELL || v == LABEL_CELL || v == NOW_CELL) return null
        val g = pn.gen
        val k = role(p)
        val from = set.from
        if (k == HUB_PLAYLISTS && v == FAVS_CELL) return favsTarget(pn.favorites, g, from)
        val r = pn.row(v) ?: return null
        return when (k) {
            HUB_ALBUMS -> SheetTarget(SheetKind.Album, r.title, Listing.AlbumTracks(r.id), g, pin = Kind.Album, id = r.id, from = from)
            HUB_ARTISTS -> SheetTarget(SheetKind.Artist, r.title, Listing.ArtistTracks(r.id), g, pin = Kind.Artist, id = r.id, from = from)
            HUB_SONGS -> SheetTarget(SheetKind.Song, r.title, pn.listing, g, v, r.id, Kind.Song, r.id, from = from)
            HUB_GENRES -> SheetTarget(SheetKind.Genre, r.title, Listing.GenreTracks(r.id), g, id = r.id, from = SheetFrom.Hub)
            HUB_PLAYLISTS -> listTarget(r, g, SheetFrom.Hub)
            else -> if (r.kind == Kind.Folder) {
                SheetTarget(SheetKind.Folder, r.title, Listing.FolderTracks(r.id), g, id = r.id, from = SheetFrom.Hub)
            } else {
                SheetTarget(SheetKind.Song, r.title, Listing.FolderTracks(folder), g, folderStart(pn, v), r.id, Kind.Song, r.id, from = SheetFrom.Hub)
            }
        }
    }

    private fun folderStart(pn: HubPane, index: Int): Int {
        val rows = pn.whole ?: return 0
        val folders = rows.filter { it.kind == Kind.Folder }
        return folders.sumOf { it.count ?: 0 } + index - folders.size
    }

    fun enter(id: Long, name: String) {
        val pn = panes[HUB_FOLDERS]
        val r = pn.row(pn.cell(pn.focus.row, 0))
        st.levels += FolderLevel(st.folder, pn.track.q, r?.id ?: -1L, r?.kind, name)
        st.folder = id
        st.settle = null
        st.entered = true
        pn.into(Listing.Folder(id))
    }

    fun up(): Boolean {
        if (set != HubSet.Music || pivot != HUB_FOLDERS || atRoot) return false
        land()
        val at = st.levels.removeLastOrNull()
        st.settleFrom = st.folder
        st.folder = at?.parent ?: st.base
        st.settle = at ?: FolderLevel(st.folder, -1, -1L, null, "")
        st.entered = false
        panes[HUB_FOLDERS].into(Listing.Folder(st.folder))
        return true
    }

    fun settleFolder(view: Int, pitch: Int, now: Long): Int? {
        val pn = panes[HUB_FOLDERS]
        if (pn.map == null) return null
        val at = st.settle
        st.settle = null
        val lead = if (atRoot) 0 else 1
        if (st.entered) {
            st.entered = false
            pn.setFocus(lead, 0, now, false)
            return max(0, (pn.focus.row + 1) * pitch - view)
        }
        if (at == null) return null
        val back = if (at.kind == null) -1 else rowOfId(pn, at.key, at.kind)
        if (at.offset >= 0 && back >= 0 && back.toLong() * pitch >= at.offset && (back + 1).toLong() * pitch <= at.offset + view) {
            pn.setFocus(back, 0, now, false)
            return at.offset
        }
        val row = rowOfId(pn, st.settleFrom, Kind.Folder).takeIf { it >= 0 } ?: lead
        pn.setFocus(row, 0, now, false)
        return max(0, (pn.focus.row + 1) * pitch - view)
    }

    private fun rowOfId(pn: HubPane, id: Long, kind: Kind): Int {
        val rows = pn.whole ?: return -1
        return pn.map?.rowOfIndex(rows.indexOfFirst { it.id == id && it.kind == kind }) ?: -1
    }

    private suspend fun base(v: LibraryView) {
        if (!atRoot) return
        val roots = v.page(Listing.Folder(0L), 0, 1)
        val b = roots.rows.firstOrNull()?.takeIf { roots.total == 1 && it.kind == Kind.Folder }?.id ?: 0L
        if (b == st.base) return
        st.base = b
        st.folder = b
        panes[HUB_FOLDERS].into(Listing.Folder(b), bump = false)
    }

    suspend fun load(v: LibraryView, p: Int) {
        val pn = panes[p]
        val k = role(p)
        if (set == HubSet.Music && k == HUB_FOLDERS) base(v)
        val l = pn.listing
        when (if (set == HubSet.Music) k else -1) {
            HUB_FOLDERS -> {
                val total = v.page(l, 0, 0).total
                val rows = v.page(l, 0, total).rows
                val s = v.sections(l)
                val n = rows.count { it.kind == Kind.Folder }
                pn.whole = rows
                pn.folders = n
                pn.sections = s
                pn.map = hubMap(s, 1, set.lead(p, atRoot), n)
            }
            HUB_PLAYLISTS -> {
                pn.lists = revisions.first
                pn.favs = revisions.second
                val total = v.page(l, 0, 0).total
                pn.favorites = v.page(Listing.Favorites, 0, 0).total
                pn.sections = ByteArray(total)
                pn.map = hubMap(ByteArray(total), 1, set.lead(p, atRoot), 0)
            }
            else -> {
                val s = v.sections(l)
                pn.sections = s
                pn.map = hubMap(s, if (grid(p)) cols else 1, set.lead(p, atRoot), if (k == HUB_GENRES) 0 else s.size)
            }
        }
        pn.gen = v.generation
    }

    suspend fun migrate(v: LibraryView, p: Int, first: Int, caps: LibraryCaps = LibraryCaps()): Int? {
        val pn = panes[p]
        val old = pn.map ?: return null
        pn.pending = null
        val hit = (max(0, first) until old.count).firstNotNullOfOrNull { row -> old.indexAt(row).takeIf { it >= 0 }?.let { i -> pn.row(i)?.let { i to it } } }
        pn.thumbs.clear()
        pn.firsts.clear()
        load(v, p)
        val (i, r) = hit ?: return null
        val whole = pn.whole
        val at = if (whole != null) {
            whole.indexOfFirst { it.kind == r.kind && it.id == r.id }.takeIf { it >= 0 } ?: i.coerceIn(0, max(0, whole.size - 1))
        } else {
            val moved = pn.paging.migrate(v, r.kind, r.id, i, caps)
            if (moved.busy) pn.pending = Anchor(r.kind, r.id, 0f, moved.at)
            moved.at
        }
        return pn.map?.rowOfIndex(at)?.takeIf { it >= 0 }
    }

    fun anchorAt(p: Int, t: RowTrack, ppu: Float): Anchor? {
        val pn = panes[p]
        val m = pn.map ?: return null
        val q = t.q
        val row = (t.rowAt(q) until m.count).firstOrNull { pn.row(m.indexAt(it)) != null } ?: return null
        val r = pn.row(m.indexAt(row)) ?: return null
        return Anchor(r.kind, r.id, (q - t.topOf(row)) / ppu, m.indexAt(row))
    }

    suspend fun restoreRow(v: LibraryView, p: Int, caps: LibraryCaps = LibraryCaps()): Int? {
        if (!restoring) return null
        restoring = false
        val a = anchor ?: return null
        val l = panes[p].listing
        suspend fun saved(): Int? = a.index.takeIf { it >= 0 }?.coerceAtMost(max(0, v.page(l, 0, 0).total - 1))
        val i = if (caps.indexOf(l)) {
            try {
                v.indexOf(l, a.kind, a.id) ?: return null
            } catch (e: Busy) {
                val at = saved()
                panes[p].pending = a
                at ?: return null
            }
        } else {
            saved() ?: return null
        }
        return panes[p].map?.rowOfIndex(i)?.takeIf { it >= 0 }
    }

    fun hold(p: Int, on: LibraryMark? = null) {
        val pn = panes[p]
        if (pn.pending == null) return
        pn.heldAt = pn.track.q
        pn.heldOn = on
    }

    fun kept(p: Int): Boolean {
        val pn = panes[p]
        if (pn.track.q != pn.heldAt) pn.pending = null
        return pn.pending != null
    }

    suspend fun retry(v: LibraryView, p: Int): Int? {
        val pn = panes[p]
        if (!kept(p)) return null
        val a = pn.pending ?: return null
        if (v.generation != pn.gen) {
            pn.pending = null
            return null
        }
        val i = try {
            v.indexOf(pn.listing, a.kind, a.id)
        } catch (e: Busy) {
            return null
        }
        if (v.generation != pn.gen || pn.pending !== a) return null
        val row = i?.let { pn.map?.rowOfIndex(it) }?.takeIf { it >= 0 }
        if (row == null) pn.pending = null
        return row
    }

    fun regrid(c: Int) {
        if (c == cols) return
        cols = c
        val pn = panes[set.grid]
        val s = pn.sections ?: return
        pn.map = hubMap(s, c, set.lead(set.grid, atRoot))
    }

    suspend fun show(v: LibraryView, p: Int, firstRow: Int, lastRow: Int) {
        val pn = panes[p]
        if (pn.whole != null) return
        val m = pn.map ?: return
        val r = m.indices(firstRow, lastRow) ?: return
        pn.paging.show(v, r.first, r.last)
    }

    suspend fun thumbs(v: LibraryView, p: Int, firstRow: Int, lastRow: Int, most: Int) {
        if (!hubThumbs(role(p))) return
        val pn = panes[p]
        val m = pn.map ?: return
        for (row in max(0, firstRow)..min(m.count - 1, lastRow)) {
            val r = pn.row(m.indexAt(row)) ?: continue
            if (r.id in pn.thumbs) continue
            val l = if (role(p) == HUB_ARTISTS) Listing.ArtistAlbums(r.id) else Listing.GenreAlbums(r.id)
            val rows = v.page(l, 0, most).rows
            pn.readThumbs(r.id, rows.map { it.art }, rows.firstOrNull()?.id ?: -1L)
        }
    }

    suspend fun art(v: LibraryView, cache: Art, ref: ArtRef, px: Int) {
        val k = ArtKey.Cover(ref, px)
        if (k in art) return
        val b = try {
            cache.cover(ref, px) { v.art(ref, px) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        art[k] = b?.asImageBitmap()
    }

    suspend fun play(act: HubAct.Play, src: MusicSource) {
        val l = act.listing
        if (l !is Listing.Search) return src.player.play(l, pane.gen, act.index, 0L)
        val v = src.library.view(pane.gen)
        val ids = v.page(l, 0, v.page(l, 0, 0).total).rows.map { it.id }
        src.player.play(ids, act.index, 0L)
    }

    suspend fun play(v: LibraryView, state: PlayerState) {
        val t = state.track
        playing = HubPlaying(t, t?.let { v.trackIds(it) }, state.origin, state.queueSize)
    }

    fun keepArt(keys: Set<ArtKey>) {
        for (k in art.keys.toList()) if (k !in keys) art.remove(k)
    }

    override fun drop() {
        lists = emptyList()
        art.clear()
    }
}

fun musicModel(entry: Long, page: MusicPage, hub: HubState = HubState()): PageModel = when (page) {
    MusicPage.Hub -> {
        hub.open()
        HubModel(entry, MusicPage.Hub, hub)
    }
    MusicPage.Queue -> QueueModel(entry)
    is MusicPage.Album -> AlbumModel(entry, page.id)
    is MusicPage.AlbumCol -> AlbumColModel(entry, page.letter)
    is MusicPage.Artist -> ArtistModel(entry, page.id)
    MusicPage.Playlists -> PlaylistsModel(entry)
    is MusicPage.ListView -> ListViewModel(entry, page.listing)
    is MusicPage.Search -> HubModel(entry, page, HubState(HubSet.Search(page.query)))
    MusicPage.History -> HistoryModel(entry)
    MusicPage.NowPlaying -> PageModel(entry, page)
}
