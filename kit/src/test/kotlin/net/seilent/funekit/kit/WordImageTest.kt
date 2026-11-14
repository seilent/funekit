package net.seilent.funekit.kit

import android.graphics.Bitmap
import android.icu.text.BreakIterator
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 34, 35])
class WordImageTest {
    private val none = Insets(0, 0, 0, 0)
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val boards = listOf(
        "a105" to board(720, 1280),
        "r1" to board(480, 800),
        "v1" to board(240, 320),
    )
    private val p2160 = "p2160" to board(1080, 2160)
    private val open = IntRect(-100000, -100000, 100000, 100000)

    private class Shot(val px: IntArray, val w: Int, val h: Int)

    private fun board(w: Int, h: Int): Scale = scale(Window(w, h, 35, none, none, none, none, none), Nav.None, Surface.Page, false)

    private fun sdk() = "sdk ${Build.VERSION.SDK_INT}"

    private fun chromeAt(s: Scale): Chrome = chromeOf(measurer, fonts, s, unitDensity, "apps", listOf("all", "new"), null)

    private fun baseline(s: Scale) = px(Tok.page_headerBaseline, s.stdPpu)

    private fun raster(w: Int, h: Int, ground: Color?, draw: DrawScope.() -> Unit): Shot {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            if (ground != null) drawRect(ground, Offset.Zero, size)
            draw()
        }
        return Shot(IntArray(w * h).also { img.readPixels(it) }, w, h)
    }

    private fun alone(word: Word, x: Float, base: Float, w: Int, h: Int): List<BooleanArray> {
        val out = ArrayList<BooleanArray>()
        for (r in word.runs) {
            val t = r.layout.layoutInput.text.text
            val b = BreakIterator.getCharacterInstance().also { it.setText(t) }
            var s = b.first()
            var e = b.next()
            while (e != BreakIterator.DONE) {
                if (!Character.isWhitespace(t.codePointAt(s))) {
                    val hidden = SpanStyle(color = Color.Transparent)
                    val start = s
                    val end = e
                    val text = buildAnnotatedString {
                        append(t)
                        if (start > 0) addStyle(hidden, 0, start)
                        if (end < t.length) addStyle(hidden, end, t.length)
                    }
                    val layout = measurer.measure(text, r.layout.layoutInput.style, softWrap = false, maxLines = 1, density = unitDensity, fontFamilyResolver = fonts)
                    val at = Offset(x + r.x + r.dx + r.layout.getLineLeft(0) - layout.getLineLeft(0), base - layout.firstBaseline)
                    val shot = raster(w, h, null) { drawText(layout, Ink.white, at, 1f) }
                    out += BooleanArray(w * h) { shot.px[it] ushr 24 > 0 }
                }
                s = e
                e = b.next()
            }
        }
        return out
    }

    private fun overlaps(glyphs: List<BooleanArray>, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(w * h)
        for (p in 0 until w * h) {
            if (glyphs.count { it[p] } < 2) continue
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val x = p % w + dx
                    val y = p / w + dy
                    if (x in 0 until w && y in 0 until h) out[y * w + x] = true
                }
            }
        }
        return out
    }

    private fun channel(px: Int, shift: Int) = px shr shift and 0xFF

    private class Worst(val levels: Int, val where: String)

    private fun worst(a: Shot, b: Shot, skip: BooleanArray): Worst {
        var levels = 0
        var at = -1
        for (p in a.px.indices) {
            if (skip[p]) continue
            for (shift in intArrayOf(0, 8, 16)) {
                val d = abs(channel(a.px[p], shift) - channel(b.px[p], shift))
                if (d > levels) {
                    levels = d
                    at = p
                }
            }
        }
        return Worst(levels, if (at < 0) "none" else "(${at % a.w}, ${at / a.w})")
    }

    private fun compare(what: String, image: Shot, text: Shot, skip: BooleanArray, note: String = "") {
        val bg = raster(1, 1, Ink.bg) {}.px[0]
        var inked = 0
        for (p in text.px.indices) if (!skip[p] && text.px[p] != bg) inked++
        val masked = skip.count { it }
        val w = worst(image, text, skip)
        val counts = "$inked ink px compared, $masked overlap px skipped"
        assertTrue("$what ${sdk()}: $counts", inked > 0 && masked < inked)
        val reason = "2 levels allowed: the image stores the coverage in 8 bit and rounds it once more than the text draw"
        assertTrue("$what ${sdk()}: worst ${w.levels} levels at ${w.where}, $counts$note; $reason", w.levels <= 2)
    }

    private fun hidePivots(c: Chrome) {
        for (i in c.pivotParts.indices) c.pivotParts[i] = ChromePart(hidden = true)
    }

    private fun unclipped(word: Word, x: Float, base: Int, w: Int, h: Int, clipBottom: Int): Shot {
        val ox = minOf(0, floor(x + word.inkLeft).toInt() - 2)
        val oy = minOf(0, floor(base + word.inkTop).toInt() - 2)
        val bw = maxOf(w, ceil(x + word.inkRight).toInt() + 2) - ox
        val bh = maxOf(h, ceil(base + word.inkBottom).toInt() + 2) - oy
        val big = raster(bw, bh, Ink.bg) { word(word, x - ox, (base - oy).toFloat(), Ink.header, Roles.pageHeader.alpha) }
        val bg = raster(1, 1, Ink.bg) {}.px[0]
        return Shot(IntArray(w * h) { p -> if (p / w < clipBottom) big.px[(p / w - oy) * bw + p % w - ox] else bg }, w, h)
    }

    @Test
    fun theHeaderAtRestDrawsAsItsUnclippedTextOutsideGlyphOverlaps() {
        for ((name, s) in boards + p2160) {
            val c = chromeAt(s)
            hidePivots(c)
            val w = s.widthPx
            val h = max(px(s.board.pivotBottom, s.ppu), px(s.board.pivotTop, s.stdPpu))
            val top = px(s.board.pivotTop, s.stdPpu)
            val x = c.headerRestX()
            val base = baseline(s)
            val word = c.header
            assertTrue("$name: the header word runs past the screen's right edge", x + word.inkRight > w)
            val image = raster(w, h, Ink.bg) { chrome(c, 0L) }
            val text = unclipped(word, x, base, w, h, top)
            val clipped = raster(w, h, Ink.bg) {
                clipRect(0f, 0f, size.width, top.toFloat()) { word(word, x, base.toFloat(), Ink.header, Roles.pageHeader.alpha) }
            }
            val skip = overlaps(alone(word, x, base.toFloat(), w, h), w, h)
            val edge = worst(clipped, text, skip)
            compare("$name header at pen $x", image, text, skip, "; the text drawn in the screen clip differs from it by ${edge.levels} levels at ${edge.where}")
        }
    }

    @Test
    fun theHeaderKeepsItsImageThroughAlphaLitPressAndPartChanges() {
        val s = p2160.second
        val c = chromeAt(s)
        val w = s.widthPx
        val h = max(px(s.board.pivotBottom, s.ppu), px(s.board.pivotTop, s.stdPpu))
        raster(w, h, Ink.bg) {
            chrome(c, 0L)
            val first = c.headerImage.placed
            assertNotNull(first)
            val steps = listOf<Pair<String, () -> Unit>>(
                "header alpha" to { c.headerAlpha = Roles.pageHeader.alpha / 2 },
                "pressed" to { c.press(Chrome.HEADER) },
                "lit after the lift" to { c.lift() },
                "cleared" to { c.clear() },
                "part alpha" to { c.headerPart = ChromePart(alpha = Roles.pageHeader.alpha) },
                "part dy" to { c.headerPart = ChromePart(dy = 1) },
            )
            for ((what, change) in steps) {
                change()
                chrome(c, 0L)
                assertSame(what, first, c.headerImage.placed)
            }
        }
    }

    @Test
    fun aWholePixelPartDxCropsAnewAndDrawsAsItsClippedTextAtThatPen() {
        for ((name, s) in boards + p2160) {
            val c = chromeAt(s)
            hidePivots(c)
            val w = s.widthPx
            val h = max(px(s.board.pivotBottom, s.ppu), px(s.board.pivotTop, s.stdPpu))
            val top = px(s.board.pivotTop, s.stdPpu)
            raster(w, h, Ink.bg) { chrome(c, 0L) }
            val rest = c.headerImage.placed
            assertNotNull(rest)
            c.headerPart = ChromePart(dx = -1f)
            val x = c.headerRestX() - 1f
            val base = baseline(s)
            val word = c.header
            val image = raster(w, h, Ink.bg) { chrome(c, 0L) }
            val moved = c.headerImage.placed
            assertNotNull(moved)
            assertNotSame("$name: the crop follows the pen", rest, moved)
            assertEquals("$name: the moved crop starts at the clip's left edge", -floor(x).toInt(), moved!!.left)
            val text = unclipped(word, x, base, w, h, top)
            compare("$name header at pen $x", image, text, overlaps(alone(word, x, base.toFloat(), w, h), w, h))
        }
    }

    @Test
    fun theImageIsTheInkBoxCutToTheHeaderClip() {
        val sizes = mapOf(
            "a105" to intArrayOf(720, 189, 136080),
            "r1" to intArrayOf(480, 126, 60480),
            "v1" to intArrayOf(240, 58, 13920),
            "p2160" to intArrayOf(1080, 284, 306720),
        )
        for ((name, s) in boards + p2160) {
            val c = chromeAt(s)
            val word = c.header
            val x = c.headerRestX()
            val base = baseline(s)
            val top = px(s.board.pivotTop, s.stdPpu)
            assertTrue("$name: the ink starts left of the screen", x + word.inkLeft < 0f)
            assertTrue("$name: the ink starts above the screen", base + word.inkTop < 0f)
            assertTrue("$name: the ink ends right of the screen", x + word.inkRight > s.widthPx)
            assertTrue("$name: the ink ends below the clip", base + word.inkBottom > top)
            raster(s.widthPx, top, Ink.bg) { chrome(c, 0L) }
            val p = c.headerImage.placed
            assertNotNull(p)
            val bitmap = p!!.image.asAndroidBitmap()
            val want = sizes.getValue(name)
            assertEquals("$name width", want[0], bitmap.width)
            assertEquals("$name height", want[1], bitmap.height)
            assertEquals("$name bytes", want[2], bitmap.byteCount)
            assertEquals("$name: the image starts at the clip's left edge", -floor(x).toInt(), p.left)
            assertEquals("$name: the image starts at the clip's top edge", -base, p.top)
            val f = x - floor(x)
            val left = floor(f + word.inkLeft).toInt() - 1
            val up = floor(word.inkTop).toInt() - 1
            val all = Bitmap.createBitmap(ceil(f + word.inkRight).toInt() + 1 - left, ceil(word.inkBottom).toInt() + 1 - up, Bitmap.Config.ALPHA_8)
            CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(all.asImageBitmap()), Size(all.width.toFloat(), all.height.toFloat())) {
                word(word, f - left, -up.toFloat(), Ink.white, 1f)
            }
            val cropped = IntArray(bitmap.width * bitmap.height)
            val same = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(cropped, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            all.getPixels(same, 0, bitmap.width, p.left - left, p.top - up, bitmap.width, bitmap.height)
            assertArrayEquals("$name ${sdk()}: the crop holds the px of the word rendered over its whole ink box", same, cropped)
        }
    }

    @Test
    fun aFractionalPartDxDrawsTheHeaderAsText() {
        val s = p2160.second
        val c = chromeAt(s)
        c.headerPart = ChromePart(dx = 0.5f)
        val w = s.widthPx
        val h = max(px(s.board.pivotBottom, s.ppu), px(s.board.pivotTop, s.stdPpu))
        val image = raster(w, h, Ink.bg) { chrome(c, 0L) }
        val text = raster(w, h, Ink.bg) {
            c.headerPart = ChromePart(hidden = true)
            chrome(c, 0L)
            c.clearParts()
            clipRect(0f, 0f, size.width, px(s.board.pivotTop, s.stdPpu).toFloat()) {
                word(c.header, c.headerRestX() + 0.5f, baseline(s).toFloat(), Ink.header, Roles.pageHeader.alpha)
            }
        }
        assertArrayEquals(sdk(), text.px, image.px)
    }

    @Test
    fun theWholeWordDrawsAsItsTextAtEveryPenFraction() {
        for ((name, s) in boards) {
            val c = chromeAt(s)
            val word = c.header
            val rest = c.headerRestX() - floor(c.headerRestX())
            for (f in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, rest)) {
                for (alpha in floatArrayOf(Roles.pageHeader.alpha, Tok.alpha_full)) {
                    val x = 2 - floor(word.inkLeft) + f
                    val base = 2 - floor(word.inkTop).toInt()
                    val w = ceil(x + word.inkRight).toInt() + 2
                    val h = ceil(base + word.inkBottom).toInt() + 2
                    val cache = WordImage()
                    val image = raster(w, h, Ink.bg) { wordImage(cache, word, x, base, Ink.header, alpha, Rect(Offset.Zero, size)) }
                    val text = raster(w, h, Ink.bg) { word(word, x, base.toFloat(), Ink.header, alpha) }
                    compare("$name whole word at pen $x alpha $alpha", image, text, overlaps(alone(word, x, base.toFloat(), w, h), w, h))
                }
            }
        }
    }

    @Test
    fun aKeyIsRenderedOnceIntoAnImmutableAlpha8Image() {
        val s = boards.first().second
        val word = chromeAt(s).header
        val cache = WordImage()
        val first = cache.at(word, 0.5f, open, unitDensity)!!
        assertSame(first, cache.at(word, 0.5f, open, unitDensity))
        assertSame(first, cache.placed)
        val bitmap = first.image.asAndroidBitmap()
        assertEquals(Bitmap.Config.ALPHA_8, bitmap.config)
        assertFalse(bitmap.isMutable)
        assertEquals(ceil(0.5f + word.inkRight).toInt() + 1 - first.left, bitmap.width)
        assertEquals(ceil(word.inkBottom).toInt() + 1 - first.top, bitmap.height)
        assertNotSame(first, cache.at(word, 0.25f, open, unitDensity))
        val again = cache.at(word, 0.5f, open, unitDensity)
        assertNotSame(first, again)
        val cut = IntRect(first.left, first.top, first.left + 1, first.top + 1)
        val small = cache.at(word, 0.5f, cut, unitDensity)!!
        assertNotSame(again, small)
        assertSame(small, cache.placed)
        assertEquals(1, small.image.width)
        assertEquals(1, small.image.height)
        assertNotSame(small, cache.at(chromeAt(s).header, 0.5f, cut, unitDensity))
        assertNull(cache.at(word, 0.5f, IntRect(first.left - 2, first.top - 2, first.left - 1, first.top - 1), unitDensity))
        assertNull(cache.placed)
    }

    @Test
    fun anImageAtOnePenFractionServesEveryWholePixelPen() {
        val s = boards.first().second
        val word = chromeAt(s).header
        val cache = WordImage()
        val placed = ArrayList<PlacedImage?>()
        val wide = Rect(-10000f, -10000f, 10000f, 10000f)
        raster(1, 1, null) {
            wordImage(cache, word, 10.5f, 0, Ink.header, 1f, wide)
            placed += cache.placed
            wordImage(cache, word, 37.5f, 0, Ink.header, 1f, wide)
            placed += cache.placed
        }
        assertNotNull(placed[0])
        assertSame(placed[0], placed[1])
    }

    @Test
    fun aWordWithNoInkMakesNoImageAndDrawsNothing() {
        val cache = WordImage()
        assertNull(cache.at(Word.none, 0f, open, unitDensity))
        val image = raster(4, 4, Ink.bg) { wordImage(cache, Word.none, 1f, 2, Ink.header, 1f, Rect(Offset.Zero, size)) }
        assertArrayEquals(raster(4, 4, Ink.bg) {}.px, image.px)
    }

    @Test
    fun theHeaderDrawsAsTextWhileThePivotStripMoves() {
        for ((name, s) in boards) {
            val c = chromeAt(s)
            c.switchTo(1, 0L, 1)
            val now = 10L
            assertTrue(c.moving(now))
            val w = s.widthPx
            val h = max(px(s.board.pivotBottom, s.ppu), px(s.board.pivotTop, s.stdPpu))
            val image = raster(w, h, Ink.bg) { chrome(c, now) }
            val text = raster(w, h, Ink.bg) {
                c.headerPart = ChromePart(hidden = true)
                chrome(c, now)
                c.clearParts()
                clipRect(0f, 0f, size.width, px(s.board.pivotTop, s.stdPpu).toFloat()) {
                    word(c.header, c.headerDrawX(now), baseline(s).toFloat(), Ink.header, Roles.pageHeader.alpha)
                }
            }
            assertArrayEquals("$name ${sdk()}", text.px, image.px)
        }
    }
}
