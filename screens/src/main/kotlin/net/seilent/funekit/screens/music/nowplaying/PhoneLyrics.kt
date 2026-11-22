package net.seilent.funekit.screens.music.nowplaying

import kotlinx.coroutines.flow.StateFlow

interface PhoneLyrics {
    val mode: StateFlow<Boolean>

    fun setMode(on: Boolean)

    val revision: StateFlow<Long>

    suspend fun lyrics(t: PhoneTrack): String?

    fun offsetMs(t: PhoneTrack): Int = 0

    fun offset(t: PhoneTrack): OffsetEdit? = null
}
