package com.minhas.teller.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.minhas.teller.MainActivity

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
        // Default to CRYSTAL if no preference is saved
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
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        (context as? android.app.Activity)?.finish()
    }
}
