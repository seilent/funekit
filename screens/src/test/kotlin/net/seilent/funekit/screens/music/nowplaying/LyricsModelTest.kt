package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.api.LyricCopy
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.fx
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.VariantMotion
import net.seilent.funekit.kit.touch.RowSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

private const val DURATION = 180000L

class LyricsModelTest {
    private val text = "[00:01.00]one\n[00:02.00]two\n[00:03.00]three"

    private fun at(pos: Long, track: Long = 1, running: Boolean = true) = LyricAt(track, track, pos, false, running)

    private fun loaded(offset: Int = 0) = LyricsModel().also {
        it.begin(1)
        it.load(1, text, offset)
    }

    @Test
    fun theOffsetMovesEveryLineEarlierAndTheLineIsTheLastOneStarted() {
        val m = loaded(500)
        assertEquals(listOf(500L, 1500L, 2500L), m.lines.map { it.ms })
        assertEquals(1, m.index(at(1600)))
        assertEquals(LYRIC_TITLE, m.index(at(400)))
        assertEquals(LYRIC_NONE, m.index(LyricAt(1, 2, 1600, false, true)))
        assertEquals(LYRIC_NONE, m.index(LyricAt(1, 1, 1600, true, true)))
    }

    @Test
    fun anAnswerForAnOlderAskIsDropped() {
        val m = LyricsModel()
        m.begin(1)
        m.begin(2)
        m.load(1, text, 0)
        assertTrue(m.pending)
        assertEquals(LYRIC_TITLE, m.index(at(1600, track = 2)))
        assertEquals(LyricView.Blank, m.view(1000, at(1600, track = 2)))
    }

    private class Served(private val copy: LyricCopy, private val off: Int) : Lyrics {
        override val fetch = MutableStateFlow(false)

        override val revision = MutableStateFlow(0L)

        override suspend fun lyrics(track: Long): String = copy.text

        override suspend fun copy(track: Long): LyricCopy = copy

        override suspend fun offsetMs(track: Long): Int = off

        override suspend fun setOffset(track: Long, ms: Int) {}

        override suspend fun setFetch(on: Boolean) {}
    }

    private fun served(copy: LyricCopy, off: Int = 0) = LyricsModel().also { runBlocking { it.read(Served(copy, off), 1, EmptyCoroutineContext) } }

    private val carried = LyricCopy("[00:00.50]one\n[00:01.50]two\n[00:02.50]three", 500)

    @Test
    fun aCarriedCopyKeepsItsServedLinesAndShowsItsOffset() {
        val m = served(carried)
        assertEquals(listOf(500L, 1500L, 2500L), m.lines.map { it.ms })
        assertEquals(500, m.offsetMs)
    }

    @Test
    fun aRetimeMovesACarriedCopyByTheDifferenceAndOnlyForItsTrack() {
        val m = served(carried)
        m.retime(1, 800)
        assertEquals(listOf(200L, 1200L, 2200L), m.lines.map { it.ms })
        m.retime(1, 200)
        assertEquals(listOf(800L, 1800L, 2800L), m.lines.map { it.ms })
        m.retime(2, 0)
        assertEquals("a retime of another track moves nothing", listOf(800L, 1800L, 2800L), m.lines.map { it.ms })
        assertEquals(200, m.offsetMs)
    }

    @Test
    fun aCopyWithNoCarriedOffsetIsShiftedByTheKit() {
        val m = served(LyricCopy(text, null), 500)
        assertEquals(listOf(500L, 1500L, 2500L), m.lines.map { it.ms })
        assertEquals(500, m.offsetMs)
        m.retime(1, 0)
        assertEquals(listOf(1000L, 2000L, 3000L), m.lines.map { it.ms })
    }

    @Test
    fun aReReadOfTheSameCopyAtTheSameOffsetLeavesTheModel() {
        val m = loaded()
        m.view(10_000, at(1100))
        m.view(10_016, at(2050))
        assertTrue("premise: a slide runs", m.sliding(10_016))
        val gen = m.gen
        m.load(1, text, 0)
        assertTrue("the slide runs on", m.sliding(10_032))
        assertEquals("the copy keeps its generation", gen, m.gen)
    }

    @Test
    fun theNextLineSlidesInWhenItArrivesInTime() {
        val m = loaded()
        m.view(10_000, at(1100))
        val v = m.view(10_016, at(2050))
        assertEquals(LyricView.Column(0, 1, v.let { (it as LyricView.Column).p }), v)
        assertTrue(m.sliding(10_016))
        assertEquals(10_016 - 50L, m.slideAt)
        assertEquals(Tok.motion_lyricSlide.toLong(), m.slideD)
        assertFalse(m.sliding(10_016 - 50 + Tok.motion_lyricSlide.toLong()))
    }

