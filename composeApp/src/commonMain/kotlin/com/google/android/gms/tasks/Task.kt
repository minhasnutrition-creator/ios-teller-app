package com.google.android.gms.tasks

class Task<T> {
    fun addOnSuccessListener(listener: (T) -> Unit): Task<T> = this
    fun addOnFailureListener(listener: (Exception) -> Unit): Task<T> = this
    fun addOnCompleteListener(listener: (Task<T>) -> Unit): Task<T> = this
    fun isSuccessful(): Boolean = true
    fun getResult(): T? = null
}

@Suppress("UNCHECKED_CAST")
suspend fun <T : Any> Task<T>.await(): T = getResult() as T
