package net.seilent.funekit.kit.saver

import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.Executor

data class SaverTile(val art: ImageBitmap, val w: Int, val h: Int, val angle: Int)

internal class Laid(
    val first: Boolean,
    val key: Long,
    val gen: Int,
    val from: SaverScene,
    val twin: SaverScenes,
)

class SaverLay(private val s: SaverScenes, private val worker: Executor, private val tiles: (SaverTile) -> Unit = {}) {
    @Volatile
    private var laid: Laid? = null
    @Volatile
    private var asked: Laid? = null
    internal var takes = 0

    init {
        s.lay = this
        s.tiles = tiles
    }

    private fun twin(): SaverScenes = SaverScenes(s.board, s.ppu, s.widthPx, s.heightPx, s.glyphs).also {
        it.specs = s.specs
        it.setTrack(s.wantTitle, s.wantArtist, s.wantAlbum, s.wantPhone)
        val p = s.pos?.invoke()
        if (p != null) it.pos = { p }
        it.art = s.art
        it.trim = s.trim
        it.cjk = s.cjk
        it.redo = s.redo
        it.redoKeep = s.redoKeep
        it.timeKeep = s.timeKeep
        it.give = s.give
        it.entryHold = s.entryHold
        it.cut = s.cut
        it.pin = s.pin
        it.tiles = tiles
    }

    private fun post(l: Laid, run: (SaverScenes) -> Unit) {
        asked = l
        laid = null
        worker.execute {
            run(l.twin)
            if (asked === l) laid = l
        }
    }

    fun ahead(now: Long, due: Long) {
        if (s.visible(now) || now < due - LEAD_MS || now >= due) return
        val a = asked
        if (a != null && a.first && a.key == due && a.gen == s.gen) return
        val l = Laid(true, due, s.gen, s.scene, twin())
        post(l) {
            it.rng.seed(due.toInt())
            it.firstAt = due + it.rng.uniform(it.delayMin, it.delayMax)
            it.begin(it.draw(false), it.firstAt, true)
        }
    }

    internal fun ahead(e: SaverScenes, now: Long) {
        if (!e.shown || e.lanes.isEmpty() || now < e.sceneEnd() - LEAD_MS) return
        val at = e.nextAt()
        val a = asked
        if (a != null && !a.first && a.key == at && a.gen == e.gen && a.from == e.scene) return
        val t = twin()
        t.rng = e.rng.copy()
        t.scene = e.scene
        val other = !e.afterChange
        post(Laid(false, at, e.gen, e.scene, t)) { it.begin(it.draw(other), at, false) }
    }

    private fun install(e: SaverScenes, l: Laid, d: Long) {
        val t = l.twin
        e.rng = t.rng
        e.pin = t.pin
        e.take(t)
        e.scene = t.scene
        e.sceneAt = t.sceneAt + d
        e.gap = t.gap
        e.firstAt = if (l.first) t.firstAt + d else e.firstAt
        e.lanes = t.lanes
        e.crosses = t.crosses
        e.crossLeft = t.crossLeft
        e.packPasses = t.packPasses
        e.glyphSteps = t.glyphSteps
        e.redoSteps = t.redoSteps
        e.most = t.most
        for (lane in e.lanes) lane.shift(d)
        e.regrow()
        takes++
    }

    internal fun takeFirst(e: SaverScenes, now: Long, seed: Long): Boolean {
        val l = laid
        asked = null
        laid = null
        if (l == null || !l.first || l.key != seed || l.gen != e.gen) return false
        install(e, l, now - seed)
        return true
    }

    internal fun takeNext(e: SaverScenes, at: Long): Boolean {
        val l = laid
        asked = null
        laid = null
        if (l == null || l.first || l.key != at || l.gen != e.gen || l.from != e.scene) return false
        install(e, l, 0)
        return true
    }
}
