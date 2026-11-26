package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class StoreWriteTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { tmp.newFolder("store") }

    @Test
    fun aRenameOverADirectoryThrowsAStoreErrorAndLeavesIt() {
        val f = File(dir, "a.txt").also { it.mkdir() }
        val e = assertThrows(StoreException::class.java) { syncedWrite(f, "new", dir) {} }
        assertEquals("rename a.txt.tmp", e.message)
        assertTrue(f.isDirectory)
        assertFalse(File(dir, "a.txt.tmp").exists())
    }

    @Test
    fun aTmpThatCannotBeOpenedThrowsAStoreErrorWithItsCause() {
        val f = File(dir, "a.txt").also { it.writeText("old") }
        File(dir, "a.txt.tmp").mkdir()
        val e = assertThrows(StoreException::class.java) { syncedWrite(f, "new", dir) {} }
        assertEquals("write a.txt.tmp", e.message)
        assertTrue(e.cause is FileNotFoundException)
        assertEquals("old", f.readText())
        assertTrue(File(dir, "a.txt.tmp").isDirectory)
    }

    @Test
    fun aFailedDirSyncThrowsAStoreErrorAfterTheFileLanded() {
        val f = File(dir, "a.txt").also { it.writeText("old") }
        val e = assertThrows(StoreException::class.java) { syncedWrite(f, "new", dir) { throw IOException("fsync ${it.name}") } }
        assertEquals("dir sync store", e.message)
        assertEquals("fsync store", e.cause?.message)
        assertEquals("new", f.readText())
    }

    @Test
    fun aLyricsFileErrorIsAStoreError() {
        val e: IOException = LyricsFileException("x")
        assertTrue(e is StoreException)
    }
}
