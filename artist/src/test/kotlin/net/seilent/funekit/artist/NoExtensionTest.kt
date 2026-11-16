package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoExtensionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val radiohead = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
    private val baseHosts = setOf("musicbrainz.org", "www.wikidata.org", "www.theaudiodb.com", "r2.theaudiodb.com", "api.discogs.com", "i.discogs.com", "api.deezer.com")

    private fun base(h: String) = h in baseHosts || h.endsWith(".wikimedia.org") || h.endsWith(".wikipedia.org") || h.endsWith(".dzcdn.net")

    private val http = Fixtures()
    private val time = FakeTime()
    private val sources = Sources(
        theAudioDb = true,
        discogs = true,
        fanartTv = true,
        deezer = true,
        commons = true,
        order = Sources.order("theaudiodb,fanarttv,youtube,discogs,commons,deezer"),
        plugins = mapOf("youtube" to true),
    )
    private val cache by lazy { ArtCache(tmp.newFolder("art")) }

    private fun art() = ArtistArt(http, AwtCodec(), cache, { sources }, TEST_AGENT, { time.now }, time).also {
        http.now = { time.now }
    }

    @Test
    fun aLookupABrowseAndAPickReachOnlyTheBaseHosts() {
        val a = art()
        a.get("Radiohead", radiohead, 384, 480)
        a.get("TSUKI", null, 240, 320)
        val c = Collected()
        a.browse("TSUKI", null, 53, c, Inline)
        a.offer("https://www.youtube.com/@tsukiniterasarete")
        assertTrue(http.hosts().isNotEmpty())
        assertTrue(http.hosts().toString(), http.hosts().all { base(it) })
        assertEquals(listOf("theaudiodb", "discogs", "commons", "deezer"), c.order)
        assertNull(c.search)
        assertEquals(ArtistArt.BROWSE_WORKERS, a.browseWorkers)
    }

    @Test
    fun aPlugInOnWithNoExtensionStaysOutOfTheMissSources() {
        assertNull(art().get("Nobody Here", null, 240, 320))
        val m = cache.miss("name:nobody here")!!
        assertEquals(setOf("theaudiodb", "discogs", "commons", "deezer"), m.sources)
    }

    @Test
    fun anOrphanPlugInAloneCountsAsOff() {
        val orphan = Sources(order = Sources.order("youtube"), plugins = mapOf("youtube" to true))
        val a = ArtistArt(http, AwtCodec(), cache, { orphan }, TEST_AGENT, { time.now }, time)
        val o = (a.offer("https://musicbrainz.org/artist/$radiohead") as Offered.Ok).offers.single()
        assertEquals(radiohead, o.mbid)
        assertEquals(radiohead, o.title)
        assertNull(a.scanBare())
        assertTrue(http.calls.toString(), http.calls.none { it.contains("musicbrainz.org") })
    }

    @Test
    fun aYouTubeLinkIsUnsupported() {
        assertEquals(PickLink.Unsupported, PickLink.parse("https://www.youtube.com/@tsukiniterasarete"))
        assertEquals(PickLink.Unsupported, PickLink.parse("https://music.youtube.com/channel/UC368BL46Vr1Y2ZbyWXY-ebw"))
        assertEquals(PickLink.UNSUPPORTED, (art().offer("https://www.youtube.com/@tsukiniterasarete") as Offered.Refused).reason)
    }
}
