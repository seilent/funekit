package net.seilent.funekit.screens.music.artist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import net.seilent.funekit.Scale
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptU
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.GlowPair
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.art.cover
import net.seilent.funekit.kit.focusAfterScroll
import net.seilent.funekit.kit.listThumbOf
import net.seilent.funekit.kit.motion.PivotMotion
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Player
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.TrackIds
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.Paging
import net.seilent.funekit.screens.music.PivotPage
import net.seilent.funekit.screens.music.overlay.SONGS_ADDED
import net.seilent.funekit.screens.music.overlay.SONG_ADDED
import net.seilent.funekit.screens.music.pivotSwitchEnd
import net.seilent.funekit.screens.music.pivots
import net.seilent.funekit.screens.music.staleIn

const val ARTIST_ALBUMS = 0
const val ARTIST_SONGS = 1
const val RING_PLAY = 0
const val RING_ADD = 1
const val RING_SHUFFLE = 2

suspend fun artistAdd(p: Player, am: ArtistModel): String? {
    val before = p.state.value.queueSize
    p.enqueue(Listing.ArtistTracks(am.id), am.gen)
    val added = p.state.value.queueSize - before
    return if (added <= 0) null else if (added == 1) SONG_ADDED else SONGS_ADDED
}

suspend fun artistPlay(p: Player, am: ArtistModel, ring: Int) = p.play(Listing.ArtistTracks(am.id), am.gen, -1, 0L, ring == RING_SHUFFLE)
const val RINGS = 3

class ArtistGeo(val s: Scale) {
    private val sp = s.stdPpu
    val ppu64: Long = fx(s.ppu)
    val top64: Long = keptU(s.board.detailRowsTop, s)
    val boxTop: Int = px64(top64, ppu64)
    val clipTop: Int = px(s.board.detailRowsTop, sp)
    val bottom: Int = px(s.stdContentBottom, sp)
    val view: Int = bottom - boxTop
    val albumPitch: Int = px64(keptU(Tok.detailRow, s), ppu64)
    val ring: Int = px(Tok.detailBlockRing, sp)
    val ringTop: Int = px(s.board.detailRingTop, sp)
    val nameBase: Int = px(s.board.detailNameBaseline, s.ppu)
    val nameX: Int = px(Tok.hubEdge, s.ppu)
    val labelBase: Int = px(s.board.detailLabelBaseline, sp)
    val labelX: Int = px(Tok.hubEdge, sp)
    val side: Int = px(Tok.tileL, sp)
    val artX: Int = px(Tok.hubEdge, sp)
    val rowRing: Int = px(Tok.detailRing, sp)
    val rowInset: Int = px(Tok.detailRingInset, sp)
    val textX: Int = px(Tok.detailTextX, sp)
    val right: Int = s.widthPx - px(Tok.hubEdge, sp)
    val pivotTop: Int = px(s.board.pivotTop, sp)
    val contentTop: Int = px(s.board.page_contentTop, sp)

    fun ringX(i: Int): Float = when (i) {
        RING_PLAY -> Tok.detailRingX0
        RING_ADD -> Tok.detailRingX1
        else -> Tok.detailRingX2
    }

    fun ringCx(i: Int): Int = px(ringX(i), sp) + ring / RING_HALF

    val ringCy: Int get() = ringTop + ring / RING_HALF

    fun spec(p: Int): RowSpec = if (p == ARTIST_SONGS) RowSpec.of64(fx(Tok.rowSong), s, view, top64 = top64) else RowSpec(albumPitch, view)

    fun ringAt(x: Float, y: Float): Int {
        val half = (Tok.detailBlockRing + Tok.detailRingGap) / RING_HALF
        val cy = s.board.detailRingTop + Tok.detailBlockRing / RING_HALF
        val sx = x / sp
        val sy = y / sp
        for (i in 0 until RINGS) {
            val cx = ringX(i) + Tok.detailBlockRing / RING_HALF
            if (sx >= cx - half && sx < cx + half && sy >= cy - half && sy < cy + half) return i
        }
        return -1
    }

    fun rowTop(t: RowTrack, r: Int): Int = boxTop + t.topOf(r) - t.q

