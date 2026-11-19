package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow

interface Favorites {
    val revision: StateFlow<Long>

    suspend fun has(track: Long): Boolean
    suspend fun toggle(track: Long): Boolean
    suspend fun add(tracks: TrackSet): Int
    suspend fun remove(index: Int, revision: Long)
    suspend fun move(from: Int, to: Int, revision: Long)
}
