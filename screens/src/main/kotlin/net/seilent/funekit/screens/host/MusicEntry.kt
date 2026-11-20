package net.seilent.funekit.screens.host

import net.seilent.funekit.api.Row
import net.seilent.funekit.kit.listRowWord
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.NpForm
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.screens.music.ALBUMS
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.PageModel
import net.seilent.funekit.screens.music.PageModels
import net.seilent.funekit.screens.music.PushFrom
import net.seilent.funekit.screens.music.albumHeader
import net.seilent.funekit.screens.music.headerWord
import net.seilent.funekit.screens.music.hub.HubDraw
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.hub.SEARCH_ALBUMS
import net.seilent.funekit.screens.music.hub.rowModel
import net.seilent.funekit.screens.music.pushFrom

data class MusicEntry(val entry: Long, val page: MusicPage, val from: PushFrom? = null, val tile: Boolean = false, val head: Row? = null)

fun musicEntry(entry: Long, page: MusicPage, kind: MoveKind, top: MusicEntry?, models: PageModels, row: Int = -1, head: Row? = null): MusicEntry {
    val from = top?.let { pushFrom(it.entry, it.page, models[it.entry]?.pivot ?: -1, row) }
    return MusicEntry(entry, page, from, page is MusicPage.Album && (kind == MoveKind.T3m || kind == MoveKind.T3a), head)
}

fun albumPushKind(top: MusicEntry?, models: PageModels, head: Row, hubTile: Boolean): MoveKind {
    val src = top ?: return MoveKind.T3
    val under = models[src.entry]
    return when {
        src.page is MusicPage.Artist && head.art == null -> MoveKind.T3z
        (src.page is MusicPage.AlbumCol || src.page is MusicPage.Artist) && under?.cell(head.id) == true -> MoveKind.T3a
        src.page == MusicPage.Hub && under?.pivot == HUB_ALBUMS && hubTile -> MoveKind.T3m
        src.page is MusicPage.Search && under?.pivot == SEARCH_ALBUMS && hubTile -> MoveKind.T3m
        else -> MoveKind.T3
    }
}

fun colPushKind(pick: Boolean): MoveKind = if (pick) MoveKind.T3c else MoveKind.T3

fun hubRowAt(m: HubModel, d: HubDraw, r: Int): Int {
    val p = m.pivot
    val model = m.rowModel(p, r)
    val g = d.geo
    return if (listRowWord(m.panes[p].track, g.top, g.bottom, r, m.gridRow(p, r), model, model?.let { d.ink(it) }, d.s) != null) r else -1
}

fun colFire(h: HubModel, onTop: Boolean, rest: Boolean, overlay: Boolean): Int {
    val l = h.colPick
    h.colPick = -1
    if (l < 0 || !onTop || !rest || overlay || h.pivot != HUB_ALBUMS || h.pane.map?.has(l) != true) return -1
    return l
}

fun musicPop(from: MusicEntry, to: MusicEntry, under: PageModel?): MoveKind {
    val p = from.page
    val src = from.from
    return when {
        p is MusicPage.Album && to.page is MusicPage.Artist && from.head != null && from.head.art == null -> MoveKind.T4z
        p is MusicPage.Album && from.tile && (to.page is MusicPage.AlbumCol || to.page is MusicPage.Artist) && under?.cell(p.id) == true -> MoveKind.T4a
        p.albumHeader && src != null && src.entry == to.entry && src.pivot == under?.pivot && src.word == ALBUMS -> MoveKind.T4m
        else -> MoveKind.T4
    }
}

fun <S : Any> musicPopKind(t: Turn<S>, music: (S) -> MusicEntry?, models: PageModels, homeHas: (String) -> Boolean): MoveKind? {
    val p = t.from?.let(music) ?: return null
    val to = t.to
    val under = to?.let(music)
    return when {
        p.page == MusicPage.NowPlaying -> MoveKind.T8
        under != null -> musicPop(p, under, models[under.entry])
        to != null -> MoveKind.T4
        p.page.headerWord?.let(homeHas) == true -> MoveKind.T4h
        else -> MoveKind.T4n
    }
}

fun isNp(e: MusicEntry): Boolean = e.page == MusicPage.NowPlaying

fun npOpenForm(atHome: Boolean, front: Boolean): NpForm = if (!atHome) NpForm.Page else if (front) NpForm.Front else NpForm.Ring

fun npCloseForm(stackEmpty: Boolean, front: Boolean): NpForm = if (stackEmpty && front) NpForm.Front else NpForm.Page
