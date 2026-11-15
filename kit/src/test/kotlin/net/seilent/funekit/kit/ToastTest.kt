package net.seilent.funekit.kit

import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToastTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 28, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val p2160 = scale(Window(1080, 2160, 28, none, none, none, none, none, Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)), Nav.None, Surface.Page, false)

    @Test
    fun atTheTopItDropsFortyThreeUnitsOver100HoldsThenLeavesOnTheOutLegs() {
        val t = Toast()
        t.show(0, "artist", "title")
        assertEquals(0f, t.pos(0), 0f)
        t.frame(ToastPlace.Top, 1280, 10)
        assertEquals(0.5f, t.pos(10 + 50), 0.001f)
        assertEquals(1f, t.pos(10 + 100), 0f)
        assertEquals(-129, t.stripY(a105, 0f))
        assertEquals(0, t.stripY(a105, 1f))
        val end = 10 + 100 + 3500L
        assertTrue(t.visible(end - 1))
        assertEquals(1f, t.pos(end + 8), 0f)
        assertEquals(0.5f, t.pos(end + 8 + 68), 0.001f)
        assertEquals(0f, t.pos(end + 8 + 136), 0f)
        assertEquals(1f, t.fade(end + 2), 0f)
        assertEquals(1 - Curves.easeOut.at(0.5f), t.fade(end + 2 + 90), 0.01f)
        assertTrue(t.visible(end + 182))
        assertFalse(t.visible(end + 183))
    }

    @Test
    fun atTheBottomItRisesOver100AboveTheSurface() {
        val t = Toast()
        t.show(0, "artist", "title")
        t.frame(ToastPlace.Bottom, 1280 - 108, 0)
        assertEquals(1f, t.pos(100), 0f)
        assertEquals(1280 - 108 - 120 + 129, t.stripY(a105, 0f))
        assertEquals(1280 - 108 - 120, t.stripY(a105, 1f))
        assertEquals(listOf(0, 1280 - 108 - 120, 720, 1280 - 108), t.rect(a105).let { listOf(it.x0, it.y0, it.x1, it.y1) })
    }

    @Test
    fun aShowDuringTheHoldSwapsTheTextWithADipAndRestartsTheHold() {
        val t = Toast()
        t.show(0, "one", "first")
        t.frame(ToastPlace.Top, 1280, 0)
        t.show(1000, "two", "second")
        assertEquals(0.2f, t.textAlpha(1200), 0.001f)
        assertEquals("one", t.artist)
        t.frame(ToastPlace.Top, 1280, 1200)
        assertEquals("two", t.artist)
        assertEquals(1f, t.textAlpha(1400), 0f)
        assertTrue(t.visible(1000 + 3500 - 1))
        assertFalse(t.visible(1000 + 3500 + 250))
    }

    @Test
    fun aShowWhileGoingOutRunsInFromWhereItIs() {
        val t = Toast()
        t.show(0, "one", "first")
        t.frame(ToastPlace.Top, 1280, 0)
        val at = 100 + 3500 + 8 + 68L
        val e = t.pos(at)
        val f = t.fade(at)
        assertEquals(0.5f, e, 0.001f)
        t.show(at, "two", "second")
        assertEquals(e, t.pos(at), 0.002f)
        assertEquals(f, t.fade(at), 0.002f)
        assertEquals(1f, t.pos(at + 50), 0.002f)
        assertTrue(t.placed)
    }

    @Test
    fun theStripIsBlackAtEightTenthsAndItsTextClipsAtTheEdges() {
        val t = Toast()
        t.show(0, "one", "first", track = true)
        t.frame(ToastPlace.Top, 1280, 0)
        val l = t.look(a105, 400)!!
        assertEquals(0.8f, l.scrim, 0.001f)
        assertEquals(listOf(48, 0, 672, 120), listOf(l.clip.x0, l.clip.y0, l.clip.x1, l.clip.y1))
        t.down(400)
        assertTrue(t.tap(450))
        assertFalse(t.tap(460))
        t.hide()
        assertNull(t.look(a105, 500))
    }

    @Test
    fun aTrackToastHitsAsDrawnSoAShrinkingStripLeavesThePage() {
        val t = Toast()
        t.show(0, "one", "first", track = true)
        t.frame(ToastPlace.Top, 1280, 0)
        assertEquals(listOf(0, 0, 720, 120), t.hitRect(a105, 400)!!.let { listOf(it.x0, it.y0, it.x1, it.y1) })
        val out = 100 + 3500 + 8 + 68L
        val travel = Math.round(43 * 3 * (1 - t.pos(out)))
        assertEquals(listOf(-travel, 120 - travel), t.hitRect(a105, out)!!.let { listOf(it.y0, it.y1) })
        val plain = Toast().also {
            it.show(0, "one", "first")
            it.frame(ToastPlace.Top, 1280, 0)
        }
        assertNull(plain.hitRect(a105, 400))
    }

    @Test
    fun aBottomTrackToastHitsAboveTheSurface() {
        val t = Toast()
        t.show(0, "one", "first", track = true)
        t.frame(ToastPlace.Bottom, 1172, 0)
        assertEquals(listOf(1172 - 120, 1172), t.hitRect(a105, 200)!!.let { listOf(it.y0, it.y1) })
    }

    @Test
    fun aSheetOrAHeldGuardPutsTheToastAtTheTopAndEveryOtherOverlayAtTheBottom() {
        assertEquals(ToastPlace.Top, toastPlace(OverKind.Sheet, closing = false, nowPlaying = false))
        assertEquals(ToastPlace.Bottom, toastPlace(OverKind.Sheet, closing = true, nowPlaying = false))
        assertEquals(ToastPlace.Bottom, toastPlace(OverKind.Dlg, closing = false, nowPlaying = false))
        assertEquals(ToastPlace.Top, toastPlace(OverKind.Dlg, closing = false, nowPlaying = false, holdUp = true))
        assertEquals(ToastPlace.Bottom, toastPlace(OverKind.Jump, closing = false, nowPlaying = false))
        assertEquals(ToastPlace.Bottom, toastPlace(null, closing = false, nowPlaying = false))
        assertEquals(ToastPlace.Top, toastPlace(null, closing = false, nowPlaying = true))
        assertEquals(ToastPlace.Top, toastPlace(OverKind.Jump, closing = false, nowPlaying = true))
    }

    @Test
    fun theToastDrawsOverASheetAndAHeldGuardAndNeverOverTheJumpGrid() {
        assertTrue(toastOver(null))
        assertTrue(toastOver(OverKind.Sheet))
        assertFalse(toastOver(OverKind.Dlg))
        assertTrue(toastOver(OverKind.Dlg, holdUp = true))
        assertFalse(toastOver(OverKind.Jump))
    }

    @Test
    fun aSheetOpeningOverARisingBottomToastTakesItToTheTopFromWhereItRose() {
        val t = Toast()
        t.show(0, "artist", "title")
        t.frame(ToastPlace.Bottom, 1280, 0)
        assertTrue(t.bottom)
        t.opened(OverKind.Jump, nowPlaying = false, now = 50)
        assertTrue(t.bottom)
        t.opened(OverKind.Sheet, nowPlaying = false, now = 50)
        assertFalse(t.bottom)
        assertEquals(ToastPlace.Top, t.place)
        assertEquals(0.5f, t.pos(50), 0.001f)
        assertEquals(0.7f, t.pos(50 + 20), 0.001f)
        assertEquals(1f, t.pos(50 + 50), 0f)
    }

    @Test
    fun theBottomToastSitsOnTheNavBandWhenThePageHasIt() {
        val bars = Insets(0, 0, 0, 108)
        val nav = scale(Window(720, 1280, 35, none, bars, none, none, none), Nav.Buttons, Surface.Page, false)
        assertEquals(1172, toastSurface(nav, navHere = true))
        assertEquals(1280, toastSurface(nav, navHere = false))
        val r1 = scale(Window(480, 800, 35, none, Insets(0, 0, 0, 72), none, none, none), Nav.Buttons, Surface.Page, false)
        assertEquals(728, toastSurface(r1, navHere = true))
        val b = navBand(nav)
        assertEquals(listOf(0, 1172, 720, 1280), listOf(b.x0, b.y0, b.x1, b.y1))
    }

    @Test
    fun onALargePageTheBandTheToastAndTheContentBottomStayAtAndroidsBarTop() {
        val large = scale(Window(720, 1280, 35, none, Insets(0, 0, 0, 108), none, none, none), Nav.Buttons, Surface.Page, true)
        assertEquals(1172, toastSurface(large, navHere = true))
        assertEquals(1280, toastSurface(large, navHere = false))
        val b = navBand(large)
        assertEquals(listOf(0, 1172, 720, 1280), listOf(b.x0, b.y0, b.x1, b.y1))
        assertEquals(1172, px(large.stdContentBottom, large.stdPpu))
        val r1 = scale(Window(480, 800, 35, none, Insets(0, 0, 0, 72), none, none, none), Nav.Buttons, Surface.Page, true)
        assertEquals(728, toastSurface(r1, navHere = true))
        assertEquals(728, navBand(r1).y0)
        assertEquals(728, px(r1.stdContentBottom, r1.stdPpu))
    }

    @Test
    fun aBarShorterThanNavHPutsTheBandTheToastAndTheContentBottomAtItsTopAtBothSizes() {
        val p2160 = Window(1080, 2160, 34, none, Insets(0, 0, 0, 132), Insets(0, 0, 0, 132), none, none)
        val std = scale(p2160, Nav.Buttons, Surface.Page, false)
        assertEquals(2028, toastSurface(std, navHere = true))
        assertEquals(2028, px(std.stdContentBottom, std.stdPpu))
        assertEquals(2028, navBand(std).y0)
        val large = scale(p2160, Nav.Buttons, Surface.Page, true)
        assertEquals(2028, toastSurface(large, navHere = true))
        assertEquals(2160, toastSurface(large, navHere = false))
        val b = navBand(large)
        assertEquals(listOf(0, 2028, 1080, 2160), listOf(b.x0, b.y0, b.x1, b.y1))
        assertEquals(2028, px(large.stdContentBottom, large.stdPpu))
    }

    @Test
    fun onTheP2160PageTheStripSpansTheWindowAndItsTextClipsAtTheEdges() {
        assertEquals(364.04f, p2160.w, 0.005f)
        val t = Toast()
        t.show(0, "one", "first", track = true)
        t.frame(ToastPlace.Top, 2160, 0)
        val l = t.look(p2160, 400)!!
        assertEquals(listOf(47, 0, 1033, 119), listOf(l.clip.x0, l.clip.y0, l.clip.x1, l.clip.y1))
        assertEquals(listOf(0, 0, 1080, 119), t.rect(p2160).let { listOf(it.x0, it.y0, it.x1, it.y1) })
    }
}
