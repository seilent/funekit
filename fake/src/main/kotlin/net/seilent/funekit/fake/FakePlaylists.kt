package net.seilent.funekit.fake

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Playlists
import net.seilent.funekit.api.Refused
import net.seilent.funekit.api.Stale
import net.seilent.funekit.api.TrackSet

class FakePlaylists internal constructor(private val store: Store, private val library: FakeLibrary) : Playlists {
    private val lock = Mutex()

    override val revision: StateFlow<Long> = store.playlistsRevision

    internal var deleted: suspend (Listing) -> Unit = {}

    override suspend fun create(tracks: TrackSet): Long? = lock.withLock {
        library.present()
        val ids = library.ids(tracks)
        if (ids.isEmpty()) return@withLock null
        val id = store.newPlaylist()
        store.updatePlaylists { docs, r ->
            val used = docs.map { it.name.lowercase() }.toSet()
            val n = generateSequence(1) { it + 1 }.first { "$NAME $it" !in used }
            docs + PlaylistDoc(id, "$NAME $n", ids, true, r)
        }
        id
    }

    override suspend fun add(id: Long, tracks: TrackSet) = lock.withLock {
        library.present()
        writable(id)
        val ids = library.ids(tracks)
        if (ids.isNotEmpty()) edit(id) { it + ids }
    }

    override suspend fun remove(id: Long, index: Int, revision: Long) = lock.withLock {
        library.present()
        writable(id)
        val at = shown(id, revision)
        if (index in at.indices) edit(id) { it.filterIndexed { i, _ -> i != at[index] } }
    }

    override suspend fun move(id: Long, from: Int, to: Int, revision: Long) = lock.withLock {
        library.present()
        writable(id)
        val at = shown(id, revision)
        if (from in at.indices && to in at.indices && from != to) {
            edit(id) { tracks ->
                val shownIds = at.map { tracks[it] }.toMutableList()
                shownIds.add(to, shownIds.removeAt(from))
                val out = tracks.toMutableList()
                at.forEachIndexed { k, i -> out[i] = shownIds[k] }
                out
            }
        }
    }

    override suspend fun delete(id: Long) {
        val gone = lock.withLock {
            library.present()
            writable(id)
            val before = store.playlists
            store.updatePlaylists { docs, _ -> docs.filterNot { it.id == id } }
            store.playlists != before
        }
        if (gone) deleted(Listing.PlaylistTracks(id))
    }

    private fun writable(id: Long) {
        if (store.playlists.firstOrNull { it.id == id }?.writable == false) throw Refused()
    }

    private fun shown(id: Long, revision: Long): List<Int> {
        val doc = store.playlists.firstOrNull { it.id == id } ?: return emptyList()
        if (library.listRevision(doc.revision) != revision) throw Stale(revision)
        return doc.tracks.indices.filter { library.track(doc.tracks[it]) != null }
    }

    private fun edit(id: Long, f: (List<Long>) -> List<Long>) {
        store.updatePlaylists { docs, r -> docs.map { if (it.id == id) it.copy(tracks = f(it.tracks), revision = r) else it } }
    }

    private companion object {
        const val NAME = "playlist"
    }
}
