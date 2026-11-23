package net.seilent.funekit.screens.host

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickplaySourceTest {
    private val f = testFake(FakeSet.Demo)
    private val src = ApiQuickplay(f.library, f.player, f.quickplay)

    private fun playFirst() = runBlocking { f.player.play(Listing.Songs, f.library.view().generation, 0, 0) }

    @Test
    fun aPausedPlayingTileReadsPausedAndATapResumesIt() {
        playFirst()
        runBlocking { f.player.pause() }
        val d = runBlocking { src.load(TILES) }
        assertNotNull("the playing tile is there", d.playing)
        assertTrue("a paused track reads paused", d.paused)
        runBlocking { src.resume() }
        assertTrue("the tap resumes the queue", f.player.state.value.playing)
        assertFalse("a playing track reads playing", runBlocking { src.load(TILES) }.paused)
    }

    @Test
    fun aResumeWithNothingQueuedOrAlreadyPlayingChangesNothing() {
        runBlocking { src.resume() }
        assertNull(f.player.state.value.track)
        assertFalse(f.player.state.value.playing)
        assertFalse("no playing tile reads paused", runBlocking { src.load(TILES) }.paused)
        playFirst()
        val track = f.player.state.value.track
        runBlocking { src.resume() }
        assertTrue(f.player.state.value.playing)
        assertTrue(track == f.player.state.value.track)
    }

    @Test
    fun aPauseAloneChangesTheQuickplayData() {
        playFirst()
        val v0 = runBlocking { src.changes.first() }
        runBlocking { f.player.pause() }
        val v1 = runBlocking { withTimeout(WAIT_MS) { src.changes.first { it != v0 } } }
        assertTrue(v1 != v0)
    }

    @Test
    fun everyKindButServiceHasItsQuickplayKind() {
        for (k in Kind.entries - Kind.Service) assertEquals(k, k.qp().api())
        assertTrue(runCatching { Kind.Service.qp() }.exceptionOrNull() is IllegalStateException)
    }

    companion object {
        private const val TILES = 8
        private const val WAIT_MS = 1_000L
    }
}
