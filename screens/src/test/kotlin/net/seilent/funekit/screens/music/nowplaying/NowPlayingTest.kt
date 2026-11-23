package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.np.NP_BACK
import net.seilent.funekit.kit.np.NP_HEART
import net.seilent.funekit.kit.np.NP_MARK
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.np.NpRingPlace
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.fxInt
import net.seilent.funekit.scale
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.host.PhoneSession
import net.seilent.funekit.screens.host.nextPhoneRepeat
import net.seilent.funekit.screens.host.npAct
import net.seilent.funekit.screens.host.npMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HELD_AT = 1000L
private const val MS_NANOS = 1000000L
private const val RATE = 48000
private const val DURATION = 180000L
private const val SLIDE_DOWN = 2700L
private const val SLIDE_UP = 2900L
private const val SWAP = 3000L
private const val TEXT_AT = 3117L
private const val OTHER_AT = 3050L
private const val START_AT = 2950L
private const val DRAG_FROM = 0.25f
private const val DRAG_TO = 0.75f
private const val SEEK_Y = 300f
private const val AFTER_UP = 2000L

class NowPlayingTest {
    private val none = Insets(0, 0, 0, 0)

    private fun layout(w: Int, h: Int, navBar: Int = 0, large: Boolean = false): NpLayout {
        val nav = if (navBar > 0) Nav.Buttons else Nav.None
        val win = Window(w, h, 35, none, Insets(0, 0, 0, navBar), none, none, none, null)
        return NpLayout(scale(win, nav, Surface.NowPlaying, large))
    }

    private val a105 = layout(720, 1280)
    private val r1 = layout(480, 800)
    private val v1 = layout(240, 320)

    private fun song(id: Long, title: String, ms: Long = 180000) = Row(id, Kind.Song, title, "Artist $id", null, durationMs = ms, album = "Album $id")

    private fun shown(id: Long, ms: Long = 180000) = NpShown.of(listOf(song(id, "Song $id", ms), song(id + 1, "Next"), song(id + 2, "Later"), song(id + 3, "Last")))

    @Test
    fun theA105LayoutTakesTheBoardTokensAtThreePxPerUnit() {
        val g = a105
        assertEquals(458, g.artSide)
        assertEquals(42, g.artX)
        assertEquals(422, g.artY)
        assertEquals(883, g.bar.y0)
        assertEquals(9, g.bar.h)
        assertEquals(998, g.titleY)
        assertEquals(1195, g.ringCy)
        assertEquals(3, g.nextLines)
        assertEquals(1044, g.nextBase(0))
        assertEquals(1079, g.nextBase(1))
        assertEquals(927, g.timeBase)
        assertEquals(206, g.backSize)
    }

    @Test
    fun theR1AndV1LayoutsTakeTheirBoardTokens() {
        assertEquals(299, r1.artSide)
        assertEquals(234, r1.artY)
        assertEquals(612, r1.titleY)
        assertEquals(744, r1.ringCy)
        assertEquals(3, r1.nextLines)
        assertEquals(92, v1.artSide)
        assertEquals(292, v1.ringCy)
        assertEquals(1, v1.nextLines)
    }

    @Test
    fun aNavBarPutsTheRingsAboveAndroidsButtonsWithTheComingUpLinesTheyClear() {
        val a105 = layout(720, 1280, 108)
        assertEquals(1087, a105.ringCy)
        assertEquals(0, a105.nextLines)
        val units = NpLayout(a105.s, NpRingPlace.Units)
        assertEquals(1163, units.ringCy)
        assertEquals(2, units.nextLines)
        val short = layout(720, 1280, 132)
        assertEquals(1063, short.ringCy)
        assertEquals(0, short.nextLines)
        val r1 = layout(480, 800, 72)
        assertEquals(672, r1.ringCy)
        assertEquals(0, r1.nextLines)
        assertEquals(722, NpLayout(r1.s, NpRingPlace.Units).ringCy)
        val phone = layout(1080, 2160, 132)
        assertEquals(1901, phone.ringCy)
        assertEquals(1, phone.nextLines)
        assertTrue(phone.nextBaseU(0) < phone.ringYU - 29.7f / 2)
        assertTrue(phone.nextBaseU(1) >= phone.ringYU - 29.7f / 2)
    }

