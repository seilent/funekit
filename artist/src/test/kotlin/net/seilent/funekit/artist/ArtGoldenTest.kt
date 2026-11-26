package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class ArtGoldenTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val names = listOf("f5a383c16210a8ea4dbf.meta", "fdd73749ac65905f81af.meta", "1a3f934c19eb4074b856.miss", "36e8bb102593070cfc91.miss", "f5a383c16210a8ea4dbf.src", "fdd73749ac65905f81af.src", "names.properties", "bare.properties")
    private val time = FakeTime()

    private fun golden(name: String): ByteArray = ArtGoldenTest::class.java.getResourceAsStream("/golden/$name")!!.readBytes()

    private fun props(b: ByteArray) = Properties().apply { load(b.inputStream().reader(Charsets.UTF_8)) }.let { p -> p.stringPropertyNames().associateWith { p.getProperty(it) } }

    private fun copy(): File = tmp.newFolder().also { d -> names.forEach { File(d, it).writeBytes(golden("cache/$it")) } }

    private fun targets(): Map<String, String> = String(golden("targets.tsv"), Charsets.UTF_8).lines().filter { it.isNotEmpty() }.associate { it.substringBefore('\t') to it.substringAfter('\t') }

    @Test
    fun theGoldenHitMissAndChannelRecordsSaveBackToEqualKeyAndValueSets() {
        val dir = copy()
        val c = ArtCache(dir)
        for (key in listOf("mbid:00000000-0000-4000-8000-000000000001", "name:name c")) {
            val e = c.hit(key)!!
            c.putHit(e, c.source(key)!!)
            if (e.sized.isNotEmpty()) c.putSized(key, e.at, e.sized)
        }
        for (key in listOf("name:name d", "name:name e")) {
            val m = c.miss(key)!!
            c.putMiss(key, m.at, m.sources, m.floor!!, m.keyHash)
        }
        for (n in names.filter { it.endsWith(".meta") || it.endsWith(".miss") }) {
            val want = props(golden("cache/$n")) + if (n.endsWith(".meta")) mapOf("renders" to "") else emptyMap()
            assertEquals(n, want, props(File(dir, n).readBytes()))
        }
        assertEquals("UC0000000000000000000002", c.hit("name:name c")!!.channel)
        assertEquals(mapOf("yt" to "1799999997000", "ytFailed" to "1799999997500"), c.miss("name:name d")!!.kept)
        assertEquals(emptyMap<String, String>(), c.miss("name:name e")!!.kept)
        assertEquals("00000000-0000-4000-8000-000000000003", c.mbid("Name F")!!.mbid)
        assertEquals("00000000-0000-4000-8000-000000000002", c.bare("Name H")!!.mbid)
    }

    @Test
    fun theYouTubeMissKeysSurviveALookupWithAnExtensionAndWithNone() {
        for (ext in listOf<ArtExtension?>(FakeExtension("youtube"), null)) {
            val dir = copy()
            val c = ArtCache(dir)
            val http = Fixtures()
            time.now = 1_800_000_000_000L
            val on = Sources.COMMONS.copy(deezer = true, order = Sources.ORDER + "youtube", plugins = mapOf("youtube" to true))
            val a = ArtistArt(http, AwtCodec(), c, { on }, TEST_AGENT, { time.now }, time, extensions = listOfNotNull(ext))
            a.get("Name D", null, 240, 320)
            val m = c.miss("name:name d")
            assertNotNull(m)
            assertEquals("1799999997000", m!!.kept["yt"])
            assertEquals("1799999997500", m.kept["ytFailed"])
            assertTrue(m.at > 1_799_999_997_000L)
        }
    }

    @Test
    fun theStoredTargetStringsParseAndPrintBack() {
        val t = targets()
        assertEquals(t["phoneTarget"], Size.parse(t["phoneTarget"]).toString())
        assertEquals(t["deviceTargets"], Targets.devices(Targets.devices(t["deviceTargets"])))
        assertEquals(t["miss floor"], Size.parse(t["miss floor"]).toString())
        val parsed = Targets.of(t["phoneTarget"], t["deviceTargets"])
        assertEquals(t["meta sized"], "${ArtistArt.SIZED_RULES} ${parsed.phone} ${parsed.device}")
        assertEquals(t["meta sized"], ArtCache(copy()).hit("mbid:00000000-0000-4000-8000-000000000001")!!.sized)
    }
}
