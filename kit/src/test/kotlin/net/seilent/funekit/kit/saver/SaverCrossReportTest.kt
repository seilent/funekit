package net.seilent.funekit.kit.saver

import androidx.compose.ui.text.font.createFontFamilyResolver
import net.seilent.funekit.gen.TokBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SaverCrossReportTest {
    @Test
    fun theKitsOwnTextDecidesAsFuneOsOnEveryLaneButTheListedOnes() {
        val lines = needFixture("arrange-a105.txt", "the funeOS saver's recorded font calls and arranger outputs for a105")
        val listed = (fixture("cross-a105.txt") ?: emptyList()).filter { it.isNotBlank() }.toSet()
        val dump = ProbeDump(lines)
        val text = SaverText(createFontFamilyResolver(RuntimeEnvironment.getApplication()))
        var lanes = 0
        val differ = LinkedHashSet<String>()
        for (c in dump.cases) {
            val e = SaverScenes(TokBoard.a105, dump.ppu, dump.w, dump.h, text).kit(c.title, c.artist, c.album, c.pos)
            e.show(0, 1)
            e.shotScene(SaverScene.valueOf(c.scene), 1000)
            val want = c.want.filter { it.startsWith("out ") }.map { it.split(' ') }
            for ((i, l) in e.lanes.withIndex()) {
                val w = want.getOrNull(i) ?: continue
                lanes++
                val kit = listOf(l.t0 - e.sceneAt, b(l.skip), b(l.gave), b(l.unfit), l.kept, l.brkCopy, l.brkKeep).joinToString(" ")
                val fos = listOf(w[2], w[4], w[5], w[6], w[7], w[10], w[11]).joinToString(" ")
                if (kit != fos) differ += "${c.set} ${c.scene}${i + 1} funeOS $fos kit $kit"
            }
        }
        assertTrue("read $lanes", lanes > 0)
        assertEquals("lanes the kit's own text lays otherwise than funeOS, not in saver/cross-a105.txt", emptyList<String>(), (differ - listed).toList())
        assertEquals("saver/cross-a105.txt lines no lane differs by", emptyList<String>(), (listed - differ).toList())
    }
}
