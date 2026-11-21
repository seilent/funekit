package net.seilent.funekit.screens.music.sheet

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.lists.HISTORY_NEW
import net.seilent.funekit.screens.music.lists.HISTORY_RECENT
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
class HistorySheetTest {
    private val sc = MusicScenes(FakeSet.Demo)

    private fun funeOs(kind: String) = kind.removePrefix("large-").replace('-', ':')

    private fun page(dev: Dev, kind: String, large: Boolean, steps: Int = 0, newPane: Boolean = false, play: Boolean = false, noPlays: Boolean = false, noNew: Boolean = false, note: String): Tile {
        val h = HistoryShot(sc, dev, large = large, noPlays = noPlays, noNew = noNew)
        if (newPane) h.switch(HISTORY_NEW, HISTORY_AT)
        for (i in 0 until steps) h.knob(i + 1, HISTORY_AT + i * SHEET_KNOB)
        val base = if (newPane) HISTORY_AT + NEW_REST else HISTORY_AT + steps * SHEET_KNOB + SHEET_SHOT
        val toast = if (play) {
            val p = h.m.pivot
            val r = requireNotNull(h.m.rows(p)?.getOrNull(h.m.pane.focus.row)) { "history row ${h.m.pane.focus.row} is gone" }
            val track = if (p == HISTORY_RECENT) r else sc.firstTrack(Listing.AlbumTracks(r.id))
            h.m.current = track.id
            h.m.playing = runBlocking { h.v.trackIds(track.id) }
            sc.sceneToast(track, h.s, base)
        } else {
            null
        }
        val shot = if (play) base + PLAY_SHOT else base
        val rec = sc.regionsOn(dev, h.s, true, S6_LOUD)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS ${funeOs(kind)}: $note; shot at $shot"), rec) {
            with(h) { page(shot, rec) }
            toast?.let { sceneToast(it, h.s, shot, rec) }
        }
    }

    private fun sheet(dev: Dev, kind: String, large: Boolean): Tile {
        val h = HistoryShot(sc, dev, large = large)
        h.knob(1, HISTORY_AT)
        val open = HISTORY_AT + SHEET_KNOB
        val ms = sc.sheetOf(h.s, requireNotNull(h.m.target(1)), open)
        val shot = open + SHEET_SHOT
        val rec = sc.regionsOn(dev, h.s, false, S6_SHEET)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS history:500:sheet: a step at $HISTORY_AT to RECENT row 1, its song sheet at $open; shot at $shot", S6_SHEET), rec) {
            overPage(sc, rec, ms, h.s, shot, null, null) { pr -> with(h) { page(shot, pr) } }
        }
    }

    private fun t5(dev: Dev, kind: String, large: Boolean): Tile {
        val h = HistoryShot(sc, dev, large = large)
        h.switch(HISTORY_NEW, HISTORY_AT)
        val shot = HISTORY_AT + T5_IN
        val rec = sc.regionsOn(dev, h.s, false, S6_T5)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("as funeOS history:150:t5: switch_pivot to NEW at $HISTORY_AT; shot $T5_IN ms in", S6_T5), rec) { with(h) { page(shot, rec) } }
    }

    private fun press(dev: Dev, kind: String, large: Boolean, phase: String, offset: Long): Tile {
        val h = HistoryShot(sc, dev, large = large)
        h.m.pane.down(1, h.m.count(HISTORY_RECENT), HISTORY_AT)
        upAt(phase)?.let { h.m.pane.press.tap(HISTORY_AT + it) }
        val shot = HISTORY_AT + offset
        val opens = upAt(phase) != null
        val rec = sc.regionsOn(dev, h.s, !opens, if (opens) S6_OPEN else S6_LOUD)
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOfNotNull("as funeOS ${funeOs(kind)}: a touch on RECENT row 1 at $HISTORY_AT; shot at $shot", if (opens) S6_OPEN else null), rec) { with(h) { page(shot, rec) } }
    }

    private fun tile(dev: Dev, kind: String, large: Boolean): Tile {
        val k = (if (large) "large-" else "") + kind
        PRESS_PHASES.firstOrNull { kind == "rowpress-0-history-${it.first}" }?.let { return press(dev, k, large, it.first, it.second) }
        return when (kind) {
            "history-500" -> page(dev, k, large, note = "history selected at $HISTORY_SELECT, RECENT row 0")
            "history-500-3" -> page(dev, k, large, steps = 3, note = "three steps from $HISTORY_AT to RECENT row 3")
            "history-500-3-play" -> page(dev, k, large, steps = 3, play = true, note = "three steps from $HISTORY_AT to RECENT row 3, played in place")
            "history-500-empty" -> page(dev, k, large, noPlays = true, note = "no plays: RECENT empty, nothing played yet")
            "history-500-empty-new" -> page(dev, k, large, newPane = true, noPlays = true, noNew = true, note = "no plays and no new albums, switch_pivot to NEW at $HISTORY_AT: NEW empty, nothing here")
            "history-500-new" -> page(dev, k, large, newPane = true, note = "switch_pivot to NEW at $HISTORY_AT, NEW row 0")
            "history-500-new-play" -> page(dev, k, large, newPane = true, play = true, note = "switch_pivot to NEW at $HISTORY_AT, NEW row 0 played in place, its album whole")
            "history-500-sheet" -> sheet(dev, k, large)
            "history-150-t5" -> t5(dev, k, large)
            else -> error("no scene $kind")
        }
    }

    @Test
    fun historyScenes() {
        for (kind in KINDS) {
            sc.render.write(Sheet(FOLDER, kind, listOf("The scene $kind on A105, R1 and V1. $S6_NONE."), listOf(sc.r1, sc.a105, sc.v1).map { tile(it, kind, false) }))
        }
        for (kind in LARGE) {
            sc.render.write(Sheet(FOLDER, "large-$kind", listOf("The scene $kind on V1 at SIZE LARGE. $S6_NONE."), listOf(tile(sc.v1, kind, true))))
        }
    }

    companion object {
        private const val FOLDER = "history"
        private const val NEW_REST = 1500L
        private const val T5_IN = 150L
        val KINDS = listOf(
            "history-500", "history-500-3", "history-500-3-play", "history-500-empty", "history-500-empty-new",
            "history-500-new", "history-500-new-play", "history-500-sheet", "history-150-t5",
        ) + PRESS_PHASES.map { "rowpress-0-history-${it.first}" }
        val LARGE = listOf("history-500", "history-500-empty", "history-500-empty-new", "history-500-sheet", "history-500-new-play") +
            PRESS_PHASES.map { "rowpress-0-history-${it.first}" }
    }
}
