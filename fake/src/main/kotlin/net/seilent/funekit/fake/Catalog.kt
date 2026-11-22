package net.seilent.funekit.fake

import net.seilent.funekit.core.ArtKey
import net.seilent.funekit.core.Section
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.TrackIds
import net.seilent.funekit.api.TrackInfo
import java.net.URLDecoder

internal class FakeRoot(val id: Long, val uri: String, val fixture: Int?) {
    val name: String = URLDecoder.decode(uri.substringAfterLast('/'), "UTF-8").substringAfterLast(':')
}

internal class Rows(
    val size: Int,
    private val letters: () -> ByteArray,
    private val ids: (Int) -> Long,
    private val kinds: (Int) -> Kind,
    private val make: (Int) -> Row,
) {
    fun row(i: Int): Row = make(i)

    fun id(i: Int): Long = ids(i)

    fun kind(i: Int): Kind = kinds(i)

    fun sections(): ByteArray = letters()

    companion object {
        fun of(rows: List<Row>, the: Boolean = false) = Rows(
            rows.size,
            { ByteArray(rows.size) { Section.key(rows[it].title, the).letter.toByte() } },
            { rows[it].id },
            { rows[it].kind },
        ) { rows[it] }
    }
}

internal class Catalog(set: FakeSet, private val roots: List<FakeRoot>, private val trackRefs: Boolean = false, dropped: Set<Long> = emptySet()) {
    private val shelf = Fixture.shelf(set)
    private val present = roots.mapNotNull { r -> r.fixture?.let { it to r } }.toMap()
    private val albums = if (set == FakeSet.Empty) emptyList() else shelf.albums.filter { it.root in present && it.id !in dropped }
    private val albumById = albums.associateBy { it.id }
    private val tracks = albums.flatMap { it.tracks }
    private val trackById = tracks.associateBy { it.id }
    private val artists = shelf.artists.filter { a -> tracks.any { a.id in it.artists } }
    private val genres = shelf.genres.filter { g -> albums.any { it.genre == g.id } }
    private val huge = set == FakeSet.Huge && roots.isNotEmpty()
    private val folderAt = shelf.folders.entries.associate { (k, v) -> v to k }

    private val artistRows by lazy { sorted(artists, true) { it.name }.map(::artistRow) }
    private val albumRows by lazy { sortedAlbums(albums).map(::albumRow) }
    private val songRows by lazy { sorted(tracks, false) { it.title }.map(::songRow) }
    private val genreRows by lazy { sorted(genres, false) { it.name }.map { Row(it.id, Kind.Genre, it.name, "", null) } }
    private val byArtist by lazy {
        tracks.flatMap { t -> t.artists.map { it to t.album } }.distinct().groupBy({ it.first }, { albumById.getValue(it.second) })
            .mapValues { (_, l) -> l.sortedWith(compareBy<Album> { it.year ?: 0 }.thenBy { it.id }) }
    }
    private val files by lazy {
        tracks.groupBy { rootOf(it) to it.dir }.mapValues { (_, l) -> l.sortedBy { it.file } }
    }
    private val subdirs by lazy {
        val names = tracks.flatMap { t -> Shelf.prefixes(t.dir).map { rootOf(t) to it } }.distinct()
        names.groupBy({ (r, p) -> r to p.substringBeforeLast('/', "") }, { it.second })
            .mapValues { (_, l) -> sorted(l, false) { it.substringAfterLast('/') } }
    }

    val trackCount: Int get() = if (huge) Fixture.HUGE else tracks.size

    val trackIds: Set<Long> get() = trackById.keys

    fun track(id: Long): Track? = trackById[id] ?: if (huge) Fixture.hugeIndex(id)?.let(Fixture::hugeTrack) else null

    fun rows(listing: Listing, store: Store): Rows = when (listing) {
        Listing.Artists -> Rows.of(artistRows, true)
        Listing.Albums -> Rows.of(albumRows)
        Listing.Songs -> if (huge) hugeRows() else Rows.of(songRows)
        Listing.Genres -> Rows.of(genreRows)
        Listing.Playlists -> Rows.of(merged(store.playlists).map(::playlistRow))
        is Listing.ArtistAlbums -> Rows.of(byArtist[listing.artist].orEmpty().map(::albumRow))
        is Listing.ArtistTracks -> Rows.of(artistTracks(listing.artist).map(::songRow))
        is Listing.AlbumTracks -> Rows.of(albumById[listing.album]?.tracks.orEmpty().map(::songRow))
        is Listing.GenreAlbums -> Rows.of(sortedAlbums(albums.filter { it.genre == listing.genre }).map(::albumRow))
        is Listing.GenreTracks -> Rows.of(songRows.filter { r -> trackById[r.id]?.genre == listing.genre })
        is Listing.PlaylistTracks -> Rows.of(store.playlists.firstOrNull { it.id == listing.playlist }?.tracks.orEmpty().mapNotNull { trackById[it] }.map(::songRow))
        is Listing.Folder -> folder(listing.folder)
        is Listing.FolderTracks -> Rows.of(folderTracks(listing.folder).map(::songRow))
        is Listing.Search -> search(listing.query, listing.kind)
        Listing.Pins -> Rows.of(store.pins.mapNotNull(::resolve))
        Listing.History -> Rows.of(store.history.mapNotNull { if (store.services && it == Fixture.SERVICE) serviceRow else track(it)?.let(::songRow) })
        Listing.NewAlbums -> Rows.of(albums.sortedByDescending { it.mtime }.map(::albumRow))
        Listing.Queue -> queueRows(store.queue.ids, store.services)
        Listing.Favorites -> Rows.of(store.favorites.mapNotNull { trackById[it] }.map(::songRow))
    }