    fun thumb(t: RowTrack): ThumbRect? = listThumbOf(s, boxTop, bottom, t)

    fun openRect(): Edges = Edges(artX, boxTop, artX + side, boxTop + side)

    companion object {
        const val RING_HALF = 2
    }
}

class ArtistPane(val pivot: Int, val listing: Listing) {
    var paging by mutableStateOf(Paging(listing))
        private set

    fun clear() {
        paging = Paging(listing)
    }
    var total by mutableIntStateOf(-1)
    val track = RowTrack(1, 0)
    var ppu = 0f
    var counted = -1
    val focus = FocusTrack().also { it.attach() }
    val press = PressSlots()
    val glow = GlowPair()

    val count: Int get() = max(0, total)

    fun row(i: Int): Row? = paging.window?.row(i)
}

data class ArtistLook(val alpha: Float, val focused: Boolean, val glow: Float?, val lit: Float, val ring: Float, val back: Boolean = false)

data class RingLook(val focused: Boolean, val lit: Float)

class ArtistModel(entry: Long, val id: Long) : PivotPage(entry, MusicPage.Artist(id)) {
    val panes = listOf(ArtistPane(ARTIST_ALBUMS, Listing.ArtistAlbums(id)), ArtistPane(ARTIST_SONGS, Listing.ArtistTracks(id)))
    val pane: ArtistPane get() = panes[pivot]
    var name by mutableStateOf<String?>(null)
    var gone by mutableStateOf(false)
        private set
    var gen = -1L
        private set
    var loads by mutableIntStateOf(0)
        private set
    var onRings by mutableStateOf(true)
    var ring by mutableIntStateOf(RING_PLAY)
    private var wasOnRings = true
    private var wasRing = -1
    val rings = PressSlots()
    val fade = ThumbFade()
    var switchFade = 1f
    val art = mutableStateMapOf<ArtKey, ImageBitmap?>()
    var playing by mutableStateOf<TrackIds?>(null)
    var track by mutableStateOf<Long?>(null)
    var hiddenAlbum by mutableLongStateOf(-1L)
    var hiddenRing by mutableStateOf(false)
    var switchAt: Long? by mutableStateOf(null)
        private set
    var switchFrom by mutableIntStateOf(0)
        private set
    override var switchDir by mutableIntStateOf(1)
        private set
    val motion = PivotMotion()
    var drags by mutableIntStateOf(0)
        private set
    var geo: ArtistGeo? = null
    override var lists: List<ListTouch> = emptyList()
    val wake = Channel<Unit>(Channel.CONFLATED)

    override fun poke() {
        wake.trySend(Unit)
    }

    override val ready: Boolean
        get() {
            val p = pane
            return gone || (p.total >= 0 && (p.total == 0 || p.row(p.track.drawFirst) != null))
        }

    fun geometry(g: ArtistGeo) {
        geo = g
        for (p in panes) p.track.resize(g.spec(p.pivot))
    }

    suspend fun load(v: LibraryView) {
        val at = v.indexOf(Listing.Artists, Kind.Artist, id)
        if (at == null) {
            empty()
        } else {
            gone = false
            name = v.page(Listing.Artists, at, 1).rows.firstOrNull()?.title ?: name
            for (p in panes) p.total = v.page(p.listing, 0, 0).total
        }
        gen = v.generation
        if (pane.count == 0) onRings = true
        loads++
    }

    private fun empty() {
        gone = true
        for (p in panes) {
            p.total = 0
            p.clear()
        }
        onRings = true
    }

    suspend fun migrate(v: LibraryView, now: Long) {
        val keys = panes.map { p -> p.row(p.focus.row)?.id }
        load(v)
        for ((i, p) in panes.withIndex()) {
            p.press.reset()
            p.glow.off()
            if (p.count == 0) continue
            val r = (keys[i]?.let { v.indexOf(p.listing, if (p.pivot == ARTIST_ALBUMS) Kind.Album else Kind.Song, it) } ?: p.focus.row).coerceIn(0, p.count - 1)
            p.paging.show(v, r, r)
            p.focus.set(r, p.count, now, false)
        }
        if (pane.count == 0) onRings = true
    }

