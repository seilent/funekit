package net.seilent.funekit.kit

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RememberLookTest {
    @get:Rule
    val rule = createComposeRule()

    private val input = mutableIntStateOf(0)

    @Test
    fun aRecompositionWithANewCaptureChangesTheLookItRemembered() {
        var lit by mutableStateOf(false)
        val seen = ArrayList<State<Boolean>>()
        rule.setContent {
            val captured = lit
            seen += rememberLook(covered = false) { captured }
        }
        rule.waitForIdle()
        assertEquals(false, seen.last().value)
        lit = true
        rule.waitForIdle()
        assertTrue(seen.size > 1)
        assertSame(seen.first(), seen.last())
        assertEquals(true, seen.last().value)
    }

    private fun pageStack() = PageStack<String>(MoveForms(), { PageKind.Plain }) { MoveEnds() }.also { it.pages.add("hub") }

    private fun frozen(s: PageStack<String>): Boolean = s.overlay?.kind.let { it == OverKind.Sheet || it == OverKind.Dlg }

    private fun keyed(s: PageStack<String>, look: () -> Int): ArrayList<State<Int>> {
        val seen = ArrayList<State<Int>>()
        rule.setContent { seen += rememberLook(s.hideFocus, s.snap, covered = frozen(s), look = look) }
        rule.waitForIdle()
        return seen
    }

    private fun step(s: PageStack<String>, now: Long, act: () -> Unit) {
        rule.runOnIdle {
            act()
            s.frame(now)
        }
        rule.waitForIdle()
    }

    @Test
    fun aLookFrozenAtADialogOpenReturnsTheHiddenForm() {
        val s = pageStack()
        val form = { if (s.hideFocus) 0 else 1 }
        val plain = ArrayList<State<Int>>()
        val look = ArrayList<State<Int>>()
        rule.setContent {
            val covered = frozen(s)
            plain += rememberLook(covered = covered, look = form)
            look += rememberLook(s.hideFocus, s.snap, covered = covered, look = form)
        }
        rule.waitForIdle()
        assertEquals(1, plain.last().value)
        assertEquals(1, look.last().value)
        step(s, 0L) { s.open(Overlay(OverKind.Dlg), hides = true) }
        assertTrue(frozen(s))
        assertEquals(1, plain.last().value)
        assertEquals(0, look.last().value)
    }

    @Test
    fun aSnapBumpUpdatesAFrozenLook() {
        val s = pageStack()
        input.intValue = 1
        val look = keyed(s) { input.intValue }
        step(s, 0L) { s.open(Overlay(OverKind.Sheet)) }
        assertEquals(1, look.last().value)
        rule.runOnIdle { input.intValue = 2 }
        rule.waitForIdle()
        assertEquals(1, look.last().value)
        step(s, STEP) { s.pick() }
        assertEquals(2, look.last().value)
    }

    @Test
    fun aPickerSwapLeavesTheFrozenLook() {
        val s = pageStack()
        input.intValue = 1
        val look = keyed(s) { input.intValue }
        step(s, 0L) { s.open(Overlay(OverKind.Sheet)) }
        assertEquals(1, look.last().value)
        rule.runOnIdle { input.intValue = 2 }
        rule.waitForIdle()
        var swapped = false
        step(s, STEP) { s.swapMenu { swapped = true } }
        assertTrue(swapped)
        assertTrue(frozen(s))
        assertEquals(1, look.last().value)
    }

    companion object {
        private const val STEP = 16L
    }
}
