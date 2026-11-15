package net.seilent.funekit.kit.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.GlowText
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.LitRow
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.Leg
import net.seilent.funekit.kit.motion.Press
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penFloor
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.pressGlow
import net.seilent.funekit.kit.ring
import net.seilent.funekit.kit.ringPress
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private const val HALF = 2
private const val GROW = 3

const val WATERMARK = "fune"

interface HomeEntry {
    val word: String
    val sub: String
    val ring: Boolean get() = false
}

class HomeInk(val s: Scale, val keys: List<HomeEntry>, val names: List<Word>, val subs: List<Word>, val watermark: Word) {
    private val x64 = pen64(Tok.homeTextX, s.stdPpu)
    private val itemPx = rolePx(Roles.homeItem, s, s.stdPpu)
    private val bases = keys.indices.map { px(homeBaseline(it), s.stdPpu) }
    private val rects = keys.indices.map { itemRect(this, it, x64, bases[it], s) }
    private val glowNames = keys.indices.map { listOf(GlowText(names[it], x64, bases[it], itemPx)) }

    fun rect(i: Int): Edges = rects[i]

    fun glowText(i: Int): List<GlowText> = glowNames[i]
}

fun homeInk(m: TextMeasurer, fonts: FontFamily.Resolver, s: Scale, density: Density, keys: List<HomeEntry>, subOf: (HomeEntry) -> String = { it.sub }): HomeInk {
    val sp = s.stdPpu
    val item = roleStyle(Roles.homeItem, s, density, Ink.white, ppu = sp)
    val sub = roleStyle(Roles.homeSubtitle, s, density, Ink.white, ppu = sp)
    return HomeInk(
        s,
        keys,
        keys.map { m.word(it.word, Roles.homeItem, item, fonts, density, null) },
        keys.map { m.word(subOf(it), Roles.homeSubtitle, sub, fonts, density, null) },
        m.word(WATERMARK, Roles.watermark, roleStyle(Roles.watermark, s, density, Ink.white, ppu = sp), fonts, density, null),
    )
}

fun homeBaseline(i: Int): Float = Tok.homeFirstBaseline + i * Tok.homePitch

class HomeGeo(val s: Scale, val count: Int) {
    private val sp = s.stdPpu
    val view: Float = s.board.homeWatermarkBaseline - Tok.homeWatermarkGap - Tok.homeFirstBaseline + Tok.homePitch
    val limit: Float = (count * Tok.homePitch - view).let { if (it < 1f) 0f else it }
    val contentPx: Int = s.heightPx + px(limit, sp)
    val watermarkBaseline: Float = max(s.board.homeWatermarkBaseline, baseline(count - 1) + Tok.homeWatermarkGap)

    fun baseline(i: Int): Float = homeBaseline(i)

    fun ringHit(): Edges = Edges(0, 0, px(Tok.homeBarX, sp), px(Tok.homeRingHitBottom, sp))

    fun itemHit(i: Int): Edges {
        val top = baseline(i) - Tok.homeBarTop
        return Edges(px(Tok.homeBarX, sp), px(top, sp), s.widthPx, px(top + Tok.homePitch, sp))
    }

    fun offU(offPx: Int): Float = offPx / sp

    fun want(item: Int, off: Float): Float {
        val top = item * Tok.homePitch
        return when {
            top < off -> top
            top + Tok.homePitch > off + view -> top + Tok.homePitch - view
            else -> off
        }
    }
}

class HomeMenu(val count: Int) {
    val focus = FocusTrack()
    val items = PressSlots()
    val ring = Press()
    val lit = LitRow()
    val glow = Glow()
    private val ends = LongArray(count)
    private var litUntil = 0L
    private var enterAt: Long? by mutableStateOf(null)
    var aim = 0f
        private set
    var aims by mutableIntStateOf(0)
        private set
    var aiming = false
        private set
    private var restOff by mutableFloatStateOf(0f)
    private var restPx by mutableIntStateOf(-1)

    fun enter(now: Long) {
        enterAt = now
    }

    fun enterAt(i: Int, now: Long): Float {
        val t = enterAt ?: return 1f
        return Leg(0f, 1f, (i * Tok.motion_homeEnterStagger), Tok.motion_homeEnter, Curves.decel).at((now - t).toFloat())
    }

    fun peek(now: Long): Float {
        val t = enterAt ?: return 1f
        return Leg(0f, 1f, 0f, Tok.motion_homeEnterPeek, Curves.decel).at((now - t).toFloat())
    }

