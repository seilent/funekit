package net.seilent.funekit.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.seilent.funekit.api.engine.Root
import kotlin.reflect.KClass

enum class Kind { Artist, Album, Song, Genre, Folder, Playlist, Service }

sealed interface ArtRef {
    data class Embedded(val track: Long, val offset: Long, val length: Long) : ArtRef
    data class Cover(val track: Long, val name: String) : ArtRef
    data class Track(val track: Long, val stamp: Long = 0) : ArtRef
}

data class Row(
    val id: Long,
    val kind: Kind,
    val title: String,
    val sub: String,
    val art: ArtRef?,
    val no: Int? = null,
    val durationMs: Long? = null,
    val year: Int? = null,
    val album: String? = null,
    val count: Int? = null,
    val count2: Int? = null,
    val writable: Boolean = false,
)

data class Page(val generation: Long, val offset: Int, val total: Int, val rows: List<Row>, val revision: Long = 0)

sealed interface Listing {
    data object Artists : Listing
    data object Albums : Listing
    data object Songs : Listing
    data object Genres : Listing
    data object Playlists : Listing
    data class ArtistAlbums(val artist: Long) : Listing
    data class ArtistTracks(val artist: Long) : Listing
    data class AlbumTracks(val album: Long) : Listing
    data class GenreAlbums(val genre: Long) : Listing
    data class GenreTracks(val genre: Long) : Listing
    data class PlaylistTracks(val playlist: Long) : Listing
    data class Folder(val folder: Long = 0) : Listing
    data class FolderTracks(val folder: Long) : Listing
    data class Search(val query: String, val kind: Kind? = null) : Listing
    data object Favorites : Listing
    data object Pins : Listing
    data object History : Listing
    data object NewAlbums : Listing
    data object Queue : Listing
}

sealed interface TrackSet {
    data class Ids(val ids: List<Long>) : TrackSet
    data class Listed(val listing: Listing, val generation: Long) : TrackSet
}

data class TrackInfo(val path: String, val lines: List<String>)

data class TrackIds(val artists: List<Long>, val album: Long?, val genre: Long?, val folders: List<Long>)

data class ScanProgress(val running: Boolean, val found: Int, val failed: Boolean)

class Expired(val generation: Long) : Exception("generation $generation expired")

class Stale(val revision: Long) : Exception("revision $revision stale")

class Disconnected : Exception("player disconnected")

class Busy(val text: String = "") : Exception(text.ifEmpty { "busy" })

class Refused(val text: String = "") : Exception(text.ifEmpty { "refused" })

class NoCard : Exception("card not available")

class NoSpace : Exception("no space")

enum class Card { Present, Out, Error, Held }

data class LibraryState(val card: Card = Card.Present, val first: Boolean = false)

private val present: StateFlow<LibraryState> = MutableStateFlow(LibraryState()).asStateFlow()

data class LibraryCaps(
    val genreAlbums: Boolean = true,
    val folderTracks: Boolean = true,
    val trackSetIds: Boolean = true,
    val unindexed: Set<KClass<out Listing>> = emptySet(),
) {
    fun indexOf(listing: Listing): Boolean = listing::class !in unindexed
}

interface LibraryView {
    val generation: Long

    suspend fun page(listing: Listing, offset: Int, count: Int): Page
    suspend fun sections(listing: Listing): ByteArray
    suspend fun jump(listing: Listing, letter: Int, count: Int): Page?
    suspend fun indexOf(listing: Listing, kind: Kind, id: Long): Int?
    suspend fun trackInfo(track: Long): TrackInfo?
    suspend fun trackIds(track: Long): TrackIds?
    suspend fun pinned(kind: Kind, id: Long): Boolean
    suspend fun artRef(track: Long): ArtRef?
    suspend fun art(ref: ArtRef, maxSide: Int): ByteArray?
}

interface Library {
    val generation: StateFlow<Long>
    val roots: StateFlow<List<Root>>
    val scan: StateFlow<ScanProgress>
    val caps: LibraryCaps get() = LibraryCaps()
    val state: StateFlow<LibraryState> get() = present

    fun view(): LibraryView
    fun view(generation: Long): LibraryView
    suspend fun addRoot(treeUri: String)
    suspend fun removeRoot(treeUri: String)
    suspend fun rescan()
}
