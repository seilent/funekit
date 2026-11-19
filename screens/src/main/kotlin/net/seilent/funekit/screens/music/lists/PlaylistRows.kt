package net.seilent.funekit.screens.music.lists

import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.hub.FAVORITES
import net.seilent.funekit.screens.music.hub.NOW_PLAYING
import net.seilent.funekit.screens.music.hub.songsLabel
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget

const val NOW_KEY = 1L
const val FAVS_KEY = 4L
private const val KEY_SHIFT = 2
private const val KEY_BIT = 2L
private const val LIST_ROW = 2
private const val PLAYLIST = " playlist"
private const val PLAYLISTS = " playlists"

fun listKey(id: Long): Long = (id shl KEY_SHIFT) or KEY_BIT

fun nowRow(queue: Int): RowModel = RowModel(primary = NOW_PLAYING, secondary = songsLabel(queue), bold = true, collection = true)

fun favsRow(n: Int, accent: Boolean): RowModel = RowModel(primary = FAVORITES, secondary = songsLabel(n), bold = true, collection = true, accent = accent)

fun listRow(r: Row, accent: Boolean): RowModel = RowModel(primary = r.title, secondary = songsLabel(r.count ?: 0), bold = true, collection = true, accent = accent)

fun favsPlay(n: Int): Listing? = if (n > 0) Listing.Favorites else null

fun listPlay(r: Row): Listing? = if ((r.count ?: 0) > 0) Listing.PlaylistTracks(r.id) else null

fun favsTarget(n: Int, g: Long, from: SheetFrom): SheetTarget = SheetTarget(SheetKind.Favorites, FAVORITES, Listing.Favorites, g, songs = n, from = from)

fun listTarget(r: Row, g: Long, from: SheetFrom): SheetTarget =
    SheetTarget(SheetKind.Playlist, r.title, Listing.PlaylistTracks(r.id), g, id = r.id, songs = r.count ?: 0, writable = r.writable, from = from)

fun playlistsCount(n: Int): String = n.toString() + if (n == 1) PLAYLIST else PLAYLISTS

class PlaylistRows(
    val queue: Int = 0,
    val favorites: Int = 0,
    val lists: List<Row> = emptyList(),
    val origin: Listing? = null,
    val generation: Long = 0L,
) {
    val size: Int get() = LIST_ROW + lists.size

    fun list(row: Int): Row? = if (row < LIST_ROW) null else lists.getOrNull(row - LIST_ROW)

    fun key(row: Int): Long = when (row) {
        0 -> NOW_KEY
        1 -> FAVS_KEY
        else -> list(row)?.let { listKey(it.id) } ?: 0L
    }

    fun find(k: Long): Int = (0 until size).firstOrNull { key(it) == k } ?: -1

    private fun playing(l: Listing): Boolean = queue > 0 && origin == l

    fun model(row: Int): RowModel? = when (row) {
        0 -> nowRow(queue)
        1 -> favsRow(favorites, playing(Listing.Favorites))
        else -> list(row)?.let { listRow(it, playing(Listing.PlaylistTracks(it.id))) }
    }

    fun play(row: Int): Listing? = when (row) {
        0 -> null
        1 -> favsPlay(favorites)
        else -> list(row)?.let(::listPlay)
    }

    fun target(row: Int, from: SheetFrom): SheetTarget? = when (row) {
        0 -> null
        1 -> favsTarget(favorites, generation, from)
        else -> list(row)?.let { listTarget(it, generation, from) }
    }

    fun count(): String = playlistsCount(lists.size)
}
