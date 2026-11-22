package net.seilent.funekit.kit.stack

import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.touch.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeaveTest {
    private class Pg(val name: String) {
        override fun toString() = name
    }

    private class Leaving(vararg names: String) {
        val pg = names.map(::Pg)
        val drops = mutableListOf<Pg>()
        val starts = mutableListOf<Turn<Pg>>()
        val kinds = mutableListOf<Pair<Pg?, Pg?>>()
        val stack = PageStack<Pg>(MoveForms(), { PageKind.Grid }, drop = { drops += it }, started = { starts += it }) { MoveEnds() }
            .also { it.pages.add(pg[0]) }
        val kind: (Turn<Pg>) -> MoveKind = {
            kinds += it.from to it.to
            MoveKind.T4
        }
        var t = 0L

        init {
            for ((i, p) in pg.withIndex().drop(1)) {
                stack.push(p, MoveKind.T3, row = i)
                rest()
            }
            starts.clear()
        }

        operator fun get(name: String): Pg = pg.first { it.name == name }

        fun names(): List<String> = stack.pages.map { it.name }

        fun rest() {
            stack.frame(t)
            while (stack.phase != Phase.Rest) {
                t += STEP
                stack.frame(t)
            }
            t += STEP
        }
    }

    @Test
    fun aFivePageLeaveErasesABuriedEntryAndItsPushRow() {
        val l = Leaving("hub", "a", "b", "c", "d")
        val b = l["b"]
        l.stack.leave({ it === b }, l.kind)
        l.rest()
        assertEquals(listOf("hub", "a", "c", "d"), l.names())
        assertEquals(listOf(b), l.drops)
        assertTrue("an erase starts no turn", l.starts.isEmpty())
        l.stack.back(l.kind)
        l.rest()
        assertEquals("d lands c on the row it was pushed from", 4, l.starts.last().row)
        l.stack.back(l.kind)
        l.rest()
        assertEquals("c lands a with no row, its row was into b", -1, l.starts.last().row)
        l.stack.back(l.kind)
        l.rest()
        assertEquals("a lands hub on the row it was pushed from", 1, l.starts.last().row)
        assertEquals(listOf("hub"), l.names())
    }

    @Test
    fun aLeaveOfTheTopPopsOnceWithTheKindOnTheEntryBelowAfterTheErases() {
        val l = Leaving("hub", "a", "b")
        val a = l["a"]
        val b = l["b"]
        l.stack.leave({ it === a || it === b }, l.kind)
        l.rest()
        assertEquals(listOf("hub"), l.names())
        assertEquals("one pop", 1, l.starts.size)
        assertSame(b, l.starts[0].from)
        assertSame(l["hub"], l.starts[0].to)
        assertTrue(l.starts[0].popping)
        assertEquals(listOf<Pair<Pg?, Pg?>>(b to l["hub"]), l.kinds)
        assertEquals("the buried entry drops at the pass, the top at its pop's land", listOf(a, b), l.drops)
    }

    @Test
    fun aTapQueuedBeforeTheExitIsNotPopped() {
        val l = Leaving("hub", "a", "b")
        val b = l["b"]
        val x = Pg("x")
        l.stack.push(x, MoveKind.T3)
        l.stack.leave({ it === b }, l.kind)
        l.rest()
        assertEquals(listOf("hub", "a", "x"), l.names())
        assertEquals(listOf(b), l.drops)
        assertEquals("only the push turns", listOf(MoveKind.T3), l.starts.map { it.kind })
    }

    @Test
    fun anExitUnderARisingSheetWaitsForRest() {
        val l = Leaving("hub", "a")
        val a = l["a"]
        val o = Overlay(OverKind.Sheet)
        l.stack.open(o)
        l.stack.leave({ it === a }, l.kind)
        l.stack.frame(l.t)
        assertTrue("premise: the sheet rises", o.moving(l.t + STEP))
        assertEquals(listOf("hub", "a"), l.names())
        assertTrue("nothing leaves under a rising sheet", l.starts.isEmpty())
        var t = l.t + o.dur().toLong()
        l.stack.frame(t)
        assertSame(o, l.stack.overlay)
        assertTrue("nothing leaves under a settled sheet", l.starts.isEmpty())
        l.stack.close()
        while (l.stack.overlay != null) {
            t += STEP
            l.stack.frame(t)
            if (l.stack.overlay != null) assertTrue("nothing leaves while the sheet closes", l.starts.isEmpty())
        }
        assertEquals("the leave runs on the frame the sheet drops", listOf(MoveKind.T4), l.starts.map { it.kind })
        l.t = t
        l.rest()
        assertEquals(listOf("hub"), l.names())
    }

    @Test
    fun twoExitsDueTogetherPopOnce() {
        val l = Leaving("hub", "a", "b")
        val a = l["a"]
        val b = l["b"]
        l.stack.leave({ it === a || it === b }) { MoveKind.T4n }
        l.stack.leave({ it === b }, l.kind)
        l.rest()
        assertEquals("both leaves hold in the one pass", listOf("hub"), l.names())
        assertEquals("one pop", 1, l.starts.size)
        assertEquals("the later pop kind is kept", MoveKind.T4, l.starts[0].kind)
        assertSame(b, l.starts[0].from)
    }

    @Test
    fun aBackHeldAcrossATopOnlyLeaveLandsNothingAndPopsNoPage() {
        val l = Leaving("hub", "a")
        val b = Pg("b")
        l.stack.push(b, MoveKind.T3)
        l.stack.frame(l.t)
        assertTrue("premise: the band holds the push", l.stack.hold(l.t + 50, target = false, band = Gesture.Edge.Left))
        l.stack.leave({ it === b }, l.kind)
        var t = l.t
        while (l.stack.turn?.popping != true && t < LANDED) {
            t += STEP
            l.stack.frame(t)
        }
        assertSame("premise: the leave pops the pushed page", b, l.stack.turn?.from)
        l.stack.heldBack(l.kind)
        l.t = t + STEP
        l.rest()
        assertEquals(listOf("hub", "a"), l.names())
        assertEquals("the push and the leave's pop only", listOf(MoveKind.T3, MoveKind.T4), l.starts.map { it.kind })
    }

    @Test
    fun aLeaveThatMatchesNothingChangesNothing() {
        val l = Leaving("hub", "a", "b")
        l.stack.leave({ false }, l.kind)
        l.rest()
        assertEquals(listOf("hub", "a", "b"), l.names())
        assertTrue(l.drops.isEmpty())
        assertTrue(l.starts.isEmpty())
        assertTrue("no pop kind asked", l.kinds.isEmpty())
        l.stack.back(l.kind)
        l.rest()
        assertEquals("the rows are kept", 2, l.starts.last().row)
    }

    @Test
    fun aBackHeldAcrossALeaveThatMatchesNothingStillLands() {
        val l = Leaving("hub", "a")
        val b = Pg("b")
        l.stack.push(b, MoveKind.T3)
        l.stack.frame(l.t)
        assertTrue("premise: the band holds the push", l.stack.hold(l.t + 50, target = false, band = Gesture.Edge.Left))
        l.stack.leave({ false }, l.kind)
        l.rest()
        assertEquals("premise: the push landed and the pass changed nothing", listOf("hub", "a", "b"), l.names())
        l.stack.heldBack(l.kind)
        l.rest()
        assertEquals("the held back still pops the pushed page", listOf("hub", "a"), l.names())
        assertEquals(listOf(MoveKind.T3, MoveKind.T4), l.starts.map { it.kind })
    }

    private companion object {
        const val STEP = 16L
        const val LANDED = 5000L
    }
}
