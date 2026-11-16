package net.seilent.funekit.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImgOriginTest {
    @Test
    fun theIdsAndWordsAreTheProtocolOnes() {
        assertEquals(listOf(0 to "library", 1 to "phone"), ImgOrigin.entries.map { it.id to it.word })
        assertEquals(ImgOrigin.PHONE, ImgOrigin.of("phone"))
        assertEquals(ImgOrigin.LIBRARY, ImgOrigin.of(0))
        assertNull(ImgOrigin.of("other"))
        assertNull(ImgOrigin.of(2))
    }
}
