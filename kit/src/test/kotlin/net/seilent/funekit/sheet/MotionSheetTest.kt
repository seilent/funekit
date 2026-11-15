package net.seilent.funekit.sheet

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.core.Section
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.Glow
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.Layout
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.MenuInk
import net.seilent.funekit.kit.MenuPlan
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.RowInk
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastInk
import net.seilent.funekit.kit.ToastPlace
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.glyph
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.jumpGrid
import net.seilent.funekit.kit.listThumbOf
import net.seilent.funekit.kit.menuInk
import net.seilent.funekit.kit.menuLayer
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Press
import net.seilent.funekit.kit.motion.pivotAt
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.ring
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.rowInk
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PagePaint
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.WordSide
import net.seilent.funekit.kit.stack.layer
import net.seilent.funekit.kit.stack.shifted
import net.seilent.funekit.kit.thumb
import net.seilent.funekit.kit.toast
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.roundToInt
import net.seilent.funekit.kit.Ink as KitInk

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MotionSheetTest {
    private val render = Render(File("build/sheets"))
    private val root = File("build/sheets/$FOLDER")
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val covers = Covers(RuntimeEnvironment.getApplication().resources)
    private val dev = Dev("720x1280", "funeOS A105 board", 720, 1280, listOf(Nav.None))
    private val board = "a105"
    private val s = scale(dev.window(Nav.None), Nav.None, Surface.Page, false)
    private val w = dev.w
    private val h = dev.h
    private val accent = Accent.forHue(Accent.defaultHue)
    private val forms = MoveForms.board(TokBoard.a105)
    private val bands = groundBands(h, false)
    private val hpx = rolePx(Roles.pageHeader, s, s.stdPpu)
    private val headerBase = px(Tok.page_headerBaseline, s.stdPpu).toFloat()

    private val artists = KitPages.artists
    private val artistsKnob = KitPages.artists.at(KNOB_ROW, -1)
    private val bt = KitPages.bt.at(-1, -1)
    private val songsRest = KitPages.songs.at(-1, -1)
    private val songsEnter = KitPages.songs.at(0, -1)
    private val songsPress = KitPages.songs
    private val songsPlaying = KitPages.songs.playing(KitPages.songs.pressed).at(KitPages.songs.focused, KitPages.songs.pressed)
    private val np = Page("np", "now playing", "", emptyList(), -1, emptyList(), -1, -1)

    private fun style(r: Role, ppu: Float = s.stdPpu) = roleStyle(r, s, unitDensity, KitInk.white, ppu = ppu)

    private fun measure(t: String, r: Role, ppu: Float = s.stdPpu) = measurer.word(t, r, style(r, ppu), fonts, unitDensity, null)

    private class PageInk(val chrome: Chrome, val rows: List<RowInk>)

    private val inks = HashMap<Page, PageInk>()

    private fun inkOf(p: Page) = inks.getOrPut(p) {
        PageInk(
            chromeOf(measurer, fonts, s, unitDensity, p.header, p.pivots, covers).also { it.select(max(p.active, 0)) },
            p.rows.map { rowInk(measurer, fonts, it, s, unitDensity, false, null) },
        )
    }

    private val homeWords = Stubs.homeItems.map { measure(it, Roles.homeItem) }
    private val watermark = measure(WATERMARK, Roles.watermark)
    private val npWords = listOf(
        measure("artist", Roles.npArtist, s.ppu), measure("album", Roles.npAlbum, s.ppu), measure("title", Roles.npTitle, s.ppu), measure("1:23", Roles.time, s.ppu),
    )
    private val npNext = (1..s.board.npNextCount.toInt()).map { measure("next $it", Roles.npNext, s.ppu) }
    private val layerPaint = Paint()

    private fun kindOf(p: Page): PageKind = if (p.pivots === KitPages.hubPivots) PageKind.Hub else PageKind.Plain

    private class Side(val text: String, val role: Role, val look: Float, val x: Float, val y: Float, val pivot: Int, val header: Boolean)

    private fun side(p: Page, pivot: Boolean): Side? {
        val c = inkOf(p).chrome
        if (pivot && p.active in p.pivots.indices) {
            return Side(p.pivots[p.active], Roles.pivotActive, Roles.pivotActive.alpha, c.pivotX(p.active), px(s.board.page_pivotBaseline, s.ppu).toFloat(), p.active, false)
        }
        if (p.header.isEmpty()) return null
        return Side(p.header, Roles.pageHeader, c.headerAlpha, c.headerRestX(), headerBase, -1, true)
    }

    private fun sidePx(d: Side) = rolePx(d.role, s, if (d.header) s.stdPpu else s.ppu)

    private fun atHeader(d: Side): Word {
        if (d.header) return measure(d.text, d.role)
        val r = d.role
        return measure(d.text, Role(Roles.pageHeader.size, r.weight, r.case, r.alpha, r.tracking * hpx / sidePx(d)))
    }

    private fun wordSide(d: Side?): WordSide? = d?.let { WordSide(it.text, sidePx(it).toFloat() / hpx, it.look, it.x, it.y, it.pivot, it.header, atHeader(it)) }

    private val kits = HashMap<Page, KitPagePaint>()

    private fun kit(p: Page): KitPagePaint = kits.getOrPut(p) {
        val ink = inkOf(p)
        val pivot = if (p.active in p.pivots.indices) wordSide(side(p, true)) else null
        KitPagePaint(
            p, s, w, h, ink.chrome, ink.rows, if (p.header.isEmpty()) null else measure(p.header, Roles.pageHeader), kindOf(p),
            Stubs.homeItems.indexOf(p.header), pivot, wordSide(side(p, false)), bands, accent,
        )
    }

    private val npPaint = object : PagePaint {
        override val chrome: Chrome? = null
        override val header: Word? = null
        override val item = -1
        override val grounded = false
        override val kind = PageKind.Plain
        override val top = 0
        override val bottom = h
        override val count = 0

        override fun rowY(i: Int) = 0

        override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

        override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) = Unit

        override fun DrawScope.rest(now: Long, rec: DrawRecord?, name: String) = npPage()
    }

    private fun paintOf(p: Page): PagePaint = if (p === np) npPaint else kit(p)

    private fun sideOf(p: Page?): PageSide? = p?.let { PageSide(s, paintOf(it)) }

    private val stubHome = object : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = homeAt(look.items, look.sel, look.selAlpha, rec)
    }

    private val homeSide = HomeSide(s, stubHome, 0f)

    private val paints = IdentityHashMap<PageStack<Page>, MovePaint>()

    private fun stack(vararg pages: Page): PageStack<Page> {
        val move = PageMove()
        val mp = MovePaint(move)
        return PageStack<Page>(forms, this::kindOf, move = move, ends = { t -> mp.ends(t, homeSide, sideOf(t.from), sideOf(t.to), forms) }).also {
            it.pages.addAll(pages)
            paints[it] = mp
        }
    }

    private fun DrawScope.listThumb(p: Page, rec: Regions?, name: String, fade: Float) {
        if (fade <= 0f || p.rows.isEmpty()) return
        val r = listThumbOf(s, px(s.board.page_contentTop, s.ppu), px(s.stdContentBottom, s.stdPpu), p.listRows, Rows.pitch(s, p.rows.first()), 0L) ?: return
        thumb(r, fade)
        rec?.thumbParts(name, r, Tok.scrollbar_alpha * fade, bands, accent)
    }

    private fun DrawScope.chromeAt(c: Chrome, now: Long, rec: Regions?, name: String) {
        chrome(c, now)
        rec?.chrome(name, c, now, w)
        c.clearParts()
    }

    private fun DrawScope.homeAt(ha: Float, sel: Int, selAlpha: Float, rec: DrawRecord?) {
        val sp = s.stdPpu
        val x = penPx(pen64(Tok.homeTextX, sp))
        val last = Stubs.homeItems.lastIndex
        val wb = px(max(s.board.homeWatermarkBaseline, Tok.homeFirstBaseline + last * Tok.homePitch + Tok.homeWatermarkGap), sp).toFloat()
        val wa = Roles.watermark.alpha * ha
        word(watermark, x, wb, KitInk.white, wa)
        rec?.word("home.watermark", watermark, x, wb, wa)
        val c = Offset(px(Tok.homeRingX, sp).toFloat(), px(Tok.homeRingY, sp).toFloat())
        val d = px(Tok.homeRingSize, sp)
        val stroke = px(Tok.homeRingStroke, sp).toFloat()
        val ra = Tok.alpha_mid * ha
        val slot = (d / 2).toFloat()
        ring(c, d.toFloat(), stroke, KitInk.white, ra)
        glyph(Glyphs.play, c, slot, KitInk.white, ra)
        rec?.ring("home.ring", c, d.toFloat(), ra)
        rec?.glyphs("home.ring.glyph", listOf(Glyphs.play), c, slot, ra, d.toFloat(), stroke)
        homeWords.forEachIndexed { i, wd ->
            val a = if (i == sel) selAlpha else ha
            val ia = a * (if (i == HOME_FOCUS) Roles.homeItem.alpha else Tok.type_homeItem_alphaDim)
            val base = px(Tok.homeFirstBaseline + i * Tok.homePitch, sp).toFloat()
            word(wd, x, base, KitInk.white, ia)
            rec?.word("home.item$i", wd, x, base, ia)
        }
    }

    private fun DrawScope.npPage() {
        val b = s.board
        val sp = s.ppu
        val back = Offset(px(Tok.npBackRingX + Tok.npBackRing / 2, sp).toFloat(), px(Tok.npBackRingY + Tok.npBackRing / 2, sp).toFloat())
        ring(back, px(Tok.npBackRing, sp).toFloat(), px(Tok.npBackRingStroke, sp).toFloat(), KitInk.white, Tok.alpha_full)
        glyph(Glyphs.back, back, px(Tok.npBackGlyph, sp).toFloat(), KitInk.white, Tok.alpha_full)
        val x = penPx(pen64(Tok.npTextX, sp))
        word(npWords[0], x, px(b.npArtistY, sp).toFloat(), KitInk.white, Roles.npArtist.alpha)
        word(npWords[1], x, px(b.npAlbumY, sp).toFloat(), KitInk.white, Roles.npAlbum.alpha)
        val art = Layout.npArt(s)
        drawRect(KitInk.tile, Offset(art.x0.toFloat(), art.y0.toFloat()), Size(art.w.toFloat(), art.h.toFloat()))
        val bar = px(b.npProgressY, sp).toFloat()
        drawRect(KitInk.white, Offset(art.x0.toFloat(), bar), Size(art.w.toFloat(), px(Tok.npBarHeight, sp).toFloat()), Tok.npBarTrack)
        word(npWords[3], penPx(pen64(Tok.npArtX, sp)), px(b.npProgressY + Tok.npTimeBaseline, sp).toFloat(), KitInk.white, Roles.time.alpha)
        word(npWords[2], x, px(b.npTitleY, sp).toFloat(), KitInk.white, Roles.npTitle.alpha)
        npNext.forEachIndexed { i, wd ->
            word(wd, penPx(pen64(Tok.npTextX + Tok.npNextIndent, sp)), px(b.npTitleY + Tok.npNextDrop + i * Tok.npNextLinePitch, sp).toFloat(), KitInk.white, Roles.npNext.alpha)
        }
        val d = px(Tok.npRing, sp)
        listOf(Glyphs.previous, Glyphs.play, Glyphs.next).forEachIndexed { i, g ->
            val c = Offset(px(Layout.npRingCx(i), sp).toFloat(), px(Layout.npRingCy(s), sp).toFloat())
            ring(c, d.toFloat(), px(Tok.npRingStroke, sp).toFloat(), KitInk.white, Tok.alpha_full)
            glyph(g, c, (d / 2).toFloat(), KitInk.white, Tok.alpha_full)
        }
    }

    private fun DrawScope.ground() = pageGround(bands, accent, 1f, 0f)

    private fun DrawScope.rest(p: Page, rec: Regions?, name: String, now: Long) {
        if (p === np) {
            npPage()
            return
        }
        ground()
        with(kit(p)) { whole(rec, name) }
        chromeAt(inkOf(p).chrome, now, rec, "$name.chrome")
    }

    private fun DrawScope.world(st: PageStack<Page>, now: Long, rec: Regions?) {
        val t = st.turn
        if (st.phase != Phase.Rest && t != null) {
            with(paints.getValue(st)) { drawMove(now, rec) }
            return
        }
        val top = st.top
        if (top == null) homeAt(1f, -1, 0f, rec) else rest(top, rec, "page", now)
    }

    private class Act(val at: Long, val act: () -> Unit)

    private class Case(
        val name: String,
        val note: String,
        val st: PageStack<Page>,
        val acts: List<Act>,
        val from: Long,
        val end: () -> Long,
        val shots: Map<Long, String>,
        val rules: List<Pair<String, String>>,
        val frames: Boolean,
        val quiet: String,
        val paint: DrawScope.(Long, Regions?) -> Unit,
    )

    private fun moveCase(name: String, note: String, st: PageStack<Page>, shots: Map<Long, String> = emptyMap(), rules: List<Pair<String, String>> = emptyList(), act: () -> Unit): Case {
        val fade = ThumbFade()
        var shown = false
        return Case(name, note, st, listOf(Act(0L, act)), 0L, { st.move.endAt }, shots, rules, true, MOVE_QUIET) { t, rec ->
            if (!shown && st.phase == Phase.Enter) {
                shown = true
                fade.track(true, false, st.move.swapAt)
            }
            world(st, t, rec)
            val top = st.top
            if (shown && st.phase == Phase.Rest && top != null && top !== np) listThumb(top, rec, "page.thumb", fade.at(t))
        }
    }

    private fun menuOf(form: MenuForm, title: String, items: List<String>): Triple<Menu, MenuInk, MenuPlan> {
        val m = Menu().also {
            it.open(form, title, items, 0L)
            it.frame(px(Tok.statusTop + Tok.statusHeight, s.stdPpu), h)
        }
        val ink = menuInk(measurer, fonts, m, s, unitDensity, false, covers)
        val plan = m.plan(ink)
        m.settle(plan)
        return Triple(m, ink, plan)
    }

    private fun overlayCase(name: String, note: String, kind: OverKind, base: Page?, title: String, items: List<String>, close: Boolean, shots: Map<Long, String>, rules: List<Pair<String, String>>): Case {
        val st = if (base == null) stack() else stack(base)
        val (m, ink, plan) = menuOf(if (kind == OverKind.Sheet) MenuForm.Sheet else MenuForm.Dlg, title, items)
        val o = Overlay(kind)
        val acts = listOf(Act(0L) { st.open(o) }) + if (close) listOf(Act(CLOSE_AT) { st.close() }) else emptyList()
        val from = if (close) CLOSE_AT else 0L
        val label = if (kind == OverKind.Sheet) "sheet" else "dialog"
        return Case(name, note, st, acts, from, { from + o.dur() }, shots, rules, true, OVERLAY_QUIET) { t, rec ->
            val open = st.overlay
            if (open == null) {
                world(st, t, rec)
            } else {
                val scrim = if (open.kind == OverKind.Sheet) open.sheet(t).scrim else open.dialog(t).scrim
                if (rec == null) world(st, t, null) else rec.fade(1 - scrim) { world(st, t, rec) }
                menuLayer(m, ink, plan, open, s, t, accent, false, Paint())
                rec?.menuLayer(label, m, ink, plan, open, s, t, w, h, accent, false)
            }
        }
    }

    private fun jumpCase(name: String, note: String, base: Page, close: Boolean, frames: Boolean, shots: Map<Long, String>, rules: List<Pair<String, String>>, avail: BooleanArray, under: String?): Case {
        val st = stack(base)
        val g = JumpGrid().also { it.open(avail, 0, s.stdH) }
        val ink = JumpInk(
            measure("exit", Roles.jumpExit),
            (0 until Section.count).map { measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), fonts, unitDensity, covers) },
        )
        val cells = JumpCells(s)
        val o = Overlay(OverKind.Jump).also { it.lead = JUMP_LEAD }
        val acts = listOf(Act(0L) { st.open(o) }) + if (close) listOf(Act(CLOSE_AT) { st.close() }) else emptyList()
        val from = if (close) CLOSE_AT else 0L
        val below = px(s.board.page_contentTop, s.ppu) + base.rows.sumOf { Rows.pitch(s, it) }
        return Case(name, note, st, acts, from, { from + o.dur() }, shots, rules, frames, FADE_QUIET) { t, rec ->
            val open = st.overlay
            if (open == null) {
                world(st, t, rec)
            } else {
                val look = open.jumpLook(t)
                val v = look.alpha
                val dy = (look.dyU * s.stdPpu).roundToInt().toFloat()
                if (rec != null && v >= 1f) {
                    rec.residual = true
                    rec.residualWhy = JUMP_LOUD
                }
                if (rec == null) layer(null, look.page, layerPaint) { world(st, t, null) } else rec.fade(look.page * (1 - v)) { layer(null, look.page, layerPaint) { world(st, t, rec) } }
                val pageRegions = rec?.count ?: 0
                layer(null, v, layerPaint) { translate(0f, dy) { jumpGrid(g, ink, cells, s, g.offset, t) } }
                if (rec != null) {
                    rec.fade(v) { rec.move(0f, dy) { if (under != null && v < 1f) rec.skip(under) { rec.jump("jump", g, ink, cells, s, g.offset, t, h) } else rec.jump("jump", g, ink, cells, s, g.offset, t, h) } }
                    if (v < 1f) {
                        rec.overlap(pageRegions)
                        rec.shade(Box(0f, below.toFloat(), w.toFloat(), h.toFloat()), PRESS_QUIET)
                    }
                }
            }
        }
    }

    private fun t5Case(name: String, note: String, to: Page, frames: Boolean, shots: Map<Long, String>): Case {
        val c = chromeOf(measurer, fonts, s, unitDensity, to.header, to.pivots, covers).also { it.select(0) }
        val leave = songsRest
        val fade = ThumbFade()
        val end = max(Old.motion_pivotStrip, Old.motion_pivotOut + max(Old.motion_pivotRows, Old.motion_pivotRowFade)).toLong()
        val switch = Act(0L) {
            c.switchTo(to.active, 0L, 1)
            fade.track(true, false, 0L)
        }
        return Case(name, note, stack(to), listOf(switch), 0L, { end }, shots, listOf("leave." to T5_GRID), frames, MOVE_QUIET) { t, rec ->
            ground()
            val p = pivotAt(t.toFloat(), 1)
            layer(rec, p.rowsAlpha, layerPaint) { shifted(rec, Math.round(p.rowsDx * s.ppu).toFloat(), 0f) { with(kit(if (p.rowsIn) to else leave)) { whole(rec, if (p.rowsIn) "arrive" else "leave") } } }
            if (p.rowsIn) listThumb(to, rec, "thumb", fade.at(t) * p.rowsAlpha)
            chromeAt(c, t, rec, "chrome")
        }
    }

    private fun pressCase(name: String, note: String, up: Long, frames: Boolean, shots: Map<Long, String>): Case {
        val p = Press()
        val glow = Glow()
        val focus = FocusTrack().also { it.attach() }
        val row = songsPress.rows[songsPress.pressed]
        val strip = Toast()
        val toastInk = ToastInk(measure(row.secondary, Roles.toastArtist, s.ppu), measure(row.primary, Roles.toastTitle, s.ppu))
        val acts = listOf(
            Act(0L) {
                p.down(0f)
                glow.down(songsPress.pressed, 0L)
                focus.set(songsPress.pressed, songsPress.rows.size, 0L, true)
            },
            Act(up) {
                p.tap(up.toFloat())
                strip.show(up, row.secondary, row.primary, track = true)
                strip.frame(ToastPlace.Bottom, h, up)
            },
        )
        val end = (up + Tok.motion_touchReturnDelay + Tok.motion_press).toLong()
        return Case(name, note, stack(songsPress), acts, 0L, { end }, shots, emptyList(), frames, PRESS_QUIET) { t, rec ->
            val now = t.toFloat()
            ground()
            val scale = p.drawn(now)
            with(kit(if (t > up) songsPlaying else songsPress)) { whole(rec, "page", scale, if (p.scale(now) < 1f) RowGlow(glow, glow.level(t)) else null, focus, t) }
            chromeAt(inkOf(songsPress).chrome, t, rec, "page.chrome")
            strip.frame(ToastPlace.Bottom, h, t)
            toast(strip, toastInk, s, t)
            rec?.toast("toast", strip, toastInk, s, t, w)
        }
    }

    private fun toastCase(name: String, note: String, leave: Boolean): Case {
        val t = Toast().also { it.show(0L, "Coastline", "11 Ash", track = true) }
        val ink = ToastInk(measure(t.artist, Roles.toastArtist, s.ppu), measure(t.title, Roles.toastTitle, s.ppu))
        val out = maxOf(Tok.motion_toastOutAt + Tok.motion_toastOut, Tok.motion_toastOutMoveAt + Tok.motion_toastOutMove).toLong()
        val from = if (leave) (Tok.motion_toastRise + Tok.motion_toastHold).toLong() else 0L
        val end = if (leave) from + out else Tok.motion_toastRise.toLong()
        val st = stack(songsRest)
        return Case(name, note, st, listOf(Act(0L) { t.frame(ToastPlace.Top, h, 0L) }), from, { end }, emptyMap(), emptyList(), true, MOVE_QUIET) { now, rec ->
            t.frame(ToastPlace.Top, h, now)
            world(st, now, rec)
            toast(t, ink, s, now)
            rec?.toast("toast", t, ink, s, now, w)
        }
    }

    private fun cases(): List<Case> {
        val t2n = stack()
        val t3 = stack(artistsKnob)
        val t4 = stack(artistsKnob, bt)
        val t4h = stack(artists)
        val t4n = stack(bt)
        val t7 = stack(songsRest)
        return listOf(
            moveCase("t2n", "home to the bluetooth page with no home word, as main.cpp t2n opens Bluetooth from the pull down; $T2_HYBRID", t2n) { t2n.push(bt, MoveKind.T2n) },
            moveCase("t3", "the artists hub (focus on the first artist, one knob step from the # row) to the bluetooth page, the artists pivot morphing into the header", t3) { t3.push(bt, MoveKind.T3) },
            moveCase("t4", "the bluetooth page back to the artists hub, the header morphing into the artists pivot; main.cpp t4 backs from the artist page of the first artist", t4, mapOf(100L to "t4-100", 200L to "t4-200", 400L to "t4-400"), listOf("leave." to T4_LEAVE, "morph.leave" to T4_LEAVE)) {
                t4.pop(MoveKind.T4)
            },
            moveCase("t4h", "the artists hub back to home, its header word into the music item", t4h) { t4h.pop(MoveKind.T4h) },
            moveCase("t4n", "the bluetooth page to home with no word", t4n) { t4n.pop(MoveKind.T4n) },
            t5Case("t5", "pivot switch from albums to artists with the # divider focused, as main.cpp t5 (the switch at 2800, Hub::switch_pivot showing the list thumb); the leaving rows stand in for the ALBUMS grid", artists, true, mapOf(100L to "t5-100", 250L to "t5-250", 450L to "t5-450", 700L to "t5-700", 1500L to "t5-1500")),
            t5Case("t5-songs", "pivot switch from albums to songs with the # divider focused, as main.cpp t5:300:songs", songsEnter, false, mapOf(300L to "t5-300-songs")),
            moveCase("t7", "the songs hub to now playing, page form", t7) { t7.push(np, MoveKind.T7) },
            overlayCase("t13-open", "the power dialog over home, as main.cpp t13", OverKind.Dlg, null, POWER, POWER_ITEMS, false, mapOf(120L to "t13-120", 400L to "t13-400"), listOf("home.item" to HOME_TEXT, "home.watermark" to HOME_TEXT, "dialog.item" to POWER_TEXT)),
            overlayCase("t13-close", "the power dialog closing 600 ms after the open, as main.cpp t13x", OverKind.Dlg, null, POWER, POWER_ITEMS, true, mapOf(CLOSE_AT + T13X to "t13x-80"), listOf("home.item" to HOME_TEXT, "home.watermark" to HOME_TEXT, "dialog.item" to POWER_TEXT)),
            jumpCase("t14-songs", "the jump grid open over the songs hub, as main.cpp t14:500:songs", songsRest, false, false, mapOf(500L to "t14-500-songs"), emptyList(), KitPages.jumpAvail, null),
            jumpCase("t14-fade-songs", "the jump grid cross fade over the songs hub with its # divider focused as main.cpp enter_hub leaves it, as main.cpp t14:150:songs", songsEnter, false, false, mapOf(150L to "t14-150-songs"), T14_SONGS, KitPages.jumpAvail, null),
            jumpCase("t14-close-songs", "the jump grid closing 600 ms after the open over the songs hub with its # divider focused, as main.cpp t14x:120:songs", songsEnter, true, false, mapOf(CLOSE_AT + T14X to "t14x-120-songs"), T14_SONGS, KitPages.jumpAvail, null),
            pressCase("f3-tap", "a tap on the first song row: down at 0, up at 50 (main.cpp rowpress tap), the press held 200 past the up, then 100 back; after the up the song plays (accent title, no press glow) and its track toast rises", TAP_UP, true, mapOf(DOWN_SHOT to "rowpress-0-hubsongs-down", TAP_SHOT to "rowpress-0-hubsongs-tap")),
            pressCase("f3-up", "the same row with the up at 150 (main.cpp rowpress up), shot at 650 with the song playing and its track toast up", UP_AT, false, mapOf(UP_SHOT to "rowpress-0-hubsongs-up")),
            toastCase("f11-drop", "the track toast dropping in at the top over the songs hub", false),
            toastCase("f11-leave", "the same toast leaving after its hold", true),
        )
    }

    private fun paint(c: Case, t: Long, rec: Regions?): Bitmap {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(KitInk.bg, Offset.Zero, size)
            c.paint(this, t, rec)
        }
        return img.asAndroidBitmap()
    }

    private fun play(c: Case): Pair<Int, List<Tile>> {
        val dir = File(root, "$board/${c.name}")
        dir.deleteRecursively()
        val acts = c.acts.sortedBy { it.at }
        var next = 0
        fun at(t: Long) {
            while (next < acts.size && acts[next].at <= t) acts[next++].act()
            c.st.frame(t)
        }
        acts.map { it.at }.filter { it < c.from }.distinct().sorted().forEach { at(it) }
        at(c.from)
        val last = c.end()
        val frames = if (c.frames) generateSequence(c.from) { it + STEP }.takeWhile { it <= last }.toSet() else emptySet()
        if (frames.isNotEmpty()) dir.mkdirs()
        val names = mutableListOf<String>()
        val tiles = mutableListOf<Tile>()
        fun shoot(t: Long) {
            val kind = c.shots[t]
            if (kind == null && t !in frames) return
            val rec = kind?.let { Regions(board, w, h, s, c.rules).also { it.residualWhy = c.quiet } }
            val bmp = paint(c, t, rec)
            if (t in frames) {
                val f = "f%04d.png".format(names.size)
                File(dir, f).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, Render.PNG_QUALITY, it) }
                names += f
            }
            if (kind != null) {
                tiles += render.tile(FOLDER, "$board-$kind", "${dev.label} $kind", listOf(render.facts(dev, Nav.None, s), "${c.name} at $t ms", c.note), bmp, emptyList(), s.stdPpu, rec)
            } else {
                bmp.recycle()
            }
        }
        shoot(c.from)
        val times = (frames + c.shots.keys + acts.map { it.at }).filter { it > c.from }.toSortedSet()
        for (t in times) {
            at(t)
            shoot(t)
        }
        if (names.isNotEmpty()) File(dir, "anim.json").writeText("{\"fps\": $FPS, \"frames\": [" + names.joinToString(", ") { "\"$it\"" } + "]}\n")
        return names.size to tiles
    }

    @Test
    fun motionFramesAndShotTiles() {
        val lines = mutableListOf("# Motion frames at A105", "", "Frames at $FPS fps from the kit's PageMove, PageStack, Overlay, Toast and Press; shot tiles carry `<tile>.regions.json` for `tools/inkband.py`.", "")
        for (c in cases()) {
            val (count, tiles) = play(c)
            val shots = tiles.joinToString(", ") { "`${File(it.path).name}`" }
            lines += "- `$FOLDER/$board/${c.name}/`: $count frames; ${c.note}" + if (shots.isEmpty()) "" else "; shots $shots"
            if (tiles.isNotEmpty()) render.write(Sheet(FOLDER, c.name, listOf("${c.name} at the funeOS shot times: ${c.note}."), tiles))
        }
        render.index(FOLDER, lines)
    }

    companion object {
        private const val FOLDER = "motion"
        private const val FPS = 40
        private const val STEP = 1000L / FPS
        private const val HOME_FOCUS = 0
        private const val KNOB_ROW = 1
        private const val CLOSE_AT = 600L
        private const val JUMP_LEAD = 17L
        private const val T13X = 80L
        private const val T14X = 120L
        private const val TAP_UP = 50L
        private const val DOWN_SHOT = 50L
        private const val TAP_SHOT = 200L
        private const val UP_AT = 150L
        private const val UP_SHOT = 650L
        private const val WATERMARK = "fune"
        private const val POWER = "power"
        private val POWER_ITEMS = listOf("screen off", "power off")
        private const val T2_HYBRID = "the rows zoom in as one layer as funeOS v0.4.2 draws them, at the step alpha of the seed set's A105 board (the bridge until the page move port)"
        private const val T4_LEAVE = "funeOS t4 leaves the artist page of the first artist; the kit has no artist page, so this case leaves the bluetooth page"
        private const val T5_GRID = "funeOS t5 leaves the ALBUMS grid; the kit has no grid page, so the songs rows stand in"
        private const val MOVE_QUIET = "page moves and pivot switches draw funeOS content the kit has no page or part for (the ALBUMS grid, the artist page, the artist row thumb strips)"
        private const val OVERLAY_QUIET = "the page or home under the overlay shows funeOS fixture rows and home items the kit page does not draw"
        private const val FADE_QUIET = "below full alpha the grid cross fades over the funeOS page under it"
        private const val PRESS_QUIET = "the funeOS songs hub shows a seventh song row below the kit page's last row"
        private const val JUMP_LOUD = "the opaque jump grid covers the page, so every funeOS mark on it is a kit element"
        private const val T14_THUMB = "funeOS draws the songs hub list thumb under the cross fading grid thumb (+45 on the thumb column in t14-150-songs and t14x-120-songs); the jump scenes draw no page list thumb under the grid, so the kit has +25 and +17"
        private const val T14_DIVIDER = "the jump grid's c letter crosses the top border of the # divider tile, so the tile's window takes the letter's ink"
        private val T14_SONGS = listOf("jump.thumb" to T14_THUMB, "page.row0" to T14_DIVIDER)
        private const val HOME_TEXT = "funeplayer's home items and watermark, funeOS has music, bluetooth, playlists, history, apps, settings"
        private const val POWER_TEXT = "funeplayer's power items; funeOS adds suspend, usb storage and reboot by device state (app.cpp open_power)"
    }
}
