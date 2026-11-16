package net.seilent.funekit.core

object AutoEq {
    private const val NAME_MAX_BYTES = 64
    private val SUFFIXES = listOf(" ParametricEQ", " GraphicEQ")
    private val RESERVED = setOf("off", "flat", "custom")

    fun name(file: String): String {
        var n = file.trim()
        if (n.endsWith(".txt", ignoreCase = true)) n = n.dropLast(4)
        SUFFIXES.forEach { if (n.endsWith(it)) n = n.removeSuffix(it) }
        return n.trim()
    }

    fun validName(n: String): Boolean {
        val bytes = n.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > NAME_MAX_BYTES) return false
        if (bytes.any { (it.toInt() and 0xff) < 0x20 || it.toInt() == 0x7f }) return false
        if ('/' in n || n.startsWith('.') || n.startsWith(' ') || n.endsWith(' ')) return false
        if (SUFFIXES.any { n.endsWith(it) }) return false
        return foldKey(n) !in RESERVED
    }

    fun foldKey(n: String) = buildString { n.forEach { append(if (it in 'A'..'Z') it + ('a' - 'A') else it) } }
}
