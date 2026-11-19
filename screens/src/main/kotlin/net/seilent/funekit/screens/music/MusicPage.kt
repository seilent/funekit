package net.seilent.funekit.screens.music

import net.seilent.funekit.api.Listing

const val ALBUMS = "albums"

private val HUB_PIVOTS = listOf(ALBUMS, "artists", "playlists", "songs", "genres", "folders")
private val SEARCH_PIVOTS = listOf("artists", ALBUMS, "songs")
private val ARTIST_PIVOTS = listOf(ALBUMS, "songs")
private val HISTORY_PIVOTS = listOf("recent", "new")

sealed interface MusicPage : PageId {
    data object Hub : MusicPage

    data class Album(val id: Long) : MusicPage

    data class AlbumCol(val letter: Int) : MusicPage

    data class Artist(val id: Long) : MusicPage

    data object Queue : MusicPage

    data object Playlists : MusicPage

    data class ListView(val listing: Listing) : MusicPage

    data object History : MusicPage

    data class Search(val query: String) : MusicPage

    data object NowPlaying : MusicPage
}

data class PushFrom(val entry: Long, val pivot: Int, val word: String, val row: Int = -1)

val MusicPage.pivots: List<String>
    get() = when (this) {
        MusicPage.Hub -> HUB_PIVOTS
        is MusicPage.Search -> SEARCH_PIVOTS
        is MusicPage.Artist -> ARTIST_PIVOTS
        MusicPage.History -> HISTORY_PIVOTS
        else -> emptyList()
    }

val MusicPage.albumHeader: Boolean get() = this is MusicPage.Album || this is MusicPage.AlbumCol

val MusicPage.headerWord: String?
    get() = when (this) {
        MusicPage.Hub -> "music"
        is MusicPage.Search -> "search"
        MusicPage.Playlists -> "playlists"
        MusicPage.History -> "history"
        MusicPage.Queue -> "queue"
        is MusicPage.Album, is MusicPage.AlbumCol -> ALBUMS
        else -> null
    }

fun pushFrom(entry: Long, page: MusicPage, pivot: Int, row: Int = -1): PushFrom {
    val names = page.pivots
    return if (pivot in names.indices) PushFrom(entry, pivot, names[pivot], row) else PushFrom(entry, -1, "", row)
}
