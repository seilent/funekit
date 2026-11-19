package net.seilent.funekit.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.seilent.funekit.api.Settings
import net.seilent.funekit.api.engine.Crossfeed
import net.seilent.funekit.api.engine.Dac
import net.seilent.funekit.api.engine.Output
import net.seilent.funekit.api.engine.Outputs
import net.seilent.funekit.api.engine.ReplayGain
import net.seilent.funekit.api.engine.Sound
import net.seilent.funekit.api.engine.Tube

class FakeSettings internal constructor(private val player: FakePlayer) : Settings {
    private val lock = Mutex()
    private val levels = HashMap<Dac, Int>()
    private val dopDacs = HashSet<Dac>()
    private val dops = HashMap<Dac, Boolean>()
    private var dac: Dac? = Dac(0, 0, "")
    private val _sound = MutableStateFlow(
        Sound(
            gapless = true,
            crossfadeMs = 0,
            replayGain = ReplayGain.Off,
            preampDb = 0f,
            crossfeed = Crossfeed.Off,
            width = WIDTH_OFF,
            tube = Tube.Off,
            output = Output.System,
            volume = null,
            volumePct = null,
            volumeStepPct = null,
        ),
    )

    override val sound: StateFlow<Sound> = _sound.asStateFlow()

    private val _outputs = MutableStateFlow(outputsNow())

    override val outputs: StateFlow<Outputs> = _outputs.asStateFlow()

    override suspend fun update(sound: Sound) = lock.withLock {
        require(sound.output == _sound.value.output || sound.output in _outputs.value.offered) { NOT_OFFERED }
        _sound.value = at(sound, if (sound.output == Output.Usb) level() else null)
        player.route(sound.output)
        _outputs.value = outputsNow()
    }

    override suspend fun setVolume(volume: Int) = lock.withLock { owned { volume } }

    override suspend fun setVolumePercent(pct: Int) = lock.withLock { owned { percentVolume(pct) } }

    override suspend fun stepVolume(detents: Int) = lock.withLock { owned { volumeStep(it, detents, STEP_PCT) } }

    override suspend fun setDop(on: Boolean) = lock.withLock {
        val d = dac
        require(d != null && dopOf(d) != null) { DOP_NOT_OFFERED }
        dops[d] = on
        _outputs.value = outputsNow()
    }

    internal suspend fun attachDac(dac: Dac, dop: Boolean) = lock.withLock {
        this.dac = dac
        if (dop) dopDacs += dac
        val s = _sound.value
        if (s.output == Output.Usb) _sound.value = at(s, level())
        _outputs.value = outputsNow()
    }

    internal suspend fun detachDac() = lock.withLock {
        dac = null
        val s = _sound.value
        if (s.output == Output.Usb) _sound.value = at(s, null)
        _outputs.value = outputsNow()
    }

    private fun owned(to: (Int) -> Int) {
        val s = _sound.value
        if (s.output != Output.Usb) return
        val d = dac ?: return
        val v = to(levels.getOrPut(d) { DAC_VOLUME }).coerceIn(0, MAX_VOLUME)
        levels[d] = v
        _sound.value = at(s, v)
    }

    private fun outputsNow(): Outputs {
        val d = dac
        val offered = listOf(Output.System, Output.BitPerfect) + listOfNotNull(Output.Usb.takeIf { d != null })
        return Outputs(offered, d?.let { DAC_NAME }, false, false, null, d?.let { dopOf(it) })
    }

    private fun dopOf(d: Dac): Boolean? = if (_sound.value.output == Output.Usb && d in dopDacs) dops[d] ?: false else null

    private fun at(s: Sound, v: Int?): Sound = s.copy(volume = v, volumePct = v?.let { volumePercent(it) }, volumeStepPct = v?.let { STEP_PCT })

    private fun level(): Int? = dac?.let { levels.getOrPut(it) { DAC_VOLUME } }

    private companion object {
        const val WIDTH_OFF = 100
        const val DAC_VOLUME = 195
        const val MAX_VOLUME = 255
        const val PERCENT = 100
        const val SPAN = 141
        const val STEP_PCT = 5
        const val DAC_NAME = "fake dac"
        const val DOP_NOT_OFFERED = "dop not offered"
        const val NOT_OFFERED = "output not offered"
        val STEPS = IntArray(PERCENT + 1).also { t ->
            var n = MAX_VOLUME - SPAN
            for (s in 1..PERCENT) {
                t[s] = n
                n += if (s <= SPAN - (PERCENT - 1)) 2 else 1
            }
        }

        fun volumePercent(v: Int): Int {
            var s = PERCENT
            while (s > 0 && STEPS[s] > v) s--
            return s
        }

        fun percentVolume(pct: Int): Int = STEPS[pct.coerceIn(0, PERCENT)]

        fun snap(v: Int, dir: Int): Int {
            val n = v.coerceIn(0, MAX_VOLUME)
            val s = volumePercent(n)
            if (STEPS[s] == n) return n
            return STEPS[if (dir > 0 || (dir == 0 && s == 0)) s + 1 else s]
        }

        fun volumeStep(v: Int, detents: Int, pct: Int): Int {
            val snapped = snap(v, detents)
            if (detents != 0 && snapped != v.coerceIn(0, MAX_VOLUME)) return snapped
            var n = percentVolume(volumePercent(v) + detents * pct)
            if (detents > 0 && n <= v) n = v + 1
            if (detents < 0 && n >= v) n = v - 1
            return n.coerceIn(0, MAX_VOLUME)
        }
    }
}
