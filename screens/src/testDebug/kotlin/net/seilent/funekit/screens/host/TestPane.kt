package net.seilent.funekit.screens.host

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.screens.eq.EqActs
import net.seilent.funekit.screens.eq.EqInk
import net.seilent.funekit.screens.eq.EqLine
import net.seilent.funekit.screens.eq.EqLook
import net.seilent.funekit.screens.eq.EqMeasure
import net.seilent.funekit.screens.eq.EqPane
import net.seilent.funekit.screens.eq.RowEnter
import net.seilent.funekit.screens.music.nowplaying.AdjustCol
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.settings.RowAdjust
import net.seilent.funekit.screens.settings.RowPivot

const val RAMP_MS = 200L

data class TestLine(override val key: String) : EqLine

class TestInk(override val height: Int) : EqInk

class TestPane(start: List<RowPivot<EqLine>>) : EqPane {
    override val pivots = mutableStateOf(start)
    val taps = mutableListOf<Pair<String, String>>()
    val holds = mutableListOf<Pair<String, String>>()
    val lefts = mutableListOf<String>()
    val drawn = mutableListOf<String>()
    val looks = HashMap<String, EqLook>()
    val glows = HashMap<String, RowGlow?>()
    var opens = 0
    var rests = 0
    var held: Set<String> = emptySet()
    var onTap: (EqActs) -> Unit = {}
    private val ramps = HashMap<String, Long>()

    fun ramp(key: String, at: Long) {
        ramps[key] = at
    }

    override fun ink(line: EqLine, m: EqMeasure): EqInk = TestInk(m.rows.pitch)

    override fun DrawScope.line(line: EqLine, ink: EqInk, look: EqLook, glow: RowGlow?) {
        drawn += line.key
        looks[line.key] = look
        glows[line.key] = glow
    }

    override fun enter(pivot: String, line: EqLine, now: Long): RowEnter {
        if (line.key in held) return RowEnter(0f, 0)
        val at = ramps[line.key] ?: return RowEnter.Rest
        return RowEnter(((now - at).toFloat() / RAMP_MS).coerceIn(0f, 1f), 0)
    }

    override fun moving(now: Long): Boolean = ramps.values.any { now - it < RAMP_MS }

    override fun rest(now: Long) {
        rests++
        for (k in held) ramps[k] = now
        held = emptySet()
    }

    override fun opened() {
        opens++
    }

    override fun tap(pivot: String, line: EqLine, acts: EqActs) {
        taps += pivot to line.key
        onTap(acts)
    }

    override fun hold(pivot: String, line: EqLine, acts: EqActs): Boolean {
        holds += pivot to line.key
        return true
    }

    override fun left(pivot: String) {
        lefts += pivot
    }
}

class TestAdjust(override val key: String, override val hud: AdjustHud, private val end: (Boolean) -> Unit) : RowAdjust {
    val calls = mutableListOf<String>()

    override fun open(now: Long): Boolean {
        calls += "open"
        hud.open(now, listOf(AdjustCol("0", key)), key, "0")
        return true
    }

    override fun tap(h: AdjustHit) {
        calls += "tap $h"
        if (h == AdjustHit.Exit) end(false)
    }

    override fun back() {
        calls += "back"
        revert()
        end(false)
    }

    override fun number(side: Int): Boolean {
        calls += "number"
        end(true)
        return true
    }

    override fun revert() {
        calls += "revert"
    }

    override fun finish() {
        calls += "finish"
    }
}
