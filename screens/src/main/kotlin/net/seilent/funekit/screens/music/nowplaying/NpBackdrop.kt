package net.seilent.funekit.screens.music.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.first
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import kotlin.math.roundToInt

private const val HALF = 0.5f

data class NpFocus(val x: Float, val y: Float) {
    companion object {
        val Center = NpFocus(HALF, HALF)
    }
}

data class NpPicture(val image: ImageBitmap, val focus: NpFocus = NpFocus.Center, val whole: Boolean = false)

class NpLayer(val picture: NpPicture, val alpha: Float)

fun npCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int, f: NpFocus): Pair<IntOffset, IntSize>? {
    val w: Int
    val h: Int
    if (srcW.toLong() * dstH >= srcH.toLong() * dstW) {
        h = srcH
        w = (srcH.toLong() * dstW / dstH).toInt()
    } else {
        w = srcW
        h = (srcW.toLong() * dstH / dstW).toInt()
    }
    if (w <= 0 || h <= 0) return null
    val x = (f.x * srcW - w * HALF).toInt()
    val y = (f.y * srcH - h * HALF).toInt()
    return IntOffset(x.coerceIn(0, srcW - w), y.coerceIn(0, srcH - h)) to IntSize(w, h)
}

class NpBackdrop {
    var shown: NpPicture? = null
        private set
    var revision by mutableIntStateOf(0)
        private set

    private var was: NpLayer? = null
    private var fadeAt = 0L
    private var fade = false
    private var w = 0
    private var h = 0
    private val dur = Tok.motion_backdropFade.toLong()

    private fun mix(now: Long): Float = if (!fade) 1f else ((now - fadeAt).toFloat() / dur).coerceIn(0f, 1f)

    fun size(w: Int, h: Int) {
        this.w = w
        this.h = h
    }

    fun fading(now: Long): Boolean = fade && now - fadeAt < dur

    fun layers(now: Long): List<NpLayer> {
        val p = shown
        val f = mix(now)
        val old = was?.takeIf { fading(now) }
        if (p == null) return listOfNotNull(old?.let { NpLayer(it.picture, it.alpha * (1 - f)) })
        return listOfNotNull(old) + NpLayer(p, f)
    }

    private fun freeze(cur: List<NpLayer>): NpLayer? {
        if (w <= 0 || h <= 0) return null
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ImageCanvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Ink.bg)
            npLayers(cur)
        }
        return NpLayer(NpPicture(img, whole = true), 1f)
    }

    fun set(p: NpPicture?, now: Long, cut: Boolean = false) {
        if (p == shown) return
        if (cut) {
            was = null
            fade = false
        } else {
            val cur = layers(now).filter { it.alpha > 0f }
            was = when {
                cur.isEmpty() -> null
                cur.size == 1 && cur[0].alpha >= 1f -> cur[0]
                else -> freeze(cur)
            }
            fade = was != null || p != null
            fadeAt = now
        }
        shown = p
        revision++
    }

    fun stepTo(now: Long) {
        if (!fade || fading(now)) return
        fade = false
        was = null
        revision++
    }
}

private fun DrawScope.npLayers(layers: List<NpLayer>) {
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    for (l in layers) {
        val img = l.picture.image
        if (l.picture.whole) {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset.Zero, IntSize(w, h), l.alpha)
        } else {
            val (at, crop) = npCrop(img.width, img.height, w, h, l.picture.focus) ?: continue
            drawImage(img, at, crop, IntOffset.Zero, IntSize(w, h), l.alpha)
        }
    }
}

fun DrawScope.npBackdrop(layers: List<NpLayer>, dim: Float) {
    if (layers.isEmpty()) return
    if (size.width.roundToInt() <= 0 || size.height.roundToInt() <= 0) return
    drawRect(Ink.bg)
    npLayers(layers)
    drawRect(
        Brush.verticalGradient(
            Tok.backdrop_stop0_pos to Ink.bg.copy(alpha = Tok.backdrop_stop0_alpha),
            Tok.backdrop_stop1_pos to Ink.bg.copy(alpha = Tok.backdrop_stop1_alpha),
            Tok.backdrop_stop2_pos to Ink.bg.copy(alpha = Tok.backdrop_stop2_alpha),
        ),
    )
    if (dim > 0f) drawRect(Ink.bg, alpha = dim * Tok.scrim_sheet_alpha)
}

@Composable
fun NpBackdropLayer(b: NpBackdrop, now: () -> Long, dim: (Long) -> Float = { 0f }, dimming: (Long) -> Boolean = { false }, watch: () -> Any? = { null }) {
    var frame by remember { mutableLongStateOf(now()) }
    LaunchedEffect(b) {
        while (true) {
            withFrameMillis { at ->
                b.stepTo(at)
                frame = at
            }
            if (b.fading(frame) || dimming(frame)) continue
            val seen = b.revision to watch()
            snapshotFlow { b.revision to watch() }.first { it != seen }
        }
    }
    Canvas(Modifier.fillMaxSize().onSizeChanged { b.size(it.width, it.height) }) { npBackdrop(b.layers(frame), dim(frame)) }
}
