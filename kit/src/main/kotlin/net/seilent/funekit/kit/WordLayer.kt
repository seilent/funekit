package net.seilent.funekit.kit

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Role
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.px
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private const val FX = 65536f

@Composable
fun rememberWord(text: String, role: Role, s: Scale, ppu: Float = s.stdPpu): Word? {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    return remember(text, role, s.board, ppu, density, fonts, covers) {
        m.word(text, role, roleStyle(role, s, density, Ink.white, ppu = ppu), fonts, density, covers).takeIf { it.inked }
    }
}

class PlacedImage(val image: ImageBitmap, val left: Int, val top: Int)

class WordImage {
    private var held: Word? = null
    private var fraction = 0f
    private var crop: IntRect? = null
    var placed: PlacedImage? = null
        private set

    fun at(w: Word, f: Float, clip: IntRect, density: Density): PlacedImage? {
        if (!w.inked) return null
        val ink = IntRect(floor(f + w.inkLeft).toInt() - 1, floor(w.inkTop).toInt() - 1, ceil(f + w.inkRight).toInt() + 1, ceil(w.inkBottom).toInt() + 1)
        val box = ink.intersect(clip)
        if (w === held && f == fraction && box == crop) return placed
        held = null
        crop = null
        placed = null
        if (box.isEmpty) return null
        val drawn = Bitmap.createBitmap(ink.width, ink.height, Bitmap.Config.ALPHA_8)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(drawn.asImageBitmap()), Size(ink.width.toFloat(), ink.height.toFloat())) {
            word(w, f - ink.left, -ink.top.toFloat(), Ink.white, 1f)
        }
        val cut = Bitmap.createBitmap(drawn, box.left - ink.left, box.top - ink.top, box.width, box.height)
        drawn.recycle()
        val fixed = cut.copy(Bitmap.Config.ALPHA_8, false)
        cut.recycle()
        held = w
        fraction = f
        crop = box
        placed = PlacedImage(fixed.asImageBitmap(), box.left, box.top)
        return placed
    }
}

fun DrawScope.wordImage(cache: WordImage, w: Word, x: Float, baseline: Int, color: Color, alpha: Float, clip: Rect) {
    if (alpha <= 0f) return
    val pen = floor(x)
    val left = pen.toInt()
    val inside = IntRect(floor(clip.left).toInt() - left, floor(clip.top).toInt() - baseline, ceil(clip.right).toInt() - left, ceil(clip.bottom).toInt() - baseline)
    val p = cache.at(w, x - pen, inside, this) ?: return
    drawImage(p.image, Offset(pen + p.left, (baseline + p.top).toFloat()), colorFilter = ColorFilter.tint(color.copy(alpha = color.alpha * alpha)))
}

fun DrawScope.sprite(w: Word, scale: Float, x: Float, y: Float, alpha: Float, bottom: Int) {
    if (alpha <= 0f) return
    val k = max(scale, 1f / FX)
    clipRect(0f, 0f, size.width, bottom.coerceIn(0, size.height.toInt()).toFloat()) {
        withTransform({
            translate(x, y)
            scale(k, k, Offset.Zero)
        }) { word(w, 0f, 0f, Ink.white, alpha) }
    }
}

fun wordBottom(s: Scale, y: Float, scale: Float, done: Float, toHeader: Boolean): Int {
    val ext = (px(s.board.pivotTop, s.stdPpu) - px(Tok.page_headerBaseline, s.stdPpu)).toFloat()
    return lerp(s.heightPx.toFloat(), y + ext * scale, if (toHeader) done else 1 - done).roundToInt()
}
