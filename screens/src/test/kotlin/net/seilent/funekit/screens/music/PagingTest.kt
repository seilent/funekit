package net.seilent.funekit.screens.music

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryCaps
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.Paging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class PagingTest {
    private val fake = Fake(CoroutineScope(EmptyCoroutineContext), { 0L }, FakeSet.Demo, 0L, LEASE_MS)

    @Test
    fun aWindowGrowsByTheMissingRowsOfOneRevision() = runBlocking {
        val v = fake.library.view()
        val p = Paging(Listing.Songs, AHEAD)
        p.show(v, 0, 9)
        val w = requireNotNull(p.window)
        assertEquals(0, w.offset)
        assertEquals(10 + AHEAD, w.rows.size)
        p.show(v, 3, 9)
        assertEquals(w, p.window)
        p.show(v, 12, 15)
        val g = requireNotNull(p.window)
        assertEquals(0, g.offset)
        assertEquals(v.page(Listing.Songs, 0, 16 + AHEAD).rows, g.rows)
    }

    @Test
    fun aWindowWhosePagesCarryTwoRevisionsReloadsWhole() = runBlocking {
        val v = fake.library.view()
        val p = Paging(Listing.Favorites, 1)
        p.show(v, 0, 0)
        val w = requireNotNull(p.window)
        val song = v.page(Listing.Songs, 0, 1).rows.first().id
        fake.favorites.toggle(song)
        p.show(v, 2, 2)
        val g = requireNotNull(p.window)
        assertNotEquals(w.revision, g.revision)
        val fresh = v.page(Listing.Favorites, 1, 3)
        assertEquals(fresh.revision, g.revision)
        assertEquals(1, g.offset)
        assertEquals(fresh.rows, g.rows)
        assertEquals(fresh.total, g.total)
    }

    @Test
    fun aMigrationTakesTheAnchorRowAndKeepsAGoneAnchorsIndexClamped() = runBlocking {
        val v = fake.library.view()
        val p = Paging(Listing.Songs, AHEAD)
        val id = v.page(Listing.Songs, ROW, 1).rows.first().id
        assertEquals(ROW, p.migrate(v, Kind.Song, id, 0).at)
        assertEquals(id, requireNotNull(p.window).row(ROW)?.id)
        val total = v.page(Listing.Songs, 0, 1).total
        assertEquals(total - 1, p.migrate(v, Kind.Song, -1, total + ROW).at)
        val w = requireNotNull(p.window)
        assertEquals(total, w.end)
        assertEquals(v.page(Listing.Songs, total - 1, 1).rows.first().id, w.row(total - 1)?.id)
    }

    @Test
    fun aMigrationOnAnUnindexedListingKeepsTheOldIndexClampedAndNeverAsksIndexOf() = runBlocking {
        val v = fake.library.view()
        val blind = object : LibraryView by v {
            override suspend fun indexOf(listing: Listing, kind: Kind, id: Long): Int? = throw UnsupportedOperationException()
        }
        val caps = LibraryCaps(unindexed = setOf(Listing.Songs::class))
        val p = Paging(Listing.Songs, AHEAD)
        val id = v.page(Listing.Songs, ROW, 1).rows.first().id
        assertEquals(ROW + 1, p.migrate(blind, Kind.Song, id, ROW + 1, caps).at)
        assertEquals(v.page(Listing.Songs, ROW + 1, 1).rows.first().id, requireNotNull(p.window).row(ROW + 1)?.id)
        val total = v.page(Listing.Songs, 0, 1).total
        assertEquals(total - 1, p.migrate(blind, Kind.Song, id, total + ROW, caps).at)
    }

    @Test
    fun aNewerShowWinsOverAnOlderLoadStillInFlight() = runBlocking {
        val v = fake.library.view()
        val gate = CompletableDeferred<Unit>()
        val slow = object : LibraryView by v {
            override suspend fun page(listing: Listing, offset: Int, count: Int): Page {
                gate.await()
                return v.page(listing, offset, count)
            }
        }
        val p = Paging(Listing.Songs, AHEAD)
        val old = launch { p.show(slow, 0, 0) }
        yield()
        assertNull(p.window)
        p.show(v, ROW, ROW)
        val shown = requireNotNull(p.window)
        gate.complete(Unit)
        old.join()
        assertEquals(shown, p.window)
        assertEquals(ROW - AHEAD, shown.offset)
    }

    @Test
    fun aLiveListingWholeInItsWindowReloadsOnARevisionChange() = runBlocking {
        val src = MusicSource(fake.library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)
        val p = Paging(Listing.Favorites)
        val follow = launch { p.follow(requireNotNull(src.revisions(Listing.Favorites))) { src.library.view() } }
        yield()
        val v = fake.library.view()
        p.show(v, 0, 0)
        val w = requireNotNull(p.window)
        assertEquals(w.total, w.end)
        val song = v.page(Listing.Songs, 0, 1).rows.first().id
        fake.favorites.toggle(song)
        p.show(v, 0, 0)
        repeat(SPINS) { if (p.window?.revision == w.revision) yield() }
        follow.cancel()
        val g = requireNotNull(p.window)
        val fresh = v.page(Listing.Favorites, 0, w.total + 1)
        assertNotEquals(w.revision, g.revision)
        assertEquals(fresh.revision, g.revision)
        assertEquals(fresh.total, g.total)
        assertEquals(fresh.rows, g.rows)
    }

    private class Gated(private val v: LibraryView) : LibraryView by v {
        val read = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun page(listing: Listing, offset: Int, count: Int): Page {
            val p = v.page(listing, offset, count)
            read.complete(Unit)
            gate.await()
            return p
        }
    }

    @Test
    fun aReloadInFlightLandsOverANoOpShow() = runBlocking {
        val v = fake.library.view()
        val p = Paging(Listing.Favorites)
        p.show(v, 0, 0)
        val w = requireNotNull(p.window)
        assertEquals(w.total, w.end)
        fake.favorites.toggle(v.page(Listing.Songs, 0, 1).rows.first().id)
        val slow = Gated(v)
        val reload = launch { p.reload(slow) }
        slow.read.await()
        p.show(v, 0, 0)
        slow.gate.complete(Unit)
        reload.join()
        val g = requireNotNull(p.window)
        val fresh = v.page(Listing.Favorites, 0, w.total + 1)
        assertNotEquals(w.revision, g.revision)
        assertEquals(fresh.revision, g.revision)
        assertEquals(fresh.rows, g.rows)
    }

    @Test
    fun aReloadOverAnExtendingShowInFlightKeepsTheAskedRows() = runBlocking {
        val v = fake.library.view()
        val p = Paging(Listing.Songs, AHEAD)
        p.show(v, 0, 9)
        assertEquals(10 + AHEAD, requireNotNull(p.window).end)
        val slow = Gated(v)
        val extend = launch { p.show(slow, 12, 15) }
        slow.read.await()
        assertEquals(10 + AHEAD, requireNotNull(p.window).end)
        p.reload(v)
        slow.gate.complete(Unit)
        extend.join()
        val g = requireNotNull(p.window)
        assertTrue(g.has(12, 15))
        assertEquals(0, g.offset)
        assertEquals(v.page(Listing.Songs, 0, 16 + AHEAD).rows, g.rows)
    }

    @Test
    fun aRevisionSeenBeforeTheFirstWindowLandsReloadsIt() = runBlocking {
        val src = MusicSource(fake.library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)
        val p = Paging(Listing.Favorites)
        val follow = launch { p.follow(requireNotNull(src.revisions(Listing.Favorites))) { src.library.view() } }
        yield()
        val v = fake.library.view()
        val before = v.page(Listing.Favorites, 0, 1)
        val slow = Gated(v)
        val first = launch { p.show(slow, 0, 0) }
        slow.read.await()
        assertNull(p.window)
        fake.favorites.toggle(v.page(Listing.Songs, 0, 1).rows.first().id)
        repeat(SPINS) { if (p.window == null) yield() }
        slow.gate.complete(Unit)
        first.join()
        follow.cancel()
        val g = requireNotNull(p.window)
        val fresh = v.page(Listing.Favorites, 0, g.total)
        assertNotEquals(before.revision, g.revision)
        assertEquals(fresh.revision, g.revision)
        assertEquals(fresh.total, g.total)
        assertEquals(fresh.rows, g.rows)
    }

    companion object {
        private const val AHEAD = 4
        private const val ROW = 40
        private const val LEASE_MS = 60_000L
        private const val SPINS = 100
    }
}
