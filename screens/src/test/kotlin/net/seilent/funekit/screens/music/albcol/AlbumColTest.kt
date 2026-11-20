package net.seilent.funekit.screens.music.albcol

import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertNull
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.core.Section
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.albcol.AlbumColGeo
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.albcol.colScrubSection
import net.seilent.funekit.screens.music.hub.hubMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumColTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)

    private fun fake(set: FakeSet = FakeSet.Demo) = testFake(set)

    private fun model(f: Fake, letter: Int = 0): AlbumColModel = runBlocking {
        val m = AlbumColModel(0L, letter)
        m.geometry(AlbumColGeo(s))
        m.load(f.library.view())
        m.track.count(m.count, 0)
        m
    }

    @Test
    fun theRowsAreTheHubGridCellsReadRowByRowAndThePageIsPlain() = runBlocking {
        val f = fake()
        val m = model(f)
        val grid = hubMap(f.library.view().sections(Listing.Albums), GRID_COLS)
        val col = m.map!!
        val cells = (0 until grid.count).flatMap { r -> (0 until grid.cells(r)).map { grid.at(r, it) } }
        assertEquals(cells, (0 until col.count).map { col.at(it) })
        assertEquals(PageKind.Plain, m.kind)
        val v = f.library.view()
        m.show(v, 0, col.count - 1)
        val albums = v.page(Listing.Albums, 0, v.page(Listing.Albums, 0, 0).total).rows.map { it.id }
        val shown = (0 until col.count).mapNotNull { r -> col.indexAt(r).takeIf { it >= 0 }?.let { r to it } }
        assertTrue("premise: the page holds album rows", shown.isNotEmpty())
        for ((r, i) in shown) assertEquals("row $r", albums[i], m.row(r)?.id)
    }

    @Test
    fun theRowTopsAtTheTopFollowTheSixteenSixteenBoxTop() {
        val m = model(fake())
        val g = m.geo!!
        assertEquals(299, g.boxTop)
        assertEquals(listOf(299, 657, 1016, 1374), (0..3).map { g.rowTop(m.track, it) })
    }

    @Test
    fun anOpenOnTheLastLetterPlacesItsRowWithTheFocusAndIsReadyOnceThatWindowLands() = runBlocking {
        val f = fake()
        val probe = model(f)
        val last = (Section.count - 1 downTo 0).first { probe.map!!.has(it) }
        val m = model(f, last)
        assertTrue(m.open(0L))
        val row = m.map!!.rowOf(last)
        assertEquals(row, m.focus.row)
        assertEquals(minOf(m.track.topOf(row), m.track.max), m.track.q)
        assertFalse(m.ready)
        m.show(f.library.view(), m.track.drawFirst, m.track.rowAt(m.track.q + m.geo!!.view - 1))
        assertTrue(m.ready)
    }

    @Test
    fun anOpenOnTheFirstLetterRestsAtRowZeroWithTheFocusOnIt() {
        val f = fake()
        val first = (0 until Section.count).first { model(f).map!!.has(it) }
        val m = model(f, first)
        assertTrue(m.open(0L))
        assertEquals(0, m.map!!.rowOf(first))
        assertEquals(0, m.focus.row)
        assertEquals(0, m.track.q)
    }

    @Test
    fun anOpenOnALetterWithNoAlbumsRestsAtRowZero() {
        val f = fake()
        val probe = model(f)
        val gap = (1 until Section.count).firstOrNull { !probe.map!!.has(it) }
        assertNotNull("premise: a letter with no albums", gap)
        val m = model(f, gap!!)
        assertTrue(m.open(0L))
        assertEquals(0, m.focus.row)
        assertEquals(0, m.track.q)
    }

    @Test
    fun anEntryIsInViewOnlyWhileItsArtLiesBetweenTheTopAndTheContentBottom() = runBlocking {
        val f = fake()
        val m = model(f)
        val g = m.geo!!
        m.show(f.library.view(), 0, m.track.rowAt(g.view - 1))
        val inView = (0 until m.count).first { m.row(it) != null && g.rowTop(m.track, it) + g.side <= g.bottom }
        val past = (0 until m.count).first { m.row(it) != null && g.rowTop(m.track, it) < g.bottom && g.rowTop(m.track, it) + g.side > g.bottom }
        assertTrue(m.cell(m.row(inView)!!.id))
        assertFalse(m.cell(m.row(past)!!.id))
        assertEquals(1280, g.bottom)
    }

    @Test
    fun aScrubBucketLandsOnItsNearestLetterRowWithTheFocusOnIt() {
        val m = model(fake())
        for (b in 0 until Section.count) {
            val sec = colScrubSection(m, b) ?: continue
            assertTrue(m.jump(sec, 0L))
            val row = m.map!!.rowOf(sec)
            assertEquals(row, m.focus.row)
            assertEquals(minOf(m.track.topOf(row), m.track.max), m.track.q)
        }
    }

    @Test
    fun aNewGenerationKeepsTheTopAlbumOnTop() = runBlocking {
        val probe = fake(FakeSet.Main)
        val p0 = probe.library.view()
        val ids = p0.page(Listing.Albums, 0, p0.page(Listing.Albums, 0, 0).total).rows.map { it.id }
        val m0 = model(probe)
        val rows0 = ids.associateWith { m0.map!!.rowOfIndex(p0.indexOf(Listing.Albums, Kind.Album, it)!!) }.filterValues { m0.track.topOf(it) <= m0.track.max }
        probe.library.removeRoot(probe.library.roots.value.last().uri)
        val p1 = probe.library.view()
        val m1 = model(probe).map!!
        val target = rows0.keys.firstOrNull { id -> p1.indexOf(Listing.Albums, Kind.Album, id)?.let { m1.rowOfIndex(it) != rows0[id] } == true }
        assertNotNull("premise: an album whose row moves with the rescan", target)
        val f = fake(FakeSet.Main)
        val m = model(f)
        val v0 = f.library.view()
        m.show(v0, 0, m.count - 1)
        val before = rows0.getValue(target!!)
        m.track.toRow(before)
        assertEquals("premise: the album sits on top", target, m.row(m.track.first)?.id)
        f.library.removeRoot(f.library.roots.value.last().uri)
        val v = f.library.view()
        m.migrate(v, 0L)
        assertNotEquals("premise: the album's row moved", before, m.track.first)
        m.show(v, 0, m.count - 1)
        assertEquals(v.generation, m.gen)
        assertEquals(target, m.row(m.track.first)?.id)
        assertEquals(m.track.first, m.focus.row)
    }

    @Test
    fun aWindowAtThePageGenerationReadsThatSnapshot() = runBlocking {
        val f = fake(FakeSet.Main)
        val m = model(f)
        val v0 = f.library.view()
        val total = v0.page(Listing.Albums, 0, 0).total
        f.library.removeRoot(f.library.roots.value.last().uri)
        assertTrue("premise: the rescan drops albums", f.library.view().page(Listing.Albums, 0, 0).total < total)
        m.show(m.pageView(MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)), m.count - WINDOW_ROWS, m.count - 1)
        assertEquals(v0.generation, m.paging.window!!.generation)
        assertEquals(total, m.paging.window!!.total)
    }

    @Test
    fun aRescanWithALetterRowOnTopPutsThatLetterRowBackOnTop() = runBlocking {
        val f = fake(FakeSet.Main)
        val m = model(f)
        val v0 = f.library.view()
        m.show(v0, 0, m.count - 1)
        val n = v0.page(Listing.Albums, 0, 0).total
        val cobalt = v0.page(Listing.Albums, 0, n).rows.first { it.title == COBALT }.id
        val letter = m.map!!.rowOfIndex(v0.indexOf(Listing.Albums, Kind.Album, cobalt)!!) - 1
        assertEquals("premise: a letter row sits above the album", -1, m.map!!.indexAt(letter))
        m.track.toRow(letter)
        f.library.removeRoot(f.library.roots.value.last().uri)
        val v = f.library.view()
        m.migrate(v, 0L)
        val now = m.map!!.rowOfIndex(v.indexOf(Listing.Albums, Kind.Album, cobalt)!!) - 1
        assertEquals("premise: the letter row stays above the album", -1, m.map!!.indexAt(now))
        assertEquals(now, m.track.first)
        assertEquals(now, m.focus.row)
    }

    @Test
    fun aColumnEntryGivesAnAlbumSheetAndALetterRowNone() = runBlocking {
        val f = fake()
        val m = model(f)
        val v = f.library.view()
        m.show(v, 0, m.count - 1)
        val letter = (0 until m.count).first { m.letterAt(it) >= 0 }
        assertNull(m.target(letter))
        val r = (0 until m.count).first { m.row(it) != null }
        val t = requireNotNull(m.target(r))
        assertEquals(SheetKind.Album, t.kind)
        assertEquals(Listing.AlbumTracks(m.row(r)!!.id), t.listing)
        assertEquals(Kind.Album, t.pin)
        assertEquals(SheetFrom.AlbumCol, t.from)
    }

    companion object {
        private const val SDK = 35
        private const val W = 720
        private const val H = 1280
        private const val GRID_COLS = 3
        private const val COBALT = "Cobalt Rooms"
        private const val WINDOW_ROWS = 4
    }
}
