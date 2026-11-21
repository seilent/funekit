package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.core.Section
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Ink as KitInk
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastInk
import net.seilent.funekit.kit.ToastPlace
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.jumpGrid
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.thumb
import net.seilent.funekit.kit.toast
import net.seilent.funekit.kit.toastSurface
import net.seilent.funekit.kit.word
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_FOLDERS
import net.seilent.funekit.screens.music.hub.HUB_GENRES
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubAct
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.hubThumbAt
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.Sheet
import net.seilent.funekit.sheet.Tile
import net.seilent.funekit.sheet.toast
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HubStateSheetTest {
    private val sc = MusicScenes()
    private val boards = listOf(sc.r1, sc.a105)

    private class Shot(val sc: MusicScenes, val dev: Dev, val m: HubModel, val d: HubDraw, val c: Chrome, val s: Scale, val accentOff: Boolean) {
        val moves = mutableListOf<LongRange>()
        var shown = SHOWN
        var toast: Toast? = null
        var toastInk: ToastInk? = null
        val pitch: Int get() = d.geo.pitch(m.pivot)
    }

    private fun shot(scenes: MusicScenes, dev: Dev, pivot: Int, nav: Nav = Nav.None, large: Boolean = false, accentOff: Boolean = false): Shot {
        val s = scenes.pageScale(dev, nav, large)
        val (m, d) = scenes.hub(s, pivot, accentOff)
        val c = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots, sc.covers).also { it.select(pivot) }
        return Shot(scenes, dev, m, d, c, s, accentOff)
    }

    private fun Shot.knob(row: Int, at: Long) {
        val pn = m.pane
        pn.setFocus(row, 0, at, false)
        val top = pn.track.topOf(pn.focus.row)
        val h = pn.track.topOf(pn.focus.row + 1) - top
        val off = pn.track.q
        val want = when {
            top < off || (top == 0 && off > 0) -> top
            top + h > off + d.geo.view -> top + h - d.geo.view
            else -> off
        }
        if (want == off) return
        pn.track.at(want)
        moves += at until at + Tok.motion_scroll.toLong()
    }

    private fun Shot.knobs(n: Int, at: Long): Long {
        repeat(n) { knob(m.pane.focus.row + 1, at + it * KNOB) }
        return at + n * KNOB
    }

    private fun Shot.select(at: Long) = runBlocking {
        sc.fill(m, d)
        val v = sc.fake.library.view()
        val pn = m.pane
        val act = m.act(m.pivot, pn.focus.row, 0, false)
        val play = requireNotNull(act as? HubAct.Play) { "the focused row ${pn.focus.row} plays nothing: $act" }
        sc.fake.player.play(play.listing, v.generation, play.index, 0L)
        m.play(v, sc.fake.player.state.value)
        showToast(requireNotNull(pn.row(pn.cell(pn.focus.row, 0))), at)
    }

    private fun Shot.showToast(row: Row, at: Long) {
        val t = Toast()
        t.show(at, row.sub, row.title, track = true)
        t.frame(ToastPlace.Bottom, toastSurface(s, s.navOn), at)
        toast = t
        toastInk = ToastInk(
            sc.measurer.word(t.artist, Roles.toastArtist, roleStyle(Roles.toastArtist, s, sc.density, KitInk.white), sc.fonts, sc.density, null),
            sc.measurer.word(t.title, Roles.toastTitle, roleStyle(Roles.toastTitle, s, sc.density, KitInk.white), sc.fonts, sc.density, null),
        )
    }

    private fun Shot.enter(part: String, at: Long) = runBlocking {
        val v = sc.fake.library.view()
        val pn = m.pane
        val rows = requireNotNull(pn.whole)
        val spaced = part.replace('_', ' ')
        val i = rows.indexOfFirst { it.kind == Kind.Folder && (it.title == part || it.title == spaced) }
        require(i >= 0) { "no folder $part in ${m.path}" }
        pn.setFocus(requireNotNull(pn.map).rowOfIndex(i), 0, at, false)
        m.enter(rows[i].id, rows[i].title)
        m.load(v, HUB_FOLDERS)
        val map = requireNotNull(pn.map)
        pn.track.count(map.count, 0)
        pn.counted = map
        m.settleFolder(d.geo.view, pitch, at)?.let { pn.track.at(it) }
    }

    private fun Shot.fade(shot: Long): Float {
        val on = hubThumbAt(m, d.geo, m.pivot, m.pane.track.q) != null
        val f = ThumbFade()
        var t = 0L
        while (true) {
            if (t >= shown) f.track(on, moves.any { t in it }, t)
            if (t >= shot) break
            t = min(t + STEP, shot)
        }
        return f.at(shot)
    }

    private fun DrawScope.page(h: Shot, now: Long, rec: Regions?) {
        val bands = groundBands(h.dev.h, h.accentOff)
        pageGround(bands, sc.accent)
        chrome(h.c, now)
        rec?.chrome("chrome", h.c, now, size.width.toInt())
        with(h.d) { content(h.m.pivot, now, true, rec?.let { HubRegions(it) }, "page") { r -> h.m.look(h.m.pivot, r, now, true) } }
        val a = h.fade(now)
        if (a > 0f) {
            hubThumbAt(h.m, h.d.geo, h.m.pivot, h.m.pane.track.q)?.let {
                thumb(it, a)
                rec?.thumbParts("thumb", it, Tok.scrollbar_alpha * a, bands, sc.accent)
            }
        }
        val t = h.toast ?: return
        val ink = requireNotNull(h.toastInk)
        t.frame(ToastPlace.Bottom, toastSurface(h.s, h.s.navOn), now)
        toast(t, ink, h.s, now)
        rec?.toast("toast", t, ink, h.s, now, h.dev.w)
    }

    private fun quiet(pivot: Int): String? = when (pivot) {
        HUB_ARTISTS -> ARTISTS_QUIET
        HUB_ALBUMS -> ALBUMS_QUIET
        else -> null
    }

    private fun tile(kind: String, h: Shot, at: Long, note: String, loud: String? = null): Tile {
        val rec = h.sc.regions(h.dev, h.s)
        val q = if (loud == null) quiet(h.m.pivot) else null
        rec.residual = q == null
        rec.residualWhy = q ?: loud ?: if (h.toast != null) TOAST_LOUD else LIST_LOUD
        h.sc.fill(h.m, h.d)
        return h.sc.tile(FOLDER, h.dev, kind, h.s, listOf(note), rec) { page(h, at, rec) }
    }

    @Test
    fun funeOsEmptyAndScanScenes() {
        val empty = MusicScenes(FakeSet.Empty)
        val pivots = listOf(HUB_ARTISTS, HUB_ALBUMS, HUB_SONGS, HUB_GENRES, HUB_FOLDERS)
        val bare = listOf(empty.r1, empty.a105)
        val tiles = bare.flatMap { dev ->
            pivots.map { p ->
                val name = MusicPage.Hub.pivots[p]
                tile("hub-500-empty-$name", shot(empty, dev, p), EMPTY_SHOT, "as funeOS hub:500:empty:$name: an empty library, the $name pivot set at 2400, its status line; shot at 3600", EMPTY_LOUD)
            }
        }
        sc.render.write(Sheet(FOLDER, "empty", listOf("The hub on an empty library beside funeOS hub-500-empty-*."), tiles))
        val scans = bare.flatMap { dev ->
            listOf(Triple("scan-0", 0, HUB_ARTISTS), Triple("scan-1", 1, HUB_ARTISTS), Triple("scan-12873-albums", SCAN_MANY, HUB_ALBUMS)).map { (kind, n, p) ->
                val h = shot(empty, dev, p)
                h.m.scanning = true
                h.m.found = n
                tile(kind, h, SCAN_SHOT, "as funeOS scan:$n: a scan running from 0 ms with $n songs found and no library, the ${MusicPage.Hub.pivots[p]} pivot; shot at 3000", EMPTY_LOUD)
            }
        }
        sc.render.write(Sheet(FOLDER, "scan", listOf("The hub status line while a scan runs on a library with no songs, beside funeOS scan-0, scan-1 and scan-12873-albums."), scans))
    }

    @Test
    fun funeOsAccentOffPlayScenes() {
        val tiles = boards.flatMap { dev ->
            val songs = shot(sc, dev, HUB_SONGS, accentOff = true)
            val at = songs.knobs(SONG_STEPS, KNOBS_AT) + SETTLE
            songs.select(at)
            val nine = tile("hub-songs-9-play-accentoff", songs, at + SETTLE, "as funeOS hub:songs:9:play:accentoff: ACCENT OFF, nine knob steps from the # row at 2600, 120 ms apart, scrolled into view; the song selected at $at and playing; shot at ${at + SETTLE}")
            val albums = shot(sc, dev, HUB_ALBUMS, accentOff = true)
            runBlocking {
                val v = sc.fake.library.view()
                val first = v.page(Listing.Albums, 0, 1).rows.first()
                sc.fake.player.play(Listing.AlbumTracks(first.id), v.generation, 0, 0L)
                albums.m.play(v, sc.fake.player.state.value)
                albums.showToast(v.page(Listing.AlbumTracks(first.id), 0, 1).rows.first(), LIT_AT)
            }
            val lit = tile("hub-albums-play-accentoff", albums, LIT_SHOT, "as funeOS hub:albums:play:accentoff: ACCENT OFF, the first album's first track playing from 900 ms with its track toast, the grid at its top; shot at 3900")
            val up = shot(sc, dev, HUB_SONGS, accentOff = true)
            runBlocking {
                val v = sc.fake.library.view()
                val n = v.page(Listing.Songs, 0, 0).total
                val i = CJK_INDEX
                val sec = requireNotNull(up.m.pane.sections)
                require(i < n && sec[i - 1] == sec[i]) { "song $i opens its section, so a divider sits above it" }
                val row = requireNotNull(up.m.pane.map).rowOfIndex(i)
                up.m.pane.setFocus(row, 0, KNOBS_AT, false)
                up.m.pane.track.toRow(row)
            }
            val sel = KNOBS_AT + KNOB + SETTLE
            up.select(sel)
            up.knob(up.m.pane.focus.row - 1, sel + SETTLE)
            val ccw = tile("hub-songs-@夜に駆ける-play-up-accentoff", up, sel + SETTLE + SETTLE, "as funeOS hub:songs:@夜に駆ける:play:up:accentoff with this fixture's song at funeOS's 夜に駆ける index (song ${CJK_INDEX + 1}, the number its row shows in the reference; this fixture's cjk.flac track sorts first in the kanji bucket, under its divider), a generated 44.1 kHz track, so the playing title and the toast show that song where funeOS shows its 96 kHz cjk.flac track: ACCENT OFF, the song focused at 2600 with the list set to its row, selected and playing at $sel, one knob step up at ${sel + SETTLE}; shot at ${sel + SETTLE + SETTLE}")
            listOf(nine, lit, ccw)
        }
        sc.render.write(Sheet(FOLDER, "play-accentoff", listOf("Playing rows under ACCENT OFF beside funeOS hub-songs-9-play-accentoff, hub-albums-play-accentoff and hub-songs-@夜に駆ける-play-up-accentoff."), tiles))
    }

    @Test
    fun funeOsLargeHubScenes() {
        val tiles = boards.flatMap { dev ->
            listOf("large-hub" to HUB_ARTISTS, "large-hub-albums" to HUB_ALBUMS, "large-hub-songs" to HUB_SONGS).map { (kind, p) ->
                tile(kind, shot(sc, dev, p, large = true), HUB_SHOT, "as funeOS $kind at SIZE LARGE: the ${MusicPage.Hub.pivots[p]} pivot as the hub opens it, focus on its first row; shot at 3600")
            }
        }
        sc.render.write(Sheet(FOLDER, "large", listOf("The hub at SIZE LARGE beside funeOS large-hub, large-hub-albums and large-hub-songs."), tiles))
    }

    private class FolderScene(val kind: String, val parts: List<String>, val steps: Int, val play: Boolean)

    private val folderScenes = listOf(
        FolderScene("folders-500-flac", listOf("flac"), 0, false),
        FolderScene("folders-500-flac-2", listOf("flac"), 2, false),
        FolderScene("folders-500-flac-2-play", listOf("flac"), 2, true),
        FolderScene("folders-500-flac+live", listOf("flac", "live"), 0, false),
        FolderScene("folders-500-flac+live+Encore_Sessions_Recorded_Live_In_Osaka+Bonus", listOf("flac", "live", LIVE_DIR, LIVE_SUB), 0, false),
    )

    @Test
    fun funeOsFolderScenes() {
        val tiles = boards.flatMap { dev ->
            folderScenes.map { fs ->
                val h = shot(sc, dev, HUB_FOLDERS)
                var at = FOLDERS_AT
                for (part in fs.parts) {
                    h.enter(part, at + SELECT_GAP)
                    at += ENTER_GAP
                }
                at = h.knobs(fs.steps, at) + FOLDER_SHOT
                if (fs.play) {
                    h.select(at)
                    at += SETTLE
                }
                tile(fs.kind, h, at, "as funeOS ${fs.kind.replaceFirst("-500-", ":500:")} with this fixture's folders ${fs.parts.joinToString(" / ")}: each entered by a knob select 400 ms apart from 2620, ${fs.steps} knob steps after${if (fs.play) ", the focused song selected and playing" else ""}; shot at $at")
            } + jumpTile(dev)
        }
        sc.render.write(Sheet(FOLDER, "folders", listOf("FOLDERS levels and the path line beside funeOS folders-500-*, and the jump grid over the folders base beside folders-500--jump."), tiles))
        val t5 = boards.map { dev ->
            val h = shot(sc, dev, HUB_FOLDERS)
            h.c.select(HUB_ALBUMS)
            h.c.switchTo(HUB_FOLDERS, T5_AT, 1)
            h.shown = T5_AT
            tile("t5-300-folders", h, T5_AT + T5_MS, "as funeOS t5:300:folders: the hub on ALBUMS, switch_pivot to FOLDERS at 2800 (the thumb shown), 300 ms in: the strip, the rows shift and the rows fade have ended, so the arriving pane is at rest")
        }
        sc.render.write(Sheet(FOLDER, "t5-folders", listOf("The pivot switch from ALBUMS to FOLDERS 300 ms in, beside funeOS t5-300-folders."), t5))
    }

    private fun jumpTile(dev: Dev): Tile {
        val h = shot(sc, dev, HUB_FOLDERS)
        val s = h.s
        val g = JumpGrid().also { it.open(h.m.avail(), h.m.focusSection(), s.stdH) }
        fun style(r: Role) = roleStyle(r, s, sc.density, KitInk.white, ppu = s.stdPpu)
        val ink = JumpInk(
            sc.measurer.word("exit", Roles.jumpExit, style(Roles.jumpExit), sc.fonts, sc.density, null),
            (0 until Section.count).map { sc.measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), sc.fonts, sc.density, sc.covers) },
        )
        val rec = sc.regions(dev, s).also {
            it.residual = true
            it.residualWhy = JUMP_LOUD
        }
        sc.fill(h.m, h.d)
        return sc.tile(FOLDER, dev, "folders-500--jump", s, listOf("as funeOS folders:500::jump: the jump grid opened at 2600 over the folders base on its focused section, 500 ms in"), rec) {
            rec.hide("under the jump grid's opaque ground") { page(h, JUMP_OPEN, rec) }
            jumpGrid(g, ink, JumpCells(s), s, g.offset, JUMP_IN)
            rec.jump("jump", g, ink, JumpCells(s), s, g.offset, JUMP_IN, dev.h)
        }
    }

    @Test
    fun funeOsPressScenes() {
        val tiles = boards.flatMap { dev ->
            listOf(Chrome.HEADER to "header", HUB_ARTISTS to "pivot").flatMap { (part, item) ->
                listOf("down" to PRESS_AT + DOWN_MS, "held" to PRESS_AT + HELD_MS).map { (phase, at) ->
                    val h = shot(sc, dev, HUB_ARTISTS)
                    h.c.press(part)
                    tile("press-0-$item-$phase", h, at, "as funeOS press:0:$item:$phase: the hub on ARTISTS, a down on the $item at 3000 held ${at - PRESS_AT} ms; the thumb shown at the T2 swap fades from 2812")
                }
            } + run {
                val h = shot(sc, dev, HUB_ARTISTS)
                h.c.press(HUB_ARTISTS)
                h.c.lift()
                h.m.toTop(PRESS_AT + HELD_MS)
                h.c.settle()
                h.c.tick(PRESS_AT + HELD_MS, false)
                listOf(tile("press-0-pivot-up", h, PRESS_AT + UP_MS, "as funeOS press:0:pivot:up: the active ARTISTS pivot tapped at 3000 and lifted at 3150, the list already at its top, so nothing moves; shot at 3170"))
            }
        }
        sc.render.write(Sheet(FOLDER, "press", listOf("Header and pivot presses on the hub beside funeOS press-0-header-down, -held and press-0-pivot-down, -held, -up."), tiles))
    }

    @Test
    fun funeOsNavBarScenes() {
        val tiles = boards.flatMap { dev ->
            val hub = tile("navbar-hub", shot(sc, dev, HUB_ARTISTS, Nav.Buttons), HUB_SHOT, "as funeOS hub:navon: NAV BAR ON, the ARTISTS pivot; shot at 3600")
            val songs = shot(sc, dev, HUB_SONGS, Nav.Buttons)
            val at = songs.knobs(SONG_STEPS, KNOBS_AT) + SETTLE
            val nine = tile("navbar-hub-songs", songs, at, "as funeOS hub:songs:9:navon: NAV BAR ON, nine knob steps from the # row at 2600, scrolled into view above the nav band, the thumb holding; shot at $at")
            val end = shot(sc, dev, HUB_SONGS, Nav.Buttons)
            val last = requireNotNull(end.m.pane.map).count - 1
            end.m.pane.setFocus(last, 0, KNOBS_AT, false)
            end.m.pane.track.at(Int.MAX_VALUE)
            val tail = tile("navbar-hub-end", end, KNOBS_AT + KNOB + SETTLE, "as funeOS hub:songs:@end:navon: NAV BAR ON, the last song focused at 2600 with the list set to its end; shot at 3720")
            val albums = tile("navbar-hub-albums", shot(sc, dev, HUB_ALBUMS, Nav.Buttons), HUB_SHOT, "as funeOS hub:albums:navon: NAV BAR ON, the ALBUMS grid; shot at 3600")
            val play = shot(sc, dev, HUB_SONGS, Nav.Buttons)
            val sel = play.knobs(SONG_STEPS, KNOBS_AT) + SETTLE
            play.select(sel)
            val playing = tile("navbar-playing", play, sel + SETTLE, "as funeOS hub:songs:9:play:navon: NAV BAR ON, nine knob steps, the song selected at $sel and playing; shot at ${sel + SETTLE}")
            listOf(hub, nine, tail, albums, playing)
        }
        sc.render.write(Sheet(FOLDER, "navbar", listOf("The hub with NAV BAR ON on a Buttons window whose bar is navH tall, beside funeOS navbar-hub, -hub-songs, -hub-end, -hub-albums and navbar-playing."), tiles))
    }

    @Test
    fun extrapolatedSizes() {
        val devs = listOf(
            Dev("1080x2160", "1080 x 2160 phone, 480 u at ppu 4.5", PHONE_W, PHONE_H, listOf(Nav.None)),
            Dev("1080x2520", "1080 x 2520 phone, 560 u at ppu 4.5", PHONE_W, TALL_H, listOf(Nav.None)),
        )
        val pivots = listOf(HUB_ALBUMS, HUB_ARTISTS, HUB_SONGS, HUB_FOLDERS)
        val tiles = devs.flatMap { dev ->
            pivots.map { p ->
                val h = shot(sc, dev, p)
                sc.fill(h.m, h.d)
                val name = MusicPage.Hub.pivots[p]
                sc.render.tile(SIZES, "${dev.id}-hub-$name", dev.label, listOf(sc.render.facts(dev, Nav.None, h.s), "the $name pivot as the hub opens it, unpaired"), sc.draw(dev) {
                    page(h, HUB_SHOT, null)
                }, emptyList(), h.s.stdPpu, null)
            }
        }
        sc.render.write(Sheet(SIZES, "sizes", listOf("The hub at the phone sizes past the boards, no funeOS reference."), tiles))
        sc.render.index(SIZES, listOf(
            "# Music hub at phone sizes", "",
            "- `$SIZES/sizes.png`: ALBUMS, ARTISTS, SONGS and FOLDERS at 1080 x 2160 (480 u) and 1080 x 2520 (560 u), unpaired",
        ))
    }

    companion object {
        private const val FOLDER = "music"
        private const val SIZES = "music/hub"
        private const val STEP = 16L
        private const val SHOWN = 1300L
        private const val KNOB = 120L
        private const val KNOBS_AT = 2600L
        private const val SETTLE = 1000L
        private const val SONG_STEPS = 9
        private const val HUB_SHOT = 3600L
        private const val EMPTY_SHOT = 3600L
        private const val SCAN_SHOT = 3000L
        private const val SCAN_MANY = 12873
        private const val LIT_SHOT = 3900L
        private const val FOLDERS_AT = 2600L
        private const val T5_AT = 2800L
        private const val T5_MS = 300L
        private const val SELECT_GAP = 20L
        private const val ENTER_GAP = 400L
        private const val FOLDER_SHOT = 500L
        private const val JUMP_OPEN = 2600L
        private const val JUMP_IN = 500L
        private const val PRESS_AT = 3000L
        private const val DOWN_MS = 50L
        private const val HELD_MS = 150L
        private const val UP_MS = 170L
        private const val PHONE_W = 1080
        private const val PHONE_H = 2160
        private const val TALL_H = 2520
        private const val CJK_INDEX = 1204
        private const val LIT_AT = 900L
        private const val LIVE_DIR = "Evening_Sessions_Recorded_Live_In_Nara"
        private const val LIVE_SUB = "Extra"
        private const val ARTISTS_QUIET = "the funeOS hub draws its fixture's album covers in the artist row thumb strips, which the regions skip (they show this fixture's art and album counts)"
        private const val ALBUMS_QUIET = "the ALBUMS grid tiles draw this fixture's covers, gated on their edges only"
        private const val LIST_LOUD = "the pane shows only list rows, every one drawn, besides the chrome and the thumb"
        private const val EMPTY_LOUD = "the pane with no songs draws only the chrome and the status line"
        private const val TOAST_LOUD = "the pane shows only list rows, every one drawn, besides the chrome, the thumb and the track toast, whose strip covers the rows under it"
        private const val JUMP_LOUD = "the opaque jump grid covers the page, so every funeOS mark on it is a kit element"
    }
}
