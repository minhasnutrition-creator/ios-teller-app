package android.os

object Build {
    object VERSION {
        const val SDK_INT = 34
    }

    object VERSION_CODES {
        const val M = 23
        const val N = 24
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
}

class Bundle {
    fun getString(key: String): String? = null
    fun getInt(key: String, defaultValue: Int = 0): Int = defaultValue
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = defaultValue
}
