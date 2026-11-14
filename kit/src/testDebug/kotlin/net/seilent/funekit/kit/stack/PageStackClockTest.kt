package net.seilent.funekit.kit.stack

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageStackClockTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val stack = PageStack<String>(MoveForms(), { PageKind.Grid }) { MoveEnds() }

    @Test
    fun theClockRunsAPushToItsEnd() {
        stack.pages.add("hub")
        rule.mainClock.autoAdvance = false
        rule.setContent { PageStackClock(stack) { MoveKind.T4 } }
        rule.runOnIdle { stack.push("album", MoveKind.T3) }
        rule.mainClock.advanceTimeBy(1000, ignoreFrameDuration = true)
        rule.runOnIdle {
            assertEquals(listOf("hub", "album"), stack.pages.toList())
            assertEquals(Phase.Rest, stack.phase)
        }
    }

    @Test
    fun androidsBackPopsThePage() {
        stack.pages.add("hub")
        stack.pages.add("album")
        rule.setContent { PageStackClock(stack) { MoveKind.T4 } }
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(1000, ignoreFrameDuration = true)
        rule.runOnIdle {
            assertEquals(listOf("hub"), stack.pages.toList())
            assertEquals(Phase.Rest, stack.phase)
        }
    }
}
