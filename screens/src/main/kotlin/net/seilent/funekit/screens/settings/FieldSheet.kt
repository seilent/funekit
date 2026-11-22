package net.seilent.funekit.screens.settings

import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.AdjustHud

sealed interface FieldPress {
    data object None : FieldPress

    data object Field : FieldPress

    class Choice(val sheet: SettingSheet) : FieldPress
}

interface FieldSheet {
    val hud: AdjustHud
    val title: String
    val items: List<String>

    fun values(): List<String>

    fun mixed(): List<Boolean> = emptyList()

    fun field(): Int

    fun press(i: Int): FieldPress

    fun pickLive(i: Int)

    fun choose(i: Int) {}

    fun adjust(now: Long): Boolean

    fun tap(h: AdjustHit)

    fun back(): Boolean

    fun typed(i: Int): Boolean

    fun hold(i: Int): Boolean

    fun holdNumber(): Boolean

    fun scrim(): Boolean

    fun key(d: Int): Boolean = false

    fun gone(): Boolean = false

    fun dismiss()
}
