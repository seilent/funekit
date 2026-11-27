package net.seilent.funekit.kit.saver

import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.motion.Curves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

class SaverMotionTest {
    private fun pen(e: SaverScenes, i: Int, t: Long) = e.penOf(e.lanes[i], t)

    @Test
    fun everyLaneEntersAtItsWindowStartAndStepsQuarterPxAlongItsAxis() {
        var bad = ""
        for (s in SaverScene.entries) {
            val e = a105().kit()
            e.show(0, 1)
            e.shotScene(s, 1000)
            var checked = 0
            var lanes = 0
            for ((i, l) in e.lanes.withIndex()) {
                val sp = l.spec
                val on = if (sp.off != 0L) 1000 + secsMs(sp.on) else l.t0
                lanes++
                if (l.gave) continue
                val p0 = pen(e, i, on)
                val before = pen(e, i, on - 1).x == NO_PEN
                val after = sp.off == 0L || pen(e, i, 1000 + secsMs(sp.off)).x == NO_PEN
                if (p0.x == NO_PEN || !before || !after) {
                    bad += " ${s.name}${i + 1} entry"
                    continue
                }
                val diag = turned(sp.angle)
                val entry = (sp.look == Look.Eased || sp.look == Look.Slide) && sp.shift != 0L
                val v = e.toPx(abs(sp.speed)) / 65536.0
                val shift = if (entry) e.toPx(sp.shift) / 65536.0 else 0.0
                val dur = if (entry) sp.shiftT shr 16 else 0L
                val sg = if (sp.speed >= 0) 1 else -1
                fun q(t: Long): Double {
                    val x = v * t / 1000 + shift
                    return if (diag) x / sqrt(2.0) else x
                }
                fun whole(x: Double) = abs(4 * x - round(4 * x)) >= 4e-3
                fun axis(d: Int, x: Double): Int {
                    val k = floor(4 * x).toInt()
                    return if (d > 0) k / PHASES else -((k + PHASES - 1) / PHASES)
                }
                val dir = when {
                    sp.angle == 0 -> SaverPt(sg, 0)
                    sp.angle == UP -> SaverPt(0, -sg)
                    sp.angle > 0 -> SaverPt(sg, sg)
                    else -> SaverPt(sg, -sg)
                }
                val q0 = q(on - l.t0)
                val late = on > l.t0
                if (late && !whole(q0)) continue
                for (t in longArrayOf(1, 17, 333, 451, 1000, 2500, 4999, 9001)) {
                    val p = pen(e, i, on + t)
                    val x = q(on - l.t0 + t)
                    if (p.x == NO_PEN || t < dur || !whole(x)) continue
                    val want = SaverPt(
                        if (dir.x != 0) axis(dir.x, x) - (if (late) axis(dir.x, q0) else 0) else 0,
                        if (dir.y != 0) axis(dir.y, x) - (if (late) axis(dir.y, q0) else 0) else 0,
                    )
                    val got = SaverPt(p.x - p0.x, p.y - p0.y)
                    checked++
                    if (got != want) bad += " ${s.name}${i + 1} at $t $got want $want"
                }
            }
            assertTrue("${s.name} checked $checked of $lanes lanes", checked > lanes * 3)
        }
        assertEquals("", bad)
    }

    private fun axis(angle: Int, a: SaverPt, b: SaverPt): Double = when (angle) {
        0 -> abs(b.x - a.x).toDouble()
        UP -> abs(b.y - a.y).toDouble()
        else -> abs(b.x - a.x) * sqrt(2.0)
    }

