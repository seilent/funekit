package net.seilent.funekit.logic

import java.io.File

class BridgeRule(val old: Set<String>, val new: Set<String>, val changed: Set<String>) {
    fun unlisted(n: String) = n in changed && n !in old && n !in new

    companion object {
        val NONE = BridgeRule(emptySet(), emptySet(), emptySet())
    }
}

object Bridge {
    private val fileName = Regex("^c[0-9]+\\.txt$")
    private val kinds = setOf("old", "new")

    class Line(val kind: String, val name: String, val file: String, val at: Int)

    fun lines(dir: File): List<Line> {
        if (!dir.isDirectory) throw GenException("bridge: ${dir.name} is missing")
        val out = ArrayList<Line>()
        for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
            if (!f.isFile || !fileName.matches(f.name)) throw GenException("bridge: ${dir.name}/${f.name} is not a c<n>.txt file")
            f.readLines().forEachIndexed { i, line ->
                if (line.isBlank()) return@forEachIndexed
                val p = line.trim().split(Regex("\\s+"))
                if (p.size != 2 || p[0] !in kinds) throw GenException("bridge: ${f.name}:${i + 1} needs old or new and a token name")
                out += Line(p[0], p[1], f.name, i + 1)
            }
        }
        return out
    }

    fun rule(tok: File, old: File): BridgeRule {
        val t = Gen.tokens(tok)
        val o = Gen.tokens(old)
        val ls = lines(File(old, "bridge"))
        val seen = HashMap<String, Line>()
        for (l in ls) {
            val first = seen.put(l.name, l)
            if (first != null) throw GenException("bridge: ${l.name} is in ${first.file}:${first.at} and ${l.file}:${l.at}")
        }
        val now = t.names.toSet()
        val was = o.names.toSet()
        val changed = now.intersect(was).filter { n -> Gen.boards.indices.any { t.value(it, n) != o.value(it, n) } }.toSet()
        val differ = now != was || changed.isNotEmpty()
        for (l in ls) {
            val at = "${l.file}:${l.at}"
            if (l.name !in now && l.name !in was) throw GenException("bridge: $at names ${l.name}, which neither vendored set has")
            if (l.kind == "new" && l.name !in now) throw GenException("bridge: $at marks the removed token ${l.name} new")
            if (differ && l.name in now && l.name !in changed) throw GenException("bridge: $at lists ${l.name}, which is not changed")
        }
        fun ids(kind: String) = ls.filter { it.kind == kind }.map { Gen.tokId(it.name) }.toSet()
        return BridgeRule(ids("old"), ids("new"), changed.map(Gen::tokId).toSet())
    }
}
