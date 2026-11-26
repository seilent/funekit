package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtBlockFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val golden = String(ArtBlockFileTest::class.java.getResourceAsStream("/golden/art/artblock.tsv")!!.readBytes(), Charsets.UTF_8)
    private val captured = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000

    private fun copy() = File(tmp.newFolder(), "artblock.tsv").also { it.writeText(golden) }

    @Test
    fun theGoldenFileLoadsAndSavesByteEqualWithALedgerAndWithNone() {
        for (ledger in listOf(PushLedger(), null)) {
            val f = copy()
            val logs = ArrayList<String>()
            ArtBlocks(f, { captured - 2 * day }, ledger) { logs += it }.save()
            assertEquals(golden, f.readText())
            assertEquals(emptyList<String>(), logs)
        }
    }

    @Test
    fun theGoldenLedgerLinesDecode() {
        val ledger = PushLedger()
        ArtBlocks(copy(), { captured }, ledger = ledger)
        assertEquals(listOf("Name L" to ImgOrigin.PHONE, "Name N" to ImgOrigin.LIBRARY), ledger.pushes("v1").map { it.name to it.origin })
        assertEquals(listOf("Name I"), ledger.held("v1").map { it.name })
        assertEquals(listOf("Name J" to ImgOrigin.LIBRARY), ledger.upgrades("r1").map { it.name to it.origin })
    }

    @Test
    fun aClearedDropOlderThan90DaysIsDroppedOnLoad() {
        for (ledger in listOf(PushLedger(), null)) {
            val f = copy()
            ArtBlocks(f, { captured }, ledger = ledger).save()
            assertEquals(golden.lines().filter { !it.startsWith("drop\tname:name g\t") }.joinToString("\n"), f.readText())
        }
    }

    @Test
    fun otherKindsPassThroughWithNoLedger() {
        val f = File(tmp.root, "lines.tsv")
        val logs = ArrayList<String>()
        val b = ArtBlocks(f)
        b.add(ArtBlocks.Block("k2", null, "deezer", "y", "s", "B", 3))
        b.add(ArtBlocks.Reject("m2", "m2", "B", "B", 4))
        b.save()
        val kinds = "held\tA\t\tv1\nupgradedevice\tv1\nupgrade\tk\tA\t\t5\tv1\tphone\npush\tA\t\t\norigin\tA\t\tlibrary\n"
        f.appendText("block\tk\t\tdeezer\tx\ts\tA\t1\tdev\nbogus\tA\n$kinds")
        val again = ArtBlocks(f, log = { logs += it })
        assertEquals(3, again.records().size)
        again.save()
        assertEquals(listOf("artblock skipped line bogus\tA"), logs)
        assertEquals(kinds, f.readLines().filter { it.substringBefore('\t') in PushLedger.KINDS }.joinToString("") { "$it\n" })
    }

    @Test
    fun aBlockDropsTheNamesPushHeldAndUpgradeLines() {
        val f = File(tmp.root, "a.tsv")
        val ledger = PushLedger()
        val b = ArtBlocks(f, ledger = ledger)
        ledger.addPush("Name A", null)
        ledger.hold("Name A", null, "v1")
        ledger.upgraded("mbid:00000000-0000-4000-8000-000000000001", "Name A", null, 5, "v1")
        ledger.addPush("Other", null)
        b.add(ArtBlocks.Block("name:name a", null, "deezer", "1", "", "name a", 6))
        assertEquals(listOf("Other"), ledger.pushes("v1").map { it.name })
        assertEquals(emptyList<PushLedger.Held>(), ledger.held("v1"))
        assertEquals(emptyList<PushLedger.Upgrade>(), ledger.upgrades("v1"))
        b.save()
        assertEquals(listOf("block", "upgradedevice", "push"), f.readLines().map { it.substringBefore('\t') })
    }

    @Test
    fun aReusedLedgerHoldsOneCopyAfterTwoLoads() {
        val f = copy()
        val ledger = PushLedger()
        ArtBlocks(f, { captured - 2 * day }, ledger)
        ArtBlocks(f, { captured - 2 * day }, ledger).save()
        assertEquals(listOf("Name L", "Name N"), ledger.pushes("v1").map { it.name })
        assertEquals(listOf("Name I"), ledger.held("v1").map { it.name })
        assertEquals(golden, f.readText())
        ArtBlocks(File(tmp.root, "none.tsv"), ledger = ledger)
        assertEquals(emptyList<PushLedger.Push>(), ledger.pushes("v1"))
        assertEquals(emptyList<PushLedger.Held>(), ledger.held("v1"))
    }

    @Test
    fun aSaveThatCannotRenameThrowsAndLeavesWhatIsAtTheFile() {
        val f = File(tmp.root, "artblock.tsv")
        val b = ArtBlocks(f)
        b.add(ArtBlocks.Block("k2", null, "deezer", "y", "s", "B", 3))
        b.add(ArtBlocks.Reject("m2", "m2", "B", "B", 4))
        f.mkdir()
        assertThrows(StoreException::class.java) { b.save() }
        assertTrue(f.isDirectory)
        f.delete()
        b.save()
        assertEquals(b.records(), ArtBlocks(f).records())
    }

    @Test
    fun aSaveThatCannotWriteItsTmpThrowsAndKeepsTheFileAsSaved() {
        val f = File(tmp.root, "artblock.tsv")
        val b = ArtBlocks(f)
        b.add(ArtBlocks.Block("k2", null, "deezer", "y", "s", "B", 3))
        b.save()
        val saved = f.readText()
        b.add(ArtBlocks.Reject("m2", "m2", "B", "B", 4))
        File(tmp.root, "artblock.tsv.tmp").mkdir()
        assertThrows(StoreException::class.java) { b.save() }
        assertEquals(saved, f.readText())
    }
}
