package net.seilent.funekit.screens.music.sheet

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.core.Section
import net.seilent.funekit.kit.Ink as KitInk
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.jumpGrid
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.word
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.SEARCH_ALBUMS
import net.seilent.funekit.screens.music.hub.SEARCH_ARTISTS
import net.seilent.funekit.screens.music.hub.SEARCH_SONGS
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.overlay.GO_TO_ARTIST
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.search.searchWord
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
class SearchSheetTest {
    private val sc = MusicScenes()

    private class Scene(val kind: String, val query: Query, val pivot: Int, val form: Form, val note: String)

    private enum class Query { Name, Empty, Long }

    private enum class Form { Page, Field, Sheet, SheetEnd, Play, Tile, ArtistSheet, AlbumSheet }

    private val scenes = listOf(
        Scene("search-500-tile", Query.Name, HUB_ARTISTS, Form.Tile, "the jump grid on the hub ARTISTS, 60 knob steps back at $TILE_BACK and one on at $TILE_ON; shot at $TILE_SHOT"),
        Scene("search-500-artists", Query.Name, SEARCH_ARTISTS, Form.Page, "Search on ARTISTS"),
        Scene("search-500-albums", Query.Name, SEARCH_ALBUMS, Form.Page, "Search on ALBUMS"),
        Scene("search-500-songs", Query.Name, SEARCH_SONGS, Form.Page, "Search on SONGS"),
        Scene("search-500-empty", Query.Empty, SEARCH_ARTISTS, Form.Page, "a query with no hits: no artists found"),
        Scene("search-500-long", Query.Name, SEARCH_ARTISTS, Form.Page, "the long names on ARTISTS"),
        Scene("search-500-longalbums", Query.Name, SEARCH_ALBUMS, Form.Page, "the long names on ALBUMS"),
        Scene("search-500-longq", Query.Long, SEARCH_ARTISTS, Form.Field, "the long artist name typed, the field open with its count word"),
        Scene("search-500-sheet", Query.Name, SEARCH_SONGS, Form.Sheet, "the song sheet of the first SONGS item at $SHEET_OPEN"),
        Scene("search-500-sheetend", Query.Name, SEARCH_SONGS, Form.SheetEnd, "the song sheet stepped to go to artist at $END_STEP"),
        Scene("search-500-play", Query.Name, SEARCH_ARTISTS, Form.Play, "the first album hit's first track played at $PIVOT_AT"),
        Scene("search-500-playalbums", Query.Name, SEARCH_ALBUMS, Form.Play, "the first album hit's first track played at $PIVOT_AT, on ALBUMS"),
        Scene("sheet-500-artist", Query.Name, HUB_ARTISTS, Form.ArtistSheet, "the second hub artist row selected at $SELECT_AT, the artist sheet on its rings at $SHEET_OPEN"),
        Scene("sheet-500-album", Query.Name, HUB_ALBUMS, Form.AlbumSheet, "the second hub album tile selected at $SELECT_AT, two rows down and its song sheet at $SHEET_OPEN"),
    )

    private val navScenes = listOf(
        Scene("navbar-search", Query.Name, SEARCH_SONGS, Form.Page, "Search on SONGS with NAV BAR ON"),
        Scene("navbar-sheet", Query.Name, HUB_ALBUMS, Form.AlbumSheet, "the album page song sheet with NAV BAR ON"),
    )

    private fun query(q: Query): String = when (q) {
        Query.Name -> NAME
        Query.Empty -> EMPTY
        Query.Long -> runBlocking {
            val v = sc.fake.library.view()
            v.page(Listing.Artists, 0, v.page(Listing.Artists, 0, 0).total).rows.maxBy { it.title.length }.title
        }
    }

    private fun top(r: S4Rig): HubModel = r.model(r.st.top!!) as HubModel

    private fun search(r: S4Rig, scene: Scene): HubModel {
        r.start(HUB_ARTISTS)
        r.run(OPEN_AT)
        r.openSearch(query(scene.query))
        r.run(PIVOT_AT)
        require(r.st.phase == Phase.Rest) { "the search push has landed by $PIVOT_AT" }
        val m = top(r)
        r.hubPivot(m, scene.pivot)
        return m
    }

    private fun firstItem(m: HubModel, kind: SheetKind): Pair<Int, SheetTarget> {
        val map = requireNotNull(m.pane.map)
        for (row in 0 until map.count) for (c in 0 until map.cells(row)) m.target(m.pivot, row, c)?.takeIf { it.kind == kind }?.let { return row to it }
        error("no $kind item on pivot ${m.pivot}")
    }

