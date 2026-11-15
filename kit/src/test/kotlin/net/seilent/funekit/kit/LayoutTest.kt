package net.seilent.funekit.kit

import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kept
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LayoutTest {
    private val none = Insets(0, 0, 0, 0)

    private fun window(w: Int, h: Int, nav: Int = 0) = Window(w, h, 28, none, Insets(0, 0, 0, nav), none, none, none)

    private val a105 = scale(window(720, 1280), Nav.None, Surface.NowPlaying, false)
    private val r1 = scale(window(480, 800), Nav.None, Surface.Page, false)
    private val a105Page = scale(window(720, 1280), Nav.None, Surface.Page, false)
    private val a105Nav = scale(window(720, 1280, nav = 108), Nav.Buttons, Surface.NowPlaying, false)
    private val p2160 = Window(1080, 2160, 34, none, none, none, none, none, Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160))
    private val p2160Page = scale(p2160, Nav.None, Surface.Page, false)
    private val p2160Np = scale(p2160, Nav.None, Surface.NowPlaying, false)

    private fun px(what: String, want: Double, got: Int) = assertTrue("$what: want $want px got $got", abs(want - got) <= 1)

    @Test
    fun nowPlayingArtTitleAndRingsOnTheA105() {
        val art = Layout.npArt(a105)
        px("art height", 152.7 * 3, art.h)
        px("art top", 140.5 * 3, art.y0)
        assertEquals(332.7f, Layout.npTitleBaseline(a105), 0.01f)
        assertEquals(1280 / 3f - 28.2f, Layout.npRingCy(a105), 0.01f)
        assertEquals(387.82f, Layout.npRingCy(a105Nav), 0.01f)
    }

    @Test
    fun hubContentTopAndVisibleRows() {
        assertEquals(120f, Layout.contentTop(r1), 0.001f)
        assertEquals(120f, Layout.contentTop(a105Page), 0.001f)
        assertEquals(7f, Layout.visibleRows(r1), 0.01f)
        assertEquals(7.67f, Layout.visibleRows(a105Page), 0.01f)
    }

    @Test
    fun homeFirstBaseline() {
        assertEquals(100.6f, Layout.homeFirstBaseline(), 0.001f)
    }

    @Test
    fun rowPitchAndStatusBandOnTheA105() {
        px("collection pitch", 55.0 * 3, Layout.row(a105Page, 0, collection = true, bold = false, countLine = false, twoLine = false, ring = true).pitch)
        px("row pitch", 40.0 * 3, Layout.row(a105Page, 0, collection = false, bold = false, countLine = false, twoLine = true, ring = false).pitch)
        px("collection title x", 55.2 * 3, Layout.row(a105Page, 0, collection = true, bold = false, countLine = false, twoLine = false, ring = true).titleX)
        val st = statusLine(a105Page)
        px("status top", 10.0 * 3, st.top)
        px("status height", 12.0 * 3, st.size)
        px("status right", (240 - 16) * 3.0, st.right)
        px("status gap", 8.0 * 3, st.gap)
    }

    @Test
    fun nowPlayingStatusBandOnTheA105() {
        val st = statusLine(a105, npStatus(a105))
        px("np status top", 5.9 * 3, st.top)
        px("np status height", 11.5 * 3, st.size)
        px("np status right", (240 - 13.7) * 3, st.right)
        px("np status gap", 7.1 * 3, st.gap)
        px("np status text", 11.3 * 3, st.textPx)
    }

    private fun ceilEm(units: Int, px: Int) = (units * px + 2047) / 2048

    @Test
    fun rowBaselinesOnTheA105() {
        val inset = 4 * 3
        val primary = 14 * 3
        val secondary = 11 * 3
        val two = Layout.row(a105Page, 0, collection = false, bold = false, countLine = false, twoLine = true, ring = false)
        assertEquals(inset + ceilEm(2027, primary), two.title)
        assertEquals(40 * 3 - inset - ceilEm(431, secondary), two.sub)
        val one = Layout.row(a105Page, 0, collection = false, bold = false, countLine = false, twoLine = false, ring = false)
        assertEquals((40 * 3 - ceilEm(2027, primary) - ceilEm(431, primary)) / 2 + ceilEm(2027, primary), one.title)
        val collection = Layout.row(a105Page, 0, collection = true, bold = false, countLine = false, twoLine = false, ring = true)
        assertEquals((29 * 3 + Math.round(0.70f * 15.7f * 3) + 1) / 2, collection.title)
        assertEquals(collection.title + 8 + Math.round(0.70f * 11 * 3), collection.sub)
        val song = Layout.row(a105Page, 0, collection = false, bold = false, countLine = false, twoLine = true, ring = false, number64 = 0, song = true)
        assertEquals(160, song.pitch)
        assertEquals((46 * 3 - Math.round(0.70f * 18.5f * 3) - 44) / 2 + Math.round(0.70f * 18.5f * 3), song.title)
        assertEquals(song.title + 44, song.sub)
        val count = Layout.row(a105Page, 120, collection = false, bold = false, countLine = true, twoLine = true, ring = false)
        assertEquals(120 + 40 * 3 - inset - ceilEm(431, 11 * 3), count.sub)
        val bold = Layout.row(a105Page, 0, collection = false, bold = true, countLine = false, twoLine = true, ring = false)
        assertEquals(inset + ceilEm(2027, 14 * 3), bold.title)
    }

    @Test
    fun theStatusRightEdgeFollowsThePageWidthAndNowPlayingKeeps240() {
        assertEquals(364.04f, p2160Page.w, 0.005f)
        assertEquals(1033, statusLine(p2160Page).right)
        assertEquals(672, statusLine(a105Page).right)
        assertEquals(662, statusLine(scale(window(720, 1280), Nav.None, Surface.Page, true)).right)
        assertEquals(240f, p2160Np.w, 0.0001f)
        assertEquals(1018, statusLine(p2160Np, npStatus(p2160Np)).right)
        assertEquals(1018, statusLine(p2160Page.kept(), npStatus(p2160Page.kept())).right)
        val largeKept = scale(p2160, Nav.None, Surface.Page, true).kept()
        assertTrue(largeKept.large)
        assertEquals(1018, statusLine(largeKept, npStatus(largeKept)).right)
    }
}
