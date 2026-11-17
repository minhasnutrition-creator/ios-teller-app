package com.minhas.teller.compat

import com.minhas.teller.PlatformUtils

/**
 * Multiplatform compatibility layer for Teller iOS.
 * Bridges legacy Android-specific types (Context, Toast, Log, Uri, Intent)
 * to native iOS capabilities via PlatformUtils without requiring massive destructive refactors
 * across 280+ files.
 */

// SharedPreferences stub for iOS NSUserDefaults
interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}