    @Test
    fun largeTakesTheLargeNextCountAndTimeBaseline() {
        val g = layout(720, 1280, large = true)
        assertEquals(2, g.nextLines)
        assertEquals(933, g.timeBase)
        val nav = layout(720, 1280, 108, large = true)
        assertEquals(0, nav.nextLines)
        assertEquals(1, NpLayout(nav.s, NpRingPlace.Units).nextLines)
    }

    @Test
    fun timesFormatAsMinutesAndSecondsWithTheHourInMinutes() {
        assertEquals("0:05", npTime(5000))
        assertEquals("60:00", npTime(NP_HOUR_MS))
        assertEquals("-2:55", npRemaining(180000, 5000))
        assertEquals("-0:00", npRemaining(180000, 200000))
    }

    @Test
    fun aTrackOfAnHourPutsTheRemainingTimeAtTheRightEdge() {
        val g = a105
        assertEquals(g.timeRight64 - 640, timeRight64(g, 100f, 10f, 180000))
        assertEquals(g.timeWideRight64 - 640, timeRight64(g, 100f, 10f, NP_HOUR_MS))
        assertEquals(g.timeWideRight64 - 640, timeRight64(g, 450f, 10f, 180000))
    }

    @Test
    fun theSeekTargetIsClampedToTheArt() {
        assertEquals(0L, a105.seekAt(0f, 180000))
        assertEquals(180000L, a105.seekAt(239f, 180000))
        assertEquals(90000.0, a105.seekAt(14.1f + 152.7f / 2, 180000).toDouble(), 1.0)
    }

    @Test
    fun ringHitsSplitHalfwayBetweenRingCentresInsideTheRingBand() {
        val g = a105
        assertEquals(0, g.ringHit(75.8f, 398f, 3))
        assertEquals(1, g.ringHit(76f, 398f, 3))
        assertEquals(2, g.ringHit(163.7f, 398f, 3))
        assertEquals(-1, g.ringHit(100f, 371.4f, 3))
        assertEquals(-1, g.ringHit(100f, 412.7f, 3))
        assertEquals(-1, g.ringHit(200f, 398f, 2))
    }

