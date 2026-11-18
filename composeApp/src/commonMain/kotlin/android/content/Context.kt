package android.content

import com.minhas.teller.compat.SharedPreferences
import com.minhas.teller.compat.InMemorySharedPreferences
import java.io.File

typealias SharedPreferences = com.minhas.teller.compat.SharedPreferences

inline fun SharedPreferences.edit(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    action(editor)
    if (commit) editor.commit() else editor.apply()
}

/**
 * Multiplatform Context interface for iOS target.
 */
interface Context {
    val packageName: String get() = "com.minhas.teller"
    val applicationContext: Context get() = this
    fun getSharedPreferences(name: String, mode: Int = 0): SharedPreferences = InMemorySharedPreferences.get(name)
    fun startActivity(intent: Any?) {}
    fun getSystemService(name: String): Any? = null
    fun grantUriPermission(toPackage: String?, uri: Any?, modeFlags: Int) {}
    fun checkSelfPermission(permission: String): Int = 0

    fun getExternalFilesDir(type: String?): File = File("Documents")
    fun getFilesDir(): File = File("Documents")
    fun getCacheDir(): File = File("Cache")
    val contentResolver: Any? get() = null
    val assets: Any? get() = null
    val resources: Any? get() = null
    fun findActivity(): Any? = null

    companion object {
        const val MODE_PRIVATE = 0
        const val WIFI_SERVICE = "wifi"
        const val NSD_SERVICE = "servicediscovery"
        const val USB_SERVICE = "usb"
        const val PRINT_SERVICE = "print"
        const val CLIPBOARD_SERVICE = "clipboard"
    }
}

open class Intent {
    var action: String = ""
    var data: Any? = null
    var type: String? = null

    constructor()
    constructor(action: String) { this.action = action }
    constructor(action: String, uri: Any?) { this.action = action; this.data = uri }
    constructor(context: Any?, cls: Any?)

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_DIAL = "android.intent.action.DIAL"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val CATEGORY_HOME = "android.intent.category.HOME"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val FLAG_ACTIVITY_NEW_TASK = 268435456
        const val FLAG_ACTIVITY_CLEAR_TASK = 32768
        const val FLAG_GRANT_READ_URI_PERMISSION = 1

        fun createChooser(target: Intent, title: CharSequence?): Intent = target
    }

    fun addFlags(flags: Int): Intent = this
    fun setFlags(flags: Int): Intent = this
    fun putExtra(name: String, value: Any?): Intent = this
    fun putParcelableArrayListExtra(name: String, value: Any?): Intent = this
    fun removeExtra(name: String) {}
    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean = defaultValue
    fun getStringExtra(name: String): String? = null
    fun getIntExtra(name: String, defaultValue: Int): Int = defaultValue
    fun setType(type: String?): Intent { this.type = type; return this }
    fun setPackage(pkg: String?): Intent = this
    fun setData(uri: Any?): Intent { this.data = uri; return this }
    fun setDataAndType(uri: Any?, type: String?): Intent { this.data = uri; this.type = type; return this }
}
