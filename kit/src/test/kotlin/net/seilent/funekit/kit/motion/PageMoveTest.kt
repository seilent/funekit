package net.seilent.funekit.kit.motion

import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Moves
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PageMoveTest {
    private val forms = MoveForms()

    private fun easeOut(t: Double) = t * (2 - t)

    private fun cubicOut(t: Double) = 1 - (1 - t).pow(3)

    private fun quartOut(t: Double) = 1 - (1 - t).pow(4)

    private fun near(want: Double, got: Float, what: String) = assertEquals(what, want, got.toDouble(), 0.002)

    private val pop = MoveEnds(pivotX = 49.8f, pivotY = 28.2f, morph = true, morphKind = MorphKind.Pivot, morphS0 = 0f, morphS1 = 1f, grounds = true)

    @Test
    fun theBoardFormsAreTheTokenModes() {
        for (board in listOf(TokBoard.v1, TokBoard.r1, TokBoard.a105)) {
            val b = MoveForms.board(board)
            assertTrue(b.leaveFade)
            assertEquals(ContentForm.Zoom, b.content)
            assertTrue(b.chromeSlide)
            assertTrue(b.headerZoom)
            assertTrue(b.t3mFade)
            assertTrue(b.wordMorph)
            assertEquals("$board hub rows", ContentForm.Zoom, b.hubRows)
            assertEquals("$board page rows", ContentForm.Zoom, b.pageRows)
        }
        assertEquals(157.3f, MoveForms.board(TokBoard.v1).t2PivotY, 0f)
        assertEquals(163.8f, MoveForms.board(TokBoard.r1).t2PivotY, 0f)
        assertEquals(163.8f, MoveForms.board(TokBoard.a105).t2PivotY, 0f)
        assertEquals(163.8f, MoveForms().t2PivotY, 0f)
    }

    @Test
    fun aPivotPopRunsTheFuneOsPivotPopKeys() {
        val m = PageMove()
        m.start(MoveKind.T4, 1000, pop, forms)
        m.release(Part.Content, 1000)
        val t = 1200L
        assertEquals(1167L, m.swapAt)
        assertEquals(183, m.wordEnd)
        near(cubicOut(212.0 / 400), m.at(Part.Word, Prop.S, t), "word S")
        near(0.35, m.at(Part.Word, Prop.A, 1100), "word A at 100")
        near(0.0, m.at(Part.Word, Prop.A, t), "word A gone at 183")
        assertFalse(m.has(Part.LeaveWord, Prop.S))
        near(0.35, m.at(Part.Leave, Prop.A, 1100), "leave A at 100")
        near(0.0, m.at(Part.Leave, Prop.A, t), "leave A at 200")
        near(19.9 * easeOut(25.0 / 50), m.at(Part.LeavePivots, Prop.X, 1025), "leave pivots X at 25")
        near(19.9, m.at(Part.LeavePivots, Prop.X, t), "leave pivots X")
        near(26.5, m.at(Part.LeavePivots, Prop.Y, t), "leave pivots Y")
        near(0.55, m.at(Part.LeavePivots, Prop.A, 1050), "leave pivots A at 50")
        near(0.34, m.at(Part.Zoom, Prop.A, t), "zoom A at 200")
        near(1.34 - 0.34 * easeOut(17.0 / 117), m.at(Part.Zoom, Prop.S, t), "zoom S")
        near(0.0, m.at(Part.Header, Prop.A, 1299), "header before 300")
        near(1.0, m.at(Part.Header, Prop.A, 1300), "header at 300")
        near(0.79, m.at(Part.Pivots, Prop.A, 1333), "pivots A at 333")
        near(33.0 / 133, m.at(Part.Ground, Prop.A, t), "ground A")
        near(0.0, m.at(Part.Content, Prop.A, 1332), "content before 333")
        near(0.16, m.at(Part.Content, Prop.A, 1333), "content A at 333")
        near(0.67, m.at(Part.Content, Prop.A, 1400), "content A at 400")
        near(1.0, m.at(Part.Content, Prop.S, t), "content S")
    }

    @Test
    fun heldContentRestsAndKeepsTheLeavingPageOneFrameShort() {
        val m = PageMove()
        m.start(MoveKind.T3, 0, MoveEnds(), forms)
        assertTrue(m.held(Part.Content))
        near(0.0, m.at(Part.Content, Prop.A, 700), "held content A")
        near(1.0, m.at(Part.Content, Prop.S, 700), "held content S")
        near(1 - 116.0 / 133, m.at(Part.Leave, Prop.A, 300), "leave A while held")
        val d = m.release(Part.Content, 500)
        assertEquals(49, d)
        near(17.0 / 200, m.at(Part.Content, Prop.A, 500), "content A after release")
        near(0.0, m.at(Part.Leave, Prop.A, 600), "leave A after release")
    }

    private val s0 = 39.7f / 150

    private val t2Ends = MoveEnds(
        word = true, wordS0 = s0, wordS1 = 1f, wordX0 = 15f, wordX1 = 40f, wordY0 = 500f, wordY1 = 150f,
        peek0 = 1f, pivotX = 49.8f, rows = true, rowsDx = 61.5f,
    )

    private fun tight(want: Double, got: Float, what: String) = assertEquals(what, want, got.toDouble(), 0.0001)

    private fun released(k: MoveKind, e: MoveEnds) = started(k, e).also {
        it.release(Part.Content, 0)
        it.release(Part.Ground, 0)
    }

    private fun rowsInk(m: PageMove, p: Part) {
        tight(0.0, m.at(p, Prop.A, 0), "$p A at 0")
        tight(0.0, m.at(p, Prop.A, 16), "$p A at 16")
        tight(0.6, m.at(p, Prop.A, 17), "$p A at 17")
        tight(0.8, m.at(p, Prop.A, 83), "$p A at 83")
        tight(1.0, m.at(p, Prop.A, 149), "$p A at 149")
        tight(1.0, m.at(p, Prop.A, 300), "$p A at 300")
    }

    @Test
    fun t2RowsInkAtSeventeenMsThenFadeFromRowsT2Alpha() {
        assertEquals(17, Moves.kT2RowsInk)
        val m = released(MoveKind.T2, t2Ends)
        assertEquals(300L, m.swapAt)
        rowsInk(m, Part.Content)
        near(0.0, m.at(Part.Home, Prop.A, 100), "home A")
        near(-33.5, m.at(Part.Pivots, Prop.X, 100), "pivots before their first key")
    }

    @Test
    fun t2nTakesTheSameRowsInk() {
        val m = released(MoveKind.T2n, MoveEnds(peek0 = 1f, rows = true, rowsDx = 61.5f))
        assertFalse(m.has(Part.Word, Prop.S))
        rowsInk(m, Part.Content)
        rowsInk(m, Part.Ground)
    }

    @Test
    fun theGroundTakesEveryContentAlphaLegAndIsHeld() {
        val m = started(MoveKind.T2, t2Ends)
        assertTrue(m.held(Part.Ground))
        assertEquals(2, m.legs.count { it.part == Part.Ground && it.prop == Prop.A })
        m.release(Part.Content, 0)
        tight(0.0, m.at(Part.Ground, Prop.A, 100), "held ground A")
        m.release(Part.Ground, 0)
        assertFalse(m.held(Part.Ground))
        rowsInk(m, Part.Ground)
    }

    @Test
    fun theT2RowsScaleAndShiftRunOverRowsT2() {
        val m = released(MoveKind.T2, t2Ends)
        tight(0.79, m.at(Part.Content, Prop.S, 0), "S at 0")
        tight(0.80375, m.at(Part.Content, Prop.S, 17), "S at 17")
        tight(0.86625, m.at(Part.Content, Prop.S, 100), "S at 100")
        tight(0.93005, m.at(Part.Content, Prop.S, 200), "S at 200")
        tight(1.0, m.at(Part.Content, Prop.S, 385), "S at 385")
        tight(61.5, m.at(Part.Content, Prop.X, 0), "X at 0")
        for (t in listOf(17, 100, 200)) {
            near(61.5 * (1 - powOut15(t / 385.0)), m.at(Part.Content, Prop.X, t.toLong()), "X at $t")
        }
        tight(0.0, m.at(Part.Content, Prop.X, 385), "X at 385")
    }

    @Test
    fun theT2PivotsZoomInOnTheT2PivKeysAboutThePivotPoint() {
        val m = released(MoveKind.T2, t2Ends.copy(pxU = 2f))
        near(0.0, m.at(Part.Pivots, Prop.A, 232), "pivots A before 233")
        near(0.10, m.at(Part.Pivots, Prop.A, 233), "pivots A at 233")
        near(0.54, m.at(Part.Pivots, Prop.A, 300), "pivots A at 300")
        near(1.0, m.at(Part.Pivots, Prop.A, 483), "pivots A at 483")
        near(0.48, m.at(Part.Pivots, Prop.S, 233), "pivots S at 233")
        near(0.754, m.at(Part.Pivots, Prop.S, 300), "pivots S at 300")
        near(1.0, m.at(Part.Pivots, Prop.S, 450), "pivots S at 450")
        near(-67.0, m.at(Part.Pivots, Prop.X, 233), "pivots X at 233")
        near(-7.8, m.at(Part.Pivots, Prop.X, 300), "pivots X at 300")
        near(0.0, m.at(Part.Pivots, Prop.X, 367), "pivots X at 367")
        near(378.0, m.at(Part.Pivots, Prop.PX, 300), "pivots PX")
        near(327.6, m.at(Part.Pivots, Prop.PY, 300), "pivots PY")
        val step = PageMove().also { it.start(MoveKind.T2, 0, t2Ends, forms.copy(chromeSlide = false)) }
        assertFalse(step.has(Part.Pivots, Prop.S))
        near(0.0, step.at(Part.Pivots, Prop.A, 482), "stepped pivots before 483")
        near(1.0, step.at(Part.Pivots, Prop.A, 483), "stepped pivots at 483")
    }

    @Test
    fun theFaceGoesAt17OnT2AndComesBackFrom200OnT4h() {
        val m = released(MoveKind.T2, t2Ends)
        near(1.0, m.at(Part.Face, Prop.A, 0), "T2 face at 0")
        near(1 - 8.0 / 17, m.at(Part.Face, Prop.A, 8), "T2 face at 8")
        near(0.0, m.at(Part.Face, Prop.A, 17), "T2 face at 17")
        val h = started(MoveKind.T4h, back)
        near(0.0, h.at(Part.Face, Prop.A, 200), "T4h face at 200")
        near(41.0 / 83, h.at(Part.Face, Prop.A, 241), "T4h face at 241")
        near(1.0, h.at(Part.Face, Prop.A, 283), "T4h face at 283")
    }

    @Test
    fun homeOpenScaleRunsTheT2ScaleKeys() {
        val m = released(MoveKind.T2, t2Ends)
        tight(0.26467, m.at(Part.Word, Prop.S, 0), "S at 0")
        tight(0.476, m.at(Part.Word, Prop.S, 17), "S at 17")
        tight(0.49345, m.at(Part.Word, Prop.S, 25), "S at 25")
        tight(0.867, m.at(Part.Word, Prop.S, 200), "S at 200")
        tight(1.0, m.at(Part.Word, Prop.S, 400), "S at 400")
        tight(1.0, m.at(Part.Word, Prop.S, 435), "S at 435")
    }

    @Test
    fun homeOpenBaseRunsTheT2BaseKeysAsProgress() {
        val m = released(MoveKind.T2, t2Ends)
        tight(500.0, m.at(Part.Word, Prop.Y, 0), "Y at 0")
        assertEquals(500.0 + 0.360 * (150 - 500), m.at(Part.Word, Prop.Y, 100).toDouble(), 0.001)
        tight(150.0, m.at(Part.Word, Prop.Y, 434), "Y at 434")
    }

    @Test
    fun homeOpenXEasesOutOverT2Base() {
        val m = released(MoveKind.T2, t2Ends)
        for (t in listOf(0, 100, 300, 435)) {
            assertEquals("X at $t", 15 + 25 * easeOut(t / 435.0), m.at(Part.Word, Prop.X, t.toLong()).toDouble(), 0.001)
        }
    }

    @Test
    fun theWordGoesAndTheHeaderStepsInAt435() {
        val m = released(MoveKind.T2, t2Ends)
        assertEquals(435, m.wordEnd)
        near(1.0, m.at(Part.Word, Prop.A, 434), "word before 435")
        near(0.0, m.at(Part.Word, Prop.A, 435), "word off at 435")
        near(0.0, m.at(Part.Header, Prop.A, 434), "header before the word ends")
        near(1.0, m.at(Part.Header, Prop.A, 435), "header at the word end")
    }

    @Test
    fun aReversedT2RunsEachChainBackFromItsValue() {
        val m = released(MoveKind.T2, t2Ends)
        val a = 0.6 + 0.4 * 83 / 132
        tight(a, m.at(Part.Content, Prop.A, 100), "content A before the reverse")
        m.reverse(100)
        tight(a, m.at(Part.Content, Prop.A, 100), "content A at the reverse")
        tight(a * (1 - 41.0 / 83), m.at(Part.Content, Prop.A, 141), "content A on the way back")
        tight(0.0, m.at(Part.Content, Prop.A, 183), "content A back at 0")
        assertEquals(1, m.legs.count { it.part == Part.Content && it.prop == Prop.A })
        tight(0.686, m.at(Part.Word, Prop.S, 100), "word S at the reverse")
        tight((0.686 + s0) / 2, m.at(Part.Word, Prop.S, 150), "word S halfway")
        tight(s0.toDouble(), m.at(Part.Word, Prop.S, 200), "word S back at wordS0")
        assertEquals(1, m.legs.count { it.part == Part.Word && it.prop == Prop.S })
    }

    @Test
    fun spanReadsTheFirstFromAndTheLastTo() {
        val m = released(MoveKind.T2, t2Ends)
        assertEquals(s0 to 1f, m.span(Part.Word, Prop.S))
        assertEquals(500f to 150f, m.span(Part.Word, Prop.Y))
        assertEquals(0f to 1f, m.span(Part.Content, Prop.A))
        assertEquals(0f, m.leg(Part.Content, Prop.A)?.from)
        assertEquals(17, m.leg(Part.Content, Prop.A)?.delay)
        assertEquals(17, m.last(Part.Content, Prop.A)?.delay)
        assertEquals(132, m.last(Part.Content, Prop.A)?.dur)
        assertNull(m.span(Part.Tile, Prop.S))
    }

    @Test
    fun homeOpenOnThePageBaseStartsUnderTheFirstKey() {
        val none = Insets(0, 0, 0, 0)
        val typed = listOf(Triple(2160, 403.2f, 0.40225), Triple(2520, 397.3f, 0.40868), Triple(1280, 355.4f, 0.32398))
        for ((h, ppi, want) in typed) {
            val w = if (h == 1280) 768 else 1080
            val win = Window(w, h, 35, none, none, none, none, none, Dpi(ppi, ppi, 480, w, h, 1, w, h))
            val home = scale(win, Nav.None, Surface.Home, false)
            val page = scale(win, Nav.None, Surface.Page, false)
            assertNotNull("$w x $h takes the page base", page.fit)
            val ws = rolePx(Roles.homeItem, home, home.stdPpu).toFloat() / rolePx(Roles.pageHeader, page, page.stdPpu)
            assertEquals("$w x $h wordS0", want, ws.toDouble(), 0.0001)
            assertTrue(ws < Moves.kT2Scale.v[0])
            val m = released(MoveKind.T2, t2Ends.copy(wordS0 = ws))
            var last = m.at(Part.Word, Prop.S, 0)
            for (t in Moves.kT2Scale.t) {
                val v = m.at(Part.Word, Prop.S, t.toLong())
                assertTrue("$w x $h S rises at $t", v > last)
                last = v
            }
        }
    }

    @Test
    fun thePeekGoesOutOver200Ms() {
        val m = started(MoveKind.T2, MoveEnds(word = true, wordS0 = 0.2647f, peek0 = 0.6f))
        near(0.6, m.at(Part.Peek, Prop.A, 0), "peek A at the start")
        near(0.3, m.at(Part.Peek, Prop.A, 100), "peek A at 100")
        near(0.0, m.at(Part.Peek, Prop.A, 200), "peek A at 200")
        val n = started(MoveKind.T2n, MoveEnds(peek0 = 0.6f))
        near(0.3, n.at(Part.Peek, Prop.A, 100), "T2n peek A at 100")
    }

    private val slide = MoveEnds(peek0 = 0.6f, slideX = 300f, grounds = true)

    private fun px(want: Double, got: Float, what: String) = assertEquals(what, want, got.toDouble(), 0.01)

    @Test
    fun t2wSlidesHomeOutThenThePageInOnExpOut6() {
        val m = released(MoveKind.T2w, slide)
        assertEquals(300L, m.swapAt)
        assertEquals(800L, m.endAt)
        val home = listOf(0L to (1.0 to 0.0), 16L to (0.72548 to -82.357), 100L to (0.13319 to -260.043), 150L to (0.04742 to -285.773), 300L to (0.0 to -300.0))
        for ((t, v) in home) {
            tight(v.first, m.at(Part.Home, Prop.A, t), "home A at $t")
            px(v.second, m.at(Part.Home, Prop.X, t), "home X at $t")
        }
        val page = listOf(0L to (0.0 to 300.0), 300L to (0.0 to 300.0), 316L to (0.17512 to 247.465), 400L to (0.70054 to 89.839), 550L to (0.95258 to 14.227), 800L to (1.0 to 0.0))
        for ((t, v) in page) {
            tight(v.first, m.at(Part.Content, Prop.A, t), "content A at $t")
            px(v.second, m.at(Part.Content, Prop.X, t), "content X at $t")
            for (p in listOf(Part.Header, Part.Pivots, Part.Ground)) assertEquals("$p A at $t", m.at(Part.Content, Prop.A, t), m.at(p, Prop.A, t), 0f)
        }
        for (t in listOf(0L, 150L, 300L, 550L, 800L)) tight(0.6, m.at(Part.Peek, Prop.A, t), "peek A at $t")
        assertFalse(m.has(Part.Peek, Prop.A))
        for (p in listOf(Part.Word, Part.LeaveWord, Part.Leave, Part.Zoom)) for (q in Prop.entries) assertFalse("$p $q", m.has(p, q))
    }

    @Test
    fun t2wTakesOneFormOnEveryBoard() {
        fun legs(f: MoveForms) = PageMove().also { it.start(MoveKind.T2w, 0, slide, f) }.legs.map { listOf(it.part, it.prop, it.from, it.to, it.delay, it.dur, it.curve) }
        val want = legs(forms)
        for (b in listOf(TokBoard.v1, TokBoard.r1, TokBoard.a105)) assertEquals("$b", want, legs(MoveForms.board(b)))
        assertEquals(want, legs(MoveForms(content = ContentForm.Step, hubRows = ContentForm.Step, pageRows = ContentForm.Step, leaveFade = false, chromeSlide = false, headerZoom = false, wordMorph = false)))
    }

    @Test
    fun aT2wReversedBeforeTheSwapRunsHomeBackLinearly() {
        val m = released(MoveKind.T2w, slide)
        m.reverse(150)
        assertTrue(m.reversed)
        assertEquals(300L, m.endAt)
        tight(0.04742, m.at(Part.Home, Prop.A, 150), "home A at the reverse")
        tight(0.52371, m.at(Part.Home, Prop.A, 225), "home A halfway back")
        tight(1.0, m.at(Part.Home, Prop.A, 300), "home A back")
        px(-285.773, m.at(Part.Home, Prop.X, 150), "home X at the reverse")
        px(0.0, m.at(Part.Home, Prop.X, 300), "home X back")
        for (t in listOf(150L, 225L, 300L)) tight(0.0, m.at(Part.Content, Prop.A, t), "content A at $t")
    }

    @Test
    fun aHeldWordHoldsItsTiedHeaderAndShiftsBothOnRelease() {
        val m = PageMove()
        m.start(MoveKind.T2, 0, MoveEnds(word = true, wordS0 = 0.2647f, wordS1 = 1f), forms)
        m.hold(Part.Word)
        near(0.2647, m.at(Part.Word, Prop.S, 50), "held word S")
        near(0.0, m.at(Part.Header, Prop.A, 500), "tied header held")
        assertEquals(100, m.releaseWords(100))
        assertEquals(535, m.wordEnd)
        near(0.0, m.at(Part.Header, Prop.A, 534), "header shifted")
        near(1.0, m.at(Part.Header, Prop.A, 535), "header shifted end")
    }

    @Test
    fun t4nHomeComesBackAtTheContentEnd() {
        val m = PageMove()
        m.start(MoveKind.T4n, 0, MoveEnds(sub = 0.6f, peek1 = 1f), forms)
        assertEquals(167L, m.swapAt)
        near(0.0, m.at(Part.Home, Prop.A, 167), "home at the swap")
        near(17.0 / 133, m.at(Part.Home, Prop.A, 184), "home A")
        near(0.6 * 17.0 / 133, m.at(Part.Sub, Prop.A, 184), "sub A")
    }

    private val leaving = MoveEnds(leaveRows = true, pxU = 3f)

    private val pitchAt = listOf(0 to 0.975, 17 to 0.94, 25 to 0.94 - 0.012 * 8 / 33, 50 to 0.928, 67 to 0.912, 83 to 0.90, 100 to 0.885, 167 to 0.885, 300 to 0.885)

    private val dxAt = listOf(0 to 7.8, 17 to 15.9, 25 to 16.95, 33 to 18.0, 50 to 19.8, 67 to 25.8, 83 to 29.7, 100 to 33.6, 167 to 33.6, 300 to 33.6)

    @Test
    fun t4hLeaveRowsShrinkOnTheT4hPitchKeys() {
        val m = started(MoveKind.T4h, leaving)
        assertEquals(0.93709, 0.94 - 0.012 * 8 / 33, 0.00001)
        for ((at, want) in pitchAt) tight(want, m.at(Part.Leave, Prop.S, at.toLong()), "leave S at $at")
    }

    @Test
    fun t4hLeaveRowsShiftOnTheT4hDxKeysInPagePx() {
        val m = started(MoveKind.T4h, leaving)
        for ((at, want) in dxAt) tight(want, m.at(Part.Leave, Prop.X, at.toLong()), "leave X at $at")
    }

    @Test
    fun t4nTakesTheSameLeaveRows() {
        val m = started(MoveKind.T4n, leaving)
        for ((at, want) in pitchAt) tight(want, m.at(Part.Leave, Prop.S, at.toLong()), "T4n leave S at $at")
        for ((at, want) in dxAt) tight(want, m.at(Part.Leave, Prop.X, at.toLong()), "T4n leave X at $at")
    }

    @Test
    fun aCloseWithNoLeaveRowsKeepsTheLeavingLayerWhole() {
        for (k in listOf(MoveKind.T4h, MoveKind.T4n)) {
            val m = started(k, MoveEnds(pxU = 3f))
            assertFalse(m.has(Part.Leave, Prop.S))
            assertFalse(m.has(Part.Leave, Prop.X))
            tight(1.0, m.at(Part.Leave, Prop.S, 50), "$k leave S")
        }
    }

    @Test
    fun theLeaveFadeIsUnchanged() {
        val m = started(MoveKind.T4h, leaving)
        for (at in listOf(0, 50, 100)) tight(1 - at / 167.0, m.at(Part.Leave, Prop.A, at.toLong()), "leave A at $at")
        tight(0.0, m.at(Part.Leave, Prop.A, 167), "leave A at 167")
    }

    @Test
    fun t7TurnsThePaneOutOnItsKeysForEveryForm() {
        assertEquals(300, npInk())
        for (form in NpForm.entries) {
            val m = started(MoveKind.T7, MoveEnds(npForm = form))
            near(-2.96 + 0.5 * (-68.29 + 2.96), m.at(Part.NpLeave, Prop.R, 125), "$form leave R")
            near(0.5 * 68.51, m.at(Part.NpLeave, Prop.X, 125), "$form leave X")
            near(-1.61 + 0.5 * (-25.21 + 1.61), m.at(Part.NpLeave, Prop.PX, 125), "$form leave PX")
            near(1.0, m.at(Part.NpLeave, Prop.A, 245), "$form leave A at its fade")
            near(1 - (88.0 / 91) * (88.0 / 91), m.at(Part.NpLeave, Prop.A, 333), "$form leave A")
            near(0.0, m.at(Part.NpLeave, Prop.A, 334), "$form leave gone at the pane end")
        }
        val px = PageMove().also { it.start(MoveKind.T7, 0, MoveEnds(pxU = 3f), forms) }
        near(3 * 68.51, px.at(Part.NpLeave, Prop.X, 250), "leave X in px")
    }

    @Test
    fun t7BringsNowPlayingInOnThePlaneAndEachPartOnItsOwnRamp() {
        val m = started(MoveKind.T7, MoveEnds())
        val b = TokBoard.a105
        near(0.0, m.at(Part.NpIn, Prop.A, 299), "in A before the ink")
        val lead = easeOut(17.0 / 333)
        near(lead, m.at(Part.NpIn, Prop.A, 300), "in A at the ink")
        near(lead + (1 - lead) * easeOut(100.0 / 316), m.at(Part.NpIn, Prop.A, 400), "in A")
        near(b.motion_t7PlaneAngle.toDouble(), m.at(Part.NpIn, Prop.R, 320), "plane held")
        near(b.motion_t7PlaneAngle * 0.125, m.at(Part.NpIn, Prop.R, 572), "plane landing")
        near(0.0, m.at(Part.NpIn, Prop.R, 758), "plane landed")
        near(b.motion_t7SlideX.toDouble(), m.at(Part.NpIn, Prop.X, 100), "slide held")
        near(0.0, m.at(Part.NpIn, Prop.X, 578), "slide landed")
        near(b.motion_t7HingeFrom.toDouble(), m.at(Part.NpIn, Prop.PX, 452), "hinge held")
        near(b.motion_t7HingeTo.toDouble(), m.at(Part.NpIn, Prop.PX, 655), "hinge landed")
        near(0.0, m.at(Part.NpArtist, Prop.A, 317), "artist from its part time")
        near(easeOut(158.0 / 317), m.at(Part.NpArtist, Prop.A, 317 + 158), "artist A")
        near(1.0, m.at(Part.NpArtist, Prop.A, 634), "artist at its land")
        near(1.0, m.at(Part.NpNext, Prop.A, 617), "next at its land")
        near(0.0, m.at(Part.NpBar, Prop.A, 367), "bar on the right time")
        near(1.0, m.at(Part.NpBar, Prop.A, 617), "bar landed")
        near(0.0, m.at(Part.NpStatus, Prop.A, 616), "status before")
        near(1.0, m.at(Part.NpStatus, Prop.A, 617), "status")
        near(-15.0, m.at(Part.NpRing, Prop.X, 300), "ring X")
        near(0.93, m.at(Part.NpRing, Prop.S, 450), "ring S")
        near(0.0, m.at(Part.NpPhoto, Prop.A, 649), "photo before")
        near(1.0, m.at(Part.NpPhoto, Prop.A, 650), "photo at rest")
        assertFalse(m.has(Part.NpPeek, Prop.A))
        val front = started(MoveKind.T7, MoveEnds(npForm = NpForm.Front))
        near(0.5, front.at(Part.NpPeek, Prop.A, 17), "peek A")
        near(0.0, front.at(Part.NpPeek, Prop.A, 317), "peek gone")
        val cut = started(MoveKind.T7, MoveEnds(npCut = true))
        for (p in npInGroup) {
            near(0.0, cut.at(p, Prop.A, 299), "$p cut before")
            near(1.0, cut.at(p, Prop.A, 300), "$p cut at the ink")
        }
    }

    @Test
    fun t8TurnsTheLeavingPageOutAndBringsThePageInRowByRow() {
        val m = started(MoveKind.T8, MoveEnds())
        near(40 * (112.0 / 162).pow(1.5), m.at(Part.NpOut, Prop.R, 100), "out R")
        near(1.0, m.at(Part.NpOut, Prop.A, 183), "out A before its faint")
        near(1 - 0.7 * 8 / 17, m.at(Part.NpOut, Prop.A, 191), "out A fainting")
        near(0.3, m.at(Part.NpOut, Prop.A, 216), "out A faint")
        near(0.0, m.at(Part.NpOut, Prop.A, 217), "out A gone at the out end")
        near(-78.46, m.at(Part.NpUnder, Prop.R, 267), "under R")
        near(-57.32, m.at(Part.NpUnder, Prop.R, 334), "under R keyed")
        near(0.0, m.at(Part.NpUnder, Prop.A, 266), "under A before")
        near(0.13, m.at(Part.NpUnder, Prop.A, 267), "under A")
        near(0.18, m.at(Part.NpUnder, Prop.A, 284), "under A keyed")
        near(0.0, m.at(Part.NpURow1, Prop.A, 283), "row 1 before its first key")
        near(0.12, m.at(Part.NpURow1, Prop.A, 284), "row 1")
        near(1.0, m.at(Part.NpUHeader, Prop.A, 517), "header landed")
        val front = started(MoveKind.T8, MoveEnds(npForm = NpForm.Front))
        near(26.9, front.at(Part.NpUnder, Prop.X, 267), "front left")
        near(-82.5, front.at(Part.NpEdge, Prop.X, 267), "front right")
        near(16.0 / 33, front.at(Part.NpUnder, Prop.A, 283), "front A")
        near(0.26, front.at(Part.NpPeek, Prop.A, 267), "front peek")
        val home = started(MoveKind.T8, MoveEnds(npHome = true, pxU = 2f))
        near(-65.6, home.at(Part.NpUnder, Prop.R, 267), "home R")
        near(-3.46, home.at(Part.NpUnder, Prop.X, 336), "home X")
        near(11.8, home.at(Part.NpUnder, Prop.PX, 300), "home hinge")
        near(0.5, home.at(Part.NpUnder, Prop.A, 292), "home A")
        val cut = started(MoveKind.T8, MoveEnds(npCut = true))
        for (p in npUnderGroup) {
            near(0.0, cut.at(p, Prop.A, 266), "$p cut before")
            near(1.0, cut.at(p, Prop.A, 267), "$p cut")
        }
    }

    @Test
    fun aT4nTurnTurnsThePageOutAndHomeIn() {
        val m = started(MoveKind.T4n, MoveEnds(turn = true, homeU = 2f, peek1 = 0.6f))
        assertEquals(Moves.kT4nOut, m.swapAt.toInt())
        val inAt = Moves.kT4nOut + 50
        near(1.0, m.at(Part.Leave, Prop.A, (Moves.kT4nOut - 1).toLong()), "leave before")
        near(0.0, m.at(Part.Leave, Prop.A, Moves.kT4nOut.toLong()), "leave gone")
        near(-67.2, m.at(Part.HomeTurn, Prop.R, inAt.toLong()), "home R")
        near(-8.6, m.at(Part.HomeTurn, Prop.X, inAt.toLong()), "home X")
        near(15.8, m.at(Part.HomeTurn, Prop.PX, 0), "home hinge")
        near(1.0, m.at(Part.HomeTurn, Prop.A, inAt.toLong()), "home A")
        near(0.6, m.at(Part.Peek, Prop.A, inAt.toLong()), "peek")
        near(0.0, m.at(Part.Sub, Prop.A, (inAt + 60).toLong()), "sub before")
    }

    @Test
    fun aBackBeforeTheSwapRunsToTheOriginOverTheTimeRun() {
        val m = PageMove()
        m.start(MoveKind.T3, 0, MoveEnds(), forms)
        m.reverse(100)
        assertTrue(m.reversed)
        assertEquals(100L, m.swapAt)
        near(1 - 100.0 / 133, m.at(Part.Leave, Prop.A, 100), "leave A at the reverse")
        near(1.0, m.at(Part.Leave, Prop.A, 200), "leave A back at the origin")
        assertFalse(m.held(Part.Content))
    }

    @Test
    fun startSetsTheKindTheTimeLiveAndClearsReversedAndTheBaseRow() {
        val m = started(MoveKind.T3, MoveEnds())
        m.set(Part.NpRing, Prop.X, 5f)
        m.set(Part.NpRing, Prop.S, 2f)
        m.reverse(100)
        assertTrue(m.reversed)
        m.stop()
        assertFalse(m.live)
        m.start(MoveKind.T4, 500, MoveEnds(), forms)
        assertEquals(MoveKind.T4, m.kind)
        assertEquals(500L, m.t0)
        assertTrue(m.live)
        assertFalse(m.reversed)
        for (q in Prop.entries) assertEquals("base $q", if (q == Prop.S) 1f else 0f, m.at(Part.NpRing, q, 600), 0f)
    }

    @Test
    fun aStopOfAMoveThatIsNotLiveChangesNothing() {
        val m = started(MoveKind.T3, MoveEnds())
        val legs = m.legs.map { listOf(it.part, it.prop, it.delay, it.dur) }
        m.stop()
        m.stop()
        assertFalse(m.live)
        assertEquals(MoveKind.T3, m.kind)
        assertEquals(0L, m.t0)
        assertEquals(legs, m.legs.map { listOf(it.part, it.prop, it.delay, it.dur) })
        near(1 - 50.0 / 133, m.at(Part.Leave, Prop.A, 50), "leave A at 50 after the stops")
        assertEquals(634L, m.endAt)
    }

    @Test
    fun landPutsEachPropsLastLegToIntoTheBaseAndClearsThePart() {
        val m = started(MoveKind.T3, MoveEnds())
        assertTrue(m.has(Part.Leave, Prop.A))
        val want = Prop.entries.associateWith { q -> m.last(Part.Leave, q)?.to ?: m.at(Part.Leave, q, 0) }
        m.land(Part.Leave)
        for (q in Prop.entries) {
            assertFalse("leave $q cleared", m.has(Part.Leave, q))
            for (t in listOf(0L, 50L, 1000L)) assertEquals("leave $q at $t", want.getValue(q), m.at(Part.Leave, q, t), 0f)
        }
        assertEquals(0f, want.getValue(Prop.A), 0f)
    }

    @Test
    fun theEndTakesTheSwapAndTheLegEndsAndNeverShrinks() {
        val t3 = started(MoveKind.T3, MoveEnds())
        assertEquals(167L, t3.swapAt)
        assertEquals(634L, t3.endAt)
        t3.add(MoveLeg(Part.Tile, Prop.X, 0f, 1f, 5000, 100))
        t3.shift(Part.Tile, 1)
        assertEquals(5101L, t3.endAt)
        t3.clear(Part.Tile)
        t3.shift(Part.Content, 100)
        assertEquals(5101L, t3.endAt)
        val none = started(MoveKind.None, MoveEnds())
        assertEquals(0L, none.endAt)
        none.pivot(Part.Zoom, 1f, 1f, 400)
        assertEquals(400L, none.endAt)
    }

    @Test
    fun theEndIsTheLargestOfTheEndTheSwapAndEveryLegEnd() {
        for (k in listOf(MoveKind.T3, MoveKind.T4, MoveKind.T2, MoveKind.T4h, MoveKind.T7, MoveKind.T8)) {
            val m = started(k, MoveEnds())
            val legs = m.legs.maxOf { it.end }.toLong()
            assertEquals("$k end", maxOf(m.swapAt - m.t0, legs), m.endAt - m.t0)
        }
        val m = started(MoveKind.T3, MoveEnds())
        val before = m.endAt - m.t0
        m.shift(Part.Content, 100)
        assertEquals(maxOf(before, m.legs.maxOf { it.end }.toLong()), m.endAt - m.t0)
        m.npPop(0)
        assertTrue("the end never shrinks", m.endAt - m.t0 >= before)
    }

    @Test
    fun aReverseRebuildsEachPartPropBackwardFromItsValueAtTheElapsedTime() {
        val m = started(MoveKind.T3, MoveEnds())
        m.release(Part.Content, 0)
        val now = 100L
        class Want(val part: Part, val prop: Prop, val from: Float, val to: Float, val delay: Int, val dur: Int)
        val want = m.legs.filter { m.leg(it.part, it.prop) === it }.map { f ->
            val z = requireNotNull(m.last(f.part, f.prop))
            val ran = z.end.toLong() - f.delay
            val v = m.at(f.part, f.prop, now)
            if (ran <= 0) {
                Want(f.part, f.prop, v, f.from, if (now >= f.delay) (now - f.delay).toInt() else 0, 0)
            } else {
                Want(f.part, f.prop, v, f.from, 0, (now - f.delay).coerceIn(0L, ran).toInt())
            }
        }
        assertTrue(want.isNotEmpty())
        m.reverse(now)
        val leave = requireNotNull(m.leg(Part.Leave, Prop.A))
        assertEquals((1 - 100.0 / 133).toFloat(), leave.from, 1e-4f)
        assertEquals(1f, leave.to, 0f)
        assertEquals(0, leave.delay)
        assertEquals(100, leave.dur)
        val zoom = requireNotNull(m.leg(Part.Zoom, Prop.S))
        assertEquals(1.34f, zoom.from, 1e-4f)
        assertEquals(1.34f, zoom.to, 1e-4f)
        assertEquals(0, zoom.delay)
        assertEquals(0, zoom.dur)
        val content = requireNotNull(m.leg(Part.Content, Prop.A))
        assertEquals(0f, content.from, 0f)
        assertEquals(0f, content.to, 0f)
        assertEquals(0, content.dur)
        assertEquals(200L, m.endAt)
        assertTrue(m.reversed)
        assertEquals(now, m.t0)
        assertEquals(now, m.swapAt)
        assertEquals(want.size, m.legs.size)
        for (w in want) {
            assertEquals("${w.part} ${w.prop} legs", 1, m.legsOf(w.part, w.prop))
            val l = requireNotNull(m.leg(w.part, w.prop))
            assertEquals("${w.part} ${w.prop} from", w.from, l.from, 0f)
            assertEquals("${w.part} ${w.prop} to", w.to, l.to, 0f)
            assertEquals("${w.part} ${w.prop} delay", w.delay, l.delay)
            assertEquals("${w.part} ${w.prop} dur", w.dur, l.dur)
        }
        assertEquals(want.maxOf { it.delay + it.dur }.toLong(), m.endAt - m.t0)
    }

    private fun powOut15(t: Double) = 1 - (1 - t).pow(1.5)

    private fun smoothStep(t: Double) = t * t * (3 - 2 * t)

    private fun started(k: MoveKind, e: MoveEnds) = PageMove().also { it.start(k, 0, e, forms) }

    @Test
    fun t3mGrowsTheTileAndTheWordThenFadesTheContentIn() {
        val m = started(MoveKind.T3m, MoveEnds(word = true, wordS0 = 0.5f, wordS1 = 1f, wordY0 = 0f, wordY1 = 1f, tileS0 = 0.2f, tileS1 = 1f))
        assertFalse(m.held(Part.Content))
        near(0.2 + 0.8 * cubicOut(144.0 / 595), m.at(Part.Tile, Prop.S, 100), "tile S")
        near(0.5 + 0.5 * easeOut(116.0 / 510), m.at(Part.Word, Prop.S, 100), "word S")
        near(cubicOut(86.0 / 600), m.at(Part.Word, Prop.Y, 100), "word Y")
        near(1.0, m.at(Part.Word, Prop.A, 613), "word before 614")
        near(0.0, m.at(Part.Word, Prop.A, 614), "word off at 614")
        near(1.0, m.at(Part.Header, Prop.A, 614), "header on at 614")
        near(0.0, m.at(Part.Content, Prop.A, 634), "content before 634")
        near(66.0 / 133, m.at(Part.Content, Prop.A, 700), "content A")
        assertEquals(767L, m.endAt)
    }

    @Test
    fun t4mShrinksTheWordThenFadesTheContentInFromTheWordEnd() {
        val m = started(MoveKind.T4m, MoveEnds(word = true, wordS0 = 1f, wordS1 = 0.4f, tileBack = true, tileS0 = 1f, tileS1 = 0.2f))
        m.release(Part.Content, 0)
        near(1 - 0.6 * cubicOut(112.0 / 360), m.at(Part.Word, Prop.S, 100), "word S")
        near(0.0, m.at(Part.Word, Prop.A, 348), "word off at 348")
        assertFalse(m.has(Part.Tile, Prop.S))
        near(0.0, m.at(Part.Content, Prop.A, 348), "content at the word end")
        near(117.0 / 233, m.at(Part.Content, Prop.A, 465), "content A")
        near(1.0, m.at(Part.Content, Prop.S, 200), "content S")
        near(0.02, m.at(Part.Pivots, Prop.A, 317), "pivots A at 317")
        near(0.79, m.at(Part.Pivots, Prop.A, 333), "pivots A at 333")
        val col = started(MoveKind.T4m, MoveEnds(word = true, colBack = true))
        col.release(Part.Content, 0)
        near(0.0, col.at(Part.Content, Prop.A, 300), "column back content at 300")
        near(83.0 / 167, col.at(Part.Content, Prop.A, 383), "column back content A")
    }

    @Test
    fun t3cFadesTheContentInFromTheWordScaleEnd() {
        val m = started(MoveKind.T3c, MoveEnds(word = true, wordS0 = 0.5f, wordS1 = 1f))
        assertTrue(m.cut)
        m.release(Part.Content, 0)
        near(0.0, m.at(Part.Content, Prop.A, 494), "content at the word scale end")
        near(0.5, m.at(Part.Content, Prop.A, 544), "content A")
        near(1.0, m.at(Part.Header, Prop.A, 614), "header at the word end")
        assertEquals(167L, m.swapAt)
    }

    @Test
    fun t3aGrowsTheArtAndFadesTheContentIn() {
        val m = started(MoveKind.T3a, MoveEnds(word = true, tileS0 = 0.2f, tileS1 = 1f))
        assertFalse(m.held(Part.Content))
        near(0.2 + 0.8 * cubicOut(144.0 / 595), m.at(Part.Tile, Prop.S, 100), "tile S")
        near(0.0, m.at(Part.Content, Prop.A, 83), "content A at 83")
        near(183.0 / 367, m.at(Part.Content, Prop.A, 266), "content A")
        near(((0.2 + 0.8 * cubicOut(44.0 / 595)) / 2 - 0.5) * 0.85, m.at(Part.Content, Prop.Y, 0), "content Y rides the art")
        near(0.0, m.at(Part.Content, Prop.Y, 450), "content Y lands at 450")
        near(1.0, m.at(Part.Header, Prop.A, 614), "header at the word end")
    }

    @Test
    fun t4aWithTheColumnHeldHasNoSwap() {
        val m = started(MoveKind.T4a, MoveEnds(word = true, colHold = true, tileBack = true, tileS0 = 1f, tileS1 = 0.2f))
        assertEquals(0L, m.swapAt)
        m.release(Part.Content, 0)
        near(0.0, m.at(Part.Content, Prop.A, 267), "content before the cut")
        near(0.5, m.at(Part.Content, Prop.A, 283), "content A at 283")
        near(1.0, m.at(Part.Content, Prop.A, 300), "content A at 300")
        near(1.0, m.at(Part.Header, Prop.A, 0), "header at once")
        near(1 - 0.8 * cubicOut(0.3), m.at(Part.Tile, Prop.S, 75), "tile S")
        assertEquals(300L, m.endAt)
    }

    private val back = MoveEnds(word = true, wordS0 = 1f, wordS1 = 119f / 450, wordX0 = 0f, wordX1 = 1f, wordY0 = 200f, wordY1 = 600f, sub = 0.6f, peek1 = 1f)

    @Test
    fun t4hWithAWordBringsTheLookAndTheSubtitleIn() {
        val m = started(MoveKind.T4h, back.copy(look = 0.4f))
        near(1.0, m.at(Part.Word, Prop.A, 299), "word before 300")
        near(0.0, m.at(Part.Word, Prop.A, 300), "word off at 300")
        near(0.0, m.at(Part.Look, Prop.A, 299), "look before the swap")
        near(0.4, m.at(Part.Look, Prop.A, 300), "look steps to the leaving look")
        near(0.4 + 0.6 * 17.0 / 33, m.at(Part.Look, Prop.A, 317), "look A")
        near(1.0, m.at(Part.Look, Prop.A, 333), "look A at the items end")
        near(0.6 * easeOut(110.0 / 220), m.at(Part.Sub, Prop.A, 436), "sub A")
    }

    @Test
    fun homeBackScaleRunsTheT4hScaleKeys() {
        val m = started(MoveKind.T4h, back)
        assertEquals(0.264444, 119.0 / 450, 0.000001)
        val typed = listOf(0 to 1.0, 8 to 0.967059, 17 to 0.93, 100 to 0.79 - 0.17 * 17 / 34, 233 to 0.31, 266 to 0.287562, 300 to 0.264444, 333 to 0.264444)
        for ((at, want) in typed) tight(want, m.at(Part.Word, Prop.S, at.toLong()), "word S at $at")
        tight(0.705, m.at(Part.Word, Prop.S, 100), "word S at 100")
    }

    @Test
    fun homeBackBaseRunsTheT4hBaseKeysAsProgress() {
        val m = started(MoveKind.T4h, back)
        val typed = listOf(0 to 0.0, 50 to 0.46 * 50 / 67, 100 to 0.58, 167 to 0.85, 300 to 1.0)
        for ((at, p) in typed) tight(p, (m.at(Part.Word, Prop.Y, at.toLong()) - 200) / 400, "word Y progress at $at")
        tight(0.343284, (m.at(Part.Word, Prop.Y, 50) - 200) / 400, "word Y progress at 50")
    }

    @Test
    fun homeBackXEasesOutOverT4hBox() {
        val m = started(MoveKind.T4h, back)
        tight(0.455669, m.at(Part.Word, Prop.X, 100), "word X at 100")
        tight(powOut15(100.0 / 300), m.at(Part.Word, Prop.X, 100), "word X powOut15")
        tight(1.0, m.at(Part.Word, Prop.X, 300), "word X at 300")
    }

    @Test
    fun theWordGoesAt300AndTheWordEndIs300() {
        val m = started(MoveKind.T4h, back)
        near(1.0, m.at(Part.Word, Prop.A, 299), "word A at 299")
        near(0.0, m.at(Part.Word, Prop.A, 300), "word A at 300")
        assertEquals(300, m.wordEnd)
    }

    @Test
    fun theT4hPeekComesInFrom50Ms() {
        assertEquals(50f, Tok.motion_t4hPeekDelay, 0f)
        for (k in listOf(MoveKind.T4h, MoveKind.T4n)) {
            val m = started(k, back)
            tight(0.0, m.at(Part.Peek, Prop.A, 50), "$k peek at 50")
            tight(0.5, m.at(Part.Peek, Prop.A, 150), "$k peek at 150")
            tight(1.0, m.at(Part.Peek, Prop.A, 250), "$k peek at 250")
        }
    }

    @Test
    fun t4nEndsAt300AndT4hAt546() {
        assertEquals(300L, started(MoveKind.T4n, MoveEnds(sub = 0.6f, peek1 = 1f)).endAt)
        assertEquals(300L, started(MoveKind.T4n, MoveEnds(sub = 0.6f)).endAt)
        assertEquals(546L, started(MoveKind.T4h, back).endAt)
    }

    @Test
    fun aRetargetedHomeBackRunsTheKeysFromTheWordAtTheRetarget() {
        val m = started(MoveKind.T4h, back.copy(retarget = true))
        m.from(Part.Word, Prop.S, 0.5f)
        tight(0.5, m.at(Part.Word, Prop.S, 0), "word S at 0")
        tight(0.93, m.at(Part.Word, Prop.S, 17), "word S at 17")
    }

    @Test
    fun homeBackOnThePageBaseDipsUnderItsEndAsTheReleaseKeys() {
        val none = Insets(0, 0, 0, 0)
        val win = Window(1080, 2160, 35, none, none, none, none, none, Dpi(403.2f, 403.2f, 480, 1080, 2160, 1, 1080, 2160))
        val home = scale(win, Nav.None, Surface.Home, false)
        val page = scale(win, Nav.None, Surface.Page, false)
        val ws = rolePx(Roles.homeItem, home, home.stdPpu).toFloat() / rolePx(Roles.pageHeader, page, page.stdPpu)
        assertEquals(0.40225, ws.toDouble(), 0.0001)
        val m = started(MoveKind.T4h, back.copy(wordS1 = ws))
        tight(0.385, m.at(Part.Word, Prop.S, 200), "word S at 200")
        tight(0.31, m.at(Part.Word, Prop.S, 233), "word S at 233")
        assertTrue(m.at(Part.Word, Prop.S, 233) < ws)
        tight(ws.toDouble(), m.at(Part.Word, Prop.S, 300), "word S at 300")
    }

    @Test
    fun aRowPushRunsTheRowWordKeysAndCutsTheLeavingPage() {
        val m = started(MoveKind.T3, MoveEnds(morph = true, morphKind = MorphKind.Row, morphS0 = 0f, morphS1 = 1f, morphX0 = 0f, morphX1 = 1f, morphY0 = 100f, morphY1 = 0f))
        assertTrue(m.cut)
        assertEquals(434, m.wordEnd)
        near(0.0, m.at(Part.Word, Prop.S, 0), "word S at 0")
        near(0.235, m.at(Part.Word, Prop.S, 17), "word S at 17")
        near(1.0, m.at(Part.Word, Prop.S, 434), "word S at 434")
        near(0.75, m.at(Part.Word, Prop.X, 50), "word X")
        near(100 - 53.6 * easeOut(0.5), m.at(Part.Word, Prop.Y, 50), "word Y drops to the row word mid")
        near(46.4, m.at(Part.Word, Prop.Y, 100), "word Y at the mid")
        near(46.4 * (1 - 0.542), m.at(Part.Word, Prop.Y, 200), "word Y on the base keys")
        near(0.72, m.at(Part.Word, Prop.A, 0), "word A at 0")
        near(1.0, m.at(Part.Word, Prop.A, 433), "word A before the end")
        near(0.0, m.at(Part.Word, Prop.A, 434), "word A at the end")
        near(0.0, m.at(Part.Leave, Prop.A, 0), "the leaving page cut while the content is held")
        near(0.0, m.at(Part.Header, Prop.A, 433), "header before the word end")
        near(1.0, m.at(Part.Header, Prop.A, 434), "header at the word end")
        val l = started(MoveKind.T3, MoveEnds(morph = true, morphKind = MorphKind.Row, lhead = true, pxU = 2f, sw = 200f, lheadBase = 50f))
        near(1.13, l.at(Part.LeaveWord, Prop.S, 33), "old header S")
        near(-111.6, l.at(Part.LeaveWord, Prop.Y, 83), "old header rise")
        near(1.0, l.at(Part.LeaveWord, Prop.A, 82), "old header before 83")
        near(0.0, l.at(Part.LeaveWord, Prop.A, 83), "old header off at 83")
        near(80.0, l.at(Part.LeaveWord, Prop.PX, 0), "old header pivot X")
        near(50.0, l.at(Part.LeaveWord, Prop.PY, 0), "old header pivot Y")
        near(0.0, l.at(Part.LeaveHeader, Prop.A, 0), "leave header off")
    }

    @Test
    fun aPivotPushZoomsTheListAtTheTapAndBringsThePivotRowIn() {
        val m = started(MoveKind.T3, MoveEnds(morph = true, morphKind = MorphKind.Pivot, morphS0 = 0.5f, morphS1 = 1f, tapX = 120f, tapY = 600f, sw = 720f, sh = 1280f))
        assertFalse(m.cut)
        assertEquals(438, m.wordEnd)
        near(1.11, m.at(Part.Leave, Prop.S, 117), "leave S at 117")
        near(0.65, m.at(Part.Leave, Prop.A, 83), "leave A at 83")
        near(120.0, m.at(Part.Leave, Prop.PX, 0), "leave pivot X")
        near(600.0, m.at(Part.Leave, Prop.PY, 0), "leave pivot Y")
        near(0.5 + 0.5 * cubicOut(112.0 / 450), m.at(Part.Word, Prop.S, 100), "word S")
        near(1.0, m.at(Part.Word, Prop.A, 437), "word A before 438")
        near(0.0, m.at(Part.Word, Prop.A, 438), "word A at 438")
        near(1.0, m.at(Part.Header, Prop.A, 438), "header at 438")
        near(0.63, m.at(Part.Pivots, Prop.S, 267), "pivots S at 267")
        near(0.0, m.at(Part.Pivots, Prop.A, 266), "pivots A before 267")
        near(0.2, m.at(Part.Pivots, Prop.A, 267), "pivots A at 267")
        near(1.0, m.at(Part.Pivots, Prop.A, 400), "pivots A at 400")
        near(0.59 * 720, m.at(Part.Pivots, Prop.PX, 300), "pivots pivot X")
        near(0.46 * 1280, m.at(Part.Pivots, Prop.PY, 300), "pivots pivot Y")
        m.release(Part.Content, 0)
        near(0.0, m.at(Part.Content, Prop.A, 399), "content before 400")
        near(1.0, m.at(Part.Content, Prop.A, 400), "content at 400")
    }

    @Test
    fun aRowPopShrinksTheLeavingRowsAndRunsTheRowWordToTheRow() {
        val m = started(MoveKind.T4, MoveEnds(morph = true, morphKind = MorphKind.Row, morphS0 = 8f, morphS1 = 1f, morphX0 = 0f, morphX1 = 70f, morphY0 = 0f, morphY1 = 140f, leaveRows = true, pxU = 3f, sw = 720f, zoomBase = 154f))
        assertEquals(316, m.wordEnd)
        m.release(Part.Content, 0)
        near(0.984, m.at(Part.Leave, Prop.S, 0), "leave S at 0")
        near(0.883, m.at(Part.Leave, Prop.S, 117), "leave S at 117")
        near(3.3 * 3, m.at(Part.Leave, Prop.X, 0), "leave X at 0")
        near(13.9 * 3, m.at(Part.Leave, Prop.X, 100), "leave X at 100")
        near(7.9 * 3, m.at(Part.Leave, Prop.Y, 0), "leave Y at 0")
        near(47.6 * 3, m.at(Part.Leave, Prop.Y, 117), "leave Y at 117")
        near(0.5, m.at(Part.Leave, Prop.A, 83), "leave A at 83")
        near(8 - 1.7 * easeOut(33.0 / 67), m.at(Part.Word, Prop.S, 33), "word S eases to its first key")
        near(6.3, m.at(Part.Word, Prop.S, 67), "word S at 67")
        near(70 * 1.7 / 7, m.at(Part.Word, Prop.X, 67), "word X at 67")
        near(140 * 1.7 / 7, m.at(Part.Word, Prop.Y, 67), "word Y at 67")
        near(1.0, m.at(Part.Word, Prop.S, 300), "word S at 300")
        near(70.0, m.at(Part.Word, Prop.X, 300), "word X at 300")
        near(0.51, m.at(Part.Word, Prop.A, 0), "word A at 0")
        near(0.25, m.at(Part.Word, Prop.A, 315), "word A before 316")
        near(0.0, m.at(Part.Word, Prop.A, 316), "word A at 316")
        near(1.115, m.at(Part.Zoom, Prop.S, 217), "zoom S at 217")
        near(0.04, m.at(Part.Zoom, Prop.A, 217), "zoom A at 217")
        near(0.36 * 720, m.at(Part.Zoom, Prop.PX, 217), "zoom pivot X")
        near(154.0, m.at(Part.Zoom, Prop.PY, 217), "zoom pivot Y")
        near(1.0, m.at(Part.Header, Prop.A, 283), "header at 283")
        near(easeOut(83.0 / 167), m.at(Part.Content, Prop.A, 383), "content A")
    }

    @Test
    fun aT3IntoTheQueueCutsTheLeavingPage() {
        val m = started(MoveKind.T3, MoveEnds(npQueue = true))
        assertTrue(m.cut)
        assertTrue(m.held(Part.Content))
        for (p in listOf(Part.Leave, Part.LeaveHeader, Part.LeavePivots)) {
            assertEquals("$p A legs", 1, m.legsOf(p, Prop.A))
            near(0.0, m.at(p, Prop.A, 0), "$p A at 0")
            near(0.0, m.at(p, Prop.A, 50), "$p A at 50 while the content is held")
        }
        val plain = started(MoveKind.T3, MoveEnds())
        assertFalse(plain.cut)
        near(1 - 50.0 / 133, plain.at(Part.Leave, Prop.A, 50), "a plain T3 fades the leaving page")
    }

    @Test
    fun onlyT3mAndT3aStartWithTheContentFree() {
        for (k in listOf(MoveKind.T3, MoveKind.T4, MoveKind.T3c, MoveKind.T4m, MoveKind.T4a, MoveKind.T2, MoveKind.T3z, MoveKind.T4z)) {
            assertTrue("$k holds", started(k, MoveEnds(word = true)).held(Part.Content))
        }
        for (k in listOf(MoveKind.T3m, MoveKind.T3a)) assertFalse("$k holds no content", started(k, MoveEnds(word = true)).held(Part.Content))
    }

    @Test
    fun t3zZoomsTheArrivingPageInAndT4zFadesTheLeavingPageDown() {
        val z = started(MoveKind.T3z, MoveEnds(sw = 720f, sh = 1280f))
        assertEquals(167L, z.swapAt)
        near(1.0, z.at(Part.Leave, Prop.A, 17), "leave held while the content is held")
        z.release(arriveGroup, 0)
        near(1.0, z.at(Part.Leave, Prop.A, 16), "leave before the cut")
        near(0.0, z.at(Part.Leave, Prop.A, 17), "leave cut at 17")
        near(0.45, z.at(Part.Content, Prop.S, 50), "content S at 50")
        near(0.04, z.at(Part.Content, Prop.A, 33), "content A at 33")
        near(0.69 * 720, z.at(Part.Content, Prop.PX, 100), "content pivot X at 100")
        near(0.55 * 720, z.at(Part.Content, Prop.PX, 417), "content pivot X at 417")
        near(0.84 * 1280, z.at(Part.Content, Prop.PY, 0), "content pivot Y")
        near(0.0, z.at(Part.Header, Prop.A, 416), "header before 417")
        near(1.0, z.at(Part.Header, Prop.A, 417), "header at 417")
        near(1.0, z.at(Part.Pivots, Prop.A, 417), "pivots at 417")
        val f = started(MoveKind.T4z, MoveEnds(pxU = 3f))
        f.release(Part.Content, 0)
        near(0.45, f.at(Part.Leave, Prop.A, 17), "leave A at 17")
        near(13.2 * 3, f.at(Part.Leave, Prop.Y, 67), "leave drop")
        near(0.37, f.at(Part.Header, Prop.A, 133), "header at 133")
        near(1.0, f.at(Part.Header, Prop.A, 300), "header at 300")
        near(0.5, f.at(Part.Content, Prop.A, 300), "content A at 300")
    }

    @Test
    fun aPivotMovesThePartAboutANewPointKeepingItsPlace() {
        val m = started(MoveKind.T3, MoveEnds())
        val s = m.at(Part.Zoom, Prop.S, 200)
        m.pivot(Part.Zoom, 40f, 20f, 200)
        near(0.0, m.at(Part.Zoom, Prop.X, 199), "zoom X before the pivot")
        near((1 - s) * -40.0, m.at(Part.Zoom, Prop.X, 200), "zoom X keeps its place")
        near((1 - s) * -20.0, m.at(Part.Zoom, Prop.Y, 200), "zoom Y keeps its place")
        near(0.0, m.at(Part.Zoom, Prop.PX, 199), "zoom pivot X before")
        near(40.0, m.at(Part.Zoom, Prop.PX, 200), "zoom pivot X")
        near(20.0, m.at(Part.Zoom, Prop.PY, 200), "zoom pivot Y")
    }

    @Test
    fun thePopAnchorIsTheListTopForT4AndBelowTheScreenForTheHomePops() {
        assertEquals(360, PageMove.popAnchor(MoveKind.T4, 360, 1280, 3f))
        assertEquals(1280 + 164, PageMove.popAnchor(MoveKind.T4h, 360, 1280, 3f))
        assertEquals(1280 + 164, PageMove.popAnchor(MoveKind.T4n, 360, 1280, 3f))
    }

    @Test
    fun theRowsFormSlidesAndFadesTheRowsIn() {
        val m = started(MoveKind.T3, MoveEnds(rows = true, rowsDx = 20.5f))
        m.release(Part.Content, 0)
        near(0.79 + 0.21 * powOut15(209.0 / 418), m.at(Part.Content, Prop.S, 209), "rows S")
        near(20.5 * (1 - powOut15(209.0 / 418)), m.at(Part.Content, Prop.X, 209), "rows X")
        near(0.0, m.at(Part.Content, Prop.A, 24), "rows A at the fade delay")
        near(smoothStep(0.5), m.at(Part.Content, Prop.A, 166), "rows A")
    }

    @Test
    fun shiftNpPopAndNpShiftMoveTheirLegs() {
        val t3 = started(MoveKind.T3, MoveEnds())
        t3.shift(Part.Header, 100)
        near(0.0, t3.at(Part.Header, Prop.A, 372), "header shifted")
        near(1.0, t3.at(Part.Header, Prop.A, 373), "header shifted on")
        val t4 = started(MoveKind.T4, MoveEnds())
        t4.release(Part.Content, 0)
        t4.npPop(200)
        near(0.0, t4.at(Part.Content, Prop.A, 200), "content from the pop delay")
        near(119.0 / 123, t4.at(Part.Content, Prop.A, 319), "content A")
        for (p in arriveGroup) for (l in listOf(Prop.A, Prop.S, Prop.X, Prop.Y)) {
            val leg = t4.leg(p, l) ?: continue
            assertTrue("$p $l from the pop delay", leg.delay >= 200)
        }
        val late = started(MoveKind.T4, MoveEnds())
        late.release(Part.Content, 0)
        for (p in arriveGroup) assertTrue("$p has an A leg", late.leg(p, Prop.A) != null)
        late.npPop(1000)
        for (p in arriveGroup) for (l in listOf(Prop.A, Prop.S, Prop.X, Prop.Y)) {
            val leg = late.leg(p, l) ?: continue
            assertTrue("$p $l from the late pop", leg.delay >= 1000)
        }
        val t7 = started(MoveKind.T7, MoveEnds())
        t7.npShift(50)
        near(0.0, t7.at(Part.NpPhoto, Prop.A, 699), "photo before")
        near(1.0, t7.at(Part.NpPhoto, Prop.A, 700), "photo shifted")
        near(0.0, t7.at(Part.NpIn, Prop.A, 349), "np in shifted")
        near(0.0, t7.at(Part.NpArtist, Prop.A, 367), "artist shifted")
    }
}
