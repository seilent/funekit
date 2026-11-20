package net.seilent.funekit.screens.host

import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.Anchor
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PushFrom
import net.seilent.funekit.screens.music.hub.HubState
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel

private const val PUSH_ROW = 3
private const val ANCHOR_INDEX = 3
private const val MUSIC_STATE = 3
private const val MUSIC_HEAD = 4
private const val MUSIC = "music"

class MusicModels {
    val hub = HubState()
    val np = NowPlayingModel()
    var searchQuery = ""
}

class SavedMusic(val entry: MusicEntry, val pivot: Int, val anchor: Anchor?, val state: List<Any>?)

private fun listingOf(l: Listing): ArrayList<Any> = when (l) {
    Listing.Artists -> arrayListOf("artists")
    Listing.Albums -> arrayListOf("albums")
    Listing.Songs -> arrayListOf("songs")
    Listing.Genres -> arrayListOf("genres")
    Listing.Playlists -> arrayListOf("playlists")
    Listing.Favorites -> arrayListOf("favorites")
    Listing.Pins -> arrayListOf("pins")
    Listing.History -> arrayListOf("history")
    Listing.NewAlbums -> arrayListOf("new")
    Listing.Queue -> arrayListOf("queue")
    is Listing.ArtistAlbums -> arrayListOf("artistalbums", l.artist)
    is Listing.ArtistTracks -> arrayListOf("artisttracks", l.artist)
    is Listing.AlbumTracks -> arrayListOf("albumtracks", l.album)
    is Listing.GenreAlbums -> arrayListOf("genrealbums", l.genre)
    is Listing.GenreTracks -> arrayListOf("genretracks", l.genre)
    is Listing.PlaylistTracks -> arrayListOf("playlisttracks", l.playlist)
    is Listing.Folder -> arrayListOf("folder", l.folder)
    is Listing.FolderTracks -> arrayListOf("foldertracks", l.folder)
    is Listing.Search -> arrayListOf("search", l.query, l.kind?.name ?: "")
}

private fun listingFrom(v: List<*>): Listing? {
    val id = v.getOrNull(1) as? Long
    return when (v.firstOrNull()) {
        "artists" -> Listing.Artists
        "albums" -> Listing.Albums
        "songs" -> Listing.Songs
        "genres" -> Listing.Genres
        "playlists" -> Listing.Playlists
        "favorites" -> Listing.Favorites
        "pins" -> Listing.Pins
        "history" -> Listing.History
        "new" -> Listing.NewAlbums
        "queue" -> Listing.Queue
        "artistalbums" -> id?.let { Listing.ArtistAlbums(it) }
        "artisttracks" -> id?.let { Listing.ArtistTracks(it) }
        "albumtracks" -> id?.let { Listing.AlbumTracks(it) }
        "genrealbums" -> id?.let { Listing.GenreAlbums(it) }
        "genretracks" -> id?.let { Listing.GenreTracks(it) }
        "playlisttracks" -> id?.let { Listing.PlaylistTracks(it) }
        "folder" -> id?.let { Listing.Folder(it) }
        "foldertracks" -> id?.let { Listing.FolderTracks(it) }
        "search" -> {
            val (_, q, k) = v
            (q as? String)?.let { Listing.Search(it, Kind.entries.firstOrNull { e -> e.name == k }) }
        }
        else -> null
    }
}

private fun pageOf(p: MusicPage): ArrayList<Any> = when (p) {
    MusicPage.Hub -> arrayListOf("hub")
    is MusicPage.Album -> arrayListOf("album", p.id)
    is MusicPage.AlbumCol -> arrayListOf("albumcol", p.letter)
    is MusicPage.Artist -> arrayListOf("artist", p.id)
    MusicPage.Queue -> arrayListOf("queue")
    MusicPage.Playlists -> arrayListOf("playlists")
    is MusicPage.ListView -> arrayListOf("list", listingOf(p.listing))
    MusicPage.History -> arrayListOf("history")
    is MusicPage.Search -> arrayListOf("search", p.query)
    MusicPage.NowPlaying -> arrayListOf("nowplaying")
}

private fun pageFrom(v: List<*>): MusicPage? {
    val arg = v.getOrNull(1)
    return when (v.firstOrNull()) {
        "hub" -> MusicPage.Hub
        "album" -> (arg as? Long)?.let { MusicPage.Album(it) }
        "albumcol" -> (arg as? Int)?.let { MusicPage.AlbumCol(it) }
        "artist" -> (arg as? Long)?.let { MusicPage.Artist(it) }
        "queue" -> MusicPage.Queue
        "playlists" -> MusicPage.Playlists
        "list" -> (arg as? List<*>)?.let(::listingFrom)?.let { MusicPage.ListView(it) }
        "history" -> MusicPage.History
        "search" -> (arg as? String)?.let { MusicPage.Search(it) }
        "nowplaying" -> MusicPage.NowPlaying
        else -> null
    }
}

