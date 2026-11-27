package net.seilent.funekit.fake

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.seilent.funekit.core.Lrc
import net.seilent.funekit.core.Section
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.EngineEvent
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.ScanProgress
import net.seilent.funekit.api.TrackInfo
import net.seilent.funekit.api.allRows
import net.seilent.funekit.api.engine.Dac
import net.seilent.funekit.api.engine.Format
import net.seilent.funekit.api.engine.Output
import net.seilent.funekit.api.engine.Outputs
import net.seilent.funekit.api.events
import net.seilent.funekit.api.history
import net.seilent.funekit.api.newAlbums
import net.seilent.funekit.api.pins
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeTest {
    private fun TestScope.now() = testScheduler.currentTime * 1_000_000

    private fun TestScope.fake(set: FakeSet = FakeSet.Main) = Fake(backgroundScope, { now() }, set, 40, 5_000)

    private fun id(title: String) = Fixture.ALBUMS.flatMap { it.tracks }.first { it.title == title }.id

    private fun album(title: String) = Fixture.ALBUMS.first { it.title == title }.id

    @Test
    fun pagesArriveAfterTheLatency() = runTest {
        val view = fake().library.view()
        val t = testScheduler.currentTime
        view.page(Listing.Albums, 0, 20)
        assertEquals(40L, testScheduler.currentTime - t)
    }

    @Test
    fun aDefaultFakeTakesPinsWithNoCap() = runTest {
        val f = fake()
        assertNull(f.pinCap)
        val view = f.library.view()
        val before = view.pins(0, 0).total
        val free = view.allRows(Listing.Songs).map { it.id }.filter { !view.pinned(Kind.Song, it) }.take(9)
        assertEquals(9, free.size)
        for (id in free) f.quickplay.pin(Kind.Song, id)
        assertTrue(free.all { view.pinned(Kind.Song, it) })
        assertEquals(before + 9, view.pins(0, 0).total)
    }

    @Test
    fun aFakeOfferingNoQuickplayRefusesPinsAndMovesNoRevision() = runTest {
        val f = Fake(backgroundScope, { now() }, FakeSet.Main, 40, 5_000, offersQuickplay = false)
        assertFalse(f.quickplay.available.value)
        assertTrue(fake().quickplay.available.value)
        val song = f.library.view().page(Listing.Songs, 0, 1).rows.single().id
        val r = f.quickplay.revision.value
        assertTrue(runCatching { f.quickplay.pin(Kind.Song, song) }.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(runCatching { f.quickplay.unpin(Kind.Song, song) }.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(r, f.quickplay.revision.value)
    }

    @Test
    fun viewIsCachedPerSnapshot() = runTest {
        val f = fake()
        val view = f.library.view()
        assertSame(view, f.library.view())
        f.library.rescan()
        f.library.generation.first { it > view.generation }
        assertSame(f.library.view(), f.library.view())
        assertTrue(f.library.view() !== view)
    }

    @Test
    fun aViewByGenerationAnswersUntilItsLease() = runTest {
        val f = fake()
        val g = f.library.generation.value
        f.library.rescan()
        f.library.generation.first { it > g }
        assertEquals(g, f.library.view(g).page(Listing.Albums, 0, 1).generation)
        assertSame(f.library.view(), f.library.view(f.library.generation.value))
        advanceTimeBy(5_000)
        assertTrue(runCatching { f.library.view(g).page(Listing.Albums, 0, 1) }.exceptionOrNull() is Expired)
        assertTrue(runCatching { f.library.view(g + 10).page(Listing.Albums, 0, 1) }.exceptionOrNull() is Expired)
        assertEquals(f.library.generation.value, f.library.view(g).page(Listing.Queue, 0, 1).generation)
    }

    @Test
    fun aFormatAboveTheCeilingThatTheDownsamplerRefusesHasNoOutput() = runTest {
        val p = fake().player
        assertNull(p.outputOf(Format(192000, 24, 6), Output.System))
        assertNull(p.outputOf(Format(180000, 24, 2), Output.System))
        assertNull(p.outputOf(Format(400000, 24, 2), Output.Usb))
        assertEquals(Format(192000, 24, 6), p.outputOf(Format(192000, 24, 6), Output.Usb))
        assertEquals(Format(180000, 24, 2), p.outputOf(Format(180000, 24, 2), Output.BitPerfect))
    }

    @Test
    fun aMonoInputIsLiftedToTwoChannelsAtEveryRateOnSystemAndUsb() = runTest {
        val p = fake().player
        assertEquals(Format(96000, 24, 2), p.outputOf(Format(192000, 24, 1), Output.System))
        assertEquals(Format(88200, 24, 2), p.outputOf(Format(352800, 24, 1), Output.System))
        assertEquals(Format(96000, 24, 2), p.outputOf(Format(96000, 24, 1), Output.System))
        assertEquals(Format(44100, 16, 2), p.outputOf(Format(44100, 16, 1), Output.System))
        assertEquals(Format(192000, 24, 2), p.outputOf(Format(192000, 24, 1), Output.Usb))
        assertEquals(Format(44100, 16, 1), p.outputOf(Format(44100, 16, 1), Output.BitPerfect))
    }

    @Test
    fun aTrackTheNewRouteRefusesFailsAndSkipsToTheNextWithOneRouteChange() = runTest {
        val f = fake()
        val refused = id(Fixture.REFUSED)
        val next = id("Brass Corridor")
        f.settings.update(f.settings.sound.value.copy(output = Output.Usb))
        f.player.play(listOf(refused, next), 0, 0)
        assertEquals(refused, f.player.state.value.track)
        assertEquals(Format(192000, 24, 6), f.player.state.value.output)
        val events = mutableListOf<EngineEvent>()
        val watch = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.player.events.toList(events) }
        f.settings.update(f.settings.sound.value.copy(output = Output.System))
        val st = f.player.state.value
        assertEquals(next, st.track)
        assertEquals(1, st.index)
        assertTrue(st.playing)
        assertEquals(Format(96000, 24, 2), st.output)
        assertTrue(st.resampled)
        assertEquals(listOf(EngineEvent.Failed(refused, "rate above the ceiling")), events.filterIsInstance<EngineEvent.Failed>())
        assertEquals(listOf(EngineEvent.RouteChanged(Output.System, true)), events.filterIsInstance<EngineEvent.RouteChanged>())
        watch.cancel()
    }

    @Test
    fun aDsdTrackPlaysAsPcmResampledOnSystemDirectOnUsbAndAsItsInputOnBitPerfect() = runTest {
        val f = fake(FakeSet.DemoProps)
        val dsd = f.library.view().allRows(Listing.Songs).single { it.title == "短いDSD" }.id
        f.player.play(listOf(dsd), 0, 0)
        var st = f.player.state.value
        assertTrue(st.playing)
        assertEquals(Format(352800, 24, 2), st.input)
        assertEquals(Format(88200, 24, 2), st.output)
        assertTrue(st.resampled)
        assertEquals(352800, st.anchor.rate)
        f.settings.update(f.settings.sound.value.copy(output = Output.Usb))
        st = f.player.state.value
        assertEquals(Output.Usb, st.route)
        assertEquals(st.input, st.output)
        assertFalse(st.resampled)
        f.settings.update(f.settings.sound.value.copy(output = Output.BitPerfect))
        st = f.player.state.value
        assertEquals(Output.BitPerfect, st.route)
        assertEquals(Format(352800, 24, 2), st.output)
        assertFalse(st.resampled)
    }

    @Test
    fun removingARootDuringAScanPublishesWithoutIt() = runTest {
        val f = fake()
        val g = f.library.generation.value
        f.library.rescan()
        advanceTimeBy(100L)
        f.library.removeRoot(Fixture.ROOTS[1])
        f.library.generation.first { it > g + 1 }
        assertEquals(1, f.library.view().page(Listing.Folder(), 0, 10).total)
        assertTrue(f.library.view().allRows(Listing.Albums).none { it.title == "Paper Lanterns" })
    }

    @Test
    fun aDropAtRescanLeavesTheAlbumOutOfTheNextGenerationOnly() = runTest {
        val f = fake()
        val a = album("Paper Lanterns")
        val g = f.library.generation.value
        f.dropAtRescan(a)
        assertEquals("the drop waits for the rescan", g, f.library.generation.value)
        assertNotNull("the album is listed until the rescan", f.library.view().indexOf(Listing.Albums, Kind.Album, a))
        f.library.rescan()
        f.library.generation.first { it > g }
        val v = f.library.view()
        assertNull(v.indexOf(Listing.Albums, Kind.Album, a))
        assertEquals(0, v.page(Listing.AlbumTracks(a), 0, 0).total)
        assertNotNull("the generation before keeps it", f.library.view(g).indexOf(Listing.Albums, Kind.Album, a))
        val next = f.library.generation.value
        f.library.rescan()
        f.library.generation.first { it > next }
        assertNull("a later rescan keeps it out", f.library.view().indexOf(Listing.Albums, Kind.Album, a))
    }

    @Test
    fun aNotReadCountHoldsUntilADoneScanReadsEverything() = runTest {
        val f = fake()
        val seen = mutableListOf<ScanProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.library.scan.toList(seen) }
        suspend fun scan(): List<ScanProgress> {
            val from = seen.size
            f.library.rescan()
            f.library.scan.first { !it.running && seen.drop(from).any { p -> p.running } }
            return seen.drop(from)
        }
        f.notReadNextScan(3)
        assertEquals("a scan that could not read 3 ends with 3", 3, scan().last().notRead)
        f.failNextScan()
        val failed = scan()
        assertEquals("a running scan keeps the last count", listOf(3), failed.filter { it.running }.map { it.notRead }.distinct())
        assertTrue("premise: the scan failed", failed.last().failed)
        assertEquals("a failed scan keeps the last count", 3, failed.last().notRead)
        f.notReadNextScan(5)
        f.failNextScan()
        val again = scan().last()
        assertTrue("premise: the scan failed", again.failed)
        assertEquals("a failed scan takes no hook", 3, again.notRead)
        assertEquals("the next done scan takes the hook", 5, scan().last().notRead)
        assertEquals("a scan that reads everything ends with 0", 0, scan().last().notRead)
    }

    @Test
    fun aScopedBusyThrowsOnlyForItsListing() = runTest {
        val f = fake()
        val v = f.library.view()
        val a = album("Paper Lanterns")
        f.busyNext("scan", Listing.Albums)
        v.page(Listing.Songs, 0, 1)
        v.trackIds(id("Slow Comet"))
        val e = runCatching { v.indexOf(Listing.Albums, Kind.Album, a) }.exceptionOrNull()
        assertTrue("a read of the listing throws, $e", e is Busy && e.text == "scan")
        assertNotNull("the busy is thrown once", v.indexOf(Listing.Albums, Kind.Album, a))
        f.busyNext("any")
        assertTrue("with no listing the first read throws", runCatching { v.page(Listing.Songs, 0, 1) }.exceptionOrNull() is Busy)
    }

    @Test
    fun commandsAreSerialized() = runTest {
        val f = fake()
        val track = id("Slow Comet")
        withContext(Dispatchers.Default) {
            (1..200).map { async { f.player.enqueue(listOf(track)) } }.awaitAll()
        }
        assertEquals(200, f.player.state.value.queueSize)
        assertEquals(200, f.library.view().allRows(Listing.Queue).size)
    }

    @Test
    fun shuffleEnqueueLandsAfterTheCurrentTrack() = runTest {
        val f = fake()
        val ids = f.library.view().page(Listing.Songs, 0, 6).rows.map { it.id }
        f.player.play(ids.take(5), 2, 0)
        f.player.shuffle(true)
        f.player.enqueue(listOf(ids[5]))
        val queue = f.library.view().allRows(Listing.Queue).map { it.id }
        assertEquals(ids[2], queue.first())
        assertTrue(ids[5] in queue.drop(1))
        f.player.shuffle(false)
        assertEquals(ids, f.library.view().allRows(Listing.Queue).map { it.id })
    }

    @Test
    fun startOutOfRangeBeginsAtTheFirstSlot() = runTest {
        val f = fake()
        val ids = f.library.view().page(Listing.Songs, 0, 6).rows.map { it.id }
        f.player.play(ids, 99, 0)
        assertEquals(0, f.player.state.value.index)
        assertEquals(ids[0], f.player.state.value.track)
        f.player.shuffle(true)
        f.player.play(ids, -1, 0)
        assertEquals(0, f.player.state.value.index)
        assertEquals(ids.sorted(), f.library.view().allRows(Listing.Queue).map { it.id }.sorted())
        f.player.shuffle(false)
        assertEquals(ids, f.library.view().allRows(Listing.Queue).map { it.id })
    }

    @Test
    fun aResumeAfterTheQueueEndStartsTheLastTrackAgain() = runTest {
        val f = fake()
        val seen = events(f.player)
        val (a, b) = f.library.view().page(Listing.Songs, 0, 2).rows.map { it.id }
        f.player.play(listOf(a, b), 1, 0)
        assertEquals(1, seen.value.count { it == EngineEvent.Started(b) })
        advanceTimeBy(f.player.state.value.durationMs + 1)
        assertFalse(f.player.state.value.playing)
        f.player.resume()
        val st = f.player.state.value
        assertTrue(st.playing)
        assertEquals(1, st.index)
        assertEquals(2, seen.value.count { it == EngineEvent.Started(b) })
    }

    @Test
    fun artIsAPlaceholderPng() = runTest {
        val view = fake().library.view()
        val ref = checkNotNull(view.artRef(id("Slow Comet")))
        val png = checkNotNull(view.art(ref, 4096))
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), png.copyOf(8))
        assertNull(view.art(ArtRef.Embedded(id("Slow Comet"), 0, 1), 4096))
        assertNull(view.artRef(id("Iron Meadow")))
    }

    @Test
    fun mainSetCoversTheEdgeScripts() = runTest {
        val f = fake()
        val view = f.library.view()
        val albums = view.allRows(Listing.Albums)
        val titles = (view.allRows(Listing.Artists) + albums + view.allRows(Listing.Songs)).map { it.title }
        assertTrue(titles.any { it.length > 100 })
        assertTrue(titles.any { t -> t.any { it in '\u3040'..'\u30FF' } })
        assertTrue(titles.any { t -> t.any { it in '\uFF65'..'\uFF9F' } })
        assertTrue(titles.any { t -> t.any { it in '\u4E00'..'\u9FFF' } })
        assertTrue(titles.any { t -> t.any { it in '\u0590'..'\u05FF' } })
        assertTrue(titles.any { t -> t.any { it in '\u0600'..'\u06FF' } })
        assertTrue(albums.any { it.art == null })
        assertTrue(albums.any { it.art is ArtRef.Embedded })
        assertTrue(albums.any { it.art is ArtRef.Cover })
        assertEquals(1, fake(FakeSet.Unavailable).library.roots.value.count { !it.available })
        assertTrue(f.library.roots.value.all { it.available })
    }

    @Test
    fun hugeSearchCoversSongs() = runTest {
        val view = fake(FakeSet.Huge).library.view()
        val hits = view.allRows(Listing.Search("a 0001"))
        assertEquals(10, hits.size)
        assertTrue(hits.all { it.kind == Kind.Song && it.title.startsWith("a 0001") })
        assertTrue(view.allRows(Listing.Search("night")).any { it.kind == Kind.Album })
        assertEquals(hits, view.allRows(Listing.Search("a 0001", Kind.Song)))
        assertTrue(view.allRows(Listing.Search("a 0001", Kind.Album)).isEmpty())
    }

    @Test
    fun seededQuickplayAndNewOrder() = runTest {
        val f = fake()
        val view = f.library.view()
        assertEquals(Fixture.PINS.map { it.kind to it.id }, view.pins(0, 50).rows.map { it.kind to it.id })
        assertEquals(Fixture.HISTORY, view.history(0, 50).rows.map { it.id })
        val newest = Fixture.ALBUMS.maxBy { it.mtime }.id
        assertEquals(newest, view.newAlbums(0, 1).rows.single().id)
        val empty = fake(FakeSet.Empty).library.view()
        assertEquals(0, empty.pins(0, 50).total)
        assertEquals(0, empty.history(0, 50).total)
    }

    @Test
    fun ownedVolumeStartsAtTheSafeLevel() = runTest {
        val f = fake()
        f.settings.update(f.settings.sound.value.copy(output = Output.Usb))
        assertEquals(195, f.settings.sound.value.volume)
    }

    @Test
    fun aDetachedDacLeavesUsbWithNoVolumeAndTheOfferedListWithoutUsb() = runTest {
        val f = fake()
        assertEquals(listOf(Output.System, Output.BitPerfect, Output.Usb), f.settings.outputs.value.offered)
        f.settings.update(f.settings.sound.value.copy(output = Output.Usb))
        f.detachDac()
        assertNull(f.settings.sound.value.volume)
        assertEquals(Output.Usb, f.settings.sound.value.output)
        assertEquals(Outputs(listOf(Output.System, Output.BitPerfect)), f.settings.outputs.value)
        f.settings.setVolume(80)
        assertNull(f.settings.sound.value.volume)
        f.attachDac(Dac(1, 1, "a"), dop = true)
        assertEquals(195, f.settings.sound.value.volume)
        assertEquals(Outputs(listOf(Output.System, Output.BitPerfect, Output.Usb), "fake dac", dop = false), f.settings.outputs.value)
    }

    @Test
    fun browseSearchAndInfo() = runTest {
        val f = fake()
        val view = f.library.view()
        val roots = view.page(Listing.Folder(), 0, 10).rows
        assertEquals(listOf("Music", "Albums"), roots.map { it.title })
        assertEquals(f.library.roots.value.map { it.id }, roots.map { it.id })
        val dirs = view.allRows(Listing.Folder(roots[0].id))
        assertTrue(dirs.isNotEmpty() && dirs.all { it.kind == Kind.Folder })
        assertTrue(view.page(Listing.Folder(dirs[0].id), 0, 50).rows.all { it.kind == Kind.Song })
        val hits = view.allRows(Listing.Search("night"))
        assertTrue(hits.any { it.kind == Kind.Album && it.title == "Night Bus Diaries" })
        assertTrue(hits.any { it.kind == Kind.Song && it.title == "Night Frequency" })
        val amber = album("Amber Field")
        assertEquals(4, view.page(Listing.AlbumTracks(amber), 0, 50).total)
        assertEquals(
            TrackInfo(
                "Music/Amber Field/02 Slow Comet.flac",
                listOf("Amber Field", "Folk", "1997", "flac", "96000 hz", "24 bit", "2 ch", "4608 kbps", "2:35", "89.3 mb"),
            ),
            view.trackInfo(id("Slow Comet")),
        )
        assertTrue(checkNotNull(f.lyrics.lyrics(id("Slow Comet"))).startsWith("[00:12.00]"))
        assertNull(f.lyrics.lyrics(id("Porchlight")))
    }

    @Test
    fun aCarryingFakeServesItsLinesWithItsOffsetAndBumpsTheRevision() = runTest {
        val f = Fake(backgroundScope, { now() }, FakeSet.Main, 40, 5_000, lyricsCarried = true)
        val t = id("Slow Comet")
        val stored = Lrc.parse(checkNotNull(Fixture.shelf(FakeSet.Main).lyrics[t]).toByteArray(Charsets.UTF_8)).lines
        f.lyrics.setOffset(t, 500)
        val c = checkNotNull(f.lyrics.copy(t))
        assertEquals(stored.map { maxOf(0L, it.ms - 500) }, Lrc.parse(c.text.toByteArray(Charsets.UTF_8)).lines.map { it.ms })
        assertEquals(500, c.carriedMs)
        assertEquals(1L, f.lyrics.revision.value)
        f.lyrics.setOffset(id("ひこうき雲"), 250)
        assertEquals("a set on another track bumps the revision", 2L, f.lyrics.revision.value)
        assertNull("a default fake carries no offset", checkNotNull(fake().lyrics.copy(t)).carriedMs)
    }

    private suspend fun letters(view: LibraryView, listing: Listing): String =
        view.sections(listing).toSet().sorted().joinToString("") { Section.letter(it.toInt()) }

    @Test
    fun demoHasTheShapeOfTheFuneOsDemoLibrary() = runTest {
        val view = fake(FakeSet.Demo).library.view()
        assertEquals(1291, view.page(Listing.Songs, 0, 1).total)
        assertEquals(171, view.page(Listing.Albums, 0, 1).total)
        val artists = view.allRows(Listing.Artists)
        assertEquals(50, artists.size)
        assertEquals("#abdefgijklmnrstvzかやらわ漢", letters(view, Listing.Artists))
        assertEquals(2, artists.count { it.count == 8 })
        assertEquals("#abcdefiklmnorstuvzあかさたなはまやらわ漢", letters(view, Listing.Songs))
        assertEquals("#abcdefiklmnprstuvzあかたなはまらわ漢", letters(view, Listing.Albums))
        val albums = view.allRows(Listing.Albums)
        assertTrue(albums.any { it.art == null })
        assertTrue(albums.any { t -> t.title.any { it in '\u30A0'..'\u30FF' } })
        val eight = albums.first { it.count == 8 }
        val tracks = view.allRows(Listing.AlbumTracks(eight.id))
        assertEquals((1..8).toList(), tracks.map { it.no })
        assertTrue(tracks.all { it.durationMs == 180_000L })
        assertEquals(listOf(false, false, false, false, true, false, false, false), tracks.map { "mp3" in checkNotNull(view.trackInfo(it.id)).lines })
        assertEquals(53, Demo.LONG_TITLE.length)
        assertEquals(52, Demo.LONG_ARTIST.length)
        assertEquals(44, Demo.LONG_ALBUM.length)
        assertEquals(47, Demo.LONG_PLAYLIST.length)
        assertTrue(artists.any { it.title == Demo.LONG_ARTIST })
        assertTrue(albums.any { it.title == Demo.LONG_ALBUM })
    }

    @Test
    fun demoFoldersHoldSubfoldersBeforeSongs() = runTest {
        val view = fake(FakeSet.Demo).library.view()
        val root = view.page(Listing.Folder(), 0, 10).rows.single()
        val top = view.allRows(Listing.Folder(root.id))
        assertEquals(47, top.size)
        assertTrue(top.all { it.kind == Kind.Folder })
        val flac = top.single { it.title == "flac" }
        val inFlac = view.allRows(Listing.Folder(flac.id))
        assertEquals(listOf(Kind.Folder, Kind.Song, Kind.Song), inFlac.map { it.kind })
        assertEquals("live", inFlac[0].title)
        assertEquals(listOf("Dialogue", "Early Frost", "夜を渡る"), view.allRows(Listing.FolderTracks(flac.id)).map { it.title })
        assertEquals(3, flac.count)
    }

    @Test
    fun demoTrackInfoFollowsTheFuneOsLines() = runTest {
        val view = fake(FakeSet.Demo).library.view()
        val songs = view.allRows(Listing.Songs)
        suspend fun info(title: String) = view.trackInfo(songs.single { it.title == title }.id)
        assertEquals(
            TrackInfo("Music/flac/01.flac", listOf("Field Album", "Ambient", "2019", "flac", "44100 hz", "16 bit", "1 ch", "706 kbps", "0:01", "0.1 mb")),
            info("Early Frost"),
        )
        assertEquals(
            TrackInfo("Music/mp3/v1.mp3", listOf("Lineage", "Jazz", "1999", "mp3", "44100 hz", "1 ch", "128 kbps", "0:01", "0.0 mb")),
            info("Old Signal"),
        )
        assertEquals(
            TrackInfo(
                "Music/flac/live/Evening_Sessions_Recorded_Live_In_Nara/Extra/duo.flac",
                listOf("Parallels", "flac", "44100 hz", "16 bit", "1 ch", "706 kbps", "0:01", "0.1 mb"),
            ),
            info("Dialogue"),
        )
    }

    @Test
    fun demoPropsAddsTheDsdTrack() = runTest {
        val view = fake(FakeSet.DemoProps).library.view()
        val songs = view.allRows(Listing.Songs)
        assertEquals(1292, songs.size)
        val root = view.page(Listing.Folder(), 0, 10).rows.single()
        assertEquals(48, view.page(Listing.Folder(root.id), 0, 100).total)
        assertEquals(
            TrackInfo("Music/dsd/tagged.dsf", listOf("Bitplane", "2018", "dsf", "2822400 hz", "dsd64", "2 ch", "5645 kbps", "0:01", "0.7 mb")),
            view.trackInfo(songs.single { it.title == "短いDSD" }.id),
        )
        assertEquals(0, fake(FakeSet.DemoNoPlaylists).library.view().page(Listing.Playlists, 0, 1).total)
    }

    @Test
    fun aTrackCreditedToTwoArtistsLinksBoth() = runTest {
        val view = fake(FakeSet.Demo).library.view()
        val artists = view.allRows(Listing.Artists)
        val song = view.allRows(Listing.Songs).single { it.title == "Early Frost" }
        assertEquals("Aster / Brook", song.sub)
        val ids = checkNotNull(view.trackIds(song.id))
        assertEquals(listOf("Aster", "Brook"), ids.artists.map { a -> artists.single { it.id == a }.title })
        for (a in ids.artists) {
            assertEquals(listOf(song.id), view.allRows(Listing.ArtistTracks(a)).map { it.id })
            assertEquals(1, artists.single { it.id == a }.count2)
        }
        assertEquals("Aster", view.allRows(Listing.Albums).single { it.title == "Field Album" }.sub)
        val duo = view.allRows(Listing.Songs).single { it.title == "Dialogue" }
        assertEquals(2, checkNotNull(view.trackIds(duo.id)).artists.size)
    }

    @Test
    fun demoSeedsPlaylistsFavoritesHistoryAndPins() = runTest {
        val view = fake(FakeSet.Demo).library.view()
        val lists = view.allRows(Listing.Playlists)
        assertEquals(listOf(Demo.LONG_PLAYLIST, "Night Drive", "雨の歌"), lists.map { it.title })
        assertEquals(listOf(3, 12, 3), lists.map { it.count })
        assertEquals(listOf(false, true, true), lists.map { it.writable })
        assertEquals(5, view.page(Listing.Favorites, 0, 1).total)
        assertEquals(10, view.page(Listing.History, 0, 1).total)
        assertEquals(listOf(Kind.Album, Kind.Artist, Kind.Song, Kind.Album), view.allRows(Listing.Pins).map { it.kind })
    }

    @Test
    fun demoLyricsCarryTheFuneOsShapes() = runTest {
        val f = fake(FakeSet.Demo)
        suspend fun text(shape: String) = checkNotNull(f.lyrics.lyrics(checkNotNull(f.lyricTrack(shape)) { shape })) { shape }
        val stamp = Regex("^\\[\\d\\d:\\d\\d\\.\\d\\d]")
        val short = text("short").lines()
        assertEquals("[ti:demo]", short[0])
        assertEquals(listOf("[00:00.00]", "[00:02.00]", "[00:04.00]", "[00:09.00]", "[00:12.00]", "[00:15.00]", "[00:18.00]", "[00:21.00]", "[00:24.00]"), short.drop(1).map { it.take(10) })
        assertEquals(66, text("long").lines()[2].drop(10).length)
        assertEquals("[01:00.00]", text("before").take(10))
        assertTrue(text("idtag").lines().none { stamp.containsMatchIn(it) })
        val plain = text("plain").lines()
        assertTrue(plain.none { stamp.containsMatchIn(it) })
        assertEquals(28, plain.drop(2).count { it.isNotEmpty() })
        assertEquals(5, plain.count { it.isEmpty() })
        assertEquals(9, text("ar").lines().size)
        assertEquals("[00:04.00]", text("arend").lines()[8].take(10))
        assertNull(f.lyricTrack("none"))
        val demo = f.library.view().allRows(Listing.Songs).count { "A lyric line that runs on past the right edge of the row" in (f.lyrics.lyrics(it.id) ?: "") }
        assertEquals(1, demo)
    }

    @Test
    fun playlistsMergeByFoldedBytesWithTheTreeListBeforeAUserListOfItsName() {
        val store = Store(FakeSet.Main)
        store.updatePlaylists { docs, r ->
            docs + listOf(
                PlaylistDoc(100L, "commute", emptyList(), true, r),
                PlaylistDoc(101L, "after hours", emptyList(), true, r),
                PlaylistDoc(102L, "ｚｅｎ", emptyList(), true, r),
                PlaylistDoc(103L, "𝄞 clef", emptyList(), true, r),
            )
        }
        val rows = Catalog(FakeSet.Main, emptyList()).rows(Listing.Playlists, store)
        val shown = (0 until rows.size).map { rows.row(it).title to rows.id(it) }
        val tree = shown.first { it.first == "Commute" }.second
        assertEquals(
            "folded names in byte order, raw past ASCII, the tree list first on a tie",
            listOf("after hours", "Commute", "commute", "Late Night", "お気に入り", "ｚｅｎ", "𝄞 clef"),
            shown.map { it.first },
        )
        assertEquals("the user list follows the tree list of its name", shown.indexOfFirst { it.second == tree } + 1, shown.indexOfFirst { it.second == 100L })
    }
}
