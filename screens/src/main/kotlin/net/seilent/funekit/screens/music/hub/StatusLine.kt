package net.seilent.funekit.screens.music.hub

import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.api.Card
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.pivots

const val NO_CARD = "no card"
const val CARD_ERROR = "card error"

fun cardLine(card: Card): String = when (card) {
    Card.Out -> NO_CARD
    Card.Error -> CARD_ERROR
    Card.Present, Card.Held -> ""
}

fun scanShown(scanning: Boolean, songs: Int): Boolean = scanning && songs == 0

fun scanSongs(generation: Long, songs: Int): Int = if (generation == 0L) 0 else songs

fun hubLine(pivot: Int, scanning: Boolean, found: Int, songs: Int, rows: Int, generation: Long): String {
    if (scanShown(scanning, scanSongs(generation, songs))) return "scanning " + songsLabel(found)
    if (songs < 0 || pivot == HUB_PLAYLISTS) return ""
    if (rows > 0 || (pivot != HUB_GENRES && songs > 0)) return ""
    return "no " + MusicPage.Hub.pivots[pivot]
}

fun lineBase(s: Scale): Int = px64(fx(s.board.page_contentTop) + fx(Tok.qpHintDrop), fx(s.ppu))

fun DrawScope.statusLine(s: Scale, w: Word, rec: DrawRecord?, name: String, base: Float = lineBase(s).toFloat()) {
    val top = px(s.board.page_contentTop, s.ppu).toFloat()
    val bottom = px(s.stdContentBottom, s.stdPpu).toFloat()
    val x = penPx(pen64(Tok.hubEdge, s.ppu))
    clipped(rec, 0f, top, size.width, bottom) {
        word(w, x, base, Ink.white, Roles.quickplayHint.alpha)
        rec?.word(name, w, x, base, Roles.quickplayHint.alpha)
    }
}
