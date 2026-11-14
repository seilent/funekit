package net.seilent.funekit.logic

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KitsetTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val script = File("../tools/kitset.sh").canonicalFile

    private fun run(vararg args: String): String {
        val p = ProcessBuilder(listOf("bash", script.path) + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        assertEquals(out, 0, p.waitFor())
        return out
    }

    private fun tree(): File {
        val d = tmp.newFolder("kit")
        fun put(path: String, text: String) = File(d, path).also { it.parentFile.mkdirs() }.writeText(text)
        put("a/b/one.txt", "x\n")
        put("a/two.txt", "y\n")
        put("sp ace.txt", "z")
        put("B.txt", "B\n")
        put("build/no", "n\n")
        put("a/build/no", "n\n")
        put(".git/no", "n\n")
        put(".gitmodules", "n\n")
        put("a/local.properties", "n\n")
        put("a/.gradle/no", "n\n")
        put("a/.kotlin/no", "n\n")
        return d
    }

    @Test
    fun theListingIsEveryKitFileInByteOrderAndTheSetIsItsHash() {
        val d = tree()
        assertEquals(
            listOf(
                "c0cde77fa8fef97d476c10aad3d2d54fcc2f336140d073651c2dcccf1e379fd6  B.txt",
                "73cb3858a687a8494ca3323053016282f3dad39d42cf62ca4e79dda2aac7d9ac  a/b/one.txt",
                "3bb2abb69ebb27fbfe63c7639624c6ec5e331b841a5bc8c3ebc10b9285e90877  a/two.txt",
                "594e519ae499312b29433b7dd8a97ff068defcba9755b6d5d00e84c524d67b06  sp ace.txt",
            ),
            run(d.path).lines().filter { it.isNotEmpty() },
        )
        assertEquals("87ed841fc934909282e2a2f41909dc6ae75c5a0abfeef8f389a3bc5f7429396b", run("--set", d.path).trim())
    }

    @Test
    fun oneChangedByteMovesTheSet() {
        val d = tree()
        File(d, "a/two.txt").writeText("Y\n")
        assertEquals(false, run("--set", d.path).trim() == "87ed841fc934909282e2a2f41909dc6ae75c5a0abfeef8f389a3bc5f7429396b")
        File(d, "a/two.txt").writeText("y\n")
        File(d, "build/no").writeText("changed\n")
        assertEquals("87ed841fc934909282e2a2f41909dc6ae75c5a0abfeef8f389a3bc5f7429396b", run("--set", d.path).trim())
    }

    @Test
    fun aPinNeedsItsCommitAndSetLines() {
        val d = tmp.newFolder("pin")
        val f = File(d, "kit.pin")
        f.writeText("commit 0123456789abcdef0123456789abcdef01234567\nset 87ed841fc934909282e2a2f41909dc6ae75c5a0abfeef8f389a3bc5f7429396b\n")
        val p = KitPin.read(f)
        assertEquals("87ed841fc934909282e2a2f41909dc6ae75c5a0abfeef8f389a3bc5f7429396b", p.set)
        f.writeText("set 87ed841fc934909282e2a2f41909dc6ae75c5a0abfeef8f389a3bc5f7429396b\n")
        org.junit.Assert.assertThrows(GenException::class.java) { KitPin.read(f) }
    }
}
