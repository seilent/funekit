package net.seilent.funekit.kit.stack

import net.seilent.funekit.gen.Moves
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaneQuadTest {
    private fun corners(angle: Float, slide: Float, hinge: Float = HINGE) = checkNotNull(npCam(angle, slide, hinge, PERSP, EYE * H, CAMERA * W)).let { m ->
        listOf(0f to 0f, W to 0f, W to H, 0f to H).map { (x, y) -> m.map(x, y).let { it.x to it.y } }
    }

    private fun check(name: String, angle: Float, slide: Float, want: List<Pair<Float, Float>>, hinge: Float = HINGE) {
        corners(angle, slide, hinge).zip(want).forEachIndexed { i, (got, w) ->
            assertEquals("$name corner $i x", w.first, got.first, NEAR)
            assertEquals("$name corner $i y", w.second, got.second, NEAR)
        }
    }

    @Test
    fun theHomePlaneTakesTheFuneOsCamera() {
        assertEquals(HINGE, Moves.kT8HomeHinge, NEAR)
        assertEquals(HOME_R, Moves.kT8HomeR, NEAR)
        assertEquals(HOME_X, Moves.kT8HomeX, NEAR)
        assertEquals(PERSP, Tok.motion_t7Persp, NEAR)
        assertEquals(EYE, TokBoard.v1.motion_t7EyeY, NEAR)
        assertEquals(CAMERA, Tok.motion_t7EyeX, NEAR)
        assertNotNull(npCam(HOME_R, HOME_X, HINGE, PERSP, EYE * H, CAMERA * W))
    }

    @Test
    fun aFlatPlaneWithItsSlideIsTheScreenShifted() {
        check("A1", 0f, HOME_X, listOf(-3.46f to 0f, 236.54f to 0f, 236.54f to 320f, -3.46f to 320f))
        check("A2", 0f, 0f, listOf(0f to 0f, 240f to 0f, 240f to 320f, 0f to 320f))
    }

    @Test
    fun theHalfTurnProjectsTheCornersAndSlidesAfterTheProjection() {
        check("A3", HALF_R, HOME_X, listOf(-1.9628f to 0.6924f, 213.3595f to -33.1723f, 213.3595f to 351.5541f, -1.9628f to 319.3413f))
        check("A4", HALF_R, 0f, listOf(1.4826f to 0.6924f, 217.5193f to -33.1723f, 217.5193f to 351.5541f, 1.4826f to 319.3413f))
    }

    @Test
    fun theFirstHomeFrameProjectsTheCorners() {
        check("A5", HOME_R, HOME_X, listOf(0.8364f to 1.1394f, 91.1351f to -63.0187f, 91.1351f to 379.9446f, 0.8364f to 318.9162f))
        check("A6", HOME_R, 0f, listOf(4.2723f to 1.1394f, 95.9246f to -63.0187f, 95.9246f to 379.9446f, 4.2723f to 318.9162f))
    }

    @Test
    fun theDrawnPlaneRoundsItsHingeAndSlideToWholePxAndProjectsTheirCorners() {
        assertEquals(WHOLE_HINGE, Math.round(HINGE).toFloat(), 0f)
        assertEquals(WHOLE_X, Math.round(HOME_X).toFloat(), 0f)
        check("B1", HALF_R, WHOLE_X, listOf(-1.4795f to 0.7041f, 213.9244f to -33.1553f, 213.9244f to 351.5379f, -1.4795f to 319.3302f), WHOLE_HINGE)
        check("B2", HOME_R, WHOLE_X, listOf(1.3654f to 1.1586f, 91.8577f to -62.9814f, 91.8577f to 379.9092f, 1.3654f to 318.8979f), WHOLE_HINGE)
    }

    private companion object {
        const val W = 240f
        const val H = 320f
        const val HINGE = 5.9f
        const val PERSP = 768f
        const val EYE = 0.5125f
        const val CAMERA = 0.5f
        const val HOME_R = -65.6f
        const val HOME_X = -3.46f
        const val HALF_R = -33.5f
        const val WHOLE_HINGE = 6f
        const val WHOLE_X = -3f
        const val NEAR = 1e-2f
    }
}
