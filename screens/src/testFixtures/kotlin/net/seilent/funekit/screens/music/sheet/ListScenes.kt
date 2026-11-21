package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.Row
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.stack.layer
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.lists.HistoryGeo
import net.seilent.funekit.screens.music.lists.HistoryModel
import net.seilent.funekit.screens.music.lists.HistoryPaint
import net.seilent.funekit.screens.music.lists.ListViewGeo
import net.seilent.funekit.screens.music.lists.ListViewModel
import net.seilent.funekit.screens.music.lists.ListViewPaint
import net.seilent.funekit.screens.music.lists.PlaylistsGeo
import net.seilent.funekit.screens.music.lists.PlaylistsKey
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.lists.PlaylistsPaint
import net.seilent.funekit.screens.music.lists.ViewKey
import net.seilent.funekit.screens.music.lists.historyDraw
import net.seilent.funekit.screens.music.lists.listViewDraw
import net.seilent.funekit.screens.music.lists.playlistsDraw
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.pivotSwitch
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.Tile

const val S6_NONE = "no funeOS reference in the set"
const val S6_LOUD = "the page shows only its header, pivots, count and empty lines, rows and thumb on the plain ground"
const val S6_SHEET = "the sheet panel and the page under it are excluded, as on the row sheets"
const val S6_T5 = "both pivot sides move under T5, so the residual pass has no rest window"
const val S6_OPEN = "funeOS opens now playing with T7 at this play (kit quads that record no region); the tile draws the page under it at the shot"
const val PRESS_DOWN = 50L
const val PRESS_HELD = 150L
const val PRESS_TAP = 200L
const val PRESS_UP = 650L
const val UP_TAP = 50L
const val UP_HELD = 150L
val PRESS_PHASES = listOf("down" to PRESS_DOWN, "held" to PRESS_HELD, "tap" to PRESS_TAP, "up" to PRESS_UP)

fun upAt(phase: String): Long? = when (phase) {
    "tap" -> UP_TAP
    "up" -> UP_HELD
    else -> null
}

fun swapAt(select: Long): Long = select + Tok.motion_t2Swap.toLong()

fun emptied(v: LibraryView, empty: (Listing) -> Boolean): LibraryView = object : LibraryView by v {
    override suspend fun page(listing: Listing, offset: Int, count: Int): Page =
        if (empty(listing)) Page(v.generation, offset, 0, emptyList()) else v.page(listing, offset, count)
}

fun MusicScenes.rowsOf(v: LibraryView, l: Listing): List<Row> = runBlocking { v.page(l, 0, v.page(l, 0, 0).total).rows }

fun MusicScenes.firstTrack(l: Listing): Row = runBlocking { fake.library.view().page(l, 0, 1).rows.first() }

fun MusicScenes.same(dev: Dev): Dev = boards.keys.firstOrNull { it.id == dev.id } ?: dev

fun MusicScenes.regionsOn(dev: Dev, s: Scale, loud: Boolean, why: String): Regions = regions(dev, s).also {
    it.residual = loud
    it.residualWhy = why
}

fun MusicScenes.s6Tile(folder: String, dev: Dev, kind: String, s: Scale, notes: List<String>, rec: Regions, block: DrawScope.() -> Unit): Tile =
    tile(folder, dev, kind, s, notes + S6_NONE, rec, block)

fun MusicScenes.reviewTile(folder: String, dev: Dev, name: String, s: Scale, notes: List<String>, block: DrawScope.() -> Unit): Tile =
    render.tile(folder, name, dev.label, listOf(render.facts(dev, s.nav, s)) + notes, draw(dev, block), emptyList(), s.stdPpu, null)

fun MusicScenes.sheetOf(s: Scale, t: SheetTarget, at: Long, steps: Int = 0, stepAt: Long = at): MenuShot =
    menuShot(s, MenuForm.Sheet, t.title, sheetItems(t, false), at, steps = steps, stepAt = stepAt)

class PlaylistsShot(val sc: MusicScenes, dev: Dev, nav: Nav = Nav.None, large: Boolean = false, origin: Listing? = null, v: LibraryView = sc.fake.library.view()) {
    val s = sc.pageScale(dev, nav, large)
    val bands = groundBands(dev.h, false)
    val m = PlaylistsModel(0L)
    val geo = PlaylistsGeo(s)
    val d = playlistsDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, sc.accent, false)
    val c: Chrome = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Playlists.headerWord ?: "", MusicPage.Playlists.pivots, sc.covers)
    private val paint = PlaylistsPaint(d, c, bands, sc.accent)

    init {
        m.pane.ppu = s.ppu
        m.pane.geometry(RowSpec(geo.pitch, geo.view))
        val queue = origin?.let { runBlocking { v.page(it, 0, 0).total } } ?: 0
        runBlocking { m.load(v, PlaylistsKey(0L, 0L, queue, origin, v.generation), 0L) }
        m.pane.fade.show(swapAt(LISTS_SELECT))
    }

    fun knob(row: Int, at: Long) = m.pane.focus.set(row, m.size, at, true)

    fun DrawScope.page(now: Long, rec: Regions?) {
        pageGround(bands, sc.accent)
        chrome(c, now)
        rec?.chrome("chrome", c, now, size.width.toInt())
        with(paint) {
            rest(now, rec, "page")
            val f = m.pane.fade.at(now)
            if (f > 0f) thumb(f, rec, "page")
        }
    }
}

