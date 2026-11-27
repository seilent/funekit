package net.seilent.funekit.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlyphTest {
    private sealed interface Op

    private data class Move(val kind: String, val x: Float, val y: Float) : Op

    private class Drawn(val path: Path, val style: PaintingStyle, val width: Float, val miter: Float, val cap: StrokeCap, val join: StrokeJoin) : Op

    private class Rec(private val c: Canvas) : Canvas by c {
        val ops = ArrayList<Op>()

        override fun translate(dx: Float, dy: Float) {
            ops += Move("translate", dx, dy)
            c.translate(dx, dy)
        }

        override fun scale(sx: Float, sy: Float) {
            ops += Move("scale", sx, sy)
            c.scale(sx, sy)
        }

        override fun rotate(degrees: Float) {
            ops += Move("rotate", degrees, 0f)
            c.rotate(degrees)
        }

        override fun skew(sx: Float, sy: Float) {
            ops += Move("skew", sx, sy)
            c.skew(sx, sy)
        }

        override fun concat(matrix: Matrix) {
            ops += Move("concat", 0f, 0f)
            c.concat(matrix)
        }

        override fun drawPath(path: Path, paint: Paint) {
            ops += Drawn(path, paint.style, paint.strokeWidth, paint.strokeMiterLimit, paint.strokeCap, paint.strokeJoin)
            c.drawPath(path, paint)
        }
    }

    private val at = Offset(500f, 500f)

    private fun draw(g: Glyph, slot: Float, scale: Float = 1f, c: Offset = at): List<Op> {
        val rec = Rec(Canvas(ImageBitmap(1000, 1000)))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, rec, Size(1000f, 1000f)) {
            glyph(g, c, slot, Ink.white, 1f, scale)
        }
        return rec.ops
    }

    private fun drawn(ops: List<Op>) = ops.filterIsInstance<Drawn>().single()

    private fun bounds(g: Glyph, slot: Float, l: Float, t: Float, r: Float, b: Float, c: Offset = at) {
        val ops = draw(g, slot, c = c)
        val before = ops.takeWhile { it !is Drawn }.map { it as Move }
        val what = "${g.name} at $slot about $c: $before"
        assertTrue(what, before.isNotEmpty() && before.all { it.kind == "translate" })
        assertTrue(what, before.all { it.x == floor(it.x) && it.y == floor(it.y) })
        val dx = before.sumOf { it.x.toDouble() }.toFloat()
        val dy = before.sumOf { it.y.toDouble() }.toFloat()
        val p = drawn(ops).path.getBounds()
        assertEquals(what, l, p.left + dx, 0.01f)
        assertEquals(what, t, p.top + dy, 0.01f)
        assertEquals(what, r, p.right + dx, 0.01f)
        assertEquals(what, b, p.bottom + dy, 0.01f)
    }

    private fun stroke(slot: Float, width: Float) {
        val d = drawn(draw(Glyphs.back, slot))
        assertEquals(PaintingStyle.Stroke, d.style)
        assertEquals(width, d.width, 0f)
        assertEquals(4f, d.miter, 0f)
        assertEquals(StrokeCap.Butt, d.cap)
        assertEquals(StrokeJoin.Miter, d.join)
    }

    @Test
    fun aGlyphIsDrawnAtItsWholeBoxCornerWithItsPathInPixels() {
        bounds(Glyphs.play, 192f, 458f, 434f, 566f, 566f)
        bounds(Glyphs.back, 192f, 434f, 446f, 572f, 554f)
        bounds(Glyphs.skipNext, 207f, 407.99687f, 448.75f, 593.00311f, 552.25f)
        bounds(Glyphs.back, 191f, 434.84375f, 446.78125f, 572.125f, 554.21875f)
        bounds(Glyphs.play, 95.5f, 479.609375f, 467.671875f, 533.328125f, 533.328125f)
        bounds(Glyphs.back, 192f, 434f, 446f, 572f, 554f, Offset(500.5f, 500.5f))
    }

    @Test
    fun aStrokeGlyphIsStrokedAtItsPixelWidthAndAFillGlyphIsFilled() {
        stroke(192f, 24f)
        stroke(191f, 23.875f)
        assertEquals(PaintingStyle.Fill, drawn(draw(Glyphs.play, 192f)).style)
    }

    @Test
    fun aGlyphPathIsBuiltOncePerSlotAndAPressScalesItAboutTheBoxMiddle() {
        val b0 = GlyphCache.builds
        val first = drawn(draw(Glyphs.back, 190f)).path
        val second = drawn(draw(Glyphs.back, 190f)).path
        assertEquals(1, GlyphCache.builds - b0)
        assertSame(first, second)
        val press = draw(Glyphs.back, 190f, 0.96f)
        assertEquals(1, GlyphCache.builds - b0)
        assertSame(first, drawn(press).path)
        val moves = listOf(Move("translate", 405f, 405f), Move("translate", 95f, 95f), Move("scale", 0.96f, 0.96f), Move("translate", -95f, -95f))
        assertEquals(moves, press.takeWhile { it !is Drawn })
    }
}
