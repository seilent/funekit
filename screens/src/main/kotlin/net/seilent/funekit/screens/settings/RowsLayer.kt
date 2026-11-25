package net.seilent.funekit.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.PageChrome
import net.seilent.funekit.kit.ThumbLayer
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.thumbProps
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.FuneList
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.rememberCoast
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.screens.music.pivotChromeAt
import net.seilent.funekit.screens.music.pivotWatch

private const val HIDDEN = -1

interface RowsDraw<R : Any, L> {
    val m: RowsPage<R>
    val s: Scale
    val top: Int
    val bottom: Int
    val view: Int
    val accent: Color

    fun look(p: Int, i: Int, now: Long): L

    fun DrawScope.row(r: R, look: L, rec: DrawRecord?, name: String)

    fun DrawScope.content(p: Int, off: Int, rec: DrawRecord? = null, name: String = "", look: (Int) -> L)

    fun thumbAt(p: Int, off: Int): ThumbRect?

    fun keep()
}

private fun stateAt(px: Int, tops: IntArray): LazyListState {
    var i = 0
    for (k in tops.indices) if (tops[k] <= px) i = k
    return LazyListState(i, if (tops.isEmpty()) 0 else px - tops[i])
}

private fun switchThumb(m: RowsPage<*>, now: Long): Float {
    if (m.switchAt == null || !m.switching(now)) return m.fade.at(now)
    return (if (m.motion.thumbIn(now)) m.fade.at(now) else m.switchFade) * m.motion.thumbAlpha(now)
}

