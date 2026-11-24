package net.seilent.funekit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcSourceTest {
    private val lines = LrcSourceTest::class.java.getResourceAsStream("/lrc/SOURCE")!!.bufferedReader().readLines().filter { it.isNotEmpty() }

    @Test
    fun theSourceNamesTheReleaseAndHashesTheThreeLrcFiles() {
        assertEquals("from funeOS v0.4.4", lines.first())
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
                "c226609f8fc45d4344f6de0c587ba6af17938e919e58d8b4bda1da01a48f041f",
            ),
            rows.map { it[1] },
        )
    }
}
