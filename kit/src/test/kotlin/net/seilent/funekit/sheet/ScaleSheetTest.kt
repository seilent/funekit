package net.seilent.funekit.sheet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.gen.TokFit
import net.seilent.funekit.px
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScaleSheetTest {
    private val render = Render(File("build/sheets"))
    private val all = listOf(Nav.Buttons, Nav.Gesture, Nav.None)
    private val cutoutRule = "Cutout rule: content draws under the cutout, nothing moves (display cutout mode shortEdges)."
    private val outlines = "Outlines are the caption layer only: probed Android bars and cutouts in color, funeOS bands in white."

    private val v1 = Dev("240x320", "unit test window", 240, 320, listOf(Nav.None))
    private val f20 = Dev("480x640", "Surfans F20 Pro+", 480, 640, all)
    private val r1 = Dev("480x800", "funeOS R1 board", 480, 800, all)
    private val a105 = Dev(
        "720x1280", "A105 stock and 16:9 DAPs", 720, 1280, all, "A105 stock probed 2026-11-09", Nav.Buttons,
        sdk = ANDROID_9, statusPx = A105_STATUS, navPx = A105_NAV,
    )
    private val d768 = Dev("768x1280", "DAP", 768, 1280, all)
    private val d750 = Dev("750x1334", "DAP", 750, 1334, all)
    private val d640 = Dev("640x1280", "DAP", 640, 1280, all)
    private val d720 = Dev("720x1440", "DAP", 720, 1440, all)
    private val p2160 = Phones.p2160
    private val p2160Buttons = Phones.p2160Buttons
    private val d1920 = Dev("1080x1920", "DAP", 1080, 1920, all)
    private val d2340 = Dev("1080x2340", "DAP or phone", 1080, 2340, all)
    private val flip7 = Phones.flip7
    private val cover = Dev(
        "948x1048", "Galaxy Z Flip7 cover", 948, 1048, listOf(Nav.Gesture), "Galaxy Z Flip7 cover probed 2026-11-09", Nav.Gesture,
        sdk = ANDROID_17, gesturePx = COVER_EDGE, cutout = Rect(0, COVER_CUTOUT, 948, 1048),
        extra = listOf(Mark("navigationBars ${1048 - COVER_NAV} px, not visible", 0, COVER_NAV, 948, 1048, Tok.accent_h120)),
    )
    private val flip6 = Dev("720x748", "Flip6 cover, not probed", 720, 748, listOf(Nav.Gesture))
    private val land = Dev("2160x1080", "landscape or split screen", 2160, 1080, listOf(Nav.Gesture, Nav.None))

    private val worked = listOf(v1, f20, r1, a105, d768, d750, d640, d720, p2160, d1920, d2340, flip7)

    private fun tile(
        folder: String, file: String, option: String, dev: Dev, nav: Nav, surface: Surface,
        notes: List<String> = emptyList(), large: Boolean = false, gs: Boolean = false,
        keep: (Mark) -> Boolean = { true }, more: (Scale) -> List<Mark> = { emptyList() },
        draw: (Ink, Scale) -> Unit,
    ): Tile {
        val (bmp, s) = render.canvas(dev, nav, surface, large, draw)
        val marks = dev.marks(nav).filter(keep) + render.bands(s, dev, nav, gs) + more(s)
        return render.tile(folder, file, option, listOf(render.facts(dev, nav, s)) + notes, bmp, marks, s.stdPpu)
    }

    private fun hub(ink: Ink, s: Scale) {
        Stubs.bg(ink)
        Stubs.hub(ink, contentBottom(s))
    }

    private fun surfaces(folder: String, dev: Dev, nav: Nav, gs: Boolean, notes: Map<String, String>): List<Tile> = listOf(
        tile(folder, "${dev.id}-home", "home", dev, nav, Surface.Home, listOfNotNull(notes["home"]), gs = gs) { ink, _ ->
            Stubs.bg(ink)
            Stubs.home(ink)
        },
        tile(folder, "${dev.id}-hub", "hub page, collection rows", dev, nav, Surface.Page, listOfNotNull(notes["page"]), gs = gs, draw = ::hub),
        tile(folder, "${dev.id}-nowplaying", "now playing", dev, nav, Surface.NowPlaying, listOfNotNull(notes["np"]), gs = gs) { ink, s ->
            Stubs.bg(ink)
            Stubs.nowPlaying(ink, s.navOn)
        },
        tile(folder, "${dev.id}-keyboard", "keyboard band (KB1)", dev, nav, Surface.Page, listOfNotNull(notes["kb"]), gs = gs) { ink, s ->
            Stubs.keyboard(ink, contentBottom(s))
        },
    )

    private fun line(folder: String, case: String, look: String, question: String) =
        "- ${Render.BASE}/$folder `$folder/$case.png`: $look. $question"

    @Test
    fun s1pBands() {
        val f = "s1p"
        val q = "Windows wider than 3:4 draw the V1 canvas centered between the side insets with color.bg bands."
        val cases = listOf(cover to Nav.Gesture, flip6 to Nav.Gesture, land to Nav.None, land to Nav.Gesture)
        val index = mutableListOf("# Pillarbox bands", "")
        for ((dev, nav) in cases) {
            val case = "${dev.id}-${nav.name.lowercase()}"
            val tiles = listOf(
                tile(f, "$case-hub", "hub page", dev, nav, Surface.Page, draw = ::hub),
                tile(f, "$case-nowplaying", "now playing", dev, nav, Surface.NowPlaying) { ink, s ->
                    Stubs.bg(ink)
                    Stubs.nowPlaying(ink, s.navOn)
                },
            )
            render.write(Sheet(f, case, listOf(q, cutoutRule, outlines), tiles))
            index += line(f, case, "${dev.w}x${dev.h} ${dev.label} in ${nav.name}, the purple outlined side bands", q)
        }
        render.index(f, index)
    }

    private fun value(b: TokBoard.Board, name: String) =
        TokBoard.Board::class.java.getMethod("get" + name.replace('.', '_').replaceFirstChar { it.uppercase() }).invoke(b) as Float

    @Test
    fun s3Extrapolation() {
        val f = "s3"
        val q = "The 31 free tokens extrapolated along the R1 to A105 line above 426.67 u."
        val heights = listOf(a105 to Nav.None, p2160 to Nav.Gesture, d2340 to Nav.None, flip7 to Nav.Gesture)
        val tiles = heights.map { (dev, nav) ->
            tile(f, "nowplaying-${dev.id}", "now playing, phone track variant", dev, nav, Surface.NowPlaying,
                listOf("free tokens drawn: npFromBaseline, npArtistY, npAlbumY, npArtTop, npArt; seek band outlined"),
                more = { s -> seek(s) }) { ink, s ->
                Stubs.bg(ink)
                Stubs.nowPlaying(ink, s.navOn, phone = true)
            }
        }
        render.write(Sheet(f, "nowplaying", listOf(q, "The A105 tile is the 426.67 u reference.", cutoutRule, outlines), tiles))
        val free = File("../tokens/fit.txt").readLines().filter { it.endsWith(" free") }.map { it.substringBefore(' ') }
        assertTrue(free.size == FREE_TOKENS)
        render.write(Sheet(f, "table", listOf(q, "Numbers for every free token; lockBtGlyph, vol.ringY, kb.keyH and the large.* lyrics pitches have no stub place."), listOf(table(f, free))))
        render.index(f, listOf(
            "# Free token extrapolation", "",
            line(f, "nowplaying", "artist, album, from, art box and seek band at 426.67, 480, 520 and 560 u", q),
            line(f, "table", "the 31 free tokens at V1, R1, A105 and 480, 520, 560 u", q),
        ))
    }

    private fun seek(s: Scale): List<Mark> {
        val b = s.board
        val y = px(b.npSeekBandBottom, s.ppu)
        return listOf(Mark("seek band X0 to X1, bottom (npSeekBandX1, npSeekBandBottom free)", s.left + px(Tok.npSeekBandX0, s.ppu), y - px(Tok.focus_border, s.ppu), s.left + px(b.npSeekBandX1, s.ppu), y, Tok.accent_h170))
    }

    private fun table(folder: String, free: List<String>): Tile {
        val ppu = render.sheetPpu
        val drawn = setOf("npArtistY", "npAlbumY", "npFromBaseline", "npArtTop", "npArt", "npSeekBandX1", "npSeekBandBottom")
        val at = listOf(TokBoard.v1, TokBoard.r1, TokBoard.a105) + listOf(p2160, d2340, flip7).map { TokFit.at(it.h / it.stdPpu, it.stdPpu) }
        val head = listOf("token", "place", "V1 320", "R1 400", "A105 426.67", "480", "520", "560")
        val rows = listOf(head) + free.map { n ->
            val place = when {
                n in drawn -> "s3 now playing"
                n == "overlayAnchorScale" -> "q3 overlays"
                else -> "table only"
            }
            listOf(n, place) + at.map { "%.2f".format(value(it, n)) }
        }
        val role = Roles.listSecondary
        val probe = Ink(Canvas(), ppu, 0, TokBoard.r1, TokBoard.r1.canvasH)
        val widths = head.indices.map { c -> rows.maxOf { probe.advance(role, it[c]) } + Tok.gutter }
        val pitch = probe.line(role)
        val wPx = px(widths.sum() + Tok.gutter * Ink.HALF, ppu)
        val hPx = px(rows.size * pitch + Tok.gutter * Ink.HALF, ppu)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(argb(Tok.color_bg, Tok.alpha_full))
        val ink = Ink(c, ppu, 0, TokBoard.r1, hPx / ppu)
        rows.forEachIndexed { r, cells ->
            var x = Tok.gutter
            val base = Tok.gutter + r * pitch + ink.ascent(role)
            cells.forEachIndexed { i, s ->
                ink.text(role, s, x, base, if (r == 0) Tok.alpha_mid else Tok.alpha_full)
                x += widths[i]
            }
        }
        return render.tile(folder, "table", "free tokens, values in u", listOf("480 u 1080x2160, 520 u 1080x2340, 560 u Galaxy Z Flip7 main, all at ppu 4.5"), bmp, emptyList(), ppu)
    }

    @Test
    fun s4sStatusBand() {
        val f = "s4s"
        val q = "Android's status bar draws over funeOS's status band (statusTop + statusHeight, 22 u) and funeOS's page status strip is not drawn. The Galaxy Z Flip7's bar runs past the band."
        val index = mutableListOf("# Status band", "")
        for (dev in listOf(a105, p2160, flip7)) {
            val nav = dev.probed!!
            val over = dev.statusPx / dev.stdPpu - (Tok.statusTop + Tok.statusHeight)
            val note = "status bar ${"%.2f".format(dev.statusPx / dev.stdPpu)} u, band 22 u, " +
                if (over > 0) "${"%.2f".format(over)} u past the band" else "inside the band"
            val more: (Scale) -> List<Mark> = { s ->
                if (over > 0) listOf(Mark("overlap ${"%.1f".format(over)} u past the band", s.left, px(Tok.statusTop + Tok.statusHeight, s.stdPpu), s.left + s.widthPx, dev.statusPx, Tok.accent_h20))
                else emptyList()
            }
            val tiles = listOf(
                tile(f, "${dev.id}-hub", "hub page", dev, nav, Surface.Page, listOf(note), more = more, draw = ::hub),
                tile(f, "${dev.id}-sheet", "sheet (SH1) over the hub page", dev, nav, Surface.Page, listOf("SH1 keeps 22 u clear above the panel"), more = more) { ink, s ->
                    hub(ink, s)
                    Stubs.sheet(ink, contentBottom(s))
                },
                tile(f, "${dev.id}-jump", "jump grid (JG1)", dev, nav, Surface.Page, listOf("jumpTop ${Tok.jumpTop} u is inside the band"), more = more) { ink, s ->
                    hub(ink, s)
                    Stubs.jump(ink, contentBottom(s))
                },
                tile(f, "${dev.id}-keyboard", "keyboard band (KB1)", dev, nav, Surface.Page, listOf("kb.fieldTop ${Tok.kb_fieldTop} u is inside the band"), more = more) { ink, s ->
                    Stubs.keyboard(ink, contentBottom(s))
                },
            )
            render.write(Sheet(f, dev.id, listOf(q, cutoutRule, outlines), tiles))
            index += line(f, dev.id, "${dev.label}: the blue status bar against the white 22 u band, $note", q)
        }
        render.index(f, index)
    }

    @Test
    fun s5hButtons() = modes("s5h", Nav.Buttons, "# Home under the nav bar (Buttons)",
        "Under 3 button navigation pages lay out as NAV BAR ON (content to navTop, rings at npRingNavY), home lays out as NAV BAR OFF with Android's bar over its bottom.",
        mapOf("home" to "NAV BAR OFF under Android's bar", "page" to "NAV BAR ON: rows end at navTop", "np" to "NAV BAR ON: npRingNavY, npNextNavCount", "kb" to "keys end edge above navTop"),
        "home under the bar, pages to navTop")

    @Test
    fun s5gGesture() = modes("s5g", Nav.Gesture, "# Gesture navigation",
        "Under gesture navigation every surface lays out as NAV BAR OFF with the handle over the bottom; the back edge bands are dropped.",
        mapOf("home" to "NAV BAR OFF, handle over the bottom", "page" to "rows to H under the handle; back edge bands dropped", "np" to "npRingY, npNextCount; bottom edge band dropped", "kb" to "keys end edge above H"),
        "the handle and side gesture insets over NAV BAR OFF")

    @Test
    fun s5nNone() = modes("s5n", Nav.None, "# No navigation bar",
        "With no navigation bar (hardware key DAPs): NAV BAR OFF with the back edge bands kept, as funeOS.",
        mapOf("home" to "NAV BAR OFF", "page" to "rows to H; back edge bands kept", "np" to "npRingY, npNextCount", "kb" to "keys end edge above H"),
        "the white back edge bands kept")

    private fun modes(f: String, nav: Nav, head: String, q: String, notes: Map<String, String>, look: String) {
        val index = mutableListOf(head, "")
        for (dev in worked.filter { nav in it.modes }) {
            render.write(Sheet(f, dev.id, listOf(q, cutoutRule, outlines), surfaces(f, dev, nav, true, notes)))
            index += line(f, dev.id, "${dev.w}x${dev.h} ${dev.label}: $look", q)
        }
        render.index(f, index)
    }

    @Test
    fun q2NowPlayingArt() {
        val f = "q2"
        val q = "Now playing above 426.67 u: (a) the free token extrapolation, which is built, (b) art held at the A105 152.7 u with the spare above it, (c) art grown to W - 2 npArtX."
        val index = mutableListOf("# Now playing art above 426.67 u", "")
        for ((dev, nav) in listOf(p2160 to Nav.Gesture, d2340 to Nav.None, flip7 to Nav.Gesture)) {
            val b = TokFit.at(dev.h / dev.stdPpu, dev.stdPpu)
            val bottom = b.npArtTop + b.npArt
            val wide = Tok.canvasW - Ink.HALF * Tok.npArtX
            val options = listOf(
                Triple("(a) extrapolated", "a", Stubs.artOf(b)),
                Triple("(b) art held at 152.7 u", "b", Stubs.Art(bottom - TokBoard.a105.npArt, TokBoard.a105.npArt)),
                Triple("(c) art to W - 2 npArtX", "c", Stubs.Art(bottom - wide, wide)),
            )
            val case = "h${(dev.h / dev.stdPpu).toInt()}"
            val tiles = options.map { (name, id, art) ->
                tile(f, "$case-$id", name, dev, nav, Surface.NowPlaying,
                    listOf("art top ${"%.2f".format(art.top)} u, size ${"%.2f".format(art.size)} u, album baseline ${"%.2f".format(b.npAlbumY)} u, art bottom kept at ${"%.2f".format(bottom)} u")) { ink, s ->
                    Stubs.bg(ink)
                    Stubs.nowPlaying(ink, s.navOn, art)
                }
            }
            render.write(Sheet(f, case, listOf(q, cutoutRule, outlines), tiles))
            index += line(f, case, "${dev.label} at ${case.drop(1)} u: the art box and the gap to the album line in (a), (b), (c)", q)
        }
        render.index(f, index)
    }

    @Test
    fun q3OverlaysAndWideWindows() {
        val f = "q3"
        val q = "overlayAnchorScale (H / 480) and the other HR overlay places above 480 u (a) follow H, which is built, or (b) hold at 480 u; windows wider than 3:4 pillarbox the V1 canvas on every page."
        val index = mutableListOf("# Overlay places above 480 u and wide windows", "")
        for ((dev, nav) in listOf(d2340 to Nav.None, flip7 to Nav.Gesture)) {
            val case = "h${(dev.h / dev.stdPpu).toInt()}"
            val hold = TokFit.at(Tok.overlayAnchorH, dev.stdPpu)
            val tiles = listOf(
                tile(f, "$case-a", "(a) follow H", dev, nav, Surface.Page) { ink, s ->
                    hub(ink, s)
                    Stubs.volume(ink)
                },
                tile(f, "$case-b", "(b) hold at 480 u", dev, nav, Surface.Page, listOf("vol.* places from TokFit.at(480)")) { ink, s ->
                    hub(ink, s)
                    Stubs.volume(ink, hold)
                },
            )
            render.write(Sheet(f, case, listOf(q, "overlayAnchorScale at ${case.drop(1)} u: ${"%.4f".format(TokFit.at(dev.h / dev.stdPpu, dev.stdPpu).overlayAnchorScale)}", cutoutRule, outlines), tiles))
            index += line(f, case, "${dev.label}: the volume overlay places in (a) and (b)", q)
        }
        val dev = cover
        val nav = Nav.Gesture
        val tiles = listOf(
            tile(f, "cover-home", "home", dev, nav, Surface.Home) { ink, _ -> Stubs.bg(ink); Stubs.home(ink) },
            tile(f, "cover-hub", "hub page", dev, nav, Surface.Page, draw = ::hub),
            tile(f, "cover-sheet", "sheet (SH1)", dev, nav, Surface.Page) { ink, s -> hub(ink, s); Stubs.sheet(ink, contentBottom(s)) },
            tile(f, "cover-jump", "jump grid (JG1)", dev, nav, Surface.Page) { ink, s -> hub(ink, s); Stubs.jump(ink, contentBottom(s)) },
            tile(f, "cover-nowplaying", "now playing", dev, nav, Surface.NowPlaying) { ink, s -> Stubs.bg(ink); Stubs.nowPlaying(ink, s.navOn) },
            tile(f, "cover-volume", "volume overlay", dev, nav, Surface.Page) { ink, s -> hub(ink, s); Stubs.volume(ink) },
            tile(f, "cover-keyboard", "keyboard band (KB1)", dev, nav, Surface.Page) { ink, s -> Stubs.keyboard(ink, contentBottom(s)) },
        )
        render.write(Sheet(f, "cover", listOf(q, "Every page on the Galaxy Z Flip7 cover 948x1048, pillarboxed.", cutoutRule, outlines), tiles))
        index += line(f, "cover", "every page on the Galaxy Z Flip7 cover with its bottom cutout and the side bands", q)
        render.index(f, index)
    }

    @Test
    fun q4StatusRow() {
        val f = "q4"
        val q = "Now playing, lyrics and the saver (a) hide Android's status bar and draw funeOS's STATUS row, which is built, or (b) keep the system bar and drop the row."
        val index = mutableListOf("# Status row on now playing, lyrics and saver", "")
        val bar: (Mark) -> Boolean = { !it.label.startsWith("statusBars") }
        for (dev in listOf(a105, p2160, flip7)) {
            val nav = dev.probed!!
            val tiles = mutableListOf<Tile>()
            for ((surface, name) in listOf(Surface.NowPlaying to "now playing", Surface.Lyrics to "lyrics", Surface.Saver to "saver")) {
                for ((id, hide) in listOf("a" to true, "b" to false)) {
                    val option = if (hide) "$name (a) bar hidden, STATUS row" else "$name (b) system bar kept"
                    tiles += tile(f, "${dev.id}-${surface.name.lowercase()}-$id", option, dev, nav, surface, keep = { m -> !hide || bar(m) }) { ink, s ->
                        Stubs.bg(ink)
                        when (surface) {
                            Surface.NowPlaying -> Stubs.nowPlaying(ink, s.navOn)
                            Surface.Lyrics -> Stubs.lyrics(ink, s.navOn)
                            else -> Stubs.saver(ink)
                        }
                        if (hide) Stubs.status(ink)
                    }
                }
            }
            render.write(Sheet(f, dev.id, listOf(q, cutoutRule, outlines), tiles))
            index += line(f, dev.id, "${dev.label}: the STATUS row in (a) against the system bar in (b)", q)
        }
        render.index(f, index)
    }

    @Test
    fun navLarge() {
        val f = "navlarge"
        val q = "NAV BAR ON at SIZE LARGE: funeOS scales its nav band with the page (navH at the LARGE ppu) while Android's bar keeps its px. navTop is (a) funeOS's LARGE band or (b) Android's bar top, which is built."
        val dev = a105
        val nav = Nav.Buttons
        val tiles = listOf(
            tile(f, "standard", "STANDARD reference", dev, nav, Surface.Page, listOf("navTop at H - navH"), draw = ::hub),
            tile(f, "large-a", "(a) LARGE, funeOS band", dev, nav, Surface.Page, listOf("navTop at the LARGE H - navH"), large = true, draw = ::hub),
            tile(f, "large-b", "(b) LARGE, Android's bar top", dev, nav, Surface.Page, listOf("navTop at the bar top, ${dev.navPx} px"), large = true,
                more = { s -> listOf(Mark("content bottom at the bar top", s.left, dev.h - dev.navPx, s.left + s.widthPx, dev.h - dev.navPx + px(Tok.focus_border, s.ppu), Tok.accent_h20)) }) { ink, s ->
                Stubs.bg(ink)
                Stubs.hub(ink, s.stdContentBottom * s.stdPpu / s.ppu)
            },
        )
        render.write(Sheet(f, dev.id, listOf(q, cutoutRule, outlines), tiles))
        render.index(f, listOf("# NAV BAR ON at LARGE", "", line(f, dev.id, "A105 stock at LARGE: where the rows end against the green Android bar and the white funeOS band", q)))
    }

    @Test
    fun navShort() {
        val f = "navshort"
        val q = "NAV BAR ON at STANDARD with Android's bar shorter than funeOS's band (1080x2160: 132 px, navH 162 px). Pages end at (a) funeOS's band, H - navH, or (b) Android's bar top, as SIZE LARGE does, which is built."
        val dev = p2160Buttons
        val nav = Nav.Buttons
        val scrolled = "list scrolled half a row, so row 7 crosses the page end"
        fun scrolledHub(ink: Ink, bottom: Float) {
            Stubs.bg(ink)
            Stubs.hub(ink, ink.b.page_contentTop)
            ink.clip(ink.b.page_contentTop, bottom) { Stubs.rows(ink, ink.b.page_contentTop - Tok.rowCollection / 2, bottom) }
        }
        val tiles = listOf(
            tile(f, "standard-a", "(a) funeOS band", dev, nav, Surface.Page, listOf("page ends at H - navH", scrolled)) { ink, s ->
                scrolledHub(ink, contentBottom(s))
            },
            tile(f, "standard-b", "(b) Android's bar top", dev, nav, Surface.Page, listOf("page ends at the bar top, ${dev.navPx} px", scrolled)) { ink, s ->
                scrolledHub(ink, s.stdContentBottom * s.stdPpu / s.ppu)
            },
        )
        render.write(Sheet(f, dev.id, listOf(q, outlines), tiles))
        render.index(f, listOf("# NAV BAR ON with a shorter bar", "", line(f, dev.id, "1080x2160 3 button at STANDARD: where row 7 is cut against the green Android bar and the white funeOS band", q)))
    }

    companion object {
        const val ANDROID_9 = 28
        const val ANDROID_17 = 37
        const val A105_STATUS = 54
        const val A105_NAV = 108
        const val COVER_EDGE = 71
        const val COVER_CUTOUT = 839
        const val COVER_NAV = 934
        const val FREE_TOKENS = 31
    }
}
