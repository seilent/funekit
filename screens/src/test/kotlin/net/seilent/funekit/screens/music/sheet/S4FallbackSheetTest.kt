package net.seilent.funekit.screens.music.sheet

import android.graphics.Bitmap
import net.seilent.funekit.Nav
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.host.MusicEntry
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Phones
import net.seilent.funekit.sheet.Sheet
import net.seilent.funekit.sheet.Tile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class S4FallbackSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)
    private val root = File(sc.render.root, FOLDER)

    private class View(val dev: Dev, val nav: Nav, val large: Boolean = false) {
        val id: String get() = dev.id + if (large) "-large" else ""
    }

    private fun acts(vararg a: Pair<Long, () -> Unit>): MutableList<Pair<Long, () -> Unit>> = a.toMutableList()

    private fun top(rig: S4Rig): MusicEntry = rig.st.top!!

    private fun album(r: S4Rig): MutableList<Pair<Long, () -> Unit>> {
        r.start(HUB_ALBUMS)
        return acts(KNOB to { r.hubKnob(0, 1, KNOB) }, SELECT to { r.hubSelect(SELECT) })
    }

    private fun artist(r: S4Rig): MutableList<Pair<Long, () -> Unit>> {
        r.start(HUB_ARTISTS)
        return acts(KNOB to { r.hubKnob(1, 0, KNOB) }, SELECT to { r.hubSelect(SELECT) })
    }

    private fun col(r: S4Rig): MutableList<Pair<Long, () -> Unit>> {
        r.start(HUB_ALBUMS)
        return acts(KNOB to { r.openJump() }, PICK to { r.pickJump() })
    }

    private fun page(v: View, name: String, at: Long, note: String, setup: (S4Rig) -> MutableList<Pair<Long, () -> Unit>>): Tile {
        val rig = S4Rig(sc, v.dev, v.nav, v.large)
        rig.run(at, setup(rig))
        return sc.render.tile(FOLDER, "${v.id}-$name", "kit ${v.dev.label}", listOf(sc.render.facts(v.dev, v.nav, rig.s), note), sc.draw(v.dev) { with(rig) { world(at, null) } }, emptyList(), rig.s.stdPpu)
    }

    private fun frames(v: View, name: String, from: Long, setup: (S4Rig) -> MutableList<Pair<Long, () -> Unit>>): Int {
        val rig = S4Rig(sc, v.dev, v.nav, v.large)
        val acts = setup(rig)
        val dir = File(root, "${v.id}/$name")
        dir.deleteRecursively()
        dir.mkdirs()
        val names = mutableListOf<String>()
        for (i in 0 until FRAMES) {
            val t = from + (i * MS_PER_S + FRAME_PHASE) / FPS
            rig.run(t, acts)
            val bmp: Bitmap = sc.draw(v.dev) { with(rig) { world(t, null) } }
            val f = "f%04d.png".format(names.size)
            File(dir, f).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            bmp.recycle()
            names += f
        }
        File(dir, "anim.json").writeText("{\"fps\": $FPS, \"frames\": [" + names.joinToString(", ") { "\"$it\"" } + "]}\n")
        return names.size
    }

    private fun playAlbum(r: S4Rig) = album(r).also {
        it += PLAY_AT to {
            val m = r.model(top(r)) as AlbumModel
            r.play(Listing.AlbumTracks(m.id), m.gen, 0, PLAY_AT)
        }
    }

    private fun playArtist(r: S4Rig) = artist(r).also {
        it += PLAY_AT to {
            val m = r.model(top(r)) as ArtistModel
            r.play(Listing.ArtistTracks(m.id), m.gen, 0, PLAY_AT)
        }
    }

    @Test
    fun s4FallbackSheets() {
        val phone = View(Phones.p2160Page, Nav.Gesture)
        val phoneLarge = View(Phones.p2160Page, Nav.Gesture, true)
        val pages = listOf(phone, phoneLarge)
        sc.render.write(Sheet(FOLDER, "album-phone", listOf("Album detail on the 1080 x 2160 page base at STANDARD and LARGE: the block clips at W - edge, the durations end at W - hubEdge."),
            pages.map { page(it, "album", SELECT + REST, FIRST_ALBUM, ::album) }))
        sc.render.write(Sheet(FOLDER, "artist-phone", listOf("The artist page on the 1080 x 2160 page base at STANDARD and LARGE: the name line under the scaled pivots."),
            pages.map { page(it, "artist", SELECT + REST, FIRST_ARTIST, ::artist) }))
        sc.render.write(Sheet(FOLDER, "column-phone", listOf("The column page on the 1080 x 2160 page base at STANDARD and LARGE: the entries wrap to W - edge."),
            pages.map { page(it, "column", PICK + COL_REST, COL_NOTE, ::col) }))
        val navCol = listOf(View(sc.a105, Nav.Buttons), View(Phones.p2160ButtonsPage, Nav.Buttons), View(sc.a105, Nav.None, true))
        sc.render.write(Sheet(FOLDER, "column-nav-large", listOf("The column page with NAV BAR ON on a Buttons window (A105 and the 1080 x 2160 page base) and at LARGE on the A105."),
            navCol.map { page(it, if (it.nav == Nav.Buttons) "column-navbar" else "column", PICK + COL_REST, COL_NOTE, ::col) }))
        val t3m = frames(phone, "t3m", SELECT, ::album)
        val t7Album = frames(View(sc.a105, Nav.None), "t7-album", PLAY_AT, ::playAlbum)
        val t7Artist = frames(View(sc.a105, Nav.None), "t7-artist", PLAY_AT, ::playArtist)
        val t7PhoneAlbum = frames(phone, "t7-album", PLAY_AT, ::playAlbum)
        sc.render.index(FOLDER, listOf(
            "# Album detail, the column page and the artist page, FALLBACK review", "",
            "Unpaired, no regions: funeOS renders 240 u boards only and has no T7 from an album, column or artist page. Frames at $FPS fps, frame i at (i x 1000 + $FRAME_PHASE) / $FPS ms after the act.", "",
            "- `album-phone.png`: album detail on the 1080 x 2160 page base at STANDARD and LARGE",
            "- `artist-phone.png`: the artist page on the 1080 x 2160 page base at STANDARD and LARGE",
            "- `column-phone.png`: the column page on the 1080 x 2160 page base at STANDARD and LARGE",
            "- `column-nav-large.png`: the column page with NAV BAR ON on A105 and the page base, and at LARGE on A105",
            "- `$FOLDER/${phone.id}/t3m/`: $t3m frames; T3m from the first ALBUMS row of the 4 column grid to album detail",
            "- `$FOLDER/720x1280/t7-album/`: $t7Album frames; T7 from album detail into now playing, the play of track 1",
            "- `$FOLDER/720x1280/t7-artist/`: $t7Artist frames; T7 from the artist page into now playing, the play ring",
            "- `$FOLDER/${phone.id}/t7-album/`: $t7PhoneAlbum frames; T7 from album detail on the page base",
        ))
    }

    companion object {
        private const val FOLDER = "musics4-fallback"
        private const val KNOB = 2600L
        private const val SELECT = 2920L
        private const val PICK = 3200L
        private const val REST = 2500L
        private const val COL_REST = 1500L
        private const val PLAY_AT = 5420L
        private const val FRAMES = 60
        private const val FPS = 60
        private const val MS_PER_S = 1000L
        private const val FRAME_PHASE = 30L
        private const val PNG_QUALITY = 100
        private const val FIRST_ARTIST = "the first # artist of the Demo order"
        private const val FIRST_ALBUM = "the first # album of the Demo order"
        private const val COL_NOTE = "the column page opened on the first present ALBUMS letter"
    }
}
