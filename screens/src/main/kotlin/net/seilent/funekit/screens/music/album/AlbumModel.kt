package net.seilent.funekit.screens.music.album

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import net.seilent.funekit.fx
import net.seilent.funekit.fxDiv
import net.seilent.funekit.fxInt
import net.seilent.funekit.fxMul
import net.seilent.funekit.fxOfPx
import net.seilent.funekit.fxU
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.keptU
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.FocusTrack
import net.seilent.funekit.kit.GlowPair
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.PressSlots
import net.seilent.funekit.kit.RowModel
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.ThumbFade
import net.seilent.funekit.kit.ThumbRect
import net.seilent.funekit.kit.art.Art
import net.seilent.funekit.kit.art.ArtKey
import net.seilent.funekit.kit.art.cover
import net.seilent.funekit.kit.focusAfterScroll
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.rolePx
import net.seilent.funekit.kit.scrollInto
import net.seilent.funekit.kit.thumbOf
import net.seilent.funekit.kit.touch.ListTouch
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.kit.touch.RowTrack
import net.seilent.funekit.px
import net.seilent.funekit.px64
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.Paging
import net.seilent.funekit.screens.music.nowplaying.npTime
import net.seilent.funekit.screens.music.staleIn

class AlbumGeo(val s: Scale) {
    val ppu64: Long = fx(s.ppu)
    private val std64: Long = fx(s.stdPpu)

    private fun kept(v: Long): Long = if (ppu64 == std64) v else fxDiv(fxInt(px64(v, std64)), ppu64)

    private val shrink: Long = kept(fx(Tok.tileL) - fx(Tok.tileS))
    private val dockU: Long = fx(Tok.albumArtTop) - fx(Tok.npTextTop) + fxMul(fx(Tok.capHeight), fx(Tok.type_npArtist_size))
    val lift64: Long = kept(fx(Tok.tileL) - fx(Tok.tileS) + dockU)
    val top: Int = px64(keptU(Tok.albumRowsTop, s), ppu64)
    val pitch: Int = Rows.pitch(s, false)
    val above: Int = px64(lift64, ppu64)
    val bottom: Int = px(s.stdContentBottom, s.stdPpu)
    val view: Int = bottom - top
    val artX: Int = px(Tok.albumArtX, s.stdPpu)
    val grid: Int = px(Tok.gridTile, s.stdPpu)
    val large: Int = px(Tok.tileL, s.stdPpu)
    val edge: Int = px(Tok.edge, s.stdPpu)
    val clipRight: Int = s.widthPx - edge
    val artistBase: Int = px(Tok.npTextTop, s.stdPpu)
    val albumBase: Int = px(Tok.npAlbumBaseline, s.stdPpu)
    val pivotTop: Int = min(px(s.board.pivotTop, s.stdPpu), s.heightPx)
    private val rise: Long = max(1L, kept(fx(Tok.albumArtTop) - fx(Tok.npAlbumBaseline)) - fxDiv(fxInt(Metrics.descent(rolePx(Roles.npAlbum, s, s.stdPpu))), ppu64))

    fun spec(): RowSpec = RowSpec(pitch, view, above, collapse = true)

    fun liftU(liftPx: Int): Long = fxOfPx(liftPx.toFloat(), ppu64)

    fun dock(l: Long): Long = max(0L, l - shrink)

    fun side(l: Long): Long = kept(fx(Tok.tileL)) - min(l, shrink)

    fun artTop(l: Long): Long = kept(fx(Tok.albumArtTop)) - dock(l)

    fun rowsTop(l: Long): Long = artTop(l) + side(l) + kept(fx(Tok.albumRowsGap))

    fun textX(l: Long): Long {
        val end = max(1L, rise)
        return kept(fx(Tok.edge)) + kept(fx(Tok.albumArtX) + fx(Tok.tileS) + fx(Tok.tileLGutter) - fx(Tok.edge)) * min(dock(l), end) / end
    }

    fun textX64(liftPx: Int): Int = pen64(fxU(textX(liftU(liftPx))), s.ppu)

    fun textXPx(liftPx: Int): Int = px64(textX(liftU(liftPx)), fx(s.ppu))

    fun textClip(liftPx: Int): Int = px64(textX(liftU(liftPx)), ppu64)

