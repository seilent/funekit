package net.seilent.funekit.screens.music.albcol

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.Roles
import net.seilent.funekit.Scale
import net.seilent.funekit.core.Section
import net.seilent.funekit.fx
import net.seilent.funekit.fxMul
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptExact
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.JumpGrid
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.art.cover
import net.seilent.funekit.kit.focusAfterScroll
import net.seilent.funekit.kit.listThumbOf
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.Paging
import net.seilent.funekit.screens.music.artist.ArtistGeo
import net.seilent.funekit.screens.music.hub.HubMap
import net.seilent.funekit.screens.music.hub.NO_CELL
import net.seilent.funekit.screens.music.hub.hubMap
import net.seilent.funekit.screens.music.hub.letterOf
import net.seilent.funekit.screens.music.staleIn

class AlbumColGeo(val s: Scale) {
    val ppu64: Long = fx(s.ppu)
    private val std64: Long = fx(s.stdPpu)
    private val sp: Float = s.stdPpu
    val top64: Long = keptExact(Tok.albcolTop, s)
    val pitch64: Long = keptExact(Tok.albcolPitch, s)
    val boxTop: Int = px64(top64, ppu64)
    val clipTop: Int = px(Tok.albcolTop, sp)
    val bottom: Int = px(s.stdContentBottom, sp)
    val view: Int = bottom - boxTop
    val side: Int = px(Tok.tileL, sp)
    val ring: Int = px(Tok.albcolRing, sp)
    val ringX: Int = side - px(Tok.albcolRingInset, sp)
    val ringDrop: Int = px(Tok.albcolRingDrop, sp)
    private val ringR: Int = ring / ArtistGeo.RING_HALF + px((Tok.rowPlayRing - Tok.rowRing) / ArtistGeo.RING_HALF, sp)
    val textX: Int = side + px(Tok.albcolTextGap, sp)
    val right: Int = s.widthPx - px(Tok.edge, sp)
    val titleBase: Int = px64(fx(Tok.albcolTitleTop) + fxMul(fx(Tok.capHeight), fx(Tok.type_albcolTitle_size)), std64)
    val titleLines: Int = Tok.albcolTitleLines.toInt()
    val trackDrop: Int = px(s.board.albcolTrackDrop, sp)
    val trackPitch: Int = px(s.board.albcolTrackPitch, sp)
    val tail: Int = if (s.board.albcolTrackTail != 0f) Metrics.descent(rolePx(Roles.sectionLabel, s, sp)) else 0
    val tile: Int = px(Tok.albcolLetter, sp)

    fun spec(): RowSpec = RowSpec.of64(pitch64, s, view, top64 = top64)

    fun titleAt(i: Int): Int = titleBase + px64(fx(Tok.albcolTitlePitch) * i, std64)

    fun artistAt(lines: Int): Int = titleBase + px64(fx(Tok.albcolTitlePitch) * (max(1, lines) - 1) + fx(Tok.albcolArtistPitch), std64)

    fun tracksFit(lines: Int): Int {
        var y = artistAt(lines) + trackDrop
        var n = 0
        while (y + tail <= side) {
            n++
            y += trackPitch
        }
        return n
    }

    fun letterTile(): Edges = Edges(side - tile, side - tile, side, side)

    fun rowTop(t: RowTrack, r: Int): Int = boxTop + t.topOf(r) - t.q

    fun ringAt(x: Float, y: Float): Boolean {
        val dx = x - ringX
        val dy = y - ringDrop
        return dx * dx + dy * dy <= ringR * ringR
    }

    fun thumb(t: RowTrack): ThumbRect? = listThumbOf(s, boxTop, bottom, t)
}

data class ColLook(val focused: Boolean, val lit: Float, val ring: Float, val ringLit: Float = 0f)

data class ColEntry(val letter: Int, val row: Row?, val tracks: List<String>, val accent: Boolean, val art: ImageBitmap?, val boxed: Boolean, val hidden: Boolean)

