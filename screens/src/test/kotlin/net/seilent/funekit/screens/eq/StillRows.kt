package net.seilent.funekit.screens.eq

import androidx.compose.runtime.mutableStateOf

internal class StillRows(start: List<EqPivot>) : EqRows {
    override val pivots = mutableStateOf(start)

    override fun choices(slot: String, kind: EqKind): List<EqChoice> = emptyList()

    override fun current(slot: String, kind: EqKind): String? = null

    override fun entry(e: EqEntry): Boolean = false

    override suspend fun on(slot: String, on: Boolean): EqOutcome = EqOutcome.Refused

    override suspend fun pick(slot: String, kind: EqKind, key: String): EqOutcome = EqOutcome.Refused

    override suspend fun preamp(slot: String, preamp10: Int): EqOutcome = EqOutcome.Refused

    override suspend fun band(slot: String, band: Int, field: EqField, value: Int): EqOutcome = EqOutcome.Refused

    override suspend fun add(slot: String, band: EqBand): EqOutcome = EqOutcome.Refused

    override suspend fun remove(slot: String): EqOutcome = EqOutcome.Refused
}
