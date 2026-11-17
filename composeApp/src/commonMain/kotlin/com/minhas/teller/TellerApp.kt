package com.minhas.teller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.minhas.teller.navigation.AppNavigation
import com.minhas.teller.ui.theme.MainBackgroundGradient
import com.minhas.teller.ui.theme.TellerAppTheme
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.ThemeManager

/**
 * Root Composable for the Teller Multiplatform Application.
 * Applies the design theme, resolves current company state, and coordinates AppNavigation.
 */
@Composable
fun TellerApp() {
    var currentAppTheme by remember { mutableStateOf(AppTheme.DEFAULT) }

    TellerAppTheme(appTheme = currentAppTheme) {
        val navController = rememberNavController()

        val backgroundBrush = when (currentAppTheme) {
            AppTheme.CYBER_FINANCE -> Brush.linearGradient(listOf(Color.Black, Color.Black))
            AppTheme.BENTO -> Brush.linearGradient(listOf(Color(0xFF2C3E50), Color(0xFF2C3E50)))
            AppTheme.CRYSTAL -> MainBackgroundGradient
            else -> MainBackgroundGradient
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(
                navController = navController,
                onThemeChanged = { themeIndex ->
                    currentAppTheme = AppTheme.values().getOrNull(themeIndex) ?: AppTheme.DEFAULT
                },
                currentAppTheme = currentAppTheme,
                backgroundBrush = backgroundBrush
            )
        }
    }
}
