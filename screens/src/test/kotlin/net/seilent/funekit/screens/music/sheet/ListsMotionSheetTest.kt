package net.seilent.funekit.screens.music.sheet

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.HUB_PLAYLISTS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.lists.HISTORY_NEW
import net.seilent.funekit.screens.music.overlay.picker
import net.seilent.funekit.screens.music.sheet.RowSheets.Companion.SHEET_OPEN
import net.seilent.funekit.screens.music.sheet.RowSheets.Companion.SHEET_SHOT
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
class ListsMotionSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)
    private val root = File(sc.render.root, FOLDER)

    private class View(val dev: Dev, val nav: Nav)

    private fun acts(vararg a: Pair<Long, () -> Unit>): MutableList<Pair<Long, () -> Unit>> = a.toMutableList()

    private fun frames(v: View, name: String, from: Long, setup: (S4Rig) -> MutableList<Pair<Long, () -> Unit>>): Int {
        val rig = S4Rig(sc, v.dev, v.nav)
        val acts = setup(rig)
        val dir = File(root, "${v.dev.id}/$name")
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

    private fun playlistsOpen(r: S4Rig) = acts(SELECT to { r.openHome(MusicPage.Playlists) })

    private fun playlistsClose(r: S4Rig) = playlistsOpen(r).also { it += BACK to { r.back() } }

    private fun viewOpen(r: S4Rig) = playlistsOpen(r).also { it += PUSH to { r.openList(NIGHT_ROW) } }

    private fun viewClose(r: S4Rig) = viewOpen(r).also { it += POP to { r.back() } }

    private fun historyOpen(r: S4Rig) = acts(SELECT to { r.openHome(MusicPage.History) })

    private fun historyT5(r: S4Rig) = historyOpen(r).also { it += PUSH to { r.switchHistory(HISTORY_NEW, PUSH) } }

    private fun hubView(r: S4Rig): MutableList<Pair<Long, () -> Unit>> {
        r.start(HUB_PLAYLISTS)
        return acts(PUSH to { r.openList(HUB_LIST_ROW) })
    }

    private fun pickerTile(dev: Dev, nav: Nav, large: Boolean) = run {
        val h = sc.hubShot(dev, HUB_SONGS, nav, large)
        val open = h.knobs(SONG_STEPS, KNOBS_AT) + SHEET_OPEN
        val t = h.target(h.m.pane.focus.row)
        val ms = sc.menuShot(h.s, MenuForm.Sheet, t.title, runBlocking { picker(sc.source) }.items, open + PICK_AFTER)
        val shot = open + PICK_AFTER + SHEET_SHOT
        val size = if (large) "large" else "standard"
        sc.reviewTile(FOLDER, dev, "picker-$size-${"$nav".lowercase()}", h.s, listOf("the picker swapped in over a hub song sheet, $size, $nav navigation; shot at $shot")) {
            overPage(sc, null, ms, h.s, shot, null, null) { _ -> with(h) { page(shot, null) } }
        }
    }

    private fun shuffleTile(dev: Dev): Tile {
        val scenes = MusicScenes(FakeSet.Demo)
        val probe = PlaylistsShot(scenes, dev)
        val l = requireNotNull(probe.m.rows.list(NIGHT_ROW))
        val listing = Listing.PlaylistTracks(l.id)
        val first = runBlocking {
            scenes.fake.player.shuffle(true)
            scenes.fake.player.play(listing, scenes.fake.library.generation.value, -1, 0L)
            val st = scenes.fake.player.state.value
            scenes.fake.library.view().page(Listing.Queue, st.index, 1).rows.first()
        }
        val h = PlaylistsShot(scenes, dev, origin = listing)
        h.knob(NIGHT_ROW, LISTS_AT)
        val toast = scenes.sceneToast(first, h.s, LISTS_AT + SHEET_SHOT)
        val shot = LISTS_AT + SHEET_SHOT + PLAY_SHOT
        return scenes.reviewTile(FOLDER, dev, "playlists-shuffle", h.s, listOf("Night Drive played whole with shuffle on: the queue starts at a random track (${first.title}); shot at $shot")) {
            with(h) { page(shot, null) }
            sceneToast(toast, h.s, shot, null)
        }
    }

    @Test
    fun listsMotionAndReviewSheets() {
        val views = listOf(View(sc.a105, Nav.None), View(Phones.p2160Page, Nav.Gesture))
        val lines = mutableListOf<String>()
        for (v in views) {
            val n = listOf(
                Triple("t2-playlists", SELECT, ::playlistsOpen),
                Triple("t4h-playlists", BACK, ::playlistsClose),
                Triple("t3-view", PUSH, ::viewOpen),
                Triple("t4-view", POP, ::viewClose),
                Triple("t2-history", SELECT, ::historyOpen),
                Triple("t5-history", PUSH, ::historyT5),
                Triple("t3-hub-view", PUSH, ::hubView),
            ).map { (name, from, setup) -> name to frames(v, name, from, setup) }
            for ((name, count) in n) lines += "- `$FOLDER/${v.dev.id}/$name/`: $count frames at $FPS fps from ${v.dev.label}"
        }
        val pickers = listOf(false, true).flatMap { large -> listOf(pickerTile(Phones.p2160Page, Nav.Gesture, large), pickerTile(Phones.p2160ButtonsPage, Nav.Buttons, large)) }
        sc.render.write(Sheet(FOLDER, "picker-phone", listOf("The picker over a hub song sheet on the page base, review only."), pickers))
        sc.render.write(Sheet(FOLDER, "playlists-shuffle", listOf("A Playlists row played whole with shuffle on, review only."), listOf(sc.a105, Phones.p2160Page).map { shuffleTile(it) }))
        sc.render.index(FOLDER, listOf(
            "# Playlists, playlist view and history, review", "",
            "Unpaired, no regions. Frames at $FPS fps, frame i at (i x 1000 + $FRAME_PHASE) / $FPS ms after the act.", "",
            "- `lists-phone.png`: the Playlists page and a playlist view on the page base",
            "- `picker-phone.png`: the picker over a hub song sheet on the page base",
            "- `playlists-shuffle.png`: a Playlists row played whole with shuffle on",
        ) + lines)
    }

    companion object {
        private const val FOLDER = "musics6"
        private const val SELECT = 1360L
        private const val BACK = 3160L
        private const val PUSH = 3160L
        private const val POP = 5160L
        private const val NIGHT_ROW = 3
        private const val HUB_LIST_ROW = 2
        private const val SONG_STEPS = 3
        private const val PICK_AFTER = 960L
        private const val FRAMES = 60
        private const val FPS = 60
        private const val MS_PER_S = 1000L
        private const val FRAME_PHASE = 30L
        private const val PNG_QUALITY = 100
    }
}
