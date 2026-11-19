package net.seilent.funekit.fake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import net.seilent.funekit.api.Favorites
import net.seilent.funekit.api.Stale
import net.seilent.funekit.api.TrackSet

class FakeFavorites internal constructor(private val store: Store, private val library: FakeLibrary, private val latencyMs: Long) : Favorites {
    override val revision: StateFlow<Long> = store.favoritesRevision

    override suspend fun has(track: Long): Boolean {
        delay(latencyMs)
        return track in store.favorites
    }

    override suspend fun toggle(track: Long): Boolean {
        delay(latencyMs)
        if (track !in store.favorites && library.track(track) == null) return false
        return store.toggleFavorite(track)
    }

    override suspend fun add(tracks: TrackSet): Int {
        delay(latencyMs)
        val ids = library.ids(tracks)
        return store.updateFavorites { old ->
            val batch = ids.distinct().filter { it !in old }
            batch + old
        }
    }

    override suspend fun remove(index: Int, revision: Long) {
        delay(latencyMs)
        store.updateFavorites { old ->
            val at = shown(old, revision)
            if (index in at.indices) old.filterIndexed { i, _ -> i != at[index] } else old
        }
    }

    override suspend fun move(from: Int, to: Int, revision: Long) {
        delay(latencyMs)
        store.updateFavorites { old ->
            val at = shown(old, revision)
            if (from !in at.indices || to !in at.indices || from == to) return@updateFavorites old
            val ids = at.map { old[it] }.toMutableList()
            ids.add(to, ids.removeAt(from))
            val out = old.toMutableList()
            at.forEachIndexed { k, i -> out[i] = ids[k] }
            out
        }
    }

    private fun shown(old: List<Long>, revision: Long): List<Int> {
        if (store.favoritesRevision.value != revision) throw Stale(revision)
        return old.indices.filter { library.track(old[it]) != null }
    }
}