    @Test
    fun aLineReachedTooLateOrAfterAJumpLandsAtOnce() {
        val late = loaded()
        late.view(10_000, at(1100))
        late.view(10_016, at(2300))
        assertFalse(late.sliding(10_016))
        val jump = loaded()
        jump.view(10_000, at(1100))
        assertEquals(LyricView.Column(2, 2, fxInt(1)), jump.view(10_016, at(3050)))
    }

    @Test
    fun newLyricsForTheSameTrackLandWithoutASlide() {
        val m = loaded()
        m.view(10_000, at(1100))
        m.load(1, "[00:01.00]uno\n[00:02.00]dos", 0)
        m.view(10_016, at(2050))
        assertFalse(m.sliding(10_016))
        assertEquals(1, m.lineShown)
    }

    @Test
    fun theViewWakesAtTheNextLineAndAMissedWakeStillLandsSoon() {
        val m = loaded()
        m.toggle(0)
        assertEquals(10_600L, m.due(at(1400), 10_000, 10_000))
        assertEquals(10_625L, m.due(at(1400), 10_000, 10_610))
        assertEquals(-1L, m.due(at(1400), 10_000, 10_700))
        assertEquals(-1L, m.due(at(1400, running = false), 10_000, 10_000))
        assertEquals(-1L, m.due(at(3100), 10_000, 10_000))
    }

    @Test
    fun theNextDueIsSetAtEachRestAndAtTheLineMove() {
        val m = loaded()
        m.toggle(0)
        assertEquals(-1L, m.nextDue)
        assertEquals(10_600L, m.rest(at(1400), 10_000))
        assertEquals(10_600L, m.nextDue)
        m.view(10_600, at(2000))
        assertEquals(11_600L, m.nextDue)
        m.rest(at(2000, running = false), 10_700)
        assertEquals(-1L, m.nextDue)
    }

    @Test
    fun theSlideEndIsSetWhenASlideStartsAndClearedWhenItEndsOrIsCut() {
        val m = loaded()
        m.view(10_000, at(1100))
        m.view(10_016, at(2050))
        val end = 10_016 - 50 + Tok.motion_lyricSlide.toLong()
        assertEquals(end, m.slideEnd)
        m.view(end, at(2900))
        assertEquals("premise: still line 1", 1, m.lineShown)
        assertEquals("the slide's end clears it", -1L, m.slideEnd)
        m.view(end + 100, at(3050))
        assertTrue("premise: a new slide", m.slideEnd >= 0)
        m.retime(1, 300)
        assertEquals("a retime cuts the slide", -1L, m.slideEnd)
    }

    @Test
    fun theLyricsFadeRunsTheVariantFadeOutThenInAndAReverseRunsItBack() {
        val f = Tok.motion_variantFade.toLong()
        val m = loaded()
        m.toggle(1000)
        assertTrue(m.mode)
        assertEquals(VariantMotion.frame(VariantMotion.Dir.Out, f / 2), m.fade(1000 + f / 2))
        assertEquals(VariantMotion.frame(VariantMotion.Dir.Out, f + f / 2), m.fade(1000 + f + f / 2))
        m.stepTo(1000 + 2 * f)
        assertNull(m.fade(1000 + 2 * f))
        val r = loaded()
        r.toggle(1000)
        val dim = r.dim(1100)
        r.reverse(1200)
        assertFalse(r.mode)
        assertEquals(VariantMotion.frame(VariantMotion.Dir.Out, 100), r.fade(1300))
        assertEquals(dim, r.dim(1300))
        assertFalse(r.fading(1400))
    }

    @Test
    fun theShadeEasesInOverOneVariantFade() {
        val m = loaded()
        m.toggle(1000)
        assertEquals(0L, m.dim(1000))
        assertTrue(m.dim(1000 + Tok.motion_variantFade.toLong() / 2) in 1 until fxInt(1))
        assertEquals(fxInt(1), m.dim(1000 + Tok.motion_variantFade.toLong()))
        m.toggle(2000)
        assertEquals(0L, m.dim(2000 + Tok.motion_variantFade.toLong()))
    }

    @Test
    fun theShadeFollowsTheDecelCurveAndMovesOnlyWhileItRuns() {
        val f = Tok.motion_variantFade.toLong()
        val m = loaded()
        m.toggle(1000)
        assertEquals(fxMul(fxInt(1), fx(Curves.decel.at(0.5f))), m.dim(1000 + f / 2))
        assertTrue(m.dimming(1000 + f / 2))
        assertFalse(m.dimming(1000 + f))
    }

    @Test
    fun aToggleDuringThePhoneVariantLeavesTheShadeOff() {
        val m = loaded()
        m.toggle(1000, variant = true)
        assertTrue(m.mode)
        assertEquals(0L, m.dim(1000 + Tok.motion_variantFade.toLong()))
        m.land(variant = false)
        assertEquals(fxInt(1), m.dim(5000))
    }

