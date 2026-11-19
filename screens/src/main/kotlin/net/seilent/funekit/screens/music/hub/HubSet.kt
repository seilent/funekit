package net.seilent.funekit.screens.music.hub

import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.pivots

val SEARCH_PIVOTS: List<String> = MusicPage.Search("").pivots
val SEARCH_ARTISTS: Int = SEARCH_PIVOTS.indexOf("artists")
val SEARCH_ALBUMS: Int = SEARCH_PIVOTS.indexOf("albums")
val SEARCH_SONGS: Int = SEARCH_PIVOTS.indexOf("songs")

const val QUERY_CELL = FAVS_CELL - 1

sealed interface HubSet {
    val pivots: List<String>
    val grid: Int
    val from: SheetFrom

    fun role(pivot: Int): Int

    fun listing(pivot: Int, folder: Long): Listing

    fun lead(pivot: Int, atRoot: Boolean): List<Int>

    fun kind(pivot: Int): PageKind = if (pivot == grid) PageKind.Grid else PageKind.Hub

    data object Music : HubSet {
        override val pivots: List<String> = MusicPage.Hub.pivots
        override val grid: Int = HUB_ALBUMS
        override val from: SheetFrom = SheetFrom.Hub

        override fun role(pivot: Int): Int = pivot

        override fun listing(pivot: Int, folder: Long): Listing = hubListing(pivot, folder)

        override fun lead(pivot: Int, atRoot: Boolean): List<Int> = when (pivot) {
            HUB_FOLDERS -> if (atRoot) emptyList() else listOf(LABEL_CELL)
            HUB_PLAYLISTS -> listOf(NOW_CELL, FAVS_CELL)
            else -> emptyList()
        }
    }

    data class Search(val query: String) : HubSet {
        override val pivots: List<String> get() = SEARCH_PIVOTS
        override val grid: Int get() = SEARCH_ALBUMS
        override val from: SheetFrom get() = SheetFrom.Search

        override fun role(pivot: Int): Int = when (pivot) {
            SEARCH_ARTISTS -> HUB_ARTISTS
            SEARCH_ALBUMS -> HUB_ALBUMS
            else -> HUB_SONGS
        }

        override fun listing(pivot: Int, folder: Long): Listing = Listing.Search(query, kindOf(pivot))

        override fun lead(pivot: Int, atRoot: Boolean): List<Int> = listOf(QUERY_CELL)

        fun kindOf(pivot: Int): Kind = when (pivot) {
            SEARCH_ARTISTS -> Kind.Artist
            SEARCH_ALBUMS -> Kind.Album
            else -> Kind.Song
        }
    }
}

fun hubPick(set: HubSet, pivot: Int): HubPick = if (set == HubSet.Music && pivot == set.grid) HubPick.Defer else HubPick.Scroll

fun hubLine(set: HubSet, pivot: Int, scanning: Boolean, found: Int, songs: Int, rows: Int, generation: Long): String = when (set) {
    HubSet.Music -> hubLine(pivot, scanning, found, songs, rows, generation)
    is HubSet.Search -> if (scanShown(scanning, scanSongs(generation, songs))) "scanning " + songsLabel(found) else ""
}
