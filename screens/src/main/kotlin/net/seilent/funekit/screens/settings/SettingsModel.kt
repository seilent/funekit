package net.seilent.funekit.screens.settings

import androidx.compose.ui.graphics.Color
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.screens.music.PageId
import kotlin.math.max
import kotlin.math.min

const val SETTINGS = "settings"

data class SettingsId(val header: String) : PageId

enum class HeaderLook { Settings, Page, Default }

enum class SettingKind { Choice, Steps, Range, Toggle, Action, Draft, Info }

enum class SettingFlag { ReadOnly, Restart, BoardOnly, Confirm }

data class SettingRow(
    val key: String,
    val title: String,
    val value: String,
    val kind: SettingKind = SettingKind.Info,
    val sub: String? = null,
    val values: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    val shows: List<String> = emptyList(),
    val at: Int = -1,
    val focus: Int = 0,
    val wrap: Boolean = true,
    val min: Int = 0,
    val max: Int = 0,
    val step: Int = 0,
    val level: Int = 0,
    val flags: Set<SettingFlag> = emptySet(),
    val live: String? = null,
    val swatch: Color? = null,
    val swatches: List<Color> = emptyList(),
    val pressLook: Boolean = true,
) {
    val editable: Boolean
        get() = SettingFlag.ReadOnly !in flags && when (kind) {
            SettingKind.Choice, SettingKind.Steps, SettingKind.Toggle, SettingKind.Range -> true
            SettingKind.Draft -> values.isNotEmpty()
            SettingKind.Action, SettingKind.Info -> false
        }
}

typealias SettingPivot = RowPivot<SettingRow>

open class SettingSheet(
    val form: MenuForm,
    val title: String,
    val items: List<String>,
    val values: List<String> = emptyList(),
    val mixed: List<Boolean> = emptyList(),
    val lead: Int = 0,
    val readOnly: Boolean = false,
    val mixedRow: Int = -1,
    val focus: Int = -1,
    val mark: Int = -1,
    val wrap: Boolean = false,
    val lift: Boolean = false,
    val confirm: Boolean = false,
    val edit: String? = null,
    val swatches: List<Color> = emptyList(),
)

fun valueSheet(row: SettingRow): SettingSheet {
    val steps = row.kind == SettingKind.Steps
    val n = row.words.size
    fun at(i: Int): Int = if (steps && i >= 0) n - 1 - i else i
    return SettingSheet(MenuForm.Sheet, row.title.lowercase(), if (steps) row.words.reversed() else row.words, focus = at(row.focus), mark = at(row.at), wrap = row.wrap, lift = true, edit = row.key, swatches = if (steps) row.swatches.reversed() else row.swatches)
}

fun settingRowAt(tops: IntArray, content: Int, y: Int): Int {
    var idx = -1
    for (i in tops.indices) if (tops[i] <= y) idx = i
    return if (y < content) idx else -1
}

fun clampFocusToView(focus: Int, tops: IntArray, heights: IntArray, off: Int, view: Int): Int {
    val n = tops.size
    if (n == 0 || focus !in 0 until n) return focus
    val top = off
    val bottom = off + view
    val content = tops[n - 1] + heights[n - 1]
    val rt = tops[focus]
    if (rt >= top && rt + heights[focus] <= bottom) return focus
    fun nearest(from: Int): Int = if (from in 0 until n) from else -1
    val t = if (rt < top) {
        var i = max(0, settingRowAt(tops, content, top))
        if (tops[i] < top) i++
        nearest(i)
    } else {
        var i = settingRowAt(tops, content, bottom)
        if (i < 0) i = n - 1
        if (tops[i] + heights[i] > bottom) i--
        nearest(i)
    }
    return if (t >= 0) t else focus
}

fun ensureVisible(top: Int, h: Int, off: Int, view: Int, limit: Int): Int {
    val want = when {
        top < off -> top
        top + h > off + view -> top + h - view
        else -> off
    }
    return want.coerceIn(0, max(0, limit))
}

data class SettingLook(val title: Float, val value: Float, val sub: Float, val swatch: Float, val glow: Float?, val edit: Boolean = false)

fun settingsLift(tops: IntArray, heights: IntArray, focus: Int, off: Int, contentTop: Int, panelTop: Int, toTop: Boolean): Int {
    if (focus !in tops.indices) return 0
    val top = tops[focus]
    var by = contentTop + top + heights[focus] - off - panelTop
    if (toTop) by = min(by, top - off)
    if (by <= 0 || top - off < by) return 0
    val to = tops.firstOrNull { it >= off + by } ?: return 0
    return to - off
}

enum class AimKind { None, Into, Lift, Drop }

class SettingsModel(entry: Long, page: SettingsId) : RowsPage<SettingRow>(entry, page) {
    val id: SettingsId = page

    override fun keyOf(r: SettingRow): String = r.key

    override fun pressLook(r: SettingRow): Boolean = r.pressLook
}
