package net.seilent.funekit.screens.host

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.flow.MutableStateFlow
import net.seilent.funekit.FuneTheme
import net.seilent.funekit.Scale
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kept
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Play
import net.seilent.funekit.kit.StatusState
import net.seilent.funekit.kit.Toast
import net.seilent.funekit.kit.groundBands
import net.seilent.funekit.kit.motion.MoveForms
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.motion.Overlay
import net.seilent.funekit.kit.motion.PageMove
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.kit.rememberCovers
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.HomeMove
import net.seilent.funekit.kit.stack.HomePaint
import net.seilent.funekit.kit.stack.HomeSide
import net.seilent.funekit.kit.stack.MovePaint
import net.seilent.funekit.kit.stack.PageKind
import net.seilent.funekit.kit.stack.PageSide
import net.seilent.funekit.kit.stack.PageStack
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.kit.touch.Swipes
import net.seilent.funekit.kit.touch.arbiter
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.hub.musicModel

class TestApp(page: Scale, np: Scale, private val src: MusicSource?, private val level: VolumeLevel, private val now: () -> Long) : MusicApp<MusicEntry> {
    var page by mutableStateOf(page)
    var np by mutableStateOf(np)
    val music = MusicModels()
    val models = PageModels { e, p -> musicModel(e, p as MusicPage, music.hub) }
    lateinit var host: MusicHost<MusicEntry>
    lateinit var stack: PageStack<MusicEntry>
    lateinit var toast: Toast
    var pokes = 0

    override fun music(s: MusicEntry): MusicEntry = s

    override fun screen(e: MusicEntry): MusicEntry = e

    override fun homeHas(word: String): Boolean = false

    override fun homeItem(word: String): Int = -1

    override fun homeBusy(now: Long): Boolean = false

    override fun homeLand() {}

    override fun homeFront(): Boolean = false

    override fun liftRow(top: MusicEntry?): Int? = null

    override fun cancel(top: MusicEntry?) {}

    override val phone = MutableStateFlow<PhoneSession?>(null)

    private val home = object : HomePaint {
        override val quickplay = false

        override fun peekAlpha(at: Long) = 0f

        override fun baseline(item: Int) = Tok.homeFirstBaseline + item * Tok.homePitch

        override fun DrawScope.peek(alpha: Float, now: Long, rec: DrawRecord?) = Unit

        override fun DrawScope.home(look: HomeMove, now: Long, rec: DrawRecord?) = Unit
    }

    fun compose(rule: ComposeContentTestRule) {
        rule.setContent {
            FuneTheme(page) {
                val m = rememberTextMeasurer()
                val fonts = LocalFontFamilyResolver.current
                val density = LocalDensity.current
                val covers = rememberCovers()
                val a = remember(page.touchPpu) { Arbiter(page) }
                val toast = remember { Toast() }
                this@TestApp.toast = toast
                val jump = remember { Overlay(OverKind.Jump) }
                val menuHost = remember { MenuHost() }
                val move = remember { PageMove() }
                val movePaint = remember { MovePaint(move) }
                val bands = remember(page.heightPx) { groundBands(page.heightPx, true) }
                val grid = remember(page) { page.kept() }
                val env = MusicEnv(page, np, m, fonts, density, covers, bands, Accent.color(Accent.defaultHue, true), true, a, now, { pokes++ }, grid)
                val h = rememberMusicHost(this@TestApp, music, models, env, src, null, level, toast, jump, menuHost, movePaint)
                host = h
                stack = remember {
                    PageStack<MusicEntry>(
                        MoveForms.board(page.board),
                        { h.kind(it) ?: PageKind.Plain },
                        pageBack = { s, _ -> h.pageBack(s) },
                        retargets = h::retargets,
                        move = move,
                        ready = { h.ready(it) ?: true },
                        enter = h::enter,
                        drop = h::drop,
                        started = h::started,
                        starting = h::starting,
                        swapped = h::swapped,
                        dropped = h::dropped,
                        isNp = h::isNp,
                        albumOf = h::albumOf,
                    ) { t ->
                        movePaint.ends(t, HomeSide(page, home, 0f), t.from?.let { s -> h.side(s)?.let { PageSide(if (h.isNp(s)) np else page, it) } }, t.to?.let { s -> h.side(s)?.let { PageSide(if (h.isNp(s)) np else page, it) } }, MoveForms.board(page.board))
                    }.also { h.stack = it }
                }
                SideEffect { h.applied() }
                h.StackClock { MoveKind.T4n }
                Box(Modifier.pxSize(page.widthPx, page.heightPx).arbiter(a, Swipes(false, false, false, { false }, { _, _ -> }))) {
                    h.MusicPages(emptyMap(), StatusState("", -1, false, Play.None, false, -1, -1))
                    h.MenuOverlayHost()
                    h.ToastHost(false)
                    h.VolumeHost()
                }
            }
        }
    }
}
