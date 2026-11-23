package net.seilent.funekit.kit

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class StatusBatteryTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 28, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)

    private fun draw(battery: Int, play: Play = Play.None): Bitmap {
        val box = npStatus(a105)
        val ink = StatusInk(StatusState("", battery, false, play, false, -1, -1), StatusText(null, null, null), statusLine(a105, box), a105, box)
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) { statusRow(ink, Ink.white, false, 1f) }
        return img.asAndroidBitmap()
    }

    private fun on(b: Bitmap, x: Int, y: Int) = b.getPixel(x, y) ushr 24 > 0

    @Test
    fun theBatteryNubSitsLeftOfTheBodyWithItsStepsAndTheLevelFillsFromTheRight() {
        val b = draw(50)
        val ink = listOf(625 to 35, 629 to 30, 629 to 40, 626 to 29, 626 to 41, 627 to 28, 627 to 42, 630 to 24, 678 to 46, 630 to 35, 674 to 35, 655 to 30, 671 to 40)
        val bare = listOf(624 to 35, 625 to 29, 625 to 41, 626 to 28, 626 to 42, 627 to 27, 627 to 43, 654 to 35, 636 to 35, 673 to 35, 679 to 35, 630 to 23)
        for ((x, y) in ink) assertEquals("ink at $x, $y", true, on(b, x, y))
        for ((x, y) in bare) assertEquals("no ink at $x, $y", false, on(b, x, y))
    }

    @Test
    fun thePlayGlyphTakesTheSlotOneGapLeftOfTheBattery() {
        val line = statusLine(a105, npStatus(a105))
        val bare = draw(50)
        val playing = draw(50, Play.Playing)
        var battery = a105.widthPx
        var x0 = a105.widthPx
        var x1 = -1
        for (y in line.top until line.top + line.size) for (x in 0 until a105.widthPx) {
            if (on(bare, x, y)) battery = minOf(battery, x)
            if (playing.getPixel(x, y) != bare.getPixel(x, y)) {
                x0 = minOf(x0, x)
                x1 = maxOf(x1, x)
            }
        }
        assertEquals("the gap is the board's", px(a105.board.npStatusGap, a105.ppu), line.gap)
        assertTrue("premise: the play glyph draws", x1 >= 0)
        assertTrue("the play glyph ends one gap left of the battery, $x1 against ${battery - line.gap}", x1 < battery - line.gap)
        assertTrue("the play glyph keeps to its slot, $x0", x0 >= battery - line.gap - line.size)
    }
}
