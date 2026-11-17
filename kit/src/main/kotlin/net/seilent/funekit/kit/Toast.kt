package net.seilent.funekit.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.delay
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Press
import net.seilent.funekit.kit.motion.lerp
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Tap
import net.seilent.funekit.kit.touch.tapTarget
import net.seilent.funekit.px
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val BISECT = 17
private const val HALF = 2
private const val SWAP_LEGS = 2

enum class ToastPlace { Top, Bottom }

fun toastPlace(over: OverKind?, closing: Boolean, nowPlaying: Boolean, holdUp: Boolean = false): ToastPlace {
    val up = (over == OverKind.Sheet || (over == OverKind.Dlg && holdUp)) && !closing
    return if (up || nowPlaying) ToastPlace.Top else ToastPlace.Bottom
}

fun toastOver(over: OverKind?, holdUp: Boolean = false): Boolean = when (over) {
    null, OverKind.Sheet -> true
    OverKind.Dlg -> holdUp
    OverKind.Jump, OverKind.Volume -> false
}

fun toastSurface(s: Scale, navHere: Boolean): Int = if (navHere) navBand(s).y0 else s.heightPx

fun expOut6Inverse(e: Float): Float {
    var lo = 0f
    var hi = 1f
    repeat(BISECT) {
        val mid = (lo + hi) / HALF
        if (Curves.expOut6.at(mid) < e) lo = mid else hi = mid
    }
    return hi
}

class ToastLook(val y: Int, val h: Int, val stripBottom: Int, val scrim: Float, val clip: Edges, val alpha: Float, val lit: Float)

class Toast {
    private enum class Phase { Hidden, In, Hold, Out }

    private var phase0 = Phase.Hidden
    private var inAt = 0L
    private var inFrom = 0f
    private var fadeFrom = 0f
    private var holdAt = 0L
    private var swapAt: Long? = null
    var artist by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set
    private var nextArtist = ""
    private var nextTitle = ""
    private var track = false
    private var nextTrack = false
    private var target = false
    var placed by mutableStateOf(false)
        private set
    var place by mutableStateOf(ToastPlace.Top)
        private set
    private var drawnE = -1f
    private var drawnF = -1f
    private var drawnTa = -1f
    var shows by mutableIntStateOf(0)
        private set
    private var bottomPx = 0
    val press = Press()
    val glow = Glow()

    val bottom: Boolean get() = placed && place == ToastPlace.Bottom

    val isTrack: Boolean get() = if (swapAt != null) nextTrack else track

    private fun inMs(): Long = Tok.motion_toastRise.toLong()

    private fun outMs(): Long = max(Tok.motion_toastOutAt + Tok.motion_toastOut, Tok.motion_toastOutMoveAt + Tok.motion_toastOutMove).toLong()

    private fun progress(e: Long, d: Long): Float = if (d <= 0) 1f else (e.toFloat() / d).coerceIn(0f, 1f)

    fun show(now: Long, artist: String, title: String, track: Boolean = false) {
        shows++
        val p = phase(now)
        if (p == Phase.In || p == Phase.Hold) {
            nextArtist = artist
            nextTitle = title
            nextTrack = track
            if (!track) {
                press.drop()
                target = false
            }
            swapAt = now
            if (p == Phase.Hold) holdAt = now
            return
        }
        fadeFrom = if (p == Phase.Out) fade(now) else 0f
        inFrom = if (p == Phase.Out) pos(now) else 0f
        inAt = now
        placed = p == Phase.Out
        holdAt = now + (inMs() * (1 - inFrom)).toLong()
        swapAt = null
        this.artist = artist
        this.title = title
        this.track = track
        reset()
        phase0 = Phase.In
    }

    fun frame(p: ToastPlace, surfacePx: Int, now: Long) {
        bottomPx = surfacePx
        if (!placed && phase(now) != Phase.Hidden) {
            place = p
            placed = true
            inAt = now
            holdAt = now + (inMs() * (1 - inFrom)).toLong()
        }
        val at = swapAt
        val d = Tok.motion_valueChange.toLong()
        if (at != null && now >= at + d) {
            artist = nextArtist
            title = nextTitle
            track = nextTrack
        }
        drawnTa = textAlpha(now)
        if (at != null && now >= at + SWAP_LEGS * d) swapAt = null
        drawnE = pos(now)
        drawnF = fade(now)
    }

