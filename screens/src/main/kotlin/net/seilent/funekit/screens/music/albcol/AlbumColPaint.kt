package net.seilent.funekit.screens.music.albcol

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Fonts
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.core.Section
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptText
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.BoxInk
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.artTile
import net.seilent.funekit.kit.boxInk
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.letterTile
import net.seilent.funekit.kit.nextCluster
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.pressFrame
import net.seilent.funekit.kit.ring
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.TileHide
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.textWidth
import net.seilent.funekit.kit.word
import net.seilent.funekit.kit.wrapRanges
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.album.albumTile
import net.seilent.funekit.screens.music.hub.ART_PEAK
import net.seilent.funekit.screens.music.hub.GridRecord
import net.seilent.funekit.screens.music.hub.KIND_TILE
import net.seilent.funekit.kit.thumb as drawThumb

class ColInk(val title: List<Word>, val artist: Word, val tracks: List<Word>, val box: BoxInk?)

class AlbumColDraw(
    val m: AlbumColModel,
    val geo: AlbumColGeo,
    private val inkOf: (ColEntry) -> ColInk,
    private val letterOf: (Int) -> Word,
    val headerSide: WordSide,
    val accent: Color,
    val accentOff: Boolean,
) {
    val s: Scale get() = geo.s
    private val inks = HashMap<ColEntry, ColInk>()
    private val letters = HashMap<Int, Word>()

    fun ink(e: ColEntry): ColInk = inks.getOrPut(e.copy(art = null, boxed = false, hidden = false)) { inkOf(e) }

    fun letter(l: Int): Word = letters.getOrPut(l) { letterOf(l) }

    fun keep(entries: Set<ColEntry>) {
        val keys = entries.map { it.copy(art = null, boxed = false, hidden = false) }.toSet()
        inks.keys.retainAll(keys)
    }

    fun DrawScope.row(e: ColEntry, look: ColLook, k: ColInk?, rec: DrawRecord?, name: String) {
        val sp = s.stdPpu
        if (e.letter >= 0) {
            val t = geo.letterTile()
            val bw = px(if (look.focused) Tok.letterTileBorderFocused else Tok.letterTileBorder, sp)
            val ba = if (look.focused) Tok.alpha_full else Tok.letterTileBorderAlpha
            letterTile(t, bw, ba)
            pressFrame(t, s, if (look.ring > 0f) look.lit else 0f, look.ring)
            val w = letter(e.letter)
            val x = (t.x1 - px(Tok.letterInsetX, sp)) - w.width
            val base = (t.y1 - px(Tok.letterInsetBaseline, sp)).toFloat()
            clipRect(t.x0.toFloat(), t.y0.toFloat(), t.x1.toFloat(), t.y1.toFloat()) { word(w, x, base, Ink.white, Roles.letterGlyph.alpha) }
            (rec as? GridRecord)?.edges(name, KIND_TILE, t, Ink.white, ba)
            rec?.word("$name.text", w, x, base, Roles.letterGlyph.alpha)
            return
        }
        if (e.row == null) return
        val side = geo.side
        val t = Edges(0, 0, side, side)
        if (!e.hidden) {
            artTile(t, e.art, if (e.art == null && e.boxed) k?.box else null, keptText(Roles.boxCaptionLib, s), 0, look.lit, look.ring, s)
            val grid = rec as? GridRecord
            if (e.art != null) grid?.art("$name.art", t, e.art, 1f, peakOff = ART_PEAK) else if (!e.boxed) grid?.edges("$name.art", KIND_TILE, t, Ink.tile, 1f)
            val c = Offset(geo.ringX.toFloat(), geo.ringDrop.toFloat())
            val stroke = px(if (look.focused) Tok.ringStrokeFocused else Tok.ringStrokeIdle, sp).toFloat()
            val ra = if (look.focused) Tok.alpha_full else Tok.alpha_mid
            ring(c, geo.ring.toFloat(), stroke, Ink.white, ra)
            glyph(Glyphs.play, c, geo.ring / RING_SLOT, Ink.white, ra)
        }
        if (k == null) return
        clipRect(geo.textX.toFloat(), 0f, geo.right.toFloat(), side.toFloat()) {
            val tc = if (e.accent) accent else Ink.white
            k.title.forEachIndexed { i, w ->
                word(w, geo.textX.toFloat(), geo.titleAt(i).toFloat(), tc, Roles.albcolTitle.alpha)
                rec?.word("$name.title$i", w, geo.textX.toFloat(), geo.titleAt(i).toFloat(), Roles.albcolTitle.alpha, tc)
            }
            val line = geo.artistAt(k.title.size)
            word(k.artist, geo.textX.toFloat(), line.toFloat(), Ink.white, Roles.albcolArtist.alpha)
            rec?.word("$name.artist", k.artist, geo.textX.toFloat(), line.toFloat(), Roles.albcolArtist.alpha)
            var y = line + geo.trackDrop
            for ((i, w) in k.tracks.withIndex()) {
                if (y + geo.tail > side) break
                word(w, geo.textX.toFloat(), y.toFloat(), Ink.white, Roles.sectionLabel.alpha)
                rec?.word("$name.track$i", w, geo.textX.toFloat(), y.toFloat(), Roles.sectionLabel.alpha)
                y += geo.trackPitch
            }
        }
    }

    fun tileArt(album: Long): ImageBitmap? {
        val n = m.count
        for (r in 0 until n) {
            val a = m.row(r) ?: continue
            if (a.id == album) return a.art?.let { m.art[ArtKey.Cover(it, geo.side)] }
        }
        return null
    }
}

