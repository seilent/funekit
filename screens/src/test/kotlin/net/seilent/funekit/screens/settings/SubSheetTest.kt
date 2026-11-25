package net.seilent.funekit.screens.settings

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
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Render
import net.seilent.funekit.sheet.Sheet
import org.junit.Assert.assertTrue
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
class SubSheetTest {
    private val render = Render(File("build/sheets"))
    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)
    private val none = Insets(0, 0, 0, 0)
    private val a105 = page(false)
    private val large = page(true)
    private val accent = Accent.forHue(Accent.defaultHue)

    private fun page(large: Boolean) = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, large)

    private fun library() = listOf(
        SettingRow("libraryFolders", "library folders", "1 on"),
        SettingRow("rescan", "rescan library", "1293 songs", SettingKind.Action, shows = listOf("scanning")),
        SettingRow("playThrough", "play through", "off", SettingKind.Choice, sub = "what plays when the queue ends"),
        SettingRow("resume", "resume", "position", SettingKind.Choice, sub = "restores the queue at power on"),
    )

    private fun output() = listOf(
        SettingRow("priority", "priority", "jack", SettingKind.Choice, sub = "jack or headset when both connect"),
        SettingRow("dsdOutput", "dsd output", "dop", SettingKind.Choice, sub = "DoP skips EQ, crossfeed, tube, width"),
    )

    private fun long() = listOf(
        SettingRow("resume", "resume", "position", SettingKind.Choice, sub = LONG_SUB),
    )

    private fun model(rows: List<SettingRow>, p: Int, focus: Int): SettingsModel = SettingsModel(1L, SettingsId(SETTINGS)).also { m ->
        m.pivots = PIVOTS.mapIndexed { i, n -> SettingPivot(n, if (i == p) rows else emptyList()) }
        m.pivot = p
        m.down(focus, 0L)
        m.cancel(0L)
    }

    private fun shot(s: Scale, rows: List<SettingRow>, p: Int, focus: Int): Bitmap {
        val m = model(rows, p, focus)
        val d = settingsDraw(m, s, measurer, fonts, density, covers, accent, false)
        val c: Chrome = settingsChrome(chromeOf(measurer, fonts, s, density, SETTINGS, m.names, covers), HeaderLook.Settings).also { it.select(m.pivot) }
        val bands = groundBands(s.heightPx, false)
        return draw(s) {
            pageGround(bands, accent)
            with(d) { content(m.pivot, m.pane(m.pivot).px) { i -> m.look(m.pivot, i, NOW) } }
            chrome(c, NOW)
        }
    }

    private fun draw(s: Scale, block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(s.widthPx, s.heightPx)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(s.widthPx.toFloat(), s.heightPx.toFloat())) {
            drawRect(Ink.bg)
            block()
        }
        return img.asAndroidBitmap()
    }

    @Test
    fun theSettingsRowsWithTheirSubLines() {
        assertTrue("premise: the large page scale", large.large && !a105.large)
        val notes = listOf("720x1280, A105 board, page scale", NOTE)
        val largeNotes = listOf("720x1280, A105 board, large page scale", NOTE)
        val tiles = listOf(
            render.tile(FOLDER, "subs-library", "the library pivot, sub lines under play through and resume, focus on resume", notes, shot(a105, library(), LIBRARY, RESUME_ROW), emptyList(), a105.stdPpu),
            render.tile(FOLDER, "large-subs-library", "the same at large", largeNotes, shot(large, library(), LIBRARY, RESUME_ROW), emptyList(), large.stdPpu),
            render.tile(FOLDER, "subs-output", "priority and dsd output with their sub lines, focus on priority", notes, shot(a105, output(), OUTPUT, 0), emptyList(), a105.stdPpu),
            render.tile(FOLDER, "subs-long", "a sub longer than the row, on one line clipped at the right edge", notes, shot(a105, long(), LIBRARY, 0), emptyList(), a105.stdPpu),
        )
        render.write(Sheet(FOLDER, "subs", listOf("Kit settings rows with sub lines (funeOS v0.4.4)."), tiles))
        render.index(
            FOLDER,
            listOf(
                "# Kit settings sub lines",
                "",
                "- `settings-subs/subs.png`: the library pivot with its sub rows at A105 and at large, the output sub rows, a clipped long sub",
                "- Not ink gated: the settings row draw records no region",
            ),
        )
    }

    private companion object {
        const val W = 720
        const val H = 1280
        const val SDK = 35
        const val FOLDER = "settings-subs"
        const val OUTPUT = 0
        const val LIBRARY = 2
        const val RESUME_ROW = 3
        const val NOW = 5000L
        const val NOTE = "funeOS player/ui/settings.cpp SettingsPage::draw_row :1929-1970, measure :555-580, v0.4.4"
        const val LONG_SUB = "a sub line that runs on past the right edge of the row in a window narrower than the device"
        val PIVOTS = listOf("output", "effects", "library", "display", "screens", "power", "connect", "system")
    }
}
