package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow

interface Lyrics {
    val fetch: StateFlow<Boolean>
    val revision: StateFlow<Long>

    suspend fun lyrics(track: Long): String?
    suspend fun offsetMs(track: Long): Int
    suspend fun setOffset(track: Long, ms: Int)
    suspend fun setFetch(on: Boolean)
}
