package net.seilent.funekit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcSourceTest {
    private val lines = LrcSourceTest::class.java.getResourceAsStream("/lrc/SOURCE")!!.bufferedReader().readLines().filter { it.isNotEmpty() }

    @Test
    fun theSourceNamesTheReleaseAndHashesTheThreeLrcFiles() {
        assertEquals("from funeOS v0.4.2", lines.first())
        assertTrue(lines.none { it.startsWith("commit") })
        val rows = lines.drop(1).map { it.split(' ') }
        assertEquals(listOf("player/lib/lrc.cpp", "player/lib/lrc.h", "player/lib/util.cpp"), rows.map { it[0] })
        for (r in rows) {
            assertEquals(r.joinToString(" "), 2, r.size)
            assertTrue(r[0], Regex("[0-9a-f]{64}").matches(r[1]))
        }
        assertEquals(
            listOf(
                "1175539d971b8eb976915e1a2dbc342d69ae639caf046a68910b9389c6e16618",
                "7f285d60b70ddbcb08187afe40fdd970582a9db33980e0db2d992dd6e97ff49c",
                "1ee2d7a1e69e740ea9a7a3494de2ae807667359c4d421d6b51885541a3683dfa",
            ),
            rows.map { it[1] },
        )
    }
}
