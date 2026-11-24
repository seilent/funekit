package net.seilent.funekit.screens.music.sheet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Fonts
import net.seilent.funekit.Nav
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.Surface
import net.seilent.funekit.api.Anchor
import net.seilent.funekit.api.Kind as ApiKind
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard
import net.seilent.funekit.kit.Band
import net.seilent.funekit.kit.Chrome
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.Menu
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.MenuInk
import net.seilent.funekit.kit.MenuPlan
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusInk
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.StatusText
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.ToastInk
import net.seilent.funekit.kit.ToastPlace
import net.seilent.funekit.kit.chrome
import net.seilent.funekit.kit.chromeOf
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.menuInk
import net.seilent.funekit.kit.menuLayer
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.kit.motion.Prop
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.pageGround
import net.seilent.funekit.kit.pageStatus
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.InputGate
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.MoveStatus
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.statusLine
import net.seilent.funekit.kit.thumb
import net.seilent.funekit.kit.toast
import net.seilent.funekit.kit.toastSurface
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.word
import net.seilent.funekit.scale
import net.seilent.funekit.screens.host.MusicEntry
import net.seilent.funekit.screens.host.PageThumb
import net.seilent.funekit.screens.host.frameMenu
import net.seilent.funekit.screens.host.isNp
import net.seilent.funekit.screens.host.pageThumbAtSwap
import net.seilent.funekit.screens.host.pageThumbsAtTurn
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HUB_FOLDERS
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.hubScrollInto
import net.seilent.funekit.screens.music.hub.hubThumbAt
import net.seilent.funekit.screens.music.hub.look
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpInks
import net.seilent.funekit.screens.music.nowplaying.NpMeasure
import net.seilent.funekit.screens.music.nowplaying.NpPaint
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.npFlat
import net.seilent.funekit.screens.music.nowplaying.npFrame
import net.seilent.funekit.screens.music.nowplaying.npPage
import net.seilent.funekit.screens.music.overlay.ADD
import net.seilent.funekit.screens.music.overlay.INFO
import net.seilent.funekit.screens.music.overlay.PIN
import net.seilent.funekit.screens.music.overlay.PLAY
import net.seilent.funekit.screens.music.overlay.PLAY_NEXT
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.queue.QueueDraw
import net.seilent.funekit.screens.music.queue.QueueModel
import net.seilent.funekit.screens.music.queue.QueuePaint
import net.seilent.funekit.screens.music.queue.queueDraw
import net.seilent.funekit.sheet.Box
import net.seilent.funekit.sheet.Dev
import net.seilent.funekit.sheet.Kind
import net.seilent.funekit.sheet.Regions
import net.seilent.funekit.sheet.menuLayer
import net.seilent.funekit.sheet.toast
import kotlin.math.min
import kotlin.math.roundToInt

const val SHEET_STEP = 16L
const val SHEET_KNOB = 120L
const val KNOBS_AT = 2600L
const val HUB_SHOWN = 1300L
const val ART_PEAK = "the fixture art is a flat placeholder against the funeOS demo cover"
const val ADD_PLAYLIST = "add to playlist"
const val SONG_SHORT = "funeOS v0.4.3 draws the song sheet with 8 rows and the kit with 7, drawing no delete, so every page region meeting either panel is excluded"
private const val GO_ALBUM = "go to album"
private const val DELETE = "delete"
val OLD_SONG_ITEMS = listOf(PLAY, PLAY_NEXT, ADD, ADD_PLAYLIST, PIN, INFO, GO_ALBUM, DELETE)

fun MusicScenes.forms(dev: Dev, s: Scale): MoveForms = MoveForms.board(
    when (boards[dev]) {
        "r1" -> TokBoard.r1
        "a105" -> TokBoard.a105
        "v1" -> TokBoard.v1
        else -> s.board
    },
)

fun MusicScenes.statusInk(s: Scale): StatusInk {
    val box = pageStatus()
    val line = statusLine(s, box)
    val style = roleStyle(Roles.time, s, density, Ink.white, Tok.alpha_full).merge(Fonts.style(Roles.time, line.textPx, density, Roles.time.tracking * s.ppu))
    fun w(t: String) = measurer.word(t, Roles.time, style, fonts, density, null, s.board.fontHanMinPx)
    return StatusInk(StatusState(STATUS_CLOCK, STATUS_BATTERY, false, Play.Playing, false, -1, -1), StatusText(w(STATUS_CLOCK), w("$STATUS_BATTERY%"), null), line, s, box)
}