class AlbumColModel(entry: Long, val letter: Int) : PageModel(entry, MusicPage.AlbumCol(letter)) {
    var sections: ByteArray? by mutableStateOf(null)
        private set
    var map: HubMap? by mutableStateOf(null)
        private set
    var paging by mutableStateOf(Paging(Listing.Albums))
        private set
    var gen = -1L
        private set
    val jumpGrid = JumpGrid()
    var loads by mutableIntStateOf(0)
        private set
    val track = RowTrack(1, 0)
    var ppu = 0f
    var counted: HubMap? = null
    val focus = FocusTrack().also { it.attach() }
    val press = PressSlots()
    var ringRow = -1
    val fade = ThumbFade()
    val art = mutableStateMapOf<ArtKey, ImageBitmap?>()
    val tracks = mutableStateMapOf<Long, List<String>>()
    var playing by mutableLongStateOf(-1L)
    var hidden by mutableLongStateOf(-1L)
    var opened = false
        private set
    var geo: AlbumColGeo? = null
    var aim by mutableIntStateOf(-1)
        private set
    var aims by mutableIntStateOf(0)
        private set
    override var lists: List<ListTouch> = emptyList()
    val wake = Channel<Unit>(Channel.CONFLATED)

    internal fun resetPresses() {
        press.reset()
        poke()
    }

    fun poke() {
        wake.trySend(Unit)
    }

    val count: Int get() = map?.count ?: 0

    fun index(r: Int): Int = map?.indexAt(r) ?: -1

    fun row(r: Int): Row? = index(r).takeIf { it >= 0 }?.let { paging.window?.row(it) }

    fun letterAt(r: Int): Int = letterOf(map?.at(r) ?: NO_CELL)

    fun target(r: Int): SheetTarget? {
        if (letterAt(r) >= 0) return null
        val a = row(r) ?: return null
        return SheetTarget(SheetKind.Album, a.title, Listing.AlbumTracks(a.id), gen, pin = Kind.Album, id = a.id, from = SheetFrom.AlbumCol)
    }

    override val ready: Boolean
        get() {
            val m = map ?: return false
            for (r in track.first until m.count) {
                if (letterAt(r) >= 0) continue
                return row(r) != null
            }
            return true
        }

    fun geometry(g: AlbumColGeo) {
        geo = g
        track.resize(g.spec())
    }

    suspend fun load(v: LibraryView) {
        val s = v.sections(Listing.Albums)
        sections = s
        map = hubMap(s)
        gen = v.generation
        loads++
    }

    fun place(r: Int, now: Long) {
        val n = count
        if (n == 0) return
        val at = r.coerceIn(0, n - 1)
        focus.set(at, n, now, false)
        val t = lists.firstOrNull()
        track.count(n, track.first)
        if (t != null) t.toRow(at) else track.toRow(at)
        counted = map
    }

    fun jump(section: Int, now: Long): Boolean {
        val r = map?.rowOf(section) ?: -1
        if (r < 0) return false
        place(r, now)
        poke()
        return true
    }

    fun open(now: Long): Boolean {
        if (opened || map == null) return false
        opened = true
        val n = count
        if (n > 0) focus.set(0, n, now, false)
        if (!jump(letter, now) && n > 0) place(0, now)
        return true
    }

    fun pageView(src: MusicSource): LibraryView = src.library.view(gen)

    suspend fun show(v: LibraryView, firstRow: Int, lastRow: Int) {
        val m = map ?: return
        val r = m.indices(max(0, firstRow), min(m.count - 1, lastRow)) ?: return
        paging.show(v, r.first, r.last)
    }

    suspend fun readTracks(v: LibraryView, firstRow: Int, lastRow: Int) {
        val g = geo ?: return
        val most = g.tracksFit(1)
        if (most <= 0) return
        for (r in max(0, firstRow)..min(count - 1, lastRow)) {
            val a = row(r) ?: continue
            if (a.id in tracks) continue
            tracks[a.id] = v.page(Listing.AlbumTracks(a.id), 0, most).rows.map { it.title }
        }
    }

