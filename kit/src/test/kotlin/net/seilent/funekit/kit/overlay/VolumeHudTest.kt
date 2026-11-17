package net.seilent.funekit.kit.overlay

import net.seilent.funekit.Case
import net.seilent.funekit.Roles
import net.seilent.funekit.Weight
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.overlay.VolumeHud.Act
import net.seilent.funekit.kit.overlay.VolumeHud.Hit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeHudTest {
    private val fade = 282L + 2027L

    private fun shown(at: Long = 0L, pct: Int = 50) = VolumeHud().apply { show(at, pct, true, "artist", "title") }

    @Test
    fun theOpenEndsAt282() {
        val h = shown()
        assertTrue(h.opening(281))
        assertFalse(h.opening(282))
        assertTrue(h.moving(281))
    }

    @Test
    fun theNumberIsHeldTo700AfterTheLaterOfTheOpenEndAndAChangeThenFadesOver520() {
        val h = shown()
        assertEquals(1f, h.numberK(981), 0f)
        assertEquals(1f - Curves.easeIn.at(0.5f), h.numberK(982 + 260), 0.0001f)
        val c = shown()
        c.show(500, 60, true, "artist", "title")
        assertEquals(1f, c.numberK(1100), 0f)
        assertEquals(1f, c.numberK(1199), 0f)
        assertTrue(c.numberK(1200 + 260) < 1f)
    }

    @Test
    fun theRestFadesSlowTo036Over650ThenFastOver104AndTheScrimOutOver267() {
        val h = shown()
        assertEquals(1f, h.rest(fade - 1), 0f)
        assertEquals(0.68f, h.rest(fade + 325), 0.0001f)
        assertEquals(0.36f, h.rest(fade + 650), 0.0001f)
        assertEquals(0f, h.rest(fade + 650 + 104), 0.0001f)
        assertFalse(h.closing(fade + 649))
        assertTrue(h.closing(fade + 650))
        assertTrue(h.visible(fade + 650 + 266))
        assertFalse(h.visible(fade + 650 + 267))
    }

    @Test
    fun theRestTimerWaitsWhileHeldAndRestartsAtTheUp() {
        val h = shown()
        assertEquals(Act.Draw, h.down(100, 120f, 230f))
        assertEquals(1f, h.rest(5000), 0f)
        assertTrue(h.visible(5000))
        h.up(5000, 120f, 230f)
        assertEquals(1f, h.rest(5000 + 2026), 0f)
        assertTrue(h.rest(5000 + 2027 + 325) < 1f)
    }

    @Test
    fun aDownInTheFadeRevivesTheHud() {
        val h = shown()
        val before = h.rest(fade + 300)
        h.down(fade + 300, 120f, 230f)
        assertEquals(before, h.rest(fade + 300), 0.0001f)
        assertEquals(1f, h.rest(fade + 300 + 365), 0f)
        assertFalse(h.closing(fade + 2000))
    }

    @Test
    fun aShowOnAVisibleHudRestartsTheRestTimer() {
        val h = shown()
        h.show(1000, 50, true, "artist", "title")
        assertEquals(1f, h.rest(2500), 0f)
        assertEquals(1f, h.rest(1000 + 2027 - 1), 0f)
        assertTrue(h.rest(1000 + 2027 + 325) < 1f)
    }

    @Test
    fun aTapOnTheExitClosesOver70WithTheScrimOver91LessItsLead() {
        val h = shown()
        h.down(500, 25.2f, 6.6f)
        assertTrue(h.exitLit)
        assertEquals(Act.None, h.up(520, 25.2f, 6.6f))
        assertEquals(0.5f, h.rest(520 + 35), 0.0001f)
        assertTrue(h.closing(520))
        assertTrue(h.visible(520 + 70))
        assertFalse(h.visible(520 + 71))
        assertEquals(0f, h.scrimK(520 + 71), 0.0001f)
    }

    @Test
    fun aDownInTheBottomGestureBandPasses() {
        val h = shown()
        assertEquals(Act.Pass, h.down(500, 120f, 320f - 14f))
        assertEquals(Act.Draw, h.down(500, 120f, 320f - 14.1f))
    }

    @Test
    fun aTouchBegunBeforeTheOpenIsNotOwned() {
        val h = VolumeHud()
        assertEquals(Act.Pass, h.down(0, 120f, 100f))
        h.show(10, 50, true, "artist", "title")
        assertEquals(Act.None, h.move(20, 120f, 40f, 50, VolumeHud.Pos()))
        assertEquals(Act.None, h.up(30, 120f, 40f))
        assertFalse(h.owns)
    }

    @Test
    fun hitsAreBoxesNotCirclesAndTwoBoxesGoToTheNearestCentre() {
        val h = VolumeHud()
        assertEquals(Hit.Plus, h.hit(120f, 56.5f - 31.9f))
        assertEquals(Hit.None, h.hit(120f, 56.5f + 32.1f))
        assertEquals(Hit.Plus, h.hit(120f + 31.9f, 56.5f - 31.9f))
        assertEquals(Hit.Exit, h.hit(25.2f - 26.9f, 6.6f + 26.9f))
        assertEquals(Hit.Prev, h.hit(90f, 159.4f, 0.5f, 1f))
        assertEquals(Hit.Ring, h.hit(100f, 159.4f, 0.5f, 1f))
    }

    @Test
    fun aDownAfterTheFirstTakesTheRestBoxes() {
        val h = shown()
        h.down(fade + 491, 64f, 159.4f)
        assertEquals(Hit.None, h.pressed())
        val g = shown()
        g.down(fade + 491, 63f, 159.4f)
        assertEquals(Hit.Prev, g.pressed())
    }

    @Test
    fun aVolumeDragStepsByTheRoundedStrideAndReAnchorsOnAnOutsideChange() {
        val h = shown()
        h.down(500, 120f, 230f)
        h.move(510, 120f, 200f, 50, VolumeHud.Pos())
        assertEquals(Act.Volume, h.move(520, 120f, 194f, 50, VolumeHud.Pos()))
        assertEquals(51, h.number)
        h.took(51)
        h.move(530, 120f, 193f, 60, VolumeHud.Pos())
        assertEquals(Act.Volume, h.move(540, 120f, 187f, 60, VolumeHud.Pos()))
        assertEquals(61, h.number)
    }

    @Test
    fun aRowDragSeeksOnlyWithATrackPositionAndHoldsItsTargetToTheUp() {
        val off = shown()
        off.down(500, 120f, 159.4f)
        off.move(510, 150f, 159.4f, 50, VolumeHud.Pos())
        assertFalse(off.seeking)
        assertNull(off.pendingSeek())
        val h = shown()
        val pos = VolumeHud.Pos(true, 60000, 200000, 7)
        h.down(500, 120f, 159.4f)
        assertEquals(Act.Draw, h.move(510, 150f, 159.4f, 50, pos))
        assertTrue(h.seeking)
        assertEquals(Act.Draw, h.move(520, 166f, 159.4f, 50, pos))
        assertEquals(80000L, h.pendingSeek())
        assertEquals("1:20", h.readout)
        assertEquals(Act.Seek, h.up(530, 166f, 159.4f))
        assertNull(h.pendingSeek())
        assertEquals(80000L, h.seekTo)
    }

    @Test
    fun thePressIsHeldGoesAtAnActedUpHoldsAnIdleUpAndFadesACancel() {
        val h = shown()
        h.down(500, 120f, 56.5f)
        assertEquals(1f, h.pressK(600), 0f)
        assertEquals(Act.Plus, h.up(600, 120f, 56.5f))
        assertEquals(0f, h.pressK(600), 0f)
        val i = shown()
        i.down(500, 120f, 56.5f)
        i.up(600, 120f, 120f)
        assertEquals(1f, i.pressK(799), 0f)
        assertEquals(0f, i.pressK(800), 0f)
        val c = shown()
        c.down(500, 120f, 56.5f)
        c.cancel(600)
        assertEquals(0.5f, c.pressK(650), 0.0001f)
    }

    @Test
    fun aTapTiltsAtItsLevelAndReturnsSoonerThanAHold() {
        val t = shown()
        t.down(500, 120f, 56.5f)
        assertEquals(1f, t.tiltK(550), 0f)
        t.up(600, 120f, 56.5f)
        assertEquals(0.83f, t.tiltK(699), 0.0001f)
        assertEquals(0.5f * 0.83f, t.tiltK(750), 0.0001f)
        val h = shown()
        h.down(500, 120f, 56.5f)
        h.up(600, 120f, 120f)
        assertEquals(1f, h.tiltK(799), 0f)
        assertEquals(0.5f, h.tiltK(850), 0.0001f)
    }

    @Test
    fun aTrackChangeCrossFadesTheLines() {
        val h = shown()
        assertTrue(h.setTrack(true, "other", "song", 1000))
        val a = h.look(1000)
        assertTrue(a.cross)
        assertEquals(a.text, a.old[0].a, 0.0001f)
        assertEquals(0f, a.line[0].a, 0f)
        assertEquals(a.text, h.look(1000 + 100 + 150).line[0].a, 0.0001f)
        assertFalse(h.moving(1000 + VolumeHud.crossMs() + 3000))
    }

    @Test
    fun aSecondChangeInsideTheInDelayKeepsTheFirstOldLines() {
        val h = shown()
        val artist = h.artist
        val title = h.title
        h.setTrack(true, "b artist", "b title", 1000)
        h.setTrack(true, "c artist", "c title", 1050)
        assertEquals(artist, h.oldArtist)
        assertEquals(title, h.oldTitle)
        assertEquals("c title", h.title)
        h.setTrack(true, "d artist", "d title", 1000 + 100)
        assertEquals("c artist", h.oldArtist)
        assertEquals("c title", h.oldTitle)
    }

    @Test
    fun theTrackRolesTakeTheirTokens() {
        assertEquals(14.5f, Roles.volArtist.size, 0f)
        assertEquals(Weight.Bold, Roles.volArtist.weight)
        assertEquals(Case.Upper, Roles.volArtist.case)
        assertEquals(21.4f, Roles.volTitle.size, 0f)
        assertEquals(Weight.Regular, Roles.volTitle.weight)
        assertEquals(Case.Mixed, Roles.volTitle.case)
        assertEquals("05", VolumeHud.numberText(5))
        assertEquals("1:02:03", VolumeHud.seekText(3723000, 3600000))
    }
}
