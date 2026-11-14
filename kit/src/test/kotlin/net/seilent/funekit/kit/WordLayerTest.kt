package net.seilent.funekit.kit

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Test

class WordLayerTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(720, 1280, 28, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val v1 = scale(Window(240, 320, 28, none, none, none, none, none), Nav.None, Surface.Page, false)

    @Test
    fun aWordArrivingAtTheHeaderEndsClippedAtThePivotBandTop() {
        assertEquals(189, wordBottom(a105, 156f, 1f, 1f, toHeader = true))
        assertEquals(1280, wordBottom(a105, 156f, 1f, 0f, toHeader = true))
        assertEquals(735, wordBottom(a105, 156f, 1f, 0.5f, toHeader = true))
        assertEquals(58, wordBottom(v1, 52f, 1f, 1f, toHeader = true))
    }

    @Test
    fun aWordLeavingTheHeaderStartsClippedAtThePivotBandTop() {
        assertEquals(189, wordBottom(a105, 156f, 1f, 0f, toHeader = false))
        assertEquals(1280, wordBottom(a105, 156f, 1f, 1f, toHeader = false))
        assertEquals(173, wordBottom(a105, 156f, 0.5f, 0f, toHeader = false))
    }
}
