package net.seilent.funekit.screens.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import kotlin.math.max
import kotlin.math.min

private const val WINDOW = 64

class Window(val generation: Long, val revision: Long, val offset: Int, val total: Int, val rows: List<Row>) {
    val end: Int get() = offset + rows.size

    fun row(i: Int): Row? = rows.getOrNull(i - offset)

    fun has(first: Int, last: Int): Boolean = first >= offset && (last < end || end >= total)
}

class Paging(val listing: Listing, private val ahead: Int = WINDOW) {
    var window: Window? by mutableStateOf(null)
        private set

    private var asked = 0
    private var from = 0
    private var to = 0

    private suspend fun whole(view: LibraryView, lo: Int, hi: Int): Window {
        val p = view.page(listing, lo, hi - lo)
        return Window(p.generation, p.revision, p.offset, p.total, p.rows)
    }

    private fun ask(lo: Int, hi: Int): Int {
        from = lo
        to = hi
        return ++asked
    }

    private fun take(mine: Int, w: Window) {
        if (mine == asked) window = w
    }

    suspend fun show(view: LibraryView, first: Int, last: Int) {
        val lo = max(0, first - ahead)
        val hi = last + 1 + ahead
        val w = window
        if (w != null && w.generation == view.generation && w.has(first, last)) return
        val mine = ask(lo, hi)
        if (w == null || w.generation != view.generation || hi < w.offset || lo > w.end) {
            take(mine, whole(view, lo, hi))
            return
        }
        val before = if (lo < w.offset) view.page(listing, lo, w.offset - lo) else null
        val after = if (hi > w.end && w.end < w.total) view.page(listing, w.end, hi - w.end) else null
        if (mine != asked) return
        val pages = listOfNotNull(before, after)
        if (pages.any { it.generation != w.generation || it.revision != w.revision }) {
            take(mine, whole(view, lo, hi))
            return
        }
        val rows = (before?.rows ?: emptyList()) + w.rows + (after?.rows ?: emptyList())
        take(mine, Window(w.generation, w.revision, before?.offset ?: w.offset, pages.lastOrNull()?.total ?: w.total, rows))
    }

    suspend fun reload(view: LibraryView) {
        if (asked == 0) return
        var lo = from
        var hi = to
        val w = window
        if (w != null && hi >= w.offset && lo <= w.end) {
            lo = min(lo, w.offset)
            hi = max(hi, w.end)
        }
        val mine = ask(lo, hi)
        take(mine, whole(view, lo, hi))
    }

    suspend fun follow(revisions: Flow<Long>, view: suspend () -> LibraryView) {
        revisions.drop(1).collect { reload(view()) }
    }

    suspend fun migrate(view: LibraryView, kind: Kind, id: Long, index: Int): Int {
        val mine = asked
        val at = view.indexOf(listing, kind, id) ?: index.coerceIn(0, max(0, view.page(listing, 0, 0).total - 1))
        if (mine != asked) return at
        window = null
        show(view, at, at)
        return at
    }
}
