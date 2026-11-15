package net.seilent.funekit.kit

import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Test

class RowsTest {
    private val none = Insets(0, 0, 0, 0)

    private fun page(w: Int, h: Int, large: Boolean = false) =
        scale(Window(w, h, 28, none, none, none, none, none), Nav.None, Surface.Page, large)

    private val v1 = page(240, 320)
    private val r1 = page(480, 800)
    private val a105 = page(720, 1280)
    private val a105Large = page(720, 1280, large = true)
    private val p2160 = scale(Window(1080, 2160, 28, none, none, none, none, none, Dpi(403.411f, 403.411f, 440, 1080, 2160, 1, 1080, 2160)), Nav.None, Surface.Page, false)

    private fun edges(e: Edges) = listOf(e.x0, e.y0, e.x1, e.y1)

    @Test
    fun aFocusedDividerTileTakesATwoUnitBorderAtFullAlpha() {
        fun tile(s: Scale, want: List<Int>, idle: Int, focused: Int) {
            val t = Rows.divider(s, 0, collection = false, label = 0f)
            assertEquals(want, edges(t))
            assertEquals(idle, Rows.dividerBorder(s, false))
            assertEquals(focused, Rows.dividerBorder(s, true))
            assertEquals(listOf(t.x0, t.y0, t.x1, t.y0 + focused), edges(Rows.letterBorder(t, Rows.dividerBorder(s, true))[0]))
            assertEquals(listOf(t.x1 - focused, t.y0 + focused, t.x1, t.y1 - focused), edges(Rows.letterBorder(t, Rows.dividerBorder(s, true))[3]))
        }
        tile(a105, listOf(60, 6, 168, 114), 3, 6)
        tile(r1, listOf(40, 4, 112, 76), 2, 4)
        tile(v1, listOf(20, 2, 56, 38), 1, 2)
        assertEquals(0.4f, Rows.dividerAlpha(false), 0f)
        assertEquals(1f, Rows.dividerAlpha(true), 0f)
    }

    @Test
    fun aLabelWiderThanTheTileWidensItByTheLabelAndBothInsets() {
        assertEquals(listOf(60, 6, 168, 114), edges(Rows.divider(a105, 0, collection = false, label = 72f)))
        val wide = Rows.divider(a105, 0, collection = false, label = 80.3f)
        assertEquals(listOf(60, 6, 177, 114), edges(wide))
        assertEquals(159, Rows.letterRight(wide, a105))
        assertEquals(listOf(20, 2, 70, 38), edges(Rows.divider(v1, 0, collection = false, label = 38.000004f)))
        assertEquals(listOf(20, 2, 71, 38), edges(Rows.divider(v1, 0, collection = false, label = 38.02f)))
        assertEquals(listOf(40, 4, 112, 76), edges(Rows.divider(r1, 0, collection = false, label = 48f)))
        assertEquals(listOf(40, 4, 113, 76), edges(Rows.divider(r1, 0, collection = false, label = 48.5f)))
        assertEquals(listOf(60, 165, 198, 303), edges(Rows.divider(a105, 165, collection = true, label = 102f)))
        assertEquals(listOf(60, 165, 199, 303), edges(Rows.divider(a105, 165, collection = true, label = 102.2f)))
        assertEquals(listOf(60, 165, 198, 303), edges(Rows.divider(a105, 165, collection = false, label = 0f, song = true)))
    }

    @Test
    fun pressFrameRunsFromOnePointFourOutsideToFourPointEightInside() {
        val t = Edges(300, 300, 450, 450)
        fun frame(s: Scale, outside: Int, inside: Int) {
            val f = Rows.pressFrame(t, s)
            assertEquals(listOf(300 - outside, 300 - outside, 450 + outside, 300 + inside), edges(f[0]))
            assertEquals(listOf(300 - outside, 450 - inside, 450 + outside, 450 + outside), edges(f[1]))
            assertEquals(listOf(300 - outside, 300 + inside, 300 + inside, 450 - inside), edges(f[2]))
            assertEquals(listOf(450 - inside, 300 + inside, 450 + outside, 450 - inside), edges(f[3]))
        }
        frame(v1, 1, 5)
        frame(r1, 3, 10)
        frame(a105, 4, 14)
        frame(a105Large, 4, 14)
    }

    @Test
    fun dividerTileAndLetterOnTheA105() {
        val t = Rows.divider(a105, 0, collection = false, label = 0f)
        assertEquals(listOf(60, 6, 168, 114), edges(t))
        assertEquals(150, Rows.letterRight(t, a105))
        assertEquals(90, Rows.letterBaseline(t, a105))
        val c = Rows.divider(a105, 165, collection = true, label = 0f)
        assertEquals(listOf(60, 165, 198, 303), edges(c))
    }

