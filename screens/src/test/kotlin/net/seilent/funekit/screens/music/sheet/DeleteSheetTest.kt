package net.seilent.funekit.screens.music.sheet

import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.deleteAsk
import net.seilent.funekit.screens.music.sheet.RowSheets.Companion.SHEET_OPEN
import net.seilent.funekit.screens.music.sheet.RowSheets.Companion.SHEET_SHOT
import net.seilent.funekit.sheet.Dev
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
class DeleteSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)

    private fun confirm(dev: Dev, kind: String, pivot: Int, target: (HubShot) -> SheetTarget, what: String): Tile {
        val h = sc.hubShot(dev, pivot)
        val open = h.knobs(STEPS, KNOBS_AT) + SHEET_OPEN
        val t = target(h)
        val songs = if (t.kind == SheetKind.Album) sc.rowsOf(sc.fake.library.view(), Listing.AlbumTracks(t.id)).size else 0
        val a = deleteAsk(t, songs)
        val ms = sc.menuShot(h.s, MenuForm.Dlg, a.title, a.items, ASK_AT, mixedRow = 0, confirm = true, steps = a.focus - a.lead, lead = a.lead)
        val shot = ASK_AT + SHEET_SHOT
        val rec = sc.regionsOn(dev, h.s, false, S6_SHEET)
        val note = "$STEPS steps from $KNOBS_AT, $what, its sheet at $open, delete selected at $ASK_AT: the confirm with its title and count as lead rows and keep focused"
        return sc.s6Tile(FOLDER, dev, kind, h.s, listOf("$note; shot at $shot", S6_SHEET), rec) {
            overPage(sc, rec, ms, h.s, shot, null, null) { pr -> with(h) { page(shot, pr) } }
        }
    }

    private fun tile(dev: Dev, kind: String): Tile = when (kind) {
        "delete-500-song" -> confirm(dev, kind, HUB_SONGS, { it.target(it.m.pane.focus.row) }, "on SONGS to row $STEPS")
        "delete-500-album" -> confirm(dev, kind, HUB_ALBUMS, { it.target(it.m.pane.focus.row) }, "on ALBUMS to row $STEPS")
        "delete-500-albumlong" -> confirm(dev, kind, HUB_ALBUMS, { h -> h.target(h.m.pane.focus.row).copy(title = longest()) }, "on ALBUMS to row $STEPS, the sheet titled with the longest album title in the set")
        else -> error("no scene $kind")
    }

    private fun longest(): String = sc.rowsOf(sc.fake.library.view(), Listing.Albums).maxBy { it.title.length }.title

    @Test
    fun deleteScenes() {
        for (kind in KINDS) {
            sc.render.write(Sheet(FOLDER, kind, listOf("The scene $kind on A105, R1 and V1. $S6_NONE."), listOf(sc.r1, sc.a105, sc.v1).map { tile(it, kind) }))
        }
    }

    companion object {
        private const val FOLDER = "delete"
        private const val STEPS = 3
        private const val ASK_AT = 4120L
        val KINDS = listOf("delete-500-song", "delete-500-album", "delete-500-albumlong")
    }
}