    fun pageView(src: MusicSource): LibraryView = src.library.view(gen)

    suspend fun show(v: LibraryView, p: Int, first: Int, last: Int) {
        val pn = panes[p]
        if (pn.count == 0) return
        pn.paging.show(v, max(0, first), last)
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

    fun keepArt(keys: Set<ArtKey>) {
        for (k in art.keys.toList()) if (k !in keys) art.remove(k)
    }

    fun songModel(i: Int): RowModel? {
        val r = panes[ARTIST_SONGS].row(i) ?: return null
        return RowModel(primary = r.title, secondary = r.sub, album = r.album.orEmpty(), song = true, accent = track == r.id)
    }

    fun litAlbum(id: Long): Boolean = playing?.album == id

    fun look(p: Int, i: Int, now: Long, hideFocus: Boolean): ArtistLook {
        val pn = panes[p]
        pn.focus.revision
        pn.press.revision
        val on = if (p == ARTIST_ALBUMS) Tok.alpha_full else Tok.type_listPrimary_alpha
        val off = if (p == ARTIST_ALBUMS) Tok.alpha_high else Tok.type_listPrimary_alphaDim
        val alpha = if (onRings) off else pn.focus.rowAlpha(i, now, on, off)
        val focused = !onRings && !hideFocus && i == pn.focus.row
        val back = pn.glow.glow.key != i && pn.glow.back.key == i
        val lit = pn.press.lit(i, now)
        return ArtistLook(alpha, focused, if (pn.glow.glow.key == i || back) lit else null, lit, pn.press.ring(i, now), back)
    }

    fun ringLook(i: Int, now: Long, hideFocus: Boolean): RingLook {
        rings.revision
        return RingLook(onRings && ring == i && !hideFocus, rings.lit(i, now))
    }

    fun target(p: Int, i: Int): SheetTarget? {
        val r = panes[p].row(i) ?: return null
        return if (p == ARTIST_ALBUMS) {
            SheetTarget(SheetKind.Album, r.title, Listing.AlbumTracks(r.id), gen, pin = Kind.Album, id = r.id, from = SheetFrom.Artist)
        } else {
            SheetTarget(SheetKind.Song, r.title, Listing.ArtistTracks(id), gen, i, r.id, Kind.Song, r.id, from = SheetFrom.Artist)
        }
    }

    fun ringTarget(i: Int): SheetTarget? {
        val n = name ?: return null
        if (i !in 0 until RINGS) return null
        return SheetTarget(SheetKind.Artist, n, Listing.ArtistTracks(id), gen, pin = Kind.Artist, id = id, from = SheetFrom.Artist)
    }

    fun ringDown(i: Int, now: Long) {
        keepRings()
        onRings = true
        ring = i
        rings.down(i, now)
    }

    fun listDown() {
        keepRings()
        onRings = false
    }

    private fun keepRings() {
        wasOnRings = onRings
        wasRing = ring
    }

    fun albumRow(album: Long): Int {
        val pn = panes[ARTIST_ALBUMS]
        val w = pn.paging.window ?: return -1
        val i = w.rows.indexOfFirst { it.id == album }
        return if (i < 0) -1 else i + w.offset
    }

    fun rectOf(album: Long, at: ArtistGeo? = geo): Edges? {
        val g = at ?: return null
        if (pivot != ARTIST_ALBUMS) return null
        val pn = panes[ARTIST_ALBUMS]
        if (pn.paging.window == null) return g.openRect()
        val r = albumRow(album)
        if (r < 0) return null
        val top = g.rowTop(pn.track, r)
        return Edges(g.artX, top, g.artX + g.side, top + g.side)
    }

    override fun cell(album: Long): Boolean {
        val g = geo ?: return false
        if (pivot != ARTIST_ALBUMS || panes[ARTIST_ALBUMS].paging.window == null) return false
        val e = rectOf(album) ?: return false
        return e.y0 >= g.clipTop && e.y1 <= g.bottom
    }

    fun animating(now: Long): Boolean {
        val pn = pane
        return switching(now) || motion.active(now) || pn.focus.animating(now) || pn.press.staleIn(pn.track.drawFirst, pn.track.rowAt(pn.track.q + pn.track.view - 1), 1, now) ||
            rings.stale(now) || pn.glow.glow.moving(now) || pn.glow.back.moving(now) || fade.fading(now)
    }

    fun wakeAt(now: Long): Long? = listOfNotNull(pane.press.due(now), rings.due(now), fade.wakeAt(now).takeIf { it >= 0 }).minOrNull()

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        fade.track(thumb, moving, now)
        val pn = pane
        for (g in listOf(pn.glow.glow, pn.glow.back)) {
            val k = g.key ?: continue
            if (!pn.press.framed(k, now) && pn.press.lit(k, now) == 0f) g.clear()
        }
    }

