package net.seilent.funekit.kit.saver

import org.junit.Assert.assertTrue
import org.junit.Test

internal class ProbeCase(val set: String, val scene: String, val title: String, val artist: String, val album: String, val pos: Long) {
    val want = ArrayList<String>()
}

internal class ProbeDump(lines: List<String>) {
    val w: Int
    val h: Int
    val ppu: Float
    val cases = ArrayList<ProbeCase>()

    init {
        val head = lines.first { it.startsWith("board ") }.split(' ')
        w = head[1].toInt()
        h = head[2].toInt()
        ppu = head[3].toInt() / 65536f
        var at: ProbeCase? = null
        val bodies = ArrayList<String>()
        for (line in lines) {
            val p = line.split(' ')
            when (p[0]) {
                "case" -> at = ProbeCase(p[1], p[2], unhex(p[3]), unhex(p[4]), unhex(p[5]), p[6].toLong()).also { cases += it }
                "end" -> at = null
                "text" -> {
                    val body = if (p[2].startsWith("=")) bodies[p[2].drop(1).toInt()] else p.drop(2).joinToString(" ").also { bodies += it }
                    at?.want?.add("text ${p[1]} $body")
                }
                "lay", "out", "cross", "scene" -> at?.want?.add(line)
            }
        }
    }
}

class SaverCrossTest {
    @Test
    fun theArrangerGivesFuneOsOutputsForEveryCaseOfTheDump() {
        val bad = ArrayList<String>()
        var read = 0
        for ((name, board) in boards.take(1)) {
            val lines = needFixture("arrange-$name.txt", "the funeOS saver's recorded font calls and arranger outputs for $name")
            val dump = ProbeDump(lines)
            val font = RecordedFont(lines)
            for (c in dump.cases) {
                val got = try {
                    val e = SaverScenes(board, dump.ppu, dump.w, dump.h, font).kit(c.title, c.artist, c.album, c.pos)
                    e.show(0, 1)
                    e.shotScene(SaverScene.valueOf(c.scene), 1000)
                    e.probeDump()
                } catch (x: AssertionError) {
                    listOf("${x.message}")
                }
                read++
                val n = maxOf(got.size, c.want.size)
                var shown = 0
                for (i in 0 until n) {
                    val a = c.want.getOrNull(i)
                    val b = got.getOrNull(i)
                    if (a != b && shown++ < 3) bad += "$name ${c.set} ${c.scene}:\n  funeOS $a\n  kit    $b"
                }
            }
        }
        assertTrue("read $read", read > 0)
        assertTrue("${bad.size} differences\n" + bad.take(40).joinToString("\n"), bad.isEmpty())
    }
}
