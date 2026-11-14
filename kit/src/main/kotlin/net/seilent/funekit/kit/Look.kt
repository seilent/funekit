package net.seilent.funekit.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

private class Last<T>(val value: T)

fun <T> lookOf(covered: State<Boolean>, look: () -> T): State<T> {
    var last: Last<T>? = null
    return derivedStateOf {
        val c = covered.value
        val l = last
        if (c && l != null) l.value else look().also { last = Last(it) }
    }
}

@Composable
fun <T> rememberLook(vararg keys: Any?, covered: Boolean, look: () -> T): State<T> {
    val c = rememberUpdatedState(covered)
    val l = rememberUpdatedState(look)
    return remember(*keys) { lookOf(c) { l.value() } }
}
