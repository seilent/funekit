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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.core.Section
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.fx
import net.seilent.funekit.fxDiv
import net.seilent.funekit.fxInt
import net.seilent.funekit.Nav
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kept
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastInk
import net.seilent.funekit.kit.ToastPlace
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.jumpGrid
import net.seilent.funekit.kit.listRow
import net.seilent.funekit.kit.menuInk
import net.seilent.funekit.kit.menuLayer
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.rowInk
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
import net.seilent.funekit.kit.Ink as KitInk

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PhoneScaleSheetTest {
    private val render = Render(File("build/sheets"))
    private val unitDensity = Density(1f)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val measurer = TextMeasurer(fonts, unitDensity, LayoutDirection.Ltr)
    private val accent = Accent.forHue(Accent.defaultHue)
    private val a105 = Phones.a105
    private val p2160 = Phones.p2160Page
    private val flip7 = Phones.flip7Page
    private val zenfone9 = Phones.zenfone9Page
    private val songs = KitPages.songsLong
    private val artists = KitPages.artists

    private class Shot(val bmp: Bitmap, val s: Scale)

    private fun shot(dev: Dev, surface: Surface = Surface.Page, large: Boolean = false, base: (Scale) -> Scale = { it }, block: DrawScope.(Scale) -> Unit): Shot {
        val nav = Phones.nav(dev)
        val s = base(scale(dev.window(nav), nav, surface, large))
        val img = ImageBitmap(dev.w, dev.h)
        CanvasDrawScope().draw(unitDensity, LayoutDirection.Ltr, Canvas(img), Size(dev.w.toFloat(), dev.h.toFloat())) {
            pageGround(groundBands(dev.h, false), accent)
            block(s)
        }
        return Shot(img.asAndroidBitmap(), s)
    }

    private fun tile(file: String, option: String, dev: Dev, sh: Shot, notes: List<String> = emptyList(), outlined: Boolean = false): Tile =
        render.tile(F, file, option, Phones.facts(dev, sh.s) + notes, sh.bmp, if (outlined) Phones.marks(dev, sh.s) else emptyList(), Phones.sheetPpu(dev))

    private fun DrawScope.hub(s: Scale, p: Page) {
        val c = chromeOf(measurer, fonts, s, unitDensity, p.header, p.pivots, null).also { it.select(p.active) }
        chrome(c, 0L)
        val top = px(s.board.page_contentTop, s.ppu)
        val bottom = px(s.stdContentBottom, s.stdPpu)
        var y = top
        clipRect(0f, top.toFloat(), size.width, bottom.toFloat()) {
            for ((i, r) in p.rows.withIndex()) {
                if (y >= bottom) break
                val focused = i == p.focused
                val ink = rowInk(measurer, fonts, r, s, unitDensity, false, null)
                translate(0f, y.toFloat()) {
                    listRow(ink, s, if (focused) Tok.alpha_full else Tok.type_listPrimary_alphaDim, focused, 1f, accent, false, null, focusShown = true)
                }
                y += Rows.pitch(s, r.collection)
            }
        }
    }

    private fun DrawScope.menu(s: Scale, kind: OverKind) {
        val form = if (kind == OverKind.Sheet) MenuForm.Sheet else MenuForm.Dlg
        val m = Menu().also {
            it.open(form, "play", listOf("play", "play next", "add to now playing"), 0L)
            it.frame(px(Tok.statusTop + Tok.statusHeight, s.stdPpu), px(s.stdContentBottom, s.stdPpu))
        }
        val ink = menuInk(measurer, fonts, m, s, unitDensity, false, null)
        val plan = m.plan(ink).also { m.settle(it) }
        val o = Overlay(kind).also { it.open(0L) }
        menuLayer(m, ink, plan, o, s, o.dur().toLong(), accent, false, Paint())
    }

    private fun DrawScope.trackToast(s: Scale) {
        val picked = songs.rows[KitPages.PICKED]
        val t = Toast()
        t.show(0L, picked.secondary, picked.primary, track = true)
        t.frame(ToastPlace.Bottom, px(s.stdContentBottom, s.stdPpu), 0L)
        t.frame(ToastPlace.Bottom, px(s.stdContentBottom, s.stdPpu), TOAST_AT)
        val ink = ToastInk(
            measurer.word(t.artist, Roles.toastArtist, roleStyle(Roles.toastArtist, s, unitDensity, KitInk.white), fonts, unitDensity, null),
            measurer.word(t.title, Roles.toastTitle, roleStyle(Roles.toastTitle, s, unitDensity, KitInk.white), fonts, unitDensity, null),
        )
        toast(t, ink, s, TOAST_AT)
    }

    private fun DrawScope.jump(s: Scale) {
        val g = JumpGrid().also { it.open(KitPages.jumpAvail, 0, s.stdContentBottom) }
        fun style(r: Role) = roleStyle(r, s, unitDensity, KitInk.white, ppu = s.stdPpu)
        val ink = JumpInk(
            measurer.word("exit", Roles.jumpExit, style(Roles.jumpExit), fonts, unitDensity, null),
            (0 until Section.count).map { measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), fonts, unitDensity, null) },
        )
        drawRect(KitInk.bg)
        jumpGrid(g, ink, JumpCells(s), s, g.offset, 0L)
    }

    private fun gridCols(s: Scale): Int {
        val w = fxDiv(fxInt(s.widthPx), fx(s.stdPpu))
        return ((w - fx(Tok.gridX) - fx(Tok.gridX) + fx(Tok.gridGap)) / fx(Tok.gridTile + Tok.gridGap)).toInt()
    }

    private fun DrawScope.albums(s: Scale, cols: Int, x0: Float) {
        val c = chromeOf(measurer, fonts, s, unitDensity, "music", KitPages.hubPivots, null).also { it.select(0) }
        chrome(c, 0L)
        val top = s.board.page_contentTop
        val bottom = px(s.stdContentBottom, s.stdPpu)
        clipRect(0f, px(top, s.ppu).toFloat(), size.width, bottom.toFloat()) {
            var r = 0
            while (px(top + r * Tok.tileRowPitch, s.ppu) < bottom) {
                for (k in 0 until cols) {
                    val x = x0 + k * (Tok.gridTile + Tok.gridGap)
                    val y = top + r * Tok.tileRowPitch
                    val l = px(x, s.ppu)
                    val t = px(y, s.ppu)
                    drawRect(KitInk.tile, Offset(l.toFloat(), t.toFloat()), Size((px(x + Tok.gridTile, s.ppu) - l).toFloat(), (px(y + Tok.gridTile, s.ppu) - t).toFloat()))
                }
                r++
            }
        }
    }

    private fun line(case: String, look: String) = "- ${Render.BASE}/$F `$F/$case.png`: $look."

    private val cover = Dev("948x1048", "Galaxy Z Flip7 cover", 948, 1048, listOf(Nav.None))
    private val widget = Dev("720x748", "cover sheet window", 720, 748, listOf(Nav.None))
    private val tall = Dev("1080x1600", "1080 x 1600 window", 1080, 1600, listOf(Nav.None))

    private fun genClass(h: Float): Float {
        val low = h < TokBoard.r1.canvasH
        val lo = if (low) TokBoard.v1 else TokBoard.r1
        val hi = if (low) TokBoard.r1 else TokBoard.a105
        return lo.minTextPx + (hi.minTextPx - lo.minTextPx) * (h - lo.canvasH) / (hi.canvasH - lo.canvasH)
    }

    private fun floored(s: Scale, floor: Float): Scale {
        s.board.javaClass.getDeclaredField("minTextPx").apply { isAccessible = true }.setFloat(s.board, floor)
        return s
    }

    private fun floorPair(id: String, what: String, dev: Dev, surface: Surface, block: DrawScope.(Scale) -> Unit): List<Tile> {
        val rule = shot(dev, surface, block = block)
        val gen = genClass(rule.s.fit?.h ?: rule.s.stdH)
        val old = shot(dev, surface, base = { floored(it, gen) }, block = block)
        return listOf(
            tile("$id-rule", "$what, the rule: text never under ${"%.1f".format(rule.s.board.minTextPx)} px", dev, rule),
            tile("$id-gen", "$what, Gen's class: text never under ${"%.1f".format(gen)} px", dev, old, listOf(GEN_NOTE)),
        )
    }

    @Test
    fun textFloor() {
        val tiles = floorPair("p2160-songs", "Mi MIX 2S songs hub, page base", p2160, Surface.Page) { hub(it, songs) } +
            floorPair("p2160-sheet", "Mi MIX 2S sheet over the songs hub, page base", p2160, Surface.Page) { hub(it, songs); menu(it, OverKind.Sheet) } +
            floorPair("p2160-home-songs", "Mi MIX 2S songs hub on the fit base, as home's size", p2160, Surface.Home) { hub(it, songs) } +
            floorPair("cover-songs", "Galaxy Z Flip7 cover songs hub, fit by height", cover, Surface.Page) { hub(it, songs) } +
            floorPair("cover-sheet", "Galaxy Z Flip7 cover sheet over the songs hub", cover, Surface.Page) { hub(it, songs); menu(it, OverKind.Sheet) }
        render.write(Sheet(FLOOR, "textfloor", listOf(FLOOR_FORM, Phones.SAME_SIZE), tiles))
        render.index(FLOOR, listOf(
            "# Text floor (FB-textfloor)",
            "",
            FLOOR_FORM,
            "",
            "- ${Render.BASE}/$FLOOR `$FLOOR/textfloor.png`: each pair the rule (left) against Gen's class (right): the Mi MIX 2S page base 23.7 px against 40 px, its fit base 36 px against 40 px, the Galaxy Z Flip7 cover 26.2 px against 10 px.",
            "- clean tiles at device px: ${Render.BASE}/$FLOOR/tiles",
        ))
    }

    private fun DrawScope.fitRuler(s: Scale, b: TokBoard.Board) {
        val ink = Ink(drawContext.canvas.nativeCanvas, s.ppu, s.left, s.board, s.h, s.w)
        val top = s.board.page_contentTop
        for (k in 0 until Tok.gridCols.toInt()) {
            val x = Tok.gridX + k * (Tok.gridTile + Tok.gridGap)
            ink.box(x, top, Tok.gridTile, Tok.gridTile)
            ink.text(Roles.gridCaption, "album $k", x, top + Tok.gridTile + Tok.tileCaptionDrop)
            ink.text(Roles.gridCaption, "artist $k", x, top + Tok.gridTile + Tok.tileCaptionDrop + b.tileCaptionPitch)
            ink.box(x, top + Tok.gridTile + b.tileCaptionBand, Tok.gridTile, 1f / s.ppu, Tok.accent_h120)
        }
        val next = Roles.npViewNext(b)
        val y = top + Tok.gridTile + b.tileCaptionBand + Tok.npNextDrop
        for (i in 0 until 3) ink.text(next, "coming up line ${i + 1}", Tok.npNextIndent, y + i * Tok.npNextLinePitch)
    }

    @Test
    fun fitByHeight() {
        val tiles = listOf(cover, widget, tall).flatMap { dev ->
            val after = shot(dev) { fitRuler(it, it.board) }
            val before = shot(dev) { fitRuler(it, TokBoard.r1) }
            listOf(
                tile("${dev.id}-units", "${dev.label}: the R1 and A105 values", dev, before),
                tile("${dev.id}-rule", "${dev.label}: the rule at funeOS v0.4.2", dev, after, listOf(FIT_NOTE)),
            )
        }
        render.write(Sheet(FIT, "fitbyheight", listOf(FIT_FORM, Phones.SAME_SIZE), tiles))
        render.index(FIT, listOf(
            "# Fit by height windows (FB-fitbyheight)",
            "",
            FIT_FORM,
            "",
            "- ${Render.BASE}/$FIT `$FIT/fitbyheight.png`: each pair with the R1 and A105 values (left) against the rule (right): the album caption pitch, the caption band (the green line, which on a 320 u canvas runs past the hit cell, as funeOS draws it) and the coming up lines, on the Galaxy Z Flip7 cover, the 720 x 748 cover sheet window and a 1080 x 1600 window.",
            "- clean tiles at device px: ${Render.BASE}/$FIT/tiles",
        ))
    }

    @Test
    fun phoneScale() {
        val pages = listOf(
            tile("a105-songs", "A105 songs hub, fit base (the reference)", a105, shot(a105) { hub(it, songs) }),
            tile("a105-artists", "A105 artists hub, fit base", a105, shot(a105) { hub(it, artists) }, listOf(ARTISTS_NOTE)),
            tile("p2160-songs", "Mi MIX 2S songs hub, page base", p2160, shot(p2160) { hub(it, songs) }, listOf(KEPT_HEADER)),
            tile("p2160-artists", "Mi MIX 2S artists hub, page base", p2160, shot(p2160) { hub(it, artists) }, listOf(KEPT_HEADER, ARTISTS_NOTE)),
            tile("p2160-songs-large", "Mi MIX 2S songs hub, page base at SIZE LARGE", p2160, shot(p2160, large = true) { hub(it, songs) }),
            tile("flip7-songs", "Galaxy Z Flip7 main songs hub, page base", flip7, shot(flip7) { hub(it, songs) }),
            tile("zenfone9-songs", "Zenfone 9 songs hub, page base", zenfone9, shot(zenfone9) { hub(it, songs) }),
        )
        render.write(Sheet(F, "pages", listOf(PAGES_FORM, Phones.SAME_SIZE), pages))

        val overlays = listOf(
            tile("a105-sheet", "A105 sheet (T12), fit base", a105, shot(a105) { hub(it, songs); menu(it, OverKind.Sheet) }),
            tile("p2160-sheet", "Mi MIX 2S sheet (T12), page base", p2160, shot(p2160) { hub(it, songs); menu(it, OverKind.Sheet) }),
            tile("a105-dialog", "A105 dialog (T13), fit base", a105, shot(a105) { hub(it, songs); menu(it, OverKind.Dlg) }),
            tile("p2160-dialog", "Mi MIX 2S dialog (T13), page base", p2160, shot(p2160) { hub(it, songs); menu(it, OverKind.Dlg) }, listOf(KEPT_TITLE)),
            tile("a105-toast", "A105 track toast, fit base", a105, shot(a105) { hub(it, songs); trackToast(it) }),
            tile("p2160-toast", "Mi MIX 2S track toast, page base", p2160, shot(p2160) { hub(it, songs); trackToast(it) }, listOf(TOAST_NOTE)),
        )
        render.write(Sheet(F, "overlays", listOf("Overlays over the songs hub at rest.", Phones.SAME_SIZE), overlays))

        val jumps = listOf(
            tile("a105-jump", "A105 jump grid, fit base", a105, shot(a105) { jump(it) }),
            tile("p2160-jump", "Mi MIX 2S jump grid, fit base over the drawer's page base", p2160, shot(p2160, base = { it.kept() }) { jump(it) }, listOf(JUMP_NOTE)),
        )
        render.write(Sheet(F, "jump", listOf(JUMP_FORM, Phones.SAME_SIZE), jumps))

        val albums = listOf(
            tile("a105-albums", "A105 ALBUMS grid stub, fit base", a105, shot(a105) { albums(it, Tok.gridCols.toInt(), Tok.gridX) }, listOf(STUB_NOTE)),
            tile("p2160-albums", "Mi MIX 2S ALBUMS grid stub, page base, columns from the width", p2160, shot(p2160) { albums(it, gridCols(it), Tok.gridX) }, listOf(ALBUMS_NOTE, STUB_NOTE)),
        )
        render.write(Sheet(F, "albums", listOf(ALBUMS_FORM, Phones.SAME_SIZE), albums))

        val marks = listOf(
            tile("p2160-songs-marks", "Mi MIX 2S songs hub, page base, with every outline", p2160, shot(p2160) { hub(it, songs) }, outlined = true),
        )
        render.write(Sheet(F, "marks", listOf(Phones.MARKS, Phones.SAME_SIZE), marks))

        render.index(F, listOf(
            "# Phone scale",
            "",
            "Home keeps the fit size. Pages, their overlays and the drawer take the A105's physical size on a canvas as wide as the window. The Mi MIX 2S is at its probed 403.411 ppi (2.96669 px/u, 364.04 x 728.08 u); the Galaxy Z Flip7 main (397.3 ppi) and the Zenfone 9 (446.1 ppi) are at their panel spec, their densities not read yet. Under Waydroid the Zenfone 9 may report a copied density, which the rule rejects, and it would keep the fit size.",
            "",
            line("pages", "the songs and artists hubs on the page base on the Mi MIX 2S, the Galaxy Z Flip7 main and the Zenfone 9, the Mi MIX 2S at LARGE, beside the A105 at the same mm scale"),
            line("overlays", "the sheet, the dialog and the toast on the Mi MIX 2S page base, each beside the A105's"),
            "- ${Render.BASE}/$LAUNCHER `$LAUNCHER/home-drawer.png`: home at the fit size beside the drawer on the page base, gesture and 3 button navigation, beside the A105's",
            line("jump", "the drawer's jump grid on the fit base on the Mi MIX 2S beside the A105's"),
            line("albums", "the ALBUMS grid token stub with columns from the width on the Mi MIX 2S beside the A105's"),
            "- where the system bands sit: ${Render.BASE}/$F `$F/marks.png` (the songs hub) and ${Render.BASE}/$LAUNCHER `$LAUNCHER/marks.png` (the drawer), the Mi MIX 2S page base with every outline and the funeOS 240 u line drawn; the other sheets draw none, their captions list the same outlines as text",
            "- clean tiles at device px: ${Render.BASE}/$F/tiles and ${Render.BASE}/$LAUNCHER/tiles",
            "",
            FALLBACKS,
        ))
    }

    companion object {
        const val F = "phonescale-signoff"
        const val LAUNCHER = "phonescale-signoff/launcher"
        const val FLOOR = "fb-textfloor"
        const val FIT = "fb-fitbyheight"
        private const val GEN_NOTE = "Gen's class: the floor follows the line between the R1 and A105 floors, so it grows on tall windows and drops to V1's 10 px on a 320 u canvas"
        private const val FLOOR_FORM = "The smallest text px on each window. The rule: 8 px per u times the window's px per u (the R1 and A105 floor), V1's own 10 px only on the V1 board at its own ppu."
        private const val FIT_NOTE = "a 320 u canvas takes V1's values; a canvas between 320 and 400 u takes the line between V1 and R1; the caption band runs past the hit cell on those windows"
        private const val FIT_FORM = "Windows whose height limits them (the cover display, the cover sheet, a 1080 x 1600 window) take V1's values for the tokens V1 alone changed at funeOS v0.4.2."
        private const val TOAST_AT = 2000L
        private const val PAGES_FORM = "Pages on the page base: the rows, the pivots and the header word at the A105's size, the extra width and height used."
        private const val JUMP_NOTE = "4.5 px/u over a drawer at 2.96669 px/u: the grid draws 1.52 times larger than the page under it"
        private const val JUMP_FORM = "The jump grid on the drawer takes the fit base: the grid over the full width, funeOS's kept rule."
        private const val ALBUMS_NOTE = "4 columns ending at 313.5 u: 50.5 u to the right edge, against 7 u at 240 u"
        private const val ALBUMS_FORM = "The ALBUMS grid takes its columns from the width: 4 on the Mi MIX 2S."
        private const val KEPT_HEADER = "the header word is a kept block drawn at the page base's STANDARD px (FALLBACK)"
        private const val KEPT_TITLE = "dialogTitle is a kept block drawn at the page base's STANDARD px (FALLBACK)"
        private const val ARTISTS_NOTE = "the kit artists page has 4 rows"
        private const val TOAST_NOTE = "the toast sits on the 66 px gesture handle, as it does on the fit base"
        private const val STUB_NOTE = "token stub: gridTile squares at gridX plus gridTile and gridGap steps, rows at tileRowPitch from page.contentTop as funeOS hub.cpp:695-701 (cells tileCellH tall, the caption space under the square); no captions"
        private const val FALLBACKS = "FALLBACK rows drawn here: the physical rule on every page base tile (pages.png p2160-songs: the A105 reference at 407.94 ppi, a page keeps the A105 size unless it is 10 % smaller, never under half of it); the kept blocks on the page base, the header word (pages.png p2160-songs, p2160-artists), dialogTitle (overlays.png p2160-dialog) and the ALBUMS grid (albums.png)."
    }
}
