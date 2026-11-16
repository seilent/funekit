package net.seilent.funekit.artist

enum class ImgOrigin(val id: Int, val word: String) {
    LIBRARY(0, "library"),
    PHONE(1, "phone"),
    ;

    companion object {
        fun of(id: Int): ImgOrigin? = entries.firstOrNull { it.id == id }

        fun of(word: String): ImgOrigin? = entries.firstOrNull { it.word == word }
    }
}
