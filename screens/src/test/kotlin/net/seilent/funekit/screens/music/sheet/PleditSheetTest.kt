package net.seilent.funekit.screens.music.sheet

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.TrackSet
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.overlay.ADDED_TO
import net.seilent.funekit.screens.music.overlay.DELETE_ASK
import net.seilent.funekit.screens.music.overlay.DELETE_PLAYLIST
import net.seilent.funekit.screens.music.overlay.KEEP_PLAYLIST
import net.seilent.funekit.screens.music.overlay.picker
import net.seilent.funekit.screens.music.sheet.RowSheets.Companion.SHEET_OPEN
import net.seilent.funekit.screens.music.sheet.RowSheets.Companion.SHEET_SHOT
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
class PleditSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)
    private val many by lazy {
        MusicScenes(FakeSet.Demo).also { m ->
            runBlocking {
                val songs = m.rowsOf(m.fake.library.view(), Listing.Songs)
                repeat(MANY) { i -> m.fake.playlists.create(TrackSet.Ids(listOf(songs[i].id))) }
            }
        }
    }

    private fun listSheet(dev: Dev, kind: String, large: Boolean, delete: Boolean): Tile {
        val h = PlaylistsShot(sc, dev, large = large)
        for (i in 0 until NIGHT_ROW) h.knob(i + 1, LISTS_AT + i * SHEET_KNOB)
        val t = requireNotNull(h.m.target(NIGHT_ROW))
        val ms = if (delete) {
            sc.menuShot(h.s, MenuForm.Dlg, DELETE_ASK, listOf(DELETE_PLAYLIST, KEEP_PLAYLIST), ASK_AT, confirm = true, steps = 1)
        } else {
            sc.sheetOf(h.s, t, LIST_SHEET)
        }
        val shot = if (delete) ASK_AT + SHEET_SHOT else LIST_SHEET_SHOT
        val note = if (delete) {
            "the Night Drive sheet at $LIST_SHEET, five steps from 4120 to delete playlist, selected at $ASK_AT: the confirm with keep playlist focused"
        } else {
            "three steps from $LISTS_AT to row 3, Night Drive (this fixture's stand in for Road Trip), its sheet at $LIST_SHEET"
        }
        val rec = sc.regionsOn(dev, h.s, false, S6_SHEET)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS ${kind.removePrefix("large-").replace('-', ':')}: $note; shot at $shot", S6_SHEET), rec) {
            overPage(sc, rec, ms, h.s, shot, null, null) { pr -> with(h) { page(shot, pr) } }
        }
    }

    private fun pick(board: Dev, kind: String, large: Boolean, scenes: MusicScenes, steps: Int, made: Boolean, readOnly: Boolean = false): Tile {
        val dev = scenes.same(board)
        val h = scenes.hubShot(dev, HUB_SONGS, large = large)
        val open = h.knobs(SONG_STEPS, KNOBS_AT) + SHEET_OPEN
        val t = h.target(h.m.pane.focus.row)
        val items = runBlocking { picker(scenes.source) }.items
        val shot = if (made) PICKED + PICK_REST else PICKED + SHEET_SHOT + steps * SHEET_KNOB
        val rec = scenes.regionsOn(dev, h.s, made, if (made) S6_LOUD else S6_SHEET)
        val base = "three steps on SONGS from $KNOBS_AT to row 3, its song sheet at $open, add to playlist at $PICK_AT swapping in the picker"
        if (made) {
            val toast = if (readOnly) scenes.sceneToast(READ_ONLY_LINE, "", h.s, PICKED, false) else scenes.sceneToast(ADDED_TO + FIRST_LIST, t.title, h.s, PICKED, false)
            val note = if (readOnly) {
                "unpaired, funeOS's shot set has no read only toast: $base, new playlist picked at $PICKED on a read only card; the picker closed, no list made, and the toast shows the device's line $READ_ONLY_LINE over no second line (FB-refusedline); shot at $shot"
            } else {
                "as funeOS pledit:500:new: $base, new playlist picked at $PICKED; the picker closed and the toast names the list made; shot at $shot"
            }
            return scenes.s6Tile(FOLDER, dev, kind, h.s, listOf(note), rec) {
                with(h) { page(shot, rec) }
                sceneToast(toast, h.s, shot, rec)
            }
        }
        val ms = scenes.menuShot(h.s, MenuForm.Sheet, t.title, items, PICK_AT, steps = steps, stepAt = PICKED + steps * SHEET_KNOB - SHEET_KNOB)
        val more = if (steps > 0) ", six lists of one track each made at 900, $steps steps from $PICKED" else ""
        return scenes.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS ${kind.removePrefix("large-").replace('-', ':')}: $base$more; shot at $shot", S6_SHEET), rec) {
            overPage(scenes, rec, ms, h.s, shot, null, null) { pr -> with(h) { page(shot, pr) } }
        }
    }

    private fun tile(dev: Dev, kind: String, large: Boolean): Tile {
        val k = (if (large) "large-" else "") + kind
        return when (kind) {
            "pledit-500-pick" -> pick(dev, k, large, sc, 0, false)
            "pledit-500-many" -> pick(dev, k, large, many, MANY_STEPS, false)
            "pledit-500-new" -> pick(dev, k, large, sc, 0, true)
            "pledit-500-sheet" -> listSheet(dev, k, large, false)
            "pledit-500-delete" -> listSheet(dev, k, large, true)
            "pledit-500-readonly" -> pick(dev, k, large, sc, 0, true, true)
            else -> error("no scene $kind")
        }
    }

    @Test
    fun pleditScenes() {
        for (kind in KINDS) {
            sc.render.write(Sheet(FOLDER, kind, listOf("The scene $kind on A105, R1 and V1. $S6_NONE."), listOf(sc.r1, sc.a105, sc.v1).map { tile(it, kind, false) }))
        }
        for (kind in LARGE) {
            sc.render.write(Sheet(FOLDER, "large-$kind", listOf("The scene $kind on V1 at SIZE LARGE. $S6_NONE."), listOf(tile(sc.v1, kind, true))))
        }
    }

    companion object {
        private const val FOLDER = "pledit"
        private const val NIGHT_ROW = 3
        private const val LIST_SHEET = 3520L
        private const val LIST_SHEET_SHOT = 4620L
        private const val ASK_AT = 4720L
        private const val PICK_AT = 4120L
        private const val PICKED = 4720L
        private const val PICK_REST = 600L
        private const val SONG_STEPS = 3
        private const val MANY = 6
        private const val MANY_STEPS = 5
        private const val FIRST_LIST = "playlist 1"
        private const val READ_ONLY_LINE = "card read only"
        val KINDS = listOf("pledit-500-pick", "pledit-500-many", "pledit-500-new", "pledit-500-sheet", "pledit-500-delete", "pledit-500-readonly")
        val LARGE = listOf("pledit-500-many", "pledit-500-delete")
    }
}
