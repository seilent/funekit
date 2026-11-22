package net.seilent.funekit.screens.eq

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.SettingMeasure
import net.seilent.funekit.kit.penFloor
import net.seilent.funekit.kit.titleLines
import net.seilent.funekit.kit.wideLine
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
class EqRowInkTest {
    private val density = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, density, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)
    private val none = Insets(0, 0, 0, 0)

    private class Inked(val m: EqMeasure, val inks: List<EqRowInk>) {
        val r: SettingMeasure get() = m.rows
        val gap: Int get() = px(Tok.eqValueGap, m.s.ppu)
    }

    private fun inked(rows: List<EqRow>, bands: List<EqBand> = emptyList(), large: Boolean = false, accentOff: Boolean = false): Inked {
        val s = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, large)
        val pane = EqRowsPane(EQ, StillRows(listOf(EqPivot(HP, HEADPHONES, rows, bands = bands))))
        val d = eqDraw(EqModel(1L, EqId(EQ)), s, measurer, fonts, density, covers, Accent.color(Accent.defaultHue, accentOff), accentOff) { pane }
        return Inked(d.measure, pane.pivots.value.single().rows.map { d.ink(it) as EqRowInk })
    }

    private fun peak(value: String, accentOff: Boolean = false): EqRowInk = inked(listOf(EqRow(EqKind.Peak, value)), accentOff = accentOff).inks.single()

    private fun collides(k: Inked, title: String, value: String, bold: Boolean): Boolean =
        penFloor(k.r.left64 + k.r.title64(title)) + k.gap > penFloor(k.r.right64 - k.r.value64(value, bold))

    private fun values(): Sequence<String> = (1..WIDE).asSequence().flatMap { a -> (0..DOTS).asSequence().map { b -> "8".repeat(a) + ".".repeat(b) } }

    @Test
    fun aValueThatMeetsTheWholeTitleSplitsItAtASpace() {
        val k = inked(listOf(EqRow(EqKind.Peak, "0")))
        val v = values().first { v ->
            val tl = k.r.titleLines(PEAK, listOf(v), false)
            collides(k, PEAK, v, false) && tl.size == 2 && !collides(k, k.r.wideLine(tl), v, false)
        }
        val ink = peak(v)
        val tl = k.r.titleLines(PEAK, listOf(v), false)
        assertEquals("the title splits", tl, ink.title.words.map { it.text.lowercase() })
        assertEquals("two title lines settingsTitlePitch apart", px(Tok.settingsTitlePitch, k.m.s.ppu), ink.title.base[1] - ink.title.base[0])
        assertEquals("the value slot starts at the wider line", k.r.left64 + k.r.title64(k.r.wideLine(tl)), ink.title.wide64)
        assertEquals("plus eqValueGap", k.gap, ink.gap)
    }

    @Test
    fun aTitleWhoseWiderLineStillMeetsTheValueStaysWholeAndClipsTheValue() {
        val k = inked(listOf(EqRow(EqKind.Peak, "0")))
        val v = values().first { v ->
            val tl = k.r.titleLines(PEAK, listOf(v), false)
            collides(k, PEAK, v, false) && (tl.size < 2 || collides(k, k.r.wideLine(tl), v, false))
        }
        val ink = peak(v)
        assertEquals("the title stays whole", listOf(PEAK), ink.title.words.map { it.text.lowercase() })
        val slot = penFloor(ink.title.wide64) + ink.gap
        assertEquals("the slot starts at the title's end plus eqValueGap", penFloor(k.r.left64 + k.r.title64(PEAK)) + k.gap, slot)
        assertTrue("the value runs past the slot's left, so the slot clips it", ink.right64 - k.r.value64(v, false) < slot * PEN)
    }

    @Test
    fun anEditedRowMeasuresItsValueBoldWhenTheAccentIsOff() {
        val k = inked(listOf(EqRow(EqKind.Peak, "0")), accentOff = true)
        val v = values().first { v ->
            val tl = k.r.titleLines(PEAK, listOf(v), false)
            !collides(k, PEAK, v, false) && collides(k, PEAK, v, true) && tl.size == 2 && !collides(k, k.r.wideLine(tl), v, true)
        }
        val ink = peak(v, accentOff = true)
        assertEquals("the light value leaves the title whole", 1, ink.title.words.size)
        assertEquals("the bold value splits it while the row is edited", k.r.titleLines(PEAK, listOf(v), false), ink.editTitle.words.map { it.text.lowercase() })
        val on = peak(v)
        assertSame("with the accent on the edited value keeps its weight, so the edited title is the resting one", on.title, on.editTitle)
    }

    @Test
    fun aLargeBandValueTakesTwoLinesSaverAlbumPitchApart() {
        val band = EqBand(EqBandType.LowShelf, SHELF_MILLI_HZ, -1, SHELF_GAIN, SHELF_Q)
        val k = inked(listOf(EqRow(EqKind.Band, band = 0)), listOf(band), large = true)
        val ink = k.inks.single()
        assertEquals(listOf("ls 105 hz", "+6.4 db q 0.70"), ink.value.map { it.text.lowercase() })
        assertEquals(px(Tok.saverAlbumPitch, k.m.s.ppu), ink.valueBase[1] - ink.valueBase[0])
        assertEquals("the band title is one line", 1, ink.title.words.size)
    }

    private companion object {
        const val W = 720
        const val H = 1280
        const val SDK = 35
        const val EQ = "eq"
        const val HP = "hp"
        const val HEADPHONES = "headphones"
        const val PEAK = "peak gain"
        const val WIDE = 60
        const val DOTS = 16
        const val PEN = 64
        const val SHELF_MILLI_HZ = 105000
        const val SHELF_GAIN = 64
        const val SHELF_Q = 70
    }
}
