package net.seilent.funekit.kit

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import net.seilent.funekit.textPx
import kotlin.math.max

class Edges(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val w: Int get() = x1 - x0
    val h: Int get() = y1 - y0

    fun same(o: Edges): Boolean = x0 == o.x0 && y0 == o.y0 && x1 == o.x1 && y1 == o.y1
}

fun edges(x: Float, y: Float, w: Float, h: Float, ppu: Float) = Edges(px(x, ppu), px(y, ppu), px(x + w, ppu), px(y + h, ppu))

fun Modifier.pxSize(w: Int, h: Int): Modifier = layout { m, _ ->
    val p = m.measure(Constraints.fixed(max(0, w), max(0, h)))
    layout(p.width, p.height) { p.place(0, 0) }
}

fun Modifier.pxAt(e: Edges): Modifier = layout { m, _ ->
    val p = m.measure(Constraints.fixed(max(0, e.w), max(0, e.h)))
    layout(p.width, p.height) { p.place(e.x0, e.y0) }
}

private const val PEN = 64
private const val INDENT_DIGITS = 4
private const val HALF = 2

fun pen64(u: Float, ppu: Float): Int = Math.round(u * ppu * PEN)

fun pen64(px: Float): Int = Math.round(px * PEN)

fun penPx(x64: Int): Float = x64.toFloat() / PEN

fun penFloor(x64: Int): Int = Math.floorDiv(x64, PEN)

fun penCeil(x64: Int): Int = -Math.floorDiv(-x64, PEN)

class RowLines(val pitch: Int, val title: Int, val sub: Int, val titleX64: Int) {
    val titleX: Int get() = penFloor(titleX64)
}

object Layout {
    fun clipTop(s: Scale): Float = s.board.page_pivotBaseline + s.board.page_listClipDrop

    fun above(s: Scale): Int = max(0, px(s.board.page_contentTop, s.ppu) - px(clipTop(s), s.ppu))

    fun npArt(s: Scale): Edges = edges(Tok.npArtX, s.board.npArtTop, s.board.npArt, s.board.npArt, s.ppu)

    fun npRingCy(s: Scale): Float = if (s.navOn) s.board.npRingNavY else s.board.npRingY

    fun npRingCx(i: Int): Float = when (i) {
        0 -> Tok.npRingCx0
        1 -> Tok.npRingCx1
        else -> Tok.npRingCx2
    }

    fun npTitleBaseline(s: Scale): Float = s.board.npTitleY

    fun contentTop(s: Scale): Float = s.board.page_contentTop

    fun visibleRows(s: Scale): Float = (s.stdContentBottom - s.board.page_contentTop) / Tok.rowHeight

    fun homeFirstBaseline(): Float = Tok.homeFirstBaseline

    fun numberColumn64(s: Scale, digits: Int, digit64: Int): Int {
        val indent = pen64(Tok.songIndent, s.ppu)
        return max(indent, digits * digit64 + indent - INDENT_DIGITS * digit64)
    }

    fun collectionBase(s: Scale, top: Int): Int {
        val cap = Math.round(Tok.capHeight * rolePx(Roles.listCollection, s))
        return top + (px(Tok.rowPlayRing, s.ppu) + cap + 1) / HALF
    }

    fun thumbsTop(s: Scale, top: Int): Int = collectionBase(s, top) + px(Tok.rowThumbDrop, s.ppu)

    fun thumbsX(s: Scale, collection: Boolean): Int {
        val x64 = if (collection) pen64(Tok.rowCollectionTextX, s.ppu) else pen64(Tok.hubEdge + Tok.songIndent, s.ppu)
        return Math.floorDiv(x64 + pen64(Tok.rowThumbIndent, s.ppu) + PEN / HALF, PEN)
    }

    fun row(
        s: Scale,
        top: Int,
        collection: Boolean,
        bold: Boolean,
        countLine: Boolean,
        twoLine: Boolean,
        ring: Boolean,
        number64: Int = 0,
        appTile: Boolean = false,
        song: Boolean = false,
    ): RowLines {
        val pitch = Rows.pitch(s, collection, song)
        val inset = px(Tok.rowTextInset, s.ppu)
        val primary = rolePx(
            when {
                collection -> Roles.listCollection
                song -> Roles.listSong
                bold -> Roles.listPlaylist
                else -> Roles.listPrimary
            },
            s,
        )
        val secondary = rolePx(
            when {
                collection -> Roles.listCollectionCount
                song -> Roles.listSongSub
                countLine || bold -> Roles.listCount
                else -> Roles.listSecondary
            },
            s,
        )
        val cap = Math.round(Tok.capHeight * primary)
        val tile = if (appTile) Rows.appTile(s, top, collection) else null
        val title = when {
            tile != null -> Rows.capBaseline(tile, cap)
            collection -> top + (px(Tok.rowPlayRing, s.ppu) + cap + 1) / HALF
            song -> top + (px(Tok.dividerTileCollection, s.ppu) - cap - px(Tok.songSubDrop, s.ppu)) / HALF + cap
            twoLine -> top + inset + Metrics.ascent(primary)
            else -> Metrics.centered(top, pitch, primary)
        }
        val sub = when {
            song -> title + px(Tok.songSubDrop, s.ppu)
            collection && tile == null -> title + px(Tok.rowCountDrop, s.ppu) + Math.round(Tok.capHeight * secondary)
            else -> top + pitch - inset - Metrics.descent(secondary)
        }
        val x64 = when {
            tile != null -> pen64(Rows.appTextX(tile, s).toFloat())
            collection && ring -> pen64(Tok.rowCollectionTextX, s.ppu)
            ring -> pen64(Tok.hubEdge + Tok.songIndent, s.ppu)
            number64 > 0 && !song -> pen64(Tok.hubEdge, s.ppu) + number64
            else -> pen64(Tok.hubEdge, s.ppu)
        }
        return RowLines(pitch, title, sub, x64)
    }
}

class StatusBox(
    val top: Float,
    val height: Float,
    val right: Float,
    val gap: Float,
    val left: Float,
    val batteryW: Float,
    val batteryH: Float,
    val text: Float,
    val batteryNub: Float = 0f,
    val batteryNubH: Float = 0f,
)

fun pageStatus(): StatusBox = StatusBox(
    Tok.statusTop, Tok.statusHeight, Tok.edge, Tok.statusGap, Tok.backRingX + Tok.backRing, 0f, 0f, Tok.type_time_size,
)

fun npStatus(s: Scale): StatusBox = StatusBox(
    Tok.npStatusTop, Tok.npStatusHeight, Tok.npStatusRight, s.board.npStatusGap, Tok.npBackRingX + Tok.npBackRing,
    Tok.npStatusBatteryW, Tok.npStatusBatteryH, Tok.npStatusText, Tok.npStatusBatteryNub, s.board.npStatusBatteryNubH,
)

data class StatusLine(val top: Int, val size: Int, val gap: Int, val right: Int, val baseline: Int, val textPx: Int)

fun statusLine(s: Scale, box: StatusBox = pageStatus()): StatusLine {
    val top = px(box.top, s.ppu)
    val size = px(box.height, s.ppu)
    val text = textPx(box.text, s.ppu, s.board.minTextPx)
    return StatusLine(top, size, px(box.gap, s.ppu), px(s.w - box.right, s.ppu), Metrics.centered(top, size, text), text)
}
