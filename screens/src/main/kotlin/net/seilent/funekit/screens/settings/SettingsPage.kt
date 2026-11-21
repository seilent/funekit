package net.seilent.funekit.screens.settings

import androidx.compose.runtime.State
import net.seilent.funekit.screens.music.nowplaying.AdjustCol

class SettingAdjust(val cols: List<AdjustCol>, val line1: String, val line2: String)

interface SettingsRows {
    val pivots: State<List<SettingPivot>>

    fun open(row: SettingRow): Boolean = false

    suspend fun set(key: String, values: List<String>): Boolean

    fun action(key: String) {}

    fun draft(key: String, value: String) {}

    fun adjust(row: SettingRow, level: Int): SettingAdjust? = null

    fun hasEntry(row: SettingRow): Boolean = false

    fun entry(row: SettingRow, side: Int) {}
}

class SettingsPage(val id: SettingsId, val rows: SettingsRows, val look: HeaderLook)

class SettingsScreen(val entry: Long, val id: SettingsId, val pivot: String? = null)
