package net.seilent.funekit.screens.eq

import androidx.compose.ui.graphics.Color
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.screens.settings.RowsPaint

class EqPaint(
    d: EqDraw,
    chrome: Chrome,
    item: Int,
    bands: List<Band>,
    accent: Color,
) : RowsPaint<EqLine, EqLook>(d, chrome, item, bands, accent)
