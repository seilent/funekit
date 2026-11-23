package net.seilent.funekit.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BoardLoadTest {
    private val names = File("funeos/player/ui/tokens-v1.txt").readLines().filter { it.isNotBlank() }.map { it.substringBefore(' ') }

    private val getters = TokBoard.Board::class.java.methods.filter { it.declaringClass == TokBoard.Board::class.java && it.parameterCount == 0 }

    @Test
    fun everyBoardLoadsAndAnswersEveryMember() {
        assertEquals(1690, names.size)
        assertEquals(442, getters.size)
        for (b in listOf(TokBoard.v1, TokBoard.r1, TokBoard.a105, TokFit.at(480f, 4.5f), TokFit.at(320f, 3.275f))) {
            for (g in getters) assertTrue("${b.javaClass.simpleName} ${g.name}", g.invoke(b) != null)
        }
    }

    @Test
    fun theMovesObjectLoadsEveryTable() {
        val tables = Moves::class.java.declaredFields.filter { it.type == Keys::class.java }
        assertEquals(69, tables.size)
        for (f in tables) {
            f.isAccessible = true
            val k = f.get(Moves) as Keys
            assertEquals(f.name, k.t.size, k.v.size)
            assertTrue(f.name, k.t.isNotEmpty())
        }
        assertEquals(4, Moves::class.java.declaredFields.count { it.name.startsWith("kT7Peek") })
    }
}
