package net.seilent.funekit.kit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.kit.motion.Press
import kotlin.math.ceil

class LitRow {
    var revision by mutableIntStateOf(0)
        private set
    private var row = -1
    private var end = 0L
    private var until = -1L

    fun down(r: Int, fadeEnd: Long) {
        revision++
        row = r
        end = fadeEnd
        until = -1L
    }

    fun up() {
        revision++
        if (row >= 0 && until < 0) until = end
    }

    fun drop() {
        revision++
        row = -1
        until = -1L
    }

    fun on(r: Int, now: Long): Boolean = r >= 0 && r == row && (until < 0 || now < until)
}

class PressSlot(val item: Int = -1, val p: Press = Press())

class PressSlots {
    var revision by mutableIntStateOf(0)
        private set
    var on = PressSlot()
        private set
    var back = PressSlot()
        private set
    private var key = false

    private fun rest(s: PressSlot, now: Long): Boolean = s.item < 0 || (!s.p.moving(now.toFloat()) && s.p.scale(now.toFloat()) == 1f)

    fun down(item: Int, now: Long) {
        revision++
        if (item != on.item) {
            var again = PressSlot(item)
            if (item == back.item) {
                again = PressSlot(item, back.p)
                back = PressSlot()
            }
            letGo(now)
            on = again
        }
        on.p.down(now.toFloat())
    }

    fun letGo(now: Long) {
        revision++
        if (rest(on, now)) {
            on = PressSlot()
            return
        }
        on.p.cancel(now.toFloat())
        back = on
        on = PressSlot()
    }

    fun focus(item: Int, pressable: Boolean, now: Long) {
        revision++
        if (key && on.item != item) {
            if (pressable) down(item, now) else letGo(now)
            return
        }
        if (on.item < 0 || on.item == item || !on.p.held) return
        if (pressable) down(item, now) else letGo(now)
    }

    fun keyDown(item: Int, pressable: Boolean, now: Long) {
        revision++
        key = true
        if (pressable) down(item, now)
    }

    fun keyUp(now: Long) {
        revision++
        key = false
        on.p.keyUp(now.toFloat())
    }

    fun tap(now: Long, acts: Boolean = true) {
        revision++
        on.p.tap(now.toFloat(), acts)
    }

    fun untap(now: Long) {
        revision++
        on.p.untap(now.toFloat())
    }

    fun hold(now: Long) {
        revision++
        on.p.hold(now.toFloat())
        back.p.hold(now.toFloat())
    }

    fun cutRing(now: Long) {
        revision++
        on.p.cutRing(now.toFloat())
        back.p.cutRing(now.toFloat())
    }

    val held: Boolean get() = on.item >= 0 && on.p.held

    fun cancel(now: Long) {
        revision++
        key = false
        on.p.cancel(now.toFloat())
    }

    fun drop() {
        revision++
        on.p.drop()
        back.p.drop()
    }

    fun reset() {
        revision++
        on = PressSlot()
        back = PressSlot()
        key = false
    }

    private fun of(item: Int): Press? = when {
        item < 0 -> null
        item == on.item -> on.p
        item == back.item -> back.p
        else -> null
    }

    fun look(item: Int, now: Long): Float = of(item)?.look(now.toFloat()) ?: 0f

    fun lit(item: Int, now: Long): Float = of(item)?.lit(now.toFloat()) ?: 0f

    fun ring(item: Int, now: Long): Float = of(item)?.ring(now.toFloat()) ?: 0f

    fun scale(item: Int, now: Long): Float = of(item)?.scale(now.toFloat()) ?: 1f

    fun stale(item: Int, now: Long): Boolean =
        item >= 0 && ((item == on.item && on.p.stale(now.toFloat())) || (item == back.item && back.p.stale(now.toFloat())))

    fun framed(item: Int, now: Long): Boolean =
        item >= 0 && ((item == on.item && on.p.framed(now.toFloat())) || (item == back.item && back.p.framed(now.toFloat())))

    fun stale(now: Long): Boolean = (on.item >= 0 && on.p.stale(now.toFloat())) || (back.item >= 0 && back.p.stale(now.toFloat()))

    fun due(now: Long): Long? {
        val a = if (on.item >= 0) on.p.due(now.toFloat()) else null
        val b = if (back.item >= 0) back.p.due(now.toFloat()) else null
        return listOfNotNull(a, b).minOrNull()?.let { ceil(it).toLong() }
    }
}
