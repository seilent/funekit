package net.seilent.funekit.kit.saver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverLayTest {
    private val due = 20000L

    private fun laid(): Triple<SaverScenes, SaverLay, Queue> {
        val q = Queue()
        val e = a105().kit()
        return Triple(e, SaverLay(e, q), q)
    }

    @Test
    fun aFirstSceneLaidAheadEqualsTheOneLaidAtTheShow() {
        val (e, lay, q) = laid()
        lay.ahead(due - 1000, due)
        q.drain()
        e.show(due, due)
        val plain = a105().kit()
        plain.show(due, due)
        assertEquals(1, lay.takes)
        assertEquals(plain.firstAt, e.firstAt)
        assertEquals(plain.probeDump(), e.probeDump())
        e.step(e.nextAt())
        plain.step(plain.nextAt())
        assertEquals(plain.probeDump(), e.probeDump())
    }

    @Test
    fun aLateShowAtAnUnchangedDueTimeShiftsTheLaidTimesByItsDelay() {
        val (e, lay, q) = laid()
        lay.ahead(due - 1000, due)
        q.drain()
        e.show(due + 250, due)
        val plain = a105().kit()
        plain.show(due + 250, due)
        assertEquals(1, lay.takes)
        assertEquals(plain.firstAt, e.firstAt)
        assertEquals(plain.probeDump(), e.probeDump())
    }

    @Test
    fun aDueTimeThatChangesBeforeTheShowDropsTheLay() {
        val (e, lay, q) = laid()
        lay.ahead(due - 1000, due)
        q.drain()
        lay.ahead(due - 500, due + 500)
        q.drain()
        e.show(due, due)
        val plain = a105().kit()
        plain.show(due, due)
        assertEquals(0, lay.takes)
        assertEquals(plain.probeDump(), e.probeDump())
        val (f, moved, r) = laid()
        moved.ahead(due - 1000, due)
        r.drain()
        moved.ahead(due - 500, due + 500)
        r.drain()
        f.show(due + 500, due + 500)
        val later = a105().kit()
        later.show(due + 500, due + 500)
        assertEquals(1, moved.takes)
        assertEquals(later.probeDump(), f.probeDump())
    }

    @Test
    fun theNextSceneLaidAheadEqualsTheOneSteppedTo() {
        val (e, lay, q) = laid()
        val plain = a105().kit()
        e.show(0, 7)
        plain.show(0, 7)
        e.step(e.sceneEnd() - 1000)
        plain.step(plain.sceneEnd() - 1000)
        assertEquals(1, q.jobs.size)
        q.drain()
        e.step(e.nextAt())
        plain.step(plain.nextAt())
        assertEquals(1, lay.takes)
        assertEquals(plain.probeDump(), e.probeDump())
        repeat(3) {
            e.step(e.nextAt())
            plain.step(plain.nextAt())
            q.jobs.clear()
            assertEquals(plain.probeDump(), e.probeDump())
        }
    }

    @Test
    fun aDroppedLayConsumesNoDrawAndTheNextSceneShowsTheNewTrack() {
        val (e, lay, q) = laid()
        val plain = a105().kit()
        e.show(0, 7)
        plain.show(0, 7)
        e.step(e.sceneEnd() - 1000)
        q.drain()
        e.setTrack("Undone", "Weezer", "Blue", false)
        plain.setTrack("Undone", "Weezer", "Blue", false)
        e.step(e.nextAt())
        plain.step(plain.nextAt())
        assertEquals(0, lay.takes)
        assertEquals("Undone", e.title)
        assertTrue(e.lanes.any { it.t.s.contains("Undone") || it.t.s.contains("Blue") })
        assertEquals(plain.probeDump(), e.probeDump())
        repeat(3) {
            e.step(e.nextAt())
            plain.step(plain.nextAt())
            q.jobs.clear()
            assertEquals(plain.probeDump(), e.probeDump())
        }
    }

    @Test
    fun aHideDropsTheNextLay() {
        val (e, lay, q) = laid()
        e.show(0, 7)
        e.step(e.sceneEnd() - 1000)
        q.drain()
        e.hide(e.sceneEnd() - 900)
        assertFalse(lay.takeNext(e, e.nextAt()))
        val (f, kept, r) = laid()
        f.show(0, 7)
        f.step(f.sceneEnd() - 1000)
        r.drain()
        assertTrue(kept.takeNext(f, f.nextAt()))
    }
}