    fun resolve(pin: Pin): Row? = when (pin.kind) {
        Kind.Album -> albumById[pin.id]?.let(::albumRow)
        Kind.Artist -> artists.firstOrNull { it.id == pin.id }?.let(::artistRow)
        Kind.Song -> track(pin.id)?.let(::songRow)
        Kind.Service -> serviceRow.takeIf { pin.id == Fixture.SERVICE }
        else -> null
    }

    fun artRef(id: Long): ArtRef? = track(id)?.let(::ref)

    fun art(ref: ArtRef, maxSide: Int): ByteArray? {
        val id = when (ref) {
            is ArtRef.Embedded -> ref.track
            is ArtRef.Cover -> ref.track
            is ArtRef.Track -> ref.track
        }
        val own = track(id)?.art ?: return null
        if (ref !is ArtRef.Track && own != ref) return null
        return Placeholder.png(id, maxSide.coerceIn(1, Placeholder.SIDE))
    }

    private fun ref(t: Track?): ArtRef? = if (trackRefs) t?.let { ArtRef.Track(it.id) } else t?.art

    fun ids(id: Long): TrackIds? {
        val t = trackById[id] ?: return null
        val album = albumById[t.album]
        val folders = album?.let { a -> present[a.root]?.let { r -> listOf(r.id) + Shelf.prefixes(t.dir).mapNotNull { shelf.folders[a.root to it] } } }
        return TrackIds(t.artists, album?.id, t.genre, folders.orEmpty())
    }

    fun info(id: Long): TrackInfo? {
        val t = track(id) ?: return null
        val album = albumById[t.album]
        val root = album?.let { present[it.root] } ?: roots.firstOrNull()
        val path = listOfNotNull(root?.name, t.dir.ifEmpty { null }, t.file)
        val bytes = bytes(t)
        val lines = listOfNotNull(
            album?.title?.ifEmpty { null },
            shelf.genreName(t.genre).ifEmpty { null },
            t.year?.toString(),
            t.codec.word,
            "${t.format.rate} hz",
            when {
                t.codec == Codec.Dsf -> "dsd${t.format.rate / DSD_BASE}"
                t.codec.depth -> "${t.format.bits} bit"
                else -> null
            },
            "${t.format.channels} ch",
            "${(bytes * BITS + t.ms / 2) / t.ms} kbps",
            "${t.ms / MINUTE}:${(t.ms / SECOND % SECONDS).toString().padStart(2, '0')}",
            (bytes + MB_TENTH / 2).div(MB_TENTH).let { "${it / TEN}.${it % TEN} mb" },
        )
        return TrackInfo(path.joinToString("/"), lines)
    }

    private fun bytes(t: Track): Long = when (t.codec) {
        Codec.Flac, Codec.Wav -> t.ms * t.format.rate * t.format.bits * t.format.channels / (BITS * SECOND)
        Codec.Dsf -> t.ms * t.format.rate * t.format.channels / (BITS * SECOND)
        Codec.Mp3, Codec.Aac -> t.ms * LOSSY_KBPS / BITS
    }

    private fun rootOf(t: Track): Int = albumById.getValue(t.album).root

    private fun hugeRows() = Rows(
        Fixture.HUGE,
        { ByteArray(Fixture.HUGE) { Fixture.hugeLetter(it).toByte() } },
        { Fixture.HUGE_BASE + it },
        { Kind.Song },
    ) { songRow(Fixture.hugeTrack(it)) }

    private fun queueRows(ids: List<Long>, services: Boolean) = Rows(
        ids.size,
        { ByteArray(ids.size) { i -> track(ids[i])?.let { Section.key(it.title).letter.toByte() } ?: 0.toByte() } },
        { ids[it] },
        { if (services && ids[it] == Fixture.SERVICE) Kind.Service else Kind.Song },
    ) { i -> if (services && ids[i] == Fixture.SERVICE) serviceRow else track(ids[i])?.let(::songRow) ?: Row(ids[i], Kind.Song, "", "", null) }

    private val serviceRow = Row(Fixture.SERVICE, Kind.Service, Fixture.SERVICE_TITLE, "", null, durationMs = Fixture.SERVICE_TRACK.ms)

    private fun folder(id: Long): Rows {
        if (id == 0L) return Rows.of(roots.map { Row(it.id, Kind.Folder, it.name, "", null, count = it.fixture?.let { f -> walk(f, "").size } ?: 0) })
        val (root, path) = place(id) ?: return Rows.of(emptyList())
        val dirs = subdirs[root to path].orEmpty().map { folderRow(root, it) }
        return Rows.of(dirs + files[root to path].orEmpty().map(::songRow))
    }

