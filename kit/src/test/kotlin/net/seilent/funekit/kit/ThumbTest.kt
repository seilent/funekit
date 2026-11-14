package net.seilent.funekit.kit

import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class ThumbTest {
    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Page, false)

    @Test
    fun theThumbIsSizedByTheViewAndPlacedByTheScroll() {
        val r = thumbOf(v1, 98, 320, 222, 4000, 0)!!
        assertEquals(236, r.x0)
        assertEquals(240, r.x1)
        assertEquals(98, r.y0)
        assertEquals(25 + ((197L * 222 + 2000) / 4000).toInt(), r.y1 - r.y0)
        val len = r.y1 - r.y0
        val end = thumbOf(v1, 98, 320, 222, 4000, 4000 - 222)!!
        assertEquals(320 - len, end.y0)
        val mid = thumbOf(v1, 98, 320, 222, 4000, 1000)!!
        assertEquals(98 + (((222 - len).toLong() * 1000 + 1889) / 3778).toInt(), mid.y0)
        assertNull(thumbOf(v1, 98, 320, 222, 222, 0))
    }

    @Test
    fun theThumbHolds1500msThenPopsOutOver500ms() {
        val f = ThumbFade()
        f.track(on = true, moving = true, now = 0)
        f.release(100)
        assertEquals(1f, f.at(1599), 0f)
        assertEquals(1f, f.at(1600), 0f)
        assertEquals(2.0.pow(-10 * 250.0 / 500).toFloat(), f.at(1850), 0.002f)
        assertEquals(0f, f.at(2100), 0f)
    }

    @Test
    fun theThumbSitsAtTheRightEdgeOfALargeCanvas() {
        val large = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, true)
        val r = thumbOf(large, 0, 1280, 1000, 4000, 0)!!
        assertEquals(Math.round((720 / 3.6 - 4) * 3.6).toInt(), r.x0)
        assertEquals(720, r.x1)
    }

    @Test
    fun aListThumbMeasuresRowsTimesPitch() {
        val r = listThumbOf(v1, 98, 320, 100, 40, 0)!!
        assertEquals(25 + ((197L * 222 + 2000) / 4000).toInt(), r.y1 - r.y0)
    }

    @Test
    fun theFadeWakesAtTheHoldEndAndStopsFadingAfterIt() {
        val f = ThumbFade()
        f.track(on = true, moving = true, now = 0)
        f.release(100)
        assertEquals(1600L, f.wakeAt(200))
        f.track(on = true, moving = false, now = 1700)
        assertEquals(true, f.fading(1700))
        f.track(on = true, moving = false, now = 2100)
        assertEquals(false, f.fading(2100))
        assertEquals(-1L, f.wakeAt(2100))
    }

    @Test
    fun theThumbKeepsItsXAndLengthAtEveryScrollAndNoThumbIsTransparent() {
        val rest = thumbOf(v1, 98, 320, 222, 4000, 0)!!
        for (scrolled in listOf(1000L, 2500L, 4000L - 222)) {
            val r = thumbOf(v1, 98, 320, 222, 4000, scrolled)!!
            assertEquals(rest.x0, r.x0)
            assertEquals(rest.x1, r.x1)
            assertEquals(rest.y1 - rest.y0, r.y1 - r.y0)
        }
        assertEquals(0f, thumbProps(null, 1f).alpha, 0f)
    }

    @Test
    fun onTheP2160PageTheThumbSitsAtTheWindowEdge() {
        val p2160 = scale(Window(1080, 2160, 35, none, none, none, none, none, Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)), Nav.None, Surface.Page, false)
        assertEquals(364.04f, p2160.w, 0.005f)
        val r = thumbOf(p2160, 0, 2160, 1000, 4000, 0)!!
        assertEquals(1068, r.x0)
        assertEquals(1080, r.x1)
    }

    @Test
    fun showRestartsAFadedThumbAndItsHold() {
        val f = ThumbFade()
        f.track(true, false, 0L)
        assertEquals(0f, f.at(2000L), 0f)
        f.show(5000L)
        assertEquals(1f, f.at(5000L), 0f)
        assertEquals(1f, f.at(6500L), 0f)
        assertEquals(1 - Curves.pop.at(0.5f), f.at(6750L), 1e-6f)
        assertEquals(0f, f.at(7000L), 0f)
    }

    @Test
    fun showDropsAHeldDrag() {
        val f = ThumbFade()
        f.track(true, true, 0L)
        assertEquals(1f, f.at(5000L), 0f)
        f.show(100L)
        assertEquals(1f, f.at(1599L), 0f)
        assertEquals(0f, f.at(2100L), 0f)
    }
}
