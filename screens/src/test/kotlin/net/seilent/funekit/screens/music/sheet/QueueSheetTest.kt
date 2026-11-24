package net.seilent.funekit.screens.music.sheet

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.api.Card
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.screens.music.overlay.sheetItems
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
class QueueSheetTest {
    private val sc = MusicScenes()
    private val boards = listOf(sc.r1, sc.a105)

    private fun rig(dev: Dev, large: Boolean): QueueRig = QueueRig(sc, dev, large = large).also { it.run(QueueRig.TAP_UP, listOf(QueueRig.TAP_UP to { it.push(QueueRig.TAP_UP) })) }

    private fun tile(kind: String, r: QueueRig, note: String, loud: String?, quiet: String = MOVE_QUIET, menu: MenuShot? = null, rules: List<Pair<String, String>> = emptyList()): Tile {
        val rec = sc.regions(r.dev, r.s, rules)
        val rest = r.st.phase == Phase.Rest && menu == null
        rec.residual = rest && loud != null
        rec.residualWhy = if (rec.residual) requireNotNull(loud) else quiet
        val at = r.t
        return sc.tile(FOLDER, r.dev, kind, r.s, listOf(note), rec) {
            if (menu == null) {
                with(r) { world(at, rec) }
            } else {
                overPage(sc, rec, menu, r.s, at, null, null) { pr -> with(r) { world(at, pr) } }
            }
        }
    }

    private fun queueTiles(dev: Dev, large: Boolean): List<Tile> {
        val pre = if (large) "large-" else ""
        val rest = rig(dev, large).also { it.run(QueueRig.TAP_UP + REST) }
        val seven = rig(dev, large).also { r -> r.run(QueueRig.TAP_UP + LATE, listOf(MOVE_AT to { r.knobs(SEVEN, MOVE_AT) })) }
        val empty = rig(dev, large).also { r ->
            r.run(MOVE_AT + EMPTY_SHOT, listOf(MOVE_AT to {
                runBlocking { sc.fake.player.clear() }
                r.resync(MOVE_AT)
            }))
        }
        val nocard = rig(dev, large).also { r -> r.run(MOVE_AT + EMPTY_SHOT, listOf(MOVE_AT to { r.q.card = Card.Out })) }
        val tiles = mutableListOf(
            tile("${pre}queue-1500", rest, "as funeOS queue:1500: ALBUMS cell 2's album playing from track 1 (this fixture's album at that cell, 8 tracks as funeOS's), now playing opened at 4240, its title tapped at 5240 with the up at 5290, the queue pushed with T3 and at rest on the current slot; shot 1500 ms after the up", LIST_LOUD),
            tile("${pre}queue-2500-7", seven, "as funeOS queue:2500:7: the queue at rest, seven knob steps from 6240 120 ms apart to row 7, scrolled into view; shot 2500 ms after the up", LIST_LOUD),
            tile("${pre}queue-500-empty", empty, "as funeOS queue:500:empty: the queue at rest, the player cleared at 6240, so no count line and the line nothing here; shot 500 ms after the clear", EMPTY_LOUD),
            tile("${pre}nocard-500-queue", nocard, "as funeOS nocard:500:queue: the queue at rest, the card out at 6240, so no rows, no count line and the line no card; shot 500 ms after", EMPTY_LOUD),
        )
        if (!large) {
            val push = rig(dev, false).also { it.run(QueueRig.TAP_UP + EARLY) }
            val at1 = rig(dev, false).also { r -> r.run(QueueRig.TAP_UP + REST, listOf(MOVE_AT to { r.q.place(1, MOVE_AT, false) })) }
            tiles.add(0, tile("queue-150", push, "as funeOS queue:150: the T3 push from now playing, 150 ms after the title tap's up at 5290: now playing leaves as one layer, the queue's rows, header and thumb arrive", null))
            tiles.add(2, tile("queue-1500-@1", at1, "as funeOS queue:1500:@1: the queue at rest, row 1 focused at 6240 with the list set to min(pitch, limit); shot 1500 ms after the up", LIST_LOUD))
            for ((ms, kind) in listOf(EARLY to "queuex-150", POP_REST to "queuex-500")) {
                val r = rig(dev, false).also { it.run(POP_UP + ms, listOf(POP_UP to { it.pop() })) }
                tiles += tile(kind, r, "as funeOS queuex:$ms: the queue's header word tapped at 6440 with the up at 6490, the T4 pop into now playing with every Content leg under motion.npContentDelay moved to it (npPop); shot $ms ms after the up", NP_LOUD)
            }
        }
        val steps = if (large) listOf(THREE) else listOf(0, 1, THREE)
        for (n in steps) {
            val r = rig(dev, large)
            val k = MOVE_AT + n * SHEET_KNOB
            r.run(k, listOf(MOVE_AT to { r.knobs(n, MOVE_AT) }))
            val open = k + SHEET_OPEN
            r.run(open)
            val t = r.target()
            val ms = sc.menuShot(r.s, MenuForm.Sheet, t.title, sheetItems(t, false), open)
            r.sheet = ms
            r.run(open + SHEET_SHOT)
            tiles += tile("${pre}queuesheet-500-$n", r, "as funeOS queuesheet:500:$n: $n knob steps from 6240 on the queue at rest, the row sheet opened 200 ms after ($open ms) with the items of funeOS sheet_target's queue row, which the kit builds alike; shot 500 ms after the open", null, SHEET_QUIET, ms)
        }
        return tiles
    }

