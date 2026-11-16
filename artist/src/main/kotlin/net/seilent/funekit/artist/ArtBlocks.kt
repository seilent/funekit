package net.seilent.funekit.artist

import java.io.File

class ArtBlocks(
    private val file: File? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    val ledger: PushLedger? = null,
    private val log: (String) -> Unit = {},
) {
    sealed interface Record {
        val key: String
        val mbid: String?
        val name: String
        val at: Long
    }

    data class Block(
        override val key: String,
        override val mbid: String?,
        val source: String,
        val id: String,
        val sha: String,
        override val name: String,
        override val at: Long,
        val applied: Set<String> = emptySet(),
        val crc: Long? = null,
    ) : Record

    data class Reject(
        override val key: String,
        override val mbid: String?,
        val resolvedBy: String,
        override val name: String,
        override val at: Long,
        val applied: Set<String> = emptySet(),
        val crc: Long? = null,
        val channel: String = "",
    ) : Record

    data class Drop(
        override val key: String,
        override val mbid: String?,
        override val name: String,
        override val at: Long,
        val applied: Set<String> = emptySet(),
        val cleared: Boolean = false,
        val crcs: Set<Long> = emptySet(),
    ) : Record

    private val blocks = ArrayList<Block>()
    private val rejects = ArrayList<Reject>()
    private val drops = ArrayList<Drop>()
    private val kept = ArrayList<String>()

    init {
        load()
    }

    @Synchronized
    fun add(r: Record) {
        when (r) {
            is Block -> blocks += r
            is Reject -> rejects += r
            is Drop -> {
                drop(r.key, r.mbid, r.name, r.at)
                return
            }
        }
        ledger?.blocked(r)
    }

    @Synchronized
    fun drop(key: String, mbid: String?, name: String, at: Long): Boolean {
        ledger?.dropped(key)
        if (drops.any { it.key == key }) return false
        drops += Drop(key, Names.mbid(mbid), name, at)
        return true
    }

    @Synchronized
    fun cleared(key: String, mbid: String?, name: String, at: Long, crcs: Set<Long> = emptySet()) {
        if (drops.any { it.key == key && !it.cleared }) return
        ledger?.dropped(key)
        drops.removeAll { it.key == key }
        drops += Drop(key, Names.mbid(mbid), name, at, cleared = true, crcs = crcs)
    }

    @Synchronized
    fun drops(): List<Drop> = drops.toList()

    @Synchronized
    fun remove(r: Record) {
        when (r) {
            is Block -> blocks.removeAll { it.key == r.key && it.source == r.source && it.id == r.id && it.at == r.at }
            is Reject -> rejects.removeAll { it.resolvedBy == r.resolvedBy && it.mbid == r.mbid && it.at == r.at }
            is Drop -> {
                drops.removeAll { it.key == r.key }
                return
            }
        }
        ledger?.unblocked(r)
    }

    @Synchronized
    fun blocked(key: String, source: String, id: String, sha: String = "", name: String = ""): Boolean {
        val byName = Names.fold(name).takeIf { it.isNotEmpty() }?.let { "name:$it" }
        return blocks.any { (it.key == key || it.key == byName) && ((it.source == source && it.id == id && id.isNotEmpty()) || (sha.isNotEmpty() && it.sha == sha)) }
    }

    @Synchronized
    fun rejected(name: String): Set<String> {
        val f = Names.fold(name)
        return rejects.filter { Names.fold(it.resolvedBy) == f && it.channel.isEmpty() }.mapNotNullTo(HashSet()) { it.mbid }
    }

    @Synchronized
    fun rejectedChannels(name: String): Set<String> {
        val f = Names.fold(name)
        return rejects.filter { Names.fold(it.resolvedBy) == f && it.channel.isNotEmpty() }.mapTo(HashSet()) { it.channel }
    }

    @Synchronized
    fun records(): List<Record> = (blocks + rejects).sortedByDescending { it.at }

    @Synchronized
    fun pending(device: String): List<Record> = (records() + drops).filter {
        device !in when (it) {
            is Block -> it.applied
            is Reject -> it.applied
            is Drop -> it.applied
        }
    }

    @Synchronized
    fun covering(wrongArtist: Boolean, name: String, keys: Set<String>, mbid: String?, crc: Long, device: String?): Record? {
        val f = Names.fold(name)
        val m = Names.mbid(mbid)
        return records().firstOrNull { r ->
            val (rc, applied) = when (r) {
                is Block -> r.crc to r.applied
                is Reject -> r.crc to r.applied
                is Drop -> null to r.applied
            }
            (r is Reject) == wrongArtist && (r.key in keys || Names.fold(r.name) == f || (m != null && r.mbid == m)) &&
                (rc == crc || (rc == null && device != null && device !in applied))
        }
    }

    @Synchronized
    fun applied(r: Record, device: String) {
        when (r) {
            is Block -> blocks.replaceAll { if (it == r || (it.key == r.key && it.at == r.at && it.id == r.id)) it.copy(applied = it.applied + device) else it }
            is Reject -> rejects.replaceAll { if (it == r || (it.resolvedBy == r.resolvedBy && it.at == r.at)) it.copy(applied = it.applied + device) else it }
            is Drop -> drops.replaceAll { if (it.key == r.key) it.copy(applied = it.applied + device) else it }
        }
    }

    @Synchronized
    fun encode(): String = buildString {
        for (b in blocks) line("block", b.key, b.mbid ?: "", b.source, b.id, b.sha, b.name, b.at.toString(), b.applied.joinToString(","), *listOfNotNull(b.crc?.toString()).toTypedArray())
        for (d in drops) line("drop", d.key, d.mbid ?: "", d.name, d.at.toString(), d.applied.joinToString(","), *(if (!d.cleared) emptyList() else if (d.crcs.isEmpty()) listOf(CLEARED) else listOf(CLEARED, d.crcs.sorted().joinToString(","))).toTypedArray())
        for (r in rejects) line("reject", r.key, r.mbid ?: "", r.resolvedBy, r.name, r.at.toString(), r.applied.joinToString(","), *(if (r.channel.isEmpty()) listOfNotNull(r.crc?.toString()) else listOf(r.crc?.toString() ?: "", r.channel)).toTypedArray())
        val l = ledger
        if (l == null) {
            kept.forEach { append(it).append('\n') }
        } else {
            l.lines().forEach { line(*it.toTypedArray()) }
        }
    }

    @Synchronized
    fun save() {
        val f = file ?: return
        val text = encode()
        f.parentFile?.mkdirs()
        val t = File(f.path + ".tmp")
        t.writeText(text, Charsets.UTF_8)
        if (!t.renameTo(f)) {
            f.delete()
            t.renameTo(f)
        }
    }

    @Synchronized
    private fun load() {
        ledger?.clear()
        val f = file ?: return
        if (!f.isFile) return
        for (l in f.readLines(Charsets.UTF_8)) {
            if (l.isBlank()) continue
            val p = l.split('\t').map { unescape(it) }
            val ok = when (p[0]) {
                "block" -> (p.size == 9 || p.size == 10) && p[7].toLongOrNull() != null && blocks.add(Block(p[1], p[2].ifEmpty { null }, p[3], p[4], p[5], p[6], p[7].toLong(), set(p[8]), p.getOrNull(9)?.toLongOrNull()))
                "reject" -> p.size in 7..9 && p[5].toLongOrNull() != null && rejects.add(Reject(p[1], p[2].ifEmpty { null }, p[3], p[4], p[5].toLong(), set(p[6]), p.getOrNull(7)?.toLongOrNull(), p.getOrNull(8) ?: ""))
                "drop" -> (p.size == 6 || (p.size in 7..8 && p[6] == CLEARED)) && p[4].toLongOrNull() != null &&
                    ((p.size >= 7 && clock() - p[4].toLong() >= CLEAR_KEEP_MS) ||
                        (drops.none { it.key == p[1] } && drops.add(Drop(p[1], p[2].ifEmpty { null }, p[3], p[4].toLong(), set(p[5]), p.size >= 7, set(p.getOrNull(7) ?: "").mapNotNullTo(HashSet()) { it.toLongOrNull() }))))
                in PushLedger.KINDS -> ledger?.load(p) ?: kept.add(l)
                else -> false
            }
            if (!ok) log("artblock skipped line ${l.take(80)}")
        }
    }

    private fun StringBuilder.line(vararg f: String) {
        append(f.joinToString("\t") { escape(it) })
        append('\n')
    }

    private fun set(s: String) = s.split(',').filter { it.isNotEmpty() }.toSet()

    internal companion object {
        const val CLEAR_KEEP_MS = 90L * 24 * 60 * 60 * 1000
        private const val CLEARED = "clear"

        fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        fun unescape(s: String) = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    append(
                        when (s[i + 1]) {
                            't' -> '\t'
                            'n' -> '\n'
                            else -> s[i + 1]
                        },
                    )
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
