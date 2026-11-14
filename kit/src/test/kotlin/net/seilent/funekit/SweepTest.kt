package net.seilent.funekit

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.gen.TokFit
import org.junit.Assert.assertTrue
import org.junit.Test

class SweepTest {
    private val slack = 0.001f

    private fun order(h: Float, stack: String, places: List<Pair<String, Float>>) {
        for ((a, b) in places.zipWithNext())
            assertTrue("H $h $stack: ${a.first} ${a.second} above ${b.first} ${b.second}", a.second <= b.second + slack)
    }

    private fun nowPlaying(b: TokBoard.Board, h: Float, navOn: Boolean): List<Pair<String, Float>> {
        val ringY = if (navOn) b.npRingNavY else b.npRingY
        val count = (if (navOn) b.npNextNavCount else b.npNextCount).toInt()
        val next = (0 until count).map { "next $it" to b.npTitleY + Tok.npNextDrop + it * Tok.npNextLinePitch }
        return listOf(
            "back ring bottom" to Tok.npBackRingY + Tok.npBackRing,
            "artist" to b.npArtistY,
            "album" to b.npAlbumY,
            "art top" to b.npArtTop,
            "art bottom" to b.npArtTop + b.npArt,
            "progress" to b.npProgressY,
            "title" to b.npTitleY,
        ) + next + listOf(
            "ring top" to ringY - Tok.npRing / 2,
            "ring centre" to ringY,
            "H" to h,
        )
    }

    private fun hub(b: TokBoard.Board, h: Float, navOn: Boolean) = listOf(
        "header baseline" to Tok.page_headerBaseline,
        "pivot top" to b.pivotTop,
        "pivot baseline" to b.page_pivotBaseline,
        "pivot bottom" to b.pivotBottom,
        "content top" to b.page_contentTop,
        "focus band bottom" to b.focusBandBottom,
        "bottom" to if (navOn) h - Tok.navH else h,
    )

    private fun hubGrid(b: TokBoard.Board, h: Float, navOn: Boolean, large: Boolean): List<Pair<String, Float>> {
        val top = b.page_contentTop
        val tile = top + Tok.gridTile
        val drop = if (large) Tok.tileCaptionDropLarge else Tok.tileCaptionDrop
        return listOf(
            "content top" to top,
            "tile bottom" to tile,
            "title baseline" to tile + drop,
            "sub baseline" to tile + drop + if (large) Tok.tileCaptionPitchLarge else Tok.tileCaptionPitch,
            "caption band bottom" to tile + if (large) Tok.tileCaptionBandLarge else Tok.tileCaptionBand,
            "cell bottom" to top + if (large) Tok.tileCellHLarge else Tok.tileCellH,
            "next row top" to top + if (large) Tok.tileRowPitchLarge else Tok.tileRowPitch,
            "bottom" to if (navOn) h - Tok.navH else h,
        )
    }

    private fun hubLine(b: TokBoard.Board, h: Float, navOn: Boolean) = listOf(
        "content top" to b.page_contentTop,
        "status line baseline" to b.page_contentTop + Tok.qpHintDrop,
        "first collection row bottom" to b.page_contentTop + Tok.rowCollection,
        "bottom" to if (navOn) h - Tok.navH else h,
    )

    private fun hubStack(b: TokBoard.Board, h: Float, navOn: Boolean, stack: String) {
        order(h, "$stack nav $navOn", hub(b, h, navOn))
        order(h, "$stack line nav $navOn", hubLine(b, h, navOn))
        for (large in listOf(false, true)) order(h, "$stack grid nav $navOn large $large", hubGrid(b, h, navOn, large))
    }

    private fun volume(b: TokBoard.Board, h: Float) = listOf(
        "exit" to b.vol_exitY,
        "number" to b.vol_numberY,
        "plus" to b.vol_plusY,
        "ring row" to b.vol_ringY,
        "minus" to b.vol_minusY,
        "text" to b.vol_textY,
        "H" to h,
    )

    private fun saver(b: TokBoard.Board, h: Float) = listOf(
        "clock baseline" to Tok.saverClockBaseline,
        "H" to h,
        "fourth baseline" to b.saverFourthBaseline,
    )

    @Test
    fun everyHeightFrom320To560KeepsTheDesignOrders() {
        var h = 320f
        while (h <= 560f) {
            val b = TokFit.at(h, 4.5f)
            for (navOn in listOf(false, true)) {
                order(h, "now playing nav $navOn", nowPlaying(b, h, navOn))
                hubStack(b, h, navOn, "hub")
            }
            order(h, "volume overlay", volume(b, h))
            order(h, "saver", saver(b, h))
            h += 0.5f
        }
    }

    private fun drawer(b: TokBoard.Board, h: Float, navOn: Boolean) = listOf(
        "content top" to b.page_contentTop,
        "first row bottom" to b.page_contentTop + Tok.rowCollection,
        "bottom" to if (navOn) h - Tok.navH else h,
    )

    @Test
    fun everyPageHeightFrom320To900AtTheP2160PagePpuKeepsTheHubAndDrawerOrders() {
        val ppu = 2.9666936f
        var h = 320f
        while (h <= 900f) {
            val b = TokFit.at(h, ppu)
            for (navOn in listOf(false, true)) {
                hubStack(b, h, navOn, "page hub")
                order(h, "drawer nav $navOn", drawer(b, h, navOn))
            }
            h += 0.5f
        }
    }
}
