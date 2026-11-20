package net.seilent.funekit.screens.host

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.albcol.AlbumColModel
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.lists.HistoryModel
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.queue.QueueModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicPagesTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val density = Density(1f)
    private val env = MusicEnv(s, s, TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null, groundBands(s.heightPx, true), Ink.white, true, Arbiter(s), { 0L }, {}, s)
    private val chrome = Chrome(s, Word.none, emptyList())
    private val types = MusicTypes(env, { -1 }, MusicChromes(chrome, chrome, chrome, chrome, chrome, chrome, chrome), RuntimeEnvironment.getApplication())

    @Test
    fun aPageWithNoModelTakesNoTypeAndNoThumb() {
        assertNull(types.of(null))
        assertNull(types.thumb(null))
        assertNull(types.side(null))
        assertNull(types.of(PageModel(0L, MusicPage.NowPlaying)))
    }

    @Test
    fun eachMusicModelTakesTheTypeOfItsKindAndTheAlbumAndListViewTakeTheEntryHead() {
        val head = Row(ID, Kind.Album, "Cobalt Rooms", "", null)
        val models = listOf(
            HubModel(0L), HubModel(1L, MusicPage.Search("a")), QueueModel(2L), AlbumModel(3L, ID), AlbumColModel(4L, 0),
            ArtistModel(5L, ID), PlaylistsModel(6L), ListViewModel(7L, Listing.Favorites), HistoryModel(8L),
        )
        val want = listOf(0, 0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals(want, models.map { types.all.indexOf(types.of(it)) })
        val album = models[3] as AlbumModel
        val view = models[7] as ListViewModel
        types.enter(album, MusicEntry(3L, MusicPage.Album(ID), head = head))
        types.enter(view, MusicEntry(7L, MusicPage.ListView(Listing.Favorites), head = head))
        assertSame(head, album.head)
        assertSame(head, view.head)
        val other = head.copy(title = "Ash")
        types.enter(album, MusicEntry(3L, MusicPage.Album(ID), head = other))
        assertSame("an album keeps the head it took", head, album.head)
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val ID = 20L
    }
}
