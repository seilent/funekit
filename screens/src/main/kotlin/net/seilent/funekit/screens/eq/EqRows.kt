package net.seilent.funekit.screens.eq

import androidx.compose.runtime.State

enum class EqKind { On, Mode, Profile, Preset, SavePreset, DeletePreset, RenamePreset, ExportPreset, Import, Preamp, AutoPreamp, Total, Peak, Band, Gain, AddBand, RemoveBand, ResetCustom }

enum class EqField { Type, Freq, Gain, Q }

enum class EqBandType { Off, Peak, LowShelf, HighShelf }

data class EqBand(val type: EqBandType, val milliHz: Int, val grid: Int, val gain10: Int, val q100: Int)

data class EqGain(val grid: Int, val gain10: Int)

data class EqRow(val kind: EqKind, val value: String = "", val band: Int = -1, val title: String? = null)

data class EqChoice(val key: String, val label: String)

data class EqPivot(
    val slot: String,
    val name: String,
    val rows: List<EqRow>,
    val preamp10: Int = 0,
    val bands: List<EqBand> = emptyList(),
    val autoPreamp10: Int = 0,
    val gains: List<EqGain> = emptyList(),
    val types: List<EqBandType> = EqBandType.entries,
    val headset: String? = null,
    val peak: String? = null,
    val target: Long? = null,
)

enum class EqOutcome { Done, Refused, Stale, NoSpace, NameTaken, NameNotAllowed, Failed }

class EqEntry(
    val title: String,
    val start: String,
    val text: Boolean,
    val extras: String,
    val unit: String,
    val decimals: Int,
    val min: Int,
    val max: Int,
    val refusal: (String) -> String?,
    val done: (String) -> Unit,
    val reopen: Boolean = false,
    val adjust: Boolean = false,
)

interface EqRows {
    val pivots: State<List<EqPivot>>

    fun choices(slot: String, kind: EqKind): List<EqChoice>

    fun current(slot: String, kind: EqKind): String?

    fun entry(e: EqEntry): Boolean

    suspend fun on(slot: String, on: Boolean): EqOutcome

    suspend fun mode(slot: String, graphic: Boolean): EqOutcome = EqOutcome.Refused

    suspend fun pick(slot: String, kind: EqKind, key: String): EqOutcome

    suspend fun save(slot: String): EqOutcome = EqOutcome.Refused

    suspend fun resetCustom(slot: String): EqOutcome = EqOutcome.Refused

    suspend fun delete(slot: String, key: String): EqOutcome = EqOutcome.Refused

    fun renameRefusal(slot: String, key: String, name: String): String? = null

    suspend fun rename(slot: String, key: String, name: String): EqOutcome = EqOutcome.Refused

    fun import(slot: String) {}

    fun export(slot: String) {}

    suspend fun preamp(slot: String, preamp10: Int): EqOutcome

    suspend fun band(slot: String, band: Int, field: EqField, value: Int): EqOutcome

    suspend fun gain(slot: String, band: Int, gain10: Int): EqOutcome = EqOutcome.Refused

    suspend fun add(slot: String, band: EqBand): EqOutcome

    suspend fun remove(slot: String): EqOutcome

    fun hasEntry(slot: String): Boolean = true
}
