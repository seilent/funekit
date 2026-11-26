package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow
import net.seilent.funekit.api.engine.EqAnswer
import net.seilent.funekit.api.engine.EqFilter
import net.seilent.funekit.api.engine.EqPart
import net.seilent.funekit.api.engine.EqView

data class EqResult(val answer: EqAnswer, val name: String? = null, val log: List<String> = emptyList())

interface Eq {
    val view: StateFlow<EqView>

    suspend fun on(on: Boolean): EqResult
    suspend fun pick(key: String): EqResult
    suspend fun preamp(preamp10: Int): EqResult
    suspend fun filter(index: Int, part: EqPart, value: Int): EqResult
    suspend fun add(filter: EqFilter): EqResult
    suspend fun remove(): EqResult
    suspend fun save(): EqResult
    suspend fun resetCustom(): EqResult
    suspend fun delete(key: String): EqResult
    suspend fun import(name: String, text: String): EqResult
}
