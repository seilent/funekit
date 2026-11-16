package net.seilent.funekit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayEntryTest {
    private val mbid = "00000000-0000-4000-8000-000000000001"

    private fun positional() = PlayEntry(7, 1_800_000_000, 240_000, "Title", "Artist", listOf("Artist", "Guest"), listOf(mbid), "Album", "Album Artist", 3, 32400)

    @Test
    fun thePositionalFormLandsEachValueInItsField() {
        val e = positional()
        assertEquals(7L, e.seq)
        assertEquals(1_800_000_000L, e.startLocalS)
        assertEquals(240_000L, e.durationMs)
        assertEquals("Title", e.title)
        assertEquals("Artist", e.artist)
        assertEquals(listOf("Artist", "Guest"), e.artists)
        assertEquals(listOf(mbid), e.mbids)
        assertEquals("Album", e.album)
        assertEquals("Album Artist", e.albumArtist)
        assertEquals(3, e.trackNo)
        assertEquals(32400, e.zoneS)
    }

    @Test
    fun entriesWithEqualFieldsAreEqual() {
        assertEquals(positional(), positional())
        assertEquals(listOf(positional()), listOf(positional()))
        assertNotEquals(positional(), positional().copy(exposed = true))
    }

    @Test
    fun theFullPositionalFormLandsLiveSeenAndExposedInTheirFields() {
        val e = PlayEntry(7, 1_800_000_000, 240_000, "Title", "Artist", listOf("Artist"), listOf(mbid), "Album", "Album Artist", 3, 32400, true, false)
        assertEquals(true, e.liveSeen)
        assertEquals(false, e.exposed)
        assertEquals(32400, e.zoneS)
    }

    @Test
    fun liveSeenAndExposedDefaultNull() {
        val e = positional()
        assertNull(e.liveSeen)
        assertNull(e.exposed)
        val bare = PlayEntry(1)
        assertNull(bare.startLocalS)
        assertNull(bare.zoneS)
    }
}
