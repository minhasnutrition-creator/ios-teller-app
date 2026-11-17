package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Provides a mock/multiplatform Context for Composable trees on iOS.
 */
val LocalContext: ProvidableCompositionLocal<Context> = staticCompositionLocalOf {
    object : Context {}
}
