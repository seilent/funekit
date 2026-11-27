package net.seilent.funekit.kit.lock

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Bidi

class LockLineTest {
    private val ltr = Bidi.DIRECTION_LEFT_TO_RIGHT
    private val d = Tok.motion_valueChange.toLong()

    @Test
    fun aNewLineShowsTheOldOneDippingThenTheNewOneRising() {
        val l = LockLine()
        l.set("first", ltr, false, 0L)
        l.cut()
        l.set("second", ltr, false, AT)
        assertEquals("the old line until valueChange (funeOS player/ui/lock.cpp LockPage::render :182, v0.4.4)", "first", l.shown(AT + d / 2).text)
        assertEquals(lerp(1f, Tok.motion_valueDip, 0.5f), l.alpha(AT + d / 2), EPS)
        assertEquals("second", l.shown(AT + d + d / 2).text)
        assertEquals(lerp(Tok.motion_valueDip, 1f, 0.5f), l.alpha(AT + d + d / 2), EPS)
        assertEquals(1f, l.alpha(AT + d * 2), 0f)
        assertFalse(l.moving(AT + d * 2))
    }

    @Test
    fun aDrawBeforeTheChangesTimeShowsTheOldLineAtItsAlpha() {
        val l = LockLine()
        l.set("first", ltr, false, 0L)
        l.cut()
        l.set("second", ltr, false, AT)
        assertEquals("first", l.shown(AT - 1).text)
        assertEquals("a frame older than the change dips nothing", 1f, l.alpha(AT - 1), 0f)
    }

    @Test
    fun theSameLineStartsNoFade() {
        val l = LockLine()
        l.set("line", ltr, false, 0L)
        l.cut()
        l.set("line", ltr, false, AT)
        assertEquals(1f, l.alpha(AT + 1), 0f)
        assertFalse(l.moving(AT + 1))
    }

    @Test
    fun anEmptyLineSetAgainInAnotherDirectionStartsNoFade() {
        val l = LockLine()
        l.set("line", ltr, false, 0L)
        l.set("", ltr, false, AT)
        assertTrue("premise: the line leaving fades", l.moving(AT + 1))
        val later = AT + d * 3
        l.set("", Bidi.DIRECTION_RIGHT_TO_LEFT, true, later)
        assertFalse("an empty line restarts only on a change of text (funeOS player/ui/lock.cpp LockPage::set_lyric :59-60, v0.4.4)", l.moving(later + 1))
    }

    @Test
    fun aCutDrawsTheNewLineWholeAtOnce() {
        val l = LockLine()
        l.set("first", ltr, false, 0L)
        l.set("second", ltr, false, AT)
        l.cut()
        assertEquals("a show cuts the fade (funeOS player/ui/lock.cpp LockPage::show :23, v0.4.4)", "second", l.shown(AT + 1).text)
        assertEquals(1f, l.alpha(AT + 1), 0f)
        assertFalse(l.moving(AT + 1))
    }

    @Test
    fun aDirectionChangeOfTheSameTextFades() {
        val l = LockLine()
        l.set("line", ltr, false, 0L)
        l.cut()
        l.set("line", Bidi.DIRECTION_RIGHT_TO_LEFT, true, AT)
        assertTrue(l.moving(AT + 1))
        assertTrue(l.text.right)
    }

    private companion object {
        const val AT = 1000L
        const val EPS = 1e-4f
    }
}
