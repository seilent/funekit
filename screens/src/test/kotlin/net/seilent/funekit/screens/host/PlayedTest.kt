package net.seilent.funekit.screens.host

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Disconnected
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.Refused
import net.seilent.funekit.api.Stale
import net.seilent.funekit.screens.music.overlay.CARD_GONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlayedTest {
    private fun of(e: Exception): Pair<Boolean, String?> = runBlocking { played { throw e } }

    @Test
    fun eachPlayOutcomeTakesItsWord() {
        assertEquals(true to null, runBlocking { played { true } })
        assertEquals("a play that sends nothing turns back with no word", false to null, runBlocking { played { false } })
        assertEquals(false to CARD_GONE, of(NoCard()))
        assertEquals("a Busy is a card or library not ready, whatever its text", false to CARD_GONE, of(Busy(BUSY_TEXT)))
        assertEquals(false to REFUSED_TEXT, of(Refused(REFUSED_TEXT)))
        assertEquals(false to CARD_GONE, of(Refused()))
        for (e in listOf(Disconnected(), Stale(STALE), Expired(EXPIRED))) assertEquals("${e::class.simpleName} turns back with no word", false to null, of(e))
    }

    @Test
    fun anyOtherFailureOfAPlayFailsPlainly() {
        val e = IllegalStateException("broken")
        assertSame(e, runCatching { of(e) }.exceptionOrNull())
    }

    private companion object {
        const val BUSY_TEXT = "library loading"
        const val REFUSED_TEXT = "usb dac"
        const val STALE = 3L
        const val EXPIRED = 2L
    }
}
