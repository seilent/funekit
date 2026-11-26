package net.seilent.funekit.screens.host

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Disconnected
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.NoSpace
import net.seilent.funekit.api.Refused
import net.seilent.funekit.api.Stale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ActedTest {
    private val outcomes: List<Exception> = listOf(Disconnected(), Busy(BUSY_TEXT), Refused(REFUSED_TEXT), NoCard(), NoSpace(), Stale(STALE), Expired(EXPIRED))
    private val sets = mapOf(
        "PLAYING" to PLAYING, "LEVEL" to LEVEL, "READ" to READ, "READ_AT" to READ_AT, "QUEUE" to QUEUE,
        "ENQUEUE" to ENQUEUE, "SHEET" to SHEET, "PICK" to PICK, "DROP" to DROP, "HEART" to HEART,
    )

    @Test
    fun aMetOutcomeIsLoggedAndAnswersNull() {
        for ((name, set) in sets) {
            for (e in outcomes.filter { o -> set.any { it.isInstance(o) } }) {
                val logs = mutableListOf<String>()
                assertNull("$name ${e::class.simpleName}", runBlocking { acted({ logs += it }, "x", set) { throw e } })
                assertEquals("$name ${e::class.simpleName}", listOf("x: ${e.message}"), logs)
            }
        }
    }

    @Test
    fun anOutcomeOutsideItsSetIsThrown() {
        for ((name, set) in sets) {
            for (e in outcomes.filter { o -> set.none { it.isInstance(o) } } + IllegalStateException("broken") + CancellationException("cancelled")) {
                val logs = mutableListOf<String>()
                assertSame("$name ${e::class.simpleName}", e, runCatching { runBlocking { acted({ logs += it }, "x", set) { throw e } } }.exceptionOrNull())
                assertEquals("$name ${e::class.simpleName} logs nothing", emptyList<String>(), logs)
            }
        }
    }

    @Test
    fun eachActsSetIsTheOutcomesItsCallsMeet() {
        assertEquals(setOf(Disconnected::class, Busy::class, Refused::class, NoCard::class), PLAYING)
        assertEquals(setOf(Disconnected::class, Busy::class, Refused::class), LEVEL)
        assertEquals(setOf(Disconnected::class, Busy::class), READ)
        assertEquals(setOf(Disconnected::class, Busy::class, Expired::class), READ_AT)
        assertEquals(setOf(Disconnected::class, Busy::class, NoCard::class, Stale::class), QUEUE)
        assertEquals(setOf(Disconnected::class, Busy::class, Refused::class, NoCard::class, Stale::class, Expired::class), ENQUEUE)
        assertEquals(setOf(Disconnected::class, Busy::class, Refused::class, NoCard::class, NoSpace::class, Stale::class, Expired::class), SHEET)
        assertEquals(setOf(Disconnected::class, Busy::class, Stale::class, Expired::class), PICK)
        assertEquals(setOf(Disconnected::class, Stale::class), DROP)
        assertEquals(setOf(Disconnected::class, Busy::class, Refused::class, NoCard::class, Stale::class), HEART)
    }

    private companion object {
        const val BUSY_TEXT = "library loading"
        const val REFUSED_TEXT = "usb dac"
        const val STALE = 3L
        const val EXPIRED = 2L
    }
}
