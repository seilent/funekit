package net.seilent.funekit.logic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

object NoScrape {
    private const val MAGIC = 0xCAFEBABE.toInt()
    private const val UTF8 = 1
    private const val WIDE_SLOTS = 2
    private val fixed = mapOf(3 to 4, 4 to 4, 5 to 8, 6 to 8, 7 to 2, 8 to 2, 9 to 4, 10 to 4, 11 to 4, 12 to 4, 15 to 3, 16 to 2, 17 to 4, 18 to 4, 19 to 2, 20 to 2)
    private val wide = setOf(5, 6)
    private const val HOSTS = "(youtube\\.com|ggpht\\.com|googleusercontent\\.com|ytimg\\.com|music\\.163\\.com|126\\.net)"
    private val name = Regex("(?i)netease|nemix|jahan|channelmatch|youtube")
    private val url = Regex("(?i)://([a-z0-9-]+\\.)*$HOSTS")
    private val host = Regex("(?i)(?<![a-z0-9-])([a-z0-9-]+\\.)*$HOSTS")
    private const val PAGES = "net/seilent/funekit/artist/PickLink"

    fun scan(files: List<File>): List<String> = files.sortedBy { it.path }.flatMap { f ->
        when {
            f.isDirectory -> f.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.sortedBy { it.path }
                .flatMap { c -> c.relativeTo(f).invariantSeparatorsPath.let { p -> check("${f.name}/$p", p, c.readBytes()) } }.toList()
            f.isFile && f.name.endsWith(".class") -> check(f.name, f.name, f.readBytes())
            f.isFile && f.name.endsWith(".aar") -> f.inputStream().use { zip(f.name, it, inner = true) }
            f.isFile && f.name.endsWith(".jar") -> f.inputStream().use { zip(f.name, it, inner = false) }
            else -> emptyList()
        }
    }

    private fun zip(at: String, input: InputStream, inner: Boolean): List<String> {
        val hits = ArrayList<String>()
        val z = ZipInputStream(input)
        while (true) {
            val e = z.nextEntry ?: break
            when {
                e.isDirectory -> {}
                inner && (e.name == "classes.jar" || (e.name.startsWith("libs/") && e.name.endsWith(".jar"))) -> hits += zip("$at!${e.name}", ByteArrayInputStream(z.readBytes()), inner = false)
                !inner && e.name.endsWith(".class") -> hits += check("$at!${e.name}", e.name, z.readBytes())
            }
        }
        return hits
    }

    fun check(at: String, entry: String, bytes: ByteArray): List<String> {
        val binary = entry.removeSuffix(".class")
        val simple = binary.substringAfterLast('/')
        val hits = ArrayList<String>()
        if (name.containsMatchIn(simple)) hits += "$at: class $simple names a scraping source"
        val pages = binary == PAGES || binary.startsWith("$PAGES$")
        val pool = try {
            strings(bytes)
        } catch (e: IOException) {
            null
        }
        if (pool == null) throw GenException("checkNoScrape: $at is not a class file")
        for (s in pool) {
            val t = s.replace("\\", "")
            when {
                url.containsMatchIn(t) -> hits += "$at: string '$s' is a scraping host url"
                !pages && host.containsMatchIn(t) -> hits += "$at: string '$s' names a scraping host"
            }
        }
        return hits
    }

    fun strings(bytes: ByteArray): List<String>? {
        val d = DataInputStream(ByteArrayInputStream(bytes))
        if (d.readInt() != MAGIC) return null
        d.readUnsignedShort()
        d.readUnsignedShort()
        val count = d.readUnsignedShort()
        val out = ArrayList<String>()
        var i = 1
        while (i < count) {
            val tag = d.readUnsignedByte()
            if (tag == UTF8) {
                out += d.readUTF()
            } else {
                val n = fixed[tag] ?: throw GenException("checkNoScrape: constant pool tag $tag at $i")
                d.skipBytes(n)
            }
            i += if (tag in wide) WIDE_SLOTS else 1
        }
        return out
    }
}

@CacheableTask
abstract class CheckNoScrapeTask : DefaultTask() {
    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val hits = NoScrape.scan(classes.files.toList())
        if (hits.isNotEmpty()) throw GradleException("checkNoScrape:\n" + hits.joinToString("\n"))
        stamp.get().asFile.writeText("ok\n")
    }
}

class NoScrapePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val checkNoScrape = project.tasks.register("checkNoScrape", CheckNoScrapeTask::class.java) {
            group = "verification"
            classes.from(project.tasks.named("jar"))
            stamp.set(project.layout.buildDirectory.file("checks/checkNoScrape"))
        }
        project.tasks.matching { it.name == "check" }.configureEach { dependsOn(checkNoScrape) }
    }
}
