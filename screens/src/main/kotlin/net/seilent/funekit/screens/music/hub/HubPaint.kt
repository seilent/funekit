package net.seilent.funekit.screens.music.hub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.seilent.funekit.Scale
import net.seilent.funekit.core.Section
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Layout
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowKind
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.RowWord
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.listRowWord
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.TileHide
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.px
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.screens.music.lists.favsRow
import net.seilent.funekit.screens.music.lists.listRow
import net.seilent.funekit.screens.music.lists.nowRow
import kotlin.math.max
import net.seilent.funekit.kit.thumb as drawThumb

const val NOW_PLAYING = "now playing"
const val FAVORITES = "favorites"

const val THUMB_SKIP = "the artist and genre thumbs show the fixture's album art, whose covers and counts differ from funeOS's fixtures"

class HubGeo(val s: Scale) {
    val top: Int = px(s.board.page_contentTop, s.ppu)
    val bottom: Int = px(s.stdContentBottom, s.stdPpu)
    val view: Int = bottom - top
    val above: Int = Layout.above(s)
    val clipTop: Int = top - above
    val grid = HubGrid(s)
    val edge: Int = px(Tok.focus_border, s.stdPpu)
    val thumb: Int = px(Tok.rowThumb, s.stdPpu)
    val thumbGap: Int = px(Tok.rowThumbGap, s.stdPpu)
    val thumbRight: Int = px(s.w - Tok.edge, s.ppu)

    fun pitch(role: Int): Int = if (hubGrid(role)) grid.pitch else Rows.pitch(s, hubCollection(role), role == HUB_SONGS)

    fun spec(role: Int, h: Int = view): RowSpec = if (role == HUB_SONGS) RowSpec.of(Tok.rowSong, s, h, above) else RowSpec(pitch(role), h, above)

    fun slots(x: Int): Int = max(0, (thumbRight - x + thumbGap) / (thumb + thumbGap))
}

fun HubModel.rowModel(p: Int, r: Int): RowModel? {
    val pn = panes[p]
    val v = pn.cell(r, 0)
    val k = role(p)
    val collection = hubCollection(k)
    val l = letterOf(v)
    if (l >= 0) return RowModel(kind = RowKind.Divider, primary = Section.letter(l), collection = collection, song = k == HUB_SONGS)
    if (v == NO_CELL || v == LABEL_CELL) return null
    if (v == QUERY_CELL) return RowModel(primary = queryText(), trailing = search?.takeIf { it.open }?.word.orEmpty())
    val pl = playing
    if (k == HUB_PLAYLISTS) {
        return when (v) {
            NOW_CELL -> nowRow(pl.queue)
            FAVS_CELL -> favsRow(pn.favorites, pl.favorites())
            else -> pn.row(v)?.let { listRow(it, pl.list(it.id)) }
        }
    }
    val row = pn.row(v) ?: return null
    return when (k) {
        HUB_ARTISTS -> RowModel(primary = row.title, collection = true, ring = true, accent = pl.artist(row.id))
        HUB_SONGS -> RowModel(primary = row.title, secondary = row.sub, album = row.album.orEmpty(), song = true, accent = pl.song(row.id))
        HUB_GENRES -> RowModel(primary = row.title, collection = true, bold = true, ring = true, accent = pl.genre(row.id))
        else -> if (row.kind == Kind.Folder) {
            RowModel(primary = row.title, secondary = songsLabel(row.count ?: 0), bold = true, accent = pl.folder(row.id))
        } else {
            RowModel(primary = row.title, secondary = row.sub, number = (v - pn.folders + 1).toString(), libSub = true, accent = pl.song(row.id))
        }
    }
}

fun HubModel.cellAt(r: Int, k: Int): GridCell? {
    val pn = panes[set.grid]
    val v = pn.cell(r, k)
    val l = letterOf(v)
    if (l >= 0) return GridCell(l, "", "", null, false)
    val row = pn.row(v) ?: return null
    return GridCell(-1, row.title, row.sub, row.art, playing.album(row.id))
}

fun HubModel.bold(p: Int, r: Int): Boolean {
    val k = role(p)
    if (k == HUB_PLAYLISTS || k == HUB_GENRES) return true
    if (k != HUB_FOLDERS) return false
    val pn = panes[p]
    return pn.row(pn.cell(r, 0))?.kind == Kind.Folder
}

