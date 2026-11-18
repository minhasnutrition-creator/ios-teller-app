package android.os

open class Bundle {
    private val map = mutableMapOf<String, Any?>()

    fun getStringArrayList(key: String): ArrayList<String>? = null
    fun getFloatArray(key: String): FloatArray? = null
    fun putString(key: String, value: String?) { map[key] = value }
    fun getString(key: String): String? = map[key] as? String
    fun putInt(key: String, value: Int) { map[key] = value }
    fun getInt(key: String, defaultValue: Int = 0): Int = (map[key] as? Int) ?: defaultValue
    fun putBoolean(key: String, value: Boolean) { map[key] = value }
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = (map[key] as? Boolean) ?: defaultValue
    fun containsKey(key: String): Boolean = map.containsKey(key)
}

open class Handler(val looper: Any? = null) {
    open fun post(r: Runnable): Boolean { r.run(); return true }
    open fun postDelayed(r: Runnable, delayMillis: Long): Boolean { return true }
    open fun removeCallbacks(r: Any?) {}
    open fun removeCallbacksAndMessages(token: Any?) {}
}

object Looper {
    fun getMainLooper(): Any? = null
}

open class CancellationSignal {
    val isCanceled: Boolean get() = false
    fun cancel() {}
}


