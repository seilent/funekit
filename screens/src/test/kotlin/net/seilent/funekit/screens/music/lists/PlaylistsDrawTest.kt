package net.seilent.funekit.screens.music.lists

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.lists.PlaylistsKey
import net.seilent.funekit.screens.music.lists.PlaylistsModel
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaylistsDrawTest {
    private val f = testFake()
    private val src = MusicSource(f.library, f.player, f.quickplay, f.favorites, f.playlists, f.lyrics)

    private fun model(): PlaylistsModel {
        val m = PlaylistsModel(0L)
        m.pane.geometry(RowSpec(PITCH, VIEW))
        val k = PlaylistsKey(f.playlists.revision.value, f.favorites.revision.value, f.player.state.value, f.library.generation.value)
        runBlocking { src.viewed { v -> m.load(v, k, 0L) } }
        return m
    }

    @Test
    fun everyRowsLookStaysEqualOverAHundredRestFrames() {
        val m = model()
        val first = (0 until m.size).map { m.look(it, AT, true) }
        for (i in 1 until FRAMES) assertEquals(first, (0 until m.size).map { m.look(it, AT + i * FRAME, true) })
        assertEquals(Tok.type_listPlaylist_alpha, first[0].alpha)
        assertEquals(Tok.type_listPlaylist_alphaDim, first[1].alpha)
    }

    @Test
    fun aFocusStepChangesTheLooksOfItsTwoRowsOnly() {
        val m = model()
        val before = (0 until m.size).map { m.look(it, AT, true) }
        m.pane.focus.set(1, m.size, AT, true)
        val end = AT + Tok.motion_focusAlpha.toLong() + FRAME
        val after = (0 until m.size).map { m.look(it, end, true) }
        assertNotEquals(before[0], after[0])
        assertNotEquals(before[1], after[1])
        for (r in 2 until m.size) assertEquals(before[r], after[r])
    }

    private companion object {
        const val PITCH = 165
        const val VIEW = 920
        const val AT = 1_000L
        const val FRAME = 16L
        const val FRAMES = 100
    }
}
