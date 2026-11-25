package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fxU
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.np.NP_RINGS
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.posture.Fold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NpSplitTest {
    private val none = Insets(0, 0, 0, 0)
    private val p = 3.9375f

    private fun window(bar: Int = 0) = Window(1080, 2520, 35, none, Insets(0, 0, 0, bar), none, none, none, null)

    private fun split(bar: Int = 0, fold: Fold = Fold(1260, 1260)): NpSplit? =
        NpSplit.of(window(bar), if (bar > 0) Nav.Buttons else Nav.None, false, fold, 0)

    private fun pt(ux: Float, uy: Float, h: NpHalf): Pair<Float, Float> = h.x + ux * h.g.p to h.y - h.dy + uy * h.g.p

    @Test
    fun eachHalfIsTheV1PageAtItsOwnScale() {
        val s = split()!!
        for (h in s.halves) {
            assertEquals(p, h.g.p)
            assertEquals(945, h.g.s.widthPx)
            assertEquals(68, h.x)
            assertEquals(240f, h.g.s.w)
            assertEquals(320f, h.g.s.h)
        }
        assertEquals(0, s.upper.y)
        assertEquals(1260, s.lower.y)
        assertEquals(0, s.lower.dy)
        assertEquals(828, px(s.upper.to, p))
        assertEquals(828, px(s.lower.from, p))
    }

    @Test
    fun theArtIsInTheUpperHalfAndTheRingRowInTheLower() {
        val s = split()!!
        assertTrue(NpPart.Art in s.upper.parts)
        assertTrue(NpPart.Status in s.upper.parts)
        assertFalse(NpPart.Rings in s.upper.parts)
        assertEquals(setOf(NpPart.Times, NpPart.Title, NpPart.Rings), s.lower.parts)
    }

    @Test
    fun theHitsLandInTheirHalfsCoordinates() {
        val s = split()!!
        val (bx, by) = pt(20f, 20f, s.upper)
        assertSame(s.upper, s.halves.at(bx, by))
        assertTrue(s.upper.g.backHit(s.upper.ux(bx), s.upper.uy(by)))
        val (ax, ay) = pt(60f, 160f, s.upper)
        assertSame(s.upper, s.halves.at(ax, ay))
        assertTrue(s.upper.g.artHit(s.upper.ux(ax), s.upper.uy(ay)))
        val (sx, sy) = pt(60f, 220f, s.lower)
        assertSame(s.lower, s.halves.at(sx, sy))
        assertTrue(s.lower.g.seekHit(s.lower.ux(sx), s.lower.uy(sy)))
        val (rx, ry) = pt(Tok.npRingCx1, s.lower.g.s.board.npRingY, s.lower)
        assertSame(s.lower, s.halves.at(rx, ry))
        assertEquals(1, s.lower.g.ringHit(s.lower.ux(rx), s.lower.uy(ry), NP_RINGS))
        assertNull(s.halves.at(500f, 1100f))
        assertNull(s.halves.at(20f, 1500f))
    }

    @Test
    fun theFoldBandTakesNoHit() {
        val s = split(fold = Fold(1250, 1270))!!
        assertNull(s.halves.at(500f, 1260f))
        assertEquals(1270, s.lower.y)
    }

    @Test
    fun aBarShiftsTheWholeLowerBandUpByItsHeight() {
        val s = split(132)!!
        val l = s.lower
        val g = l.g
        assertFalse(g.s.navOn)
        assertEquals(132, l.dy)
        assertEquals(698, g.bar.y0 - l.dy)
        assertEquals(759, g.timeBase - l.dy)
        assertEquals(851, g.titleY - l.dy)
        assertEquals(1, g.nextLines)
        assertEquals(911, g.nextBase(0) - l.dy)
        assertEquals(1017, g.ringCy - l.dy)
        assertEquals(958.5f, g.ringCy - l.dy - g.ringD / 2f)
        assertEquals(1128, l.clip)
        val flat = split()!!.lower.g
        assertEquals(flat.ringCy - flat.ringD / 2f - flat.nextBase(0), g.ringCy - g.ringD / 2f - g.nextBase(0))
        assertTrue(g.ringCy - l.dy - g.ringD / 2f > g.titleY - l.dy)
    }

    @Test
    fun theBandsHitsMoveWithIt() {
        val s = split(132)!!
        val l = s.lower
        val (sx, sy) = pt(60f, 220f, l)
        assertEquals(1260 - 132 + 220f * p, sy)
        assertSame(l, s.halves.at(sx, sy))
        assertTrue(l.g.seekHit(l.ux(sx), l.uy(sy)))
        val (tx, ty) = pt(60f, 245f, l)
        assertTrue(l.g.titleHit(l.ux(tx), l.uy(ty)))
        val ry = 1260f + 1017f
        val rx = 68 + Tok.npRingCx0 * p
        assertSame(l, s.halves.at(rx, ry))
        assertEquals(0, l.g.ringHit(l.ux(rx), l.uy(ry), NP_RINGS))
        assertNull(s.halves.at(rx, 2520f - 66))
    }

    @Test
    fun theUpperHalfIsUnchangedByTheBar() {
        val a = split()!!.upper
        val b = split(132)!!.upper
        assertEquals(a.x, b.x)
        assertEquals(a.dy, b.dy)
        assertEquals(a.to, b.to)
        assertEquals(a.g.artY, b.g.artY)
        assertEquals(a.g.artSide, b.g.artSide)
        assertEquals(a.g.backCy, b.g.backCy)
        assertEquals(a.g.statusClip, b.g.statusClip)
    }

    @Test
    fun theRingsAloneAboveTheBarTakeAboveBarWithNoShift() {
        val low = NpLayout(scale(Window(1080, 1260, 35, none, Insets(0, 0, 0, 132), none, none, none, null), Nav.Buttons, Surface.NowPlaying, false))
        assertTrue(low.s.navOn)
        assertEquals(px(low.s.board.npRingY + low.s.stdContentBottom - low.s.stdH, p), low.ringCy)
        val band = split(132)!!.lower
        assertTrue(abs(low.ringCy - (band.g.ringCy - band.dy)) <= 1)
    }

    @Test
    fun aBarTallerThanTheBandsEmptySpaceTakesTheFlatPage() {
        val s = split()!!
        assertNotNull(split(s.lower.g.bar.y0))
        assertNull(split(s.lower.g.bar.y0 + 1))
    }

    @Test
    fun aFoldAtTheWindowEdgeGivesNoSplit() {
        assertNull(split(fold = Fold(0, 0)))
        assertNull(split(fold = Fold(2520, 2520)))
    }

    private fun faded(): NowPlayingModel {
        val m = NowPlayingModel()
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000, album = "Album"))), 0)
        m.resetFocus(0)
        m.stepTo(5000)
        assertTrue(m.idleAlpha(5380) < 1f)
        return m
    }

    @Test
    fun aTouchOutsideBothHalvesWakesTheFadedControls() {
        val s = split(fold = Fold(1250, 1270))!!
        for (y in listOf(1260f, 1000f)) {
            val m = faded()
            val t = NpTouch(m, s.halves, {}, {})
            t.press()
            t.at(540f, y, 6000)
            assertEquals("y $y", 1f, m.idleAlpha(6380), 0.001f)
        }
        val m = faded()
        val t = NpTouch(m, s.halves, {}, {})
        t.press()
        t.at(20f, 300f, 6000)
        assertEquals(1f, m.idleAlpha(6380), 0.001f)
    }

    @Test
    fun inLyricsModeTheArtHalfTakesTheLyricsAndTheirTaps() {
        val s = split()!!
        val h = s.halves.holding(NpPart.Art)
        assertSame(s.upper, h)
        val plain = lyricPlain(h.g, LyricTable.of(h.g.s))
        val e = lyricTarget(h, plain)
        assertEquals(68 + px(Tok.edge, p), e.x0)
        assertEquals(68 + px(Tok.canvasW - Tok.edge, p), e.x1)
        assertEquals(plain.y0, e.y0)
        assertTrue(e.y1 > e.y0 && e.y1 <= h.y + h.clip)
        val m = NowPlayingModel()
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000, album = "Album"))), 0)
        m.lyricsView = true
        m.lyrics.toggle(0)
        m.lyrics.land()
        val acts = mutableListOf<NpAct>()
        val t = NpTouch(m, s.halves, { acts += it }, {})
        val (ax, ay) = pt(60f, 160f, s.upper)
        t.press()
        t.at(ax, ay, 10)
        t.tap(20)
        assertEquals(listOf(NpAct.LyricsSheet), acts)
    }

    @Test
    fun inLyricsModeAHoldInTheHalfWithNoColumnAsks() {
        val s = split()!!
        val m = NowPlayingModel()
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000, album = "Album"))), 0)
        m.lyricsView = true
        m.lyrics.toggle(0)
        m.lyrics.land()
        var asks = 0
        val t = NpTouch(m, s.halves, {}, {}, artHold = {
            asks++
            true
        })
        val lg = s.lower.g
        val lt = LyricTable.of(lg.s)
        val end = fxU(lt.end(lt.ap))
        val y = (lg.s.board.npSeekBandBottom + minOf(lg.titleBottomU, end)) / 2
        assertTrue("premise: the point is in the column span, below the seek band, above the rings", y >= fxU(lt.top) && y < end && y >= lg.s.board.npSeekBandBottom && y < lg.titleBottomU)
        val (lx, ly) = pt(100f, y, s.lower)
        assertSame("premise: the lower half takes the down", s.lower, s.halves.at(lx, ly))
        t.press()
        t.at(lx, ly, 10)
        assertTrue("the lower half draws no column, so the hold is backdrop", t.hold())
        assertEquals(1, asks)
        t.cancel(20)
        val ug = s.upper.g
        val (ux, uy) = pt(100f, (ug.artY + ug.artSide / 2f) / ug.p, s.upper)
        assertSame("premise: the upper half takes the down", s.upper, s.halves.at(ux, uy))
        t.press()
        t.at(ux, uy, 30)
        assertFalse(t.hold())
        assertEquals("the upper half's column asks nothing", 1, asks)
    }

    @Test
    fun theFlatHalfDrawsAndHitsAsThePage() {
        val g = NpLayout(scale(window(), Nav.None, Surface.NowPlaying, false))
        val f = npFlat(g)
        assertEquals(NP_PARTS, f.parts)
        assertSame(f, listOf(f).at(500f, 1500f))
        assertEquals(500f / g.p, f.ux(500f))
        assertEquals(1500f / g.p, f.uy(1500f))
    }
}