    private fun nthItem(m: HubModel, kind: SheetKind, n: Int): Pair<Int, Int> {
        val map = requireNotNull(m.pane.map)
        var k = 0
        for (row in 0 until map.count) for (c in 0 until map.cells(row)) if (m.target(m.pivot, row, c)?.kind == kind && k++ == n) return row to c
        error("no item $n of $kind")
    }

    private fun tileOf(dev: Dev, scene: Scene, nav: Nav, large: Boolean, kind: String): Tile = when (scene.form) {
        Form.Tile -> jumpTile(dev, scene, nav, large, kind)
        Form.ArtistSheet, Form.AlbumSheet -> pageSheet(dev, scene, nav, large, kind)
        else -> searchTile(dev, scene, nav, large, kind)
    }

    private fun searchTile(dev: Dev, scene: Scene, nav: Nav, large: Boolean, kind: String): Tile {
        val r = S4Rig(sc, dev, nav, large)
        val m = search(r, scene)
        var shot = PIVOT_AT + SHOT_AFTER
        var sheet: MenuShot? = null
        when (scene.form) {
            Form.Field -> {
                m.openField()
                m.search!!.word = searchWord(runBlocking { sc.fake.library.view().page(Listing.Search(m.committed(), Kind.Song), 0, 0).total })
            }
            Form.Sheet, Form.SheetEnd -> {
                val (row, t) = firstItem(m, SheetKind.Song)
                m.pane.setFocus(row, 0, SHEET_OPEN, false)
                val steps = if (scene.form == Form.SheetEnd) sheetItems(t, false).indexOf(GO_TO_ARTIST) else 0
                sheet = sc.sheetOf(r.s, t, SHEET_OPEN, steps, END_STEP)
                shot = (if (scene.form == Form.SheetEnd) END_STEP else SHEET_OPEN) + SHOT_AFTER
            }
            Form.Play -> runBlocking {
                val v = sc.fake.library.view()
                val album = v.page(Listing.Search(m.committed(), Kind.Album), 0, 1).rows.first()
                sc.fake.player.play(Listing.AlbumTracks(album.id), v.generation, 0, 0L)
                m.play(v, sc.fake.player.state.value)
            }
            else -> Unit
        }
        r.run(shot)
        val ms = sheet
        val rec = sc.regionsOn(dev, r.s, ms == null, if (ms == null) LOUD else S6_SHEET)
        val notes = listOfNotNull("as funeOS ${funeOs(kind)}: ${scene.note}; shot at $shot", if (scene.form == Form.Play) S6_OPEN else null, if (scene.query != Query.Empty) FAKE else null)
        return sc.s6Tile(FOLDER, dev, kind, r.s, notes, rec) {
            if (ms != null) overPage(sc, rec, ms, r.s, shot, null, null) { pr -> with(r) { world(shot, pr) } } else with(r) { world(shot, rec) }
        }
    }

    private fun pageSheet(dev: Dev, scene: Scene, nav: Nav, large: Boolean, kind: String): Tile {
        val r = S4Rig(sc, dev, nav, large)
        r.start(scene.pivot)
        val m = r.hubModel
        val (row, c) = nthItem(m, if (scene.form == Form.ArtistSheet) SheetKind.Artist else SheetKind.Album, 1)
        r.hubKnob(row, c, KNOB_AT)
        r.run(SELECT_AT)
        r.hubSelect(SELECT_AT)
        r.run(SHEET_OPEN)
        require(r.st.phase == Phase.Rest) { "the page push has landed by $SHEET_OPEN" }
        val t = when (val pm = r.model(r.st.top!!)) {
            is ArtistModel -> requireNotNull(pm.ringTarget(0))
            is AlbumModel -> {
                pm.focus.set(ALBUM_ROW, pm.count, SHEET_OPEN, false)
                requireNotNull(pm.target(ALBUM_ROW))
            }
            else -> error("no page under the sheet")
        }
        val ms = sc.sheetOf(r.s, t, SHEET_OPEN)
        val shot = SHEET_OPEN + SHOT_AFTER
        r.run(shot)
        val rec = sc.regionsOn(dev, r.s, false, S6_SHEET)
        return sc.s6Tile(FOLDER, dev, kind, r.s, listOf("as funeOS ${funeOs(kind)}: ${scene.note}; shot at $shot", S6_SHEET), rec) {
            overPage(sc, rec, ms, r.s, shot, null, null) { pr -> with(r) { world(shot, pr) } }
        }
    }

