package androidx.room

/**
 * Multiplatform compat extension for Room database transactions.
 */
suspend inline fun <R> RoomDatabase.withTransaction(crossinline block: suspend () -> R): R = block()
