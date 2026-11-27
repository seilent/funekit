package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.NpFeed
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NpFeedsTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(V1_W, V1_H, SDK, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
    private val scheduler = TestCoroutineScheduler()
    private val fake = testFake(FakeSet.Demo, scheduler)
    private val phone = MutableStateFlow<PhoneSession?>(null)
    private val photo = MutableStateFlow<NpPicture?>(null)
    private var cover = png(Color.BLUE)

    private fun painted(v: LibraryView): LibraryView = object : LibraryView by v {
        override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray = cover
    }

    private val library = object : Library by fake.library {
        override fun view(): LibraryView = painted(fake.library.view())

        override fun view(generation: Long): LibraryView = painted(fake.library.view(generation))
    }

    private val src = MusicSource(library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)

    private fun png(color: Int): ByteArray {
        val b = Bitmap.createBitmap(PNG_SIDE, PNG_SIDE, Bitmap.Config.ARGB_8888)
        b.eraseColor(color)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun arts(n: Int): List<Long> = runBlocking {
        fake.library.view().page(Listing.Songs, 0, ART_SONGS).rows.filter { it.art != null }.distinctBy { it.art }.map { it.id }.take(n)
    }

    private fun feeds(ids: List<Long>, lyrics: Boolean = false): NpFeeds {
        runBlocking { fake.player.play(ids, 0, 0) }
        var f: NpFeeds? = null
        rule.setContent { f = rememberNpFeeds(src, phone, photo, s, Dispatchers.Unconfined, {}, { NOW }, lyrics) }
        rule.waitForIdle()
        return checkNotNull(f)
    }

    private fun grounded(f: NpFeeds, track: Long) {
        rule.waitUntil(WAIT_MS) { f.np.artFor == track && f.np.art != null }
        rule.waitForIdle()
    }

    @Test
    fun withTheDefaultsAndNoPhotoTheGroundIsTheCoverBlur() {
        val ids = arts(1)
        val f = feeds(ids)
        assertTrue("the feeds keep the now playing feed's answer", f.feed is NpFeed.Own)
        grounded(f, ids[0])
        val l = rule.runOnIdle { f.backdrop.layers(NOW) }
        assertEquals("the cover blur under no photo (funeOS player/ui/app.cpp App::cover_view :21763-21764, v0.4.4)", listOf(true to 1f), l.map { it.picture.whole to it.alpha })
        val pic = NpPicture(ImageBitmap(PNG_SIDE, PNG_SIDE))
        photo.value = pic
        rule.waitForIdle()
        assertSame("a photo wanted is the ground", pic, f.backdrop.shown)
    }

    @Test
    fun aTrackChangeDrawsTheNewGroundWithNoFade() {
        val ids = arts(2)
        val f = feeds(ids)
        grounded(f, ids[0])
        val first = f.backdrop.shown
        cover = png(Color.RED)
        runBlocking { fake.player.next() }
        rule.waitForIdle()
        grounded(f, ids[1])
        assertNotSame("premise: the new cover is blurred", first, f.backdrop.shown)
        assertFalse("every change cut (funeOS player/ui/lock.cpp LockPage::set_ground :45-53, v0.4.4)", rule.runOnIdle { f.backdrop.fading(NOW) })
        assertEquals(listOf(1f), rule.runOnIdle { f.backdrop.layers(NOW) }.map { it.alpha })
    }

    @Test
    fun withLyricsTheShownTracksLinesReadWithLyricsModeOff() {
        val t = checkNotNull(fake.lyricTrack(SHAPE))
        val f = feeds(listOf(t), lyrics = true)
        scheduler.runCurrent()
        rule.waitForIdle()
        assertFalse("premise: lyrics mode is off", f.np.lyrics.mode)
        assertTrue("every track's lyrics read (funeOS player/ui/app.cpp App::want_lyrics :7463-7503, v0.4.4)", f.np.lyrics.lines.isNotEmpty())
    }

    private companion object {
        const val SDK = 35
        const val V1_W = 240
        const val V1_H = 320
        const val PNG_SIDE = 64
        const val ART_SONGS = 20
        const val WAIT_MS = 5_000L
        const val NOW = 10_000L
        const val SHAPE = "short"
    }
}
