package net.seilent.funekit.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.core.Section
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverlayDrawTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)

    private fun shot(draw: DrawScope.() -> Unit): IntArray {
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) {
            drawRect(Ink.white, Offset.Zero, size)
            draw()
        }
        return IntArray(720 * 1280).also { img.readPixels(it) }
    }

    private fun red(px: IntArray, x: Int, y: Int) = px[y * 720 + x] shr 16 and 0xFF

    private fun near(want: Int, got: Int, tol: Int = 2) = assertTrue("want $want got $got", abs(want - got) <= tol)

    @Test
    fun theJumpGridFramesTheFocusAtTwoUnitsAvailableLettersAtOneAndEmptyOnesNot() {
        val g = JumpGrid().also { it.open(BooleanArray(Section.count) { i -> i != 1 }, 0, 1280 / 3f) }
        fun style(r: Role) = roleStyle(r, a105, unitDensity, Ink.white, ppu = 3f)
        val ink = JumpInk(
            measurer.word("exit", Roles.jumpExit, style(Roles.jumpExit), fonts, unitDensity, null, a105.board.fontHanMinPx),
            (0 until Section.count).map { measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), fonts, unitDensity, covers, a105.board.fontHanMinPx) },
        )
        val px = shot { jumpGrid(g, ink, JumpCells(a105), a105, 0f, 0) }
        near(0xFF, red(px, 200, 130), 0)
        near(0xFF, red(px, 203, 130), 0)
        near(0x11, red(px, 204, 130), 0)
        near(0x11, red(px, 368, 130), 0)
        near(Math.round(0x11 + (0xFF - 0x11) * 0.4f), red(px, 535, 130))
        near(0x11, red(px, 538, 130), 0)
    }

    @Test
    fun aPressedLetterKeepsItsTileAndGlowsItsLetterAtScaleOne() {
        fun style(r: Role) = roleStyle(r, a105, unitDensity, Ink.white, ppu = 3f)
        val ink = JumpInk(
            measurer.word("exit", Roles.jumpExit, style(Roles.jumpExit), fonts, unitDensity, null, a105.board.fontHanMinPx),
            (0 until Section.count).map { measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), fonts, unitDensity, covers, a105.board.fontHanMinPx) },
        )
        val cells = JumpCells(a105)
        val t = cells.at(Jump.slotOf(2), 0f)
        val plain = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, 1280 / 3f) }
        val pressed = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, 1280 / 3f) }
        pressed.press.down(2, 0)
        val a = shot { jumpGrid(plain, ink, cells, a105, 0f, 100) }
        val b = shot { jumpGrid(pressed, ink, cells, a105, 0f, 100) }
        for (x in t.x0 until t.x1) assertEquals(red(a, x, t.y0), red(b, x, t.y0))
        val bw = px(Tok.letterTileBorder, 3f)
        fun lit(p: IntArray): Int {
            var n = 0
            for (y in t.y0 + bw until t.y1 - bw) for (x in t.x0 + bw until t.x1 - bw) if (red(p, x, y) > 0x11) n++
            return n
        }
        assertTrue("${lit(b)} against ${lit(a)}", lit(b) > lit(a))
    }

    @Test
    fun aFocusedExitIsMarkedByItsLabelAtFullAlphaAndNoBar() {
        fun style(r: Role) = roleStyle(r, a105, unitDensity, Ink.white, ppu = 3f)
        val ink = JumpInk(
            measurer.word("exit", Roles.jumpExit, style(Roles.jumpExit), fonts, unitDensity, null, a105.board.fontHanMinPx),
            (0 until Section.count).map { measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), fonts, unitDensity, covers, a105.board.fontHanMinPx) },
        )
        val cells = JumpCells(a105)
        val e = cells.at(Jump.slotOf(Jump.EXIT), 0f)
        val focused = JumpGrid().also { it.open(BooleanArray(Section.count) { false }, 0, 1280 / 3f) }
        assertEquals(Jump.EXIT, focused.focus)
        val idle = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, 1280 / 3f) }
        assertEquals(0, idle.focus)
        val on = shot { jumpGrid(focused, ink, cells, a105, 0f, 0) }
        val off = shot { jumpGrid(idle, ink, cells, a105, 0f, 0) }
        fun most(px: IntArray): Int {
            var m = 0
            for (y in e.y0 until e.y1) for (x in 0 until 190) m = maxOf(m, red(px, x, y))
            return m
        }
        fun plain(): Int {
            val v = mutableListOf<Int>()
            for (y in e.y0 until e.y1) for (x in 0 until 190) if (red(on, x, y) >= 0xFD) v.add(red(off, x, y))
            assertTrue("no fully covered label px", v.isNotEmpty())
            return v.sorted()[v.size / 2]
        }
        near(0xFF, most(on))
        near(Math.round(0x11 + (0xFF - 0x11) * 0.6f), plain())
        for (x in 0 until 190) assertTrue("white column at $x", (e.y0 until e.y1).any { red(on, x, it) < 0xF0 })
    }

    @Test
    fun aHeldToastIsABlackStripAtEightTenthsAcrossTheTop() {
        val t = Toast()
        t.show(0, "artist", "title")
        t.frame(ToastPlace.Top, 1280, 0)
        val ink = ToastInk(
            measurer.word("artist", Roles.toastArtist, roleStyle(Roles.toastArtist, a105, unitDensity, Ink.white), fonts, unitDensity, null, a105.board.fontHanMinPx),
            measurer.word("title", Roles.toastTitle, roleStyle(Roles.toastTitle, a105, unitDensity, Ink.white), fonts, unitDensity, null, a105.board.fontHanMinPx),
        )
        val px = shot { toast(t, ink, a105, 400) }
        near(Math.round(0xFF * 0.2f), red(px, 5, 60))
        near(0xFF, red(px, 5, 121), 0)
        var text = 0
        for (y in 0 until 120) for (x in 48 until 300) text = maxOf(text, red(px, x, y))
        assertTrue("text $text", text > 200)
    }

    @Test
    fun theNavBandRepaintsThePageGroundOverAnOverlay() {
        val nav = scale(Window(720, 1280, 35, none, Insets(0, 0, 0, 108), none, none, none), Nav.Buttons, Surface.Page, false)
        val bands = groundBands(1280, false)
        val accent = Accent.forHue(Accent.defaultHue)
        val px = shot { navBand(nav, NavGround(bands, accent)) }
        val ground = shot { pageGround(bands, accent) }
        assertEquals(0xFFFFFFFF.toInt(), px[1171 * 720 + 10])
        for (y in 1172 until 1280) for (x in listOf(0, 360, 719)) assertEquals("$x $y", ground[y * 720 + x], px[y * 720 + x])
    }

    @Test
    fun onALargePageTheBandAndTheJumpGridEndStayAtAndroidsBarTop() {
        val large = scale(Window(720, 1280, 35, none, Insets(0, 0, 0, 108), none, none, none), Nav.Buttons, Surface.Page, true)
        val bands = groundBands(1280, false)
        val accent = Accent.forHue(Accent.defaultHue)
        val drawn = shot { navBand(large, NavGround(bands, accent)) }
        val ground = shot { pageGround(bands, accent) }
        assertEquals(0xFFFFFFFF.toInt(), drawn[1171 * 720 + 10])
        for (y in 1172 until 1280) for (x in listOf(0, 360, 719)) assertEquals("$x $y", ground[y * 720 + x], drawn[y * 720 + x])
        val g = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, large.stdContentBottom) }
        assertEquals(1172, px(g.bottom, large.stdPpu))
        val p2160 = scale(Window(1080, 2160, 34, none, Insets(0, 0, 0, 132), Insets(0, 0, 0, 132), none, none), Nav.Buttons, Surface.Page, true)
        val m = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, p2160.stdContentBottom) }
        assertEquals(2028, px(m.bottom, p2160.stdPpu))
        val p2160Std = scale(Window(1080, 2160, 34, none, Insets(0, 0, 0, 132), Insets(0, 0, 0, 132), none, none), Nav.Buttons, Surface.Page, false)
        val ms = JumpGrid().also { it.open(BooleanArray(Section.count) { true }, 0, p2160Std.stdContentBottom) }
        assertEquals(2028, px(ms.bottom, p2160Std.stdPpu))
    }

    @Test
    fun aPageWithNoGroundPaintsTheBandInTheBackground() {
        val nav = scale(Window(720, 1280, 35, none, Insets(0, 0, 0, 108), none, none, none), Nav.Buttons, Surface.Page, false)
        val px = shot { navBand(nav, NavGround(emptyList(), Ink.white, ground = false)) }
        near(0xFF, red(px, 10, 1171), 0)
        near(0x11, red(px, 10, 1172), 0)
        near(0x11, red(px, 10, 1279), 0)
    }

    @Test
    fun aFlatGroundNavBandFillsEveryBandRowWithTheBackgroundOverWhatIsUnderIt() {
        val nav = scale(Window(720, 1280, 35, none, Insets(0, 0, 0, 108), none, none, none), Nav.Buttons, Surface.Page, false)
        val px = shot { navBand(nav, NavGround(groundBands(1280, true), Accent.forHue(Accent.defaultHue))) }
        val bg = Ink.bg.toArgb()
        assertEquals(0xFFFFFFFF.toInt(), px[1171 * 720 + 10])
        for (y in 1172 until 1280) for (x in listOf(0, 360, 719)) assertEquals("$x $y", bg, px[y * 720 + x])
    }
}
