package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Fonts
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.StatusText
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.art.decodeArt
import net.seilent.funekit.kit.lock.LockDrag
import net.seilent.funekit.kit.lock.LockInk
import net.seilent.funekit.kit.lock.LockLayout
import net.seilent.funekit.kit.lock.lockPaint
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.npStatus
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.statusLine
import net.seilent.funekit.kit.word
import net.seilent.funekit.scale
import net.seilent.funekit.screens.lock.lockFrame
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpMeasure
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.npFrame
import net.seilent.funekit.screens.music.nowplaying.npPage
import net.seilent.funekit.screens.music.nowplaying.npTimes
import net.seilent.funekit.sheet.Box
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Kind
import net.seilent.funekit.sheet.Regions
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
class LockSheetTest {
    private val sc = MusicScenes()
    private val boards = listOf(sc.a105, sc.r1, sc.v1)

    private class Rig(
        val s: Scale,
        val g: NpLayout,
        val m: NowPlayingModel,
        val npArt: ImageBitmap?,
        val square: ImageBitmap?,
        val ink: LockInk,
        val drag: LockDrag,
    )

    private fun word(s: Scale, text: String, role: Role): Word =
        sc.measurer.word(text, role, roleStyle(role, s, sc.density, Ink.white, ppu = s.stdPpu), sc.fonts, sc.density, sc.covers, s.board.fontHanMinPx)

