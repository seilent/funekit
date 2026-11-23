package net.seilent.funekit.screens.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import net.seilent.funekit.Scale
import net.seilent.funekit.kit.Covers
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.home.HomeEntry
import net.seilent.funekit.kit.home.HomeMenu
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.screens.music.MusicSource

class TestHome(home: Scale, entries: List<HomeEntry>, source: QpSource?) {
    var home by mutableStateOf(home)
    var entries by mutableStateOf(entries)
    var source by mutableStateOf(source)
    var density by mutableStateOf<Density?>(null)
    var state: HomeState? = null
        private set
    val picks = mutableListOf<HomeEntry>()
    val menus = mutableListOf<HomeMenu>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun poke() {
        wake.trySend(Unit)
    }

    @Composable
    internal fun State(toast: Toast, covers: Covers, m: TextMeasurer, fonts: FontFamily.Resolver, density: Density, now: () -> Long, poke: () -> Unit, log: (String) -> Unit): HomeState {
        val s = rememberHomeState(entries, { it.sub }, home, source, now, poke, toast, covers, m, fonts, this.density ?: density, log)
        state = s
        if (menus.lastOrNull() !== s.menu) menus += s.menu
        return s
    }

    @Composable
    internal fun Layer(s: HomeState, host: MusicHost<TestScreen>, models: MusicModels, music: MusicSource?, page: Scale, a: Arbiter) {
        HomeLayer(s, host, models, music, page, a, onEntry = { picks += it }, onRing = { host.openNp(npOpenForm(true, false)) })
    }

    @Composable
    internal fun Loop(s: HomeState) {
        LaunchedEffect(s) {
            var frame = 0L
            while (true) {
                withFrameMillis { t ->
                    s.tick(t)
                    frame = t
                }
                if (s.animating(frame)) continue
                val at = s.wakeAt(frame)
                if (at == null) wake.receive() else withTimeoutOrNull(at - frame) { wake.receive() }
            }
        }
    }
}