    fun artRect(liftPx: Int): Edges {
        val l = liftU(liftPx)
        val y = px64(artTop(l), ppu64)
        val side = px64(artTop(l) + side(l), ppu64) - y
        return Edges(artX, y, artX + side, y + side)
    }

    fun rowsTopPx(liftPx: Int): Int = px64(rowsTop(liftU(liftPx)), ppu64)

    fun thumb(t: RowTrack): ThumbRect? {
        if (t.max <= above) return null
        val lift = t.lift
        val y = top - lift
        return thumbOf(s, y, bottom, (bottom - y).toLong(), t.topOf(t.count).toLong(), (t.q - lift).toLong())
    }
}

data class AlbumLook(val alpha: Float, val focused: Boolean, val glow: Float?, val back: Boolean = false)

data class AlbumBlock(val sideU: Long, val art: ImageBitmap?, val gone: Boolean)

class AlbumModel(entry: Long, val id: Long) : PageModel(entry, MusicPage.Album(id)) {
    var head: Row? by mutableStateOf(null)
    var gone by mutableStateOf(false)
        private set
    var paging by mutableStateOf(Paging(Listing.AlbumTracks(id)))
        private set
    var total by mutableIntStateOf(-1)
        private set
    var loads by mutableIntStateOf(0)
        private set
    var gen = -1L
        private set
    val track = RowTrack(1, 0)
    var ppu = 0f
    var counted = -1
    val focus = FocusTrack().also { it.attach() }
    val press = PressSlots()
    val glow = GlowPair()
    val fade = ThumbFade()
    var playing by mutableStateOf<Long?>(null)
    var hidden by mutableStateOf(false)
    val art = mutableStateMapOf<Int, ImageBitmap?>()
    var arts by mutableIntStateOf(0)
        private set
    var aim by mutableIntStateOf(-1)
        private set
    var aims by mutableIntStateOf(0)
        private set
    var geo: AlbumGeo? = null
    override var lists: List<ListTouch> = emptyList()
    val wake = Channel<Unit>(Channel.CONFLATED)

    fun poke() {
        wake.trySend(Unit)
    }

    fun row(i: Int): Row? = paging.window?.row(i)

    val count: Int get() = if (gone) 0 else max(0, total)

    override val ready: Boolean
        get() = gone || (total >= 0 && (total == 0 || row(track.drawFirst) != null))

    fun geometry(g: AlbumGeo) {
        geo = g
        track.resize(g.spec())
    }

    fun target(i: Int): SheetTarget? {
        val r = row(i) ?: return null
        return SheetTarget(SheetKind.Song, r.title, Listing.AlbumTracks(id), gen, i, r.id, Kind.Song, r.id, from = SheetFrom.Album)
    }

    fun rowModel(i: Int): RowModel? {
        val r = row(i) ?: return null
        return RowModel(primary = r.title, number = (i + 1).toString(), trailing = r.durationMs?.takeIf { it > 0 }?.let(::npTime).orEmpty(), accent = playing == r.id)
    }

    fun look(i: Int, now: Long, focusShown: Boolean): AlbumLook {
        focus.revision
        press.revision
        val alpha = focus.rowAlpha(i, now, Tok.type_listPrimary_alpha, Tok.type_listPrimary_alphaDim)
        val back = glow.glow.key != i && glow.back.key == i
        return AlbumLook(alpha, focusShown && i == focus.row, if (glow.glow.key == i || back) press.lit(i, now) else null, back)
    }

    fun image(px: Int): ImageBitmap? {
        arts
        return art[px]
    }

    fun block(liftPx: Int): AlbumBlock {
        val g = geo ?: return AlbumBlock(0L, null, gone)
        val side = g.side(g.liftU(liftPx))
        val sidePx = g.artRect(liftPx).w
        val img = if (sidePx <= g.grid) image(g.grid) ?: image(g.large) else image(g.large) ?: image(g.grid)
        return AlbumBlock(side, if (hidden || gone) null else img, gone)
    }

    fun source(liftPx: Int): Int {
        val g = geo ?: return 0
        return if (g.artRect(liftPx).w <= g.grid && image(g.grid) != null) g.grid else if (image(g.large) != null) g.large else g.grid
    }

