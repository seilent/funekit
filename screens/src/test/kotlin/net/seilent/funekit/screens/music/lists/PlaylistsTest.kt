package net.seilent.funekit.screens.music.lists

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.TrackSet
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.lists.PlaylistsGeo
import net.seilent.funekit.screens.music.lists.PlaylistsKey
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {
    private val none = Insets(0, 0, 0, 0)

    private fun fake(set: FakeSet = FakeSet.Demo) = testFake(set)

    private fun src(f: Fake) = MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)

    private fun key(f: Fake) = PlaylistsKey(f.playlists.revision.value, f.favorites.revision.value, f.player.state.value, f.library.generation.value)

    private fun model(): PlaylistsModel = PlaylistsModel(0L).also { it.pane.geometry(RowSpec(PITCH, VIEW)) }

    private fun load(m: PlaylistsModel, f: Fake) = runBlocking { src(f).viewed { v -> m.load(v, key(f), 0L) } }

    private fun lists(f: Fake) = runBlocking {
        val v = f.library.view()
        v.page(Listing.Playlists, 0, v.page(Listing.Playlists, 0, 0).total).rows
    }

    private fun songs(f: Fake, n: Int): List<Long> = runBlocking { f.library.view().page(Listing.Songs, 0, n).rows.map { it.id } }

    private fun row(m: PlaylistsModel, title: String): Int = (0 until m.size).first { m.rowModel(it)?.primary == title }

    @Test
    fun theRowsAreNowPlayingFavoritesAndTheListsInMergeOrder() {
        val f = fake()
        val m = model()
        load(m, f)
        assertEquals("now playing", m.rowModel(0)?.primary)
        assertEquals("0 songs", m.rowModel(0)?.secondary)
        assertEquals("favorites", m.rowModel(1)?.primary)
        assertEquals("5 songs", m.rowModel(1)?.secondary)
        val l = lists(f)
        assertEquals(l.map { it.title }, (2 until m.size).map { m.rowModel(it)?.primary })
        assertTrue((0 until m.size).all { m.rowModel(it)?.bold == true && m.rowModel(it)?.collection == true })
        assertEquals("3 playlists", m.count())
    }

    @Test
    fun theNowPlayingRowCountsTheQueue() {
        val f = fake()
        runBlocking { f.player.play(songs(f, 3), 0, 0) }
        val m = model()
        load(m, f)
        assertEquals("3 songs", m.rowModel(0)?.secondary)
    }

    @Test
    fun thePlayingListAndFavoritesTakeTheAccent() {
        val f = fake()
        val m = model()
        val id = lists(f).first().id
        runBlocking { f.player.play(Listing.PlaylistTracks(id), f.library.generation.value, 0, 0) }
        load(m, f)
        assertTrue(m.rowModel(2)?.accent == true)
        assertFalse(m.rowModel(1)?.accent == true)
        assertFalse(m.rowModel(3)?.accent == true)
        runBlocking { f.player.play(Listing.Favorites, f.library.generation.value, 0, 0) }
        load(m, f)
        assertTrue(m.rowModel(1)?.accent == true)
        assertFalse(m.rowModel(2)?.accent == true)
    }

    @Test
    fun theFocusKeepsItsListAcrossAListAddedBeforeIt() {
        val f = fake()
        val m = model()
        load(m, f)
        val last = m.size - 1
        val title = m.rowModel(last)?.primary
        m.pane.focus.set(last, m.size, 0L, false)
        runBlocking { f.playlists.create(TrackSet.Ids(songs(f, 1))) }
        load(m, f)
        val at = row(m, title!!)
        assertTrue("premise: the new list sorts before the focused one", at > last)
        assertEquals("the focus keeps its list by key", at, m.pane.focus.row)
    }

    @Test
    fun theFocusedListKeepsItsPlaceOnScreenWhenAListSortsAboveIt() {
        val f = fake()
        val m = PlaylistsModel(0L).also { it.pane.geometry(RowSpec(PITCH, SHORT_VIEW * PITCH)) }
        load(m, f)
        val last = m.size - 1
        m.pane.track.at((last + 1 - SHORT_VIEW) * PITCH)
        m.pane.focus.set(last, m.size, 0L, false)
        val screen = last * PITCH - m.pane.track.q
        runBlocking { f.playlists.create(TrackSet.Ids(songs(f, 1))) }
        load(m, f)
        val at = m.pane.focus.row
        assertTrue("premise: the new list sorts before the focused one", at > last)
        assertEquals("the focused row stays where it was on screen", screen, at * PITCH - m.pane.track.q)
    }

    @Test
    fun theFocusOnTheListOfIdOneStaysAcrossAQueueSizeChange() {
        val f = fake()
        val m = model()
        load(m, f)
        val at = (2 until m.size).first { lists(f)[it - 2].id == 1L }
        m.pane.focus.set(at, m.size, 0L, false)
        runBlocking { f.player.enqueue(songs(f, 2)) }
        load(m, f)
        assertEquals("2 songs", m.rowModel(0)?.secondary)
        assertEquals("the focus stays on the list of id 1", at, m.pane.focus.row)
    }

    @Test
    fun eachOfTheFiveTriggersMakesTheSetStaleAndTheReloadTakesIt() {
        val f = fake()
        val m = model()
        load(m, f)
        assertFalse(m.stale(key(f)))

        runBlocking { f.playlists.create(TrackSet.Ids(songs(f, 1))) }
        assertTrue("a playlist change makes the set stale", m.stale(key(f)))
        load(m, f)
        assertEquals("4 playlists", m.count())

        val fav = songs(f, FAV_PICK).first { id -> runBlocking { !f.favorites.has(id) } }
        runBlocking { f.favorites.toggle(fav) }
        assertTrue("a favorites change makes the set stale", m.stale(key(f)))
        load(m, f)
        assertEquals("6 songs", m.rowModel(1)?.secondary)

        runBlocking { f.player.enqueue(songs(f, 2)) }
        assertTrue("a queue size change makes the set stale", m.stale(key(f)))
        load(m, f)
        assertEquals("2 songs", m.rowModel(0)?.secondary)

        val l = lists(f).filter { (it.count ?: 0) == SHORT }
        assertTrue("premise: two lists of one size", l.size >= 2)
        runBlocking { f.player.play(Listing.PlaylistTracks(l[0].id), f.library.generation.value, 0, 0) }
        load(m, f)
        runBlocking { f.player.play(Listing.PlaylistTracks(l[1].id), f.library.generation.value, 0, 0) }
        assertTrue("an origin change makes the set stale", m.stale(key(f)))
        load(m, f)
        assertTrue(m.rowModel(row(m, l[1].title))?.accent == true)

        val k = key(f)
        assertTrue("a generation change makes the set stale", m.stale(k.copy(generation = k.generation + 1)))
    }

    @Test
    fun noPlaylistsCountsZero() {
        val f = fake(FakeSet.DemoNoPlaylists)
        val m = model()
        load(m, f)
        assertEquals("0 playlists", m.count())
        assertEquals(2, m.size)
    }

    @Test
    fun theA105RowsStartAtTheQueueRowsTopOnTheCollectionPitch() {
        val s = scale(Window(A105_W, A105_H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
        val g = PlaylistsGeo(s)
        assertEquals(360, g.top)
        assertEquals(165, g.pitch)
        assertEquals(360 + 165 * 3, g.top + 3 * g.pitch)
    }

    @Test
    fun theModelIsReadyOnceTheSetAndTheQueueStateLand() {
        val f = fake()
        val m = model()
        assertFalse(m.ready)
        assertEquals(0, m.size)
        load(m, f)
        assertTrue(m.ready)
    }

    @Test
    fun theListAndFavoritesTargetsComeFromThePlaylistsPage() {
        val f = fake()
        val m = model()
        load(m, f)
        assertEquals(null, m.target(0))
        assertEquals(SheetKind.Favorites, m.target(1)?.kind)
        assertEquals(SheetFrom.Playlists, m.target(1)?.from)
        assertEquals(SheetKind.Playlist, m.target(2)?.kind)
        assertEquals(SheetFrom.Playlists, m.target(2)?.from)
        assertEquals(Listing.PlaylistTracks(lists(f)[0].id), m.target(2)?.listing)
    }

    private companion object {
        const val PITCH = 165
        const val VIEW = 920
        const val SHORT = 3
        const val SHORT_VIEW = 2
        const val FAV_PICK = 40
        const val A105_W = 720
        const val A105_H = 1280
        const val SDK = 35
    }
}