data class HubLook(
    val alpha: Float,
    val focused: Boolean,
    val col: Int,
    val glow: Float?,
    val ringLit: Float = 0f,
    val lit: List<Float> = emptyList(),
    val rings: List<Float> = emptyList(),
    val back: Boolean = false,
)

fun HubModel.look(p: Int, r: Int, now: Long, focusShown: Boolean): HubLook {
    val pn = panes[p]
    pn.focus.revision
    pn.press.revision
    val bold = bold(p, r)
    val alpha = pn.focus.rowAlpha(
        r, now,
        if (bold) Tok.type_listPlaylist_alpha else Tok.type_listPrimary_alpha,
        if (bold) Tok.type_listPlaylist_alphaDim else Tok.type_listPrimary_alphaDim,
    )
    val focused = focusShown && r == pn.focus.row
    if (gridRow(p, r)) {
        val n = pn.map?.cells(r) ?: 0
        return HubLook(alpha, focused, if (focused) pn.col else -1, null, 0f, List(n) { pn.press.lit(pn.item(r, it), now) }, List(n) { pn.press.ring(pn.item(r, it), now) })
    }
    val lit = pn.press.lit(pn.item(r, 0), now)
    val ring = if (pn.ring && r == pn.down) lit else 0f
    val g = pn.glow
    val back = g.glow.key != r && g.back.key == r
    return HubLook(alpha, focused, -1, if (g.glow.key == r || back) lit else null, ring, back = back)
}

data class HubThumb(val image: ImageBitmap?, val missed: Boolean)

data class HubRow(
    val model: RowModel? = null,
    val label: Boolean = false,
    val cells: List<GridCell?> = emptyList(),
    val art: List<ImageBitmap?> = emptyList(),
    val boxed: List<Boolean> = emptyList(),
    val thumbs: List<HubThumb> = emptyList(),
    val hidden: List<Boolean> = emptyList(),
    val query: Boolean = false,
)

fun thumbWalk(refs: List<ArtRef?>, geo: HubGeo, x0: Int, state: (ArtRef) -> HubThumb): List<HubThumb> {
    val out = ArrayList<HubThumb>()
    val slots = geo.slots(x0)
    var x = x0
    var misses = 0
    for (ref in refs) {
        if (x + geo.thumb > geo.thumbRight) break
        val t = ref?.let(state) ?: HubThumb(null, true)
        out += t
        if (t.missed) {
            if (++misses >= slots) break
            continue
        }
        x += geo.thumb + geo.thumbGap
    }
    return out
}