    fun animating(now: Long): Boolean = focus.animating(now) || press.staleIn(track.drawFirst, track.rowAt(track.q + track.view - 1), 1, now) || glow.glow.moving(now) || glow.back.moving(now) || fade.fading(now)

    fun wakeAt(now: Long): Long? = listOfNotNull(press.due(now), fade.wakeAt(now).takeIf { it >= 0 }).minOrNull()

    fun tick(now: Long, thumb: Boolean, moving: Boolean) {
        fade.track(thumb, moving, now)
        for (g in listOf(glow.glow, glow.back)) {
            val k = g.key ?: continue
            if (!press.framed(k, now) && press.lit(k, now) == 0f) g.clear()
        }
    }

    suspend fun load(v: LibraryView) {
        val l = Listing.AlbumTracks(id)
        total = v.page(l, 0, 0).total
        gen = v.generation
        loads++
    }

    fun pageView(src: MusicSource): LibraryView = src.library.view(gen)

    suspend fun show(v: LibraryView, first: Int, last: Int) {
        if (gone || total <= 0) return
        paging.show(v, max(0, first), last)
    }

    suspend fun migrate(v: LibraryView, now: Long): Boolean {
        val at = v.indexOf(Listing.Albums, Kind.Album, id)
        if (at == null) {
            gone = true
            total = 0
            gen = v.generation
            paging = Paging(Listing.AlbumTracks(id))
            focus.set(0, 0, now, false)
            press.reset()
            glow.off()
            loads++
            return false
        }
        v.page(Listing.Albums, at, 1).rows.firstOrNull()?.let { head = it }
        gone = false
        val key = row(focus.row)?.id
        val off = track.topOf(focus.row) - track.q
        val l = Listing.AlbumTracks(id)
        total = v.page(l, 0, 0).total
        gen = v.generation
        paging = Paging(l)
        paging.show(v, 0, max(0, total - 1))
        val w = paging.window
        val r = (key?.let { k -> w?.rows?.indexOfFirst { it.id == k }?.takeIf { it >= 0 }?.plus(w.offset) } ?: focus.row).coerceIn(0, max(0, total - 1))
        press.reset()
        glow.off()
        if (total > 0) {
            focus.set(r, total, now, false)
            track.count(total, r)
            track.at(track.topOf(r) - off)
        }
        loads++
        return true
    }

    suspend fun loadArt(v: LibraryView, cache: Art, ref: ArtRef?, px: Int) {
        if (ref == null || px in art) return
        val b = try {
            cache.cover(ref, px) { v.art(ref, px) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        art[px] = b?.asImageBitmap()
        arts++
    }

    fun tileArt(cache: Art, ref: ArtRef?, tile: Int): ImageBitmap? {
        val g = geo ?: return null
        if (ref == null) return null
        val l = cache[ArtKey.Cover(ref, g.large)]
        if (l != null && g.large > tile) return l.asImageBitmap()
        return cache[ArtKey.Cover(ref, g.grid)]?.asImageBitmap()
    }

    fun play(state: PlayerState) {
        playing = state.track
    }

    fun afterScroll(now: Long) {
        if (count == 0) return
        val r = focusAfterScroll(focus.row, track)
        if (r != focus.row) focus.set(r, count, now, false)
    }

    fun intoView(i: Int): Int = scrollInto(track.topOf(i), track.topOf(i + 1) - track.topOf(i), track)

    fun aimTo(q: Int) {
        aim = q
        aims++
    }

    suspend fun tap(i: Int, src: MusicSource): Boolean {
        if (gone || row(i) == null) return false
        src.player.play(Listing.AlbumTracks(id), gen, i, 0L)
        return true
    }

    private var kept: List<*>? = null

    override fun state(): List<Any>? = if (ppu > 0f) listOf(focus.row, track.q / ppu) else null

    override fun restoreState(v: List<*>) {
        kept = v
    }

    fun restoreAt(now: Long) {
        val v = kept ?: return
        if (count == 0 || ppu <= 0f) return
        kept = null
        val row = v.getOrNull(0) as? Int ?: return
        val off = v.getOrNull(1) as? Float ?: return
        lists.firstOrNull()?.toPx(px(off, ppu)) ?: track.at(px(off, ppu))
        focus.set(row.coerceIn(0, count - 1), count, now, false)
    }

    override fun drop() {
        lists = emptyList()
        art.clear()
    }
}
