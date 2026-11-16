package net.seilent.funekit.kit

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class RowWordTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val unit = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unit, LayoutDirection.Ltr)
    private val artist = RowModel(primary = "Aster", secondary = "3 albums", collection = true, ring = true)

    private fun ink(r: RowModel) = rowInk(measurer, fonts, r, a105, unit, false, null)

    private fun track(q: Int = 0) = RowTrack(PITCH, VIEW).also {
        it.count(ROWS, 0)
        it.at(q)
    }

    private fun word(t: RowTrack, row: Int, r: RowModel = artist, i: RowInk? = ink(r), grid: Boolean = false) =
        listRowWord(t, TOP, BOTTOM, row, grid, r, i, a105)

    private fun wide(i: RowInk, width: Float): RowInk {
        val p = i.primary
        return RowInk(
            i.r, Word(p.text, p.runs, width, p.inkLeft, p.inkRight, p.inkTop, p.inkBottom), i.secondary, i.album, i.albumX64, i.number, i.trailing,
            i.lines, i.right, i.trailingX64, i.primaryRight, i.spot, i.spotFocused, i.glowText, i.glowClip,
        )
    }

    @Test
    fun aRowWhollyInsideTheListGivesItsWordAndOneCutByAnEdgeNone() {
        assertEquals(165, Rows.pitch(a105, true))
        val t = track()
        for (row in 0..4) assertNotNull("row $row", word(t, row))
        assertNull("row 5 ends at 1350", word(t, 5))
        val w = requireNotNull(word(t, 2))
        val i = ink(artist)
        assertEquals("Aster", w.text)
        assertEquals(i.lines.titleX64, w.x64)
        assertEquals(TOP + 2 * PITCH + i.lines.title, w.base)
        assertEquals(Roles.listCollection.alpha, w.look, 0f)
        val up = track(1)
        assertNull("row 0 starts at 359", word(up, 0))
        assertNotNull(word(up, 1))
        assertNull(word(t, -1))
        assertNull(word(t, ROWS))
    }

    @Test
    fun aDividerAnAccentRowAnEmptyTitleAGridAndNoModelGiveNone() {
        val t = track()
        assertNull(word(t, 1, RowModel(kind = RowKind.Divider, primary = "a")))
        assertNull(word(t, 1, artist.copy(accent = true)))
        assertNull(word(t, 1, artist.copy(primary = "")))
        assertNull(word(t, 1, grid = true))
        assertNull(listRowWord(t, TOP, BOTTOM, 1, false, null, ink(artist), a105))
        assertNull(listRowWord(t, TOP, BOTTOM, 1, false, artist, null, a105))
    }

    @Test
    fun aTitleEndingExactlyAtTheRightEdgeGivesItsWordAndOneSixtyFourthLongerNone() {
        val t = track()
        val right = px(a105.w - Tok.hubEdge, a105.ppu)
        assertEquals(660, right)
        val long = artist.copy(primary = "Aster and the Long Evening Orchestra of the Northern Coast")
        val li = ink(long)
        assertTrue("the long title runs past the edge", li.lines.titleX64 + pen64(li.primary.width) > right * PEN)
        val edge = (right * PEN - li.lines.titleX64).toFloat() / PEN
        val at = wide(li, edge)
        assertEquals(right * PEN, at.lines.titleX64 + pen64(at.primary.width))
        assertNotNull(word(t, 1, long, at))
        assertNull(word(t, 1, long, wide(li, edge + 1f / PEN)))
    }

    @Test
    fun aTrailingCountMovesTheRightEdgeToTheCountLessTheGutter() {
        val t = track()
        val counted = artist.copy(trailing = "12")
        val ci = ink(counted)
        val w = pen64(ci.trailing.width)
        assertTrue(w > 0)
        assertEquals(36, px(Tok.gutter, a105.ppu))
        val right = ((660 * PEN - w) shr SHIFT) - 36
        val edge = (right * PEN - ci.lines.titleX64).toFloat() / PEN
        assertNotNull(word(t, 1, counted, wide(ci, edge)))
        assertNull(word(t, 1, counted, wide(ci, edge + 1f / PEN)))
        assertNotNull(word(t, 1, artist, wide(ink(artist), edge + 1f / PEN)))
    }

    private companion object {
        const val TOP = 360
        const val BOTTOM = 1280
        const val PITCH = 165
        const val VIEW = 920
        const val ROWS = 20
        const val PEN = 64
        const val SHIFT = 6
    }
}
