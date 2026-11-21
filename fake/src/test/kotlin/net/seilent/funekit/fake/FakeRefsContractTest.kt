package net.seilent.funekit.fake

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import net.seilent.funekit.api.Caps
import net.seilent.funekit.api.Edge
import net.seilent.funekit.api.SeamContract
import net.seilent.funekit.api.Subject
import net.seilent.funekit.api.Wait
import java.nio.ByteBuffer

@OptIn(ExperimentalCoroutinesApi::class)
class FakeRefsContractTest : SeamContract() {
    override fun TestScope.open(edge: Edge): Subject {
        val clock = { testScheduler.currentTime * MILLI }
        val f = Fake(backgroundScope, clock, FakeSet.valueOf(edge.name), LATENCY, LEASE, PIN_CAP, trackRefs = true)
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
            caps = Caps(artRefs = false, outcomes = true, pinCap = f.pinCap, firstIndex = true, trackRefs = true),
            busy = { f.busyNext(it) },
            card = { f.card(it) },
            bookTrack = Fixture.ALBUMS.first { it.title == Fixture.BOOK }.tracks.first().id,
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
        const val PIN_CAP = 8
        const val LEASE = 5_000L
        const val MILLI = 1_000_000L
    }
}
