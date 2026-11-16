package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLClassLoader

class FixturesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun module(name: String, rows: String, captures: Map<String, String> = emptyMap()): File {
        val d = tmp.newFolder(name)
        File(d, "fixtures").mkdirs()
        File(d, "fixtures/urls.tsv").writeText(rows)
        captures.forEach { (n, body) -> File(d, "fixtures/$n").writeText(body) }
        return d
    }

    private fun loader(vararg dirs: File) = URLClassLoader(dirs.map { it.toURI().toURL() }.toTypedArray(), null)

    @Test
    fun theUrlsOfEveryModuleMerge() {
        val base = module("base", "https://a.example/1\ta1.json\nhttps://a.example/2\tcode:503\n", mapOf("a1.json" to "{\"a\":1}"))
        val ext = module("ext", "https://b.example/1\tb1.json\n", mapOf("b1.json" to "{\"b\":1}"))
        val f = Fixtures(loader(base, ext))
        assertEquals("{\"a\":1}", f.get("https://a.example/1", emptyMap()).text)
        assertEquals(503, f.get("https://a.example/2", emptyMap()).code)
        assertEquals("{\"b\":1}", f.get("https://b.example/1", emptyMap()).text)
        assertEquals(404, f.get("https://c.example/1", emptyMap()).code)
        assertEquals(listOf("https://c.example/1"), f.missing)
    }

    @Test
    fun aKeyInTwoModulesFailsNamingBothFiles() {
        val base = module("base", "https://a.example/1\ta1.json\n")
        val ext = module("ext", "https://a.example/1\tother.json\n")
        val e = assertThrows(IllegalStateException::class.java) { Fixtures(loader(base, ext)) }
        val m = e.message!!
        assertTrue(m, m.startsWith("fixture https://a.example/1 in "))
        assertTrue(m, File(base, "fixtures/urls.tsv").toURI().toString() in m)
        assertTrue(m, File(ext, "fixtures/urls.tsv").toURI().toString() in m)
    }

    @Test
    fun aCaptureNameInTwoModulesFailsNamingBoth() {
        val base = module("base", "https://a.example/1\ta1.json\n", mapOf("a1.json" to "{}"))
        val ext = module("ext", "https://b.example/1\tb1.json\n", mapOf("a1.json" to "{}"))
        val e = assertThrows(IllegalStateException::class.java) { Fixtures.load("a1.json", loader(base, ext)) }
        val m = e.message!!
        assertTrue(m, m.startsWith("fixture a1.json in "))
        val built = assertThrows(IllegalStateException::class.java) { Fixtures(loader(base, ext)) }
        assertTrue(built.message, built.message!!.startsWith("fixture a1.json in "))
        assertTrue(m, File(base, "fixtures/a1.json").toURI().toString() in m)
        assertTrue(m, File(ext, "fixtures/a1.json").toURI().toString() in m)
    }
}
