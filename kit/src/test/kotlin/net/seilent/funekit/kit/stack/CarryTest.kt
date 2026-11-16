package net.seilent.funekit.kit.stack

import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.proj.M3
import net.seilent.funekit.kit.proj.turn
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarryTest {
    private val rows = CarryAt(
        MoveKind.T3, toHome = false, rows = true, bar = Float.MAX_VALUE, contentTop = TOP, bandTop = TOP, pivotTop = PIVOT_TOP, pivotBottom = PIVOT_BOTTOM,
        xa = XA, ya = YA, ox = OX, oy = OY, ca = 1f, cs = 0.9f, cx = 12.4f,
    )

    private fun at(m: M3?, x: Float, y: Float): Pair<Float, Float> = checkNotNull(m).map(x, y).let { it.x to it.y }

    private fun near(want: Pair<Float, Float>, got: Pair<Float, Float>) {
        assertEquals(want.first, got.first, NEAR)
        assertEquals(want.second, got.second, NEAR)
    }

    private fun copy(cs: Float = rows.cs, cx: Float = rows.cx, bar: Float = rows.bar, ca: Float = rows.ca, pa: Float = 0f, px: Float = 0f, py: Float = 0f, rowsOn: Boolean = true, kind: MoveKind = MoveKind.T3) = CarryAt(
        kind, false, rowsOn, bar, rows.contentTop, rows.bandTop, rows.pivotTop, rows.pivotBottom, rows.xa, rows.ya, rows.ox, rows.oy, ca, cs, cx, pa, px, py,
    )

    @Test
    fun aRowsTapUnderFullScaleMapsByTheRoundedContentXOnly() {
        near(400f - 12f to 900f, at(carryOf(400f, 900f, rows), 400f, 900f))
    }

    @Test
    fun aRowsTapAboveFullScaleMapsByTheScaleAboutTheRowsAnchor() {
        val m = carryOf(400f, 900f, copy(cs = 1.2f, cx = 0f))
        near(XA + (400f - XA) / 1.2f to YA + (900f - YA) / 1.2f, at(m, 400f, 900f))
    }

    @Test
    fun aTapAboveTheScaledBandTopTakesNoPart() {
        val top = YA + 0.9f * (TOP - YA)
        assertNull(carryOf(400f, top - 1f, rows))
        assertNotNull(carryOf(400f, top + 1f, rows))
    }

    @Test
    fun plainContentMapsByTheScaleAboutTheContentOrigin() {
        val m = carryOf(400f, 900f, copy(cs = 1.5f, cx = 30f, rowsOn = false))
        near(OX + (400f - OX) / 1.5f to OY + (900f - OY) / 1.5f, at(m, 400f, 900f))
        near(400f to 900f, at(carryOf(400f, 900f, copy(cs = 0.7f, rowsOn = false)), 400f, 900f))
    }

    @Test
    fun aPivotTapMapsByTheRoundedPivotsOffset() {
        val m = carryOf(300f, PIVOT_TOP + 10f, copy(ca = 0f, pa = 0.5f, px = 7.6f, py = -3.4f))
        near(300f - 8f to PIVOT_TOP + 10f + 3f, at(m, 300f, PIVOT_TOP + 10f))
        assertNull(carryOf(300f, PIVOT_BOTTOM, copy(ca = 0f, pa = 0.5f, px = 7.6f)))
    }

    @Test
    fun aTapAtOrBelowTheNavBarTopTakesNone() {
        assertNull(carryOf(400f, BAR, copy(bar = BAR)))
        assertNotNull(carryOf(400f, BAR - 1f, copy(bar = BAR)))
    }

    @Test
    fun aT8TapOntoAPageMapsByItsPreimageFixedAtTheDown() {
        val c = CarryAt(MoveKind.T8, toHome = true, rows = false, bar = Float.MAX_VALUE, under = turn(T8_R, 0f, T8_EYE, T8_D))
        val m = carryOf(360f, 640f, c)
        near(369.145f to 638.324f, at(m, 360f, 640f))
        near(389.145f to 598.324f, at(m, 380f, 600f))
        near(105.312f to 204.096f, at(carryOf(100f, 200f, c), 100f, 200f))
        assertNull(carryOf(360f, 640f, CarryAt(MoveKind.T8, toHome = true, rows = false, bar = Float.MAX_VALUE)))
    }

    @Test
    fun aTouchDuringT7OrAMoveToHomeTakesNone() {
        assertNull(carryOf(400f, 900f, copy(kind = MoveKind.T7)))
        assertNull(carryOf(400f, 900f, CarryAt(MoveKind.T4h, toHome = true, rows = false, bar = Float.MAX_VALUE)))
    }

    @Test
    fun aT3MapsATapByTheDrawnContentXWhileItsScaleIsUnderOne() {
        val none = Insets(0, 0, 0, 0)
        val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.Page, false)
        val move = PageMove()
        val mp = MovePaint(move)
        val forms = MoveForms()
        val st = PageStack<String>(forms, { PageKind.Plain }, move = move, ends = { mp.ends(it, null, PageSide(s, Blank), PageSide(s, Blank), forms) })
        st.pages.add("a")
        st.push("b", MoveKind.T3)
        st.frame(0L)
        assertEquals(MoveKind.T3, st.turn?.kind)
        var seen = 0
        var moved = 0
        for (t in 0L..FRAMES step STEP) {
            val ca = move.at(Part.Content, Prop.A, t)
            val cs = move.at(Part.Content, Prop.S, t)
            if (ca <= 0f || cs > 1f) continue
            val dx = Math.round(move.at(Part.Content, Prop.X, t)).toFloat()
            val v = FloatArray(MATRIX)
            checkNotNull(mp.carry(400f, 900f, t)).getValues(v)
            assertEquals(1f, v[0], NEAR)
            assertEquals(-dx, v[2], NEAR)
            assertEquals(0f, v[5], NEAR)
            seen++
            if (dx != 0f) moved++
        }
        assertTrue("a T3 frame with the arriving content shown at a scale of at most 1", seen > 0)
        assertTrue("a T3 frame whose rows carry a non zero content x, so the rows form is read", moved > 0)
    }

    @Test
    fun aDownDuringAnAlbumPushMapsAboutTheContentOriginAtAtLeastFullScaleOrGoesToTheLandedPage() {
        val none = Insets(0, 0, 0, 0)
        val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.Page, false)
        val ox = Tok.motion_contentX * s.widthPx
        val oy = Tok.motion_contentY * s.heightPx
        for ((k, shown) in listOf(MoveKind.T3a to true, MoveKind.T3z to true, MoveKind.T3m to false, MoveKind.T3c to false)) {
            val move = PageMove()
            val mp = MovePaint(move)
            val forms = MoveForms()
            val st = PageStack<String>(forms, { PageKind.Plain }, move = move, ends = { mp.ends(it, null, PageSide(s, Blank), PageSide(s, Blank), forms) })
            st.pages.add("a")
            st.push("b", k)
            st.frame(0L)
            assertEquals(k, st.turn?.kind)
            assertEquals("$k content at $DOWN_AT", shown, move.at(Part.Content, Prop.A, DOWN_AT) > 0f)
            val m = mp.carry(DOWN_X, DOWN_Y, DOWN_AT)
            if (!shown) {
                assertNull("$k", m)
                continue
            }
            val cs = max(move.at(Part.Content, Prop.S, DOWN_AT), 1f)
            assertEquals("$k", 1f, cs, 0f)
            val v = FloatArray(MATRIX)
            checkNotNull(m).getValues(v)
            near(ox + (DOWN_X - ox) / cs to oy + (DOWN_Y - oy) / cs, v[0] * DOWN_X + v[1] * DOWN_Y + v[2] to v[3] * DOWN_X + v[4] * DOWN_Y + v[5])
            near(DOWN_X to DOWN_Y, v[0] * DOWN_X + v[1] * DOWN_Y + v[2] to v[3] * DOWN_X + v[4] * DOWN_Y + v[5])
        }
    }

    private object Blank : PagePaint {
        override val chrome: Chrome? = null
        override val header: Word? = null
        override val item = -1
        override val grounded = false
        override val kind = PageKind.Plain
        override val top = 0
        override val bottom = 1280
        override val count = 0

        override fun rowY(i: Int) = 0

        override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

        override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) = Unit
    }

    private companion object {
        const val TOP = 360f
        const val PIVOT_TOP = 189f
        const val PIVOT_BOTTOM = 278f
        const val XA = 60f
        const val YA = 1174f
        const val OX = 360f
        const val OY = 624f
        const val BAR = 1172f
        const val T8_R = -20f
        const val T8_EYE = 594f
        const val T8_D = 3465f
        const val NEAR = 1e-2f
        const val FRAMES = 1000L
        const val STEP = 16L
        const val MATRIX = 9
        const val DOWN_AT = 300L
        const val DOWN_X = 360f
        const val DOWN_Y = 700f
    }
}
