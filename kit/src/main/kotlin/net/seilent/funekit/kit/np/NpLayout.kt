package net.seilent.funekit.kit.np

import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.px
import kotlin.math.max
import kotlin.math.min

const val NP_RINGS = 3
const val NP_BACK = 3
const val NP_MARK = 4
const val NP_PRESSES = 5
const val NP_HEART = NP_RINGS - 1
private const val HALF = 2f

enum class NpRingPlace { AboveBar, Units }

class NpRect(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val w: Int get() = x1 - x0
    val h: Int get() = y1 - y0
}

class NpLayout(val s: Scale, place: NpRingPlace = NpRingPlace.AboveBar) {
    val p: Float = s.stdPpu
    private val b = s.board

    val backSize: Int = px(Tok.npBackRing, p)
    val backClip: NpRect = NpRect(0, 0, px(Tok.npBackRingX + Tok.npBackRing, p), px(Tok.npBackRingY + Tok.npBackRing, p))
    val backCx: Int = px(Tok.npBackRingX + Tok.npBackRing / HALF, p)
    val backCy: Int = px(Tok.npBackRingY + Tok.npBackRing / HALF, p)
    val backStroke: Int = px(Tok.npBackRingStroke, p)
    val backStrokeIdle: Int = px(Tok.ringStrokeIdle, p)
    val backGlyph: Int = px(Tok.npBackGlyph, p)

    val textX64: Int = pen64(Tok.npTextX, p)
    val textClip: NpRect = NpRect(px(Tok.npTextX, p), 0, px(Tok.npTextRight, p), s.heightPx)
    val artistY: Int = px(b.npArtistY, p)
    val albumY: Int = px(b.npAlbumY, p)
    val fromY: Int = px(b.npFromBaseline, p)

    val artSide: Int = px(b.npArt, p)
    val artX: Int = px(Tok.npArtX, p)
    val artY: Int = px(b.npArtTop, p)
    val artOx: Int = (artSide * Old.motion_trackArtOriginX).toInt()
    val artOy: Int = (artSide * Old.motion_trackArtOriginY).toInt()
    val btGlyph: Int = px(Tok.npBtGlyph, p)

    val bar: NpRect = run {
        val y0 = artY + artSide + px(Tok.npBarGap, p)
        NpRect(artX, y0, artX + artSide, y0 + px(Tok.npBarHeight, p))
    }

    val timeBase: Int = px(b.npProgressY + if (s.large) Tok.npTimeBaselineLarge else Tok.npTimeBaseline, p)
    val timeX64: Int = pen64(Tok.npArtX, p)
    val timeRight64: Int = pen64(Tok.npArtX + b.npArt, p)
    val timeWideRight64: Int = pen64(s.w - Tok.edge, p)
    val timePair64: Int = pen64(Tok.npTimePairGap, p)
    val art64: Int = pen64(b.npArt, p)

    val titleY: Int = px(b.npTitleY, p)
    val nextTracks: Int = b.npNextCount.toInt()
    val nextX64: Int = pen64(Tok.npTextX + Tok.npNextIndent, p)
    val ringYU: Float = if (!s.navOn) b.npRingY else if (place == NpRingPlace.AboveBar) b.npRingY + s.stdContentBottom - s.stdH else b.npRingNavY
    private val navLines: Int = (
        if (s.large) {
            if (s.navOn) b.npNextNavCountLarge else b.npNextCountLarge
        } else if (s.navOn) {
            b.npNextNavCount
        } else {
            b.npNextCount
        }
        ).toInt()
    val nextLines: Int = if (s.navOn && place == NpRingPlace.AboveBar) (0 until navLines).count { nextBaseU(it) < ringYU - Tok.npRing / HALF } else navLines

    fun nextBaseU(i: Int): Float =
        if (s.large) b.npTitleY + Tok.npNextDropLarge + i * Tok.npNextLinePitchLarge else b.npTitleY + Tok.npNextDrop + i * Tok.npNextLinePitch

    fun nextBase(i: Int): Int = px(nextBaseU(i), p)

    val titleTopU: Float = b.npTitleY - Tok.capHeight * Roles.npViewTitle.size
    val titleBottomU: Float = nextBaseU(max(nextLines - 1, 0))

    val ringD: Int = px(Tok.npRing, p)
    val ringCy: Int = px(ringYU, p)
    val ringStroke: Int = px(Tok.npRingStroke, p)
    val ringStrokeFocused: Int = px(Tok.ringStrokeFocused, p)
    val repeatOne: Int = px(Tok.npRepeatOne, p)

    fun ringCxU(i: Int): Float = when (i) {
        0 -> Tok.npRingCx0
        1 -> Tok.npRingCx1
        else -> Tok.npRingCx2
    }

    fun ringCx(i: Int): Int = px(ringCxU(i), p)

    val markX: Int = artX + artSide + px(Tok.tileLGutter, p) + ringD / HALF.toInt()
    val markY: Int = artY + artSide - (ringD + 1) / HALF.toInt()
    private val markXU: Float = Tok.npArtX + b.npArt + Tok.tileLGutter + Tok.npRing / HALF
    private val markYU: Float = b.npArtTop + b.npArt - Tok.npRing / HALF

    val statusClip: Int = px(Tok.npStatusTop + Tok.npStatusHeight, p)

    fun backHit(x: Float, y: Float): Boolean = x < Tok.npBackRingX + Tok.npBackRing && y < Tok.npBackRingY + Tok.npBackRing

    fun ringHit(x: Float, y: Float, rings: Int): Int {
        val c0 = ringCxU(0)
        val c1 = ringCxU(1)
        val c2 = ringCxU(NP_HEART)
        val lo = c0 - (c1 - c0) / HALF
        val hi = c2 + (c2 - c1) / HALF
        val top = titleBottomU
        val bottom = min(HALF * ringYU - top, s.stdH - Tok.gesture_bottom)
        if (x < lo || x >= hi || y < top || y >= bottom) return -1
        val i = if (x < (c0 + c1) / HALF) 0 else if (x < (c1 + c2) / HALF) 1 else NP_HEART
        return if (i < rings) i else -1
    }

    fun markHit(x: Float, y: Float): Boolean {
        val half = Tok.npMarkHit / HALF
        return x >= max(markXU - half, Tok.npArtX + b.npArt) && x < min(markXU + half, s.w) && y >= markYU - half && y < markYU + half
    }

    fun titleHit(x: Float, y: Float): Boolean = x >= Tok.npTextX && x < s.w - Tok.edge && y >= titleTopU && y < titleBottomU

    fun artHit(x: Float, y: Float): Boolean = x >= Tok.npArtX && x < Tok.npArtX + b.npArt && y >= b.npArtTop && y < b.npArtTop + b.npArt

    fun seekHit(x: Float, y: Float): Boolean = x >= Tok.npSeekBandX0 && x < b.npSeekBandX1 && y >= b.npProgressY && y < b.npSeekBandBottom

    fun seekAt(x: Float, durationMs: Long): Long {
        val c = x.coerceIn(Tok.npArtX, Tok.npArtX + b.npArt)
        return (durationMs * ((c - Tok.npArtX) / b.npArt)).toLong()
    }

}
