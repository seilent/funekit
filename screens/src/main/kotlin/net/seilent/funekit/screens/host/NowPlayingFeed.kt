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
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Disconnected
import net.seilent.funekit.api.LibraryState
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.PlayerState
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.api.Row
import net.seilent.funekit.api.Stale
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
import net.seilent.funekit.screens.music.nowplaying.NpQueueRead
import net.seilent.funekit.screens.music.nowplaying.NpReadKey
import net.seilent.funekit.screens.music.nowplaying.NpShown
import net.seilent.funekit.screens.music.nowplaying.PhoneLyrics
import net.seilent.funekit.screens.music.nowplaying.PhoneRepeat
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack
import net.seilent.funekit.screens.music.nowplaying.Variant
import net.seilent.funekit.screens.music.overlay.listEdit

const val FAV_ADDED = "added to favorites"
const val FAV_REMOVED = "removed from favorites"

private val noState = MutableStateFlow(PlayerState())
private val noLibrary = MutableStateFlow(LibraryState())
private val noRevision = MutableStateFlow(0L)

fun playOf(feed: NpFeed?): Play = when (feed) {
    is NpFeed.Own -> if (feed.state.queueSize == 0) Play.None else if (feed.state.playing) Play.Playing else Play.Paused
    is NpFeed.Phone -> if (feed.track.playing) Play.Playing else if (feed.track.paused) Play.Paused else Play.None
    null -> Play.None
}

fun nextRepeat(r: Repeat): Repeat = when (r) {
    Repeat.Off -> Repeat.All
    Repeat.All -> Repeat.One
    Repeat.One -> Repeat.Off
}

fun nextPhoneRepeat(r: PhoneRepeat): PhoneRepeat = when (r) {
    PhoneRepeat.Off -> PhoneRepeat.All
    PhoneRepeat.All -> PhoneRepeat.One
    PhoneRepeat.One -> PhoneRepeat.Off
}

fun npHasTrack(np: NowPlayingModel): Boolean = np.shown.track != null

internal fun npInfoTrack(np: NowPlayingModel): Long? = if (np.state.service != null) null else np.shown.track

fun npReadIn(np: NowPlayingModel, src: MusicSource): Boolean {
    val k = np.readKey ?: return false
    if (np.queueRead?.key != k) return false
    val s = src.player.state.value
    return k.track == s.track && k.index == s.index && k.queueRevision == s.queueRevision && k.queueSize == s.queueSize &&
        k.repeat == s.repeat && k.generation == src.library.generation.value && k.library == src.library.state.value
}

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

internal suspend fun npQueueRead(np: NowPlayingModel, source: MusicSource, s: PlayerState, key: NpReadKey, log: (String) -> Unit, now: () -> Long) {
    val (prev, rows) = try {
        queueRows(source, s, key.next)
    } catch (e: Exception) {
        if (e !is Busy && e !is Disconnected && e !is NoCard && e !is Stale) throw e
        log("now playing queue read at ${s.index}: ${e.message}")
        np.readEnds(NpQueueRead.Failed(key, e))
        return
    }
    np.prevId = prev
    val n = NpShown.of(rows)
    if (np.shown.track == null) np.sync(n, now()) else np.changed(n, now())
    np.readEnds(if (rows.isEmpty()) NpQueueRead.NoRow(key) else NpQueueRead.Shown(key, rows))
}

internal suspend fun npMark(np: NowPlayingModel, l: Lyrics, track: Long): Boolean {
    if (!np.lyricsView) return l.lyrics(track) != null
    np.lyrics.read(l, track, np.workContext)
    return np.lyrics.has(track)
}

internal suspend fun npPhoneMark(np: NowPlayingModel, pl: PhoneLyrics, t: PhoneTrack, shown: Long): Boolean {
    if (!np.lyricsView) return pl.lyrics(t) != null
    np.lyrics.readPhone(pl, t, shown, np.workContext)
    return np.lyrics.has(shown)
}

