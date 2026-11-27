package net.seilent.funekit.screens.host

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.settings.SettingSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class ReopenSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val page = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)
    private val picks = mutableListOf<String>()

    private fun app(): TestApp = TestApp(page, page, null, StillLevel) { rule.mainClock.currentTime }.also {
        it.compose(rule)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun until(what: String, ok: () -> Boolean) {
        var n = 0
        while (!rule.runOnIdle(ok)) {
            frames(1)
            assertTrue(what, ++n < MAX_FRAMES)
        }
    }

    private fun picker(name: String): (Int) -> Boolean = { i ->
        picks += "$name $i"
        true
    }

    private fun open(app: TestApp, sheet: SettingSheet, name: String = OLD): Overlay {
        val o = requireNotNull(rule.runOnIdle { app.host.openSheet(sheet, picker(name)) }) { "premise: the sheet opens" }
        until("premise: the sheet settles") { app.stack.overlay === o && !o.moving(rule.mainClock.currentTime) }
        return o
    }

    private fun reopen(app: TestApp, o: Overlay, sheet: SettingSheet, name: String = NEW): Boolean = rule.runOnIdle { app.host.reopenSheet(o, sheet, picker(name)) }

    private fun sheetY(app: TestApp, i: Int): Float {
        val m = app.menu
        val ppu = if (m.kept) page.stdPpu else page.ppu
        val pitch = px(Tok.settingsPitch, ppu)
        return m.bottomPx - px(Tok.edge, ppu) - (m.items.size - i) * pitch + pitch / 2f
    }

    private fun steps(focus: Int, mark: Int) = SettingSheet(MenuForm.Sheet, SLEEP, STEPS, focus = focus, mark = mark, wrap = true, lift = true)

    private fun led(focus: Int, mark: Int) = SettingSheet(MenuForm.Sheet, SLEEP, listOf(LEFT) + STEPS, lead = 1, focus = focus, mark = mark, wrap = true, lift = true)

    @Test
    fun aReopenTakesTheNewItemsOnTheShownSheetWithNoNewOverlayOrMotion() {
        val app = app()
        val o = open(app, steps(2, 2))
        val start = o.start
        assertTrue("the reopen acts on the shown sheet", reopen(app, o, led(-1, -1)))
        frames(OPEN_FRAMES)
        assertSame("no new overlay opens", o, rule.runOnIdle { app.stack.overlay })
        assertEquals("no open motion runs", start, o.start)
        rule.runOnIdle {
            assertFalse(o.moving(rule.mainClock.currentTime))
            assertEquals(listOf(LEFT) + STEPS, app.menu.items)
            assertEquals(1, app.menu.lead)
            assertEquals(SLEEP, app.menu.title)
        }
        rule.runOnIdle { if (app.menuHost.act(2)) app.stack.accept() }
        assertEquals("the pick is the new sheet's", listOf("$NEW 2"), picks)
    }

    @Test
    fun aReopenKeepsTheFocusAndMarkOnTheirItemsAcrossALeadAddedAndRemoved() {
        val app = app()
        val o = open(app, steps(2, 2))
        rule.runOnIdle { app.menu.step(1, rule.mainClock.currentTime) }
        assertEquals("premise: the focus moves to ${STEPS[3]}", 3, rule.runOnIdle { app.menu.focus.row })
        assertTrue(reopen(app, o, led(-1, -1)))
        frames(OPEN_FRAMES)
        assertEquals("the focus stays on ${STEPS[3]} under the lead", 4, rule.runOnIdle { app.menu.focus.row })
        assertEquals("the mark stays on ${STEPS[2]} under the lead", 3, rule.runOnIdle { app.menu.mark })
        assertTrue(reopen(app, o, steps(-1, -1)))
        frames(OPEN_FRAMES)
        assertEquals("the focus stays on ${STEPS[3]} with the lead gone", 3, rule.runOnIdle { app.menu.focus.row })
        assertEquals("the mark stays on ${STEPS[2]} with the lead gone", 2, rule.runOnIdle { app.menu.mark })
    }

    @Test
    fun aReopenTakesTheFocusAndMarkTheSheetSets() {
        val app = app()
        val o = open(app, steps(2, 2))
        assertTrue(reopen(app, o, led(5, 6)))
        frames(OPEN_FRAMES)
        assertEquals(5, rule.runOnIdle { app.menu.focus.row })
        assertEquals(6, rule.runOnIdle { app.menu.mark })
    }

    @Test
    fun aPlainSheetReopenTakesItsMarkAndKeepsItOnItsItem() {
        val app = app()
        val o = open(app, SettingSheet(MenuForm.Sheet, LISTS, listOf(ONE, TWO, THREE)))
        assertTrue(reopen(app, o, SettingSheet(MenuForm.Sheet, LISTS, listOf(ONE, TWO, THREE), mark = 1)))
        frames(OPEN_FRAMES)
        assertFalse("premise: no value sheet", rule.runOnIdle { app.menu.valueSheet })
        assertEquals("the plain sheet takes the mark it sets", 1, rule.runOnIdle { app.menu.mark })
        assertTrue(reopen(app, o, SettingSheet(MenuForm.Sheet, LISTS, listOf(LEFT, ONE, TWO, THREE), lead = 1)))
        frames(OPEN_FRAMES)
        assertEquals("the mark stays on $TWO under the lead", 2, rule.runOnIdle { app.menu.mark })
    }

    @Test
    fun aReopenAnswersFalseWhenItsSheetIsNotShown() {
        val app = app()
        val o = open(app, steps(2, 2))
        rule.runOnIdle { app.stack.cut() }
        until("premise: the cut drops the sheet") { app.stack.overlay == null }
        assertFalse("premise: a cut sheet is not closing", o.closing)
        assertFalse("a sheet no longer shown takes nothing", reopen(app, o, led(-1, -1)))
        val p = open(app, steps(1, 1))
        val q = open(app, SettingSheet(MenuForm.Sheet, OTHER, listOf(OTHER, OTHER)))
        assertFalse("a sheet under another takes nothing", reopen(app, p, led(-1, -1)))
        assertEquals(listOf(OTHER, OTHER), rule.runOnIdle { app.menu.items })
        rule.runOnIdle { app.stack.close() }
        frames(CUT_FRAMES)
        assertTrue("premise: the shown sheet closes", rule.runOnIdle { app.stack.overlay === q && q.closing })
        assertFalse("a closing sheet takes nothing", reopen(app, q, led(-1, -1)))
        assertEquals(listOf(OTHER, OTHER), rule.runOnIdle { app.menu.items })
    }

    @Test
    fun aPressInProgressOnTheSheetPicksNothingAfterAReopen() {
        val app = app()
        val o = open(app, SettingSheet(MenuForm.Sheet, OTHER, listOf(ONE, TWO, THREE)))
        rule.onRoot().performTouchInput { down(Offset(page.widthPx / 2f, sheetY(app, 1))) }
        frames(CUT_FRAMES)
        assertEquals("premise: the down presses $TWO", 1, rule.runOnIdle { app.menu.pressed })
        assertTrue(reopen(app, o, SettingSheet(MenuForm.Sheet, OTHER, listOf(THREE, TWO, ONE))))
        frames(CUT_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals("the up picks nothing", emptyList<String>(), picks)
        assertSame(o, rule.runOnIdle { app.stack.overlay })
    }

    @Test
    fun aReadOnlySheetReopenedPickableTakesATap() {
        val app = app()
        val o = open(app, SettingSheet(MenuForm.Sheet, LISTS, listOf(LOADING), readOnly = true))
        assertTrue(reopen(app, o, SettingSheet(MenuForm.Sheet, LISTS, listOf(ONE, TWO, THREE), focus = 0, mark = 1)))
        frames(OPEN_FRAMES)
        assertFalse("the reopened sheet takes picks", rule.runOnIdle { app.menu.readOnly })
        rule.onRoot().performTouchInput {
            down(Offset(page.widthPx / 2f, sheetY(app, 2)))
            up()
        }
        frames(OPEN_FRAMES)
        assertEquals(listOf("$NEW 2"), picks)
        assertNull("the pick closes the sheet", rule.runOnIdle { app.stack.overlay?.takeIf { !it.closing } })
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val OPEN_FRAMES = 30
        private const val CUT_FRAMES = 2
        private const val MAX_FRAMES = 400
        private const val OLD = "old"
        private const val NEW = "new"
        private const val SLEEP = "sleep timer"
        private const val LEFT = "44 min left"
        private const val OTHER = "other"
        private const val LISTS = "playlist"
        private const val LOADING = "loading"
        private const val ONE = "one"
        private const val TWO = "two"
        private const val THREE = "three"
        private val STEPS = listOf("120 min", "90 min", "60 min", "45 min", "30 min", "15 min", "off")
    }
}
