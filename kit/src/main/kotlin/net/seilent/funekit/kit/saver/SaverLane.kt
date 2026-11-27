package net.seilent.funekit.kit.saver

import net.seilent.funekit.Weight

internal class G(val face: Int, val gid: Int, val ox: Int, val oy: Int, val box: SaverRect, val byte: Int)

internal class Text {
    var s = ""
    var st = SaverStyle(Weight.Regular, 0, 0, false)
    var g: List<G> = emptyList()
    var ink0 = 0
    var ink1 = 0
    var top = 0
    var bottom = 0
    var cut = 0
    var ink1a = 0
    var ink0b = 0
    var tm0 = 0
    var tm1 = 0
    var top64 = 0
    var bottom64 = 0
    var cap64 = 0
    val foot = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE)
    val foot64 = intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE)
    var edge0 = 0
    var edge1 = 0
    var timed = false
    var two = false

    fun copy(): Text = Text().also {
        it.s = s
        it.st = st
        it.g = g
        it.ink0 = ink0
        it.ink1 = ink1
        it.top = top
        it.bottom = bottom
        it.cut = cut
        it.ink1a = ink1a
        it.ink0b = ink0b
        it.tm0 = tm0
        it.tm1 = tm1
        it.top64 = top64
        it.bottom64 = bottom64
        it.cap64 = cap64
        foot.copyInto(it.foot)
        foot64.copyInto(it.foot64)
        it.edge0 = edge0
        it.edge1 = edge1
        it.timed = timed
        it.two = two
    }
}

internal class Span(var b0: Int = 0, var b1: Int = Int.MAX_VALUE) {
    fun copy() = Span(b0, b1)
}

internal class Blocker(var lane: Int = -1, var keep: Int = 0, var len64: Long = 0) {
    fun copy() = Blocker(lane, keep, len64)
}

internal class Break(val copy: Int = 0, val keep: Int = 0, val add: Int = 0)

internal class Seg(var a: Long, var b: Long)

internal class Need(var k: Int = 0, var kind: Int = 0) {
    var ry0 = 0.0
    var ry1 = 0.0
    var rk0 = 0.0
    var rk1 = 0.0
    var ryv0 = 0.0
    var ryv1 = 0.0
    var rkv0 = 0.0
    var rkv1 = 0.0
}

internal class Box(var u0: Double, var u1: Double, var v0: Double, var v1: Double)

internal class Probe {
    var contact = -1L
    var run = 0L
    var total = 0L
    var thick = 0.0
    var steps = 0
    var left = 0
    var at0 = false
    var capped = false
}

internal class Cross(
    val x: Int,
    val m: Int,
    val ry0: Double,
    val ry1: Double,
    val rk0: Double,
    val rk1: Double,
    var ax: Long,
    var am: Long,
    var kept: Boolean,
) {
    fun copy() = Cross(x, m, ry0, ry1, rk0, rk1, ax, am, kept)
}

internal class Quad(val p: Array<SaverPt>, val glyph: Int, val copy: Int, val part: Int)

internal class Lane(val spec: Spec) {
    var t = Text()
    var time = ""
    var ahead: List<G> = emptyList()
    var rest = SaverPt(0, 0)
    var dir = SaverPt(0, 0)
    var t0 = 0L
    var u0 = 0L
    var umin = 0L
    var umax = 0L
    var out = 0L
    var period = 0
    var count = 0
    var skip = false
    var first = false
    var job = false
    var across = 0L
    var ox = 0L
    var oy = 0L
    var half = false
    var two = false
    var aheadCut = 0
    var kept = -1
    var grow = 0
    var cutBy = -1L
    var cutFell = false
    var holdRegion = -1L
    var holdGlyph = -1L
    var contact = -1L
    var p2run = 0L
    var p2total = 0L
    var p2thick = 0.0
    var capped = false
    var unfit = false
    val span = arrayOf(Span(), Span())
    var block = Blocker()
    var pieced = false
    var gave = false
    var brkCopy = 0
    var brkKeep = 0
    var brkEdge = 0L
    var brkStart = 0L
    var redone = false
    var redoCut = -1
    var en = false
    var enShift = 0L
    var enDur = 0

    fun copy(): Lane = Lane(spec).also {
        it.t = t.copy()
        it.time = time
        it.ahead = ahead
        it.rest = rest
        it.dir = dir
        it.t0 = t0
        it.u0 = u0
        it.umin = umin
        it.umax = umax
        it.out = out
        it.period = period
        it.count = count
        it.skip = skip
        it.first = first
        it.job = job
        it.across = across
        it.ox = ox
        it.oy = oy
        it.half = half
        it.two = two
        it.aheadCut = aheadCut
        it.kept = kept
        it.grow = grow
        it.cutBy = cutBy
        it.cutFell = cutFell
        it.holdRegion = holdRegion
        it.holdGlyph = holdGlyph
        it.contact = contact
        it.p2run = p2run
        it.p2total = p2total
        it.p2thick = p2thick
        it.capped = capped
        it.unfit = unfit
        it.span[0] = span[0].copy()
        it.span[1] = span[1].copy()
        it.block = block.copy()
        it.pieced = pieced
        it.gave = gave
        it.brkCopy = brkCopy
        it.brkKeep = brkKeep
        it.brkEdge = brkEdge
        it.brkStart = brkStart
        it.redone = redone
        it.redoCut = redoCut
        it.en = en
        it.enShift = enShift
        it.enDur = enDur
    }

    fun shift(d: Long) {
        t0 += d
        out += d
    }
}