    suspend fun loadArt(v: LibraryView, cache: Art, ref: ArtRef, px: Int) {
        val k = ArtKey.Cover(ref, px)
        if (k in art) return
        val b = try {
            cache.cover(ref, px) { v.art(ref, px) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        art[k] = b?.asImageBitmap()
    }

    fun keepArt(first: Int, last: Int, side: Int) {
        val keys = HashSet<ArtKey>()
        val ids = HashSet<Long>()
        for (r in max(0, first)..min(count - 1, last)) {
            val a = row(r) ?: continue
            a.art?.let { keys += ArtKey.Cover(it, side) }
            ids += a.id
        }
        for (k in art.keys.toList()) if (k !in keys) art.remove(k)
        for (k in tracks.keys.toList()) if (k !in ids) tracks.remove(k)
    }

    fun entry(r: Int): ColEntry {
        val l = letterAt(r)
        if (l >= 0) return ColEntry(l, null, emptyList(), false, null, false, false)
        val a = row(r) ?: return ColEntry(-1, null, emptyList(), false, null, false, false)
        val side = geo?.side ?: 0
        val k = a.art?.let { ArtKey.Cover(it, side) }
        val img = k?.let { art[it] }
        return ColEntry(-1, a, tracks[a.id].orEmpty(), a.id == playing, img, k == null || (k in art && img == null), a.id == hidden)
    }

    fun look(r: Int, now: Long, focusShown: Boolean): ColLook {
        focus.revision
        press.revision
        if (r == ringRow) return ColLook(focusShown && r == focus.row, 0f, 0f, press.look(r, now))
        return ColLook(focusShown && r == focus.row, press.lit(r, now), press.ring(r, now))
    }

    fun rectOf(album: Long): Edges? {
        val g = geo ?: return null
        val n = count
        if (n == 0) return null
        for (r in track.first..min(n - 1, track.rowAt(track.q + g.view - 1))) {
            if (row(r)?.id != album) continue
            val top = g.rowTop(track, r)
            return Edges(0, top, g.side, top + g.side)
        }
        return null
    }

    override fun cell(album: Long): Boolean {
        val g = geo ?: return false
        val e = rectOf(album) ?: return false
        return e.y0 >= g.clipTop && e.y1 <= g.bottom
    }

    suspend fun migrate(v: LibraryView, now: Long) {
        val first = track.first
        val onLetter = map?.indexAt(first)?.let { it < 0 } == true
        val topKey = if (onLetter) null else (first until count).firstNotNullOfOrNull { row(it) }?.id
        val topSection = map?.sectionAt(first, 0, sections ?: ByteArray(0)) ?: -1
        paging = Paging(Listing.Albums)
        tracks.clear()
        load(v)
        val m = map ?: return
        press.reset()
        if (m.count == 0) return
        val at = topKey?.let { v.indexOf(Listing.Albums, Kind.Album, it) }
        if (at != null) {
            place(m.rowOfIndex(at), now)
            return
        }
        if (topSection >= 0) {
            for (d in 0 until Section.count) {
                for (sec in listOf(topSection - d, topSection + d)) {
                    if (m.has(sec)) {
                        place(m.rowOf(sec), now)
                        return
                    }
                }
            }
        }
        place(0, now)
    }

    fun animating(now: Long): Boolean = focus.animating(now) || press.staleIn(track.drawFirst, track.rowAt(track.q + track.view - 1), 1, now) || fade.fading(now)

    fun wakeAt(now: Long): Long? = listOfNotNull(press.due(now), fade.wakeAt(now).takeIf { it >= 0 }).minOrNull()

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        fade.track(thumb, moving, now)
    }

    fun afterScroll(now: Long) {
        val n = count
        if (n == 0) return
        val r = focusAfterScroll(focus.row, track)
        if (r != focus.row) focus.set(r, n, now, false)
    }

    fun avail(): BooleanArray = map?.avail() ?: BooleanArray(Section.count)

    fun aimTo(q: Int) {
        aim = q
        aims++
    }

    private var kept: List<*>? = null

    override fun state(): List<Any>? = if (ppu > 0f && opened) listOf(focus.row, track.q / ppu) else null

    override fun restoreState(v: List<*>) {
        kept = v
        opened = true
    }

    fun restoreAt(now: Long) {
        val v = kept ?: return
        if (map == null || count == 0 || ppu <= 0f) return
        kept = null
        val row = v.getOrNull(0) as? Int ?: return
        val off = v.getOrNull(1) as? Float ?: return
        lists.firstOrNull()?.toPx(px(off, ppu)) ?: track.at(px(off, ppu))
        focus.set(row.coerceIn(0, count - 1), count, now, false)
    }

    override fun drop() {
        lists = emptyList()
        art.clear()
        tracks.clear()
    }
}