private const val RING_SLOT = 2f

fun albumColDraw(m: AlbumColModel, s: Scale, measurer: TextMeasurer, fonts: FontFamily.Resolver, density: Density, covers: Covers?, header: WordSide, accent: Color, accentOff: Boolean): AlbumColDraw {
    val geo = AlbumColGeo(s)
    val sp = s.stdPpu
    val ts = roleStyle(Roles.albcolTitle, s, density, Ink.white, ppu = sp)
    val tw = textWidth(Roles.albcolTitle, ts, fonts, density, covers)
    val us = ts.copy(fontWeight = Fonts.weight(Accent.bold(Roles.albcolTitle.weight, accentOff)))
    val asStyle = roleStyle(Roles.albcolArtist, s, density, Ink.white, ppu = sp)
    val ks = roleStyle(Roles.sectionLabel, s, density, Ink.white, ppu = sp)
    val box = keptText(Roles.boxCaptionLib, s)
    val bs = roleStyle(box, s, density, Ink.white, ppu = sp)
    val ls = roleStyle(Roles.letterGlyph, s, density, Ink.white, ppu = sp)
    val width64 = pen64((geo.right - geo.textX).toFloat())
    fun ink(e: ColEntry): ColInk {
        val a = e.row ?: return ColInk(emptyList(), Word.none, emptyList(), null)
        val title = a.title
        val rows = wrapRanges(title, width64, geo.titleLines, { b, en -> if (en > b) pen64(tw(title.substring(b, en))) else 0 }, { p, en -> nextCluster(title, p, en) })
        val lines = rows.map { (b, en) -> measurer.word(title.substring(b, en), Roles.albcolTitle, if (e.accent) us else ts, fonts, density, covers) }
        return ColInk(
            lines,
            measurer.word(a.sub, Roles.albcolArtist, asStyle, fonts, density, covers),
            e.tracks.map { measurer.word(it, Roles.sectionLabel, ks, fonts, density, covers) },
            boxInk(measurer, fonts, title, box, bs, Edges(0, 0, geo.side, geo.side), Tok.boxLines.toInt(), sp, density, covers),
        )
    }
    return AlbumColDraw(
        m,
        geo,
        ::ink,
        { l -> measurer.word(Section.letter(l), Roles.letterGlyph, ls, fonts, density, covers) },
        header,
        accent,
        accentOff,
    )
}

class AlbumColPaint(
    private val d: AlbumColDraw,
    override val chrome: Chrome,
    private val accent: Color,
) : PagePaint {
    private val m = d.m
    private val geo = d.geo
    override val header: Word = chrome.header
    override val item: Int = -1
    override val grounded: Boolean = false
    override val kind: PageKind = PageKind.Plain
    override val albumCol: Boolean = true
    override val top: Int = geo.clipTop
    override val bottom: Int = geo.bottom
    override val count: Int get() = m.count
    override val scroll: Int get() = m.track.q

    override fun rowY(i: Int): Int = geo.boxTop + m.track.topOf(i)

    override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

    override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) {
        val e = m.entry(i)
        val look = m.look(i, now, true)
        shifted(rec, 0f, y.toFloat()) { with(d) { row(e, look, ink(e), rec, "$name.row$i") } }
    }

    override fun wordSide(pivot: Boolean): WordSide? = if (pivot) null else d.headerSide

    override fun tileRect(album: Long): Edges? = m.rectOf(album)

    override fun DrawScope.tile(album: Long, side: Float, cx: Float, cy: Float, alpha: Float, rec: DrawRecord?) = albumTile(d.tileArt(album), side, cx, cy, alpha, rec)

    override fun hide(h: TileHide?) {
        m.hidden = h?.album ?: -1L
    }

    override fun thumbFade(at: Long): Float = m.fade.at(at)

    override fun DrawScope.thumb(fade: Float, rec: DrawRecord?, name: String) {
        val r = geo.thumb(m.track) ?: return
        drawThumb(r, fade)
        rec?.thumbParts("$name.thumb", r, Tok.scrollbar_alpha * fade, groundBands(geo.s.heightPx, true), accent)
    }
}
