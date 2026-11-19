package net.seilent.funekit.screens.music.overlay

import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.api.TrackInfo
import net.seilent.funekit.screens.music.hub.PATH_DOTS
import net.seilent.funekit.screens.music.hub.PATH_JOIN
import net.seilent.funekit.screens.music.hub.fitPath

private const val SEP = "/"

class InfoLines(val lines: List<String>, val pathRow: Int)

fun infoMax64(s: Scale): Int = pen64(s.w - Tok.edge, s.ppu) - pen64(Tok.edge, s.ppu)

fun infoLines(info: TrackInfo, max64: Int, width64: (String) -> Int): InfoLines {
    if (info.path.isEmpty()) return InfoLines(info.lines, -1)
    val path = fitPath(info.path.split(SEP).joinToString(PATH_JOIN), max64, PATH_DOTS, width64)
    return InfoLines(info.lines + path, info.lines.size)
}