    fun opened(over: OverKind, nowPlaying: Boolean, now: Long, holdUp: Boolean = false) {
        if (toastPlace(over, false, nowPlaying, holdUp) == ToastPlace.Top) toTop(now)
    }

    fun toTop(now: Long) {
        if (!bottom) return
        if (phase(now) == Phase.In) {
            inFrom = min(1f, inFrom + progress(now - inAt, inMs()))
            inAt = now
            place = ToastPlace.Top
            holdAt = now + (inMs() * (1 - inFrom)).toLong()
            return
        }
        place = ToastPlace.Top
    }

    fun hide() {
        if (phase0 == Phase.Hidden) return
        phase0 = Phase.Hidden
        reset()
        shows++
    }

    fun visible(now: Long): Boolean = phase(now) != Phase.Hidden

    fun moving(now: Long): Boolean {
        val p = phase(now)
        val swap = swapAt?.let { now < it + SWAP_LEGS * Tok.motion_valueChange.toLong() } ?: false
        return p == Phase.In || p == Phase.Out || swap ||
            (p != Phase.Hidden && (press.stale(now.toFloat()) || pos(now) != drawnE || fade(now) != drawnF || textAlpha(now) != drawnTa))
    }

    fun wakeAt(now: Long): Long? {
        val w = if (phase(now) == Phase.Hold) holdAt + Tok.motion_toastHold.toLong() else null
        val d = press.due(now.toFloat())?.let { ceil(it).toLong() }
        return listOfNotNull(w, d).minOrNull()
    }

    fun down(now: Long) {
        press.down(now.toFloat())
        target = true
    }

    fun tap(now: Long): Boolean {
        press.tap(now.toFloat())
        val t = target
        target = false
        return t
    }

    fun cancel(now: Long) {
        press.cancel(now.toFloat())
        target = false
    }

    private fun reset() {
        press.reset()
        target = false
    }

    private fun phase(now: Long): Phase {
        if (phase0 == Phase.Hidden) return Phase.Hidden
        val hold = Tok.motion_toastHold.toLong()
        val out = outMs()
        if (!placed) return if (now < inAt + inMs() + hold + out) Phase.In else Phase.Hidden
        val end = holdAt + hold
        return when {
            now < holdAt -> Phase.In
            now < end -> Phase.Hold
            now < end + out -> Phase.Out
            else -> Phase.Hidden
        }
    }

    fun pos(now: Long): Float = when (phase(now)) {
        Phase.In -> if (!placed) 0f else min(1f, inFrom + progress(now - inAt, inMs()))
        Phase.Hold -> 1f
        Phase.Out -> 1 - progress(now - holdAt - Tok.motion_toastHold.toLong() - Tok.motion_toastOutMoveAt.toLong(), Tok.motion_toastOutMove.toLong())
        Phase.Hidden -> 0f
    }

    fun fade(now: Long): Float {
        val p = phase(now)
        if (p == Phase.In) return if (inFrom >= 1f) 1f else fadeFrom + (1 - fadeFrom) * (pos(now) - inFrom) / (1 - inFrom)
        if (p != Phase.Out) return pos(now)
        val t = now - holdAt - Tok.motion_toastHold.toLong() - Tok.motion_toastOutAt.toLong()
        return 1 - Curves.easeOut.at(progress(t, Tok.motion_toastOut.toLong()))
    }

    fun textAlpha(now: Long): Float {
        val at = swapAt ?: return 1f
        val e = now - at
        val d = Tok.motion_valueChange.toLong()
        if (e < d) return lerp(1f, Tok.motion_valueDip, progress(e, d))
        return lerp(Tok.motion_valueDip, 1f, progress(e - d, d))
    }

    fun stripY(s: Scale, e: Float): Int {
        if (bottom) return bottomPx - px(Tok.toastHeight, s.ppu) + (Tok.motion_toastTravel * (1 - e) * s.ppu).roundToInt()
        return (-Tok.motion_toastTravel * (1 - e) * s.ppu).roundToInt()
    }

    fun rect(s: Scale): Edges =
        if (bottom) Edges(0, bottomPx - px(Tok.toastHeight, s.ppu), s.widthPx, bottomPx) else Edges(0, 0, s.widthPx, px(Tok.toastHeight, s.ppu))

