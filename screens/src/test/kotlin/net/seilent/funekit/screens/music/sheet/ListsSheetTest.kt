package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.sheet.RowSheets.Companion.SHEET_SHOT
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Phones
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
class ListsSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)
    private val bare by lazy { MusicScenes(FakeSet.DemoNoPlaylists) }

    private fun lists(h: PlaylistsShot, r: Int): Row = requireNotNull(h.m.rows.list(r)) { "playlists row $r is no list" }

    private fun head(l: Row): Row = Row(l.id, Kind.Playlist, l.title, "", null, writable = l.writable)

    private fun listTile(board: Dev, kind: String, large: Boolean, rows: Int, shot: Long, note: String, scenes: MusicScenes = sc, card: Card = Card.Present): Tile {
        val dev = scenes.same(board)
        val h = PlaylistsShot(scenes, dev, large = large)
        h.m.card = card
        for (i in 0 until rows) h.knob(i + 1, LISTS_AT + i * SHEET_KNOB)
        val rec = scenes.regionsOn(dev, h.s, true, S6_LOUD)
        return scenes.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS ${kind.removePrefix("large-").replace('-', ':')}: $note; shot at $shot"), rec) { with(h) { page(shot, rec) } }
    }

    private fun favsSheet(dev: Dev, kind: String, large: Boolean): Tile {
        val h = PlaylistsShot(sc, dev, large = large)
        h.knob(FAVS_ROW, LISTS_AT)
        val open = LISTS_AT + SHEET_KNOB
        val ms = sc.sheetOf(h.s, requireNotNull(h.m.target(FAVS_ROW)), open)
        val shot = open + SHEET_SHOT
        val rec = sc.regionsOn(dev, h.s, false, S6_SHEET)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS playlists:500:sheet: one step at 3160 to FAVORITES, its sheet at $open; shot at $shot", S6_SHEET), rec) {
            overPage(sc, rec, ms, h.s, shot, null, null) { pr -> with(h) { page(shot, pr) } }
        }
    }

    private fun listPlay(dev: Dev, kind: String, large: Boolean): Tile {
        val probe = PlaylistsShot(sc, dev, large = large)
        val l = lists(probe, LONG_ROW)
        val listing = Listing.PlaylistTracks(l.id)
        val h = PlaylistsShot(sc, dev, large = large, origin = listing)
        h.knob(LONG_ROW, LISTS_AT + SHEET_KNOB)
        h.knob(LONG_ROW + 1, LIST_STEP)
        val toast = sc.sceneToast(sc.firstTrack(listing), h.s, LIST_PLAY)
        val rec = sc.regionsOn(dev, h.s, true, S6_LOUD)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS playlists:500:play: steps at 3160 and 3280 to the long list, its sheet at 3400, play at $LIST_PLAY in place (the list's row takes the accent), a step at $LIST_STEP; shot at $LIST_PLAY_SHOT"), rec) {
            with(h) { page(LIST_PLAY_SHOT, rec) }
            sceneToast(toast, h.s, LIST_PLAY_SHOT, rec)
        }
    }

    private fun night(dev: Dev, large: Boolean): Row = lists(PlaylistsShot(sc, dev, large = large), NIGHT_ROW)

    private fun viewTile(dev: Dev, kind: String, large: Boolean, fav: Boolean, play: Boolean, empty: Boolean = false): Tile {
        val listing: Listing
        val head: Row?
        if (fav) {
            listing = Listing.Favorites
            head = null
        } else {
            val l = night(dev, large)
            listing = Listing.PlaylistTracks(l.id)
            head = head(l)
        }
        val h = ViewShot(sc, dev, listing, head, large = large, empty = empty)
        val base = if (fav) FAV_AT else VIEW_AT
        val shot = base + SHEET_SHOT + when {
            play -> PLAY_SHOT
            empty -> EMPTY_REST
            else -> 0L
        }
        val toast = if (play) {
            val r = requireNotNull(h.m.row(0)) { "the view has no row 0" }
            h.m.current = r.id
            sc.sceneToast(r, h.s, base + SHEET_SHOT)
        } else {
            null
        }
        val rec = sc.regionsOn(dev, h.s, true, S6_LOUD)
        val what = when {
            empty -> "the Night Drive view refreshed empty at $base"
            fav -> "FAVORITES opened from its sheet, the view at rest from $base"
            else -> "Night Drive opened from its sheet (this fixture's stand in for Road Trip), the view at rest from $base"
        }
        val playNote = if (play) ", row 0 played in place at ${base + SHEET_SHOT}" else ""
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS ${kind.removePrefix("large-").replace('-', ':')}: $what$playNote; shot at $shot"), rec) {
            with(h) { page(shot, rec) }
            toast?.let { sceneToast(it, h.s, shot, rec) }
        }
    }

    private fun viewSheet(dev: Dev, kind: String, large: Boolean): Tile {
        val l = night(dev, large)
        val h = ViewShot(sc, dev, Listing.PlaylistTracks(l.id), head(l), large = large)
        h.knob(1, VIEW_AT)
        val ms = sc.sheetOf(h.s, requireNotNull(h.m.target(1)), VIEW_SHEET)
        val shot = VIEW_SHEET + SHEET_SHOT
        val rec = sc.regionsOn(dev, h.s, false, S6_SHEET)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS plview:500:sheet: the Night Drive view, a step at $VIEW_AT to row 1, its entry sheet at $VIEW_SHEET; shot at $shot", S6_SHEET), rec) {
            overPage(sc, rec, ms, h.s, shot, null, null) { pr -> with(h) { page(shot, pr) } }
        }
    }

    private fun pressTile(dev: Dev, kind: String, large: Boolean, item: String, phase: String, offset: Long): Tile {
        val down: Long
        val s: Scale
        val draw: DrawScope.(Long, Regions) -> Unit
        if (item == "playlists") {
            val h = PlaylistsShot(sc, dev, large = large)
            down = LISTS_AT
            h.m.pane.down(FAVS_ROW, h.m.size, down)
            upAt(phase)?.let { h.m.pane.press.tap(down + it) }
            s = h.s
            draw = { now, rec -> with(h) { page(now, rec) } }
        } else {
            val h = ViewShot(sc, dev, Listing.Favorites, null, large = large)
            down = FAV_AT
            h.m.pane.down(0, h.m.total, down)
            upAt(phase)?.let { h.m.pane.press.tap(down + it) }
            s = h.s
            draw = { now, rec -> with(h) { page(now, rec) } }
        }
        val shot = down + offset
        val opens = upAt(phase) != null
        val rec = sc.regionsOn(dev, s, !opens, if (opens) S6_OPEN else S6_LOUD)
        val row = if (item == "playlists") "row 1 (FAVORITES) of the Playlists page" else "row 0 of the FAVORITES view"
        return sc.s6Tile(FOLDER, dev, kind, s, listOfNotNull("as funeOS ${kind.removePrefix("large-").replace('-', ':')}: a touch on $row at $down; shot at $shot", if (opens) S6_OPEN else null), rec) { draw(shot, rec) }
    }

    private fun tile(dev: Dev, kind: String, large: Boolean): Tile {
        val k = (if (large) "large-" else "") + kind
        val press = Regex("rowpress-0-(playlists|favorites)-(\\w+)").matchEntire(kind)
        if (press != null) {
            val (item, phase) = press.destructured
            return pressTile(dev, k, large, item, phase, PRESS_PHASES.first { it.first == phase }.second)
        }
        return when (kind) {
            "playlists-500" -> listTile(dev, k, large, 0, LISTS_AT + SHEET_SHOT, "playlists selected at $LISTS_SELECT, row 0 focused")
            "playlists-500-1" -> listTile(dev, k, large, 1, LISTS_AT + SHEET_KNOB + SHEET_SHOT, "one step from 3160 to row 1, FAVORITES")
            "playlists-500-2" -> listTile(dev, k, large, 2, LISTS_AT + 2 * SHEET_KNOB + SHEET_SHOT, "two steps from 3160 to row 2, the long list")
            "playlists-500-3" -> listTile(dev, k, large, 3, LISTS_AT + 3 * SHEET_KNOB + SHEET_SHOT, "three steps from 3160 to row 3, Night Drive (this fixture's stand in for Road Trip)")
            "playlists-500-none" -> listTile(dev, k, large, 0, LISTS_AT + SHEET_SHOT, "no lists loaded (this fixture's DemoNoPlaylists): now playing and FAVORITES, 0 playlists", bare)
            "playlists-500-sheet" -> favsSheet(dev, k, large)
            "playlists-500-play" -> listPlay(dev, k, large)
            "nocard-500-plpage" -> listTile(dev, k, large, 0, LISTS_AT + SHEET_SHOT, "the card out, the Playlists page with its status line and no count", card = Card.Out)
            "plview-500" -> viewTile(dev, k, large, fav = false, play = false)
            "plview-500-play" -> viewTile(dev, k, large, fav = false, play = true)
            "plview-500-sheet" -> viewSheet(dev, k, large)
            "plview-500-fav" -> viewTile(dev, k, large, fav = true, play = false)
            "plview-500-fav-play" -> viewTile(dev, k, large, fav = true, play = true)
            "plview-500-empty" -> viewTile(dev, k, large, fav = false, play = false, empty = true)
            else -> error("no scene $kind")
        }
    }

    @Test
    fun playlistsAndViewScenes() {
        for (kind in KINDS) {
            val tiles = listOf(sc.r1, sc.a105, sc.v1).map { tile(it, kind, false) }
            sc.render.write(Sheet(FOLDER, kind, listOf("The scene $kind on A105, R1 and V1. $S6_NONE."), tiles))
        }
        for (kind in LARGE) {
            sc.render.write(Sheet(FOLDER, "large-$kind", listOf("The scene $kind on V1 at SIZE LARGE. $S6_NONE."), listOf(tile(sc.v1, kind, true))))
        }
    }

    @Test
    fun phonePagesForReview() {
        val tiles = mutableListOf<Tile>()
        for ((dev, nav) in listOf(Phones.p2160Page to Nav.Gesture, Phones.p2160ButtonsPage to Nav.Buttons)) {
            for (large in listOf(false, true)) {
                val h = PlaylistsShot(sc, dev, nav, large)
                val size = if (large) "large" else "standard"
                val form = "$size-${"$nav".lowercase()}"
                tiles += sc.reviewTile(REVIEW, dev, "playlists-$form", h.s, listOf("Playlists on the ${dev.label} at $size, $nav navigation")) { with(h) { page(LISTS_AT + SHEET_SHOT, null) } }
                val l = lists(h, NIGHT_ROW)
                val v = ViewShot(sc, dev, Listing.PlaylistTracks(l.id), head(l), nav, large)
                tiles += sc.reviewTile(REVIEW, dev, "plview-$form", v.s, listOf("the Night Drive view on the ${dev.label} at $size, $nav navigation")) { with(v) { page(VIEW_AT + SHEET_SHOT, null) } }
            }
        }
        sc.render.write(Sheet(REVIEW, "lists-phone", listOf("FALLBACK on the phone page base with gesture and button navigation: the Playlists page and a playlist view, review only."), tiles))
    }

    companion object {
        private const val FOLDER = "lists"
        private const val REVIEW = "musics6"
        private const val FAVS_ROW = 1
        private const val LONG_ROW = 2
        private const val NIGHT_ROW = 3
        private const val LIST_PLAY = 4000L
        private const val LIST_STEP = 4800L
        private const val LIST_PLAY_SHOT = 5420L
        private const val VIEW_AT = 6400L
        private const val VIEW_SHEET = 6820L
        private const val FAV_AT = 6160L
        private const val EMPTY_REST = 600L
        val KINDS = listOf(
            "playlists-500", "playlists-500-1", "playlists-500-2", "playlists-500-3", "playlists-500-none", "playlists-500-sheet", "playlists-500-play", "nocard-500-plpage",
            "plview-500", "plview-500-play", "plview-500-sheet", "plview-500-fav", "plview-500-fav-play", "plview-500-empty",
        ) + listOf("playlists", "favorites").flatMap { i -> PRESS_PHASES.map { "rowpress-0-$i-${it.first}" } }
        val LARGE = listOf("playlists-500", "playlists-500-sheet", "playlists-500-play", "plview-500", "plview-500-sheet", "plview-500-empty") +
            listOf("playlists", "favorites").flatMap { i -> PRESS_PHASES.map { "rowpress-0-$i-${it.first}" } }
    }
}