@Composable
fun <R : Any, L> RowsLayer(
    m: RowsPage<R>,
    draw: RowsDraw<R, L>,
    chrome: Chrome,
    a: Arbiter,
    bands: List<Band>,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    frozen: Boolean,
    snap: Int,
    onBack: () -> Unit,
    onTap: (R) -> Unit,
    onHold: ((R) -> Boolean)?,
    leavesLit: () -> Boolean = { false },
) {
    val s = draw.s
    val scope = rememberCoroutineScope()
    var frame by remember { mutableLongStateOf(0L) }
    val p = m.pivot
    val rows = m.rows(p)
    val tops = m.tops(p)
    val state = remember(m, m.names.getOrNull(p), draw, tops.toList()) { stateAt(m.pane(p).px, tops) }
    val coast = rememberCoast(Kind.List, s)
    val t = remember(state, s.touchPpu, s.stdPpu, s.ppu, coast) { ListTouch(Kind.List, state, s, scope, coast = coast) }
    DisposableEffect(t) { onDispose { t.stop() } }
    val switching by remember(m) { derivedStateOf { m.switching(frame) } }
    val side by remember(m) { derivedStateOf { m.side(frame) } }
    val chromeAt by remember(chrome) { derivedStateOf { pivotChromeAt(chrome, frame) } }
    SideEffect {
        m.lists = listOf(t)
        draw.keep()
        t.onScroll = { at ->
            m.afterScroll(scrollOf(state, m.tops(p)), at)
            m.poke()
        }
        if (!switching && chrome.active != m.pivot) chrome.select(m.pivot)
    }
    DisposableEffect(m) { onDispose { m.lists = emptyList() } }

    LaunchedEffect(m) {
        while (true) {
            withFrameMillis { at ->
                frame = at
                val tt = m.lists.firstOrNull()
                m.tick(at, draw.thumbAt(m.pivot, m.pane.px) != null, tt?.moving == true || tt?.scroll?.dragging == true)
            }
            val tt = m.lists.firstOrNull()
            if (m.animating(frame) || tt?.moving == true || tt?.scroll?.dragging == true) continue
            val w = m.wakeAt(frame)
            if (w == null) m.wake.receive() else withTimeoutOrNull(w - frame) { m.wake.receive() }
        }
    }
    LaunchedEffect(m, t) {
        val at = tops
        snapshotFlow { scrollOf(state, at) to t.moving }.collect { (px, _) ->
            m.pane(p).px = px
            m.poke()
        }
    }
    LaunchedEffect(m, m.aimAt) {
        if (m.aimAt == 0) return@LaunchedEffect
        val aimed = m.aimAt
        val cut = m.aimCut
        val cuts = t.cuts
        val from = scrollOf(state, m.tops(p)).toFloat()
        val to = m.aim.toFloat()
        val d = m.aimMs
        val curve = m.aimCurve
        var t0 = -1L
        var e = 0f
        try {
            while (e < d) {
                if (t.scroll.dragging || t.moving || m.aimCut != cut || t.cuts != cuts) break
                withFrameMillis { at ->
                    if (t0 < 0) t0 = at
                    e = (at - t0).toFloat()
                    if (m.aimCut == cut && t.cuts == cuts) {
                        val want = Math.round(lerp(from, to, curve.at((e / d).coerceIn(0f, 1f))))
                        state.dispatchRawDelta((want - scrollOf(state, m.tops(p))).toFloat())
                    }
                }
            }
        } finally {
            m.aimEnded(aimed)
        }
    }

    fun rowTap(i: Int): Tap = Tap(
        onPress = {
            if (live) {
                m.stopAim()
                m.down(i, now())
                m.poke()
            }
        },
        onCancel = {
            m.cancel(now())
            m.poke()
        },
        onTap = {
            if (live) m.tap(i, now())?.let(onTap)
            m.poke()
        },
        onCut = {
            m.cut()
            m.poke()
        },
        onLong = onHold?.let { h -> { live && rows.getOrNull(i)?.let(h) == true } },
    )

    val layer = if (live) 0 else HIDDEN
    val watch = Modifier.pivotWatch(m, chrome, live, now)
    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx).then(watch)) { covered ->
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { pageGround(bands, draw.accent) }
        Column(
            Modifier.graphicsLayer {
                alpha = if (switching) 0f else 1f
                translationX = m.dragX(frame, s.ppu).toFloat()
            },
        ) {
            Box(Modifier.pxSize(s.widthPx, draw.top))
            key(m.names.getOrNull(p)) {
                Box(Modifier.pxSize(s.widthPx, draw.view).graphicsLayer { clip = true }) {
                    FuneList(a, t, Modifier.pxSize(s.widthPx, m.listHeight(p)), layer) {
                        items(rows.size) { i ->
                            val row = rows[i]
                            val look = rememberLook(p, m.keyOf(row), m, covered = covered) { draw.look(p, i, frame) }
                            Canvas(Modifier.pxSize(s.widthPx, m.heightOf(row)).tapTarget(a, rowTap(i), layer)) { with(draw) { row(row, look.value, null, "") } }
                        }
                        val pad = m.pad(p)
                        if (pad > 0) item { Box(Modifier.pxSize(s.widthPx, pad)) }
                    }
                }
            }
        }
        val at = m.switchAt
        if (switching && at != null) {
            val shownAt = if (side == m.pivot) at + Tok.motion_pivotOut.toLong() else at
            Box(
                Modifier.pxSize(s.widthPx, s.heightPx).graphicsLayer {
                    translationX = m.motion.dx(frame, s.ppu).toFloat()
                    alpha = m.motion.alpha(frame)
                },
            ) {
                key(side) {
                    val k = side
                    Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) { with(draw) { content(k, m.pane(k).px, null, "") { r -> draw.look(k, r, shownAt) } } }
                }
            }
        }
        val bar = draw.thumbAt(side, 0)
        if (bar != null) {
            key(side, m.content(side)) { ThumbLayer(bar, covered && m.aimKind != AimKind.Lift, snap, follow = m.aimKind == AimKind.Drop || m.aimKind == AimKind.Into) { thumbProps(draw.thumbAt(side, m.pane(side).px), switchThumb(m, frame)) } }
        }
        PageChrome(chrome, a, { chromeAt }, onPivot = { if (live) settingsSwitch(m, chrome, it, now()) }, onHeader = if (live) onBack else null, leavesLit = leavesLit)
    }
}
