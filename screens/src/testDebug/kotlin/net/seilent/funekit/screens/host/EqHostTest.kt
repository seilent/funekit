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
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.awaitCancellation
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.scale
import net.seilent.funekit.screens.eq.EqId
import net.seilent.funekit.screens.eq.EqLine
import net.seilent.funekit.screens.eq.EqModel
import net.seilent.funekit.screens.eq.EqPage
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.music.pivotSwitch
import net.seilent.funekit.screens.settings.AimKind
import net.seilent.funekit.screens.settings.RowPivot
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
class EqHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val page = scale(Window(W, H, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)

    private fun lines(prefix: String, n: Int, extra: List<String> = emptyList()): List<EqLine> = extra.map { TestLine(it) } + (0 until n).map { TestLine("$prefix:$it") }

    private fun outputs(n: Int = ROWS, preamp: Boolean = false): List<RowPivot<EqLine>> = listOf(
        RowPivot(HP, lines("hp", n, if (preamp) listOf(HP_PREAMP) else emptyList())),
        RowPivot(BT, lines("bt", n, if (preamp) listOf(BT_PREAMP) else emptyList())),
    )

    private fun app(pane: TestPane = TestPane(outputs()), root: Boolean = false, vararg more: EqPage): TestApp = TestApp(page, page, null, StillLevel) { rule.mainClock.currentTime }.also {
        it.eqList = listOf(EqPage(ID, pane)) + more
        it.compose(rule, root)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun now(): Long = rule.mainClock.currentTime

    private fun open(app: TestApp, pivot: String? = null): Pair<TestScreen, EqModel> {
        val sc = TestScreen.Eq(app.stack.entry(), ID, pivot)
        rule.runOnIdle { app.stack.reset(listOf(sc)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the eq page is up", sc, app.stack.top)
        return sc to app.models[sc.entry] as EqModel
    }

    private fun shot() {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
    }

    private fun tap(o: Offset) {
        rule.onRoot().performTouchInput {
            down(o)
            up()
        }
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
    }

    private fun rowAt(app: TestApp, m: EqModel, i: Int): Offset {
        val d = rule.runOnIdle { app.host.types.eq.drawOf(m) }
        return Offset(page.widthPx / 2f, (d.top + m.tops(m.pivot)[i] + m.heights(m.pivot)[i] / 2).toFloat())
    }

    private fun sheet(items: List<String> = FOUR) = SettingSheet(MenuForm.Sheet, TITLE, items)

    private fun alphas(pane: TestPane, key: String): List<Float> = (0 until RAMP_FRAMES).map {
        frames(1)
        shot()
        pane.looks.getValue(key).enter.alpha
    }

    @Test
    fun anEqScreenOpensAtItsPivot() {
        val app = app(root = true)
        val (_, m) = open(app, BT)
        assertEquals("the page opens at its pivot", 1, m.pivot)
        assertEquals("the chrome shows the pivot", 1, app.host.eqChromes.getValue(ID).active)
    }

    @Test
    fun anEqPageHeaderTakesTheSettingsRowCaseAndWeight() {
        val app = app()
        open(app)
        val h = app.host.eqChromes.getValue(ID).header
        assertEquals("the eq header takes the row case", "EQ", h.text)
        assertEquals("the eq header takes the row weight", FontWeight.Bold, h.runs[0].layout.layoutInput.style.fontWeight)
    }

    @Test
    fun anEqPageWhoseOnePivotHasNoNameDrawsAChromeWithNoPivots() {
        val app = app(TestPane(listOf(RowPivot("", lines("hp", ROWS)))))
        open(app)
        assertEquals("the chrome has no pivots", 0, app.host.eqChromes.getValue(ID).count)
    }

    @Test
    fun aSecondEnterOfTheSameEqScreenKeepsTheSwitchedPivot() {
        val app = app()
        val (sc, m) = open(app, BT)
        rule.runOnIdle { m.switchTo(0, now()) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the switch lands", 0, m.pivot)
        rule.runOnIdle { app.host.enter(sc) }
        frames(OPEN_FRAMES)
        assertSame("premise: the model is kept", m, app.models[sc.entry])
        assertEquals("the page keeps the switched pivot", 0, m.pivot)
    }

    @Test
    fun anEqPageListedTwiceFailsTheListing() {
        val failed = runCatching { app(TestPane(outputs()), false, EqPage(ID, TestPane(outputs()))) }.exceptionOrNull()
        assertTrue("the listing fails, $failed", failed is IllegalArgumentException)
        assertEquals("an eq page is listed once", failed?.message)
    }

    @Test
    fun anEqScreenNamingAnUnlistedPageFailsAtEnter() {
        val app = app()
        val other = TestScreen.Eq(app.stack.entry(), EqId(OTHER))
        val refused = rule.runOnIdle { runCatching { app.host.enter(other) }.exceptionOrNull() }
        assertTrue("the enter refuses, $refused", refused is IllegalArgumentException)
        assertEquals("an eq screen names a listed page", refused?.message)
    }

    @Test
    fun anEnterThatMakesTheModelTellsThePaneItOpened() {
        val pane = TestPane(outputs())
        val app = app(pane)
        val (sc, _) = open(app)
        assertEquals("one open", 1, pane.opens)
        rule.runOnIdle { app.host.enter(sc) }
        frames(OPEN_FRAMES)
        assertEquals("one open", 1, pane.opens)
    }

    @Test
    fun aRowTapReachesThePaneWithTheShownPivotAndItsLine() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        val (_, m) = open(app, BT)
        tap(rowAt(app, m, 1))
        frames(OPEN_FRAMES)
        assertEquals(listOf(BT to "bt:1"), pane.taps)
    }

    @Test
    fun aRowHoldReachesThePaneAndEndsThePressLook() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        val (_, m) = open(app, BT)
        val at = rowAt(app, m, 1)
        rule.onRoot().performTouchInput { down(at) }
        frames(HOLD_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals("the pane takes the hold", listOf(BT to "bt:1"), pane.holds)
        assertFalse("the row is not lit", m.lit.on(1, now()))
    }

    @Test
    fun aChromeSwitchTellsThePaneThePivotLeft() {
        val pane = TestPane(outputs())
        val app = app(pane)
        val (sc, m) = open(app)
        rule.runOnIdle {
            m.down(2, now())
            m.switchTo(1, now())
        }
        frames(OPEN_FRAMES)
        pane.lefts.clear()
        val (pm, c) = rule.runOnIdle { app.host.pivot(sc)!! }
        rule.runOnIdle { pivotSwitch(pm, c, 0, now()) }
        frames(OPEN_FRAMES)
        assertEquals(listOf(BT), pane.lefts)
        assertEquals("the new pivot opens at row 0", 0, m.focusRow(0))
    }

    @Test
    fun aRestoredModelGetsItsWiringFromApplied() {
        val pane = TestPane(outputs())
        val app = app(pane)
        rule.runOnIdle {
            app.models.restore(ENTRY, ID, 0, null, listOf(HP))
            app.host.applied()
        }
        val m = app.models[ENTRY] as EqModel
        rule.runOnIdle { m.switchTo(1, now()) }
        assertEquals(listOf(HP), pane.lefts)
    }

    @Test
    fun aDownOnAnEqRowGivesThePaneItsGlow() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        val (_, m) = open(app, BT)
        val at = rowAt(app, m, 1)
        rule.onRoot().performTouchInput { down(at) }
        frames(2)
        shot()
        assertSame("the pane gets the model's glow", m.glow, pane.glows["bt:1"]?.glow)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
    }

    @Test
    fun anEnteringRowRedrawsEachFrameWithThePanesEnter() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        val (_, m) = open(app, BT)
        shot()
        rule.runOnIdle {
            pane.ramp("bt:1", now())
            m.poke()
        }
        val a = alphas(pane, "bt:1")
        assertTrue("the enter alpha rises, $a", a.zipWithNext().all { (x, y) -> y > x })
    }

    @Test
    fun aHeldRowStartsWhenTheOverlayLeaves() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        open(app, BT)
        rule.runOnIdle { app.host.openSheet(sheet()) { true } }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            pane.held = setOf("bt:1")
            pane.rests = 0
        }
        frames(OPEN_FRAMES)
        assertEquals("no rest while the sheet shows", 0, pane.rests)
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        assertEquals("one rest after the close", 1, pane.rests)
        frames(OPEN_FRAMES)
        shot()
        assertEquals("the held row runs", 1f, pane.looks.getValue("bt:1").enter.alpha)
    }

    @Test
    fun aBandAddedUnderASheetTakesItsOwnLook() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        open(app, BT)
        rule.runOnIdle { app.host.openSheet(sheet()) { true } }
        frames(OPEN_FRAMES)
        shot()
        val bt = lines("bt", ROWS)
        rule.runOnIdle {
            pane.held = setOf(ADDED)
            pane.pivots.value = listOf(outputs()[0], RowPivot(BT, bt.take(2) + TestLine(ADDED) + bt.drop(2)))
        }
        frames(RESIZE_FRAMES)
        shot()
        assertEquals("premise: the sheet is up", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("the new band enters held", 0f, pane.looks.getValue(ADDED).enter.alpha)
        assertEquals("the shifted row takes its own look", 1f, pane.looks.getValue("bt:2").enter.alpha)
    }

    @Test
    fun aSameCountLandingWakesTheFrameLoop() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        open(app, BT)
        shot()
        rule.runOnIdle {
            val next = listOf(outputs()[0], RowPivot(BT, lines("bt:n", ROWS)))
            for (l in next[1].rows) pane.ramp(l.key, now())
            pane.pivots.value = next
        }
        frames(RESIZE_FRAMES)
        shot()
        assertTrue("premise: the landed rows draw", "bt:n:1" in pane.looks)
        val a = alphas(pane, "bt:n:1")
        assertTrue("the enter alpha rises, $a", a.zipWithNext().all { (x, y) -> y > x })
    }

    @Test
    fun aLiftSheetFromAnEqRowLiftsTheFocusedRowAndItsCloseDropsIt() {
        val pane = TestPane(outputs(LONG))
        val app = app(pane, root = true)
        val (_, m) = open(app, BT)
        rule.runOnIdle { m.aimTo(m.base(1), AimKind.Into) }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            m.down(LONG - 1, now())
            m.cancel(now())
        }
        frames(OPEN_FRAMES)
        val before = m.pane.px
        assertEquals("premise: the list rests at its end", m.base(1), before)
        assertTrue(rule.runOnIdle { app.host.actsOf(m).sheet(SettingSheet(MenuForm.Sheet, TITLE, FOUR, lift = true, edit = "bt:${LONG - 1}", focus = 0), { true }) })
        frames(OPEN_FRAMES)
        assertTrue("the sheet lifts the row, ${m.pane.px} against $before", m.pane.px > before)
        rule.runOnIdle { app.stack.close() }
        frames(2 * OPEN_FRAMES)
        assertEquals("the row drops back after the close", before, m.pane.px)
    }

    @Test
    fun aHandOffDuringTheSheetsRiseKeepsTheLiftAndOpensTheEntryAtOnce() {
        val pane = TestPane(outputs(LONG))
        val app = app(pane, root = true)
        val (_, m) = open(app, BT)
        rule.runOnIdle { m.aimTo(m.base(1), AimKind.Into) }
        frames(OPEN_FRAMES)
        rule.runOnIdle {
            m.down(LONG - 1, now())
            m.cancel(now())
        }
        frames(OPEN_FRAMES)
        frames(((Tok.scrollbar_hold + Tok.scrollbar_fade).toLong() / FRAME_MS).toInt() + 1)
        val before = m.pane.px
        assertEquals("premise: the list rests at its end", m.base(1), before)
        assertEquals("premise: the page thumb is out", 0f, m.fade.at(now()))
        val acts = rule.runOnIdle { app.host.actsOf(m) }
        assertTrue(rule.runOnIdle { acts.sheet(SettingSheet(MenuForm.Sheet, TITLE, FOUR, lift = true, edit = "bt:${LONG - 1}", focus = 0), { true }) })
        frames(CUT_FRAMES)
        val sheet = app.stack.overlay
        assertTrue("premise: the sheet rises", sheet != null && sheet.moving(now()))
        assertEquals("premise: the sheet lifts the row", AimKind.Lift, m.aimKind)
        val at = m.pane.px
        val entry = Overlay(OverKind.Sheet)
        rule.runOnIdle {
            acts.handOff()
            app.stack.open(entry)
        }
        frames(1)
        val shown = app.stack.overlay
        val swap = app.stack.clock
        val thumb = m.fade.at(swap)
        val px = mutableListOf(m.pane.px)
        repeat(OPEN_FRAMES) {
            frames(1)
            px += m.pane.px
        }
        assertSame("the entry opens at the hand off", entry, shown)
        assertEquals("the entry starts at the hand off", swap, entry.start)
        assertEquals("the hand off shows no page thumb", 0f, thumb)
        assertTrue("the row never falls below its place at the hand off, $px against $at", px.all { it >= at })
        rule.runOnIdle { app.stack.close() }
        frames(2 * OPEN_FRAMES)
        assertEquals("the entry's close drops the lift", before, m.pane.px)
    }

    @Test
    fun aSheetTellsThePaneWhenItClosesWithNoPickOnly() {
        val app = app()
        val (_, m) = open(app)
        val acts = rule.runOnIdle { app.host.actsOf(m) }
        var closed = 0
        val picks = mutableListOf<Int>()
        val pick = { i: Int ->
            picks += i
            true
        }
        assertTrue(rule.runOnIdle { acts.sheet(sheet(), pick) { closed++ } })
        frames(OPEN_FRAMES)
        back()
        assertEquals("closed after a back", 1, closed)
        assertTrue(rule.runOnIdle { acts.sheet(sheet(), pick) { closed++ } })
        frames(OPEN_FRAMES)
        rule.runOnIdle { if (app.menuHost.act(1)) app.stack.accept() }
        frames(OPEN_FRAMES)
        assertNull("premise: the pick closes the sheet", app.stack.overlay)
        assertEquals(listOf(1), picks)
        assertEquals("no closed after a pick", 1, closed)
    }

    @Test
    fun closeClosesThePanesOwnOverlayOnly() {
        val app = app()
        val (_, m) = open(app)
        val acts = rule.runOnIdle { app.host.actsOf(m) }
        assertTrue(rule.runOnIdle { acts.sheet(sheet(), { true }) })
        frames(OPEN_FRAMES)
        rule.runOnIdle { acts.close() }
        frames(OPEN_FRAMES)
        assertNull("the pane's sheet closes", app.stack.overlay)
        rule.runOnIdle { app.host.openSheet(sheet()) { true } }
        frames(OPEN_FRAMES)
        rule.runOnIdle { acts.close() }
        frames(OPEN_FRAMES)
        assertEquals("another overlay stays", OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun theActsRefuseOffTheTopInMotionAndUnderAnOverlay() {
        val app = app()
        val (_, m) = open(app)
        val acts = rule.runOnIdle { app.host.actsOf(m) }
        fun refuses(): Boolean = rule.runOnIdle {
            !acts.sheet(sheet(), { true }) && !acts.fields(BandFields()) && !acts.adjust { hud, end -> TestAdjust(HP_PREAMP, hud, end) }
        }
        rule.runOnIdle { app.stack.push(TestScreen.Music(MusicEntry(app.stack.entry(), MusicPage.Album(ALBUM))), MoveKind.T2) }
        frames(OPEN_FRAMES)
        assertTrue("the acts refuse off the top", refuses())
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(2)
        assertTrue("premise: the pop moves", app.stack.phase != Phase.Rest)
        assertTrue("the acts refuse in motion", refuses())
        frames(OPEN_FRAMES)
        rule.runOnIdle { app.host.openSheet(sheet()) { true } }
        frames(OPEN_FRAMES)
        assertTrue("the acts refuse under an overlay", refuses())
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        assertTrue("premise: the field sheet opens", rule.runOnIdle { acts.fields(BandFields()) })
        frames(OPEN_FRAMES)
        assertTrue("premise: the hold takes the keyboard", rule.runOnIdle { app.menuHost.hold!!(Q) })
        frames(OPEN_FRAMES)
        assertNull("premise: the sheet goes for the keyboard", app.stack.overlay)
        assertTrue("the acts refuse while a field run waits for its keyboard", refuses())
    }

    @Test
    fun aRowAdjustOnAnotherHudFailsPlainly() {
        val app = app(TestPane(outputs(preamp = true)))
        val (_, m) = open(app, BT)
        val failed = rule.runOnIdle { runCatching { app.host.actsOf(m).adjust { _, end -> TestAdjust(BT_PREAMP, AdjustHud(), end) } }.exceptionOrNull() }
        assertEquals("the adjust fails", "a row adjust shows on the host's hud", (failed as? IllegalArgumentException)?.message)
        assertNull("nothing opens", app.stack.overlay)
    }

    @Test
    fun theActsTimeIsTheHostClock() {
        val app = app()
        val (_, m) = open(app)
        assertEquals("the acts time is the host clock", now(), rule.runOnIdle { app.host.actsOf(m).now() })
    }

    @Test
    fun aRelistedPaneDrawsTheNextFrame() {
        val app = app(root = true)
        open(app, BT)
        shot()
        val next = TestPane(outputs())
        rule.runOnIdle { app.eqList = listOf(EqPage(ID, next)) }
        frames(2)
        shot()
        assertTrue("the relisted pane draws", next.drawn.isNotEmpty())
    }

    @Test
    fun aMixedChoiceSheetWithNoValuesKeepsItsMixedItems() {
        val app = app()
        val (_, m) = open(app)
        val s = SettingSheet(MenuForm.Sheet, PROFILE, listOf("off", "card/a.txt"), mixed = listOf(false, true))
        assertTrue(rule.runOnIdle { app.host.actsOf(m).sheet(s, { true }) })
        frames(OPEN_FRAMES)
        assertEquals(listOf(false, true), app.menu.mixed)
    }

    @Test
    fun aFieldSheetFromAnEqRowOpensOverTheEqPage() {
        val app = app(root = true)
        val (_, m) = open(app, BT)
        val f = BandFields()
        assertTrue("the field sheet opens", rule.runOnIdle { app.host.actsOf(m).fields(f) })
        frames(OPEN_FRAMES)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertSame(f, app.host.fieldSheet)
        val b = app.host.eqChromes.getValue(ID).pivotBox(0)
        tap(Offset((b.x0 + b.x1) / 2f, (b.y0 + b.y1) / 2f))
        frames(OPEN_FRAMES)
        assertEquals("a pivot tap under the sheet leaves the page on bluetooth", 1, m.pivot)
    }

    @Test
    fun anEqAdjustMarksItsRowAndEndsOnThePage() {
        val app = app(TestPane(outputs(preamp = true)))
        val (sc, m) = open(app, BT)
        var a: TestAdjust? = null
        assertTrue(rule.runOnIdle { app.host.actsOf(m).adjust { hud, end -> TestAdjust(BT_PREAMP, hud, end).also { a = it } } })
        frames(OPEN_FRAMES)
        assertEquals("the row shows its edit", BT_PREAMP, m.editing)
        assertEquals(OverKind.Adjust, app.stack.overlay?.kind)
        tap(Offset(2f, 2f))
        frames(OPEN_FRAMES)
        assertNull("the adjust ends on the page", app.stack.overlay)
        assertEquals(sc, app.stack.top)
        assertNull(m.editing)
        assertEquals(listOf("open", "tap Exit", "finish"), a?.calls)
    }

    @Test
    fun anEqAdjustNumberCutsTheHudAndEndsTheEdit() {
        val app = app(TestPane(outputs(preamp = true)))
        val (_, m) = open(app, BT)
        var a: TestAdjust? = null
        assertTrue(rule.runOnIdle { app.host.actsOf(m).adjust { hud, end -> TestAdjust(BT_PREAMP, hud, end).also { a = it } } })
        frames(OPEN_FRAMES)
        rule.runOnIdle { a!!.number(0) }
        frames(CUT_FRAMES)
        assertNull("the hud is cut at once", app.stack.overlay)
        assertNull(m.editing)
        assertTrue("finish" in a!!.calls)
    }

    @Test
    fun anEqAdjustEndsWhenItsKeyMovesToTheOtherPivot() {
        val pane = TestPane(outputs(preamp = true))
        val app = app(pane)
        val (_, m) = open(app, BT)
        var a: TestAdjust? = null
        assertTrue(rule.runOnIdle { app.host.actsOf(m).adjust { hud, end -> TestAdjust(BT_PREAMP, hud, end).also { a = it } } })
        frames(OPEN_FRAMES)
        rule.runOnIdle { pane.pivots.value = listOf(RowPivot(HP, lines("hp", ROWS, listOf(BT_PREAMP))), RowPivot(BT, lines("bt", ROWS))) }
        frames(1)
        assertNull("the adjust ends with its landing", m.editing)
        frames(OPEN_FRAMES)
        assertNull("the hud goes", app.stack.overlay)
        assertTrue("finish" in a!!.calls)
        assertFalse("no revert", "revert" in a.calls)
    }

    @Test
    fun anEqAdjustEndsWhenAHeldNameMovesTheShownPivot() {
        val pane = TestPane(outputs(preamp = true).take(1))
        val app = app(pane)
        val (_, m) = open(app, BT)
        assertEquals("premise: the page shows headphones until bluetooth lands", 0, m.pivot)
        var a: TestAdjust? = null
        assertTrue(rule.runOnIdle { app.host.actsOf(m).adjust { hud, end -> TestAdjust(HP_PREAMP, hud, end).also { a = it } } })
        frames(OPEN_FRAMES)
        rule.runOnIdle { pane.pivots.value = outputs(preamp = true) }
        frames(1)
        assertEquals("the page moves to bluetooth", 1, m.pivot)
        assertNull("the adjust ends with its landing", m.editing)
        frames(OPEN_FRAMES)
        assertNull("the hud goes", app.stack.overlay)
        assertTrue("finish" in a!!.calls)
        assertFalse("no revert", "revert" in a.calls)
    }

    @Test
    fun theEqPagePopsWithT4ToThePageUnderItAndT4hToHome() {
        val app = app()
        val sc = TestScreen.Eq(ENTRY, ID)
        val album = TestScreen.Music(MusicEntry(ENTRY + 1, MusicPage.Album(ALBUM)))
        assertEquals(MoveKind.T4, app.host.popKind(Turn(MoveKind.None, sc, album, popping = true)))
        app.homeWords = setOf(EQ)
        assertEquals(MoveKind.T4h, app.host.popKind(Turn(MoveKind.None, sc, null, popping = true)))
    }

    @Test
    fun aHeldBackOnTheEqHeaderTargetsIt() {
        val app = app()
        val (sc, _) = open(app)
        val b = app.host.eqChromes.getValue(ID).headerBox()
        assertEquals(true, rule.runOnIdle { app.host.heldTarget(sc, Offset((b.x0 + b.x1) / 2f, (b.y0 + b.y1) / 2f)) })
    }

    @Test
    fun theFocusActMovesTheFocusWhenItsRowLands() {
        val pane = TestPane(outputs())
        val app = app(pane)
        val (_, m) = open(app, BT)
        rule.runOnIdle { app.host.actsOf(m).focus(BAND) }
        frames(1)
        assertEquals("premise: the key has not landed", 0, m.focusRow(1))
        rule.runOnIdle { pane.pivots.value = listOf(outputs()[0], RowPivot(BT, lines("bt", 2) + TestLine(BAND))) }
        frames(RESIZE_FRAMES)
        assertEquals(2, m.focusRow(1))
    }

    @Test
    fun theToastActShowsItsLines() {
        val app = app()
        val (_, m) = open(app)
        rule.runOnIdle { app.host.actsOf(m).toast(PRESETS, "") }
        frames(RESIZE_FRAMES)
        assertEquals("the toast shows its line", PRESETS, app.toast.artist)
    }

    @Test
    fun aLandingThatMovesTheShownPivotAtItsIndexDrawsTheNextFromItsTop() {
        val pane = TestPane(outputs(LONG))
        val app = app(pane, root = true)
        val (_, m) = open(app)
        rule.runOnIdle { m.aimTo(SCROLLED * m.heights(0)[0], AimKind.Into) }
        frames(OPEN_FRAMES)
        assertTrue("premise: headphones is scrolled", m.pane.px > 0)
        rule.runOnIdle { pane.pivots.value = outputs(LONG).drop(1) }
        frames(OPEN_FRAMES)
        pane.drawn.clear()
        shot()
        assertEquals("the next pivot draws from its top", 0, m.pane.px)
        assertEquals("the first drawn row is row 0", "bt:0", pane.drawn.firstOrNull())
    }

    @Test
    fun aHeldFieldRunHoldsTheRestUntilItsReturn() {
        val pane = TestPane(outputs())
        val app = app(pane, root = true)
        val (_, m) = open(app, BT)
        assertTrue(rule.runOnIdle { app.host.actsOf(m).fields(BandFields()) })
        frames(OPEN_FRAMES)
        rule.runOnIdle { pane.rests = 0 }
        assertTrue(rule.runOnIdle { app.menuHost.hold!!(Q) })
        frames(OPEN_FRAMES)
        assertNull("premise: the sheet goes for the keyboard", app.stack.overlay)
        assertEquals("no rest while the run is held", 0, pane.rests)
        rule.runOnIdle { app.host.fieldReturn(false) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the sheet reopens", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals("no rest at the reopen", 0, pane.rests)
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        assertEquals("one rest once it closes", 1, pane.rests)
    }

    @Test
    fun aPaneLaunchStartsAtOnceOnTheHostScope() {
        val app = app()
        val (_, m) = open(app)
        val log = mutableListOf<String>()
        rule.runOnIdle {
            app.host.actsOf(m).launch {
                log += "a"
                try {
                    awaitCancellation()
                } finally {
                    log += "cancelled"
                }
            }
            log += "b"
        }
        assertEquals("the block starts at once", listOf("a", "b"), log)
        rule.runOnIdle { app.hosted = false }
        frames(RESIZE_FRAMES)
        assertEquals("the host's scope ends the block", listOf("a", "b", "cancelled"), log)
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val EQ = "eq"
        private const val HP = "headphones"
        private const val BT = "bluetooth"
        private const val OTHER = "other"
        private const val TITLE = "type"
        private const val PROFILE = "profile"
        private const val PRESETS = "too many presets"
        private const val HP_PREAMP = "hp:preamp"
        private const val BT_PREAMP = "bt:preamp"
        private const val BAND = "bt:band:2"
        private const val ADDED = "bt:band:3"
        private const val ENTRY = 99L
        private const val ALBUM = 20L
        private const val ROWS = 4
        private const val LONG = 24
        private const val SCROLLED = 3
        private const val Q = 3
        private const val OPEN_FRAMES = 30
        private const val HOLD_FRAMES = 40
        private const val CUT_FRAMES = 2
        private const val FRAME_MS = 16L
        private const val RESIZE_FRAMES = 2
        private const val RAMP_FRAMES = 3
        private val ID = EqId(EQ)
        private val FOUR = listOf("off", "peak", "low shelf", "high shelf")
    }
}
