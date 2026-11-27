package net.seilent.funekit.screens.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import net.seilent.funekit.Scale
import net.seilent.funekit.kit.np.NpLayout
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.nowplaying.CoverBlur
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.NpBackdrop
import net.seilent.funekit.screens.music.nowplaying.NpFeed
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import kotlin.coroutines.CoroutineContext

class NpFeeds(val np: NowPlayingModel, val g: NpLayout, val backdrop: NpBackdrop, val src: MusicSource?) {
    var feed: NpFeed? by mutableStateOf(null)
        internal set
}

@Composable
fun NpGround(
    b: NpBackdrop,
    cover: Boolean,
    art: ImageBitmap?,
    artId: Any?,
    backdrop: StateFlow<NpPicture?>,
    scale: Scale,
    work: CoroutineContext,
    now: () -> Long,
    cut: (same: Boolean) -> Boolean,
    onShown: (id: Any?, same: Boolean) -> Unit = { _, _ -> },
) {
    val pic by backdrop.collectAsState()
    var blurred by remember { mutableStateOf<Blurred?>(null) }
    var showing by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(art, cover, scale) {
        if (!cover || art == null || blurred?.of(art, scale) == true) return@LaunchedEffect
        val img = withContext(work) { CoverBlur.image(art, scale.widthPx, scale.heightPx, scale) }
        blurred = Blurred(art, scale, img?.let { NpPicture(it, whole = true) }, artId)
    }
    val done = blurred?.takeIf { it.of(art, scale) }
    val wait = cover && art != null && done == null
    val want = (if (cover) done?.picture else null) ?: pic
    LaunchedEffect(want, wait) {
        if (wait) return@LaunchedEffect
        val id = done?.takeIf { want != null && it.picture === want }?.artId
        val t = now()
        val same = id != null && id === showing
        if (same && b.fading(t)) b.swap(want) else b.set(want, t, cut = cut(same))
        showing = id
        onShown(id, same)
    }
}

@Composable
fun rememberNpFeeds(
    src: MusicSource?,
    phone: StateFlow<PhoneSession?>,
    backdrop: StateFlow<NpPicture?>,
    scale: Scale,
    work: CoroutineContext,
    log: (String) -> Unit,
    now: () -> Long,
    lyrics: Boolean,
    cover: (NpPicture?) -> Boolean = { it == null },
    cut: (same: Boolean) -> Boolean = { true },
): NpFeeds {
    val g = remember(scale) { NpLayout(scale) }
    val np = remember { NowPlayingModel() }
    val b = remember { NpBackdrop() }
    np.workContext = work
    np.lyricsView = lyrics
    val p by phone.collectAsState()
    val feed = NowPlayingFeed(np, g, src, p, null, log, now)
    val pic by backdrop.collectAsState()
    NpGround(b, cover(pic), np.art?.takeIf { !np.artPhone }, np.artId, backdrop, g.s, work, now, cut)
    val feeds = remember(g, src) { NpFeeds(np, g, b, src) }
    feeds.feed = feed
    return feeds
}

private class Blurred(val art: ImageBitmap, val scale: Scale, val picture: NpPicture?, val artId: Any?) {
    fun of(a: ImageBitmap?, s: Scale): Boolean = art === a && scale == s
}
