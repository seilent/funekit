package net.seilent.funekit.screens.music.hub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.core.Section
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryCaps
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.TrackSet
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.Anchor
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.hub.FAVS_CELL
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_COUNT
import net.seilent.funekit.screens.music.hub.HUB_FOLDERS
import net.seilent.funekit.screens.music.hub.HUB_GENRES
import net.seilent.funekit.screens.music.hub.HUB_PLAYLISTS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubAct
import net.seilent.funekit.screens.music.hub.HubGeo
import net.seilent.funekit.screens.music.hub.HubGrid
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPick
import net.seilent.funekit.screens.music.hub.HubPlaying
import net.seilent.funekit.screens.music.hub.LABEL_CELL
import net.seilent.funekit.screens.music.hub.NOW_CELL
import net.seilent.funekit.screens.music.hub.NO_CELL
import net.seilent.funekit.screens.music.hub.PATH_DOTS
import net.seilent.funekit.screens.music.hub.fitPath
import net.seilent.funekit.screens.music.hub.gridCols
import net.seilent.funekit.screens.music.hub.hubLine
import net.seilent.funekit.screens.music.hub.hubMap
import net.seilent.funekit.screens.music.hub.hubJumpScroll
import net.seilent.funekit.screens.music.hub.hubPick
import net.seilent.funekit.screens.music.hub.hubPickAtDrop
import net.seilent.funekit.screens.music.hub.hubPicked
import net.seilent.funekit.screens.music.hub.hubScrollInto
import net.seilent.funekit.screens.music.hub.letterOf
import net.seilent.funekit.screens.music.hub.rowModel
import net.seilent.funekit.screens.music.overlay.SheetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs

class HubTest {
    private val none = Insets(0, 0, 0, 0)

    private fun page(w: Int, h: Int, large: Boolean = false): Scale = scale(Window(w, h, SDK, none, none, none, none, none), Nav.None, Surface.Page, large)

    private fun fake(set: FakeSet = FakeSet.Demo) = Fake(CoroutineScope(EmptyCoroutineContext), { 0L }, set, 0L, LEASE_MS)

    private fun blind(v: LibraryView): LibraryView = object : LibraryView by v {
        override suspend fun indexOf(listing: Listing, kind: Kind, id: Long): Int? = throw UnsupportedOperationException()
    }

    private fun loaded(f: Fake, pivot: Int): Pair<HubModel, LibraryView> = runBlocking {
        val m = HubModel(0L)
        m.select(pivot)
        m.cols = GRID_COLS
        val v = f.library.view()
        m.load(v, pivot)
        m.show(v, pivot, 0, WINDOW_ROWS)
        m to v
    }

    @Test
    fun theGridCellsAndRowPitchFollowTheBoard() {
        val boards = listOf(page(V1_W, V1_H) to 1, page(R1_W, R1_H) to 2, page(A105_W, A105_H) to 3)
        for ((s, k) in boards) {
            val g = HubGrid(s)
            assertEquals(GRID_COLS, g.cols)
            assertEquals(listOf(7 * k, Math.round(87.5f * k), 168 * k), (0 until GRID_COLS).map { g.x(it) })
            assertEquals(65 * k, g.tile)
            assertEquals(101 * k, g.pitch)
            assertEquals(89 * k, g.cellH)
            assertEquals(11 * k, g.drop)
            assertEquals(2 * k, g.border)
            if (k > 1) {
                assertEquals(22 * k, g.sub)
                assertEquals(24 * k, g.band)
            }
        }
        val large = HubGrid(page(A105_W, A105_H, true))
        assertEquals(317, large.pitch)
        assertEquals(281, large.cellH)
        assertEquals(195, large.tile)
    }

    @Test
    fun theV1GridTakesItsOwnCaptionPitchAndBand() {
        val g = HubGrid(page(V1_W, V1_H))
        assertEquals("caption pitch 12 u", 12, g.sub - g.drop)
        assertEquals("caption band 27 u", 27, g.band)
        val r1 = HubGrid(page(R1_W, R1_H))
        assertEquals("R1 caption pitch 11 u", 22, r1.sub - r1.drop)
        assertEquals("R1 caption band 24 u", 48, r1.band)
    }

    @Test
    fun theGridColumnsComeFromTheWidth() {
        assertEquals(3, gridCols(240f))
        assertEquals(3, gridCols(320.4f))
        assertEquals(4, gridCols(320.5f))
        assertEquals(4, gridCols(364.27f))
        assertEquals(5, gridCols(401f))
    }

    @Test
    fun aSectionMapPutsADividerBeforeEachLetterRun() {
        val m = hubMap(byteArrayOf(0, 0, 1, 1, 1, 3))
        assertEquals(9, m.count)
        assertEquals(0, letterOf(m.at(0)))
        assertEquals(0, m.at(1))
        assertTrue(m.isLetter(3))
        assertEquals(3, m.rowOf(1))
        assertEquals(7, m.rowOf(3))
        assertFalse(m.has(2))
        assertEquals(8, m.rowOfIndex(5))
        assertEquals(0..1, m.indices(0, 3))
        val g = hubMap(byteArrayOf(0, 0, 1, 1, 1, 3), 3)
        assertEquals(3, g.count)
        assertEquals(1, g.rowOf(1))
        assertEquals(0, g.colOf(1))
        assertEquals(2, g.rowOf(3))
        assertEquals(1, g.colOf(3))
        assertEquals(3, g.cells(2))
        assertEquals(5, g.at(2, 2))
        val f = hubMap(byteArrayOf(1, 1, 2, 2), 1, listOf(LABEL_CELL), 2)
        assertEquals(listOf(LABEL_CELL, -1, 0, 1, 2, 3), (0 until f.count).map { r -> f.at(r).let { if (letterOf(it) >= 0) -1 else it } })
        assertEquals(1, f.rowOf(1))
        assertFalse(f.has(2))
    }

