package net.seilent.funekit.screens.music.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import kotlin.math.max
import kotlin.math.min
import net.seilent.funekit.Fonts
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptText
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.BoxInk
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.GlowText
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Tiles
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.artTile
import net.seilent.funekit.kit.boxInk
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.motion.NpLeave
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.nextCluster
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.pressGlow
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.ring
import net.seilent.funekit.kit.ringHalo
import net.seilent.funekit.kit.ringPress
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.NpBands
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.TileHide
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.textWidth
import net.seilent.funekit.kit.thumb as drawThumb
import net.seilent.funekit.kit.word
import net.seilent.funekit.kit.wrapRanges
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.album.albumTile
import net.seilent.funekit.screens.music.hub.ART_PEAK
import net.seilent.funekit.screens.music.hub.GridRecord
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.KIND_BAR
import net.seilent.funekit.screens.music.hub.KIND_TILE
import net.seilent.funekit.screens.music.morphSide
import net.seilent.funekit.screens.music.pivots

const val IN_COLLECTION = "in collection"
private const val KIND_GLOW = "glow"

private val RING_GLYPHS = listOf(Glyphs.play, Glyphs.plus, Glyphs.shuffle)

class AlbumInk(val title: List<Word>, val year: Word, val box: BoxInk?)

fun DrawScope.detailRing(g: Glyph, i: Int, cx: Int, cy: Int, d: Int, l: RingLook, s: Scale, rec: DrawRecord?, n: String, a: Float = 1f) {
    val stroke = px(if (l.focused) Tok.ringStrokeFocused else Tok.ringStrokeIdle, s.stdPpu)
    val al = (if (l.focused) Tok.alpha_full else Tok.alpha_mid) * a
    if (l.lit > 0f) {
        ringPress(d, stroke, cx, cy, l.lit, a)
        val h = d / ArtistGeo.RING_HALF + ringHalo(d) + 1
        val o = d / ArtistGeo.RING_HALF
        val grid = rec as? GridRecord
        listOf(
            Edges(cx - h, cy - h, cx + h, cy - o),
            Edges(cx - h, cy + o, cx + h, cy + h),
            Edges(cx - h, cy - o, cx - o, cy + o),
            Edges(cx + o, cy - o, cx + h, cy + o),
        ).forEachIndexed { k, e -> grid?.edges("$n.halo$i.$k", KIND_GLOW, e, Ink.white, l.lit) }
    }
    val c = Offset(cx.toFloat(), cy.toFloat())
    ring(c, d.toFloat(), stroke.toFloat(), Ink.white, al)
    glyph(g, c, (d / ArtistGeo.RING_HALF).toFloat(), Ink.white, al)
    rec?.ring("$n.ring$i", c, d.toFloat(), al)
    rec?.glyphs("$n.glyph$i", listOf(g), c, (d / ArtistGeo.RING_HALF).toFloat(), al)
}

fun detailLabel(): Role = Role(Tok.type_detailLabel_size, Roles.detailTitle.weight, Roles.detailTitle.case, Tok.type_detailLabel_alpha, Roles.detailTitle.tracking)

fun artistHeader(): Role = Role(Roles.pageHeader.size, Roles.pivotActive.weight, Roles.pivotActive.case, Roles.pageHeader.alpha, Roles.pageHeader.tracking)

fun artistChrome(m: TextMeasurer, fonts: FontFamily.Resolver, s: Scale, density: Density, covers: Covers?): Chrome {
    val word = MusicPage.Hub.pivots[HUB_ARTISTS]
    val role = artistHeader()
    val base = chromeOf(m, fonts, s, density, word, MusicPage.Artist(0L).pivots, covers)
    return Chrome(s, m.word(word, role, roleStyle(role, s, density, Ink.header, ppu = s.stdPpu), fonts, density, covers), base.pivots)
}

@Composable
fun rememberArtistChrome(s: Scale): Chrome {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    return remember(s, density, fonts, covers) { artistChrome(m, fonts, s, density, covers) }
}

