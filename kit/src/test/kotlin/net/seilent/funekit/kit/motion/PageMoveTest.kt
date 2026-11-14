package net.seilent.funekit.kit.motion

import net.seilent.funekit.Dpi
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Rel
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
        val b = MoveForms.board(TokBoard.v1)
        assertTrue(b.leaveFade)
        assertEquals(ContentForm.Zoom, b.content)
        assertTrue(b.chromeSlide)
        assertTrue(b.headerZoom)
        assertTrue(b.t3mFade)
        assertTrue(b.wordMorph)
    }

    @Test
    fun t4At200() {
        val m = PageMove()
        m.start(MoveKind.T4, 1000, pop, forms)
        m.release(Part.Content, 1000)
        val t = 1200L
        assertEquals(1167L, m.swapAt)
        near(cubicOut(212.0 / 400), m.at(Part.Word, Prop.S, t), "word S")
        near(0.47, m.at(Part.LeaveWord, Prop.A, t), "leave word A")
        near(0.53, m.at(Part.Word, Prop.A, t), "word A")
        near(1.34 - 0.34 * easeOut(62.0 / 135), m.at(Part.Zoom, Prop.S, t), "zoom S")
        near(49.8 * (1 - easeOut(33.0 / 167)), m.at(Part.Pivots, Prop.X, t), "pivot X")
        near(28.2 * (1 - easeOut(33.0 / 167)), m.at(Part.Pivots, Prop.Y, t), "pivot Y")
        near(33.0 / 67, m.at(Part.Pivots, Prop.A, t), "pivot A")
        near(33.0 / 133, m.at(Part.Ground, Prop.A, t), "ground A")
        near(0.0, m.at(Part.Leave, Prop.A, t), "leave A")
        near(67.0 / 305, m.at(Part.Content, Prop.A, t), "content A")
        near(0.71 + 0.29 * easeOut(67.0 / 305), m.at(Part.Content, Prop.S, t), "content S")
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
        assertEquals(17, Rel.T2_ROWS_INK)
        val m = released(MoveKind.T2, t2Ends)
        assertEquals(300L, m.swapAt)
        rowsInk(m, Part.Content)
        near(0.0, m.at(Part.Home, Prop.A, 100), "home A")
        near(49.8, m.at(Part.Pivots, Prop.X, 100), "pivots before their delay")
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
    fun theRowsScaleAndShiftRunOverMotionRows() {
        val m = released(MoveKind.T2, t2Ends)
        tight(0.79, m.at(Part.Content, Prop.S, 0), "S at 0")
        tight(0.80268, m.at(Part.Content, Prop.S, 17), "S at 17")
        tight(0.86065, m.at(Part.Content, Prop.S, 100), "S at 100")
        tight(0.92091, m.at(Part.Content, Prop.S, 200), "S at 200")
        tight(1.0, m.at(Part.Content, Prop.S, 418), "S at 418")
        tight(61.5, m.at(Part.Content, Prop.X, 0), "X at 0")
        for (t in listOf(17, 100, 200)) {
            near(61.5 * (1 - powOut15(t / 418.0)), m.at(Part.Content, Prop.X, t.toLong()), "X at $t")
        }
        tight(0.0, m.at(Part.Content, Prop.X, 418), "X at 418")
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
            assertTrue(ws < Rel.T2_SCALE.v[0])
            val m = released(MoveKind.T2, t2Ends.copy(wordS0 = ws))
            var last = m.at(Part.Word, Prop.S, 0)
            for (t in Rel.T2_SCALE.t) {
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
    fun t4hHomeComesBackAtTheSwap() {
        val m = PageMove()
        m.start(MoveKind.T4n, 0, MoveEnds(sub = 0.6f, peek1 = 1f), forms)
        assertEquals(300L, m.swapAt)
        near(0.0, m.at(Part.Home, Prop.A, 300), "home at the swap")
        near(17.0 / 33, m.at(Part.Home, Prop.A, 317), "home A")
        near(0.6 * 17.0 / 33, m.at(Part.Sub, Prop.A, 317), "sub A")
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
    fun t7LegsPerForm() {
        assertEquals(100, npInk(NpForm.Page, forms))
        assertEquals(300, npInk(NpForm.Front, forms))
        assertEquals(350, npInk(NpForm.Ring, forms))
        assertEquals(50, npInk(NpForm.Ring, MoveForms(leaveFade = false)))
        val page = PageMove().also { it.start(MoveKind.T7, 0, MoveEnds(), forms) }
        near(easeOut(150.0 / 333), page.at(Part.NpIn, Prop.A, 250), "np in A")
        near(39.4, page.at(Part.NpIn, Prop.R, 150), "np in R")
        near(39.4 * (1 - quartOut(50.0 / 400)), page.at(Part.NpIn, Prop.R, 200), "np in R at 200")
        near(0.0, page.at(Part.NpPhoto, Prop.A, 449), "photo before")
        near(1.0, page.at(Part.NpPhoto, Prop.A, 450), "photo at rest")
        near(1 - 20.0 / 33, page.at(Part.NpLeave, Prop.A, 20), "np leave A")
        val front = PageMove().also { it.start(MoveKind.T7, 0, MoveEnds(npForm = NpForm.Front), forms) }
        near(39.4 * (1 - quartOut(50.0 / 400)), front.at(Part.NpIn, Prop.R, 400), "front R")
        val cut = PageMove().also { it.start(MoveKind.T7, 0, MoveEnds(npCut = true), forms) }
        near(0.0, cut.at(Part.NpIn, Prop.A, 99), "cut before")
        near(1.0, cut.at(Part.NpIn, Prop.A, 100), "cut at ink")
    }

    @Test
    fun t8TurnsTheLeavingPageOut() {
        val m = PageMove()
        m.start(MoveKind.T8, 0, MoveEnds(npX = -26.5f), forms)
        near(40 * (112.0 / 162).pow(1.5), m.at(Part.NpOut, Prop.R, 100), "np out R")
        near(1.0, m.at(Part.NpOut, Prop.A, 166), "np out A before")
        near(0.0, m.at(Part.NpOut, Prop.A, 167), "np out A off")
        near(-26.5 * (1 - cubicOut(66.0 / 300)), m.at(Part.NpUnder, Prop.X, 300), "np under X")
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
        near(0.5, m.at(Part.Content, Prop.A, 625), "content A")
        assertEquals(750L, m.endAt)
    }

    @Test
    fun t4mShrinksTheWordAndBringsTheTileBack() {
        val m = started(MoveKind.T4m, MoveEnds(word = true, wordS0 = 1f, wordS1 = 0.4f, tileBack = true, tileS0 = 1f, tileS1 = 0.2f))
        m.release(Part.Content, 0)
        near(1 - 0.6 * cubicOut(112.0 / 360), m.at(Part.Word, Prop.S, 100), "word S")
        near(0.0, m.at(Part.Word, Prop.A, 348), "word off at 348")
        near(1 - 0.8 * cubicOut(0.5), m.at(Part.Tile, Prop.S, 75), "tile S")
        near(67.0 / 265, m.at(Part.Content, Prop.A, 200), "content A")
    }

    @Test
    fun t3cFadesTheContentInFromTheWordScaleEnd() {
        val m = started(MoveKind.T3c, MoveEnds(word = true, wordS0 = 0.5f, wordS1 = 1f))
        m.release(Part.Content, 0)
        near(0.5, m.at(Part.Content, Prop.A, 594), "content A")
        near(1.0, m.at(Part.Header, Prop.A, 614), "header at the word end")
        assertEquals(167L, m.swapAt)
    }

    @Test
    fun t3aGrowsTheArtAndFadesTheContentIn() {
        val m = started(MoveKind.T3a, MoveEnds(word = true, tileS0 = 0.2f, tileS1 = 1f))
        m.release(Part.Content, 0)
        near(0.2 + 0.8 * cubicOut(144.0 / 595), m.at(Part.Tile, Prop.S, 100), "tile S")
        near(0.5, m.at(Part.Content, Prop.A, 625), "content A")
        near(1.0, m.at(Part.Header, Prop.A, 614), "header at the word end")
    }

    @Test
    fun t4aWithTheColumnHeldHasNoSwap() {
        val m = started(MoveKind.T4a, MoveEnds(word = true, colHold = true, tileBack = true, tileS0 = 1f, tileS1 = 0.2f))
        assertEquals(0L, m.swapAt)
        near(1.0, m.at(Part.Content, Prop.A, 0), "content shown")
        near(0.0, m.at(Part.Header, Prop.A, 149), "header before the tile is back")
        near(1.0, m.at(Part.Header, Prop.A, 150), "header with the tile back")
        assertEquals(150L, m.endAt)
    }

    private val back = MoveEnds(word = true, wordS0 = 1f, wordS1 = 119f / 450, wordX0 = 0f, wordX1 = 1f, wordY0 = 200f, wordY1 = 600f, sub = 0.6f, peek1 = 1f)

    @Test
    fun t4hWithAWordBringsTheLookAndTheSubtitleIn() {
        val m = started(MoveKind.T4h, back)
        near(1.0, m.at(Part.Word, Prop.A, 332), "word before 333")
        near(0.0, m.at(Part.Word, Prop.A, 333), "word off at 333")
        near(17.0 / 33, m.at(Part.Look, Prop.A, 317), "look A")
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
    fun theWordGoesAt333AndTheWordEndIs300() {
        val m = started(MoveKind.T4h, back)
        near(1.0, m.at(Part.Word, Prop.A, 332), "word A at 332")
        near(0.0, m.at(Part.Word, Prop.A, 333), "word A at 333")
        assertEquals(300, m.wordEnd)
    }

    @Test
    fun theT4hPeekComesInFrom50Ms() {
        assertEquals(50f, Rel.motion_t4hPeekDelay, 0f)
        for (k in listOf(MoveKind.T4h, MoveKind.T4n)) {
            val m = started(k, back)
            tight(0.0, m.at(Part.Peek, Prop.A, 50), "$k peek at 50")
            tight(0.5, m.at(Part.Peek, Prop.A, 150), "$k peek at 150")
            tight(1.0, m.at(Part.Peek, Prop.A, 250), "$k peek at 250")
        }
    }

    @Test
    fun t4nEndsAt333AndT4hAt546() {
        assertEquals(333L, started(MoveKind.T4n, MoveEnds(sub = 0.6f, peek1 = 1f)).endAt)
        assertEquals(333L, started(MoveKind.T4n, MoveEnds(sub = 0.6f)).endAt)
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
    fun aRowMorphPushStepsTheLeavingHeaderOutWhenTheWordReachesTheBand() {
        val m = started(MoveKind.T3, MoveEnds(morph = true, morphKind = MorphKind.Row, morphS0 = 0f, morphS1 = 1f, morphX0 = 0f, morphX1 = 1f, morphY0 = 100f, morphY1 = 0f, rowAscent = 0f, rowBand = 50f))
        near(0.75, m.at(Part.Word, Prop.S, 75), "word S")
        near(0.0, m.at(Part.Word, Prop.X, 138), "word X before its delay")
        near(0.75, m.at(Part.Word, Prop.X, 304), "word X")
        near(0.5, m.at(Part.LeaveWord, Prop.A, 235), "leave word A")
        near(1.0, m.at(Part.Leave, Prop.A, 0), "leave held with the content")
        m.release(Part.Content, 0)
        near(1.0, m.at(Part.Leave, Prop.A, 16), "leave before the frame after the shown time")
        near(0.0, m.at(Part.Leave, Prop.A, 17), "leave cut one frame after the shown time")
        near(1.0, m.at(Part.LeaveHeader, Prop.A, 50), "leave header before")
        near(0.0, m.at(Part.LeaveHeader, Prop.A, 51), "leave header at the band")
    }

    @Test
    fun theRowsFormSlidesAndFadesTheRowsIn() {
        val m = started(MoveKind.T3, MoveEnds(rows = true, rowsDx = 20.5f))
        m.release(Part.Content, 0)
        near(0.79 + 0.21 * powOut15(209.0 / 418), m.at(Part.Content, Prop.S, 209), "rows S")
        near(20.5 * (1 - powOut15(209.0 / 418)), m.at(Part.Content, Prop.X, 209), "rows X")
        near(smoothStep(0.5), m.at(Part.Content, Prop.A, 67), "rows A")
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
        val t7 = started(MoveKind.T7, MoveEnds())
        t7.npShift(50)
        near(0.0, t7.at(Part.NpPhoto, Prop.A, 499), "photo before")
        near(1.0, t7.at(Part.NpPhoto, Prop.A, 500), "photo shifted")
        near(easeOut(100.0 / 333), t7.at(Part.NpIn, Prop.A, 250), "np in shifted")
    }
}
