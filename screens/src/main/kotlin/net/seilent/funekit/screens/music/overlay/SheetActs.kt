package net.seilent.funekit.screens.music.overlay

import kotlinx.coroutines.CancellationException
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.TrackInfo
import net.seilent.funekit.api.TrackSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.hub.FAVORITES
import net.seilent.funekit.screens.music.lists.ListViewModel

const val SONG_ADDED = "song added to now playing"
const val SONGS_ADDED = "songs added to now playing"
const val NEW_PLAYLIST = "new playlist"
const val ADDED_TO = "added to "
const val NOT_READABLE = "playlist not readable"

sealed interface SheetDone {
    data object Close : SheetDone

    data class Toast(val line: String, val title: String) : SheetDone

    data class Info(val track: Long, val live: Boolean, val generation: Long) : SheetDone

    data class Confirm(val playlist: Long) : SheetDone
}

sealed interface PickTo {
    data object New : PickTo

    data object Favorites : PickTo

    data class Playlist(val id: Long) : PickTo
}

class Picker(val items: List<String>, val to: List<PickTo>)

suspend fun picker(src: MusicSource): Picker {
    val lists = src.viewed { v -> v.page(Listing.Playlists, 0, v.page(Listing.Playlists, 0, 0).total).rows }.filter { it.writable }
    return Picker(listOf(NEW_PLAYLIST, FAVORITES) + lists.map { it.title }, listOf(PickTo.New, PickTo.Favorites) + lists.map { PickTo.Playlist(it.id) })
}

fun pickTracks(t: SheetTarget): TrackSet? {
    val track = t.track
    if (t.kind == SheetKind.Song && track != null) return TrackSet.Ids(listOf(track))
    return t.listing?.let { TrackSet.Listed(it, t.generation) }
}

suspend fun runPick(i: Int, p: Picker, t: SheetTarget, src: MusicSource): SheetDone {
    val to = p.to.getOrNull(i) ?: return SheetDone.Close
    var name = p.items[i]
    val tracks = pickTracks(t) ?: return SheetDone.Close
    when (to) {
        PickTo.New -> {
            val id = src.playlists.create(tracks) ?: return SheetDone.Close
            name = src.viewed { v -> v.page(Listing.Playlists, 0, v.page(Listing.Playlists, 0, 0).total).rows.firstOrNull { it.id == id }?.title } ?: name
        }
        PickTo.Favorites -> src.favorites.add(tracks)
        is PickTo.Playlist -> try {
            src.playlists.add(to.id, tracks)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalStateException) {
            return SheetDone.Toast(NOT_READABLE, name)
        } catch (_: Busy) {
            return SheetDone.Toast(NOT_READABLE, name)
        }
    }
    return SheetDone.Toast(ADDED_TO + name, t.title)
}

suspend fun runEntry(word: String, t: SheetTarget, view: ListViewModel?, src: MusicSource, now: Long): SheetDone {
    if (word == INFO) return t.track?.let { SheetDone.Info(it, false, t.generation) } ?: SheetDone.Close
    view?.verb(word, t, src, now)
    return SheetDone.Close
}

suspend fun runSheet(word: String, t: SheetTarget, src: MusicSource): SheetDone {
    val p = src.player
    val listing = t.listing
    when (word) {
        PLAY -> {
            val track = t.track
            if (listing != null) p.play(listing, t.generation, t.start, 0L) else if (track != null) p.play(listOf(track), 0, 0L)
        }
        SHUFFLE -> if (listing != null) p.play(listing, t.generation, -1, 0L, shuffle = true)
        PLAY_NEXT, ADD -> {
            val before = p.state.value.queueSize
            val one = t.kind == SheetKind.Song || t.kind == SheetKind.Tile && t.pin == Kind.Song
            val next = word == PLAY_NEXT
            when {
                one && t.track != null -> if (next) p.playNext(listOf(t.track)) else p.enqueue(listOf(t.track))
                listing != null -> if (next) p.playNext(listing, t.generation) else p.enqueue(listing, t.generation)
                else -> return SheetDone.Close
            }
            val n = p.state.value.queueSize - before
            if (n <= 0) return SheetDone.Close
            return SheetDone.Toast(if (n == 1) SONG_ADDED else SONGS_ADDED, t.title)
        }
        PIN -> t.pin?.let { src.quickplay.pin(it, t.id) }
        UNPIN_QP, UNPIN -> t.pin?.let { src.quickplay.unpin(it, t.id) }
        INFO -> t.track?.let { return SheetDone.Info(it, listing == Listing.Queue, t.generation) }
        DELETE_PLAYLIST -> if (t.kind == SheetKind.Playlist) return SheetDone.Confirm(t.id)
    }
    return SheetDone.Close
}

suspend fun artistRows(t: SheetTarget, src: MusicSource): List<Row> {
    val track = t.track ?: return emptyList()
    val v = src.library.view(t.generation)
    val ids = v.trackIds(track)?.artists ?: return emptyList()
    return ids.mapNotNull { id -> v.indexOf(Listing.Artists, Kind.Artist, id)?.let { at -> v.page(Listing.Artists, at, 1).rows.firstOrNull() } }
}

suspend fun confirmDelete(word: String, playlist: Long, src: MusicSource) {
    if (word == DELETE_PLAYLIST) src.playlists.delete(playlist)
}

suspend fun infoOf(d: SheetDone.Info, src: MusicSource): TrackInfo? =
    if (d.live) src.viewed { it.trackInfo(d.track) } else src.library.view(d.generation).trackInfo(d.track)
