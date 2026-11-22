package net.seilent.funekit.screens.settings

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.penFloor
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SettingsRowTest {
    private val none = Insets(0, 0, 0, 0)
    private val a105 = scale(Window(A105_W, A105_H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val density = Density(1f)

    private fun model() = SettingsModel(1L, SettingsId(SETTINGS))

    private fun draw() = settingsDraw(model(), a105, TextMeasurer(fonts, density, LayoutDirection.Ltr), fonts, density, null, Ink.white, true)

    private fun row(kind: SettingKind, values: List<String> = emptyList(), flags: Set<SettingFlag> = emptySet()) =
        SettingRow("key", "title", "value", kind, values = values, flags = flags)

    private fun keyed(name: String, vararg keys: String) = SettingPivot(name, keys.map { SettingRow(it, it, "") })

    @Test
    fun aRowIsEditableByItsKindUnlessReadOnlyAndADraftOnlyWithValues() {
        for (k in listOf(SettingKind.Choice, SettingKind.Steps, SettingKind.Toggle, SettingKind.Range)) {
            assertTrue("$k", row(k).editable)
            assertFalse("$k read only", row(k, flags = setOf(SettingFlag.ReadOnly)).editable)
        }
        for (k in listOf(SettingKind.Action, SettingKind.Info)) assertFalse("$k", row(k).editable)
        assertFalse("a draft with no values, as funeOS dateTime", row(SettingKind.Draft).editable)
        assertTrue("a draft with values", row(SettingKind.Draft, listOf("aac", "ldac")).editable)
        assertFalse("a draft with values read only", row(SettingKind.Draft, listOf("aac", "ldac"), setOf(SettingFlag.ReadOnly)).editable)
        assertTrue("restart, board only and confirm leave a row editable", row(SettingKind.Choice, flags = setOf(SettingFlag.Restart, SettingFlag.BoardOnly, SettingFlag.Confirm)).editable)
    }

    @Test
    fun aStatusWordTheRowShowsSplitsTheTitleAsAValueWould() {
        val d = draw()
        val plain = SettingRow("rescan", "rescan library", "", SettingKind.Action)
        assertEquals("premise: with no status word the title stays one line", listOf("rescan library"), d.ink(plain).lines)
        assertEquals(listOf("rescan", "library"), d.ink(plain.copy(shows = listOf("scanning"))).lines)
    }

    @Test
    fun aLiveWordDrawsInTheValueSlotAndTheValueStaysACandidate() {
        val d = draw()
        val r = SettingRow("bluetooth", "bluetooth", "on", SettingKind.Choice, values = listOf("off", "on"), words = listOf("off", "on"), live = "checking")
        assertEquals(listOf("CHECKING"), d.ink(r).value.map { it.text })
        assertEquals(listOf("ON"), d.ink(r.copy(live = null)).value.map { it.text })
        val rescan = SettingRow("rescan", "rescan library", "on", SettingKind.Action)
        assertEquals("premise: the live word alone leaves the title one line", listOf("rescan library"), d.ink(rescan).lines)
        assertEquals("the value under a live word still splits the title", listOf("rescan", "library"), d.ink(rescan.copy(value = "scanning", live = "on")).lines)
    }

    @Test
    fun aSpotSavedWithNoKeyFocusesNoRowByKey() {
        val m = model()
        m.restoreState(listOf(SYSTEM, arrayListOf<Any>(SYSTEM, "", 0f)))
        m.pivots = listOf(SettingPivot(SYSTEM, listOf(SettingRow("about", "about", "0.1.0"), SettingRow("", "keyless", ""))))
        assertEquals("the empty key is no key, so no row named by it takes the focus", 0, m.focusRow(0))
    }

    @Test
    fun aModelRestoredAndSavedBeforeItsFirstRowsKeepsItsSpots() {
        val m = model()
        val saved = listOf(EFFECTS, arrayListOf<Any>(EFFECTS, "crossfeed", 1.5f), arrayListOf<Any>(SYSTEM, "about", 0f))
        m.restoreState(saved)
        assertEquals(saved, m.state())
        m.pivots = listOf(SettingPivot(EFFECTS, listOf(SettingRow("eq", "eq", "off"), SettingRow("crossfeed", "crossfeed", "off"))))
        assertEquals("the rows landed, so the state is the landed pivots'", listOf(EFFECTS, arrayListOf<Any>(EFFECTS, "crossfeed", 1.5f)), m.state())
        assertEquals(1, m.focusRow(0))
    }

    @Test
    fun rowsThatLandEmptyKeepThePivotAndTheSpotsUntilRowsLandAgain() {
        val m = model()
        val rows = listOf(
            SettingPivot(EFFECTS, listOf(SettingRow("eq", "eq", "off"), SettingRow("crossfeed", "crossfeed", "off"))),
            SettingPivot(SYSTEM, listOf(SettingRow("lyrics", "lyrics online", "off"), SettingRow("about", "about", "0.1.0"))),
        )
        m.pivots = rows
        m.pivot = 1
        m.down(1, 0L)
        m.cancel(0L)
        val saved = m.state()
        m.pivots = emptyList()
        assertEquals("an empty landing keeps the spots", saved, m.state())
        m.pivots = rows
        assertEquals("the rows that land again take the pivot by its name", 1, m.pivot)
        assertEquals("and its focus by key", 1, m.focusRow(1))
        assertEquals(saved, m.state())
    }

    @Test
    fun aRemovedFocusedRowPassesItsFocusToTheRowInItsPlace() {
        val m = model()
        m.pivots = listOf(keyed(SYSTEM, "a", "b", "c"))
        m.down(1, 0L)
        m.pivots = listOf(keyed(SYSTEM, "a", "c"))
        assertEquals("the row in its place takes the focus", 1, m.focusRow(0))
        m.pivots = listOf(keyed(SYSTEM, "a", "b", "c"))
        assertEquals("and keeps it when the removed row lands again", 2, m.focusRow(0))
    }

    @Test
    fun aRestoredKeyTheFirstRowsLackWaitsForRowsThatHoldIt() {
        val m = model()
        m.pivots = listOf(keyed(SYSTEM, "a", "c"))
        m.restoreState(listOf(SYSTEM, arrayListOf<Any>(SYSTEM, "b", 0f)))
        m.pivots = listOf(keyed(SYSTEM, "a", "c", "d"))
        m.pivots = listOf(keyed(SYSTEM, "a", "b", "c"))
        assertEquals("the restored row takes the focus once it lands", 1, m.focusRow(0))
    }

    @Test
    fun aLandingThatMovesTheShownPivotLeavesTheNewPivotsKey() {
        val m = model()
        m.pivots = listOf(keyed(EFFECTS, "a", "b"), keyed(SYSTEM, "c", "d"))
        m.pivot = 1
        m.down(1, 0L)
        m.pivot = 0
        m.down(0, 0L)
        m.pivots = listOf(keyed(SYSTEM, "c"))
        assertEquals("premise: the shown pivot is gone, so the page shows system", listOf(SYSTEM), m.names)
        m.pivots = listOf(keyed(SYSTEM, "c", "d"))
        assertEquals("system keeps its own key", 1, m.focusRow(0))
    }

    @Test
    fun aSwatchRowDrawsItsSquareAtTheRightEdgeWithNoValueAndClipsItsTitleBeforeIt() {
        val d = draw()
        val k = d.ink(SettingRow("accent", "accent", HUE, SettingKind.Range, swatch = Ink.white))
        assertTrue("a swatch row draws no value text", k.value.isEmpty())
        val sq = requireNotNull(k.swatch)
        val side = px(Tok.settingsSwatch, a105.ppu)
        assertEquals(listOf(side, side), listOf(sq.w, sq.h))
        assertEquals("at the right edge", penFloor(d.measure.right64), sq.x1)
        assertEquals("centered in the row band", k.size.pad + (k.size.band - side) / 2, sq.y0)
        assertEquals("the title clips a gutter before it", sq.x0 - d.measure.gutter, k.clipX)
        assertEquals(sq.x0 - d.measure.gutter, k.glowClip.x1)
        assertTrue("a plain row has no swatch", d.ink(SettingRow("accent", "accent", HUE)).swatch == null)
    }

    @Test
    fun aSwatchTakesTheUneditedValueAlphaOverTheFocusedOne() {
        val m = model()
        m.pivots = listOf(SettingPivot(SYSTEM, listOf(SettingRow("about", "about", "0.1.0"), SettingRow("accent", "accent", "", SettingKind.Range, swatch = Ink.white))))
        assertEquals("premise: the focus is on the first row", 0, m.focusRow(0))
        m.edit("accent", false)
        val l = m.look(0, 1, 0L)
        assertEquals("premise: the edited value draws at full alpha", Tok.alpha_full, l.value, 0f)
        assertEquals(Tok.settingsValueDim / Tok.settingsValueFocused, l.swatch, 1e-4f)
        assertEquals(1f, m.look(0, 0, 0L).swatch, 1e-4f)
    }

    @Test
    fun aDownOnARowWithNoPressLookMovesTheFocusWithNoLitTitleAndNoGlow() {
        val m = model()
        m.pivots = listOf(SettingPivot(SYSTEM, listOf(SettingRow("b", "b", ""), SettingRow("a", "a", "", pressLook = false))))
        m.down(1, 0L)
        val unlit = m.look(0, 1, FRAME)
        assertEquals("the down moves the focus", 1, m.focusRow(0))
        assertTrue("the title takes the focus mix, below full alpha", unlit.title < Tok.alpha_full)
        assertFalse("no lit title", m.lit.on(1, FRAME))
        assertNull("no glow", unlit.glow)
        m.down(0, LATER)
        val pressed = m.look(0, 0, LATER + FRAME)
        assertEquals("a row with the press look lights", Tok.alpha_full, pressed.title, 0f)
        assertNotNull("and glows", pressed.glow)
        assertNull("the row with no press look has none", m.look(0, 1, LATER + FRAME).glow)
        assertTrue("premise: the pressed row is lit", m.lit.on(0, LATER + 2 * FRAME))
        m.down(1, LATER + 2 * FRAME)
        assertFalse("the down on the row with no press look ends the last press at once", m.lit.on(0, LATER + 3 * FRAME))
    }

    private companion object {
        const val A105_W = 720
        const val A105_H = 1280
        const val SDK = 35
        const val EFFECTS = "effects"
        const val SYSTEM = "system"
        const val HUE = "350"
        const val FRAME = 16L
        const val LATER = 1000L
    }
}
