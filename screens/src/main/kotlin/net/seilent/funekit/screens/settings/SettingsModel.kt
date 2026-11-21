package net.seilent.funekit.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.LitRow
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.motion.Curve
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.PivotMotion
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.PageId
import net.seilent.funekit.screens.music.PivotPage
import net.seilent.funekit.screens.music.pivotSwitchEnd
import kotlin.math.max
import kotlin.math.min

const val SETTINGS = "settings"

private const val SPOT_FIELDS = 3

data class SettingsId(val header: String) : PageId

enum class HeaderLook { Settings, Page, Default }

enum class SettingKind { Choice, Steps, Range, Toggle, Action, Draft, Info }

enum class SettingFlag { ReadOnly, Restart, BoardOnly, Confirm }

data class SettingRow(
    val key: String,
    val title: String,
    val value: String,
    val kind: SettingKind = SettingKind.Info,
    val sub: String? = null,
    val values: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    val shows: List<String> = emptyList(),
    val at: Int = -1,
    val focus: Int = 0,
    val wrap: Boolean = true,
    val min: Int = 0,
    val max: Int = 0,
    val step: Int = 0,
    val level: Int = 0,
    val flags: Set<SettingFlag> = emptySet(),
    val live: String? = null,
) {
    val editable: Boolean
        get() = SettingFlag.ReadOnly !in flags && when (kind) {
            SettingKind.Choice, SettingKind.Steps, SettingKind.Toggle, SettingKind.Range -> true
            SettingKind.Draft -> values.isNotEmpty()
            SettingKind.Action, SettingKind.Info -> false
        }
}

data class SettingPivot(val name: String, val rows: List<SettingRow>)

open class SettingSheet(
    val form: MenuForm,
    val title: String,
    val items: List<String>,
    val values: List<String> = emptyList(),
    val mixed: List<Boolean> = emptyList(),
    val lead: Int = 0,
    val readOnly: Boolean = false,
    val mixedRow: Int = -1,
    val focus: Int = -1,
    val mark: Int = -1,
    val wrap: Boolean = false,
    val lift: Boolean = false,
    val confirm: Boolean = false,
    val edit: String? = null,
)

fun valueSheet(row: SettingRow): SettingSheet {
    val steps = row.kind == SettingKind.Steps
    val n = row.words.size
    fun at(i: Int): Int = if (steps && i >= 0) n - 1 - i else i
    return SettingSheet(MenuForm.Sheet, row.title.lowercase(), if (steps) row.words.reversed() else row.words, focus = at(row.focus), mark = at(row.at), wrap = row.wrap, lift = true, edit = row.key)
}

fun settingRowAt(tops: IntArray, content: Int, y: Int): Int {
    var idx = -1
    for (i in tops.indices) if (tops[i] <= y) idx = i
    return if (y < content) idx else -1
}

fun clampFocusToView(focus: Int, tops: IntArray, heights: IntArray, off: Int, view: Int): Int {
    val n = tops.size
    if (n == 0 || focus !in 0 until n) return focus
    val top = off
    val bottom = off + view
    val content = tops[n - 1] + heights[n - 1]
    val rt = tops[focus]
    if (rt >= top && rt + heights[focus] <= bottom) return focus
    fun nearest(from: Int): Int = if (from in 0 until n) from else -1
    val t = if (rt < top) {
        var i = max(0, settingRowAt(tops, content, top))
        if (tops[i] < top) i++
        nearest(i)
    } else {
        var i = settingRowAt(tops, content, bottom)
        if (i < 0) i = n - 1
        if (tops[i] + heights[i] > bottom) i--
        nearest(i)
    }
    return if (t >= 0) t else focus
}

fun ensureVisible(top: Int, h: Int, off: Int, view: Int, limit: Int): Int {
    val want = when {
        top < off -> top
        top + h > off + view -> top + h - view
        else -> off
    }
    return want.coerceIn(0, max(0, limit))
}

data class SettingLook(val title: Float, val value: Float, val sub: Float, val glow: Float?, val edit: Boolean = false)

fun settingsLift(tops: IntArray, heights: IntArray, focus: Int, off: Int, contentTop: Int, panelTop: Int, toTop: Boolean): Int {
    if (focus !in tops.indices) return 0
    val top = tops[focus]
    var by = contentTop + top + heights[focus] - off - panelTop
    if (toTop) by = min(by, top - off)
    if (by <= 0 || top - off < by) return 0
    val to = tops.firstOrNull { it >= off + by } ?: return 0
    return to - off
}

