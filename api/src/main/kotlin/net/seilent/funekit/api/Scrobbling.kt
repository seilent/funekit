package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow

enum class ScrobbleService { LastFm, ListenBrainz }

data class ScrobbleView(
    val lastFm: Boolean = false,
    val lastFmUser: String = "",
    val listenBrainz: Boolean = false,
    val listenBrainzUser: String = "",
    val queued: Int = 0,
    val lastFmNote: String = "",
    val listenBrainzNote: String = "",
    val signingIn: Boolean = false,
    val appKey: Boolean = false,
)

interface Scrobbling {
    val view: StateFlow<ScrobbleView>

    suspend fun enable(service: ScrobbleService, on: Boolean)
    suspend fun lastFmStart(): String?
    suspend fun lastFmFinish()
    suspend fun listenBrainzToken(token: String): Boolean
    suspend fun signOut(service: ScrobbleService)
}
