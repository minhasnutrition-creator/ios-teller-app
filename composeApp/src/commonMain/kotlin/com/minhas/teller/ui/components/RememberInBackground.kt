package com.minhas.teller.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Like `remember(keys) { compute() }`, but [compute] runs on [Dispatchers.Default] instead of the
 * UI thread.
 *
 * Report screens aggregate every voucher in the period (after a Tally import that is tens of
 * thousands of entries). Doing that inside composition froze the screen on each recomposition and
 * could trigger "App not responding". Here the screen shows [initial] until the first result is
 * ready, then keeps showing the latest result while a newer one is computed whenever a key changes
 * (an in-flight computation is cancelled when keys change again).
 *
 * [compute] must be a pure function of its inputs: no Compose state writes, no Context/UI calls.
 */
@Composable
fun <T> rememberInBackground(vararg keys: Any?, initial: T, compute: suspend CoroutineScope.() -> T): T {
    val latestCompute by rememberUpdatedState(compute)
    val state = produceState(initialValue = initial, *keys) {
        value = withContext(Dispatchers.Default) { latestCompute() }
    }
    return state.value
}