    private fun jumpTile(dev: Dev, scene: Scene, nav: Nav, large: Boolean, kind: String): Tile {
        val s = sc.pageScale(dev, nav, large)
        val (m, d) = sc.hub(s, scene.pivot)
        sc.fill(m, d)
        val c = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots, sc.covers).also { it.select(scene.pivot) }
        val g = JumpGrid().also { it.open(m.avail(), 0, s.stdH) }
        repeat(TILE_STEPS) { g.step(-1, TILE_BACK) }
        g.step(1, TILE_ON)
        g.follow(g.target)
        fun style(role: Role) = roleStyle(role, s, sc.density, KitInk.white, ppu = s.stdPpu)
        val ink = JumpInk(
            sc.measurer.word("exit", Roles.jumpExit, style(Roles.jumpExit), sc.fonts, sc.density, null, s.board.fontHanMinPx),
            (0 until Section.count).map { sc.measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), sc.fonts, sc.density, sc.covers, s.board.fontHanMinPx) },
        )
        val rec = sc.regionsOn(dev, s, true, JUMP_LOUD)
        val hr = HubRegions(rec)
        return sc.s6Tile(FOLDER, dev, kind, s, listOf("as funeOS ${funeOs(kind)}: ${scene.note}", FAKE), rec) {
            pageGround(groundBands(dev.h, false), sc.accent)
            rec.hide("under the jump grid's opaque ground") {
                chrome(c, TILE_SHOT)
                with(d) { content(scene.pivot, TILE_SHOT, true, hr, "page") { r -> m.look(scene.pivot, r, TILE_SHOT, true) } }
            }
            jumpGrid(g, ink, JumpCells(s), s, g.offset, TILE_SHOT)
            rec.jump("jump", g, ink, JumpCells(s), s, g.offset, TILE_SHOT, dev.h)
        }
    }

    private fun funeOs(kind: String): String = kind.removePrefix("large-").removePrefix("navbar-").replace('-', ':')

    @Test
    fun searchScenes() {
        for (dev in listOf(sc.r1, sc.a105, sc.v1)) {
            val tiles = scenes.map { tileOf(dev, it, Nav.None, false, it.kind) }
            sc.render.write(Sheet(FOLDER, "search-${dev.id}", listOf("The search page, its sheets and the album and artist page sheets, no funeOS reference in the set."), tiles))
        }
    }

    @Test
    fun largeAndNavScenes() {
        val large = scenes.map { tileOf(sc.v1, it, Nav.None, true, "large-" + it.kind) } +
            listOf(sc.r1, sc.a105).flatMap { dev -> scenes.filter { it.kind == "search-500-artists" || it.kind == "sheet-500-album" }.map { tileOf(dev, it, Nav.None, true, "large-" + it.kind) } }
        sc.render.write(Sheet(FOLDER, "search-large", listOf("The search scenes at SIZE LARGE, no funeOS reference in the set."), large))
        val nav = listOf(sc.r1, sc.a105, sc.v1).flatMap { dev -> navScenes.map { tileOf(dev, it, Nav.Buttons, false, it.kind) } } +
            navScenes.map { tileOf(sc.v1, it, Nav.Buttons, true, "large-" + it.kind) }
        sc.render.write(Sheet(FOLDER, "search-navbar", listOf("The search page and the album page sheet with NAV BAR ON, no funeOS reference in the set."), nav))
    }

    private companion object {
        const val FOLDER = "search"
        const val NAME = "name"
        const val EMPTY = "zzqx"
        const val OPEN_AT = 2600L
        const val PIVOT_AT = 3200L
        const val SHOT_AFTER = 500L
        const val KNOB_AT = 2600L
        const val SELECT_AT = 2700L
        const val SHEET_OPEN = 5300L
        const val END_STEP = 5900L
        const val TILE_BACK = 3400L
        const val TILE_ON = 3500L
        const val TILE_SHOT = 4000L
        const val TILE_STEPS = 60
        const val ALBUM_ROW = 2
        const val LOUD = "the search page shows only its chrome, row 0, its result rows, letter cells and lines on the plain ground, every one drawn"
        const val JUMP_LOUD = "the jump grid's opaque ground hides the page; every cell and the exit and search tiles are drawn"
        const val FAKE = "the fake matches titles only, so the scene's Demo query stands in for funeOS's alpha and long"
    }
}
