package net.seilent.funekit.screens.music.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.motion.NpLeave
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.NpBands
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.TileHide
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.restRows
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.word
import net.seilent.funekit.kit.ringPress
import net.seilent.funekit.px
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.ALBUMS
import net.seilent.funekit.screens.music.artist.RingLook
import net.seilent.funekit.screens.music.artist.detailRing
import net.seilent.funekit.screens.music.hub.ART_PEAK
import net.seilent.funekit.screens.music.hub.GridRecord
import net.seilent.funekit.screens.music.hub.KIND_TILE
import net.seilent.funekit.screens.music.morphSide
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import net.seilent.funekit.kit.thumb as drawThumb

private const val HALF = 2f

fun albumChrome(m: TextMeasurer, fonts: FontFamily.Resolver, s: Scale, density: Density, covers: Covers?): Chrome =
    Chrome(s, m.word(ALBUMS, Roles.albumHeader, roleStyle(Roles.albumHeader, s, density, Ink.header, ppu = s.stdPpu), fonts, density, covers, s.board.fontHanMinPx), emptyList()).also {
        it.headerPen = Tok.page_albumHeaderX
        it.headerAlpha = Roles.albumHeader.alpha
    }

@Composable
fun rememberAlbumChrome(s: Scale): Chrome {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    return remember(s, density, fonts, covers) { albumChrome(m, fonts, s, density, covers) }
}

fun albumHeaderSide(m: TextMeasurer, fonts: FontFamily.Resolver, s: Scale, density: Density, covers: Covers?, chrome: Chrome): WordSide =
    morphSide(m, fonts, s, density, covers, ALBUMS, Roles.albumHeader, true, chrome.headerAlpha, chrome.headerRestX(), px(Tok.page_headerBaseline, s.stdPpu).toFloat(), -1)

fun DrawScope.albumTile(img: ImageBitmap?, side: Float, cx: Float, cy: Float, alpha: Float, rec: DrawRecord?) {
    val h = side / HALF
    val x0 = Math.round(cx - h)
    val y0 = Math.round(cy - h)
    val sz = Math.round(side)
    val e = Edges(x0, y0, x0 + sz, y0 + sz)
    val grid = rec as? GridRecord
    if (img == null) {
        drawRect(Ink.tile, Offset(x0.toFloat(), y0.toFloat()), Size(sz.toFloat(), sz.toFloat()), alpha = alpha)
        grid?.edges(FLIGHT, KIND_TILE, e, Ink.tile, alpha)
    } else {
        drawImage(img, dstOffset = IntOffset(x0, y0), dstSize = IntSize(sz, sz), alpha = alpha)
        grid?.art(FLIGHT, e, img, alpha, peakOff = ART_PEAK)
    }
}

const val FLIGHT = "flight"

private val ALBUM_RING_GLYPHS = listOf(Glyphs.play, Glyphs.shuffle)

