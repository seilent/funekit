package net.seilent.funekit.kit.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import java.text.Bidi
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val HALF = 2f

class LockLayout(val s: Scale) {
    private val b = s.board
    private val p = s.stdPpu
    private val titleU = b.lockArtY + Tok.lockTitleDrop
    private val artistU = titleU + Tok.lockArtistPitch
    val left: Int = s.left
    val right64: Int = pen64(Tok.canvasW - Tok.edge, p)
    val clockBase: Int = px(Tok.saverClockBaseline, p)
    val artX: Int = px(Tok.edge, p)
    val artY: Int = px(b.lockArtY, p)
    val artSide: Int = px(Tok.lockArt, p)
    val btGlyph: Int = px(b.lockBtGlyph, p)
    val textX64: Int = pen64(Tok.lockTextX, p)
    val textX0: Int = px(Tok.lockTextX, p)
    val textX1: Int = px(Tok.canvasW - Tok.edge, p)
    val titleBase: Int = px(titleU, p)
    val artistBase: Int = px(artistU, p)
    val albumBase: Int = px(artistU + Tok.lockAlbumPitch, p)
    val lineBase: Int = px(b.lockLyricBaseline, p)
    val lineX64: Int = pen64(Tok.edge, p)
    val lineX0: Int = px(Tok.edge, p)
}

class LockInk(
    val clock: Word,
    val title: Word,
    val artist: Word,
    val album: Word,
    val line: Word,
    val lineRight: Boolean,
    val lineAlpha: Float,
)

@Composable
fun rememberLockWord(text: String, role: Role, s: Scale, base: Int = Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT): Word {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    return remember(text, role, s.board, s.stdPpu, base, density, fonts, covers) {
        m.word(text, role, roleStyle(role, s, density, Ink.white, ppu = s.stdPpu), fonts, density, covers, s.board.fontHanMinPx, base)
    }
}

fun DrawScope.lockPaint(l: LockLayout, ink: LockInk, art: ImageBitmap?, phone: Boolean, rec: DrawRecord? = null) {
    val x0 = l.left.toFloat()
    val right = x0 + penPx(l.right64)
    if (ink.clock.text.isNotEmpty()) {
        val cx = right - ink.clock.width
        val a = Roles.saverClock.alpha
        word(ink.clock, cx, l.clockBase.toFloat(), Ink.white, a)
        rec?.word("lock.clock", ink.clock, cx, l.clockBase.toFloat(), a)
    }
    val side = l.artSide
    val ax = l.left + l.artX
    when {
        art != null -> drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset(ax, l.artY), IntSize(side, side))
        phone -> glyph(Glyphs.bluetooth, Offset(ax + side / HALF, l.artY + side / HALF), l.btGlyph.toFloat(), Ink.white, 1f)
        else -> drawRect(Ink.tile, Offset(ax.toFloat(), l.artY.toFloat()), Size(side.toFloat(), side.toFloat()))
    }
    val tx = x0 + penPx(l.textX64)
    clipRect(x0 + l.textX0, 0f, x0 + l.textX1, size.height) {
        word(ink.title, tx, l.titleBase.toFloat(), Ink.white, Roles.lockTitle.alpha)
        word(ink.artist, tx, l.artistBase.toFloat(), Ink.white, Roles.lockArtist.alpha)
        word(ink.album, tx, l.albumBase.toFloat(), Ink.white, Roles.lockAlbum.alpha)
    }
    rec?.word("lock.title", ink.title, tx, l.titleBase.toFloat(), Roles.lockTitle.alpha)
    rec?.word("lock.artist", ink.artist, tx, l.artistBase.toFloat(), Roles.lockArtist.alpha)
    rec?.word("lock.album", ink.album, tx, l.albumBase.toFloat(), Roles.lockAlbum.alpha)
    val w = ink.line
    if (w.text.isEmpty()) return
    val over = ceil(max(0f, w.inkRight - w.width))
    val x1 = min(x0 + l.s.widthPx, x0 + l.textX1 + over)
    val lx = if (ink.lineRight) right - w.width else x0 + penPx(l.lineX64)
    val a = Roles.npNext.alpha * ink.lineAlpha
    clipRect(x0 + l.lineX0, 0f, x1, size.height) {
        word(w, lx, l.lineBase.toFloat(), Ink.white, a)
    }
    rec?.word("lock.line", w, lx, l.lineBase.toFloat(), a)
}
