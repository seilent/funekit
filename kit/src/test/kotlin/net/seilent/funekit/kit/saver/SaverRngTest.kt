package net.seilent.funekit.kit.saver

import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SaverRngTest {
    @Test
    fun theGeneratorIsTheStandardMt19937() {
        val r = Mt19937()
        r.seed(5489)
        var v = 0L
        repeat(10000) { v = r.next() }
        assertEquals(4123659995L, v)
    }

    @Test
    fun sceneGapsAreTriangularFrom180To1340WithTheirModeAt600() {
        val r = Mt19937()
        r.seed(1234)
        val g = LongArray(100000) {
            r.triangular(fx(Tok.motion_saverGapMin), fx(Tok.motion_saverGapMode), fx(Tok.motion_saverGapMax))
        }
        val below = g.count { it <= 600 }
        g.sort()
        assertTrue("gaps ${g.first()} to ${g.last()}", g.first() >= 180 && g.last() <= 1340 && g.first() < 220 && g.last() > 1250)
        assertTrue("cdf at 600 ${below / 100000.0}", abs(below / 100000.0 - 0.362) <= 0.015)
        assertTrue("median ${g[50000]}", abs(g[50000] - 685) <= 10)
    }

    @Test
    fun theDrawsEqualFuneOsProbesDraws() {
        val lines = needFixture("draws.txt", "the funeOS saver's first 1000 draws for seed 1")
        val want = lines.map { it.split(' ') }.filter { it[0] == "draw" }
        val got = ArrayList<List<String>>()
        val r = Mt19937()
        r.seed(1)
        repeat(1000) { got += listOf("draw", "u15", r.uniform(0, 14).toString()) }
        r.seed(1)
        repeat(1000) { got += listOf("draw", "u14", r.uniform(0, 13).toString()) }
        r.seed(1)
        repeat(1000) {
            got += listOf("draw", "gap", r.triangular(fx(Tok.motion_saverGapMin), fx(Tok.motion_saverGapMode), fx(Tok.motion_saverGapMax)).toString())
        }
        r.seed(1)
        repeat(1000) { got += listOf("draw", "ink", r.uniform(770, 1420).toString()) }
        val e = a105().kit()
        e.show(0, 7)
        got += listOf("draw", "first", e.firstAt.toString())
        got += listOf("draw", "scene", e.scene.ordinal.toString())
        got += listOf("draw", "gapat", e.gap.toString())
        repeat(300) {
            e.step(e.nextAt())
            got += listOf("draw", "scene", e.scene.ordinal.toString())
            got += listOf("draw", "at", e.sceneAt.toString())
            got += listOf("draw", "gapat", e.gap.toString())
        }
        assertEquals(want.size, got.size)
        for (i in want.indices) assertEquals("draw $i", want[i], got[i])
    }
}
