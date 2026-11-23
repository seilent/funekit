package net.seilent.funekit.kit.motion

import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.roundToInt

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
    fun t12At150RunsTheRiseFadeAndScrimOnTheirOwnLegs() {
        val s = Overlay(OverKind.Sheet).also { it.open(0) }.sheet(150)
        assertEquals(0.55f * Curves.easeOut.at(150f / 288), s.scrim, 0.0001f)
        assertEquals(1 - Curves.easeOut.at(150f / 264), s.dy, 0.0001f)
        assertEquals(Curves.easeOut.at(150f / 196), s.alpha, 0.0001f)
        assertEquals(288, Overlay(OverKind.Sheet).dur())
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
    fun t14RisesAPitchAndFadesInAfterItsDelayAndClosesUpward() {
        val o = Overlay(OverKind.Jump).also { it.open(0) }
        assertEquals(206, o.dur())
        val l = o.jumpLook(150)
        assertEquals(Curves.easeOut.at(144f / 200), l.alpha, 0.0001f)
        assertEquals((1 - Curves.easeOut.at(144f / 166)) * Tok.jumpPitch, l.dyU, 0.0001f)
        assertEquals(0f, l.page, 0.0001f)
        assertEquals(0f, o.jumpLook(6).alpha, 0f)
        val out = Overlay(OverKind.Jump)
        out.open(0)
        out.close(300)
        assertEquals(191, out.dur())
        assertEquals(1 - Curves.cubicOut.at(0.5f), out.jump(300 + 69), 0.0001f)
        assertEquals(-86f * Curves.easeOut.at(0.5f), out.jumpLook(300 + 84).dyU, 0.0001f)
        assertEquals(0f, out.jumpLook(300 + 86).page, 0.0001f)
        assertEquals(0.5f, out.jumpLook(300 + 86 + 52).page, 0.01f)
    }

    @Test
    fun t5At308IsTheRestFrame() {
        for (dir in listOf(-1, 1)) {
            val p = pivotAt(308f, dir)
            assertEquals(0f, p.stripDx, 0.0001f)
            assertEquals(1f, p.stripAlpha, 0.0001f)
            assertEquals(0f, p.rowsDx, 0.0001f)
            assertEquals(1f, p.rowsAlpha, 0.0001f)
        }
        val out = pivotAt(25f, 1)
        assertEquals(false, out.rowsIn)
        assertEquals(1 - 25f / 133, out.rowsAlpha, 0.0001f)
        assertEquals(-450f * 25 / 1000, out.rowsDx, 0.0001f)
        val into = pivotAt(133f + 87.5f, -1)
        assertEquals(true, into.rowsIn)
        assertEquals(-41f * (1 - Curves.easeOut.at(0.5f)), into.rowsDx, 0.0001f)
        assertEquals(0.5f, into.rowsAlpha, 0.0001f)
    }

    @Test
    fun aDraggedPaneFollowsTheFingerAndAReleaseLandsItOnTheStripCurve() {
        val m = PivotMotion()
        m.drag(30f)
        assertTrue(m.dragging)
        assertTrue(m.active(0))
        assertEquals(90, m.dx(0, 3f))
        assertEquals(1f, m.alpha(0), 0f)
        m.release(1000)
        assertFalse(m.dragging)
        assertEquals(90, m.dx(1000, 3f))
        assertEquals((30f * (1 - Curves.expOut6.at(0.5f)) * 3).roundToInt(), m.dx(1118, 3f))
        assertEquals(0, m.dx(1236, 3f))
        assertFalse(m.active(1236))
    }

    @Test
    fun aSwitchFromADragCarriesItsOffsetOutAtThePaneSpeed() {
        val m = PivotMotion()
        m.begin(0, 1, 20f)
        assertEquals(60, m.dx(0, 3f))
        assertEquals(((20f - 450f * 100 / 1000) * 3).roundToInt(), m.dx(100, 3f))
        assertFalse(m.entering(132))
        assertTrue(m.entering(133))
        assertEquals((41f * 3).roundToInt(), m.dx(133, 3f))
        assertEquals(0, m.dx(133 + 175, 3f))
        assertTrue(m.active(307))
        assertFalse(m.active(308))
        assertFalse(m.thumbIn(100))
        assertTrue(m.thumbIn(200))
        m.prepare(7L)
        assertTrue(m.prepared(7L))
        assertFalse(m.prepared(8L))
        m.end()
        assertFalse(m.active(0))
    }

    @Test
    fun t4MorphAt200() {
        val m = 1 - 212.0 / 400
        assertEquals(1 - m * m * m, Curves.cubicOut.at(212f / 400).toDouble(), 0.0001)
        assertEquals(0.8962f, Curves.cubicOut.at(212f / 400), 0.0001f)
    }

    private fun cexp(x: Double): Double {
        val r = x / CEXP_SCALE
        var term = 1.0
        var sum = 1.0
        for (i in 1 until CEXP_TERMS) {
            term *= r / i
            sum += term
        }
        repeat(CEXP_SQUARES) { sum *= sum }
        return sum
    }

    @Test
    fun expOut6IsTheFuneOsTable() {
        for (i in 0..SEG) {
            val t = i.toDouble() / SEG
            val q = (1 - (cexp(6 * (1 - t)) - 1) / (cexp(6.0) - 1)) * FX
            val want = (if (q < 0) q - 0.5 else q + 0.5).toInt()
            assertEquals("entry $i", want, (Curves.expOut6.at(i.toFloat() / SEG) * FX).roundToInt())
        }
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
        assertEquals(1 - 0.04f * 2 / 16, p.draw(515f), 0.0001f)
    }

    @Test
    fun aShownPressIsStaleWhileItMovesAndNotOnceItsLegEnded() {
        val p = Press().also { it.scaled = true }
        p.down(0f)
        p.draw(50f)
        assertTrue(p.stale(50f))
        p.tap(100f)
        p.draw(350f)
        assertTrue(p.stale(350f))
        p.draw(500f)
        assertFalse(p.stale(500f))
    }

    @Test
    fun everyPressMutatorBumpsTheRevision() {
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
    fun aSheetClosedWhileOpeningRunsEachLegBackOnItsShare() {
        val from = sheetIn(150)
        val o = Overlay(OverKind.Sheet)
        o.open(0)
        o.close(150)
        val fade = (188 * from.alpha).toInt()
        assertEquals(maxOf((176 * from.rise).toInt(), fade, (220 * from.scrim).toInt()), o.dur())
        assertEquals(from.alpha, o.sheet(150).alpha, 0.0001f)
        assertEquals(from.alpha * (1 - Curves.smoothStep.at(50f / fade)), o.sheet(200).alpha, 0.0001f)
        assertEquals(0f, o.sheet(150 + o.dur().toLong()).alpha, 0.0001f)
        val full = Overlay(OverKind.Sheet)
        full.open(0)
        full.close(400)
        assertEquals(220, full.dur())
        assertEquals(0.5f, full.sheet(400 + 94).alpha, 0.0001f)
        assertEquals(94f / 176, full.sheet(400 + 94).dy, 0.0001f)
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
    fun aJumpGridClosedWhileOpeningClosesFromItsLook() {
        val from = jumpIn(100)
        val j = Overlay(OverKind.Jump)
        j.open(0)
        j.close(100)
        assertEquals(191, j.dur())
        assertEquals(from.alpha, j.jump(100), 0.0001f)
        assertEquals(from.dyU, j.jumpLook(100).dyU, 0.0001f)
        assertEquals(from.alpha * (1 - Curves.cubicOut.at(0.5f)), j.jump(100 + 69), 0.0001f)
        assertTrue(j.done(100 + 191))
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

    @Test
    fun atReadsThePressWithNoShownStateAndEqualsTheNextLook() {
        val p = Press()
        p.down(0f)
        val now = SETTLED
        assertFalse(p.moving(now))
        assertTrue(p.stale(now))
        val a = p.at(now)
        assertEquals(a, p.at(now))
        assertTrue(p.stale(now))
        assertEquals(1f, a.lit, 0f)
        assertEquals(1f, a.ring, 0f)
        assertTrue(a.framed)
        val f = p.draw(now)
        assertEquals(a.factor, f, 0f)
        assertEquals(a.lit, p.look(now), 0f)
        assertFalse(p.stale(now))
    }

    private companion object {
        const val SETTLED = 10000f
        const val SEG = 512
        const val FX = 65536f
        const val CEXP_SCALE = 1024.0
        const val CEXP_TERMS = 12
        const val CEXP_SQUARES = 10
    }
}
