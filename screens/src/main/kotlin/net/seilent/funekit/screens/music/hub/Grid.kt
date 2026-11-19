package net.seilent.funekit.screens.music.hub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Fonts
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.core.Section
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptText
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.BoxInk
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Tiles
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.artTile
import net.seilent.funekit.kit.boxInk
import net.seilent.funekit.kit.letterTile
import net.seilent.funekit.kit.pressFrame
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.api.ArtRef
import kotlin.math.ceil
import kotlin.math.floor

fun gridCols(w: Float): Int {
    var n = 1
    while (Tok.gridX + (n + 1) * Tok.gridTile + n * Tok.gridGap <= w - Tok.gridX) n++
    return n
}

class HubGrid(val s: Scale) {
    private val sp = s.stdPpu
    val cols: Int = gridCols(s.widthPx / sp)
    val tile: Int = px(Tok.gridTile, sp)
    val pitch: Int = px(if (s.large) Tok.tileRowPitchLarge else Tok.tileRowPitch, sp)
    val cellH: Int = px(if (s.large) Tok.tileCellHLarge else Tok.tileCellH, sp)
    private val dropU = if (s.large) Tok.tileCaptionDropLarge else Tok.tileCaptionDrop
    private val pitchU = if (s.large) Tok.tileCaptionPitchLarge else s.board.tileCaptionPitch
    val drop: Int = px(dropU, sp)
    val sub: Int = px(dropU + pitchU, sp)
    val band: Int = px(if (s.large) Tok.tileCaptionBandLarge else s.board.tileCaptionBand, sp)
    val border: Int = px(Tok.focus_border, sp)
    val caption: Role = keptText(Roles.gridCaption, s)
    val box: Role = keptText(Roles.boxCaptionLib, s)

    fun x(k: Int): Int = px(Tok.gridX + k * (Tok.gridTile + Tok.gridGap), sp)

    fun tileAt(k: Int, top: Int): Edges = Edges(x(k), top, x(k) + tile, top + tile)

    fun colAt(x: Float): Int = (0 until cols).firstOrNull { x >= x(it) && x < x(it) + tile } ?: -1

    fun letterBorder(focused: Boolean): Int = px(if (focused) Tok.letterTileBorderFocused else Tok.letterTileBorder, sp)

    fun letterRight(t: Edges): Int = t.x1 - px(Tok.letterInsetX, sp)

    fun letterBaseline(t: Edges): Int = t.y1 - px(Tok.letterInsetBaseline, sp)
}

data class GridCell(val letter: Int, val title: String, val sub: String, val ref: ArtRef?, val accent: Boolean)

class GridInk(val cell: GridCell, val title: Word, val sub: Word, val box: BoxInk?)

fun gridInk(m: TextMeasurer, fonts: FontFamily.Resolver, g: HubGrid, c: GridCell, k: Int, density: Density, accentOff: Boolean, covers: Covers?): GridInk {
    val s = g.s
    val sp = s.stdPpu
    if (c.letter >= 0) {
        val ls = roleStyle(Roles.letterGlyph, s, density, Ink.white, ppu = sp)
        return GridInk(c, m.word(Section.letter(c.letter), Roles.letterGlyph, ls, fonts, density, covers), Word.none, null)
    }
    val cs = roleStyle(g.caption, s, density, Ink.white, ppu = sp)
    val ts = if (c.accent) cs.copy(fontWeight = Fonts.weight(Accent.bold(g.caption.weight, accentOff))) else cs
    val bs = roleStyle(g.box, s, density, Ink.white, ppu = sp)
    return GridInk(
        c,
        m.word(c.title, g.caption, ts, fonts, density, covers),
        m.word(c.sub, g.caption, cs, fonts, density, covers),
        boxInk(m, fonts, c.title, g.box, bs, g.tileAt(k, 0), Tok.boxLines.toInt(), sp, density, covers),
    )
}

interface GridRecord {
    fun edges(name: String, kind: String, e: Edges, color: Color, alpha: Float, masks: List<Edges> = emptyList(), peakOff: String? = null)

    fun art(name: String, e: Edges, image: ImageBitmap, alpha: Float, masks: List<Edges> = emptyList(), peakOff: String? = null)
}

const val ART_PEAK = "the fixture art is a flat placeholder against the funeOS fixture covers, so the art gates on its edges only"

