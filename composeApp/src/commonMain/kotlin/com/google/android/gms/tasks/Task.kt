package com.google.android.gms.tasks

class Task<T> {
    fun addOnSuccessListener(listener: (T) -> Unit): Task<T> = this
    fun addOnFailureListener(listener: (Exception) -> Unit): Task<T> = this
    fun addOnCompleteListener(listener: (Task<T>) -> Unit): Task<T> = this
    fun isSuccessful(): Boolean = true
    fun getResult(): T? = null
}

suspend fun <T : Any> Task<T>.await(): T {
    @Suppress("UNCHECKED_CAST")
    return getResult() ?: com.google.firebase.database.DataSnapshot() as T
}
