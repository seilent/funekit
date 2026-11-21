package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.fx
import net.seilent.funekit.keptU
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.px64
import net.seilent.funekit.screens.music.album.AlbumDraw
import net.seilent.funekit.screens.music.album.AlbumGeo
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.album.AlbumPaint
import net.seilent.funekit.screens.music.album.albumChrome
import net.seilent.funekit.screens.music.album.albumDraw
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
class AlbumSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)
    private val art = Art(MusicScenes.ART_BYTES)

    private class Shot(val m: AlbumModel, val d: AlbumDraw, val c: Chrome, val s: Scale, val off: Boolean, val fade: Float, val toast: SceneToast?)

    private class Scene(
        val kind: String,
        val album: (List<Row>) -> Row,
        val knob: Int = 0,
        val play: Boolean = false,
        val offU: Int = -1,
        val accentOff: Boolean = false,
        val note: String = "",
        val residual: Boolean = true,
        val rules: List<Pair<String, String>> = emptyList(),
        val nav: Nav = Nav.None,
        val end: Boolean = false,
    )

    private fun albums(): List<Row> = runBlocking {
        val v = sc.fake.library.view()
        v.page(Listing.Albums, 0, v.page(Listing.Albums, 0, 0).total).rows
    }

    private fun shot(dev: Dev, scene: Scene, large: Boolean): Shot = runBlocking {
        val s = sc.pageScale(dev, scene.nav, large)
        val v = sc.fake.library.view()
        val head = scene.album(albums())
        val m = AlbumModel(0L, head.id)
        m.head = head
        val g = AlbumGeo(s)
        m.geometry(g)
        m.ppu = s.ppu
        m.load(v)
        m.show(v, 0, m.total - 1)
        m.track.count(m.count, 0)
        head.art?.let { ref ->
            m.loadArt(v, art, ref, g.large)
            m.loadArt(v, art, ref, g.grid)
        }
        val row = scene.knob.coerceIn(0, maxOf(0, m.count - 1))
        m.focus.set(row, m.count, 0L, false)
        var moved = false
        if (scene.knob > 0) {
            val q = m.intoView(row)
            if (q != m.track.q) {
                m.track.at(q)
                moved = true
            }
        }
        if (scene.end && m.count > 0) {
            m.focus.set(m.count - 1, m.count, 0L, false)
            m.track.at(Int.MAX_VALUE)
        }
        if (scene.offU >= 0) m.track.at(px64(keptU(scene.offU.toFloat(), s), fx(s.ppu)))
        var toast: SceneToast? = null
        if (scene.play) {
            sc.fake.player.play(Listing.AlbumTracks(m.id), m.gen, row, 0L)
            m.play(sc.fake.player.state.value)
            m.row(row)?.let { toast = sc.sceneToast(it, s, SHOT - shotMs(scene.kind) + playAt(scene.knob)) }
        }
        val accent = Accent.color(Accent.defaultHue, scene.accentOff)
        val c = albumChrome(sc.measurer, sc.fonts, s, sc.density, sc.covers)
        Shot(m, albumDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, c, accent, scene.accentOff), c, s, scene.accentOff, if (moved) knobFade(scene.knob, shotMs(scene.kind)) else 0f, toast)
    }

    private fun DrawScope.page(h: Shot, rec: Regions?) {
        val accent = Accent.color(Accent.defaultHue, h.off)
        val p = AlbumPaint(h.d, h.c, accent)
        val hr = rec?.let { HubRegions(it) }
        with(p) { rest(SHOT, hr, "page") }
        if (h.fade > 0f) with(p) { thumb(h.fade, hr, "page") }
        h.toast?.let { sceneToast(it, h.s, SHOT, rec) }
        chrome(h.c, SHOT)
        rec?.chrome("chrome", h.c, SHOT, size.width.toInt())
    }

    private fun tile(dev: Dev, scene: Scene, large: Boolean): Tile {
        val h = shot(dev, scene, large)
        val kind = (if (large) "large-" else "") + scene.kind
        val navLarge = large && scene.nav == Nav.Buttons && scene.end
        val rec = sc.regions(dev, h.s, scene.rules).also {
            it.residual = scene.residual && !navLarge
            it.residualWhy = if (navLarge) "$NAV_LARGE; at the list end funeOS's rows sit 7 px higher (row 5 number at 154 against 161), and funeOS shows its page thumb (x 235 to 240, y 207 to 276, the lift form of thumb_of over its 277 px bottom): at the list top its thumb track, 277 less the 252 px list top, is 25 px, under scrollbar.min (30 px at LARGE), so thumb_of gives no thumb until the @end set lifts the list and the fade shows on that turn on (ThumbFade.track), where the kit's track is 32 px, its thumb on from the start and not shown again" else if (scene.residual) ALBUM_LOUD else scene.note
        }
        return sc.tile(FOLDER, dev, kind, h.s, listOf(scene.note).filter { it.isNotEmpty() }, rec) { page(h, rec) }
    }

    private val first: (List<Row>) -> Row = { it[0] }
    private val second: (List<Row>) -> Row = { it[1] }
    private fun titled(t: String): (List<Row>) -> Row = { rows -> rows.first { it.title == t } }

    private val scenes = listOf(
        Scene("album-2500", first, note = FIRST_NOTE),
        Scene("album-2500-2-7", second, knob = 7, note = SECOND_NOTE),
        Scene("album-2500-2-2-play", second, knob = 2, play = true, note = SECOND_NOTE),
        Scene("album-2500-2-2-play-accentoff", second, knob = 2, play = true, accentOff = true, note = SECOND_NOTE),
        Scene("album-2500-@テスト盤", titled(TERRACE), note = TERRACE_NOTE),
        Scene("album-2500-@Chunks", titled(CLUSTERS), residual = false, note = CLUSTERS_NOTE, rules = listOf("page.row1" to CLUSTERS_NOTE, "page.art" to CLUSTERS_NOTE)),
    ) + VENVENSA_OFFS.map { off ->
        Scene("album-6000-@Venvensa_Soven-0-play-$off-saveroff", second, play = true, offU = off, note = VENVENSA_NOTE)
    } + listOf(
        Scene("album-6000-@Venvensa_Soven-4-play-200-saveroff", second, knob = 4, play = true, offU = 200, note = VENVENSA_NOTE),
        Scene("album-6000-@0_Long_Album_Title_Clipped_At_The_Tile_Width-0-play-saveroff", titled(LONG_ALBUM), play = true, residual = false, note = LONG_NOTE),
        Scene("navbar-album", first, nav = Nav.Buttons, note = FIRST_NOTE),
        Scene("navbar-album-end", second, nav = Nav.Buttons, end = true, note = SECOND_NOTE),
    )

    @Test
    fun funeOsAlbumScenes() {
        for (scene in scenes) {
            val tiles = listOf(sc.r1, sc.a105).map { tile(it, scene, false) }
            sc.render.write(Sheet(FOLDER, scene.kind, listOf("Album detail as funeOS ${scene.kind}, A105 and R1."), tiles))
        }
        val large = listOf("album-2500", "album-6000-@Venvensa_Soven-0-play-115-saveroff").map { k -> scenes.first { it.kind == k } }
        for (scene in large) {
            val tiles = listOf(sc.r1, sc.a105).map { tile(it, scene, true) }
            sc.render.write(Sheet(FOLDER, "large-${scene.kind}", listOf("Album detail at SIZE LARGE as funeOS large-${scene.kind}."), tiles))
        }
        val v1 = listOf("album-2500").map { k -> scenes.first { it.kind == k } }.map { tile(sc.v1, it, false) } +
            listOf(
                "album-2500", "album-2500-2-7", "album-6000-@0_Long_Album_Title_Clipped_At_The_Tile_Width-0-play-saveroff",
                "album-6000-@Venvensa_Soven-0-play-0-saveroff", "album-6000-@Venvensa_Soven-0-play-115-saveroff", "album-6000-@Venvensa_Soven-4-play-200-saveroff",
                "navbar-album", "navbar-album-end",
            ).map { k -> scenes.first { it.kind == k } }.map { tile(sc.v1, it, true) }
        sc.render.write(Sheet(FOLDER, "v1-album", listOf("Album detail on V1 at both sizes."), v1))
    }

    companion object {
        private const val FOLDER = "album"
        private const val SHOT = 10_000L
        private val VENVENSA_OFFS = listOf(0, 31, 63, 67, 89, 115, 215)
        private const val TERRACE = "テラス盤"
        private const val CLUSTERS = "Clusters"
        private const val LONG_ALBUM = "0 An Album Name Cut Short At The Tile Border"
        private const val ALBUM_LOUD = "album detail shows only its header, art, two lines and track rows on the plain ground"
        private const val FIRST_NOTE = "the first # album of the Demo order stands in for funeOS's"
        private const val SECOND_NOTE = "the second # album of the Demo order stands in for funeOS's"
        private const val TERRACE_NOTE = "テラス盤 (one track, no cover) stands in for funeOS's テスト盤"
        private const val CLUSTERS_NOTE = "Clusters (one track, no cover) stands in for funeOS's Chunks (two tracks, the second with a cover): its second row and art differ"
        private const val VENVENSA_NOTE = "a generated album of 8 tracks with a cover stands in for funeOS's Venvensa Soven"
        private const val LONG_NOTE = "the Demo's long album title stands in for funeOS's"
    }
}
