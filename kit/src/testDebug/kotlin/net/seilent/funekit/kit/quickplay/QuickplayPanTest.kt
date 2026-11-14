package net.seilent.funekit.kit.quickplay

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.FuneTheme
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.home.HomeGeo
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.kit.touch.rememberListTouch
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import net.seilent.funekit.kit.touch.Kind as TouchKind

private const val HOME_ITEMS = 5

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickplayPanTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Home, false)
    private val geo = HomeGeo(s, HOME_ITEMS)
    private val menu = HomeMenu(HOME_ITEMS)
    private val qp = QuickplayState()
    private val toast = Toast()
    private val l = QpLayout(s, qpTiles(QpData(null, items(3, QpKind.Album), items(10, QpKind.Song), items(12, QpKind.Album))), 3)
    private lateinit var home: ListTouch
    private lateinit var qpTouch: ListTouch
    private var front by mutableStateOf(false)

    private fun items(n: Int, kind: QpKind) = List(n) { QpItem(kind, it.toLong(), "t$it", null) }

    private fun layers() {
        rule.setContent {
            FuneTheme(s) {
                val a = remember { Arbiter(s) }
                home = rememberListTouch(TouchKind.List, s)
                qpTouch = rememberListTouch(TouchKind.Quickplay, s)
                Box(Modifier.pxSize(s.widthPx, s.heightPx).arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                    if (front) {
                        FuneList(a, qpTouch, Modifier.pxSize(s.widthPx, s.heightPx)) {
                            item { Box(Modifier.pxSize(s.widthPx, l.contentPx)) }
                        }
                    } else {
                        FuneList(a, home, Modifier.pxSize(s.widthPx, s.heightPx)) {
                            items(200) { Box(Modifier.pxSize(s.widthPx, s.heightPx / 8)) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aPanStartStopsAHomeFlingWithNoTouch() {
        layers()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(0f, -60f), delayMillis = 8) }
            up()
        }
        rule.mainClock.advanceTimeBy(48)
        assertTrue(home.scroll.flinging)
        rule.runOnUiThread { qpPanStart(false, geo, menu, home, qp, qpTouch, l, toast) }
        val index = home.state.firstVisibleItemIndex
        val offset = home.state.firstVisibleItemScrollOffset
        rule.mainClock.advanceTimeBy(160)
        assertFalse(home.scroll.flinging)
        assertFalse(home.moving)
        assertEquals(index, home.state.firstVisibleItemIndex)
        assertEquals(offset, home.state.firstVisibleItemScrollOffset)
    }

    @Test
    fun theFirstFrontFrameDrawsTheFocusSpanAskedForTheWayIn() {
        layers()
        qp.focus = l.count - 1
        var first = -1L
        rule.runOnUiThread {
            qpPanStart(true, geo, menu, home, qp, qpTouch, l, toast)
            front = true
            first = qpOff(qpTouch, qp, l)
        }
        assertEquals(44924930L, first)
        rule.waitForIdle()
        assertEquals(686, qpTouch.state.firstVisibleItemScrollOffset)
        assertEquals(44924930L, qpOff(qpTouch, qp, l))
    }

    @Test
    fun aPanStartHidesTheToast() {
        layers()
        toast.show(0L, "artist", "title")
        assertTrue(toast.visible(0L))
        rule.runOnUiThread { qpPanStart(false, geo, menu, home, qp, qpTouch, l, toast) }
        assertFalse(toast.visible(0L))
    }
}
