package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.FuneTheme
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.core.Section
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.RowKind
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberChrome
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_GENRES
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubAct
import net.seilent.funekit.screens.music.hub.HubGeo
import net.seilent.funekit.screens.music.hub.HubGrid
import net.seilent.funekit.screens.music.hub.HubLayer
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.hubDraw
import net.seilent.funekit.screens.music.hub.hubPickAtDrop
import net.seilent.funekit.screens.music.hub.hubPicked
import net.seilent.funekit.screens.music.hub.hubSwitch
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.hub.rowModel
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HubTouchTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(V1_W, V1_H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val r1 = scale(Window(R1_W, R1_H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val r1Large = scale(Window(R1_W, R1_H, SDK, none, none, none, none, none), Nav.None, Surface.Page, true)
    private val a105 = scale(Window(A105_W, A105_H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val fake = testFake(FakeSet.Demo)
    private val music = MusicSource(fake.library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)

    private lateinit var chrome: Chrome

    private fun hub(
        pivot: Int,
        src: MusicSource = music,
        now: () -> Long = { 0L },
        live: State<Boolean> = mutableStateOf(true),
        onJump: (Int) -> Unit = {},
        onPlay: ((HubAct.Play) -> Boolean)? = null,
        onAlbum: (Row, Int) -> Unit = { _, _ -> },
        onArtist: (Row, Int, Long) -> Unit = { _, _, _ -> },
        sc: Scale = s,
        scaleOf: State<Scale> = mutableStateOf(sc),
    ): HubModel {
        val m = HubModel(0L).also { it.select(pivot) }
        rule.setContent {
            val sc = scaleOf.value
            FuneTheme(sc) {
                val a = remember(sc) { Arbiter(sc) }
                val chrome = rememberChrome(sc, "music", MusicPage.Hub.pivots).also { this@HubTouchTest.chrome = it }
                val tm = rememberTextMeasurer()
                val fonts = LocalFontFamilyResolver.current
                val density = LocalDensity.current
                val covers = rememberCovers()
                val draw = remember(m, sc) { hubDraw(m, sc, tm, fonts, density, covers, Ink.white, true) }
                Box(Modifier.pxSize(sc.widthPx, sc.heightPx).arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                    HubLayer(
                        m, draw, chrome, src, a, groundBands(sc.heightPx, true), now,
                        live = live.value, visible = true, frozen = false, hideFocus = false, snap = 0,
                        onBack = {}, onJump = onJump, onPlay = onPlay, onAlbum = onAlbum, onArtist = onArtist,
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTrue((m.pane.map?.count ?: 0) > 0)
        return m
    }

    @Test
    fun aTapOnADividerOpensTheJumpGridOnItsLetter() {
        var jumped = -1
        hub(HUB_SONGS, onJump = { jumped = it })
        val first = runBlocking { fake.library.view().sections(Listing.Songs) }.first().toInt()
        rule.onRoot().performTouchInput {
            down(Offset(TAP_X, TOP + ROW / 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals(first, jumped)
    }

    @Test
    fun aRingTapAsksThePlayOpenOfTheArtistsSongs() {
        var jumped = -1
        val asked = mutableListOf<HubAct.Play>()
        val m = hub(HUB_ARTISTS, onJump = { jumped = it }, onPlay = {
            asked += it
            true
        })
        val id = m.pane.row(m.pane.cell(1, 0))!!.id
        rule.onRoot().performTouchInput {
            down(Offset(RING_X, TOP + COLLECTION + COLLECTION / 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(HubAct.Play(Listing.ArtistTracks(id), 0)), asked)
        assertEquals(HUB_ARTISTS, m.pivot)
        assertEquals(1, m.pane.focus.row)
        assertEquals(-1, jumped)
    }

    @Test
    fun anArtistsRowTapReachesOnArtistWithItsRowAndItsFirstAlbum() {
        val asked = mutableListOf<Triple<Long, Int, Long>>()
        val m = hub(HUB_ARTISTS, onArtist = { r, at, album -> asked += Triple(r.id, at, album) })
        val id = m.pane.row(m.pane.cell(1, 0))!!.id
        val first = runBlocking { fake.library.view().page(Listing.ArtistAlbums(id), 0, 1).rows.first().id }
        rule.onRoot().performTouchInput {
            down(Offset(TAP_X, TOP + COLLECTION + COLLECTION / 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Triple(id, 1, first)), asked)
    }

    @Test
    fun anAlbumsCellTapReachesOnAlbumWithItsRow() {
        val asked = mutableListOf<Pair<Long, Int>>()
        val m = hub(HUB_ALBUMS, onAlbum = { r, at -> asked += r.id to at })
        val g = HubGeo(s).grid
        val id = m.pane.row(m.pane.cell(0, 1))!!.id
        rule.onRoot().performTouchInput {
            down(Offset((g.x(1) + g.tile / 2).toFloat(), TOP + CELL_Y))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(id to 0), asked)
    }

    private class Letters(val present: List<Int>, val cells: List<Int>, val count: Int)

    private fun letters(listing: Listing): Letters {
        val sections = runBlocking { fake.library.view().sections(listing) }
        val present = ArrayList<Int>()
        val cells = ArrayList<Int>()
        for (i in sections.indices) {
            if (i == 0 || sections[i] != sections[i - 1]) {
                present += sections[i].toInt()
                cells += i + cells.size
            }
        }
        return Letters(present, cells, sections.size + cells.size)
    }

    private fun scrubY(b: Int): Float {
        val height = (V1_H - TOP).toFloat()
        return TOP + (b + HALF_ROW) * height / Section.count
    }

    private fun scrubDownY(b: Int): Float {
        val height = (V1_H - TOP).toFloat()
        return if (scrubY(b) - TOP < height / 2) TOP + height * 3 / 4 else TOP + height / 4
    }

    private fun scrub(b: Int) {
        val y = scrubY(b)
        val down = scrubDownY(b)
        rule.onRoot().performTouchInput {
            down(Offset(width - EDGE_X, down))
            moveTo(Offset(width - EDGE_X, (down + y) / 2))
            moveTo(Offset(width - EDGE_X, y))
            up()
        }
        rule.waitForIdle()
    }

    private fun scrubs(pivot: Int, pitch: Int, listing: Listing) {
        val m = hub(pivot)
        val l = letters(listing)
        val view = V1_H - TOP
        val t = m.pane.track
        assertEquals(pitch, t.topOf(1))
        val max = t.topOf(l.count) - view
        for (b in 0 until Section.count) {
            scrub(b)
            val row = l.cells[Section.first(l.present.size, b) { l.present[it] }]
            val q = minOf(t.topOf(row), max)
            assertEquals("bucket $b", q, m.pane.track.q)
            assertEquals("bucket $b", row, m.pane.focus.row)
            assertEquals("bucket $b", 0, m.pane.col)
            assertTrue("bucket $b", t.topOf(row) >= q && t.topOf(row + 1) <= q + view)
        }
    }

    @Test
    @Config(qualifiers = "w480dp-h800dp-mdpi")
    fun anR1SongsTapTakesTheRowOfThe16Dot16Pitch() {
        val m = hub(HUB_SONGS, sc = r1)
        assertEquals(R1_TOP, HubGeo(r1).top)
        rule.onRoot().performTouchInput {
            down(Offset(TAP_X, R1_ROW1_LAST))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, m.pane.focus.row)
        rule.onRoot().performTouchInput {
            down(Offset(TAP_X, R1_ROW1_LAST + 1))
            up()
        }
        rule.waitForIdle()
        assertEquals(2, m.pane.focus.row)
    }

    @Test
    fun anEdgeScrubOnTheAlbumsGridFocusesTheLetterCell() {
        val m = hub(HUB_ALBUMS)
        assertEquals(GRID_ROW, HubGeo(s).pitch(HUB_ALBUMS))
        assertEquals(COLS, m.pane.map?.cols)
        val l = letters(Listing.Albums)
        val view = V1_H - TOP
        val max = (l.count + COLS - 1) / COLS * GRID_ROW - view
        val cols = HashSet<Int>()
        for (b in 0 until Section.count) {
            scrub(b)
            val cell = l.cells[Section.first(l.present.size, b) { l.present[it] }]
            val row = cell / COLS
            cols += cell % COLS
            assertEquals("bucket $b", minOf(row * GRID_ROW, max), m.pane.track.q)
            assertEquals("bucket $b", row, m.pane.focus.row)
            assertEquals("bucket $b", cell % COLS, m.pane.col)
        }
        assertTrue(cols.any { it != 0 })
    }

    @Test
    fun aJumpGridPickOnSongsWaitsForTheGridToGoThenScrollsToItsDividerOnT15() {
        val m = hub(HUB_SONGS)
        val l = letters(Listing.Songs)
        val t = m.pane.track
        val max = t.topOf(l.count) - (V1_H - TOP)
        val row = l.cells[1]
        assertTrue(t.topOf(row) > 0)
        rule.runOnIdle { hubPicked(m, l.present[1], 0L) }
        rule.waitForIdle()
        assertEquals("the pick waits for the grid to go", 0, m.pane.track.q)
        assertTrue(m.pane.focus.row != row)
        rule.runOnIdle { hubPickAtDrop(m, 0L) }
        rule.waitForIdle()
        val rest = Math.round(Tok.motion_jumpScrollRest * s.ppu)
        assertEquals("the T15 scroll ends the jump rest above the divider", (t.topOf(row) - rest).coerceIn(0, max), m.pane.track.q)
        assertEquals(row, m.pane.focus.row)
        assertEquals("the pick is used once", -1, m.jumpPick)
    }

    @Test
    fun aSwipeOnTheHubStepsThePivotAndStopsAtTheFirst() {
        val app = TestApp(s, s, music, StillLevel) { SystemClock.uptimeMillis() }
        app.compose(rule, root = true)
        rule.waitForIdle()
        val stack = app.stack
        val screen = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub))
        rule.runOnUiThread { stack.push(screen, MoveKind.T2) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        assertEquals(Phase.Rest, stack.phase)
        val m = app.models[screen.entry] as HubModel
        assertEquals(HUB_ALBUMS, m.pivot)
        rule.onRoot().performTouchInput { swipeLeft(width * FROM, width * TO, SWIPE_MS) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        assertEquals(HUB_ARTISTS, m.pivot)
        rule.onRoot().performTouchInput { swipeRight(width * TO, width * FROM, SWIPE_MS) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        assertEquals(HUB_ALBUMS, m.pivot)
        rule.onRoot().performTouchInput { swipeRight(width * TO, width * FROM, SWIPE_MS) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        assertEquals(HUB_ALBUMS, m.pivot)
        assertEquals(screen, stack.top)
    }

    @Test
    fun aFlingUnderAnOpenSheetStepsNoPivotAndOnlyItsFirstEdgeBackActs() {
        val app = TestApp(s, s, music, StillLevel) { SystemClock.uptimeMillis() }
        app.compose(rule, root = true)
        rule.waitForIdle()
        val stack = app.stack
        val screen = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub))
        rule.runOnUiThread { stack.push(screen, MoveKind.T2) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        val m = app.models[screen.entry] as HubModel
        assertEquals(HUB_ALBUMS, m.pivot)
        rule.runOnIdle { app.host.openMenu(MenuForm.Sheet, "settings", listOf("one", "two"), false, -1, 0, false, { true }) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        val sheet = requireNotNull(rule.runOnIdle { stack.overlay })
        rule.onRoot().performTouchInput { swipe(Offset(width * FROM, height / 4f), Offset(width * TO, height / 4f), SWIPE_MS) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        assertEquals(HUB_ALBUMS, m.pivot)
        assertSame(sheet, rule.runOnIdle { stack.overlay })
        assertFalse(sheet.closing)
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { swipeRight(1f, width * FROM, SWIPE_MS) }
        var n = 0
        while (!rule.runOnUiThread { sheet.closing }) {
            frames()
            assertTrue("the first edge back closes the sheet", ++n < MAX_FRAMES)
        }
        rule.onRoot().performTouchInput { swipeRight(1f, width * FROM, SWIPE_MS) }
        assertTrue("premise: the sheet still closes at the up", rule.runOnUiThread { stack.overlay === sheet && sheet.closing })
        frames()
        rule.runOnUiThread {
            assertEquals(screen, stack.top)
            assertNull(stack.turn)
        }
        settle()
        rule.runOnUiThread {
            assertNull(stack.overlay)
            assertEquals(HUB_ALBUMS, m.pivot)
            assertEquals(screen, stack.top)
        }
    }

    private fun launched(pivot: Int): HubModel {
        val app = TestApp(s, s, music, StillLevel) { SystemClock.uptimeMillis() }
        app.compose(rule, root = true)
        rule.waitForIdle()
        val stack = app.stack
        val screen = TestScreen.Music(MusicEntry(stack.entry(), MusicPage.Hub))
        rule.runOnUiThread { stack.push(screen, MoveKind.T2) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        val m = app.models[screen.entry] as HubModel
        rule.runOnUiThread { m.select(pivot) }
        rule.mainClock.advanceTimeBy(SETTLE)
        rule.waitForIdle()
        assertTrue("premise: the pane holds rows", (m.pane.map?.count ?: 0) > 0)
        return m
    }

    @Test
    fun aCutGestureClearsTheHubRowPressAndGlowByTheNextFrame() {
        val m = hub(HUB_SONGS, now = { clock() })
        val pn = m.pane
        val r = (0 until V1_ROWS).first { m.rowModel(HUB_SONGS, it)?.kind == RowKind.Item }
        rule.mainClock.autoAdvance = false
        down(TAP_X, TOP + pn.track.topOf(r) + ROW / 2f)
        rule.mainClock.advanceTimeBy(HELD_MS, ignoreFrameDuration = true)
        assertEquals("premise: the row glows", r, pn.glow.glow.key)
        rule.runOnUiThread { m.lists[HUB_SONGS].cancel() }
        rule.onRoot().performTouchInput { moveBy(Offset(0f, 1f)) }
        apply()
        frames()
        assertNull(pn.glow.glow.key)
        assertNull(pn.glow.back.key)
        assertFalse(pn.press.stale(clock()))
        up()
    }

    @Test
    fun aPivotPanLockAfterARowDownClearsTheRowPressAndGlow() {
        val m = launched(HUB_SONGS)
        val pn = m.panes[HUB_SONGS]
        val r = pn.track.rowAt(Math.round(PAN_Y) - TOP)
        assertEquals("premise: the pan starts on an item row", RowKind.Item, m.rowModel(HUB_SONGS, r)?.kind)
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(Offset(PAN_X0, PAN_Y)) }
        apply()
        assertEquals("premise: the down glows its row", r, pn.glow.glow.key)
        rule.onRoot().performTouchInput {
            moveTo(Offset(PAN_X0 - PAN_STEP, PAN_Y))
            moveTo(Offset(PAN_X0 - PAN_DRAG, PAN_Y))
        }
        apply()
        assertTrue("premise: the pan took the gesture", m.motion.dragging)
        frames()
        assertNull("the take cut the glow", pn.glow.glow.key)
        assertNull(pn.glow.back.key)
        assertFalse("the take dropped the press", pn.press.stale(clock()))
        rule.onRoot().performTouchInput {
            moveTo(Offset(PAN_X0 - PAN_BACK, PAN_Y))
            up()
        }
        apply()
        rule.mainClock.advanceTimeBy(SETTLE)
        assertEquals("premise: the pan released with no switch", HUB_SONGS, m.pivot)
    }

    @Test
    fun thePivotPanDragsByTheFingerFromTheGestureOriginAndReturnsWithNoSwipe() {
        val m = launched(HUB_ARTISTS)
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(Offset(PAN_X0, PAN_Y))
            moveTo(Offset(PAN_X0 - PAN_STEP, PAN_Y))
            moveTo(Offset(PAN_X0 - PAN_DRAG, PAN_Y))
        }
        apply()
        assertTrue("premise: the pan drags", m.motion.dragging)
        assertEquals(-PAN_DRAG, m.dragX(SystemClock.uptimeMillis(), s.ppu).toFloat(), 1f)
        rule.onRoot().performTouchInput {
            moveTo(Offset(PAN_X0 - PAN_BACK, PAN_Y))
            up()
        }
        apply()
        rule.mainClock.advanceTimeBy(SETTLE)
        assertFalse(m.motion.dragging)
        assertEquals(HUB_ARTISTS, m.pivot)
        assertEquals(0, m.dragX(SystemClock.uptimeMillis() + SETTLE, s.ppu))
    }

    @Test
    fun thePivotPanRefusesADownInAnEdgeBand() {
        val m = launched(HUB_ARTISTS)
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput {
            down(Offset(EDGE_X, PAN_Y))
            moveTo(Offset(EDGE_X + PAN_STEP, PAN_Y))
            moveTo(Offset(EDGE_X + PAN_DRAG, PAN_Y))
        }
        apply()
        assertFalse(m.motion.dragging)
        assertEquals(0, m.dragX(SystemClock.uptimeMillis(), s.ppu))
        rule.onRoot().performTouchInput { up() }
    }

    private fun shot(): Bitmap {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
        return b
    }

    private fun inked(b: Bitmap, ys: IntRange): Boolean = ys.any { y -> (0 until b.width / 2).any { x -> Color.red(b.getPixel(x, y)) > INK } }

    private fun lineTop(b: Bitmap, from: Int, run: Int): Int = (from until b.height).first { y ->
        var n = 0
        (0 until b.width / 2).any { x ->
            n = if (Color.red(b.getPixel(x, y)) >= WHITE) n + 1 else 0
            n >= run
        }
    }

    @Test
    @Config(qualifiers = "w720dp-h1280dp-mdpi")
    fun anA105SongsDividerFiveRowsDownSitsOnThe16Dot16TopsOfTheLiveList() {
        val m = hub(HUB_SONGS, sc = a105)
        val pn = m.pane
        fun divider(r: Int) = m.rowModel(HUB_SONGS, r)?.kind == RowKind.Divider
        val k = (A105_ROWS until pn.map!!.count).first { r -> divider(r) && (r - A105_ROWS until r).none(::divider) }
        fun placed(row: Int): Int {
            rule.runOnUiThread {
                m.lists[HUB_SONGS].toRow(row)
                pn.setFocus(k, 0, 0L, false)
                m.poke()
            }
            rule.waitForIdle()
            return lineTop(shot(), A105_TOP, (Tok.dividerTile * a105.ppu / 2).toInt())
        }
        val atTop = placed(k)
        val down = placed(k - A105_ROWS)
        assertEquals(Math.round(k * A105_SONG_PX) - Math.round((k - A105_ROWS) * A105_SONG_PX), down - atTop)
    }

    @Test
    @Config(qualifiers = "w480dp-h800dp-mdpi")
    fun anR1ListAfterAScaleChangeAndBackSyncsToItsFirstDrawnRow() {
        val scales = mutableStateOf(r1)
        val m = hub(HUB_SONGS, sc = r1, scaleOf = scales)
        rule.runOnUiThread { m.lists[HUB_SONGS].toPx(R1_Q) }
        rule.waitForIdle()
        rule.runOnUiThread { scales.value = r1Large }
        rule.waitForIdle()
        rule.runOnUiThread { scales.value = r1 }
        rule.waitForIdle()
        val t = m.pane.track
        assertEquals("premise: the first visible row", 3, t.first)
        assertEquals("premise: the first drawn row", 2, t.drawFirst)
        assertEquals(t.drawFirst, m.lists[HUB_SONGS].state.firstVisibleItemIndex)
        assertTrue("row 2 inks the clip band", inked(shot(), R1_CLIP_TOP until R1_TOP))
    }

    @Test
    @Config(qualifiers = "w480dp-h800dp-mdpi")
    fun anR1ListScrolledInsideItsClipBandDrawsTheRowAboveAndTakesNoTapThere() {
        val m = hub(HUB_SONGS, sc = r1)
        assertEquals(R1_CLIP_TOP, HubGeo(r1).clipTop)
        rule.runOnUiThread { m.lists[HUB_SONGS].toPx(R1_SCROLL) }
        rule.waitForIdle()
        assertTrue("row 0 inks y 220 to 240", inked(shot(), R1_TOP - R1_SCROLL until R1_TOP))
        val row = m.pane.focus.row
        rule.onRoot().performTouchInput {
            down(Offset(TAP_X, R1_BAND_Y))
            up()
        }
        rule.waitForIdle()
        assertEquals(-1, m.pane.down)
        assertEquals(row, m.pane.focus.row)
    }

    @Test
    fun theFocusRingOfATopRowTileShowsWholeAboveTheContentTop() {
        val m = hub(HUB_ALBUMS)
        val g = HubGrid(s)
        assertTrue(m.pane.cell(0, 1) >= 0)
        assertEquals(TOP, HubGeo(s).top)
        assertEquals(RING, g.border)
        val x = g.x(1) + g.tile / 2
        val before = shot()
        for (y in TOP - RING until TOP) assertTrue("unfocused row $y", Color.red(before.getPixel(x, y)) < DIM)
        rule.runOnUiThread {
            m.pane.setFocus(0, 1, 0L, false)
            m.poke()
        }
        rule.waitForIdle()
        val img = shot()
        for (y in TOP - RING until TOP) {
            assertTrue("ring row $y", Color.red(img.getPixel(x, y)) >= WHITE)
            assertTrue("ground row $y", Color.red(img.getPixel(g.x(2) + g.tile / 2, y)) < DIM)
        }
        assertTrue(Color.red(img.getPixel(x, TOP - RING - 1)) < DIM)
    }

    @Test
    fun aWindowLoadsWhileTheLastWindowsArtIsHeld() {
        val art = Art.get(RuntimeEnvironment.getApplication())
        for (k in art.keys()) art.remove(k)
        val gate = CompletableDeferred<Unit>()
        val held = AtomicInteger()
        val library = object : Library by fake.library {
            override fun view(generation: Long): LibraryView {
                val v = fake.library.view(generation)
                return object : LibraryView by v {
                    override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray? {
                        held.incrementAndGet()
                        gate.await()
                        return v.art(ref, maxSide)
                    }
                }
            }
        }
        val m = hub(HUB_ARTISTS, MusicSource(library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics))
        rule.waitUntil(WAIT_MS) { held.get() > 0 }
        val pn = m.pane
        val first = pn.thumbs.keys.toSet()
        assertTrue(first.isNotEmpty())
        rule.runOnUiThread { m.lists[HUB_ARTISTS].toRow(FAR) }
        rule.waitForIdle()
        val shown = (FAR until FAR + SHOWN).mapNotNull { r -> pn.row(pn.cell(r, 0))?.id }
        assertTrue(shown.isNotEmpty())
        assertTrue(shown.any { it in pn.thumbs })
        assertTrue(first.none { it in pn.thumbs })
        assertFalse(gate.isCompleted)
        gate.complete(Unit)
    }

    private fun clock(): Long = rule.mainClock.currentTime

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames() {
        apply()
        rule.mainClock.advanceTimeByFrame()
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun settle() {
        apply()
        rule.mainClock.advanceTimeBy(SETTLE, ignoreFrameDuration = true)
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun loaded(from: Int, target: Int, live: State<Boolean> = mutableStateOf(true)): HubModel {
        val m = hub(target, now = { clock() }, live = live)
        rule.runOnIdle { m.select(from) }
        rule.waitForIdle()
        assertNotNull(m.panes[target].map)
        assertNotNull(m.panes[from].map)
        return m
    }

    private fun switchNow(m: HubModel, target: Int) {
        rule.mainClock.autoAdvance = false
        val at = clock()
        rule.runOnUiThread { hubSwitch(m, chrome, target, at) }
        frames()
        val now = clock()
        val switchAt = m.switchAt
        assertNotNull(switchAt)
        assertTrue(m.switching(now))
        assertTrue(now - switchAt!! < SWITCH_END)
    }

    private fun assertLanded(m: HubModel, target: Int) = rule.runOnUiThread {
        assertNull(m.switchAt)
        assertEquals(target, chrome.active)
        assertNull(chrome.since(clock()))
    }

    private fun down(x: Float, y: Float) {
        rule.onRoot().performTouchInput { down(Offset(x, y)) }
        apply()
    }

    private fun up() {
        rule.onRoot().performTouchInput { up() }
        apply()
    }

    private fun songRow(m: HubModel): Long {
        assertEquals(RowKind.Item, m.rowModel(HUB_SONGS, SONG_ROW)?.kind)
        return m.panes[HUB_SONGS].row(m.panes[HUB_SONGS].cell(SONG_ROW, 0))!!.id
    }

    private fun assertSongPlays(id: Long) {
        val st = fake.player.state.value
        assertEquals(Listing.Songs, st.origin)
        assertEquals(id, st.track)
    }

    private fun slid(x: Float, y: Float) {
        down(x, y)
        rule.mainClock.advanceTimeBy(HELD_MS, ignoreFrameDuration = true)
        rule.onRoot().performTouchInput { moveTo(Offset(x + SLIDE_X, y)) }
        apply()
        up()
    }

    @Test
    fun aRowPressedInsideTheLastRowsFadeKeepsItFadingOnTheBackGlow() {
        val m = hub(HUB_SONGS, now = { clock() })
        val pn = m.pane
        val (a, b) = (0 until V1_ROWS).filter { m.rowModel(HUB_SONGS, it)?.kind == RowKind.Item }.take(2)
        fun y(r: Int) = TOP + pn.track.topOf(r) + ROW / 2f
        rule.mainClock.autoAdvance = false
        slid(TAP_X, y(a))
        rule.mainClock.advanceTimeByFrame()
        down(TAP_X, y(b))
        rule.runOnUiThread {
            val at = clock()
            val la = m.look(HUB_SONGS, a, at, true)
            val lb = m.look(HUB_SONGS, b, at, true)
            assertEquals(b, pn.glow.glow.key)
            assertEquals(a, pn.glow.back.key)
            assertTrue("row $a draws on the back glow", la.back && (la.glow ?: 0f) > 0f)
            assertFalse(lb.back)
            assertTrue("row $b rises from 0", (lb.glow ?: 1f) < (la.glow ?: 0f))
        }
        rule.mainClock.advanceTimeBy(HELD_MS, ignoreFrameDuration = true)
        rule.onRoot().performTouchInput { moveTo(Offset(TAP_X + SLIDE_X, y(b))) }
        apply()
        up()
        down(TAP_X, y(a))
        rule.runOnUiThread {
            assertEquals(a, pn.glow.glow.key)
            assertEquals("the front glow on row $b moves to the back", b, pn.glow.back.key)
        }
        up()
    }

    @Test
    fun aRowDownDuringASwitchLandsItAndPressesTheRow() {
        val m = loaded(HUB_ARTISTS, HUB_SONGS)
        val id = songRow(m)
        switchNow(m, HUB_SONGS)
        down(TAP_X, SONG_Y)
        assertLanded(m, HUB_SONGS)
        rule.runOnUiThread {
            assertEquals(SONG_ROW, m.pane.focus.row)
            assertEquals(SONG_ROW, m.pane.down)
        }
        up()
        settle()
        assertSongPlays(id)
    }

    @Test
    fun aDragDuringASwitchLandsItAndScrollsTheLandedPane() {
        val m = loaded(HUB_ARTISTS, HUB_SONGS)
        rule.runOnIdle { m.lists[HUB_ARTISTS].toRow(ARTISTS_ROW) }
        rule.waitForIdle()
        assertEquals(ARTISTS_ROW * COLLECTION, m.panes[HUB_ARTISTS].track.q)
        switchNow(m, HUB_SONGS)
        rule.onRoot().performTouchInput {
            down(Offset(TAP_X, TOP + DRAG_FROM))
            moveTo(Offset(TAP_X, TOP + DRAG_SLOP))
            moveTo(Offset(TAP_X, TOP + DRAG_TO))
        }
        apply()
        assertLanded(m, HUB_SONGS)
        rule.runOnUiThread {
            assertEquals(DRAGGED, m.pane.track.q)
            assertEquals(ARTISTS_ROW * COLLECTION, m.panes[HUB_ARTISTS].track.q)
        }
        up()
    }

    @Test
    fun aScrubDuringASwitchLandsItAtItsDown() {
        val m = loaded(HUB_ARTISTS, HUB_SONGS)
        val l = letters(Listing.Songs)
        val view = V1_H - TOP
        val songs = m.panes[HUB_SONGS].track
        val max = songs.topOf(l.count) - view
        val row = l.cells[Section.first(l.present.size, BUCKET) { l.present[it] }]
        switchNow(m, HUB_SONGS)
        val y = scrubY(BUCKET)
        val from = scrubDownY(BUCKET)
        rule.onRoot().performTouchInput { down(Offset(width - EDGE_X, from)) }
        apply()
        assertLanded(m, HUB_SONGS)
        rule.onRoot().performTouchInput {
            moveTo(Offset(width - EDGE_X, (from + y) / 2))
            moveTo(Offset(width - EDGE_X, y))
            up()
        }
        settle()
        assertEquals(minOf(songs.topOf(row), max), m.pane.track.q)
        assertEquals(row, m.pane.focus.row)
    }

    @Test
    fun aGridDownDuringASwitchLandsItAndPressesTheCell() {
        val m = loaded(HUB_ARTISTS, HUB_ALBUMS)
        val g = HubGrid(s)
        assertTrue(m.panes[HUB_ALBUMS].cell(0, 1) >= 0)
        switchNow(m, HUB_ALBUMS)
        down((g.x(1) + g.tile / 2).toFloat(), TOP + CELL_Y)
        assertLanded(m, HUB_ALBUMS)
        rule.runOnUiThread {
            assertEquals(0, m.pane.focus.row)
            assertEquals(1, m.pane.col)
            assertEquals(0, m.pane.down)
            assertEquals(1, m.pane.downCol)
        }
        up()
    }

    @Test
    fun anEmptyContentDownDuringASwitchLandsItAndActsOnNothing() {
        val m = loaded(HUB_ARTISTS, HUB_ALBUMS)
        val g = HubGrid(s)
        val row = m.panes[HUB_ALBUMS].focus.row
        val col = m.panes[HUB_ALBUMS].col
        val origin = fake.player.state.value.origin
        switchNow(m, HUB_ALBUMS)
        down((g.x(1) + g.tile / 2).toFloat(), TOP + GAP_Y)
        assertLanded(m, HUB_ALBUMS)
        rule.runOnUiThread {
            assertEquals(row, m.pane.focus.row)
            assertEquals(col, m.pane.col)
            assertEquals(-1, m.pane.down)
        }
        up()
        settle()
        assertEquals(origin, fake.player.state.value.origin)
    }

    @Test
    fun theFrameAfterTheLandIsTheRestFrame() {
        val m = loaded(HUB_ARTISTS, HUB_ALBUMS)
        val g = HubGrid(s)
        switchNow(m, HUB_ALBUMS)
        val during = shot()
        down((g.x(1) + g.tile / 2).toFloat(), TOP + GAP_Y)
        up()
        rule.mainClock.advanceTimeByFrame()
        val a = shot()
        apply()
        rule.mainClock.advanceTimeBy(PAST)
        val b = shot()
        assertFalse(during.sameAs(b))
        assertTrue(a.sameAs(b))
    }

    @Test
    fun aPivotTapDuringASwitchLandsAtItsDownAndSwitchesAtItsUp() {
        val m = loaded(HUB_ARTISTS, HUB_SONGS)
        switchNow(m, HUB_SONGS)
        lateinit var box: Edges
        rule.runOnUiThread { box = chrome.pivotBox(HUB_GENRES) }
        assertTrue(box.x0 >= 0 && box.y0 >= 0 && box.x1 <= V1_W && box.y1 <= V1_H)
        down((box.x0 + box.x1) / 2f, (box.y0 + box.y1) / 2f)
        rule.runOnUiThread {
            assertNull(m.switchAt)
            assertNull(chrome.since(clock()))
        }
        val at = clock()
        up()
        rule.runOnUiThread {
            assertEquals(HUB_GENRES, m.pivot)
            assertEquals(HUB_SONGS, m.switchFrom)
            assertEquals(at, m.switchAt)
            assertEquals(HUB_GENRES, chrome.active)
        }
    }

    @Test
    fun aRowTapAfterASwitchRanToItsEndPlaysTheRow() {
        val m = loaded(HUB_ARTISTS, HUB_SONGS)
        val id = songRow(m)
        switchNow(m, HUB_SONGS)
        apply()
        rule.mainClock.advanceTimeBy(PAST)
        apply()
        assertNotNull(m.switchAt)
        assertFalse(m.switching(clock()))
        down(TAP_X, SONG_Y)
        up()
        settle()
        assertSongPlays(id)
    }

    @Test
    fun aSwitchLandsAtTheFirstDownAfterTheHubTurnsLive() {
        val l = mutableStateOf(false)
        val m = loaded(HUB_ARTISTS, HUB_SONGS, l)
        rule.onRoot().performTouchInput {
            down(Offset(TAP_X, SONG_Y))
            up()
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            l.value = true
            Snapshot.sendApplyNotifications()
        }
        frames()
        switchNow(m, HUB_SONGS)
        down(TAP_X, SONG_Y)
        assertLanded(m, HUB_SONGS)
        rule.runOnUiThread { assertEquals(SONG_ROW, m.pane.focus.row) }
        up()
    }

    @Test
    fun anEdgeScrubLandsEachBucketOnTheSongsPivot() = scrubs(HUB_SONGS, ROW, Listing.Songs)

    @Test
    fun anEdgeScrubLandsEachBucketOnTheArtistsPivot() = scrubs(HUB_ARTISTS, COLLECTION, Listing.Artists)

    companion object {
        private const val SDK = 35
        private const val V1_W = 240
        private const val V1_H = 320
        private const val R1_W = 480
        private const val R1_H = 800
        private const val R1_TOP = 240
        private const val R1_ROW1_LAST = 452f
        private const val R1_CLIP_TOP = 203
        private const val R1_Q = 321
        private const val R1_SCROLL = 20
        private const val R1_BAND_Y = 230f
        private const val A105_W = 720
        private const val A105_H = 1280
        private const val A105_TOP = 360
        private const val A105_ROWS = 5
        private const val A105_SONG_PX = 159.6f
        private const val INK = 60
        private const val V1_ROWS = 4
        private const val PAN_X0 = 160f
        private const val PAN_Y = 200f
        private const val PAN_STEP = 20f
        private const val PAN_DRAG = 60f
        private const val PAN_BACK = 10f
        private const val HELD_MS = 60L
        private const val SLIDE_X = 60f
        private const val TOP = 98
        private const val RING = 2
        private const val WHITE = 250
        private const val DIM = 120
        private const val ROW = 53
        private const val COLLECTION = 55
        private const val GRID_ROW = 101
        private const val COLS = 3
        private const val TAP_X = 120f
        private const val RING_X = 34.5f
        private const val EDGE_X = 5f
        private const val HALF_ROW = 0.5f
        private const val SETTLE = 1000L
        private const val SWIPE_MS = 120L
        private const val FROM = 0.7f
        private const val TO = 0.25f
        private const val WAIT_MS = 5_000L
        private const val MAX_FRAMES = 60
        private const val FAR = 30
        private const val SHOWN = 4
        private const val SWITCH_END = 250L
        private const val PAST = 300L
        private const val SONG_ROW = 2
        private const val SONG_Y = TOP + SONG_ROW * ROW + ROW / 2f
        private const val ARTISTS_ROW = 3
        private const val DRAG_FROM = 200f
        private const val DRAG_SLOP = 170f
        private const val DRAG_TO = 70f
        private const val DRAGGED = 100
        private const val BUCKET = 13
        private const val CELL_Y = 40f
        private const val GAP_Y = 95f
    }
}
