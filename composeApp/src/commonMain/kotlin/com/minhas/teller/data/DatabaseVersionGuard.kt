package com.minhas.teller.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * Detects the "older APK installed over a newer one" case BEFORE Room opens the database.
 *
 * Room used to be built with fallbackToDestructiveMigrationOnDowngrade(true), which silently
 * deleted every company, voucher and ledger when a user side-loaded an older Teller APK on top of
 * a newer one. That fallback is gone; instead MainActivity calls [isDowngrade] first and, if the
 * on-disk schema is newer than this build understands, shows a blocking "please update" screen
 * and never touches the database. The user's books stay intact until they install a newer build.
 */
object DatabaseVersionGuard {

    private const val TAG = "DbVersionGuard"

    /** Schema version stored in the on-disk database file, or null if there is no database yet. */
    fun onDiskVersion(context: Context): Int? {
        val file = context.applicationContext.getDatabasePath(AppDatabase.DB_NAME)
        if (!file.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.version
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read on-disk database version", e)
            null
        }
    }

    /** True when the installed app is older than the database it would open. */
    fun isDowngrade(context: Context): Boolean {
        val onDisk = onDiskVersion(context) ?: return false
        return onDisk > AppDatabase.DB_VERSION
    }
}
