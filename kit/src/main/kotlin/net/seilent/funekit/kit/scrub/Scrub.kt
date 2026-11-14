package net.seilent.funekit.kit.scrub

private const val HALF = 2f

data class ScrubZone(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

class Scrub<T : Any>(private val buckets: List<T>) {
    private var index = -1

    var active = false
        private set

    val bucket: T? get() = buckets.getOrNull(index)

    fun down(x: Float, y: Float, zone: ScrubZone): Boolean {
        index = -1
        active = buckets.isNotEmpty() && zone.contains(x, y)
        return active
    }

    fun move(y: Float, height: Float): T? {
        if (!active) return null
        val i = at(buckets.size, y, height)
        if (i == index) return null
        index = i
        return buckets[i]
    }

    fun up() {
        active = false
        index = -1
    }

    companion object {
        fun at(count: Int, y: Float, height: Float): Int {
            if (count <= 1 || height <= 0f) return 0
            return (y * count / height).toInt().coerceIn(0, count - 1)
        }

        fun zone(width: Float, height: Float, edge: Float, cap: Float): ScrubZone {
            val h = minOf(height, cap)
            val top = (height - h) / HALF
            return ScrubZone(width - edge, top, width, top + h)
        }
    }
}
