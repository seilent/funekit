package net.seilent.funekit.logic

import java.io.File

object Moves {
    val files = listOf("player/ui/pagemove.cpp", "player/ui/pagemove.h")
    private const val num = "-?[0-9]+(?:\\.[0-9]+)?"
    private val head = Regex("^(?:inline )?constexpr (Key|int|fx) (.*)$")
    private val name = Regex("^(\\w+)\\s*(\\[\\s*])?\\s*([(=])?")
    private val keyed = Regex("^\\w+\\s*\\[\\s*]\\s*=\\s*\\{(.*)}$")
    private val key = Regex("^\\{\\s*(-?[0-9]+)\\s*,\\s*(?:F\\(\\s*($num)\\s*\\)|key_fx\\(\\s*($num)\\s*\\)|(0))\\s*}$")
    private val whole = Regex("^\\w+\\s*=\\s*(-?[0-9]+)$")
    private val fixed = Regex("^\\w+\\s*=\\s*(?:F|key_fx)\\(\\s*($num)\\s*\\)$")
    private val ratio = Regex("^\\w+\\s*=\\s*fx_int\\(\\s*(-?[0-9]+)\\s*\\)\\s*/\\s*(-?[0-9]+)$")
    private val space = Regex("\\s+")
    private val direct = Regex("^\\(\\s*(-?[0-9.]|F\\(|key_fx\\(|fx_int\\()")

    sealed class Decl(val name: String, val file: String, val line: Int) {
        class Table(name: String, file: String, line: Int, val t: List<Int>, val v: List<String>) : Decl(name, file, line)
        class Whole(name: String, file: String, line: Int, val value: Int) : Decl(name, file, line)
        class Fixed(name: String, file: String, line: Int, val value: String) : Decl(name, file, line)
        class Ratio(name: String, file: String, line: Int, val q: Int) : Decl(name, file, line)
    }

    private fun quoted(t: String, i: Int): Int {
        var j = i + 1
        while (t[j] != t[i]) j += if (t[j] == '\\') 2 else 1
        return j + 1
    }

    fun strip(t: String): String {
        val o = StringBuilder(t.length)
        var i = 0
        while (i < t.length) {
            when {
                t.startsWith("//", i) -> while (i < t.length && t[i] != '\n') { o.append(' '); i++ }
                t.startsWith("/*", i) -> {
                    val end = t.indexOf("*/", i + 2)
                    if (end < 0) throw GenException("moves: a comment has no end")
                    for (k in i until end + 2) o.append(if (t[k] == '\n') '\n' else ' ')
                    i = end + 2
                }
                t[i] == '"' || t[i] == '\'' -> { val j = quoted(t, i); o.append(t, i, j); i = j }
                else -> { o.append(t[i]); i++ }
            }
        }
        return o.lines().joinToString("\n") { if (it.trimStart().startsWith("#")) " ".repeat(it.length) else it }
    }

    private fun block(t: String, from: Int): Int {
        var d = 0
        var i = from
        while (i < t.length) {
            val c = t[i]
            if (c == '"' || c == '\'') { i = quoted(t, i); continue }
            if (c == '{') d++
            if (c == '}' && --d == 0) return i + 1
            i++
        }
        throw GenException("moves: a block has no end")
    }

