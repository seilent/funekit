package net.seilent.funekit.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import net.seilent.funekit.core.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

enum class Edge { Main, Empty, Huge, Unavailable }

data class Caps(
    val lease: Boolean = true,
    val scanProgress: Boolean = true,
    val scanFailed: Boolean = true,
    val rowFields: Boolean = true,
    val trackGenre: Boolean = true,
    val artRefs: Boolean = true,
    val fetchDefault: Boolean = false,
    val outcomes: Boolean = false,
    val pinCap: Int? = null,
    val firstIndex: Boolean = false,
)

class Subject(
    val player: Player,
    val library: Library,
    val quickplay: Quickplay,
    val favorites: Favorites,
    val playlists: Playlists,
    val lyrics: Lyrics,
    val leaseMs: Long,
    val elapsedNanos: () -> Long,
    val failNextScan: (suspend () -> Unit)? = null,
    val built: () -> Long,
    val loads: () -> Long,
    val imageSide: (ByteArray) -> Int,
    private val wait: Wait,
    val caps: Caps = Caps(),
    val change: suspend () -> Unit = {},
    val dropSome: suspend (Long) -> Unit = { folder -> library.removeRoot(library.roots.value.single { it.id == folder }.uri) },
    val engine: EngineSubject? = null,
    val eq: Eq? = null,
    val scrobbling: Scrobbling? = null,
    val busy: (suspend (String) -> Unit)? = null,
    val card: (suspend (Card) -> Unit)? = null,
) {
    suspend fun <T> within(block: suspend () -> T): T = wait.within(block)

    suspend fun <T> until(flow: StateFlow<T>, p: (T) -> Boolean): T = within { flow.first(p) }

    suspend fun saw(events: StateFlow<List<EngineEvent>>, p: (EngineEvent) -> Boolean): List<EngineEvent> = until(events) { list -> list.any(p) }
}

interface Wait {
    suspend fun <T> within(block: suspend () -> T): T
}

