package net.seilent.funekit.screens.host

import kotlinx.coroutines.flow.StateFlow
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack

class PhoneSession(val track: PhoneTrack, val toggle: () -> Unit, val skip: (Int) -> Unit)

interface MusicApp<S : Any> {
    fun music(s: S): MusicEntry?

    fun screen(e: MusicEntry): S

    fun homeHas(word: String): Boolean

    fun homeItem(word: String): Int

    fun homeBusy(now: Long): Boolean

    fun homeLand()

    fun homeFront(): Boolean

    fun liftRow(top: S?): Int?

    fun cancel(top: S?)

    val phone: StateFlow<PhoneSession?>
}

interface VolumeLevel {
    fun percent(): Int

    fun stepPct(): Int

    suspend fun step(detents: Int)

    suspend fun set(pct: Int)

    fun maxPct(): Int? = null
}
