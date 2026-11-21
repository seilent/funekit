package net.seilent.funekit.fake

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.EngineEvent
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.PlayerCaps
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Refused
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.api.Stale
import net.seilent.funekit.api.engine.Format
import net.seilent.funekit.api.engine.Output
import kotlin.random.Random

class FakePlayer internal constructor(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val library: FakeLibrary,
    private val store: Store,
    private val bus: MutableSharedFlow<EngineEvent>,
) : Player {
    private val lock = Mutex()
    private val queue = FakeQueue(Random(SEED))
    private val _state = MutableStateFlow(PlayerState())
    private var end: Job? = null
    private var origin: Listing? = null
    private var stopped = true
    private var bookSpeed = PERCENT
    private var sleepAt: Long? = null
    private var sleepJob: Job? = null
    private var sleepEnd = false
    private var streamed: Long? = null

    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val events: SharedFlow<EngineEvent> = bus.asSharedFlow()
    override val caps = PlayerCaps(sleep = true, endArm = true, speeds = SPEEDS)

    override suspend fun play(ids: List<Long>, index: Int, posMs: Long) = lock.withLock {
        library.present()
        start(ids, index, posMs, null, false)
    }

    override suspend fun play(listing: Listing, generation: Long, index: Int, posMs: Long, shuffle: Boolean) = lock.withLock {
        library.present()
        start(library.ids(listing, generation), index, posMs, listing, shuffle)
    }

    override suspend fun toggle() = lock.withLock { if (_state.value.playing) halt() else proceed() }

    override suspend fun pause() = lock.withLock { halt() }

    override suspend fun resume() = lock.withLock { proceed() }

    override suspend fun next() = lock.withLock {
        val s = _state.value
        val to = if (queue.slot + 1 < queue.size) queue.slot + 1 else if (s.repeat == Repeat.All && queue.size > 0) 0 else -1
        if (to >= 0) {
            queue.select(to)
            load(0, s.playing)
        }
    }

    override suspend fun prev() = lock.withLock {
        if (queue.size == 0) return@withLock
        val s = _state.value
        if (!stopped && s.anchor.ms(clock()) >= RESTART_MS) {
            seekTo(0)
            return@withLock
        }
        val to = if (queue.slot > 0) queue.slot - 1 else if (s.repeat == Repeat.All) queue.size - 1 else -1
        if (to < 0) {
            seekTo(0)
            return@withLock
        }
        queue.select(to)
        load(0, stopped || s.playing)
    }

    override suspend fun seek(ms: Long) = lock.withLock { seekTo(ms) }

    override suspend fun stop() = lock.withLock {
        end?.cancel()
        val s = _state.value
        if (s.input == null) return@withLock
        val at = if (stopped) frozen(s) else s.anchor.copy(frame = 0, elapsedNanos = clock(), running = false)
        publish(s.copy(playing = false, anchor = at))
    }

    override suspend fun enqueue(ids: List<Long>) = lock.withLock {
        library.present()
        add(ids) { queue.enqueue(ids) }
    }

    override suspend fun enqueue(listing: Listing, generation: Long) = lock.withLock {
        library.present()
        val ids = library.ids(listing, generation)
        add(ids) { queue.enqueue(ids) }
    }

    override suspend fun playNext(ids: List<Long>) = lock.withLock {
        library.present()
        add(ids) { queue.playNext(ids) }
    }

    override suspend fun playNext(listing: Listing, generation: Long) = lock.withLock {
        library.present()
        val ids = library.ids(listing, generation)
        add(ids) { queue.playNext(ids) }
    }

    override suspend fun move(from: Int, to: Int, revision: Long) = lock.withLock {
        fresh(revision)
        if (queue.move(from, to)) edited()
    }

    override suspend fun remove(index: Int, revision: Long) = lock.withLock {
        fresh(revision)
        val wasCurrent = index == queue.slot
        if (!queue.remove(index)) return@withLock
        when {
            queue.size == 0 -> reset()
            wasCurrent && stopped -> {
                load(0, false)
                stopped = true
                emit(EngineEvent.QueueChanged)
            }
            wasCurrent -> {
                load(0, _state.value.playing)
                emit(EngineEvent.QueueChanged)
            }
            else -> edited()
        }
    }

    override suspend fun playAt(index: Int, revision: Long) = lock.withLock {
        library.present()
        fresh(revision)
        if (index !in 0 until queue.size) return@withLock
        queue.select(index)
        load(0, true)
    }

    override suspend fun playNextAt(slot: Int, revision: Long) = lock.withLock {
        fresh(revision)
        val cur = queue.slot
        if (slot !in 0 until queue.size || slot == cur || slot == cur + 1) return@withLock
        if (queue.move(slot, if (slot < cur) cur else cur + 1)) edited()
    }

    override suspend fun clear() = lock.withLock { reset() }

    override suspend fun shuffle(on: Boolean) = lock.withLock {
        val r = queue.revision
        queue.shuffle(on)
        publish(_state.value)
        if (queue.revision != r) emit(EngineEvent.QueueChanged)
    }

    override suspend fun repeat(mode: Repeat) = lock.withLock { publish(_state.value.copy(repeat = mode)) }

    override suspend fun skip(ms: Long) = lock.withLock {
        require(ms in -MAX_SKIP..MAX_SKIP) { "skip $ms" }
        val s = _state.value
        if (s.input == null || stopped || !s.book) throw Refused()
        val to = s.anchor.ms(clock()) + ms
        when {
            to < s.durationMs -> seekTo(to.coerceAtLeast(0))
            queue.slot + 1 < queue.size -> {
                queue.select(queue.slot + 1)
                load(0, s.playing)
            }
            else -> throw Refused()
        }
    }

    override suspend fun speed(pct: Int) = lock.withLock {
        require(pct in SPEEDS) { "speed $pct" }
        val s = _state.value
        if (s.input == null || stopped || !s.book) throw Refused()
        bookSpeed = pct
        publish(s.copy(anchor = frozen(s).copy(running = s.anchor.running, speedPct = pct)))
        schedule()
    }

    override suspend fun sleep(minutes: Int) = lock.withLock {
        require(minutes in 0..MAX_SLEEP) { "sleep $minutes" }
        sleepJob?.cancel()
        sleepEnd = false
        sleepAt = if (minutes == 0) null else clock() + minutes * MINUTE * MILLI
        sleepJob = sleepAt?.let {
            scope.launch {
                delay(minutes * MINUTE)
                lock.withLock { slept() }
            }
        }
        publish(_state.value)
    }

    override suspend fun playService(id: Long) = lock.withLock {
        if (!library.service(id)) throw Refused()
        start(listOf(id), 0, 0, null, false, id)
    }

    override suspend fun endOfTrack() = lock.withLock {
        if (queue.current == null) throw Refused()
        sleepJob?.cancel()
        sleepJob = null
        sleepAt = null
        sleepEnd = true
        publish(_state.value)
    }

    internal suspend fun route(output: Output) = lock.withLock {
        val s = _state.value
        if (s.route == output) return@withLock
        val input = s.input
        val out = input?.let { outputOf(it, output) }
        if (input != null && out == null) {
            publish(s.copy(route = output))
            load(0, s.playing)
            if (_state.value.input == null) emit(EngineEvent.RouteChanged(output, false))
            return@withLock
        }
        val resampled = input != null && out != null && out.rate != input.rate
        publish(s.copy(route = output, output = out, resampled = resampled))
        emit(EngineEvent.RouteChanged(output, resampled))
        if (input != null && out != null) emit(EngineEvent.FormatChanged(input, out))
    }

    internal suspend fun loseOutput() = lock.withLock {
        val s = _state.value
        if (s.input == null) return@withLock
        end?.cancel()
        publish(s.copy(playing = false, anchor = frozen(s)))
        emit(EngineEvent.OutputLost)
    }

    internal suspend fun routeTimeout() = lock.withLock {
        end?.cancel()
        val s = _state.value
        emit(EngineEvent.RouteTimeout)
        if (s.input != null) publish(s.copy(playing = false, anchor = frozen(s)))
    }

    internal suspend fun xrun() = lock.withLock {
        val s = _state.value.copy(xruns = _state.value.xruns + 1)
        publish(s)
        emit(EngineEvent.Xruns(s.xruns))
    }

    internal suspend fun drop(ids: Set<Long>) = lock.withLock {
        val current = queue.current
        if (!queue.drop(ids)) return@withLock
        when {
            queue.size == 0 -> reset()
            queue.current != current -> {
                load(0, false)
                emit(EngineEvent.QueueChanged)
            }
            else -> edited()
        }
    }

    internal suspend fun unorigin(listing: Listing) = lock.withLock {
        if (origin != listing) return@withLock
        origin = null
        publish(_state.value)
    }

    private fun fresh(revision: Long) {
        if (revision != queue.revision) throw Stale(revision)
    }

    private fun start(ids: List<Long>, index: Int, posMs: Long, from: Listing?, shuffle: Boolean, service: Long? = null) {
        origin = from
        streamed = service
        sleepEnd = false
        queue.set(ids, index, shuffle)
        load(posMs, true)
        emit(EngineEvent.QueueChanged)
    }

    private fun seekTo(ms: Long) {
        val s = _state.value
        if (s.input == null || stopped) return
        val at = ms.coerceIn(0, s.durationMs)
        publish(s.copy(anchor = s.anchor.copy(frame = at * s.anchor.rate / 1000, elapsedNanos = clock(), running = s.playing)))
        schedule()
    }

    private fun halt() {
        end?.cancel()
        val s = _state.value
        if (s.input == null) return
        publish(s.copy(playing = false, anchor = frozen(s)))
    }

    private fun proceed() {
        val s = _state.value
        if (queue.current == null) return
        if (s.input == null || s.anchor.ms(clock()) >= s.durationMs) return load(0, true)
        stopped = false
        publish(s.copy(playing = true, anchor = s.anchor.copy(elapsedNanos = clock(), running = true)))
        schedule()
    }

    private fun add(ids: List<Long>, edit: () -> Unit) {
        if (ids.isEmpty()) return
        val empty = queue.size == 0
        edit()
        if (empty) {
            load(0, false)
            emit(EngineEvent.QueueChanged)
        } else {
            edited()
        }
    }

    private fun edited() {
        publish(_state.value)
        emit(EngineEvent.QueueChanged)
    }

    private fun reset() {
        end?.cancel()
        stopped = true
        origin = null
        sleepEnd = false
        streamed = null
        queue.clear()
        publish(_state.value.copy(playing = false, anchor = Anchor.NONE, input = null, output = null, resampled = false))
        emit(EngineEvent.QueueChanged)
    }

    private fun emit(e: EngineEvent) {
        bus.tryEmit(e)
    }

    private fun publish(s: PlayerState) {
        if (store.queue.revision != queue.revision) store.queued(queue.ids(), queue.revision)
        val current = queue.current
        val service = current?.takeIf { it == streamed }
        _state.value = s.copy(
            queueSize = queue.size,
            queueRevision = queue.revision,
            index = queue.slot,
            track = current.takeIf { service == null },
            origin = origin,
            shuffle = queue.shuffle,
            book = service == null && current?.let { library.track(it)?.book } == true,
            sleepAtNanos = sleepAt,
            sleepEnd = sleepEnd,
            service = service,
        )
    }

    private fun frame(s: PlayerState): Long {
        val a = s.anchor
        if (!a.running || a.rate <= 0) return a.frame
        val wall = (clock() - a.elapsedNanos).coerceAtLeast(0)
        val t = wall / PERCENT * a.speedPct + wall % PERCENT * a.speedPct / PERCENT
        val f = a.frame + t / NANOS * a.rate + t % NANOS * a.rate / NANOS
        return f.coerceAtMost(a.durationMs * a.rate / 1000)
    }

    private fun frozen(s: PlayerState) = s.anchor.copy(frame = frame(s), elapsedNanos = clock(), running = false)

    internal fun outputOf(input: Format, route: Output): Format? {
        val ceiling = when (route) {
            Output.System -> SYSTEM_CEILING
            Output.Usb -> USB_CEILING
            Output.BitPerfect -> return input
        }
        if (input.rate <= ceiling) return input.copy(channels = maxOf(input.channels, STEREO))
        if (input.channels > STEREO) return null
        for (shift in 1..2) {
            val rate = input.rate shr shift
            if (rate shl shift != input.rate || rate > ceiling) continue
            return if (rate in STANDARD_RATES) Format(rate, input.bits, STEREO) else null
        }
        return null
    }

    private fun inputOf(t: Track): Format =
        if (t.codec == Codec.Dsf) Format(if (t.format.rate % DSD_441 == 0) DSD_PCM_441 else DSD_PCM_48, DSD_PCM_BITS, t.format.channels) else t.format

    private fun load(posMs: Long, playing: Boolean) {
        end?.cancel()
        val route = _state.value.route
        var t: Track? = null
        var out: Format? = null
        while (true) {
            val id = queue.current ?: break
            val found = entry(id)
            val o = found?.takeUnless { it.broken }?.let { outputOf(inputOf(it), route) }
            if (found != null && o != null) {
                t = found
                out = o
                break
            }
            emit(EngineEvent.Failed(id, if (found == null) MISSING else if (found.broken) UNREADABLE else ABOVE_CEILING))
            if (queue.slot + 1 >= queue.size) break
            queue.select(queue.slot + 1)
        }
        val s = _state.value
        if (t == null || out == null) {
            stopped = true
            publish(s.copy(playing = false, anchor = Anchor.NONE, input = null, output = null, resampled = false))
            return
        }
        stopped = false
        val input = inputOf(t)
        val resampled = out.rate != input.rate
        val at = posMs.coerceIn(0, t.ms)
        val anchor = Anchor(at * input.rate / 1000, input.rate, clock(), playing, t.ms, if (t.book) bookSpeed else PERCENT)
        publish(s.copy(playing = playing, anchor = anchor, input = input, output = out, resampled = resampled))
        emit(EngineEvent.FormatChanged(input, out))
        emit(EngineEvent.RouteChanged(s.route, resampled))
        if (playing) {
            store.played(t.id)
            if (t.id != streamed) emit(EngineEvent.Started(t.id))
        }
        schedule()
    }

    private fun schedule() {
        end?.cancel()
        val s = _state.value
        if (!s.playing || !s.anchor.running) return
        val left = (s.durationMs - s.anchor.ms(clock())).coerceAtLeast(0)
        end = scope.launch {
            delay((left * PERCENT + s.anchor.speedPct - 1) / s.anchor.speedPct)
            lock.withLock { ended() }
        }
    }

    private fun ended() {
        end = null
        val s = _state.value
        val id = queue.current ?: return
        if (id != streamed) emit(EngineEvent.Ended(id))
        val play = !sleepEnd
        sleepEnd = false
        when {
            s.repeat == Repeat.One -> load(0, play)
            queue.slot + 1 < queue.size -> {
                queue.select(queue.slot + 1)
                load(0, play)
            }
            s.repeat == Repeat.All -> {
                queue.select(0)
                load(0, play)
            }
            else -> {
                stopped = true
                publish(s.copy(playing = false, anchor = s.anchor.copy(frame = s.durationMs * s.anchor.rate / 1000, elapsedNanos = clock(), running = false)))
            }
        }
    }

    private fun entry(id: Long): Track? = if (id == streamed) Fixture.SERVICE_TRACK else library.track(id)

    private fun slept() {
        sleepJob = null
        sleepAt = null
        halt()
        publish(_state.value)
    }

    private companion object {
        const val SEED = 7L
        const val RESTART_MS = 3000L
        const val NANOS = 1_000_000_000L
        const val SYSTEM_CEILING = 96000
        const val USB_CEILING = 384000
        const val STEREO = 2
        const val DSD_441 = 2822400
        const val DSD_PCM_441 = 352800
        const val DSD_PCM_48 = 384000
        const val DSD_PCM_BITS = 24
        val STANDARD_RATES = setOf(32000, 44100, 48000, 64000, 88200, 96000, 176400, 192000, 352800, 384000)
        const val MISSING = "missing"
        const val UNREADABLE = "unreadable"
        const val ABOVE_CEILING = "rate above the ceiling"
        const val PERCENT = 100
        const val MILLI = 1_000_000L
        const val MINUTE = 60_000L
        const val MAX_SLEEP = 24 * 60
        const val MAX_SKIP = 24 * 60 * MINUTE
        val SPEEDS = listOf(50, 100, 150, 200)
    }
}
