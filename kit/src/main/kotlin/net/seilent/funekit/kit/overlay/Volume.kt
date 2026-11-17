package net.seilent.funekit.kit.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.OverHud
import net.seilent.funekit.kit.motion.Press
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.proj.TiltPose
import net.seilent.funekit.kit.proj.platePose
import net.seilent.funekit.kit.touch.Axis
import net.seilent.funekit.kit.touch.axisOf
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val HALF = 2
private const val PAD = 10
private const val PCT = 100
private const val MS_S = 1000
private const val MIN_S = 60
private const val HOUR_S = 3600
private const val HOUR_MS = 3600000L

class VolumeLine(val a: Float, val dx: Float)

class VolumeLook(
    val group: Float,
    val k: Float,
    val size: Float,
    val number: Float,
    val exit: Float,
    val text: Float,
    val textDx: Float,
    val scrim: Float,
    val exitGlow: Float,
    val press: Float,
    val line: List<VolumeLine>,
    val old: List<VolumeLine>,
    val cross: Boolean,
)

class VolumeHud(var board: TokBoard.Board = TokBoard.v1, var w: Float = Tok.canvasW, var h: Float = board.canvasH) : OverHud {
    enum class Hit { None, Exit, Plus, Minus, Prev, Ring, Next }
    enum class Act { None, Pass, Plus, Minus, Prev, Ring, Next, Volume, Seek, Draw }
    class Pos(val on: Boolean = false, val ms: Long = 0, val dur: Long = 0, val track: Long = 0)

    private enum class Drag { Tap, Volume, Seek, Off }
    private enum class Up { Held, Acted, Idle, Cancelled }

    var revision by mutableIntStateOf(0)
        private set

    var shown = false
        private set
    var percent = 0
        private set
    var playing = false
        private set
    var artist = ""
        private set
    var title = ""
        private set
    var oldArtist = ""
        private set
    var oldTitle = ""
        private set
    var held = false
        private set
    var owns = false
        private set
    var drops = false
        private set
    var seeking = false
        private set
    var exitLit = false
        private set
    var readout = ""
        private set
    var seekTo = 0L
        private set
    val exitPress = Press()

    private var inAt = 0L
    private var fadeAt = -1L
    private var chgAt = -1L
    private var closeAt = -1L
    private var backAt = -1L
    private var heldAt = -1L
    private var numShift = 0L
    private var crossAt = -1L
    private var backFrom = 1f
    private var scrimFrom = 1f
    private var closeFrom = 1f
    private var closeScrim = 1f
    private var numFrom = 1f
    private var closeK = 1f
    private var closeSize = 1f
    private var atRest = false
    private var crossSwap = false
    private var outFrom = listOf(VolumeLine(1f, 0f), VolumeLine(1f, 0f))
    private var row = false
    private var drag = Drag.Tap
    private var hit = Hit.None
    private var from = 0
    private var to = 0
    private var keep = -1
    private var step = Tok.volume_stepDefault.toInt()
    private var x0 = 0f
    private var y0 = 0f
    private var ax = 0f
    private var ay = 0f
    private var ly = 0f
    private var at = Pos()
    private var tilt = false
    private var tiltTap = false
    private var rx = 0.0
    private var ry = 0.0
    private var upAt = -1L
    private var upFrom = -1L
    private var press = Hit.None
    private var pressUp = -1L
    private var pressEnd = Up.Held
    private val near = intArrayOf(-1, -1, -1, -1)

    private fun bump() {
        revision++
    }

    private fun progress(e: Long, d: Long): Float = if (d <= 0) 1f else (e.toFloat() / d).coerceIn(0f, 1f)
    private fun endAt(): Long = inAt + Tok.motion_volumeOpenEnd.toLong()
    private fun numberAt(): Long = max(endAt(), chgAt) + numShift + Tok.motion_volumeNumberHold.toLong()
    private fun tiltHold(): Long = (if (tiltTap) Tok.motion_volumeTiltTapHold else Tok.motion_volumeTiltHold).toLong()

    private fun restFrom(now: Long) {
        fadeAt = if (held) -1 else max(endAt(), now) + Tok.motion_volumeRestHold.toLong()
    }

    private fun holdEnd(now: Long) {
        if (heldAt >= 0) numShift += now - heldAt
        heldAt = -1
    }

