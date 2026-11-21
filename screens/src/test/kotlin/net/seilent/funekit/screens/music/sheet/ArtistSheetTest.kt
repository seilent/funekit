package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.scrollInto
import net.seilent.funekit.screens.music.artist.ARTIST_ALBUMS
import net.seilent.funekit.screens.music.artist.ARTIST_SONGS
import net.seilent.funekit.screens.music.artist.ArtistDraw
import net.seilent.funekit.screens.music.artist.ArtistGeo
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.ArtistPaint
import net.seilent.funekit.screens.music.artist.RINGS
import net.seilent.funekit.screens.music.artist.artistChrome
import net.seilent.funekit.screens.music.artist.artistDraw
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.Sheet
import net.seilent.funekit.sheet.Tile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ArtistSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)
    private val art = Art(MusicScenes.ART_BYTES)

    private class Shot(val m: ArtistModel, val d: ArtistDraw, val c: Chrome, val s: Scale, val fade: Float, val toast: SceneToast?)

    private class Scene(
        val kind: String,
        val artist: (List<Row>) -> Row,
        val knob: Int = 0,
        val songs: Boolean = false,
        val play: Boolean = false,
        val first: Boolean = false,
        val note: String = "",
        val nav: Nav = Nav.None,
    )

    private fun artists(): List<Row> = runBlocking {
        val v = sc.fake.library.view()
        v.page(Listing.Artists, 0, v.page(Listing.Artists, 0, 0).total).rows
    }

    private fun shot(dev: Dev, scene: Scene, large: Boolean): Shot = runBlocking {
        val s = sc.pageScale(dev, scene.nav, large)
        val v = sc.fake.library.view()
        val a = scene.artist(artists())
        val m = ArtistModel(0L, a.id)
        val g = ArtistGeo(s)
        m.geometry(g)
        for (p in m.panes) p.ppu = s.ppu
        m.load(v)
        for (p in m.panes) {
            p.track.count(p.count, 0)
            if (p.count > 0) m.show(v, p.pivot, 0, p.count - 1)
        }
        for (r in 0 until m.panes[ARTIST_ALBUMS].count) m.panes[ARTIST_ALBUMS].row(r)?.art?.let { m.loadArt(v, art, it, g.side) }
        if (scene.songs) m.pivot = ARTIST_SONGS
        val pn = m.pane
        var moved = false
        if (scene.knob >= RINGS && pn.count > 0) {
            m.listDown()
            val row = (scene.knob - RINGS).coerceIn(0, pn.count - 1)
            pn.focus.set(row, pn.count, 0L, false)
            val t = pn.track
            val q = scrollInto(t.topOf(row), t.topOf(row + 1) - t.topOf(row), t)
            if (q != t.q) {
                t.at(q)
                moved = true
            }
        } else {
            m.ring = scene.knob
        }
        val played = when {
            scene.first -> v.page(Listing.ArtistTracks(m.id), 0, 1).rows.firstOrNull()
            scene.play && !m.onRings -> pn.row(pn.focus.row)
            else -> null
        }
        if (played != null) {
            m.track = played.id
            m.playing = v.trackIds(played.id)
        }
        val toast = played?.let { sc.sceneToast(it, s, SHOT - shotMs(scene.kind) + if (scene.first) ARTIST_PLAY else playAt(scene.knob)) }
        val c = artistChrome(sc.measurer, sc.fonts, s, sc.density, sc.covers).also { it.select(m.pivot) }
        Shot(m, artistDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, c, sc.accent, false), c, s, if (moved) knobFade(scene.knob, shotMs(scene.kind)) else 0f, toast)
    }

    private fun DrawScope.page(h: Shot, rec: Regions?) {
        val hr = rec?.let { HubRegions(it) }
        with(h.d) {
            block(SHOT, false, hr, "page")
            content(h.m.pivot, SHOT, false, false, hr, "page")
        }
        if (h.fade > 0f) with(ArtistPaint(h.d, h.c, sc.accent)) { thumb(h.fade, hr, "page") }
        h.toast?.let { sceneToast(it, h.s, SHOT, rec) }
        chrome(h.c, SHOT)
        rec?.chrome("chrome", h.c, SHOT, size.width.toInt())
    }

    private fun tile(dev: Dev, scene: Scene, large: Boolean): Tile {
        val h = shot(dev, scene, large)
        val kind = (if (large) "large-" else "") + scene.kind
        val rec = sc.regions(dev, h.s, if (large && scene.nav == Nav.Buttons) listOf(ROW_ART to NAV_LARGE) else emptyList()).also {
            it.residual = true
            it.residualWhy = ARTIST_LOUD
        }
        return sc.tile(FOLDER, dev, kind, h.s, listOf(scene.note).filter { it.isNotEmpty() }, rec) { page(h, rec) }
    }

    private val first: (List<Row>) -> Row = titled(MUKA)
    private fun titled(t: String): (List<Row>) -> Row = { rows -> rows.first { it.title == t } }

    private val scenes = listOf(
        Scene("artist-2500", first, note = FIRST_NOTE),
        Scene("artist-2500-1-4", first, knob = 4, note = FIRST_NOTE),
        Scene("artist-2500-1-5-songs", first, knob = 5, songs = true, note = FIRST_NOTE),
        Scene("artist-2500-1-5-songs-play", first, knob = 5, songs = true, play = true, note = FIRST_NOTE),
        Scene("artist-2500-1-play", first, first = true, note = FIRST_NOTE),
        Scene("artist-2500-@An_Artist_Name_Long_Enough_To_Clip_At_The_Right_Edge", titled(LONG_ARTIST), note = LONG_NOTE),
        Scene("navbar-artist", first, nav = Nav.Buttons, note = FIRST_NOTE),
    )

    private fun scene(k: String) = scenes.first { it.kind == k }

    @Test
    fun funeOsArtistScenes() {
        for (scene in scenes) {
            val tiles = listOf(sc.r1, sc.a105).map { tile(it, scene, false) }
            sc.render.write(Sheet(FOLDER, scene.kind, listOf("The artist page as funeOS ${scene.kind}, A105 and R1."), tiles))
        }
        sc.render.write(Sheet(FOLDER, "large-artist-2500", listOf("The artist page at SIZE LARGE as funeOS large-artist-2500."), listOf(sc.r1, sc.a105).map { tile(it, scene("artist-2500"), true) }))
        val v1 = listOf(tile(sc.v1, scene("artist-2500"), false), tile(sc.v1, Scene("artist-2500-1-0-songs", first, songs = true, note = FIRST_NOTE), false)) +
            listOf("artist-2500", "artist-2500-1-5-songs", "artist-2500-1-play", "artist-2500-@An_Artist_Name_Long_Enough_To_Clip_At_The_Right_Edge", "navbar-artist").map { tile(sc.v1, scene(it), true) }
        sc.render.write(Sheet(FOLDER, "v1-artist", listOf("The artist page on V1 at both sizes."), v1))
    }

    companion object {
        private const val FOLDER = "artist"
        private const val SHOT = 10_000L
        private const val LONG_ARTIST = "A Singer Name Long Enough To Be Cut At The Right End"
        private const val ARTIST_LOUD = "the artist page shows only its header, pivots, name, rings, label and rows on the plain ground"
        private const val MUKA = "48 Muka"
        private const val ROW_ART = "page.row0.art"
        private const val FIRST_NOTE = "48 Muka (albums The Xx Yy of 1997, two title lines, and NN Word of 1998) stands in by shape for funeOS's first # artist 81 Mali"
        private const val LONG_NOTE = "the Demo's long artist name stands in for funeOS's"
    }
}
