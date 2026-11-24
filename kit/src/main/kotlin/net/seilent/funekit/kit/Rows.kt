package net.seilent.funekit.kit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.seilent.funekit.Fonts
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.roundToInt

private const val HALF = 2
private const val DIGITS = "0123456789"

enum class RowKind { Item, Divider }

data class RowModel(
    val kind: RowKind = RowKind.Item,
    val primary: String = "",
    val secondary: String = "",
    val album: String = "",
    val number: String = "",
    val trailing: String = "",
    val countLine: Boolean = false,
    val accent: Boolean = false,
    val bold: Boolean = false,
    val ring: Boolean = false,
    val ringToggle: Boolean = false,
    val ringOn: Boolean = false,
    val ringBare: Boolean = false,
    val hide: Boolean = false,
    val collection: Boolean = false,
    val song: Boolean = false,
    val libSub: Boolean = false,
    val glyph: Glyph = Glyphs.play,
    val wifi: Boolean = false,
    val arc: Int = 0,
    val level: Int = -1,
    val ringScale: Float = 1f,
    val art: Boolean = false,
    val appTile: Boolean = false,
)

class RingSpot(val cx: Int, val cy: Int, val d: Int, val stroke: Int, val alpha: Float)

object Rows {
    fun primaryRole(r: RowModel): Role = when {
        r.collection -> Roles.listCollection
        r.song -> Roles.listSong
        r.bold -> Roles.listPlaylist
        else -> Roles.listPrimary
    }

    fun secondaryRole(r: RowModel): Role = when {
        r.collection -> Roles.listCollectionCount
        r.song -> Roles.listSongSub
        r.countLine || r.bold -> Roles.listCount
        else -> Roles.listSecondary
    }

    fun pitch(s: Scale, collection: Boolean, song: Boolean = false): Int =
        px(if (collection) Tok.rowCollection else if (song) Tok.rowSong else Tok.rowHeight, s.ppu)

    fun pitch(s: Scale, r: RowModel): Int = pitch(s, r.collection, r.song)

    fun right(s: Scale): Int = px(s.w - Tok.hubEdge, s.ppu)

    private fun ringSide(s: Scale, collection: Boolean): Int = px(if (collection) Tok.rowPlayRing else Tok.rowRing, s.ppu)

    private fun ringCx(s: Scale, d: Int): Int = px(Tok.hubEdge, s.ppu) + d / HALF

    private fun ringCy(s: Scale, top: Int, collection: Boolean, appTile: Boolean, d: Int): Int =
        if (collection && !appTile) top + d / HALF else top + pitch(s, collection) / HALF

    private fun square(cx: Int, cy: Int, d: Int): Edges {
        val x0 = cx - d / HALF
        val y0 = cy - d / HALF
        return Edges(x0, y0, x0 + d, y0 + d)
    }

    fun ring(s: Scale, top: Int, r: RowModel, focused: Boolean): RingSpot {
        val d = ringSide(s, r.collection)
        val lit = if (r.ringToggle) r.ringOn else focused
        val stroke = px(if (focused) Tok.ringStrokeFocused else Tok.ringStrokeIdle, s.ppu)
        return RingSpot(ringCx(s, d), ringCy(s, top, r.collection, r.appTile, d), d, stroke, if (lit) Tok.alpha_full else Tok.alpha_mid)
    }

    fun artSlot(spot: RingSpot): Edges = square(spot.cx, spot.cy, spot.d)

    fun appTile(s: Scale, top: Int, collection: Boolean): Edges {
        val d = ringSide(s, collection)
        return square(ringCx(s, d), ringCy(s, top, collection, true, d), d)
    }

    fun capBaseline(tile: Edges, cap: Int): Int = tile.y0 + (tile.h + cap) / HALF

    fun appTextX(tile: Edges, s: Scale): Int = tile.x1 + px(Tok.tileLGutter, s.ppu)