    private fun tiltUp(now: Long, tap: Boolean) {
        if (tilt && upAt < 0) {
            upAt = now
            upFrom = -1
            tiltTap = tap
        }
    }

    fun show(now: Long, percent: Int, playing: Boolean, artist: String, title: String) {
        if (!visible(now)) {
            inAt = now
            chgAt = -1
            closeAt = -1
            backAt = -1
            crossAt = -1
            pressUp = -1
            heldAt = -1
            crossSwap = false
            press = Hit.None
            exitPress.reset()
            numShift = 0
            numFrom = 1f
            atRest = false
            tilt = false
            upAt = -1
            this.artist = artist
            this.title = title
        } else {
            if (closing(now) || (fadeAt >= 0 && now >= fadeAt)) revive(now)
            if (percent != this.percent) {
                numFrom = numberK(now)
                chgAt = now
                numShift = 0
                if (held) heldAt = now
            }
            setTrack(playing, artist, title, now)
        }
        exitLit = exitLit && owns
        shown = true
        this.percent = percent
        this.playing = playing
        restFrom(now)
        bump()
    }

    override fun dismiss() {
        fadeAt = -1
        closeAt = -1
        pressUp = -1
        press = Hit.None
        exitPress.reset()
        shown = false
        tilt = false
        upAt = -1
        dropTouch()
        bump()
    }

    fun setTrack(playing: Boolean, artist: String, title: String, now: Long = -1): Boolean {
        val flip = playing != this.playing
        this.playing = playing
        if (artist == this.artist && title == this.title) {
            if (flip) bump()
            return flip
        }
        if (now < 0 || !shown) {
            crossAt = -1
            crossSwap = false
        } else if (crossAt < 0 || now - crossAt >= Tok.motion_volumeTextInAt.toLong()) {
            var inLines = listOf(VolumeLine(1f, 0f), VolumeLine(1f, 0f))
            if (crossAt >= 0) inLines = crossLines(now - crossAt).first
            outFrom = inLines
            crossAt = now
            crossSwap = true
            oldArtist = this.artist
            oldTitle = this.title
        }
        this.artist = artist
        this.title = title
        bump()
        return true
    }

    override fun hide(now: Long) {
        if (closeAt < 0 && shown) {
            val l = look(now)
            closeFrom = rest(now)
            closeScrim = scrimK(now)
            closeK = l.k
            closeSize = l.size
            closeAt = now
        }
        if (press != Hit.None && pressUp < 0) {
            pressUp = now
            pressEnd = Up.Cancelled
        }
        tiltUp(now, drag == Drag.Tap)
        holdEnd(now)
        dropTouch()
        bump()
    }

    fun revive(now: Long) {
        backFrom = rest(now)
        scrimFrom = scrimK(now)
        backAt = now
        closeAt = -1
        atRest = true
        bump()
    }

    override fun visible(now: Long): Boolean =
        shown && !(closeAt >= 0 && now >= closeAt + closeMs()) &&
            !(fadeAt >= 0 && now >= fadeAt + Tok.motion_volumeRestSlow.toLong() + Tok.motion_volumeScrimOut.toLong())

    override fun opening(now: Long): Boolean = shown && now < endAt()

    override fun closing(now: Long): Boolean = closeAt >= 0 || (fadeAt >= 0 && now >= fadeAt + Tok.motion_volumeRestSlow.toLong())

    fun tapClosing(now: Long): Boolean = closeAt >= 0 && now < closeAt + closeMs()

    override val hiding: Boolean get() = closeAt >= 0

    fun rest(now: Long): Float {
        if (!shown) return 0f
        if (closeAt >= 0) return closeFrom * (1f - progress(now - closeAt, Tok.motion_volumeTouchClose.toLong()))
        val backMs = Tok.motion_volumeNumberBack.toLong()
        val back = if (backAt >= 0) min(1f, backFrom + progress(now - backAt, backMs)) else 1f
        if (fadeAt < 0 || now < fadeAt) return back
        val slow = Tok.motion_volumeRestSlow.toLong()
        if (now < fadeAt + slow) return lerp(back, Tok.motion_volumeRestLevel, progress(now - fadeAt, slow))
        return lerp(Tok.motion_volumeRestLevel, 0f, progress(now - fadeAt - slow, Tok.motion_volumeRestFast.toLong()))
    }

