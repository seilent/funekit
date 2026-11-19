package net.seilent.funekit.screens.music.hub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuInk
import net.seilent.funekit.kit.MenuPlan
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.restRows
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.HUB_GENRES
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPaint
import net.seilent.funekit.screens.music.hub.hubDraw
import net.seilent.funekit.screens.music.pivots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HubMovePaintTest {
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
    private val xa = Tok.hubEdge * s.stdPpu

    private class Geo(val s: Scale, val chrome: Chrome, val pitch: Int, val top: Int, val bottom: Int)

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

    private inner class Home(val drawn: MutableList<String>, val g: Geo) : HomePaint {
        var look: HomeMove? = null
        override val quickplay = true

        override fun peekAlpha(at: Long) = Tok.qpPeekAlpha

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) {
            drawn += "peek"
            rec?.word("peek.word", g.chrome.header, 0f, 0f, alpha)
        }

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) {
            drawn += "home"
            this@Home.look = look
            drawRect(HOME_INK, alpha = look.items)
            rec?.word("home.word", g.chrome.header, 0f, 0f, look.items)
        }
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
        private val forms: MoveForms = this@HubMovePaintTest.forms,
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
        val stack = PageStack<String>(forms, { PageKind.Plain }, move = move, swapped = onSwap) { t ->
            ends++
            paint.ends(t, HomeSide(home, homePaint, off()), t.from?.let { PageSide(g.s, side(it)) }, t.to?.let { PageSide(g.s, side(it)) }, forms)
        }

        fun side(p: String): PagePaint = page(p).let { wraps[p]?.invoke(it) ?: it }

        fun page(p: String): ListPage = ListPage(rows[p] ?: ROWS, drawn, sides[p] ?: { null }, scrolls[p] ?: 0, g, kinds[p] ?: PageKind.Plain, bars[p]).also { pages[p] = it }

        fun draw(at: Long): Track {
            val rec = Track()
            drawn.clear()
            homeDrawn.clear()
            val w = g.s.widthPx
            val h = g.s.heightPx
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(ImageBitmap(w, h)), Size(w.toFloat(), h.toFloat())) {
                with(paint) { drawMove(at, rec) }
            }
            return rec
        }

        fun arrive(): List<Drawn> = drawn.filter { it.name == "arrive" }
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

    @Test
    fun anEmptyHubPivotLeavesItsStatusLineUnderTheLeaveTransform() {
        val m = HubModel(0L)
        m.select(HUB_GENRES)
        m.songs = 0
        val accent = Accent.color(Accent.defaultHue, true)
        val d = hubDraw(m, s, measurer, fonts, unit, null, accent, true)
        val hub = HubPaint(d, chromeOf(measurer, fonts, s, unit, "music", MusicPage.Hub.pivots, null), 0, groundBands(s.heightPx, true), accent)
        assertEquals(PageKind.Hub, hub.kind)
        assertEquals(0, hub.count)
        val r = closing(wraps = mapOf(B to { _: PagePaint -> hub }))
        val c = r.paint.ctx
        assertTrue(c.ends.leaveRows)
        assertEquals(listOf(top, maxOf(bottom, top + 1)), requireNotNull(c.leaveBands).y.toList())
        val anchor = PageMove.popAnchor(MoveKind.T4h, top, s.heightPx, s.stdPpu).toFloat()
        assertEquals(anchor, c.leaveAnchor, 0f)
        val at = 100L
        val ls = r.move.at(Part.Leave, Prop.S, at)
        val tx = Math.round(r.move.at(Part.Leave, Prop.X, at)) + (1 - ls) * xa
        val ty = (1 - ls) * anchor
        val rec = r.draw(at)
        assertEquals(1, rec.words.count { it == "leave.line" })
        val w = rec.wordAt.getValue("leave.line")
        assertEquals(ls, w[0], 1e-5f)
        assertEquals(tx, w[1], 0.01f)
        assertEquals(ty, w[2], 0.01f)
        val clip = rec.wordClips.getValue("leave.line")
        assertEquals(tx, clip[0], 0.01f)
        assertEquals(ty + ls * top, clip[1], 0.01f)
        assertEquals(tx + ls * px(s.w - Tok.scrollbar_width, s.ppu), clip[2], 0.01f)
        assertEquals(ty + ls * maxOf(bottom, top + 1), clip[3], 0.01f)
    }

    @Test
    fun anEmptyHubPivotOnR1LeavesFromItsClipTop() {
        val r1 = scale(Dev("480x800", "R1", R1_W, R1_H, listOf(Nav.None)).window(Nav.None), Nav.None, Surface.Page, false)
        val m = HubModel(0L)
        m.select(HUB_GENRES)
        m.songs = 0
        val accent = Accent.color(Accent.defaultHue, true)
        val d = hubDraw(m, r1, measurer, fonts, unit, null, accent, true)
        val hub = HubPaint(d, chromeOf(measurer, fonts, r1, unit, "music", MusicPage.Hub.pivots, null), 0, groundBands(r1.heightPx, true), accent)
        val end = px(r1.stdContentBottom, r1.stdPpu)
        val g = Geo(r1, chromeOf(measurer, fonts, r1, unit, "apps", emptyList(), null), Rows.pitch(r1, true), px(r1.board.page_contentTop, r1.ppu), end)
        val r = closing(g = g, wraps = mapOf(B to { _: PagePaint -> hub }))
        assertEquals(listOf(R1_CLIP_TOP, maxOf(end, R1_CLIP_TOP + 1)), requireNotNull(r.paint.ctx.leaveBands).y.toList())
    }

    companion object {
        private const val R1_W = 480
        private const val R1_H = 800
        private const val R1_CLIP_TOP = 203
        private const val ROWS = 40
        private const val B = "drawer"
        private val HOME_INK = Color.White
    }
}
