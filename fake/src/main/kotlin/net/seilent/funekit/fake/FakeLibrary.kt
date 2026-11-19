package net.seilent.funekit.fake

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.seilent.funekit.core.Section
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.EngineEvent
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.Root
import net.seilent.funekit.api.ScanProgress
import net.seilent.funekit.api.TrackIds
import net.seilent.funekit.api.TrackInfo
import net.seilent.funekit.api.TrackSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class FakeLibrary internal constructor(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val set: FakeSet,
    private val latencyMs: Long,
    leaseMs: Long,
    private val store: Store,
    private val bus: MutableSharedFlow<EngineEvent>,
) : Library {
    private class Jumps {
        val seq = AtomicLong()
        val lock = Mutex()
    }

    private inner class Snap(val generation: Long, val catalog: Catalog) {
        @Volatile
        var supersededAt: Long? = null

        val view: LibraryView = View(this)
    }

    private val lock = Mutex()
    private val leaseNanos = leaseMs * MILLI
    private var nextRoot = 1L
    private val known = Fixture.shelf(set).roots
    private var rootList: List<FakeRoot> = if (set == FakeSet.Empty) emptyList() else known.mapIndexed { i, uri -> FakeRoot(nextRoot++, uri, i) }
    private val unavailable = if (set == FakeSet.Unavailable) setOf(Fixture.UNAVAILABLE_ROOT) else emptySet()
    private val snaps = ConcurrentHashMap<Long, Snap>()

    @Volatile
    private var current = Snap(1, Catalog(set, rootList)).also { snaps[it.generation] = it }
    private val _generation = MutableStateFlow(current.generation)
    private val _roots = MutableStateFlow(rootsOf(rootList))
    private val _scan = MutableStateFlow(ScanProgress(false, current.catalog.trackCount, false))
    private var scanJob: Job? = null

    @Volatile
    private var failNext = false
    private val built = AtomicLong()
    private val loads = AtomicLong()

    internal var dropped: suspend (Set<Long>) -> Unit = {}

    override val generation: StateFlow<Long> = _generation.asStateFlow()
    override val roots: StateFlow<List<Root>> = _roots.asStateFlow()
    override val scan: StateFlow<ScanProgress> = _scan.asStateFlow()

    val rowsBuilt: Long get() = built.get()
    val pageLoads: Long get() = loads.get()

    override fun view(): LibraryView = current.view

    override fun view(generation: Long): LibraryView =
        snaps[generation]?.view ?: Snap(generation, current.catalog).also { it.supersededAt = clock() - leaseNanos }.view

    override suspend fun addRoot(treeUri: String) {
        lock.withLock {
            if (rootList.none { it.uri == treeUri }) {
                rootList = rootList + FakeRoot(nextRoot++, treeUri, known.indexOf(treeUri).takeIf { it >= 0 })
                _roots.value = rootsOf(rootList)
            }
        }
        rescan()
    }

    override suspend fun removeRoot(treeUri: String) {
        val gone = lock.withLock {
            val root = rootList.firstOrNull { it.uri == treeUri } ?: return
            val before = current.catalog.trackIds
            rootList = rootList - root
            _roots.value = rootsOf(rootList)
            publish(Catalog(set, rootList))
            before - current.catalog.trackIds
        }
        dropped(gone)
    }

    override suspend fun rescan() {
        lock.withLock {
            if (scanJob?.isActive == true) return
            scanJob = scope.launch { scan() }
        }
    }

    private suspend fun scan() {
        val total = lock.withLock { Catalog(set, rootList).trackCount }
        progress(ScanProgress(true, 0, false))
        for (step in 1..SCAN_STEPS) {
            delay(latencyMs)
            progress(ScanProgress(true, total * step / SCAN_STEPS, false))
        }
        val found = lock.withLock {
            if (failNext) {
                failNext = false
                null
            } else {
                publish(Catalog(set, rootList))
                current.catalog.trackCount
            }
        }
        progress(ScanProgress(false, found ?: total, found == null))
    }

    internal fun failNextScan() {
        failNext = true
    }

    internal fun track(id: Long): Track? = current.catalog.track(id)

    internal fun resolves(pin: Pin): Boolean = current.catalog.resolve(pin) != null

    internal fun ids(listing: Listing, generation: Long): List<Long> {
        val snap = if (live(listing)) current else snaps[generation]?.takeUnless { expired(it) } ?: throw Expired(generation)
        val rows = snap.catalog.rows(listing, store)
        return List(rows.size) { rows.id(it) }
    }

    internal fun ids(tracks: TrackSet): List<Long> = when (tracks) {
        is TrackSet.Ids -> tracks.ids.filter { track(it) != null }
        is TrackSet.Listed -> ids(tracks.listing, tracks.generation)
    }

    private fun live(listing: Listing): Boolean = when (listing) {
        Listing.Queue, Listing.Pins, Listing.History, Listing.Favorites, Listing.Playlists, is Listing.PlaylistTracks -> true
        else -> false
    }

    private fun revision(listing: Listing): Long = when (listing) {
        Listing.Queue -> store.queue.revision
        Listing.Pins, Listing.History -> store.revision.value
        Listing.Favorites -> store.favoritesRevision.value
        Listing.Playlists -> store.playlistsRevision.value
        is Listing.PlaylistTracks -> listRevision(store.playlists.firstOrNull { it.id == listing.playlist }?.revision ?: store.playlistsRevision.value)
        else -> 0
    }

    internal fun listRevision(docRevision: Long): Long = current.generation shl GENERATION_SHIFT or docRevision

    private fun rootsOf(list: List<FakeRoot>) = list.map { Root(it.id, it.uri, it.fixture == null || it.fixture !in unavailable) }

    private fun progress(p: ScanProgress) {
        _scan.value = p
        bus.tryEmit(EngineEvent.Scan(p))
    }

    private fun publish(catalog: Catalog) {
        val old = current
        val snap = Snap(old.generation + 1, catalog)
        val now = clock()
        old.supersededAt = now
        snaps[snap.generation] = snap
        current = snap
        snaps.values.removeIf { expired(it) }
        store.updatePins { pins -> pins.filter { catalog.resolve(it) != null } }
        _generation.value = snap.generation
    }

    private fun expired(snap: Snap): Boolean = snap.supersededAt?.let { clock() - it >= leaseNanos } ?: false

    private inner class View(private val snap: Snap) : LibraryView {
        private val bytes = ConcurrentHashMap<Listing, ByteArray>()
        private val jumps = ConcurrentHashMap<Listing, Jumps>()

        override val generation: Long get() = snap.generation

        private suspend fun answer() {
            delay(latencyMs)
            if (expired(snap)) throw Expired(snap.generation)
        }

        private suspend fun answer(listing: Listing): Snap {
            if (!live(listing)) {
                answer()
                return snap
            }
            delay(latencyMs)
            return current
        }

        private fun load(at: Snap, listing: Listing, offset: Int, count: Int): Page {
            val revision = revision(listing)
            val rows = at.catalog.rows(listing, store)
            val from = offset.coerceIn(0, rows.size)
            val to = (from.toLong() + count.coerceAtLeast(0)).coerceAtMost(rows.size.toLong()).toInt()
            val list = (from until to).map { rows.row(it) }
            loads.incrementAndGet()
            built.addAndGet(list.size.toLong())
            return Page(at.generation, from, rows.size, list, revision)
        }

        override suspend fun page(listing: Listing, offset: Int, count: Int): Page = load(answer(listing), listing, offset, count)

        override suspend fun sections(listing: Listing): ByteArray = answer(listing).catalog.rows(listing, store).sections()

        override suspend fun jump(listing: Listing, letter: Int, count: Int): Page? {
            val j = jumps.getOrPut(listing) { Jumps() }
            val mine = j.seq.incrementAndGet()
            return j.lock.withLock {
                if (mine != j.seq.get()) return@withLock null
                val at = answer(listing)
                val s = if (live(listing)) at.catalog.rows(listing, store).sections() else bytes.getOrPut(listing) { snap.catalog.rows(listing, store).sections() }
                val page = load(at, listing, Section.first(s, letter), count)
                if (mine != j.seq.get()) null else page
            }
        }

        override suspend fun indexOf(listing: Listing, kind: Kind, id: Long): Int? {
            val rows = answer(listing).catalog.rows(listing, store)
            return (0 until rows.size).firstOrNull { rows.id(it) == id && rows.kind(it) == kind }
        }

        override suspend fun trackInfo(track: Long): TrackInfo? {
            answer()
            return snap.catalog.info(track)
        }

        override suspend fun trackIds(track: Long): TrackIds? {
            answer()
            return snap.catalog.ids(track)
        }

        override suspend fun pinned(kind: Kind, id: Long): Boolean {
            delay(latencyMs)
            return Pin(kind, id) in store.pins
        }

        override suspend fun artRef(track: Long): ArtRef? {
            answer()
            return snap.catalog.track(track)?.art
        }

        override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? {
            answer()
            return snap.catalog.art(ref, maxSide)
        }
    }

    private companion object {
        const val MILLI = 1_000_000L
        const val SCAN_STEPS = 10
        const val GENERATION_SHIFT = 32
    }
}
