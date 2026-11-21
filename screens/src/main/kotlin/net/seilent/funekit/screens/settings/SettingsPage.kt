package net.seilent.funekit.screens.settings

import androidx.compose.runtime.State

interface SettingsRows {
    val pivots: State<List<SettingPivot>>

    fun open(row: SettingRow): Boolean = false

    suspend fun set(key: String, values: List<String>): Boolean

    fun action(key: String) {}

    fun draft(key: String, value: String) {}
}

class SettingsPage(val id: SettingsId, val rows: SettingsRows, val look: HeaderLook)

class SettingsScreen(val entry: Long, val id: SettingsId)