class HubDraw(
    val m: HubModel,
    val geo: HubGeo,
    private val inkOf: (RowModel) -> RowInk,
    private val gridInkOf: (GridCell, Int) -> GridInk,
    private val labelOf: (String) -> Word,
    private val lineOf: (String) -> Word,
    val accent: Color,
    val accentOff: Boolean,
    val sides: List<WordSide> = emptyList(),
    val headerSide: WordSide? = null,
    val rowSide: ((RowWord, RowModel) -> WordSide)? = null,
) {
    var hidden = -1

    val s: Scale get() = geo.s
    val thumbX: Int = Layout.thumbsX(s, true)
    private val inks = HashMap<RowModel, RowInk>()
    private val grids = HashMap<Pair<GridCell, Int>, GridInk>()
    private val labels = HashMap<String, Word>()
    private val lines = HashMap<String, Word>()

    fun ink(r: RowModel): RowInk = inks.getOrPut(r) { inkOf(r) }

    fun gridInk(c: GridCell, k: Int): GridInk = grids.getOrPut(c to k) { gridInkOf(c, k) }

    fun label(): Word = labelPath(m.path).let { path -> labels.getOrPut(path) { labelOf(path) } }

    fun line(text: String): Word {
        if (text !in lines && lines.size >= HUB_COUNT) lines.clear()
        return lines.getOrPut(text) { lineOf(text) }
    }

    fun keep(models: Set<RowModel>, cells: Set<Pair<GridCell, Int>>, texts: Set<String>) {
        inks.keys.retainAll(models)
        grids.keys.retainAll(cells)
        labels.keys.retainAll(setOf(labelPath(m.path)))
        lines.keys.retainAll(texts)
    }

    fun art(ref: ArtRef?, px: Int): ImageBitmap? = ref?.let { m.art[ArtKey.Cover(it, px)] }

    fun boxed(ref: ArtRef?, px: Int): Boolean = ref == null || m.art.containsKey(ArtKey.Cover(ref, px)) && m.art[ArtKey.Cover(ref, px)] == null

    fun data(p: Int, r: Int): HubRow {
        if (m.gridRow(p, r)) {
            val n = m.panes[p].map?.cells(r) ?: 0
            val tile = geo.grid.tile
            val pn = m.panes[p]
            val cells = List(n) { k -> m.cellAt(r, k) }
            val hidden = List(n) { k -> m.hidden >= 0 && pn.row(pn.cell(r, k))?.id == m.hidden }
            return HubRow(cells = cells, art = cells.map { art(it?.ref, tile) }, boxed = cells.map { boxed(it?.ref, tile) }, hidden = hidden)
        }
        if (m.onLabel(p, r)) return HubRow(label = true)
        val model = m.rowModel(p, r) ?: return HubRow()
        if (m.panes[p].cell(r, 0) == QUERY_CELL) return HubRow(model, query = true)
        if (!(model.ring && model.collection && model.kind == RowKind.Item)) return HubRow(model)
        val pn = m.panes[p]
        val id = pn.row(pn.cell(r, 0))?.id ?: return HubRow(model)
        val refs = pn.thumbs[id] ?: return HubRow(model)
        val walk = thumbWalk(refs, geo, thumbX) { ref -> art(ref, geo.thumb).let { HubThumb(it, it == null && boxed(ref, geo.thumb)) } }
        val hide = m.hidden >= 0 && pn.firsts[id] == m.hidden && walk.firstOrNull()?.missed == false
        return HubRow(model, thumbs = if (hide) listOf(HubThumb(null, false)) + walk.drop(1) else walk)
    }

    fun DrawScope.row(p: Int, r: Int, look: HubLook, rec: DrawRecord?, name: String, focusShown: Boolean) {
        val d = data(p, r)
        val model = if (r == hidden) d.model?.copy(primary = "") else d.model
        row(p, look, d, model?.let { ink(it) }, d.cells.mapIndexed { k, c -> c?.let { gridInk(it, k) } }, rec, name, focusShown)
    }

    fun DrawScope.row(p: Int, look: HubLook, d: HubRow, k: RowInk?, cells: List<GridInk?>, rec: DrawRecord?, name: String, focusShown: Boolean) {
        val pn = m.panes[p]
        if (m.grid(p) && d.model == null && !d.label) {
            val g = geo.grid
            for (i in cells.indices) {
                val c = cells[i] ?: continue
                gridCell(g, i, c, look.col == i, look.lit.getOrElse(i) { 0f }, look.rings.getOrElse(i) { 0f }, d.art[i], d.boxed[i], accent, rec, "$name.cell$i", d.hidden.getOrElse(i) { false })
            }
            return
        }
        if (d.label) {
            folderLabel(s, label(), rec, "$name.label")
            return
        }
        if (k == null) return
        val glow = look.glow?.let { RowGlow(if (look.back) pn.glow.back else pn.glow.glow, it) }
        val shown = focusShown && !d.query
        listRow(k, s, look.alpha, look.focused, 1f, accent, accentOff, glow, null, shown, look.ringLit)
        rec?.row(name, k, s, size.width.toInt(), look.alpha, look.focused, 1f, glow != null, accent, accentOff, shown)
        if (d.thumbs.isNotEmpty()) thumbs(d.thumbs, rec, "$name.thumb")
    }

    private fun DrawScope.thumbs(ts: List<HubThumb>, rec: DrawRecord?, name: String) {
        val side = geo.thumb
        var x = thumbX
        val y = Layout.thumbsTop(s, 0)
        for (t in ts) {
            if (t.missed) continue
            val img = t.image
            if (img != null) {
                drawImage(img, dstOffset = IntOffset(x, y), dstSize = IntSize(side, side))
                val e = Edges(x, y, x + side, y + side)
                rec?.skip(THUMB_SKIP) { (rec as? GridRecord)?.art("$name$x", e, img, 1f, peakOff = ART_PEAK) }
            }
            x += side + geo.thumbGap
        }
    }

    fun DrawScope.content(p: Int, now: Long, focusShown: Boolean, rec: DrawRecord?, name: String, look: (Int) -> HubLook) {
        val pn = m.panes[p]
        val map = pn.map
        val text = m.line(p)
        if (text.isNotEmpty()) statusLine(s, line(text), rec, "$name.line")
        if (map == null || map.count == 0 || scanShown(m.scanning, scanSongs(m.generation, m.songs))) return
        val t = pn.track
        val off = t.q
        val first = t.drawFirst
        val last = minOf(map.count - 1, t.rowAt(off + geo.view - 1))
        for (r in first..last) {
            val y = geo.top + t.topOf(r) - off
            val clipTop = if (m.gridRow(p, r) && y >= geo.clipTop) geo.clipTop - geo.edge else geo.clipTop
            clipped(rec, 0f, clipTop.toFloat(), size.width, geo.bottom.toFloat()) {
                shifted(rec, 0f, y.toFloat()) { row(p, r, look(r), rec, "$name.row$r", focusShown) }
            }
        }
        val found = m.found(p)
        if (found.isNotEmpty()) statusLine(s, line(found), rec, "$name.found", foundBase(m, p).toFloat())
    }

    fun foundBase(m: HubModel, p: Int): Int = lineBase(s) + m.panes[p].track.topOf(1) - m.panes[p].track.q
}