suspend fun LibraryView.allRows(listing: Listing, count: Int = 50): List<Row> {
    val first = page(listing, 0, count)
    val rows = first.rows.toMutableList()
    while (rows.size < first.total) {
        val next = page(listing, rows.size, count).rows
        if (next.isEmpty()) break
        rows += next
    }
    return rows
}

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.events(player: Player): StateFlow<List<EngineEvent>> {
    val seen = MutableStateFlow<List<EngineEvent>>(emptyList())
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { player.events.collect { e -> seen.update { it + e } } }
    return seen
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class SeamContract {
    protected abstract fun TestScope.open(edge: Edge): Subject

    protected suspend fun songs(s: Subject, n: Int): List<Long> = s.library.view().page(Listing.Songs, 0, n).rows.map { it.id }

    protected suspend fun queue(s: Subject): List<Long> = s.library.view().allRows(Listing.Queue).map { it.id }

    private fun revision(s: Subject): Long = s.player.state.value.queueRevision

    protected fun ms(s: Subject): Long = s.player.state.value.anchor.ms(s.elapsedNanos())

    private suspend fun edit(s: Subject, block: suspend () -> Unit): PlayerState {
        val r = s.player.state.value.queueRevision
        block()
        return s.until(s.player.state) { it.queueRevision > r }
    }

    private suspend fun playing(s: Subject, track: Long): PlayerState = s.until(s.player.state) { it.playing && it.track == track && (s.engine == null || it.input != null) }

    private suspend fun unsupported(block: suspend () -> Unit) {
        val e = runCatching { block() }.exceptionOrNull()
        assertTrue("$e", e is UnsupportedOperationException)
    }

    private suspend fun outcome(s: Subject, typed: (Throwable?) -> Boolean, block: suspend () -> Unit) {
        val e = runCatching { block() }.exceptionOrNull()
        assertTrue("$e", if (s.caps.outcomes) typed(e) else e !is Expired && e !is Stale && e !is Disconnected)
    }

    private suspend fun walk(view: LibraryView, folder: Long): List<Long> {
        val rows = view.allRows(Listing.Folder(folder))
        val kinds = rows.map { it.kind }
        assertEquals(kinds.sortedBy { it != Kind.Folder }, kinds)
        return rows.flatMap { if (it.kind == Kind.Folder) walk(view, it.id) else listOf(it.id) }
    }

    private suspend fun noArt(view: LibraryView) {
        assertTrue(view.allRows(Listing.Albums).all { it.art == null })
        assertTrue(view.page(Listing.Songs, 0, 10).rows.all { it.art == null && view.artRef(it.id) == null })
        assertNull(view.art(ArtRef.Cover(-1, "cover.jpg"), SIDE))
    }

    private suspend fun albumUnder(view: LibraryView, folder: Long): Row = view.allRows(Listing.Albums).first { a ->
        view.page(Listing.AlbumTracks(a.id), 0, 1).rows.single().let { view.trackIds(it.id)?.folders?.contains(folder) == true }
    }

    private suspend fun pagesWhole(view: LibraryView, listing: Listing, kind: Kind): List<Row> {
        val p = view.page(listing, 0, 2)
        assertEquals(view.generation, p.generation)
        val rows = view.allRows(listing, 2)
        assertEquals(p.total, rows.size)
        assertTrue(listing.toString(), rows.isNotEmpty())
        assertTrue(rows.all { it.kind == kind })
        return rows
    }

    @Test
    fun pagesAreGenerationTaggedWithStableIds() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        assertEquals(s.library.generation.value, view.generation)
        val p = view.page(Listing.Songs, 0, 10)
        assertEquals(view.generation, p.generation)
        assertEquals(0, p.offset)
        assertTrue(p.total > 10)
        assertEquals(10, p.rows.size)
        assertEquals(10, p.rows.map { it.id }.distinct().size)
        assertTrue(p.rows.all { it.kind == Kind.Song })
        assertEquals(p.rows, view.page(Listing.Songs, 0, 10).rows)
        assertEquals(3, view.page(Listing.Songs, p.total - 3, 10).rows.size)
        val past = view.page(Listing.Songs, p.total + 5, 10)
        assertEquals(p.total, past.offset)
        assertTrue(past.rows.isEmpty())
    }

    @Test
    fun genresPage() = runTest {
        val s = open(Edge.Main)
        pagesWhole(s.library.view(), Listing.Genres, Kind.Genre)
    }

    @Test
    fun playlistsPage() = runTest {
        val s = open(Edge.Main)
        pagesWhole(s.library.view(), Listing.Playlists, Kind.Playlist)
    }

    @Test
    fun artistAlbumsPage() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val artist = view.page(Listing.Artists, 0, 1).rows.single()
        pagesWhole(view, Listing.ArtistAlbums(artist.id), Kind.Album)
    }

    @Test
    fun genreAlbumsPage() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val genre = view.page(Listing.Genres, 0, 1).rows.single()
        if (s.library.caps.genreAlbums) {
            pagesWhole(view, Listing.GenreAlbums(genre.id), Kind.Album)
        } else {
            unsupported { view.page(Listing.GenreAlbums(genre.id), 0, 2) }
        }
    }

    @Test
    fun playlistTracksPage() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val playlist = view.page(Listing.Playlists, 0, 1).rows.single()
        pagesWhole(view, Listing.PlaylistTracks(playlist.id), Kind.Song)
    }

    @Test
    fun idsStayStableAcrossGenerations() = runTest {
        val s = open(Edge.Main)
        val old = s.library.view()
        val before = old.allRows(Listing.Albums)
        s.change()
        s.library.rescan()
        s.until(s.library.generation) { it > old.generation }
        assertEquals(before, s.library.view().allRows(Listing.Albums))
    }

    @Test
    fun aViewByGenerationReadsThatSnapshotUntilItsLease() = runTest {
        val s = open(Edge.Main)
        val g = s.library.generation.value
        val first = s.library.view().page(Listing.Songs, 0, 3)
        s.change()
        s.library.rescan()
        s.until(s.library.generation) { it > g }
        val old = s.library.view(g)
        assertEquals(g, old.generation)
        if (s.caps.lease) {
            val page = old.page(Listing.Songs, 0, 3)
            assertEquals(g, page.generation)
            assertEquals(first.rows, page.rows)
            advanceTimeBy(s.leaseMs)
        }
        assertTrue(runCatching { s.library.view(g).page(Listing.Songs, 0, 3) }.exceptionOrNull() is Expired)
    }

    @Test
    fun leaseExpiresOldViewsButNeverTheCurrentOne() = runTest {
        val s = open(Edge.Main)
        val old = s.library.view()
        val g = old.generation
        val ref = if (s.caps.artRefs) checkNotNull(old.page(Listing.Albums, 0, 50).rows.firstNotNullOfOrNull { it.art }) else ArtRef.Cover(-1, "cover.jpg")
        s.change()
        s.library.rescan()
        s.until(s.library.generation) { it > g }
        if (s.caps.lease) {
            val early = runCatching { old.page(Listing.Albums, 0, 5) }
            assertTrue(early.isSuccess || early.exceptionOrNull() is Expired)
            early.getOrNull()?.let { assertEquals(g, it.generation) }
            advanceTimeBy(s.leaseMs)
        }
        val page = runCatching { old.page(Listing.Albums, 0, 5) }.exceptionOrNull()
        assertTrue(page is Expired)
        assertEquals(g, (page as Expired).generation)
        assertTrue(runCatching { old.sections(Listing.Albums) }.exceptionOrNull() is Expired)
        assertTrue(runCatching { old.jump(Listing.Albums, 1, 5) }.exceptionOrNull() is Expired)
        assertTrue(runCatching { old.art(ref, SIDE) }.exceptionOrNull() is Expired)
        assertTrue(runCatching { s.player.play(Listing.Songs, g, 0, 0) }.exceptionOrNull() is Expired)
        val fresh = s.library.view()
        assertEquals(s.library.generation.value, fresh.generation)
        advanceTimeBy(s.leaseMs * 3)
        assertEquals(fresh.generation, fresh.page(Listing.Albums, 0, 5).generation)
    }

    @Test
    fun scanReportsProgressThenPublishes() = runTest {
        val s = open(Edge.Main)
        val seen = CopyOnWriteArrayList<ScanProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.library.scan.toList(seen) }
        val g = s.library.generation.value
        s.change()
        s.library.rescan()
        s.until(s.library.generation) { it > g }
        val done = s.until(s.library.scan) { !it.running && seen.any { p -> p.running } }
        assertFalse(done.failed)
        if (s.caps.scanProgress) {
            assertTrue(done.found > 0)
            val running = seen.filter { it.running }.map { it.found }
            assertEquals(running.sorted(), running)
        }
    }

    @Test
    fun failedScanKeepsTheGeneration() = runTest {
        val s = open(Edge.Main)
        val g = s.library.generation.value
        if (s.caps.scanFailed) {
            s.change()
            checkNotNull(s.failNextScan)()
            s.library.rescan()
            val done = s.until(s.library.scan) { it.failed }
            assertFalse(done.running)
        } else {
            val seen = CopyOnWriteArrayList<ScanProgress>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.library.scan.toList(seen) }
            s.library.rescan()
            assertFalse(s.until(s.library.scan) { !it.running && seen.any { p -> p.running } }.failed)
        }
        assertEquals(g, s.library.generation.value)
    }

    @Test
    fun emptyLibraryAnswersEmptyPages() = runTest {
        val s = open(Edge.Empty)
        val view = s.library.view()
        val listings = listOf(
            Listing.Artists, Listing.Albums, Listing.Songs, Listing.Genres, Listing.Playlists, Listing.Folder(),
            Listing.Search("a"), Listing.Search("a", Kind.Song), Listing.Pins, Listing.History, Listing.NewAlbums, Listing.Queue,
            Listing.Favorites,
        )
        for (l in listings) {
            val p = view.page(l, 0, 20)
            assertEquals(l.toString(), 0, p.total)
            assertTrue(p.rows.isEmpty())
            assertEquals(0, view.sections(l).size)
        }
        assertEquals(0, view.jump(Listing.Songs, 5, 20)?.total)
        s.player.play(emptyList(), 0, 0)
        val st = s.until(s.player.state) { it.queueSize == 0 }
        assertEquals(-1, st.index)
        assertNull(st.track)
    }

    @Test
    fun anchorExtrapolatesOnlyWhilePlaying() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 3)
        s.player.play(ids, 0, 1_000)
        val st = playing(s, ids[0])
        assertTrue(st.durationMs > 20_000)
        val start = ms(s)
        assertEquals(1_000L, start)
        advanceTimeBy(2_000L)
        assertEquals(start + 2_000, ms(s))
        s.player.pause()
        s.until(s.player.state) { !it.playing }
        val paused = ms(s)
        advanceTimeBy(5_000L)
        assertEquals(paused, ms(s))
        s.player.resume()
        s.until(s.player.state) { it.playing && it.anchor.running }
        val resumed = ms(s)
        advanceTimeBy(1_000L)
        assertEquals(resumed + 1_000, ms(s))
        s.player.seek(10_000)
        s.until(s.player.state) { it.anchor.ms(s.elapsedNanos()) == 10_000L }
        advanceTimeBy(500L)
        assertEquals(10_500L, ms(s))
        s.player.toggle()
        s.until(s.player.state) { !it.playing }
        s.player.toggle()
        s.until(s.player.state) { it.playing }
    }

    @Test
    fun trackEndPlaysTheNextThenStops() = runTest {
        val s = open(Edge.Main)
        val seen = events(s.player)
        val (a, b) = songs(s, 2)
        s.player.play(listOf(a, b), 0, 0)
        val first = playing(s, a)
        advanceTimeBy(first.durationMs + 1)
        s.saw(seen) { it == EngineEvent.Ended(a) }
        s.saw(seen) { it == EngineEvent.Started(b) }
        val second = playing(s, b)
        advanceTimeBy(second.durationMs + 1)
        val stopped = s.until(s.player.state) { !it.playing }
        assertEquals(stopped.durationMs, stopped.anchor.ms(s.elapsedNanos()))
        val ended = seen.value.count { it == EngineEvent.Ended(b) }
        assertEquals(1, ended)
        s.player.resume()
        s.until(s.player.state) { it.playing }
        advanceTimeBy(100L)
        assertEquals(ended, seen.value.count { it == EngineEvent.Ended(b) })
    }

    @Test
    fun repeatOneReplaysTheTrack() = runTest {
        val s = open(Edge.Main)
        val seen = events(s.player)
        val (a, b) = songs(s, 2)
        s.player.play(listOf(a, b), 0, 0)
        s.player.repeat(Repeat.One)
        val st = s.until(s.player.state) { it.repeat == Repeat.One && it.playing && it.track == a }
        advanceTimeBy(st.durationMs + 1)
        s.until(seen) { list -> list.count { it == EngineEvent.Started(a) } == 2 }
        assertEquals(a, s.player.state.value.track)
    }

    @Test
    fun queuePagesFollowEdits() = runTest {
        val s = open(Edge.Main)
        val (a, b, c, d, e) = songs(s, 5)
        edit(s) { s.player.play(listOf(a, b, c, d, e), 0, 0) }
        val page = s.library.view().page(Listing.Queue, 0, 10)
        assertEquals(s.library.view().generation, page.generation)
        assertEquals(listOf(a, b, c, d, e), page.rows.map { it.id })
        assertTrue(page.rows.all { it.kind == Kind.Song })
        edit(s) { s.player.move(4, 0, page.revision) }
        assertEquals(listOf(e, a, b, c, d), queue(s))
        edit(s) { s.player.remove(1, revision(s)) }
        assertEquals(listOf(e, b, c, d), queue(s))
        assertEquals(4, s.player.state.value.queueSize)
        assertEquals(listOf(b, c), s.library.view().page(Listing.Queue, 1, 2).rows.map { it.id })
    }

    @Test
    fun playListingResolvesOnThePlayerSide() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val songs = view.allRows(Listing.Songs)
        s.player.play(Listing.Songs, view.generation, 3, 0)
        val st = s.until(s.player.state) { it.queueSize == songs.size && it.track == songs[3].id }
        assertEquals(3, st.index)
        assertEquals(songs.map { it.id }, queue(s))
    }

    @Test
    fun hugeListingPlaysWithoutLoadingAll() = runTest {
        val s = open(Edge.Huge)
        val view = s.library.view()
        val row = view.page(Listing.Songs, 40_000, 1).rows.single()
        val built = s.built()
        s.player.play(Listing.Songs, view.generation, 40_000, 0)
        val st = s.until(s.player.state) { it.queueSize == HUGE && it.track == row.id }
        assertEquals(40_000, st.index)
        assertEquals(listOf(row.id), view.page(Listing.Queue, 40_000, 1).rows.map { it.id })
        assertTrue(s.built() - built <= 1)
    }

    @Test
    fun playStartsAndQueueEdits() = runTest {
        val s = open(Edge.Main)
        val seen = events(s.player)
        val (a, b, c, d, e) = songs(s, 5)
        s.player.play(listOf(a, b, c), 1, 0)
        playing(s, b)
        s.saw(seen) { it == EngineEvent.Started(b) }
        edit(s) { s.player.enqueue(listOf(d)) }
        assertEquals(listOf(a, b, c, d), queue(s))
        edit(s) { s.player.playNext(listOf(e)) }
        assertEquals(listOf(a, b, e, c, d), queue(s))
        assertEquals(1, s.player.state.value.index)
        edit(s) { s.player.move(4, 0, revision(s)) }
        assertEquals(listOf(d, a, b, e, c), queue(s))
        assertEquals(2, s.player.state.value.index)
        edit(s) { s.player.move(2, 4, revision(s)) }
        assertEquals(listOf(d, a, e, c, b), queue(s))
        assertEquals(4, s.player.state.value.index)
        edit(s) { s.player.remove(0, revision(s)) }
        assertEquals(listOf(a, e, c, b), queue(s))
        assertEquals(3, s.player.state.value.index)
        s.player.next()
        s.until(s.player.state) { it.index == 3 && it.track == b }
        s.player.prev()
        s.until(s.player.state) { it.index == 2 && it.track == c }
        s.saw(seen) { it == EngineEvent.QueueChanged }
        edit(s) { s.player.clear() }
        val st = s.until(s.player.state) { it.queueSize == 0 }
        assertEquals(-1, st.index)
        assertFalse(st.playing)
        assertTrue(queue(s).isEmpty())
    }

    @Test
    fun clearTurnsShuffleOff() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 4)
        s.player.play(ids, 0, 0)
        playing(s, ids[0])
        edit(s) { s.player.shuffle(true) }
        s.until(s.player.state) { it.shuffle }
        edit(s) { s.player.clear() }
        assertFalse(s.until(s.player.state) { it.queueSize == 0 }.shuffle)
    }

    @Test
    fun prevPastThreeSecondsRestartsTheTrackAndAtTheFirstTrackRestartsIt() = runTest {
        val s = open(Edge.Main)
        val (a, b) = songs(s, 2)
        s.player.play(listOf(a, b), 1, 0)
        playing(s, b)
        advanceTimeBy(4_000L)
        s.player.prev()
        s.until(s.player.state) { it.index == 1 && it.track == b && ms(s) < 1_000 }
        s.player.prev()
        playing(s, a)
        assertEquals(0, s.player.state.value.index)
        advanceTimeBy(1_000L)
        s.player.prev()
        s.until(s.player.state) { it.index == 0 && it.track == a && ms(s) < 1_000 }
        assertTrue(s.player.state.value.playing)
    }

    @Test
    fun removingTheCurrentLastTrackPlaysTheOneBeforeIt() = runTest {
        val s = open(Edge.Main)
        val (a, b, c) = songs(s, 3)
        s.player.play(listOf(a, b, c), 2, 0)
        playing(s, c)
        edit(s) { s.player.remove(2, revision(s)) }
        val st = playing(s, b)
        assertEquals(1, st.index)
        assertEquals(listOf(a, b), queue(s))
    }

    @Test
    fun aSeekAfterTheQueueEndsIsIgnored() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 1)
        s.player.play(ids, 0, 0)
        val st = playing(s, ids[0])
        s.player.seek(st.durationMs - 500)
        s.until(s.player.state) { it.anchor.ms(s.elapsedNanos()) >= st.durationMs - 500 }
        advanceTimeBy(1_000L)
        val ended = s.until(s.player.state) { !it.playing }
        s.player.seek(10_000)
        advanceTimeBy(100L)
        assertEquals(ended.anchor, s.player.state.value.anchor)
        assertFalse(s.player.state.value.playing)
    }

    @Test
    fun stopPausesAtTheStartAndKeepsTheQueue() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 2)
        s.player.play(ids, 1, 5_000)
        playing(s, ids[1])
        s.player.stop()
        val stopped = s.until(s.player.state) { !it.playing && !it.anchor.running && it.anchor.frame == 0L }
        assertEquals(ids[1], stopped.track)
        assertEquals(1, stopped.index)
        assertEquals(ids, queue(s))
        advanceTimeBy(1_000L)
        assertEquals(0L, ms(s))
        s.player.seek(2_000)
        s.until(s.player.state) { it.anchor.frame > 0L }
        assertEquals(2_000L, ms(s))
        assertFalse(s.player.state.value.playing)
    }

    @Test
    fun shuffleOffRestoresTheOriginalOrder() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 8)
        s.player.play(ids, 2, 0)
        playing(s, ids[2])
        edit(s) { s.player.shuffle(true) }
        var st = s.until(s.player.state) { it.shuffle }
        assertEquals(ids[2], st.track)
        assertEquals(0, st.index)
        assertEquals(ids.sorted(), queue(s).sorted())
        edit(s) { s.player.shuffle(false) }
        st = s.until(s.player.state) { !it.shuffle }
        assertEquals(ids[2], st.track)
        assertEquals(2, st.index)
        assertEquals(ids, queue(s))
        s.player.play(ids, ids.lastIndex, 0)
        s.player.repeat(Repeat.All)
        s.until(s.player.state) { it.repeat == Repeat.All && it.index == ids.lastIndex }
        s.player.next()
        s.until(s.player.state) { it.index == 0 && it.track == ids[0] }
    }

    @Test
    fun quickplayPagesAreGenerationTaggedAndRevised() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val seeded = view.pins(0, 100).rows
        val unpinned = s.quickplay.revision.value
        for (r in seeded) s.quickplay.unpin(r.kind, r.id)
        if (seeded.isNotEmpty()) s.until(s.quickplay.revision) { it > unpinned }
        assertEquals(0, view.pins(0, 100).total)
        val album = view.page(Listing.Albums, 0, 1).rows.single()
        val artist = view.page(Listing.Artists, 0, 1).rows.single()
        val song = view.page(Listing.Songs, 0, 1).rows.single()
        var r = s.quickplay.revision.value
        s.quickplay.pin(Kind.Album, album.id)
        r = s.until(s.quickplay.revision) { it > r }
        s.quickplay.pin(Kind.Artist, artist.id)
        s.quickplay.pin(Kind.Song, song.id)
        s.quickplay.pin(Kind.Album, album.id)
        s.until(s.quickplay.revision) { it >= r + 2 }
        val pins = view.pins(0, 100)
        assertEquals(view.generation, pins.generation)
        assertEquals(listOf(Kind.Album to album.id, Kind.Artist to artist.id, Kind.Song to song.id), pins.rows.map { it.kind to it.id })
        r = s.quickplay.revision.value
        s.quickplay.unpin(Kind.Artist, artist.id)
        s.until(s.quickplay.revision) { it > r }
        assertEquals(listOf(album.id, song.id), view.pins(0, 100).rows.map { it.id })

        r = s.quickplay.revision.value
        s.player.play(listOf(song.id), 0, 0)
        s.until(s.quickplay.revision) { it > r }
        val history = view.history(0, 50)
        assertEquals(view.generation, history.generation)
        assertEquals(song.id, history.rows.first().id)
        assertTrue(history.rows.all { it.kind == Kind.Song })
        assertEquals(history.rows.size, history.rows.map { it.id }.distinct().size)

        val fresh = view.newAlbums(0, 50)
        assertEquals(view.generation, fresh.generation)
        assertTrue(fresh.rows.isNotEmpty())
        assertTrue(fresh.rows.all { it.kind == Kind.Album })
    }

    @Test
    fun sectionBytesDecodeIntoTheBuckets() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        assertEquals(38, Section.count)
        val seen = BooleanArray(Section.count)
        for ((listing, the) in listOf(Listing.Artists to true, Listing.Albums to false, Listing.Songs to false)) {
            val bytes = view.sections(listing)
            val rows = view.allRows(listing)
            assertEquals(rows.size, bytes.size)
            rows.forEachIndexed { i, r ->
                val letter = bytes[i].toInt()
                assertEquals(r.title, Section.key(r.title, the).letter, letter)
                seen[letter] = true
                if (i > 0) assertTrue(r.title, Section.compare(Section.key(rows[i - 1].title, the).sort, Section.key(r.title, the).sort) <= 0)
            }
        }
        val letters = (0 until Section.count).map { Section.letter(it) }
        assertEquals(letters, letters.filterIndexed { i, _ -> seen[i] })
    }

    @Test
    fun hugeLibraryPagesWithoutLoadingAll() = runTest {
        val s = open(Edge.Huge)
        val view = s.library.view()
        val bytes = view.sections(Listing.Songs)
        assertEquals(HUGE, bytes.size)
        assertEquals(Section.count, bytes.toSet().size)
        val built = s.built()
        val pages = listOf(0, HUGE / 2, HUGE - 10).map { view.page(Listing.Songs, it, 10) }
        assertTrue(s.built() - built <= 30)
        for (p in pages) {
            assertEquals(HUGE, p.total)
            assertEquals(10, p.rows.size)
            p.rows.forEachIndexed { i, r -> assertEquals(r.title, Section.key(r.title).letter, bytes[p.offset + i].toInt()) }
        }
        assertEquals(30, pages.flatMap { it.rows }.map { it.id }.distinct().size)
    }

    @Test
    fun scrubRequestsAreConflated() = runTest {
        val s = open(Edge.Huge)
        val bytes = s.library.view().sections(Listing.Songs)
        val loads = s.loads()
        val pages = (0 until Section.count).map { letter -> async { s.library.view().jump(Listing.Songs, letter, 10) } }.awaitAll()
        val last = checkNotNull(pages.last()) { "newest jump returned no page" }
        assertEquals(Section.first(bytes, Section.count - 1), last.offset)
        assertEquals(Section.count - 1, Section.key(last.rows.first().title).letter)
        pages.forEachIndexed { letter, p -> if (p != null) assertEquals(letter, Section.key(p.rows.first().title).letter) }
        assertTrue(s.loads() - loads <= 2)
        val one = checkNotNull(s.library.view().jump(Listing.Songs, 1, 10)) { "single jump returned no page" }
        assertEquals(1, Section.key(one.rows.first().title).letter)
    }

    @Test
    fun lyricsFetchIsOffUntilSwitchedOn() = runTest {
        val s = open(Edge.Main)
        val start = s.caps.fetchDefault
        assertEquals(start, s.lyrics.fetch.value)
        s.lyrics.setFetch(!start)
        assertEquals(!start, s.until(s.lyrics.fetch) { it != start })
        s.lyrics.setFetch(start)
        assertEquals(start, s.until(s.lyrics.fetch) { it == start })
    }

    @Test
    fun artBytesForRowRefs() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        if (!s.caps.artRefs) {
            noArt(view)
            return@runTest
        }
        val refs = view.allRows(Listing.Albums).mapNotNull { it.art }
        assertTrue(refs.any { it is ArtRef.Embedded } && refs.any { it is ArtRef.Cover })
        for (ref in refs.take(5)) assertTrue(checkNotNull(view.art(ref, SIDE)).isNotEmpty())
        assertNull(view.art(ArtRef.Cover(-1, "cover.jpg"), SIDE))
    }

    @Test
    fun unknownIdsAnswerNull() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        assertNull(view.trackInfo(-1))
        assertNull(view.artRef(-1))
        assertNull(s.lyrics.lyrics(-1))
        assertNull(view.trackIds(-1))
        assertFalse(s.favorites.has(-1))
        assertFalse(view.pinned(Kind.Song, -1))
        val song = songs(s, 1).single()
        assertTrue(checkNotNull(view.trackInfo(song)).path.isNotEmpty())
    }

    @Test
    fun rowsCarryNumbersDurationsYearsAndCounts() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.allRows(Listing.Albums).first { (it.count ?: 0) > 2 }
        val tracks = view.allRows(Listing.AlbumTracks(album.id))
        assertEquals(album.count, tracks.size)
        if (s.caps.rowFields) {
            assertEquals((1..tracks.size).toList(), tracks.map { it.no })
            assertTrue(tracks.all { (it.durationMs ?: 0) > 0 })
            assertTrue(tracks.all { it.album == album.title && it.year == album.year })
            assertTrue(album.year != null)
        } else {
            assertTrue(tracks.all { it.no == null && it.durationMs == null && it.year == null })
            assertNull(album.year)
        }
        for (artist in view.allRows(Listing.Artists)) {
            val albums = view.page(Listing.ArtistAlbums(artist.id), 0, 1).total
            if (s.caps.rowFields) assertEquals(artist.title, albums, artist.count) else assertNull(artist.title, artist.count)
            assertEquals(artist.title, view.page(Listing.ArtistTracks(artist.id), 0, 1).total, artist.count2)
        }
        for (list in view.allRows(Listing.Playlists)) assertEquals(view.page(Listing.PlaylistTracks(list.id), 0, 1).total, list.count)
    }

    @Test
    fun artistTracksFollowTheArtistAlbumOrder() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        for (artist in view.allRows(Listing.Artists)) {
            val albums = view.allRows(Listing.ArtistAlbums(artist.id))
            if (s.caps.rowFields) {
                val years = albums.map { checkNotNull(it.year) }
                assertEquals(artist.title, years.sorted(), years)
            } else {
                assertTrue(artist.title, albums.all { it.year == null })
            }
            val want = albums.flatMap { a -> view.allRows(Listing.AlbumTracks(a.id)).map { it.id } }
            assertEquals(artist.title, want, view.allRows(Listing.ArtistTracks(artist.id)).map { it.id })
        }
    }

    @Test
    fun genreTracksAreInSongOrder() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val songs = view.allRows(Listing.Songs).map { it.id }
        for (genre in view.allRows(Listing.Genres)) {
            val tracks = view.allRows(Listing.GenreTracks(genre.id)).map { it.id }
            val mine = if (s.library.caps.genreAlbums) {
                view.allRows(Listing.GenreAlbums(genre.id)).flatMap { a -> view.allRows(Listing.AlbumTracks(a.id)).map { it.id } }.toSet()
            } else {
                assertTrue(genre.title, tracks.isNotEmpty())
                tracks.toSet()
            }
            assertEquals(genre.title, songs.filter { it in mine }, tracks)
        }
    }

    @Test
    fun folderTracksAreDepthFirstWithSubfoldersFirst() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        for (root in view.allRows(Listing.Folder())) {
            val want = walk(view, root.id)
            assertTrue(want.isNotEmpty())
            if (s.library.caps.folderTracks) {
                assertEquals(want, view.allRows(Listing.FolderTracks(root.id)).map { it.id })
            } else {
                unsupported { view.page(Listing.FolderTracks(root.id), 0, 1) }
                edit(s) { s.player.play(Listing.FolderTracks(root.id), view.generation, 0, 0) }
                assertEquals(want, queue(s))
            }
            assertEquals(want.size, root.count)
        }
    }

    @Test
    fun trackIdsNameTheGroupsOfATrack() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val caps = s.library.caps
        val artist = view.page(Listing.Artists, 0, 1).rows.single()
        val album = view.page(Listing.ArtistAlbums(artist.id), 0, 1).rows.single()
        val song = view.page(Listing.AlbumTracks(album.id), 0, 1).rows.single()
        val ids = checkNotNull(view.trackIds(song.id))
        assertEquals(album.id, ids.album)
        assertTrue(artist.id in ids.artists)
        if (s.caps.trackGenre) {
            val genre = checkNotNull(ids.genre)
            if (caps.genreAlbums) {
                assertTrue(view.allRows(Listing.GenreAlbums(genre)).any { it.id == album.id })
            } else {
                assertTrue(view.allRows(Listing.GenreTracks(genre)).any { it.id == song.id })
            }
        } else {
            assertNull(ids.genre)
        }
        assertTrue(ids.folders.isNotEmpty())
        for (f in ids.folders) {
            val under = if (caps.folderTracks) view.allRows(Listing.FolderTracks(f)).map { it.id } else walk(view, f)
            assertTrue(song.id in under)
        }
        assertNull(view.trackIds(-1))
    }

    @Test
    fun favoritesToggleNewestFirstAndRevise() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val (a, b) = songs(s, 2)
        for (id in listOf(a, b)) if (s.favorites.has(id)) s.favorites.toggle(id)
        var r = s.favorites.revision.value
        assertTrue(s.favorites.toggle(a))
        r = s.until(s.favorites.revision) { it > r }
        assertTrue(s.favorites.toggle(b))
        r = s.until(s.favorites.revision) { it > r }
        assertTrue(s.favorites.has(a) && s.favorites.has(b))
        val page = view.page(Listing.Favorites, 0, 2)
        assertEquals(listOf(b, a), page.rows.map { it.id })
        assertEquals(r, page.revision)
        assertFalse(s.favorites.toggle(a))
        s.until(s.favorites.revision) { it > r }
        assertFalse(s.favorites.has(a))
        assertTrue(view.allRows(Listing.Favorites).none { it.id == a })
    }

    @Test
    fun createNamesTheLowestFreePlaylistNumber() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val song = TrackSet.Ids(songs(s, 1))
        var r = s.playlists.revision.value
        val one = checkNotNull(s.playlists.create(song))
        r = s.until(s.playlists.revision) { it > r }
        val two = checkNotNull(s.playlists.create(song))
        r = s.until(s.playlists.revision) { it > r }
        val rows = view.allRows(Listing.Playlists)
        val first = rows.single { it.id == one }
        assertEquals("playlist 1", first.title)
        assertTrue(first.writable)
        assertEquals(1, first.count)
        assertEquals("playlist 2", rows.single { it.id == two }.title)
        assertEquals(r, view.page(Listing.Playlists, 0, 1).revision)
        s.playlists.delete(one)
        r = s.until(s.playlists.revision) { it > r }
        assertTrue(view.allRows(Listing.Playlists).none { it.id == one })
        val again = checkNotNull(s.playlists.create(song))
        s.until(s.playlists.revision) { it > r }
        assertEquals("playlist 1", view.allRows(Listing.Playlists).single { it.id == again }.title)
    }

    @Test
    fun aCreateHoldsItsTracksByListingAndByIds() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.allRows(Listing.Albums).first { (it.count ?: 0) >= 2 }
        val tracks = view.allRows(Listing.AlbumTracks(album.id)).map { it.id }
        var r = s.playlists.revision.value
        val listed = checkNotNull(s.playlists.create(TrackSet.Listed(Listing.AlbumTracks(album.id), view.generation)))
        r = s.until(s.playlists.revision) { it > r }
        assertEquals(tracks, view.allRows(Listing.PlaylistTracks(listed)).map { it.id })
        val (a, b) = songs(s, 2)
        val ids = checkNotNull(s.playlists.create(TrackSet.Ids(listOf(b, a))))
        s.until(s.playlists.revision) { it > r }
        assertEquals(listOf(b, a), view.allRows(Listing.PlaylistTracks(ids)).map { it.id })
    }

    @Test
    fun aCreateOfNothingMakesNoList() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val before = view.allRows(Listing.Playlists)
        val r = s.playlists.revision.value
        assertNull(s.playlists.create(TrackSet.Ids(listOf(-1))))
        assertEquals(r, s.playlists.revision.value)
        assertEquals(before, view.allRows(Listing.Playlists))
    }

    @Test
    fun anAddByIdsAppendsInItsOrder() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val (a, b, c) = songs(s, 3)
        var r = s.playlists.revision.value
        val id = checkNotNull(s.playlists.create(TrackSet.Ids(listOf(a))))
        r = s.until(s.playlists.revision) { it > r }
        s.playlists.add(id, TrackSet.Ids(listOf(c, a, b)))
        s.until(s.playlists.revision) { it > r }
        assertEquals(listOf(a, c, a, b), view.allRows(Listing.PlaylistTracks(id)).map { it.id })
    }

    @Test
    fun favoritesAddPutsTheBatchFirstAndDropsPresentAndRepeatedTracks() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val (a, b, c) = songs(s, 3)
        for (id in listOf(a, b, c)) if (s.favorites.has(id)) s.favorites.toggle(id)
        var r = s.favorites.revision.value
        assertTrue(s.favorites.toggle(c))
        r = s.until(s.favorites.revision) { it > r }
        val old = view.allRows(Listing.Favorites).map { it.id }
        assertEquals(2, s.favorites.add(TrackSet.Ids(listOf(a, c, a, b))))
        r = s.until(s.favorites.revision) { it > r }
        assertEquals(listOf(a, b) + old, view.allRows(Listing.Favorites).map { it.id })
        assertEquals(0, s.favorites.add(TrackSet.Ids(listOf(b))))
        assertEquals(r, s.favorites.revision.value)
    }

    @Test
    fun favoritesRemoveAndMoveTakeTheRevisionTheRowWasReadAt() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val (a, b, c) = songs(s, 3)
        for (f in view.allRows(Listing.Favorites)) s.favorites.toggle(f.id)
        var r = s.favorites.revision.value
        s.favorites.add(TrackSet.Ids(listOf(a, b, c)))
        r = s.until(s.favorites.revision) { it > r }
        val page = view.page(Listing.Favorites, 0, 3)
        assertEquals(listOf(a, b, c), page.rows.map { it.id })
        assertEquals(r, page.revision)
        s.favorites.move(0, 2, page.revision)
        r = s.until(s.favorites.revision) { it > r }
        assertEquals(listOf(b, c, a), view.allRows(Listing.Favorites).map { it.id })
        assertTrue(runCatching { s.favorites.move(0, 1, page.revision) }.exceptionOrNull() is Stale)
        assertTrue(runCatching { s.favorites.remove(0, page.revision) }.exceptionOrNull() is Stale)
        assertEquals(listOf(b, c, a), view.allRows(Listing.Favorites).map { it.id })
        s.favorites.remove(1, r)
        s.until(s.favorites.revision) { it > r }
        assertEquals(listOf(b, a), view.allRows(Listing.Favorites).map { it.id })
    }

    @Test
    fun deletingTheOriginListClearsTheOrigin() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        var r = s.playlists.revision.value
        val id = checkNotNull(s.playlists.create(TrackSet.Ids(songs(s, 2))))
        r = s.until(s.playlists.revision) { it > r }
        val listing = Listing.PlaylistTracks(id)
        s.player.play(listing, view.generation, 0, 0)
        assertEquals(listing, s.until(s.player.state) { it.origin != null }.origin)
        s.playlists.delete(id)
        s.until(s.playlists.revision) { it > r }
        val st = s.until(s.player.state) { it.origin == null }
        assertEquals(2, st.queueSize)
    }

    @Test
    fun playlistsTakeTheMergeOrder() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        var r = s.playlists.revision.value
        checkNotNull(s.playlists.create(TrackSet.Ids(songs(s, 1))))
        r = s.until(s.playlists.revision) { it > r }
        checkNotNull(s.playlists.create(TrackSet.Ids(songs(s, 1))))
        s.until(s.playlists.revision) { it > r }
        assertEquals(listOf("Commute", "Late Night", "playlist 1", "playlist 2", "お気に入り"), view.allRows(Listing.Playlists).map { it.title })
    }

    @Test
    fun playlistEditsTakeTheRevisionTheIndexWasReadAt() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.allRows(Listing.Albums).first { (it.count ?: 0) >= 3 }
        val tracks = view.allRows(Listing.AlbumTracks(album.id)).map { it.id }
        var r = s.playlists.revision.value
        val id = checkNotNull(s.playlists.create(TrackSet.Listed(Listing.AlbumTracks(album.id), view.generation)))
        r = s.until(s.playlists.revision) { it > r }
        val listing = Listing.PlaylistTracks(id)
        var page = view.page(listing, 0, tracks.size)
        assertEquals(tracks, page.rows.map { it.id })
        val old = page.revision
        s.playlists.move(id, 0, 2, old)
        r = s.until(s.playlists.revision) { it > r }
        page = view.page(listing, 0, tracks.size)
        assertEquals(listOf(tracks[1], tracks[2], tracks[0]) + tracks.drop(3), page.rows.map { it.id })
        assertNotEquals(old, page.revision)
        assertTrue(runCatching { s.playlists.remove(id, 0, old) }.exceptionOrNull() is Stale)
        assertTrue(runCatching { s.playlists.move(id, 0, 1, old) }.exceptionOrNull() is Stale)
        assertEquals(page.rows, view.page(listing, 0, tracks.size).rows)
        s.playlists.remove(id, 1, page.revision)
        s.until(s.playlists.revision) { it > r }
        assertEquals(listOf(tracks[1], tracks[0]) + tracks.drop(3), view.allRows(listing).map { it.id })
    }

    @Test
    fun queueIndexEditsWithAnOldRevisionAreStale() = runTest {
        val s = open(Edge.Main)
        val (a, b, c) = songs(s, 3)
        edit(s) { s.player.play(listOf(a, b, c), 0, 0) }
        val page = s.library.view().page(Listing.Queue, 0, 3)
        assertEquals(revision(s), page.revision)
        edit(s) { s.player.move(0, 1, page.revision) }
        assertEquals(listOf(b, a, c), queue(s))
        assertTrue(runCatching { s.player.move(0, 1, page.revision) }.exceptionOrNull() is Stale)
        assertTrue(runCatching { s.player.remove(0, page.revision) }.exceptionOrNull() is Stale)
        assertEquals(listOf(b, a, c), queue(s))
    }

    @Test
    fun playAtJumpsToTheSlotKeepingTheQueueAndOrigin() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 5)
        s.player.play(Listing.Songs, s.library.view().generation, 0, 0)
        playing(s, ids[0])
        val before = s.player.state.value
        val q = queue(s)
        val seen = events(s.player)
        s.player.playAt(3, before.queueRevision)
        val st = playing(s, ids[3])
        s.saw(seen) { it == EngineEvent.Started(ids[3]) }
        assertEquals(3, st.index)
        assertTrue(ms(s) < 1_000)
        assertEquals(q, queue(s))
        assertEquals(before.queueSize, st.queueSize)
        assertEquals(before.queueRevision, st.queueRevision)
        assertEquals(Listing.Songs, st.origin)
        assertFalse(seen.value.any { it is EngineEvent.Ended })
    }

    @Test
    fun playAtUnderShuffleKeepsThePlayOrder() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 6)
        s.player.play(ids, 0, 0)
        playing(s, ids[0])
        edit(s) { s.player.shuffle(true) }
        val order = queue(s)
        s.player.playAt(4, revision(s))
        val st = playing(s, order[4])
        assertEquals(4, st.index)
        assertTrue(st.shuffle)
        assertEquals(order, queue(s))
    }

    @Test
    fun playAtWithAnOldRevisionIsStale() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 4)
        s.player.play(ids, 0, 0)
        playing(s, ids[0])
        val old = revision(s)
        edit(s) { s.player.move(2, 3, old) }
        val failed = runCatching { s.player.playAt(1, old) }.exceptionOrNull()
        assertTrue(failed is Stale)
        assertEquals(old, (failed as Stale).revision)
        assertEquals(0, s.player.state.value.index)
        assertEquals(ids[0], s.player.state.value.track)
    }

    @Test
    fun playAtOutOfRangeChangesNothing() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 3)
        s.player.play(ids, 1, 0)
        playing(s, ids[1])
        advanceTimeBy(2_000L)
        val before = s.player.state.value
        val at = ms(s)
        assertEquals(2_000L, at)
        s.player.playAt(3, revision(s))
        s.player.playAt(-1, revision(s))
        val st = s.player.state.value
        assertEquals(before.index, st.index)
        assertEquals(before.track, st.track)
        assertEquals(before.anchor, st.anchor)
        assertEquals(at, ms(s))
        assertTrue(st.playing)
    }

    @Test
    fun playAtWhilePausedPlays() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 3)
        s.player.play(ids, 0, 0)
        playing(s, ids[0])
        s.player.pause()
        s.until(s.player.state) { !it.playing }
        s.player.playAt(2, revision(s))
        val st = playing(s, ids[2])
        assertEquals(2, st.index)
        assertTrue(st.playing)
    }

    @Test
    fun playNextAtPutsTheRowAfterTheCurrentOne() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 8)
        s.player.play(ids, 3, 0)
        playing(s, ids[3])
        edit(s) { s.player.playNextAt(6, revision(s)) }
        val first = queue(s)
        assertEquals(ids[6], first[4])
        assertEquals(3, s.player.state.value.index)
        edit(s) { s.player.playNextAt(1, revision(s)) }
        val second = queue(s)
        assertEquals(first[1], second[3])
        assertEquals(2, s.player.state.value.index)
        assertEquals(ids[3], s.player.state.value.track)
        val r = revision(s)
        s.player.playNextAt(3, r)
        assertEquals(second, queue(s))
        assertEquals(r, revision(s))
    }

    @Test
    fun playNextAtWithAnOldRevisionIsStale() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 5)
        s.player.play(ids, 0, 0)
        playing(s, ids[0])
        val old = revision(s)
        edit(s) { s.player.move(3, 4, old) }
        val q = queue(s)
        val failed = runCatching { s.player.playNextAt(4, old) }.exceptionOrNull()
        assertTrue(failed is Stale)
        assertEquals(q, queue(s))
    }

    @Test
    fun aShuffledListingPlayIsOneCommand() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.page(Listing.Search("Amber Field", Kind.Album), 0, 1).rows.single()
        val listing = Listing.AlbumTracks(album.id)
        val tracks = view.allRows(listing).map { it.id }
        assertTrue(tracks.size >= 4)
        assertFalse(s.player.state.value.shuffle)
        val firsts = mutableListOf<Long>()
        repeat(8) {
            if (s.player.state.value.shuffle) edit(s) { s.player.shuffle(false) }
            assertFalse(s.player.state.value.shuffle)
            val r = revision(s)
            s.player.play(listing, view.generation, -1, 0, shuffle = true)
            val st = s.until(s.player.state) { it.queueRevision != r && it.playing }
            assertTrue(st.shuffle)
            assertEquals(tracks.sorted(), queue(s).sorted())
            firsts += checkNotNull(st.track)
        }
        assertFalse(firsts.all { it == tracks[0] })
        val q = queue(s)
        val before = s.player.state.value
        val g = view.generation
        s.change()
        s.library.rescan()
        s.until(s.library.generation) { it > g }
        advanceTimeBy(s.leaseMs)
        assertTrue(runCatching { s.player.play(listing, g, -1, 0, shuffle = true) }.exceptionOrNull() is Expired)
        assertEquals(q, queue(s))
        assertEquals(before.queueRevision, revision(s))
        assertTrue(s.player.state.value.shuffle)
    }

    @Test
    fun pagesReadAcrossAnEditCarryTheirOwnRevisions() = runTest {
        val s = open(Edge.Main)
        val ids = songs(s, 7)
        edit(s) { s.player.play(ids.take(6), 0, 0) }
        val view = s.library.view()
        val head = view.page(Listing.Queue, 0, 3)
        edit(s) { s.player.playNext(listOf(ids[6])) }
        val tail = view.page(Listing.Queue, 3, 3)
        assertNotEquals(head.revision, tail.revision)
        val again = listOf(view.page(Listing.Queue, 0, 3), view.page(Listing.Queue, 3, 4))
        assertEquals(1, again.map { it.revision }.distinct().size)
        assertEquals(queue(s), again.flatMap { it.rows }.map { it.id })
        assertEquals(listOf(ids[0], ids[6], ids[1]), again[0].rows.map { it.id })
    }

    @Test
    fun liveListingsAnswerThroughAnOldViewAndFixedOnesExpire() = runTest {
        val s = open(Edge.Main)
        val old = s.library.view()
        val list = old.page(Listing.Playlists, 0, 1).rows.single()
        val song = songs(s, 1).single()
        edit(s) { s.player.play(listOf(song), 0, 0) }
        s.favorites.toggle(song)
        s.change()
        s.library.rescan()
        s.until(s.library.generation) { it > old.generation }
        advanceTimeBy(s.leaseMs)
        assertTrue(runCatching { old.page(Listing.Albums, 0, 5) }.exceptionOrNull() is Expired)
        val live = listOf(Listing.Queue, Listing.Pins, Listing.History, Listing.Favorites, Listing.Playlists, Listing.PlaylistTracks(list.id))
        for (l in live) {
            val p = old.page(l, 0, 50)
            assertEquals(l.toString(), s.library.view().page(l, 0, 50).rows, p.rows)
            old.sections(l)
        }
        assertEquals(listOf(song), old.page(Listing.Queue, 0, 5).rows.map { it.id })
        edit(s) { s.player.enqueue(Listing.PlaylistTracks(list.id), old.generation) }
    }

    @Test
    fun indexOfFindsAnAnchorAcrossGenerations() = runTest {
        val s = open(Edge.Main)
        val old = s.library.view()
        val caps = s.library.caps
        for (l in listOf(Listing.Songs, Listing.Search("a"))) if (!caps.indexOf(l)) unsupported { old.indexOf(l, Kind.Song, 0) }
        val (listing, kind, other) = if (caps.indexOf(Listing.Songs)) Triple(Listing.Songs, Kind.Song, Kind.Album) else Triple(Listing.Albums, Kind.Album, Kind.Song)
        val rows = old.allRows(listing)
        val gone = old.allRows(Listing.Folder()).last { it.kind == Kind.Folder }
        suspend fun track(row: Row) = if (row.kind == Kind.Song) row.id else old.page(Listing.AlbumTracks(row.id), 0, 1).rows.single().id
        val anchor = rows.last { row -> old.trackIds(track(row))?.folders?.contains(gone.id) != true }
        assertEquals(rows.indexOf(anchor), old.indexOf(listing, kind, anchor.id))
        val g = s.library.generation.value
        s.dropSome(gone.id)
        s.until(s.library.generation) { it > g }
        val fresh = s.library.view()
        val now = fresh.allRows(listing)
        assertTrue(now.size < rows.size)
        assertEquals(now.indexOfFirst { it.id == anchor.id }, fresh.indexOf(listing, kind, anchor.id))
        assertNull(fresh.indexOf(listing, kind, -1))
        assertNull(fresh.indexOf(listing, other, anchor.id))
    }

    @Test
    fun indexOfMatchesTheKindOnAMixedListing() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.page(Listing.Albums, 0, 1).rows.single()
        val song = view.page(Listing.AlbumTracks(album.id), 0, 1).rows.single()
        assertEquals("premise: the first album shares its id with its first song", album.id, song.id)
        var r = s.quickplay.revision.value
        for ((k, id) in listOf(Kind.Album to album.id, Kind.Song to song.id)) {
            if (!view.pinned(k, id)) {
                s.quickplay.pin(k, id)
                r = s.until(s.quickplay.revision) { it > r }
            }
        }
        val pins = view.allRows(Listing.Pins)
        fun at(kind: Kind, id: Long) = pins.indexOfFirst { it.kind == kind && it.id == id }.takeIf { it >= 0 }
        val albumAt = view.indexOf(Listing.Pins, Kind.Album, album.id)
        val songAt = view.indexOf(Listing.Pins, Kind.Song, song.id)
        assertEquals(checkNotNull(at(Kind.Album, album.id)), albumAt)
        assertEquals(checkNotNull(at(Kind.Song, song.id)), songAt)
        assertNotEquals(albumAt, songAt)
        assertEquals(at(Kind.Song, album.id), view.indexOf(Listing.Pins, Kind.Song, album.id))
        assertEquals(at(Kind.Album, song.id), view.indexOf(Listing.Pins, Kind.Album, song.id))
    }

    @Test
    fun aPlaylistReadBeforeARootChangeIsStaleAfterIt() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val folders = view.allRows(Listing.Folder()).filter { it.kind == Kind.Folder }
        val gone = folders.last()
        var r = s.playlists.revision.value
        val id = checkNotNull(s.playlists.create(TrackSet.Listed(Listing.AlbumTracks(albumUnder(view, gone.id).id), view.generation)))
        r = s.until(s.playlists.revision) { it > r }
        s.playlists.add(id, TrackSet.Listed(Listing.AlbumTracks(albumUnder(view, folders.first().id).id), view.generation))
        s.until(s.playlists.revision) { it > r }
        val listing = Listing.PlaylistTracks(id)
        val before = view.allRows(listing)
        val old = view.page(listing, 0, 1).revision
        val g = s.library.generation.value
        s.dropSome(gone.id)
        s.until(s.library.generation) { it > g }
        val after = view.page(listing, 0, before.size)
        assertTrue(after.total < before.size)
        assertTrue(after.rows.all { row -> before.any { it.id == row.id } })
        assertNotEquals(old, after.revision)
        assertTrue(runCatching { s.playlists.remove(id, 0, old) }.exceptionOrNull() is Stale)
        assertEquals(after.rows, view.page(listing, 0, before.size).rows)
        assertEquals(after.total, view.allRows(Listing.Playlists).single { it.id == id }.count)
    }

    @Test
    fun artComesWithinMaxSideFolderCoversIncluded() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        if (!s.caps.artRefs) {
            noArt(view)
            return@runTest
        }
        val refs = view.allRows(Listing.Albums).mapNotNull { it.art }
        for (ref in listOf(refs.first { it is ArtRef.Embedded }, refs.first { it is ArtRef.Cover })) {
            assertTrue(s.imageSide(checkNotNull(view.art(ref, SIDE))) <= SIDE)
            assertTrue(s.imageSide(checkNotNull(view.art(ref, SMALL))) <= SMALL)
        }
    }

    @Test
    fun searchAnswersPerKindInTheLibraryOrder() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val artist = view.page(Listing.Artists, 0, 1).rows.single()
        for (q in listOf(artist.title.take(3), "night")) {
            val all = view.allRows(Listing.Search(q))
            val parts = listOf(Kind.Artist, Kind.Album, Kind.Song).map { k ->
                val rows = view.allRows(Listing.Search(q, k))
                assertTrue(rows.all { it.kind == k })
                assertEquals(all.filter { it.kind == k }, rows)
                rows
            }
            assertEquals(all, parts.flatten())
        }
        assertTrue(view.allRows(Listing.Search(artist.title.take(3), Kind.Artist)).any { it.id == artist.id })
        assertEquals(0, view.page(Listing.Search("night", Kind.Genre), 0, 5).total)
    }

    @Test
    fun pinnedFollowsPinAndUnpin() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.allRows(Listing.Albums).first { !view.pinned(Kind.Album, it.id) && !view.pinned(Kind.Artist, it.id) }
        var r = s.quickplay.revision.value
        s.quickplay.pin(Kind.Album, album.id)
        r = s.until(s.quickplay.revision) { it > r }
        assertTrue(view.pinned(Kind.Album, album.id))
        assertFalse(view.pinned(Kind.Artist, album.id))
        s.quickplay.unpin(Kind.Album, album.id)
        s.until(s.quickplay.revision) { it > r }
        assertFalse(view.pinned(Kind.Album, album.id))
    }

    @Test
    fun lyricsOffsetsRoundTrip() = runTest {
        val s = open(Edge.Main)
        val (a, b) = songs(s, 2)
        assertEquals(0, s.lyrics.offsetMs(a))
        s.lyrics.setOffset(a, 250)
        s.lyrics.setOffset(b, -500)
        assertEquals(250, s.lyrics.offsetMs(a))
        assertEquals(-500, s.lyrics.offsetMs(b))
        s.lyrics.setOffset(a, 0)
        assertEquals(0, s.lyrics.offsetMs(a))
        assertEquals(-500, s.lyrics.offsetMs(b))
    }

    @Test
    fun originIsTheListingPlayed() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.page(Listing.Albums, 0, 1).rows.single()
        val listing = Listing.AlbumTracks(album.id)
        s.player.play(listing, view.generation, 1, 0)
        assertEquals(listing, s.until(s.player.state) { it.origin != null }.origin)
        s.player.enqueue(songs(s, 1))
        assertEquals(listing, s.until(s.player.state) { it.queueSize == (album.count ?: 0) + 1 }.origin)
        s.player.play(songs(s, 2), 0, 0)
        assertNull(s.until(s.player.state) { it.queueSize == 2 }.origin)
    }

    @Test
    fun addsByListingResolveOnThePlayerSide() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val (album, other) = view.page(Listing.Albums, 0, 2).rows
        val first = view.allRows(Listing.AlbumTracks(album.id)).map { it.id }
        val second = view.allRows(Listing.AlbumTracks(other.id)).map { it.id }
        val song = songs(s, 1).single()
        edit(s) { s.player.play(listOf(song), 0, 0) }
        edit(s) { s.player.enqueue(Listing.AlbumTracks(album.id), view.generation) }
        assertEquals(listOf(song) + first, queue(s))
        edit(s) { s.player.playNext(Listing.AlbumTracks(other.id), view.generation) }
        assertEquals(listOf(song) + second + first, queue(s))
        val g = view.generation
        s.change()
        s.library.rescan()
        s.until(s.library.generation) { it > g }
        advanceTimeBy(s.leaseMs)
        assertTrue(runCatching { s.player.enqueue(Listing.AlbumTracks(album.id), g) }.exceptionOrNull() is Expired)
        val made = checkNotNull(s.playlists.create(TrackSet.Ids(listOf(song))))
        assertTrue(runCatching { s.playlists.add(made, TrackSet.Listed(Listing.AlbumTracks(album.id), g)) }.exceptionOrNull() is Expired)
        assertTrue(runCatching { s.playlists.create(TrackSet.Listed(Listing.AlbumTracks(album.id), g)) }.exceptionOrNull() is Expired)
        assertTrue(runCatching { s.favorites.add(TrackSet.Listed(Listing.AlbumTracks(album.id), g)) }.exceptionOrNull() is Expired)
    }

    @Test
    fun anIdsSetWithAnUnknownIdTakesItsKnownIdsOrIsRefusedWhole() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val (a, b, c) = songs(s, 3)
        val f = songs(s, 20).first { !s.favorites.has(it) }
        var r = s.playlists.revision.value
        if (s.library.caps.trackSetIds) {
            val id = checkNotNull(s.playlists.create(TrackSet.Ids(listOf(a, -1, b))))
            r = s.until(s.playlists.revision) { it > r }
            assertEquals(listOf(a, b), view.allRows(Listing.PlaylistTracks(id)).map { it.id })
            s.playlists.add(id, TrackSet.Ids(listOf(-1, c)))
            s.until(s.playlists.revision) { it > r }
            assertEquals(listOf(a, b, c), view.allRows(Listing.PlaylistTracks(id)).map { it.id })
            val fr = s.favorites.revision.value
            assertEquals(1, s.favorites.add(TrackSet.Ids(listOf(-1, f))))
            s.until(s.favorites.revision) { it > fr }
            assertTrue(s.favorites.has(f))
        } else {
            val before = view.allRows(Listing.Playlists)
            assertNull(s.playlists.create(TrackSet.Ids(listOf(a, -1, b))))
            assertEquals(r, s.playlists.revision.value)
            assertEquals(before, view.allRows(Listing.Playlists))
            val id = checkNotNull(s.playlists.create(TrackSet.Ids(listOf(a))))
            s.until(s.playlists.revision) { it > r }
            val add = runCatching { s.playlists.add(id, TrackSet.Ids(listOf(-1, c))) }.exceptionOrNull()
            assertTrue("$add", add is IllegalArgumentException)
            assertEquals(listOf(a), view.allRows(Listing.PlaylistTracks(id)).map { it.id })
            val fav = runCatching { s.favorites.add(TrackSet.Ids(listOf(-1, f))) }.exceptionOrNull()
            assertTrue("$fav", fav is IllegalArgumentException)
            assertFalse(s.favorites.has(f))
        }
    }

    @Test
    fun aViewAfterAnExpiredGenerationReadsAboveIt() = runTest {
        val s = open(Edge.Main)
        val g = s.library.generation.value
        s.change()
        s.library.rescan()
        var floor: Long? = null
        var read: Page? = null
        s.within {
            while (read == null) {
                val view = s.library.view()
                floor?.let { assertTrue("view ${view.generation} after Expired($it)", view.generation > it) }
                val got = runCatching { view.page(Listing.Albums, 0, 5) }
                val e = got.exceptionOrNull()
                if (e is Expired) floor = e.generation else read = got.getOrThrow().takeIf { it.generation > g }
                delay(1)
            }
        }
        assertTrue(checkNotNull(read).generation > g)
        floor?.let { assertTrue(it >= g) }
    }

    @Test
    fun aGoneParentAtTheCurrentGenerationAnswersAnEmptyPage() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val gone = view.allRows(Listing.Folder()).last { it.kind == Kind.Folder }
        val album = albumUnder(view, gone.id)
        val song = view.page(Listing.AlbumTracks(album.id), 0, 1).rows.single().id
        val g = s.library.generation.value
        s.dropSome(gone.id)
        s.until(s.library.generation) { it > g }
        val fresh = s.library.view()
        val caps = s.library.caps
        for (l in listOf(Listing.AlbumTracks(album.id), Listing.Folder(gone.id))) {
            val p = fresh.page(l, 0, 10)
            assertEquals(l.toString(), fresh.generation, p.generation)
            assertEquals(l.toString(), 0, p.total)
            assertTrue(l.toString(), p.rows.isEmpty())
            assertEquals(l.toString(), 0, fresh.sections(l).size)
            assertEquals(l.toString(), 0, fresh.jump(l, 1, 10)?.total ?: 0)
            if (caps.indexOf(l)) assertNull(l.toString(), fresh.indexOf(l, Kind.Song, song))
        }
    }

    @Test
    fun anIdMissAnswersAnEmptyPageAndNeverExpired() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val caps = s.library.caps
        val song = songs(s, 1).single()
        val listings = listOfNotNull(
            Listing.ArtistAlbums(-1), Listing.ArtistTracks(-1), Listing.AlbumTracks(-1), Listing.GenreTracks(-1),
            Listing.PlaylistTracks(-1), Listing.Folder(-1), Listing.GenreAlbums(-1).takeIf { caps.genreAlbums },
            Listing.FolderTracks(-1).takeIf { caps.folderTracks },
        )
        for (l in listings) {
            val p = view.page(l, 0, 10)
            assertEquals(l.toString(), view.generation, p.generation)
            assertEquals(l.toString(), 0, p.total)
            assertTrue(l.toString(), p.rows.isEmpty())
            assertEquals(l.toString(), 0, view.sections(l).size)
            assertEquals(l.toString(), 0, view.jump(l, 1, 10)?.total ?: 0)
            if (caps.indexOf(l)) assertNull(l.toString(), view.indexOf(l, Kind.Song, song))
        }
    }

    @Test
    fun aListThatIsNotWritableRefusesItsEdits() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val lists = view.allRows(Listing.Playlists)
        val list = lists.first { !it.writable }
        val listing = Listing.PlaylistTracks(list.id)
        val page = view.page(listing, 0, 2)
        assertEquals(2, page.rows.size)
        val tracks = view.allRows(listing)
        val r = s.playlists.revision.value
        outcome(s, { it is Refused }) { s.playlists.add(list.id, TrackSet.Ids(songs(s, 1))) }
        outcome(s, { it is Refused }) { s.playlists.remove(list.id, 0, page.revision) }
        outcome(s, { it is Refused }) { s.playlists.move(list.id, 0, 1, page.revision) }
        outcome(s, { it is Refused }) { s.playlists.delete(list.id) }
        assertEquals(r, s.playlists.revision.value)
        assertEquals(tracks, view.allRows(listing))
        assertEquals(lists, view.allRows(Listing.Playlists))
    }

    @Test
    fun aPinPastTheCapAnswersNoSpace() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val free = view.allRows(Listing.Songs).map { it.id }.filter { !view.pinned(Kind.Song, it) }
        var r = s.quickplay.revision.value
        val cap = s.caps.pinCap
        if (cap == null) {
            s.quickplay.pin(Kind.Song, free.first())
            s.until(s.quickplay.revision) { it > r }
            assertTrue(view.pinned(Kind.Song, free.first()))
            return@runTest
        }
        val fill = free.take(cap - view.pins(0, 0).total)
        for (id in fill) s.quickplay.pin(Kind.Song, id)
        if (fill.isNotEmpty()) s.until(s.quickplay.revision) { it > r }
        val full = view.pins(0, cap)
        assertEquals(cap, full.total)
        val extra = free[fill.size]
        r = s.quickplay.revision.value
        outcome(s, { it is NoSpace }) { s.quickplay.pin(Kind.Song, extra) }
        assertEquals(r, s.quickplay.revision.value)
        assertEquals(full.rows, view.pins(0, cap).rows)
        assertFalse(view.pinned(Kind.Song, extra))
        val held = full.rows.first()
        s.quickplay.pin(held.kind, held.id)
        assertEquals(r, s.quickplay.revision.value)
        assertEquals(full.rows, view.pins(0, cap).rows)
        s.quickplay.unpin(held.kind, held.id)
        r = s.until(s.quickplay.revision) { it > r }
        s.quickplay.pin(Kind.Song, extra)
        s.until(s.quickplay.revision) { it > r }
        assertTrue(view.pinned(Kind.Song, extra))
        assertEquals(cap, view.pins(0, 0).total)
    }

    @Test
    fun aBusyReadChangesNothingAndTheNextAskAnswers() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val album = view.page(Listing.Albums, 2, 1).rows.single().id
        val text = "index busy"
        val reads = listOf<suspend () -> Any?>({ view.page(Listing.Songs, 0, 5) }, { view.indexOf(Listing.Albums, Kind.Album, album) })
        for (read in reads) {
            val want = read()
            s.busy?.let { busy ->
                busy(text)
                outcome(s, { it is Busy && it.text == text }) { read() }
            }
            assertEquals(want, read())
        }
    }

    @Test
    fun aCardNotPresentRefusesPlaysAndEditsWithNoCard() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val (a, b) = songs(s, 2)
        val album = Listing.AlbumTracks(view.page(Listing.Albums, 0, 1).rows.single().id)
        val lists = view.allRows(Listing.Playlists)
        val list = lists.first { it.writable }
        val listing = Listing.PlaylistTracks(list.id)
        val tracks = view.allRows(listing).map { it.id }
        val favorites = view.allRows(Listing.Favorites).map { it.id }
        val queued = queue(s)
        val pins = view.pins(0, 100).rows
        val held = pins.first()
        val free = songs(s, 20).first { id -> pins.none { it.kind == Kind.Song && it.id == id } }
        val g = view.generation
        val q = s.player.state.value.queueRevision
        val lr = view.page(listing, 0, 1).revision
        val fr = view.page(Listing.Favorites, 0, 1).revision
        val guarded = listOf<suspend () -> Unit>(
            { s.player.play(listOf(a, b), 0, 0) },
            { s.player.play(album, g, 0, 0) },
            { s.player.enqueue(listOf(a)) },
            { s.player.enqueue(album, g) },
            { s.player.playNext(listOf(a)) },
            { s.player.playNext(album, g) },
            { s.player.playAt(0, q) },
            { s.playlists.create(TrackSet.Ids(listOf(a))) },
            { s.playlists.add(list.id, TrackSet.Ids(listOf(a))) },
            { s.playlists.remove(list.id, 0, lr) },
            { s.playlists.move(list.id, 0, 1, lr) },
            { s.playlists.delete(list.id) },
            { s.favorites.toggle(a) },
            { s.favorites.add(TrackSet.Ids(listOf(b))) },
            { s.favorites.remove(0, fr) },
            { s.favorites.move(0, 1, fr) },
            { s.quickplay.pin(Kind.Song, free) },
            { s.quickplay.unpin(held.kind, held.id) },
        )
        s.card?.let { card ->
            for (c in listOf(Card.Out, Card.Error, Card.Held)) {
                card(c)
                s.until(s.library.state) { it.card == c }
                for (call in guarded) outcome(s, { it is NoCard }) { call() }
            }
            card(Card.Present)
        }
        s.until(s.library.state) { it.card == Card.Present }
        assertEquals(queued, queue(s))
        assertEquals(lists, view.allRows(Listing.Playlists))
        assertEquals(tracks, view.allRows(listing).map { it.id })
        assertEquals(favorites, view.allRows(Listing.Favorites).map { it.id })
        assertEquals(pins, view.pins(0, 100).rows)
        val r = s.playlists.revision.value
        s.playlists.add(list.id, TrackSet.Ids(listOf(a)))
        s.until(s.playlists.revision) { it > r }
        assertEquals(tracks + a, view.allRows(listing).map { it.id })
    }

    @Test
    fun aRescanOfALibraryWithTracksIsNoFirstIndex() = runTest {
        val s = open(Edge.Main)
        val states = CopyOnWriteArrayList<LibraryState>()
        val scans = CopyOnWriteArrayList<ScanProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.library.state.toList(states) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.library.scan.toList(scans) }
        s.change()
        s.library.rescan()
        s.until(s.library.scan) { !it.running && scans.any { p -> p.running } }
        assertTrue("$states", states.none { it.first })
        assertEquals(Card.Present, s.library.state.value.card)
    }

    @Test
    fun theFirstIndexOfAnEmptyLibraryIsMarked() = runTest {
        val s = open(Edge.Empty)
        val states = CopyOnWriteArrayList<LibraryState>()
        val scans = CopyOnWriteArrayList<ScanProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.library.state.toList(states) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.library.scan.toList(scans) }
        assertFalse(s.library.state.value.first)
        s.library.rescan()
        s.until(s.library.scan) { !it.running && scans.any { p -> p.running } }
        s.until(s.library.state) { !it.first }
        assertEquals("$states", s.caps.firstIndex, states.any { it.first })
    }

    @Test
    fun indexOfAnswersNullOnlyForAnAbsentRow() = runTest {
        val s = open(Edge.Main)
        val view = s.library.view()
        val last = view.page(Listing.Songs, view.page(Listing.Songs, 0, 1).total - 1, 1).rows.single()
        val ids = checkNotNull(view.trackIds(last.id))
        val album = checkNotNull(ids.album)
        val artist = ids.artists.first()
        val albumAt = view.indexOf(Listing.Albums, Kind.Album, album)
        val artistAt = view.indexOf(Listing.Artists, Kind.Artist, artist)
        val albums = view.allRows(Listing.Albums)
        assertEquals(albums.indexOfFirst { it.id == album }, checkNotNull(albumAt))
        assertEquals(view.allRows(Listing.Artists).indexOfFirst { it.id == artist }, checkNotNull(artistAt))
        if (s.library.caps.indexOf(Listing.Songs)) {
            val song = view.page(Listing.AlbumTracks(albums.last().id), 0, 1).rows.single().id
            val songAt = view.indexOf(Listing.Songs, Kind.Song, song)
            assertEquals(view.allRows(Listing.Songs).indexOfFirst { it.id == song }, checkNotNull(songAt))
        }
        assertNull(view.indexOf(Listing.Albums, Kind.Album, -1))
        assertNull(view.indexOf(Listing.Albums, Kind.Song, album))
        assertNull(view.indexOf(Listing.Artists, Kind.Artist, -1))
    }

    private companion object {
        const val HUGE = 50_000
        const val SIDE = 4096
        const val SMALL = 4
    }
}