    private fun rig(dev: Dev): Rig = runBlocking {
        val s = scale(dev.window(Nav.None), Nav.None, Surface.Saver, false)
        val g = NpLayout(scale(dev.window(Nav.None), Nav.None, Surface.NowPlaying, false))
        val v = sc.fake.library.view()
        val a = v.page(Listing.Albums, ALBUM, 1).rows.first()
        val rows = v.page(Listing.AlbumTracks(a.id), 0, 1 + g.nextTracks).rows
        val track = rows.first()
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = rows.size, index = 0, track = track.id, playing = true, anchor = Anchor(0, RATE, 0, false, track.durationMs ?: 0L)))
        m.sync(NpShown.of(rows), NP_OPEN)
        m.resetFocus(NP_OPEN)
        val l = LockLayout(s)
        val ref = track.art
        val npArt = ref?.let { v.art(it, g.artSide) }?.let { decodeArt(it, g.artSide) }?.asImageBitmap()
        val square = ref?.let { v.art(it, l.artSide) }?.let { decodeArt(it, l.artSide) }?.asImageBitmap()
        val n = m.shown
        val ink = LockInk(word(s, CLOCK, Roles.saverClock), word(s, n.title, Roles.lockTitle), word(s, n.artist, Roles.lockArtist), word(s, n.album, Roles.lockAlbum), Word.none, false, 1f)
        Rig(s, g, m, npArt, square, ink, LockDrag(s.heightPx))
    }

    private fun status(s: Scale): StatusInk {
        val box = npStatus(s)
        val line = statusLine(s, box)
        val style = roleStyle(Roles.time, s, sc.density, Ink.white, Tok.alpha_full).merge(Fonts.style(Roles.time, line.textPx, sc.density, Roles.time.tracking * s.ppu))
        fun w(t: String) = sc.measurer.word(t, Roles.time, style, sc.fonts, sc.density, null, s.board.fontHanMinPx)
        return StatusInk(StatusState(CLOCK, BATTERY, false, Play.Playing, false, -1, -1), StatusText(w(CLOCK), w("$BATTERY%"), null), line, s, box)
    }

    private fun drag(r: Rig, moves: Int, dyU: Float, upAt: Long?): Long {
        val p = r.s.stdPpu
        val x = DOWN_X
        val y0 = r.s.stdH - DOWN_LIFT
        r.drag.down(y0 * p, x, y0, T0, T0)
        for (i in 1..moves) {
            val y = y0 - dyU * i / moves
            r.drag.move(y * p, y, T0 + i * STEP)
        }
        val end = T0 + moves * STEP
        if (upAt == null) return end
        val up = end + upAt
        r.drag.up((y0 - dyU) * p, x, y0 - dyU, up, up)
        return up
    }

    private fun DrawScope.under(r: Rig, at: Long, rec: Regions) {
        val measure = NpMeasure(r.g, sc.measurer, sc.fonts, sc.density, sc.covers)
        npPage(r.g, npFrame(r.m, r.g, at, false), measure.ink(r.m.shownAt(at), false), measure.time(npTimes(r.g, r.m, at, 0L)), r.npArt, status(r.g.s), sc.accent, false, false, rec)
    }

    private fun tile(dev: Dev, kind: String, r: Rig, at: Long, note: String): Tile {
        val s = r.s
        val l = LockLayout(s)
        val off = r.drag.offset(at)
        val alpha = r.drag.sheetAlpha(at)
        val rules = if (dev == sc.v1) listOf(RING0_GLYPH to V1_RING_GLYPH, RING1_GLYPH to V1_RING_GLYPH) else emptyList()
        val rec = sc.regions(dev, s, rules)
        rec.residual = false
        rec.residualWhy = RESIDUAL
        val art = HubRegions(rec)
        return sc.tile(FOLDER, dev, kind, s, listOf(note, GROUND), rec) {
            if (off > 0) under(r, at, rec)
            if (off > 0) rec.shade(Box(0f, 0f, dev.w.toFloat(), (dev.h - off).toFloat()), COVERED)
            lockFrame(false, off, alpha, Paint()) {
                rec.move(0f, -off.toFloat()) {
                    rec.fade(alpha) {
                        drawRect(Ink.bg)
                        lockPaint(l, r.ink, r.square, false, rec)
                        val e = Edges(l.left + l.artX, l.artY, l.left + l.artX + l.artSide, l.artY + l.artSide)
                        val sq = r.square
                        if (sq != null) art.art("lock.art", e, sq, 1f, emptyList(), PEAK_OFF) else rec.edges("lock.tile", Kind.Tile, e, Ink.tile, 1f)
                    }
                }
            }
        }
    }

    @Test
    fun lockScenes() {
        val tiles = boards.flatMap { dev ->
            val rest = rig(dev).let { r -> tile(dev, "lock-500", r, T0, "the lock at rest 500 ms after the wake (funeOS player/app/main.cpp run_shot :5553-5581, v0.4.4)") }
            val dragged = rig(dev).let { r ->
                val end = drag(r, DRAG_MOVES, DRAG_U, null)
                tile(dev, "lock-500-drag-120", r, end + STEP, "a down at (120 u, H - 30 u) and two moves 120 u up, the finger held, now playing under the band")
            }
            val commit = rig(dev).let { r ->
                val up = drag(r, COMMIT_MOVES, r.s.stdH * COMMIT_SHARE, 0L)
                tile(dev, "lock-500-commit-250", r, up + SHOT_AFTER, "six moves over 3/5 of H and an up, 250 ms into the commit ease and the sliver fade")
            }
            val spring = rig(dev).let { r ->
                val up = drag(r, SPRING_MOVES, SPRING_U, SPRING_HOLD)
                tile(dev, "lock-500-spring-250", r, up + SHOT_AFTER, "20 moves over 30 u, held 200 ms, an up, 250 ms into the spring back")
            }
            listOf(rest, dragged, commit, spring)
        }
        sc.render.write(Sheet(FOLDER, "lock", listOf(TITLE), tiles))
    }

    companion object {
        const val FOLDER = "lock"
        const val ALBUM = 1
        const val NP_OPEN = 3800L
        const val T0 = 6300L
        const val STEP = 16L
        const val DOWN_X = 120f
        const val DOWN_LIFT = 30f
        const val DRAG_MOVES = 2
        const val DRAG_U = 120f
        const val COMMIT_MOVES = 6
        const val COMMIT_SHARE = 0.6f
        const val SPRING_MOVES = 20
        const val SPRING_U = 30f
        const val SPRING_HOLD = 200L
        const val SHOT_AFTER = 250L
        const val RATE = 48000
        const val CLOCK = "13:51"
        const val BATTERY = 80
        const val PEAK_OFF = "the fake's placeholder art is one flat gray, the shot library's a gradient"
        const val GROUND = "ground color.bg: funeOS grounds the lock in the cover blur of its shot library's art, which the fake's flat placeholder art cannot give"
        const val COVERED = "under the lock sheet"
        const val RING0_GLYPH = "np.ring0.glyph"
        const val RING1_GLYPH = "np.ring1.glyph"
        const val V1_RING_GLYPH = "at V1 the shuffle and repeat glyph boxes reach the ring's hole, so the glyph window, one px past the box, takes the ring stroke's ink in both images"
        const val RESIDUAL = "residual off: the shot library's long titles and its art and ground are not the fake's"
        const val TITLE = "The lock screen on A105, R1 and V1 at the funeOS lock scene times: at rest, a drag held, 250 ms into a commit and into a spring back."
    }
}
