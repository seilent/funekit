package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
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
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.touch.Gesture
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.settings.HeaderLook
import net.seilent.funekit.screens.settings.SETTINGS
import net.seilent.funekit.screens.settings.SettingKind
import net.seilent.funekit.screens.settings.SettingPivot
import net.seilent.funekit.screens.settings.SettingRow
import net.seilent.funekit.screens.settings.SettingsId
import net.seilent.funekit.screens.settings.SettingsModel
import net.seilent.funekit.screens.settings.SettingsPage
import net.seilent.funekit.screens.settings.SettingsRows
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
class FieldSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val page = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)

    private class Rows(start: List<SettingPivot>) : SettingsRows {
        override val pivots = mutableStateOf(start)

        override suspend fun set(key: String, value: String): Boolean = true
    }

    private fun pivots(vararg names: String) = names.map { SettingPivot(it, listOf(SettingRow(it, it, WORDS[0], SettingKind.Choice, values = WORDS, words = WORDS, at = 0))) }

    private fun app(rows: Rows = Rows(pivots(OUT, SYS)), root: Boolean = false): Pair<TestApp, SettingsModel> {
        val app = TestApp(page, page, null, StillLevel) { rule.mainClock.currentTime }.also {
            it.settingsList = listOf(SettingsPage(ID, rows, HeaderLook.Settings))
            it.compose(rule, root)
            rule.waitForIdle()
            rule.mainClock.autoAdvance = false
        }
        val sc = TestScreen.Settings(app.stack.entry(), ID)
        rule.runOnIdle { app.stack.reset(listOf(sc)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the settings page is up", sc, app.stack.top)
        return app to app.models[sc.entry] as SettingsModel
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun open(app: TestApp, f: TestFields, n: Int = OPEN_FRAMES) {
        assertTrue("premise: the field sheet opens", rule.runOnIdle { app.host.openFields(f) })
        frames(n)
    }

    private fun pick(app: TestApp, i: Int, n: Int = OPEN_FRAMES) {
        rule.runOnIdle { if (app.menuHost.act(i)) app.stack.accept() }
        frames(n)
    }

    private fun back(n: Int = OPEN_FRAMES) {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(n)
    }

    private fun settle(app: TestApp) {
        var n = 0
        while (app.stack.moving && n < SETTLE_FRAMES) {
            frames(1)
            n++
        }
    }

    private fun hold(app: TestApp, i: Int): Boolean = rule.runOnIdle { app.menuHost.hold!!(i) }.also { frames(CUT_FRAMES) }

    private fun shot() {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
    }

    private fun tap(x: Float, y: Float) {
        rule.onRoot().performTouchInput {
            down(Offset(x, y))
            up()
        }
    }

    private fun holdNumber(f: TestFields) {
        shot()
        val x = px(Tok.edge, page.stdPpu) + 2f
        val y = px(page.board.overlayRingY, page.stdPpu) - 2f
        rule.onRoot().performTouchInput { down(Offset(x, y)) }
        var n = 0
        while (f.hud.shown && n < OPEN_FRAMES) {
            frames(1)
            n++
        }
        frames(CUT_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
    }

    private fun edge(app: TestApp) {
        rule.runOnIdle { app.stack.edgeBack(app.host.popKind) }
        frames(OPEN_FRAMES)
    }

    private fun hudUp(app: TestApp, f: TestFields, i: Int) {
        open(app, f, 2)
        pick(app, i)
        assertTrue("premise: the hud is up", f.hud.shown)
    }

    @Test
    fun aFieldPickOpensItsAdjustOnceTheSheetSettlesWithTheCellOnIt() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f, 2)
        pick(app, GAIN, 1)
        assertFalse("premise: the hud waits for the sheet to settle", f.hud.shown)
        frames(OPEN_FRAMES)
        assertTrue("the hud shows once the sheet settles", f.hud.shown)
        assertEquals("the edit cell is the field's item", GAIN, app.menu.edit)
    }

    @Test
    fun aBandSwipeBegunInTheRiseAndLiftedOnTheSettledSheetDropsTheLiveField() {
        val (app, _) = app(root = true)
        val f = BandFields().also { it.noHud = true }
        open(app, f, CUT_FRAMES)
        pick(app, GAIN, 1)
        val o = requireNotNull(app.stack.overlay)
        assertTrue("premise: the sheet rises", o.moving(rule.mainClock.currentTime))
        val downAt = rule.mainClock.currentTime
        rule.onRoot().performTouchInput {
            down(Offset(EDGE_X, page.heightPx / BAND_Y))
            moveTo(Offset(page.widthPx / 2f, page.heightPx / BAND_Y))
        }
        var n = 0
        while (o.moving(rule.mainClock.currentTime) && n < OPEN_FRAMES) {
            frames(1)
            n++
        }
        val upAt = rule.mainClock.currentTime
        assertFalse("premise: the up comes after the rise", o.moving(upAt))
        assertTrue("premise: the up comes inside the key hold, ${upAt - downAt} ms", upAt - downAt < Tok.input_keyHoldMs.toLong())
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("drop $GAIN", f.calls.last())
    }

    private fun sheetY(app: TestApp, i: Int): Float {
        val m = app.menu
        val ppu = if (m.kept) page.stdPpu else page.ppu
        val pitch = px(Tok.settingsPitch, ppu)
        return m.bottomPx - px(Tok.edge, ppu) - (m.items.size - i) * pitch + pitch / 2f
    }

    @Test
    fun aDownThatLandsAFieldSheetsRiseIsCancelledWhenItsAdjustShows() {
        val (app, _) = app(root = true)
        val f = BandFields()
        open(app, f, 2)
        pick(app, GAIN, 1)
        val last = f.items.lastIndex
        assertTrue("premise: another field row", last != GAIN)
        val o = checkNotNull(app.stack.overlay)
        val at = Offset(page.widthPx / 2f, sheetY(app, last))
        var n = 0
        while (app.menu.backTarget(at.x, at.y, o, rule.mainClock.currentTime, page.heightPx)) {
            frames(1)
            assertTrue("premise: the rising top passes the row", ++n < OPEN_FRAMES)
        }
        assertTrue("premise: the sheet still rises", o.moving(rule.mainClock.currentTime))
        assertFalse("premise: the hud waits for the sheet to settle", f.hud.shown)
        rule.onRoot().performTouchInput { down(at) }
        assertNull("premise: the down is not held", app.stack.held)
        frames(1)
        assertTrue("premise: the hud shows", f.hud.shown)
        val calls = f.calls.toList()
        assertEquals("premise: the picked field stays live under the hud, so a row pick there takes it, $calls", GAIN, f.live)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals("the up picks nothing on the sheet under the hud", calls, f.calls)
    }

    @Test
    fun aBandDownDuringTheReturnFromTheHudIsHeld() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        val o = app.stack.overlay
        tap(2f, 2f)
        frames(2)
        assertFalse("premise: the hud goes", f.hud.shown)
        assertTrue("premise: the sheet rises again", o != null && !o.closing && o.moving(rule.mainClock.currentTime))
        val held = rule.runOnIdle { app.stack.hold(rule.mainClock.currentTime, false, Gesture.Edge.Left).also { app.stack.touch() } }
        assertEquals("a band down during the return from the HUD is held", true, held)
    }

    @Test
    fun anAdjustExitKeepsTheFieldAndRaisesTheSheetAtIt() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        val o = app.stack.overlay
        tap(2f, 2f)
        frames(2)
        assertFalse("the hud goes", f.hud.shown)
        assertSame("the same sheet stays", o, app.stack.overlay)
        assertTrue("the sheet rises again", o != null && !o.closing && o.moving(rule.mainClock.currentTime))
        assertEquals("the menu focus stays on the field", GAIN, app.menu.focus.row)
        assertEquals("the edit cell goes with the field", -1, app.menu.edit)
        assertEquals("keep $GAIN", f.calls.last())
    }

    @Test
    fun anAdjustBackDropsTheFieldAndRaisesTheSheet() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        back()
        assertEquals("the back drops the field", "drop $GAIN", f.calls.last())
        assertFalse("the hud goes", f.hud.shown)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun aNumberHoldKeepsTheFieldAndTheReturnReopensTheSheetAtItWithTheAdjust() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        holdNumber(f)
        assertNull("the sheet goes for the entry", app.stack.overlay)
        assertSame("the run is held", f, app.host.fieldSheet)
        assertEquals(listOf("press $GAIN", "keep $GAIN", "entry $GAIN true"), f.calls)
        rule.runOnIdle { app.host.fieldReturn(true) }
        frames(RETURN_FRAMES)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the sheet reopens at the field", GAIN, app.menu.focus.row)
        assertFalse("the reopen is no value sheet", app.menu.valueSheet)
        assertTrue("the return opens the adjust", f.hud.shown)
        assertEquals("press $GAIN", f.calls.last())
    }

    @Test
    fun aDateTimeNumberHoldDropsTheFieldAndTheReturnBeginsItAgain() {
        val (app, _) = app()
        val f = ClockFields()
        hudUp(app, f, HOUR)
        holdNumber(f)
        assertEquals(listOf("press $HOUR", "drop $HOUR", "entry $HOUR true"), f.calls)
        rule.runOnIdle { app.host.fieldReturn(true) }
        frames(RETURN_FRAMES)
        assertEquals("the sheet reopens at the field", HOUR, app.menu.focus.row)
        assertTrue("the return opens the adjust", f.hud.shown)
        assertEquals("press $HOUR", f.calls.last())
    }

    @Test
    fun anItemHoldOpensItsEntryAndTheReturnReopensTheSheetAtTheItemAlone() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        assertTrue("the hold takes the entry", hold(app, Q))
        assertNull("premise: the sheet goes for the entry", app.stack.overlay)
        assertEquals(listOf("entry $Q false"), f.calls)
        rule.runOnIdle { app.host.fieldReturn(false) }
        frames(OPEN_FRAMES)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the sheet reopens at the item", Q, app.menu.focus.row)
        assertFalse("an item return opens no adjust", f.hud.shown)
        assertEquals(listOf("entry $Q false"), f.calls)
    }

    @Test
    fun aFieldSheetAskedWhileAnOpenIsHeldIsRefused() {
        val (app, _) = app()
        val opened = rule.runOnIdle {
            app.stack.open(Overlay(OverKind.Sheet))
            app.host.openFields(BandFields())
        }
        assertFalse("a field sheet asked while an open is held is refused", opened)
        frames(OPEN_FRAMES)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertNull(app.host.fieldSheet)
    }

    @Test
    fun aHoldOnAnUntypedItemCutsNothing() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        assertFalse("the hold takes no entry", hold(app, 0))
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertTrue("no entry", f.calls.none { it.startsWith("entry") })
    }

    @Test
    fun aNumberHoldOnAnUntypedFieldCutsNothing() {
        val (app, _) = app()
        val f = BandFields().also { it.untyped = setOf(Q) }
        hudUp(app, f, Q)
        holdNumber(f)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertTrue("the hud stays", f.hud.shown)
        assertTrue("no entry", f.calls.none { it.startsWith("entry") })
    }

    @Test
    fun anItemHoldTheSheetRefusesEndsTheRun() {
        val (app, _) = app()
        val f = BandFields().also { it.refuses = true }
        open(app, f)
        assertFalse("premise: the sheet refuses the hold", hold(app, Q))
        frames(OPEN_FRAMES)
        assertNull("the refused hold ends the run", app.host.fieldSheet)
        assertEquals("dismiss", f.calls.last())
        assertNull("nothing stays up", app.stack.overlay)
    }

    @Test
    fun aNumberHoldTheSheetRefusesEndsTheRun() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        f.refuses = true
        holdNumber(f)
        assertNull("the refused hold ends the run", app.host.fieldSheet)
        assertEquals("dismiss", f.calls.last())
        assertNull("nothing stays up", app.stack.overlay)
    }

    @Test
    fun aChoiceItemSwapsInItsChoiceAndAPickReturnsToTheFieldSheet() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        val o = app.stack.overlay
        pick(app, 0)
        assertEquals("the choice shows", TYPE, app.menu.title)
        assertEquals(TYPES, app.menu.items)
        assertSame("the overlay stays", o, app.stack.overlay)
        pick(app, 1)
        assertEquals("choose 1", f.calls.last())
        assertSame("the field sheet stays up", o, app.stack.overlay)
        assertEquals("the band's items return", BAND_ITEMS, app.menu.items)
        assertEquals("at the choice's item", 0, app.menu.focus.row)
        assertFalse(app.menu.valueSheet)
    }

    @Test
    fun backOnAChoiceDropsTheFieldAndReturnsToTheFieldSheet() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        pick(app, 0)
        back()
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("drop 0", f.calls.last())
        assertEquals("the band's items return", BAND_ITEMS, app.menu.items)
    }

    @Test
    fun anEdgeBackUnderAFieldAdjustDoesNothing() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        settle(app)
        edge(app)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertTrue("the hud stays", f.hud.shown)
        assertEquals("the edge reaches no field", listOf("press $GAIN"), f.calls)
    }

    @Test
    fun anEdgeBackOnAChoiceReturnsToTheFieldSheet() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        pick(app, 0)
        settle(app)
        edge(app)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the band's items return", BAND_ITEMS, app.menu.items)
    }

    @Test
    fun anEdgeBackWithAFieldLiveDropsItAndKeepsTheSheet() {
        val (app, _) = app()
        val f = BandFields().also { it.noHud = true }
        open(app, f)
        pick(app, GAIN)
        settle(app)
        assertFalse("premise: the live field shows no hud", f.hud.shown)
        edge(app)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("drop $GAIN", f.calls.last())
    }

    @Test
    fun anEdgeBackDuringTheFieldSheetsRiseDoesNothing() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f, CUT_FRAMES)
        val o = app.stack.overlay
        assertTrue("premise: the sheet rises", o != null && o.moving(rule.mainClock.currentTime))
        edge(app)
        assertTrue("an edge back during the sheet's rise does nothing", app.stack.overlay === o && o?.closing == false)
        assertEquals("the edge reaches no field", emptyList<String>(), f.calls)
    }

    @Test
    fun aScrimTapOnAChoiceIsItsBack() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        pick(app, 0)
        rule.runOnIdle { if (!app.menuHost.scrim()) app.stack.close() }
        frames(OPEN_FRAMES)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the band's items return", BAND_ITEMS, app.menu.items)
        assertEquals("drop 0", f.calls.last())
    }

    @Test
    fun theChoiceKeepsItsItemsWhileItShows() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        pick(app, 0)
        rule.runOnIdle { f.shown = f.shown.map { it + "x" } }
        frames(OPEN_FRAMES)
        assertEquals("the choice keeps its words", TYPES, app.menu.items)
        assertFalse("the choice keeps no value cells", app.menu.cellsOn)
    }

    @Test
    fun aChoiceOfAnotherFormFailsPlainly() {
        val (app, _) = app()
        val f = BandFields(MenuForm.Dlg)
        open(app, f)
        val failed = rule.runOnIdle { runCatching { app.menuHost.act(0) }.exceptionOrNull() }
        assertEquals("the open fails", "a field sheet's choice is a sheet", (failed as? IllegalArgumentException)?.message)
    }

    @Test
    fun aPickWhileAFieldIsLiveMovesTheFocusKeepsTheFieldAndOpensNoChoice() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f, 2)
        pick(app, GAIN, 1)
        assertFalse("premise: the hud waits for the sheet to settle", f.hud.shown)
        pick(app, 0, 1)
        assertEquals("no choice opens", BAND_ITEMS, app.menu.items)
        assertTrue("the live field takes the pick", "pickLive 0" in f.calls)
        assertTrue("the pick presses nothing", "press 0" !in f.calls)
        assertEquals("the pick moves the focus", 0, app.menu.focus.row)
        frames(OPEN_FRAMES)
        assertFalse("the hud stays shut", f.hud.shown)
    }

    @Test
    fun backWithAFieldLiveDropsItAndKeepsTheSheet() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f, 2)
        pick(app, GAIN, 1)
        back(1)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("drop $GAIN", f.calls.last())
        frames(OPEN_FRAMES)
        assertEquals("the sheet stays up", OverKind.Sheet, app.stack.overlay?.kind)
        assertFalse("the hud stays shut", f.hud.shown)
    }

    @Test
    fun aBackWithAFieldLiveCountsOnceOnTheStack() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f, 2)
        pick(app, GAIN, 1)
        val before = app.stack.backs
        back(1)
        assertEquals("premise: the back drops the live field", "drop $GAIN", f.calls.last())
        assertEquals("the back counts once", before + 1, app.stack.backs)
    }

    @Test
    fun aBackOnAChoiceCountsOnceOnTheStack() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        pick(app, 0)
        val before = app.stack.backs
        back()
        assertEquals("premise: the back drops the choice", "drop 0", f.calls.last())
        assertEquals("the back counts once", before + 1, app.stack.backs)
    }

    @Test
    fun anAdjustWhoseFieldEndsWhileItsTargetStaysShowsTheSheetInPlace() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        val o = app.stack.overlay
        rule.runOnIdle { f.live = -1 }
        frames(2)
        assertFalse("the hud goes when its field ends", f.hud.shown)
        assertSame("the same sheet stays", o, app.stack.overlay)
        assertFalse("the sheet shows in place", o!!.moving(rule.mainClock.currentTime))
        assertTrue("no back among the calls", "back" !in f.calls)
    }

    @Test
    fun aGoneTargetClosesTheSheetAndDropsTheLiveField() {
        val (app, _) = app()
        val f = BandFields()
        hudUp(app, f, GAIN)
        rule.runOnIdle { f.gone = true }
        frames(OPEN_FRAMES)
        assertNull("the sheet closes when its target goes", app.stack.overlay)
        assertFalse("the hud goes", f.hud.shown)
        assertNull(app.host.fieldSheet)
        assertEquals("dismiss", f.calls.last())
    }

    @Test
    fun aGoneTargetDuringTheKeyboardEndsTheHeldRun() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        hold(app, Q)
        rule.runOnIdle { f.gone = true }
        frames(CUT_FRAMES)
        assertNull("the held run ends", app.host.fieldSheet)
        assertEquals("dismiss", f.calls.last())
        rule.runOnIdle { app.host.fieldReturn(false) }
        frames(OPEN_FRAMES)
        assertNull("nothing reopens", app.stack.overlay)
    }

    @Test
    fun aReturnOverAnotherPageReopensNothing() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        hold(app, Q)
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.host.fieldReturn(false) }
        frames(OPEN_FRAMES)
        assertEquals("nothing reopens off its page", null, app.stack.overlay?.kind)
        assertNull(app.host.fieldSheet)
        assertEquals("dismiss", f.calls.last())
    }

    @Test
    fun aReturnAfterTheOwnersPivotMovedReopensNothing() {
        val (app, m) = app()
        val f = BandFields()
        open(app, f)
        hold(app, Q)
        rule.runOnIdle { m.switchTo(1, rule.mainClock.currentTime) }
        frames(CUT_FRAMES)
        assertNull("the held run ends when its pivot moves", app.host.fieldSheet)
        assertEquals("dismiss", f.calls.last())
        rule.runOnIdle { app.host.fieldReturn(false) }
        frames(OPEN_FRAMES)
        assertNull("nothing reopens", app.stack.overlay)
    }

    @Test
    fun aLandingThatMovesTheOwnersPivotClosesItsFieldSheet() {
        val rows = Rows(pivots(OUT, SYS))
        val (app, m) = app(rows)
        val f = BandFields()
        open(app, f)
        assertEquals("premise: the owner shows its first pivot", OUT, m.names[m.pivot])
        rule.runOnIdle { rows.pivots.value = pivots(SYS) }
        frames(OPEN_FRAMES)
        assertNull("the sheet closes when its pivot moves", app.stack.overlay)
        assertEquals("dismiss", f.calls.last())
    }

    @Test
    fun aReturnWithNoHeldEntryDoesNothing() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        val o = app.stack.overlay
        rule.runOnIdle { app.host.fieldReturn(true) }
        frames(OPEN_FRAMES)
        assertSame("a return with no entry opens nothing", o, app.stack.overlay)
        assertTrue("and presses nothing", f.calls.isEmpty())
        hold(app, Q)
        rule.runOnIdle {
            app.host.fieldReturn(false)
            app.host.fieldReturn(false)
        }
        frames(OPEN_FRAMES)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertSame("one reopen keeps the run", f, app.host.fieldSheet)
        assertEquals(listOf("entry $Q false"), f.calls)
    }

    @Test
    fun theOwnerPagesDropEndsAHeldRun() {
        val (app, _) = app()
        val f = BandFields()
        open(app, f)
        hold(app, Q)
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(OPEN_FRAMES)
        settle(app)
        assertNull("premise: the settings page is popped", app.stack.top)
        assertFalse("premise: the pop has landed", app.stack.moving)
        assertNull("the owner's drop ends the run", app.host.fieldSheet)
        assertEquals("dismiss", f.calls.last())
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val OUT = "output"
        private const val SYS = "system"
        private const val OPEN_FRAMES = 30
        private const val SETTLE_FRAMES = 120
        private const val CUT_FRAMES = 2
        private const val RETURN_FRAMES = 3
        private const val GAIN = 2
        private const val Q = 3
        private const val HOUR = 3
        private const val ALBUM = 20L
        private const val EDGE_X = 1f
        private const val BAND_Y = 4f
        private val ID = SettingsId(SETTINGS)
        private val WORDS = listOf("off", "on")
    }
}
