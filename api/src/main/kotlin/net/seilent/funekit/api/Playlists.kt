package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow

interface Playlists {
    val revision: StateFlow<Long>

    suspend fun create(tracks: TrackSet): Long?
    suspend fun add(id: Long, tracks: TrackSet)
    suspend fun remove(id: Long, index: Int, revision: Long)
    suspend fun move(id: Long, from: Int, to: Int, revision: Long)
    suspend fun delete(id: Long)
}
