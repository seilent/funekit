package net.seilent.funekit.kit

fun nextCluster(s: String, p: Int, end: Int): Int = minOf(end, p + Character.charCount(s.codePointAt(p)))

fun wrapRanges(s: String, width64: Int, maxRows: Int, width: (Int, Int) -> Int, step: (Int, Int) -> Int): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    var lb = 0
    var le = 0
    var i = 0
    var line = false
    while (i < s.length && out.size < maxRows) {
        val sp = s.indexOf(' ', i)
        val we = if (sp < 0) s.length else sp
        val next = if (sp < 0) s.length else sp + 1
        val cb = if (line) lb else i
        if (width(cb, we) <= width64) {
            lb = cb
            le = we
            line = le > lb
            i = next
            continue
        }
        if (line) {
            out += lb to le
            line = false
            if (out.size >= maxRows) break
        }
        var p = i
        var pb = i
        var pe = i
        var part = false
        while (p < we) {
            val q = step(p, we)
            if (part && width(pb, q) > width64) {
                out += pb to pe
                part = false
                if (out.size >= maxRows) break
                continue
            }
            if (!part) pb = p
            part = true
            pe = q
            p = q
        }
        lb = pb
        le = pe
        line = part
        i = next
    }
    if (line && out.size < maxRows) out += lb to le
    return out
}

fun fitPrefix(s: String, width64: Int, width: (String) -> Int): Int {
    var p = 0
    var fit = 0
    while (p < s.length) {
        val q = nextCluster(s, p, s.length)
        if (width(s.substring(0, q)) > width64) break
        fit = q
        p = q
    }
    return fit
}

fun wrapAt(s: String, width64: Int, width: (String) -> Int): Int {
    val first = wrapRanges(s, width64, 1, { b, e -> width(s.substring(b, e)) }, { p, e -> nextCluster(s, p, e) }).firstOrNull()
    val line = first?.let { s.substring(it.first, it.second) } ?: ""
    val k = line.length
    if (k > 0 && k < s.length && s.startsWith(line)) {
        val r = (k until s.length).firstOrNull { s[it] != ' ' }
        if (r == null || width(s.substring(r)) <= width64) return k
    }
    return fitPrefix(s, width64, width)
}

