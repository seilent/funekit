package net.seilent.funekit.screens.music.hub

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.word
import net.seilent.funekit.px

const val PATH_JOIN = " / "
const val PATH_DOTS = "..."

fun fitPath(path: String, max64: Int, dots: String, width64: (String) -> Int): String {
    if (width64(path) <= max64) return path
    var at = path.indexOf(PATH_JOIN)
    while (at >= 0) {
        val s = dots + path.substring(at)
        if (width64(s) <= max64) return s
        at = path.indexOf(PATH_JOIN, at + PATH_JOIN.length)
    }
    val last = path.lastIndexOf(PATH_JOIN)
    return if (last < 0) path else path.substring(last + PATH_JOIN.length)
}

fun labelMax64(s: Scale): Int = pen64(s.w - Tok.hubEdge, s.ppu) - pen64(Tok.hubEdge, s.ppu)

fun labelPath(names: List<String>): String = names.joinToString(PATH_JOIN)

fun DrawScope.folderLabel(s: Scale, w: Word, rec: DrawRecord?, name: String) {
    val pitch = px(Tok.rowHeight, s.ppu)
    val x = penPx(pen64(Tok.hubEdge, s.ppu))
    val base = (pitch - px(Tok.settingsLabelBaselineInset, s.ppu)).toFloat()
    val right = px(s.w - Tok.hubEdge, s.ppu).toFloat()
    clipRect(0f, 0f, right, pitch.toFloat()) { word(w, x, base, Ink.white, Roles.sectionLabel.alpha) }
    rec?.clip(0f, 0f, right, pitch.toFloat()) { rec.word(name, w, x, base, Roles.sectionLabel.alpha) }
}