fun MusicScenes.moveStatus(s: Scale): MoveStatus = MoveStatus(statusInk(s), statusInk(s), accent, false)

private const val STATUS_CLOCK = "12:00"
private const val STATUS_BATTERY = 100

fun MusicScenes.toastInk(t: Toast, s: Scale): ToastInk = ToastInk(
    measurer.word(t.artist, Roles.toastArtist, roleStyle(Roles.toastArtist, s, density, Ink.white), fonts, density, null, s.board.fontHanMinPx),
    measurer.word(t.title, Roles.toastTitle, roleStyle(Roles.toastTitle, s, density, Ink.white), fonts, density, null, s.board.fontHanMinPx),
)

fun DrawScope.trackToast(t: Toast, ink: ToastInk, s: Scale, w: Int, now: Long, rec: Regions?) {
    t.frame(ToastPlace.Bottom, toastSurface(s, s.navOn), now)
    toast(t, ink, s, now)
    rec?.toast("toast", t, ink, s, now, w)
}

class MenuShot(val menu: Menu, val ink: MenuInk, val plan: MenuPlan, val over: Overlay) {
    val name: String get() = if (over.kind == OverKind.Sheet) "sheet" else "dialog"

    fun top(now: Long, panelH: Int): Int = menu.bottomPx - panelH + (over.sheet(now).dy * panelH).roundToInt()
}

fun MusicScenes.menuShot(
    s: Scale,
    form: MenuForm,
    title: String,
    items: List<String>,
    at: Long,
    readOnly: Boolean = false,
    mixedRow: Int = -1,
    confirm: Boolean = false,
    steps: Int = 0,
    stepAt: Long = at,
    lead: Int = 0,
): MenuShot {
    val m = Menu()
    m.open(form, title, items, at, readOnly, mixedRow, lead = lead, confirm = confirm)
    frameMenu(m, s, false) { null }
    val ink = menuInk(measurer, fonts, m, s, density, false, covers)
    m.settle(m.plan(ink))
    repeat(steps) { m.step(1, stepAt) }
    val plan = m.plan(ink)
    m.settle(plan)
    return MenuShot(m, ink, plan, Overlay(if (form == MenuForm.Sheet) OverKind.Sheet else OverKind.Dlg).also { it.open(at) })
}

fun MusicScenes.funeOsPanelH(s: Scale, ms: MenuShot, items: List<String>): Int {
    val m = Menu()
    m.open(ms.menu.form, ms.menu.title, items, 0L)
    frameMenu(m, s, false) { null }
    return m.plan(menuInk(measurer, fonts, m, s, density, false, covers)).panelH
}

fun DrawScope.overPage(
    sc: MusicScenes,
    rec: Regions?,
    ms: MenuShot,
    s: Scale,
    now: Long,
    funeOs: List<String>?,
    why: String?,
    page: DrawScope.(Regions?) -> Unit,
) {
    val o = ms.over
    val w = size.width.toInt()
    val h = size.height.toInt()
    val scrim = if (o.kind == OverKind.Sheet) o.sheet(now).scrim else o.dialog(now).scrim
    if (rec == null) page(null) else rec.under(Ink.bg, scrim) { rec.fade(1 - scrim) { page(rec) } }
    if (rec != null && funeOs != null && why != null && o.kind == OverKind.Sheet) {
        val top = min(ms.top(now, ms.plan.panelH), ms.top(now, sc.funeOsPanelH(s, ms, funeOs)))
        rec.shade(Box(0f, top.toFloat(), w.toFloat(), h.toFloat()), why)
    }
    menuLayer(ms.menu, ms.ink, ms.plan, o, s, now, sc.accent, false, Paint())
    rec?.menuLayer(ms.name, ms.menu, ms.ink, ms.plan, o, s, now, w, h, sc.accent, false)
}

class HubShot(val sc: MusicScenes, val dev: Dev, val s: Scale, val m: HubModel, val d: HubDraw, val c: Chrome) {
    val bands = groundBands(dev.h, false)
    val moves = mutableListOf<LongRange>()
    var shown = HUB_SHOWN
    val pitch: Int get() = d.geo.pitch(m.pivot)