class ArtistDraw(
    val m: ArtistModel,
    val geo: ArtistGeo,
    private val nameOf: (String) -> Word,
    val label: Word,
    private val albumOf: (Row, Boolean) -> AlbumInk,
    private val songOf: (RowModel) -> RowInk,
    val sides: List<WordSide?>,
    val headerSide: WordSide,
    val accent: Color,
    val accentOff: Boolean,
) {
    val s: Scale get() = geo.s
    private var name: Pair<String, Word>? = null
    private val albums = HashMap<Pair<Row, Boolean>, AlbumInk>()
    private val songs = HashMap<RowModel, RowInk>()

    fun nameWord(): Word {
        val n = m.name.orEmpty()
        name?.let { if (it.first == n) return it.second }
        return nameOf(n).also { name = n to it }
    }

    fun albumInk(r: Row, lit: Boolean): AlbumInk = albums.getOrPut(r to lit) { albumOf(r, lit) }

    fun songInk(r: RowModel): RowInk = songs.getOrPut(r) { songOf(r) }

    fun keep(rows: Set<Row>, models: Set<RowModel>) {
        albums.keys.retainAll { it.first in rows }
        songs.keys.retainAll(models)
    }

    fun DrawScope.name(rec: DrawRecord?, n: String) {
        val w = nameWord()
        clipped(rec, geo.nameX.toFloat(), 0f, size.width, size.height) {
            word(w, geo.nameX.toFloat(), geo.nameBase.toFloat(), Ink.white, Roles.detailName.alpha)
            rec?.word("$n.name", w, geo.nameX.toFloat(), geo.nameBase.toFloat(), Roles.detailName.alpha)
        }
    }

    fun DrawScope.rings(looks: List<RingLook>, rec: DrawRecord?, n: String) {
        for (i in 0 until RINGS) detailRing(RING_GLYPHS[i], i, geo.ringCx(i), geo.ringCy, geo.ring, looks[i], s, rec, n)
    }

    fun DrawScope.label(rec: DrawRecord?, n: String) {
        word(label, geo.labelX.toFloat(), geo.labelBase.toFloat(), Ink.white, detailLabel().alpha)
        rec?.word("$n.label", label, geo.labelX.toFloat(), geo.labelBase.toFloat(), detailLabel().alpha)
    }

    fun DrawScope.block(now: Long, hideFocus: Boolean, rec: DrawRecord?, n: String) = block((0 until RINGS).map { m.ringLook(it, now, hideFocus) }, rec, n)

    fun DrawScope.block(looks: List<RingLook>, rec: DrawRecord?, n: String) {
        name(rec, n)
        rings(looks, rec, n)
        label(rec, n)
    }

    fun DrawScope.albumRow(r: Int, look: ArtistLook, rec: DrawRecord?, n: String) {
        val a = m.panes[ARTIST_ALBUMS].row(r) ?: return
        val lit = m.litAlbum(a.id)
        val k = albumInk(a, lit)
        val side = geo.side
        val t = Edges(geo.artX, 0, geo.artX + side, side)
        val hidden = a.id == m.hiddenAlbum
        val key = a.art?.let { ArtKey.Cover(it, side) }
        val img = key?.let { m.art[it] }
        val boxed = key == null || (key in m.art && img == null)
        if (!hidden) {
            val boxRole = keptText(Roles.boxCaptionLib, s)
            val box = if (img == null && boxed) k.box else null
            artTile(t, img, box, boxRole, 0, look.lit, look.ring, s)
            val grid = rec as? GridRecord
            val frame = if (look.lit > 0f) Rows.pressFrame(t, s) else emptyList()
            when {
                img != null -> grid?.art("$n.art", t, img, 1f, frame, ART_PEAK)
                box != null -> Tiles.line(t, s.stdPpu).let { ls -> ls.forEachIndexed { i, e -> grid?.edges("$n.line$i", KIND_BAR, e, Ink.white, Tok.boxLineAlpha, ls.filterIndexed { j, _ -> j != i }) } }
                !boxed -> grid?.edges("$n.art", KIND_TILE, t, Ink.tile, 1f, frame)
            }
            box?.lines?.forEachIndexed { i, w -> rec?.word("$n.box$i", w, box.x(w), Tiles.base(t, i, box.lines.size, box.pitch, box.ppu).toFloat(), boxRole.alpha) }
            frame.forEachIndexed { i, e -> grid?.edges("$n.frame$i", KIND_BAR, e, Ink.white, look.lit, frame.filterIndexed { j, _ -> j != i } + t) }
        }
        if (!hidden || m.hiddenRing) {
            val d = geo.rowRing
            val cx = geo.artX + side - geo.rowInset - d / ArtistGeo.RING_HALF
            val cy = geo.rowInset + d / ArtistGeo.RING_HALF
            val c = Offset(cx.toFloat(), cy.toFloat())
            val stroke = px(if (look.focused) Tok.ringStrokeFocused else Tok.ringStrokeIdle, s.stdPpu)
            val ra = if (look.focused) Tok.alpha_full else Tok.alpha_mid
            ringPress(d, stroke, cx, cy, look.ringLit)
            ring(c, d.toFloat(), stroke.toFloat(), Ink.white, ra)
            glyph(Glyphs.play, c, (d / ArtistGeo.RING_HALF).toFloat(), Ink.white, ra)
        }
        val clipBottom = px(Tok.detailRow, s.stdPpu).toFloat()
        val al = if (m.onRings) Tok.alpha_high else look.alpha
        val pn = m.panes[ARTIST_ALBUMS]
        val glow = if (lit && !accentOff) null else look.glow?.let { RowGlow(if (look.back) pn.glow.back else pn.glow.glow, it) }
        if (glow != null) {
            val tp = rolePx(keptText(Roles.detailTitle, s), s, s.stdPpu)
            val texts = k.title.mapIndexed { i, w -> GlowText(w, pen64(geo.textX.toFloat()), titleAt(i), tp) }
            pressGlow(glow.glow, texts, Edges(geo.textX, 0, geo.right, clipBottom.toInt()), s.ppu, glow.level)
            val reach = px(Tok.pressGlowDilate, s.ppu) + px(Tok.pressGlow, s.ppu)
            (rec as? GridRecord)?.edges("$n.glow", KIND_GLOW, Edges(geo.textX - reach, 0, geo.right, clipBottom.toInt()), Ink.white, glow.level)
        }
        clipRect(geo.textX.toFloat(), 0f, geo.right.toFloat(), clipBottom) {
            k.title.forEachIndexed { i, w ->
                val y = titleAt(i).toFloat()
                word(w, geo.textX.toFloat(), y, if (lit) accent else Ink.white, if (lit) 1f else al)
                rec?.word("$n.title$i", w, geo.textX.toFloat(), y, if (lit) 1f else al, if (lit) accent else Ink.white)
            }
            if ((a.year ?: 0) > 0) {
                val y = yearAt(k.title.size).toFloat()
                word(k.year, geo.textX.toFloat(), y, Ink.white, Roles.detailSub.alpha)
                rec?.word("$n.year", k.year, geo.textX.toFloat(), y, Roles.detailSub.alpha)
            }
        }
    }

    private fun titlePitch(): Long = fx(if (s.large) Tok.detailTitlePitchLarge else Tok.detailTitlePitch)

    private fun titleAt(i: Int): Int = px64(fx(Tok.detailTitleDrop) + titlePitch() * i, fx(s.stdPpu))

    private fun yearAt(n: Int): Int = px64(fx(Tok.detailTitleDrop) + titlePitch() * (maxOf(1, n) - 1) + fx(if (s.large) Tok.detailSubPitchLarge else Tok.detailSubPitch), fx(s.stdPpu))

    fun DrawScope.songRow(look: ArtistLook, k: RowInk?, rec: DrawRecord?, n: String, focusShown: Boolean) {
        if (k == null) return
        val glow = look.glow?.let { RowGlow(if (look.back) m.panes[ARTIST_SONGS].glow.back else m.panes[ARTIST_SONGS].glow.glow, it) }
        listRow(k, s, look.alpha, false, 1f, accent, accentOff, glow, null, focusShown)
        rec?.row(n, k, s, size.width.toInt(), look.alpha, false, 1f, glow != null, accent, accentOff, focusShown)
    }

    fun DrawScope.row(p: Int, r: Int, look: ArtistLook, rec: DrawRecord?, n: String, focusShown: Boolean) {
        if (p == ARTIST_ALBUMS) {
            albumRow(r, look, rec, n)
        } else {
            songRow(look, m.songModel(r)?.let { songInk(it) }, rec, n, focusShown)
        }
    }

    fun DrawScope.content(p: Int, now: Long, hideFocus: Boolean, covered: Boolean, rec: DrawRecord?, n: String) {
        val pn = m.panes[p]
        if (pn.count == 0) return
        val t = pn.track
        val first = t.rowAt(t.q)
        val last = min(pn.count - 1, t.rowAt(t.q + geo.view - 1))
        val shown = !m.onRings && !hideFocus
        clipped(rec, 0f, geo.clipTop.toFloat(), size.width, geo.bottom.toFloat()) {
            for (r in first..last) {
                shifted(rec, 0f, geo.rowTop(t, r).toFloat()) { row(p, r, m.look(p, r, now, hideFocus), rec, "$n.row$r", shown && !covered) }
            }
        }
    }

    fun leaveBands(): NpBands {
        val w = s.widthPx
        val h = s.heightPx
        val bottom = min(geo.bottom, h)
        val head = geo.pivotTop
        val nameTop = px64(fx(s.board.detailNameBaseline) - fxMul(fx(Tok.capHeight), fx(Tok.type_detailName_size)), fx(s.ppu))
        val d = geo.ring
        val halo = ringHalo(d) + 1
        val top = geo.ringTop
        val rows = geo.clipTop
        val r0 = maxOf(nameTop, top - halo)
        val r1 = min(rows, top + d + halo)
        val cx = (0 until RINGS).map { geo.ringCx(it) }
        val xs = listOf(0) + cx.zipWithNext { a, b -> (a + b) shr 1 } + w
        val parts = listOf(Part.NpLRing0, Part.NpLRing1, Part.NpLRing2)
        val pressRing = if (m.onRings) m.ring else -1
        val out = ArrayList<Pair<Edges, Part>>()
        fun band(e: Edges, p: Part) {
            val c = Edges(e.x0.coerceIn(0, w), e.y0.coerceIn(0, h), e.x1.coerceIn(0, w), e.y1.coerceIn(0, h))
            if (c.w > 0 && c.h > 0) out += c to p
        }
        band(Edges(0, 0, w, head), Part.NpLHead)
        band(Edges(0, head, w, nameTop), Part.NpLPivot)
        band(Edges(0, nameTop, w, r0), Part.NpLName)
        for (i in 0 until RINGS) band(Edges(xs[i], r0, xs[i + 1], r1), if (i == pressRing) Part.NpLPress else parts[i])
        band(Edges(0, r1, w, rows), Part.NpLLabel)
        band(Edges(0, rows, w, bottom), Part.NpLLabel)
        if (!m.onRings) {
            val pn = m.pane
            val f = pn.focus.row
            if (f >= 0 && f < pn.count) band(Edges(0, maxOf(rows, geo.rowTop(pn.track, f)), w, min(bottom, geo.rowTop(pn.track, f + 1))), Part.NpLPress)
        }
        return NpBands(NpLeave.Detail, out)
    }
}

