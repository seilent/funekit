package net.seilent.funekit.screens.host

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.seilent.funekit.kit.quickplay.QpData
import net.seilent.funekit.kit.quickplay.QpItem
import net.seilent.funekit.kit.quickplay.QpKind
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.Kind as ApiKind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.Quickplay
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.history
import net.seilent.funekit.api.newAlbums
import net.seilent.funekit.api.pins

interface QpSource {
    val changes: Flow<Any>

    val ready: Flow<Boolean> get() = flowOf(true)

    suspend fun load(tiles: Int): QpData

    suspend fun art(ref: ArtRef, maxSide: Int): ByteArray?

    suspend fun play(item: QpItem): Boolean

    suspend fun resume() {}
}

class ApiQuickplay(private val library: Library, private val player: Player, private val quickplay: Quickplay) : QpSource {
    override val changes: Flow<Any> = combine(
        library.generation,
        quickplay.revision,
        player.state.map { Triple(it.track, it.index, it.playing) }.distinctUntilChanged(),
    ) { g, r, t -> Triple(g, r, t) }

    override val ready: Flow<Boolean> = library.generation.map { it > 0 }.distinctUntilChanged()

    private suspend fun <T> viewed(block: suspend (LibraryView) -> T): T {
        while (true) {
            try {
                return block(library.view())
            } catch (e: Expired) {
                continue
            }
        }
    }

    private fun item(r: Row) = if (r.kind == ApiKind.Service) null else QpItem(r.kind.qp(), r.id, r.title, r.art)

    override suspend fun load(tiles: Int): QpData = viewed { v ->
        val pins = v.pins(0, Int.MAX_VALUE).rows.mapNotNull(::item)
        val history = v.history(0, tiles).rows.mapNotNull(::item)
        val newest = v.newAlbums(0, tiles).rows.mapNotNull(::item)
        val s = player.state.value
        val track = s.track
        val playing = if (track == null) null else {
            val title = v.page(Listing.Queue, s.index, 1).rows.firstOrNull()?.title ?: ""
            QpItem(QpKind.Song, track, title, v.artRef(track))
        }
        QpData(playing, pins, history, newest, v.generation, playing != null && !s.playing)
    }

    override suspend fun resume() {
        val s = player.state.value
        if (s.track != null && !s.playing) player.resume()
    }

    override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? = viewed { it.art(ref, maxSide) }

    override suspend fun play(item: QpItem): Boolean {
        when (item.kind) {
            QpKind.Song -> player.play(listOf(item.id), 0, 0)
            QpKind.Album -> player.play(Listing.AlbumTracks(item.id), library.generation.value, 0, 0)
            QpKind.Artist -> {
                val v = library.view(library.generation.value)
                val ids = v.page(Listing.ArtistAlbums(item.id), 0, Int.MAX_VALUE).rows.flatMap { a ->
                    v.page(Listing.AlbumTracks(a.id), 0, Int.MAX_VALUE).rows.map { it.id }
                }
                if (ids.isEmpty()) return false
                player.play(ids, 0, 0)
            }
            else -> return false
        }
        return true
    }
}

fun ApiKind.qp(): QpKind = when (this) {
    ApiKind.Artist -> QpKind.Artist
    ApiKind.Album -> QpKind.Album
    ApiKind.Song -> QpKind.Song
    ApiKind.Genre -> QpKind.Genre
    ApiKind.Folder -> QpKind.Folder
    ApiKind.Playlist -> QpKind.Playlist
    ApiKind.Service -> throw IllegalStateException("quickplay $this")
}

fun QpKind.api(): ApiKind = when (this) {
    QpKind.Artist -> ApiKind.Artist
    QpKind.Album -> ApiKind.Album
    QpKind.Song -> ApiKind.Song
    QpKind.Genre -> ApiKind.Genre
    QpKind.Folder -> ApiKind.Folder
    QpKind.Playlist -> ApiKind.Playlist
}