    fun divider(s: Scale, top: Int, collection: Boolean, label: Float, appTile: Boolean = false, song: Boolean = false): Edges {
        if (appTile) return this.appTile(s, top, collection)
        val h = pitch(s, collection, song)
        val d = px(if (collection || song) Tok.dividerTileCollection else Tok.dividerTile, s.ppu)
        val inset = px(Tok.letterInsetX, s.ppu)
        val x = px(Tok.hubEdge, s.ppu)
        val y = if (collection || song) top else top + (h - d) / HALF
        return Edges(x, y, x + max(d, penCeil(pen64(label)) + inset + inset), y + d)
    }

    fun dividerBorder(s: Scale, focused: Boolean): Int = px(if (focused) Tok.letterTileBorderFocused else Tok.letterTileBorder, s.ppu)

    fun dividerAlpha(focused: Boolean): Float = if (focused) Tok.alpha_full else Tok.letterTileBorderAlpha

    fun letterRight(t: Edges, s: Scale): Int = t.x1 - px(Tok.letterInsetX, s.ppu)

    fun letterBaseline(t: Edges, s: Scale): Int = t.y1 - px(Tok.letterInsetBaseline, s.ppu)

    fun trailingX64(s: Scale, trailing64: Int): Int = pen64(right(s).toFloat()) - trailing64

    fun ringRects(r: Edges, w: Int): List<Edges> = listOf(
        Edges(r.x0 - w, r.y0 - w, r.x1 + w, r.y0),
        Edges(r.x0 - w, r.y1, r.x1 + w, r.y1 + w),
        Edges(r.x0 - w, r.y0, r.x0, r.y1),
        Edges(r.x1, r.y0, r.x1 + w, r.y1),
    )

    fun insetRing(t: Edges, center: Float, width: Float, ppu: Float): List<Edges> {
        val inset = px(center + width / HALF, ppu)
        val w = max(1, inset - px(center - width / HALF, ppu))
        return ringRects(Edges(t.x0 + inset, t.y0 + inset, t.x1 - inset, t.y1 - inset), w)
    }

    fun pressFrame(t: Edges, s: Scale): List<Edges> = insetRing(t, Tok.boxInset, Tok.pressFrame, s.stdPpu)

    fun letterBorder(t: Edges, bw: Int): List<Edges> = listOf(
        Edges(t.x0, t.y0, t.x1, t.y0 + bw),
        Edges(t.x0, t.y1 - bw, t.x1, t.y1),
        Edges(t.x0, t.y0 + bw, t.x0 + bw, t.y1 - bw),
        Edges(t.x1 - bw, t.y0 + bw, t.x1, t.y1 - bw),
    )
}

private fun DrawScope.fill(e: Edges, color: Color, alpha: Float) =
    drawRect(color, Offset(e.x0.toFloat(), e.y0.toFloat()), Size(e.w.toFloat(), e.h.toFloat()), alpha)

fun DrawScope.pressFrame(t: Edges, s: Scale) = Rows.pressFrame(t, s).forEach { fill(it, Ink.white, 1f) }

fun DrawScope.letterTile(t: Edges, bw: Int, borderAlpha: Float) {
    fill(t, Ink.bg, 1f)
    Rows.letterBorder(t, bw).forEach { fill(it, Ink.white, borderAlpha) }
}

private val digits = HashMap<List<Any>, Int>()

class RowInk(
    val r: RowModel,
    val primary: Word,
    val secondary: Word,
    val album: Word,
    val albumX64: Int,
    val number: Word,
    val trailing: Word,
    val lines: RowLines,
    val right: Int,
    val trailingX64: Int,
    val primaryRight: Int,
    val spot: RingSpot?,
    val spotFocused: RingSpot?,
    val glowText: List<GlowText>,
    val glowClip: Edges,
)

