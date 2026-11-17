package androidx.core.content

import android.content.Context

object ContextCompat {
    const val PERMISSION_GRANTED = 0
    const val PERMISSION_DENIED = -1

    fun checkSelfPermission(context: Context, permission: String): Int = PERMISSION_GRANTED
    fun getDrawable(context: Context, id: Int): Any? = null
    fun getColor(context: Context, id: Int): Int = 0
}
