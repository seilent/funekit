package net.seilent.funekit.kit.np

import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptText
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.px
import net.seilent.funekit.textPx
import kotlin.math.max
import kotlin.math.min

const val NP_RINGS = 3
const val NP_BACK = 3
const val NP_MARK = 4
const val NP_PRESSES = 5
const val NP_HEART = NP_RINGS - 1
private const val HALF = 2f
private const val THIRD = 3f

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
    val btGlyph: Int = px(Tok.npBtGlyph, p)

    val bar: NpRect = run {
        val y0 = artY + artSide + px(Tok.npBarGap, p)
        NpRect(artX, y0, artX + artSide, y0 + px(Tok.npBarHeight, p))
    }

    val seekBand: NpRect = NpRect(px(Tok.npSeekBandX0, p), px(b.npProgressY, p), px(b.npSeekBandX1, p), px(b.npSeekBandBottom, p))

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

    val ringRect: NpRect = run {
        val d = px(Tok.npBackRing, p)
        val r = px(Tok.npBackRing / HALF, p)
        val side = d + r - d / HALF.toInt()
        val x0 = px(Tok.npBackRingX + Tok.npBackRing / HALF, p) - r
        val y0 = px(Tok.npBackRingY + Tok.npBackRing / HALF, p) - r
        NpRect(x0, y0, x0 + side, y0 + side)
    }

    fun partRects(variant: Boolean, lyrics: Boolean): Map<Part, NpRect> {
        val w = s.widthPx
        val h = s.heightPx
        val half = HALF.toInt()
        val ap = rolePx(Roles.npViewArtist, s, p)
        val lp = rolePx(Roles.npViewAlbum, s, p)
        var top = artistY - Metrics.ascent(ap)
        if (variant) top = min(top, fromY - Metrics.ascent(rolePx(keptText(Roles.sectionLabel, s), s, p)))
        val mid = (artistY + Metrics.descent(ap) + albumY - Metrics.ascent(lp) + 1) / half
        var last = 0
        fun next(v: Int): Int {
            last = min(max(v, last), h)
            return last
        }
        val eTop = next(top)
        val eMid = next(mid)
        val eArt = next(artY)
        val eBar0 = next(bar.y0)
        val eBar1 = next(bar.y1)
        val eTime = next(px((b.npProgressY + (if (s.large) Tok.npTimeBaselineLarge else Tok.npTimeBaseline) + titleTopU) / HALF, p))
        val eTitle = next(px(b.npTitleY + Tok.npNextDrop / THIRD, p))
        val eRing = next(px(ringYU - Tok.npRing / HALF - Tok.npRingStroke, p))
        val eEnd = next(h)
        fun row(y0: Int, y1: Int) = NpRect(0, y0, w, y1)
        val tx = artX + artSide / half
        val rx = px((Tok.npRingCx0 + Tok.npRingCx1) / HALF, p)
        val st = px(Tok.npStatusTop, p)
        val sh = px(Tok.npStatusHeight, p)
        val tp = textPx(Tok.npStatusText, p, b.minTextPx)
        val sb = st + (sh - Metrics.ascent(tp) - Metrics.descent(tp)) / half + Metrics.ascent(tp)
        val none = NpRect(0, 0, 0, 0)
        val out = mutableMapOf(
            Part.NpStatus to NpRect(0, 0, w, min(max(st + sh, sb + Metrics.descent(tp)), eTop)),
            Part.NpArtist to none, Part.NpAlbum to none, Part.NpIn to none, Part.NpBar to none, Part.NpTimeL to none,
            Part.NpTimeR to none, Part.NpTitle to none, Part.NpNext to none,
            Part.NpShuffle to NpRect(0, eRing, rx, eEnd),
            Part.NpRings to NpRect(rx, eRing, w, eEnd),
        )
        if (lyrics) {
            out[Part.NpTitle] = NpRect(0, eTop, w, eRing)
            return out
        }
        out[Part.NpArtist] = row(eTop, eMid)
        out[Part.NpAlbum] = row(eMid, eArt)
        out[Part.NpIn] = row(eArt, eBar0)
        out[Part.NpBar] = row(eBar0, eBar1)
        out[Part.NpTimeL] = NpRect(0, eBar1, tx, eTime)
        out[Part.NpTimeR] = NpRect(tx, eBar1, w, eTime)
        out[Part.NpTitle] = row(eTime, eTitle)
        out[Part.NpNext] = row(eTitle, eRing)
        return out
    }

}
