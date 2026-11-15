package net.seilent.funekit.sheet

import net.seilent.funekit.Roles
import net.seilent.funekit.gen.Old
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import kotlin.math.min

object Stubs {
    private const val HALF = Ink.HALF
    private val pivots = listOf("albums", "artists", "songs")
    val homeItems = listOf("music", "playlists", "history", "apps", "settings")
    private val sheetItems = listOf("play", "play next", "add to now playing")
    private val jumpLetters = listOf("#") + ('a'..'z').map { it.toString() } + listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら")

    fun bg(ink: Ink) = ink.box(0f, 0f, ink.w, ink.h, Tok.color_bg)

    fun hub(ink: Ink, bottom: Float) {
        val b = ink.b
        ink.clip(0f, b.pivotTop) { ink.text(Roles.pageHeader, "music", Tok.page_headerX, Tok.page_headerBaseline) }
        var x = Tok.hubEdge
        x += ink.text(Roles.pivotActive, pivots.first(), x, b.page_pivotBaseline) + Tok.pivotActiveGap
        for (p in pivots.drop(1)) x += ink.text(Roles.pivotInactive, p, x, b.page_pivotBaseline) + Tok.pivotGap
        rows(ink, b.page_contentTop, bottom)
    }

    fun rows(ink: Ink, top: Float, bottom: Float) = ink.clip(top, bottom) {
        var t = top
        var i = 1
        while (t < bottom) {
            ink.ring(Tok.rowPlayRingX, t + Tok.rowPlayRing / HALF, Tok.rowPlayRing, Tok.ringStrokeIdle)
            ink.text(Roles.listCollection, "row $i", Tok.rowCollectionTextX, t + (Tok.rowPlayRing + Tok.capHeight * Roles.listCollection.size) / HALF)
            t += Tok.rowCollection
            i++
        }
    }

    fun sheet(ink: Ink, bottom: Float) {
        val title = ink.line(Roles.dialogTitle)
        val height = Tok.edge + title + sheetItems.size * Tok.settingsPitch + Tok.edge
        val top = bottom - height
        ink.box(0f, 0f, ink.w, top, Tok.color_black, Tok.scrim_sheet_alpha)
        ink.box(0f, top, ink.w, height, Tok.color_bgElevated)
        ink.text(Roles.dialogTitle, "row 1", Tok.edge, top + Tok.edge + ink.ascent(Roles.dialogTitle))
        sheetItems.forEachIndexed { i, s ->
            val mid = top + Tok.edge + title + i * Tok.settingsPitch + Tok.settingsPitch / HALF
            val alpha = if (i == 0) Tok.type_dialogItem_alpha else Tok.type_dialogItem_alphaDim
            ink.text(Roles.dialogItem, s, Tok.edge, mid + ink.centre(Roles.dialogItem), alpha)
        }
    }

    fun jump(ink: Ink, bottom: Float) {
        ink.box(0f, 0f, ink.w, ink.h, Tok.color_black, Tok.scrim_overlay_alpha)
        val cols = Tok.jumpCols.toInt()
        val cell = Tok.jumpPitch - Tok.jumpGap
        ink.clip(0f, bottom) {
            ink.text(Roles.jumpExit, "exit", Tok.jumpExitCx - ink.advance(Roles.jumpExit, "exit") / HALF, Tok.jumpExitTop + ink.ascent(Roles.jumpExit))
            for (k in Tok.jumpExitCells.toInt() until Tok.jumpCells.toInt()) {
                val x = Tok.jumpLeft + (k % cols) * Tok.jumpPitch
                val y = Tok.jumpTop + (k / cols) * Tok.jumpPitch
                ink.box(x, y, cell, cell)
                val s = jumpLetters[k - Tok.jumpExitCells.toInt()]
                ink.text(Roles.jumpCell, s, x + Tok.jumpLetterInsetX, y + cell - Tok.jumpLetterInsetBaseline)
            }
        }
    }

    class Art(val top: Float, val size: Float)

    fun artOf(b: TokBoard.Board) = Art(b.npArtTop, b.npArt)

    fun nowPlaying(ink: Ink, navOn: Boolean, art: Art = artOf(ink.b), phone: Boolean = false) {
        val b = ink.b
        ink.ring(Tok.npBackRingX + Tok.npBackRing / HALF, Tok.npBackRingY + Tok.npBackRing / HALF, Tok.npBackRing, Tok.npBackRingStroke)
        if (phone) ink.text(Roles.sectionLabel, "from phone", Tok.npTextX, b.npFromBaseline)
        ink.text(Roles.npArtist, "artist", Tok.npTextX, b.npArtistY)
        ink.text(Roles.npAlbum, "album", Tok.npTextX, b.npAlbumY)
        ink.box(Tok.npArtX, art.top, art.size, art.size)
        ink.box(Tok.npArtX, b.npProgressY, art.size, Tok.npBarHeight, Tok.color_pageHeader, Tok.npBarTrack)
        ink.text(Roles.time, "1:23", Tok.npArtX, b.npProgressY + Tok.npTimeBaseline)
        ink.text(Roles.npTitle, "title", Tok.npTextX, b.npTitleY)
        val count = (if (navOn) b.npNextNavCount else b.npNextCount).toInt()
        for (i in 0 until count)
            ink.text(Roles.npNext, "next ${i + 1}", Tok.npTextX + Tok.npNextIndent, b.npTitleY + Tok.npNextDrop + i * Tok.npNextLinePitch)
        val ringY = if (navOn) b.npRingNavY else b.npRingY
        for (cx in listOf(Tok.npRingCx0, Tok.npRingCx1, Tok.npRingCx2)) ink.ring(cx, ringY, Tok.npRing, Tok.npRingStroke)
    }