class ViewShot(val sc: MusicScenes, dev: Dev, listing: Listing, head: Row?, nav: Nav = Nav.None, large: Boolean = false, empty: Boolean = false) {
    val s = sc.pageScale(dev, nav, large)
    val bands = groundBands(dev.h, false)
    val m = ListViewModel(0L, listing).also { it.head = head }
    val geo = ListViewGeo(s)
    private val v: LibraryView = sc.fake.library.view().let { base -> if (empty) emptied(base) { it == listing } else base }
    val d = listViewDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, sc.accent, false)
    val c: Chrome = requireNotNull(d.chrome)
    private val paint = ListViewPaint(d, c, bands, sc.accent)

    init {
        m.pane.ppu = s.ppu
        m.pane.geometry(RowSpec(geo.pitch, geo.view))
        runBlocking {
            m.refresh(v, ViewKey(0L, v.generation), 0L)
            if (m.total > 0) m.show(v, 0, m.total - 1)
        }
    }

    fun shown(at: Long) = m.pane.fade.show(at)

    fun knob(row: Int, at: Long) = m.pane.focus.set(row, m.total, at, true)

    fun DrawScope.page(now: Long, rec: Regions?) {
        pageGround(bands, sc.accent)
        chrome(c, now)
        rec?.chrome("chrome", c, now, size.width.toInt())
        with(paint) {
            rest(now, rec, "page")
            val f = m.pane.fade.at(now)
            if (f > 0f) thumb(f, rec, "page")
        }
    }
}

class HistoryShot(val sc: MusicScenes, dev: Dev, nav: Nav = Nav.None, large: Boolean = false, noPlays: Boolean = false, noNew: Boolean = false) {
    val s = sc.pageScale(dev, nav, large)
    val bands = groundBands(dev.h, false)
    val m = HistoryModel(0L)
    val geo = HistoryGeo(s)
    val v: LibraryView = emptied(sc.fake.library.view()) { (noPlays && it == Listing.History) || (noNew && it == Listing.NewAlbums) }
    val d = historyDraw(m, s, sc.measurer, sc.fonts, sc.density, sc.covers, sc.accent, false)
    val c: Chrome = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.History.headerWord ?: "", MusicPage.History.pivots, sc.covers)

    init {
        for (p in m.panes) {
            p.ppu = s.ppu
            p.geometry(RowSpec(geo.pitch, geo.view))
        }
        runBlocking { m.load(v, ViewKey(0L, v.generation), 0L) }
        m.pane.fade.show(swapAt(HISTORY_SELECT))
    }

    fun knob(row: Int, at: Long) = m.pane.focus.set(row, m.count(m.pivot), at, true)

    fun switch(to: Int, at: Long) = pivotSwitch(m, c, to, at)

    fun DrawScope.page(now: Long, rec: Regions?) {
        pageGround(bands, sc.accent)
        chrome(c, now)
        rec?.chrome("chrome", c, now, size.width.toInt())
        val at = m.switchAt
        if (at != null && m.switching(now)) {
            val side = m.side(now)
            val shownAt = if (side == m.pivot) at + Tok.motion_pivotOut.toLong() else at
            layer(rec, m.motion.alpha(now), Paint()) {
                shifted(rec, m.motion.dx(now, s.ppu).toFloat(), 0f) { with(d) { content(side, shownAt, true, rec, "page") } }
            }
            val a = m.thumbAlpha(now)
            val r = geo.thumb(m.count(side), 0)
            if (a > 0f && r != null) {
                thumb(r, a)
                rec?.thumbParts("page.thumb", r, Tok.scrollbar_alpha * a, bands, sc.accent)
            }
            return
        }
        with(HistoryPaint(d, c, bands, sc.accent)) {
            rest(now, rec, "page")
            val f = m.pane.fade.at(now)
            if (f > 0f) thumb(f, rec, "page")
        }
    }
}

const val LISTS_SELECT = 1360L
const val LISTS_AT = 3160L
const val HISTORY_SELECT = 1480L
const val HISTORY_AT = 3280L
const val PLAY_SHOT = 1000L
