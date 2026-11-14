package net.seilent.funekit.sheet

import android.graphics.Rect
import net.seilent.funekit.A105_PPI
import net.seilent.funekit.Dpi
import net.seilent.funekit.Nav
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.px

object Phones {
    const val P2160_PPI = 403.411f
    const val FLIP7_PPI = 397.3f
    const val ZENFONE9_PPI = 446.1f
    const val ANDROID_14 = 34
    const val ANDROID_17 = 37
    const val P2160_BAR = 66
    const val P2160_NAV = 132
    const val P2160_EDGE = 82
    const val P2160_MANDATORY = 2072
    const val FLIP7_STATUS = 109
    const val FLIP7_NAV = 39
    const val FLIP7_EDGE = 78
    private const val P2160_STABLE = 440
    private const val NOT_READ = 0
    private val phone = listOf(Nav.Buttons, Nav.Gesture)

    val a105 = Dev("720x1280", "A105", 720, 1280, listOf(Nav.None))
    val p2160 = Dev(
        "1080x2160", "Mi MIX 2S", 1080, 2160, phone, "Mi MIX 2S probed 2026-11-09", Nav.Gesture,
        sdk = ANDROID_14, statusPx = P2160_BAR, navPx = P2160_BAR, gesturePx = P2160_EDGE,
        extra = listOf(Mark("mandatorySystemGestures ${2160 - P2160_MANDATORY} px", 0, P2160_MANDATORY, 1080, 2160, Tok.accent_h50)),
    )
    val p2160Buttons = Dev(
        "1080x2160", "Mi MIX 2S 3 button", 1080, 2160, listOf(Nav.Buttons), "Mi MIX 2S probed 2026-11-11", Nav.Buttons,
        sdk = ANDROID_14, statusPx = P2160_BAR, navPx = P2160_NAV,
    )
    val flip7 = Dev(
        "1080x2520", "Galaxy Z Flip7 main", 1080, 2520, phone, "Galaxy Z Flip7 probed 2026-11-09", Nav.Gesture,
        sdk = ANDROID_17, statusPx = FLIP7_STATUS, navPx = FLIP7_NAV, gesturePx = FLIP7_EDGE, cutout = Rect(0, 0, 1080, FLIP7_STATUS),
    )
    val zenfone9 = Dev("1080x2400", "Zenfone 9", 1080, 2400, listOf(Nav.None))

    private val p2160Dpi = Dpi(P2160_PPI, P2160_PPI, P2160_STABLE, 1080, 2160, 1, 1080, 2160)
    val p2160Page = p2160.withDpi(p2160Dpi)
    val p2160ButtonsPage = p2160Buttons.withDpi(p2160Dpi)
    val flip7Page = flip7.withDpi(Dpi(FLIP7_PPI, FLIP7_PPI, NOT_READ, 1080, 2520, 1, 1080, 2520))
    val zenfone9Page = zenfone9.withDpi(Dpi(ZENFONE9_PPI, ZENFONE9_PPI, NOT_READ, 1080, 2400, 1, 1080, 2400))

    fun ppi(d: Dev): Float = d.dpi?.x ?: A105_PPI

    private fun source(d: Dev): String = when (d) {
        p2160Page, p2160ButtonsPage -> "ppi probed 2026-11-11"
        flip7Page -> "ppi from the panel spec, not read on the device; bars and cutout probed 2026-11-09"
        zenfone9Page -> "ppi from the panel spec, not read on the device; bars and cutout not probed, so none drawn"
        else -> "ppi from the panel spec"
    }

    fun nav(d: Dev): Nav = d.probed ?: Nav.None

    fun sheetPpu(d: Dev): Float = p2160.stdPpu * ppi(d) / P2160_PPI

    private fun colour(c: Long): String = when (c) {
        Tok.accent_default -> "red"
        Tok.accent_h20 -> "orange"
        Tok.accent_h50 -> "yellow"
        Tok.accent_h120 -> "green"
        Tok.accent_h200 -> "blue"
        Tok.accent_h270 -> "violet"
        Tok.accent_h300 -> "magenta"
        else -> "white"
    }

    private fun outlines(d: Dev, s: Scale): List<Mark> {
        val line = px(Tok.canvasW, s.ppu)
        val canvas = if (line < s.widthPx) listOf(Mark("funeOS canvasW ${Tok.canvasW.toInt()} u at this ppu, x $line px", line, 0, line + 1, s.heightPx, Tok.color_pageHeader)) else emptyList()
        return d.marks(nav(d)) + canvas
    }

    fun facts(d: Dev, s: Scale): List<String> {
        val base = if (s.fit != null) "page base" else "fit base"
        val facts = "${d.w}x${d.h} ${d.label}, ${nav(d).name}, $base ${"%.5f".format(s.stdPpu)} px/u, ppu ${"%.5f".format(s.ppu)}, " +
            "canvas ${"%.2f".format(s.w)} x ${"%.2f".format(s.h)} u, ${ppi(d)} ppi, ${source(d)}"
        return listOf(facts) + outlines(d, s).distinctBy { it.label }.map { "${it.label} (${colour(it.color)} outline on marks.png)" }
    }

    fun marks(d: Dev, s: Scale): List<Mark> = outlines(d, s).map { Mark("", it.l, it.t, it.r, it.b, it.color) }

    const val MARKS = "Where the system bands sit: Android's bars, gesture bands and cutout as probed, and the funeOS canvasW line, each outline's label in the caption list with its colour. The other sheets draw none of them."

    const val SAME_SIZE = "Every tile is drawn at the Mi MIX 2S's px per mm: a tile's px are scaled by its ppi against the Mi MIX 2S's 403.411, so a u looks as large on the sheet as on the device."
}