    private fun entry(e: SaverScenes, i: Int, shift: Long, dur: Long, fade: Long, what: String): String {
        var bad = ""
        val l = e.lanes[i]
        val sp = l.spec
        val v = e.toPx(abs(sp.speed)) / 65536.0
        val sh = e.toPx(shift) / 65536.0
        val p0 = pen(e, i, l.t0)
        for (t in longArrayOf(dur / 2, dur)) {
            val u = t * ONE / dur
            val c = (if (sp.look == Look.Slide) u else Curves.powOut17.fx(u)) / 65536.0
            val want = v * t / 1000 + sh * c
            val got = axis(sp.angle, p0, pen(e, i, l.t0 + t))
            if (abs(got - want) > 2.0) bad += " $what at $t ${got.toInt()} want ${want.toInt()}"
        }
        val a0 = e.alpha(l, l.t0)
        val a1 = e.alpha(l, l.t0 + maxOf(fade, 1L))
        if (if (fade != 0L) a0 != 0 || a1 != 256 else a0 != 256) bad += " $what fade $a0 $a1"
        return bad
    }

    @Test
    fun everyEntryLaneMovesByItsPanPlusItsShiftAlongItsCurve() {
        var bad = ""
        for (s in SaverScene.entries) {
            val e = a105().kit()
            e.show(0, 1)
            e.shotScene(s, 1000)
            for ((i, l) in e.lanes.withIndex()) {
                val sp = l.spec
                if ((sp.look == Look.Eased || sp.look == Look.Slide) && sp.shift != 0L && !l.skip)
                    bad += entry(e, i, sp.shift, sp.shiftT shr 16, sp.fadeT shr 16, "${s.name}${i + 1}")
            }
        }
        val first = a105().kit()
        first.pinNext(SaverScene.E)
        first.show(0, 1)
        bad += entry(
            first, 0, fx(TokBoard.a105.motion_saverWordEntryShift), fx(Tok.motion_saverWordEntry) shr 16,
            fx(Tok.motion_saverWordFadeIn) shr 16, "E1 at the show",
        )
        assertEquals("", bad)
    }

    @Test
    fun atItsOutEveryLaneStepsToItsWhooshSpeedAndFadesLinearly() {
        var bad = ""
        var read = 0
        for (s in SaverScene.entries) {
            val e = a105().kit()
            e.show(0, 1)
            e.shotScene(s, 0)
            val end = e.sceneEnd()
            val w = e.whoosh
            for ((i, l) in e.lanes.withIndex()) {
                if (!e.laneIn(l, end) || l.out != end || !e.laneIn(l, end + 100)) continue
                val sp = l.spec
                fun along(t0: Long, t1: Long): Double {
                    val a = e.fineOf(l, t0)
                    val b = e.fineOf(l, t1)
                    return axis(sp.angle, a, b) / PHASES
                }
                val v = e.toPx(sp.whoosh) / 65536.0
                val pan = e.toPx(abs(sp.speed)) / 65536.0
                val fast = along(end, end + 100)
                val before = along(end - 200, end)
                val a0 = e.alpha(l, end)
                val ah = e.alpha(l, end + w / 2)
                var last = end + w - 1
                if (!sp.repeat || l.count > 0) while (last > end + 100 && !e.laneIn(l, last)) last--
                val held = last - 50 < end + 100 || abs(along(last - 50, last) - v / 20) <= 1.5
                read++
                if (abs(fast - v / 10) > 1.5 || abs(before - pan / 5) > 1.5 || e.laneIn(l, end + w) || !e.laneIn(l, last) || !held ||
                    abs(2 * ah - a0) > a0 / 10 + 2
                ) bad += " ${s.name}${i + 1} ${fast.toInt()} px"
            }
        }
        assertTrue("read $read", read > 20)
        assertEquals("", bad)
    }

    @Test
    fun aTimeLaneRelaysEachSecondAndKeepsItsPen() {
        var p = 15200L
        val k = a105().kit()
        k.setPosition { p }
        k.show(0, 1)
        k.shotScene(SaverScene.D, 0)
        val still = a105().kit(pos = 15200)
        still.show(0, 1)
        still.shotScene(SaverScene.D, 0)
        val t0 = secsMs(fx(Tok.motion_saverD1In))
        val lays = k.timeLays
        var pens = true
        var text = true
        var t = t0
        while (t < t0 + 10000) {
            p = 15200 + (t - t0)
            k.step(t)
            still.step(t)
            pens = pens && k.penOf(k.lanes[0], t) == still.penOf(still.lanes[0], t)
            text = text && k.lanes[0].t.s.startsWith(formatTime(p))
            t += 16
        }
        assertEquals(10, k.timeLays - lays)
        assertTrue("pens kept", pens)
        assertTrue("text follows the position", text)
    }

