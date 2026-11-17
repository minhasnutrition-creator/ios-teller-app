package android.util

object Log {
    fun d(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    fun i(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    fun w(tag: String, msg: String, tr: Throwable? = null): Int {
        println("[$tag] WARN: $msg")
        tr?.printStackTrace()
        return 0
    }

    fun e(tag: String, msg: String, tr: Throwable? = null): Int {
        println("[$tag] ERROR: $msg")
        tr?.printStackTrace()
        return 0
    }
}
