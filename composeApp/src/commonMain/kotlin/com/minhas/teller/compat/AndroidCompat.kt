package com.minhas.teller.compat

import com.minhas.teller.PlatformUtils

/**
 * Multiplatform compatibility layer for Teller iOS.
 * Bridges legacy Android-specific types (Context, Toast, Log, Uri, Intent)
 * to native iOS capabilities via PlatformUtils.
 */

interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

class InMemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getString(key: String, defValue: String?): String? = (data[key] as? String) ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (data[key] as? Set<String>) ?: defValues

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    private inner class EditorImpl : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor { data[key] = value; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { data[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { data[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { data[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { data[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { data[key] = values; return this }
        override fun remove(key: String): SharedPreferences.Editor { data.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { data.clear(); return this }
        override fun apply() {}
        override fun commit(): Boolean = true
    }

    companion object {
        private val instances = mutableMapOf<String, InMemorySharedPreferences>()
        fun get(name: String): InMemorySharedPreferences = instances.getOrPut(name) { InMemorySharedPreferences() }
    }
}
