package net.seilent.funekit.kit.saver

import net.seilent.funekit.Weight
import net.seilent.funekit.gen.TokBoard
import org.junit.Assert.fail
import java.util.concurrent.Executor

internal class BoxFont : SaverGlyphs {
    private fun upper(s: String) = buildString { for (c in s) append(if (c in 'a'..'z') c.uppercaseChar() else c) }

    fun adv64(cp: Int, px: Int): Int = when {
        cp == ' '.code -> px * 64 * 3 / 10
        cp == '1'.code -> px * 64 * 4 / 10
        cp in '0'.code..'9'.code -> px * 64 * 55 / 100
        cp == ':'.code -> px * 64 / 4
        cjkCp(cp) -> px * 64
        else -> px * 64 * 6 / 10
    }

    override fun run(st: SaverStyle, s: String): GlyphRun {
        val b = utf8(upper(s))
        val o = Utf8Next(0, 0)
        val g = ArrayList<RunGlyph>()
        var at = 0
        var pen = 0
        while (at < b.size) {
            utf8Next(b, at, o)
            g += RunGlyph(if (st.saver && cjkCp(o.cp)) 4 else 0, o.cp, pen, 0, 0, at)
            pen += adv64(o.cp, st.px) + st.track64
            at = o.at
        }
        return GlyphRun(g, pen)
    }

    override fun width64(st: SaverStyle, s: String): Int = run(st, s).width64

    override fun box(face: Int, px: Int, gid: Int): SaverRect {
        if (gid == ' '.code) return SaverRect.NONE
        val w = adv64(gid, px) / 64
        val lsb = px / 20
        val cap = if (cjkCp(gid)) px * 8 / 10 else px * 7 / 10
        val tail = if (cjkCp(gid)) px / 10 else if (gid == 'Q'.code || gid == 'J'.code || gid == ','.code) px / 8 else 0
        return SaverRect(lsb, -cap, maxOf(lsb + 1, w - lsb), tail)
    }

    override fun hbox(face: Int, px: Int, gid: Int): IntArray? = box(face, px, gid).let {
        if (it.empty) null else intArrayOf(it.x0 * 64, it.x1 * 64)
    }

    override fun vbox(face: Int, px: Int, gid: Int): IntArray? = box(face, px, gid).let {
        if (it.empty) null else intArrayOf(it.y0 * 64 - 1, it.y1 * 64 + 1)
    }
}

internal fun fosWeight(w: Weight): Int = when (w) {
    Weight.Light -> 0
    Weight.Regular -> 1
    Weight.Bold -> 2
    Weight.SemiLight -> 3
}

internal fun hex(s: String): String = if (s.isEmpty()) "-" else utf8(s).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

internal fun unhex(h: String): String =
    if (h == "-") "" else String(ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, Charsets.UTF_8)

internal class RecordedFont(lines: List<String>) : SaverGlyphs {
    private val runs = HashMap<String, GlyphRun>()
    private val w64 = HashMap<String, Int>()
    private val boxes = HashMap<String, SaverRect?>()
    private val hboxes = HashMap<String, IntArray?>()
    private val vboxes = HashMap<String, IntArray?>()
    val asked = ArrayList<String>()

    init {
        for (line in lines) {
            val p = line.split(' ')
            if (p[0] != "font") continue
            when (p[1]) {
                "run" -> {
                    val n = p[8].toInt()
                    runs[p.subList(2, 7).joinToString(" ")] = GlyphRun(
                        (0 until n).map { k ->
                            val f = p[9 + k].split(',').map { it.toInt() }
                            RunGlyph(f[0], f[1], f[2], f[3], f[4], f[5])
                        },
                        p[7].toInt(),
                    )
                }
                "w64" -> w64[p.subList(2, 7).joinToString(" ")] = p[7].toInt()
                "box" -> boxes[p.subList(2, 5).joinToString(" ")] =
                    if (p[5] == "1") SaverRect(p[6].toInt(), p[7].toInt(), p[8].toInt(), p[9].toInt()) else null
                "hbox" -> hboxes[p.subList(2, 5).joinToString(" ")] = if (p[5] == "1") intArrayOf(p[6].toInt(), p[7].toInt()) else null
                "vbox" -> vboxes[p.subList(2, 5).joinToString(" ")] = if (p[5] == "1") intArrayOf(p[6].toInt(), p[7].toInt()) else null
            }
        }
    }

