package net.seilent.funekit.screens.music.artist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.artist.ARTIST_ALBUMS
import net.seilent.funekit.screens.music.artist.ARTIST_SONGS
import net.seilent.funekit.screens.music.artist.ArtistGeo
import net.seilent.funekit.screens.music.artist.ArtistModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class ArtistRestTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val fake = Fake(CoroutineScope(EmptyCoroutineContext), { 0L }, FakeSet.Main, 0L, LEASE_MS)

    private fun loaded(): ArtistModel = runBlocking {
        val v = fake.library.view()
        val n = v.page(Listing.Artists, 0, 0).total
        val id = v.page(Listing.Artists, 0, n).rows.first { v.page(Listing.ArtistAlbums(it.id), 0, 0).total > 1 }.id
        val m = ArtistModel(0L, id)
        m.geometry(ArtistGeo(a105))
        m.load(v)
        for (p in m.panes) {
            p.track.count(p.count, 0)
            if (p.count > 0) m.show(v, p.pivot, 0, p.count - 1)
        }
        m
    }

    private class Gone(v: LibraryView) : LibraryView by v {
        override suspend fun indexOf(listing: Listing, kind: Kind, id: Long): Int? = null
    }

    private class First(v: LibraryView) : LibraryView by v {
        override suspend fun indexOf(listing: Listing, kind: Kind, id: Long): Int? = 0
    }

    @Test
    fun aNewGenerationKeepsTheFocusedAlbumOfEachPane() {
        val m = loaded()
        val albums = m.panes[ARTIST_ALBUMS]
        albums.focus.set(1, albums.count, 0L, false)
        val key = albums.row(1)!!.id
        runBlocking { fake.library.rescan() }
        val v = runBlocking { fake.library.view() }
        runBlocking { m.migrate(v, 0L) }
        assertEquals(v.generation, m.gen)
        assertEquals(key, albums.row(albums.focus.row)!!.id)
        assertFalse(m.gone)
    }

    @Test
    fun aNewGenerationPutsEachPanesFocusWhereTheViewFindsItsKey() {
        val m = loaded()
        val albums = m.panes[ARTIST_ALBUMS]
        albums.focus.set(1, albums.count, 0L, false)
        runBlocking { m.migrate(First(fake.library.view()), 0L) }
        assertEquals(0, albums.focus.row)
    }

    @Test
    fun anArtistGoneFromTheViewEmptiesBothPanesAndPutsTheFocusOnTheRings() {
        val m = loaded()
        assertTrue("premise: both panes loaded", m.panes[ARTIST_ALBUMS].count > 0 && m.panes[ARTIST_SONGS].count > 0)
        m.listDown()
        assertFalse("premise: the focus sits in the list", m.onRings)
        runBlocking { m.migrate(Gone(fake.library.view()), 0L) }
        assertTrue(m.gone)
        assertEquals(0, m.panes[ARTIST_ALBUMS].count)
        assertEquals(0, m.panes[ARTIST_SONGS].count)
        assertEquals(null, m.panes[ARTIST_ALBUMS].row(0))
        assertEquals(null, m.panes[ARTIST_SONGS].row(0))
        assertTrue(m.onRings)
        assertTrue(m.ready)
    }

    companion object {
        private const val SDK = 35
        private const val W = 720
        private const val H = 1280
        private const val LEASE_MS = 60_000L
    }
}