    @Test
    fun letterTileBorderBandsSitInsideTheTile() {
        val t = Edges(60, 6, 168, 114)
        val b = Rows.letterBorder(t, 3)
        assertEquals(listOf(60, 6, 168, 9), edges(b[0]))
        assertEquals(listOf(60, 111, 168, 114), edges(b[1]))
        assertEquals(listOf(60, 9, 63, 111), edges(b[2]))
        assertEquals(listOf(165, 9, 168, 111), edges(b[3]))
    }

    @Test
    fun rowRingsOnTheA105() {
        val song = Rows.ring(a105, 0, RowModel(ring = true), focused = false)
        assertEquals(listOf(90, 60, 60, 9), listOf(song.cx, song.cy, song.d, song.stroke))
        assertEquals(0.6f, song.alpha, 0f)
        val focused = Rows.ring(a105, 0, RowModel(ring = true), focused = true)
        assertEquals(12, focused.stroke)
        assertEquals(1f, focused.alpha, 0f)
        val toggled = Rows.ring(a105, 0, RowModel(ring = true, ringToggle = true, ringOn = true), focused = false)
        assertEquals(1f, toggled.alpha, 0f)
        assertEquals(9, toggled.stroke)
        val off = Rows.ring(a105, 0, RowModel(ring = true, ringToggle = true, ringOn = false), focused = true)
        assertEquals(0.6f, off.alpha, 0f)
        val collection = Rows.ring(a105, 0, RowModel(ring = true, collection = true), focused = false)
        assertEquals(listOf(103, 43, 87), listOf(collection.cx, collection.cy, collection.d))
    }

    @Test
    fun numberColumnKeepsTheSongIndentUntilTheDigitsOutgrowIt() {
        val digit64 = 7 * 3 * 64
        val indent64 = 24 * 3 * 64
        assertEquals(indent64, Layout.numberColumn64(a105, 2, digit64))
        assertEquals(indent64, Layout.numberColumn64(a105, 4, digit64))
        assertEquals(5 * digit64 + indent64 - 4 * digit64, Layout.numberColumn64(a105, 5, digit64))
        val lines = Layout.row(a105, 0, collection = false, bold = false, countLine = false, twoLine = false, ring = false, number64 = indent64)
        assertEquals(20 * 3 * 64 + indent64, lines.titleX64)
        assertEquals(20 * 3 + 24 * 3, lines.titleX)
        val ring = Layout.row(a105, 0, collection = false, bold = false, countLine = false, twoLine = false, ring = true, number64 = indent64)
        assertEquals((20 + 24) * 3 * 64, ring.titleX64)
    }

    @Test
    fun trailingTextEndsAtTheRightHubEdge() {
        assertEquals(660, Rows.right(a105))
        assertEquals(440, Rows.right(r1))
        assertEquals(660 * 64 - 2000, Rows.trailingX64(a105, 2000))
        assertEquals(648, Rows.right(a105Large))
    }

    @Test
    fun primaryAndSecondaryRolesFollowTheRowForm() {
        assertEquals(Roles.listCollection, Rows.primaryRole(RowModel(collection = true, bold = true)))
        assertEquals(Roles.listPlaylist, Rows.primaryRole(RowModel(bold = true)))
        assertEquals(Roles.listPrimary, Rows.primaryRole(RowModel()))
        assertEquals(Roles.listCount, Rows.secondaryRole(RowModel(countLine = true)))
        assertEquals(Roles.listCount, Rows.secondaryRole(RowModel(bold = true)))
        assertEquals(Roles.listSecondary, Rows.secondaryRole(RowModel()))
    }

    @Test
    fun onTheP2160PageTitlesAndTrailingTextEndAtTheWindowLessTheHubEdge() {
        assertEquals(364.04f, p2160.w, 0.005f)
        assertEquals(1021, Rows.right(p2160))
        assertEquals(1021 * 64 - 2000, Rows.trailingX64(p2160, 2000))
    }

    @Test
    fun aSongRowTakesNoNumberColumnAndItsOwnRoles() {
        val r = RowModel(primary = "Slow Comet", secondary = "Amber", number = "12", song = true)
        assertEquals(Roles.listSong, Rows.primaryRole(r))
        assertEquals(Roles.listSongSub, Rows.secondaryRole(r))
        assertEquals(Roles.listCollectionCount, Rows.secondaryRole(RowModel(collection = true)))
        assertEquals(160, Rows.pitch(a105, r))
        val lines = Layout.row(a105, 0, collection = false, bold = false, countLine = false, twoLine = true, ring = false, number64 = 7 * 3 * 64, song = true)
        assertEquals(60, lines.titleX)
    }

    @Test
    fun theThumbStripStartsAtTheIndentPastTheCollectionName() {
        assertEquals(180, Layout.thumbsX(a105, true))
        assertEquals(78, Layout.thumbsTop(a105, 0))
        assertEquals(Layout.collectionBase(a105, 165) + 18, Layout.thumbsTop(a105, 165))
    }
}
