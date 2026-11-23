package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow

class LyricCopy(val text: String, val carriedMs: Int?)

interface Lyrics {
    val fetch: StateFlow<Boolean>
    val revision: StateFlow<Long>

    suspend fun lyrics(track: Long): String?
    suspend fun copy(track: Long): LyricCopy? = lyrics(track)?.let { LyricCopy(it, null) }
    suspend fun offsetMs(track: Long): Int
    suspend fun setOffset(track: Long, ms: Int)
    suspend fun setFetch(on: Boolean)
}
