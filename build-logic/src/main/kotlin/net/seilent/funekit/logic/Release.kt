package net.seilent.funekit.logic

import java.io.File

object Release {
    private val sha = Regex("^[0-9a-f]{64}$")
    private val hex = Regex("^[0-9a-f]{40}$")
    private val key = Regex("\\{\\s*(-?[0-9]+)\\s*,\\s*(?:F\\(\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*\\)|(0))\\s*\\}")

    class Source(
        val commit: String?,
        val tree: String?,
        val from: String?,
        val files: Map<String, String>,
        val set: String,
        val names: List<String>,
        val shadows: List<String>,
    )

    fun source(rel: File): Source {
        val f = File(rel, "SOURCE")
        if (!f.isFile) throw GenException("rel: ${rel.name}/SOURCE is missing")
        var commit: String? = null
        var tree: String? = null
        var from: String? = null
        var set: String? = null
        val files = LinkedHashMap<String, String>()
        val names = ArrayList<String>()
        val shadows = ArrayList<String>()
        for ((i, line) in f.readLines().withIndex()) {
            if (line.isBlank()) continue
            val p = line.trim().split(Regex("\\s+"))
            if (p[0] == "from" && p.size > 1 && from == null) {
                from = p.drop(1).joinToString(" ")
                continue
            }
            if (p.size != 2) throw GenException("rel: SOURCE:${i + 1} needs a key and a value")
            val k = p[0]
            val v = p[1]
            if (k == "commit" && hex.matches(v) && commit == null) {
                commit = v
            } else if (k == "tree" && hex.matches(v) && tree == null) {
                tree = v
            } else if (k == "set" && sha.matches(v) && set == null) {
                set = v
            } else if (k == "name" && v !in names) {
                names += v
            } else if (k == "shadow" && v !in shadows) {
                shadows += v
            } else if (!(sha.matches(k) && files.put(v, k) == null)) {
                throw GenException("rel: SOURCE:${i + 1} is not a from, commit, tree, set, name, shadow or file line, or repeats one")
            }
        }
        if (from == null && (commit == null || tree == null) || set == null || files.isEmpty()) {
            throw GenException("rel: SOURCE needs a from line or commit and tree lines, a set line and file lines")
        }
        val loose = shadows.filter { it !in names }
        if (loose.isNotEmpty()) throw GenException("rel: SOURCE shadows names Rel does not read: $loose")
        return Source(commit, tree, from, files, set, names, shadows)
    }

    fun checkPin(rel: File, pin: File) {
        val s = source(rel)
        val listed = s.files.keys
        val found = File(rel, "player").walkTopDown().filter { it.isFile }.map { it.relativeTo(rel).invariantSeparatorsPath }.toSet()
        val loose = found - listed
        if (loose.isNotEmpty()) throw GenException("checkRelPin: files under ${rel.name} with no SOURCE line: ${loose.sorted()}")
        for ((path, want) in s.files) {
            val f = File(rel, path)
            if (!f.isFile) throw GenException("checkRelPin: ${rel.name}/$path is missing")
            val got = Gen.sha256(f.readBytes())
            if (got != want) throw GenException("checkRelPin: ${rel.name}/$path hashes to $got, SOURCE pins $want")
        }
        if (!pin.isFile) throw GenException("checkRelPin: ${pin.name} is missing")
        val set = pin.readText().trim()
        if (set != s.set) throw GenException("checkRelPin: ${pin.name} pins $set, ${rel.name}/SOURCE was compared against ${s.set}")
    }

    class Keys(val t: List<Int>, val v: List<String>)

    private fun declared(text: String, name: String): Int = Regex("\\b${Regex.escape(name)}\\s*(\\[\\s*\\])?\\s*=(?!=)").findAll(text).count()

    fun keys(text: String, name: String): Keys {
        if (declared(text, name) != 1) throw GenException("rel: pagemove.cpp declares $name ${declared(text, name)} times")
        val m = Regex("constexpr\\s+Key\\s+${Regex.escape(name)}\\s*\\[\\s*\\]\\s*=\\s*\\{(.*?)\\};", RegexOption.DOT_MATCHES_ALL).find(text)
            ?: throw GenException("rel: pagemove.cpp has no constexpr Key $name[]")
        val body = m.groupValues[1]
        val ks = key.findAll(body).toList()
        val rest = key.replace(body, "").replace(",", "").trim()
        if (ks.isEmpty() || rest.isNotEmpty()) throw GenException("rel: $name holds a key that is not {int, F(decimal)} or {int, 0}")
        return Keys(ks.map { it.groupValues[1].toInt() }, ks.map { it.groupValues[2].ifEmpty { it.groupValues[3] } })
    }

    fun int(text: String, name: String): Int {
        if (declared(text, name) != 1) throw GenException("rel: pagemove.cpp declares $name ${declared(text, name)} times")
        for (st in Regex("constexpr\\s+int\\s+([^;]*);").findAll(text)) {
            for (d in st.groupValues[1].split(',')) {
                val p = d.split('=')
                if (p.size == 2 && p[0].trim() == name) {
                    return p[1].trim().toIntOrNull() ?: throw GenException("rel: $name is not an int literal")
                }
            }
        }
        throw GenException("rel: pagemove.cpp has no constexpr int $name")
    }

    fun token(rel: File, name: String): String {
        val dir = File(rel, "player/ui")
        val files = Gen.boards.map { it.second } + Gen.ap80
        val values = files.map { f ->
            val file = File(dir, f)
            if (!file.isFile) throw GenException("rel: ${rel.name}/player/ui/$f is missing")
            val hits = file.readLines().map { it.split(' ') }.filter { it.size == 3 && it[0] == name }
            if (hits.size != 1) throw GenException("rel: $f names $name ${hits.size} times")
            hits[0][1]
        }
        if (values.toSet().size != 1) throw GenException("rel: $name differs between the release token files: ${files.zip(values)}")
        return values[0]
    }

    fun constName(name: String): String {
        val n = name.removePrefix("k")
        val o = StringBuilder()
        for ((i, c) in n.withIndex()) {
            if (i > 0 && c.isUpperCase() && !n[i - 1].isUpperCase()) o.append('_')
            o.append(c.uppercaseChar())
        }
        return o.toString()
    }

    private fun float(v: String) = "${v}f"

    fun relKt(rel: File): String {
        val s = source(rel)
        val cpp = s.files.keys.firstOrNull { it.endsWith("/pagemove.cpp") }?.let { File(rel, it) }
        val text = cpp?.takeIf { it.isFile }?.readText()
        val o = StringBuilder("package ${Gen.pkg}\n\nclass Keys(val t: IntArray, val v: FloatArray)\n\nobject Rel {\n")
        for (n in s.names) {
            if (n.contains('.')) {
                val v = token(rel, n)
                if (!Regex("^-?[0-9]+(\\.[0-9]+)?$").matches(v)) throw GenException("rel: $n is not a number")
                o.append("    const val ${Gen.tokId(n)} = ${float(v)}\n")
                continue
            }
            if (text == null) throw GenException("rel: SOURCE names $n but lists no pagemove.cpp")
            val arr = Regex("constexpr\\s+Key\\s+${Regex.escape(n)}\\s*\\[").containsMatchIn(text)
            if (arr) {
                val k = keys(text, n)
                o.append("    val ${constName(n)} = Keys(intArrayOf(${k.t.joinToString(", ")}), floatArrayOf(${k.v.joinToString(", ") { float(it) }}))\n")
            } else {
                o.append("    const val ${constName(n)} = ${int(text, n)}\n")
            }
        }
        return o.append("}\n").toString()
    }
}
