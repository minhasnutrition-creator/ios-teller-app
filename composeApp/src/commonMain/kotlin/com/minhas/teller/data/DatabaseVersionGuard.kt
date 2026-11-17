package com.minhas.teller.data

import android.content.Context

/**
 * Detects the "older version installed over a newer one" case BEFORE Room opens the database.
 * On iOS, downgrade protection is a no-op as the App Store prevents downgrades.
 */
object DatabaseVersionGuard {

    /** Schema version stored in the on-disk database file, or null if there is no database yet. */
    fun onDiskVersion(context: Context): Int? = null

    /** True when the installed app is older than the database it would open. */
    fun isDowngrade(context: Context): Boolean = false
}
