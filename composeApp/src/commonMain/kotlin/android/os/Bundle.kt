package android.os

open class Bundle {
    private val map = mutableMapOf<String, Any?>()

    fun getStringArrayList(key: String): java.util.ArrayList<String>? = null
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
    open fun post(r: () -> Unit): Boolean { r(); return true }
    open fun postDelayed(r: () -> Unit, delayMillis: Long): Boolean { return true }
}

object Looper {
    fun getMainLooper(): Any? = null
}
