package net.seilent.funekit.screens.music.hub

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPlaying
import net.seilent.funekit.screens.music.hub.cellAt
import net.seilent.funekit.screens.music.hub.hubChromeAt
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.sheet.HubRegions
import net.seilent.funekit.screens.music.sheet.MusicScenes
import net.seilent.funekit.sheet.Regions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HubDrawTest {
    private val sc = MusicScenes()
    private val dev = sc.a105
    private val s: Scale = sc.pageScale(dev)

    private fun hub(pivot: Int): Pair<HubModel, HubDraw> = sc.hub(s, pivot).also { (m, d) -> sc.fill(m, d) }

    private fun shot(m: HubModel, d: HubDraw, focusShown: Boolean, rec: Regions? = null): Bitmap = sc.draw(dev) {
        with(d) { content(m.pivot, 0L, focusShown, rec?.let { HubRegions(it) }, "page") { r -> m.look(m.pivot, r, 0L, focusShown) } }
    }

    private fun red(b: Bitmap, x: Int, y: Int): Int = Color.red(b.getPixel(x, y))

    @Test
    fun aFocusedDividerTakesItsTwoUnitBorderAndAHiddenFocusTheIdleOne() {
        val (m, d) = hub(HUB_ARTISTS)
        assertTrue(m.pane.map!!.isLetter(0))
        val y = TOP + COLLECTION_PITCH / 2
        val x = HUB_EDGE + IDLE_BORDER + 1
        val shown = shot(m, d, true)
        assertTrue(red(shown, x, y) >= WHITE)
        val hidden = shot(m, d, false)
        assertTrue(red(hidden, x, y) < DIM)
        assertTrue(red(hidden, HUB_EDGE, y) > red(hidden, x, y))
    }

    @Test
    fun theGridFocusBorderSitsOutsideTheTileAndTheFocusedCaptionIsWhole() {
        val (m, d) = hub(HUB_ALBUMS)
        val g = d.geo.grid
        assertTrue(m.pane.cell(0, 1) >= 0)
        m.pane.setFocus(0, 1, 0L, false)
        val y = TOP + g.tile / 2
        val x = g.x(1) - g.border / 2
        val focused = shot(m, d, true)
        assertTrue(red(focused, x, y) >= WHITE)
        val cap = TOP + g.tile + 1 until TOP + g.tile + g.drop + 1
        val title = (g.x(1) until g.x(1) + g.tile).maxOf { cx -> cap.maxOf { cy -> red(focused, cx, cy) } }
        val other = (g.x(2) until g.x(2) + g.tile).maxOf { cx -> cap.maxOf { cy -> red(focused, cx, cy) } }
        assertTrue(title >= WHITE)
        assertTrue(other < CAPTION)
        m.pane.setFocus(0, 0, 0L, false)
        assertTrue(red(shot(m, d, true), x, y) < DIM)
    }

    @Test
    fun anAlbumHiddenUnderTheFlyingTileDropsItsArtAndKeepsItsCaption() {
        val (m, d) = hub(HUB_ALBUMS)
        val g = d.geo.grid
        val id = requireNotNull(m.pane.row(m.pane.cell(0, 1))).id
        val t = g.tileAt(1, TOP)
        val cap = TOP + g.tile + 1 until TOP + g.tile + g.drop + 1
        fun caption(b: Bitmap) = (g.x(1) until g.x(1) + g.tile).maxOf { cx -> cap.maxOf { cy -> red(b, cx, cy) } }
        val shown = shot(m, d, false)
        assertTrue("premise: the tile is drawn", red(shown, (t.x0 + t.x1) / 2, (t.y0 + t.y1) / 2) > GROUND)
        m.hidden = id
        val hid = shot(m, d, false)
        assertTrue("the hidden tile's art is gone", red(hid, (t.x0 + t.x1) / 2, (t.y0 + t.y1) / 2) <= GROUND)
        assertTrue("the hidden tile keeps its caption", caption(hid) > GROUND)
        assertEquals(caption(shown), caption(hid))
    }

    @Test
    fun aPressedAlbumTileKeepsItsTileBoxAtScaleOne() {
        val (m, d) = hub(HUB_ALBUMS)
        val g = d.geo.grid
        val pn = m.pane
        val k = (0 until pn.map!!.cells(ART_ROW)).first { c -> m.cellAt(ART_ROW, c)?.ref?.let { m.art[ArtKey.Cover(it, g.tile)] } != null }
        pn.press.down(pn.item(ART_ROW, k), 0L)
        val b = sc.draw(dev) { with(d) { content(HUB_ALBUMS, PRESSED_AT, true, null, "page") { r -> m.look(HUB_ALBUMS, r, PRESSED_AT, true) } } }
        assertTrue("premise: the cell is framed", m.look(HUB_ALBUMS, ART_ROW, PRESSED_AT, true).rings[k] > 0f)
        val t = g.tileAt(k, TOP + ART_ROW * g.pitch)
        assertTrue("the tile corner is drawn, not the ground", red(b, t.x0, t.y0) > GROUND)
        assertTrue("the tile's last column is drawn, not the ground", red(b, t.x1 - 1, t.y1 - 1) > GROUND)
    }

    @Test
    fun theThumbStripStopsBeforeTheWidthLessEdge() {
        val (m, d) = hub(HUB_ARTISTS)
        val pn = m.pane
        val id = pn.row(pn.cell(1, 0))!!.id
        val ref = pn.thumbs[id]!!.filterNotNull().first { m.art[ArtKey.Cover(it, d.geo.thumb)] != null }
        pn.thumbs[id] = List(MANY) { ref }
        val rec = sc.regions(dev, s)
        shot(m, d, true, rec)
        val strip = rec.regions.filter { it.name.startsWith("page.row1.thumb") }
        assertEquals(THUMBS, strip.size)
        assertEquals(TITLE_X.toFloat(), strip.first().box.x0)
        assertEquals(THUMB_TOP.toFloat(), strip.first().box.y0)
        assertTrue(strip.all { it.box.x1 <= W_LESS_EDGE })
        assertEquals((TITLE_X + (THUMBS - 1) * (THUMB + THUMB_GAP) + THUMB).toFloat(), strip.last().box.x1)
    }

    @Test
    fun aPlayingSongDrawsItsTitleInTheAccent() {
        val (m, d) = hub(HUB_SONGS)
        val pn = m.pane
        m.playing = HubPlaying(pn.row(pn.cell(PICKED, 0))!!.id)
        val b = shot(m, d, true)
        fun tinted(r: Int) = (SONG_TOPS[r] until SONG_TOPS[r + 1]).any { y ->
            (0 until dev.w).any { x ->
                val c = b.getPixel(x, y)
                abs(Color.red(c) - Color.blue(c)) > TINT || abs(Color.green(c) - Color.blue(c)) > TINT
            }
        }
        assertTrue(tinted(PICKED))
        assertFalse(tinted(PICKED + 1))
    }

    @Test
    fun theSongsRowsTakeTheirTopsFromThe16Dot16Pitch() {
        val (m, _) = hub(HUB_SONGS)
        val t = m.pane.track
        assertFalse(t.whole)
        assertEquals(SONG_TOPS, SONG_TOPS.indices.map { TOP + t.topOf(it) })
    }

    @Test
    fun aDialogHidesTheFocusAndALibrarySheetKeepsIt() {
        val (m, _) = hub(HUB_ARTISTS)
        val st = PageStack<String>(MoveForms(), { PageKind.Hub }) { MoveEnds() }.also { it.pages.add("hub") }
        st.open(Overlay(OverKind.Dlg))
        st.frame(0L)
        assertTrue(st.hideFocus)
        assertFalse(m.look(HUB_ARTISTS, 0, 0L, !st.hideFocus).focused)
        val sheet = PageStack<String>(MoveForms(), { PageKind.Hub }) { MoveEnds() }.also { it.pages.add("hub") }
        sheet.open(Overlay(OverKind.Sheet))
        sheet.frame(0L)
        assertFalse(sheet.hideFocus)
        assertTrue(m.look(HUB_ARTISTS, 0, 0L, !sheet.hideFocus).focused)
    }

    @Test
    fun atRestEveryRowKeepsAnEqualLookAndDataAcrossFrames() {
        for (pivot in listOf(HUB_ALBUMS, HUB_ARTISTS, HUB_SONGS)) {
            val (m, d) = hub(pivot)
            val rows = 0 until ROWS
            val looks = rows.map { r -> m.look(pivot, r, 0L, true) }
            val data = rows.map { r -> d.data(pivot, r) }
            for (t in 1L..FRAMES) {
                val now = t * FRAME_MS
                assertEquals(looks, rows.map { r -> m.look(pivot, r, now, true) })
                assertEquals(data, rows.map { r -> d.data(pivot, r) })
            }
        }
    }

    @Test
    fun anArrivingTileChangesOnlyTheDataOfItsRow() {
        val (m, d) = hub(HUB_ALBUMS)
        val rows = 0 until ROWS
        val ref = (0 until m.pane.map!!.cells(ART_ROW)).firstNotNullOf { m.cellAt(ART_ROW, it)?.ref }
        m.art.remove(ArtKey.Cover(ref, d.geo.grid.tile))
        val before = rows.map { r -> d.data(HUB_ALBUMS, r) }
        m.art[ArtKey.Cover(ref, d.geo.grid.tile)] = ImageBitmap(d.geo.grid.tile, d.geo.grid.tile)
        val after = rows.map { r -> d.data(HUB_ALBUMS, r) }
        assertEquals(listOf(ART_ROW), rows.filter { before[it] != after[it] })
    }

    @Test
    fun aFocusStepChangesOnlyTheLooksOfTheRowsItLeavesAndReaches() {
        val (m, _) = hub(HUB_SONGS)
        val rows = 0 until ROWS
        m.pane.setFocus(1, 0, 0L, false)
        val before = rows.map { r -> m.look(HUB_SONGS, r, 0L, true) }
        m.pane.setFocus(2, 0, 0L, false)
        val after = rows.map { r -> m.look(HUB_SONGS, r, 0L, true) }
        assertEquals(listOf(1, 2), rows.filter { before[it] != after[it] })
    }

    @Test
    fun theChromeTimeMovesOnlyWhileThePivotStripMoves() {
        val c = chromeOf(sc.measurer, sc.fonts, s, sc.density, "music", MusicPage.Hub.pivots, null)
        assertEquals(0L, hubChromeAt(c, FRAME_MS))
        assertEquals(0L, hubChromeAt(c, FRAME_MS * 2))
        c.switchTo(1, SWITCH_AT, 1)
        val strip = Tok.motion_pivotStrip.toLong()
        assertEquals(SWITCH_AT + FRAME_MS, hubChromeAt(c, SWITCH_AT + FRAME_MS))
        val rest = SWITCH_AT + strip
        for (t in listOf(rest, rest + FRAME_MS, rest + strip)) assertEquals(rest, hubChromeAt(c, t))
    }

    companion object {
        private const val ROWS = 8
        private const val FRAMES = 100L
        private const val FRAME_MS = 16L
        private const val ART_ROW = 2
        private const val PRESSED_AT = 100L
        private const val GROUND = 17
        private const val SWITCH_AT = 5000L
        private const val TOP = 360
        private const val HUB_EDGE = 60
        private const val IDLE_BORDER = 3
        private const val COLLECTION_PITCH = 165
        private val SONG_TOPS = listOf(360, 520, 679, 839, 998, 1158)
        private const val PICKED = 3
        private const val WHITE = 250
        private const val DIM = 120
        private const val CAPTION = 220
        private const val TINT = 40
        private const val MANY = 12
        private const val THUMBS = 10
        private const val TITLE_X = 180
        private const val THUMB_TOP = 603
        private const val THUMB = 38
        private const val THUMB_GAP = 12
        private const val W_LESS_EDGE = 672f
    }
}
