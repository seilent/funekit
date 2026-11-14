package net.seilent.funekit.kit.stack

import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.npInk
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NpEndsTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val forms = MoveForms.board(TokBoard.a105)
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
    fun t8TakesThePageSlideOrTheFrontSlideInWholePx() {
        val page = npEnds(MoveKind.T8, side, null, NpForm.Page, forms)
        assertEquals(-79f, page.npX, 0f)
        assertEquals(0f, page.npY, 0f)
        val front = npEnds(MoveKind.T8, side, null, NpForm.Front, forms)
        assertEquals(-55f, front.npX, 0f)
        assertEquals(11f, front.npY, 0f)
        assertFalse(page.npCut)
    }

    @Test
    fun t7EndsAtItsInkPlusTheRestAndTakesThePaneAndRingShiftsInPx() {
        val e = npEnds(MoveKind.T7, side, null, NpForm.Ring, forms)
        assertEquals(npInk(NpForm.Ring, forms) + Tok.motion_t7Rest.toInt(), e.endAt)
        assertEquals(-349f, e.npX, 0f)
        assertEquals(68f, e.npLeft, 0f)
        assertEquals(NpForm.Ring, e.npForm)
        assertEquals(450, npEnds(MoveKind.T7, side, null, NpForm.Page, forms).endAt)
    }

    @Test
    fun theEyeIsThePerspectiveTheHingeAndTheEyeRatioOfTheScreenHeight() {
        val e = npEye(3f, 1280, Tok.motion_t7Hinge, Tok.motion_t7Eye)
        assertEquals(1656f, e.d, 0f)
        assertEquals(-267f, e.hinge, 0f)
        assertEquals(659f, e.eye, 0f)
    }
}
