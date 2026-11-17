package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

interface ManagedActivityResultLauncher<I, O> {
    fun launch(input: I) {}
    fun unregister() {}
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: Any?,
    onResult: (O) -> Unit
): ManagedActivityResultLauncher<I, O> {
    return remember {
        object : ManagedActivityResultLauncher<I, O> {
            override fun launch(input: I) {}
            override fun unregister() {}
        }
    }
}
