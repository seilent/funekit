package net.seilent.funekit.screens.music.overlay

import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing

const val PLAY = "play"
const val PLAY_NEXT = "play next"
const val ADD = "add to now playing"
const val SHUFFLE = "shuffle"
const val PIN = "pin to quickplay"
const val UNPIN_QP = "unpin from quickplay"
const val UNPIN = "unpin"
const val INFO = "info"
const val DELETE_PLAYLIST = "delete playlist"
const val KEEP_PLAYLIST = "keep playlist"
const val DELETE_ASK = "delete playlist?"
const val REMOVE = "remove"
const val MOVE_UP = "move up"
const val MOVE_DOWN = "move down"
const val ADD_TO_PLAYLIST = "add to playlist"
const val OPEN_PLAYLIST = "open playlist"
const val GO_TO_ALBUM = "go to album"
const val GO_TO_ARTIST = "go to artist"

enum class SheetKind { Song, Album, Artist, Genre, Folder, Playlist, Favorites, Tile, Queue, Entry }

enum class SheetFrom { Other, Hub, Playlists, View, History, Queue, Tile, NowPlaying, Album, Artist, AlbumCol, Search }

data class SheetTarget(
    val kind: SheetKind,
    val title: String,
    val listing: Listing? = null,
    val generation: Long = 0L,
    val start: Int = 0,
    val track: Long? = null,
    val pin: Kind? = null,
    val id: Long = 0L,
    val songs: Int = 0,
    val writable: Boolean = false,
    val slot: Int = -1,
    val cur: Int = -1,
    val size: Int = 0,
    val revision: Long = 0L,
    val library: Boolean = true,
    val pinned: Boolean? = null,
    val from: SheetFrom = SheetFrom.Other,
)

fun sheetItems(t: SheetTarget, pinned: Boolean): List<String> {
    val pin = if (pinned) UNPIN_QP else PIN
    return when (t.kind) {
        SheetKind.Song -> buildList {
            addAll(listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST, pin, INFO))
            if (t.from != SheetFrom.Album) add(GO_TO_ALBUM)
            if (t.from == SheetFrom.Search) add(GO_TO_ARTIST)
        }
        SheetKind.Album, SheetKind.Artist -> listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST, pin)
        SheetKind.Genre -> listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST)
        SheetKind.Folder -> listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST)
        SheetKind.Playlist, SheetKind.Favorites -> buildList {
            if (t.songs > 0) addAll(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE))
            add(OPEN_PLAYLIST)
            if (t.kind == SheetKind.Playlist && t.writable) add(DELETE_PLAYLIST)
        }
        SheetKind.Tile -> listOf(if (pinned) UNPIN else PIN, PLAY_NEXT, ADD)
        SheetKind.Queue -> buildList {
            add(REMOVE)
            if (t.slot != t.cur && t.slot != t.cur + 1) add(PLAY_NEXT)
            if (t.slot > 0) add(MOVE_UP)
            if (t.slot < t.size - 1) add(MOVE_DOWN)
            if (t.library) add(INFO)
        }
        SheetKind.Entry -> buildList {
            if (t.writable) {
                add(REMOVE)
                if (t.slot > 0) add(MOVE_UP)
                if (t.slot < t.size - 1) add(MOVE_DOWN)
            }
            add(INFO)
        }
    }
}
