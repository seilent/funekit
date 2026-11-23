package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryState
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.core.Lrc
import net.seilent.funekit.fake.Fake
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.PhoneRepeat
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.IOException
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

    private var cover = png(Color.BLUE)
    private var artFails = false
    private var phone by mutableStateOf<PhoneSession?>(null)

    private fun painted(v: LibraryView): LibraryView = object : LibraryView by v {
        override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray = if (artFails) throw IOException("art read failed") else cover
    }

    private val artLibrary = object : Library by fake.library {
        override fun view(): LibraryView = painted(fake.library.view())

        override fun view(generation: Long): LibraryView = painted(fake.library.view(generation))
    }

    private val artSrc = MusicSource(artLibrary, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)

    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(PNG_SIDE, PNG_SIDE, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun artFor(np: NowPlayingModel, track: Long) {
        rule.waitUntil(WAIT_MS) { (np.artKey as? List<*>)?.firstOrNull() == track }
    }

    private fun artPlayed(np: NowPlayingModel, n: Int = 2): List<Long> {
        val ids = runBlocking { fake.library.view().page(Listing.Songs, 0, ART_SONGS).rows.filter { it.art != null }.map { it.id }.take(n) }
        runBlocking { fake.player.play(ids, 0, 0) }
        rule.setContent { NowPlayingFeed(np, NpLayout(s), artSrc, phone) { 0L } }
        artFor(np, ids[0])
        return ids
    }

    @Test
    fun aTrackWithTheSameArtPictureKeepsTheArt() {
        val np = NowPlayingModel()
        val (_, b) = artPlayed(np)
        val first = checkNotNull(np.art)
        player { next() }
        artFor(np, b)
        assertSame(first, np.art)
    }

    @Test
    fun aTrackWithAnotherArtPictureReadsItsArt() {
        val np = NowPlayingModel()
        val (_, b) = artPlayed(np)
        val first = checkNotNull(np.art)
        cover = png(Color.RED)
        player { next() }
        artFor(np, b)
        assertNotSame(first, checkNotNull(np.art))
    }

    @Test
    fun theSamePictureAfterAFailedReadReadsItsArtAgain() {
        val np = NowPlayingModel()
        val (_, _, c) = artPlayed(np, ART_TRACKS)
        artFails = true
        player { next() }
        rule.waitUntil(WAIT_MS) { np.art == null }
        artFails = false
        player { next() }
        artFor(np, c)
        assertNotNull("the same picture after a failed read shows its art", np.art)
    }

    @Test
    fun anOwnTrackResumedOverAPhoneSessionReadsItsArtAgain() {
        val np = NowPlayingModel()
        artPlayed(np)
        player { pause() }
        val phoneArt = Bitmap.createBitmap(PNG_SIDE, PNG_SIDE, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { phone = PhoneSession(PhoneTrack("Radio", "Song", "Artist", "Album", true, false, PHONE_MS, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, phoneArt), {}) {} }
        rule.waitForIdle()
        rule.waitUntil(WAIT_MS) { np.artPhone }
        assertSame("premise: the phone's art shows", phoneArt, np.art?.asAndroidBitmap())
        player { resume() }
        rule.waitUntil(WAIT_MS) { !np.artPhone && np.art != null }
        assertNotSame("the own track's art replaces the phone's", phoneArt, np.art?.asAndroidBitmap())
    }

    private fun settle() {
        scheduler.runCurrent()
        rule.waitForIdle()
    }

    private fun carryingFeed(np: NowPlayingModel, offset: Int): Pair<Fake, Long> {
        val f = testFake(FakeSet.Demo, scheduler, lyricsCarried = true)
        val t = checkNotNull(f.lyricTrack(SHAPE))
        np.lyricsView = true
        np.workContext = Dispatchers.Unconfined
        runBlocking {
            f.lyrics.setOffset(t, offset)
            f.player.play(listOf(t), 0, 0)
        }
        val source = MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)
        rule.setContent { NowPlayingFeed(np, NpLayout(s), source, null) { 0L } }
        settle()
        assertEquals("premise: the lyric track shows", t, np.shown.track)
        return f to t
    }

    private fun stamps(t: Long): List<Long> =
        Lrc.parse(checkNotNull(runBlocking { fake.lyrics.lyrics(t) }).toByteArray(Charsets.UTF_8)).lines.map { it.ms }

    @Test
    fun aCarryingSourceShowsItsLinesOnceWithTheServedOffset() {
        val np = NowPlayingModel()
        val (f, t) = carryingFeed(np, OFFSET)
        val fixture = stamps(t)
        assertEquals(fixture.map { maxOf(0L, it - OFFSET) }, np.lyrics.lines.map { it.ms })
        assertEquals(OFFSET, np.lyrics.offsetMs)
        runBlocking { f.lyrics.setOffset(t, 0) }
        settle()
        assertEquals(fixture, np.lyrics.lines.map { it.ms })
        assertEquals(0, np.lyrics.offsetMs)
    }

    @Test
    fun aReReadOfTheShownTrackKeepsItsLinesUntilTheNewCopyLands() {
        val np = NowPlayingModel()
        val (f, t) = carryingFeed(np, OFFSET)
        val fixture = stamps(t)
        assertEquals("premise: the first copy shows", fixture.map { maxOf(0L, it - OFFSET) }, np.lyrics.lines.map { it.ms })
        val held = StandardTestDispatcher()
        rule.runOnIdle { np.workContext = held }
        runBlocking { f.lyrics.setOffset(t, MOVED) }
        settle()
        assertFalse("a re-read keeps its copy while it parses", np.lyrics.pending)
        assertEquals(fixture.map { maxOf(0L, it - OFFSET) }, np.lyrics.lines.map { it.ms })
        held.scheduler.advanceUntilIdle()
        rule.waitForIdle()
        assertEquals(fixture.map { maxOf(0L, it - MOVED) }, np.lyrics.lines.map { it.ms })
        assertEquals(MOVED, np.lyrics.offsetMs)
    }

    private companion object {
        const val SDK = 35
        const val V1_W = 240
        const val V1_H = 320
        const val SONGS = 3
        const val SEEK_MS = 30_000L
        const val SHAPE = "short"
        const val OFFSET = 500
        const val MOVED = 200
        const val PNG_SIDE = 64
        const val ART_SONGS = 20
        const val ART_TRACKS = 3
        const val WAIT_MS = 5_000L
        const val PHONE_MS = 180_000L
    }
}
