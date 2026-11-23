package net.seilent.funekit.screens.host

import net.seilent.funekit.kit.quickplay.QP_PINS
import net.seilent.funekit.kit.quickplay.QP_PLAYING
import net.seilent.funekit.kit.quickplay.QpItem
import net.seilent.funekit.kit.quickplay.QpKind
import net.seilent.funekit.kit.quickplay.QpTile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QpResumeTest {
    private val song = QpItem(QpKind.Song, SONG, "Song", null)

    @Test
    fun thePlayingTileResumesWhateverPlays() {
        assertTrue(qpResumes(QpTile(QP_PLAYING, song), null, false))
        assertTrue(qpResumes(QpTile(QP_PLAYING, song), OTHER, true))
    }

    @Test
    fun aHistoryOrPinsTileOfTheCurrentSongResumes() {
        assertTrue(qpResumes(QpTile(HISTORY, song), SONG, false))
        assertTrue(qpResumes(QpTile(QP_PINS, song), SONG, false))
    }

    @Test
    fun anotherSongAnAlbumOfTheSameIdOrAPhoneTrackPlaysFromTheStart() {
        assertFalse(qpResumes(QpTile(HISTORY, song), OTHER, false))
        assertFalse(qpResumes(QpTile(HISTORY, song), null, false))
        assertFalse(qpResumes(QpTile(HISTORY, QpItem(QpKind.Album, SONG, "Album", null)), SONG, false))
        assertFalse(qpResumes(QpTile(HISTORY, song), SONG, true))
    }

    private companion object {
        const val SONG = 7L
        const val OTHER = 8L
        const val HISTORY = 1
    }
}