fun DrawScope.gridCell(
    g: HubGrid,
    k: Int,
    ink: GridInk,
    focused: Boolean,
    lit: Float,
    ring: Float,
    art: ImageBitmap?,
    boxed: Boolean,
    accent: Color,
    rec: DrawRecord?,
    name: String,
    hidden: Boolean = false,
) {
    val s = g.s
    val t = g.tileAt(k, 0)
    val c = ink.cell
    val grid = rec as? GridRecord
    if (c.letter >= 0) {
        val bw = g.letterBorder(focused)
        val ba = if (focused) Tok.alpha_full else Tok.letterTileBorderAlpha
        letterTile(t, bw, ba)
        pressFrame(t, s, if (ring > 0f) lit else 0f, ring)
        val x = g.letterRight(t) - ink.title.width
        val base = g.letterBaseline(t).toFloat()
        clipRect(t.x0.toFloat(), t.y0.toFloat(), t.x1.toFloat(), t.y1.toFloat()) {
            word(ink.title, x, base, Ink.white, Roles.letterGlyph.alpha)
        }
        if (grid != null) {
            val strips = Rows.letterBorder(t, bw)
            val glyph = if (ink.title.inked) {
                listOf(Edges(floor(x + ink.title.inkLeft).toInt() - 1, floor(base + ink.title.inkTop).toInt() - 1, ceil(x + ink.title.inkRight).toInt() + 1, ceil(base + ink.title.inkBottom).toInt() + 1))
            } else {
                emptyList()
            }
            grid.edges(name, KIND_TILE, t, Ink.white, ba)
            listOf("top", "bottom", "left", "right").forEachIndexed { i, side ->
                grid.edges("$name.tile.$side", KIND_BAR, strips[i], Ink.white, ba, strips.filterIndexed { j, _ -> j != i } + glyph)
            }
            if (ring > 0f) grid.edges("$name.frame", KIND_TILE, t, Ink.white, ring)
        }
        rec?.word("$name.text", ink.title, x, base, Roles.letterGlyph.alpha)
        return
    }
    val box = if (art == null && boxed && !hidden) ink.box else null
    val border = if (focused) g.border else 0
    if (!hidden) artTile(t, art, box, g.box, border, lit, ring, s)
    if (grid != null && !hidden) {
        val ring = if (border > 0) Rows.ringRects(t, border) else emptyList()
        when {
            art != null -> grid.art(name, t, art, 1f, ring, ART_PEAK)
            box != null -> Tiles.line(t, s.stdPpu).forEachIndexed { i, e -> grid.edges("$name.line$i", KIND_BAR, e, Ink.white, Tok.boxLineAlpha) }
            else -> grid.edges(name, KIND_TILE, t, Ink.tile, 1f, ring)
        }
        ring.forEachIndexed { i, e -> grid.edges("$name.focus$i", KIND_BAR, e, Ink.white, 1f, listOf(t) + ring.filterIndexed { j, _ -> j != i }) }
    }
    if (rec != null && box != null) {
        box.lines.forEachIndexed { i, w -> rec.word("$name.box$i", w, box.x(w), Tiles.base(t, i, box.lines.size, box.pitch, box.ppu).toFloat(), g.box.alpha) }
    }
    val cap = Edges(t.x0, t.y1, t.x1, t.y1 + g.band)
    val ta = if (c.accent) 1f else if (focused) Tok.alpha_full else g.caption.alpha
    val tc = if (c.accent) accent else Ink.white
    clipRect(cap.x0.toFloat(), cap.y0.toFloat(), cap.x1.toFloat(), cap.y1.toFloat()) {
        word(ink.title, t.x0.toFloat(), (t.y1 + g.drop).toFloat(), tc, ta)
        word(ink.sub, t.x0.toFloat(), (t.y1 + g.sub).toFloat(), Ink.white, g.caption.alpha)
    }
    if (rec != null) {
        rec.clip(cap.x0.toFloat(), cap.y0.toFloat(), cap.x1.toFloat(), cap.y1.toFloat()) {
            rec.word("$name.title", ink.title, t.x0.toFloat(), (t.y1 + g.drop).toFloat(), ta, tc)
            rec.word("$name.sub", ink.sub, t.x0.toFloat(), (t.y1 + g.sub).toFloat(), g.caption.alpha)
        }
    }
}

const val KIND_TILE = "tile"
const val KIND_BAR = "bar"
