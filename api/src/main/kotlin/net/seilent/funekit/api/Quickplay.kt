package net.seilent.funekit.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val offered: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

interface Quickplay {
    val revision: StateFlow<Long>

    val available: StateFlow<Boolean> get() = offered

    suspend fun pin(kind: Kind, id: Long)
    suspend fun unpin(kind: Kind, id: Long)
}

suspend fun LibraryView.pins(offset: Int, count: Int): Page = page(Listing.Pins, offset, count)

suspend fun LibraryView.history(offset: Int, count: Int): Page = page(Listing.History, offset, count)

suspend fun LibraryView.newAlbums(offset: Int, count: Int): Page = page(Listing.NewAlbums, offset, count)
