package net.seilent.funekit.screens.host

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleClockTest {
    private fun live(): IdleClock = IdleClock(0L).also { it.resumed(true, 1L) }

    @Test
    fun aNewClockIsAwayUntilItsFirstResume() {
        val c = IdleClock(5L)
        assertTrue("a new clock is away", c.away)
        c.focus(true, 6L)
        assertTrue("a focus gain alone leaves it away", c.away)
        c.resumed(true, 7L)
        assertFalse("the first resume brings it back", c.away)
        assertEquals("the resume stamps the clock", 7L, c.last)
        c.resumed(false, 8L)
        assertTrue("a pause is away", c.away)
        assertEquals("a pause stamps nothing", 7L, c.last)
    }

    @Test
    fun aPointerUpKeepsTheFingerHeld() {
        val c = live()
        c.touch(MotionEvent.ACTION_DOWN, 2L)
        c.touch(MotionEvent.ACTION_POINTER_DOWN, 3L)
        c.touch(MotionEvent.ACTION_POINTER_UP, 4L)
        assertTrue("a pointer up keeps the finger down", c.held)
        c.touch(MotionEvent.ACTION_MOVE, 5L)
        assertTrue(c.held)
        c.touch(MotionEvent.ACTION_UP, 6L)
        assertFalse("the last up frees it", c.held)
        c.touch(MotionEvent.ACTION_DOWN, 7L)
        c.touch(MotionEvent.ACTION_CANCEL, 8L)
        assertFalse("a cancel frees it", c.held)
        assertEquals("every action stamps the clock", 8L, c.last)
    }

    @Test
    fun aKeyHoldsUntilItsUpAndEveryKeyStamps() {
        val c = live()
        c.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 2L)
        c.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 3L)
        c.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 4L)
        assertTrue("a key still down holds", c.held)
        c.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 5L)
        assertFalse(c.held)
        c.moved(6L)
        assertEquals("a generic motion stamps the clock", 6L, c.last)
    }

    @Test
    fun aFocusLossClearsAHeldFingerAndKey() {
        val c = live()
        c.touch(MotionEvent.ACTION_DOWN, 2L)
        c.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 3L)
        assertTrue("premise: held", c.held)
        c.focus(false, 4L)
        assertTrue(c.away)
        assertFalse("the focus loss drops the finger and the key, whose ups go to another window", c.held)
        c.focus(true, 9L)
        assertFalse(c.away)
        assertFalse("nothing is held after the gain", c.held)
        assertEquals("the gain stamps the clock", 9L, c.last)
    }

    @Test
    fun aScreenOffIsAwayAndItsWakeStamps() {
        val c = live()
        c.touch(MotionEvent.ACTION_DOWN, 2L)
        c.screen(false, 3L)
        assertTrue("a screen off is away", c.away)
        assertFalse("and drops the finger", c.held)
        assertEquals("the off stamps nothing", 2L, c.last)
        c.screen(true, 30L)
        assertFalse("the screen on brings it back", c.away)
        assertEquals("the wake stamps the clock", 30L, c.last)
    }

    @Test
    fun aReturnIsSpentUntilTheNextInput() {
        val c = live()
        c.returnedAt(20L)
        assertEquals(20L, c.returned)
        assertTrue("a return is spent", c.spent)
        c.stamp(21L)
        assertFalse("the next input arms it again", c.spent)
        assertEquals("the return time stays", 20L, c.returned)
    }
}
