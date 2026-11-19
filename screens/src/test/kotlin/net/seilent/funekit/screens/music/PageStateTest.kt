package net.seilent.funekit.screens.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fxU
import net.seilent.funekit.scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.albcol.AlbumColGeo
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.album.AlbumGeo
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.artist.ARTIST_ALBUMS
import net.seilent.funekit.screens.music.artist.ARTIST_SONGS
import net.seilent.funekit.screens.music.artist.ArtistGeo
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.RING_ADD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class PageStateTest {
    private val none = Insets(0, 0, 0, 0)

    private fun page(large: Boolean): Scale = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, large)

    private fun fake(set: FakeSet) = Fake(CoroutineScope(EmptyCoroutineContext), { 0L }, set, 0L, LEASE_MS)

    private fun album(f: Fake, s: Scale, saved: List<*>? = null): AlbumModel = runBlocking {
        val v = f.library.view()
        val n = v.page(Listing.Albums, 0, 0).total
        val id = v.page(Listing.Albums, 0, n).rows.first { it.title == COBALT }.id
        val m = AlbumModel(0L, id)
        saved?.let { m.restoreState(it) }
        m.geometry(AlbumGeo(s))
        m.ppu = s.ppu
        m.load(v)
        m.show(v, 0, m.total - 1)
        m.track.count(m.count, 0)
        m
    }

    private fun albumRoundTrip(s: Scale) {
        val f = fake(FakeSet.Main)
        val m = album(f, s)
        m.track.at(Q)
        assertEquals("premise: the list sits at $Q", Q, m.track.q)
        m.focus.set(FOCUS, m.count, 0L, false)
        val back = album(f, s, m.state())
        back.restoreAt(0L)
        assertEquals(Q, back.track.q)
        assertEquals(FOCUS, back.focus.row)
        assertEquals("the block collapsed whole, 115 u at the A105 px", ABOVE_PX, back.track.lift)
        if (!s.large) assertEquals(LIFT_U, fxU(back.geo!!.liftU(back.track.lift)), 0.01f)
    }

    @Test
    fun anAlbumComesBackAtItsOffsetWithTheBlockCollapsed() {
        albumRoundTrip(page(false))
    }

    @Test
    fun anAlbumAtLargeComesBackAtItsOffsetOnTheKeptListTopAndPitch() {
        val s = page(true)
        val g = AlbumGeo(s)
        assertEquals(LIST_TOP, g.top)
        assertEquals(LARGE_PITCH, g.pitch)
        albumRoundTrip(s)
    }

    @Test
    fun theColumnComesBackAtItsRowWithoutRunningItsOpenAgain() {
        val s = page(false)
        val f = fake(FakeSet.Demo)
        fun col(saved: List<*>?): AlbumColModel = runBlocking {
            val m = AlbumColModel(0L, 0)
            saved?.let { m.restoreState(it) }
            m.geometry(AlbumColGeo(s))
            m.ppu = s.ppu
            m.load(f.library.view())
            m.track.count(m.count, 0)
            m
        }
        val m = col(null)
        assertTrue(m.open(0L))
        assertTrue("premise: the column has rows past $COL_ROW", m.count > COL_ROW + 1)
        m.track.toRow(COL_ROW)
        m.focus.set(COL_ROW, m.count, 0L, false)
        val q = m.track.q
        val back = col(m.state())
        back.restoreAt(0L)
        assertFalse("the open does not run again", back.open(0L))
        assertEquals(q, back.track.q)
        assertEquals(COL_ROW, back.focus.row)
    }

    private fun artist(f: Fake, s: Scale, id: Long, saved: List<*>? = null): ArtistModel = runBlocking {
        val v = f.library.view()
        val m = ArtistModel(0L, id)
        saved?.let { m.restoreState(it) }
        m.geometry(ArtistGeo(s))
        for (p in m.panes) p.ppu = s.ppu
        m.load(v)
        for (p in m.panes) {
            p.track.count(p.count, 0)
            if (p.count > 0) m.show(v, p.pivot, 0, p.count - 1)
        }
        m
    }

    private fun anArtist(f: Fake): Long = runBlocking {
        val v = f.library.view()
        val n = v.page(Listing.Artists, 0, 0).total
        v.page(Listing.Artists, 0, n).rows.first {
            v.page(Listing.ArtistAlbums(it.id), 0, 0).total > ALBUM_ROW && v.page(Listing.ArtistTracks(it.id), 0, 0).total > SONG_ROW
        }.id
    }

    @Test
    fun theArtistComesBackOnSongsOffTheRingsWithEachPaneAtItsRow() {
        val s = page(false)
        val f = fake(FakeSet.Demo)
        val id = anArtist(f)
        val m = artist(f, s, id)
        m.listDown()
        m.pivot = ARTIST_SONGS
        m.panes[ARTIST_SONGS].focus.set(SONG_ROW, m.panes[ARTIST_SONGS].count, 0L, false)
        m.panes[ARTIST_ALBUMS].focus.set(ALBUM_ROW, m.panes[ARTIST_ALBUMS].count, 0L, false)
        val back = artist(f, s, id, m.state())
        back.pivot = ARTIST_SONGS
        back.restoreAt(0L)
        assertFalse(back.onRings)
        assertEquals(SONG_ROW, back.panes[ARTIST_SONGS].focus.row)
        assertEquals(ALBUM_ROW, back.panes[ARTIST_ALBUMS].focus.row)
    }

    @Test
    fun theArtistComesBackOnTheAddRing() {
        val s = page(false)
        val f = fake(FakeSet.Demo)
        val id = anArtist(f)
        val m = artist(f, s, id)
        m.ringDown(RING_ADD, 0L)
        val back = artist(f, s, id, m.state())
        back.restoreAt(0L)
        assertTrue(back.onRings)
        assertEquals(RING_ADD, back.ring)
    }

    companion object {
        private const val SDK = 35
        private const val W = 720
        private const val H = 1280
        private const val LEASE_MS = 60_000L
        private const val COBALT = "Cobalt Rooms"
        private const val Q = 600
        private const val FOCUS = 5
        private const val LIFT_U = 115f
        private const val ABOVE_PX = 345
        private const val LIST_TOP = 756
        private const val LARGE_PITCH = 144
        private const val COL_ROW = 5
        private const val ALBUM_ROW = 2
        private const val SONG_ROW = 3
    }
}
