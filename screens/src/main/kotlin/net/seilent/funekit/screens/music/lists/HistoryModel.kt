package net.seilent.funekit.screens.music.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.motion.PivotMotion
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.px
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.TrackIds
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PivotPage
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.pivotSwitchEnd
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.queue.QUEUE_EMPTY
import kotlin.math.max

const val HISTORY_RECENT = 0
const val HISTORY_NEW = 1
const val NOTHING_PLAYED = "nothing played yet"

sealed interface HistoryAct {
    data object None : HistoryAct

    data class Song(val id: Long) : HistoryAct

    data class Album(val listing: Listing, val index: Int) : HistoryAct
}

class HistoryModel(entry: Long) : PivotPage(entry, MusicPage.History) {
    val wake = Channel<Unit>(Channel.CONFLATED)
    val panes = listOf(ListPane(wake), ListPane(wake))
    val pane: ListPane get() = panes[pivot]
    var current by mutableStateOf<Long?>(null)
    var playing by mutableStateOf<TrackIds?>(null)
    var generation = 0L
        private set
    var seen: ViewKey? = null
        private set
    var loads by mutableIntStateOf(0)
        private set
    val motion = PivotMotion()
    var switchAt: Long? by mutableStateOf(null)
        private set
    override var switchDir by mutableIntStateOf(1)
        private set
    var switchFrom by mutableIntStateOf(0)
        private set
    var switchFade = 1f
    private var kept: List<*>? = null
    override val lists: List<ListTouch> get() = panes.flatMap { it.lists }

    init {
        panes[HISTORY_RECENT].present = { it < count(HISTORY_RECENT) }
        panes[HISTORY_NEW].present = { it < count(HISTORY_NEW) }
    }

    override fun poke() {
        wake.trySend(Unit)
    }

    fun rows(p: Int): List<Row>? = panes[p].whole

    fun count(p: Int): Int = rows(p)?.size ?: 0

    override val ready: Boolean get() = rows(pivot) != null

    fun empty(p: Int): String? = if (rows(p)?.isEmpty() == true) (if (p == HISTORY_RECENT) NOTHING_PLAYED else QUEUE_EMPTY) else null

    fun rowModel(p: Int, i: Int): RowModel? {
        val r = rows(p)?.getOrNull(i) ?: return null
        val accent = if (p == HISTORY_RECENT) current != null && r.id == current else playing?.album == r.id
        return RowModel(primary = r.title, secondary = r.sub, accent = accent)
    }

    fun look(p: Int, i: Int, now: Long): ListLook = panes[p].look(i, now, false, Tok.type_listPrimary_alpha, Tok.type_listPrimary_alphaDim)

    private fun key(p: Int, i: Int): Long = rows(p)?.getOrNull(i)?.id ?: -1L

    fun stale(k: ViewKey): Boolean = seen != null && k != seen

    suspend fun load(v: LibraryView, k: ViewKey, now: Long) {
        val first = seen == null
        val cap = Tok.history_rows.toInt()
        for ((p, l) in listOf(Listing.History, Listing.NewAlbums).withIndex()) {
            val pn = panes[p]
            val keep = key(p, pn.focus.row)
            val screen = pn.focus.row * pn.pitch - pn.track.q
            val total = v.page(l, 0, 0).total
            val rows = v.page(l, 0, total).rows.take(cap)
            pn.whole = rows
            val n = rows.size
            if (first) {
                pn.go(n, row = 0)
                if (n > 0) pn.focus.set(0, n, now, false)
            } else {
                val at = rows.indexOfFirst { it.id == keep }
                if (n > 0) pn.focus.set(if (at >= 0) at else pn.focus.row.coerceIn(0, n - 1), n, now, false)
                if (at >= 0) pn.go(n, px = max(0, at * pn.pitch - screen)) else pn.go(n)
            }
            pn.reset()
        }
        generation = v.generation
        seen = k
        loads++
        if (first) restoreAt(now)
        poke()
    }

