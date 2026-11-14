package net.seilent.funekit.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpTest {
    private fun grid(bottom: Float, avail: (Int) -> Boolean = { true }, section: Int = 0) =
        JumpGrid().also { it.open(BooleanArray(38) { i -> avail(i) }, section, bottom) }

    @Test
    fun slotsRunExitHashAToZSearchThenKanaInReadingOrder() {
        assertEquals(0, Jump.slotOf(Jump.EXIT))
        assertEquals(1, Jump.slotOf(0))
        assertEquals(27, Jump.slotOf(26))
        assertEquals(28, Jump.slotOf(Jump.SEARCH))
        assertEquals(29, Jump.slotOf(27))
        assertEquals(39, Jump.slotOf(37))
        for (slot in 0 until 40) assertEquals(slot, Jump.slotOf(Jump.itemOf(slot)))
        assertEquals(10, Jump.rows)
    }

    @Test
    fun tilesAndRowsSitWhereTheDesignPutsThem() {
        assertEquals(51.79f, Jump.tile, 0.001f)
        assertEquals(listOf(10.01f, 66.04f, 122.07f, 178.10f), (0 until 4).map { Jump.cellX(it) }.map { Math.round(it * 100) / 100f })
        assertEquals(19.54f + 56.03f, Jump.rowTop(4), 0.001f)
        assertEquals(591.6f, Jump.content(), 0.01f)
        assertEquals(271.6f, grid(320f).limit(), 0.01f)
        assertEquals(164.93f, grid(426.67f).limit(), 0.02f)
    }

    @Test
    fun eachSlotHitsItsPitchCellAndEmptyLettersHitNothing() {
        val g = grid(426.67f, avail = { it != 2 })
        assertEquals(Jump.EXIT, g.hit(10.01f - 2f, 19.54f - 2f, 0f))
        assertEquals(0, g.hit(66.04f + 25f, 19.54f + 25f, 0f))
        assertEquals(Jump.NONE, g.hit(178.10f + 25f, 19.54f + 25f, 0f))
        assertEquals(1, g.hit(122.07f + 25f, 19.54f + 25f, 0f))
        assertEquals(Jump.NONE, g.hit(5f, 100f, 0f))
        assertEquals(Jump.NONE, g.hit(120f, 15f, 0f))
        assertEquals(Jump.SEARCH, g.hit(10.01f + 25f, 19.54f + 7 * 56.03f + 25f - 100f, 100f))
    }

    @Test
    fun theOpenFocusScrollsIntoTheBandAndAStepSkipsEmptyLetters() {
        val g = grid(320f, avail = { it == 0 || it == 25 || it == 26 }, section = 26)
        val top = 19.54f + 6 * 56.03f
        assertEquals(top + 51.79f - (320f - 16f), g.offsetFor(26, 0f), 0.01f)
        assertTrue(g.inBand(Jump.slotOf(26), g.offsetFor(26, 0f) + 0.01f))
        assertTrue(g.step(-1, 0))
        assertEquals(25, g.focus)
        assertTrue(g.step(-1, 0))
        assertEquals(0, g.focus)
        assertTrue(g.step(-1, 0))
        assertEquals(Jump.EXIT, g.focus)
        assertFalse(g.step(-1, 0))
        assertEquals(0f, g.offsetFor(Jump.EXIT, 200f), 0f)
    }

    @Test
    fun onlyAStepAsksForTheF2ScrollAndNotADown() {
        val g = grid(320f, section = 0)
        g.down(26, 0)
        assertEquals(0, g.aims)
        assertEquals(26, g.focus)
        repeat(5) { g.step(1, 0) }
        assertEquals(Jump.itemOf(Jump.slotOf(26) + 5), g.focus)
        assertTrue(g.aims > 0)
        assertEquals(g.offsetFor(g.focus, g.target), g.target, 0f)
        g.dragging = true
        val aims = g.aims
        g.step(1, 0)
        assertEquals(aims, g.aims)
    }

    @Test
    fun aScrollMovesTheFocusToTheNearestTakenSlotInViewOnTheSideItLeft() {
        val g = grid(320f, section = 0)
        val off = 2 * 56.03f
        g.focusAfterScroll(off, 0)
        assertEquals(Jump.itemOf(8), g.focus)
    }
}
