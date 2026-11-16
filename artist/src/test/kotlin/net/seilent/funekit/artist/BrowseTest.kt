package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class BrowseTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val http = Fixtures()
    private val time = FakeTime()
    private var sources = Sources.COMMONS.copy(discogs = true, deezer = true)
    private val cache by lazy { ArtCache(tmp.newFolder("art")) }

    private fun art() = ArtistArt(http, AwtCodec(), cache, { sources }, TEST_AGENT, { time.now }, time).also {
        http.now = { time.now }
    }

    private fun browse(a: ArtistArt = art()): List<Tile> {
        val c = Collected()
        a.browse("TSUKI", null, 53, c, Inline)
        return c.all()
    }

    @Test
    fun tilesComeInSectionOrder() {
        val c = Collected()
        art().browse("TSUKI", null, 53, c, Inline)
        assertEquals(listOf(Wikimedia.SOURCE, Sources.DISCOGS, Sources.DEEZER), c.order)
        assertEquals(c.order, c.done)
        val ranks = c.tiles.map { c.order.indexOf(it.first) }
        assertEquals(ranks.sorted(), ranks)
        assertEquals("dnb, Jump up", c.of(Sources.DISCOGS).single().caption)
        assertEquals(10, c.of(Sources.DEEZER).size)
        assertEquals(4, http.calls.count { it.startsWith("https://musicbrainz.org/ws/2/artist/") && "query=" !in it })
        assertTrue("missing ${http.missing}", http.missing.isEmpty())
    }

    @Test
    fun theLimitersHoldAcrossTheFanOut() {
        browse()
        fun gaps(hosts: Set<String>) = http.times.filter { (_, u) -> u.substringAfter("://").substringBefore('/') in hosts }.map { it.first }.zipWithNext { a, b -> b - a }
        assertTrue(gaps(setOf("musicbrainz.org")).all { it >= 1000 })
        assertTrue(gaps(setOf("api.discogs.com")).all { it >= 60_000L / 25 })
        for (h in listOf("i.discogs.com", "api.deezer.com", "cdn-images.dzcdn.net")) assertTrue(h, gaps(setOf(h)).all { it >= 1000 })
    }

    @Test
    fun disabledSourcesMakeNoCalls() {
        sources = Sources.COMMONS
        val c = Collected()
        art().browse("TSUKI", null, 53, c, Inline)
        assertEquals(listOf(Wikimedia.SOURCE), c.order)
        assertEquals(listOf(Wikimedia.SOURCE), c.done)
        assertEquals(null, c.search)
        assertTrue(c.tiles.isEmpty())
        assertEquals(setOf("musicbrainz.org"), http.hosts())
    }

    @Test
    fun aCancelStopsFurtherCalls() {
        val run = HttpRun()
        val c = Collected()
        var calls = -1
        c.onTile = {
            run.cancel()
            calls = http.calls.size
        }
        try {
            run.on { art().browse("TSUKI", null, 53, c, Inline) }
            fail()
        } catch (e: IOException) {
            assertEquals("cancelled", e.message)
        }
        assertEquals(1, c.tiles.size)
        assertEquals(calls, http.calls.size)
        assertEquals(c.order, c.done)
    }

    @Test
    fun aRuntimeErrorInOneCallSkipsThatTile() {
        val bad = Http { url, h ->
            if (url.contains("dzcdn.net") && url.contains("3c0e4ec7")) throw IllegalArgumentException("bad url")
            http.get(url, h)
        }
        val c = Collected()
        ArtistArt(bad, AwtCodec(), cache, { sources }, TEST_AGENT, { time.now }, time).browse("TSUKI", null, 53, c, Inline)
        assertEquals(9, c.of(Sources.DEEZER).size)
    }

    @Test
    fun aTapFetchesTheFullImage() {
        val tiles = browse()
        val a = art()
        val full = tiles.first { it.source == Sources.DISCOGS }
        val o = (a.take(full) as Offered.Ok).offers.single()
        assertEquals(Sources.DISCOGS, o.source)
        assertEquals(full.full, o.url)
        assertNotNull(o.src)
    }

    @Test
    fun aTapRefusesATooSmallTile() {
        val tiles = browse()
        val a = art()
        val small = tiles.first { it.source == Sources.DEEZER }
        http.queued[small.full] = ArrayDeque(listOf(Response(200, Images.jpeg(Images.photo(250, 250), 92))))
        assertEquals(PickLink.TOO_SMALL, (a.take(small) as Offered.Refused).reason)
    }
}
