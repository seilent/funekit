package net.seilent.funekit.screens.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.EngineEvent
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.kit.scrub.SeekRamp

internal enum class Hold { Glyph, Key }

internal class Refuse(val variant: Boolean, val card: Card)

internal class SeekHold(
    private val scope: CoroutineScope,
    private val hud: VolumeHud,
    private val log: (String) -> Unit,
    private val now: () -> Long,
    private val moved: (Int, Long, Long) -> Unit,
) {
    private val ramp = SeekRamp()
    private val holds = mutableSetOf<Hold>()
    private val keys = mutableListOf<() -> Boolean>()
    private val poke = Channel<Unit>(Channel.CONFLATED)
    private var player: Player? = null
    private var track: Long? = null
    private var service = false
    private var askIndex = -1
    private var asking = false
    private var landTrack: Long? = null
    private var gone = false
    private var followed = 0L
    private var followAt = -1L
    private var opened = false
    private var call: Job? = null
    private var events: Job? = null
    private var gen = 0

    var dir by mutableIntStateOf(0)
        private set
    var target by mutableLongStateOf(0L)
        private set
    var dur by mutableLongStateOf(0L)
        private set

    val on: Boolean get() = ramp.on

    fun holds(h: Hold): Boolean = h in holds

    fun wakeAt(at: Long): Long? {
        if (!ramp.on) return null
        val tick = Tok.seekTickMs.toLong()
        val due = mutableListOf(ramp.finalDue(), ramp.waitEnd(), ramp.stepDue())
        if (!ramp.waiting && ramp.finalDue() < 0 && ramp.target != followed && !service && call?.isActive != true) {
            val f = if (followAt < 0) at else followAt + tick
            due += if (f <= at) at + Tok.seekRampMs.toLong() else f
        }
        if (Hold.Key in holds) due += at + tick
        return due.filter { it >= 0 }.minOrNull()
    }

    suspend fun idle(at: Long) {
        val w = wakeAt(at)
        if (w == null) poke.receive() else if (w > at) withTimeoutOrNull(w - at) { poke.receive() }
    }

    fun start(dir: Int, h: Hold, at: Long, p: Player, st: PlayerState, pos: Long, from: Refuse, held: () -> Boolean, open: () -> Boolean): Boolean {
        if (from.variant || from.card != Card.Present || st.queueSize == 0 || (st.track == null && st.service == null)) return false
        if (h == Hold.Glyph && st.durationMs <= 0) return false
        if (ramp.on) {
            ramp.join(dir, at)
        } else {
            val d = st.durationMs
            if (dir > 0 && d <= 0) return false
            gen++
            opened = open()
            ramp.start(dir, at, pos, d)
            player = p
            track = st.track
            service = st.track == null && st.service != null
            asking = false
            landTrack = null
            gone = false
            followed = pos
            followAt = -1
            val g = gen
            events = scope.launch(FailFast, CoroutineStart.UNDISPATCHED) {
                launch(start = CoroutineStart.UNDISPATCHED) { p.state.collect { poke.trySend(Unit) } }
                p.events.collect {
                    if (g == gen) edge(it)
                    poke.trySend(Unit)
                }
            }
        }
        holds += h
        if (h == Hold.Key) keys += held
        poke.trySend(Unit)
        return true
    }

    fun endHold(h: Hold, at: Long) {
        if (!holds.remove(h)) return
        if (h == Hold.Key) keys.clear()
        if (holds.isEmpty() && ramp.on) ramp.release(at)
        poke.trySend(Unit)
    }

    private fun edge(e: EngineEvent) {
        if (!ramp.on || gone) return
        when (e) {
            is EngineEvent.Ended -> if (e.track == track && !ramp.waiting) gone = true
            is EngineEvent.Started -> if (ramp.waiting && asking) {
                landTrack = e.track
            } else if (e.track != track || ramp.edgeHeld) {
                gone = true
            }
            else -> Unit
        }
    }

    fun tick(at: Long, st: PlayerState, pos: Long, variant: Boolean, plate: Boolean): Boolean {
        if (!ramp.on) return false
        if (variant || gone || (!ramp.waiting && st.track != track)) {
            end(false, at)
            return false
        }
        if (Hold.Key in holds && keys.removeAll { !it() } && keys.isEmpty()) {
            holds -= Hold.Key
            if (holds.isEmpty() && !ramp.released) ramp.release(at)
        }
        if (ramp.waiting) {
            if (asking && (st.index == askIndex + 1 || (landTrack != null && st.track == landTrack))) {
                asking = false
                landTrack = null
                track = st.track
                followed = pos
                if (st.durationMs <= 0) {
                    end(false, at)
                    return false
                }
                if (ramp.land(at, st.durationMs)) {
                    end(true, at)
                    return false
                }
            } else if (at >= ramp.waitEnd()) {
                end(false, at)
                return false
            } else {
                return true
            }
        }
        if (ramp.finalDue() >= 0) {
            if (at < ramp.finalDue()) return true
            ramp.finalStep()
            end(true, at)
            return false
        }
        if (ramp.step(at) == SeekRamp.Step.RunIn) {
            if (st.index >= st.queueSize - 1 && st.repeat != Repeat.All) {
                ramp.holdEdge()
            } else if (!runIn(at, st)) {
                return false
            }
        }
        if (dir != ramp.dir || target != ramp.target || dur != ramp.dur) {
            dir = ramp.dir
            target = ramp.target
            dur = ramp.dur
            moved(dir, target, dur)
        }
        if (plate) {
            hud.seekShow(ramp.dir, ramp.target, ramp.dur, at, opened)
            hud.keyHold(Hold.Key in holds, at)
            opened = false
        }
        follow(at)
        return true
    }

    private fun runIn(at: Long, st: PlayerState): Boolean {
        val p = player ?: return true
        val g = gen
        asking = true
        askIndex = st.index
        scope.launch(FailFast) {
            try {
                p.next()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!outcome(e)) throw e
                log("seek hold: ${e.message}")
                if (g == gen && ramp.on) end(false, now())
            }
        }
        if (st.playing) return true
        end(false, at)
        return false
    }

    private fun follow(at: Long) {
        if (ramp.waiting || service || ramp.target == followed || call?.isActive == true) return
        if (followAt >= 0 && at - followAt < Tok.seekTickMs.toLong()) return
        followed = ramp.target
        followAt = at
        send(ramp.target, null)
    }

    private fun send(ms: Long, after: Job?) {
        val p = player ?: return
        val g = gen
        call = scope.launch(FailFast) {
            after?.join()
            try {
                p.seek(ms)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!outcome(e)) throw e
                log("seek hold: ${e.message}")
                if (g == gen && ramp.on) end(false, now())
            }
            poke.trySend(Unit)
        }
    }

    fun end(final: Boolean, at: Long) {
        if (!ramp.on) return
        if (final && ramp.target != followed) send(ramp.target, call)
        ramp.stop()
        holds.clear()
        keys.clear()
        asking = false
        landTrack = null
        gone = false
        opened = false
        events?.cancel()
        events = null
        dir = 0
        target = 0
        dur = 0
        moved(0, 0, 0)
        hud.keyHold(false, at)
        if (final) hud.seekFinal(at) else hud.seekDrop(at)
    }

    private fun outcome(e: Exception): Boolean = PLAYING.any { it.isInstance(e) }
}
