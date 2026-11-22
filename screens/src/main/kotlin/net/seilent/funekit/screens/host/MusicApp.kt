package net.seilent.funekit.screens.host

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.seilent.funekit.screens.music.nowplaying.NpPicture
import net.seilent.funekit.screens.music.nowplaying.OffsetEdit
import net.seilent.funekit.screens.music.nowplaying.PhoneLyrics
import net.seilent.funekit.screens.music.nowplaying.PhoneRepeat
import net.seilent.funekit.screens.music.nowplaying.PhoneTrack
import net.seilent.funekit.screens.music.overlay.SheetDelete
import net.seilent.funekit.screens.music.overlay.SheetExtra
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.settings.SettingsPage
import net.seilent.funekit.screens.settings.SettingsScreen

class PhoneSession(
    val track: PhoneTrack,
    val toggle: () -> Unit,
    val shuffle: ((Boolean) -> Unit)? = null,
    val repeat: ((PhoneRepeat) -> Unit)? = null,
    val skip: (Int) -> Unit,
)

private val noBackdrop: StateFlow<NpPicture?> = MutableStateFlow<NpPicture?>(null).asStateFlow()

interface MusicApp<S : Any> {
    fun music(s: S): MusicEntry?

    fun screen(e: MusicEntry): S

    fun homeHas(word: String): Boolean

    fun homeItem(word: String): Int

    fun homeBusy(now: Long): Boolean

    fun homeLand()

    fun homeFront(): Boolean

    fun liftRow(top: S?): Int? = null

    fun cancel(top: S?)

    val phone: StateFlow<PhoneSession?>

    val backdrop: StateFlow<NpPicture?> get() = noBackdrop

    val lyricsView: Boolean get() = false

    fun offset(track: Long): OffsetEdit? = null

    fun sheetDelete(t: SheetTarget): SheetDelete? = null

    fun sheetExtras(t: SheetTarget): List<SheetExtra> = emptyList()

    val phoneLyrics: PhoneLyrics? get() = null

    fun log(line: String) {}

    fun settings(s: S): SettingsScreen? = null

    val settingsPages: List<SettingsPage> get() = emptyList()
}

interface VolumeLevel {
    fun percent(): Int

    fun stepPct(): Int

    suspend fun step(detents: Int)

    suspend fun set(pct: Int)

    fun maxPct(): Int? = null
}
