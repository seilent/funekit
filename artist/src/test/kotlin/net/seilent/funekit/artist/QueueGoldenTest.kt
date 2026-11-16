package net.seilent.funekit.artist

import net.seilent.funekit.core.PlayEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset

class QueueGoldenTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_800_000_000L
    private val lastFm = setOf(Accounts.LAST_FM)
    private val m1 = "00000000-0000-4000-8000-000000000001"
    private val m2 = "00000000-0000-4000-8000-000000000002"

    private fun golden(name: String): String = String(QueueGoldenTest::class.java.getResourceAsStream("/golden/$name")!!.readBytes(), Charsets.UTF_8)

    private fun copy(set: String, vararg names: String): File = tmp.newFolder().also { d -> names.forEach { File(d, it).writeText(golden("$set/$it")) } }

    private fun added(text: String, next: Long, line: String): String {
        val rest = text.substringAfter('\n')
        return "next\t$next\n$rest$line\n"
    }

    private fun entry(seq: Long) = PlayEntry(seq, now - 86400 + seq * 60, 240_000, "Track $seq", "Artist", listOf("Artist"), emptyList(), "Album", zoneS = 0)

    @Test
    fun theGoldenQueueLoadsAndCommitsToFuneCompsLines() {
        val dir = copy("scrobble", ScrobbleQueue.QUEUE, "sent.lastfm", "sent.listenbrainz")
        val q = ScrobbleQueue(dir)
        assertEquals(4L, q.cursor(Accounts.LAST_FM))
        assertEquals(0L, q.cursor(Accounts.LISTEN_BRAINZ))
        assertEquals(22L, q.logSeq(2L or Long.MIN_VALUE))
        assertEquals(null, q.logSeq(1L or Long.MIN_VALUE))
        val l = q.add(Listen(now, "After", "Load"), lastFm)!!
        assertEquals(added(golden("scrobble/queue.tsv"), 23, l.line()), File(dir, ScrobbleQueue.QUEUE).readText())
        assertEquals("a load and an add leave sent alone", golden("scrobble/sent.lastfm"), File(dir, "sent.lastfm").readText())
    }

    private fun captured(seq: Long, zoneS: Int? = 0, exposed: Boolean? = null) = PlayEntry(
        seq, now - 86400 + seq * 60, 240_000, "Track $seq", "Artist ${seq % 5}", listOf("Artist ${seq % 5}"),
        if (seq % 3 == 0L) listOf(m1) else emptyList(), "Album ${seq % 4}", "Album Artist", (seq % 12).toInt() + 1, zoneS = zoneS, exposed = exposed,
    )

    @Test
    fun theCaptureSequenceWritesTheGoldenQueueAndSentBytes() {
        val dir = tmp.newFolder()
        val both = setOf(Accounts.LAST_FM, Accounts.LISTEN_BRAINZ)
        val q = ScrobbleQueue(dir)
        q.add(Listen(now - 7200, "Ra\tdio", "Air\nbag", "OK\rComputer", 240, albumArtist = "Radio\thead", trackNo = 3, mbids = listOf(m1, m2)), both)
        q.add(Listen(now - 7000, "Plain", "Song"), both)
        for (log in 1L..17L) q.addLog(log or Long.MIN_VALUE, listOf(captured(log * 10)), both, ZoneOffset.UTC, now)
        q.addLog(2L or Long.MIN_VALUE, listOf(captured(21, zoneS = 32400, exposed = true), captured(22, zoneS = null, exposed = true)), both, ZoneOffset.UTC, now)
        q.addLog(5L or Long.MIN_VALUE, listOf(captured(50, exposed = true)), both, ZoneOffset.UTC, now)
        q.sent(Accounts.LAST_FM, 4)
        assertEquals(golden("scrobble/queue.tsv"), File(dir, ScrobbleQueue.QUEUE).readText())
        assertEquals(golden("scrobble/sent.lastfm"), File(dir, "sent.lastfm").readText())
        assertEquals(golden("scrobble/sent.listenbrainz"), File(dir, "sent.listenbrainz").readText())
    }

    @Test
    fun theLegacyLinesAreRewrittenAsV2AndAnOrphanMarkIsGone() {
        val dir = tmp.newFolder()
        File(dir, ScrobbleQueue.QUEUE).writeText(golden("queue-in.tsv"))
        File(dir, "sent.lastfm").writeText("1\n")
        ScrobbleQueue(dir).add(Listen(now, "After", "Load"), lastFm)
        assertEquals(golden("queue-in/queue.tsv"), File(dir, ScrobbleQueue.QUEUE).readText())
        assertEquals("a load and an add leave sent alone", golden("queue-in/sent.lastfm"), File(dir, "sent.lastfm").readText())
    }

    @Test
    fun aFirstLineThatIsAListenIsSkipped() {
        val dir = tmp.newFolder()
        File(dir, ScrobbleQueue.QUEUE).writeText("v2\t1\t1799990000\t\tA\tT\t\t\t\t\nv2\t2\t1799990300\t\tB\tU\t\t\t\t\n")
        File(dir, "sent.lastfm").writeText("1\n")
        val q = ScrobbleQueue(dir)
        assertEquals(1, q.size)
        assertEquals(3L, q.add(Listen(now, "After", "Load"), lastFm)!!.id)
        assertEquals("next\t4\nv2\t2\t1799990300\t\tB\tU\t\t\t\t\nv2\t3\t1800000000\t\tAfter\tLoad\t\t\t\t\n", File(dir, ScrobbleQueue.QUEUE).readText())
    }

    @Test
    fun theOwnRecordAndTheReadBoundWriteFuneCompsBytes() {
        val dir = tmp.newFolder()
        val r = OwnRecord(dir, Accounts.LAST_FM, {}, {})
        r.load(2, now)
        r.sent(listOf(Listen(now - 600, "Ra\tdio", "Air\nbag", id = 3), Listen(now - 300, "Plain", "Song", id = 4)), now)
        r.moved(4, now)
        val b = ReadBound(dir, Accounts.LAST_FM, {})
        b.failed(5, now * 1000)
        b.failed(5, now * 1000 + 60_000)
        assertEquals(golden("own/own.lastfm"), File(dir, "own.lastfm").readText())
        assertEquals(golden("own/check.lastfm"), File(dir, "check.lastfm").readText())
    }

    @Test
    fun theGoldenOwnRecordAndReadBoundLoadAsTheyAre() {
        val dir = copy("own", "own.lastfm", "check.lastfm")
        val r = OwnRecord(dir, Accounts.LAST_FM, {}, {})
        r.load(4, now)
        assertEquals(now, r.from)
        assertEquals(listOf(3L, 4L), r.lines.map { it.id })
        assertTrue(r.shifts)
        assertEquals(golden("own/own.lastfm"), File(dir, "own.lastfm").readText())
        assertFalse(ReadBound(dir, Accounts.LAST_FM, {}).failed(5, now * 1000 + 120_000))
        assertEquals("5\t3\t${now * 1000}\n", File(dir, "check.lastfm").readText())
    }

    @Test
    fun anInstalledVc9QueueReads() {
        val dir = copy("vc9/scrobble", ScrobbleQueue.QUEUE, "sent.lastfm", "sent.listenbrainz")
        val vc9 = golden("vc9/scrobble/queue.tsv")
        val q = ScrobbleQueue(dir)
        assertEquals(12, q.size)
        assertEquals(12, q.after(Accounts.LISTEN_BRAINZ).size)
        assertEquals(4L, q.cursor(Accounts.LAST_FM))
        assertEquals(listOf(21L, 30L, 40L, 50L, 60L, 70L, 80L, 90L), (2L..9L).map { q.logSeq(it or Long.MIN_VALUE) })
        val l = q.add(Listen(now, "After", "Load"), lastFm)!!
        assertEquals(13L, l.id)
        val written = File(dir, ScrobbleQueue.QUEUE).readText()
        assertEquals(added(vc9, 14, l.line()), written)
        assertTrue(written.lines().none { it.startsWith("${Listen.MARK}\t") })
        for (log in 10L..18L) q.addLog(log or Long.MIN_VALUE, listOf(entry(log * 10)), lastFm, ZoneOffset.UTC, now)
        assertEquals(16, File(dir, ScrobbleQueue.QUEUE).readLines().count { it.startsWith("${ScrobbleQueue.DAP}\t") })
        val again = ScrobbleQueue(dir)
        assertEquals(null, again.logSeq(3L or Long.MIN_VALUE))
        assertEquals(21L, again.logSeq(2L or Long.MIN_VALUE))
        assertEquals(180L, again.logSeq(18L or Long.MIN_VALUE))
    }

    @Test
    fun aSeenLogWritesItsCursorUnderDap() {
        val dir = tmp.newFolder()
        val q = ScrobbleQueue(dir)
        q.add(Listen(now, "A", "T"), lastFm)
        q.seen(0xA1, 5)
        assertTrue(File(dir, ScrobbleQueue.QUEUE).readLines().contains("${ScrobbleQueue.DAP}\ta1\t5"))
        assertEquals(1L, q.addLog(0xA1, listOf(entry(1)), lastFm, ZoneOffset.UTC, now))
        assertEquals(1, ScrobbleQueue(dir).size)
    }

    @Test
    fun aNativePlayStartsInUtcWhateverTheZone() {
        val e = PlayEntry(3, 1_700_000_011, 200_000, "a", "b", listOf("b"), emptyList(), null, null, 2, 0, liveSeen = false)
        assertEquals(1_700_000_011L, Listen.of(e, ZoneId.of("Asia/Tokyo"))?.startS)
        val native = PlayEntry(3, 1_700_000_011, 200_000, "a", "b", listOf("b"), emptyList(), null, null, 2, 0)
        assertEquals(1_700_000_011L, Listen.of(native, ZoneId.of("Asia/Tokyo"))?.startS)
    }
}
