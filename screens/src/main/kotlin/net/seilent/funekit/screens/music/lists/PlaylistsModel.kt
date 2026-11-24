package net.seilent.funekit.screens.music.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.hub.HubAct
import net.seilent.funekit.screens.music.hub.cardLine
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetTarget
import kotlin.math.max

data class PlaylistsKey(val lists: Long, val favs: Long, val queue: Int, val origin: Listing?, val generation: Long) {
    constructor(lists: Long, favs: Long, st: PlayerState, generation: Long) : this(lists, favs, st.queueSize, st.origin, generation)
}

class PlaylistsModel(entry: Long) : PageModel(entry, MusicPage.Playlists) {
    val pane = ListPane().also { p -> p.present = { it < rows.size } }
    var rows by mutableStateOf(PlaylistRows())
        private set
    var loaded by mutableStateOf<PlaylistsKey?>(null)
        private set
    private var keep = 0L
    private var keepOff = 0f
    val track: RowTrack get() = pane.track
    val size: Int get() = if (loaded == null || status() != null) 0 else rows.size
    override var lists: List<ListTouch>
        get() = pane.lists
        set(v) {
            pane.lists = v
        }

    override val ready: Boolean get() = loaded != null || status() != null

    fun status(): String? = cardLine(card).ifEmpty { null }

    fun poke() = pane.poke()

    fun rowModel(i: Int): RowModel? = rows.model(i)

    fun count(): String = rows.count()

    fun look(i: Int, now: Long, focusShown: Boolean): ListLook = pane.look(i, now, focusShown, Tok.type_listPlaylist_alpha, Tok.type_listPlaylist_alphaDim)

    fun animating(now: Long): Boolean = pane.animating(now)

    fun wakeAt(now: Long): Long? = pane.wakeAt(now)

    fun tick(now: Long, thumb: Boolean, moving: Boolean) = pane.tick(now, thumb, moving)

    fun stale(k: PlaylistsKey): Boolean = k != loaded

    suspend fun load(v: LibraryView, k: PlaylistsKey, now: Long) {
        val total = v.page(Listing.Playlists, 0, 0).total
        val lists = v.page(Listing.Playlists, 0, total).rows
        val favs = v.page(Listing.Favorites, 0, 0).total
        val first = loaded == null
        val key = if (first) keep else rows.key(pane.focus.row)
        val screen = pane.focus.row * pane.pitch - track.q
        rows = PlaylistRows(k.queue, favs, lists, k.origin, v.generation)
        loaded = k
        val n = rows.size
        val at = rows.find(key)
        if (first) {
            if (at > 0) {
                pane.focus.set(at, n, now, false)
                pane.go(n, px = px(keepOff, pane.ppu))
            } else {
                pane.focus.set(0, n, now, false)
                pane.go(n, row = 0)
            }
        } else {
            pane.focus.set(if (at >= 0) at else pane.focus.row.coerceIn(0, n - 1), n, now, false)
            if (at >= 0) pane.go(n, px = max(0, at * pane.pitch - screen)) else pane.go(n)
        }
        pane.reset()
        poke()
    }

    fun act(row: Int): HubAct = when {
        row == 0 -> HubAct.NowPlaying
        row >= size -> HubAct.None
        else -> rows.play(row)?.let { HubAct.Play(it, -1) } ?: HubAct.None
    }

    fun target(row: Int): SheetTarget? = rows.target(row, SheetFrom.Playlists)

    override fun state(): List<Any> = listOf(rows.key(pane.focus.row), if (pane.ppu > 0f) track.q / pane.ppu else 0f)

    override fun restoreState(v: List<*>) {
        keep = v.getOrNull(0) as? Long ?: 0L
        keepOff = v.getOrNull(1) as? Float ?: 0f
    }

    override fun drop() {
        lists = emptyList()
    }
}
