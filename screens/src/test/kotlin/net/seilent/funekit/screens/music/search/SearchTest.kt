package net.seilent.funekit.screens.music.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.SEARCH_ALBUMS
import net.seilent.funekit.screens.music.hub.SEARCH_ARTISTS
import net.seilent.funekit.screens.music.hub.musicModel
import net.seilent.funekit.screens.music.search.NO_RESULTS
import net.seilent.funekit.screens.music.search.searchWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class SearchTest {
    private val fake = Fake(CoroutineScope(EmptyCoroutineContext), { 0L }, FakeSet.Demo, 0L, LEASE_MS)

    private fun search(query: String): HubModel = musicModel(0L, MusicPage.Search(query)) as HubModel

    private fun hits(v: LibraryView, q: String, k: Kind): Int = runBlocking { v.page(Listing.Search(q, k), 0, 0).total }

    private fun albumsOnly(): String = runBlocking {
        val v = fake.library.view()
        val n = v.page(Listing.Albums, 0, 0).total
        val words = v.page(Listing.Albums, 0, n).rows.flatMap { it.title.lowercase().split(' ') }.filter { it.length >= MIN_WORD }
        words.first { w -> hits(v, w, Kind.Artist) == 0 && hits(v, w, Kind.Album) > 0 }
    }

    private fun HubModel.loadAll() = runBlocking { landAll(fake.library.view(), ROWS) }

    @Test
    fun anOpenRunsTheQueryAndNeverLands() {
        val q = albumsOnly()
        val m = search(q)
        m.loadAll()
        assertTrue("premise: the albums pane has hits", m.hits(SEARCH_ALBUMS) > 0)
        assertFalse(m.land(T))
        assertEquals(SEARCH_ARTISTS, m.pivot)
        assertEquals("no artists found", m.found(SEARCH_ARTISTS))
        assertEquals(0, m.pane.focus.row)
    }

    @Test
    fun thePanesSwapOnlyAtDone() {
        val m = search(QUERY)
        m.openField()
        m.typed(EMPTY_HITS)
        assertEquals(QUERY, m.committed())
        assertEquals(Listing.Search(QUERY, Kind.Artist), m.panes[SEARCH_ARTISTS].listing)
        assertTrue(m.done())
        assertEquals(EMPTY_HITS, m.committed())
        assertEquals(Listing.Search(EMPTY_HITS, Kind.Album), m.panes[SEARCH_ALBUMS].listing)
    }

    @Test
    fun doneLandsOnTheFirstPivotWithHitsAndOnArtistsWithNone() {
        val m = search(QUERY)
        m.loadAll()
        m.openField()
        m.typed(albumsOnly())
        assertTrue(m.done())
        assertFalse("no landing before the panes have their windows", m.land(T))
        m.loadAll()
        assertTrue(m.land(T))
        assertEquals(SEARCH_ALBUMS, m.pivot)
        m.openField()
        m.typed(EMPTY_HITS)
        assertTrue(m.done())
        m.loadAll()
        assertTrue(m.land(T))
        assertEquals(SEARCH_ARTISTS, m.pivot)
    }

    @Test
    fun aNewQueryFocusesTheQueryRowAtTheTop() {
        val m = search(QUERY)
        m.loadAll()
        m.pane.setFocus(2, 0, T, false)
        m.openField()
        m.typed(QUERY_B)
        assertTrue(m.done())
        m.loadAll()
        assertTrue(m.land(T))
        assertEquals(0, m.pane.focus.row)
        assertEquals(0, m.pane.col)
    }

    @Test
    fun theSameQueryAgainLandsOnItsLoadedHitsWithNoReload() {
        val m = search(QUERY)
        m.loadAll()
        val map = m.pane.map
        m.pane.setFocus(2, 0, T, false)
        m.openField()
        assertTrue(m.done())
        assertSame("no reload", map, m.pane.map)
        assertTrue("lands at once on the loaded hits", m.land(T))
        assertEquals(SEARCH_ARTISTS, m.pivot)
        assertEquals(0, m.pane.focus.row)
    }

    @Test
    fun theEmptyLineShowsOnlyOnceTheHitsArrived() {
        val m = search(EMPTY_HITS)
        assertEquals("", m.found(SEARCH_ARTISTS))
        m.loadAll()
        assertEquals("no artists found", m.found(SEARCH_ARTISTS))
        assertEquals("no albums found", m.found(SEARCH_ALBUMS))
        val n = search(QUERY)
        n.loadAll()
        assertEquals("", n.found(SEARCH_ARTISTS))
    }

    @Test
    fun aFailedCountReadKeepsTheWord() = runTest {
        val m = search(QUERY)
        val q = m.search!!
        m.openField()
        launch { q.settle(QUERY, q.fresh) { 2 } }
        runCurrent()
        assertEquals("premise: the open word", searchWord(2), q.word)
        m.typed(QUERY_B)
        launch { q.settle(QUERY_B, q.fresh) { null } }
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        assertEquals("a failed read shows no count word of its own", searchWord(2), q.word)
    }

    @Test
    fun theCountWordWaitsForTheDebounceAndTheOpenWordDoesNot() = runTest {
        val m = search(QUERY)
        val q = m.search!!
        m.openField()
        launch { q.settle(QUERY, q.fresh) { 2 } }
        runCurrent()
        assertEquals("the open word is the committed query's at once", searchWord(2), q.word)
        m.typed(QUERY_B)
        launch { q.settle(QUERY_B, q.fresh) { 1 } }
        advanceTimeBy(DEBOUNCE - 1)
        runCurrent()
        assertEquals(searchWord(2), q.word)
        advanceTimeBy(2)
        runCurrent()
        assertEquals("1 song", q.word)
        m.typed("")
        launch { q.settle("", q.fresh) { 9 } }
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        assertEquals("", q.word)
        assertEquals(NO_RESULTS, searchWord(0))
        assertEquals("3 songs", searchWord(3))
    }

    @Test
    fun theContentWaitsForTheFirstWindowOfANonEmptyQuery() {
        val m = search(QUERY)
        assertFalse(m.ready)
        m.loadAll()
        assertTrue(m.ready)
        assertTrue("an empty query draws its row at once", search("").ready)
    }

    @Test
    fun theStateSlotKeepsTheDoneQuery() {
        val m = search(QUERY)
        m.openField()
        m.typed(QUERY_B)
        assertTrue(m.done())
        val state = requireNotNull(m.state())
        assertEquals(listOf(QUERY_B), state)
        val n = search(QUERY)
        n.restoreState(state)
        assertEquals(QUERY_B, n.committed())
        assertEquals(Listing.Search(QUERY_B, Kind.Artist), n.panes[SEARCH_ARTISTS].listing)
        assertFalse(n.search!!.open)
    }

    private companion object {
        const val QUERY = "name"
        const val QUERY_B = "aster"
        const val EMPTY_HITS = "zzqx"
        const val ROWS = 20
        const val T = 1_000L
        const val MIN_WORD = 4
        const val LEASE_MS = 60_000L
        val DEBOUNCE = Tok.input_searchDebounceMs.toLong()
    }
}
