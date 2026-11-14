package net.seilent.funekit.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReleaseTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val rel = File("../tokens/funeos-rel").canonicalFile
    private val pinned = File("../tokens/funeos").canonicalFile

    private val scaleT = listOf(17, 50, 67, 83, 100, 117, 133, 150, 167, 183, 200, 233, 267, 300, 333, 367, 383, 400)
    private val scaleV = listOf(
        "0.476", "0.548", "0.599", "0.646", "0.686", "0.724", "0.762", "0.791", "0.823", "0.845", "0.867", "0.910",
        "0.940", "0.965", "0.982", "0.994", "0.997", "1.0",
    )
    private val baseT = listOf(0, 17, 33, 50, 67, 83, 100, 117, 133, 150, 167, 183, 200, 233, 267, 300, 333, 367, 383, 400, 417, 434)
    private val baseV = listOf(
        "0", "0.158", "0.172", "0.219", "0.264", "0.314", "0.360", "0.408", "0.458", "0.503", "0.553", "0.597", "0.640",
        "0.726", "0.800", "0.868", "0.922", "0.963", "0.978", "0.989", "0.997", "1.0",
    )

    private fun copy(): File = tmp.newFolder().also { rel.copyRecursively(it, overwrite = true) }

    private fun pin(): File = tmp.newFolder().let { d ->
        File(pinned, "SET.sha256").copyTo(File(d, "SET.sha256"))
        File(pinned, "FUNEOS_FROM").copyTo(File(d, "FUNEOS_FROM"))
        File(d, "SET.sha256")
    }

    private fun edit(f: File, from: String, to: String) {
        val t = f.readText()
        assertTrue("${f.name} holds $from", t.contains(from))
        f.writeText(t.replaceFirst(from, to))
    }

    @Test
    fun theVendoredReleaseKeysAndTokenReadAsFuneOsWritesThem() {
        val s = Release.source(rel)
        assertEquals("funeOS release head", s.from)
        assertEquals(null, s.commit)
        assertEquals(null, s.tree)
        assertEquals(listOf("kT2Scale", "kT2Base", "kT2RowsInk", "motion.t2Peek", "kT4hPitch", "kT4hDx", "kT4hScale", "kT4hScaleEnd", "kT4hBase", "motion.t4hPeekDelay"), s.names)
        assertEquals(listOf("motion.t2Peek", "motion.t4hPeekDelay"), s.shadows)
        Release.checkPin(rel, File(pinned, "SET.sha256"))
        val cpp = File(rel, "player/ui/pagemove.cpp").readText()
        val scale = Release.keys(cpp, "kT2Scale")
        assertEquals(scaleT, scale.t)
        assertEquals(scaleV, scale.v)
        val base = Release.keys(cpp, "kT2Base")
        assertEquals(baseT, base.t)
        assertEquals(baseV, base.v)
        assertEquals(17, Release.int(cpp, "kT2RowsInk"))
        assertEquals("200", Release.token(rel, "motion.t2Peek"))
        val kt = Release.relKt(rel)
        assertTrue(kt.contains("    val T2_SCALE = Keys(intArrayOf(${scaleT.joinToString(", ")}), floatArrayOf(${scaleV.joinToString(", ") { "${it}f" }}))\n"))
        assertTrue(kt.contains("    val T2_BASE = Keys(intArrayOf(${baseT.joinToString(", ")}), floatArrayOf(${baseV.joinToString(", ") { "${it}f" }}))\n"))
        assertTrue(kt.contains("    const val T2_ROWS_INK = 17\n"))
        assertTrue(kt.contains("    const val motion_t2Peek = 200f\n"))
        assertEquals("50", Release.token(rel, "motion.t4hPeekDelay"))
        assertTrue(kt.contains("    const val motion_t4hPeekDelay = 50f\n"))
    }

    @Test
    fun theT4hKeysAreVendoredAsWritten() {
        val cpp = File(rel, "player/ui/pagemove.cpp").readText()
        val kt = Release.relKt(rel)
        val want = listOf(
            Triple("kT4hPitch", listOf(0, 17, 50, 67, 83, 100), listOf("0.975", "0.94", "0.928", "0.912", "0.90", "0.885")),
            Triple("kT4hDx", listOf(0, 17, 33, 50, 67, 83, 100), listOf("2.6", "5.3", "6.0", "6.6", "8.6", "9.9", "11.2")),
            Triple(
                "kT4hScale",
                listOf(17, 50, 67, 83, 117, 150, 167, 183, 200, 217, 233),
                listOf("0.93", "0.87", "0.84", "0.79", "0.62", "0.53", "0.47", "0.43", "0.385", "0.34", "0.31"),
            ),
            Triple(
                "kT4hBase",
                listOf(0, 67, 100, 133, 167, 183, 200, 217, 233, 250, 300),
                listOf("0", "0.46", "0.58", "0.73", "0.85", "0.87", "0.90", "0.93", "0.96", "0.98", "1.0"),
            ),
        )
        for ((name, ts, vs) in want) {
            val k = Release.keys(cpp, name)
            assertEquals(name, ts, k.t)
            assertEquals(name, vs, k.v)
            val line = "    val ${Release.constName(name)} = Keys(intArrayOf(${ts.joinToString(", ")}), floatArrayOf(${vs.joinToString(", ") { "${it}f" }}))\n"
            assertTrue(line, kt.contains(line))
        }
        assertEquals(listOf("T4H_PITCH", "T4H_DX", "T4H_SCALE", "T4H_BASE"), want.map { Release.constName(it.first) })
        assertEquals(300, Release.int(cpp, "kT4hScaleEnd"))
        assertTrue(kt.contains("    const val T4H_SCALE_END = 300\n"))
    }

    @Test
    fun aNamedArrayHoldingAKeyOfAnotherShapeFails() {
        val text = "constexpr Key kT2Scale[] = {{17, 0.476}, {50, F(0.548)}};\n"
        assertThrows(GenException::class.java) { Release.keys(text, "kT2Scale") }
        assertEquals(listOf(17, 50), Release.keys("constexpr Key kT2Scale[] = {{17, F(0.476)}, {50, 0}};\n", "kT2Scale").t)
    }

    @Test
    fun aNameMissingOrDeclaredTwiceFails() {
        val once = "constexpr int kT2RowsInk = 17, kT2nPlugInk = 150;\n"
        assertEquals(150, Release.int(once, "kT2nPlugInk"))
        assertThrows(GenException::class.java) { Release.int(once + "constexpr int kT2RowsInk = 18;\n", "kT2RowsInk") }
        assertThrows(GenException::class.java) { Release.int(once, "kT3RowsInk") }
        assertThrows(GenException::class.java) { Release.keys("constexpr Key kT2Base[] = {{0, 0}};\n", "kT2Scale") }
        val both = "constexpr Key kT4hScale[] = {{17, F(0.93)}, {233, F(0.31)}};\nconstexpr int kT4hScaleEnd = 300;\n"
        assertEquals(listOf(17, 233), Release.keys(both, "kT4hScale").t)
        assertEquals(300, Release.int(both, "kT4hScaleEnd"))
    }

    @Test
    fun aNamedTokenEditedInOneBoardFileFails() {
        val d = copy()
        edit(File(d, "player/ui/tokens-r1.txt"), "motion.t2Peek 200 ms", "motion.t2Peek 201 ms")
        val e = assertThrows(GenException::class.java) { Release.relKt(d) }
        assertTrue(e.message!!.contains("motion.t2Peek"))
    }

    @Test
    fun theReleasePinFollowsEveryVendoredByteAndThePinnedSetHash() {
        val d = copy()
        Release.checkPin(d, pin())
        edit(File(d, "player/ui/pagemove.cpp"), "{17, F(0.476)}", "{17, F(0.477)}")
        assertThrows(GenException::class.java) { Release.checkPin(d, pin()) }
        val clean = copy()
        val p = pin()
        edit(p, "00f1555", "10f1555")
        assertThrows(GenException::class.java) { Release.checkPin(clean, p) }
        val q = pin()
        File(q.parentFile, "FUNEOS_FROM").writeText("funeOS release head\n")
        Release.checkPin(clean, q)
        File(clean, "player/ui/extra.txt").writeText("x\n")
        assertThrows(GenException::class.java) { Release.checkPin(clean, pin()) }
    }
}
