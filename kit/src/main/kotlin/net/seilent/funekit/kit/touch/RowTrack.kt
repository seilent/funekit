package net.seilent.funekit.kit.touch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.px
import kotlin.math.abs
import kotlin.math.max

private const val HALF_PX = 0.5f

data class RowSpec(val pitch: Int, val view: Int, val above: Int = 0)

class RowTrack(pitch: Int, view: Int, q: Int = 0, above: Int = 0) : Track {
    var pitch = pitch
        private set
    var view = view
        private set
    var above by mutableIntStateOf(above)
        private set
    var count = 0
        private set
    var q by mutableIntStateOf(q)
        private set
    private var pending = 0f
    private var counted = false

    val max: Int get() = max(0, count * pitch - view)
    val first: Int get() = q / pitch
    val sub: Int get() = q - first * pitch
    val drawFirst: Int get() = max(0, q - above) / pitch
    val drawSub: Int get() = q - above - drawFirst * pitch

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
        q = (i * pitch).coerceIn(0, max)
    }

    fun at(px: Int) {
        q = px.coerceIn(0, max)
    }

    fun count(n: Int, anchor: Int) {
        val s = sub
        count = n
        if (!counted && n == 0) return
        counted = true
        q = (anchor * pitch + s).coerceIn(0, max)
    }

    fun resize(pitch: Int, view: Int, above: Int = this.above) {
        this.pitch = pitch
        this.view = view
        this.above = above
        if (counted) q = q.coerceIn(0, max)
    }

    fun rescale(lastPpu: Float, ppu: Float, pitch: Int) {
        q = first * pitch + px(sub / lastPpu, ppu)
        this.pitch = pitch
    }

    fun save(ppu: Float): List<Float> = listOf(first.toFloat(), sub / ppu)

    companion object {
        fun restore(saved: List<Float>, spec: RowSpec, ppu: Float): RowTrack =
            RowTrack(spec.pitch, spec.view, saved[0].toInt() * spec.pitch + px(saved[1], ppu), spec.above)
    }
}
