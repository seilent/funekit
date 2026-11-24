package net.seilent.funekit.screens.music.queue

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.overlay.MOVE_DOWN
import net.seilent.funekit.screens.music.overlay.MOVE_UP
import net.seilent.funekit.screens.music.overlay.PLAY_NEXT
import net.seilent.funekit.screens.music.overlay.REMOVE
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.queue.QueueModel
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelTest {
    private val f = testFake()
    private val src = MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)

    private fun songs(n: Int): List<Long> = runBlocking { f.library.view().page(Listing.Songs, 0, n).rows.map { it.id } }

    private fun opened(ids: List<Long>, cur: Int): QueueModel = runBlocking {
        src.player.play(ids, cur, 0)
        val m = QueueModel(0)
        m.geometry(RowSpec(PITCH, VIEW))
        val st = src.player.state.value
        m.state(st)
        src.viewed { v -> m.show(v, 0, ids.size) }
        assertTrue(m.open(st, 0L))
        m
    }

    private fun synced(m: QueueModel) = runBlocking {
        val st = src.player.state.value
        assertTrue(m.changed(st))
        src.viewed { v -> m.sync(v, st, 0L) }
    }

    @Test
    fun aCardOutShowsNoRows() {
        val m = opened(songs(8), 0)
        assertEquals("premise: the queue shows its rows", m.size, m.rowsShown)
        assertTrue(m.size > 0)
        m.card = Card.Out
        assertEquals(0, m.rowsShown)
        m.card = Card.Present
        assertEquals(m.size, m.rowsShown)
    }

    @Test
    fun openFocusesTheCurrentSlotAndScrollsItIntoTheList() {
        val ids = songs(8)
        val m = opened(ids, 5)
        assertEquals(5, m.focus.row)
        assertEquals(minOf(5 * PITCH, 8 * PITCH - VIEW), m.track.q)
        assertTrue(m.rowModel(5)?.accent == true)
        assertFalse(m.rowModel(4)?.accent == true)
        assertEquals("6 of 8", m.count())
    }

    @Test
    fun aQueueRowDrawsItsTitleOverItsArtistWithItsSlotNumber() {
        val ids = songs(8)
        val m = opened(ids, 2)
        val rows = runBlocking { f.library.view().page(Listing.Queue, 0, ids.size).rows }
        val r = checkNotNull(m.rowModel(4))
        assertEquals("the title is the primary line", rows[4].title, r.primary)
        assertEquals("the artist line is the source's", rows[4].sub, r.secondary)
        assertEquals("the slot number counts from one", "5", r.number)
        assertFalse(r.accent)
        assertTrue("the playing slot takes the accent", checkNotNull(m.rowModel(2)).accent)
    }

    @Test
    fun aModelBuiltBeforeTheFirstStateWithATrackFocusesNothingUntilItComes() {
        val m = QueueModel(0)
        m.geometry(RowSpec(PITCH, VIEW))
        assertFalse(m.open(PlayerState(), 0L))
        assertFalse(m.opened)
        val ids = songs(8)
        runBlocking {
            src.player.play(ids, 3, 0)
            val st = src.player.state.value
            m.state(st)
            src.viewed { v -> m.show(v, 0, ids.size) }
            assertTrue(m.open(st, 0L))
        }
        assertEquals(3, m.focus.row)
    }

    @Test
    fun playNextAndMoveUpLandTheRowAndTheFocusWhereThePlayerPutIt() {
        val ids = songs(8)
        var m = opened(ids, 3)
        runBlocking { m.verb(PLAY_NEXT, 1, m.revision, src, 0L) }
        assertEquals(ids[1], m.row(3)?.id)
        assertEquals(3, m.focus.row)
        m = opened(ids, 3)
        runBlocking { m.verb(PLAY_NEXT, 5, m.revision, src, 0L) }
        assertEquals(ids[5], m.row(4)?.id)
        assertEquals(4, m.focus.row)
        m = opened(ids, 3)
        runBlocking { m.verb(MOVE_UP, 2, m.revision, src, 0L) }
        assertEquals(ids[2], m.row(1)?.id)
        assertEquals(1, m.focus.row)
        assertEquals("4 of 8", m.count())
    }

    @Test
    fun aVerbAtAnOldRevisionChangesNothingAndTheWindowFollows() {
        val ids = songs(8)
        val m = opened(ids, 0)
        runBlocking { src.player.move(6, 7, src.player.state.value.queueRevision) }
        val moved = src.player.state.value
        runBlocking { m.verb(REMOVE, 1, m.revision, src, 0L) }
        assertEquals(8, src.player.state.value.queueSize)
        synced(m)
        assertEquals(moved.queueRevision, m.revision)
        runBlocking { m.verb(REMOVE, 7, m.revision, src, 0L) }
        assertEquals(7, src.player.state.value.queueSize)
        assertEquals("1 of 7", m.count())
    }

    @Test
    fun anEditAboveTheFocusedRowKeepsTheFocusOnItsTrackAtItsScreenOffset() {
        val ids = songs(9)
        val m = opened(ids.take(8), 0)
        m.focus.set(3, 8, 0L, false)
        val q = m.track.q
        runBlocking { src.player.playNext(listOf(ids[8])) }
        synced(m)
        assertEquals(4, m.focus.row)
        assertEquals(ids[3], m.row(4)?.id)
        assertEquals(q + PITCH, m.track.q)
    }

    @Test
    fun aRowTapWithAnUnreloadedWindowSendsItsRevisionAndPlaysNothing() {
        val ids = songs(8)
        val m = opened(ids, 0)
        runBlocking { src.player.move(5, 6, src.player.state.value.queueRevision) }
        val played = runBlocking { m.tap(2, src) }
        assertFalse(played)
        assertEquals(0, src.player.state.value.index)
        synced(m)
        assertTrue(runBlocking { m.tap(2, src) })
        assertEquals(2, src.player.state.value.index)
    }

    @Test
    fun aServiceRowKeepsItsSlotActsAndNoTrackActs() = runBlocking {
        val g = testFake(services = true)
        val s = MusicSource(g.library, g.player, g.quickplay, g.favorites, g.playlists, g.lyrics)
        val service = g.library.view().page(Listing.History, 0, HISTORY).rows.first { it.kind == Kind.Service }.id
        val song = g.library.view().page(Listing.Songs, 0, 1).rows.single().id
        s.player.playService(service)
        s.player.enqueue(listOf(song))
        s.player.playAt(1, s.player.state.value.queueRevision)
        val m = QueueModel(0)
        m.geometry(RowSpec(PITCH, VIEW))
        val st = s.player.state.value
        m.state(st)
        s.viewed { v -> m.show(v, 0, 2) }
        assertTrue(m.open(st, 0L))
        val t = checkNotNull(m.target(0))
        assertNull(t.track)
        assertFalse(t.library)
        assertEquals(listOf(REMOVE, PLAY_NEXT, MOVE_DOWN), sheetItems(t, false))
        assertEquals(song, m.target(1)?.track)
        assertTrue(m.tap(0, s))
        assertEquals(service, s.player.state.value.service)
    }

    @Test
    fun aVerbAtItsSheetTargetsRevisionAfterTheWindowFollowedAnEditIsStale() {
        val ids = songs(8)
        val m = opened(ids, 0)
        val t = checkNotNull(m.target(1))
        runBlocking { src.player.move(6, 7, src.player.state.value.queueRevision) }
        synced(m)
        assertNotEquals("premise: the window follows the edit", t.revision, m.revision)
        runBlocking { m.verb(REMOVE, t.slot, t.revision, src, 0L) }
        assertEquals(8, src.player.state.value.queueSize)
        assertEquals(ids[1], m.row(1)?.id)
    }

    @Test
    fun aStateWhoseSyncNeverFinishedIsStillChanged() {
        val ids = songs(8)
        val m = opened(ids, 0)
        runBlocking { src.player.move(6, 7, src.player.state.value.queueRevision) }
        val st = src.player.state.value
        assertTrue(m.changed(st))
        assertTrue(m.changed(st))
        runBlocking { src.viewed { v -> m.sync(v, st, 0L) } }
        assertFalse(m.changed(st))
    }

    @Test
    fun aQueueIsReadyOnceItsWindowHoldsItsFirstShownRow() {
        val ids = songs(LONG)
        val m = QueueModel(0)
        m.geometry(RowSpec(PITCH, VIEW))
        assertFalse(m.ready)
        runBlocking {
            src.player.play(ids, FAR, 0)
            val st = src.player.state.value
            m.state(st)
            assertFalse(m.ready)
            src.viewed { v -> m.show(v, 0, 0) }
            assertTrue(m.open(st, 0L))
            assertEquals(FAR, m.track.first)
            assertFalse(m.ready)
            src.viewed { v -> m.show(v, FAR - 2, FAR + 2) }
        }
        assertTrue(m.ready)
        val u = QueueModel(2)
        u.geometry(RowSpec(PITCH, VIEW))
        runBlocking {
            val st = src.player.state.value.copy(track = null)
            u.state(st)
            assertFalse(u.ready)
            src.viewed { v -> u.show(v, FAR - 2, FAR + 2) }
            assertFalse(u.open(st, 0L))
            assertEquals(0, u.track.first)
            assertFalse(u.ready)
            src.viewed { v -> u.show(v, 0, 0) }
        }
        assertTrue(u.ready)
        val e = QueueModel(1)
        assertFalse(e.ready)
        e.state(PlayerState())
        assertTrue(e.ready)
    }

    @Test
    fun aPressOnARowScrolledOutOfTheQueueKeepsNoFrame() {
        val m = opened(songs(LONG), 0)
        m.press.down(0, PRESS_AT)
        m.track.at(FAR * PITCH)
        assertTrue("premise: the press still moves", m.press.stale(PRESS_AT + PRESS_STEP))
        assertFalse(m.animating(PRESS_AT + PRESS_STEP))
        m.track.at(0)
        assertTrue(m.animating(PRESS_AT + PRESS_STEP))
    }

    companion object {
        private const val PRESS_AT = 10_000L
        private const val PRESS_STEP = 10L
        private const val PITCH = 120
        private const val HISTORY = 100
        private const val VIEW = 920
        private const val LONG = 800
        private const val FAR = 700
    }
}
