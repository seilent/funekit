package net.seilent.funekit.kit.lock

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.word
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LockPaintTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(A105_W, A105_H, SDK, none, none, none, none, none), Nav.None, Surface.Saver, false)
    private val r1 = scale(Window(R1_W, R1_H, SDK, none, none, none, none, none), Nav.None, Surface.Saver, false)
    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)

    private fun word(s: Scale, text: String, role: Role, base: Int = Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT): Word =
        measurer.word(text, role, roleStyle(role, s, density, Ink.white, ppu = s.stdPpu), fonts, density, null, s.board.fontHanMinPx, base)

    private fun ink(
        s: Scale,
        clock: String = "",
        title: String = "",
        line: String = "",
        right: Boolean = false,
        base: Int = Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
    ) = LockInk(word(s, clock, Roles.saverClock), word(s, title, Roles.lockTitle), Word.none, Word.none, word(s, line, Roles.npNext, base), right, 1f)

    private fun image(s: Scale, draw: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(s.widthPx + s.left * 2, s.heightPx)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
            drawRect(Ink.black, Offset.Zero, size)
            draw()
        }
        return img.asAndroidBitmap()
    }

    private fun lit(b: Bitmap, x: Int, y: Int): Boolean = b.getPixel(x, y) != Ink.black.toArgb()

    private fun columns(b: Bitmap, y0: Int, y1: Int): List<Int> =
        (0 until b.width).filter { x -> (y0.coerceAtLeast(0) until y1.coerceAtMost(b.height)).any { y -> lit(b, x, y) } }

    @Test
    fun noArtDrawsTheTileSquareAlsoWithNoTrack() {
        for (s in listOf(a105, r1)) {
            val l = LockLayout(s)
            val b = image(s) { lockPaint(l, ink(s), null, false) }
            val cx = l.left + l.artX + l.artSide / 2
            val cy = l.artY + l.artSide / 2
            assertEquals("color.tile at the art square (funeOS player/ui/lock.cpp LockPage::render :164-166, v0.4.4)", Ink.tile.toArgb(), b.getPixel(cx, cy))
            assertEquals("the square ends at lockArt", Ink.black.toArgb(), b.getPixel(l.left + l.artX + l.artSide + 1, cy))
        }
    }

    @Test
    fun anotherAppsTrackWithNoArtDrawsTheBluetoothGlyphCentred() {
        for (s in listOf(a105, r1)) {
            val l = LockLayout(s)
            val drawn = image(s) { lockPaint(l, ink(s), null, true) }
            val side = l.artSide
            val c = Offset(l.left + l.artX + side / 2f, l.artY + side / 2f)
            val alone = image(s) { glyph(Glyphs.bluetooth, c, l.btGlyph.toFloat(), Ink.white, 1f) }
            for (y in l.artY until l.artY + side) for (x in l.left + l.artX until l.left + l.artX + side) {
                assertEquals("the glyph alone in the square (funeOS player/ui/lock.cpp LockPage::render :154-159, v0.4.4) at $x,$y", alone.getPixel(x, y), drawn.getPixel(x, y))
            }
            assertTrue("premise: the glyph draws ink", (l.artY until l.artY + side).any { y -> lit(alone, c.x.toInt(), y) })
        }
    }

    @Test
    fun aRightToLeftLineEndsAtTheCanvasEdge() {
        for (s in listOf(a105, r1)) {
            val l = LockLayout(s)
            val rtl = image(s) { lockPaint(l, ink(s, line = "line", right = true, base = Bidi.DIRECTION_RIGHT_TO_LEFT), null, false) }
            val band = l.lineBase - s.heightPx / BAND
            val cols = columns(rtl, band, l.lineBase + PAD)
            assertTrue("premise: the line draws", cols.isNotEmpty())
            val right = l.left + l.textX1
            assertTrue("right aligned at canvasW - edge (funeOS player/ui/lock.cpp LockPage::render :188, v0.4.4): last ink ${cols.max()} against $right", cols.max() in right - SLACK..right + PAD)
            val ltr = image(s) { lockPaint(l, ink(s, line = "line"), null, false) }
            val left = columns(ltr, band, l.lineBase + PAD)
            assertTrue("a left to right line starts at edge: first ink ${left.min()}", left.min() in l.left + l.lineX0 - PAD..l.left + l.lineX0 + SLACK)
        }
    }

    @Test
    fun aLongTitleIsClippedAtTheCanvasEdge() {
        for (s in listOf(a105, r1)) {
            val l = LockLayout(s)
            val b = image(s) { lockPaint(l, ink(s, title = "W".repeat(LONG)), null, false) }
            val square = l.left + l.artX + l.artSide
            val cols = columns(b, l.titleBase - s.heightPx / BAND, l.titleBase + PAD).filter { it > square }
            assertTrue("premise: the title draws", cols.isNotEmpty())
            assertTrue("no ink past canvasW - edge (funeOS player/ui/lock.cpp LockPage::render :169, v0.4.4): ${cols.max()}", cols.max() < l.left + l.textX1)
            assertTrue("no ink left of lockTextX: ${cols.min()}", cols.min() >= l.left + l.textX0)
        }
    }

    @Test
    fun anEmptyClockDrawsNoClockInk() {
        val s = a105
        val l = LockLayout(s)
        val on = image(s) { lockPaint(l, ink(s, clock = "12:00"), null, false) }
        val off = image(s) { lockPaint(l, ink(s), null, false) }
        val y0 = l.clockBase - s.heightPx / BAND
        assertTrue("premise: a clock draws in its band", columns(on, y0, l.clockBase + PAD).isNotEmpty())
        assertTrue("an empty clock text draws none (funeOS player/ui/lock.cpp LockPage::render :147, v0.4.4)", columns(off, y0, l.clockBase + PAD).isEmpty())
        val cols = columns(on, y0, l.clockBase + PAD)
        assertTrue("the clock ends at canvasW - edge: ${cols.max()}", cols.max() in l.left + l.textX1 - SLACK..l.left + l.textX1 + PAD)
    }

    private companion object {
        const val SDK = 35
        const val A105_W = 720
        const val A105_H = 1280
        const val R1_W = 480
        const val R1_H = 800
        const val BAND = 16
        const val PAD = 4
        const val SLACK = 8
        const val LONG = 40
    }
}
