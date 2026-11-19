package net.seilent.funekit.fake

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.NoSpace
import net.seilent.funekit.api.Quickplay

class FakeQuickplay internal constructor(private val store: Store, private val library: FakeLibrary, private val cap: Int?) : Quickplay {
    private val lock = Mutex()

    override val revision: StateFlow<Long> = store.revision

    override suspend fun pin(kind: Kind, id: Long) = lock.withLock {
        require(kind == Kind.Album || kind == Kind.Artist || kind == Kind.Song) { "$kind cannot be pinned" }
        val pin = Pin(kind, id)
        store.updatePins {
            when {
                pin in it || !library.resolves(pin) -> it
                cap != null && it.size >= cap -> throw NoSpace()
                else -> it + pin
            }
        }
    }

    override suspend fun unpin(kind: Kind, id: Long) = lock.withLock { store.updatePins { it - Pin(kind, id) } }
}