    @Test
    fun theOpenFocusesRingZeroAndARingDownMovesIt() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.resetFocus(0)
        assertEquals(0, m.ringFocus())
        m.down(a105, 120f, 398f, 10)
        assertEquals(1, m.ringFocus())
        m.tap(a105, 20)
        assertEquals(1, m.ringFocus())
    }

    @Test
    fun aTitleDownHidesTheRingFocusAndARingDownBringsItBack() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.resetFocus(0)
        m.down(a105, 100f, 330f, 10)
        assertTrue(m.titleFocus)
        assertEquals(-1, m.ringFocus())
        assertEquals(NpAct.Queue, m.tap(a105, 20))
        assertTrue(m.titleFocus)
        m.down(a105, 30f, 398f, 30)
        assertEquals(0, m.ringFocus())
    }

    @Test
    fun aCancelledTitleTouchKeepsTheTitleFocusUntilTheGestureEnds() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.down(a105, 100f, 330f, 10)
        m.cancel(20)
        assertTrue(m.titleFocus)
        m.gestureUp()
        assertFalse(m.titleFocus)
    }

    @Test
    fun theHeartRingIsAFavoriteActAndTheOthersToggle() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.down(a105, 207f, 398f, 0)
        assertEquals(NpAct.Favorite, m.tap(a105, 10))
        m.down(a105, 30f, 398f, 20)
        assertEquals(NpAct.Shuffle, m.tap(a105, 30))
        m.down(a105, 120f, 398f, 40)
        assertEquals(NpAct.Repeat, m.tap(a105, 50))
        m.down(a105, 10f, 10f, 60)
        assertEquals(NpAct.Back, m.tap(a105, 70))
        m.down(a105, 80f, 200f, 80)
        assertEquals(NpAct.Controls, m.tap(a105, 90))
    }

    @Test
    fun aKeyDropsThePressAtOnceAndTheUpTakesNoAct() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.down(a105, 207f, 398f, 0)
        m.keyed(40)
        assertEquals("the key drops the heart press at once", 1f, m.press[NP_HEART].scale(40f), 0f)
        assertEquals(NpAct.None, m.tap(a105, 40))
    }

    @Test
    fun aSeekPressAndReleaseSeeksOnceToTheReleasePoint() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 14.1f + 152.7f / 4, 300f, 0)
        assertTrue(m.seeking)
        assertEquals(45000.0, m.seekUp(a105, 14.1f + 152.7f / 4)!!.toDouble(), 1.0)
        assertFalse(m.seeking)
        assertEquals(null, m.seekUp(a105, 50f))
    }

    @Test
    fun aSeekDragShowsItsTargetTimeAndFillUntilTheUp() {
        val state = PlayerState(queueSize = 4, index = 0, track = 1, playing = true, anchor = Anchor(0, RATE, 0, true, DURATION))
        val m = NowPlayingModel()
        m.feed(state)
        m.sync(shown(1), 0)
        val unit = NowPlayingModel()
        unit.feed(state)
        unit.sync(shown(1), 0)
        val x = { f: Float -> Tok.npArtX + f * a105.s.board.npArt }
        m.down(a105, x(DRAG_FROM), SEEK_Y, 0)
        assertTrue("premise: the down takes the band", m.seeking)
        m.seekMove(a105, x(DRAG_TO))
        val target = a105.seekAt(x(DRAG_TO), DURATION)
        val held = npTimes(a105, m, HELD_AT, HELD_AT * MS_NANOS)
        assertEquals("the time shows the drag's target", npTime(target), held.left)
        assertEquals("the fill shows the drag's target", (target * a105.bar.w / DURATION).toInt(), held.fill)
        assertNotEquals("premise: the unit plays elsewhere", npTimes(a105, unit, HELD_AT, HELD_AT * MS_NANOS), held)
        m.seekUp(a105, x(DRAG_TO))
        assertEquals("after the up the unit's time and fill", npTimes(a105, unit, AFTER_UP, AFTER_UP * MS_NANOS), npTimes(a105, m, AFTER_UP, AFTER_UP * MS_NANOS))
    }

    @Test
    fun aTrackChangeShowsTheNewTrackAtOnce() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.changed(shown(5), 1000)
        assertEquals(5L, m.shown.track)
        assertEquals(5L, m.shownAt(1000).track)
    }

    @Test
    fun theSameTrackWithNewNextLinesChangesAtOnce() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        val n = NpShown.of(listOf(song(1, "Song 1"), song(9, "Other")))
        m.changed(n, 100)
        assertEquals("Other", m.shown.next)
    }

    @Test
    fun theControlsFadeToTheIdleAlphaAfterFiveSeconds() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.resetFocus(0)
        m.stepTo(4999)
        assertEquals(1f, m.idleAlpha(4999), 0f)
        m.stepTo(5000)
        assertEquals(0.4f, m.idleAlpha(5380), 0.001f)
        m.touched(6000)
        assertEquals(1f, m.idleAlpha(6380), 0.001f)
    }

    @Test
    fun theTimeBandChangesOnlyAtTheProgressTick() {
        val m = NowPlayingModel()
        val nanos = 1000000L
        m.feed(PlayerState(queueSize = 1, index = 0, track = 1, playing = true, anchor = Anchor(0, 48000, 0, true, 180000)))
        m.sync(shown(1), 0)
        val a = npTimes(a105, m, 1000, 1000 * nanos)
        assertEquals(a, npTimes(a105, m, 1249, 1249 * nanos))
        assertNotEquals(a, npTimes(a105, m, 1250, 1250 * nanos))
        assertEquals("0:01", a.left)
        assertEquals("-2:59", a.right)
    }

    @Test
    fun aCommittedSlideLandsOnTheNextCover() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, 1000)
        assertTrue(m.slideLock(100f))
        m.slideDrag(40f, 240f)
        assertEquals(-60f, m.slideOffset(1100), 0f)
        assertTrue(m.slideUp(1200, 1, 240f, 1200 * MS_NANOS))
        assertEquals(2L, m.slide.target)
        assertEquals(180f, m.slideOffset(1200), 0.001f)
        assertTrue(m.slideOffset(1700) > 0f)
        assertEquals("the slide lands over motion.npSlideLand", 0f, m.slideOffset(1750), 0f)
        m.changed(shown(2), 1300)
        assertEquals(2L, m.shown.track)
        assertEquals("the old text holds to the text delay", 1L, m.shownAt(1416).track)
        assertEquals(2L, m.shownAt(1417).track)
    }

    private fun committed(): NowPlayingModel {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true, anchor = Anchor(0, RATE, 0, true, DURATION)))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, SLIDE_DOWN)
        assertTrue(m.slideLock(100f))
        m.slideDrag(40f, 240f)
        assertTrue(m.slideUp(SLIDE_UP, 1, 240f, SLIDE_UP * MS_NANOS))
        assertEquals(2L, m.slide.target)
        return m
    }

    @Test
    fun aCommittedSlideHoldsItsPositionWithTheOldTextUntilTheTextDelay() {
        val m = committed()
        m.feed(PlayerState(queueSize = 4, index = 1, track = 2, playing = true, anchor = Anchor(0, RATE, SWAP * MS_NANOS, true, DURATION)))
        m.changed(shown(2), SWAP)
        assertEquals("premise: the text delay ends at the up plus motion.npSlideTextDelay", TEXT_AT, SLIDE_UP + NowPlayingModel.SLIDE_TEXT_DELAY)
        val held = npTimes(a105, m, TEXT_AT - 1, (TEXT_AT - 1) * MS_NANOS)
        assertEquals(npTime(SLIDE_UP), held.left)
        assertEquals(npRemaining(DURATION, SLIDE_UP), held.right)
        assertEquals((SLIDE_UP * a105.bar.w / DURATION).toInt(), held.fill)
        val live = npTimes(a105, m, TEXT_AT, TEXT_AT * MS_NANOS)
        assertEquals(npTime(0), live.left)
        assertEquals(npRemaining(DURATION, 0), live.right)
    }

    @Test
    fun aSyncToAnotherTrackDuringTheHoldDropsTheSlide() {
        val m = committed()
        m.sync(shown(3), OTHER_AT)
        assertEquals(null, m.slide.target)
        assertEquals(3L, m.shownAt(OTHER_AT).track)
    }

    @Test
    fun aSyncToTheStartTrackDuringTheHoldKeepsTheSlide() {
        val m = committed()
        m.sync(shown(1), START_AT)
        assertEquals(2L, m.slide.target)
        assertEquals(1L, m.shownAt(START_AT).track)
    }

    @Test
    fun aSyncToTheTargetDuringTheHoldMarksItShownAndHoldsTheOldTextToTheDelay() {
        val m = committed()
        m.sync(shown(2), SWAP)
        assertEquals(SWAP, m.slide.shownAt)
        assertEquals(1L, m.shownAt(TEXT_AT - 1).track)
        assertEquals(2L, m.shownAt(TEXT_AT).track)
    }

    @Test
    fun aLandedSlideCommitsNothingAtTheUpBeforeTheNextFrame() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, 1000)
        assertTrue(m.slideLock(100f))
        m.slideDrag(40f, 240f)
        m.slideLand(1100, 240f)
        assertFalse(m.slideUp(1110, 1, 240f, 1110 * MS_NANOS))
        assertEquals(null, m.slide.target)
    }

    @Test
    fun aLandedSlideThatNeverLockedKeepsItsDownForTheSwipe() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, 1000)
        m.slideLand(1100, 240f)
        assertTrue(m.slide.touch)
        assertFalse(m.slideLock(100f))
    }

    @Test
    fun anUncommittedSlideLandsBackAndClears() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, 1000)
        m.slideLock(100f)
        m.slideDrag(150f, 240f)
        assertFalse(m.slideUp(1200, 0, 240f, 1200 * MS_NANOS))
        m.stepTo(1200)
        assertEquals(50f, m.slideOffset(1200), 0.001f)
        m.stepTo(1800)
        m.stepTo(1801)
        assertFalse(m.slideBusy())
    }

    @Test
    fun aSlideIsNotArmedInTheGestureBand() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.down(a105, 100f, 420f, 1000)
        assertFalse(m.slideLock(100f))
    }

    @Test
    fun aStillPausedPageWakesOnlyAtTheIdleTimeAndAPlayingOneAtTheTick() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.resetFocus(0)
        m.stepTo(100)
        assertFalse(m.animating(100))
        assertEquals(5000L, m.wakeAt(100))
        m.stepTo(5000)
        assertTrue(m.animating(5100))
        m.stepTo(5400)
        assertFalse(m.animating(5400))
        assertEquals(null, m.wakeAt(5400))
        m.feed(PlayerState(queueSize = 1, index = 0, track = 1, playing = true))
        assertEquals(5500L, m.wakeAt(5400))
    }

    @Test
    fun noFeedClearsTheShownTrack() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.clear()
        assertEquals(null, m.shown.track)
        assertFalse(m.variant)
    }

    @Test
    fun thePhonePositionRunsOnTheElapsedClock() {
        val p = PhoneTrack("Radio", "T", "A", "B", true, false, 180000, 10000, 500000, 1f, false, PhoneRepeat.Off, 0, 0, null)
        val m = NowPlayingModel()
        m.feed(p)
        assertEquals(12000L, m.position(1000, 502000L * 1000000L))
    }

    @Test
    fun thePhoneVariantTakesNoShuffleOrRepeatRing() {
        val m = NowPlayingModel()
        m.feed(PhoneTrack("Radio", "T", "A", "B", true, false, 0, 0, 0, 1f, false, PhoneRepeat.Off, 0, 0, null))
        m.sync(NpShown.of(m.phone!!), 0)
        assertEquals(0, m.ringAt(a105, 30f, 398f))
        assertEquals(-1, m.ringAt(a105, 120f, 398f))
        assertEquals(-1, m.ringAt(a105, 207f, 398f))
        val l = npRingsLook(m, 0, false)
        assertFalse(l.rings[1].lit || l.rings[1].focused || l.rings[2].lit || l.rings[2].focused)
    }

    @Test
    fun theSlideLocksAtTheMoveThatLeavesTheSlop() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, 1000)
        assertTrue(m.slideLock(130f))
        assertEquals(0f, m.slideOffset(1050), 0f)
        m.slideDrag(140f, 240f)
        assertEquals(10f, m.slideOffset(1060), 0f)
    }

    @Test
    fun aCommittedSlideWithNoNewTrackSlidesBackAfterTheCap() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, 1000)
        m.slideLock(100f)
        m.slideDrag(40f, 240f)
        m.slideUp(1200, 1, 240f, 1200 * MS_NANOS)
        m.stepTo(1800)
        m.stepTo(1800 + NowPlayingModel.HOLD_CAP)
        assertEquals(null, m.slide.target)
        assertEquals(-240f, m.slideOffset(1800 + NowPlayingModel.HOLD_CAP), 0.001f)
    }

    @Test
    fun aTrackChangeDisarmsTheSlideAndClearsItsNeighbours() {
        val m = NowPlayingModel()
        m.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        m.sync(shown(1), 0)
        m.down(a105, 100f, 200f, 1000)
        m.changed(shown(7), 1100)
        assertEquals(null, m.slide.start)
        assertFalse(m.slideLock(130f))
    }

    @Test
    fun thePhoneTrackKeyIsTheFnvHashOfItsLines() {
        val p = PhoneTrack("Radio", "T", "A", "B", true, false, 0, 0, 0, 1f, false, PhoneRepeat.Off, 0, 0, null)
        assertEquals(1460936240L, NpShown.of(p).track)
    }

    private val radio = PhoneTrack("Radio", "T", "A", "B", true, false, 0, 0, 0, 1f, false, PhoneRepeat.Off, 0, 0, null)

    private fun phoneShown(p: PhoneTrack = radio) = NowPlayingModel().also {
        it.feed(p)
        it.sync(NpShown.of(p), 0)
    }

    @Test
    fun withPhoneLyricsTheMarkTakesATapInTheVariant() {
        val m = phoneShown()
        m.lyricsView = true
        m.mark = true
        m.down(a105, a105.markX / a105.p, a105.markY / a105.p, 10)
        assertNotEquals("premise: with no phone lyrics the variant mark takes no tap", NpAct.Lyrics, m.tap(a105, 20))
        assertFalse(npMarkLook(m, 30).shown)
        m.lyrics.phoneLyrics = true
        m.down(a105, a105.markX / a105.p, a105.markY / a105.p, 40)
        assertEquals(NpAct.Lyrics, m.tap(a105, 50))
        assertTrue(npMarkLook(m, 60).shown)
    }

    private class Acts {
        val shuffles = mutableListOf<Boolean>()
        val repeats = mutableListOf<PhoneRepeat>()

        fun session(p: PhoneTrack) = PhoneSession(p, {}, { shuffles += it }, { repeats += it }) {}
    }

    @Test
    fun aPhoneSessionWithShuffleAndRepeatTakesRingsOneAndTwo() {
        val m = phoneShown()
        m.phoneShuffle = true
        m.phoneRepeat = true
        assertEquals(0, m.ringAt(a105, 30f, 398f))
        assertEquals(1, m.ringAt(a105, 120f, 398f))
        assertEquals(2, m.ringAt(a105, 207f, 398f))
        m.down(a105, 30f, 398f, 10)
        assertEquals(NpAct.Toggle, m.tap(a105, 20))
        m.down(a105, 120f, 398f, 30)
        assertEquals(NpAct.Shuffle, m.tap(a105, 40))
        m.down(a105, 207f, 398f, 50)
        assertEquals(NpAct.Repeat, m.tap(a105, 60))
        val a = Acts()
        val toast = Toast()
        for (r in PhoneRepeat.entries) {
            val p = radio.copy(shuffle = r == PhoneRepeat.All, repeat = r)
            m.feed(p)
            runBlocking {
                npAct(NpAct.Shuffle, m, null, a.session(p), toast) { 0L }
                npAct(NpAct.Repeat, m, null, a.session(p), toast) { 0L }
            }
        }
        assertEquals(listOf(true, false, true), a.shuffles)
        assertEquals("repeat cycles off, all, one, off", listOf(PhoneRepeat.All, PhoneRepeat.One, PhoneRepeat.Off), a.repeats)
        assertEquals(PhoneRepeat.All, nextPhoneRepeat(PhoneRepeat.Off))
    }

    @Test
    fun aPhoneRingWithAnActDrawsLitByItsStateAndTakesFocus() {
        val m = phoneShown(radio.copy(shuffle = true, repeat = PhoneRepeat.All))
        m.phoneShuffle = true
        m.phoneRepeat = true
        val l = npRingsLook(m, 0, false)
        assertTrue("shuffle on draws lit", l.rings[1].lit)
        assertTrue("repeat all draws lit", l.rings[2].lit)
        for (i in 1..2) {
            val x = if (i == 1) 120f else 207f
            m.down(a105, x, 398f, i * 100L)
            m.tap(a105, i * 100L + 10)
            assertTrue("ring $i takes focus", npRingsLook(m, i * 100L + 20, false).rings[i].focused)
        }
    }

    @Test
    fun aFeedEdgeLandsTheShadeWithNoPhoneLyrics() {
        val one = fxInt(1)
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.lyrics.toggle(0)
        m.lyrics.land()
        assertEquals("premise: the own mode shows", one, m.lyrics.dim(1000))
        m.feed(radio)
        assertEquals("a phone feed lands the shade off", 0L, m.lyrics.dim(1000))
        m.feed(PlayerState())
        assertEquals("the own state lands it on", one, m.lyrics.dim(1000))
        m.feed(radio)
        assertEquals(0L, m.lyrics.dim(1000))
        m.clear()
        assertEquals("a clear lands it on the own mode", one, m.lyrics.dim(1000))
    }

    private val lrc = "[00:01.00]one\n[00:02.00]two\n[00:03.00]three"

    @Test
    fun aLineWakeAtSpeedTwoComesAtHalfTheGap() {
        val p = radio.copy(durationMs = DURATION, positionMs = 1400, speed = 2f)
        val m = phoneShown(p)
        m.lyrics.phoneLyrics = true
        val id = requireNotNull(m.shown.track)
        m.lyrics.begin(id)
        m.lyrics.load(id, lrc, 0)
        m.lyrics.toggle(0, variant = true)
        val at = lyricAt(m, 0, 0)
        assertEquals(200, at.speedPct)
        assertEquals("a gap of 600 ms at speed 2 wakes 300 ms on", 300L, m.lyrics.due(at, 0, 0))
    }

    @Test
    fun aPhoneSpeedThatIsNoNumberWakesAtTheNormalGap() {
        val p = radio.copy(durationMs = DURATION, positionMs = 1400, speed = Float.NaN)
        val m = phoneShown(p)
        m.lyrics.phoneLyrics = true
        val id = requireNotNull(m.shown.track)
        m.lyrics.begin(id)
        m.lyrics.load(id, lrc, 0)
        m.lyrics.toggle(0, variant = true)
        val at = lyricAt(m, 0, 0)
        assertEquals("a speed that is no number reads as the normal rate", 100, at.speedPct)
        assertEquals(600L, m.lyrics.due(at, 0, 0))
        assertEquals("the position runs at the normal rate too", 2400L, p.position(1000))
        assertThrows(IllegalArgumentException::class.java) { LyricAt(id, id, 1400, true, true, 0) }
    }

    @Test
    fun anOwnAnchorAtSpeedTwoWakesAtHalfTheGap() {
        val m = NowPlayingModel()
        m.feed(PlayerState(track = 1, playing = true, anchor = Anchor(1400L * RATE / 1000, RATE, 0, true, DURATION, 200)))
        m.sync(shown(1), 0)
        m.lyrics.begin(1)
        m.lyrics.load(1, lrc, 0)
        m.lyrics.toggle(0)
        val at = lyricAt(m, 0, 0)
        assertEquals(1400L, at.pos)
        assertEquals(200, at.speedPct)
        assertEquals("a gap of 600 ms at speed 200 wakes 300 ms on", 300L, m.lyrics.due(at, 0, 0))
    }

    @Test
    fun thePhoneTrackShowsItsCountAndApp() {
        val p = PhoneTrack("Radio", "T", "A", "B", true, false, 0, 0, 0, 1f, false, PhoneRepeat.Off, 3, 12, null)
        val s = NpShown.of(p)
        assertEquals("3 of 12", s.next)
        assertEquals("from Radio", s.from)
        assertEquals("", NpShown.of(p.copy(track = 0)).next)
    }

    @Test
    fun aBlankAppDrawsNoSourceLine() {
        val p = PhoneTrack("", "T", "A", "B", true, false, 0, 0, 0, 1f, false, PhoneRepeat.Off, 3, 12, null)
        assertEquals("", NpShown.of(p).from)
        assertEquals("", NpShown.of(p.copy(app = " ")).from)
        assertEquals("T", NpShown.of(p).title)
    }

    @Test
    fun aHoldOnTheTitleIsTheInfoActAndARingHoldIsRefusedSoItsUpActs() {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.down(a105, 100f, 330f, 10)
        assertTrue(m.titleFocus)
        assertTrue(m.hold(a105))
        assertFalse(m.titleFocus)
        assertEquals(NpAct.None, m.tap(a105, 510))
        m.down(a105, 30f, 398f, 600)
        assertFalse(m.hold(a105))
        assertEquals(NpAct.Shuffle, m.tap(a105, 1100))
        m.feed(PhoneTrack("Radio", "T", "A", "B", true, false, 180000, 10000, 500000, 1f, false, PhoneRepeat.Off, 0, 0, null))
        m.down(a105, 100f, 330f, 1200)
        assertFalse(m.hold(a105))
    }

    private fun held(k: Int): NowPlayingModel {
        val m = NowPlayingModel()
        m.sync(shown(1), 0)
        m.press[k].down(0f)
        assertFalse("premise: the press has stopped moving", m.press[k].moving(HELD_AT.toFloat()))
        assertTrue("premise: the press is stale by its shown state", m.press[k].stale(HELD_AT.toFloat()))
        return m
    }

    @Test
    fun theRingsLookLeavesThePressShownStateToTheFrameLoop() {
        val m = held(1)
        val a = npRingsLook(m, HELD_AT, false)
        val b = npRingsLook(m, HELD_AT, false)
        assertTrue(m.press[1].stale(HELD_AT.toFloat()))
        m.press[1].draw(HELD_AT.toFloat())
        assertEquals(a, b)
        assertEquals(npRingsLook(m, HELD_AT, false), a)
    }

    @Test
    fun theBackLookLeavesThePressShownStateToTheFrameLoop() {
        val m = held(NP_BACK)
        val a = npBackLook(m, HELD_AT, false)
        val b = npBackLook(m, HELD_AT, false)
        assertTrue(m.press[NP_BACK].stale(HELD_AT.toFloat()))
        m.press[NP_BACK].draw(HELD_AT.toFloat())
        assertEquals(a, b)
        assertEquals(npBackLook(m, HELD_AT, false), a)
    }

    @Test
    fun theMarkLookLeavesThePressShownStateToTheFrameLoop() {
        val m = held(NP_MARK)
        val a = npMarkLook(m, HELD_AT)
        val b = npMarkLook(m, HELD_AT)
        assertTrue(m.press[NP_MARK].stale(HELD_AT.toFloat()))
        m.press[NP_MARK].draw(HELD_AT.toFloat())
        assertEquals(a, b)
        assertEquals(npMarkLook(m, HELD_AT), a)
    }

    private fun lyrics(view: Boolean): NowPlayingModel = NowPlayingModel().also {
        it.feed(PlayerState(queueSize = 4, index = 0, track = 1, playing = true))
        it.sync(shown(1), 0)
        it.lyricsView = view
        it.lyrics.toggle(0)
        it.lyrics.land()
    }

    @Test
    fun inLyricsModeATapOffTheRingsAsksForTheLyricsSheet() {
        val m = lyrics(true)
        assertTrue(m.lyricsShown())
        m.down(a105, 80f, 200f, 10)
        assertFalse(m.slide.armed)
        assertEquals(NpAct.LyricsSheet, m.tap(a105, 20))
        m.down(a105, 100f, 330f, 30)
        assertFalse(m.titleFocus)
        assertFalse(m.hold(a105))
        assertEquals(NpAct.LyricsSheet, m.tap(a105, 40))
        m.down(a105, 30f, 398f, 50)
        assertEquals(NpAct.Shuffle, m.tap(a105, 60))
        m.down(a105, 10f, 10f, 70)
        assertEquals(NpAct.Back, m.tap(a105, 80))
    }

    @Test
    fun inLyricsModeTheHiddenMarkTakesNoTap() {
        val m = lyrics(true)
        m.mark = true
        m.down(a105, a105.markX / a105.p, a105.markY / a105.p, 10)
        assertEquals(NpAct.LyricsSheet, m.tap(a105, 20))
    }

    @Test
    fun inLyricsModeTheTimeBandDoesNotSeek() {
        val m = lyrics(true)
        m.down(a105, 14.1f + 152.7f / 4, 300f, 0)
        assertFalse(m.seeking)
    }

    @Test
    fun withoutTheLyricsViewTheLyricsModeChangesNoTap() {
        val m = lyrics(false)
        assertFalse(m.lyricsShown())
        m.down(a105, 80f, 200f, 10)
        assertEquals(NpAct.Controls, m.tap(a105, 20))
    }

    @Test
    fun lyricsShownIsSnapshotStateForTheApp() = runBlocking {
        val m = NowPlayingModel()
        val seen = mutableListOf<Boolean>()
        val watch = launch(start = CoroutineStart.UNDISPATCHED) { snapshotFlow { m.lyricsShown() }.collect { seen += it } }
        for (step in listOf({ m.lyricsView = true }, { m.lyrics.toggle(0) }, { m.lyricsView = false })) {
            step()
            Snapshot.sendApplyNotifications()
            repeat(2) { yield() }
        }
        watch.cancel()
        assertEquals(listOf(false, true, false), seen)
    }

    private class Text(private val text: String?) : Lyrics {
        var offsets = 0

        override val fetch: StateFlow<Boolean> = MutableStateFlow(false)

        override val revision: StateFlow<Long> = MutableStateFlow(0L)

        override suspend fun lyrics(track: Long): String? = text

        override suspend fun offsetMs(track: Long): Int {
            offsets++
            return 0
        }

        override suspend fun setOffset(track: Long, ms: Int) {}

        override suspend fun setFetch(on: Boolean) {}
    }

    @Test
    fun withNoLyricsViewTheMarkReadsTheTextAndLoadsNoLyrics() = runBlocking {
        val m = NowPlayingModel()
        val l = Text("")
        assertTrue(npMark(m, l, 1))
        assertFalse(npMark(m, Text(null), 1))
        assertNull(m.lyrics.track)
        assertEquals(0, l.offsets)
    }

    @Test
    fun withTheLyricsViewTheMarkNeedsLyricLines() = runBlocking {
        val m = NowPlayingModel().also {
            it.lyricsView = true
            it.workContext = Dispatchers.Unconfined
        }
        assertFalse(npMark(m, Text(""), 1))
        assertTrue(npMark(m, Text("[00:01.00]one"), 1))
        assertEquals(1, m.lyrics.lines.size)
    }
}
