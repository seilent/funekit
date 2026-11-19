package net.seilent.funekit.api

import kotlin.metadata.KmClass
import kotlin.metadata.isSecondary
import kotlin.metadata.jvm.KotlinClassMetadata
import net.seilent.funekit.api.engine.Crossfeed
import net.seilent.funekit.api.engine.Format
import net.seilent.funekit.api.engine.Output
import net.seilent.funekit.api.engine.Outputs
import net.seilent.funekit.api.engine.ReplayGain
import net.seilent.funekit.api.engine.Root
import net.seilent.funekit.api.engine.Sound
import net.seilent.funekit.api.engine.Tube
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiShapeTest {
    private fun km(c: Class<*>): KmClass =
        (KotlinClassMetadata.readStrict(c.getAnnotation(Metadata::class.java)) as KotlinClassMetadata.Class).kmClass

    private fun fields(c: Class<*>): List<String> =
        km(c).constructors.singleOrNull { !it.isSecondary }?.valueParameters?.map { it.name }.orEmpty()

    private fun variants(c: Class<*>): Map<String, List<String>> = km(c).sealedSubclasses.associate { n ->
        val name = n.substringAfterLast('/')
        name.substringAfterLast('.') to fields(Class.forName(n.substringBeforeLast('/').replace('/', '.') + "." + name.replace('.', '$')))
    }

    private fun misses(want: Map<out Class<*>, Any>, got: (Class<*>) -> Any): List<String> =
        want.mapNotNull { (c, v) -> got(c).takeIf { it != v }?.let { "${c.simpleName} expected $v but was $it" } }

    @Test
    fun everyEnumKeepsItsEntriesInOrder() {
        assertEquals("Kind", listOf("Artist", "Album", "Song", "Genre", "Folder", "Playlist"), Kind.entries.map { it.name })
        assertEquals("Repeat", listOf("Off", "All", "One"), Repeat.entries.map { it.name })
        assertEquals("ReplayGain", listOf("Off", "Track", "Album"), ReplayGain.entries.map { it.name })
        assertEquals("Crossfeed", listOf("Off", "Default", "ChuMoy", "JanMeier"), Crossfeed.entries.map { it.name })
        assertEquals("Tube", listOf("Off", "Low", "Mid", "High"), Tube.entries.map { it.name })
        assertEquals("Output", listOf("System", "BitPerfect", "Usb"), Output.entries.map { it.name })
        assertEquals("ScrobbleService", listOf("LastFm", "ListenBrainz"), ScrobbleService.entries.map { it.name })
        assertEquals("Card", listOf("Present", "Out", "Error", "Held"), Card.entries.map { it.name })
    }

    @Test
    fun everySealedTypeKeepsItsVariantsAndTheirFields() {
        val none = emptyList<String>()
        val want = mapOf(
            ArtRef::class.java to mapOf("Embedded" to listOf("track", "offset", "length"), "Cover" to listOf("track", "name")),
            Listing::class.java to mapOf(
                "Artists" to none, "Albums" to none, "Songs" to none, "Genres" to none, "Playlists" to none,
                "ArtistAlbums" to listOf("artist"), "ArtistTracks" to listOf("artist"), "AlbumTracks" to listOf("album"),
                "GenreAlbums" to listOf("genre"), "GenreTracks" to listOf("genre"), "PlaylistTracks" to listOf("playlist"),
                "Folder" to listOf("folder"), "FolderTracks" to listOf("folder"), "Search" to listOf("query", "kind"),
                "Favorites" to none, "Pins" to none, "History" to none, "NewAlbums" to none, "Queue" to none,
            ),
            TrackSet::class.java to mapOf("Ids" to listOf("ids"), "Listed" to listOf("listing", "generation")),
            EngineEvent::class.java to mapOf(
                "Started" to listOf("track"), "Ended" to listOf("track"), "Failed" to listOf("track", "message"),
                "OutputLost" to none, "RouteTimeout" to none, "FormatChanged" to listOf("input", "output"),
                "RouteChanged" to listOf("route", "resampled"), "Xruns" to listOf("count"), "QueueChanged" to none,
                "Scan" to listOf("progress"),
            ),
        )
        assertEquals(emptyList<String>(), misses(want) { variants(it) })
    }

    @Test
    fun everyCodecDataClassKeepsItsFieldsInOrder() {
        val want = mapOf(
            Row::class.java to listOf("id", "kind", "title", "sub", "art", "no", "durationMs", "year", "album", "count", "count2", "writable"),
            Page::class.java to listOf("generation", "offset", "total", "rows", "revision"),
            PlayerState::class.java to listOf(
                "queueSize", "queueRevision", "index", "track", "origin", "playing", "anchor", "shuffle", "repeat", "route",
                "resampled", "input", "output", "xruns",
            ),
            Anchor::class.java to listOf("frame", "rate", "elapsedNanos", "running", "durationMs"),
            Sound::class.java to listOf(
                "gapless", "crossfadeMs", "replayGain", "preampDb", "crossfeed", "width", "tube", "output", "volume", "volumePct",
                "volumeStepPct",
            ),
            Format::class.java to listOf("rate", "bits", "channels"),
            TrackInfo::class.java to listOf("path", "lines"),
            TrackIds::class.java to listOf("artists", "album", "genre", "folders"),
            Root::class.java to listOf("id", "uri", "available"),
            ScanProgress::class.java to listOf("running", "found", "failed"),
            LibraryState::class.java to listOf("card", "first"),
            Outputs::class.java to listOf("offered", "dac", "held", "asks", "usbWhy", "dop"),
            ScrobbleView::class.java to listOf(
                "lastFm", "lastFmUser", "listenBrainz", "listenBrainzUser", "queued", "lastFmNote", "listenBrainzNote", "signingIn",
                "appKey",
            ),
        )
        assertEquals(emptyList<String>(), misses(want) { fields(it) })
    }
}
