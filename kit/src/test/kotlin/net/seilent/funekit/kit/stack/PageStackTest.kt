package net.seilent.funekit.kit.stack

import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.motion.ContentForm
import net.seilent.funekit.kit.motion.MoveEnds
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.MoveLeg
import net.seilent.funekit.kit.motion.MorphKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.touch.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PageStackTest {
    private val ends = MoveEnds(pivotX = 49.8f, pivotY = 28.2f)

    private fun stack() = PageStack<String>(MoveForms(), { PageKind.Grid }) { ends }.also { it.pages.add("hub") }

    private val pop: (Turn<String>) -> MoveKind = { MoveKind.T4 }

    @Test
    fun aPushSwapsAtTheSwapTimeAndRestsTheFrameAfterItsEnd() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertEquals(Phase.Exit, s.phase)
        s.frame(166)
        assertEquals("hub", s.top)
        s.frame(167)
        assertEquals("album", s.top)
        assertEquals(Phase.Enter, s.phase)
        s.frame(633)
        assertEquals(Phase.Enter, s.phase)
        s.frame(634)
        assertEquals("the end frame still draws the move", Phase.Enter, s.phase)
        s.frame(634)
        assertEquals("a second frame at the end time is still the end frame", Phase.Enter, s.phase)
        s.frame(650)
        assertEquals(Phase.Rest, s.phase)
    }

    @Test
    fun aBackBeforeTheSwapReversesToTheOrigin() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.frame(100)
        s.back(pop)
        s.frame(110)
        assertTrue(s.move.reversed)
        assertEquals(Phase.Enter, s.phase)
        assertEquals(listOf("hub"), s.pages.toList())
        s.frame(219)
        assertEquals(Phase.Enter, s.phase)
        s.frame(220)
        assertEquals(Phase.Enter, s.phase)
        s.frame(236)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub"), s.pages.toList())
    }

    @Test
    fun aTouchDuringAMoveLandsIt() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.touch()
        s.frame(50)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub", "album"), s.pages.toList())
        assertFalse(s.move.live)
    }

    @Test
    fun anOpenSheetTakesTheBackAndBlocksAPush() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        s.push("album", MoveKind.T3)
        s.frame(16)
        assertEquals(Phase.Rest, s.phase)
        s.back(pop)
        s.frame(150)
        val o = s.overlay
        assertTrue(o != null && o.closing)
        s.frame(150 + o!!.dur().toLong())
        assertNull(s.overlay)
        assertEquals(listOf("hub"), s.pages.toList())
    }

    @Test
    fun theSwapHookSeesTheTopPageAtTheSwapAndNotForAPopToHome() {
        val seen = mutableListOf<Pair<String, Long>>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, swapped = { p, at -> seen += p to at }) { ends }.also { it.pages.add("hub") }
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(seen.isEmpty())
        val push = s.move.swapAt
        s.frame(push)
        assertEquals(listOf("album" to push), seen)
        s.frame(s.move.endAt)
        assertEquals(Phase.Enter, s.phase)
        s.frame(s.move.endAt + STEP)
        assertEquals(Phase.Rest, s.phase)
        val t0 = s.move.endAt + 100
        s.pop(MoveKind.T4)
        s.frame(t0)
        val pop = s.move.swapAt
        assertEquals(1, seen.size)
        s.frame(pop)
        assertEquals(listOf("album" to push, "hub" to pop), seen)
        s.frame(s.move.endAt)
        s.frame(s.move.endAt + STEP)
        s.back { MoveKind.T4n }
        s.frame(s.move.endAt + 100)
        assertEquals(MoveKind.T4n, s.turn?.kind)
        s.frame(s.move.swapAt)
        assertTrue(s.pages.isEmpty())
        assertEquals(2, seen.size)
    }

    @Test
    fun theLastPagePopsToHomeAndThenThereIsNoBack() {
        val s = stack()
        assertTrue(s.canBack)
        s.back { MoveKind.T4n }
        s.frame(0)
        assertEquals(MoveKind.T4n, s.turn?.kind)
        assertNull(s.turn?.to)
        s.frame(167)
        assertTrue(s.pages.isEmpty())
        assertTrue(s.canBack)
        s.frame(299)
        assertEquals(Phase.Enter, s.phase)
        s.frame(300)
        assertEquals(Phase.Enter, s.phase)
        assertTrue(s.canBack)
        s.frame(316)
        assertEquals(Phase.Rest, s.phase)
        assertFalse(s.canBack)
    }

    @Test
    fun aPushFromHomeCanGoBackBeforeItsSwap() {
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }) { ends }
        assertFalse(s.canBack)
        s.push("hub", MoveKind.T2n)
        s.frame(0)
        assertTrue(s.canBack)
        s.back(pop)
        s.frame(100)
        assertTrue(s.move.reversed)
        assertTrue(s.pages.isEmpty())
        s.frame(100 + 100)
        assertEquals(Phase.Enter, s.phase)
        s.frame(100 + 100 + STEP)
        assertEquals(Phase.Rest, s.phase)
        assertTrue(s.pages.isEmpty())
    }

    @Test
    fun anOpenAskedAtTheRootTakesTheBackBeforeItsFrame() {
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }) { ends }
        assertFalse("premise: the root at rest has no back", s.canBack)
        val o = Overlay(OverKind.Sheet)
        assertTrue(s.open(o))
        assertTrue("premise: the open is asked and has not risen", s.taken && s.overlay == null)
        assertTrue("an asked open takes the back", s.canBack)
        s.frame(0)
        assertSame(o, s.overlay)
        assertTrue(s.canBack)
    }

    @Test
    fun aBackDuringT2wReversesBeforeTheSwapAndRetargetsAfterIt() {
        val slide = MoveEnds(peek0 = 0.6f, slideX = 300f, grounds = true)
        fun pushed() = PageStack<String>(MoveForms(), { PageKind.Plain }) { slide }.also {
            it.push("drawer", MoveKind.T2w)
            it.frame(0)
        }
        val a = pushed()
        a.frame(150)
        a.back { MoveKind.T4n }
        a.frame(150)
        assertTrue(a.move.reversed)
        assertTrue(a.pages.isEmpty())
        val b = pushed()
        b.frame(300)
        assertEquals(Phase.Enter, b.phase)
        b.back { MoveKind.T4n }
        b.frame(400)
        assertEquals(MoveKind.T4n, b.move.kind)
        assertEquals(true, b.turn?.retarget)
        for (p in listOf(Part.Leave, Part.LeaveHeader, Part.LeavePivots, Part.LeaveGround)) assertEquals("$p A", 0.70054, b.move.at(p, Prop.A, 400).toDouble(), 1e-4)
    }

    @Test
    fun aSecondBackDuringThePopToHomeDoesNothing() {
        val s = stack()
        s.back { MoveKind.T4n }
        s.frame(0)
        s.back { MoveKind.T4n }
        s.frame(50)
        assertEquals(Phase.Rest, s.phase)
        assertTrue(s.pages.isEmpty())
        assertNull(s.turn)
    }

    @Test
    fun aSettledOverlayStopsTheClock() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        assertTrue(s.moving)
        s.frame(380)
        assertFalse(s.moving)
        assertTrue(s.overlay != null)
    }

    @Test
    fun aTouchLandsAnOpeningOverlay() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        s.touch()
        s.frame(100)
        assertFalse(s.moving)
        assertEquals(1f, s.overlay!!.sheet(100).alpha, 0f)
    }

    @Test
    fun aBackOnAClosingOverlayLandsItAndPopsThePageUnder() {
        val s = stack()
        s.pages.add("album")
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        s.frame(400)
        s.back(pop)
        s.frame(410)
        assertTrue(s.overlay!!.closing)
        s.back(pop)
        s.frame(420)
        assertNull(s.overlay)
        assertEquals(MoveKind.T4, s.turn?.kind)
        assertEquals("hub", s.turn?.to)
    }

    @Test
    fun aBackAfterTheSwapRetargetsFromTheCurrentLegValues() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.frame(200)
        s.back(pop)
        s.frame(210)
        assertEquals(Phase.Exit, s.phase)
        assertEquals(MoveKind.T4, s.turn?.kind)
        assertEquals(listOf("hub", "album"), s.pages.toList())
        val q = 43.0 / 167
        assertEquals(43.0 / 67, s.move.at(Part.LeavePivots, Prop.A, 210).toDouble(), 0.002)
        assertEquals(49.8 * (1 - q * (2 - q)), s.move.at(Part.LeavePivots, Prop.X, 210).toDouble(), 0.01)
        assertEquals(0.0, s.move.at(Part.LeaveHeader, Prop.A, 210).toDouble(), 0.0)
        s.frame(210 + 167)
        assertEquals(listOf("hub"), s.pages.toList())
    }

    @Test
    fun theMoveStartHookRunsOnceOnTheFirstFrameOfAPushAPopARetargetedPopAndHomeAndNeverOnAReverse() {
        val starts = mutableListOf<Pair<MoveKind, Long>>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, starting = { starts += it.kind to it.at }) { ends }.also { it.pages.add("hub") }
        s.push("album", MoveKind.T3)
        assertTrue(starts.isEmpty())
        s.frame(0)
        assertEquals(listOf(MoveKind.T3 to 0L), starts)
        var t = rest(s, 0) + STEP
        assertEquals(1, starts.size)
        s.pop(MoveKind.T4)
        s.frame(t)
        assertEquals(MoveKind.T4 to t, starts.last())
        t = rest(s, t) + STEP
        assertEquals(2, starts.size)
        s.push("album", MoveKind.T3)
        s.frame(t)
        assertEquals(MoveKind.T3 to t, starts.last())
        s.back(pop)
        s.frame(t + STEP)
        assertTrue(s.move.reversed)
        t = rest(s, t + STEP) + STEP
        assertEquals(3, starts.size)
        s.push("album", MoveKind.T3)
        s.frame(t)
        assertEquals(4, starts.size)
        s.frame(t + RETARGET_MS)
        assertEquals(Phase.Enter, s.phase)
        s.back(pop)
        s.frame(t + RETARGET_MS + STEP)
        assertEquals(Phase.Exit, s.phase)
        assertEquals(true, s.turn?.retarget)
        assertEquals(MoveKind.T4 to t + RETARGET_MS + STEP, starts.last())
        t = rest(s, t + RETARGET_MS + STEP) + STEP
        assertEquals(5, starts.size)
        assertEquals(listOf("hub"), s.pages.toList())
        s.home(MoveKind.T4n)
        s.frame(t)
        assertEquals(MoveKind.T4n to t, starts.last())
        rest(s, t)
        assertEquals(6, starts.size)
    }

    @Test
    fun aPagePushPicksTheContentFormOfItsKind() {
        val forms = MoveForms(pageRows = ContentForm.Rest)
        val kinds = mapOf("albums" to PageKind.Hub, "settings" to PageKind.Plain, "grid" to PageKind.Grid)
        fun pushed(to: String) = PageStack<String>(forms, { kinds.getValue(it) }) { ends }.also {
            it.pages.add("hub")
            it.push(to, MoveKind.T3)
            it.frame(0)
        }
        val hub = pushed("albums")
        val h = 209.0 / 418
        assertEquals(0.79 + 0.21 * (1 - (1 - h).pow(1.5)), hub.move.at(Part.Content, Prop.S, 209).toDouble(), 0.002)
        assertEquals(0.0, hub.move.at(Part.Content, Prop.A, 24).toDouble(), 0.002)
        assertEquals(0.5, hub.move.at(Part.Content, Prop.A, 166).toDouble(), 0.002)
        val plain = pushed("settings")
        assertEquals(1f, plain.move.at(Part.Content, Prop.A, 0), 0f)
        assertEquals(1f, plain.move.at(Part.Content, Prop.S, 0), 0f)
        assertEquals(1f, plain.move.at(Part.Leave, Prop.A, 16), 0f)
        assertEquals(0f, plain.move.at(Part.Leave, Prop.A, 17), 0f)
        val grid = pushed("grid")
        assertEquals(0.71f, grid.move.at(Part.Content, Prop.S, 434), 0.0001f)
        assertEquals(0f, grid.move.at(Part.Content, Prop.A, 434), 0f)
        assertEquals(0.5f, grid.move.at(Part.Content, Prop.A, 534), 0.0001f)
    }

    @Test
    fun homeFromADeepStackRunsT4nAndLandsOnTheRoot() {
        val s = stack().also { it.pages.add("album") }
        s.home(MoveKind.T4n)
        s.frame(0)
        assertEquals(Phase.Exit, s.phase)
        assertEquals(MoveKind.T4n, s.turn?.kind)
        assertNull(s.turn?.to)
        s.frame(166)
        assertEquals(listOf("hub", "album"), s.pages.toList())
        s.frame(167)
        assertTrue(s.pages.isEmpty())
        assertEquals(Phase.Enter, s.phase)
        s.frame(299)
        assertEquals(Phase.Enter, s.phase)
        s.frame(300)
        assertEquals(Phase.Enter, s.phase)
        s.frame(316)
        assertEquals(Phase.Rest, s.phase)
    }

    @Test
    fun homeDropsAnOpenOverlayAndDoesNothingAtTheRoot() {
        val s = stack()
        s.open(Overlay(OverKind.Jump))
        s.frame(0)
        s.home(MoveKind.T4n)
        s.frame(16)
        assertNull(s.overlay)
        assertEquals(Phase.Exit, s.phase)
        val root = PageStack<String>(MoveForms(), { PageKind.Grid }) { ends }
        root.home(MoveKind.T4n)
        root.frame(0)
        assertEquals(Phase.Rest, root.phase)
        assertFalse(root.canBack)
    }

    @Test
    fun homeTakesItsKindFromThePageItsLandLeavesOnTop() {
        val s = stack()
        s.push("np", MoveKind.T3)
        s.frame(0)
        assertEquals("premise: the push is before its swap", Phase.Exit, s.phase)
        s.home { t -> if (t.from == "np") MoveKind.T8 else MoveKind.T4n }
        s.frame(16)
        assertEquals("the home kind reads the landed top", MoveKind.T8, s.turn?.kind)
        assertEquals("np", s.turn?.from)
    }

    @Test
    fun aPageBackHookTakesTheBackAtRestAndNothingPops() {
        val asked = mutableListOf<Pair<String, Long>>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, pageBack = { p, now -> asked += p to now; true }) { ends }.also { it.pages.add("hub") }
        s.back(pop)
        s.frame(40)
        assertEquals(listOf("hub" to 40L), asked)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub"), s.pages.toList())
    }

    @Test
    fun aPageThatIsNoRetargetLandsTheMoveAndThenAsksItsHook() {
        val asked = mutableListOf<String>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, pageBack = { p, _ -> asked += p; true }, retargets = { it != "folder" }) { ends }.also { it.pages.add("hub") }
        s.push("folder", MoveKind.T3)
        s.frame(0)
        s.frame(200)
        s.back(pop)
        s.frame(210)
        assertEquals(listOf("folder"), asked)
        assertEquals(Phase.Rest, s.phase)
        assertFalse(s.move.live)
        assertEquals(listOf("hub", "folder"), s.pages.toList())
    }

    @Test
    fun aHookThatDeclinesLetsThePagePop() {
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, pageBack = { _, _ -> false }) { ends }.also { it.pages.add("hub") }
        s.pages.add("album")
        s.back(pop)
        s.frame(0)
        assertEquals(MoveKind.T4, s.turn?.kind)
        assertEquals("album", s.turn?.from)
        assertEquals("hub", s.turn?.to)
    }

    @Test
    fun aHoldOnABackTargetKeepsTheSwapUntilTheHoldWindowEnds() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(s.hold(100, target = true, band = Gesture.Edge.None))
        s.frame(167)
        assertEquals(Phase.Exit, s.phase)
        assertEquals("hub", s.top)
        s.frame(566)
        assertEquals(Phase.Exit, s.phase)
        s.frame(567)
        assertEquals(Phase.Enter, s.phase)
        assertEquals("album", s.top)
        assertEquals(434, s.move.leg(Part.Content, Prop.A)?.delay)
        assertEquals(634L, s.move.endAt)
    }

    @Test
    fun aHoldThatStartsAfterTheSwapTimeKeepsNothing() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(s.hold(167, target = true, band = Gesture.Edge.None))
        s.frame(167)
        assertEquals(Phase.Enter, s.phase)
    }

    @Test
    fun aBandHoldNeverKeepsTheSwap() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(s.hold(100, target = false, band = Gesture.Edge.Left))
        s.frame(167)
        assertEquals(Phase.Enter, s.phase)
        assertEquals("album", s.top)
    }

    @Test
    fun aHeldBackBeforeTheSwapReversesThePush() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(s.hold(50, target = false, band = Gesture.Edge.Left))
        s.frame(100)
        s.heldBack(pop)
        s.frame(120)
        assertTrue(s.move.reversed)
        assertEquals(Phase.Enter, s.phase)
        assertEquals(listOf("hub"), s.pages.toList())
        assertNull(s.held)
    }

    @Test
    fun aHeldBackAfterTheSwapLandsThePushAndPopsIt() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(s.hold(50, target = false, band = Gesture.Edge.Left))
        s.frame(200)
        s.heldBack(pop)
        s.frame(210)
        assertFalse(s.move.reversed)
        assertEquals(Phase.Exit, s.phase)
        assertEquals(MoveKind.T4, s.turn?.kind)
        assertEquals("album", s.turn?.from)
        assertEquals("hub", s.turn?.to)
    }

    @Test
    fun aHeldBackFromAnEarlierMoveLandsItAndBacksOnThePageItLandsOn() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(s.hold(50, target = false, band = Gesture.Edge.Left))
        s.back(pop)
        s.frame(60)
        assertTrue(s.move.reversed)
        s.heldBack(pop)
        s.frame(70)
        assertFalse(s.move.reversed)
        assertEquals(Phase.Exit, s.phase)
        assertEquals("hub", s.turn?.from)
        assertNull(s.turn?.to)
    }

    @Test
    fun aPopHoldsOnlyItsBackTargetAndATapLandsItThenFindsNoPage() {
        val s = stack()
        s.back { MoveKind.T4n }
        s.frame(0)
        assertFalse(s.hold(10, target = false, band = Gesture.Edge.Left))
        assertTrue(s.hold(10, target = true, band = Gesture.Edge.None))
        s.frame(100)
        assertEquals(Phase.Exit, s.phase)
        s.heldBack { MoveKind.T4n }
        s.frame(110)
        assertEquals(Phase.Rest, s.phase)
        assertTrue(s.pages.isEmpty())
        assertNull(s.turn)
    }

    @Test
    fun nothingHoldsAtRestAndATouchDropsTheHold() {
        val s = stack()
        assertFalse(s.hold(0, target = true, band = Gesture.Edge.Left))
        assertNull(s.held)
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue(s.hold(100, target = true, band = Gesture.Edge.None))
        s.touch()
        s.frame(120)
        assertNull(s.held)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub", "album"), s.pages.toList())
    }

    @Test
    fun aBandHoldOnAnOpeningSheetClosesItFromWhereItIs() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        assertTrue(s.hold(50, target = false, band = Gesture.Edge.Right))
        s.heldBack(pop)
        s.frame(100)
        assertTrue(s.overlay!!.closing)
        assertEquals(listOf("hub"), s.pages.toList())
    }

    @Test
    fun aSettledSheetHoldsNothing() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        s.frame(400)
        assertFalse(s.hold(400, target = false, band = Gesture.Edge.Right))
    }

    private val jumpIn = Overlay(OverKind.Jump).dur().toLong()

    @Test
    fun jumpSettledIsSetOnTheFrameTheJumpGridLandsAndClearedWhenItsCloseStarts() {
        val s = stack()
        s.open(Overlay(OverKind.Jump))
        s.frame(0)
        assertFalse(s.jumpSettled)
        s.frame(jumpIn - 1)
        assertFalse(s.jumpSettled)
        s.frame(jumpIn)
        assertTrue(s.jumpSettled)
        s.frame(jumpIn + 100)
        assertTrue(s.jumpSettled)
        s.close()
        s.frame(jumpIn + 116)
        assertFalse(s.jumpSettled)
        s.frame(jumpIn + 1000)
        assertNull(s.overlay)
        assertFalse(s.jumpSettled)
    }

    @Test
    fun aSettledSheetOrDialogIsNotAJumpGrid() {
        for (kind in listOf(OverKind.Sheet, OverKind.Dlg)) {
            val s = stack()
            s.open(Overlay(kind))
            s.frame(0)
            s.frame(1000)
            assertTrue(s.overlay != null)
            assertFalse(s.jumpSettled)
        }
    }

    @Test
    fun aWarmHomeFromASettledGridClearsItAndAReopenStartsUnsettled() {
        val s = stack()
        s.open(Overlay(OverKind.Jump))
        s.frame(0)
        s.frame(jumpIn)
        assertTrue(s.jumpSettled)
        s.home(MoveKind.T4n)
        s.frame(jumpIn + 16)
        assertNull(s.overlay)
        assertFalse(s.jumpSettled)
        s.frame(jumpIn + 5000)
        s.frame(jumpIn + 5000 + STEP)
        assertEquals(Phase.Rest, s.phase)
        s.push("hub", MoveKind.T3)
        s.frame(jumpIn + 5016)
        s.frame(jumpIn + 10000)
        s.open(Overlay(OverKind.Jump))
        s.frame(jumpIn + 10016)
        assertFalse(s.jumpSettled)
    }

    @Test
    fun aTouchThatLandsAnOpeningGridSettlesIt() {
        val s = stack()
        s.open(Overlay(OverKind.Jump))
        s.frame(0)
        s.frame(10)
        assertFalse(s.jumpSettled)
        s.touch()
        s.frame(20)
        assertTrue(s.jumpSettled)
    }

    @Test
    fun aMoveStartLandsTheLiveMoveFirst() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.frame(100)
        assertEquals(Phase.Exit, s.phase)
        s.pop(MoveKind.T4)
        s.frame(116)
        val t = requireNotNull(s.turn)
        assertEquals(MoveKind.T4, t.kind)
        assertEquals("album", t.from)
        assertEquals("hub", t.to)
        assertEquals(116L, s.move.t0)
        assertTrue(s.move.live)
        assertEquals(listOf("hub", "album"), s.pages.toList())
    }

    @Test
    fun aTouchAtRestStopsNothing() {
        val drops = mutableListOf<String>()
        val swaps = mutableListOf<String>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, drop = { drops += it }, swapped = { p, _ -> swaps += p }) { ends }
            .also { it.pages.add("hub") }
        s.touch()
        s.frame(0)
        assertEquals(Phase.Rest, s.phase)
        assertNull(s.turn)
        assertFalse(s.move.live)
        assertEquals(listOf("hub"), s.pages.toList())
        assertTrue(drops.isEmpty())
        assertTrue(swaps.isEmpty())
    }

    @Test
    fun aLandSwapsAnExitTurnOnceAndLandsAnEnterTurnWithNoSwap() {
        val swaps = mutableListOf<Pair<String, Long>>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, swapped = { p, at -> swaps += p to at }) { ends }.also { it.pages.add("hub") }
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.touch()
        s.frame(50)
        assertEquals(listOf("album" to 50L), swaps)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub", "album"), s.pages.toList())
        s.pop(MoveKind.T4)
        s.frame(100)
        val at = s.move.swapAt
        s.frame(at)
        assertEquals(Phase.Enter, s.phase)
        assertEquals(listOf("album" to 50L, "hub" to at), swaps)
        s.touch()
        s.frame(at + STEP)
        assertEquals(2, swaps.size)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub"), s.pages.toList())
    }

    @Test
    fun aT7SwapsAtItsStartAndALandKeepsTheNowPlayingEntryOnTop() {
        val s = stack()
        s.push("np", MoveKind.T7)
        s.frame(0)
        assertEquals(0L, s.move.swapAt)
        assertEquals(Phase.Enter, s.phase)
        assertEquals(listOf("hub", "np"), s.pages.toList())
        s.touch()
        s.frame(STEP)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub", "np"), s.pages.toList())
        assertEquals("np", s.top)
    }

    private fun popsInto(to: String, isNp: (String) -> Boolean) = PageStack<String>(MoveForms(), { PageKind.Hub }, isNp = isNp) { MoveEnds() }
        .also { it.pages.addAll(listOf(to, "queue")) }

    @Test
    fun aT4IntoNowPlayingStartsEveryArrivingLegUnderTheContentDelayThereAndKeepsItsEnd() {
        val d = Tok.motion_npContentDelay.toInt()
        val plain = popsInto("np") { false }
        val np = popsInto("np") { it == "np" }
        for (s in listOf(plain, np)) {
            s.pop(MoveKind.T4)
            s.frame(0)
            assertEquals(MoveKind.T4, s.turn?.kind)
        }
        val a = plain.move.legs
        val b = np.move.legs
        assertEquals(a.size, b.size)
        val arrive = setOf(Part.Content, Part.Header, Part.Pivots)
        assertTrue("an arriving leg starts under the delay", a.any { it.part in arrive && it.delay < d })
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            assertEquals(x.part, y.part)
            assertEquals(x.prop, y.prop)
            if (x.part in arrive && x.delay < d) {
                assertEquals("${x.part} ${x.prop} delay", d, y.delay)
                assertEquals("${x.part} ${x.prop} end", maxOf(x.end, d), y.end)
            } else {
                assertEquals("${x.part} ${x.prop} delay", x.delay, y.delay)
                assertEquals("${x.part} ${x.prop} dur", x.dur, y.dur)
            }
        }
    }

    @Test
    fun aT4IntoAnotherPageKeepsItsLegs() {
        val plain = popsInto("hub") { false }
        val np = popsInto("hub") { it == "np" }
        for (s in listOf(plain, np)) {
            s.pop(MoveKind.T4)
            s.frame(0)
        }
        assertEquals(plain.move.legs.map { listOf(it.part, it.prop, it.delay, it.dur) }, np.move.legs.map { listOf(it.part, it.prop, it.delay, it.dur) })
    }

    @Test
    fun aT3IntoTheQueueCutsTheLeavingPageAtZero() {
        val s = PageStack<String>(MoveForms(), { PageKind.Hub }) { MoveEnds(npQueue = true) }.also { it.pages.add("np") }
        s.push("queue", MoveKind.T3)
        s.frame(0)
        for (p in listOf(Part.Leave, Part.LeaveHeader, Part.LeavePivots)) assertEquals("$p A at 0", 0f, s.move.at(p, Prop.A, 0), 0f)
    }

    private class E(val entry: Long, val page: String)

    private class Models {
        val held = mutableMapOf<Long, String>()
        val dropped = mutableListOf<Long>()
        val entered = mutableListOf<Long>()
        val turns = mutableListOf<MoveKind>()
        val stack = PageStack<E>(
            MoveForms(),
            { PageKind.Grid },
            enter = { e ->
                entered += e.entry
                held[e.entry] = e.page
            },
            drop = { e ->
                dropped += e.entry
                held.remove(e.entry)
            },
            started = { turns += it.kind },
        ) { MoveEnds() }

        fun open(page: String): E = E(stack.entry(), page)
    }

    private fun rest(s: PageStack<*>, from: Long): Long {
        var t = from
        s.frame(t)
        while (s.phase != Phase.Rest) {
            t += STEP
            s.frame(t)
        }
        return t
    }

    private fun swapped(s: PageStack<*>, from: Long): Long {
        var t = from
        s.frame(t)
        while (s.phase == Phase.Exit) {
            t += STEP
            s.frame(t)
        }
        return t
    }

    private fun Models.pushAll(pages: List<String>): Long {
        var t = 0L
        for (p in pages) {
            stack.push(open(p), MoveKind.T3)
            t = rest(stack, t) + STEP
        }
        return t
    }

    @Test
    fun entriesKeepTheirOwnModelsAndAPopDropsItsModelAtItsLandNotItsSwap() {
        val m = Models()
        var t = m.pushAll(listOf("hub", "album5", "artist", "album5"))
        assertEquals(listOf(0L, 1L, 2L, 3L), m.stack.pages.map { it.entry })
        assertEquals(listOf("hub", "album5", "artist", "album5"), m.stack.pages.map { m.held.getValue(it.entry) })
        assertEquals(4L, m.stack.entries)
        assertEquals(listOf(0L, 1L, 2L, 3L), m.entered)
        m.stack.pop(MoveKind.T4)
        t = swapped(m.stack, t)
        assertEquals(Phase.Enter, m.stack.phase)
        assertEquals(3, m.stack.pages.size)
        assertTrue(m.dropped.isEmpty())
        assertTrue(3L in m.held)
        rest(m.stack, t)
        assertEquals(listOf(3L), m.dropped)
        assertEquals(setOf(0L, 1L, 2L), m.held.keys)
    }

    @Test
    fun aReversedPushDropsThePushedModelAtItsLand() {
        val m = Models()
        var t = m.pushAll(listOf("hub"))
        val album = m.open("album5")
        m.stack.push(album, MoveKind.T3)
        m.stack.frame(t)
        assertTrue(album.entry in m.held)
        t += STEP
        m.stack.back { MoveKind.T4 }
        m.stack.frame(t)
        assertTrue(m.stack.move.reversed)
        assertTrue(m.dropped.isEmpty())
        rest(m.stack, t)
        assertEquals(listOf(album.entry), m.dropped)
        assertEquals(listOf(0L), m.stack.pages.map { it.entry })
    }

    @Test
    fun aRefusedPushMakesNoModelAndStartsNoTurn() {
        val m = Models()
        val t = m.pushAll(listOf("hub"))
        val album = m.open("album5")
        val artist = m.open("artist")
        m.stack.push(album, MoveKind.T3)
        m.stack.push(artist, MoveKind.T3)
        m.stack.frame(t)
        assertEquals(listOf(0L, album.entry), m.entered)
        val r = rest(m.stack, t) + STEP
        val queue = m.open("queue")
        m.stack.open(Overlay(OverKind.Sheet))
        m.stack.push(queue, MoveKind.T3)
        m.stack.frame(r)
        assertEquals(listOf(0L, album.entry), m.entered)
        assertEquals(setOf(0L, album.entry), m.held.keys)
        assertEquals(listOf(MoveKind.T3, MoveKind.T3), m.turns)
        assertEquals(listOf(0L, album.entry), m.stack.pages.map { it.entry })
    }

    @Test
    fun aRetargetedPushDropsItsPageAtTheLandOfThePopThatReplacedIt() {
        val m = Models()
        val t = m.pushAll(listOf("hub"))
        val album = m.open("album5")
        m.stack.push(album, MoveKind.T3)
        m.stack.frame(t)
        m.stack.frame(t + RETARGET_MS)
        assertEquals(Phase.Enter, m.stack.phase)
        m.stack.back { MoveKind.T4 }
        m.stack.frame(t + RETARGET_MS + STEP)
        assertEquals(Phase.Exit, m.stack.phase)
        assertEquals(listOf(MoveKind.T3, MoveKind.T3, MoveKind.T4), m.turns)
        val swap = swapped(m.stack, t + RETARGET_MS + STEP)
        assertEquals(listOf(0L), m.stack.pages.map { it.entry })
        assertTrue(m.dropped.isEmpty())
        assertTrue(album.entry in m.held)
        rest(m.stack, swap)
        assertEquals(listOf(album.entry), m.dropped)
        assertEquals(setOf(0L), m.held.keys)
    }

    @Test
    fun homeDropsEveryModelAtItsLand() {
        val m = Models()
        var t = m.pushAll(listOf("hub", "album5", "artist"))
        m.stack.home(MoveKind.T4n)
        t = swapped(m.stack, t)
        assertTrue(m.stack.pages.isEmpty())
        assertTrue(m.dropped.isEmpty())
        rest(m.stack, t)
        assertEquals(listOf(0L, 1L, 2L), m.dropped)
        assertTrue(m.held.isEmpty())
    }

    @Test
    fun aResetEntersOnlyThePagesItAddsAndStartsNoTurn() {
        val m = Models()
        val t = m.pushAll(listOf("hub", "album5"))
        val hub = m.stack.pages[0]
        val np = m.open("np")
        m.stack.reset(listOf(hub, np))
        m.stack.frame(t)
        assertEquals(listOf(hub.entry, np.entry), m.stack.pages.map { it.entry })
        assertEquals("only the added page enters", listOf(0L, 1L, np.entry), m.entered)
        assertEquals("no turn starts", listOf(MoveKind.T3, MoveKind.T3), m.turns)
        assertNull(m.stack.turn)
        assertEquals(Phase.Rest, m.stack.phase)
        assertFalse(m.stack.move.live)
    }

    @Test
    fun aResetDropsOnlyThePagesItRemoves() {
        val m = Models()
        val t = m.pushAll(listOf("hub", "album5", "artist"))
        m.stack.reset(listOf(m.stack.pages[0]))
        m.stack.frame(t)
        assertEquals(listOf(0L), m.stack.pages.map { it.entry })
        assertEquals(listOf(1L, 2L), m.dropped)
        assertEquals(setOf(0L), m.held.keys)
    }

    @Test
    fun aResetDropsAnOpenSheetWithNoCloseAndShowsThePageItWasOver() {
        val c = Cuts()
        val t = c.open(OverKind.Sheet)
        assertTrue("premise: the sheet covers the page", c.stack.covered)
        c.stack.reset(listOf("hub", "np"))
        c.stack.frame(t + STEP)
        assertNull(c.stack.overlay)
        assertFalse(c.stack.covered)
        assertTrue("no close", c.closings.isEmpty())
        assertEquals("the page under the sheet is shown, as a home shows it", listOf("hub"), c.dropped)
        assertEquals(listOf("hub", "np"), c.stack.pages.toList())
        assertEquals(listOf("np"), c.enters)
        assertTrue("no turn", c.starts.isEmpty())
    }

    @Test
    fun aBackHeldAcrossAResetLandsNothingAndPopsNoPage() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertTrue("premise: the back target holds the push", s.hold(50, target = true, band = Gesture.Edge.None))
        s.reset(listOf("hub", "np"))
        s.frame(60)
        s.heldBack(pop)
        s.frame(70)
        assertNull("no turn", s.turn)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub", "np"), s.pages.toList())
        s.back(pop)
        s.frame(90)
        assertEquals("premise: a back after the reset pops the new top", "np", s.turn?.from)
    }

    @Test
    fun aResetDuringAPushLandsItFirstAndDropsThePushedPageOnce() {
        val enters = mutableListOf<String>()
        val drops = mutableListOf<String>()
        val swaps = mutableListOf<String>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, enter = { enters += it }, drop = { drops += it }, swapped = { p, _ -> swaps += p }) { ends }
            .also { it.pages.add("hub") }
        s.push("album", MoveKind.T3)
        s.frame(0)
        assertEquals("premise: the push is before its swap", Phase.Exit, s.phase)
        s.reset(listOf("hub", "np"))
        s.frame(STEP)
        assertEquals(Phase.Rest, s.phase)
        assertNull(s.turn)
        assertFalse(s.move.live)
        assertEquals(listOf("hub", "np"), s.pages.toList())
        assertEquals("the land swaps the push in once", listOf("album"), swaps)
        assertEquals(listOf("album", "np"), enters)
        assertEquals("the pushed page drops once", listOf("album"), drops)
    }

    private fun settle(s: PageStack<String>, from: Long): Long = from + requireNotNull(s.overlay).dur().toLong()

    @Test
    fun aDialogOpenHidesTheFocusAndItsSettledCloseShowsItWithASnapshot() {
        val s = stack()
        s.open(Overlay(OverKind.Dlg))
        s.frame(0)
        assertTrue(s.hideFocus)
        assertEquals(1, s.snap)
        val t = settle(s, 0)
        s.frame(t)
        s.close()
        s.frame(t)
        assertTrue(s.overlay!!.closing)
        assertFalse(s.hideFocus)
        assertEquals(2, s.snap)
    }

    @Test
    fun aSheetOrADialogCoversThePageAndOnlyADialogHidesItsFocus() {
        val s = stack()
        assertTrue(s.focusShown)
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        assertFalse(s.hideFocus)
        assertTrue(s.covered)
        assertFalse(s.focusShown)
        val t = settle(s, 0)
        s.frame(t)
        s.accept()
        s.frame(t)
        assertTrue(s.covered)
        assertEquals(2, s.snap)
        while (s.overlay != null) s.frame(s.clock + STEP)
        assertFalse(s.covered)
        assertTrue(s.focusShown)
        s.open(Overlay(OverKind.Dlg))
        s.frame(s.clock + STEP)
        assertTrue(s.hideFocus)
        assertTrue(s.covered)
        val d = settle(s, s.clock)
        s.frame(d)
        s.close()
        s.frame(d)
        assertFalse(s.hideFocus)
        assertFalse(s.covered)
        assertTrue(s.focusShown)
        while (s.overlay != null) s.frame(s.clock + STEP)
        s.open(Overlay(OverKind.Jump))
        s.frame(s.clock + STEP)
        assertFalse(s.covered)
        assertTrue(s.focusShown)
    }

    @Test
    fun aReadyPageReleasesTheHeldHeaderAndPivotsOfAPageZoom() {
        val s = stack()
        s.push("album", MoveKind.T3z)
        s.frame(0)
        assertFalse(s.move.held(Part.Header))
        assertFalse(s.move.held(Part.Pivots))
        assertFalse(s.move.held(Part.Content))
        assertEquals(1f, s.move.at(Part.Header, Prop.A, 417), 0f)
        assertEquals(1f, s.move.at(Part.Pivots, Prop.A, 417), 0f)
    }

    @Test
    fun aPickerPickKeepsThePageCovered() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        s.pick()
        s.frame(STEP)
        assertFalse(s.hideFocus)
        assertTrue(s.covered)
        assertEquals(2, s.snap)
    }

    @Test
    fun aDialogClosedWhileOpeningKeepsTheHideThroughTheReverse() {
        val s = stack()
        s.open(Overlay(OverKind.Dlg), hides = true)
        s.frame(0)
        s.frame(STEP)
        s.close()
        s.frame(STEP)
        var t = STEP
        while (s.overlay != null) {
            assertTrue(s.overlay!!.closing)
            assertTrue(s.hideFocus)
            t += STEP
            s.frame(t)
        }
        assertEquals(1, s.snap)
        assertFalse(s.hideFocus)
    }

    @Test
    fun theLyricsSheetHidesTheFocusAndItsCloseKeepsTheHide() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet), hides = true)
        s.frame(0)
        assertTrue(s.hideFocus)
        val t = settle(s, 0)
        s.frame(t)
        s.close()
        s.frame(t)
        assertTrue(s.overlay!!.closing)
        assertTrue(s.hideFocus)
        assertEquals(1, s.snap)
    }

    @Test
    fun aLibrarySheetShowsTheFocusAndThePickerSwapTakesNoSnapshot() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        assertFalse(s.hideFocus)
        assertEquals(1, s.snap)
        val t = settle(s, 0)
        s.frame(t)
        var at = -1L
        s.swapMenu { at = it }
        s.frame(t)
        assertEquals(t, at)
        assertFalse(s.hideFocus)
        assertEquals(1, s.snap)
        assertTrue(s.overlay!!.moving(t))
        assertFalse(s.overlay!!.closing)
        val c = settle(s, t)
        s.frame(c)
        s.close()
        s.frame(c)
        assertTrue(s.overlay!!.closing)
        s.swapMenu {}
        s.frame(c + STEP)
        assertFalse(s.overlay!!.closing)
        assertTrue(s.overlay!!.moving(c + STEP))
        assertEquals(1, s.snap)
    }

    @Test
    fun theDroppedHookSeesTheTopPageOnTheFrameTheOverlayGoes() {
        val seen = mutableListOf<Pair<String, Long>>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, dropped = { p, at -> seen += p to at }) { ends }.also { it.pages.add("hub") }
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        val t = settle(s, 0)
        s.frame(t)
        s.close()
        s.frame(t)
        s.frame(t + STEP)
        assertTrue("nothing while the overlay closes", seen.isEmpty())
        val c = settle(s, t) + STEP
        s.frame(c)
        assertNull(s.overlay)
        assertEquals(listOf("hub" to c), seen)
        s.frame(c + STEP)
        assertEquals("once per overlay", 1, seen.size)
        s.open(Overlay(OverKind.Jump))
        s.frame(c + STEP)
        s.home(MoveKind.T4n)
        s.frame(c + STEP + STEP)
        assertEquals("a home that drops the overlay shows the page it leaves", listOf("hub" to c, "hub" to c + STEP + STEP), seen)
        s.frame(s.move.endAt)
        s.home(MoveKind.T4n)
        s.frame(s.move.endAt + STEP)
        assertEquals("a home with no overlay drops nothing", 2, seen.size)
    }

    @Test
    fun aDialogOpenedOverAClosingSheetFiresNoDroppedAndItsCloseFiresItOnce() {
        val seen = mutableListOf<Pair<String, Long>>()
        val s = PageStack<String>(MoveForms(), { PageKind.Grid }, dropped = { p, at -> seen += p to at }) { ends }.also { it.pages.add("hub") }
        s.open(Overlay(OverKind.Sheet))
        s.frame(0)
        val t = settle(s, 0)
        s.frame(t)
        val dialog = Overlay(OverKind.Dlg)
        s.accept()
        s.open(dialog)
        s.frame(t + STEP)
        assertSame(dialog, s.overlay)
        assertTrue("an open over a closing sheet drops it with no dropped", seen.isEmpty())
        val r = settle(s, t + STEP)
        s.frame(r)
        s.close()
        s.frame(r)
        val c = settle(s, r) + STEP
        s.frame(c)
        assertNull(s.overlay)
        assertEquals(listOf("hub" to c), seen)
        s.frame(c + STEP)
        assertEquals("once per drop", 1, seen.size)
    }

    @Test
    fun aSwapForOneOverlayDoesNothingOnceItIsClosingOrAnotherIsOpen() {
        val s = stack()
        val first = Overlay(OverKind.Sheet)
        s.open(first)
        s.frame(0)
        val t = settle(s, 0)
        s.frame(t)
        var swapped = 0
        s.close()
        s.swapMenu(first) { swapped++ }
        s.frame(t)
        assertEquals(0, swapped)
        assertSame(first, s.overlay)
        assertTrue(first.closing)
        val c = settle(s, t) + STEP
        s.frame(c)
        assertNull(s.overlay)
        val second = Overlay(OverKind.Sheet)
        s.open(second)
        s.frame(c)
        val r = settle(s, c)
        s.frame(r)
        s.swapMenu(first) { swapped++ }
        s.frame(r)
        assertEquals(0, swapped)
        assertFalse(second.moving(r))
        s.swapMenu(second) { swapped++ }
        s.frame(r)
        assertEquals(1, swapped)
    }

    @Test
    fun aSheetVerbAndAPickerPickShowTheFocusWithASnapshot() {
        val s = stack()
        s.open(Overlay(OverKind.Sheet), hides = true)
        s.frame(0)
        s.pick()
        s.frame(STEP)
        assertFalse(s.hideFocus)
        assertEquals(2, s.snap)
        val u = stack()
        u.open(Overlay(OverKind.Sheet), hides = true)
        u.frame(0)
        val t = settle(u, 0)
        u.frame(t)
        u.accept()
        u.frame(t)
        assertFalse(u.hideFocus)
        assertEquals(2, u.snap)
        assertTrue(u.overlay!!.closing)
    }

    @Test
    fun aJumpGridPickClosesTheGridWithNoSnapshotAndLeavesThePageLive() {
        val s = stack()
        s.open(Overlay(OverKind.Jump))
        s.frame(0)
        assertFalse(s.hideFocus)
        assertEquals(1, s.snap)
        val t = settle(s, 0)
        s.frame(t)
        assertTrue(s.jumpSettled)
        s.close()
        s.frame(t + STEP)
        assertTrue(s.overlay!!.closing)
        assertFalse(s.jumpSettled)
        assertEquals(1, s.snap)
        assertFalse(s.hideFocus)
        var at = t + STEP
        while (s.overlay != null) {
            at += STEP
            s.frame(at)
        }
        assertEquals(1, s.snap)
        assertFalse(s.hideFocus)
    }

    private class Cuts {
        val enters = mutableListOf<String>()
        val starts = mutableListOf<Turn<String>>()
        val closings = mutableListOf<Overlay>()
        val dropped = mutableListOf<String>()
        val stack = PageStack<String>(
            MoveForms(),
            { PageKind.Hub },
            enter = { enters += it },
            started = { starts += it },
            closing = { o, _ -> closings += o },
            dropped = { p, _ -> dropped += p },
        ) { MoveEnds() }.also { it.pages.add("hub") }

        fun open(kind: OverKind): Long {
            stack.open(Overlay(kind))
            stack.frame(0)
            val t = requireNotNull(stack.overlay).dur().toLong()
            stack.frame(t)
            return t
        }

        fun refused(o: Overlay?) {
            assertNull("no turn", stack.turn)
            assertSame("the overlay is kept", o, stack.overlay)
            assertTrue("nothing entered", enters.isEmpty())
        }
    }

    @Test
    fun aCutPushDropsASettledSheetWithNoCloseAndStartsThePushOnItsFrame() {
        val c = Cuts()
        val t = c.open(OverKind.Sheet)
        assertEquals(1, c.stack.snap)
        assertTrue(c.stack.covered)
        c.stack.cutPush("album", MoveKind.T3, 2)
        assertNotNull("an ask runs on a later frame", c.stack.overlay)
        c.stack.frame(t + STEP)
        assertNull(c.stack.overlay)
        val turn = requireNotNull(c.stack.turn)
        assertEquals(MoveKind.T3, turn.kind)
        assertEquals("hub", turn.from)
        assertEquals("album", turn.to)
        assertFalse(turn.popping)
        assertEquals(2, turn.row)
        assertEquals(t + STEP, turn.at)
        assertEquals(Phase.Exit, c.stack.phase)
        assertEquals(listOf(turn), c.starts)
        assertTrue("no close", c.closings.isEmpty())
        assertTrue("no dropped", c.dropped.isEmpty())
        assertFalse(c.stack.hideFocus)
        assertFalse(c.stack.covered)
        assertEquals("no snapshot", 1, c.stack.snap)
        assertEquals(listOf("album"), c.enters)
        c.stack.frame(t + STEP + STEP)
        assertNull(c.stack.overlay)
        assertTrue(c.dropped.isEmpty())
    }

    @Test
    fun aCutPushOverADialogIsRefused() {
        val c = Cuts()
        val t = c.open(OverKind.Dlg)
        val o = c.stack.overlay
        c.stack.cutPush("album", MoveKind.T3, 2)
        c.stack.frame(t + STEP)
        c.refused(o)
        assertFalse(o!!.closing)
    }

    @Test
    fun aCutPushOverAClosingSheetIsRefused() {
        val c = Cuts()
        val t = c.open(OverKind.Sheet)
        val o = c.stack.overlay
        c.stack.close()
        c.stack.cutPush("album", MoveKind.T3, 2)
        c.stack.frame(t + STEP)
        assertTrue(o!!.closing)
        c.refused(o)
    }

    @Test
    fun aCutPushOverARisingSheetIsRefused() {
        val c = Cuts()
        c.stack.open(Overlay(OverKind.Sheet))
        c.stack.frame(0)
        val o = c.stack.overlay
        assertTrue(o!!.moving(STEP))
        c.stack.cutPush("album", MoveKind.T3, 2)
        c.stack.frame(STEP)
        c.refused(o)
    }

    @Test
    fun aCutPushWhileAMoveRunsIsRefused() {
        val c = Cuts()
        c.stack.pages.add("artist")
        val t = c.open(OverKind.Sheet)
        val o = c.stack.overlay
        c.stack.pop(MoveKind.T4)
        c.stack.cutPush("album", MoveKind.T3, 2)
        c.stack.frame(t + STEP)
        val turn = requireNotNull(c.stack.turn)
        assertEquals(MoveKind.T4, turn.kind)
        assertTrue(turn.popping)
        assertEquals("artist", turn.from)
        assertSame("the overlay is kept", o, c.stack.overlay)
        assertTrue("nothing entered", c.enters.isEmpty())
    }

    @Test
    fun aCutPushDropsASettledJumpGridWithNoCloseAndStartsThePushOnItsFrame() {
        val c = Cuts()
        val t = c.open(OverKind.Jump)
        assertTrue(c.stack.jumpSettled)
        c.stack.cutPush("search", MoveKind.T3)
        assertNotNull("an ask runs on a later frame", c.stack.overlay)
        c.stack.frame(t + STEP)
        assertNull(c.stack.overlay)
        val turn = requireNotNull(c.stack.turn)
        assertEquals(MoveKind.T3, turn.kind)
        assertEquals("hub", turn.from)
        assertEquals("search", turn.to)
        assertFalse(turn.popping)
        assertEquals(t + STEP, turn.at)
        assertEquals(Phase.Exit, c.stack.phase)
        assertEquals(listOf(turn), c.starts)
        assertTrue("no close", c.closings.isEmpty())
        assertTrue("no dropped", c.dropped.isEmpty())
        assertFalse(c.stack.jumpSettled)
        assertEquals(listOf("search"), c.enters)
    }

    @Test
    fun aCutDropsASettledJumpGridWithNoCloseAndStartsNoTurn() {
        val c = Cuts()
        val t = c.open(OverKind.Jump)
        val snap = c.stack.snap
        assertTrue(c.stack.jumpSettled)
        c.stack.cut()
        assertNotNull("an ask runs on a later frame", c.stack.overlay)
        c.stack.frame(t + STEP)
        assertNull(c.stack.overlay)
        assertNull("no turn", c.stack.turn)
        assertEquals(Phase.Rest, c.stack.phase)
        assertTrue("no start", c.starts.isEmpty())
        assertTrue("no close", c.closings.isEmpty())
        assertTrue("no dropped", c.dropped.isEmpty())
        assertTrue("nothing entered", c.enters.isEmpty())
        assertFalse(c.stack.jumpSettled)
        assertEquals("no snapshot", snap, c.stack.snap)
        c.stack.frame(t + STEP + STEP)
        assertNull(c.stack.overlay)
        assertTrue(c.dropped.isEmpty())
    }

    @Test
    fun aCutDropsASettledSheetWithNoCloseAndNoTurn() {
        val c = Cuts()
        val t = c.open(OverKind.Sheet)
        assertEquals(1, c.stack.snap)
        assertTrue(c.stack.covered)
        c.stack.cut()
        c.stack.frame(t + STEP)
        assertNull(c.stack.overlay)
        assertNull("no turn", c.stack.turn)
        assertEquals(Phase.Rest, c.stack.phase)
        assertTrue("no close", c.closings.isEmpty())
        assertTrue("no dropped", c.dropped.isEmpty())
        assertTrue("nothing entered", c.enters.isEmpty())
        assertFalse(c.stack.hideFocus)
        assertFalse(c.stack.covered)
        assertEquals("no snapshot", 1, c.stack.snap)
    }

    @Test
    fun aCutOverADialogIsRefused() {
        val c = Cuts()
        val t = c.open(OverKind.Dlg)
        val o = c.stack.overlay
        c.stack.cut()
        c.stack.frame(t + STEP)
        c.refused(o)
        assertFalse(o!!.closing)
        assertTrue(c.dropped.isEmpty())
    }

    @Test
    fun aCutOverAnOpeningJumpGridIsRefused() {
        val c = Cuts()
        c.stack.open(Overlay(OverKind.Jump))
        c.stack.frame(0)
        val o = c.stack.overlay
        assertTrue("premise: the grid still opens", o!!.moving(STEP))
        c.stack.cut()
        c.stack.frame(STEP)
        c.refused(o)
        assertFalse(o.closing)
    }

    @Test
    fun aCutPushOnTheLastFrameOfAJumpGridCloseIsRefused() {
        val c = Cuts()
        val t = c.open(OverKind.Jump)
        val o = requireNotNull(c.stack.overlay)
        c.stack.close()
        c.stack.frame(t + STEP)
        val end = o.start + o.dur()
        assertTrue("premise: the grid closes", o.closing)
        assertFalse("premise: the close has run its time", o.moving(end))
        assertTrue("premise: the frame drops it after its asks", o.done(end))
        c.stack.cutPush("search", MoveKind.T3)
        c.stack.frame(end)
        assertNull("no turn", c.stack.turn)
        assertTrue("nothing entered", c.enters.isEmpty())
        assertNull("the close dropped it", c.stack.overlay)
        assertEquals(listOf("hub"), c.dropped)
        assertEquals(listOf(o), c.closings)
    }

    @Test
    fun aCutWhileAMoveRunsIsRefused() {
        val c = Cuts()
        c.stack.pages.add("artist")
        val t = c.open(OverKind.Jump)
        val o = c.stack.overlay
        c.stack.pop(MoveKind.T4)
        c.stack.cut()
        c.stack.frame(t + STEP)
        val turn = requireNotNull(c.stack.turn)
        assertEquals(MoveKind.T4, turn.kind)
        assertTrue(turn.popping)
        assertEquals("artist", turn.from)
        assertSame("the overlay is kept", o, c.stack.overlay)
        assertTrue(c.dropped.isEmpty())
    }

    companion object {
        private const val STEP = 16L
        private const val RETARGET_MS = 200L
        private const val NP_POP = 200
        private const val ALBUM = 7L
        private const val LANDED = 5000L
        private const val ENTERED = 200L
        private const val EXITING = 50L
    }

    private fun popped(hook: String): PageStack<String> {
        val move = PageMove()
        val s = PageStack<String>(
            MoveForms(),
            { PageKind.Plain },
            move = move,
            starting = { if (hook == "starting") move.npPop(NP_POP) },
            legs = { if (hook == "legs") move.npPop(NP_POP) },
        ) { ends }.also { it.pages.addAll(listOf("np", "queue")) }
        s.pop(MoveKind.T4)
        s.frame(0)
        return s
    }

    @Test
    fun theLegsHookRunsAfterTheMoveStartAndItsReleasesOnAPushAPopAndARetargetsPop() {
        val kinds = mutableListOf<MoveKind>()
        val s = PageStack<String>(MoveForms(), { PageKind.Plain }, legs = { kinds += it.kind }) { ends }.also { it.pages.add("hub") }
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.frame(300)
        s.back(pop)
        s.frame(310)
        s.frame(2000)
        s.pop(MoveKind.T4)
        s.frame(2010)
        assertEquals(listOf(MoveKind.T3, MoveKind.T4, MoveKind.T4), kinds)
        val plain = popped("none")
        val hooked = popped("legs")
        val early = popped("starting")
        assertTrue(plain.move.at(Part.Content, Prop.A, NP_POP - 1L) > plain.move.at(Part.Content, Prop.A, 0))
        assertEquals(hooked.move.at(Part.Content, Prop.A, 0), hooked.move.at(Part.Content, Prop.A, NP_POP - 1L), 0f)
        assertEquals(plain.move.at(Part.Content, Prop.A, plain.move.endAt), hooked.move.at(Part.Content, Prop.A, plain.move.endAt), 0f)
        assertEquals(plain.move.endAt, hooked.move.endAt)
        for (t in listOf(0L, NP_POP - 1L, NP_POP.toLong(), plain.move.endAt)) {
            assertEquals(plain.move.at(Part.Content, Prop.A, t), early.move.at(Part.Content, Prop.A, t), 0f)
        }
    }

    private fun waits(ready: (String) -> Boolean, kind: PageKind = PageKind.Grid, m: PageMove = PageMove(), legs: (Turn<String>) -> Unit = {}) =
        PageStack<String>(MoveForms(), { kind }, move = m, legs = legs, ready = ready) { ends }.also { it.pages.add("hub") }

    private fun legsOf(m: PageMove) = m.legs.map { listOf(it.part, it.prop, it.delay, it.dur) }

    private fun content(m: PageMove): List<MoveLeg> = m.legs.filter { it.part == Part.Content }

    private val held = 1f - (Tok.motion_leave - 17f) / Tok.motion_leave

    @Test
    fun aPageReadyAtTheStartReleasesAtTheStart() {
        for (k in listOf(MoveKind.T3, MoveKind.T4)) {
            val a = waits({ true }, PageKind.Hub)
            val b = PageStack<String>(MoveForms(), { PageKind.Hub }) { ends }.also { it.pages.add("hub") }
            for (x in listOf(a, b)) {
                if (k == MoveKind.T3) {
                    x.push("album", k)
                } else {
                    x.pages.add("album")
                    x.pop(k)
                }
                x.frame(0)
            }
            assertEquals(b.move.endAt, a.move.endAt)
            var t = 0L
            while (t <= b.move.endAt) {
                for (p in Part.entries) for (q in Prop.entries) assertEquals("$k $p $q $t", b.move.at(p, q, t), a.move.at(p, q, t), 1e-4f)
                t += 16
            }
        }
    }

    @Test
    fun aPageNotReadyHoldsTheContentAndClampsTheLeave() {
        assertEquals(133f, Tok.motion_leave)
        val s = waits({ it != "hub" }).also { it.pages.add("album") }
        s.pop(MoveKind.T4)
        s.frame(0)
        s.frame(100)
        assertEquals(0f, s.move.at(Part.Content, Prop.A, 100), 1e-4f)
        s.frame(150)
        assertEquals(0f, s.move.at(Part.Content, Prop.A, 150), 1e-4f)
        assertEquals(held, s.move.at(Part.Leave, Prop.A, 150), 1e-4f)
        s.frame(160)
        assertEquals(held, s.move.at(Part.Leave, Prop.A, 160), 1e-4f)
        assertTrue(s.move.held(Part.Content))
    }

    @Test
    fun theFirstFrameThePageIsReadyReleasesIt() {
        var ok = false
        val s = waits({ ok }, PageKind.Hub)
        val r = PageStack<String>(MoveForms(), { PageKind.Hub }) { ends }.also { it.pages.add("hub") }
        s.push("album", MoveKind.T3)
        r.push("album", MoveKind.T3)
        s.frame(0)
        r.frame(0)
        s.frame(150)
        assertEquals(held, s.move.at(Part.Leave, Prop.A, 150), 1e-4f)
        ok = true
        s.frame(200)
        assertFalse(s.move.held(Part.Content))
        assertEquals(1f - 200f / 217f, s.move.at(Part.Leave, Prop.A, 200), 1e-4f)
        assertEquals(0f, s.move.at(Part.Leave, Prop.A, 217), 1e-4f)
        val first = content(r.move).minOf { it.delay }
        val d = maxOf(0, 183 - first)
        assertTrue(d > 0)
        val moved = content(s.move)
        val base = content(r.move)
        assertEquals(base.size, moved.size)
        for (i in base.indices) {
            assertEquals(base[i].delay + d, moved[i].delay)
            assertEquals(base[i].dur, moved[i].dur)
        }
        val rest = r.move.legs.filter { it.part != Part.Content && it.part != Part.Leave }.maxOf { it.delay + it.dur }
        val end = maxOf(rest, base.maxOf { it.delay + it.dur } + d, 217)
        assertEquals(end.toLong(), s.move.endAt)
        assertTrue(s.move.endAt > r.move.endAt)
    }

    @Test
    fun aPageNeverReadyLandsAtItsEndWithNoRelease() {
        val s = waits({ it != "album" })
        s.push("album", MoveKind.T3)
        s.frame(0)
        val before = legsOf(s.move)
        val end = s.move.endAt
        var t = 16L
        while (t < end) {
            s.frame(t)
            assertEquals(0f, s.move.at(Part.Content, Prop.A, t), 1e-4f)
            assertEquals(Phase.Enter.takeIf { t >= s.move.swapAt } ?: Phase.Exit, s.phase)
            t += 16
        }
        s.frame(t)
        assertEquals("the end frame still draws the move", Phase.Enter, s.phase)
        s.frame(t + 16)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub", "album"), s.pages.toList())
        assertTrue(s.move.held(Part.Content))
        assertEquals(before, legsOf(s.move))
        assertEquals(end, s.move.endAt)
    }

    @Test
    fun aPageReadyOnItsEndFrameLandsWithNoRelease() {
        var ok = false
        val s = waits({ ok })
        s.push("album", MoveKind.T3)
        s.frame(0)
        val before = legsOf(s.move)
        val end = s.move.endAt
        s.frame(200)
        assertEquals(Phase.Enter, s.phase)
        ok = true
        s.frame(end)
        assertEquals(Phase.Enter, s.phase)
        assertEquals(0f, s.move.at(Part.Content, Prop.A, end), 0f)
        s.frame(end + STEP)
        assertEquals(Phase.Rest, s.phase)
        assertTrue(s.move.held(Part.Content))
        assertEquals(before, legsOf(s.move))
    }

    @Test
    fun aWaitingPopRunsTheLegsHookOnTheHeldMove() {
        var ok = false
        val m = PageMove()
        val s = waits({ it != "hub" || ok }, m = m, legs = { m.npPop(200) }).also { it.pages.add("album") }
        s.pop(MoveKind.T4)
        s.frame(0)
        assertTrue(content(m).isNotEmpty())
        for (l in content(m)) assertEquals(200, l.delay)
        s.frame(100)
        ok = true
        s.frame(250)
        assertFalse(m.held(Part.Content))
        for (l in content(m)) assertEquals(maxOf(200, 250 - 17), l.delay)
    }

    @Test
    fun aReverseWhileWaitingReleasesNothing() {
        var ok = false
        var asked = 0
        val s = waits({
            asked++
            ok
        }, PageKind.Hub)
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.frame(100)
        assertTrue(s.move.held(Part.Content))
        val seen = asked
        s.back(pop)
        s.frame(110)
        assertTrue(s.move.reversed)
        val before = legsOf(s.move)
        ok = true
        s.frame(150)
        assertEquals(seen, asked)
        assertEquals(before, legsOf(s.move))
    }

    @Test
    fun aRetargetedPopReleasesWhenThePageItReturnsToIsReady() {
        val s = waits({ it != "album" })
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.frame(200)
        assertTrue(s.move.held(Part.Content))
        assertEquals(0f, s.move.at(Part.Content, Prop.A, 200), 1e-4f)
        s.back(pop)
        s.frame(210)
        assertEquals(MoveKind.T4, s.turn?.kind)
        assertEquals("hub", s.turn?.to)
        assertFalse(s.move.held(Part.Content))
        assertEquals(0f, s.move.at(Part.Leave, Prop.A, 210), 1e-4f)
    }

    private fun albumStack() = PageStack<String>(MoveForms(), { PageKind.Grid }, albumOf = { if (it == "A") ALBUM else -1L }) { ends }.also { it.pages.add("hub") }

    @Test
    fun aTurnWithNoAlbumTakesTheAlbumOfThePagePushedOrPopped() {
        val s = albumStack()
        s.push("A", MoveKind.T3)
        s.frame(0)
        assertEquals(ALBUM, s.turn?.album)
        s.frame(LANDED - STEP)
        s.frame(LANDED)
        assertEquals(Phase.Rest, s.phase)
        assertEquals("A", s.top)
        s.back(pop)
        s.frame(LANDED + 1)
        assertEquals(true, s.turn?.popping)
        assertEquals(ALBUM, s.turn?.album)
        val kept = albumStack()
        kept.push("A", MoveKind.T3, album = 3L)
        kept.frame(0)
        assertEquals(3L, kept.turn?.album)
        val popped = albumStack().also { it.pages.add("A") }
        popped.pop(MoveKind.T4, 3L)
        popped.frame(0)
        assertEquals(3L, popped.turn?.album)
        val plain = PageStack<String>(MoveForms(), { PageKind.Grid }) { ends }.also { it.pages.addAll(listOf("hub", "A")) }
        plain.back(pop)
        plain.frame(0)
        assertEquals(-1L, plain.turn?.album)
        plain.frame(LANDED - STEP)
        plain.frame(LANDED)
        plain.push("A", MoveKind.T3)
        plain.frame(LANDED + 1)
        assertEquals(-1L, plain.turn?.album)
    }

    @Test
    fun aRetargetedPopCarriesTheAlbumAndAReversedPushDropsIt() {
        val s = albumStack()
        s.push("A", MoveKind.T3)
        s.frame(0)
        s.frame(ENTERED)
        assertEquals(Phase.Enter, s.phase)
        s.back(pop)
        s.frame(ENTERED + 1)
        assertEquals(true, s.turn?.retarget)
        assertEquals(true, s.turn?.popping)
        assertEquals(ALBUM, s.turn?.album)
        val r = albumStack()
        r.push("A", MoveKind.T3)
        r.frame(0)
        r.frame(EXITING)
        assertEquals(Phase.Exit, r.phase)
        r.back(pop)
        r.frame(EXITING + 1)
        assertTrue(r.move.reversed)
        assertEquals(-1L, r.turn?.album)
    }

    private fun popper(e: MoveEnds) = PageStack<String>(MoveForms(), { PageKind.Grid }) { e }.also { it.pages.addAll(listOf("hub", "artist")) }

    private fun landsAt(s: PageStack<String>, kind: MoveKind, end: Long) {
        s.pop(kind)
        s.frame(0)
        assertEquals(end, s.move.endAt)
        s.frame(end - 1)
        assertEquals(Phase.Enter, s.phase)
        s.frame(end)
        assertEquals("the end frame still draws the move", Phase.Enter, s.phase)
        s.frame(end + STEP)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub"), s.pages.toList())
    }

    @Test
    fun aT4PivotPopDrawsItsEndFrameAt516AndRestsOnTheNext() =
        landsAt(popper(MoveEnds(morph = true, morphKind = MorphKind.Pivot, morphS0 = 0.5f, morphS1 = 1f, grounds = true)), MoveKind.T4, 516L)

    @Test
    fun aT4mDrawsItsEndFrameAt581AndRestsOnTheNext() = landsAt(popper(MoveEnds(word = true)), MoveKind.T4m, 581L)

    @Test
    fun aT7RestsOnItsEndFrame() {
        val s = stack()
        s.push("np", MoveKind.T7)
        s.frame(0)
        val end = s.move.endAt
        s.frame(end - 1)
        assertEquals(Phase.Enter, s.phase)
        s.frame(end)
        assertEquals(Phase.Rest, s.phase)
        assertEquals("np", s.top)
    }

    @Test
    fun aT8RestsOnItsEndFrame() {
        val s = stack()
        s.pages.add("np")
        s.pop(MoveKind.T8)
        s.frame(0)
        val end = s.move.endAt
        s.frame(end - 1)
        assertEquals(Phase.Enter, s.phase)
        s.frame(end)
        assertEquals(Phase.Rest, s.phase)
        assertEquals("hub", s.top)
    }

    @Test
    fun aBackOnTheEndFrameStartsAPopThatRunsToItsOwnEnd() {
        val s = stack()
        s.push("album", MoveKind.T3)
        s.frame(0)
        s.frame(167)
        s.frame(634)
        assertEquals(Phase.Enter, s.phase)
        s.back(pop)
        s.frame(650)
        assertEquals(true, s.turn?.popping)
        assertEquals(817L, s.move.swapAt)
        assertEquals(Phase.Exit, s.phase)
        val end = s.move.endAt
        s.frame(end)
        assertEquals(Phase.Enter, s.phase)
        s.frame(end + STEP)
        assertEquals(Phase.Rest, s.phase)
        assertEquals(listOf("hub"), s.pages.toList())
    }
}
