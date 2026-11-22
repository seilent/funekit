package net.seilent.funekit.screens.host

import net.seilent.funekit.api.Listing
import net.seilent.funekit.screens.music.MusicPage
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteExitsTest {
    @Test
    fun anAlbumPageOfADueAlbumLeavesWhereverItSits() {
        val hub = MusicPage.Hub
        val gone = MusicPage.Album(GONE)
        assertEquals("on top", listOf(1), deleteExits(listOf(hub, gone), setOf(GONE), emptySet()))
        assertEquals("buried", listOf(1), deleteExits(listOf(hub, gone, MusicPage.Queue), setOf(GONE), emptySet()))
        assertEquals("under now playing", listOf(1), deleteExits(listOf(hub, gone, MusicPage.NowPlaying), setOf(GONE), emptySet()))
        assertEquals("twice on the stack", listOf(1, 3), deleteExits(listOf(hub, gone, MusicPage.Artist(ARTIST), gone), setOf(GONE), emptySet()))
    }

    @Test
    fun anArtistPageLeavesOnlyWhenItsArtistIsGiven() {
        val pages = listOf(MusicPage.Hub, MusicPage.Artist(ARTIST), MusicPage.Album(GONE))
        assertEquals(listOf(2), deleteExits(pages, setOf(GONE), emptySet()))
        assertEquals(listOf(1, 2), deleteExits(pages, setOf(GONE), setOf(ARTIST)))
    }

    @Test
    fun otherPagesStay() {
        val pages = listOf(
            MusicPage.Hub,
            MusicPage.Search("q"),
            MusicPage.ListView(Listing.PlaylistTracks(1)),
            MusicPage.Queue,
            MusicPage.Album(OTHER),
            MusicPage.Artist(OTHER),
            null,
        )
        assertEquals(emptyList<Int>(), deleteExits(pages, setOf(GONE), setOf(ARTIST)))
    }

    private companion object {
        const val GONE = 7L
        const val OTHER = 8L
        const val ARTIST = 9L
    }
}
