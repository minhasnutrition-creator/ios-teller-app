package androidx.compose.ui.graphics

// Real Compose Multiplatform declares Bitmap.asImageBitmap() in this package
// (androidx.compose.ui.graphics), not android.graphics — several screens import it
// from here directly (`import androidx.compose.ui.graphics.asImageBitmap`).
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap =
    ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
