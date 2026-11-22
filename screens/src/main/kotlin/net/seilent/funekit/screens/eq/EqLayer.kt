package net.seilent.funekit.screens.eq

import androidx.compose.runtime.Composable
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.screens.settings.RowsLayer

@Composable
fun EqLayer(
    m: EqModel,
    draw: EqDraw,
    chrome: Chrome,
    a: Arbiter,
    bands: List<Band>,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    frozen: Boolean,
    snap: Int,
    onBack: () -> Unit,
    onTap: (EqLine) -> Unit,
    onHold: (EqLine) -> Boolean,
    leavesLit: () -> Boolean = { false },
) = RowsLayer(m, draw, chrome, a, bands, now, live, visible, frozen, snap, onBack, onTap, onHold, leavesLit)