    @Test
    fun noLineWakeWhileTheLyricsAreHiddenOrHaveNoLine() {
        val m = loaded()
        assertEquals(-1L, m.due(at(1400), 10_000, 10_000))
        m.toggle(0)
        assertEquals(-1L, m.due(LyricAt(1, 1, 1400, true, true), 10_000, 10_000))
        assertEquals(-1L, m.due(LyricAt(1, 2, 1400, false, true), 10_000, 10_000))
    }

    @Test
    fun theScrollThumbHoldWakesThePlainView() {
        val m = LyricsModel()
        m.begin(1)
        m.load(1, "first\nsecond", 0)
        assertEquals(-1L, m.wakeAt(at(0), 1000))
        m.plainFade.track(true, false, 1000)
        val end = 1000 + Tok.scrollbar_hold.toLong()
        assertEquals(end, m.wakeAt(at(0), 1000))
        assertTrue(m.moving(end + 10))
        val seen = m.revision
        m.scrolled()
        assertTrue(m.revision != seen)
    }

    @Test
    fun thePlainRowsSetTheScrollTrack() {
        val m = LyricsModel()
        m.plainRows(RowSpec(10, 50), 12)
        assertEquals(12, m.plainTrack.count)
        assertEquals(70, m.plainTrack.max)
    }

    private class Copy(private val text: String?, private val off: Int = 0) : PhoneLyrics {
        override val mode = MutableStateFlow(false)

        override fun setMode(on: Boolean) {
            mode.value = on
        }

        override val revision = MutableStateFlow(0L)

        override suspend fun lyrics(t: PhoneTrack): String? = text

        override fun offsetMs(t: PhoneTrack): Int = off
    }

    private val phone = PhoneTrack("Radio", "T", "A", "B", true, false, DURATION, 1400, 0, 1f, false, PhoneRepeat.Off, 0, 0, null)

    private fun phoneModel(copy: Copy): NowPlayingModel = NowPlayingModel().also { np ->
        np.lyrics.phoneLyrics = true
        np.feed(phone)
        val shown = NpShown.of(phone)
        np.sync(shown, 0)
        runBlocking { np.lyrics.readPhone(copy, phone, requireNotNull(shown.track), EmptyCoroutineContext) }
    }

    @Test
    fun withNoPhoneLyricsTheVariantShowsNoLyricsAsBefore() {
        val m = loaded()
        m.toggle(0)
        assertTrue("premise: the own source shows its lyrics", m.shown(false))
        assertFalse(m.shown(true))
        assertFalse(m.on(true) && m.phoneLyrics)
        assertEquals(LYRIC_NONE, m.index(LyricAt(1, 1, 1600, true, true)))
        assertEquals(-1L, m.due(LyricAt(1, 1, 1400, true, true), 10_000, 10_000))
    }

    @Test
    fun aPhoneCopyIndexesAndWakesOnThePhoneTrack() {
        val np = phoneModel(Copy(text))
        val m = np.lyrics
        m.toggle(0, variant = true)
        val id = np.shown.track
        val at = lyricAt(np, 0, 0)
        assertEquals("the copy is keyed on the shown id", id, m.track)
        assertEquals(id, at.shown)
        assertEquals("the variant plays its shown id", id, at.playing)
        assertTrue("the phone runs", at.running)
        assertEquals(1400L, at.pos)
        assertEquals(0, m.index(at))
        assertEquals("the wake is at the next line", 600L, m.due(at, 0, 0))
    }

    @Test
    fun thePhoneModeAndTheOwnModeAreApart() {
        val m = loaded()
        m.phoneLyrics = true
        m.toggle(0, variant = true)
        assertTrue(m.phoneMode)
        assertFalse("a variant toggle leaves the own mode", m.mode)
        assertTrue(m.shown(true))
        assertFalse("the own source reads its own mode", m.shown(false))
        m.toggle(1000, variant = false)
        assertTrue(m.mode)
        assertTrue("an own toggle leaves the phone mode", m.phoneMode)
    }

    @Test
    fun aSecondTapDuringTheFadeInTheVariantReversesThePhoneMode() {
        val m = loaded()
        m.phoneLyrics = true
        m.toggle(1000, variant = true)
        assertTrue("premise: the fade runs", m.fading(1100))
        m.reverse(1100, variant = true)
        assertFalse(m.phoneMode)
        assertFalse("the reverse leaves the own mode", m.mode)
        assertEquals(VariantMotion.frame(VariantMotion.Dir.Out, 100), m.fade(1100))
    }

    @Test
    fun aPhoneCopyAppliesItsOffsetMs() {
        val m = phoneModel(Copy(text, 500)).lyrics
        assertEquals(500, m.offsetMs)
        assertEquals(listOf(500L, 1500L, 2500L), m.lines.map { it.ms })
    }

    @Test
    fun plainLyricsShowWhenThereAreNoTimedLines() {
        val m = LyricsModel()
        m.begin(1)
        m.load(1, "first\n\nsecond", 0)
        assertEquals(listOf("first", "", "second"), m.plain)
        assertEquals(LyricView.Plain, m.view(1000, at(0)))
    }
}
