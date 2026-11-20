package net.seilent.funekit.screens.host

import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Row
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PageId
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.PushFrom
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicEntryTest {
    private class Cells(e: Long, p: PageId) : PageModel(e, p) {
        var shown = setOf<Long>()

        override fun cell(album: Long): Boolean = album in shown
    }

    private val models = PageModels { e, p -> Cells(e, p) }
    private val covered = Row(ALBUM, Kind.Album, "Cobalt Rooms", "", ArtRef.Cover(TRACK, "cover.jpg"))
    private val bare = covered.copy(art = null)
    private val home = setOf("music", "playlists", "history", "apps", "settings")

    private fun kind(from: MusicEntry, to: MusicEntry?) = musicPopKind(Turn(MoveKind.None, from, to, popping = true), { it }, models, home::contains)

    private fun top(page: MusicPage, shown: Set<Long> = emptySet(), pivot: Int = 0): MusicEntry {
        val m = models.enter(ENTRY, page) as Cells
        m.shown = shown
        m.pivot = pivot
        return MusicEntry(ENTRY, page)
    }

    @Test
    fun theRootPopFollowsTheHeaderWordAndNowPlayingPopsWithT8() {
        for (p in listOf(MusicPage.Hub, MusicPage.Playlists, MusicPage.History)) assertEquals(MoveKind.T4h, kind(MusicEntry(1, p), null))
        for (p in listOf(MusicPage.Search("a"), MusicPage.Queue, MusicPage.Album(5), MusicPage.Artist(3))) assertEquals(MoveKind.T4n, kind(MusicEntry(1, p), null))
        assertEquals(MoveKind.T8, kind(MusicEntry(1, MusicPage.NowPlaying), MusicEntry(0, MusicPage.Hub)))
        assertEquals(MoveKind.T8, kind(MusicEntry(1, MusicPage.NowPlaying), null))
        assertNull(musicPopKind(Turn(MoveKind.None, null, MusicEntry(0, MusicPage.Hub), popping = true), { it }, models, home::contains))
    }

    @Test
    fun aPopKindComesFromTheStackEntryAndThePageUnderAtPopTime() {
        var next = 0L
        fun push(page: MusicPage, kind: MoveKind, top: MusicEntry?) = musicEntry(next++, page, kind, top, models).also { models.enter(it.entry, it.page) }
        fun cells(s: MusicEntry, vararg albums: Long) {
            (models[s.entry] as Cells).shown = albums.toSet()
        }
        val hub = push(MusicPage.Hub, MoveKind.T2, null)
        assertNull(hub.from)
        val artist = push(MusicPage.Artist(3), MoveKind.T3, hub)
        assertEquals(PushFrom(hub.entry, 0, "albums"), artist.from)
        assertEquals(MoveKind.T4, kind(artist, hub))

        val byTile = push(MusicPage.Album(5), MoveKind.T3a, artist)
        assertTrue(byTile.tile)
        assertEquals(PushFrom(artist.entry, 0, "albums"), byTile.from)
        cells(artist, 5)
        assertEquals(MoveKind.T4a, kind(byTile, artist))
        cells(artist)
        assertEquals(MoveKind.T4m, kind(byTile, artist))
        val byRow = push(MusicPage.Album(6), MoveKind.T3, artist)
        assertFalse(byRow.tile)
        assertEquals(MoveKind.T4m, kind(byRow, artist))
        requireNotNull(models[artist.entry]).pivot = 1
        assertEquals(MoveKind.T4, kind(byRow, artist))
        assertEquals(MoveKind.T4, kind(byTile, artist))

        val col = push(MusicPage.AlbumCol(0), MoveKind.T3c, hub)
        assertEquals(PushFrom(hub.entry, 0, "albums"), col.from)
        val fromCol = push(MusicPage.Album(8), MoveKind.T3a, col)
        assertEquals(PushFrom(col.entry, -1, ""), fromCol.from)
        cells(col, 8)
        assertEquals(MoveKind.T4a, kind(fromCol, col))
        cells(col)
        assertEquals(MoveKind.T4, kind(fromCol, col))
        assertEquals(MoveKind.T4m, kind(col, hub))

        val fromGrid = push(MusicPage.Album(9), MoveKind.T3m, hub)
        assertTrue(fromGrid.tile)
        assertEquals(MoveKind.T4m, kind(fromGrid, hub))
        requireNotNull(models[hub.entry]).pivot = HUB_SONGS
        assertEquals(MoveKind.T4, kind(fromGrid, hub))
        assertEquals(MoveKind.T4, kind(col, hub))
        val bySong = push(MusicPage.Album(10), MoveKind.T3, hub)
        assertEquals(PushFrom(hub.entry, HUB_SONGS, "songs"), bySong.from)
        assertEquals(MoveKind.T4, kind(bySong, hub))
        assertEquals(MoveKind.T4, kind(byRow, hub))
    }

    @Test
    fun aColumnPushIsT3cFromTheHubPickAndT3Else() {
        assertEquals(MoveKind.T3c, colPushKind(true))
        assertEquals(MoveKind.T3, colPushKind(false))
    }

    @Test
    fun anAlbumPushTakesItsKindFromTheSourceInFuneOsOrder() {
        val artist = MusicPage.Artist(ARTIST)
        assertEquals(MoveKind.T3z, albumPushKind(top(artist), models, bare, false))
        assertEquals(MoveKind.T3z, albumPushKind(top(artist, setOf(ALBUM)), models, bare, false))
        assertEquals(MoveKind.T3a, albumPushKind(top(artist, setOf(ALBUM)), models, covered, false))
        assertEquals(MoveKind.T3, albumPushKind(top(artist), models, covered, false))
        val col = MusicPage.AlbumCol(LETTER)
        assertEquals(MoveKind.T3a, albumPushKind(top(col, setOf(ALBUM)), models, bare, false))
        assertEquals(MoveKind.T3, albumPushKind(top(col), models, covered, false))
        assertEquals(MoveKind.T3m, albumPushKind(top(MusicPage.Hub, pivot = HUB_ALBUMS), models, covered, true))
        assertEquals(MoveKind.T3, albumPushKind(top(MusicPage.Hub, pivot = HUB_ALBUMS), models, covered, false))
        assertEquals(MoveKind.T3, albumPushKind(top(MusicPage.Hub, pivot = HUB_ARTISTS), models, covered, true))
        assertEquals(MoveKind.T3, albumPushKind(null, models, covered, true))
    }

    @Test
    fun anAlbumPopsT4zToTheArtistWithACoverlessHeadAndT4mFromTheGridAfterIt() {
        val artist = MusicEntry(UNDER, MusicPage.Artist(ARTIST))
        models.enter(UNDER, artist.page)
        val from = PushFrom(UNDER, 0, ALBUMS_WORD)
        assertEquals(MoveKind.T4z, musicPop(MusicEntry(ENTRY, MusicPage.Album(ALBUM), from, false, bare), artist, models[UNDER]))
        assertEquals(MoveKind.T4m, musicPop(MusicEntry(ENTRY, MusicPage.Album(ALBUM), from, false, covered), artist, models[UNDER]))
        assertEquals(MoveKind.T4m, musicPop(MusicEntry(ENTRY, MusicPage.Album(ALBUM), from, false, null), artist, models[UNDER]))
        (models[UNDER] as Cells).shown = setOf(ALBUM)
        assertEquals(MoveKind.T4a, musicPop(MusicEntry(ENTRY, MusicPage.Album(ALBUM), from, true, covered), artist, models[UNDER]))
    }

    @Test
    fun aTileEntryOverAPageWithNoLoadedCellPopsT4mFromTheArtistAndT4FromTheColumn() {
        val artist = MusicEntry(UNDER, MusicPage.Artist(ARTIST))
        models.enter(UNDER, artist.page)
        assertEquals(MoveKind.T4m, musicPop(MusicEntry(ENTRY, MusicPage.Album(ALBUM), PushFrom(UNDER, 0, ALBUMS_WORD), true, covered), artist, models[UNDER]))
        val col = MusicEntry(UNDER, MusicPage.AlbumCol(LETTER))
        models.enter(UNDER, col.page)
        assertEquals(MoveKind.T4, musicPop(MusicEntry(ENTRY, MusicPage.Album(ALBUM), PushFrom(UNDER, -1, ""), true, covered), col, models[UNDER]))
        (models[UNDER] as Cells).shown = setOf(ALBUM)
        assertEquals(MoveKind.T4a, musicPop(MusicEntry(ENTRY, MusicPage.Album(ALBUM), PushFrom(UNDER, -1, ""), true, covered), col, models[UNDER]))
    }

    @Test
    fun theColumnPickFiresOnceOnlyAtRestOnTopWithNoOverlayOnAPresentSection() {
        val h = HubModel(UNDER, MusicPage.Hub, HubState())
        h.select(HUB_ALBUMS)
        h.colPick = LETTER
        assertEquals(-1, colFire(h, true, true, true))
        assertEquals(-1, h.colPick)
        h.colPick = LETTER
        assertEquals(-1, colFire(h, false, true, false))
        h.colPick = LETTER
        assertEquals("no map yet, so the section is not present", -1, colFire(h, true, true, false))
        assertEquals(-1, colFire(h, true, true, false))
    }

    @Test
    fun aMusicEntryCarriesItsSourceRowAndHead() {
        val hub = MusicEntry(UNDER, MusicPage.Hub)
        models.enter(UNDER, hub.page)
        val e = musicEntry(ENTRY, MusicPage.Album(ALBUM), MoveKind.T3m, hub, models, ROW, covered)
        assertEquals(ROW, e.from?.row)
        assertEquals(covered, e.head)
        assertEquals(true, e.tile)
        assertNull(musicEntry(ENTRY, MusicPage.Album(ALBUM), MoveKind.T3, null, models).from)
    }

    companion object {
        private const val ENTRY = 4L
        private const val UNDER = 3L
        private const val ALBUM = 20L
        private const val ARTIST = 6L
        private const val TRACK = 77L
        private const val LETTER = 5
        private const val ROW = 3
        private const val ALBUMS_WORD = "albums"
    }
}