    @Test
    fun timeDigitsSitInFixedZeroCells() {
        val ones = a105().kit(pos = 11 * 60000 + 11000)
        val eights = a105().kit(pos = 18 * 60000 + 48000)
        for (e in listOf(ones, eights)) {
            e.show(0, 1)
            e.shotScene(SaverScene.D, 0)
        }
        val a = ones.lanes[0].t
        val b = eights.lanes[0].t
        val colon = 2
        assertEquals("11:11", a.s.substring(0, 5))
        assertEquals("18:48", b.s.substring(0, 5))
        assertEquals(a.g[colon].ox, b.g[colon].ox)
        assertEquals(a.tm0, b.tm0)
        assertEquals(a.tm1, b.tm1)
        val f = BoxFont()
        val st = a.st.copy(weight = ones.lanes[0].spec.w)
        val cell = f.width64(st, "0")
        val one = f.width64(st, "1")
        assertEquals(cell / 64.0, (a.g[1].ox - a.g[0].ox).toDouble(), 1.0)
        assertEquals((cell - one) / 2 / 64.0, (a.g[1].ox - b.g[1].ox).toDouble(), 1.0)
    }

    @Test
    fun aRepeatLaneTilesEveryAdvancePlusOneSpace() {
        val f = BoxFont()
        val e = a105(f).kit("Hi", "Ab", "")
        e.show(0, 1)
        e.shotScene(SaverScene.E, 0)
        val st = e.style(fx(TokBoard.a105.saverGiant), net.seilent.funekit.Weight.SemiLight)
        val want = (f.run(st, "Ab").width64 + f.width64(st, " ") + 32) shr 6
        assertEquals(want, e.lanes[0].period)
        assertEquals(e.px(fx(TokBoard.a105.saverE2Period)), e.lanes[1].period)
    }

    @Test
    fun aLaneEntersJustOffItsLeadingEdge() {
        var read = 0
        for (s in SaverScene.entries) {
            val e = a105().kit()
            e.show(0, 1)
            e.shotScene(s, 0)
            for (l in e.lanes) {
                val sp = l.spec
                if (l.skip || l.gave || sp.angle != 0 || sp.roll != 0L || sp.tail || sp.src == Src.Art || sp.join != 0L || sp.top) continue
                val p = e.penOf(l, l.t0)
                if (p.x == NO_PEN) continue
                read++
                if (sp.speed < 0) assertEquals("${s.name} ${l.t.s}", 720.0, p.x + l.t.edge0 / 64.0, 1.0)
                else assertEquals("${s.name} ${l.t.s}", 0.0, p.x + l.t.edge1 / 64.0, 1.0)
            }
        }
        assertTrue("read $read", read > 10)
    }

    @Test
    fun aTimeLaneLaidBeforeTenMinutesKeepsItsPeriodAndCountAcrossThem() {
        for ((scene, i) in listOf(SaverScene.H to 7, SaverScene.L to 0)) {
            var p = 599500L
            val e = a105().kit("愛 think so,", "DECO*27 FEAT. とぴ", "相愛性理論")
            e.setPosition { p }
            e.show(0, 1)
            e.shotScene(scene, 0)
            val l = e.lanes[i]
            val t = l.t0 + 100
            e.step(t)
            val per = l.period
            val n = l.count
            val before = l.t.s
            assertTrue("${scene.name}${i + 1} grows", l.grow > 0)
            p = 600500
            e.step(t + 1000)
            assertTrue("${scene.name}${i + 1} period $per to ${l.period}", per > 0 && l.period >= per)
            assertEquals(n, l.count)
            assertTrue(l.t.s != before)
        }
    }
}
