package net.seilent.funekit.screens.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.screens.music.nowplaying.AdjustCol
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.settings.FieldPress
import net.seilent.funekit.screens.settings.FieldSheet
import net.seilent.funekit.screens.settings.SettingSheet

const val BAND = "band 1"
const val TYPE = "type"
const val CLOCK = "date/time"
val BAND_ITEMS = listOf(TYPE, "frequency", "gain", "q")
val TYPES = listOf("off", "peak", "low shelf", "high shelf")
val CLOCK_ITEMS = listOf("year", "month", "day", "hour", "minute")
private val BAND_VALUES = listOf("peak", "1000", "0", "71")
private val CLOCK_VALUES = listOf("2026", "11", "22", "10", "30")

open class TestFields(override val title: String, override val items: List<String>, start: List<String>, private val keeps: Boolean) : FieldSheet {
    val calls = mutableListOf<String>()
    override val hud = AdjustHud()
    var shown by mutableStateOf(start)
    var live by mutableIntStateOf(-1)
    var gone by mutableStateOf(false)
    var untyped: Set<Int> = emptySet()
    var refuses = false

    override fun values(): List<String> = shown

    override fun field(): Int = live

    override fun press(i: Int): FieldPress {
        calls += "press $i"
        live = i
        return FieldPress.Field
    }

    override fun pickLive(i: Int) {
        calls += "pickLive $i"
        end("keep")
    }

    override fun adjust(now: Long): Boolean {
        if (live < 0 || hud.shown) return false
        hud.open(now, listOf(AdjustCol(shown[live], items[live])), title, shown[live])
        return true
    }

    override fun tap(h: AdjustHit) {
        when (h) {
            AdjustHit.Exit -> end("keep")
            AdjustHit.Up0 -> calls += "up $live"
            AdjustHit.Down0 -> calls += "down $live"
            else -> Unit
        }
    }

    override fun back(): Boolean {
        calls += "back"
        if (live < 0) return false
        end("drop")
        return true
    }

    override fun typed(i: Int): Boolean = i in items.indices && i !in untyped

    override fun hold(i: Int): Boolean {
        if (refuses) return false
        if (live >= 0) end("drop")
        calls += "entry $i false"
        return true
    }

    override fun holdNumber(): Boolean {
        if (!hud.shown || refuses) return false
        val i = live
        end(if (keeps) "keep" else "drop")
        calls += "entry $i true"
        return true
    }

    override fun scrim(): Boolean {
        if (live < 0) return false
        end("drop")
        return true
    }

    override fun gone(): Boolean = gone

    override fun dismiss() {
        calls += "dismiss"
        hud.dismiss()
        live = -1
    }

    private fun end(how: String) {
        calls += "$how $live"
        live = -1
        hud.dismiss()
    }
}

class BandFields(private val form: MenuForm = MenuForm.Sheet) : TestFields(BAND, BAND_ITEMS, BAND_VALUES, true) {
    override fun press(i: Int): FieldPress {
        if (i != 0) return super.press(i)
        calls += "press 0"
        live = 0
        val at = TYPES.indexOf(shown[0])
        return FieldPress.Choice(SettingSheet(form, TYPE, TYPES, focus = at, mark = at))
    }

    override fun choose(i: Int) {
        calls += "choose $i"
        shown = listOf(TYPES[i]) + shown.drop(1)
        live = -1
    }

    override fun typed(i: Int): Boolean = i != 0 && super.typed(i)
}

class ClockFields : TestFields(CLOCK, CLOCK_ITEMS, CLOCK_VALUES, false)
