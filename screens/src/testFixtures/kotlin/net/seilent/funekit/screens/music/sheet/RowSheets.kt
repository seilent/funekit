package net.seilent.funekit.screens.music.sheet

import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.hub.HUB_FOLDERS
import net.seilent.funekit.screens.music.hub.HUB_GENRES
import net.seilent.funekit.screens.music.hub.HUB_PLAYLISTS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.overlay.ADD
import net.seilent.funekit.screens.music.overlay.PIN
import net.seilent.funekit.screens.music.overlay.PLAY
import net.seilent.funekit.screens.music.overlay.PLAY_NEXT
import net.seilent.funekit.screens.music.overlay.SHUFFLE
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Tile

class RowSheets(val sc: MusicScenes) {
    class HubSheet(val kind: String, val pivot: Int, val steps: Int, val funeOs: List<String>?, val why: String, val note: String)

    val hubSheets = listOf(
        HubSheet("sheet-500-albums", HUB_ALBUMS, ALBUM_STEPS, OLD_ALBUM, ALBUM_PANEL, "two knob steps on ALBUMS from 2600 to cell 2, this fixture's album there; the sheet opened at 3040"),
        HubSheet("sheet-500-songs", HUB_SONGS, SONG_STEPS, OLD_SONG_ITEMS, SONG_SHORT, "three knob steps on SONGS from 2600 to row 3; the sheet opened at 3160"),
        HubSheet("sheet-500-genres", HUB_GENRES, 1, GENRE_ITEMS, GENRE_PANEL, "one knob step on GENRES from 2600 to row 1, the second genre (funeOS v0.4.3 draws the genre list with no dividers); the sheet opened at 2920"),
        HubSheet("sheet-500-playlists", HUB_PLAYLISTS, 1, OLD_PLAYLIST, PLAYLIST_PANEL, "one knob step on PLAYLISTS from 2600 to row 1, FAVORITES with this fixture's favorites; the sheet opened at 2920"),
        HubSheet("sheet-500-folders", HUB_FOLDERS, 1, OLD_FOLDER, FOLDER_PANEL, "one knob step on FOLDERS from 2600 to row 1, the first folder; the sheet opened at 2920"),
    )

    fun hubSheet(dev: Dev, hs: HubSheet, large: Boolean, folder: String, paired: Boolean = true): Tile {
        val h = sc.hubShot(dev, hs.pivot, large = large)
        val col: Int
        val open: Long
        if (hs.pivot == HUB_ALBUMS) {
            col = hs.steps
            h.m.pane.setFocus(0, col, KNOBS_AT + (hs.steps - 1) * SHEET_KNOB, false)
            open = KNOBS_AT + hs.steps * SHEET_KNOB + SHEET_OPEN
        } else {
            col = 0
            open = h.knobs(hs.steps, KNOBS_AT) + SHEET_OPEN
        }
        val t = h.target(h.m.pane.focus.row, col)
        val ms = sc.menuShot(h.s, MenuForm.Sheet, t.title, h.items(t), open)
        val shot = open + SHEET_SHOT
        val rec = if (paired) sc.regions(dev, h.s, listOf("sheet.thumb" to hs.why)).also { it.residualWhy = hs.why } else null
        val kind = (if (large) "large-" else "") + hs.kind
        return sc.tile(folder, dev, kind, h.s, listOf("as funeOS ${hs.kind.replace('-', ':')}: ${hs.note}; shot 500 ms after the open", hs.why), rec) {
            overPage(sc, rec, ms, h.s, shot, hs.funeOs, hs.why) { pr -> with(h) { page(shot, pr) } }
        }
    }

    companion object {
        const val SHEET_OPEN = 200L
        const val SHEET_SHOT = 500L
        const val ALBUM_STEPS = 2
        const val SONG_STEPS = 3
        const val OPEN_PLAYLIST = "open playlist"
        val V1_SHEETS = setOf("sheet-500-folders", "sheet-500-songs", "sheet-500-playlists")
        val OLD_ALBUM = listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_PLAYLIST, PIN)
        val OLD_FOLDER = listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_PLAYLIST)
        val OLD_PLAYLIST = listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, OPEN_PLAYLIST)
        val GENRE_ITEMS = listOf(PLAY, PLAY_NEXT, ADD, ADD_PLAYLIST)
        const val ALBUM_PANEL = "funeOS v0.4.3 draws the album sheet with 6 rows, as the kit does, so the panel and every page region under it are excluded"
        const val PLAYLIST_PANEL = "funeOS v0.4.3 draws the FAVORITES sheet with 5 rows, as the kit does, so the panel and every page region under it are excluded"
        const val FOLDER_PANEL = "funeOS v0.4.3 draws the folder sheet with 5 rows, as the kit does, so the panel and every page region under it are excluded"
        const val GENRE_PANEL = "funeOS v0.4.3 draws the genre sheet with 4 rows, as the kit does, so the panel and every page region under it are excluded"
    }
}
