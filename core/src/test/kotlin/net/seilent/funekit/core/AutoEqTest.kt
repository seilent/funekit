package net.seilent.funekit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqTest {
    @Test
    fun names() {
        assertEquals("Sennheiser HD 650", AutoEq.name("Sennheiser HD 650 ParametricEQ.txt"))
        assertEquals("Sennheiser HD 650", AutoEq.name("Sennheiser HD 650 GraphicEQ.TXT"))
        assertEquals("1Custom SA02", AutoEq.name("1Custom SA02"))
        assertTrue(AutoEq.validName("Sennheiser HD 650 (2020, worn earpads)"))
        assertTrue(AutoEq.validName("a".repeat(64)))
        assertFalse(AutoEq.validName("a".repeat(65)))
        assertFalse(AutoEq.validName("ア".repeat(22)))
        assertFalse(AutoEq.validName(""))
        assertFalse(AutoEq.validName("a/b"))
        assertFalse(AutoEq.validName(".hidden"))
        assertFalse(AutoEq.validName(" lead"))
        assertFalse(AutoEq.validName("tab\there"))
        assertFalse(AutoEq.validName("del\u007f"))
        assertFalse(AutoEq.validName("X ParametricEQ"))
        assertFalse(AutoEq.validName("Custom"))
        assertFalse(AutoEq.validName("FLAT"))
    }

    @Test
    fun namesFollowTheDeviceRule() {
        assertTrue(AutoEq.validName("\u3000HD 650"))
        assertTrue(AutoEq.validName("HD 650\u3000"))
        assertFalse(AutoEq.validName(" HD"))
        assertFalse(AutoEq.validName("HD "))
        assertFalse(AutoEq.validName("OFF"))
        assertTrue(AutoEq.validName("\uFF2Fff"))
        assertTrue(AutoEq.validName("\u00D6ff"))
        assertFalse(AutoEq.validName("a/b"))
        assertTrue(AutoEq.validName("ア".repeat(21) + "a"))
        assertFalse(AutoEq.validName("ア".repeat(21) + "ab"))
        assertEquals("sennheiser hd 650 \u00C9", AutoEq.foldKey("Sennheiser HD 650 \u00C9"))
    }
}
