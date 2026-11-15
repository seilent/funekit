package net.seilent.funekit.kit.motion

import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class MotionTest {
    private fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, t: Double): Double {
        fun b(a: Double, c: Double, s: Double) = 3 * (1 - s) * (1 - s) * s * a + 3 * (1 - s) * s * s * c + s * s * s
        var lo = 0.0
        var hi = 1.0
        repeat(60) {
            val m = (lo + hi) / 2
            if (b(x1, x2, m) < t) lo = m else hi = m
        }
        return b(y1, y2, (lo + hi) / 2)
    }

    @Test
    fun t12At150IsDecelOver380ms() {
        val p = bezier(0.1, 0.9, 0.2, 1.0, 150.0 / 380)
        val s = Overlay(OverKind.Sheet).also { it.open(0) }.sheet(150)
        assertEquals(0.55 * p, s.scrim.toDouble(), 0.001)
        assertEquals(1 - p, s.dy.toDouble(), 0.001)
        assertEquals(p, s.alpha.toDouble(), 0.001)
        assertEquals(0.5149, s.scrim.toDouble(), 0.001)
    }

    @Test
    fun t13At120IsPopAndBezierPopOver300ms() {
        val t = 120.0 / 300
        val o = 1 - 2.0.pow(-10 * t)
        val m = t - 1
        val k = 1.70158
        val back = m * m * ((k + 1) * m + k) + 1
        val d = Overlay(OverKind.Dlg).also { it.open(0) }.dialog(120)
        assertEquals(0.96 * o, d.scrim.toDouble(), 0.002)
        assertEquals(0.9 + 0.1 * back, d.scale.toDouble(), 0.0005)
    }

    @Test
    fun t14At150IsHalfWayLinearOver300ms() {
        assertEquals(0.5f, Overlay(OverKind.Jump).also { it.open(0) }.jump(150), 0.0001f)
        val out = Overlay(OverKind.Jump)
        out.open(0)
        out.close(300)
        assertEquals(0.5f, out.jump(390), 0.0001f)
    }

    @Test
    fun t5At250IsTheRestFrame() {
        for (dir in listOf(-1, 1)) {
            val p = pivotAt(250f, dir)
            assertEquals(0f, p.stripDx, 0.0001f)
            assertEquals(1f, p.stripAlpha, 0.0001f)
            assertEquals(0f, p.rowsDx, 0.0001f)
            assertEquals(1f, p.rowsAlpha, 0.0001f)
        }
        val out = pivotAt(25f, 1)
        assertEquals(false, out.rowsIn)
        assertEquals(1 - bezier(0.2, 0.8, 0.2, 1.0, 0.5), out.rowsAlpha.toDouble(), 0.002)
    }

    @Test
    fun t4MorphAt200() {
        val m = 1 - 212.0 / 400
        assertEquals(1 - m * m * m, Curves.cubicOut.at(212f / 400).toDouble(), 0.0001)
        assertEquals(0.8962f, Curves.cubicOut.at(212f / 400), 0.0001f)
    }

    @Test
    fun curvesHitTheirEnds() {
        val all = listOf(
            Curves.linear, Curves.expOut6, Curves.expIn6, Curves.expIn1, Curves.expOut1, Curves.expIn15, Curves.decel,
            Curves.strip, Curves.sine, Curves.pop, Curves.bezierPop, Curves.easeIn, Curves.easeOut, Curves.powOut17,
            Curves.cubicOut, Curves.smoothStep, Curves.powOut15, Curves.quartOut, Curves.powIn15,
        )
        for (c in all) {
            assertEquals(0f, c.at(0f), 0.0001f)
            assertEquals(1f, c.at(1f), 0.0001f)
        }
    }

    @Test
    fun f3PressGoesTo096In100msAndReturnsAfter200ms() {
        val p = Press()
        p.down(0f)
        assertEquals(0.98f, p.scale(50f), 0.0001f)
        assertEquals(0.96f, p.scale(100f), 0.0001f)
        p.tap(100f)
        assertEquals(0.96f, p.scale(300f), 0.0001f)
        assertEquals(0.98f, p.scale(350f), 0.0001f)
        assertEquals(1f, p.scale(400f), 0.0001f)
        p.down(500f)
        assertEquals(1 - 0.04f * 2 / 16, p.drawn(515f), 0.0001f)
    }

    @Test
    fun aShownPressIsStaleWhileItMovesAndNotOnceItsLegEnded() {
        val p = Press().also { it.scaled = true }
        p.down(0f)
        p.show(50f)
        assertTrue(p.stale(50f))
        p.tap(100f)
        p.show(350f)
        assertTrue(p.stale(350f))
        p.show(500f)
        assertFalse(p.stale(500f))
    }

    @Test
    fun factorAfterShowAndFactorAtEqualDrawnForEveryStep() {
        val seen = HashSet<Float>()
        for (k in 0..16) {
            val t = k * Tok.motion_press / 16
            val a = Press().also { it.down(0f) }
            val b = Press().also { it.down(0f) }
            val c = Press().also { it.down(0f) }
            a.show(t)
            val d = c.drawn(t)
            assertEquals(d, a.factor(), 0f)
            assertEquals(d, b.factorAt(t), 0f)
            seen += d
        }
        assertEquals(17, seen.size)
    }

    @Test
    fun factorAtLeavesTheShownStepAlone() {
        val p = Press().also { it.scaled = true }
        p.down(0f)
        p.show(50f)
        val shown = p.factor()
        assertTrue(p.factorAt(100f) < shown)
        assertEquals(shown, p.factor(), 0f)
        assertTrue(p.stale(100f))
    }

    @Test
    fun everyPressMutatorButShowBumpsTheRevision() {
        val p = Press()
        val mutators = listOf<(Press) -> Unit>(
            { it.down(0f) }, { it.tap(10f) }, { it.cancel(20f) }, { it.drop() }, { it.reset() },
            { it.down(30f) }, { it.hold(31f) }, { it.cutRing(32f) }, { it.keyUp(33f) }, { it.untap(34f) },
        )
        for (m in mutators) {
            val r = p.revision
            m(p)
            assertTrue(p.revision > r)
        }
        val r = p.revision
        p.show(30f)
        p.factor()
        p.factorAt(40f)
        p.drawn(50f)
        p.stale(60f)
        p.look(70f)
        p.lit(80f)
        p.ring(90f)
        assertEquals(r, p.revision)
    }

    @Test
    fun theLitRisesOverThePressGlowAndATapThatActsCutsItAtTheTap() {
        val p = Press()
        p.down(0f)
        assertEquals(Curves.cubicOut.at(0.5f), p.lit(25f), 0.0001f)
        assertEquals(1f, p.lit(Tok.motion_pressGlow), 0f)
        assertEquals(1f, p.ring(60f), 0f)
        p.tap(100f)
        assertEquals(0f, p.lit(100f), 0f)
        assertEquals(0f, p.ring(100f), 0f)
        assertTrue(p.framed(299f))
        assertFalse(p.framed(300f))
    }

    @Test
    fun aTapThatDoesNotActKeepsTheLitAndTheRingUntilTheRelease() {
        val p = Press()
        p.down(0f)
        p.tap(100f, acts = false)
        assertEquals(1f, p.lit(150f), 0f)
        assertEquals(1f, p.ring(299f), 0f)
        assertEquals(0f, p.lit(300f), 0f)
        assertEquals(0f, p.ring(300f), 0f)
    }

    @Test
    fun aCancelFadesTheLitAndTheRingTogether() {
        val p = Press()
        p.down(0f)
        p.cancel(50f)
        assertEquals(0.5f, p.lit(100f), 0.0001f)
        assertEquals(0.5f, p.ring(100f), 0.0001f)
        assertEquals(0f, p.ring(150f), 0f)
        p.down(200f)
        p.cancel(250f)
        p.cutRing(260f)
        assertEquals(0f, p.ring(270f), 0f)
        assertTrue(p.lit(270f) > 0f)
    }

    @Test
    fun aHoldSetsThePressedStateAndAKeyUpCutsTheLitAndReturns() {
        val p = Press()
        p.down(0f)
        p.hold(10f)
        assertEquals(Tok.motion_pressScale, p.scale(10f), 0f)
        assertEquals(1f, p.lit(10f), 0f)
        p.keyUp(100f)
        assertFalse(p.held)
        assertEquals(0f, p.lit(100f), 0f)
        assertEquals(lerp(Tok.motion_pressScale, 1f, 0.5f), p.scale(150f), 0.0001f)
    }

    @Test
    fun anUnscaledPressMovesOnlyWhileItsGlowRuns() {
        val p = Press()
        p.down(0f)
        assertTrue(p.moving(40f))
        assertFalse(p.moving(60f))
        p.scaled = true
        assertTrue(p.moving(60f))
        assertFalse(p.moving(100f))
    }

    @Test
    fun theBridgeFactorKeepsThePressScaleStepsAndLookReturnsTheLit() {
        val p = Press()
        p.down(0f)
        p.show(50f)
        assertEquals(1 - (1 - Tok.motion_pressScale) * 8 / 16, p.factor(), 0.0001f)
        assertEquals(p.factorAt(50f), p.factor(), 0f)
        assertEquals(1f, p.look(50f), 0f)
        assertEquals(1 - (1 - Tok.motion_pressScale) * 8 / 16, p.factor(), 0.0001f)
    }

    @Test
    fun anEarlyTapKeepsShrinkingUntilTheReturn() {
        val p = Press()
        p.down(0f)
        p.tap(50f)
        assertEquals(0.96f, p.scale(100f), 0.0001f)
        assertEquals(0.96f, p.scale(250f), 0.0001f)
        assertEquals(0.98f, p.scale(300f), 0.0001f)
        assertEquals(1f, p.scale(350f), 0.0001f)
    }

    @Test
    fun aCancelAfterATapAndASecondTapDoNothing() {
        val p = Press()
        p.down(0f)
        p.tap(50f)
        p.tap(80f)
        p.cancel(100f)
        assertEquals(0.96f, p.scale(250f), 0.0001f)
        assertEquals(0.98f, p.scale(300f), 0.0001f)
        val q = Press()
        q.down(0f)
        q.cancel(50f)
        assertEquals(0.98f, q.scale(50f), 0.0001f)
        assertEquals(0.985f, q.scale(75f), 0.0001f)
        assertEquals(1f, q.scale(150f), 0.0001f)
    }

    @Test
    fun aSheetClosedWhileOpeningRunsBackFromItsValue() {
        val p = bezier(0.1, 0.9, 0.2, 1.0, 150.0 / 380)
        val o = Overlay(OverKind.Sheet)
        o.open(0)
        o.close(150)
        assertEquals((380 * p).toInt(), o.dur())
        assertEquals(p, o.sheet(150).alpha.toDouble(), 0.001)
        val half = bezier(0.1, 0.9, 0.2, 1.0, 100.0 / (380 * p).toInt())
        assertEquals(p * (1 - half), o.sheet(250).alpha.toDouble(), 0.001)
        assertEquals(0.0, o.sheet(150 + (380 * p).toLong()).alpha.toDouble(), 0.001)
        val full = Overlay(OverKind.Sheet)
        full.open(0)
        full.close(400)
        assertEquals(380, full.dur())
        assertEquals(1 - bezier(0.1, 0.9, 0.2, 1.0, 150.0 / 380), full.sheet(550).alpha.toDouble(), 0.001)
    }

    @Test
    fun aDialogClosedWhileOpeningKeepsItsScaleAndFadesFromItsOpacity() {
        val t = 120.0 / 300
        val pop = 1 - 2.0.pow(-10 * t)
        val m = t - 1
        val k = 1.70158
        val scale = 0.9 + 0.1 * (m * m * ((k + 1) * m + k) + 1)
        val d = Overlay(OverKind.Dlg)
        d.open(0)
        d.close(120)
        assertEquals(150 - Math.round(-150 / 10.0 * kotlin.math.log2(pop)).toInt(), d.dur())
        assertEquals(pop, d.dialog(120).alpha.toDouble(), 0.002)
        assertEquals(scale, d.dialog(170).scale.toDouble(), 0.0005)
        assertEquals(pop * (2.0.pow(-10 * 50.0 / 150)), d.dialog(170).alpha.toDouble(), 0.002)
        val hide = Overlay(OverKind.Dlg)
        hide.open(0)
        hide.close(400)
        assertEquals(150, hide.dur())
        assertEquals(1.0, hide.dialog(450).scale.toDouble(), 0.0)
    }

    @Test
    fun aJumpGridClosedWhileOpeningReversesOverTheTimeRun() {
        val j = Overlay(OverKind.Jump)
        j.open(0)
        j.close(150)
        assertEquals(150, j.dur())
        assertEquals(0.5f, j.jump(150), 0.0001f)
        assertEquals(0.25f, j.jump(225), 0.0001f)
        assertEquals(true, j.done(300))
        val out = Overlay(OverKind.Jump)
        out.open(0)
        out.close(300)
        assertEquals(180, out.dur())
        assertEquals(0.5f, out.jump(390), 0.0001f)
    }

    @Test
    fun aCloseOnTheOpeningFrameIsGoneAtOnce() {
        val d = Overlay(OverKind.Dlg)
        d.open(100)
        d.close(100)
        assertTrue(d.done(100))
        assertEquals(0f, d.dialog(100).alpha, 0f)
        assertEquals(0f, d.dialog(100).scrim, 0f)
    }

    @Test
    fun landingSettlesAnOpeningOverlayAndEndsAClosingOne() {
        val o = Overlay(OverKind.Sheet)
        o.open(0)
        assertTrue(o.moving(100))
        assertFalse(o.land(100))
        assertFalse(o.moving(100))
        assertEquals(1f, o.sheet(100).alpha, 0f)
        o.close(200)
        assertTrue(o.land(250))
    }
}
