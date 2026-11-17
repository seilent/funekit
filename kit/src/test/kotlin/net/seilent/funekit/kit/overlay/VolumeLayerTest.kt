package net.seilent.funekit.kit.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.proj.tiltMatrix
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Kind
import net.seilent.funekit.sheet.Regions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VolumeLayerTest {
    private val v = VolumeParts(TokBoard.v1, 1f)

    private val unit = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())

    private fun shown(playing: Boolean, percent: Int = 50) = VolumeHud().apply { show(0L, percent, playing, "artist", "title") }

    private fun ink(h: VolumeHud) = TextMeasurer(fonts, unit, LayoutDirection.Ltr).volumeInk(h, v, fonts, unit, null)

    private fun pixels(h: VolumeHud, ink: VolumeInk, now: Long, rec: DrawRecord? = null): IntArray {
        val img = ImageBitmap(W, H)
        CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(img), Size(W.toFloat(), H.toFloat())) {
            drawRect(Ink.bg, Offset.Zero, size)
            volumePlate(h, v, ink, Glow(), now, rec)
        }
        return IntArray(W * H).also { img.readPixels(it) }
    }

    private fun red(c: Int) = (c shr 16) and 0xff

    private fun inkBox(pix: IntArray, x0: Int, y0: Int, x1: Int, y1: Int): IntArray? {
        val ground = red(pix[(H - 1) * W + W - 1])
        val half = (255 - ground) / 2
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = Int.MIN_VALUE
        var b = Int.MIN_VALUE
        for (y in maxOf(0, y0) until minOf(H, y1)) {
            for (x in maxOf(0, x0) until minOf(W, x1)) {
                if (red(pix[y * W + x]) - ground < half) continue
                l = minOf(l, x)
                t = minOf(t, y)
                r = maxOf(r, x + 1)
                b = maxOf(b, y + 1)
            }
        }
        return if (r < 0) null else intArrayOf(l, t, r, b)
    }

    @Test
    fun theSkipsSitBesideTheRingAndThePlusAndMinusOnTheirRows() {
        val cx = px(Tok.canvasW, 1f) / 2
        val ry = px(TokBoard.v1.vol_ringY, 1f)
        val sx = px(Tok.vol_skipX, 1f)
        assertEquals(cx - sx to ry, v.anchor(VolumePart.Prev))
        assertEquals(cx + sx to ry, v.anchor(VolumePart.GlowNext))
        assertEquals(cx to px(TokBoard.v1.vol_plusY, 1f), v.anchor(VolumePart.Plus))
        assertEquals(cx to px(TokBoard.v1.vol_minusY, 1f), v.anchor(VolumePart.GlowMinus))
        assertEquals(cx to ry, v.anchor(VolumePart.Ring))
    }

    @Test
    fun atRestEveryPlatePartIsInPlaceAndTheNumberIsNeverMoved() {
        val h = shown(true)
        val l = h.look(1_000L)
        for (p in VolumePart.entries) {
            val f = v.flat(p, l)
            assertEquals("$p scale", 1f, f.scale, 0f)
            assertEquals("$p x", 0f, f.x, 0f)
            assertEquals("$p y", 0f, f.y, 0f)
        }
    }

    @Test
    fun playShowsOnlyPausedAndNoGlowOrDiscWithNoPress() {
        val l = shown(true).look(1_000L)
        assertEquals(0f, v.alpha(VolumePart.Play, l, shown(true)), 0f)
        assertEquals(Tok.overlayLit, v.alpha(VolumePart.Pause, l, shown(true)), 1e-6f)
        assertEquals(Tok.overlayLit, v.alpha(VolumePart.Play, l, shown(false)), 1e-6f)
        assertEquals(0f, v.alpha(VolumePart.Pause, l, shown(false)), 0f)
        for (p in listOf(VolumePart.Disc, VolumePart.GlowRing, VolumePart.GlowPrev, VolumePart.GlowPlus)) assertEquals("$p", 0f, v.alpha(p, l, shown(true)), 0f)
    }

    @Test
    fun theLinesClipAtTheTextXOrAtTheCrossClip() {
        val l = shown(true).look(1_000L)
        assertEquals(px(Tok.vol_textX, 1f), v.lineLeft(l))
    }

    @Test
    fun theNumberShrinksToItsRoomAndStepsDownUntilItsInkFits() {
        assertEquals(76, numberFit(76, 1520) { it * 20 })
        assertEquals(50, numberFit(76, 1000) { it * 20 })
        assertEquals(48, numberFit(76, 1000) { it * 20 + 30 })
    }

    @Test
    fun theNumberRoomEndsThreeEighthsOfThePlusLeftOfTheCentre() {
        assertEquals(5929, v.numberRoom64())
    }

    @Test
    fun theLinesSitUnderTheLabelAtTheV1TextTop() {
        assertEquals(278, v.labelBase())
        assertEquals(291, v.textTop())
        assertEquals(296, v.artistBase())
        assertEquals(315, v.titleBase())
        assertEquals(224, v.lineRight())
    }

    @Test
    fun theExitAndTheReadoutTakeTheirV1Baselines() {
        assertEquals(11, v.exitBase())
        assertEquals(176, v.readoutBase())
    }

    @Test
    fun atRestTheExitIsLitWithNoGlowAndTheLinesTakeTheirRoleAlpha() {
        val l = shown(true).look(1_000L)
        assertEquals(Tok.overlayLit, v.exitAlpha(l), 1e-6f)
        assertEquals(0f, v.exitGlowAlpha(l), 0f)
        assertEquals(Tok.overlayLit * Tok.type_volTitle_alpha, v.lineAlpha(l.line[1], Roles.volTitle), 1e-6f)
        assertEquals(0, v.lineDx(l.line[0]))
    }

    @Test
    fun aTiltedBoxMapsToTheScreenBoundsOfItsCorners() {
        val box = intArrayOf(0, 0, 100, 100)
        assertArrayEquals(box, screenBox(box, doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)))
        assertArrayEquals(intArrayOf(5, 7, 105, 107), screenBox(box, doubleArrayOf(1.0, 0.0, 5.0, 0.0, 1.0, 7.0, 0.0, 0.0, 1.0)))
        assertArrayEquals(intArrayOf(0, 0, 91, 100), screenBox(box, doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.001, 0.0, 1.0)))
    }

    @Test
    fun theRingBoxIsItsSquareAboutTheRingCentreAndAnEmptyLineHasNone() {
        val n = Word.none
        val ink = VolumeInk(n, n, n, 0, n, n, n, n, n, 0, 0)
        assertArrayEquals(intArrayOf(82, 121, 159, 198), v.partBox(VolumePart.Ring, ink))
        assertEquals(null, v.partBox(VolumePart.Label, ink))
        val glow = v.partBox(VolumePart.GlowRing, ink)!!
        assertTrue("the ring glow ${glow.toList()} holds the ring", glow[0] < 82 && glow[1] < 121 && glow[2] > 159 && glow[3] > 198)
    }

    @Test
    fun aDownOnThePlusTiltsThePlateAndMovesTheRingBoxOnScreen() {
        val h = shown(true)
        h.down(1_000L, Tok.canvasW / 2, TokBoard.v1.vol_plusY)
        assertEquals(VolumeHud.Hit.Plus, h.pressed())
        assertEquals(1f, h.tiltK(1_000L), 0f)
        val n = Word.none
        val ring = v.partBox(VolumePart.Ring, VolumeInk(n, n, n, 0, n, n, n, n, n, 0, 0))!!
        val flat = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        assertArrayEquals(ring, screenBox(ring, flat))
        assertNotEquals(ring.toList(), screenBox(ring, v.plate(h, 1f)).toList())
    }

    @Test
    fun theGlyphGlowGrowsByTheSkipGlowWithNoDilate() {
        assertEquals(49, v.glyphGlow(VolumePart.GlowPlus)!!.image.width)
        assertEquals(54, v.glyphGlow(VolumePart.GlowPrev)!!.image.width)
        assertEquals(null, v.glyphGlow(VolumePart.GlowRing))
    }

    @Test
    fun aLongTitleInksFromTheTextXAndNothingPastTheTextRight() {
        val h = VolumeHud().apply { show(0L, 50, true, "artist", "W".repeat(60)) }
        val d = Density(1f)
        val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val ink = TextMeasurer(fonts, d, LayoutDirection.Ltr).volumeInk(h, v, fonts, d, null)
        val w = 240
        val ht = 320
        val img = ImageBitmap(w, ht)
        CanvasDrawScope().draw(d, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), ht.toFloat())) {
            drawRect(Ink.bg, Offset.Zero, size)
            volumePlate(h, v, ink, Glow(), 1_000L)
        }
        val px = IntArray(w * ht)
        img.readPixels(px)
        val ground = px[316 * w + 1]
        val band = (v.titleBase() - 14)..v.titleBase()
        var inked = 0
        for (y in band) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                if (x >= v.lineRight() || x < v.lineLeft(h.look(1_000L))) assertEquals("x $x y $y", ground, c)
                if (c != ground) inked++
            }
        }
        assertTrue("title ink $inked", inked > 0)
    }

    @Test
    fun theLookIsEqualOverAHundredRestFramesAndMovesWhenTheNumberFades() {
        val h = shown(true)
        val ink = ink(h)
        val first = v.drawn(h, ink, 400L)
        assertNotNull(first)
        for (i in 0 until 100) assertEquals("frame $i", first, v.drawn(h, ink, 400L + 5L * i))
        assertNotEquals(first, v.drawn(h, ink, 1_200L))
        assertNull(v.drawn(h, ink, -1L))
    }

    @Test
    fun theNumberInksFromTheNumberX() {
        val h = shown(true)
        val ink = ink(h)
        val pix = pixels(h, ink, 600L)
        val base = v.numberBase(ink.numberPx)
        val plusLeft = v.anchor(VolumePart.Plus).first - px(Tok.vol_plus, 1f) / 2
        val box = inkBox(pix, 0, base + floor(ink.number.inkTop).toInt(), plusLeft, base + 1)!!
        val x = Tok.vol_numberX
        assertTrue("number ink from ${box[0]} where numberX $x", box[0] >= floor(x).toInt() && box[0] <= ceil(x).toInt())
    }

    @Test
    fun threeDigitsShrinkToTheirRoom() {
        val h = shown(true, 100)
        val ink = ink(h)
        assertTrue("number px ${ink.numberPx} under ${v.rolePx(Roles.volNumber)}", ink.numberPx < v.rolePx(Roles.volNumber))
        assertTrue("number ink within its room", ((ink.number.inkRight - ink.number.inkLeft) * 64).roundToInt() <= v.numberRoom64())
        val pix = pixels(h, ink, 600L)
        val plus = v.partBox(VolumePart.Plus, ink)!!
        val base = v.numberBase(ink.numberPx)
        val rows = inkBox(pix, 0, base + floor(ink.number.inkTop).toInt(), plus[0], base + 1)!!
        val under = inkBox(pix, 0, plus[3] + 2, W, base + 1)!!
        val x = Tok.vol_numberX
        val room = x + v.numberRoom64() / 64f
        assertTrue("number ink from ${rows[0]} where numberX $x", rows[0] >= floor(x).toInt() && rows[0] <= ceil(x).toInt())
        assertTrue("number ink under the plus to ${under[2]} past the room $room", under[2] <= ceil(room).toInt())
    }

    @Test
    fun aDownDraggedToTwoHundredSixtyTiltsThePlateUnderTiltMatrixAndLeavesTheExitAndTextFlat() {
        val still = shown(true)
        val h = shown(true)
        h.down(1_000L, Tok.canvasW / 2, TokBoard.v1.vol_plusY)
        h.move(1_000L, 200f, 60f, 50, VolumeHud.Pos())
        assertEquals(1f, h.tiltK(1_000L), 0f)
        val ink = ink(h)
        val flatPix = pixels(still, ink, 1_000L)
        val tiltPix = pixels(h, ink, 1_000L)
        val near = v.partBox(VolumePart.Minus, ink)!!
        val flat = inkBox(flatPix, near[0] - 2, near[1] - 2, near[2] + 2, near[3] + 2)!!
        val want = screenBox(listOf(Offset(flat[0].toFloat(), flat[1].toFloat()), Offset(flat[2].toFloat(), flat[1].toFloat()), Offset(flat[0].toFloat(), flat[3].toFloat()), Offset(flat[2].toFloat(), flat[3].toFloat())), v.plate(h, 1f))
        assertTrue("the plate moves the minus right edge from ${flat[2]} to ${want.right}", abs(want.right - flat[2]) > 2f)
        val got = inkBox(tiltPix, floor(want.left).toInt() - 2, floor(want.top).toInt() - 2, ceil(want.right).toInt() + 2, ceil(want.bottom).toInt() + 2)!!
        assertEquals("minus left", want.left, got[0].toFloat(), 2f)
        assertEquals("minus top", want.top, got[1].toFloat(), 2f)
        assertEquals("minus right", want.right, got[2].toFloat(), 2f)
        assertEquals("minus bottom", want.bottom, got[3].toFloat(), 2f)
        assertArrayEquals("the exit stays flat", inkBox(flatPix, 0, 0, W / 4, H / 10), inkBox(tiltPix, 0, 0, W / 4, H / 10))
        val tb = v.titleBase()
        val band = tb + floor(ink.title.inkTop).toInt()
        assertArrayEquals("the title stays flat", inkBox(flatPix, 0, band, W, tb + 1), inkBox(tiltPix, 0, band, W, tb + 1))
    }

    @Test
    fun aTiltedPartRecordsItsScreenBoxAndTheExitAndLinesRecordAsText() {
        val h = shown(true)
        h.down(1_000L, Tok.canvasW / 2, TokBoard.v1.vol_plusY)
        h.move(1_000L, 200f, 60f, 50, VolumeHud.Pos())
        val ink = ink(h)
        val dev = Dev("240x320", "funeOS V1 board", W, H, listOf(Nav.None))
        val regions = Regions("v1", W, H, scale(dev.window(Nav.None), Nav.None, Surface.NowPlaying, false))
        pixels(h, ink, 1_000L, regions)
        fun named(n: String) = regions.regions.single { it.name == n }
        val minus = named("volume.minus")
        val want = screenBox(v.partPoints(VolumePart.Minus, ink)!!, v.plate(h, 1f))
        assertEquals(Kind.Glyph, minus.kind)
        assertEquals("minus x0", want.left, minus.box.x0, 1e-3f)
        assertEquals("minus y0", want.top, minus.box.y0, 1e-3f)
        assertEquals("minus x1", want.right, minus.box.x1, 1e-3f)
        assertEquals("minus y1", want.bottom, minus.box.y1, 1e-3f)
        assertEquals(Kind.Ring, named("volume.ring").kind)
        assertEquals(Kind.Glow, named("volume.glowplus").kind)
        assertNotNull("the scrim is excluded", named("volume.scrim").exclude)
        assertNotNull("the plus under its press glow is excluded", named("volume.plus").exclude)
        assertNull("the ring with no press is gated", named("volume.ring").exclude)
        for (n in listOf("volume.exit", "volume.artist", "volume.title", "volume.label", "volume.number")) assertEquals(n, Kind.Text, named(n).kind)
    }

    @Test
    fun aTitleWhoseAccentsReachTheArtistMovesDownUntilNoColumnHoldsBothLines() {
        val a105 = VolumeParts(TokBoard.a105, 3f)
        val h = VolumeHud(TokBoard.a105).apply { show(0L, 50, true, "QUEQUE", "ÅÉÎÅÉÎ") }
        val ink = TextMeasurer(fonts, unit, LayoutDirection.Ltr).volumeInk(h, a105, fonts, unit, null)
        assertTrue("the title ${ink.titleBase} moves down from ${a105.titleBase()}", ink.titleBase > a105.titleBase())
        val w = 720
        val ht = 1280
        fun lines(artist: Word, title: Word): IntArray {
            val one = VolumeInk(ink.label, ink.exit, ink.number, ink.numberPx, ink.readout, artist, title, Word.none, Word.none, ink.artistBase, ink.titleBase)
            val img = ImageBitmap(w, ht)
            CanvasDrawScope().draw(unit, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), ht.toFloat())) {
                drawRect(Ink.bg, Offset.Zero, size)
                volumePlate(h, a105, one, Glow(), 1_000L)
            }
            return IntArray(w * ht).also { img.readPixels(it) }
        }
        val a = lines(ink.artist, Word.none)
        val t = lines(Word.none, ink.title)
        val ground = red(a[(ht - 1) * w + w - 1])
        val top = ink.artistBase + floor(ink.artist.inkTop).toInt()
        val bottom = ink.titleBase + ceil(ink.title.inkBottom).toInt()
        var shared = 0
        var inkA = 0
        var inkT = 0
        for (y in top until bottom) {
            for (x in 0 until w) {
                val ia = red(a[y * w + x]) > ground
                val ib = red(t[y * w + x]) > ground
                if (ia) inkA++
                if (ib) inkT++
                if (ia && ib) shared++
            }
        }
        assertTrue("artist ink $inkA and title ink $inkT", inkA > 0 && inkT > 0)
        assertEquals("pixels with both lines' ink", 0, shared)
    }

    @Test
    fun thePlateTurnsAboutTheCanvasCentreAtTheEyeOfTheBoard() {
        val h = shown(true)
        h.down(1_000L, Tok.canvasW / 2, TokBoard.v1.vol_plusY)
        assertArrayEquals(tiltMatrix(h.plate(), 1.0, 120.0, 160.0, 864.0), v.plate(h, 1f), 0.1)
        val a105 = VolumeParts(TokBoard.a105, 3f)
        val g = VolumeHud(TokBoard.a105).apply { show(0L, 50, true, "artist", "title") }
        g.down(1_000L, Tok.canvasW / 2, TokBoard.a105.vol_plusY)
        assertArrayEquals(tiltMatrix(g.plate(), 1.0, 360.0, 640.0, 2592.0), a105.plate(g, 1f), 1.0)
    }

    companion object {
        private const val W = 240
        private const val H = 320
    }
}
