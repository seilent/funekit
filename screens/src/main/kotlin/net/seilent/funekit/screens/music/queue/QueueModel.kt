package net.seilent.funekit.screens.music.queue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.GlowPair
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.Stale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.hub.cardLine
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.Paging
import net.seilent.funekit.screens.music.lists.ListLook
import net.seilent.funekit.screens.music.lists.ListPane
import net.seilent.funekit.screens.music.overlay.MOVE_DOWN
import net.seilent.funekit.screens.music.overlay.MOVE_UP
import net.seilent.funekit.screens.music.overlay.PLAY_NEXT
import net.seilent.funekit.screens.music.overlay.REMOVE
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import kotlin.math.max

const val QUEUE_EMPTY = "nothing here"
private const val OF = " of "
private const val DIP_LEGS = 2

data class CountLine(val text: String, val alpha: Float)

class QueueModel(entry: Long) : PageModel(entry, MusicPage.Queue) {
    val paging = Paging(Listing.Queue)
    val pane = ListPane().also { it.paging = paging }
    val track: RowTrack get() = pane.track
    var ppu: Float
        get() = pane.ppu
        set(v) {
            pane.ppu = v
        }
    var counted: Int
        get() = pane.counted
        set(v) {
            pane.counted = v
        }
    val focus: FocusTrack get() = pane.focus
    val press: PressSlots get() = pane.press
    val glow: GlowPair get() = pane.glow
    val fade: ThumbFade get() = pane.fade
    var index by mutableIntStateOf(-1)
        private set
    var size by mutableIntStateOf(0)
        private set
    val rowsShown: Int get() = if (cardLine(card).isNotEmpty()) 0 else size
    var loaded by mutableStateOf(false)
    private var stated = false
    private var untracked = false
    private var shown by mutableStateOf<String?>(null)
    private var next: String? = null
    private var countAt by mutableLongStateOf(-1L)
    var opened = false
        private set
    val pitch: Int get() = pane.pitch
    val view: Int get() = pane.view
    val aim: Int get() = pane.aim
    val aims: Int get() = pane.aims
    override var lists: List<ListTouch>
        get() = pane.lists
        set(v) {
            pane.lists = v
        }
    val wake: Channel<Unit> get() = pane.wake
    private var seen: Triple<Long, Int, Int>? = null

    fun poke() = pane.poke()

    fun row(i: Int): Row? = pane.row(i)

    val revision: Long get() = paging.window?.revision ?: -1L

    fun geometry(spec: RowSpec) = pane.geometry(spec)

    fun rowModel(i: Int): RowModel? {
        val r = row(i) ?: return null
        return RowModel(primary = r.title, secondary = r.sub, number = (i + 1).toString(), accent = i == index)
    }

    fun count(): String? {
        if (loaded && size == 0) return null
        if (size == 0) return "0" + OF + "0"
        return (index + 1).toString() + OF + size
    }

    fun countLine(now: Long): CountLine? {
        if (cardLine(card).isNotEmpty()) return null
        val c = count() ?: return null
        val at = countAt
        if (at < 0) return CountLine(shown ?: c, 1f)
        val d = Tok.motion_valueChange.toLong()
        val e = now - at
        val text = if (e >= d) next ?: shown ?: c else shown ?: c
        val a = when {
            e < d -> lerp(1f, Tok.motion_valueDip, e.toFloat() / d)
            e < DIP_LEGS * d -> lerp(Tok.motion_valueDip, 1f, (e - d).toFloat() / d)
            else -> 1f
        }
        return CountLine(text, a)
    }

    private fun counted(at: Long) {
        val c = count() ?: return
        if (c == shown || c == next) return
        next = c
        countAt = at
    }

    fun empty(): Boolean = loaded && size == 0

    fun line(): String? = cardLine(card).ifEmpty { null } ?: QUEUE_EMPTY.takeIf { empty() }

    fun look(i: Int, now: Long, focusShown: Boolean): ListLook = pane.look(i, now, focusShown, Tok.type_listPrimary_alpha, Tok.type_listPrimary_alphaDim)

    fun animating(now: Long): Boolean = countAt >= 0 || pane.animating(now)

