package net.seilent.funekit.screens.host

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import net.seilent.funekit.screens.music.nowplaying.OffsetEdit
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack

class PhoneSession(val track: PhoneTrack, val toggle: () -> Unit, val skip: (Int) -> Unit)

private val noBackdrop: StateFlow<NpPicture?> = MutableStateFlow<NpPicture?>(null).asStateFlow()

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

    val backdrop: StateFlow<NpPicture?> get() = noBackdrop

    val lyricsView: Boolean get() = false

    fun offset(track: Long): OffsetEdit? = null
}

interface VolumeLevel {
    fun percent(): Int

    fun stepPct(): Int

    suspend fun step(detents: Int)

    suspend fun set(pct: Int)

    fun maxPct(): Int? = null
}
