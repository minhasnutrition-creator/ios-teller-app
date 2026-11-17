package android.graphics

class Bitmap {
    val width: Int get() = 0
    val height: Int get() = 0

    enum class Config {
        ARGB_8888,
        RGB_565
    }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap()
    }
}

class Canvas(val bitmap: Bitmap? = null) {
    fun drawColor(color: Int) {}
}

class Paint {
    var color: Int = 0
    var strokeWidth: Float = 1f
    var isAntiAlias: Boolean = true
}

object Color {
    const val BLACK = -16777216
    const val WHITE = -1
    const val RED = -65536
    const val GREEN = -16711936
    const val BLUE = -16776961
    const val TRANSPARENT = 0

    fun parseColor(colorString: String): Int = 0
}
