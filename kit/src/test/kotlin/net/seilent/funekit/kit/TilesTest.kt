package net.seilent.funekit.kit

import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import org.junit.Assert.assertEquals
import org.junit.Test

class TilesTest {
    private val caption = Roles.boxCaption
    private val large = Role(1163919 / 65536f, caption.weight, caption.case, caption.alpha, caption.tracking)

    @Test
    fun theCaptionPitchScalesWithTheRoleInFixedPointTruncated() {
        assertEquals(792986L, Tiles.pitch(caption))
        assertEquals(951582L, Tiles.pitch(large))
    }

    @Test
    fun captionBaselinesRoundTheFixedPointSumOnce() {
        val t = Edges(0, 0, 1000, 1000)
        assertEquals(listOf(909, 945, 982), (0 until 3).map { Tiles.base(t, it, 3, caption, 3f) })
        assertEquals(listOf(895, 938, 982), (0 until 3).map { Tiles.base(t, it, 3, large, 3f) })
        assertEquals(listOf(965, 979, 994), (0 until 3).map { Tiles.base(t, it, 3, large, 1f) })
    }
}
