package net.seilent.funekit.kit.quickplay

enum class QpKind { Artist, Album, Song, Genre, Folder, Playlist }

data class QpItem(val kind: QpKind, val id: Long, val title: String, val art: Any?)

data class QpData(val playing: QpItem?, val pins: List<QpItem>, val history: List<QpItem>, val newest: List<QpItem>, val generation: Long = 0L) {
    companion object {
        val EMPTY = QpData(null, emptyList(), emptyList(), emptyList())
    }
}