class AlbumDraw(
    val m: AlbumModel,
    private val full: AlbumGeo,
    private val inkOf: (RowModel) -> RowInk,
    private val lineOf: (String, Role) -> Word,
    val headerSide: WordSide,
    val accent: Color,
    val accentOff: Boolean,
) {
    private val bare = AlbumGeo(full.s, rings = false)
    val geo: AlbumGeo get() = if (m.total == 0) bare else full
    val s: Scale get() = full.s
    private val inks = HashMap<RowModel, RowInk>()
    private var lines: Triple<Row, Word, Word>? = null

    fun ink(r: RowModel): RowInk = inks.getOrPut(r) { inkOf(r) }

    fun keep(models: Set<RowModel>) {
        inks.keys.retainAll(models)
    }

    fun lines(): Pair<Word, Word>? {
        if (m.gone) return null
        val h = m.head ?: return null
        val l = lines
        if (l != null && l.first == h) return l.second to l.third
        val a = lineOf(h.sub, Roles.npArtist)
        val b = lineOf(h.title, Roles.npAlbum)
        lines = Triple(h, a, b)
        return a to b
    }

    fun DrawScope.row(look: AlbumLook, k: RowInk?, rec: DrawRecord?, name: String, focusShown: Boolean) {
        if (k == null) return
        val glow = look.glow?.let { RowGlow(if (look.back) m.glow.back else m.glow.glow, it) }
        listRow(k, s, look.alpha, false, 1f, accent, accentOff, glow, null, focusShown)
        rec?.row(name, k, s, size.width.toInt(), look.alpha, false, 1f, glow != null, accent, accentOff, focusShown)
    }

    fun DrawScope.art(liftPx: Int, rec: DrawRecord?, name: String) {
        val b = m.block(liftPx)
        if (m.hidden && !b.gone) return
        val e = geo.artRect(liftPx)
        val img = b.art
        if (img == null) {
            drawRect(Ink.tile, Offset(e.x0.toFloat(), e.y0.toFloat()), Size(e.w.toFloat(), e.h.toFloat()))
            (rec as? GridRecord)?.edges(name, KIND_TILE, e, Ink.tile, 1f)
        } else {
            drawImage(img, dstOffset = IntOffset(e.x0, e.y0), dstSize = IntSize(e.w, e.h))
            (rec as? GridRecord)?.art(name, e, img, 1f, peakOff = ART_PEAK)
        }
    }

    fun DrawScope.textLines(liftPx: Int, rec: DrawRecord?, name: String) {
        val l = lines() ?: return
        val x = penPx(geo.textX64(liftPx))
        val clip = geo.textClip(liftPx).toFloat()
        clipped(rec, clip, 0f, geo.clipRight.toFloat(), size.height) {
            word(l.first, x, geo.artistBase.toFloat(), Ink.white, Roles.npArtist.alpha)
            word(l.second, x, geo.albumBase.toFloat(), Ink.white, Roles.npAlbum.alpha)
            rec?.word("$name.artist", l.first, x, geo.artistBase.toFloat(), Roles.npArtist.alpha)
            rec?.word("$name.album", l.second, x, geo.albumBase.toFloat(), Roles.npAlbum.alpha)
        }
    }

    fun DrawScope.block(liftPx: Int, rec: DrawRecord?, name: String) {
        art(liftPx, rec, "$name.art")
        textLines(liftPx, rec, name)
    }

    fun DrawScope.rings(looks: List<RingLook>, liftPx: Int, q: Int, rec: DrawRecord?, name: String) {
        val a = m.ringA(liftPx)
        if (a <= 0f) return
        val d = geo.ring
        val cy = geo.ringCy(q)
        val bottom = geo.bottom.toFloat()
        if (a == 1f) clipped(rec, 0f, geo.pressTopPx(liftPx).toFloat(), size.width, bottom) {
            for (i in looks.indices) ringPress(d, px(if (m.onRings && m.ring == i) Tok.ringStrokeFocused else Tok.ringStrokeIdle, s.stdPpu), geo.ringCx(i), cy, looks[i].lit)
        }
        clipped(rec, 0f, geo.rowsTopPx(liftPx).toFloat(), size.width, bottom) {
            for (i in looks.indices) detailRing(ALBUM_RING_GLYPHS[i], i, geo.ringCx(i), cy, d, looks[i].copy(lit = 0f), s, rec, name, a)
        }
    }

    fun leaveBands(liftPx: Int): NpBands {
        val w = s.widthPx
        val h = s.heightPx
        val hy = geo.pivotTop
        val bottom = min(geo.bottom, h)
        val l = lines()
        val low = geo.artistBase + (l?.first?.let { ceil(it.inkBottom).toInt() } ?: 0)
        val high = geo.albumBase + (l?.second?.let { floor(it.inkTop).toInt() } ?: 0)
        val mid = ((low + high + 1) shr 1).coerceIn(hy, h)
        val art = geo.artRect(liftPx)
        val rows = geo.rowsTopPx(liftPx)
        val t = m.track
        val ring = (geo.top - t.q).coerceIn(rows, bottom)
        val split = (geo.top + t.topOf(t.drawFirst + 1) - t.q).coerceIn(ring, bottom)
        val out = ArrayList<Pair<Edges, Part>>()
        fun band(e: Edges, p: Part) {
            val c = Edges(e.x0.coerceIn(0, w), e.y0.coerceIn(0, h), e.x1.coerceIn(0, w), e.y1.coerceIn(0, h))
            if (c.w > 0 && c.h > 0) out += c to p
        }
        band(Edges(if (art.y0 < mid) art.x1 else 0, hy, w, mid), Part.NpLArtist)
        band(Edges(0, rows, w, ring), Part.NpLArtist)
        band(Edges(0, ring, w, split), Part.NpLRow1)
        band(Edges(0, split, w, bottom), Part.NpLRows)
        return NpBands(NpLeave.Album, out)
    }
}

fun albumDraw(m: AlbumModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, chrome: Chrome, accent: Color, accentOff: Boolean): AlbumDraw {
    val geo = AlbumGeo(s)
    return AlbumDraw(
        m,
        geo,
        { r -> rowInk(measurer, fonts, r, s, density, accentOff, covers) },
        { t, r -> measurer.word(t, r, roleStyle(r, s, density, Ink.white, ppu = s.stdPpu), fonts, density, covers, s.board.fontHanMinPx) },
        albumHeaderSide(measurer, fonts, s, density, covers, chrome),
        accent,
        accentOff,
    )
}

class AlbumPaint(
    private val d: AlbumDraw,
    override val chrome: Chrome,
    private val accent: Color,
    private val tileArt: (Long, Float) -> ImageBitmap? = { _, _ -> null },
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    private val lift = m.track.lift
    override val header: Word = chrome.header
    override val item: Int = -1
    override val grounded: Boolean = false
    override val kind: PageKind = PageKind.Plain
    override val top: Int = geo.rowsTopPx(lift)
    override val bottom: Int = geo.bottom
    override val count: Int get() = m.count
    override val scroll: Int get() = m.track.q
    override val npArtTop: Int = geo.artRect(lift).y0

    override fun rowY(i: Int): Int = geo.top + m.track.topOf(i)

    override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val model = m.rowModel(i) ?: return
        val look = m.look(i, now, true)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(look, ink(model), rec, "$name.row$i", true) } }
    }

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
        with(d) { block(lift, rec, name) }
        if (!m.gone) restRows(this@AlbumPaint, now, rec, name)
        with(d) { rings(m.ringLooks(now, false), lift, scroll, rec, name) }
    }

    override fun wordSide(pivot: Boolean): WordSide? = if (pivot) null else d.headerSide

    override fun tileRect(album: Long): Edges? = if (album == m.id) geo.artRect(lift) else null

    override fun DrawScope.tile(album: Long, side: Float, cx: Float, cy: Float, alpha: Float, rec: DrawRecord?) = albumTile(tileArt(album, side), side, cx, cy, alpha, rec)

    override fun hide(h: TileHide?) {
        m.hidden = h != null && h.album == m.id
    }

    override fun npLeaveBands(): NpBands? = d.leaveBands(lift)

    override fun thumbFade(at: Long): Float = m.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = geo.thumb(m.track) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, groundBands(geo.s.heightPx, true), accent)
    }
}
