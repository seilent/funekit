package net.seilent.funekit.kit.quickplay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.FuneTheme
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.kit.touch.rememberListTouch
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import net.seilent.funekit.kit.touch.Kind as TouchKind

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QuickplayFrontTest {
    private data class Ref(val id: Long, val name: String)

    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Home, false)

    private fun items(n: Int, kind: QpKind) = List(n) { QpItem(kind, it.toLong(), "t$it", null) }

    private val st = QuickplayState()
    private lateinit var touch: ListTouch
    private lateinit var layout: QpLayout

    private val mixed = QpData(null, items(3, QpKind.Album), items(10, QpKind.Song), items(12, QpKind.Album))

    private fun front(onPlay: (QpTile) -> Unit, onBack: () -> Unit, data: QpData = mixed) {
        rule.setContent {
            FuneTheme(s) {
                val a = remember { Arbiter(s) }
                touch = rememberListTouch(TouchKind.Quickplay, s)
                val m = rememberTextMeasurer()
                val fonts = LocalFontFamilyResolver.current
                val density = LocalDensity.current
                val ink = remember { qpInk(m, fonts, s, density) }
                val l = remember { QpLayout(s, qpTiles(data), ink.hint.size) }
                layout = l
                val boxes = remember { qpBoxes(m, fonts, l, density, null) }
                Box(Modifier.pxSize(s.widthPx, s.heightPx).arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                    QuickplayFront(
                        l, ink, st, boxes, a, touch, { 0L }, { 0L }, live = true, visible = true, poke = {},
                        onPlay = onPlay, onBack = onBack,
                    )
                }
            }
        }
    }

    @Test
    fun aTileScrolledIntoViewTakesItsTap() {
        val played = mutableListOf<QpTile>()
        front({ played += it }, {})
        assertTrue(layout.limit > 0L)
        rule.runOnIdle { touch.state.requestScrollToItem(0, layout.pxOf(layout.limit)) }
        rule.waitForIdle()
        val last = layout.count - 1
        val r = layout.rect(last, qpOff(touch, st, layout))
        assertTrue(r.y1 <= s.heightPx)
        rule.onRoot().performTouchInput {
            down(Offset((r.x0 + r.x1) / 2f, (r.y0 + r.y1) / 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(layout.tiles[last]), played)
        assertEquals(last, st.focus)
    }

    @Test
    fun theFrontScrollsFromEveryDownOutsideTheMenuPeek() {
        var backs = 0
        front({}, { backs++ })
        rule.runOnIdle { touch.state.requestScrollToItem(0, layout.pxOf(layout.limit / 2)) }
        rule.waitForIdle()
        val peek = menuPeek(s, layout.panelPx)
        assertTrue(peek.x0 >= layout.panelPx && peek.y0 > 0)
        val start = qpPos(touch, st, layout)
        val inPeek = Offset((peek.x0 + peek.x1) / 2f, (peek.y0 + peek.y1) / 2f)
        rule.onRoot().performTouchInput {
            down(inPeek)
            repeat(3) { moveBy(Offset(0f, 30f)) }
            up()
        }
        rule.waitForIdle()
        assertEquals(start, qpPos(touch, st, layout))
        rule.onRoot().performTouchInput {
            down(inPeek)
            up()
        }
        rule.waitForIdle()
        assertEquals(1, backs)
        rule.onRoot().performTouchInput {
            down(Offset((peek.x0 + s.widthPx) / 2f, peek.y0 / 2f))
            repeat(3) { moveBy(Offset(0f, 30f)) }
            up()
        }
        rule.waitForIdle()
        assertTrue(qpPos(touch, st, layout) < start)
    }

    @Test
    fun requestScrollToItemSetsTheOffsetBeforeTheNextMeasure() {
        front({}, {})
        var composed = -1
        var free = -1
        rule.runOnIdle {
            touch.state.requestScrollToItem(0, 37)
            composed = touch.state.firstVisibleItemScrollOffset
            val loose = LazyListState()
            loose.requestScrollToItem(0, 37)
            free = loose.firstVisibleItemScrollOffset
        }
        assertEquals("requestScrollToItem(0, 37) read before the measure: composed list $composed, uncomposed state $free", listOf(37, 37), listOf(composed, free))
        rule.waitForIdle()
        assertEquals(37, touch.state.firstVisibleItemScrollOffset)
    }

    @Test
    fun aWaitingFrontTakesNoTileTap() {
        val played = mutableListOf<QpTile>()
        st.waiting = true
        front({ played += it }, {})
        val r = layout.rect(0, qpOff(touch, st, layout))
        val at = Offset((r.x0 + r.x1) / 2f, (r.y0 + r.y1) / 2f)
        rule.onRoot().performTouchInput {
            down(at)
            up()
        }
        rule.waitForIdle()
        assertEquals(emptyList<QpTile>(), played)
        rule.runOnIdle { st.waiting = false }
        rule.waitForIdle()
        rule.onRoot().performTouchInput {
            down(at)
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(layout.tiles[0]), played)
    }

    @Test
    fun aWaitingQuickplayDrawsItsHeadersOnly() {
        val unit = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = TextMeasurer(fonts, unit, LayoutDirection.Ltr)
        val ink = qpInk(m, fonts, s, unit)
        fun draw(l: QpLayout, waiting: Boolean): IntArray {
            val q = QuickplayState().also { it.waiting = waiting }
            val img = ImageBitmap(s.widthPx, s.heightPx)
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(img), Size(s.widthPx.toFloat(), s.heightPx.toFloat())) {
                drawRect(Ink.bg)
                quickplay(l, ink, q, qpBoxes(m, fonts, l, unit, null), 0L, q.look(l, 0L), true)
            }
            return IntArray(s.widthPx * s.heightPx).also { img.readPixels(it) }
        }
        fun band(px: IntArray, base: Int, above: Int): List<Int> = (base - above until base + 2).flatMap { y -> (0 until s.widthPx).map { px[y * s.widthPx + it] and RGB } }
        val empty = QpLayout(s, qpTiles(QpData.EMPTY), ink.hint.size)
        val shown = draw(empty, false)
        val waiting = draw(empty, true)
        for (k in 0 until 3) {
            val base = empty.pxOf(empty.sectionTop(k))
            assertEquals(band(shown, base, HEAD), band(waiting, base, HEAD))
            assertTrue(band(waiting, base, HEAD).any { it != BG })
        }
        for (i in ink.hint.indices) {
            val base = empty.pxOf(empty.hintTop(i))
            assertTrue(band(shown, base, HINT).any { it != BG })
            assertTrue(band(waiting, base, HINT).all { it == BG })
        }
        val loading = List(3) { QpItem(QpKind.Album, it.toLong(), "t$it", Ref(it.toLong(), COVER)) }
        val tiled = QpLayout(s, qpTiles(QpData(null, loading, emptyList(), emptyList())), ink.hint.size)
        val r = tiled.rect(0, 0L)
        val centre = ((r.y0 + r.y1) / 2) * s.widthPx + (r.x0 + r.x1) / 2
        assertTrue(draw(tiled, false)[centre] and RGB != BG)
        assertEquals(BG, draw(tiled, true)[centre] and RGB)
    }

    @Test
    fun aHiddenFocusDrawsNoFocusBorderAroundTheFocusedTile() {
        val unit = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = TextMeasurer(fonts, unit, LayoutDirection.Ltr)
        val l = QpLayout(s, qpTiles(QpData(null, items(3, QpKind.Album), emptyList(), emptyList())), 3)
        val ink = qpInk(m, fonts, s, unit)
        val boxes = qpBoxes(m, fonts, l, unit, null)
        val q = QuickplayState()
        q.focus = 0
        q.keySelect()
        val r = l.rect(0, 0L)
        fun left(hide: Boolean): Int {
            val img = ImageBitmap(s.widthPx, s.heightPx)
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(img), Size(s.widthPx.toFloat(), s.heightPx.toFloat())) {
                drawRect(Ink.bg)
                quickplay(l, ink, q, boxes, 0L, q.look(l, 0L, hide), true)
            }
            val px = IntArray(s.widthPx * s.heightPx).also { img.readPixels(it) }
            return px[(r.y0 + r.h / 2) * s.widthPx + r.x0 - 1] and 0xFFFFFF
        }
        assertEquals(0xFFFFFF, left(false))
        assertEquals(0x111111, left(true))
    }

    @Test
    fun theFocusBorderDrawsOnlyWithKeyFocus() {
        val unit = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = TextMeasurer(fonts, unit, LayoutDirection.Ltr)
        val l = QpLayout(s, qpTiles(QpData(null, items(3, QpKind.Album), emptyList(), emptyList())), 3)
        val ink = qpInk(m, fonts, s, unit)
        val boxes = qpBoxes(m, fonts, l, unit, null)
        val q = QuickplayState()
        fun left(i: Int): Int {
            val r = l.rect(i, 0L)
            val img = ImageBitmap(s.widthPx, s.heightPx)
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(img), Size(s.widthPx.toFloat(), s.heightPx.toFloat())) {
                drawRect(Ink.bg)
                quickplay(l, ink, q, boxes, 0L, q.look(l, 0L), true)
            }
            val px = IntArray(s.widthPx * s.heightPx).also { img.readPixels(it) }
            return px[(r.y0 + r.h / 2) * s.widthPx + r.x0 - 1] and RGB
        }
        assertEquals(0, q.focus)
        assertEquals("no border before a key moves", BG, left(0))
        q.keySelect()
        assertEquals(RGB, left(0))
        q.dropKeyFocus()
        assertEquals("a dropped key focus draws no border", BG, left(0))
        q.keyMove(1, l)
        assertEquals(1, q.focus)
        assertEquals(RGB, left(1))
        assertEquals(BG, left(0))
    }

    private companion object {
        const val RGB = 0xFFFFFF
        const val BG = 0x111111
        const val HEAD = 8
        const val HINT = 6
        const val COVER = "cover.jpg"
    }
}
