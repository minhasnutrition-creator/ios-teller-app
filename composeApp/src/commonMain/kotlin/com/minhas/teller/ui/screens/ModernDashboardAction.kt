package com.minhas.teller.ui.screens

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class ModernDashboardAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val gradient: Brush
)

// Legacy DashboardAction for compatibility with other screens
data class DashboardAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val color: Color
)
