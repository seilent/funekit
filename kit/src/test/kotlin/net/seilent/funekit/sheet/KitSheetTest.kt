package net.seilent.funekit.sheet

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowKind
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.letterTile
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.pressFrame
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import net.seilent.funekit.kit.Ink as KitInk

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class KitSheetTest {
    private val render = Render(File("build/sheets"))
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)
    private val v1 = Dev("240x320", "funeOS V1 board", 240, 320, listOf(Nav.None))
    private val r1 = Dev("480x800", "funeOS R1 board", 480, 800, listOf(Nav.None))
    private val a105 = Dev("720x1280", "funeOS A105 board", 720, 1280, listOf(Nav.None))
    private val boardOf = mapOf(v1 to "v1", r1 to "r1", a105 to "a105")
    private val accent = Accent.forHue(Accent.defaultHue)

    private fun draw(dev: Dev, large: Boolean, block: DrawScope.(Scale, Regions) -> Unit): Triple<Bitmap, Scale, Regions> {
        val s = scale(dev.window(Nav.None), Nav.None, Surface.Page, large)
        val bands = groundBands(dev.h, false)
        val regions = Regions(boardOf.getValue(dev), dev.w, dev.h, s).also { it.residualWhy = PAGE_QUIET }
        val img = ImageBitmap(dev.w, dev.h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(dev.w.toFloat(), dev.h.toFloat())) {
            pageGround(bands, accent)
            block(s, regions)
        }
        return Triple(img.asAndroidBitmap(), s, regions)
    }

    private fun DrawScope.hub(s: Scale, p: Page, rec: Regions, headerLit: Boolean = false) {
        val w = size.width.toInt()
        val c = chromeOf(measurer, fonts, s, unitDensity, p.header, p.pivots, covers).also {
            it.select(p.active)
            if (headerLit) it.press(Chrome.HEADER)
        }
        chrome(c, 0L)
        rec.chrome("chrome", c, 0L, w)
        val glow = Glow().also { if (p.pressed >= 0) it.down(p.pressed, 0L) }
        var y = px(s.board.page_contentTop, s.ppu)
        p.rows.forEachIndexed { i, r ->
            val focused = i == p.focused
            val pressed = i == p.pressed
            val ink = rowInk(measurer, fonts, r, s, unitDensity, false, null)
            val alpha = if (focused) Tok.alpha_full else Tok.type_listPrimary_alphaDim
            val press = if (pressed) Tok.motion_pressScale else 1f
            translate(0f, y.toFloat()) {
                listRow(ink, s, alpha, focused, press, accent, false, if (pressed) RowGlow(glow, glow.level(Tok.motion_pressGlow.toLong())) else null, focusShown = true)
            }
            rec.move(0f, y.toFloat()) { rec.row("row$i", ink, s, w, alpha, focused, press, pressed, accent, false, focusShown = true) }
            y += Rows.pitch(s, r.collection)
        }
    }

    private fun page(dev: Dev, large: Boolean, p: Page) = draw(dev, large) { s, rec -> hub(s, p, rec) }

    private fun letters(dev: Dev) = draw(dev, false) { s, rec ->
        val side = px(Tok.albcolLetter, s.ppu)
        val step = px(Tok.albcolLetter + Tok.gridGap, s.ppu)
        val x0 = px(Tok.hubEdge, s.ppu)
        val y0 = px(s.board.page_contentTop, s.ppu)
        val looks = listOf(Triple(Tok.letterTileBorder, Tok.letterTileBorderAlpha, false), Triple(Tok.letterTileBorderFocused, Tok.alpha_full, false), Triple(Tok.letterTileBorder, Tok.letterTileBorderAlpha, true))
        val ink = rowInk(measurer, fonts, RowModel(kind = RowKind.Divider, primary = "b"), s, unitDensity, false, null).primary
        looks.forEachIndexed { i, (border, alpha, frame) ->
            val t = Edges(x0 + i * step, y0, x0 + i * step + side, y0 + side)
            letterTile(t, px(border, s.ppu), alpha)
            rec.edges("letter$i", Kind.Tile, t, KitInk.white, alpha)
            if (frame) {
                pressFrame(t, s)
                val parts = Rows.pressFrame(t, s)
                rec.add("letter$i.frame", Kind.Tile, Box(parts.minOf { it.x0 }.toFloat(), parts.minOf { it.y0 }.toFloat(), parts.maxOf { it.x1 }.toFloat(), parts.maxOf { it.y1 }.toFloat()), KitInk.white, 1f)
            }
            val x = Rows.letterRight(t, s) - ink.width
            val base = Rows.letterBaseline(t, s).toFloat()
            word(ink, x, base, KitInk.white, Roles.letterGlyph.alpha)
            rec.word("letter$i.text", ink, x, base, Roles.letterGlyph.alpha)
        }
    }

    @Test
    fun kitRowsChromeAndLetterTiles() {
        val f = "kit"
        val boards = listOf(v1 to false, r1 to false, a105 to false, a105 to true)
        for (p in KitPages.pages) {
            val tiles = boards.map { (dev, large) ->
                val (bmp, s, rec) = page(dev, large, p)
                val board = boardOf.getValue(dev)
                val id = if (!large && p.kind != null) "$board-${p.kind}" else "${dev.id}${if (large) "-large" else ""}-${p.id}"
                render.tile(f, id, "${dev.label}${if (large) " LARGE" else ""}", listOf(render.facts(dev, Nav.None, s), p.note), bmp, emptyList(), s.stdPpu, rec)
            }
            render.write(Sheet(f, p.id, listOf("Kit rows, page header word and pivots (funeOS ui/rows.cpp draw_list_row, Chrome::draw)."), tiles))
        }
        val letterTiles = listOf(v1, r1, a105).map { dev ->
            val (bmp, s, rec) = letters(dev)
            render.tile(f, "${dev.id}-letters", dev.label, listOf("albcolLetter tiles: idle, focused, F15 press frame"), bmp, emptyList(), s.stdPpu, rec)
        }
        render.write(Sheet(f, "letters", listOf("Letter tiles (funeOS ui/rows.cpp draw_letter_tile, draw_press_frame)."), letterTiles))
        render.index(f, listOf(
            "# Kit rows and chrome", "",
            "- `kit/bt-headphones.png`: V1, R1, A105 and A105 LARGE; the V1, R1 and A105 tiles are `<board>-rowpress-0-btarc-held`, paired with the funeOS shot of that name",
            "- `kit/forms.png`: the other row forms",
            "- `kit/letters.png`: letter tiles idle, focused and with the F15 press frame at V1, R1 and A105",
            "- the hub scenes (hub, rowpress-0-hubsongs-held, jump, toast, thumbfade, fling) are `app/build/sheets/music/` (HubSheetTest)",
            "- every tile has `<tile>.regions.json` beside it for `tools/inkband.py`",
        ))
    }

    companion object {
        private const val PAGE_QUIET = "the funeOS page draws fixture rows past the kit page"
    }
}
