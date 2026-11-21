package net.seilent.funekit.screens.host

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.api.Row
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.art.configFor
import net.seilent.funekit.kit.art.largeArt
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpAct
import net.seilent.funekit.screens.music.nowplaying.NpFeed
import net.seilent.funekit.screens.music.nowplaying.NpInks
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.Variant

const val FAV_ADDED = "added to favorites"
const val FAV_REMOVED = "removed from favorites"

private val noState = MutableStateFlow(PlayerState())
private val noRevision = MutableStateFlow(0L)

fun playOf(feed: NpFeed?): Play = when (feed) {
    is NpFeed.Own -> if (feed.state.track == null) Play.None else if (feed.state.playing) Play.Playing else Play.Paused
    is NpFeed.Phone -> if (feed.track.playing) Play.Playing else if (feed.track.paused) Play.Paused else Play.None
    null -> Play.None
}

fun nextRepeat(r: Repeat): Repeat = when (r) {
    Repeat.Off -> Repeat.All
    Repeat.All -> Repeat.One
    Repeat.One -> Repeat.Off
}

fun npHasTrack(np: NowPlayingModel): Boolean = np.shown.track != null

private suspend fun <T> quietly(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

suspend fun queueRows(source: MusicSource, s: PlayerState, next: Int): Pair<Long?, List<Row>> {
    if (s.index < 0) return null to emptyList()
    val from = if (s.index > 0) s.index - 1 else 0
    val got = source.viewed { it.page(Listing.Queue, from, s.index - from + 1 + next).rows }
    val prev = if (s.index > 0) got.firstOrNull()?.id else null
    val rows = got.drop(s.index - from).take(1 + next).toMutableList()
    val wrap = minOf(next, s.queueSize - 1) - (rows.size - 1)
    if (s.repeat == Repeat.All && rows.isNotEmpty() && wrap > 0) rows += source.viewed { it.page(Listing.Queue, 0, wrap).rows }.take(wrap)
    return prev to rows
}

internal suspend fun npMark(np: NowPlayingModel, l: Lyrics, track: Long): Boolean {
    if (!np.lyricsView) return l.lyrics(track) != null
    np.lyrics.read(l, track)
    return np.lyrics.has(track)
}

@Composable
fun NowPlayingFeed(np: NowPlayingModel, g: NpLayout, src: MusicSource?, phone: PhoneSession?, now: () -> Long): NpFeed? {
    val own by (src?.player?.state ?: noState).collectAsState()
    val lyricsRevision by (src?.lyrics?.revision ?: noRevision).collectAsState()
    val favoritesRevision by (src?.favorites?.revision ?: noRevision).collectAsState()
    val feed = Variant.pick(if (src != null) own else null, phone?.track)
    LaunchedEffect(feed, src) {
        when (feed) {
            is NpFeed.Own -> {
                val s = feed.state
                np.feed(s)
                val source = src ?: return@LaunchedEffect
                val (prev, rows) = quietly { queueRows(source, s, g.nextTracks) } ?: return@LaunchedEffect
                np.prevId = prev
                val n = NpShown.of(rows)
                if (np.shown.track == null) np.sync(n, now()) else np.changed(n, now())
            }
            is NpFeed.Phone -> {
                np.feed(feed.track)
                val n = NpShown.of(feed.track)
                if (np.shown.track == null) np.sync(n, now()) else np.changed(n, now())
            }
            null -> np.clear()
        }
    }
    val track = np.shown.track
    LaunchedEffect(track, src, np.variant, lyricsRevision, favoritesRevision) {
        val source = src
        if (track == null || source == null || np.variant) {
            np.favorite = false
            np.mark = false
            return@LaunchedEffect
        }
        np.favorite = quietly { source.favorites.has(track) } == true
        np.mark = quietly { npMark(np, source.lyrics, track) } == true
    }
    val ref = np.shown.art
    val phoneArt = phone?.track?.art
    LaunchedEffect(track, ref, g.artSide, phoneArt, np.variant) {
        val key = listOf(track, ref, g.artSide, phoneArt, np.variant)
        if (np.artKey == key) return@LaunchedEffect
        val source = src
        val img = when {
            np.variant -> phoneArt?.asImageBitmap()
            ref == null || source == null -> null
            else -> {
                val config = configFor(Build.VERSION.SDK_INT, true)
                val got = quietly { withContext(Dispatchers.IO) { listOf(largeArt(g.artSide, config) { source.viewed { it.art(ref, g.artSide) } }) } }
                if (got == null) {
                    np.art = null
                    return@LaunchedEffect
                }
                got.single()?.asImageBitmap()
            }
        }
        np.art = img
        np.artKey = key
    }
    return feed
}

suspend fun npSkip(d: Int, np: NowPlayingModel, src: MusicSource?, phone: PhoneSession?) {
    if (np.variant) {
        val p = phone ?: return
        p.skip(d)
        return
    }
    val player = src?.player ?: return
    if (d > 0) player.next() else player.prev()
}

suspend fun npAct(a: NpAct, np: NowPlayingModel, src: MusicSource?, phone: PhoneSession?, toast: Toast, now: () -> Long) {
    if (np.variant) {
        val p = phone ?: return
        if (a == NpAct.Toggle) p.toggle()
        return
    }
    val source = src ?: return
    val s = source.player.state.value
    when (a) {
        NpAct.Favorite -> {
            val t = np.shown.track ?: return
            val on = source.favorites.toggle(t)
            val at = now()
            np.favorite = on
            toast.show(at, if (on) FAV_ADDED else FAV_REMOVED, np.shown.title)
        }
        NpAct.Shuffle -> source.player.shuffle(!s.shuffle)
        NpAct.Repeat -> source.player.repeat(nextRepeat(s.repeat))
        else -> Unit
    }
}

fun <S : Any> npAtTurn(t: Turn<S>, inks: NpInks, isNp: (S) -> Boolean) {
    if (listOf(t.from, t.to).any { it != null && isNp(it) }) inks.prime(t.at)
}
