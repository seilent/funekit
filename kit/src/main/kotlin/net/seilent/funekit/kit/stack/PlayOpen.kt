package net.seilent.funekit.kit.stack

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PlayOpen internal constructor(
    internal val tapAt: Long,
    internal val boundMs: Long,
    private val onDone: (PlayOpen) -> Unit,
    private val onFail: (PlayOpen) -> Unit,
) {
    enum class State { Pending, Local, Armed, Released, Refused }

    var state by mutableStateOf(State.Pending)
        internal set

    internal var latch: Latch? = null
    internal var doneAt = -1L

    internal val bound: Long get() = tapAt + boundMs

    fun done() = onDone(this)

    fun fail() = onFail(this)
}

internal enum class OpenForm { Push, Cut, Keep }

internal enum class Latch { Done, Fail }

internal fun onMain(what: String) {
    check(Looper.myLooper() == Looper.getMainLooper()) { "$what runs on the main thread only" }
}
