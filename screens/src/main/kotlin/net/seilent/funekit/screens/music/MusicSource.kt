package net.seilent.funekit.screens.music

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.seilent.funekit.api.Eq
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.Favorites
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.Playlists
import net.seilent.funekit.api.Quickplay
import net.seilent.funekit.api.Scrobbling
import net.seilent.funekit.api.Settings

class MusicSource(
    val library: Library,
    val player: Player,
    val quickplay: Quickplay,
    val favorites: Favorites,
    val playlists: Playlists,
    val lyrics: Lyrics,
    val settings: Settings? = null,
    val eq: Eq? = null,
    val scrobbling: Scrobbling? = null,
) {
    suspend fun <T> viewed(block: suspend (LibraryView) -> T): T {
        while (true) {
            try {
                return block(library.view())
            } catch (_: Expired) {
                continue
            }
        }
    }

    fun revisions(listing: Listing): Flow<Long>? = when (listing) {
        Listing.Queue -> player.state.map { it.queueRevision }.distinctUntilChanged()
        Listing.Pins, Listing.History -> quickplay.revision
        Listing.Favorites -> favorites.revision
        Listing.Playlists, is Listing.PlaylistTracks -> playlists.revision
        else -> null
    }
}