    private fun pressTiles(dev: Dev): List<Tile> = listOf("down" to -1L, "held" to -1L, "tap" to TAP_UP_MS, "up" to HELD_MS).map { (phase, up) ->
        val r = rig(dev, false)
        val acts = mutableListOf<Pair<Long, () -> Unit>>(PRESS_AT to {
            r.q.focus.set(1, r.q.size, PRESS_AT, true)
            r.q.press.down(1, PRESS_AT)
            r.q.glow.arm(1, PRESS_AT)
        })
        if (up >= 0) acts += (PRESS_AT + up) to { r.tapOpen(1, PRESS_AT + up) }
        val end = PRESS_AT + when (phase) {
            "down" -> DOWN_MS
            "held" -> HELD_MS
            "tap" -> TAP_END
            else -> UP_END
        }
        r.run(end, acts)
        tile("rowpress-0-queue-$phase", r, "as funeOS rowpress:0:queue:$phase: the queue at rest, a down on row 1 at 6640${if (up >= 0) ", the up $up ms later plays its slot and turns back to the now playing under the queue with T7 (play open, popToNp), the reply at once" else ""}; shot at $end", if (up >= 0) null else LIST_LOUD, if (up >= 0) T7_QUIET else MOVE_QUIET)
    }

    @Test
    fun queueScenes() {
        val tiles = boards.flatMap { queueTiles(it, false) + pressTiles(it) } + queueTiles(sc.v1, true)
        sc.render.write(Sheet(FOLDER, "queue", listOf("The queue page, its T3 push and T4 pop, its row sheets and a row press beside funeOS queue-*, queuex-*, queuesheet-500-* and rowpress-0-queue-*."), tiles))
        sc.render.index(FOLDER, listOf(
            "# Queue", "",
            "- `$FOLDER/queue.png`: R1 and A105 `queue-150`, `-1500`, `-1500-@1`, `-2500-7`, `-500-empty`, `nocard-500-queue`, `queuex-150`, `-500`, `queuesheet-500-0`, `-1`, `-3`, `rowpress-0-queue-down`, `-held`, `-tap`, `-up`; V1 `large-queue-1500`, `-2500-7`, `-500-empty`, `large-nocard-500-queue`, `large-queuesheet-500-3`",
            "- the queue is this fixture's album at ALBUMS cell 2, 8 tracks as funeOS's 15 Dorro; the STATUS row is not drawn",
        ))
    }

    companion object {
        private const val FOLDER = "queue"
        private const val EARLY = 150L
        private const val REST = 1500L
        private const val LATE = 2500L
        private const val MOVE_AT = 6240L
        private const val EMPTY_SHOT = 500L
        private const val SEVEN = 7
        private const val THREE = 3
        private const val SHEET_OPEN = 200L
        private const val SHEET_SHOT = 500L
        private const val POP_UP = 6490L
        private const val POP_REST = 500L
        private const val PRESS_AT = 6640L
        private const val DOWN_MS = 50L
        private const val HELD_MS = 150L
        private const val TAP_UP_MS = 50L
        private const val TAP_END = 200L
        private const val UP_END = 650L
        private const val MOVE_QUIET = "mid move the leaving and arriving layers fade over each other, so funeOS ink outside a region window is part of a layer, not a difference"
        private const val SHEET_QUIET = "the sheet panel is excluded, so the residual pass has no window for the funeOS sheet's ink"
        private const val LIST_LOUD = "the queue shows only its header, count line, numbered rows and thumb on the page ground"
        private const val EMPTY_LOUD = "the empty queue shows only its header and the line nothing here"
        private const val T7_QUIET = "T7 turns the leaving queue out on the NpLeave pane and brings now playing in on its NpIn plane, both drawn inside kit quads that record no region, so funeOS ink there is part of a turned layer, not a difference"
        private const val NP_LOUD = "now playing shows only its back ring, art, bar, rings, mark and text lines on the plain ground, so every funeOS mark outside them is a difference"
    }
}
