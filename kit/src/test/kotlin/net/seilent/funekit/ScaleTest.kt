package net.seilent.funekit

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.navBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScaleTest {
    private val none = Insets(0, 0, 0, 0)

    private fun window(
        w: Int,
        h: Int,
        sdk: Int = 35,
        status: Int = 0,
        nav: Insets = none,
        gestures: Insets = none,
        cutout: Insets = none,
        ime: Int = 0,
        dpi: Dpi? = null,
    ) = Window(w, h, sdk, Insets(0, status, 0, 0), nav, gestures, cutout, Insets(0, 0, 0, ime), dpi)

    private fun dpi(ppi: Float, w: Int, h: Int, stable: Int = 480, y: Float = ppi, sizes: Int = 1, logicalW: Int = w, logicalH: Int = h) =
        Dpi(ppi, y, stable, w, h, sizes, logicalW, logicalH)

    private val p2160Dpi = dpi(403.411f, 1080, 2160, stable = 440)
    private val p2160Gesture = window(1080, 2160, sdk = 34, status = 66, nav = Insets(0, 0, 0, 66), gestures = Insets(82, 0, 82, 0), dpi = p2160Dpi)
    private val p2160Buttons = window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132), dpi = p2160Dpi)

    private val boardGetters = TokBoard.Board::class.java.methods
        .filter { it.declaringClass == TokBoard.Board::class.java && it.parameterCount == 0 }

    private fun same(want: Scale, got: Scale, what: String) {
        assertEquals("$what ppu", want.ppu, got.ppu)
        assertEquals("$what stdPpu", want.stdPpu, got.stdPpu)
        assertEquals("$what w", want.w, got.w)
        assertEquals("$what h", want.h, got.h)
        assertEquals("$what stdH", want.stdH, got.stdH)
        assertEquals("$what left", want.left, got.left)
        assertEquals("$what widthPx", want.widthPx, got.widthPx)
        assertEquals("$what heightPx", want.heightPx, got.heightPx)
        assertEquals("$what nav", want.nav, got.nav)
        assertEquals("$what navOn", want.navOn, got.navOn)
        assertEquals("$what stdContentBottom", want.stdContentBottom, got.stdContentBottom)
        assertEquals("$what edgeBack", want.edgeBack, got.edgeBack)
        assertEquals("$what bottomSwipe", want.bottomSwipe, got.bottomSwipe)
        assertEquals("$what statusBar", want.statusBar, got.statusBar)
        assertEquals("$what stdStatusOverlap", want.stdStatusOverlap, got.stdStatusOverlap)
        assertEquals("$what stdNavOverlap", want.stdNavOverlap, got.stdNavOverlap)
        assertEquals("$what stdImeTop", want.stdImeTop, got.stdImeTop)
        assertEquals("$what large", want.large, got.large)
        assertEquals("$what touchPpu", want.touchPpu, got.touchPpu)
        for (g in boardGetters) assertEquals("$what board ${g.name}", g.invoke(want.board), g.invoke(got.board))
        val wf = want.fit
        val gf = got.fit
        if (wf == null || gf == null) assertTrue("$what fit", wf == null && gf == null) else same(wf, gf, "$what fit")
    }

    private val a105 = window(720, 1280, sdk = 28, status = 54, nav = Insets(0, 0, 0, 108))
    private val gesture = window(1080, 2160, sdk = 34, status = 66, nav = Insets(0, 0, 0, 66), gestures = Insets(82, 0, 82, 0))
    private val tall = window(1080, 2520, sdk = 37, status = 109, nav = Insets(0, 0, 0, 39), gestures = Insets(78, 0, 78, 0), cutout = Insets(0, 109, 0, 0))
    private val keys = window(720, 1280, sdk = 33)

    private fun page(w: Window, large: Boolean = false) = scale(w, navMode(w), Surface.Page, large)

    private fun near(want: Float, got: Float, tol: Float, what: String) =
        assertTrue("$what: want $want got $got", abs(want - got) <= tol)

    private fun row(w: Int, h: Int, ppu: Float, canvasH: Float, npArt: Float, npRingY: Float) {
        val s = page(window(w, h))
        val at = "${w}x$h"
        near(ppu, s.ppu, 0.0005f, "$at ppu")
        near(canvasH, s.stdH, 0.005f, "$at H")
        near(canvasH, s.h, 0.005f, "$at page H")
        near(canvasH, s.board.canvasH, 0.005f, "$at board H")
        near(npArt, s.board.npArt, 0.005f, "$at npArt")
        near(npRingY, s.board.npRingY, 0.005f, "$at npRingY")
    }

    @Test
    fun boardSizedWindowsTakeTheBoardLiterals() {
        row(240, 320, 1.0f, 320f, 91.5f, 291.8f)
        row(480, 640, 2.0f, 320f, 91.5f, 291.8f)
        row(480, 800, 2.0f, 400f, 149.4f, 371.8f)
        row(720, 1280, 3.0f, 426.67f, 152.7f, 398.47f)
        row(768, 1280, 3.2f, 400f, 149.4f, 371.8f)
        row(1080, 1920, 4.5f, 426.67f, 152.7f, 398.47f)
    }

    @Test
    fun tallerWindowsExtrapolateAlongTheR1ToA105Line() {
        val s = page(window(750, 1334))
        near(3.125f, s.ppu, 0.0005f, "ppu")
        near(426.88f, s.stdH, 0.005f, "H")
        near(0.21f, s.stdH - 426.67f, 0.005f, "past the A105")
        near(398.68f, s.board.npRingY, 0.005f, "npRingY at H - 28.2")
        for ((w, h, ppu) in listOf(Triple(640, 1280, 2.667f), Triple(720, 1440, 3.0f), Triple(1080, 2160, 4.5f))) {
            val t = page(window(w, h))
            near(ppu, t.ppu, 0.0005f, "${w}x$h ppu")
            near(480f, t.stdH, 0.005f, "${w}x$h H")
            near(451.8f, t.board.npRingY, 0.005f, "${w}x$h npRingY")
        }
        near(520f, page(window(1080, 2340)).stdH, 0.005f, "1080x2340 H")
        near(491.8f, page(window(1080, 2340)).board.npRingY, 0.005f, "1080x2340 npRingY")
        near(560f, page(window(1080, 2520)).stdH, 0.005f, "1080x2520 H")
        near(531.8f, page(window(1080, 2520)).board.npRingY, 0.005f, "1080x2520 npRingY")
    }

    @Test
    fun wideWindowsPillarboxTheV1Canvas() {
        val flip7 = page(window(948, 1048))
        near(3.275f, flip7.ppu, 0.0005f, "Flip7 cover ppu")
        near(320f, flip7.stdH, 0.005f, "Flip7 cover H")
        near(91.5f, flip7.board.npArt, 0.005f, "Flip7 cover npArt")
        assertEquals(786, flip7.widthPx)
        assertEquals(81, flip7.left)
        assertEquals(81, 948 - flip7.left - flip7.widthPx)
        val flip6 = page(window(720, 748))
        near(2.338f, flip6.ppu, 0.001f, "Flip6 cover ppu")
        near(320f, flip6.stdH, 0.005f, "Flip6 cover H")
        assertEquals(561, flip6.widthPx)
        assertTrue(abs(flip6.left - (720 - flip6.left - flip6.widthPx)) <= 1)
        val land = page(window(2160, 1080))
        near(3.375f, land.ppu, 0.0005f, "landscape ppu")
        near(320f, land.stdH, 0.005f, "landscape H")
        assertEquals(810, land.widthPx)
        assertEquals(675, land.left)
        val side = page(window(2160, 1080, nav = Insets(0, 0, 132, 0)))
        assertEquals(810, side.widthPx)
        assertEquals(609, side.left)
        assertEquals(side.left, 2160 - 132 - side.left - side.widthPx)
    }

    @Test
    fun largeKeepsTheStandardBoardOnALargerPpu() {
        for ((w, h, fx) in listOf(Triple(240, 320, 78644), Triple(480, 800, 157287), Triple(720, 1280, 235930))) {
            val std = page(window(w, h))
            val large = page(window(w, h), large = true)
            assertEquals(fx / 65536f, large.ppu)
            assertEquals(std.stdPpu, large.stdPpu)
            assertEquals(std.stdH, large.stdH)
            near(200f, large.w, 0.005f, "${w}x$h LARGE W")
            assertEquals(std.board.npArt, large.board.npArt)
            assertEquals(std.board.npRingY, large.board.npRingY)
        }
        val a = page(window(720, 1280), large = true)
        near(3.6f, a.ppu, 0.00001f, "A105 LARGE ppu")
        near(355.56f, a.h, 0.01f, "A105 LARGE canvas")
        near(426.67f, a.board.canvasH, 0.005f, "A105 LARGE board H")
        near(152.7f, a.board.npArt, 0.005f, "A105 LARGE npArt")
        near(266.67f, page(window(240, 320), large = true).h, 0.01f, "V1 LARGE canvas")
        near(333.33f, page(window(480, 800), large = true).h, 0.01f, "R1 LARGE canvas")
    }

    @Test
    fun theNavigationModeComesFromTheBarsIgnoringVisibility() {
        assertEquals(Nav.Buttons, navMode(a105))
        assertEquals(Nav.Gesture, navMode(gesture))
        assertEquals(Nav.Gesture, navMode(tall))
        assertEquals(Nav.None, navMode(keys))
        assertEquals(Nav.Buttons, navMode(window(720, 1280, sdk = 28, nav = Insets(0, 0, 0, 108), gestures = Insets(0, 0, 0, 108))))
        assertEquals(Nav.None, navMode(window(720, 1280, sdk = 28, gestures = Insets(30, 0, 30, 0))))
    }

    @Test
    fun buttonsLayOutNavBarOnExceptHome() {
        val p = scale(a105, Nav.Buttons, Surface.Page, false)
        assertTrue(p.navOn)
        near(390.67f, p.stdContentBottom, 0.005f, "navTop")
        assertTrue(p.edgeBack)
        assertTrue(p.bottomSwipe)
        assertEquals(0f, p.stdNavOverlap)
        val np = scale(a105, Nav.Buttons, Surface.NowPlaying, false)
        assertTrue(np.navOn)
        assertFalse(scale(a105, Nav.Buttons, Surface.Saver, false).navOn)
        val home = scale(a105, Nav.Buttons, Surface.Home, false)
        assertFalse(home.navOn)
        near(426.67f, home.stdContentBottom, 0.005f, "home bottom")
        near(4f, scale(window(720, 1280, nav = Insets(0, 0, 0, 120)), Nav.Buttons, Surface.Home, false).stdNavOverlap, 0.005f, "taller bar under home")
    }

    @Test
    fun aPageWithTheNavBarOnEndsAtAndroidsBarTopAtBothSizes() {
        val p2160 = window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132))
        val std = scale(p2160, Nav.Buttons, Surface.Page, false)
        near(450.67f, std.stdContentBottom, 0.005f, "standard bar top")
        assertEquals(2028, px(std.stdContentBottom, std.stdPpu))
        assertEquals(0f, std.stdNavOverlap)
        val large = scale(p2160, Nav.Buttons, Surface.Page, true)
        assertTrue(large.navOn)
        near(450.67f, large.stdContentBottom, 0.005f, "large bar top")
        assertEquals(2028, px(large.stdContentBottom, large.stdPpu))
        assertEquals(0f, large.stdNavOverlap)
        near(480f, scale(p2160, Nav.Buttons, Surface.Home, true).stdContentBottom, 0.005f, "large home bottom")
        val taller = window(720, 1280, nav = Insets(0, 0, 0, 120))
        val tallStd = scale(taller, Nav.Buttons, Surface.Page, false)
        assertEquals(1160, px(tallStd.stdContentBottom, tallStd.stdPpu))
        assertEquals(0f, tallStd.stdNavOverlap)
        val tallLarge = scale(taller, Nav.Buttons, Surface.Page, true)
        assertEquals(1160, px(tallLarge.stdContentBottom, tallLarge.stdPpu))
        assertEquals(0f, tallLarge.stdNavOverlap)
        val a105Std = scale(a105, Nav.Buttons, Surface.Page, false)
        assertEquals(1172, px(a105Std.stdContentBottom, a105Std.stdPpu))
        val a105Large = scale(a105, Nav.Buttons, Surface.Page, true)
        assertEquals(1172, px(a105Large.stdContentBottom, a105Large.stdPpu))
    }

    @Test
    fun p2160ButtonsAndGestureBarsEachReadTheirModeWithTheImeUpAndATallerImeBar() {
        val buttons = window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132))
        val buttonsIme = window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132), ime = 900)
        val gestureImeBar = window(1080, 2160, sdk = 34, status = 66, nav = Insets(0, 0, 0, 126), gestures = Insets(82, 0, 82, 0), ime = 900)
        assertEquals(Nav.Buttons, navMode(buttons))
        assertEquals(Nav.Buttons, navMode(buttonsIme))
        assertEquals(Nav.Gesture, navMode(gesture))
        assertEquals(Nav.Gesture, navMode(gestureImeBar))
    }

    @Test
    fun aSideNavigationBarIsButtonsWithTheBandOff() {
        val w = window(2160, 1080, nav = Insets(0, 0, 132, 0), gestures = Insets(0, 0, 132, 0))
        assertEquals(Nav.Buttons, navMode(w))
        val s = scale(w, navMode(w), Surface.Page, false)
        assertFalse(s.navOn)
        assertTrue(s.edgeBack)
        assertTrue(s.bottomSwipe)
        near(320f, s.stdContentBottom, 0.005f, "bottom")
        assertEquals(Nav.Gesture, navMode(window(2160, 1080, nav = Insets(0, 0, 0, 66), gestures = Insets(82, 0, 82, 0))))
    }

    @Test
    fun gestureDropsTheEdgeAndBottomBands() {
        val s = scale(gesture, Nav.Gesture, Surface.Page, false)
        assertFalse(s.navOn)
        assertFalse(s.edgeBack)
        assertFalse(s.bottomSwipe)
        near(480f, s.stdContentBottom, 0.005f, "bottom")
        assertEquals(0f, s.stdNavOverlap)
    }

    @Test
    fun noneKeepsTheEdgeAndBottomBands() {
        val s = scale(keys, Nav.None, Surface.Page, false)
        assertFalse(s.navOn)
        assertTrue(s.edgeBack)
        assertTrue(s.bottomSwipe)
        near(426.67f, s.stdContentBottom, 0.005f, "bottom")
    }

    @Test
    fun theImeChangesNeitherTheModeNorTheCanvas() {
        val cases = listOf(
            Triple(a105, window(720, 1280, sdk = 28, status = 54, nav = Insets(0, 0, 0, 108), ime = 500), 260f),
            Triple(
                window(720, 1280, nav = Insets(0, 0, 0, 108)),
                window(720, 1280, nav = Insets(0, 0, 0, 108), ime = 500),
                260f,
            ),
            Triple(gesture, window(1080, 2160, sdk = 34, status = 66, nav = Insets(0, 0, 0, 66), gestures = Insets(82, 0, 82, 0), ime = 900), 280f),
            Triple(keys, window(720, 1280, sdk = 33, ime = 500), 260f),
        )
        for ((down, up, top) in cases) {
            assertEquals(navMode(down), navMode(up))
            val a = scale(down, navMode(down), Surface.Page, false)
            val b = scale(up, navMode(up), Surface.Page, false)
            assertEquals(a.ppu, b.ppu)
            assertEquals(a.stdH, b.stdH)
            assertEquals(a.widthPx, b.widthPx)
            assertEquals(a.stdContentBottom, b.stdContentBottom)
            assertEquals(a.board.npArt, b.board.npArt)
            assertNull(a.stdImeTop)
            near(top, b.stdImeTop!!, 0.005f, "IME top")
        }
        assertEquals(Nav.Buttons, navMode(window(720, 1280, sdk = 28, nav = Insets(0, 0, 0, 108), ime = 500)))
    }

    @Test
    fun barsHiddenOrShownLeaveTheCanvas() {
        for (w in listOf(a105, gesture, tall, keys)) {
            val shown = scale(w, navMode(w), Surface.Page, false)
            for (hidden in listOf(Surface.NowPlaying, Surface.Lyrics, Surface.Saver)) {
                val s = scale(w, navMode(w), hidden, false)
                assertTrue(shown.statusBar)
                assertFalse(s.statusBar)
                assertEquals(shown.ppu, s.ppu)
                assertEquals(shown.stdH, s.stdH)
                assertEquals(shown.left, s.left)
                assertEquals(shown.widthPx, s.widthPx)
                assertEquals(shown.board.npRingY, s.board.npRingY)
                assertEquals(shown.board.page_contentTop, s.board.page_contentTop)
            }
        }
    }

    @Test
    fun aStatusBarPastTheStatusBandIsMeasured() {
        assertEquals(0f, page(a105).stdStatusOverlap)
        assertEquals(0f, page(gesture).stdStatusOverlap)
        near(2.2f, page(tall).stdStatusOverlap, 0.05f, "tall status bar overlap")
    }

    @Test
    fun pxRoundsEachEdgeHalfUp() {
        assertEquals(2, px(0.5f, 3f))
        assertEquals(458, px(152.7f, 3f))
        assertEquals(8, textPx(7f, 1f, 8f))
        assertEquals(12, textPx(8f, 1.5f, 8f))
    }

    @Test
    fun tokensTakeTheFixedPointOfTheirDecimalAsFuneOsTokensSh() {
        assertEquals(6481510L, fx(98.9f))
        assertEquals(773325L, fx(11.8f))
        assertEquals(4679270L, fx(71.4f))
        assertEquals(321126L, fx(4.9f))
        assertEquals(3434086L, fx(52.4f))
        assertEquals(157286L, fx(2.4f))
        assertEquals(196608L, fx(3f))
    }

    @Test
    fun px64RoundsTheFixedPointProductOnceHalfUp() {
        val ppu = fx(3f)
        assertEquals(1066, px64(23298047L, ppu))
        assertEquals(1067, px64(fx(355.5f), ppu))
        assertEquals(-72, px64(-1572864L - 1L, ppu))
        assertEquals(74L shl 16, fxDiv(fxInt(222), ppu))
        assertEquals(fx(1f), fxOfPx(3f, ppu))
    }

    @Test
    fun keptTextGrowsRolesUnderTheKeptMaxByTheLargeScaleInFixedPoint() {
        val large = scale(keys, Nav.Buttons, Surface.Home, true)
        val std = scale(keys, Nav.Buttons, Surface.Home, false)
        assertEquals(766771L, fx(Roles.qpHint.size))
        assertEquals(920125 / 65536f, keptText(Roles.qpHint, large).size, 0f)
        assertEquals(969933L, fx(Roles.boxCaption.size))
        assertEquals(1163919 / 65536f, keptText(Roles.boxCaption, large).size, 0f)
        val atMax = Role(16.8f, Roles.qpHint.weight, Roles.qpHint.case, Roles.qpHint.alpha, Roles.qpHint.tracking)
        assertTrue(keptText(atMax, large) === atMax)
        val under = Role(1101004 / 65536f, atMax.weight, atMax.case, atMax.alpha, atMax.tracking)
        assertEquals(1101005L, fx(Tok.size_keptTextMax))
        assertEquals(1321204 / 65536f, keptText(under, large).size, 0f)
        assertEquals(Roles.qpHint.tracking, keptText(Roles.qpHint, large).tracking, 0f)
        assertTrue(keptText(Roles.qpHint, std) === Roles.qpHint)
        assertTrue(keptText(Roles.homeItem, large) === Roles.homeItem)
        assertTrue(large.large)
        assertTrue(large.kept().large)
        assertFalse(std.large)
    }

    @Test
    fun keptUAndKeptExactTakeTheStandardPxBackToThePagePpuOnlyUnderLarge() {
        val std = scale(keys, Nav.Buttons, Surface.Page, false)
        val large = scale(keys, Nav.Buttons, Surface.Page, true)
        assertEquals(196608L, fx(std.ppu))
        assertEquals(235930L, fx(large.ppu))
        assertEquals(196608L, fx(large.stdPpu))
        assertEquals(6520832L, fx(99.5f))
        assertEquals(6520832L, keptU(99.5f, std))
        assertEquals(6520832L, keptExact(99.5f, std))
        assertEquals(fx(252f), keptU(252f, std))
        assertEquals(13762536L, keptU(252f, large))
        assertEquals(756, px64(keptU(252f, large), fx(large.ppu)))
        assertEquals(5443119L, keptU(99.5f, large))
        assertEquals(299, px64(keptU(99.5f, large), fx(large.ppu)))
        assertEquals(5434018L, keptExact(99.5f, large))
        assertEquals(6526283L, keptExact(119.5f, large))
        assertEquals(11068283L, keptU(202.5f, large))
        assertEquals(608, px64(keptU(202.5f, large), fx(large.ppu)))
        assertEquals(6280522L, keptU(115f, large))
        assertEquals(345, px64(keptU(115f, large), fx(large.ppu)))
    }

    @Test
    fun theP2160PageTakesTheA105PhysicalSizeAndHomeKeepsFit() {
        for (w in listOf(p2160Gesture, p2160Buttons)) {
            val at = "${navMode(w)}"
            val p = page(w)
            assertNotNull(at, p.fit)
            near(2.96669f, p.stdPpu, 0.00001f, "$at page ppu")
            assertEquals(p.stdPpu, p.ppu)
            near(364.04f, p.w, 0.005f, "$at page W")
            near(728.08f, p.h, 0.005f, "$at page H")
            near(728.08f, p.stdH, 0.005f, "$at page stdH")
            near(728.08f, p.board.canvasH, 0.005f, "$at page board H")
            assertEquals(1080, p.widthPx)
            assertEquals(0, p.left)
            assertEquals(4.5f, p.touchPpu)
            val home = scale(w, navMode(w), Surface.Home, false)
            assertNull(at, home.fit)
            assertEquals(4.5f, home.stdPpu)
            assertEquals(4.5f, home.touchPpu)
            near(240f, home.w, 0.0001f, "$at home W")
            near(480f, home.h, 0.0001f, "$at home H")
            assertEquals(1080, home.widthPx)
            assertEquals(0, home.left)
            for (s in listOf(Surface.NowPlaying, Surface.Lyrics, Surface.Saver)) assertNull("$at $s", scale(w, navMode(w), s, false).fit)
        }
        val g = page(p2160Gesture)
        assertFalse(g.navOn)
        assertEquals(g.stdH, g.stdContentBottom)
        val b = page(p2160Buttons)
        assertTrue(b.navOn)
        near(683.59f, b.stdContentBottom, 0.005f, "page bar top")
        assertEquals(2028, px(b.stdContentBottom, b.stdPpu))
        assertEquals(2028, px(b.kept().stdContentBottom, b.kept().stdPpu))
        assertEquals(0f, b.stdNavOverlap)
    }

    @Test
    fun keptOfTheP2160PageIsTheFitBase() {
        val k = page(p2160Buttons).kept()
        assertNull(k.fit)
        assertEquals(4.5f, k.ppu)
        assertEquals(4.5f, k.stdPpu)
        assertEquals(4.5f, k.touchPpu)
        near(240f, k.w, 0.0001f, "kept W")
        near(480f, k.h, 0.0001f, "kept H")
        near(480f, k.board.canvasH, 0.005f, "kept board H")
        near(450.67f, k.stdContentBottom, 0.005f, "kept bar top")
        assertEquals(2028, navBand(k).y0)
        assertEquals(2028, navBand(page(p2160Buttons)).y0)
        val ime = window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132), ime = 900, dpi = p2160Dpi)
        near(280f, page(ime).kept().stdImeTop!!, 0.005f, "kept IME top")
        near(424.715f, page(ime).stdImeTop!!, 0.005f, "page IME top")
        assertEquals(1260, px(page(ime).stdImeTop!!, page(ime).stdPpu))
    }

    @Test
    fun theA105KeepsFitAtItsSpecAndAtEveryNearOrBucketReport() {
        for (ppi in listOf(407.94f, 395.70f, 420.18f, 360f, 320f)) {
            val w = window(720, 1280, sdk = 28, status = 54, nav = Insets(0, 0, 0, 108), dpi = dpi(ppi, 720, 1280, stable = 360))
            val s = page(w)
            assertNull("$ppi", s.fit)
            assertEquals(3f, s.stdPpu)
            near(240f, s.w, 0.0001f, "$ppi W")
            same(page(a105), s, "A105 at $ppi")
        }
        assertNull("360 is a bucket", appPpi(dpi(360f, 720, 1280, stable = 320)))
        assertNull("320 is a bucket", appPpi(dpi(320f, 720, 1280, stable = 360)))
        assertNull("420.18 is within NEAR of DENSITY_420", appPpi(dpi(420.18f, 720, 1280, stable = 360)))
        assertNotNull("395.70 is trusted, the deadband keeps fit", appPpi(dpi(395.70f, 720, 1280, stable = 360)))
        val above = window(720, 1280, sdk = 28, status = 54, nav = Insets(0, 0, 0, 108), dpi = dpi(416.10f, 720, 1280, stable = 360))
        assertNotNull("416.10 is trusted", appPpi(above.dpi!!))
        same(page(a105), page(above), "A105 at 416.10")
    }

    @Test
    fun theV1AndTheR1WithNoDensityKeepFit() {
        assertNull(page(window(240, 320)).fit)
        assertEquals(1f, page(window(240, 320)).touchPpu)
        assertNull(page(window(480, 800)).fit)
        assertEquals(2f, page(window(480, 800)).touchPpu)
    }

    @Test
    fun aResolutionOverrideKeepsThePageUOfTheFullResolution() {
        val full = page(window(1440, 3200, dpi = dpi(516f, 1440, 3200, stable = 560)))
        near(3.79468f, full.stdPpu, 0.00001f, "full ppu")
        near(379.48f, full.w, 0.005f, "full W")
        near(843.29f, full.h, 0.005f, "full H")
        assertEquals(6f, full.touchPpu)
        val over = dpi(516f, 1440, 3200, stable = 560, logicalW = 1080, logicalH = 2400)
        assertEquals(387f, appPpi(over)!!, 0.001f)
        val small = page(window(1080, 2400, dpi = over))
        near(2.84601f, small.stdPpu, 0.00001f, "override ppu")
        near(full.w, small.w, 0.005f, "override W")
        near(full.h, small.h, 0.005f, "override H")
        assertEquals(4.5f, small.touchPpu)
        val modes = dpi(516f, 1440, 3200, stable = 560, sizes = 2, logicalW = 1080, logicalH = 2400)
        assertNull(appPpi(modes))
        assertNull(page(window(1080, 2400, dpi = modes)).fit)
    }

    @Test
    fun aSmallDenseOrAWideWindowKeepsFit() {
        val small = page(window(480, 854, dpi = dpi(326.6f, 480, 854, stable = 240)))
        assertNull(small.fit)
        assertEquals(2f, small.stdPpu)
        val flip7 = page(window(948, 1048, dpi = dpi(344.7f, 948, 1048, stable = 420)))
        assertNull(flip7.fit)
        near(3.275f, flip7.ppu, 0.0005f, "Flip7 cover ppu")
        assertEquals(786, flip7.widthPx)
        assertEquals(81, flip7.left)
    }

    @Test
    fun aPageTakesThePhysicalSizeOnlyUnderTheDeadbandAndAboveTheFloor() {
        val under = page(window(720, 1280, dpi = dpi(363.0666f, 720, 1280, stable = 320)))
        assertNotNull(under.fit)
        near(2.67f, under.stdPpu, 0.00001f, "0.89 of fit")
        assertEquals(3f, under.touchPpu)
        assertNull(page(window(720, 1280, dpi = dpi(371.2254f, 720, 1280, stable = 320))).fit)
        val tablet = dpi(216f, 800, 1280, stable = 213)
        assertNotNull(appPpi(tablet))
        assertNull(page(window(800, 1280, dpi = tablet)).fit)
        val big = dpi(282f, 1600, 2560, stable = 320)
        assertNotNull(appPpi(big))
        assertNull(page(window(1600, 2560, dpi = big)).fit)
    }

    @Test
    fun theBandEdgeComparesIn16Dot16() {
        assertTrue(TokBoard.a105.u * 367.145f / 407.94f < 3f * 0.9f)
        assertEquals(fx(3f * 0.9f), fx(TokBoard.a105.u * 367.145f / 407.94f))
        assertNull(page(window(720, 1280, dpi = dpi(367.145f, 720, 1280, stable = 320))).fit)
        assertTrue(fx(TokBoard.a105.u * 367.14f / 407.94f) < fx(3f * 0.9f))
        assertNotNull(page(window(720, 1280, dpi = dpi(367.14f, 720, 1280, stable = 320))).fit)
    }

    @Test
    fun anUntrustedReportKeepsFit() {
        val reports = listOf(
            dpi(0f, 1080, 2160, stable = 440),
            dpi(403.4f, 1080, 2160, stable = 403),
            dpi(400.5f, 1080, 2160, stable = 440),
            dpi(403.411f, 1080, 2160, stable = 440, y = 484.09f),
            dpi(390.4f, 1080, 2160, stable = 440),
        )
        for (d in reports) {
            assertNull("${d.x} ${d.y} ${d.stable}", appPpi(d))
            val s = page(window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132), dpi = d))
            assertNull(s.fit)
            assertEquals(4.5f, s.stdPpu)
        }
        assertEquals(403.411f, appPpi(p2160Dpi)!!, 0.001f)
    }

    @Test
    fun everyPlatformDensityBucketIsUntrusted() {
        val buckets = listOf(120, 140, 160, 180, 200, 213, 220, 240, 260, 280, 300, 320, 340, 360, 390, 400, 420, 440, 450, 480, 520, 560, 600, 640)
        assertEquals(24, buckets.size)
        for (b in buckets) assertNull("$b", appPpi(dpi(b.toFloat(), 1080, 2160, stable = 0)))
        assertNotNull(appPpi(dpi(395f, 1080, 2160, stable = 0)))
    }

    @Test
    fun largeOnThePhysicalBaseGrowsThePagePpu() {
        val large = page(p2160Gesture, large = true)
        assertEquals(233310 / 65536f, large.ppu)
        near(2.96669f, large.stdPpu, 0.00001f, "large std ppu")
        near(303.37f, large.w, 0.005f, "large W")
        near(606.74f, large.h, 0.005f, "large H")
        near(728.08f, large.stdH, 0.005f, "large stdH")
        assertTrue(large.large)
        assertFalse(large.fit!!.large)
        assertEquals(4.5f, large.touchPpu)
        assertTrue(large.kept().large)
        assertEquals(4.5f, large.kept().ppu)
        near(240f, large.kept().w, 0.0001f, "large kept W")
    }

    @Test
    fun aPhonePanelCoastsOnEverySurfaceKeptIncluded() {
        for (w in listOf(p2160Gesture, p2160Buttons)) for (surface in Surface.entries) for (large in listOf(false, true)) {
            val at = "${navMode(w)} $surface large $large"
            val s = scale(w, navMode(w), surface, large)
            assertTrue(at, s.coast)
            assertTrue("$at kept", s.kept().coast)
            s.fit?.let { assertTrue("$at fit", it.coast) }
        }
    }

    @Test
    fun aPhonePanelCoastsInSplitScreenInLandscapeAndOnAFlipCoverWhereThePageKeepsFit() {
        val split = scale(window(1080, 1050, sdk = 34, dpi = p2160Dpi), Nav.None, Surface.Page, false)
        assertNull(split.fit)
        assertTrue(split.coast)
        val land = scale(window(2160, 1080, sdk = 34, dpi = p2160Dpi), Nav.None, Surface.Page, false)
        assertNull(land.fit)
        assertTrue(land.coast)
        val turned = dpi(403.411f, 1080, 2160, stable = 440, logicalW = 2160, logicalH = 1080)
        assertTrue(scale(window(2160, 1080, sdk = 34, dpi = turned), Nav.None, Surface.Page, false).coast)
        val flip7 = page(window(948, 1048, dpi = dpi(344.7f, 948, 1048, stable = 420)))
        assertNull(flip7.fit)
        assertTrue(flip7.coast)
        assertTrue(flip7.kept().coast)
    }

    @Test
    fun noDensityADapSizedPanelOrATabletDoesNotCoast() {
        assertFalse(page(window(240, 320)).coast)
        assertFalse(page(window(480, 800)).coast)
        val probed = window(720, 1280, sdk = 28, status = 54, nav = Insets(0, 0, 0, 108), dpi = dpi(406.4f, 720, 1280, stable = 360))
        assertNotNull(appPpi(probed.dpi!!))
        for (surface in Surface.entries) {
            assertFalse("A105 probed $surface", scale(probed, navMode(probed), surface, false).coast)
            assertFalse("A105 probed $surface kept", scale(probed, navMode(probed), surface, false).kept().coast)
        }
        for (ppi in listOf(407.94f, 395.70f, 430f, 420.2f, 360f, 320f)) {
            val w = window(720, 1280, sdk = 28, status = 54, nav = Insets(0, 0, 0, 108), dpi = dpi(ppi, 720, 1280, stable = 360))
            assertFalse("A105 at $ppi", page(w).coast)
            assertFalse("A105 home at $ppi", scale(w, navMode(w), Surface.Home, false).coast)
        }
        assertNull("430 is trusted, the deadband keeps it off the coast", dpiReject(dpi(430f, 720, 1280, stable = 360)))
        assertEquals("420.2 is within NEAR of DENSITY_420", DpiReject.Bucket, dpiReject(dpi(420.2f, 720, 1280, stable = 360)))
        assertEquals("360 is the stable density", DpiReject.Stable, dpiReject(dpi(360f, 720, 1280, stable = 360)))
        assertEquals("320 is a bucket", DpiReject.Bucket, dpiReject(dpi(320f, 720, 1280, stable = 360)))
        val tablet = dpi(216f, 800, 1280, stable = 213)
        assertNotNull(appPpi(tablet))
        assertFalse(page(window(800, 1280, dpi = tablet)).coast)
        val big = dpi(282f, 1600, 2560, stable = 320)
        assertNotNull(appPpi(big))
        assertFalse(page(window(1600, 2560, dpi = big)).coast)
        val untrusted = dpi(400.5f, 1080, 2160, stable = 440)
        assertNull(appPpi(untrusted))
        assertFalse(page(window(1080, 2160, sdk = 34, dpi = untrusted)).coast)
    }

    @Test
    fun everyWindowWhoseRuleTakesThePageBaseCoasts() {
        val phones = listOf(
            p2160Gesture,
            p2160Buttons,
            window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132), ime = 900, dpi = p2160Dpi),
            window(1080, 2400, dpi = dpi(516f, 1440, 3200, stable = 560, logicalW = 1080, logicalH = 2400)),
        )
        for (w in phones) for (large in listOf(false, true)) {
            val s = scale(w, navMode(w), Surface.Page, large)
            assertNotNull("${w.widthPx}x${w.heightPx} large $large", s.fit)
            assertTrue("${w.widthPx}x${w.heightPx} large $large", s.coast)
        }
        val windows = listOf(window(720, 1280), window(1080, 2160), window(1080, 2400), window(1080, 2520), window(1440, 3200), window(800, 1280), window(1600, 2560))
        var paged = 0
        for (bare in windows) {
            var ppi = 100f
            while (ppi <= 700f) {
                val w = window(bare.widthPx, bare.heightPx, dpi = dpi(ppi, bare.widthPx, bare.heightPx))
                val s = page(w)
                if (s.fit != null) paged++
                assertEquals("${bare.widthPx}x${bare.heightPx} $ppi", s.fit != null, s.coast)
                ppi += 5f
            }
        }
        assertTrue(paged > 0)
    }

    @Test
    fun everyWindowTheRuleKeepsOnFitEqualsTheNoDensityPathOfTheSameScaleFieldByField() {
        val windows = listOf(
            window(240, 320),
            window(480, 800),
            window(720, 1280, sdk = 28, status = 54, nav = Insets(0, 0, 0, 108)),
            window(1080, 2160, sdk = 34, status = 66, nav = Insets(0, 0, 0, 66), gestures = Insets(82, 0, 82, 0)),
            window(1080, 2160, sdk = 34, nav = Insets(0, 0, 0, 132), gestures = Insets(0, 0, 0, 132), ime = 900),
            window(1080, 2400),
            window(1080, 2520, sdk = 37, status = 109, nav = Insets(0, 0, 0, 39), gestures = Insets(78, 0, 78, 0), cutout = Insets(0, 109, 0, 0)),
            window(1440, 3200),
            window(480, 854),
            window(768, 1280),
            window(800, 1280),
            window(1600, 2560),
            window(948, 1048),
            window(2160, 1080, nav = Insets(0, 0, 132, 0)),
        )
        var physical = 0
        for (bare in windows) {
            val nav = navMode(bare)
            var ppi = 100f
            while (ppi <= 700f) {
                val w = Window(
                    bare.widthPx, bare.heightPx, bare.sdk, bare.statusBars, bare.navigationBars, bare.systemGestures, bare.cutout, bare.ime,
                    dpi(ppi, bare.widthPx, bare.heightPx),
                )
                for (surface in Surface.entries) for (large in listOf(false, true)) {
                    val at = "${bare.widthPx}x${bare.heightPx} $ppi $surface large $large"
                    val s = scale(w, nav, surface, large)
                    val n = scale(bare, nav, surface, large)
                    val f = s.fit
                    if (f == null) {
                        same(n, s, at)
                        continue
                    }
                    physical++
                    assertEquals(at, Surface.Page, surface)
                    same(scale(bare, nav, surface, false), f, "$at fit")
                    same(n.kept(), s.kept(), "$at kept")
                    assertEquals(at, n.stdPpu, s.touchPpu)
                    assertTrue(at, fx(s.stdPpu) < fx(f.stdPpu * 0.9f) && fx(s.stdPpu) >= fx(f.stdPpu * 0.5f))
                    assertEquals(at, 0, s.left)
                    assertEquals(at, bare.widthPx, s.widthPx)
                }
                ppi += 5f
            }
        }
        assertTrue(physical > 0)
    }
}
