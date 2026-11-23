package net.seilent.funekit.screens.host

import net.seilent.funekit.kit.quickplay.qpMenuLook
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePanLookTest {
    @Test
    fun aPanDrawsTheMenuWithoutTheRingAndTheRingAtThePeekAlpha() {
        for (t in listOf(0f, 0.25f, 0.5f, 1f)) {
            val k = qpMenuLook(t)
            val menu = panMenuLook(t)
            assertEquals(0f, menu.ring, 0f)
            assertEquals(k.items, menu.items, 0f)
            assertEquals(k.sub, menu.sub, 0f)
            assertEquals(k.mark, menu.mark, 0f)
            val ring = panRingLook(1f)
            assertEquals(1f, ring.ring, 0f)
            assertEquals(0.5f, panRingLook(0.5f).ring, 0f)
            assertEquals(0f, ring.items, 0f)
            assertEquals(0f, ring.sub, 0f)
            assertEquals(0f, ring.mark, 0f)
        }
    }
}