class HubPaint(
    private val d: HubDraw,
    override val chrome: Chrome,
    override val item: Int,
    private val bands: List<Band>,
    private val accent: Color,
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    private val p = m.pivot
    override val header: Word = chrome.header
    override val grounded: Boolean = true
    override val kind: PageKind = m.kind
    override val top: Int = geo.top
    override val above: Int = geo.above
    override val bottom: Int = geo.bottom
    override val count: Int get() = if (scanShown(m.scanning, scanSongs(m.generation, m.songs))) 0 else m.panes[p].map?.count ?: 0
    override val scroll: Int get() = m.panes[p].track.q

    override fun rowY(i: Int): Int = geo.top + m.panes[p].track.topOf(i)

    override fun DrawScope.ground(leave: Float, arrive: Float) = pageGround(bands, accent, leave, arrive)

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val look = m.look(p, i, now, true)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(p, i, look, rec, "$name.row$i", true) } }
    }

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
        with(d) { content(p, now, true, rec, name) { i -> m.look(p, i, now, true) } }
    }

    override fun wordSide(pivot: Boolean): WordSide? =
        if (pivot) d.sides.getOrNull(chrome.active)?.let { WordSide(it.text, it.s, it.look, chrome.pivotX(chrome.active), it.y, it.pivot, it.header, it.word) } else d.headerSide

    override fun rowSide(row: Int): WordSide? {
        if (m.role(p) != HUB_PLAYLISTS) return null
        val model = m.rowModel(p, row) ?: return null
        val w = listRowWord(m.panes[p].track, geo.top, geo.bottom, row, false, model, d.ink(model), geo.s) ?: return null
        return d.rowSide?.invoke(w, model)
    }

    override fun hideRow(row: Int) {
        d.hidden = row
    }

    override fun tileRect(album: Long): Edges? {
        if (!m.grid(p)) return null
        val pn = m.panes[p]
        val map = pn.map ?: return null
        val w = pn.paging.window ?: return null
        val i = w.rows.indexOfFirst { it.id == album }
        if (i < 0) return null
        val r = map.rowOfIndex(i + w.offset)
        if (r < 0) return null
        val e = geo.grid.tileAt(map.colOfIndex(i + w.offset), geo.top + pn.track.topOf(r) - pn.track.q)
        return if (e.y0 < geo.clipTop) null else e
    }

    override fun thumbRect(row: Int): Edges? {
        if (m.role(p) != HUB_ARTISTS) return null
        val pn = m.panes[p]
        val id = pn.row(pn.cell(row, 0))?.id ?: return null
        val ref = pn.thumbs[id]?.firstOrNull() ?: return null
        if (d.art(ref, geo.thumb) == null && d.art(ref, px(Tok.tileL, geo.s.stdPpu)) == null) return null
        val y = geo.top + pn.track.topOf(row) - pn.track.q + Layout.thumbsTop(geo.s, 0)
        val e = Edges(d.thumbX, y, d.thumbX + geo.thumb, y + geo.thumb)
        return if (e.y0 < geo.clipTop || e.y1 > geo.bottom) null else e
    }

    override fun hide(h: TileHide?) {
        m.hidden = h?.album ?: -1L
    }

    override fun thumbFade(at: Long): Float = m.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = hubThumbAt(m, geo, p, scroll) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, bands, accent)
    }
}
