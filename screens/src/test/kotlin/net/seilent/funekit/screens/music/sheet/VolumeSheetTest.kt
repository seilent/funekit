package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Fonts
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.engine.Output
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.StatusText
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastInk
import net.seilent.funekit.kit.ToastPlace
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.npStatus
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.overlay.VolumeParts
import net.seilent.funekit.kit.overlay.volumeInk
import net.seilent.funekit.kit.overlay.volumePlate
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.statusLine
import net.seilent.funekit.kit.toast
import net.seilent.funekit.kit.toastSurface
import net.seilent.funekit.kit.word
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpMeasure
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.npFrame
import net.seilent.funekit.screens.music.nowplaying.npPage
import net.seilent.funekit.screens.music.nowplaying.npTimes
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Sheet
import net.seilent.funekit.sheet.Tile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VolumeSheetTest {
    private val sc = MusicScenes()
    private val boards = listOf(sc.r1, sc.a105)

    private class Hud(val s: Scale, val hud: VolumeHud, val m: NowPlayingModel?, val g: NpLayout)

    private fun npScale(dev: Dev): Scale = scale(dev.window(Nav.None), Nav.None, Surface.NowPlaying, false)

    private fun level(): Int = sc.fake.settings.sound.value.volumePct ?: 0

    private fun step(hud: VolumeHud, at: Long, artist: String, title: String) {
        runBlocking { sc.fake.settings.stepVolume(1) }
        hud.show(at, level(), true, artist, title)
    }

    private fun onNp(dev: Dev): Hud = runBlocking {
        val s = npScale(dev)
        val g = NpLayout(s)
        val v = sc.fake.library.view()
        val a = v.page(Listing.Albums, ALBUM, 1).rows.first()
        val rows = v.page(Listing.AlbumTracks(a.id), 0, 1 + g.nextTracks).rows
        val track = rows.first()
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = rows.size, index = 0, track = track.id, playing = true, anchor = Anchor(0, RATE, 0, false, track.durationMs ?: 0L)))
        m.sync(NpShown.of(rows), NP_OPEN)
        m.resetFocus(NP_OPEN)
        sc.fake.settings.update(sc.fake.settings.sound.value.copy(output = Output.Usb))
        sc.fake.settings.setVolumePercent(START_PCT)
        val hud = VolumeHud(s.board, s.w, s.h)
        step(hud, AT + KNOB_AT, m.shown.artist, m.shown.title)
        step(hud, AT + KNOB_AT + KNOB, m.shown.artist, m.shown.title)
        Hud(s, hud, m, g)
    }

    private fun onHome(dev: Dev): Hud {
        val s = npScale(dev)
        val hud = VolumeHud(s.board, s.w, s.h)
        hud.show(HOME_OPEN, level(), false, "", "")
        return Hud(s, hud, null, NpLayout(s))
    }

    private fun status(s: Scale): StatusInk {
        val box = npStatus(s)
        val line = statusLine(s, box)
        val style = roleStyle(Roles.time, s, sc.density, Ink.white, Tok.alpha_full).merge(Fonts.style(Roles.time, line.textPx, sc.density, Roles.time.tracking * s.ppu))
        fun w(t: String) = sc.measurer.word(t, Roles.time, style, sc.fonts, sc.density, null)
        return StatusInk(StatusState(CLOCK, BATTERY, false, Play.Playing, false, -1, -1), StatusText(w(CLOCK), w("$BATTERY%"), null), line, s, box)
    }

    private fun DrawScope.page(h: Hud, at: Long) {
        val m = h.m ?: return
        val measure = NpMeasure(h.g, sc.measurer, sc.fonts, sc.density, sc.covers)
        npPage(h.g, npFrame(m, h.g, at, false), measure.ink(m.shownAt(at), false), measure.time(npTimes(h.g, m, at, 0L)), null, status(h.s), sc.accent, false, false, null)
    }

    private fun tile(dev: Dev, kind: String, h: Hud, at: Long, note: String): Tile {
        val v = VolumeParts(h.s.board, h.s.ppu, h.s.w)
        val ink = sc.measurer.volumeInk(h.hud, v, sc.fonts, sc.density, sc.covers)
        val glow = Glow()
        val rec = sc.regions(dev, h.s)
        rec.residual = true
        rec.residualWhy = RESIDUAL
        return sc.tile(FOLDER, dev, kind, h.s, listOf(note, NO_REF), rec) {
            page(h, at)
            volumePlate(h.hud, v, ink, glow, at)
        }
    }

    @Test
    fun volumeScenes() {
        val tiles = boards.flatMap { dev ->
            val rest = listOf(SHORT, LONG).map { ms ->
                val h = onNp(dev)
                tile(dev, "vol-$ms", h, AT + KNOB_AT + ms, "the hud from now playing's two volume steps, $ms ms after the first")
            }
            val tilt = onNp(dev).let { h ->
                val end = AT + KNOB_AT + SHORT
                h.hud.down(end - HELD_FOR, TILT_X, TILT_Y)
                tile(dev, "vol-$SHORT-tilt-${TILT_X.toInt()}-${TILT_Y.toInt()}", h, end, "a down held at (${TILT_X.toInt()}, ${TILT_Y.toInt()}) u from ${HELD_FOR} ms before the shot")
            }
            val tiltUp = onNp(dev).let { h ->
                val end = AT + KNOB_AT + SHORT
                h.hud.down(end - HELD_FOR, TILT_X, TILT_Y)
                h.hud.cancel(end)
                tile(dev, "vol-$SHORT-tiltup-${TILT_X.toInt()}-${TILT_Y.toInt()}-$UP_AFTER", h, end + UP_AFTER, "the down cut at the shot time of the tilt form, $UP_AFTER ms later")
            }
            val exits = listOf("down" to EXIT_DOWN, "held" to EXIT_HELD, "up" to EXIT_UP).map { (form, ms) ->
                val h = onHome(dev)
                h.hud.down(EXIT_AT, Tok.vol_exitX, h.s.board.vol_exitY)
                if (form == "up") h.hud.up(EXIT_AT + EXIT_HELD, Tok.vol_exitX, h.s.board.vol_exitY)
                tile(dev, "press-0-volexit-$form", h, EXIT_AT + ms, "the exit $form on the hud opened from home, home drawn as its ground")
            }
            rest + tilt + tiltUp + exits
        }
        sc.render.write(Sheet(FOLDER, "volume", listOf(TITLE), tiles))
    }

    @Test
    fun trackToastScene() {
        val tiles = boards.map { dev ->
            val s = sc.pageScale(dev)
            val (m, d) = sc.hub(s, HUB_SONGS)
            sc.fill(m, d)
            val c = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots, sc.covers).also { it.select(HUB_SONGS) }
            val row = runBlocking { sc.fake.library.view().page(Listing.Songs, 0, 1).rows.first() }
            val t = Toast()
            t.show(TOAST_AT, row.sub, row.title, track = true)
            t.frame(ToastPlace.Bottom, toastSurface(s, false), TOAST_AT)
            val tink = ToastInk(
                sc.measurer.word(t.artist, Roles.toastArtist, roleStyle(Roles.toastArtist, s, sc.density, Ink.white), sc.fonts, sc.density, null),
                sc.measurer.word(t.title, Roles.toastTitle, roleStyle(Roles.toastTitle, s, sc.density, Ink.white), sc.fonts, sc.density, null),
            )
            val shot = TOAST_AT + TOAST_SHOT
            t.frame(ToastPlace.Bottom, toastSurface(s, false), shot)
            val rec = sc.regions(dev, s)
            rec.residual = true
            rec.residualWhy = RESIDUAL_TOAST
            sc.tile(FOLDER, dev, "track-toast-$TOAST_SHOT", s, listOf("the track toast on the hub's songs, $TOAST_SHOT ms after a track change", NO_REF), rec) {
                chrome(c, 0L)
                with(d) { content(HUB_SONGS, 0L, true, null, "page") { r -> m.look(HUB_SONGS, r, 0L, true) } }
                toast(t, tink, s, shot)
            }
        }
        sc.render.write(Sheet(FOLDER, "track-toast", listOf(TOAST_TITLE), tiles))
    }

    companion object {
        const val FOLDER = "volume"
        const val ALBUM = 1
        const val NP_OPEN = 3800L
        const val AT = 5120L
        const val KNOB_AT = 200L
        const val KNOB = 120L
        const val SHORT = 300L
        const val LONG = 3000L
        const val HELD_FOR = 50L
        const val UP_AFTER = 250L
        const val TILT_X = 200f
        const val TILT_Y = 60f
        const val HOME_OPEN = 1000L
        const val EXIT_AT = 2000L
        const val EXIT_DOWN = 50L
        const val EXIT_HELD = 150L
        const val EXIT_UP = 170L
        const val TOAST_AT = 1000L
        const val TOAST_SHOT = 500L
        const val RATE = 48000
        const val START_PCT = 41
        const val CLOCK = "10:08"
        const val BATTERY = 80
        const val NO_REF = "no funeOS reference in the set (the volume references are V1 only, paired in funekit)"
        const val RESIDUAL = "residual on: the hud and now playing draw their own parts over the scrim"
        const val RESIDUAL_TOAST = "residual on: the hub rows, the chrome and the toast strip only"
        const val TITLE = "The volume overlay on A105 and R1 at the funeOS vol scene times, the exit press from home."
        const val TOAST_TITLE = "The track toast on a page, which has no funeOS reference."
    }
}