fun rowInk(m: TextMeasurer, fonts: FontFamily.Resolver, r: RowModel, s: Scale, density: Density, accentOff: Boolean, covers: Covers?): RowInk {
    fun style(role: Role) = roleStyle(role, s, density, Ink.white)
    fun text(t: String, role: Role, st: TextStyle = style(role)) = m.word(t, role, st, fonts, density, covers, s.board.fontHanMinPx)
    val divider = r.kind == RowKind.Divider
    val p = if (divider) Roles.letterGlyph else Rows.primaryRole(r)
    val ps = if (r.accent && !divider) style(p).copy(fontWeight = Fonts.weight(Accent.bold(p.weight, accentOff))) else style(p)
    val primary = text(r.primary, p, ps)
    val count = style(Roles.listCount)
    val digit = if (r.number.isEmpty() || divider) 0 else digits.getOrPut(listOf(count, density.density, s.ppu)) {
        DIGITS.maxOf { pen64(text(it.toString(), Roles.listCount, count).width) }
    }
    val number = if (divider) Word.none else text(r.number, Roles.listCount, count)
    val trailing = if (divider) Word.none else text(r.trailing, Roles.listCount, count)
    val number64 = if (r.number.isEmpty()) 0 else Layout.numberColumn64(s, r.number.length, digit)
    val lines = Layout.row(s, 0, r.collection, r.bold, r.countLine, r.secondary.isNotEmpty(), r.ring, number64, r.appTile, r.song)
    val right = Rows.right(s)
    val tx64 = Rows.trailingX64(s, if (r.trailing.isEmpty()) 0 else pen64(trailing.width))
    val primaryRight = if (r.trailing.isEmpty()) right else penFloor(tx64) - px(Tok.gutter, s.ppu)
    val secondary = if (divider) Word.none else text(r.secondary, Rows.secondaryRole(r))
    val album = if (divider || !r.song || r.album.isEmpty()) Word.none else text(r.album, Rows.secondaryRole(r))
    val albumX64 = if (r.secondary.isEmpty()) 0 else pen64(secondary.inkRight) + pen64(Tok.gutter, s.ppu) - pen64(album.inkLeft)
    return RowInk(
        r,
        primary,
        secondary,
        album,
        albumX64,
        number,
        trailing,
        lines,
        right,
        tx64,
        primaryRight,
        if (r.ring) Rows.ring(s, 0, r, false) else null,
        if (r.ring) Rows.ring(s, 0, r, true) else null,
        listOf(GlowText(primary, lines.titleX64, lines.title, rolePx(p, s))),
        Edges(lines.titleX, 0, primaryRight, lines.pitch),
    )
}

@Composable
fun rememberRowInk(r: RowModel, s: Scale, accentOff: Boolean): RowInk {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    return remember(r, s, accentOff, density, fonts, covers) { rowInk(m, fonts, r, s, density, accentOff, covers) }
}

class RowWord(val text: String, val x64: Int, val base: Int, val look: Float)

fun listRowWord(t: RowTrack, top: Int, bottom: Int, row: Int, grid: Boolean, m: RowModel?, ink: RowInk?, s: Scale): RowWord? {
    if (m == null || ink == null || grid || row < 0 || row >= t.count) return null
    val y = top + t.topOf(row) - t.q
    if (y < top || y + t.topOf(row + 1) - t.topOf(row) > bottom) return null
    if (m.kind != RowKind.Item || m.accent || m.primary.isEmpty()) return null
    var right = Rows.right(s)
    if (m.trailing.isNotEmpty()) right = penFloor(pen64(right.toFloat()) - pen64(ink.trailing.width)) - px(Tok.gutter, s.ppu)
    val x64 = ink.lines.titleX64
    if (x64 + pen64(ink.primary.width) > pen64(right.toFloat())) return null
    return RowWord(m.primary, x64, y + ink.lines.title, Rows.primaryRole(m).alpha)
}

class RowGlow(val glow: Glow, val level: Float)