    fun knob(row: Int, at: Long) {
        val pn = m.pane
        pn.setFocus(row, 0, at, false)
        val t = pn.track
        val r = pn.focus.row
        val want = hubScrollInto(t.topOf(r), t.topOf(r + 1) - t.topOf(r), t.q, d.geo.view)
        if (want == pn.track.q) return
        pn.track.at(want)
        moves += at until at + Tok.motion_scroll.toLong()
    }

    fun knobs(n: Int, at: Long): Long {
        repeat(n) { knob(m.pane.focus.row + 1, at + it * SHEET_KNOB) }
        return at + n * SHEET_KNOB
    }

    fun fade(shot: Long): Float {
        val on = hubThumbAt(m, d.geo, m.pivot, m.pane.track.q) != null
        val f = ThumbFade()
        var t = 0L
        while (true) {
            if (t >= shown) f.track(on, moves.any { t in it }, t)
            if (t >= shot) break
            t = min(t + SHEET_STEP, shot)
        }
        return f.at(shot)
    }

    fun enter(part: String, at: Long) = runBlocking {
        val v = sc.fake.library.view()
        val pn = m.pane
        val rows = requireNotNull(pn.whole)
        val spaced = part.replace('_', ' ')
        val i = rows.indexOfFirst { it.kind == ApiKind.Folder && (it.title == part || it.title == spaced) }
        require(i >= 0) { "no folder $part in ${m.path}" }
        pn.setFocus(requireNotNull(pn.map).rowOfIndex(i), 0, at, false)
        m.enter(rows[i].id, rows[i].title)
        m.load(v, HUB_FOLDERS)
        val map = requireNotNull(pn.map)
        pn.track.count(map.count, 0)
        pn.counted = map
        m.settleFolder(d.geo.view, pitch, at)?.let { pn.track.at(it) }
    }

    fun firstSong(at: Long): Int {
        val pn = m.pane
        val rows = requireNotNull(pn.whole)
        val i = rows.indexOfFirst { it.kind == ApiKind.Song }
        require(i >= 0) { "no song in ${m.path}" }
        val row = requireNotNull(pn.map).rowOfIndex(i)
        pn.setFocus(row, 0, at, false)
        return row
    }

    fun target(row: Int, col: Int = 0): SheetTarget {
        sc.fill(m, d)
        return requireNotNull(m.target(m.pivot, row, col)) { "row $row, cell $col of pivot ${m.pivot} has no sheet" }
    }

    fun items(t: SheetTarget): List<String> = runBlocking {
        val pinned = t.pin?.let { k -> sc.fake.library.view().pinned(k, t.id) } ?: false
        sheetItems(t, pinned)
    }

    fun DrawScope.page(now: Long, rec: Regions?) {
        pageGround(bands, sc.accent)
        chrome(c, now)
        rec?.chrome("chrome", c, now, size.width.toInt())
        with(d) { content(m.pivot, now, true, rec?.let { HubRegions(it) }, "page") { r -> m.look(m.pivot, r, now, true) } }
        val a = fade(now)
        if (a > 0f) {
            hubThumbAt(m, d.geo, m.pivot, m.pane.track.q)?.let {
                thumb(it, a)
                rec?.thumbParts("thumb", it, Tok.scrollbar_alpha * a, bands, sc.accent)
            }
        }
    }
}

fun MusicScenes.hubShot(dev: Dev, pivot: Int, nav: Nav = Nav.None, large: Boolean = false): HubShot {
    val s = pageScale(dev, nav, large)
    val (m, d) = hub(s, pivot)
    val c = chromeOf(measurer, fonts, s, density, MusicPage.Hub.headerWord ?: "", MusicPage.Hub.pivots, covers).also { it.select(pivot) }
    return HubShot(this, dev, s, m, d, c)
}

val HOME = listOf("music", "playlists", "history", "apps", "settings")