    fun scrimK(now: Long): Float {
        if (!shown) return 0f
        if (closeAt >= 0) {
            val e = now - closeAt + Tok.motion_volumeTouchScrimLead.toLong()
            return closeScrim * (1f - Curves.easeOut.at(progress(e, Tok.motion_volumeTouchScrim.toLong())))
        }
        val inK = if (backAt >= 0) {
            min(1f, scrimFrom + progress(now - backAt, Tok.motion_volumeNumberBack.toLong()))
        } else {
            progress(now - inAt + Tok.motion_volumeScrimLead.toLong(), Tok.motion_volumeOpen.toLong())
        }
        if (fadeAt < 0) return inK
        val out = fadeAt + Tok.motion_volumeRestSlow.toLong()
        return if (now < out) inK else inK * (1f - progress(now - out, Tok.motion_volumeScrimOut.toLong()))
    }

    fun numberK(now: Long): Float {
        if (!shown) return 0f
        val t = if (held && heldAt >= 0) min(now, heldAt) else now
        val hAt = numberAt()
        var own = if (t < hAt) 1f else 1f - Curves.easeIn.at(progress(t - hAt, Tok.motion_volumeNumberFade.toLong()))
        if (chgAt >= 0 && numFrom < 1f) own = min(own, numFrom + progress(now - chgAt, Tok.motion_volumeNumberBack.toLong()))
        return own
    }

    fun look(now: Long): VolumeLook {
        val nowF = now.toFloat()
        if (!shown) {
            val z = listOf(VolumeLine(0f, 0f), VolumeLine(0f, 0f))
            return VolumeLook(0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, exitPress.lit(nowF), pressK(now), z, z, false)
        }
        val r = rest(now)
        val e = now - inAt
        val lead = Tok.motion_volumeGroupLead.toLong()
        var k = if (atRest) 1f else Curves.powOut17.at(progress(e + lead, Tok.motion_volumeGroup.toLong()))
        var size = Tok.motion_volumeGroupScale + (1f - Tok.motion_volumeGroupScale) * k
        val slow = Tok.motion_volumeRestSlow.toLong()
        if (closeAt >= 0) {
            k = closeK
            size = closeSize
        } else if (fadeAt >= 0 && now >= fadeAt && now < fadeAt + slow) {
            k = lerp(1f, Tok.motion_volumeCloseSlowK, progress(now - fadeAt, slow))
            size = 1f
        } else if (fadeAt >= 0 && now >= fadeAt + slow) {
            val p = progress(now - fadeAt - slow, Tok.motion_volumeRestFast.toLong())
            k = lerp(Tok.motion_volumeCloseSlowK, Tok.motion_volumeCloseK, p)
            size = lerp(1f, Tok.motion_volumeCloseSize, p)
        }
        val group = Curves.cubicOut.at(progress(e + lead, Tok.motion_volumeGroupFade.toLong())) * r
        val num = Curves.powOut17.at(progress(e - Tok.motion_volumeNumberAt.toLong(), Tok.motion_volumeNumberIn.toLong()))
        val number = num * numberK(now) * r
        val exitIn = Curves.powOut17.at(progress(e - Tok.motion_volumeExitAt.toLong(), Tok.motion_volumeExitIn.toLong()))
        val text = Curves.easeOut.at(progress(e - Tok.motion_volumeTextFadeAt.toLong(), Tok.motion_volumeTextFadeIn.toLong())) * r
        val slid = Curves.easeOut.at(progress(e - Tok.motion_volumeTextAt.toLong(), Tok.motion_volumeTextSlide.toLong()))
        val textDx = Tok.motion_volumeTextShift * (1f - slid)
        var line = listOf(VolumeLine(text, textDx), VolumeLine(text, textDx))
        var old = listOf(VolumeLine(0f, 0f), VolumeLine(0f, 0f))
        val cross = crossAt >= 0
        if (cross) {
            val (inL, outL) = crossLines(now - crossAt)
            line = inL.map { VolumeLine(it.a * text, textDx + it.dx) }
            old = outL.map { VolumeLine(it.a * text, textDx + it.dx) }
        }
        return VolumeLook(group, k, size, number, exitIn * r, text, textDx, scrimK(now), exitPress.lit(nowF), pressK(now), line, old, cross)
    }

