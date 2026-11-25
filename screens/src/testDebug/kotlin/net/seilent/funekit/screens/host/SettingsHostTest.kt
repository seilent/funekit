package net.seilent.funekit.screens.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.FuneTheme
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.kit.touch.Kind
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.nowplaying.AdjustCol
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.settings.AimKind
import net.seilent.funekit.screens.settings.HeaderLook
import net.seilent.funekit.screens.settings.RangeAdjust
import net.seilent.funekit.screens.settings.SETTINGS
import net.seilent.funekit.screens.settings.SettingAdjust
import net.seilent.funekit.screens.settings.SettingFlag
import net.seilent.funekit.screens.settings.SettingKind
import net.seilent.funekit.screens.settings.SettingPivot
import net.seilent.funekit.screens.settings.SettingRow
import net.seilent.funekit.screens.settings.SettingSheet
import net.seilent.funekit.screens.settings.SettingsId
import net.seilent.funekit.screens.settings.SettingsModel
import net.seilent.funekit.screens.settings.SettingsPage
import net.seilent.funekit.screens.settings.SettingsRows
import net.seilent.funekit.screens.settings.rememberSettingsChrome
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
        val drafts = mutableListOf<Pair<String, String>>()
        val entries = mutableListOf<Pair<String, Int>>()
        var takes = false
        var onEntry: () -> Unit = {}
        var replies: Channel<Boolean>? = null
        var adjusts: (SettingRow, Int) -> SettingAdjust? = { _, _ -> null }
        var opened: Set<String> = emptySet()
        var onSet: () -> Unit = {}

        override fun open(row: SettingRow): Boolean {
            opens += row.key
            return row.key in opened
        }

        override suspend fun set(key: String, value: String): Boolean {
            sets += mapOf(key to value)
            onSet()
            val r = replies ?: return true.also { echo(key, value) }
            return r.receive()
        }

        private fun echo(key: String, v: String) {
            pivots.value = pivots.value.map { p -> SettingPivot(p.name, p.rows.map { if (it.key == key && it.kind == SettingKind.Range && it.swatches.isEmpty()) it.copy(value = v, level = v.toInt()) else it }) }
        }

        override fun action(key: String) {
            actions += key
        }

        override fun draft(key: String, value: String) {
            drafts += key to value
        }

        override fun adjust(row: SettingRow, level: Int): SettingAdjust? = adjusts(row, level)

        override fun hasEntry(row: SettingRow): Boolean = takes

        override fun entry(row: SettingRow, side: Int) {
            entries += row.key to side
            onEntry()
        }
    }

    private fun balance(level: Int = 0) = SettingRow(BAL, BAL, level.toString(), SettingKind.Range, min = -LIMIT, max = LIMIT, step = 1, level = level)

    private fun hudRows(start: SettingRow = balance()) = Rows(pivot(start)).also { r ->
        r.adjusts = { _, l -> SettingAdjust(listOf(AdjustCol(l.toString(), LEFT), AdjustCol(l.toString(), RIGHT)), BAL, l.toString()) }
    }

    private val upRightAt = Offset(px(Tok.canvasW - Tok.edge - Tok.adjustColumn, page.stdPpu).toFloat(), px(Tok.overlayPlusY, page.stdPpu).toFloat())

    private val upLeftAt = Offset(px(Tok.edge + Tok.adjustColumn, page.stdPpu).toFloat(), upRightAt.y)

    private val upCentreAt = Offset(page.widthPx / 2f, upRightAt.y)

    private val downCentreAt = Offset(upCentreAt.x, px(page.board.overlayMinusY, page.stdPpu).toFloat())

    private val numberAt = Offset(px(Tok.canvasW - Tok.edge - Tok.adjustColumn, page.stdPpu).toFloat(), px(page.board.overlayRingY, page.stdPpu) - 2f)

    private fun press(at: Offset, n: Int = OPEN_FRAMES) {
        rule.onRoot().performTouchInput {
            down(at)
            up()
        }
        frames(n)
    }

    private fun upRight() = press(upRightAt)

    private fun drawn() {
        val v = rule.activity.findViewById<View>(android.R.id.content)
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(Canvas(b)) }
    }

    private fun downRight() = press(Offset(upRightAt.x, px(page.board.overlayMinusY, page.stdPpu).toFloat()))

    private fun held(): Rows = hudRows().also { it.replies = Channel(Channel.UNLIMITED) }

    private fun reply(rows: Rows, ok: Boolean) {
        rule.runOnIdle { rows.replies?.trySend(ok) }
        frames(RESIZE_FRAMES)
    }

    private fun land(rows: Rows, vararg levels: Int) {
        rule.runOnIdle { for (l in levels) rows.pivots.value = pivot(balance(l)) }
        frames(RESIZE_FRAMES)
    }

    private fun exit() = press(Offset(px(Tok.adjustExit, page.stdPpu) / 2f, px(Tok.adjustExit, page.stdPpu) / 2f), HIDE_FRAMES)

    private fun hudApp(rows: Rows): Pair<TestApp, SettingsModel> {
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        return app to m
    }

    private fun choice(key: String, words: List<String> = WORDS) = SettingRow(key, key, words[0], SettingKind.Choice, values = words, words = words, at = 0)

    private fun pivot(vararg rows: SettingRow) = listOf(SettingPivot(OUT, rows.toList()))

    private fun named(vararg names: String) = names.map { SettingPivot(it, listOf(choice(it))) }

    private fun library() = listOf(
        SettingRow(FOLDERS, "library folders", "1 on"),
        SettingRow(RESCAN, "rescan library", "1293 songs", SettingKind.Action, shows = listOf("scanning")),
        choice(PLAY, PLAY_WORDS).copy(title = "play through", sub = "what plays when the queue ends"),
        choice(RESUME, RESUME_WORDS).copy(title = "resume", sub = "restores the queue at power on"),
    )

    private fun subRows(subs: List<Int>) = (0 until SUB_PIVOT_ROWS).map { i -> SettingRow("k$i", "row $i", "", sub = if (i in subs) "the sub of row $i" else null) }

    private fun window(h: Int) = scale(Window(W, h, SDK, none, none, none, none, none), Nav.None, Surface.Page, false)

    private fun slackRows(words: List<String> = WORDS) = Rows(pivot(*(0 until SLACK_ROWS).map { choice("r$it", words) }.toTypedArray()))

    private fun dragBy(h: Int): Pair<Int, Int> {
        val s = window(h)
        val app = app(SettingsPage(ID, slackRows(), HeaderLook.Settings), root = true, s = s)
        val (_, m) = open(app)
        val pitch = px(Tok.settingsPitch, s.ppu)
        rule.onRoot().performTouchInput { down(Offset(s.widthPx / 2f, (px(s.board.page_contentTop, s.ppu) + SLACK_FROM).toFloat())) }
        frames(1)
        repeat(PAN_STEPS) {
            rule.onRoot().performTouchInput { moveBy(Offset(0f, -pitch.toFloat() / PAN_STEPS)) }
            frames(1)
        }
        rule.onRoot().performTouchInput { up() }
        frames(SETTLE_FRAMES)
        return m.content(0) - m.view to m.pane.px
    }

    private fun onRow(m: SettingsModel, y: Int) = Offset(page.widthPx / 2f, (px(page.board.page_contentTop, page.ppu) + y - m.pane.px).toFloat())

    private fun app(vararg pages: SettingsPage, root: Boolean = false, s: Scale = page) = TestApp(s, s, null, StillLevel) { rule.mainClock.currentTime }.also {
        it.settingsList = pages.toList()
        it.compose(rule, root)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun open(app: TestApp, id: SettingsId = ID, pivot: String? = null): Pair<TestScreen, SettingsModel> {
        val sc = TestScreen.Settings(app.stack.entry(), id, pivot)
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
    fun aPageListedTwiceFailsTheListing() {
        val failed = runCatching { app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings), SettingsPage(ID, Rows(pivot(choice("b"))), HeaderLook.Page)) }.exceptionOrNull()
        assertTrue("the listing fails, $failed", failed is IllegalArgumentException)
        assertEquals("a settings page is listed once", failed?.message)
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
    fun aScreenOpensAtItsPivot() {
        val app = app(SettingsPage(ID, Rows(named(OUT, SYS, LIB)), HeaderLook.Settings), root = true)
        val (_, m) = open(app, pivot = SYS)
        assertEquals("the page opens at its pivot", 1, m.pivot)
        assertEquals("the chrome shows the pivot", 1, app.host.types.settings.chromes.getValue(ID).active)
    }

    @Test
    fun aPivotTheRowsDoNotNameOpensTheFirstUntilTheyNameIt() {
        val rows = Rows(named(OUT, SYS))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app, pivot = LIB)
        assertEquals("the page opens at the first pivot", 0, m.pivot)
        rule.runOnIdle { rows.pivots.value = named(OUT, SYS, LIB) }
        frames(RESIZE_FRAMES)
        assertEquals("the rows that name the pivot move the page there", 2, m.pivot)
    }

    @Test
    fun aSwitchBeforeTheRowsNameTheOpenPivotKeepsTheSwitchedPivot() {
        val rows = Rows(named(OUT, SYS))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app, pivot = LIB)
        rule.runOnIdle { m.switchTo(1, rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the switch lands", 1, m.pivot)
        rule.runOnIdle { rows.pivots.value = named(OUT, SYS, LIB) }
        frames(RESIZE_FRAMES)
        assertEquals("the page stays at the switched pivot", 1, m.pivot)
    }

    @Test
    fun aRestoredModelKeepsItsSavedPivotThroughAnEnter() {
        val app = app(SettingsPage(ID, Rows(named(OUT, SYS, LIB)), HeaderLook.Settings))
        val sc = TestScreen.Settings(app.stack.entry(), ID, LIB)
        rule.runOnIdle {
            app.models.restore(sc.entry, ID, 0, null, listOf(SYS))
            app.stack.reset(listOf(sc))
        }
        frames(OPEN_FRAMES)
        assertEquals("premise: the settings page is up", sc, app.stack.top)
        assertEquals("the page keeps its saved pivot", 1, app.models[sc.entry]?.pivot)
    }

    @Test
    fun aSecondEnterOfTheSameScreenKeepsTheSwitchedPivot() {
        val app = app(SettingsPage(ID, Rows(named(OUT, SYS, LIB)), HeaderLook.Settings))
        val (sc, m) = open(app, pivot = SYS)
        rule.runOnIdle { m.switchTo(0, rule.mainClock.currentTime) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the switch lands", 0, m.pivot)
        rule.runOnIdle { app.host.enter(sc) }
        frames(OPEN_FRAMES)
        assertSame("premise: the model is kept", m, app.models[sc.entry])
        assertEquals("the page keeps the switched pivot", 0, m.pivot)
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
    fun aPageWhoseOnePivotHasNoNameDrawsAChromeWithNoPivots() {
        val id = SettingsId(PAGE)
        val app = app(SettingsPage(id, Rows(listOf(SettingPivot("", listOf(choice("a"), choice("b"))))), HeaderLook.Page))
        val (_, m) = open(app, id)
        val c = app.host.types.settings.chromes.getValue(id)
        assertEquals("the chrome has no pivots", 0, c.count)
        assertEquals("the header is the page's word", PAGE, c.header.text)
        assertEquals("the page shows its rows", listOf("a", "b"), m.rows(m.pivot).map { it.key })
        assertEquals("the focus is on the first row", 0, m.focusRow(m.pivot))
    }

    @Test
    fun aSettingsPivotSwipePutsBackTheRowFocus() {
        val app = app(SettingsPage(ID, Rows(listOf(SettingPivot(OUT, listOf(choice("a"), choice("b"), choice("c"))), SettingPivot(SYS, listOf(choice("d"))))), HeaderLook.Settings), root = true)
        val (_, m) = open(app)
        val p = m.pivot
        assertEquals("premise: the top row has the focus", 0, m.focusRow(p))
        val x = page.widthPx / 2f
        val y = px(page.board.page_contentTop, page.ppu) + m.tops(p)[1] - m.pane.px + m.heights(p)[1] / 2f
        val step = Tok.touchSlop * page.touchPpu * 3 / 2 / PAN_STEPS
        rule.onRoot().performTouchInput { down(Offset(x, y)) }
        frames(1)
        assertEquals("premise: the down moves the focus to row 1", 1, m.pane.focus.row)
        repeat(PAN_STEPS) {
            rule.onRoot().performTouchInput { moveBy(Offset(-step, 0f)) }
            frames(1)
        }
        assertTrue("premise: the pan took the gesture", m.motion.dragging)
        assertEquals("the take puts back row 0", 0, m.pane.focus.row)
        assertEquals("and its key", 0, m.focusRow(p))
        assertFalse("with no fade left", m.pane.focus.animating(rule.mainClock.currentTime))
        rule.onRoot().performTouchInput {
            moveTo(Offset(x - step, y))
            up()
        }
        frames(OPEN_FRAMES)
        assertEquals("premise: no switch", p, m.pivot)
        assertEquals("the return lands on row 0", 0, m.focusRow(p))
    }

    @Test
    fun aSidewaysDragOnAPageWithNoPivotsLeavesItsRowsInPlace() {
        val id = SettingsId(PAGE)
        val app = app(SettingsPage(id, Rows(listOf(SettingPivot("", listOf(choice("a"), choice("b"))))), HeaderLook.Page), root = true)
        val (_, m) = open(app, id)
        val step = 2 * Tok.touchSlop * page.touchPpu / PAN_STEPS
        rule.onRoot().performTouchInput { down(Offset(page.widthPx / 2f, PAN_Y)) }
        frames(1)
        repeat(PAN_STEPS) {
            rule.onRoot().performTouchInput { moveBy(Offset(-step, 0f)) }
            frames(1)
            assertEquals("the rows stay at x 0 during the drag", 0, m.dragX(rule.mainClock.currentTime, page.ppu))
        }
        rule.onRoot().performTouchInput { up() }
        repeat(OPEN_FRAMES) {
            frames(1)
            assertEquals("the rows stay at x 0 after the drag", 0, m.dragX(rule.mainClock.currentTime, page.ppu))
        }
    }

    @Test
    fun rememberSettingsChromeOfAnUnnamedPivotHasNoPivots() {
        val m = SettingsModel(ENTRY, ID).also { it.pivots = listOf(SettingPivot("", listOf(choice("a")))) }
        var chrome: Chrome? = null
        rule.setContent { FuneTheme(page) { chrome = rememberSettingsChrome(page, m, HeaderLook.Page) } }
        rule.waitForIdle()
        assertEquals("the chrome has no pivots", 0, chrome?.count)
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
    fun theHostsUpLiftsAHeldValueSheetSoItsThumbFades() {
        val rows = Rows(pivot(choice("a", TALL)))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals("premise: the value sheet is up", OverKind.Sheet, app.stack.overlay?.kind)
        val v = app.menu.version
        rule.runOnIdle { app.host.up(rule.mainClock.currentTime, null) }
        assertEquals("an up with nothing held bumps nothing", v, app.menu.version)
        val scrolled = rule.runOnIdle {
            app.menu.held = true
            app.menu.scrollTo(1, rule.mainClock.currentTime)
        }
        assertTrue("premise: the held sheet scrolls", scrolled)
        frames(2)
        rule.runOnIdle { app.host.up(rule.mainClock.currentTime, null) }
        frames(((Tok.scrollbar_hold + Tok.scrollbar_fade) / FRAME_MS).toInt() + FADE_SLACK)
        val t = rule.mainClock.currentTime
        assertEquals("the thumb faded out", 0f, app.menu.fade.at(t), 0f)
        assertFalse("its fade ended in the frame state", app.menu.fade.fading(t))
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
    fun aReadOnlyChoiceOpensNoValueSheetAndSendsNothing() {
        val rows = Rows(pivot(choice("a").copy(flags = setOf(SettingFlag.ReadOnly))))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals("premise: the route asks the app first", listOf("a"), rows.opens)
        assertNull(app.stack.overlay)
        assertTrue(rows.sets.isEmpty())
    }

    @Test
    fun aPickAfterItsRowTurnedReadOnlySendsNothing() {
        val rows = Rows(pivot(choice("a")))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        rule.runOnIdle { rows.pivots.value = pivot(choice("a").copy(flags = setOf(SettingFlag.ReadOnly))) }
        frames(RESIZE_FRAMES)
        assertTrue("premise: the row is read only", SettingFlag.ReadOnly in m.rows(0)[0].flags)
        assertTrue("the pick closes the menu", rule.runOnIdle { app.menuHost.act(1) })
        assertTrue("and sends nothing", rows.sets.isEmpty())
    }

    @Test
    fun aReadOnlyActionCallsItsAction() {
        val rows = Rows(pivot(SettingRow("a", "a", "", SettingKind.Action, flags = setOf(SettingFlag.ReadOnly))))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals(listOf("a"), rows.actions)
        assertNull(app.stack.overlay)
    }

    @Test
    fun aReadOnlyInfoRowTheAppOpensReachesOpen() {
        val rows = Rows(pivot(SettingRow("a", "a", NEW, flags = setOf(SettingFlag.ReadOnly)))).also { it.opened = setOf("a") }
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals(listOf("a"), rows.opens)
        assertNull(app.stack.overlay)
    }

    @Test
    fun aDraftPickSendsADraftAndNoSet() {
        val rows = Rows(pivot(SettingRow("a", "a", WORDS[0], SettingKind.Draft, values = WORDS, words = WORDS, at = 0)))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals("premise: a draft with values opens its value sheet", OverKind.Sheet, app.stack.overlay?.kind)
        assertTrue(rule.runOnIdle { app.menuHost.act(1) })
        assertEquals(listOf("a" to WORDS[1]), rows.drafts)
        assertTrue(rows.sets.isEmpty())
    }

    @Test
    fun aRangeRowWithWordsOpensNoValueSheet() {
        val rows = Rows(pivot(SettingRow("a", "a", WORDS[0], SettingKind.Range, values = WORDS, words = WORDS, at = 0)))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertTrue("premise: a range row is editable", m.rows(0)[0].editable)
        assertNull(app.stack.overlay)
        assertTrue(rows.sets.isEmpty())
    }

    @Test
    fun aRangeRowWithAnAdjustOpensItsHudAndMarksItsEdit() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals(OverKind.Adjust, app.stack.overlay?.kind)
        assertEquals(BAL, m.editing)
        assertTrue(rows.sets.isEmpty())
    }

    @Test
    fun theBalanceHudTakesItsTapsWhileItsOverlayIsCurrent() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        assertEquals(listOf(mapOf(BAL to "1")), rows.sets)
    }

    @Test
    fun aStepAfterTheRowsLandGoesFromTheLandedRow() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        rule.runOnIdle { rows.pivots.value = pivot(balance(LANDED)) }
        frames(RESIZE_FRAMES)
        assertEquals("premise: the hud stays", OverKind.Adjust, app.stack.overlay?.kind)
        upRight()
        assertEquals(listOf(mapOf(BAL to (LANDED + 1).toString())), rows.sets)
    }

    @Test
    fun aBalanceRowTurnedReadOnlyWhileItsHudIsUpDropsItAndSendsNothingMore() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        rule.runOnIdle { rows.pivots.value = pivot(balance(1).copy(flags = setOf(SettingFlag.ReadOnly))) }
        frames(CLOSE_FRAMES)
        assertNull("the hud drops at once", app.stack.overlay)
        assertNull(m.editing)
        assertEquals(listOf(mapOf(BAL to "1")), rows.sets)
    }

    @Test
    fun aRangeEditWhoseRowLeavesTheShownPivotEndsKeepingItsLevel() {
        val rows = hudRows().also { it.pivots.value = listOf(SettingPivot(OUT, listOf(balance(), choice("a"))), SettingPivot(SYS, listOf(choice("b")))) }
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        rule.runOnIdle { rows.pivots.value = listOf(SettingPivot(OUT, listOf(choice("a"))), SettingPivot(SYS, listOf(choice("b"), balance(1)))) }
        frames(CLOSE_FRAMES)
        assertEquals("premise: the page stays on output", 0, m.pivot)
        assertNull("the hud drops at once", app.stack.overlay)
        assertNull(m.editing)
        assertEquals(listOf(mapOf(BAL to "1")), rows.sets)
    }

    @Test
    fun aStepStopsAtTheRowsMax() {
        val rows = hudRows(balance(LIMIT - 1))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        upRight()
        assertEquals(listOf(mapOf(BAL to LIMIT.toString())), rows.sets)
    }

    @Test
    fun aOneColumnRangeRowStepsUpOnItsPlusAndDownOnItsMinus() {
        val rows = Rows(pivot(balance().copy(key = LEVEL, title = LEVEL))).also { r ->
            r.adjusts = { _, l -> SettingAdjust(listOf(AdjustCol(l.toString(), LEVEL)), LEVEL, l.toString()) }
        }
        hudApp(rows)
        press(upCentreAt)
        assertEquals("the plus steps up", listOf(mapOf(LEVEL to "1")), rows.sets)
        press(downCentreAt)
        assertEquals("the minus steps down", listOf(mapOf(LEVEL to "1"), mapOf(LEVEL to "0")), rows.sets)
    }

    @Test
    fun aBalanceLeftPlusStepsTheBalanceLeft() {
        val rows = hudRows()
        hudApp(rows)
        press(upLeftAt)
        assertEquals("the left plus moves the balance left", listOf(mapOf(BAL to "-1")), rows.sets)
    }

    @Test
    fun aRangeRowThatChangesItsColumnCountMidEditFails() {
        val two = listOf(AdjustCol("0", LEFT), AdjustCol("0", RIGHT))
        val rows = Rows(pivot(balance())).also { r ->
            r.adjusts = { _, l -> SettingAdjust(if (l == 0) two else two.take(1), BAL, l.toString()) }
        }
        val src = RangeAdjust(balance(), rows, SettingsModel(ENTRY, ID), AdjustHud(), 0, {}, CoroutineScope(EmptyCoroutineContext))
        assertTrue("premise: the edit opens with two columns", src.open(0L))
        val failed = runCatching { src.tap(AdjustHit.Up1) }.exceptionOrNull()
        assertTrue("the step fails, $failed", failed is IllegalArgumentException)
        val message = failed?.message.orEmpty()
        assertTrue("the message names the rule, $message", message.startsWith("a range row keeps its column count for its edit"))
        assertTrue("the message names the row, $message", message.contains(BAL))
    }

    @Test
    fun backSendsTheLevelAtOpenAndClosesTheHud() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertEquals(listOf(mapOf(BAL to "1"), mapOf(BAL to "0")), rows.sets)
        assertNull(app.stack.overlay)
        assertNull(m.editing)
    }

    @Test
    fun anEdgeBackUnderARangeAdjustDoesNothing() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        rule.runOnIdle { app.stack.edgeBack(app.host.popKind) }
        frames(OPEN_FRAMES)
        assertEquals("the hud stays", OverKind.Adjust, app.stack.overlay?.kind)
        assertEquals("no revert", listOf(mapOf(BAL to "1")), rows.sets)
        assertEquals("the edit stays", BAL, m.editing)
    }

    @Test
    fun aBackTheStackDrivesSendsTheLevelAtOpenAsTheHudCloses() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(CLOSE_FRAMES)
        assertEquals("premise: the hud hides with its overlay up", OverKind.Adjust, app.stack.overlay?.kind)
        assertEquals(listOf(mapOf(BAL to "1"), mapOf(BAL to "0")), rows.sets)
        frames(OPEN_FRAMES)
        assertNull(app.stack.overlay)
        assertNull(m.editing)
    }

    @Test
    fun aTurnOffItsSettingsScreenDropsTheHudAndSendsTheLevelAtOpen() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        rule.runOnIdle { app.stack.pop(MoveKind.T4n) }
        frames(OPEN_FRAMES)
        assertEquals(listOf(mapOf(BAL to "1"), mapOf(BAL to "0")), rows.sets)
        assertNull(app.stack.overlay)
    }

    @Test
    fun aHoldOnAColumnNumberTakesTheEntryAndDropsTheHudWithTheSteppedLevel() {
        val rows = hudRows().also { it.takes = true }
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        upRight()
        drawn()
        rule.onRoot().performTouchInput { down(numberAt) }
        frames(Tok.input_keyHoldMs.toInt() / FRAME_MS + CUT_FRAMES + 1)
        assertNull(app.stack.overlay)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals(listOf(BAL to 1), rows.entries)
        assertEquals("the stepped level stays", listOf(mapOf(BAL to "1")), rows.sets)
    }

    @Test
    fun aPressThatOutlivesALandingTakesItsStepFromTheLandedLevel() {
        val rows = hudRows()
        hudApp(rows)
        rule.onRoot().performTouchInput { down(upRightAt) }
        rule.runOnIdle { rows.pivots.value = pivot(balance(LANDED)) }
        frames(RESIZE_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals(listOf(mapOf(BAL to (LANDED + 1).toString())), rows.sets)
    }

    @Test
    fun anEntryThatOpensASheetShowsItOverTheDroppedHud() {
        val rows = hudRows().also { it.takes = true }
        val (app, _) = hudApp(rows)
        rows.onEntry = { app.host.openSheet(SettingSheet(MenuForm.Dlg, OTHER, listOf(OTHER))) { true } }
        drawn()
        rule.onRoot().performTouchInput { down(numberAt) }
        frames(Tok.input_keyHoldMs.toInt() / FRAME_MS + OPEN_FRAMES)
        rule.onRoot().performTouchInput { up() }
        frames(OPEN_FRAMES)
        assertEquals(listOf(BAL to 1), rows.entries)
        assertEquals("the entry's sheet is up", OverKind.Dlg, app.stack.overlay?.kind)
    }

    @Test
    fun anExitKeepsTheSteppedLevelEndsTheEditAndHides() {
        val rows = hudRows()
        val (app, m) = hudApp(rows)
        upRight()
        exit()
        assertEquals("the hud hides with its overlay up", OverKind.Adjust, app.stack.overlay?.kind)
        assertNull(m.editing)
        frames(OPEN_FRAMES)
        assertNull(app.stack.overlay)
        assertEquals(listOf(mapOf(BAL to "1")), rows.sets)
    }

    @Test
    fun aStepWhileTheHudHidesAfterBackSendsNothing() {
        val rows = hudRows()
        val (app, _) = hudApp(rows)
        upRight()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(HIDE_FRAMES)
        assertEquals("premise: the hud hides", OverKind.Adjust, app.stack.overlay?.kind)
        press(upRightAt, HIDE_FRAMES)
        assertEquals(listOf(mapOf(BAL to "1"), mapOf(BAL to "0")), rows.sets)
    }

    @Test
    fun aBackWhileTheHudHidesAfterExitRevertsNothing() {
        val rows = hudRows()
        val (app, _) = hudApp(rows)
        upRight()
        exit()
        assertEquals("premise: the hud hides", OverKind.Adjust, app.stack.overlay?.kind)
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(OPEN_FRAMES)
        assertEquals(listOf(mapOf(BAL to "1")), rows.sets)
    }

    @Test
    fun aHoldThatFiresWhileTheHudHidesTakesNoEntry() {
        val rows = hudRows().also { it.takes = true }
        val (app, _) = hudApp(rows)
        upRight()
        drawn()
        rule.onRoot().performTouchInput { down(numberAt) }
        frames(Tok.input_keyHoldMs.toInt() / FRAME_MS - HOLD_LEAD)
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        frames(HIDE_FRAMES)
        assertEquals("premise: the hud hides while the hold runs", OverKind.Adjust, app.stack.overlay?.kind)
        frames(OPEN_FRAMES)
        rule.onRoot().performTouchInput { up() }
        assertTrue(rows.entries.isEmpty())
    }

    @Test
    fun upUpAndDownWithInOrderRepliesKeepTheStepBase() {
        val rows = held()
        hudApp(rows)
        upRight()
        upRight()
        downRight()
        land(rows, 1)
        reply(rows, true)
        land(rows, 2)
        upRight()
        assertEquals("the landed 2 is the HUD's own step, the base stays 1", listOf("1", "2", "1", "2").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun aRefusedStepAdoptsTheRowsLevel() {
        val rows = held()
        hudApp(rows)
        upRight()
        reply(rows, false)
        upRight()
        assertEquals("the next step goes from the row's 0", listOf("1", "1").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun aMergeBackToTheStartLevelInOneFrameMovesTheBase() {
        val rows = held()
        hudApp(rows)
        upRight()
        land(rows, 1, 0)
        reply(rows, true)
        upRight()
        assertEquals("the merged 0 is no step of the HUD's", listOf("1", "1").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun aDeviceResetAfterEveryStepResolvedMovesTheBase() {
        val rows = held()
        hudApp(rows)
        upRight()
        land(rows, 1)
        reply(rows, true)
        land(rows, 0)
        upRight()
        assertEquals(listOf("1", "1").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun aReplyBeforeItsEchoKeepsTheStepBase() {
        val rows = held()
        hudApp(rows)
        upRight()
        upRight()
        reply(rows, true)
        land(rows, 1)
        upRight()
        assertEquals("the echo of 1 lands with 2 still in flight, the base stays 2", listOf("1", "2", "3").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun theSecondPushLandingBeforeItsReplyKeepsTheStepBase() {
        val rows = held()
        hudApp(rows)
        upRight()
        upRight()
        reply(rows, true)
        land(rows, 1)
        land(rows, 2)
        reply(rows, true)
        upRight()
        assertEquals(listOf("1", "2", "3").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun theFirstOfTwoStepsRefusedAdoptsNothingWhileTheSecondIsInFlight() {
        val rows = held()
        hudApp(rows)
        upRight()
        upRight()
        reply(rows, false)
        upRight()
        assertEquals("the base stays 2 until the last reply", listOf("1", "2", "3").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun anUnrelatedCompositionBetweenAStepAndItsEchoKeepsTheHud() {
        val rows = held()
        val (app, _) = hudApp(rows)
        upRight()
        rule.runOnIdle { app.lyricsOn = true }
        frames(RESIZE_FRAMES)
        upRight()
        assertEquals("the unchanged row moves nothing", listOf("1", "2").map { mapOf(BAL to it) }, rows.sets)
    }

    @Test
    fun aStepInTheFrameItsRowTurnsReadOnlySendsNothing() {
        val rows = hudRows()
        val (app, m) = hudApp(rows)
        rule.runOnIdle { rows.pivots.value = pivot(balance().copy(flags = setOf(SettingFlag.ReadOnly))) }
        frames(1)
        assertTrue("premise: the read only row landed", SettingFlag.ReadOnly in m.rows(0)[0].flags)
        press(upRightAt, CLOSE_FRAMES)
        assertTrue(rows.sets.isEmpty())
        assertNull(app.stack.overlay)
    }

    @Test
    fun aRangeRowWithSwatchesOpensItsSwatchSheetAndAPickSetsAtOnce() {
        val swatches = WORDS.indices.map { Accent.forHue(it * HUE_STEP) }
        val rows = Rows(pivot(choice("a").copy(kind = SettingKind.Range, swatches = swatches)))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        tap(app, m, 0)
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(swatches, app.menu.swatches)
        assertTrue(rule.runOnIdle { app.menuHost.act(2) })
        assertEquals(listOf(mapOf("a" to WORDS[2])), rows.sets)
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
    fun aVolumeOpenBehindAHeldSheetKeepsTheListDrag() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings))
        val (_, m) = open(app)
        val list = dragging()
        m.lists = listOf(list)
        rule.runOnIdle {
            app.stack.open(Overlay(OverKind.Sheet))
            app.host.openVolume(rule.mainClock.currentTime)
        }
        assertEquals("a volume open behind a held open keeps the list drag", true, list.scroll.dragging)
        frames(OPEN_FRAMES)
        assertEquals("the held sheet takes the slot", OverKind.Sheet, app.stack.overlay?.kind)
    }

    @Test
    fun aRangeTapWhileAnOpenIsHeldMarksNoEditAndSendsNothing() {
        val rows = hudRows()
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings))
        val (_, m) = open(app)
        val editing = rule.runOnIdle {
            app.stack.open(Overlay(OverKind.Sheet))
            app.host.settingsTap(m, m.rows(m.pivot)[0])
            m.editing
        }
        assertEquals("a range tap behind a held open marks no edit", null, editing)
        frames(OPEN_FRAMES)
        assertTrue("the tap sends nothing", rows.sets.isEmpty())
        assertEquals(OverKind.Sheet, app.stack.overlay?.kind)
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

    @Test
    fun aValueSheetLiftLandsWithTheCloseAndStaysWhenTheSheetLeaves() {
        val rows = Rows(pivot(*(0 until LIFT_ROWS).map { choice("r$it", FOUR) }.toTypedArray()))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings), root = true)
        val (_, m) = open(app)
        rule.runOnIdle {
            app.host.types.settings.drawOf(m)
            m.down(LIFT_ROW, rule.mainClock.currentTime)
            m.cancel(rule.mainClock.currentTime)
        }
        val before = m.pane.px
        tap(app, m, LIFT_ROW)
        assertTrue("premise: the lift moved the pane, ${m.pane.px} against $before", m.pane.px > before)
        assertEquals("premise: the lift has landed", AimKind.None, m.aimKind)
        rule.runOnIdle { app.stack.close() }
        frames((Tok.motion_scroll * 3 / 2 / FRAME_MS).toInt() + 1)
        assertTrue("premise: the sheet is still up", app.stack.overlay != null)
        assertEquals("the row is back where it was one and a half scroll times after the close", before, m.pane.px)
        assertEquals(AimKind.None, m.aimKind)
        frames(OPEN_FRAMES)
        assertNull("premise: the sheet is gone", app.stack.overlay)
        assertEquals("and stays there once the sheet leaves", before, m.pane.px)
        assertEquals(AimKind.None, m.aimKind)
    }

    @Test
    fun aHeldDownOnTheSettingsHeaderDuringItsExitHoldsTheBack() {
        val app = app(SettingsPage(ID, Rows(pivot(choice("a"))), HeaderLook.Settings), root = true)
        open(app)
        rule.runOnIdle { app.stack.back(app.host.popKind) }
        frames(EXIT_FRAMES)
        assertEquals("premise: the settings page exits", Phase.Exit, app.stack.phase)
        assertEquals("premise: the exit pops settings", true, app.stack.turn?.popping)
        val b = app.host.types.settings.chromes.getValue(ID).headerBox()
        rule.onRoot().performTouchInput { down(Offset((b.x0 + b.x1) / 2f, (b.y0 + b.y1) / 2f)) }
        assertTrue("the down on the header holds the back", app.stack.held?.target == true)
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aTapUnderASubLineStillHitsItsRow() {
        val rows = Rows(listOf(SettingPivot(LIB, library())))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings), root = true)
        val (_, m) = open(app)
        val d = rule.runOnIdle { app.host.types.settings.drawOf(m) }
        assertEquals("premise: PLAY THROUGH's sub sits at 348", PLAY_SUB_TOP, m.tops(0)[PLAY_ROW] + d.ink(m.rows(0)[PLAY_ROW]).size.subY)
        press(onRow(m, PAD_TAP))
        assertEquals("a tap in PLAY THROUGH's bottom pad hits its row (funeOS player/ui/settings.cpp SettingsPage::row_at :803-810, v0.4.4)", listOf(PLAY), rows.opens)
        assertEquals("and opens its value sheet", OverKind.Sheet, app.stack.overlay?.kind)
        assertEquals(PLAY_WORDS, app.menu.items)
        rule.runOnIdle { app.stack.close() }
        frames(OPEN_FRAMES)
        assertNull("premise: the sheet is gone", app.stack.overlay)
        press(onRow(m, RESUME_TOP))
        assertEquals("a tap on RESUME's top hits RESUME", listOf(PLAY, RESUME), rows.opens)
        assertEquals(RESUME_WORDS, app.menu.items)
    }

    @Test
    fun aTapOnARowWhoseSubIsBelowTheViewScrollsItsSubIntoView() {
        val rows = Rows(listOf(SettingPivot(LIB, subRows(listOf(CUT_ROW)))))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings), root = true)
        val (_, m) = open(app)
        val d = rule.runOnIdle { app.host.types.settings.drawOf(m) }
        val top = m.tops(0)[CUT_ROW]
        val h = m.heights(0)[CUT_ROW]
        assertTrue("premise: the view's bottom cuts the row and its sub is below the view", top < m.view && top + d.ink(m.rows(0)[CUT_ROW]).size.subY >= m.view)
        press(onRow(m, top + TAP_IN), 1)
        assertEquals("premise: the info row reaches the app unopened", listOf("k$CUT_ROW"), rows.opens)
        assertNull("no value sheet", app.stack.overlay)
        assertEquals("an into aim at the up (funeOS player/ui/settings.cpp SettingsPage::ensure_visible :773-786, v0.4.4)", AimKind.Into, m.aimKind)
        assertEquals("to the row's top plus its full height less the view", top + h - m.view, m.aim)
        frames(OPEN_FRAMES)
        assertEquals(top + h - m.view, m.pane.px)
    }

    @Test
    fun aLandingThatDropsTheSubsClampsTheScrolledOffset() {
        val rows = Rows(listOf(SettingPivot(LIB, subRows(SUB_ROWS))))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings), root = true)
        val (_, m) = open(app)
        rule.runOnIdle { m.aimTo(m.limit(0), AimKind.Into) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the pivot rests at its end", m.content(0) - m.view, m.pane.px)
        rule.runOnIdle { rows.pivots.value = listOf(SettingPivot(LIB, subRows(emptyList()))) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the subs dropped", SUB_PIVOT_ROWS * px(Tok.settingsPitch, page.ppu), m.content(0))
        assertEquals("the list's offset rests at the new content less view", m.content(0) - m.view, m.pane.px)
    }

    @Test
    fun aDragToTheEndStopsAtTheContentEndWithTheSubRows() {
        val rows = Rows(listOf(SettingPivot(LIB, subRows(SUB_ROWS))))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings), root = true)
        val (_, m) = open(app)
        val pitch = px(Tok.settingsPitch, page.ppu)
        assertEquals("premise: the sub rows are in the content", SUB_PIVOT_ROWS * pitch + SUB_ROWS.size * (A105_SUB_ROW - pitch), m.content(0))
        val x = page.widthPx / 2f
        rule.onRoot().performTouchInput { down(Offset(x, DRAG_FROM)) }
        frames(1)
        repeat(DRAG_STEPS) {
            rule.onRoot().performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frames(1)
        }
        rule.onRoot().performTouchInput { up() }
        frames(SETTLE_FRAMES)
        assertEquals("the drag rests at the content end", m.content(0) - m.view, m.pane.px)
    }

    @Test
    fun aTapInATallRowsReservedBandHitsIt() {
        val ear = choice(EAR).copy(title = "correction eq", sub = "headphone correction before EQ", tall = true)
        val rows = Rows(listOf(SettingPivot(EFFECTS, listOf(ear, choice(CROSSFEED)))))
        val app = app(SettingsPage(ID, rows, HeaderLook.Settings), root = true)
        val (_, m) = open(app)
        assertTrue("premise: the tap is past the one line sub row's end", TALL_TAP >= A105_SUB_ROW)
        press(onRow(m, TALL_TAP))
        assertEquals("the reserved band is the row's (funeOS player/ui/settings.cpp SettingsPage::measure :573, row_at :803-810, v0.4.4)", listOf(EAR), rows.opens)
    }

    @Test
    fun aDragOnAPivotOnePixelOverItsViewLeavesItsRowsInPlace() {
        val (over, rest) = dragBy(ONE_OVER_H)
        assertEquals("premise: the pivot is 1 px over its view", 1, over)
        assertEquals("a drag by a row leaves the first row's top in place (funeOS player/ui/settings.cpp SettingsPage::overflow :549-553, v0.4.4)", 0, rest)
    }

    @Test
    fun aDragOnAPivotTwoPixelsOverItsViewMovesItsRowsTwoPixels() {
        val (over, rest) = dragBy(ONE_OVER_H - 1)
        assertEquals("premise: 2 px over", 2, over)
        assertEquals("the same drag moves the rows 2 px", 2, rest)
    }

    @Test
    fun aLiftedSheetOnASlackPivotRisesByItsFullReach() {
        val app = app(SettingsPage(ID, slackRows(FOUR), HeaderLook.Settings), root = true, s = window(ONE_OVER_H))
        val (_, m) = open(app)
        rule.runOnIdle { app.host.types.settings.drawOf(m) }
        assertEquals("premise: the pivot is 1 px over its view", 1, m.content(0) - m.view)
        rule.runOnIdle {
            m.down(SLACK_ROWS - 1, rule.mainClock.currentTime)
            m.cancel(rule.mainClock.currentTime)
        }
        tap(app, m, SLACK_ROWS - 1)
        assertTrue("premise: the sheet lifts its row", m.pane.reach > 0)
        assertEquals("premise: the lift has landed", AimKind.None, m.aimKind)
        assertEquals("the list rises by the full reach", m.pane.reach, m.pane.px)
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
        private const val EXIT_FRAMES = 3
        private const val DRAG_Y = 400f
        private const val TALLER = 600
        private const val SWAP = 300L
        private const val GONE = 3000L
        private const val LIFT_ROWS = 4
        private const val LIFT_ROW = 2
        private const val BAL = "balance"
        private const val LEFT = "left db"
        private const val RIGHT = "right db"
        private const val LEVEL = "level"
        private const val PAN_Y = 200f
        private const val PAN_STEPS = 8
        private const val LIMIT = 20
        private const val LANDED = 5
        private const val HUE_STEP = 10
        private const val CUT_FRAMES = 3
        private const val FRAME_MS = 16
        private const val HIDE_FRAMES = 2
        private const val HOLD_LEAD = 3
        private const val FADE_SLACK = 4
        private const val FOLDERS = "libraryFolders"
        private const val RESCAN = "rescan"
        private const val PLAY = "playThrough"
        private const val RESUME = "resume"
        private const val PLAY_ROW = 2
        private const val PLAY_SUB_TOP = 348
        private const val PAD_TAP = 395
        private const val RESUME_TOP = 406
        private const val SUB_PIVOT_ROWS = 9
        private const val CUT_ROW = 7
        private const val TAP_IN = 8
        private const val A105_SUB_ROW = 148
        private const val DRAG_FROM = 1100f
        private const val DRAG_STEP = 60f
        private const val DRAG_STEPS = 10
        private const val SETTLE_FRAMES = 90
        private const val EFFECTS = "effects"
        private const val EAR = "earEq"
        private const val CROSSFEED = "crossfeed"
        private const val TALL_TAP = 190
        private const val SLACK_ROWS = 7
        private const val ONE_OVER_H = 1262
        private const val SLACK_FROM = 400
        private val ID = SettingsId(SETTINGS)
        private val WORDS = listOf("off", "low", "high")
        private val LATER = listOf("x", "y", "z")
        private val FOUR = listOf("off", "low", "mid", "high")
        private val TALL = List(24) { "word $it" }
        private val PLAY_WORDS = listOf("off", "on")
        private val RESUME_WORDS = listOf("position", "off")
        private val SUB_ROWS = listOf(2, 6)
    }
}
