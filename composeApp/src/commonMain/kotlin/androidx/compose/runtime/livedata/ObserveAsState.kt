package androidx.compose.runtime.livedata

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.LiveData

@Composable
fun <T> LiveData<T>.observeAsState(): State<T?> {
    return remember { mutableStateOf(this.value) }
}

@Composable
fun <T> LiveData<T>.observeAsState(initial: T): State<T> {
    return remember { mutableStateOf(this.value ?: initial) }
}
