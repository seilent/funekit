package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow

data class EqResult(val ok: Boolean, val profile: String?, val log: List<String>)

interface Eq {
    val profile: StateFlow<String?>

    suspend fun setEq(profile: String?): EqResult
}
