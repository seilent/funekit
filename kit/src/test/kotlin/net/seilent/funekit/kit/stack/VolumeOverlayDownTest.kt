package net.seilent.funekit.kit.stack

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VolumeOverlayDownTest {
    @Test
    fun aDownLandsNothingWhileTheHudRests() {
        val v = Vol()
        v.stack.touchDown()
        v.stack.frame(500)
        assertSame(v.o, v.stack.overlay)
        assertTrue(v.hud.visible(500))
        assertFalse(v.hud.hiding)
    }
}
