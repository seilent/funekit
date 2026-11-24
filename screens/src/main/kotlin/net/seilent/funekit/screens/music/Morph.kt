package net.seilent.funekit.screens.music

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.RowWord
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.word

fun morphSide(
    m: TextMeasurer,
    fonts: FontFamily.Resolver,
    s: Scale,
    density: Density,
    covers: Covers?,
    text: String,
    role: Role,
    header: Boolean,
    look: Float,
    x: Float,
    y: Float,
    pivot: Int,
): WordSide {
    val hpx = rolePx(Roles.pageHeader, s, s.stdPpu)
    val spx = rolePx(role, s, if (header) s.stdPpu else s.ppu)
    val at = Role(Roles.pageHeader.size, role.weight, role.case, role.alpha, role.tracking * hpx / spx)
    val w = m.word(text, at, roleStyle(at, s, density, Ink.white, ppu = s.stdPpu), fonts, density, covers, s.board.fontHanMinPx)
    return WordSide(text, spx.toFloat() / hpx, look, x, y, pivot, header, w)
}

fun rowWordSide(m: TextMeasurer, fonts: FontFamily.Resolver, s: Scale, density: Density, covers: Covers?): (RowWord, RowModel) -> WordSide = { w, r ->
    morphSide(m, fonts, s, density, covers, w.text, Rows.primaryRole(r), false, w.look, penPx(w.x64), w.base.toFloat(), -1)
}
