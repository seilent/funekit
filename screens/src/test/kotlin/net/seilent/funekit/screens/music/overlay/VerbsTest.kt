package net.seilent.funekit.screens.music.overlay

import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VerbsTest {
    private fun queue(slot: Int, cur: Int = 2, size: Int = 6) = SheetTarget(SheetKind.Queue, "row", Listing.Queue, slot = slot, cur = cur, size = size)

    @Test
    fun aSongSheetShowsItsBuiltItemsInFuneOsOrder() {
        val t = SheetTarget(SheetKind.Song, "song", Listing.Songs, pin = Kind.Song, id = 3, from = SheetFrom.Hub)
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST, PIN, INFO, GO_TO_ALBUM), sheetItems(t, false))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST, UNPIN_QP, INFO, GO_TO_ALBUM), sheetItems(t, true))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST, PIN, INFO, GO_TO_ALBUM), sheetItems(t.copy(from = SheetFrom.History), false))
        assertTrue("delete" !in sheetItems(t, false))
    }

    @Test
    fun aSearchSongSheetEndsWithGoToArtistAfterGoToAlbum() {
        val t = SheetTarget(SheetKind.Song, "s", Listing.Search("q", Kind.Song), track = 1L, pin = Kind.Song, id = 1L, from = SheetFrom.Search)
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST, PIN, INFO, GO_TO_ALBUM, GO_TO_ARTIST), sheetItems(t, false))
        for (f in SheetFrom.entries - SheetFrom.Search) assertFalse("from $f", GO_TO_ARTIST in sheetItems(t.copy(from = f), false))
        assertFalse(GO_TO_ARTIST in sheetItems(t.copy(kind = SheetKind.Album, from = SheetFrom.Search), false))
    }

    @Test
    fun aSongSheetFromTheAlbumPageHasNoGoToAlbum() {
        val t = SheetTarget(SheetKind.Song, "song", Listing.AlbumTracks(2), pin = Kind.Song, id = 3, from = SheetFrom.Album)
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST, PIN, INFO), sheetItems(t, false))
    }

    @Test
    fun albumArtistGenreAndFolderSheetsFollowTheirTargets() {
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST, PIN), sheetItems(SheetTarget(SheetKind.Album, "a"), false))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST, UNPIN_QP), sheetItems(SheetTarget(SheetKind.Artist, "a"), true))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, ADD_TO_PLAYLIST), sheetItems(SheetTarget(SheetKind.Genre, "g"), false))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST), sheetItems(SheetTarget(SheetKind.Folder, "f"), false))
    }

    @Test
    fun playlistSheetsPlayWithSongsOpenAndDeleteOnlyAWritableList() {
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, OPEN_PLAYLIST), sheetItems(SheetTarget(SheetKind.Favorites, "favorites", songs = 3), false))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, OPEN_PLAYLIST, DELETE_PLAYLIST), sheetItems(SheetTarget(SheetKind.Playlist, "p", songs = 2, writable = true), false))
        assertEquals(listOf(OPEN_PLAYLIST, DELETE_PLAYLIST), sheetItems(SheetTarget(SheetKind.Playlist, "p", writable = true), false))
        assertEquals(listOf(OPEN_PLAYLIST), sheetItems(SheetTarget(SheetKind.Playlist, "p"), false))
        assertEquals(listOf(OPEN_PLAYLIST), sheetItems(SheetTarget(SheetKind.Favorites, "favorites", writable = true), false))
    }

    @Test
    fun anEntrySheetEditsAWritableListAndShowsInfo() {
        fun entry(slot: Int, writable: Boolean) = SheetTarget(SheetKind.Entry, "e", Listing.PlaylistTracks(1), slot = slot, size = 4, writable = writable)
        assertEquals(listOf(REMOVE, MOVE_DOWN, INFO), sheetItems(entry(0, true), false))
        assertEquals(listOf(REMOVE, MOVE_UP, MOVE_DOWN, INFO), sheetItems(entry(1, true), false))
        assertEquals(listOf(REMOVE, MOVE_UP, INFO), sheetItems(entry(3, true), false))
        assertEquals(listOf(INFO), sheetItems(entry(1, false), false))
    }

    @Test
    fun theQuickplayTileSheetHasNoPlay() {
        assertEquals(listOf(UNPIN, PLAY_NEXT, ADD), sheetItems(SheetTarget(SheetKind.Tile, "t", pin = Kind.Album, id = 1), true))
        assertEquals(listOf(PIN, PLAY_NEXT, ADD), sheetItems(SheetTarget(SheetKind.Tile, "t", pin = Kind.Album, id = 1), false))
    }

    @Test
    fun queueRowsOfferPlayNextAwayFromTheCurrentSlotAndMovesInsideTheQueue() {
        assertEquals(listOf(REMOVE, MOVE_UP, MOVE_DOWN, INFO), sheetItems(queue(2), false))
        assertEquals(listOf(REMOVE, MOVE_UP, MOVE_DOWN, INFO), sheetItems(queue(3), false))
        assertEquals(listOf(REMOVE, PLAY_NEXT, MOVE_DOWN, INFO), sheetItems(queue(0), false))
        assertEquals(listOf(REMOVE, PLAY_NEXT, MOVE_UP, INFO), sheetItems(queue(5), false))
        assertEquals(listOf(REMOVE, PLAY_NEXT, MOVE_UP, MOVE_DOWN), sheetItems(queue(4).copy(library = false), false))
    }

    private val pickImage = SheetExtra("pick image") { null }

    @Test
    fun deleteIsTheLastKitWordOnSongAndAlbumSheetsAndTheExtrasFollow() {
        val song = SheetTarget(SheetKind.Song, "song", Listing.Songs, track = 3L, pin = Kind.Song, id = 3, from = SheetFrom.Hub)
        for (t in listOf(song, song.copy(from = SheetFrom.Album), song.copy(listing = Listing.Search("q", Kind.Song), from = SheetFrom.Search))) {
            assertEquals("from ${t.from}", sheetItems(t, false) + DELETE, sheetWords(t, false, true, emptyList()))
            assertEquals("from ${t.from}", sheetItems(t, true) + DELETE + "pick image", sheetWords(t, true, true, listOf(pickImage)))
            assertEquals("from ${t.from}, no delete", sheetItems(t, false), sheetWords(t, false, false, emptyList()))
        }
        val album = SheetTarget(SheetKind.Album, "album", Listing.AlbumTracks(2), pin = Kind.Album, id = 2, from = SheetFrom.Hub)
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST, PIN, DELETE), sheetWords(album, false, true, emptyList()))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST, PIN, DELETE, "pick image"), sheetWords(album, false, true, listOf(pickImage)))
        assertEquals(listOf(PLAY, PLAY_NEXT, ADD, SHUFFLE, ADD_TO_PLAYLIST, PIN, "pick image"), sheetWords(album, false, false, listOf(pickImage)))
    }

    @Test
    fun noOtherSheetKindTakesDelete() {
        val targets = listOf(
            SheetTarget(SheetKind.Artist, "a"),
            SheetTarget(SheetKind.Genre, "g"),
            SheetTarget(SheetKind.Folder, "f"),
            SheetTarget(SheetKind.Playlist, "p", songs = 2, writable = true),
            SheetTarget(SheetKind.Favorites, "favorites", songs = 3),
            SheetTarget(SheetKind.Tile, "t", pin = Kind.Album, id = 1),
            queue(2),
            SheetTarget(SheetKind.Entry, "e", Listing.PlaylistTracks(1), slot = 1, size = 4, writable = true),
        )
        for (t in targets) assertEquals("${t.kind}", sheetItems(t, false), sheetWords(t, false, true, emptyList()))
    }

    @Test
    fun anExtraWordThatAnyKitSheetShowsIsRefused() {
        val artist = SheetTarget(SheetKind.Artist, "a")
        for (w in listOf(PLAY, DELETE, REMOVE)) {
            assertThrows("$w on an artist sheet", IllegalArgumentException::class.java) { sheetWords(artist, false, false, listOf(SheetExtra(w) { null })) }
        }
        val album = SheetTarget(SheetKind.Album, "album", pin = Kind.Album, id = 2)
        assertThrows(IllegalArgumentException::class.java) { sheetWords(album, false, true, listOf(SheetExtra(UNPIN_QP) { null })) }
        assertEquals(sheetItems(artist, false) + "pick image", sheetWords(artist, false, false, listOf(pickImage)))
    }
}
