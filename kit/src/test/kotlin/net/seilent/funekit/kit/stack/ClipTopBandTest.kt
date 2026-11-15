package net.seilent.funekit.kit.stack

import androidx.compose.ui.graphics.drawscope.DrawScope
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Word
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipTopBandTest {
    private class Hub(override val scroll: Int, override val above: Int) : PagePaint {
        override val chrome: Chrome? = null
        override val header: Word? = null
        override val item = -1
        override val grounded = false
        override val kind = PageKind.Hub
        override val top = TOP
        override val bottom = BOTTOM
        override val count = ROWS

        override fun rowY(i: Int) = TOP + i * PITCH

        override fun DrawScope.ground(leave: Float, arrive: Float) = Unit

        override fun DrawScope.row(i: Int, y: Int, now: Long, rec: DrawRecord?, name: String) = Unit
    }

    @Test
    fun bandZeroOfAnR1ListStartsAtItsClipTop() {
        val p = Hub(UP, ABOVE)
        assertEquals(CLIP, p.clipTopPx())
        assertEquals(0, p.first())
        assertArrayEquals(intArrayOf(CLIP, 326, 432, 538, 644, 750, 800), p.bandTops())
        assertEquals(CLIP, p.bandsOrRest().y.first())
    }

    @Test
    fun aRowWhoseBottomIsAboveTheListTopButBelowTheClipTopIsDrawn() {
        val p = Hub(PITCH + UP, ABOVE)
        assertEquals(0, p.first())
        assertEquals(CLIP, p.bandTops()!!.first())
        assertEquals(1, Hub(PITCH + UP, 0).first())
        assertEquals(TOP, Hub(PITCH + UP, 0).bandTops()!!.first())
    }

    @Test
    fun anEmptyListBandsFromItsClipTop() {
        val p = object : PagePaint by Hub(0, ABOVE) {
            override val count = 0
        }
        assertArrayEquals(intArrayOf(CLIP, BOTTOM), p.bandsOrRest().y)
    }

    companion object {
        private const val TOP = 240
        private const val BOTTOM = 800
        private const val PITCH = 106
        private const val ROWS = 30
        private const val UP = 20
        private const val ABOVE = 37
        private const val CLIP = 203
    }
}
