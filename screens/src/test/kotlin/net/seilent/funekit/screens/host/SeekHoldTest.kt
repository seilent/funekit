package net.seilent.funekit.screens.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Disconnected
import net.seilent.funekit.api.NoSpace
import net.seilent.funekit.api.EngineEvent
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Refused
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekHoldTest {
    private class HoldPlayer(base: Player) : Player by base {
        override val events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8)
        val seeks = mutableListOf<Long>()
        var nexts = 0
        var seekFail: Exception? = null
        var nextFail: Exception? = null
        var slow: CompletableDeferred<Unit>? = null

        override suspend fun seek(ms: Long) {
            seeks += ms
            slow?.await()
            seekFail?.let { throw it }
        }

        override suspend fun next() {
            nexts++
            nextFail?.let { throw it }
        }
    }

    private val scheduler = TestCoroutineScheduler()
    private val thrown = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler) + CoroutineExceptionHandler { _, e -> thrown += e })
    private val hud = VolumeHud().apply { show(0, 50, true, "artist", "title") }
    private val logs = mutableListOf<String>()
    private var clock = 0L
    private val hold = SeekHold(scope, hud, { logs += it }, { clock }) { _, _, _ -> }
    private val p = HoldPlayer(testFake().player)
    private val ok = Refuse(false, Card.Present)

    private fun state(track: Long? = 1L, index: Int = 0, size: Int = 3, playing: Boolean = true, dur: Long = DUR, service: Long? = null, repeat: Repeat = Repeat.Off) =
        PlayerState(queueSize = size, index = index, track = track, playing = playing, anchor = Anchor(0, 0, 0, false, dur), service = service, repeat = repeat)

    private fun start(st: PlayerState = state(), pos: Long = POS, h: Hold = Hold.Glyph, dir: Int = 1, from: Refuse = ok, held: () -> Boolean = { true }): Boolean =
        hold.start(dir, h, 1000, p, st, pos, from, held) { false }.also { scheduler.runCurrent() }

    private fun tick(at: Long, st: PlayerState = state(), pos: Long = POS, variant: Boolean = false): Boolean {
        clock = at
        return hold.tick(at, st, pos, variant, true).also { scheduler.runCurrent() }
    }

    private fun emit(e: EngineEvent) {
        p.events.tryEmit(e)
        scheduler.runCurrent()
    }

    private fun toTheRunIn(st: PlayerState = state()) {
        assertTrue("premise: the hold starts", start(st, NEAR_END))
        tick(1000, st, NEAR_END)
        assertEquals("premise: 6:16, the end margin", "6:16", hud.seekLeft)
        tick(1048, st, NEAR_END)
    }

    @Test
    fun aHoldWithNoTrackStartsNothing() {
        assertFalse("no track", start(state(track = null)))
        assertFalse("an empty queue", start(state(size = 0)))
        assertFalse("the phone's track", start(from = Refuse(true, Card.Present)))
        assertFalse("no card queue", start(from = Refuse(false, Card.Out)))
        assertFalse("a glyph hold back with no duration (funeOS player/ui/app.cpp App::vol_glyph_hold :14384-14391, v0.4.3)", start(state(dur = 0), dir = -1))
        assertFalse("a key hold forward with no duration (funeOS player/ui/app.cpp App::seek_start :14393-14402, v0.4.3)", start(state(dur = 0), h = Hold.Key))
        assertTrue("a key hold back with no duration", start(state(dur = 0), h = Hold.Key, dir = -1))
    }

    @Test
    fun aTrackChangedElsewhereMidHoldEndsIt() {
        start()
        tick(1000)
        assertTrue("an index move alone keeps the hold", tick(1048, state(index = 2)))
        assertTrue(hold.on)
        tick(1096, state(track = 2L, index = 1))
        assertFalse("funeOS player/ui/app.cpp App::seek_tick :14428-14432, v0.4.3", hold.on)
    }

    @Test
    fun anEndedForTheHeldTrackEndsTheHoldAndNoOther() {
        start()
        tick(1000)
        emit(EngineEvent.Ended(9L))
        assertTrue("an end for another track keeps the hold", tick(1048))
        emit(EngineEvent.Ended(1L))
        tick(1096)
        assertFalse("funeOS player/ui/app.cpp App::seek_edge :14518-14528, v0.4.3", hold.on)
    }

    @Test
    fun aHoldPastTheEndRunsIntoTheNextSong() {
        toTheRunIn()
        assertEquals("the crossing step asks the next song", 1, p.nexts)
        assertTrue(hold.on)
        tick(1100, state(track = 2L, index = 1, dur = 300000), 0)
        assertEquals("the overshoot carried into the next song", "0:02", hud.seekLeft)
        assertEquals("-4:58", hud.seekRight)
        assertTrue(hold.on)
    }

    @Test
    fun theRampsOwnRunInWaitsForItsStart() {
        val last = state(index = 2, repeat = Repeat.All)
        toTheRunIn(last)
        tick(1100, last, NEAR_END)
        assertEquals("a state with no start keeps the wait", "6:16", hud.seekLeft)
        emit(EngineEvent.Started(7L))
        tick(1120, state(track = 7L, index = 0, repeat = Repeat.All, dur = 300000), 0)
        assertEquals("the started play lands the ramp", "0:02", hud.seekLeft)
        hold.end(false, 1200)
        toTheRunIn()
        assertTrue("the wait runs to seekRunInMs", tick(1048 + 1999, state(), NEAR_END))
        tick(1048 + 2000, state(), NEAR_END)
        assertFalse("no start by seekRunInMs after the ask ends the hold", hold.on)
    }

    @Test
    fun aRunInWhilePausedEndsTheHold() {
        toTheRunIn(state(playing = false))
        assertEquals("the paused run in still asks the next song (funeOS player/ui/app.cpp App::seek_tick :14482-14487, v0.4.3)", 1, p.nexts)
        assertFalse("a paused run in ends the hold", hold.on)
        p.nextFail = Refused("not now")
        toTheRunIn()
        assertFalse("a refused next ends the hold", hold.on)
        assertEquals(listOf("seek hold: not now"), logs)
    }

    @Test
    fun aHoldAtTheLastSongStopsAtTheEdge() {
        val last = state(index = 2)
        toTheRunIn(last)
        assertEquals("no next song, so nothing is asked", 0, p.nexts)
        assertTrue(hold.on)
        tick(1300, last, NEAR_END)
        assertEquals("the ramp holds its edge", "6:16", hud.seekLeft)
        assertTrue(hold.on)
    }

    @Test
    fun aFreshStartAtAHeldEdgeEndsTheHold() {
        val last = state(index = 2)
        toTheRunIn(last)
        assertTrue("premise: the edge is held", hold.on)
        emit(EngineEvent.Started(1L))
        tick(1300, last, NEAR_END)
        assertFalse("funeOS player/ui/app.cpp App::seek_edge :14526-14527, v0.4.3", hold.on)
    }

    @Test
    fun aSlowSeekHoldsTheNextFollow() {
        p.slow = CompletableDeferred()
        start()
        tick(1000)
        assertEquals(listOf(217000L), p.seeks)
        tick(1250)
        assertEquals("one seek in flight (funeOS player/ui/app.cpp App::seek_follow :14506-14516, v0.4.3)", listOf(217000L), p.seeks)
        p.slow?.complete(Unit)
        scheduler.runCurrent()
        tick(1264)
        assertEquals(2, p.seeks.size)
    }

    @Test
    fun theFinalSeekWaitsForTheCallInFlight() {
        p.slow = CompletableDeferred()
        start()
        tick(1000)
        hold.endHold(Hold.Glyph, 1010)
        tick(1110)
        assertFalse("premise: the final step ended the hold", hold.on)
        assertEquals("the final seek waits for the call in flight", listOf(217000L), p.seeks)
        p.slow?.complete(Unit)
        scheduler.runCurrent()
        assertEquals(listOf(217000L, 218000L), p.seeks)
    }

    @Test
    fun aLostKeyUpEndsTheKeyHold() {
        var down = true
        start(h = Hold.Key, held = { down })
        tick(1000)
        down = false
        tick(1048)
        assertTrue("the release owes one last step", tick(1100))
        tick(1148)
        assertFalse("a key read up each tick releases the ramp", hold.on)
    }

    @Test
    fun aDisconnectedSeekEndsTheHoldWithItsLine() {
        p.seekFail = Disconnected()
        start()
        tick(1000)
        assertFalse("a dropped link ends the hold, no retry", hold.on)
        assertEquals(listOf("seek hold: player disconnected"), logs)
        assertEquals(1, p.seeks.size)
    }

    @Test
    fun aFailureOutsideTheSeamPropagates() {
        p.seekFail = IllegalStateException("broken")
        start()
        tick(1000)
        assertEquals(listOf("broken"), thrown.map { it.message })
    }

    @Test
    fun aServiceTrackHoldsWithNoSeek() {
        val stream = state(track = null, service = 5L)
        assertTrue(start(stream))
        tick(1000, stream)
        tick(1300, stream)
        assertEquals("the ramp runs, seven steps from 3:36", "3:43", hud.seekLeft)
        assertEquals("a service track skips only the seek (funeOS player/ui/app.cpp App::seek_follow :14508, v0.4.3)", emptyList<Long>(), p.seeks)
    }

    @Test
    fun aLandingOwedAReleaseEndsAsFinal() {
        toTheRunIn()
        hold.endHold(Hold.Glyph, 1060)
        tick(1100, state(track = 2L, index = 1, dur = 300000), 0)
        assertFalse("premise: the landing ended the hold", hold.on)
        assertEquals("the owed release lands as a final seek of the carry (funeOS player/ui/app.cpp App::seek_tick :14455-14457, v0.4.3)", 2000L, p.seeks.last())
    }

    @Test
    fun theVariantTakingOverEndsTheHold() {
        start()
        tick(1000)
        tick(1048, variant = true)
        assertFalse("funeOS player/ui/app.cpp App::seek_tick :14428-14431, v0.4.3", hold.on)
    }

    @Test
    fun twoHeldKeysEachHoldTheRamp() {
        var a = true
        var b = true
        start(h = Hold.Key, held = { a })
        tick(1000)
        start(h = Hold.Key, held = { b })
        tick(1048)
        b = false
        tick(1096)
        tick(1300)
        assertTrue("the other key still holds the ramp (funeOS player/ui/app.cpp App::hold_bit :16064-16078, v0.4.3)", hold.on)
        a = false
        tick(1348)
        tick(1448)
        assertFalse("the last key up releases the ramp", hold.on)
    }

    @Test
    fun aRunInOnTheSameTrackLandsAtItsStart() {
        val one = state(size = 1, repeat = Repeat.All)
        toTheRunIn(one)
        emit(EngineEvent.Started(1L))
        tick(1100, one, 0)
        assertEquals("a one song queue on repeat all lands on its own restart (funeOS player/ui/app.cpp App::seek_edge :14524-14525, v0.4.3)", "0:02", hud.seekLeft)
    }

    @Test
    fun aNoSpaceSeekEndsTheHoldWithItsLine() {
        p.seekFail = NoSpace()
        start()
        tick(1000)
        assertFalse("a NoSpace seek ends the hold as a seam outcome", hold.on)
        assertEquals(listOf("seek hold: no space"), logs)
        assertEquals(emptyList<Throwable>(), thrown)
    }

    @Test
    fun aHeldEdgeSleepsUntilAPoke() {
        val last = state(index = 2)
        toTheRunIn(last)
        tick(1100, last, NEAR_END)
        assertNull("nothing is due at a held edge with no key (funeOS player/ui/app.cpp App::seek_wake :14551-14571, v0.4.3)", hold.wakeAt(1100))
        assertEquals("a running ramp wakes at its next step", 1096L, run {
            hold.end(false, 1100)
            start()
            tick(1048)
            hold.wakeAt(1048)
        })
    }

    private companion object {
        const val DUR = 377000L
        const val POS = 216000L
        const val NEAR_END = 375500L
    }
}
