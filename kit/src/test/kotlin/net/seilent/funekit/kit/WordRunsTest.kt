package net.seilent.funekit.kit

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.Bidi
import kotlin.math.abs
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 35])
class WordRunsTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)
    private val role = Roles.listCollection
    private val bold = roleStyle(role, a105, unitDensity, Ink.white)
    private val regular = bold.copy(fontWeight = FontWeight.Normal)

    private fun flat(st: TextStyle): Paint = Paint().also {
        it.isAntiAlias = true
        it.isLinearText = true
        it.isSubpixelText = true
        it.typeface = fonts.resolve(st.fontFamily, st.fontWeight ?: FontWeight.Normal, FontStyle.Normal, FontSynthesis.None).value as Typeface
        it.textSize = st.fontSize.value
    }

    private val flatBold = flat(bold)
    private val flatRegular = flat(regular)
    private val trackedRegular = Paint(flatRegular).also { it.letterSpacing = TRACK / it.textSize }
    private val trackedBold = Paint(flatBold).also { it.letterSpacing = TRACK / it.textSize }

    private fun adv(p: Paint, s: String, rtl: Boolean = false) = p.getRunAdvance(s, 0, s.length, 0, s.length, rtl, s.length)

    private fun tracksOf(tracked: Paint, flat: Paint, s: String, rtl: Boolean = false) = adv(tracked, s, rtl) - adv(flat, s, rtl)

    private fun sdk() = "sdk ${Build.VERSION.SDK_INT}"

    private fun texts(w: Word) = w.runs.map { it.layout.layoutInput.text.text }

    private fun directions(w: Word) = w.runs.map { it.layout.layoutInput.style.textDirection }

    private fun pens(w: Word) = w.runs.map { it.x }

    private fun assertPens(seen: String, want: List<Float>, w: Word) {
        assertEquals(seen, want.size, w.runs.size)
        want.zip(pens(w)).forEach { (a, b) -> assertEquals(seen, a, b, WIDTH_SLACK) }
    }

    private fun word(t: String, bolded: Boolean, c: Covers? = covers) = measurer.word(t, role, if (bolded) bold else regular, fonts, unitDensity, c)

    private class Shot(val px: IntArray, val w: Int, val h: Int) {
        fun red(x: Int, y: Int) = px[y * w + x] shr 16 and 0xFF
    }

    private fun shot(draw: DrawScope.() -> Unit): Shot {
        val img = ImageBitmap(SHOT_W, SHOT_H)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(SHOT_W.toFloat(), SHOT_H.toFloat())) {
            drawRect(Ink.bg, Offset.Zero, size)
            draw()
        }
        return Shot(IntArray(SHOT_W * SHOT_H).also { img.readPixels(it) }, SHOT_W, SHOT_H)
    }

    private fun column(s: Shot, x: Int): Int {
        var m = 0
        for (y in 0 until s.h) m = maxOf(m, s.red(x, y))
        return m
    }

    private fun leftEdge(s: Shot, from: Int): Float {
        val bg = s.red(0, 0)
        var x = from
        while (column(s, x) <= bg) x++
        return x + 1 - (column(s, x) - bg).toFloat() / (FULL - bg)
    }

    private fun diff(a: Shot, b: Shot): Long = a.px.indices.sumOf { i -> abs((a.px[i] shr 16 and 0xFF) - (b.px[i] shr 16 and 0xFF)).toLong() }

    private fun centroid(s: Shot, from: Int, to: Int = s.w): Float {
        val bg = s.red(0, 0)
        var sum = 0.0
        var at = 0.0
        for (y in 0 until s.h) for (x in from until to) {
            val v = s.red(x, y) - bg
            if (v > 0) {
                sum += v
                at += v.toDouble() * x
            }
        }
        return (at / sum).toFloat()
    }

    @Test
    fun theStyleOfABoldCollectionRowIsBold() {
        assertEquals(FontWeight.Bold, bold.fontWeight)
    }

    @Test
    fun aRunSelawikLacksMeasuresAsTheSameRunAtRegular() {
        for (t in listOf("漢字", "שלום", "𠮷")) {
            val lacked = word(t, bolded = true)
            val flat = word(t, bolded = false, c = null)
            assertEquals(t, flat.width, lacked.width, 0f)
            assertEquals(t, flat.inkLeft, lacked.inkLeft, 0f)
            assertEquals(t, flat.inkRight, lacked.inkRight, 0f)
        }
    }

    @Test
    fun aMixedNameIsTheSumOfItsRunsAtTheirFaces() {
        for ((t, cut) in listOf("ab漢字" to 2, "ab שלום" to 3)) {
            val mixed = word(t, bolded = true)
            val latin = word(t.substring(0, cut), bolded = true)
            val rest = word(t.substring(cut), bolded = false, c = null)
            assertEquals(t, latin.width + rest.width, mixed.width, WIDTH_SLACK)
            assertEquals(t, latin.inkLeft, mixed.inkLeft, 0f)
            assertEquals(t, latin.width, mixed.runs[1].x, WIDTH_SLACK)
            assertEquals(t, mixed.runs[1].x + rest.inkRight, mixed.inkRight, WIDTH_SLACK)
        }
        val emoji = word("ab🎵", bolded = true)
        assertEquals(listOf("AB", "🎵"), emoji.runs.map { it.layout.layoutInput.text.text })
        assertEquals(word("ab", bolded = true).width + word("🎵", bolded = false, c = null).width, emoji.width, WIDTH_SLACK)
    }

    @Test
    fun textWidthCasesAsTheRoleAndMeasuresTheSameRunsAsTheWord() {
        val w = textWidth(role, bold, fonts, unitDensity, covers)
        for (t in listOf("ab漢字", "漢字 cd", "ab", "ラジオ", "ab שלום", "ab🎵", "𠮷", "a1\uFE0F\u20E3", "A\u200BB", HEBREW_DIGIT, MIXED_DIGITS, LTR_HEBREW, ARABIC_FIRST)) {
            assertEquals(t, word(t, bolded = true).width, w(t), WIDTH_SLACK)
        }
        val ab = adv(flatBold, "AB") + 2 * TRACK
        assertEquals(ab, w("ab"), WIDTH_SLACK)
        assertEquals(ab, word("ab", bolded = true).width, WIDTH_SLACK)
    }

    @Test
    fun bidiIgnorablesTakeNoTrackingAndEveryOtherCodePointTakesOne() {
        val w = textWidth(role, bold, fonts, unitDensity, covers)
        val flat = textWidth(role, bold.copy(letterSpacing = TextUnit.Unspecified), fonts, unitDensity, covers)
        assertEquals(2 * TRACK, w("A\u200BB") - adv(flatBold, "A\u200BB"), WIDTH_SLACK)
        assertEquals(2 * TRACK, w("A\u2060B") - adv(flatBold, "A\u2060B"), WIDTH_SLACK)
        assertEquals(-3f, w("1\uFE0F\u20E3") - flat("1\uFE0F\u20E3"), WIDTH_SLACK)
        assertEquals(-1.5f, w("𠮷") - flat("𠮷"), WIDTH_SLACK)
    }

    @Test
    fun caseMapsAsciiLettersOnlyAndKeepsTheLength() {
        assertEquals("STRAßE éTé", cased("straße été", Roles.listCollection))
        assertEquals("istanbul İ", cased("ISTANBUL İ", Roles.homeItem))
    }

    @Test
    fun eachRunStartsAtTheWidth64OfThePlainRunsBeforeIt() {
        val w = word("ab 漢字 cd", bolded = true)
        val cjk = word("漢字", bolded = false, c = null)
        assertEquals(listOf("AB ", "漢字", " CD"), texts(w))
        assertEquals(listOf(FontWeight.Bold, FontWeight.Normal, FontWeight.Bold), w.runs.map { it.layout.layoutInput.style.fontWeight })
        val first = adv(flatBold, "AB ") + 3 * TRACK
        val second = adv(flatRegular, "漢字") + 2 * TRACK
        assertEquals(second, cjk.width, WIDTH_SLACK)
        assertPens("ab 漢字 cd", listOf(0f, first, first + second), w)
        assertEquals(first + second + adv(flatBold, " CD") + 3 * TRACK, w.width, WIDTH_SLACK)
    }

    @Test
    fun aMultiCodePointClusterMovesTheNextPenByThePlatformTracksAndTheWidthByItsCodePoints() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val w = word("ab$family cd", bolded = true)
        assertEquals(listOf("AB", family, " CD"), texts(w))
        val tracks = tracksOf(trackedRegular, flatRegular, family)
        assertTrue("${sdk()}: the family emoji draws ${tracks / TRACK} tracks", abs(tracks) >= abs(TRACK) / 2)
        val first = adv(flatBold, "AB") + 2 * TRACK
        assertPens("family", listOf(0f, first, first + adv(flatRegular, family) + tracks), w)
        val model = adv(flatBold, "AB") + 2 * TRACK + adv(flatRegular, family) + 3 * TRACK + adv(flatBold, " CD") + 3 * TRACK
        assertEquals(model, w.width, WIDTH_SLACK)
        assertEquals(w.width, textWidth(role, bold, fonts, unitDensity, covers)("ab$family cd"), WIDTH_SLACK)
        val x = 40f
        val cd = w.runs[2]
        val rest = word(" cd", bolded = true)
        val drawn = shot { word(w, x, BASELINE, Ink.white, 1f) }
        val alone = shot { word(rest, x, BASELINE, Ink.white, 1f) }
        val from = ceil(x + cd.x + adv(flatBold, " ") / 2).toInt()
        assertEquals(sdk(), x + cd.x + rest.inkLeft, leftEdge(drawn, from), INK_SLACK)
        assertEquals(sdk(), cd.x, centroid(drawn, from) - centroid(alone, 0), PEN_SLACK)
    }

    @Test
    fun aTwoLetterWordAtTheV1TrackKeepsBothTracks() {
        val v1 = roleStyle(role, a105, unitDensity, Ink.white, ppu = 1f)
        assertEquals(V1_TRACK, v1.letterSpacing.value, WIDTH_SLACK)
        val f = flat(v1)
        val w = measurer.word("ab", role, v1, fonts, unitDensity, covers)
        assertEquals(adv(f, "AB") + 2 * V1_TRACK, w.width, WIDTH_SLACK)
        assertEquals(w.width, textWidth(role, v1, fonts, unitDensity, covers)("ab"), WIDTH_SLACK)
    }

    @Test
    fun aCoveredNameKeepsOneRun() {
        val w = word("abc", bolded = true)
        assertEquals(1, w.runs.size)
        assertEquals(FontWeight.Bold, w.runs[0].layout.layoutInput.style.fontWeight)
        assertEquals(word("abc", bolded = true, c = null).width, w.width, 0f)
    }

    @Test
    fun theSecondRunInksAtItsPen() {
        val x = 40f
        val latin = word("ab ", bolded = true)
        assertEquals(adv(flatBold, "AB ") + 3 * TRACK, latin.width, WIDTH_SLACK)
        for (t in listOf("ab 漢字", "ab שלום")) {
            val mixed = word(t, bolded = true)
            val rest = word(t.substring(latin.text.length), bolded = false, c = null)
            val seen = "sdk ${Build.VERSION.SDK_INT} $t"
            assertEquals(seen, 2, mixed.runs.size)
            val pen = mixed.runs[1].x
            assertEquals(seen, latin.width, pen, WIDTH_SLACK)
            val drawn = shot { word(mixed, x, BASELINE, Ink.white, 1f) }
            val alone = shot { word(rest, x, BASELINE, Ink.white, 1f) }
            val from = ceil(x + latin.inkRight).toInt() + 2
            assertEquals(seen, x + rest.inkLeft, leftEdge(alone, 0), INK_SLACK)
            assertEquals(seen, x + pen + rest.inkLeft, leftEdge(drawn, from), INK_SLACK)
            assertEquals(seen, pen, centroid(drawn, from) - centroid(alone, 0), PEN_SLACK)
        }
    }

    @Test
    fun anRtlNameWithADigitLaysItsRunsOutInVisualOrder() {
        val w = word(HEBREW_DIGIT, bolded = true)
        assertEquals(listOf("2", " ", "שלום"), texts(w))
        assertEquals(listOf(TextDirection.Ltr, TextDirection.Rtl, TextDirection.Rtl), directions(w))
        assertEquals(listOf(FontWeight.Bold, FontWeight.Bold, FontWeight.Normal), w.runs.map { it.layout.layoutInput.style.fontWeight })
        val digit = adv(flatBold, "2") + TRACK
        val space = adv(flatBold, " ", rtl = true) + TRACK
        assertPens(HEBREW_DIGIT, listOf(0f, digit, digit + space), w)
        val model = digit + space + adv(flatRegular, "שלום", rtl = true) + 4 * TRACK
        assertEquals(model, w.width, WIDTH_SLACK)
    }

    @Test
    fun anLtrNameWithAHebrewWordAndDigitsPutsTheDigitsBeforeTheHebrew() {
        val w = word(MIXED_DIGITS, bolded = true)
        assertEquals(listOf("AB ", "12", " ", "שלום"), texts(w))
        assertEquals(listOf(TextDirection.Ltr, TextDirection.Ltr, TextDirection.Rtl, TextDirection.Rtl), directions(w))
        val latin = adv(flatBold, "AB ") + 3 * TRACK
        val digits = adv(flatBold, "12") + 2 * TRACK
        val space = adv(flatBold, " ", rtl = true) + TRACK
        assertPens(MIXED_DIGITS, listOf(0f, latin, latin + digits, latin + digits + space), w)
        val model = latin + digits + space + adv(flatRegular, "שלום", rtl = true) + 4 * TRACK
        assertEquals(model, w.width, WIDTH_SLACK)
    }

    @Test
    fun anLtrNameKeepsItsRunsInLogicalOrder() {
        val w = word(LTR_HEBREW, bolded = true)
        assertEquals(listOf("AB ", "שלום", " CD"), texts(w))
        assertEquals(listOf(TextDirection.Ltr, TextDirection.Rtl, TextDirection.Ltr), directions(w))
        val latin = adv(flatBold, "AB ") + 3 * TRACK
        val hebrew = adv(flatRegular, "שלום", rtl = true) + 4 * TRACK
        assertPens(LTR_HEBREW, listOf(0f, latin, latin + hebrew), w)
        val model = latin + hebrew + adv(flatBold, " CD") + 3 * TRACK
        assertEquals(model, w.width, WIDTH_SLACK)
    }

    @Test
    fun aHebrewRunLastInVisualOrderInksAtItsPenAsTheRunDrawnAlone() {
        val x = 40f
        val hebrew = word("שלום", bolded = false, c = null)
        val alone = shot { word(hebrew, x, BASELINE, Ink.white, 1f) }
        assertEquals("sdk ${Build.VERSION.SDK_INT}", x + hebrew.inkLeft, leftEdge(alone, 0), INK_SLACK)
        for (t in listOf(HEBREW_DIGIT, MIXED_DIGITS)) {
            val w = word(t, bolded = true)
            val seen = "sdk ${Build.VERSION.SDK_INT} $t"
            val last = w.runs.last()
            val space = w.runs[w.runs.lastIndex - 1]
            assertEquals(seen, "שלום", last.layout.layoutInput.text.text)
            assertEquals(seen, TextDirection.Rtl, last.layout.layoutInput.style.textDirection)
            val drawn = shot { word(w, x, BASELINE, Ink.white, 1f) }
            val from = ceil(x + (space.x + last.x) / 2f).toInt()
            assertEquals(seen, x + last.x + hebrew.inkLeft, leftEdge(drawn, from), INK_SLACK)
            assertEquals(seen, last.x, centroid(drawn, from) - centroid(alone, 0), PEN_SLACK)
        }
    }

    @Test
    fun anArabicRunTakesNoTrackingAndTheNextRunInksAtItsDrawnAdvance() {
        val arabic = "سلام"
        assertEquals(adv(flatRegular, arabic), adv(trackedRegular, arabic), WIDTH_SLACK)
        val w = word(ARABIC_FIRST, bolded = true)
        assertEquals(listOf(arabic, " ", "AB", " ", arabic), texts(w))
        assertEquals(listOf(FontWeight.Normal, FontWeight.Bold, FontWeight.Bold, FontWeight.Bold, FontWeight.Normal), w.runs.map { it.layout.layoutInput.style.fontWeight })
        val drawnArabic = adv(flatRegular, arabic, rtl = true)
        val space = adv(flatBold, " ", rtl = true) + TRACK
        val ab = adv(flatBold, "AB") + 2 * TRACK
        assertPens(ARABIC_FIRST, listOf(0f, drawnArabic, drawnArabic + space, drawnArabic + space + ab, drawnArabic + space + ab + space), w)
        assertEquals(drawnArabic + space + ab + space + drawnArabic, w.width, WIDTH_SLACK)
        val x = 40f
        val seen = "sdk ${Build.VERSION.SDK_INT}"
        val drawn = shot { word(w, x, BASELINE, Ink.white, 1f) }
        val alone = shot { word(word("ab", bolded = true), x, BASELINE, Ink.white, 1f) }
        val from = ceil(x + drawnArabic + space / 2f).toInt()
        val to = (x + drawnArabic + space + ab + space / 2f).toInt()
        assertEquals(seen, drawnArabic + space, centroid(drawn, from, to) - centroid(alone, 0), PEN_SLACK)
    }

    @Test
    fun twoRtlRunsSplitByANeutralInAnLtrNameSwapPlaces() {
        val w = word("ab שלום עולם", bolded = true)
        assertEquals(listOf("AB ", "עולם", " ", "שלום"), texts(w))
        assertEquals(listOf(TextDirection.Ltr, TextDirection.Rtl, TextDirection.Rtl, TextDirection.Rtl), directions(w))
        val latin = adv(flatBold, "AB ") + 3 * TRACK
        val second = latin + adv(flatRegular, "עולם", rtl = true) + 4 * TRACK
        assertPens("two rtl", listOf(0f, latin, second, second + adv(flatBold, " ", rtl = true) + TRACK), w)
    }

    @Test
    fun theFlatWidthIsTheUnroundedAdvanceNotTheRoundedMeasure() {
        val flatStyle = bold.copy(letterSpacing = TextUnit.Unspecified)
        val flatWidth = textWidth(role, flatStyle, fonts, unitDensity, covers)("ab")
        val widths = FloatArray(2).also { flatBold.getTextWidths("AB", it) }
        val measured = flatBold.measureText("AB")
        val seen = "${sdk()}: getTextWidths ${widths.toList()}, measureText $measured, flat width $flatWidth"
        assertEquals(seen, widths.sum(), flatWidth, UNIT64)
        assertTrue(seen, abs(measured - flatWidth) < 1f)
        assertEquals(seen, flatWidth, measurer.word("ab", role, flatStyle, fonts, unitDensity, covers).width, WIDTH_SLACK)
    }

    @Test
    fun aSingleClusterRunTakesTheTracksThePlatformDraws() {
        for ((s, tracked, flat) in listOf(Triple("A", trackedBold, flatBold), Triple(" ", trackedBold, flatBold))) {
            assertEquals("${sdk()} '$s'", TRACK, tracksOf(tracked, flat, s), WIDTH_SLACK)
            assertEquals("${sdk()} '$s'", adv(flat, s) + TRACK, word(s, bolded = true).width, WIDTH_SLACK)
        }
        assertEquals(sdk(), TRACK, tracksOf(trackedRegular, flatRegular, "𠮷"), WIDTH_SLACK)
        assertEquals(sdk(), adv(flatRegular, "𠮷") + TRACK, word("𠮷", bolded = false).width, WIDTH_SLACK)
        assertEquals(sdk(), 0f, tracksOf(trackedRegular, flatRegular, "س", rtl = true), WIDTH_SLACK)
        assertEquals(sdk(), adv(flatRegular, "س", rtl = true), word("س", bolded = false).width, WIDTH_SLACK)
    }

    @Test
    fun aRunMixingASpacedAndAnUnspacedScriptTracksOnlyTheSpacedCodePoints() {
        val mixed = "שלום سلام"
        assertEquals(sdk(), 0f, tracksOf(trackedRegular, flatRegular, "سلام", rtl = true), WIDTH_SLACK)
        assertEquals(sdk(), tracksOf(trackedRegular, flatRegular, "שלום ", rtl = true), tracksOf(trackedRegular, flatRegular, mixed, rtl = true), WIDTH_SLACK)
        val w = word(mixed, bolded = false)
        assertEquals(listOf(mixed), texts(w))
        assertEquals(sdk(), adv(flatRegular, mixed, rtl = true) + 5 * TRACK, w.width, WIDTH_SLACK)
        assertEquals(sdk(), w.width, textWidth(role, regular, fonts, unitDensity, covers)(mixed), WIDTH_SLACK)
    }

    @Test
    fun aRegularArabicAndLatinRunTracksTheSpaceAndTheLatinOnly() {
        val t = cased("سلام ab", role)
        val w = word("سلام ab", bolded = false)
        assertEquals(listOf(t), texts(w))
        assertEquals(sdk(), 0f, tracksOf(trackedRegular, flatRegular, "سلام", rtl = true), WIDTH_SLACK)
        assertEquals(sdk(), 3 * TRACK, tracksOf(trackedRegular, flatRegular, " AB"), WIDTH_SLACK)
        assertEquals(sdk(), adv(flatRegular, "سلام ", rtl = true) + adv(flatRegular, "AB") + 3 * TRACK, w.width, WIDTH_SLACK)
        assertEquals(sdk(), w.width, textWidth(role, regular, fonts, unitDensity, covers)("سلام ab"), WIDTH_SLACK)
    }

    @Test
    fun aMixedScriptRunBetweenCoveredRunsMovesTheNextPenByThePlatformTracks() {
        val t = "ab שלוםسلام cd"
        val mixed = "שלוםسلام"
        val w = word(t, bolded = true)
        assertEquals(listOf("AB ", mixed, " CD"), texts(w))
        assertEquals(listOf(TextDirection.Ltr, TextDirection.Rtl, TextDirection.Ltr), directions(w))
        val tracks = tracksOf(trackedRegular, flatRegular, mixed, rtl = true)
        assertEquals(sdk(), tracksOf(trackedRegular, flatRegular, "שלום", rtl = true), tracks, WIDTH_SLACK)
        assertEquals(sdk(), 4 * TRACK, tracks, WIDTH_SLACK)
        val latin = adv(flatBold, "AB ") + 3 * TRACK
        val run = adv(flatRegular, mixed, rtl = true)
        assertPens(t, listOf(0f, latin, latin + run + tracks), w)
        assertEquals(sdk(), latin + run + 4 * TRACK + adv(flatBold, " CD") + 3 * TRACK, w.width, WIDTH_SLACK)
        assertEquals(sdk(), w.width, textWidth(role, bold, fonts, unitDensity, covers)(t), WIDTH_SLACK)
    }

    @Test
    fun textWidthAtAnRtlBaseMeasuresAsTheWordAtThatBase() {
        val base = Bidi.DIRECTION_RIGHT_TO_LEFT
        val w = textWidth(role, bold, fonts, unitDensity, covers, base)
        val rtlName = measurer.word(RTL_BASE, role, bold, fonts, unitDensity, covers, base)
        assertTrue(texts(rtlName).toString(), rtlName.runs.size > 1)
        for (t in listOf(RTL_BASE, "ab", "שלום", "ab 12", "(ab)")) {
            assertEquals(t, measurer.word(t, role, bold, fonts, unitDensity, covers, base).width, w(t), WIDTH_SLACK)
        }
    }

    @Test
    fun aSingleRunWordMeasuresEachBidiRunInItsOwnDirection() {
        val hebrew = word("שלום", bolded = false, c = null)
        assertEquals(sdk(), 1, hebrew.runs.size)
        assertEquals(sdk(), adv(flatRegular, "שלום", rtl = true) + 4 * TRACK, hebrew.width, WIDTH_SLACK)
        val mixed = word("ab שלום", bolded = false, c = null)
        assertEquals(sdk(), 1, mixed.runs.size)
        assertEquals(sdk(), adv(flatRegular, "AB ") + adv(flatRegular, "שלום", rtl = true) + 7 * TRACK, mixed.width, WIDTH_SLACK)
        assertEquals(sdk(), mixed.width, textWidth(role, regular, fonts, unitDensity, null)("ab שלום"), WIDTH_SLACK)
    }

    @Test
    fun theCommonCodePointsOfTheArabicFaceTakeNoTracks() {
        val t = "سـلام،؛؟"
        assertEquals(sdk(), 0f, tracksOf(trackedRegular, flatRegular, t, rtl = true), WIDTH_SLACK)
        val w = word(t, bolded = false)
        assertEquals(sdk(), adv(flatRegular, t, rtl = true), w.width, WIDTH_SLACK)
        assertEquals(sdk(), w.width, textWidth(role, regular, fonts, unitDensity, covers)(t), WIDTH_SLACK)
        val split = word("ab$t", bolded = true)
        assertEquals(sdk(), adv(flatBold, "AB") + 2 * TRACK + adv(flatRegular, t, rtl = true), split.width, WIDTH_SLACK)
    }

    @Test
    fun aLoneCommonCodePointOfTheArabicFaceTakesNoTrackAndLeavesArabicUnspaced() {
        val t = "؟ سلام"
        val w = word(t, bolded = false)
        assertEquals(sdk(), adv(flatRegular, t, rtl = true) + TRACK, w.width, WIDTH_SLACK)
        val after = word("سلام", bolded = false)
        assertEquals(sdk(), adv(flatRegular, "سلام", rtl = true), after.width, WIDTH_SLACK)
    }

    @Test
    fun aWordAtAnLtrBaseInksWhereItDrawsWhateverItsFirstStrongCharacter() {
        val x = 40f
        for (t in listOf("ab", "שלום", "שלום ab")) {
            val w = measurer.word(t, role, regular, fonts, unitDensity, covers, Bidi.DIRECTION_LEFT_TO_RIGHT)
            val drawn = shot { word(w, x, BASELINE, Ink.white, 1f) }
            assertEquals("${sdk()} $t", x + w.inkLeft, leftEdge(drawn, 0), INK_SLACK)
        }
        val ltr = measurer.word("שלום", role, regular, fonts, unitDensity, covers, Bidi.DIRECTION_LEFT_TO_RIGHT)
        assertEquals(sdk(), word("שלום", bolded = false, c = null).inkLeft, ltr.inkLeft, WIDTH_SLACK)
    }

    @Test
    fun anRtlRunTakesItsLeadingShiftFromItsVisualLeftGlyph() {
        val x = 40f
        val hebrew = word("שלום", bolded = false, c = null)
        val mixed = word("سلام שלום", bolded = false, c = null)
        assertEquals(sdk(), 1, mixed.runs.size)
        assertEquals(sdk(), hebrew.inkLeft, mixed.inkLeft, WIDTH_SLACK)
        val drawn = shot { word(mixed, x, BASELINE, Ink.white, 1f) }
        assertEquals(sdk(), x + mixed.inkLeft, leftEdge(drawn, 0), INK_SLACK)
    }

    @Test
    fun aBracketInksAsTheGlyphItsDirectionDraws() {
        val open = measurer.word("(", role, bold, fonts, unitDensity, covers, Bidi.DIRECTION_RIGHT_TO_LEFT)
        val close = measurer.word(")", role, bold, fonts, unitDensity, covers, Bidi.DIRECTION_LEFT_TO_RIGHT)
        assertEquals(sdk(), close.inkLeft, open.inkLeft, WIDTH_SLACK)
        assertEquals(sdk(), close.inkRight, open.inkRight, WIDTH_SLACK)
        val x = 40f
        val drawnOpen = shot { word(open, x, BASELINE, Ink.white, 1f) }
        assertEquals(sdk(), x + open.inkLeft, leftEdge(drawnOpen, 0), INK_SLACK)
    }

    @Test
    fun aBracketInAnRtlRunIsMirroredAndOrdered() {
        val w = word("שלום (2)", bolded = true)
        assertEquals(listOf(")", "2", " (", "שלום"), texts(w))
        assertEquals(listOf(TextDirection.Rtl, TextDirection.Ltr, TextDirection.Rtl, TextDirection.Rtl), directions(w))
        val x = 40f
        val mirrored = w.runs[0].layout
        fun drawn(l: TextLayoutResult) = shot { drawText(l, Ink.white, Offset(x - l.getLineLeft(0), BASELINE - l.firstBaseline)) }
        fun ltr(s: String) = measurer.measure(s, bold.copy(textDirection = TextDirection.Ltr), softWrap = false, maxLines = 1, density = unitDensity, fontFamilyResolver = fonts)
        val got = drawn(mirrored)
        assertTrue(diff(got, drawn(ltr("("))) < diff(got, drawn(ltr(")"))))
    }

    @Test
    fun anRtlCaptionLaysItsLatinFirstSecondRowOutRightToLeft() {
        val name = "שלום ab שלום"
        val second = cased("ab שלום", role)
        val ink = requireNotNull(
            (BOX_MIN..BOX_MAX).asSequence()
                .map { boxInk(measurer, fonts, name, role, bold, Edges(0, 0, it, BOX_H), 2, a105.ppu, unitDensity, covers) }
                .firstOrNull { it.lines.size == 2 && it.lines[1].text == second },
        )
        assertTrue(ink.rtl)
        assertEquals(listOf("שלום", " ", "AB"), texts(ink.lines[1]))
        assertEquals(listOf(TextDirection.Rtl, TextDirection.Rtl, TextDirection.Ltr), directions(ink.lines[1]))
        assertEquals(listOf("AB ", "שלום"), texts(word("ab שלום", bolded = true)))
    }

    @Test
    fun aBoxCaptionWrapsAtTheWidthFuneOsPlacesWithTracksBetweenGlyphsOnly() {
        val caption = Roles.boxCaption
        val style = roleStyle(caption, a105, unitDensity, Ink.white)
        val w = textWidth(caption, style, fonts, unitDensity, covers)
        assertTrue(sdk(), pen64(w("UNKNOW")) <= QP_AVAIL64)
        assertTrue(sdk(), pen64(w("UNKNOW")) - pen64(TRACK) > QP_AVAIL64)
        val ink = boxInk(measurer, fonts, "unknown album", caption, style, Edges(0, 0, QP_TILE, QP_TILE), QP_LINES, a105.ppu, unitDensity, covers)
        assertEquals(sdk(), listOf("UNKNO", "WN", "ALBUM"), ink.lines.map { it.text })
    }

    @Test
    fun theCaptionWrapWidthTakesOneTrackOffWhateverTheScriptsWhereFuneOsPlaceTracksBetweenUnits() {
        val caption = Roles.boxCaption
        val style = roleStyle(caption, a105, unitDensity, Ink.white)
        val flatCaption = flat(style)
        val flatLacked = flat(style.copy(fontWeight = FontWeight.Normal))
        val arabic = "سلام"
        val mixed = cased("ab سلام", caption)
        val track64 = pen64(caption.tracking, a105.ppu)
        val placed = { t: String, base: Int -> penPx(Tiles.placed64(textWidth(caption, style, fonts, unitDensity, covers, base)(t), track64)) }
        val arabicTracks = (placed(arabic, Bidi.DIRECTION_RIGHT_TO_LEFT) - adv(flatLacked, arabic, rtl = true)) / TRACK
        assertEquals("${sdk()}: the kit places an all Arabic caption at -1 track; funeOS Fonts::place (gfx/shape.cpp:237-272) adds no track between two glyphs of one joined face, 0", -1f, arabicTracks, WIDTH_SLACK)
        val mixedTracks = (placed(mixed, Bidi.DIRECTION_LEFT_TO_RIGHT) - adv(flatCaption, "AB ") - adv(flatLacked, arabic, rtl = true)) / TRACK
        assertEquals("${sdk()}: the kit places AB سلام at 2 tracks; funeOS Fonts::place (gfx/shape.cpp:237-272) tracks A to B, B to the space and the space to the Arabic face, 3", 2f, mixedTracks, WIDTH_SLACK)
    }

    @Test
    fun anRtlCaptionRowEndsItsPlacedWidthAtTheCaptionEdge() {
        val caption = Roles.boxCaption
        val style = roleStyle(caption, a105, unitDensity, Ink.white)
        val tile = Edges(0, 0, QP_TILE, QP_TILE)
        val ink = boxInk(measurer, fonts, "שלום", caption, style, tile, QP_LINES, a105.ppu, unitDensity, covers)
        assertTrue(ink.rtl)
        val row = ink.lines.single()
        assertEquals("${sdk()}: funeOS rows.cpp:1099 places an RTL row at x64 - r.width64, the width Fonts::place gives (one track fewer than its code points)", QP_EDGE, ink.x(row) + row.width - TRACK, UNIT64)
    }

    @Test
    fun anAllArabicRtlCaptionRowStartsOneTrackLeftOfWhereFuneOsPlacesIt() {
        val caption = Roles.boxCaption
        val style = roleStyle(caption, a105, unitDensity, Ink.white)
        val flatLacked = flat(style.copy(fontWeight = FontWeight.Normal))
        val arabic = "سلام"
        val ink = boxInk(measurer, fonts, arabic, caption, style, Edges(0, 0, QP_TILE, QP_TILE), QP_LINES, a105.ppu, unitDensity, covers)
        assertTrue(ink.rtl)
        assertEquals("${sdk()}: BoxInk.x places an all Arabic RTL row one track (1.5 px at the A105) left of funeOS's x64 - r.width64, Fonts::place (gfx/shape.cpp:237-272) adds no track inside one joined face", QP_EDGE - adv(flatLacked, arabic, rtl = true) + TRACK, ink.x(ink.lines.single()), UNIT64 * 2)
    }

    companion object {
        private const val TRACK = -1.5f
        private const val QP_TILE = 222
        private const val QP_EDGE = 205.65625f
        private const val QP_AVAIL64 = 12714
        private const val QP_LINES = 3
        private const val WIDTH_SLACK = 0.01f
        private const val UNIT64 = 1f / 64
        private const val INK_SLACK = 0.5f
        private const val PEN_SLACK = 0.5f
        private const val SHOT_W = 400
        private const val SHOT_H = 300
        private const val BASELINE = 200f
        private const val FULL = 0xFF
        private const val V1_TRACK = -0.5f
        private const val BOX_MIN = 60
        private const val BOX_MAX = 720
        private const val BOX_H = 400
        private const val HEBREW_DIGIT = "שלום 2"
        private const val MIXED_DIGITS = "ab שלום 12"
        private const val LTR_HEBREW = "ab שלום cd"
        private const val ARABIC_FIRST = "سلام ab سلام"
        private const val RTL_BASE = "(ab) שלום"
    }
}
