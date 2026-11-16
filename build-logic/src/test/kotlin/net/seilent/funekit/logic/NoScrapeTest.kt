package net.seilent.funekit.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NoScrapeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun klass(name: String, vararg strings: String): ByteArray {
        val b = ByteArrayOutputStream()
        val d = DataOutputStream(b)
        d.writeInt(0xCAFEBABE.toInt())
        d.writeShort(0)
        d.writeShort(52)
        d.writeShort(7 + strings.size * 2)
        d.writeByte(1)
        d.writeUTF(name)
        d.writeByte(7)
        d.writeShort(1)
        d.writeByte(1)
        d.writeUTF("java/lang/Object")
        d.writeByte(7)
        d.writeShort(3)
        d.writeByte(5)
        d.writeLong(42)
        strings.forEachIndexed { i, s ->
            d.writeByte(1)
            d.writeUTF(s)
            d.writeByte(8)
            d.writeShort(7 + i * 2)
        }
        d.writeShort(0x21)
        d.writeShort(2)
        d.writeShort(4)
        repeat(4) { d.writeShort(0) }
        return b.toByteArray()
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val b = ByteArrayOutputStream()
        ZipOutputStream(b).use { z ->
            for ((n, bytes) in entries) {
                z.putNextEntry(ZipEntry(n))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return b.toByteArray()
    }

    private fun scan(): List<String> {
        val jar = File(tmp.root, "art.jar")
        jar.writeBytes(
            zip(
                linkedMapOf(
                    "a/NetEaseLyrics.class" to klass("a/NetEaseLyrics"),
                    "a/PickLink\$YouTube.class" to klass("a/PickLink\$YouTube"),
                    "a/Inline.class" to klass("a/Inline", "https://www.youtube.com/channel/"),
                    "a/Pages.class" to klass("a/Pages", "youtube.com"),
                    "net/seilent/funekit/artist/PickLink.class" to klass("net/seilent/funekit/artist/PickLink", "youtube.com"),
                    "net/seilent/funekit/artist/PickLink\$Link.class" to klass("net/seilent/funekit/artist/PickLink\$Link", "music.youtube.com"),
                    "b/PickLink.class" to klass("b/PickLink", "youtube.com"),
                    "a/Built.class" to klass("a/Built", "youtube.com"),
                    "a/Clean.class" to klass("a/Clean", "https://musicbrainz.org/ws/2/"),
                    "a/Rx.class" to klass("a/Rx", "^https?://(www\\.|m\\.)?youtube\\.com/(channel/|user/|c/|@)"),
                ),
            ),
        )
        val aar = File(tmp.root, "lib.aar")
        aar.writeBytes(
            zip(
                linkedMapOf(
                    "AndroidManifest.xml" to ByteArray(0),
                    "classes.jar" to zip(mapOf("a/NeMix.class" to klass("a/NeMix"))),
                    "libs/x.jar" to zip(mapOf("a/Libbed.class" to klass("a/Libbed", "https://www.youtube.com/watch"))),
                    "lint.jar" to zip(mapOf("a/Linted.class" to klass("a/Linted", "https://www.youtube.com/lint"))),
                ),
            ),
        )
        return NoScrape.scan(listOf(jar, aar))
    }

    private fun hits(path: String) = scan().filter { it.substringBefore(':').endsWith("!$path.class") }

    @Test
    fun scrapingClassNamesAreNamed() {
        assertEquals(listOf("art.jar!a/NetEaseLyrics.class: class NetEaseLyrics names a scraping source"), hits("a/NetEaseLyrics"))
        assertEquals(listOf("art.jar!a/PickLink\$YouTube.class: class PickLink\$YouTube names a scraping source"), hits("a/PickLink\$YouTube"))
    }

    @Test
    fun aScrapingUrlInACleanNamedClassIsNamedWithItsString() {
        assertEquals(listOf("art.jar!a/Inline.class: string 'https://www.youtube.com/channel/' is a scraping host url"), hits("a/Inline"))
    }

    @Test
    fun aBareHostOutsidePickLinkIsNamed() {
        assertEquals(listOf("art.jar!a/Pages.class: string 'youtube.com' names a scraping host"), hits("a/Pages"))
        assertEquals(listOf("art.jar!a/Built.class: string 'youtube.com' names a scraping host"), hits("a/Built"))
    }

    @Test
    fun aBareHostInsidePickLinkAndACleanClassPass() {
        assertEquals(emptyList<String>(), hits("net/seilent/funekit/artist/PickLink"))
        assertEquals(emptyList<String>(), hits("net/seilent/funekit/artist/PickLink\$Link"))
        assertEquals(emptyList<String>(), hits("a/Clean"))
    }

    @Test
    fun aClassNamedPickLinkInAnotherPackageIsScanned() {
        assertEquals(listOf("art.jar!b/PickLink.class: string 'youtube.com' names a scraping host"), hits("b/PickLink"))
    }

    @Test
    fun anAarIsOpenedThroughItsClassesJar() {
        assertEquals(listOf("lib.aar!classes.jar!a/NeMix.class: class NeMix names a scraping source"), hits("a/NeMix"))
    }

    @Test
    fun aHostWrittenAsARegexIsNamed() {
        assertEquals(listOf("art.jar!a/Rx.class: string '^https?://(www\\.|m\\.)?youtube\\.com/(channel/|user/|c/|@)' names a scraping host"), hits("a/Rx"))
    }

    @Test
    fun aJarUnderLibsInsideAnAarIsOpened() {
        assertEquals(listOf("lib.aar!libs/x.jar!a/Libbed.class: string 'https://www.youtube.com/watch' is a scraping host url"), hits("a/Libbed"))
    }

    @Test
    fun aLintJarInsideAnAarIsNotRead() {
        assertEquals(emptyList<String>(), hits("a/Linted"))
    }

    @Test
    fun aClassEntryThatIsNotAClassFileFailsTheScan() {
        val jar = File(tmp.root, "bad.jar")
        jar.writeBytes(zip(mapOf("a/Bad.class" to "not a class".toByteArray())))
        val e = assertThrows(GenException::class.java) { NoScrape.scan(listOf(jar)) }
        assertTrue(e.message, e.message!!.contains("bad.jar!a/Bad.class is not a class file"))
    }

    @Test
    fun aClassDirIsWalked() {
        val dir = File(tmp.root, "classes")
        File(dir, "a").mkdirs()
        File(dir, "a/JaHan.class").writeBytes(klass("a/JaHan"))
        File(dir, "a/Clean.class").writeBytes(klass("a/Clean"))
        assertEquals(listOf("classes/a/JaHan.class: class JaHan names a scraping source"), NoScrape.scan(listOf(dir)))
    }
}