    private fun entering(now: Long): Boolean {
        val t = enterAt ?: return false
        return now - t < max((count - 1) * Tok.motion_homeEnterStagger + Tok.motion_homeEnter, Tok.motion_homeEnterPeek)
    }

    fun move(to: Int, now: Long, animate: Boolean) {
        val from = focus.row
        if (to == from || to !in 0 until count) return
        val end = now + if (animate) Tok.motion_focusAlpha.toLong() else 0L
        ends[from] = end
        ends[to] = end
        focus.set(to, count, now, animate)
    }

    fun down(i: Int, now: Long) {
        lit.up()
        glow.clear()
        move(i, now, true)
        items.down(i, now)
        lit.down(i, ends[i])
        glow.down(i, now)
    }

    fun ringDown(now: Long) {
        lit.up()
        glow.clear()
        ring.down(now.toFloat())
    }

    fun land() {
        enterAt = null
        items.reset()
        ring.reset()
        lit.drop()
        litUntil = 0L
        glow.clear()
    }

    fun cancel(now: Long) {
        ring.cancel(now.toFloat())
        items.cancel(now)
        lit.up()
        glow.clear()
    }

    fun tap(now: Long) {
        lit.up()
        litUntil = max(litUntil, ends[focus.row])
        ring.tap(now.toFloat())
        items.tap(now)
    }

    fun into(off: Float, geo: HomeGeo) {
        val w = geo.want(focus.row, off)
        if (w == off) return
        aim = w
        aiming = true
        aims++
    }

    fun stopAim() {
        aiming = false
    }

    fun landAim(): Float? {
        if (!aiming) return null
        aiming = false
        return aim
    }

    fun rest(off: Float, px: Int) {
        restOff = off
        restPx = px
    }

    fun moved(px: Int) {
        if (px != restPx) restPx = -1
    }

    fun off(px: Int, geo: HomeGeo): Float = if (px == restPx) restOff else geo.offU(px)

    fun shift(geo: HomeGeo, px: () -> Int): Float = if (restPx < 0 || px() != restPx) 0f else restOff - geo.offU(restPx)

    fun afterScroll(off: Float, geo: HomeGeo, now: Long) {
        val top = focus.row * Tok.homePitch
        if (top >= off && top + Tok.homePitch <= off + geo.view) return
        val first = ceil(off / Tok.homePitch).toInt()
        val last = max(first, floor((off + geo.view) / Tok.homePitch).toInt() - 1)
        move((if (top < off) first else last).coerceIn(0, count - 1), now, false)
    }

    fun tick(now: Long) {
        val k = glow.key
        if (k != null && !lit.on(k, now)) glow.clear()
    }

    fun animating(now: Long): Boolean =
        entering(now) || focus.animating(now) || items.stale(now) || ring.stale(now.toFloat()) || glow.moving(now)

    fun wakeAt(now: Long): Long? = listOfNotNull(items.due(now), ring.due(now.toFloat())?.let { ceil(it).toLong() }, litUntil.takeIf { it > now }).minOrNull()

    fun ringAlpha(lit: Boolean, now: Long): Float = (if (lit || ring.held) Tok.alpha_full else Tok.alpha_mid) * peek(now)

    fun restAt(now: Long, ringLit: Boolean): HomeRest {
        focus.revision
        items.revision
        ring.revision
        lit.revision
        val g = glow.key
        val out = (0 until count).map { i ->
            val e = enterAt(i, now)
            val f = focus.rowAlpha(i, now, Roles.homeItem.alpha, Tok.type_homeItem_alphaDim)
            val a = if (lit.on(i, now)) Tok.alpha_full else f
            HomeItemRest(
                e,
                e * a,
                f,
                e * Roles.homeSubtitle.alpha,
                1f,
                if (g == i) items.lit(i, now) else null,
            )
        }
        return HomeRest(out, ringAlpha(ringLit, now), ring.lit(now.toFloat()))
    }
}

data class HomeItemRest(val enter: Float, val alpha: Float, val focus: Float, val sub: Float, val zoom: Float, val glow: Float?)

data class HomeRest(val items: List<HomeItemRest>, val ring: Float, val ringLit: Float)

class HomeLook(val items: Float, val sel: Int, val selAlpha: Float, val sub: Float, val ring: Float = items, val mark: Float = items)

fun homeGlowReach(s: Scale): Int = px(s.board.homeGlowDilate, s.stdPpu) + px(s.board.homeGlow, s.stdPpu)

fun homeRingHaloReach(ppu: Float): Int = px(Tok.homeRingHalo, ppu) + px(Tok.homeRingHaloSpread, ppu)

