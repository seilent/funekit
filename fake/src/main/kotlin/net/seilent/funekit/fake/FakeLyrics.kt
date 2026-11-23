package net.seilent.funekit.fake

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.seilent.funekit.api.LyricCopy
import net.seilent.funekit.api.Lyrics
import net.seilent.funekit.core.Lrc

class FakeLyrics internal constructor(private val set: FakeSet, private val latencyMs: Long, private val carried: Boolean) : Lyrics {
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
        return if (carried) served(track)?.text else stored(track)
    }

    override suspend fun copy(track: Long): LyricCopy? {
        if (!carried) return super.copy(track)
        delay(latencyMs)
        return served(track)
    }

    override suspend fun offsetMs(track: Long): Int {
        delay(latencyMs)
        return synchronized(offsets) { offsetOf(track) }
    }

    override suspend fun setOffset(track: Long, ms: Int) {
        delay(latencyMs)
        setNow(track, ms)
        if (carried) _revision.update { it + 1 }
    }

    private fun stored(track: Long): String? = if (set == FakeSet.Empty) null else Fixture.shelf(set).lyrics[track]

    private fun served(track: Long): LyricCopy? {
        val text = stored(track) ?: return null
        val ms = synchronized(offsets) { offsetOf(track) }
        val lines = Lrc.parse(text.toByteArray(Charsets.UTF_8)).lines
        if (lines.isEmpty()) return LyricCopy(text, ms)
        return LyricCopy(Lrc.shift(lines, ms.toLong()).joinToString("\n") { stamp(it.ms) + it.text }, ms)
    }

    private fun offsetOf(track: Long): Int = offsets.firstOrNull { it.first == track }?.second ?: 0

    private fun setNow(track: Long, ms: Int) = synchronized(offsets) {
        offsets.removeAll { it.first == track }
        if (ms != 0) offsets += track to ms
        if (offsets.size > CAP) offsets.removeAt(0)
        Unit
    }

    private fun stamp(ms: Long): String {
        val m = ms / MINUTE
        val s = ms % MINUTE / SECOND
        val f = ms % SECOND
        return "[${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${f.toString().padStart(3, '0')}]"
    }

    private companion object {
        const val CAP = 4096
        const val SECOND = 1_000L
        const val MINUTE = 60_000L
    }
}
