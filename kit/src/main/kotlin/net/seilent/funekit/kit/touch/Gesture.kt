package net.seilent.funekit.kit.touch

import kotlin.math.abs

enum class Axis { Vertical, Horizontal }

enum class Swipe { None, Left, Right, Up, Down }

private const val ALONG = 10f
private const val ACROSS = 14f

fun axisOf(dx: Float, dy: Float): Axis = if (abs(dy) < abs(dx)) Axis.Horizontal else Axis.Vertical

class Slop(val slop: Float, val swipe: Float) {
    fun tap(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val dx = x1 - x0
        val dy = y1 - y0
        return dx * dx + dy * dy <= slop * slop
    }

    fun swipeOf(dx: Float, dy: Float): Swipe {
        if (abs(dx) <= swipe || abs(dx) * ALONG <= abs(dy) * ACROSS) return Swipe.None
        return if (dx < 0) Swipe.Left else Swipe.Right
    }

    fun swipeUpOf(dx: Float, dy: Float): Swipe {
        if (dy >= -swipe || abs(dy) * ALONG <= abs(dx) * ACROSS) return Swipe.None
        return Swipe.Up
    }

    fun swipeDownOf(dx: Float, dy: Float): Swipe {
        if (dy <= swipe || abs(dy) * ALONG <= abs(dx) * ACROSS) return Swipe.None
        return Swipe.Down
    }
}

class Lift(val swipe: Swipe, val turned: Boolean, val x: Float, val origin: Float)

class Gesture(private val s: Slop) {
    enum class State { Idle, Pending, Vertical, Horizontal, NoCommit }

    enum class Edge { None, Left, Right }

    private class Sample(val x: Float, val y: Float, val ms: Long)

    var state = State.Idle
        private set
    var edge = Edge.None
        private set
    var turned = false
        private set
    var originX = 0f
        private set
    var liftX = 0f
        private set

    private var y0 = 0f
    private var ax = 0f
    private var ay = 0f
    private var px = 0f
    private var py = 0f
    private var lx = 0f
    private var ly = 0f
    private var prev = Sample(0f, 0f, 0)
    private var last = Sample(0f, 0f, 0)
    private var samples = 0
    private var bottom = false
    private var vertical = false
    private var down = false
    private var turn = false
    private var land = false
    private var kept = false

    fun arm(x: Float, y: Float, edge: Edge = Edge.None, bottom: Boolean = false, turn: Boolean = false, ms: Long = 0) {
        state = State.Pending
        last = Sample(x, y, ms)
        samples = 1
        originX = x
        y0 = y
        this.edge = edge
        this.bottom = bottom
        this.turn = turn
        vertical = false
        down = false
        turned = false
        land = false
        kept = false
    }

    fun armVertical(x: Float, y: Float, down: Boolean, ms: Long = 0) {
        arm(x, y, Edge.None, false, false, ms)
        vertical = true
        this.down = down
    }

    fun cancel() {
        if (state != State.Idle) state = State.NoCommit
    }

    fun keep() {
        kept = true
    }

    fun move(x: Float, y: Float, ms: Long = 0): Boolean {
        prev = last
        last = Sample(x, y, ms)
        samples++
        if (state == State.Vertical && turn) return turn(x, y)
        if (state == State.Horizontal && turned && turn && !kept && axisOf(x - originX, y - y0) == Axis.Vertical) {
            state = State.Vertical
            turned = false
            ax = x
            px = x
            ay = y
            py = y
            return false
        }
        if (state != State.Pending || s.tap(originX, y0, x, y)) return false
        state = if (axisOf(x - originX, y - y0) == Axis.Horizontal) State.Horizontal else State.Vertical
        ax = x
        px = x
        ay = y
        py = y
        return true
    }

    private fun turn(x: Float, y: Float): Boolean {
        if (land) {
            land = false
            val on = x - lx
            val was = lx - ax
            if (on == 0f || (on < 0) != (was < 0)) {
                ax = lx
                ay = ly
            }
        } else if (!s.tap(px, py, x, y) && axisOf(x - ax, y - ay) == Axis.Horizontal) {
            land = true
            lx = x
            px = x
            ly = y
            py = y
            return false
        }
        px = x
        py = y
        if (axisOf(x - ax, y - ay) == Axis.Vertical) {
            ax = x
            ay = y
            return false
        }
        if (s.tap(ax, ay, x, y)) return false
        state = State.Horizontal
        originX = ax
        y0 = ay
        edge = Edge.None
        turned = true
        return true
    }

    fun commitAt(x: Float, y: Float): Swipe =
        if (state == State.Horizontal) s.swipeOf(x - originX, y - y0) else Swipe.None

    private fun liftPoint(ms: Long, bare: Boolean): Sample? {
        if (!bare || samples <= 1 || (state != State.Vertical && state != State.Horizontal)) return null
        val step = last.ms - prev.ms
        val gap = ms - last.ms
        if (step <= 0 || gap < 0 || gap > step) return null
        return Sample(last.x + (last.x - prev.x) * gap / step, last.y + (last.y - prev.y) * gap / step, ms)
    }

    private fun copy(): Gesture = Gesture(s).also {
        it.state = state
        it.edge = edge
        it.turned = turned
        it.originX = originX
        it.liftX = liftX
        it.y0 = y0
        it.ax = ax
        it.ay = ay
        it.px = px
        it.py = py
        it.lx = lx
        it.ly = ly
        it.prev = prev
        it.last = last
        it.samples = samples
        it.bottom = bottom
        it.vertical = vertical
        it.down = down
        it.turn = turn
        it.land = land
        it.kept = kept
    }

    fun liftCommit(x: Float, y: Float, ms: Long): Lift {
        val g = copy()
        val swipe = g.up(x, y, ms, true)
        return Lift(swipe, g.turned, g.liftX, g.originX)
    }

    fun up(x: Float, y: Float, ms: Long = 0, bare: Boolean = false): Swipe {
        var ux = x
        var uy = y
        liftPoint(ms, bare)?.let {
            move(it.x, it.y, ms)
            ux = it.x
            uy = it.y
        }
        liftX = ux
        val was = state
        state = State.Idle
        val dx = ux - originX
        val dy = uy - y0
        if (vertical) {
            if (was != State.Pending && was != State.Vertical) return Swipe.None
            if (s.swipeUpOf(dx, dy) == Swipe.Up) return Swipe.Up
            return if (down) s.swipeDownOf(dx, dy) else Swipe.None
        }
        if (bottom && (was == State.Pending || was == State.Vertical) && s.swipeUpOf(dx, dy) == Swipe.Up) return Swipe.Up
        if (was != State.Pending && was != State.Horizontal) return Swipe.None
        return s.swipeOf(dx, dy)
    }
}