class SettingsPane {
    val focus = FocusTrack().also { it.attach() }
    var px by mutableIntStateOf(0)
    var key: String? = null
    var restoreU: Float? = null
    var reach by mutableIntStateOf(0)
    var dropping = false
}

enum class AimKind { None, Into, Lift, Drop }

class SettingsModel(entry: Long, page: SettingsId) : PivotPage(entry, page) {
    val id: SettingsId = page
    var groups: List<SettingPivot> by mutableStateOf(emptyList())
        private set
    var pivots: List<SettingPivot>
        get() = groups
        set(v) {
            if (v == groups) return
            val was = groups.getOrNull(pivot)?.name
            val from = groups.getOrNull(switchFrom)?.name
            if (v.isEmpty()) {
                if (groups.isNotEmpty()) restored = state()
                held = held ?: was
            } else {
                restored = null
            }
            groups = v
            val at = groups.indexOfFirst { it.name == was }
            if (at >= 0) pivot = at else pivot = pivot.coerceIn(0, max(0, groups.size - 1))
            val back = groups.indexOfFirst { it.name == from }
            switchFrom = if (back >= 0) back else switchFrom.coerceIn(0, max(0, groups.size - 1))
            resync()
        }
    private var restored: List<Any>? = null
    private var held: String? = null
    private val panes = HashMap<String, SettingsPane>()
    val lit = LitRow()
    val glow = Glow()
    val fade = ThumbFade()
    var view = 0
    var ppu = 0f
        private set
    var heightOf: (SettingRow) -> Int = { 0 }
    private var focusEnd = 0L
    private var litUntil = 0L
    var switchAt: Long? by mutableStateOf(null)
        private set
    var switchFrom by mutableIntStateOf(0)
        private set
    override var switchDir by mutableIntStateOf(1)
        private set
    val motion = PivotMotion()
    var drags by mutableIntStateOf(0)
        private set
    var switchFade = 1f
        private set
    var aimAt by mutableIntStateOf(0)
        private set
    var aim = 0
        private set
    var aimCut = 0
        private set
    var aimMs = Tok.motion_scroll
        private set
    var aimCurve: Curve = Curves.easeIn
        private set
    var aimKind by mutableStateOf(AimKind.None)
        private set
    override var lists: List<ListTouch> = emptyList()
    var editing: String? by mutableStateOf(null)
        private set
    private var lifted: SettingsPane? = null
    var liftable = false
        private set
    val wake = Channel<Unit>(Channel.CONFLATED)

    override val kind: PageKind get() = PageKind.Hub

    val names: List<String> get() = groups.map { it.name }

    fun rows(p: Int): List<SettingRow> = groups.getOrNull(p)?.rows ?: emptyList()

    fun pane(p: Int): SettingsPane = panes.getOrPut(groups.getOrNull(p)?.name ?: "") { SettingsPane() }

    val pane: SettingsPane get() = pane(pivot)

    fun heights(p: Int): IntArray = rows(p).map(heightOf).toIntArray()

    fun tops(p: Int): IntArray {
        val h = heights(p)
        val t = IntArray(h.size)
        var y = 0
        for (i in h.indices) {
            t[i] = y
            y += h[i]
        }
        return t
    }

    fun content(p: Int): Int = heights(p).sum()

    fun base(p: Int): Int = max(0, content(p) - view)

    fun limit(p: Int): Int = max(base(p), pane(p).reach)

    fun pad(p: Int): Int = if (pane(p).reach > base(p)) pane(p).reach + view - content(p) else 0

    override fun poke() {
        wake.trySend(Unit)
    }

    fun scale(to: Float) {
        if (to == ppu) return
        for (pn in panes.values) {
            val u = pn.restoreU
            if (u != null) {
                pn.px = px(u, to)
                pn.restoreU = null
            } else if (ppu > 0f) {
                pn.px = px(pn.px / ppu, to)
            }
        }
        ppu = to
    }

    private fun setFocus(pn: SettingsPane, rows: List<SettingRow>, i: Int, now: Long, animate: Boolean) {
        if (i !in rows.indices) return
        if (i != pn.focus.row) focusEnd = now + if (animate) Tok.motion_focusAlpha.toLong() else 0L
        pn.focus.set(i, rows.size, now, animate)
        pn.key = rows[i].key
    }

