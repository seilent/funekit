package net.seilent.funekit.fake

import kotlin.random.Random

internal class FakeQueue(private val random: Random) {
    private val entries = mutableListOf<Long>()
    private val order = mutableListOf<Int>()

    var slot = -1
        private set
    var shuffle = false
        private set
    var revision = 0L
        private set

    val size: Int get() = entries.size

    val current: Long? get() = order.getOrNull(slot)?.let { entries[it] }

    fun ids(): List<Long> = order.map { entries[it] }

    fun set(ids: List<Long>, start: Int, shuffled: Boolean = false) {
        if (shuffled) shuffle = true
        entries.clear()
        entries += ids
        identity()
        when {
            ids.isEmpty() -> slot = -1
            start in ids.indices -> {
                slot = start
                if (shuffle) front()
            }
            else -> {
                slot = 0
                if (shuffle) order.shuffle(random)
            }
        }
        revision++
    }

    fun select(at: Int) {
        if (at in order.indices) slot = at
    }

    fun shuffle(on: Boolean) {
        if (on == shuffle) return
        shuffle = on
        if (entries.isNotEmpty()) {
            if (on) {
                front()
            } else {
                val row = order[slot]
                identity()
                slot = row
            }
        }
        revision++
    }

    fun enqueue(ids: List<Long>) {
        if (ids.isEmpty()) return
        val empty = entries.isEmpty()
        val first = entries.size
        entries += ids
        when {
            !shuffle -> identity()
            empty -> {
                identity()
                order.shuffle(random)
            }
            else -> for (row in first until entries.size) order.add(slot + 1 + random.nextInt(order.size - slot), row)
        }
        if (empty) slot = 0
        revision++
    }

    fun playNext(ids: List<Long>) {
        if (entries.isEmpty()) return enqueue(ids)
        if (ids.isEmpty()) return
        val at = order[slot] + 1
        entries.addAll(at, ids)
        for (i in order.indices) if (order[i] >= at) order[i] += ids.size
        order.addAll(slot + 1, (at until at + ids.size).toList())
        revision++
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in order.indices || to !in order.indices || from == to) return false
        if (shuffle) order.add(to, order.removeAt(from)) else entries.add(to, entries.removeAt(from))
        slot = when {
            from == slot -> to
            from < slot && to >= slot -> slot - 1
            from > slot && to <= slot -> slot + 1
            else -> slot
        }
        revision++
        return true
    }

    fun remove(at: Int): Boolean {
        if (at !in order.indices) return false
        val row = order.removeAt(at)
        entries.removeAt(row)
        for (i in order.indices) if (order[i] > row) order[i]--
        if (at < slot) slot--
        slot = slot.coerceAtMost(order.lastIndex)
        revision++
        return true
    }

    fun drop(ids: Set<Long>): Boolean {
        var changed = false
        for (i in order.indices.reversed()) if (entries[order[i]] in ids) changed = remove(i) || changed
        return changed
    }

    fun clear() {
        entries.clear()
        order.clear()
        slot = -1
        shuffle = false
        revision++
    }

    private fun identity() {
        order.clear()
        order += entries.indices
    }

    private fun front() {
        val row = order[slot]
        identity()
        order.removeAt(row)
        val rest = order.shuffled(random)
        order.clear()
        order += row
        order += rest
        slot = 0
    }
}