    fun act(i: Int): HistoryAct {
        val r = rows(pivot)?.getOrNull(i) ?: return HistoryAct.None
        return if (pivot == HISTORY_RECENT) HistoryAct.Song(r.id) else HistoryAct.Album(Listing.AlbumTracks(r.id), -1)
    }

    fun target(i: Int): SheetTarget? {
        val r = rows(pivot)?.getOrNull(i) ?: return null
        return if (pivot == HISTORY_RECENT) {
            SheetTarget(SheetKind.Song, r.title, null, generation, 0, r.id, Kind.Song, r.id, from = SheetFrom.History)
        } else {
            SheetTarget(SheetKind.Album, r.title, Listing.AlbumTracks(r.id), generation, pin = Kind.Album, id = r.id, from = SheetFrom.History)
        }
    }

    fun animating(now: Long): Boolean = switching(now) || motion.active(now) || panes.any { it.animating(now) }

    fun wakeAt(now: Long): Long? = panes.mapNotNull { it.wakeAt(now) }.minOrNull()

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        for ((p, pn) in panes.withIndex()) pn.tick(now, thumb && p == pivot, moving && p == pivot)
    }

    fun switching(now: Long): Boolean = switchAt?.let { now - it < pivotSwitchEnd() } == true

    fun thumbAlpha(now: Long): Float {
        if (switchAt == null || !switching(now)) return pane.fade.at(now)
        return (if (motion.thumbIn(now)) pane.fade.at(now) else switchFade) * motion.thumbAlpha(now)
    }

    fun side(now: Long): Int {
        if (switchAt == null || !switching(now)) return pivot
        return if (motion.entering(now)) pivot else switchFrom
    }

    override fun canSwitch(d: Int): Boolean = pivot + d in MusicPage.History.pivots.indices

    override fun motionActive(now: Long): Boolean = motion.active(now)

    override fun haltList() {
        pane.lists.firstOrNull()?.halt()
    }

    override fun switchTo(p: Int, now: Long): Boolean {
        val to = p.coerceIn(0, MusicPage.History.pivots.size - 1)
        if (to == pivot) return false
        val from = if (motion.dragging) motion.offset else 0f
        pane.reset()
        switchFrom = pivot
        val dir = if (to > pivot) 1 else -1
        switchDir = dir
        switchFade = pane.fade.at(now)
        pivot = to
        pane.fade.show(now)
        switchAt = now
        motion.begin(now, dir, from)
        return true
    }

    override fun land() {
        switchAt = null
        motion.end()
    }

    override fun drag(dxU: Float, now: Long) {
        if (!motion.dragging && motion.active(now)) land()
        motion.drag(dxU)
    }

    override fun release(now: Long) {
        if (!motion.dragging) return
        motion.release(now)
    }

    override fun dragX(now: Long, ppu: Float): Int = if (switching(now)) 0 else motion.dx(now, ppu)

    override fun state(): List<Any>? {
        return panes.withIndex().flatMap { (p, pn) -> listOf(pn.focus.row, rows(p)?.getOrNull(pn.focus.row)?.id ?: -1L, if (pn.ppu > 0f) pn.track.q / pn.ppu else 0f) }
    }

    override fun restoreState(v: List<*>) {
        kept = v
    }

    private fun restoreAt(now: Long) {
        val v = kept ?: return
        kept = null
        val f = v.iterator()
        for ((p, pn) in panes.withIndex()) {
            val row = (if (f.hasNext()) f.next() else null) as? Int ?: return
            val id = (if (f.hasNext()) f.next() else null) as? Long ?: return
            val off = (if (f.hasNext()) f.next() else null) as? Float ?: return
            val n = count(p)
            if (n == 0 || pn.ppu <= 0f) continue
            val at = rows(p)?.indexOfFirst { it.id == id }?.takeIf { it >= 0 } ?: row
            pn.go(n, px = px(off, pn.ppu))
            pn.focus.set(at.coerceIn(0, max(0, n - 1)), n, now, false)
        }
    }

    override fun drop() {
        for (p in panes) p.lists = emptyList()
    }
}
