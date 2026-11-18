package android.app

import android.content.Context

open class Application : Context {
    open fun onCreate() {}
    open fun attachBaseContext(base: Context?) {}
}

open class Activity : Context {
    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
}
