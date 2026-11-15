package net.seilent.funekit.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PageChromeTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val pivots = mutableListOf<Int>()
    private var headers = 0
    private lateinit var chrome: Chrome

    private fun page(headerWins: Boolean = false, switching: Boolean = false) {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            val c = rememberChrome(v1, "artists", listOf("artists", "albums", "songs"))
            chrome = c
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                PageChrome(c, a, { rule.mainClock.currentTime }, onPivot = {
                    pivots += it
                    if (switching) c.switchTo(it, rule.mainClock.currentTime, 1)
                }, onHeader = { headers++ }, headerWins = headerWins)
            }
        }
        rule.waitForIdle()
    }

    private fun tap(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
        rule.waitForIdle()
    }

    private fun mid(e: Edges) = Offset((e.x0 + e.x1) / 2f, (e.y0 + e.y1) / 2f)

    @Test
    fun aTapOnAPivotLabelPicksIt() {
        page()
        val p = mid(chrome.pivotBox(1))
        tap(p.x, p.y)
        assertEquals(listOf(1), pivots)
        assertEquals(0, headers)
    }

    @Test
    fun aTapInTheHeaderBandAboveThePivotsTakesTheHeader() {
        page()
        tap(120f, 30f)
        assertEquals(1, headers)
        assertEquals(emptyList<Int>(), pivots)
    }

    @Test
    fun wherePivotAndHeaderMeetThePivotWinsUnlessThePageGivesItToTheHeader() {
        page()
        tap(chrome.pivotBox(0).x0 + 4f, 64f)
        assertEquals(listOf(0), pivots)
        assertEquals(0, headers)
    }

    @Test
    fun onHeaderFirstPagesTheHeaderWinsTheOverlap() {
        page(headerWins = true)
        tap(chrome.pivotBox(0).x0 + 4f, 64f)
        assertEquals(1, headers)
        assertEquals(emptyList<Int>(), pivots)
    }

    @Test
    fun aDownLightsTheLabelAndASlopMoveClearsIt() {
        page()
        val p = mid(chrome.pivotBox(2))
        rule.onRoot().performTouchInput { down(p) }
        rule.waitForIdle()
        assertEquals(2, chrome.pressed)
        rule.onRoot().performTouchInput {
            moveBy(Offset(0f, 40f))
            up()
        }
        rule.waitForIdle()
        assertNull(chrome.pressed)
        assertEquals(emptyList<Int>(), pivots)
    }

    @Test
    fun aTappedPivotStaysLitThroughTheSwitchItStartsAndItsFrameLoopEnds() {
        page(switching = true)
        rule.mainClock.autoAdvance = false
        val p = mid(chrome.pivotBox(1))
        rule.onRoot().performTouchInput {
            down(p)
            up()
        }
        rule.mainClock.advanceTimeBy(96)
        assertEquals(listOf(1), pivots)
        assertEquals(1, chrome.active)
        assertEquals(1, chrome.pressed)
        assertTrue(chrome.lit(1, rule.mainClock.currentTime))
        rule.mainClock.advanceTimeBy(192)
        assertNull(chrome.pressed)
        assertFalse(chrome.lit(1, rule.mainClock.currentTime))
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertNull(chrome.pressed)
    }
}
