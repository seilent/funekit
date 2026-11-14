package net.seilent.funekit.kit.stack

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.StatusText
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.pageStatus
import net.seilent.funekit.kit.statusLine
import net.seilent.funekit.kit.statusRow
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Phones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MoveStatusTest {
    private val unit = Density(1f)
    private val state = StatusState("", -1, false, Play.Playing, false, -1, -1)

    private class Blank : PagePaint {
        override val chrome: Chrome? = null
        override val header: Word? = null
        override val item = -1
        override val grounded = false
        override val kind = PageKind.Plain
        override val top = 0
        override val bottom = 0
        override val count = 0

        override fun rowY(i: Int) = 0

        override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

        override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) = Unit
    }

    private class NoHome : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = 0f

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = Unit
    }

    private class Run(home: Scale, page: Scale, push: Boolean, kind: MoveKind) {
        val move = PageMove()
        val paint = MovePaint(move)

        init {
            val forms = MoveForms.board(page.board)
            val stack = PageStack<String>(forms, { PageKind.Plain }, move = move) { t ->
                paint.ends(t, HomeSide(home, NoHome(), 0f), t.from?.let { PageSide(page, Blank()) }, t.to?.let { PageSide(page, Blank()) }, forms)
            }
            if (push) {
                stack.push("drawer", kind)
            } else {
                stack.pages.add("drawer")
                stack.back { kind }
            }
            stack.frame(0L)
        }
    }

    private fun ink(s: Scale) = StatusInk(state, StatusText(null, null, null), statusLine(s), s, pageStatus())

    private fun scales(d: Dev): Pair<Scale, Scale> {
        val w = d.window(Nav.None)
        return scale(w, Nav.None, Surface.Home, false) to scale(w, Nav.None, Surface.Page, false)
    }

    private fun draw(d: Dev, block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(d.w, d.h)
        CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(img), Size(d.w.toFloat(), d.h.toFloat())) { block() }
        return img.asAndroidBitmap()
    }

    private fun most(b: Bitmap, x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var m = 0
        for (y in y0 until y1) for (x in x0 until x1) m = maxOf(m, b.getPixel(x, y) ushr ALPHA_SHIFT)
        return m
    }

    private fun mid(r: Run): Long = (0L until SPAN).first { abs(r.move.at(Part.Home, Prop.A, it) - HALF) < NEAR_HALF }

    @Test
    fun onTheP2160AT4hDrawsTheHomeRowAtTheHomeLayerAlphaAndThePageRowAtTheRest() {
        val (home, page) = scales(Phones.p2160Page)
        assertTrue(page.fit != null)
        val h = ink(home)
        val p = ink(page)
        assertFalse(h.sameRow(p))
        assertEquals(listOf(45, 54, 1008), listOf(h.line.top, h.line.size, h.line.right))
        assertEquals(listOf(30, 36, 1033), listOf(p.line.top, p.line.size, p.line.right))
        val r = Run(home, page, false, MoveKind.T4h)
        val at = mid(r)
        val a = r.move.at(Part.Home, Prop.A, at)
        val b = draw(Phones.p2160Page) { with(r.paint) { drawMove(at, null, MoveStatus(h, p, Ink.white, true)) } }
        assertEquals(a * FULL, most(b, 954, 997, 45, 99).toFloat(), SLACK)
        assertEquals((1 - a) * FULL, most(b, 1008, 1033, 30, 66).toFloat(), SLACK)
    }

    @Test
    fun onTheP2160AT2DrawsOnlyThePageRowOnceTheHomeLayerIsGone() {
        val (home, page) = scales(Phones.p2160Page)
        val h = ink(home)
        val p = ink(page)
        val r = Run(home, page, true, MoveKind.T2)
        assertEquals(0f, r.move.at(Part.Home, Prop.A, FRAME), 0f)
        val moved = draw(Phones.p2160Page) { with(r.paint) { drawMove(FRAME, null, MoveStatus(h, p, Ink.white, true)) } }
        val rest = draw(Phones.p2160Page) { statusRow(p, Ink.white, true, 1f) }
        assertTrue(moved.sameAs(rest))
    }

    @Test
    fun onTheP2160AT2wCrossfadesTheHomeRowWithHomeAndSlidesNeither() {
        val (home, page) = scales(Phones.p2160Page)
        val h = ink(home)
        val p = ink(page)
        assertFalse(h.sameRow(p))
        val r = Run(home, page, true, MoveKind.T2w)
        val a = r.move.at(Part.Home, Prop.A, T2W_OUT)
        assertEquals(0.13319f, a, 1e-4f)
        val b = draw(Phones.p2160Page) { with(r.paint) { drawMove(T2W_OUT, null, MoveStatus(h, p, Ink.white, true)) } }
        assertEquals(a * FULL, most(b, 954, 997, 45, 99).toFloat(), SLACK)
        assertEquals((1 - a) * FULL, most(b, 1008, 1033, 30, 66).toFloat(), SLACK)
        assertEquals(0f, r.move.at(Part.Home, Prop.A, T2W_IN), 0f)
        val moved = draw(Phones.p2160Page) { with(r.paint) { drawMove(T2W_IN, null, MoveStatus(h, p, Ink.white, true)) } }
        val rest = draw(Phones.p2160Page) { statusRow(p, Ink.white, true, 1f) }
        assertTrue(moved.sameAs(rest))
    }

    @Test
    fun onTheA105BothBasesDrawOneRowAtFullAlphaThroughTheMove() {
        val (home, page) = scales(Phones.a105)
        val h = ink(home)
        val p = ink(page)
        assertTrue(h.sameRow(p))
        val r = Run(home, page, false, MoveKind.T4h)
        val at = mid(r)
        val moved = draw(Phones.a105) { with(r.paint) { drawMove(at, null, MoveStatus(h, p, Ink.white, true)) } }
        val rest = draw(Phones.a105) { statusRow(p, Ink.white, true, 1f) }
        assertTrue(moved.sameAs(rest))
    }

    companion object {
        private const val ALPHA_SHIFT = 24
        private const val FULL = 255f
        private const val HALF = 0.5f
        private const val NEAR_HALF = 0.2f
        private const val SPAN = 5000L
        private const val FRAME = 16L
        private const val SLACK = 2f
        private const val T2W_OUT = 100L
        private const val T2W_IN = 400L
    }
}
