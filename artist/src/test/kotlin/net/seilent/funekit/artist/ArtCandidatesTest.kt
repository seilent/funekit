package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Properties

class ArtCandidatesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val http = Fixtures()
    private val time = FakeTime()
    private val dir by lazy { tmp.newFolder("art") }
    private val cache by lazy { ArtCache(dir) }
    private val picks = ArtPicks()
    private val same = listOf("Same Name", "SAME  name", "ｓａｍｅ name", " same name ", "Same\tName")

    private fun art() = ArtistArt(http, AwtCodec(), cache, { Sources.COMMONS }, TEST_AGENT, { time.now }, time, picks = picks)

    private fun mbidOf(i: Int) = "00000000-0000-4000-8000-%012d".format(i)

    private fun hit(key: String, name: String, mbid: String?) =
        cache.putHit(ArtCache.Entry(key, name, mbid, Sources.DEEZER, null, "https://e/$key.jpg", time.now++, key), byteArrayOf(key.length.toByte()))

    private fun seed(): List<Pair<String, String?>> {
        same.forEachIndexed { i, n -> hit(mbidOf(i + 1), n, mbidOf(i + 1)) }
        hit(mbidOf(6), "ab", mbidOf(6))
        hit(mbidOf(7), "abc", mbidOf(7))
        hit("name:abd", "abd", null)
        hit(mbidOf(8), "", mbidOf(8))
        hit("name:aaa early", "Aaa Early", mbidOf(1))
        hit("name:zzz late", "Zzz Late", mbidOf(1))
        val (k, k2) = ArtPicks.keys("TSUKI", null, false)
        val p = ArtPicks.Pick(k, k2, "TSUKI", ArtPicks.IMAGE, Sources.DEEZER, "https://e/TSUKI.jpg", "TSUKI", "", null, null, time.now++).also { picks.add(it) }
        hit(p.cacheKey, "TSUKI", null)
        for (i in 0 until 188) {
            val n = if (i < 90) "Artist %03d".format(i) else "アーティスト%03d".format(i)
            if (i % 3 == 0) hit("name:${Names.fold(n)}", n, null) else hit(mbidOf(100 + i), n, mbidOf(100 + i))
        }
        return listed().map { it.name to it.mbid }
    }

    private fun listed(): List<ArtCache.Entry> = (dir.listFiles { f -> f.name.endsWith(".meta") } ?: emptyArray())
        .mapNotNull { f -> Properties().apply { f.reader().use { load(it) } }.getProperty("key")?.let { cache.hit(it) } }
        .sortedBy { Names.fold(it.name) }

    private fun oracle(a: ArtistArt, all: List<ArtCache.Entry>, name: String, mbid: String?): List<ArtCache.Entry> {
        val f = Names.fold(name)
        val m = Names.mbid(mbid)
        return (all.filter { Names.fold(it.name) == f || (m != null && it.mbid == m) } + listOfNotNull(cache.hit(a.keyOf(name, mbid)), picks.of(mbid, name)?.let { cache.hit(it.cacheKey) }))
            .distinctBy { it.key }
    }

    private fun expected(a: ArtistArt, all: List<ArtCache.Entry>, name: String, mbid: String?): List<String> = oracle(a, all, name, mbid).map { it.key }

    private fun shown(l: List<ArtCache.Entry>) = l.map { "${it.key} ${it.w}x${it.h}" }

    @Test
    fun candidatesEqualTheWholeScanInItsOrder() {
        val stored = seed()
        val a = art()
        val all = listed()
        assertEquals(all.map { it.key }, cache.entries().map { it.key })
        val ids = stored.mapNotNull { it.second }
        val lookups = ArrayList<Pair<String, String?>>()
        stored.map { it.first }.distinct().forEachIndexed { i, n ->
            for (v in listOf(n, n.uppercase(), n.replace(" ", "  "))) {
                lookups += v to null
                stored.firstOrNull { it.first == n }?.second?.let { lookups += v to it }
                lookups += v to ids[(i + 1) % ids.size]
            }
        }
        for (i in 0 until 20) {
            lookups += "Nobody %02d".format(i) to null
            lookups += "Nobody %02d".format(i) to mbidOf(3)
        }
        for ((n, m) in lookups) assertEquals("$n $m", expected(a, all, n, m), a.candidates(n, m).map { it.key })
    }

    @Test
    fun aNewHitIsACandidateAtItsPlace() {
        seed()
        val a = art()
        assertEquals(expected(a, listed(), "Same Name", null), a.candidates("Same Name", null).map { it.key })
        hit(mbidOf(9), "same NAME", mbidOf(9))
        val keys = a.candidates("Same Name", null).map { it.key }
        assertTrue(mbidOf(9) in keys)
        assertEquals(expected(a, listed(), "Same Name", null), keys)
    }

    @Test
    fun aRebuildAfterASizeOrADropGivesTheWholeScan() {
        seed()
        val a = art()
        a.candidates("Same Name", mbidOf(1))
        val e = cache.hit(mbidOf(2))!!
        cache.putSize(e.key, e.at, Size(900, 900))
        assertEquals(shown(oracle(a, listed(), "Same Name", mbidOf(1))), shown(a.candidates("Same Name", mbidOf(1))))
        cache.drop(mbidOf(3))
        assertEquals(shown(oracle(a, listed(), "Same Name", mbidOf(1))), shown(a.candidates("Same Name", mbidOf(1))))
    }
}
