package net.seilent.funekit.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

class MovesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val vendored = File("../tokens/funeos").canonicalFile
    private val decls = Moves.read(vendored).associateBy { it.name }

    private fun table(name: String) = decls.getValue(name) as Moves.Decl.Table

    private fun cpp(v: String): Long {
        val d = v.toDouble() * 65536 + if (v.toDouble() < 0) -0.5 else 0.5
        return d.toLong()
    }

    private fun fx(f: Float): Long = BigDecimal(f.toString()).multiply(BigDecimal(65536)).setScale(0, RoundingMode.HALF_UP).toLong()

    private fun ratio(q: Int): Float = q / 65536f

    private fun keys(text: String): Pair<List<Int>, List<String>> {
        val ks = Regex("\\{(-?[0-9]+), (-?[0-9.]+)}").findAll(text).toList()
        return ks.map { it.groupValues[1].toInt() } to ks.map { it.groupValues[2] }
    }

    private fun assertTable(name: String, typed: String) {
        val (t, v) = keys(typed)
        val got = table(name)
        assertEquals(name, t, got.t)
        assertEquals(name, v, got.v)
    }

    @Test
    fun theReleaseTablesReadAsFuneOsWritesThem() {
        assertTable(
            "kT2Scale",
            "{17, 0.476} {50, 0.548} {67, 0.599} {83, 0.646} {100, 0.686} {117, 0.724} {133, 0.762} {150, 0.791} " +
                "{167, 0.823} {183, 0.845} {200, 0.867} {233, 0.910} {267, 0.940} {300, 0.965} {333, 0.982} {367, 0.994} " +
                "{383, 0.997} {400, 1.0}",
        )
        assertTable(
            "kT2Base",
            "{0, 0} {17, 0.158} {33, 0.172} {50, 0.219} {67, 0.264} {83, 0.314} {100, 0.360} {117, 0.408} {133, 0.458} " +
                "{150, 0.503} {167, 0.553} {183, 0.597} {200, 0.640} {233, 0.726} {267, 0.800} {300, 0.868} {333, 0.922} " +
                "{367, 0.963} {383, 0.978} {400, 0.989} {417, 0.997} {434, 1.0}",
        )
        assertTable("kT4hPitch", "{0, 0.975} {17, 0.94} {50, 0.928} {67, 0.912} {83, 0.90} {100, 0.885}")
        assertTable("kT4hDx", "{0, 2.6} {17, 5.3} {33, 6.0} {50, 6.6} {67, 8.6} {83, 9.9} {100, 11.2}")
        assertTable(
            "kT4hScale",
            "{17, 0.93} {50, 0.87} {67, 0.84} {83, 0.79} {117, 0.62} {150, 0.53} {167, 0.47} {183, 0.43} {200, 0.385} " +
                "{217, 0.34} {233, 0.31}",
        )
        assertTable(
            "kT4hBase",
            "{0, 0} {67, 0.46} {100, 0.58} {133, 0.73} {167, 0.85} {183, 0.87} {200, 0.90} {217, 0.93} {233, 0.96} " +
                "{250, 0.98} {300, 1.0}",
        )
        assertEquals(17, (decls.getValue("kT2RowsInk") as Moves.Decl.Whole).value)
        assertEquals(300, (decls.getValue("kT4hScaleEnd") as Moves.Decl.Whole).value)
    }

    @Test
    fun eachFileGivesEveryFormItHolds() {
        assertEquals("pagemove.cpp", table("kT2Face").file)
        assertTable("kT2Face", "{0, 1.0} {17, 0}")
        assertEquals("pagemove.h", table("kT4RowDy").file)
        assertTable("kT4RowDy", "{0, 7.9} {33, 21.8} {50, 27.8} {67, 33.7} {83, 39.0} {100, 43.7} {117, 47.6}")
        assertEquals(17, (decls.getValue("kTail") as Moves.Decl.Whole).value)
        assertEquals(150, (decls.getValue("kT2nPlugInk") as Moves.Decl.Whole).value)
        assertEquals(133, (decls.getValue("kT4nItems") as Moves.Decl.Whole).value)
        assertEquals("46.4", (decls.getValue("kT3RowWordDrop") as Moves.Decl.Fixed).value)
        assertEquals("-55.8", (decls.getValue("kT3OldRise") as Moves.Decl.Fixed).value)
        assertEquals("0.4", (decls.getValue("kT3OldHeaderX") as Moves.Decl.Fixed).value)
        assertEquals("0.498", (decls.getValue("kT4nInEye") as Moves.Decl.Fixed).value)
        assertEquals(64487, (decls.getValue("kPopPitch0") as Moves.Decl.Ratio).q)
        assertEquals(517734, (decls.getValue("kPopDrop0") as Moves.Decl.Ratio).q)
        assertEquals(66, decls.values.count { it is Moves.Decl.Table && it.file == "pagemove.cpp" })
        assertEquals(3, decls.values.count { it is Moves.Decl.Table && it.file == "pagemove.h" })
        for (n in listOf("F", "key_fx", "nkeys", "kArriveGroup", "kLeaveGroup", "from", "delay", "leave_fade")) assertTrue(n, n !in decls)
    }

    @Test
    fun everyKeyIsTheCppFixedPointValue() {
        for (d in decls.values) {
            when (d) {
                is Moves.Decl.Table -> for (v in d.v) assertEquals("${d.name} $v", cpp(v), fx(v.toFloat()))
                is Moves.Decl.Fixed -> assertEquals(d.name, cpp(d.value), fx(d.value.toFloat()))
                is Moves.Decl.Ratio -> assertEquals(d.name, d.q.toLong(), fx(ratio(d.q)))
                is Moves.Decl.Whole -> {}
            }
        }
    }

    @Test
    fun theRowPopStartsOnItsTwoFixedPointScalars() {
        val pitch = table("kT4RowPitch")
        val dy = table("kT4RowDy")
        assertEquals(64487L, fx(pitch.v[0].toFloat()))
        assertEquals(fx(pitch.v[0].toFloat()), fx(ratio((decls.getValue("kPopPitch0") as Moves.Decl.Ratio).q)))
        assertEquals(517734L, fx(dy.v[0].toFloat()))
        assertEquals(fx(dy.v[0].toFloat()), fx(ratio((decls.getValue("kPopDrop0") as Moves.Decl.Ratio).q)))
        assertEquals(pitch.t, dy.t)
    }

    @Test
    fun theGeneratedObjectNamesEachDeclarationAsCppDoes() {
        val kt = Moves.movesKt(vendored)
        assertTrue(kt.startsWith("package ${Gen.pkg}\n\nclass Keys(val t: IntArray, val v: FloatArray)\n\nobject Moves {\n"))
        assertTrue(kt.contains("    val kT4hDx = Keys(intArrayOf(0, 17, 33, 50, 67, 83, 100), floatArrayOf(2.6f, 5.3f, 6.0f, 6.6f, 8.6f, 9.9f, 11.2f))\n"))
        assertTrue(kt.contains("    val kT2Face = Keys(intArrayOf(0, 17), floatArrayOf(1.0f, 0f))\n"))
        assertTrue(kt.contains("    const val kT2RowsInk = 17\n"))
        assertTrue(kt.contains("    const val kT4hScaleEnd = 300\n"))
        assertTrue(kt.contains("    const val kT3OldRise = -55.8f\n"))
        assertTrue(kt.contains("    const val kPopPitch0 = 64487f / 65536f\n"))
    }

    private fun parse(text: String) = Moves.parse("fixture.cpp", text).associateBy { it.name }

    private fun fails(text: String, what: String) {
        val e = assertThrows(GenException::class.java) { Moves.parse("fixture.cpp", text) }
        assertTrue(e.message!!, e.message!!.contains(what))
    }

    @Test
    fun theSkippedKindsGiveNothing() {
        val text = """
            #include "pagemove.h"
            namespace fd {
            namespace {
            int ms(int32_t t) { return t >> kFxBits; }
            constexpr fx F(double v) { return (fx)(v * 65536 + (v < 0 ? -0.5 : 0.5)); }
            template <int N> constexpr int nkeys(const Key (&)[N]) { return N; }
            constexpr int count(int n);
            enum class Kind : uint8_t { A = 1, B };
            struct Leg { fx from = 0, to = 0; int delay = 0; };
            inline constexpr uint64_t kGroup = parts(1, 2);
            static_assert(kA == 1, "a { brace } in a string");
            const char* kName = "kGone";
            // constexpr Key kGone[] = {{0, F(1.0)}};
            /* constexpr int kAlsoGone = 3; */
            constexpr int kKept = 4;
            }
            }
        """.trimIndent()
        val got = parse(text)
        assertEquals(setOf("kKept"), got.keys)
        assertEquals(15, got.getValue("kKept").line)
    }

    @Test
    fun theNumberFormsRead() {
        val got = parse(
            "constexpr Key kA[] = {{0, F(-55.8)}, {17, key_fx(0.5)}, {33, 0},};\n" +
                "constexpr fx kB = F(0.4), kC = key_fx(-2.5), kD = fx_int(984) / 1000;\n" +
                "inline constexpr fx kThird = fx_int(2) / 3;\n" +
                "constexpr int kE = 17, kF = -150;\n",
        )
        val a = got.getValue("kA") as Moves.Decl.Table
        assertEquals(listOf(0, 17, 33), a.t)
        assertEquals(listOf("-55.8", "0.5", "0"), a.v)
        assertEquals("0.4", (got.getValue("kB") as Moves.Decl.Fixed).value)
        assertEquals("-2.5", (got.getValue("kC") as Moves.Decl.Fixed).value)
        assertEquals(64487, (got.getValue("kD") as Moves.Decl.Ratio).q)
        assertEquals(43690, (got.getValue("kThird") as Moves.Decl.Ratio).q)
        assertEquals(-150, (got.getValue("kF") as Moves.Decl.Whole).value)
        assertEquals(3, got.getValue("kThird").line)
    }

    @Test
    fun anyOtherFormOfARecognisedDeclaratorFails() {
        fails("constexpr Key kA[] = {{0, F(1.0), 3}};\n", "fixture.cpp:1: constexpr Key")
        fails("constexpr Key kA[] = {{0, 1.0}};\n", "fixture.cpp:1: constexpr Key")
        fails("constexpr Key kA = {0, F(1.0)};\n", "fixture.cpp:1: constexpr Key")
        fails("\nconstexpr int kA = kB + 1;\n", "fixture.cpp:2: constexpr int")
        fails("constexpr fx kA = F(0.4) * 2;\n", "constexpr fx")
        fails("constexpr fx kA = 0.4;\n", "constexpr fx")
        fails("constexpr fx kA = fx_int(1) / 0;\n", "constexpr fx")
        fails("constexpr Key kA[]{{0, F(1.0)}};\n", "fixture.cpp:1: constexpr Key")
        fails("constexpr int kA{3};\n", "fixture.cpp:1: constexpr int")
        fails("constexpr int kA(3);\n", "fixture.cpp:1: constexpr int")
        fails("constexpr fx kA(F(0.4));\n", "constexpr fx")
    }

    @Test
    fun aNameDefinedTwiceAcrossTheFilesFails() {
        val d = tmp.newFolder().also { File(it, "player/ui").mkdirs() }
        File(d, "player/ui/pagemove.cpp").writeText("constexpr int kA = 1;\n")
        File(d, "player/ui/pagemove.h").writeText("\ninline constexpr int kA = 2;\n")
        val e = assertThrows(GenException::class.java) { Moves.read(d) }
        assertEquals("moves: kA is defined at pagemove.cpp:1 and pagemove.h:2", e.message)
    }
}
