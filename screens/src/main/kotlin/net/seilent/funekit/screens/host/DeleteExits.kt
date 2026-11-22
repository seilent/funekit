package net.seilent.funekit.screens.host

import net.seilent.funekit.screens.music.MusicPage

fun deleteExits(pages: List<MusicPage?>, albums: Set<Long>, artists: Set<Long>): List<Int> = pages.indices.filter { i ->
    when (val p = pages[i]) {
        is MusicPage.Album -> p.id in albums
        is MusicPage.Artist -> p.id in artists
        else -> false
    }
}
