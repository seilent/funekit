package net.seilent.funekit.sheet

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
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.overlay.VolumeParts
import net.seilent.funekit.kit.overlay.volumeInk
import net.seilent.funekit.kit.overlay.volumePlate
import net.seilent.funekit.kit.scrub.SeekRamp
import net.seilent.funekit.scale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import net.seilent.funekit.kit.Ink as KitInk

private class VolHost(val hud: VolumeHud) {
    var percent = 40
    private val ramp = SeekRamp()

    fun show(t: Long) = hud.show(t, percent, true, "An Artist Name", "A Song Title")

    fun knob(t: Long) {
        percent = minOf(100, percent + Tok.volume_stepDefault.toInt())
        show(t)
    }

    fun act(a: VolumeHud.Act, t: Long) {
        when (a) {
            VolumeHud.Act.Plus -> knob(t)
            VolumeHud.Act.Minus -> {
                percent = maxOf(0, percent - Tok.volume_stepDefault.toInt())
                show(t)
            }
            VolumeHud.Act.Volume -> {
                val was = percent
                percent = hud.number
                hud.took(percent)
                if (percent != was) show(t)
            }
            else -> Unit
        }
    }

    fun hold(t: Long) {
        val d = hud.holdDue(t)
        if (d != 0) ramp.start(d, t, t, HOLD_DUR)
        if (!ramp.on) return
        if (!hud.holding && !ramp.released) ramp.release(t)
        if (ramp.finalDue() >= 0) {
            if (t < ramp.finalDue()) return
            ramp.finalStep()
            ramp.stop()
            hud.seekFinal(t)
            return
        }
        ramp.step(t)
        hud.seekShow(ramp.dir, ramp.target, ramp.dur, t, false)
    }

    companion object {
        const val HOLD_DUR = 377000L
    }
}

private class VolStep(val at: Long, val run: (VolHost, Long) -> Unit)

private class VolScene(val kind: String, val note: String, val shot: Long, val steps: List<VolStep>)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VolumeSheetTest {
    private val render = Render(File("build/sheets"))
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val v1 = Dev("240x320", "funeOS V1 board", 240, 320, listOf(Nav.None))

    private fun at(t: Long, run: (VolHost, Long) -> Unit) = VolStep(t, run)

    private fun knob() = listOf(at(AT + 200) { h, t -> h.knob(t) }, at(AT + 320) { h, t -> h.knob(t) })

    private fun tap(t0: Long, x: Float, y: Float) = listOf(
        at(t0) { h, t -> h.act(h.hud.down(t, x, y), t) },
        at(t0 + 50) { h, t -> h.act(h.hud.up(t, x, y), t) },
    )

    private fun swipe(): List<VolStep> {
        val x = 164f
        val y = 159f
        val n = 18
        val moves = (1..3 * n).map { i ->
            at(SWIPE + 16L * i) { h, t -> h.act(h.hud.move(t, x, y + (if (i <= n) 5 * i else 10 * n - 5 * i), h.percent, VolumeHud.Pos()), t) }
        }
        return listOf(at(SWIPE) { h, t -> h.act(h.hud.down(t, x, y), t) }) + moves +
            at(SWIPE + 16L * (3 * n + 1)) { h, t -> h.act(h.hud.up(t, x, y - 5 * n), t) }
    }

    private fun hudAt(sc: VolScene): VolumeHud {
        val host = VolHost(VolumeHud(TokBoard.v1))
        val steps = sc.steps.sortedBy { it.at }
        val times = (steps.map { it.at } + (0L..sc.shot step FRAME) + sc.shot).filter { it <= sc.shot }.distinct().sorted()
        for (t in times) {
            for (s in steps) if (s.at == t) s.run(host, t)
            host.hold(t)
            host.hud.tiltFrame(t)
            host.hud.seekFrame(t)
        }
        return host.hud
    }

