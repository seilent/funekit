package net.seilent.funekit.screens.music.sheet

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.api.Listing
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.screens.host.MusicEntry
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.album.AlbumModel
import net.seilent.funekit.screens.music.artist.ARTIST_ALBUMS
import net.seilent.funekit.screens.music.artist.ARTIST_SONGS
import net.seilent.funekit.screens.music.artist.ArtistModel
import net.seilent.funekit.screens.music.artist.RING_ADD
import net.seilent.funekit.screens.music.artist.RING_PLAY
import net.seilent.funekit.screens.music.artist.RING_SHUFFLE
import net.seilent.funekit.screens.music.artist.artistAdd
import net.seilent.funekit.screens.music.artist.artistPlay
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Sheet
import net.seilent.funekit.sheet.Tile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class S4MotionSheetTest {
    private val sc = MusicScenes(FakeSet.Demo)

    private class Shot(val kind: String, val at: Long, val quiet: String? = null, val rules: List<Pair<String, String>> = emptyList(), val funeos: Boolean = false)

    private class Look(val nav: Nav = Nav.None, val large: Boolean = false, val accentOff: Boolean = false)

    private fun scene(dev: Dev, look: Look, shots: List<Shot>, note: String, setup: (S4Rig) -> MutableList<Pair<Long, () -> Unit>>): List<Tile> {
        val rig = S4Rig(sc, dev, look.nav, look.large, look.accentOff)
        val acts = setup(rig)
        val out = ArrayList<Tile>()
        for (shot in shots.sortedBy { it.at }) {
            rig.run(shot.at, acts)
            val kind = (if (look.large) "large-" else "") + shot.kind
            if (rig.st.overlay != null) continue
            val rest = rig.st.phase == Phase.Rest
            val rec = sc.regions(dev, rig.s, shot.rules).also {
                it.residual = rest && shot.quiet == null
                it.residualWhy = shot.quiet ?: if (it.residual) PAGE_LOUD else MOVE_QUIET
            }
            val turn = rig.st.turn?.kind?.let { if (rest) "" else ", in its $it at ${shot.at - (rig.st.turn?.at ?: shot.at)} ms" } ?: ""
            out += sc.tile(FOLDER, dev, kind, rig.s, listOf("$note; shot at ${shot.at}$turn"), rec) {
                with(rig) {
                    world(shot.at, rec)
                    if (shot.funeos) funeosYear(rec)
                }
            }
        }
        return out
    }

    private fun acts(vararg a: Pair<Long, () -> Unit>): MutableList<Pair<Long, () -> Unit>> = a.toMutableList()

    private fun header(r: S4Rig, acts: MutableList<Pair<Long, () -> Unit>>) {
        acts += HEADER_TAP to { r.headerDown() }
        acts += HEADER_TAP + X_SHOT to { r.headerUp(HEADER_TAP + X_SHOT) }
    }

    private fun top(rig: S4Rig): MusicEntry = rig.st.top!!

    private fun artistTo(rig: S4Rig, by: (S4Rig) -> Unit = { it.hubKnob(1, 0, KNOB) }, select: Long = SELECT): MutableList<Pair<Long, () -> Unit>> {
        rig.start(HUB_ARTISTS)
        return acts(KNOB to { by(rig) }, select to { rig.hubSelect(select) })
    }

    private fun albumTo(rig: S4Rig): MutableList<Pair<Long, () -> Unit>> {
        rig.start(HUB_ALBUMS)
        return acts(KNOB to { rig.hubKnob(0, 1, KNOB) }, SELECT to { rig.hubSelect(SELECT) })
    }

    private val muka: (S4Rig) -> Unit = { it.hubPickTitle(MUKA, KNOB, top = false) }

    private fun series(name: String, ms: List<Int>, from: Long, back: Boolean = false, rules: (Int) -> List<Pair<String, String>> = { emptyList() }, funeos: (Int) -> Boolean = { false }): List<Shot> =
        ms.map { Shot("$name-$it${if (back) "-back" else ""}-accentoff", from + it, rules = rules(it), funeos = funeos(it)) }

    private fun v1T3Rules(ms: Int): List<Pair<String, String>> = when (ms) {
        17, 100 -> listOf(V1_STRIPS to STRIP_AA)
        417, 433 -> listOf(FUNEOS to FUNEOS_YEAR)
        else -> emptyList()
    }

    private fun t4mRules(ms: Int): List<Pair<String, String>> = if (ms == T4M_FAINT) listOf(ARRIVE_FOCUS to FAINT_FOCUS) else emptyList()

    private fun navT4Rules(d: Dev, ms: Int): List<Pair<String, String>> = when {
        d == sc.a105 && ms >= NAV_T4_REST -> listOf(HUB_THUMB_END to ARTIST_ROWS)
        d == sc.a105 && ms >= T4_THUMB -> listOf(ARRIVE_THUMB to ARTIST_ROWS)
        d == sc.r1 && ms == NAV_T4_R1_GROUND -> listOf(ARRIVE_DIVIDER to R1_GROUND)
        d == sc.r1 && ms == NAV_T4_R1_FADE -> listOf(LEAVE_RING0 to LEAVE_FADE, LEAVE_GLYPH0 to LEAVE_FADE)
        else -> emptyList()
    }

    private fun t4Rules(d: Dev, ms: Int): List<Pair<String, String>> = if (d == sc.a105 && ms >= T4_THUMB) listOf(ARRIVE_THUMB to ARTIST_ROWS) else emptyList()

    private fun t5Rules(d: Dev, ms: Int): List<Pair<String, String>> = if (d == sc.a105 && ms == T5_LEAD_SHOT) listOf(PAGE_THUMB to T5_LEAD) else emptyList()

    private fun write(name: String, line: String, tiles: List<Tile>) {
        if (tiles.isNotEmpty()) sc.render.write(Sheet(FOLDER, name, listOf(line), tiles))
    }

    @Test
    fun funeOsS4Moves() {
        val a = sc.a105
        val off = Look(accentOff = true)
        write("t3", "T3 from the hub ARTISTS row to the artist page with the thumb flight (funeOS t3, knob 1, select at 2920).",
            scene(a, off, series("t3", listOf(100, 233, 333, 383), SELECT) + Shot("t3-$T3_REST-accentoff", SELECT + T3_REST, rules = listOf(FUNEOS to FUNEOS_YEAR), funeos = true), FIRST_ARTIST) { artistTo(it) } +
                scene(sc.r1, off, series("t3", listOf(100), SELECT), FIRST_ARTIST) { artistTo(it) } +
                scene(sc.v1, off, series("t3", listOf(0, 17, 100, 167, 283, 417, 433), SELECT, rules = ::v1T3Rules, funeos = { it >= V1_T3_ARRIVED }), FIRST_ARTIST) { artistTo(it) })
        write("t4", "T4 from the artist page back to the hub (funeOS t4, back at 4920).",
            scene(a, off, series("t4", listOf(50, 183, 250, 333, 517), BACK, rules = { t4Rules(a, it) }), FIRST_ARTIST) { r -> artistTo(r).also { it += BACK to { r.back() } } })
        fun t4(d: Dev) = listOf(100, 200, 400).map { Shot("t4-$it", BACK + it, rules = t4Rules(d, it)) }
        write("t4-static", "T4 from the artist page back to the hub at 100, 200 and 400 ms.",
            listOf(sc.r1, a, sc.v1).flatMap { d -> scene(d, Look(), t4(d), FIRST_ARTIST) { r -> artistTo(r).also { it += BACK to { r.back() } } } })
        fun navT4(d: Dev) = (0..NAV_T4_LAST step NAV_T4_STEP).map { Shot("navbar-t4-$it", BACK + it, rules = navT4Rules(d, it)) }
        write("navbar-t4", "T4 back to the hub with NAV BAR ON on a Buttons window.",
            listOf(sc.r1, a).flatMap { d -> scene(d, Look(nav = Nav.Buttons), navT4(d), FIRST_ARTIST) { r -> artistTo(r).also { it += BACK to { r.back() } } } })
        write("t3m", "T3m from the hub ALBUMS cell to album detail (funeOS t3m).",
            scene(a, off, series("t3m", listOf(100, 300, 550, 633, 700, 767), SELECT), FIRST_ALBUM) { albumTo(it) })
        write("t4m", "T4m from album detail back to the ALBUMS grid (funeOS t4m and t4mtile).",
            scene(a, off, series("t4m", listOf(67, 183, 350, 467, 583), BACK, rules = ::t4mRules), FIRST_ALBUM) { r -> albumTo(r).also { it += BACK to { r.back() } } } +
                scene(a, off, series("t4mtile", listOf(67, 183, 350, 467, 583), BACK, rules = ::t4mRules), FIRST_ALBUM + TILE_BACK) { r -> albumTo(r).also { it += BACK to { r.back() } } })
        fun col(r: S4Rig): MutableList<Pair<Long, () -> Unit>> {
            r.start(HUB_ALBUMS)
            return acts(KNOB to { r.openJump() }, PICK to { r.pickJump() })
        }
        write("t3c", "T3c from the ALBUMS jump pick to the column page and back (funeOS t3c).",
            scene(a, off, series("t3c", listOf(100, 300, 500, 550, 617), PICK), COL_NOTE) { col(it) } +
                scene(a, off, series("t3c", listOf(100, 300, 350, 383, 467), PICK + COL_BACK, true), COL_NOTE) { r -> col(r).also { it += PICK + COL_BACK to { r.back() } } })
        fun colAlbum(r: S4Rig) = col(r).also {
            it += COL_KNOB to { r.colKnob(COL_KNOB) }
            it += COL_SELECT to { r.pageSelect(COL_SELECT) }
        }
        write("t3a", "T3a from the column entry to album detail and T4a back (funeOS t3a).",
            scene(a, off, series("t3a", listOf(83, 200, 300, 450, 550), COL_SELECT), COL_NOTE) { colAlbum(it) } +
                scene(a, off, series("t3a", listOf(33, 67, 150, 250, 283, 300), COL_SELECT + PAGE_BACK, true), COL_NOTE) { r -> colAlbum(r).also { it += COL_SELECT + PAGE_BACK to { r.back() } } })
        fun artistAlbum(r: S4Rig, by: (S4Rig) -> Unit = { it.hubKnob(1, 0, KNOB) }, select: Long = SELECT) = artistTo(r, by, select).also {
            repeat(ROW_KNOBS) { i -> it += (ART_KNOB + i * SHEET_KNOB) to { r.artistKnob(1, ART_KNOB + i * SHEET_KNOB) } }
            it += ART_SELECT to { r.pageSelect(ART_SELECT) }
        }
        write("t3aartist", "T3a from the artist ALBUMS row to album detail and T4a back (funeOS t3aartist).",
            scene(a, off, series("t3aartist", listOf(83, 200, 450, 550, 617), ART_SELECT), FIRST_ARTIST) { artistAlbum(it, select = ARTIST_SELECT) } +
                scene(a, off, series("t3aartist", listOf(67, 167, 250, 350, 500), ART_BACK, true), FIRST_ARTIST) { r -> artistAlbum(r, select = ARTIST_SELECT).also { it += ART_BACK to { r.back() } } })
        val coverless: (S4Rig) -> Unit = { it.hubPickTitle(TERRACE_ARTIST, KNOB) }
        write("t3zartist", "T3z from the artist's coverless album row to album detail and T4z back (funeOS t3zartist).",
            scene(a, off, series("t3zartist", listOf(33, 100, 200, 300, 417), ART_SELECT), TERRACE_NOTE) { artistAlbum(it, coverless) } +
                scene(a, off, series("t3zartist", listOf(50, 83, 133, 217, 300, 317), ART_BACK, true), TERRACE_NOTE) { r -> artistAlbum(r, coverless).also { it += ART_BACK to { r.back() } } })
    }

    @Test
    fun funeOsS4PageMoves() {
        val boards = listOf(sc.r1, sc.a105)
        fun early(d: Dev) = listOf(Shot("artist-150", SELECT + 150, rules = if (d == sc.v1) listOf(V1_STRIPS to STRIP_AA) else emptyList()))
        val late = listOf(Shot("artist-450", SELECT + 450))
        write("artist-mid", "The artist page 150 and 450 ms into its T3 from the hub (funeOS artist:150 and artist:450).",
            (boards + sc.v1).flatMap { d -> scene(d, Look(), early(d), FIRST_ARTIST) { artistTo(it) } + scene(d, Look(), late, MUKA_NOTE) { artistTo(it, muka) } } +
                scene(sc.v1, Look(large = true), late, MUKA_NOTE) { artistTo(it, muka) })
        fun t5(d: Dev) = listOf(150, 500).map { Shot("t5artist-$it", SWITCH + it, rules = t5Rules(d, it)) }
        write("t5artist", "T5 on the artist page from ALBUMS to SONGS (funeOS t5artist).",
            boards.flatMap { d -> scene(d, Look(), t5(d), MUKA_NOTE) { r -> artistTo(r, muka).also { it += SWITCH to { r.switchArtist(ARTIST_SONGS, SWITCH) } } } })
        write("albumx", "A tap on the albums header pops album detail with T4m (funeOS albumx:500).",
            boards.flatMap { d -> scene(d, Look(), listOf(Shot("albumx-500", HEADER_TAP + X_SHOT + 500)), FIRST_ALBUM) { r -> albumTo(r).also { header(r, it) } } })
        write("artistx", "A tap on the artists header pops the artist page with T4 (funeOS artistx:150 and :500).",
            boards.flatMap { d -> scene(d, Look(), listOf(150, 500).map { Shot("artistx-$it", HEADER_TAP + X_SHOT + it, rules = t4Rules(d, it)) }, FIRST_ARTIST) { r -> artistTo(r).also { header(r, it) } } })
        write("added", "The add ring selected on the artist page and its add toast (funeOS added:500).",
            boards.flatMap { d ->
                scene(d, Look(), listOf(Shot("added-500", HEADER_TAP + ADD_SELECT + 500)), MUKA_NOTE) { r ->
                    artistTo(r, muka).also {
                        it += HEADER_TAP to { r.artistKnob(1, HEADER_TAP) }
                        it += HEADER_TAP + ADD_SELECT to {
                            val m = r.model(top(r)) as ArtistModel
                            check(m.ring == RING_ADD)
                            val words = requireNotNull(runBlocking { artistAdd(sc.fake.player, m) }) { "premise: the add ring adds songs" }
                            r.toast = sc.sceneToast(words, m.name.orEmpty(), r.s, HEADER_TAP + ADD_SELECT, false)
                        }
                    }
                }
            })
    }

    private fun pressTiles(
        dev: Dev,
        look: Look,
        name: String,
        at: Long,
        rest: Boolean,
        note: String,
        setup: (S4Rig) -> MutableList<Pair<Long, () -> Unit>>,
        down: (S4Rig) -> Unit,
        lift: (S4Rig, Long) -> Unit,
    ): List<Tile> {
        val restShot = if (rest) scene(dev, look, listOf(Shot("$name-rest", at)), note, setup) else emptyList()
        val held = scene(dev, look, listOf(Shot("$name-down", at + DOWN_SHOT), Shot("$name-held", at + HELD_SHOT)), note) { r -> setup(r).also { it += at to { down(r) } } }
        fun lifted(shot: Shot, upAt: Long) = scene(dev, look, listOf(shot), "$note; the up at $upAt") { r ->
            setup(r).also {
                it += at to { down(r) }
                it += upAt to { lift(r, upAt) }
            }
        }
        return restShot + held + lifted(Shot("$name-tap", at + TAP_SHOT), at + TAP_UP) + lifted(Shot("$name-up", at + if (rest) RING_UP_SHOT else ROW_UP_SHOT), at + UP_UP)
    }

    @Test
    fun funeOsS4Presses() {
        val boards = listOf(sc.r1 to Look(), sc.a105 to Look(), sc.v1 to Look(large = true))
        val at = SELECT + PRESS_WAIT
        write("rowpress-album", "A press on album row 0 (funeOS rowpress:0:album); its tap and up open now playing with T7 (play open, Page form).",
            boards.flatMap { (d, l) ->
                pressTiles(d, l, "rowpress-0-album", at, false, FIRST_ALBUM, ::albumTo, { r ->
                    val m = r.model(top(r)) as AlbumModel
                    val g = m.geo!!
                    m.focus.set(0, m.count, at, true)
                    if (g.top + g.pitch / 2 - m.track.q < g.bottom) m.onRings = false
                    m.press.down(0, at)
                    m.glow.arm(0, at)
                }) { r, t ->
                    val m = r.model(top(r)) as AlbumModel
                    m.press.tap(t)
                    r.play(Listing.AlbumTracks(m.id), m.gen, 0, t)
                }
            })
        val songsAt = at + SONGS_WAIT
        fun songs(r: S4Rig) = artistTo(r, muka).also { it += at to { (r.model(top(r)) as ArtistModel).pivot = ARTIST_SONGS } }
        write("rowpress-artistsongs", "A press on the artist SONGS row 1 (funeOS rowpress:0:artistsongs); its tap and up open now playing with T7.",
            boards.flatMap { (d, l) ->
                pressTiles(d, l, "rowpress-0-artistsongs", songsAt, false, MUKA_NOTE, ::songs, { r ->
                    val m = r.model(top(r)) as ArtistModel
                    val pn = m.panes[ARTIST_SONGS]
                    m.listDown()
                    pn.focus.set(1, pn.count, songsAt, true)
                    pn.press.down(1, songsAt)
                    pn.glow.arm(1, songsAt)
                }) { r, t ->
                    val m = r.model(top(r)) as ArtistModel
                    m.panes[ARTIST_SONGS].press.tap(t)
                    r.play(Listing.ArtistTracks(m.id), m.gen, 1, t)
                }
            })
        write("rowpress-artistart", "A press on the artist ALBUMS row 0 tile (funeOS rowpress:0:artistart); its tap and up push album detail with T3a.",
            boards.take(2).flatMap { (d, l) ->
                pressTiles(d, l, "rowpress-0-artistart", at, false, MUKA_NOTE, { artistTo(it, muka) }, { r ->
                    val m = r.model(top(r)) as ArtistModel
                    val pn = m.panes[ARTIST_ALBUMS]
                    m.listDown()
                    pn.focus.set(0, pn.count, at, true)
                    pn.press.down(0, at)
                    pn.glow.arm(0, at)
                }) { r, t ->
                    (r.model(top(r)) as ArtistModel).panes[ARTIST_ALBUMS].press.tap(t)
                    r.pageSelect(t)
                }
            })
        for ((name, ring) in listOf("artist0" to RING_PLAY, "artist2" to RING_SHUFFLE)) {
            write("ringpress-$name", "A press on the artist page ring $ring (funeOS ringpress:0:$name); its tap and up open now playing with T7.",
                boards.flatMap { (d, l) ->
                    pressTiles(d, l, "ringpress-0-$name", at, true, MUKA_NOTE, { artistTo(it, muka) }, { r -> (r.model(top(r)) as ArtistModel).ringDown(ring, at) }) { r, t ->
                        val m = r.model(top(r)) as ArtistModel
                        m.rings.tap(t)
                        r.playBy(t) { artistPlay(it, m, ring) }
                    }
                })
        }
    }

    companion object {
        private const val FOLDER = "musics4"
        private const val KNOB = 2600L
        private const val SELECT = 2920L
        private const val ARTIST_SELECT = 2800L
        private const val BACK = 4920L
        private const val PICK = 3200L
        private const val COL_BACK = 1600L
        private const val COL_KNOB = 4800L
        private const val COL_SELECT = 5000L
        private const val PAGE_BACK = 2000L
        private const val ART_KNOB = 4800L
        private const val ART_SELECT = 5200L
        private const val ART_BACK = 7200L
        private const val ROW_KNOBS = 3
        private const val SWITCH = 4120L
        private const val HEADER_TAP = 4120L
        private const val X_SHOT = 50L
        private const val ADD_SELECT = 200L
        private const val PRESS_WAIT = 1000L
        private const val SONGS_WAIT = 400L
        private const val DOWN_SHOT = 50L
        private const val HELD_SHOT = 150L
        private const val TAP_SHOT = 200L
        private const val TAP_UP = 50L
        private const val UP_UP = 150L
        private const val ROW_UP_SHOT = 650L
        private const val RING_UP_SHOT = 400L
        private const val TILE_BACK = ", popped by the device back where funeOS t4mtile takes KnobDouble"
        private const val NAV_T4_LAST = 880
        private const val NAV_T4_STEP = 40
        private const val NAV_T4_REST = 560
        private const val NAV_T4_R1_GROUND = 360
        private const val V1_STRIPS = "leave.row0.tile."
        private const val T4M_FAINT = 350
        private const val T3_REST = 450
        private const val V1_T3_ARRIVED = 417
        private const val FUNEOS = "funeos."
        private const val FUNEOS_YEAR = "where funeOS draws its first album row's year: its first # artist 81 Mali has a two line first album (The Xx Yy, year at 756 to 777 on A105) and the position stand in 22 Taal a one line one, so funeOS's year line sits one title pitch under ours"
        private const val ARRIVE_FOCUS = "arrive.row0.cell1.focus"
        private const val FAINT_FOCUS = "the arriving grid is at alpha 0.0086 here: the focus ring reads 19 over the 17 ground in the kit and 18 over 17 in funeOS, a contrast of 1 level, under the half contrast"
        private const val HUB_THUMB_END = "page.thumb.y472"
        private const val ARRIVE_DIVIDER = "arrive.row0.tile"
        private const val STRIP_AA = "the kit draws the leaving layer at a fractional scale and offset, so the V1 1 px divider border spreads over 2 px (t3-17 at x 40: 46 and 80 levels where funeOS keeps one px at 107; t3-100: 57 and 35 against 66) and its peak falls under funeOS's"
        private const val ARTIST_ROWS = "the Demo ARTISTS list holds 73 rows against funeOS's 75, so at the 165 px pitch the thumb is 140 px against funeOS's 138 at the 920 px view and 125 against 123 at the 812 px NAV BAR ON view (funeOS player/ui/scrollbar.cpp thumb_of :79-88, v0.4.4), and its last part ends 2 px lower"
        private const val ARRIVE_THUMB = "arrive.thumb."
        private const val T4_THUMB = 333
        private const val R1_GROUND = "the R1 reference is RGB565: its accent ground steps from 41 to 33 at y 316 where the kit's is a gradient at 38 and 37, so against the color.bg reference of the arriving # tile (border alpha 0.146, half contrast 17.4 levels) the measured ground masks every box row in the kit and 76 of 92 in funeOS, while the border reads 65 over 38 here and 66 over 41 there"
        private const val NAV_T4_R1_FADE = 160
        private const val T5_LEAD_SHOT = 150
        private const val PAGE_THUMB = "page.thumb."
        private const val T5_LEAD = "the A105 starts this T5 at its first shown frame, 8 ms after the switch at 4120, when the pivot strip slot is not yet whole on its GPU (funeOS player/ui/app.cpp App::pivot_lead :35767-35801, v0.4.4), so funeOS's thumb fade reads 2 levels over the ground against the kit's 5, under the half contrast; R1 draws no such lead"
        private const val LEAVE_RING0 = "leave.ring0"
        private const val LEAVE_GLYPH0 = "leave.glyph0"
        private const val LEAVE_FADE = "the leave releases a few ms later in funeOS: the kit's arriving hub is ready at the T4 start, so the leave runs its own ramp (Leave A 0.072 at 160 ms, as at the turn start at 4920), while funeOS's reads 0.042 there, the value of a leave released at 150 ms (1 - 160 / (150 + kTail 17)), its arriving layer rendered then; on R1 navbar-t4 the focused ring's left stroke reads 34 over the 17 ground against funeOS's 26 over 16, so its peak is 17 levels against 12, off the 4 level floor, while ring1, the art and the A105 frames stay inside it"
        private const val TERRACE_ARTIST = "ヨルノオト"
        private const val FIRST_ARTIST = "the first # artist of the Demo order stands in for funeOS's"
        private const val MUKA = "48 Muka"
        private const val MUKA_NOTE = "48 Muka (albums The Xx Yy of 1997, two title lines, and NN Word of 1998) stands in by shape for funeOS's first # artist 81 Mali"
        private const val FIRST_ALBUM = "the first # album of the Demo order stands in for funeOS's"
        private const val COL_NOTE = "the column page opens on the first present ALBUMS letter, the grid's focus at the pick"
        private const val TERRACE_NOTE = "ヨルノオト and its coverless テラス盤 stand in for funeOS's ヨアソビ and テスト盤"
        private const val PAGE_LOUD = "the album, column or artist page at rest shows only its header, block and rows on the plain ground"
        private const val MOVE_QUIET = "a move frame: the moving layers are kit parts that record their own regions and funeOS ink between them is part of the move"
    }
}
