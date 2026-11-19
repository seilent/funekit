package net.seilent.funekit.fake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.seilent.funekit.api.Lyrics

class FakeLyrics internal constructor(private val set: FakeSet, private val latencyMs: Long) : Lyrics {
    private val offsets = ArrayList<Pair<Long, Int>>()
    private val _fetch = MutableStateFlow(false)
    private val _revision = MutableStateFlow(0L)

    override val fetch: StateFlow<Boolean> = _fetch.asStateFlow()

    override val revision: StateFlow<Long> = _revision.asStateFlow()

    override suspend fun setFetch(on: Boolean) {
        _fetch.value = on
    }

    override suspend fun lyrics(track: Long): String? {
        delay(latencyMs)
        return if (set == FakeSet.Empty) null else Fixture.shelf(set).lyrics[track]
    }

    override suspend fun offsetMs(track: Long): Int {
        delay(latencyMs)
        return synchronized(offsets) { offsets.firstOrNull { it.first == track }?.second ?: 0 }
    }

    override suspend fun setOffset(track: Long, ms: Int) {
        delay(latencyMs)
        setNow(track, ms)
    }

    private fun setNow(track: Long, ms: Int) = synchronized(offsets) {
        offsets.removeAll { it.first == track }
        if (ms != 0) offsets += track to ms
        if (offsets.size > CAP) offsets.removeAt(0)
        Unit
    }

    private companion object {
        const val CAP = 4096
    }
}
