package net.seilent.funekit.kit.lock

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class LockDragTest {
    private val ease = Tok.motion_lockCommit.toLong()

    private fun drag(lock: LockDrag, dyU: Float, steps: Int, stepMs: Long, at: Long = 0L): Long {
        val x = X_U
        val y0 = Y0_U
        lock.down(y0 * PPU, x, y0, at, at)
        var t = at
        for (i in 1..steps) {
            t += stepMs
            val y = y0 - dyU * i / steps
            lock.move(y * PPU, y, t)
        }
        lock.up((y0 - dyU) * PPU, x, y0 - dyU, t, t)
        return t
    }

    @Test
    fun aSlowDragPastHalfTheHeightCommitsAndEasesToTheTop() {
        val lock = LockDrag(H)
        val up = drag(lock, H * 0.6f / PPU, SLOW_STEPS, SLOW_STEP_MS)
        assertTrue("funeOS player/ui/lock.cpp LockPage::up :259, v0.4.4", lock.committing)
        assertTrue(lock.moving(up + 1))
        assertEquals(H, lock.offset(up + ease))
        assertFalse(lock.moving(up + ease))
        assertTrue(lock.landed(up + ease))
    }

    @Test
    fun aSlowDragUnderHalfSpringsBackToZeroOverTheCommitTime() {
        val lock = LockDrag(H)
        val dy = H * 0.4f / PPU
        val up = drag(lock, dy, SLOW_STEPS, SLOW_STEP_MS)
        assertFalse(lock.committing)
        assertEquals((dy * PPU).roundToInt(), lock.offset(up))
        assertTrue(lock.moving(up + 1))
        assertEquals(0, lock.offset(up + ease))
        assertFalse(lock.moving(up + ease))
        assertFalse(lock.landed(up + ease))
    }

    @Test
    fun aShortFastUpSwipeCommits() {
        val lock = LockDrag(H)
        drag(lock, SWIPE_U, FAST_STEPS, FAST_STEP_MS)
        assertTrue("past gesture.swipe 45 u at or past flingMin 50 u/s (funeOS player/ui/lock.cpp LockPage::up :256-257, v0.4.4)", lock.committing)
    }

    @Test
    fun aShortSlowUpDragSpringsBack() {
        val lock = LockDrag(H)
        drag(lock, SWIPE_U, SLOW_STEPS, SLOW_STEP_MS)
        assertFalse("under flingMin the swipe term is false", lock.committing)
    }

    @Test
    fun aDragDownKeepsZeroAndADragPastTheTopKeepsTheHeight() {
        val lock = LockDrag(H)
        lock.down(Y0_U * PPU, X_U, Y0_U, 0L, 0L)
        lock.move((Y0_U + DOWN_U) * PPU, Y0_U + DOWN_U, FAST_STEP_MS)
        assertEquals(0, lock.offset(FAST_STEP_MS))
        lock.move(-H.toFloat() * PPU, -H.toFloat(), FAST_STEP_MS * 2)
        assertEquals(H, lock.offset(FAST_STEP_MS * 2))
    }

    @Test
    fun aDownDuringTheCommitEaseHoldsTheEasedOffsetAndClearsTheCommit() {
        val lock = LockDrag(H)
        val up = drag(lock, H * 0.6f / PPU, SLOW_STEPS, SLOW_STEP_MS)
        val at = up + CATCH_MS
        val eased = lock.offset(at)
        assertTrue("premise: the ease is between its ends", eased in 1 until H)
        lock.down(Y0_U * PPU, X_U, Y0_U, at, at)
        assertFalse(lock.committing)
        assertTrue(lock.dragging)
        assertEquals(eased, lock.offset(at + CATCH_MS))
    }

    @Test
    fun theEaseRunsOnExpOut6() {
        val lock = LockDrag(H)
        val dy = H * 0.6f / PPU
        val up = drag(lock, dy, SLOW_STEPS, SLOW_STEP_MS)
        val from = Y0_U * PPU - (Y0_U - dy) * PPU
        val want = lerp(from, H.toFloat(), Curves.expOut6.at(CURVE_MS.toFloat() / ease)).roundToInt()
        assertEquals("ExpOut6 over motion.lockCommit (funeOS player/ui/lock.cpp LockPage::up :261, v0.4.4)", want, lock.offset(up + CURVE_MS))
    }

    @Test
    fun theSheetFadesToTheSliverAfterItsDelay() {
        val lock = LockDrag(H)
        val up = drag(lock, H * 0.6f / PPU, SLOW_STEPS, SLOW_STEP_MS)
        val delay = Tok.motion_lockSliverDelay.toLong()
        val d = Tok.motion_lockSliver.toLong()
        assertEquals(1f, lock.sheetAlpha(up + delay - 1), 0f)
        assertEquals(lerp(1f, Tok.motion_lockSliverAlpha, 0.5f), lock.sheetAlpha(up + delay + d / 2), EPS)
        assertEquals(Tok.motion_lockSliverAlpha, lock.sheetAlpha(up + delay + d), 0f)
        val spring = LockDrag(H)
        val back = drag(spring, H * 0.4f / PPU, SLOW_STEPS, SLOW_STEP_MS)
        assertEquals("a spring back keeps the sheet whole", 1f, spring.sheetAlpha(back + delay + d), 0f)
    }

    @Test
    fun restSetsZeroAtOnce() {
        val lock = LockDrag(H)
        val up = drag(lock, H * 0.6f / PPU, SLOW_STEPS, SLOW_STEP_MS)
        lock.rest()
        assertEquals(0, lock.offset(up + 1))
        assertFalse(lock.moving(up + 1))
        assertFalse(lock.committing)
    }

    private companion object {
        const val H = 1280
        const val PPU = 3f
        const val X_U = 120f
        const val Y0_U = 400f
        const val SWIPE_U = 60f
        const val DOWN_U = 30f
        const val SLOW_STEPS = 400
        const val SLOW_STEP_MS = 16L
        const val FAST_STEPS = 3
        const val FAST_STEP_MS = 16L
        const val CATCH_MS = 200L
        const val CURVE_MS = 100L
        const val EPS = 1e-4f
    }
}