    @Test
    fun theStatusLineTakesTheScanThenTheEmptyText() {
        assertEquals("scanning 12 songs", hubLine(HUB_ALBUMS, true, 12, 0, 0, 1L))
        assertEquals("scanning 1 song", hubLine(HUB_GENRES, true, 1, 0, 0, 1L))
        assertEquals("no albums", hubLine(HUB_ALBUMS, false, 0, 0, 0, 1L))
        assertEquals("no folders", hubLine(HUB_FOLDERS, false, 0, 0, 0, 1L))
        assertEquals("no genres", hubLine(HUB_GENRES, false, 0, 10, 0, 1L))
        assertEquals("", hubLine(HUB_ARTISTS, false, 0, 10, 0, 1L))
        assertEquals("", hubLine(HUB_PLAYLISTS, false, 0, 0, 0, 1L))
        assertEquals("", hubLine(HUB_SONGS, false, 0, -1, 0, 1L))
        assertEquals("", hubLine(HUB_SONGS, true, 4, 10, 30, 1L))
    }

    @Test
    fun aPathTooWideForTheLineKeepsItsTailAfterTheDots() {
        val w: (String) -> Int = { it.length * PEN }
        val path = "aaaaa / bb / ccc"
        assertEquals(16, path.length)
        assertEquals(path, fitPath(path, 16 * PEN, PATH_DOTS, w))
        assertEquals("... / bb / ccc", fitPath(path, 15 * PEN, PATH_DOTS, w))
        assertEquals("... / bb / ccc", fitPath(path, 14 * PEN, PATH_DOTS, w))
        assertEquals("... / ccc", fitPath(path, 13 * PEN, PATH_DOTS, w))
        assertEquals("... / ccc", fitPath(path, 9 * PEN, PATH_DOTS, w))
        assertEquals("ccc", fitPath(path, 8 * PEN, PATH_DOTS, w))
        assertEquals("ccc", fitPath(path, 2 * PEN, PATH_DOTS, w))
        assertEquals("abcdef", fitPath("abcdef", 2 * PEN, PATH_DOTS, w))
    }

    @Test
    fun anAlbumsPickWaitsForTheColumnPageAndOtherPivotsScrollInPlace() {
        assertEquals(HubPick.Defer, hubPick(HUB_ALBUMS))
        listOf(HUB_ARTISTS, HUB_PLAYLISTS, HUB_SONGS, HUB_GENRES, HUB_FOLDERS).forEach { assertEquals(HubPick.Scroll, hubPick(it)) }
        val f = fake()
        val (albums, _) = loaded(f, HUB_ALBUMS)
        hubPicked(albums, SECTION_B, 0L)
        assertEquals(0, albums.pane.focus.row)
        val (songs, v) = loaded(f, HUB_SONGS)
        hubPicked(songs, SECTION_B, 0L)
        assertEquals(SECTION_B, songs.jumpPick)
        assertEquals(0, songs.pane.focus.row)
        hubPickAtDrop(songs, 0L)
        assertEquals(-1, songs.jumpPick)
        val s = runBlocking { v.sections(Listing.Songs) }
        val dividers = s.indices.filter { it == 0 || s[it] != s[it - 1] }
        val b = dividers.indexOfFirst { s[it].toInt() == SECTION_B }
        assertTrue(b > 0)
        assertEquals(dividers[b] + b, songs.pane.focus.row)
    }

    @Test
    fun aDragThatCommitsCarriesItsOffsetOutAtThePaneSpeed() {
        val (m, _) = loaded(fake(), HUB_ARTISTS)
        m.drag(DRAG_U, 0L)
        assertEquals(Math.round(DRAG_U * PPU), m.dragX(0L, PPU))
        assertTrue(m.switchTo(m.pivot + 1, T0))
        val e = OUT_MS
        assertEquals(Math.round((DRAG_U - Tok.motion_pivotPaneSpeed * e / MS_PER_S) * PPU), m.motion.dx(T0 + e, PPU))
        assertEquals("the live pane is not shifted while the sides switch", 0, m.dragX(T0 + e, PPU))
    }

    @Test
    fun anUncommittedDragReturnsToRestOverTheStrip() {
        val (m, _) = loaded(fake(), HUB_ARTISTS)
        m.drag(DRAG_U, 0L)
        m.release(T0)
        assertEquals(Math.round(DRAG_U * PPU), m.dragX(T0, PPU))
        val end = T0 + Tok.motion_pivotStrip.toLong()
        assertEquals(0, m.dragX(end, PPU))
        assertFalse(m.motion.active(end))
        assertEquals(HUB_ARTISTS, m.pivot)
    }

    @Test
    fun theContentFormFollowsThePivot() {
        val m = HubModel(0L)
        assertEquals(PageKind.Grid, m.kind)
        for (p in listOf(HUB_ARTISTS, HUB_PLAYLISTS, HUB_SONGS, HUB_GENRES, HUB_FOLDERS)) {
            m.select(p)
            assertEquals(PageKind.Hub, m.kind)
        }
    }

