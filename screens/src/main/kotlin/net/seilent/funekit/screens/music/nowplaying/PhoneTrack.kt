package net.seilent.funekit.screens.music.nowplaying

import android.graphics.Bitmap

data class PhoneTrack(
    val app: String,
    val title: String,
    val artist: String,
    val album: String,
    val playing: Boolean,
    val paused: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float,
    val shuffle: Boolean,
    val repeat: PhoneRepeat,
    val track: Int,
    val tracks: Int,
    val art: Bitmap?,
) {
    fun position(elapsedMs: Long): Long {
        if (!playing) return positionMs
        val p = positionMs + ((elapsedMs - positionAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else maxOf(0L, p)
    }
}

enum class PhoneRepeat { Off, All, One }