    private fun resync() {
        held?.let { n ->
            val at = groups.indexOfFirst { it.name == n }
            if (at >= 0) {
                pivot = at
                held = null
            }
        }
        for (p in groups.indices) {
            val pn = pane(p)
            val rows = rows(p)
            val k = pn.key ?: continue
            val i = rows.indexOfFirst { it.key == k }
            val to = if (i >= 0) i else pn.focus.row.coerceIn(0, max(0, rows.size - 1))
            if (to != pn.focus.row) pn.focus.set(to, rows.size, 0L, false)
        }
    }

    fun focusRow(p: Int): Int {
        val pn = pane(p)
        val rows = rows(p)
        val k = pn.key ?: return pn.focus.row.coerceIn(0, max(0, rows.size - 1))
        val i = rows.indexOfFirst { it.key == k }
        return if (i >= 0) i else pn.focus.row.coerceIn(0, max(0, rows.size - 1))
    }

    fun down(r: Int, now: Long) {
        val rows = rows(pivot)
        if (r !in rows.indices) return
        lit.up()
        glow.clear()
        setFocus(pane, rows, r, now, true)
        lit.down(r, focusEnd)
        glow.down(r, now)
    }

    fun cancel(now: Long) {
        lit.up()
        litUntil = max(litUntil, focusEnd)
        glow.fade(now)
    }

    fun cut() {
        lit.up()
        glow.clear()
    }

    fun tap(r: Int, now: Long): SettingRow? {
        val rows = rows(pivot)
        if (r !in rows.indices) return null
        lit.up()
        glow.idleUp(now)
        litUntil = max(litUntil, focusEnd)
        val t = tops(pivot)
        aimTo(ensureVisible(t[r], heights(pivot)[r], target(), view, limit(pivot)), AimKind.Into)
        return rows[r]
    }

    fun afterScroll(off: Int, now: Long) {
        val pn = pane
        pn.px = off
        val rows = rows(pivot)
        val f = focusRow(pivot)
        val to = clampFocusToView(f, tops(pivot), heights(pivot), off, view)
        if (to != f) setFocus(pn, rows, to, now, false)
    }

    fun target(): Int = if (aimKind != AimKind.None) aim else pane.px

    fun aimTo(q: Int, kind: AimKind, ms: Float = Tok.motion_scroll, curve: Curve = Curves.easeIn) {
        if (q == target()) return
        aim = q
        aimMs = ms
        aimCurve = curve
        aimKind = kind
        aimAt++
    }

    fun aimEnded(at: Int) {
        if (at != aimAt) return
        aimKind = AimKind.None
        settle()
    }

    fun stopAim() {
        aimKind = AimKind.None
        aimCut++
    }

    fun switching(now: Long): Boolean = switchAt?.let { now - it < pivotSwitchEnd() } == true

    fun side(now: Long): Int {
        if (switchAt == null || !switching(now)) return pivot
        return if (motion.entering(now)) pivot else switchFrom
    }

    override fun canSwitch(d: Int): Boolean = pivot + d in groups.indices

    override fun motionActive(now: Long): Boolean = motion.active(now)

    override fun haltList() {
        lists.firstOrNull()?.halt()
    }

    override fun switchTo(p: Int, now: Long): Boolean {
        val to = p.coerceIn(0, max(0, groups.size - 1))
        if (to == pivot) return false
        val from = if (motion.dragging) motion.offset else 0f
        lit.drop()
        glow.clear()
        held = null
        switchFrom = pivot
        switchDir = if (to > pivot) 1 else -1
        switchFade = fade.at(now)
        show(to, now)
        fade.show(now)
        switchAt = now
        motion.begin(now, switchDir, from)
        return true
    }

    override fun drag(dxU: Float, now: Long) {
        if (!motion.dragging && motion.active(now)) land()
        motion.drag(dxU)
        drags++
    }

    override fun release(now: Long) {
        if (!motion.dragging) return
        motion.release(now)
        drags++
    }

    override fun dragX(now: Long, ppu: Float): Int = if (switching(now)) 0 else motion.dx(now, ppu)

    private fun show(p: Int, now: Long) {
        pivot = p
        val pn = pane(p)
        val rows = rows(p)
        if (rows.isEmpty()) return
        val f = focusRow(p)
        setFocus(pn, rows, f, now, false)
        val top = tops(p)[f]
        val h = heights(p)[f]
        pn.px = min(max(pn.px, top + h - view), top).coerceIn(0, limit(p))
    }

    override fun land() {
        switchAt = null
        motion.end()
    }

