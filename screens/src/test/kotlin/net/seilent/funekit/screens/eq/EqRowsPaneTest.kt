package net.seilent.funekit.screens.eq

import org.junit.Assert.assertEquals
import org.junit.Test

class EqRowsPaneTest {
    private val bands = listOf(EqBand(EqBandType.LowShelf, 105000, -1, 64, 70), EqBand(EqBandType.Peak, 1000000, 34, 30, 71))

    private val parametric = EqPivot(
        HP,
        HEADPHONES,
        listOf(
            EqRow(EqKind.On, "on"),
            EqRow(EqKind.Mode, "parametric"),
            EqRow(EqKind.Profile, "custom"),
            EqRow(EqKind.SavePreset),
            EqRow(EqKind.Import, title = "import autoeq"),
            EqRow(EqKind.Preamp),
            EqRow(EqKind.Band, band = 0),
            EqRow(EqKind.Band, band = 1),
            EqRow(EqKind.AddBand),
            EqRow(EqKind.RemoveBand),
        ),
        preamp10 = -61,
        bands = bands,
    )

    private val graphic = EqPivot(
        EAR_BT,
        BLUETOOTH,
        listOf(EqRow(EqKind.On, "off"), EqRow(EqKind.Mode, "graphic"), EqRow(EqKind.Preset, "flat"), EqRow(EqKind.AutoPreamp), EqRow(EqKind.Gain, band = 0), EqRow(EqKind.Gain, band = 1)),
        autoPreamp10 = -30,
        gains = listOf(EqGain(34, 45), EqGain(40, -20)),
    )

    private fun lines(p: EqPivot, header: String = EQ): List<EqPaneLine> =
        EqRowsPane(header, StillRows(listOf(p))).pivots.value.single().rows.map { it as EqPaneLine }

    @Test
    fun aPivotsRowsKeyBySlotKindAndBand() {
        assertEquals(
            listOf("hp:On", "hp:Mode", "hp:Profile", "hp:SavePreset", "hp:Import", "hp:Preamp", "hp:Band:0", "hp:Band:1", "hp:AddBand", "hp:RemoveBand"),
            lines(parametric).map { it.key },
        )
        assertEquals(listOf("earbluetooth:Gain:0", "earbluetooth:Gain:1"), lines(graphic).drop(4).map { it.key })
    }

    @Test
    fun theRowsTakeTheUnitsWordsAndTheSuppliersOwnTitle() {
        assertEquals(
            listOf("eq", "mode", "profile", "save preset", "import autoeq", "preamp", "1", "2", "add band", "remove band"),
            lines(parametric).map { it.title },
        )
        assertEquals("the on row takes the page header", "correction eq", lines(graphic, "correction eq").first().title)
        assertEquals("gain rows are titled by frequency", listOf("1 khz", "2 khz"), lines(graphic).drop(4).map { it.title })
    }

    @Test
    fun theNumberRowsPrintTheirNumbersAndTheRestTheSuppliersText() {
        assertEquals(
            listOf("on", "parametric", "custom", "", "", "-6.1 db", "ls 105 hz +6.4 db q 0.70", "pk 1 khz +3.0 db q 0.71", "", "2"),
            lines(parametric).map { it.value },
        )
        assertEquals(listOf("off", "graphic", "flat", "-3.0 db", "+4.5 db", "-2.0 db"), lines(graphic).map { it.value })
    }

    @Test
    fun thePivotsTakeTheSuppliersNamesInOrder() {
        val pane = EqRowsPane(EQ, StillRows(listOf(parametric, graphic)))
        assertEquals(listOf(HEADPHONES, BLUETOOTH), pane.pivots.value.map { it.name })
    }

    companion object {
        private const val EQ = "eq"
        private const val HP = "hp"
        private const val EAR_BT = "earbluetooth"
        private const val HEADPHONES = "headphones"
        private const val BLUETOOTH = "bluetooth"
    }
}
