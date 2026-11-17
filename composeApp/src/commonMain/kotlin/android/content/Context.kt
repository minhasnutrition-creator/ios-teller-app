package android.content

import com.minhas.teller.compat.SharedPreferences

/**
 * Multiplatform Context interface for iOS target.
 */
interface Context {
    val packageName: String get() = "com.minhas.teller"
    val applicationContext: Context get() = this
    fun getSharedPreferences(name: String, mode: Int = 0): SharedPreferences? = null
    fun startActivity(intent: Any?) {}
}

open class Intent(val action: String = "", val uri: Any? = null) {
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val FLAG_ACTIVITY_NEW_TASK = 268435456
    }
    fun addFlags(flags: Int): Intent = this
    fun putExtra(name: String, value: Any?): Intent = this
    fun removeExtra(name: String) {}
    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean = defaultValue
    fun getStringExtra(name: String): String? = null
    fun getIntExtra(name: String, defaultValue: Int): Int = defaultValue
}
