package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExtensionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val http = Fixtures()
    private val time = FakeTime()
    private val fake = FakeExtension()
    private var sources = Sources.COMMONS.copy(order = Sources.ORDER + "fake", plugins = mapOf("fake" to true))
    private var targets = Targets.NONE
    private val cache by lazy { ArtCache(tmp.newFolder("art")) }
    private val small = "https://fake.example/img/a=s900"
    private val big = "https://fake.example/img/a=s0"

    private fun art() = ArtistArt(http, AwtCodec(), cache, { sources }, TEST_AGENT, { time.now }, time, targets = { targets }, extensions = listOf(fake)).also {
        http.now = { time.now }
    }

    private fun serve(url: String, spec: String) {
        http.queued.getOrPut(url) { ArrayDeque() } += Response(200, Fixtures.gen(spec))
    }

    private fun hit(key: String, url: String, spec: String): ArtCache.Entry {
        val src = Fixtures.gen(spec)
        cache.putHit(ArtCache.Entry(key, "Name U", null, "fake", null, url, 5, "a", ArtCache.sha256(src)), src)
        return cache.hit(key)!!
    }

    @Test
    fun aVetoSkipsTheMusicBrainzResolveAndThePlainLookups() {
        fake.vetoed = setOf("UC0000000000000000000009")
        fake.due = true
        assertNull(art().get("UC0000000000000000000009", null, 240, 320))
        assertTrue(fake.calls.contains("identify UC0000000000000000000009"))
        assertTrue(fake.calls.contains("resolve UC0000000000000000000009"))
        assertTrue(http.calls.none { it.contains("query=") })
        assertTrue(fake.calls.none { it.startsWith("candidates") })
    }

    @Test
    fun anOfferedLinkResolvesThroughTheExtension() {
        val page = FakeExtension.Page("p")
        fake.links = mapOf("https://fake.example/page" to page)
        fake.offered = mapOf(page to small)
        serve(small, "1000x1000@3")
        val o = (art().offer("https://fake.example/page") as Offered.Ok).offers.single()
        assertEquals("fake", o.source)
        assertEquals(small, o.url)
        assertNotNull(o.src)
        assertEquals(PickLink.Direct("https://fake.example/page"), PickLink.parse("https://fake.example/page"))
        assertEquals(PickLink.UNSUPPORTED, (art().offer("https://www.youtube.com/@x") as Offered.Refused).reason)
    }

    @Test
    fun aBrowseSectionEndsWithTheExtensionsSearch() {
        val c = Collected()
        val a = art()
        a.browse("TSUKI", null, 53, c, Inline)
        assertEquals(listOf(Wikimedia.SOURCE, "fake", "fake search"), c.order)
        assertEquals(c.order.toSet(), c.done.toSet())
        assertEquals(BrowseFeed.Search.Unavailable(gated = true), c.search)
        assertTrue(fake.calls.containsAll(listOf("browse fake", "browse fake search")))
        assertEquals(ArtistArt.BROWSE_WORKERS + 2, a.browseWorkers)
    }

    @Test
    fun aTagResolvesToAStoredChannel() {
        fake.due = true
        val avatar = "https://fake.example/avatar.jpg"
        fake.resolved = mapOf("Name Z" to ArtExtension.Resolved("UC0000000000000000000009", null, Candidate(avatar, "fake", id = "avatar")))
        serve(avatar, "1000x1000@3")
        val a = art()
        val got = a.get("Name Z", null, 240, 320)!!
        assertEquals("fake", got.source)
        val e = cache.hit("name:name z")!!
        assertEquals("UC0000000000000000000009", e.channel)
        assertEquals(avatar, e.url)
        assertTrue(a.wrongArtistOffered(e))
    }

    @Test
    fun theMissKeysTheExtensionWritesAreStoredAndReadBack() {
        fake.due = true
        fake.keys = mapOf("fakeAt" to "5")
        assertNull(art().get("Name Y", null, 240, 320))
        assertEquals(mapOf("fakeAt" to "5"), cache.miss("name:name y")!!.kept)
        fake.due = false
        fake.calls.clear()
        assertNull(art().get("Name Y", null, 240, 320))
        assertEquals(listOf("due {fakeAt=5}"), fake.calls.filter { it.startsWith("due") })
    }

    @Test
    fun aResizeUrlIsFetched() {
        fake.found = listOf(Candidate(small, "fake"))
        fake.resized = mapOf(small to big)
        serve(big, "1600x1600@5")
        assertEquals("fake", art().get("Name R", null, 240, 320)!!.source)
        assertEquals(big, cache.hit("name:name r")!!.url)
        assertTrue(http.calls.contains(big))
        assertFalse(http.calls.contains(small))
    }

    @Test
    fun anUpgradeThroughResizeTakesTheSameImageLarger() {
        targets = Targets(Size(720, 1680))
        val e = hit("k", small, "900x900@5")
        fake.resized = mapOf(small to big)
        serve(big, "3000x3000@5")
        val a = art()
        assertTrue(a.upgradable(e))
        assertEquals(listOf("k"), a.upgrades().map { it.key })
        assertTrue(a.upgrade(e, 240, 320).replaced)
        val n = cache.hit("k")!!
        assertTrue(cache.rendered("k", 240, 320) != null)
        assertEquals(big, n.url)
        assertEquals(e.id, n.id)
        assertEquals(3000 to 3000, n.w to n.h)
        assertEquals(listOf(big), http.calls)
        assertFalse(a.upgradable(n))
    }

    @Test
    fun anUpgradeThroughResizeTakesTheSameImageLargerThatStillDoesNotFillTheDevice() {
        targets = Targets(Size(720, 1280))
        val e = hit("k", small, "900x900@5")
        fake.resized = mapOf(small to big)
        serve(big, "1080x1080@5")
        val a = art()
        assertTrue(a.upgradable(e))
        assertTrue(a.upgrade(e, 720, 1280).replaced)
        val n = cache.hit("k")!!
        assertEquals(big, n.url)
        assertEquals(1080 to 1080, n.w to n.h)
        assertEquals(listOf(big), http.calls)
        assertTrue(http.missing.isEmpty())
    }

    @Test
    fun anUpgradeThroughResizeNeverUndoesAChangeMadeWhileItDownloaded() {
        targets = Targets(Size(720, 1680))
        val e = hit("k", small, "900x900@5")
        fake.resized = mapOf(small to big)
        http.answer = { u ->
            if (u == big) {
                cache.drop("k")
                Response(200, Fixtures.gen("1600x1600@5"))
            } else {
                null
            }
        }
        assertFalse(art().upgrade(e, 240, 320).replaced)
        assertNull(cache.hit("k"))
    }

    @Test
    fun anEntryWithNoIdTakesTheExtensionsIdRuleForItsBlock() {
        val src = Fixtures.gen("900x900@5")
        cache.putHit(ArtCache.Entry("k", "Name U", null, "fake", null, small, 5, "", ArtCache.sha256(src)), src)
        val e = cache.hit("k")!!
        val a = art()
        assertEquals("https://fake.example/img/a", a.imageId(e))
        assertFalse(a.blocked(e))
        a.blocks.add(ArtBlocks.Block("k", null, "fake", a.imageId(e), "", "Name U", 6))
        assertTrue(a.blocked(e))
    }

    @Test
    fun aThrowingResizeFallsBackToThePlainFetchAndTheAvatarStillWritesItsMiss() {
        fake.resizeFails = true
        fake.found = listOf(Candidate(small, "fake"))
        fake.resized = mapOf(small to big, "https://fake.example/gone.jpg" to big)
        serve(small, "1600x1600@5")
        assertEquals(small, cache.hit(art().get("Name R", null, 240, 320)!!.key!!)!!.url)
        fake.found = emptyList()
        fake.due = true
        fake.resolved = mapOf("Name G" to ArtExtension.Resolved("UC0000000000000000000008", null, Candidate("https://fake.example/gone.jpg", "fake", id = "gone")))
        assertNull(art().get("Name G", null, 240, 320))
        assertNotNull(cache.miss("name:name g"))
        assertTrue(fake.calls.contains("resize https://fake.example/gone.jpg 0"))
    }

    @Test
    fun aSecondDueExtensionResolvesWhenTheFirstFindsNoImage() {
        val one = FakeExtension("f1")
        val two = FakeExtension("f2")
        one.due = true
        two.due = true
        one.resolved = mapOf("Name T" to ArtExtension.Resolved("UC0000000000000000000006", null, Candidate("https://fake.example/missing.jpg", "f1", id = "missing")))
        two.resolved = mapOf("Name T" to ArtExtension.Resolved("UC0000000000000000000007", null, Candidate("https://fake.example/two.jpg", "f2", id = "two")))
        serve("https://fake.example/two.jpg", "1000x1000@3")
        sources = Sources.COMMONS.copy(order = Sources.ORDER + "f1" + "f2", plugins = mapOf("f1" to true, "f2" to true))
        val a = ArtistArt(http, AwtCodec(), cache, { sources }, TEST_AGENT, { time.now }, time, extensions = listOf(one, two))
        assertEquals("f2", a.get("Name T", null, 240, 320)!!.source)
        assertEquals("UC0000000000000000000007", cache.hit("name:name t")!!.channel)
        assertTrue(one.calls.contains("resolve Name T"))
    }

    @Test
    fun aLowercasedHostReachesTheExtensionsLink() {
        val page = FakeExtension.Page("yt")
        fake.linkHosts = mapOf("www.youtube.com" to page)
        assertEquals(page, PickLink.parse("https://WWW.YouTube.com/@x", listOf(fake)))
        assertTrue(fake.calls.contains("link www.youtube.com"))
    }

    @Test
    fun aDueStepWithSearchOffMarksTheSweepPending() {
        fake.due = true
        val a = art()
        a.beginMark()
        assertNull(a.get("Name P", null, 240, 320, search = false))
        val m = a.mark()
        assertTrue(a.holds(m))
        assertFalse(a.holds(m, searchAllowed = true))
        assertFalse(fake.calls.contains("resolve Name P"))
    }

    @Test
    fun aResizeToTheSameUrlIsNoUpgrade() {
        targets = Targets(Size(720, 1680))
        val e = hit("k", small, "900x900@5")
        fake.resized = mapOf(small to small)
        serve(small, "3000x3000@5")
        assertFalse(art().upgrade(e, 240, 320).replaced)
        assertEquals(900 to 900, Images.size(cache.source("k")!!))
    }

    @Test
    fun anExtensionSectionTakesItsTilesThroughTheBrowse() {
        val preview = "https://fake.example/tile=s176"
        val full = "https://fake.example/tile=s900"
        fake.tiles = listOf(preview to full)
        http.answer = { u -> if (u == preview) Response(200, Fixtures.gen("176x176@4")) else null }
        val c = Collected()
        art().browse("TSUKI", null, 53, c, Inline)
        val t = c.of("fake")
        assertEquals(listOf(full), t.map { it.full })
        assertEquals(listOf("fake"), t.map { it.source })
        assertEquals(listOf(full), t.map { it.id })
        assertTrue(t.single().preview.isNotEmpty())
    }
}
