package net.seilent.funekit.screens.music.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import net.seilent.funekit.kit.pxSize
import net.seilent.funekit.screens.music.hub.HubGeo
import kotlin.math.max
import kotlin.math.min

class ImeInsets(val now: () -> Int, val target: () -> Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberIme(): ImeInsets {
    val d = LocalDensity.current
    val ime = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget
    return remember(d, ime, target) { ImeInsets({ ime.getBottom(d) }, { target.getBottom(d) }) }
}

fun searchView(geo: HubGeo, height: Int, imeBottom: Int): Int = max(0, min(geo.bottom, height - imeBottom) - geo.top)

object SearchCap : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (searchBytes(asCharSequence()) > MAX_BYTES) revertAllChanges()
    }
}

@Composable
fun SearchField(q: SearchQuery, ime: ImeInsets, done: () -> Boolean, close: () -> Unit, typed: (String) -> Unit) {
    val state = rememberTextFieldState(q.text)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val manager = LocalFocusManager.current
    val doneNow = rememberUpdatedState(done)
    val closeNow = rememberUpdatedState(close)
    val typedNow = rememberUpdatedState(typed)
    val held = remember { BooleanArray(1) }
    LaunchedEffect(q, q.open, q.opens) {
        state.setTextAndPlaceCursorAtEnd(q.text)
        if (q.open) {
            held[0] = true
            focus.requestFocus()
            keyboard?.show()
        } else if (held[0]) {
            held[0] = false
            manager.clearFocus(true)
            keyboard?.hide()
        }
    }
    LaunchedEffect(q, q.shows) {
        if (q.open && q.shows > 0) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(q, state) {
        snapshotFlow { state.text.toString() }.collect { t -> if (q.open && t != q.text) typedNow.value(t) }
    }
    LaunchedEffect(q, ime) {
        snapshotFlow { q.open to ime.target() }.collect { (open, b) ->
            if (!open) return@collect
            if (b > 0) q.armed = true else if (q.armed) closeNow.value()
        }
    }
    BackHandler(enabled = q.open) { closeNow.value() }
    BasicTextField(
        state,
        Modifier.pxSize(1, 1).graphicsLayer { alpha = 0f }.semantics { hideFromAccessibility() }.focusRequester(focus),
        inputTransformation = SearchCap,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = KeyboardActionHandler { doneNow.value() },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}