    fun hitRect(s: Scale, now: Long): Edges? {
        val e = pos(now)
        if (e <= 0f || !isTrack) return null
        val h = px(Tok.toastHeight, s.ppu)
        val travel = (Tok.motion_toastTravel * (1 - e) * s.ppu).roundToInt()
        return if (bottom) Edges(0, bottomPx - h + travel, s.widthPx, bottomPx) else Edges(0, -travel, s.widthPx, h - travel)
    }

    fun look(s: Scale, now: Long): ToastLook? {
        val e = pos(now)
        val f = fade(now)
        if (e <= 0f || f <= 0f) return null
        val y = stripY(s, e)
        val h = px(Tok.toastHeight, s.ppu)
        val bottomEdge = if (bottom) min(y + h, bottomPx) else y + h
        val clip = Edges(px(Tok.edge, s.ppu), y, px(s.w - Tok.edge, s.ppu), bottomEdge)
        return ToastLook(y, h, bottomEdge, f * Tok.scrim_overlay_alpha, clip, f * textAlpha(now), press.look(now.toFloat()))
    }
}

class ToastInk(val artist: Word, val title: Word)

fun DrawScope.toast(t: Toast, ink: ToastInk, s: Scale, now: Long) {
    val l = t.look(s, now) ?: return
    drawRect(Ink.black, Offset(0f, l.y.toFloat()), Size(size.width, (l.stripBottom - l.y).toFloat()), l.scrim)
    val c = l.clip
    clipRect(c.x0.toFloat(), c.y0.toFloat(), c.x1.toFloat(), c.y1.toFloat()) {
        val x64 = pen64(Tok.edge, s.ppu)
        val ab = l.y + px(Tok.toastArtistBaseline, s.ppu)
        val tb = l.y + px(Tok.toastTitleBaseline, s.ppu)
        t.glow.follow(0, l.lit * l.alpha, l.lit > 0f)?.let { g ->
            pressGlow(g, listOf(GlowText(ink.artist, x64, ab, rolePx(Roles.toastArtist, s)), GlowText(ink.title, x64, tb, rolePx(Roles.toastTitle, s))), c, s.ppu, g.level(now))
        }
        word(ink.artist, penPx(x64), ab.toFloat(), Ink.white, l.alpha * Roles.toastArtist.alpha)
        word(ink.title, penPx(x64), tb.toFloat(), Ink.white, l.alpha * Roles.toastTitle.alpha)
    }
}

@Composable
fun ToastStrip(
    t: Toast,
    s: Scale,
    now: () -> Long,
    place: () -> ToastPlace,
    surfacePx: Int,
    modifier: Modifier = Modifier,
    a: Arbiter? = null,
    layer: Int = 0,
    onTap: () -> Unit = {},
) {
    val m = rememberTextMeasurer()
    val fonts = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val covers = rememberCovers()
    val ink = remember(t.artist, t.title, s, density, fonts, covers) {
        fun style(r: Role) = roleStyle(r, s, density, Ink.white)
        ToastInk(m.word(t.artist, Roles.toastArtist, style(Roles.toastArtist), fonts, density, covers), m.word(t.title, Roles.toastTitle, style(Roles.toastTitle), fonts, density, covers))
    }
    var frameAt by remember { mutableLongStateOf(-1L) }
    var hit: Edges? by remember { mutableStateOf(null) }
    var pokes by remember { mutableIntStateOf(0) }
    LaunchedEffect(t, t.shows, pokes) {
        while (true) {
            withFrameMillis {
                frameAt = it
                t.frame(place(), surfacePx, it)
                hit = t.hitRect(s, it)
            }
            if (!t.visible(frameAt)) break
            if (!t.moving(frameAt)) {
                val w = t.wakeAt(frameAt) ?: break
                delay(w - frameAt)
            }
        }
        hit = null
    }
    Box(modifier.pxSize(s.widthPx, s.heightPx)) {
        Canvas(Modifier.pxSize(s.widthPx, s.heightPx)) {
            val at = frameAt
            if (at >= 0) toast(t, ink, s, at)
        }
        val box = hit
        if (a != null && box != null) {
            val tap = Tap(
                onPress = {
                    t.down(now())
                    pokes++
                },
                onCancel = {
                    t.cancel(now())
                    pokes++
                },
                onTap = {
                    pokes++
                    if (t.tap(now())) onTap()
                },
            )
            Box(Modifier.pxAt(box).tapTarget(a, tap, layer))
        }
    }
}
