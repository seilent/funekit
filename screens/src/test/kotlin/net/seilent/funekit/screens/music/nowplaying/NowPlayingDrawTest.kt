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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Case
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Weight
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.np.NP_MARK
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.GlowText
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.pressGlow
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.word
import net.seilent.funekit.kit.np.NP_BACK
import net.seilent.funekit.kit.np.NP_RINGS
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.api.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class NowPlayingDrawTest {
    private val none = Insets(0, 0, 0, 0)
    private val g = NpLayout(scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false))

    private fun draw(block: DrawScope.() -> Unit): Bitmap {
        val img = ImageBitmap(720, 1280)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(720f, 1280f)) {
            drawRect(Color.Black, Offset.Zero, size)
            block()
        }
        return img.asAndroidBitmap()
    }

    private fun red(b: Bitmap, x: Int, y: Int) = (b.getPixel(x, y) shr 16) and 0xFF

    private fun glyphRed(b: Bitmap, i: Int): Int {
        val h = g.ringD / 4
        var m = 0
        for (y in g.ringCy - h..g.ringCy + h) for (x in g.ringCx(i) - h..g.ringCx(i) + h) m = maxOf(m, red(b, x, y))
        return m
    }

    private fun model(state: PlayerState = PlayerState(queueSize = 1, index = 0, track = 1)): NowPlayingModel {
        val m = NowPlayingModel()
        m.feed(state)
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000, album = "Album"))), 0)
        m.resetFocus(0)
        return m
    }

    private fun rings(m: NowPlayingModel, hide: Boolean = false) = draw { npRings(g, npRingsLook(m, 0, hide), 1f, null) }

    @Test
    fun theFocusedRingTakesTheFocusedStroke() {
        val m = model()
        val focused = rings(m)
        val hidden = rings(m, hide = true)
        val x = g.ringCx(0) - 35
        assertTrue(red(focused, x, g.ringCy) > 0)
        assertEquals(0, red(hidden, x, g.ringCy))
    }

    @Test
    fun aLitRingDrawsItsGlyphAtFullAlphaAndAnUnlitOneAtMidWithTheRingAtTheIdleAlpha() {
        val off = rings(model())
        val on = rings(model(PlayerState(queueSize = 1, index = 0, track = 1, shuffle = true)))
        val y = g.ringCy
        val x = g.ringCx(0) + g.ringD / 2 - g.ringStroke / 2
        assertTrue("ring ink at ($x, $y)", red(off, x, y) > 0)
        assertEquals(red(off, x, y), red(on, x, y))
        assertTrue(glyphRed(on, 0) > glyphRed(off, 0))
    }

    private fun repeat(r: Repeat) = rings(model(PlayerState(queueSize = 1, index = 0, track = 1, repeat = r)))

    private fun around(deg: Float, r: Float): Pair<Int, Int> {
        val a = Math.toRadians(deg.toDouble())
        return Math.round(g.ringCx(1) + r * sin(a)).toInt() to Math.round(g.ringCy - r * cos(a)).toInt()
    }

    private fun lit(b: Bitmap, at: Pair<Int, Int>) = red(b, at.first, at.second) > LIT

    private class OneBox(val x: Float, val y: Float, val k: Float) {
        fun at(u: Float, v: Float): Pair<Int, Int> = Math.round(x + u * k) to Math.round(y + v * k)

        fun holds(p: Pair<Int, Int>): Boolean =
            p.first >= x + ONE_X0 * k - 1 && p.first <= x + ONE_X1 * k + 1 && p.second >= y + ONE_Y0 * k - 1 && p.second <= y + ONE_Y1 * k + 1
    }

    private fun oneBox(): OneBox {
        val a = Math.toRadians(Tok.npRepeatOneAt.toDouble())
        val r = Tok.npRepeatOneR * g.p
        val ox = g.ringCx(1) + Math.round(r * sin(a)).toInt()
        val oy = g.ringCy - Math.round(r * cos(a)).toInt()
        val size = g.repeatOne
        return OneBox(ox - size / 2f, oy - size / 2f, size / Tok.glyphBox)
    }

    @Test
    fun repeatOneDrawsTheArcFromTheTopToNineWithItsOneInTheGap() {
        val one = repeat(Repeat.One)
        val r = (g.ringD - g.ringStroke) / 2f
        for (deg in ARC_FROM..ARC_TO step ARC_STEP) assertTrue("$deg lit", lit(one, around(deg.toFloat(), r)))
        val box = oneBox()
        for (deg in GAP_FROM..GAP_TO step GAP_STEP) {
            val p = around(deg.toFloat(), r)
            if (!box.holds(p)) assertFalse("$deg dark", lit(one, p))
        }
        assertTrue("the one's stem", lit(one, box.at(STEM_X, STEM_Y)))
        assertTrue("the one's flag", lit(one, box.at(FLAG_X, FLAG_Y)))
    }

    @Test
    fun repeatAllDrawsTheWholeRingAndNoOne() {
        val all = repeat(Repeat.All)
        val r = (g.ringD - g.ringStroke) / 2f
        for (deg in 0 until FULL_TURN step RING_STEP) assertTrue("$deg lit", lit(all, around(deg.toFloat(), r)))
        assertFalse("no one", lit(all, oneBox().at(FLAG_X, FLAG_Y)))
    }

    @Test
    fun repeatOneKeepsTheRepeatGlyphOfRepeatAll() {
        val all = repeat(Repeat.All)
        val one = repeat(Repeat.One)
        val h = g.ringD / 4
        for (y in g.ringCy - h..g.ringCy + h) for (x in g.ringCx(1) - h..g.ringCx(1) + h) assertEquals("glyph at $x, $y", red(all, x, y), red(one, x, y))
    }

    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)

    private fun scaled(w: Int, h: Int) = scale(Window(w, h, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)

    @Test
    fun theWordsTakeTheirRolesAtTheBoardSizes() {
        for ((name, s) in listOf("a105" to scaled(720, 1280), "v1" to scaled(240, 320))) {
            val at = NpLayout(s)
            val me = NpMeasure(at, measurer, fonts, density, null)
            val n = NpShown(1, 180000, "Artist", "Album", "Song", "Next", emptyList(), null, "")
            val ink = me.ink(n, false)
            val time = me.time(NpTimes("1:23", "-2:00", 0, true))
            val want = listOf(
                Triple("artist", ink.artist, Role(Tok.type_npViewArtist_size, Weight.Bold, Case.Upper, Tok.type_npViewArtist_alpha, Tok.tracking)) to Roles.npViewArtist,
                Triple("album", ink.album, Role(Tok.type_npViewAlbum_size, Weight.Regular, Case.Upper, Tok.type_npViewAlbum_alpha, Tok.tracking)) to Roles.npViewAlbum,
                Triple("title", ink.title, Role(Tok.type_npViewTitle_size, Weight.Regular, Case.Mixed, Tok.type_npViewTitle_alpha, Tok.tracking)) to Roles.npViewTitle,
                Triple("next", ink.next[0], Role(s.board.type_npViewNext_size, Weight.Light, Case.Mixed, Tok.type_npViewNext_alpha, Tok.tracking)) to npNextRole(at, false),
                Triple("time", time.left, Role(Tok.type_npViewTime_size, Weight.Bold, Case.Mixed, Tok.type_npViewTime_alpha, Tok.tracking)) to npTimeRole(at),
            )
            val texts = mapOf("artist" to n.artist, "album" to n.album, "title" to n.title, "next" to n.next, "time" to "1:23")
            for ((w, kit) in want) {
                val (what, word, role) = w
                assertEquals("$name $what width", me.word(texts.getValue(what), role).width, word.width, 0f)
                assertEquals("$name $what alpha", role.alpha, kit.alpha, 0f)
                assertEquals("$name $what weight", role.weight, kit.weight)
                assertEquals("$name $what px", rolePx(role, s, at.p), rolePx(kit, s, at.p))
            }
        }
    }

    @Test
    fun anotherAppsSessionDrawsItsFromLineAtTheTextEdgeAndItsCountAtTheNextIndent() {
        val me = NpMeasure(g, measurer, fonts, density, null)
        val n = NpShown(1, 0, "", "", "", "3 of 9", emptyList(), null, "Radio")
        val ink = me.ink(n, true)
        val from = Role(Tok.type_sectionLabel_size, Weight.Regular, Case.Upper, Tok.type_sectionLabel_alpha, Tok.tracking)
        val count = Role(Tok.type_npCount_size, Weight.Light, Case.Upper, Tok.type_npCount_alpha, Tok.tracking)
        val kit = draw {
            npTop(g, ink, true, 1f, null)
            npTitle(g, ink, true, 1f, null)
        }
        val want = draw {
            clipRect(px(Tok.npTextX, g.p).toFloat(), 0f, px(Tok.npTextRight, g.p).toFloat(), size.height) {
                word(me.word("Radio", from), penPx(pen64(Tok.npTextX, g.p)), px(g.s.board.npFromBaseline, g.p).toFloat(), Ink.white, from.alpha)
                word(me.word("3 of 9", count), penPx(pen64(Tok.npTextX + Tok.npNextIndent, g.p)), g.nextBase(0).toFloat(), Ink.white, count.alpha)
            }
        }
        assertTrue("the from line at the text edge and the count at the next indent", kit.sameAs(want))
    }

    @Test
    fun aHeldTitleDrawsThePressGlowUnderTheTitleLineAlone() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 2, index = 0, track = 1))
        m.sync(NpShown.of(listOf(Row(1, Kind.Song, "Song", "Artist", null, durationMs = 180000, album = "Album"), Row(2, Kind.Song, "Next song", "Artist", null, durationMs = 180000, album = "Album"))), 0)
        m.resetFocus(0)
        val ink = NpMeasure(g, measurer, fonts, density, null).ink(m.shown, false)
        assertTrue("premise: the next line inks", ink.next[0].width > 0)
        m.down(g, Tok.npTextX + TITLE_DX, g.s.board.npTitleY - TITLE_DY, 0)
        val lit = checkNotNull(npTitleGlow(m, Tok.motion_pressGlow.toLong()))
        val rest = draw { npTitle(g, ink, false, 1f, null) }
        val held = draw { npTitle(g, ink, false, 1f, null, RowGlow(m.titleGlow, lit)) }
        val c = g.textClip
        val want = draw {
            pressGlow(m.titleGlow, listOf(GlowText(ink.title, g.textX64, g.titleY, rolePx(Roles.npViewTitle, g.s))), Edges(c.x0, c.y0, c.x1, c.y1), g.s.ppu, lit)
            npTitle(g, ink, false, 1f, null)
        }
        assertFalse("premise: the held title glows", held.sameAs(rest))
        assertTrue("the held title draws the glow of its own line under it", held.sameAs(want))
        m.tap(g, Tok.motion_pressGlow.toLong() + 10)
        val after = draw { npTitle(g, ink, false, 1f, null, npTitleGlow(m, Tok.motion_pressGlow.toLong() + 10)?.let { RowGlow(m.titleGlow, it) }) }
        assertTrue("after the tap the title draws at rest", after.sameAs(rest))
    }

    @Test
    fun aPressedMarkDrawsAtThePressScaleWithNoDiscOrHalo() {
        val m = model()
        m.mark = true
        val heldAt = (Tok.motion_press + FRAME).toLong()
        val rest = draw { npMark(g, npMarkLook(m, 0), 1f, null) }
        m.press[NP_MARK].down(0f)
        assertEquals("the mark takes the press scale", Tok.motion_pressScale, npMarkLook(m, heldAt).press, 0.001f)
        val held = draw { npMark(g, npMarkLook(m, heldAt), 1f, null) }
        val r = g.ringD / 2
        val side = if (g.markX < g.s.widthPx / 2) 1 else -1
        fun edge(b: Bitmap): Int = (0..r + 2).last { red(b, g.markX + side * it, g.markY) > 0 }
        assertEquals("the ring at the press scale", Tok.motion_pressScale, edge(held).toFloat() / edge(rest), SCALE_TOLERANCE)
        val inside = g.markX + side * (Math.round(r * Tok.motion_pressScale) - g.ringStroke - Math.round(DISC_IN * g.p))
        val outside = g.markX + side * (r + Math.round(MARK_OUT * g.p))
        assertEquals("no disc", red(rest, inside, g.markY), red(held, inside, g.markY))
        assertEquals("no halo", red(rest, outside, g.markY), red(held, outside, g.markY))
    }

    @Test
    fun theHeartRingIsLitForAFavorite() {
        val m = model()
        val off = rings(m)
        m.favorite = true
        val on = rings(m)
        val x = g.ringCx(2) + g.ringD / 2 - g.ringStroke / 2
        assertTrue("ring ink at ($x, ${g.ringCy})", red(off, x, g.ringCy) > 0)
        assertEquals(red(off, x, g.ringCy), red(on, x, g.ringCy))
        assertTrue(glyphRed(on, 2) > glyphRed(off, 2))
    }

    @Test
    fun theMarkRingDrawsOnlyWithLyrics() {
        val m = model()
        val off = draw { npMark(g, npMarkLook(m, 0), 1f, null) }
        m.mark = true
        val on = draw { npMark(g, npMarkLook(m, 0), 1f, null) }
        val x = g.markX + g.ringD / 2 - 2
        assertEquals(0, red(off, x, g.markY))
        assertTrue(red(on, x, g.markY) > 0)
    }

    private class Probe(val name: String, val press: Int, val cx: Int, val cy: Int, val r: Int, val stroke: Int)

    @Test
    fun aPressedRingAndTheBackRingDrawTheDiscAndTheHaloUntilTheCancel() {
        val m = model()
        val heldAt = Tok.motion_pressGlow + FRAME
        val backAt = heldAt + Tok.motion_press + 2 * FRAME
        fun look(at: Float) = draw {
            npRings(g, npRingsLook(m, at.toLong(), true), 1f, null)
            npBackRing(g, npBackLook(m, at.toLong(), false), 1f, null)
        }
        val probes = (0 until NP_RINGS).map { Probe("ring $it", it, g.ringCx(it), g.ringCy, g.ringD / 2, g.ringStroke) } +
            Probe("back", NP_BACK, g.backCx, g.backCy, g.backSize / 2, g.backStroke)
        for (k in probes) {
            val rest = look(0f)
            m.press[k.press].down(0f)
            val held = look(heldAt)
            val side = if (k.cx < g.s.widthPx / 2) 1 else -1
            val inside = k.cx + side * (k.r - k.stroke - Math.round(DISC_IN * g.p))
            val outside = k.cx + side * (k.r + Math.round(HALO_OUT * g.p))
            val inBase = red(rest, inside, k.cy)
            assertEquals("${k.name} disc", Tok.vol_discAlpha, (red(held, inside, k.cy) - inBase).toFloat() / (255 - inBase), DISC_TOLERANCE)
            assertTrue("${k.name} halo", red(held, outside, k.cy) > red(rest, outside, k.cy) + HALO_LIFT)
            m.press[k.press].cancel(heldAt)
            val back = look(backAt)
            assertEquals("${k.name} disc back", inBase, red(back, inside, k.cy))
            assertEquals("${k.name} halo back", red(rest, outside, k.cy), red(back, outside, k.cy))
            m.press[k.press].reset()
        }
    }

    @Test
    fun theBackRingTakesTheIdleStrokeWhileTheFocusIsHidden() {
        val m = model()
        val shown = draw { npBackRing(g, npBackLook(m, 0, false), 1f, null) }
        val hidden = draw { npBackRing(g, npBackLook(m, 0, true), 1f, null) }
        val y = g.backCy
        val x = g.backCx + g.backSize / 2 - 14
        assertTrue(red(shown, x, y) > 0)
        assertEquals(0, red(hidden, x, y))
    }

    private companion object {
        const val FRAME = 16f
        const val TITLE_DX = 20f
        const val TITLE_DY = 4f
        const val DISC_IN = 1f
        const val HALO_OUT = 0.25f
        const val MARK_OUT = 0.5f
        const val HALO_LIFT = 10
        const val DISC_TOLERANCE = 0.08f
        const val SCALE_TOLERANCE = 0.03f
        const val LIT = 80
        const val ARC_FROM = 2
        const val ARC_TO = 268
        const val ARC_STEP = 6
        const val GAP_FROM = 280
        const val GAP_TO = 350
        const val GAP_STEP = 5
        const val FULL_TURN = 360
        const val RING_STEP = 10
        const val ONE_X0 = 13.94f
        const val ONE_Y0 = 9.76f
        const val ONE_X1 = 22.2f
        const val ONE_Y1 = 22.24f
        const val STEM_X = 18.2f
        const val STEM_Y = 16f
        const val FLAG_X = 15.7f
        const val FLAG_Y = 11.82f
    }
}
