package net.seilent.funekit.kit.stack

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.gen.Moves
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.MenuInk
import net.seilent.funekit.kit.MenuPlan
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.motion.MorphKind
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.proj.M3
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.wordBottom
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Dev
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MovePaintTest {
    private val dev = Dev("720x1280", "A105", 720, 1280, listOf(Nav.None))
    private val s: Scale = scale(dev.window(Nav.None), Nav.None, Surface.Page, false)
    private val forms = MoveForms.board(TokBoard.a105)
    private val unit = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unit, LayoutDirection.Ltr)
    private val chrome = chromeOf(measurer, fonts, s, unit, "apps", emptyList(), null)
    private val pitch = Rows.pitch(s, true)
    private val top = px(s.board.page_contentTop, s.ppu)
    private val bottom = px(s.stdContentBottom, s.stdPpu)
    private val ya = s.heightPx - Tok.motion_rowsYa * s.stdPpu
    private val xa = Tok.hubEdge * s.stdPpu
    private val barX = px(s.w - Tok.scrollbar_width, s.ppu).toFloat()
    private val anchor = PageMove.popAnchor(MoveKind.T4h, top, s.heightPx, s.stdPpu).toFloat()
    private val zoomForms = MoveForms.board(s.board)

    private class Geo(val s: Scale, val chrome: Chrome, val pitch: Int, val top: Int, val bottom: Int)

    private fun geoOf(sc: Scale) =
        Geo(sc, chromeOf(measurer, fonts, sc, unit, "apps", emptyList(), null), Rows.pitch(sc, true), px(sc.board.page_contentTop, sc.ppu), px(sc.stdContentBottom, sc.stdPpu))

    private val base = Geo(s, chrome, pitch, top, bottom)

    private class Drawn(
        val name: String,
        val row: Int,
        val dx: Float,
        val dy: Float,
        val y: Int,
        val k: Float = 1f,
        val top: Float = 0f,
        val bottom: Float = 0f,
        val clip: List<Float> = emptyList(),
        val alpha: Float = 1f,
    )

    private class Track : DrawRecord {
        var k = 1f
        var tx = 0f
        var ty = 0f
        var a = 1f
        var clip = listOf(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val words = mutableListOf<String>()
        val alphas = mutableMapOf<String, Float>()
        val wordClips = mutableMapOf<String, List<Float>>()
        val wordAt = mutableMapOf<String, List<Float>>()
        val wordFade = mutableMapOf<String, Float>()
        val chromes = mutableMapOf<String, List<Float>>()
        val quads = mutableListOf<Quad>()
        var inQuad: String? = null

        private fun keep(block: () -> Unit) {
            val was = listOf(k, tx, ty, a)
            val c = clip
            block()
            k = was[0]
            tx = was[1]
            ty = was[2]
            a = was[3]
            clip = c
        }

        override fun move(dx: Float, dy: Float, block: () -> Unit) = keep {
            tx += k * dx
            ty += k * dy
            block()
        }

        override fun zoom(z: Float, px: Float, py: Float, block: () -> Unit) = keep {
            tx += k * (1 - z) * px
            ty += k * (1 - z) * py
            k *= z
            block()
        }

        override fun fade(f: Float, block: () -> Unit) = keep {
            a *= f
            block()
        }

        override fun clip(x0: Float, y0: Float, x1: Float, y1: Float, block: () -> Unit) = keep {
            clip = listOf(maxOf(clip[0], k * x0 + tx), maxOf(clip[1], k * y0 + ty), minOf(clip[2], k * x1 + tx), minOf(clip[3], k * y1 + ty))
            block()
        }

        override fun skip(reason: String, block: () -> Unit) = block()

        override fun hide(reason: String, block: () -> Unit) = block()

        override fun on(c: Color, block: () -> Unit) = block()

        override fun word(name: String, wd: Word, x: Float, base: Float, alpha: Float, color: Color) {
            words += name
            alphas[name] = alpha
            wordClips[name] = clip
            wordAt[name] = listOf(k, tx, ty)
            wordFade[name] = a
        }

        override fun ring(name: String, c: Offset, d: Float, alpha: Float) = Unit

        override fun glyphs(name: String, gs: List<Glyph>, c: Offset, slot: Float, alpha: Float, ringD: Float, ringStroke: Float) = Unit

        override fun row(name: String, ink: RowInk, s: Scale, w: Int, alpha: Float, focused: Boolean, press: Float, glow: Boolean, accent: Color, accentOff: Boolean, focusShown: Boolean) = Unit

        override fun thumbParts(name: String, r: ThumbRect, alpha: Float, bands: List<Band>, accent: Color) = Unit

        override fun chrome(name: String, c: Chrome, now: Long, w: Int) {
            chromes[name] = listOf(tx + k * c.headerRestX(), a * c.headerAlpha * c.headerPart.alpha, a)
        }

        override fun jump(name: String, g: JumpGrid, ink: JumpInk, cells: JumpCells, s: Scale, off: Float, now: Long, h: Int) = Unit

        override fun menuRegions(name: String, menu: Menu, ink: MenuInk, plan: MenuPlan, s: Scale, now: Long, w: Int, h: Int, accent: Color, accentOff: Boolean) = Unit

        override fun quad(name: String, a: Float, t: M3, src: Edges?, block: () -> Unit) {
            quads += Quad(name, a, t, src)
            val was = inQuad
            inQuad = name
            block()
            inQuad = was
        }

        fun quad(name: String): Quad = quads.single { it.name == name }
    }

    private class Quad(val name: String, val a: Float, val t: M3, val src: Edges?)

    private class NoItem(d: PagePaint) : PagePaint by d {
        override val item = -1
    }

    private inner class ListPage(
        val n: Int,
        val drawn: MutableList<Drawn>,
        val sides: (Boolean) -> WordSide?,
        override val scroll: Int = 0,
        val g: Geo = base,
        override val kind: PageKind = PageKind.Plain,
        val bar: Pair<ThumbRect, ThumbFade>? = null,
    ) : PagePaint {
        val rests = mutableListOf<String>()
        val restAt = mutableListOf<Drawn>()
        override val chrome: Chrome = g.chrome
        override val header: Word = g.chrome.header
        override val item = 2
        override val grounded = true
        override val top = g.top
        override val bottom = g.bottom
        override val count = n

        override fun rowY(i: Int) = g.top + i * g.pitch

        override fun wordSide(pivot: Boolean): WordSide? = sides(pivot)

        override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

        override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
            val t = rec as? Track
            if (t == null) {
                drawn += Drawn(name, i, 0f, 0f, y)
                return
            }
            drawn += Drawn(name, i, t.tx, t.ty, y, t.k, t.k * y + t.ty, t.k * (y + g.pitch) + t.ty, t.clip, t.a)
        }

        override fun thumbFade(at: Long): Float = bar?.second?.at(at) ?: 0f

        override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
            val r = bar?.first ?: return
            val t = rec as? Track ?: return
            drawn += Drawn("$name.thumb", -1, t.tx, t.ty, r.y0, t.k, t.k * r.y0 + t.ty, t.k * r.y1 + t.ty, t.clip, t.a * fade)
        }

        override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
            rests += name
            (rec as? Track)?.let { restAt += Drawn(name, -1, it.tx, it.ty, 0, it.k, clip = it.clip) }
            restRows(this@ListPage, now, rec, name)
        }
    }

    private class Scrolled(d: PagePaint) : PagePaint by d {
        override val scroll = 1165

        override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) = restRows(this@Scrolled, now, rec, name)

        override fun DrawScope.content(c: Content, now: Long, rec: DrawRecord?, name: String, fade: Float) = contentRows(this@Scrolled, c, now, rec, name, fade)

        override fun DrawScope.bands(b: Bands, now: Long, rec: DrawRecord?, name: String) = bandRows(this@Scrolled, b, now, rec, name)
    }

    private inner class Home(val drawn: MutableList<String>, val g: Geo) : HomePaint {
        var look: HomeMove? = null
        var track: Track? = null
        var pane: Edges? = null
        var peekA = -1f
        var peekX = 0f
        val where = mutableListOf<Pair<String, String?>>()
        override val quickplay = true

        override fun frontPane(): Edges? = pane

        override fun DrawScope.front(now: Long) {
            where += "front" to track?.inQuad
        }

        override fun DrawScope.frontPeek(alpha: Float, now: Long) {
            where += "frontPeek" to track?.inQuad
        }

        override fun peekAlpha(at: Long) = Tok.qpPeekAlpha

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) {
            drawn += "peek"
            peekA = alpha
            peekX = track?.tx ?: 0f
            where += "peek" to track?.inQuad
            rec?.word("peek.word", g.chrome.header, 0f, 0f, alpha)
        }

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) {
            drawn += "home"
            this@Home.look = look
            where += "home" to track?.inQuad
            drawRect(HOME_INK, alpha = look.items)
            rec?.word("home.word", g.chrome.header, 0f, 0f, look.items)
        }
    }

    private class Painted(private val d: PagePaint) : PagePaint by d {
        override fun DrawScope.ground(leave: Float, arrive: Float) = drawRect(GROUND_INK)

        override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
            drawRect(ROW_INK, Offset(size.width / ROW_PARTS, y.toFloat()), Size(size.width - size.width / ROW_PARTS, ROW_H))
            with(d) { row(i, y, now, rec, name) }
        }

        override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) = restRows(this@Painted, now, rec, name)

        override fun DrawScope.content(c: Content, now: Long, rec: DrawRecord?, name: String, fade: Float) = contentRows(this@Painted, c, now, rec, name, fade)

        override fun DrawScope.bands(b: Bands, now: Long, rec: DrawRecord?, name: String) = bandRows(this@Painted, b, now, rec, name)
    }

    private class NowPlaying(d: PagePaint) : PagePaint by d {
        override val nowPlaying = true
    }

    private class Queue(d: PagePaint) : PagePaint by d {
        override val queue = true
    }

    private inner class Run(
        private val off: () -> Float = { 0f },
        private val rows: Map<String, Int> = emptyMap(),
        private val sides: Map<String, (Boolean) -> WordSide?> = emptyMap(),
        private val scrolls: Map<String, Int> = emptyMap(),
        private val wraps: Map<String, (PagePaint) -> PagePaint> = emptyMap(),
        private val kinds: Map<String, PageKind> = emptyMap(),
        private val bars: Map<String, Pair<ThumbRect, ThumbFade>> = emptyMap(),
        private val onSwap: (String, Long) -> Unit = { _, _ -> },
        private val forms: MoveForms = this@MovePaintTest.forms,
        val g: Geo = base,
        private val home: Scale = g.s,
    ) {
        val drawn = mutableListOf<Drawn>()
        val homeDrawn = mutableListOf<String>()
        val homePaint = Home(homeDrawn, g)
        val pages = mutableMapOf<String, ListPage>()
        val move = PageMove()
        val paint = MovePaint(move)
        var ends = 0
        var row = -1
        val stack = PageStack<String>(forms, { PageKind.Plain }, move = move, swapped = onSwap) { t ->
            ends++
            row = t.row
            paint.ends(t, HomeSide(home, homePaint, off()), t.from?.let { PageSide(g.s, side(it)) }, t.to?.let { PageSide(g.s, side(it)) }, forms)
        }

        fun side(p: String): PagePaint = page(p).let { wraps[p]?.invoke(it) ?: it }

        fun page(p: String): ListPage = ListPage(rows[p] ?: ROWS, drawn, sides[p] ?: { null }, scrolls[p] ?: 0, g, kinds[p] ?: PageKind.Plain, bars[p]).also { pages[p] = it }

        fun draw(at: Long): Track {
            val rec = Track()
            drawn.clear()
            homeDrawn.clear()
            homePaint.where.clear()
            homePaint.track = rec
            val w = g.s.widthPx
            val h = g.s.heightPx
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(ImageBitmap(w, h)), Size(w.toFloat(), h.toFloat())) {
                with(paint) { drawMove(at, rec) }
            }
            return rec
        }

        fun arrive(): List<Drawn> = drawn.filter { it.name == "arrive" }
    }

    private fun bandTops(): List<Int> {
        val y = mutableListOf(top)
        var r = 1
        while (r < ROWS && top + r * pitch < bottom) {
            y += top + r * pitch
            r++
        }
        y += maxOf(bottom, y.last() + 1)
        return y
    }

    private fun side(text: String, header: Boolean) =
        if (header) WordSide(text, HEADER_S, HEADER_LOOK, HEADER_X, HEADER_Y, -1, true, chrome.header) else WordSide(text, PIVOT_S, PIVOT_LOOK, PIVOT_X, PIVOT_Y, 0, false, chrome.header)

    private fun morphRun(pivotText: () -> String): Run {
        val r = Run(sides = mapOf("a" to { p: Boolean -> if (p) side(pivotText(), false) else null }, "b" to { p: Boolean -> if (p) null else side(B, true) }))
        assertTrue(forms.wordMorph)
        return r
    }

    private class RowWord(d: PagePaint, val text: String, val hidden: MutableList<Int>) : PagePaint by d {
        override fun rowSide(row: Int): WordSide? = if (row == ROW) WordSide(text, ROW_S, ROW_LOOK, ROW_X, ROW_Y, -1, false, header ?: Word.none) else null

        override fun hideRow(row: Int) {
            hidden += row
        }
    }

    private fun rowRun(text: String, hidden: MutableList<Int>, pivot: Boolean = false, bars: Map<String, Pair<ThumbRect, ThumbFade>> = emptyMap()) = Run(
        sides = mapOf("a" to { p: Boolean -> if (p && pivot) side(A, false) else null }, "b" to { p: Boolean -> if (p) null else side(B, true) }),
        wraps = mapOf("a" to { d: PagePaint -> RowWord(d, text, hidden) }),
        bars = bars,
    )

    private fun rowPopped(r: Run): Long {
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3, row = ROW)
        r.stack.frame(0L)
        r.stack.frame(LAND)
        r.stack.frame(LAND + FRAME)
        r.paint.landed()
        r.stack.pop(MoveKind.T4)
        r.stack.frame(LAND * 2)
        return LAND * 2
    }

    private fun cubicOut(p: Float): Float = 1 - (1 - p) * (1 - p) * (1 - p)

    @Test
    fun theT2WordStartsAtTheHomeItemBaselineLessTheOffsetRoundedOnce() {
        val r = Run({ OFF })
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val e = r.paint.ctx.ends
        assertEquals(Math.round((Tok.homeFirstBaseline + 2 * Tok.homePitch - OFF) * s.stdPpu).toFloat(), e.wordY0, 0f)
        assertEquals(rolePx(Roles.homeItem, s, s.stdPpu).toFloat() / rolePx(Roles.pageHeader, s, s.stdPpu), e.wordS0, 1e-6f)
        assertEquals(chrome.headerRestX(), e.wordX1, 0f)
        assertEquals(Tok.qpPeekAlpha, e.peek0, 0f)
        assertFalse(e.zoom)
        assertTrue(r.paint.ctx.rows)
    }

    @Test
    fun aT2RetargetedIntoT4hFreezesTheZoomedBands() {
        val r = Run({ OFF }, forms = zoomForms)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val y0 = r.paint.ctx.ends.wordY0
        val swap = r.move.swapAt
        r.stack.frame(swap)
        val at = swap + ENTER
        assertEquals(Phase.Enter, r.stack.phase)
        val sy = r.move.at(Part.Content, Prop.S, at)
        val dx = Math.round(r.move.at(Part.Content, Prop.X, at)).toFloat()
        assertTrue(sy < 1f)
        r.stack.back { MoveKind.T4h }
        r.stack.frame(at)
        val c = r.paint.ctx
        assertEquals(MoveKind.T4h, c.kind)
        assertEquals(2, r.ends)
        assertEquals(y0, c.ends.wordY1, 0f)
        val b = requireNotNull(c.leaveBands)
        assertEquals(bandTops(), b.y.toList())
        assertTrue(b.dy.all { it == 0 })
        val held = requireNotNull(c.leaveAt)
        assertEquals(sy, held.s, 0f)
        assertEquals(dx, held.dx, 0f)
        assertEquals(xa, held.xa, 0f)
        assertEquals(ya, held.ya, 0f)
        assertTrue(c.ends.leaveRows)
        assertEquals(1444f, anchor, 0f)
        assertEquals(anchor, c.leaveAnchor, 0f)
        val ox = dx + (1 - sy) * xa
        val oy = (1 - sy) * ya
        var draws = 0
        for (t in listOf(at + 1, at + LATER)) {
            if (r.move.at(Part.Leave, Prop.A, t) <= 0f) continue
            draws++
            val ls = r.move.at(Part.Leave, Prop.S, t)
            val lx = Math.round(r.move.at(Part.Leave, Prop.X, t)).toFloat()
            if (t == at + 1) {
                assertEquals("S 1 ms into the carried T4h", 0.97294f, ls, 1e-4f)
                assertEquals("round(X) 1 ms into the carried T4h", 8f, lx, 0f)
            }
            assertTrue(ls < 1f)
            r.draw(t)
            val leave = r.drawn.filter { it.name == "leave" }
            assertEquals(b.dy.size, leave.size)
            leave.forEachIndexed { k, d ->
                assertEquals(b.first + k, d.row)
                assertEquals(sy * ls, d.k, 1e-5f)
                assertEquals(ox + sy * (lx + (1 - ls) * xa), d.dx, 0.01f)
                assertEquals(oy + sy * (ls * d.y + (1 - ls) * anchor), d.top, 0.01f)
            }
            assertTrue(r.drawn.none { it.name == "arrive" })
            assertTrue("home" in r.homeDrawn)
        }
        assertTrue(draws > 0)
    }

    @Test
    fun aT2FrameZoomsTheArrivingRowsAboutHubEdgeAndTheRowsPivot() {
        assertEquals(60f, xa, 0f)
        assertEquals(1232.3f, ya, 0.01f)
        assertEquals(360, top)
        val r = Run(forms = zoomForms)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val c = r.paint.ctx
        assertTrue(c.lift)
        assertEquals(xa, c.xa, 0f)
        assertEquals(ya, c.ya, 0f)
        assertEquals(Tok.motion_rowsDx * s.stdPpu, c.ends.rowsDx, 0f)
        val typed = listOf(
            listOf(17f, 0.80375f, 57f, 68.775f, 241.834f, 531.186f),
            listOf(100f, 0.86625f, 39f, 47.025f, 164.82f, 476.67f),
            listOf(200f, 0.93005f, 20f, 24.197f, 86.198f, 421.017f),
        )
        for (row in typed) {
            val t = row[0]
            val sy = row[1]
            val dx = row[2]
            val rx = row[3]
            val ry = row[4]
            val row0 = row[5]
            val at = t.toLong()
            assertEquals(sy, r.move.at(Part.Content, Prop.S, at), 1e-4f)
            assertEquals(dx, Math.round(r.move.at(Part.Content, Prop.X, at)).toFloat(), 0f)
            r.draw(at)
            val last = r.pages.getValue("drawer").last()
            val a = r.arrive().take(last + 1)
            assertEquals((0..last).toList(), a.map { it.row })
            for (d in a) {
                assertEquals("k at $t", sy, d.k, 1e-4f)
                assertEquals("x at $t", rx, d.dx, 0.01f)
                assertEquals("top of row ${d.row} at $t", ry + sy * d.y, d.top, 0.02f)
                assertEquals("bottom of row ${d.row} at $t", ry + sy * (d.y + pitch), d.bottom, 0.02f)
            }
            assertEquals("row 0 at $t", row0, a[0].top, 0.01f)
        }
    }

    @Test
    fun aT2FrameOnThePageBaseTakesTheArrivingSidesScale() {
        val none = Insets(0, 0, 0, 0)
        val win = Window(1080, 2160, 35, none, none, none, none, none, Dpi(403.2f, 403.2f, 480, 1080, 2160, 1, 1080, 2160))
        val ps = scale(win, Nav.None, Surface.Page, false)
        assertNotNull(ps.fit)
        assertEquals(2.9651f, ps.stdPpu, 1e-4f)
        val hs = scale(win, Nav.None, Surface.Home, false)
        assertEquals(4.5f, hs.stdPpu, 0f)
        val g = geoOf(ps)
        val r = Run(forms = MoveForms.board(ps.board), g = g, home = hs)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val c = r.paint.ctx
        assertTrue(c.xa != Tok.hubEdge * hs.stdPpu)
        val pxa = Tok.hubEdge * ps.stdPpu
        val pya = 2160 - Tok.motion_rowsYa * ps.stdPpu
        assertEquals(59.30f, pxa, 0.01f)
        assertEquals(2112.85f, pya, 0.01f)
        assertEquals(pxa, c.xa, 0f)
        assertEquals(pya, c.ya, 0f)
        assertEquals(Tok.motion_rowsDx * ps.stdPpu, c.ends.rowsDx, 0f)
        assertEquals(60.79f, c.ends.rowsDx, 0.01f)
        assertEquals(120f, ps.board.page_contentTop, 0f)
        assertEquals(px(120f, ps.stdPpu), g.top)
        val at = 17L
        val sy = r.move.at(Part.Content, Prop.S, at)
        assertEquals(0.80375f, sy, 1e-4f)
        assertEquals(57f, Math.round(r.move.at(Part.Content, Prop.X, at)).toFloat(), 0f)
        r.draw(at)
        val a = r.arrive()
        assertTrue(a.isNotEmpty())
        for (d in a) {
            assertEquals(68.64f, d.dx, 0.01f)
            assertEquals(414.64f + sy * d.y, d.top, 0.02f)
        }
    }

    @Test
    fun aZoomFormT2DrawsNoArrivingRowBefore17Ms() {
        val r = Run(forms = zoomForms)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        r.draw(16L)
        assertTrue(r.arrive().isEmpty())
        r.draw(17L)
        val a = r.arrive()
        assertTrue(a.isNotEmpty())
        assertTrue(a.all { Math.abs(it.alpha - Tok.motion_rowsT2Alpha) < 1e-4f })
    }

    @Test
    fun aT2FrameAtRestDrawsTheRowsWhole() {
        val r = Run(forms = zoomForms)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        for (t in listOf(Tok.motion_rows.toLong(), Tok.motion_rows.toLong() + 10)) {
            r.draw(t)
            val a = r.arrive()
            assertTrue(a.isNotEmpty())
            for (d in a) {
                assertEquals(1f, d.k, 0f)
                assertEquals(0f, d.dx, 0f)
                assertEquals(0f, d.dy, 0f)
                assertEquals(d.y.toFloat(), d.top, 0f)
                assertEquals(1f, d.alpha, 0f)
            }
        }
    }

    @Test
    fun aT3FrameZoomsTheArrivingRowsAsOneBandLayer() {
        val r = Run(forms = zoomForms)
        r.stack.pages.add(A)
        r.stack.push(B, MoveKind.T3)
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.rows)
        assertTrue(r.paint.ctx.lift)
        r.stack.frame(r.move.swapAt)
        val at = r.move.swapAt + ENTER
        assertEquals(207L, at)
        val sy = r.move.at(Part.Content, Prop.S, at)
        assertEquals(0.92468f, sy, 1e-4f)
        assertEquals(22f, Math.round(r.move.at(Part.Content, Prop.X, at)).toFloat(), 0f)
        assertTrue(r.move.at(Part.Content, Prop.A, at) > 0f)
        r.draw(at)
        val y = bandTops()
        val a = r.arrive().take(y.size - 1)
        assertEquals((0 until y.size - 1).toList(), a.map { it.row })
        for (d in a) {
            assertEquals("k of row ${d.row}", sy, d.k, 0f)
            assertEquals("x of row ${d.row}", 26.519f, d.dx, 0.01f)
            assertEquals("y of row ${d.row}", 92.811f, d.dy, 0.01f)
            assertEquals("top of row ${d.row}", 92.811f + sy * d.y, d.top, 0.01f)
            assertEquals("bar clip of row ${d.row}", d.dx + sy * barX, d.clip[2], 0.01f)
        }
    }

    @Test
    fun aT2IntoAListWhoseLastBandTopIsBelowTheRowsPivotDrawsItsTailRows() {
        val r = Run(scrolls = mapOf(B to 70), forms = zoomForms)
        r.stack.push(B, MoveKind.T2)
        r.stack.frame(0L)
        val p = r.pages.getValue(B)
        assertEquals(5, p.last())
        assertEquals(1280, p.rowTop(6))
        val hb = requireNotNull(p.bandTops()).last()
        assertEquals(1280, hb)
        assertTrue(hb > ya)
        val at = 100L
        val sy = r.move.at(Part.Content, Prop.S, at)
        assertTrue(sy < 1f)
        assertTrue(r.move.at(Part.Content, Prop.A, at) > 0f)
        r.draw(at)
        assertEquals(13, r.paint.ctx.tail)
        val tail = r.arrive().filter { it.row == 6 }
        assertEquals("tail rows ${r.arrive().map { it.row }}", 1, tail.size)
        val d = tail[0]
        val ry = (1 - sy) * ya
        assertEquals(sy, d.k, 0f)
        assertEquals(ry + sy * hb, d.top, 0.01f)
        assertEquals(1273.62f, d.clip[1], 0.01f)
        assertTrue(ry + sy * (hb + 13) > hb)
        assertEquals("tail clip ${d.clip}", hb.toFloat(), d.clip[3], 0f)
    }

    @Test
    fun aQueuePushFromNowPlayingCutsTheLeavingPage() {
        val r = Run(wraps = mapOf(A to { p: PagePaint -> NowPlaying(p) }, B to { p: PagePaint -> Queue(p) }), forms = zoomForms)
        r.stack.pages.add(A)
        r.stack.push(B, MoveKind.T3)
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.ends.npQueue)
        assertTrue(r.move.cut)
        for (at in listOf(0L, 17L, r.move.swapAt + ENTER)) {
            assertEquals("leave alpha at $at", 0f, r.move.at(Part.Leave, Prop.A, at), 0f)
            r.draw(at)
            assertTrue("leave rows at $at", leaveOf(r).isEmpty())
        }
    }

    @Test
    fun aPivotMorphPushZoomsTheLeavingPageAboutTheTappedRowCentre() {
        val r = morphRun { A }
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3, row = 3)
        r.stack.frame(0L)
        val c = r.paint.ctx
        assertTrue(c.ends.morph)
        assertFalse(c.rows)
        val p = r.pages.getValue("a")
        assertEquals(855, p.rowTop(3))
        assertEquals(p.rowTop(3) + pitch / 2f, c.ends.tapY, 0f)
        assertEquals(937.5f, c.ends.tapY, 0f)
        assertEquals(s.widthPx / 2f, c.ends.tapX, 0f)
        val at = 117L
        val ls = r.move.at(Part.Leave, Prop.S, at)
        assertEquals(1.11f, ls, 1e-4f)
        assertTrue(r.move.at(Part.Leave, Prop.A, at) > 0f)
        r.draw(at)
        val leave = p.restAt.filter { it.name == "leave" }
        assertEquals(1, leave.size)
        assertEquals(ls, leave[0].k, 0f)
        assertEquals(-39.6f, leave[0].dx, 0.01f)
        assertEquals("about y ${c.ends.tapY}, not ${s.heightPx / 2f}", -103.125f, leave[0].dy, 0.01f)
    }

    @Test
    fun aLargePageRoundsTheRowsPivotAndShiftToStandardPx() {
        val large = scale(dev.window(Nav.None), Nav.None, Surface.Page, true)
        assertTrue(large.large)
        assertTrue(large.ppu != large.stdPpu)
        val g = geoOf(large)
        val r = Run(forms = MoveForms.board(large.board), g = g)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val c = r.paint.ctx
        assertTrue(c.lift)
        assertEquals(Math.round(Tok.hubEdge * large.stdPpu).toFloat(), c.xa, 0f)
        assertEquals(large.heightPx - Math.round(Tok.motion_rowsYa * large.stdPpu).toFloat(), c.ya, 0f)
        assertEquals(1232f, c.ya, 0f)
        assertEquals(Math.round(Tok.motion_rowsDx * large.stdPpu).toFloat(), c.ends.rowsDx, 0f)
        assertEquals(62f, c.ends.rowsDx, 0f)
        val std = Run(forms = zoomForms)
        std.stack.push("drawer", MoveKind.T2)
        std.stack.frame(0L)
        assertEquals(1232.3f, std.paint.ctx.ya, 0.01f)
        assertEquals(61.5f, std.paint.ctx.ends.rowsDx, 0f)
    }

    @Test
    fun theT2WordBottomTakesTheSpanOfItsKeys() {
        val r = Run({ OFF }, forms = zoomForms)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val at = 100L
        val v = r.move
        val sc = v.at(Part.Word, Prop.S, at)
        val y = v.at(Part.Word, Prop.Y, at)
        val ss = requireNotNull(v.span(Part.Word, Prop.S))
        val ys = requireNotNull(v.span(Part.Word, Prop.Y))
        val p = minOf((sc - ss.first) / (ss.second - ss.first), (y - ys.first) / (ys.second - ys.first))
        assertTrue(p > 0f && p < 1f)
        val want = wordBottom(s, y, sc, p, true)
        assertTrue(want != wordBottom(s, y, sc, 1f, true))
        val rec = r.draw(at)
        assertEquals(want.coerceIn(0, s.heightPx).toFloat(), rec.wordClips.getValue("word")[3], 0f)
    }

    @Test
    fun aTallNavBarKeepsTheZoomedRowsAboveTheContentBottom() {
        val ns = window(1080, 2160, Nav.Buttons, 132)
        assertTrue(ns.navOn)
        assertEquals(4.5f, ns.stdPpu, 0f)
        assertTrue(132 / ns.stdPpu > Tok.motion_rowsYa)
        val cut = px(ns.stdContentBottom, ns.stdPpu).toFloat()
        assertEquals(2028f, cut, 0f)
        val g = geoOf(ns)
        val r = Run(forms = MoveForms.board(ns.board), g = g)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val c = r.paint.ctx
        assertEquals(cut, c.cut)
        assertEquals(2088.45f, c.ya, 0.01f)
        assertEquals(cut, g.bottom.toFloat(), 0f)
        val sy = r.move.at(Part.Content, Prop.S, 17L)
        assertTrue((1 - sy) * c.ya + sy * g.bottom > cut)
        r.draw(17L)
        val a = r.arrive()
        assertTrue(a.isNotEmpty())
        for (d in a) assertTrue("row ${d.row} clip ${d.clip}", d.clip[3] <= cut + 0.01f)
    }

    private fun retargeted(r: Run, at: Long) {
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        r.stack.frame(r.move.swapAt)
        assertEquals(Phase.Enter, r.stack.phase)
        r.stack.back { MoveKind.T4h }
        r.stack.frame(at)
        assertEquals(MoveKind.T4h, r.move.kind)
    }

    @Test
    fun aFrozenZoomAtScaleOneComposesToThePlainClose() {
        val r = Run(forms = zoomForms)
        val at = Tok.motion_rows.toLong() + 2
        retargeted(r, at)
        val held = requireNotNull(r.paint.ctx.leaveAt)
        val b = requireNotNull(r.paint.ctx.leaveBands)
        assertEquals(1f, held.s, 0f)
        assertEquals(0f, held.dx, 0f)
        val plain = closing(forms = zoomForms)
        assertEquals(plain.paint.ctx.leaveAnchor, r.paint.ctx.leaveAnchor, 0f)
        assertEquals(requireNotNull(plain.paint.ctx.leaveBands).y.toList(), b.y.toList())
        for (e in listOf(1L, LATER)) {
            r.draw(at + e)
            plain.draw(e)
            val got = r.drawn.filter { it.name == "leave" }
            val want = plain.drawn.filter { it.name == "leave" }
            assertEquals(b.dy.size, got.size)
            assertEquals(want.map { it.row }, got.map { it.row })
            got.zip(want).forEach { (g, w) ->
                assertEquals("k at $e", w.k, g.k, 1e-6f)
                assertEquals("x at $e", w.dx, g.dx, 0.01f)
                assertEquals("top at $e", w.top, g.top, 0.01f)
            }
        }
    }

    @Test
    fun aT2IntoAListWithNoVisibleRowRetargetedLeavesItsZoomedRestAsOneBandAboutThePopAnchor() {
        val r = Run(rows = mapOf("drawer" to 0), forms = zoomForms)
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        r.stack.frame(r.move.swapAt)
        val at = r.move.swapAt + ENTER
        val sy = r.move.at(Part.Content, Prop.S, at)
        val dx = Math.round(r.move.at(Part.Content, Prop.X, at)).toFloat()
        assertTrue(sy < 1f)
        assertTrue(r.move.at(Part.Content, Prop.A, at) > 0f)
        r.stack.back { MoveKind.T4h }
        r.stack.frame(at)
        val c = r.paint.ctx
        assertEquals(MoveKind.T4h, c.kind)
        assertTrue(c.ends.leaveRows)
        assertEquals(listOf(360, 1280), requireNotNull(c.leaveBands).y.toList())
        assertEquals(1444f, c.leaveAnchor, 0f)
        val held = requireNotNull(c.leaveAt)
        assertEquals(sy, held.s, 0f)
        assertEquals(dx, held.dx, 0f)
        val t = at + 1
        val ls = r.move.at(Part.Leave, Prop.S, t)
        val lx = Math.round(r.move.at(Part.Leave, Prop.X, t)).toFloat()
        assertEquals(0.97294f, ls, 1e-4f)
        assertEquals(8f, lx, 0f)
        assertTrue(r.move.at(Part.Leave, Prop.A, t) > 0f)
        r.draw(t)
        val leave = r.pages.values.flatMap { it.restAt }.filter { it.name == "leave" }
        assertEquals(1, leave.size)
        val k = sy * ls
        val ox = dx + (1 - sy) * xa + sy * (lx + (1 - ls) * xa)
        val oy = (1 - sy) * ya + sy * (1 - ls) * 1444f
        assertEquals(k, leave[0].k, 1e-5f)
        assertEquals(ox, leave[0].dx, 0.01f)
        assertEquals(oy, leave[0].dy, 0.01f)
        assertEquals(ox, leave[0].clip[0], 0.01f)
        assertEquals(oy + k * 360, leave[0].clip[1], 0.01f)
        assertEquals(ox + k * 708, leave[0].clip[2], 0.01f)
        assertEquals(oy + k * 1280, leave[0].clip[3], 0.01f)
        assertTrue(leaveOf(r).isEmpty())
    }

    @Test
    fun aT3IntoAListWithNoVisibleRowRetargetedIntoT4LeavesItsRestAsOneBandUnderTheHeldPlace() {
        val r = Run(rows = mapOf("b" to 0), forms = zoomForms)
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3)
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.rows)
        r.stack.frame(r.move.swapAt)
        val at = r.move.swapAt + ENTER
        val sy = r.move.at(Part.Content, Prop.S, at)
        val dx = Math.round(r.move.at(Part.Content, Prop.X, at)).toFloat()
        assertTrue("the T3 content is drawn at the retarget", r.move.at(Part.Content, Prop.A, at) > 0f)
        r.stack.back { MoveKind.T4 }
        r.stack.frame(at)
        val c = r.paint.ctx
        assertEquals(MoveKind.T4, c.kind)
        assertEquals(listOf(360, 1280), requireNotNull(c.leaveBands).y.toList())
        assertEquals(360f, c.leaveAnchor, 0f)
        val held = requireNotNull(c.leaveAt)
        assertEquals(sy, held.s, 0f)
        assertEquals(dx, held.dx, 0f)
        assertEquals(60f, held.xa, 0f)
        val t = at + 1
        val ls = r.move.at(Part.Leave, Prop.S, t)
        val lx = Math.round(r.move.at(Part.Leave, Prop.X, t)).toFloat()
        val ly = r.move.at(Part.Leave, Prop.Y, t)
        assertEquals("a T4 with no row morph has no leave scale of its own", 1f, ls, 0f)
        assertTrue(r.move.at(Part.Leave, Prop.A, t) > 0f)
        r.draw(t)
        val leave = r.pages.getValue("b").restAt.filter { it.name == "leave" }
        assertEquals(1, leave.size)
        val k = sy * ls
        val ox = dx + (1 - sy) * 60f + sy * (lx + (1 - ls) * 60f)
        val oy = (1 - sy) * ya + sy * (ly + (1 - ls) * 360f)
        assertEquals(k, leave[0].k, 1e-5f)
        assertEquals(ox, leave[0].dx, 0.01f)
        assertEquals(oy, leave[0].dy, 0.01f)
        assertEquals(ox, leave[0].clip[0], 0.01f)
        assertEquals(oy + k * 360, leave[0].clip[1], 0.01f)
        assertEquals(ox + k * 708, leave[0].clip[2], 0.01f)
        assertEquals(oy + k * 1280, leave[0].clip[3], 0.01f)
        assertTrue(leaveOf(r).isEmpty())
    }

    @Test
    fun aTallNavBarKeepsTheFrozenZoomAboveTheContentBottomAfterARetarget() {
        val none = Insets(0, 0, 0, 0)
        val bar = Insets(0, 0, 0, 168)
        val ns = scale(Window(1080, 2160, 35, none, bar, bar, none, none), Nav.Buttons, Surface.Page, false)
        val cut = px(ns.stdContentBottom, ns.stdPpu).toFloat()
        val g = geoOf(ns)
        val r = Run(forms = MoveForms.board(ns.board), g = g)
        retargeted(r, Tok.motion_t2Swap.toLong() + ENTER)
        val c = r.paint.ctx
        assertEquals(cut, c.leaveCut)
        val held = requireNotNull(c.leaveAt)
        assertTrue(held.s < 1f)
        assertTrue((1 - held.s) * held.ya + held.s * g.bottom > cut)
        r.draw(r.move.t0 + 1)
        val leave = r.drawn.filter { it.name == "leave" }
        assertTrue(leave.isNotEmpty())
        for (d in leave) assertTrue("row ${d.row} clip ${d.clip}", d.clip[3] <= cut + 0.01f)
    }

    @Test
    fun aT2nRetargetedIntoT4nWhileTheHeaderZoomsShrinksTheZoomWordOutAsTheLeaveWord() {
        val r = Run()
        r.stack.push("drawer", MoveKind.T2n)
        r.stack.frame(0L)
        r.stack.frame(r.move.swapAt)
        val at = (Tok.motion_headerDelay + Tok.motion_header / 2).toLong()
        assertTrue(at >= r.move.swapAt)
        assertEquals(Phase.Enter, r.stack.phase)
        assertTrue(r.move.at(Part.Zoom, Prop.A, at) > 0f && r.move.at(Part.Header, Prop.A, at) <= 0f)
        val zs = r.move.at(Part.Zoom, Prop.S, at)
        r.stack.back { MoveKind.T4n }
        r.stack.frame(at)
        val lw = requireNotNull(r.paint.ctx.leaveWord)
        assertEquals(chrome.headerAlpha, lw.alpha, 0f)
        assertEquals(chrome.headerRestX(), lw.pen, 0f)
        assertEquals(zs, r.move.at(Part.LeaveWord, Prop.S, at), 0f)
        val rec = r.draw(at + 1)
        assertTrue("leave.zoom" in rec.words)
        assertNull(r.paint.ctx.zoom)
    }

    @Test
    fun aReversedT2DrawsTheDrawerRowsFadingOutAndHome() {
        val r = Run()
        r.stack.push("drawer", MoveKind.T2)
        r.stack.frame(0L)
        val at = r.move.swapAt / 2
        r.stack.back { MoveKind.T4h }
        r.stack.frame(at)
        assertTrue(r.move.reversed)
        val t = at + 1
        assertTrue(r.move.at(Part.Content, Prop.A, t) > 0f)
        r.draw(t)
        assertTrue(r.drawn.any { it.name == "arrive" })
        assertTrue("home" in r.homeDrawn)
    }

    @Test
    fun aT3MorphsThePivotIntoTheHeaderAsOneSpriteOnTheMorphPushLegs() {
        val r = morphRun { A }
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3)
        r.stack.frame(0L)
        val e = r.paint.ctx.ends
        assertTrue(e.morph)
        assertTrue(e.morphHeaderIn)
        assertFalse(e.morphHeaderOut)
        assertEquals(PIVOT_S, e.morphS0, 0f)
        assertEquals(HEADER_S, e.morphS1, 0f)
        val half = (Tok.motion_t4mDelay + Tok.motion_morphPush / 2).toLong()
        val v = r.move
        assertEquals(Tok.motion_t4mDelay + Tok.motion_morphPush, v.wordEnd.toFloat(), 0f)
        assertTrue(e.lhead)
        assertEquals(px(Tok.page_headerBaseline, s.stdPpu).toFloat(), e.lheadBase, 0f)
        assertEquals(chrome.headerAlpha, requireNotNull(r.paint.ctx.leaveWord).alpha, 0f)
        assertFalse(r.paint.ctx.rows)
        assertEquals(1f, v.at(Part.Word, Prop.A, half), 0f)
        assertEquals(PIVOT_S + (HEADER_S - PIVOT_S) * cubicOut(0.5f), v.at(Part.Word, Prop.S, half), 1e-4f)
        assertEquals(PIVOT_X + (HEADER_X - PIVOT_X) * cubicOut(0.5f), v.at(Part.Word, Prop.X, half), 1e-3f)
        assertEquals(PIVOT_Y + (HEADER_Y - PIVOT_Y) * cubicOut(0.5f), v.at(Part.Word, Prop.Y, half), 1e-3f)
        val early = r.draw(50L)
        assertEquals(chrome.headerAlpha * 0.52f, early.alphas.getValue("leave.zoom"), 1e-4f)
        val rec = r.draw(half)
        assertEquals(listOf("morph"), rec.words.filter { it.startsWith("morph") })
        assertEquals(HEADER_LOOK, rec.alphas.getValue("morph"), 1e-4f)
        assertTrue("word" !in rec.words)
        assertTrue("leave.zoom" !in rec.words)
    }

    @Test
    fun aT4MorphsTheHeaderBackIntoThePivotAsOneSpriteOnTheMorphPopLegs() {
        val r = morphRun { A }
        r.stack.pages.addAll(listOf("a", "b"))
        r.stack.pop(MoveKind.T4)
        r.stack.frame(0L)
        val e = r.paint.ctx.ends
        assertTrue(e.morph)
        assertTrue(e.morphHeaderOut)
        assertFalse(e.morphHeaderIn)
        val at = 100L
        val v = r.move
        assertEquals(183, v.wordEnd)
        assertFalse(v.has(Part.LeaveWord, Prop.A))
        assertEquals(0.35f, v.at(Part.Word, Prop.A, at), 1e-4f)
        assertEquals(HEADER_S + (PIVOT_S - HEADER_S) * cubicOut((at - Tok.motion_t4mDelay) / Tok.motion_morphPop), v.at(Part.Word, Prop.S, at), 1e-4f)
        val rec = r.draw(at)
        assertEquals(listOf("morph"), rec.words.filter { it.startsWith("morph") })
        assertEquals(HEADER_LOOK, r.paint.ctx.morphLook, 0f)
        assertEquals(HEADER_LOOK * 0.35f, rec.alphas.getValue("morph"), 1e-4f)
    }

    @Test
    fun aT3RetargetedIntoItsExactReverseRunsTheMorphBackWithNoFades() {
        val r = morphRun { A }
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3)
        r.stack.frame(0L)
        r.stack.frame(r.move.swapAt)
        val at = r.move.swapAt + ENTER
        assertTrue(at < r.move.t0 + r.move.wordEnd)
        val ws = r.move.at(Part.Word, Prop.S, at)
        val mwa = r.move.at(Part.Word, Prop.A, at)
        assertTrue(mwa > 0f)
        r.stack.back { MoveKind.T4 }
        r.stack.frame(at)
        val c = r.paint.ctx
        assertEquals(MoveKind.T4, c.kind)
        assertTrue(c.ends.morphBack)
        assertTrue(c.fades.isEmpty())
        assertEquals(ws, requireNotNull(r.move.leg(Part.Word, Prop.S)).from, 1e-4f)
        assertEquals(mwa, requireNotNull(r.move.leg(Part.Word, Prop.A)).from, 1e-4f)
        assertEquals(listOf("morph"), r.draw(at + 1).words.filter { it.startsWith("morph") || it.startsWith("fade") })
    }

    @Test
    fun aT3RetargetedIntoAnotherMorphFadesTheOldWordOutOverTheLeave() {
        var pivot = A
        val r = morphRun { pivot }
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3)
        r.stack.frame(0L)
        r.stack.frame(r.move.swapAt)
        val at = r.move.swapAt + ENTER
        val mwa = r.move.at(Part.Word, Prop.A, at)
        assertTrue(mwa > 0f)
        pivot = OTHER
        r.stack.back { MoveKind.T4 }
        r.stack.frame(at)
        val c = r.paint.ctx
        assertFalse(c.ends.morphBack)
        assertEquals(1, c.fades.size)
        assertEquals(HEADER_LOOK * mwa, c.fades[0].a, 1e-4f)
        assertEquals(HEADER_S, requireNotNull(r.move.leg(Part.Word, Prop.S)).from, 0f)
        val rec = r.draw(at + 1)
        val k = 1 - 1f / Tok.motion_leave
        assertTrue("fade.arrive" !in rec.words)
        assertEquals(HEADER_LOOK * mwa * k, rec.alphas.getValue("fade.leave"), 1e-4f)
    }

    @Test
    fun aT7TurnsTheArrivingPageInOnTheT7Legs() {
        val r = Run()
        r.stack.pages.add("a")
        r.stack.push("np", MoveKind.T7)
        r.stack.frame(0L)
        val ink = Tok.motion_t7PaneInk
        val at = (ink + 100).toLong()
        val pl = TokBoard.a105
        val x = (at - pl.motion_t7PlaneHold) / (pl.motion_t7PlaneLand - pl.motion_t7PlaneHold)
        assertEquals(pl.motion_t7PlaneAngle * (1 - x) * (1 - x) * (1 - x), r.move.at(Part.NpIn, Prop.R, at), 0.05f)
        val lead = Curves.easeOut.at(17f / Tok.motion_t7Fade)
        val a = lead + (1 - lead) * Curves.easeOut.at(100f / (Tok.motion_t7Fade - 17))
        assertEquals(a, r.move.at(Part.NpIn, Prop.A, at), 1e-3f)
        assertEquals(0f, r.move.at(Part.NpLeave, Prop.A, at), 0f)
        r.draw(at)
        assertTrue(r.drawn.any { it.name == "arrive" })
        assertTrue(r.drawn.none { it.name == "leave" })
    }

    @Test
    fun aT8TurnsNowPlayingOutThenBringsThePageUnderInBandByBand() {
        val r = Run()
        r.stack.pages.add("a")
        r.stack.pages.add("np")
        r.stack.back { MoveKind.T8 }
        r.stack.frame(0L)
        assertFalse(r.paint.ctx.ends.npHome)
        r.draw(100L)
        assertTrue(r.drawn.any { it.name == "leave" })
        assertTrue(r.drawn.none { it.name == "arrive" })
        r.draw(300L)
        assertTrue(r.drawn.none { it.name == "leave" })
        assertTrue(r.drawn.any { it.name == "arrive" })
    }

    @Test
    fun aT8ToHomeTurnsHomeInOnItsOwnCamera() {
        val r = Run()
        r.stack.pages.add("np")
        r.stack.back { MoveKind.T8 }
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.ends.npHome)
        assertEquals(0.66f, r.move.at(Part.NpUnder, Prop.A, 300L), 1e-3f)
        r.draw(100L)
        assertTrue("home" !in r.homeDrawn)
        val rec = r.draw(300L)
        assertTrue("home" in r.homeDrawn)
        assertEquals(listOf("peek" to NP_HOME, "home" to NP_HOME), r.homePaint.where)
        assertEquals(0.66f, rec.quad(NP_HOME).a, 1e-3f)
        assertFalse(requireNotNull(r.homePaint.look).watermark)
        assertEquals(Tok.qpPeekAlpha, r.homePaint.peekA, 0f)
    }

    @Test
    fun aT7FromTheHomeRingTurnsHomeOutWithItsPeekAndNoWatermark() {
        for (form in listOf(NpForm.Ring, NpForm.Page)) {
            val r = Run()
            r.paint.npForm = form
            r.stack.push("np", MoveKind.T7)
            r.stack.frame(0L)
            assertEquals(1f, r.move.at(Part.NpLeave, Prop.A, 100L), 0f)
            val rec = r.draw(100L)
            assertEquals(1f, rec.quad(NP_LEAVE).a, 0f)
            assertEquals("$form", listOf("peek" to NP_LEAVE, "home" to NP_LEAVE), r.homePaint.where)
            assertFalse(requireNotNull(r.homePaint.look).watermark)
            assertEquals(Tok.qpPeekAlpha, r.homePaint.peekA, 0f)
        }
    }

    @Test
    fun aT8BringsThePageUnderInBandByBandEachOnItsOwnPart() {
        assertEquals(360, top)
        assertEquals(165, pitch)
        assertEquals(1280, bottom)
        assertEquals(63f, s.board.pivotTop, 0f)
        val r = Run()
        r.stack.pages.add("a")
        r.stack.pages.add("np")
        r.stack.back { MoveKind.T8 }
        r.stack.frame(0L)
        val at = 342L
        val rec = r.draw(at)
        val want = listOf(
            Triple(Part.NpUHeader, Edges(0, 0, 720, 189), 0.48f),
            Triple(Part.NpUnder, Edges(0, 189, 720, 360), 0.855f),
            Triple(Part.NpURow0, Edges(0, 360, 720, 525), 0.79f),
            Triple(Part.NpURow1, Edges(0, 525, 720, 690), 0.835f),
            Triple(Part.NpURow2, Edges(0, 690, 720, 855), 0.645f),
            Triple(Part.NpURow3, Edges(0, 855, 720, 1020), 0.51f),
            Triple(Part.NpURows, Edges(0, 1020, 720, 1185), 0.475f),
            Triple(Part.NpURows, Edges(0, 1185, 720, 1280), 0.475f),
        )
        assertEquals(want.map { NP_UNDER + it.first.name }, rec.quads.map { it.name })
        val deg = r.move.at(Part.NpUnder, Prop.R, at).toDouble()
        assertEquals(0.4642f, Tok.motion_t8InEye, 0f)
        assertEquals(1155f, Tok.motion_t8InD, 0f)
        val (wx, wy) = turnAt(720.0, 640.0, deg, 0.0, 594.0, 3465.0)
        for ((i, q) in rec.quads.withIndex()) {
            val (_, b, a) = want[i]
            assertEquals("band $i", listOf(b.x0, b.y0, b.x1, b.y1), requireNotNull(q.src).let { listOf(it.x0, it.y0, it.x1, it.y1) })
            assertEquals("band $i alpha", a, q.a, 1e-3f)
            val o = q.t.map(720f, 640f)
            assertEquals("band $i x", wx, o.x.toDouble(), 0.05)
            assertEquals("band $i y", wy, o.y.toDouble(), 0.05)
        }
    }

    @Test
    fun aT8OntoAnEmptyListBringsItsContentInOnTheFirstRowPart() {
        val r = Run(rows = mapOf("e" to 0))
        r.stack.pages.add("e")
        r.stack.pages.add("np")
        r.stack.back { MoveKind.T8 }
        r.stack.frame(0L)
        val rec = r.draw(342L)
        assertEquals(listOf(Part.NpUHeader, Part.NpUnder, Part.NpURow0).map { NP_UNDER + it.name }, rec.quads.map { it.name })
        assertEquals(listOf(0, 360, 720, 1280), requireNotNull(rec.quads[2].src).let { listOf(it.x0, it.y0, it.x1, it.y1) })
        assertEquals(0.79f, rec.quads[2].a, 1e-3f)
    }

    @Test
    fun aT8ToTheQuickplayFrontTurnsTheThirdColumnEdgeOntoItsRightKey() {
        val r = Run()
        r.homePaint.pane = Edges(0, 0, 400, 1280)
        r.paint.npForm = NpForm.Front
        r.stack.pages.add("np")
        r.stack.back { MoveKind.T8 }
        r.stack.frame(0L)
        val at = 284L
        val rec = r.draw(at)
        assertEquals(listOf(NP_PEEK, NP_PANE), rec.quads.map { it.name })
        val q = rec.quad(NP_PANE)
        assertEquals(17f / 33f, q.a, 1e-3f)
        assertEquals(listOf(101, 0, 551, 1280), requireNotNull(q.src).let { listOf(it.x0, it.y0, it.x1, it.y1) })
        val l = 27.8 * 3
        val rk = -65.7 * 3
        assertEquals(l, r.move.at(Part.NpUnder, Prop.X, at).toDouble(), 1e-3)
        assertEquals(rk, r.move.at(Part.NpEdge, Prop.X, at).toDouble(), 1e-3)
        val o = q.t.map(551f, 594f)
        assertEquals(Math.round(l) + 551 + (rk - l), o.x.toDouble(), 0.5)
        assertEquals(594.0, o.y.toDouble(), 0.05)
        assertEquals(listOf("frontPeek" to NP_PEEK, "front" to NP_PANE), r.homePaint.where)
    }

    @Test
    fun aT7FromTheQuickplayFrontTurnsItsPeekAboutThePeekRect() {
        val r = Run()
        val pane = Edges(0, 0, 400, 1280)
        r.homePaint.pane = pane
        r.paint.npForm = NpForm.Front
        r.stack.push("np", MoveKind.T7)
        r.stack.frame(0L)
        val at = 190L
        val rec = r.draw(at)
        val deg = r.move.at(Part.NpPeek, Prop.R, at).toDouble()
        assertEquals(25.84, deg, 1e-3)
        val pr = Edges(551, 398, 720, 986)
        val q = rec.quad(NP_PEEK)
        assertEquals(listOf(pr.x0, pr.y0, pr.x1, pr.y1), requireNotNull(q.src).let { listOf(it.x0, it.y0, it.x1, it.y1) })
        val dx = Math.round(r.move.at(Part.NpPeek, Prop.X, at)).toDouble()
        val dy = Math.round(r.move.at(Part.NpPeek, Prop.Y, at)).toDouble()
        val (wx, wy) = turnAt(640.0, 300.0, deg, 551.0, 692.0, 1656.0)
        val o = q.t.map(640f, 300f)
        assertEquals(dx + wx, o.x.toDouble(), 0.05)
        assertEquals(dy + wy, o.y.toDouble(), 0.05)
        assertEquals(listOf(101, 0, 551, 1280), requireNotNull(rec.quad(NP_LEAVE).src).let { listOf(it.x0, it.y0, it.x1, it.y1) })
        assertEquals(listOf("front" to NP_LEAVE, "frontPeek" to NP_PEEK), r.homePaint.where)
    }

    @Test
    fun aT7FromAPageDrawsItsLeaveFirstOnTheLeaveCameraAndFromHomeLast() {
        val at = 300L
        val r = Run()
        r.stack.pages.add("a")
        r.stack.push("np", MoveKind.T7)
        r.stack.frame(0L)
        val rec = r.draw(at)
        val names = rec.quads.map { it.name }
        assertEquals(NP_LEAVE, names.first())
        assertTrue(names.contains(NP_PART + Part.NpIn.name))
        val deg = -68.29 + (-85.93 + 68.29) * 50 / 85
        val slide = Math.round(205.53 + (254.1 - 205.53) * 50 / 76).toDouble()
        val hinge = Math.round(-27.66 * 3).toDouble()
        assertEquals(237.0, slide, 0.0)
        assertEquals(deg, r.move.at(Part.NpLeave, Prop.R, at).toDouble(), 1e-3)
        assertEquals(slide, Math.round(r.move.at(Part.NpLeave, Prop.X, at)).toDouble(), 0.0)
        assertEquals(hinge, Math.round(r.move.at(Part.NpLeave, Prop.PX, at)).toDouble(), 0.0)
        assertEquals(0.5086f, s.board.motion_t7EyeY, 0f)
        assertEquals(950f, Tok.motion_t7PaneD, 0f)
        val m = rec.quad(NP_LEAVE).t
        for ((x, y) in listOf(0.0 to 0.0, 720.0 to 0.0, 360.0 to 640.0, 720.0 to 1280.0)) {
            val o = m.map(x.toFloat(), y.toFloat())
            val (bx, by) = ay(o.x - slide, o.y.toDouble(), deg, hinge, 651.0, 2850.0, 360.0 - slide)
            assertEquals("leave x ($x, $y)", x, bx, 0.05)
            assertEquals("leave y ($x, $y)", y, by, 0.05)
        }
        val h = Run()
        h.paint.npForm = NpForm.Ring
        h.stack.push("np", MoveKind.T7)
        h.stack.frame(0L)
        assertEquals(NP_LEAVE, h.draw(at).quads.last().name)
    }

    @Test
    fun aT4nFromAPageWithNoHomeItemTurnsThePageOutAndHomeIn() {
        val r = Run(wraps = mapOf("t" to { p: PagePaint -> NoItem(p) }))
        r.stack.pages.add("t")
        r.stack.back { MoveKind.T4n }
        r.stack.frame(0L)
        val e = r.paint.ctx.ends
        assertTrue(e.turn)
        assertEquals(3f, e.homeU, 0f)
        val out = r.draw(100L)
        assertEquals(listOf(TURN_LEAVE), out.quads.map { it.name })
        assertEquals(1f, out.quad(TURN_LEAVE).a, 0f)
        assertEquals(-151.6f, Tok.motion_t8OutHinge, 0f)
        assertEquals(0.5029f, Tok.motion_t8OutEye, 0f)
        val ld = r.move.at(Part.Leave, Prop.R, 100L).toDouble()
        assertTrue(ld > 0.0)
        val (lx, ly) = turnAt(720.0, 640.0, ld, -455.0, 644.0, 1656.0)
        val lo = out.quad(TURN_LEAVE).t.map(720f, 640f)
        assertEquals(lx, lo.x.toDouble(), 0.05)
        assertEquals(ly, lo.y.toDouble(), 0.05)
        val at = 320L
        val rec = r.draw(at)
        assertEquals(listOf(TURN_HOME), rec.quads.map { it.name })
        assertEquals(1f, rec.quad(TURN_HOME).a, 0f)
        val x = 103.0 / 418
        val deg = -67.2 * (1 - x) * (1 - x) * (1 - x)
        val slide = Math.round(-4.3 * 3 * (1 - 103.0 / 203)).toDouble()
        val hinge = Math.round(7.9 * 3).toDouble()
        assertEquals(-6.0, slide, 0.0)
        assertEquals(deg, r.move.at(Part.HomeTurn, Prop.R, at).toDouble(), 0.05)
        val m = rec.quad(TURN_HOME).t
        for ((px, py) in listOf(0.0 to 0.0, 720.0 to 0.0, 360.0 to 640.0, 720.0 to 1280.0)) {
            val o = m.map(px.toFloat(), py.toFloat())
            val (bx, by) = ay(o.x - slide, o.y.toDouble(), deg, hinge, 637.0, 2304.0, 360.0 - slide)
            assertEquals("home x ($px, $py)", px, bx, 0.05)
            assertEquals("home y ($px, $py)", py, by, 0.05)
        }
        assertEquals(listOf("peek" to null, "home" to TURN_HOME), r.homePaint.where)
        assertEquals(Tok.qpPeekAlpha, r.homePaint.peekA, 0f)
        assertEquals(Math.round(r.move.at(Part.Peek, Prop.X, at)).toFloat(), r.homePaint.peekX, 0f)
        val look = requireNotNull(r.homePaint.look)
        assertTrue(look.watermark)
        assertEquals((320f - 277f) / 75f * Roles.homeSubtitle.alpha, look.sub, 1e-3f)
    }

    @Test
    fun aT4nTurnsOnlyForAMorphPopOfAPageWithNoHomeItem() {
        val item = Run()
        item.stack.pages.add("a")
        item.stack.back { MoveKind.T4n }
        item.stack.frame(0L)
        assertFalse(item.paint.ctx.ends.turn)
        val home = Run(wraps = mapOf("t" to { p: PagePaint -> NoItem(p) }))
        home.stack.pages.add("t")
        home.stack.home(MoveKind.T4n)
        home.stack.frame(0L)
        assertFalse(home.paint.ctx.ends.turn)
        val t4h = Run(wraps = mapOf("t" to { p: PagePaint -> NoItem(p) }))
        t4h.stack.pages.add("t")
        t4h.stack.back { MoveKind.T4h }
        t4h.stack.frame(0L)
        assertFalse(t4h.paint.ctx.ends.turn)
        val under = Run(wraps = mapOf("t" to { p: PagePaint -> NoItem(p) }))
        under.stack.pages.add("a")
        under.stack.pages.add("t")
        under.stack.back { MoveKind.T4n }
        under.stack.frame(0L)
        assertFalse(under.paint.ctx.ends.turn)
    }

    private fun turnAt(x: Double, y: Double, deg: Double, hinge: Double, eye: Double, d: Double): Pair<Double, Double> {
        val th = Math.toRadians(deg)
        val sn = sin(th) / d
        val k = hinge * sn + cos(th)
        val w = sn * x + 1 - hinge * sn
        return Pair((k * x + hinge * (1 - k)) / w, (eye * sn * x + y - eye * hinge * sn) / w)
    }

    private fun ay(x: Double, y: Double, deg: Double, h: Double, eye: Double, d: Double, cx: Double): Pair<Double, Double> {
        val th = Math.toRadians(deg)
        val c = cos(th)
        val sn = sin(th)
        val es = eye * sn / d
        val u = (1 - h * sn / d) * x + (h * c - h + h * cx * sn / d)
        val v = -es * x + (c + (cx - h) * sn / d) * y + es * h
        val w = -sn / d * x + c + cx * sn / d
        return Pair(u / w, v / w)
    }

    @Test
    fun theRowsFormOfAPageWithNoVisibleRowsDrawsItsRestAsOneLayer() {
        val r = Run(rows = mapOf("empty" to 0))
        r.stack.push("empty", MoveKind.T2n)
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.rows)
        val at = r.move.swapAt + ENTER
        assertTrue(r.move.at(Part.Content, Prop.A, at) > 0f)
        r.draw(at)
        assertEquals(listOf("arrive"), r.pages.getValue("empty").rests)
    }

    @Test
    fun aHeaderTapOnTheDrawerWordBeforeT4hDrawsTheWordSpriteAtFullAlpha() {
        assertTrue(chrome.headerAlpha < Tok.alpha_full)
        val r = Run()
        r.stack.pages.add("drawer")
        chrome.press(Chrome.HEADER)
        chrome.lift()
        r.stack.pop(MoveKind.T4h)
        r.stack.frame(0L)
        assertEquals(Tok.alpha_full, r.paint.ctx.wordLook, 0f)
        val at = 1L
        assertEquals(1f, r.move.at(Part.Word, Prop.A, at), 0f)
        assertEquals(0f, r.move.at(Part.Look, Prop.A, at), 0f)
        val rec = r.draw(at)
        assertEquals(Tok.alpha_full, rec.alphas.getValue("word"), 0f)
    }

    @Test
    fun theZoomRowsOfAScrolledListClipAtTheContentTopInsideTheZoom() {
        val scroll = pitch + pitch / 2
        val r = Run(scrolls = mapOf("drawer" to scroll), forms = zoomForms)
        r.stack.push("drawer", MoveKind.T2n)
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.lift)
        r.stack.frame(r.move.swapAt)
        val at = r.move.swapAt + ENTER
        val sy = r.move.at(Part.Content, Prop.S, at)
        assertTrue(sy < 1f)
        assertTrue(r.move.at(Part.Content, Prop.A, at) > 0f)
        val p = r.pages.getValue("drawer")
        assertEquals(1, p.first())
        assertTrue(p.rowTop(1) < top)
        r.draw(at)
        val n = p.last() - p.first() + 1
        val a = r.arrive().take(n)
        assertEquals((p.first()..p.last()).toList(), a.map { it.row })
        val ry = (1 - sy) * ya
        assertEquals(ry + sy * top, a[0].clip[1], 0.01f)
        assertEquals(ry + sy * p.rowTop(1), a[0].top, 0.01f)
        assertTrue(a[0].top < a[0].clip[1])
        assertEquals(ry + sy * bottom, a.last().clip[3], 0.01f)
    }

    @Test
    fun aSideThatOverridesTheScrollMovesEveryBandAndRow() {
        val r = Run(rows = mapOf(B to 30), scrolls = mapOf(B to 1000), wraps = mapOf(B to { p: PagePaint -> Scrolled(p) }))
        r.stack.pages.add(A)
        r.stack.push(B, MoveKind.T3)
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.rows)
        val p = requireNotNull(r.paint.ctx.to).paint
        assertEquals(1000, r.pages.getValue(B).scroll)
        assertEquals(1165, p.scroll)
        assertEquals(7, p.first())
        assertEquals(12, p.last())
        val tops = intArrayOf(360, 515, 680, 845, 1010, 1175, 1280)
        assertArrayEquals(tops, p.bandTops())
        val ys = listOf(350, 515, 680, 845, 1010, 1175)
        r.drawn.clear()
        CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(ImageBitmap(dev.w, dev.h)), Size(dev.w.toFloat(), dev.h.toFloat())) {
            with(p) { content(Content.Rest, 0L, Track(), "rest") }
        }
        assertEquals((7..12).toList(), r.drawn.map { it.row })
        assertEquals(ys, r.drawn.map { it.y })
        assertTrue(r.drawn.all { it.dy == 0f })
        r.stack.frame(r.move.swapAt)
        val at = r.move.swapAt + ENTER
        val sy = r.move.at(Part.Content, Prop.S, at)
        assertTrue(sy < 1f)
        assertTrue(r.move.at(Part.Content, Prop.A, at) > 0f)
        r.draw(at)
        val arrive = r.drawn.filter { it.name == "arrive" }.take(ys.size)
        assertEquals((7..12).toList(), arrive.map { it.row })
        assertEquals(ys, arrive.map { it.y })
        for (d in arrive) {
            assertEquals("y of row ${d.row}", (1 - sy) * ya, d.dy, 0.01f)
            assertEquals("top of row ${d.row}", (1 - sy) * ya + sy * d.y, d.top, 0.01f)
        }
    }

    private fun closing(
        kind: MoveKind = MoveKind.T4h,
        rows: Int = ROWS,
        scroll: Int = 0,
        g: Geo = base,
        forms: MoveForms = this.forms,
        kinds: Map<String, PageKind> = emptyMap(),
        wraps: Map<String, (PagePaint) -> PagePaint> = emptyMap(),
        bars: Map<String, Pair<ThumbRect, ThumbFade>> = emptyMap(),
    ): Run {
        val r = Run(rows = mapOf(B to rows), scrolls = mapOf(B to scroll), wraps = wraps, kinds = kinds, bars = bars, forms = forms, g = g)
        r.stack.pages.add(B)
        r.stack.pop(kind)
        r.stack.frame(0L)
        assertEquals(kind, r.move.kind)
        return r
    }

    private fun leaveOf(r: Run): List<Drawn> = r.drawn.filter { it.name == "leave" }

    private fun pageBase(w: Int, h: Int, ppi: Float, large: Boolean = false): Scale {
        val none = Insets(0, 0, 0, 0)
        return scale(Window(w, h, 35, none, none, none, none, none, Dpi(ppi, ppi, 480, w, h, 1, w, h)), Nav.None, Surface.Page, large)
    }

    private fun assertOneLayer(r: Run, at: Long, anchor: Float, x0: Float, rows: List<Int>) {
        val ls = r.move.at(Part.Leave, Prop.S, at)
        val tx = Math.round(r.move.at(Part.Leave, Prop.X, at)) + (1 - ls) * x0
        val ty = (1 - ls) * anchor
        r.draw(at)
        val l = leaveOf(r)
        assertEquals(rows, l.map { it.row })
        for (d in l) {
            assertEquals("k of row ${d.row} at $at", ls, d.k, 1e-5f)
            assertEquals("x of row ${d.row} at $at", tx, d.dx, 0.01f)
            assertEquals("top of row ${d.row} at $at", ty + ls * d.y, d.top, 0.01f)
        }
    }

    @Test
    fun aT4hFromTheDrawerAtRestShrinksAndShiftsItsRowsAsOneLayer() {
        val r = closing()
        val c = r.paint.ctx
        assertTrue(c.ends.leaveRows)
        assertEquals(s.ppu, c.ends.pxU, 0f)
        assertEquals(listOf(360, 525, 690, 855, 1020, 1185, 1280), requireNotNull(c.leaveBands).y.toList())
        assertEquals(1444f, c.leaveAnchor, 0f)
        assertEquals(60f, requireNotNull(c.leaveAt).xa, 0f)
        assertEquals(708f, barX, 0f)
        val typed = listOf(
            listOf(0f, 0.975f, 8f, 9.5f, 36.1f, 387.1f, 1f),
            listOf(17f, 0.94f, 16f, 19.6f, 86.64f, 425.04f, 0.89820f),
            listOf(50f, 0.928f, 20f, 24.32f, 103.968f, 438.048f, 1 - 50 / 167f),
            listOf(100f, 0.885f, 34f, 40.9f, 166.06f, 484.66f, 1 - 100 / 167f),
            listOf(150f, 0.885f, 34f, 40.9f, 166.06f, 484.66f, 0.10180f),
        )
        for (row in typed) {
            val at = row[0].toLong()
            val k = row[1]
            val tx = row[3]
            val ty = row[4]
            assertEquals(k, r.move.at(Part.Leave, Prop.S, at), 1e-4f)
            assertEquals(row[2], Math.round(r.move.at(Part.Leave, Prop.X, at)).toFloat(), 0f)
            r.draw(at)
            val l = leaveOf(r)
            assertEquals((0..5).toList(), l.map { it.row })
            for (d in l) {
                assertEquals("k at $at", k, d.k, 1e-4f)
                assertEquals("x at $at", tx, d.dx, 0.01f)
                assertEquals("top of row ${d.row} at $at", ty + k * d.y, d.top, 0.01f)
                assertEquals("alpha at $at", row[6], d.alpha, 1e-4f)
                assertEquals(tx, d.clip[0], 0.01f)
                assertEquals(tx + k * barX, d.clip[2], 0.01f)
            }
            assertEquals(row[5], l[0].top, 0.01f)
            assertEquals(ty + k * top, l[0].clip[1], 0.01f)
            assertEquals(ty + k * bottom, l.last().clip[3], 0.01f)
        }
        r.draw(100L)
        val l = leaveOf(r)
        assertEquals(listOf(40.9f, 484.66f, 667.48f), listOf(l[0].clip[0], l[0].clip[1], l[0].clip[2]).map { Math.round(it * 1000) / 1000f })
        assertEquals(1298.86f, l.last().clip[3], 0.01f)
        r.draw(167L)
        assertTrue(leaveOf(r).isEmpty())
    }

    @Test
    fun aT4hFromAScrolledDrawerLeavesFromItsScrolledBands() {
        val r = closing(rows = 30, scroll = 1000)
        val c = r.paint.ctx
        assertEquals(listOf(360, 515, 680, 845, 1010, 1175, 1280), requireNotNull(c.leaveBands).y.toList())
        assertEquals(1444f, c.leaveAnchor, 0f)
        assertOneLayer(r, 100L, 1444f, xa, (6..11).toList())
        val row7 = leaveOf(r).first { it.row == 7 }
        assertEquals(0.115f * 1444 + 0.885f * 515, row7.top, 0.01f)
        assertEquals(621.835f, row7.top, 0.01f)
    }

    @Test
    fun aT4nTakesTheSameLeavingLayer() {
        val r = closing(MoveKind.T4n)
        assertTrue(r.paint.ctx.ends.leaveRows)
        assertEquals(1444f, r.paint.ctx.leaveAnchor, 0f)
        assertOneLayer(r, 100L, 1444f, xa, (0..5).toList())
        val l = leaveOf(r)
        assertEquals(40.9f, l[0].dx, 0.01f)
        assertEquals(484.66f, l[0].top, 0.01f)
    }

    @Test
    fun aT4hFromAListWithNoVisibleRowLeavesItsRestAsOneBandAboutThePopAnchor() {
        val r = closing(rows = 0)
        val c = r.paint.ctx
        assertTrue(c.ends.leaveRows)
        assertEquals(listOf(360, 1280), requireNotNull(c.leaveBands).y.toList())
        assertEquals(1444f, c.leaveAnchor, 0f)
        assertEquals(0.885f, r.move.at(Part.Leave, Prop.S, 100L), 1e-4f)
        assertEquals(34f, Math.round(r.move.at(Part.Leave, Prop.X, 100L)).toFloat(), 0f)
        r.draw(100L)
        val rest = r.pages.getValue(B).restAt.filter { it.name == "leave" }
        assertEquals(1, rest.size)
        assertEquals(0.885f, rest[0].k, 1e-4f)
        assertEquals(40.9f, rest[0].dx, 0.01f)
        assertEquals(166.06f, rest[0].dy, 0.01f)
        assertEquals(listOf(40.9f, 484.66f, 667.48f, 1298.86f), rest[0].clip.map { Math.round(it * 100) / 100f })
        assertTrue(leaveOf(r).isEmpty())
    }

    @Test
    fun aT4hFromAGridPageLeavesWhole() {
        val r = closing(kinds = mapOf(B to PageKind.Grid))
        val c = r.paint.ctx
        assertFalse(c.ends.leaveRows)
        assertNull(c.leaveAt)
        assertNull(c.leaveBands)
        assertFalse(r.move.has(Part.Leave, Prop.S))
        r.draw(100L)
        val rest = r.pages.getValue(B).restAt.filter { it.name == "leave" }
        assertEquals(1, rest.size)
        assertEquals(1f, rest[0].k, 0f)
        assertEquals(0f, rest[0].dx, 0f)
        assertEquals(0f, rest[0].dy, 0f)
    }

    @Test
    fun aT4hOnThePageBaseTakesTheLeavingSidesScale() {
        val ps = pageBase(1080, 2160, 403.2f)
        assertNotNull(ps.fit)
        assertEquals(2.9651f, ps.stdPpu, 1e-4f)
        val r = closing(g = geoOf(ps), forms = MoveForms.board(ps.board))
        val c = r.paint.ctx
        val x0 = requireNotNull(c.leaveAt).xa
        assertEquals(59.30f, x0, 0.01f)
        assertEquals(Tok.hubEdge * ps.stdPpu, x0, 0f)
        assertEquals(ps.ppu, c.ends.pxU, 0f)
        assertEquals(8f, Math.round(r.move.at(Part.Leave, Prop.X, 0L)).toFloat(), 0f)
        assertEquals(33f, Math.round(r.move.at(Part.Leave, Prop.X, 100L)).toFloat(), 0f)
        val p = r.pages.getValue(B)
        assertEquals(356, p.top)
        assertEquals(163, p.rowY(1) - p.rowY(0))
        assertEquals(11, p.last())
        assertEquals(2322f, c.leaveAnchor, 0f)
        assertEquals(2160 - px(Tok.motion_t4hRowsYa, ps.stdPpu), c.leaveAnchor.toInt())
        assertOneLayer(r, 100L, 2322f, x0, (0..11).toList())
    }

    @Test
    fun aTallPageAnchorsTheLeavingLayerAtT4hRowsYaBelowTheScreenWhateverItsBands() {
        val ts = pageBase(1080, 2520, 397.3f)
        assertNotNull(ts.fit)
        val g = geoOf(ts)
        val r = closing(g = g, forms = MoveForms.board(ts.board))
        val p = r.pages.getValue(B)
        val c = r.paint.ctx
        val b = requireNotNull(c.leaveBands)
        assertEquals(14, b.dy.size)
        assertEquals(351, p.top)
        assertEquals(161, p.rowY(1) - p.rowY(0))
        assertEquals(13, p.last())
        assertEquals(2680f, c.leaveAnchor, 0f)
        assertEquals(2520 - px(Tok.motion_t4hRowsYa, ts.stdPpu), c.leaveAnchor.toInt())
        assertOneLayer(r, 100L, 2680f, Tok.hubEdge * ts.stdPpu, (0..13).toList())
    }

    @Test
    fun aCarriedTallPageAnchorsAtT4hRowsYaOfThePop() {
        val ts = pageBase(1080, 2520, 397.3f)
        val r = Run(forms = MoveForms.board(ts.board), g = geoOf(ts))
        retargeted(r, Tok.motion_t2Swap.toLong() + ENTER)
        val c = r.paint.ctx
        val b = requireNotNull(c.leaveBands)
        assertNotNull(c.leaveAt)
        assertEquals(14, b.dy.size)
        assertEquals(351, b.y[0])
        assertEquals(2680f, c.leaveAnchor, 0f)
        assertEquals(PageMove.popAnchor(MoveKind.T4h, b.y[0], 2520, ts.stdPpu).toFloat(), c.leaveAnchor, 0f)
    }

    @Test
    fun aLargePageShiftsTheLeavingRowsInPagePxAboutHubEdgeInStandardPx() {
        val ls = pageBase(1080, 2160, 403.2f, large = true)
        assertTrue(ls.large)
        assertTrue(ls.ppu != ls.stdPpu)
        val r = closing(g = geoOf(ls), forms = MoveForms.board(ls.board))
        val c = r.paint.ctx
        assertEquals(ls.ppu, c.ends.pxU, 0f)
        assertEquals(59f, requireNotNull(c.leaveAt).xa, 0f)
        assertEquals(Math.round(Tok.hubEdge * ls.stdPpu).toFloat(), c.leaveAt?.xa ?: 0f, 0f)
        assertEquals(11.2f * ls.ppu, r.move.at(Part.Leave, Prop.X, 100L), 1e-3f)
    }

    private val bar = ThumbRect(708, 360, 720, 592)

    private fun seenAt(at: Long) = ThumbFade().also { it.track(true, false, at) }

    private fun thumbOf(r: Run, name: String): Drawn? = r.drawn.firstOrNull { it.name == "$name.thumb" }

    @Test
    fun aT2DrawsThePageThumbUnscaledAtItsRestRectInTheBarStrip() {
        val r = Run(forms = zoomForms, bars = mapOf(B to (bar to seenAt(0L))))
        r.stack.push(B, MoveKind.T2)
        r.stack.frame(0L)
        val c = r.paint.ctx
        assertEquals(0L, c.thumbAt)
        assertEquals(1f, c.arriveThumb, 0f)
        r.draw(17L)
        assertTrue(r.arrive().all { it.k < 1f })
        val d = requireNotNull(thumbOf(r, "arrive"))
        assertEquals(1f, d.k, 0f)
        assertEquals(0f, d.dx, 0f)
        assertEquals(0f, d.dy, 0f)
        assertEquals(bar.y0.toFloat(), d.top, 0f)
        assertEquals(bar.y1.toFloat(), d.bottom, 0f)
        assertEquals(listOf(barX, top.toFloat(), s.widthPx.toFloat(), bottom.toFloat()), d.clip)
        assertEquals(Tok.motion_rowsT2Alpha, d.alpha, 1e-4f)
        assertEquals(0.6f, d.alpha, 1e-4f)
    }

    @Test
    fun aReopenedPageThumbHoldsTheValueAtTheTurnStartThroughTheMove() {
        for (seen in listOf(-3000L, -1700L)) {
            val fade = seenAt(seen)
            val r = Run(forms = zoomForms, bars = mapOf(B to (bar to fade)), onSwap = { _, at -> fade.show(at) })
            r.stack.push(B, MoveKind.T2)
            r.stack.frame(0L)
            val v = fade.at(0L)
            if (seen == -3000L) assertEquals(0f, v, 0f) else assertEquals(0.0625f, v, 1e-4f)
            assertEquals(v, r.paint.ctx.arriveThumb, 0f)
            val end = r.move.endAt
            var t = 0L
            var shown = 0
            while (t < end) {
                r.stack.frame(t)
                if (r.stack.phase == Phase.Rest) break
                r.draw(t)
                val rows = r.arrive()
                val d = thumbOf(r, "arrive")
                if (v == 0f || rows.isEmpty()) {
                    assertNull("no thumb at $t", d)
                } else {
                    shown++
                    assertEquals("thumb at $t", rows[0].alpha * v, requireNotNull(d).alpha, 1e-4f)
                }
                t += FRAME
            }
            assertTrue(t >= r.move.swapAt)
            assertEquals(1f, fade.at(r.move.swapAt), 0f)
            if (seen == -1700L) assertTrue(shown > 0)
        }
    }

    @Test
    fun aT4hDrawsTheLeavingThumbUnscaledInTheBarStripAtTheTurnStartValue() {
        val fade = seenAt(-1700L)
        val v = fade.at(0L)
        assertTrue(v > 0f && v < 1f)
        val r = closing(bars = mapOf(B to (bar to fade)))
        assertEquals(v, r.paint.ctx.leaveThumb, 0f)
        fade.show(50L)
        for (at in listOf(17L, 100L)) {
            r.draw(at)
            val rows = leaveOf(r)
            val d = requireNotNull(thumbOf(r, "leave"))
            assertTrue(rows[0].k < 1f)
            assertEquals(1f, d.k, 0f)
            assertEquals(0f, d.dx, 0f)
            assertEquals(bar.y0.toFloat(), d.top, 0f)
            assertEquals(listOf(barX, top.toFloat(), s.widthPx.toFloat(), bottom.toFloat()), d.clip)
            assertEquals(rows[0].alpha * v, d.alpha, 1e-4f)
        }
    }

    @Test
    fun aCarriedT4hKeepsTheThumbValueOfTheT2Start() {
        val fade = seenAt(-1700L)
        val v = fade.at(0L)
        val r = Run(forms = zoomForms, bars = mapOf(B to (bar to fade)), onSwap = { _, at -> fade.show(at) })
        retargeted(r, Tok.motion_t2Swap.toLong() + ENTER)
        assertEquals(1f, fade.at(r.move.t0), 0f)
        val c = r.paint.ctx
        assertEquals(0L, c.thumbAt)
        assertEquals(v, c.leaveThumb, 0f)
        r.draw(r.move.t0 + 1)
        val rows = leaveOf(r)
        val d = requireNotNull(thumbOf(r, "leave"))
        assertTrue(rows[0].k < 1f)
        assertEquals(1f, d.k, 0f)
        assertEquals(bar.y0.toFloat(), d.top, 0f)
        assertEquals(rows[0].alpha * v, d.alpha, 1e-4f)
    }

    private fun slid(r: Run, swap: Boolean = false) {
        r.stack.push(B, MoveKind.T2w)
        r.stack.frame(0L)
        assertEquals(MoveKind.T2w, r.move.kind)
        if (swap) r.stack.frame(r.move.swapAt)
    }

    private fun bitmapOf(w: Int, h: Int, block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { block() }
        return img.asAndroidBitmap()
    }

    private fun window(w: Int, h: Int, nav: Nav = Nav.None, bar: Int = 0): Scale {
        val none = Insets(0, 0, 0, 0)
        val b = Insets(0, 0, 0, bar)
        return scale(Window(w, h, 35, none, b, b, none, none), nav, Surface.Page, false)
    }

    @Test
    fun aT2wFrameSlidesHomeAndThePeekAsOneLayer() {
        val ps = pageBase(1080, 2160, 403.2f)
        val none = Insets(0, 0, 0, 0)
        val hs = scale(Window(1080, 2160, 35, none, none, none, none, none, Dpi(403.2f, 403.2f, 480, 1080, 2160, 1, 1080, 2160)), Nav.None, Surface.Home, false)
        assertEquals(4.5f, hs.stdPpu, 0f)
        val g = geoOf(ps)
        for ((r, x) in listOf(Run({ OFF }) to -260f, Run({ OFF }, forms = MoveForms.board(ps.board), g = g, home = hs) to -390f)) {
            slid(r)
            assertEquals(r.g.s.widthPx * 200f / 480f, r.paint.ctx.ends.slideX, 1e-3f)
            assertFalse(r.paint.ctx.ends.zoom)
            assertEquals(Tok.qpPeekAlpha, r.paint.ctx.ends.peek0, 0f)
            val first = r.draw(0L)
            for (w in listOf("home.word", "peek.word")) {
                assertEquals("$w fade at 0", 1f, first.wordFade.getValue(w), 0f)
                assertEquals("$w move at 0", 0f, first.wordAt.getValue(w)[1], 0f)
            }
            val rec = r.draw(100L)
            val look = requireNotNull(r.homePaint.look)
            assertEquals(listOf(1f, -1f, 0f, Roles.homeSubtitle.alpha, OFF), listOf(look.items, look.sel.toFloat(), look.selAlpha, look.sub, look.off))
            for (w in listOf("home.word", "peek.word")) {
                assertEquals("$w fade at 100", 0.13319f, rec.wordFade.getValue(w), 1e-4f)
                assertEquals("$w move at 100", x, rec.wordAt.getValue(w)[1], 0f)
                assertEquals("$w y at 100", 0f, rec.wordAt.getValue(w)[2], 0f)
            }
            assertEquals(Tok.qpPeekAlpha, rec.alphas.getValue("peek.word"), 0f)
            assertTrue(r.arrive().isEmpty())
            assertFalse("arrive.chrome" in rec.chromes)
        }
    }

    @Test
    fun aT2wFrameSlidesThePageAsOneLayer() {
        val ps = pageBase(1080, 2160, 403.2f)
        for ((r, x) in listOf(Run(bars = mapOf(B to (bar to seenAt(0L)))) to 90f, Run(bars = mapOf(B to (bar to seenAt(0L))), forms = MoveForms.board(ps.board), g = geoOf(ps)) to 135f)) {
            slid(r)
            assertEquals(1f, r.paint.ctx.arriveThumb, 0f)
            val none = r.draw(300L)
            assertTrue(r.drawn.isEmpty())
            assertTrue(r.homeDrawn.isEmpty())
            assertTrue(none.words.isEmpty())
            assertTrue(none.chromes.isEmpty())
            val rec = r.draw(400L)
            assertTrue(r.homeDrawn.isEmpty())
            val a = r.arrive()
            assertTrue(a.isNotEmpty())
            for (d in a) {
                assertEquals("alpha of row ${d.row}", 0.70054f, d.alpha, 1e-4f)
                assertEquals("x of row ${d.row}", x, d.dx, 0f)
                assertEquals("y of row ${d.row}", r.g.top + d.row * r.g.pitch, d.y)
                assertEquals("top of row ${d.row}", d.y.toFloat(), d.top, 0f)
                assertEquals(1f, d.k, 0f)
            }
            val t = requireNotNull(thumbOf(r, "arrive"))
            assertEquals(0.70054f, t.alpha, 1e-4f)
            assertEquals(x, t.dx, 0f)
            val c = rec.chromes.getValue("arrive.chrome")
            assertEquals(r.g.chrome.headerRestX() + x, c[0], 1e-3f)
            assertEquals(0.70054f, c[2], 1e-4f)
        }
    }

    @Test
    fun aT2wOnAWindowNotAMultipleOf12WideRoundsEachFrame() {
        val ws = window(1000, 1600)
        assertEquals(1000, ws.widthPx)
        val r = Run({ OFF }, forms = MoveForms.board(ws.board), g = geoOf(ws))
        slid(r)
        assertEquals(416.6667f, r.paint.ctx.ends.slideX, 1e-3f)
        assertEquals(-361f, r.draw(100L).wordAt.getValue("home.word")[1], 0f)
        r.draw(400L)
        val a = r.arrive()
        assertTrue(a.isNotEmpty())
        for (d in a) assertEquals(125f, d.dx, 0f)
    }

    @Test
    fun theT2wLastFrameIsTheRestFrame() {
        val fade = seenAt(-1700L)
        val r = Run(wraps = mapOf(B to { p: PagePaint -> Painted(p) }), bars = mapOf(B to (bar to fade)))
        slid(r)
        val v = r.paint.ctx.arriveThumb
        assertEquals(fade.at(0L), v, 0f)
        val at = r.move.endAt
        assertEquals(800L, at)
        val moved = bitmapOf(s.widthPx, s.heightPx) { with(r.paint) { drawMove(at, null) } }
        val page = r.side(B)
        val rest = bitmapOf(s.widthPx, s.heightPx) {
            with(page) {
                ground(1f, 0f)
                content(Content.Rest, at, null, "arrive", v)
            }
            chrome(chrome, at)
        }
        assertTrue(moved.sameAs(rest))
        assertTrue(r.drawn.any { it.name == "arrive" })
    }

    @Test
    fun aT2wWithTheNavBarOnDrawsNoRowBelowTheContentBottom() {
        val ns = window(1080, 2160, Nav.Buttons, 132)
        assertTrue(ns.navOn)
        val cut = px(ns.stdContentBottom, ns.stdPpu).toFloat()
        assertEquals(2028f, cut, 0f)
        val g = geoOf(ns)
        val r = Run(forms = MoveForms.board(ns.board), g = g)
        slid(r)
        r.draw(400L)
        val a = r.arrive()
        assertTrue(a.isNotEmpty())
        for (d in a) assertTrue("row ${d.row} clip ${d.clip}", d.clip[3] <= cut + 0.01f)
    }

    @Test
    fun aT2wOnAFitByHeightWindowDrawsNothingIntoThePillars() {
        val ws = window(1600, 1280)
        assertEquals(320, ws.left)
        assertEquals(960, ws.widthPx)
        val r = Run(wraps = mapOf(B to { p: PagePaint -> Painted(p) }), forms = MoveForms.board(ws.board), g = geoOf(ws))
        slid(r)
        val fill = PILLAR_INK.toArgb()
        fun pillars(at: Long, inside: Int) {
            val img = ImageBitmap(1600, 1280)
            val cv = Canvas(img)
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, cv, Size(1600f, 1280f)) { drawRect(PILLAR_INK) }
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, cv, Size(960f, 1280f)) { translate(320f, 0f) { with(r.paint) { drawMove(at, null) } } }
            val b = img.asAndroidBitmap()
            assertTrue("the ${r.move.kind} draws inside the canvas at $at", b.getPixel(inside, 640) != fill)
            for (y in 0 until 1280 step PROBE) for (x in (0 until 320) + (1280 until 1600)) assertEquals("${r.move.kind} pillar px $x, $y at $at", fill, b.getPixel(x, y))
        }
        pillars(100L, 400)
        pillars(400L, 1200)
        r.stack.frame(r.move.swapAt)
        r.stack.back { MoveKind.T4n }
        r.stack.frame(400L)
        assertEquals(MoveKind.T4n, r.move.kind)
        assertEquals(120f, r.paint.ctx.leaveDx, 0f)
        for (at in listOf(400L, 450L, 500L)) pillars(at, 1200)
    }

    @Test
    fun aT2wRetargetedIntoT4nCarriesItsPlace() {
        val r = Run(wraps = mapOf(B to { p: PagePaint -> Painted(p) }))
        slid(r, swap = true)
        assertEquals(Phase.Enter, r.stack.phase)
        val at = 400L
        assertEquals(90f, Math.round(r.move.at(Part.Content, Prop.X, at)).toFloat(), 0f)
        r.stack.back { MoveKind.T4n }
        r.stack.frame(at)
        val c = r.paint.ctx
        assertEquals(MoveKind.T4n, c.kind)
        assertEquals(90f, c.leaveDx, 0f)
        assertEquals(listOf(360, 525, 690, 855, 1020, 1185, 1280), requireNotNull(c.leaveBands).y.toList())
        assertEquals(1444f, c.leaveAnchor, 0f)
        val held = requireNotNull(c.leaveAt)
        assertEquals(listOf(1f, 90f, 0f, 60f, 1444f), listOf(held.s, held.dx, held.dy, held.xa, held.ya))
        val rec = r.draw(at)
        val l = leaveOf(r)
        assertEquals((0..5).toList(), l.map { it.row })
        for (d in l) {
            assertEquals(0.975f, d.k, 1e-4f)
            assertEquals(99.5f, d.dx, 0.01f)
            assertEquals(36.1f + 0.975f * d.y, d.top, 0.01f)
            assertEquals(0.70054f, d.alpha, 1e-4f)
        }
        assertEquals(387.1f, l[0].top, 0.01f)
        val h = rec.chromes.getValue("leave.chrome")
        assertEquals(chrome.headerRestX() + 90f, h[0], 1e-3f)
        assertEquals(chrome.headerAlpha * 0.70054f, h[1], 1e-4f)
        val b = bitmapOf(s.widthPx, s.heightPx) { with(r.paint) { drawMove(at, null) } }
        assertEquals(0, b.getPixel(GROUND_X - 45, GROUND_Y))
        assertEquals(GROUND_INK.toArgb(), b.getPixel(GROUND_X + 45, GROUND_Y))
        val gone = r.draw(at + 167L)
        assertTrue(leaveOf(r).isEmpty())
        assertEquals(0f, gone.chromes.getValue("leave.chrome")[1], 0f)
    }

    @Test
    fun aT3FromARowWhoseTitleFoldsToTheArrivingHeaderTakesTheRowForm() {
        val hidden = mutableListOf<Int>()
        val r = rowRun("Drawer", hidden)
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3, row = ROW)
        r.stack.frame(0L)
        val c = r.paint.ctx
        val e = c.ends
        assertTrue(e.morph)
        assertEquals(MorphKind.Row, e.morphKind)
        assertTrue(e.morphHeaderIn)
        assertFalse(e.morphHeaderOut)
        assertEquals(ROW_S, e.morphS0, 0f)
        assertEquals(HEADER_S, e.morphS1, 0f)
        assertEquals(ROW_Y, e.morphY0, 0f)
        assertEquals(HEADER_Y, e.morphY1, 0f)
        assertTrue("a row push carries the leaving header out", e.lhead)
        assertFalse("a row push takes no tile and no tap", e.thumb)
        assertEquals(Moves.kT3RowWordEnd, r.move.wordEnd)
        assertEquals("the row word starts at the row's own size", ROW_S, r.move.at(Part.Word, Prop.S, 0L), 0f)
        assertEquals(ROW, c.hideRow)
        assertEquals("the pushed row's title is hidden while the word flies", listOf(ROW), hidden)
        r.paint.landed()
        assertEquals("the land shows the row's title again", listOf(ROW, -1), hidden)
    }

    @Test
    fun aRowTitleThatDoesNotFoldToTheHeaderKeepsThePivotForm() {
        val hidden = mutableListOf<Int>()
        val r = rowRun("albums", hidden, pivot = true)
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3, row = ROW)
        r.stack.frame(0L)
        assertEquals(MorphKind.Pivot, r.paint.ctx.ends.morphKind)
        assertEquals(-1, r.paint.ctx.hideRow)
        assertTrue(hidden.isEmpty())
        val f = rowRun("DRAWER", mutableListOf())
        f.stack.pages.add("a")
        f.stack.push("b", MoveKind.T3, row = ROW)
        f.stack.frame(0L)
        assertEquals("the fold is ASCII case only", MorphKind.Row, f.paint.ctx.ends.morphKind)
    }

    @Test
    fun aT4BackToThePushedRowTakesTheRowFormAndClipsTheWordAtTheRowBottom() {
        val hidden = mutableListOf<Int>()
        val r = rowRun("Drawer", hidden)
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3, row = ROW)
        r.stack.frame(0L)
        r.stack.frame(LAND)
        r.stack.frame(LAND + FRAME)
        assertEquals(listOf("a", "b"), r.stack.pages.toList())
        r.paint.landed()
        hidden.clear()
        r.stack.pop(MoveKind.T4)
        r.stack.frame(LAND * 2)
        val c = r.paint.ctx
        val e = c.ends
        assertEquals(MorphKind.Row, e.morphKind)
        assertTrue(e.morphHeaderOut)
        assertFalse(e.morphHeaderIn)
        assertEquals(HEADER_S, e.morphS0, 0f)
        assertEquals(ROW_S, e.morphS1, 0f)
        assertEquals("a row pop takes the word look, not the full alpha of a lit header", HEADER_LOOK, c.morphLook, 0f)
        val px = Math.round(ROW_S * rolePx(Roles.pageHeader, s, s.stdPpu))
        assertEquals(Math.round(ROW_Y) + maxOf(1, Metrics.descent(px)), c.rowBottom)
        val rec = r.draw(LAND * 2 + MORPH_AT)
        assertEquals("the landing word is clipped at the row bottom", c.rowBottom.toFloat(), rec.wordClips.getValue("morph")[3], 0f)
        assertEquals("the push's hide is cleared, then the arriving row's title is hidden while the word lands", listOf(-1, ROW), hidden)
    }

    @Test
    fun aT4WithNoPushedRowKeepsThePivotForm() {
        val r = rowRun("Drawer", mutableListOf(), pivot = true)
        r.stack.pages.addAll(listOf("a", "b"))
        r.stack.pop(MoveKind.T4)
        r.stack.frame(0L)
        assertEquals("pages put on the stack by hand carry no row", MorphKind.Pivot, r.paint.ctx.ends.morphKind)
    }

    @Test
    fun aT4RowPopZoomsTheLeavingRowsAsOneLayerOnTheRowPopKeys() {
        val r = rowRun("Drawer", mutableListOf())
        val t0 = rowPopped(r)
        val c = r.paint.ctx
        val e = c.ends
        assertEquals(MorphKind.Row, e.morphKind)
        assertTrue("a row pop bands the leaving page as funeOS band_leave", e.leaveRows)
        assertTrue(c.leaveBands != null)
        assertEquals(c.xa, requireNotNull(c.leaveAt).xa, 0f)
        assertEquals("a T4 pop anchors at the page top", top.toFloat(), c.leaveAnchor, 0f)
        val v = r.move
        for ((dt, ls, dx, dy) in listOf(Quad4(33L, 0.942f, 7.9f, 21.8f), Quad4(67L, 0.914f, 11.2f, 33.7f), Quad4(100L, 0.891f, 13.9f, 43.7f))) {
            assertEquals("kT4RowPitch at +$dt", ls, v.at(Part.Leave, Prop.S, t0 + dt), 1e-4f)
            assertEquals("kT4RowDx at +$dt", dx * e.pxU, v.at(Part.Leave, Prop.X, t0 + dt), 1e-3f)
            assertEquals("kT4RowDy at +$dt", dy * e.pxU, v.at(Part.Leave, Prop.Y, t0 + dt), 1e-3f)
        }
    }

    @Test
    fun twoPushesOfOnePageInstanceKeepTheirOwnRowsAndARestoredStackKeepsNone() {
        val r = rowRun("Drawer", mutableListOf())
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3, row = ROW)
        r.stack.frame(0L)
        r.stack.frame(LAND)
        r.stack.frame(LAND + FRAME)
        r.stack.push("b", MoveKind.T3, row = ROW2)
        r.stack.frame(LAND * 2)
        r.stack.frame(LAND * 3)
        r.stack.frame(LAND * 3 + FRAME)
        assertEquals(listOf("a", "b", "b"), r.stack.pages.toList())
        r.stack.pop(MoveKind.T4)
        r.stack.frame(LAND * 4)
        assertEquals("the top slot pops with its own row", ROW2, r.row)
        r.stack.frame(LAND * 5)
        r.stack.frame(LAND * 5 + FRAME)
        r.stack.pop(MoveKind.T4)
        r.stack.frame(LAND * 6)
        assertEquals("the slot under it keeps the first push's row", ROW, r.row)
        r.stack.frame(LAND * 7)
        r.stack.frame(LAND * 7 + FRAME)
        r.stack.push("b", MoveKind.T3, row = ROW)
        r.stack.frame(LAND * 8)
        r.stack.frame(LAND * 9)
        r.stack.frame(LAND * 9 + FRAME)
        r.stack.pages.clear()
        r.stack.pages.addAll(listOf("a", String(charArrayOf('b'))))
        r.stack.pop(MoveKind.T4)
        r.stack.frame(LAND * 10)
        assertEquals("pages restored by hand carry no row, so the pop takes the pivot form", -1, r.row)
    }

    @Test
    fun aT4RowPopDrawsTheArrivingThumbAtItsTurnStartValue() {
        val fade = ThumbFade()
        val r = rowRun("Drawer", mutableListOf(), bars = mapOf("a" to (bar to fade)))
        val t0 = rowPopped(r)
        assertEquals("a thumb not shown since the push reads 0 at the turn start", 0f, r.paint.ctx.arriveThumb, 0f)
        r.draw(t0 + THUMB_AT)
        assertTrue("no arriving thumb ink while the row pop runs", r.drawn.none { it.name.endsWith(".thumb") && it.alpha > 0f })
    }

    private data class Quad4(val dt: Long, val s: Float, val x: Float, val y: Float)

    private class AlbumColumn(d: PagePaint) : PagePaint by d {
        override val albumCol = true
    }

    @Test
    fun aT4mFromTheAlbumColumnTakesTheColumnBackAndFromAnAlbumFadesInFromTheWordEnd() {
        val plain = MoveForms()
        val r = Run(forms = plain, wraps = mapOf("c" to { d: PagePaint -> AlbumColumn(d) }))
        r.stack.pages.addAll(listOf("hub", "c"))
        r.stack.pop(MoveKind.T4m)
        r.stack.frame(0L)
        assertTrue(r.paint.ctx.ends.colBack)
        assertEquals(0f, r.move.at(Part.Content, Prop.A, 299L), 0f)
        assertEquals(100f / Tok.motion_t3cBack, r.move.at(Part.Content, Prop.A, 400L), 1e-4f)
        assertEquals(1f, r.move.at(Part.Content, Prop.A, 467L), 0f)
        val a = Run(forms = plain)
        a.stack.pages.addAll(listOf("hub", "album"))
        a.stack.pop(MoveKind.T4m)
        a.stack.frame(0L)
        assertFalse(a.paint.ctx.ends.colBack)
        assertTrue(a.paint.ctx.ends.word)
        val l = requireNotNull(a.move.leg(Part.Content, Prop.A))
        assertEquals(a.move.wordEnd, l.delay)
        assertEquals(Tok.motion_t4mFade.toInt(), l.dur)
    }

    private class PivotRowPage(d: PagePaint, val band: Edges, val marks: MutableList<List<Float>>) : PagePaint by d {
        override fun pivotRow(): Edges = band

        override fun DrawScope.pivotRowInk(rec: DrawRecord?) {
            val t = rec as? Track ?: return
            drawRect(ROW_INK, Offset(band.x0.toFloat(), band.y0.toFloat()), Size(MARK, MARK))
            marks += listOf(t.k, t.tx, t.ty, t.a, t.clip[2])
        }
    }

    @Test
    fun aPivotPushDrawsThePagesPivotRowInkInsideTheBandWidenedToTheReach() {
        val pc = chromeOf(measurer, fonts, s, unit, "artist", listOf(OTHER, "songs"), null)
        val g = Geo(s, pc, pitch, top, bottom)
        val band = Edges(0, px(s.board.pivotTop, s.ppu), s.widthPx, px(s.board.page_contentTop, s.ppu))
        assertEquals(189, band.y0)
        val marks = mutableListOf<List<Float>>()
        val r = Run(
            g = g,
            sides = mapOf("a" to { p: Boolean -> if (p) side(A, false) else null }, "b" to { p: Boolean -> if (p) null else side(B, true) }),
            wraps = mapOf("b" to { d: PagePaint -> PivotRowPage(d, band, marks) }),
        )
        r.stack.pages.add("a")
        r.stack.push("b", MoveKind.T3)
        r.stack.frame(0L)
        val e = r.paint.ctx.ends
        assertTrue(e.morph)
        assertTrue(e.pivots)
        assertTrue(r.move.has(Part.Pivots, Prop.S))
        assertFalse(r.move.has(Part.Pivots, Prop.X))
        assertEquals(0.59f * s.widthPx, r.move.at(Part.Pivots, Prop.PX, 300L), 1e-3f)
        assertEquals(0.63f, r.move.at(Part.Pivots, Prop.S, 267L), 1e-6f)
        r.draw(266L)
        assertTrue(marks.isEmpty())
        for (at in listOf(267L, 333L, 400L)) {
            marks.clear()
            r.draw(at)
            val sc = r.move.at(Part.Pivots, Prop.S, at)
            val ox = r.move.at(Part.Pivots, Prop.PX, at)
            val oy = r.move.at(Part.Pivots, Prop.PY, at)
            val m = marks.single()
            assertEquals("$at", sc, m[0], 1e-6f)
            assertEquals("$at", ox - ox * sc, m[1], 1e-3f)
            assertEquals("$at", oy - oy * sc, m[2], 1e-3f)
            assertEquals("$at", r.move.at(Part.Pivots, Prop.A, at), m[3], 1e-6f)
        }
        assertEquals(895, r.paint.ctx.pivotReach)
        marks.clear()
        r.draw(300L)
        val m = marks.single()
        assertEquals(m[0] * 895 + m[1], m[4], 1e-3f)
        assertTrue("the pivot ink at ${s.widthPx + 40} px falls inside the clip at ${m[4]}", m[0] * (s.widthPx + 40) + m[1] < m[4])
    }

    companion object {
        private const val FRAME = 16L
        private const val ROWS = 40
        private const val OFF = 7.3f
        private const val ENTER = 40L
        private const val LATER = 120L
        private const val A = "artists"
        private const val B = "drawer"
        private const val OTHER = "albums"
        private const val PIVOT_S = 0.6f
        private const val PIVOT_LOOK = 0.6f
        private const val PIVOT_X = 40f
        private const val PIVOT_Y = 200f
        private const val HEADER_S = 1.5f
        private const val HEADER_LOOK = 0.9f
        private const val HEADER_X = 20f
        private const val HEADER_Y = 100f
        private const val ROW = 3
        private const val ROW_S = 0.7f
        private const val ROW_LOOK = 1f
        private const val ROW_X = 60f
        private const val ROW_Y = 520f
        private const val LAND = 5000L
        private const val MORPH_AT = 100L
        private const val ROW2 = 5
        private const val THUMB_AT = 517L
        private const val ROW_PARTS = 4f
        private const val ROW_H = 20f
        private const val PROBE = 8
        private const val GROUND_X = 90
        private const val GROUND_Y = 1240
        private const val MARK = 4f
        private val HOME_INK = Color.White
        private val ROW_INK = Color.White
        private val GROUND_INK = Color(0xFF203040)
        private val PILLAR_INK = Color(0xFF102030)
    }
}
