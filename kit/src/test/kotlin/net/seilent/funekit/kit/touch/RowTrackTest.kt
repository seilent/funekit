package net.seilent.funekit.kit.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowTrackTest {
    private fun a105(q: Int = 0): RowTrack = RowTrack(165, 920).also {
        it.count(30, 0)
        if (q > 0) assertEquals(q.toFloat(), it.by(q.toFloat()), 0f)
    }

    @Test
    fun theA105DrawerScrollsOverItsRowsLessTheView() {
        val t = a105()
        assertEquals(4030, t.max)
        assertTrue(t.canMove)
        assertTrue(t.atEnd(false))
        assertFalse(t.atEnd(true))
    }

    @Test
    fun aPositionSplitsIntoTheFirstRowAndTheSubRowPx() {
        val t = a105(1000)
        assertEquals(1000, t.q)
        assertEquals(6, t.first)
        assertEquals(10, t.sub)
        assertEquals(3030f, t.by(3030f), 0f)
        assertEquals(4030, t.q)
        assertEquals(24, t.first)
        assertEquals(70, t.sub)
        assertTrue(t.atEnd(true))
    }

    @Test
    fun atPutsTheTrackOnAnyPxHeldToTheEnds() {
        val t = a105()
        t.at(1234)
        assertEquals(1234, t.q)
        assertEquals(7, t.first)
        assertEquals(79, t.sub)
        t.at(5000)
        assertEquals(4030, t.q)
        t.at(-5)
        assertEquals(0, t.q)
    }

    @Test
    fun aStepPastEitherEndStopsThereAndReturnsTheUsedPart() {
        val t = a105(4000)
        assertEquals(30f, t.by(100f), 0f)
        assertEquals(4030, t.q)
        assertEquals(0f, t.by(10f), 0f)
        assertEquals(4030, t.q)
        assertEquals(-4010f, t.by(-4010f), 0f)
        assertEquals(20, t.q)
        assertEquals(-20f, t.by(-50f), 0f)
        assertEquals(0, t.q)
        assertEquals(0f, t.by(-10f), 0f)
        assertEquals(0, t.q)
    }

    @Test
    fun rowsThatFitTheViewDoNotMove() {
        val five = RowTrack(165, 920).also { it.count(5, 0) }
        assertEquals(0, five.max)
        assertFalse(five.canMove)
        assertEquals(0f, five.by(100f), 0f)
        val none = RowTrack(165, 920).also { it.count(0, 0) }
        assertEquals(0, none.max)
        assertFalse(none.canMove)
    }

    @Test
    fun toRowPutsTheRowAtTheTopHeldToTheEnd() {
        val t = a105()
        t.toRow(6)
        assertEquals(990, t.q)
        t.toRow(30)
        assertEquals(4030, t.q)
    }

    @Test
    fun aCountKeepsTheSubRowPxAtTheAnchorRowHeldToTheNewEnd() {
        val t = a105(1000)
        t.count(31, 7)
        assertEquals(1165, t.q)
        val u = a105(1000)
        u.count(10, 6)
        assertEquals(730, u.q)
        assertEquals(4, u.first)
        assertEquals(70, u.sub)
    }

    @Test
    fun aViewChangeHoldsThePositionToTheNewEnd() {
        val t = a105(4030)
        t.resize(165, 812)
        assertEquals(4030, t.q)
        assertEquals(4138, t.max)
        assertEquals(108f, t.by(108f), 0f)
        assertEquals(4138, t.q)
        t.resize(165, 920)
        assertEquals(4030, t.q)
    }

    @Test
    fun thePositionIsSavedAsTheFirstRowAndTheSubRowInUnits() {
        val t = a105(1000)
        assertEquals(listOf(6f, 10f / 3f), t.save(3f))
        val r = RowTrack.restore(listOf(6f, 10f / 3f), RowSpec(165, 920), 3f)
        assertEquals(1000, r.q)
    }

    @Test
    fun aRestoreKeepsItsPositionThroughAnEmptyFirstCount() {
        val r = RowTrack.restore(listOf(6f, 10f / 3f), RowSpec(165, 920), 3f)
        r.resize(165, 920)
        assertEquals(1000, r.q)
        r.count(0, 6)
        assertEquals(1000, r.q)
        r.count(30, 6)
        assertEquals(1000, r.q)
        assertEquals(4030, r.max)
    }

    @Test
    fun aRescaleKeepsTheFirstRowAndTheSubRowInUnits() {
        val t = a105(1000)
        t.rescale(3f, 2f, 110)
        assertEquals(667, t.q)
        assertEquals(110, t.pitch)
        t.resize(110, 560)
        assertEquals(667, t.q)
        assertEquals(2740, t.max)
    }

    @Test
    fun theP2160PitchSplitsAPositionAtItsOwnRows() {
        val t = RowTrack(248, 1620).also { it.count(30, 0) }
        assertEquals(5820, t.max)
        assertEquals(1000f, t.by(1000f), 0f)
        assertEquals(4, t.first)
        assertEquals(8, t.sub)
    }

    @Test
    fun aSubPxStepWaitsUntilItPassesHalfAPx() {
        val t = a105()
        assertEquals(0.4f, t.by(0.4f), 0f)
        assertEquals(0, t.q)
        assertEquals(0.4f, t.by(0.4f), 0f)
        assertEquals(1, t.q)
        val h = a105()
        assertEquals(247.5f, h.by(247.5f), 0f)
        assertEquals(247, h.q)
        assertEquals(0.5f, h.by(0.5f), 0f)
        assertEquals(248, h.q)
    }

    @Test
    fun theResidualOutlivesARowRequestAndDropsAtAnEnd() {
        val t = a105(1000)
        assertEquals(0.4f, t.by(0.4f), 0f)
        t.toRow(6)
        assertEquals(990, t.q)
        assertEquals(0.2f, t.by(0.2f), 0f)
        assertEquals(991, t.q)
        val e = a105(4029)
        assertEquals(1f, e.by(1.7f), 1e-6f)
        assertEquals(4030, e.q)
        assertEquals(-0.6f, e.by(-0.6f), 0f)
        assertEquals(4029, e.q)
    }
}
