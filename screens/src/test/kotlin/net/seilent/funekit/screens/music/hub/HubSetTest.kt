package net.seilent.funekit.screens.music.hub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.FAVS_CELL
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_COUNT
import net.seilent.funekit.screens.music.hub.HUB_FOLDERS
import net.seilent.funekit.screens.music.hub.HUB_PLAYLISTS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubAct
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPick
import net.seilent.funekit.screens.music.hub.HubSet
import net.seilent.funekit.screens.music.hub.HubState
import net.seilent.funekit.screens.music.hub.LABEL_CELL
import net.seilent.funekit.screens.music.hub.NOW_CELL
import net.seilent.funekit.screens.music.hub.NO_CELL
import net.seilent.funekit.screens.music.hub.QUERY_CELL
import net.seilent.funekit.screens.music.hub.SEARCH_ALBUMS
import net.seilent.funekit.screens.music.hub.SEARCH_ARTISTS
import net.seilent.funekit.screens.music.hub.SEARCH_SONGS
import net.seilent.funekit.screens.music.hub.hubLine
import net.seilent.funekit.screens.music.hub.hubListing
import net.seilent.funekit.screens.music.hub.hubMap
import net.seilent.funekit.screens.music.hub.hubPick
import net.seilent.funekit.screens.music.hub.letterCell
import net.seilent.funekit.screens.music.hub.letterOf
import net.seilent.funekit.screens.music.hub.rowModel
import net.seilent.funekit.screens.music.overlay.SheetFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class HubSetTest {
    private val search = HubSet.Search(QUERY)

    private fun searchHub(): HubModel = HubModel(0L, MusicPage.Search(QUERY), HubState(search))

    @Test
    fun theSearchSetHasItsPivotsAndASearchListingPerPivot() {
        assertEquals(listOf("artists", "albums", "songs"), search.pivots)
        assertEquals(Listing.Search(QUERY, Kind.Artist), search.listing(SEARCH_ARTISTS, 0L))
        assertEquals(Listing.Search(QUERY, Kind.Album), search.listing(SEARCH_ALBUMS, 0L))
        assertEquals(Listing.Search(QUERY, Kind.Song), search.listing(SEARCH_SONGS, 0L))
        assertEquals(listOf(HUB_ARTISTS, HUB_ALBUMS, HUB_SONGS), search.pivots.indices.map(search::role))
        for (p in 0 until HUB_COUNT) assertEquals(hubListing(p, FOLDER), HubSet.Music.listing(p, FOLDER))
        val m = searchHub()
        assertEquals(search.pivots.size, m.panes.size)
        assertEquals(Listing.Search(QUERY, Kind.Album), m.panes[SEARCH_ALBUMS].listing)
        assertEquals(SheetFrom.Search, search.from)
        assertEquals(SheetFrom.Hub, HubSet.Music.from)
    }

    @Test
    fun theGridPivotIsAlbumsInBothSets() {
        assertEquals(1, search.grid)
        assertEquals(HUB_ALBUMS, HubSet.Music.grid)
    }

    @Test
    fun theSearchLeadIsTheQueryRowOnEveryPivotAndTheMusicLeadsAreToday() {
        for (p in search.pivots.indices) assertEquals(listOf(QUERY_CELL), search.lead(p, true))
        assertEquals(emptyList<Int>(), HubSet.Music.lead(HUB_FOLDERS, true))
        assertEquals(listOf(LABEL_CELL), HubSet.Music.lead(HUB_FOLDERS, false))
        assertEquals(listOf(NOW_CELL, FAVS_CELL), HubSet.Music.lead(HUB_PLAYLISTS, true))
        for (p in listOf(HUB_ALBUMS, HUB_ARTISTS, HUB_SONGS)) assertEquals(emptyList<Int>(), HubSet.Music.lead(p, true))
        assertTrue(letterOf(QUERY_CELL) < 0)
    }

    @Test
    fun theScanLineShowsOnSearchAndHidesEveryRow() {
        assertEquals("scanning 3 songs", hubLine(search, SEARCH_ARTISTS, true, 3, 0, 0, 1L))
        assertEquals("", hubLine(search, SEARCH_ARTISTS, false, 3, 0, 0, 1L))
        assertEquals("no <pivot> is the music hub's alone", "", hubLine(search, SEARCH_ALBUMS, false, 0, 0, 0, 1L))
        assertEquals("no albums", hubLine(HubSet.Music, HUB_ALBUMS, false, 0, 0, 0, 1L))
        val m = loaded(SEARCH_ARTISTS)
        m.generation = 1L
        m.songs = 0
        m.scanning = true
        m.found = 3
        assertEquals("scanning 3 songs", m.line(SEARCH_ARTISTS))
        assertNull("no row takes a hold while the scan line shows", m.target(SEARCH_ARTISTS, 1, 0))
        assertTrue("the page waits for no row", m.ready)
    }

    @Test
    fun aSearchGridPickScrollsAndAMusicGridPickDefers() {
        assertEquals(HubPick.Scroll, hubPick(search, SEARCH_ALBUMS))
        assertEquals(HubPick.Scroll, hubPick(search, SEARCH_ARTISTS))
        assertEquals(HubPick.Defer, hubPick(HubSet.Music, HUB_ALBUMS))
        assertEquals(HubPick.Scroll, hubPick(HubSet.Music, HUB_SONGS))
    }

    @Test
    fun aSearchPageOnAlbumsIsAGridPage() {
        val m = searchHub()
        m.select(SEARCH_ALBUMS)
        assertEquals(PageKind.Grid, m.kind)
        m.select(SEARCH_ARTISTS)
        assertEquals(PageKind.Hub, m.kind)
        m.select(SEARCH_SONGS)
        assertEquals(PageKind.Hub, m.kind)
        val h = HubModel(0L)
        h.select(HUB_ALBUMS)
        assertEquals(PageKind.Grid, h.kind)
    }

    @Test
    fun theSearchAlbumsMapGivesTheQueryARowOfItsOwn() {
        val map = hubMap(byteArrayOf(0, 0, 0, 0, 1), GRID, listOf(QUERY_CELL))
        assertEquals(1, map.cells(0))
        assertEquals(QUERY_CELL, map.at(0))
        assertEquals(NO_CELL, map.at(0, 1))
        assertEquals(listOf(letterCell(0), 0, 1), (0 until GRID).map { map.at(1, it) })
        assertEquals(listOf(2, 3, letterCell(1)), (0 until GRID).map { map.at(2, it) })
        assertEquals(listOf(4), (0 until map.cells(3)).map { map.at(3, it) })
        assertEquals(4, map.count)
        assertEquals(1, map.rowOf(0))
        assertEquals(0, map.colOf(0))
        assertEquals(2, map.rowOf(1))
        assertEquals(2, map.colOf(1))
        assertEquals(2, map.rowOfIndex(2))
        assertEquals(0, map.colOfIndex(2))
        assertEquals(3, map.rowOfIndex(4))
        assertEquals(0..1, map.indices(0, 1))
    }

    @Test
    fun theMusicMapsAreTheirCellStreams() {
        val sections = byteArrayOf(0, 0, 1, 2, 2, 2, 5)
        val stream = ArrayList<Int>()
        var prev = -1
        for (i in sections.indices) {
            if (sections[i].toInt() != prev) {
                stream += letterCell(sections[i].toInt())
                prev = sections[i].toInt()
            }
            stream += i
        }
        val grid = hubMap(sections, GRID)
        assertEquals((stream.size + GRID - 1) / GRID, grid.count)
        for (r in 0 until grid.count) for (c in 0 until GRID) assertEquals(stream.getOrElse(r * GRID + c) { NO_CELL }, grid.at(r, c))
        for (lead in listOf(listOf(LABEL_CELL), listOf(NOW_CELL, FAVS_CELL))) {
            val list = hubMap(sections, 1, lead)
            val cells = lead + stream
            assertEquals(cells.size, list.count)
            for (r in cells.indices) assertEquals(cells[r], list.at(r))
            for (i in sections.indices) assertEquals(cells.indexOf(i), list.rowOfIndex(i))
        }
    }

    @Test
    fun aLoadedSearchPaneOpensOnTheQueryRow() {
        for (p in search.pivots.indices) {
            val m = loaded(p)
            val pn = m.panes[p]
            assertEquals(QUERY_CELL, pn.cell(0, 0))
            assertEquals(QUERY, m.rowModel(p, 0)?.primary)
            assertNull(m.target(p, 0, 0))
            assertEquals(HubAct.Query, m.act(p, 0, 0, false))
            assertTrue("row 1 is a letter", letterOf(pn.cell(1, 0)) >= 0)
        }
    }

    private fun loaded(p: Int): HubModel = runBlocking {
        val f = Fake(CoroutineScope(EmptyCoroutineContext), { 0L }, FakeSet.Demo, 0L, LEASE_MS)
        val m = searchHub()
        m.select(p)
        m.cols = GRID
        val v = f.library.view()
        m.load(v, p)
        m.show(v, p, 0, ROWS)
        m
    }

    private companion object {
        const val QUERY = "name"
        const val FOLDER = 7L
        const val GRID = 3
        const val ROWS = 20
        const val LEASE_MS = 60_000L
    }
}
