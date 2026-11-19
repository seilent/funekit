package net.seilent.funekit.fake

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.EngineEvent
import net.seilent.funekit.api.engine.Dac

class Fake(scope: CoroutineScope, clock: () -> Long, set: FakeSet, latencyMs: Long, leaseMs: Long, val pinCap: Int? = null) {
    private val bus = MutableSharedFlow<EngineEvent>(extraBufferCapacity = EVENTS, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val store = Store(set)
    private val shelf = Fixture.shelf(set)

    val library = FakeLibrary(scope, clock, set, latencyMs, leaseMs, store, bus)
    val player = FakePlayer(scope, clock, library, store, bus)
    val quickplay = FakeQuickplay(store, library, pinCap)
    val favorites = FakeFavorites(store, library, latencyMs)
    val playlists = FakePlaylists(store, library)
    val settings = FakeSettings(player)
    val eq = FakeEq()
    val lyrics = FakeLyrics(set, latencyMs)
    val scrobbling = FakeScrobbling()

    init {
        library.dropped = player::drop
        playlists.deleted = player::unorigin
    }

    suspend fun loseOutput() = player.loseOutput()

    suspend fun routeTimeout() = player.routeTimeout()

    suspend fun xrun() = player.xrun()

    suspend fun attachDac(dac: Dac, dop: Boolean = false) = settings.attachDac(dac, dop)

    suspend fun detachDac() = settings.detachDac()

    fun failNextScan() = library.failNextScan()

    fun busyNext(text: String) = library.busyNext(text)

    fun card(card: Card) = library.card(card)

    fun lyricTrack(shape: String): Long? = shelf.lyricShapes[shape]

    private companion object {
        const val EVENTS = 64
    }
}
