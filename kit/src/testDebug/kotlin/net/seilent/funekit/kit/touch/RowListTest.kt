package net.seilent.funekit.kit.touch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 35], qualifiers = "w1080dp-h2160dp-mdpi")
class RowListTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private fun sc(w: Int, h: Int, nav: Insets = none, mode: Nav = Nav.None): Scale = scale(Window(w, h, 35, none, nav, none, none, none), mode, Surface.Page, false)

    private val a105 = sc(720, 1280)
    private val a105Nav = sc(720, 1280, Insets(0, 0, 0, 108), Nav.Buttons)
    private val r1 = sc(480, 800)
    private val r1Nav = sc(480, 800, Insets(0, 0, 0, 72), Nav.Buttons)
    private val v1 = sc(240, 320)
    private val p2160 = sc(1080, 2160)

    private class Geo(val pitch: Int, val top: Int, val view: Int)

    private fun geo(s: Scale): Geo {
        val top = px(s.board.page_contentTop, s.ppu)
        return Geo(Rows.pitch(s, true), top, px(s.stdContentBottom, s.stdPpu) - top)
    }

    private val start = (0 until 30).map { "k$it" }
    private val scale = mutableStateOf(a105)
    private var late = false
    private lateinit var keys: MutableState<List<String>>
    private lateinit var ref: ListTouch
    private lateinit var row: ListTouch
    private val refAt = HashMap<String, LayoutCoordinates>()
    private val rowAt = HashMap<String, LayoutCoordinates>()
    private var t = 0L

    private fun anchor(old: List<String>?, ks: List<String>, first: Int): Int {
        val k = old?.getOrNull(first) ?: return first
        val i = ks.indexOf(k)
        return if (i >= 0) i else first
    }

    private fun show(s: Scale): StateRestorationTester {
        scale.value = s
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val sc = scale.value
            val g = geo(sc)
            val a = remember(sc) { Arbiter(sc) }
            ref = rememberListTouch(Kind.List, sc)
            row = rememberListTouch(Kind.List, sc, rows = RowSpec(g.pitch, g.view))
            val shown = remember { mutableStateOf(if (late) emptyList() else start) }
            keys = shown
            val ks = shown.value
            val counted = remember { arrayOfNulls<List<String>>(1) }
            SideEffect {
                if (counted[0] !== ks) {
                    row.count(ks.size, anchor(counted[0], ks, row.first))
                    counted[0] = ks
                }
            }
            Box {
                Column {
                    Box(Modifier.pxSize(sc.widthPx, g.top))
                    FuneList(a, ref, Modifier.pxSize(sc.widthPx, g.view)) {
                        items(ks.size, key = { ks[it] }) { i -> Box(Modifier.pxSize(sc.widthPx, g.pitch).onGloballyPositioned { refAt[ks[i]] = it }) }
                    }
                }
                Column {
                    Box(Modifier.pxSize(sc.widthPx, g.top))
                    FuneList(a, row, Modifier.pxSize(sc.widthPx, g.view)) {
                        items(ks.size, key = { ks[it] }) { i -> Box(Modifier.pxSize(sc.widthPx, g.pitch).onGloballyPositioned { rowAt[ks[i]] = it }) }
                    }
                }
            }
        }
        rule.waitForIdle()
        return tester
    }

    private fun same(where: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val p = geo(scale.value).pitch
            val st = ref.state
            assertEquals(where, st.firstVisibleItemIndex * p + st.firstVisibleItemScrollOffset, row.px)
            assertEquals(where, ref.over, row.over, 0f)
            assertEquals(where, row.first, row.state.firstVisibleItemIndex)
            assertEquals(where, 0, row.state.firstVisibleItemScrollOffset)
            val placed = st.layoutInfo.visibleItemsInfo.map { it.key as String }
            assertTrue(where, placed.isNotEmpty())
            for (k in placed) {
                assertEquals("$where $k", refAt.getValue(k).positionInRoot().y, rowAt.getValue(k).positionInRoot().y, 0.01f)
            }
        }
    }

    private fun both(f: (ListTouch) -> Unit) = rule.runOnIdle {
        for (l in listOf(ref, row)) {
            l.stop()
            f(l)
            l.sync()
        }
    }

    private fun frames(where: String) {
        var n = 0
        while (ref.scroll.moving || row.scroll.moving) {
            t += FRAME
            both { it.scroll.step(t) }
            same("$where frame ${n++}")
            assertTrue(where, n < MAX_FRAMES)
        }
    }

    private fun drag(where: String, vararg ds: Float) {
        var y = FINGER
        both { it.scroll.begin(y) }
        for (d in ds) {
            y -= d
            val at = y
            both { it.scroll.drag(at) }
            same("$where drag $d")
        }
        both { it.scroll.endDrag() }
        frames("$where return")
    }

    private fun fling(where: String, fingerV: Float) {
        t += FRAME
        both { it.scroll.release(t, fingerV) }
        same("$where release")
        frames(where)
    }

    private fun cap(): Float = px(Tok.flingCap, scale.value.stdPpu).toFloat()

    private fun request(where: String, i: Int) {
        rule.runOnIdle {
            row.toRow(i)
            ref.state.requestScrollToItem(i)
        }
        same(where)
    }

    private fun at1000() {
        rule.runOnIdle {
            row.toRow(6)
            requireNotNull(row.rows).by(10f)
            ref.state.requestScrollToItem(6, 10)
        }
        same("q 1000")
        rule.runOnIdle { assertEquals(1000, row.px) }
    }

    private fun crossing(where: String) = drag("$where crossing", geo(scale.value).pitch * 3 / 2f)

    private fun sequence(s: Scale) {
        show(s)
        same("rest")
        fling("down", -2 * cap())
        rule.runOnIdle { assertTrue(row.px >= geo(s).pitch) }
        fling("up", 2 * cap())
        val p = geo(s).pitch.toFloat()
        drag("steps", 0.5f, 1.5f, p, -p)
        request("top", 0)
        drag("pull", -10f, -10f)
        request("end", 30)
        drag("push", 10f, 10f)
    }

    @Test
    fun theGeometriesAreTheDrawers() {
        assertEquals(listOf(165, 360, 920), geo(a105).let { listOf(it.pitch, it.top, it.view) })
        assertEquals(listOf(165, 360, 812), geo(a105Nav).let { listOf(it.pitch, it.top, it.view) })
        assertEquals(listOf(110, 240, 560), geo(r1).let { listOf(it.pitch, it.top, it.view) })
        assertEquals(listOf(110, 240, 488), geo(r1Nav).let { listOf(it.pitch, it.top, it.view) })
        assertEquals(listOf(55, 98, 222), geo(v1).let { listOf(it.pitch, it.top, it.view) })
        assertEquals(listOf(248, 540, 1620), geo(p2160).let { listOf(it.pitch, it.top, it.view) })
    }

    @Test
    fun theA105RowListPlacesEveryRowWhereLazyListDoes() = sequence(a105)

    @Test
    fun theV1RowListPlacesEveryRowWhereLazyListDoes() = sequence(v1)

    @Test
    fun theP2160RowListPlacesEveryRowWhereLazyListDoes() = sequence(p2160)

    @Test
    fun aRowRequestAndAKeyedEditKeepThePositionLazyListKeeps() {
        show(a105)
        request("row 20", 20)
        crossing("row 20")
        rule.runOnIdle { keys.value = start.take(2) + "x" + start.drop(2) }
        same("insert")
        crossing("insert")
        rule.runOnIdle { keys.value = keys.value - keys.value[row.first] }
        same("removal")
        crossing("removal")
        request("row 30", 30)
        rule.runOnIdle { assertEquals(4030, row.px) }
    }

    @Test
    fun theResidualOfAFlingOutlivesARowRequest() {
        show(a105)
        fling("down", -2 * cap())
        drag("residual", 0.4f)
        request("row 6", 6)
        drag("after", 0.2f)
        crossing("after")
    }

    @Test
    fun aRestoreKeepsThePositionWithTheListAtTheFirstRow() {
        val tester = show(a105)
        at1000()
        tester.emulateSavedInstanceStateRestore()
        same("restored")
        rule.runOnIdle {
            assertEquals(1000, row.px)
            assertEquals(6, row.state.firstVisibleItemIndex)
            assertEquals(0, row.state.firstVisibleItemScrollOffset)
        }
        crossing("restored")
    }

    @Test
    fun aRestoreKeepsThePositionWhenTheRowsArriveAFrameLater() {
        val tester = show(a105)
        at1000()
        late = true
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.runOnIdle { keys.value = start }
        same("rows arrived")
        rule.runOnIdle { assertEquals(1000, row.px) }
        crossing("rows arrived")
    }

    @Test
    fun aScaleChangeRescalesThePositionAsLazyListDoes() {
        show(a105)
        at1000()
        val old = row
        rule.runOnIdle { scale.value = r1 }
        same("rescaled")
        rule.runOnIdle {
            assertNotSame(old, row)
            assertEquals(667, row.px)
            assertEquals(6, row.state.firstVisibleItemIndex)
        }
        crossing("rescaled")
    }

    @Test
    fun aPitchThatGrowsAtTheListEndPlacesEveryRowAsLazyListDoes() {
        show(r1Nav)
        request("end", 30)
        rule.runOnIdle {
            assertEquals(2812, row.px)
            assertEquals(62, row.sub)
        }
        rule.runOnIdle { scale.value = a105Nav }
        same("grown")
        rule.runOnIdle {
            assertEquals(4138, row.px)
            assertEquals(25, row.first)
            assertEquals(13, row.sub)
        }
        drag("grown back", -geo(a105Nav).pitch * 3 / 2f)
    }

    @Test
    fun aNavBarChangeKeepsThePosition() {
        show(a105)
        at1000()
        rule.runOnIdle { scale.value = a105Nav }
        same("nav")
        rule.runOnIdle { assertEquals(1000, row.px) }
        crossing("nav")
    }

    companion object {
        private const val FRAME = 16L
        private const val MAX_FRAMES = 2000
        private const val FINGER = 1000f
    }
}
