package net.seilent.funekit.screens.lock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.api.FailFast
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.art.cover
import net.seilent.funekit.kit.art.squareArt
import net.seilent.funekit.kit.lock.LockInk
import net.seilent.funekit.kit.lock.LockLayout
import net.seilent.funekit.kit.lock.lockPaint
import net.seilent.funekit.kit.lock.rememberLockWord
import net.seilent.funekit.kit.overlay.VolumeLayer
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.screens.host.LEVEL
import net.seilent.funekit.screens.host.NpFeeds
import net.seilent.funekit.screens.host.VolumeLevel
import net.seilent.funekit.screens.host.acted
import net.seilent.funekit.screens.host.playOf
import net.seilent.funekit.screens.music.nowplaying.npBackdrop
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

private const val MS_S = 1000L

private fun wakeAt(due: Long, t: Long, w: Long): Long {
    val second = t + MS_S - w.mod(MS_S)
    return if (due in t + 1 until second) due else second
}

internal fun DrawScope.lockFrame(under: Boolean, off: Int, alpha: Float, paint: Paint, sheet: DrawScope.() -> Unit) {
    if (under) drawRect(Ink.bg)
    translate(0f, -off.toFloat()) {
        if (alpha >= 1f) {
            sheet()
        } else {
            paint.alpha = alpha
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
            sheet()
            drawContext.canvas.restore()
        }
    }
}

private suspend fun PointerInputScope.lockTouch(state: LockState, s: () -> Scale, now: () -> Long, onDown: () -> Unit) {
    awaitEachGesture {
        val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val p = s().stdPpu
        onDown()
        val t = now()
        if (state.hud.visible(t)) state.hud.dismiss()
        state.down(d.position.y, d.position.x / p, d.position.y / p, d.uptimeMillis, t)
        var ended = false
        try {
            while (!ended) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == d.id } ?: break
                if (c.isConsumed && !c.pressed) break
                if (c.pressed) {
                    state.drag.move(c.position.y, c.position.y / p, c.uptimeMillis)
                } else {
                    state.drag.up(c.position.y, c.position.x / p, c.position.y / p, c.uptimeMillis, now())
                    state.bump()
                    ended = true
                }
            }
        } finally {
            if (!ended) state.rest()
        }
    }
}

