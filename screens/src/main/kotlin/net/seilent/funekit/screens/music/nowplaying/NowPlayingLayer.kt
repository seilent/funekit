package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.fxU
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberLook
import net.seilent.funekit.kit.stack.PageLayer
import net.seilent.funekit.kit.statusRow
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.tapTarget

private const val HIDDEN = -1

private fun seekEdges(h: NpHalf): Edges {
    val r = h.g.seekBand
    val top = h.y - h.dy
    return Edges(h.x + r.x0, maxOf(h.y, top + r.y0), h.x + r.x1, minOf(h.y + h.clip, top + r.y1))
}

class NpTouch(private val m: NowPlayingModel, private val halves: List<NpHalf>, private val act: (NpAct) -> Unit, private val seek: (Long) -> Unit) {
    private var fresh = false
    private var cur: NpHalf? = null

    fun press() {
        fresh = true
    }

    fun at(x: Float, y: Float, now: Long) {
        if (fresh) {
            fresh = false
            val h = halves.at(x, y)
            cur = h
            if (h != null) m.down(h.g, h.ux(x), h.uy(y), now) else m.wake(now)
            return
        }
        val h = cur ?: return
        if (m.seeking) m.seekMove(h.g, h.ux(x))
    }

    fun cancel(now: Long) {
        fresh = false
        cur = null
        m.cancel(now)
    }

    fun hold(): Boolean {
        val h = cur ?: return false
        if (!m.hold(h.g)) return false
        cur = null
        fresh = false
        act(NpAct.Info)
        return true
    }

    fun tap(now: Long) {
        fresh = false
        val h = cur ?: return
        cur = null
        if (m.seeking) {
            m.seekUp(h.g, m.seekX)?.let(seek)
            return
        }
        val a = m.tap(h.g, now)
        if (a != NpAct.None) act(a)
    }
}

@Composable
fun NowPlayingLayer(
    m: NowPlayingModel,
    g: NpLayout,
    covers: Covers?,
    art: ImageBitmap?,
    status: StatusInk?,
    a: Arbiter,
    accent: Color,
    accentOff: Boolean,
    now: () -> Long,
    live: Boolean,
    visible: Boolean,
    frozen: Boolean,
    hideFocus: Boolean,
    snap: Int,
    onAct: (NpAct) -> Unit,
    onSeek: (Long) -> Unit,
    split: NpSplit? = null,
    backdrop: NpBackdrop? = null,
) {
    val s = g.s
    val halves = remember(g, split) { split?.halves ?: listOf(npFlat(g)) }
    val measurer = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val measures = remember(halves, measurer, fonts, density, covers) { halves.map { NpMeasure(it.g, measurer, fonts, density, covers) } }
    var frame by remember { mutableLongStateOf(now()) }
    LaunchedEffect(m) {
        while (true) {
            withFrameMillis { at ->
                m.stepTo(at)
                frame = at
            }
            if (m.animating(frame)) continue
            val seen = m.revision to m.lyrics.revision
            val wake = m.wakeAt(frame)
            val wait = suspend { snapshotFlow { m.revision to m.lyrics.revision }.first { it != seen } }
            if (wake == null) wait() else withTimeoutOrNull(maxOf(1L, wake - frame)) { wait() }
        }
    }
    val shown by remember(m) { derivedStateOf { m.shownAt(frame) } }
    val variant = m.variant
    val inks = remember(shown, variant, measures) { measures.map { it.ink(shown, variant) } }
    val times by remember(m, halves) { derivedStateOf { halves.map { npTimes(it.g, m, frame, npNanosAt(frame)) } } }
    val time = remember(times, measures) { measures.zip(times) { me, t -> me.time(t) } }
    val act by rememberUpdatedState(onAct)
    val seek by rememberUpdatedState(onSeek)
    val touch = remember(m, halves) { NpTouch(m, halves, { act(it) }, { seek(it) }) }
    val liveNow by rememberUpdatedState(live)
    val halvesNow by rememberUpdatedState(halves)
    val owner = remember { Any() }
    LaunchedEffect(m, owner) {
        snapshotFlow { if (liveNow && !m.lyricsShown() && m.seekShown()) seekEdges(halvesNow.holding(NpPart.Times)) else null }.collect { m.band(owner, it) }
    }
    DisposableEffect(m, owner) { onDispose { m.band(owner, null) } }
    PageLayer(visible, frozen, Modifier.pxSize(s.widthPx, s.heightPx)) { covered ->
        if (backdrop != null) {
            NpBackdropLayer(backdrop, now, dim = { t -> fxU(m.lyrics.dim(t)) }, dimming = { t -> m.lyrics.dimming(t) }, watch = { m.lyrics.revision })
        }
        val lyr = m.lyricsView
        val fade = remember(m) { derivedStateOf { if (m.lyricsView) m.lyrics.fade(frame) else null } }.value
        val on = m.lyrics.on(variant)
        val lyricsNow = lyr && (!variant || m.lyrics.phoneLyrics) && (if (fade != null && fade.from) !on else on)
        val k = fade?.alpha ?: 1f
        val back = rememberLook(m, hideFocus, snap, covered = covered) { npBackLook(m, frame, hideFocus) }
        val text = rememberLook(m, halves, covered = covered) { halves.map { npTextLook(m, it.g, frame) } }
        val mark = rememberLook(m, covered = covered) { npMarkLook(m, frame) }
        val rings = rememberLook(m, hideFocus, snap, covered = covered) { npRingsLook(m, frame, hideFocus) }
        val statusAlpha = rememberLook(m, covered = covered) { npStatusAlpha(m, frame) }
        val full = Modifier.pxSize(s.widthPx, s.heightPx)
        Canvas(full) { halves.forEach { h -> if (NpPart.Back in h.parts) npHalf(h) { npBackRing(h.g, back.value, k, null) } } }
        if (lyricsNow) {
            val lh = halves.holding(NpPart.Art)
            NpLyrics(m.lyrics, m, lh.g, covers, a, now, lh) { k }
        } else {
            Canvas(full) {
                halves.forEachIndexed { i, h ->
                    val t = text.value[i]
                    npHalf(h) {
                        if (NpPart.Top in h.parts) npTop(h.g, inks[i], variant, k, null)
                        if (NpPart.Art in h.parts) npArt(h.g, art, variant, k, t.covers)
                        if (NpPart.Title in h.parts) npTitle(h.g, inks[i], variant, k, null)
                    }
                }
            }
            Canvas(full) { halves.forEach { h -> if (NpPart.Mark in h.parts) npHalf(h) { npMark(h.g, mark.value, k, null) } } }
            Canvas(full) {
                halves.forEachIndexed { i, h ->
                    val t = text.value[i]
                    if (NpPart.Times in h.parts) npHalf(h) { npTimeBand(h.g, time[i], t.shown.durationMs, accent, k, null) }
                }
            }
        }
        Canvas(full) { halves.forEach { h -> if (NpPart.Rings in h.parts) npHalf(h) { npRings(h.g, rings.value, k, null) } } }
        if (status != null) Canvas(full) { halves.forEach { h -> if (NpPart.Status in h.parts) npHalf(h) { statusRow(status, accent, accentOff, statusAlpha.value * k) } } }
        Box(
            full.tapTarget(
                a,
                Tap(
                    onPress = { touch.press() },
                    onCancel = { touch.cancel(now()) },
                    onTap = { touch.tap(now()) },
                    onLong = { touch.hold() },
                    onAt = { o -> touch.at(o.x, o.y, now()) },
                ),
                if (live) 0 else HIDDEN,
            ),
        )
    }
}
