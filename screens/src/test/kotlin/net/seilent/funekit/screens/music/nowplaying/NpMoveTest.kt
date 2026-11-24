package net.seilent.funekit.screens.music.nowplaying

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Fonts
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.word
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.px
import net.seilent.funekit.kit.StatusText
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.statusRow
import net.seilent.funekit.kit.statusLine
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.npStatus
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.posture.Fold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
class NpMoveTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val forms = MoveForms.board(TokBoard.a105).copy(leaveFade = true)

    private class Fill(val c: Color) : PagePaint {
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

        override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) = drawRect(c, Offset.Zero, size)
    }

    private class Home(private val pane: Edges?) : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = 0f

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = drawRect(Color.White, Offset.Zero, size)

        override fun frontPane() = pane

        override fun DrawScope.front(now: Long) = drawRect(Color.Green, Offset.Zero, size)
    }

    private class Run(val move: PageMove, val paint: MovePaint)

    private fun run(k: MoveKind, form: NpForm, home: HomePaint): Run {
        val move = PageMove()
        val paint = MovePaint(move)
        paint.npForm = form
        val np = PageSide(s, Fill(Color.Red))
        val t = if (k == MoveKind.T7) Turn(k, null, "np", false) else Turn<String>(k, "np", null, true)
        val e = paint.ends(t, HomeSide(s, home, 0f), if (k == MoveKind.T8) np else null, if (k == MoveKind.T7) np else null, forms)
        move.start(k, 0, e, forms)
        return Run(move, paint)
    }

    private fun draw(r: Run, now: Long): Bitmap {
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) {
            drawRect(Color.Black, Offset.Zero, size)
            with(r.paint) { drawMove(now, null) }
        }
        return img.asAndroidBitmap()
    }

    private fun Bitmap.red(x: Int, y: Int) = (getPixel(x, y) shr 16) and 0xFF

    private fun Bitmap.green(x: Int, y: Int) = (getPixel(x, y) shr 8) and 0xFF

    private fun Bitmap.blue(x: Int, y: Int) = getPixel(x, y) and 0xFF

    @Test
    fun theRingFormTurnsNowPlayingInAfterTheDark() {
        val r = run(MoveKind.T7, NpForm.Ring, Home(null))
        val b = draw(r, 1000)
        assertEquals(255, b.red(360, 640))
        assertEquals(0, b.blue(360, 640))
    }

    @Test
    fun theFrontFormDrawsThePaneAndThenItsTurnedCopyInsteadOfNowPlaying() {
        val r = run(MoveKind.T7, NpForm.Front, Home(Edges(0, 0, 720, 1280)))
        assertEquals(255, draw(r, 0).green(360, 640))
        val turned = draw(r, 200)
        assertTrue((0 until 720 step 40).any { turned.green(it, 640) > 0 })
        assertEquals(0, turned.red(360, 640))
    }

    @Test
    fun t8TurnsNowPlayingOutThenBringsHomeUnderIn() {
        val r = run(MoveKind.T8, NpForm.Page, Home(null))
        assertTrue((0 until 720 step 40).any { draw(r, 100).let { b -> b.red(it, 640) > 0 && b.blue(it, 640) == 0 } })
        val gone = draw(r, 200)
        assertEquals(0, gone.red(360, 640))
        val home = draw(r, 900)
        assertEquals(255, home.blue(360, 640))
    }

    @Test
    fun t8FrontBringsTheQuickplayFrontInNotHome() {
        val r = run(MoveKind.T8, NpForm.Front, Home(Edges(0, 0, 720, 1280)))
        val b = draw(r, 900)
        assertEquals(255, b.green(360, 640))
        assertEquals(0, b.blue(360, 640))
    }

    @Test
    fun theRestDrawsTheFocusedRingOnlyWhenTheFocusIsShown() {
        val g = NpLayout(s)
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = NowPlayingModel()
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000))), 0)
        m.down(g, g.ringCxU(1), g.ringYU, 0)
        m.tap(g, 10)
        assertEquals(1, m.ring)
        assertTrue(!m.titleFocus)
        val now = 1000L
        assertEquals(0f, m.press[1].look(now.toFloat()), 0f)
        val inks = NpInks(NpMeasure(g, TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null), m)
        fun rest(hide: Boolean): Bitmap {
            val paint = NpPaint(g, m, listOf(npFlat(g) to inks), { null }, Color.Red, { hide })
            val img = ImageBitmap(720, 1280)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) {
                drawRect(Color.Black, Offset.Zero, size)
                with(paint) { rest(now, null, "np") }
            }
            return img.asAndroidBitmap()
        }
        val r = g.ringD / 2f - (g.ringStroke + g.ringStrokeFocused) / 2f
        val x = Math.round(g.ringCx(1) - r - 0.5f)
        val y = g.ringCy
        val shown = rest(false)
        val hidden = rest(true)
        assertTrue("the focused stroke inks $x, $y", shown.green(x, y) > 0)
        assertEquals("the idle stroke leaves $x, $y", 0, hidden.green(x, y))
        assertNotEquals(shown.getPixel(x, y), hidden.getPixel(x, y))
    }

    @Test
    fun theMoveDrawsEachPartOfASplitPageInItsOwnHalf() {
        val w = Window(1080, 2520, 35, none, none, none, none, none, null)
        val flat = NpLayout(scale(w, Nav.None, Surface.NowPlaying, false))
        val split = NpSplit.of(w, Nav.None, false, Fold(1260, 1260), 0)
        assertNotNull(split)
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = NowPlayingModel()
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000))), 0)
        val halves = split!!.halves.map { h -> h to NpInks(NpMeasure(h.g, TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null), m) }
        val paint = NpPaint(flat, m, halves, { null }, Color.Red)
        val parts = listOf(
            Part.NpStatus, Part.NpArtist, Part.NpAlbum, Part.NpIn, Part.NpBar, Part.NpTimeL, Part.NpTimeR, Part.NpTitle, Part.NpNext,
            Part.NpShuffle, Part.NpRings, Part.NpRing,
        )
        val now = 1000L
        fun frame(block: DrawScope.() -> Unit): Bitmap {
            val img = ImageBitmap(1080, 2520)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(1080f, 2520f)) {
                drawRect(Color.Black, Offset.Zero, size)
                block()
            }
            return img.asAndroidBitmap()
        }
        val rest = frame { with(paint) { rest(now, null, "np") } }
        val moved = frame { with(paint) { for (p in parts) npPart(p, now, null) } }
        val low = split.lower
        val x = low.x + low.g.ringCx(0) - low.g.ringD / 2 + low.g.ringStroke / 2
        val y = low.y - low.dy + low.g.ringCy
        assertTrue("the lower ring inks $x, $y at rest", rest.red(x, y) > 0)
        assertEquals("the moved lower ring at $x, $y", rest.getPixel(x, y), moved.getPixel(x, y))
        val fx = flat.ringCx(0) - flat.ringD / 2 + flat.ringStroke / 2
        assertEquals("nothing at the flat ring place $fx, ${flat.ringCy}", rest.getPixel(fx, flat.ringCy), moved.getPixel(fx, flat.ringCy))
        val up = split.upper
        val ring = paint.npRing()
        assertEquals(up.x + up.g.ringRect.x0, ring.x0)
        assertEquals(up.y + up.g.ringRect.y0, ring.y0)
    }

    private fun statusInk(battery: Int): StatusInk {
        val box = npStatus(s)
        val line = statusLine(s, box)
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val style = roleStyle(Roles.time, s, density, Ink.white, Tok.alpha_full).merge(Fonts.style(Roles.time, line.textPx, density, Roles.time.tracking * s.ppu))
        val percent = TextMeasurer(fonts, density, LayoutDirection.Ltr).word("$battery%", Roles.time, style, fonts, density, null, s.board.fontHanMinPx)
        return StatusInk(StatusState("", battery, false, Play.Playing, false, -1, -1), StatusText(null, percent, null), line, s, box)
    }

    private fun npPaint(status: StatusInk?, accentOff: Boolean = false): NpPaint {
        val g = NpLayout(s)
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 1, index = 0, track = 1, playing = true))
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000))), 0)
        m.resetFocus(0)
        val inks = NpInks(NpMeasure(g, TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null), m)
        return NpPaint(g, m, listOf(npFlat(g) to inks), { null }, Color.Red, status = { status }, accentOff = accentOff)
    }

    private fun npRun(k: MoveKind, paint: PagePaint): Run {
        val move = PageMove()
        val mp = MovePaint(move)
        mp.npForm = NpForm.Page
        val np = PageSide(s, paint)
        val page = PageSide(s, Fill(Color.Blue))
        val t = if (k == MoveKind.T7) Turn(k, "page", "np", false) else Turn(k, "np", "page", true)
        val e = mp.ends(t, HomeSide(s, Home(null), 0f), if (k == MoveKind.T7) page else np, if (k == MoveKind.T7) np else page, forms)
        move.start(k, 0, e, forms)
        return Run(move, mp)
    }

    private fun band(b: Bitmap): List<Int> {
        val box = npStatus(s)
        val x0 = px(box.left, s.ppu)
        return (px(box.top, s.ppu) until px(box.top + box.height, s.ppu)).flatMap { y -> (x0 until s.widthPx).map { x -> b.getPixel(x, y) } }
    }

    private fun drawWith(r: Run, now: Long, over: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) {
            drawRect(Color.Black, Offset.Zero, size)
            with(r.paint) { drawMove(now, null) }
            over()
        }
        return img.asAndroidBitmap()
    }

    @Test
    fun theStatusRowStepsInOnTheNowPlayingPlaneAtTheT7StatusStep() {
        val ink = statusInk(HALF_BATTERY)
        val rowed = npRun(MoveKind.T7, npPaint(ink))
        val bare = npRun(MoveKind.T7, npPaint(null))
        assertEquals("no status row before the step", band(draw(bare, STEP_MS - 1)), band(draw(rowed, STEP_MS - 1)))
        assertNotEquals("the status row at the step", band(draw(bare, STEP_MS)), band(draw(rowed, STEP_MS)))
        assertEquals("at rest the band is the row drawn alone", band(drawWith(bare, REST_MS) { statusRow(ink, Color.Red, false, 1f) }), band(draw(rowed, REST_MS)))
    }

    @Test
    fun t8TurnsTheStatusRowOutWithNowPlaying() {
        val rowed = npRun(MoveKind.T8, npPaint(statusInk(HALF_BATTERY)))
        val bare = npRun(MoveKind.T8, npPaint(null))
        assertFalse("the row leaves with now playing", draw(bare, OUT_MS).sameAs(draw(rowed, OUT_MS)))
        assertTrue("the row is gone with now playing", draw(bare, GONE_MS).sameAs(draw(rowed, GONE_MS)))
    }

    @Test
    fun aLowBatteryWithTheAccentOnDrawsTheOutline() {
        val ink = statusInk(LOW_BATTERY)
        val on = npRun(MoveKind.T7, npPaint(ink))
        val off = npRun(MoveKind.T7, npPaint(ink, accentOff = true))
        assertNotEquals("the accent on draws the low battery in the accent", band(draw(off, REST_MS)), band(draw(on, REST_MS)))
    }

    private companion object {
        const val HALF_BATTERY = 50
        const val LOW_BATTERY = 5
        const val STEP_MS = 617L
        const val REST_MS = 1000L
        const val OUT_MS = 100L
        const val GONE_MS = 217L
    }
}
