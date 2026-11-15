package net.seilent.funekit.kit.quickplay

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.fxU
import net.seilent.funekit.kit.lookOf
import net.seilent.funekit.kit.proj.qpProjection
import net.seilent.funekit.px
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickplayTest {
    private data class Ref(val id: Long, val name: String)

    private val none = Insets(0, 0, 0, 0)
    private val v1 = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Home, false)

    private fun items(n: Int, kind: QpKind = QpKind.Album) = List(n) { QpItem(kind, it.toLong(), "t$it", null) }

    @Test
    fun anEmptyLibraryShowsTheHintAndEmptySections() {
        val l = QpLayout(v1, qpTiles(QpData.EMPTY), 3)
        assertEquals(98.9f, fxU(l.sectionTop(0)), 0.001f)
        assertEquals(98.9f + 11.8f + 2 * 10f + 71.4f, fxU(l.sectionTop(1)), 0.001f)
        assertEquals(98.9f + 11.8f + 2 * 10f + 71.4f + 4.9f + 67.5f, fxU(l.sectionTop(2)), 0.001f)
        assertEquals(98.9f + 11.8f + 2 * 10f + 71.4f + 4.9f + 67.5f + 4.9f + 68.8f - 320f, fxU(l.limit), 0.001f)
    }

    @Test
    fun aWaitingQuickplayChangesTheLookAndKeepsItsSectionTops() {
        val st = QuickplayState()
        val l = QpLayout(v1, qpTiles(QpData.EMPTY), 3)
        val before = st.look(l, 0L)
        st.waiting = true
        assertNotEquals(before, st.look(l, 0L))
        val shown = st.look(l, 0L)
        st.waiting = true
        assertEquals(shown, st.look(l, 0L))
        assertEquals(98.9f, fxU(l.sectionTop(0)), 0.001f)
    }

    @Test
    fun historyAndNewKeepEightTilesInTwoColumns() {
        val q = qpTiles(QpData(null, items(3), items(10, QpKind.Song), items(12)))
        assertEquals(listOf(3, 8, 8), q.counts.toList())
        val l = QpLayout(v1, q, 3)
        val pins = 98.9f
        val history = pins + 4.9f + 2 * 74.1f + 2f + 67.5f
        assertEquals(history, fxU(l.sectionTop(1)), 0.001f)
        val first = 3
        assertEquals(33.7f, fxU(l.tileX(first)), 0.001f)
        assertEquals(33.7f + 74.1f + 2f, fxU(l.tileX(first + 1)), 0.001f)
        assertEquals(history + 4.9f + 74.1f + 2f, fxU(l.tileTop(first + 2)), 0.001f)
        val new = history + 4.9f + 4 * 74.1f + 3 * 2f + 67.5f
        assertEquals(new, fxU(l.sectionTop(2)), 0.001f)
        assertEquals(new + 4.9f + 4 * 74.1f + 3 * 2f + 68.8f - 320f, fxU(l.limit), 0.001f)
    }

    @Test
    fun thePlayingArtSpansBothColumns() {
        val l = QpLayout(v1, qpTiles(QpData(QpItem(QpKind.Song, 1, "now", null), emptyList(), emptyList(), emptyList())), 3)
        assertEquals(150.2f, fxU(l.tileSide(0)), 0.001f)
        assertEquals(98.9f + 2.4f, fxU(l.tileTop(0)), 0.001f)
        assertEquals(98.9f + 2.4f + 150.2f + 52.4f, fxU(l.sectionTop(0)), 0.001f)
    }

    @Test
    fun thePeekShowsThePaneAtThirtyPercentFromTheDesign() {
        val m = qpProjection(0f, 1f)
        assertEquals(6.8f, m.map(33.7f, 0f).x, 0.05f)
        assertEquals(51.9f, m.map(183.9f, 0f).x, 0.05f)
        assertEquals(165.1f, m.map(0f, 0f).y, 0.001f)
        val front = qpProjection(1f, 1f)
        assertEquals(33.7f, front.map(33.7f, 0f).x, 0.001f)
    }

    @Test
    fun theMenuFollowsTheFingerOverTheMenuTravel() {
        val p = QpPan()
        p.begin(100f)
        p.drag(100f + 164.3f / 2, 1f)
        assertEquals(0.5f, p.at(0), 0.001f)
        p.drag(0f, 1f)
        assertEquals(0f, p.at(0), 0f)
        p.end(true, 1000)
        assertEquals(1f, p.at(1000 + 445), 0.001f)
    }

    @Test
    fun aDragThatAWarmHomeLeftEndsOnHome() {
        val p = QpPan()
        p.begin(100f)
        assertTrue(p.active)
        p.drag(100f + 164.3f, 1f)
        p.leave(1000)
        p.drag(0f, 1f)
        p.end(true, 1000)
        assertFalse(p.front)
        assertEquals(0f, p.at(1000 + 445), 0.001f)
    }

    @Test
    fun theLayerBuiltForTheWayBackDrawsNoFocus() {
        val p = QpPan()
        p.show(true, 0)
        assertTrue(p.active)
        p.settle(445)
        p.begin(200f)
        assertFalse(p.active)
        p.end(false, 500)
        assertFalse(p.active)
    }

    private val playing = QpItem(QpKind.Song, 99, "now", null)

    @Test
    fun aPlayingTrackMovesTheFocusAndTheScrollByThePlayingSpan() {
        val st = QuickplayState()
        val plain = QpLayout(v1, qpTiles(QpData(null, items(3), items(4, QpKind.Song), items(4))), 3)
        val now = QpLayout(v1, qpTiles(QpData(playing, items(3), items(4, QpKind.Song), items(4))), 3)
        assertNull(st.build(plain, 0L))
        st.focus = 2
        assertEquals(50f + 2.4f + 150.2f + 52.4f, fxU(st.build(now, fx(50f))!!), 0.001f)
        assertEquals(3, st.focus)
        assertEquals(0L, st.build(plain, fx(50f)))
        assertEquals(2, st.focus)
        assertNull(st.build(plain, fx(50f)))
        assertEquals(2, st.focus)
    }

    @Test
    fun theFirstPlayingTrackLeavesAnEmptyPanesFocus() {
        val st = QuickplayState()
        assertNull(st.build(QpLayout(v1, qpTiles(QpData.EMPTY), 3), 0L))
        assertNull(st.build(QpLayout(v1, qpTiles(QpData(playing, items(2), emptyList(), emptyList())), 3), 0L))
        assertEquals(0, st.focus)
    }

    @Test
    fun aRebuildClampsTheFocusAndDropsTheTapInFlight() {
        val st = QuickplayState()
        val big = QpLayout(v1, qpTiles(QpData(null, items(3), items(8, QpKind.Song), items(8))), 3)
        st.build(big, 0L)
        st.down(10, 0)
        st.build(QpLayout(v1, qpTiles(QpData(null, items(3), emptyList(), emptyList())), 3), 0L)
        assertEquals(2, st.focus)
        assertFalse(st.tap(10, 100))
        st.down(1, 200)
        assertTrue(st.tap(1, 300))
        assertFalse(st.tap(1, 400))
    }

    @Test
    fun theHintWrapsAtTheHintWidth() {
        val w: (String) -> Int = { it.length * 64 }
        assertEquals(listOf("press and", "hold a", "media item"), qpHintLines("press and hold a media item", 10 * 64, w))
    }

    private fun big() = QpLayout(v1, qpTiles(QpData(null, items(3), items(10, QpKind.Song), items(12))), 3)

    @Test
    fun theFocusSpanScrollsIntoViewWithItsSectionHeader() {
        val l = big()
        val side = px(74.1f, v1.stdPpu) / v1.stdPpu
        val history = 98.9f + 4.9f + 2 * 74.1f + 2f + 67.5f
        val new = history + 4.9f + 4 * 74.1f + 3 * 2f + 67.5f
        val asc = fx(30f)
        assertEquals(fx(123f), l.visible(-1, fx(123f), asc))
        assertEquals(0L, l.visible(0, fx(200f), asc))
        assertEquals(history - 30f, fxU(l.visible(3, fx(400f), asc)), 0.001f)
        assertEquals(history + 4.9f + side + 2f - 320f, fxU(l.visible(4, 0L, asc)), 0.001f)
        assertEquals(history + 4.9f + 3 * (74.1f + 2f) - 2f, fxU(l.visible(9, fx(600f), asc)), 0.001f)
        assertEquals(new + 4.9f + 3 * (74.1f + 2f) + side + 2f - 320f, fxU(l.visible(18, 0L, asc)), 0.001f)
    }

    @Test
    fun theFocusSpanPadsByTheBorderRoundedToStandardPx() {
        val s = scale(Window(640, 1280, 35, none, none, none, none, none), Nav.None, Surface.Home, false)
        assertEquals(5, px(2f, s.stdPpu))
        val l = QpLayout(s, qpTiles(QpData(null, items(3), items(10, QpKind.Song), items(12))), 3)
        val border = 1.875f
        val side = 74.25f
        val history = 98.9f + 4.9f + 2 * 74.1f + 2f + 67.5f
        val new = history + 4.9f + 4 * 74.1f + 3 * 2f + 67.5f
        val asc = fx(30f)
        assertEquals(history + 4.9f + 3 * (74.1f + 2f) - border, fxU(l.visible(9, fx(600f), asc)), 0.001f)
        assertEquals(new + 4.9f + 3 * (74.1f + 2f) + side + border - 480f, fxU(l.visible(18, 0L, asc)), 0.001f)
    }

    @Test
    fun aFocusScrolledOutMovesToTheNearestTileInViewInItsColumnFirst() {
        val l = big()
        assertEquals(0, l.clamp(0, 0L))
        assertEquals(3, l.clamp(0, fx(200f)))
        assertEquals(-1, l.clamp(-1, 0L))
        val pins = QpLayout(v1, qpTiles(QpData(null, items(3), emptyList(), emptyList())), 3)
        assertEquals(1, pins.clamp(1, fx(100f)))
        assertEquals(2, pins.clamp(1, fx(140f)))
    }

    @Test
    fun aRebuildStopsTheFocusScroll() {
        val st = QuickplayState()
        val l = big()
        st.build(l, 0L)
        st.focus = 9
        st.into(l, 0L)
        assertTrue(st.aiming)
        st.build(l, 0L)
        assertFalse(st.aiming)
        assertNull(st.landAim())
    }

    private val a105 = scale(Window(720, 1280, 35, none, none, none, none, none), Nav.None, Surface.Home, false)
    private val r1 = scale(Window(480, 800, 35, none, none, none, none, none), Nav.None, Surface.Home, false)

    @Test
    fun tileTopsRoundAsFuneOsFixedPoint() {
        val l = QpLayout(a105, qpTiles(QpData(null, emptyList(), emptyList(), items(4))), 3)
        assertEquals(1066, l.rect(2, 0L).y0)
        assertEquals(823, l.pxOf(l.sectionTop(2)))
    }

    private fun scrolled(s: Scale, focus: Int): Pair<QpLayout, Long> {
        val l = QpLayout(s, qpTiles(QpData(null, items(9), items(10, QpKind.Song), items(12))), 3)
        var aim = 0L
        for (f in 1..focus) aim = l.visible(f, aim, l.headerAsc())
        return l to aim
    }

    @Test
    fun aScrolledPaneRoundsEachRowOnceAgainstTheOffset() {
        val (a, at) = scrolled(a105, 20)
        assertEquals(150, a.rect(11, at).y1)
        assertEquals(156, a.rect(13, at).y0)
        val (r, rt) = scrolled(r1, 20)
        assertEquals(46, r.rect(11, rt).y1)
        assertEquals(51, r.rect(13, rt).y0)
    }

    @Test
    fun theOffsetKeepsTheFractionalRestWhileTheListSitsOnItsPx() {
        val st = QuickplayState()
        val (l, aim) = scrolled(a105, 20)
        assertEquals(l.ofPx(0f), st.pos(0, 0f, false, l))
        st.rest(aim, l.pxOf(aim))
        assertEquals(aim, st.pos(l.pxOf(aim), 0f, false, l))
        assertEquals(l.ofPx(l.pxOf(aim).toFloat()), st.pos(l.pxOf(aim), 0f, true, l))
        assertEquals(l.ofPx(l.pxOf(aim) + 2f), st.pos(l.pxOf(aim), 2f, false, l))
        assertEquals(l.ofPx(l.pxOf(aim) + 1f), st.pos(l.pxOf(aim) + 1, 0f, false, l))
    }

    @Test
    fun aCoverThatFailsToDecodeDrawsTheBoxTile() {
        val st = QuickplayState()
        val bad = Ref(1, "cover")
        val loading = Ref(2, "cover")
        val l = QpLayout(v1, qpTiles(QpData(null, listOf(QpItem(QpKind.Album, 1, "a", bad), QpItem(QpKind.Album, 2, "b", loading)), emptyList(), emptyList())), 3)
        val side = l.rect(0, 0L).w
        assertTrue(st.boxed(null, side))
        assertFalse(st.boxed(bad, side))
        st.putArt(bad, side, null)
        assertTrue(st.boxed(bad, side))
        assertTrue(st.tried(bad, side))
        assertNull(st.art(bad, side))
        assertFalse(st.boxed(loading, side))
        assertFalse(st.tried(loading, side))
        st.keep(QpLayout(v1, qpTiles(QpData(null, listOf(QpItem(QpKind.Album, 2, "b", loading)), emptyList(), emptyList())), 3))
        assertFalse(st.boxed(bad, side))
    }

    @Test
    fun sizeLargeTakesTheLargeHintBaseAndStepAndKeptCaptions() {
        val large = scale(Window(240, 320, 35, none, none, none, none, none), Nav.None, Surface.Home, true)
        val l = QpLayout(large, qpTiles(QpData.EMPTY), 3)
        assertEquals(98.9f + 13.44f + 2 * 12f + 71.4f, fxU(l.sectionTop(1)), 0.001f)
        assertEquals(98.9f + 13.44f + 12f, fxU(l.hintTop(1)), 0.001f)
        assertEquals(1163919 / 65536f, l.caption.size, 0f)
        val std = QpLayout(v1, qpTiles(QpData.EMPTY), 3)
        assertEquals(98.9f + 11.8f + 10f, fxU(std.hintTop(1)), 0.001f)
        assertTrue(std.caption === Roles.boxCaption)
    }

    @Test
    fun anAskedOffsetDrawsUntilTheListSitsOnItsPx() {
        val l = QpLayout(v1, qpTiles(QpData(null, items(9), emptyList(), emptyList())), 3)
        val st = QuickplayState()
        val pos = fx(50.25f)
        st.ask(pos, l.pxOf(pos))
        assertEquals(pos, st.pos(0, 0f, false, l))
        assertEquals(pos, st.pos(l.pxOf(pos), 0f, false, l))
        assertEquals(l.ofPx(0f), st.pos(0, 0f, false, l))
        st.ask(pos, l.pxOf(pos))
        assertEquals(l.ofPx(7f), st.pos(7, 0f, true, l))
        assertEquals(l.ofPx(7f), st.pos(7, 0f, false, l))
        st.ask(pos, l.pxOf(pos))
        st.forget()
        assertEquals(l.ofPx(0f), st.pos(0, 0f, false, l))
        assertEquals(l.ofPx(l.pxOf(pos).toFloat()), st.pos(l.pxOf(pos), 0f, false, l))
    }

    @Test
    fun aKeyMoveOrSelectSetsTheKeyFocusAndATouchDownOrADropClearsIt() {
        val l = QpLayout(v1, qpTiles(QpData(null, items(4), emptyList(), emptyList())), 3)
        val st = QuickplayState()
        assertFalse(st.keyFocus)
        val start = st.look(l, 0L)
        assertFalse(start.key)
        st.keyMove(1, l)
        assertTrue(st.keyFocus)
        assertEquals(1, st.focus)
        assertTrue(st.look(l, 0L).key)
        assertNotEquals(start, st.look(l, 0L))
        st.keyMove(-5, l)
        assertEquals(0, st.focus)
        st.keyMove(10, l)
        assertEquals(3, st.focus)
        st.down(2, 0L)
        assertFalse(st.keyFocus)
        assertEquals(2, st.focus)
        assertFalse(st.look(l, 0L).key)
        st.keySelect()
        assertTrue(st.keyFocus)
        assertEquals(2, st.focus)
        st.dropKeyFocus()
        assertFalse(st.keyFocus)
    }

    @Test
    fun aHiddenFocusKeepsTheFocusIndexAndMarksTheLookHidden() {
        val l = QpLayout(v1, qpTiles(QpData(null, items(4), emptyList(), emptyList())), 3)
        val st = QuickplayState()
        st.focus = 2
        val shown = st.look(l, 0L)
        val hidden = st.look(l, 0L, hideFocus = true)
        assertTrue(shown.shown)
        assertFalse(hidden.shown)
        assertEquals(shown.focus, hidden.focus)
        assertNotEquals(shown, hidden)
        assertEquals(shown, st.look(l, 0L, hideFocus = false))
    }

    @Test
    fun everyMutatorChangesTheLookWithNoFrameWrite() {
        val cover = Ref(1, "cover")
        val l = QpLayout(v1, qpTiles(QpData(QpItem(QpKind.Song, 1, "now", cover), items(4), emptyList(), emptyList())), 3)
        val side = l.rect(0, 0L).w
        val st = QuickplayState()
        val frame = mutableLongStateOf(1000L)
        val covered = mutableStateOf(false)
        Snapshot.sendApplyNotifications()
        val look = lookOf(covered) { st.look(l, frame.longValue) }
        fun changes(name: String, step: () -> Unit) {
            val before = look.value
            step()
            assertNotEquals(name, before, look.value)
            assertEquals(name, st.look(l, frame.longValue), look.value)
        }
        changes("build") { st.build(l, 0L) }
        changes("putArt") { st.putArt(cover, side, null) }
        changes("keep") { st.keep(l) }
        changes("reload") { st.reload() }
        changes("focus") { st.focus = 2 }
        changes("down") { st.down(0, frame.longValue) }
        changes("tap") { st.tap(0, frame.longValue) }
        changes("cancel") { st.cancel(frame.longValue) }
        changes("rest") { st.rest(fx(10f), 3) }
        changes("ask") { st.ask(fx(20f), 6) }
        changes("forget") { st.forget() }
        val still = look.value
        val f = st.focus
        st.focus = f
        assertEquals(still, look.value)
    }

    @Test
    fun theT6MenuKeepsTheHomeRingAtTheQuickplayAlphaAndFadesTheWatermark() {
        for (t in listOf(0f, 0.5f, 1f)) {
            val l = qpMenuLook(t)
            val am = 1 + t * (Tok.motion_quickplayAlpha - 1)
            assertEquals(am, l.ring, 0.0001f)
            assertEquals((1 - t) * am, l.mark, 0.0001f)
        }
    }

    @Test
    fun aPausedPlayingTileTakesThePausedHeader() {
        val playing = QpItem(QpKind.Song, 7L, "Slow Comet", null)
        assertFalse(qpTiles(QpData(playing, emptyList(), emptyList(), emptyList())).paused)
        assertTrue(qpTiles(QpData(playing, emptyList(), emptyList(), emptyList(), paused = true)).paused)
        assertFalse(qpTiles(QpData(null, emptyList(), emptyList(), emptyList(), paused = true)).paused)
        assertEquals("Paused", QP_PAUSED)
    }
}
