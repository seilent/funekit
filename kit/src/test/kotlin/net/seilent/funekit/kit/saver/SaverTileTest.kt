package net.seilent.funekit.kit.saver

import androidx.compose.ui.graphics.ImageBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SaverTileTest {
    private val due = 20000L

    @Test
    fun theLayAsksItsScenesTilesBeforeTheirLanesStart() {
        val asks = ArrayList<Pair<Long, SaverTile>>()
        var clock = 0L
        val q = Queue()
        val e = a105().kit()
        e.setArt(ImageBitmap(4, 4))
        val lay = SaverLay(e, q) { asks += clock to it }
        e.pinNext(SaverScene.K)
        clock = due - 1000
        lay.ahead(clock, due)
        q.drain()
        e.show(due, due)
        assertEquals(1, lay.takes)
        val tiles = e.lanes.filter { it.spec.src == Src.Art && !it.skip }
        assertTrue(tiles.isNotEmpty())
        assertEquals(tiles.size, asks.size)
        for ((i, l) in tiles.withIndex()) {
            assertTrue(l.job)
            assertTrue(asks[i].first < l.t0)
            assertEquals(e.px(l.spec.size), asks[i].second.w)
            assertEquals(if (l.spec.tall != 0L) e.px(l.spec.tall) else e.px(l.spec.size), asks[i].second.h)
        }
        val turned = a105().kit()
        turned.setArt(ImageBitmap(4, 4))
        val seen = ArrayList<SaverTile>()
        SaverLay(turned, q) { seen += it }
        turned.show(0, 1)
        turned.shotScene(SaverScene.B, 0)
        assertTrue(seen.any { it.angle == DIAG } && seen.any { it.angle == -DIAG })
    }

    @Test
    fun anAbsentArtSkipsItsTileLaneAtItsTime() {
        val e = a105().kit()
        e.show(0, 1)
        e.shotScene(SaverScene.K, 0)
        val tiles = e.lanes.filter { it.spec.src == Src.Art && !it.skip }
        assertTrue(tiles.isNotEmpty())
        for (l in tiles) {
            e.step(maxOf(l.t0, e.laneOn(l)))
            assertTrue(l.skip)
        }
        val late = a105().kit()
        val asks = ArrayList<SaverTile>()
        SaverLay(late, Queue()) { asks += it }
        late.show(0, 1)
        late.shotScene(SaverScene.K, 0)
        late.setArt(ImageBitmap(4, 4))
        val lane = late.lanes.first { it.spec.src == Src.Art && !it.skip }
        late.step(maxOf(lane.t0, late.laneOn(lane)))
        assertTrue(lane.job && !lane.skip && asks.isNotEmpty())
    }
}
