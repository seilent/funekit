package net.seilent.funekit.sheet

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.RowKind
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class BorderGateSheetTest {
    private val root = File("build/bordergate")
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val dev = Dev("720x1280", "funeOS A105 board", 720, 1280, listOf(Nav.None))
    private val s = scale(dev.window(Nav.None), Nav.None, Surface.Page, false)
    private val divider = rowInk(measurer, fonts, RowModel(kind = RowKind.Divider, primary = "#"), s, unitDensity, false, null)
    private val top = px(s.board.page_contentTop, s.ppu).toFloat()

    private fun drawn(focused: Boolean, rec: Regions?): Bitmap {
        val img = ImageBitmap(dev.w, dev.h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(dev.w.toFloat(), dev.h.toFloat())) {
            drawRect(KitInk.bg, Offset.Zero, size)
            translate(0f, top) { listRow(divider, s, Tok.alpha_full, focused, focusShown = true) }
        }
        rec?.move(0f, top) { rec.row("row0", divider, s, dev.w, Tok.alpha_full, focused, 1f, false, KitInk.white, false, focusShown = true) }
        return img.asAndroidBitmap()
    }

    private fun gate(tileFocused: Boolean): Pair<Int, List<String>> {
        val dir = File(root, if (tileFocused) "focused" else "idle")
        dir.deleteRecursively()
        val ref = File(dir, "ref/a105").also { it.mkdirs() }
        File(ref, "$KIND.png").outputStream().use { drawn(true, null).compress(Bitmap.CompressFormat.PNG, Render.PNG_QUALITY, it) }
        val rec = Regions("a105", dev.w, dev.h, s)
        Render(dir).tile(FOLDER, "a105-$KIND", KIND, emptyList(), drawn(tileFocused, rec), emptyList(), s.stdPpu, rec)
        val report = File(dir, "inkband.txt")
        val run = ProcessBuilder(
            "python3", File("../tools/inkband.py").path, "--ref", File(dir, "ref").path,
            "--tokens", File("../tokens/funeos/player/ui/tokens.txt").path, "--report", report.path, File(dir, FOLDER).path,
        ).redirectErrorStream(true).start()
        val out = run.inputStream.bufferedReader().readText()
        val rc = run.waitFor()
        assertTrue(out, report.exists())
        return rc to report.readLines().filter { it.startsWith("a105/$KIND") }
    }

    private fun result(rows: List<String>, region: String): String? =
        rows.map { it.split(Regex("\\s+")) }.firstOrNull { it.size > RESULT && it[1] == region }?.get(RESULT)

    @Test
    fun anIdleBorderAgainstAFocusedReferenceMissesOnEveryStrip() {
        val (rc, rows) = gate(false)
        val all = rows.joinToString("\n")
        assertEquals(all, 1, rc)
        for (side in SIDES) assertEquals(all, "MISS", result(rows, "row0.tile.$side"))
    }

    @Test
    fun aFocusedBorderAgainstAFocusedReferencePassesOnEveryStrip() {
        val (rc, rows) = gate(true)
        val all = rows.joinToString("\n")
        assertEquals(all, 0, rc)
        for (side in SIDES) assertEquals(all, "ok", result(rows, "row0.tile.$side"))
    }

    companion object {
        private const val FOLDER = "probe"
        private const val KIND = "divider"
        private const val RESULT = 3
        private val SIDES = listOf("top", "bottom", "left", "right")
    }
}
