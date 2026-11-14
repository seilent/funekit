package net.seilent.funekit.logic

import java.io.File

object Fence {
    val allowed = listOf(
        "net.seilent.funekit", "kotlin", "kotlinx.coroutines", "java", "android",
        "androidx.compose.runtime", "androidx.compose.ui", "androidx.compose.foundation", "androidx.compose.animation",
        "androidx.activity", "androidx.core", "androidx.lifecycle", "androidx.window",
    )
    val denied = listOf("androidx.compose.material", "net.seilent.funeplayer", "net.seilent.funecomp")

    private val importLine = Regex("^import\\s+([A-Za-z0-9_.]+)")

    private fun under(fq: String, prefix: String) = fq == prefix || fq.startsWith("$prefix.")

    fun scan(files: List<File>, allowed: List<String> = Fence.allowed, denied: List<String> = Fence.denied): List<String> {
        val named = denied.map { d -> Regex("(?<![A-Za-z0-9_.])" + Regex.escape(d) + "[A-Za-z0-9_]*\\.") }
        return files.sortedBy { it.path }.flatMap { f ->
            f.readLines().withIndex().flatMap { (i, line) ->
                val at = "${f.name}:${i + 1}"
                val fq = importLine.find(line.trim())?.groupValues?.get(1)
                val bad = ArrayList<String>()
                if (fq != null && allowed.none { under(fq, it) }) bad += "$at: import $fq is outside the kit allow list"
                for ((d, r) in denied.zip(named)) if (r.containsMatchIn(line)) bad += "$at: names $d"
                bad
            }
        }
    }
}

class KitPinLine(val commit: String, val set: String)

object KitPin {
    val excludes = listOf("**/.git", "**/.git/**", "**/.gitmodules", "**/build", "**/build/**", "**/.gradle", "**/.gradle/**", "**/.kotlin", "**/.kotlin/**", "**/local.properties")

    fun read(f: File): KitPinLine {
        if (!f.isFile) throw GenException("checkKitPin: ${f.name} is missing")
        val m = f.readLines().filter { it.isNotBlank() }.associate { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size != 2 || p[0] !in setOf("commit", "set")) throw GenException("checkKitPin: ${f.name} line '$line' is not commit <sha> or set <sha256>")
            p[0] to p[1]
        }
        val commit = m["commit"] ?: throw GenException("checkKitPin: ${f.name} has no commit line")
        val set = m["set"] ?: throw GenException("checkKitPin: ${f.name} has no set line")
        return KitPinLine(commit, set)
    }
}