    private fun tile(sc: VolScene): Tile {
        val s = scale(v1.window(Nav.None), Nav.None, Surface.NowPlaying, false)
        val hud = hudAt(sc)
        val parts = VolumeParts(TokBoard.v1, s.stdPpu, s.w)
        val ink = measurer.volumeInk(hud, parts, fonts, unitDensity, null)
        val regions = Regions("v1", v1.w, v1.h, s).also { it.residualWhy = QUIET }
        val img = ImageBitmap(v1.w, v1.h)
        val l = hud.look(sc.shot)
        val draw = {
            CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(v1.w.toFloat(), v1.h.toFloat())) {
                drawRect(KitInk.bg)
                volumePlate(hud, parts, ink, Glow(), sc.shot, regions)
            }
        }
        if (l.scrim < 1f) regions.skip(UNDER_PAGE, draw) else draw()
        val look = "visible ${hud.visible(sc.shot)}, number ${hud.percent}, tilt k ${"%.3f".format(hud.tiltK(sc.shot))}, group ${"%.3f".format(l.group)}, scrim ${"%.3f".format(l.scrim)}"
        val notes = listOf(render.facts(v1, Nav.None, s), sc.note, look, START)
        return render.tile(FOLDER, "v1-${sc.kind}", v1.label, notes, img.asAndroidBitmap(), emptyList(), s.stdPpu, regions)
    }

    @Test
    fun theVolumeHudOnV1() {
        val held = at(AT + 450) { h, t -> h.act(h.hud.down(t, 200f, 60f), t) }
        val tilt = listOf(
            VolScene("vol-300-tilt-200-60", "funeOS vol:300:tilt:200:60 (main.cpp:8731-8735): a down at (200, 60) u at 450 ms, held to the shot at 500; no control there, so the plate stays flat", AT + 500, knob() + held),
            VolScene("vol-300-tiltup-200-60-250", "funeOS vol:300:tiltup:200:60:250: as tilt, the touch cut at 500 ms (touch_cut, VolumeHud cancel), the shot 250 ms after it", AT + 750, knob() + held + at(AT + 500) { h, t -> h.hud.cancel(t) }),
        )
        val closeAt = tap(AT + 900, Tok.canvasW / 2 + Tok.vol_skipX / 2, TokBoard.v1.vol_plusY)
        val close = listOf(0L, 67L, 150L).map { ms ->
            VolScene("vol-$ms-close-accentoff", "funeOS vol:$ms:close (main.cpp:8707-8713): a tap at (164.1, 56.5) u, between the controls, down at 900 and up at 950 ms, closes the HUD; the shot $ms ms after the up", AT + 950 + ms, knob() + closeAt)
        }
        val plusAt = tap(AT + 900, Tok.canvasW / 2, TokBoard.v1.vol_plusY)
        val plus = listOf(0L, 50L, 150L, 250L, 400L).map { ms ->
            VolScene("vol-$ms-plus-accentoff", "funeOS vol:$ms:plus (main.cpp:8726-8729): a tap on the plus, down at 900 and up at 950 ms, steps the volume up; the shot $ms ms after the down", AT + 900 + ms, knob() + plusAt)
        }
        val swipe = listOf(0L, 17L, 283L, 867L, 883L, 1083L, 1183L, 1283L).map { ms ->
            VolScene("vol-300-swipe-164-159-$ms-accentoff", "funeOS vol:300:swipe:164:159:$ms (main.cpp:8738-8755): a down at (164, 159) u at 500 ms, 54 moves 16 ms apart, 5 u down a step for 18 steps then back up, the up 880 ms after the down; the shot $ms ms after the down", SWIPE + ms, knob() + swipe())
        }
        val glyphAt = at(AT + 1200) { h, t -> h.act(h.hud.down(t, Tok.canvasW / 2 + Tok.vol_skipX, TokBoard.v1.vol_ringY), t) }
        val hold = listOf(0L, 383L, 400L, 417L, 450L, 517L).map { ms ->
            VolScene("vol-$ms-holdglyph-accentoff", "funeOS vol:$ms:holdglyph (main.cpp:8648-8653, :8698-8701): a down on the next glyph at 1200 ms, held; the hold starts seekHoldMs after it and the seek readout counts the ramp from the 6:17 track's elapsed time, now playing opened at 0 ms, 1000 ms before the scene's at (main.cpp:8627); the shot $ms ms after the down", AT + 1200 + ms, knob() + glyphAt)
        }
        val cases = listOf("tilt" to tilt, "close" to close, "plus" to plus, "swipe" to swipe, "hold" to hold)
        for ((case, scenes) in cases) {
            render.write(Sheet(FOLDER, case, listOf("Kit volume HUD on V1 (funeOS VolumeHud compose, the vol scenes of main.cpp)."), scenes.map { tile(it) }))
        }
        render.index(FOLDER, listOf(
            "# Kit volume HUD", "",
            "- `volume/tilt.png`: a held down at (200, 60) u, then cut, at V1",
            "- `volume/close.png`: the tap close at 0, 67 and 150 ms at V1",
            "- `volume/plus.png`: a plus tap at 0, 50, 150, 250 and 400 ms at V1",
            "- `volume/swipe.png`: a volume drag from (164, 159) u at 0, 17, 283, 867, 883, 1083, 1183 and 1283 ms at V1",
            "- `volume/hold.png`: a held next glyph at 0, 383, 400, 417, 450 and 517 ms at V1",
            "- every tile has `<tile>.regions.json` beside it for `tools/inkband.py`",
        ))
    }

    companion object {
        private const val FOLDER = "volume"
        private const val AT = 1000L
        private const val SWIPE = AT + 500
        private const val FRAME = 16L
        private const val QUIET = "the kit sheet draws the volume HUD over the plain ground: funeOS draws it over now playing, whose page under the scrim the kit sheet does not draw"
        private const val UNDER_PAGE = "the scrim lifts as the tap close runs, so funeOS's now playing page, which the kit sheet does not draw, shows under every part"
        private const val START = "the volume starts at 40 percent and each knob detent and plus steps it by volume.stepDefault (the funeOS shot's start volume is not read), and the HUD steps on 16 ms frames from 0 ms (the shot harness's frame clock is not read)"
    }
}
