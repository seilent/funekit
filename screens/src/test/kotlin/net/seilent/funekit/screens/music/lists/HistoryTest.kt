package net.seilent.funekit.screens.music.lists

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.scale
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.TrackIds
import net.seilent.funekit.screens.music.lists.HISTORY_NEW
import net.seilent.funekit.screens.music.lists.HISTORY_RECENT
import net.seilent.funekit.screens.music.lists.HistoryAct
import net.seilent.funekit.screens.music.lists.HistoryGeo
import net.seilent.funekit.screens.music.lists.HistoryModel
import net.seilent.funekit.screens.music.lists.ViewKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class HistoryTest {
    private val none = Insets(0, 0, 0, 0)
    private var recent: List<Row> = (1L..FAKE).map { Row(it, Kind.Song, "song $it", "artist $it", null) }
    private var newest: List<Row> = (1L..FAKE).map { Row(ALBUM + it, Kind.Album, "album $it", "artist $it", null) }
    private var gen = 1L

    private fun view(): LibraryView = Proxy.newProxyInstance(LibraryView::class.java.classLoader, arrayOf(LibraryView::class.java)) { _, method, args ->
        when (method.name) {
            "getGeneration" -> gen
            "page" -> {
                val rows = if (args[0] == Listing.History) recent else newest
                val off = args[1] as Int
                val n = args[2] as Int
                Page(gen, off, rows.size, rows.drop(off).take(n))
            }
            else -> throw UnsupportedOperationException(method.name)
        }
    } as LibraryView

    private fun model(): HistoryModel = HistoryModel(0L).also { m -> m.panes.forEach { it.geometry(RowSpec(PITCH, VIEW)) } }

    private fun load(m: HistoryModel, k: ViewKey = ViewKey(0L, gen)) = runBlocking { m.load(view(), k, 0L) }

    @Test
    fun bothPanesKeepTheViewOrderCutToTheHistoryRows() {
        val m = model()
        load(m)
        val cap = Tok.history_rows.toInt()
        assertEquals(cap, m.count(HISTORY_RECENT))
        assertEquals(cap, m.count(HISTORY_NEW))
        assertEquals(recent.take(cap).map { it.title }, (0 until cap).map { m.rowModel(HISTORY_RECENT, it)?.primary })
        assertEquals(newest.take(cap).map { it.title }, (0 until cap).map { m.rowModel(HISTORY_NEW, it)?.primary })
    }

    @Test
    fun aCardErrorShowsItsLineAndNoRows() {
        val m = model()
        load(m)
        assertTrue("premise: recent holds rows", m.count(HISTORY_RECENT) > 0)
        m.card = Card.Error
        assertEquals(0, m.count(HISTORY_RECENT))
        assertEquals(0, m.count(HISTORY_NEW))
        assertEquals("card error", m.empty(HISTORY_RECENT))
        assertTrue("the line counts as ready", m.ready)
    }

    @Test
    fun recentMarksTheCurrentTrackAndNewThePlayingAlbum() {
        val m = model()
        load(m)
        m.current = CURRENT
        m.playing = TrackIds(emptyList(), ALBUM + PLAYING, null, emptyList())
        assertTrue(m.rowModel(HISTORY_RECENT, (CURRENT - 1).toInt())?.accent == true)
        assertFalse(m.rowModel(HISTORY_RECENT, 0)?.accent == true)
        assertTrue("the playing album takes the accent", m.rowModel(HISTORY_NEW, (PLAYING - 1).toInt())?.accent == true)
        assertFalse(m.rowModel(HISTORY_NEW, (CURRENT - 1).toInt())?.accent == true)
    }

    @Test
    fun theRowsTakeThePlainSecondary() {
        val m = model()
        load(m)
        val r = m.rowModel(HISTORY_RECENT, 0)!!
        assertEquals("artist 1", r.secondary)
        assertFalse("history rows take the plain secondary", r.libSub)
        assertFalse(m.rowModel(HISTORY_NEW, 0)!!.libSub)
        assertFalse(m.look(HISTORY_RECENT, 0, AT).focused)
    }

    @Test
    fun emptyPanesShowTheirLines() {
        recent = emptyList()
        newest = emptyList()
        val m = model()
        load(m)
        assertEquals("nothing played yet", m.empty(HISTORY_RECENT))
        assertEquals("nothing here", m.empty(HISTORY_NEW))
    }

    @Test
    fun theA105RowsStartAtTheContentTopOnTheRowPitch() {
        val s = scale(Window(A105_W, A105_H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
        val g = HistoryGeo(s)
        assertEquals(360, g.top)
        assertEquals(120, g.pitch)
        assertEquals(360 + 120 * 4, g.top + 4 * g.pitch)
    }

    @Test
    fun theModelIsReadyOnceTheActivePaneLands() {
        val m = model()
        assertFalse(m.ready)
        load(m)
        assertTrue(m.ready)
    }

    @Test
    fun aNewGenerationReloadsBothPanesWithTheFocusByKey() {
        val m = model()
        load(m)
        m.panes[HISTORY_RECENT].focus.set(FOCUS, m.count(HISTORY_RECENT), 0L, false)
        m.panes[HISTORY_NEW].focus.set(FOCUS, m.count(HISTORY_NEW), 0L, false)
        recent = listOf(Row(NEW_SONG, Kind.Song, "new song", "", null)) + recent
        newest = listOf(Row(NEW_SONG, Kind.Album, "new album", "", null)) + newest
        gen++
        val k = ViewKey(0L, gen)
        assertTrue(m.stale(k))
        load(m, k)
        assertEquals("new song", m.rowModel(HISTORY_RECENT, 0)?.primary)
        assertEquals(FOCUS + 1, m.panes[HISTORY_RECENT].focus.row)
        assertEquals(FOCUS + 1, m.panes[HISTORY_NEW].focus.row)
    }

    @Test
    fun aReloadKeepsTheFocusedRowWhereItWasOnScreen() {
        val m = model()
        load(m)
        val pn = m.panes[HISTORY_RECENT]
        pn.track.at(FOCUS * PITCH - PITCH)
        pn.focus.set(FOCUS, m.count(HISTORY_RECENT), 0L, false)
        val screen = FOCUS * PITCH - pn.track.q
        recent = listOf(Row(NEW_SONG, Kind.Song, "new song", "", null)) + recent
        gen++
        load(m, ViewKey(0L, gen))
        assertEquals("premise: the focus follows its song", FOCUS + 1, pn.focus.row)
        assertEquals("the focused row stays where it was on screen", screen, pn.focus.row * PITCH - pn.track.q)
    }

    @Test
    fun aPaneNeverComposedStillSavesTheOthersState() {
        val m = HistoryModel(0L).also { it.panes[HISTORY_RECENT].geometry(RowSpec(PITCH, VIEW)) }
        m.panes[HISTORY_RECENT].ppu = PPU
        load(m)
        assertNotNull("a pane with no scale yet leaves the state saved", m.state())
    }

    @Test
    fun aServiceRowPlaysThroughTheServiceDoorAndHasNoSheet() {
        recent = recent.toMutableList().also { it[2] = Row(SERVICE, Kind.Service, "radio", "", null) }
        val m = model()
        load(m)
        assertEquals(HistoryAct.Service(SERVICE), m.act(2))
        assertNull(m.target(2))
        assertEquals(HistoryAct.Song(recent[1].id), m.act(1))
        assertNotNull(m.target(1))
    }

    @Test
    fun aRecentRowPlaysTheSongAloneAndANewRowItsAlbumWhole() {
        val m = model()
        load(m)
        assertEquals(HistoryAct.Song(recent[2].id), m.act(2))
        m.switchTo(HISTORY_NEW, AT)
        m.land()
        assertEquals(HistoryAct.Album(Listing.AlbumTracks(newest[2].id), -1), m.act(2))
    }

    @Test
    fun aDragMovesThePaneAndASwitchStartsFromItsOffset() {
        val m = model()
        load(m)
        m.drag(DRAG_U, AT)
        assertEquals(Math.round(DRAG_U * PPU), m.dragX(AT, PPU))
        assertTrue(m.switchTo(HISTORY_NEW, AT))
        assertEquals(Math.round(DRAG_U * PPU), m.motion.dx(AT, PPU))
        assertEquals(0, m.dragX(AT, PPU))
    }

    private fun scaled(): HistoryModel = model().also { m -> m.panes.forEach { it.ppu = PPU } }

    @Test
    fun aRestoredFocusFindsItsSongAfterTheHistoryChanged() {
        val m = scaled()
        load(m)
        m.panes[HISTORY_RECENT].focus.set(FOCUS, m.count(HISTORY_RECENT), 0L, false)
        val kept = requireNotNull(m.state())
        val id = recent[FOCUS].id
        recent = listOf(Row(NEW_SONG, Kind.Song, "new", "artist", null)) + recent
        val r = scaled()
        r.restoreState(kept)
        load(r)
        assertEquals("the focus comes back on its song", id, recent[r.panes[HISTORY_RECENT].focus.row].id)
    }

    @Test
    fun aRestoreBringsBackTheInactivePanesSpot() {
        val m = scaled()
        load(m)
        val nw = m.panes[HISTORY_NEW]
        val n = m.count(HISTORY_NEW)
        nw.go(n, px = FOCUS * PITCH)
        nw.focus.set(FOCUS + 1, n, 0L, false)
        val kept = requireNotNull(m.state())
        val r = scaled()
        r.restoreState(kept)
        load(r)
        assertEquals("premise: the restored page opens on RECENT", HISTORY_RECENT, r.pivot)
        assertEquals("the inactive pane's focus comes back", FOCUS + 1, r.panes[HISTORY_NEW].focus.row)
        assertEquals("the inactive pane's offset comes back", FOCUS * PITCH, r.panes[HISTORY_NEW].track.q)
    }

    private companion object {
        const val FAKE = 150L
        const val SERVICE = 9_000L
        const val ALBUM = 1000L
        const val CURRENT = 5L
        const val PLAYING = 3L
        const val FOCUS = 5
        const val NEW_SONG = 9999L
        const val PITCH = 120
        const val VIEW = 920
        const val AT = 1_000L
        const val A105_W = 720
        const val A105_H = 1280
        const val SDK = 35
        const val DRAG_U = -30f
        const val PPU = 2f
    }
}