fun homeRingRect(s: Scale, off: Float, halo: Boolean = true): Edges {
    val sp = s.stdPpu
    val cx = px(Tok.homeRingX, sp)
    val cy = px(Tok.homeRingY - off, sp)
    val r = px(Tok.homeRingSize, sp) / HALF + 1 + if (halo) homeRingHaloReach(sp) else 0
    return Edges(cx - r, cy - r, cx + r, cy + r)
}

private fun itemRect(ink: HomeInk, i: Int, x64: Int, base: Int, s: Scale): Edges {
    val sp = s.stdPpu
    val ip = rolePx(Roles.homeItem, s, sp)
    val subPx = rolePx(Roles.homeSubtitle, s, sp)
    val subBase = base + px(Tok.homeSubtitleDrop + Tok.capHeight * Roles.homeSubtitle.size, sp)
    val x = penFloor(x64)
    val reach = homeGlowReach(s)
    val w = penFloor(maxOf(pen64(ink.names[i].width) + pen64(reach.toFloat()), pen64(Tok.homeSubtitleX, sp) + pen64(ink.subs[i].width)))
    return Edges(x - GROW, base - Metrics.ascent(ip) - reach - GROW, x + w + GROW, subBase + Metrics.descent(subPx) + GROW)
}

fun DrawScope.homeMenu(ink: HomeInk, m: HomeMenu, geo: HomeGeo, rest: HomeRest, look: HomeLook?, off: Float = 0f, rec: DrawRecord? = null) {
    val s = geo.s
    val sp = s.stdPpu
    val markA = look?.mark ?: 1f
    val ringA = look?.ring ?: 1f
    val x0 = pen64(Tok.homeTextX, sp)
    val wb = px(geo.watermarkBaseline - off, sp).toFloat()
    val wa = Roles.watermark.alpha * markA
    word(ink.watermark, penPx(x0), wb, Ink.white, wa)
    rec?.word("home.watermark", ink.watermark, penPx(x0), wb, wa)
    val c = Offset(px(Tok.homeRingX, sp).toFloat(), px(Tok.homeRingY - off, sp).toFloat())
    val d = px(Tok.homeRingSize, sp).toFloat()
    val ra = rest.ring * ringA
    val stroke = px(Tok.homeRingStroke, sp).toFloat()
    if (look == null) ringPress(d.toInt(), stroke.toInt(), c.x.toInt(), c.y.toInt(), rest.ringLit, ra, px(Tok.homeRingHalo, sp), px(Tok.homeRingHaloSpread, sp), Tok.homeRingHaloAlpha)
    ring(c, d, stroke, Ink.white, ra)
    glyph(Glyphs.play, c, (d.toInt() / HALF).toFloat(), Ink.white, ra)
    rec?.ring("home.ring", c, d, ra)
    rec?.glyphs("home.ring.glyph", listOf(Glyphs.play), c, (d.toInt() / HALF).toFloat(), ra, d, stroke)
    val drop = px(Tok.homeSubtitleDrop + Tok.capHeight * Roles.homeSubtitle.size, sp)
    val ip = rolePx(Roles.homeItem, s, sp)
    for (i in ink.keys.indices) {
        val item = rest.items[i]
        val e = if (look == null) item.enter else 1f
        val x64 = x0 + pen64(px(Tok.motion_homeEnterShift * (1 - e), sp).toFloat())
        val base = px(geo.baseline(i) - off, sp)
        val a: Float
        val sa: Float
        if (look == null) {
            a = item.alpha
            sa = item.sub
        } else {
            a = item.focus * if (i == look.sel) look.selAlpha else look.items
            sa = if (look.sel < 0 || i == look.sel) look.sub else Roles.homeSubtitle.alpha * look.items
        }
        val still = x64 == x0 && off == 0f
        val r = if (still) ink.rect(i) else itemRect(ink, i, x64, base, s)
        val w = ink.names[i]
        run {
            val g = item.glow
            if (look == null && g != null) m.glow.tune(s.board.homeGlowDilate, s.board.homeGlow, s.board.homeGlowAlpha)
            if (look == null && g != null) pressGlow(m.glow, if (still) ink.glowText(i) else listOf(GlowText(w, x64, base, ip)), r, sp, g)
            word(w, penPx(x64), base.toFloat(), Ink.white, a)
            rec?.word("home.item$i", w, penPx(x64), base.toFloat(), a)
            val sx = penPx(x64 + pen64(Tok.homeSubtitleX, sp))
            word(ink.subs[i], sx, (base + drop).toFloat(), Ink.white, sa)
            rec?.word("home.sub$i", ink.subs[i], sx, (base + drop).toFloat(), sa)
        }
    }
}

