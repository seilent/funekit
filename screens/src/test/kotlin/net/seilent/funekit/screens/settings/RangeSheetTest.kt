package net.seilent.funekit.screens.settings

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
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
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.menuInk
import net.seilent.funekit.kit.menuLayer
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.host.frameMenu
import net.seilent.funekit.sheet.Render
import net.seilent.funekit.sheet.Sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class RangeSheetTest {
    private val render = Render(File("build/sheets"))
    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val accent = Accent.forHue(Accent.defaultHue)
    private val bands = groundBands(a105.heightPx, false)
    private val hues = (0 until Tok.accent_hueCount.toInt()).map { it * Tok.accent_hueStep.toInt() }
    private val words = listOf(OFF) + hues.map { it.toString() }
    private val at = words.indexOf(Accent.defaultHue.toString())

    private fun display() = listOf(
        SettingRow("brightness", "brightness", "55%", SettingKind.Steps),
        SettingRow("accent", "accent", "", SettingKind.Range, values = words, words = words, at = at, focus = at, swatch = accent, swatches = listOf(Ink.white) + hues.map { Accent.forHue(it) }),
        SettingRow("clock", "clock", "24h", SettingKind.Choice),
        SettingRow("batteryPercent", "battery percent", "off", SettingKind.Toggle),
        SettingRow("statusVolume", "status volume", "off", SettingKind.Toggle),
    )

    private fun model(rows: List<SettingRow>, p: Int): SettingsModel = SettingsModel(1L, SettingsId(SETTINGS)).also { m ->
        m.pivots = PIVOTS.mapIndexed { i, n -> SettingPivot(n, if (i == p) rows else emptyList()) }
        m.pivot = p
    }

    private fun chromeFor(m: SettingsModel): Chrome =
        settingsChrome(chromeOf(measurer, fonts, a105, density, SETTINGS, m.names, covers), HeaderLook.Settings).also { it.select(m.pivot) }

    private fun shot(block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(a105.widthPx, a105.heightPx)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(a105.widthPx.toFloat(), a105.heightPx.toFloat())) {
            drawRect(Ink.bg)
            block()
        }
        return img.asAndroidBitmap()
    }

    private fun DrawScope.page(m: SettingsModel, d: SettingsDraw, c: Chrome) {
        pageGround(bands, accent)
        with(d) { content(m.pivot, m.pane(m.pivot).px) { i -> m.look(m.pivot, i, NOW) } }
        chrome(c, NOW)
    }

    @Test
    fun theAccentRowAndItsSwatchSheet() {
        val m = model(display(), DISPLAY)
        val d = settingsDraw(m, a105, measurer, fonts, density, covers, accent, false)
        val c = chromeFor(m)
        val rows = shot { page(m, d, c) }
        m.down(ACCENT_ROW, 0L)
        val row = requireNotNull(m.tap(ACCENT_ROW, 0L))
        val sheet = valueSheet(row)
        assertEquals("the sheet lists off then the hues", words, sheet.items)
        assertEquals(sheet.items.size, sheet.swatches.size)
        val menu = Menu()
        menu.open(sheet.form, sheet.title, sheet.items, 0, sheet.readOnly, sheet.mixedRow, lead = sheet.lead, confirm = sheet.confirm)
        menu.value(sheet.focus, sheet.mark, sheet.wrap, 0)
        menu.swatches(sheet.swatches)
        frameMenu(menu, a105, sheet.lift) { null }
        val ink = menuInk(measurer, fonts, menu, a105, density, false, covers)
        val plan = menu.plan(ink).also { menu.settle(it) }
        m.edit(row.key, sheet.lift)
        assertFalse("the row would rise past its own top, so it stays (v0.4.2 lift_focus)", m.lift(px(a105.board.page_contentTop, a105.ppu), menu.panelTop - px(Tok.gutter, a105.ppu), menu.kept))
        val o = Overlay(OverKind.Sheet).also { it.open(0) }
        val open = shot {
            page(m, d, c)
            menuLayer(menu, ink, plan, o, a105, NOW, accent, false, Paint())
        }
        val notes = listOf(
            "720x1280, A105 board, page scale",
            "funeOS player/ui/settings.cpp build :359-372, :449-454, draw_row :1771-1786, v0.4.2",
            "funeOS player/ui/app.cpp value_menu :11935-11955, player/ui/overlay.cpp :366-391, player/ui/settings.cpp lift_focus :725-738, v0.4.2",
        )
        val tiles = listOf(
            render.tile(FOLDER, "accent-display", "the display pivot at A105 defaults, the accent swatch at hue 350", notes, rows, emptyList(), a105.stdPpu),
            render.tile(FOLDER, "accent-sheet", "the accent value sheet, off then hues 0 to 350, focus and mark on 350", notes, open, emptyList(), a105.stdPpu),
        )
        render.write(Sheet(FOLDER, "accent", listOf("Kit settings accent row and swatch sheet (funeOS v0.4.2)."), tiles))
        render.index(
            FOLDER,
            listOf(
                "# Kit settings range rows",
                "",
                "- `settings/accent.png`: the display pivot with the accent swatch, then its value sheet, at A105",
                "- Unpaired: the funeOS reference set has no display pivot, accent sheet or adjust HUD shot",
            ),
        )
    }

    private companion object {
        const val W = 720
        const val H = 1280
        const val SDK = 35
        const val FOLDER = "settings"
        const val OFF = "off"
        const val DISPLAY = 3
        const val ACCENT_ROW = 1
        const val NOW = 5000L
        val PIVOTS = listOf("output", "effects", "library", "display", "screens", "power", "connect", "system")
    }
}
