package net.seilent.funekit.kit

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.scale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StatusTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 28, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
    private val box = npStatus(a105)
    private val line = statusLine(a105, box)

    private fun image(draw: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f), draw)
        return img.asAndroidBitmap()
    }

    private fun status(play: Play) = image {
        statusRow(StatusInk(StatusState("", -1, false, play, false, -1, -1), StatusText(null, null, null), line, a105, box), Ink.white, false, 1f)
    }

    private fun alone(g: Glyph, dx: Int) = image {
        glyph(g, Offset((line.right - line.size / 2 + dx).toFloat(), (line.top + line.size / 2).toFloat()), line.size.toFloat(), Ink.white, 1f)
    }

    private fun same(a: Bitmap, b: Bitmap): Boolean {
        for (y in 0 until line.top + line.size * 2) for (x in 0 until a105.widthPx) if (a.getPixel(x, y) != b.getPixel(x, y)) return false
        return true
    }

    private fun nudged(play: Play, g: Glyph) {
        val drawn = status(play)
        assertFalse("premise: the nudge moves the ink", same(alone(g, 0), alone(g, 2)))
        assertFalse("the seek glyph is not drawn at the play slot centre", same(drawn, alone(g, 0)))
        assertTrue(
            "the seek glyph sits 2 px right of the slot centre, (size * 2 + glyphBox / 2) / glyphBox at the A105's status size " +
                "(funeOS player/ui/rows.cpp draw_status :1270-1279, kSeekInkDx :21, v0.4.3)",
            same(drawn, alone(g, 2)),
        )
    }

    @Test
    fun aSeekForwardStateDrawsItsGlyphNudgedRight() = nudged(Play.SeekForward, Glyphs.seekForward)

    @Test
    fun aSeekBackStateDrawsItsGlyphNudgedRight() = nudged(Play.SeekBack, Glyphs.seekBack)

    @Test
    fun thePlayStateDrawsAtTheSlotCentre() {
        assertTrue(same(status(Play.Playing), alone(Glyphs.play, 0)))
    }
}