class QueueRig(val sc: MusicScenes, val dev: Dev, nav: Nav = Nav.None, large: Boolean = false, album: Int = QUEUE_ALBUM_CELL) {
    val s = sc.pageScale(dev, nav, large)
    val npS = scale(dev.window(nav), nav, Surface.NowPlaying, large)
    val g = NpLayout(npS)
    val forms: MoveForms = sc.forms(dev, s)
    val bands = groundBands(dev.h, false)
    val np = NowPlayingModel()
    val q = QueueModel(QUEUE_ENTRY)
    val d: QueueDraw = queueDraw(q, s, sc.measurer, sc.fonts, sc.density, sc.covers, sc.accent, false)
    val chrome = chromeOf(sc.measurer, sc.fonts, s, sc.density, MusicPage.Queue.headerWord ?: "", MusicPage.Queue.pivots, sc.covers)
    val paint = QueuePaint(d, chrome, bands, sc.accent)
    val inks = NpInks(NpMeasure(g, sc.measurer, sc.fonts, sc.density, sc.covers), np)
    val npPaint = NpPaint(g, np, listOf(npFlat(g) to inks), { null }, sc.accent, hideFocus = { false })
    val npScreen = MusicEntry(NP_ENTRY, MusicPage.NowPlaying)
    val queue = MusicEntry(QUEUE_ENTRY, MusicPage.Queue)
    val move = PageMove()
    val mp = MovePaint(move)
    val moves = mutableListOf<LongRange>()
    var toast: Toast? = null
    var toastInk: ToastInk? = null
    var sheet: MenuShot? = null
    var t = 0L
        private set

