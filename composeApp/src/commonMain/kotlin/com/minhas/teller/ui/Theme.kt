package com.minhas.teller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.ThemeUtils

// --- Color Scheme for DEFAULT (MODERN) theme ---
// Use solid background for surface to ensure menus (DropdownMenu) are readable and not transparent.
private val SolidDarkSurface = Color(0xFF1E1E1E) // Dark Gray, Opaque
private val SolidDarkSurfaceVariant = Color(0xFF2C2C2C) // Slightly Lighter Gray, Opaque

private val ModernColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.White,
    primaryContainer = GlassWhite,
    onPrimaryContainer = Color.White,
    secondary = NeonPurple,
    onSecondary = Color.White,
    tertiary = NeonBlue,
    background = Color.Transparent, // Will be handled by a Brush
    onBackground = Color.White,
    surface = SolidDarkSurface, // Changed from GlassWhite to SolidDarkSurface
    onSurface = Color.White,
    surfaceVariant = SolidDarkSurfaceVariant, // Changed from transparent to SolidDarkSurfaceVariant
    onSurfaceVariant = Color(0xFFEEEEEE), // Light Gray text
    outline = Color(0x66FFFFFF),
    outlineVariant = Color(0x33FFFFFF),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

// --- Color Scheme for MODERN LIGHT theme ---
private val ModernLightColorScheme = lightColorScheme(
    primary = Color(0xFF00838F), // Darker Cyan for visibility on light
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF006064),
    secondary = Color(0xFF7B1FA2), // Darker Purple
    onSecondary = Color.White,
    tertiary = Color(0xFF1565C0), // Darker Blue
    background = Color(0xFFF5F5F5), // Light Grey Background
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF424242),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFB00020),
    onError = Color.White
)

// --- Color Scheme for CYBER-FINANCE theme ---
private val CyberFinanceColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF), // Neon Cyan
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212), // Charcoal
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

// --- Color Scheme for BENTO themes ---
private val BentoColorScheme = lightColorScheme(
    primary = Color(0xFF2C3E50), // Darker accent
    background = Color(0xFF0A0E21), // Navy Blue
    onBackground = Color.White,
    surface = Color.White, // Cards are light pastels, text on them will be dark
    onSurface = Color(0xFF212121), // Dark text for pastel cards
    surfaceVariant = Color(0xFFECF0F1),
    onSurfaceVariant = Color(0xFF34495E)
)

// --- Color Scheme for CRYSTAL theme ---
private val CrystalColorScheme = darkColorScheme(
    primary = Color.White,
    background = Color.Transparent, // Will use a blob brush
    onBackground = Color.White,
    surface = SolidDarkSurface, // Changed to Solid for readable dropdowns
    onSurface = Color.White,
    surfaceVariant = SolidDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFEEEEEE)
)


@Composable
fun TellerAppTheme(
    appTheme: AppTheme = AppTheme.DEFAULT, // New parameter
    content: @Composable () -> Unit
) {
    val colors = when (appTheme) {
        AppTheme.DEFAULT -> ModernColorScheme
        AppTheme.MODERN_LIGHT -> ModernLightColorScheme
        AppTheme.CYBER_FINANCE -> CyberFinanceColorScheme
        AppTheme.BENTO -> BentoColorScheme
        AppTheme.CRYSTAL -> CrystalColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
