package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * Multiplatform resource resolver for screens referencing Int-based resource IDs.
 */
@Composable
fun painterResource(id: Int): Painter {
    return ColorPainter(Color(0xFF3B82F6))
}

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String {
    return ""
}