    private fun crossLines(c: Long): Pair<List<VolumeLine>, List<VolumeLine>> {
        val atIn = Tok.motion_volumeTextInAt.toLong()
        val gone = progress(c, Tok.motion_volumeTextOut.toLong())
        val shift = Tok.motion_volumeTextOutShift * gone
        val old = listOf(
            VolumeLine(outFrom[0].a * (1f - progress(c, Tok.motion_volumeTextOutFade.toLong())), outFrom[0].dx - shift),
            VolumeLine(outFrom[1].a * (1f - Curves.easeOut.at(gone)), outFrom[1].dx - shift * Tok.motion_volumeTextTitleOut),
        )
        val come = if (c < atIn) 0f else progress(c - atIn, Tok.motion_volumeTextIn.toLong())
        val inA = if (c < atIn) 0f else progress(c - atIn, Tok.motion_volumeTextInFade.toLong())
        val left = Tok.motion_volumeTextInShift * (1f - come)
        return listOf(VolumeLine(inA, left), VolumeLine(inA, left * Tok.motion_volumeTextTitleIn)) to old
    }

    private fun dropTouch() {
        if (tilt && upAt < 0) tilt = false
        held = false
        seeking = false
        drag = Drag.Tap
        if (owns) {
            owns = false
            drops = true
        }
    }

    fun touch() {
        owns = false
        drops = false
        bump()
    }

    fun cancel(now: Long) {
        if (!owns) return
        if (press != Hit.None && pressUp < 0) {
            pressUp = now
            pressEnd = Up.Cancelled
        }
        tiltUp(now, drag == Drag.Tap)
        holdEnd(now)
        dropTouch()
        exitLit = false
        exitPress.cancel(now.toFloat())
        restFrom(now)
        bump()
    }

    fun anchor(percent: Int) {
        if (drag != Drag.Volume) return
        from = percent
        to = percent
        keep = percent
        ay = ly
    }

    fun setStep(pct: Int) {
        step = pct
    }

    fun setNear(below: Int, above: Int, below2: Int, above2: Int) {
        listOf(below, above, below2, above2).forEachIndexed { i, v -> near[i] = v }
        bump()
    }

    fun near(i: Int): Int = near[i]

    val number: Int get() = to

    fun took(percent: Int) {
        keep = percent
    }

    fun keep(): Int = if (owns && drag == Drag.Volume) keep else -1

    fun pendingSeek(): Long? = if (seeking) seekTo else null

    fun seekMoved(): Boolean = seekTo != at.ms

    fun seekTrack(): Long = at.track

    fun hit(x: Float, y: Float, k: Float = 1f, size: Float = 1f): Hit {
        val r = Tok.vol_hit / HALF * size
        val cx = Tok.canvasW / HALF
        val ringY = board.vol_ringY
        fun inside(gx: Float, gy: Float, half: Float) = x >= gx - half && x < gx + half && y >= gy - half && y < gy + half
        if (inside(Tok.vol_exitX, board.vol_exitY, Tok.adjustExit / HALF)) return Hit.Exit
        val parts = listOf(
            Triple(Hit.Plus, cx to ringY + (board.vol_plusY - ringY) * k, r),
            Triple(Hit.Minus, cx to ringY + (board.vol_minusY - ringY) * k, r),
            Triple(Hit.Prev, cx - Tok.vol_skipX * k to ringY, r),
            Triple(Hit.Ring, cx to ringY, Tok.vol_ring / HALF * size),
            Triple(Hit.Next, cx + Tok.vol_skipX * k to ringY, r),
        )
        var best = Hit.None
        var nearest = 0f
        for ((hitOf, at, half) in parts) {
            if (!inside(at.first, at.second, half)) continue
            val dx = x - at.first
            val dy = y - at.second
            val d = dx * dx + dy * dy
            if (best == Hit.None || d < nearest) {
                best = hitOf
                nearest = d
            }
        }
        return best
    }

    private fun tap(x1: Float, y1: Float): Boolean {
        val dx = x1 - x0
        val dy = y1 - y0
        return dx * dx + dy * dy <= Tok.touchSlop * Tok.touchSlop
    }

