package net.seilent.funekit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionParityTest {
    private class Row(val title: String, val the: Boolean, val letter: String)

    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun rows() = SectionParityTest::class.java.getResourceAsStream("/section/funeos.tsv")!!.bufferedReader().readLines()
        .filter { it.isNotEmpty() }
        .map { l -> l.split('\t').let { Row(String(unhex(it[0]), Charsets.UTF_8), it[1] == "1", it[2]) } }

    @Test
    fun everyTitleTakesTheFuneosLetter() {
        val rows = rows()
        assertEquals(59, rows.size)
        for (r in rows) assertEquals(r.title, r.letter, Section.letter(Section.key(r.title, r.the).letter))
    }

    @Test
    fun theSortKeysRunInTheFuneosOrder() {
        val keys = rows().map { Section.key(it.title, it.the).sort }
        for (i in 1 until keys.size) assertTrue("row $i", Section.compare(keys[i - 1], keys[i]) < 0)
    }
}
