package net.seilent.funekit.screens.eq

import net.seilent.funekit.screens.host.pageModels
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.settings.RowPivot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqModelTest {
    private data class Line(override val key: String) : EqLine

    private fun pivot(name: String, vararg keys: String) = RowPivot<EqLine>(name, keys.map { Line(it) })

    private val outputs = listOf(pivot(HP, "hp:a", "hp:b", "hp:c"), pivot(BT, "bt:a", "bt:b", "bt:c", "bt:d"))

    private class Probe(val m: EqModel, val lefts: MutableList<String>)

    private fun probe(): Probe {
        val m = EqModel(1L, EqId(EQ))
        m.heightOf = { ROW }
        m.view = VIEW
        val lefts = mutableListOf<String>()
        m.left = { lefts += it }
        return Probe(m, lefts)
    }

    private fun landed(rows: List<RowPivot<EqLine>> = outputs): Probe = probe().also { it.m.pivots = rows }

    @Test
    fun aSwitchOpensTheNewPivotAtItsFirstRowAndTop() {
        val m = landed().m
        m.switchTo(1, 0L)
        m.down(3, 0L)
        m.pane.px = ROW
        assertEquals("premise: bluetooth is at row 3", 3, m.focusRow(1))
        m.switchTo(0, 0L)
        m.switchTo(1, 0L)
        assertEquals("the pivot opens at row 0", 0, m.focusRow(1))
        assertEquals("and at its top", 0, m.pane(1).px)
    }

    @Test
    fun aSwitchTellsThePaneWhichPivotLeftAtOnce() {
        val p = landed()
        p.m.switchTo(1, 0L)
        assertEquals(listOf(HP), p.lefts)
    }

    @Test
    fun aLandingWithoutTheShownPivotOpensTheNextAtRowZeroAndOwesItsLeft() {
        val abc = listOf(pivot("a", "a:x", "a:y", "a:z"), pivot("b", "b:x", "b:y", "b:z"), pivot("c", "c:x", "c:y", "c:z"))
        val p = landed(abc)
        val m = p.m
        m.switchTo(2, 0L)
        m.down(2, 0L)
        m.switchTo(1, 0L)
        p.lefts.clear()
        m.pivots = listOf(abc[0], abc[2])
        assertEquals("premise: the landing shows c", "c", m.names[m.pivot])
        assertEquals("the pivot opens at row 0", 0, m.focusRow(m.pivot))
        assertTrue("the left waits for leave", p.lefts.isEmpty())
        m.leave()
        assertEquals(listOf("b"), p.lefts)
    }

    @Test
    fun aHeldFocusKeyMovesTheFocusOnceTheShownRowsHoldIt() {
        val m = landed(listOf(pivot(HP, "hp:a", "hp:b"))).m
        m.focusKey("hp:band:3")
        assertEquals("the focus stays until the key lands", 0, m.focusRow(0))
        m.pivots = listOf(pivot(HP, "hp:a", "hp:b", "hp:band:3"))
        assertEquals(2, m.focusRow(0))
    }

    @Test
    fun aMoveDropsAHeldFocusKey() {
        val m = landed().m
        m.focusKey("hp:x")
        m.switchTo(1, 0L)
        m.switchTo(0, 0L)
        m.pivots = listOf(pivot(HP, "hp:a", "hp:b", "hp:x"), outputs[1])
        assertEquals(0, m.focusRow(0))
    }

    @Test
    fun aHeldFocusKeyLandsInTheLandingThatDropsTheFocusedRow() {
        val m = landed().m
        m.down(1, 0L)
        m.focusKey("hp:d")
        m.pivots = listOf(pivot(HP, "hp:a", "hp:c", "hp:d"), outputs[1])
        assertEquals("the held key's row takes the focus", 2, m.focusRow(0))
    }

    @Test
    fun aPageListingAKeyTwiceFailsItsLanding() {
        val m = probe().m
        val failed = runCatching { m.pivots = listOf(pivot(HP, "k"), pivot(BT, "k")) }.exceptionOrNull()
        assertEquals("the landing fails", "an eq page lists each key once", (failed as? IllegalArgumentException)?.message)
    }

    @Test
    fun aPageNamingAPivotTwiceFailsItsLanding() {
        val m = probe().m
        val failed = runCatching { m.pivots = listOf(pivot(HP, "hp:a"), pivot(HP, "bt:a")) }.exceptionOrNull()
        assertEquals("the landing fails", "an eq page names each pivot once", (failed as? IllegalArgumentException)?.message)
    }

    @Test
    fun anEmptyLandingAndBackKeepsFocusAndScrollWithNoLeft() {
        val p = landed()
        val m = p.m
        m.switchTo(1, 0L)
        m.down(2, 0L)
        m.pane.px = ROW
        p.lefts.clear()
        m.pivots = emptyList()
        m.pivots = outputs
        m.leave()
        assertEquals("the focus stays", 2, m.focusRow(1))
        assertEquals("the scroll stays", ROW, m.pane(1).px)
        assertEquals("no left", emptyList<String>(), p.lefts)
    }

    @Test
    fun aHeldNameThatDoesNotLandMovesNothing() {
        val p = landed()
        val m = p.m
        m.down(2, 0L)
        m.openAt(CAR)
        m.pivots = listOf(outputs[0], pivot(BT, "bt:a", "bt:b"))
        m.leave()
        assertEquals("the focus stays", 2, m.focusRow(0))
        assertEquals("no left", emptyList<String>(), p.lefts)
    }

    @Test
    fun aRestoredEqModelOpensItsSavedPivotAtRowZero() {
        val m = landed().m
        m.switchTo(1, 0L)
        m.down(2, 0L)
        val saved = m.state()
        val p = probe()
        p.m.restoreState(saved)
        p.m.pivots = outputs
        p.m.leave()
        assertEquals(1, p.m.pivot)
        assertEquals("the restored pivot opens at row 0", 0, p.m.focusRow(1))
        assertEquals(0, p.m.pane(1).px)
        assertEquals("no left", emptyList<String>(), p.lefts)
        assertEquals("the saved spot is the pivot name", listOf(BT), saved)
    }

    @Test
    fun theModelAnimatesWhileThePaneMoves() {
        val m = landed().m
        assertFalse("premise: the base is idle", m.animating(0L))
        m.moving = { true }
        assertEquals("the model animates while the pane moves", true, m.animating(0L))
    }

    @Test
    fun pageModelsBuildsAnEqModelForAnEqId() {
        assertTrue("pageModels builds an eq model", pageModels { e, p -> PageModel(e, p) }.enter(1L, EqId(EQ)) is EqModel)
    }

    @Test
    fun aPendingHeldNameAcrossAnEmptyLandingMovesNothing() {
        val p = landed()
        val m = p.m
        m.down(2, 0L)
        m.openAt(CAR)
        m.pivots = emptyList()
        m.pivots = outputs
        m.leave()
        assertEquals("the focus stays", 2, m.focusRow(0))
        assertEquals("no left", emptyList<String>(), p.lefts)
    }

    @Test
    fun anOpenAtALandedPivotRecordsItAsShown() {
        val p = landed()
        val m = p.m
        m.openAt(BT)
        assertEquals("premise: bluetooth shows", 1, m.pivot)
        m.down(2, 0L)
        m.pivots = listOf(outputs[0], pivot(BT, "bt:a", "bt:b", "bt:c", "bt:d", "bt:e"))
        m.leave()
        assertEquals("the focus stays", 2, m.focusRow(1))
        assertEquals("no left", emptyList<String>(), p.lefts)
    }

    companion object {
        private const val EQ = "eq"
        private const val HP = "headphones"
        private const val BT = "bluetooth"
        private const val CAR = "car"
        private const val ROW = 10
        private const val VIEW = 25
    }
}
