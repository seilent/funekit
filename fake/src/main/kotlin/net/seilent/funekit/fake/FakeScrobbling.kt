package net.seilent.funekit.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.seilent.funekit.api.ScrobbleService
import net.seilent.funekit.api.ScrobbleView
import net.seilent.funekit.api.Scrobbling

class FakeScrobbling internal constructor() : Scrobbling {
    private val _view = MutableStateFlow(ScrobbleView(appKey = true))

    override val view: StateFlow<ScrobbleView> = _view.asStateFlow()

    override suspend fun enable(service: ScrobbleService, on: Boolean) = _view.update {
        when (service) {
            ScrobbleService.LastFm -> it.copy(lastFm = on)
            ScrobbleService.ListenBrainz -> it.copy(listenBrainz = on)
        }
    }

    override suspend fun lastFmStart(): String? {
        _view.update { it.copy(signingIn = true) }
        return AUTH
    }

    override suspend fun lastFmFinish() = _view.update { if (it.signingIn) it.copy(signingIn = false, lastFmUser = USER) else it }

    override suspend fun listenBrainzToken(token: String): Boolean {
        if (token.isBlank()) return false
        _view.update { it.copy(listenBrainzUser = USER) }
        return true
    }

    override suspend fun signOut(service: ScrobbleService) = _view.update {
        when (service) {
            ScrobbleService.LastFm -> it.copy(lastFmUser = "", signingIn = false)
            ScrobbleService.ListenBrainz -> it.copy(listenBrainzUser = "")
        }
    }

    private companion object {
        const val AUTH = "https://www.last.fm/api/auth/?api_key=fake&token=fake"
        const val USER = "fake"
    }
}
