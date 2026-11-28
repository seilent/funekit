package net.seilent.funekit.screens.eq

import net.seilent.funekit.api.engine.EqLevels
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.settings.FieldSheet
import net.seilent.funekit.screens.settings.RowAdjust
import net.seilent.funekit.screens.settings.SettingSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun leveled(levels: EqLevels, on: Boolean = true, listed: Boolean = true, rows: List<EqRow> = parametric.rows): EqPivot =
        parametric.copy(rows = eqLevelRows(rows, levels, on, listed), levels = levels)

    private fun valueOf(p: EqPivot, kind: EqKind): String = lines(p).single { it.kind == kind }.value

    private class HudActs(private val hud: AdjustHud) : EqActs {
        var made: RowAdjust? = null

        override fun now(): Long = 0L

        override fun sheet(sheet: SettingSheet, pick: (Int) -> Boolean, closed: () -> Unit, keep: Boolean): Boolean = false

        override fun fields(sheet: FieldSheet): Boolean = false

        override fun adjust(make: (AdjustHud, (Boolean) -> Unit) -> RowAdjust): Boolean = make(hud) {}.also { made = it }.open(0L)

        override fun focus(key: String) {}

        override fun toast(line1: String, line2: String) {}

        override fun close() {}

        override fun handOff() {}

        override fun launch(block: suspend () -> Unit) {}
    }

    private fun hudLine(p: EqPivot): String {
        val hud = AdjustHud()
        val pane = EqRowsPane(EQ, StillRows(listOf(p)))
        val pivot = pane.pivots.value.single()
        pane.tap(pivot.name, pivot.rows.first { (it as EqPaneLine).kind == EqKind.Preamp }, HudActs(hud))
        return hud.line2
    }

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
    fun updatePresetTakesItsTitleAndTheSuppliersValue() {
        val l = lines(parametric.copy(rows = parametric.rows + EqRow(EqKind.UpdatePreset, PRESET))).last()
        assertEquals(listOf("hp:UpdatePreset", "update preset", PRESET), listOf(l.key, l.title, l.value))
    }

    @Test
    fun thePivotsTakeTheSuppliersNamesInOrder() {
        val pane = EqRowsPane(EQ, StillRows(listOf(parametric, graphic)))
        assertEquals(listOf(HEADPHONES, BLUETOOTH), pane.pivots.value.map { it.name })
    }

    @Test
    fun aCappedCurveListsTotalEqOffAtItsRate() {
        assertEquals("eq off at 88.2 khz", valueOf(leveled(EqLevels(level = 1, rateHz = 88200)), EqKind.Total))
        assertEquals("eq off at 96 khz", valueOf(leveled(EqLevels(level = 2, rateHz = 96000)), EqKind.Total))
    }

    @Test
    fun aDsdCapReadsItsDsdRate() {
        assertEquals("eq off at dsd64", valueOf(leveled(EqLevels(level = 2, dsd = 1)), EqKind.Total))
        assertEquals("eq off at dsd128", valueOf(leveled(EqLevels(level = 1, dsd = 2)), EqKind.Total))
    }

    @Test
    fun aListedCurveWithEqOnListsPeakGainAfterTotal() {
        val p = leveled(EqLevels(level = 1, rateHz = 44100, peak10 = -15))
        assertEquals(
            listOf(
                EqKind.On, EqKind.Mode, EqKind.Profile, EqKind.SavePreset, EqKind.Import, EqKind.Preamp, EqKind.Total, EqKind.Peak,
                EqKind.Band, EqKind.Band, EqKind.AddBand, EqKind.RemoveBand,
            ),
            p.rows.map { it.kind },
        )
        assertEquals(listOf("total", "peak gain"), lines(p).filter { it.kind == EqKind.Total || it.kind == EqKind.Peak }.map { it.title })
        assertEquals("-1.5 db", valueOf(p, EqKind.Peak))
    }

    @Test
    fun peakGainReadsZeroAtLevelTwo() {
        assertEquals("0.0 db", valueOf(leveled(EqLevels(level = 2, rateHz = 44100, peak10 = 35)), EqKind.Peak))
    }

    @Test
    fun aDopCurveListsTotalEqOffForDopAndThePeakFromTheTrim() {
        val dop = EqLevels(dop = true, dopTrim = 3, peak10 = 12)
        val p = leveled(dop)
        assertEquals("eq off for dop", valueOf(p, EqKind.Total))
        assertEquals("-1.5 db", valueOf(p, EqKind.Peak))
        assertTrue("eq off lists neither", leveled(dop, on = false).rows.none { it.kind == EqKind.Total || it.kind == EqKind.Peak })
    }

    @Test
    fun anUnknownPeakListsPeakGainWithNoValue() {
        val p = leveled(EqLevels(rateHz = 44100))
        assertTrue("no total", p.rows.none { it.kind == EqKind.Total })
        assertEquals(EqKind.Peak, p.rows[p.rows.indexOfFirst { it.kind == EqKind.Preamp } + 1].kind)
        assertEquals("", valueOf(p, EqKind.Peak))
    }

    @Test
    fun totalGoesLastWithNoPreamp() {
        val off = listOf(EqRow(EqKind.On, "on"), EqRow(EqKind.Profile, "off"), EqRow(EqKind.Import))
        val p = leveled(EqLevels(level = 2, rateHz = 44100), listed = false, rows = off)
        assertEquals(listOf(EqKind.On, EqKind.Profile, EqKind.Import, EqKind.Total), p.rows.map { it.kind })
        assertEquals("eq off at 44.1 khz", valueOf(p, EqKind.Total))
    }

    @Test
    fun thePreampHudTakesThePeakLine() {
        assertEquals("PEAK GAIN -1.5 DB", hudLine(leveled(EqLevels(rateHz = 44100, peak10 = -15))))
        assertEquals("a capped curve takes the total", "EQ OFF AT 44.1 KHZ", hudLine(leveled(EqLevels(level = 1, rateHz = 44100))))
        assertEquals("no peak yet keeps the preamp line", "PREAMP -6.1 DB", hudLine(leveled(EqLevels(rateHz = 44100))))
        assertEquals("the supplier's own line wins", "SUPPLIED", hudLine(leveled(EqLevels(peak10 = -15)).copy(peak = "supplied")))
    }

    @Test
    fun levelsLandingUnderTheOpenPreampHudMoveItsSecondLine() {
        val rows = StillRows(listOf(leveled(EqLevels(rateHz = 44100))))
        val hud = AdjustHud()
        val acts = HudActs(hud)
        val pane = EqRowsPane(EQ, rows)
        val pivot = pane.pivots.value.single()
        pane.tap(pivot.name, pivot.rows.first { (it as EqPaneLine).kind == EqKind.Preamp }, acts)
        assertEquals("premise: no peak yet", "PREAMP -6.1 DB", hud.line2)
        rows.pivots.value = listOf(leveled(EqLevels(rateHz = 44100, peak10 = -15)))
        checkNotNull(acts.made).landed()
        assertEquals("PEAK GAIN -1.5 DB", hud.line2)
        rows.pivots.value = listOf(leveled(EqLevels(level = 1, rateHz = 44100, peak10 = -15)))
        checkNotNull(acts.made).landed()
        assertEquals("a cap landing takes the total", "EQ OFF AT 44.1 KHZ", hud.line2)
    }

    @Test
    fun nullLevelsAndUncappedLevelsWithEqOffReturnTheRowsAsTheyCame() {
        assertEquals(parametric.rows, eqLevelRows(parametric.rows, null, on = true, listed = true))
        assertEquals(parametric.rows, eqLevelRows(parametric.rows, EqLevels(), on = false, listed = true))
        assertEquals("the supplier's own text stays", "supplied", lines(parametric.copy(rows = parametric.rows + EqRow(EqKind.Total, "supplied"))).last().value)
    }

    companion object {
        private const val EQ = "eq"
        private const val HP = "hp"
        private const val EAR_BT = "earbluetooth"
        private const val HEADPHONES = "headphones"
        private const val BLUETOOTH = "bluetooth"
        private const val PRESET = "HD 650"
    }
}
