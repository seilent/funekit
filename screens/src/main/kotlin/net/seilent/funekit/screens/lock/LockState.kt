package net.seilent.funekit.screens.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.seilent.funekit.core.Lrc
import net.seilent.funekit.kit.lock.LockDrag
import net.seilent.funekit.kit.lock.LockLine
import net.seilent.funekit.kit.overlay.VolumeHud
import net.seilent.funekit.screens.music.nowplaying.LYRIC_NONE
import net.seilent.funekit.screens.music.nowplaying.NowPlayingModel
import net.seilent.funekit.screens.music.nowplaying.lyricAt
import net.seilent.funekit.screens.music.nowplaying.lyricDir
import net.seilent.funekit.screens.music.nowplaying.lyricLineDir
import java.text.Bidi

class LockState {
    internal var drag = LockDrag(0)
        private set
    internal val line = LockLine()
    internal val hud = VolumeHud()
    internal var clock by mutableStateOf("")
    internal var rev by mutableIntStateOf(0)
        private set
    internal var lineAt = 0L
        private set
    internal var opened = false
    internal var press: ((Int) -> Unit)? = null
    internal var fresh = true
    internal var wake = 0L
    private var width = 0
    private var dirOf: List<Lrc.Line>? = null
    private var dir = Bidi.DIRECTION_LEFT_TO_RIGHT

    internal fun bump() {
        rev++
    }

    internal fun size(w: Int, h: Int) {
        if (width == w && drag.heightPx == h) return
        width = w
        drag = LockDrag(h)
        opened = false
        bump()
    }

    internal fun down(py: Float, x: Float, y: Float, ms: Long, now: Long) {
        drag.down(py, x, y, ms, now)
        opened = false
        bump()
    }

    internal fun read(np: NowPlayingModel, clock: String, now: Long, cut: Boolean): Long {
        this.clock = clock
        val ls = np.lyrics.lines
        if (ls !== dirOf) {
            dirOf = ls
            dir = lyricDir(ls.map { it.text })
        }
        val at = lyricAt(np, now)
        val i = if (np.variant) LYRIC_NONE else np.lyrics.index(at)
        val text = if (i >= 0) ls[i].text else ""
        val was = line.text
        line.set(text, lyricLineDir(text, dir), dir == Bidi.DIRECTION_RIGHT_TO_LEFT, now)
        if (cut) line.cut()
        if (cut || line.text !== was) {
            lineAt = now
            bump()
        }
        return if (np.variant) -1L else np.lyrics.lineDue(at, now, now)
    }

    internal fun moving(now: Long): Boolean = drag.moving(now) || line.moving(now)

    fun rest() {
        drag.rest()
        opened = false
        bump()
    }

    fun volume(d: Int): Boolean {
        val p = press ?: return false
        p(d)
        return true
    }

    fun screenOff() {
        rest()
        hud.dismiss()
    }
}
