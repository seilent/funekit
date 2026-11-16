package net.seilent.funekit.sheet

import androidx.compose.ui.geometry.Offset
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
import net.seilent.funekit.Surface
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.home.HomeEntry
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.home.homeInk
import net.seilent.funekit.kit.home.homeMenu
import net.seilent.funekit.kit.home.homeRingRect
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.stack.DrawRecord
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

private enum class FuneHome(override val word: String, override val sub: String) : HomeEntry {
    Music("music", "albums artists playlists songs genres"),
    Bluetooth("bluetooth", "not connected"),
    Playlists("playlists", "3 playlists"),
    History("history", "recent new"),
    Apps("apps", "no apps"),
    Settings("settings", "output library display power"),
    ;

    override val ring: Boolean get() = this == Music
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HomeSheetTest {
    private val render = Render(File("build/sheets"))
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val v1 = Dev("240x320", "funeOS V1 board", 240, 320, listOf(Nav.None))
    private val r1 = Dev("480x800", "funeOS R1 board", 480, 800, listOf(Nav.None))
    private val a105 = Dev("720x1280", "funeOS A105 board", 720, 1280, listOf(Nav.None))
    private val boardOf = mapOf(v1 to "v1", r1 to "r1", a105 to "a105")

    private class Scene(val kind: String, val note: String, val down: Boolean, val ms: Long, val track: Boolean, val toast: Boolean)

    private fun recorder(regions: Regions, pressed: Boolean, press: Box, words: List<Box>): DrawRecord = object : DrawRecord by regions {
        override fun ring(name: String, c: Offset, d: Float, alpha: Float) {
            if (pressed) regions.add("$name.press", Kind.Ring, press, KitInk.white, alpha, masks = words) else regions.ring(name, c, d, alpha)
        }

        override fun glyphs(name: String, gs: List<Glyph>, c: Offset, slot: Float, alpha: Float, ringD: Float, ringStroke: Float) {
            if (pressed) regions.skip(ON_DISC) { regions.glyphs(name, gs, c, slot, alpha, ringD, ringStroke) } else regions.glyphs(name, gs, c, slot, alpha, ringD, ringStroke)
        }
    }

    private fun tile(dev: Dev, sc: Scene): Tile {
        val s = scale(dev.window(Nav.None), Nav.None, Surface.Home, false)
        val keys = FuneHome.entries
        val ink = homeInk(measurer, fonts, s, unitDensity, keys)
        val geo = HomeGeo(s, keys.size)
        val menu = HomeMenu(keys.size)
        if (sc.down) menu.ringDown(AT)
        val rest = menu.restAt(AT + sc.ms, sc.track)
        val pressed = rest.ringLit > 0f
        val board = boardOf.getValue(dev)
        val regions = Regions(board, dev.w, dev.h, s).also { it.residualWhy = if (sc.toast) QUIET_TOAST else QUIET }
        val x = penPx(pen64(Tok.homeTextX, s.stdPpu))
        val words = ink.names.mapIndexed { i, w ->
            val base = px(geo.baseline(i), s.stdPpu)
            Box(x + w.inkLeft, base + w.inkTop, x + w.inkRight, base + w.inkBottom).grow(1f)
        }
        val rec = recorder(regions, pressed, Box.of(homeRingRect(s, 0f)), words)
        val img = ImageBitmap(dev.w, dev.h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(dev.w.toFloat(), dev.h.toFloat())) {
            drawRect(KitInk.bg)
            homeMenu(ink, menu, geo, rest, null, 0f, rec)
        }
        if (sc.toast) regions.shade(Box(0f, (dev.h - px(Tok.toastHeight, s.stdPpu)).toFloat(), dev.w.toFloat(), dev.h.toFloat()), UNDER_TOAST)
        val notes = listOf(render.facts(dev, Nav.None, s), sc.note, "ring lit ${"%.3f".format(rest.ringLit)}, ring alpha ${"%.3f".format(rest.ring)}")
        return render.tile(FOLDER, "$board-${sc.kind}", dev.label, notes, img.asAndroidBitmap(), emptyList(), s.stdPpu, regions)
    }

    @Test
    fun homeAndTheHomeRingPress() {
        val still = listOf(
            Scene("home", "funeOS home: seed(\"\"), the shot at 1000 ms, nothing playing, so the ring at alpha.mid", false, 0, false, false),
            Scene("home-full", "funeOS home:full: seed(\"full\"), the shot at 1000 ms, nothing playing", false, 0, false, false),
            Scene("home-full-off", "funeOS home:full:off: QUICKPLAY OFF, the shot at 1000 ms, nothing playing", false, 0, false, false),
        )
        val held = listOf(0L, 50L, 100L, 400L, 417L, 600L).map {
            Scene("ringpress-$it-hometrack-held-series-accentoff", "funeOS ringpress:$it:hometrack:held:series: a paused track lights the ring, the ring down at 1000 ms, the shot $it ms after it", true, it, true, true)
        }
        val tap = Scene("ringpress-0-home-tap-series-accentoff", "funeOS ringpress:0:home:tap:series: the ring down at 1000 ms and the shot at it, before its up at 1050", true, 0, false, false)
        val home = Scene("ringpress-0-home-held", "funeOS ringpress:0:home:held: the ring down at 1000 ms and the shot 150 ms after it", true, 150, false, false)
        val cases = listOf(
            "home" to (listOf(a105, r1) to still),
            "hometrack" to (listOf(a105, v1) to held),
            "hometap" to (listOf(a105, v1) to listOf(tap)),
            "homeheld" to (listOf(v1) to listOf(home)),
        )
        for ((case, sets) in cases) {
            val (devs, scenes) = sets
            val tiles = scenes.flatMap { sc -> devs.map { tile(it, sc) } }
            render.write(Sheet(FOLDER, case, listOf("Kit home menu and its ring press (funeOS draw_home_ring, home_ring_rect, the ring halo)."), tiles))
        }
        render.index(FOLDER, listOf(
            "# Kit home", "",
            "- `home/home.png`: home, home-full and home-full-off at A105 and R1",
            "- `home/hometrack.png`: the ring held over a paused track at 0, 50, 100, 400, 417 and 600 ms at A105 and V1",
            "- `home/hometap.png`: the ring down at 0 ms at A105 and V1",
            "- `home/homeheld.png`: the ring held 150 ms at V1",
            "- every tile has `<tile>.regions.json` beside it for `tools/inkband.py`",
        ))
    }

    companion object {
        private const val FOLDER = "home"
        private const val AT = 1000L
        private const val QUIET = "the kit sheet draws the home menu only: funeOS's Quickplay peek with its fixture covers is not drawn"
        private const val QUIET_TOAST = "the kit sheet draws the home menu only: funeOS's Quickplay peek with its fixture covers and the track toast of the paused track are not drawn"
        private const val UNDER_TOAST = "under funeOS's bottom track toast, which the kit sheet does not draw"
        private const val ON_DISC = "the play glyph sits on the press disc, whose ink fills the glyph window"
    }
}
