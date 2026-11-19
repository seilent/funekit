package net.seilent.funekit.screens.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.api.Kind

const val HUB_ALBUMS = 0

data class Anchor(val kind: Kind, val id: Long, val off: Float, val index: Int = -1)

interface PageId

open class PageModel(val entry: Long, val page: PageId) {
    var pivot by mutableIntStateOf(0)
    var anchor: Anchor? = null

    open val kind: PageKind
        get() = when (page) {
            MusicPage.Hub -> if (pivot == HUB_ALBUMS) PageKind.Grid else PageKind.Hub
            is MusicPage.Search -> PageKind.Hub
            else -> PageKind.Plain
        }

    open val lists: List<ListTouch> get() = emptyList()

    open val ready: Boolean get() = true

    open fun cell(album: Long): Boolean = false

    open fun state(): List<Any>? = null

    open fun restoreState(v: List<*>) {}

    open fun drop() {}
}

class PageModels(private val make: (Long, PageId) -> PageModel = ::PageModel) {
    private val models = HashMap<Long, PageModel>()

    val entries: Set<Long> get() = models.keys

    operator fun get(entry: Long): PageModel? = models[entry]

    fun enter(entry: Long, page: PageId): PageModel {
        val m = models[entry]
        if (m != null && m.page == page) return m
        m?.drop()
        return make(entry, page).also { models[entry] = it }
    }

    fun restore(entry: Long, page: PageId, pivot: Int, anchor: Anchor?, state: List<*>? = null) {
        if (models[entry]?.page == page) return
        enter(entry, page).also {
            it.pivot = pivot
            it.anchor = anchor
            if (state != null) it.restoreState(state)
        }
    }

    fun retain(keep: Set<Long>) {
        for (e in models.keys - keep) drop(e)
    }

    fun drop(entry: Long) {
        models.remove(entry)?.drop()
    }
}