    fun edit(key: String?, lift: Boolean) {
        editing = key
        liftable = lift
    }

    fun lift(contentTop: Int, panelTop: Int, toTop: Boolean): Boolean {
        if (!liftable) return false
        val f = focusRow(pivot)
        val by = settingsLift(tops(pivot), heights(pivot), f, target(), contentTop, panelTop, toTop)
        if (by <= 0) return false
        val pn = pane
        lifted?.takeIf { it !== pn }?.let { it.reach = 0 }
        lifted = pn
        pn.dropping = false
        pn.reach = target() + by
        aimTo(pn.reach, AimKind.Lift)
        return true
    }

    fun drop(ms: Float) {
        val pn = lifted ?: return
        if (pn !== pane || pn.reach == 0) {
            pn.reach = 0
            lifted = null
            return
        }
        val base = base(pivot)
        if (max(pn.px, target()) <= base) {
            pn.reach = 0
            lifted = null
            return
        }
        pn.dropping = true
        aimTo(min(target(), base), AimKind.Drop, ms, Curves.decel)
        if (aimKind == AimKind.None) settle()
    }

    private fun settle() {
        val pn = lifted ?: return
        if (!pn.dropping) return
        pn.dropping = false
        pn.reach = 0
        lifted = null
    }

    fun endEdit() {
        editing = null
        liftable = false
    }

    override fun toTop(now: Long): Boolean {
        val rows = rows(pivot)
        if (rows.isEmpty() || (focusRow(pivot) == 0 && pane.px == 0)) return false
        setFocus(pane, rows, 0, now, false)
        aimTo(0, AimKind.Into)
        return true
    }

    fun look(p: Int, r: Int, now: Long): SettingLook {
        val pn = pane(p)
        pn.focus.revision
        lit.revision
        val mix = pn.focus.rowAlpha(r, now, 1f, 0f)
        val live = p == pivot
        val title = if (live && lit.on(r, now)) Tok.alpha_full else lerp(Tok.type_settingsItem_alphaDim, Tok.type_settingsItem_alpha, mix)
        val edit = editing != null && rows(p).getOrNull(r)?.key == editing
        return SettingLook(
            title,
            if (edit) Tok.alpha_full else lerp(Tok.settingsValueDim, Tok.settingsValueFocused, mix),
            lerp(Tok.type_settingsSub_alphaDim, Tok.type_settingsSub_alpha, mix),
            if (live && glow.key == r) glow.level(now) else null,
            edit,
        )
    }

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        fade.track(thumb, moving, now)
        val k = glow.key
        if (k != null && !lit.on(k, now) && !glow.live(now)) glow.clear()
    }

    fun animating(now: Long): Boolean = switching(now) || motion.active(now) || pane.focus.animating(now) || glow.moving(now) || fade.fading(now)

    fun wakeAt(now: Long): Long? = listOfNotNull(litUntil.takeIf { it > now }, glow.due(now), fade.wakeAt(now).takeIf { it >= 0 }).minOrNull()

    override fun state(): List<Any> = restored ?: ArrayList<Any>().also { out ->
        out.add(held ?: groups.getOrNull(pivot)?.name ?: "")
        for (g in groups) {
            val pn = panes[g.name] ?: continue
            val u = pn.restoreU ?: if (ppu > 0f) pn.px / ppu else 0f
            out.add(arrayListOf<Any>(g.name, pn.key ?: "", u))
        }
    }

    override fun restoreState(v: List<*>) {
        if (groups.isEmpty()) restored = v.filterNotNull()
        (v.firstOrNull() as? String)?.takeIf { it.isNotEmpty() }?.let { held = it }
        for (e in v) {
            val l = e as? List<*> ?: continue
            if (l.size < SPOT_FIELDS) continue
            val (name, key, u) = l
            if (name !is String || key !is String || u !is Float) continue
            val pn = panes.getOrPut(name) { SettingsPane() }
            pn.key = key.takeIf { it.isNotEmpty() }
            pn.restoreU = u
            val p = groups.indexOfFirst { it.name == name }
            if (p >= 0) {
                val rows = rows(p)
                val i = rows.indexOfFirst { it.key == pn.key }
                if (i >= 0) pn.focus.set(i, rows.size, 0L, false)
            }
        }
        resync()
        if (pivot !in groups.indices) pivot = max(0, groups.size - 1)
    }

    override fun drop() {
        lists = emptyList()
        glow.clear()
    }
}
