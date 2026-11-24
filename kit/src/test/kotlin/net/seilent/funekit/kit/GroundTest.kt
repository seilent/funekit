package net.seilent.funekit.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GroundTest {
    private val unitDensity = Density(1f)
    private val accent = Accent.forHue(Accent.defaultHue)
    private val heights = listOf(1280, 800, 320, 2160)
    private val moves = listOf(0f to 0f, 1f to 0f, 0f to 1f, 0.3f to 0.6f, 1f to 1f)

    private fun ops(h: Int, draw: DrawScope.() -> Unit): List<String> {
        val calls = mutableListOf<String>()
        val canvas = Proxy.newProxyInstance(Canvas::class.java.classLoader, arrayOf(Canvas::class.java), InvocationHandler { _, m, _ ->
            when (m.name) {
                "hashCode" -> 0
                "equals" -> false
                "toString" -> "ops"
                else -> {
                    if (m.name.startsWith("draw")) calls += m.name
                    null
                }
            }
        }) as Canvas
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, canvas, Size(4f, h.toFloat())) { draw() }
        return calls
    }

    private fun pixels(h: Int, draw: DrawScope.() -> Unit): IntArray {
        val img = ImageBitmap(4, h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(4f, h.toFloat())) {
            drawRect(Ink.white, Offset.Zero, size)
            draw()
        }
        return IntArray(4 * h).also { img.readPixels(it) }
    }

    private fun DrawScope.before(bands: List<Band>, accent: Color) {
        drawRect(Ink.bg, Offset.Zero, size)
        val over = accent.toArgb().toLong() and 0xFFFFFFFFL
        for (b in bands) {
            if (b.level > 0) drawRect(Color(blendPx(Tok.color_bg, over, b.level) or 0xFF000000L), Offset(0f, b.y0.toFloat()), Size(size.width, (b.y1 - b.y0).toFloat()))
        }
    }

    private fun DrawScope.before(bands: List<Band>, accent: Color, leave: Float, arrive: Float) {
        drawRect(Ink.bg, Offset.Zero, size)
        val over = accent.toArgb().toLong() and 0xFFFFFFFFL
        val la = (leave * 256).roundToInt()
        val aa = (arrive * 256).roundToInt()
        for (b in bands) {
            if (b.level <= 0) continue
            val g = blendPx(Tok.color_bg, over, b.level)
            drawRect(Color(blendPx(blendPx(Tok.color_bg, g, la), g, aa) or 0xFF000000L), Offset(0f, b.y0.toFloat()), Size(size.width, (b.y1 - b.y0).toFloat()))
        }
    }

    @Test
    fun theTintStepsDownFromThirtyEightOver256ToNoneAtTheBottom() {
        val b = groundBands(1280, false)
        assertEquals(38, b.size)
        assertEquals(listOf(0, 34, 38), listOf(b.first().y0, b.first().y1, b.first().level))
        assertEquals(listOf(1247, 1280, 1), listOf(b.last().y0, b.last().y1, b.last().level))
        assertEquals(1280, b.sumOf { it.y1 - it.y0 })
    }

    @Test
    fun theGroundRowsAreFuneOsBlendPxAsTheHubShotHasThem() {
        val over = Accent.forHue(Accent.defaultHue).toArgb().toLong() and 0xFFFFFFFFL
        val b = groundBands(1280, false)
        fun at(y: Int) = blendPx(Tok.color_bg, over, b.first { y in it.y0 until it.y1 }.level)
        assertEquals(0x331419L, at(0))
        assertEquals(0x221215L, at(640))
        assertEquals(0x111111L, at(1279))
    }

    @Test
    fun accentOffIsTheFlatGround() {
        val b = groundBands(1280, true)
        assertEquals(1, b.size)
        assertEquals(0, b.single().level)
    }

    @Test
    fun aFlatGroundRecordsNoDrawOpInEitherForm() {
        for (h in heights) {
            val flat = groundBands(h, true)
            assertEquals(emptyList<String>(), ops(h) { pageGround(flat, accent) })
            for ((l, a) in moves) assertEquals(emptyList<String>(), ops(h) { pageGround(flat, accent, l, a) })
        }
    }

    @Test
    fun aTintedGroundDrawsAsBeforeInBothForms() {
        for (h in heights) {
            val bands = groundBands(h, false)
            assertTrue(ops(h) { pageGround(bands, accent) }.isNotEmpty())
            assertArrayEquals("height $h", pixels(h) { before(bands, accent) }, pixels(h) { pageGround(bands, accent) })
            for ((l, a) in moves) {
                assertArrayEquals("height $h move $l $a", pixels(h) { before(bands, accent, l, a) }, pixels(h) { pageGround(bands, accent, l, a) })
            }
        }
    }

    @Test
    fun theHostGroundFillsTheCanvasWithTheBackground() {
        for (h in heights) {
            val px = pixels(h) { hostGround() }
            for (i in px.indices) assertEquals("height $h px $i", Ink.bg.toArgb(), px[i])
            assertEquals("height $h", listOf("drawRect"), ops(h) { hostGround() })
        }
    }
}
