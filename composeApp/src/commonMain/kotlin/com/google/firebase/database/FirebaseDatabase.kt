package com.google.firebase.database

interface ValueEventListener {
    fun onDataChange(snapshot: DataSnapshot)
    fun onCancelled(error: DatabaseError)
}

class DatabaseError(val message: String = "")

class DataSnapshot {
    val key: String? = null
    val value: Any? = null
    val children: Iterable<DataSnapshot> = emptyList()

    fun exists(): Boolean = false
    fun hasChildren(): Boolean = false
    fun hasChild(path: String): Boolean = false
    fun child(path: String): DataSnapshot = DataSnapshot()
    inline fun <reified T : Any> getValue(valueType: Class<T>): T? = null
    fun <T> getValue(t: Any?): T? = null
}

class DatabaseReference {
    val key: String? = null

    fun child(path: String): DatabaseReference = this
    fun push(): DatabaseReference = DatabaseReference()
    fun get(): com.google.android.gms.tasks.Task<DataSnapshot> = com.google.android.gms.tasks.Task()
    fun setValue(value: Any?): com.google.android.gms.tasks.Task<Any> = com.google.android.gms.tasks.Task()
    fun removeValue(): com.google.android.gms.tasks.Task<Any> = com.google.android.gms.tasks.Task()
    fun updateChildren(update: Map<String, Any?>): com.google.android.gms.tasks.Task<Any> = com.google.android.gms.tasks.Task()
    fun addValueEventListener(listener: ValueEventListener): ValueEventListener = listener
    fun addListenerForSingleValueEvent(listener: ValueEventListener) {}
    fun removeEventListener(listener: ValueEventListener) {}
}

class FirebaseDatabase private constructor() {
    companion object {
        fun getInstance(): FirebaseDatabase = FirebaseDatabase()
    }

    val reference: DatabaseReference get() = getReference()
    fun getReference(path: String = ""): DatabaseReference = DatabaseReference()
}

object ServerValue {
    val TIMESTAMP: Map<String, String> = mapOf(".sv" to "timestamp")
}

