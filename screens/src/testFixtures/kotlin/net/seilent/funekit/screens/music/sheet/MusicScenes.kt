package net.seilent.funekit.screens.music.sheet

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.hub.GridRecord
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.hubDraw
import net.seilent.funekit.screens.music.hub.loadArt
import net.seilent.funekit.screens.music.testFake
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Kind
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.Render
import net.seilent.funekit.sheet.Tile
import org.robolectric.RuntimeEnvironment
import java.io.File

class HubRegions(val r: Regions) : DrawRecord by r, GridRecord {
    override fun edges(name: String, kind: String, e: Edges, color: Color, alpha: Float, masks: List<Edges>, peakOff: String?) =
        r.edges(name, Kind.entries.first { it.key == kind }, e, color, alpha, masks, peakOff)

    override fun art(name: String, e: Edges, image: ImageBitmap, alpha: Float, masks: List<Edges>, peakOff: String?) =
        r.edges(name, Kind.Art, e, mean(image), alpha, masks, peakOff)

    private fun mean(image: ImageBitmap): Color {
        val b = image.asAndroidBitmap()
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val n = px.size.toFloat()
        return Color(px.sumOf { android.graphics.Color.red(it) } / n / LEVELS, px.sumOf { android.graphics.Color.green(it) } / n / LEVELS, px.sumOf { android.graphics.Color.blue(it) } / n / LEVELS)
    }

    private companion object {
        const val LEVELS = 255f
    }
}

class MusicScenes(set: FakeSet = FakeSet.Demo, root: File = File(SHEETS)) {
    val fake = testFake(set)
    val source = MusicSource(fake.library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)
    val render = Render(root)
    val density = Density(1f)
    val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)
    val covers = Covers(RuntimeEnvironment.getApplication().resources)
    val r1 = Dev("480x800", "funeOS R1 board", 480, 800, listOf(Nav.None, Nav.Buttons))
    val a105 = Dev("720x1280", "funeOS A105 board", 720, 1280, listOf(Nav.None, Nav.Buttons))
    val v1 = Dev("240x320", "funeOS V1 board", 240, 320, listOf(Nav.None))
    val boards = mapOf(v1 to "v1", r1 to "r1", a105 to "a105")
    val accent: Color = Accent.forHue(Accent.defaultHue)
    private val art = Art(ART_BYTES)

    fun pageScale(dev: Dev, nav: Nav = Nav.None, large: Boolean = false): Scale = scale(dev.window(nav), nav, Surface.Page, large)

    fun regions(dev: Dev, s: Scale, rules: List<Pair<String, String>> = emptyList()): Regions = Regions(boards.getValue(dev), dev.w, dev.h, s, rules)

    fun draw(dev: Dev, block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(dev.w, dev.h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(dev.w.toFloat(), dev.h.toFloat())) {
            drawRect(Ink.bg)
            block()
        }
        return img.asAndroidBitmap()
    }

    fun tile(folder: String, dev: Dev, kind: String, s: Scale, notes: List<String>, rec: Regions?, block: DrawScope.() -> Unit): Tile =
        render.tile(folder, "${boards.getValue(dev)}-$kind", dev.label, listOf(render.facts(dev, s.nav, s)) + notes, draw(dev, block), emptyList(), s.stdPpu, rec)

    fun hub(s: Scale, pivot: Int, accentOff: Boolean = false): Pair<HubModel, HubDraw> = runBlocking {
        val m = HubModel(0L)
        m.select(pivot)
        val d = hubDraw(m, s, measurer, fonts, density, covers, Accent.color(Accent.defaultHue, accentOff), accentOff)
        m.cols = d.geo.grid.cols
        val v = fake.library.view()
        m.load(v, pivot)
        m.songs = v.page(Listing.Songs, 0, 0).total
        val pn = m.panes[pivot]
        pn.track.resize(d.geo.spec(pivot))
        pn.track.count(pn.map?.count ?: 0, 0)
        pn.counted = pn.map
        m to d
    }

    fun fill(m: HubModel, d: HubDraw) = runBlocking {
        val p = m.pivot
        val geo = d.geo
        val t = m.pane.track
        val f = t.drawFirst
        val l = t.rowAt(t.q + geo.view - 1)
        val v = fake.library.view()
        m.show(v, p, f, l + AHEAD)
        m.thumbs(v, p, f, l, 2 * geo.slots(d.thumbX))
        loadArt(m, v, art, d, p, f, l)
    }

    companion object {
        const val SHEETS = "build/sheets"
        const val ART_BYTES = 1 shl 26
        const val AHEAD = 2
    }
}
