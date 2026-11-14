package net.seilent.funekit.kit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w240dp-h320dp-mdpi")
class ThumbLayerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val fade = ThumbFade()
    private val rest = ThumbRect(X0, 0, W, LEN)
    private var now by mutableLongStateOf(0L)
    private var covered by mutableStateOf(false)
    private var snap by mutableIntStateOf(0)
    private var follow by mutableStateOf(false)
    private var dy by mutableIntStateOf(0)

    private fun compose(first: Boolean = false) {
        covered = first
        fade.track(true, false, 0L)
        rule.setContent {
            Box(Modifier.pxSize(W, H)) {
                androidx.compose.foundation.Canvas(Modifier.pxSize(W, H)) { drawRect(Ink.bg) }
                ThumbLayer(rest, covered, snap, follow) { thumbProps(ThumbRect(X0, TOP + dy, W, TOP + dy + LEN), fade.at(now)) }
            }
        }
        rule.waitForIdle()
    }

    private fun at(t: Long) {
        rule.runOnIdle { now = t }
        settle()
    }

    private fun settle() {
        rule.runOnIdle { Snapshot.sendApplyNotifications() }
        rule.waitForIdle()
    }

    private fun shot(): Bitmap {
        settle()
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
        return b
    }

    private fun red(b: Bitmap, y: Int): Int = (b.getPixel(CX, y) shr RED) and BYTE

    private fun mid(b: Bitmap): Int = red(b, TOP + dy + LEN / HALF)

    private fun ground(b: Bitmap): Int = red(b, H - 1)

    private fun topOf(b: Bitmap): Int = (0 until H).firstOrNull { red(b, it) > ground(b) + SLACK } ?: -1

    private fun full(): Int {
        val b = shot()
        val v = mid(b)
        assertTrue("premise: the uncovered thumb inks at $v over a ground of ${ground(b)}", v > ground(b) + SLACK)
        return v
    }

    @Test
    fun aCoveredThumbHoldsItsLastAlphaWhileTheFadeRuns() {
        compose()
        full()
        at(SHOWN)
        val held = mid(shot())
        rule.runOnIdle { covered = true }
        settle()
        assertEquals("premise: the live fade is out at $GONE", 0f, fade.at(GONE), 0f)
        at(OUT)
        assertEquals("held at $OUT", held, mid(shot()))
        at(GONE)
        assertEquals("held at $GONE", held, mid(shot()))
    }

    @Test
    fun anUncoveredThumbDrawsTheLiveValueAgain() {
        compose()
        full()
        at(SHOWN)
        rule.runOnIdle { covered = true }
        at(GONE)
        rule.runOnIdle { covered = false }
        settle()
        val b = shot()
        assertEquals("the uncovered thumb draws the live value, the ground", ground(b), mid(b))
    }

    @Test
    fun aSnapBumpWhileCoveredTakesTheValueAtTheBump() {
        compose()
        val shown = full()
        at(SHOWN)
        rule.runOnIdle { covered = true }
        at(GONE)
        assertEquals("premise: the live fade is out at $GONE", 0f, fade.at(GONE), 0f)
        assertEquals("premise: held full before the bump", shown, mid(shot()))
        rule.runOnIdle { snap++ }
        settle()
        val bb = shot()
        assertEquals("the bump takes the value at $GONE, the ground", ground(bb), mid(bb))
        rule.runOnIdle { fade.show(LATER) }
        at(LATER)
        assertEquals("premise: the live fade is full again at $LATER", 1f, fade.at(LATER), 0f)
        assertEquals("held at the bump's value", ground(bb), mid(shot()))
    }

    @Test
    fun aFollowingThumbHoldsItsAlphaAndMovesItsY() {
        compose()
        val shown = full()
        at(SHOWN)
        val top0 = topOf(shot())
        rule.runOnIdle {
            covered = true
            follow = true
        }
        at(GONE)
        rule.runOnIdle { dy = MOVE }
        settle()
        val moved = shot()
        assertEquals("the y follows the props", top0 + MOVE, topOf(moved))
        assertEquals("the alpha is the held one", shown, mid(moved))
        rule.runOnIdle { follow = false }
        settle()
        assertEquals("a follow turned off keeps the followed y", top0 + MOVE, topOf(shot()))
        rule.runOnIdle { dy = MOVE + MOVE }
        settle()
        val last = shot()
        assertEquals("held at the followed y", top0 + MOVE, topOf(last))
        assertNotEquals(top0 + MOVE + MOVE, topOf(last))
    }

    @Test
    fun aThumbFirstComposedCoveredDrawsTheCurrentValue() {
        compose(first = true)
        val b = shot()
        val v = mid(b)
        assertTrue("a layer first composed covered reads once: $v over ${ground(b)}", v > ground(b) + SLACK)
    }

    private companion object {
        const val W = 240
        const val H = 320
        const val X0 = 232
        const val CX = 236
        const val TOP = 60
        const val LEN = 60
        const val MOVE = 40
        const val HALF = 2
        const val RED = 16
        const val BYTE = 255
        const val SLACK = 4
        const val SHOWN = 1000L
        const val OUT = 2500L
        const val GONE = 3000L
        const val LATER = 3500L
    }
}
