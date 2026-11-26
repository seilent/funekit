package net.seilent.funekit.screens.eq

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Scale
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.SettingMeasure
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.screens.music.PageId
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.settings.FieldSheet
import net.seilent.funekit.screens.settings.RowAdjust
import net.seilent.funekit.screens.settings.RowPivot
import net.seilent.funekit.screens.settings.SettingLook
import net.seilent.funekit.screens.settings.SettingSheet

data class EqId(val header: String) : PageId

class EqScreen(val entry: Long, val id: EqId, val pivot: String? = null)

interface EqLine {
    val key: String
}

interface EqInk {
    val height: Int
}

data class RowEnter(val alpha: Float, val shift: Int) {
    companion object {
        val Rest = RowEnter(1f, 0)
    }
}

data class EqLook(val look: SettingLook, val enter: RowEnter)

class EqMeasure(
    val s: Scale,
    val text: TextMeasurer,
    val fonts: FontFamily.Resolver,
    val density: Density,
    val covers: Covers?,
    val rows: SettingMeasure,
    val accent: Color,
    val accentOff: Boolean,
)

interface EqPane {
    val pivots: State<List<RowPivot<EqLine>>>

    fun ink(line: EqLine, m: EqMeasure): EqInk

    fun DrawScope.line(line: EqLine, ink: EqInk, look: EqLook, glow: RowGlow?, rec: DrawRecord?, name: String)

    fun enter(pivot: String, line: EqLine, now: Long): RowEnter = RowEnter.Rest

    fun moving(now: Long): Boolean = false

    fun rest(now: Long) {}

    fun opened() {}

    fun tap(pivot: String, line: EqLine, acts: EqActs)

    fun hold(pivot: String, line: EqLine, acts: EqActs): Boolean = false

    fun left(pivot: String) {}
}

interface EqActs {
    fun now(): Long

    fun sheet(sheet: SettingSheet, pick: (Int) -> Boolean, closed: () -> Unit = {}, keep: Boolean = false): Boolean

    fun fields(sheet: FieldSheet): Boolean

    fun adjust(make: (AdjustHud, (Boolean) -> Unit) -> RowAdjust): Boolean

    fun focus(key: String)

    fun toast(line1: String, line2: String)

    fun close()

    fun handOff()

    fun launch(block: suspend () -> Unit)
}

class EqPage(val id: EqId, val pane: EqPane)
