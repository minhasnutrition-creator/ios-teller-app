package android.app

import android.content.Context

open class Application : Context {
    open fun onCreate() {}
    open fun attachBaseContext(base: Context?) {}
}

open class Activity : Context
