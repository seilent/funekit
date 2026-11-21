package net.seilent.funekit.screens.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.music.nowplaying.AdjustSource

class RangeAdjust internal constructor(
    row: SettingRow,
    private val rows: SettingsRows,
    override val hud: AdjustHud,
    internal val from: Int,
    private val end: (Boolean) -> Unit,
    private val scope: CoroutineScope,
) : AdjustSource {
    internal var row = row
        private set
    internal var level = row.level
        private set
    private val pending = mutableListOf<Int>()
    private var done = false

    override fun tap(h: AdjustHit) {
        val d = when (h) {
            AdjustHit.Exit -> {
                end(false)
                return
            }
            AdjustHit.Up0, AdjustHit.Down1 -> -row.step
            AdjustHit.Down0, AdjustHit.Up1 -> row.step
            AdjustHit.None -> return
        }
        val v = (level + d).coerceIn(row.min, row.max)
        if (v == level) return
        level = v
        pending += v
        show()
        scope.launch(FailFast, CoroutineStart.UNDISPATCHED) {
            try {
                rows.set(row.key, listOf(v.toString()))
            } finally {
                pending.remove(v)
            }
            if (pending.isEmpty()) rows.pivots.value.firstNotNullOfOrNull { p -> p.rows.firstOrNull { it.key == row.key } }?.let { adopt(it) }
        }
    }

    override fun back() {
        revert()
        end(false)
    }

    internal fun revert() {
        scope.launch(FailFast, CoroutineStart.UNDISPATCHED) { rows.set(row.key, listOf(from.toString())) }
    }

    override fun number(side: Int): Boolean {
        if (!rows.hasEntry(row)) return false
        end(true)
        rows.entry(row, side)
        return true
    }

    internal fun landed(r: SettingRow) {
        row = r
        if (pending.isEmpty()) level = r.level
        show()
    }

    internal fun finish() {
        done = true
    }

    private fun adopt(r: SettingRow) {
        if (done) return
        row = r
        level = r.level
        show()
    }

    private fun show() {
        if (done) return
        rows.adjust(row, level)?.let { hud.set(it.cols, it.line2) }
    }
}
