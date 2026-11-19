package net.seilent.funekit.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import net.seilent.funekit.api.ApiContract
import net.seilent.funekit.api.Edge
import net.seilent.funekit.api.EngineSubject
import net.seilent.funekit.api.Subject
import net.seilent.funekit.api.Wait
import java.nio.ByteBuffer

@OptIn(ExperimentalCoroutinesApi::class)
class FakeContractTest : ApiContract() {
    override fun TestScope.open(edge: Edge): Subject {
        val clock = { testScheduler.currentTime * MILLI }
        val f = Fake(backgroundScope, clock, FakeSet.valueOf(edge.name), LATENCY, LEASE)
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
        )
    }

    private companion object {
        fun pngSide(bytes: ByteArray): Int {
            val b = ByteBuffer.wrap(bytes)
            return minOf(b.getInt(PNG_WIDTH), b.getInt(PNG_HEIGHT))
        }

        const val PNG_WIDTH = 16
        const val PNG_HEIGHT = 20
        val VIRTUAL = object : Wait {
            override suspend fun <T> within(block: suspend () -> T): T = withTimeout(WAIT_MS) { block() }
        }
        const val WAIT_MS = 60_000L
        const val LATENCY = 40L
        const val LEASE = 5_000L
        const val MILLI = 1_000_000L
    }
}
