package net.seilent.funekit.screens.music.queue

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.cased
import net.seilent.funekit.scale
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.screens.music.accentShown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDrawTest {
    private val none = Insets(0, 0, 0, 0)

    private fun geo(w: Int, h: Int) = QueueGeo(scale(Window(w, h, 35, none, none, none, none, none), Nav.None, Surface.Page, false))

    @Test
    fun theRowsCountAndEmptyLineSitOnTheBoardTokens() {
        val a = geo(720, 1280)
        assertEquals(360, a.top)
        assertEquals(120, a.pitch)
        assertEquals(257, a.countBase)
        assertEquals(408, a.emptyBase)
        assertEquals(60f, a.x, 0.001f)
        assertEquals(1280 - 360, a.view)
        val r = geo(480, 800)
        assertEquals(240, r.top)
        assertEquals(80, r.pitch)
        assertEquals(171, r.countBase)
        val v = geo(240, 320)
        assertEquals(98, v.top)
        assertEquals(40, v.pitch)
        assertEquals(79, v.countBase)
    }

    @Test
    fun theCountReadsSlotOfSizeInTheCountCaseAndNoneOnceAReadyQueueIsEmpty() {
        val m = QueueModel(0)
        assertEquals("0 of 0", m.count())
        m.state(PlayerState(queueSize = 8, index = 2, track = 5))
        assertEquals("3 OF 8", cased(checkNotNull(m.count()), Roles.listCount))
        m.state(PlayerState())
        m.loaded = true
        assertNull(m.count())
        assertTrue(m.empty())
        m.loaded = false
        assertFalse(m.empty())
    }

    @Test
    fun aCoveredQueueDrawsItsPlayingTitleWholeAsAHiddenFocusDoes() {
        assertTrue(accentShown(hideFocus = false, covered = false))
        assertFalse("a page under a sheet or dialog draws its playing title whole", accentShown(hideFocus = false, covered = true))
        assertFalse(accentShown(hideFocus = true, covered = false))
    }

    @Test
    fun looksHoldAtRestAndAFocusStepChangesTwoRows() {
        val m = QueueModel(0)
        m.state(PlayerState(queueSize = 8, index = 2, track = 5))
        m.focus.set(2, 8, 0L, false)
        val rest = (0 until 8).map { m.look(it, 1_000L, true) }
        for (t in 1_000L until 1_100L) assertEquals(rest, (0 until 8).map { m.look(it, t, true) })
        m.focus.set(3, 8, 2_000L, false)
        val after = (0 until 8).map { m.look(it, 2_000L, true) }
        assertEquals(listOf(2, 3), (0 until 8).filter { rest[it] != after[it] })
        assertNotEquals(rest[3], after[3])
    }
}