fun artistDraw(m: ArtistModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, chrome: Chrome, accent: Color, accentOff: Boolean): ArtistDraw {
    val geo = ArtistGeo(s)
    val sp = s.stdPpu
    val ns = roleStyle(Roles.detailName, s, density, Ink.white)
    val lr = keptText(detailLabel(), s)
    val title = keptText(Roles.detailTitle, s)
    val ts = roleStyle(title, s, density, Ink.white, ppu = sp)
    val us = ts.copy(fontWeight = Fonts.weight(Accent.bold(title.weight, accentOff)))
    val tw = textWidth(title, ts, fonts, density, covers)
    val sub = keptText(Roles.detailSub, s)
    val ss = roleStyle(sub, s, density, Ink.white, ppu = sp)
    val box = keptText(Roles.boxCaptionLib, s)
    val bs = roleStyle(box, s, density, Ink.white, ppu = sp)
    val width64 = pen64((geo.right - geo.textX).toFloat())
    fun album(r: Row, lit: Boolean): AlbumInk {
        val t = r.title
        val rows = wrapRanges(t, width64, Tok.detailTitleLines.toInt(), { b, e -> if (e > b) pen64(tw(t.substring(b, e))) else 0 }, { p, e -> nextCluster(t, p, e) })
        return AlbumInk(
            rows.map { (b, e) -> measurer.word(t.substring(b, e), title, if (lit) us else ts, fonts, density, covers) },
            measurer.word((r.year ?: 0).toString(), sub, ss, fonts, density, covers),
            boxInk(measurer, fonts, t, box, bs, Edges(geo.artX, 0, geo.artX + geo.side, geo.side), Tok.boxLines.toInt(), sp, density, covers),
        )
    }
    val base = px(s.board.page_pivotBaseline, s.ppu).toFloat()
    val pivots = MusicPage.Artist(m.id).pivots.mapIndexed { i, p -> morphSide(measurer, fonts, s, density, covers, p, Roles.pivotActive, false, Roles.pivotActive.alpha, chrome.pivotX(i), base, i) }
    val header = morphSide(measurer, fonts, s, density, covers, chrome.header.text, artistHeader(), true, chrome.headerAlpha, chrome.headerRestX(), px(Tok.page_headerBaseline, sp).toFloat(), -1)
    return ArtistDraw(
        m,
        geo,
        { n -> measurer.word(n, Roles.detailName, ns, fonts, density, covers) },
        measurer.word(IN_COLLECTION, lr, roleStyle(lr, s, density, Ink.white, ppu = sp), fonts, density, covers),
        ::album,
        { r -> rowInk(measurer, fonts, r, s, density, accentOff, covers) },
        pivots,
        header,
        accent,
        accentOff,
    )
}

