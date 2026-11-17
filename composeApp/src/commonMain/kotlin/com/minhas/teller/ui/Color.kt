package com.minhas.teller.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// New Cyberpunk / Neon Palette (Darkened as requested)
val NeonCyan = Color(0xFF00838F) // Was 0xFF00ACC1
val NeonCyanDark = Color(0xFF006064) // Was 0xFF00838F
val NeonPurple = Color(0xFF7B1FA2) // Was 0xFFAA00FF
val NeonBlue = Color(0xFF1565C0)

val DarkBackground = Color(0xFF050A14) // Deep Dark Blue
val DarkSurface = Color(0xFF121926)    // Slightly lighter dark blue
val DarkSurfaceVariant = Color(0xFF1E2633) 

val TextWhite = Color(0xFFEEEEEE)
val TextGray = Color(0xFFB0B0B0)

// Light Theme Colors
val LightBackground = Color(0xFFF5F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE1E2E6)
val TextBlack = Color(0xFF1A1C1E)

// Global Neon Colors for Cyber-Finance Theme
val GlobalNeonCyan = Color(0xFF00E5FF)
val GlobalNeonMagenta = Color(0xFFE040FB)
val GlobalNeonPink = Color(0xFFFF4081)
val GlobalNeonGreen = Color(0xFF00E676)
val GlobalNeonYellow = Color(0xFFFFEA00)
val GlobalNeonOrange = Color(0xFFFF9100)
val GlobalNeonBlue = Color(0xFF00B0FF) // Added GlobalNeonBlue
val GlobalCharcoal = Color(0xFF121212)

// Global Pastel Colors for Bento Grid A
val GlobalSalmonRed = Color(0xFFF87060)
val GlobalPaleBlue = Color(0xFF90E0EF)
val GlobalPaleYellow = Color(0xFFFAF3DD)
val GlobalPastelOrange = Color(0xFFFFDAB9)
val GlobalMintGreen = Color(0xFFC1FBA4)
val GlobalLavender = Color(0xFFD4C1EC)

// Global Pastel Colors for Bento Grid B
val GlobalCoralPink = Color(0xFFF9A8A8)
val GlobalSkyBlue = Color(0xFF90CAF9)
val GlobalPaleYellowB = Color(0xFFFFF59D)
val GlobalPastelOrangeB = Color(0xFFFFCC80)
val GlobalMintGreenB = Color(0xFFA5D6A7)

// Dashboard Gradients (Aesthetically matched to screenshot)
val DashboardVoucherGradient = Brush.verticalGradient(listOf(Color(0xFF1C6EA4), Color(0xFF113D5E))) // Blue to Dark Blue
val DashboardDisplayEntriesGradient = Brush.verticalGradient(listOf(Color(0xFF8F1E40), Color(0xFFC05131))) // Crimson Red to Rust Orange
val DashboardAccountsGradient = Brush.verticalGradient(listOf(Color(0xFF0F7A65), Color(0xFF0A4D3F))) // Teal to Forest Green
val DashboardInventoryGradient = Brush.verticalGradient(listOf(Color(0xFF4C3078), Color(0xFF9E813B))) // Indigo to Gold Bronze
val DashboardLiveStockGradient = Brush.verticalGradient(listOf(Color(0xFFB8184C), Color(0xFF4F0A1E))) // Crimson Pink to Deep Burgundy
val DashboardDailyTasksGradient = Brush.verticalGradient(listOf(Color(0xFF00838F), Color(0xFF003B44))) // Teal Cyan to Deep Teal
val DashboardQuotesGradient = Brush.verticalGradient(listOf(Color(0xFFD35400), Color(0xFF8E3A00))) // Gold Orange to Dark Rust

val DashboardAiHelperGradient = Brush.verticalGradient(listOf(Color(0xFF8E24AA), Color(0xFF3A0D70))) // Deep Purple to Dark Violet
val DashboardSupportGradient = Brush.verticalGradient(listOf(Color(0xFF00897B), Color(0xFF004D40))) // Deep Slate Teal to Dark Forest Teal
val DashboardOverviewGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF2C3E50).copy(alpha = 0.9f),
        Color(0xFF4CA1AF).copy(alpha = 0.7f)
    ),
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
)
val DashboardBarChartGradient1 = Brush.verticalGradient(listOf(Color(0xFF69F0AE), Color(0xFF00C853)))
val DashboardBarChartGradient2 = Brush.verticalGradient(listOf(Color(0xFF40C4FF), Color(0xFF0091EA)))

// Exact Deep Abstract Background (Match screenshot dark slate)
val DashboardBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF121722), // Deep Slate Navy
        Color(0xFF141926),
        Color(0xFF161C2C)
    )
)

// --- NEW DYNAMIC BACKGROUNDS ---
// 1. Purple Tint (Vouchers)
val BackgroundPurpleGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1A0524), // Very Dark Purple
        Color(0xFF120822), // Dark Violet
        Color(0xFF1A1A2E)  // Fade to Navy
    )
)

// 2. Yellow-Red-Blue Dark Gradient (Reports / Display Entries)
// "yellow and red and blue but more yellowwish tone but more darker"
val BackgroundYellowGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2E2405), // Dark Yellowish/Olive
        Color(0xFF2E0E05), // Dark Reddish/Brown
        Color(0xFF0A1021)  // Dark Blue
    )
)

// 3. Teal Tint (Accounts Info)
val BackgroundTealGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF052420), // Very Dark Teal
        Color(0xFF08221F),
        Color(0xFF0F2424)
    )
)

// 4. Green/Dark Teal Tint (Inventory Info)
val BackgroundInventoryGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF052414), // Very Dark Green
        Color(0xFF082218),
        Color(0xFF0F2420)
    )
)

val DashboardImportGradient = Brush.verticalGradient(listOf(Color(0xFF6A1B9A), Color(0xFF4A148C))) // Purple to Deep Indigo

// Glass Menu Color
val GlassMenuColor = Color.Black.copy(alpha = 0.3f) // More transparent for glass effect
val GlassBorderColor = Color.White.copy(alpha = 0.15f)

