package net.seilent.funekit.kit.stack

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.ring
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Regions
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
class DrawRecordTest {
    private val dev = Dev("720x1280", "A105", 720, 1280, listOf(Nav.None))
    private val s = scale(dev.window(Nav.None), Nav.None, Surface.Page, false)

    private class Ink4(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    private fun DrawScope.scene(rec: DrawRecord?, clipY: Float) {
        layer(rec, ALPHA, Paint()) {
            shifted(rec, DX, DY) {
                zoomed(rec, ZOOM, OX, OY) {
                    clipped(rec, 0f, 0f, size.width, clipY) {
                        ring(C, D, STROKE, Ink.white, 1f)
                        rec?.ring("ring", C, D, 1f)
                    }
                }
            }
        }
    }

    private fun draw(rec: DrawRecord?, clipY: Float): Ink4 {
        val img = ImageBitmap(dev.w, dev.h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(dev.w.toFloat(), dev.h.toFloat())) { scene(rec, clipY) }
        val b = img.asAndroidBitmap()
        var x0 = dev.w
        var y0 = dev.h
        var x1 = 0
        var y1 = 0
        for (y in 0 until dev.h) for (x in 0 until dev.w) {
            if (b.getPixel(x, y) ushr ALPHA_SHIFT != 0) {
                x0 = minOf(x0, x)
                y0 = minOf(y0, y)
                x1 = maxOf(x1, x + 1)
                y1 = maxOf(y1, y + 1)
            }
        }
        return Ink4(x0, y0, x1, y1)
    }

    @Test
    fun aRecordedRingBoxIsTheDrawnBoxUnderTranslateScaleClipAndLayerAlpha() {
        for (clipY in listOf(dev.h.toFloat(), CUT)) {
            val rec = Regions("a105", dev.w, dev.h, s)
            val ink = draw(rec, clipY)
            assertEquals(1, rec.count)
            val r = rec.regions.single()
            assertEquals(ALPHA, r.alpha, 1e-6f)
            assertEquals(ink.x0.toFloat(), r.box.x0, 1f)
            assertEquals(ink.y0.toFloat(), r.box.y0, 1f)
            assertEquals(ink.x1.toFloat(), r.box.x1, 1f)
            assertEquals(ink.y1.toFloat(), r.box.y1, 1f)
        }
    }

    @Test
    fun aNullRecorderDrawsTheSameAndRecordsNothing() {
        val rec = Regions("a105", dev.w, dev.h, s)
        val with = draw(rec, CUT)
        val none = draw(null, CUT)
        assertEquals(with.x0, none.x0)
        assertEquals(with.y0, none.y0)
        assertEquals(with.x1, none.x1)
        assertEquals(with.y1, none.y1)
        assertTrue(none.y1 > none.y0)
        assertEquals(1, rec.count)
    }

    companion object {
        private const val ALPHA = 0.5f
        private const val ALPHA_SHIFT = 24
        private const val DX = 37f
        private const val DY = 51f
        private const val ZOOM = 0.8f
        private const val OX = 360f
        private const val OY = 640f
        private const val D = 120f
        private const val STROKE = 6f
        private const val CUT = 520f
        private val C = Offset(200f, 500f)
    }
}
