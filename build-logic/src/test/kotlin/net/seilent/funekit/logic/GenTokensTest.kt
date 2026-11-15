package net.seilent.funekit.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GenTokensTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val vendored = File("../tokens/funeos").canonicalFile
    private val old = File("../tokens/funeos-old").canonicalFile

    private val rows = listOf(
        listOf("u", "1.0", "2.0", "3.0", "px_per_u"),
        listOf("canvasH", "320", "400", "426.66669", "u"),
        listOf("prop", "32", "40", "42.66667", "u"),
        listOf("anch", "280", "360", "386.66667", "u"),
        listOf("px", "8", "16", "24", "px"),
        listOf("minTextPx", "10", "16", "24", "px"),
        listOf("cons", "58", "63", "63", "u"),
        listOf("lift", "210.5", "266.6", "293.2", "u"),
        listOf("n", "1", "3", "3", "count"),
        listOf("motion.npPace", "1", "2", "2", "count"),
        listOf("saver.aheadMs", "9.6", "51", "11.5", "ms"),
        listOf("art", "91.5", "149.4", "152.7", "u"),
        listOf("same", "16", "16", "16", "u"),
        listOf("color.bg", "#111111", "#111111", "#111111", "rgb"),
    )

    private fun dir(rs: List<List<String>> = rows, ap80: List<String> = rs.map { it[0] }): File = tmp.newFolder().also { root ->
        val ui = File(root, "player/ui").also { it.mkdirs() }
        for ((col, f) in listOf(1 to "tokens-v1.txt", 2 to "tokens-r1.txt", 3 to "tokens-a105.txt"))
            File(ui, f).writeText(rs.joinToString("") { "${it[0]} ${it[col]} ${it[4]}\n" })
        File(ui, "tokens.txt").writeText(ap80.joinToString("") { n -> "$n ${rs.first { it[0] == n }[1]} u\n" })
    }

    private fun fitFile(root: File) = File(root, "fit.txt").also { it.writeText(Gen.fitText(root)) }

    private fun bridged(now: List<List<String>>, lines: Map<String, String>): BridgeRule {
        val was = dir()
        val b = File(was, "bridge").also { it.mkdirs() }
        for ((f, text) in lines) File(b, f).writeText(text)
        return Bridge.rule(dir(now), was)
    }

    @Test
    fun eachClassIsAssignedFromThePrintedValues() {
        val c = Gen.classes(dir())
        assertEquals(
            mapOf(
                "u" to "ppu", "canvasH" to "h", "prop" to "hr", "anch" to "h", "px" to "ppu", "cons" to "v1c",
                "lift" to "v1h", "n" to "step", "motion.npPace" to "step", "saver.aheadMs" to "x", "art" to "free",
                "minTextPx" to "ppuTop",
            ),
            c,
        )
    }

    @Test
    fun theToleranceIsHalfTheLastPrintedDigit() {
        val near = rows.map { if (it[0] == "prop") listOf("prop", "32.0", "40", "42.7", "u") else it }
        assertEquals("hr", Gen.classes(dir(near))["prop"])
        val far = rows.map { if (it[0] == "prop") listOf("prop", "32.0", "40", "42.8", "u") else it }
        assertEquals("free", Gen.classes(dir(far))["prop"])
    }

    @Test
    fun tokHoldsOnlyTokensEqualOnTheThreeBoards() {
        val d = dir()
        val tok = Gen.tokKt(d)
        assertTrue(tok.contains("    const val same = 16f\n"))
        assertTrue(tok.contains("    const val color_bg = 0xFF111111L\n"))
        assertFalse(tok.contains("const val prop"))
        Gen.checkTok(d, tok)
        val e = assertThrows(GenException::class.java) { Gen.checkTok(d, tok.replace("same = 16f", "same = 17f")) }
        assertTrue(e.message!!.contains("same"))
    }

    @Test
    fun tokBoardHasTheThreeBoardsAndNoRendererTokens() {
        val d = dir()
        val kt = Gen.tokBoardKt(d, fitFile(d))
        assertTrue(kt.contains("    object a105 : Board() {\n"))
        assertTrue(kt.contains("        override val canvasH: Float = 426.66669f\n"))
        assertTrue(kt.contains("        override val art: Float = 149.4f\n"))
        assertFalse(kt.contains("saver_aheadMs"))
        assertFalse(kt.contains("ap80"))
    }

    @Test
    fun tokFitEmitsOneFormPerClass() {
        val d = dir()
        val kt = Gen.tokFitKt(d, fitFile(d))
        val lin = { n: String -> "fitLo.$n + (fitHi.$n - fitLo.$n) * fitT" }
        assertTrue(kt.contains("        override val prop: Float = if (fitAbove) TokBoard.a105.prop * fitH / TokBoard.a105.canvasH else ${lin("prop")}\n"))
        assertTrue(kt.contains("        override val anch: Float = if (fitAbove) TokBoard.a105.anch + (fitH - TokBoard.a105.canvasH) else ${lin("anch")}\n"))
        assertTrue(kt.contains("        override val lift: Float = if (fitAbove) TokBoard.a105.lift + (fitH - TokBoard.a105.canvasH) else ${lin("lift")}\n"))
        assertTrue(kt.contains("        override val cons: Float = if (fitAbove) TokBoard.a105.cons else ${lin("cons")}\n"))
        assertTrue(kt.contains("        override val art: Float = fitFree?.art ?: (${lin("art")})\n"))
        assertTrue(kt.contains("    fun at(h: Float, ppu: Float, free: TokBoard.Board? = null): TokBoard.Board {\n"))
        assertTrue(kt.contains("        if (snap != null) return At(snap, snap, 0f, snap, ppu, h, false, free)\n"))
        assertTrue(kt.contains("        override val px: Float = TokBoard.v1.px / TokBoard.v1.u * fitPpu\n"))
        assertTrue(kt.contains("        override val n: Float = fitStep.n\n"))
        assertTrue(kt.contains("        override val motion_npPace: Float = fitStep.motion_npPace\n"))
        assertTrue(
            kt.contains(
                "        override val minTextPx: Float = if (fitLo === TokBoard.v1 && fitHi === TokBoard.v1 && fitPpu == TokBoard.v1.u) " +
                    "TokBoard.v1.minTextPx else TokBoard.r1.minTextPx / TokBoard.r1.u * fitPpu\n",
            ),
        )
        assertFalse(kt.contains("budget"))
        assertFalse(kt.contains("saver_aheadMs"))
        Gen.checkFit(d, fitFile(d))
    }

    @Test
    fun oldIsTheTokFormUnderItsOwnName() {
        val d = dir()
        val kt = Gen.tokKt(d, "Old")
        assertTrue(kt.startsWith("package ${Gen.pkg}\n\nobject Old {\n"))
        assertTrue(kt.contains("    const val same = 16f\n"))
        assertTrue(kt.contains("    const val color_bg = 0xFF111111L\n"))
        assertFalse(kt.contains("const val prop"))
        assertEquals(Gen.tokKt(d).replace("object Tok {", "object Old {"), kt)
    }

    @Test
    fun oldBoardHoldsEveryBoardTokenButTheRendererOnesAndReadsNoFitFile() {
        val d = dir()
        val kt = Gen.oldBoardKt(d)
        assertFalse(File(d, "fit.txt").exists())
        assertTrue(kt.contains("object OldBoard {\n    abstract class Board {\n"))
        assertTrue(kt.contains("    object r1 : Board() {\n"))
        assertTrue(kt.contains("        override val motion_npPace: Float = 2f\n"))
        assertTrue(kt.contains("        override val art: Float = 149.4f\n"))
        assertFalse(kt.contains("saver_aheadMs"))
        assertFalse(kt.contains("val same"))
        assertEquals(Gen.tokBoardKt(d, fitFile(d)).replace("object TokBoard {", "object OldBoard {"), kt)
    }

    @Test
    fun theBridgeReadsItsLinesAndNamesTheChangedTokens() {
        val equal = bridged(rows, mapOf("c2.txt" to "old same\n", "c3.txt" to "new art\nold color.bg\n"))
        assertEquals(setOf("same", "color_bg"), equal.old)
        assertEquals(setOf("art"), equal.new)
        assertEquals(emptySet<String>(), equal.changed)
        val moved = bridged(rows.map { if (it[0] == "art") listOf("art", "91.5", "149.4", "160", "u") else it }, mapOf("c3.txt" to "new art\n"))
        assertEquals(setOf("art"), moved.changed)
        assertFalse(moved.unlisted("art"))
        val removed = bridged(rows.filter { it[0] != "same" }, mapOf("c2.txt" to "old same\n"))
        assertEquals(setOf("same"), removed.old)
    }

    @Test
    fun aBridgeLineInTwoFilesInNeitherSetOrNotChangedFailsGen() {
        fun fails(now: List<List<String>>, lines: Map<String, String>, what: String) {
            val e = assertThrows(GenException::class.java) { bridged(now, lines) }
            assertTrue(e.message!!, e.message!!.contains(what))
        }
        val removed = rows.filter { it[0] != "same" }
        fails(rows, mapOf("c2.txt" to "old same\n", "c3.txt" to "old same\n"), "same is in c2.txt:1 and c3.txt:1")
        fails(rows, mapOf("c2.txt" to "old gone\n"), "names gone, which neither vendored set has")
        fails(removed, mapOf("c2.txt" to "new same\n"), "marks the removed token same new")
        fails(removed, mapOf("c2.txt" to "old art\n"), "lists art, which is not changed")
        val valued = rows.map { if (it[0] == "art") listOf("art", "91.5", "149.4", "160", "u") else it }
        fails(valued, mapOf("c2.txt" to "old same\n"), "lists same, which is not changed")
        fails(rows, mapOf("notes.txt" to "old same\n"), "is not a c<n>.txt file")
        fails(rows, mapOf("c2.txt" to "keep same\n"), "needs old or new and a token name")
    }

    @Test
    fun theOldSetKeepsItsOwnPinApartFromTheVendoredSet() {
        Gen.checkPin(old, File(old, "SET.sha256"), "checkOldPin")
        Gen.checkSource(vendored)
        assertNotEquals(Gen.setHash(vendored), Gen.setHash(old))
        assertEquals(Gen.source(vendored).set, Gen.setHash(vendored))
        assertNotEquals(Gen.source(vendored).set, File(old, "SET.sha256").readText().trim())
        val e = assertThrows(GenException::class.java) { Gen.checkPin(dir(), File(old, "SET.sha256"), "checkOldPin") }
        assertTrue(e.message!!.startsWith("checkOldPin: "))
    }

    @Test
    fun aFitFileThatDiffersFromTheValuesFails() {
        val d = dir()
        val f = fitFile(d)
        f.writeText(f.readText().replace("art free", "art hr"))
        val e = assertThrows(GenException::class.java) { Gen.checkFit(d, f) }
        assertTrue(e.message!!.contains("art"))
    }

    @Test
    fun aBoardTokenNamedLikeATokFitParameterFails() {
        for (name in listOf("fitH", "fitFree")) {
            val clash = rows + listOf(listOf(name, "1", "2", "3", "u"))
            assertThrows(GenException::class.java) { Gen.tokens(dir(clash)) }
        }
    }

    @Test
    fun aNameMissingOrMovedInTheAp80FileFails() {
        val names = rows.map { it[0] }
        assertThrows(GenException::class.java) { Gen.tokens(dir(ap80 = names.drop(1))) }
        assertThrows(GenException::class.java) { Gen.tokens(dir(ap80 = names.reversed())) }
    }

    @Test
    fun theSetHashFollowsEveryByte() {
        val d = dir()
        val a = Gen.setHash(d)
        val f = File(d, "player/ui/tokens-r1.txt")
        f.writeText(f.readText().replace("art 149.4", "art 149.5"))
        assertNotEquals(a, Gen.setHash(d))
        val pin = File(d, "SET.sha256").also { it.writeText("$a\n") }
        assertThrows(GenException::class.java) { Gen.checkPin(d, pin) }
    }

    @Test
    fun glyphRowsSplitOverLinesAreJoined() {
        val d = tmp.newFolder()
        File(d, "player/gfx").mkdirs()
        File(d, "player/gfx/glyph.cpp").writeText(
            "constexpr int kBox = 32;\nconstexpr int kStroke = 4;\nconst GlyphPath kGlyphs[] = {\n" +
                "    {\"play\", true, \"M 9 5 L 27 16 L 9 27 Z\"},\n" +
                "    {\"micBase\", true,\n     \"M 8 12.6 V \"\n     \"25.3 Z\"},\n};\n",
        )
        val g = Gen.glyphs(d)
        assertEquals(32, g.box)
        assertEquals(4, g.stroke)
        assertEquals(listOf("play", "micBase"), g.list.map { it.name })
        assertEquals("M 8 12.6 V 25.3 Z", g.list[1].path)
        assertTrue(Gen.glyphsKt(d).contains("    val micBase = Glyph(\"micBase\", true, \"M 8 12.6 V 25.3 Z\")\n"))
    }

    @Test
    fun glyphDrawablesCarryTheRowsAndTheRingTokens() {
        val res = Gen.glyphDrawables(vendored)
        assertEquals(
            listOf(
                "drawable/fp_glyph_play.xml", "drawable/fp_glyph_pause.xml", "drawable/fp_glyph_next.xml",
                "drawable/fp_glyph_previous.xml", "drawable/fp_ring.xml", "values/fp_colors.xml",
            ),
            res.keys.toList(),
        )
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:width=\"32dp\"\n    android:height=\"32dp\"\n" +
                "    android:viewportWidth=\"32\"\n    android:viewportHeight=\"32\">\n" +
                "    <path\n        android:fillColor=\"#FFFFFFFF\"\n" +
                "        android:pathData=\"M 9 5 L 27 16 L 9 27 Z\" />\n</vector>\n",
            res["drawable/fp_glyph_play.xml"],
        )
        val ring = res.getValue("drawable/fp_ring.xml")
        assertTrue(ring.contains("    android:viewportWidth=\"29.7\"\n    android:viewportHeight=\"29.7\">\n"))
        assertTrue(ring.contains("        android:strokeColor=\"#FFFFFFFF\"\n        android:strokeWidth=\"2.5\"\n"))
        assertTrue(ring.contains("android:pathData=\"M 14.85 1.25 A 13.6 13.6 0 1 1 14.85 28.45 A 13.6 13.6 0 1 1 14.85 1.25 Z\""))
        assertFalse(ring.contains("fillColor"))
        val colors = res.getValue("values/fp_colors.xml")
        assertTrue(colors.contains("    <color name=\"fp_color_bg\">#FF111111</color>\n"))
        assertTrue(colors.contains("    <color name=\"fp_color_tile\">#FF202020</color>\n"))
        val d = tmp.newFolder()
        File(d, "player/gfx").mkdirs()
        File(d, "player/gfx/glyph.cpp").writeText(
            "constexpr int kBox = 32;\nconstexpr int kStroke = 4;\nconst GlyphPath kGlyphs[] = {\n" +
                "    {\"play\", false, \"M 9 5 L 27 16 L 9 27 Z\"},\n};\n",
        )
        val e = assertThrows(GenException::class.java) { Gen.glyphDrawables(d) }
        assertEquals("res: glyph play is not filled", e.message)
    }

    @Test
    fun theVendoredSetMatchesItsPinsAndFitFile() {
        Gen.checkSource(vendored)
        Gen.checkFit(vendored, File(vendored, "../fit.txt"))
        val counts = Gen.classes(vendored).values.groupingBy { it }.eachCount()
        assertEquals(
            mapOf("hr" to 353, "v1c" to 37, "free" to 28, "h" to 9, "step" to 7, "x" to 4, "v1h" to 2, "ppu" to 1, "ppuTop" to 1),
            counts,
        )
        val free = setOf(
            "page.listClipDrop", "npArtistY", "npAlbumY", "npFromBaseline", "npArtTop", "npArt", "lockBtGlyph", "kb.keyH",
            "overlayAnchorScale", "vol.textY", "motion.t7EyeY", "motion.saverB3In", "motion.saverD3In", "motion.saverF5In",
            "saverA6Across", "saverA7Across", "saverA8Across", "saverB6Across", "saverF4Across", "saverK3Across",
            "saverR4Across", "saverJ6Across", "saverL5Across", "npSeekBandX1", "npSeekBandBottom", "large.npLyricPitch",
            "large.npNextPitch", "large.npAlbumPitch",
        )
        assertEquals(free, Gen.classes(vendored).filterValues { it == "free" }.keys)
        assertEquals(setOf("u"), Gen.classes(vendored).filterValues { it == "ppu" }.keys)
        assertEquals(setOf("minTextPx"), Gen.classes(vendored).filterValues { it == "ppuTop" }.keys)
        assertEquals(1659, Gen.tokens(vendored).names.size)
        assertEquals(442, Gen.tokens(vendored).board.size)
        assertEquals(28, Gen.glyphs(vendored).list.size)
        assertEquals("funeOS v0.4.2", Gen.source(vendored).from)
        assertEquals(null, Gen.source(vendored).commit)
    }

    @Test
    fun theReleaseTokensAreUniformAtTheVendoredContent() {
        val kt = Gen.tokKt(vendored)
        assertTrue(kt.contains("    const val motion_t2Peek = 200f\n"))
        assertTrue(kt.contains("    const val motion_t4hPeekDelay = 50f\n"))
    }

    @Test
    fun aSourceThatMissesAByteAFileOrTheSetFails() {
        fun copy(): File = tmp.newFolder().also { vendored.copyRecursively(it, overwrite = true) }
        Gen.checkSource(copy())
        val edited = copy()
        val glyph = File(edited, "player/gfx/glyph.cpp")
        glyph.writeText(glyph.readText().replaceFirst("kBox", "kBoX"))
        assertTrue(assertThrows(GenException::class.java) { Gen.checkSource(edited) }.message!!.contains("player/gfx/glyph.cpp hashes to"))
        val loose = copy()
        File(loose, "player/ui/extra.txt").writeText("x\n")
        assertTrue(assertThrows(GenException::class.java) { Gen.checkSource(loose) }.message!!.contains("with no SOURCE line: [player/ui/extra.txt]"))
        val set = copy()
        val src = File(set, "SOURCE")
        src.writeText(src.readText().replace(Regex("set [0-9a-f]{64}"), "set " + "0".repeat(64)))
        assertTrue(assertThrows(GenException::class.java) { Gen.checkSource(set) }.message!!.contains("SOURCE pins " + "0".repeat(64)))
        val committed = copy()
        File(committed, "SOURCE").appendText("commit " + "a".repeat(40) + "\n")
        Gen.checkSource(committed)
        File(committed, "SOURCE").appendText("tree " + "b".repeat(40) + "\n")
        assertTrue(assertThrows(GenException::class.java) { Gen.checkSource(committed) }.message!!.contains("is not a from, commit, set or file line"))
    }
}
