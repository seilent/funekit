package net.seilent.funekit.kit

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.structuralEqualityPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class LookTest {
    private val frame = mutableLongStateOf(0L)
    private val input = mutableIntStateOf(0)

    private class Watch(val read: () -> Unit) {
        var calls = 0
        var dirty = false
    }

    private fun watch(watches: List<Watch>, steps: List<() -> Unit>) {
        val observer = SnapshotStateObserver { it() }
        fun observe(w: Watch) = observer.observeReads(w, { it.dirty = true; it.calls++ }) { w.read() }
        observer.start()
        try {
            Snapshot.sendApplyNotifications()
            watches.forEach { observe(it) }
            for (step in steps) {
                step()
                Snapshot.sendApplyNotifications()
                for (w in watches) if (w.dirty) {
                    w.dirty = false
                    observe(w)
                }
            }
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    private fun frames(n: Int): List<() -> Unit> = List(n) { { frame.longValue += 1 } }

    private val change: () -> Unit = {
        input.intValue += 1
        frame.longValue += 1
    }

    private fun constant(): Int = if (frame.longValue >= 0) input.intValue else -1

    @Test
    fun aDerivedLookWithAnUnchangedValueInvalidatesNothingWhileTheFrameStateDoes() {
        val plain = derivedStateOf { constant() }
        val structural = derivedStateOf(structuralEqualityPolicy()) { constant() }
        val direct = Watch { frame.longValue }
        val look = Watch { plain.value }
        val policy = Watch { structural.value }
        val all = listOf(direct, look, policy)
        watch(all, frames(10))
        assertEquals(10, direct.calls)
        assertEquals(0, look.calls)
        assertEquals(0, policy.calls)
        watch(all, listOf(change))
        assertEquals(1, look.calls)
        assertEquals(1, policy.calls)
        watch(all, frames(10) + listOf(change))
        assertEquals(2, look.calls)
        assertEquals(2, policy.calls)
    }

    @Test
    fun aCoveredLookKeepsItsValueAndInvalidatesNothingUntilUncovered() {
        val covered = mutableStateOf(true)
        val start = input.intValue
        val frozen: State<Int> = lookOf(covered) { constant() }
        val look = Watch { frozen.value }
        watch(listOf(look), frames(10) + List(10) { change })
        assertEquals(0, look.calls)
        assertEquals(start, frozen.value)
        watch(listOf(look), listOf({ covered.value = false }))
        assertEquals(1, look.calls)
        assertEquals(input.intValue, frozen.value)
        watch(listOf(look), frames(10))
        assertEquals(1, look.calls)
        watch(listOf(look), listOf(change))
        assertEquals(2, look.calls)
    }

    @Test
    fun aLookFirstReadWhileCoveredComputesTheCurrentValueThenFreezes() {
        input.intValue = 7
        val covered = mutableStateOf(true)
        Snapshot.sendApplyNotifications()
        val frozen = lookOf(covered) { constant() }
        assertEquals(7, frozen.value)
        input.intValue = 8
        frame.longValue += 1
        Snapshot.sendApplyNotifications()
        assertEquals(7, frozen.value)
        covered.value = false
        Snapshot.sendApplyNotifications()
        assertEquals(8, frozen.value)
    }
}
