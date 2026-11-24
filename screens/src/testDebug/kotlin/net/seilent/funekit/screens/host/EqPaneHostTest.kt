package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.eq.EqActs
import net.seilent.funekit.screens.eq.EqChoice
import net.seilent.funekit.screens.eq.EqEntry
import net.seilent.funekit.screens.eq.EqId
import net.seilent.funekit.screens.eq.EqKind
import net.seilent.funekit.screens.eq.EqLine
import net.seilent.funekit.screens.eq.EqModel
import net.seilent.funekit.screens.eq.EqOutcome
import net.seilent.funekit.screens.eq.EqPage
import net.seilent.funekit.screens.eq.EqPaneLine
import net.seilent.funekit.screens.eq.EqPivot
import net.seilent.funekit.screens.eq.EqRow
import net.seilent.funekit.screens.eq.EqRowsPane
import net.seilent.funekit.screens.eq.RowEnter
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.settings.AimKind
import net.seilent.funekit.screens.settings.FieldSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class EqPaneHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val page = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)

    private fun outputs(bands: Int = 2, target: Long? = null): List<EqPivot> = listOf(
        parametric(HP_SLOT, HP, (0 until bands).map { testBand(it) }, target = target),
        parametric(BT_SLOT, BT, (0 until bands).map { testBand(it) }, target = target),
    )

    private fun app(rows: TestEqRows): TestApp = TestApp(page, page, null, StillLevel) { rule.mainClock.currentTime }.also {
        it.eqList = listOf(EqPage(ID, EqRowsPane(EQ, rows)))
        it.compose(rule, true)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun now(): Long = rule.mainClock.currentTime

    private fun open(app: TestApp, pivot: String = HP): EqModel {
        val sc = TestScreen.Eq(app.stack.entry(), ID, pivot)
        rule.runOnIdle { app.stack.reset(listOf(sc)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the eq page is up", sc, app.stack.top)
        return app.models[sc.entry] as EqModel
    }

    private fun pane(app: TestApp): EqRowsPane = app.eqList.single().pane as EqRowsPane

    private fun line(m: EqModel, key: String): EqLine = m.rows(m.pivot).first { it.key == key }

    private fun tapRow(app: TestApp, m: EqModel, key: String) {
        rule.runOnIdle { app.host.eqTap(m, line(m, key)) }
        frames(OPEN_FRAMES)
    }

    private fun pick(app: TestApp, i: Int) {
        rule.runOnIdle { if (app.menuHost.act(i)) app.stack.accept() }
        frames(OPEN_FRAMES)
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
    }

    private fun step(app: TestApp, h: AdjustHit) {
        rule.runOnIdle { app.host.fieldSheet!!.tap(h) }
        frames(STEP_FRAMES)
    }

    private fun exit() {
        rule.onRoot().performTouchInput {
            down(Offset(2f, 2f))
            up()
        }
        frames(OPEN_FRAMES)
    }

    private fun gainHud(app: TestApp, m: EqModel, band: String = BAND_0) {
        tapRow(app, m, band)
        pick(app, GAIN)
        assertTrue("premise: the gain hud is up", app.host.fieldSheet?.hud?.shown == true)
    }

    private fun shot() {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
    }

    private fun holdNumber(app: TestApp) {
        val hud = app.host.fieldSheet!!.hud
        shot()
        val x = px(Tok.edge, page.stdPpu) + 2f
        val y = px(page.board.overlayRingY, page.stdPpu) - 2f
        rule.onRoot().performTouchInput { down(Offset(x, y)) }
        var n = 0
        while (hud.shown && n < OPEN_FRAMES) {
            frames(1)
            n++
        }
        frames(CUT_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
    }

    private fun holdItem(app: TestApp, i: Int) {
        assertTrue("premise: the item takes an entry", rule.runOnIdle { app.menuHost.hold!!(i) })
        frames(CUT_FRAMES)
    }

    private class Spy(private val a: EqActs) : EqActs by a {
        val toasts = mutableListOf<String>()
        var fieldAsks = 0

        override fun toast(line1: String, line2: String) {
            toasts += "$line1 / $line2"
            a.toast(line1, line2)
        }

        override fun fields(sheet: FieldSheet): Boolean {
            fieldAsks++
            return a.fields(sheet)
        }
    }

    private fun spyTap(app: TestApp, m: EqModel, key: String): Spy {
        val s = Spy(app.host.actsOf(m))
        rule.runOnIdle { pane(app).tap(m.names[m.pivot], line(m, key), s) }
        frames(OPEN_FRAMES)
        return s
    }

    private fun typeValue(app: TestApp, m: EqModel, key: String) {
        assertTrue("premise: the row takes a type value sheet", rule.runOnIdle { pane(app).hold(m.names[m.pivot], line(m, key), app.host.actsOf(m)) })
        frames(OPEN_FRAMES)
        pick(app, 0)
    }

    private fun shows(app: TestApp): Int = rule.runOnIdle { app.toast.shows }

    private fun presets(rows: TestEqRows) {
        rows.choiceOf = { _, _ -> listOf(EqChoice("custom", "custom"), EqChoice(PRESET_KEY, PRESET)) }
        rows.currentOf = { _, _ -> PRESET_KEY }
    }

    private fun entryOf(e: EqEntry): List<Any> = listOf(e.title, e.start, e.text, e.extras, e.unit, e.decimals, e.min, e.max)

    private fun profiles(rows: TestEqRows) {
        rows.choiceOf = { _, _ -> listOf(EqChoice("off", "off"), EqChoice("custom", "custom"), EqChoice(CARD, "a")) }
        rows.currentOf = { _, _ -> "custom" }
    }

    @Test
    fun aFieldKeptByExitAndPressedAgainBeforeItsReplyBacksOutToTheKeptValue() {
        val rows = TestEqRows(outputs()).also { it.holds = true }
        val app = app(rows)
        val m = open(app)
        gainHud(app, m)
        step(app, AdjustHit.Up0)
        step(app, AdjustHit.Up0)
        assertEquals("premise: two steps out, no reply", listOf("band $HP_SLOT 0 Gain 1", "band $HP_SLOT 0 Gain 2"), rows.sends)
        exit()
        pick(app, GAIN)
        assertTrue("premise: the gain hud is up again", app.host.fieldSheet?.hud?.shown == true)
        back()
        assertEquals("the back keeps the kept value", "band $HP_SLOT 0 Gain 2", rows.sends.last())
        assertEquals("the back sends nothing", 2, rows.sends.size)
    }

    @Test
    fun aTypedReturnPressStartsFromTheTypedValue() {
        val rows = TestEqRows(outputs()).also { it.holds = true }
        val app = app(rows)
        val m = open(app)
        gainHud(app, m)
        holdNumber(app)
        assertEquals("premise: the number hold opens one entry", 1, rows.entries.size)
        val e = rows.entries.single()
        assertTrue("premise: a number entry returns with the adjust", e.reopen && e.adjust)
        rule.runOnIdle { e.done("3.5") }
        rule.runOnIdle { app.host.fieldReturn(e.adjust) }
        frames(OPEN_FRAMES)
        assertTrue("premise: the return opens the gain hud", app.host.fieldSheet?.hud?.shown == true)
        back()
        assertEquals("the back keeps the typed value", "band $HP_SLOT 0 Gain 35", rows.sends.last())
        assertEquals(1, rows.sends.size)
    }

    @Test
    fun rowsAnAddBandLandingAddsEnterOnEaseOutFromTheShift() {
        val rows = TestEqRows(outputs(1))
        val app = app(rows)
        val m = open(app)
        val p = pane(app)
        val t0 = now()
        rule.runOnIdle { app.host.eqTap(m, line(m, "$HP_SLOT:AddBand")) }
        val pv = rule.runOnIdle { p.pivots.value.first { it.name == HP } }
        val added = pv.rows.first { it.key == "$HP_SLOT:Band:1" }
        val kept = pv.rows.first { it.key == "$HP_SLOT:Band:0" }
        val d = Tok.motion_listChange.toLong()
        val first = p.enter(HP, added, t0)
        assertEquals("an added row starts unseen", 0f, first.alpha, 0f)
        assertEquals("from the shift", pen64(Tok.motion_listChangeShift, page.ppu), first.shift)
        assertEquals("it eases out", Curves.easeOut.at(HALF_WAY), p.enter(HP, added, t0 + d / 2).alpha, EPS)
        assertEquals("it lands at rest", RowEnter.Rest, p.enter(HP, added, t0 + d))
        assertEquals("a kept row does not enter", RowEnter.Rest, p.enter(HP, kept, t0))
        frames(OPEN_FRAMES)
        assertEquals("the new band's sheet opens", "2", app.host.fieldSheet?.title)
    }

    @Test
    fun aLandingThatMovesTheShownPivotClosesThePickSheet() {
        val rows = TestEqRows(outputs())
        profiles(rows)
        val app = app(rows)
        val m = open(app)
        tapRow(app, m, "$HP_SLOT:Profile")
        assertEquals("premise: the pick sheet is up", OverKind.Sheet, app.stack.overlay?.kind)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) it.copy(name = WIRED) else it } } }
        frames(OPEN_FRAMES)
        assertNull("the move closes the pick sheet", app.stack.overlay)
    }

    @Test
    fun aNewTargetClosesThePickSheetAndEndsItsWatch() {
        val rows = TestEqRows(outputs(target = 1L))
        profiles(rows)
        val app = app(rows)
        val m = open(app)
        tapRow(app, m, "$HP_SLOT:Profile")
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) it.copy(target = 2L) else it } } }
        frames(OPEN_FRAMES)
        assertNull("the new target closes the pick sheet", app.stack.overlay)
        tapRow(app, m, "$HP_SLOT:Profile")
        pick(app, 0)
        assertNull("premise: the pick closes the sheet", app.stack.overlay)
        rule.runOnIdle { m.switchTo(1, now()) }
        frames(OPEN_FRAMES)
        tapRow(app, m, "$BT_SLOT:Profile")
        assertEquals("premise: the bluetooth sheet is up", OverKind.Sheet, app.stack.overlay?.kind)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) it.copy(target = 3L) else it } } }
        frames(OPEN_FRAMES)
        assertEquals("the ended watch closes no other sheet", OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun aTypeValuePickHandsItsLiftToTheEntry() {
        val rows = TestEqRows(listOf(graphic(HP_SLOT, HP, List(LONG) { 0 })))
        val app = app(rows)
        val m = open(app)
        val last = m.rows(m.pivot).lastIndex
        rule.runOnIdle { m.aimTo(m.base(m.pivot), AimKind.Into) }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            m.down(last, now())
            m.cancel(now())
        }
        frames(OPEN_FRAMES)
        val before = m.pane.px
        val entry = Overlay(OverKind.Sheet)
        rows.opens = {
            app.stack.open(entry)
            true
        }
        assertTrue(rule.runOnIdle { pane(app).hold(HP, m.rows(m.pivot)[last], app.host.actsOf(m)) })
        frames(OPEN_FRAMES)
        val lifted = m.pane.px
        assertTrue("premise: the sheet lifts the row", lifted > before)
        rule.runOnIdle { if (app.menuHost.act(0)) app.stack.accept() }
        val px = mutableListOf<Int>()
        var n = 0
        while (app.stack.overlay !== entry && n < OPEN_FRAMES) {
            frames(1)
            px += m.pane.px
            n++
        }
        assertSame("the entry opens", entry, app.stack.overlay)
        val swap = now()
        repeat(OPEN_FRAMES) {
            frames(1)
            px += m.pane.px
        }
        assertEquals("one entry", 1, rows.entries.size)
        assertTrue("the entry rises at the swap", abs(entry.start - swap) <= FRAME)
        assertTrue("the row stays lifted through the swap, $px against $lifted", px.all { it == lifted })
        assertEquals("the row is lifted under the entry", lifted, m.pane.px)
    }

    @Test
    fun aBackAfterTheBandLeftSendsNoRevert() {
        val rows = TestEqRows(outputs()).also { it.holds = true }
        val app = app(rows)
        val m = open(app)
        gainHud(app, m, BAND_1)
        step(app, AdjustHit.Up0)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) withBands(it, it.bands.take(1)) else it } } }
        frames(OPEN_FRAMES)
        assertNull("premise: the lost band ends the sheet", app.host.fieldSheet)
        back()
        assertEquals("no revert goes out", listOf("band $HP_SLOT 1 Gain 1"), rows.sends)
    }

    @Test
    fun aBackWithNoStepSendsNothing() {
        val rows = TestEqRows(outputs())
        val app = app(rows)
        val m = open(app)
        gainHud(app, m)
        back()
        assertEquals("no send", emptyList<String>(), rows.sends)
    }

    @Test
    fun aTypedValueForALeftPivotAnOlderTargetOrALostBandIsDropped() {
        val rows = TestEqRows(outputs(target = 1L))
        val app = app(rows)
        val m = open(app)
        tapRow(app, m, BAND_0)
        holdItem(app, GAIN)
        rule.runOnIdle { m.switchTo(1, now()) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { rows.entries.last().done("3.5") }
        assertEquals("a left pivot drops the value", emptyList<String>(), rows.sends)
        rule.runOnIdle { m.switchTo(0, now()) }
        frames(OPEN_FRAMES)
        tapRow(app, m, BAND_0)
        holdItem(app, GAIN)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) it.copy(target = 2L) else it } } }
        frames(OPEN_FRAMES)
        rule.runOnIdle { rows.entries.last().done("3.5") }
        assertEquals("an older target drops the value", emptyList<String>(), rows.sends)
        tapRow(app, m, BAND_1)
        holdItem(app, GAIN)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) withBands(it, it.bands.take(1)) else it } } }
        frames(OPEN_FRAMES)
        rule.runOnIdle { rows.entries.last().done("3.5") }
        assertEquals("a lost band drops the value", emptyList<String>(), rows.sends)
        assertEquals("premise: three entries opened", 3, rows.entries.size)
    }

    @Test
    fun aStaleOutcomeToastsNothing() {
        val rows = TestEqRows(outputs()).also { it.answer = EqOutcome.Stale }
        profiles(rows)
        val app = app(rows)
        val m = open(app)
        val shows = shows(app)
        tapRow(app, m, "$HP_SLOT:Profile")
        pick(app, 2)
        assertEquals("premise: the pick went out", listOf("pick $HP_SLOT Profile $CARD"), rows.sends)
        assertEquals("a stale pick toasts nothing", shows, shows(app))
        assertEquals("the row shows the device's word", "custom", rule.runOnIdle { (line(m, "$HP_SLOT:Profile") as EqPaneLine).value })
        tapRow(app, m, "$HP_SLOT:SavePreset")
        assertEquals("premise: the save went out", "save $HP_SLOT", rows.sends.last())
        assertEquals("a stale save toasts nothing", shows, shows(app))
    }

    @Test
    fun aPickOfTheCurrentChoiceSendsNothing() {
        val rows = TestEqRows(outputs())
        profiles(rows)
        val app = app(rows)
        val m = open(app)
        tapRow(app, m, "$HP_SLOT:On")
        pick(app, 0)
        tapRow(app, m, "$HP_SLOT:Mode")
        pick(app, 0)
        tapRow(app, m, "$HP_SLOT:Profile")
        pick(app, 1)
        assertNull("premise: each pick closes its sheet", app.stack.overlay)
        assertEquals("no send", emptyList<String>(), rows.sends)
    }

    @Test
    fun aNoSpaceSaveToastsTooManyPresets() {
        val rows = TestEqRows(outputs()).also { it.answer = EqOutcome.NoSpace }
        val app = app(rows)
        val m = open(app)
        val s = spyTap(app, m, "$HP_SLOT:SavePreset")
        assertEquals("premise: the save went out", listOf("save $HP_SLOT"), rows.sends)
        assertEquals(listOf("too many presets / "), s.toasts)
    }

    @Test
    fun theResetCustomRowAsksItsSupplier() {
        val rows = TestEqRows(outputs().map { it.copy(rows = it.rows + EqRow(EqKind.ResetCustom)) })
        val app = app(rows)
        val m = open(app)
        assertEquals("reset custom", rule.runOnIdle { (line(m, "$HP_SLOT:ResetCustom") as EqPaneLine).title })
        tapRow(app, m, "$HP_SLOT:ResetCustom")
        assertEquals("the row asks first", listOf<Any>("reset custom?", listOf("reset custom", "keep custom")), rule.runOnIdle { listOf(app.menu.title, app.menu.items) })
        pick(app, 1)
        assertEquals("keep custom sends nothing", emptyList<String>(), rows.sends)
        tapRow(app, m, "$HP_SLOT:ResetCustom")
        pick(app, 0)
        assertEquals("reset custom asks the supplier", listOf("reset $HP_SLOT"), rows.sends)
    }

    @Test
    fun aFullStoreAnswerShowsTooManyPresets() {
        val rows = TestEqRows(outputs()).also { it.answer = EqOutcome.NoSpace }
        profiles(rows)
        val app = app(rows)
        val m = open(app)
        val s = spyTap(app, m, "$HP_SLOT:Profile")
        pick(app, 2)
        assertEquals("premise: the pick went out", listOf("pick $HP_SLOT Profile $CARD"), rows.sends)
        assertEquals(listOf("too many presets / "), s.toasts)
    }

    @Test
    fun aNameTakenRenameToastsAndReopensTheEntryWithTheTypedName() {
        val rows = TestEqRows(outputs()).also { it.answer = EqOutcome.NameTaken }
        presets(rows)
        val app = app(rows)
        val m = open(app)
        val s = spyTap(app, m, "$HP_SLOT:RenamePreset")
        rule.runOnIdle { rows.entries.single().done(TYPED) }
        assertEquals("premise: the rename went out", listOf("rename $HP_SLOT $PRESET_KEY $TYPED"), rows.sends)
        assertEquals(listOf("rename preset / name taken"), s.toasts)
        assertEquals("the entry opens again", 2, rows.entries.size)
        assertEquals("holding the typed name", listOf<Any>("rename preset", TYPED), entryOf(rows.entries.last()).take(2))
    }

    @Test
    fun aNameNotAllowedRenameToastsAndReopensTheEntryWithTheTypedName() {
        val rows = TestEqRows(outputs()).also { it.answer = EqOutcome.NameNotAllowed }
        presets(rows)
        val app = app(rows)
        val m = open(app)
        val s = spyTap(app, m, "$HP_SLOT:RenamePreset")
        rule.runOnIdle { rows.entries.single().done(TYPED) }
        assertEquals("premise: the rename went out", listOf("rename $HP_SLOT $PRESET_KEY $TYPED"), rows.sends)
        assertEquals(listOf("rename preset / name not allowed"), s.toasts)
        assertEquals("the entry opens again", 2, rows.entries.size)
        assertEquals("holding the typed name", listOf<Any>("rename preset", TYPED), entryOf(rows.entries.last()).take(2))
    }

    @Test
    fun aFailedPresetSendToastsItsVerbAndThePresetsName() {
        val rows = TestEqRows(outputs()).also { it.answer = EqOutcome.Failed }
        presets(rows)
        val app = app(rows)
        val m = open(app)
        val save = spyTap(app, m, "$HP_SLOT:SavePreset")
        assertEquals("a save toasts its verb over nothing", listOf("preset not saved / "), save.toasts)
        val rename = spyTap(app, m, "$HP_SLOT:RenamePreset")
        rule.runOnIdle { rows.entries.last().done(TYPED) }
        assertEquals("a rename toasts its verb over the old name", listOf("preset not renamed / $PRESET"), rename.toasts)
        assertEquals("no entry again", 1, rows.entries.size)
        val delete = spyTap(app, m, "$HP_SLOT:DeletePreset")
        pick(app, 0)
        assertEquals("a delete toasts its verb over the name", listOf("preset not deleted / $PRESET"), delete.toasts)
        assertEquals("premise: three sends", listOf("save $HP_SLOT", "rename $HP_SLOT $PRESET_KEY $TYPED", "delete $HP_SLOT $PRESET_KEY"), rows.sends)
    }

    @Test
    fun aRefusedSendToastsNothing() {
        val rows = TestEqRows(outputs()).also { it.answer = EqOutcome.Refused }
        presets(rows)
        val app = app(rows)
        val m = open(app)
        val shows = shows(app)
        tapRow(app, m, "$HP_SLOT:SavePreset")
        tapRow(app, m, "$HP_SLOT:RenamePreset")
        rule.runOnIdle { rows.entries.last().done(TYPED) }
        tapRow(app, m, "$HP_SLOT:DeletePreset")
        pick(app, 0)
        tapRow(app, m, "$HP_SLOT:Profile")
        pick(app, 0)
        assertEquals("premise: four sends", listOf("save $HP_SLOT", "rename $HP_SLOT $PRESET_KEY $TYPED", "delete $HP_SLOT $PRESET_KEY", "pick $HP_SLOT Profile custom"), rows.sends)
        assertEquals("no toast", shows, shows(app))
        assertEquals("no entry again", 1, rows.entries.size)
    }

    @Test
    fun aHeldAddBandEntersFromItsFirstFrameAndAStaleReplyEndsTheRun() {
        val rows = TestEqRows(outputs(1)).also { it.holds = true }
        val app = app(rows)
        val m = open(app)
        val p = pane(app)
        rule.runOnIdle { app.host.eqTap(m, line(m, "$HP_SLOT:AddBand")) }
        frames(1)
        val t = now()
        val added = rule.runOnIdle { p.pivots.value.first { it.name == HP }.rows.first { it.key == "$HP_SLOT:Band:1" } }
        assertEquals("premise: the add waits for its reply", listOf("add $HP_SLOT"), rows.sends)
        val d = Tok.motion_listChange.toLong()
        assertEquals("the laid over band starts unseen at its first frame", 0f, p.enter(HP, added, t).alpha, 0f)
        assertEquals("from the shift", pen64(Tok.motion_listChangeShift, page.ppu), p.enter(HP, added, t).shift)
        assertEquals("it eases out from there", Curves.easeOut.at(HALF_WAY), p.enter(HP, added, t + d / 2).alpha, EPS)
        rule.runOnIdle { rows.reply(EqOutcome.Stale) }
        frames(1)
        assertEquals("a stale reply ends the run", RowEnter.Rest, p.enter(HP, added, t + d / 2))
        assertTrue("the band leaves with the reply", rule.runOnIdle { p.pivots.value.first { it.name == HP }.rows.none { it.key == added.key } })
    }

    @Test
    fun aBandAddRepliedAfterItsVisitEndedOpensNoSheet() {
        val rows = TestEqRows(outputs(1)).also { it.holds = true }
        val app = app(rows)
        val m = open(app)
        val s = spyTap(app, m, "$HP_SLOT:AddBand")
        assertEquals("premise: the add waits for its reply", listOf("add $HP_SLOT"), rows.sends)
        val again = TestScreen.Eq(app.stack.entry(), ID, HP)
        rule.runOnIdle { app.stack.reset(listOf(again)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: a new visit", again, app.stack.top)
        rule.runOnIdle { rows.reply(EqOutcome.Done, lands = false) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) withBands(it, it.bands + testBand(1)) else it } } }
        frames(OPEN_FRAMES)
        assertEquals("the old visit's wait asks for no band sheet", 0, s.fieldAsks)
        assertNull("no band sheet opens", app.host.fieldSheet)
    }

    @Test
    fun aBackOutOfAnAdjustWithNoStepSendsNothing() {
        val rows = TestEqRows(outputs())
        val app = app(rows)
        val m = open(app)
        tapRow(app, m, PREAMP)
        assertEquals("premise: the preamp adjust is up", OverKind.Adjust, app.stack.overlay?.kind)
        back()
        assertEquals("no send", emptyList<String>(), rows.sends)
    }

    @Test
    fun aTypedPreampForALeftPivotAnOlderTargetOrAnUnlistedRowIsDropped() {
        val rows = TestEqRows(outputs(target = 1L))
        val app = app(rows)
        val m = open(app)
        typeValue(app, m, PREAMP)
        rule.runOnIdle { m.switchTo(1, now()) }
        frames(OPEN_FRAMES)
        rule.runOnIdle { rows.entries.last().done(TYPED_DB) }
        assertEquals("a left pivot drops the value", emptyList<String>(), rows.sends)
        rule.runOnIdle { m.switchTo(0, now()) }
        frames(OPEN_FRAMES)
        typeValue(app, m, PREAMP)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) it.copy(target = 2L) else it } } }
        frames(OPEN_FRAMES)
        rule.runOnIdle { rows.entries.last().done(TYPED_DB) }
        assertEquals("an older target drops the value", emptyList<String>(), rows.sends)
        typeValue(app, m, PREAMP)
        rule.runOnIdle { rows.land { ps -> ps.map { if (it.slot == HP_SLOT) it.copy(rows = it.rows.filter { r -> r.kind != EqKind.Preamp }) else it } } }
        frames(OPEN_FRAMES)
        rule.runOnIdle { rows.entries.last().done(TYPED_DB) }
        assertEquals("an unlisted row drops the value", emptyList<String>(), rows.sends)
        assertEquals("premise: three entries opened", 3, rows.entries.size)
    }

    @Test
    fun theEntriesTakeTheUnitsTitlesKeysUnitsAndLengths() {
        val rows = TestEqRows(listOf(parametric(HP_SLOT, HP, listOf(testBand(0)), preamp10 = PREAMP10), graphic(BT_SLOT, BT, listOf(GRAPHIC10))))
        presets(rows)
        val app = app(rows)
        val m = open(app)
        typeValue(app, m, PREAMP)
        val pre = rows.entries.last()
        assertEquals(listOf<Any>("preamp", "-6.1", false, "-.", "db", 1, 1, ENTRY_MAX), entryOf(pre))
        assertEquals("-24 to 0 db", pre.refusal("1"))
        assertNull(pre.refusal("-6"))
        tapRow(app, m, BAND_0)
        val band = listOf(1 to listOf<Any>("frequency", "1000", false, "", "hz", 0, 1, ENTRY_MAX), GAIN to listOf<Any>("gain", "0.0", false, "-.", "db", 1, 1, ENTRY_MAX), 3 to listOf<Any>("q", "1.40", false, ".", "", 2, 1, ENTRY_MAX))
        val refused = listOf("19" to "20 to 20000 hz", "12.1" to "-24 to +12 db", "11" to "0.1 to 10")
        band.forEachIndexed { k, (i, want) ->
            holdItem(app, i)
            val e = rows.entries.last()
            assertEquals(want, entryOf(e))
            assertTrue("a band entry returns to its sheet", e.reopen && !e.adjust)
            assertEquals(refused[k].second, e.refusal(refused[k].first))
            rule.runOnIdle { app.host.fieldReturn(e.adjust) }
            frames(OPEN_FRAMES)
        }
        back()
        tapRow(app, m, "$HP_SLOT:RenamePreset")
        assertEquals(listOf<Any>("rename preset", PRESET, true, "", "", 0, 1, NAME_MAX), entryOf(rows.entries.last()))
        rule.runOnIdle { m.switchTo(1, now()) }
        frames(OPEN_FRAMES)
        typeValue(app, m, "$BT_SLOT:Gain:0")
        val gain = rows.entries.last()
        assertEquals(listOf<Any>("gain", "4.5", false, "-.", "db", 1, 1, ENTRY_MAX), entryOf(gain))
        assertEquals("-24 to +12 db", gain.refusal("12.1"))
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val EQ = "eq"
        private const val HP = "headphones"
        private const val BT = "bluetooth"
        private const val WIRED = "wired"
        private const val HP_SLOT = "hp"
        private const val BT_SLOT = "bt"
        private const val BAND_0 = "hp:Band:0"
        private const val BAND_1 = "hp:Band:1"
        private const val CARD = "card/a.txt"
        private const val PREAMP = "hp:Preamp"
        private const val PRESET = "HD 650"
        private const val PRESET_KEY = "hd650"
        private const val TYPED = "WARM"
        private const val TYPED_DB = "-3"
        private const val PREAMP10 = -61
        private const val GRAPHIC10 = 45
        private const val ENTRY_MAX = 8
        private const val NAME_MAX = 64
        private const val GAIN = 2
        private const val LONG = 24
        private const val OPEN_FRAMES = 30
        private const val STEP_FRAMES = 2
        private const val CUT_FRAMES = 2
        private const val FRAME = 16L
        private const val HALF_WAY = 0.5f
        private const val EPS = 0.01f
        private val ID = EqId(EQ)
    }
}