    fun down(now: Long, x: Float, y: Float): Act {
        if (!visible(now) || closing(now) || y >= h - Tok.gesture_bottom) return Act.Pass
        val lk = look(now)
        if (fadeAt >= 0 && now >= fadeAt) revive(now)
        owns = true
        held = true
        heldAt = now
        drops = false
        seeking = false
        fadeAt = -1
        drag = Drag.Tap
        hit = if (atRest) hit(x, y) else hit(x, y, lk.k, lk.size)
        atRest = true
        press = if (hit == Hit.Exit) Hit.None else hit
        pressUp = -1
        pressEnd = Up.Held
        exitLit = hit == Hit.Exit
        if (exitLit) exitPress.down(now.toFloat())
        row = y >= board.vol_ringY - Tok.vol_hit / HALF && y < board.vol_ringY + Tok.vol_hit / HALF
        x0 = x
        y0 = y
        ly = y
        if (press != Hit.None) {
            tilt = true
            upAt = -1
            tiltAt(x, y)
        }
        bump()
        return Act.Draw
    }

    fun move(now: Long, x: Float, y: Float, percent: Int, pos: Pos): Act {
        if (!owns) return Act.None
        val a = slide(x, y, percent, pos)
        if (drag != Drag.Tap && (!tilt || upAt >= 0)) {
            tilt = true
            upAt = -1
        }
        if (tilt) tiltAt(x, y)
        val lit = exitLit
        val pressed = press != Hit.None && pressUp < 0
        exitLit = lit && drag == Drag.Tap
        if (lit && !exitLit) exitPress.cancel(now.toFloat())
        if (pressed && drag != Drag.Tap) {
            pressUp = now
            pressEnd = Up.Cancelled
        }
        bump()
        return if (a == Act.None && (tilt || lit != exitLit || (pressed && pressUp >= 0))) Act.Draw else a
    }

    fun up(now: Long, x: Float, y: Float): Act {
        if (!owns) return Act.None
        val isTap = drag == Drag.Tap && tap(x, y)
        tiltUp(now, isTap)
        holdEnd(now)
        if (press != Hit.None && pressUp < 0) {
            pressUp = now
            pressEnd = if (isTap) Up.Acted else Up.Idle
        }
        owns = false
        held = false
        restFrom(now)
        val d = drag
        drag = Drag.Tap
        bump()
        if (d == Drag.Seek) {
            seeking = false
            return Act.Seek
        }
        if (!isTap) {
            if (exitLit) exitPress.tap(now.toFloat(), false)
            exitLit = false
            return Act.None
        }
        return when (hit) {
            Hit.Plus -> Act.Plus
            Hit.Minus -> Act.Minus
            Hit.Prev -> Act.Prev
            Hit.Ring -> Act.Ring
            Hit.Next -> Act.Next
            else -> {
                exitPress.reset()
                hide(now)
                Act.None
            }
        }
    }

    private fun slide(x: Float, y: Float, percent: Int, pos: Pos): Act {
        val was = ly
        ly = y
        if (drag == Drag.Tap) {
            if (tap(x, y)) return Act.None
            ax = x
            ay = y
            from = percent
            to = percent
            keep = percent
            if (axisOf(x - x0, y - y0) == Axis.Vertical) {
                drag = Drag.Volume
                return Act.None
            }
            drag = if (row && pos.on) Drag.Seek else Drag.Off
            if (drag == Drag.Off) return Act.None
            at = Pos(pos.on, pos.ms.coerceIn(0, pos.dur), pos.dur, pos.track)
            seekTo = at.ms
            seeking = true
            readout = seekText(seekTo, at.dur)
            return Act.Draw
        }
        if (drag == Drag.Volume) {
            if (percent != keep) {
                from = percent
                to = percent
                keep = percent
                ay = was
                return Act.None
            }
            val stride = board.vol_slideStride.toDouble()
            val d = (ay - y).toDouble() * step
            val n = (from + ((d + (if (d < 0) -stride else stride) / HALF) / stride).toInt()).coerceIn(0, PCT)
            if (n == to) return Act.None
            to = n
            return Act.Volume
        }
        if (drag != Drag.Seek) return Act.None
        val span = HALF * Tok.overlayGlyphOffset.toDouble()
        seekTo = (at.ms + ((x - ax).toDouble() * at.dur / span).toLong()).coerceIn(0, at.dur)
        val s = seekText(seekTo, at.dur)
        if (s == readout) return Act.None
        readout = s
        return Act.Draw
    }

    private fun tiltAt(x: Float, y: Float) {
        val p = platePose((x - w / HALF).toDouble(), (y - h / HALF).toDouble(), Tok.vol_tiltReach.toDouble(), Tok.vol_tilt.toDouble())
        rx = p.rx
        ry = p.ry
    }

