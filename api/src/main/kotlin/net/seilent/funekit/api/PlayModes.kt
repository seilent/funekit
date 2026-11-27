package net.seilent.funekit.api

import kotlinx.coroutines.flow.StateFlow

enum class PlayThrough { Off, Folders, Albums, Playlist, Library }

enum class ResumeMode { Off, Track, Position }

data class PlayPrefs(val through: PlayThrough = PlayThrough.Off, val list: Listing? = null, val resume: ResumeMode = ResumeMode.Position)

interface PlayModes {
    val prefs: StateFlow<PlayPrefs>

    suspend fun set(prefs: PlayPrefs)
}
