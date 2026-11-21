package net.seilent.funekit.screens.music.nowplaying

interface OffsetEdit {
    val shownMs: Int
    val editing: Boolean

    fun begin()

    fun step(d: Int)

    fun coarse(d: Int)

    fun keep()

    fun restore()

    fun entry(adjust: Boolean)

    fun end()
}

private const val MAX_MS = 60000

class OffsetSheet(val edit: OffsetEdit) {
    val hud = AdjustHud()

    val items: List<String> get() = listOf(ITEM)

    fun value(): String = text(edit.shownMs)

    fun mark(): Int = if (edit.editing) 0 else -1

    private fun cols(): List<AdjustCol> {
        val v = edit.shownMs
        return listOf(AdjustCol(v.toString(), LABEL, up = (v < MAX_MS), down = (v > -MAX_MS)))
    }

    private fun refresh() {
        if (hud.shown) hud.set(cols(), value())
    }

    fun press(i: Int) {
        if (i != 0 || edit.editing) return
        edit.begin()
    }

    fun adjust(now: Long): Boolean {
        if (!edit.editing || hud.shown) return false
        hud.open(now, cols(), TITLE, value())
        return true
    }

    fun tap(h: AdjustHit) {
        when (h) {
            AdjustHit.Exit -> {
                edit.keep()
                hud.drop()
            }
            AdjustHit.Up0 -> edit.step(1)
            AdjustHit.Down0 -> edit.step(-1)
            else -> return
        }
        refresh()
    }

    fun hold(i: Int): Boolean {
        if (i != 0) return false
        if (edit.editing) edit.restore()
        edit.entry(false)
        return true
    }

    fun holdNumber(): Boolean {
        if (!hud.shown) return false
        edit.keep()
        hud.drop()
        edit.entry(true)
        return true
    }

    fun scrim(): Boolean {
        if (!edit.editing) return false
        edit.restore()
        return true
    }

    fun back(): Boolean {
        if (hud.shown) {
            edit.restore()
            hud.drop()
            return true
        }
        if (!edit.editing) return false
        edit.restore()
        return true
    }

    fun key(d: Int): Boolean {
        if (!hud.shown) return false
        edit.coarse(d)
        refresh()
        return true
    }

    fun dismiss() {
        hud.drop()
        if (edit.editing) edit.restore()
        edit.end()
    }

    companion object {
        const val TITLE = "lyrics"
        const val ITEM = "offset"
        const val LABEL = "offset ms"

        fun text(ms: Int): String = (if (ms > 0) "+" else "") + ms + " MS"
    }
}
