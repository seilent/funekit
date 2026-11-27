package net.seilent.funekit.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayModes
import net.seilent.funekit.api.PlayPrefs
import net.seilent.funekit.api.PlayThrough

class FakeModes internal constructor() : PlayModes {
    private val _prefs = MutableStateFlow(PlayPrefs())

    override val prefs: StateFlow<PlayPrefs> = _prefs.asStateFlow()

    override suspend fun set(prefs: PlayPrefs) {
        require(prefs.list == null || prefs.list is Listing.PlaylistTracks || prefs.list == Listing.Favorites) { "play through list ${prefs.list}" }
        require(prefs.through != PlayThrough.Playlist || prefs.list != null) { "play through playlist with no list" }
        _prefs.value = prefs
    }
}
