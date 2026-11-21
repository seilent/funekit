package net.seilent.funekit.screens.music.hub

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.api.Kind
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.host.MusicModels
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_FOLDERS
import net.seilent.funekit.screens.music.hub.HUB_PLAYLISTS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPane
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubKeepTest {
    private val fake = testFake(FakeSet.Demo)

    private fun HubPane.scrolledTo(q: Int, row: Int, col: Int) {
        track.resize(PITCH, VIEW)
        track.count(map!!.count, 0)
        track.at(q)
        setFocus(row, col, 0L, false)
    }

    @Test
    fun aSecondVisitKeepsEveryPivotButAlbumsAndTheOpenFolder() = runBlocking {
        val music = MusicModels()
        val pages = PageModels { e, p -> musicModel(e, p as MusicPage, music.hub) }
        val first = pages.enter(0L, MusicPage.Hub) as HubModel
        first.cols = COLS
        val v = fake.library.view()
        for (p in listOf(HUB_ALBUMS, HUB_ARTISTS, HUB_PLAYLISTS, HUB_SONGS, HUB_FOLDERS)) {
            first.load(v, p)
            first.panes[p].gen = v.generation
        }
        first.panes[HUB_ALBUMS].scrolledTo(Q, ROW, 1)
        first.panes[HUB_ARTISTS].scrolledTo(Q, ROW, 0)
        first.panes[HUB_SONGS].scrolledTo(Q * 2, ROW + 1, 0)
        val folders = first.panes[HUB_FOLDERS]
        val map = folders.map!!
        val row = (0 until map.count).first { folders.row(map.indexAt(it))?.kind == Kind.Folder }
        val folder = folders.row(map.indexAt(row))!!
        first.select(HUB_FOLDERS)
        first.enter(folder.id, folder.title)
        first.load(v, HUB_FOLDERS)
        first.select(HUB_SONGS)
        pages.drop(0L)
        val second = pages.enter(1L, MusicPage.Hub) as HubModel
        assertTrue(second !== first)
        assertEquals(HUB_ALBUMS, second.pivot)
        assertEquals(0, second.panes[HUB_ALBUMS].track.q)
        assertEquals(0, second.panes[HUB_ALBUMS].focus.row)
        assertEquals(0, second.panes[HUB_ALBUMS].col)
        assertEquals(Q, second.panes[HUB_ARTISTS].track.q)
        assertEquals(ROW, second.panes[HUB_ARTISTS].focus.row)
        assertEquals(Q * 2, second.panes[HUB_SONGS].track.q)
        assertEquals(ROW + 1, second.panes[HUB_SONGS].focus.row)
        assertEquals(v.generation, second.panes[HUB_SONGS].gen)
        assertTrue(second.panes[HUB_PLAYLISTS].map != null)
        assertEquals(-1L, second.panes[HUB_PLAYLISTS].gen)
        assertEquals(folder.id, second.folder)
        assertEquals(listOf(folder.title), second.path)
        second.select(HUB_FOLDERS)
        assertTrue(second.up())
        assertTrue(second.atRoot)
    }

    @Test
    fun aHubOutsideTheLauncherModelsStartsFresh() {
        val m = HubModel(0L)
        assertEquals(0, m.panes[HUB_SONGS].track.q)
        assertTrue(m.atRoot)
        assertTrue(m.path.isEmpty())
    }

    companion object {
        private const val COLS = 3
        private const val PITCH = 120
        private const val VIEW = 920
        private const val Q = 600
        private const val ROW = 6
    }
}
