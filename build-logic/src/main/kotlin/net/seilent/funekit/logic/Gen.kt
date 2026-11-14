package net.seilent.funekit.logic

import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.security.MessageDigest

class GenException(message: String) : Exception(message)

object Gen {
    const val pkg = "net.seilent.funekit.gen"
    val boards = listOf("v1" to "tokens-v1.txt", "r1" to "tokens-r1.txt", "a105" to "tokens-a105.txt")
    const val ap80 = "tokens.txt"
    val scanRoots = listOf(
        "kit/src/main/kotlin",
    )
    val budget = setOf(
        "motion.volumeTiltAngle", "motion.volumeTiltDepth", "qpTiltAngle",
        "motion.contentHubRows", "motion.contentPageRows", "motion.npPace",
    )
    val classNames = setOf("ppu", "h", "hr", "v1c", "v1h", "step", "budget", "x", "free")
    private val linear = setOf("h", "hr", "v1c", "v1h", "free")
    private val keywords = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
        "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
        "val", "var", "when", "while",
    )
    private val fitParams = setOf("fitLo", "fitHi", "fitT", "fitStep", "fitPpu", "fitH", "fitAbove", "fitFree")
    private val number = Regex("^-?[0-9]+(\\.[0-9]+)?$")
    private val color = Regex("^#[0-9A-Fa-f]{6}$")
    private val mc = MathContext.DECIMAL64

    fun renderer(name: String) = name.startsWith("motion.slices") || name == "motion.npSlices" || name == "saver.aheadMs"

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun setList(root: File, task: String = "checkTokPin"): String {
        val files = File(root, "player").walkTopDown().filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }
            .sortedBy { it.first }
            .toList()
        if (files.isEmpty()) throw GenException("$task: no files under ${root.name}/player")
        return files.joinToString("") { (path, f) -> "${sha256(f.readBytes())}  $path\n" }
    }

    fun setHash(root: File): String = sha256(setList(root).toByteArray())

    fun checkPin(root: File, pin: File, task: String = "checkTokPin") {
        if (!pin.isFile) throw GenException("$task: ${pin.name} is missing")
        val want = pin.readText().trim()
        val got = sha256(setList(root, task).toByteArray())
        if (got != want) throw GenException("$task: the vendored set hashes to $got, ${pin.name} pins $want\n${setList(root, task)}")
    }

    class Row(val name: String, val value: String, val unit: String)

    class Tokens(val names: List<String>, val each: List<List<Row>>, val board: Set<String>) {
        private val index = names.withIndex().associate { it.value to it.index }
        fun value(b: Int, name: String): String =
            each[b][index[name] ?: throw GenException("tokens: no token $name")].value
    }

    private fun rows(f: File): List<Row> {
        if (!f.isFile) throw GenException("tokens: ${f.name} is missing")
        return f.readLines().mapIndexedNotNull { i, line ->
            if (line.isBlank()) return@mapIndexedNotNull null
            val p = line.split(' ')
            if (p.size != 3 || p.any { it.isEmpty() }) throw GenException("tokens: ${f.name}:${i + 1} needs name value unit")
            Row(p[0], p[1], p[2])
        }
    }

    fun tokens(root: File): Tokens {
        val dir = File(root, "player/ui")
        val each = boards.map { rows(File(dir, it.second)) }
        val names = each[0].map { it.name }
        if (names.toSet().size != names.size) throw GenException("tokens: ${boards[0].second} names a token twice")
        val others = boards.drop(1).map { it.second }.zip(each.drop(1)) + (ap80 to rows(File(dir, ap80)))
        for ((f, r) in others) if (r.map { it.name } != names)
            throw GenException("tokens: ${boards[0].second} and $f name different tokens or order them differently")
        val ids = names.map(::tokId)
        if (ids.toSet().size != ids.size) throw GenException("tokens: two token names map to one Kotlin name")
        val board = names.indices.filter { i -> each.any { it[i].value != each[0][i].value } }.map { names[it] }.toSet()
        for (n in listOf("u", "canvasH")) if (n !in board) throw GenException("tokens: $n is not a board token")
        val clash = board.map(::tokId).filter { it in fitParams }
        if (clash.isNotEmpty()) throw GenException("tokens: board token names clash with TokFit: $clash")
        return Tokens(names, each, board)
    }

    private fun dec(s: String) = BigDecimal(s)

    private fun half(s: String): BigDecimal {
        val places = s.substringAfter('.', "").length
        return BigDecimal.valueOf(5L, places + 1)
    }

    private fun meet(iv: List<Pair<BigDecimal, BigDecimal>>) = iv.maxOf { it.first } <= iv.minOf { it.second }

    private fun numeric(vs: List<String>, hs: List<BigDecimal>, ps: List<BigDecimal>): String {
        val lo = vs.map { dec(it) - half(it) }
        val hi = vs.map { dec(it) + half(it) }
        val all = listOf(0, 1, 2)
        val top = listOf(1, 2)
        fun anchored(ix: List<Int>) = meet(ix.map { hs[it] - hi[it] to hs[it] - lo[it] })
        fun ratio(by: List<BigDecimal>) = meet(all.map { lo[it].divide(by[it], mc) to hi[it].divide(by[it], mc) })
        return when {
            anchored(all) -> "h"
            ratio(hs) -> "hr"
            ratio(ps) -> "ppu"
            meet(top.map { lo[it] to hi[it] }) -> "v1c"
            anchored(top) -> "v1h"
            else -> "free"
        }
    }

    fun classes(root: File): LinkedHashMap<String, String> {
        val t = tokens(root)
        val hs = t.each.indices.map { dec(t.value(it, "canvasH")) }
        val ps = t.each.indices.map { dec(t.value(it, "u")) }
        val out = LinkedHashMap<String, String>()
        for ((i, n) in t.names.withIndex()) {
            if (n !in t.board) continue
            val vs = t.each.map { it[i].value }
            val unit = t.each[0][i].unit
            out[n] = when {
                n in budget -> "budget"
                renderer(n) -> "x"
                unit == "count" || unit == "mode" || vs.any { !number.matches(it) } -> "step"
                else -> numeric(vs, hs, ps)
            }
        }
        return out
    }

    fun fitText(root: File): String = classes(root).entries.joinToString("") { "${it.key} ${it.value}\n" }

    fun readFit(f: File): LinkedHashMap<String, String> {
        if (!f.isFile) throw GenException("fit: ${f.name} is missing")
        val out = LinkedHashMap<String, String>()
        f.readLines().forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            val p = line.split(' ')
            if (p.size != 2 || p[1] !in classNames) throw GenException("fit: ${f.name}:${i + 1} needs a token name and one of $classNames")
            if (out.put(p[0], p[1]) != null) throw GenException("fit: ${f.name}:${i + 1} names ${p[0]} twice")
        }
        return out
    }

    private fun fitFor(t: Tokens, fit: File): LinkedHashMap<String, String> {
        val cls = readFit(fit)
        if (cls.keys.toSet() != t.board) throw GenException("fit: ${fit.name} does not list exactly the board tokens")
        return cls
    }

    fun checkFit(root: File, fit: File) {
        val want = classes(root)
        val have = readFit(fit)
        if (have.toList() != want.toList()) {
            val diff = (want.keys + have.keys).filter { want[it] != have[it] }.take(10)
                .joinToString(", ") { "$it ${have[it] ?: "absent"} computes ${want[it] ?: "absent"}" }
            throw GenException("checkTokFit: ${fit.name} differs from the numeric classes: ${diff.ifEmpty { "order" }}")
        }
    }

    private fun safe(name: String) = if (name in keywords) "`$name`" else name

    fun tokId(name: String) = safe(name.replace('.', '_'))

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    private class Lit(val type: String, val text: String)

    private fun lit(v: String) = when {
        number.matches(v) -> Lit("Float", "${v}f")
        color.matches(v) -> Lit("Long", "0xFF${v.substring(1).uppercase()}L")
        else -> Lit("String", quote(v))
    }

    fun tokKt(root: File, name: String = "Tok"): String {
        val t = tokens(root)
        val o = StringBuilder("package $pkg\n\nobject $name {\n")
        for ((i, n) in t.names.withIndex()) {
            if (n in t.board) continue
            o.append("    const val ${tokId(n)} = ${lit(t.each[0][i].value).text}\n")
        }
        return o.append("}\n").toString()
    }

    fun checkTok(root: File, tokKt: String) {
        val t = tokens(root)
        val line = Regex("^    const val (\\S+) = (.+)$")
        val emitted = tokKt.lines().mapNotNull { line.find(it) }.associate { it.groupValues[1] to it.groupValues[2] }
        for ((i, n) in t.names.withIndex()) {
            val id = tokId(n)
            val got = emitted[id]
            if (n in t.board) {
                if (got != null) throw GenException("checkTokTok: board token $n is in Tok")
                continue
            }
            if (got == null) throw GenException("checkTokTok: Tok has no $n")
            for ((b, board) in boards.withIndex()) {
                val want = lit(t.each[b][i].value).text
                if (got != want) throw GenException("checkTokTok: Tok.$id is $got, ${board.second} has $want")
            }
        }
        val extra = emitted.keys - t.names.filter { it !in t.board }.map(::tokId).toSet()
        if (extra.isNotEmpty()) throw GenException("checkTokTok: Tok has names no board file has: $extra")
    }

    private fun boardTypes(t: Tokens, kept: List<Int>) = kept.associateWith { i ->
        t.each.map { lit(it[i].value).type }.toSet().singleOrNull() ?: "String"
    }

    private fun keptRows(t: Tokens, cls: Map<String, String>) =
        t.names.indices.filter { t.names[it] in t.board && cls[t.names[it]] != "x" }

    fun tokBoardKt(root: File, fit: File): String {
        val t = tokens(root)
        return boardKt("TokBoard", t, keptRows(t, fitFor(t, fit)))
    }

    fun oldBoardKt(root: File): String {
        val t = tokens(root)
        return boardKt("OldBoard", t, t.names.indices.filter { t.names[it] in t.board && !renderer(t.names[it]) })
    }

    private fun boardKt(name: String, t: Tokens, kept: List<Int>): String {
        val types = boardTypes(t, kept)
        val o = StringBuilder("package $pkg\n\nobject $name {\n    abstract class Board {\n")
        for (i in kept) o.append("        abstract val ${tokId(t.names[i])}: ${types[i]}\n")
        o.append("    }\n")
        for ((b, board) in boards.withIndex()) {
            o.append("\n    object ${board.first} : Board() {\n")
            for (i in kept) {
                val v = t.each[b][i].value
                val text = if (types[i] == "String") quote(v) else lit(v).text
                o.append("        override val ${tokId(t.names[i])}: ${types[i]} = $text\n")
            }
            o.append("    }\n")
        }
        return o.append("}\n").toString()
    }

    fun tokFitKt(root: File, fit: File): String {
        val t = tokens(root)
        val cls = fitFor(t, fit)
        val kept = keptRows(t, cls)
        val types = boardTypes(t, kept)
        val o = StringBuilder("package $pkg\n\nimport kotlin.math.roundToInt\n\nobject TokFit {\n")
        o.append("    private val boards = listOf(TokBoard.v1, TokBoard.r1, TokBoard.a105)\n\n")
        o.append("    fun at(h: Float, ppu: Float, free: TokBoard.Board? = null): TokBoard.Board {\n")
        o.append("        val px = (h * ppu).roundToInt()\n")
        o.append("        val snap = boards.firstOrNull { (it.canvasH * ppu).roundToInt() == px }\n")
        o.append("        if (snap != null) return At(snap, snap, 0f, snap, ppu, h, false, free)\n")
        o.append("        val low = h < TokBoard.r1.canvasH\n")
        o.append("        val lo = if (low) TokBoard.v1 else TokBoard.r1\n")
        o.append("        val hi = if (low) TokBoard.r1 else TokBoard.a105\n")
        o.append("        val above = h > TokBoard.a105.canvasH\n")
        o.append("        val step = if (low) TokBoard.v1 else if (above) TokBoard.a105 else TokBoard.r1\n")
        o.append("        return At(lo, hi, (h - lo.canvasH) / (hi.canvasH - lo.canvasH), step, ppu, h, above, free)\n")
        o.append("    }\n\n")
        o.append("    private class At(\n")
        o.append("        fitLo: TokBoard.Board,\n        fitHi: TokBoard.Board,\n        fitT: Float,\n")
        o.append("        fitStep: TokBoard.Board,\n        fitPpu: Float,\n        fitH: Float,\n        fitAbove: Boolean,\n        fitFree: TokBoard.Board?,\n")
        o.append("    ) : TokBoard.Board() {\n")
        for (i in kept) {
            val id = tokId(t.names[i])
            val c = cls.getValue(t.names[i])
            if ((c in linear || c == "ppu") && types[i] != "Float") throw GenException("tokens: ${t.names[i]} is class $c but not a number")
            val lin = "fitLo.$id + (fitHi.$id - fitLo.$id) * fitT"
            val e = when (c) {
                "ppu" -> "TokBoard.v1.$id / TokBoard.v1.u * fitPpu"
                "step" -> "fitStep.$id"
                "budget" -> "TokBoard.v1.$id"
                "hr" -> "if (fitAbove) TokBoard.a105.$id * fitH / TokBoard.a105.canvasH else $lin"
                "h", "v1h" -> "if (fitAbove) TokBoard.a105.$id + (fitH - TokBoard.a105.canvasH) else $lin"
                "v1c" -> "if (fitAbove) TokBoard.a105.$id else $lin"
                "free" -> "fitFree?.$id ?: ($lin)"
                else -> lin
            }
            o.append("        override val $id: ${types[i]} = $e\n")
        }
        return o.append("    }\n}\n").toString()
    }

    class Glyph(val name: String, val fill: Boolean, val path: String)

    class GlyphSet(val box: Int, val stroke: Int, val list: List<Glyph>)

    fun glyphs(root: File): GlyphSet {
        val f = File(root, "player/gfx/glyph.cpp")
        if (!f.isFile) throw GenException("glyphs: ${f.name} is missing")
        val text = f.readText()
        fun constant(name: String) = Regex("constexpr int $name = ([0-9]+);").find(text)?.groupValues?.get(1)?.toInt()
            ?: throw GenException("glyphs: ${f.name} has no $name")
        val start = text.indexOf("const GlyphPath kGlyphs[] = {")
        if (start < 0) throw GenException("glyphs: ${f.name} has no kGlyphs table")
        val end = text.indexOf("\n};", start)
        if (end < 0) throw GenException("glyphs: ${f.name} kGlyphs table has no end")
        val table = text.substring(start, end)
        val row = Regex("\\{\"([A-Za-z0-9]+)\",\\s*(true|false),\\s*((?:\"[^\"]*\"\\s*)+)\\}")
        val piece = Regex("\"([^\"]*)\"")
        val list = row.findAll(table).map { m ->
            Glyph(m.groupValues[1], m.groupValues[2] == "true", piece.findAll(m.groupValues[3]).joinToString("") { it.groupValues[1] })
        }.toList()
        val rows = table.lines().count { it.trimStart().startsWith("{\"") }
        if (list.isEmpty() || list.size != rows) throw GenException("glyphs: read ${list.size} of $rows kGlyphs rows")
        if (list.map { it.name }.toSet().size != list.size) throw GenException("glyphs: a glyph name repeats")
        return GlyphSet(constant("kBox"), constant("kStroke"), list)
    }

    fun glyphsKt(root: File): String {
        val g = glyphs(root)
        val o = StringBuilder("package $pkg\n\nclass Glyph(val name: String, val fill: Boolean, val path: String)\n\n")
        o.append("object Glyphs {\n    const val box = ${g.box}f\n    const val stroke = ${g.stroke}f\n")
        for (e in g.list) o.append("    val ${safe(e.name)} = Glyph(${quote(e.name)}, ${e.fill}, ${quote(e.path)})\n")
        o.append("    val all: List<Glyph> = listOf(${g.list.joinToString(", ") { safe(it.name) }})\n")
        return o.append("}\n").toString()
    }

    val drawn = listOf("play", "pause", "next", "previous")
    private const val WHITE = "#FFFFFFFF"

    private fun shared(t: Tokens, name: String): String {
        if (name in t.board) throw GenException("res: $name is a board token")
        return t.value(0, name)
    }

    private fun plain(v: BigDecimal) = v.stripTrailingZeros().toPlainString()

    fun glyphDrawables(root: File): Map<String, String> {
        val g = glyphs(root)
        val out = LinkedHashMap<String, String>()
        for (name in drawn) {
            val e = g.list.firstOrNull { it.name == name } ?: throw GenException("res: glyph.cpp has no $name row")
            if (!e.fill) throw GenException("res: glyph $name is not filled")
            out["drawable/fp_glyph_$name.xml"] = vector(g.box.toString(), "        android:fillColor=\"$WHITE\"\n", e.path)
        }
        val t = tokens(root)
        val d = dec(shared(t, "npRing"))
        val w = dec(shared(t, "npRingStroke"))
        val two = BigDecimal(2)
        val c = d.divide(two, mc)
        val r = (d - w).divide(two, mc)
        val x = plain(c)
        val ring = "M $x ${plain(c - r)} A ${plain(r)} ${plain(r)} 0 1 1 $x ${plain(c + r)} A ${plain(r)} ${plain(r)} 0 1 1 $x ${plain(c - r)} Z"
        out["drawable/fp_ring.xml"] = vector(plain(d), "        android:strokeColor=\"$WHITE\"\n        android:strokeWidth=\"${plain(w)}\"\n", ring)
        val colors = listOf("color.bg", "color.tile").joinToString("") { n ->
            "    <color name=\"fp_${n.replace('.', '_')}\">#FF${shared(t, n).substring(1).uppercase()}</color>\n"
        }
        out["values/fp_colors.xml"] = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n$colors</resources>\n"
        return out
    }

    private fun vector(box: String, paint: String, path: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:width=\"${box}dp\"\n    android:height=\"${box}dp\"\n" +
            "    android:viewportWidth=\"$box\"\n    android:viewportHeight=\"$box\">\n" +
            "    <path\n$paint        android:pathData=\"$path\" />\n</vector>\n"
}