fun DrawScope.listRow(
    ink: RowInk,
    s: Scale,
    alpha: Float,
    focused: Boolean = false,
    press: Float = 1f,
    accent: Color = Ink.white,
    accentOff: Boolean = true,
    glow: RowGlow? = null,
    art: ImageBitmap? = null,
    focusShown: Boolean = false,
    ringLit: Float = 0f,
) {
    val r = ink.r
    val h = Rows.pitch(s, r)
    val w = size.width
    if (r.kind == RowKind.Divider) {
        val t = Rows.divider(s, 0, r.collection, ink.primary.width, r.appTile, r.song)
        letterTile(t, Rows.dividerBorder(s, focused), Rows.dividerAlpha(focused))
        clipRect(0f, 0f, w, h.toFloat()) {
            word(ink.primary, Rows.letterRight(t, s) - ink.primary.width, Rows.letterBaseline(t, s).toFloat(), Ink.white, Roles.letterGlyph.alpha)
        }
        return
    }
    val lines = ink.lines
    val base = lines.title.toFloat()
    run {
        val spot = if (focused) ink.spotFocused else ink.spot
        if (spot != null && r.art) {
            val slot = Rows.artSlot(spot)
            clipRect(0f, 0f, w, h.toFloat()) {
                if (art == null) {
                    fill(slot, Ink.tile, 1f)
                } else {
                    drawImage(art, dstOffset = IntOffset(slot.x0, slot.y0), dstSize = IntSize(slot.w, slot.h))
                }
            }
        } else if (spot != null) {
            val c = Offset(spot.cx.toFloat(), spot.cy.toFloat())
            val d = if (r.ringScale != 1f) (spot.d * r.ringScale).roundToInt() else spot.d
            val stroke = spot.stroke * d.toFloat() / spot.d
            if (ringLit > 0f) {
                val g = ringHalo(spot.d).toFloat()
                clipRect(-g, -g, w + g, h + g) { ringPress(spot.d, spot.stroke, spot.cx, spot.cy, ringLit) }
            }
            clipRect(0f, 0f, w, h.toFloat()) {
                if (!r.ringBare) ring(c, d.toFloat(), stroke, Ink.white, spot.alpha)
                val slot = (d / HALF).toFloat()
                if (r.wifi) wifi(c, slot, r.level, Ink.white, spot.alpha) else glyph(r.glyph, c, slot, Ink.white, spot.alpha)
                if (r.arc > 0) arc(c, d.toFloat(), stroke, Tok.ringArcStart, r.arc.toFloat(), accent, 1f)
            }
        }
        if (r.number.isNotEmpty() && !r.song) {
            clipRect(0f, 0f, w, h.toFloat()) {
                word(ink.number, penPx(pen64(Tok.hubEdge, s.ppu)), base, Ink.white, Roles.listCount.alpha)
            }
        }
        if (r.trailing.isNotEmpty()) {
            clipRect(0f, 0f, w, h.toFloat()) {
                word(ink.trailing, penPx(ink.trailingX64), base, Ink.white, Roles.listCount.alpha)
            }
        }
        val clipL = lines.titleX.toFloat()
        if (glow != null && !r.hide && (!r.accent || accentOff)) pressGlow(glow.glow, ink.glowText, ink.glowClip, s.ppu, glow.level)
        if (!r.hide) {
            clipRect(clipL, 0f, ink.primaryRight.toFloat(), h.toFloat()) {
                word(ink.primary, penPx(lines.titleX64), base, if (r.accent) accent else Ink.white, if (r.accent && (!focusShown || r.ring)) 1f else alpha)
            }
        }
        if (r.secondary.isNotEmpty()) {
            val sa = if (r.libSub && !r.song) Roles.listLibSecondary.alpha else Rows.secondaryRole(r).alpha
            clipRect(clipL, 0f, ink.right.toFloat(), h.toFloat()) {
                word(ink.secondary, penPx(lines.titleX64), lines.sub.toFloat(), Ink.white, sa)
            }
        }
        if (ink.album.text.isNotEmpty() && lines.titleX64 + ink.albumX64 < pen64(ink.right.toFloat())) {
            clipRect(clipL, 0f, ink.right.toFloat(), h.toFloat()) {
                word(ink.album, penPx(lines.titleX64 + ink.albumX64), lines.sub.toFloat(), Ink.white, Rows.secondaryRole(r).alpha)
            }
        }
    }
}

@Composable
fun ListRow(
    r: RowModel,
    s: Scale,
    alpha: Float,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    press: () -> Float = { 1f },
    accent: Color = Ink.white,
    accentOff: Boolean = true,
    glow: (() -> RowGlow?)? = null,
    focusShown: Boolean = false,
    ringLit: () -> Float = { 0f },
) {
    val ink = rememberRowInk(r, s, accentOff)
    Canvas(modifier.pxSize(s.widthPx, Rows.pitch(s, r))) { listRow(ink, s, alpha, focused, press(), accent, accentOff, glow?.invoke(), focusShown = focusShown, ringLit = ringLit()) }
}
