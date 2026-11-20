package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.PhoneRepeat
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class HostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val window = Window(W, H, SDK, none, none, none, none, none)
    private val page = scale(window, Nav.None, Surface.Page, false)
    private val np = scale(window, Nav.None, Surface.NowPlaying, false)

    private class Level(var pct: Int, private val cap: Int?) : VolumeLevel {
        val sets = mutableListOf<Int>()

        override fun percent(): Int = pct

        override fun stepPct(): Int = STEP

        override suspend fun step(detents: Int) {
            pct = (pct + detents * STEP).coerceIn(0, PCT)
        }

        override suspend fun set(pct: Int) {
            sets += pct
            this.pct = pct
        }

        override fun maxPct(): Int? = cap
    }

    private fun app(level: VolumeLevel) = TestApp(page, np, null, level) { rule.mainClock.currentTime }.also {
        it.compose(rule)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun drag(level: Level) {
        val app = app(level)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the hud is up", OverKind.Volume, app.stack.overlay?.kind)
        val x = Tok.canvasW / 2 * np.ppu + (np.left - page.left)
        val y = MID_U * np.ppu
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            repeat(STEPS) { moveBy(Offset(0f, -DRAG_PX / STEPS)) }
            up()
        }
        frames(OPEN_FRAMES)
    }

    @Test
    fun aDragOnTheHudPassesTheCapPercentWhenTheLevelHasNoCap() {
        val level = Level(START, null)
        drag(level)
        assertTrue("premise: the drag raises the level past $CAP, at ${level.pct}", level.pct > CAP)
    }

    @Test
    fun aCappedLevelStopsTheHudDragAtItsCap() {
        val level = Level(START, CAP)
        drag(level)
        assertTrue("premise: the drag set the level", level.sets.isNotEmpty())
        assertTrue("no set passes the cap: ${level.sets}", level.sets.all { it <= CAP })
        assertEquals(CAP, level.pct)
    }

    private fun shot(): Bitmap {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
        return b
    }

    private fun white(c: Int): Boolean = Color.red(c) >= WHITE && Color.green(c) >= WHITE && Color.blue(c) >= WHITE

    private fun whiteRows(b: Bitmap): List<Int> {
        val pix = IntArray(b.width * b.height)
        b.getPixels(pix, 0, b.width, 0, 0, b.width, b.height)
        return (0 until b.height).filter { y -> (0 until b.width).any { x -> white(pix[y * b.width + x]) } }
    }

    private fun plusTop(s: Scale, other: Scale): Int {
        val b = shot()
        val cx = px(s.w, s.ppu) / 2 + (s.left - page.left)
        val end = px(minOf(s.board.vol_plusY + s.board.vol_ringY, other.board.vol_plusY + other.board.vol_ringY) / 2, s.ppu)
        return (0 until end).firstOrNull { y -> (cx - COL..cx + COL).any { x -> Color.red(b.getPixel(x, y)) >= WHITE } } ?: -1
    }

    @Test
    fun anNpScaleChangeUnderTheHudDrawsItOnTheNewBoardAtOnce() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.NowPlaying, false)
        val shift = px(shorter.board.vol_plusY, shorter.ppu) - px(np.board.vol_plusY, np.ppu)
        assertTrue("premise: both scales draw at one ppu, ${np.ppu} and ${shorter.ppu}", np.ppu == shorter.ppu)
        assertTrue("premise: the plus moves with the board, $shift px", abs(shift) >= MIN_SHIFT)
        val app = app(Level(START, null))
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        val before = plusTop(np, shorter)
        assertTrue("premise: the plus is drawn, top $before", before >= 0)
        val rev = app.host.hud.revision
        rule.runOnIdle { app.np = shorter }
        frames(RESIZE_FRAMES)
        assertEquals("premise: the scale change changes nothing in the hud", rev, app.host.hud.revision)
        val after = plusTop(np, shorter)
        assertTrue("the plus moves with the new board at once, from $before by ${after - before} where $shift", abs(after - before - shift) <= 1)
    }

    @Test
    fun aToastShownWhileAToastHoldsAfterAPageScaleChangeSitsOnTheNewSurface() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
        val strip = px(Tok.toastHeight, page.ppu)
        val app = app(Level(START, null))
        rule.runOnIdle { app.toast.show(rule.mainClock.currentTime, "line", "title") }
        frames(TOAST_FRAMES)
        val first = whiteRows(shot())
        assertTrue("premise: the toast draws on the page bottom, rows $first", first.isNotEmpty() && first.all { it in page.heightPx - strip until page.heightPx })
        rule.runOnIdle { app.page = shorter }
        frames(RESIZE_FRAMES)
        rule.runOnIdle {
            assertTrue("premise: the first toast still holds", app.toast.visible(rule.mainClock.currentTime))
            app.toast.show(rule.mainClock.currentTime, "next", "title")
        }
        frames(TOAST_FRAMES)
        val rows = whiteRows(shot())
        assertTrue("the next toast sits on the new page bottom ${shorter.heightPx}, rows $rows", rows.isNotEmpty() && rows.all { it in shorter.heightPx - strip until shorter.heightPx })
    }

    @Test
    fun aTurnAfterAPageScaleChangeTakesItsThumbFromTheNewDrawMapsAndLeavesTheOldOnes() {
        val shorter = scale(Window(W, H_SHORT, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
        val app = app(Level(START, null))
        val first = app.host.types
        rule.runOnIdle { app.page = shorter }
        frames(RESIZE_FRAMES)
        val types = app.host.types
        assertTrue("premise: the scale change makes new draw maps", types !== first)
        val e = MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))
        rule.runOnIdle { app.stack.push(e, MoveKind.T2) }
        frames(OPEN_FRAMES)
        val m = app.models[e.entry]
        assertTrue("premise: the push enters the album, $m", m is AlbumModel)
        assertTrue("premise: the album is drawn from the new maps", types.holds(m))
        assertFalse("the turn's thumb builds no draw in the old maps", first.holds(m))
    }

    @Test
    fun aPhoneSessionThatStartsAfterTheFirstCompositionShowsAsTheVariant() {
        val app = app(Level(START, null))
        frames(OPEN_FRAMES)
        assertFalse("premise: no session, no variant", app.music.np.variant)
        val track = PhoneTrack("Radio", "Song", "Artist", "Album", true, false, DURATION, 0L, 0L, 1f, false, PhoneRepeat.Off, 0, 0, null)
        rule.runOnIdle { app.phone.value = PhoneSession(track, {}, {}) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { assertTrue("the late session shows as the variant", app.music.np.variant) }
    }

    @Test
    fun aResetUnderNowPlayingRefusesUnderPagesThatHoldNowPlaying() {
        val app = app(Level(START, null))
        val kept = MusicEntry(app.stack.entry(), MusicPage.NowPlaying)
        val refused = rule.runOnIdle { runCatching { app.host.resetNp(listOf(kept)) }.exceptionOrNull() }
        assertTrue("the reset refuses, $refused", refused is IllegalArgumentException)
    }

    @Test
    fun aResetUnderNowPlayingLandsTheAppPagesUnderANewNowPlayingWithNoTurnAndSetsItUp() {
        val app = app(Level(START, null))
        val hub = MusicEntry(app.stack.entry(), MusicPage.Hub)
        val album = MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))
        rule.runOnIdle { app.stack.reset(listOf(hub, album)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the hub and the album are up", listOf(hub, album), app.stack.pages.toList())
        val np = app.music.np
        rule.runOnIdle {
            np.slide.start = STALE
            np.slide.target = STALE + 1
        }
        val pokes = app.pokes
        val wake = np.idleWake()
        val at = rule.runOnIdle { rule.mainClock.currentTime.also { app.host.resetNp(listOf(hub)) } }
        assertTrue("premise: the focus was last reset before the reset, wake $wake at $at", wake < at + NowPlayingModel.IDLE)
        frames(OPEN_FRAMES)
        val pages = app.stack.pages.toList()
        assertEquals(2, pages.size)
        assertEquals(hub, pages[0])
        assertTrue("now playing is on top", app.host.isNp(pages[1]))
        assertNull("no turn", app.stack.turn)
        assertEquals(Phase.Rest, app.stack.phase)
        assertNull("the album's model is dropped", app.models[album.entry])
        assertNotNull("the new now playing has its model", app.models[pages[1].entry])
        assertEquals("the focus is reset at the reset", at + NowPlayingModel.IDLE, np.idleWake())
        assertNull("a stale slide is cleared", np.slide.target)
        assertTrue("the reset pokes the page", app.pokes > pokes)
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val PCT = 100
        private const val STEP = 10
        private const val START = 50
        private const val CAP = 60
        private const val MID_U = 300f
        private const val DRAG_PX = 600f
        private const val STEPS = 10
        private const val OPEN_FRAMES = 30
        private const val DURATION = 180_000L
        private const val H_SHORT = 1080
        private const val MIN_SHIFT = 10
        private const val COL = 2
        private const val WHITE = 200
        private const val RESIZE_FRAMES = 2
        private const val TOAST_FRAMES = 20
        private const val ALBUM = 20L
        private const val STALE = 7L
    }
}
