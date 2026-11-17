package com.minhas.teller.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.minhas.teller.navigation.Screen

// --- Colors ---
val RoyalBlue = Color(0xFF0A1128) // Dark Navy Blue
val DeepPurple = Color(0xFF000814) // Deep Navy Blue

val GlassWhite = Color(0x1AFFFFFF) // 10% White
val GlassBorder = Color(0x33FFFFFF) // 20% White

// Gradients
val MainBackgroundGradient = Brush.linearGradient(
    colors = listOf(RoyalBlue, DeepPurple),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

val VoucherGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)) // Purple to Magenta
)

val EstimatesGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFF12711), Color(0xFFF5AF19)) // Orange to Yellow-Orange
)

val AccountsGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)) // Teal to Emerald
)

val InventoryGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF005C97), Color(0xFF363795)) // Dark Cyan to Cyan
)

val LiveStockGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFEF473A), Color(0xFFCB2D3E)) // Red/Pink
)

val ReportsGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF2193b0), Color(0xFF6dd5ed)) // Blue
)

val ReportsOrangeGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFF512F), Color(0xFFF09819)) // Deep Orange to Orange-Yellow
)

val EstimatesGreyGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF606c88), Color(0xFF3f4c6b)) // Ash/Dark Grey
)

// Gemini-style Gradient (Blue -> Purple -> Red -> Orange)
val AiAssistantGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF4285F4), // Blue
        Color(0xFF9B72CB), // Purple
        Color(0xFFD96570), // Red
        Color(0xFFF4B400)  // Yellow/Orange
    )
)

val BarChartSalesGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF00b09b), Color(0xFF96c93d))
)

val BarChartPurchaseGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF2193b0), Color(0xFF6dd5ed))
)

// --- Dynamic Backgrounds ---

fun getBackgroundBrush(route: String?, isDark: Boolean): Brush {
    return if (isDark) getDarkBrush(route) else getLightBrush(route)
}

private fun getDarkBrush(route: String?): Brush {
    if (route == null || route == Screen.Dashboard.route) return MainBackgroundGradient
    
    // Use darker shades of the grid colors for background to ensure white text pops
    return when {
        route.contains("voucher") -> Brush.linearGradient(listOf(Color(0xFF2A0036), Color(0xFF1A0022))) // Deep Purple
        route.contains("quotation") -> Brush.linearGradient(listOf(Color(0xFF3E1C00), Color(0xFF2E1200))) // Deep Orange/Brown
        route.contains("ledger") || route.contains("account") || route.contains("group") -> Brush.linearGradient(listOf(Color(0xFF002824), Color(0xFF001A15))) // Deep Teal
        route.contains("stock") || route.contains("unit") || route.contains("inventory") -> Brush.linearGradient(listOf(Color(0xFF001A2E), Color(0xFF000E1A))) // Deep Blue/Cyan
        route.contains("live_stock") -> Brush.linearGradient(listOf(Color(0xFF4E0000), Color(0xFF290000))) // Deep Red
        route.contains("report") || route.contains("register") || route.contains("book") -> Brush.linearGradient(listOf(Color(0xFF0A1128), Color(0xFF050A14))) // Deep Blue for Reports
        else -> Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF050A14))) // Fallback Dark Blue
    }
}

private fun getLightBrush(route: String?): Brush {
    if (route == null || route == Screen.Dashboard.route) return Brush.linearGradient(listOf(Color(0xFFF0F4F8), Color(0xFFE1E8ED)))
    
    // Use pastel/light shades
    return when {
        route.contains("voucher") -> Brush.linearGradient(listOf(Color(0xFFF3E5F5), Color(0xFFEDE7F6)))
        route.contains("quotation") -> Brush.linearGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFF8E1)))
        route.contains("ledger") || route.contains("account") || route.contains("group") -> Brush.linearGradient(listOf(Color(0xFFE0F2F1), Color(0xFFE8F5E9)))
        route.contains("stock") || route.contains("unit") || route.contains("inventory") -> Brush.linearGradient(listOf(Color(0xFFE0F7FA), Color(0xFFE1F5FE)))
        route.contains("live_stock") -> Brush.linearGradient(listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2))) // Light Red/Pink
        route.contains("report") || route.contains("register") || route.contains("book") -> Brush.linearGradient(listOf(Color(0xFFE1F5FE), Color(0xFFBBDEFB))) // Light Blue for Reports
        else -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF5F5F5)))
    }
}
