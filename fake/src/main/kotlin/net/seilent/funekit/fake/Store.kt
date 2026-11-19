package net.seilent.funekit.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.seilent.funekit.api.Kind

internal data class Pin(val kind: Kind, val id: Long)

internal data class Queued(val ids: List<Long>, val revision: Long)

internal data class PlaylistDoc(
    val id: Long,
    val name: String,
    val tracks: List<Long>,
    val writable: Boolean,
    val revision: Long,
    val source: String? = null,
)

internal class Store(set: FakeSet) {
    private val _revision = MutableStateFlow(0L)
    private val _favorites = MutableStateFlow(0L)
    private val _playlists = MutableStateFlow(0L)
    private val seeded = set != FakeSet.Empty

    val revision: StateFlow<Long> = _revision.asStateFlow()
    val favoritesRevision: StateFlow<Long> = _favorites.asStateFlow()
    val playlistsRevision: StateFlow<Long> = _playlists.asStateFlow()

    @Volatile
    var pins: List<Pin> = if (seeded) Fixture.shelf(set).pins else emptyList()
        private set

    @Volatile
    var history: List<Long> = if (seeded) Fixture.shelf(set).history else emptyList()
        private set

    @Volatile
    var queue: Queued = Queued(emptyList(), 0)
        private set

    @Volatile
    var favorites: List<Long> = if (seeded) Fixture.shelf(set).favorites else emptyList()
        private set

    @Volatile
    var playlists: List<PlaylistDoc> = if (seeded) Fixture.shelf(set).playlists else emptyList()
        private set

    private var nextPlaylist = playlists.maxOfOrNull { it.id + 1 } ?: 1L

    fun queued(ids: List<Long>, revision: Long) {
        queue = Queued(ids, revision)
    }

    @Synchronized
    fun newPlaylist(): Long = nextPlaylist++

    @Synchronized
    fun updatePins(f: (List<Pin>) -> List<Pin>) {
        val next = f(pins)
        if (next == pins) return
        pins = next
        _revision.update { it + 1 }
    }

    @Synchronized
    fun played(track: Long) {
        history = listOf(track) + history.filter { it != track }
        _revision.update { it + 1 }
    }

    @Synchronized
    fun toggleFavorite(track: Long): Boolean {
        val on = track !in favorites
        favorites = if (on) listOf(track) + favorites else favorites - track
        _favorites.update { it + 1 }
        return on
    }

    @Synchronized
    fun updateFavorites(f: (List<Long>) -> List<Long>): Int {
        val next = f(favorites)
        if (next == favorites) return 0
        val added = next.size - favorites.size
        favorites = next
        _favorites.update { it + 1 }
        return added.coerceAtLeast(0)
    }

    @Synchronized
    fun updatePlaylists(f: (List<PlaylistDoc>, Long) -> List<PlaylistDoc>) {
        val next = _playlists.value + 1
        val docs = f(playlists, next)
        if (docs == playlists) return
        playlists = docs
        _playlists.value = next
    }
}