    private fun folderTracks(id: Long): List<Track> {
        if (id == 0L) return roots.flatMap { r -> r.fixture?.let { walk(it, "") }.orEmpty() }
        val (root, path) = place(id) ?: return emptyList()
        return walk(root, path)
    }

    private fun place(id: Long): Pair<Int, String>? {
        roots.firstOrNull { it.id == id }?.let { r -> return r.fixture?.let { it to "" } }
        return folderAt[id]?.takeIf { it.first in present }
    }

    private fun walk(root: Int, path: String): List<Track> =
        subdirs[root to path].orEmpty().flatMap { walk(root, it) } + files[root to path].orEmpty()

    private fun folderRow(root: Int, path: String): Row {
        val below = walk(root, path)
        return Row(shelf.folders.getValue(root to path), Kind.Folder, path.substringAfterLast('/'), "", ref(below.firstOrNull()), count = below.size)
    }

    private fun search(query: String, kind: Kind?): Rows {
        val q = ArtKey.fold(query)
        if (q.isEmpty()) return Rows.of(emptyList())
        fun hit(title: String) = ArtKey.fold(title).contains(q)
        fun wants(k: Kind) = kind == null || kind == k
        val head = (if (wants(Kind.Artist)) artistRows.filter { hit(it.title) } else emptyList()) +
            (if (wants(Kind.Album)) albumRows.filter { hit(it.title) } else emptyList()) +
            (if (wants(Kind.Song)) songRows.filter { hit(it.title) } else emptyList())
        if (!huge || !wants(Kind.Song)) return Rows.of(head)
        val more = (0 until Fixture.HUGE).filter { hit(Fixture.hugeTrack(it).title) }.toIntArray()
        return Rows(
            head.size + more.size,
            { ByteArray(head.size + more.size) { i -> if (i < head.size) Section.key(head[i].title).letter.toByte() else Fixture.hugeLetter(more[i - head.size]).toByte() } },
            { i -> if (i < head.size) head[i].id else Fixture.HUGE_BASE + more[i - head.size] },
            { i -> if (i < head.size) head[i].kind else Kind.Song },
        ) { i -> if (i < head.size) head[i] else songRow(Fixture.hugeTrack(more[i - head.size])) }
    }

    private fun artistTracks(artist: Long): List<Track> = byArtist[artist].orEmpty().flatMap { al -> al.tracks.filter { artist in it.artists } }

    private fun artistRow(a: Named) =
        Row(a.id, Kind.Artist, a.name, "", null, count = byArtist[a.id].orEmpty().size, count2 = artistTracks(a.id).size)

    private fun albumRow(a: Album) = Row(
        a.id, Kind.Album, a.title, a.credit, ref(a.tracks.firstOrNull()), year = a.year, count = a.tracks.size,
    )

    private fun songRow(t: Track): Row {
        val album = albumById[t.album]
        return Row(
            t.id, Kind.Song, t.title, t.credit, ref(t),
            no = t.no, durationMs = t.ms, year = album?.year, album = album?.title,
        )
    }

    private fun playlistRow(p: PlaylistDoc) =
        Row(p.id, Kind.Playlist, p.name, "", null, count = p.tracks.count { it in trackById }, writable = p.writable)

    private fun sortedAlbums(list: List<Album>) = sorted(list, false) { it.title }

    private fun merged(docs: List<PlaylistDoc>): List<PlaylistDoc> = docs.sortedWith { a, b ->
        val c = utf8(fold(a.name), fold(b.name))
        val sa = a.source
        val sb = b.source
        when {
            c != 0 -> c
            sa != null && sb != null -> utf8(sa, sb)
            sa != null -> -1
            sb != null -> 1
            else -> a.id.compareTo(b.id)
        }
    }

    private fun fold(name: String): String = buildString { for (ch in name) append(if (ch in 'A'..'Z') ch + ('a' - 'A') else ch) }

    private fun utf8(a: String, b: String): Int {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        for (i in 0 until minOf(x.size, y.size)) {
            val c = (x[i].toInt() and BYTE) - (y[i].toInt() and BYTE)
            if (c != 0) return c
        }
        return x.size - y.size
    }

    private fun <T> sorted(items: List<T>, the: Boolean, name: (T) -> String): List<T> {
        val keys = items.map { Section.key(name(it), the).sort }
        return items.indices.sortedWith { a, b ->
            val c = Section.compare(keys[a], keys[b])
            if (c != 0) c else Section.compare(name(items[a]), name(items[b]))
        }.map { items[it] }
    }

    private companion object {
        const val DSD_BASE = 44100
        const val BYTE = 0xff
        const val BITS = 8L
        const val SECOND = 1_000L
        const val MINUTE = 60_000L
        const val SECONDS = 60L
        const val LOSSY_KBPS = 128L
        const val MB_TENTH = 100_000L
        const val TEN = 10L
    }
}