    fun tiltK(now: Long): Float {
        if (!tilt) return 0f
        if (upAt < 0) return 1f
        val start = if (upFrom >= 0) upFrom else upAt
        val k = 1f - progress(now - start - tiltHold(), Tok.motion_volumeTiltReturn.toLong())
        return if (tiltTap) k * Tok.motion_volumeTiltTapLevel else k
    }

    fun tiltFrame(now: Long) {
        if (tilt && upAt >= 0 && upFrom < 0) upFrom = max(upAt, now - tiltHold())
    }

    fun pose(now: Long): TiltPose {
        val q = tiltK(now).toDouble()
        return TiltPose(rx * q, ry * q)
    }

    fun plate(): TiltPose = TiltPose(rx, ry)

    fun pressed(): Hit = press

    fun pressK(now: Long): Float {
        if (press == Hit.None) return 0f
        if (pressUp < 0) return 1f
        return when (pressEnd) {
            Up.Acted -> 0f
            Up.Idle -> if (now < pressEnd()) 1f else 0f
            Up.Cancelled -> 1f - progress(now - pressUp, Tok.motion_volumePressOff.toLong())
            Up.Held -> 1f
        }
    }

    private fun pressEnd(): Long {
        if (press == Hit.None || pressUp < 0 || pressEnd == Up.Acted) return -1
        return pressUp + (if (pressEnd == Up.Idle) Tok.motion_touchReturnDelay else Tok.motion_volumePressOff).toLong()
    }

    fun moving(now: Long): Boolean {
        if (!shown) return false
        val out = Tok.motion_volumeRestSlow.toLong() + max(Tok.motion_volumeRestFast.toLong(), Tok.motion_volumeScrimOut.toLong())
        val back = Tok.motion_volumeNumberBack.toLong()
        val legs = (closeAt >= 0 && now < closeAt + closeMs()) ||
            (fadeAt >= 0 && now >= fadeAt && now < fadeAt + out) ||
            (!held && now >= numberAt() && now < numberAt() + Tok.motion_volumeNumberFade.toLong()) ||
            (chgAt >= 0 && numFrom < 1f && now < chgAt + back) ||
            (backAt >= 0 && now < backAt + back) || (pressEnd == Up.Cancelled && now < pressEnd())
        return now < endAt() || legs || exitPress.moving(now.toFloat()) || (crossAt >= 0 && now < crossAt + crossMs()) ||
            (tilt && upAt >= 0 && tiltK(now) > 0f)
    }

    override fun wakeAt(now: Long): Long? {
        val r = if (tilt && upAt >= 0) upAt + tiltHold() else -1
        val fast = if (fadeAt >= 0) fadeAt + Tok.motion_volumeRestSlow.toLong() else -1
        val gone = if (fadeAt >= 0) fast + Tok.motion_volumeScrimOut.toLong() else -1
        val cross = if (crossAt >= 0) crossAt + crossMs() else -1
        val exitDue = exitPress.due(now.toFloat())?.toLong() ?: -1
        var w: Long? = null
        for (c in listOf(r, endAt(), if (shown && !held) numberAt() else -1, fadeAt, fast, gone, cross, pressEnd(), exitDue)) {
            if (c > now && (w == null || c < w)) w = c
        }
        return w
    }

    companion object {
        fun closeMs(): Long =
            max(Tok.motion_volumeTouchClose.toLong(), Tok.motion_volumeTouchScrim.toLong() - Tok.motion_volumeTouchScrimLead.toLong())

        fun crossMs(): Long = maxOf(
            Tok.motion_volumeTextOutFade.toLong(),
            Tok.motion_volumeTextOut.toLong(),
            Tok.motion_volumeTextInAt.toLong() + max(Tok.motion_volumeTextIn.toLong(), Tok.motion_volumeTextInFade.toLong()),
        )

        fun numberText(percent: Int): String = (if (percent < PAD) "0" else "") + percent

        fun seekText(t: Long, dur: Long): String {
            val s = max(0L, t / MS_S)
            return if (dur >= HOUR_MS) {
                String.format(Locale.ROOT, "%d:%02d:%02d", s / HOUR_S, s / MIN_S % MIN_S, s % MIN_S)
            } else {
                String.format(Locale.ROOT, "%d:%02d", s / MIN_S, s % MIN_S)
            }
        }
    }
}
