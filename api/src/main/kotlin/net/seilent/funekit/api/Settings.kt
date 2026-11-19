package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow
import net.seilent.funekit.api.engine.Outputs
import net.seilent.funekit.api.engine.Sound

interface Settings {
    val sound: StateFlow<Sound>
    val outputs: StateFlow<Outputs>

    suspend fun update(sound: Sound)
    suspend fun setVolume(volume: Int)
    suspend fun setVolumePercent(pct: Int)
    suspend fun stepVolume(detents: Int)
    suspend fun setDop(on: Boolean)
}