class ArtistPaint(
    private val d: ArtistDraw,
    override val chrome: Chrome,
    private val accent: Color,
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    private val p = m.pivot
    override val header: Word = chrome.header
    override val item: Int = -1
    override val grounded: Boolean = false
    override val kind: PageKind = PageKind.Plain
    override val top: Int = geo.clipTop
    override val bottom: Int = geo.bottom
    override val count: Int get() = m.panes[p].count
    override val scroll: Int get() = m.panes[p].track.q

    override fun rowY(i: Int): Int = geo.boxTop + m.panes[p].track.topOf(i)

    override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val look = m.look(p, i, now, false)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(p, i, look, rec, "$name.row$i", true) } }
    }

    override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) {
        with(d) {
            block(now, false, rec, name)
            content(p, now, false, false, rec, name)
        }
    }

    override fun wordSide(pivot: Boolean): WordSide? =
        if (pivot) d.sides.getOrNull(chrome.active)?.let { WordSide(it.text, it.s, it.look, chrome.pivotX(chrome.active), it.y, it.pivot, it.header, it.word) } else d.headerSide

    override fun pivotRow(): Edges = Edges(0, geo.pivotTop, geo.s.widthPx, geo.contentTop)

    override fun lineBand(): Edges = Edges(0, geo.pivotTop, geo.s.widthPx, geo.nameBase + max(1, Metrics.descent(rolePx(Roles.detailName, geo.s))))

    override fun DrawScope.pivotRowInk(rec: DrawRecord?) {
        with(d) { name(rec, "pivrow") }
    }

    override fun tileRect(album: Long): Edges? = m.rectOf(album, geo)

    override fun DrawScope.tile(album: Long, side: Float, cx: Float, cy: Float, alpha: Float, rec: DrawRecord?) {
        val r = m.panes[ARTIST_ALBUMS].paging.window?.rows?.firstOrNull { it.id == album }
        albumTile(r?.art?.let { m.art[ArtKey.Cover(it, geo.side)] }, side, cx, cy, alpha, rec)
    }

    override fun hide(h: TileHide?) {
        m.hiddenAlbum = h?.album ?: -1L
        m.hiddenRing = h?.ring == true
    }

    override fun npLeaveBands(): NpBands = d.leaveBands()

    override fun thumbFade(at: Long): Float = m.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = geo.thumb(m.panes[p].track) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, groundBands(geo.s.heightPx, true), accent)
    }
}
