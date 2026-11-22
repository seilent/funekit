package net.seilent.funekit.screens.settings

import net.seilent.funekit.screens.music.nowplaying.AdjustSource

interface RowAdjust : AdjustSource {
    val key: String

    fun open(now: Long): Boolean

    fun revert()

    fun finish()

    fun landed() {}
}
