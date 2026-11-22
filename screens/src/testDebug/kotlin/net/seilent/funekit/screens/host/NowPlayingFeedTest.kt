package net.seilent.funekit.screens.host

import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryState
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NowPlayingFeedTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(V1_W, V1_H, SDK, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
    private val scheduler = TestCoroutineScheduler()
    private val fake = testFake(FakeSet.Demo, scheduler)
    private val shelf = MutableStateFlow(LibraryState(first = true))
    private val reads = AtomicInteger()

    private fun counted(v: LibraryView): LibraryView = object : LibraryView by v {
        override suspend fun page(listing: Listing, offset: Int, count: Int): Page {
            if (listing == Listing.Queue) {
                reads.incrementAndGet()
                if (shelf.value.first) throw Busy()
            }
            return v.page(listing, offset, count)
        }
    }

    private val library = object : Library by fake.library {
        override val state: StateFlow<LibraryState> = shelf

        override fun view(): LibraryView = counted(fake.library.view())

        override fun view(generation: Long): LibraryView = counted(fake.library.view(generation))
    }

    private val src = MusicSource(library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)

    private fun songs(): List<Long> = runBlocking { fake.library.view().page(Listing.Songs, 0, SONGS).rows.map { it.id } }

    private fun player(act: suspend Player.() -> Unit) {
        runBlocking { fake.player.act() }
        rule.waitForIdle()
    }

    @Test
    fun aQueueReadKeysOnTheRowsAndTheLibraryNotThePosition() {
        val ids = songs()
        val np = NowPlayingModel()
        val g = NpLayout(s)
        assertEquals("premise: one next row on V1", 1, g.nextTracks)
        runBlocking { fake.player.play(ids, 0, 0) }
        rule.setContent { NowPlayingFeed(np, g, src, null) { 0L } }
        rule.waitForIdle()
        val n = reads.get()
        assertTrue("premise: the first index reads the queue", n >= 1)
        assertNull("a busy read shows nothing", np.shown.track)

        player { toggle() }
        assertEquals("a play toggle reads nothing", n, reads.get())
        assertNull(np.shown.track)

        rule.runOnIdle { shelf.value = LibraryState() }
        rule.waitForIdle()
        assertEquals("the first index's end reads again", n + 1, reads.get())
        assertEquals(ids[0], np.shown.track)

        val fed = fake.player.state.value
        player {
            seek(SEEK_MS)
            toggle()
        }
        assertNotEquals("premise: the fed state moved", fed, fake.player.state.value)
        assertEquals("a seek and a play toggle read nothing", n + 1, reads.get())

        player { repeat(Repeat.All) }
        assertEquals("a repeat change reads again", n + 2, reads.get())

        val moved = fake.player.state.value
        player { move(1, 2, moved.queueRevision) }
        val after = fake.player.state.value
        assertNotEquals("premise: the move is a new revision", moved.queueRevision, after.queueRevision)
        assertEquals("premise: the same index", moved.index, after.index)
        assertEquals("premise: the same size", moved.queueSize, after.queueSize)
        assertEquals("a queue move reads again", n + 3, reads.get())

        player { pause() }
        assertEquals("a pause reads nothing", n + 3, reads.get())
        val gen = fake.library.generation.value
        runBlocking { fake.library.rescan() }
        scheduler.advanceUntilIdle()
        rule.waitForIdle()
        assertEquals("premise: the rescan keeps the queue revision", after.queueRevision, fake.player.state.value.queueRevision)
        assertEquals("premise: the rescan moves the generation one step", gen + 1, fake.library.generation.value)
        assertEquals("a rescan reads again", n + 4, reads.get())

        player { next() }
        assertEquals("a track change reads again", n + 5, reads.get())
        assertEquals(ids[2], np.shown.track)
    }

    private companion object {
        const val SDK = 35
        const val V1_W = 240
        const val V1_H = 320
        const val SONGS = 3
        const val SEEK_MS = 30_000L
    }
}
