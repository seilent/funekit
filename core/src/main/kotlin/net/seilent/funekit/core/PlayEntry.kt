package net.seilent.funekit.core

data class PlayEntry(
    val seq: Long,
    val startLocalS: Long? = null,
    val durationMs: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val mbids: List<String> = emptyList(),
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNo: Int? = null,
    val zoneS: Int? = null,
    val liveSeen: Boolean? = null,
    val exposed: Boolean? = null,
    val sentTo: Int? = null,
)
