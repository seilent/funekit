package net.seilent.funekit.screens.music.nowplaying

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
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
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.api.Row
import org.junit.Assert.assertEquals
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
class NowPlayingDrawTest {
    private val none = Insets(0, 0, 0, 0)
    private val g = NpLayout(scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false))

    private fun draw(block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) {
            drawRect(Color.Black, Offset.Zero, size)
            block()
        }
        return img.asAndroidBitmap()
    }

    private fun red(b: Bitmap, x: Int, y: Int) = (b.getPixel(x, y) shr 16) and 0xFF

    private fun glyphRed(b: Bitmap, i: Int): Int {
        val h = g.ringD / 4
        var m = 0
        for (y in g.ringCy - h..g.ringCy + h) for (x in g.ringCx(i) - h..g.ringCx(i) + h) m = maxOf(m, red(b, x, y))
        return m
    }

    private fun model(state: PlayerState = PlayerState(queueSize = 1, index = 0, track = 1)): NowPlayingModel {
        val m = NowPlayingModel()
        m.feed(state)
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000, album = "Album"))), 0)
        m.resetFocus(0)
        return m
    }

    private fun rings(m: NowPlayingModel, hide: Boolean = false) = draw { npRings(g, npRingsLook(m, 0, hide), 1f, null) }

    @Test
    fun theFocusedRingTakesTheFocusedStroke() {
        val m = model()
        val focused = rings(m)
        val hidden = rings(m, hide = true)
        val x = g.ringCx(0) - 35
        assertTrue(red(focused, x, g.ringCy) > 0)
        assertEquals(0, red(hidden, x, g.ringCy))
    }

    @Test
    fun aLitRingDrawsItsGlyphAtFullAlphaAndAnUnlitOneAtMidWithTheRingAtTheIdleAlpha() {
        val off = rings(model())
        val on = rings(model(PlayerState(queueSize = 1, index = 0, track = 1, shuffle = true)))
        val y = g.ringCy
        val x = g.ringCx(0) + g.ringD / 2 - g.ringStroke / 2
        assertTrue("ring ink at ($x, $y)", red(off, x, y) > 0)
        assertEquals(red(off, x, y), red(on, x, y))
        assertTrue(glyphRed(on, 0) > glyphRed(off, 0))
    }

    @Test
    fun repeatOneDrawsTheArcAndTheDigitInsteadOfTheRing() {
        val all = rings(model(PlayerState(queueSize = 1, index = 0, track = 1, repeat = Repeat.All)))
        val one = rings(model(PlayerState(queueSize = 1, index = 0, track = 1, repeat = Repeat.One)))
        assertFalse(all.sameAs(one))
    }

    @Test
    fun theHeartRingIsLitForAFavorite() {
        val m = model()
        val off = rings(m)
        m.favorite = true
        val on = rings(m)
        val x = g.ringCx(2) + g.ringD / 2 - g.ringStroke / 2
        assertTrue("ring ink at ($x, ${g.ringCy})", red(off, x, g.ringCy) > 0)
        assertEquals(red(off, x, g.ringCy), red(on, x, g.ringCy))
        assertTrue(glyphRed(on, 2) > glyphRed(off, 2))
    }

    @Test
    fun theMarkRingDrawsOnlyWithLyrics() {
        val m = model()
        val off = draw { npMark(g, npMarkLook(m, 0), 1f, null) }
        m.mark = true
        val on = draw { npMark(g, npMarkLook(m, 0), 1f, null) }
        val x = g.markX + g.ringD / 2 - 2
        assertEquals(0, red(off, x, g.markY))
        assertTrue(red(on, x, g.markY) > 0)
    }

    @Test
    fun theBackRingTakesTheIdleStrokeWhileTheFocusIsHidden() {
        val m = model()
        val shown = draw { npBackRing(g, npBackLook(m, 0, false), 1f, null) }
        val hidden = draw { npBackRing(g, npBackLook(m, 0, true), 1f, null) }
        val y = g.backCy
        val x = g.backCx + g.backSize / 2 - 14
        assertTrue(red(shown, x, y) > 0)
        assertEquals(0, red(hidden, x, y))
    }
}
