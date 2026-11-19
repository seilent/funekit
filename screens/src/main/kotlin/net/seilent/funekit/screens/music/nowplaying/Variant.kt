package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.api.PlayerState

sealed interface NpFeed {
    data class Own(val state: PlayerState) : NpFeed
    data class Phone(val track: PhoneTrack) : NpFeed
}

object Variant {
    fun pick(own: PlayerState?, phone: PhoneTrack?): NpFeed? {
        if (own != null && own.track != null && own.playing) return NpFeed.Own(own)
        if (phone != null && phone.playing) return NpFeed.Phone(phone)
        if (own != null && own.track != null) return NpFeed.Own(own)
        return phone?.let { NpFeed.Phone(it) }
    }
}
