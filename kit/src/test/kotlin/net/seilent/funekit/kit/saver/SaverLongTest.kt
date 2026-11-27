package net.seilent.funekit.kit.saver

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class SaverLongTest {
    private val four = buildString {
        while (utf8(this.toString()).size + 30 <= 4096) append("夜に駆ける夜に駆ける夜に駆ける、")
    }

    @Test
    fun everyLaneIsLaidWithA4kBTitleOneGlyphOrNoAlbum() {
        val sets = listOf(
            "4 kB" to Triple(four, "DECO*27 FEAT. とぴ", "相愛性理論"),
            "one glyph" to Triple("A", "愛", "1"),
            "no album" to Triple("My Name Is Jonas", "Weezer", ""),
        )
        var bad = ""
        for ((tag, set) in sets) {
            for (s in SaverScene.entries) {
                val e = a105().kit(set.first, set.second, set.third, 15000)
                e.show(0, 1)
                e.shotScene(s, 0)
                val end = e.sceneEnd()
                val specs = e.specs.count { it.scene == s }
                var skipped = 0
                for ((i, l) in e.lanes.withIndex()) {
                    val sp = l.spec
                    val count = sp.pieces > 0
                    val once = !sp.repeat || count
                    val name = "$tag ${s.name}${i + 1}"
                    if (once && e.laneIn(l, end - 1) && !e.laneIn(l, end)) bad += " $name cut at the whoosh"
                    if (sp.repeat && !count && sp.src != Src.Art) {
                        var t = 0L
                        while (t < end) {
                            if (e.laneIn(l, t)) {
                                val c = IntArray(2)
                                e.laneCopyRange(l, t, c)
                                if (c[1] - c[0] + 1 > 256) bad += " $name copies ${c[1] - c[0] + 1}"
                            }
                            t += 1000
                        }
                    }
                    var live = false
                    var t = 0L
                    while (t < end && !live) {
                        live = e.laneIn(l, t)
                        t += 100
                    }
                    val empty = sp.src != Src.Art && l.t.s.isEmpty()
                    if (!live && !empty && !l.gave && l.block.lane >= 0) skipped++
                    else if (!live && !empty && !l.gave) bad += " $name gone unnamed"
                    val v = e.toPx(abs(sp.speed)) / 65536.0
                    val chord = if (sp.angle == 0) 720.0 else if (sp.angle == UP) 1280.0 else -1.0
                    val due = end - e.sceneAt - (if (chord > 0 && v > 0) (chord / v * 1000).toLong() else 0L)
                    val hold = l.t0 - e.sceneAt - secsMs(sp.inAt)
                    if (hold > 0 && chord > 0 && v > 0 && l.t0 - e.sceneAt > maxOf(secsMs(sp.inAt), due)) bad += " $name late ${l.t0 - e.sceneAt}"
                }
                if (e.lanes.size != specs || skipped != e.crossLeft) bad += " $tag ${s.name} lanes ${e.lanes.size} of $specs skipped $skipped"
            }
        }
        assertEquals("", bad)
    }
}
