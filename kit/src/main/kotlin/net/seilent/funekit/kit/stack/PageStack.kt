package net.seilent.funekit.kit.stack

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.MorphKind
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.MoveLeg
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.motion.arriveGroup
import net.seilent.funekit.kit.motion.npInGroup
import net.seilent.funekit.kit.motion.npLeaveGroup
import net.seilent.funekit.kit.touch.Gesture

enum class Phase { Rest, Exit, Enter }

enum class PageKind { Grid, Hub, Plain }

class Turn<P : Any>(val kind: MoveKind, val from: P?, val to: P?, val popping: Boolean, val retarget: Boolean = false, val at: Long = 0L, val row: Int = -1, val album: Long = -1L, val morph: Boolean = true, val keep: P? = null)

class PageStack<P : Any>(
    var forms: MoveForms,
    private val kindOf: (P) -> PageKind,
    private val pageBack: (P, Long) -> Boolean = { _, _ -> false },
    private val retargets: (P) -> Boolean = { true },
    val move: PageMove = PageMove(),
    private val enter: (P) -> Unit = {},
    private val drop: (P) -> Unit = {},
    private val started: (Turn<P>) -> Unit = {},
    private val starting: (Turn<P>) -> Unit = {},
    private val swapped: (P, Long) -> Unit = { _, _ -> },
    private val closing: (Overlay, Long) -> Unit = { _, _ -> },
    private val dropped: (P, Long) -> Unit = { _, _ -> },
    private val legs: (Turn<P>) -> Unit = {},
    private val ready: (P) -> Boolean = { true },
    private val isNp: (P) -> Boolean = { false },
    private val gate: InputGate? = null,
    private val albumOf: (P) -> Long = { -1L },
    private val ends: (Turn<P>) -> MoveEnds,
) {
    val pages = mutableStateListOf<P>()
    private val pushRows = ArrayList<Pair<P, Int>?>()
    var phase by mutableStateOf(Phase.Rest)
        private set
    var turn: Turn<P>? by mutableStateOf(null)
        private set
    var overlay: Overlay? by mutableStateOf(null)
        private set
    var clock by mutableLongStateOf(0L)
        private set
    var asked by mutableIntStateOf(0)
        private set
    var jumpSettled by mutableStateOf(false)
        private set
    var hideFocus by mutableStateOf(false)
        private set
    var covered by mutableStateOf(false)
        private set
    var snap by mutableIntStateOf(0)
        private set
    var entries = 0L

    var held: Held? = null
        private set
    private var devicePop = false

    private val asks = ArrayDeque<(Long) -> Unit>()
    private var moveEnds = MoveEnds()
    private var seq = 0
    private var openSeq = -1
    private var swapHeld = false
    private var waiting = false
    private var landAt = -1L
    private val gone = mutableListOf<P>()
    private var opened: PlayOpen? by mutableStateOf(null)
    private var pending = 0
    private var replayDue = false
    private var cutOwed = false
    fun heldTap(downAt: Long): Boolean = gate?.tapped(downAt) == true

    init {
        gate?.host = object : GateHost {
            override val asked: Int get() = this@PageStack.asked

            override val armed: Boolean get() = opened != null

            override val turning: Boolean
                get() = phase != Phase.Rest && opened == null && pending == 0 && overlay.let { it == null || !(it.closing || it.moving(clock)) }

            override val motion: Boolean
                get() = phase != Phase.Rest || overlay.let { it != null && (it.closing || it.moving(clock)) }

            override fun cut() = npCut()

            override fun back() = backKey()

            override fun land() = carryLand()
        }
    }

    val top: P? get() = pages.lastOrNull()

    val focusShown: Boolean get() = !hideFocus && !covered

    private val under: P? get() = pages.getOrNull(pages.lastIndex - 1)

    val moving: Boolean
        get() = phase != Phase.Rest || asks.isNotEmpty() || overlay?.let { it.closing || it.moving(clock) } == true

    val canBack: Boolean get() = pages.isNotEmpty() || overlay != null || phase != Phase.Rest

    val armed: Boolean
        get() = opened.let { it != null && (it.state == PlayOpen.State.Armed || it.state == PlayOpen.State.Released) }

    internal val due: Boolean get() = replayDue

    private fun ask(a: (Long) -> Unit) {
        asks.addLast(a)
        asked++
    }

    fun push(page: P, kind: MoveKind, row: Int = -1, album: Long = -1L) = ask { now ->
        if (phase != Phase.Rest || overlay != null) return@ask
        start(Turn(kind, top, page, popping = false, row = row, album = album), now, retarget = false)
    }

    fun pop(kind: MoveKind, album: Long = -1L) = ask { now ->
        armedLand(now)
        if (phase != Phase.Rest) land(now)
        if (pages.isEmpty()) return@ask
        start(Turn(kind, top, under, popping = true, album = album), now, retarget = false)
    }

    fun entry(): Long = entries++

    fun open(o: Overlay, hides: Boolean = o.kind == OverKind.Dlg, covers: Boolean = o.kind == OverKind.Sheet || o.kind == OverKind.Dlg) = ask { now ->
        armedLand(now)
        if (phase != Phase.Rest) land(now)
        landOverlay(now, show = false)
        if (overlay != null) return@ask
        o.open(now)
        overlay = o
        openSeq = ++seq
        snapshot(hides, covers)
    }

    fun close() = ask { now -> overlay?.let { closeOverlay(it, now) } }

    fun accept() = ask { now ->
        val o = overlay ?: return@ask
        snapshot(false, true)
        closeOverlay(o, now)
    }

    fun pick() = ask { if (overlay != null) snapshot(false, true) }

    fun swapMenu(only: Overlay? = null, swap: (Long) -> Unit) = ask { now ->
        val o = overlay
        if (o == null || o.kind != OverKind.Sheet) return@ask
        if (only != null && (o !== only || o.closing)) return@ask
        swap(now)
        o.open(now)
    }

    private fun snapshot(hide: Boolean, covers: Boolean) {
        hideFocus = hide
        covered = covers
        snap++
    }

    private fun closeOverlay(o: Overlay, now: Long) {
        val settled = !o.closing && !o.moving(now)
        val was = o.closing
        o.close(now)
        if (!was) closing(o, now)
        if (settled && o.kind == OverKind.Dlg) snapshot(false, false)
    }

    private fun dropOverlay(show: Boolean = true) {
        val was = overlay
        overlay = null
        hideFocus = false
        covered = false
        if (show && was != null) top?.let { dropped(it, clock) }
    }

    fun back(popKind: (Turn<P>) -> MoveKind) = back(false, popKind)

    fun back(device: Boolean, popKind: (Turn<P>) -> MoveKind) = ask { now ->
        if (opened != null) {
            gate?.drop()
            refuse(now)
            devicePop = device
            return@ask
        }
        val o = overlay
        when {
            o != null && !o.closing && o.moving(now) -> closeOverlay(o, now)
            o == null && reversible() -> reverse(now)
            o == null && retargetable() -> retarget(now, popKind)
            else -> landBack(now, popKind)
        }
        devicePop = device
    }

    private fun landBack(now: Long, popKind: (Turn<P>) -> MoveKind) {
        landOverlay(now)
        if (phase != Phase.Rest) land(now)
        val open = overlay
        val page = top
        if (open != null) {
            closeOverlay(open, now)
        } else if (page != null && !pageBack(page, now)) {
            val t = Turn(MoveKind.None, page, under, popping = true)
            start(Turn(popKind(t), t.from, t.to, popping = true), now, retarget = false)
        }
    }

    fun hold(now: Long, target: Boolean, band: Gesture.Edge): Boolean {
        val t = turn
        val pop = phase == Phase.Exit && t != null && t.popping && overlay == null
        val starts = if (pop) {
            target
        } else {
            holdReversible(now) && (target || band != Gesture.Edge.None)
        }
        held = if (starts) Held(now, target, band, seq, pop && devicePop) else null
        return starts
    }

    fun heldBack(popKind: (Turn<P>) -> MoveKind) {
        val h = held
        held = null
        ask { now -> heldBack(h, now, popKind) }
    }

    private fun heldBack(h: Held?, now: Long, popKind: (Turn<P>) -> MoveKind) {
        val o = overlay
        when {
            h != null && h.seq == seq && holdReversible(now) && o != null -> closeOverlay(o, now)
            h != null && h.seq == seq && holdReversible(now) -> reverse(now)
            h != null && h.dev -> touch(now)
            else -> landBack(now, popKind)
        }
    }

    private fun holdReversible(now: Long): Boolean {
        val o = overlay
        if (o != null) return !o.closing && o.moving(now) && openSeq == seq
        return reversible()
    }

    private fun moveHeld(at: Long): Boolean {
        val h = held ?: return false
        if (!h.target || !reversible()) return false
        return h.at < move.swapAt && at < move.swapAt + Tok.input_keyHoldMs.toLong()
    }

    fun home(kind: MoveKind) = ask { now ->
        armedLand(now, replay = false)
        dropOverlay()
        if (phase != Phase.Rest) land(now)
        if (pages.isEmpty()) return@ask
        start(Turn(kind, top, null, popping = true, morph = false), now, retarget = false)
    }

    fun touch() {
        held = null
        ask { now -> touch(now) }
    }

    fun touchDown() {
        held = null
        ask { now ->
            val o = overlay
            if (o == null || !(o.closing || o.moving(now))) touch(now)
        }
    }

    private fun carryLand() {
        held = null
        ask { now ->
            val o = overlay
            if (o == null || !(o.closing || o.moving(now))) touch(now)
            replayDue = true
        }
    }

    internal fun heldReplay(downAt: Long, target: Boolean): Boolean {
        val g = gate ?: return false
        if (!g.replayHeld(downAt, target)) return false
        held = null
        ask { now ->
            touch(now)
            replayDue = true
        }
        return true
    }

    private fun touch(now: Long) {
        armedLand(now)
        val o = overlay
        if (o != null && (o.closing || o.moving(now))) landOverlay(now)
        if (phase != Phase.Rest) land(now)
    }

    private fun landOverlay(now: Long, show: Boolean = true) {
        val o = overlay ?: return
        if (o.land(now)) dropOverlay(show)
    }

    private fun reversible(): Boolean {
        val t = turn ?: return false
        return phase == Phase.Exit && !t.popping && !move.reversed
    }

    private fun retargetable(): Boolean {
        val t = turn ?: return false
        val page = top ?: return false
        return move.live && !move.reversed && phase == Phase.Enter && t.to != null && t.kind != MoveKind.T7 && retargets(page)
    }

    private fun formsFor(to: P?): MoveForms {
        if (to == null) return forms
        val content = when (kindOf(to)) {
            PageKind.Grid -> forms.content
            PageKind.Hub -> forms.hubRows
            PageKind.Plain -> forms.pageRows
        }
        return forms.copy(content = content)
    }

    private fun start(from: Turn<P>, now: Long, retarget: Boolean) {
        devicePop = false
        if (!from.popping && from.keep == null) from.to?.let(enter)
        seq++
        swapHeld = false
        landAt = -1L
        val row = if (from.popping && from.row < 0) from.from?.let { pushedRow(it) } ?: -1 else from.row
        val album = if (from.album >= 0) from.album else (if (from.popping) from.from else from.to)?.let(albumOf) ?: -1L
        val t = Turn(from.kind, from.from, from.to, from.popping, retarget, now, row, album, from.morph, from.keep)
        turn = t
        started(t)
        starting(t)
        moveEnds = ends(t).copy(retarget = retarget, rows = t.to?.let { kindOf(it) != PageKind.Grid } ?: false)
        move.start(t.kind, now, moveEnds, formsFor(t.to))
        waiting = t.kind != MoveKind.T7 && t.to?.let { !ready(it) } ?: false
        if (!waiting) arrive(now)
        legs(t)
        if (t.kind == MoveKind.T4 && t.to?.let(isNp) == true) move.npPop(Tok.motion_npContentDelay.toInt())
        phase = Phase.Exit
    }

    private fun arrive(now: Long) {
        move.release(arriveGroup + Part.Ground, now)
    }

    private fun retarget(now: Long, popKind: (Turn<P>) -> MoveKind) {
        val v = move
        fun cur(p: Part, q: Prop) = v.at(p, q, now)
        val ca = cur(Part.Content, Prop.A)
        val cs = if (moveEnds.rows) 1f else cur(Part.Content, Prop.S)
        val ha = cur(Part.Header, Prop.A)
        val zoomed = moveEnds.zoom && cur(Part.Zoom, Prop.A) > 0f && ha <= 0f
        val zs = cur(Part.Zoom, Prop.S)
        val pa = cur(Part.Pivots, Prop.A)
        val pvx = cur(Part.Pivots, Prop.X)
        val pvy = cur(Part.Pivots, Prop.Y)
        val ga = cur(Part.Ground, Prop.A)
        val word = v.has(Part.Word, Prop.S) && cur(Part.Word, Prop.A) > 0f
        val ws = cur(Part.Word, Prop.S)
        val wx = cur(Part.Word, Prop.X)
        val wy = cur(Part.Word, Prop.Y)
        val tile = v.has(Part.Tile, Prop.S) && cur(Part.Tile, Prop.A) > 0f
        val ts = cur(Part.Tile, Prop.S)
        val tx = cur(Part.Tile, Prop.X)
        val ty = cur(Part.Tile, Prop.Y)
        val wasMorph = moveEnds.morph && now < v.t0 + v.wordEnd
        val mwa = cur(Part.Word, Prop.A)
        val t = Turn(MoveKind.None, top, under, popping = true)
        start(Turn(popKind(t), t.from, t.to, popping = true), now, retarget = true)
        val n = move
        val e = moveEnds
        val leave = (if (n.kind == MoveKind.T4h || n.kind == MoveKind.T4n) Tok.motion_t4hContent else Tok.motion_leave).toInt()
        n.fadeFrom(Part.Leave, ca)
        n.set(Part.Leave, Prop.S, cs)
        n.fadeFrom(Part.LeaveHeader, ha)
        n.fadeFrom(Part.LeavePivots, pa)
        if (n.has(Part.LeavePivots, Prop.X)) {
            n.from(Part.LeavePivots, Prop.X, pvx)
            n.from(Part.LeavePivots, Prop.Y, pvy)
        } else {
            n.set(Part.LeavePivots, Prop.X, pvx)
            n.set(Part.LeavePivots, Prop.Y, pvy)
        }
        if (n.has(Part.LeaveGround, Prop.A)) n.from(Part.LeaveGround, Prop.A, ga)
        if (wasMorph && e.morph && e.morphBack) {
            n.from(Part.Word, Prop.S, ws)
            n.from(Part.Word, Prop.X, wx)
            n.from(Part.Word, Prop.Y, wy)
            n.from(Part.Word, Prop.A, mwa)
            if (e.morphKind != MorphKind.Row) n.shift(Part.Word, -Tok.motion_t4mDelay.toInt())
        } else if (zoomed && !e.morph) {
            n.set(Part.LeaveWord, Prop.S, zs)
            n.add(MoveLeg(Part.LeaveWord, Prop.A, 1f, 0f, 0, leave))
        } else if (zoomed) {
            n.from(Part.LeaveHeader, Prop.A, 1f)
        }
        if (word && !e.morph && n.has(Part.Word, Prop.S)) {
            n.from(Part.Word, Prop.S, ws)
            n.from(Part.Word, Prop.X, wx)
            n.from(Part.Word, Prop.Y, wy)
        }
        if (!tile) return
        if (n.has(Part.Tile, Prop.S)) {
            for ((q, value) in listOf(Prop.S to ts, Prop.X to tx, Prop.Y to ty)) {
                val l = n.leg(Part.Tile, q) ?: continue
                n.add(MoveLeg(l.part, l.prop, value, l.to, l.delay, l.dur, Curves.easeIn, l.pin))
            }
            n.set(Part.Tile, Prop.A, 1f)
            return
        }
        n.set(Part.Tile, Prop.S, ts)
        n.set(Part.Tile, Prop.X, tx)
        n.set(Part.Tile, Prop.Y, ty)
        n.add(MoveLeg(Part.Tile, Prop.A, 1f, 0f, 0, leave))
    }

    private fun reverse(now: Long) {
        seq++
        swapHeld = false
        waiting = false
        landAt = -1L
        move.reverse(now)
        turn?.to?.let { gone += it }
        turn = turn?.let { Turn(it.kind, it.to, it.from, true, it.retarget, it.at, morph = it.morph) }
        phase = Phase.Enter
    }

    private fun swap(at: Long) {
        val t = turn ?: return
        val by = if (swapHeld) (at - move.swapAt).toInt() else 0
        swapHeld = false
        if (by > 0) move.shift(Part.Content, by, at)
        if (t.popping) {
            if (t.to == null) {
                gone.addAll(pages)
                pages.clear()
            } else if (pages.isNotEmpty()) {
                gone += pages.removeAt(pages.lastIndex)
            }
        } else if (t.to != null && t.keep == null) {
            pages.add(t.to)
            keepRow(t.to, t.row)
        }
        phase = Phase.Enter
        (t.keep ?: top)?.let { swapped(it, at) }
    }

    private fun keepRow(page: P, row: Int) {
        val i = pages.lastIndex
        while (pushRows.size > i) pushRows.removeAt(pushRows.lastIndex)
        while (pushRows.size < i) pushRows.add(null)
        pushRows.add(if (row >= 0) page to row else null)
    }

    private fun pushedRow(page: P): Int {
        val i = pages.lastIndex
        val r = pushRows.getOrNull(i) ?: return -1
        return if (r.first === page && pages.getOrNull(i) === page) r.second else -1
    }

    private fun land(now: Long) {
        if (phase == Phase.Exit) swap(now)
        rest()
        clock = now
    }

    private fun rest() {
        val k = turn?.keep
        move.stop()
        phase = Phase.Rest
        turn = null
        waiting = false
        landAt = -1L
        if (k != null) {
            val i = pages.lastIndexOf(k)
            if (i >= 0) while (pages.lastIndex > i) gone += pages.removeAt(pages.lastIndex)
        }
        val g = gone.toList()
        gone.clear()
        while (pushRows.size > pages.size) pushRows.removeAt(pushRows.lastIndex)
        g.forEach(drop)
    }

    fun pushOpen(page: P, tapAt: Long, boundMs: Long): PlayOpen = playOpen(OpenForm.Push, page, tapAt, boundMs)

    fun cutThenPush(page: P, tapAt: Long, boundMs: Long): PlayOpen = playOpen(OpenForm.Cut, page, tapAt, boundMs)

    fun popToNp(np: P, tapAt: Long, boundMs: Long): PlayOpen = playOpen(OpenForm.Keep, np, tapAt, boundMs)

    private fun playOpen(form: OpenForm, page: P, tapAt: Long, boundMs: Long): PlayOpen {
        onMain("a play open")
        checkNotNull(gate) { "a play open needs the stack's InputGate" }
        val h = PlayOpen(tapAt, boundMs, ::openDone, ::openFail)
        pending++
        gateNow()
        ask { now -> arm(h, form, page, now) }
        return h
    }

    private fun arm(h: PlayOpen, form: OpenForm, page: P, now: Long) {
        pending--
        val back = gate?.backOwed == true
        gate?.backOwed = false
        if (h.latch == Latch.Fail) {
            h.state = PlayOpen.State.Refused
            gateNow()
            return
        }
        if (phase != Phase.Rest || (form != OpenForm.Cut && overlay != null)) {
            h.state = PlayOpen.State.Local
            gateNow()
            return
        }
        if (form == OpenForm.Cut) {
            overlay?.let { o ->
                if (!o.closing) closing(o, now)
                dropOverlay(show = false)
            }
        }
        start(Turn(MoveKind.T7, top, page, popping = false, keep = if (form == OpenForm.Keep) page else null), now, retarget = false)
        move.hold(npInGroup)
        opened = h
        if (h.latch == Latch.Done) {
            h.doneAt = now
            h.state = PlayOpen.State.Released
        } else {
            h.state = PlayOpen.State.Armed
        }
        if (cutOwed) cutNow()
        if (back) {
            gate?.drop()
            refuse(now)
            return
        }
        gateNow()
    }

    private fun openDone(h: PlayOpen) {
        onMain("PlayOpen.done")
        if (h.state == PlayOpen.State.Pending) {
            if (h.latch == null) h.latch = Latch.Done
        } else if (h === opened && h.state == PlayOpen.State.Armed) {
            h.doneAt = clock
            h.state = PlayOpen.State.Released
        }
    }

    private fun openFail(h: PlayOpen) {
        onMain("PlayOpen.fail")
        if (h.state == PlayOpen.State.Pending) {
            if (h.latch == null) h.latch = Latch.Fail
        } else if (h === opened && h.state == PlayOpen.State.Armed) {
            h.state = PlayOpen.State.Refused
            ask { now -> if (opened === h) refuse(now) }
        }
    }

    internal fun npCut() {
        cutOwed = true
        if (opened != null && move.live && move.kind == MoveKind.T7) cutNow()
    }

    private fun cutNow() {
        cutOwed = false
        move.land(Part.NpLeave)
        move.land(Part.NpPeek)
        for (p in npLeaveGroup) move.land(p)
    }

    private fun backKey() = ask { now -> if (opened != null) refuse(now) }

    private fun gateNow() {
        val g = gate ?: return
        val hold = pending > 0 || opened != null
        if (hold && !g.holding) g.hold()
        if (!hold && g.holding) {
            g.free()
            cutOwed = false
            replayDue = true
        }
    }

    private fun opens(now: Long) {
        val h = opened ?: return
        when (h.state) {
            PlayOpen.State.Armed -> if (now >= h.bound) refuse(now)
            PlayOpen.State.Released -> {
                val to = turn?.to
                if ((to != null && ready(to)) || now >= h.doneAt + h.boundMs) release(now)
            }
            else -> Unit
        }
    }

    private fun release(now: Long) {
        opened = null
        move.npRelease(now)
        if (gate?.queued == true) land(now)
        gateNow()
    }

    fun armedDrop() {
        onMain("PageStack.armedDrop")
        armedLand(clock, replay = false)
    }

    private fun armedLand(now: Long, replay: Boolean = true) {
        val h = opened ?: return
        opened = null
        h.state = PlayOpen.State.Released
        move.npRelease(now)
        land(now)
        if (!replay) gate?.drop()
        gateNow()
    }

    private fun refuse(now: Long) {
        val h = opened ?: return
        opened = null
        h.state = PlayOpen.State.Refused
        if (phase == Phase.Exit) swap(now)
        val t = turn
        turn = null
        rest()
        val np = t?.to
        val q = top
        if (t?.keep != null) {
            if (np != null && q != null) start(Turn(MoveKind.T8, np, q, popping = false, keep = q), now, retarget = false)
        } else if (q != null) {
            start(Turn(MoveKind.T8, q, under, popping = true), now, retarget = false)
        }
        move.clear(Part.NpOut)
        if (gate?.queued == true) land(now)
        gateNow()
    }

    fun frame(now: Long) {
        clock = now
        if (replayDue) {
            replayDue = false
            if (gate?.replay(now) == true) replayDue = true
        }
        while (asks.isNotEmpty()) asks.removeFirst()(now)
        opens(now)
        if (phase == Phase.Exit && now >= move.swapAt) {
            if (moveHeld(now)) swapHeld = true else swap(now)
        }
        if (phase == Phase.Enter && opened == null) {
            val np = turn?.kind.let { it == MoveKind.T7 || it == MoveKind.T8 }
            if (landAt in 0 until now || (np && now >= move.endAt)) {
                rest()
            } else if (landAt < 0 && now >= move.endAt) {
                landAt = now
                waiting = false
            }
        }
        if (waiting && turn?.to?.let(ready) == true) {
            waiting = false
            arrive(now)
        }
        val o = overlay
        o?.rise(now)
        if (o != null && o.done(now)) dropOverlay()
        jumpSettled = overlay?.let { it.kind == OverKind.Jump && !it.closing && !it.moving(now) } == true
    }
}

@Composable
fun <P : Any> PageStackClock(stack: PageStack<P>, popKind: (Turn<P>) -> MoveKind) {
    LaunchedEffect(stack, stack.asked) {
        withFrameMillis { stack.frame(it) }
        while (stack.moving || stack.due) withFrameMillis { stack.frame(it) }
    }
    PredictiveBackHandler(enabled = stack.canBack) { progress ->
        progress.collect {}
        stack.back(true, popKind)
    }
}
