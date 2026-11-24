package net.seilent.funekit.kit.home

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penFloor
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GROW = 3
private const val HALF_LEVEL = 128
private const val ALPHA_SHIFT = 24
private const val EDGE_LEVEL = 4
private const val FRAME_MS = 16L

private enum class Key(override val word: String, override val sub: String) : HomeEntry {
    Music("music", "albums artists playlists songs genres"),
    Playlists("playlists", "now playing saved"),
    History("history", "recent new"),
    Apps("apps", "no apps"),
    Settings("settings", "output library display power"),
    ;

    override val ring: Boolean get() = this == Music
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HomeGlowTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Home, false)

    @Test
    fun aGlowingItemAtRestDrawsOneMaskAcrossFrames() {
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = TextMeasurer(fonts, density, LayoutDirection.Ltr)
        val keys = Key.entries
        val ink = homeInk(m, fonts, s, density, keys) { if (it == Key.Apps) "" else it.sub }
        val geo = HomeGeo(s, keys.size)
        val menu = HomeMenu(keys.size)
        val mask = Glow::class.java.getDeclaredField("mask").apply { isAccessible = true }
        val img = ImageBitmap(s.widthPx, geo.contentPx)
        fun draw(t: Long): Any? {
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(s.widthPx.toFloat(), geo.contentPx.toFloat())) {
                homeMenu(ink, menu, geo, menu.restAt(t, false), null)
            }
            return mask.get(menu.glow)
        }
        menu.down(1, 1000L)
        val first = draw(1016L)
        assertNotNull(first)
        assertSame(first, draw(1032L))
        assertSame(first, draw(1048L))
    }

    @Test
    fun aPressGlowsPastTheNameAndKeepsTheItemFace() {
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = TextMeasurer(fonts, density, LayoutDirection.Ltr)
        val keys = Key.entries
        val ink = homeInk(m, fonts, s, density, keys) { if (it == Key.Apps) "" else it.sub }
        val geo = HomeGeo(s, keys.size)
        val menu = HomeMenu(keys.size)
        val sp = s.stdPpu
        val w = s.widthPx
        val h = geo.contentPx
        val x64 = pen64(Tok.homeTextX, sp)
        val base = px(geo.baseline(1), sp)
        val band = base - px(Tok.capHeight * Roles.homeItem.size, sp) until base
        val cols = ink.rect(1).x0 until w
        fun unzoomed(t: Long): HomeRest = menu.restAt(t, false).let { r -> r.copy(items = r.items.map { it.copy(zoom = 1f) }) }
        fun draw(block: DrawScope.() -> Unit): IntArray {
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
            return IntArray(w * h).also { img.readPixels(it) }
        }
        fun alpha(p: IntArray, x: Int, y: Int): Int = p[y * w + x] ushr ALPHA_SHIFT
        fun inkWidth(p: IntArray): Int {
            val inked = cols.filter { x -> band.any { y -> alpha(p, x, y) >= HALF_LEVEL } }
            return inked.last() - inked.first() + 1
        }
        menu.down(1, 1000L)
        val glowing = draw { homeMenu(ink, menu, geo, unzoomed(1048L), null) }
        val cut = penFloor(x64) + penFloor(maxOf(pen64(ink.names[1].width), pen64(Tok.homeSubtitleX, sp) + pen64(ink.subs[1].width))) + GROW
        assertTrue(band.any { alpha(glowing, cut, it) > 0 })
        assertTrue(band.all { alpha(glowing, ink.rect(1).x1 - 1, it) < EDGE_LEVEL })
        menu.glow.clear()
        val rest = unzoomed(1048L)
        val a = rest.items[1].alpha
        val pressed = inkWidth(draw { homeMenu(ink, menu, geo, rest, null) })
        val regular = inkWidth(draw { word(ink.names[1], penPx(x64), base.toFloat(), Ink.white, a) })
        val boldStyle = roleStyle(Roles.homeItem, s, density, Ink.white, ppu = sp).copy(fontWeight = FontWeight.Bold)
        val bold = m.word(keys[1].word, Roles.homeItem, boldStyle, fonts, density, null, s.board.fontHanMinPx)
        val boldWidth = inkWidth(draw { word(bold, penPx(x64), base.toFloat(), Ink.white, a) })
        assertEquals(regular.toFloat(), pressed.toFloat(), 1f)
        assertTrue(boldWidth - pressed >= 10)
        val held = menu.restAt(1048L, false)
        assertEquals(1f, held.items[1].zoom)
        assertEquals(regular.toFloat(), inkWidth(draw { homeMenu(ink, menu, geo, held, null) }).toFloat(), 1f)
    }

    @Test
    fun aSlopExitFadesTheDrawnGlowWithThePressLit() {
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = TextMeasurer(fonts, density, LayoutDirection.Ltr)
        val keys = Key.entries
        val ink = homeInk(m, fonts, s, density, keys) { if (it == Key.Apps) "" else it.sub }
        val geo = HomeGeo(s, keys.size)
        val menu = HomeMenu(keys.size)
        val sp = s.stdPpu
        val w = s.widthPx
        val h = geo.contentPx
        val x64 = pen64(Tok.homeTextX, sp)
        val base = px(geo.baseline(2), sp)
        val band = base - px(Tok.capHeight * Roles.homeItem.size, sp) until base
        fun draw(block: DrawScope.() -> Unit): IntArray {
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat()), block)
            return IntArray(w * h).also { img.readPixels(it) }
        }
        fun alpha(p: IntArray, x: Int, y: Int): Int = p[y * w + x] ushr ALPHA_SHIFT
        val name = draw { word(ink.names[2], penPx(x64), base.toFloat(), Ink.white, 1f) }
        val past = (0 until w).last { x -> band.any { y -> alpha(name, x, y) > 0 } } + 1
        var t = 0L
        fun frames(now: Long) {
            while (t <= now) {
                menu.tick(t)
                t += FRAME_MS
            }
        }
        menu.down(2, 0L)
        frames(49L)
        menu.cancel(50L)
        val at50 = draw { homeMenu(ink, menu, geo, menu.restAt(50L, false), null) }
        val y = band.maxBy { alpha(at50, past, it) }
        val a50 = alpha(at50, past, y)
        assertTrue(a50 > EDGE_LEVEL)
        frames(100L)
        val at100 = draw { homeMenu(ink, menu, geo, menu.restAt(100L, false), null) }
        assertEquals(a50 / 2f, alpha(at100, past, y).toFloat(), 2f)
        frames(150L)
        val at150 = draw { homeMenu(ink, menu, geo, menu.restAt(150L, false), null) }
        assertEquals(0, alpha(at150, past, y))
    }

    @Test
    fun aRingPressDrawsItsHaloOverTheSpreadRingAndItsDiscAtScaleOne() {
        val density = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val m = TextMeasurer(fonts, density, LayoutDirection.Ltr)
        val keys = Key.entries
        val ink = homeInk(m, fonts, s, density, keys) { if (it == Key.Apps) "" else it.sub }
        val geo = HomeGeo(s, keys.size)
        val menu = HomeMenu(keys.size)
        val sp = s.stdPpu
        val w = s.widthPx
        val h = geo.contentPx
        val cx = px(Tok.homeRingX, sp)
        val cy = px(Tok.homeRingY, sp)
        val d = px(Tok.homeRingSize, sp)
        val stroke = px(Tok.homeRingStroke, sp)
        val halo = px(Tok.homeRingHalo, sp)
        val spread = px(Tok.homeRingHaloSpread, sp)
        assertEquals(15, spread)
        assertEquals(17, halo)
        fun draw(r: HomeRest): IntArray {
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { homeMenu(ink, menu, geo, r, null) }
            return IntArray(w * h).also { img.readPixels(it) }
        }
        val idle = draw(menu.restAt(5000L, false))
        menu.ringDown(5000L)
        val lit = menu.restAt(5050L, false)
        assertEquals(1f, lit.ringLit)
        val pressed = draw(lit)
        fun at(p: IntArray, dy: Int): Int = p[(cy + dy) * w + cx] ushr ALPHA_SHIFT
        val far = d / 2 + spread + halo / 2
        assertEquals(0, at(idle, far))
        assertTrue(at(pressed, far) > EDGE_LEVEL)
        val inside = d / 2 - stroke - 2
        assertEquals(0, at(idle, inside))
        assertTrue(at(pressed, inside) > EDGE_LEVEL)
        assertEquals(0, at(pressed, d / 2 + spread + halo + 2))
    }
}