private fun anchorOf(a: Anchor?): ArrayList<Any> = if (a == null) arrayListOf() else arrayListOf(a.kind.name, a.id, a.off, a.index)

private fun anchorFrom(v: List<*>): Anchor? {
    if (v.isEmpty()) return null
    val (k, id, off) = v
    val index = v.getOrNull(ANCHOR_INDEX) as? Int ?: -1
    val kind = Kind.entries.firstOrNull { it.name == k } ?: return null
    return if (id is Long && off is Float) Anchor(kind, id, off, index) else null
}

private fun fromOf(f: PushFrom?): ArrayList<Any> = if (f == null) arrayListOf() else arrayListOf(f.entry, f.pivot, f.word, f.row)

private fun fromFrom(v: List<*>): PushFrom? {
    if (v.isEmpty()) return null
    val (entry, pivot, word) = v
    val row = v.getOrNull(PUSH_ROW) as? Int ?: -1
    return if (entry is Long && pivot is Int && word is String) PushFrom(entry, pivot, word, row) else null
}

private fun artOf(a: ArtRef?): ArrayList<Any> = when (a) {
    null -> arrayListOf("n")
    is ArtRef.Embedded -> arrayListOf("e", a.track, a.offset, a.length)
    is ArtRef.Cover -> arrayListOf("c", a.track, a.name)
}

private fun artFrom(v: List<*>): ArtRef? = when (v.firstOrNull()) {
    "e" -> {
        val (_, track, offset, length) = v
        if (track is Long && offset is Long && length is Long) ArtRef.Embedded(track, offset, length) else null
    }
    "c" -> {
        val (_, track, name) = v
        if (track is Long && name is String) ArtRef.Cover(track, name) else null
    }
    else -> null
}

private fun rowOf(r: Row?): ArrayList<Any> = if (r == null) {
    arrayListOf()
} else {
    arrayListOf(r.id, r.kind.name, r.title, r.sub, artOf(r.art), r.no ?: -1, r.durationMs ?: -1L, r.year ?: -1, ArrayList(listOfNotNull(r.album)), r.count ?: -1, r.count2 ?: -1, r.writable)
}

private fun rowFrom(v: List<*>): Row? {
    if (v.isEmpty()) return null
    val f = v.iterator()
    val id = f.next() as? Long ?: return null
    val kind = f.next().let { k -> Kind.entries.firstOrNull { it.name == k } } ?: return null
    val title = f.next() as? String ?: return null
    val sub = f.next() as? String ?: return null
    val art = (f.next() as? List<*>)?.let(::artFrom)
    val no = (f.next() as? Int)?.takeIf { it >= 0 }
    val dur = (f.next() as? Long)?.takeIf { it >= 0L }
    val year = (f.next() as? Int)?.takeIf { it >= 0 }
    val album = (f.next() as? List<*>)?.firstOrNull() as? String
    val count = (f.next() as? Int)?.takeIf { it >= 0 }
    val count2 = (f.next() as? Int)?.takeIf { it >= 0 }
    val writable = f.next() as? Boolean ?: return null
    return Row(id, kind, title, sub, art, no, dur, year, album, count, count2, writable)
}

fun savedMusic(e: MusicEntry, pivot: Int, anchor: Anchor?, state: List<Any>?): ArrayList<Any> =
    arrayListOf(MUSIC, arrayListOf<Any>(e.entry, pageOf(e.page), fromOf(e.from), e.tile, rowOf(e.head)), arrayListOf<Any>(pivot, anchorOf(anchor)), ArrayList(state.orEmpty()))

fun musicSaved(v: List<*>): SavedMusic? {
    if (v.firstOrNull() != MUSIC) return null
    val (_, e, m) = v
    val st = v.getOrNull(MUSIC_STATE) as? List<*> ?: emptyList<Any>()
    val es = e as List<*>
    val (entry, page, from, tile) = es
    val head = es.getOrNull(MUSIC_HEAD) as? List<*> ?: emptyList<Any>()
    val (pivot, anchor) = m as List<*>
    val p = (page as? List<*>)?.let(::pageFrom)
    val f = from as? List<*>
    return if (entry is Long && p != null && f != null && tile is Boolean && pivot is Int) {
        SavedMusic(MusicEntry(entry, p, fromFrom(f), tile, rowFrom(head)), pivot, (anchor as? List<*>)?.let(::anchorFrom), st.filterNotNull().takeIf { it.isNotEmpty() })
    } else {
        null
    }
}
