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
    // `Class<T>` is our `typealias Class<T> = Any` shim, which erases T entirely — a
    // parameter typed that way gives Kotlin's inference nothing to work with, so T could
    // only ever be solved via target-type context (e.g. an immediate `?: default`), and
    // silently failed everywhere else ("Cannot infer type for this parameter"). `KClass<T>`
    // is what `SomeType::class.java` actually evaluates to under our `.java` shim, so typing
    // the parameter that way lets every call site infer T from the argument directly.
    inline fun <reified T : Any> getValue(valueType: kotlin.reflect.KClass<T>): T? = null
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
    fun increment(delta: Long): Map<String, Any> = mapOf(".sv" to mapOf("increment" to delta))
    fun increment(delta: Double): Map<String, Any> = mapOf(".sv" to mapOf("increment" to delta))
}