    private val home = object : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = Unit
    }

    private val status = sc.moveStatus(s)

    private fun queueThumb(): PageThumb = PageThumb(q.fade, d.geo.thumb(q.size, q.track.q) != null, q::poke)

    private fun side(m: MusicEntry?): PageSide? = m?.let { if (it.page == MusicPage.NowPlaying) PageSide(npS, npPaint) else PageSide(s, paint) }

    val st = PageStack<MusicEntry>(
        forms,
        { PageKind.Plain },
        move = move,
        started = { turn -> pageThumbsAtTurn(turn) { if (it == queue) queueThumb() else null } },
        swapped = { top, at -> pageThumbAtSwap(top, at) { if (it == queue) queueThumb() else null } },
        ready = { p -> !isNp(p) || np.shown.track == sc.fake.player.state.value.track },
        isNp = ::isNp,
        gate = InputGate(),
    ) { turn -> mp.ends(turn, HomeSide(s, home, 0f), side(turn.from), side(turn.to), forms) }

    init {
        runBlocking {
            val v = sc.fake.library.view()
            val (hm, hd) = sc.hub(s, HUB_ALBUMS)
            sc.fill(hm, hd)
            val i = requireNotNull(hm.pane.map).indexAt(0, album)
            require(i >= 0) { "ALBUMS cell $album holds no album" }
            val a = v.page(Listing.Albums, i, 1).rows.first()
            sc.fake.player.play(Listing.AlbumTracks(a.id), v.generation, 0, 0L)
            val rows = v.page(Listing.AlbumTracks(a.id), 0, 1 + g.nextTracks).rows
            val track = rows.first()
            np.feed(PlayerState(queueSize = sc.fake.player.state.value.queueSize, index = 0, track = track.id, playing = true, anchor = Anchor(0, RATE, 0, false, track.durationMs ?: 0L)))
            np.sync(NpShown.of(rows), NP_OPEN)
            np.resetFocus(NP_OPEN)
            np.touched(TAP_DOWN)
            q.loaded = true
            q.geometry(RowSpec(d.geo.pitch, d.geo.view))
            q.state(sc.fake.player.state.value)
            q.show(v, 0, d.geo.view / d.geo.pitch + AHEAD)
        }
        st.pages.add(npScreen)
        t = TAP_DOWN
        st.frame(t)
    }

    fun push(at: Long) {
        q.open(sc.fake.player.state.value, at)
        st.push(queue, MoveKind.T3)
    }

    fun knob(row: Int, at: Long) {
        val was = q.track.q
        q.place(row, at, true)
        if (q.aim == was) return
        q.track.at(q.aim)
        moves += at until at + Tok.motion_scroll.toLong()
    }

    fun knobs(n: Int, at: Long) {
        repeat(n) { knob(q.focus.row + 1, at + it * SHEET_KNOB) }
    }

    fun pop() {
        st.pop(MoveKind.T4)
    }

    fun tapOpen(row: Int, at: Long) {
        q.press.tap(at)
        val h = st.popToNp(npScreen, at, NowPlayingModel.HOLD_CAP)
        runBlocking { q.tap(row, sc.source) }
        resync(at)
        runBlocking {
            val p = sc.fake.player.state.value
            val rows = sc.fake.library.view().page(Listing.Queue, p.index, 1 + g.nextTracks).rows
            np.feed(p.copy(anchor = Anchor(0, RATE, 0, false, rows.first().durationMs ?: 0L)))
            np.sync(NpShown.of(rows), at)
        }
        h.done()
    }

    fun resync(at: Long) = runBlocking {
        q.sync(sc.fake.library.view(), sc.fake.player.state.value, at)
    }

    fun run(to: Long, acts: List<Pair<Long, () -> Unit>> = emptyList()) {
        val due = acts.sortedBy { it.first }.toMutableList()
        val times = (generateSequence((t / SHEET_STEP + 1) * SHEET_STEP) { it + SHEET_STEP }.takeWhile { it < to } + due.map { it.first }.filter { it in t..to } + to).toSortedSet()
        for (x in times) {
            while (due.isNotEmpty() && due.first().first <= x) due.removeAt(0).second()
            st.frame(x)
            if (st.phase == Phase.Rest && st.top == queue) q.tick(x, d.geo.thumb(q.size, q.track.q) != null, moves.any { x in it })
            t = x
        }
    }

    fun target(): SheetTarget = requireNotNull(q.target(q.focus.row)) { "queue row ${q.focus.row} has no sheet" }

    fun DrawScope.world(now: Long, rec: Regions?) {
        np.stepTo(now)
        when {
            st.phase != Phase.Rest && st.turn != null -> with(mp) { drawMove(now, rec?.let { groundScaled(it, now) }, if (st.turn?.kind == MoveKind.T7) status else null) }
            st.top == npScreen -> npRest(now, rec)
            else -> queueRest(now, rec)
        }
        val tt = toast ?: return
        trackToast(tt, requireNotNull(toastInk), s, dev.w, now, rec)
    }

    private fun groundScaled(rec: Regions, now: Long): DrawRecord {
        val turn = requireNotNull(st.turn)
        val a = when {
            turn.to == queue -> if (move.has(Part.Ground, Prop.A)) move.at(Part.Ground, Prop.A, now) else 0f
            turn.from == queue -> move.at(Part.LeaveGround, Prop.A, now)
            else -> 1f
        }
        return object : DrawRecord by rec {
            override fun thumbParts(name: String, r: ThumbRect, alpha: Float, bands: List<Band>, accent: Color) =
                rec.thumbParts(name, r, alpha, bands.map { Band(it.y0, it.y1, (it.level * a).roundToInt()) }, accent)
        }
    }

    fun showToast(artist: String, title: String, at: Long) {
        val t = Toast().also {
            it.show(at, artist, title, track = true)
            it.frame(ToastPlace.Bottom, toastSurface(s, s.navOn), at)
        }
        toast = t
        toastInk = sc.toastInk(t, s)
    }

    private fun DrawScope.queueRest(now: Long, rec: Regions?) {
        chrome.clearParts()
        pageGround(bands, sc.accent)
        chrome(chrome, now)
        rec?.chrome("page.chrome", chrome, now, size.width.toInt())
        with(paint) {
            rest(now, rec, "page")
            val f = thumbFade(sheet?.over?.start ?: now)
            if (f > 0f) thumb(f, rec, "page")
        }
    }

    private fun DrawScope.npRest(now: Long, rec: Regions?) {
        if (rec != null) {
            rec.edges("np.art", Kind.Art, Edges(g.artX, g.artY, g.artX + g.artSide, g.artY + g.artSide), Ink.tile, 1f, peakOff = ART_PEAK)
            val b = g.bar
            rec.edges("np.bar", Kind.Bar, Edges(b.x0, b.y0, b.x1, b.y1), Ink.white, Tok.npBarTrack)
        }
        npPage(g, npFrame(np, g, now, false), inks.ink(now), inks.time(now, 0L), null, null, sc.accent, false, false, rec)
    }

    companion object {
        const val NP_ENTRY = 1L
        const val QUEUE_ENTRY = 2L
        const val QUEUE_ALBUM_CELL = 2
        const val RATE = 48000
        const val AHEAD = 2
        const val NP_OPEN = 4240L
        const val TAP_DOWN = 5240L
        const val TAP_UP = 5290L
    }
}
