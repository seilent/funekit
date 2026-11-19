package net.seilent.funekit.api

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class Repeat { Off, All, One }

interface Player {
    val state: StateFlow<PlayerState>
    val events: SharedFlow<EngineEvent>

    suspend fun play(ids: List<Long>, index: Int, posMs: Long)
    suspend fun play(listing: Listing, generation: Long, index: Int, posMs: Long, shuffle: Boolean = false)
    suspend fun toggle()
    suspend fun pause()
    suspend fun resume()
    suspend fun next()
    suspend fun prev()
    suspend fun seek(ms: Long)
    suspend fun stop()

    suspend fun enqueue(ids: List<Long>)
    suspend fun enqueue(listing: Listing, generation: Long)
    suspend fun playNext(ids: List<Long>)
    suspend fun playNext(listing: Listing, generation: Long)
    suspend fun move(from: Int, to: Int, revision: Long)
    suspend fun remove(index: Int, revision: Long)
    suspend fun playAt(index: Int, revision: Long)
    suspend fun playNextAt(slot: Int, revision: Long)
    suspend fun clear()
    suspend fun shuffle(on: Boolean)
    suspend fun repeat(mode: Repeat)
}