    private fun style(st: SaverStyle, s: String) = "${fosWeight(st.weight)} ${st.px} ${st.track64} ${if (st.saver) 1 else 0} ${hex(s)}"

    private fun <T> get(m: Map<String, T>, k: String, what: String): T {
        if (!m.containsKey(k)) {
            asked += "$what $k"
            throw AssertionError("the kit asked $what $k, which funeOS never asked")
        }
        @Suppress("UNCHECKED_CAST")
        return m[k] as T
    }

    override fun run(st: SaverStyle, s: String): GlyphRun = get(runs, style(st, s), "run")

    override fun width64(st: SaverStyle, s: String): Int = get(w64, style(st, s), "w64")

    override fun box(face: Int, px: Int, gid: Int): SaverRect? = get(boxes, "$face $px $gid", "box")

    override fun hbox(face: Int, px: Int, gid: Int): IntArray? = get(hboxes, "$face $px $gid", "hbox")

    override fun vbox(face: Int, px: Int, gid: Int): IntArray? = get(vboxes, "$face $px $gid", "vbox")
}

internal fun fixture(name: String): List<String>? =
    SaverGlyphs::class.java.getResourceAsStream("/saver/$name")?.bufferedReader()?.readLines()

internal fun needFixture(name: String, how: String): List<String> =
    fixture(name) ?: run {
        fail("fixture saver/$name is missing: $how")
        emptyList()
    }

internal val boards = listOf("a105" to TokBoard.a105, "r1" to TokBoard.r1, "v1" to TokBoard.v1)

internal fun a105(font: SaverGlyphs = BoxFont()): SaverScenes = SaverScenes(TokBoard.a105, TokBoard.a105.u, 720, 1280, font)

internal fun SaverScenes.kit(title: String = "My Name Is Jonas", artist: String = "Weezer", album: String = "Pinkerton", pos: Long = 75000): SaverScenes {
    setTrack(title, artist, album, false)
    setPosition { pos }
    return this
}

internal class Queue : Executor {
    val jobs = ArrayList<Runnable>()

    override fun execute(command: Runnable) {
        jobs += command
    }

    fun drain() {
        while (jobs.isNotEmpty()) jobs.removeAt(0).run()
    }
}

internal fun b(v: Boolean) = if (v) 1 else 0

internal fun SaverScenes.probeDump(): List<String> {
    val out = ArrayList<String>()
    for ((i, l) in lanes.withIndex()) {
        val t = l.t
        val head = listOf(
            "text", i, hex(t.s), fosWeight(t.st.weight), t.st.px, t.st.track64, b(t.st.saver), t.ink0, t.ink1, t.top, t.bottom,
            t.cut, t.ink1a, t.ink0b, t.tm0, t.tm1, t.top64, t.bottom64, t.cap64, t.foot64[0], t.foot64[1], t.edge0, t.edge1,
            b(t.timed), b(t.two), t.g.size,
        ).joinToString(" ")
        out += head + t.g.joinToString("") { " ${it.face},${it.gid},${it.ox},${it.oy},${it.box.x0},${it.box.y0},${it.box.x1},${it.box.y1},${it.byte}" }
        out += "lay $i ${l.period} ${l.count} ${b(l.half)} ${b(l.two)} ${hex(l.time)}"
        out += listOf(
            "out", i, l.t0 - sceneAt, l.out - sceneAt, b(l.skip), b(l.gave), b(l.unfit), l.kept, l.cutBy, b(l.cutFell), l.brkCopy,
            l.brkKeep, l.brkStart, b(l.redone), l.redoCut, l.block.lane, l.block.keep, l.block.len64, l.across, l.ox, l.oy, l.umin,
            l.umax, l.u0, l.rest.x, l.rest.y, l.grow, l.holdRegion, l.holdGlyph, l.contact, b(l.capped),
        ).joinToString(" ")
    }
    for (c in crosses) out += "cross ${c.x} ${c.m} ${c.ax} ${c.am} ${b(c.kept)}"
    out += "scene ${scene.name} $sceneAt $gap $packPasses $glyphSteps $crossLeft"
    return out
}
