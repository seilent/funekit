package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoSynchronizedListTest {
    @Test
    fun noTestSourceOrFixtureCallsCollectionsSynchronizedList() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
        assertNotNull("no settings.gradle.kts above ${System.getProperty("user.dir")}", root)
        val banned = "synchronized" + "List("
        val sources = root!!.listFiles { f -> f.isDirectory }!!.flatMap { m ->
            listOf("src/test/kotlin", "src/testFixtures/kotlin").flatMap { d ->
                File(m, d).walkTopDown().filter { it.isFile && it.extension == "kt" }.map { m to it }.toList()
            }
        }
        assertTrue("no test fixture sources under $root", sources.any { "testFixtures" in it.second.path })
        val hits = sources.filter { banned in it.second.readText() }.map { "${it.first.name}/${it.second.name}" }
        assertEquals(emptyList<String>(), hits.sorted())
    }
}
