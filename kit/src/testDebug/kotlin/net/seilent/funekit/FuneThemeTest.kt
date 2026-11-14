package net.seilent.funekit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FuneThemeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun touchValuesComeFromTheTokens() {
        val none = Insets(0, 0, 0, 0)
        val s = scale(Window(720, 1280, 28, none, Insets(0, 0, 0, 108), none, none, none), Nav.Buttons, Surface.Page, false)
        var slop = -1f
        var hold = -1L
        var target = DpSize.Unspecified
        var tap = -1L
        var tapMin = -1L
        var overscroll: Any? = Unit
        var indication: Any? = null
        var fontScale = -1f
        var dir: LayoutDirection? = null
        rule.setContent {
            FuneTheme(s) {
                val vc = LocalViewConfiguration.current
                slop = vc.touchSlop
                hold = vc.longPressTimeoutMillis
                target = vc.minimumTouchTargetSize
                tap = vc.doubleTapTimeoutMillis
                tapMin = vc.doubleTapMinTimeMillis
                overscroll = LocalOverscrollFactory.current
                indication = LocalIndication.current
                fontScale = LocalDensity.current.fontScale
                dir = LocalLayoutDirection.current
            }
        }
        rule.waitForIdle()
        assertEquals(75f, slop)
        assertEquals(400L, hold)
        assertEquals(DpSize.Zero, target)
        assertEquals(0L, tap)
        assertEquals(0L, tapMin)
        assertNull(overscroll)
        assertSame(NoIndication, indication)
        assertEquals(1f, fontScale)
        assertEquals(LayoutDirection.Ltr, dir)
    }
}
