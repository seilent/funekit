package net.seilent.funekit.kit.stack

import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.npInk
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpEndsTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val side = PageSide(s, BlankPaint)

    private object BlankPaint : PagePaint {
        override val chrome: Chrome? = null
        override val header: Word? = null
        override val item = -1
        override val grounded = false
        override val kind = PageKind.Plain
        override val top = 0
        override val bottom = 1280
        override val count = 0

        override fun rowY(i: Int) = 0

        override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

        override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) = Unit
    }

    @Test
    fun aT8ToHomeTakesTheHomeTurnAndEveryNpMoveTakesTheStandardPxPerU() {
        val home = npEnds(MoveKind.T8, side, null, NpForm.Page, toHome = true)
        assertTrue(home.npHome)
        assertEquals(3f, home.pxU, 0f)
        assertFalse(home.npCut)
        assertFalse(npEnds(MoveKind.T8, side, null, NpForm.Page).npHome)
        assertFalse(npEnds(MoveKind.T7, side, null, NpForm.Page, toHome = true).npHome)
    }

    @Test
    fun aT7KeepsItsFormAndEndsWithItsLegs() {
        val e = npEnds(MoveKind.T7, side, null, NpForm.Ring)
        assertEquals(NpForm.Ring, e.npForm)
        assertEquals(0, e.endAt)
        assertEquals(300, npInk())
    }

    @Test
    fun theEyeIsThePerspectiveTheHingeAndTheEyeRatioOfTheScreenHeight() {
        val e = npEye(3f, 1280, Tok.motion_t8OutHinge, Tok.motion_t8OutEye)
        assertEquals(1656f, e.d, 0f)
        assertEquals(-455f, e.hinge, 0f)
        assertEquals(644f, e.eye, 0f)
    }

    @Test
    fun aCameraPlaneTurnedPastItsEdgeFromTheSlidEyeDrawsNothing() {
        val c = kotlin.math.cos(Math.toRadians(-30.0))
        val sn = kotlin.math.sin(Math.toRadians(-30.0))
        assertTrue(c + (360 - 0) * sn / 100 < 0)
        assertTrue(c + (360 - 300) * sn / 100 > 0)
        assertEquals(null, npCam(-30f, 0f, 0f, 100f, 640f, 360f))
        assertTrue(npCam(-30f, 300f, 0f, 100f, 640f, 360f) != null)
    }
}
