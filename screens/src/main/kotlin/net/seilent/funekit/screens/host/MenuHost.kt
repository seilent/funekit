package net.seilent.funekit.screens.host

import kotlin.math.floor
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.px

class MenuHost {
    val menu = Menu()
    var lift = false
    var act: (Int) -> Boolean = { false }
    var hold: ((Int) -> Boolean)? = null
    var scrim: () -> Boolean = { false }
}

fun frameMenu(menu: Menu, s: Scale, lift: Boolean, liftRow: () -> Int?) {
    val bottom = px(s.stdContentBottom, s.stdPpu)
    menu.frame(px(Tok.statusTop + Tok.statusHeight, s.ppu), bottom)
    val kept = s.large && menu.form == MenuForm.Sheet && lift
    val row = if (kept) liftRow() else null
    if (row != null) {
        menu.keep(true, true, floor(bottom - row - (s.board.page_contentTop + Tok.gutter) * s.ppu).toInt())
    } else {
        menu.keep(kept || s.large && menu.form == MenuForm.Dlg && menu.dialogEnd(s) > bottom, false, 0)
    }
}