@Composable
fun LockScreen(
    state: LockState,
    feeds: NpFeeds,
    scale: Scale,
    clock: () -> String,
    volume: VolumeLevel,
    under: Boolean,
    onDown: () -> Unit,
    onOpen: () -> Unit,
    log: (String) -> Unit,
    now: () -> Long,
    wall: () -> Long,
    read: CoroutineContext = Dispatchers.IO,
) {
    val np = feeds.np
    val b = feeds.backdrop
    val hud = state.hud
    hud.board = scale.board
    hud.w = scale.w
    hud.h = scale.h
    SideEffect { state.size(scale.widthPx, scale.heightPx) }
    val life by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = life.isAtLeast(Lifecycle.State.STARTED)
    if (!started) {
        state.fresh = true
    } else if (state.fresh) {
        state.fresh = false
        val t = now()
        state.wake = wakeAt(state.read(np, clock(), t, true), t, wall())
    }
    val l = remember(scale) { LockLayout(scale) }
    val sc by rememberUpdatedState(scale)
    val opens by rememberUpdatedState(onOpen)
    val downs by rememberUpdatedState(onDown)
    val clocks by rememberUpdatedState(clock)
    val scope = rememberCoroutineScope()
    var tick by remember { mutableLongStateOf(now()) }
    val rev = state.rev
    LaunchedEffect(state, rev) {
        var drawn = Int.MIN_VALUE
        while (true) {
            withFrameMillis {
                val t = now()
                val off = state.drag.offset(t)
                if (!state.drag.dragging || off != drawn || state.line.moving(t)) {
                    tick = t
                    drawn = off
                }
            }
            if (state.drag.landed(tick) && !state.opened) {
                state.opened = true
                opens()
            }
            if (!state.moving(tick)) break
        }
    }
    LaunchedEffect(b) {
        snapshotFlow { b.revision }.collect { tick = now() }
    }
    DisposableEffect(state, volume, scope) {
        fun show(t: Long) {
            hud.show(t, volume.percent(), playOf(feeds.feed) == Play.Playing, if (np.variant) np.shown.from else np.shown.artist, np.shown.title)
        }
        val press: (Int) -> Unit = { d ->
            val t = now()
            if (!hud.visible(t)) {
                val drag = state.drag
                if (drag.committing && !drag.dragging) {
                    if (!state.opened) {
                        state.opened = true
                        opens()
                    }
                } else {
                    state.rest()
                }
            }
            show(t)
            scope.launch(FailFast) { if (acted(log, "volume step", LEVEL) { volume.step(d) } != null) show(now()) }
        }
        state.press = press
        onDispose { if (state.press === press) state.press = null }
    }
    LaunchedEffect(state, np, started) {
        if (!started) return@LaunchedEffect
        while (true) {
            delay(max(1L, state.wake - now()))
            val t = now()
            state.wake = wakeAt(state.read(np, clocks(), t, false), t, wall())
        }
    }
    val context = LocalContext.current
    val cache = remember(context) { Art.get(context) }
    val side = l.artSide
    val ref = np.shown.art
    var square by remember(ref, side) { mutableStateOf(ref?.let { cache[ArtKey.Cover(it, side)] }?.asImageBitmap()) }
    val src = feeds.src
    LaunchedEffect(ref, side, src) {
        val r = ref ?: return@LaunchedEffect
        val source = src ?: return@LaunchedEffect
        if (square != null) return@LaunchedEffect
        square = try {
            withContext(read) { cache.cover(r, side) { source.viewed { it.art(r, side) } }?.asImageBitmap() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("lock art: ${e.message}")
            null
        }
    }
    val phoneArt = np.phone?.art
    var phoneSquare by remember(phoneArt, side) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(phoneArt, side) {
        val p = phoneArt ?: return@LaunchedEffect
        phoneSquare = withContext(Dispatchers.Default) { squareArt(p, side).asImageBitmap() }
    }
    val phone = np.variant
    val art = if (phone) phoneSquare else square
    val shown = np.shown
    val clockWord = rememberLockWord(state.clock, Roles.saverClock, scale)
    val title = rememberLockWord(shown.title, Roles.lockTitle, scale)
    val artist = rememberLockWord(shown.artist, Roles.lockArtist, scale)
    val album = rememberLockWord(if (phone) shown.from else shown.album, Roles.lockAlbum, scale)
    val cur = state.line.text
    val old = remember(rev) { state.line.shown(state.lineAt) }
    val curWord = rememberLockWord(cur.text, Roles.npNext, scale, cur.base)
    val oldWord = rememberLockWord(old.text, Roles.npNext, scale, old.base)
    val paint = remember { Paint() }
    Box(Modifier.fillMaxSize().onSizeChanged { b.size(it.width, it.height) }.pointerInput(state) { lockTouch(state, { sc }, now) { downs() } }) {
        Canvas(Modifier.fillMaxSize()) {
            val t = tick
            lockFrame(under, state.drag.offset(t), state.drag.sheetAlpha(t), paint) {
                drawRect(Ink.bg)
                npBackdrop(b.layers(t), 0f)
                val line = state.line.shown(t)
                val w = if (line === cur) curWord else oldWord
                lockPaint(l, LockInk(clockWord, title, artist, album, w, line.right, state.line.alpha(t)), art, phone)
            }
        }
        Box(Modifier.offset { IntOffset(scale.left, 0) }.pxSize(scale.widthPx, scale.heightPx)) { VolumeLayer(hud, scale) }
    }
}
