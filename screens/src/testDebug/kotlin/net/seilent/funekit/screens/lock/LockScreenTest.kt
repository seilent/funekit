package net.seilent.funekit.screens.lock

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.scale
import net.seilent.funekit.screens.host.NpFeeds
import net.seilent.funekit.screens.host.VolumeLevel
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpBackdrop
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.PhoneRepeat
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack
import net.seilent.funekit.screens.music.nowplaying.lyricAt
import net.seilent.funekit.screens.music.nowplaying.npNanosAt
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.InterruptedIOException
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class LockScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val none = Insets(0, 0, 0, 0)
    private fun window(h: Int, w: Int = W) = Window(w, h, SDK, none, none, none, none, none)
    private val lockScale = scale(window(H), Nav.None, Surface.Saver, false)
    private val npScale = scale(window(H), Nav.None, Surface.NowPlaying, false)
    private val level = Level()
    private val owner = Owner()
    private var size by mutableStateOf(lockScale)
    private var opens = 0
    private var downs = 0
    private var reads = 0
    private val logs = mutableListOf<String>()
    private var t0 = 0L

    private class Level : VolumeLevel {
        var pct = START_PCT
        var steps = 0

        override fun percent(): Int = pct

        override fun stepPct(): Int = 1

        override suspend fun step(detents: Int) {
            steps += detents
            pct += detents
        }

        override suspend fun set(pct: Int) {
            this.pct = pct
        }
    }

    private class Owner : LifecycleOwner {
        val reg = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = reg
    }

    private fun now(): Long = rule.mainClock.currentTime

    private fun wall(): Long = WALL0 + now() - t0

    private fun clockText(): String = "c${wall() / MS_S}"

    private fun begin() {
        rule.mainClock.autoAdvance = false
        t0 = now()
    }

    private fun frames(n: Int) = repeat(n) {
        rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun lyricNp(pos: Long): NowPlayingModel = NowPlayingModel().also {
        it.feed(PlayerState(queueSize = 1, index = 0, track = TRACK, playing = true, anchor = Anchor(pos, RATE, npNanosAt(now()), true, DURATION)))
        it.sync(NpShown.of(listOf(Row(TRACK, Kind.Song, "Song", "Artist", null, durationMs = DURATION, album = "Album"))), now())
        it.lyrics.begin(TRACK)
        it.lyrics.load(TRACK, LRC, 0)
    }

    private fun lock(np: NowPlayingModel = NowPlayingModel(), started: Boolean = true, src: MusicSource? = null): LockState {
        val st = LockState()
        val feeds = NpFeeds(np, NpLayout(npScale), NpBackdrop(), src)
        rule.runOnUiThread { owner.reg.currentState = if (started) Lifecycle.State.RESUMED else Lifecycle.State.CREATED }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LockScreen(st, feeds, size, { reads++; clockText() }, level, false, { downs++ }, { opens++ }, { logs += it }, ::now, ::wall, Dispatchers.Unconfined)
            }
        }
        frames(2)
        return st
    }

    private fun commitDrag() {
        rule.onRoot().performTouchInput {
            down(Offset(X, Y0))
            moveTo(Offset(X, Y0 - H * COMMIT_SHARE))
            up()
        }
        frames(1)
    }

    @Test
    fun aVolumeKeyStepsTheLevelAndShowsThePlate() {
        begin()
        assertFalse("a key before the lock composes is not taken", LockState().volume(1))
        val st = lock()
        assertTrue("a key on the lock is taken", rule.runOnIdle { st.volume(1) })
        frames(2)
        assertEquals("funeOS player/ui/app.cpp App::open_volume :23022-23050, v0.4.4", 1, level.steps)
        assertTrue(rule.runOnIdle { st.hud.visible(now()) })
        assertEquals("the plate shows the stepped level", START_PCT + 1, st.hud.percent)
    }

    @Test
    fun aDownWithThePlateUpClosesItAndDrags() {
        begin()
        val st = lock()
        rule.runOnUiThread { st.volume(1) }
        frames(2)
        assertTrue("premise: the plate shows", rule.runOnIdle { st.hud.visible(now()) })
        rule.onRoot().performTouchInput { down(Offset(X, Y0)) }
        frames(1)
        assertFalse("a down closes the plate at once (funeOS player/ui/app.cpp App::vol_touch :17237-17246, v0.4.4)", rule.runOnIdle { st.hud.visible(now()) })
        assertTrue("and drags the lock", st.drag.dragging)
        assertEquals(1, downs)
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aVolumeKeyMidDragRestsItAndMidCommitEaseOpens() {
        begin()
        val st = lock()
        rule.onRoot().performTouchInput {
            down(Offset(X, Y0))
            moveTo(Offset(X, Y0 - DRAG_PX))
        }
        frames(1)
        assertTrue("premise: the lock is dragged", st.drag.dragging && st.drag.offset(now()) > 0)
        rule.runOnUiThread { st.volume(1) }
        frames(1)
        assertFalse("a key mid drag rests it (funeOS player/ui/app.cpp App::cancel_touch :15157-15158, v0.4.4)", st.drag.dragging)
        assertEquals(0, st.drag.offset(now()))
        assertEquals(0, opens)
        rule.onRoot().performTouchInput { up() }
        frames(1)
        commitDrag()
        assertTrue("premise: the commit eases", st.drag.committing && st.drag.moving(now()))
        assertFalse("premise: the down closed the plate", rule.runOnIdle { st.hud.visible(now()) })
        rule.runOnUiThread { st.volume(1) }
        assertEquals("a key mid commit ease ends the lock (funeOS player/ui/app.cpp App::cancel_touch :15155-15156, v0.4.4)", 1, opens)
        frames(2)
        assertEquals("the step still applies", 2, level.steps)
    }

    @Test
    fun theOpenIsCalledOnceAtTheEasesLandingAndNotOnASpringBack() {
        begin()
        val st = lock()
        commitDrag()
        assertTrue("premise: the up commits", st.drag.committing)
        assertEquals("none at the up", 0, opens)
        rule.mainClock.advanceTimeBy(Tok.motion_lockCommit.toLong() / EASE_PARTS)
        frames(2)
        assertTrue("premise: the commit still eases", st.drag.committing && st.drag.moving(now()))
        assertEquals("none mid ease", 0, opens)
        rule.mainClock.advanceTimeBy(Tok.motion_lockCommit.toLong() + FRAME * 3)
        frames(2)
        assertEquals("once at the landing (funeOS player/ui/app.cpp App::draw_lock :11732-11733, v0.4.4)", 1, opens)
        frames(4)
        assertEquals(1, opens)
        rule.runOnUiThread { st.rest() }
        frames(1)
        rule.onRoot().performTouchInput {
            down(Offset(X, Y0))
            moveTo(Offset(X, Y0 - SPRING_PX))
            moveTo(Offset(X, Y0 - SPRING_PX * 2))
            up()
        }
        frames(1)
        assertFalse("premise: a short drag springs back", st.drag.committing)
        rule.mainClock.advanceTimeBy(Tok.motion_lockCommit.toLong() + FRAME * 3)
        frames(2)
        assertEquals("a spring back opens nothing", 1, opens)
        assertEquals(0, st.drag.offset(now()))
    }

    @Test
    fun aPointerCancelMidDragRests() {
        begin()
        val st = lock()
        rule.onRoot().performTouchInput {
            down(Offset(X, Y0))
            moveTo(Offset(X, Y0 - DRAG_PX))
        }
        frames(1)
        assertTrue("premise: the lock is dragged", st.drag.dragging)
        rule.onRoot().performTouchInput { cancel() }
        frames(1)
        assertFalse("a cancel rests the drag (funeOS player/ui/app.cpp App::cancel_touch :15157-15158, v0.4.4)", st.drag.dragging)
        assertEquals(0, st.drag.offset(now()))
        assertFalse(st.drag.committing)
    }

    @Test
    fun theClockFollowsTheClockTextAtTheNextWallSecond() {
        begin()
        val st = lock()
        val first = clockText()
        assertEquals("one read at the start", first, st.clock)
        val second = now() + MS_S - wall().mod(MS_S)
        rule.mainClock.advanceTimeBy(second - now() - FRAME * 3)
        assertEquals("premise: still the first second", first, clockText())
        assertEquals(first, st.clock)
        rule.mainClock.advanceTimeBy(FRAME * 4)
        frames(1)
        assertEquals("the clock at the next wall second (funeOS player/ui/app.cpp App::wake_step :21458-21459, v0.4.4)", clockText(), st.clock)
        assertTrue("premise: the clock text moved", st.clock != first)
    }

    @Test
    fun theLineFollowsThePositionAtItsLinesTimeWithLyricsModeOff() {
        begin()
        val np = lyricNp(HALF_LINE)
        val st = lock(np)
        assertFalse("premise: lyrics mode is off", np.lyrics.mode)
        assertEquals("one", st.line.text.text)
        rule.mainClock.advanceTimeBy(LINE_MS - HALF_LINE + FRAME * 3)
        frames(1)
        assertEquals("premise: before the next wall second", WALL0 / MS_S, wall() / MS_S)
        assertEquals("the line at its time (funeOS player/ui/app.cpp App::saver_line_due :7936-7942, v0.4.4)", "two", st.line.text.text)
    }

    @Test
    fun aPhoneTrackWithLyricsDrawsNoLine() {
        begin()
        val np = NowPlayingModel()
        val p = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, HALF_LINE, npNanosAt(now()) / NANOS_MS, 1f, false, PhoneRepeat.Off, 0, 0, null)
        np.feed(p)
        val shown = NpShown.of(p)
        np.sync(shown, now())
        val id = checkNotNull(shown.track)
        np.lyrics.phoneLyrics = true
        np.lyrics.begin(id)
        np.lyrics.load(id, LRC, 0)
        assertTrue("premise: the phone's lyrics name a line", np.lyrics.index(lyricAt(np, now())) >= 0)
        val st = lock(np)
        assertTrue("premise: the phone is the variant", np.variant)
        assertEquals("no line for a phone track (funeOS player/ui/app.cpp App::lock_text :11456, v0.4.4)", "", st.line.text.text)
    }

    @Test
    fun belowStartedTheRefreshReadsNothingAndTheStartReadsOnce() {
        begin()
        val st = lock(started = false)
        rule.mainClock.advanceTimeBy(MS_S * 2)
        assertEquals("below STARTED nothing is read", 0, reads)
        assertEquals("", st.clock)
        rule.runOnUiThread { owner.reg.currentState = Lifecycle.State.STARTED }
        frames(2)
        assertEquals("the start reads once (funeOS player/ui/app.cpp App::lock_show :11407-11420, v0.4.4)", 1, reads)
        assertEquals(clockText(), st.clock)
    }

    @Test
    fun aLineThatMovedBelowStartedShowsWholeAtTheStart() {
        begin()
        val np = lyricNp(HALF_LINE)
        val st = lock(np)
        assertEquals("premise: the first line shows", "one", st.line.text.text)
        rule.runOnUiThread { owner.reg.currentState = Lifecycle.State.CREATED }
        frames(1)
        rule.mainClock.advanceTimeBy(LINE_MS - HALF_LINE + FRAME * 3)
        assertEquals("premise: no read while stopped", "one", st.line.text.text)
        rule.runOnUiThread { owner.reg.currentState = Lifecycle.State.STARTED }
        frames(1)
        assertEquals("the line at the start (funeOS player/ui/app.cpp App::lock_show :11415, v0.4.4)", "two", st.line.shown(now()).text)
        assertFalse("a show cuts the fade (funeOS player/ui/lock.cpp LockPage::show :23, v0.4.4)", st.line.moving(now()))
        assertEquals(1f, st.line.alpha(now()), 0f)
    }

    @Test
    fun underFillsColorBgBehindTheBandAndTheSliver() {
        val off = H / 2
        for (under in listOf(true, false)) {
            val img = ImageBitmap(W, H)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(W.toFloat(), H.toFloat())) {
                lockFrame(under, off, Tok.motion_lockSliverAlpha, Paint()) { drawRect(Color.White) }
            }
            val b = img.asAndroidBitmap()
            val band = b.getPixel(W / 2, off + off / 2)
            val sliver = b.getPixel(W / 2, off / 2)
            if (under) {
                assertEquals("color.bg behind the band (funeOS player/ui/app.cpp App::draw_lock :11679-11737, v0.4.4)", Ink.bg.toArgb(), band)
                assertEquals("an opaque sliver over color.bg", OPAQUE, android.graphics.Color.alpha(sliver))
            } else {
                assertEquals("a clear band", 0, android.graphics.Color.alpha(band))
                assertEquals("the sliver at its alpha alone", (Tok.motion_lockSliverAlpha * OPAQUE).roundToInt().toFloat(), android.graphics.Color.alpha(sliver).toFloat(), 1f)
            }
        }
    }

    @Test
    fun aScreenOffRestsAndCutsThePlate() {
        begin()
        val st = lock()
        rule.runOnUiThread { st.volume(1) }
        frames(1)
        assertTrue("premise: the plate shows", rule.runOnIdle { st.hud.visible(now()) })
        rule.runOnUiThread { st.screenOff() }
        assertFalse("the plate is cut (funeOS player/ui/app.cpp App::screen_off :23603-23605, v0.4.4)", st.hud.visible(now()))
        frames(1)
        rule.onRoot().performTouchInput {
            down(Offset(X, Y0))
            moveTo(Offset(X, Y0 - DRAG_PX))
        }
        frames(1)
        assertTrue("premise: the lock is dragged", st.drag.dragging)
        rule.runOnUiThread { st.screenOff() }
        assertFalse("the drag rests (funeOS player/ui/app.cpp App::screen_off :23619-23622, v0.4.4)", st.drag.dragging)
        assertEquals(0, st.drag.offset(now()))
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun anArtReadFailingAtACardEjectLogsOnceAndKeepsTheTile() {
        begin()
        val np = NowPlayingModel().also { it.sync(NpShown.None.copy(track = TRACK, title = "Song", art = ArtRef.Cover(EJECT_ART, "cover.jpg")), now()) }
        lock(np, src = ejecting())
        assertEquals("a read cut by the eject is absent art, the tile stays", listOf("lock art: $EJECTED"), logs)
    }

    private fun ejecting(): MusicSource {
        val fake = testFake(FakeSet.Demo, TestCoroutineScheduler())
        fun cut(v: LibraryView): LibraryView = object : LibraryView by v {
            override suspend fun art(ref: ArtRef, maxSide: Int): ByteArray = throw InterruptedIOException(EJECTED)
        }
        val library = object : Library by fake.library {
            override fun view(): LibraryView = cut(fake.library.view())

            override fun view(generation: Long): LibraryView = cut(fake.library.view(generation))
        }
        return MusicSource(library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)
    }

    @Test
    fun aWindowSizeChangeMidDragRests() {
        begin()
        val st = lock()
        rule.onRoot().performTouchInput {
            down(Offset(X, Y0))
            moveTo(Offset(X, Y0 - DRAG_PX))
        }
        frames(1)
        assertTrue("premise: the lock is dragged", st.drag.dragging)
        size = scale(window(H_SHORT), Nav.None, Surface.Saver, false)
        frames(2)
        assertFalse("a size change rests the drag, as the launcher cancels at a fold", st.drag.dragging)
        assertEquals("the drag takes the new height", H_SHORT, st.drag.heightPx)
        assertEquals(0, st.drag.offset(now()))
        rule.onRoot().performTouchInput { up() }
        frames(1)
        assertFalse("the up after it commits nothing", st.drag.committing)
        rule.onRoot().performTouchInput {
            down(Offset(X, Y0))
            moveTo(Offset(X, Y0 - DRAG_PX))
        }
        frames(1)
        assertTrue("premise: dragged again", st.drag.dragging)
        size = scale(window(H_SHORT, W_NARROW), Nav.None, Surface.Saver, false)
        frames(2)
        assertFalse("a width change alone rests it too", st.drag.dragging)
        rule.onRoot().performTouchInput { up() }
    }

    private companion object {
        const val SDK = 35
        const val W = 720
        const val H = 1280
        const val H_SHORT = 1180
        const val W_NARROW = 700
        const val X = 360f
        const val Y0 = 1000f
        const val DRAG_PX = 200f
        const val SPRING_PX = 45f
        const val COMMIT_SHARE = 0.6f
        const val EASE_PARTS = 4L
        const val FRAME = 16L
        const val MS_S = 1000L
        const val WALL0 = 1_700_000_000_100L
        const val START_PCT = 50
        const val TRACK = 1L
        const val RATE = 1000
        const val HALF_LINE = 1500L
        const val LINE_MS = 2000L
        const val DURATION = 180_000L
        const val NANOS_MS = 1_000_000L
        const val OPAQUE = 255
        const val EJECT_ART = 9_901L
        const val EJECTED = "card ejected"
        const val LRC = "[00:01.00]one\n[00:02.00]two\n[00:03.00]three"
    }
}
