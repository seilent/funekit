package net.seilent.funekit.screens.music.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.Stale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.Paging
import net.seilent.funekit.screens.music.hub.FAVORITES
import net.seilent.funekit.screens.music.hub.HubAct
import net.seilent.funekit.screens.music.hub.hubScrollInto
import net.seilent.funekit.screens.music.hub.songsLabel
import net.seilent.funekit.screens.music.overlay.MOVE_DOWN
import net.seilent.funekit.screens.music.overlay.MOVE_UP
import net.seilent.funekit.screens.music.overlay.REMOVE
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import kotlin.math.max

data class ViewKey(val revision: Long, val generation: Long)

class ListViewModel(entry: Long, val listing: Listing) : PageModel(entry, MusicPage.ListView(listing)) {
    val paging = Paging(listing)
    val pane = ListPane().also { it.paging = paging }
    var head: Row? = null
    val track: RowTrack get() = pane.track
    var total by mutableIntStateOf(0)
        private set
    var loaded by mutableStateOf(false)
        private set
    var current by mutableStateOf<Long?>(null)
    var seen: ViewKey? = null
        private set
    override var lists: List<ListTouch>
        get() = pane.lists
        set(v) {
            pane.lists = v
        }

    val title: String get() = head?.title ?: if (listing == Listing.Favorites) FAVORITES else ""

    val writable: Boolean get() = listing == Listing.Favorites || head?.writable == true

    val revision: Long get() = paging.window?.revision ?: -1L

    val generation: Long get() = paging.window?.generation ?: 0L

    override val ready: Boolean get() = loaded && (total == 0 || row(track.first) != null)

    fun poke() = pane.poke()

    fun row(i: Int): Row? = pane.row(i)

    fun rowModel(i: Int): RowModel? {
        val r = row(i) ?: return null
        return RowModel(primary = r.title, secondary = r.sub, number = (i + 1).toString(), libSub = true, accent = current != null && r.id == current)
    }

    fun count(): String? = if (!loaded || empty()) null else songsLabel(total)

    fun empty(): Boolean = loaded && total == 0

    fun look(i: Int, now: Long): ListLook = pane.look(i, now, false, Tok.type_listPrimary_alpha, Tok.type_listPrimary_alphaDim)

    fun animating(now: Long): Boolean = pane.animating(now)

    fun wakeAt(now: Long): Long? = pane.wakeAt(now)

    fun tick(now: Long, thumb: Boolean, moving: Boolean) = pane.tick(now, thumb, moving)

    suspend fun show(v: LibraryView, first: Int, last: Int) {
        paging.show(v, max(0, first), last)
        val n = paging.window?.total ?: 0
        if (!loaded) {
            loaded = true
            total = n
            pane.go(n, row = 0)
            if (n > 0) pane.focus.set(0, n, 0L, false)
            poke()
        }
    }

    fun stale(k: ViewKey): Boolean = seen != null && k != seen

    fun note(k: ViewKey) {
        if (seen == null) seen = k
    }

    suspend fun refresh(v: LibraryView, k: ViewKey, now: Long) {
        seen = k
        paging.reload(v)
        if (paging.window == null) paging.show(v, 0, 0)
        val n = paging.window?.total ?: 0
        loaded = true
        total = n
        pane.reset()
        if (n == 0) {
            pane.go(0)
            poke()
            return
        }
        val f = pane.focus.row.coerceIn(0, n - 1)
        pane.focus.set(f, n, now, false)
        pane.go(n, px = hubScrollInto(f * pane.pitch, pane.pitch, track.q, pane.view))
        poke()
    }

    fun act(i: Int): HubAct = if (row(i) != null) HubAct.Play(listing, i) else HubAct.None

    fun target(i: Int): SheetTarget? {
        val r = row(i) ?: return null
        return SheetTarget(
            SheetKind.Entry, r.title, listing, generation, i, r.id, id = (listing as? Listing.PlaylistTracks)?.playlist ?: 0L,
            slot = i, size = total, revision = revision, writable = writable, from = SheetFrom.View,
        )
    }

    suspend fun verb(word: String, t: SheetTarget, src: MusicSource, now: Long) {
        val i = t.slot
        val rev = t.revision
        if (rev < 0 || i < 0) return
        val to = when (word) {
            REMOVE -> i
            MOVE_UP -> i - 1
            MOVE_DOWN -> i + 1
            else -> return
        }
        try {
            val l = listing
            if (l is Listing.PlaylistTracks) {
                if (word == REMOVE) src.playlists.remove(l.playlist, i, rev) else src.playlists.move(l.playlist, i, to, rev)
            } else {
                if (word == REMOVE) src.favorites.remove(i, rev) else src.favorites.move(i, to, rev)
            }
        } catch (_: Stale) {
            return
        }
        val key = ViewKey(src.revisions(listing)?.first() ?: rev, src.library.generation.value)
        if (seen != key) src.viewed { v -> refresh(v, key, now) }
        if (total > 0) pane.place(to.coerceIn(0, total - 1), total, now, true)
    }

    override fun drop() {
        lists = emptyList()
    }
}
