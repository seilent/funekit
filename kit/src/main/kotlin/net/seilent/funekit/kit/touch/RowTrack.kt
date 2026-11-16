package net.seilent.funekit.kit.touch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxOfPx
import net.seilent.funekit.px
import net.seilent.funekit.px64
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

private const val HALF_PX = 0.5f

data class RowSpec(val pitch: Int, val view: Int, val above: Int = 0, val pitch64: Long = fxInt(pitch), val ppu64: Long = fxInt(1)) {
    val whole: Boolean get() = ppu64 == fxInt(1) && pitch64 == fxInt(pitch)

    companion object {
        fun of(pitchU: Float, s: Scale, view: Int, above: Int = 0): RowSpec {
            val p64 = fx(pitchU)
            val ppu64 = fx(s.ppu)
            return RowSpec(px64(p64, ppu64) + 1, view, above, p64, ppu64)
        }
    }
}

class RowTrack(pitch: Int, view: Int, q: Int = 0, above: Int = 0, pitch64: Long = fxInt(pitch), ppu64: Long = fxInt(1), whole: Boolean = ppu64 == fxInt(1) && pitch64 == fxInt(pitch)) : Track {
    var pitch = pitch
        private set
    var view = view
        private set
    var above by mutableIntStateOf(above)
        private set
    var pitch64 = pitch64
        private set
    var ppu64 = ppu64
        private set
    var whole = whole
        private set
    var count = 0
        private set
    var q by mutableIntStateOf(q)
        private set
    private var pending = 0f
    private var counted = false

    fun topOf(r: Int): Int = px64(r * pitch64, ppu64)

    fun rowAt(px: Int): Int {
        var r = (fxOfPx(px.toFloat(), ppu64) / pitch64).toInt()
        while (r > 0 && topOf(r) > px) r--
        while (topOf(r + 1) <= px) r++
        return r
    }

    fun tapRow(y: Float): Int = rowAt(floor(y).toInt() + q)

    val max: Int get() = max(0, topOf(count) - view)
    val first: Int get() = rowAt(q)
    val sub: Int get() = q - topOf(first)
    val drawFirst: Int get() = rowAt(max(0, q - above))
    val drawSub: Int get() = q - above - topOf(drawFirst)

    override val canMove: Boolean get() = max > 0

    override fun atEnd(forward: Boolean): Boolean = if (forward) q >= max else q <= 0

    override fun by(delta: Float): Float {
        if (delta > 0f && q >= max || delta < 0f && q <= 0) return 0f
        pending -= delta
        if (abs(pending) > HALF_PX) {
            val to = (q - Math.round(pending)).coerceIn(0, max)
            pending -= q - to
            q = to
        }
        if (abs(pending) <= HALF_PX) return delta
        val used = delta + pending
        pending = 0f
        return used
    }

    fun toRow(i: Int) {
        q = topOf(i).coerceIn(0, max)
    }

    fun at(px: Int) {
        q = px.coerceIn(0, max)
    }

    fun count(n: Int, anchor: Int) {
        val s = sub
        count = n
        if (!counted && n == 0) return
        counted = true
        q = (topOf(anchor) + s).coerceIn(0, max)
    }

    fun resize(pitch: Int, view: Int, above: Int = this.above) {
        if (whole) pitch64 = fxInt(pitch)
        this.pitch = pitch
        this.view = view
        this.above = above
        if (counted) q = q.coerceIn(0, max)
    }

    fun resize(spec: RowSpec) {
        pitch = spec.pitch
        view = spec.view
        above = spec.above
        pitch64 = spec.pitch64
        ppu64 = spec.ppu64
        whole = spec.whole
        if (counted) q = q.coerceIn(0, max)
    }

    fun rescale(lastPpu: Float, ppu: Float, pitch: Int) {
        val f = first
        val s = sub
        if (whole) pitch64 = fxInt(pitch) else ppu64 = fx(ppu)
        this.pitch = pitch
        q = topOf(f) + px(s / lastPpu, ppu)
    }

    fun rescale(lastPpu: Float, ppu: Float, spec: RowSpec) {
        val f = first
        val s = sub
        pitch = spec.pitch
        above = spec.above
        pitch64 = spec.pitch64
        ppu64 = spec.ppu64
        whole = spec.whole
        q = topOf(f) + px(s / lastPpu, ppu)
    }

    fun save(ppu: Float): List<Float> = listOf(first.toFloat(), sub / ppu)

    companion object {
        fun restore(saved: List<Float>, spec: RowSpec, ppu: Float): RowTrack =
            RowTrack(spec.pitch, spec.view, 0, spec.above, spec.pitch64, spec.ppu64, spec.whole).also { it.q = it.topOf(saved[0].toInt()) + px(saved[1], ppu) }
    }
}