    @Test
    fun eachPivotKeepsItsOwnFocus() {
        val f = fake()
        val (m, v) = loaded(f, HUB_SONGS)
        runBlocking { m.load(v, HUB_ARTISTS) }
        m.pane.setFocus(FOCUS_ROW, 0, 0L, false)
        assertTrue(m.switchTo(HUB_ARTISTS, 0L))
        assertEquals(0, m.pane.focus.row)
        assertEquals(FOCUS_ROW, m.panes[HUB_SONGS].focus.row)
        assertTrue(m.switching(1L))
        assertFalse(m.switching(HubModel.switchEnd()))
    }

    @Test
    fun aSongRowPlaysTheSongsFromItsIndexAndAPlayingTrackLightsItsRow() {
        val f = fake()
        val (m, _) = loaded(f, HUB_SONGS)
        val index = m.pane.cell(PICKED, 0)
        assertTrue(index >= 0)
        assertEquals(HubAct.Play(Listing.Songs, index), m.act(HUB_SONGS, PICKED, 0, false))
        assertEquals(HubAct.Jump(0), m.act(HUB_SONGS, 0, 0, false))
        val row = requireNotNull(m.pane.row(index))
        m.playing = HubPlaying(row.id)
        assertTrue(m.rowModel(HUB_SONGS, PICKED)?.accent == true)
        assertFalse(m.rowModel(HUB_SONGS, PICKED + 1)?.accent == true)
        val r = requireNotNull(m.rowModel(HUB_SONGS, PICKED))
        assertTrue(r.song)
        assertEquals("", r.number)
        assertTrue(m.rowModel(HUB_SONGS, 0)?.song == true)
    }

    @Test
    fun anArtistRowRingPlaysItsSongsAndTheRowItselfOpensTheArtist() {
        val f = fake()
        val (m, _) = loaded(f, HUB_ARTISTS)
        val id = m.pane.row(m.pane.cell(1, 0))!!.id
        assertEquals(HubAct.Play(Listing.ArtistTracks(id), 0), m.act(HUB_ARTISTS, 1, 0, true))
        assertEquals(HubAct.Artist(id), m.act(HUB_ARTISTS, 1, 0, false))
    }

    @Test
    fun thePlaylistsPivotLeadsWithNowPlayingAndFavorites() = runBlocking {
        val f = fake()
        val (m, v) = loaded(f, HUB_PLAYLISTS)
        assertEquals(NOW_CELL, m.pane.cell(0, 0))
        assertEquals(FAVS_CELL, m.pane.cell(1, 0))
        assertEquals(v.page(Listing.Playlists, 0, 0).total + 2, m.pane.map!!.count)
        assertEquals(HubAct.NowPlaying, m.act(HUB_PLAYLISTS, 0, 0, false))
        assertEquals(HubAct.Play(Listing.Favorites, -1), m.act(HUB_PLAYLISTS, 1, 0, false))
        assertEquals("favorites", m.rowModel(HUB_PLAYLISTS, 1)?.primary)
    }

    @Test
    fun theRowsNoSheetTargetsPlayWholeAndTheTargetsAtTheirOwnIndex() = runBlocking {
        val f = fake()
        val (lists, _) = loaded(f, HUB_PLAYLISTS)
        val list = checkNotNull(lists.pane.row(lists.pane.cell(2, 0)))
        assertEquals(Kind.Playlist, list.kind)
        assertEquals(HubAct.Play(Listing.PlaylistTracks(list.id), -1), lists.act(HUB_PLAYLISTS, 2, 0, false))
        assertEquals(HubAct.Play(Listing.Favorites, -1), lists.act(HUB_PLAYLISTS, 1, 0, false))
        val (genres, _) = loaded(f, HUB_GENRES)
        val genre = checkNotNull(genres.pane.row(genres.pane.cell(0, 0)))
        assertEquals(HubAct.Play(Listing.GenreTracks(genre.id), -1), genres.act(HUB_GENRES, 0, 0, false))
        val (songs, _) = loaded(f, HUB_SONGS)
        val index = songs.pane.cell(PICKED, 0)
        assertEquals(HubAct.Play(Listing.Songs, index), songs.act(HUB_SONGS, PICKED, 0, false))
        val (artists, _) = loaded(f, HUB_ARTISTS)
        val artist = checkNotNull(artists.pane.row(artists.pane.cell(1, 0))).id
        assertEquals(HubAct.Play(Listing.ArtistTracks(artist), 0), artists.act(HUB_ARTISTS, 1, 0, true))
    }

    @Test
    fun withOneRootTheFolderPivotOpensOnThatRootsContents() = runBlocking {
        val f = fake()
        val (m, v) = loaded(f, HUB_FOLDERS)
        val roots = v.page(Listing.Folder(0L), 0, 1)
        assertEquals(1, roots.total)
        val root = roots.rows.single()
        assertEquals(Kind.Folder, root.kind)
        assertEquals(root.id, m.folder)
        assertTrue(m.atRoot)
        assertEquals(Listing.Folder(root.id), m.pane.listing)
        assertTrue(m.path.isEmpty())
        assertFalse(m.onLabel(HUB_FOLDERS, 0))
        val inside = v.page(Listing.Folder(root.id), 0, 0).total
        assertEquals(inside, m.pane.whole!!.size)
        assertTrue((0 until m.pane.map!!.count).none { m.pane.row(m.pane.map!!.indexAt(it))?.id == root.id })
        assertFalse(m.up())
    }