    fun wakeAt(now: Long): Long? = pane.wakeAt(now)

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        if (countAt >= 0 && now - countAt >= DIP_LEGS * Tok.motion_valueChange.toLong()) {
            shown = next ?: shown
            next = null
            countAt = -1L
        }
        pane.tick(now, thumb, moving)
    }

    override val ready: Boolean
        get() = stated && (size == 0 || (opened || untracked) && row(track.first) != null)

    fun state(st: PlayerState) {
        index = st.index
        size = st.queueSize
        stated = true
        untracked = st.track == null && st.service == null
    }

    suspend fun show(v: LibraryView, first: Int, last: Int) {
        if (size == 0) return
        paging.show(v, max(0, first), last)
    }

    fun open(st: PlayerState, now: Long): Boolean {
        if (opened || (st.track == null && st.service == null) || paging.window == null) return false
        opened = true
        state(st)
        shown = count()
        next = null
        countAt = -1L
        seen = keyOf(st)
        place(st.index, now, false)
        return true
    }

    private fun keyOf(st: PlayerState) = Triple(st.queueRevision, st.index, st.queueSize)

    fun changed(st: PlayerState): Boolean = keyOf(st) != seen

    private fun keyAt(i: Int): Pair<Long, Int>? {
        val w = paging.window ?: return null
        val r = w.row(i) ?: return null
        return r.id to (w.offset until i).count { w.row(it)?.id == r.id }
    }

    private fun find(key: Pair<Long, Int>): Int {
        val w = paging.window ?: return -1
        var n = 0
        for (i in w.offset until w.end) {
            if (w.row(i)?.id != key.first) continue
            if (n == key.second) return i
            n++
        }
        return -1
    }

    suspend fun sync(v: LibraryView, st: PlayerState, now: Long) {
        val key = keyAt(focus.row)
        val off = focus.row * pitch - track.q
        state(st)
        counted(now)
        paging.reload(v)
        if (paging.window == null && size > 0) paging.show(v, 0, 0)
        pane.reset()
        if (size == 0) {
            focus.set(0, 0, now, false)
            pane.go(size)
            seen = keyOf(st)
            return
        }
        val at = key?.let { find(it) }?.takeIf { it >= 0 } ?: focus.row.coerceIn(0, size - 1)
        focus.set(at, size, now, false)
        pane.go(size, px = at * pitch - off)
        poke()
        seen = keyOf(st)
    }

    fun place(i: Int, now: Long, animate: Boolean) = pane.place(i, size, now, animate)

    fun aimTo(q: Int) = pane.aimTo(q)

    fun afterScroll(off: Int, now: Long) = pane.afterScroll(off, size, now)

    fun target(i: Int): SheetTarget? {
        val r = row(i) ?: return null
        val library = r.kind != Kind.Service
        return SheetTarget(
            SheetKind.Queue, r.title, Listing.Queue, paging.window?.generation ?: 0L, track = r.id.takeIf { library },
            slot = i, cur = index, size = size, revision = revision, library = library, from = SheetFrom.Queue,
        )
    }

    suspend fun tap(i: Int, src: MusicSource): Boolean {
        val rev = revision
        if (rev < 0 || row(i) == null) return false
        return try {
            src.player.playAt(i, rev)
            true
        } catch (_: Stale) {
            false
        }
    }

    suspend fun verb(word: String, i: Int, rev: Long, src: MusicSource, now: Long) {
        if (rev < 0) return
        val p = src.player
        val to = try {
            when (word) {
                REMOVE -> {
                    p.remove(i, rev)
                    i
                }
                PLAY_NEXT -> {
                    p.playNextAt(i, rev)
                    -1
                }
                MOVE_UP -> {
                    p.move(i, i - 1, rev)
                    i - 1
                }
                MOVE_DOWN -> {
                    p.move(i, i + 1, rev)
                    i + 1
                }
                else -> return
            }
        } catch (_: Stale) {
            return
        }
        val st = p.state.value
        src.viewed { v -> sync(v, st, now) }
        place(if (to < 0) st.index + 1 else to, now, true)
    }

    override fun drop() {
        lists = emptyList()
    }
}