@Composable
fun NowPlayingFeed(np: NowPlayingModel, g: NpLayout, src: MusicSource?, phone: PhoneSession?, phoneLyrics: PhoneLyrics? = null, log: (String) -> Unit, now: () -> Long): NpFeed? {
    val own by (src?.player?.state ?: noState).collectAsState()
    val generation by (src?.library?.generation ?: noRevision).collectAsState()
    val library by (src?.library?.state ?: noLibrary).collectAsState()
    val lyricsRevision by (src?.lyrics?.revision ?: noRevision).collectAsState()
    val favoritesRevision by (src?.favorites?.revision ?: noRevision).collectAsState()
    val phoneRevision by (phoneLyrics?.revision ?: noRevision).collectAsState()
    val feed = Variant.pick(if (src != null) own else null, phone?.track)
    LaunchedEffect(feed, src) {
        when (feed) {
            is NpFeed.Own -> np.feed(feed.state)
            is NpFeed.Phone -> {
                np.feed(feed.track)
                val n = NpShown.of(feed.track)
                if (np.shown.track == null) np.sync(n, now()) else np.changed(n, now())
            }
            null -> np.clear()
        }
    }
    val fed = (feed as? NpFeed.Own)?.state
    LaunchedEffect(fed?.track, fed?.index, fed?.queueRevision, fed?.queueSize, fed?.repeat, src, g.nextTracks, generation, library) {
        val s = fed
        val source = src
        if (s == null || source == null) {
            np.noRead()
            return@LaunchedEffect
        }
        val key = np.readStarts { NpReadKey(it, s.track, s.index, s.queueRevision, s.queueSize, s.repeat, generation, library, g.nextTracks) }
        npQueueRead(np, source, s, key, log, now)
    }
    val track = np.shown.track
    LaunchedEffect(track, src, np.variant, lyricsRevision, favoritesRevision, phoneLyrics, phoneRevision) {
        val source = src
        val pl = phoneLyrics
        val p = np.phone
        if (np.variant && pl != null && track != null && p != null) {
            np.favorite = false
            np.mark = quietly { npPhoneMark(np, pl, p, track) } == true
            return@LaunchedEffect
        }
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
        var pic = 0L
        val img = when {
            np.variant -> phoneArt?.asImageBitmap()
            ref == null || source == null -> null
            else -> {
                val config = configFor(Build.VERSION.SDK_INT, true)
                val got = quietly { withContext(Dispatchers.IO) { listOf(largeArt(g.artSide, config, { pic = it }) { source.viewed { it.art(ref, g.artSide) } }) } }
                if (got == null) {
                    np.art = null
                    np.artKey = null
                    np.artFor = track
                    return@LaunchedEffect
                }
                got.single()?.asImageBitmap()
            }
        }
        if (np.variant) {
            np.artPhone = true
            np.art = img
            np.artKey = key
            np.artRef = null
            np.artFor = track
            return@LaunchedEffect
        }
        when {
            img != null && pic == np.artPic && np.art != null && !np.artPhone -> Unit
            img != null && ref == np.artRef -> {
                np.art = img
                np.artPic = pic
            }
            else -> {
                np.art = img
                np.artPic = pic
                np.artId = Any()
            }
        }
        if (img != null && np.bare == track) np.lateArt(track)
        np.artPhone = false
        np.artKey = key
        np.artRef = ref
        np.artFor = track
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
        when (a) {
            NpAct.Toggle -> p.toggle()
            NpAct.Shuffle -> p.shuffle?.invoke(!p.track.shuffle)
            NpAct.Repeat -> p.repeat?.invoke(nextPhoneRepeat(p.track.repeat))
            else -> Unit
        }
        return
    }
    val source = src ?: return
    val s = source.player.state.value
    when (a) {
        NpAct.Favorite -> {
            val t = np.shown.track ?: return
            var on = false
            listEdit(true, "") { on = source.favorites.toggle(t) }?.let {
                toast.show(now(), it.line, it.title)
                return
            }
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
