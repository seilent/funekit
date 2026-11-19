package net.seilent.funekit.screens.music.overlay

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.scale
import net.seilent.funekit.api.TrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackInfoTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val lines = listOf("Amber Field", "Folk", "1997", "flac", "96000 hz", "24 bit", "2 ch", "4608 kbps", "2:35", "89.3 mb")

    private fun each(s: String): Int = s.length * 64 * 10

    @Test
    fun thePathRowFollowsTheInfoLinesInMixedCase() {
        val info = infoLines(TrackInfo("Music/Amber Field/02 Slow Comet.flac", lines), Int.MAX_VALUE, ::each)
        assertEquals(lines + "Music / Amber Field / 02 Slow Comet.flac", info.lines)
        assertEquals(lines.size, info.pathRow)
    }

    @Test
    fun aPathWiderThanTheSheetFitsWithDots() {
        val max = infoMax64(a105)
        assertEquals((720 - 2 * 16 * 3) * 64, max)
        val info = infoLines(TrackInfo("Music/A Very Long Folder Name/Another Long Name/02 Slow Comet.flac", lines), max, ::each)
        val path = info.lines[info.pathRow]
        assertTrue(path, path.startsWith("..."))
        assertTrue(path, each(path) <= max)
        assertTrue(path, path.endsWith("02 Slow Comet.flac"))
    }

    @Test
    fun noPathGivesNoPathRow() {
        assertEquals(-1, infoLines(TrackInfo("", lines), Int.MAX_VALUE, ::each).pathRow)
    }
}
