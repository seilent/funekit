package net.seilent.funekit.screens.music.nowplaying

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fx
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxU
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Render
import net.seilent.funekit.sheet.Sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LyricsSheetTest {
    private val render = Render(File("build/sheets"))
    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)
    private val none = Insets(0, 0, 0, 0)
    private val lines = listOf("line one", "line two before the current one", "line three runs long enough to wrap", "line four", "line five", "line six", "line seven")

    private fun geo(large: Boolean) = NpLayout(scale(Window(720, 960, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, large))

    private fun ink(g: NpLayout, plain: List<String> = emptyList()) =
        LyricsInk(LyricTable.of(g.s), g, measurer, fonts, density, null, lines, "Song", "Next Song", plain)

    private fun shot(g: NpLayout, block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(g.s.widthPx, g.s.heightPx)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(g.s.widthPx.toFloat(), g.s.heightPx.toFloat())) {
            drawRect(Ink.bg)
            block()
        }
        return img.asAndroidBitmap()
    }

    private fun column(g: NpLayout, from: Int, to: Int, p: Long): Bitmap {
        val k = ink(g)
        return shot(g) { npLyricColumn(g, LyricRows.column(k.t, from, to, p, fx(g.p), lines, "Next Song", k), k, 1f) }
    }

    private fun brightest(b: Bitmap, y0: Int, y1: Int): Int {
        var m = 0
        for (y in y0 until y1) for (x in 0 until b.width) m = maxOf(m, (b.getPixel(x, y) shr 16) and 0xFF)
        return m
    }

    @Test
    fun aBlankPlainLineIsOneEmptyRow() {
        val g = geo(false)
        val k = ink(g, listOf("first", "", "third"))
        assertEquals(listOf(false, true, false), k.plain.map { it == null })
    }

    @Test
    fun aLongPlainCopyShowsTheScrollThumbAtTheRightEdge() {
        val g = geo(false)
        val k = ink(g, List(LONG_PLAIN) { "plain line $it" })
        assertEquals("premise: one row a line", LONG_PLAIN, k.plain.size)
        val p = lyricPlain(g, k.t)
        val m = LyricsModel()
        m.plainRows(p.spec, k.plain.size)
        val bar = checkNotNull(lyricPlainBar(g, p, m))
        assertEquals(px(Tok.canvasW - Tok.scrollbar_width, g.p), bar.x0)
        assertEquals(px(Tok.canvasW, g.p), bar.x1)
        assertEquals("the thumb starts at the text top", px64(fx(Tok.npTextTop), fx(g.p)), bar.y0)
        assertEquals(p.y0, bar.y0)
    }

    @Test
    fun theLyricsColumnOnV1() {
        val g = geo(false)
        val t = LyricTable.of(g.s)
        val rest = column(g, 2, 2, fxInt(1))
        val top = px(Tok.npTextTop, g.p)
        val cur = px(Tok.npTextTop + Tok.npNextPitch, g.p)
        val bg = (Ink.bg.red * 255).roundToInt()
        val low = bg + (255 - bg) * lyricAlpha(fx(Tok.alpha_low)) / 256f
        assertEquals(bg, brightest(rest, 0, top))
        assertTrue(abs(brightest(rest, top, cur) - low) <= 4)
        assertTrue(brightest(rest, cur, px(Tok.npTextTop + Tok.npNextPitch + Tok.npLyricPitch, g.p)) >= 240)
        assertEquals(bg, brightest(rest, px(fxU(t.bottom), g.p), rest.height))
        val mid = column(g, 2, 3, fxInt(1) / 2)
        val large = geo(true)
        val big = column(large, 2, 2, fxInt(1))
        val k = ink(g, listOf("A plain line that runs long enough to wrap", "", "after a blank line"))
        val m = LyricsModel()
        val plain = shot(g) { npLyricPlain(g, m, k, 0, 1f) }
        val notes = listOf("720x960, V1 board at 3 px per u", "funeOS player/ui/nowplaying.cpp draw_column :748-902, draw_plain :977-1008, v0.4.4")
        val tiles = listOf(
            render.tile(FOLDER, "column-rest", "line 2 at rest", notes, rest, emptyList(), g.p),
            render.tile(FOLDER, "column-slide-half", "line 2 to 3 at half the slide", notes, mid, emptyList(), g.p),
            render.tile(FOLDER, "column-large", "line 2 at rest, LARGE", notes, big, emptyList(), large.p),
            render.tile(FOLDER, "plain", "plain lyrics at the top", notes, plain, emptyList(), g.p),
        )
        render.write(Sheet(FOLDER, "column", listOf("Kit lyrics view (funeOS NowPlaying draw_lyrics, v0.4.4)."), tiles))
        render.index(FOLDER, listOf("# Kit lyrics view", "", "- `lyrics/column.png`: the column at rest, at half a slide, in LARGE, and plain lyrics, at V1"))
    }

    companion object {
        private const val FOLDER = "lyrics"
        private const val LONG_PLAIN = 200
    }
}
