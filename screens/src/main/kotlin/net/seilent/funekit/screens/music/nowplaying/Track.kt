package net.seilent.funekit.screens.music.nowplaying

import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.LibraryState
import net.seilent.funekit.api.Repeat
import net.seilent.funekit.api.Row

private const val SECOND = 1000L
private const val MINUTE = 60L
private const val PAD = 10L
private const val LATER_FROM = 2
private const val FNV_BASIS = 2166136261L
private const val FNV_PRIME = 16777619L
private const val FNV_MASK = 0xFFFFFFFFL
private const val ID_MASK = 0x7FFFFFFFL

const val NP_HOUR_MS = 3600000L

data class NpShown(
    val track: Long?,
    val durationMs: Long,
    val artist: String,
    val album: String,
    val title: String,
    val next: String,
    val later: List<String>,
    val art: ArtRef?,
    val from: String,
    val nextId: Long? = null,
) {
    fun nextLine(i: Int): String = if (i == 0) next else later.getOrElse(i - 1) { "" }

    companion object {
        val None = NpShown(null, 0L, "", "", "", "", emptyList(), null, "")

        fun of(rows: List<Row>): NpShown {
            val r = rows.firstOrNull() ?: return None
            val next = rows.getOrNull(1)?.title ?: ""
            val later = rows.drop(LATER_FROM).map { it.title }
            return NpShown(r.id, r.durationMs ?: 0L, r.sub, r.album ?: "", r.title, next, later, r.art, "", rows.getOrNull(1)?.id)
        }

        fun of(p: PhoneTrack): NpShown {
            var h = FNV_BASIS
            for (t in listOf(p.title, p.artist, p.album)) {
                for (ch in (t + "\n").toByteArray()) h = ((h xor (ch.toLong() and BYTE)) * FNV_PRIME) and FNV_MASK
            }
            val count = if (p.track > 0 && p.tracks > 0) "${p.track} of ${p.tracks}" else ""
            return NpShown(h and ID_MASK, p.durationMs, p.artist, p.album, p.title, count, emptyList(), null, if (p.app.isBlank()) "" else "from ${p.app}")
        }

        private const val BYTE = 0xFFL
    }
}

data class NpReadKey(
    val serial: Long,
    val track: Long?,
    val index: Int,
    val queueRevision: Long,
    val queueSize: Int,
    val repeat: Repeat,
    val generation: Long,
    val library: LibraryState,
    val next: Int,
)

sealed interface NpQueueRead {
    val key: NpReadKey

    data class Shown(override val key: NpReadKey, val rows: List<Row>) : NpQueueRead

    data class NoRow(override val key: NpReadKey) : NpQueueRead

    data class Failed(override val key: NpReadKey, val cause: Exception) : NpQueueRead
}

fun npTime(ms: Long): String {
    val total = maxOf(0L, ms / SECOND)
    val sec = total % MINUTE
    return "${total / MINUTE}:${if (sec < PAD) "0" else ""}$sec"
}

fun npRemaining(durationMs: Long, posMs: Long): String = "-" + npTime(durationMs - minOf(posMs, durationMs))
