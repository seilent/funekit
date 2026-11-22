package net.seilent.funekit.screens.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqTextTest {
    @Test
    fun theGridIsTheSixthOctaveGridWithOneKhzAtPoint34() {
        val want = mapOf(0 to 20000, 1 to 22097, 4 to 31250, 28 to 500000, 33 to 890900, 34 to 1000000, 35 to 1122467, 36 to 1259918, 40 to 2000000, 58 to 16000000, 59 to 17959473, 60 to 20000000)
        for ((i, v) in want) assertEquals("grid point $i", v, eqGridMilliHz(i))
        assertEquals("a point under the grid is its first", 20000, eqGridMilliHz(-1))
        assertEquals("a point past the grid is its last", 20000000, eqGridMilliHz(61))
        assertEquals(34, eqGridOf(1000000))
        assertEquals("an off grid frequency has no point", -1, eqGridOf(1000001))
    }

    @Test
    fun frequenciesPrintInHzUnderOneKhzAndInTenthsOfAKhzFromIt() {
        assertEquals("31 hz", eqFreqText(31250, 4))
        assertEquals("891 hz", eqFreqText(890900, 33))
        assertEquals("1 khz", eqFreqText(1000000, 34))
        assertEquals("1.1 khz", eqFreqText(1122467, 35))
        assertEquals("1.3 khz", eqFreqText(1259918, 36))
        assertEquals("an off grid frequency that is no whole 100 hz prints in hz", "1150 hz", eqFreqText(1150000, -1))
        assertEquals("an off grid whole 100 hz prints in khz", "1.2 khz", eqFreqText(1200000, -1))
        assertEquals("105 hz", eqFreqText(105000, -1))
    }

    @Test
    fun gainQAndBandTextsAreTheUnitsWords() {
        assertEquals("+3.0 db", eqGainText(30, true))
        assertEquals("3.0 db", eqGainText(30, false))
        assertEquals("-6.1 db", eqGainText(-61, true))
        assertEquals("0.0 db", eqGainText(0, true))
        assertEquals("-0.5 db", eqGainText(-5, false))
        assertEquals("0.71", eqQText(71))
        assertEquals("1.40", eqQText(140))
        assertEquals("pk 1 khz +3.0 db q 0.71", eqBandText(EqBand(EqBandType.Peak, 1000000, 34, 30, 71)))
        assertEquals("ls 105 hz +6.4 db q 0.70", eqBandText(EqBand(EqBandType.LowShelf, 105000, -1, 64, 70)))
        assertEquals("off", eqBandText(EqBand(EqBandType.Off, 1000000, 34, 30, 71)))
        assertEquals("high shelf", eqTypeText(EqBandType.HighShelf, false))
        assertEquals("hs", eqTypeText(EqBandType.HighShelf, true))
    }

    @Test
    fun entriesStartFromTheValueAndParseInsideTheRangeOnly() {
        assertEquals("1000", eqEntryText(EqField.Freq, 1000000))
        assertEquals("-6.1", eqEntryText(EqField.Gain, -61))
        assertEquals("0.71", eqEntryText(EqField.Q, 71))
        assertEquals(-61, eqParse(EqField.Gain, true, "-6.1"))
        assertEquals(35, eqParse(EqField.Gain, false, "3.5"))
        assertNull("a gain takes one decimal", eqParse(EqField.Gain, false, "1.25"))
        assertNull("no plus sign", eqParse(EqField.Gain, false, "+3"))
        assertNull("a preamp stops at 0 db", eqParse(EqField.Gain, true, "1"))
        assertNull("a gain stops at +12 db", eqParse(EqField.Gain, false, "12.1"))
        assertEquals(1000000, eqParse(EqField.Freq, false, "1000"))
        assertNull("a frequency takes no sign", eqParse(EqField.Freq, false, "-5"))
        assertNull("a frequency starts at 20 hz", eqParse(EqField.Freq, false, "19"))
        assertEquals(71, eqParse(EqField.Q, false, "0.71"))
        assertNull("a q stops at 10", eqParse(EqField.Q, false, "11"))
        assertNull("a type is never typed", eqParse(EqField.Type, false, "1"))
        assertEquals("-24 to 0 db", eqRangeText(EqField.Gain, true))
        assertEquals("-24 to +12 db", eqRangeText(EqField.Gain, false))
        assertEquals("20 to 20000 hz", eqRangeText(EqField.Freq, false))
        assertEquals("0.1 to 10", eqRangeText(EqField.Q, false))
    }

    @Test
    fun stepsSnapToTheirStepAndClampToTheRange() {
        assertEquals(1, eqStepGain(0, 1, false))
        assertEquals(-1, eqStepGain(0, -1, false))
        assertEquals("a preamp step stops at 0 db", 0, eqStepGain(0, 1, true))
        assertEquals("a gain step stops at +12 db", 120, eqStepGain(120, 1, false))
        assertEquals("a q steps by 0.10 snapped up", 80, eqStepQ(71, 1))
        assertEquals("a q steps by 0.10 snapped down", 70, eqStepQ(71, -1))
        assertEquals("a q step stops at 0.1", 10, eqStepQ(10, -1))
        assertEquals("a frequency steps one grid point", 1122467, eqStepFreq(1000000, 34, 1))
        assertEquals("an off grid frequency moves to the next point up", 1259918, eqStepFreq(1150000, -1, 1))
        assertEquals("an off grid frequency moves to the next point down", 1122467, eqStepFreq(1150000, -1, -1))
        assertEquals("a frequency step stops at 20 hz", 20000, eqStepFreq(20000, 0, -1))
        assertTrue(eqAtEnd(EqField.Gain, true, 0, 1))
        assertFalse(eqAtEnd(EqField.Gain, false, 0, 1))
        assertTrue(eqAtEnd(EqField.Freq, false, 20000000, 1))
    }

    @Test
    fun aFreshBandIsAPeakAtOneKhzFlatWithQ140() {
        assertEquals(EqBand(EqBandType.Peak, 1000000, 34, 0, 140), freshBand)
    }
}
