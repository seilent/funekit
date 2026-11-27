package net.seilent.funekit.kit.saver

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fx
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverSpecsTest {
    @Test
    fun theSpecsHoldNinetyOneLanesInFuneOsOrder() {
        val s = specsOf(TokBoard.a105)
        assertEquals(91, s.size)
        assertEquals(listOf(8, 7, 6, 5, 5, 8, 7, 6, 5, 4, 6, 5, 6, 6, 7), SaverScene.entries.map { sc -> s.count { it.scene == sc } })
        assertEquals(23, s.count { it.src == Src.Art })
        assertEquals(68, s.count { it.src != Src.Art })
        assertTrue(s.zipWithNext().all { (a, b) -> a.scene.ordinal <= b.scene.ordinal })
        assertEquals(listOf("A", "B", "D", "E", "F", "H", "S1", "K", "P", "R", "J", "L", "M", "N", "Q"), SaverScene.entries.map { it.name })
    }

    @Test
    fun theSpecsReadEachBoardsTokens() {
        val a = specsOf(TokBoard.a105)
        assertEquals(fx(285.3f), a[0].size)
        assertEquals(fx(-24.9f), a[0].speed)
        val e = a.filter { it.scene == SaverScene.E }
        assertEquals(fx(432.0f), e[3].across)
        assertEquals(fx(117.3f), e[3].size)
        assertEquals(Src.Time, e[3].src2)
        assertEquals(fx(38.39f), e[4].on)
        assertEquals(fx(52.92f), e[4].off)
        assertEquals(fx(55.2f), e[4].tall)
        assertEquals(fx(214f), specsOf(TokBoard.v1)[0].size)
        assertEquals(fx(267.5f), specsOf(TokBoard.r1)[0].size)
    }

    @Test
    fun aLargeScaleLaysTheSameSaverAsTheStandardOne() {
        val none = Insets(0, 0, 0, 0)
        val w = Window(720, 1280, 35, none, none, none, none, none)
        val std = scale(w, Nav.None, Surface.Saver, false)
        val big = scale(w, Nav.None, Surface.Saver, true)
        assertNotEquals(std.ppu, big.ppu)
        assertEquals(std.stdPpu, big.stdPpu)
        val a = SaverScenes(std.board, std.stdPpu, 720, 1280, BoxFont()).kit()
        val b = SaverScenes(big.board, big.stdPpu, 720, 1280, BoxFont()).kit()
        for (e in listOf(a, b)) {
            e.show(0, 1)
            e.shotScene(SaverScene.K, 0)
        }
        assertEquals(a.probeDump(), b.probeDump())
    }
}
