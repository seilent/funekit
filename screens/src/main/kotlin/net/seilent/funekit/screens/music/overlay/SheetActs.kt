package net.seilent.funekit.screens.music.overlay

import kotlinx.coroutines.CancellationException
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.Refused
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
const val SONG_NOT_DELETED = "song not deleted"
const val ALBUM_NOT_DELETED = "album not deleted"
const val READ_ONLY = "card is read only"
const val CUE_PART = "part of a cue image"
const val CARD_GONE = "card not available"
const val FAVS_UNREADABLE = "favorites not readable"

sealed interface SheetDone {
    data object Close : SheetDone

    data class Toast(val line: String, val title: String) : SheetDone

    data class Info(val track: Long, val live: Boolean, val generation: Long) : SheetDone

    data class Confirm(val playlist: Long) : SheetDone
}

class SheetDelete(val songs: Int, val artist: Long?, val delete: suspend (Long) -> DeleteDone)

sealed interface DeleteDone {
    data object Done : DeleteDone

    data class Partly(val kept: Int) : DeleteDone

    data object Refused : DeleteDone

    data object ReadOnly : DeleteDone

    data object Cue : DeleteDone

    data object NoCard : DeleteDone

    data class Words(val line: String, val title: String) : DeleteDone {
        init {
            require(line.isNotBlank()) { "a delete outcome shows a line" }
        }
    }
}

class DeleteAsk(val title: String, val items: List<String>, val lead: Int, val focus: Int, val yes: String)

fun deleteAsk(t: SheetTarget, songs: Int): DeleteAsk {
    val album = t.kind == SheetKind.Album
    val yes = if (album) DELETE_ALBUM else DELETE_SONG
    val lead = if (album) listOf(t.title, if (songs == 1) "1 song" else "$songs songs") else listOf(t.title)
    val items = lead + listOf(yes, if (album) KEEP_ALBUM else KEEP_SONG)
    return DeleteAsk(if (album) DELETE_ALBUM_ASK else DELETE_SONG_ASK, items, lead.size, items.lastIndex, yes)
}

fun deleteToast(t: SheetTarget, d: DeleteDone): SheetDone.Toast? {
    val album = t.kind == SheetKind.Album
    val what = if (album) ALBUM_NOT_DELETED else SONG_NOT_DELETED
    return when (d) {
        DeleteDone.Done -> null
        is DeleteDone.Partly -> SheetDone.Toast(if (!album) what else if (d.kept == 1) "1 $SONG_NOT_DELETED" else "${d.kept} songs not deleted", t.title)
        DeleteDone.Refused -> SheetDone.Toast(what, t.title)
        DeleteDone.ReadOnly -> SheetDone.Toast(what, READ_ONLY)
        DeleteDone.Cue -> SheetDone.Toast(what, CUE_PART)
        DeleteDone.NoCard -> SheetDone.Toast(CARD_GONE, "")
        is DeleteDone.Words -> SheetDone.Toast(d.line, d.title)
    }
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
            var made: Long? = null
            listWrite { made = src.playlists.create(tracks) }?.let { return it }
            val id = made ?: return SheetDone.Close
            name = src.viewed { v -> v.page(Listing.Playlists, 0, v.page(Listing.Playlists, 0, 0).total).rows.firstOrNull { it.id == id }?.title } ?: name
        }
        PickTo.Favorites -> listEdit(true, name) { src.favorites.add(tracks) }?.let { return it }
        is PickTo.Playlist -> listEdit(false, name) { src.playlists.add(to.id, tracks) }?.let { return it }
    }
    return SheetDone.Toast(ADDED_TO + name, t.title)
}

suspend fun runEntry(word: String, t: SheetTarget, view: ListViewModel?, src: MusicSource, now: Long): SheetDone {
    if (word == INFO) return t.track?.let { SheetDone.Info(it, false, t.generation) } ?: SheetDone.Close
    if (view != null) listEdit(view.listing == Listing.Favorites, view.title) { view.verb(word, t, src, now) }?.let { return it }
    return SheetDone.Close
}

suspend fun listEdit(favorites: Boolean, name: String, edit: suspend () -> Unit): SheetDone? = try {
    listWrite(edit)
} catch (e: CancellationException) {
    throw e
} catch (_: IllegalStateException) {
    if (favorites) SheetDone.Toast(FAVS_UNREADABLE, "") else SheetDone.Toast(NOT_READABLE, name)
}

private suspend fun listWrite(edit: suspend () -> Unit): SheetDone? = try {
    edit()
    null
} catch (_: NoCard) {
    SheetDone.Toast(CARD_GONE, "")
} catch (_: Busy) {
    SheetDone.Toast(CARD_GONE, "")
} catch (e: Refused) {
    if (e.text.isBlank()) SheetDone.Close else SheetDone.Toast(e.text, "")
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

suspend fun confirmDelete(word: String, playlist: Long, src: MusicSource): SheetDone? =
    if (word == DELETE_PLAYLIST) listWrite { src.playlists.delete(playlist) } else null

suspend fun infoOf(d: SheetDone.Info, src: MusicSource): TrackInfo? =
    if (d.live) src.viewed { it.trackInfo(d.track) } else src.library.view(d.generation).trackInfo(d.track)
