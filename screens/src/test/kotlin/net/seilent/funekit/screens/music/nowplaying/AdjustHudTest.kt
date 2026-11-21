package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AdjustHudTest {
    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val big = scale(Window(720, 960, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)

    private fun hud(vararg cols: AdjustCol) = AdjustHud().also { it.open(0, cols.toList(), "lyrics", "+100 ms") }

    @Test
    fun theExitCornerAndTheControlsOfOneColumnTakeTheirTaps() {
        val h = hud(AdjustCol("100", "offset ms"))
        assertEquals(AdjustHit.Exit, h.hit(10f, 10f, v1))
        assertEquals(AdjustHit.Exit, h.hit(53.4f, 10f, v1))
        assertEquals(AdjustHit.None, h.hit(53.6f, 10f, v1))
        assertEquals(AdjustHit.None, h.hit(54f, 10f, v1))
        assertEquals(AdjustHit.Up0, h.hit(120f, Tok.overlayPlusY, v1))
        assertEquals(AdjustHit.Up0, h.hit(100f, 50f, v1))
        assertEquals(AdjustHit.Down0, h.hit(120f, v1.board.overlayMinusY, v1))
        assertEquals(AdjustHit.None, h.hit(120f, v1.board.overlayRingY, v1))
        assertEquals(AdjustHit.None, h.hit(200f, Tok.overlayPlusY, v1))
    }

    @Test
    fun aControlAtTheEndOfTheRangeTakesNoTap() {
        val h = hud(AdjustCol("60000", "offset ms", up = false, down = true))
        assertEquals(AdjustHit.None, h.hit(120f, Tok.overlayPlusY, v1))
        assertEquals(AdjustHit.Down0, h.hit(120f, v1.board.overlayMinusY, v1))
    }

    @Test
    fun twoColumnsSitAtTheirEdges() {
        val h = hud(AdjustCol("0", "left db"), AdjustCol("0", "right db"))
        assertEquals(px(Tok.edge + Tok.adjustColumn, 1f), h.colX(0, v1))
        assertEquals(AdjustHit.Down1, h.hit(px(Tok.canvasW - Tok.edge - Tok.adjustColumn, 1f).toFloat(), v1.board.overlayMinusY, v1))
    }

    @Test
    fun theOverlayRisesOverTheVolumeOpenAndFadesOverTheTouchClose() {
        val h = hud(AdjustCol("0", "offset ms"))
        assertTrue(h.legs(0).scrim > 0f && h.legs(0).scrim < 1f)
        assertTrue(h.moving(Tok.motion_volumeOpenEnd.toLong() - 1))
        val open = h.legs(Tok.motion_volumeOpenEnd.toLong())
        assertEquals(1f, open.scrim, 0f)
        assertEquals(1f, open.number, 0.001f)
        assertEquals(0f, open.textDx, 0.001f)
        assertFalse(h.moving(Tok.motion_volumeOpenEnd.toLong()))
        h.hide(1000)
        assertTrue(h.closing())
        assertEquals(0f, h.legs(1000 + Tok.motion_volumeTouchClose.toLong()).number, 0f)
        assertTrue(h.visible(1000 + VolumeHud.closeMs() - 1))
        assertFalse(h.visible(1000 + VolumeHud.closeMs()))
    }

    @Test
    fun theNumberIsPlacedForItsHoldAndTheScrimDimsThePage() {
        val h = hud(AdjustCol("-300", "offset ms"))
        val img = ImageBitmap(big.widthPx, big.heightPx)
        val ink = AdjustInk(big, measurer, fonts, density, null)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(big.widthPx.toFloat(), big.heightPx.toFloat())) {
            drawRect(Ink.white)
            adjustHud(h, big, ink, Tok.motion_volumeOpenEnd.toLong())
        }
        val b = img.asAndroidBitmap()
        val base = px(big.board.overlayRingY, big.stdPpu)
        assertEquals(0, h.numberAt(px(Tok.edge, big.stdPpu).toFloat() + 2, base - 2f))
        assertEquals(-1, h.numberAt(big.widthPx - 2f, base - 2f))
        val ground = (b.getPixel(big.widthPx - 4, big.heightPx / 2 + 8) shr 16) and 0xFF
        assertTrue(abs(ground - 255 * (1 - Tok.scrim_overlay_alpha)) <= 3)
    }

    private val open = Tok.motion_volumeOpenEnd.toLong()

    private fun place(h: AdjustHud): AdjustPlace = adjustPlace(h, big, AdjustInk(big, measurer, fonts, density, null), open)

    @Test
    fun oneColumnSitsAtTheScreenCentreWithItsNumberAtTheEdge() {
        val h = hud(AdjustCol("-300", "offset ms"))
        assertEquals(big.widthPx / 2, h.colX(0, big))
        val p = big.stdPpu
        val ink = AdjustInk(big, measurer, fonts, density, null)
        val pl = adjustPlace(h, big, ink, open)
        val npx = ink.px(Roles.overlayNumber)
        val num = pl.texts.single { it.text == "-300" }
        assertEquals(px(big.board.overlayRingY, p) + (npx * Tok.adjustNumberDrop).roundToInt(), num.base)
        assertEquals(pen64(Tok.edge, p), num.x64)
        assertEquals(num.base - Metrics.ascent(npx), pl.numbers.single().top)
        val exit = pl.texts.single { it.text == "exit" }
        assertEquals(pen64(Tok.edge, p), exit.x64)
        assertEquals(px(Tok.edge, p) + Metrics.ascent(ink.px(Roles.overlayLabel)), exit.base)
        val label = pl.texts.single { it.text == "offset ms" }
        assertEquals(px(big.board.overlayMinusY + Tok.overlayGlyph / 2, p) + px(Tok.overlayLabelDrop, p), label.base)
        assertEquals(h.colX(0, big) * 64 - pen64(ink.word(Roles.overlayLabel, "offset ms").width) / 2, label.x64)
    }

    @Test
    fun theLinesSitAboveTheTitleBottomInsideTheEdges() {
        val pl = place(hud(AdjustCol("0", "offset ms")))
        val p = big.stdPpu
        val ink = AdjustInk(big, measurer, fonts, density, null)
        val line2 = pl.texts.single { it.text == "+100 MS" }
        val line1 = pl.texts.single { it.text == "lyrics" }
        assertEquals(px(big.board.overlayTitleBottom, p) - Metrics.descent(ink.px(Roles.toastTitle)), line2.base)
        assertEquals(line2.base - px(Tok.overlayTextPitch, p), line1.base)
        assertEquals(IntRect(px(Tok.edge, p), 0, px(Tok.canvasW - Tok.edge, p), big.heightPx), line2.clip)
        assertEquals(pen64(Tok.edge, p), line1.x64)
        assertEquals(Tok.overlayDim * Roles.toastTitle.alpha, line2.alpha, 0.001f)
    }

    @Test
    fun aControlAtTheRangeEndDrawsDim() {
        val pl = place(hud(AdjustCol("60000", "offset ms", up = false, down = true)))
        assertEquals(Tok.overlayDim, pl.glyphs.single { it.plus }.alpha, 0.001f)
        assertEquals(Tok.overlayLit, pl.glyphs.single { !it.plus }.alpha, 0.001f)
        assertEquals(px(Tok.overlayPlusY, big.stdPpu), pl.glyphs.single { it.plus }.y)
    }

    @Test
    fun twoColumnNumbersCentreOnTheirColumns() {
        val h = hud(AdjustCol("12", "left db"), AdjustCol("345", "right db"))
        val ink = AdjustInk(big, measurer, fonts, density, null)
        val pl = adjustPlace(h, big, ink, open)
        for ((i, n) in listOf("12", "345").withIndex()) {
            val t = pl.texts.single { it.text == n }
            assertEquals(h.colX(i, big) * 64 - pen64(ink.word(Roles.overlayNumber, n).width) / 2, t.x64)
        }
    }

    @Test
    fun theWordCacheKeepsOnlyRecentFrames() {
        val ink = AdjustInk(big, measurer, fonts, density, null)
        val w = ink.word(Roles.overlayNumber, "1")
        ink.frame()
        assertSame(w, ink.word(Roles.overlayNumber, "1"))
        ink.frame()
        ink.frame()
        assertNotSame(w, ink.word(Roles.overlayNumber, "1"))
    }
}
