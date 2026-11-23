package net.seilent.funekit.screens.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import net.seilent.funekit.Scale
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.rememberChrome
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.screens.music.pivotSwitch

fun scrollOf(state: LazyListState, tops: IntArray): Int {
    val i = state.firstVisibleItemIndex
    return (if (i in tops.indices) tops[i] else 0) + state.firstVisibleItemScrollOffset
}

fun settingsCanSwitch(m: SettingsModel, d: Int): Boolean = m.canSwitch(d)

fun settingsSwitch(m: RowsPage<*>, chrome: Chrome, to: Int, now: Long) = pivotSwitch(m, chrome, to, now)

@Composable
fun rememberSettingsChrome(s: Scale, m: SettingsModel, look: HeaderLook): Chrome {
    val c = rememberChrome(s, m.id.header, pivotNames(m.groups))
    return remember(c, look) { settingsChrome(c, look) }
}

@Composable
fun rememberSettingsDraw(m: SettingsModel, s: Scale, accent: Color, accentOff: Boolean): SettingsDraw {
    val measurer = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    return remember(m, s, density, fonts, covers, accent, accentOff) { settingsDraw(m, s, measurer, fonts, density, covers, accent, accentOff) }
}

@Composable
fun SettingsLayer(
    m: SettingsModel,
    draw: SettingsDraw,
    chrome: Chrome,
    a: Arbiter,
    bands: List<Band>,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    frozen: Boolean,
    snap: Int,
    onBack: () -> Unit,
    onTap: (SettingRow) -> Unit,
    leavesLit: () -> Boolean = { false },
) = RowsLayer(m, draw, chrome, a, bands, now, live, visible, frozen, snap, onBack, onTap, null, leavesLit)
