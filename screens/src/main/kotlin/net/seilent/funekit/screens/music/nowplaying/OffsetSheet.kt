package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.screens.settings.FieldPress
import net.seilent.funekit.screens.settings.FieldSheet

interface OffsetEdit {
    val shownMs: Int
    val editing: Boolean
    val hasEntry: Boolean get() = true

    fun begin()

    fun step(d: Int)

    fun coarse(d: Int)

    fun keep()

    fun restore()

    fun entry(adjust: Boolean)

    fun end()
}

class OffsetSheet(val edit: OffsetEdit) : FieldSheet {
    override val hud = AdjustHud()

    override val title: String get() = TITLE

    override val items: List<String> get() = listOf(ITEM)

    fun value(): String = text(edit.shownMs)

    override fun values(): List<String> = listOf(value())

    override fun mixed(): List<Boolean> = listOf(false)

    override fun field(): Int = if (edit.editing) 0 else -1

    private fun cols(): List<AdjustCol> {
        val v = edit.shownMs
        return listOf(AdjustCol(v.toString(), LABEL, up = (v < MAX_MS), down = (v > -MAX_MS)))
    }

    private fun refresh() {
        if (hud.shown) hud.set(cols(), value())
    }

    override fun press(i: Int): FieldPress {
        if (i == 0 && !edit.editing) edit.begin()
        return if (edit.editing) FieldPress.Field else FieldPress.None
    }

    override fun pickLive(i: Int) {}

    override fun typed(i: Int): Boolean = i == 0 && edit.hasEntry

    override fun adjust(now: Long): Boolean {
        if (!edit.editing || hud.shown) return false
        hud.open(now, cols(), TITLE, value())
        return true
    }

    override fun tap(h: AdjustHit) {
        when (h) {
            AdjustHit.Exit -> {
                edit.keep()
                hud.dismiss()
            }
            AdjustHit.Up0 -> edit.step(1)
            AdjustHit.Down0 -> edit.step(-1)
            else -> return
        }
        refresh()
    }

    override fun hold(i: Int): Boolean {
        if (i != 0) return false
        if (edit.editing) edit.restore()
        edit.entry(false)
        return true
    }

    override fun holdNumber(): Boolean {
        if (!hud.shown) return false
        edit.keep()
        hud.dismiss()
        edit.entry(true)
        return true
    }

    override fun scrim(): Boolean {
        if (!edit.editing) return false
        edit.restore()
        return true
    }

    override fun back(): Boolean {
        if (hud.shown) {
            edit.restore()
            hud.dismiss()
            return true
        }
        if (!edit.editing) return false
        edit.restore()
        return true
    }

    override fun key(d: Int): Boolean {
        if (!hud.shown) return false
        edit.coarse(d)
        refresh()
        return true
    }

    override fun dismiss() {
        hud.dismiss()
        if (edit.editing) edit.restore()
        edit.end()
    }

    companion object {
        const val MAX_MS = 60000
        const val TITLE = "lyrics"
        const val ITEM = "offset"
        const val LABEL = "offset ms"

        fun text(ms: Int): String = (if (ms > 0) "+" else "") + ms + " MS"
    }
}
