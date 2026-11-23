package net.seilent.funekit.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import net.seilent.funekit.api.Caps
import net.seilent.funekit.api.Edge
import net.seilent.funekit.api.EngineSubject
import net.seilent.funekit.api.Subject
import net.seilent.funekit.api.Wait
import java.nio.ByteBuffer

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.fakeSubject(edge: Edge, refs: Boolean = false, lyricsCarried: Boolean = false): Subject {
    val clock = { testScheduler.currentTime * MILLI }
    val f = Fake(backgroundScope, clock, FakeSet.valueOf(edge.name), LATENCY, LEASE, PIN_CAP, trackRefs = refs, services = refs, lyricsCarried = lyricsCarried)
    val tracks = Fixture.ALBUMS.flatMap { it.tracks }
    return Subject(
        player = f.player,
        library = f.library,
        quickplay = f.quickplay,
        favorites = f.favorites,
        playlists = f.playlists,
        lyrics = f.lyrics,
        leaseMs = LEASE,
        elapsedNanos = clock,
        failNextScan = { f.failNextScan() },
        built = { f.library.rowsBuilt },
        loads = { f.library.pageLoads },
        imageSide = { pngSide(it) },
        wait = VIRTUAL,
        caps = Caps(artRefs = !refs, outcomes = true, pinCap = f.pinCap, firstIndex = true, trackRefs = refs),
        engine = EngineSubject(
            settings = f.settings,
            brokenTrack = tracks.first { it.broken }.id,
            resampledTrack = tracks.first { it.title == "Room 1" }.id,
            plainTrack = tracks.first { it.title == "Lantern One" }.id,
            loseOutput = { f.loseOutput() },
            routeTimeout = { f.routeTimeout() },
            xrun = { f.xrun() },
            attachDac = { f.attachDac(it) },
            detachDac = { f.detachDac() },
            attachDopDac = { f.attachDac(it, true) },
        ),
        eq = f.eq,
        scrobbling = f.scrobbling,
        busy = { f.busyNext(it) },
        card = { f.card(it) },
        bookTrack = Fixture.ALBUMS.first { it.title == Fixture.BOOK }.tracks.first().id,
        serviceEntry = if (refs) ({}) else null,
    )
}

private fun pngSide(bytes: ByteArray): Int {
    val b = ByteBuffer.wrap(bytes)
    return minOf(b.getInt(PNG_WIDTH), b.getInt(PNG_HEIGHT))
}

private const val PNG_WIDTH = 16
private const val PNG_HEIGHT = 20
private val VIRTUAL = object : Wait {
    override suspend fun <T> within(block: suspend () -> T): T = withTimeout(WAIT_MS) { block() }
}
private const val WAIT_MS = 60_000L
private const val LATENCY = 40L
private const val PIN_CAP = 8
private const val LEASE = 5_000L
private const val MILLI = 1_000_000L