    fun lyrics(ink: Ink, navOn: Boolean) {
        val b = ink.b
        ink.ring(Tok.npBackRingX + Tok.npBackRing / HALF, Tok.npBackRingY + Tok.npBackRing / HALF, Tok.npBackRing, Tok.npBackRingStroke)
        ink.text(Roles.npArtist, "artist", Tok.npTextX, b.npArtistY)
        ink.text(Roles.npAlbum, "album", Tok.npTextX, b.npAlbumY)
        var base = b.npLyricBottom
        var i = 0
        while (base - Tok.npLyricPitch > b.npAlbumY) {
            val role = if (i == 0) Roles.npTitle else Roles.npNext
            ink.text(role, "lyric line", Tok.npTextX, base)
            base -= Tok.npLyricPitch
            i++
        }
        val ringY = if (navOn) b.npRingNavY else b.npRingY
        for (cx in listOf(Tok.npRingCx0, Tok.npRingCx1, Tok.npRingCx2)) ink.ring(cx, ringY, Tok.npRing, Tok.npRingStroke)
    }

    fun saver(ink: Ink) {
        val b = ink.b
        ink.box(0f, 0f, ink.w, ink.h, Tok.color_black)
        ink.text(Roles.saverClock, "12:00", Tok.edge, Tok.saverClockBaseline)
        ink.text(Roles.saverFourth(b), "title", Tok.edge, b.saverFourthBaseline)
    }

    fun status(ink: Ink) {
        val right = ink.w - Tok.npStatusRight
        val batteryX = right - Tok.npStatusBatteryW
        val mid = Tok.npStatusTop + Tok.npStatusHeight / HALF
        ink.frame(batteryX, mid - Tok.npStatusBatteryH / HALF, Tok.npStatusBatteryW, Tok.npStatusBatteryH, Tok.letterTileBorder)
        val clock = "12:00"
        val role = Roles.time
        ink.text(role, clock, batteryX - ink.b.npStatusGap - ink.advance(role, clock), mid + ink.centre(role))
    }

    fun home(ink: Ink) {
        val b = ink.b
        ink.ring(Tok.homeRingX, Tok.homeRingY, Tok.homeRingSize, Tok.homeRingStroke)
        homeItems.forEachIndexed { i, s -> ink.text(Roles.homeItem, s, Tok.homeTextX, Tok.homeFirstBaseline + i * Tok.homePitch) }
        ink.text(Roles.watermark, "fune", Tok.homeTextX, b.homeWatermarkBaseline)
    }

    fun volume(ink: Ink, v: TokBoard.Board = ink.b) {
        ink.box(0f, 0f, ink.w, ink.h, Tok.color_black, Tok.scrim_overlay_alpha)
        val cx = ink.w / HALF
        ink.text(Roles.volExit, "exit", Tok.vol_exitX, v.vol_exitY)
        val n = "12"
        ink.text(Roles.volNumber, n, cx - ink.advance(Roles.volNumber, n) / HALF, v.vol_numberY)
        ink.box(cx - Tok.vol_plus / HALF, v.vol_plusY - Tok.vol_plus / HALF, Tok.vol_plus, Tok.vol_plus)
        ink.ring(cx, v.vol_ringY, Tok.vol_ring, Tok.vol_ringStroke)
        for (sx in listOf(cx - Tok.vol_skipX, cx + Tok.vol_skipX))
            ink.box(sx - Tok.vol_skip / HALF, v.vol_ringY - Tok.vol_skip / HALF, Tok.vol_skip, Tok.vol_skip)
        ink.box(cx - Tok.vol_minus / HALF, v.vol_minusY - Tok.vol_minus / HALF, Tok.vol_minus, Tok.vol_minus)
        ink.text(Roles.volLabel, "title", Tok.vol_textX, v.vol_textY)
        ink.text(Roles.volLabel, "artist", Tok.vol_textX, v.vol_textY + v.vol_textGap)
    }

    fun keyboard(ink: Ink, bottom: Float) {
        val rows = KB_ROWS
        ink.box(0f, 0f, ink.w, ink.h, Tok.color_bgElevated)
        ink.frame(Tok.edge, Tok.kb_fieldTop, ink.w - Tok.edge * HALF, Tok.kb_fieldH, Tok.kb_fieldBorder)
        val kh = min(ink.b.kb_keyH, (bottom - Tok.edge - Tok.kb_top - (rows - 1) * Tok.qpGutter) / rows)
        for (r in 0 until rows) for (k in 0 until rows) {
            ink.box(Tok.edge + k * (Tok.kb_keyW + Tok.qpGutter), Tok.kb_top + r * (kh + Tok.qpGutter), Tok.kb_keyW, kh)
        }
    }

    private const val KB_ROWS = 4
}
