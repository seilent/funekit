package net.seilent.funekit.kit.scrub

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EdgeScrubTest {
    @get:Rule
    val rule = createComposeRule()

    private val got = mutableListOf<String>()
    private var taps = 0

    private fun content(scrub: @Composable Modifier.() -> Modifier = { edgeScrub(30f, 25f, listOf("a", "b", "c", "d")) { got += it } }) {
        rule.setContent {
            Box(
                Modifier.fillMaxSize()
                    .scrub()
                    .pointerInput(Unit) { detectTapGestures { taps++ } },
            )
        }
    }

    @Test
    fun aTapInTheBandPassesThrough() {
        content()
        rule.onRoot().performTouchInput {
            down(Offset(width - 10f, centerY))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, taps)
        assertTrue(got.isEmpty())
    }

    @Test
    fun aVerticalMovePastTheSlopScrubsAndIsNoTap() {
        content()
        rule.onRoot().performTouchInput {
            down(Offset(width - 10f, centerY))
            moveBy(Offset(0f, 10f))
            moveBy(Offset(0f, 30f))
            up()
        }
        rule.waitForIdle()
        assertEquals(0, taps)
        assertTrue(got.isNotEmpty())
    }

    @Test
    fun aHorizontalMoveFromTheBandIsLeftAlone() {
        content()
        rule.onRoot().performTouchInput {
            down(Offset(width - 10f, centerY))
            moveBy(Offset(-40f, 2f))
            up()
        }
        rule.waitForIdle()
        assertTrue(got.isEmpty())
    }

    @Test
    fun onTheP2160PageTheBandAndTheSlopKeepTheFitPx() {
        val none = Insets(0, 0, 0, 0)
        val dpi = Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)
        val p2160 = scale(Window(1080, 2160, 35, none, none, none, none, none, dpi), Nav.None, Surface.Page, false)
        content { edgeScrub(p2160, listOf("a", "b", "c", "d")) { got += it } }
        rule.onRoot().performTouchInput {
            down(Offset(width - 90f, centerY))
            moveBy(Offset(0f, 100f))
            up()
        }
        rule.waitForIdle()
        assertTrue(got.isEmpty())
        rule.onRoot().performTouchInput {
            down(Offset(width - 90f, centerY - 100f))
            moveBy(Offset(0f, 10f))
            moveBy(Offset(0f, 110f))
            up()
        }
        rule.waitForIdle()
        assertTrue(got.isNotEmpty())
    }
}
