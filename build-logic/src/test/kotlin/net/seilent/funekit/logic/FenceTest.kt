package net.seilent.funekit.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FenceTest {
    private val dir = File(javaClass.getResource("/fence")!!.toURI())

    private fun scan(name: String) = Fence.scan(listOf(File(dir, name)))

    @Test
    fun aQualifiedForbiddenNameFailsWithNoImportOfIt() {
        assertEquals(listOf("Forbidden.kt:6: names net.seilent.funeplayer"), scan("Forbidden.kt"))
    }

    @Test
    fun importsOutsideTheAllowListAndDeniedNamesFail() {
        assertEquals(
            listOf(
                "Imports.kt:3: import androidx.compose.material3.Text is outside the kit allow list",
                "Imports.kt:3: names androidx.compose.material",
                "Imports.kt:4: import com.example.Other is outside the kit allow list",
                "Imports.kt:5: import net.seilent.funecomp.ui.Kit is outside the kit allow list",
                "Imports.kt:5: names net.seilent.funecomp",
            ),
            scan("Imports.kt"),
        )
    }

    @Test
    fun theSeedImportsPass() {
        assertEquals(emptyList<String>(), scan("Clean.kt"))
    }

    @Test
    fun theMadeUpLibraryIsNamedOnlyInItsOwnModule() {
        val f = listOf(File(dir, "Fake.kt"))
        assertEquals(listOf("Fake.kt:3: names net.seilent.funekit.fake"), Fence.scan(f, denied = Fence.deniedIn(":screens")))
        assertEquals(emptyList<String>(), Fence.scan(f, denied = Fence.deniedIn(":fake")))
    }

    @Test
    fun aTestFixtureTakesJunitBesideTheAllowList() {
        assertEquals(listOf("Fixture.kt:3: import org.junit.Test is outside the kit allow list"), scan("Fixture.kt"))
        assertEquals(emptyList<String>(), Fence.scan(listOf(File(dir, "Fixture.kt")), Fence.allowed + Fence.fixtureAllowed))
    }

    @Test
    fun theKitSourcesPassTheFence() {
        val repo = File("..").canonicalFile
        val files = listOf("core", "kit", "sheet").flatMap { m ->
            File(repo, "$m/src/main").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
        }
        assertEquals(true, files.size > 50)
        assertEquals(emptyList<String>(), Fence.scan(files))
    }
}
