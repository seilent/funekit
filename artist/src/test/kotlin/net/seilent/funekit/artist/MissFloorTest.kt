package net.seilent.funekit.artist

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class MissFloorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val name = "Sztest"
    private val key = "name:sztest"
    private val mbid = "8e8b4b44-6e4c-4f0c-9d8d-0c1c2a3b4d5e"
    private val dzSearch = "https://api.deezer.com/search/artist?q=$name"
    private val mbSearch = "https://musicbrainz.org/ws/2/artist/?query=$name&fmt=json&limit=10"
    private val mbArtist = "https://musicbrainz.org/ws/2/artist/$mbid?inc=url-rels&fmt=json"
    private val one = "11111111111111111111111111111111"
    private fun dz(side: Int = 1000) = "https://cdn-images.dzcdn.net/images/artist/$one/${side}x$side-000000-80-0-0.jpg"

    private val http = Fixtures()
    private val time = FakeTime()
    private val dir by lazy { tmp.newFolder("art") }
    private val cache by lazy { ArtCache(dir) }
    private var sources = Sources.NONE.copy(deezer = true)

    private fun art() = ArtistArt(http, AwtCodec(), cache, { sources }, TEST_AGENT, { time.now }, time, blocks = ArtBlocks(), targets = { Targets(Size(720, 1680)) })

    private fun queue(url: String, r: Response) {
        http.queued.getOrPut(url) { ArrayDeque() } += r
    }

    private fun search(image: String?) {
        queue(mbSearch, Response(404, ByteArray(0)))
        val data = image?.let { "{\"name\":\"$name\",\"nb_fan\":1,\"picture_xl\":\"${dz()}\"}" } ?: ""
        queue(dzSearch, Response(200, "{\"data\":[$data]}".toByteArray()))
        if (image == null) return
        queue(dz(), Response(200, Fixtures.gen(image)))
        queue(dz(1680), Response(404, ByteArray(0)))
    }

    private fun legacy(ext: String, vararg extra: String) {
        cache.putMbid(name, null, time.now)
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
        val lines = listOf("state=miss", "key=$key", "at=${time.now}", "sources=${Sources.DEEZER}", "cleaned=3") + extra
        File(dir, "$hash$ext").writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun lookedUp(before: Int) = http.calls.drop(before).contains(dzSearch)

    private fun theV1CallLooksUp() {
        val before = http.calls.size
        search("300x400@5")
        assertNotNull(art().get(name, null, 240, 320))
        assertTrue(lookedUp(before))
    }

    @Test
    fun aMissAtTheBackdropFloorDoesNotAnswerTheSmallerV1Call() {
        search("300x400@5")
        assertNull(art().get(name, null, 360, 480))
        assertNotNull(cache.miss(key))
        val before = http.calls.size
        search("300x400@5")
        val a = art().get(name, null, 240, 320)
        assertEquals(240 to 320, a?.let { Images.size(it.jpeg) })
        assertEquals(Sources.DEEZER, a!!.source)
        assertTrue(lookedUp(before))
        assertNotNull(cache.hit(key))
        assertTrue(http.missing.isEmpty())
    }

    @Test
    fun aPreviewIsNotAnsweredByAMissAtALargerFloor() {
        queue(mbArtist, Response(200, "{\"id\":\"$mbid\",\"name\":\"$name\",\"relations\":[]}".toByteArray()))
        search("300x400@5")
        assertNull(art().get(name, mbid, 360, 480))
        assertNotNull(cache.miss(mbid))
        queue(mbArtist, Response(200, "{\"id\":\"$mbid\",\"name\":\"$name\",\"relations\":[]}".toByteArray()))
        search("300x400@5")
        assertArrayEquals(Fixtures.gen("300x400@5"), art().previewArtist(name, mbid, 240, 320))
        assertNull(cache.hit(mbid))
        assertTrue(http.missing.isEmpty())
    }

    @Test
    fun aMissAtASmallerFloorAnswersALargerCall() {
        search("200x300@5")
        assertNull(art().get(name, null, 240, 320))
        val before = http.calls.size
        assertNull(art().get(name, null, 360, 480))
        assertEquals(before, http.calls.size)
    }

    @Test
    fun callsThatFloorAlikeShareTheMiss() {
        search("300x400@5")
        assertNull(art().get(name, null, 720, 1280))
        val before = http.calls.size
        assertNull(art().get(name, null, 360, 480))
        assertNull(art().get(name, null, 480, 800))
        assertEquals(before, http.calls.size)
    }

    @Test
    fun theMissFileCarriesTheFloorAsWidthByHeight() {
        search("300x400@5")
        assertNull(art().get(name, null, 720, 1280))
        val f = dir.listFiles { x -> x.name.endsWith(".miss") }!!.single()
        assertTrue(f.readText().lines().contains("floor=360x480"))
    }

    @Test
    fun aMissLargerOnlyInHeightDoesNotAnswer() {
        search(null)
        assertNull(art().get(name, null, 360, 400))
        val before = http.calls.size
        search(null)
        assertNull(art().get(name, null, 360, 320))
        assertTrue(lookedUp(before))
        assertEquals(Size(360, 320), cache.miss(key)!!.floor)
    }

    @Test
    fun aMissLargerOnlyInWidthDoesNotAnswer() {
        search(null)
        assertNull(art().get(name, null, 300, 480))
        val before = http.calls.size
        search(null)
        assertNull(art().get(name, null, 240, 480))
        assertTrue(lookedUp(before))
        assertEquals(Size(240, 480), cache.miss(key)!!.floor)
    }

    @Test
    fun aMissWithNoFloorAnswersEveryCall() {
        legacy(".miss")
        assertNull(cache.miss(key)!!.floor)
        assertNull(art().get(name, null, 240, 320))
        assertNull(art().get(name, null, 360, 480))
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun anOldMetaMissAnswersEveryCall() {
        legacy(".meta")
        assertNotNull(cache.miss(key))
        assertNull(art().get(name, null, 240, 320))
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun aMalformedFloorAnswersEveryCall() {
        legacy(".miss", "floor=bad")
        assertNull(art().get(name, null, 240, 320))
        legacy(".miss", "floor=0x320")
        assertNull(art().get(name, null, 240, 320))
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun aFlooredMissIsLookedUpAgainAfterItsTtl() {
        search("300x400@5")
        assertNull(art().get(name, null, 360, 480))
        time.now += ArtistArt.TTL_MS
        val before = http.calls.size
        val at = time.now
        search("300x400@5")
        assertNull(art().get(name, null, 360, 480))
        assertTrue(lookedUp(before))
        assertEquals(at, cache.miss(key)!!.at)
    }

    @Test
    fun aSourceClearKeepsTheFloor() {
        search("300x400@5")
        assertNull(art().get(name, null, 360, 480))
        cache.clearSource(Sources.DEEZER)
        val m = cache.miss(key)!!
        assertEquals(Size(360, 480), m.floor)
        assertFalse(Sources.DEEZER in m.sources)
    }

    @Test
    fun aMissWhoseRecordCannotBeSavedStillComesDue() {
        search(null)
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
        File(dir, "$hash.miss").mkdir()
        val a = art()
        a.beginMark()
        val now = time.now
        assertThrows(StoreException::class.java) { a.get(name, null, 240, 320) }
        assertEquals(now + ArtistArt.TTL_MS, a.mark().until)
    }

    @Test
    fun aMissAtALargerCallWritesTheBackdropFloor() {
        search("300x400@5")
        assertNull(art().get(name, null, 720, 1280))
        assertEquals(Size(360, 480), cache.miss(key)!!.floor)
        assertTrue(http.missing.isEmpty())
        theV1CallLooksUp()
    }
}