    fun switching(now: Long): Boolean = switchAt?.let { now - it < pivotSwitchEnd() } == true

    fun thumbAlpha(now: Long): Float {
        if (switchAt == null || !switching(now)) return fade.at(now)
        return (if (motion.thumbIn(now)) fade.at(now) else switchFade) * motion.thumbAlpha(now)
    }

    override fun canSwitch(d: Int): Boolean = pivot + d in MusicPage.Artist(id).pivots.indices

    override fun motionActive(now: Long): Boolean = motion.active(now)

    override fun haltList() {
        lists.getOrNull(pivot)?.halt()
    }

    override fun switchTo(p: Int, now: Long): Boolean {
        val to = p.coerceIn(0, MusicPage.Artist(id).pivots.size - 1)
        if (to == pivot) return false
        val from = if (motion.dragging) motion.offset else 0f
        pane.press.reset()
        pane.glow.off()
        switchFrom = pivot
        switchDir = if (to > pivot) 1 else -1
        pivot = to
        switchFade = fade.at(now)
        fade.show(now)
        switchAt = now
        motion.begin(now, switchDir, from)
        if (pane.count == 0) onRings = true
        return true
    }

    override fun land() {
        switchAt = null
        motion.end()
    }

    override fun drag(dxU: Float, now: Long) {
        if (!motion.dragging && motion.active(now)) land()
        motion.drag(dxU)
        drags++
    }

    override fun release(now: Long) {
        if (!motion.dragging) return
        motion.release(now)
        drags++
    }

    override fun unpress(now: Long) {
        if (wasRing >= 0) {
            onRings = wasOnRings
            ring = wasRing
            wasRing = -1
        }
        pane.focus.unpress(pane.count, now)
    }

    override fun dropUnpress() {
        wasRing = -1
        panes.forEach { it.focus.dropUnpress() }
    }

    override fun dragX(now: Long, ppu: Float): Int = if (switching(now)) 0 else motion.dx(now, ppu)

    fun afterScroll(now: Long) {
        val pn = pane
        pn.focus.dropUnpress()
        if (pn.count == 0) return
        val r = focusAfterScroll(pn.focus.row, pn.track)
        if (r != pn.focus.row) pn.focus.set(r, pn.count, now, false)
    }

    private var kept: List<*>? = null

    override fun state(): List<Any>? {
        if (panes.any { it.ppu <= 0f }) return null
        return listOf(onRings, ring) + panes.flatMap { listOf(it.focus.row, it.track.q / it.ppu) }
    }

    override fun restoreState(v: List<*>) {
        kept = v
        onRings = v.getOrNull(0) as? Boolean ?: true
        ring = (v.getOrNull(1) as? Int)?.coerceIn(0, RINGS - 1) ?: RING_PLAY
    }

    fun restoreAt(now: Long) {
        val v = kept ?: return
        if (panes.any { it.total < 0 || it.ppu <= 0f }) return
        kept = null
        val f = v.iterator()
        f.next()
        f.next()
        for ((i, p) in panes.withIndex()) {
            val row = (if (f.hasNext()) f.next() else null) as? Int ?: return
            val off = (if (f.hasNext()) f.next() else null) as? Float ?: return
            if (p.count == 0) continue
            lists.getOrNull(i)?.toPx(px(off, p.ppu)) ?: p.track.at(px(off, p.ppu))
            p.focus.set(row.coerceIn(0, p.count - 1), p.count, now, false)
        }
        if (pane.count == 0) onRings = true
    }

    override fun drop() {
        lists = emptyList()
        art.clear()
    }
}
