package net.seilent.funekit.screens.host

import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.Anchor
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PushFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicModelsTest {
    private val full = Row(ALBUM, Kind.Album, "Cobalt Rooms", "", ArtRef.Embedded(TRACK, OFFSET, LENGTH), 2, DURATION, YEAR, "Cobalt Rooms", 2, 1, true)

    @Test
    fun aMusicEntryCarriesItsSourceRowHeadAndStateThroughTheSaver() {
        val e = MusicEntry(ENTRY, MusicPage.Album(ALBUM), PushFrom(UNDER, 0, "albums", ROW), true, full)
        val back = musicSaved(savedMusic(e, 1, Anchor(Kind.Song, ID, OFF, INDEX), listOf(2, 600f)))!!
        assertEquals(e, back.entry)
        assertEquals(1, back.pivot)
        assertEquals(Anchor(Kind.Song, ID, OFF, INDEX), back.anchor)
        assertEquals(listOf(2, 600f), back.state)
        val bare = musicSaved(savedMusic(MusicEntry(ENTRY, MusicPage.ListView(Listing.PlaylistTracks(ID))), 0, null, null))!!
        assertNull(bare.entry.head)
        assertNull(bare.entry.from)
        assertNull(bare.anchor)
        assertNull(bare.state)
    }

    @Test
    fun aMusicPageSavedWithNoAnchorIndexRestoresItAsUnknown() {
        val old = arrayListOf<Any>(
            "music",
            arrayListOf<Any>(ENTRY, arrayListOf("album", ALBUM), arrayListOf<Any>(UNDER, 0, "albums", ROW), true, arrayListOf<Any>(ALBUM, "Album", "Cobalt Rooms", "", arrayListOf<Any>("c", TRACK, "cover.jpg"), -1, -1L, -1, arrayListOf<Any>(), -1, -1, false)),
            arrayListOf<Any>(1, arrayListOf<Any>("Song", ID, OFF)),
            arrayListOf<Any>(2, 600f),
        )
        val back = musicSaved(old)!!
        assertEquals(MusicEntry(ENTRY, MusicPage.Album(ALBUM), PushFrom(UNDER, 0, "albums", ROW), true, Row(ALBUM, Kind.Album, "Cobalt Rooms", "", ArtRef.Cover(TRACK, "cover.jpg"))), back.entry)
        assertEquals(Anchor(Kind.Song, ID, OFF, -1), back.anchor)
        assertEquals(listOf(2, 600f), back.state)
        assertNull("another tag is no music page", musicSaved(arrayListOf<Any>("drawer")))
    }

    companion object {
        private const val ENTRY = 4L
        private const val UNDER = 3L
        private const val ALBUM = 20L
        private const val TRACK = 77L
        private const val ID = 7L
        private const val ROW = 3
        private const val INDEX = 41
        private const val OFF = 12.5f
        private const val OFFSET = 128L
        private const val LENGTH = 4096L
        private const val DURATION = 181_000L
        private const val YEAR = 2004
    }
}
