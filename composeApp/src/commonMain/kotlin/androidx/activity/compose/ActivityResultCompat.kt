package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.result.contract.ActivityResultContract

interface ManagedActivityResultLauncher<I, O> {
    fun launch(input: I) {}
    fun unregister() {}
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ManagedActivityResultLauncher<I, O> {
    return remember {
        object : ManagedActivityResultLauncher<I, O> {
            override fun launch(input: I) {}
            override fun unregister() {}
        }
    }
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
