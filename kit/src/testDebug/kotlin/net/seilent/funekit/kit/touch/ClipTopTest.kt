package net.seilent.funekit.kit.touch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Layout
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h800dp-mdpi")
class ClipTopTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val r1 = scale(Window(480, 800, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val top = px(r1.board.page_contentTop, r1.ppu)
    private val view = px(r1.stdContentBottom, r1.stdPpu) - top
    private lateinit var arb: Arbiter
    private lateinit var clipped: ListTouch
    private lateinit var plain: ListTouch
    private val taps = HashMap<Int, Tap>()

    private fun rowTap(i: Int): Tap = taps.getOrPut(i) { Tap(onPress = {}, onCancel = {}, onTap = {}) }

    private fun show(up: Int = UP) {
        rule.setContent {
            val a = remember { Arbiter(r1) }
            arb = a
            clipped = rememberListTouch(Kind.List, r1, rows = RowSpec(PITCH, view, Layout.above(r1)))
            plain = rememberListTouch(Kind.List, r1, rows = RowSpec(PITCH, view))
            val counted = remember { booleanArrayOf(false) }
            SideEffect {
                if (!counted[0]) {
                    clipped.count(ROWS, 0)
                    plain.count(ROWS, 0)
                    counted[0] = true
                }
            }
            Box(Modifier.fillMaxSize().arbiter(a, Swipes(edges = false, bottom = false, turn = false, acts = { false }, onSwipe = { _, _ -> }))) {
                androidx.compose.foundation.Canvas(Modifier.pxSize(480, 800)) { drawRect(Ink.bg) }
                Row {
                    Column {
                        Box(Modifier.pxSize(HALF, top))
                        FuneList(a, clipped, Modifier.pxSize(HALF, view), rowTap = { rowTap(it) }) {
                            items(ROWS) { androidx.compose.foundation.Canvas(Modifier.pxSize(HALF, PITCH)) { drawRect(Ink.white) } }
                        }
                    }
                    Column {
                        Box(Modifier.pxSize(HALF, top))
                        FuneList(a, plain, Modifier.pxSize(HALF, view)) {
                            items(ROWS) { androidx.compose.foundation.Canvas(Modifier.pxSize(HALF, PITCH)) { drawRect(Ink.white) } }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle {
            clipped.toPx(up)
            plain.toPx(up)
        }
        rule.runOnIdle { Snapshot.sendApplyNotifications() }
        rule.waitForIdle()
    }

    private fun shot(): Bitmap {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
        return b
    }

    @Test
    fun aRowScrolledUpOnR1DrawsItsTopIntoTheBandAboveTheListTop() {
        show()
        assertEquals(240, top)
        assertEquals(UP, clipped.px)
        val b = shot()
        val white = Ink.white.toArgb()
        val bg = Ink.bg.toArgb()
        for (y in top - UP until top) {
            assertEquals("clip top row y $y", white, b.getPixel(HALF / 2, y))
            assertEquals("content top row y $y", bg, b.getPixel(HALF + HALF / 2, y))
        }
        assertEquals("above the scrolled row", bg, b.getPixel(HALF / 2, top - UP - 1))
        assertEquals("below the list top", white, b.getPixel(HALF / 2, top + 1))
    }

    @Test
    fun aRowScrolledPastTheClipTopIsCutThereOnR1() {
        show(PAST)
        assertEquals(PAST, clipped.px)
        val b = shot()
        val white = Ink.white.toArgb()
        val bg = Ink.bg.toArgb()
        val clip = top - Layout.above(r1)
        assertEquals(203, clip)
        assertEquals("above the clip top", bg, b.getPixel(HALF / 2, 190))
        assertEquals("the last row above the clip top", bg, b.getPixel(HALF / 2, clip - 1))
        assertEquals("the clip top", white, b.getPixel(HALF / 2, clip))
        assertEquals("between the clip top and the list top", white, b.getPixel(HALF / 2, 210))
    }

    @Test
    fun aTapAboveTheListTopTakesNoRowOnR1() {
        show()
        rule.runOnIdle {
            assertNull(arb.tapAt(Offset((HALF / 2).toFloat(), 230f)))
            assertSame(rowTap(0), arb.tapAt(Offset((HALF / 2).toFloat(), 241f)))
        }
    }

    companion object {
        private const val HALF = 240
        private const val PITCH = 106
        private const val ROWS = 30
        private const val UP = 20
        private const val PAST = 60
    }
}
