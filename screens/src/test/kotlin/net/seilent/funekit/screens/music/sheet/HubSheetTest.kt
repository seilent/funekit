package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Nav
import net.seilent.funekit.Role
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.core.Section
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink as KitInk
import net.seilent.funekit.kit.JumpCells
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.JumpInk
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.jumpGrid
import net.seilent.funekit.kit.listThumbOf
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.InputGate
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.thumb
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Kind as TouchKind
import net.seilent.funekit.kit.touch.Scroll
import net.seilent.funekit.kit.touch.Velocity
import net.seilent.funekit.kit.word
import net.seilent.funekit.scale
import net.seilent.funekit.screens.host.MusicEntry
import net.seilent.funekit.screens.host.isNp
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HUB_ARTISTS
import net.seilent.funekit.screens.music.hub.HUB_FOLDERS
import net.seilent.funekit.screens.music.hub.HUB_SONGS
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubLook
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.HubPaint
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpInks
import net.seilent.funekit.screens.music.nowplaying.NpMeasure
import net.seilent.funekit.screens.music.nowplaying.NpPaint
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.npFlat
import net.seilent.funekit.screens.music.nowplaying.npFrame
import net.seilent.funekit.screens.music.nowplaying.npPage
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Kind
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.Sheet
import net.seilent.funekit.sheet.Tile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HubSheetTest {
    private val sc = MusicScenes()
    private val accent = sc.accent

    private class Shot(val m: HubModel, val d: HubDraw, val c: Chrome, val s: Scale)

    private fun hubShot(dev: Dev, pivot: Int, large: Boolean = false): Shot {
        val s = sc.pageScale(dev, Nav.None, large)
        val (m, d) = sc.hub(s, pivot)
        sc.fill(m, d)
        val c = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots, sc.covers).also { it.select(pivot) }
        return Shot(m, d, c, s)
    }

    private fun DrawScope.ground(dev: Dev) = pageGround(groundBands(dev.h, false), accent)

    private fun DrawScope.page(h: Shot, now: Long, rec: Regions?, look: (Int) -> HubLook) {
        val hr = rec?.let { HubRegions(it) }
        chrome(h.c, now)
        rec?.chrome("chrome", h.c, now, size.width.toInt())
        with(h.d) { content(h.m.pivot, now, true, hr, "page", look) }
    }

    private fun rest(h: Shot, now: Long): (Int) -> HubLook = { r -> h.m.look(h.m.pivot, r, now, true) }

    private fun tile(folder: String, dev: Dev, kind: String, h: Shot, notes: List<String>, rec: Regions?, block: DrawScope.() -> Unit): Tile =
        sc.tile(folder, dev, kind, h.s, notes, rec, block)

    @Test
    fun funeOsHubScenes() {
        val f = FOLDER
        val boards = listOf(sc.r1, sc.a105)
        val hubTiles = boards.map { dev ->
            val h = hubShot(dev, HUB_ARTISTS)
            val rec = sc.regions(dev, h.s).also { it.residualWhy = HUB_QUIET }
            tile(f, dev, "hub", h, listOf(HUB_NOTE), rec) {
                ground(dev)
                page(h, 0L, rec, rest(h, 0L))
            }
        } + listOf(sc.v1 to false, sc.a105 to true).map { (dev, large) ->
            val h = hubShot(dev, HUB_ARTISTS, large)
            sc.render.tile(f, "${dev.id}${if (large) "-large" else ""}-hub", dev.label, listOf(sc.render.facts(dev, Nav.None, h.s), HUB_NOTE), sc.draw(dev) {
                ground(dev)
                page(h, 0L, null, rest(h, 0L))
            }, emptyList(), h.s.stdPpu, null)
        }
        sc.render.write(Sheet(f, "hub", listOf("The music hub on ARTISTS with the # divider focused, beside funeOS hub (V1 and A105 LARGE unpaired)."), hubTiles))
        val held = boards.map { dev ->
            val h = hubShot(dev, HUB_SONGS)
            val pn = h.m.pane
            pn.setFocus(PRESSED, 0, 0L, false)
            pn.glow.arm(PRESSED, 0L)
            val rec = sc.regions(dev, h.s).also { it.residualWhy = PAGE_LOUD }
            rec.residual = true
            tile(f, dev, "rowpress-0-hubsongs-held", h, listOf(HELD_NOTE), rec) {
                ground(dev)
                page(h, 0L, rec) { r ->
                    val k = h.m.look(HUB_SONGS, r, 0L, true)
                    if (r == PRESSED) k.copy(glow = 1f) else k
                }
            }
        }
        sc.render.write(Sheet(f, "hub-songs", listOf("The songs pivot with its first song pressed and glowing, beside funeOS rowpress-0-hubsongs-held."), held))
    }

    @Test
    fun folderRoots() {
        val two = MusicScenes(FakeSet.Main)
        val tiles = listOf(sc.r1, sc.a105).flatMap { dev ->
            listOf(Triple(sc, "folders", ONE_ROOT), Triple(two, "folders-roots", TWO_ROOTS)).map { (scenes, kind, note) ->
                val s = scenes.pageScale(dev)
                val (m, d) = scenes.hub(s, HUB_FOLDERS)
                scenes.fill(m, d)
                val c = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots, sc.covers).also { it.select(HUB_FOLDERS) }
                sc.render.tile(FOLDER, "${dev.id}-$kind", dev.label, listOf(sc.render.facts(dev, Nav.None, s), note), sc.draw(dev) {
                    ground(dev)
                    chrome(c, 0L)
                    with(d) { content(HUB_FOLDERS, 0L, true, null, "page") { r -> m.look(HUB_FOLDERS, r, 0L, true) } }
                }, emptyList(), s.stdPpu, null)
            }
        }
        sc.render.write(Sheet(FOLDER, "folders-roots", listOf("FOLDERS at its root: one library root opens on that root's folders and songs, two roots list the roots. No funeOS reference for the two root form."), tiles))
    }

    private class Scene(val name: String, val note: String, val build: (JumpGrid) -> Long)

    private val jumpScenes = listOf(
        Scene("jump-500-top", "the songs hub jumped to a, the grid opened; main.cpp jump opens at 2600 and shoots at 4100, so the thumb is at the end of its hold") { g ->
            g.open(g.avail.copyOf(), 1, g.bottom)
            HOLD_SHOT
        },
        Scene("jump-500-mid", "the songs hub jumped to s, the grid opened, shot as jump-500-top") { g ->
            g.open(g.avail.copyOf(), MID_LETTER, g.bottom)
            HOLD_SHOT
        },
        Scene("jump-500-end", "a down at (120, 300) u at scroll 0 focuses u, its drag to the grid's end cancels the press") { g ->
            g.open(g.avail.copyOf(), 1, g.bottom)
            g.down(g.hit(TOUCH_X.toFloat(), END_Y, 0f), 0L)
            g.press.cancel(0L)
            g.follow(g.limit())
            g.focusAfterScroll(g.limit(), 0L)
            0L
        },
        Scene("jump-500-exit", "three knob steps back from a at 1000, 1200 and 1400 ms after the open: # then EXIT, the third has nowhere to go; shot at 1900 (main.cpp 4500), 400 ms into the thumb fade") { g ->
            g.open(g.avail.copyOf(), 1, g.bottom)
            repeat(EXIT_STEPS) { g.step(-1, EXIT_STEP + it * KNOB_GAP) }
            EXIT_SHOT
        },
        Scene("rowpress-0-jump-held", "the grid open on the songs hub, its focused tile held 150 ms") { g ->
            g.open(g.avail.copyOf(), 0, g.bottom)
            g.down(g.focus, 0L)
            HELD_MS
        },
    )

    private fun jumpTile(dev: Dev, scene: Scene): Tile {
        val h = hubShot(dev, HUB_SONGS)
        val s = h.s
        val g = JumpGrid().also { it.open(h.m.avail(), 0, s.stdH) }
        val at = scene.build(g)
        fun style(r: Role) = roleStyle(r, s, sc.density, KitInk.white, ppu = s.stdPpu)
        val ink = JumpInk(
            sc.measurer.word("exit", Roles.jumpExit, style(Roles.jumpExit), sc.fonts, sc.density, null, s.board.fontHanMinPx),
            (0 until Section.count).map { sc.measurer.word(Section.letter(it), Roles.jumpCell, style(Roles.jumpCell), sc.fonts, sc.density, sc.covers, s.board.fontHanMinPx) },
        )
        h.m.pane.setFocus(PICKED, 0, 0L, false)
        val rec = sc.regions(dev, s).also {
            it.residual = true
            it.residualWhy = JUMP_LOUD
        }
        return tile(FOLDER, dev, scene.name, h, listOf(scene.note), rec) {
            ground(dev)
            rec.hide("under the jump grid's opaque ground") { page(h, 0L, rec, rest(h, 0L)) }
            jumpGrid(g, ink, JumpCells(s), s, g.offset, at)
            rec.jump("jump", g, ink, JumpCells(s), s, g.offset, at, dev.h)
        }
    }

    private class ToastScene(val name: String, val note: String, val at: Long, val rest: Boolean, val down: Long = -1L)

    private val toastScenes = listOf(
        ToastScene("toast-150", "as funeOS toast:150: the song picked after main.cpp toast's three knob steps opens now playing (play open), the reply at once; T7 150 ms after the pick, the hub turned out on the NpLeave pane", TOAST_IN, false),
        ToastScene("toast-2000", "as funeOS toast:2000: now playing at rest 2000 ms after the pick, no toast", TOAST_HOLD, true),
        ToastScene("toast-500-press", "as funeOS toast:500:press: the down at (120, 30) u 350 ms after the pick lands the T7, as any down during a move does, and draws nothing; now playing at rest at the shot, 500 ms after the pick", TOAST_PRESS, true, TOAST_PRESS - PRESS_LEAD),
    )

    private val noHome = object : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = Unit
    }

    private fun toastTile(dev: Dev, scene: ToastScene): Tile {
        val h = hubShot(dev, HUB_SONGS)
        val s = h.s
        val pn = h.m.pane
        pn.setFocus(PICKED, 0, 0L, false)
        val npS = scale(dev.window(Nav.None), Nav.None, Surface.NowPlaying, false)
        val g = NpLayout(npS)
        val m = NowPlayingModel()
        runBlocking {
            val rows = sc.fake.library.view().page(Listing.Songs, pn.cell(PICKED, 0), 1 + g.nextTracks).rows
            val track = rows.first()
            m.feed(PlayerState(queueSize = rows.size, index = 0, track = track.id, playing = true, anchor = Anchor(0, RATE, 0, false, track.durationMs ?: 0L)))
            m.sync(NpShown.of(rows), 0L)
            m.resetFocus(0L)
        }
        val inks = NpInks(NpMeasure(g, sc.measurer, sc.fonts, sc.density, sc.covers), m)
        val npPaint = NpPaint(g, m, listOf(npFlat(g) to inks), { null }, accent)
        val hubPaint = HubPaint(h.d, h.c, HOME.indexOf(MusicPage.Hub.headerWord ?: ""), groundBands(dev.h, false), accent)
        fun side(sm: MusicEntry?): PageSide? = sm?.let { if (it.page == MusicPage.NowPlaying) PageSide(npS, npPaint) else PageSide(s, hubPaint) }
        val forms = sc.forms(dev, s)
        val move = PageMove()
        val mp = MovePaint(move).also { it.npForm = NpForm.Page }
        val hub = MusicEntry(0L, MusicPage.Hub)
        val np = MusicEntry(1L, MusicPage.NowPlaying)
        val track = m.shown.track
        val st = PageStack<MusicEntry>(forms, { if (it == hub) h.m.kind else PageKind.Plain }, move = move, ready = { p -> !isNp(p) || m.shown.track == track }, isNp = ::isNp, gate = InputGate()) { t ->
            mp.ends(t, HomeSide(s, noHome, 0f), side(t.from), side(t.to), forms)
        }
        st.pages.add(hub)
        st.pushOpen(np, 0L, NowPlayingModel.HOLD_CAP).done()
        var t = 0L
        var land = -1L
        var down = scene.down
        fun frameAt(now: Long) {
            st.frame(now)
            if (land < 0 && st.phase == Phase.Rest) {
                land = now
                m.touched(now)
            }
            m.stepTo(now)
        }
        while (t <= scene.at) {
            if (down in 0..t) {
                st.touchDown()
                frameAt(down)
                down = -1L
            }
            frameAt(t)
            t += STEP
        }
        st.frame(scene.at)
        m.stepTo(scene.at)
        val rest = st.phase == Phase.Rest
        require(rest == scene.rest && (!rest || st.top == np)) { "${scene.name}: now playing at rest $rest at the shot, the scene wants ${scene.rest}" }
        val rec = sc.regions(dev, s)
        rec.residual = rest
        rec.residualWhy = if (rest) NP_LOUD else T7_QUIET
        return tile(FOLDER, dev, scene.name, h, listOf(scene.note), rec) {
            if (rest) {
                rec.edges("np.art", Kind.Art, Edges(g.artX, g.artY, g.artX + g.artSide, g.artY + g.artSide), KitInk.tile, 1f, peakOff = ART_PEAK)
                val b = g.bar
                rec.edges("np.bar", Kind.Bar, Edges(b.x0, b.y0, b.x1, b.y1), KitInk.white, Tok.npBarTrack)
                npPage(g, npFrame(m, g, scene.at, false), inks.ink(scene.at), inks.time(scene.at, 0L), null, null, accent, false, false, rec)
            } else {
                with(mp) { drawMove(scene.at, rec, sc.moveStatus(s)) }
            }
        }
    }

    @Test
    fun funeOsJumpAndToastScenes() {
        val boards = listOf(sc.r1, sc.a105)
        sc.render.write(Sheet(FOLDER, "jump", listOf("Jump grid scenes over the songs hub beside funeOS jump-500-top, -mid, -end, -exit and rowpress-0-jump-held."), boards.flatMap { dev -> jumpScenes.map { jumpTile(dev, it) } }))
        sc.render.write(Sheet(FOLDER, "toast", listOf("The songs pick that opens now playing (play open) beside funeOS toast-150, toast-2000 and toast-500-press: T7 turning the hub out, then now playing at rest with no toast."), boards.flatMap { dev -> toastScenes.map { toastTile(dev, it) } }))
    }

    private enum class Ev { Down, Move, Up }

    private class Touch(val at: Long, val y: Int, val ev: Ev)

    private fun line(y0: Int, y1: Int, n: Int): List<Int> = (1..n).map { y0 + (y1 - y0) * it / n } + y1

    private val fadeTouches: List<Touch> = run {
        val ys = line(FADE_Y0, FADE_Y1, FADE_STEPS) + List(FADE_HOLDS) { FADE_Y1 }
        listOf(Touch(FADE_DOWN, FADE_Y0, Ev.Down)) + ys.mapIndexed { i, y -> Touch(FADE_DOWN + (i + 1) * STEP, y, if (i == ys.lastIndex) Ev.Up else Ev.Move) }
    }

    private val flingTouches: List<Touch> =
        listOf(Touch(FLING_DOWN, FLING_Y0, Ev.Down)) +
            (1..FLING_MOVES).map { Touch(FLING_DOWN + it * FLING_GAP, FLING_Y0 - FLING_DY * it, Ev.Move) } +
            Touch(FLING_UP, FLING_Y0 - FLING_DY * FLING_MOVES, Ev.Up)

    private class SongsScene(val shownAt: Long, val switches: Boolean, val touches: List<Touch>)

    private val fadeScene = SongsScene(SWITCH, true, fadeTouches)

    private val flingScene = SongsScene(SELECT + Tok.motion_t2Swap.toLong(), false, flingTouches)

    private fun songsTile(dev: Dev, scene: SongsScene, shot: Long, kind: String, note: String): Tile {
        val h = hubShot(dev, HUB_SONGS)
        val s = h.s
        val m = h.m
        val pn = m.pane
        val geo = h.d.geo
        val n = requireNotNull(pn.map).count
        val track = pn.track
        val scroll = Scroll(TouchKind.List, s.touchPpu, s.stdPpu, s.ppu, track)
        val slop = Arbiter(s).slop
        val vel = Velocity(Tok.motion_flingWindow.toLong())
        val fade = ThumbFade()
        val c = h.c.also { it.select(if (scene.switches) 0 else HUB_SONGS) }
        fun bar() = listThumbOf(s, geo.top, geo.bottom, track)
        fun afterScroll(at: Long) = pn.afterScroll(at)
        var dragging = false
        var downY = 0f
        fun touch(e: Touch) {
            val y = e.y * s.stdPpu
            vel.add(e.at, y)
            when (e.ev) {
                Ev.Down -> {
                    downY = y
                    scroll.grab(e.at)
                    val row = track.tapRow(y - geo.top)
                    pn.focus.set(row, n, e.at, true)
                    pn.press.down(row, e.at)
                    pn.glow.arm(row, e.at)
                }
                Ev.Move -> if (dragging) {
                    scroll.drag(y)
                    afterScroll(e.at)
                } else if (!slop.tap(TOUCH_X * s.stdPpu, downY, TOUCH_X * s.stdPpu, y)) {
                    pn.press.cancel(e.at)
                    pn.glow.off()
                    dragging = true
                    scroll.begin(y)
                }
                Ev.Up -> {
                    fade.release(e.at)
                    if (dragging) {
                        dragging = false
                        scroll.release(e.at, vel.perS())
                    }
                }
            }
        }
        val pivot = if (scene.switches) listOf<Pair<Long, (Long) -> Unit>>(SWITCH to { at: Long -> c.switchTo(HUB_SONGS, at, 1) }) else emptyList()
        val acts = pivot + scene.touches.map { e -> e.at to { _: Long -> touch(e) } }
        var next = 0
        var t = 0L
        while (true) {
            while (next < acts.size && acts[next].first <= t) {
                val (at, act) = acts[next++]
                act(at)
            }
            val flinging = scroll.flinging
            scroll.step(t)
            if (flinging) afterScroll(t)
            if (t >= scene.shownAt) fade.track(bar() != null, scroll.moving || scroll.dragging, t)
            if (t >= shot) break
            t = min(t + STEP, shot)
        }
        sc.fill(m, h.d)
        val rec = sc.regions(dev, s).also {
            it.residual = true
            it.residualWhy = SCROLL_LOUD
        }
        return tile(FOLDER, dev, kind, h, listOf(note), rec) {
            ground(dev)
            page(h, shot, rec) { r -> m.look(HUB_SONGS, r, shot, true) }
            bar()?.let {
                val a = fade.at(shot)
                thumb(it, a)
                rec.thumbParts("thumb", it, Tok.scrollbar_alpha * a, groundBands(dev.h, false), accent)
            }
        }
    }

    @Test
    fun funeOsThumbFadeAndFlingScenes() {
        val boards = listOf(sc.r1, sc.a105)
        sc.render.write(Sheet(FOLDER, "thumbfade", listOf("Scroll thumb scenes beside funeOS thumbfade-2700 and thumbfade-4800: the hub's songs list dragged by the main.cpp touches, the ThumbFade tracked per 16 ms frame."), boards.flatMap { dev ->
            fadeNotes.map { (ms, note) -> songsTile(dev, fadeScene, ms, "thumbfade-$ms", note) }
        }))
        sc.render.write(Sheet(FOLDER, "fling", listOf("Fling scenes beside funeOS fling-songs-150 and fling-songs-900: the hub's songs list dragged and flung by the main.cpp touches, the focus following the scroll."), boards.flatMap { dev ->
            flingNotes.map { (ms, note) -> songsTile(dev, flingScene, FLING_UP + ms, "fling-songs-$ms", note) }
        }))
        sc.render.index(FOLDER, listOf(
            "# Music hub", "",
            "- `$FOLDER/hub.png`: R1 and A105 tiles `<board>-hub` paired with funeOS hub; V1 and A105 LARGE unpaired",
            "- `$FOLDER/hub-songs.png`: `<board>-rowpress-0-hubsongs-held`",
            "- `$FOLDER/jump.png`: the jump grid scenes over the songs hub; `$FOLDER/toast.png`: the songs pick opening now playing (play open), T7 and now playing at rest",
            "- `$FOLDER/thumbfade.png`, `$FOLDER/fling.png`: the funeOS thumbfade and fling scenes on the hub's songs list",
            "- `$FOLDER/empty.png`, `$FOLDER/scan.png`: the status line on an empty library and while a scan runs",
            "- `$FOLDER/play-accentoff.png`: playing rows under ACCENT OFF",
            "- `$FOLDER/large.png`: the hub at SIZE LARGE",
            "- `$FOLDER/folders.png`: FOLDERS levels, the path line and the jump grid over the folders base",
            "- `$FOLDER/press.png`: header and pivot presses",
            "- `$FOLDER/t5-folders.png`: the pivot switch to FOLDERS 300 ms in",
            "- `$FOLDER/navbar.png`: the hub with NAV BAR ON",
            "- `$FOLDER/folders-roots.png`: FALLBACK, FOLDERS at its root with one and two library roots, unpaired",
            "- `$FOLDER/hub/sizes.png`: the hub at 1080 x 2160 and 1080 x 2520, unpaired",
            "- every paired tile has `<tile>.regions.json` beside it for `kit/tools/inkband.py`",
        ))
    }

    private val fadeNotes = mapOf(
        FADE_REST to "main.cpp thumbfade: home music selected at 1000, the songs pivot switched in at 2000 (the thumb shown), 700 ms into the 1500 ms thumb hold; the list at 0, the focus on the # row",
        FADE_SHOT to "then a down at (120, 250) u at 2900 focuses and presses row 3, leaves the slop at 2964 (y 224), drags to 170 by 3092 and holds there to the up at 3236, so no fling; the thumb hold runs from the up and the fade is 64 ms in",
    )

    private val flingNotes = mapOf(
        FLING_EARLY to "main.cpp fling: home music selected at 1000 (the thumb shown at the T2 swap, 1312), the songs pivot set at 2400; a down at (120, 300) u at 2800 focuses and presses row 4, moves up 20 u per 10 ms, leaves the slop at 2820 (y 260) and lifts at (120, 140) at 2880, a 2000 u/s fling; 150 ms after the up the focus follows the scroll to the first whole row",
        FLING_LATE to "the same fling 900 ms after the up, settled; the thumb holds from the fling's last frame",
    )

    companion object {
        private const val FOLDER = "music"
        private const val ONE_ROOT = "one library root: the root's folders and songs, the path line from below the root"
        private const val TWO_ROOTS = "two library roots: one row per root, each with its song count"
        private const val PRESSED = 1
        private const val PICKED = 3
        private const val MID_LETTER = 19
        private const val END_Y = 300f
        private const val HELD_MS = 150L
        private const val HOLD_SHOT = 1500L
        private const val EXIT_STEPS = 3
        private const val EXIT_STEP = 1000L
        private const val KNOB_GAP = 200L
        private const val EXIT_SHOT = 1900L
        private const val TOAST_IN = 150L
        private const val TOAST_HOLD = 2000L
        private const val TOAST_PRESS = 500L
        private const val PRESS_LEAD = 150L
        private const val STEP = 16L
        private const val SWITCH = 2000L
        private const val TOUCH_X = 120
        private const val FADE_DOWN = 2900L
        private const val FADE_Y0 = 250
        private const val FADE_Y1 = 170
        private const val FADE_STEPS = 12
        private const val FADE_HOLDS = 8
        private const val FADE_REST = 2700L
        private const val FADE_SHOT = 4800L
        private const val SELECT = 1000L
        private const val FLING_DOWN = 2800L
        private const val FLING_Y0 = 300
        private const val FLING_DY = 20
        private const val FLING_GAP = 10L
        private const val FLING_MOVES = 8
        private const val FLING_UP = FLING_DOWN + FLING_MOVES * FLING_GAP
        private const val FLING_EARLY = 150L
        private const val FLING_LATE = 900L
        private const val HUB_NOTE = "as funeOS hub: the ARTISTS pivot with the collection # divider focused as enter_hub leaves it, the fixture's artists as collection rows with the play ring and the album thumb strips"
        private const val HELD_NOTE = "as funeOS rowpress-0-hubsongs-held: the songs pivot, # divider, numbered two line rows with the library second line, the first song focused and pressed at motion.pressScale with the press glow"
        private const val HUB_QUIET = "the funeOS hub draws its fixture's album covers in the artist row thumb strips, which the regions skip (they show this fixture's art and album counts)"
        private const val PAGE_LOUD = "the songs pivot shows only list rows, every one drawn, besides the chrome"
        private const val JUMP_LOUD = "the opaque jump grid covers the page, so every funeOS mark on it is a kit element"
        private const val T7_QUIET = "T7 turns the leaving hub out on the NpLeave pane inside a kit quad that records no region, so funeOS ink there is part of a turned layer, not a difference"
        private const val NP_LOUD = "now playing shows only its back ring, art, bar, rings, mark and text lines on the plain ground, so every funeOS mark outside them is a difference"
        private const val ART_PEAK = "the fixture art is a flat placeholder against the funeOS demo cover"
        private const val RATE = 48000
        private const val SCROLL_LOUD = "the songs hub content area shows only list rows, every one drawn, besides the chrome and the thumb"
    }
}
