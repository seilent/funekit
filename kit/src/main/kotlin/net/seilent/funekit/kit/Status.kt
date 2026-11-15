package net.seilent.funekit.kit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Fonts
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyph
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val PERCENT = 100
private const val HALF = 2

enum class Play { None, Playing, Paused }

class StatusState(
    val clock: String,
    val battery: Int,
    val percent: Boolean,
    val play: Play,
    val bluetooth: Boolean,
    val wifi: Int,
    val volume: Int,
)

class StatusText(val clock: Word?, val percent: Word?, val volume: Word?)

class StatusInk(val state: StatusState, val text: StatusText, val line: StatusLine, val scale: Scale, val box: StatusBox) {
    fun sameRow(o: StatusInk): Boolean = scale.ppu == o.scale.ppu && scale.stdPpu == o.scale.stdPpu && line == o.line
}

@Composable
fun rememberStatusInk(state: StatusState, scale: Scale, box: StatusBox): StatusInk {
    val measurer = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val line = statusLine(scale, box)
    val text = remember(state.clock, state.battery, state.volume, line.textPx, scale.ppu, density, fonts) {
        fun measure(t: String) = measurer.word(t, Roles.time, timeStyle(scale, line, density), fonts, density, null)
        StatusText(
            clock = if (state.clock.isEmpty()) null else measure(state.clock),
            percent = if (state.battery >= 0) measure("${min(state.battery, PERCENT)}%") else null,
            volume = if (state.volume >= 0) measure(state.volume.toString()) else null,
        )
    }
    return StatusInk(state, text, line, scale, box)
}

fun DrawScope.statusRow(ink: StatusInk, accent: Color, accentOff: Boolean, alpha: Float) =
    drawStatus(ink.state, ink.text, ink.line, ink.scale, ink.box, Ink.white, accent, accentOff, alpha)

@Composable
fun Status(
    state: StatusState,
    scale: Scale,
    box: StatusBox,
    accent: Color,
    accentOff: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val ink = rememberStatusInk(state, scale, box)
    Canvas(modifier) { statusRow(ink, accent, accentOff, alpha) }
}

private fun DrawScope.drawStatus(
    st: StatusState,
    texts: StatusText,
    line: StatusLine,
    s: Scale,
    box: StatusBox,
    fg: Color,
    accent: Color,
    accentOff: Boolean,
    a: Float,
) {
    val size = line.size
    val top = line.top
    val gap = line.gap
    var right = line.right
    val ta = Roles.time.alpha * a
    val cy = (top + size / HALF).toFloat()

    fun text(t: Word, color: Color): Int {
        word(t, right - t.width, line.baseline.toFloat(), color, ta)
        return ceil(t.width).toInt()
    }

    fun center() = Offset((right - size / HALF).toFloat(), cy)

    fun mark(g: Glyph, color: Color) {
        glyph(g, center(), size.toFloat(), color, a)
        right -= size + gap
    }

    if (st.battery >= 0) {
        val low = st.battery < Tok.batteryLowPercent
        val color = if (low) accent else fg
        val percent = texts.percent
        if (percent != null && (st.percent || (low && accentOff))) {
            right -= text(percent, color) + gap
        } else if (box.batteryW > 0f) {
            right = outline(st.battery, right, top, size, box, s, color, a) - gap
        } else {
            val x0 = right - size
            glyph(Glyphs.battery, Offset((x0 + size / HALF).toFloat(), cy), size.toFloat(), color, a)
            val box = Tok.glyphBox
            val lx = x0 + (Tok.statusBatteryX * size / box).toInt()
            val lw = (Tok.statusBatteryW * size * min(st.battery, PERCENT) / (box * PERCENT)).toInt()
            val y0 = top + (Tok.statusBatteryY * size / box).toInt()
            val y1 = top + ((Tok.statusBatteryY + Tok.statusBatteryH) * size / box).toInt()
            drawRect(color, Offset(lx.toFloat(), y0.toFloat()), Size(lw.toFloat(), (y1 - y0).toFloat()), a)
            right = x0 - gap
        }
    }
    if (st.bluetooth) mark(Glyphs.bluetooth, fg)
    if (st.play != Play.None) mark(if (st.play == Play.Playing) Glyphs.play else Glyphs.pause, fg)
    if (st.wifi >= 0) {
        wifi(center(), size.toFloat(), st.wifi, fg, a)
        right -= size + gap
    }
    texts.clock?.let { right -= text(it, fg) + gap }
    val left = px(box.left, s.stdPpu) + gap
    val volume = texts.volume
    if (volume != null && right - ceil(volume.width).toInt() - gap - size >= left) {
        right -= text(volume, fg) + gap
        glyph(Glyphs.speaker, center(), size.toFloat(), fg, a)
        glyph(Glyphs.speakerWaves, center(), size.toFloat(), fg, a)
    }
}

private const val SKIN = 9
private const val LEVEL_X = 18
private const val LEVEL_Y = 10
private const val ROUND_X = 9
private const val ROUND_Y = 5

private fun timeStyle(s: Scale, line: StatusLine, density: Density) =
    roleStyle(Roles.time, s, density, Ink.white, 1f).merge(
        Fonts.style(Roles.time, line.textPx, density, Roles.time.tracking * s.ppu),
    )

private fun DrawScope.outline(battery: Int, right: Int, top: Int, size: Int, box: StatusBox, s: Scale, color: Color, a: Float): Int {
    val bw = px(box.batteryW, s.ppu)
    val bh = px(box.batteryH, s.ppu)
    val sk = max(1, px(box.batteryH * HALF / SKIN, s.ppu))
    val nub = max(1, px(box.batteryNub, s.ppu))
    val nh = max(1, px(box.batteryNubH, s.ppu))
    val n0 = right - bw
    val x0 = n0 + nub
    val x1 = right
    val y0 = top + size / HALF - bh / HALF
    val y1 = y0 + bh
    fun rect(l: Int, t: Int, r: Int, b: Int) = drawRect(color, Offset(l.toFloat(), t.toFloat()), Size((r - l).toFloat(), (b - t).toFloat()), a)
    rect(x0, y0, x1, y0 + sk)
    rect(x0, y1 - sk, x1, y1)
    rect(x0, y0 + sk, x0 + sk, y1 - sk)
    rect(x1 - sk, y0 + sk, x1, y1 - sk)
    val ny = y0 + (bh - nh) / HALF
    rect(n0, ny, x0, ny + nh)
    for (k in 1..nub / HALF) {
        rect(n0 + k, ny - k, x0, ny - k + 1)
        rect(n0 + k, ny + nh + k - 1, x0, ny + nh + k)
    }
    val iw = x1 - x0 - HALF * sk
    val ih = bh - HALF * sk
    val ix = max(1, (iw + ROUND_X) / LEVEL_X)
    val iy = max(1, (ih + ROUND_Y) / LEVEL_Y)
    val rx = x1 - sk - ix
    val ly = y0 + sk + iy
    val fw = iw - HALF * ix
    rect(rx - fw * min(battery, PERCENT) / PERCENT, ly, rx, y1 - sk - iy)
    return n0
}
