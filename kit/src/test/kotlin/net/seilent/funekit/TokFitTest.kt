package net.seilent.funekit

import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.gen.TokFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

class TokFitTest {
    private val dir = File("../tokens/funeos/player/ui")
    private val fit = File("../tokens/fit.txt").readLines().filter { it.isNotBlank() }.associate { it.substringBefore(' ') to it.substringAfter(' ') }

    private fun read(board: TokBoard.Board, name: String): Any? {
        val id = name.replace('.', '_')
        return TokBoard.Board::class.java.getMethod("get" + id.replaceFirstChar { it.uppercase() }).invoke(board)
    }

    @Test
    fun atEachBoardHeightAndPpuTheBoardLiteralsComeBack() {
        for ((file, literal) in listOf("tokens-v1.txt" to TokBoard.v1, "tokens-r1.txt" to TokBoard.r1, "tokens-a105.txt" to TokBoard.a105)) {
            val rows = File(dir, file).readLines().filter { it.isNotBlank() }.map { it.split(' ') }
            val at = TokFit.at(literal.canvasH, literal.u)
            var seen = 0
            for (r in rows) {
                val cls = fit[r[0]] ?: continue
                if (cls == "x") continue
                val want: Any = r[1].toFloatOrNull() ?: r[1]
                assertEquals("$file ${r[0]}", want, read(literal, r[0]))
                if (cls != "budget") assertEquals("$file ${r[0]} at", want, read(at, r[0]))
                seen++
            }
            assertEquals(311, seen)
        }
    }

    @Test
    fun budgetTokensTakeTheV1Value() {
        val at = TokFit.at(TokBoard.a105.canvasH, TokBoard.a105.u)
        assertEquals(17.19f, at.motion_volumeTiltAngle)
        assertEquals(12.5f, at.motion_volumeTiltDepth)
        assertEquals("zoom", at.motion_contentHubRows)
    }

    @Test
    fun aboveTheA105EachClassFollowsItsOwnRule() {
        val at = TokFit.at(480f, 4.5f)
        assertEquals(480f, at.canvasH, 0.001f)
        assertEquals(451.8f, at.npRingY, 0.001f)
        assertEquals(386.03f, at.npTitleY, 0.005f)
        assertEquals(348.02f, at.vol_minusY, 0.005f)
        assertEquals(486f, at.saverFourthBaseline, 0.005f)
        assertEquals(120f, at.page_contentTop)
        assertEquals(159.3f, at.npArt, 0.001f)
        assertEquals(3f, at.npNextCount)
        assertEquals(36f, at.minTextPx, 0.0001f)
    }

    @Test
    fun ppuTokensFollowThePpu() {
        val at = TokFit.at(400f, 3.2f)
        assertEquals(3.2f, at.u)
        assertEquals(25.6f, at.minTextPx, 0.0001f)
        assertEquals(149.4f, at.npArt)
        assertEquals(3f, at.npNextCount)
    }

    @Test
    fun aPageBoardTakesItsFreeTokensFromTheFitBoard() {
        val free = fit.filterValues { it == "free" }.keys.toList()
        assertEquals(
            listOf(
                "npArtistY", "npAlbumY", "npFromBaseline", "npArtTop", "npArt", "lockBtGlyph", "kb.keyH", "overlayAnchorScale",
                "vol.ringY", "npSeekBandX1", "npSeekBandBottom", "large.npLyricPitch", "large.npNextPitch", "large.npAlbumPitch",
            ),
            free,
        )
        val fitBoard = TokFit.at(480f, 4.5f)
        val page = TokFit.at(728.08f, 2.96669f, fitBoard)
        val plain = TokFit.at(728.08f, 2.96669f)
        for (n in free) assertEquals(n, read(fitBoard, n), read(page, n))
        assertNotEquals(read(plain, "npArt"), read(page, "npArt"))
        for ((n, cls) in listOf("overlayRingY" to "hr", "npRingY" to "h", "pivotTop" to "v1c")) {
            assertEquals(n, cls, fit[n])
            assertEquals(n, read(plain, n), read(page, n))
        }
        val snap = TokFit.at(TokBoard.a105.canvasH, TokBoard.a105.u, fitBoard)
        for (n in free) assertEquals("snap $n", read(fitBoard, n), read(snap, n))
        assertEquals(TokBoard.a105.pivotTop, snap.pivotTop)
    }
}