    @Test
    fun withTwoRootsTheFolderPivotListsTheRoots() = runBlocking {
        val f = fake(FakeSet.Main)
        val (m, v) = loaded(f, HUB_FOLDERS)
        val roots = v.page(Listing.Folder(0L), 0, 0).total
        assertEquals(2, roots)
        assertEquals(0L, m.folder)
        assertTrue(m.atRoot)
        assertEquals(Listing.Folder(0L), m.pane.listing)
        assertEquals(roots, m.pane.whole!!.size)
        assertTrue(m.pane.whole!!.all { it.kind == Kind.Folder })
    }

    @Test
    fun aFolderEntersWithItsPathLineAndUpReturnsToTheFolderItCameFrom() = runBlocking {
        val f = fake()
        val (m, v) = loaded(f, HUB_FOLDERS)
        val base = m.folder
        val map = m.pane.map!!
        val row = (0 until map.count).first { m.pane.row(map.indexAt(it))?.kind == Kind.Folder }
        val folder = m.pane.row(map.indexAt(row))!!
        val act = m.act(HUB_FOLDERS, row, 0, false)
        assertEquals(HubAct.Enter(folder.id, folder.title), act)
        m.enter(folder.id, folder.title)
        m.load(v, HUB_FOLDERS)
        assertEquals(listOf(folder.title), m.path)
        assertEquals(LABEL_CELL, m.pane.cell(0, 0))
        assertTrue(m.onLabel(HUB_FOLDERS, 0))
        assertEquals(0, m.settleFolder(VIEW, PITCH, 0L))
        assertEquals(1, m.pane.focus.row)
        assertTrue(m.up())
        assertEquals(base, m.folder)
        m.load(v, HUB_FOLDERS)
        m.settleFolder(VIEW, PITCH, 0L)
        assertEquals(row, m.pane.focus.row)
        assertFalse(m.up())
    }

    private fun dividedRow(s: ByteArray, index: Int): Int = index + (0..index).count { it == 0 || s[it] != s[it - 1] }

    @Test
    fun aNewGenerationKeepsTheAnchorRowWhenTheAnchorSurvives() = runBlocking {
        val f = fake(FakeSet.Main)
        val (m, v) = loaded(f, HUB_ARTISTS)
        val gone = f.library.roots.value.last().uri
        val total = v.page(Listing.Artists, 0, 0).total
        val artists = v.page(Listing.Artists, 0, total).rows
        f.library.removeRoot(gone)
        val v2 = f.library.view()
        assertTrue(v2.generation > v.generation)
        val old = (total / 2 until total).first { v2.indexOf(Listing.Artists, artists[it].kind, artists[it].id) != null }
        val r = artists[old]
        val first = dividedRow(v.sections(Listing.Artists), old)
        m.show(v, HUB_ARTISTS, first, first + WINDOW_ROWS)
        assertEquals(r.id, m.pane.row(m.pane.map!!.indexAt(first))?.id)
        val to = m.migrate(v2, HUB_ARTISTS, first)
        val now = requireNotNull(v2.indexOf(Listing.Artists, r.kind, r.id))
        assertEquals(dividedRow(v2.sections(Listing.Artists), now), to)
        assertEquals(v2.generation, m.panes[HUB_ARTISTS].gen)
    }

    @Test
    fun aNewGenerationWhoseAnchorIsGoneKeepsItsIndexClampedToTheNewTotal() = runBlocking {
        val f = fake(FakeSet.Main)
        val (m, v) = loaded(f, HUB_SONGS)
        val gone = f.library.roots.value.last().uri
        val total = v.page(Listing.Songs, 0, 0).total
        val songs = v.page(Listing.Songs, 0, total).rows
        f.library.removeRoot(gone)
        val v2 = f.library.view()
        val left = v2.page(Listing.Songs, 0, 0).total
        assertTrue(left < total)
        val old = (total - 1 downTo left).first { v2.indexOf(Listing.Songs, songs[it].kind, songs[it].id) == null }
        assertTrue(old >= left)
        val first = dividedRow(v.sections(Listing.Songs), old)
        m.show(v, HUB_SONGS, first, first + WINDOW_ROWS)
        assertEquals(songs[old].id, m.pane.row(m.pane.map!!.indexAt(first))?.id)
        val to = m.migrate(v2, HUB_SONGS, first)
        assertEquals(dividedRow(v2.sections(Listing.Songs), left - 1), to)
    }

    @Test
    fun aNewGenerationOnAnUnindexedSongsPaneKeepsTheOldIndexClampedToTheNewTotal() = runBlocking {
        val f = fake(FakeSet.Main)
        val (m, v) = loaded(f, HUB_SONGS)
        val total = v.page(Listing.Songs, 0, 0).total
        f.library.removeRoot(f.library.roots.value.last().uri)
        val v2 = f.library.view()
        val left = v2.page(Listing.Songs, 0, 0).total
        assertTrue("premise: the root change drops songs", left < total)
        val first = dividedRow(v.sections(Listing.Songs), total - 1)
        m.show(v, HUB_SONGS, first, first + WINDOW_ROWS)
        val to = m.migrate(blind(v2), HUB_SONGS, first, LibraryCaps(unindexed = setOf(Listing.Songs::class)))
        assertEquals(dividedRow(v2.sections(Listing.Songs), left - 1), to)
    }

