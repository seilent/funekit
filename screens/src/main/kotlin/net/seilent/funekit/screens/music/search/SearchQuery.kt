package net.seilent.funekit.screens.music.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import net.seilent.funekit.gen.Tok

const val MAX_BYTES = 64
const val NO_RESULTS = "no results"

fun searchWord(n: Int): String = if (n == 0) NO_RESULTS else if (n == 1) "1 song" else "$n songs"

fun searchBytes(text: CharSequence): Int = text.toString().encodeToByteArray().size

class SearchQuery(first: String) {
    var text by mutableStateOf(first)
    var open by mutableStateOf(false)
        private set
    var word by mutableStateOf("")
    var armed = false
    var fresh = false
    var landing by mutableStateOf(false)
    var commits by mutableIntStateOf(0)
        private set
    var opens by mutableIntStateOf(0)
        private set
    var shows by mutableIntStateOf(0)
        private set

    fun openField(committed: String) {
        text = committed
        word = ""
        armed = false
        fresh = true
        open = true
        opens++
    }

    fun show() {
        if (open) shows++
    }

    fun shut(committed: String) {
        text = committed
        word = ""
        armed = false
        open = false
    }

    fun committed(land: Boolean) {
        landing = land
        commits++
    }

    suspend fun settle(text: String, opened: Boolean, count: suspend (String) -> Int?) {
        if (!opened) delay(Tok.input_searchDebounceMs.toLong())
        val w = if (text.isEmpty()) "" else searchWord(count(text) ?: return)
        if (open && this.text == text) word = w
    }
}
