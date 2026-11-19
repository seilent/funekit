package net.seilent.funekit.screens.posture

import androidx.window.layout.FoldingFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostureTest {
    private val half = FoldingFeature.State.HALF_OPENED
    private val flat = FoldingFeature.State.FLAT
    private val across = FoldingFeature.Orientation.HORIZONTAL
    private val down = FoldingFeature.Orientation.VERTICAL

    @Test
    fun aHalfOpenedHorizontalSeparatingFoldAcrossTheWindowIsAFoldAtItsBounds() {
        assertEquals(Fold(1260, 1260), Posture.of(half, across, true, 0, 1260, 1080, 1260, 1080, 2520))
        assertEquals(Fold(1250, 1270), Posture.of(half, across, true, 0, 1250, 1080, 1270, 1080, 2520))
    }

    @Test
    fun theZeroHeightHalfOpenedFormIsAFoldAndItsFlatFormIsNot() {
        assertEquals(Fold(1260, 1260), Posture.of(half, across, true, 0, 1260, 1080, 1260, 1080, 2520))
        assertNull(Posture.of(flat, across, false, 0, 1260, 1080, 1260, 1080, 2520))
    }

    @Test
    fun aFlatFoldIsNone() {
        assertNull(Posture.of(flat, across, true, 0, 1260, 1080, 1260, 1080, 2520))
    }

    @Test
    fun aVerticalFoldIsNone() {
        assertNull(Posture.of(half, down, true, 540, 0, 540, 2520, 1080, 2520))
        assertNull(Posture.of(half, down, true, 0, 1260, 1080, 1260, 1080, 2520))
    }

    @Test
    fun aFoldThatDoesNotSeparateIsNone() {
        assertNull(Posture.of(half, across, false, 0, 1260, 1080, 1260, 1080, 2520))
    }

    @Test
    fun aFoldNarrowerThanTheWindowIsNone() {
        assertNull(Posture.of(half, across, true, 10, 1260, 1080, 1260, 1080, 2520))
        assertNull(Posture.of(half, across, true, 0, 1260, 1070, 1260, 1080, 2520))
    }

    @Test
    fun aFoldOutsideTheWindowIsNone() {
        assertNull(Posture.of(half, across, true, 0, 2600, 1080, 2600, 1080, 2520))
        assertNull(Posture.of(half, across, true, 0, -10, 1080, -10, 1080, 2520))
        assertNull(Posture.of(half, across, true, 0, 0, 1080, 0, 1080, 2520))
        assertNull(Posture.of(half, across, true, 0, 2520, 1080, 2520, 1080, 2520))
    }
}
