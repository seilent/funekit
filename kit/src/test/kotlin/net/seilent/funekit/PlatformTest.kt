package net.seilent.funekit

import android.app.Activity
import android.util.DisplayMetrics
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlatformTest {
    private fun insets(barsShown: Boolean, ime: Int): WindowInsetsCompat {
        val status = Insets.of(0, 66, 0, 0)
        val nav = Insets.of(0, 0, 0, 66)
        val b = WindowInsetsCompat.Builder()
            .setInsetsIgnoringVisibility(Type.statusBars(), status)
            .setInsetsIgnoringVisibility(Type.navigationBars(), nav)
            .setInsetsIgnoringVisibility(Type.systemGestures(), Insets.of(82, 0, 82, 0))
            .setInsets(Type.systemGestures(), Insets.of(82, 0, 82, 0))
            .setInsets(Type.statusBars(), if (barsShown) status else Insets.NONE)
            .setInsets(Type.navigationBars(), if (barsShown) nav else Insets.NONE)
            .setVisible(Type.statusBars(), barsShown)
            .setVisible(Type.navigationBars(), barsShown)
            .setInsets(Type.ime(), Insets.of(0, 0, 0, ime))
            .setVisible(Type.ime(), ime > 0)
        return b.build()
    }

    @Test
    fun hiddenBarsAndTheImeReadAsTheSameWindow() {
        val shown = Platform.window(1080, 2160, 34, insets(true, 0))
        for (other in listOf(insets(false, 0), insets(true, 900), insets(false, 900))) {
            val w = Platform.window(1080, 2160, 34, other)
            assertEquals(shown.statusBars.top, w.statusBars.top)
            assertEquals(shown.navigationBars.bottom, w.navigationBars.bottom)
            assertEquals(shown.systemGestures.left, w.systemGestures.left)
            assertEquals(Nav.Gesture, navMode(w))
            val a = scale(shown, navMode(shown), Surface.Page, false)
            val b = scale(w, navMode(w), Surface.Page, false)
            assertEquals(a.ppu, b.ppu)
            assertEquals(a.stdH, b.stdH)
            assertEquals(a.stdContentBottom, b.stdContentBottom)
        }
        assertEquals(66, shown.statusBars.top)
        assertEquals(900, Platform.window(1080, 2160, 34, insets(true, 900)).ime.bottom)
    }

    @Suppress("DEPRECATION")
    private fun stable(barsShown: Boolean): WindowInsetsCompat {
        val bars = Insets.of(0, 54, 0, 108)
        return WindowInsetsCompat.Builder()
            .setStableInsets(bars)
            .setSystemWindowInsets(if (barsShown) bars else Insets.NONE)
            .build()
    }

    @Test
    @Config(sdk = [28])
    fun onApi28TheStableBarsGiveButtonsShownOrHidden() {
        for (shown in listOf(true, false)) {
            val w = Platform.window(720, 1280, 28, stable(shown))
            assertEquals(54, w.statusBars.top)
            assertEquals(108, w.navigationBars.bottom)
            assertEquals(Nav.Buttons, navMode(w))
            val s = scale(w, navMode(w), Surface.Page, false)
            assertEquals(3f, s.stdPpu)
            assertEquals(true, s.navOn)
        }
    }

    @Test
    @Config(sdk = [28, 35], qualifiers = "w360dp-h640dp-xhdpi")
    fun theDensityIsReadFromTheDisplayAndRobolectricsCopiedReportIsUntrusted() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val d = Platform.dpi(activity)
        assertEquals(320f, d.x)
        assertEquals(320f, d.y)
        assertEquals(DisplayMetrics.DENSITY_XHIGH.toFloat(), d.x)
        assertEquals(720, d.modeW)
        assertEquals(1280, d.modeH)
        assertEquals(1, d.modeSizes)
        assertEquals(720, d.logicalW)
        assertEquals(1280, d.logicalH)
        assertNull(appPpi(d))
        val w = Platform.window(activity, WindowInsetsCompat.CONSUMED)
        assertEquals(320f, w.dpi!!.x)
        val s = scale(w, navMode(w), Surface.Page, false)
        assertNull(s.fit)
        assertEquals(s.stdPpu, s.touchPpu)
    }
}
