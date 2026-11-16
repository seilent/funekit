package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelLookupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val suisei = "8eeb59f4-0071-4261-b1f8-91d8294622d2"
    private val wrongSuisei = "e43c7000"
    private val chelly = "4dfe9c0b-7602-4189-9919-151d493d1028"
    private val fujiAoi = "03b96523-db37-4e85-ac9d-b7ceb6926e9b"
    private val itsuki = "61d436ef-099b-4e25-b936-de7bda576565"
    private val dazbee = "10623c33-205c-49f4-b8a0-55a105dd4fa3"
    private val day = 24 * 3600 * 1000L

    private val http = Fixtures()
    private val time = FakeTime()
    private var sources = Sources.COMMONS.copy(order = Sources.ORDER)
    private val cache by lazy { ArtCache(tmp.newFolder("art")) }
    private val blocks by lazy { ArtBlocks(java.io.File(tmp.root, "artblock.tsv")) }
    private val picks = ArtPicks()
    private val logs = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun art() = ArtistArt(http, AwtCodec(), cache, { sources }, TEST_AGENT, { time.now }, time, blocks = blocks, picks = picks, log = { logs += it }).also {
        http.now = { time.now }
    }

    private fun queries() = http.calls.count { it.startsWith("https://musicbrainz.org/ws/2/artist/?query=") }

    private fun key(tag: String) = "name:${Names.fold(tag)}"

    @Test
    fun aBareVariantResolvesOnlyOnAUniqueMusicBrainzMatch() {
        sources = Sources.COMMONS.copy(deezer = true)
        assertNull(art().get("Suisei Channel", null, 240, 320))
        assertTrue(http.calls.contains("https://musicbrainz.org/ws/2/artist/?query=Suisei&fmt=json&limit=10"))
        assertTrue(http.calls.none { it.contains("/artist/$wrongSuisei") })
        assertFalse(http.calls.contains("https://api.deezer.com/search/artist?q=Suisei"))
        assertNull(cache.bare("Suisei Channel")!!.mbid)
        assertNull(cache.mbid("Suisei"))
    }

    @Test
    fun aBareLookupIgnoresThePlainNameStore() {
        sources = Sources.COMMONS.copy(deezer = true)
        val full = "e43c7000-2a81-4a4b-a0a2-61c84fd96a34"
        cache.putMbid("Suisei", full, time.now)
        assertNull(art().get("Suisei Channel", null, 240, 320))
        assertTrue(http.calls.none { it.contains("/artist/$wrongSuisei") })
        assertEquals(full, art().keyOf("Suisei", null))
    }

    @Test
    fun aChannelSuffixKeepsItsUniqueBareHitAndDropsAnAmbiguousOne() {
        sources = Sources.COMMONS.copy(deezer = true)
        val a = art().get("DAZBEE official", null, 240, 320)!!
        assertEquals(dazbee, a.mbid)
        val e = cache.hit(key("DAZBEE official"))!!
        assertTrue(e.bare)
        assertEquals("DAZBEE official", e.resolvedBy)
        assertTrue(art().wrongArtistOffered(e, "DAZBEE official"))
        assertNull(art().get("Iris Official", null, 240, 320))
        assertTrue(http.calls.contains("https://musicbrainz.org/ws/2/artist/?query=Iris&fmt=json&limit=10"))
        assertTrue(http.calls.none { it.contains("/artist/a2aeb4e5") })
        assertFalse(http.calls.contains("https://api.deezer.com/search/artist?q=Iris"))
    }

    @Test
    fun aBareNullIsLookedUpAgainAfterTheTtl() {
        sources = Sources.COMMONS.copy(deezer = true)
        val q = "https://musicbrainz.org/ws/2/artist/?query=Suisei&fmt=json&limit=10"
        assertNull(art().get("Suisei Channel", null, 240, 320))
        time.now += 3 * day
        cache.drop(key("Suisei Channel"))
        assertNull(art().get("Suisei Channel", null, 240, 320))
        assertEquals(1, http.calls.count { it == q })
        time.now += 5 * day
        cache.drop(key("Suisei Channel"))
        assertNull(art().get("Suisei Channel", null, 240, 320))
        assertEquals(2, http.calls.count { it == q })
    }

    @Test
    fun clearEmptiesTheBareStore() {
        cache.putBare("Suisei", null, time.now)
        assertNotNull(cache.bare("Suisei"))
        cache.clear()
        assertNull(cache.bare("Suisei"))
        assertNull(ArtCache(java.io.File(tmp.root, "art")).bare("Suisei"))
    }

    @Test
    fun wrongArtistOnABareHitLeavesThePlainTag() {
        sources = Sources.COMMONS.copy(deezer = true)
        val a = art()
        assertNotNull(a.get("DAZBEE official", null, 240, 320))
        val e = cache.hit(key("DAZBEE official"))!!
        blocks.add(ArtBlocks.Reject(e.key, e.mbid, a.resolvedBy(e), e.name, time.now))
        a.forget(e)
        assertNull(art().get("DAZBEE official", null, 240, 320))
        assertEquals(dazbee, art().get("DAZBEE", null, 240, 320)!!.mbid)
    }

    @Test
    fun anOlderMissIsLookedUpOnceUnderTheNewRules() {
        val tag = "NÄO Official Channel"
        cache.putMiss(key(tag), time.now, sources.enabled, Size(240, 320))
        val f = java.io.File(tmp.root, "art").listFiles { x -> x.name.endsWith(".miss") }!!.single()
        f.writeText(f.readText().lines().map { if (it.startsWith("cleaned")) "cleaned=2" else it }.joinToString("\n"))
        assertFalse(cache.miss(key(tag))!!.cleaned)
        assertNull(art().get(tag, null, 240, 320))
        assertEquals(2, queries())
        assertTrue(f.readText().lines().contains("cleaned=3"))
        assertNull(art().get(tag, null, 240, 320))
        assertEquals(2, queries())
    }

    @Test
    fun theBareScanDropsAmbiguousOldHitsAndKeepsTheRest() {
        val iris = "a2aeb4e5-0000-4000-8000-000000000000"
        val megu = "86d34b4b-0000-4000-8000-000000000000"
        val mair = "944d7f7e-0000-4000-8000-000000000000"
        val img = Images.jpeg(Images.photo(500, 500), 90)
        fun seed(tag: String, mbid: String?, source: String, by: String) =
            cache.putHit(ArtCache.Entry(key(tag), tag, mbid, source, null, "https://e/$tag.jpg", time.now, resolvedBy = by), img)
        seed("Iris Official", iris, Sources.DEEZER, "Iris")
        seed("Megu Channel", megu, Sources.DEEZER, "")
        seed("DAZBEE official", dazbee, Sources.DEEZER, "DAZBEE")
        seed("MaiR Channel", mair, Sources.DEEZER, "MaiR")
        val (k, k2) = ArtPicks.keys("MaiR Channel", null, false)
        picks.add(ArtPicks.Pick(k, k2, "MaiR Channel", ArtPicks.IMAGE, Sources.LINK, "https://e/mair.jpg", "https://e/mair.jpg", "", null, null, time.now))
        val off = sources
        sources = Sources.NONE
        val before = http.calls.size
        assertNull(art().scanBare())
        assertEquals(before, http.calls.size)
        assertNotNull(cache.hit(key("Iris Official")))
        sources = off
        assertEquals(1, art().scanBare())
        assertNull(cache.hit(key("Iris Official")))
        assertEquals(listOf(key("Iris Official")), blocks.drops().map { it.key })
        assertNotNull(cache.hit(key("Megu Channel")))
        assertNotNull(cache.hit(key("DAZBEE official")))
        assertNotNull(cache.hit(key("MaiR Channel")))
        assertTrue(http.calls.none { it.contains("query=MaiR") || it.contains("query=Megu") })
        val n = http.calls.size
        assertEquals(0, art().scanBare())
        assertEquals(n, http.calls.size)
    }

    @Test
    fun twoTagsSharingABareWordKeepTheirOwnAnswer() {
        sources = Sources.COMMONS.copy(deezer = true)
        val a = art()
        assertEquals(dazbee, a.get("DAZBEE official", null, 240, 320)!!.mbid)
        assertEquals(dazbee, a.get("DAZBEE Channel", null, 240, 320)!!.mbid)
        val e = cache.hit(key("DAZBEE official"))!!
        blocks.add(ArtBlocks.Reject(e.key, e.mbid, a.resolvedBy(e), e.name, time.now))
        a.forget(e)
        assertNull(art().get("DAZBEE official", null, 240, 320))
        assertNull(cache.bare("DAZBEE official")!!.mbid)
        a.forget(key("DAZBEE Channel"))
        assertEquals(dazbee, art().get("DAZBEE Channel", null, 240, 320)!!.mbid)
        assertEquals(dazbee, cache.bare("DAZBEE Channel")!!.mbid)
    }

}
