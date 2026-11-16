package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {
    private val old = "commons,theaudiodb,fanarttv,discogs,deezer"

    @Test
    fun everySourceIsOnByDefaultInTheMeasuredOrder() {
        val s = Sources(theAudioDb = true, discogs = true, fanartTv = true, deezer = true, commons = true)
        assertEquals(listOf("theaudiodb", "fanarttv", "discogs", "commons", "deezer"), Sources.ORDER)
        assertEquals(Sources.ORDER, s.order)
        assertEquals(setOf("commons", "theaudiodb", "discogs", "deezer"), s.enabled)
        assertEquals(listOf("theaudiodb", "discogs", "commons", "deezer"), s.ordered)
        assertEquals(listOf("theaudiodb", "fanarttv", "discogs", "commons", "deezer"), s.copy(fanartTvKey = "k").ordered)
    }

    @Test
    fun noneIsEveryToggleOffAndCommonsTurnsOff() {
        assertEquals(emptySet<String>(), Sources.NONE.enabled)
        assertEquals(Sources.NONE.copy(commons = true), Sources.COMMONS)
        assertEquals(setOf("theaudiodb", "discogs", "deezer"), Sources(theAudioDb = true, discogs = true, fanartTv = true, deezer = true).enabled)
        assertEquals(listOf("theaudiodb", "discogs", "deezer"), Sources(theAudioDb = true, discogs = true, fanartTv = true, deezer = true).ordered)
    }

    @Test
    fun commonsIsCommonsOnlyInTheOldOrder() {
        assertEquals(setOf("commons"), Sources.COMMONS.enabled)
        assertEquals(old.split(','), Sources.COMMONS.order)
        assertEquals(setOf("commons", "fanarttv"), Sources.COMMONS.copy(fanartTv = true, fanartTvKey = "k").enabled)
        assertEquals(setOf("commons"), Sources.COMMONS.copy(fanartTvKey = "k").enabled)
    }

    @Test
    fun aStoredOrderKeepsKnownIdsAndAppendsTheRest() {
        assertEquals(Sources.ORDER, Sources.order(null))
        assertEquals(listOf("deezer", "theaudiodb", "fanarttv", "discogs", "commons"), Sources.order("deezer"))
    }

    @Test
    fun aKitDefaultTurnsNothingOn() {
        assertEquals(Sources.NONE.copy(order = Sources.ORDER), Sources())
        assertEquals(emptySet<String>(), Sources().enabled)
        assertTrue(Sources().ordered.isEmpty())
    }

    @Test
    fun aStoredOrderKeepsAnIdTheKitDoesNotKnowInPlace() {
        val stored = "theaudiodb,fanarttv,youtube,discogs,commons,deezer"
        assertEquals(stored.split(','), Sources.order(stored))
        assertEquals(listOf("theaudiodb", "fanarttv", "discogs", "commons", "deezer"), Sources.order("theaudiodb,fanarttv,discogs,commons,deezer"))
        val s = Sources.COMMONS.copy(order = Sources.order(stored), plugins = mapOf("youtube" to true))
        assertEquals(listOf("youtube", "commons"), s.ordered)
        assertEquals(listOf("commons"), s.copy(plugins = mapOf("youtube" to false)).ordered)
    }

    @Test
    fun aHostBaseOrderPlacesItsPlugInWithNothingStored() {
        val base = listOf("theaudiodb", "fanarttv", "youtube", "discogs", "commons", "deezer")
        assertEquals(base, Sources.order(null, base))
        assertEquals(listOf("deezer", "theaudiodb", "fanarttv", "youtube", "discogs", "commons"), Sources.order("deezer", base))
        val s = Sources(theAudioDb = true, commons = true, order = Sources.order(null, base), plugins = mapOf("youtube" to true))
        assertEquals(listOf("theaudiodb", "youtube", "commons"), s.ordered)
    }
}
