package net.seilent.funekit.screens.host

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.CoroutineScope
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.scale
import net.seilent.funekit.screens.settings.AimKind
import net.seilent.funekit.screens.settings.HeaderLook
import net.seilent.funekit.screens.settings.SETTINGS
import net.seilent.funekit.screens.settings.SettingFlag
import net.seilent.funekit.screens.settings.SettingKind
import net.seilent.funekit.screens.settings.SettingPivot
import net.seilent.funekit.screens.settings.SettingRow
import net.seilent.funekit.screens.settings.SettingSheet
import net.seilent.funekit.screens.settings.SettingsId
import net.seilent.funekit.screens.settings.SettingsModel
import net.seilent.funekit.screens.settings.SettingsPage
import net.seilent.funekit.screens.settings.SettingsRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class SettingsHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val page = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)

    private class Rows(start: List<SettingPivot>) : SettingsRows {
        override val pivots = mutableStateOf(start)
        val opens = mutableListOf<String>()
        val sets = mutableListOf<Map<String, String>>()
        val actions = mutableListOf<String>()
        var opened: Set<String> = emptySet()
        var onSet: () -> Unit = {}

        override fun open(row: SettingRow): Boolean {
            opens += row.key
            return row.key in opened
        }

        override suspend fun set(key: String, values: List<String>): Boolean {
            sets += mapOf(key to values.single())
            onSet()
            return true
        }

        override fun action(key: String) {
            actions += key
        }
    }

    private fun choice(key: String, words: List<String> = WORDS) = SettingRow(key, key, words[0], SettingKind.Choice, values = words, words = words, at = 0)

    private fun pivot(vararg rows: SettingRow) = listOf(SettingPivot(OUT, rows.toList()))

    private fun app(vararg pages: SettingsPage) = TestApp(page, page, null, StillLevel) { rule.mainClock.currentTime }.also {
        it.settingsList = pages.toList()
        it.compose(rule)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun open(app: TestApp, id: SettingsId = ID): Pair<TestScreen, SettingsModel> {
        val sc = TestScreen.Settings(app.stack.entry(), id)
        rule.runOnIdle { app.stack.reset(listOf(sc)) }
        frames(OPEN_FRAMES)
        val m = app.models[sc.entry] as SettingsModel
        assertEquals("premise: the settings page is up", sc, app.stack.top)
        return sc to m
    }

    private fun tap(app: TestApp, m: SettingsModel, r: Int) {
        rule.runOnIdle { app.host.settingsTap(m, m.rows(m.pivot)[r]) }
        frames(OPEN_FRAMES)
    }

    private fun dragging(): ListTouch = ListTouch(Kind.List, LazyListState(), page, CoroutineScope(EmptyCoroutineContext)).also {
        it.scroll.begin(DRAG_Y)
        assertTrue("premise: the list drags", it.scroll.dragging)
    }

    private fun closed(app: TestApp): Boolean = app.stack.overlay.let { it == null || it.closing }

    @Test
    fun pageModelsBuildsASettingsModelForASettingsPage() {
        val app = TestApp(page, page, null, StillLevel) { 0L }
        assertTrue(app.models.enter(ENTRY, ID) is SettingsModel)
    }

    @Test
    fun aScreenNamingAPageTheAppDoesNotListFailsAtEnter() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings))
        val other = TestScreen.Settings(app.stack.entry(), SettingsId(OTHER))
        val refused = rule.runOnIdle { runCatching { app.host.enter(other) }.exceptionOrNull() }
        assertTrue("the enter refuses, $refused", refused is IllegalArgumentException)
    }

    @Test
    fun theStackTakesTheHubFormForASettingsEntryThroughTheHost() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings))
        val (sc, _) = open(app)
        assertEquals(PageKind.Hub, app.host.kind(sc))
    }

    @Test
    fun settingsPopsToHomeWithT4hAsTheHomeItemItOpensFrom() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings))
        app.homeWords = setOf(SETTINGS)
        val t = Turn<TestScreen>(MoveKind.None, TestScreen.Settings(ENTRY, ID), null, popping = true)
        assertEquals(MoveKind.T4h, app.host.popKind(t))
    }

    @Test
    fun aSupplierRowChangeWithNoOtherAppChangeLandsTheRowsAndTheNames() {
        val rows = Rows(pivot(choice("a")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        assertEquals("premise: one pivot", listOf(OUT), m.names)
        rule.runOnIdle { rows.pivots.value = listOf(SettingPivot(OUT, listOf(choice("a").copy(value = NEW))), SettingPivot(SYS, listOf(choice("b")))) }
        frames(RESIZE_FRAMES)
        val r = m.rows(0)[0]
        assertEquals(NEW, r.value)
        assertEquals(listOf(OUT, SYS), m.names)
        assertEquals("the chrome takes the new names", 2, app.host.types.settings.chromes.getValue(ID).count)
        assertEquals("the drawn row shows the new value", listOf(NEW.uppercase()), rule.runOnIdle { app.host.types.settings.drawOf(m).ink(r).value.map { it.text } })
    }

    @Test
    fun aSupplierWriteBetweenTheCompositionAndItsSideEffectLandsTheListTheCompositionRead() {
        val rows = Rows(pivot(choice("a")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        val read = listOf(SettingPivot(OUT, listOf(choice("a"))), SettingPivot(SYS, listOf(choice("b"))))
        val late = read + SettingPivot(LIB, listOf(choice("c")))
        rule.runOnIdle {
            app.beforeApplied = {
                app.beforeApplied = {}
                rows.pivots.value = late
            }
            rows.pivots.value = read
        }
        frames(1)
        assertEquals("the side effect lands the list the composition read", listOf(OUT, SYS), m.names)
        assertEquals("with the chrome of the same names", 2, app.host.types.settings.chromes.getValue(ID).count)
        frames(RESIZE_FRAMES)
        assertEquals("the later write lands with the next composition", listOf(OUT, SYS, LIB), m.names)
    }

    @Test
    fun aPageListedAheadOfAPageKeepsTheLattersChromeAndItsPivot() {
        val b = SettingsPage(ID, Rows(listOf(SettingPivot(OUT, listOf(choice("a"))), SettingPivot(SYS, listOf(choice("b"))))), HeaderLook.Settings)
        val app = app(b)
        frames(1)
        val c = app.host.types.settings.chromes.getValue(ID)
        rule.runOnIdle { c.select(1) }
        rule.runOnIdle { app.settingsList = listOf(SettingsPage(SettingsId(OTHER), Rows(pivot(choice("c"))), HeaderLook.Page), b) }
        frames(RESIZE_FRAMES)
        val kept = app.host.types.settings.chromes.getValue(ID)
        assertSame(c, kept)
        assertEquals(1, kept.active)
    }

    @Test
    fun eachPageDrawsTheHeaderLookItNames() {
        val app = app(
            SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings),
            SettingsPage(SettingsId(PAGE), Rows(pivot(choice("b"))), HeaderLook.Page),
            SettingsPage(SettingsId(DEFAULT), Rows(pivot(choice("c"))), HeaderLook.Default),
        )
        frames(1)
        val chromes = app.host.types.settings.chromes
        val settings = chromes.getValue(ID)
        assertEquals(Tok.page_settingsHeaderX, settings.headerPen, 0f)
        assertEquals(Tok.page_settingsHeaderAlpha, settings.headerAlpha, 0f)
        val pageLook = chromes.getValue(SettingsId(PAGE))
        assertEquals(Tok.page_headerX, pageLook.headerPen, 0f)
        assertEquals(Tok.page_settingsHeaderAlpha, pageLook.headerAlpha, 0f)
        val chromeDefault = chromes.getValue(SettingsId(DEFAULT))
        assertEquals(Tok.page_headerX, chromeDefault.headerPen, 0f)
        assertEquals(Roles.pageHeader.alpha, chromeDefault.headerAlpha, 0f)
    }

    @Test
    fun aPageWhoseLookTurnsDefaultTakesTheChromeDefaultsBack() {
        val rows = Rows(pivot(choice("a")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        frames(1)
        assertEquals("premise: the settings look", Tok.page_settingsHeaderX, app.host.types.settings.chromes.getValue(ID).headerPen, 0f)
        rule.runOnIdle { app.settingsList = listOf(SettingsPage(ID, rows, HeaderLook.Default)) }
        frames(RESIZE_FRAMES)
        val c = app.host.types.settings.chromes.getValue(ID)
        assertEquals(Tok.page_headerX, c.headerPen, 0f)
        assertEquals(Roles.pageHeader.alpha, c.headerAlpha, 0f)
    }

    @Test
    fun anAccentOffChangeRenewsTheSettingsDrawsMeasure() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings))
        val (_, m) = open(app)
        val before = rule.runOnIdle { app.host.types.settings.drawOf(m).measure }
        rule.runOnIdle { app.accentOff = false }
        frames(RESIZE_FRAMES)
        assertNotSame(before, rule.runOnIdle { app.host.types.settings.drawOf(m).measure })
    }

    @Test
    fun aRowTheAppOpensGetsNoValueSheet() {
        val rows = Rows(pivot(choice("a"))).also { it.opened = setOf("a") }
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals(listOf("a"), rows.opens)
        assertNull(app.stack.overlay)
    }

    @Test
    fun anOpenThatAnswersFalseReachesTheValueSheetOnce() {
        val rows = Rows(pivot(choice("a")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals(listOf("a"), rows.opens)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(WORDS, app.menu.items)
    }

    @Test
    fun anEditableRowWithNoWordsOpensNoValueSheet() {
        val rows = Rows(pivot(SettingRow("a", "a", "on", SettingKind.Choice, shows = listOf("on"))))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertNull(app.stack.overlay)
        assertTrue(rows.sets.isEmpty())
    }

    @Test
    fun aRowThatDoesNotWrapOpensAValueSheetWhoseStepsStopAtTheEnds() {
        val rows = Rows(pivot(choice("a").copy(wrap = false)))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals("premise: the value sheet opens on the first item", 0, app.menu.focus.row)
        assertFalse("a step up from the first item stops there", rule.runOnIdle { app.menu.step(-1, rule.mainClock.currentTime) })
        assertEquals(0, app.menu.focus.row)
        rule.runOnIdle { repeat(WORDS.size) { app.menu.step(1, rule.mainClock.currentTime) } }
        assertEquals("steps down stop at the last item", WORDS.lastIndex, app.menu.focus.row)
    }

    @Test
    fun aPickThatOpensAnotherSheetKeepsTheMenu() {
        val rows = Rows(pivot(choice("a")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        rows.onSet = { app.host.openSheet(SettingSheet(MenuForm.Dlg, OTHER, listOf(OTHER))) { true } }
        tap(app, m, 0)
        val first = app.stack.overlay
        assertEquals("premise: the value sheet is open", OverKind.Sheet, first?.kind)
        assertFalse("a pick that opened another sheet keeps the menu", rule.runOnIdle { app.menuHost.act(1) })
        assertEquals(listOf(mapOf("a" to WORDS[1])), rows.sets)
        frames(OPEN_FRAMES)
        assertEquals(OverKind.Dlg, app.stack.overlay?.kind)
    }

    @Test
    fun aPickAfterTheRowsValuesChangedSendsTheValueTheSheetShowed() {
        val rows = Rows(pivot(choice("a")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        rule.runOnIdle { rows.pivots.value = pivot(choice("a", LATER)) }
        frames(RESIZE_FRAMES)
        assertEquals("premise: the row is still there", LATER, m.rows(0)[0].values)
        assertFalse("premise: the sheet stays", closed(app))
        rule.runOnIdle { app.menuHost.act(1) }
        assertEquals(listOf(mapOf("a" to WORDS[1])), rows.sets)
    }

    @Test
    fun rowsThatLandWithoutTheEditedRowOrWithItReadOnlyCloseItsSheet() {
        val rows = Rows(pivot(choice("a"), choice("b")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals("premise: the edit is on a", "a", m.editing)
        rule.runOnIdle { rows.pivots.value = pivot(choice("a").copy(flags = setOf(SettingFlag.ReadOnly)), choice("b")) }
        frames(OPEN_FRAMES)
        assertTrue("a row turned read only closes its sheet", closed(app))
        tap(app, m, 1)
        assertEquals("premise: the edit is on b", "b", m.editing)
        assertFalse("premise: the sheet is open", closed(app))
        rule.runOnIdle { rows.pivots.value = pivot(choice("a")) }
        frames(OPEN_FRAMES)
        assertTrue("a row gone closes its sheet", closed(app))
    }

    @Test
    fun rowsDroppingTheRowInTheCompositionItsSheetOpensCloseTheSheetAsItBecomesCurrent() {
        val rows = Rows(pivot(choice("a"), choice("b")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        var landed = false
        var over: OverKind? = null
        rule.runOnIdle {
            app.beforeApplied = {
                app.beforeApplied = {}
                landed = true
                over = app.stack.overlay?.kind
            }
            app.host.settingsTap(m, m.rows(0)[0])
            rows.pivots.value = pivot(choice("b"))
        }
        frames(OPEN_FRAMES)
        assertTrue("premise: the rows landed", landed)
        assertNull("premise: the sheet was not current when the rows landed", over)
        assertTrue("the sheet of the gone row closes", closed(app))
    }

    @Test
    fun aMoveFromSettingsCancelsItsListDrag() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings))
        val (sc, m) = open(app)
        val list = dragging()
        m.lists = listOf(list)
        rule.runOnIdle { app.host.starting(Turn(MoveKind.T4h, sc, null, popping = true)) }
        assertFalse(list.scroll.dragging)
    }

    @Test
    fun aVolumeOpenOverSettingsCancelsItsListDrag() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings))
        val (_, m) = open(app)
        val list = dragging()
        m.lists = listOf(list)
        rule.runOnIdle { app.host.openVolume(rule.mainClock.currentTime) }
        assertFalse(list.scroll.dragging)
    }

    @Test
    fun aReopenAfterTheThumbFadeDrawsNoThumbUntilTheSwap() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"), choice("b"), choice("c"))), HeaderLook.Settings))
        val (sc, m) = open(app)
        val d = rule.runOnIdle { app.host.types.settings.drawOf(m) }
        m.heightOf = { TALLER }
        assertTrue("premise: the tall rows give a thumb", d.thumbAt(m.pivot, 0) != null)
        rule.runOnIdle {
            app.host.started(Turn(MoveKind.T2, null, sc, false, false, 0L))
            app.host.swapped(sc, SWAP)
        }
        assertEquals(1f, m.fade.at(SWAP), 0f)
        val again = SWAP + GONE
        rule.runOnIdle { app.host.started(Turn(MoveKind.T2, null, sc, false, false, again)) }
        assertEquals(0f, m.fade.at(again), 0f)
        rule.runOnIdle { app.host.swapped(sc, again + SWAP) }
        assertEquals(1f, m.fade.at(again + SWAP), 0f)
    }

    @Test
    fun aLiftedPaneStartsItsDropAsItsSheetCloses() {
        val rows = Rows(pivot(*(0 until LIFT_ROWS).map { choice("r$it", FOUR) }.toTypedArray()))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        rule.runOnIdle {
            app.host.types.settings.drawOf(m)
            m.down(LIFT_ROW, rule.mainClock.currentTime)
            m.cancel(rule.mainClock.currentTime)
        }
        tap(app, m, LIFT_ROW)
        assertEquals("premise: the sheet lifts its row", AimKind.Lift, m.aimKind)
        rule.runOnIdle { app.stack.close() }
        frames(CLOSE_FRAMES)
        assertTrue("premise: the sheet is closing", app.stack.overlay?.closing == true)
        assertEquals("the drop starts at the close", AimKind.Drop, m.aimKind)
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val ENTRY = 3L
        private const val OUT = "output"
        private const val SYS = "system"
        private const val LIB = "library"
        private const val OTHER = "other"
        private const val PAGE = "pairing"
        private const val DEFAULT = "bluetooth"
        private const val NEW = "new"
        private const val OPEN_FRAMES = 30
        private const val RESIZE_FRAMES = 2
        private const val CLOSE_FRAMES = 4
        private const val DRAG_Y = 400f
        private const val TALLER = 600
        private const val SWAP = 300L
        private const val GONE = 3000L
        private const val LIFT_ROWS = 4
        private const val LIFT_ROW = 2
        private val ID = SettingsId(SETTINGS)
        private val WORDS = listOf("off", "low", "high")
        private val LATER = listOf("x", "y", "z")
        private val FOUR = listOf("off", "low", "mid", "high")
    }
}