    @Test
    fun aNewGenerationOnAnUnindexedFoldersPaneKeepsTheOldIndex() = runBlocking {
        val f = fake()
        val (m, _) = loaded(f, HUB_FOLDERS)
        val old = requireNotNull(m.pane.whole).lastIndex
        val first = m.pane.map!!.rowOfIndex(old)
        val g = f.library.generation.value
        f.library.rescan()
        f.library.generation.first { it > g }
        val to = m.migrate(blind(f.library.view()), HUB_FOLDERS, first, LibraryCaps(unindexed = setOf(Listing.Folder::class)))
        assertEquals(old, requireNotNull(m.pane.whole).lastIndex)
        assertEquals(m.pane.map!!.rowOfIndex(old), to)
    }

    @Test
    fun aPlayFromAPaneOfAnExpiredGenerationIsDropped() = runBlocking {
        var now = 0L
        val f = Fake(CoroutineScope(EmptyCoroutineContext), { now }, FakeSet.Main, 0L, LEASE_MS)
        val src = MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)
        val (m, v) = loaded(f, HUB_SONGS)
        val act = m.act(HUB_SONGS, PICKED, 0, false) as HubAct.Play
        f.library.removeRoot(f.library.roots.value.last().uri)
        assertTrue(f.library.generation.value > v.generation)
        now += LEASE_MS * NANOS_PER_MS
        assertTrue(runCatching { m.play(act, src) }.exceptionOrNull() is Expired)
        assertEquals(null, f.player.state.value.track)
    }

    @Test
    fun aWindowAtThePaneGenerationReadsThatSnapshot() = runBlocking {
        val f = fake(FakeSet.Main)
        val (m, v) = loaded(f, HUB_SONGS)
        val total = v.page(Listing.Songs, 0, 0).total
        f.library.removeRoot(f.library.roots.value.last().uri)
        assertTrue(f.library.view().page(Listing.Songs, 0, 0).total < total)
        val far = m.pane.map!!.count - WINDOW_ROWS
        m.show(f.library.view(m.pane.gen), HUB_SONGS, far, far + WINDOW_ROWS)
        val w = requireNotNull(m.pane.paging.window)
        assertEquals(v.generation, w.generation)
        assertEquals(total, w.total)
    }

    @Test
    fun pnGenIsSetWithTheMap() = runBlocking {
        val f = fake()
        for (p in listOf(HUB_ALBUMS, HUB_SONGS, HUB_PLAYLISTS, HUB_FOLDERS)) {
            val m = HubModel(0L).also { it.select(p) }
            assertEquals(-1L, m.pane.gen)
            val v = f.library.view()
            m.load(v, p)
            assertTrue(m.pane.map != null)
            assertEquals(v.generation, m.pane.gen)
        }
    }

    @Test
    fun thumbStripsReadAtMostTwiceTheSlots() = runBlocking {
        val f = fake()
        val (m, v) = loaded(f, HUB_ARTISTS)
        val asked = ArrayList<Int>()
        val counted = object : LibraryView by v {
            override suspend fun page(listing: Listing, offset: Int, count: Int): Page {
                if (listing is Listing.ArtistAlbums) asked += count
                return v.page(listing, offset, count)
            }
        }
        m.thumbs(counted, HUB_ARTISTS, 0, WINDOW_ROWS, MOST)
        assertTrue(asked.isNotEmpty())
        assertTrue(asked.all { it == MOST })
        assertTrue(m.pane.thumbs.values.all { it.size <= MOST })
    }

    @Test
    fun aScanWithNoReadyGenerationShowsTheScanLine() {
        val m = HubModel(0L)
        m.generation = 0L
        m.scanning = true
        m.found = FOUND
        m.songs = SONGS_BEFORE
        assertEquals("scanning $FOUND songs", hubLine(HUB_ALBUMS, m.scanning, m.found, m.songs, 0, m.generation))
        assertEquals("", hubLine(HUB_ALBUMS, m.scanning, m.found, m.songs, 0, 1L))
    }

    @Test
    fun noLineBeforeTheFirstReadyGenerationWithNoScan() {
        val m = HubModel(0L)
        m.generation = 0L
        assertEquals(-1, m.songs)
        for (p in 0 until HUB_COUNT) assertEquals("", hubLine(p, false, 0, m.songs, m.panes[p].map?.count ?: 0, m.generation))
    }

    @Test
    fun aTapScrollsItsRowIntoTheView() {
        assertEquals(0, hubScrollInto(0, PITCH, 0, VIEW))
        assertEquals(0, hubScrollInto(0, PITCH, PITCH, VIEW))
        assertEquals(PITCH * 2, hubScrollInto(PITCH * 2, PITCH, PITCH * 3, VIEW))
        assertEquals(PITCH * 8 - VIEW, hubScrollInto(PITCH * 7, PITCH, 0, VIEW))
        assertEquals(PITCH, hubScrollInto(PITCH * 2, PITCH, PITCH, VIEW))
        assertEquals(Section.count, m0().avail().size)
    }

    private fun m0() = HubModel(0L)

    private fun item(m: HubModel, p: Int, kind: Kind? = null): Int {
        val pn = m.panes[p]
        val map = checkNotNull(pn.map)
        return (0 until map.count).first { r ->
            val v = pn.cell(r, 0)
            v >= 0 && v != NO_CELL && letterOf(v) < 0 && (kind == null || pn.row(v)?.kind == kind)
        }
    }

    @Test
    fun eachPivotGivesItsSheetTargetAndDividersAndTheScanLineGiveNone() {
        val f = fake()
        val (artists, _) = loaded(f, HUB_ARTISTS)
        val ar = item(artists, HUB_ARTISTS)
        val at = checkNotNull(artists.target(HUB_ARTISTS, ar, 0))
        assertEquals(SheetKind.Artist, at.kind)
        assertEquals(Kind.Artist, at.pin)
        assertTrue(at.listing is Listing.ArtistTracks)
        val dividers = (0 until checkNotNull(artists.pane.map).count).filter { letterOf(artists.pane.cell(it, 0)) >= 0 }
        assertTrue(dividers.isNotEmpty())
        assertEquals(null, artists.target(HUB_ARTISTS, dividers.first(), 0))
        val (albums, _) = loaded(f, HUB_ALBUMS)
        val cell = (0 until checkNotNull(albums.pane.map).count).firstNotNullOf { r -> (0 until GRID_COLS).firstOrNull { c -> albums.pane.cell(r, c).let { it >= 0 && it != NO_CELL && letterOf(it) < 0 } }?.let { r to it } }
        assertEquals(SheetKind.Album, albums.target(HUB_ALBUMS, cell.first, cell.second)?.kind)
        val (songs, _) = loaded(f, HUB_SONGS)
        val sr = item(songs, HUB_SONGS)
        val st = checkNotNull(songs.target(HUB_SONGS, sr, 0))
        assertEquals(SheetKind.Song, st.kind)
        assertEquals(songs.pane.cell(sr, 0), st.start)
        assertEquals(Listing.Songs, st.listing)
        val (genres, _) = loaded(f, HUB_GENRES)
        assertEquals(SheetKind.Genre, genres.target(HUB_GENRES, item(genres, HUB_GENRES), 0)?.kind)
        val (lists, _) = loaded(f, HUB_PLAYLISTS)
        assertEquals(null, lists.target(HUB_PLAYLISTS, 0, 0))
        val fav = checkNotNull(lists.target(HUB_PLAYLISTS, 1, 0))
        assertEquals(SheetKind.Favorites, fav.kind)
        assertEquals(lists.pane.favorites, fav.songs)
        val (folders, fv) = loaded(f, HUB_FOLDERS)
        val fr = item(folders, HUB_FOLDERS, Kind.Folder)
        val ft = checkNotNull(folders.target(HUB_FOLDERS, fr, 0))
        assertEquals(SheetKind.Folder, ft.kind)
        var row = checkNotNull(folders.pane.row(folders.pane.cell(fr, 0)))
        while (true) {
            folders.enter(row.id, row.title)
            runBlocking { folders.load(fv, HUB_FOLDERS) }
            val rows = checkNotNull(folders.pane.whole)
            if (rows.any { it.kind == Kind.Song }) break
            row = rows.first { it.kind == Kind.Folder }
        }
        assertEquals(null, folders.target(HUB_FOLDERS, 0, 0))
        val song = checkNotNull(folders.target(HUB_FOLDERS, item(folders, HUB_FOLDERS, Kind.Song), 0))
        assertEquals(SheetKind.Song, song.kind)
        assertEquals(Listing.FolderTracks(row.id), song.listing)
        songs.scanning = true
        songs.songs = 0
        songs.generation = 1L
        assertEquals(null, songs.target(HUB_SONGS, sr, 0))
    }

    private fun relisted(m: HubModel, f: Fake) = runBlocking {
        val r = f.playlists.revision.value to f.favorites.revision.value
        m.revisions = r
        val pn = m.panes[HUB_PLAYLISTS]
        m.revised(r.first, r.second)
        if (pn.gen < 0) {
            val v = f.library.view()
            m.migrate(v, HUB_PLAYLISTS, 0)
            m.show(v, HUB_PLAYLISTS, 0, WINDOW_ROWS)
        }
    }

    @Test
    fun aDeletedPlaylistLeavesThePlaylistsPaneAtItsNextLoad() {
        val f = fake(FakeSet.Main)
        runBlocking {
            val song = TrackSet.Ids(listOf(f.library.view().page(Listing.Songs, 0, 1).rows.single().id))
            f.playlists.create(song)
            f.playlists.create(song)
        }
        val (m, _) = loaded(f, HUB_PLAYLISTS)
        m.revisions = f.playlists.revision.value to f.favorites.revision.value
        runBlocking { m.load(f.library.view(), HUB_PLAYLISTS) }
        m.revisions.let { assertEquals(it.first, m.panes[HUB_PLAYLISTS].lists) }
        val before = checkNotNull(m.pane.map).count
        val lists = runBlocking { f.library.view().page(Listing.Playlists, 0, 10).rows }
        assertEquals(5, lists.size)
        runBlocking { f.playlists.delete(lists[3].id) }
        relisted(m, f)
        val map = checkNotNull(m.pane.map)
        assertEquals(before - 1, map.count)
        val last = runBlocking { f.library.view().page(Listing.Playlists, 0, 10).rows.last() }
        assertEquals(last.id, m.pane.row(m.pane.cell(map.count - 1, 0))?.id)
    }

    @Test
    fun theFavoritesRowFollowsTheHeart() {
        val f = fake(FakeSet.Main)
        runBlocking {
            for (r in f.library.view().page(Listing.Favorites, 0, 100).rows) f.favorites.toggle(r.id)
        }
        val (m, _) = loaded(f, HUB_PLAYLISTS)
        relisted(m, f)
        assertEquals(HubAct.None, m.act(HUB_PLAYLISTS, 1, 0, false))
        val song = runBlocking { f.library.view().page(Listing.Songs, 0, 1).rows.single() }
        runBlocking { f.favorites.toggle(song.id) }
        relisted(m, f)
        assertEquals(1, m.panes[HUB_PLAYLISTS].favorites)
        assertEquals(HubAct.Play(Listing.Favorites, -1), m.act(HUB_PLAYLISTS, 1, 0, false))
        runBlocking { f.favorites.toggle(song.id) }
        relisted(m, f)
        assertEquals(HubAct.None, m.act(HUB_PLAYLISTS, 1, 0, false))
    }

    @Test
    fun aHubIsReadyOnceItsPaneHoldsItsFirstShownRow() = runBlocking {
        val f = fake()
        val v = f.library.view()
        val m = HubModel(0L)
        m.select(HUB_SONGS)
        assertFalse(m.ready)
        m.load(v, HUB_SONGS)
        assertFalse(m.ready)
        m.show(v, HUB_SONGS, 0, WINDOW_ROWS)
        assertTrue(m.ready)
        val pn = m.pane
        val count = pn.map!!.count
        pn.track.resize(PITCH, VIEW)
        pn.track.count(count, 0)
        pn.track.at(FAR_ROW * PITCH)
        assertEquals(FAR_ROW, pn.track.first)
        assertFalse(m.ready)
        m.show(v, HUB_SONGS, FAR_ROW, FAR_ROW + WINDOW_ROWS)
        assertTrue(m.ready)
        val e = HubModel(0L)
        e.select(HUB_SONGS)
        val ev = fake(FakeSet.Empty).library.view()
        assertFalse(e.ready)
        e.load(ev, HUB_SONGS)
        assertEquals(0, e.pane.map!!.count)
        assertTrue(e.ready)
    }

    @Test
    fun aPressOnARowScrolledOutOfTheListKeepsNoFrame() {
        val (m, _) = songsOn(page(R1_W, R1_H))
        val pn = m.pane
        pn.press.down(pn.item(0, 0), PRESS_AT)
        pn.track.at(pn.track.topOf(FAR_ROW))
        assertTrue("premise: the press still moves", pn.press.stale(PRESS_AT + PRESS_STEP))
        assertFalse(m.animating(PRESS_AT + PRESS_STEP))
        pn.track.at(0)
        assertTrue(m.animating(PRESS_AT + PRESS_STEP))
    }

    private fun songsOn(s: Scale): Pair<HubModel, LibraryView> {
        val (m, v) = loaded(fake(), HUB_SONGS)
        val pn = m.pane
        pn.track.resize(HubGeo(s).spec(HUB_SONGS))
        pn.track.count(pn.map!!.count, 0)
        pn.ppu = s.ppu
        return m to v
    }

    @Test
    fun onlyTheSongsPaneTakesThe16Dot16Pitch() {
        val g = HubGeo(page(R1_W, R1_H))
        for (p in 0 until HUB_COUNT) assertEquals("pivot $p", p != HUB_SONGS, g.spec(p).whole)
        assertTrue("FOLDERS settles on its whole px pitch", g.spec(HUB_FOLDERS).whole)
    }

    @Test
    fun anR1SongsTapTakesTheRowOfThe16Dot16Pitch() {
        val (m, _) = songsOn(page(R1_W, R1_H))
        val t = m.pane.track
        assertEquals(1, t.tapRow(R1_ROW1_LAST))
        assertEquals(2, t.tapRow(R1_ROW1_LAST + 1))
    }

    @Test
    fun anAnchorAtA16Dot16OffsetRestoresItsPlace() = runBlocking {
        val s = page(R1_W, R1_H)
        val (m, v) = songsOn(s)
        val t = m.pane.track
        t.at(ANCHOR_Q)
        m.show(v, HUB_SONGS, t.drawFirst, t.rowAt(ANCHOR_Q + t.view - 1))
        val a = requireNotNull(m.anchorAt(HUB_SONGS, t, s.ppu))
        val i = requireNotNull(v.indexOf(Listing.Songs, a.kind, a.id))
        assertEquals(i, a.index)
        val row = m.pane.map!!.rowOfIndex(i)
        assertEquals(ANCHOR_Q, t.topOf(row) + px(a.off, s.ppu))
    }

    @Test
    fun aRestoreOnAnUnindexedPaneTakesTheSavedIndexClampedToTheTotal() = runBlocking {
        val f = fake()
        val caps = LibraryCaps(unindexed = setOf(Listing.Songs::class))
        val total = f.library.view().page(Listing.Songs, 0, 0).total
        assertTrue("premise: the songs pane holds FAR_ROW", FAR_ROW < total)
        for ((saved, want) in listOf(FAR_ROW to FAR_ROW, total + FAR_ROW to total - 1, -1 to null)) {
            val (m, v) = loaded(f, HUB_SONGS)
            m.anchor = Anchor(Kind.Song, -1, 0f, saved)
            assertEquals("$saved", want?.let { m.pane.map!!.rowOfIndex(it) }, m.restoreRow(blind(v), HUB_SONGS, caps))
        }
    }

    @Test
    fun aSongsPaneOnThe16Dot16PitchIsReadyOnceItsWindowHoldsTheRowAtItsOffset() = runBlocking {
        val v = fake().library.view()
        val m = HubModel(0L)
        m.select(HUB_SONGS)
        m.load(v, HUB_SONGS)
        val t = m.pane.track
        t.resize(HubGeo(page(R1_W, R1_H)).spec(HUB_SONGS))
        t.count(m.pane.map!!.count, 0)
        t.at(t.topOf(FAR_ROW))
        assertEquals(FAR_ROW, t.first)
        assertFalse(m.ready)
        m.show(v, HUB_SONGS, FAR_ROW, FAR_ROW + WINDOW_ROWS)
        assertTrue(m.ready)
    }

    @Test
    fun afterAScrollOnThe16Dot16PitchTheFocusTakesTheFirstWholeRow() {
        val (m, _) = songsOn(page(R1_W, R1_H))
        val pn = m.pane
        pn.setFocus(0, 0, 0L, false)
        pn.track.at(pn.track.topOf(FAR_ROW))
        pn.afterScroll(PRESS_AT)
        assertEquals(FAR_ROW, pn.focus.row)
    }

    @Test
    fun aSongsTrackKeepsIts16Dot16PitchThroughARescaleAndBack() {
        val a105 = page(A105_W, A105_H)
        val r1 = page(R1_W, R1_H)
        val t = RowTrack(1, 0)
        t.resize(HubGeo(a105).spec(HUB_SONGS))
        t.rescale(a105.ppu, r1.ppu, HubGeo(r1).spec(HUB_SONGS))
        assertEquals(fx(SONG_U), t.pitch64)
        assertFalse(t.whole)
        t.rescale(r1.ppu, a105.ppu, HubGeo(a105).spec(HUB_SONGS))
        assertEquals(fx(SONG_U), t.pitch64)
        assertFalse(t.whole)
    }

    @Test
    fun aFolderUpOnItsRowSpecSettlesOnTheTrackTops() = runBlocking {
        val g = HubGeo(page(R1_W, R1_H))
        val (m, v) = loaded(fake(), HUB_FOLDERS)
        val pn = m.pane
        val t = pn.track
        t.resize(g.spec(HUB_FOLDERS))
        assertTrue("premise: FOLDERS keeps a whole px track", t.whole)
        val pitch = g.pitch(HUB_FOLDERS)
        fun base(): Int {
            val map = pn.map!!
            t.count(map.count, 0)
            return (0 until map.count).first { it >= FOLDER_ROW && pn.row(map.indexAt(it))?.kind == Kind.Folder }
        }
        suspend fun into(row: Int, q: Int) {
            val f = pn.row(pn.map!!.indexAt(row))!!
            pn.setFocus(row, 0, 0L, false)
            t.at(q)
            m.enter(f.id, f.title)
            m.load(v, HUB_FOLDERS)
            t.count(pn.map!!.count, 0)
            val lead = m.settleFolder(t.view, pitch, 0L)
            assertEquals(1, pn.focus.row)
            assertEquals(maxOf(0, t.topOf(2) - t.view), lead)
            assertTrue(m.up())
            m.load(v, HUB_FOLDERS)
        }
        val row = base()
        assertEquals(row * pitch, t.topOf(row))
        val shown = t.topOf(row) - pitch
        into(row, shown)
        assertEquals(row, base())
        assertEquals("the kept offset holds the row whole", shown, m.settleFolder(t.view, pitch, 0L))
        assertEquals(row, pn.focus.row)
        t.at(shown)
        assertTrue(t.topOf(row) >= t.q && t.topOf(row + 1) <= t.q + t.view)
        into(row, 0)
        base()
        assertEquals("the least scroll that shows the row", t.topOf(row + 1) - t.view, m.settleFolder(t.view, pitch, 0L))
        assertEquals(row, pn.focus.row)
    }

    @Test
    fun aJumpAimOnTheSongsPaneEndsAtItsDividerTopLessTheRest() {
        val s = page(R1_W, R1_H)
        val (m, _) = songsOn(s)
        val map = m.pane.map!!
        val t = m.pane.track
        val section = (0 until Section.count).first { map.has(it) && map.rowOf(it) >= JUMP_ROW }
        val row = map.rowOf(section)
        hubJumpScroll(m, section, 0L)
        val want = (t.topOf(row) - px(Tok.motion_jumpScrollRest, s.ppu)).coerceIn(0, t.max)
        assertTrue("aim ${m.aim}, want $want", abs(m.aim - want) <= 1)
    }

    companion object {
        private const val PRESS_AT = 10_000L
        private const val R1_ROW1_LAST = 212f
        private const val ANCHOR_Q = 1000
        private const val SONG_U = 53.2f
        private const val JUMP_ROW = 100
        private const val PRESS_STEP = 10L
        private const val SDK = 35
        private const val LEASE_MS = 60_000L
        private const val DRAG_U = 30f
        private const val PPU = 3f
        private const val T0 = 1_000L
        private const val OUT_MS = 100L
        private const val MS_PER_S = 1000f
        private const val GRID_COLS = 3
        private const val WINDOW_ROWS = 20
        private const val V1_W = 240
        private const val V1_H = 320
        private const val R1_W = 480
        private const val R1_H = 800
        private const val A105_W = 720
        private const val A105_H = 1280
        private const val PEN = 64
        private const val SECTION_B = 2
        private const val FOCUS_ROW = 5
        private const val PICKED = 3
        private const val VIEW = 920
        private const val PITCH = 120
        private const val NANOS_PER_MS = 1_000_000L
        private const val MOST = 3
        private const val FOUND = 12
        private const val SONGS_BEFORE = 30
        private const val FAR_ROW = 800
        private const val FOLDER_ROW = 20
    }
}
