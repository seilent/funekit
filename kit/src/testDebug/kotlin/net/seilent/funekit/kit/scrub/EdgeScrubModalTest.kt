package net.seilent.funekit.kit.scrub

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
import net.seilent.funekit.kit.touch.BlockBelow
import net.seilent.funekit.scale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EdgeScrubModalTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val got = mutableListOf<String>()

    private fun scrubUnder(modal: Int, layer: Int) {
        rule.setContent {
            val a = remember { Arbiter(v1) }
            BlockBelow(a, modal)
            Box(Modifier.fillMaxSize().edgeScrub(30f, 25f, listOf("a", "b", "c", "d"), a, layer) { got += it })
        }
        rule.onRoot().performTouchInput {
            down(Offset(width - 10f, centerY))
            moveBy(Offset(0f, 10f))
            moveBy(Offset(0f, 30f))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun aDownUnderAHigherModalDoesNotScrub() {
        scrubUnder(modal = 1, layer = 0)
        assertTrue(got.isEmpty())
    }

    @Test
    fun aDownAtTheModalsLayerScrubs() {
        scrubUnder(modal = 1, layer = 1)
        assertTrue(got.isNotEmpty())
    }
}