    fun statements(t: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        val buf = StringBuilder()
        val breaks = t.indices.filter { t[it] == '\n' }
        var line = 1
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (buf.isEmpty() && c.isWhitespace()) { i++; continue }
            if (buf.isEmpty()) line = -(breaks.binarySearch(i) + 1) + 1
            when {
                c == '"' || c == '\'' -> { val j = quoted(t, i); buf.append(t, i, j); i = j }
                c == '{' -> {
                    val text = buf.toString().trim()
                    if (Regex("^namespace(\\s+\\w+)?$").matches(text)) {
                        buf.clear()
                        i++
                    } else if (text.endsWith("=") || (head.containsMatchIn(text.split(space).joinToString(" ")) && '(' !in text)) {
                        val j = block(t, i)
                        buf.append(t, i, j)
                        i = j
                    } else {
                        i = block(t, i)
                        var k = i
                        while (k < t.length && t[k].isWhitespace()) k++
                        if (k < t.length && t[k] == ';') i = k + 1
                        buf.clear()
                    }
                }
                c == '}' && buf.isBlank() -> { buf.clear(); i++ }
                c == ';' -> { out += line to buf.toString().trim().split(space).joinToString(" "); buf.clear(); i++ }
                else -> { buf.append(c); i++ }
            }
        }
        return out
    }

    private fun split(s: String): List<String> {
        val parts = ArrayList<String>()
        var d = 0
        val cur = StringBuilder()
        for (ch in s) {
            if (ch in "({[") d++
            if (ch in ")}]") d--
            if (ch == ',' && d == 0) { parts += cur.toString().trim(); cur.clear() } else cur.append(ch)
        }
        parts += cur.toString().trim()
        return parts
    }

    fun parse(file: String, text: String): List<Decl> {
        val out = ArrayList<Decl>()
        for ((line, s) in statements(strip(text))) {
            val m = head.find(s) ?: continue
            val type = m.groupValues[1]
            fun fail(d: String): Nothing = throw GenException("moves: $file:$line: constexpr $type $d is not a form Moves reads")
            for (d in split(m.groupValues[2])) {
                val n = name.find(d) ?: fail(d)
                if (n.groupValues[3] == "(") {
                    if (direct.containsMatchIn(d.substring(n.range.last))) fail(d)
                    break
                }
                val id = n.groupValues[1]
                out += when (type) {
                    "Key" -> {
                        val body = keyed.find(d)?.groupValues?.get(1)?.trim()?.removeSuffix(",") ?: fail(d)
                        val ks = split(body).map { e -> key.find(e) ?: fail(d) }
                        Decl.Table(id, file, line, ks.map { it.groupValues[1].toInt() }, ks.map { k -> (2..4).map { k.groupValues[it] }.first { it.isNotEmpty() } })
                    }
                    "int" -> Decl.Whole(id, file, line, whole.find(d)?.groupValues?.get(1)?.toInt() ?: fail(d))
                    else -> {
                        val f = fixed.find(d)
                        val r = ratio.find(d)
                        when {
                            f != null -> Decl.Fixed(id, file, line, f.groupValues[1])
                            r != null -> {
                                val b = r.groupValues[2].toLong()
                                if (b == 0L) fail(d)
                                Decl.Ratio(id, file, line, ((r.groupValues[1].toLong() shl 16) / b).toInt())
                            }
                            else -> fail(d)
                        }
                    }
                }
            }
        }
        return out
    }

    fun read(root: File): List<Decl> {
        val all = files.flatMap { p ->
            val f = File(root, p)
            if (!f.isFile) throw GenException("moves: ${root.name}/$p is missing")
            parse(f.name, f.readText())
        }
        val seen = HashMap<String, Decl>()
        for (d in all) {
            val first = seen.put(d.name, d)
            if (first != null) throw GenException("moves: ${d.name} is defined at ${first.file}:${first.line} and ${d.file}:${d.line}")
        }
        return all
    }

    private fun float(v: String) = "${v}f"

    fun movesKt(root: File): String {
        val o = StringBuilder("package ${Gen.pkg}\n\nclass Keys(val t: IntArray, val v: FloatArray)\n\nobject Moves {\n")
        for (d in read(root)) {
            when (d) {
                is Decl.Table -> o.append("    val ${d.name} = Keys(intArrayOf(${d.t.joinToString(", ")}), floatArrayOf(${d.v.joinToString(", ") { float(it) }}))\n")
                is Decl.Whole -> o.append("    const val ${d.name} = ${d.value}\n")
                is Decl.Fixed -> o.append("    const val ${d.name} = ${float(d.value)}\n")
                is Decl.Ratio -> o.append("    const val ${d.name} = ${d.q}f / 65536f\n")
            }
        }
        return o.append("}\n").toString()
    }
}
