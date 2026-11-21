package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.Case
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fx
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.scale
import java.text.Bidi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRowsTest {
    private val one = fxInt(1)
    private val plain = Roles.npAlbum.let { Role(it.size, it.weight, Case.Mixed, it.alpha, it.tracking) }
    private val t = LyricTable(
        Roles.npTitle, Roles.npNext, plain, fx(Tok.npLyricPitch), fx(Tok.npNextPitch),
        fx(Tok.npAlbumBaseline) - fx(Tok.npTextTop), fx(Tok.npTextTop), fx(TokBoard.v1.npLyricBottom),
    )
    private val lines = listOf("one", "two", "three", "four", "five", "six", "seven", "eight")

    private class Rows(val big: Map<Int, Int> = emptyMap(), val small: Map<Int, Int> = emptyMap(), val inkTop: Int = 0, val inkBottom: Int = 0) : LyricInk {
        override fun rows(line: Int, big: Boolean): Int = (if (big) this.big else small)[line] ?: 1

        override fun top(line: Int, big: Boolean): Int = inkTop

        override fun bottom(line: Int, big: Boolean, row: Int): Int = inkBottom
    }

    @Test
    fun theCurrentLineSitsUnderThePreviousOneAndTheNextLinesFillTheRoom() {
        val b = LyricRows.blocks(t, 2, lines, "", Rows())
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), b.list.map { it.line })
        assertEquals(LyricBlock(1, false, t.top, 1, t.np, fx(Tok.alpha_low)), b.list[0])
        assertEquals(LyricBlock(2, true, t.top + t.np, 1, t.cp, fx(Roles.npTitle.alpha)), b.list[1])
        assertEquals(t.top + t.np + t.cp, b.list[2].top)
        assertEquals(fx(Roles.npNext.alpha), b.list[2].alpha)
        assertEquals(t.top + t.np + t.cp + 5 * t.np, b.vis)
    }

    @Test
    fun thePreviousLineGivesWayWhenItAndOneNextLineDoNotFit() {
        val fits = LyricRows.blocks(t, 2, lines, "", Rows(big = mapOf(2 to 4)))
        assertEquals(1, fits.list.first().line)
        val wide = LyricRows.blocks(t, 2, lines, "", Rows(big = mapOf(2 to 4), small = mapOf(1 to 2)))
        assertEquals(2, wide.list.first().line)
        assertEquals(t.top, wide.list.first().top)
    }

    @Test
    fun aCurrentLineTallerThanTheRoomKeepsTheRowsThatFit() {
        val b = LyricRows.blocks(t, 0, lines, "", Rows(big = mapOf(0 to 7)))
        assertEquals(LyricBlock(0, true, t.top, ((t.bottom - t.top) / t.cp).toInt(), t.cp, fx(Roles.npTitle.alpha)), b.list.single())
    }

    @Test
    fun withNoLyricLineTheTitleAndTheNextTitleShow() {
        val b = LyricRows.blocks(t, LYRIC_NONE, lines, "Next", Rows())
        assertEquals(listOf(LYRIC_TITLE to true, LYRIC_TITLE to false), b.list.map { it.line to it.big })
        assertEquals(t.top + t.cp, b.list[1].top)
        assertEquals(1, LyricRows.blocks(t, LYRIC_NONE, null, "", Rows()).list.size)
    }

    @Test
    fun beforeTheFirstLineOnlyTheComingLinesShowAndEmptyLinesAreSkipped() {
        val b = LyricRows.blocks(t, LYRIC_TITLE, listOf("one", "", "three"), "", Rows())
        assertEquals(listOf(0, 2), b.list.map { it.line })
        assertTrue(b.list.none { it.big })
    }

    @Test
    fun aRestingColumnDrawsEachBlockOnceAtItsAlpha() {
        val c = LyricRows.column(t, 2, 2, one, one, lines, "", Rows())
        assertEquals(LyricRows.blocks(t, 2, lines, "", Rows()).list.map { it.top }, c.draws.map { it.top })
        assertTrue(c.draws.all { it.old == null && it.new?.let { n -> n.alpha == n.block.alpha } == true })
    }

    @Test
    fun aSlideMovesTheSharedLinesAndSwapsTheLineThatChangesSize() {
        val start = LyricRows.column(t, 2, 3, 0L, one, lines, "", Rows())
        val end = LyricRows.column(t, 2, 3, one, one, lines, "", Rows())
        val leaving = start.draws.single { it.line == 1 }
        assertEquals(t.np, leaving.h)
        assertEquals(t.top, leaving.top)
        assertEquals(fx(Tok.alpha_low), leaving.new!!.alpha)
        val gone = end.draws.single { it.line == 1 }
        assertEquals(t.top - t.np, gone.top)
        assertEquals(0L, gone.new!!.alpha)
        val swap = start.draws.single { it.line == 2 }
        assertTrue(swap.change)
        assertEquals(t.top + t.np, swap.top)
        assertEquals(fx(Roles.npTitle.alpha), swap.old!!.alpha)
        assertEquals(0L, swap.new!!.alpha)
        val landed = end.draws.single { it.line == 2 }
        assertEquals(t.top, landed.top)
        assertEquals(0L, landed.old!!.alpha)
        assertEquals(fx(Tok.alpha_low), landed.new!!.alpha)
        assertEquals(t.top + t.np, end.draws.single { it.line == 3 }.top)
    }

    @Test
    fun linesPushedDownByTheFirstLineJumpAtHalfTheSlide() {
        val early = LyricRows.column(t, LYRIC_TITLE, 0, one / 4, one, lines, "", Rows())
        val late = LyricRows.column(t, LYRIC_TITLE, 0, one * 3 / 4, one, lines, "", Rows())
        val before = LyricRows.blocks(t, LYRIC_TITLE, lines, "", Rows()).list.single { it.line == 1 }
        val after = LyricRows.blocks(t, 0, lines, "", Rows()).list.single { it.line == 1 }
        assertEquals(before.top, early.draws.single { it.line == 1 }.top)
        assertEquals(after.top, late.draws.single { it.line == 1 }.top)
        assertEquals(0L, early.draws.single { it.line == 1 }.new!!.alpha)
        assertTrue(early.draws.single { it.line == 0 }.change)
        assertNull(LyricRows.column(t, 0, 0, one, one, lines, "", Rows()).draws.single { it.line == 0 }.old)
    }

    @Test
    fun aRowClipGrowsToItsInkButNeverPastTheLyricBottom() {
        val ppu = fx(3f)
        val c = LyricRows.column(t, 0, 0, one, ppu, lines, "", Rows(big = mapOf(0 to 5), inkBottom = 1000))
        val d = c.draws.single { it.line == 0 }
        assertEquals(lyricEdge(t.bottom, ppu), d.y1)
        val low = LyricRows.column(t, 0, 0, one, ppu, lines, "", Rows(inkTop = 1000))
        assertEquals(lyricEdge(t.top, ppu), low.draws.first().y0)
    }

    private val none = Insets(0, 0, 0, 0)
    private val g = NpLayout(scale(Window(720, 960, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false))

    private fun word(width: Float, inkRight: Float = width, inkBottom: Float = 0f) = Word("w", emptyList(), width, 0f, inkRight, -1f, inkBottom)

    private fun lerp(a: Long, b: Long, p: Long) = a + fxMul(b - a, p)

    @Test
    fun theEnteringLineStopsAtTheLineBelowIt() {
        val p = one * 3 / 4
        val d = LyricRows.column(t, 2, 3, p, one, lines, "", Rows()).draws.single { it.line == 3 }
        val a = LyricRows.blocks(t, 2, lines, "", Rows()).list
        val b = LyricRows.blocks(t, 3, lines, "", Rows()).list
        val top = lerp(a.single { it.line == 3 }.top, b.single { it.line == 3 }.top, p)
        val lim = lerp(a.single { it.line == 4 }.top, b.single { it.line == 4 }.top, p)
        assertEquals(top, d.top)
        assertEquals(lim - top, d.h)
        assertTrue(d.h < t.cp)
    }

    @Test
    fun largeTakesTheLargeLyricTable() {
        val s = scale(Window(720, 960, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, true)
        val l = LyricTable.of(s)
        assertEquals(Tok.large_type_npTitle_size, l.big.size, 0f)
        assertEquals(Tok.large_type_npNext_size, l.next.size, 0f)
        assertEquals(Tok.large_type_npAlbum_size, l.plain.size, 0f)
        assertEquals(Case.Mixed, l.plain.case)
        assertEquals(fx(s.board.large_npLyricPitch), l.cp)
        assertEquals(fx(s.board.large_npNextPitch), l.np)
        assertEquals(fx(s.board.large_npAlbumPitch), l.ap)
        assertEquals(fx(s.board.npLyricBottom), l.bottom)
        val std = LyricTable.of(g.s)
        assertEquals(fx(Tok.npAlbumBaseline) - fx(Tok.npTextTop), std.ap)
    }

    @Test
    fun rightToLeftLyricLinesEndAtTheRightEdgeAndTitlesStayLeft() {
        val c = LyricRows.column(t, 0, 0, one, fx(g.p), lines, "Next", Rows())
        val rows = lyricColumnRows(g, t, c, { listOf(word(30f)) }, rtl = true)
        val right = pen64(Tok.canvasW - Tok.edge, g.p) - pen64(30f)
        assertTrue(rows.all { it.x64 == right })
        val titles = LyricRows.column(t, LYRIC_NONE, LYRIC_NONE, one, fx(g.p), lines, "Next", Rows())
        val left = lyricColumnRows(g, t, titles, { listOf(word(30f)) }, rtl = true)
        assertEquals(2, left.size)
        assertTrue(left.all { it.x64 == pen64(Tok.edge, g.p) })
    }

    @Test
    fun aRowWhoseInkOverhangsGrowsItsClipUpToTheScrollbar() {
        val c = LyricRows.column(t, 0, 0, one, fx(g.p), lines, "", Rows())
        val x1 = px(Tok.canvasW - Tok.edge, g.p)
        val wide = lyricColumnRows(g, t, c, { listOf(word(30f, inkRight = 40f)) }, rtl = false).first()
        assertEquals(x1 + 10, wide.x1)
        val far = lyricColumnRows(g, t, c, { listOf(word(30f, inkRight = 1000f)) }, rtl = false).first()
        assertEquals(px(Tok.scrollbar_x, g.p), far.x1)
    }

    @Test
    fun theLastPlainRowAtTheEndMayDropBelowTheViewToTheLyricBottom() {
        val rows = listOf(word(30f), word(30f, inkBottom = 1000f))
        val plain = lyricPlain(g, t)
        val end = lyricPlainRows(g, t, rows, 0, end = true, rtl = false)
        assertEquals(px64(t.bottom, fx(g.p)), end.last().y1)
        assertEquals(plain.y1, end.first().y1)
        assertEquals(plain.y1, lyricPlainRows(g, t, rows, 0, end = false, rtl = false).last().y1)
        assertTrue(lyricPlainRows(g, t, rows, 0, end = true, rtl = true).all { it.x64 == pen64(Tok.canvasW - Tok.edge, g.p) - pen64(30f) })
    }

    @Test
    fun theLyricDirectionSkipsTimeTagsAndIsolatedRuns() {
        val hebrew = "\u05e9\u05dc\u05d5\u05dd"
        assertEquals(Bidi.DIRECTION_RIGHT_TO_LEFT, lyricDir(listOf("123", "[00:01.00] $hebrew")))
        assertEquals(Bidi.DIRECTION_LEFT_TO_RIGHT, lyricLineDir("\u2067$hebrew\u2069 hi", Bidi.DIRECTION_RIGHT_TO_LEFT))
        assertEquals(Bidi.DIRECTION_RIGHT_TO_LEFT, lyricLineDir("\u2067abc\u2069 123", Bidi.DIRECTION_RIGHT_TO_LEFT))
    }
}
