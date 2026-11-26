package net.seilent.funekit.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import net.seilent.funekit.api.engine.Crossfeed
import net.seilent.funekit.api.engine.Dac
import net.seilent.funekit.api.engine.EQ_CUSTOM
import net.seilent.funekit.api.engine.EQ_OFF
import net.seilent.funekit.api.engine.EqAnswer
import net.seilent.funekit.api.engine.EqFilter
import net.seilent.funekit.api.engine.EqFilterType
import net.seilent.funekit.api.engine.EqLevels
import net.seilent.funekit.api.engine.EqView
import net.seilent.funekit.api.engine.Output
import net.seilent.funekit.api.engine.ReplayGain
import net.seilent.funekit.api.engine.Tube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class EngineSubject(
    val settings: Settings,
    val brokenTrack: Long,
    val resampledTrack: Long,
    val plainTrack: Long,
    val loseOutput: suspend () -> Unit,
    val routeTimeout: suspend () -> Unit,
    val xrun: suspend () -> Unit,
    val attachDac: suspend (Dac) -> Unit,
    val usbVolume: Boolean = true,
    val bitPerfect: Boolean = true,
    val detachDac: (suspend () -> Unit)? = null,
    val attachDopDac: (suspend (Dac) -> Unit)? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ApiContract : SeamContract() {
    private fun engine(s: Subject): EngineSubject = checkNotNull(s.engine) { "an ApiContract subject passes its engine" }

    private suspend fun routed(s: Subject, track: Long): PlayerState = s.until(s.player.state) { it.playing && it.track == track && it.input != null }

    private fun eqOf(s: Subject): Eq = checkNotNull(s.eq) { "an ApiContract subject passes its eq" }

    private suspend fun imports(s: Subject, name: String): EqView {
        val r = eqOf(s).import(name, AUTOEQ)
        assertEquals(EqAnswer.Done, r.answer)
        assertEquals(name, r.name)
        return s.until(eqOf(s).view) { it.key == name }
    }

    private fun still(v: EqView): EqView = v.copy(levels = EqLevels())

    private suspend fun custom(s: Subject, preamp10: Int): EqView {
        assertEquals(EqAnswer.Done, eqOf(s).pick(EQ_CUSTOM).answer)
        assertEquals(EqAnswer.Done, eqOf(s).preamp(preamp10).answer)
        return s.until(eqOf(s).view) { it.key == EQ_CUSTOM && it.shown?.preamp10 == preamp10 }
    }

    private suspend fun firstSong(view: LibraryView, folder: Long): Long {
        val row = view.page(Listing.Folder(folder), 0, 1).rows.first()
        return if (row.kind == Kind.Song) row.id else firstSong(view, row.id)
    }

    @Test
    fun settingsRoundTripAndVolume() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val now = e.settings.sound.value
        val want = now.copy(
            gapless = !now.gapless,
            crossfadeMs = now.crossfadeMs + 2_000,
            replayGain = ReplayGain.Album,
            preampDb = -3f,
            crossfeed = Crossfeed.ChuMoy,
            width = 150,
            tube = Tube.Low,
        )
        e.settings.update(want)
        assertEquals(want, s.until(e.settings.sound) { it.gapless == want.gapless })
        assertEquals(Output.System, want.output)
        assertNull(want.volume)
        e.settings.setVolume(30)
        assertNull(e.settings.sound.value.volume)
        if (e.bitPerfect) {
            e.settings.update(want.copy(output = Output.BitPerfect))
            assertNull(s.until(e.settings.sound) { it.output == Output.BitPerfect }.volume)
            e.settings.setVolume(30)
            assertNull(e.settings.sound.value.volume)
        }
        assumeTrue(e.usbVolume)
        e.attachDac(DAC_A)
        e.settings.update(want.copy(output = Output.Usb))
        val usb = s.until(e.settings.sound) { it.output == Output.Usb }
        assertEquals(DAC_VOLUME, usb.volume)
        e.settings.setVolume(50)
        s.until(e.settings.sound) { it.volume == 50 }
        e.settings.update(usb.copy(tube = Tube.High, volume = 7))
        assertEquals(50, s.until(e.settings.sound) { it.tube == Tube.High }.volume)
        e.settings.update(usb.copy(output = Output.System))
        assertNull(s.until(e.settings.sound) { it.output == Output.System }.volume)
        e.settings.update(usb.copy(output = Output.Usb))
        assertEquals(50, s.until(e.settings.sound) { it.output == Output.Usb }.volume)
        e.attachDac(DAC_B)
        assertEquals(DAC_VOLUME, s.until(e.settings.sound) { it.volume != 50 }.volume)
        e.settings.setVolume(80)
        s.until(e.settings.sound) { it.volume == 80 }
        e.attachDac(DAC_A)
        assertEquals(50, s.until(e.settings.sound) { it.volume != 80 }.volume)
    }

    @Test
    fun offeredOutputsFollowTheDacAttachAndDetach() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val detach = e.detachDac
        assumeTrue(detach != null)
        assertTrue(Output.System in e.settings.outputs.value.offered)
        assertEquals(e.bitPerfect, Output.BitPerfect in e.settings.outputs.value.offered)
        e.attachDac(DAC_A)
        s.until(e.settings.outputs) { Output.Usb in it.offered }
        detach!!()
        val gone = s.until(e.settings.outputs) { Output.Usb !in it.offered }
        assertTrue(Output.System in gone.offered)
        assertNull(gone.dac)
        e.attachDac(DAC_B)
        assertTrue(s.until(e.settings.outputs) { Output.Usb in it.offered }.dac != null)
    }

    @Test
    fun anOutputNotOfferedIsRefusedAndAKeptOneApplies() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val detach = e.detachDac
        assumeTrue(detach != null && e.usbVolume)
        e.attachDac(DAC_A)
        s.until(e.settings.outputs) { Output.Usb in it.offered }
        e.settings.update(e.settings.sound.value.copy(output = Output.Usb))
        val usb = s.until(e.settings.sound) { it.output == Output.Usb }
        detach!!()
        s.until(e.settings.outputs) { Output.Usb !in it.offered }
        e.settings.update(usb.copy(crossfadeMs = 2000))
        assertEquals(Output.Usb, s.until(e.settings.sound) { it.crossfadeMs == 2000 }.output)
        e.settings.update(usb.copy(crossfadeMs = 2000, output = Output.System))
        s.until(e.settings.sound) { it.output == Output.System }
        val refused = runCatching { e.settings.update(usb.copy(crossfadeMs = 0)) }.exceptionOrNull()
        assertTrue("$refused", refused is IllegalArgumentException)
        assertEquals(Output.System, e.settings.sound.value.output)
        assertEquals(2000, e.settings.sound.value.crossfadeMs)
        if (!e.bitPerfect) {
            assertTrue(runCatching { e.settings.update(usb.copy(output = Output.BitPerfect)) }.exceptionOrNull() is IllegalArgumentException)
        }
        e.attachDac(DAC_B)
        s.until(e.settings.outputs) { Output.Usb in it.offered }
        e.settings.update(usb.copy(crossfadeMs = 0))
        s.until(e.settings.sound) { it.output == Output.Usb }
    }

    @Test
    fun dopIsOfferedOnlyOnADacMeetingTheRuleAndKeptPerDac() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val dop = e.attachDopDac
        assumeTrue(dop != null && e.usbVolume)
        val system = e.settings.sound.value
        assertTrue(runCatching { e.settings.setDop(true) }.exceptionOrNull() is IllegalArgumentException)
        assertNull(e.settings.outputs.value.dop)
        e.attachDac(DAC_A)
        e.settings.update(system.copy(output = Output.Usb))
        s.until(e.settings.sound) { it.output == Output.Usb }
        assertTrue(runCatching { e.settings.setDop(true) }.exceptionOrNull() is IllegalArgumentException)
        assertNull(e.settings.outputs.value.dop)
        dop!!(DAC_B)
        assertEquals(false, s.until(e.settings.outputs) { it.dop != null }.dop)
        e.settings.setDop(true)
        s.until(e.settings.outputs) { it.dop == true }
        e.attachDac(DAC_A)
        s.until(e.settings.outputs) { it.dop == null }
        e.attachDac(DAC_B)
        s.until(e.settings.outputs) { it.dop == true }
        e.settings.setDop(false)
        s.until(e.settings.outputs) { it.dop == false }
        e.settings.update(e.settings.sound.value.copy(output = Output.System))
        s.until(e.settings.outputs) { it.dop == null }
    }

    @Test
    fun ownedVolumeStepsByTheFuneOsDetent() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val system = e.settings.sound.value
        assertEquals(Output.System, system.output)
        assertNull(system.volumePct)
        assertNull(system.volumeStepPct)
        e.settings.stepVolume(1)
        e.settings.setVolumePercent(50)
        assertNull(e.settings.sound.value.volume)
        assumeTrue(e.usbVolume)
        e.attachDac(DAC_A)
        e.settings.update(system.copy(output = Output.Usb))
        val usb = s.until(e.settings.sound) { it.output == Output.Usb }
        assertEquals(DAC_VOLUME, usb.volume)
        assertEquals(41, usb.volumePct)
        assertEquals(5, usb.volumeStepPct)
        e.settings.stepVolume(1)
        assertEquals(42, s.until(e.settings.sound) { it.volume == 196 }.volumePct)
        e.settings.stepVolume(1)
        assertEquals(47, s.until(e.settings.sound) { it.volume == 202 }.volumePct)
        e.settings.stepVolume(-1)
        s.until(e.settings.sound) { it.volume == 196 }
        e.settings.stepVolume(-1)
        assertEquals(37, s.until(e.settings.sound) { it.volume == 186 }.volumePct)
        e.settings.setVolumePercent(50)
        assertEquals(50, s.until(e.settings.sound) { it.volume == 205 }.volumePct)
        e.settings.setVolumePercent(100)
        assertEquals(100, s.until(e.settings.sound) { it.volume == 255 }.volumePct)
        e.settings.stepVolume(1)
        assertEquals(255, e.settings.sound.value.volume)
        e.settings.setVolumePercent(0)
        assertEquals(0, s.until(e.settings.sound) { it.volume == 0 }.volumePct)
        e.settings.stepVolume(1)
        assertEquals(5, s.until(e.settings.sound) { it.volume == 122 }.volumePct)
        e.attachDac(DAC_B)
        s.until(e.settings.sound) { it.volume == DAC_VOLUME }
        e.settings.stepVolume(-1)
        assertEquals(41, s.until(e.settings.sound) { it.volume == 194 }.volumePct)
        e.attachDac(DAC_A)
        assertEquals(122, s.until(e.settings.sound) { it.volume != 194 }.volume)
    }

    @Test
    fun resampledOnlyAboveTheCeiling() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val seen = events(s.player)
        s.player.play(listOf(e.plainTrack), 0, 0)
        var st = routed(s, e.plainTrack)
        assertEquals(Output.System, st.route)
        assertFalse(st.resampled)
        assertEquals(st.input, st.output)
        s.player.play(listOf(e.resampledTrack), 0, 0)
        st = routed(s, e.resampledTrack)
        assertTrue(st.resampled)
        val input = checkNotNull(st.input)
        val output = checkNotNull(st.output)
        assertTrue(output.toString(), output.rate == input.rate / 2 || output.rate == input.rate / 4)
        assertEquals(input.copy(rate = output.rate), output)
        assumeTrue(e.bitPerfect)
        e.settings.update(e.settings.sound.value.copy(output = Output.BitPerfect))
        st = s.until(s.player.state) { it.route == Output.BitPerfect }
        assertFalse(st.resampled)
        assertEquals(st.input, st.output)
        s.saw(seen) { it == EngineEvent.RouteChanged(Output.BitPerfect, false) }
        e.settings.update(e.settings.sound.value.copy(output = Output.System))
        assertTrue(s.until(s.player.state) { it.route == Output.System }.resampled)
    }

    @Test
    fun outputLostPausesAndFreezesThePosition() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val seen = events(s.player)
        val ids = songs(s, 2)
        s.player.play(ids, 0, 0)
        routed(s, ids[0])
        advanceTimeBy(1_500L)
        val at = ms(s)
        e.loseOutput()
        s.saw(seen) { it == EngineEvent.OutputLost }
        val lost = s.until(s.player.state) { !it.playing && !it.anchor.running }
        assertEquals(ids[0], lost.track)
        assertEquals(at, ms(s))
        advanceTimeBy(10_000L)
        assertEquals(at, ms(s))
        assertFalse(s.player.state.value.playing)
        s.player.resume()
        s.until(s.player.state) { it.playing && it.anchor.running }
        assertEquals(at, ms(s))
        advanceTimeBy(500L)
        assertEquals(at + 500, ms(s))
    }

    @Test
    fun brokenTrackFailsAndSkips() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val seen = events(s.player)
        val ok = songs(s, 1).single()
        s.player.play(listOf(e.brokenTrack, ok), 0, 0)
        s.saw(seen) { it is EngineEvent.Failed && it.track == e.brokenTrack }
        val st = routed(s, ok)
        assertEquals(1, st.index)
    }

    @Test
    fun routeTimeoutPauses() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val seen = events(s.player)
        val ids = songs(s, 1)
        s.player.play(ids, 0, 0)
        routed(s, ids[0])
        advanceTimeBy(1_000L)
        e.routeTimeout()
        s.saw(seen) { it == EngineEvent.RouteTimeout }
        s.until(s.player.state) { !it.playing }
        val at = ms(s)
        advanceTimeBy(1_000L)
        assertEquals(at, ms(s))
    }

    @Test
    fun xrunsCount() = runTest {
        val s = open(Edge.Main)
        val e = engine(s)
        val seen = events(s.player)
        val x = s.player.state.value.xruns
        e.xrun()
        s.until(s.player.state) { it.xruns == x + 1 }
        s.saw(seen) { it is EngineEvent.Xruns && it.count == x + 1 }
    }

    @Test
    fun idsStayStableAcrossRootChanges() = runTest {
        val s = open(Edge.Main)
        engine(s)
        val before = s.library.view().allRows(Listing.Songs).map { it.id }
        val gone = s.library.roots.value.last()
        val g = s.library.generation.value
        s.library.removeRoot(gone.uri)
        val g2 = s.until(s.library.generation) { it > g }
        val mid = s.library.view().allRows(Listing.Songs).map { it.id }
        assertTrue(mid.size < before.size)
        assertTrue(before.containsAll(mid))
        s.library.addRoot(gone.uri)
        s.until(s.library.generation) { it > g2 }
        assertEquals(before, s.library.view().allRows(Listing.Songs).map { it.id })
    }

    @Test
    fun unavailableRootKeepsItsRecords() = runTest {
        val s = open(Edge.Unavailable)
        engine(s)
        val roots = s.library.roots.value
        assertTrue(roots.any { !it.available })
        val before = s.library.view().allRows(Listing.Songs)
        val seen = mutableListOf<ScanProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.library.scan.toList(seen) }
        s.library.rescan()
        s.until(s.library.scan) { !it.running && seen.any { p -> p.running } }
        assertEquals(before.map { it.id }, s.library.view().allRows(Listing.Songs).map { it.id })
        assertEquals(roots, s.library.roots.value)
    }

    @Test
    fun removingARootDropsItsQueueEntriesAndPins() = runTest {
        val s = open(Edge.Main)
        engine(s)
        val roots = s.library.roots.value
        assertTrue(roots.size >= 2)
        val view = s.library.view()
        val keep = firstSong(view, roots.first().id)
        val gone = firstSong(view, roots.last().id)
        val pins = s.quickplay.available.value
        val r = s.quickplay.revision.value
        if (pins) {
            s.quickplay.pin(Kind.Song, gone)
            s.until(s.quickplay.revision) { it > r }
            assertTrue(view.pins(0, 100).rows.any { it.id == gone })
        }
        s.player.play(listOf(keep, gone), 0, 0)
        routed(s, keep)
        val g = s.library.generation.value
        s.library.removeRoot(roots.last().uri)
        s.until(s.library.generation) { it > g }
        s.until(s.player.state) { it.queueSize == 1 }
        assertEquals(listOf(keep), queue(s))
        if (pins) assertTrue(s.library.view().pins(0, 100).rows.none { it.id == gone })
        assertEquals(roots.dropLast(1), s.until(s.library.roots) { it.size == roots.size - 1 })
    }

    @Test
    fun eqAnImportAddsAPresetAndShowsIt() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        val r = eq.import(ROOM, AUTOEQ)
        assertEquals(EqAnswer.Done, r.answer)
        assertEquals(ROOM, r.name)
        val v = s.until(eq.view) { it.key == ROOM }
        assertEquals(listOf(ROOM), v.presets)
        assertNotNull(v.shown)
    }

    @Test
    fun eqAnImportOfAKnownNameAndCurveReusesItsPreset() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        imports(s, ROOM)
        val again = eq.import(ROOM, AUTOEQ)
        assertEquals(EqAnswer.Done, again.answer)
        assertEquals(ROOM, again.name)
        assertEquals(listOf(ROOM), eq.view.value.presets)
    }

    @Test
    fun eqARefusedImportAddsNothing() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        val before = eq.view.value
        val r = eq.import(ROOM, "nothing here")
        assertEquals(EqAnswer.Refused, r.answer)
        assertTrue(r.log.isNotEmpty())
        assertEquals(still(before), still(eq.view.value))
    }

    @Test
    fun eqOffKeepsTheKeyAndOnShowsItAgain() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        val shown = imports(s, ROOM).shown
        assertEquals(EqAnswer.Done, eq.on(false).answer)
        val off = s.until(eq.view) { !it.on }
        assertEquals(ROOM, off.key)
        assertEquals(shown, off.shown)
        assertEquals(EqAnswer.Done, eq.on(true).answer)
        val on = s.until(eq.view) { it.on }
        assertEquals(ROOM, on.key)
        assertEquals(shown, on.shown)
    }

    @Test
    fun eqTheBandsListWhileEqIsOff() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        imports(s, ROOM)
        assertEquals(EqAnswer.Done, eq.on(false).answer)
        assertNotNull(s.until(eq.view) { !it.on }.shown)
    }

    @Test
    fun eqAnEditOfAPresetMakesCustomFromItsCurve() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        val preset = checkNotNull(imports(s, ROOM).shown)
        assertEquals(EqAnswer.Done, eq.preamp(-40).answer)
        val v = s.until(eq.view) { it.shown?.preamp10 == -40 }
        assertEquals(EQ_CUSTOM, v.key)
        assertEquals(preset.filters, v.shown?.filters)
    }

    @Test
    fun eqAnEditOfAPresetKeepsAnUnsavedCustomAsAPreset() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        custom(s, -20)
        imports(s, ROOM)
        val r = eq.preamp(-40)
        assertEquals(EqAnswer.Done, r.answer)
        assertEquals(PRESET_1, r.name)
        val v = s.until(eq.view) { it.key == EQ_CUSTOM }
        assertTrue(PRESET_1 in v.presets)
        assertEquals(EqAnswer.Done, eq.pick(PRESET_1).answer)
        assertEquals(-20, s.until(eq.view) { it.key == PRESET_1 }.shown?.preamp10)
    }

    @Test
    fun eqSaveTakesTheFirstFreePresetNumberAndShowsIt() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        custom(s, -20)
        val first = eq.save()
        assertEquals(EqAnswer.Done, first.answer)
        assertEquals(PRESET_1, first.name)
        assertEquals(PRESET_1, s.until(eq.view) { PRESET_1 in it.presets }.key)
        assertEquals(EqAnswer.Done, eq.preamp(-30).answer)
        s.until(eq.view) { it.key == EQ_CUSTOM }
        val second = eq.save()
        assertEquals(EqAnswer.Done, second.answer)
        assertEquals(PRESET_2, second.name)
        val v = s.until(eq.view) { PRESET_2 in it.presets }
        assertEquals(PRESET_2, v.key)
        assertEquals(listOf(PRESET_1, PRESET_2), v.presets)
    }

    @Test
    fun eqResetCustomLeavesOneFreshFilter() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        assertEquals(EqAnswer.Done, eq.pick(EQ_CUSTOM).answer)
        val n = checkNotNull(s.until(eq.view) { it.key == EQ_CUSTOM }.shown).filters.size
        assertEquals(EqAnswer.Done, eq.preamp(-20).answer)
        assertEquals(EqAnswer.Done, eq.add(SHELF).answer)
        assertEquals(EqAnswer.Done, eq.add(SHELF).answer)
        s.until(eq.view) { it.shown?.preamp10 == -20 && it.shown?.filters?.size == n + 2 }
        assertEquals(EqAnswer.Done, eq.resetCustom().answer)
        val v = s.until(eq.view) { it.shown?.filters?.size == 1 }
        assertEquals(listOf(FRESH), v.shown?.filters)
        assertEquals(0, v.shown?.preamp10)
    }

    @Test
    fun eqDeletingTheShownPresetTurnsTheKeyOff() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        imports(s, ROOM)
        assertEquals(EqAnswer.Done, eq.delete(ROOM).answer)
        val v = s.until(eq.view) { ROOM !in it.presets }
        assertEquals(EQ_OFF, v.key)
        assertEquals(emptyList<String>(), v.presets)
    }

    @Test
    fun eqAFullStoreAnswersNoSpace() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        val cap = checkNotNull(s.caps.eqCap) { "an ApiContract subject passes its eq cap" }
        val names = (1..cap).map { "$ROOM $it" }
        for (n in names) imports(s, n)
        assertEquals(EqAnswer.NoSpace, eq.import("$ROOM ${cap + 1}", AUTOEQ).answer)
        assertEquals(EqAnswer.Done, eq.pick(EQ_CUSTOM).answer)
        s.until(eq.view) { it.key == EQ_CUSTOM }
        assertEquals(EqAnswer.NoSpace, eq.save().answer)
        assertEquals(names, eq.view.value.presets)
    }

    @Test
    fun eqPresetsListDigitAware() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        imports(s, PRESET_10)
        imports(s, PRESET_2)
        assertEquals(listOf(PRESET_2, PRESET_10), eq.view.value.presets)
    }

    @Test
    fun eqPresetsListInFileNameOrder() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        imports(s, ROOM)
        imports(s, ROOM_2)
        assertEquals(listOf(ROOM_2, ROOM), eq.view.value.presets)
    }

    @Test
    fun eqAnUnchangedValueAnswersDone() = runTest {
        val s = open(Edge.Main)
        val eq = eqOf(s)
        val v = imports(s, ROOM)
        val r = eq.preamp(checkNotNull(v.shown).preamp10)
        assertEquals(EqAnswer.Done, r.answer)
        assertNull(r.name)
        assertEquals(still(v), still(eq.view.value))
    }

    @Test
    fun scrobbleSwitchesShowInTheView() = runTest {
        val s = open(Edge.Main)
        val scrobbling = checkNotNull(s.scrobbling)
        scrobbling.enable(ScrobbleService.LastFm, true)
        s.until(scrobbling.view) { it.lastFm }
        scrobbling.enable(ScrobbleService.ListenBrainz, true)
        s.until(scrobbling.view) { it.listenBrainz }
        scrobbling.enable(ScrobbleService.LastFm, false)
        assertTrue(s.until(scrobbling.view) { !it.lastFm }.listenBrainz)
    }

    private companion object {
        const val DAC_VOLUME = 195
        val DAC_A = Dac(1, 1, "a")
        val DAC_B = Dac(1, 2, "b")
        const val ROOM = "Room One"
        const val ROOM_2 = "Room One 2"
        const val AUTOEQ = "Preamp: -3 dB\nFilter 1: ON PK Fc 1000 Hz Gain 2 dB Q 1.41"
        const val PRESET_1 = "Preset 1"
        const val PRESET_2 = "Preset 2"
        const val PRESET_10 = "Preset 10"
        val FRESH = EqFilter(EqFilterType.Peak, 1_000_000, 0, 140)
        val SHELF = EqFilter(EqFilterType.LowShelf, 105_000, -24, 70)
    }
}
