package com.minhas.teller.utils

import android.content.Context

/**
 * Persists which "Needs Attention" warnings the user has chosen to ignore,
 * keyed by the warning's stable id (e.g. "negative_cash", "missing_tax").
 */
object WarningsPrefs {
    private const val PREFS = "warnings_prefs"
    private const val KEY_IGNORED = "ignored_warning_ids"

    fun getIgnored(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_IGNORED, emptySet())?.toSet() ?: emptySet()

    fun ignore(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = prefs.getStringSet(KEY_IGNORED, emptySet())?.toMutableSet() ?: mutableSetOf()
        cur.add(id)
        prefs.edit().putStringSet(KEY_IGNORED, cur).apply()
    }
}
