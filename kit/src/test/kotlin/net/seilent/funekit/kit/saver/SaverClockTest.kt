package net.seilent.funekit.kit.saver

import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverClockTest {
    private val lo = ms(fx(Tok.motion_saverWordDelayMin)).toLong()
    private val hi = ms(fx(Tok.motion_saverWordDelayMax)).toLong()

    @Test
    fun aShowsFirstSceneIsDrawnFromAllFifteenWithItsFirstInkUniformAfterTheShow() {
        val e = a105().kit()
        val firsts = IntArray(SaverScene.entries.size)
        var least = Long.MAX_VALUE
        var most = 0L
        for (n in 1L..300L) {
            e.show(0, n)
            val first = e.firstAt
            least = minOf(least, first)
            most = maxOf(most, first)
            assertTrue("seed $n first $first", e.sceneAt == first && first in lo..hi)
            assertEquals(SaverStage.Gap, e.stage(first - 1))
            assertEquals(SaverStage.Scene, e.stage(first))
            firsts[e.scene.ordinal]++
            e.land()
        }
        assertTrue(firsts.joinToString(), firsts.all { it > 0 })
        assertTrue("$least to $most", least < lo + 100 && most > hi - 100)
    }

    @Test
    fun aPinMakesTheNextSceneItsTemplateOnce() {
        val e = a105().kit()
        e.pinNext(SaverScene.E)
        e.show(0, 7)
        assertEquals(SaverScene.E, e.scene)
        e.step(e.sceneEnd() + e.whoosh)
        assertNotEquals(SaverScene.E, e.scene)
    }

    @Test
    fun threeHundredScenesNeverRepeatAndEachGapIsFrom180To1340() {
        val fine = a105().kit()
        val jump = a105().kit()
        fine.show(0, 7)
        jump.show(0, 7)
        val seen = IntArray(SaverScene.entries.size)
        var last = fine.scene
        repeat(300) {
            val gone = fine.sceneEnd() + fine.whoosh
            val next = fine.nextAt()
            assertTrue("gap ${next - gone}", next - gone in 180L..1340L)
            var t = gone
            while (t < next) {
                fine.step(t)
                t += 64
            }
            fine.step(next)
            assertNotEquals(last, fine.scene)
            last = fine.scene
            seen[fine.scene.ordinal]++
        }
        assertTrue(seen.joinToString(), seen.all { it > 0 })
        jump.step(fine.sceneAt)
        assertEquals(fine.scene, jump.scene)
        assertEquals(fine.sceneAt, jump.sceneAt)
        assertEquals(fine.nextAt(), jump.nextAt())
    }

    @Test
    fun eachRecordedSeedDrawsItsRecordedSceneAndFirstInk() {
        val drawn = setOf("saver-500", "saver-4000", "saver-9000", "saver-4000-photo", "saver-4000-cjk", "saver-4000-accent", "saver-4000-qj", "saver-gap")
        for ((name, _) in boards) {
            val lines = needFixture("seeds-$name.txt", "the funeOS saver's recorded seeds for $name")
            var read = 0
            for (line in lines) {
                val p = line.trim().split(' ')
                if (p.size < 5) continue
                val kind = p[0]
                val e = a105().kit()
                if (kind == "saver-gap") e.pinNext(SaverScene.E)
                e.show(p[2].toLong(), p[1].toLong())
                val scenes = p.drop(3).chunked(2)
                val upto = if (kind in drawn) scenes.size else 1
                for ((k, s) in scenes.take(upto).withIndex()) {
                    if (k > 0) e.step(e.nextAt())
                    assertEquals("$name $kind scene $k", s[0], e.scene.name)
                    assertEquals("$name $kind first ink $k", s[1].toLong(), e.sceneAt)
                }
                read++
            }
            assertTrue("$name read $read", read >= drawn.size)
        }
    }
}
