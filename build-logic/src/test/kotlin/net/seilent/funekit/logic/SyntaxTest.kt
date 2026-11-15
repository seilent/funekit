package net.seilent.funekit.logic

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class SyntaxTest {
    companion object {
        private lateinit var syntax: Syntax

        @BeforeClass
        @JvmStatic
        fun open() {
            syntax = Syntax()
        }

        @AfterClass
        @JvmStatic
        fun close() {
            syntax.close()
        }
    }

    private val repo = File("..").canonicalFile

    private fun found(body: String, imports: String = "", allowed: Set<Pair<String, String>> = emptySet()) =
        syntax.scan("kit/src/main/kotlin/A.kt", "package a\n$imports\nfun f() {\n$body\n}\n", allowed).map { it.what }

    private fun caught(body: String, what: String, imports: String = "") {
        val f = found(body, imports)
        assertTrue("$imports $body gave $f", f.any { it.startsWith(what) })
    }

    private fun clean(body: String, imports: String = "") = assertEquals("$imports $body", emptyList<String>(), found(body, imports))

    private val rule = BridgeRule(
        setOf("motion_sheet"),
        setOf("motion_jumpIn"),
        setOf("motion_sheet", "motion_jumpIn", "motion_toastOut"),
    )

    private fun bridgeScan(body: String, imports: String = "", path: String = "kit/src/main/kotlin/A.kt", only: Boolean = false, bridge: BridgeRule = rule) =
        syntax.scan(path, "package a\n$imports\nfun f() {\n$body\n}\n", emptySet(), HashSet(), bridge, only).map { it.what }

    @Test
    fun numericLiteralsOtherThanZeroAndOneFail() {
        caught("val a = 16f", "numeric literal 16f")
        caught("val a = 0x10", "numeric literal 0x10")
        caught("val a = 2", "numeric literal 2")
        clean("val a = 0\nval b = 1f\nval c = 0.0\nval d = 1L\nval e = -1")
    }

    @Test
    fun anAllowedLiteralPassesOnlyAtItsConstSite() {
        val text = "package a\nprivate const val FX = 65536f\nval b = 65536f\nfun f() { val c = 65536f }\n"
        val got = syntax.scan("A.kt", text, setOf("FX" to "65536f")).map { it.line }
        assertEquals(listOf(3, 4), got)
        val used = HashSet<Pair<String, String>>()
        syntax.scan("A.kt", text, setOf("FX" to "65536f"), used)
        assertEquals(setOf("FX" to "65536f"), used)
    }

    @Test
    fun unitsFail() {
        caught("val a = x.dp", ".dp")
        caught("val a = x.sp", ".sp")
        caught("val a = x?.dp", ".dp")
        caught("", "Material import", "import androidx.compose.material3.Text")
        caught("", "Material import", "import androidx.compose.material.ripple.rememberRipple")
    }

    @Test
    fun namedColorsFailInEveryForm() {
        caught("val a = Color.White", "Color.White")
        caught("val a = androidx.compose.ui.graphics.Color.White", "Color.White")
        caught("val a = Color.Companion.White", "Color.White")
        caught("val a = Color?.White", "Color.White")
        caught("val a = android.graphics.Color.TRANSPARENT", "Color.TRANSPARENT")
        caught("", "Color import", "import androidx.compose.ui.graphics.Color.Companion.White")
        caught("", "Color import", "import androidx.compose.ui.graphics.Color.Companion.*")
        caught("", "Color import", "import android.graphics.Color.TRANSPARENT")
        caught("", "Color import", "import android.graphics.Color.*")
        caught("", "Color import", "import androidx.compose.ui.graphics.Color as C")
        clean("", "import androidx.compose.ui.graphics.Color")
    }

    @Test
    fun colorValuesComeFromTokens() {
        caught("val a = Color(0xFF000000)", "Color( without Tok")
        caught("val a = Color(red, green, blue)", "Color( without Tok")
        caught("val a = Color(Toks.bg)", "Color( without Tok")
        caught("val a = Color(TokFit.at(h, p).bg)", "Color( without Tok")
        caught("val a = Color.hsv(h, s, v)", "Color.hsv(")
        caught("val a = Color.hsl(h, s, l)", "Color.hsl(")
        caught("val a = android.graphics.Color.parseColor(s)", "Color.parseColor(")
        caught("val a = android.graphics.Color.valueOf(c)", "Color.valueOf(")
        clean("val a = Color(Tok.color_bg)")
        clean("val a = Color(TokBoard.v1.color_bg)")
    }

    @Test
    fun aListedArgbConstPassesOnlyAtItsSite() {
        val text = "package a\nprivate const val WHITE = 0xFFFFFFFFL\nval a = Color(WHITE)\n"
        val listed = setOf("WHITE" to "0xFFFFFFFFL")
        assertEquals(emptyList<String>(), syntax.scan("A.kt", text, listed).map { it.what })
        assertEquals(listOf("numeric literal 0xFFFFFFFFL", "Color( without Tok"), syntax.scan("A.kt", text, emptySet()).map { it.what })
        val other = "package a\nprivate const val WHITE = 0xFFFFFFFFL\nprivate const val GRAY = 0xFF808080L\nval a = Color(WHITE)\nval b = Color(GRAY)\n"
        assertEquals(listOf("numeric literal 0xFF808080L", "Color( without Tok"), syntax.scan("A.kt", other, listed).map { it.what })
        val notColor = "package a\nprivate const val FX = 65536f\nval a = Color(FX)\n"
        assertEquals(listOf("Color( without Tok"), syntax.scan("A.kt", notColor, setOf("FX" to "65536f")).map { it.what })
        val local = "package a\nval WHITE = 0xFFFFFFFFL\nval a = Color(WHITE)\n"
        assertEquals(listOf("numeric literal 0xFFFFFFFFL", "Color( without Tok"), syntax.scan("A.kt", local, listed).map { it.what })
    }

    @Test
    fun aParameterOrLocalShadowingTheListedConstFails() {
        val listed = setOf("WHITE" to "0xFFFFFFFFL")
        val param = "package a\nprivate const val WHITE = 0xFFFFFFFFL\nval a = Color(WHITE)\nfun f(WHITE: Long) = Color(WHITE)\n"
        assertEquals(listOf("Color( without Tok", "Color( without Tok"), syntax.scan("A.kt", param, listed).map { it.what })
        val local = "package a\nprivate const val WHITE = 0xFFFFFFFFL\nfun f(v: Long): Color {\n    val WHITE = v\n    return Color(WHITE)\n}\n"
        assertEquals(listOf("Color( without Tok"), syntax.scan("A.kt", local, listed).map { it.what })
    }

    @Test
    fun theOneAllowedColorImportPassesOnlyWhereListed() {
        val imports = "import android.graphics.Color.TRANSPARENT"
        assertEquals(emptyList<String>(), found("", imports, setOf("import" to "android.graphics.Color.TRANSPARENT")))
        caught("", "Color import", imports)
    }

    @Test
    fun animationsNeedAFullSpecFromTok() {
        caught("val a = tween<Float>(durationMillis = d, easing = e)", "tween(")
        caught("val a = tween<Float>(Tok.motion_scroll.toInt())", "tween(")
        caught("val a = tween<Float>(TokX.motion_scroll.toInt(), easing = e)", "tween(")
        caught("val a = spring<Float>(stiffness = Tok.s)", "spring(")
        caught("val a = spring<Float>(dampingRatio = Tok.d)", "spring(")
        caught("val a = keyframes<Float> { durationMillis = d }", "keyframes(")
        caught("val a by animateFloatAsState(t)", "animateFloatAsState(")
        caught("a.animateTo(t)", "animateTo(")
        caught("a?.animateTo(t)", "animateTo(")
        caught("val a by transition.animateFloat { s -> v }", "animateFloat(")
        caught("animate(0f, t) { v, _ -> x = v }", "animate(")
        caught("animateDecay(0f, v) { x, _ -> y = x }", "animateDecay(")
        caught("a.animateDecay(v, d)", "animateDecay(")
        caught("a.animateDecay(v)", "animateDecay(")
        caught("s.animateScrollTo(v)", "animateScrollTo(")
        caught("s.animateScrollBy(v)", "animateScrollBy(")
        caught("p.animateScrollToPage(i)", "animateScrollToPage(")
        clean("val a = tween<Float>(Tok.motion_scroll.toInt(), easing = e)")
        clean("val a = tween<Float>(Tok.motion_scroll.toInt(), 0, e)")
        clean("val a = spring<Float>(dampingRatio = Tok.d, stiffness = Tok.s)")
        clean("val a = keyframes<Float> { durationMillis = Tok.motion_t.toInt() }")
        clean("val a by animateFloatAsState(t, tween(Tok.motion_scroll.toInt(), easing = e))")
        clean("a.animateTo(t, animationSpec = tween(Tok.motion_scroll.toInt(), easing = e))")
        clean("val a by transition.animateFloat(transitionSpec = { tween(Tok.motion_t.toInt(), easing = e) }) { s -> v }")
        clean("animate(0f, t, 0f, tween(Tok.motion_t.toInt(), easing = e)) { v, _ -> x = v }")
        clean("animateDecay(0f, v, exponentialDecay(TokBoard.v1.k)) { x, _ -> y = x }")
        clean("a.animateDecay(v, exponentialDecay(TokBoard.v1.k))")
        clean("a.animateDecay(v, animationSpec = exponentialDecay(TokBoard.v1.k))")
        clean("s.animateScrollTo(v, tween(Tok.motion_t.toInt(), easing = e))")
        clean("p.animateScrollToPage(i, 0f, tween(Tok.motion_t.toInt(), easing = e))")
    }

    @Test
    fun composeCurvesAndSpringConstantsFail() {
        for (n in listOf("FastOutSlowInEasing", "LinearOutSlowInEasing", "FastOutLinearInEasing", "LinearEasing", "Ease", "EaseInOutCubic", "EaseOutBack"))
            caught("val a = tween<Float>(Tok.motion_t.toInt(), easing = $n)", n)
        caught("val a = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Tok.s)", "Spring.DampingRatioNoBouncy")
        caught("val a = spring<Float>(dampingRatio = Tok.d, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)", "Spring.StiffnessLow")
        caught("", "Spring import", "import androidx.compose.animation.core.Spring.StiffnessLow")
        caught("", "Spring import", "import androidx.compose.animation.core.Spring.*")
        clean("val a = tween<Float>(Tok.motion_t.toInt(), easing = easeOut)")
    }

    @Test
    fun unitTypesAndDpPassOnlyAtTheirListedFunction() {
        val text = "package a\nfun style(px: Float) = TextUnit(px, TextUnitType.Sp)\nfun other(px: Float) = TextUnit(px, TextUnitType.Sp)\n" +
            "fun em(px: Float) = TextUnit(px, TextUnitType.Em)\nfun box(v: Float) = Dp(v)\n"
        val allowed = setOf("style" to "TextUnitType.Sp")
        val used = HashSet<Pair<String, String>>()
        val got = syntax.scan("A.kt", text, allowed, used).map { it.line to it.what }
        assertEquals(listOf(3 to "TextUnitType.Sp", 4 to "TextUnitType.Em", 5 to "Dp("), got)
        assertEquals(allowed, used)
        assertEquals(emptyList<String>(), syntax.scan("A.kt", "package a\nfun box(v: Float) = Dp(v)\n", setOf("box" to "Dp(")).map { it.what })
    }

    @Test
    fun colorIntHelpersFail() {
        caught("val a = s.toColorInt()", "toColorInt")
        caught("val a = ColorUtils.blendARGB(x, y, r)", "ColorUtils")
    }

    @Test
    fun listsAndGridsMustNotScrollThemselves() {
        caught("LazyColumn { }", "LazyColumn without")
        caught("LazyRow(userScrollEnabled = true) { }", "LazyRow without")
        caught("LazyVerticalGrid(columns) { }", "LazyVerticalGrid without")
        caught("LazyHorizontalGrid(rows) { }", "LazyHorizontalGrid without")
        caught("LazyVerticalStaggeredGrid(columns) { }", "LazyVerticalStaggeredGrid without")
        clean("LazyColumn(userScrollEnabled = false) { }")
        clean("LazyVerticalGrid(columns, userScrollEnabled = false) { }")
        caught("val m = Modifier.animateItem()", "animateItem")
        caught("s.animateScrollToItem(i)", "animateScrollToItem")
    }

    @Test
    fun pressesScrollsProjectionAndWindowApisFail() {
        for (n in listOf(
            "clickable", "combinedClickable", "verticalScroll", "horizontalScroll", "scrollable", "draggable",
            "anchoredDraggable", "selectable", "toggleable", "triStateToggleable", "animateContentSize",
        )) caught("val m = Modifier.$n(s)", n)
        caught("val m = Modifier.indication(s, i)", "indication(")
        caught("val m = Modifier.graphicsLayer { cameraDistance = d }", "cameraDistance")
        caught("val m = Modifier.graphicsLayer { rotationX = r }", "rotationX")
        caught("val m = Modifier.graphicsLayer { rotationY = r }", "rotationY")
        caught("AnimatedVisibility(v) { }", "AnimatedVisibility")
        caught("AnimatedContent(v) { }", "AnimatedContent")
        caught("Crossfade(v) { }", "Crossfade")
        caught("HorizontalPager(s) { }", "HorizontalPager")
        caught("Dialog(onDismissRequest = { }) { }", "Dialog")
        caught("Popup { }", "Popup")
    }

    @Test
    fun anOldNameReadsThroughOldAndFailsThroughTokInEveryForm() {
        assertEquals(emptyList<String>(), bridgeScan("val a = Old.motion_sheet\nval b = OldBoard.r1.motion_sheet\nval c = Old::motion_sheet"))
        val tok = listOf("Tok.motion_sheet is bridged: read Old.motion_sheet")
        assertEquals(tok, bridgeScan("val a = Tok.motion_sheet"))
        assertEquals(tok, bridgeScan("val a = net.seilent.funekit.gen.Tok.motion_sheet"))
        assertEquals(tok, bridgeScan("", "import net.seilent.funekit.gen.Tok.motion_sheet"))
        assertEquals(tok, bridgeScan("val a = T.motion_sheet", "import net.seilent.funekit.gen.Tok as T"))
        assertEquals(tok, bridgeScan("val a = Tok::motion_sheet"))
        assertEquals(tok, bridgeScan("val a = TokBoard.a105.motion_sheet"))
        assertEquals(
            listOf("Old is not the receiver of a member read", "motion_sheet is a bridged token read unqualified"),
            bridgeScan("val a = with(Old) { motion_sheet }"),
        )
        assertEquals(listOf("::motion_sheet reads a bridged token unqualified"), bridgeScan("val a = ::motion_sheet"))
    }

    @Test
    fun aPortedNameFailsThroughOldAndAnUnlistedChangedNameFailsEitherWay() {
        assertEquals(emptyList<String>(), bridgeScan("val a = Tok.motion_jumpIn\nval b = Tok.motion_jumpRiseIn"))
        val ported = listOf("Old.motion_jumpIn is ported: read Tok.motion_jumpIn")
        assertEquals(ported, bridgeScan("val a = Old.motion_jumpIn"))
        assertEquals(ported, bridgeScan("val a = O.motion_jumpIn", "import net.seilent.funekit.gen.Old as O"))
        assertEquals(ported, bridgeScan("", "import net.seilent.funekit.gen.Old.motion_jumpIn"))
        assertEquals(ported, bridgeScan("val a = Old::motion_jumpIn"))
        assertEquals(ported, bridgeScan("val a = OldBoard.r1.motion_jumpIn"))
        assertEquals(listOf("Old.motion_toastOut has no old line in a bridge file"), bridgeScan("val a = OldBoard.a105.motion_toastOut"))
        assertEquals(listOf("Tok.motion_toastOut is changed and no bridge file lists it"), bridgeScan("val a = Tok.motion_toastOut"))
        assertEquals(listOf("Old.motion_toastOut has no old line in a bridge file"), bridgeScan("val a = Old.motion_toastOut"))
        assertEquals(listOf("motion_toastOut is a bridged token read unqualified"), bridgeScan("val a = with(Tok) { motion_toastOut }"))
    }

    @Test
    fun oldAndOldBoardAreReadOnlyThroughAMember() {
        assertEquals(
            emptyList<String>(),
            bridgeScan(
                "val a = net.seilent.funekit.gen.Old.motion_sheet\nval b = net.seilent.funekit.gen.OldBoard.r1.motion_sheet\nval c = OldBoard.a105::motion_sheet\nval d = B.v1.motion_sheet",
                "import net.seilent.funekit.gen.Old\nimport net.seilent.funekit.gen.OldBoard\nimport net.seilent.funekit.gen.OldBoard as B",
            ),
        )
        assertEquals(listOf("Old is not the receiver of a member read"), bridgeScan("val a = with(Old) { motion_jumpIn }"))
        assertEquals(listOf("Old is not the receiver of a member read"), bridgeScan("val a = Old"))
        assertEquals(listOf("O is not the receiver of a member read"), bridgeScan("val a = with(O) { motion_jumpIn }", "import net.seilent.funekit.gen.Old as O"))
        assertEquals(listOf("OldBoard.r1 is not the receiver of a member read"), bridgeScan("val a = OldBoard.r1"))
        assertEquals(listOf("OldBoard.r1 is not the receiver of a member read"), bridgeScan("val a = with(OldBoard.r1) { motion_jumpIn }"))
        assertEquals(listOf("OldBoard is not the receiver of a member read"), bridgeScan("val a = OldBoard::motion_jumpIn"))
        assertEquals(listOf("B.r1 is not the receiver of a member read"), bridgeScan("val a = B.r1", "import net.seilent.funekit.gen.OldBoard as B"))
    }

    @Test
    fun anImportOfANestedBoardMemberIsCheckedAndAWholeOldImportFails() {
        assertEquals(emptyList<String>(), bridgeScan("", "import net.seilent.funekit.gen.OldBoard.r1.motion_sheet"))
        assertEquals(listOf("Old.motion_jumpIn is ported: read Tok.motion_jumpIn"), bridgeScan("", "import net.seilent.funekit.gen.OldBoard.r1.motion_jumpIn"))
        assertEquals(listOf("Tok.motion_sheet is bridged: read Old.motion_sheet"), bridgeScan("", "import net.seilent.funekit.gen.TokBoard.a105.motion_sheet"))
        for (fq in listOf("OldBoard.r1", "OldBoard.Board", "Old.motion_sheet.x")) {
            val got = bridgeScan("", "import net.seilent.funekit.gen.$fq")
            assertTrue("$fq gave $got", got.contains("import net.seilent.funekit.gen.$fq reads the old set whole"))
        }
        for (fq in listOf("Old", "OldBoard", "OldBoard.r1")) {
            assertEquals(listOf("import net.seilent.funekit.gen.$fq.* reads the old set whole"), bridgeScan("", "import net.seilent.funekit.gen.$fq.*"))
        }
    }

    @Test
    fun aChangedNameReadThroughAnyReceiverNeedsItsLine() {
        val wide = BridgeRule(
            setOf("motion_sheet"),
            setOf("minTextPx", "vol_textGap"),
            setOf("motion_sheet", "minTextPx", "vol_textGap", "npStatusGap"),
        )
        fun scan(body: String, path: String = "kit/src/main/kotlin/A.kt", only: Boolean = false) = bridgeScan(body, path = path, only = only, bridge = wide)
        assertEquals(listOf("Tok.npStatusGap is changed and no bridge file lists it"), scan("val a = Tok.npStatusGap"))
        assertEquals(listOf("Tok.npStatusGap is changed and no bridge file lists it"), scan("val a = TokBoard.r1.npStatusGap"))
        assertEquals(listOf(".npStatusGap is changed and no bridge file lists it"), scan("val a = s.board.npStatusGap"))
        assertEquals(listOf(".npStatusGap is changed and no bridge file lists it"), scan("val a = s?.board?.npStatusGap"))
        assertEquals(listOf(".npStatusGap is changed and no bridge file lists it"), scan("val a = TokFit.at(h, p).npStatusGap"))
        assertEquals(listOf(".npStatusGap is changed and no bridge file lists it"), scan("val a = b::npStatusGap"))
        assertEquals(
            emptyList<String>(),
            scan("val a = Tok.minTextPx\nval b = TokBoard.v1.minTextPx\nval c = s.board.minTextPx\nval d = TokFit.at(h, p).minTextPx\nval e = b::minTextPx"),
        )
        assertEquals(listOf(".motion_sheet is bridged: read Old.motion_sheet"), scan("val a = s.board.motion_sheet"))
        assertEquals(listOf(".motion_sheet is bridged: read Old.motion_sheet"), scan("val a = TokFit.at(h, p).motion_sheet"))
        val stubs = "kit/src/test/kotlin/net/seilent/funekit/sheet/Stubs.kt"
        assertEquals(
            emptyList<String>(),
            scan("ink.text(Roles.volLabel, \"artist\", Tok.vol_textX, v.vol_textY + v.vol_textGap)\nval x = 16f", path = stubs, only = true),
        )
        assertEquals(
            listOf(".npStatusGap is changed and no bridge file lists it"),
            scan("val g = s.board.npStatusGap + 6.4f", path = stubs, only = true),
        )
        assertEquals(emptyList<String>(), bridgeScan("val a = s.board.npStatusGap"))
    }

    @Test
    fun theTestRootPassRunsTheBridgeChecksAlone() {
        val path = "kit/src/test/kotlin/A.kt"
        assertEquals(
            listOf("Tok.motion_sheet is bridged: read Old.motion_sheet"),
            bridgeScan("val a = 16f\nval b = Tok.motion_sheet\nval c = x.dp\nval d = Color.White\nval e = tween(300)", path = path, only = true),
        )
        assertEquals(listOf("Old.motion_jumpIn is ported: read Tok.motion_jumpIn"), bridgeScan("val a = Old.motion_jumpIn", path = path, only = true))
        assertEquals(emptyList<String>(), bridgeScan("val a = 16f\nval b = Old.motion_sheet", path = path, only = true))
    }

    @Test
    fun oldAndOldBoardAreTokReceiversForColoursAndSpecs() {
        val listed = BridgeRule(setOf("color_bg", "color_ring", "motion_sheet", "motion_x", "motion_d", "motion_s"), emptySet(), emptySet())
        val body = listOf(
            "val c = Color(Old.color_bg)",
            "val d = Color(OldBoard.r1.color_ring)",
            "val s = tween(Old.motion_sheet.toInt(), easing = e)",
            "val t = tween(OldBoard.a105.motion_x.toInt(), 0, e)",
            "val u = spring(dampingRatio = Old.motion_d, stiffness = Old.motion_s)",
        ).joinToString("\n")
        assertEquals(emptyList<String>(), bridgeScan(body, bridge = listed))
        assertEquals(
            listOf("Color( without Tok", ".color_bg is bridged: read Old.color_bg"),
            bridgeScan("val c = Color(Older.color_bg)", bridge = listed),
        )
    }

    @Test
    fun aLiteralReasonTakesTheGrammarAndAnFbIdMustBeListed() {
        val ids = setOf("FB-lit-1")
        assertEquals(null, Literals.reasonError("fos player/ui/rows.cpp:815 the row inset", ids))
        assertEquals(null, Literals.reasonError("fos player/ui/rows.cpp:815-817 the row inset", ids))
        assertEquals(null, Literals.reasonError("platform View.GestureExclusion the 200 dp cap", ids))
        assertEquals(null, Literals.reasonError("fx 16.16 one", ids))
        assertEquals(null, Literals.reasonError("FB FB-lit-1 seilent picked it", ids))
        assertEquals("fos needs <path>:<line>", Literals.reasonError("fos player/ui/rows.cpp the row inset", ids))
        assertEquals("fos needs <path>:<line>", Literals.reasonError("fos", ids))
        assertEquals("platform needs an api", Literals.reasonError("platform", ids))
        assertEquals("FB needs an id", Literals.reasonError("FB", ids))
        assertEquals("FB FB-lit-2 is not in fallbacks.txt", Literals.reasonError("FB FB-lit-2 picked", ids))
        assertEquals("the reason starts with fos, platform, fx or FB", Literals.reasonError("funeOS ui/rows.cpp:815 the inset", ids))
    }

    @Test
    fun theLiteralsFileFailsAReasonOutsideTheGrammarAndFallbacksNeedADateOrPending() {
        val d = kotlin.io.path.createTempDirectory("lit").toFile()
        try {
            val lit = File(d, "literals.txt")
            val fb = File(d, "fallbacks.txt")
            fb.writeText("FB-lit-1 pending a seed literal\nFB-x 2026-11-15 a signed one\n")
            lit.writeText("kit/A.kt HALF 2f fos player/ui/a.cpp:3 half\nkit/A.kt X 3f FB FB-x why\n")
            assertEquals(2, Literals.read(lit, fb).entries.size)
            lit.writeText("kit/A.kt HALF 2f half the width\n")
            val e = org.junit.Assert.assertThrows(GenException::class.java) { Literals.read(lit, fb) }
            assertEquals("literals: literals.txt:1 the reason starts with fos, platform, fx or FB", e.message)
            fb.writeText("FB-x soon a bad one\n")
            org.junit.Assert.assertThrows(GenException::class.java) { Fallbacks.read(fb) }
            fb.writeText("FB-x pending a\nFB-x pending b\n")
            org.junit.Assert.assertThrows(GenException::class.java) { Fallbacks.read(fb) }
        } finally {
            d.deleteRecursively()
        }
    }

    @Test
    fun theUiTreeIsCleanWithEveryListedEntryAndFailsWithoutEachOne() {
        val tree = Gen.scanRoots
            .flatMap { File(repo, it).walkTopDown().filter { f -> f.isFile && f.name.endsWith(".kt") }.toList() }
        val literals = Literals.read(File(repo, "kit/literals.txt"))
        val bridge = Bridge.rule(File(repo, "tokens/funeos"), File(repo, "tokens/funeos-old"))
        val counts = Bridge.lines(File(repo, "tokens/funeos-old/bridge")).groupingBy { it.file to it.kind }.eachCount()
        assertEquals(
            mapOf(
                ("c1.txt" to "new") to 20, ("c3.txt" to "new") to 8, ("c4.txt" to "new") to 2,
                ("c5.txt" to "old") to 7, ("c6.txt" to "old") to 8, ("c7.txt" to "old") to 15, ("c8.txt" to "old") to 1,
                ("c9.txt" to "old") to 43,
            ),
            counts,
        )
        assertEquals(74, bridge.old.size)
        assertEquals(30, bridge.new.size)
        assertEquals(178, bridge.changed.size)
        assertTrue(bridge.changed.containsAll(bridge.new))
        val back = syntax.scan("kit/src/main/kotlin/A.kt", "package a\nval a = Tok.motion_sheet\n", emptySet(), HashSet(), bridge)
        assertEquals(listOf("Tok.motion_sheet is bridged: read Old.motion_sheet"), back.map { it.what })
        val tests = listOf("kit/src").flatMap { src ->
            File(repo, src).listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("test") }
                .flatMap { File(it, "kotlin").walkTopDown().filter { f -> f.isFile && f.name.endsWith(".kt") }.toList() }
        } + File(repo, "sheet/src").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
        assertTrue(listOf("MovePaintTest.kt", "ArbiterTest.kt", "Regions.kt").all { n -> tests.any { it.name == n } })
        assertEquals(emptyList<String>(), Syntax.scanFiles(repo, tree, literals, bridge, tests).map { it.toString() })
        for (a in literals.entries) {
            val text = File(repo, a.path).readText()
            val bare = syntax.scan(a.path, text, literals.allowed(a.path) - (a.site to a.text))
            assertTrue("${a.path} ${a.site} ${a.text}", bare.isNotEmpty())
        }
    }
}
