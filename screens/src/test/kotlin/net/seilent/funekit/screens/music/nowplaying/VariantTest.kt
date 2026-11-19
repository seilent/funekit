package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.api.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariantTest {
    private val phone = PhoneTrack("Radio", "T", "A", "B", true, false, 0, 0, 0, 1f, false, PhoneRepeat.Off, 0, 0, null)
    private val playing = PlayerState(queueSize = 1, index = 0, track = 1, playing = true)
    private val paused = PlayerState(queueSize = 1, index = 0, track = 1, playing = false)

    @Test
    fun ownPlaybackWinsOverAPlayingPhoneSession() {
        assertEquals(NpFeed.Own(playing), Variant.pick(playing, phone))
    }

    @Test
    fun aPlayingPhoneSessionShowsWhileOwnPlaybackIsPaused() {
        assertEquals(NpFeed.Phone(phone), Variant.pick(paused, phone))
    }

    @Test
    fun aPausedOwnTrackWinsOverAPausedPhoneSession() {
        assertEquals(NpFeed.Own(paused), Variant.pick(paused, phone.copy(playing = false, paused = true)))
    }

    @Test
    fun anEmptyQueueShowsThePhoneSessionAndNothingWithout() {
        assertEquals(NpFeed.Phone(phone), Variant.pick(PlayerState(), phone))
        assertNull(Variant.pick(PlayerState(), null))
    }
}
