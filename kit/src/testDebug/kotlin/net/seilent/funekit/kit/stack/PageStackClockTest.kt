package net.seilent.funekit.kit.stack

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.overlay.VolumeHud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun aPredictiveBackIsLiveFromItsStartToItsEndOrCancel() {
        stack.pages.add("hub")
        stack.pages.add("album")
        rule.setContent { PageStackClock(stack) { MoveKind.T4 } }
        val d = rule.activity.onBackPressedDispatcher
        rule.runOnIdle { d.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)) }
        rule.runOnIdle { assertTrue("the gesture is live", stack.backing) }
        rule.runOnIdle { d.dispatchOnBackCancelled() }
        rule.runOnIdle {
            assertFalse("a cancel ends it", stack.backing)
            assertEquals("a cancel takes no back", listOf("hub", "album"), stack.pages.toList())
        }
        rule.runOnIdle { d.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)) }
        rule.runOnIdle { assertTrue("premise: a second gesture is live", stack.backing) }
        rule.runOnIdle { d.onBackPressed() }
        rule.mainClock.advanceTimeBy(1000, ignoreFrameDuration = true)
        rule.runOnIdle {
            assertFalse("its end ends it", stack.backing)
            assertEquals(listOf("hub"), stack.pages.toList())
        }
    }

    @Test
    fun theClockDropsAVolumeOverlayOnceTheHudFadesOutOnItsOwn() {
        stack.pages.add("hub")
        val hud = VolumeHud()
        rule.mainClock.autoAdvance = false
        rule.setContent { PageStackClock(stack) { MoveKind.T4 } }
        rule.runOnIdle {
            hud.show(stack.clock, 50, true, "artist", "title")
            stack.open(Overlay(OverKind.Volume, hud))
        }
        rule.mainClock.advanceTimeBy(1000, ignoreFrameDuration = true)
        rule.runOnIdle { assertNotNull(stack.overlay) }
        rule.mainClock.advanceTimeBy(282L + 2027L + 650L + 267L, ignoreFrameDuration = true)
        rule.runOnIdle { assertNull(stack.overlay) }
    }
}
