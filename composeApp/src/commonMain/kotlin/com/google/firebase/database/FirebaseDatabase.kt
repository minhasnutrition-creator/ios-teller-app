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
    inline fun <reified T> getValue(valueType: Class<T>): T? = null
    fun <T> getValue(t: Any?): T? = null
}

class DatabaseReference {
    val key: String? = null

    fun child(path: String): DatabaseReference = this
    fun setValue(value: Any?): Any? = null
    fun removeValue(): Any? = null
    fun updateChildren(update: Map<String, Any?>): Any? = null
    fun addValueEventListener(listener: ValueEventListener): ValueEventListener = listener
    fun addListenerForSingleValueEvent(listener: ValueEventListener) {}
    fun removeEventListener(listener: ValueEventListener) {}
}

class FirebaseDatabase private constructor() {
    companion object {
        fun getInstance(): FirebaseDatabase = FirebaseDatabase()
    }

    fun getReference(path: String = ""): DatabaseReference = DatabaseReference()
}
