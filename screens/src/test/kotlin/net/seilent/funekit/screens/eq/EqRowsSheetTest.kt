package net.seilent.funekit.screens.eq

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.scale
import net.seilent.funekit.screens.settings.HeaderLook
import net.seilent.funekit.screens.settings.settingsChrome
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.Render
import net.seilent.funekit.sheet.Sheet
import net.seilent.funekit.sheet.Tile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EqRowsSheetTest {
    private val render = Render(File("build/sheets"))
    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)
    private val v1 = Dev("240x320", "funeOS V1 board", 240, 320, listOf(Nav.None))
    private val r1 = Dev("480x800", "funeOS R1 board", 480, 800, listOf(Nav.None))
    private val a105 = Dev("720x1280", "funeOS A105 board", 720, 1280, listOf(Nav.None))
    private val boards = listOf(v1 to "v1", r1 to "r1", a105 to "a105")
    private val accent = Accent.forHue(Accent.defaultHue)

    private val parametric = EqPivot(
        HP,
        HEADPHONES,
        listOf(
            EqRow(EqKind.On, ON),
            EqRow(EqKind.Mode, "parametric"),
            EqRow(EqKind.Profile, "custom"),
            EqRow(EqKind.SavePreset),
            EqRow(EqKind.Import),
            EqRow(EqKind.Preamp),
            EqRow(EqKind.Peak, PEAK_ROW),
            EqRow(EqKind.Band, band = 0),
            EqRow(EqKind.Band, band = 1),
            EqRow(EqKind.Band, band = 2),
            EqRow(EqKind.AddBand),
            EqRow(EqKind.RemoveBand),
        ),
        preamp10 = PREAMP,
        bands = listOf(
            EqBand(EqBandType.LowShelf, SHELF_MILLI_HZ, -1, SHELF_GAIN, SHELF_Q),
            EqBand(EqBandType.Peak, eqGridMilliHz(KHZ_POINT), KHZ_POINT, PEAK_GAIN, PEAK_Q),
            EqBand(EqBandType.Off, eqGridMilliHz(HIGH_POINT), HIGH_POINT, 0, PEAK_Q),
        ),
    )

    private val graphic = EqPivot(
        BT,
        BLUETOOTH,
        listOf(EqRow(EqKind.On, ON), EqRow(EqKind.Mode, "graphic"), EqRow(EqKind.Preset, "flat"), EqRow(EqKind.AutoPreamp)) +
            OCTAVES.indices.map { EqRow(EqKind.Gain, band = it) },
        autoPreamp10 = AUTO_PREAMP,
        gains = OCTAVES.mapIndexed { i, g -> EqGain(g, GAINS[i]) },
    )

    private fun shot(dev: Dev, board: String, large: Boolean, shown: Int, edit: EqKind?): Triple<Bitmap, Scale, Regions> {
        val s = scale(dev.window(Nav.None), Nav.None, Surface.Page, large)
        val pane = EqRowsPane(EQ, StillRows(listOf(parametric, graphic)))
        val m = EqModel(1L, EqId(EQ))
        val d = eqDraw(m, s, measurer, fonts, density, covers, accent, false) { pane }
        m.pivots = pane.pivots.value
        m.pivot = shown
        if (edit != null) {
            val i = m.rows(shown).indexOfFirst { (it as EqPaneLine).kind == edit }
            m.down(i, 0L)
            m.cancel(0L)
            m.edit(m.rows(shown)[i].key, false)
        }
        val c = settingsChrome(chromeOf(measurer, fonts, s, density, EQ, m.names, covers, Roles.settingsItem.case, Roles.settingsItem.weight), HeaderLook.Page).also { it.select(shown) }
        val rec = Regions(board, dev.w, dev.h, s)
        val bands = groundBands(dev.h, false)
        val img = ImageBitmap(dev.w, dev.h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(dev.w.toFloat(), dev.h.toFloat())) {
            drawRect(Ink.bg)
            pageGround(bands, accent)
            with(d) { content(m.pivot, m.pane(m.pivot).px) { i -> d.look(m.pivot, i, NOW) } }
            chrome(c, NOW)
            rec.chrome("chrome", c, NOW, dev.w)
        }
        return Triple(img.asAndroidBitmap(), s, rec)
    }

    private fun tile(dev: Dev, board: String, kind: String, caption: String, large: Boolean, shown: Int, edit: EqKind?): Tile {
        val (bmp, s, rec) = shot(dev, board, large, shown, edit)
        val notes = listOf(render.facts(dev, Nav.None, s), NOTE_DRAW, NOTE_TEXT)
        return render.tile(FOLDER, "$board-$kind", "${dev.label}${if (large) " LARGE" else ""}: $caption", notes, bmp, emptyList(), s.stdPpu, rec)
    }

    @Test
    fun theEqRowsOnEveryBoard() {
        val kinds = listOf(
            Triple("eq-parametric", "headphones, parametric, three bands", Pair(0, null)),
            Triple("eq-graphic", "bluetooth, graphic, ten gains", Pair(1, null)),
            Triple("eq-preamp-edit", "headphones, preamp focused and adjusted", Pair(0, EqKind.Preamp)),
        )
        val tiles = boards.flatMap { (dev, board) ->
            kinds.map { (kind, caption, at) -> tile(dev, board, kind, caption, false, at.first, at.second) }
        } + tile(a105, "a105", "eq-parametric-large", "headphones, parametric, band values in two lines", true, 0, null)
        assertEquals("the large band value is type and frequency over gain and q", listOf("ls 105 hz", "+6.4 db q 0.70"), largeBand())
        render.write(Sheet(FOLDER, "rows", listOf("Kit EQ pane rows on the eq host (funeOS v0.4.2 eqpane.cpp draw_row)."), tiles))
        render.index(
            FOLDER,
            listOf(
                "# Kit EQ pane",
                "",
                "- `eq/rows.png`: the parametric, graphic and preamp edit pivots at V1, R1 and A105, and the parametric pivot at A105 LARGE",
                "- Unpaired: the funeOS reference set has no EQ page shot",
            ),
        )
    }

    private fun largeBand(): List<String> {
        val s = scale(a105.window(Nav.None), Nav.None, Surface.Page, true)
        val pane = EqRowsPane(EQ, StillRows(listOf(parametric)))
        val m = EqModel(1L, EqId(EQ))
        val d = eqDraw(m, s, measurer, fonts, density, covers, accent, false) { pane }
        m.pivots = pane.pivots.value
        val line = m.rows(0).first { (it as EqPaneLine).kind == EqKind.Band }
        return (d.ink(line) as EqRowInk).value.map { it.text.lowercase() }
    }

    private companion object {
        const val FOLDER = "eq"
        const val EQ = "eq"
        const val HP = "hp"
        const val BT = "bt"
        const val HEADPHONES = "headphones"
        const val BLUETOOTH = "bluetooth"
        const val ON = "on"
        const val PEAK_ROW = "+1.5 db"
        const val PREAMP = -61
        const val AUTO_PREAMP = -30
        const val SHELF_MILLI_HZ = 105000
        const val SHELF_GAIN = 64
        const val SHELF_Q = 70
        const val KHZ_POINT = 34
        const val HIGH_POINT = 52
        const val PEAK_GAIN = 30
        const val PEAK_Q = 71
        const val NOW = 5000L
        const val NOTE_DRAW = "funeOS player/ui/eqpane.cpp draw_row :599-671, v0.4.2"
        const val NOTE_TEXT = "funeOS player/ui/eqstore.cpp band text :497-514, v0.4.2; the values are the test's"
        val OCTAVES = listOf(4, 10, 16, 22, 28, 34, 40, 46, 52, 58)
        val GAINS = listOf(45, 30, 10, 0, -10, -20, 0, 15, 30, 60)
    }
}
