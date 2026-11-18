package com.minhas.teller.utils

import android.content.Context

enum class AppTheme(val themeName: String) {
    DEFAULT("Modern Dark"),
    MODERN_LIGHT("Modern Light"),
    CYBER_FINANCE("Cyber-Finance"),
    BENTO("Pastel Bento"),
    CRYSTAL("Crystal Minimalist")
}

object ThemeManager {
    private const val PREFS_NAME = "ThemePrefs"
    private const val THEME_KEY = "current_theme_v2"

    fun getCurrentTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(THEME_KEY, AppTheme.CRYSTAL.name) ?: AppTheme.CRYSTAL.name
        return when (themeName) {
            "BENTO_A", "BENTO_B" -> AppTheme.BENTO
            else -> runCatching { AppTheme.valueOf(themeName) }.getOrDefault(AppTheme.CRYSTAL)
        }
    }

    fun setTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(THEME_KEY, theme.name).apply()
    }
    
    fun restartApp(context: Context) {
        // No-op on iOS
    }
}
